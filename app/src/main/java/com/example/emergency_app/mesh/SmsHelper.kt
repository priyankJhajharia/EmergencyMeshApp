package com.example.emergency_app.mesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.example.emergency_app.data.EmergencyContact

class SmsHelper(private val context: Context) {

    // send SOS SMS to all emergency contacts
    fun sendSosToContacts(
        contacts: List<EmergencyContact>,
        senderName: String,
        latitude: Double?,
        longitude: Double?
    ) {
        if (!hasSmsPermission()) return
        if (contacts.isEmpty()) return

        val locationText = if (latitude != null && longitude != null) {
            "My location: https://maps.google.com/?q=$latitude,$longitude"
        } else {
            "Location unavailable"
        }

        val message = """
            🆘 EMERGENCY SOS ALERT
            From: $senderName
            $locationText
            Please help or contact emergency services!
        """.trimIndent()

        contacts.forEach { contact ->
            sendSms(contact.phoneNumber, message)
        }
    }

    // send single SMS
    private fun sendSms(phoneNumber: String, message: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val smsManager = context.getSystemService(SmsManager::class.java)
                // split message if too long
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    phoneNumber, null, parts, null, null
                )
            } else {
                @Suppress("DEPRECATION")
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    phoneNumber, null, parts, null, null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // check SMS permission
    fun hasSmsPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
}