# CI / Déploiement — contexte spécifique

> Chargé par Claude Code uniquement quand il travaille sur des fichiers sous `.github/` (ex.
> `workflows/deploy.yml`). Contexte général du projet : [`../CLAUDE.md`](../CLAUDE.md).

## Dockerisation (étape 9, fait)

[`../Dockerfile`](../Dockerfile) (backend, multi-stage, JVM bornée par `-XX:MaxRAMPercentage`) et
[`../frontend/Dockerfile`](../frontend/Dockerfile) (multi-stage, servi par nginx qui proxifie
`/api`, `/ws`, `/oauth2`, `/login` vers le backend en **same-origin** — CORS obsolète en prod, une
même image fonctionne derrière n'importe quel domaine). `spring-boot-starter-actuator` pour un
vrai `HEALTHCHECK` Docker (`/actuator/health`). [`../docker-compose.prod.yml`](../docker-compose.prod.yml) :
postgres sans port publié, `depends_on: condition: service_healthy` en chaîne.

**Piège à retenir** : `docker-compose.prod.yml` et le `docker-compose.yml` de dev partagent le
même nom de projet Compose par défaut (donc le même volume Postgres) si lancés depuis le même
dossier sans `-p` — utiliser un nom de projet distinct pour tester la stack prod en local sans
toucher aux données de dev.

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
    chemin absolu par `docker-compose.prod.yml`. Voir "Variables d'environnement" dans
    [`../src/CLAUDE.md`](../src/CLAUDE.md) pour son contenu attendu.
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
