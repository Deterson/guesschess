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
   comptes joueurs en OAuth uniquement (Google/GitHub), sessions JWT stateless pour les
   endpoints REST du contexte "Compte joueur". Game/GameAccess sont passés de l'implémentation
   en mémoire à Postgres (voir `infrastructure/persistence/jpa`) ; le flux WebSocket/
   `PlayerToken` du contexte "Partie" reste inchangé et non authentifié pour l'instant — le
   lien compte↔partie (historique) est prévu à l'étape 6 (voir section dédiée plus bas).
5. ✅ Frontend VueJS 3 (échiquier interactif, client WebSocket, UI de devinette) (fait)
6. ✅ Compte joueur lié à une partie (fait) : `game_access` porte désormais, par couleur, un lien
   optionnel-puis-immuable vers un `PlayerRef` (compte ou identité anonyme) — jamais les deux,
   jamais modifié une fois posé (`GameAccess.withPlayerLinked`, écriture SQL conditionnelle
   `... where white_player_type is null`). Identité anonyme : cookie HttpOnly signé HMAC
   (`AnonymousIdentityFilter`, réutilise `app.jwt.secret`), résolue au handshake WebSocket
   (`AnonymousIdentityHandshakeInterceptor`). Compte : JWT en header `Authorization` du CONNECT
   STOMP (`JwtStompChannelInterceptor`, absent de la requête HTTP handshake elle-même — les
   navigateurs n'autorisent pas de header custom dessus). Le lien se pose au premier coup/
   devinette soumis par chaque couleur (`GameLifecycleService.submitMove/submitGuess(..., PlayerRef)`) ;
   aucun choix de couleur ni flux d'invitation n'existe encore (prévu étape 7), donc la création de
   partie elle-même ne lie personne pour l'instant. Le flux WebSocket reste par ailleurs non
   authentifiant à ce stade (jeton = seul mécanisme d'autorisation) : l'identité résolue n'est
   encore que la métadonnée à lier, pas un contrôle d'accès — durci à l'étape 7.
7. ✅ Page d'accueil — création de partie (fait, avec durcissement ultérieur) : choix de couleur
   (blancs/noirs/aléatoire) sur `HomeView`, création via REST (`POST /api/games`, pas STOMP — doit
   s'enchaîner avec une redirection OAuth complète du navigateur) qui lie immédiatement le
   créateur à la couleur choisie (`GameLifecycleService.createGame(variant, color, creator)`).
   **Un seul lien par partie** (`/game/{gameId}`, jamais de jeton dans une URL) : quiconque l'ouvre
   est résolu par sa seule identité via `GET /api/games/{id}/my-access`
   (`GameLifecycleService.findMyAccess`) — déjà lié → joueur ; pas lié → spectateur avec un bouton
   « Rejoindre cette partie » qui revendique l'unique siège encore libre
   (`POST /api/games/{id}/join`, sans corps, `GameLifecycleService.joinGame(gameId, requester)` ;
   partie déjà complète → `409 GAME_FULL`, `NoOpenColorException`). Le contrôle d'accès aux
   coups/devinettes a été durci en conséquence : `GameLifecycleService.requireOwnership` rejette
   (`NotYourColorException`) toute action dont l'identité ne correspond pas à celle déjà liée à la
   couleur — le jeton (`PlayerToken`) redevient un détail d'implémentation interne (routage dans
   les messages STOMP `submitMove`/`submitGuess`), jamais exposé côté client ; ce filet de sécurité
   protège contre un jeton forgé même s'il ne peut plus fuiter par une URL. Résolution d'identité
   HTTP compte/anonyme via `HttpPlayerIdentityResolver`, symétrique à `WebSocketPlayerIdentity`
   côté STOMP. L'endpoint STOMP `/app/games.create` existant n'a pas été touché (toujours utilisé
   par les tests d'intégration STOMP), simplement plus appelé par le frontend. UI de login complète
   ajoutée (elle n'existait pas avant cette étape) : `AuthModal` (composant partagé create/join),
   `OAuthCallbackView` (`/oauth-callback`, lit le JWT dans le fragment d'URL), store `auth` (JWT en
   localStorage), intention différée (`services/pendingAction.js`, sessionStorage) pour reprendre
   création/join après une redirection OAuth complète (sortie du SPA).
8. Page de profil (historique des parties, nom de joueur, etc.). Détail de ce qui est prévu
   (état actuel du code vérifié avant d'écrire cette section : aucun header/nav n'existe
   encore — `App.vue` ne rend qu'un `<router-view>` et un footer statique ; le router
   (`frontend/src/router/index.ts`) n'a que `/`, `/game/:gameId`, `/oauth-callback`, sans
   aucun garde de route ; `GET /api/account/me` existe déjà et renvoie `id`/`displayName`/
   `email` ; rien côté backend ne permet aujourd'hui de lister les parties d'un joueur).

   **Header global**, ajouté sur toutes les pages (nouveau composant, ex. `AppHeader.vue`,
   monté une fois dans `App.vue` au-dessus du `<router-view>`) : à gauche "Jouer" (renvoie
   vers `/`, la `HomeView` existante, inchangée) et "Tutoriel" (nouvelle route, ex.
   `/how-to-play` — pour cette étape, page **volontairement vide**, juste "tutoriel en
   cours" centré en petit ; le contenu réel est le périmètre de l'étape 13 déjà décrite
   plus bas, cette étape-ci ne fait que poser le lien et la route vide) ; à droite "Profil"
   si `authStore.isLoggedIn`, sinon "Connexion" (ouvre le même `AuthModal` que
   création/join, ou redirige vers `/` si plus simple à cadrer techniquement).

   **Page Profil** (nouvelle route, ex. `/profile`) : **réservée aux comptes connectés**,
   pas accessible à un joueur anonyme — protégée à la fois côté front (garde de route,
   inexistante aujourd'hui donc à créer, qui redirige vers `/` si `!authStore.isLoggedIn`)
   et côté back (les nouveaux endpoints listés ci-dessous exigent un JWT valide, 401/403
   sinon — aucune donnée de profil ne doit être atteignable via un cookie anonyme seul).
   En haut : nom du joueur (`GET /api/account/me`, déjà existant, champ `displayName`).
   À gauche : menu, pour l'instant seulement "Mes parties".

   **"Mes parties"** (nouvelle route, ex. `/profile/games`) : liste déroulante des parties
   passées et en cours du compte. Nécessite une nouvelle capacité backend absente
   aujourd'hui — `GameAccessRepository` n'expose que `save`/`findByToken`/`findByGameId`/
   `linkPlayer`, rien d'indexé par joueur — donc une requête "toutes les parties où ce
   `PlayerRef` (compte) apparaît, triées par récence" à ajouter, exposée par un nouvel
   endpoint protégé (ex. `GET /api/account/games?cursor=...&limit=...`). "Fetch
   intelligent qui limite le nombre de parties envoyées" = pagination par curseur (ou
   offset simple pour la v1), pas tout l'historique d'un coup — le nombre de parties d'un
   compte actif peut grossir indéfiniment. Chaque ligne : miniature de l'état final (ou
   actuel si en cours) du plateau — même logique de réutilisation du composant échiquier
   en lecture seule que prévu à l'étape 13 (pas d'images statiques à maintenir), alimentée
   par la dernière position de `positionHistory` de la partie ; couleur de fond de la
   ligne selon l'issue **du point de vue de ce compte** : vert si gagnée, rouge si perdue,
   gris si nulle, noir/normal si toujours en cours (dérivé de `GameResultCause` côté
   Game + de la couleur jouée par ce compte). Pour l'instant, seule autre information
   affichée : le nom de l'adversaire. D'autres colonnes viendront plus tard (date, contrôle
   de temps une fois l'étape 12 faite, etc.) — pas la peine de figer le format de ligne
   dès maintenant au-delà de ces champs.

   **Nom affiché (`display_name`)** : la colonne existe déjà (`V3__create_users_table.sql`,
   `users.display_name`), posée une seule fois à la création du compte à partir du nom
   fourni par Google/GitHub (`AccountService.findOrCreateByOAuthIdentity`) et jamais
   retouchée aux logins suivants. Cette étape la rend **modifiable par l'utilisateur**
   depuis la page de profil (nouvel endpoint, ex. `PATCH /api/account/me`), avec une
   contrainte minimale de 3 caractères. Ce `display_name` reste pour l'instant un simple
   nom d'affichage libre (pas unique, pas garanti stable) — voir l'étape 14 pour la
   distinction avec le futur identifiant unique et immuable, un sujet volontairement
   séparé de celui-ci.

   **Point de vigilance — modale "comment voulez-vous jouer" pour un utilisateur déjà
   connecté** : vérifié que ce n'est *pas* déjà géré — `HomeView.openModal()` affiche
   aujourd'hui `AuthModal` inconditionnellement, y compris quand `authStore.isLoggedIn`
   est déjà vrai. À corriger dans cette étape : si un JWT valide est présent, la création
   (et le join) d'une partie doivent se faire directement avec l'identité du compte déjà
   connu, sans jamais afficher la modale de choix connexion/anonyme.

   **Point de vigilance — fusion identité anonyme → compte** : si un visiteur qui a déjà
   les droits d'une ou plusieurs parties en tant qu'anonyme (cookie `guesschess_anon`) se
   connecte ensuite avec un compte Google/GitHub, tous les `game_access` actuellement liés
   à cette identité anonyme doivent être **réécrits pour pointer vers le compte** plutôt que
   dupliqués ou laissés orphelins côté "Mes parties" — sans quoi ces parties resteraient
   invisibles depuis le profil du compte. Point de bascule naturel : au moment du login
   réussi (`OAuthLoginSuccessHandler`, qui a déjà accès à la requête donc au cookie anonyme
   en cours), après find-or-create du compte, relier en une fois tous les `game_access` de
   cette `AnonymousId` au `UserId` du compte (nouvelle capacité repository à ajouter, le
   pendant "réécriture" de `linkPlayer`). **Exception explicite** à la règle d'immuabilité
   du lien posée aux étapes 6/7 ("jamais modifié une fois posé") : ce cas précis de fusion
   anonyme → compte est la seule situation où un `game_access` déjà lié change de titulaire
   après coup ; à documenter comme tel dans le code (commentaire sur la méthode concernée)
   pour ne pas surprendre un lecteur qui s'attend à l'invariant habituel.
9. ✅ Dockerisation (fait) : `Dockerfile` racine (backend, multi-stage `eclipse-temurin:25-jdk`
   → `25-jre`, wrapper Maven car pas de tag `maven:*-eclipse-temurin-25` garanti, JVM bornée par
   `-XX:MaxRAMPercentage=75.0`) et `frontend/Dockerfile` (multi-stage `node:22-alpine` → build servi
   par `nginx:1.27-alpine`). `frontend/nginx.conf` proxifie `/api`, `/ws`, `/oauth2`, `/login` vers le
   backend en **same-origin** — `api.ts`/`stompClient.ts` retombent sur des URLs relatives en prod
   (au lieu de `localhost:8080` en dur), donc une même image fonctionne derrière n'importe quel
   domaine sans le connaître au moment du build ; CORS devient obsolète en prod. `spring-boot-starter-actuator`
   ajouté (`/actuator/health` seul exposé, `permitAll`) pour un vrai `HEALTHCHECK` Docker plutôt qu'un
   test TCP aveugle. `docker-compose.prod.yml` : postgres sans port publié, `image:`+`build:` sur
   backend/frontend (même fichier sert au build local et au `pull` depuis GHCR à l'étape 10),
   `depends_on: condition: service_healthy` en chaîne, `.env.prod.example` documenté. Vérifié
   localement (build + up + partie anonyme jouée de bout en bout via le proxy nginx, coup envoyé et
   round persisté en base). **Piège à retenir** : `docker-compose.prod.yml` et le `docker-compose.yml`
   de dev partagent le même nom de projet Compose par défaut (dossier `Guesschess`) donc le même
   volume Postgres si lancés depuis le même dossier sans `-p` — utiliser un nom de projet distinct
   (`docker compose -p ... -f docker-compose.prod.yml ...`) pour tester la stack prod en local sans
   toucher aux données de dev.
10. Format PGGN (Portable Game Guess Notation) — notation inspirée du PGN, avec le
    coup deviné entre parenthèses juste après le coup réel (ex. `1. e4(e3) e5(Nc6)`).
    Si la devinette est correcte (round annulé, coup réel == deviné par définition),
    seule la devinette entre parenthèses apparaît (`2. (Nf3) Nc6(a5)`) — pas de
    redondance puisque coup réel et deviné sont alors identiques. Si aucune devinette
    n'a été soumise, les parenthèses sont omises (juste `e4`). `+`/`#` uniquement sur
    un coup réellement joué qui met en échec/mat ; jamais sur une devinette dans un
    round annulé non-terminal (le plateau revient à l'état d'avant, rien à tester) —
    sauf le cas terminal Guessmate (roi capturé via devinette correcte en échec,
    ex. `16. (Ke2)#`), seul cas où l'annulation elle-même termine la partie. En-têtes
    façon PGN (`[Event]`, `[Date]`, `[White]`/`[Black]` à `"?"` tant que la page profil
    de l'étape 8 n'existe pas, `[Variant]`, `[Result]`, `[Termination]` — ce dernier
    portant directement la valeur de `GameResultCause`, plus précis qu'un `[Result]`
    seul qui ne distingue pas la cause). Parseur en extraction simple (pas de
    revalidation contre le moteur de règles) : un .pggn réimporté n'est donc pas une
    source de vérité fiable. Exposition probable via `GET /api/games/{id}/pggn`.

    **Implique de changer le stockage de l'état de partie** : `Game`/`GameStateJson`
    ne gardent aujourd'hui que `lastRoundResult` (le dernier round), pas l'historique
    complet — à remplacer par un `roundHistory` (liste de `RoundResult`, un par round,
    y compris les rounds annulés), qui devient la source de vérité pour reconstruire
    le PGGN (avec l'aide de `positionHistory`, qui a déjà un instantané par round,
    pour la désambiguïsation SAN). Une fois `roundHistory` en place, `moveHistory`
    devient redondant (dérivable des rounds où `movePlayed = true`) et doit être
    supprimé plutôt que maintenu en double, plutôt que gardé comme information
    dupliquée à synchroniser à la main.

11. ✅ Historique de partie navigable (fait), façon lichess.org : contrairement à
    l'hypothèse initiale de cette étape, une **nouvelle donnée structurée a bien dû
    remonter du backend** — `GameStateMessage.moveHistory`/`RoundSummaryMessage`
    n'exposaient que les coups réellement joués (pas les devinettes, pas les rounds
    annulés, pas de plateau par round), insuffisant pour une liste façon PGGN et un
    fantôme de devinette ; `positionHistory`/`Game.roundHistoryWithPositions()`
    existaient déjà côté domaine (étape 10) mais n'étaient consommés que par
    `PggnWriter` (texte brut), jamais exposés en JSON. Nouvel endpoint
    `GET /api/games/{gameId}/history` (lecture seule, sans jeton, même posture que
    `/pggn`/`/my-access`) : `GameHistoryHttpResponse`/`GameHistoryEntryHttpResponse`
    (`infrastructure/web/dto`), construits dans `GameCreationController` à partir de
    `GameLifecycleService.gameHistory` (nouveau, retourne un `GameHistorySnapshot`
    nested record) et de `Game.roundHistoryWithPositions()`. Réutilise telles quelles
    la recette SAN de `PggnWriter.toPly` (rendue `public`, seul changement sur ce
    fichier) et la conversion plateau→JSON de `GameMessageMapper.toBoardCells`
    (rendue `public`) — pas de nouvelle logique de sérialisation dupliquée.
    `GameSnapshot`/`GameStateMessage` gagnent un champ `roundCount` (nombre de rounds
    déjà résolus) pour que le frontend sache quand refetch l'historique détaillé sans
    diffuser tout cet historique sur chaque message d'état live.

    Front : `MoveHistoryList.vue` réécrit pour consommer cet historique détaillé et
    rendre une notation façon PGGN cliquable (`1. e4(d4) e5(Nc6)`, mêmes règles
    d'omission que `PggnWriter.renderPly`), avec une entrée "Position de départ".
    `ChessBoard.vue` gagne une prop `ghostMove` dédiée (rendu `opacity-40 grayscale`,
    délibérément distincte de `hoverGuess` qui reste l'affichage opaque du survol
    manuel en direct sur `RoundResultBanner`) pour le fantôme de la devinette en
    navigation historique. Store `game` : `historyRounds`/`historyInitialBoard`/
    `historyIndex` (`null`=direct, `-1`=position de départ, `0..n-1`=après le round i),
    navigation `null ↔ n-1 ↔ ... ↔ -1` symétrique aux deux bouts. Navigation clavier
    ←/→ (`GameView.vue`, ignorée si le focus est dans le champ de chat) en plus du
    clic. Cliquer le dernier coup de la liste renvoie explicitement à `null` (direct)
    plutôt qu'à son propre index, conformément à l'exigence ci-dessous. Un round
    résolu pendant la navigation met à jour `historyRounds` en silence (`watch` sur
    `roundCount`) sans jamais changer `historyIndex` tant que l'utilisateur n'a pas
    cliqué lui-même le dernier coup. Vérifié de bout en bout dans un vrai navigateur
    (création de partie, deux rounds résolus dont un round annulé, navigation
    clavier/souris, fantôme affiché, plateau non-interactif pendant la navigation).

    **Point de vigilance corrigé après coup** :
    `myAccessRecoversTheTokenFromTheAnonymousCookieAloneAfterLosingTheUrl`
    (`GameCreationControllerIntegrationTest`) échouait de façon reproductible (404 au
    lieu de 200). Cause réelle : `app.anonymous-cookie.secure` valait `true` par
    défaut dans `application.properties` (et dans le `@Value` de
    `AnonymousIdentityFilter`), alors que `.env.example`/`.env.prod.example` et la
    section "Variables d'environnement" documentaient déjà un défaut `false` — un
    cookie `Secure` posé sur une connexion HTTP simple (dev local, et le client HTTP
    du test) est silencieusement ignoré au rappel par tout client conforme RFC 6265
    (`java.net.CookieManager` y compris), donc le second appel ne renvoyait jamais le
    cookie, une identité anonyme différente était générée, et `/my-access` ne
    trouvait logiquement aucun accès lié. Corrigé en alignant les deux défauts sur
    `false`.

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

13. Tutoriel des règles : lien depuis le header global (posé à l'étape 8, qui crée déjà la
    route `/how-to-play` avec un contenu vide "tutoriel en cours") vers une page dédiée,
    route publique côté front, aucune auth requise — au même titre que la lecture d'une
    partie en spectateur — qui explique le concept du jeu en texte, illustré
    par des échiquiers représentant des positions d'exemple (coup réel vs devinette révélés
    côte à côte, cas devinette correcte → coup annulé et tour qui passe au devineur, cas
    devinette incorrecte → coup joué normalement, cas particulier Guessmate → roi capturé).
    Plutôt que des images statiques à maintenir séparément, réutiliser le composant
    d'échiquier déjà existant (étape 5) en mode non-interactif/lecture seule, alimenté par
    des positions figées codées en dur pour chaque exemple — reste cohérent visuellement
    avec le vrai plateau (mêmes assets de pièces, même thème) et évite un jeu d'images à
    régénérer si le thème du plateau change. Contenu purement statique, pas de backend
    impliqué.

14. Identifiant unique de compte (login) : à choisir une seule fois, à la toute première
    connexion Google ou GitHub (écran dédié avant d'entrer dans l'app, une seule fois par
    compte), **immuable** une fois posé — distinct du `display_name` libre et modifiable
    posé à l'étape 8, qui reste par ailleurs inchangé (il sera peut-être supprimé plus tard
    au profit de ce login unique, décision à prendre à ce moment-là, pas avant). Objectif :
    permettre à deux joueurs de se retrouver l'un l'autre de façon fiable (recherche de
    profil, invitation directe, futur système d'amis...), ce qu'un simple nom d'affichage
    modifiable et potentiellement dupliqué entre comptes ne permet pas. Détails de
    validation (charset, longueur, unicité en base) et d'écran de sélection à préciser au
    moment d'attaquer cette étape — non développée ici au-delà de cette intention.

## Liaison compte/session ↔ partie (étapes 6-7)

- **Lien immuable** : chaque partie référence, par couleur, soit un compte (`userId`), soit une
  identité de session anonyme — jamais les deux, et jamais modifié une fois posé. Pas de
  "changement de joueur" en cours de partie.
- **Identité anonyme** : cookie **HttpOnly signé côté serveur** (pas de JWT en localStorage, pour
  éviter l'exposition XSS), longue durée (~1 an), régénéré uniquement si absent. Réutilisée pour
  toutes les parties jouées depuis le même navigateur, ce qui permet un mini-historique pour les
  joueurs non connectés sans nécessiter de compte — mais la page de profil (étape 8) est elle-même
  réservée aux comptes connectés, donc ce mini-historique anonyme n'est exposé nulle part pour
  l'instant. Fusion anonyme → compte : tranchée à l'étape 8 (voir cette section) — au login, tous
  les `game_access` liés à l'identité anonyme du cookie en cours sont réécrits vers le compte, seule
  exception documentée à l'immuabilité du lien décrite juste au-dessus.
- **Page d'accueil (étape 7)** : choix de couleur à la création (blancs / noirs / aléatoire). Que
  le créateur soit déjà connecté ou non, il passe par la **même modale** "se connecter / jouer en
  anonyme" que l'adversaire avant que la partie soit effectivement créée et son lien posé —
  comportement symétrique entre les deux joueurs.
- Une fois la partie créée : redirection du créateur vers l'URL de sa partie, et bannière affichant
  ce **même lien** (`/game/{gameId}`, sans jeton) à envoyer à l'adversaire — un seul lien par
  partie, pas de lien séparé par couleur (voir étape 7 pour le pourquoi : le jeton a cessé d'être
  le mécanisme d'autorisation, il n'a donc plus de raison d'être dans une URL).
- **Le lien reste valide pour tout le monde, la liaison est à usage unique** : n'importe qui peut
  toujours ouvrir `/game/{gameId}` (lecture seule, mode spectateur, pas de jeton nécessaire), mais
  une fois qu'un adversaire (compte ou anonyme) a revendiqué le siège encore libre, toute nouvelle
  tentative de revendication échoue clairement (`409 GAME_FULL`) — pas de second joueur possible
  sur la même couleur.
- **Flux de l'adversaire visitant le lien** : d'abord résolu comme spectateur via
  `GET /api/games/{id}/my-access` (identité pas encore liée). S'il clique "Rejoindre cette
  partie" : déjà connecté → son compte est immédiatement et immuablement lié (pas de modale).
  Sinon, modale "se connecter" ou "jouer en anonyme" ; le choix qui en résulte (compte après
  OAuth, ou identité de session anonyme) est immédiatement et immuablement lié à la partie.
- **Bounded context** : le lien lui-même (partie ↔ compte/anonyme) vit côté "Partie" (le `Game`
  référence un identifiant de joueur), la résolution de cet identifiant (qui est ce compte, ou
  gestion du cookie anonyme) reste du ressort du contexte "Compte joueur".

## Variables d'environnement (depuis l'étape 4)

⚠️ Toutes celles marquées **obligatoire** doivent être définies pour que l'application démarre
tout court (échec rapide voulu au boot Spring, pas seulement au moment du login) :

- `POSTGRES_USER` / `POSTGRES_PASSWORD` — identifiants Postgres (dev local via `docker-compose.yml`, valeur par défaut `guesschess`/`guesschess`).
- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` — **obligatoire**. App OAuth Google Cloud Console (redirect URI : `http://localhost:8080/login/oauth2/code/google`). Spring Security valide la présence de ces identifiants au démarrage dès que la registration `google` est déclarée dans `application.properties`, même s'ils ne servent qu'au moment du login.
- `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` — **obligatoire**, même remarque. App OAuth GitHub Developer Settings (redirect URI : `http://localhost:8080/login/oauth2/code/github`).
- `JWT_SECRET` — **obligatoire**. Secret HMAC pour signer les JWT (≥ 32 octets aléatoires, ex. `openssl rand -base64 32`).
- `OAUTH_POST_LOGIN_REDIRECT_URI` — URL du frontend vers laquelle rediriger après login, JWT en fragment d'URL (`#token=...`). Défaut : `http://localhost:5173/oauth-callback`.
- `ANONYMOUS_COOKIE_SECURE` — (étape 6) `true`/`false`, flag `Secure` du cookie d'identité anonyme `guesschess_anon`. Défaut `false` (dev local en HTTP) ; mettre `true` derrière HTTPS (étape 10).

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