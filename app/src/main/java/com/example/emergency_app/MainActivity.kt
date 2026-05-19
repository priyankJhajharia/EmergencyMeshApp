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
import android.widget.Button
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
    private lateinit var btnSend: Button
    private lateinit var btnSos: Button
    private lateinit var tvPeerCount: TextView
    private lateinit var tvMenuStatus: TextView
    private lateinit var tvConnectionDot: TextView
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
                Manifest.permission.SEND_SMS
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS
            )
        }

    // ✅ fixed serviceConnection with proper onServiceConnected
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

        // white status bar with black icons
        window.statusBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        // initialize views
        recyclerMessages = findViewById(R.id.recyclerMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnSos = findViewById(R.id.btnSos)
        tvPeerCount = findViewById(R.id.tvPeerCount)
        tvMenuStatus = findViewById(R.id.tvMenuStatus)
        tvConnectionDot = findViewById(R.id.tvConnectionDot)
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

        observeMessages()

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
            // confirm before clearing
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear Messages")
                .setMessage("Delete all messages from this device?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        MessageDatabase.getDatabase(this@MainActivity)
                            .messageDao()
                            .deleteAllMessages()
                    }
                    Toast.makeText(this, "Messages cleared", Toast.LENGTH_SHORT).show()
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

        // SOS button
        btnSos.setOnClickListener {
            if (!isBound) {
                Toast.makeText(this, "Mesh network not ready", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🆘 Send SOS?")
                .setMessage("This will send an emergency alert with your location to all nearby devices and your emergency contacts.")
                .setPositiveButton("YES, SEND SOS") { _, _ ->
                    meshService?.meshRouter?.sendSosMessage(userName)
                    Toast.makeText(this, "SOS sent!", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ✅ moved outside serviceConnection — correct place
    private fun startObserving() {
        // BT connection status
        lifecycleScope.launch {
            meshService?.bluetoothManager?.connectionStatus?.collect { status ->
                runOnUiThread {
                    tvPeerCount.text = status
                    tvMenuStatus.text = status
                }
            }
        }

        // WiFi connection status
        lifecycleScope.launch {
            meshService?.wifiDirectManager?.connectionStatus?.collect { status ->
                runOnUiThread {
                    tvPeerCount.text = status
                    tvMenuStatus.text = status
                }
            }
        }

        // peer count
        lifecycleScope.launch {
            meshService?.bluetoothManager?.connectedPeers?.collect { count ->
                runOnUiThread {
                    val wifiConnected =
                        meshService?.wifiDirectManager?.isConnected?.value ?: false
                    val wifiStatus = if (wifiConnected) " | WiFi ✓" else ""
                    val btStatus = if (count > 0) "$count BT peers" else "No BT peers"
                    tvPeerCount.text = "$btStatus$wifiStatus"
                    tvMenuStatus.text = "$btStatus$wifiStatus"
                    tvConnectionDot.setBackgroundColor(
                        if (count > 0 || wifiConnected)
                            android.graphics.Color.GREEN
                        else
                            android.graphics.Color.parseColor("#888888")
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

        val allDevices = meshService?.bluetoothManager
            ?.scanForDevices(this) ?: emptyList()

        if (allDevices.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("No devices found")
                .setMessage("Make sure other phone has app open and Bluetooth is ON")
                .setPositiveButton("Open Bluetooth Settings") { _, _ ->
                    startActivity(
                        Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            val deviceNames = allDevices.map { device ->
                "${device.name ?: "Unknown"} (${device.address})"
            }.toTypedArray()

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select device to connect")
                .setItems(deviceNames) { _, index ->
                    val device = allDevices[index]
                    Log.d("BT_DEBUG", "Connecting to: ${device.name}")
                    Toast.makeText(
                        this,
                        "Connecting to ${device.name}...",
                        Toast.LENGTH_SHORT
                    ).show()
                    meshService?.bluetoothManager?.connectToDevice(device)
                }
                .show()
        }
    }

    private fun updateStatus(status: String) {
        tvPeerCount.text = status
        tvMenuStatus.text = status
    }

    private fun observeMessages() {
        val database = MessageDatabase.getDatabase(this)
        lifecycleScope.launch {
            database.messageDao().getAllMessages().collect { messages ->
                chatAdapter.updateMessages(messages)
                if (messages.isNotEmpty()) {
                    recyclerMessages.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun startMeshService() {
        val intent = Intent(this, MeshService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        tvPeerCount.text = "Starting mesh network..."
        tvMenuStatus.text = "Starting mesh network..."
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
        stopService(Intent(this, MeshService::class.java))
    }
}