package com.guesschess.application.computer;

import com.guesschess.application.computer.NegamaxSearch.ScoredMove;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.PieceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent "negamax-timed@1" (etape 22, tournoi) : meme recherche que minimax@1
 * (NegamaxSearch/PositionEvaluator), mais approfondissement iteratif borne par un budget
 * de temps plutot qu'une profondeur fixe par ComputerLevel - un budget "profondeur/temps
 * par coup" configurable independamment des niveaux EASY/MEDIUM/HARD (voir Config),
 * demande par le tournoi de l'etape 22. Volontairement plus simple que minimax@1 : pas de
 * recherche active de fast_mate, pas de strategie "eviter une refutation devinable", pas
 * de memoire de coups bloques - un moteur classique nu, utile comme point de comparaison
 * isole de ces heuristiques. minimax@1 reste inchange (gele, golden tests) : cet agent est
 * une nouvelle version separee, jamais un remplacement.
 *
 * Le controle du budget se fait ENTRE deux profondeurs completes (jamais a l'interieur de
 * NegamaxSearch, qui reste tel quel, zero risque pour son comportement fige) : peut donc
 * legerement depasser budgetMillis sur la derniere iteration, acceptable pour un outil de
 * mesure hors production.
 */
public final class NegamaxTimedAgent implements GuessAgent {

    /**
     * @param maxDepth     profondeur maximale visee (plis), plafond de l'approfondissement iteratif
     * @param budgetMillis budget de temps par decision ; verifie seulement entre deux
     *                     profondeurs completes, jamais a l'interieur d'une profondeur
     */
    public record Config(int maxDepth, long budgetMillis) {
        public Config {
            if (maxDepth < 1) {
                throw new IllegalArgumentException("maxDepth must be >= 1, got " + maxDepth);
            }
            if (budgetMillis < 1) {
                throw new IllegalArgumentException("budgetMillis must be >= 1, got " + budgetMillis);
            }
        }

        public static Config forLevel(ComputerLevel level) {
            return switch (level) {
                case EASY -> new Config(3, 500);
                case MEDIUM -> new Config(5, 1_000);
                case HARD -> new Config(7, 2_000);
            };
        }
    }

    private final AgentId id;
    private final Config config;

    public NegamaxTimedAgent(AgentId id, Config config) {
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

    private final class Session implements AgentSession {

        private final AgentSessionContext context;

        Session(AgentSessionContext context) {
            this.context = context;
        }

        @Override
        public void onRoundResolved(RoundResult lastRound) {
        }

        @Override
        public Move move(Board board, List<Move> legalMoves) {
            List<Move> ownKingCaptures = kingCaptures(legalMoves, move -> true);
            if (ownKingCaptures.size() == legalMoves.size()) {
                return ownKingCaptures.get(context.rng().nextInt(ownKingCaptures.size()));
            }
            // Jamais "gratuite" tant qu'une alternative existe, meme raisonnement que
            // EngineBackedAgent : le mover n'est pas en echec, une devinette correcte
            // l'annule juste normalement au lieu de couter quoi que ce soit a l'adversaire.
            List<Move> candidates = ownKingCaptures.isEmpty()
                    ? legalMoves
                    : legalMoves.stream().filter(m -> !ownKingCaptures.contains(m)).toList();
            return think(board, candidates, legalMoves);
        }

        @Override
        public Move guess(Board board, List<Move> opponentLegalMoves) {
            // Une capture du propre roi de l'agent est toujours devinee (meme raisonnement
            // que EngineBackedAgent.guessKingCaptureOrFallback) : sans ca, un round annule qui
            // laisse ce roi en echec non resolu ne serait jamais devine, alors que c'est la
            // seule devinette qui sauve la partie.
            List<Move> ownKingCaptures = kingCaptures(opponentLegalMoves, m -> m.capturedPiece().color() == context.color());
            if (!ownKingCaptures.isEmpty()) {
                return ownKingCaptures.get(context.rng().nextInt(ownKingCaptures.size()));
            }
            return think(board, opponentLegalMoves, opponentLegalMoves);
        }

        private List<Move> kingCaptures(List<Move> moves, java.util.function.Predicate<Move> extra) {
            List<Move> result = new ArrayList<>();
            for (Move move : moves) {
                if (move.isCapture() && move.capturedPiece().type() == PieceType.KING && extra.test(move)) {
                    result.add(move);
                }
            }
            return result;
        }

        /**
         * Approfondissement iteratif jusqu'a config.maxDepth() ou config.budgetMillis(), le
         * premier atteint - la deadline est verifiee a l'INTERIEUR de chaque profondeur
         * (searchRootWithDeadline), pas seulement entre deux profondeurs completes : sans ca,
         * une seule profondeur peut a elle seule largement depasser le budget. Une seule
         * TranspositionTable (etape 24) partagee entre toutes les profondeurs de cette
         * decision : les profondeurs courtes deja resolues accelerent les profondeurs
         * suivantes des qu'une transposition est retrouvee, sans changer aucun score.
         */
        private Move think(Board board, List<Move> candidates, List<Move> legalMoves) {
            if (candidates.size() == 1) {
                return candidates.get(0);
            }
            TranspositionTable tt = new TranspositionTable();
            long deadline = System.nanoTime() + config.budgetMillis() * 1_000_000L;
            List<ScoredMove> best = NegamaxSearch.searchRoot(board, 1, tt);
            for (int depth = 2; depth <= config.maxDepth() && System.nanoTime() < deadline; depth++) {
                try {
                    best = NegamaxSearch.searchRootWithDeadline(board, depth, deadline, tt);
                } catch (NegamaxSearch.SearchTimeoutException timeout) {
                    break;
                }
            }
            for (ScoredMove scored : best) {
                if (candidates.contains(scored.move())) {
                    return resolve(scored.move(), legalMoves);
                }
            }
            return candidates.get(context.rng().nextInt(candidates.size()));
        }

        private Move resolve(Move candidate, List<Move> legalMoves) {
            return legalMoves.stream().filter(candidate::equals).findFirst().orElse(candidate);
        }
    }
}
