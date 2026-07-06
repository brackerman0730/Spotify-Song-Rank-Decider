# ============================================================
#  Rankify — Run Script (PowerShell)
# ============================================================

$JavaFxLib  = "C:\javafx-sdk-26.0.1\lib"
$OutDir     = "out"
$MainClass  = "com.rankify.Main"

if (-not (Test-Path "$OutDir\com\rankify\Main.class")) {
    Write-Host "No compiled classes found. Running compile first..." -ForegroundColor Yellow
    & "$PSScriptRoot\compile.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not (Test-Path "$JavaFxLib\javafx.controls.jar")) {
    Write-Host "*** ERROR: JavaFX not found at $JavaFxLib" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Launching Rankify ===" -ForegroundColor Cyan
& java `
    --module-path $JavaFxLib `
    --add-modules javafx.controls,java.desktop `
    -cp $OutDir `
    $MainClass