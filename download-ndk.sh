#!/bin/sh

ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT:-~/Android/android-ndk-r19c}

test -d ${ANDROID_NDK_ROOT} || ( mkdir -p ~/Android ; cd ~/Android ; wget -q https://dl.google.com/android/repository/android-ndk-r19c-linux-x86_64.zip ; unzip -q android-ndk-r19c-linux-x86_64.zip )
