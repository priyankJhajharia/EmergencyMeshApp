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

    // get conversation with one specific peer (1-to-1 chat)
    // shows: messages I sent to this peer + messages received from this peer
    @Query("""
        SELECT * FROM messages 
        WHERE (recipientId = :peerId AND isMine = 1) 
           OR (senderId = :peerId AND isMine = 0)
        ORDER BY timestamp ASC
    """)
    fun getConversationWithPeer(peerId: String): Flow<List<MeshMessage>>

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

    // delete conversation with one specific peer
    @Query("""
        DELETE FROM messages 
        WHERE (recipientId = :peerId AND isMine = 1) 
           OR (senderId = :peerId AND isMine = 0)
    """)
    suspend fun deleteConversationWithPeer(peerId: String)

    // delete old messages to save storage
    @Query("DELETE FROM messages WHERE timestamp < :cutoffTime")
    suspend fun deleteOldMessages(cutoffTime: Long)

    // get list of unique peers you've talked to (for device list screen)
    @Query("""
        SELECT DISTINCT 
            CASE WHEN isMine = 1 THEN recipientId ELSE senderId END as peerId
        FROM messages
        WHERE recipientId IS NOT NULL OR isMine = 0
    """)
    suspend fun getAllPeerIds(): List<String>
}