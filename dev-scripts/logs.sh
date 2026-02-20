#!/bin/bash

# Configuration
PACKAGE_NAME="com.tazztone.losslesscut"
ADB="/home/tazztone/Android/Sdk/platform-tools/adb"

PID=$($ADB shell pidof -s $PACKAGE_NAME)

if [ -z "$PID" ]; then
    echo "⚠️ $PACKAGE_NAME is not running. Starting it now..."
    $ADB shell am start -n $PACKAGE_NAME/.MainActivity
    sleep 1
    PID=$($ADB shell pidof -s $PACKAGE_NAME)
fi

echo "📋 Streaming logs for $PACKAGE_NAME (PID: $PID)..."
$ADB logcat --pid=$PID
