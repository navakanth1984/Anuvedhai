package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ==========================================
// GEMINI API MODELS
// ==========================================

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    val mimeType: String,
    val data: String // Base64 encoded payload
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent?
)

// ==========================================
// SARVAM AI API MODELS
// ==========================================

@JsonClass(generateAdapter = true)
data class SarvamTranslateRequest(
    val input: String,
    val source_language_code: String,
    val target_language_code: String,
    val speaker_gender: String = "Male",
    val mode: String = "formal"
)

@JsonClass(generateAdapter = true)
data class SarvamTranslateResponse(
    val translated_text: String
)

@JsonClass(generateAdapter = true)
data class SarvamTtsRequest(
    val inputs: List<String>,
    val target_language_code: String,
    val speaker_gender: String = "Male"
)

@JsonClass(generateAdapter = true)
data class SarvamTtsResponse(
    val audios: List<String> // base64 string audio files
)

@JsonClass(generateAdapter = true)
data class SarvamAsrResponse(
    val transcript: String
)

@JsonClass(generateAdapter = true)
data class SarvamLangIdRequest(
    val input: String
)

@JsonClass(generateAdapter = true)
data class SarvamLangIdResponse(
    val language_to_confidence_map: Map<String, Double>
)

