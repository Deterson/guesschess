# Projet : Échecs avec règle de devinette

> Ce fichier couvre le contexte transverse (concept, architecture, roadmap, règles de session). Le
> détail par thème vit dans des `CLAUDE.md` imbriqués, que Claude Code charge automatiquement
> seulement quand il travaille dans le dossier correspondant : [`frontend/CLAUDE.md`](frontend/CLAUDE.md)
> (Vue/Vite, conventions front), [`src/CLAUDE.md`](src/CLAUDE.md) (Java/Spring, moteur d'échecs,
> variables d'environnement), [`.github/CLAUDE.md`](.github/CLAUDE.md) (CI/déploiement Pi).

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
- Le Docker de dev (Postgres inclus) tourne sur une VM distante, pas en local sur la machine de dev Windows — accès SSH, piège JDK/Testcontainers : voir [`src/CLAUDE.md`](src/CLAUDE.md). Piège Node/Vite : voir [`frontend/CLAUDE.md`](frontend/CLAUDE.md).

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
- Convention `api.ts` (retry/gestion de session expirée) : voir [`frontend/CLAUDE.md`](frontend/CLAUDE.md).

## Performance et scalabilité

- Pièges de performance du moteur d'échecs (détection d'échec, cache des coups légaux, recherche de pièces...) : voir [`src/CLAUDE.md`](src/CLAUDE.md).

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

1. ✅ Modéliser le moteur d'échecs pur en Java 25 (domaine, sans Spring ni réseau)
2. ✅ Modéliser la règle de devinette comme extension du moteur (état du round, résolution)
3. ✅ Architecture applicative Spring Boot (couches, WebSocket vs STOMP, cycle de vie d'une partie)
4. ✅ Persistance et comptes joueurs (fait) — PostgreSQL/JPA/Flyway, OAuth, JWT stateless. Détail : [`src/CLAUDE.md`](src/CLAUDE.md).
5. ✅ Frontend VueJS 3 (échiquier interactif, client WebSocket, UI de devinette) (fait)
6. ✅ Compte joueur lié à une partie (fait) — voir "Liaison compte/session ↔ partie" ci-dessous.
7. ✅ Page d'accueil — création de partie (fait) — voir "Liaison compte/session ↔ partie" ci-dessous et [`frontend/CLAUDE.md`](frontend/CLAUDE.md).
8. ✅ Page de profil (fait) — header global, "Mes parties", nom modifiable, fusion identité anonyme → compte au login. Détail et pièges rencontrés : [`src/CLAUDE.md`](src/CLAUDE.md) (backend) et [`frontend/CLAUDE.md`](frontend/CLAUDE.md) (frontend).
9. ✅ Dockerisation (fait) — images multi-stage back/front, JVM bornée, healthcheck actuator. Détail : [`.github/CLAUDE.md`](.github/CLAUDE.md).
10. ✅ Format PGGN (Portable Game Guess Notation) (fait) — notation façon PGN avec le coup deviné entre parenthèses après le coup réel (`e4(e3)`). Détail : [`src/CLAUDE.md`](src/CLAUDE.md).
11. ✅ Historique de partie navigable (fait), façon lichess.org. Détail : [`src/CLAUDE.md`](src/CLAUDE.md) (endpoint) et [`frontend/CLAUDE.md`](frontend/CLAUDE.md) (composants, navigation clavier).
12. Timers (temps de réflexion + timer de devinette) — spec complète dans [`src/CLAUDE.md`](src/CLAUDE.md) (le backend pilote le décompte, jamais le client).
13. ✅ Tutoriel des règles (fait) — page statique FR/EN, aucun backend. Détail : [`frontend/CLAUDE.md`](frontend/CLAUDE.md).
14. ✅ Identifiant unique de compte (login) (fait) — pseudonyme immuable 3-20 caractères, profil public `/profile/{login}`. Détail : [`src/CLAUDE.md`](src/CLAUDE.md) (backend) et [`frontend/CLAUDE.md`](frontend/CLAUDE.md) (convention d'affichage, routes).

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

## Questions encore ouvertes (à trancher plus tard)

- Contrôle de temps en mode asynchrone (voir étape 12 — hors périmètre pour l'instant, le
  décompte live et le timer de devinette à la seconde tels que décrits ne concernent que le
  temps réel)

## Procédée

- Ne jamais laisser tourner le back ou le front à la fin d'un prompt. Cela permet de libérer les ports pour qu'ils puissent être lancées directement depuis la machine locale.
- Cependant il ne faut pas down le docker compose.
- **Git** : en session locale interactive, ne jamais toucher à git — l'utilisateur gère les
  commits/push. **Exception** : une session Claude Code **distante** (conteneur cloud éphémère,
  ex. lancée depuis claude.ai/code) peut et doit committer/pousser elle-même sur la branche
  demandée.
- Les ajouts des étapes réalisées sur les CLAUDE.md doivent être succincts, il ne faut garder que ce qui est pertinent.
