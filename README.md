# Live Radio Karaoke — Android (on-device)

Real-time, multilingual radio transcription with karaoke-style word highlighting, **running
fully on-device**. A native Kotlin/Jetpack-Compose port of the [Live Radio Karaoke web
app](https://huggingface.co/spaces/Luigi/Live-Radio-Karaoke), built for **F-Droid**: no servers,
no tracking, all AI on the edge.

- **ASR** — streaming speech-to-text via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
  (bilingual zh+en with inline punctuation, or French).
- **Summarize + translate** — one on-device LLM (**Gemma 3 1B**, QAT Q4_0) via
  [llama.cpp](https://github.com/ggml-org/llama.cpp), serving both summaries and live translation.
- **Playback** — AndroidX **Media3 / ExoPlayer** streams the radio and a pass-through audio
  processor taps decoded PCM (16 kHz mono) for ASR — one decode path, two consumers.
- **Karaoke** — a per-frame loop maps the player position onto absolute token times.

> **Status:** complete source scaffold with full feature parity, committed for build in Android
> Studio. It has **not** been compiled on the authoring machine (no Android SDK there). Expect a
> few first-build fixups — see *Known fixup areas*.

## Architecture (Python web app → Android)

| Web app (Python/JS) | Android (Kotlin) |
|---|---|
| `core/audio_streamer.py` (aiohttp + ffmpeg) | `audio/RadioStreamController` (ExoPlayer) + `audio/PcmTapAudioProcessor` |
| `core/asr_service.py` (sherpa-onnx) | `asr/AsrEngine` + `asr/OpenCcConverter` |
| `core/summarizer_service.py` (llama-cpp) | `llm/LlmEngine` + `llm/LlamaBridge` (JNI) + `llm/GemmaPrompt` |
| `core/connection_manager.py` (WebSocket fan-out) | *removed* — UI observes Kotlin `StateFlow`s |
| `config.py` / `performance_config.py` | `data/Config` / `data/DeviceTier` |
| `api/radio_browser.py` | `data/RadioBrowserApi` |
| `app.py` startup downloads | `data/ModelRepository` + first-run UI |
| `frontend/*` (HTML/CSS/JS) | `ui/*` (Jetpack Compose) |

Going on-device collapses the network layer: playback and ASR share one decoded timeline in one
process, so the WebSocket/clock-offset/epoch machinery is gone — token times anchor directly to
`ExoPlayer.currentPosition`.

## Build

Requirements: Android Studio (Ladybug+) or the command-line SDK, JDK 17+, NDK 27, CMake 3.22.1.

### 1. Audio-only / UI build (no native AI) — fastest
```bash
./gradlew assembleDevDebug
```
Builds the full app without the heavy native libs. ASR/LLM are disabled (the app is a radio
player); everything else (UI, discovery, playback, export) works. Good for iterating on UI.

### 2. Full on-device build (ASR + LLM)
```bash
./scripts/fetch-native-libs.sh        # clones llama.cpp, points you at the sherpa-onnx .so
./gradlew assembleDevDebug -PwithNative
```
`-PwithNative` enables the CMake build of `llama.cpp` and packages the sherpa-onnx native
libraries. Models are downloaded on first run (see below).

### On-device models (downloaded at first run, never bundled)
- **ASR**: sherpa-onnx X-ASR (zh+en) or fr-kroko — fetched from the k2-fsa GitHub release / HF.
- **LLM**: **Gemma 3 1B** GGUF from Hugging Face.

## Licensing

- **App code: Apache-2.0** (`LICENSE`) — OSI-approved, F-Droid-eligible.
- **Dependencies are all FOSS and source-buildable**: sherpa-onnx (Apache-2.0), onnxruntime (MIT),
  llama.cpp (MIT), Media3/Compose/OkHttp/commons-compress (Apache-2.0), opencc4j (Apache-2.0).
  No prebuilt binary blobs are committed.
- **Models download at runtime, so the APK stays 100 % free:**
  - **Gemma 3 1B** is under the **non-free "Gemma Terms of Use."** The app shows a **consent
    screen** before downloading it, and offers a **fully-open alternative** (LFM2.5-1.2B /
    Qwen2.5-1.5B, both Apache-2.0) for a zero-non-free path. For F-Droid this is declared as the
    `NonFreeNet` AntiFeature.
  - ASR models from k2-fsa are Apache/MIT (verify per model on its release page).

## F-Droid

`fdroid/metadata-template.yml` is the build recipe to drop into `fdroiddata`. The whole native
stack builds **from source** (no prebuilt blobs) — **verified locally (2026-06)**:

| Library | Source | Output | How |
|---|---|---|---|
| onnxruntime v1.24.3 | microsoft/onnxruntime | `libonnxruntime.so` (19 MB) | `scripts/build-onnxruntime-android.sh` |
| sherpa-onnx v1.13.3 | k2-fsa/sherpa-onnx | `libsherpa-onnx-jni.so` (3.4 MB) | `scripts/build-sherpa-android.sh` (links the above) |
| llama.cpp | ggml-org/llama.cpp | `libllama-android.so` (4.9 MB) | gradle `externalNativeBuild`, `-PwithNative` |

A from-source-built APK (all three) compiles and packages cleanly; the rebuilt
`libsherpa-onnx-jni.so` links our source-built `libonnxruntime.so` and exports the exact JNI
symbols our vendored Kotlin API binds to.

**Offline build (F-Droid's no-network policy) — solved & verified.** F-Droid builders have no
network after srclib checkout, but onnxruntime's CMake FetchContent pulls ~15 deps at configure
time. `scripts/build-onnxruntime-android.sh` (with `OFFLINE_DEPS_MIRROR=<dir>`) mirrors those
archives and rewrites `cmake/deps.txt` to `file://` URLs (SHA1 hashes preserved), then builds.
**Verified to configure with zero network under `unshare -rn`** (`Configuring done`, exit 0). The
15 deps: abseil_cpp, date, eigen, flatbuffers, googletest, microsoft_gsl, kleidiai, mp11, json,
onnx, protobuf, pytorch_cpuinfo, re2, safeint, protoc_linux_x64.

Two mechanical items remain before an actual fdroiddata merge request: (1) ship those 15 archives
on the offline builder as an `ortdeps` srclib; (2) `protoc_linux_x64` is a prebuilt host-protoc
blob — for a fully blob-free build, compile protoc from the bundled protobuf source for the host.
Meanwhile, the self-hosted repo (Route B) ships the same source-built `.so` today.

## Known fixup areas (first compile in Android Studio)

1. **`PcmTapAudioProcessor` / `RadioStreamController`** — Media3's audio-processor + `buildAudioSink`
   APIs are `@UnstableApi` and shift between releases; signatures here target Media3 **1.5.1**.
2. **sherpa-onnx native coordinates** — `app/src/main/java/com/k2fsa/sherpa/onnx/SherpaOnnx.kt`
   is vendored to match the JNI symbols of `libsherpa-onnx-jni.so`; keep it in sync with the `.so`
   version you ship (`scripts/fetch-native-libs.sh`).
3. **llama.cpp JNI** — `app/src/main/cpp/llama_jni.cpp` targets the modern llama.cpp API
   (`llama_model_load_from_file`, sampler chains); pin a `LLAMA_REF` known to match.

## Credits

Ports the architecture and station set of the original Live Radio Karaoke. sherpa-onnx Kotlin API
files are vendored from k2-fsa/sherpa-onnx (Apache-2.0).
