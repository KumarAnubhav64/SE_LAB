#!/usr/bin/env bash

set -euo pipefail

APP_NAME="HtmlEditorFX"
VERSION="$(date +%Y.%m.%d-%H%M)"
RELEASE_DIR="release/${APP_NAME}-${VERSION}"

echo "Version: ${VERSION}"
mkdir -p "${RELEASE_DIR}"

./build.sh
./appimage.sh "${RELEASE_DIR}"

echo "===================================="
echo "RELEASE COMPLETE"
echo "Location: ${RELEASE_DIR}"
echo "===================================="
