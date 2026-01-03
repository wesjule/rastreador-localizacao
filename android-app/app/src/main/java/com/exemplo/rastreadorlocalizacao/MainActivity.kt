package com.exemplo.rastreadorlocalizacao

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var locCountText: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnEnviar: Button
    private lateinit var editServerUrl: EditText
    
    private lateinit var database: LocationDatabase
    private var isServiceRunning = false
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "LocationAppPrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://192.168.1.100:5000"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        database = LocationDatabase.getDatabase(this)
        
        initViews()
        loadServerUrl()
        checkPermissions()
        updateUI()
    }
    
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        locCountText = findViewById(R.id.locCountText)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnEnviar = findViewById(R.id.btnEnviar)
        editServerUrl = findViewById(R.id.editServerUrl)
        
        btnStartStop.setOnClickListener {
            if (isServiceRunning) {
                stopLocationService()
            } else {
                if (checkPermissions()) {
                    startLocationService()
                }
            }
        }
        
        btnEnviar.setOnClickListener {
            saveServerUrl()
            enviarLocalizacoes()
        }
    }
    
    private fun loadServerUrl() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        editServerUrl.setText(url)
    }
    
    private fun saveServerUrl() {
        val url = editServerUrl.text.toString()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, url)
            .apply()
    }
    
    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        return if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissões concedidas!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Permissões necessárias para rastreamento de localização",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
        updateUI()
    }
    
    private fun stopLocationService() {
        val intent = Intent(this, LocationService::class.java)
        stopService(intent)
        isServiceRunning = false
        updateUI()
    }
    
    private fun updateUI() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                database.locationDao().getLocationCount()
            }
            
            locCountText.text = "Localizações salvas: $count"
            
            if (isServiceRunning) {
                statusText.text = "Status: Rastreando"
                statusText.setTextColor(getColor(android.R.color.holo_green_dark))
                btnStartStop.text = "Parar Rastreamento"
            } else {
                statusText.text = "Status: Parado"
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                btnStartStop.text = "Iniciar Rastreamento"
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
    
    private fun enviarLocalizacoes() {
        if (!isConnectedToWifi()) {
            AlertDialog.Builder(this)
                .setTitle("Sem WiFi")
                .setMessage("Você não está conectado ao WiFi. Deseja enviar mesmo assim usando dados móveis?")
                .setPositiveButton("Sim") { _, _ ->
                    realizarEnvio()
                }
                .setNegativeButton("Não", null)
                .show()
            return
        }
        
        realizarEnvio()
    }
    
    private fun realizarEnvio() {
        val serverUrl = editServerUrl.text.toString()
        
        if (serverUrl.isBlank()) {
            Toast.makeText(this, "Digite o endereço do servidor", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                btnEnviar.isEnabled = false
                btnEnviar.text = "Enviando..."
                
                val locations = withContext(Dispatchers.IO) {
                    database.locationDao().getAllLocations()
                }
                
                if (locations.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Nenhuma localização para enviar",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                
                val deviceId = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                
                val result = withContext(Dispatchers.IO) {
                    NetworkHelper.enviarLocalizacoes(serverUrl, deviceId, locations)
                }
                
                if (result) {
                    withContext(Dispatchers.IO) {
                        database.locationDao().deleteAll()
                    }
                    Toast.makeText(
                        this@MainActivity,
                        "Localizações enviadas com sucesso!",
                        Toast.LENGTH_LONG
                    ).show()
                    updateUI()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Erro ao enviar localizações",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Erro: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                btnEnviar.isEnabled = true
                btnEnviar.text = "Enviar para Servidor"
            }
        }
    }
    
    private fun isConnectedToWifi(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }
}

