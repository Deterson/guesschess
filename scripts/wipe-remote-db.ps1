<#
.SYNOPSIS
    Wipe la base Postgres de dev sur la VM AWS EC2 distante (35.180.147.199).

.DESCRIPTION
    Se connecte en SSH a la VM Docker distante et reinitialise le schema "public" de la
    base "guesschess" (DROP SCHEMA public CASCADE ; CREATE SCHEMA public), ce qui supprime
    toutes les tables/donnees. Flyway recree le schema depuis les migrations au prochain
    demarrage du backend (application.properties pointe par defaut sur cette meme base
    partagee - voir CLAUDE.md).

    ATTENTION : cette base est potentiellement utilisee par d'autres sessions/devs en
    parallele. Action destructive et irreversible.

.PARAMETER Force
    Ignore la confirmation interactive (utile en script/CI). A eviter en usage manuel.

.EXAMPLE
    .\scripts\wipe-remote-db.ps1
#>

[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$RemoteHost = "ubuntu@35.180.147.199"
$RemoteKey = Join-Path $HOME ".ssh\guesschess-dev-docker.pem"
$ContainerName = "postgres-dev"
$DbName = "guesschess"
$DbUser = "guesschess"

if (-not (Test-Path $RemoteKey)) {
    Write-Error "Cle SSH introuvable : $RemoteKey"
    exit 1
}

if (-not $Force) {
    Write-Warning "Ceci va DEFINITIVEMENT supprimer toutes les donnees de la base '$DbName' sur $RemoteHost (conteneur '$ContainerName')."
    Write-Warning "Cette base peut etre utilisee par d'autres sessions/devs en ce moment meme."
    $confirmation = Read-Host "Tape 'WIPE' pour confirmer"
    if ($confirmation -ne "WIPE") {
        Write-Host "Annule."
        exit 0
    }
}

Write-Host "==> Reinitialisation du schema 'public' de la base '$DbName'..."

$sql = "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO $DbUser; GRANT ALL ON SCHEMA public TO public;"

# Guillemets simples (et non `"..."`) autour de $sql : PowerShell 5.1 mele mal les guillemets
# doubles imbriques lors du passage d'arguments a un executable natif (ssh.exe) - ils arrivent
# corrompus cote distant (voir l'erreur "extra command-line argument" de psql). Passer la
# commande distante entiere comme UNE seule variable evite aussi le probleme.
$remoteCmd = "docker exec -i $ContainerName psql -U $DbUser -d $DbName -c '$sql'"

& ssh -i $RemoteKey -o ConnectTimeout=10 $RemoteHost $remoteCmd

if ($LASTEXITCODE -ne 0) {
    Write-Error "Echec du wipe (code de sortie $LASTEXITCODE)."
    exit $LASTEXITCODE
}

Write-Host "==> OK : base '$DbName' videe. Les migrations Flyway la recreeront au prochain demarrage du backend."
