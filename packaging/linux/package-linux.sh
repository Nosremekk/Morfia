#!/usr/bin/env bash
set -euo pipefail

# Packaging script for Morfia on Linux x86_64 (jpackage)
# External FFmpeg/FFprobe binaries are NOT bundled (KD-004).

PRODUCT_NAME="morfia"
DISPLAY_NAME="Morfia"
SEMVER_VERSION="1.0.0-rc.1"
JPACKAGE_VERSION="1.0.0"
MAIN_CLASS="com.ffmpegstudio.ui.Launcher"
MAIN_JAR="morfia-${SEMVER_VERSION}.jar"
VENDOR="Emerson Vinicius Santos de Santana"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DIST_ROOT="${ROOT_DIR}/dist/${SEMVER_VERSION}"
DIST_DIR="${DIST_ROOT}/linux-x64"
STAGE_DIR="${ROOT_DIR}/target/jpackage-input"
ICON_PNG="${ROOT_DIR}/packaging/icons/morfia-256.png"

mkdir -p "${DIST_DIR}" "${STAGE_DIR}"
rm -rf "${STAGE_DIR:?}"/*
cp "${ROOT_DIR}/target/${MAIN_JAR}" "${STAGE_DIR}/"
if [ -d "${ROOT_DIR}/target/deps" ]; then
  cp "${ROOT_DIR}/target/deps/"*.jar "${STAGE_DIR}/" || true
fi
cp "${ROOT_DIR}/LICENSE" "${STAGE_DIR}/"
cp "${ROOT_DIR}/THIRD_PARTY_NOTICES.md" "${STAGE_DIR}/"

ICON_ARGS=()
if [ -f "${ICON_PNG}" ]; then
  ICON_ARGS=(--icon "${ICON_PNG}")
fi

RUNTIME_ARGS=()
JAVA_HOME_DETECTED="${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk}"
MODULES="java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.scripting,java.sql,java.xml,jdk.unsupported"
rm -rf "${ROOT_DIR}/target/runtime"
if [ -d "${JAVA_HOME_DETECTED}/jmods" ]; then
  echo "[info] Building modular Java 25 runtime via jlink (jmods)..."
  jlink --module-path "${JAVA_HOME_DETECTED}/jmods" --add-modules "${MODULES}" --strip-debug --no-man-pages --no-header-files --compress=zip-6 --output "${ROOT_DIR}/target/runtime"
  RUNTIME_ARGS=(--runtime-image "${ROOT_DIR}/target/runtime")
elif jlink --add-modules "${MODULES}" --strip-debug --no-man-pages --no-header-files --compress=zip-6 --output "${ROOT_DIR}/target/runtime" >/dev/null 2>&1; then
  echo "[info] Building modular Java 25 runtime via jlink (JEP 493)..."
  RUNTIME_ARGS=(--runtime-image "${ROOT_DIR}/target/runtime")
elif [ -d "${JAVA_HOME_DETECTED}" ]; then
  echo "[info] jlink runtime-image fallback; using --runtime-image ${JAVA_HOME_DETECTED}"
  rm -rf "${ROOT_DIR}/target/runtime"
  RUNTIME_ARGS=(--runtime-image "${JAVA_HOME_DETECTED}")
fi

echo "[1/4] Building Linux app-image..."
rm -rf "${DIST_DIR}/app-image"
jpackage \
  --type app-image \
  --name "${PRODUCT_NAME}" \
  --app-version "${JPACKAGE_VERSION}" \
  --vendor "${VENDOR}" \
  --input "${STAGE_DIR}" \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --dest "${DIST_DIR}/app-image" \
  --description "Open-source local-first multimedia workstation using external FFmpeg" \
  "${RUNTIME_ARGS[@]}" \
  "${ICON_ARGS[@]}"

cp "${ROOT_DIR}/LICENSE" "${DIST_DIR}/app-image/${PRODUCT_NAME}/"
cp "${ROOT_DIR}/THIRD_PARTY_NOTICES.md" "${DIST_DIR}/app-image/${PRODUCT_NAME}/"

echo "[2/4] Creating portable tar.gz..."
tar -czf "${DIST_DIR}/${PRODUCT_NAME}-${SEMVER_VERSION}-linux-x64.tar.gz" -C "${DIST_DIR}/app-image" "${PRODUCT_NAME}"

if command -v rpmbuild >/dev/null 2>&1; then
  echo "[3/4] Building RPM package..."
  jpackage \
    --type rpm \
    --name "${PRODUCT_NAME}" \
    --app-version "${JPACKAGE_VERSION}" \
    --linux-app-release "0.1.rc1" \
    --vendor "${VENDOR}" \
    --input "${STAGE_DIR}" \
    --main-jar "${MAIN_JAR}" \
    --main-class "${MAIN_CLASS}" \
    --dest "${DIST_DIR}" \
    --license-file "${ROOT_DIR}/LICENSE" \
    --linux-shortcut \
    --linux-menu-group "AudioVideo" \
    --linux-app-category "AudioVideo" \
    "${RUNTIME_ARGS[@]}" \
    "${ICON_ARGS[@]}"
else
  echo "[3/4] Skipping RPM (rpmbuild not found; install via 'sudo dnf install rpm-build')."
fi

if command -v dpkg-deb >/dev/null 2>&1 && command -v fakeroot >/dev/null 2>&1; then
  echo "[4/4] Building DEB package..."
  jpackage \
    --type deb \
    --name "${PRODUCT_NAME}" \
    --app-version "${JPACKAGE_VERSION}" \
    --linux-app-release "0.1.rc1" \
    --vendor "${VENDOR}" \
    --input "${STAGE_DIR}" \
    --main-jar "${MAIN_JAR}" \
    --main-class "${MAIN_CLASS}" \
    --dest "${DIST_DIR}" \
    --license-file "${ROOT_DIR}/LICENSE" \
    --linux-shortcut \
    --linux-menu-group "AudioVideo" \
    --linux-app-category "AudioVideo" \
    "${RUNTIME_ARGS[@]}" \
    "${ICON_ARGS[@]}"
else
  echo "[4/4] Skipping DEB (requires both dpkg-deb and fakeroot; use Ubuntu/Debian CI runner or install fakeroot)."
fi

echo "Generating SHA-256 checksums..."
"${ROOT_DIR}/scripts/generate-checksums.sh" "${DIST_DIR}"
