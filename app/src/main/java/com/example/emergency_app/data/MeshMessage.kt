package com.example.emergency_app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessagePriority {
    NORMAL,
    HIGH,
    SOS
}

enum class MessageType {
    CHAT,
    SOS,
    ACK,
    DISCOVERY
}

@Entity(tableName = "messages")
data class MeshMessage(
    @PrimaryKey
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val ttl: Int = 7,
    val hopCount: Int = 0,
    val priority: MessagePriority = MessagePriority.NORMAL,
    val type: MessageType = MessageType.CHAT,
    val delivered: Boolean = false,
    val isMine: Boolean = false,

    // ✅ new — only filled for SOS messages
    val latitude: Double? = null,
    val longitude: Double? = null
)