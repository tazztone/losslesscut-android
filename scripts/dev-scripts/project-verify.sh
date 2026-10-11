#!/bin/bash
set -euo pipefail
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/lossless-cut-gradle}"

echo "🔍 Running Lint, Unit Tests, and Coverage Report..."
./gradlew detekt
./gradlew testDebugUnitTest
./gradlew lint
./gradlew koverHtmlReport
./gradlew :core:domain:koverVerify
./gradlew :app:processReleaseMainManifest
MANIFEST=app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml
if grep -E "android\.permission\.(INTERNET|ACCESS_NETWORK_STATE)" "$MANIFEST"; then
  echo "❌ Release manifest contains a network permission."
  exit 1
fi
echo "✅ All checks passed successfully."
