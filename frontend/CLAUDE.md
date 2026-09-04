# Frontend (VueJS 3) — contexte spécifique

> Chargé par Claude Code uniquement quand il travaille sur des fichiers sous `frontend/`. Contexte
> général du projet (concept, architecture, roadmap) : [`../CLAUDE.md`](../CLAUDE.md).

## Environnement de dev

- **Node plus récent nécessaire** : le Node par défaut de la machine de dev est en v20.11.0, mais
  `vite` (v8, `package.json`) dépend de `rolldown`, qui a besoin de `node:util.styleText` (Node
  ~21.7+) et échoue au démarrage sinon (`SyntaxError: The requested module 'node:util' does not
  provide an export named 'styleText'`). Un Node plus récent est présent localement via le node
  embarqué d'IntelliJ : `C:\Users\drde6\AppData\Roaming\JetBrains\IntelliJIdea2026.2\node\versions\24.20.0`.
  Plutôt que de modifier le PATH système (le vieux Node est enregistré au niveau **Machine**, avant
  le PATH **User** dans l'ordre de résolution Windows — le corriger demanderait des droits admin),
  [`run-dev.cmd`](run-dev.cmd) fixe le PATH localement avant de lancer `npm run dev`, et
  [`../.claude/launch.json`](../.claude/launch.json) pointe dessus pour que la preview du frontend
  lancée depuis une session Claude Code utilise ce script plutôt que `npm` directement. (Chemin du
  Node IntelliJ à vérifier si l'utilisateur change de version gérée par IntelliJ.)

## Conventions

- **`src/services/api.ts`** : toute nouvelle route qui exige toujours un compte (contrairement à
  `createGame`/`joinGame`/`myAccess`, permitAll et donc passées en
  `allowAnonymousFallback: true`) doit laisser `request()` remonter le code `SESSION_EXPIRED` sur
  un 401 plutôt que d'implémenter son propre retry/log — l'appelant peut alors renvoyer proprement
  vers l'accueil au lieu d'afficher un 401 brut (bug corrigé une fois, voir historique).
- **Login affiché** : partout où le login d'un compte est affiché (`PlayerLabel.vue`,
  `ProfileAboutView.vue`, `PublicProfileView.vue`), il est précédé d'un `@` (ex. `@Deterson`) —
  jamais pour un nom d'affichage ou une identité anonyme.

## Détail des étapes de la roadmap (liste complète : [`../CLAUDE.md`](../CLAUDE.md))

- **Étape 5 — Frontend VueJS 3** : échiquier interactif, client WebSocket, UI de devinette.
- **Étape 7 — Page d'accueil** : choix de couleur à la création ; créateur et adversaire passent
  par la même modale "se connecter / jouer en anonyme" (sautée si déjà connecté). Un seul lien de
  partie (`/game/{gameId}`, sans jeton) envoyé à l'adversaire.
- **Étape 8 — Page de profil** : header global (`AppHeader.vue`, "Jouer"/"Tutoriel"/"Profil"-ou-
  "Connexion") monté dans `App.vue`. Routes imbriquées `/profile` → `/profile/games`
  (`ProfileLayout.vue`/`ProfileGamesView.vue`), réservées aux comptes connectés (garde
  `router.beforeEach`). "Mes parties" : miniature du plateau + issue calculées côté serveur. Nom
  affiché modifiable (3 caractères minimum). Bouton "Connexion" du header ouvre une mini-modale
  dédiée (Google/GitHub uniquement).
  **Piège rencontré et corrigé** : `mx-auto` sur la racine de `ProfileLayout` désactivait
  l'étirement flex attendu de `<router-view class="flex-1">` (marge `auto` sur l'axe transversal =
  pas de `stretch`), retiré au profit de `w-full`.
- **Étape 11 — Historique de partie navigable**, façon lichess.org : `MoveHistoryList.vue`
  (notation PGGN cliquable), `ChessBoard.vue` avec une prop `ghostMove` dédiée pour la devinette en
  navigation (distincte de `hoverGuess`, le survol manuel en direct), navigation clavier ←/→,
  `historyIndex` (`null`=direct, `-1`=départ, `0..n-1`=après le round i).
- **Étape 12 — Timers** : `CreateGameModal.vue` ajoute un choix de cadence (correspondance, par
  défaut, ou temps réel). Presets classiques en grille 2 colonnes × 4 lignes, cliquer sur l'un
  d'eux lance la partie immédiatement (pas besoin de confirmer en plus) ; le champ minutes libre
  accepte les fractions (`1/2`, `1/4`) et les décimales (`,` ou `.`) pour les cadences bullet, voir
  `parseMinutes`. Pendule affichée dans `PlayerLabel.vue` (props `clockMs`/`clockRunning`/
  `urgent`), doublée de taille (`text-2xl`), au bord droit de la ligne. `useClock.ts` (composable)
  dérive un décompte local par couleur depuis `GameStateMessage` — jamais de source de vérité côté
  client, juste une interpolation entre deux messages.
  **Halo rouge** : deux portées distinctes calculées dans `GameView.vue`. `awaitingGuess` (public,
  `clockRunningFor !== sideToMove`) pilote uniquement le clignotement des pendules — visible des
  deux joueurs, sans rien exposer de plus que l'état déjà public. `awaitingGuessMine` (`&&
  myRole === 'guesser'`) restreint en plus le halo du plateau (`ChessBoard`) et celui du message de
  statut (`GameStatusBar`, prop `awaitingGuess`) au seul joueur qui doit effectivement deviner —
  jamais son adversaire, jamais un spectateur. Animation partagée (`guess-halo-glow`/
  `guess-halo-ring`, définies globalement dans `style.css` plutôt que dupliquées par composant) :
  volontairement lente et peu saturée, pas une alarme.
  **Piège rencontré** : la diffusion publique mi-round ajoutée côté backend (voir `src/CLAUDE.md`)
  a révélé un bug préexistant de `stores/game.ts`, qui effaçait `pendingSubmission`/`pendingMove`
  sur CHAQUE message de `/topic/games/{gameId}` en supposant qu'il ne pouvait signifier qu'"un
  round vient de se résoudre" — faux dès qu'un message intermédiaire (pendule, offre de nulle...)
  y transite aussi. Corrigé en ne réinitialisant que si `roundCount` a changé.
- **Étape 13 — Tutoriel des règles** : `HowToPlayView.vue` (`/how-to-play`), contenu FR/EN
  statique, aucun backend. Réutilise `ChessBoard.vue` en `disabled` avec des positions codées en
  dur. Trois exemples via les props de surbrillance existantes de `ChessBoard` (devinette
  incorrecte, devinette correcte, Guessmate) ; Guessmate présenté comme seule variante (pas de
  mention du choix GUESSCHESS/GUESSMATE de l'accueil).
- **Étape 14 — Identifiant unique de compte (login)** : `login` affiché au-dessus/en-dessous du
  plateau (`PlayerLabel.vue`, tenu à jour en direct par `PlayersBroadcastService` sur
  `/topic/games/{id}/players`), "Anonyme"/"Anonymous" en italique pour les identités anonymes.
  Nouveau compte bloqué côté front sur `/choose-login` tant que le login n'est pas choisi.
  `display_name`/`bio` modifiables séparément (`ProfileAboutView.vue`).
  **Profil public** : `/profile/{login}` (`PublicProfileView.vue`), en lecture seule et public
  (sans authentification), distinct de `/my-profile/*` (l'ancien `/profile/*`, renommé pour
  libérer ce chemin) qui reste réservé au compte connecté pour éditer ses propres infos.
