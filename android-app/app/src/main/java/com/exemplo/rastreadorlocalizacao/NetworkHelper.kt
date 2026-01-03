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
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
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
        val timestamp_envio: String,
        val localizacoes: List<LocationData>
    )
    
    fun enviarLocalizacoes(
        serverUrl: String,
        deviceId: String,
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
                timestamp_envio = dateFormat.format(Date()),
                localizacoes = locationDataList
            )
            
            val json = gson.toJson(envioData)
            val requestBody = json.toRequestBody("application/json".toMediaType())
            
            val url = if (serverUrl.endsWith("/")) {
                "${serverUrl}api/localizacoes"
            } else {
                "$serverUrl/api/localizacoes"
            }
            
            val request = Request.Builder()
                .url(url)
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

