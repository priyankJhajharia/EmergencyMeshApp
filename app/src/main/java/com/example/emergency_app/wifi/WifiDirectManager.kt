package com.example.emergency_app.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class WifiDirectManager(private val context: Context) {

    private var clientSocket: Socket? = null  // Client's persistent socket to Group Owner

    private val manager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: Channel = manager.initialize(context, context.mainLooper, null)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val PORT = 49152
    private val SOCKET_TIMEOUT = 5000

    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages: SharedFlow<String> = _incomingMessages

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectionStatus = MutableStateFlow("WiFi: Idle")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private var groupOwnerAddress: String? = null
    private var isGroupOwner = false
    private val clientSockets = mutableListOf<Socket>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (!hasWifiPermission()) return
                    manager.requestPeers(channel) { peerList ->
                        scope.launch {
                            val peers = peerList.deviceList.toList()
                            _connectionStatus.value = "Found ${peers.size} WiFi peers"
                            if (peers.isNotEmpty() && !_isConnected.value) {
                                connectToPeer(peers.first())
                            }
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    if (!hasWifiPermission()) return
                    manager.requestConnectionInfo(channel) { info ->
                        if (info.groupFormed) {
                            _isConnected.value = true
                            isGroupOwner = info.isGroupOwner
                            groupOwnerAddress = info.groupOwnerAddress?.hostAddress
                            scope.launch {
                                if (isGroupOwner) {
                                    _connectionStatus.value = "WiFi Direct: Group owner"
                                    startTcpServer()
                                } else {
                                    _connectionStatus.value = "WiFi Direct: Connected to group"
                                    groupOwnerAddress?.let {
                                        delay(1000)
                                        connectToGroupOwner(it)
                                    }
                                }
                            }
                        } else {
                            _isConnected.value = false
                            groupOwnerAddress = null
                            clientSocket = null
                            scope.launch {
                                _connectionStatus.value = "WiFi Direct: Disconnected"
                            }
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                        scope.launch {
                            _connectionStatus.value = "WiFi Direct not available"
                        }
                    }
                }
            }
        }
    }

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    fun register() {
        context.registerReceiver(receiver, intentFilter)
        manager.cancelConnect(channel, null)
        manager.removeGroup(channel, null)
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun discoverPeers() {
        if (!hasWifiPermission()) {
            scope.launch { _connectionStatus.value = "WiFi permission missing" }
            return
        }
        manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { startDiscovery() }
            override fun onFailure(reason: Int) { startDiscovery() }
        })
    }

    private fun startDiscovery() {
        if (!hasWifiPermission()) return
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                scope.launch {
                    _connectionStatus.value = "WiFi Direct: Scanning..."
                }
            }
            override fun onFailure(reason: Int) {
                scope.launch {
                    when (reason) {
                        WifiP2pManager.ERROR -> {
                            _connectionStatus.value = "WiFi error — toggle WiFi off/on"
                        }
                        WifiP2pManager.P2P_UNSUPPORTED -> {
                            _connectionStatus.value = "WiFi Direct not supported"
                        }
                        WifiP2pManager.BUSY -> {
                            _connectionStatus.value = "System busy — retrying in 5s..."
                            delay(5000)
                            manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                                override fun onSuccess() { startDiscovery() }
                                override fun onFailure(r: Int) { startDiscovery() }
                            })
                        }
                        else -> {
                            _connectionStatus.value = "WiFi failed ($reason) retrying..."
                            delay(5000)
                            startDiscovery()
                        }
                    }
                }
            }
        })
    }

    fun connectToPeer(device: WifiP2pDevice) {
        if (!hasWifiPermission()) return

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                scope.launch {
                    _connectionStatus.value = "WiFi Direct: Connecting to ${device.deviceName}..."
                }
            }
            override fun onFailure(reason: Int) {
                scope.launch {
                    _connectionStatus.value = "WiFi Direct: Connection failed ($reason)"
                }
            }
        })
    }

    private fun startTcpServer() {
        scope.launch {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(PORT)
                _connectionStatus.value = "WiFi Direct: TCP server ready"
                while (_isConnected.value) {
                    val client = serverSocket.accept()
                    synchronized(clientSockets) {
                        clientSockets.add(client)
                    }
                    handleTcpClient(client)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                serverSocket?.close()
            }
        }
    }

    private fun handleTcpClient(socket: Socket) {
        scope.launch {
            try {
                val buffer = ByteArray(4096)
                val inputStream = socket.getInputStream()
                while (true) {
                    val bytes = inputStream.read(buffer)
                    if (bytes == -1) break
                    val message = String(buffer, 0, bytes)
                    _incomingMessages.emit(message)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                socket.close()
                synchronized(clientSockets) {
                    clientSockets.remove(socket)
                }
                if (socket == clientSocket) clientSocket = null
            }
        }
    }

    private fun connectToGroupOwner(address: String) {
        scope.launch {
            try {
                val socket = Socket()
                socket.reuseAddress = true
                socket.soTimeout = 0 // no timeout — keep connection alive
                socket.connect(InetSocketAddress(address, PORT), SOCKET_TIMEOUT)
                clientSocket = socket // save persistent reference
                synchronized(clientSockets) {
                    clientSockets.add(socket)
                }
                _connectionStatus.value = "WiFi Direct: TCP connected!"
                handleTcpClient(socket) // start reading messages from Group Owner
            } catch (e: IOException) {
                e.printStackTrace()
                _connectionStatus.value = "TCP failed: ${e.message}"
            }
        }
    }

    fun sendMessage(message: String) {
        if (!_isConnected.value) return
        scope.launch {
            val data = message.toByteArray()
            if (isGroupOwner) {
                // Group Owner sends to ALL connected clients
                val deadSockets = mutableListOf<Socket>()
                synchronized(clientSockets) {
                    clientSockets.forEach { socket ->
                        try {
                            socket.getOutputStream().write(data)
                            socket.getOutputStream().flush()
                        } catch (e: IOException) {
                            deadSockets.add(socket)
                        }
                    }
                    clientSockets.removeAll(deadSockets)
                }
            } else {
                // Client reuses persistent socket — no new connection!
                try {
                    clientSocket?.getOutputStream()?.write(data)
                    clientSocket?.getOutputStream()?.flush()
                } catch (e: IOException) {
                    e.printStackTrace()
                    clientSocket = null
                    _connectionStatus.value = "WiFi: Send failed, reconnecting..."
                    groupOwnerAddress?.let {
                        delay(1000)
                        connectToGroupOwner(it)
                    }
                }
            }
        }
    }

    fun disconnect() {
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isConnected.value = false
                clientSocket = null
                synchronized(clientSockets) {
                    clientSockets.forEach { try { it.close() } catch (e: Exception) { } }
                    clientSockets.clear()
                }
            }
            override fun onFailure(reason: Int) {}
        })
    }

    private fun hasWifiPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}