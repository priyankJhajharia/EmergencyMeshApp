package com.example.emergency_app.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
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
import android.util.Log
import java.util.UUID

class BluetoothManager(private val context: Context) {

    private val APP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val APP_NAME = "EmergencyMesh"

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val connectedSockets = mutableListOf<BluetoothSocket>()

    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages: SharedFlow<String> = _incomingMessages

    private val _connectedPeers = MutableStateFlow<Int>(0)
    val connectedPeers: StateFlow<Int> = _connectedPeers

    private val _connectionStatus = MutableSharedFlow<String>()
    val connectionStatus: SharedFlow<String> = _connectionStatus

    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }

    // start server — runs continuously accepting connections
    fun startServer() {
        scope.launch {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return@launch

            while (true) {
                var serverSocket: BluetoothServerSocket? = null
                try {
                    serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                        APP_NAME, APP_UUID
                    )
                    _connectionStatus.emit("BT: Waiting for connections...")

                    val socket = serverSocket?.accept(30000)
                    if (socket != null) {
                        serverSocket.close()
                        handleConnection(socket)
                        _connectedPeers.value = connectedSockets.size
                        _connectionStatus.emit("BT: Connected to ${socket.remoteDevice.name}")
                    }
                } catch (e: IOException) {
                    serverSocket?.close()
                    _connectionStatus.emit("BT: Server restarting...")
                    delay(2000)
                }
            }
        }
    }

    // connect to a specific device with retries
    fun connectToDevice(device: BluetoothDevice) {
        scope.launch {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return@launch

            _connectionStatus.emit("BT: Connecting to ${device.name}...")

            if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                bluetoothAdapter?.cancelDiscovery()
            }

            delay(500)

            var socket: BluetoothSocket? = null
            var connected = false

            // Method 1
            try {
                _connectionStatus.emit("BT: Method 1 trying...")
                socket = device.createRfcommSocketToServiceRecord(APP_UUID)
                socket.connect()
                connected = true
                _connectionStatus.emit("BT: Method 1 worked!")
            } catch (e: IOException) {
                _connectionStatus.emit("BT: Method 1 failed...")
                socket?.close()
                socket = null
                delay(1000)
            }

            // Method 2
            if (!connected) {
                try {
                    _connectionStatus.emit("BT: Method 2 trying...")
                    val method = device.javaClass.getMethod(
                        "createRfcommSocket",
                        Int::class.java
                    )
                    socket = method.invoke(device, 1) as BluetoothSocket
                    socket.connect()
                    connected = true
                    _connectionStatus.emit("BT: Method 2 worked!")
                } catch (e: Exception) {
                    _connectionStatus.emit("BT: Method 2 failed...")
                    socket?.close()
                    socket = null
                    delay(1000)
                }
            }

            // Method 3
            if (!connected) {
                try {
                    _connectionStatus.emit("BT: Method 3 trying...")
                    socket = device.createInsecureRfcommSocketToServiceRecord(APP_UUID)
                    socket.connect()
                    connected = true
                    _connectionStatus.emit("BT: Method 3 worked!")
                } catch (e: IOException) {
                    _connectionStatus.emit("BT: All methods failed")
                    socket?.close()
                    socket = null
                }
            }

            if (connected && socket != null) {
                connectedSockets.add(socket)
                _connectedPeers.value = connectedSockets.size
                _connectionStatus.emit("BT: Connected to ${device.name}!")
                handleConnection(socket)
            } else {
                _connectionStatus.emit("BT: Failed — make sure both apps are open")
            }
        }
    }

    // handle a connected socket
    private fun handleConnection(socket: BluetoothSocket) {
        if (!connectedSockets.contains(socket)) {
            connectedSockets.add(socket)
        }
        scope.launch {
            try {
                val inputStream = socket.inputStream
                val buffer = ByteArray(4096)

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
                connectedSockets.remove(socket)
                _connectedPeers.value = connectedSockets.size
            }
        }
    }

    // broadcast to all connected devices
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
            connectedSockets.removeAll(deadSockets)
            _connectedPeers.value = connectedSockets.size
        }
    }

    // get paired devices
    fun scanForDevices(context: Context): List<BluetoothDevice> {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return emptyList()
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun getConnectedPeerCount(): Int = connectedSockets.size

    fun shutdown() {
        connectedSockets.forEach {
            try { it.close() } catch (e: IOException) { }
        }
        connectedSockets.clear()
    }

    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}