#!/usr/bin/env bash
set -euo pipefail

echo "Checking repository hygiene..."
if grep -rn "/home/vini" src/ docs/ 2>/dev/null; then
  echo "WARNING: Found personal path references!"
fi

for f in LICENSE THIRD_PARTY_NOTICES.md CHANGELOG.md CONTRIBUTING.md SECURITY.md README.md pom.xml; do
  if [ ! -f "$f" ]; then
    echo "ERROR: Missing required root file: $f"
    exit 1
  fi
done
echo "Repository structural hygiene check passed."
