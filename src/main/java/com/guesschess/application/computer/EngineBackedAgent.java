package com.guesschess.application.computer;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.CheckDetector;
import com.guesschess.domain.rules.MaterialEvaluator;
import com.guesschess.domain.rules.MoveGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * Agent (etape 20) qui s'appuie sur un ChessEngine, simple oracle "meilleur coup" - les
 * deux moteurs historiques (minimax@1, stockfish@1) passent par lui. Reprend telles
 * quelles, sans changement de comportement, les regles qui vivaient dans
 * ComputerPlayerService (etapes 15/17) : capture de roi jamais jouee sauf si forcee,
 * recherche active de fast_mate, memoire des coups annules (BlockedMove, difficile) et de
 * la derniere devinette. Un futur agent qui n'a pas besoin d'un oracle implemente
 * GuessAgent directement.
 *
 * engines : fabrique du ChessEngine d'une session, recevant le rng de la partie (voir
 * AgentSessionContext) - un moteur sans hasard peut l'ignorer.
 */
public final class EngineBackedAgent implements GuessAgent {

    private static final Logger log = LoggerFactory.getLogger(EngineBackedAgent.class);

    /**
     * Un coup reel de l'ordinateur recemment devine (et donc annule, voir
     * Session.onRoundResolved) reste evite pendant ce nombre de tours suivants ou
     * l'ordinateur doit a nouveau choisir son propre coup (pas ses tours de devinette) -
     * constat empirique (niveau difficile uniquement) qu'un adversaire qui vient de deviner
     * juste a de bonnes chances de retenter la meme devinette si l'ordinateur rejoue le meme
     * coup, objectivement toujours le meilleur dans une position autrement inchangee.
     */
    private static final int BLOCKED_MOVE_TURNS = 2;

    /**
     * Etape 17, strategie 3 ("varier la devinette") : la derniere devinette reste evitee (si
     * une alternative existe) pour le tour de devinette suivant - "deux fois d'affilee", donc
     * une memoire d'un seul coup (pas de compte a rebours comme BLOCKED_MOVE_TURNS).
     */
    private static final int GUESS_REPETITION_AVOID_TURNS = 1;

    private final AgentId id;
    private final ComputerLevel level;
    private final Function<RandomGenerator, ChessEngine> engines;
    private final BooleanSupplier available;

    public EngineBackedAgent(AgentId id, ComputerLevel level, Function<RandomGenerator, ChessEngine> engines,
                             BooleanSupplier available) {
        this.id = id;
        this.level = level;
        this.engines = engines;
        this.available = available;
    }

    @Override
    public AgentId id() {
        return id;
    }

    @Override
    public boolean isAvailable() {
        return available.getAsBoolean();
    }

    @Override
    public AgentSession newSession(AgentSessionContext context) {
        return new Session(context, engines.apply(context.rng()));
    }

    /**
     * Un coup identifie par origine/destination/promotion plutot que par egalite complete de
     * Move : la piece capturee (si il y en a une) depend de la position au moment du coup,
     * alors que c'est bien "le meme coup" du point de vue de la previsibilite qu'on cherche a
     * eviter.
     */
    private record BlockedMove(Position from, Position to, PieceType promotionType, int turnsRemaining) {
        boolean matches(Move move) {
            return from.equals(move.from()) && to.equals(move.to()) && promotionType == move.promotionType();
        }
    }

    private final class Session implements AgentSession {

        private final AgentSessionContext context;
        private final ChessEngine engine;
        private List<BlockedMove> blockedMoves = new ArrayList<>();
        private BlockedMove lastGuess;

        Session(AgentSessionContext context, ChessEngine engine) {
            this.context = context;
            this.engine = engine;
        }

        @Override
        public void onRoundResolved(RoundResult lastRound) {
            if (level != ComputerLevel.HARD || lastRound == null
                    || lastRound.mover() != context.color() || !lastRound.guessedCorrectly()) {
                return;
            }
            // Le coup reel de l'ordinateur vient d'etre devine puis annule : ne pas le retenter
            // tel quel dans l'immediat (voir BLOCKED_MOVE_TURNS).
            Move blocked = lastRound.actualMove();
            blockedMoves.add(new BlockedMove(blocked.from(), blocked.to(), blocked.promotionType(), BLOCKED_MOVE_TURNS));
        }

        @Override
        public Move move(Board board, List<Move> legalMoves) {
            return chooseMoveOrFallback(board, legalMoves, movesToAvoidThisTurn(legalMoves));
        }

        @Override
        public Move guess(Board board, List<Move> opponentLegalMoves) {
            Move chosen = guessKingCaptureOrFallback(board, opponentLegalMoves);
            if (level == ComputerLevel.HARD) {
                lastGuess = new BlockedMove(chosen.from(), chosen.to(), chosen.promotionType(), GUESS_REPETITION_AVOID_TURNS);
            }
            return chosen;
        }

        /**
         * A appeler une fois par tour ou l'ordinateur choisit son propre coup (jamais pour une
         * devinette) : decompte BLOCKED_MOVE_TURNS pour chaque coup encore surveille et renvoie,
         * parmi legalMoves, ceux a eviter si une alternative existe (le moteur ne les exclut pas
         * si ca viderait la liste - voir ChessEngine.chooseMove).
         */
        private Set<Move> movesToAvoidThisTurn(List<Move> legalMoves) {
            if (blockedMoves.isEmpty()) {
                return Set.of();
            }
            List<BlockedMove> remaining = new ArrayList<>();
            Set<Move> toAvoid = new HashSet<>();
            for (BlockedMove blocked : blockedMoves) {
                legalMoves.stream().filter(blocked::matches).forEach(toAvoid::add);
                if (blocked.turnsRemaining() - 1 > 0) {
                    remaining.add(new BlockedMove(blocked.from(), blocked.to(), blocked.promotionType(), blocked.turnsRemaining() - 1));
                }
            }
            blockedMoves = remaining;
            return toAvoid;
        }

