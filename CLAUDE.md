# Projet : Échecs avec règle de devinette

## Concept du jeu

Jeu d'échecs classique avec une règle additionnelle :

- À chaque demi-coup, les deux joueurs réfléchissent **simultanément** :
  - celui dont c'est le tour choisit son coup réel ;
  - l'adversaire choisit un coup qu'il pense être celui joué (une "devinette" parmi les coups légaux de l'adversaire).
- Les deux choix sont **cachés** l'un de l'autre jusqu'à ce que les deux aient soumis.
- Une fois les deux soumis, on révèle et on résout :
  - **Devinette correcte** → le coup réel est annulé, le tour passe au devineur (c'est lui qui joue au tour suivant).
  - **Devinette incorrecte** → le coup réel est joué normalement, tour normal.
- **Cas particulier** : si le joueur au trait est en échec et que l'adversaire devine correctement son coup, le roi est capturé et la partie se termine immédiatement (au lieu de simplement annuler le coup).

## Stack technique

- **Backend** : Java 25, Spring Boot
- **Frontend** : VueJS 3
- **Déploiement** : Docker + docker-compose, sur Raspberry Pi 4 (4 Go RAM) — si le Pi devient un jour insuffisant, la conteneurisation permet de redéployer les mêmes images sur du cloud sans réécrire l'application
- **Modes de jeu** : temps réel (WebSocket) **et** asynchrone (façon correspondance), au choix des joueurs
- attention : le docker tourne sur une VM remote 35.180.147.199:5432 (AWS EC2, Elastic IP donc fixe d'un redemarrage a l'autre — avant l'allocation de cette Elastic IP, l'IP publique changeait a chaque stop/start de l'instance, ce qui a deja necessite une mise a jour de cette meme ligne), le SSH est ouvert et le .pem est situé là: C:\Users\drde6\.ssh\guesschess-dev-docker.pem
  - Pas de Docker local sur la machine de dev Windows (CLI `docker` absente du PATH, testé aussi bien en Git Bash qu'en PowerShell) : tout Docker (Postgres de dev inclus) vit uniquement sur cette VM distante, accessible via le `.pem` ci-dessus.
  - Consequence directe : `spring.datasource.url` (application.properties) pointe par defaut sur `35.180.147.199:5432` — lancer le backend en local se connecte donc a cette base Postgres **partagee**, pas a une instance locale isolee. A garder en tete avant de la modifier ou d'y jouer une partie de test (les autres sessions/devs y accedent potentiellement aussi).
  - Utilisateur SSH de la VM : `ubuntu` (dans le groupe `docker`, `docker ps`/`docker run` fonctionnent sans `sudo`). Alias configure dans `~/.ssh/config` (poste de dev de l'utilisateur) : `ssh guesschess-vm` suffit, pas besoin de repeter `-i .pem` ni l'utilisateur/IP a chaque fois.
  - **Tests Maven qui necessitent Java 25** : le `JAVA_HOME` par defaut de cette machine de dev pointe vers un JDK 8 (`mvnw -v` le confirme), ce qui fait echouer la compilation de tout le code utilisant `record`/pattern matching avec des erreurs trompeuses ("class, interface, or enum expected") qui n'ont rien a voir avec une vraie erreur de syntaxe. Un JDK 25 est neanmoins deja installe localement (gere par IntelliJ) : `C:\Users\drde6\.jdks\ms-25.0.4.1`. Prefixer les commandes Maven avec, par exemple en Git Bash :
    ```
    JAVA_HOME="/c/Users/drde6/.jdks/ms-25.0.4.1" PATH="/c/Users/drde6/.jdks/ms-25.0.4.1/bin:$PATH" ./mvnw ...
    ```
    (chemin a verifier si l'utilisateur change de JDK gere par IntelliJ - lister `~/.jdks/` en cas de doute). Alternative durable : l'utilisateur peut positionner `JAVA_HOME` sur ce JDK 25 une bonne fois pour toutes dans son profil Windows, mais ce n'est pas fait automatiquement ici pour ne pas casser d'autres usages de JDK 8 sur la meme machine.
  - **Tests d'integration bases sur Testcontainers** (`JpaGameRepositoryIntegrationTest`, `StompFlowIntegrationTest`, ... via `PostgresTestContainerConfig`) : ont besoin d'un **daemon Docker local** pour lancer un Postgres jetable, absent sur cette machine de dev (`Could not find a valid Docker environment`) — mais peuvent tourner sur la VM distante ci-dessus, qui en a un. Script pret a l'emploi : [`scripts/test-integration-remote.sh`](scripts/test-integration-remote.sh) `[filtre -Dtest optionnel]` — copie `pom.xml`/`mvnw`/`.mvn`/`src` vers la VM via SSH, lance les tests dans un conteneur Java 25 ephemere (`docker run --network host -v /var/run/docker.sock:...`, le pattern classique Docker-in-Docker par socket monte : `--network host` est necessaire pour que ce conteneur atteigne, via `localhost`, les conteneurs Testcontainers "freres" qu'il demarre lui-meme sur cette meme VM), puis nettoie sa copie (via un conteneur, les fichiers generes appartenant a `root`). L'image du conteneur runner (Java 25 + `unzip`/`curl`, necessaires au wrapper Maven mais absents de l'image de base) est construite une fois et reutilisee (`guesschess-mvn-runner:25`, deja en cache sur la VM). Seuls les tests unitaires domaine/application (aucune dependance Postgres/Docker) sont directement verifiables en local sans ce script.
  - **Frontend (`npm run dev` / Vite) qui necessite un Node plus recent** : le Node par defaut de cette machine de dev est en v20.11.0, mais `vite` (v8, package.json de `frontend`) depend de `rolldown`, qui a besoin de `node:util.styleText` (Node ~21.7+) et echoue au demarrage sinon (`SyntaxError: The requested module 'node:util' does not provide an export named 'styleText'`). Un Node plus recent est neanmoins deja present localement via le node embarque d'IntelliJ : `C:\Users\drde6\AppData\Roaming\JetBrains\IntelliJIdea2026.2\node\versions\24.20.0`. Plutot que de modifier le PATH systeme (le vieux Node est enregistre au niveau **Machine**, avant le PATH **User** dans l'ordre de resolution Windows — le corriger demanderait des droits admin), [`frontend/run-dev.cmd`](frontend/run-dev.cmd) fixe le PATH localement avant de lancer `npm run dev`, et [`.claude/launch.json`](.claude/launch.json) pointe dessus pour que la preview du frontend lancee depuis une session Claude Code utilise ce script plutot que `npm` directement. (Chemin du Node IntelliJ a verifier si l'utilisateur change de version geree par IntelliJ, meme remarque que pour le JDK 25 ci-dessus.)

## Décisions d'architecture

- **Pas de microservices** — overkill pour un jeu à 2 joueurs sur un Pi. On part sur un **monolithe modulaire** avec séparation claire :
  - `domain` : moteur d'échecs pur (plateau, pièces, coups légaux, échec/mat/pat) + mécanique de devinette, sans dépendance Spring ni réseau
  - `application` : orchestration du cycle de vie d'une partie
  - `infrastructure` : WebSocket, persistence, notifications
- Le moteur doit exposer "tous les coups légaux pour une couleur donnée" (pas juste valider un coup), car le devineur doit pouvoir parcourir/sélectionner parmi ces coups.
- Chaque "round" (coup réel + devinette) est modélisé comme un petit état à deux soumissions cachées, résolu seulement quand les deux sont arrivées. Le serveur ne doit jamais exposer une soumission avant que les deux soient là (anti-triche).
- Cet état de round doit être **persisté en base**, pas seulement gardé en mémoire, pour fonctionner aussi bien en temps réel qu'en asynchrone (un joueur peut soumettre puis se déconnecter, l'autre soumet plus tard).
- Java 25 + threads virtuels pour bien encaisser les connexions WebSocket concurrentes sans trop peser sur les 4 Go du Pi.
- Base de données : PostgreSQL en conteneur léger (tranché à l'étape 4), cohérent avec le docker-compose complet prévu à l'étape 9. Migrations gérées par Flyway.
- **Architecture : Domain-Driven Design (DDD)**, cohérente avec le monolithe modulaire à trois couches ci-dessus :
  - **Bounded contexts** : séparer le contexte "Partie" (moteur d'échecs + mécanique de devinette) du contexte "Compte joueur" (identité, auth, OAuth) — ils n'ont pas de raison de partager leur modèle.
  - **Aggregate root** : `Game` encapsule tout l'état d'une partie (plateau, historique des coups, round en cours, résultat) et garantit ses invariants.
  - **Value Objects** : `Move`, `Position`, `Color`, `PieceType`, `GuessOutcome`... — immuables, sans identité propre.
  - **Repositories comme ports** : interfaces définies dans le domaine, implémentées dans l'infrastructure (Spring Data ou autre) — le domaine ne dépend jamais de la persistance.
  - **Domain services** pour la logique transverse qui ne tient pas naturellement dans un objet (génération des coups légaux, détection échec/mat/pat).

## Communication frontend-backend

- **Temps réel** : WebSocket, connexion ouverte une fois à l'arrivée sur la partie. Chaque coup ou devinette est un message sur cette connexion déjà établie — pas une nouvelle requête HTTP par coup. Le round de devinette (double soumission cachée) se résout côté serveur puis le résultat est **poussé** aux deux joueurs sur cette même connexion (pas de polling).
- **Asynchrone** : REST classique (ex. `POST /games/{id}/moves`), sans connexion persistante — logique vu que les deux joueurs ne sont pas connectés en même temps et que la fréquence est naturellement faible.
- **Dimensionnement** : même à 1000 joueurs simultanés (~500 parties), le volume de messages reste faible (quelques dizaines à centaines par seconde, sur des connexions déjà ouvertes) — le vrai axe de dimensionnement, ce sont les connexions WebSocket maintenues ouvertes, pas le débit de requêtes. C'est ce que les threads virtuels de Java 25 encaissent bien.
- Seule la soumission finale (coup ou devinette validé) part vers le serveur — les interaction UI (déplacer une pièce, hésiter) restent côté client. Le serveur revalide systématiquement chaque soumission (jamais confiance dans le client).

## Performance et scalabilité

### Moteur d'échecs — pièges classiques à surveiller

- **Détection d'échec par simulation complète** : jouer chaque coup candidat puis rescanner tout le plateau pour voir si le roi est attaqué, répété pour chaque coup candidat, peut coûter cher si la détection d'attaque elle-même n'est pas ciblée (éviter les scans imbriqués évitables sur les 64 cases).
- **Recalcul systématique de "tous les coups légaux"** à chaque appel plutôt que mise en cache pour la durée du round — un round peut durer plusieurs secondes voire minutes (surtout en asynchrone), pas la peine de tout recalculer si la position n'a pas changé entre deux appels.
- **Recherche de pièces par balayage complet du plateau** à chaque fois plutôt qu'une structure indexée (liste des pièces par couleur/type) maintenue à jour incrémentalement à chaque coup.
- Les Value Objects immuables (choix DDD ci-dessus) sont voulus pour la clarté du domaine — vérifier que la copie de plateau à chaque coup reste bon marché (tableau simple), pas un clonage profond de structures lourdes.
- **Mesurer avant d'optimiser** : un benchmark (JMH) plutôt qu'une intuition — ce qui semble lent à la lecture n'est pas toujours le vrai goulot d'étranglement, et l'inverse est vrai aussi.
- **Point identifié en pratique (étape 4)** : certains algorithmes basiques de validation sont actuellement peu efficaces — à profiler et corriger en utilisant la liste ci-dessus comme grille de lecture, avant de considérer la montée en charge.

### Réseau à grande échelle

- Une seule instance Spring Boot + threads virtuels tient bien plusieurs milliers de connexions WebSocket ouvertes — le débit de messages n'est pas le problème (voir section Communication frontend-backend), la mémoire par connexion l'est davantage.
- **Si un jour plusieurs instances backend sont nécessaires** (Pi seul insuffisant, ou migration cloud) : le vrai défi du WebSocket multi-instance, c'est qu'un message pour le joueur B doit atteindre l'instance qui tient *sa* connexion, potentiellement différente de celle de A. Deux options : sessions collantes (sticky sessions, simple mais rigide) ou un bus pub/sub entre instances (Redis pub/sub par exemple) pour diffuser les événements de résolution de round. L'état des rounds déjà persisté en base facilite cette transition, car les instances restent sans état applicatif.
- Le reverse proxy (étape 10 de la roadmap) doit être configuré spécifiquement pour le WebSocket : upgrade de connexion géré correctement, timeouts assez longs pour ne pas couper des connexions ouvertes mais inactives.
- Prévoir une limite de connexions concurrentes et une dégradation propre (plutôt qu'un crash) si un pic dépasse la capacité.
- Limitation de débit (rate limiting) par joueur/IP sur les soumissions, pour se protéger d'un client abusif ou buggé plutôt que de faire confiance à la fréquence naturelle du jeu.

### Scaling Docker

- `docker-compose` sur un seul hôte permet de multiplier les réplicas du backend (`--scale`), mais seulement si l'application est suffisamment sans état pour ça — voir le point pub/sub ci-dessus.
- Au-delà d'un seul hôte, il faudra un orchestrateur (Docker Swarm ou Kubernetes) — pas la peine d'y penser avant d'en avoir réellement besoin, mais la conteneurisation actuelle rend cette migration possible sans réécrire l'application.
- Configurer la JVM pour qu'elle respecte les limites mémoire du conteneur (`-XX:MaxRAMPercentage` ou équivalent) plutôt que de laisser Java deviner — sinon risque d'OOM kill par Docker/le Pi sans message d'erreur clair.
- Health checks et arrêt propre (graceful shutdown) des conteneurs, pour qu'un redéploiement ne coupe pas brutalement les parties en cours — l'état déjà persisté en base permet aux clients de reprendre après reconnexion.

### Observabilité

- Mettre en place des métriques basiques dès que possible (connexions actives, parties en cours, latence de résolution d'un round) — c'est ce qui permet de savoir objectivement quand le Raspberry Pi devient réellement insuffisant, plutôt que de le deviner.

## Tests

Écrire des tests **quand c'est pertinent**, pas systématiquement partout :

- **Toujours** pour la logique du domaine, qui concentre le risque du projet : moteur d'échecs (coups légaux, échec/mat/pat, roque, prise en passant, promotion) et mécanique de devinette (résolution des rounds, cas de l'échec).
- Tests d'intégration pour les couches applicative/infrastructure quand ça a du sens (ex. persistance des rounds, résolution WebSocket).
- Pas la peine de sur-tester le code trivial (DTOs, mappers simples, configuration Spring).

## Fonctionnalités prévues (pas forcément dans la v1)

- **Comptes joueurs** : déjà couvert par l'étape 4 de la roadmap.
- **Historique de matchs** : quasi gratuit vu que l'état des parties est déjà persisté en base pour l'asynchrone — il suffit de ne pas supprimer les parties terminées.
- **Classement ELO** : prévu comme un module séparé, ajouté plus tard, qui lit l'historique de matchs et calcule les scores sans toucher au moteur d'échecs ni à la mécanique de devinette.
- **OAuth** (Google/GitHub, etc.) : authentification, en plus ou à la place de comptes email/mot de passe classiques.

### ⚠️ Points de vigilance pour la v1 (à cause de ces fonctionnalités futures)

- **Authentification** : tranché à l'étape 4 — OAuth uniquement (Google/GitHub), pas d'email/mot de passe. Sessions JWT stateless (pas de session stockée côté serveur au-delà de la poignée de main OAuth2 elle-même).
- **Résultat d'une partie** : structurer dès l'étape 2/3 l'agrégat `Game` pour qu'il enregistre le résultat **et sa cause** (mat classique, abandon, roi capturé via devinette...) plutôt qu'un simple gagnant/perdant. Ça évite de devoir réparer les données a posteriori quand on branchera l'historique et l'ELO.

## Roadmap (ordre des prompts à donner à Claude Code)

1. Modéliser le moteur d'échecs pur en Java 25 (domaine, sans Spring ni réseau)
2. Modéliser la règle de devinette comme extension du moteur (état du round, résolution)
3. Architecture applicative Spring Boot (couches, WebSocket vs STOMP, cycle de vie d'une partie)
4. ✅ Persistance et comptes joueurs (fait) : PostgreSQL (Spring Data JPA, migrations Flyway),
   comptes OAuth uniquement (Google/GitHub), sessions JWT stateless pour les endpoints REST du
   contexte "Compte joueur".
5. ✅ Frontend VueJS 3 (échiquier interactif, client WebSocket, UI de devinette) (fait)
6. ✅ Compte joueur lié à une partie (fait) — modèle détaillé dans la section "Liaison
   compte/session ↔ partie" plus bas.
7. ✅ Page d'accueil — création de partie (fait, avec durcissement de l'accès aux coups/devinettes
   ultérieur) — flux détaillé dans la section "Liaison compte/session ↔ partie" plus bas.
8. ✅ Page de profil (fait) : header global (`AppHeader.vue`, "Jouer"/"Tutoriel"/"Profil"-ou-
   "Connexion") monté dans `App.vue`. Routes imbriquées `/profile` → `/profile/games`
   (`ProfileLayout.vue`/`ProfileGamesView.vue`), réservées aux comptes connectés (garde
   `router.beforeEach` côté front + `/api/account/**` `.authenticated()` côté back). "Mes
   parties" : `GET /api/account/games?page=&size=` (`GameLifecycleService.listGamesForAccount`),
   miniature du plateau + issue (`GameSummary.Outcome`) calculées côté serveur. Nom affiché
   modifiable via `PATCH /api/account/me` (3 caractères minimum). Fusion identité anonyme →
   compte au login (`GameAccessRepository.relinkAnonymousToAccount`, appelée dans
   `OAuthLoginSuccessHandler`) — seule exception à l'immuabilité du lien posée aux étapes 6/7.
   Modale "comment voulez-vous jouer" sautée si déjà connecté ; bouton "Connexion" du header
   ouvre une mini-modale dédiée (Google/GitHub uniquement).

   **Pièges rencontrés et corrigés** : `AnonymousIdentityFilter` doit être positionné avant
   `OAuth2LoginAuthenticationFilter` (pas `UsernamePasswordAuthenticationFilter`, qui s'exécute
   après lui dans l'ordre par défaut de Spring Security) pour que l'identité anonyme soit
   résolue avant `OAuthLoginSuccessHandler` ; `roundHistory`/`positionHistory` peuvent être
   `null` sur les parties créées avant l'étape 10 (traité comme liste vide dans
   `GameJpaMapper.toDomain` plutôt que de planter) ; CORS n'autorisait pas `PATCH` (ajouté à
   `SecurityConfig`) ; `mx-auto` sur la racine de `ProfileLayout` désactivait l'étirement flex
   attendu de `<router-view class="flex-1">` (marge `auto` sur l'axe transversal = pas de
   `stretch`), retiré au profit de `w-full`.

9. ✅ Dockerisation (fait) : `Dockerfile` racine (backend, multi-stage, JVM bornée par
   `-XX:MaxRAMPercentage`) et `frontend/Dockerfile` (multi-stage, servi par nginx qui proxifie
   `/api`, `/ws`, `/oauth2`, `/login` vers le backend en **same-origin** — CORS obsolète en prod,
   une même image fonctionne derrière n'importe quel domaine). `spring-boot-starter-actuator`
   pour un vrai `HEALTHCHECK` Docker (`/actuator/health`). `docker-compose.prod.yml` : postgres
   sans port publié, `depends_on: condition: service_healthy` en chaîne. **Piège à retenir** :
   `docker-compose.prod.yml` et le `docker-compose.yml` de dev partagent le même nom de projet
   Compose par défaut (donc le même volume Postgres) si lancés depuis le même dossier sans `-p` —
   utiliser un nom de projet distinct pour tester la stack prod en local sans toucher aux données
   de dev.
10. ✅ Format PGGN (Portable Game Guess Notation) (fait) — notation façon PGN avec le coup
    deviné entre parenthèses après le coup réel (`e4(e3)`) ; devinette correcte → seule elle
    apparaît entre parenthèses (`(Nf3)`, pas de redondance) ; pas de devinette → pas de
    parenthèses. `+`/`#` uniquement sur un coup réellement joué, jamais sur une devinette
    annulée (sauf le cas terminal Guessmate, ex. `16. (Ke2)#`). En-têtes façon PGN,
    `[Termination]` porte directement `GameResultCause`. `GET /api/games/{id}/pggn`
    (`PggnWriter`/`PggnParser`, ce dernier en extraction simple, non revalidé contre le
    moteur). Stockage : `roundHistory` (liste de `RoundResult`, y compris rounds annulés) a
    remplacé `lastRoundResult`/`moveHistory`, devenu redondant.

11. ✅ Historique de partie navigable (fait), façon lichess.org : `GET /api/games/{gameId}/history`
    (lecture seule, sans jeton) expose le détail par round (coup joué, devinette, plateau) ;
    `roundCount` sur `GameSnapshot`/`GameStateMessage` évite de le refetch à chaque message
    live. Front : `MoveHistoryList.vue` (notation PGGN cliquable), `ChessBoard.vue` avec une
    prop `ghostMove` dédiée pour la devinette en navigation (distincte de `hoverGuess`, le
    survol manuel en direct), navigation clavier ←/→, `historyIndex` (`null`=direct,
    `-1`=départ, `0..n-1`=après le round i).

12. Timers (temps de réflexion + timer de devinette) : configuration du contrôle de temps
    au moment de la création de la partie (étape 7), façon échecs classique — un temps total
    par joueur + un bonus (incrément) ajouté après chaque coup joué (type Fischer). Timer
    géré et arbitré côté backend (jamais côté client, cohérent avec le principe "le serveur
    revalide tout") : le décompte réel vit dans l'agrégat `Game`/le round en cours, le
    frontend ne fait qu'afficher un countdown dérivé d'un timestamp reçu, pas sa propre
    source de vérité. Quand le temps d'un joueur tombe à zéro, la partie se termine
    immédiatement (perte au temps) — nouvelle valeur de cause dans `GameResultCause` (voir
    section "Résultat d'une partie"). Nécessite un mécanisme serveur pour détecter le
    flag-fall même en l'absence de toute action du joueur (personne ne soumet rien, donc
    rien ne déclenche naturellement la vérification) — un scheduler (threads virtuels /
    `ScheduledExecutorService`) qui surveille les timers actifs, pas une vérification
    dépendant de la prochaine soumission.

    **Timer de devinette** : distinct du timer de réflexion, il ne consomme pas le temps
    total du devineur. Démarre au moment où le joueur au trait a soumis son coup réel et
    tant que le devineur n'a pas encore soumis sa devinette ; s'arrête dès que la devinette
    est soumise. Durée proportionnelle au temps total choisi à la création (ex. 15s pour une
    partie 5 min/side, 30s pour 10 min/side — ratio à figer précisément, éventuellement aussi
    fonction du bonus par coup). À expiration sans devinette soumise : aucune devinette n'est
    considérée faite et le coup réel est simplement joué (résolution identique au cas
    "devinette incorrecte", sans pénalité de temps côté devineur au-delà de ne pas avoir
    deviné) — le round se résout sans attendre davantage, ce qui reste cohérent avec la règle
    "le serveur ne doit jamais exposer une soumission avant que les deux soient là" puisqu'il
    n'y a alors jamais de seconde soumission à attendre.

    **Portée mode asynchrone** : les deux timers tels que décrits (décompte live à la
    seconde) n'ont de sens qu'en temps réel — en asynchrone les joueurs ne sont pas connectés
    simultanément, donc ni perte au temps classique ni fenêtre de devinette chronométrée à la
    seconde près. Reste une question ouverte séparée si l'asynchrone doit avoir son
    propre contrôle de temps (ex. temps par coup en jours, façon correspondance) ; hors
    périmètre de cette étape.

13. ✅ Tutoriel des règles (fait) : `HowToPlayView.vue` (`/how-to-play`), contenu FR/EN
    statique, aucun backend. Réutilise `ChessBoard.vue` en `disabled` avec des positions codées
    en dur. Trois exemples via les props de surbrillance existantes de `ChessBoard`
    (devinette incorrecte, devinette correcte, Guessmate) ; Guessmate présenté comme seule
    variante (pas de mention du choix GUESSCHESS/GUESSMATE de l'accueil).

14. ✅ Identifiant unique de compte (login) (fait) : pseudonyme immuable, 3-20 caractères,
    unique insensible à la casse (index `lower(login)`, migration V9), interdit sur
    "Anonymous"/"Anonyme". `login` nullable en SQL pour les comptes créés avant cette étape
    (bloqués côté front sur `/choose-login` jusqu'à `PATCH /api/account/login`) ; un nouveau
    compte n'est jamais inséré sans login (inscription en deux temps via un JWT
    "pending_registration", `RegistrationController`/`POST /api/registration/complete`).
    `display_name`/`bio` modifiables séparément (`ProfileAboutView.vue`). Login affiché
    au-dessus/en-dessous du plateau (`PlayerLabel.vue`, tenu à jour en direct par
    `PlayersBroadcastService` sur `/topic/games/{id}/players`), "Anonyme"/"Anonymous" en
    italique pour les identités anonymes. Recherche de profil/invitation directe/amis :
    toujours hors périmètre.

    **Convention d'affichage** : partout où le login d'un compte est affiché côté front
    (`PlayerLabel.vue`, `ProfileAboutView.vue`, `PublicProfileView.vue`), il est précédé d'un
    `@` (ex. `@Deterson`) — jamais pour un nom d'affichage ou une identité anonyme.

    **Profil public** (ajouté après coup) : `/profile/{login}` (`PublicProfileView.vue`), en
    lecture seule et public (sans authentification), distinct de `/my-profile/*` (l'ancien
    `/profile/*`, renommé pour libérer ce chemin) qui reste réservé au compte connecté pour
    éditer ses propres infos. Backend : `GET /api/players/{login}` et
    `/api/players/{login}/games`, en dehors de `/api/account/**` donc jamais authentifiés
    (`PlayerProfileController`, `UserRepository.findByLoginIgnoreCase`). Le login affiché en
    partie (`PlayerLabel.vue`) pointe vers ce profil.

## Liaison compte/session ↔ partie (étapes 6-7)

- **Lien immuable, à usage unique** : chaque partie référence, par couleur, un compte (`userId`)
  ou une identité de session anonyme (jamais les deux), posé une fois et jamais changé. `Game`
  porte l'identifiant de joueur (bounded context "Partie") ; la résolution de cet identifiant
  (quel compte, ou gestion du cookie anonyme) reste côté "Compte joueur".
- **Identité anonyme** : cookie HttpOnly signé côté serveur (pas de JWT en localStorage), longue
  durée, réutilisé pour toutes les parties du même navigateur. Fusion anonyme → compte au login
  (étape 8) : tous les `game_access` de l'identité anonyme du cookie sont réécrits vers le
  compte — seule exception à l'immuabilité ci-dessus.
- **Page d'accueil (étape 7)** : choix de couleur à la création ; créateur et adversaire passent
  par la même modale "se connecter / jouer en anonyme". Un seul lien de partie (`/game/{gameId}`,
  sans jeton) envoyé à l'adversaire, pas de lien par couleur.
- **Le lien reste ouvrable par tout le monde en spectateur**, mais une fois une couleur revendiquée
  par un adversaire, toute nouvelle tentative échoue (`409 GAME_FULL`).
- **Flux de l'adversaire** : résolu d'abord en spectateur via `GET /api/games/{id}/my-access`, puis
  lié immédiatement et immuablement au clic sur "Rejoindre" — compte si déjà connecté, sinon après
  la modale connexion/anonyme.

## Variables d'environnement (depuis l'étape 4)

⚠️ Toutes celles marquées **obligatoire** doivent être définies pour que l'application démarre
tout court (échec rapide voulu au boot Spring, pas seulement au moment du login) :

- `POSTGRES_USER` / `POSTGRES_PASSWORD` — identifiants Postgres (dev local via `docker-compose.yml`, valeur par défaut `guesschess`/`guesschess`).
- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` — **obligatoire**. App OAuth Google Cloud Console (redirect URI : `http://localhost:8080/login/oauth2/code/google`). Spring Security valide la présence de ces identifiants au démarrage dès que la registration `google` est déclarée dans `application.properties`, même s'ils ne servent qu'au moment du login.
- `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` — **obligatoire**, même remarque. App OAuth GitHub Developer Settings (redirect URI : `http://localhost:8080/login/oauth2/code/github`).
- `JWT_SECRET` — **obligatoire**. Secret HMAC pour signer les JWT (≥ 32 octets aléatoires, ex. `openssl rand -base64 32`).
- `OAUTH_POST_LOGIN_REDIRECT_URI` — URL du frontend vers laquelle rediriger après login, JWT en fragment d'URL (`#token=...`). Défaut : `http://localhost:5173/oauth-callback`.
- `ANONYMOUS_COOKIE_SECURE` — (étape 6) `true`/`false`, flag `Secure` du cookie d'identité anonyme `guesschess_anon`. Défaut `false` (dev local en HTTP) ; mettre `true` derrière HTTPS (étape 10).
  Piège déjà rencontré : un cookie `Secure` posé sur une connexion HTTP simple est silencieusement ignoré au rappel par tout client conforme RFC 6265 — si un test/flux d'identité anonyme échoue étrangement en dev local, vérifier d'abord ce flag avant de chercher plus loin.
  Second piège déjà rencontré (corrigé, étape 14) : ce cookie doit rester en `SameSite=Lax`, jamais `Strict` — le callback OAuth (`/login/oauth2/code/{provider}`) est atteint par une redirection *initiée par* Google/GitHub, donc cross-site du point de vue du navigateur ; un cookie `Strict` y est silencieusement omis, ce qui faisait perdre le lien anonyme → compte pour un joueur qui se connecte en pleine partie jouée anonymement.

## Accès au Raspberry Pi de déploiement (étape 10)

Site public : `https://guesschess.fr`. Le Pi qui héberge la prod est sur le réseau local du
propriétaire — voici ce qu'il faut savoir pour s'y connecter depuis une session de développement.

- **Adresse** : `192.168.1.28` (LAN uniquement, hostname `raspberrypi`), utilisateur SSH `deterson`.
- **Authentification par clé, une clé dédiée par machine** — jamais de mot de passe, jamais de
  clé copiée d'une machine à l'autre :
  - Clé attendue : `~/.ssh/id_ed25519_guesschess_pi`.
  - **Si cette clé n'existe pas sur la machine courante** (plusieurs machines servent au
    développement de ce projet) : ne pas réutiliser ni copier une clé depuis ailleurs. En
    générer une nouvelle (`ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519_guesschess_pi -C
    "<user>-<machine>@guesschess-pi" -N ""`), puis donner sa clé **publique** à l'utilisateur et
    lui demander de l'ajouter lui-même à `~/.ssh/authorized_keys` sur le Pi — l'agent n'a (et ne
    doit pas avoir) de moyen d'accéder au Pi pour s'auto-autoriser.
  - Connexion : `ssh -i ~/.ssh/id_ed25519_guesschess_pi deterson@192.168.1.28`.
- **`sudo`** : `deterson` a un accès complet mais protégé par mot de passe — **ne jamais
  demander/taper ce mot de passe**. Un accès sans mot de passe, restreint à une liste précise de
  commandes utiles au déploiement, est déjà configuré via `/etc/sudoers.d/guesschess-deploy`
  (`mkdir`/`chown` sur `/opt/actions-runner`, `apt-get install -y`, `/opt/actions-runner/svc.sh`,
  `systemctl … actions.runner.*`, `ufw`). Si une commande sudo hors de cette liste devient
  nécessaire, demander à l'utilisateur de l'ajouter lui-même via
  `sudo visudo -f /etc/sudoers.d/guesschess-deploy` (jamais éditer ce fichier directement par un
  autre moyen — une erreur de syntaxe peut bloquer tout `sudo` sur la machine).
- **Emplacements clés sur le Pi** :
  - `/opt/guesschess/.env` — secrets de prod (`chmod 600`, `deterson` uniquement), référencé en
    chemin absolu par `docker-compose.prod.yml`. Voir "Variables d'environnement" ci-dessus pour
    son contenu attendu.
  - `/opt/actions-runner` — runner GitHub Actions auto-hébergé (service systemd
    `actions.runner.Deterson-guesschess.raspberrypi.service`), qui clone le dépôt à chaque job
    dans `_work/guesschess/guesschess` et y exécute le déploiement
    (`docker compose --env-file /opt/guesschess/.env -f docker-compose.prod.yml up -d`).
  - GHCR (images privées) : `docker login ghcr.io` déjà fait sur le Pi avec un PAT
    `read:packages` de l'utilisateur.
- **Ne jamais lancer un `docker compose up` manuel en parallèle** de ce que la CI gère (même
  projet ou un autre dossier) sans le redescendre (`down`) ensuite — deux stacks se disputant les
  ports 80/443 ont déjà cassé un déploiement en laissant un conteneur avec une interface réseau
  jamais attachée.

## Questions encore ouvertes (à trancher plus tard)

- Contrôle de temps en mode asynchrone (voir étape 12 — hors périmètre pour l'instant, le
  décompte live et le timer de devinette à la seconde tels que décrits ne concernent que le
  temps réel)

## Procédée
- Ne jamais laisser tourner le back ou le front à la fin d'un prompt. Cela permet de libérer les ports pour qu'ils puissent être lancées directement depuis la machine locale 
- Cependant il ne faut pas down le docker compose
- Ne jamais toucher à git, l'utilisateur gère les commits / push 
- les ajouts des étapes réalisées sur le CLAUDE.md doivent être succinctes, il ne faut garder que ce qui est pertinent.