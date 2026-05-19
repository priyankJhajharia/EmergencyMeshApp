package com.example.emergency_app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // save a message to database
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MeshMessage)

    // get all messages ordered by time — live updates via Flow
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MeshMessage>>

    // get only SOS messages
    @Query("SELECT * FROM messages WHERE type = 'SOS' ORDER BY timestamp DESC")
    fun getSosMessages(): Flow<List<MeshMessage>>

    // get undelivered messages to forward to other peers
    @Query("SELECT * FROM messages WHERE delivered = 0")
    suspend fun getPendingMessages(): List<MeshMessage>

    // mark message as delivered
    @Query("UPDATE messages SET delivered = 1 WHERE id = :messageId")
    suspend fun markAsDelivered(messageId: String)

    // check if message already exists — for deduplication
    @Query("SELECT COUNT(*) FROM messages WHERE id = :messageId")
    suspend fun messageExists(messageId: String): Int

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    // delete old messages to save storage
    @Query("DELETE FROM messages WHERE timestamp < :cutoffTime")
    suspend fun deleteOldMessages(cutoffTime: Long)
}