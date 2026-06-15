package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation): Long

    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Delete
    suspend fun deleteConversation(conversation: Conversation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurn(turn: TranslationTurn): Long

    @Query("SELECT * FROM translation_turns WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getTurnsForConversation(conversationId: Long): Flow<List<TranslationTurn>>

    @Query("SELECT * FROM translation_turns WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getTurnsForConversationSync(conversationId: Long): List<TranslationTurn>

    @Query("DELETE FROM translation_turns WHERE conversationId = :conversationId")
    suspend fun clearTurnsForConversation(conversationId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTranslation(cachedTranslation: CachedTranslation)

    @Query("SELECT * FROM cached_translations WHERE originalText = :originalText AND sourceLangCode = :sourceLangCode AND targetLangCode = :targetLangCode LIMIT 1")
    suspend fun getCachedTranslation(originalText: String, sourceLangCode: String, targetLangCode: String): CachedTranslation?
}
