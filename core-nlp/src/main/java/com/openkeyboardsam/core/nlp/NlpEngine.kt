package com.openkeyboardsam.core.nlp

import android.content.Context
import java.io.File

class NlpEngine(private val context: Context) {

    private var nativeHandle: Long = 0L

    init {
        System.loadLibrary("openkeyboardsam_nlp")
        nativeHandle = nativeCreate()
    }

    fun loadLanguage(langCode: String): Boolean {
        if (nativeHandle == 0L) return false
        val dictFile = ensureDictOnDisk(langCode) ?: return false
        return nativeLoadDict(nativeHandle, dictFile.absolutePath)
    }

    fun predict(prefix: String, maxResults: Int = 3): List<String> {
        if (nativeHandle == 0L || prefix.isBlank()) return emptyList()
        return nativePredict(nativeHandle, prefix, maxResults).toList()
    }

    fun decodeSwipe(keySequence: String, maxResults: Int = 3): List<String> {
        if (nativeHandle == 0L || keySequence.length < 2) return emptyList()
        return nativeDecodeSwipe(nativeHandle, keySequence, maxResults).toList()
    }

    fun learnWord(word: String) {
        if (nativeHandle != 0L && word.length >= 2) {
            nativeLearnWord(nativeHandle, word)
        }
    }

    fun trimMemory() {
        if (nativeHandle != 0L) {
            nativeTrimMemory(nativeHandle)
        }
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    private fun ensureDictOnDisk(langCode: String): File? {
        val dir = File(context.filesDir, "dicts").apply { mkdirs() }
        val target = File(dir, "$langCode.dict")
        if (!target.exists()) {
            runCatching {
                context.assets.open("dicts/$langCode.dict").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure { return null }
        }
        return target
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeLoadDict(handle: Long, path: String): Boolean
    private external fun nativePredict(handle: Long, prefix: String, maxResults: Int): Array<String>
    private external fun nativeDecodeSwipe(handle: Long, seq: String, maxResults: Int): Array<String>
    private external fun nativeLearnWord(handle: Long, word: String)
    private external fun nativeTrimMemory(handle: Long)
}
