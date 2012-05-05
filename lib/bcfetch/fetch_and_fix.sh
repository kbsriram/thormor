#!/bin/bash

rm -f bcprov-jdk15on-147.jar.orig bcpg-jdk15on-147.jar.orig

wget http://www.bouncycastle.org/download/bcprov-jdk15on-147.jar -O bcprov-jdk15on-147.jar.orig
wget http://www.bouncycastle.org/download/bcpg-jdk15on-147.jar -O bcpg-jdk15on-147.jar.orig

## Verify we got expected files
shasum -c shacheck.txt || exit 1

## Unzip and remove signatures.
function fixup() {
    echo "Renaming packages from $1"
    rm -rf tmp
    mkdir tmp
    cd tmp
    unzip -q "../$1.orig"
    rm -rf META-INF

    # rename package name
    find . -type f -exec perl -pi -e 's/bouncycastle/bouncyrattle/g' '{}' \;

    # rename constant pool string for the security provider id.
    if [ -e org/bouncycastle/jce/provider/BouncyCastleProvider.class ]
    then
        # This is asciiz (01) length (0002) BC
        perl -pi -e 's/\x01\x00\x02BC/\x01\x00\x02BR/' org/bouncycastle/jce/provider/BouncyCastleProvider.class
    fi

    # move classes to the right spot
    mv org/bouncycastle org/bouncyrattle

    # rezip
    rm -f "../../$1"
    zip -q -r -9 "../../$1" org
    cd ..
    rm -rf tmp
}

fixup bcprov-jdk15on-147.jar
fixup bcpg-jdk15on-147.jar
