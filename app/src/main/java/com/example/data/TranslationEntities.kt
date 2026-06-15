package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "translation_turns",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class TranslationTurn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val inputType: String, // "TEXT", "AUDIO", "IMAGE", "VIDEO"
    val mediaPath: String? = null, // URI or path of recorded audio / photo / video
    val sourceLanguageName: String,
    val sourceLanguageCode: String,
    val targetLanguageName: String,
    val targetLanguageCode: String,
    val originalContent: String, // Typed text, OCR text from image, or Speech-to-Text transcript
    val translatedText: String, // Final translated target text
    val isFromUser: Boolean = true,
    val durationMs: Long = 0L,
    val sentiment: String? = null
)

@Entity(
    tableName = "cached_translations",
    primaryKeys = ["originalText", "sourceLangCode", "targetLangCode"]
)
data class CachedTranslation(
    val originalText: String,
    val sourceLangCode: String,
    val targetLangCode: String,
    val translatedText: String,
    val sentiment: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)
