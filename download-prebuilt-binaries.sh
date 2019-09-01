#!/bin/sh

SHA256SUM="3750c8c24902b38d496f3a4d05cc25d2dad473c91715e6d50d5cb53f8e96a56f"
set -x

test -d ./app/src/main/jniLibs && exit

set -e
curl https://almond-static.stanford.edu/test-data/android-prebuilt/${SHA256SUM}.tar.xz -o ${SHA256SUM}.tar.xz
echo "${SHA256SUM} ${SHA256SUM}.tar.xz" | sha256sum -c
tar xvf ${SHA256SUM}.tar.xz
rm ${SHA256SUM}.tar.xz

