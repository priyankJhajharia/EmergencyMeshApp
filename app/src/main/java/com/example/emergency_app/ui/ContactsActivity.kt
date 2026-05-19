package com.example.emergency_app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
    private lateinit var recyclerContacts: RecyclerView
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var database: MessageDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        // initialize views
        etName = findViewById(R.id.etContactName)
        etPhone = findViewById(R.id.etContactPhone)
        btnAdd = findViewById(R.id.btnAddContact)
        recyclerContacts = findViewById(R.id.recyclerContacts)

        // initialize database
        database = MessageDatabase.getDatabase(this)

        // setup adapter
        contactsAdapter = ContactsAdapter { contact ->
            // delete contact when delete button clicked
            lifecycleScope.launch {
                database.contactDao().deleteContact(contact)
                Toast.makeText(
                    this@ContactsActivity,
                    "${contact.name} removed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // setup recycler view
        recyclerContacts.apply {
            adapter = contactsAdapter
            layoutManager = LinearLayoutManager(this@ContactsActivity)
        }

        // observe contacts
        lifecycleScope.launch {
            database.contactDao().getAllContacts().collect { contacts ->
                contactsAdapter.updateContacts(contacts)
            }
        }

        // add contact button
        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            // validate inputs
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

            // save to database
            lifecycleScope.launch {
                database.contactDao().insertContact(
                    EmergencyContact(
                        name = name,
                        phoneNumber = phone
                    )
                )
                Toast.makeText(
                    this@ContactsActivity,
                    "$name added as emergency contact",
                    Toast.LENGTH_SHORT
                ).show()

                // clear inputs
                etName.text.clear()
                etPhone.text.clear()
            }
        }
    }
}