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
MediaPipe Hand Landmarker      (core/vision)
  |
Hand landmarks
  |
Custom sign model              (core/sign)
  |
Top-K sign candidates
  |
Temporal decoder               (core/temporal)
  |
Qwen (language reasoner)       (core/reasoning)
  |
Natural-language reconstruction / intent
  |
TTS / captions                 (tts, ui/caption)
```

Qwen never sees frames or images - only structured JSON evidence (glosses + confidences +
prior turn), and it is never given any tool or action surface (no telecom, contacts, SMS,
accessibility APIs). Its entire job is "return this JSON shape or be ignored." See
`core/reasoning/QwenPromptBuilder.kt` for the exact prompt and
`core/reasoning/QwenLanguageReasoner.kt` for how its output is validated.

The temporal decoder also means Qwen runs once per completed phrase (after a signing pause),
not per camera frame - see `core/temporal/TemporalDecoder.kt`.

## What's real vs. placeholder right now

| Stage | Status |
| --- | --- |
| Camera (CameraX) | Real |
| Hand tracking (MediaPipe HandLandmarker) | Real - `hand_landmarker.task` is bundled in `app/src/main/assets/` |
| Sign classifier | **Placeholder**: `HeuristicSignClassifier` recognizes 7 static hand shapes (fist, open palm, thumbs-up, pointing, peace, OK sign, shaka) via landmark geometry and maps them to demo glosses (HELLO, HELP, YES, YOU, PLEASE, GOOD, DOCTOR). No training data or trained model exists yet. `TfLiteSignClassifier` is the intended real implementation - swap it in once a temporal sign model is trained and exported to `assets/sign_model.tflite`. |
| Temporal decoder | Real logic (stability + pause-based phrase boundaries), unit-tested in `app/src/test/.../TemporalDecoderTest.kt` |
| Language reasoning | `MockLanguageReasoner` (default, deterministic, no model weights needed) reconstructs short sentences from the same glosses Qwen would see, following the same "don't invent facts" contract. `QwenLanguageReasoner` + `LlamaCppQwenRuntime` define the real on-device Qwen path but need a vendored llama.cpp build and a GGUF model file - see `native/README.md`. Toggle between them in Settings. |
| Room (conversation history / context) | Real |
| DataStore (settings) | Real |
| TTS | Real (Android `TextToSpeech`) |
| Telecom / call actions | Not implemented - out of scope for this pass; the `LanguageReasoner` -> `ResolvedUtterance` seam is where a future `InCallService` integration would hook in, downstream of everything here |

## Building

Requires Android Studio (or a standalone Android SDK) with API 34 installed - **this
environment does not have the Android SDK and could not run a real Gradle build**, so this
project has been written carefully but not compiled. Open it in Android Studio and let it sync;
that's the first real build/verification pass.

```
./gradlew assembleDebug
```

Minimum SDK 26 (Android 8.0), target/compile SDK 34. Kotlin 1.9.24, Jetpack Compose, no Hilt
(a small manual `AppContainer` in `di/` is enough for this project's size).

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
app/src/main/java/com/pisquarelabs/mudra/
  core/model/       Shared data classes (HandFrame, SignCandidate, ResolvedUtterance, ...)
  core/vision/       CameraX + MediaPipe HandLandmarker analyzer
  core/sign/         SignClassifier interface + heuristic/TFLite implementations
  core/temporal/     Phrase-boundary detection
  core/reasoning/     LanguageReasoner interface, Qwen prompt/JSON contract, Mock + real backends
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
