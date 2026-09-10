# Backup quotidien de la base (étape 19)

Dump Postgres (`docker exec ... pg_dump`) → disque dur externe monté sur le Pi, avec rétention
(purge des dumps de plus de 30 jours). Pas encore de copie hors du Pi — prévu plus tard (voir
[`../CLAUDE.md`](../CLAUDE.md) étape 19, ex. synchro vers Google Drive).

- [`../scripts/backup-db.sh`](../scripts/backup-db.sh) — le script de backup.
- [`systemd/guesschess-backup.service`](systemd/guesschess-backup.service) et
  [`.timer`](systemd/guesschess-backup.timer) — déclenche à 5h du matin (`Persistent=true`,
  rattrape le run manqué si la machine était éteinte à 5h). `RequiresMountsFor=/mnt/backup-hdd`
  empêche le service de tourner (et d'écrire ailleurs par erreur) si le disque n'est pas monté.
- [`install-backup.sh`](install-backup.sh) — (ré)installe le script, `backup.env` (si absent) et
  les units systemd. **Lancé automatiquement à chaque déploiement**
  ([`../.github/workflows/deploy.yml`](../.github/workflows/deploy.yml)), idempotent : ne casse
  rien si déjà en place, rattrape l'installation si les units ont disparu (nouveau Pi...).

## Ce qui reste manuel, une seule fois par machine

Deux choses ne peuvent pas être automatisées dans le déploiement (CI non interactif, ne peut pas
taper de mot de passe) :

### 1. Monter le disque de façon persistante

```bash
sudo mkdir -p /mnt/backup-hdd
echo 'UUID=<uuid-du-disque>  /mnt/backup-hdd  ntfs-3g  defaults,uid=1000,gid=1000,umask=002,windows_names,nofail  0  0' \
  | sudo tee -a /etc/fstab
sudo mount -a
touch /mnt/backup-hdd/.write-test && rm /mnt/backup-hdd/.write-test && echo OK
```

(`lsblk -f` pour trouver l'UUID. `nofail` évite qu'un disque débranché bloque le boot.)

Disque actuellement utilisé : `/dev/sdc1` (WD "Elements", NTFS, UUID `B6682D7B682D3C0D`) —
`/dev/sda1`/`/data` a été écarté (montage cassé, sans rapport avec ce disque : c'est le stockage
du seedbox, pas candidat pour ce backup).

### 2. Autoriser les commandes sudo utilisées par `install-backup.sh`

`install-backup.sh` a besoin d'écrire les units systemd et de recharger `systemd` — pas possible
sans mot de passe avec la configuration sudo actuelle du Pi. Ajouter, une seule fois :

```bash
sudo visudo -f /etc/sudoers.d/guesschess-deploy
```

et compléter la ligne `NOPASSWD:` existante avec :

```
/usr/bin/tee /etc/systemd/system/guesschess-backup.service, /usr/bin/tee /etc/systemd/system/guesschess-backup.timer, /usr/bin/systemctl daemon-reload, /usr/bin/systemctl * guesschess-backup.*
```

(détail de la liste complète déjà en place : [`../.github/CLAUDE.md`](../.github/CLAUDE.md)).

Une fois ces deux points faits, tout déploiement suivant installe/rafraîchit le backup tout seul.

## Test manuel (vérifier que ça tourne)

```bash
sudo systemctl start guesschess-backup.service
journalctl -u guesschess-backup.service -n 50 --no-pager
ls -la /mnt/backup-hdd/guesschess
```

`journalctl -u guesschess-backup.service` est l'équivalent du "log d'erreur à consulter" pour ce
backup (voir aussi la remarque étape 18 dans [`../CLAUDE.md`](../CLAUDE.md)). Pas de monitoring
externe (healthchecks.io) pour l'instant — `backup-db.sh` sait pinguer une URL si
`HEALTHCHECK_PING_URL` est renseignée dans `backup.env`, mais rien n'y est mis par défaut.
