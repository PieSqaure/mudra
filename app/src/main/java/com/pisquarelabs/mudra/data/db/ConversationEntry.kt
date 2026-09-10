package com.pisquarelabs.mudra.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One resolved utterance, kept locally so the reasoner can use recent turns as context. */
@Entity(tableName = "conversation_entries")
data class ConversationEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val intent: String,
    val confidence: Float,
    val needsConfirmation: Boolean,
    val timestampMs: Long
)
