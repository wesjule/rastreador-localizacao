package com.exemplo.rastreadorlocalizacao

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var editDriverName: EditText

    private lateinit var database: LocationDatabase
    private var isServiceRunning = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "LocationAppPrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DRIVER_NAME = "driver_name"
        private const val DEFAULT_SERVER_URL = "https://anoteia.com.br"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = LocationDatabase.getDatabase(this)

        initViews()
        loadSettings()
        checkPermissions()
        updateUI()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        locCountText = findViewById(R.id.locCountText)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnEnviar = findViewById(R.id.btnEnviar)
        editServerUrl = findViewById(R.id.editServerUrl)
        editDriverName = findViewById(R.id.editDriverName)

        btnStartStop.setOnClickListener {
            if (isServiceRunning) {
                stopLocationService()
            } else {
                if (checkPermissions()) {
                    saveSettings()
                    startLocationService()
                }
            }
        }

        btnEnviar.setOnClickListener {
            saveSettings()
            enviarAgora()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        editServerUrl.setText(prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL)
        editDriverName.setText(prefs.getString(KEY_DRIVER_NAME, "") ?: "")
    }

    private fun saveSettings() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, editServerUrl.text.toString().trim())
            .putString(KEY_DRIVER_NAME, editDriverName.text.toString().trim())
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
        Toast.makeText(this, "Rastreando e enviando automaticamente", Toast.LENGTH_SHORT).show()
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

            locCountText.text = "Pontos no buffer (ainda não enviados): $count"

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

    // Envio manual ("Enviar agora"): mesmo caminho do automático (lote + apaga por id), sem trava de WiFi.
    private fun enviarAgora() {
        val serverUrl = editServerUrl.text.toString().trim()

        if (serverUrl.isBlank()) {
            Toast.makeText(this, "Digite o endereço do servidor", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                btnEnviar.isEnabled = false
                btnEnviar.text = "Enviando..."

                val deviceId = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                val name = editDriverName.text.toString().trim()

                val sent = withContext(Dispatchers.IO) {
                    val batch = database.locationDao().getOldestBatch(500)
                    if (batch.isEmpty()) return@withContext 0
                    val ok = NetworkHelper.enviarLocalizacoes(serverUrl, deviceId, name, batch)
                    if (ok) {
                        database.locationDao().deleteByIds(batch.map { it.id })
                        batch.size
                    } else {
                        -1
                    }
                }

                when {
                    sent > 0 -> Toast.makeText(this@MainActivity, "Enviado: $sent ponto(s)", Toast.LENGTH_LONG).show()
                    sent == 0 -> Toast.makeText(this@MainActivity, "Nada no buffer — tudo já foi enviado", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(this@MainActivity, "Falha ao enviar (verifique o endereço/sinal)", Toast.LENGTH_LONG).show()
                }
                updateUI()

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Erro: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                btnEnviar.isEnabled = true
                btnEnviar.text = "Enviar agora"
            }
        }
    }
}
