package com.example.emergency_app.ui

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.emergency_app.R

class ProfileActivity : AppCompatActivity() {

    private lateinit var etUserName: EditText
    private lateinit var etUserPhone: EditText
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton
    private lateinit var tvDeviceId: TextView
    private lateinit var tvDeviceModel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // dark status bar
        window.statusBarColor = android.graphics.Color.parseColor("#111111")
        window.decorView.systemUiVisibility = 0

        etUserName = findViewById(R.id.etUserName)
        etUserPhone = findViewById(R.id.etUserPhone)
        btnSave = findViewById(R.id.btnSaveProfile)
        btnBack = findViewById(R.id.btnBack)
        tvDeviceId = findViewById(R.id.tvDeviceId)
        tvDeviceModel = findViewById(R.id.tvDeviceModel)

        // load saved profile
        val prefs = getSharedPreferences("profile", Context.MODE_PRIVATE)
        etUserName.setText(prefs.getString("user_name", ""))
        etUserPhone.setText(prefs.getString("user_phone", ""))

        // device info
        val deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        tvDeviceId.text = "ID: $deviceId"
        tvDeviceModel.text = android.os.Build.MODEL

        // back button
        btnBack.setOnClickListener {
            finish()
        }

        // save profile
        btnSave.setOnClickListener {
            val name = etUserName.text.toString().trim()
            val phone = etUserPhone.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("user_name", name)
                .putString("user_phone", phone)
                .apply()

            Toast.makeText(this, "✅ Profile saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        fun getUserName(context: Context): String {
            val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
            return prefs.getString("user_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
        }

        fun getUserPhone(context: Context): String {
            val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
            return prefs.getString("user_phone", "") ?: ""
        }
    }
}