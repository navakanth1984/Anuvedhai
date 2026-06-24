package com.example.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import org.json.JSONObject

class TranslationRepository(
    private val context: Context,
    private val database: TranslationDatabase
) {
    private val dao = database.translationDao()

    fun getAllConversations(): Flow<List<Conversation>> = dao.getAllConversations()

    fun getTurns(conversationId: Long): Flow<List<TranslationTurn>> =
        dao.getTurnsForConversation(conversationId)

    suspend fun createConversation(title: String): Long {
        return dao.insertConversation(Conversation(title = title))
    }

    suspend fun deleteConversation(id: Long) {
        dao.deleteConversation(Conversation(id = id, title = ""))
    }

    suspend fun insertTurn(turn: TranslationTurn) {
        dao.insertTurn(turn)
    }

    suspend fun clearHistory(conversationId: Long) {
        dao.clearTurnsForConversation(conversationId)
    }

    // Generate synthesized audio bytes via Sarvam AI
    suspend fun getSarvamTtsAudio(text: String, langCode: String, apiKey: String, speakerGender: String): String? {
        if (apiKey.isBlank() || apiKey == "YOUR_SARVAM_API_KEY") return null
        return try {
            val req = SarvamTtsRequest(
                inputs = listOf(text),
                target_language_code = langCode,
                speaker_gender = speakerGender
            )
            val res = NetworkClient.sarvamService.textToSpeech(apiKey, req)
            res.audios.firstOrNull()
        } catch (e: Exception) {
            Log.e("TranslationRepo", "Sarvam TTS synthesis failed", e)
            null
        }
    }

    // Build previous conversation context for Gemini translation turns
    private suspend fun buildContextText(conversationId: Long): String {
        val turns = dao.getTurnsForConversationSync(conversationId)
        val sb = StringBuilder()
        for (t in turns.takeLast(8)) { // optimal 8 context turns to preserve buffer limits
            val typeStr = if (t.inputType == "TEXT") "" else " (${t.inputType} input)"
            sb.append("Source (${t.sourceLanguageName}$typeStr): ${t.originalContent}\n")
            sb.append("Translation in ${t.targetLanguageName}: ${t.translatedText}\n\n")
        }
        return sb.toString()
    }

    suspend fun processTranslation(
        conversationId: Long,
        inputType: String,
        file: File?,
        typedText: String,
        sourceLangCode: String,
        sourceLangName: String,
        targetLangCode: String,
        targetLangName: String,
        useSarvam: Boolean,
        sarvamApiKey: String
    ): TranslationTurn {
        val startTime = System.currentTimeMillis()
        val contextText = buildContextText(conversationId)
        var originalText = typedText
        var translatedOutput = ""
        var sentimentValue: String? = null

        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        val actualSarvamKey = sarvamApiKey.ifBlank { BuildConfig.SARVAM_API_KEY }

        var actualSourceCode = sourceLangCode
        var actualSourceName = sourceLangName

        // Handle auto-detect language before translation
        if (actualSourceCode == "auto") {
            if (inputType == "TEXT" && typedText.isNotBlank()) {
                // Try Sarvam text-lang-id API if useSarvam is true
                if (useSarvam && actualSarvamKey.isNotBlank() && actualSarvamKey != "YOUR_SARVAM_API_KEY") {
                    try {
                        val detectReq = SarvamLangIdRequest(input = typedText)
                        val detectRes = NetworkClient.sarvamService.detectLanguage(actualSarvamKey, detectReq)
                        val topLang = detectRes.language_to_confidence_map.maxByOrNull { it.value }?.key
                        if (topLang != null) {
                            val matched = mapDetectedCodeToLocalCodes(topLang)
                            if (matched != null) {
                                actualSourceCode = matched.second
                                actualSourceName = "${matched.first} (Auto-Detected)"
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TranslationRepo", "Sarvam auto-detect language failed, trying Gemini", e)
                    }
                }
                // If still auto (Sarvam skipped or failed), fallback to Gemini language detection
                if (actualSourceCode == "auto") {
                    val geminiDetected = detectLanguageViaGemini(typedText, geminiApiKey)
                    if (geminiDetected != null) {
                        actualSourceCode = geminiDetected.second
                        actualSourceName = "${geminiDetected.first} (Auto-Detected)"
                    } else {
                        actualSourceCode = "hi-IN"
                        actualSourceName = "Hindi"
                    }
                }
            } else if (inputType == "AUDIO") {
                // Sarvam's Speech-To-Text API requires a fixed language_code and can't use "auto".
                // We utilize Gemini's multimodal audio API to transcribe, translate and auto-detect the spoken language simultaneously.
                val geminiRes = processViaGemini(
                    contextText = contextText,
                    inputType = "AUDIO",
                    file = file,
                    typedText = typedText,
                    sourceLangCode = "auto",
                    sourceLangName = "Auto-Detect",
                    targetLangCode = targetLangCode,
                    targetLangName = targetLangName,
                    apiKey = geminiApiKey
                )
                originalText = geminiRes.originalContent
                translatedOutput = geminiRes.translatedText
                val detected = geminiRes.detectedLanguageCode?.let { mapDetectedCodeToLocalCodes(it) }
                if (detected != null) {
                    actualSourceCode = detected.second
                    actualSourceName = "${detected.first} (Auto-Detected)"
                } else {
                    actualSourceCode = "hi-IN"
                    actualSourceName = "Hindi"
                }

                val durationMs = System.currentTimeMillis() - startTime
                val turn = TranslationTurn(
                    conversationId = conversationId,
                    inputType = inputType,
                    mediaPath = file?.absolutePath,
                    sourceLanguageName = actualSourceName,
                    sourceLanguageCode = actualSourceCode,
                    targetLanguageName = targetLangName,
                    targetLanguageCode = targetLangCode,
                    originalContent = originalText,
                    translatedText = translatedOutput,
                    isFromUser = true,
                    durationMs = durationMs,
                    sentiment = geminiRes.sentiment
                )
                dao.insertTurn(turn)
                return turn
            }
        }

        // Standard Translation Route (with now-resolved actualSourceCode and actualSourceName)
        if (inputType == "TEXT" && typedText.isNotBlank()) {
            try {
                val cached = dao.getCachedTranslation(typedText.trim(), actualSourceCode, targetLangCode)
                if (cached != null) {
                    val durationMs = System.currentTimeMillis() - startTime
                    val turn = TranslationTurn(
                        conversationId = conversationId,
                        inputType = "TEXT",
                        mediaPath = null,
                        sourceLanguageName = actualSourceName,
                        sourceLanguageCode = actualSourceCode,
                        targetLanguageName = targetLangName,
                        targetLanguageCode = targetLangCode,
                        originalContent = typedText,
                        translatedText = cached.translatedText,
                        isFromUser = true,
                        durationMs = durationMs,
                        sentiment = cached.sentiment
                    )
                    dao.insertTurn(turn)
                    return turn
                }
            } catch (e: Exception) {
                Log.e("TranslationRepo", "Error reading translation cache", e)
            }
        }

        if (useSarvam && actualSarvamKey.isNotBlank() && actualSarvamKey != "YOUR_SARVAM_API_KEY") {
            try {
                if (inputType == "TEXT") {
                    val req = SarvamTranslateRequest(
                        input = typedText,
                        source_language_code = actualSourceCode,
                        target_language_code = targetLangCode
                    )
                    val res = NetworkClient.sarvamService.translate(actualSarvamKey, req)
                    translatedOutput = res.translated_text
                    sentimentValue = detectSentimentViaGemini(typedText, geminiApiKey)
                } else if (inputType == "AUDIO" && file != null && file.exists()) {
                    // 1. Voice transcript transcription using Sarvam ASR
                    val fileBody = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                    val filePart = MultipartBody.Part.createFormData("file", file.name, fileBody)
                    val modelPart = "saaras_v1".toRequestBody("text/plain".toMediaTypeOrNull())
                    val langPart = actualSourceCode.toRequestBody("text/plain".toMediaTypeOrNull())

                    val asrRes = NetworkClient.sarvamService.speechToText(
                        apiKey = actualSarvamKey,
                        file = filePart,
                        model = modelPart,
                        languageCode = langPart
                    )
                    originalText = asrRes.transcript

                    // Check cache for speech-to-text transcript
                    val cached = dao.getCachedTranslation(originalText.trim(), actualSourceCode, targetLangCode)
                    if (cached != null) {
                        translatedOutput = cached.translatedText
                        sentimentValue = cached.sentiment
                    } else {
                        // 2. Translate transcribed text using Sarvam Translate
                        val req = SarvamTranslateRequest(
                            input = originalText,
                            source_language_code = actualSourceCode,
                            target_language_code = targetLangCode
                        )
                        val res = NetworkClient.sarvamService.translate(actualSarvamKey, req)
                        translatedOutput = res.translated_text
                        sentimentValue = detectSentimentViaGemini(originalText, geminiApiKey)
                    }
                } else {
                    // Vision & Video inputs are only supported via Gemini Vision
                    val geminiRes = processViaGemini(
                        contextText = contextText,
                        inputType = inputType,
                        file = file,
                        typedText = typedText,
                        sourceLangCode = actualSourceCode,
                        sourceLangName = actualSourceName,
                        targetLangCode = targetLangCode,
                        targetLangName = targetLangName,
                        apiKey = geminiApiKey
                    )
                    originalText = geminiRes.originalContent
                    translatedOutput = geminiRes.translatedText
                    sentimentValue = geminiRes.sentiment
                }
            } catch (e: Exception) {
                Log.e("TranslationRepo", "Sarvam service threw error, executing backup Gemini routing", e)
                try {
                    val geminiRes = processViaGemini(
                        contextText = contextText,
                        inputType = inputType,
                        file = file,
                        typedText = typedText,
                        sourceLangCode = actualSourceCode,
                        sourceLangName = actualSourceName,
                        targetLangCode = targetLangCode,
                        targetLangName = targetLangName,
                        apiKey = geminiApiKey
                    )
                    originalText = if (originalText.isBlank()) geminiRes.originalContent else originalText
                    translatedOutput = geminiRes.translatedText
                    sentimentValue = geminiRes.sentiment
                } catch (gemException: Exception) {
                    Log.e("TranslationRepo", "Gemini backup failed, checking cache fallback", gemException)
                    val searchKey = if (inputType == "TEXT") typedText else originalText
                    val cached = dao.getCachedTranslation(searchKey.trim(), actualSourceCode, targetLangCode)
                    if (cached != null) {
                        originalText = searchKey
                        translatedOutput = cached.translatedText
                        sentimentValue = cached.sentiment
                    } else {
                        throw gemException
                    }
                }
            }
        } else {
            // Process directly using Gemini Multimodal Models with multi-turn context
            try {
                val geminiRes = processViaGemini(
                    contextText = contextText,
                    inputType = inputType,
                    file = file,
                    typedText = typedText,
                    sourceLangCode = actualSourceCode,
                    sourceLangName = actualSourceName,
                    targetLangCode = targetLangCode,
                    targetLangName = targetLangName,
                    apiKey = geminiApiKey
                )
                originalText = geminiRes.originalContent
                translatedOutput = geminiRes.translatedText
                sentimentValue = geminiRes.sentiment
                
                // Extract detected language if returned
                val detected = geminiRes.detectedLanguageCode?.let { mapDetectedCodeToLocalCodes(it) }
                if (detected != null && actualSourceCode == "auto") {
                    actualSourceCode = detected.second
                    actualSourceName = "${detected.first} (Auto-Detected)"
                }
            } catch (gemException: Exception) {
                Log.e("TranslationRepo", "Direct Gemini call failed, checking cache fallback", gemException)
                val searchKey = if (inputType == "TEXT") typedText else originalText
                val cached = dao.getCachedTranslation(searchKey.trim(), actualSourceCode, targetLangCode)
                if (cached != null) {
                    originalText = searchKey
                    translatedOutput = cached.translatedText
                    sentimentValue = cached.sentiment
                } else {
                    throw gemException
                }
            }
        }

        // Cache the successful translation pair for future offline / poor network speedups
        if (originalText.isNotBlank() && translatedOutput.isNotBlank() && (inputType == "TEXT" || inputType == "AUDIO")) {
            try {
                dao.insertCachedTranslation(
                    CachedTranslation(
                        originalText = originalText.trim(),
                        sourceLangCode = actualSourceCode,
                        targetLangCode = targetLangCode,
                        translatedText = translatedOutput,
                        sentiment = sentimentValue
                    )
                )
            } catch (e: Exception) {
                Log.e("TranslationRepo", "Failed to cache translation pair", e)
            }
        }

        val durationMs = System.currentTimeMillis() - startTime
        val turn = TranslationTurn(
            conversationId = conversationId,
            inputType = inputType,
            mediaPath = file?.absolutePath,
            sourceLanguageName = actualSourceName,
            sourceLanguageCode = actualSourceCode,
            targetLanguageName = targetLangName,
            targetLanguageCode = targetLangCode,
            originalContent = originalText,
            translatedText = translatedOutput,
            isFromUser = true,
            durationMs = durationMs,
            sentiment = sentimentValue
        )
        dao.insertTurn(turn)
        return turn
    }

    private suspend fun processViaGemini(
        contextText: String,
        inputType: String,
        file: File?,
        typedText: String,
        sourceLangCode: String,
        sourceLangName: String,
        targetLangCode: String,
        targetLangName: String,
        apiKey: String
    ): GeminiResult {
        if (apiKey.isBlank() || false /* apiKey == "MY_GEMINI_API_KEY" */) {
            return GeminiResult(
                originalContent = typedText.ifBlank { "Unprocessed multimedia input" },
                translatedText = "Missing active Gemini API configuration. Please configure GEMINI_API_KEY in the AI Studio environment, or insert a Sarvam API Key."
            )
        }

        // Selected models based on capacity requirements
        val model = if (inputType == "VIDEO" || inputType == "IMAGE") {
            "gemini-3.1-pro-preview"
        } else {
            "gemini-3.5-flash"
        }

        val taskPrompt = when (inputType) {
            "TEXT" -> "Translate this text message from $sourceLangName to $targetLangName. Respond in the correct linguistic script and natural syntax style."
            "AUDIO" -> "Analyze the attached voice speech. Transcribe what is spoken accurately, and then translate the transcript to $targetLangName."
            "IMAGE" -> "Extract words (OCR) or describe elements of this photo, then translate the resulting extraction into $targetLangName."
            "VIDEO" -> "Review this video clip. Transcribe conversation or summarize visual steps happening, and translate that info into $targetLangName."
            else -> "Translate the input content to $targetLangName."
        }

        val systemInstruction = """
            You are a translation assistant specializing in Indian Subcontinent dialects and English.
            You must output your reply strictly as a single JSON object. Do not include any markdown backticks (like ```json), labels, or preambles. Output only the raw validated JSON.
            The JSON object structure must possess exactly these keys:
            1. "original": Retain or transcribe the source dialect text representing exactly the recorded speech, typed text, or OCR contents.
            2. "translated": Translate the statement to $targetLangName. Ensure it reads naturally for a native speaker.
            ${if (sourceLangCode == "auto") "3. \"detected_language_code\": Detect the original language of the input, mapping it strictly to one of these: [\"en-IN\", \"hi-IN\", \"bn-IN\", \"ta-IN\", \"te-IN\", \"kn-IN\", \"ml-IN\", \"mr-IN\", \"gu-IN\", \"pa-IN\"]." else ""}
            4. "sentiment": Detect the mood or sentiment of the statement. Respond with exactly one of these lowercase strings: "happy", "serious", "urgent", "peaceful", "sad", "angry", "neutral".

            IMPORTANT context: A dialogue is taking place. Keep the preceding dialogue history in mind to translate pronouns (like "it", "he", "you") or style shifts properly.
            Here is the historic dialogue background context for reference:
            $contextText
        """.trimIndent()

        val parts = mutableListOf<GeminiPart>()
        if (inputType == "TEXT") {
            parts.add(GeminiPart(text = "Prompt input: $typedText\n\n$taskPrompt"))
        } else if (file != null && file.exists()) {
            val contentBytes = file.readBytes()
            val base64Payload = Base64.encodeToString(contentBytes, Base64.NO_WRAP)
            val mimeType = when (inputType) {
                "AUDIO" -> "audio/mp4"
                "IMAGE" -> "image/jpeg"
                "VIDEO" -> "video/mp4"
                else -> "application/octet-stream"
            }
            parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = mimeType, data = base64Payload)))
            parts.add(GeminiPart(text = "Task query: $taskPrompt"))
        } else {
            parts.add(GeminiPart(text = "Prompt input: $typedText\n\n$taskPrompt"))
        }

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = parts)),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstruction))),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.2f,
                responseMimeType = "application/json"
            )
        )

        val response = NetworkClient.geminiService.generateContent(model, apiKey, request)
        val responseBody = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No candidates received from Gemini")

        // Parse JSON output safely
        val trimmedBody = responseBody.trim().removeSurrounding("```json", "```").trim()
        return try {
            val obj = JSONObject(trimmedBody)
            GeminiResult(
                originalContent = obj.optString("original", typedText).ifBlank { typedText },
                translatedText = obj.optString("translated", "Translation failed"),
                detectedLanguageCode = obj.optString("detected_language_code", null),
                sentiment = obj.optString("sentiment", null)
            )
        } catch (e: Exception) {
            Log.e("TranslationRepo", "Failed to parse JSON result logic: $trimmedBody", e)
            GeminiResult(
                originalContent = typedText.ifBlank { "Raw transcription" },
                translatedText = trimmedBody
            )
        }
    }

    private fun mapDetectedCodeToLocalCodes(rawCode: String): Pair<String, String>? {
        val normalized = rawCode.lowercase().replace("_", "-").split("-").firstOrNull() ?: ""
        val mapping = mapOf(
            "en" to Pair("English", "en-IN"),
            "hi" to Pair("Hindi", "hi-IN"),
            "bn" to Pair("Bengali", "bn-IN"),
            "ta" to Pair("Tamil", "ta-IN"),
            "te" to Pair("Telugu", "te-IN"),
            "kn" to Pair("Kannada", "kn-IN"),
            "ml" to Pair("Malayalam", "ml-IN"),
            "mr" to Pair("Marathi", "mr-IN"),
            "gu" to Pair("Gujarati", "gu-IN"),
            "pa" to Pair("Punjabi", "pa-IN")
        )
        return mapping[normalized] ?: mapping.values.firstOrNull { 
            it.second.lowercase() == rawCode.lowercase() 
        }
    }

    private suspend fun detectLanguageViaGemini(text: String, apiKey: String): Pair<String, String>? {
        if (apiKey.isBlank()) return null
        try {
            val systemInstruction = """
                You are a language identification agent.
                Identify which of the following Indian languages (or English) the user's text is written or transliterated in:
                - en-IN (English)
                - hi-IN (Hindi)
                - bn-IN (Bengali)
                - ta-IN (Tamil)
                - te-IN (Telugu)
                - kn-IN (Kannada)
                - ml-IN (Malayalam)
                - mr-IN (Marathi)
                - gu-IN (Gujarati)
                - pa-IN (Punjabi)

                Respond strictly in a single JSON object. Do not include markdown blocks. Output only raw validated JSON.
                The JSON object structure must possess exactly one key:
                "language_code": One of the values from: ["en-IN", "hi-IN", "bn-IN", "ta-IN", "te-IN", "kn-IN", "ml-IN", "mr-IN", "gu-IN", "pa-IN"].
            """.trimIndent()

            val parts = listOf(GeminiPart(text = "Analyze this text: \"$text\""))
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = parts)),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstruction))),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1f,
                    responseMimeType = "application/json"
                )
            )

            val response = NetworkClient.geminiService.generateContent("gemini-3.5-flash", apiKey, request)
            val responseBody = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return null
            val trimmedBody = responseBody.trim().removeSurrounding("```json", "```").trim()
            val obj = JSONObject(trimmedBody)
            val code = obj.optString("language_code").trim()
            
            val mapping = mapOf(
                "en-IN" to Pair("English", "en-IN"),
                "hi-IN" to Pair("Hindi", "hi-IN"),
                "bn-IN" to Pair("Bengali", "bn-IN"),
                "ta-IN" to Pair("Tamil", "ta-IN"),
                "te-IN" to Pair("Telugu", "te-IN"),
                "kn-IN" to Pair("Kannada", "kn-IN"),
                "ml-IN" to Pair("Malayalam", "ml-IN"),
                "mr-IN" to Pair("Marathi", "mr-IN"),
                "gu-IN" to Pair("Gujarati", "gu-IN"),
                "pa-IN" to Pair("Punjabi", "pa-IN")
            )
            return mapping[code] ?: mapDetectedCodeToLocalCodes(code)
        } catch (e: Exception) {
            Log.e("TranslationRepo", "Gemini language detect failed", e)
            return null
        }
    }

    private suspend fun detectSentimentViaGemini(text: String, apiKey: String): String? {
        if (apiKey.isBlank() || text.isBlank() || false /* apiKey == "MY_GEMINI_API_KEY" */) return null
        return try {
            val systemInstruction = """
                Analyze the emotional sentiment of the following message.
                Classify it into exactly one of these moods: "happy", "serious", "urgent", "peaceful", "sad", "angry", "neutral".
                Respond strictly with a single JSON object having one key:
                "sentiment": "<one of the allowed mood strings>"
                No markdown formatting. Output only validated JSON.
            """.trimIndent()

            val parts = listOf(GeminiPart(text = "Analyze this statement: \"$text\""))
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = parts)),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstruction))),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1f,
                    responseMimeType = "application/json"
                )
            )

            val response = NetworkClient.geminiService.generateContent("gemini-3.5-flash", apiKey, request)
            val responseBody = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return null
            val trimmedBody = responseBody.trim().removeSurrounding("```json", "```").trim()
            val obj = JSONObject(trimmedBody)
            obj.optString("sentiment").trim().lowercase()
        } catch (e: Exception) {
            Log.e("TranslationRepo", "Gemini sentiment analysis failed", e)
            null
        }
    }

    data class GeminiResult(val originalContent: String, val translatedText: String, val detectedLanguageCode: String? = null, val sentiment: String? = null)
}

