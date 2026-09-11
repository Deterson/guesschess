# Backup quotidien de la base (étape 19)

Dump Postgres (`docker exec ... pg_dump`) → `/data/guesschess-backups` sur le Pi (le disque du
seedbox, réutilisé — voir plus bas pourquoi), avec rétention (purge des dumps de plus de 30
jours). Pas encore de copie hors du Pi — prévu plus tard (voir [`../CLAUDE.md`](../CLAUDE.md)
étape 19, ex. synchro vers Google Drive).

- [`../scripts/backup-db.sh`](../scripts/backup-db.sh) — le script de backup.
- [`systemd/guesschess-backup.service`](systemd/guesschess-backup.service) et
  [`.timer`](systemd/guesschess-backup.timer) — déclenche à 5h du matin (`Persistent=true`,
  rattrape le run manqué si la machine était éteinte à 5h). `RequiresMountsFor=/data` empêche le
  service de tourner (et d'écrire ailleurs par erreur) si le disque n'est pas monté.
- [`install-backup.sh`](install-backup.sh) — (ré)installe le script, `backup.env` (si absent) et
  les units systemd. **Lancé automatiquement à chaque déploiement**
  ([`../.github/workflows/deploy.yml`](../.github/workflows/deploy.yml)), idempotent.

## Pourquoi `/data` (le disque du seedbox) plutôt qu'un disque dédié

Décision assumée : `/data` (`/dev/sda1`, WD "Elements" NTFS, 3.7 To) est le même disque physique
que celui initialement envisagé comme "dédié" (même UUID `B6682D7B682D3C0D` retrouvé sous deux
noms `sdX` différents selon l'ordre de détection au boot — il n'y a qu'un seul disque "Elements"
sur ce Pi). Ce disque sert déjà de stockage au seedbox (jackett/jellyfin/radarr/sonarr/plex/
transmission/filebrowser) depuis 2 ans sans souci connu jusqu'à un incident ponctuel (disque
disparu du bus USB, résolu par un reboot) - accepté comme rare plutôt que rédhibitoire.

Implication assumée : tant que la copie hors du Pi (Google Drive, pas encore fait) n'existe pas,
le backup local partage son support physique avec les données qu'il est censé protéger - la copie
locale n'est qu'un confort de récupération rapide, pas le filet de sécurité final.

## Ce qui reste manuel, une seule fois par machine

### 1. `/data` monté proprement (fstab, pas juste la crontab root)

`/data` était jusqu'ici monté via une ligne `@reboot mount -t ntfs /dev/sda1 /data` dans la
crontab de `root` - fonctionne, mais fragile (référence `/dev/sda1` en dur plutôt que l'UUID, pas
de `nofail`, et un `@reboot` cron peut s'exécuter avant que le noyau ait fini de détecter le
disque USB). Migration vers une vraie entrée `/etc/fstab` (même comportement de permissions,
`0777`/`root:root`, pour ne rien casser côté seedbox) :

```bash
sudo cp /etc/fstab /etc/fstab.bak-$(date +%F)
echo 'UUID=B6682D7B682D3C0D  /data  ntfs-3g  defaults,uid=0,gid=0,umask=000,windows_names,nofail,x-systemd.device-timeout=30  0  0' \
  | sudo tee -a /etc/fstab
sudo crontab -l -u root | grep -v 'mount -t ntfs /dev/sda1' | sudo crontab -u root -
sudo systemctl daemon-reload
findmnt --verify   # vérifie la syntaxe de fstab sans rien monter
```

`nofail` + `x-systemd.device-timeout=30` : si le disque est absent au boot, le Pi démarre quand
même plutôt que d'attendre indéfiniment. Le nouveau montage prend
effet au prochain remontage (reboot, ou `sudo mount -a` après un `umount` manuel si tu veux tester
sans attendre) - pas besoin de redémarrer immédiatement pour que ce soit en place.

### 2. Autoriser les commandes sudo utilisées par `install-backup.sh`

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
ls -la /data/guesschess-backups
```

`journalctl -u guesschess-backup.service` est l'équivalent du "log d'erreur à consulter" pour ce
backup (voir aussi la remarque étape 18 dans [`../CLAUDE.md`](../CLAUDE.md)). Pas de monitoring
externe (healthchecks.io) pour l'instant — `backup-db.sh` sait pinguer une URL si
`HEALTHCHECK_PING_URL` est renseignée dans `backup.env`, mais rien n'y est mis par défaut.
