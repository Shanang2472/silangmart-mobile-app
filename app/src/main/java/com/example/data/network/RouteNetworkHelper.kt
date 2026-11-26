package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LocationSearchResult(
    val lat: Double,
    val lng: Double,
    val displayName: String,
    val barangay: String
)

object RouteNetworkHelper {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchDrivingRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): List<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        runCatching {
            val urlStr = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$endLng,$endLat?overview=full&geometries=geojson"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) return@runCatching emptyList()
            val body = conn.inputStream.bufferedReader().readText()
            val root = json.parseToJsonElement(body).jsonObject
            val routes = root["routes"]?.jsonArray ?: return@runCatching emptyList()
            if (routes.isEmpty()) return@runCatching emptyList()
            val coords = routes[0].jsonObject["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
                ?: return@runCatching emptyList()
            coords.mapNotNull { el ->
                val pair = el.jsonArray
                if (pair.size >= 2) {
                    val lng = pair[0].jsonPrimitive.content.toDoubleOrNull()
                    val lat = pair[1].jsonPrimitive.content.toDoubleOrNull()
                    if (lat != null && lng != null) lat to lng else null
                } else null
            }
        }.getOrDefault(emptyList())
    }

    suspend fun searchLocations(query: String): List<LocationSearchResult> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val urlStr = "https://photon.komoot.io/api/?q=$encoded&limit=6&bbox=120.90,14.17,121.05,14.28"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept-Language", "en")
            }
            if (conn.responseCode != 200) return@runCatching emptyList()
            val body = conn.inputStream.bufferedReader().readText()
            val features = json.parseToJsonElement(body).jsonObject["features"]?.jsonArray ?: return@runCatching emptyList()
            features.mapNotNull { feature ->
                val obj = feature.jsonObject
                val coords = obj["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
                val props = obj["properties"]?.jsonObject ?: return@mapNotNull null
                val lng = coords.getOrNull(0)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val lat = coords.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val name = props["name"]?.jsonPrimitive?.content.orEmpty()
                val district = props["district"]?.jsonPrimitive?.content.orEmpty()
                val locality = props["locality"]?.jsonPrimitive?.content.orEmpty()
                val city = props["city"]?.jsonPrimitive?.content.orEmpty()
                val brgyRaw = district.ifBlank { locality.ifBlank { city.ifBlank { name } } }
                val barangay = if (brgyRaw.isNotBlank()) "Brgy. ${brgyRaw.replace(Regex("^Brgy\\.?\\s*", RegexOption.IGNORE_CASE), "")}" else ""
                val display = listOf(name, district, city).filter { it.isNotBlank() }.joinToString(", ")
                LocationSearchResult(lat, lng, display.ifBlank { "${"%.5f".format(lat)}, ${"%.5f".format(lng)}" }, barangay)
            }
        }.getOrDefault(emptyList())
    }
}
