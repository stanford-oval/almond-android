# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library(node-launcher SHARED
             src/main/jni/node-launcher.cpp
             src/main/jni/node-launcher-utils.cpp
             src/main/jni/node-launcher-marshal.cpp
             src/main/jni/node-launcher-module.cpp
             src/main/jni/node-cvc4.cpp )

add_subdirectory(src/main/jni/sqlite)
add_subdirectory(src/main/jni/node-sqlite3)

find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(node-launcher
                      node-sqlite3 sqlite ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libnode.so ${log-lib} ${android-lib})