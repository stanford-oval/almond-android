#!/bin/bash

set -e
set -x

export CC_host=${CC:-gcc}
export CXX_host=${CXX:-g++}
export LINK_host=${LINK:-g++}
export AR_host=${AR:-ar}

ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT:-~/Android/android-ndk-r19c}
./download-ndk.sh

export CFLAGS="-g -O2 -fPIC -fexceptions"
export CXXFLAGS="-g -O2 -fPIC -std=c++14 -fexceptions"
base_PATH=$PATH

this_dir=`pwd`
download=${this_dir}/download
test -d ${download} || mkdir -p ${download}

export OUT=${OUT:-./app/src/main/jniLibs}

download_all() {
test -d ${download}/node || git clone https://github.com/stanford-oval/node.git ${download}/node
(cd ${download}/node ; git checkout 74941eb33c983b2a1a4d6bfc3311bcfb44958357 )

}

build_for_arch() {
arch=$1
case $arch in
    arm)
        clang_triple=armv7a-linux-androideabi21
        binutils_triple=arm-linux-androideabi
        nodejs_cpu=arm
        android_arch=armeabi-v7a
        ;;
    x86)
        clang_triple=i686-linux-android21
        binutils_triple=i686-linux-android
        nodejs_cpu=ia32
        android_arch=x86
        ;;   
    x86_64)
        clang_triple=x86_64-linux-android21
        binutils_triple=x86_64-linux-android
        nodejs_cpu=ia32
        android_arch=x86_64
        ;;
    arm64)
        clang_triple=aarch64-linux-android21
        binutils_triple=aarch64-linux-android
        nodejs_cpu=arm64
        android_arch=arm64-v8a
        ;;
    *)
        echo "Invalid architecture "$arch
        exit 1
esac

# Step 1: prepare the toolchain

mkdir -p ${this_dir}/build-$arch
mkdir -p ${this_dir}/build-$arch/prefix
mkdir -p ${this_dir}/build-$arch/prefix/include
mkdir -p ${this_dir}/build-$arch/build
prefix=${this_dir}/build-$arch/prefix
build=${this_dir}/build-$arch/build

export PATH=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin:${base_PATH}
export CPATH="${prefix}/include"
export LDFLAGS="-L${prefix}/lib -Wl,--build-id=sha1"

export CC=$clang_triple-clang
export CXX=$clang_triple-clang++
export LINK=$clang_triple-clang++
export AR=$binutils_triple-ar
export STRIP=$binutils_triple-strip
export LD=$binutils_triple-ld

# Step 2: build node

export GYP_DEFINES="target_arch=${arch} v8_target_arch=${nodejs_cpu} android_target_arch=${arch} host_os=linux OS=android"

test -d ${build}/node || git clone ${download}/node ${build}/node
( cd ${build}/node ;
test -f ./config.gypi || ./configure --shared --dest-cpu=${nodejs_cpu} --dest-os=android --without-snapshot --openssl-no-asm --with-intl=small-icu --without-inspector )
make -j8 -C ${build}/node/out CC.host=${CC_host} CXX.host=${CXX_host} LINK.host=${LINK_host} AR.host=${AR_host}

# Step 3: copy the libraries in the right place
mkdir -p ${OUT}/${android_arch}
cp ${build}/node/out/Release/lib.target/libnode.so ${OUT}/${android_arch}/
#${STRIP} ${OUT}/${android_arch}/libcvc4.so ${OUT}/${android_arch}/libcvc4parser.so ${OUT}/${android_arch}/libnode.so 

}

download_all
build_for_arch arm
build_for_arch x86
build_for_arch arm64
build_for_arch x86_64
