package com.example.emergency_app.mesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat

class LocationHelper(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // get current location for SOS
    fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return null

        // try GPS first
        var location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

        // fall back to network if GPS unavailable
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }

        return location
    }

    // converts location to readable string
    fun formatLocation(lat: Double, lon: Double): String {
        return "📍 Location: $lat, $lon\n" +
                "🗺️ Maps: https://maps.google.com/?q=$lat,$lon"
    }

    // checks if location permission is granted
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}