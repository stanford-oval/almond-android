#!/bin/bash

set -e
set -x

export CC_host=${CC:-gcc}
export CXX_host=${CXX:-g++}
export LINK_host=${LINK:-g++}
export AR_host=${AR:-ar}

ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT:-~/Android/android-ndk-r16b}

test -d ${ANDROID_NDK_ROOT} || ( mkdir -p ~/Android ; cd ~/Android ; wget https://dl.google.com/android/repository/android-ndk-r16b-linux-x86_64.zip ; unzip android-ndk-r16b-linux-x86_64.zip )

export CFLAGS="-g -O2 -fPIC -fexceptions"
export CXXFLAGS="-g -O2 -fPIC -std=c++14 -fexceptions"
base_PATH=$PATH

this_dir=`pwd`
download=${this_dir}/download
test -d ${download} || mkdir -p ${download}

export OUT=${OUT:-./app/src/main/jniLibs}

download_all() {
# Step 1: boost
test -f ${download}/boost_1_65_1.tar.bz2 || wget https://dl.bintray.com/boostorg/release/1.65.1/source/boost_1_65_1.tar.bz2 -O ${download}/boost_1_65_1.tar.bz2
test -d ${download}/boost_1_65_1 || ( tar -xvf ${download}/boost_1_65_1.tar.bz2 -C ${download} ; cd ${download}/boost_1_65_1 ; ./bootstrap.sh )

test -f ${download}/gmp-6.1.2.tar.xz || wget https://ftp.gnu.org/gnu/gmp/gmp-6.1.2.tar.xz -O ${download}/gmp-6.1.2.tar.xz
test -d ${download}/gmp-6.1.2 || tar -xvf ${download}/gmp-6.1.2.tar.xz -C ${download}

test -f ${download}/cvc4-1.5.tar.gz || wget http://cvc4.cs.stanford.edu/downloads/builds/src/cvc4-1.5.tar.gz -O ${download}/cvc4-1.5.tar.gz
test -d ${download}/cvc4-1.5 || (
tar -xvf ${download}/cvc4-1.5.tar.gz -C ${download} ;
cd ${download}/cvc4-1.5 ;
./contrib/get-antlr-3.4 ;
cd antlr-3.4/src/libantlr3c-3.4 ;
make distclean
autoreconf -fvi )

test -d ${download}/node || git clone https://github.com/Stanford-Mobisocial-IoT-Lab/node.git ${download}/node
(cd ${download}/node ; git checkout 74941eb33c983b2a1a4d6bfc3311bcfb44958357 )

}

build_for_arch() {
arch=$1
case $arch in
    arm)
        triple=arm-linux-androideabi
        nodejs_cpu=arm
        android_arch=armeabi-v7a
        ;;
    x86)
        triple=i686-linux-android
        nodejs_cpu=ia32
        android_arch=x86
        ;;   
    x86_64)
        triple=x86_64-linux-android
        nodejs_cpu=ia32
        android_arch=x86_64
        ;;
    arm64)
        triple=aarch64-linux-android
        nodejs_cpu=arm64
        android_arch=arm64-v8a
        ;;
    *)
        echo "Invalid architecture "$arch
        exit 1
esac

# Step 1: make the toolchain
mkdir -p ${this_dir}/build-$arch
mkdir -p ${this_dir}/build-$arch/prefix
mkdir -p ${this_dir}/build-$arch/prefix/include
mkdir -p ${this_dir}/build-$arch/build
prefix=${this_dir}/build-$arch/prefix
build=${this_dir}/build-$arch/build

test -d ${this_dir}/build-$arch/toolchain || ${ANDROID_NDK_ROOT}/build/tools/make_standalone_toolchain.py --arch ${arch} --api 21 --stl libc++ --install-dir ${this_dir}/build-$arch/toolchain
touch ${prefix}/include/fpu_control.h
# the python in the toolchain is broken, remove it
rm -fr ${this_dir}/build-$arch/toolchain/bin/python* ${this_dir}/build-$arch/toolchain/lib/python2.7 ${this_dir}/build-$arch/toolchain/lib/libpython2.7.a

