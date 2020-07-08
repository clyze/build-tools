#!/usr/bin/env bash

set -e

function usage() {
    echo "Post build:"
    echo "  build.sh postBuild [MODULE]"
    echo "Save build to directory:"
    echo "  build.sh save BUILD_DIR [MODULE]"
    echo
    echo "Generates the appropriate files to be posted to the Web UI."
    echo "This script should be run in the top-level directory."
    echo
    echo "  BUILD_DIR       example: build-myapp"
    echo "  MODULE          example: app (default is current directory)"
    echo
    exit
}

function cleanBuildDir() {
    mkdir -p $1
    rm -f $1/*.apk $1/*.zip $1/*.jar java8.txt
    rm -rf $1/json
}

function createBuildArchive() {
    local BUILD_DIR="$1"

    cleanBuildDir "${LOCAL_BUILD_DIR}"
    cleanBuildDir "${BUILD_DIR}"

    cp ${LOCAL_BUILD_DIR}/metadata.zip ${BUILD_DIR}
    cp ${LOCAL_BUILD_DIR}/configurations.zip ${BUILD_DIR}
    cp ${LOCAL_BUILD_DIR}/sources.jar ${BUILD_DIR}
    cp ${LOCAL_BUILD_DIR}/*.apk ${BUILD_DIR}

    BUILD_FILE=build.tar.gz
    rm -f ${BUILD_FILE}
    tar -czf ${BUILD_FILE} ${BUILD_DIR}
    echo "Output written to file: ${BUILD_FILE}"
}

if [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
    usage
elif [ "$1" != "postBuild" ] && [ "$1" != "save" ]; then
    usage
elif [ "$1" == "save" ] && [ "$2" == "" ]; then
    usage
fi

LOCAL_BUILD_DIR=".clue-build"
SCRIPTS_DIR=$(dirname "$0")

if [ "$1" == "save" ]; then
    ${SCRIPTS_DIR}/build-build.sh dry-run $3
    createBuildArchive "$2"
elif [ "$1" == "postBuild" ]; then
    cleanBuildDir "${LOCAL_BUILD_DIR}"
    ${SCRIPTS_DIR}/build-build.sh postBuild $2
fi
