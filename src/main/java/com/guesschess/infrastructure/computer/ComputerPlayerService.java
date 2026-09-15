package com.guesschess.infrastructure.computer;

import com.guesschess.application.GameAccess;
import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.GameSnapshot;
import com.guesschess.application.MoveIntent;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.PlayerToken;
import com.guesschess.application.computer.ChessEngine;
import com.guesschess.application.computer.ComputerLevel;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.CheckDetector;
import com.guesschess.domain.rules.MaterialEvaluator;
import com.guesschess.domain.rules.MoveGenerator;
import com.guesschess.infrastructure.websocket.GameBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fait jouer/deviner l'ordinateur (etape 15 de la roadmap) : declenchee a chaque debut
 * de round (creation d'une partie contre l'ordinateur, ou resolution du round
 * precedent - voir GameCreationController/GameController), jamais par polling. Le role
 * de l'ordinateur pour CE round (joueur au trait, donc il soumet son propre coup, ou
 * adversaire du trait, donc il devine) decoule simplement de Game.sideToMove() compare
 * a la couleur liee a PlayerRef.Computer - dans les deux cas ChessEngine.chooseMove
 * pose exactement la meme question ("meilleur coup dans cette position ?"), voir
 * ChessEngine.
 *
 * Le calcul (process Stockfish, potentiellement quelques centaines de ms) tourne sur
 * un thread virtuel plutot que de bloquer le thread qui vient de traiter la soumission
 * humaine - la reponse de l'ordinateur arrive alors comme un message pousse ordinaire
 * (GameBroadcastService), jamais comme une attente synchrone cote client. Si ce coup
 * complete a son tour la paire du round (l'humain avait deja devine par avance, voir
 * Game.submitMove/submitGuess), ce thread diffuse lui-meme le resultat et se
 * redeclenche pour le round suivant - aucun autre appelant ne surveille cette
 * completion asynchrone.
 */
@Component
public class ComputerPlayerService {

    private static final Logger log = LoggerFactory.getLogger(ComputerPlayerService.class);

    private final GameLifecycleService gameLifecycleService;
    private final GameAccessRepository gameAccessRepository;
    private final ChessEngine chessEngine;
    private final GameBroadcastService gameBroadcastService;

    public ComputerPlayerService(GameLifecycleService gameLifecycleService, GameAccessRepository gameAccessRepository,
                                  ChessEngine chessEngine, GameBroadcastService gameBroadcastService) {
        this.gameLifecycleService = gameLifecycleService;
        this.gameAccessRepository = gameAccessRepository;
        this.chessEngine = chessEngine;
        this.gameBroadcastService = gameBroadcastService;
    }

    /**
     * Un coup reel de l'ordinateur qui vient d'etre devine (et donc annule, voir
     * rememberIfOwnMoveWasJustBlocked) reste evite pendant ce nombre de tours suivants
     * ou l'ordinateur doit a nouveau choisir son propre coup (pas ses tours de
     * devinette) - constat empirique (niveau difficile uniquement) qu'un adversaire qui
     * vient de deviner juste a de bonnes chances de retenter la meme devinette si
     * l'ordinateur rejoue le meme coup, objectivement toujours le meilleur dans une
     * position autrement inchangee.
     */
    private static final int BLOCKED_MOVE_TURNS = 2;

    /** Etat par partie, best-effort (pas persiste) - voir onRoundStarted/act. */
    private final Map<GameId, List<BlockedMove>> blockedMovesByGame = new ConcurrentHashMap<>();

    /**
     * Un coup reel de l'ordinateur recemment devine, identifie par origine/destination/
     * promotion plutot que par egalite complete de Move : la piece capturee (si il y en
     * a une) depend de la position au moment du coup, qui change d'un tour a l'autre,
     * alors que c'est bien "le meme coup" du point de vue de la previsibilite qu'on
     * cherche a eviter.
     */
    private record BlockedMove(Position from, Position to, PieceType promotionType, int turnsRemaining) {
        boolean matches(Move move) {
            return from.equals(move.from()) && to.equals(move.to()) && promotionType == move.promotionType();
        }
    }

    public void onRoundStarted(GameId gameId) {
        GameAccess access = gameAccessRepository.findByGameId(gameId).orElse(null);
        if (access == null) {
            return;
        }
        Color computerColor = computerColorOf(access);
        if (computerColor == null) {
            return;
        }
        ComputerLevel level = ((PlayerRef.Computer) access.playerOf(computerColor)).level();
        GameSnapshot snapshot = gameLifecycleService.viewGame(gameId);
        if (snapshot.status() != GameStatus.ONGOING) {
            blockedMovesByGame.remove(gameId);
            return;
        }
        if (level == ComputerLevel.HARD) {
            rememberIfOwnMoveWasJustBlocked(gameId, computerColor, snapshot.lastRoundResult());
        }

        PlayerToken token = computerColor == Color.WHITE ? access.whiteToken() : access.blackToken();
        boolean computerIsMover = snapshot.sideToMove() == computerColor;
        Board board = snapshot.board();
        List<Move> legalMoves = snapshot.legalMoves();
        GameVariant variant = snapshot.variant();

        Thread.ofVirtual().name("computer-player-" + gameId).start(
                () -> act(gameId, token, computerIsMover, computerColor, board, legalMoves, level, variant));
    }

