<#
.SYNOPSIS
    Lance un tournoi entre moteurs d'IA (etape 22) et enregistre le resultat dans results/.

.DESCRIPTION
    Construit (sauf -SkipBuild) puis lance le jar Spring Boot avec le point d'entree
    com.guesschess.tournament.Tournament (voir src/CLAUDE.md). Chaque parametre a une
    valeur par defaut raisonnable : lance sans aucun argument pour un tournoi de
    demonstration immediat, ou ne precise que ceux que tu veux changer. Le JSONL
    resultant est ecrit dans results/result-<timestamp>.json (dossier cree au besoin,
    ignore par git - voir .gitignore).

.PARAMETER Agents
    Specs d'agent separees par ";" (les parametres a l'interieur d'une spec sont separes
    par ","), ex. "minimax@1:hard;guessaware@1:depth=4,guessPlies=2,budgetMillis=1500"

.PARAMETER Variant
    GUESSCHESS ou GUESSMATE.

.PARAMETER Openings
    Nombre de positions de depart generees aleatoirement.

.PARAMETER OpeningPlies
    Nombre de demi-coups aleatoires par position de depart.

.PARAMETER Seed
    Graine du tournoi (positions + RNG des agents). Par defaut basee sur l'heure courante
    (non reproductible d'un run a l'autre) - fixe-la explicitement pour rejouer le meme
    tournoi.

.PARAMETER RoundLimit
    Nombre de rounds max avant d'arreter une partie (comptee nulle dans le rapport).

.PARAMETER Threads
    Nombre de parties jouees en parallele (independantes entre elles). Par defaut le
    nombre de coeurs de la machine.

.PARAMETER StockfishPath
    Chemin du binaire Stockfish - uniquement necessaire si un agent stockfish@N est
    utilise. Par defaut la variable d'environnement STOCKFISH_PATH si elle est definie.

.PARAMETER SkipBuild
    Ne reconstruit pas le jar avant de lancer (reutilise celui deja present dans target/,
    le plus recent s'il y en a plusieurs).

.PARAMETER NoReport
    N'affiche pas le rapport agrege dans la console a la fin (le JSONL est ecrit quand
    meme) - le rapport peut etre regenere plus tard via la sous-commande "report".

.EXAMPLE
    .\scripts\run-tournament.ps1
    Tournoi de demonstration avec toutes les valeurs par defaut.

.EXAMPLE
    .\scripts\run-tournament.ps1 -Agents "minimax@1:hard;guessaware@1:hard" -Openings 8 -Threads 4
    Ne change que les parametres precises, garde les defauts pour le reste.
#>

[CmdletBinding()]
param(
    [string]$Agents = "minimax@1:hard;minimax@2:hard;minimax@3:hard;guessaware@1:hard;negamax-timed@1:maxDepth=6,budgetMillis=1500;stockfish@1:hard",
    [ValidateSet('GUESSCHESS', 'GUESSMATE')]
    [string]$Variant = "GUESSCHESS",
    [int]$Openings = 6,
    [int]$OpeningPlies = 4,
    [long]$Seed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(),
    [int]$RoundLimit = 300,
    [int]$Threads = [Environment]::ProcessorCount,
    # $env:STOCKFISH_PATH (variable d'environnement reelle) d'abord, sinon le chemin documente
    # dans src/CLAUDE.md pour cette machine (.env ne se charge pas tout seul dans ce shell).
    [string]$StockfishPath = $(if ($env:STOCKFISH_PATH) { $env:STOCKFISH_PATH } elseif (Test-Path "C:\Users\drde6\tools\stockfish.exe") { "C:\Users\drde6\tools\stockfish.exe" } else { "" }),
    [switch]$SkipBuild,
    [switch]$NoReport
)

