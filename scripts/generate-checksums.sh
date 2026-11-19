#!/usr/bin/env bash
set -euo pipefail

TARGET_DIR="${1:-dist}"
if [ ! -d "${TARGET_DIR}" ]; then
  echo "Directory not found: ${TARGET_DIR}"
  exit 1
fi

cd "${TARGET_DIR}"
rm -f SHA256SUMS.txt
found=0
for f in *.tar.gz *.rpm *.deb *.zip *.exe *.msi *.dmg *.pkg; do
  if [ -f "$f" ]; then
    sha256sum "$f" >> SHA256SUMS.txt
    found=1
  fi
done

if [ "${found}" -eq 1 ]; then
  cat SHA256SUMS.txt
else
  echo "No distributable archive files found in ${TARGET_DIR}."
fi
