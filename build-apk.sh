#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
wardrobe_javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
command -v "$wardrobe_javac" >/dev/null || { echo 'Install a full JDK 17 or 21 and set JAVA_HOME.' >&2; exit 1; }
wardrobe_version="$("$wardrobe_javac" -version 2>&1)"
[[ "$wardrobe_version" =~ javac\ (17|21)\. ]] || { echo 'Use JDK 17 or 21.' >&2; exit 1; }
wardrobe_jar=gradle/wrapper/gradle-wrapper.jar
[[ -f "$wardrobe_jar" ]] || { echo 'Missing bundled Gradle wrapper.' >&2; exit 1; }
if command -v sha256sum >/dev/null; then wardrobe_hash="$(sha256sum "$wardrobe_jar" | cut -d ' ' -f 1)"; else wardrobe_hash="$(shasum -a 256 "$wardrobe_jar" | cut -d ' ' -f 1)"; fi
[[ "$wardrobe_hash" == '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046' ]] || { echo 'Gradle wrapper checksum mismatch.' >&2; exit 1; }
rg_wrapper_props=gradle/wrapper/gradle-wrapper.properties
grep -qx 'distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6' "$rg_wrapper_props" || { echo 'Gradle distribution checksum mismatch.' >&2; exit 1; }
grep -Fqx 'distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip' "$rg_wrapper_props" || { echo 'Unexpected Gradle distribution.' >&2; exit 1; }
if (( $# == 0 )); then set -- testDebugUnitTest lintDebug assembleDebug; fi
bash ./gradlew --no-daemon "$@"
for wardrobe_task in "$@"; do
 case "$wardrobe_task" in *assembleDebug) echo 'APK: app/build/outputs/apk/debug/app-debug.apk';; *assembleRelease) echo 'APK: app/build/outputs/apk/release/app-release-unsigned.apk';; esac
done
