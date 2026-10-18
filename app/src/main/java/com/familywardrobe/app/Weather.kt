package com.familywardrobe.app

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class WeatherCity(val name: String, val latitude: Double, val longitude: Double)
data class ForecastDay(val date: String, val low: Double, val high: Double, val rain: Int?)
data class Forecast(val city: WeatherCity, val fetchedAt: Long, val currentDate: String, val feelsLike: Double,
    val days: List<ForecastDay>) {
    fun mode(date: String): String? = if (date == currentDate && System.currentTimeMillis() - fetchedAt < 6 * 60 * 60 * 1000L)
        RoadmapRules.weatherMode(feelsLike) else if (System.currentTimeMillis() - fetchedAt < 24 * 60 * 60 * 1000L)
            days.find { it.date == date }?.let { RoadmapRules.weatherMode((it.low + it.high) / 2) } else null
}

/** Optional city search; no device location permission. Cache and settings are account-private. */
class WeatherController(context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("weather", 0)
    val enabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val city = MutableStateFlow(readCity())
    val forecast = MutableStateFlow(runCatching { prefs.getString("cache", null)?.let { decode(JSONObject(it)) } }.getOrNull())
    val loading = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    val results = MutableStateFlow<List<WeatherCity>>(emptyList())
    private var job: Job? = null
    private var generation = 0
    private fun readCity(): WeatherCity? = runCatching {
        prefs.getString("city", null)?.let { val o = JSONObject(it); WeatherCity(o.getString("name"), o.getDouble("lat"), o.getDouble("lon")) }
    }.getOrNull()
    fun setEnabled(value: Boolean) {
        enabled.value = value; prefs.edit().putBoolean("enabled", value).apply()
        if (!value) { generation++; job?.cancel(); loading.value = false }
        else if (city.value != null) refresh()
    }
    fun search(query: String) {
        val name = query.trim(); if (name.length !in 2..100) return
        job?.cancel(); val token = ++generation; results.value = emptyList(); failed.value = false; loading.value = true
        job = scope.launch {
            try {
                val lang = Locale.getDefault().language.takeIf { it in setOf("uk", "en", "pl") } ?: "en"
                val response = request("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(name, "UTF-8")}&count=5&language=$lang&format=json")
                val entries = response.optJSONArray("results") ?: JSONArray()
                results.value = (0 until minOf(entries.length(), 5)).map { index ->
                    val o = entries.getJSONObject(index)
                    val lat = o.getDouble("latitude"); val lon = o.getDouble("longitude")
                    require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0)
                    WeatherCity(listOf(o.getString("name"), o.optString("admin1"), o.optString("country"))
                        .filter { it.isNotBlank() }.distinct().joinToString(", ").take(250), lat, lon)
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failed.value = true }
            finally { if (generation == token) loading.value = false }
        }
    }
    fun select(value: WeatherCity) {
        city.value = value; forecast.value = null; results.value = emptyList()
        prefs.edit().putString("city", JSONObject().put("name", value.name).put("lat", value.latitude).put("lon", value.longitude).toString()).remove("cache").apply()
        setEnabled(true)
    }
    fun refreshIfOld() {
        if (enabled.value && city.value != null && (forecast.value == null || System.currentTimeMillis() - forecast.value!!.fetchedAt > 60 * 60 * 1000L)) refresh()
    }
    fun refresh() {
        val selected = city.value ?: return
        job?.cancel(); val token = ++generation; loading.value = true; failed.value = false
        job = scope.launch {
            try {
                val json = request("https://api.open-meteo.com/v1/forecast?latitude=${selected.latitude}&longitude=${selected.longitude}&current=apparent_temperature&daily=temperature_2m_min,temperature_2m_max,precipitation_probability_max&timezone=auto&forecast_days=7")
                json.put("city", JSONObject().put("name", selected.name).put("lat", selected.latitude).put("lon", selected.longitude))
                    .put("fetchedAt", System.currentTimeMillis())
                val value = decode(json)
                ensureActive()
                if (city.value == selected && enabled.value) { forecast.value = value; prefs.edit().putString("cache", json.toString()).apply() }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failed.value = true }
            finally { if (generation == token) loading.value = false }
        }
    }
    private fun decode(o: JSONObject): Forecast {
        val c = o.getJSONObject("city"); val selected = WeatherCity(c.getString("name"), c.getDouble("lat"), c.getDouble("lon"))
        val current = o.getJSONObject("current"); val apparent = current.getDouble("apparent_temperature")
        require(apparent.isFinite() && apparent in -90.0..65.0)
        val currentDate = current.getString("time").take(10); require(RoadmapRules.validDate(currentDate))
        val daily = o.getJSONObject("daily"); val dates = daily.getJSONArray("time")
        val low = daily.getJSONArray("temperature_2m_min"); val high = daily.getJSONArray("temperature_2m_max")
        val rain = daily.optJSONArray("precipitation_probability_max")
        val days = (0 until minOf(dates.length(), 7)).map { index ->
            val date = dates.getString(index); val l = low.getDouble(index); val h = high.getDouble(index)
            require(RoadmapRules.validDate(date) && l.isFinite() && h.isFinite() && l in -90.0..65.0 && h in l..65.0)
            ForecastDay(date, l, h, rain?.takeIf { !it.isNull(index) }?.optInt(index)?.takeIf { it in 0..100 })
        }
        require(days.isNotEmpty())
        return Forecast(selected, o.getLong("fetchedAt"), currentDate, apparent, days)
    }
    private suspend fun request(address: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(address).openConnection() as HttpsURLConnection
        connection.connectTimeout = 8_000; connection.readTimeout = 10_000; connection.instanceFollowRedirects = false
        try {
            ensureActive(); require(connection.responseCode == 200)
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(8192)
                while (true) { ensureActive(); val size = input.read(chunk); if (size < 0) break
                    require(bytes.size() + size <= 1_048_576); bytes.write(chunk, 0, size) }
            }
            ensureActive(); JSONObject(bytes.toString("UTF-8")).also { require(!it.optBoolean("error", false)) }
        } finally { connection.disconnect() }
    }
}