    /**
     * Round juste resolu (celui qui a declenche cet appel, voir GameController/act) :
     * si l'ordinateur y etait joueur au trait et que sa devinette adverse est correcte,
     * son coup reel vient d'etre annule - a memoriser pour ne pas le retenter tel quel
     * dans l'immediat (voir BLOCKED_MOVE_TURNS). lastRoundResult est null pour le tout
     * premier round d'une partie (rien resolu encore).
     */
    private void rememberIfOwnMoveWasJustBlocked(GameId gameId, Color computerColor, RoundResult lastRoundResult) {
        if (lastRoundResult == null || lastRoundResult.mover() != computerColor || !lastRoundResult.guessedCorrectly()) {
            return;
        }
        Move blocked = lastRoundResult.actualMove();
        blockedMovesByGame.computeIfAbsent(gameId, id -> new ArrayList<>())
                .add(new BlockedMove(blocked.from(), blocked.to(), blocked.promotionType(), BLOCKED_MOVE_TURNS));
    }

    /**
     * A appeler une fois par tour ou l'ordinateur choisit son propre coup (jamais pour
     * une devinette, sans rapport avec les coups annules de l'ordinateur lui-meme) :
     * decompte BLOCKED_MOVE_TURNS pour chaque coup encore surveille et renvoie, parmi
     * legalMoves, ceux a eviter si une alternative existe (laisse a StockfishChessEngine
     * le soin de ne pas les exclure si ca viderait la liste - voir
     * ChessEngine.chooseMove).
     */
    private Set<Move> movesToAvoidThisTurn(GameId gameId, List<Move> legalMoves) {
        List<BlockedMove> current = blockedMovesByGame.get(gameId);
        if (current == null || current.isEmpty()) {
            return Set.of();
        }
        List<BlockedMove> remaining = new ArrayList<>();
        Set<Move> toAvoid = new HashSet<>();
        for (BlockedMove blocked : current) {
            legalMoves.stream().filter(blocked::matches).forEach(toAvoid::add);
            if (blocked.turnsRemaining() - 1 > 0) {
                remaining.add(new BlockedMove(blocked.from(), blocked.to(), blocked.promotionType(), blocked.turnsRemaining() - 1));
            }
        }
        if (remaining.isEmpty()) {
            blockedMovesByGame.remove(gameId);
        } else {
            blockedMovesByGame.put(gameId, remaining);
        }
        return toAvoid;
    }

    private void act(GameId gameId, PlayerToken token, boolean computerIsMover, Color computerColor,
                      Board board, List<Move> legalMoves, ComputerLevel level, GameVariant variant) {
        Move chosen = computerIsMover
                ? chooseMoveOrFallback(gameId, board, legalMoves, level, variant, movesToAvoidThisTurn(gameId, legalMoves))
                : guessKingCaptureOrFallback(gameId, computerColor, board, legalMoves, level, variant);
        try {
            MoveIntent intent = chosen.promotionType() == null
                    ? MoveIntent.of(chosen.from(), chosen.to())
                    : MoveIntent.promotingTo(chosen.from(), chosen.to(), chosen.promotionType());
            Optional<GameSnapshot> resolved = computerIsMover
                    ? gameLifecycleService.submitMove(token, intent)
                    : gameLifecycleService.submitGuess(token, intent);
            resolved.ifPresent(snapshot -> {
                gameBroadcastService.broadcast(snapshot);
                onRoundStarted(gameId);
            });
        } catch (Exception e) {
            log.error("computer player failed to act for game {}", gameId, e);
        }
    }

    /**
     * Devinette de l'ordinateur : parmi les coups legaux de l'adversaire au trait
     * (voir ChessEngine), un coup qui capture le roi de l'ordinateur lui-meme peut
     * exister - cas du round suivant une devinette adverse correcte alors que
     * l'ordinateur etait en echec (voir Game.resolveRound/applyRealMove, variante
     * GUESSCHESS sans Guessmate : le coup reel annule laisse le roi en echec non
     * resolu, et l'adversaire devient alors joueur au trait avec ce coup de capture
     * parmi ses coups legaux). Stockfish ne considere jamais ce coup comme LA reponse
     * a deviner (la capture du roi n'existe pas dans son modele des echecs classiques),
     * donc sans ce raccourci l'ordinateur ne devine jamais ce coup, meme quand c'est
     * objectivement la seule devinette qui sauve la partie. Priorite absolue sur
     * l'evaluation du moteur des qu'un tel coup existe ; hasard si plusieurs pieces
     * peuvent capturer ce roi.
     */
    private Move guessKingCaptureOrFallback(GameId gameId, Color computerColor, Board board,
                                             List<Move> legalMoves, ComputerLevel level, GameVariant variant) {
        List<Move> kingCaptures = legalMoves.stream()
                .filter(move -> move.isCapture()
                        && move.capturedPiece().type() == PieceType.KING
                        && move.capturedPiece().color() == computerColor)
                .toList();
        if (!kingCaptures.isEmpty()) {
            return kingCaptures.get(ThreadLocalRandom.current().nextInt(kingCaptures.size()));
        }
        // Devinette, pas coup propre : BlockedMove ne s'applique jamais ici (voir
        // movesToAvoidThisTurn). findFastMateMoves s'applique quand meme tel quel : un
        // adversaire rationnel qui a acces a un coup qui nous mettrait en fast_mate va le
        // jouer, c'est donc aussi la devinette la plus plausible (voir findFastMateMoves).
        return chooseMoveOrFallback(gameId, board, legalMoves, level, variant, Set.of());
    }