        /**
         * Symetrique de movesToAvoidThisTurn, cote devinette : renvoie la derniere devinette
         * (si elle existe encore parmi legalMoves) et l'oublie aussitot.
         */
        private Set<Move> guessesToAvoidThisTurn(List<Move> legalMoves) {
            BlockedMove last = lastGuess;
            lastGuess = null;
            if (last == null) {
                return Set.of();
            }
            return legalMoves.stream().filter(last::matches).collect(Collectors.toSet());
        }

        /**
         * Devinette : parmi les coups legaux de l'adversaire au trait, un coup qui capture le
         * roi de l'ordinateur lui-meme peut exister - round suivant une devinette adverse
         * correcte alors que l'ordinateur etait en echec (voir Game.resolveRound/applyRealMove,
         * variante GUESSCHESS sans Guessmate : le coup reel annule laisse le roi en echec non
         * resolu). Stockfish ne considere jamais ce coup comme LA reponse a deviner (la capture
         * du roi n'existe pas dans son modele des echecs classiques), donc sans ce raccourci
         * l'ordinateur ne le devine jamais, meme quand c'est la seule devinette qui sauve la
         * partie. Priorite absolue sur l'evaluation du moteur des qu'un tel coup existe ; hasard
         * si plusieurs pieces peuvent capturer ce roi.
         */
        private Move guessKingCaptureOrFallback(Board board, List<Move> legalMoves) {
            List<Move> kingCaptures = legalMoves.stream()
                    .filter(move -> move.isCapture()
                            && move.capturedPiece().type() == PieceType.KING
                            && move.capturedPiece().color() == context.color())
                    .toList();
            if (!kingCaptures.isEmpty()) {
                return kingCaptures.get(context.rng().nextInt(kingCaptures.size()));
            }
            // BlockedMove (coups reels annules) ne s'applique jamais ici : guessesToAvoidThisTurn
            // est son equivalent cote devinette. findFastMateMoves s'applique quand meme tel
            // quel : un adversaire rationnel qui a acces a un coup qui nous mettrait en fast_mate
            // va le jouer, c'est donc aussi la devinette la plus plausible.
            Set<Move> toAvoid = level == ComputerLevel.HARD ? guessesToAvoidThisTurn(legalMoves) : Set.of();
            return chooseMoveOrFallback(board, legalMoves, toAvoid);
        }

        /**
         * Cote coup reel : le round precedent peut avoir laisse le roi adverse en echec non
         * resolu, auquel cas une capture de ce roi figure parmi les coups legaux. Jouee
         * UNIQUEMENT si c'est le seul coup legal (coup force) - sinon exclue des candidats. Ce
         * coup n'est jamais "gratuit" : le mover n'est pas en echec, donc une devinette adverse
         * correcte ne declenche jamais la regle GUESSMATE d'issue immediate (Game.resolveRound,
         * moverWasInCheck) - elle annule juste le coup, rendant le trait au devineur. Un humain
         * qui connait la regle devine cette capture quasi systematiquement : coup tres previsible
         * a eviter tant qu'une alternative existe.
         */
        private Move chooseMoveOrFallback(Board board, List<Move> legalMoves, Set<Move> movesToAvoidIfPossible) {
            List<Move> kingCaptures = legalMoves.stream()
                    .filter(move -> move.isCapture() && move.capturedPiece().type() == PieceType.KING)
                    .toList();
            if (kingCaptures.size() == legalMoves.size()) {
                return kingCaptures.get(context.rng().nextInt(kingCaptures.size()));
            }
            List<Move> candidateMoves = kingCaptures.isEmpty()
                    ? legalMoves
                    : legalMoves.stream().filter(move -> !kingCaptures.contains(move)).toList();
            if (context.variant() == GameVariant.GUESSCHESS && Game.isFastMateEnabled()) {
                List<Move> fastMateMoves = findFastMateMoves(board, candidateMoves);
                if (!fastMateMoves.isEmpty()) {
                    return fastMateMoves.get(context.rng().nextInt(fastMateMoves.size()));
                }
            }
            try {
                return engine.chooseMove(board, candidateMoves, level, movesToAvoidIfPossible);
            } catch (Exception e) {
                log.error("agent {} failed for game {}, falling back to a random legal move", id, context.label(), e);
                return candidateMoves.get(context.rng().nextInt(candidateMoves.size()));
            }
        }

        /**
         * Parmi legalMoves, ceux qui declenchent un fast_mate (voir Game.applyFastMateIfApplicable) :
         * apres le coup, l'adversaire se retrouve au trait en echec avec un seul coup legal et
         * assez de materiel en face pour forcer le mat - victoire immediate des la resolution du
         * round suivant, que ce coup soit lui-meme devine ou non. Stockfish ne modelise pas cette
         * regle maison, d'ou cette recherche manuelle prioritaire sur l'appel au moteur. Reutilise
         * tel quel pour deviner (voir guessKingCaptureOrFallback).
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
    }
}
