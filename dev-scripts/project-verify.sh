#!/bin/bash

echo "🔍 Running Lint, Unit Tests, and Coverage Report..."
./gradlew detekt lint testDebugUnitTest koverHtmlReport

if [ $? -eq 0 ]; then
    echo "✅ All checks passed successfully."
else
    echo "❌ Checks failed. Please review the output above."
    exit 1
fi
