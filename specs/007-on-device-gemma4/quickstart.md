# Quickstart: On-Device Gemma 4 E4B (spec 007)

## Prerequisites

- Android NDK r27+ installed (via Android Studio SDK Manager)
- `ANDROID_NDK_ROOT` environment variable set
- Ninja build system installed (`choco install ninja` on Windows)
- Lenovo Legion Y700 Gen 3 connected via USB with ADB debugging enabled
- ~6 GB free space on device

---

## Step 1: Build llama.cpp for Android

```bash
# Clone llama.cpp (use April 2026+ commit for Gemma 4 support)
git clone https://github.com/ggml-org/llama.cpp.git
cd llama.cpp

# Create Android build directory
mkdir build-android && cd build-android

# Configure with OpenCL (Adreno 750) + Android arm64
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

# Copy .so files to your app
cp lib/libllama.so F:/Git/meetmind-assistant/app/src/main/jniLibs/arm64-v8a/
cp lib/libggml.so F:/Git/meetmind-assistant/app/src/main/jniLibs/arm64-v8a/
```

---

## Step 2: Download the Gemma 4 E4B GGUF

```bash
# Option A: Download via huggingface-cli
pip install huggingface_hub
huggingface-cli download ggml-org/gemma-4-E4B-it-GGUF \
  gemma-4-E4B-it-Q4_K_M.gguf \
  --local-dir ~/Downloads/

# Option B: Direct browser download from:
# https://huggingface.co/ggml-org/gemma-4-E4B-it-GGUF
```

---

## Step 3: Push model to device

```bash
# Push GGUF to device Downloads folder (fastest path for personal APK)
adb push ~/Downloads/gemma-4-E4B-it-Q4_K_M.gguf /sdcard/Download/

# Verify it's there (~5 GB)
adb shell ls -lh /sdcard/Download/gemma-4-E4B-it-Q4_K_M.gguf
```

---

## Step 4: Build and install the app

```bash
cd F:/Git/meetmind-assistant

# Build debug APK
./gradlew installDebug

# Launch app
adb shell am start -n com.meetmind.assistant/.MainActivity
```

---

## Step 5: Configure model path in app

1. Open **Settings → On-Device Model**
2. The default path `/sdcard/Download/gemma-4-E4B-it-Q4_K_M.gguf` should be pre-filled
3. Tap **Load Model** — wait ~3–5 seconds for model to load into RAM
4. Status indicator turns green: **"Model ready"**
5. Start a session — questions will now be answered by Gemma 4 E4B on-device

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| "Model not found" | Re-run adb push; verify path in Settings |
| "Load failed: JNI error" | Ensure `liballama.so` is in `jniLibs/arm64-v8a/` |
| OOM crash | Reduce `nGpuLayers` in Settings to 20 (partial GPU offload) |
| Very slow (< 3 tok/s) | Check OpenCL is enabled; run `adb logcat -s llama` for backend info |
| First token > 1s | Expected for first request (JIT kernel compilation). Second+ requests are faster. |

---

## Monitoring inference on device

```bash
# Stream llama.cpp logs
adb logcat -s llama,ggml,OnDeviceLlamaProvider

# Check RAM usage
adb shell dumpsys meminfo com.meetmind.assistant | grep -E "TOTAL|Native"

# Check GPU utilisation (Adreno)
adb shell cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage
```
