package com.guesschess.application.computer;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache des valeurs deja prouvees par NegamaxSearch/GuessAwareSearch pour un noeud donne
 * (etape 24) - jamais utilisee pour reordonner les coups, seulement pour eviter de
 * recalculer une valeur deja connue : algorithme standard alpha-beta + table de
 * transposition (utilise par tout moteur alpha-beta serieux), provablement equivalent en
 * valeur retournee a une recherche sans table, seulement plus rapide en presence de
 * transpositions.
 *
 * Creee et jetee a chaque appel racine (searchRoot/searchRootWithDeadline/solveRoot)
 * plutot que partagee entre agents ou entre parties : le tournoi (etape 22) lance des
 * parties en parallele (--threads), une table par appel evite toute question de
 * concurrence sans avoir a ecrire de politique de remplacement/bornage de taille (HashMap
 * simple, jetee avec l'appel qui l'a creee - sa taille reste bornee par le nombre de
 * noeuds distincts visites par CET appel, pas un cache qui grossirait indefiniment).
 *
 * Cle = hash Zobrist (ZobristHash) seul : une collision (deux positions distinctes au meme
 * hash 64 bits) est acceptee comme risque negligeable (~1/2^64), comme dans tout moteur a
 * base de Zobrist - une verification supplementaire serait sur-ingenierer un cas dont la
 * probabilite pratique est nulle.
 */
public final class TranspositionTable {

    public enum Bound {
        EXACT, LOWERBOUND, UPPERBOUND
    }

    public record Entry(int depth, int score, Bound bound) {
    }

    private final Map<Long, Entry> entries = new HashMap<>();

    /** Compteurs pour le benchmark (etape 24) - jamais consultes par la recherche elle-meme. */
    private long probes;
    private long hits;

    /**
     * @return un score directement utilisable a la place d'une recherche, si une entree
     * existe a profondeur suffisante ET coherente avec la fenetre alpha/beta courante
     * (LOWERBOUND seulement si deja >= beta, UPPERBOUND seulement si deja <= alpha) -
     * sinon null, une recherche normale reste necessaire.
     */
    public Integer probe(long hash, int depth, int alpha, int beta) {
        probes++;
        Entry entry = entries.get(hash);
        if (entry == null || entry.depth() < depth) {
            return null;
        }
        Integer result = switch (entry.bound()) {
            case EXACT -> entry.score();
            case LOWERBOUND -> entry.score() >= beta ? entry.score() : null;
            case UPPERBOUND -> entry.score() <= alpha ? entry.score() : null;
        };
        if (result != null) {
            hits++;
        }
        return result;
    }

    public long probes() {
        return probes;
    }

    public long hits() {
        return hits;
    }

    /** Remplace uniquement si l'entree existante est a profondeur inferieure ou absente (garde la plus fiable). */
    public void store(long hash, int depth, int score, Bound bound) {
        Entry existing = entries.get(hash);
        if (existing == null || existing.depth() <= depth) {
            entries.put(hash, new Entry(depth, score, bound));
        }
    }
}
