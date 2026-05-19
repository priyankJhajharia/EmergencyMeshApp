package com.example.emergency_app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    // add new contact
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContact)

    // get all contacts live
    @Query("SELECT * FROM emergency_contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<EmergencyContact>>

    // get all contacts as simple list
    @Query("SELECT * FROM emergency_contacts")
    suspend fun getContactsList(): List<EmergencyContact>

    // delete a contact
    @Delete
    suspend fun deleteContact(contact: EmergencyContact)

    // get contact count
    @Query("SELECT COUNT(*) FROM emergency_contacts")
    suspend fun getContactCount(): Int
}