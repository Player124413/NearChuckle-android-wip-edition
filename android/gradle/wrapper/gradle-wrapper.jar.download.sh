#!/bin/sh
# Fallback: download gradle wrapper if missing (CI will do)
set -e
WRAPPER_JAR="$(dirname "$0")/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ] || [ ! -s "$WRAPPER_JAR" ]; then
  echo "Downloading gradle-wrapper.jar..."
  URL="https://github.com/gradle/gradle/raw/master/gradle/wrapper/gradle-wrapper.jar"
  # try services.gradle.org direct
  if command -v curl >/dev/null 2>&1; then
    curl -L -o "$WRAPPER_JAR" "https://services.gradle.org/distributions/gradle-8.7-bin.zip" || true
    # Actually need jar, use github
    curl -L -o "$WRAPPER_JAR" "https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar" || true
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$WRAPPER_JAR" "https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar" || true
  fi
  ls -lh "$WRAPPER_JAR" || echo "download attempted"
fi
