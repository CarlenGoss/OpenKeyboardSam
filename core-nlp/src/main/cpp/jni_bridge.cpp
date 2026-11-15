#include <jni.h>
#include <string>
#include "nlp_engine.h"

using openkeyboardsam::nlp::NlpEngine;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_openkeyboardsam_core_nlp_NlpEngine_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new NlpEngine());
}

JNIEXPORT void JNICALL
Java_com_openkeyboardsam_core_nlp_NlpEngine_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<NlpEngine*>(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_openkeyboardsam_core_nlp_NlpEngine_nativeLoadDict(
    JNIEnv* env, jobject, jlong handle, jstring path) {
    auto* engine = reinterpret_cast<NlpEngine*>(handle);
    if (!engine || !path) return JNI_FALSE;
    const char* cPath = env->GetStringUTFChars(path, nullptr);
    bool ok = engine->loadDictionaryFile(cPath);
    env->ReleaseStringUTFChars(path, cPath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_openkeyboardsam_core_nlp_NlpEngine_nativePredict(
    JNIEnv* env, jobject, jlong handle, jstring prefix, jint maxResults) {
    auto* engine = reinterpret_cast<NlpEngine*>(handle);
    jclass strClass = env->FindClass("java/lang/String");
    if (!engine || !prefix) return env->NewObjectArray(0, strClass, nullptr);

    const char* cPrefix = env->GetStringUTFChars(prefix, nullptr);
    auto results = engine->predict(cPrefix, static_cast<size_t>(maxResults));
    env->ReleaseStringUTFChars(prefix, cPrefix);

    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(results.size()), strClass, nullptr);
    for (size_t i = 0; i < results.size(); ++i) {
        jstring s = env->NewStringUTF(results[i].word.c_str());
        env->SetObjectArrayElement(arr, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    return arr;
}

JNIEXPORT jobjectArray JNICALL
Java_com_openkeyboardsam_core_nlp_NlpEngine_nativeDecodeSwipe(
    JNIEnv* env, jobject, jlong handle, jstring seq, jint maxResults) {
    auto* engine = reinterpret_cast<NlpEngine*>(handle);
    jclass strClass = env->FindClass("java/lang/String");
    if (!engine || !seq) return env->NewObjectArray(0, strClass, nullptr);

    const char* cSeq = env->GetStringUTFChars(seq, nullptr);
    auto results = engine->decodeSwipe(cSeq, static_cast<size_t>(maxResults));
    env->ReleaseStringUTFChars(seq, cSeq);

    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(results.size()), strClass, nullptr);
    for (size_t i = 0; i < results.size(); ++i) {
        jstring s = env->NewStringUTF(results[i].word.c_str());
        env->SetObjectArrayElement(arr, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    return arr;
}

JNIEXPORT void JNICALL
Java_com_openkeyboardsam_core_nlp_NlpEngine_nativeLearnWord(
    JNIEnv* env, jobject, jlong handle, jstring word) {
    auto* engine = reinterpret_cast<NlpEngine*>(handle);
    if (!engine || !word) return;
    const char* cWord = env->GetStringUTFChars(word, nullptr);
    engine->learnWord(cWord);
    env->ReleaseStringUTFChars(word, cWord);
}

JNIEXPORT void JNICALL
Java_com_openkeyboardsam_core_nlp_NlpEngine_nativeTrimMemory(
    JNIEnv*, jobject, jlong handle) {
    auto* engine = reinterpret_cast<NlpEngine*>(handle);
    if (engine) engine->trimMemory();
}

}
