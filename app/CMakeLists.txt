cmake_minimum_required(VERSION 3.22)
project(meetmind)

# T003 (spec 007): Stub CMakeLists for llama.cpp JNI bridge.
#
# The native .so files (liballama.so, libggml*.so) are built EXTERNALLY from the
# llama.cpp source tree using the commands in:
#   app/src/main/jniLibs/arm64-v8a/BUILD_INSTRUCTIONS.md
#
# This CMakeLists.txt exists so Gradle/NDK resolves the toolchain correctly.
# It also declares the pre-built library as an IMPORTED target so the linker
# can reference it from Kotlin via System.loadLibrary("llama").
#
# To add the actual JNI glue (if needed beyond the pre-built .so):
# add_library(meetmind_jni SHARED src/main/cpp/meetmind_jni.cpp)
# target_link_libraries(meetmind_jni llama log)

# Declare pre-built liballama.so as an imported shared library
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES
    IMPORTED_LOCATION
    "${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/liballama.so"
)

# Android system log library
find_library(log-lib log)
