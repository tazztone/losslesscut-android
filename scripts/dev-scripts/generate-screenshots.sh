#!/bin/bash
# ==============================================================================
# LosslessCut Automated Screenshot Generation Pipeline
# ==============================================================================
# Golden Device Profile: Google Pixel 7/8 (or 1080x2400 @ 420dpi), API 34+
# This script standardizes System UI, pushes test media, executes instrumented
# screenshot tests, pulls captured framebuffers, converts them to high-efficiency
# WebP assets, and updates docs/images.
# ==============================================================================

set -euo pipefail

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/lossless-cut-gradle}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

DOCS_IMAGES_DIR="$PROJECT_ROOT/docs/images"
ASSET_FILE="$PROJECT_ROOT/assets/example_files/SpatCut_20260113230858.mp4"
DEVICE_STAGING_PATH="/data/local/tmp/SpatCut_20260113230858.mp4"
DEVICE_EXPORT_DIR="/sdcard/Download/losslesscut_screenshots"
LOCAL_TMP_DIR="/tmp/lossless-cut-screenshots"

EXPECTED_SCREENSHOTS=(
    "screenshot_dashboard"
    "screenshot_settings"
    "screenshot_landscape"
    "screenshot_portrait"
    "screenshot_smart_cut_silence"
    "screenshot_smart_cut_visual"
)

# Parse CLI arguments
DRY_RUN=false
VERIFY_ONLY=false
CLEAN_ONLY=false

for arg in "$@"; do
    case $arg in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --verify-only)
            VERIFY_ONLY=true
            shift
            ;;
        --clean)
            CLEAN_ONLY=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [--dry-run | --verify-only | --clean]"
            echo "  --dry-run      Validate environment, tools, and prerequisites without running tests"
            echo "  --verify-only  Verify existing WebP screenshots in docs/images/"
            echo "  --clean        Clean temporary and device screenshot artifacts"
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg"
            echo "Run with --help for usage."
            exit 1
            ;;
    esac
done

# Check verification mode
if [ "$VERIFY_ONLY" = true ]; then
    echo "🔍 Verifying existing screenshots in $DOCS_IMAGES_DIR..."
    MISSING_COUNT=0
    for name in "${EXPECTED_SCREENSHOTS[@]}"; do
        target="$DOCS_IMAGES_DIR/${name}.webp"
        if [ -f "$target" ]; then
            size=$(stat -c %s "$target" 2>/dev/null || stat -f %z "$target")
            echo "  ✅ $name.webp ($size bytes)"
        else
            echo "  ❌ MISSING: $name.webp"
            MISSING_COUNT=$((MISSING_COUNT + 1))
        fi
    done

    if [ "$MISSING_COUNT" -eq 0 ]; then
        echo "🎉 All $((${#EXPECTED_SCREENSHOTS[@]})) screenshot assets verified successfully!"
        exit 0
    else
        echo "❌ Verification failed: $MISSING_COUNT missing screenshot(s)."
        exit 1
    fi
fi

# Detect ADB binary
ADB=$(command -v adb || echo "/home/tazztone/Android/Sdk/platform-tools/adb")
if [ ! -x "$ADB" ]; then
    echo "❌ Error: ADB not found or executable. Please install adb or set PATH."
    exit 1
fi

# Detect Image conversion tools
CONVERTER=""
if command -v ffmpeg >/dev/null 2>&1; then
    CONVERTER="ffmpeg"
elif command -v convert >/dev/null 2>&1; then
    CONVERTER="convert"
else
    echo "❌ Error: Neither ffmpeg nor imagemagick (convert) found. Please install ffmpeg or imagemagick."
    exit 1
fi

echo "📷 Using image converter: $CONVERTER"

# Ensure local temporary and docs directories exist
mkdir -p "$LOCAL_TMP_DIR"
mkdir -p "$DOCS_IMAGES_DIR"

if [ "$CLEAN_ONLY" = true ]; then
    echo "🧹 Cleaning screenshot artifacts..."
    rm -rf "$LOCAL_TMP_DIR"
    $ADB shell rm -rf "$DEVICE_EXPORT_DIR" 2>/dev/null || true
    echo "✅ Clean completed."
    exit 0
fi

# Validate source demo asset
if [ ! -f "$ASSET_FILE" ]; then
    echo "❌ Error: Demo asset missing at $ASSET_FILE"
    exit 1
fi
LOCAL_ASSET_SIZE=$(stat -c %s "$ASSET_FILE" 2>/dev/null || stat -f %z "$ASSET_FILE")

# Check connected device
DEVICE_STATE=$($ADB get-state 2>/dev/null || echo "offline")
if [ "$DEVICE_STATE" != "device" ]; then
    echo "❌ Error: No authorized Android device/emulator connected via ADB (State: $DEVICE_STATE)."
    exit 1
fi

