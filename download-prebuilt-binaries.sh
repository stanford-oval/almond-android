#!/bin/sh

SHA256SUM="230b391bf57877cc8f22c1a44fcaed47f7c164735d6a7dc8ce392205420121bd"
set -x

test -d ./app/src/main/jniLibs && exit

set -e
curl https://almond-static.stanford.edu/test-data/android-prebuilt/${SHA256SUM}.tar.xz -o ${SHA256SUM}.tar.xz
echo "${SHA256SUM} ${SHA256SUM}.tar.xz" | sha256sum -c
tar xvf ${SHA256SUM}.tar.xz
rm ${SHA256SUM}.tar.xz

