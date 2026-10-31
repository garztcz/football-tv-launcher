#!/bin/bash
# Manual Android APK build for the TV Live Feed add-on, same Gradle-free
# pipeline as the launcher build (aapt2 -> javac -> d8 -> zipalign ->
# apksigner). The add-on uses framework APIs only (JobScheduler,
# HttpURLConnection, org.json), so there are no library dependencies.
set -euo pipefail

SDK=~/Android/Sdk
BT=$SDK/build-tools/34.0.0
AJAR=$SDK/platforms/android-34/android.jar
REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
APP=$REPO_ROOT/live-feed/app
W=$REPO_ROOT/build/manual-livefeed
rm -rf "$W"; mkdir -p "$W"/{res,gen,classes,dex}

echo "== aapt2 link =="
"$BT/aapt2" compile --dir "$APP/src/main/res" -o "$W/res/app.zip"
sed 's|<manifest |<manifest package="com.gathin.tvhome.livefeed" |' \
  "$APP/src/main/AndroidManifest.xml" > "$W/AndroidManifest.xml"
"$BT/aapt2" link -o "$W/base.apk" -I "$AJAR" \
  --manifest "$W/AndroidManifest.xml" \
  --java "$W/gen" \
  --auto-add-overlay \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 1 --version-name 1.0 \
  "$W/res/app.zip"

echo "== javac =="
find "$APP/src/main/java" "$W/gen" -name "*.java" > "$W/sources.txt"
~/tools/jdk-17/bin/javac -proc:none -encoding UTF-8 \
  -cp "$AJAR" -d "$W/classes" @"$W/sources.txt" 2>&1 | head -40

echo "== d8 =="
(cd "$W/classes" && ~/tools/jdk-17/bin/jar cf "$W/classes.jar" .)
"$BT/d8" --min-api 26 --lib "$AJAR" --output "$W/dex" "$W/classes.jar" 2>&1 | head -20

echo "== package + align + sign =="
cp "$W/base.apk" "$W/app.apk"
(cd "$W/dex" && zip -q "$W/app.apk" classes.dex)
"$BT/zipalign" -f 4 "$W/app.apk" "$W/app-aligned.apk"
KS=~/.android/debug.keystore
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$W/live-feed-v1.apk" "$W/app-aligned.apk"
"$BT/apksigner" verify --print-certs "$W/live-feed-v1.apk" | head -3
ls -la "$W/live-feed-v1.apk"
"$BT/aapt2" dump badging "$W/live-feed-v1.apk" | head -8
echo "MANUAL BUILD OK: $W/live-feed-v1.apk"
