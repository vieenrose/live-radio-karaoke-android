#!/usr/bin/env bash
# Build protoc (3.21.12) from the protobuf source for the HOST — so the onnxruntime cross-build
# uses a from-source protoc instead of onnxruntime's prebuilt protoc blob (F-Droid: no blobs).
# VERIFIED 2026-06: onnxruntime accepts it via --path_to_protoc_exe ("Using custom protoc
# executable") and builds offline with no protoc download.
#
# Uses the protobuf v21.12 archive already in the offline deps mirror (same version onnxruntime
# pins), so no extra download. Outputs the protoc path on stdout (last line).
set -euo pipefail

WORK="${WORK:-$PWD/.native-build}"
MIRROR="${OFFLINE_DEPS_MIRROR:-$WORK/onnxruntime-deps-mirror}"
OUT="$WORK/host-protoc"
export PATH="$WORK/buildenv/bin:$PATH"   # recent cmake/ninja from build-onnxruntime-android.sh

PB_ZIP="$MIRROR/v21.12.zip"
[ -f "$PB_ZIP" ] || { echo "protobuf v21.12.zip not in mirror ($MIRROR); run the mirror step first" >&2; exit 1; }

rm -rf "$OUT"; mkdir -p "$OUT"; cd "$OUT"
unzip -q "$PB_ZIP"
SRC="$PWD/$(ls -d protobuf-*/ | head -1)"
# protobuf 3.21.12 is self-contained (no abseil dep); build only the protoc target.
cmake -S "$SRC" -B build -G Ninja -DCMAKE_BUILD_TYPE=Release \
  -Dprotobuf_BUILD_TESTS=OFF -Dprotobuf_BUILD_SHARED_LIBS=OFF -DCMAKE_CXX_STANDARD=17 >/dev/null
cmake --build build --target protoc -j"$(nproc)" >/dev/null
PROTOC="$(readlink -f build/protoc)"
"$PROTOC" --version >&2
echo "$PROTOC"
