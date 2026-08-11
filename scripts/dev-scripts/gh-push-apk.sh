#!/bin/bash
set -euo pipefail

# Navigate to project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

# Check dependencies
if ! command -v gh &> /dev/null; then
    echo "❌ GitHub CLI (gh) is not installed. Please install it to push releases."
    exit 1
fi

DATE_TAG=$(date +"%Y%m%d-%H%M%S")
RELEASE_TAG="v$DATE_TAG"
RELEASE_TITLE="Manual Release $RELEASE_TAG"

# Determine build variant and target path based on keystore configuration
if [ -n "${ANDROID_KEYSTORE_PASSWORD:-}" ] && [ -n "${ANDROID_KEY_ALIAS:-}" ] && [ -n "${ANDROID_KEY_PASSWORD:-}" ]; then
    echo "🚀 Building signed release APK..."
    BUILD_TASK="assembleRelease"
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
else
    echo "🚀 Building debug APK..."
    BUILD_TASK="assembleDebug"
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

./gradlew "$BUILD_TASK"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK not found at $APK_PATH"
    exit 1
fi

echo "📦 Pushing APK to GitHub Releases ($RELEASE_TAG)..."
# Create a full release on GitHub using the gh CLI (without -p / --prerelease flag)
gh release create "$RELEASE_TAG" "$APK_PATH" -t "$RELEASE_TITLE" -n "Automated manual release build." --generate-notes

echo "✅ Successfully created release: https://github.com/tazztone/lossless-video-cut/releases/tag/$RELEASE_TAG"
