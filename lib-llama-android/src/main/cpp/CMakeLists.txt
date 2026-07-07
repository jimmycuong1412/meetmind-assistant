cmake_minimum_required(VERSION 3.31.6)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}"   CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}" CACHE STRING "" FORCE)

# --------------------------------------------------------------------------
# AI Chat library
# --------------------------------------------------------------------------

if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../../llama.cpp-master)
add_subdirectory(${LLAMA_SRC} build-llama)

# --------------------------------------------------------------------------
# mtmd — llama.cpp multimodal (vision) support, compiled from the vendored
# tree. Built here directly (not via add_subdirectory) because upstream's
# tools/mtmd/CMakeLists.txt also defines CLI executables and install rules
# that do not apply to an Android library build.
# --------------------------------------------------------------------------
file(GLOB MTMD_MODEL_SOURCES ${LLAMA_SRC}/tools/mtmd/models/*.cpp)
add_library(mtmd STATIC
        ${LLAMA_SRC}/tools/mtmd/mtmd.cpp
        ${LLAMA_SRC}/tools/mtmd/mtmd-audio.cpp
        ${LLAMA_SRC}/tools/mtmd/mtmd-image.cpp
        ${LLAMA_SRC}/tools/mtmd/mtmd-helper.cpp
        ${LLAMA_SRC}/tools/mtmd/clip.cpp
        ${MTMD_MODEL_SOURCES})
target_include_directories(mtmd PUBLIC ${LLAMA_SRC}/tools/mtmd)
target_include_directories(mtmd PRIVATE ${LLAMA_SRC} ${LLAMA_SRC}/vendor)
target_link_libraries(mtmd PUBLIC ggml llama)
target_compile_features(mtmd PRIVATE cxx_std_17)
# stb_image.h needs -Wno-cast-qual; miniaudio.h needs -Wno-missing-prototypes (upstream does the same)
target_compile_options(mtmd PRIVATE -Wno-cast-qual -Wno-missing-prototypes)

add_library(${CMAKE_PROJECT_NAME} SHARED
        ai_chat.cpp)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src
        ${LLAMA_SRC}/vendor)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        llama-common
        mtmd
        android
        log)
