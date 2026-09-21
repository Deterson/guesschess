package com.guesschess.infrastructure.computer;

import com.guesschess.application.computer.ChessEngine;
import com.guesschess.application.computer.ComputerLevel;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.PieceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Adaptateur infrastructure du port ChessEngine (etape 15 de la roadmap) : pilote un
 * binaire Stockfish externe via le protocole texte UCI (Universal Chess Interface) sur
 * stdin/stdout d'un process. Un process est lance, interroge, puis tue pour chaque
 * appel a chooseMove plutot que garde "chaud" entre deux coups - un process UCI ne
 * repond qu'a une recherche a la fois, donc plusieurs parties contre l'ordinateur en
 * parallele ont de toute facon besoin d'un process chacune ; le cout de demarrage
 * (~50-150ms) est negligeable face au temps de recherche (voir EngineSettings) et
 * evite toute la complexite d'un pool de process partages entre requetes concurrentes.
 *
 * guesschess.stockfish.path (variable d'environnement STOCKFISH_PATH) localise le
 * binaire - absent/vide en dev local si Stockfish n'est pas installe : ne bloque pas le
 * demarrage de l'application (contrairement aux variables obligatoires, voir
 * CLAUDE.md), seule la creation d'une partie contre l'ordinateur echoue alors
 * (StockfishUnavailableException, voir GameCreationController).
 *
 * Enregistre comme agent stockfish@1 (etape 20, voir ComputerAgentsConfiguration), selectionnable
 * par niveau (guesschess.engine.easy/medium/hard=stockfish@1) ; toujours instancie (bean), mais
 * inerte tant qu'aucun niveau ne le choisit - isAvailable() reste faux sans STOCKFISH_PATH.
 */
@Component
public class StockfishChessEngine implements ChessEngine {

    private final String stockfishPath;

    public StockfishChessEngine(@Value("${guesschess.stockfish.path:}") String stockfishPath) {
        this.stockfishPath = stockfishPath;
    }

    /**
     * Parametres UCI par niveau (etape 15, valeurs validees avec l'utilisateur) :
     * Facile ~ Elo 800-1000 effectif (l'engin ne descend nativement qu'a ~1320,
     * l'ecart est comble par un choix aleatoire pondere parmi les meilleurs coups via
     * MultiPV plutot qu'un livre d'ouverture - voir pickByRankWeight) ; Moyen ~ Elo
     * 1500 ; Difficile = pleine puissance, sans limite d'Elo, mais MultiPV > 1 lui
     * aussi depuis le constat qu'un coup toujours objectivement optimal est trop facile
     * a deviner dans guesschess - voir pickAvoidingObviousBest, raison differente de
     * celle du niveau facile (eviter la previsibilite, pas affaiblir). movetimeMillis
     * borne le cout CPU (Pi partage entre plusieurs parties potentiellement simultanees
     * contre l'ordinateur) autant qu'il affaiblit le niveau facile/moyen.
     */
    private record EngineSettings(boolean limitStrength, int elo, int multiPv, int movetimeMillis) {
        static EngineSettings forLevel(ComputerLevel level) {
            return switch (level) {
                case EASY -> new EngineSettings(true, 1320, 3, 300);
                case MEDIUM -> new EngineSettings(true, 1500, 1, 600);
                case HARD -> new EngineSettings(false, 0, HARD_MULTIPV, 1500);
            };
        }
    }

    /** Nombre de lignes MultiPV demandees en difficile - voir pickAvoidingObviousBest. */
    private static final int HARD_MULTIPV = 5;

    /**
     * Ecart de score (centipawns) au-dela duquel un coup est considere "clairement
     * meilleur" que les autres - voir pickAvoidingObviousBest. Valeur arbitraire, a
     * ajuster empiriquement (un pion = 100).
     */
    private static final int GAP_THRESHOLD_CP = 150;

