#!/usr/bin/env bash

# This script removes signing information from Android apps in .apk
# format and resigns them with the current debug key.

function unsign() {
    echo "Removing signing information from ${APK}..."

    TMPDIR=$(mktemp -d)
    echo "TMPDIR=${TMPDIR}"

    pushd ${TMPDIR} &> /dev/null
    echo "Decompressing..."
    unzip ${APK} &> /dev/null
    for f in "META-INF/CERT.RSA" "META-INF/CERT.SF" "META-INF/ANDROIDD.RSA" "META-INF/ANDROIDD.SF"
    do
	echo "Removing ${f}"
	rm -f $f
    done

    for man in "META-INF/MANIFEST.MF"
    do
	echo "Removing signatures from ${man}..."
	TMP_MANIFEST=$(mktemp)
	grep -v '^SHA[-0-9]*-Digest:' ${man} > ${TMP_MANIFEST}
	mv ${TMP_MANIFEST} ${man}
    done

    echo "Recompressing..."
    rm -f ${OUT_APK}
    zip -r ${OUT_APK} . &> /dev/null

    # rm -rf ${TMP_DIR}
    popd &> /dev/null
}

function sign() {
    local KEYSTORE=~/.android/debug.keystore
    local KEYSTORE_PASS=android
    local KEYSTORE_KEY=androiddebugkey
    echo "Signing using keystore ${KEYSTORE} (key: ${KEYSTORE_KEY}, pass: ${KEYSTORE_PASS})"
    set -x
    jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore ${KEYSTORE} -storepass ${KEYSTORE_PASS} $1 ${KEYSTORE_KEY} > /dev/null
    set +x
}

if [ "$1" == "" ] || [ "$2" == "" ]; then
    echo "Usage: sign-apk.sh app.apk out.apk"
    exit
fi

APK=$(realpath "$1")
OUT_APK=$(realpath "$2")
unsign
sign ${OUT_APK}
