package com.example.emergency_app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.emergency_app.data.MessageDatabase
import com.example.emergency_app.service.MeshService
import com.example.emergency_app.ui.ChatAdapter
import com.example.emergency_app.ui.ContactsActivity
import com.example.emergency_app.ui.ProfileActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var tvWifiStatus: TextView
    private lateinit var tvPeerCount: TextView
    private lateinit var tvMenuStatus: TextView
    private lateinit var tvConnectionDot: View
    private lateinit var tvSubtitle: TextView
    private lateinit var btnMenu: ImageButton
    private lateinit var drawerMenu: LinearLayout
    private lateinit var drawerOverlay: View
    private lateinit var chatAdapter: ChatAdapter

    private var meshService: MeshService? = null
    private var isBound = false
    private var isDrawerOpen = false

    private val userName get() = ProfileActivity.getUserName(this)

    private val PERMISSION_REQUEST_CODE = 100
    private val PERMISSIONS =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,  // ← ADD THIS
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS  // ← ADD THIS
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS  // ← ADD THIS
            )
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val meshBinder = binder as MeshService.MeshBinder
            meshService = meshBinder.getService()
            isBound = true
            startObserving()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            meshService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // dark status bar with white icons
        window.statusBarColor = android.graphics.Color.parseColor("#111111")
        window.decorView.systemUiVisibility = 0

        // initialize views
        recyclerMessages = findViewById(R.id.recyclerMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        tvPeerCount = findViewById(R.id.tvPeerCount)
        tvMenuStatus = findViewById(R.id.tvMenuStatus)
        tvConnectionDot = findViewById(R.id.tvConnectionDot)
        tvWifiStatus = findViewById(R.id.tvWifiStatus)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        btnMenu = findViewById(R.id.btnMenu)
        drawerMenu = findViewById(R.id.drawerMenu)
        drawerOverlay = findViewById(R.id.drawerOverlay)

        // setup recycler
        chatAdapter = ChatAdapter()
        recyclerMessages.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
        }

        // request permissions
        if (hasAllPermissions()) {
            startMeshService()
        } else {
            requestPermissions()
        }

        // hamburger menu
        btnMenu.setOnClickListener {
            toggleDrawer()
        }

        // close drawer when clicking overlay
        drawerOverlay.setOnClickListener {
            closeDrawer()
        }

        // menu items
        findViewById<LinearLayout>(R.id.menuFindPeers).setOnClickListener {
            closeDrawer()
            findPeers()
        }

        findViewById<LinearLayout>(R.id.menuContacts).setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menuProfile).setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menuClearMessages).setOnClickListener {
            closeDrawer()
            val peerId = meshService?.meshRouter?.currentPeerId?.value
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear Conversation")
                .setMessage("Delete messages with this device?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        if (peerId != null) {
                            MessageDatabase.getDatabase(this@MainActivity)
                                .messageDao()
                                .deleteConversationWithPeer(peerId)
                        } else {
                            MessageDatabase.getDatabase(this@MainActivity)
                                .messageDao()
                                .deleteAllMessages()
                        }
                    }
                    Toast.makeText(this, "Conversation cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // send button
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Type a message first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isBound) {
                Toast.makeText(this, "Mesh network not ready", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            meshService?.meshRouter?.sendChatMessage(text, userName)
            etMessage.text.clear()
        }

        // SOS button — hold to send
        val btnSos = findViewById<TextView>(R.id.btnSos)
        btnSos.setOnLongClickListener {
            if (!isBound) {
                Toast.makeText(this, "Mesh network not ready", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🆘 Send SOS?")
                .setMessage("This will send an emergency alert with your location to all nearby devices and your emergency contacts.")
                .setPositiveButton("YES, SEND SOS") { _, _ ->
                    meshService?.meshRouter?.sendSosMessage(userName)
                    Toast.makeText(this, "🆘 SOS sent!", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        // show hint for SOS
        btnSos.setOnClickListener {
            Toast.makeText(
                this,
                "Hold the SOS button to send emergency alert",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startObserving() {
        observeMessages()
        // BT status
        lifecycleScope.launch {
            meshService?.bluetoothManager?.connectionStatus?.collect { status ->
                runOnUiThread {
                    tvPeerCount.text = status
                    tvSubtitle.text = status
                    tvMenuStatus.text = status
                }
            }
        }

        // ADD THIS — observe WiFi connection separately
        lifecycleScope.launch {
            meshService?.wifiDirectManager?.isConnected?.collect { wifiConnected ->
                runOnUiThread {
                    val btCount = meshService?.bluetoothManager?.connectedPeers?.value ?: 0
                    tvConnectionDot.setBackgroundColor(
                        if (btCount > 0 || wifiConnected)
                            android.graphics.Color.parseColor("#30D158")
                        else
                            android.graphics.Color.parseColor("#636366")
                    )
                }
            }
        }

        // peer count — update connection dot
        lifecycleScope.launch {
            meshService?.bluetoothManager?.connectedPeers?.collect { count ->
                runOnUiThread {
                    val wifiConnected =
                        meshService?.wifiDirectManager?.isConnected?.value ?: false
                    tvConnectionDot.setBackgroundColor(
                        if (count > 0 || wifiConnected)
                            android.graphics.Color.parseColor("#30D158")
                        else
                            android.graphics.Color.parseColor("#636366")
                    )
                }
            }
        }
    }

    private fun toggleDrawer() {
        if (isDrawerOpen) closeDrawer() else openDrawer()
    }

    private fun openDrawer() {
        drawerMenu.visibility = View.VISIBLE
        drawerOverlay.visibility = View.VISIBLE
        isDrawerOpen = true
    }

    private fun closeDrawer() {
        drawerMenu.visibility = View.GONE
        drawerOverlay.visibility = View.GONE
        isDrawerOpen = false
    }

    private fun findPeers() {
        if (!isBound) return

        val pairedDevices: List<android.bluetooth.BluetoothDevice> = meshService?.bluetoothManager
            ?.scanForDevices(this) ?: emptyList()

        val bleDevices: List<android.bluetooth.BluetoothDevice> =
            meshService?.bleManager?.getDiscoveredDevices() ?: emptyList()

        val allDevices: List<android.bluetooth.BluetoothDevice> = (pairedDevices + bleDevices)
            .distinctBy { device: android.bluetooth.BluetoothDevice -> device.address }

        if (allDevices.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("No devices found")
                .setMessage("Make sure other phone has app open, Bluetooth is ON and both phones are nearby")
                .setPositiveButton("Open Bluetooth Settings") { _, _ ->
                    startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            val deviceNames = allDevices.map { device: android.bluetooth.BluetoothDevice ->
                val name = if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED)
                    device.name ?: "Unknown" else "Unknown"
                val type = if (bleDevices.any { b: android.bluetooth.BluetoothDevice ->
                        b.address == device.address }) " [BLE]" else " [Paired]"
                "$name$type (${device.address})"
            }.toTypedArray()

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select device to connect")
                .setItems(deviceNames) { _, index: Int ->
                    val device = allDevices[index]
                    val name = if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                        device.name ?: "Unknown" else "Unknown"
                    Log.d("BT_DEBUG", "Connecting to: $name")
                    Toast.makeText(this, "Connecting to $name...", Toast.LENGTH_SHORT).show()
                    meshService?.bluetoothManager?.connectToDevice(device)
                }
                .show()
        }
    }

    private var currentMessagesJob: kotlinx.coroutines.Job? = null

    private fun observeMessages() {
        // observe which peer we're currently connected to
        lifecycleScope.launch {
            meshService?.meshRouter?.currentPeerId?.collect { peerId ->
                // cancel previous observer
                currentMessagesJob?.cancel()

                val database = MessageDatabase.getDatabase(this@MainActivity)

                currentMessagesJob = lifecycleScope.launch {
                    if (peerId != null) {
                        // show only conversation with this peer
                        database.messageDao().getConversationWithPeer(peerId).collect { messages ->
                            chatAdapter.updateMessages(messages)
                            if (messages.isNotEmpty()) {
                                recyclerMessages.scrollToPosition(messages.size - 1)
                            }
                        }
                    } else {
                        // no peer connected yet — show empty
                        chatAdapter.updateMessages(emptyList())
                    }
                }
            }
        }
    }

    private fun startMeshService() {
        val intent = Intent(this, MeshService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        tvPeerCount.text = "BT: Starting..."
        tvWifiStatus.text = "WiFi: Starting..."
        tvSubtitle.text = "Initializing mesh network..."
        tvMenuStatus.text = "Initializing..."
    }

    private fun hasAllPermissions(): Boolean {
        return PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startMeshService()
            } else {
                Toast.makeText(
                    this,
                    "Permissions required for mesh network",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        meshService?.wifiDirectManager?.register()
    }

    override fun onPause() {
        super.onPause()
        meshService?.wifiDirectManager?.unregister()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}