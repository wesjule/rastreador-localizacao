package com.exemplo.rastreadorlocalizacao

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var database: LocationDatabase
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sending = AtomicBoolean(false)

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val UPDATE_INTERVAL = 4000L   // 4s — pontos mais juntos p/ desenhar a curva do trajeto
        private const val FASTEST_INTERVAL = 2000L  // aceita até 2s
        private const val SEND_INTERVAL = 3000L     // tenta esvaziar o buffer a cada 3s (tempo real)
        private const val BATCH_SIZE = 50
        private const val PREFS_NAME = "LocationAppPrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DRIVER_NAME = "driver_name"
    }

    override fun onCreate() {
        super.onCreate()
        database = LocationDatabase.getDatabase(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupLocationCallback()
        startForeground(NOTIFICATION_ID, createNotification())
        startLocationUpdates()
        startAutoSender()
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, LocationApp.CHANNEL_ID)
            .setContentTitle("Rastreador do Entregador")
            .setContentText("Enviando sua localização em tempo real...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    saveLocation(
                        location.latitude,
                        location.longitude,
                        location.accuracy,
                        location.altitude,
                        location.speed
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun saveLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        altitude: Double,
        speed: Float
    ) {
        serviceScope.launch {
            val location = LocationEntity(
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                altitude = altitude,
                speed = speed,
                timestamp = Date()
            )
            database.locationDao().insert(location)
        }
    }

    // Envia o buffer pro servidor em TEMPO REAL e apaga só o que foi confirmado (por id). Sem sinal:
    // o envio falha, os pontos FICAM no banco e vão na próxima tentativa (retry automático/offline).
    private fun startAutoSender() {
        serviceScope.launch {
            while (isActive) {
                trySendBatch()
                delay(SEND_INTERVAL)
            }
        }
    }

    private suspend fun trySendBatch() {
        if (!sending.compareAndSet(false, true)) return // já tem um envio em andamento
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val serverUrl = prefs.getString(KEY_SERVER_URL, "") ?: ""
            if (serverUrl.isBlank()) return
            val name = prefs.getString(KEY_DRIVER_NAME, "") ?: ""
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: return
            val batch = database.locationDao().getOldestBatch(BATCH_SIZE)
            if (batch.isEmpty()) return
            val ok = NetworkHelper.enviarLocalizacoes(serverUrl, deviceId, name, batch)
            if (ok) database.locationDao().deleteByIds(batch.map { it.id })
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            sending.set(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel() // encerra o auto-sender
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