# stockfish@1 par defaut mais aucun binaire trouvable : le retirer plutot que de planter le
# tournoi entier sur cet agent (utile si ce script tourne un jour sur une autre machine).
if (($Agents -match '(?i)stockfish') -and (-not $StockfishPath -or -not (Test-Path $StockfishPath))) {
    Write-Warning "stockfish@1 demande mais aucun binaire Stockfish trouve (-StockfishPath vide/invalide) - retire de la liste d'agents pour ce run."
    $Agents = ($Agents -split ';' | Where-Object { $_ -notmatch '(?i)^\s*stockfish' }) -join ';'
}

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
# Voir src/CLAUDE.md ("Tests Maven qui necessitent Java 25") - a changer si le JDK gere par
# IntelliJ change sur cette machine.
$JavaHome = "C:\Users\drde6\.jdks\ms-25.0.4.1"

Write-Host "Agents        : $Agents"
Write-Host "Variante      : $Variant"
Write-Host "Ouvertures    : $Openings (x$OpeningPlies demi-coups aleatoires)"
Write-Host "Seed          : $Seed"
Write-Host "Round limit   : $RoundLimit"
Write-Host "Threads       : $Threads"
if ($StockfishPath) {
    Write-Host "Stockfish     : $StockfishPath"
}

# --- Dossier de resultats ---

$ResultsDir = Join-Path $RepoRoot "results"
New-Item -ItemType Directory -Force -Path $ResultsDir | Out-Null
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$OutFile = Join-Path $ResultsDir "result-$Timestamp.json"

# --- Build + lancement (JAVA_HOME/Path/repertoire courant restaures a la fin, pas de fuite vers le shell appelant) ---
# Push-Location : mvnw.cmd resout le pom.xml depuis le repertoire COURANT du processus
# appelant (pas depuis son propre emplacement) - sans ca, lancer ce script depuis un
# autre repertoire que la racine du repo (ex. depuis scripts/ lui-meme) echoue avec
# "no POM in this directory".

$originalJavaHome = $env:JAVA_HOME
$originalPath = $env:Path
Push-Location $RepoRoot
try {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$env:Path"

    if (-not $SkipBuild) {
        Write-Host "==> Construction du jar (mvnw package -DskipTests)..." -ForegroundColor Cyan
        & "$RepoRoot\mvnw.cmd" -q -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Echec de la construction du jar (code $LASTEXITCODE)."
            exit $LASTEXITCODE
        }
    }

    $Jar = Get-ChildItem -Path (Join-Path $RepoRoot "target") -Filter "guesschess-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '(sources|javadoc)\.jar$' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $Jar) {
        Write-Error "Aucun jar trouve dans target/ - relance sans -SkipBuild, ou construis-le d'abord (mvnw package)."
        exit 1
    }

    $javaExe = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path $javaExe)) {
        Write-Warning "JDK 25 introuvable a $javaExe (voir src/CLAUDE.md) - utilisation de 'java' du PATH, peut echouer si ce n'est pas un JDK 25."
        $javaExe = "java"
    }

    $JavaArgs = @(
        "-cp", $Jar.FullName,
        "-Dloader.main=com.guesschess.tournament.Tournament",
        "org.springframework.boot.loader.launch.PropertiesLauncher",
        "run",
        "--agents", $Agents,
        "--variant", $Variant,
        "--openings", $Openings,
        "--opening-plies", $OpeningPlies,
        "--seed", $Seed,
        "--round-limit", $RoundLimit,
        "--threads", $Threads,
        "--out", $OutFile
    )
    if ($StockfishPath) {
        $JavaArgs += @("--stockfish-path", $StockfishPath)
    }
    if (-not $NoReport) {
        $JavaArgs += "--report"
    }

    Write-Host "==> Lancement du tournoi..." -ForegroundColor Cyan
    & $javaExe @JavaArgs
    $exitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $originalJavaHome
    $env:Path = $originalPath
    Pop-Location
}

if ($exitCode -eq 0) {
    Write-Host "==> Resultats ecrits dans $OutFile" -ForegroundColor Green
} else {
    Write-Error "Le tournoi a echoue (code de sortie $exitCode)."
}
exit $exitCode
