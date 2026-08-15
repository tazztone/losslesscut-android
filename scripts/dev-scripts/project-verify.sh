#!/bin/bash
set -euo pipefail

echo "🔍 Running Lint, Unit Tests, and Coverage Report..."
./gradlew detekt
./gradlew testDebugUnitTest
./gradlew lint
./gradlew koverHtmlReport
./gradlew :core:domain:koverVerify
echo "✅ All checks passed successfully."
