#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/lib/android-sdk}}"
ANDROID_JAR="$SDK_ROOT/platforms/android-30/android.jar"
BUILD_TOOLS="$SDK_ROOT/build-tools/34.0.0"
BUILD_DIR="$PROJECT_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"
SIGNING_DIR="$PROJECT_DIR/.signing"

if [[ ! -f "$ANDROID_JAR" ]]; then
    echo "Android 30 platform not found: $ANDROID_JAR" >&2
    exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/stub-classes" "$BUILD_DIR/classes" "$BUILD_DIR/dex" \
    "$DIST_DIR" "$SIGNING_DIR"

find "$PROJECT_DIR/stubs" -name '*.java' -print0 \
    | xargs -0 javac -source 8 -target 8 -d "$BUILD_DIR/stub-classes"
jar cf "$BUILD_DIR/xposed-api-stubs.jar" -C "$BUILD_DIR/stub-classes" .

find "$PROJECT_DIR/src" -name '*.java' -print0 \
    | xargs -0 javac -source 8 -target 8 \
        -cp "$ANDROID_JAR:$BUILD_DIR/xposed-api-stubs.jar" \
        -d "$BUILD_DIR/classes"
jar cf "$BUILD_DIR/module-classes.jar" -C "$BUILD_DIR/classes" .

"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 27 \
    --output "$BUILD_DIR/dex" "$BUILD_DIR/module-classes.jar"

"$BUILD_TOOLS/aapt" package -f \
    -M "$PROJECT_DIR/AndroidManifest.xml" \
    -A "$PROJECT_DIR/assets" \
    -I "$ANDROID_JAR" \
    -F "$BUILD_DIR/unsigned.apk"

(cd "$BUILD_DIR/dex" && zip -q "$BUILD_DIR/unsigned.apk" classes.dex)

"$BUILD_TOOLS/zipalign" -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"

KEYSTORE="$SIGNING_DIR/debug.keystore"
if [[ ! -f "$KEYSTORE" ]]; then
    keytool -genkeypair -noprompt \
        -keystore "$KEYSTORE" \
        -storepass android -keypass android -alias androiddebugkey \
        -dname "CN=Pico IME Unlock,O=Local,C=US" \
        -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi

OUTPUT="$DIST_DIR/pico-ime-unlock-v1.0.3.apk"
"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$OUTPUT" "$BUILD_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$OUTPUT"
echo "$OUTPUT"
