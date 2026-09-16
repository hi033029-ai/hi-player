// app/src/main/cpp/chromaprint_jni.cpp
//
// Thin JNI bridge: takes raw 16-bit PCM samples from Kotlin, runs them through
// libchromaprint, returns the base64-encoded fingerprint string AcoustID expects.
//
// NOTE: Chromaprint's C API (chromaprint_new / _start / _feed / _finish /
// _get_fingerprint / _free) has been stable for years, but always worth a quick
// diff against chromaprint.h from the exact version you built in
// BUILDING_CHROMAPRINT.md before relying on this compiling unmodified.

#include <jni.h>
#include <chromaprint.h>
#include <cstring>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_recognition_ChromaprintNative_fingerprint(
    JNIEnv *env,
    jobject /* this */,
    jshortArray pcmData,
    jint sampleRate,
    jint numChannels) {

    if (pcmData == nullptr || sampleRate <= 0 || numChannels <= 0) {
        return env->NewStringUTF("");
    }
    jsize numSamples = env->GetArrayLength(pcmData);
    if (numSamples <= 0) {
        return env->NewStringUTF("");
    }
    jshort *samples = env->GetShortArrayElements(pcmData, nullptr);
    if (samples == nullptr) {
        return env->NewStringUTF("");
    }

    ChromaprintContext *ctx = chromaprint_new(CHROMAPRINT_ALGORITHM_DEFAULT);
    if (ctx == nullptr || chromaprint_start(ctx, sampleRate, numChannels) != 1) {
        if (ctx != nullptr) chromaprint_free(ctx);
        env->ReleaseShortArrayElements(pcmData, samples, JNI_ABORT);
        return env->NewStringUTF("");
    }

    // chromaprint_feed expects int16_t* — jshort is already 16-bit, safe direct cast.
    if (chromaprint_feed(ctx, reinterpret_cast<const int16_t *>(samples), numSamples) != 1 ||
        chromaprint_finish(ctx) != 1) {
        chromaprint_free(ctx);
        env->ReleaseShortArrayElements(pcmData, samples, JNI_ABORT);
        return env->NewStringUTF("");
    }

    char *fingerprint = nullptr;
    jstring result = nullptr;

    if (chromaprint_get_fingerprint(ctx, &fingerprint) == 1 && fingerprint != nullptr) {
        result = env->NewStringUTF(fingerprint);
        chromaprint_dealloc(fingerprint);
    }

    chromaprint_free(ctx);
    env->ReleaseShortArrayElements(pcmData, samples, JNI_ABORT);

    return result != nullptr ? result : env->NewStringUTF("");
}
