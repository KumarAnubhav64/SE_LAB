#!/usr/bin/env bash

set -euo pipefail

APP_NAME="htmleditorfx"
MAIN_CLASS="editor.HtmlEditorFX"
APP_VERSION="1.0"
JDK="${JDK:-/usr/lib/jvm/java-17-openjdk-amd64}"
JFX="${JFX:-/usr/share/openjfx/lib}"
BUILD_DIR="build-deb"
INPUT_DIR="${BUILD_DIR}/input"
OUTPUT_DIR="${BUILD_DIR}/output"
CLASS_DIR="${BUILD_DIR}/classes"
SOURCE_DIR="src/main/java"
RESOURCE_DIR="src/main/resources"
MANIFEST_PATH="${BUILD_DIR}/manifest.txt"
JAR_PATH="${INPUT_DIR}/app.jar"

if [[ ! -d "${JFX}" ]]; then
	echo "Error: JavaFX libs directory not found at ${JFX}" >&2
	exit 1
fi

if [[ ! -x "${JDK}/bin/jpackage" ]]; then
	echo "Error: jpackage not found at ${JDK}/bin/jpackage" >&2
	exit 1
fi

if [[ ! -d "${SOURCE_DIR}" ]]; then
	echo "Error: source directory not found at ${SOURCE_DIR}" >&2
	exit 1
fi

echo "Cleaning old build artifacts..."
rm -rf "${BUILD_DIR}"
mkdir -p "${INPUT_DIR}" "${OUTPUT_DIR}" "${CLASS_DIR}"

echo "Compiling Java sources..."
SOURCE_FILES=$(find "${SOURCE_DIR}" -name "*.java" -type f)
if [[ -z "${SOURCE_FILES}" ]]; then
	echo "Error: no Java source files found under ${SOURCE_DIR}" >&2
	exit 1
fi

javac \
	--module-path "${JFX}" \
	--add-modules javafx.controls,javafx.web \
	-d "${CLASS_DIR}" \
	${SOURCE_FILES}

if [[ -d "${RESOURCE_DIR}" ]]; then
	echo "Copying resources..."
	cp -r "${RESOURCE_DIR}"/* "${CLASS_DIR}/"
fi

echo "Creating executable JAR..."
printf 'Main-Class: %s\n' "${MAIN_CLASS}" > "${MANIFEST_PATH}"
jar cfm "${JAR_PATH}" "${MANIFEST_PATH}" -C "${CLASS_DIR}" .

echo "Building jpackage DEB..."
"${JDK}/bin/jpackage" \
	--type deb \
	--name "${APP_NAME}" \
	--app-version "${APP_VERSION}" \
	--input "${INPUT_DIR}" \
	--main-jar "$(basename "${JAR_PATH}")" \
	--main-class "${MAIN_CLASS}" \
	--module-path "${JDK}/jmods:${JFX}" \
	--add-modules javafx.controls,javafx.web,java.net.http,jdk.crypto.ec,jdk.crypto.cryptoki \
	--linux-package-name "${APP_NAME}" \
	--linux-deb-maintainer "developer@localhost" \
	--linux-shortcut \
	--linux-app-category "Development" \
	--description "A JavaFX based HTML Editor" \
	--dest "${OUTPUT_DIR}"

echo "Build complete."
ls -lh "${OUTPUT_DIR}"
