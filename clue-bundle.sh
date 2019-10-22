#!/usr/bin/env bash

if [ "$1" == "" ] || [ "$2" == "" ] || [ "$3" == "" ]
then
    echo "Usage: clue-bundle.sh BUNDLE_DIR ASSEMBLE_TASK APK"
    echo
    echo "Generates the appropriate files to be posted to the Web UI."
    echo "This script should be run in the application module directory."
    echo
    echo "  BUNDLE_DIR      example: bundle-app"
    echo "  ASSEMBLE_TASK   example: assembleDebug"
    echo "  APK             example: build/outputs/apk/app-debug.apk"
    exit
fi

BUNDLE_DIR="$1"
ASSEMBLE_TASK="$2"
APK="$3"

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

mkdir -p ${BUNDLE_DIR}
rm -f ${BUNDLE_DIR}/*.apk
rm -f ${BUNDLE_DIR}/*.zip
rm -f ${BUNDLE_DIR}/*.jar

# Build with minifyEnabled = true.
time ${GRADLE} --info clean ${ASSEMBLE_TASK} sourcesJar jcpluginZip configurations

cp build/scavenge/metadata.zip ${BUNDLE_DIR}
cp build/scavenge/configurations.zip ${BUNDLE_DIR}
cp build/libs/sources.jar ${BUNDLE_DIR}

# Build with minifyEnabled = false.
BUILD2=build-minifyDisabled.gradle
grep -vF minifyEnabled build.gradle | grep -vF shrinkResources > ${BUILD2}
# Temporarily swap build.gradle with custom one. We cannot use a build
# script with a different name, as that may clash with existing settings files.
mv build.gradle build.gradle.backup
mv ${BUILD2} build.gradle
time ${GRADLE} clean codeApk
mv build.gradle.backup build.gradle

cp ${APK} ${BUNDLE_DIR}

BUNDLE_FILE=bundle.tar.gz
rm -f ${BUNDLE_FILE}
tar -czf ${BUNDLE_FILE} ${BUNDLE_DIR}
echo "Output written to file: ${BUNDLE_FILE}"
