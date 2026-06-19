package com.example.emergency_app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.emergency_app.R
import com.example.emergency_app.data.EmergencyContact
import com.example.emergency_app.data.MessageDatabase
import kotlinx.coroutines.launch

class ContactsActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnAdd: Button
    private lateinit var btnBack: android.widget.ImageButton
    private lateinit var tvContactCount: TextView
    private lateinit var recyclerContacts: RecyclerView
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var database: MessageDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        // dark status bar
        window.statusBarColor = android.graphics.Color.parseColor("#111111")
        window.decorView.systemUiVisibility = 0

        etName = findViewById(R.id.etContactName)
        etPhone = findViewById(R.id.etContactPhone)
        btnAdd = findViewById(R.id.btnAddContact)
        btnBack = findViewById(R.id.btnBack)
        tvContactCount = findViewById(R.id.tvContactCount)
        recyclerContacts = findViewById(R.id.recyclerContacts)

        database = MessageDatabase.getDatabase(this)

        contactsAdapter = ContactsAdapter { contact ->
            lifecycleScope.launch {
                database.contactDao().deleteContact(contact)
                Toast.makeText(
                    this@ContactsActivity,
                    "${contact.name} removed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        recyclerContacts.apply {
            adapter = contactsAdapter
            layoutManager = LinearLayoutManager(this@ContactsActivity)
        }

        // back button
        btnBack.setOnClickListener {
            finish()
        }

        // observe contacts
        lifecycleScope.launch {
            database.contactDao().getAllContacts().collect { contacts ->
                contactsAdapter.updateContacts(contacts)
                tvContactCount.text = "${contacts.size} contacts"
            }
        }

        // add contact
        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Enter contact name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phone.length < 10) {
                Toast.makeText(this, "Enter valid phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                database.contactDao().insertContact(
                    EmergencyContact(name = name, phoneNumber = phone)
                )
                Toast.makeText(
                    this@ContactsActivity,
                    "✅ $name added",
                    Toast.LENGTH_SHORT
                ).show()
                etName.text.clear()
                etPhone.text.clear()
            }
        }
    }
}