# Wiring up on-device Qwen via llama.cpp

This directory is a scaffold, not a working build - `LlamaCppQwenRuntime` in the app depends
on it but it is not compiled by default. To make it real:

1. Vendor llama.cpp:
   ```
   git submodule add https://github.com/ggerganov/llama.cpp native/llama.cpp
   ```
2. In `CMakeLists.txt`, uncomment `add_subdirectory(llama.cpp)` and link against `llama`
   instead of just `log`/`android`.
3. Implement the three `TODO`s in `llama_bridge.cpp` using llama.cpp's public API
   (`llama_model_load_from_file`, `llama_new_context_with_model`, `llama_decode` + a sampler,
   `llama_free*`).
4. In `app/build.gradle.kts`, add:
   ```kotlin
   android {
       externalNativeBuild {
           cmake {
               path = file("../native/CMakeLists.txt")
               version = "3.22.1"
           }
       }
       defaultConfig {
           ndk { abiFilters += listOf("arm64-v8a") } // Snapdragon devices are arm64
       }
   }
   ```
5. Convert a Qwen3-0.6B or Qwen3-1.7B checkpoint to a quantized GGUF (e.g. Q4_K_M) using
   llama.cpp's `convert_hf_to_gguf.py`, then push it to app-private storage on-device
   (do not bundle multi-hundred-MB weights in the APK/assets).
6. Point `SettingsRepository`'s model path at the pushed file and switch
   `AppContainer.languageReasoner` from `MockLanguageReasoner` to
   `QwenLanguageReasoner(LlamaCppQwenRuntime(modelPath))`.

## Alternative: Qualcomm AI Hub / QNN

If the Qualcomm AI Hub path for the target Snapdragon SKU proves viable, swap step 3-6 for a
QNN-backed `QwenRuntime` implementation instead - `QwenLanguageReasoner` and everything above
it in the pipeline is unaffected either way, since both backends implement the same
`QwenRuntime` interface (see `core/reasoning/QwenRuntime.kt`).
