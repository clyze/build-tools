#!/usr/bin/env bash

trap "exit" INT

function usage() {
    echo "Analyze bundle:"
    echo "  clue-bundle.sh analyze"
    echo "Pack bundle:"
    echo "  clue-bundle.sh pack BUNDLE_DIR"
    echo
    echo "Generates the appropriate files to be posted to the Web UI."
    echo "This script should be run in the application module directory."
    echo
    echo "  BUNDLE_DIR      example: bundle-app"
    echo
    exit
}

# Run actions that are compatible with default build (assumed
# "minifyEnabled = true").
function runDefaultBuildActions() {
    time ${GRADLE} --info clean configurations
}

# Build without minifyEnabled.
function runCustomBuildActions() {
    BUILD2=build-minifyDisabled.gradle
    grep -vF minifyEnabled build.gradle | grep -vF shrinkResources > ${BUILD2}
    # Temporarily swap build.gradle with custom one. We cannot use a build
    # script with a different name, as that may clash with existing settings files.
    mv build.gradle build.gradle.backup
    mv ${BUILD2} build.gradle
    time ${GRADLE} --info clean sourcesJar jcpluginZip codeApk
    mv build.gradle.backup build.gradle
}

function runBuildActions() {
    runDefaultBuildActions
    runCustomBuildActions
}

function createBundleArchive() {
    mkdir -p ${BUNDLE_DIR}
    rm -f ${BUNDLE_DIR}/*.apk
    rm -f ${BUNDLE_DIR}/*.zip
    rm -f ${BUNDLE_DIR}/*.jar

    cp ${LOCAL_BUNDLE_DIR}/metadata.zip ${BUNDLE_DIR}
    cp ${LOCAL_BUNDLE_DIR}/configurations.zip ${BUNDLE_DIR}
    cp ${LOCAL_BUNDLE_DIR}/sources.jar ${BUNDLE_DIR}
    cp ${LOCAL_BUNDLE_DIR}/*.apk ${BUNDLE_DIR}

    BUNDLE_FILE=bundle.tar.gz
    rm -f ${BUNDLE_FILE}
    tar -czf ${BUNDLE_FILE} ${BUNDLE_DIR}
    echo "Output written to file: ${BUNDLE_FILE}"
}

if [ "$1" != "analyze" ] && [ "$1" != "pack" ]; then
    usage
elif [ "$1" == "analyze" ] && [ "$2" != "" ]; then
    usage
elif [ "$1" == "pack" ] && [ "$2" == "" ]; then
    usage
fi

# Autodetect gradlew wrapper.
if [ "${GRADLE}" == "" ]; then
    if [ -f "./gradlew" ]; then
        GRADLE="./gradlew"
    elif [ -f "../gradlew" ]; then
        GRADLE="../gradlew"
    else
        echo "Please set GRADLE to an appropriate Gradle binary (or wrapper)."
        exit
    fi
fi

LOCAL_BUNDLE_DIR=".clue-bundle"

if [ "$1" == "pack" ]; then
    BUNDLE_DIR="$2"
    runBuildActions
    createBundleArchive
elif [ "$1" == "analyze" ]; then
    runBuildActions
    ${GRADLE} analyze
fi
