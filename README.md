# Mudra

An Android app that turns hand signs into speech and captions, entirely on-device: camera →
MediaPipe hand tracking → a sign classifier → a temporal decoder → an on-device Qwen model for
language reconstruction → TTS/captions. No cloud AI required.

`index.html` at the repo root is the project's marketing/landing page and is unrelated to the
Android app below.

## Architecture

```
Camera
  |
MediaPipe Hand Landmarker      (app: core/vision)
  |
Hand landmarks
  |
Custom sign model              (core: core/sign)
  |
Top-K sign candidates
  |
Temporal decoder               (core: core/temporal)
  |
Qwen (language reasoner)       (core: core/reasoning)
  |
Natural-language reconstruction / intent
  |
TTS / captions                 (app: tts, ui/caption)
```

This is two Gradle modules, split deliberately along the "does it need Android?" line:

- **`:core`** - a plain Kotlin/JVM module (no Android Gradle Plugin, no Android SDK dependency
  at all). Sign classification math, temporal phrase-boundary detection, the Qwen prompt/JSON
  contract, and the Mock/real language reasoners all live here, precisely because none of that
  logic actually needs a phone to run or to be tested.
- **`:app`** - the Android application. CameraX, MediaPipe, Room, DataStore, Compose UI, and the
  JNI bridge live here, wrapping `:core`'s pipeline stages in real Android glue.

Qwen never sees frames or images - only structured JSON evidence (glosses + confidences +
prior turn), and it is never given any tool or action surface (no telecom, contacts, SMS,
accessibility APIs). Its entire job is "return this JSON shape or be ignored." See
`core/src/main/kotlin/.../reasoning/QwenPromptBuilder.kt` for the exact prompt and
`core/src/main/kotlin/.../reasoning/QwenLanguageReasoner.kt` for how its output is validated.

The temporal decoder also means Qwen runs once per completed phrase (after a signing pause),
not per camera frame - see `core/src/main/kotlin/.../temporal/TemporalDecoder.kt`.

## What's real vs. placeholder right now

| Stage | Status |
| --- | --- |
| Camera (CameraX) | Real |
| Hand tracking (MediaPipe HandLandmarker) | Real - `hand_landmarker.task` is bundled in `app/src/main/assets/` |
| Sign classifier | **Placeholder**: `HeuristicSignClassifier` recognizes 7 static hand shapes (fist, open palm, thumbs-up, pointing, peace, OK sign, shaka) via landmark geometry and maps them to demo glosses (HELLO, HELP, YES, YOU, PLEASE, GOOD, DOCTOR). No training data or trained model exists yet. `TfLiteSignClassifier` (in `:app`) is the intended real implementation - swap it in once a temporal sign model is trained and exported to `assets/sign_model.tflite`. Unit-tested with synthetic landmark fixtures per gesture. |
| Temporal decoder | Real logic (stability + pause-based phrase boundaries), unit-tested including interruption/reset and force-flush edge cases |
| Language reasoning | `MockLanguageReasoner` (default, deterministic, no model weights needed) reconstructs short sentences from the same glosses Qwen would see, following the same "don't invent facts" contract. `QwenLanguageReasoner` + `LlamaCppQwenRuntime` define the real on-device Qwen path but need a vendored llama.cpp build and a GGUF model file - see `native/README.md`. Toggle between them in Settings. Both reasoners, plus the prompt builder and JSON-validation/fallback behavior, are unit-tested with a fake `QwenRuntime`. |
| Room (conversation history / context) | Real |
| DataStore (settings) | Real |
| TTS | Real (Android `TextToSpeech`) |
| Telecom / call actions | Not implemented - out of scope for this pass; the `LanguageReasoner` -> `ResolvedUtterance` seam is where a future `InCallService` integration would hook in, downstream of everything here |

## Building

Building `:app` requires Android Studio (or a standalone Android SDK) with API 34 installed:

```
./gradlew :app:assembleDebug
```

