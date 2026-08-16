#!/bin/bash
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/lossless-cut-gradle}"

CLEAN_CACHE=false
if [[ "${1:-}" == "--all" || "${1:-}" == "--cache" || "${1:-}" == "-a" ]]; then
    CLEAN_CACHE=true
fi

echo "🧹 Cleaning Gradle project build outputs..."
./gradlew clean

if [ "$CLEAN_CACHE" = true ]; then
    echo "🧹 Wiping Gradle persistent build cache and transform directories..."
    rm -rf "$GRADLE_USER_HOME/caches/build-cache-1" \
           "$GRADLE_USER_HOME/caches/transforms-4" \
           "$GRADLE_USER_HOME/caches/jars-9" 2>/dev/null || true
fi

if [ $? -eq 0 ]; then
    echo "✅ Project cleaned successfully."
else
    echo "❌ Clean failed."
    exit 1
fi