    /**
     * Court-circuit symetrique de guessKingCaptureOrFallback, cote coup reel cette
     * fois : le round precedent peut avoir laisse le roi adverse en echec non resolu
     * (voir plus haut), auquel cas une capture de ce roi figure parmi les coups
     * legaux de l'ordinateur ici joueur au trait. Sans ce court-circuit, chooseMove
     * envoyait cette position a Stockfish via son FEN (roi adverse en echec alors
     * que ce n'est pas son tour - illegal aux yeux d'un moteur d'echecs classique) ;
     * plausible cause d'un crash natif intermittent du process observe en pratique
     * (recherche qui explore cette capture puis une position sans roi, jamais prevue
     * par un moteur classique), plus frequent en difficile (recherche plus profonde,
     * plus de chances d'atteindre cette branche). Capturer ce roi est de toute facon
     * objectivement le meilleur coup possible, inutile de consulter le moteur.
     */
    private Move chooseMoveOrFallback(GameId gameId, Board board, List<Move> legalMoves, ComputerLevel level,
                                       GameVariant variant, Set<Move> movesToAvoidIfPossible) {
        List<Move> kingCaptures = legalMoves.stream()
                .filter(move -> move.isCapture() && move.capturedPiece().type() == PieceType.KING)
                .toList();
        if (!kingCaptures.isEmpty()) {
            return kingCaptures.get(ThreadLocalRandom.current().nextInt(kingCaptures.size()));
        }
        if (variant == GameVariant.GUESSCHESS && Game.isFastMateEnabled()) {
            List<Move> fastMateMoves = findFastMateMoves(board, legalMoves);
            if (!fastMateMoves.isEmpty()) {
                return fastMateMoves.get(ThreadLocalRandom.current().nextInt(fastMateMoves.size()));
            }
        }
        try {
            return chessEngine.chooseMove(board, legalMoves, level, movesToAvoidIfPossible);
        } catch (Exception e) {
            log.error("computer player engine failed for game {}, falling back to a random legal move", gameId, e);
            return legalMoves.get(ThreadLocalRandom.current().nextInt(legalMoves.size()));
        }
    }

    /**
     * Parmi legalMoves, ceux qui declenchent un fast_mate (voir Game.applyFastMateIfApplicable) :
     * apres le coup, l'adversaire se retrouve au trait en echec avec un seul coup legal et
     * assez de materiel en face pour forcer le mat - victoire immediate (KING_CAPTURED) des la
     * resolution du round suivant, que ce coup soit lui-meme devine ou non (fast_mate ne depend
     * que de la position resultante, pas de si CE coup-ci a ete devine). Stockfish ne modelise
     * pas cette regle maison : un coup qui echec-et-mate en un mais que son evaluation classique
     * ne distingue pas nettement d'un coup "juste bon" (ex. le mat lui-meme sacrifie du materiel)
     * peut donc etre ecarte par le moteur alors qu'il gagne la partie sur-le-champ - d'ou cette
     * recherche manuelle, prioritaire sur l'appel au moteur. Reutilise tel quel pour deviner
     * (voir guessKingCaptureOrFallback) : legalMoves/board y representent alors les coups de
     * l'adversaire, et un adversaire rationnel qui a acces a un fast_mate va le jouer - donc
     * aussi la devinette la plus plausible dans ce cas.
     */
    private List<Move> findFastMateMoves(Board board, List<Move> legalMoves) {
        List<Move> fastMateMoves = new ArrayList<>();
        for (Move move : legalMoves) {
            Board after = board.applyMove(move);
            Color responder = after.sideToMove();
            if (CheckDetector.isInCheck(after, responder)
                    && MoveGenerator.generateLegalMoves(after, responder).size() == 1
                    && !MaterialEvaluator.isInsufficientMaterial(after)) {
                fastMateMoves.add(move);
            }
        }
        return fastMateMoves;
    }

    private Color computerColorOf(GameAccess access) {
        if (access.whitePlayer() instanceof PlayerRef.Computer) {
            return Color.WHITE;
        }
        if (access.blackPlayer() instanceof PlayerRef.Computer) {
            return Color.BLACK;
        }
        return null;
    }
}
