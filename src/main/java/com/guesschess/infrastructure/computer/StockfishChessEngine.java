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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
     * MultiPV plutot qu'un livre d'ouverture - voir pickAmongCandidates) ; Moyen ~ Elo
     * 1500 ; Difficile = pleine puissance, sans limite d'Elo. movetimeMillis borne le
     * cout CPU (Pi partage entre plusieurs parties potentiellement simultanees contre
     * l'ordinateur) autant qu'il affaiblit le niveau facile/moyen.
     */
    private record EngineSettings(boolean limitStrength, int elo, int multiPv, int movetimeMillis) {
        static EngineSettings forLevel(ComputerLevel level) {
            return switch (level) {
                case EASY -> new EngineSettings(true, 1320, 3, 300);
                case MEDIUM -> new EngineSettings(true, 1500, 1, 600);
                case HARD -> new EngineSettings(false, 0, 1, 1500);
            };
        }
    }

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
    public Move chooseMove(Board board, List<Move> legalMoves, ComputerLevel level) {
        if (legalMoves.isEmpty()) {
            throw new IllegalArgumentException("no legal move to choose from");
        }
        if (stockfishPath == null || stockfishPath.isBlank()) {
            throw new StockfishUnavailableException("STOCKFISH_PATH is not configured");
        }
        EngineSettings settings = EngineSettings.forLevel(level);
        List<String> candidates = searchWithRetry(board, settings);
        String chosenUci = pickAmongCandidates(candidates, settings);
        return matchLegalMove(chosenUci, legalMoves);
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
    private List<String> searchWithRetry(Board board, EngineSettings settings) {
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
     * @return les coups candidats en notation UCI longue algebrique (ex. "e2e4",
     * "e7e8q"), index 0 = meilleur coup de l'engin (bestmove), index suivants =
     * alternatives issues des lignes MultiPV (uniquement si settings.multiPv() > 1) -
     * peut contenir moins d'elements que multiPv si l'engin n'a pas eu le temps
     * d'explorer toutes les lignes demandees.
     */
    private List<String> search(Board board, EngineSettings settings) {
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

    private List<String> runUciSession(Process process, Board board, EngineSettings settings) {
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
     * Lit les lignes "info ... multipv K ... pv <coup> ..." (la derniere par index K
     * l'emporte, la profondeur ne fait qu'augmenter) jusqu'a "bestmove <coup> ...",
     * qui fait autorite pour l'index 1 (toujours coherent avec la derniere ligne
     * multipv 1, mais bestmove reste la source de verite UCI). L'ordre du resultat
     * suit l'index MultiPV (1 = meilleur), pas l'ordre d'arrivee des lignes.
     */
    private List<String> awaitBestMove(BufferedReader stdout, int multiPv, long deadline) throws IOException {
        Map<Integer, String> byMultiPv = new HashMap<>();
        return readWithDeadline(deadline, () -> {
            String line;
            while ((line = stdout.readLine()) != null) {
                if (line.startsWith("bestmove")) {
                    String[] tokens = line.trim().split("\\s+");
                    if (tokens.length < 2 || "(none)".equals(tokens[1])) {
                        throw new StockfishUnavailableException("stockfish returned no move: " + line);
                    }
                    byMultiPv.put(1, tokens[1]);
                    List<String> ordered = new ArrayList<>();
                    for (int i = 1; i <= multiPv; i++) {
                        String move = byMultiPv.get(i);
                        if (move != null) {
                            ordered.add(move);
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

    private void parseMultiPvLine(String line, Map<Integer, String> byMultiPv) {
        String[] tokens = line.trim().split("\\s+");
        Integer index = null;
        String firstPvMove = null;
        for (int i = 0; i < tokens.length; i++) {
            if ("multipv".equals(tokens[i]) && i + 1 < tokens.length) {
                index = Integer.parseInt(tokens[i + 1]);
            }
            if ("pv".equals(tokens[i]) && i + 1 < tokens.length) {
                firstPvMove = tokens[i + 1];
            }
        }
        if (index != null && firstPvMove != null) {
            byMultiPv.put(index, firstPvMove);
        }
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

    /**
     * Facile (multiPv > 1) : choix aleatoire pondere vers les premiers candidats
     * (voir EngineSettings) plutot que toujours le meilleur coup - c'est ce qui pousse
     * la force effective sous le plancher natif de UCI_Elo (~1320) et introduit une
     * inconsistance "humaine" plutot qu'un simple affaiblissement uniforme. Moyen/
     * difficile (multiPv == 1) : un seul candidat, aucun alea a ce niveau au-dessus de
     * l'affaiblissement deja fait par UCI_Elo/pleine puissance.
     */
    private String pickAmongCandidates(List<String> candidates, EngineSettings settings) {
        if (candidates.size() <= 1) {
            return candidates.get(0);
        }
        double[] weights = {0.5, 0.3, 0.2};
        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += i < weights.length ? weights[i] : 0;
            if (roll < cumulative) {
                return candidates.get(i);
            }
        }
        return candidates.get(0);
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
