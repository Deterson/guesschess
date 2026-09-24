# Moteurs d'IA — tableau comparatif

> Contexte complet (registre de versions, tournoi, roadmap) : [`CLAUDE.md`](CLAUDE.md) (étapes
> 20-23) et [`src/CLAUDE.md`](src/CLAUDE.md) (détail d'implémentation par étape).

Colonne "Efficacité" volontairement qualitative : aucun de ces moteurs n'a encore de vrai score
Elo mesuré à l'échelle (c'est exactement le rôle du tournoi de l'étape 22, une fois assez de
parties jouées) - un petit test de fumée n'est jamais une mesure de force fiable.

| Moteur | Capacités distinctives | Profondeur/plis max | Temps moyen par coup | Efficacité (indicatif, pas un Elo mesuré) |
|---|---|---|---|---|
| `minimax@1` | Négamax + alpha-bêta classique, figé (golden tests) ; heuristiques ad hoc : capture de roi jamais jouée sauf forcée, recherche active de fast_mate, évite une réfutation devinable + mémoire anti-répétition (HARD uniquement) | 2/3/4 plis fixes (easy/medium/hard) | ~20ms (easy, mesuré) à ~300-400ms (hard, depuis la position de départ) | Moteur de prod par défaut actuel - référence de comparaison pour tous les autres |
| `guessaware@1` | Seul moteur qui modélise le round comme un vrai jeu matriciel (stratégies mixtes coup/devinette) au cœur de la recherche, pas en heuristique après coup | 2-3 plis classiques + 1-2 plis "guess-aware" (un pli guess-aware coûte ×2 à ×12 un pli classique) | Budget configurable par coup (500/1000/2000ms par niveau), souvent sous-utilisé (approfondissement itératif) | **Non validé** - test de fumée (8 parties) : seulement 2/8 gagnées contre `minimax@1` moyen, échantillon bien trop petit pour conclure |
| `negamax-timed@1` | Même recherche que `minimax@1`, mais budget de temps réel vérifié à CHAQUE nœud au lieu d'une profondeur fixe par niveau ; volontairement sans les heuristiques de `minimax@1` (pas de fast_mate actif, pas d'évitement de réfutation, pas de mémoire) - référence "recherche nue" | Configurable (`maxDepth`), presets 3/5/7 plis par niveau | Budget respecté avec précision (~300ms mesuré pour un budget de 300ms, écart <1ms) | Pas encore testé à l'échelle |
| `random@1` | Aucune recherche, coup uniforme au hasard (jouer et deviner), aucune protection heuristique | 0 (aucune recherche) | ~0ms (instantané) | Adversaire plancher - repère bas du classement (0% de score dans les tests de fumée) |
| `stockfish@1` | Process externe UCI, même wrapper de heuristiques que `minimax@1` (`EngineBackedAgent`) mais évaluation Stockfish ; en difficile, MultiPV pour éviter le coup "trop évident" | Non piloté en plis (force par `UCI_LimitStrength`/`UCI_Elo`, temps par `movetime`) | ~2050ms (hard, mesuré via `--stockfish-path`) - plus que le `movetime` borné 300-1500ms documenté à l'étape 15, l'écart vient probablement du coût de lancement du process par appel | Fort en théorie (force Stockfish native) - vérifié fonctionnel cette session (binaire présent : `C:/Users/drde6/tools/stockfish.exe`, aussi référencé dans `.env`), mais seulement 2 parties jouées, pas une mesure de force |
