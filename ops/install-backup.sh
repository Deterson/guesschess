#!/usr/bin/env bash
# Installe/assure la présence du backup quotidien de la base (étape 19) - lancé à chaque
# déploiement (voir .github/workflows/deploy.yml), idempotent : ne casse rien si déjà en place,
# rattrape l'installation si les units systemd ont disparu (nouveau Pi, migration...).
#
# Suppose le disque de backup déjà monté à demeure sur l'hôte (mise en place manuelle, une seule
# fois par machine, voir "1. Monter le disque" dans ops/README.md) - seuls le script, sa config
# par défaut et le timer systemd sont (ré)installés ici, rien qui touche au montage du disque.
#
# Nécessite les entrées sudo NOPASSWD documentées dans .github/CLAUDE.md (tee vers les deux units
# systemd, systemctl daemon-reload / enable sur guesschess-backup.*) - sans elles ce script échoue
# et le déploiement échoue avec lui : volontaire, mieux vaut un déploiement qui signale l'absence
# de ces droits qu'un déploiement qui "réussit" sans que le backup ne soit jamais installé.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
install_prefix="${INSTALL_PREFIX:-/opt/guesschess}"

mkdir -p "$install_prefix"
cp "$repo_root/scripts/backup-db.sh" "$install_prefix/backup-db.sh"
chmod +x "$install_prefix/backup-db.sh"

backup_env="$install_prefix/backup.env"
if [ ! -f "$backup_env" ]; then
    cat > "$backup_env" <<EOF
BACKUP_DIR=${BACKUP_DIR:-/mnt/backup-hdd/guesschess}
RETENTION_DAYS=${RETENTION_DAYS:-30}
POSTGRES_CONTAINER=${POSTGRES_CONTAINER:-guesschess-postgres-1}
POSTGRES_USER=${POSTGRES_USER:-guesschess}
POSTGRES_DB=${POSTGRES_DB:-guesschess}
EOF
    chmod 600 "$backup_env"
    echo "$backup_env créé avec des valeurs par défaut - vérifier BACKUP_DIR si le point de montage diffère."
else
    echo "$backup_env déjà présent, laissé tel quel."
fi

sudo tee /etc/systemd/system/guesschess-backup.service < "$repo_root/ops/systemd/guesschess-backup.service" > /dev/null
sudo tee /etc/systemd/system/guesschess-backup.timer < "$repo_root/ops/systemd/guesschess-backup.timer" > /dev/null
sudo systemctl daemon-reload
sudo systemctl enable --now guesschess-backup.timer

echo "Backup installé/à jour :"
systemctl list-timers guesschess-backup.timer --no-pager
