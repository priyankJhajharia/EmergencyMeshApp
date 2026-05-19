package com.example.emergency_app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromPriority(priority: MessagePriority): String {
        return priority.name
    }

    @TypeConverter
    fun toPriority(value: String): MessagePriority {
        return MessagePriority.valueOf(value)
    }

    @TypeConverter
    fun fromType(type: MessageType): String {
        return type.name
    }

    @TypeConverter
    fun toType(value: String): MessageType {
        return MessageType.valueOf(value)
    }
}

// ✅ added EmergencyContact::class and version 2
@Database(
    entities = [MeshMessage::class, EmergencyContact::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MessageDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao  // ✅ added

    companion object {
        @Volatile
        private var INSTANCE: MessageDatabase? = null

        fun getDatabase(context: Context): MessageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MessageDatabase::class.java,
                    "mesh_messages_db"
                )
                    .fallbackToDestructiveMigration() // ✅ handles version upgrade
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}