# Backend (Java 25 / Spring Boot) — contexte spécifique

> Chargé par Claude Code uniquement quand il travaille sur des fichiers sous `src/`. Contexte
> général du projet (concept, architecture, roadmap) : [`../CLAUDE.md`](../CLAUDE.md).

## Environnement de dev

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
- **Étape 14 — Identifiant unique de compte (login)** : pseudonyme immuable, 3-20 caractères,
  unique insensible à la casse (index `lower(login)`, migration V9), interdit sur
  "Anonymous"/"Anonyme". `login` nullable en SQL pour les comptes créés avant cette étape ; un
  nouveau compte n'est jamais inséré sans login (inscription en deux temps via un JWT
  "pending_registration", `RegistrationController`/`POST /api/registration/complete`). Backend :
  `GET /api/players/{login}` et `/api/players/{login}/games`, en dehors de `/api/account/**` donc
  jamais authentifiés (`PlayerProfileController`, `UserRepository.findByLoginIgnoreCase`).
