package com.guesschess.application.computer;

import com.guesschess.application.computer.GuessAwareSearch.RootMove;
import com.guesschess.application.computer.GuessAwareSearch.RootSolution;
import com.guesschess.application.computer.GuessAwareSearch.Rules;
import com.guesschess.application.computer.GuessAwareSearch.SearchTimeoutException;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.PieceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Agent guessaware@1 (etape 21) : joue et devine selon l'equilibre du jeu matriciel de chaque
 * round (voir GuessAwareSearch), au lieu de "le meilleur coup" (EngineBackedAgent). Sans
 * oracle ChessEngine : c'est la recherche qui distingue les deux roles.
 *  - jouer : tirage selon la strategie mixte x du joueur au trait - imprevisible par
 *    construction, donc sans memoire de coups annules ni de devinette (BlockedMove de minimax@1) ;
 *  - deviner : tirage selon la strategie y de l'adversaire (deviner plus souvent les coups qui
 *    lui rapporteraient le plus).
 * Comme minimax@1, une capture du roi adverse n'est jouee que si elle est forcee, et une capture
 * de son propre roi est toujours devinee (voir Session.guess).
 *
 * Recherche par approfondissement iteratif : les profondeurs 1..config.depth() sont resolues
 * tour a tour, le dernier resultat complet est garde si le budget de temps est depasse
 * (la pendule de l'ordinateur tourne pendant qu'il reflechit).
 */
public final class GuessAwareAgent implements GuessAgent {

    /**
     * @param depth profondeur maximale visee (plis)
     * @param guessPlies nombre de plis, depuis la racine, resolus comme jeu matriciel (voir GuessAwareSearch)
     * @param budgetMillis budget de temps par decision (approfondissement iteratif)
     */
    public record Config(int depth, int guessPlies, long budgetMillis) {
        public static Config forLevel(ComputerLevel level) {
            return switch (level) {
                case EASY -> new Config(2, 1, 500);
                case MEDIUM -> new Config(3, 1, 1_000);
                case HARD -> new Config(3, 2, 2_000);
            };
        }
    }

    private final AgentId id;
    private final Config config;

    public GuessAwareAgent(AgentId id, Config config) {
        this.id = id;
        this.config = config;
    }

    @Override
    public AgentId id() {
        return id;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public AgentSession newSession(AgentSessionContext context) {
        return new Session(context);
    }

    /** Toute la memoire vit dans la strategie mixte : rien a retenir d'un round a l'autre. */
    private final class Session implements AgentSession {

        private final AgentSessionContext context;
        private final Rules rules;

        Session(AgentSessionContext context) {
            this.context = context;
            this.rules = Rules.forVariant(context.variant());
        }

        @Override
        public void onRoundResolved(RoundResult lastRound) {
        }

        @Override
        public Move move(Board board, List<Move> legalMoves) {
            List<Move> kingCaptures = legalMoves.stream().filter(GuessAwareAgent::capturesKing).toList();
            if (kingCaptures.size() == legalMoves.size()) {
                return pick(kingCaptures);
            }
            // Jamais "gratuite" tant qu'une alternative existe : le mover n'est pas en echec,
            // une devinette correcte l'annule simplement (voir EngineBackedAgent).
            return sample(board, legalMoves, RootMove::probability, kingCaptures);
        }

        @Override
        public Move guess(Board board, List<Move> opponentLegalMoves) {
            List<Move> ownKingCaptures = opponentLegalMoves.stream()
                    .filter(m -> capturesKing(m) && m.capturedPiece().color() == context.color())
                    .toList();
            if (!ownKingCaptures.isEmpty()) {
                return pick(ownKingCaptures);
            }
            return sample(board, opponentLegalMoves, RootMove::guessProbability, List.of());
        }

        private Move pick(List<Move> moves) {
            return moves.get(context.rng().nextInt(moves.size()));
        }

        /**
         * Tire un coup de legalMoves selon weight (probabilite de RootMove), hors excluded,
         * en repartissant le poids restant. Poids tous nuls (ne devrait pas arriver) : le coup
         * au meilleur score.
         */
        private Move sample(Board board, List<Move> legalMoves, ToDoubleFunction<RootMove> weight, List<Move> excluded) {
            RootSolution solution = solve(board);
            List<RootMove> candidates = new ArrayList<>();
            for (RootMove rootMove : solution.moves()) {
                if (legalMoves.contains(rootMove.move()) && !excluded.contains(rootMove.move())) {
                    candidates.add(rootMove);
                }
            }
            double total = candidates.stream().mapToDouble(weight).sum();
            if (total <= 1e-12) {
                return resolve(candidates.stream().max(Comparator.comparingInt(RootMove::playedScore))
                        .orElseThrow().move(), legalMoves);
            }
            double roll = context.rng().nextDouble() * total;
            double cumulative = 0;
            for (RootMove candidate : candidates) {
                cumulative += weight.applyAsDouble(candidate);
                if (roll < cumulative) {
                    return resolve(candidate.move(), legalMoves);
                }
            }
            return resolve(candidates.get(candidates.size() - 1).move(), legalMoves);
        }

        /** Approfondissement iteratif : garde le dernier resultat complet si le budget est depasse. */
        private RootSolution solve(Board board) {
            long deadline = System.nanoTime() + config.budgetMillis() * 1_000_000L;
            RootSolution best = new GuessAwareSearch(rules).solveRoot(board, 1, 1);
            for (int depth = 2; depth <= config.depth(); depth++) {
                try {
                    best = new GuessAwareSearch(rules).withDeadline(deadline)
                            .solveRoot(board, depth, Math.min(config.guessPlies(), depth));
                } catch (SearchTimeoutException timeout) {
                    break;
                }
            }
            return best;
        }

        /** La recherche regenere ses coups : on rend l'instance de legalMoves (meme valeur, Move est un record). */
        private Move resolve(Move candidate, List<Move> legalMoves) {
            return legalMoves.stream().filter(candidate::equals).findFirst().orElse(candidate);
        }
    }

    private static boolean capturesKing(Move move) {
        return move.isCapture() && move.capturedPiece().type() == PieceType.KING;
    }
}
