#!/usr/bin/env bash

set -euo pipefail

APP_NAME="HtmlEditorFX"
ARCH="x86_64"
BUILD_APP_IMAGE_DIR="build/output/${APP_NAME}"
APPDIR="${APP_NAME}.AppDir"
APPIMAGETOOL="appimagetool-${ARCH}.AppImage"
APPIMAGETOOL_URL="https://github.com/AppImage/AppImageKit/releases/download/continuous/${APPIMAGETOOL}"
ICON_SOURCE="src/main/resources/icon.png"
OUTPUT_DESTINATION="${1:-}"

if [[ ! -d "${BUILD_APP_IMAGE_DIR}" ]]; then
    echo "Build output not found at ${BUILD_APP_IMAGE_DIR}. Running build.sh first..."
    ./build.sh
fi

if [[ ! -d "${BUILD_APP_IMAGE_DIR}" ]]; then
    echo "Error: expected app-image directory ${BUILD_APP_IMAGE_DIR} does not exist." >&2
    exit 1
fi

echo "Preparing AppDir..."
rm -rf "${APPDIR}"
mkdir -p "${APPDIR}"
cp -r "${BUILD_APP_IMAGE_DIR}"/* "${APPDIR}/"

echo "Creating AppRun and desktop entry..."
cat > "${APPDIR}/AppRun" << 'EOF'
#!/usr/bin/env bash
HERE="$(dirname "$(readlink -f "$0")")"
exec "${HERE}/bin/HtmlEditorFX"
EOF
chmod +x "${APPDIR}/AppRun"

cat > "${APPDIR}/${APP_NAME}.desktop" << EOF
[Desktop Entry]
Name=${APP_NAME}
Exec=${APP_NAME}
Icon=${APP_NAME}
Type=Application
Categories=Development;
Terminal=false
EOF

if [[ -f "${ICON_SOURCE}" ]]; then
    cp "${ICON_SOURCE}" "${APPDIR}/${APP_NAME}.png"
else
    echo "Warning: ${ICON_SOURCE} not found; creating placeholder icon."
    : > "${APPDIR}/${APP_NAME}.png"
fi

if [[ ! -f "${APPIMAGETOOL}" ]]; then
    echo "Downloading ${APPIMAGETOOL}..."
    wget -O "${APPIMAGETOOL}" "${APPIMAGETOOL_URL}"
    chmod +x "${APPIMAGETOOL}"
fi

echo "Building AppImage..."
"./${APPIMAGETOOL}" --appimage-extract-and-run "${APPDIR}"

OUTPUT_IMAGE="${APP_NAME}-${ARCH}.AppImage"
if [[ ! -f "${OUTPUT_IMAGE}" ]]; then
    echo "Error: AppImage was not generated: ${OUTPUT_IMAGE}" >&2
    exit 1
fi

if [[ -n "${OUTPUT_DESTINATION}" ]]; then
    mkdir -p "${OUTPUT_DESTINATION}"
    mv "${OUTPUT_IMAGE}" "${OUTPUT_DESTINATION}/"
    echo "AppImage created: ${OUTPUT_DESTINATION}/${OUTPUT_IMAGE}"
else
    echo "AppImage created: ${OUTPUT_IMAGE}"
fi

rm -rf "${APPDIR}"
