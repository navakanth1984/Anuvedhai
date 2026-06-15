package com.example.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

interface SarvamApiService {
    @POST("translation")
    suspend fun translate(
        @Header("api-subscription-key") apiKey: String,
        @Body request: SarvamTranslateRequest
    ): SarvamTranslateResponse

    @POST("text-to-speech")
    suspend fun textToSpeech(
        @Header("api-subscription-key") apiKey: String,
        @Body request: SarvamTtsRequest
    ): SarvamTtsResponse

    @Multipart
    @POST("speech-to-text")
    suspend fun speechToText(
        @Header("api-subscription-key") apiKey: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("language_code") languageCode: RequestBody
    ): SarvamAsrResponse

    @POST("text-lang-id")
    suspend fun detectLanguage(
        @Header("api-subscription-key") apiKey: String,
        @Body request: SarvamLangIdRequest
    ): SarvamLangIdResponse
}

object NetworkClient {
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val geminiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }

    val sarvamService: SarvamApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.sarvam.ai/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SarvamApiService::class.java)
    }
}
