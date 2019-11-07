#!/usr/bin/env bash

function usage() {
    echo "Post bundle:"
    echo "  bundle.sh postBundle"
    echo "Save bundle to directory:"
    echo "  bundle.sh save BUNDLE_DIR"
    echo
    echo "Generates the appropriate files to be posted to the Web UI."
    echo "This script should be run in the application module directory."
    echo
    echo "  BUNDLE_DIR      example: bundle-app"
    echo
    exit
}

function createBundleArchive() {
    local BUNDLE_DIR="$1"
    local LOCAL_BUNDLE_DIR=".clue-bundle"

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

if [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
    usage
elif [ "$1" != "postBundle" ] && [ "$1" != "save" ]; then
    usage
elif [ "$1" == "postBundle" ] && [ "$2" != "" ]; then
    usage
elif [ "$1" == "save" ] && [ "$2" == "" ]; then
    usage
fi

SCRIPTS_DIR=$(dirname "$0")

if [ "$1" == "save" ]; then
    ${SCRIPTS_DIR}/build-bundle.sh
    createBundleArchive "$2"
elif [ "$1" == "postBundle" ]; then
    ${SCRIPTS_DIR}/build-bundle.sh postBundle
fi
