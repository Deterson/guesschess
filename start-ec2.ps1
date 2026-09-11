$ErrorActionPreference="Stop"

<#
.SYNOPSIS
    Démarre une instance EC2 AWS, identifiée par son ID ou par son adresse IP publique fixe (Elastic IP).

.NOTES
    Prérequis :
    1) Module AWS Tools for PowerShell :
         Install-Module -Name AWS.Tools.EC2 -Scope CurrentUser
    2) Un profil de credentials AWS configuré avec un utilisateur IAM dédié
       (PAS le compte root !) ayant les droits ec2:DescribeInstances et ec2:StartInstances :
         Set-AWSCredential -AccessKey "AKIA..." -SecretKey "..." -StoreAs "mon-profil-aws"
#>

# ---------------- À adapter ----------------
$ProfileName = "start-ec2-postgres"     # nom du profil créé avec Set-AWSCredential
$Region      = "eu-west-3"          # région AWS où se trouve l'instance
$InstanceId  = ""                   # optionnel : ex "i-0123456789abcdef0" (laisser vide pour chercher via l'IP)
$PublicIp    = "35.180.147.199"       # IP publique fixe de l'instance
# --------------------------------------------

Import-Module AWS.Tools.EC2 -ErrorAction Stop

if (-not $InstanceId) {
    $instance = Get-EC2Instance -Filter @{ Name = "ip-address"; Values = @($PublicIp) } `
                             -ProfileName $ProfileName -Region $Region |
            Select-Object -ExpandProperty Instances

    if (-not $instance) {
        Write-Error "Aucune instance trouvée avec l'IP publique $PublicIp"
        exit 1
    }
    $InstanceId = $instance.InstanceId
}

$state = (Get-EC2InstanceStatus -InstanceId $InstanceId -IncludeAllInstance $true `
            -ProfileName $ProfileName -Region $Region).InstanceState.Name

if ($state -eq "running") {
    Write-Host "L'instance $InstanceId est déjà démarrée."
} else {
    Write-Host "Démarrage de l'instance $InstanceId..."
    Start-EC2Instance -InstanceId $InstanceId -ProfileName $ProfileName -Region $Region | Out-Null

    do {
        Start-Sleep -Seconds 5
        $state = (Get-EC2InstanceStatus -InstanceId $InstanceId -IncludeAllInstance `
                    -ProfileName $ProfileName -Region $Region).InstanceState.Name
        Write-Host "État actuel : $state"
    } while ($state -ne "running")

    Write-Host "Instance démarrée. IP publique : $PublicIp"
}