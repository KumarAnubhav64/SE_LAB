#!/usr/bin/env bash

set -euo pipefail

REPO_DIR="apt-repo"
BUILD_OUTPUT_DIR="build-deb/output"

echo "Initializing APT Repository inside ./${REPO_DIR}"

mkdir -p "${REPO_DIR}/pool/main"
mkdir -p "${REPO_DIR}/dists/stable/main/binary-amd64"

if ls "${BUILD_OUTPUT_DIR}"/*.deb 1> /dev/null 2>&1; then
    echo "Found .deb package(s), copying to pool directory..."
    cp "${BUILD_OUTPUT_DIR}"/*.deb "${REPO_DIR}/pool/main/"
else
    echo "No .deb packages found in ${BUILD_OUTPUT_DIR}. Did you run ./build-deb.sh?"
    exit 1
fi

echo "Scanning packages and generating metadata..."
cd "${REPO_DIR}"
dpkg-scanpackages --arch amd64 pool/ > dists/stable/main/binary-amd64/Packages
cat dists/stable/main/binary-amd64/Packages | gzip -9c > dists/stable/main/binary-amd64/Packages.gz

# Note: We are creating a simple unsigned repository.
cat <<EOF > dists/stable/Release
Architectures: amd64
Components: main
Suite: stable
EOF

echo "APT Repository generated successfully in ./${REPO_DIR}."
echo ""
echo "To host this on GitHub Pages, just commit the apt-repo directory to a branch mapped to Pages."
echo "Users can then use your repo by running:"
echo "  echo \"deb [trusted=yes] https://<username>.github.io/<repo>/apt-repo stable main\" | sudo tee /etc/apt/sources.list.d/htmleditorfx.list"
echo "  sudo apt update"
echo "  sudo apt install htmleditorfx"
