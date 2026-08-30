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
- Seule la soumission finale (coup ou devinette validé) part vers le serveur — les interactions UI (déplacer une pièce, hésiter) restent côté client. Le serveur revalide systématiquement chaque soumission (jamais confiance dans le client).

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
8. Page de profil (historique des parties, nom de joueur, etc.)
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
10. Déploiement sur le Raspberry Pi (reverse proxy, HTTPS, limites mémoire/CPU, dimensionnement JVM)
11. Tests et peaufinage (tests unitaires du moteur, tests de la mécanique de devinette, UX)
12. Format PGGN (Portable Game Guess Notation) — notation inspirée du PGN, avec le
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

## Liaison compte/session ↔ partie (étapes 6-7)

- **Lien immuable** : chaque partie référence, par couleur, soit un compte (`userId`), soit une
  identité de session anonyme — jamais les deux, et jamais modifié une fois posé. Pas de
  "changement de joueur" en cours de partie.
- **Identité anonyme** : cookie **HttpOnly signé côté serveur** (pas de JWT en localStorage, pour
  éviter l'exposition XSS), longue durée (~1 an), régénéré uniquement si absent. Réutilisée pour
  toutes les parties jouées depuis le même navigateur, ce qui permet un mini-historique pour les
  joueurs non connectés (utile pour la page de profil, étape 8) sans nécessiter de compte. Fusion
  ultérieure anonyme → compte : hors scope v1, à trancher plus tard si besoin.
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

## Questions encore ouvertes (à trancher plus tard)

- Mode spectateur
- Gestion du temps / timer par coup
- Fusion d'une identité anonyme vers un compte après coup (ex. un joueur qui a joué en anonyme se
  connecte ensuite et voudrait récupérer son historique) — hors scope v1 (voir section "Liaison
  compte/session ↔ partie")

## Procédée
- Ne jamais laisser tourner le back ou le front à la fin d'un prompt. Cela permet de libérer les ports pour qu'ils puissent être lancées directement depuis la machine locale 
- Cependant il ne faut pas down le docker compose
- Ne jamais toucher à git, l'utilisateur gère les commits / push 