#!/usr/bin/env bash
set -euo pipefail

# Packaging script for Morfia on macOS (arm64 or x86_64) via jpackage
# External FFmpeg/FFprobe binaries are NOT bundled (KD-004).

PRODUCT_NAME="morfia"
DISPLAY_NAME="Morfia"
SEMVER_VERSION="1.0.0-rc.1"
# macOS jpackage requires first version component >= 1
JPACKAGE_VERSION="1.0.0"
MAIN_CLASS="com.ffmpegstudio.ui.Launcher"
MAIN_JAR="morfia-${SEMVER_VERSION}.jar"
VENDOR="Emerson Vinicius Santos de Santana"
APP_ID="io.github.nosremekk.morfia"

ARCH_RAW="$(uname -m)"
ARCH_LABEL="arm64"
if [ "${ARCH_RAW}" = "x86_64" ]; then
  ARCH_LABEL="x64"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist/${SEMVER_VERSION}/macos-${ARCH_LABEL}"
STAGE_DIR="${ROOT_DIR}/target/jpackage-input"
ICONSET_DIR="${ROOT_DIR}/target/morfia.iconset"
ICNS_FILE="${ROOT_DIR}/target/morfia.icns"

mkdir -p "${DIST_DIR}" "${STAGE_DIR}"
rm -rf "${STAGE_DIR:?}"/* "${ICONSET_DIR}"
cp "${ROOT_DIR}/target/${MAIN_JAR}" "${STAGE_DIR}/"
if [ -d "${ROOT_DIR}/target/deps" ]; then
  cp "${ROOT_DIR}/target/deps/"*.jar "${STAGE_DIR}/" || true
fi
cp "${ROOT_DIR}/LICENSE" "${STAGE_DIR}/"
cp "${ROOT_DIR}/THIRD_PARTY_NOTICES.md" "${STAGE_DIR}/"

ICON_ARGS=()
if command -v iconutil >/dev/null 2>&1 && [ -f "${ROOT_DIR}/packaging/icons/morfia-1024.png" ]; then
  mkdir -p "${ICONSET_DIR}"
  cp "${ROOT_DIR}/packaging/icons/morfia-16.png" "${ICONSET_DIR}/icon_16x16.png"
  cp "${ROOT_DIR}/packaging/icons/morfia-32.png" "${ICONSET_DIR}/icon_16x16@2x.png"
  cp "${ROOT_DIR}/packaging/icons/morfia-32.png" "${ICONSET_DIR}/icon_32x32.png"
  cp "${ROOT_DIR}/packaging/icons/morfia-64.png" "${ICONSET_DIR}/icon_32x32@2x.png"
  cp "${ROOT_DIR}/packaging/icons/morfia-128.png" "${ICONSET_DIR}/icon_128x128.png"
  cp "${ROOT_DIR}/packaging/icons/morfia-256.png" "${ICONSET_DIR}/icon_128x128@2x.png"
  cp "${ROOT_DIR}/packaging/icons/morfia-256.png" "${ICONSET_DIR}/icon_256x256.png"
  cp "${ROOT_DIR}/packaging/icons/morfia-512.png" "${ICONSET_DIR}/icon_256x256@2x.png"
  cp "${ROOT_DIR}/packaging/icons/morfia-512.png" "${ICONSET_DIR}/icon_512x512.png"
  cp "${ROOT_DIR}/packaging/icons/morfia-1024.png" "${ICONSET_DIR}/icon_512x512@2x.png"
  iconutil -c icns "${ICONSET_DIR}" -o "${ICNS_FILE}"
  ICON_ARGS=(--icon "${ICNS_FILE}")
fi

echo "[1/3] Building macOS .app bundle (${ARCH_LABEL})..."
rm -rf "${DIST_DIR}/app-image"
jpackage \
  --type app-image \
  --name "${DISPLAY_NAME}" \
  --app-version "${JPACKAGE_VERSION}" \
  --vendor "${VENDOR}" \
  --input "${STAGE_DIR}" \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --dest "${DIST_DIR}/app-image" \
  --mac-package-identifier "${APP_ID}" \
  "${ICON_ARGS[@]}"

echo "[2/3] Building macOS DMG (${ARCH_LABEL})..."
jpackage \
  --type dmg \
  --name "${DISPLAY_NAME}" \
  --app-version "${JPACKAGE_VERSION}" \
  --vendor "${VENDOR}" \
  --input "${STAGE_DIR}" \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --dest "${DIST_DIR}" \
  --mac-package-identifier "${APP_ID}" \
  "${ICON_ARGS[@]}"

echo "[3/3] Building macOS PKG (${ARCH_LABEL})..."
jpackage \
  --type pkg \
  --name "${DISPLAY_NAME}" \
  --app-version "${JPACKAGE_VERSION}" \
  --vendor "${VENDOR}" \
  --input "${STAGE_DIR}" \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --dest "${DIST_DIR}" \
  --license-file "${ROOT_DIR}/LICENSE" \
  --mac-package-identifier "${APP_ID}" \
  "${ICON_ARGS[@]}" || echo "PKG skipped/failed"

"${ROOT_DIR}/scripts/generate-checksums.sh" "${DIST_DIR}"
