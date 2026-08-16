#!/bin/bash
set -euo pipefail
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/lossless-cut-gradle}"

echo "🔍 Running Lint, Unit Tests, and Coverage Report..."
./gradlew detekt
./gradlew testDebugUnitTest
./gradlew lint
./gradlew koverHtmlReport
./gradlew :core:domain:koverVerify
echo "✅ All checks passed successfully."
