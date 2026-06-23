#!/usr/bin/env bash
# Fetch the native dependencies that are intentionally NOT committed to this repo:
#   1. sherpa-onnx prebuilt Android .so (ASR)  -> app/src/main/jniLibs/
#   2. llama.cpp source (LLM, built from source by CMake) -> app/src/main/cpp/llama.cpp/
#
# Run this once before building with -PwithNative:
#   ./scripts/fetch-native-libs.sh && ./gradlew assembleDevDebug -PwithNative
#
# The F-Droid build does NOT use this script for sherpa-onnx — it compiles sherpa-onnx +
# onnxruntime from source as srclibs (see fdroid/metadata-template.yml). llama.cpp is built
# from source in both cases.
set -euo pipefail
cd "$(dirname "$0")/.."

SHERPA_VERSION="${SHERPA_VERSION:-1.10.46}"   # pick a release that ships the streaming Kotlin API .so
LLAMA_REF="${LLAMA_REF:-master}"

JNI_DIR="app/src/main/jniLibs"
CPP_DIR="app/src/main/cpp/llama.cpp"

echo "==> llama.cpp ($LLAMA_REF)"
if [ ! -d "$CPP_DIR/.git" ]; then
  git clone --depth 1 --branch "$LLAMA_REF" https://github.com/ggml-org/llama.cpp "$CPP_DIR" \
    || git clone --depth 1 https://github.com/ggml-org/llama.cpp "$CPP_DIR"
else
  echo "    already present"
fi

echo "==> sherpa-onnx prebuilt Android libs (v$SHERPA_VERSION)"
mkdir -p "$JNI_DIR"
TARBALL="sherpa-onnx-v${SHERPA_VERSION}-android.tar.bz2"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/${TARBALL}"
echo "    NOTE: verify the asset name on the sherpa-onnx releases page; layout changes across versions."
echo "    Expected: arm64-v8a/, x86_64/ containing libsherpa-onnx-jni.so + libonnxruntime.so"
echo "    URL: $URL"
echo
echo "    Download it, extract the per-ABI folders into: $JNI_DIR/"
echo "    e.g. $JNI_DIR/arm64-v8a/libsherpa-onnx-jni.so"
echo
echo "Done. llama.cpp is ready; place the sherpa-onnx .so files as described, then build with -PwithNative."
