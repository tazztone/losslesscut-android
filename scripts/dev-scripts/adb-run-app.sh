#!/bin/bash
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/lossless-cut-gradle}"

# Configuration
PACKAGE_NAME="com.tazztone.losslesscut"
MAIN_ACTIVITY=".ui.MainActivity"

# Use system ADB or fall back
ADB=$(command -v adb || echo "/home/tazztone/Android/Sdk/platform-tools/adb")

# Generate a descriptive dev version with build timestamp and git short SHA
GIT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "dev")
BUILD_TIME=$(date +'%m%d.%H%M')
VERSION_NAME="dev-${GIT_SHA}-${BUILD_TIME}"

echo "🚀 Building and installing debug APK (version: $VERSION_NAME)..."
./gradlew installDebug -PversionName="$VERSION_NAME"

if [ $? -eq 0 ]; then
    echo "✅ Install successful ($VERSION_NAME). Launching $PACKAGE_NAME..."
    $ADB shell am start -n $PACKAGE_NAME/$MAIN_ACTIVITY
else
    echo "❌ Build or Install failed."
    exit 1
fi
