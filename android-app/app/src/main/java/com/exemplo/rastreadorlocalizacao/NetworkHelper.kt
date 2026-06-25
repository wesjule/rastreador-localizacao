package com.exemplo.rastreadorlocalizacao

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object NetworkHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Datas SEMPRE em UTC: o backend grava em UTC e calcula "há Xs"/replay a partir disso.
    // Mandar a hora LOCAL (-03) bagunçava o relógio do painel.
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val altitude: Double,
        val speed: Float,
        val timestamp: String
    )

    data class EnvioData(
        val dispositivo_id: String,
        val nome: String?,
        val timestamp_envio: String,
        val localizacoes: List<LocationData>
    )

    // Monta a URL do ingest a partir do endereço base (ex.: https://anoteia.com.br ->
    // https://anoteia.com.br/api/driver_tracking.php?action=ingest).
    private fun ingestUrl(serverUrl: String): String {
        val base = serverUrl.trim().trimEnd('/')
        return when {
            base.contains("driver_tracking.php") && base.contains("action=") -> base
            base.contains("driver_tracking.php") -> "$base?action=ingest"
            else -> "$base/api/driver_tracking.php?action=ingest"
        }
    }

    fun enviarLocalizacoes(
        serverUrl: String,
        deviceId: String,
        deviceName: String,
        locations: List<LocationEntity>
    ): Boolean {
        try {
            val locationDataList = locations.map { loc ->
                LocationData(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracy = loc.accuracy,
                    altitude = loc.altitude,
                    speed = loc.speed,
                    timestamp = dateFormat.format(loc.timestamp)
                )
            }

            val envioData = EnvioData(
                dispositivo_id = deviceId,
                nome = deviceName.ifBlank { null },
                timestamp_envio = dateFormat.format(Date()),
                localizacoes = locationDataList
            )

            val json = gson.toJson(envioData)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(ingestUrl(serverUrl))
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                return response.isSuccessful
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
