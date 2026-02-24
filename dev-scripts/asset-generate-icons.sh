#!/bin/bash
# Script to generate Android app icons from docs/logo.png

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
LOGO_PATH="$PROJECT_ROOT/docs/logo.png"
RES_DIR="$PROJECT_ROOT/app/src/main/res"

if [ ! -f "$LOGO_PATH" ]; then
    echo "Error: $LOGO_PATH not found."
    exit 1
fi

echo "Compiling IconGenerator..."
javac "$SCRIPT_DIR/IconGenerator.java"

echo "Generating icons..."
java -cp "$SCRIPT_DIR" IconGenerator "$LOGO_PATH" "$RES_DIR"

echo "Cleaning up..."
rm "$SCRIPT_DIR/IconGenerator.class"

echo "Done!"
