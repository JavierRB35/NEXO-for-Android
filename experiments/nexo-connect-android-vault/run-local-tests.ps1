# Nexo Connect pilot: run ALL checks on your Windows PC, no GitHub Actions.
# Run from experiments/nexo-connect-android-vault with:
# powershell -NoProfile -ExecutionPolicy Bypass -File .\run-local-tests.ps1
# Prerequisites: Android Studio/SDK 34, JDK 17 and an already booted AVD.
param([switch]$SkipEmulator, [switch]$IncludeNexoMain, [switch]$MainOnly)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$wrapper = Join-Path $repoRoot 'gradlew.bat'
$cryptoProject = Join-Path $repoRoot 'experiments\nexo-connect-crypto'
$androidProject = Join-Path $repoRoot 'experiments\nexo-connect-android-vault'
if (-not (Test-Path $wrapper)) {
    throw 'Falta gradlew.bat de Gradle 8.9 en la raiz del repositorio.'
}
$env:JAVA_OPTS = '-Xmx768m -XX:MaxMetaspaceSize=256m -Dfile.encoding=UTF-8'
if ($MainOnly) { $IncludeNexoMain = $true }

function Invoke-GradleStep([string]$title, [string]$project, [string[]]$taskNames) {
    Write-Host ""
    Write-Host "NEXO LOCAL: $title" -ForegroundColor Cyan
    & $wrapper '-p' $project @taskNames '--no-daemon' '--max-workers=1' '--console=plain'
    if ($LASTEXITCODE -ne 0) {
        throw "Ha fallado '$title' (Gradle exit code $LASTEXITCODE). No se ejecutaran las fases restantes."
    }
}


function Ensure-SherpaRuntime {
    # Keep exactly the version and SHA256 already pinned in
    # prepare-transcription-runtime.sh; never accept an unverified .aar.
    $folder = Join-Path $repoRoot 'app\libs'
    $dest = Join-Path $folder 'sherpa-onnx-1.13.7.aar'
    $expectedSha = 'c4ef49e309f24fcee5c106b8a279481aaecaabb078cd37b2cd6e9a62cc8a73c8'
    $url = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.7/sherpa-onnx-1.13.7.aar'
    if (Test-Path -LiteralPath $dest) {
        $actualSha = (Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash
        if ($actualSha -ieq $expectedSha) {
            Write-Host 'sherpa-onnx 1.13.7 verificado. No es necesario descargarlo.' -ForegroundColor Green
            return
        }
        throw 'El sherpa-onnx .aar existente NO coincide con su SHA256. No se sustituira automaticamente.'
    }

    if (-not (Test-Path -LiteralPath $folder)) {
        New-Item -ItemType Directory -Path $folder -Force | Out-Null
    }
    $partial = "$dest.download-$PID"
    Write-Host 'Falta sherpa-onnx 1.13.7: descarga oficial unica y comprobacion SHA256...' -ForegroundColor Cyan
    try {
        # Supports Windows PowerShell 5.1 without curl, bash or GitHub Actions.
        Invoke-WebRequest -Uri $url -OutFile $partial -UseBasicParsing -ErrorAction Stop
        $actualSha = (Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash
        if ($actualSha -ine $expectedSha) {
            throw 'SHA256 incorrecto: se rechaza el archivo descargado.'
        }
        Move-Item -LiteralPath $partial -Destination $dest -ErrorAction Stop
        Write-Host 'sherpa-onnx 1.13.7 descargado y verificado correctamente.' -ForegroundColor Green
    } finally {
        if (Test-Path -LiteralPath $partial) {
            Remove-Item -LiteralPath $partial -Force
        }
    }
}

if (-not $MainOnly) {
    Invoke-GradleStep 'Test criptografia Java 17 / Google Tink (sin red)' $cryptoProject @('test')
    Invoke-GradleStep 'Compilar APK piloto de Android (SDK 34)' $androidProject @(':pilot:assembleDebug')
    Invoke-GradleStep 'Compilar instrumentacion Android' $androidProject @(':vault:assembleDebugAndroidTest')
    if (-not $SkipEmulator) {
        Invoke-GradleStep 'Ejecutar TODAS las pruebas en los AVD conectados' $androidProject @(':vault:connectedDebugAndroidTest')
    } else {
        Write-Host 'Pruebas instrumentadas omitidas a peticion (SkipEmulator).' -ForegroundColor Yellow
    }
}
if ($IncludeNexoMain) {
    Ensure-SherpaRuntime
    # Optional complete Nexo DEBUG build; does not install/merge the APK.
    Invoke-GradleStep 'Compilar Nexo completo (DEBUG)' $repoRoot @(':app:assembleDebug')
    $mainApk = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $mainApk)) { throw 'Falta la APK debug de Nexo principal.' }
    Write-Host "APK Nexo debug: $mainApk"
}
if (-not $MainOnly) {
    $apk = Join-Path $androidProject 'pilot\build\outputs\apk\debug\pilot-debug.apk'
    if (-not (Test-Path -LiteralPath $apk)) {
        throw 'Gradle finalizo sin encontrar el APK piloto.'
    }
    Write-Host "APK piloto: $apk"
}
Write-Host ""
Write-Host 'VALIDACION LOCAL TERMINADA. No se ha ejecutado GitHub Actions.' -ForegroundColor Green
