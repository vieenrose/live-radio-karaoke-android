# fdroiddata merge-request draft

Ready-to-adapt files for submitting this app to **f-droid.org**. Copy into a `fdroiddata`
checkout, run `fdroid lint` + `fdroid build`, iterate, then open the MR.

```
fdroiddata/
├── metadata/io.github.vieenrose.liveradiokaraoke.yml   ← from metadata/
└── srclibs/
    ├── onnxruntime.yml          ├── sherpa-onnx.yml
    ├── llama.cpp.yml            └── LiveRadioKaraokeOrtDeps.yml
```

## What's proven (locally, this build)
- **From source**: onnxruntime v1.24.3 + sherpa-onnx v1.13.3 + llama.cpp all compile to an
  installable, signed arm64 APK. No prebuilt `.so` shipped.
- **Offline**: onnxruntime builds with **zero network** — its 14 FetchContent deps are mirrored
  in the `LiveRadioKaraokeOrtDeps` srclib (https://github.com/vieenrose/lrk-ortdeps) and
  `cmake/deps.txt` is rewritten to `file://` (SHA1 hashes preserved). Verified under `unshare -rn`.
- **Blob-free**: onnxruntime's prebuilt host protoc is replaced by protoc 3.21.12 built from the
  protobuf source (`--path_to_protoc_exe`). Verified: *"Using custom protoc executable"*.

## The one item to confirm on the F-Droid builder
onnxruntime 1.24 requires **cmake ≥ 3.28** present **offline** during the build. The recipe's
`sudo:` step installs `cmake`/`ninja-build` (network is allowed in `sudo:`). If the builder's
Debian apt cmake is older than 3.28, fetch the official cmake binary in that `sudo:` step instead.
Everything else (deps, protoc, srclib wiring) is handled by the build scripts in the app repo
(`scripts/build-onnxruntime-android.sh`, `scripts/build-host-protoc.sh`, `scripts/build-sherpa-android.sh`).

## Not yet done
`fdroid build` has not been run in an actual fdroiddata environment from here (it needs that
buildserver). These files are a verified-logic draft, not a green CI run.