Minimum SDK 26 (Android 8.0), target/compile SDK 34. Kotlin 1.9.24, Jetpack Compose, no Hilt
(a small manual `AppContainer` in `di/` is enough for this project's size).

**`:app` has been written carefully but not build-verified** - the sandbox this project was
built in has no Android SDK, and its network policy explicitly blocks `dl.google.com` (where
the Android Gradle Plugin and SDK components are distributed), so `:app` could not be compiled
here even by installing more tooling. Opening it in Android Studio is the first real build pass
for that module; see "Known risks" below for what to check first.

**`:core` has no such restriction and is fully built and tested in this repo already** - it's a
plain Kotlin/JVM module with no Android dependency, so it only needs Maven Central and the
Gradle Plugin Portal (both reachable everywhere `:app`'s dependencies are). See "Testing" below.

## Testing

```
./gradlew :core:test
```

This actually runs - 34 unit tests over sign classification, temporal decoding, the Qwen
prompt/JSON contract, and both language reasoners, all currently green. `gradle.properties`
sets `org.gradle.configureondemand=true` specifically so this command never needs to configure
`:app` (and therefore never touches the Android Gradle Plugin or SDK). A bare, unscoped task
like `./gradlew clean` still configures every module, including `:app` - prefix module-scoped
tasks with `:core:` (e.g. `:core:clean`) to stay Android-SDK-free.

`:app` has no automated tests to run in this environment (no SDK), but its source only adds
Android-specific glue (CameraX, MediaPipe, Room, DataStore, Compose, JNI) around the
already-tested `:core` pipeline - see "Known risks" for what to check by hand in Android Studio.

### Recommended Qwen setup (once wiring in the real backend)

- Start with **Qwen3-0.6B**, quantized (e.g. Q4_K_M GGUF); try 1.7B only if the 0.6B path is too
  weak on reconstruction quality and the device benchmark still has headroom.
- Investigate the Qualcomm AI Hub / QNN path for the exact Snapdragon SKU in the target phone
  first; fall back to llama.cpp (see `native/README.md`) if that's not practical in the time you
  have.
- Never run Qwen per-frame - only once per completed phrase, which `TemporalDecoder` already
  enforces.

## Project layout

```
core/src/main/kotlin/com/pisquarelabs/mudra/core/   Plain Kotlin/JVM, no Android dependency
  model/       Shared data classes (HandFrame, SignCandidate, ResolvedUtterance, ...)
  sign/        SignClassifier interface + HeuristicSignClassifier
  temporal/    Phrase-boundary detection (TemporalDecoder)
  reasoning/   LanguageReasoner interface, Qwen prompt/JSON contract, Mock + real backends
core/src/test/kotlin/...                            34 unit tests, all green (see Testing)

app/src/main/java/com/pisquarelabs/mudra/           Android application
  core/vision/       CameraX + MediaPipe HandLandmarker analyzer
  core/sign/         TfLiteSignClassifier (Android-only glue around :core's SignClassifier)
  core/reasoning/    LlamaCppQwenRuntime (JNI bridge around :core's QwenRuntime)
  data/db/           Room (conversation history, used as reasoning context)
  data/settings/     DataStore-backed settings
  tts/               Android TextToSpeech wrapper
  ui/                Jetpack Compose screens (camera+caption overlay, settings) and nav
  di/                Manual dependency container

native/              llama.cpp JNI bridge scaffold (not wired into the Gradle build yet)
```

## Known risks / what to verify first in Android Studio

- The MediaPipe Tasks Vision Kotlin API (`com.google.mediapipe:tasks-vision:0.10.14`) has
  shifted across versions; double-check `HandLandmarkerAnalyzer.kt` against that exact
  version's `HandLandmarker.HandLandmarkerOptions` / `ResultListener` signatures on first sync.
- `ImageProxy.toBitmap()` in `HandLandmarkerAnalyzer.kt` uses a YUV→NV21→JPEG→Bitmap round trip
  for simplicity/correctness over performance. Replace with a direct YUV→RGB conversion (or
  CameraX's `ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888`) if frame latency matters for the demo.
- The heuristic sign classifier is a placeholder vocabulary, not linguistically accurate sign
  language recognition - it exists so the full pipeline is demoable end-to-end before a trained
  model exists.
