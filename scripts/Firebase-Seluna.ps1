param([switch]$Cerrar)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$session = Join-Path $project 'entrega/firebase-luisa-cli'

if ($Cerrar) {
    $files = @('configstore/firebase-tools.json', 'firebase-debug.log')
    foreach ($file in $files) {
        $target = Join-Path $session $file
        if (Test-Path -LiteralPath $target -PathType Leaf) { Remove-Item -LiteralPath $target -Force }
    }
    Write-Host 'Credenciales temporales de Firebase retiradas.'
    return
}

New-Item -Path $session -ItemType Directory -Force | Out-Null
$env:XDG_CONFIG_HOME = $session
$env:CLOUDSDK_CONFIG = Join-Path $session 'gcloud'
$env:GOOGLE_APPLICATION_CREDENTIALS = Join-Path $session 'sin-credenciales-adc.json'
Remove-Item Env:FIREBASE_TOKEN, Env:GOOGLE_CLOUD_PROJECT, Env:GCLOUD_PROJECT -ErrorAction SilentlyContinue
$Host.UI.RawUI.WindowTitle = 'Firebase de Luisa - seluna-app'
Set-Location -LiteralPath $session

Write-Host 'Abre el enlace que muestra Firebase en incógnito. Elige tu cuenta y pega el código en esta terminal.'
firebase login --no-localhost --reauth
if ($LASTEXITCODE -ne 0) { throw 'El acceso temporal a Firebase quedó pendiente.' }

$projects = firebase projects:list --json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or -not ($projects.result | Where-Object projectId -eq 'seluna-app')) {
    throw 'Esta cuenta no pudo verificar acceso al proyecto seluna-app.'
}
Write-Host 'Acceso verificado al proyecto seluna-app. Las órdenes administrativas deben usar --project seluna-app.'
