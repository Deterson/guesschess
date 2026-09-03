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
- **Étape 12 — Timers** (temps de réflexion + timer de devinette), **pas encore implémenté** :
  configuration du contrôle de temps au moment de la création de la partie (étape 7), façon échecs
  classique — un temps total par joueur + un bonus (incrément) ajouté après chaque coup joué (type
  Fischer). Timer géré et arbitré côté backend (jamais côté client, cohérent avec le principe "le
  serveur revalide tout") : le décompte réel vit dans l'agrégat `Game`/le round en cours, le
  frontend ne fait qu'afficher un countdown dérivé d'un timestamp reçu, pas sa propre source de
  vérité. Quand le temps d'un joueur tombe à zéro, la partie se termine immédiatement (perte au
  temps) — nouvelle valeur de cause dans `GameResultCause`. Nécessite un mécanisme serveur pour
  détecter le flag-fall même en l'absence de toute action du joueur — un scheduler (threads
  virtuels / `ScheduledExecutorService`) qui surveille les timers actifs, pas une vérification
  dépendant de la prochaine soumission.
  **Timer de devinette** : distinct du timer de réflexion, il ne consomme pas le temps total du
  devineur. Démarre au moment où le joueur au trait a soumis son coup réel et tant que le devineur
  n'a pas encore soumis sa devinette ; s'arrête dès que la devinette est soumise. Durée
  proportionnelle au temps total choisi à la création (ex. 15s pour une partie 5 min/side, 30s
  pour 10 min/side — ratio à figer précisément, éventuellement aussi fonction du bonus par coup).
  À expiration sans devinette soumise : aucune devinette n'est considérée faite et le coup réel est
  simplement joué (résolution identique au cas "devinette incorrecte", sans pénalité de temps côté
  devineur au-delà de ne pas avoir deviné).
  **Portée mode asynchrone** : les deux timers tels que décrits (décompte live à la seconde) n'ont
  de sens qu'en temps réel — en asynchrone les joueurs ne sont pas connectés simultanément, donc
  ni perte au temps classique ni fenêtre de devinette chronométrée à la seconde près. Reste une
  question ouverte séparée si l'asynchrone doit avoir son propre contrôle de temps (ex. temps par
  coup en jours, façon correspondance) ; hors périmètre de cette étape.
- **Étape 14 — Identifiant unique de compte (login)** : pseudonyme immuable, 3-20 caractères,
  unique insensible à la casse (index `lower(login)`, migration V9), interdit sur
  "Anonymous"/"Anonyme". `login` nullable en SQL pour les comptes créés avant cette étape ; un
  nouveau compte n'est jamais inséré sans login (inscription en deux temps via un JWT
  "pending_registration", `RegistrationController`/`POST /api/registration/complete`). Backend :
  `GET /api/players/{login}` et `/api/players/{login}/games`, en dehors de `/api/account/**` donc
  jamais authentifiés (`PlayerProfileController`, `UserRepository.findByLoginIgnoreCase`).
