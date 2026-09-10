package com.pisquarelabs.mudra.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(entry: ConversationEntry)

    @Query("SELECT * FROM conversation_entries ORDER BY timestampMs DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<ConversationEntry>>

    @Query("SELECT * FROM conversation_entries ORDER BY timestampMs DESC LIMIT 1")
    suspend fun latest(): ConversationEntry?

    @Query("DELETE FROM conversation_entries")
    suspend fun clear()
}
