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
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.infrastructure.websocket.GameBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
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
            return;
        }

        PlayerToken token = computerColor == Color.WHITE ? access.whiteToken() : access.blackToken();
        boolean computerIsMover = snapshot.sideToMove() == computerColor;
        Board board = snapshot.board();
        List<Move> legalMoves = snapshot.legalMoves();

        Thread.ofVirtual().name("computer-player-" + gameId).start(
                () -> act(gameId, token, computerIsMover, computerColor, board, legalMoves, level));
    }

    private void act(GameId gameId, PlayerToken token, boolean computerIsMover, Color computerColor,
                      Board board, List<Move> legalMoves, ComputerLevel level) {
        Move chosen = computerIsMover
                ? chooseMoveOrFallback(gameId, board, legalMoves, level)
                : guessKingCaptureOrFallback(gameId, computerColor, board, legalMoves, level);
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
                                             List<Move> legalMoves, ComputerLevel level) {
        List<Move> kingCaptures = legalMoves.stream()
                .filter(move -> move.isCapture()
                        && move.capturedPiece().type() == PieceType.KING
                        && move.capturedPiece().color() == computerColor)
                .toList();
        if (!kingCaptures.isEmpty()) {
            return kingCaptures.get(ThreadLocalRandom.current().nextInt(kingCaptures.size()));
        }
        return chooseMoveOrFallback(gameId, board, legalMoves, level);
    }

    /**
     * Filet de secours si le moteur echoue malgre la nouvelle tentative deja faite
     * cote StockfishChessEngine (process/communication toujours indisponible) : un
     * coup aleatoire parmi les coups legaux plutot que de laisser le round bloque
     * indefiniment - sans lui, cette exception etait avalee par le catch de act() et
     * l'ordinateur ne soumettait alors plus jamais rien pour ce round, laissant
     * l'humain attendre indefiniment (bug rencontre en pratique). Degrade la qualite
     * de ce seul coup, jamais la progression de la partie.
     */
    private Move chooseMoveOrFallback(GameId gameId, Board board, List<Move> legalMoves, ComputerLevel level) {
        try {
            return chessEngine.chooseMove(board, legalMoves, level);
        } catch (Exception e) {
            log.error("computer player engine failed for game {}, falling back to a random legal move", gameId, e);
            return legalMoves.get(ThreadLocalRandom.current().nextInt(legalMoves.size()));
        }
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
