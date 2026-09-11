#!/usr/bin/env bash
# Sauvegarde quotidienne de la base Postgres de prod (étape 19 de la roadmap) :
# dump -> disque dur externe (rétention locale). Pas encore de copie hors du Pi (prévu plus tard,
# voir CLAUDE.md étape 19 - synchro vers un stockage externe type Google Drive).
#
# Déployé sur le Pi à /opt/guesschess/backup-db.sh par ops/install-backup.sh (lancé à chaque
# déploiement, voir .github/workflows/deploy.yml), exécuté par le timer systemd
# guesschess-backup.timer (voir ops/systemd/). Config attendue dans /opt/guesschess/backup.env
# (non versionné, comme /opt/guesschess/.env) :
#   BACKUP_DIR=/data/guesschess-backups
#   RETENTION_DAYS=30
#   POSTGRES_CONTAINER=guesschess-postgres-1
#   POSTGRES_USER=guesschess
#   POSTGRES_DB=guesschess
#   HEALTHCHECK_PING_URL=https://hc-ping.com/<uuid>   (facultatif - ping de succès/échec si défini,
#                                                       aucune notification sinon)

set -euo pipefail

: "${BACKUP_DIR:?BACKUP_DIR manquant (voir /opt/guesschess/backup.env)}"
: "${RETENTION_DAYS:=30}"
: "${POSTGRES_CONTAINER:=guesschess-postgres-1}"
: "${POSTGRES_USER:=guesschess}"
: "${POSTGRES_DB:=guesschess}"
: "${HEALTHCHECK_PING_URL:=}"

on_error() {
    [ -n "$HEALTHCHECK_PING_URL" ] && curl -fsS -m 10 --retry 3 "${HEALTHCHECK_PING_URL}/fail" -d "backup-db.sh a échoué (voir journalctl -u guesschess-backup)" >/dev/null 2>&1
    true
}
trap on_error ERR

mkdir -p "$BACKUP_DIR"

timestamp="$(date +%F-%H%M%S)"
dump_file="$BACKUP_DIR/guesschess-${timestamp}.sql.gz"

echo "Dump de ${POSTGRES_DB} (conteneur ${POSTGRES_CONTAINER}) vers ${dump_file}"
docker exec "$POSTGRES_CONTAINER" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" | gzip > "$dump_file"

# Un dump vide/corrompu (ex. pg_dump qui échoue silencieusement côté pipe) ne doit jamais être
# pris pour un succès - gzip produit toujours un petit fichier même sur une entrée vide.
if [ "$(stat -c%s "$dump_file")" -lt 200 ]; then
    echo "Dump anormalement petit (< 200 octets), abandon" >&2
    exit 1
fi

echo "Purge des dumps locaux de plus de ${RETENTION_DAYS} jours"
find "$BACKUP_DIR" -name 'guesschess-*.sql.gz' -mtime "+${RETENTION_DAYS}" -delete

if [ -n "$HEALTHCHECK_PING_URL" ]; then
    echo "Ping healthchecks.io (succès)"
    curl -fsS -m 10 --retry 3 "$HEALTHCHECK_PING_URL" >/dev/null
fi

echo "Backup terminé : ${dump_file}"
