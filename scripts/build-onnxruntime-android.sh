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

# onnxruntime 1.24 needs cmake >= 3.28 (Ubuntu's 3.22 is too old). By default we pip-install a
# recent cmake/ninja into a venv. On an OFFLINE builder (e.g. F-Droid) that has cmake>=3.28 +
# ninja already provisioned (via the recipe's `sudo: apt-get install`), set ORT_USE_SYSTEM_TOOLS=1
# to skip the venv/pip (which would need network).
if [ -z "${ORT_USE_SYSTEM_TOOLS:-}" ]; then
  if [ ! -x "$WORK/buildenv/bin/cmake" ]; then
    python3 -m venv "$WORK/buildenv"
    "$WORK/buildenv/bin/pip" -q install --upgrade pip
    "$WORK/buildenv/bin/pip" -q install packaging numpy cmake ninja
  fi
  export PATH="$WORK/buildenv/bin:$PATH"
fi

[ -d "$ORT_SRC/.git" ] || git clone --depth 1 --branch "v$ORT_VERSION" \
  https://github.com/microsoft/onnxruntime.git "$ORT_SRC"

# ── Offline mode (for F-Droid: the build must run with NO network) ────────────────────────────
# onnxruntime's CMake FetchContent pulls ~15 deps over the network at configure time. To build
# offline, mirror those archives locally and rewrite deps.txt to file:// URLs (SHA1 hashes are
# kept, so integrity is still verified). VERIFIED 2026-06 to configure with zero network under
# `unshare -rn`. Set OFFLINE_DEPS_MIRROR=<dir> to enable; on a networked machine the archives are
# fetched once into that dir, after which the build needs no network.
# Note: protoc_linux_x64 (onnxruntime's prebuilt host protoc) is intentionally NOT mirrored —
# in offline mode we build protoc from the protobuf source below and pass --path_to_protoc_exe,
# so the build pulls no prebuilt binary blob.
DEPS_FOR_ANDROID_CPU="abseil_cpp date eigen flatbuffers googletest microsoft_gsl kleidiai mp11 \
json onnx protobuf pytorch_cpuinfo re2 safeint"
PROTOC_ARG=()
if [ -n "${OFFLINE_DEPS_MIRROR:-}" ]; then
  mkdir -p "$OFFLINE_DEPS_MIRROR"
  [ -f "$ORT_SRC/cmake/deps.txt.orig" ] || cp "$ORT_SRC/cmake/deps.txt" "$ORT_SRC/cmake/deps.txt.orig"
  for k in $DEPS_FOR_ANDROID_CPU; do
    url=$(grep -E "^$k;" "$ORT_SRC/cmake/deps.txt.orig" | cut -d';' -f2)
    fn=$(basename "$url")
    [ -f "$OFFLINE_DEPS_MIRROR/$fn" ] || wget -q -O "$OFFLINE_DEPS_MIRROR/$fn" "$url"
  done
  awk -F';' -v OFS=';' -v mir="$OFFLINE_DEPS_MIRROR" -v need="$DEPS_FOR_ANDROID_CPU" '
    BEGIN{ split(need,a," "); for(i in a) n[a[i]]=1 }
    /^#/||NF<3 { print; next }
    { if($1 in n){ c=split($2,p,"/"); $2="file://" mir "/" p[c] } print }' \
    "$ORT_SRC/cmake/deps.txt.orig" > "$ORT_SRC/cmake/deps.txt"
  echo "Offline mirror ready ($(echo $DEPS_FOR_ANDROID_CPU | wc -w) deps in $OFFLINE_DEPS_MIRROR)"
  # Build host protoc from source (blob-free) and tell onnxruntime to use it.
  HOST_PROTOC="$(bash "$(dirname "$0")/build-host-protoc.sh")"
  PROTOC_ARG=(--path_to_protoc_exe "$HOST_PROTOC")
  echo "Using source-built host protoc: $HOST_PROTOC"
fi

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
  --compile_no_warning_as_error \
  "${PROTOC_ARG[@]}"

mkdir -p "$OUT_DIR"
cp "build/android-arm64-v8a/Release/libonnxruntime.so" "$OUT_DIR/"
echo "Built: $OUT_DIR/libonnxruntime.so"
echo "Headers: $ORT_SRC/include/onnxruntime/core/session"