DEVICE_MODEL=$($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r')
DEVICE_API=$($ADB shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
echo "📱 Connected device: $DEVICE_MODEL (API $DEVICE_API)"

if [ "$DRY_RUN" = true ]; then
    echo "✅ [DRY RUN] All prerequisites validated successfully."
    echo "   - ADB: $ADB"
    echo "   - Converter: $CONVERTER"
    echo "   - Source Asset: $ASSET_FILE ($LOCAL_ASSET_SIZE bytes)"
    echo "   - Device: $DEVICE_MODEL (API $DEVICE_API)"
    exit 0
fi

# Save original screen awake and timeout settings to restore afterwards
ORIG_STAY_ON=$($ADB shell settings get global stay_on_while_plugged_in | tr -d '\r')
ORIG_TIMEOUT=$($ADB shell settings get system screen_off_timeout | tr -d '\r')

# Cleanup handler for System UI Demo Mode and screen settings
cleanup() {
    echo "🧹 Restoring System UI and screen settings..."
    $ADB shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
    $ADB shell settings put global sysui_demo_allowed 0 >/dev/null 2>&1 || true
    if [ -n "$ORIG_STAY_ON" ] && [ "$ORIG_STAY_ON" != "null" ]; then
        $ADB shell settings put global stay_on_while_plugged_in "$ORIG_STAY_ON" >/dev/null 2>&1 || true
    else
        $ADB shell settings put global stay_on_while_plugged_in 0 >/dev/null 2>&1 || true
    fi
    if [ -n "$ORIG_TIMEOUT" ] && [ "$ORIG_TIMEOUT" != "null" ]; then
        $ADB shell settings put system screen_off_timeout "$ORIG_TIMEOUT" >/dev/null 2>&1 || true
    fi
    $ADB shell rm -rf "$DEVICE_EXPORT_DIR" 2>/dev/null || true
}
trap cleanup EXIT

echo "⚡ Preventing screen timeout during test run..."
$ADB shell settings put global stay_on_while_plugged_in 7 >/dev/null 2>&1 || true
$ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
$ADB shell wm dismiss-keyguard >/dev/null 2>&1 || true

echo "✨ Enabling System UI Demo Mode (clean status bar, 12:00, 100% battery)..."
$ADB shell settings put global sysui_demo_allowed 1
$ADB shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null 2>&1
$ADB shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null 2>&1
$ADB shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null 2>&1
$ADB shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e datatype lte -e level 4 >/dev/null 2>&1
$ADB shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null 2>&1

# Check device staging video
echo "📦 Checking demo asset staging on device..."
DEVICE_FILE_SIZE=$($ADB shell "stat -c %s $DEVICE_STAGING_PATH 2>/dev/null || echo 0" | tr -d '\r')
if [ "$DEVICE_FILE_SIZE" != "$LOCAL_ASSET_SIZE" ]; then
    echo "🚀 Pushing demo video ($LOCAL_ASSET_SIZE bytes) to $DEVICE_STAGING_PATH..."
    $ADB push "$ASSET_FILE" "$DEVICE_STAGING_PATH"
    $ADB shell chmod 666 "$DEVICE_STAGING_PATH"
else
    echo "✅ Demo video already cached on device ($DEVICE_FILE_SIZE bytes)."
fi

# Clean previous temporary screenshots
$ADB shell rm -rf "$DEVICE_EXPORT_DIR" 2>/dev/null || true
rm -rf "${LOCAL_TMP_DIR:?}"/*

# Clean previous device screenshots
$ADB shell rm -rf /sdcard/Download/losslesscut_screenshots 2>/dev/null || true
$ADB shell mkdir -p /sdcard/Download/losslesscut_screenshots 2>/dev/null || true

# Execute screenshot tests
echo "🧪 Running ScreenshotCaptureTest test suite..."
cd "$PROJECT_ROOT"
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.tazztone.losslesscut.ui.ScreenshotCaptureTest

# Pull screenshots from device
echo "📥 Pulling screenshots from device..."
$ADB pull /sdcard/Download/losslesscut_screenshots/. "$LOCAL_TMP_DIR/" || true

# Validate and process screenshots
echo "🎨 Converting and optimizing screenshots to WebP..."
MISSING_SCREENSHOTS=()

for name in "${EXPECTED_SCREENSHOTS[@]}"; do
    src_png="$LOCAL_TMP_DIR/${name}.png"
    dest_webp="$DOCS_IMAGES_DIR/${name}.webp"

    if [ ! -f "$src_png" ]; then
        echo "  ❌ Error: Missing generated screenshot: $src_png"
        MISSING_SCREENSHOTS+=("$name")
        continue
    fi

    if [ "$CONVERTER" = "ffmpeg" ]; then
        ffmpeg -y -i "$src_png" \
            -vf "scale='min(1280,iw)':'min(1280,ih)':force_original_aspect_ratio=decrease" \
            -c:v libwebp -quality 90 "$dest_webp" >/dev/null 2>&1
    else
        convert "$src_png" -resize "1280x1280>" -quality 90 "$dest_webp"
    fi

    webp_size=$(stat -c %s "$dest_webp" 2>/dev/null || stat -f %z "$dest_webp")
    echo "  ✅ Generated $dest_webp ($webp_size bytes)"
done

if [ ${#MISSING_SCREENSHOTS[@]} -gt 0 ]; then
    echo "❌ Screenshot pipeline completed with ${#MISSING_SCREENSHOTS[@]} error(s)."
    exit 1
fi

echo "🎉 Screenshot generation pipeline finished successfully!"
exit 0
