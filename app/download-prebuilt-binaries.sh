#!/bin/sh

SHA256SUM="9c59827a99901423d62d1fa83078393bc73f3e10229ec127cfc04836a4e930dd"
set -x

test -d ./src/main/jniLibs && exit

set -e
cd ./src/main
curl https://crowdie.stanford.edu/android/${SHA256SUM}.tar.gz > ${SHA256SUM}.tar.gz
echo "${SHA256SUM} ${SHA256SUM}.tar.gz" | sha256sum -c
tar xvf ${SHA256SUM}.tar.gz
rm ${SHA256SUM}.tar.gz

