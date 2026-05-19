package com.example.emergency_app.ui

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.emergency_app.R

class ProfileActivity : AppCompatActivity() {

    private lateinit var etUserName: EditText
    private lateinit var etUserPhone: EditText
    private lateinit var btnSave: Button
    private lateinit var tvDeviceId: TextView
    private lateinit var tvDeviceModel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        etUserName = findViewById(R.id.etUserName)
        etUserPhone = findViewById(R.id.etUserPhone)
        btnSave = findViewById(R.id.btnSaveProfile)
        tvDeviceId = findViewById(R.id.tvDeviceId)
        tvDeviceModel = findViewById(R.id.tvDeviceModel)

        // load saved profile
        val prefs = getSharedPreferences("profile", Context.MODE_PRIVATE)
        val savedName = prefs.getString("user_name", "")
        val savedPhone = prefs.getString("user_phone", "")

        etUserName.setText(savedName)
        etUserPhone.setText(savedPhone)

        // show device info
        val deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        tvDeviceId.text = "Device ID: $deviceId"
        tvDeviceModel.text = "Device Model: ${android.os.Build.MODEL}"

        // save profile
        btnSave.setOnClickListener {
            val name = etUserName.text.toString().trim()
            val phone = etUserPhone.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // save to SharedPreferences
            prefs.edit()
                .putString("user_name", name)
                .putString("user_phone", phone)
                .apply()

            Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        // get saved user name anywhere in app
        fun getUserName(context: Context): String {
            val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
            return prefs.getString("user_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
        }

        // get saved user phone anywhere in app
        fun getUserPhone(context: Context): String {
            val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
            return prefs.getString("user_phone", "") ?: ""
        }
    }
}