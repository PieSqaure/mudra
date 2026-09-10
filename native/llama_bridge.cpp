// JNI bridge for com.pisquarelabs.mudra.core.reasoning.LlamaCppQwenRuntime.
//
// This is a stub: it defines the correct JNI signatures for the Kotlin `external fun`
// declarations so the shape of the integration is clear, but the actual llama.cpp calls
// are TODOs until llama.cpp is vendored (see README.md in this directory).

#include <jni.h>
#include <string>

extern "C" JNIEXPORT jlong JNICALL
Java_com_pisquarelabs_mudra_core_reasoning_LlamaCppQwenRuntime_nativeLoadModel(
        JNIEnv *env, jobject /* this */, jstring modelPath) {
    // TODO: llama_model_load_from_file(...) + llama_new_context_with_model(...)
    // Return the heap-allocated context pointer cast to jlong, or 0 on failure.
    (void) env;
    (void) modelPath;
    return 0L;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pisquarelabs_mudra_core_reasoning_LlamaCppQwenRuntime_nativeGenerate(
        JNIEnv *env, jobject /* this */, jlong handle, jstring prompt, jint maxTokens) {
    // TODO: tokenize `prompt`, run llama_decode in a sampling loop up to `maxTokens`,
    // detokenize, and return the generated text.
    (void) handle;
    (void) prompt;
    (void) maxTokens;
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT void JNICALL
Java_com_pisquarelabs_mudra_core_reasoning_LlamaCppQwenRuntime_nativeFreeModel(
        JNIEnv *env, jobject /* this */, jlong handle) {
    // TODO: llama_free(ctx); llama_free_model(model);
    (void) env;
    (void) handle;
}
