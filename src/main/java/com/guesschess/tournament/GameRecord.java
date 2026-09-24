package com.guesschess.tournament;

/**
 * Une ligne du fichier JSONL de sortie du tournoi (etape 22 de la roadmap) - une partie
 * complete, headless, entre deux agents. agent = la chaine telle que passee en ligne de
 * commande (ex. "guessaware@1:hard" ou "negamax-timed@1:maxDepth=6,budgetMillis=1200"),
 * volontairement distincte de l'AgentId seul : deux configurations du meme agent doivent
 * apparaitre comme des concurrents differents dans le rapport.
 */
public record GameRecord(
        int gameIndex,
        long seed,
        int openingIndex,
        String openingFen,
        String variant,
        String whiteAgent,
        String blackAgent,
        String resultTag,
        String winner,
        String cause,
        boolean abortedRoundLimit,
        int roundCount,
        long whiteThinkMillis,
        long blackThinkMillis,
        int whiteGuesses,
        int whiteGuessesCorrect,
        int blackGuesses,
        int blackGuessesCorrect,
        long durationMillis,
        String machine,
        String startedAt,
        String pggn
) {
}