export ANTLR=`pwd`/download/cvc4-1.5/antlr-3.4/bin/antlr3
export PATH=`pwd`/build-$arch/toolchain/bin:${prefix}/bin:${base_PATH}
export CPATH="${prefix}/include"
export LDFLAGS="-L${prefix}/lib -Wl,--build-id=sha1"

# Step 2: boost
( cd ${download}/boost_1_65_1 ;
./b2 -j8 toolset=clang-android$arch link=shared runtime-link=shared threading=multi variant=release target-os=android architecture=${arch} --without-python install --prefix=${prefix} )

# Step 3: gmp
mkdir -p ${build}/gmp
( cd ${build}/gmp ;
test -f ./config.status || ${download}/gmp-6.1.2/configure CC=${triple}-clang CXX=${triple}-clang++ --prefix=${prefix} --host=${triple} --build=x86_64-linux-gnu --enable-cxx --disable-shared --without-readline --with-pic )
make -j8 -C ${build}/gmp
make -C ${build}/gmp install

# Step 4: antlr
mkdir -p ${build}/antlr3
( cd ${build}/antlr3 ;
test -f ./config.status || ${download}/cvc4-1.5/antlr-3.4/src/libantlr3c-3.4/configure CC=${triple}-clang CXX=${triple}-clang++ --prefix=${prefix} --host=${triple} --build=x86_64-linux-gnu --disable-shared --disable-antlrdebug --with-pic
)
make -j8 -C ${build}/antlr3 CFLAGS="${CFLAGS} -I${download}/cvc4-1.5/antlr-3.4/src/libantlr3c-3.4/include"
make -C ${build}/antlr3 install

# Step 5: cvc4
mkdir -p ${build}/cvc4
( cd ${build}/cvc4 ;
test -f ./config.status || ${download}/cvc4-1.5/configure CC=${triple}-clang CXX=${triple}-clang++ --prefix=${prefix} --host=${triple} --build=x86_64-linux-gnu --enable-optimized --disable-doxygen-doc --disable-replay --with-gmp --with-boost=${prefix} --without-readline --disable-thread-support )
make -j8 -C ${build}/cvc4 CFLAGS="${CFLAGS}" CXXFLAGS="${CXXFLAGS} -Dfgetc_unlocked=fgetc"
make -C ${build}/cvc4 install

# Step 6: node
export CC=${this_dir}/build-$arch/toolchain/bin/$triple-clang
export CXX=${this_dir}/build-$arch/toolchain/bin/$triple-clang++
export LINK=${this_dir}/build-$arch/toolchain/bin/$triple-clang++
export AR=${this_dir}/build-$arch/toolchain/bin/$triple-ar
export STRIP=${this_dir}/build-$arch/toolchain/bin/$triple-strip
export LD=${this_dir}/build-$arch/toolchain/bin/$triple-ld
export GYP_DEFINES="target_arch=${arch} v8_target_arch=${nodejs_cpu} android_target_arch=${arch} host_os=linux OS=android"

test -d ${build}/node || git clone ${download}/node ${build}/node
( cd ${build}/node ;
test -f ./config.gypi || ./configure --shared --dest-cpu=${nodejs_cpu} --dest-os=android --without-snapshot --openssl-no-asm --with-intl=small-icu --without-inspector )
make -j8 -C ${build}/node/out CC.host=${CC_host} CXX.host=${CXX_host} LINK.host=${LINK_host} AR.host=${AR_host}

# Step 7: copy the libraries in the right place
cp ${prefix}/lib/libcvc4.so ${prefix}/lib/libcvc4parser.so ${build}/node/out/Release/lib.target/libnode.so ${OUT}/${android_arch}/
#${STRIP} ${OUT}/${android_arch}/libcvc4.so ${OUT}/${android_arch}/libcvc4parser.so ${OUT}/${android_arch}/libnode.so 

}

download_all
build_for_arch arm
build_for_arch x86
