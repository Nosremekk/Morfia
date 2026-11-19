#!/usr/bin/env bash
set -euo pipefail

# Packaging script for Morfia on macOS (arm64 or x86_64) via jpackage
# External FFmpeg/FFprobe binaries are NOT bundled (KD-004).

PRODUCT_NAME="morfia"
DISPLAY_NAME="Morfia"
SEMVER_VERSION="1.0.0-rc.1"
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

mkdir -p "${DIST_DIR}" "${STAGE_DIR}"
rm -rf "${STAGE_DIR:?}"/*
cp "${ROOT_DIR}/target/${MAIN_JAR}" "${STAGE_DIR}/"
if [ -d "${ROOT_DIR}/target/deps" ]; then
  cp "${ROOT_DIR}/target/deps/"*.jar "${STAGE_DIR}/" || true
fi
cp "${ROOT_DIR}/LICENSE" "${STAGE_DIR}/"
cp "${ROOT_DIR}/THIRD_PARTY_NOTICES.md" "${STAGE_DIR}/"

echo "[1/2] Building macOS .app bundle (${ARCH_LABEL})..."
jpackage \
  --type app-image \
  --name "${DISPLAY_NAME}" \
  --app-version "${JPACKAGE_VERSION}" \
  --vendor "${VENDOR}" \
  --input "${STAGE_DIR}" \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --dest "${DIST_DIR}/app-image" \
  --mac-package-identifier "${APP_ID}"

echo "[2/2] Building macOS DMG (${ARCH_LABEL})..."
jpackage \
  --type dmg \
  --name "${DISPLAY_NAME}" \
  --app-version "${JPACKAGE_VERSION}" \
  --vendor "${VENDOR}" \
  --input "${STAGE_DIR}" \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --dest "${DIST_DIR}" \
  --mac-package-identifier "${APP_ID}"

"${ROOT_DIR}/scripts/generate-checksums.sh" "${DIST_DIR}"
