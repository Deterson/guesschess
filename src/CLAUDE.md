# Backend (Java 25 / Spring Boot) — contexte spécifique

> Chargé par Claude Code uniquement quand il travaille sur des fichiers sous `src/`. Contexte
> général du projet (concept, architecture, roadmap) : [`../CLAUDE.md`](../CLAUDE.md).

## Environnement de dev

- ⚠️ **Le backend Spring Boot tourne en local** sur cette machine Windows (`./mvnw spring-boot:run`
  ou IDE), jamais sur la VM distante — seul Postgres (Docker) y tourne, voir point suivant. Erreur
  déjà faite plusieurs fois : ne pas supposer que "le backend ne tourne pas" faute de pouvoir SSH
  sur la VM, et ne pas y chercher/lancer un process backend.
- **VM Docker distante** : le Docker de dev (Postgres inclus) tourne sur une VM AWS EC2 distante,
  IP fixe (Elastic IP, donc stable d'un redémarrage à l'autre) `35.180.147.199`, SSH ouvert, `.pem`
  situé à `C:\Users\drde6\.ssh\guesschess-dev-docker.pem`. Pas de Docker local sur la machine de
  dev Windows (CLI `docker` absente du PATH, testé en Git Bash et PowerShell).
  - `spring.datasource.url` (`application.properties`) pointe par défaut sur
    `35.180.147.199:5432` — lancer le backend en local se connecte donc à cette base Postgres
    **partagée**, pas une instance locale isolée. À garder en tête avant de la modifier ou d'y
    jouer une partie de test (d'autres sessions/devs y accèdent potentiellement aussi).
  - Utilisateur SSH de la VM : `ubuntu` (groupe `docker`, `docker ps`/`docker run` fonctionnent
    sans `sudo`). Alias configuré dans `~/.ssh/config` du poste de dev : `ssh guesschess-vm`
    suffit.
  - **Session Claude Code distante (conteneur cloud)** : pas d'accès à cette VM (sortant limité au
    HTTPS via proxy, SSH/Postgres bloqués même vers l'IP fixe) - utiliser à la place
    `docker-compose.yml` (Postgres local jetable, déjà à la racine) avec `SPRING_DATASOURCE_URL`
    surchargée en variable d'environnement plutôt que de toucher `application.properties`, qui
    pointe en dur sur la VM.
- **Tests Maven qui nécessitent Java 25** : le `JAVA_HOME` par défaut de la machine de dev pointe
  vers un JDK 8 (`mvnw -v` le confirme), ce qui fait échouer la compilation de tout le code
  utilisant `record`/pattern matching avec des erreurs trompeuses ("class, interface, or enum
  expected") sans rapport avec une vraie erreur de syntaxe. Un JDK 25 est installé localement
  (géré par IntelliJ) : `C:\Users\drde6\.jdks\ms-25.0.4.1`. Préfixer les commandes Maven, ex. en
  Git Bash :
  ```
  JAVA_HOME="/c/Users/drde6/.jdks/ms-25.0.4.1" PATH="/c/Users/drde6/.jdks/ms-25.0.4.1/bin:$PATH" ./mvnw ...
  ```
  (chemin à vérifier si l'utilisateur change de JDK géré par IntelliJ — lister `~/.jdks/` en cas de
  doute). Alternative durable : positionner `JAVA_HOME` sur ce JDK 25 dans le profil Windows, non
  fait automatiquement pour ne pas casser d'autres usages de JDK 8 sur la même machine.
- **Tests d'intégration Testcontainers** (`JpaGameRepositoryIntegrationTest`,
  `StompFlowIntegrationTest`, ... via `PostgresTestContainerConfig`) : besoin d'un daemon Docker
  local pour lancer un Postgres jetable, absent sur la machine de dev (`Could not find a valid
  Docker environment`) — mais peuvent tourner sur la VM distante ci-dessus, qui en a un. Script
  prêt à l'emploi : [`../scripts/test-integration-remote.sh`](../scripts/test-integration-remote.sh)
  `[filtre -Dtest optionnel]` — copie `pom.xml`/`mvnw`/`.mvn`/`src` vers la VM via SSH, lance les
  tests dans un conteneur Java 25 éphémère (`docker run --network host -v
  /var/run/docker.sock:...`, pattern classique Docker-in-Docker par socket monté :
  `--network host` nécessaire pour que ce conteneur atteigne, via `localhost`, les conteneurs
  Testcontainers "frères" qu'il démarre lui-même sur cette même VM), puis nettoie sa copie (via un
  conteneur, les fichiers générés appartenant à `root`). L'image du conteneur runner (Java 25 +
  `unzip`/`curl`, nécessaires au wrapper Maven mais absents de l'image de base) est construite une
  fois et réutilisée (`guesschess-mvn-runner:25`, déjà en cache sur la VM). Seuls les tests
  unitaires domaine/application (aucune dépendance Postgres/Docker) sont directement vérifiables en
  local sans ce script.

## Moteur d'échecs — pièges de performance à surveiller

- **Détection d'échec par simulation complète** : jouer chaque coup candidat puis rescanner tout
  le plateau pour voir si le roi est attaqué, répété pour chaque coup candidat, peut coûter cher
  si la détection d'attaque elle-même n'est pas ciblée (éviter les scans imbriqués évitables sur
  les 64 cases).
- **Recalcul systématique de "tous les coups légaux"** à chaque appel plutôt que mise en cache
  pour la durée du round — un round peut durer plusieurs secondes voire minutes (surtout en
  asynchrone), pas la peine de tout recalculer si la position n'a pas changé entre deux appels.
- **Recherche de pièces par balayage complet du plateau** à chaque fois plutôt qu'une structure
  indexée (liste des pièces par couleur/type) maintenue à jour incrémentalement à chaque coup.
- Les Value Objects immuables (choix DDD, voir [`../CLAUDE.md`](../CLAUDE.md)) sont voulus pour la
  clarté du domaine — vérifier que la copie de plateau à chaque coup reste bon marché (tableau
  simple), pas un clonage profond de structures lourdes.
- **Mesurer avant d'optimiser** : un benchmark (JMH) plutôt qu'une intuition — ce qui semble lent
  à la lecture n'est pas toujours le vrai goulot d'étranglement, et l'inverse est vrai aussi.
- **Point identifié en pratique (étape 4)** : certains algorithmes basiques de validation sont
  actuellement peu efficaces — à profiler et corriger en utilisant la liste ci-dessus comme grille
  de lecture, avant de considérer la montée en charge.

## Variables d'environnement (depuis l'étape 4)

⚠️ Toutes celles marquées **obligatoire** doivent être définies pour que l'application démarre
tout court (échec rapide voulu au boot Spring, pas seulement au moment du login) :

- `POSTGRES_USER` / `POSTGRES_PASSWORD` — identifiants Postgres (dev local via
  `../docker-compose.yml`, valeur par défaut `guesschess`/`guesschess`).
- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` — **obligatoire**. App OAuth Google Cloud Console
  (redirect URI : `http://localhost:8080/login/oauth2/code/google`). Spring Security valide la
  présence de ces identifiants au démarrage dès que la registration `google` est déclarée dans
  `application.properties`, même s'ils ne servent qu'au moment du login.
- `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` — **obligatoire**, même remarque. App OAuth GitHub
  Developer Settings (redirect URI : `http://localhost:8080/login/oauth2/code/github`).
- `JWT_SECRET` — **obligatoire**. Secret HMAC pour signer les JWT (≥ 32 octets aléatoires, ex.
  `openssl rand -base64 32`).
- `OAUTH_POST_LOGIN_REDIRECT_URI` — URL du frontend vers laquelle rediriger après login, JWT en
  fragment d'URL (`#token=...`). Défaut : `http://localhost:5173/oauth-callback`.
- `ANONYMOUS_COOKIE_SECURE` — (étape 6) `true`/`false`, flag `Secure` du cookie d'identité anonyme
  `guesschess_anon`. Défaut `false` (dev local en HTTP) ; mettre `true` derrière HTTPS (étape 10).
  Piège déjà rencontré : un cookie `Secure` posé sur une connexion HTTP simple est silencieusement
  ignoré au rappel par tout client conforme RFC 6265 — si un test/flux d'identité anonyme échoue
  étrangement en dev local, vérifier d'abord ce flag avant de chercher plus loin.
  Second piège déjà rencontré (corrigé, étape 14) : ce cookie doit rester en `SameSite=Lax`,
  jamais `Strict` — le callback OAuth (`/login/oauth2/code/{provider}`) est atteint par une
  redirection *initiée par* Google/GitHub, donc cross-site du point de vue du navigateur ; un
  cookie `Strict` y est silencieusement omis, ce qui faisait perdre le lien anonyme → compte pour
  un joueur qui se connecte en pleine partie jouée anonymement.
- `STOCKFISH_PATH` — (étape 15) chemin du binaire Stockfish, **facultatif** (contrairement aux
  autres variables ci-dessus) : absent/introuvable ne bloque pas le démarrage, seule la création
  d'une partie contre l'ordinateur échoue. Docker : déjà positionné par le Dockerfile
  (`/usr/games/stockfish`, paquet Debian). Dev local Windows : binaire officiel (build "universal",
  Sept. 2025) installé à `C:\Users\drde6\tools\stockfish.exe`, positionné dans `.env` (chemin à
  revoir si l'utilisateur change de machine/emplacement, comme pour le JDK/Node ci-dessus).
- `ADMIN_EMAILS` — (étape 18) **facultatif**, défaut vide (page admin inaccessible). Liste
  d'emails séparés par des virgules autorisés sur `/api/admin/**` (`AdminAccessService`, comparaison
  insensible à la casse contre l'email du compte OAuth). Positionné en dev local (`.env`) avec
  l'email du propriétaire du projet.
- `DEFAULT_GAME_VARIANT` — **facultatif**, défaut `GUESSCHESS`. Feature flag
  (`guesschess.default-variant`) pour trancher en prod entre variante Guessmate ou
  No-Guessmate par défaut sans rebuild ; lu par `GameCreationController`, exposé en lecture
  au frontend via `GET /api/games/default-variant` (coche la case correspondante dans la
  modale de création).

## Détail des étapes de la roadmap (liste complète : [`../CLAUDE.md`](../CLAUDE.md))

- **Étape 4 — Persistance et comptes joueurs** : PostgreSQL (Spring Data JPA, migrations Flyway),
  comptes OAuth uniquement (Google/GitHub), sessions JWT stateless pour les endpoints REST du
  contexte "Compte joueur".
- **Étape 8 — Page de profil**, pièges backend rencontrés et corrigés : `AnonymousIdentityFilter`
  doit être positionné avant `OAuth2LoginAuthenticationFilter` (pas
  `UsernamePasswordAuthenticationFilter`, qui s'exécute après lui dans l'ordre par défaut de
  Spring Security) pour que l'identité anonyme soit résolue avant `OAuthLoginSuccessHandler` ;
  `roundHistory`/`positionHistory` peuvent être `null` sur les parties créées avant l'étape 10
  (traité comme liste vide dans `GameJpaMapper.toDomain` plutôt que de planter) ; CORS n'autorisait
  pas `PATCH` (ajouté à `SecurityConfig`).
- **Étape 10 — Format PGGN** (Portable Game Guess Notation) : notation façon PGN avec le coup
  deviné entre parenthèses après le coup réel (`e4(e3)`) ; devinette correcte → seule elle
  apparaît entre parenthèses (`(Nf3)`, pas de redondance) ; pas de devinette → pas de parenthèses.
  `+`/`#` uniquement sur un coup réellement joué, jamais sur une devinette annulée (sauf le cas
  terminal Guessmate, ex. `16. (Ke2)#`). En-têtes façon PGN, `[Termination]` porte directement
  `GameResultCause`. `GET /api/games/{id}/pggn` (`PggnWriter`/`PggnParser`, ce dernier en
  extraction simple, non revalidé contre le moteur). Stockage : `roundHistory` (liste de
  `RoundResult`, y compris rounds annulés) a remplacé `lastRoundResult`/`moveHistory`, devenu
  redondant.
- **Étape 11 — Historique de partie navigable** : `GET /api/games/{gameId}/history` (lecture
  seule, sans jeton) expose le détail par round (coup joué, devinette, plateau) ; `roundCount` sur
  `GameSnapshot`/`GameStateMessage` évite de le refetch à chaque message live.
- **Étape 12 — Timers** (fait) : pendule Fischer (temps de base + incrément par coup), optionnelle
  à la création (`TimeControl`, null = correspondance, comportement inchangé). **Une seule pendule
  active à la fois** (`Game.clockRunningFor`/`clockRunningSince`, comme aux échecs classiques) :
  elle tourne pour le joueur au trait, puis — dès qu'il soumet son coup réel — pour son adversaire
  jusqu'à ce qu'il devine (sauf devinette déjà soumise à l'avance, gratuite). Pas de timer de
  devinette séparé : deviner consomme la pendule principale du devineur (s'il tombe à zéro en
  devinant, il perd au temps) ; l'incrément n'est crédité que sur un coup réel soumis dans les
  temps (jamais sur une devinette), **toujours** même si ce coup est ensuite annulé par une
  devinette correcte.
  **Premier round gratuit** : ni le tout premier coup ni la toute première devinette de la partie
  ne décomptent la pendule (ni increment sur ce premier coup) - aucune pendule ne démarre avant que
  ce round ne se résolve. C'est ce même mécanisme qui remplace l'ancien démarrage explicite à la
  jonction du deuxième joueur : plus besoin de distinguer "partie complète" de "partie déjà en
  cours", la pendule du round 2 démarre simplement comme celle de n'importe quel round suivant (voir
  `resolveRound`), qu'il s'agisse d'une invitation classique ou d'une revanche (les deux couleurs
  déjà liées). `stopClockFor`/`startClockFor` sont idempotents : une resoumission (déjà permise
  librement par le domaine) ne re-décompte ni ne relance la pendule concernée, donc pas besoin de
  la restreindre en partie chronométrée.
  **Flag-fall** : `GameClockScheduler` (nouveau package `infrastructure/scheduling`,
  `@Scheduled`, threads virtuels via `spring.threads.virtual.enabled`) balaie
  `clock_deadline_at` (colonne dénormalisée, migration V10, le reste de l'état pendule vit dans le
  JSONB `state` comme le reste de l'agrégat) et appelle `Game.forfeitOnTimeIfExpired` sous le même
  verrou (`GameRepository.withGame`) que les soumissions normales — nouvelle cause
  `GameResultCause.TIMEOUT`.
  **Piège rencontré (backend)** : `GameController.submitMove`/`submitGuess` ne diffusaient l'état
  public (`/topic/games/{id}`) qu'une fois le round résolu ; un coup réel qui arrête la pendule du
  mover et démarre celle du devineur sans résoudre le round (cas courant, devinette pas encore
  soumise) ne déclenchait donc aucune diffusion, et le halo/la pendule ne se mettaient jamais à
  jour côté adversaire avant qu'il devine. `GameSnapshot` ne révélant jamais le coup en attente,
  diffuser aussi dans ce cas (si `timeControl != null`) est sans risque anti-triche — corrigé en
  rediffusant explicitement dans la branche "pas encore résolu" de `submitMove`.
  **Piège rencontré (frontend), même cause racine** : `stores/game.ts` traitait tout message sur
  `/topic/games/{gameId}` comme "un round vient de se résoudre" et effaçait `pendingSubmission`/
  `pendingMove` sans condition - correct tant que ce topic ne diffusait qu'à la résolution, faux
  dès que le fix backend ci-dessus s'est mis à y diffuser aussi un coup réel encore en attente : le
  joueur qui venait de jouer se voyait ré-afficher "à vous de jouer" alors que son coup était bien
  enregistré serveur. Corrigé en ne réinitialisant plus que si `roundCount` a effectivement changé.
  **Bug de production rencontré** : `GameStateJson.whiteMillisRemaining`/`blackMillisRemaining`
  avaient été laissés en `long` primitif (au lieu de `Long`, contrairement à
  `timeControlBaseMillis` et aux autres champs de pendule) - toute partie persistée avant cette
  étape n'a tout simplement pas ces clés dans son JSON, et Jackson 3 refuse de mapper `null` sur un
  primitif (`MismatchedInputException`), plantant en 500 dès qu'on rouvrait une telle partie (ex.
  "Mes parties" après avoir joué en anonyme puis s'être connecté). Corrigé en les rendant `Long`
  nullable, comme les autres. Le test de régression correspondant
  (`GameJpaMapperTest.toDomainDefaultsClockFieldsForAGameJsonPersistedBeforeTimers`) passe par le
  vrai `GameStateJsonConverter` plutôt que de construire `GameStateJson` directement en Java - seul
  moyen de reproduire ce genre de plantage (une valeur `null` explicite en Java n'est pas la même
  chose qu'une clé absente du JSON désérialisé).
  **Portée mode asynchrone** : hors périmètre — la pendule ne concerne que le temps réel (démarrée
  uniquement une fois la partie complète, décompte live). Un contrôle de temps propre à la
  correspondance (ex. jours par coup) reste une question ouverte séparée.
  **Revanche** : `GameLifecycleService.createRematchGame` reprend le `TimeControl` de la partie qui
  se termine (pas juste le variant, déjà fait) - son propre premier round reste gratuit comme pour
  toute nouvelle partie, aucun traitement particulier nécessaire.
  `TimeControl.of`/`TimeControlHttpRequest.baseMinutes` acceptent des minutes fractionnaires
  (`double`, ex. 0.25 = 1/4 minute) pour les cadences bullet très courtes.
- **Étape 15 — Jouer contre l'ordinateur** : `PlayerRef.Computer(ComputerLevel)` (pas de compte,
  lié immédiatement aux deux couleurs à la création via `GameLifecycleService.createComputerGame` -
  jamais de flux "rejoindre"). `StockfishChessEngine` (port `ChessEngine`, `application/computer/`)
  pilote un binaire Stockfish externe en UCI (`ProcessBuilder`, un process par appel - pas de pool,
  chaque partie/coup a de toute façon besoin du sien) ; position reconstruite via
  `position startpos moves ...` (notation UCI longue), jamais de FEN. Niveaux : `UCI_LimitStrength`+
  `UCI_Elo` (facile ≈ 1320 + choix aléatoire pondéré parmi le top-3 MultiPV pour descendre sous le
  plancher natif de l'engin ; moyen ≈ 1500 ; difficile = pleine puissance), `movetime` borné (300-
  1500ms) pour limiter le coût CPU.
  **Difficile rendu imprévisible** : jouer systématiquement le coup objectivement optimal le rend
  trop facile à deviner (annulation + perte du trait pour l'ordinateur, pire que jouer un coup
  légèrement plus faible mais non deviné) — corrigé en demandant aussi du MultiPV (5 lignes) en
  difficile, avec une stratégie différente du facile (`pickAvoidingObviousBest` vs
  `pickByRankWeight`, `StockfishChessEngine`) : coup PV1 nettement meilleur que PV2 (écart de score
  au-delà de `GAP_THRESHOLD_CP`, ex. seule pièce capable de reprendre) → jouer au hasard parmi les
  *autres* candidats plutôt que le coup évident ; coups tous proches (ex. roi en échec avec peu de
  coups légaux) → jouer au hasard parmi eux plutôt que toujours le premier. Un mat forcé reste
  toujours joué sans exception (le manquer coûte plus qu'une devinette réussie). Prépare le terrain
  pour l'IA guess-aware de l'étape 17 sans l'anticiper entièrement.
  **Mémoire de coups bloqués** (difficile uniquement) : quand la devinette adverse annule le coup
  réel de l'ordinateur, ce coup (origine/destination/promotion, pas égalité complète de `Move` — la
  pièce capturée dépend de la position) est évité pendant ses 2 prochains tours de coup réel
  (`ComputerPlayerService.BlockedMove`/`movesToAvoidThisTurn`, jamais pour ses tours de devinette,
  état en mémoire non persisté). `ChessEngine.chooseMove` a gagné un paramètre
  `movesToAvoidIfPossible` pour ça — indicatif seulement, `StockfishChessEngine` l'ignore plutôt que
  de vider la liste de candidats MultiPV ou de forcer un coup nettement pire.
  **Recherche active de fast_mate** (tous niveaux, variante GUESSCHESS) : Stockfish ne modélise pas
  la règle maison fast_mate (voir `Game.FAST_MATE_ENABLED`/`isFastMateEnabled`) — un coup qui met
  l'adversaire échec avec une seule case de fuite gagne pourtant la partie sur-le-champ dès la
  résolution du round suivant, que son évaluation classique le distingue ou non d'un coup "juste
  bon". `ComputerPlayerService.findFastMateMoves` simule chaque coup légal candidat
  (`Board.applyMove`) et cherche cette condition manuellement, prioritaire sur l'appel au moteur —
  même méthode réutilisée côté devinette (`guessKingCaptureOrFallback`), un adversaire rationnel
  avec un fast_mate à disposition étant aussi la devinette la plus plausible.
  Un seul et même appel (`chooseMove`) sert à jouer son propre coup
  ET à deviner celui de l'adversaire (même question posée au moteur) - voir `ComputerPlayerService`,
  qui détermine ce rôle via `Game.sideToMove()` et déclenche l'action sur un thread virtuel à chaque
  début de round (création de partie, ou résolution du round précédent - jamais de polling).
  `STOCKFISH_PATH` (`guesschess.stockfish.path`) optionnel : absent/binaire introuvable ne bloque pas
  le démarrage de l'app, seule la création d'une partie contre l'ordinateur échoue alors
  (`ComputerUnavailableException` → 503 `COMPUTER_UNAVAILABLE`, vérifié avant même de créer la
  partie). Docker : paquet Debian `stockfish` (`/usr/games/stockfish`, dispo nativement en ARM64
  pour le Pi) plutôt qu'un téléchargement manuel par architecture.
  **Piège rencontré (corrigé, migration V11)** : la contrainte posée en V6 sur `game_access`
  exigeait que `*_player_type` et `*_player_id` soient tous les deux nuls ou tous les deux non-nuls
  - rejetait `PlayerRef.Computer` (type non-null, id null, un ordinateur n'ayant pas de compte),
  plantait la creation d'une partie contre l'ordinateur en 500 (`DataIntegrityViolationException`).
  Assouplie pour autoriser explicitement `type LIKE 'COMPUTER_%' AND id IS NULL`.
  **Piège rencontré (dev local, Windows)** : le process Stockfish lancé/tué à chaque `chooseMove`
  (voir plus haut) échoue occasionnellement en dev local (`StockfishUnavailableException: stockfish
  closed its output before sending 'bestmove'`), cause précise non identifiée (suspecté : lancement
  répété d'un process par l'antivirus/l'OS). `ComputerPlayerService.act` avalait cette exception sans
  filet, laissant le round bloqué indéfiniment (l'ordinateur ne soumettant plus jamais rien).
  Corrigé : `StockfishChessEngine` retente une fois avec un process frais avant d'abandonner, et
  `ComputerPlayerService` retombe sur un coup légal aléatoire si le moteur échoue quand même -
  dégrade la qualité d'un seul coup plutôt que de bloquer la partie.
  **Piège rencontré (corrigé)** : en variante GUESSCHESS (sans Guessmate), quand l'ordinateur
  devine (n'est pas au trait) et que son propre roi est resté en échec non résolu suite à une
  devinette adverse correcte au round précédent (voir `Game.resolveRound`/`applyRealMove`), les
  coups légaux de l'adversaire incluent une capture de ce roi - Stockfish ne connaît pas cette
  notion et ne la propose donc jamais comme devinette. `ComputerPlayerService` court-circuite
  désormais le moteur dans ce cas précis : devine immédiatement ce coup (au hasard s'il y en a
  plusieurs), sans même interroger `ChessEngine`.
  **Piège rencontré (dev local)** : `.env` sourcé par `source .env` (bash) - un chemin Windows avec
  antislashs (`C:\Users\...`) non quoté se fait manger ses antislashs par bash (`\U`, `\d`... sont
  interpretes comme de l'echappement, silencieusement supprimes), rendant le chemin invalide sans
  aucune erreur visible (juste `isAvailable()` qui renvoie false). Utiliser des slashs (`C:/Users/...`)
  dans `.env`, qui fonctionnent aussi bien pour Java/NIO sous Windows.
- **Étape 17 (en cours) — IA « guess-aware »** : constat de départ, `ComputerPlayerService` pose la
  même question à `ChessEngine.chooseMove` pour jouer et pour deviner ("meilleur coup pour cette
  position ?") — aucune des deux décisions ne modélise le fait qu'une devinette correcte *annule*
  le coup réel plutôt que de le laisser se jouer. Décision (après une note de conception dédiée,
  forker Stockfish envisagé puis écarté - le travail réel est dans une pénalité de prévisibilité au
  milieu d'une recherche, pas dans la génération de coups/l'évaluation déjà couvertes côté Java) :
  moteur maison plutôt que Stockfish-comme-oracle, qui remplace `StockfishChessEngine` en
  implémentant directement le port `ChessEngine`.
  - **Moteur** : `domain/rules/PositionEvaluator` (éval statique pure - matériel, tables
    positionnelles standard, mobilité, aux côtés de `MaterialEvaluator`/`CheckDetector`) +
    `application/computer/NegamaxSearch` (negamax + alpha-bêta ; chaque coup racine avec sa propre
    fenêtre complète, pas de coupe entre coups racine, pour un score exact et comparable par
    candidat - nécessaire à la couche guess-aware). `application/computer/MinimaxChessEngine`
    implémente `ChessEngine` par-dessus. ~300-400ms à profondeur 4 depuis la position de départ, en
    Java pur, sur un thread virtuel - largement dans le budget d'un appel Stockfish existant.
    **Piège rencontré (corrigé)** : un round annulé peut laisser un roi en échec non résolu (voir
    plus bas, court-circuit `ComputerPlayerService`) et faire apparaître une capture de roi parmi
    les coups légaux du plateau racine - situation qui n'existe jamais en échecs classiques.
    Explorer ce coup produisait un plateau sans roi que `CheckDetector.findKing` ne sait pas
    interpréter (`IllegalStateException` dès la récursion suivante). `NegamaxSearch` court-circuite
    désormais ce cas précis (`KING_CAPTURE_SCORE`, au-delà de tout score de mat) avant même
    d'appliquer le coup - jamais de plateau sans roi construit.
  - **Sélection du moteur** : propriété `guesschess.engine` (`GUESSCHESS_ENGINE`), `minimax` par
    défaut (`@ConditionalOnProperty` sur les deux implémentations) ou `stockfish` - gardé
    sélectionnable explicitement plutôt que retiré, décision volontaire (voir plus bas).
  - **Niveaux** : profondeur par `ComputerLevel` (facile 2, moyen 3, difficile 4) remplace
    `UCI_LimitStrength`/`UCI_Elo`. Facile affaibli par le même `pickByRankWeight` (aléatoire pondéré
    parmi le top 3) que `StockfishChessEngine`.
  - **Stratégie 1 — ignorer une réfutation parable** (`MinimaxChessEngine.
    pickPreferringGuessableRefutation`, difficile uniquement) : parmi les coups candidats déjà
    proches du meilleur coup classique (`GAP_THRESHOLD_CP` = 150, même convention que
    `StockfishChessEngine`), préfère celui qui expose une réponse adverse nettement meilleure que
    ses alternatives (`exposesGuessableRefutation`, recherche auxiliaire à profondeur fixe
    `REFUTATION_CHECK_DEPTH` = 2, indépendante du niveau) - le pire cas réel (devinée puis annulée)
    vaut mieux que ce qu'un minimax classique suppose. Un mat force adverse n'est jamais mis de
    côté de cette façon. Appliquée identiquement côté coup réel et côté devinette (même appel
    `chooseMove` pour les deux, `ChessEngine` ne distingue pas les rôles) - sans effet indésirable
    côté devinette, les candidats considérés étant déjà proches du meilleur.
  - **Capture de roi évitable (piège rencontré, corrigé)** : le court-circuit hérité de
    l'étape 15 (`ComputerPlayerService.chooseMoveOrFallback`, roi adverse en échec non
    résolu capturable) jouait systématiquement cette capture dès qu'elle était légale -
    correct pour Stockfish (raison d'être d'origine : coup "objectivement" le meilleur),
    mais oubliait qu'en guesschess ce coup n'est jamais gratuit tant qu'il n'est pas forcé
    : le mover n'est pas lui-même en échec, donc une devinette adverse correcte l'annule
    juste normalement (`Game.resolveRound`) plutôt que de déclencher une issue immédiate -
    un humain qui connaît la règle la devine donc quasi systématiquement. Corrigé à deux
    niveaux : `ComputerPlayerService` ne joue plus cette capture que si c'est l'unique
    coup légal (sinon exclue des candidats, y compris pour `findFastMateMoves` - sinon
    même risque de plateau sans roi que `NegamaxSearch` ci-dessus) ;
    `MinimaxChessEngine.excludeKingCaptureUnlessForced` fait de même en interne, car
    `NegamaxSearch` régénère ses propres coups depuis `board` et retrouverait sinon cette
    capture malgré la liste restreinte transmise par l'appelant.
  - **Stratégie 2 — varier après une parade** : déjà couverte par le `BlockedMove`/
    `movesToAvoidThisTurn` existant (étape 15, difficile uniquement) - rien de nouveau à écrire,
    `MinimaxChessEngine` honore `movesToAvoidIfPossible` comme `StockfishChessEngine`.
  - **Stratégie 3 — varier la devinette** (`ComputerPlayerService.lastGuessByGame`/
    `rememberGuess`/`guessesToAvoidThisTurn`, difficile uniquement) : symétrique de `BlockedMove`
    côté devinette plutôt que côté coup réel - mémoire d'un seul coup (pas de compte à rebours,
    "deux fois d'affilée" dans l'énoncé), réutilise le même canal `movesToAvoidIfPossible`.
  - **Tests** : `PositionEvaluatorTest`, `NegamaxSearchTest` (mat en 1, non-braderie de la dame,
    capture de matériel gratuit), `MinimaxChessEngineTest` (dont un test direct de
    `pickPreferringGuessableRefutation`/`exposesGuessableRefutation`, méthodes package-private
    exprès pour ça), `ComputerPlayerServiceTest` (non-répétition de devinette). Aucun ne démarre de
    process externe. Validé aussi de bout en bout (backend + frontend réels,
    `guesschess.engine=minimax`, partie contre l'ordinateur jouée).
  - **Défaut basculé sur `minimax`** (choix explicite de l'utilisateur, pas automatique à la suite
    de l'implémentation) - `StockfishChessEngine`/`STOCKFISH_PATH`/le paquet `stockfish` du
    Dockerfile sont gardés tels quels plutôt que retirés (dernière étape du plan de migration
    d'origine, volontairement pas faite) : `guesschess.engine=stockfish` reste un retour en arrière
    possible sans toucher au code, le temps de calibrer `GAP_THRESHOLD_CP`/`REFUTATION_CHECK_DEPTH`
    par l'expérience réelle.
- **Étape 18 — Page admin** : lecture seule, pas de mutation. `AdminAccessService` verifie
  l'email du compte (JWT `sub` -> `AccountService.getById`) contre `ADMIN_EMAILS` ; le JWT de
  session ne porte pas l'email en claim (seulement `displayName`), d'ou ce lookup plutot qu'une
  lecture directe du token. `AdminUserQueries`/`AdminGameQueries` (package des repos Spring Data
  concernes, pas le domaine - une recherche paginee tous comptes/parties confondus n'est pas un
  besoin du domaine) interrogent directement `SpringDataUserJpaRepository`/`SpringDataGameJpaRepository`
  plutot que de passer par `UserRepository`/`GameRepository` (ports concus pour un acces cible, pas
  un listing admin). `AdminController` resout le login d'un joueur `ACCOUNT` via `AccountService`
  (un lookup par compte affiche, volume interne trop faible pour justifier un batch).
- **Étape 14 — Identifiant unique de compte (login)** : pseudonyme immuable, 3-20 caractères,
  unique insensible à la casse (index `lower(login)`, migration V9), interdit sur
  "Anonymous"/"Anonyme". `login` nullable en SQL pour les comptes créés avant cette étape ; un
  nouveau compte n'est jamais inséré sans login (inscription en deux temps via un JWT
  "pending_registration", `RegistrationController`/`POST /api/registration/complete`). Backend :
  `GET /api/players/{login}` et `/api/players/{login}/games`, en dehors de `/api/account/**` donc
  jamais authentifiés (`PlayerProfileController`, `UserRepository.findByLoginIgnoreCase`).
