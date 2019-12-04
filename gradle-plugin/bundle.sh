#!/usr/bin/env bash

function usage() {
    echo "Post bundle:"
    echo "  bundle.sh postBundle [MODULE]"
    echo "Save bundle to directory:"
    echo "  bundle.sh save BUNDLE_DIR [MODULE]"
    echo
    echo "Generates the appropriate files to be posted to the Web UI."
    echo "This script should be run in the top-level directory."
    echo
    echo "  BUNDLE_DIR      example: bundle-myapp"
    echo "  MODULE          example: app (default is current directory)"
    echo
    exit
}

function cleanBundleDir() {
    mkdir -p $1
    rm -f $1/*.apk $1/*.zip $1/*.jar java8.txt
    rm -rf $1/json
}

function createBundleArchive() {
    local BUNDLE_DIR="$1"

    cleanBundleDir "${LOCAL_BUNDLE_DIR}"
    cleanBundleDir "${BUNDLE_DIR}"

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
elif [ "$1" == "save" ] && [ "$2" == "" ]; then
    usage
fi

LOCAL_BUNDLE_DIR=".clue-bundle"
SCRIPTS_DIR=$(dirname "$0")

if [ "$1" == "save" ]; then
    ${SCRIPTS_DIR}/build-bundle.sh dry-run $3
    createBundleArchive "$2"
elif [ "$1" == "postBundle" ]; then
    cleanBundleDir "${LOCAL_BUNDLE_DIR}"
    ${SCRIPTS_DIR}/build-bundle.sh postBundle $2
fi
