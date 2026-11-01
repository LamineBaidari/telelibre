#!/bin/bash
# Publishes a new version of Télé Libre as a GitHub release; TVs pick it up within a day.
# Usage: ./release.sh 1.2 "What changed"
# Needs: gh logged in, the signing key configured in ~/.gradle/gradle.properties.
set -euo pipefail
cd "$(dirname "$0")"

VERSION=${1:?usage: ./release.sh <version> "<notes>"}
NOTES=${2:-"Télé Libre $VERSION"}
GRADLE=app/build.gradle.kts
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}

if [ -n "$(git status --porcelain)" ]; then
  echo "Commit or stash your changes first." >&2
  exit 1
fi

# versionCode goes up by one each release; versionName is what the TV compares with the release tag.
CODE=$(sed -nE 's/ *versionCode = ([0-9]+)/\1/p' "$GRADLE")
sed -i '' -E "s/versionCode = [0-9]+/versionCode = $((CODE + 1))/; s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" "$GRADLE"

./gradlew -q test assembleRelease
APK="telelibre-$VERSION.apk"
cp app/build/outputs/apk/release/app-release.apk "/tmp/$APK"

git commit -qam "Release $VERSION"
git tag "v$VERSION"
git push -q origin HEAD "v$VERSION"
gh release create "v$VERSION" "/tmp/$APK" --title "Télé Libre $VERSION" --notes "$NOTES"
echo "Published v$VERSION ($APK)."
