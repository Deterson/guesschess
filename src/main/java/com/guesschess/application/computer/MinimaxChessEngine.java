package com.guesschess.application.computer;

import com.guesschess.application.computer.NegamaxSearch.ScoredMove;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.MoveGenerator;

import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Implementation maison du port ChessEngine (etape 17 de la roadmap, voir la note de
 * conception qui a precede cette classe) - remplace StockfishChessEngine derriere la
 * meme interface, sans process externe ni protocole UCI : NegamaxSearch fait toute la
 * recherche, cette classe se contente de choisir la profondeur par niveau et d'appliquer
 * les memes regles de selection que StockfishChessEngine (exclusion "si possible" des
 * coups a eviter, aleatoire pondere en facile). Enregistre comme agent minimax@1 (etape 20, voir
 * BuiltInAgents) ; StockfishChessEngine reste selectionnable (stockfish@1) plutot que retire.
 */
public class MinimaxChessEngine implements ChessEngine {

    private final RandomGenerator rng;

    /** rng : seule source de hasard (facile, voir pickByRankWeight) - graine fixe = choix rejouables. */
    public MinimaxChessEngine(RandomGenerator rng) {
        this.rng = rng;
    }

    public MinimaxChessEngine() {
        this(RandomGeneratorFactory.of("L64X128MixRandom").create());
    }

    /**
     * Profondeur de recherche par niveau (etape 17) - remplace UCI_LimitStrength/UCI_Elo,
     * specifiques a Stockfish, par un levier propre a ce moteur. Facile reste affaibli
     * par un choix aleatoire pondere parmi les 3 meilleurs coups (pickByRankWeight),
     * comme le faisait deja StockfishChessEngine, pas seulement par la profondeur.
     */
    private record EngineSettings(int depth) {
        static EngineSettings forLevel(ComputerLevel level) {
            return switch (level) {
                case EASY -> new EngineSettings(2);
                case MEDIUM -> new EngineSettings(3);
                case HARD -> new EngineSettings(4);
            };
        }
    }

    /** Aucune dependance externe (pas de process, pas de binaire) - toujours disponible. */
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Move chooseMove(Board board, List<Move> legalMoves, ComputerLevel level, Set<Move> movesToAvoidIfPossible) {
        if (legalMoves.isEmpty()) {
            throw new IllegalArgumentException("no legal move to choose from");
        }
        int depth = EngineSettings.forLevel(level).depth();
        List<ScoredMove> scored = NegamaxSearch.searchRoot(board, depth);
        List<ScoredMove> withoutAvoidableKingCapture = excludeKingCaptureUnlessForced(scored);
        List<ScoredMove> preferred = excludeIfAlternativeExists(withoutAvoidableKingCapture, movesToAvoidIfPossible);
        Move chosen = switch (level) {
            case EASY -> pickByRankWeight(preferred);
            case MEDIUM -> preferred.get(0).move();
            case HARD -> pickPreferringGuessableRefutation(board, preferred);
        };
        return resolveAgainstLegalMoves(chosen, legalMoves);
    }

    /**
     * Une capture du roi adverse n'est un coup "gratuit" que si c'est le seul coup legal
     * disponible (voir ComputerPlayerService.chooseMoveOrFallback, meme raisonnement) -
     * sinon quasi certaine d'etre devinee : le mover n'est pas lui-meme en echec, donc une
     * devinette correcte l'annule juste normalement (Game.resolveRound) plutot que de
     * declencher une issue immediate, rendant le trait au devineur sans rien lui avoir
     * coute. Exclue des candidats des qu'une alternative existe, quel que soit son score
     * classique (toujours le plus haut possible, NegamaxSearch.KING_CAPTURE_SCORE) -
     * sinon systematiquement choisie malgre sa previsibilite. NegamaxSearch regenerant
     * ses propres coups depuis board (voir searchRoot), cette exclusion doit se faire ici
     * et pas seulement cote appelant (ComputerPlayerService) pour etre effective.
     */
    private static List<ScoredMove> excludeKingCaptureUnlessForced(List<ScoredMove> scored) {
        List<ScoredMove> withoutKingCapture = scored.stream()
                .filter(sm -> !isKingCapture(sm.move()))
                .toList();
        return withoutKingCapture.isEmpty() ? scored : withoutKingCapture;
    }

    private static boolean isKingCapture(Move move) {
        return move.isCapture() && move.capturedPiece().type() == PieceType.KING;
    }

    /**
     * Ecart de score (centipawns) au-dela duquel une reponse adverse est consideree
     * "clairement meilleure" que les autres - meme convention et meme valeur que
     * StockfishChessEngine.GAP_THRESHOLD_CP (un pion = 100).
     */
    private static final int GAP_THRESHOLD_CP = 150;

    /**
     * Profondeur de la recherche auxiliaire qui verifie si un coup candidat expose une
     * refutation "evidente" (voir pickPreferringGuessableRefutation) - volontairement
     * faible et independante de la profondeur du niveau choisi : appelee une fois par
     * candidat proche du meilleur, un ordre de grandeur suffit a mesurer l'ecart entre
     * les deux meilleures reponses adverses, pas la peine de rechercher aussi profond
     * que la decision principale.
     */
    private static final int REFUTATION_CHECK_DEPTH = 2;

