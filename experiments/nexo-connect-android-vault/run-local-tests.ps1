# Nexo Connect pilot: run ALL checks on your Windows PC, no GitHub Actions.
# Run from experiments/nexo-connect-android-vault with:
# powershell -NoProfile -ExecutionPolicy Bypass -File .\run-local-tests.ps1
# Prerequisites: Android Studio/SDK 34, JDK 17 and an already booted AVD.
param([switch]$SkipEmulator)

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

function Invoke-GradleStep([string]$title, [string]$project, [string[]]$taskNames) {
    Write-Host ""
    Write-Host "NEXO LOCAL: $title" -ForegroundColor Cyan
    & $wrapper '-p' $project @taskNames '--no-daemon' '--max-workers=1' '--console=plain'
    if ($LASTEXITCODE -ne 0) {
        throw "Ha fallado '$title' (Gradle exit code $LASTEXITCODE). No se ejecutaran las fases restantes."
    }
}

Invoke-GradleStep 'Test criptografia Java 17 / Google Tink (sin red)' $cryptoProject @('test')
Invoke-GradleStep 'Compilar APK piloto de Android (SDK 34)' $androidProject @(':pilot:assembleDebug')
Invoke-GradleStep 'Compilar instrumentacion Android' $androidProject @(':vault:assembleDebugAndroidTest')
if (-not $SkipEmulator) {
    Invoke-GradleStep 'Ejecutar TODAS las pruebas en los AVD conectados' $androidProject @(':vault:connectedDebugAndroidTest')
} else {
    Write-Host 'Pruebas instrumentadas omitidas a peticion (SkipEmulator).' -ForegroundColor Yellow
}
$apk = Join-Path $androidProject 'pilot\build\outputs\apk\debug\pilot-debug.apk'
if (-not (Test-Path $apk)) {
    throw 'Gradle finalizo sin encontrar el APK piloto.'
}
Write-Host ""
Write-Host 'VALIDACION LOCAL TERMINADA. No se ha ejecutado GitHub Actions.' -ForegroundColor Green
Write-Host "APK piloto: $apk"
