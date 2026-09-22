package com.surakkha.bd

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.surakkha.bd.network.LocationUpdateRequest
import com.surakkha.bd.network.NetworkResult
import com.surakkha.bd.network.RetrofitClient
import com.surakkha.bd.network.SosRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvStatus: TextView
    private lateinit var tvWelcomeUser: TextView
    private lateinit var btnSos: Button
    private lateinit var sosProgressBar: ProgressBar

    // Coroutine Job for periodic 30-second location updates
    private var locationUpdateJob: Job? = null

    companion object {
        private const val TAG = "Surakkha_MainActivity"
        private const val LOCATION_UPDATE_INTERVAL_MS = 30_000L // 30 seconds
    }

    // Modern ActivityResultLauncher to request Location permissions at runtime
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // Permission granted -> Fetch location and dispatch SOS
            fetchLocationAndTriggerSos()
            // Also kick off periodic location sync
            startPeriodicLocationUpdates()
        } else {
            // Permission denied -> Show informative message
            Toast.makeText(
                this,
                "Location permission is required for the SOS button and emergency network features.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Verify Authentication Token in SharedPreferences
        val sharedPreferences = getSharedPreferences("surakkha_prefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("auth_token", null)

        if (token.isNullOrEmpty()) {
            // Not authenticated -> Redirect to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // 2. Set XML View layout
        setContentView(R.layout.activity_main)

        // 3. Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 4. Find UI views
        btnSos = findViewById(R.id.btnSos)
        sosProgressBar = findViewById(R.id.sosProgressBar)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        tvWelcomeUser = findViewById(R.id.tvWelcomeUser)
        tvStatus = findViewById(R.id.tvStatus)

        // Display user name if saved
        val userName = sharedPreferences.getString("user_name", "")
        if (!userName.isNullOrEmpty()) {
            tvWelcomeUser.text = "Welcome, $userName!"
        }

        // 5. SOS Button Click Listener
        btnSos.setOnClickListener {
            handleSosClick()
        }

        // 6. Logout Button
        btnLogout.setOnClickListener {
            stopPeriodicLocationUpdates()
            sharedPreferences.edit().clear().apply()
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Start or resume periodic location updates every 30 seconds
        startPeriodicLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic loop when activity is not in foreground to save battery
        stopPeriodicLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicLocationUpdates()
    }

    /**
     * Starts a coroutine loop that updates user location immediately, then every 30 seconds.
     */
    private fun startPeriodicLocationUpdates() {
        stopPeriodicLocationUpdates()

        val sharedPreferences = getSharedPreferences("surakkha_prefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("auth_token", null)
        if (token.isNullOrEmpty()) return

        locationUpdateJob = lifecycleScope.launch {
            while (isActive) {
                if (hasLocationPermission()) {
                    fetchAndSendLocationUpdate(token)
                }
                delay(LOCATION_UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * Cancels the active periodic location update loop.
     */
    private fun stopPeriodicLocationUpdates() {
        locationUpdateJob?.cancel()
        locationUpdateJob = null
    }

    /**
     * Fetches current GPS coordinates silently and sends them to Geo Service (port 3003).
     */
    @SuppressLint("MissingPermission")
    private fun fetchAndSendLocationUpdate(token: String) {
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                sendLocationToGeoService(token, location.latitude, location.longitude)
            } else {
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                    if (lastLoc != null) {
                        sendLocationToGeoService(token, lastLoc.latitude, lastLoc.longitude)
                    }
                }
            }
        }.addOnFailureListener { e ->
            Log.w(TAG, "Periodic location fetch failed: ${e.message}")
        }
    }

    /**
     * Sends location payload to Geo Service (POST /location/update).
     */
    private fun sendLocationToGeoService(token: String, latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            try {
                val request = LocationUpdateRequest(latitude = latitude, longitude = longitude)
                val response = RetrofitClient.geoApiService.updateLocation("Bearer $token", request)

                if (response.isSuccessful) {
                    Log.d(TAG, "[Geo] Periodic location sent successfully: ($latitude, $longitude)")
                } else {
                    Log.w(TAG, "[Geo] Location update returned HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Geo] Error updating location to Geo Service: ${e.message}")
            }
        }
    }

    /**
     * Checks if location permission is granted.
     * If yes, fetches GPS coordinates. If no, requests permission using ActivityResultLauncher.
     */
    private fun handleSosClick() {
        if (hasLocationPermission()) {
            fetchLocationAndTriggerSos()
        } else {
            requestLocationPermission()
        }
    }

    /**
     * Checks if either FINE or COARSE location permission is already granted.
     */
    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    /**
     * Requests runtime location permissions.
     */
    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    /**
     * Fetches current GPS location and sends the SOS request to the backend.
     */
    @SuppressLint("MissingPermission")
    private fun fetchLocationAndTriggerSos() {
        setLoadingState(true)
        tvStatus.text = "● Acquiring live GPS coordinates..."
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.primary))

        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                sendSosToBackend(location.latitude, location.longitude)
            } else {
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                    if (lastLoc != null) {
                        sendSosToBackend(lastLoc.latitude, lastLoc.longitude)
                    } else {
                        setLoadingState(false)
                        showErrorDialog(
                            "Unable to acquire GPS location. Please ensure Location/GPS is turned on in your device settings."
                        )
                    }
                }.addOnFailureListener { e ->
                    setLoadingState(false)
                    showErrorDialog("Location error: ${e.localizedMessage}")
                }
            }
        }.addOnFailureListener { e ->
            Log.w(TAG, "getCurrentLocation failed, falling back to lastLocation: ${e.message}")
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                if (lastLoc != null) {
                    sendSosToBackend(lastLoc.latitude, lastLoc.longitude)
                } else {
                    setLoadingState(false)
                    showErrorDialog("Error acquiring GPS location: ${e.localizedMessage}")
                }
            }.addOnFailureListener { err ->
                setLoadingState(false)
                showErrorDialog("Failed to retrieve location: ${err.localizedMessage}")
            }
        }
    }

    /**
     * Sends the SOS incident payload to the SOS Microservice (Port 3002).
     */
    private fun sendSosToBackend(latitude: Double, longitude: Double) {
        val sharedPreferences = getSharedPreferences("surakkha_prefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("auth_token", null)

        if (token.isNullOrEmpty()) {
            setLoadingState(false)
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        tvStatus.text = "● Dispatching SOS to Emergency Responders..."
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.primary))

        lifecycleScope.launch {
            val request = SosRequest(
                latitude = latitude,
                longitude = longitude,
                sosType = "general"
            )

            // Call POST /sos on SOS Microservice (port 3002)
            val result = RetrofitClient.safeApiCall {
                RetrofitClient.sosApiService.triggerSos("Bearer $token", request)
            }

            setLoadingState(false)

            when (result) {
                is NetworkResult.Success -> {
                    val incidentId = result.data.incidentId
                    val successMessage = "SOS Sent! Help is on the way.\nIncident ID: $incidentId"

                    Log.i(TAG, "New SOS incident created successfully: ID #$incidentId")

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("🚨 SOS Alert Dispatched")
                        .setMessage(successMessage)
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .setCancelable(false)
                        .show()

                    tvStatus.text = "● Active SOS: ID #$incidentId | Responders Alerted"
                    tvStatus.setTextColor(Color.parseColor("#2E7D32"))
                }

                is NetworkResult.Error -> {
                    val errorMessage = "Failed to dispatch SOS: ${result.message}\nPlease check your network connection."
                    Log.e(TAG, errorMessage)

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("⚠️ SOS Dispatch Failed")
                        .setMessage(errorMessage)
                        .setPositiveButton("Retry") { _, _ ->
                            fetchLocationAndTriggerSos()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()

                    tvStatus.text = "● SOS Dispatch Failed. Tap SOS to retry."
                    tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.sos_red))
                }

                is NetworkResult.Loading -> {}
            }
        }
    }

    /**
     * Controls the visual loading state of the SOS button and progress spinner.
     */
    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            btnSos.isEnabled = false
            btnSos.text = ""
            sosProgressBar.visibility = View.VISIBLE
        } else {
            btnSos.isEnabled = true
            btnSos.text = "SOS"
            sosProgressBar.visibility = View.GONE
        }
    }

    /**
     * Displays an error alert dialog.
     */
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Location Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
