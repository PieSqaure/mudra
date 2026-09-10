package com.pisquarelabs.mudra.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ConversationEntry::class], version = 1, exportSchema = false)
abstract class MudraDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile private var instance: MudraDatabase? = null

        fun getInstance(context: Context): MudraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MudraDatabase::class.java,
                    "mudra.db"
                ).build().also { instance = it }
            }
    }
}
