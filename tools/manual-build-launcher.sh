#!/bin/bash
# Manual Android APK build without Gradle (sandbox: Java IPC is blocked, so
# Gradle workers cannot run; every tool here is a single CLI process).
# Pipeline: aapt2 compile/link -> javac -> d8 -> zipalign -> apksigner.
set -euo pipefail

SDK=~/Android/Sdk
BT=$SDK/build-tools/34.0.0
AJAR=$SDK/platforms/android-34/android.jar
REPO=${LOCAL_MAVEN:-$HOME/local-maven}
REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
APP=$REPO_ROOT/launcher/app
W=$REPO_ROOT/build/manual-launcher
rm -rf "$W"; mkdir -p "$W"/{aars,res,gen,classes,dex}

# group:artifact:version list (runtime closure of the two app deps)
DEPS=(
  androidx.annotation:annotation:1.1.0
  androidx.annotation:annotation-experimental:1.1.0
  androidx.arch.core:core-common:2.1.0
  androidx.arch.core:core-runtime:2.1.0
  androidx.collection:collection:1.0.0
  androidx.concurrent:concurrent-futures:1.0.0
  androidx.core:core:1.7.0
  androidx.core:core-ktx:1.5.0
  androidx.customview:customview:1.0.0
  androidx.customview:customview-poolingcontainer:1.0.0
  androidx.dynamicanimation:dynamicanimation:1.0.0
  androidx.lifecycle:lifecycle-common:2.3.1
  androidx.lifecycle:lifecycle-runtime:2.3.1
  androidx.recyclerview:recyclerview:1.3.2
  androidx.versionedparcelable:versionedparcelable:1.1.1
  org.jetbrains.kotlin:kotlin-stdlib:1.4.31
)

CP="$AJAR"
EXTRA_PKGS=""
declare -a LINK_RES=()
# app resources first (primary)
"$BT/aapt2" compile --dir "$APP/src/main/res" -o "$W/res/app.zip"
LINK_RES+=("$W/res/app.zip")

for dep in "${DEPS[@]}"; do
  IFS=: read -r g a v <<<"$dep"
  dir="$REPO/${g//.//}/$a/$v"
  if [ -f "$dir/$a-$v.aar" ]; then
    ex="$W/aars/$a"; mkdir -p "$ex"
    (cd "$ex" && unzip -oq "$dir/$a-$v.aar")
    CP="$CP:$ex/classes.jar"
    pkg=$(grep -o 'package="[^"]*"' "$ex/AndroidManifest.xml" | head -1 | cut -d'"' -f2)
    EXTRA_PKGS="${EXTRA_PKGS:+$EXTRA_PKGS:}$pkg"
    if [ -d "$ex/res" ] && [ -n "$(ls -A "$ex/res" 2>/dev/null)" ]; then
      "$BT/aapt2" compile --dir "$ex/res" -o "$W/res/$a.zip"
      LINK_RES+=("$W/res/$a.zip")
    fi
    # library assets, if any
    if [ -d "$ex/assets" ] && [ -n "$(ls -A "$ex/assets" 2>/dev/null)" ]; then
      mkdir -p "$W/libassets/$a"; cp -r "$ex/assets/." "$W/libassets/$a/"
    fi
  elif [ -f "$dir/$a-$v.jar" ]; then
    CP="$CP:$dir/$a-$v.jar"
  else
    echo "MISSING artifact file for $dep in $dir"; exit 1
  fi
done

echo "== aapt2 link =="
# AGP injects the namespace as the manifest package; do the same on a copy.
sed 's|<manifest |<manifest package="com.gathin.tvhome" |' \
  "$APP/src/main/AndroidManifest.xml" > "$W/AndroidManifest.xml"
"$BT/aapt2" link -o "$W/base.apk" -I "$AJAR" \
  --manifest "$W/AndroidManifest.xml" \
  --java "$W/gen" \
  --extra-packages "$EXTRA_PKGS" \
  --auto-add-overlay \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 46 --version-name 3.2-nothing5.7 \
  "${LINK_RES[@]}"

echo "== javac =="
find "$APP/src/main/java" "$W/gen" -name "*.java" > "$W/sources.txt"
~/tools/jdk-17/bin/javac -proc:none -encoding UTF-8 \
  -cp "$CP" -d "$W/classes" @"$W/sources.txt" 2>&1 | head -40

echo "== d8 =="
LIBJARS=()
for dep in "${DEPS[@]}"; do
  IFS=: read -r g a v <<<"$dep"
  dir="$REPO/${g//.//}/$a/$v"
  if [ -f "$W/aars/$a/classes.jar" ]; then LIBJARS+=("$W/aars/$a/classes.jar");
  elif [ -f "$dir/$a-$v.jar" ]; then LIBJARS+=("$dir/$a-$v.jar"); fi
done
(cd "$W/classes" && ~/tools/jdk-17/bin/jar cf "$W/classes.jar" .)
"$BT/d8" --min-api 26 --lib "$AJAR" --output "$W/dex" "$W/classes.jar" "${LIBJARS[@]}" 2>&1 | head -20

echo "== package + align + sign =="
cp "$W/base.apk" "$W/app.apk"
(cd "$W/dex" && zip -q "$W/app.apk" classes.dex)
if [ -d "$APP/src/main/assets" ]; then (cd "$APP/src/main" && zip -q -r "$W/app.apk" assets); fi
if [ -d "$W/libassets" ]; then (cd "$W/libassets" && for d in */; do (cd "$d" && zip -q -r "$W/app.apk" . -x ./); done); fi
"$BT/zipalign" -f 4 "$W/app.apk" "$W/app-aligned.apk"
KS=~/.android/debug.keystore
if [ ! -f "$KS" ]; then
  mkdir -p ~/.android
  ~/tools/jdk-17/bin/keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$W/football-tv-v1.apk" "$W/app-aligned.apk"
"$BT/apksigner" verify --print-certs "$W/football-tv-v1.apk" | head -3
ls -la "$W/football-tv-v1.apk"
"$BT/aapt2" dump badging "$W/football-tv-v1.apk" | head -6
echo "MANUAL BUILD OK: $W/football-tv-v1.apk"
