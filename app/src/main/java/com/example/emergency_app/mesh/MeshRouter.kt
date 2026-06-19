package com.example.emergency_app.mesh

import com.example.emergency_app.bluetooth.BluetoothManager
import com.example.emergency_app.data.MeshMessage
import com.example.emergency_app.data.MessageDatabase
import com.example.emergency_app.data.MessagePriority
import com.example.emergency_app.data.MessageType
import com.example.emergency_app.wifi.WifiDirectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.example.emergency_app.service.NotificationHelper
import java.util.UUID

class MeshRouter(
    private val context: android.content.Context,
    private val bluetoothManager: BluetoothManager,
    private val wifiDirectManager: WifiDirectManager,
) {
    private val notificationHelper = NotificationHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val database = MessageDatabase.getDatabase(context)
    private val messageDao = database.messageDao()

    // keeps track of seen message IDs to avoid duplicates
    private val seenMessages = LinkedHashMap<String, Long>(100, 0.75f, true)
    private val MAX_SEEN = 200

    // this device's unique ID
    val deviceId = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ANDROID_ID
    )

    // tracks the deviceId of whoever is currently connected
    // updated automatically as soon as we receive their first message
    private val _currentPeerId = MutableStateFlow<String?>(null)
    val currentPeerId: StateFlow<String?> = _currentPeerId

    init {
        // listen for incoming messages from bluetooth
        scope.launch {
            bluetoothManager.incomingMessages.collect { raw ->
                handleIncomingRaw(raw)
            }
        }

        // listen for incoming messages from wifi direct
        scope.launch {
            wifiDirectManager.incomingMessages.collect { raw ->
                handleIncomingRaw(raw)
            }
        }
    }

    // send a new chat message — goes to whichever peer is currently connected
    fun sendChatMessage(content: String, senderName: String) {
        val message = MeshMessage(
            id = UUID.randomUUID().toString(),
            senderId = deviceId,
            senderName = senderName,
            content = content,
            timestamp = System.currentTimeMillis(),
            ttl = 7,
            hopCount = 0,
            priority = MessagePriority.NORMAL,
            type = MessageType.CHAT,
            isMine = true,
            recipientId = _currentPeerId.value // tag with current peer
        )
        scope.launch {
            saveAndBroadcast(message)
        }
    }

    // send an SOS message with location — broadcast, no specific recipient
    fun sendSosMessage(senderName: String) {
        val locationHelper = LocationHelper(context)
        val location = locationHelper.getCurrentLocation()

        val locationText = if (location != null) {
            "\n📍 https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "\n📍 Location unavailable"
        }

        val message = MeshMessage(
            id = UUID.randomUUID().toString(),
            senderId = deviceId,
            senderName = senderName,
            content = "🆘 SOS EMERGENCY ALERT from $senderName$locationText",
            timestamp = System.currentTimeMillis(),
            ttl = 20,
            hopCount = 0,
            priority = MessagePriority.SOS,
            type = MessageType.SOS,
            isMine = true,
            latitude = location?.latitude,
            longitude = location?.longitude,
            recipientId = _currentPeerId.value // tag with current peer if any
        )

        scope.launch {
            // save and broadcast via mesh
            saveAndBroadcast(message)

            // send SMS to emergency contacts
            val contacts = database.contactDao().getContactsList()
            val smsHelper = SmsHelper(context)
            smsHelper.sendSosToContacts(
                contacts,
                senderName,
                location?.latitude,
                location?.longitude
            )
        }
    }

    // handle raw incoming message string
    private suspend fun handleIncomingRaw(raw: String) {
        try {
            val message = deserialize(raw)

            // skip if already seen
            if (seenMessages.containsKey(message.id)) return

            // skip if TTL expired
            if (message.ttl <= 0) return

            // mark as seen
            markSeen(message.id)

            // update current connected peer — this is who we're talking to now
            _currentPeerId.value = message.senderId

            // save to database — recipientId stays null for received messages,
            // we use senderId directly when querying conversation
            messageDao.insertMessage(message.copy(delivered = true))

            //  show notification for incoming messages
            if (message.type == com.example.emergency_app.data.MessageType.SOS) {
                notificationHelper.showSosNotification(
                    message.senderName,
                    message.content
                )
            } else {
                notificationHelper.showMessageNotification(
                    message.senderName,
                    message.content
                )
            }

            // forward to other peers with decremented TTL
            val forwarded = message.copy(
                ttl = message.ttl - 1,
                hopCount = message.hopCount + 1
            )
            broadcast(serialize(forwarded))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // save message locally and broadcast to peers
    private suspend fun saveAndBroadcast(message: MeshMessage) {
        // mark as seen so we don't reprocess our own message
        markSeen(message.id)

        // save to local database
        messageDao.insertMessage(message)

        // send to all peers
        broadcast(serialize(message))
    }

    // send raw string to all connected peers
    private fun broadcast(raw: String) {
        // prefer WiFi Direct — faster and longer range
        if (wifiDirectManager.isConnected.value) {
            wifiDirectManager.sendMessage(raw)
        }
        // always also send via Bluetooth for redundancy
        bluetoothManager.broadcastMessage(raw)
    }

    private fun serialize(message: MeshMessage): String {
        return JSONObject().apply {
            put("id", message.id)
            put("senderId", message.senderId)
            put("senderName", message.senderName)
            put("content", message.content)
            put("timestamp", message.timestamp)
            put("ttl", message.ttl)
            put("hopCount", message.hopCount)
            put("priority", message.priority.name)
            put("type", message.type.name)
            message.latitude?.let { put("latitude", it) }
            message.longitude?.let { put("longitude", it) }
        }.toString()
    }

    private fun deserialize(raw: String): MeshMessage {
        val json = JSONObject(raw)
        return MeshMessage(
            id = json.getString("id"),
            senderId = json.getString("senderId"),
            senderName = json.getString("senderName"),
            content = json.getString("content"),
            timestamp = json.getLong("timestamp"),
            ttl = json.getInt("ttl"),
            hopCount = json.getInt("hopCount"),
            priority = MessagePriority.valueOf(json.getString("priority")),
            type = MessageType.valueOf(json.getString("type")),
            delivered = true,
            isMine = json.getString("senderId") == deviceId,
            latitude = if (json.has("latitude")) json.getDouble("latitude") else null,
            longitude = if (json.has("longitude")) json.getDouble("longitude") else null
        )
    }

    // track seen messages — prevents infinite loops
    private fun markSeen(id: String) {
        if (seenMessages.size >= MAX_SEEN) {
            seenMessages.remove(seenMessages.keys.first())
        }
        seenMessages[id] = System.currentTimeMillis()
    }
}