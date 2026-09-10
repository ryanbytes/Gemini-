#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$SDK" ]] || { echo "Set ANDROID_SDK_ROOT" >&2; exit 2; }
ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-37/android.jar}"
BT="${BUILD_TOOLS_DIR:-$SDK/build-tools/36.0.0}"
for f in "$ANDROID_JAR" "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner"; do [[ -e "$f" ]] || { echo "Missing: $f" >&2; exit 2; }; done
command -v javac >/dev/null || { echo "Missing javac" >&2; exit 2; }
command -v keytool >/dev/null || { echo "Missing keytool" >&2; exit 2; }
command -v jar >/dev/null || { echo "Missing jar" >&2; exit 2; }
command -v zip >/dev/null || { echo "Missing zip" >&2; exit 2; }
OUT="$ROOT/out"; DIST="$ROOT/dist"; rm -rf "$OUT" "$DIST"; mkdir -p "$OUT/classes" "$OUT/dex" "$DIST"
find "$ROOT/src" -name '*.java' -print0 | xargs -0 javac -source 8 -target 8 -Xlint:-options -classpath "$ANDROID_JAR" -d "$OUT/classes"
jar --create --file "$OUT/classes.jar" -C "$OUT/classes" .
"$BT/d8" --lib "$ANDROID_JAR" --min-api 23 --output "$OUT/dex" "$OUT/classes.jar"
"$BT/aapt2" link -o "$OUT/unsigned.apk" --manifest "$ROOT/AndroidManifest.xml" -I "$ANDROID_JAR" --min-sdk-version 23 --target-sdk-version 37
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KEYSTORE="$ROOT/debug.keystore"
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android -alias gemini -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Gemini MDIU,O=ryanbytes,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias gemini --ks-pass pass:android --key-pass pass:android --out "$DIST/Gemini-MDIU.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --verbose "$DIST/Gemini-MDIU.apk"
sha256sum "$DIST/Gemini-MDIU.apk" | tee "$DIST/Gemini-MDIU.apk.sha256"
echo "Built $DIST/Gemini-MDIU.apk"
