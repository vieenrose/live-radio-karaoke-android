#!/usr/bin/env bash
# Build onnxruntime from source for Android arm64-v8a (Route A / F-Droid — no prebuilt blobs).
# VERIFIED 2026-06: produces a 19 MB libonnxruntime.so exporting OrtGetApiBase@@VERS_1.24.3,
# ABI-compatible with the sherpa-onnx v1.13.3 Kotlin/JNI API.
#
# Prereqalls: Android NDK r27, Python 3.10+, and CMake >= 3.28 (onnxruntime 1.24 requires it;
# Ubuntu's 3.22 is too old — this script uses a pip-installed cmake in a venv).
#
# Outputs:
#   $OUT_DIR/libonnxruntime.so
#   $ORT_SRC/include/onnxruntime/core/session/   (C/C++ API headers, flat)
set -euo pipefail

ORT_VERSION="${ORT_VERSION:-1.24.3}"          # pinned by sherpa-onnx v1.13.3
ANDROID_NDK="${ANDROID_NDK:?set ANDROID_NDK (e.g. \$ANDROID_HOME/ndk/27.2.12479018)}"
ANDROID_SDK="${ANDROID_HOME:?set ANDROID_HOME}"
WORK="${WORK:-$PWD/.native-build}"
ORT_SRC="$WORK/onnxruntime"
OUT_DIR="$WORK/onnxruntime-android-arm64"
PARALLEL="${PARALLEL:-2}"                       # keep low: ORT TUs are RAM-hungry

mkdir -p "$WORK"

# Recent cmake/ninja in an isolated venv (system cmake 3.22 is too old for ORT 1.24).
if [ ! -x "$WORK/buildenv/bin/cmake" ]; then
  python3 -m venv "$WORK/buildenv"
  "$WORK/buildenv/bin/pip" -q install --upgrade pip
  "$WORK/buildenv/bin/pip" -q install packaging numpy cmake ninja
fi
export PATH="$WORK/buildenv/bin:$PATH"

[ -d "$ORT_SRC/.git" ] || git clone --depth 1 --branch "v$ORT_VERSION" \
  https://github.com/microsoft/onnxruntime.git "$ORT_SRC"

cd "$ORT_SRC"
python tools/ci_build/build.py \
  --build_dir "build/android-arm64-v8a" \
  --config Release \
  --android \
  --android_sdk_path "$ANDROID_SDK" \
  --android_ndk_path "$ANDROID_NDK" \
  --android_abi arm64-v8a \
  --android_api 26 \
  --build_shared_lib \
  --parallel "$PARALLEL" \
  --skip_tests \
  --cmake_generator Ninja \
  --compile_no_warning_as_error

mkdir -p "$OUT_DIR"
cp "build/android-arm64-v8a/Release/libonnxruntime.so" "$OUT_DIR/"
echo "Built: $OUT_DIR/libonnxruntime.so"
echo "Headers: $ORT_SRC/include/onnxruntime/core/session"
