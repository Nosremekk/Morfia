# Packaging script for Morfia on Windows x86_64 (jpackage)
# External FFmpeg/FFprobe binaries are NOT bundled (KD-004).
$ErrorActionPreference = "Stop"

$ProductName = "morfia"
$DisplayName = "Morfia"
$SemVerVersion = "1.0.0-rc.1"
$JPackageVersion = "1.0.0"
$MainClass = "com.ffmpegstudio.ui.Launcher"
$MainJar = "morfia-$SemVerVersion.jar"
$Vendor = "Emerson Vinicius Santos de Santana"

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

Write-Host "[1/3] Building Windows app-image..."
jpackage `
  --type app-image `
  --name $DisplayName `
  --app-version $JPackageVersion `
  --vendor $Vendor `
  --input $StageDir `
  --main-jar $MainJar `
  --main-class $MainClass `
  --dest (Join-Path $DistDir "app-image") `
  @IconArgs

Copy-Item (Join-Path $RootDir "LICENSE") (Join-Path $DistDir "app-image\$DisplayName\") -Force
Copy-Item (Join-Path $RootDir "THIRD_PARTY_NOTICES.md") (Join-Path $DistDir "app-image\$DisplayName\") -Force

Write-Host "[2/3] Creating portable ZIP..."
$ZipPath = Join-Path $DistDir "$ProductName-$SemVerVersion-windows-x64.zip"
Compress-Archive -Path (Join-Path $DistDir "app-image\$DisplayName\*") -DestinationPath $ZipPath -Force

if (Get-Command "wix.exe" -ErrorAction SilentlyContinue -or Get-Command "candle.exe" -ErrorAction SilentlyContinue) {
    Write-Host "[3/3] Building Windows EXE and MSI installers..."
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
      --win-dir-chooser `
      --win-menu `
      --win-shortcut `
      @IconArgs
} else {
    Write-Host "[3/3] WiX Toolset not found; skipping EXE/MSI installer generation."
}
