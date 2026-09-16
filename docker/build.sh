#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "======================================================================"
echo "[Tip] If 'git pull' fails before build (e.g. tag clobber or local diff):"
echo "      * Quick reset (Recommended): git fetch --tags -f && git reset --hard origin/master"
echo "      * Force pull tags:           git pull --tags -f"
echo "      * One-click alias:           git config --global alias.sync '!git fetch --tags -f && git reset --hard origin/master'"
echo "======================================================================"
echo ""
echo "==> Building JTrac NG Docker image from context: .."
docker build -f Dockerfile -t jtrac-ng:latest -t jtrac-ng:3.0.0-beta ..
echo "==> Build complete! Images: jtrac-ng:latest, jtrac-ng:3.0.0-beta"
