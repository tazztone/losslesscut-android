#!/bin/bash

echo "🔍 Running Lint and Unit Tests..."
./gradlew detekt lint testDebugUnitTest

if [ $? -eq 0 ]; then
    echo "✅ All checks passed successfully."
else
    echo "❌ Checks failed. Please review the output above."
    exit 1
fi
