package com.example.myapplication.data.room

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Chat::class], version = 1)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                Log.d("ChatDatabase", "Creating new instance")
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                ).build()
                INSTANCE = instance
                instance
            }
         }
    }
}