    /**
     * Valeur de score equivalente attribuee a un mat force pour le comparer aux scores
     * en centipawns (voir effectiveCp) - grande devant n'importe quel score cp normal
     * pour qu'un mat l'emporte toujours dans un tri/ecart, tout en preservant l'ordre
     * entre mats de distances differentes.
     */
    private static final int MATE_SCORE_MAGNITUDE = 100_000;

    /**
     * Verification legere (pas de process lance) : le chemin est configure et pointe
     * vers un fichier executable. Ne garantit pas que le binaire repondra bien au
     * protocole UCI (seul un vrai appel a chooseMove le prouve), mais suffit a
     * detecter les cas les plus courants (STOCKFISH_PATH absent, ou mal configure)
     * avant meme de creer la partie - voir GameLifecycleService.createComputerGame.
     */
    @Override
    public boolean isAvailable() {
        if (stockfishPath == null || stockfishPath.isBlank()) {
            return false;
        }
        try {
            return Files.isExecutable(Path.of(stockfishPath));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Move chooseMove(Board board, List<Move> legalMoves, ComputerLevel level, Set<Move> movesToAvoidIfPossible) {
        if (legalMoves.isEmpty()) {
            throw new IllegalArgumentException("no legal move to choose from");
        }
        if (stockfishPath == null || stockfishPath.isBlank()) {
            throw new StockfishUnavailableException("STOCKFISH_PATH is not configured");
        }
        EngineSettings settings = EngineSettings.forLevel(level);
        List<Candidate> candidates = searchWithRetry(board, settings);
        List<Candidate> preferred = excludeIfAlternativeExists(candidates, movesToAvoidIfPossible);
        String chosenUci = pickAmongCandidates(preferred, level);
        return matchLegalMove(chosenUci, legalMoves);
    }

    /**
     * Retire des candidats ceux presents dans movesToAvoidIfPossible (comparaison par
     * origine/destination/promotion via leur notation UCI, pas egalite complete de
     * Move - voir ChessEngine.chooseMove et ComputerPlayerService.BlockedMove). Ne vide
     * jamais completement la liste : si l'exclusion ne laissait plus aucun candidat
     * (coup force, ou toutes les lignes MultiPV exploreees sont a eviter), on ignore
     * l'exclusion plutot que d'echouer - "si possible" dans le nom du parametre.
     */
    private static List<Candidate> excludeIfAlternativeExists(List<Candidate> candidates, Set<Move> movesToAvoidIfPossible) {
        if (movesToAvoidIfPossible.isEmpty()) {
            return candidates;
        }
        Set<String> avoidUci = movesToAvoidIfPossible.stream()
                .map(StockfishChessEngine::toUci)
                .collect(Collectors.toSet());
        List<Candidate> filtered = candidates.stream()
                .filter(c -> !avoidUci.contains(c.uci()))
                .toList();
        return filtered.isEmpty() ? candidates : filtered;
    }

    private static String toUci(Move move) {
        String promotion = move.promotionType() == null ? "" : String.valueOf(promotionLetter(move.promotionType()));
        return move.from().toAlgebraic() + move.to().toAlgebraic() + promotion;
    }

    private static char promotionLetter(PieceType type) {
        return switch (type) {
            case QUEEN -> 'q';
            case ROOK -> 'r';
            case BISHOP -> 'b';
            case KNIGHT -> 'n';
            default -> throw new IllegalArgumentException("not a promotion piece type: " + type);
        };
    }

    private static final int SEARCH_ATTEMPTS = 2;

    /**
     * Un echec de communication UCI (ex. le process ferme stdout avant "bestmove") est
     * observe occasionnellement en dev local sans cause identifiee (probablement le
     * lancement/l'arret d'un process par appel, voir javadoc de la classe, qui se prete
     * a une interference ponctuelle de l'antivirus ou de l'OS) - une nouvelle tentative
     * avec un process frais resout la grande majorite des cas. Sans ce filet, l'echec
     * remontait jusqu'a ComputerPlayerService.act qui l'avalait silencieusement,
     * laissant la partie bloquee indefiniment (l'ordinateur ne soumettant jamais son
     * coup/sa devinette).
     */
    private List<Candidate> searchWithRetry(Board board, EngineSettings settings) {
        StockfishUnavailableException lastFailure = null;
        for (int attempt = 1; attempt <= SEARCH_ATTEMPTS; attempt++) {
            try {
                return search(board, settings);
            } catch (StockfishUnavailableException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    /**
     * Un coup candidat issu d'une ligne MultiPV : coup en notation UCI longue
     * algebrique (ex. "e2e4", "e7e8q"), score converti en equivalent centipawns
     * (null si non disponible - voir effectiveCp), et indicateur de mat force en notre
     * faveur (prioritaire sur toute comparaison de score - voir pickAvoidingObviousBest).
     */
    private record Candidate(String uci, Integer effectiveCp, boolean forcedMate) {}

    /**
     * @return les coups candidats ordonnes par rang MultiPV (index 0 = meilleur coup de
     * l'engin, issu de "bestmove"), suivi des alternatives (uniquement si
     * settings.multiPv() > 1) - peut contenir moins d'elements que multiPv si l'engin
     * n'a pas eu le temps d'explorer toutes les lignes demandees, ou si la position a
     * moins de coups legaux que multiPv (Stockfish borne alors lui-meme le nombre de
     * lignes).
     */
    private List<Candidate> search(Board board, EngineSettings settings) {
        ProcessBuilder builder = new ProcessBuilder(stockfishPath).redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new StockfishUnavailableException("failed to start stockfish at " + stockfishPath, e);
        }
        try {
            return runUciSession(process, board, settings);
        } finally {
            process.destroyForcibly();
        }
    }

    private List<Candidate> runUciSession(Process process, Board board, EngineSettings settings) {
        try (Writer stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.US_ASCII);
             BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.US_ASCII))) {

            long deadline = System.currentTimeMillis() + settings.movetimeMillis() + 5000;

            send(stdin, "uci");
            awaitLine(stdout, "uciok", deadline);

            send(stdin, "setoption name UCI_LimitStrength value " + settings.limitStrength());
            if (settings.limitStrength()) {
                send(stdin, "setoption name UCI_Elo value " + settings.elo());
            }
            send(stdin, "setoption name MultiPV value " + settings.multiPv());

            send(stdin, "isready");
            awaitLine(stdout, "readyok", deadline);

            send(stdin, "position fen " + board.toFen());
            send(stdin, "go movetime " + settings.movetimeMillis());

            return awaitBestMove(stdout, settings.multiPv(), deadline);
        } catch (IOException e) {
            throw new StockfishUnavailableException("stockfish UCI communication failed", e);
        }
    }

    private void send(Writer stdin, String command) throws IOException {
        stdin.write(command);
        stdin.write('\n');
        stdin.flush();
    }

    private void awaitLine(BufferedReader stdout, String expected, long deadline) throws IOException {
        readWithDeadline(deadline, () -> {
            String line;
            while ((line = stdout.readLine()) != null) {
                if (line.trim().equals(expected)) {
                    return null;
                }
            }
            throw new StockfishUnavailableException("stockfish closed its output before sending '" + expected + "'");
        });
    }

    /**
     * Lit les lignes "info ... multipv K ... score cp|mate X ... pv <coup> ..." (la
     * derniere par index K l'emporte, la profondeur ne fait qu'augmenter) jusqu'a
     * "bestmove <coup> ...", qui fait autorite pour le coup de l'index 1 (toujours
     * coherent avec la derniere ligne multipv 1, mais bestmove reste la source de
     * verite UCI) - son score est en revanche perdu s'il n'a ete vu sur aucune ligne
     * "info" (cas rare, recherche trop courte), auquel cas ce candidat garde un score
     * null. L'ordre du resultat suit l'index MultiPV (1 = meilleur), pas l'ordre
     * d'arrivee des lignes.
     */
    private List<Candidate> awaitBestMove(BufferedReader stdout, int multiPv, long deadline) throws IOException {
        Map<Integer, Candidate> byMultiPv = new HashMap<>();
        return readWithDeadline(deadline, () -> {
            String line;
            while ((line = stdout.readLine()) != null) {
                if (line.startsWith("bestmove")) {
                    String[] tokens = line.trim().split("\\s+");
                    if (tokens.length < 2 || "(none)".equals(tokens[1])) {
                        throw new StockfishUnavailableException("stockfish returned no move: " + line);
                    }
                    byMultiPv.putIfAbsent(1, new Candidate(tokens[1], null, false));
                    List<Candidate> ordered = new ArrayList<>();
                    for (int i = 1; i <= multiPv; i++) {
                        Candidate candidate = byMultiPv.get(i);
                        if (candidate != null) {
                            ordered.add(candidate);
                        }
                    }
                    return ordered;
                }
                if (multiPv > 1 && line.startsWith("info") && line.contains(" pv ")) {
                    parseMultiPvLine(line, byMultiPv);
                }
            }
            throw new StockfishUnavailableException("stockfish closed its output before sending 'bestmove'");
        });
    }

    private void parseMultiPvLine(String line, Map<Integer, Candidate> byMultiPv) {
        String[] tokens = line.trim().split("\\s+");
        Integer index = null;
        String move = null;
        Integer cp = null;
        Integer mate = null;
        for (int i = 0; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "multipv" -> { if (i + 1 < tokens.length) index = Integer.parseInt(tokens[i + 1]); }
                case "pv" -> { if (i + 1 < tokens.length) move = tokens[i + 1]; }
                case "cp" -> { if (i + 1 < tokens.length) cp = Integer.parseInt(tokens[i + 1]); }
                case "mate" -> { if (i + 1 < tokens.length) mate = Integer.parseInt(tokens[i + 1]); }
                default -> { }
            }
        }
        if (index != null && move != null) {
            byMultiPv.put(index, new Candidate(move, effectiveCp(cp, mate), mate != null && mate > 0));
        }
    }

