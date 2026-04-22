# Native Library Build Instructions

The `.so` files in this directory (`liballama.so`, `libggml.so`, etc.) are built from
[llama.cpp](https://github.com/ggml-org/llama.cpp) source and are **not checked into git**.

## Build Steps

See `specs/007-on-device-gemma4/quickstart.md` Step 1 for the full CMake build command.

**Quick reference:**

```bash
git clone https://github.com/ggml-org/llama.cpp.git
cd llama.cpp && mkdir build-android && cd build-android

cmake .. -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  -DBUILD_SHARED_LIBS=ON \
  -DGGML_OPENCL=ON \
  -DGGML_OPENCL_USE_ADRENO_KERNELS=ON \
  -DGGML_OPENCL_EMBED_KERNELS=ON \
  -DGGML_OPENCL_SMALL_ALLOC=ON \
  -DGGML_OPENMP=OFF

cmake --build . --config Release -j8

# Copy output to this directory:
cp lib/liballama.so <repo>/app/src/main/jniLibs/arm64-v8a/
cp lib/libggml*.so  <repo>/app/src/main/jniLibs/arm64-v8a/
```

## Requirements

- Android NDK r27+
- Ninja build system
- `ANDROID_NDK_ROOT` environment variable set
- Target: Lenovo Legion Y700 Gen 3 (Snapdragon 8 Gen 3, Adreno 750, arm64-v8a)

## Minimum llama.cpp version

Use a commit from **April 2026 or later** for Gemma 4 architecture support.
