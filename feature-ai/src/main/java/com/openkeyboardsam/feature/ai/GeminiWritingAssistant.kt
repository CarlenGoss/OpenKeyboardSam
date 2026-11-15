package com.openkeyboardsam.feature.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

enum class WritingAction(val promptInstruction: String) {
    FIX_GRAMMAR("Fix spelling and grammar in the same language. Return only the corrected text without explanations:"),
    TONE_PROFESSIONAL("Rewrite in a polite, professional tone in the same language. Return only the rewritten text:"),
    TONE_CASUAL("Rewrite in a friendly, casual tone in the same language. Return only the rewritten text:"),
    TONE_SOCIAL("Rewrite for social media with engaging phrasing and 1-2 relevant emojis. Return only the text:"),
    TRANSLATE_TO_EN("Translate accurately to English. Return only the translated text:"),
    TRANSLATE_TO_ES("Translate accurately to Spanish. Return only the translated text:")
}

sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Error(val message: String) : AiResult()
}

/**
 * Zero-bloat Gemini REST client for Writing Assist & Real-time Translation.
 * Uses user-provided API key (BYOK) and standard HttpURLConnection to avoid heavy SDK RAM overhead.
 */
class GeminiWritingAssistant(
    private val modelName: String = "gemini-2.5-flash"
) {

    suspend fun transformText(
        apiKey: String,
        inputText: String,
        action: WritingAction
    ): AiResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AiResult.Error("Configura tu Gemini API Key en Ajustes de OpenKeyboardSam.")
        }
        if (inputText.isBlank()) {
            return@withContext AiResult.Error("No hay texto para procesar.")
        }

        var conn: HttpURLConnection? = null
        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", "${action.promptInstruction}\n\n$inputText")
                    }))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 512)
                })
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

            if (code !in 200..299) {
                return@withContext AiResult.Error("Error Gemini ($code)")
            }

            val json = JSONObject(body)
            val text = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()

            if (text.isNullOrEmpty()) {
                AiResult.Error("Respuesta vacía de Gemini.")
            } else {
                AiResult.Success(text)
            }
        } catch (e: Exception) {
            AiResult.Error(e.localizedMessage ?: "Error de conexión")
        } finally {
            conn?.disconnect()
        }
    }
}
