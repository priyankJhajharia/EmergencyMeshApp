package com.example.emergency_app.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
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
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothManager(private val context: Context) {

    private val APP_UUID: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val APP_NAME = "EmergencyMesh"

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val connectedSockets = mutableListOf<BluetoothSocket>()
    private var isServerRunning = false

    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages: SharedFlow<String> = _incomingMessages

    private val _connectedPeers = MutableStateFlow<Int>(0)
    val connectedPeers: StateFlow<Int> = _connectedPeers

    private val _connectionStatus = MutableSharedFlow<String>()
    val connectionStatus: SharedFlow<String> = _connectionStatus

    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }

    fun startServer() {
        if (isServerRunning) return
        isServerRunning = true

        scope.launch {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                _connectionStatus.emit("BT: Missing permission")
                return@launch
            }

            _connectionStatus.emit("BT: Server starting...")

            while (isServerRunning) {
                var serverSocket: BluetoothServerSocket? = null
                try {
                    serverSocket = try {
                        bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
                    } catch (e: IOException) {
                        bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, APP_UUID)
                    }

                    _connectionStatus.emit("BT: Waiting for connections...")
                    Log.d("BT_DEBUG", "Server socket created, waiting...")

                    val socket = serverSocket?.accept(60000)

                    if (socket != null) {
                        Log.d("BT_DEBUG", "Connection accepted from ${socket.remoteDevice.name}")
                        serverSocket.close()
                        if (!connectedSockets.contains(socket)) {
                            connectedSockets.add(socket)
                        }
                        _connectedPeers.value = connectedSockets.size
                        _connectionStatus.emit("BT: Connected to ${socket.remoteDevice.name}!")
                        handleConnection(socket)
                    }
                } catch (e: IOException) {
                    Log.e("BT_DEBUG", "Server error: ${e.message}")
                    serverSocket?.close()
                    _connectionStatus.emit("BT: Server restarting...")
                    delay(3000)
                }
            }
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        scope.launch {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                _connectionStatus.emit("BT: Missing permission")
                return@launch
            }

            // If device was found via BLE scan, get Classic BT reference
            // using the same MAC address so RFCOMM works
            val actualDevice = if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
                Log.d("BT_DEBUG", "BLE device detected, switching to Classic BT reference")
                bluetoothAdapter?.getRemoteDevice(device.address) ?: device
            } else {
                device
            }

            if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                bluetoothAdapter?.cancelDiscovery()
            }

            _connectionStatus.emit("BT: Connecting to ${actualDevice.name}...")
            Log.d("BT_DEBUG", "Connecting to ${actualDevice.name} - ${actualDevice.address}")

            delay(500)

            var socket: BluetoothSocket? = null
            var connected = false

            // Method 1 — secure RFCOMM
            if (!connected) {
                try {
                    _connectionStatus.emit("BT: Trying secure connection...")
                    socket = actualDevice.createRfcommSocketToServiceRecord(APP_UUID)
                    socket.connect()
                    connected = true
                    Log.d("BT_DEBUG", "Method 1 SUCCESS")
                } catch (e: IOException) {
                    Log.e("BT_DEBUG", "Method 1 FAILED: ${e.message}")
                    try { socket?.close() } catch (ex: IOException) { }
                    socket = null
                    delay(500)
                }
            }

            // Method 2 — insecure RFCOMM (no pairing needed)
            if (!connected) {
                try {
                    _connectionStatus.emit("BT: Trying insecure connection...")
                    socket = actualDevice.createInsecureRfcommSocketToServiceRecord(APP_UUID)
                    socket.connect()
                    connected = true
                    Log.d("BT_DEBUG", "Method 2 SUCCESS")
                } catch (e: IOException) {
                    Log.e("BT_DEBUG", "Method 2 FAILED: ${e.message}")
                    try { socket?.close() } catch (ex: IOException) { }
                    socket = null
                    delay(500)
                }
            }

            // Method 3 — reflection fallback
            if (!connected) {
                try {
                    _connectionStatus.emit("BT: Trying fallback connection...")
                    val method = actualDevice.javaClass.getMethod(
                        "createRfcommSocket",
                        Int::class.java
                    )
                    socket = method.invoke(actualDevice, 1) as BluetoothSocket
                    socket.connect()
                    connected = true
                    Log.d("BT_DEBUG", "Method 3 SUCCESS")
                } catch (e: Exception) {
                    Log.e("BT_DEBUG", "Method 3 FAILED: ${e.message}")
                    try { socket?.close() } catch (ex: IOException) { }
                    socket = null
                }
            }

            if (connected && socket != null) {
                if (!connectedSockets.contains(socket)) {
                    connectedSockets.add(socket)
                }
                _connectedPeers.value = connectedSockets.size
                _connectionStatus.emit("BT: Connected to ${actualDevice.name}! ✓")
                Log.d("BT_DEBUG", "Successfully connected to ${actualDevice.name}")
                handleConnection(socket)
            } else {
                _connectionStatus.emit("BT: Failed to connect to ${actualDevice.name}")
                Log.e("BT_DEBUG", "All connection methods failed")
            }
        }
    }

    private fun handleConnection(socket: BluetoothSocket) {
        scope.launch {
            try {
                val inputStream = socket.inputStream
                val buffer = ByteArray(4096)

                while (true) {
                    val bytes = inputStream.read(buffer)
                    if (bytes == -1) break
                    val message = String(buffer, 0, bytes)
                    Log.d("BT_DEBUG", "Received message: ${message.take(50)}")
                    _incomingMessages.emit(message)
                }
            } catch (e: IOException) {
                Log.e("BT_DEBUG", "Connection lost: ${e.message}")
            } finally {
                try { socket.close() } catch (ex: IOException) { }
                connectedSockets.remove(socket)
                _connectedPeers.value = connectedSockets.size
                _connectionStatus.emit("BT: Peer disconnected")
            }
        }
    }

    fun broadcastMessage(message: String) {
        scope.launch {
            val data = message.toByteArray()
            val deadSockets = mutableListOf<BluetoothSocket>()

            connectedSockets.forEach { socket ->
                try {
                    socket.outputStream.write(data)
                    socket.outputStream.flush()
                } catch (e: IOException) {
                    deadSockets.add(socket)
                }
            }
            if (deadSockets.isNotEmpty()) {
                connectedSockets.removeAll(deadSockets)
                _connectedPeers.value = connectedSockets.size
            }
        }
    }

    fun scanForDevices(context: Context): List<BluetoothDevice> {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return emptyList()
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun getConnectedPeerCount(): Int = connectedSockets.size

    fun shutdown() {
        isServerRunning = false
        connectedSockets.forEach {
            try { it.close() } catch (e: IOException) { }
        }
        connectedSockets.clear()
        _connectedPeers.value = 0
    }

    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}