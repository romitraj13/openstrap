#!/usr/bin/env bash
# Build the shared Kotlin XCFramework, generate the Xcode project and open it.
# For Romit's Mac. Run from anywhere: ./iosApp/build.sh
set -euo pipefail

cd "$(dirname "$0")/.."

fail() {
    echo "error: $*" >&2
    exit 1
}

if ! command -v java >/dev/null 2>&1; then
    fail "Java 21 is required but 'java' was not found. Install it with: brew install --cask temurin@21"
fi
JAVA_MAJOR="$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1)"
if [ "$JAVA_MAJOR" != "21" ]; then
    fail "Java 21 is required, found Java ${JAVA_MAJOR:-unknown}. Install it with: brew install --cask temurin@21"
fi

if ! command -v xcodegen >/dev/null 2>&1; then
    fail "xcodegen is required but was not found. Install it with: brew install xcodegen"
fi

./gradlew :shared:assembleOpenStrapSharedDebugXCFramework

(cd iosApp && xcodegen generate)

open iosApp/OpenStrap.xcodeproj
