#!/usr/bin/env bash
# Build sherpa-onnx (streaming ASR + JNI) from source for Android arm64-v8a, linking against a
# source-built onnxruntime (so NO prebuilt blobs enter the build — Route A / F-Droid).
# VERIFIED 2026-06: produces a 3.4 MB libsherpa-onnx-jni.so that NEEDED libonnxruntime.so and
# exports the Java_com_k2fsa_sherpa_onnx_Online{Recognizer,Stream}_* symbols our vendored
# Kotlin API (app/src/main/java/com/k2fsa/sherpa/onnx/SherpaOnnx.kt) binds to.
#
# Run build-onnxruntime-android.sh FIRST. Outputs:
#   $OUT_DIR/{libsherpa-onnx-jni.so, libonnxruntime.so}  → copy into app/src/main/jniLibs/arm64-v8a/
set -euo pipefail

SHERPA_VERSION="${SHERPA_VERSION:-1.13.3}"
ANDROID_NDK="${ANDROID_NDK:?set ANDROID_NDK}"
WORK="${WORK:-$PWD/.native-build}"
SHERPA_SRC="$WORK/sherpa-onnx"
ORT_OUT="$WORK/onnxruntime-android-arm64"                    # from build-onnxruntime-android.sh
ORT_SRC="$WORK/onnxruntime"
OUT_DIR="$WORK/sherpa-android-arm64"
export PATH="$WORK/buildenv/bin:$PATH"

[ -f "$ORT_OUT/libonnxruntime.so" ] || { echo "Run build-onnxruntime-android.sh first"; exit 1; }

[ -d "$SHERPA_SRC/.git" ] || git clone --depth 1 --branch "v$SHERPA_VERSION" \
  https://github.com/k2-fsa/sherpa-onnx.git "$SHERPA_SRC"

cd "$SHERPA_SRC"
# Point sherpa's build at our source-built onnxruntime (skips its prebuilt download).
export SHERPA_ONNXRUNTIME_LIB_DIR="$ORT_OUT"
export SHERPA_ONNXRUNTIME_INCLUDE_DIR="$ORT_SRC/include/onnxruntime/core/session"
export BUILD_SHARED_LIBS=ON
export SHERPA_ONNX_ENABLE_TTS=OFF                  # we only need streaming ASR
export SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF
export SHERPA_ONNX_ENABLE_JNI=ON
export SHERPA_ONNX_ENABLE_BINARY=OFF
export SHERPA_ONNX_ENABLE_C_API=OFF
export SHERPA_ONNX_ANDROID_PLATFORM=android-26

bash build-android-arm64-v8a.sh

mkdir -p "$OUT_DIR"
cp build-android-arm64-v8a/install/lib/libsherpa-onnx-jni.so "$OUT_DIR/"
cp "$ORT_OUT/libonnxruntime.so" "$OUT_DIR/"
echo "Built (copy both into app/src/main/jniLibs/arm64-v8a/):"
ls -lh "$OUT_DIR"
