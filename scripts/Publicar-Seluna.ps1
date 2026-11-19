param([string]$Repositorio = 'seluna', [switch]$SesionEnCurso)

$ErrorActionPreference = 'Stop'
$cuenta = '123LUISAV'
$destino = "$cuenta/$Repositorio"
$proyecto = Split-Path -Parent $PSScriptRoot

if ($Repositorio -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$') { throw 'Nombre de repositorio inválido.' }
$configuracion = if ($SesionEnCurso) {
    Join-Path $proyecto 'entrega/gh-device-session'
} else {
    Join-Path ([IO.Path]::GetTempPath()) ('seluna-gh-' + [guid]::NewGuid().ToString('N'))
}
if ($SesionEnCurso) {
    if (-not (Test-Path -LiteralPath (Join-Path $configuracion 'hosts.yml') -PathType Leaf)) {
        throw 'La sesión temporal todavía no ha sido autorizada.'
    }
} else {
    New-Item -ItemType Directory -Path $configuracion | Out-Null
}

$variables = @{
    GH_CONFIG_DIR = $configuracion
    GH_HOST = 'github.com'
    GH_BROWSER = 'cmd.exe /d /c exit 0'
    GH_PROMPT_DISABLED = '1'
    GH_TOKEN = $null
    GITHUB_TOKEN = $null
    GH_ENTERPRISE_TOKEN = $null
    GITHUB_ENTERPRISE_TOKEN = $null
    GIT_CONFIG_GLOBAL = 'NUL'
    GIT_TERMINAL_PROMPT = '0'
}
$anteriores = @{}
foreach ($nombre in $variables.Keys) {
    $anteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process')
    [Environment]::SetEnvironmentVariable($nombre, $variables[$nombre], 'Process')
}

Push-Location -LiteralPath $proyecto
try {
    $rama = git branch --show-current
    if ($LASTEXITCODE -ne 0 -or $rama -ne 'main') { throw 'Abre la rama main del proyecto.' }
    $pendiente = git status --porcelain
    if ($LASTEXITCODE -ne 0 -or $pendiente) { throw 'El proyecto debe tener sus cambios guardados en commits.' }

    if (-not $SesionEnCurso) {
        Write-Host "Publicación privada en $destino. Abre el enlace en incógnito y autoriza el código con $cuenta."
        gh auth login --hostname github.com --git-protocol https --skip-ssh-key --web --insecure-storage
        if ($LASTEXITCODE -ne 0) { throw 'No se completó el acceso por código a GitHub.' }
    }

    $usuario = gh api --hostname github.com user --jq .login
    if ($LASTEXITCODE -ne 0 -or $usuario -ne $cuenta) { throw "La cuenta autenticada debe ser $cuenta." }

    $env:GH_TOKEN = gh auth token --hostname github.com --user $cuenta
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($env:GH_TOKEN)) { throw 'No se encontró la credencial temporal.' }

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
    try {
        foreach ($archivo in 'hosts.yml', 'config.yml') {
            $ruta = Join-Path $configuracion $archivo
            if (Test-Path -LiteralPath $ruta -PathType Leaf) {
                Remove-Item -LiteralPath $ruta -Force
            }
        }
        if (-not (Get-ChildItem -LiteralPath $configuracion -Force)) {
            [IO.Directory]::Delete($configuracion)
        }
    } finally {
        foreach ($nombre in $anteriores.Keys) {
            [Environment]::SetEnvironmentVariable($nombre, $anteriores[$nombre], 'Process')
        }
        Pop-Location
    }
}