    /**
     * Convertit un score UCI (cp OU mate, jamais les deux) en une seule echelle
     * comparable : un mat en notre faveur (mate > 0) devient une valeur enorme (plus le
     * mat est proche, plus elle est grande) pour toujours l'emporter sur un score cp
     * normal ; un mat qui nous est inflige (mate < 0) devient tres negatif de la meme
     * facon. Retourne null si ni cp ni mate n'etaient presents sur la ligne (recherche
     * trop courte).
     */
    private static Integer effectiveCp(Integer cp, Integer mate) {
        if (mate != null) {
            return mate > 0 ? MATE_SCORE_MAGNITUDE - mate : -MATE_SCORE_MAGNITUDE - mate;
        }
        return cp;
    }

    private interface ThrowingSupplier<T> {
        T get() throws IOException;
    }

    private <T> T readWithDeadline(long deadline, ThrowingSupplier<T> reader) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
            throw new StockfishUnavailableException("stockfish timed out");
        }
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return reader.get();
            } catch (IOException e) {
                throw new StockfishUnavailableException("stockfish UCI communication failed", e);
            }
        });
        try {
            return future.get(remaining, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new StockfishUnavailableException("stockfish timed out", e);
        } catch (Exception e) {
            throw new StockfishUnavailableException("stockfish UCI communication failed", e);
        }
    }

    private String pickAmongCandidates(List<Candidate> candidates, ComputerLevel level) {
        if (candidates.size() <= 1) {
            return candidates.get(0).uci();
        }
        return level == ComputerLevel.HARD
                ? pickAvoidingObviousBest(candidates)
                : pickByRankWeight(candidates);
    }

    /**
     * Facile (multiPv > 1) : choix aleatoire pondere vers les premiers candidats
     * (voir EngineSettings) plutot que toujours le meilleur coup - c'est ce qui pousse
     * la force effective sous le plancher natif de UCI_Elo (~1320) et introduit une
     * inconsistance "humaine" plutot qu'un simple affaiblissement uniforme. Moyen
     * (multiPv == 1) : un seul candidat, jamais appelee.
     */
    private String pickByRankWeight(List<Candidate> candidates) {
        double[] weights = {0.5, 0.3, 0.2};
        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += i < weights.length ? weights[i] : 0;
            if (roll < cumulative) {
                return candidates.get(i).uci();
            }
        }
        return candidates.get(0).uci();
    }

    /**
     * Difficile uniquement : Stockfish reste tres fort mais ne doit plus etre
     * systematiquement previsible en jouant toujours objectivement le meilleur coup -
     * facile a deviner pour l'adversaire, ce qui annule le coup ET lui donne le trait
     * (voir Game.resolveRound), un resultat strictement pire pour l'ordinateur que
     * jouer un coup legerement plus faible mais non devine. Decision basee sur l'ecart
     * de score (voir effectiveCp) entre le meilleur coup (PV1) et le second (PV2) :
     * - Ecart superieur a GAP_THRESHOLD_CP ("un coup clairement meilleur que les
     *   autres", ex. seule pièce capable de reprendre une piece attaquee, ou une des
     *   rares cases de fuite d'un roi en echec qui ne perd pas de materiel) : jouer ce
     *   coup reviendrait a offrir une devinette quasi certaine - on choisit alors au
     *   hasard PARMI LES AUTRES candidats plutot que le meilleur.
     * - Ecart faible ou nul (plusieurs coups sensiblement equivalents - typiquement le
     *   cas d'un roi en echec avec peu de coups legaux, tous a peu pres aussi bons) :
     *   aucun coup n'est "evident", mais toujours renvoyer le meme (le premier de la
     *   liste, choix arbitraire cote UCI) resterait previsible pour un adversaire qui
     *   observerait plusieurs parties - on choisit au hasard parmi tous les candidats a
     *   moins de GAP_THRESHOLD_CP du meilleur.
     * Exception : un mat force (PV1) est toujours joue sans se poser la question,
     * quel que soit le risque de devinette - le manquer coute bien plus qu'une
     * devinette reussie. De meme si un score est manquant (recherche trop courte pour
     * avoir vu toutes les lignes MultiPV) : on ne peut pas mesurer l'ecart, on retombe
     * sur le meilleur coup plutot que de choisir au hasard a l'aveugle.
     */
    private String pickAvoidingObviousBest(List<Candidate> candidates) {
        Candidate best = candidates.get(0);
        if (best.forcedMate()) {
            return best.uci();
        }
        if (candidates.stream().anyMatch(c -> c.effectiveCp() == null)) {
            return best.uci();
        }
        Candidate second = candidates.get(1);
        int gap = best.effectiveCp() - second.effectiveCp();
        if (gap > GAP_THRESHOLD_CP) {
            List<Candidate> others = candidates.subList(1, candidates.size());
            return others.get(ThreadLocalRandom.current().nextInt(others.size())).uci();
        }
        List<Candidate> closeGroup = candidates.stream()
                .filter(c -> best.effectiveCp() - c.effectiveCp() <= GAP_THRESHOLD_CP)
                .toList();
        return closeGroup.get(ThreadLocalRandom.current().nextInt(closeGroup.size())).uci();
    }

    private Move matchLegalMove(String uci, List<Move> legalMoves) {
        Position from = Position.fromAlgebraic(uci.substring(0, 2));
        Position to = Position.fromAlgebraic(uci.substring(2, 4));
        PieceType promotion = uci.length() > 4 ? promotionFromLetter(uci.charAt(4)) : null;
        return legalMoves.stream()
                .filter(m -> m.from().equals(from) && m.to().equals(to) && m.promotionType() == promotion)
                .findFirst()
                .orElseThrow(() -> new StockfishUnavailableException(
                        "stockfish chose a move outside the legal move list: " + uci));
    }

    private PieceType promotionFromLetter(char letter) {
        return switch (letter) {
            case 'q' -> PieceType.QUEEN;
            case 'r' -> PieceType.ROOK;
            case 'b' -> PieceType.BISHOP;
            case 'n' -> PieceType.KNIGHT;
            default -> throw new IllegalArgumentException("unknown promotion letter: " + letter);
        };
    }
}
