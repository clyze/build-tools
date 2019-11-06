#!/usr/bin/env bash

# Default "trap" behavior: exit on Ctrl-C.
function defaultTrap() {
    trap "exit" INT
}

# Special "trap" behavior: restore build file and exit on Ctrl-C.
function finallyRestoreTrap() {
    trap "restoreBuildGradle; exit" INT
}

# Run actions that are compatible with default build (assumed
# "minifyEnabled = true").
function runDefaultBuildActions() {
    time ${GRADLE} --stacktrace --info clean configurations
}

# Build without minifyEnabled.
function runCustomBuildActions() {
    BUILD2=build-minifyDisabled.gradle
    grep -vF minifyEnabled build.gradle | grep -vF shrinkResources > ${BUILD2}
    # Temporarily swap build.gradle with custom one. We cannot use a build
    # script with a different name, as that may clash with existing settings files.
    mv build.gradle build.gradle.backup
    # If the user interrupts the build, restore build.gradle.
    finallyRestoreTrap
    mv ${BUILD2} build.gradle
    time ${GRADLE} --stacktrace --info clean sourcesJar jcpluginZip codeApk
    restoreBuildGradle
    # Revert to default trap behavior.
    defaultTrap
}

function restoreBuildGradle() {
    mv build.gradle.backup build.gradle
}

function runBuildActions() {
    runDefaultBuildActions
    runCustomBuildActions
}

if [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
    echo "Usage: clue-bundle-build.sh [postBundle]"
    echo
    echo "Generates the appropriate files to be posted to the Web UI."
    echo "This script should be run in the application module directory."
    echo
    echo "Options:"
    echo "  postBundle    also post bundle to server"
    echo
    exit
fi

# Autodetect gradlew wrapper or system Gradle.
if [ "${GRADLE}" == "" ]; then
    if [ -f "./gradlew" ]; then
        GRADLE="./gradlew"
    elif [ -f "../gradlew" ]; then
        GRADLE="../gradlew"
    elif command -v "gradle"; then
        GRADLE="gradle"
        echo "Found '${GRADLE}' command in path."
    else
        echo "Please set GRADLE to an appropriate Gradle binary (or wrapper)."
        exit
    fi
fi


defaultTrap
runBuildActions

if [ "$1" == "postBundle" ]; then
    time ${GRADLE} postBundle
fi
