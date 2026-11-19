# Packaging script for Morfia on Windows x86_64 (jpackage)
# External FFmpeg/FFprobe binaries are NOT bundled (KD-004).
$ErrorActionPreference = "Stop"

$ProductName = "morfia"
$DisplayName = "Morfia"
$SemVerVersion = "1.0.0-rc.1"
# MSI/EXE upgrade ordering: 0.99.1 (rc.1) < 0.99.2 (rc.2) < 1.0.0 (final)
$JPackageVersion = "0.99.1"
$MainClass = "com.ffmpegstudio.ui.Launcher"
$MainJar = "morfia-$SemVerVersion.jar"
$Vendor = "Emerson Vinicius Santos de Santana"
$UpgradeUuid = "7f4e9b6c-2e1a-4c9a-9f8e-3d5c6b7a8e91"

$RootDir = (Resolve-Path "$PSScriptRoot\..\..").Path
$DistDir = Join-Path $RootDir "dist\$SemVerVersion\windows-x64"
$StageDir = Join-Path $RootDir "target\jpackage-input"
$IconIco = Join-Path $RootDir "packaging\icons\morfia.ico"

New-Item -ItemType Directory -Force -Path $DistDir, $StageDir | Out-Null
Remove-Item -Path "$StageDir\*" -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $RootDir "target\$MainJar") $StageDir -Force
if (Test-Path (Join-Path $RootDir "target\deps")) {
    Copy-Item (Join-Path $RootDir "target\deps\*.jar") $StageDir -Force
}
Copy-Item (Join-Path $RootDir "LICENSE") $StageDir -Force
Copy-Item (Join-Path $RootDir "THIRD_PARTY_NOTICES.md") $StageDir -Force

$IconArgs = @()
if (Test-Path $IconIco) {
    $IconArgs = @("--icon", $IconIco)
}

Write-Host "[1/4] Building Windows app-image..."
$AppImageDest = Join-Path $DistDir "app-image"
if (Test-Path $AppImageDest) { Remove-Item $AppImageDest -Recurse -Force }
jpackage `
  --type app-image `
  --name $DisplayName `
  --app-version $JPackageVersion `
  --vendor $Vendor `
  --input $StageDir `
  --main-jar $MainJar `
  --main-class $MainClass `
  --dest $AppImageDest `
  @IconArgs

Copy-Item (Join-Path $RootDir "LICENSE") (Join-Path $AppImageDest "$DisplayName\") -Force
Copy-Item (Join-Path $RootDir "THIRD_PARTY_NOTICES.md") (Join-Path $AppImageDest "$DisplayName\") -Force

Write-Host "[2/4] Creating portable ZIP..."
$ZipPath = Join-Path $DistDir "$ProductName-$SemVerVersion-windows-x64.zip"
Compress-Archive -Path (Join-Path $AppImageDest "$DisplayName\*") -DestinationPath $ZipPath -Force

$HasWix = (Get-Command "wix.exe" -ErrorAction SilentlyContinue) -or (Get-Command "candle.exe" -ErrorAction SilentlyContinue)
if ($HasWix) {
    Write-Host "[3/4] Building Windows EXE installer..."
    jpackage `
      --type exe `
      --name $DisplayName `
      --app-version $JPackageVersion `
      --vendor $Vendor `
      --input $StageDir `
      --main-jar $MainJar `
      --main-class $MainClass `
      --dest $DistDir `
      --license-file (Join-Path $RootDir "LICENSE") `
      --win-upgrade-uuid $UpgradeUuid `
      --win-dir-chooser `
      --win-menu `
      --win-shortcut `
      @IconArgs

    Write-Host "[4/4] Building Windows MSI installer..."
    jpackage `
      --type msi `
      --name $DisplayName `
      --app-version $JPackageVersion `
      --vendor $Vendor `
      --input $StageDir `
      --main-jar $MainJar `
      --main-class $MainClass `
      --dest $DistDir `
      --license-file (Join-Path $RootDir "LICENSE") `
      --win-upgrade-uuid $UpgradeUuid `
      --win-dir-chooser `
      --win-menu `
      --win-shortcut `
      @IconArgs
} else {
    Write-Host "[3/4] WiX Toolset not found; skipping EXE/MSI installer generation."
}

Get-ChildItem -Path $DistDir -File | Where-Object { $_.Extension -in ".zip", ".exe", ".msi" } | ForEach-Object {
    $hash = (Get-FileHash -Path $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $($_.Name)"
} | Set-Content -Path (Join-Path $DistDir "SHA256SUMS.txt") -Encoding UTF8
