package com.example.emergency_app.service

import android.app.AlarmManager
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
import com.example.emergency_app.bluetooth.BleManager
import com.example.emergency_app.ui.ProfileActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MeshService : Service() {

    private val binder = MeshBinder()

    lateinit var bluetoothManager: BluetoothManager
    lateinit var wifiDirectManager: WifiDirectManager
    lateinit var meshRouter: MeshRouter
    lateinit var bleManager: BleManager
    private lateinit var shakeDetector: ShakeDetector

    private val scope = CoroutineScope(Dispatchers.Main)

    private val CHANNEL_ID = "MeshServiceChannel"
    private val NOTIFICATION_ID = 1
    private val SOS_PENDING_NOTIFICATION_ID = 2

    // tracks if SOS countdown is active so double shake cancels it
    private var sosPendingJob: kotlinx.coroutines.Job? = null
    private var isSosPending = false

    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = BluetoothManager(this)
        wifiDirectManager = WifiDirectManager(this)
        bleManager = BleManager(this)
        meshRouter = MeshRouter(this, bluetoothManager, wifiDirectManager)
        createNotificationChannel()

        // setup shake detector
        shakeDetector = ShakeDetector(this) {
            onShakeDetected()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        if (bluetoothManager.isBluetoothAvailable()) {
            bluetoothManager.startServer()
        }

        bleManager.startAdvertising()
        bleManager.startScanning()

        wifiDirectManager.register()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            wifiDirectManager.discoverPeers()
        }, 3000)

        // start shake detector
        shakeDetector.start()

        return START_STICKY
    }

    private fun onShakeDetected() {
        if (isSosPending) {
            // second shake within countdown — CANCEL SOS
            cancelSos()
        } else {
            // first shake — start 5 second countdown
            startSosCountdown()
        }
    }

    private fun startSosCountdown() {
        isSosPending = true

        // show countdown notification
        showSosPendingNotification(5)

        sosPendingJob = scope.launch {
            for (secondsLeft in 4 downTo 1) {
                delay(1000)
                if (!isSosPending) return@launch // cancelled
                showSosPendingNotification(secondsLeft)
            }
            delay(1000)
            if (!isSosPending) return@launch // cancelled

            // countdown finished — send SOS
            isSosPending = false
            cancelSosPendingNotification()
            val userName = ProfileActivity.getUserName(this@MeshService)
            meshRouter.sendSosMessage(userName)
        }
    }

    private fun cancelSos() {
        isSosPending = false
        sosPendingJob?.cancel()
        sosPendingJob = null
        cancelSosPendingNotification()

        // show cancelled notification briefly
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOS Cancelled ✅")
            .setContentText("Emergency alert has been cancelled")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(SOS_PENDING_NOTIFICATION_ID, notification)

        // auto dismiss after 3 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            notificationManager.cancel(SOS_PENDING_NOTIFICATION_ID)
        }, 3000)
    }

    private fun showSosPendingNotification(secondsLeft: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🆘 SOS Sending in ${secondsLeft}s...")
            .setContentText("Shake again to CANCEL")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .build()
        notificationManager.notify(SOS_PENDING_NOTIFICATION_ID, notification)
    }

    private fun cancelSosPendingNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(SOS_PENDING_NOTIFICATION_ID)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(applicationContext, MeshService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        shakeDetector.stop()
        bluetoothManager.shutdown()
        bleManager.shutdown()
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
            .setContentText("Monitoring for nearby devices...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)  // ← change to MAX
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            )                                               // ← add this
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mesh Service",
                NotificationManager.IMPORTANCE_HIGH  // ← change LOW to HIGH
            ).apply {
                description = "Emergency mesh network service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}