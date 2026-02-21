#!/bin/bash

# Configuration
PACKAGE_NAME="com.tazztone.losslesscut"
MAIN_ACTIVITY=".ui.MainActivity"
ADB="/home/tazztone/Android/Sdk/platform-tools/adb"

echo "🚀 Building and installing debug APK..."
./gradlew installDebug

if [ $? -eq 0 ]; then
    echo "✅ Install successful. Launching $PACKAGE_NAME..."
    $ADB shell am start -n $PACKAGE_NAME/$MAIN_ACTIVITY
else
    echo "❌ Build or Install failed."
    exit 1
fi
