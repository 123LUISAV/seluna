param([string]$Repositorio = 'seluna')

$ErrorActionPreference = 'Stop'
$cuenta = '123LUISAV'
$destino = "$cuenta/$Repositorio"
$proyecto = Split-Path -Parent $PSScriptRoot

if ($Repositorio -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$') { throw 'Nombre de repositorio inválido.' }
if (Test-Path Env:GH_TOKEN) { throw 'Abre una terminal nueva sin GH_TOKEN antes de publicar.' }
if (Test-Path Env:GITHUB_TOKEN) { throw 'Abre una terminal nueva sin GITHUB_TOKEN antes de publicar.' }

Push-Location -LiteralPath $proyecto
try {
    $rama = git branch --show-current
    if ($LASTEXITCODE -ne 0 -or $rama -ne 'main') { throw 'Abre la rama main del proyecto.' }
    $pendiente = git status --porcelain
    if ($LASTEXITCODE -ne 0 -or $pendiente) { throw 'El proyecto debe tener sus cambios guardados en commits.' }

    Write-Host "Publicación privada en $destino. Pega un token temporal de GitHub de $cuenta y pulsa Entrar."
    $secreto = Read-Host 'Token de GitHub (oculto)' -AsSecureString
    if ($secreto.Length -eq 0) { $secreto.Dispose(); throw 'Ingresa un token de tu cuenta de GitHub.' }
    $puntero = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secreto)
    try { $env:GH_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($puntero) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($puntero); $secreto.Dispose() }

    $usuario = gh api --hostname github.com user --jq .login
    if ($LASTEXITCODE -ne 0 -or $usuario -ne $cuenta) { throw "La cuenta autenticada debe ser $cuenta." }

    $cabeceras = @{ Authorization = "Bearer $env:GH_TOKEN"; Accept = 'application/vnd.github+json' }
    $existe = $true
    try { $null = Invoke-RestMethod -Uri "https://api.github.com/repos/$destino" -Headers $cabeceras }
    catch {
        if ([int]$_.Exception.Response.StatusCode -ne 404) { throw }
        $existe = $false
    } finally { $cabeceras.Clear() }
    if (-not $existe) {
        gh repo create $destino --private --description 'Seluna · Beta Android de Tecnologías Móviles'
        if ($LASTEXITCODE -ne 0) { throw 'GitHub no permitió crear el repositorio privado.' }
    }

    $privado = gh api --hostname github.com "repos/$destino" --jq '.private and (.owner.login == "123LUISAV")'
    if ($LASTEXITCODE -ne 0 -or $privado -ne 'true') { throw 'GitHub no confirmó que el destino sea privado y de Luisa.' }

    $url = "https://github.com/$destino.git"
    git -c 'credential.helper=' -c 'credential.https://github.com.helper=!gh auth git-credential' push $url main:main
    if ($LASTEXITCODE -ne 0) { throw 'El push falló. Revisa el permiso de escritura o si ya existe otra historia en main.' }
    $local = git rev-parse HEAD
    $remoto = gh api --hostname github.com "repos/$destino/branches/main" --jq .commit.sha
    if ($LASTEXITCODE -ne 0 -or $local -ne $remoto) { throw 'GitHub no confirmó el commit publicado.' }
    Write-Host "Publicación verificada: https://github.com/$destino"
} finally {
    Remove-Item Env:GH_TOKEN -ErrorAction SilentlyContinue
    Pop-Location
}
