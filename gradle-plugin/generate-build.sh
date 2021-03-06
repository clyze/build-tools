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

function runCustomBuildActions() {
    time ${GRADLE} --stacktrace --info clean sourcesJar jcpluginZip $1
}

# Build without minifyEnabled.
function runCustomBuildActionsNoMinify() {
    local BUILD2=build-minifyDisabled.gradle
    grep -vF minifyEnabled build.gradle | grep -vF shrinkResources > ${BUILD2}
    # Temporarily swap build.gradle with custom one. We cannot use a build
    # script with a different name, as that may clash with existing settings files.
    mv build.gradle build.gradle.backup
    # If the user interrupts the build, restore build.gradle.
    finallyRestoreTrap
    mv ${BUILD2} build.gradle
    runCustomBuildActions codeApk
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
    for MODULE in "${MODULES[@]}"; do
        echo "Building module ${MODULE} without minification..."
        pushd ${MODULE}
        runCustomBuildActionsNoMinify
        popd
    done
}

if [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
    echo "Usage: generate-build.sh ACTION [MODULE...]"
    echo
    echo "Generates the appropriate files to be posted to the Web UI."
    echo "This script should be run in the application module directory."
    echo
    echo "Options:"
    echo "  ACTION   'postBuild' or 'dry-run'"
    echo "  MODULE   the project modules to package/post (default is current directory)"
    echo
    exit
fi

# Autodetect gradlew wrapper or system Gradle.
if [ "${GRADLE}" == "" ]; then
    if [ -f "./gradlew" ]; then
        GRADLE=$(realpath "./gradlew")
    elif [ -f "../gradlew" ]; then
        GRADLE=$(realpath "../gradlew")
    elif command -v "gradle"; then
        GRADLE="gradle"
        echo "Found '${GRADLE}' command in path (set environment variable GRADLE to override it)."
    else
        echo "Please set GRADLE to an appropriate Gradle binary (or wrapper)."
        exit
    fi
fi

defaultTrap

# Read parameters and set defaults.
ACTION="$1"
if [ "$2" == "" ]; then
    MODULES=( "." )
else
    shift
    MODULES=( "$@" )
fi
echo "Modules: ${MODULES}"

runBuildActions

if [ "${ACTION}" == "postBuild" ]; then
    for MODULE in "${MODULES[@]}"; do
        echo "Posting build in module: ${MODULE}"
        pushd ${MODULE}
        time ${GRADLE} postBuild
        popd
    done
elif [ "${ACTION}" == "dry-run" ]; then
    echo "Dry run requested: will not post build to the server."
fi
