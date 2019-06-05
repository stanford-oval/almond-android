#!/bin/sh

SHA256SUM="08dc5a2275703ff8704fc5030c87cb6226b97f37a4e69f950b50b0b2630c8b9b"
set -x

test -d ./src/main/jniLibs && exit

set -e
cd ./src/main
curl https://crowdie.stanford.edu/android/${SHA256SUM}.tar.gz > ${SHA256SUM}.tar.gz
echo "${SHA256SUM} ${SHA256SUM}.tar.gz" | sha256sum -c
tar xvf ${SHA256SUM}.tar.gz
rm ${SHA256SUM}.tar.gz

