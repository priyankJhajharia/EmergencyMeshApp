package com.example.emergency_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.emergency_app.MainActivity
import com.example.emergency_app.bluetooth.BluetoothManager
import com.example.emergency_app.mesh.MeshRouter
import com.example.emergency_app.wifi.WifiDirectManager

class MeshService : Service() {

    private val binder = MeshBinder()

    lateinit var bluetoothManager: BluetoothManager
    lateinit var wifiDirectManager: WifiDirectManager
    lateinit var meshRouter: MeshRouter

    private val CHANNEL_ID = "MeshServiceChannel"
    private val NOTIFICATION_ID = 1

    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = BluetoothManager(this)
        wifiDirectManager = WifiDirectManager(this)
        meshRouter = MeshRouter(this, bluetoothManager, wifiDirectManager)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        if (bluetoothManager.isBluetoothAvailable()) {
            bluetoothManager.startServer()
        }

        wifiDirectManager.register()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            wifiDirectManager.discoverPeers()
        }, 3000)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.shutdown()
        wifiDirectManager.unregister()
        wifiDirectManager.disconnect()
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Emergency Mesh Active")
            .setContentText("Tap to open")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Emergency mesh network service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}