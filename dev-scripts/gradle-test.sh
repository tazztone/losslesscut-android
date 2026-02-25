#!/bin/bash

# Utility script to run specific Gradle unit tests more easily.
# Usage: ./dev-scripts/gradle-test.sh <module> <test_pattern>
# Example: ./dev-scripts/gradle-test.sh :engine "*.TrackInspectorTest"

MODULE=$1
PATTERN=$2

if [ -z "$MODULE" ] || [ -z "$PATTERN" ]; then
    echo "Usage: $0 <module> <test_pattern>"
    echo "Example: $0 :engine \"*.TrackInspectorTest\""
    exit 1
fi

# Determine the correct test task (Domain is pure JVM, others are Android Libraries)
if [[ "$MODULE" == ":core:domain" ]]; then
    TASK="test"
else
    TASK="testDebugUnitTest"
fi

echo "🚀 Running tests for $MODULE with pattern: $PATTERN"
./gradlew "$MODULE:$TASK" --tests "$PATTERN"

if [ $? -eq 0 ]; then
    echo "✅ Tests passed!"
else
    echo "❌ Tests failed."
    exit 1
fi