    /**
     * Strategie 1 (etape 17, difficile uniquement) : un minimax classique suppose que
     * l'adversaire joue toujours sa meilleure reponse - mais si cette reponse est
     * nettement meilleure que ses alternatives ("evidente"), c'est aussi la devinette la
     * plus probable de notre propre camp au round suivant, qui l'annulerait alors. Le
     * pire cas reel est donc meilleur que ce que le score classique suppose : parmi les
     * candidats deja proches du meilleur coup classique (a GAP_THRESHOLD_CP pres), on
     * prefere celui qui expose une telle refutation plutot que de s'en detourner.
     * Applique identiquement que ce moteur choisisse son propre coup ou devine celui de
     * l'adversaire (ChessEngine pose la meme question dans les deux cas, voir
     * ComputerPlayerService) - sans effet indesirable cote devinette, puisque tous les
     * candidats consideres ici sont deja proches du meilleur coup classique.
     */
    static Move pickPreferringGuessableRefutation(Board board, List<ScoredMove> candidates) {
        ScoredMove classicalBest = candidates.get(0);
        List<ScoredMove> nearBest = candidates.stream()
                .filter(c -> classicalBest.score() - c.score() <= GAP_THRESHOLD_CP)
                .toList();
        for (ScoredMove candidate : nearBest) {
            if (exposesGuessableRefutation(board, candidate.move())) {
                return candidate.move();
            }
        }
        return classicalBest.move();
    }

    /**
     * Vrai si, apres move, l'adversaire dispose d'une reponse nettement meilleure que sa
     * deuxieme meilleure option (voir GAP_THRESHOLD_CP) - donc une reponse plausible a
     * deviner et annuler. Un mat force adverse n'est jamais mis de cote de cette facon
     * (toujours une vraie menace, quel que soit l'ecart avec la 2e meilleure option).
     */
    static boolean exposesGuessableRefutation(Board board, Move move) {
        Board afterMove = board.applyMove(move);
        Color opponent = afterMove.sideToMove();
        if (!MoveGenerator.hasAnyLegalMove(afterMove, opponent)) {
            return false;
        }
        List<ScoredMove> replies = NegamaxSearch.searchRoot(afterMove, REFUTATION_CHECK_DEPTH);
        if (replies.size() < 2) {
            return false;
        }
        ScoredMove best = replies.get(0);
        if (NegamaxSearch.isMateScore(best.score())) {
            return false;
        }
        ScoredMove second = replies.get(1);
        return best.score() - second.score() > GAP_THRESHOLD_CP;
    }

    /**
     * Meme contrat que StockfishChessEngine.excludeIfAlternativeExists : retire les
     * coups presents dans movesToAvoidIfPossible, sauf si ca viderait completement la
     * liste (coup force, ou tous les candidats restants sont a eviter) - "si possible"
     * dans le nom du parametre. Comparaison par egalite de valeur (Move est un record) :
     * contrairement a Stockfish, ce moteur ne passe jamais par une notation UCI
     * intermediaire, les Move sont deja les memes objets structurels des deux cotes.
     */
    private static List<ScoredMove> excludeIfAlternativeExists(List<ScoredMove> scored, Set<Move> movesToAvoidIfPossible) {
        if (movesToAvoidIfPossible.isEmpty()) {
            return scored;
        }
        List<ScoredMove> filtered = scored.stream()
                .filter(sm -> !movesToAvoidIfPossible.contains(sm.move()))
                .toList();
        return filtered.isEmpty() ? scored : filtered;
    }

    /**
     * Facile : choix aleatoire pondere vers les meilleurs coups plutot que toujours le
     * meilleur - meme ponderation que StockfishChessEngine.pickByRankWeight, pour un
     * comportement comparable entre les deux moteurs a ce niveau.
     */
    private Move pickByRankWeight(List<ScoredMove> scored) {
        if (scored.size() == 1) {
            return scored.get(0).move();
        }
        double[] weights = {0.5, 0.3, 0.2};
        double roll = rng.nextDouble();
        double cumulative = 0;
        for (int i = 0; i < scored.size(); i++) {
            cumulative += i < weights.length ? weights[i] : 0;
            if (roll < cumulative) {
                return scored.get(i).move();
            }
        }
        return scored.get(0).move();
    }

    /**
     * NegamaxSearch regenere ses propres coups legaux (MoveGenerator) plutot que de
     * reutiliser legalMoves - memes valeurs par construction (record, meme position),
     * mais on retombe sur l'instance de legalMoves par precaution, comme
     * StockfishChessEngine.matchLegalMove le fait pour sa reponse UCI.
     */
    private static Move resolveAgainstLegalMoves(Move candidate, List<Move> legalMoves) {
        return legalMoves.stream().filter(candidate::equals).findFirst().orElse(candidate);
    }
}
