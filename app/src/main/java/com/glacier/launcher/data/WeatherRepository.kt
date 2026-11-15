package com.glacier.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Weather from Open-Meteo (https://open-meteo.com): no API key, no account.
 * Endpoints used:
 *   https://geocoding-api.open-meteo.com/v1/search   (city name -> lat/lon)
 *   https://api.open-meteo.com/v1/forecast           (current + daily min/max)
 * Only the city name you typed is sent. The last result is cached so the widget works offline.
 */
class WeatherRepository {
    data class Data(val query: String, val city: String, val tempC: Double, val code: Int, val hi: Double, val lo: Double, val updated: Long)

    sealed interface State {
        data object NoCity : State
        data object Loading : State
        data class Ready(val data: Data) : State
        data class Error(val message: String, val cached: Data?) : State
    }

    private val _state = MutableStateFlow<State>(State.NoCity)
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun refresh(city: String, cacheJson: String, force: Boolean, saveCache: suspend (String) -> Unit) {
        val q = city.trim()
        if (q.isEmpty()) { _state.value = State.NoCity; return }
        val cached = parse(cacheJson)?.takeIf { it.query.equals(q, ignoreCase = true) }
        val fresh = cached != null && System.currentTimeMillis() - cached.updated < 30 * 60_000L
        if (!force && fresh) { _state.value = State.Ready(cached!!); return }
        if (cached == null) _state.value = State.Loading else _state.value = State.Ready(cached)
        try {
            val d = withContext(Dispatchers.IO) { fetch(q) }
            saveCache(serialize(d))
            _state.value = State.Ready(d)
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "Network error", cached)
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun fetch(query: String): Data {
        val geo = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(query, "UTF-8")))
        val hit = geo.optJSONArray("results")?.optJSONObject(0) ?: throw IllegalStateException("City not found")
        val lat = hit.getDouble("latitude")
        val lon = hit.getDouble("longitude")
        val name = hit.optString("name", query)
        val w = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
            )
        )
        val cur = w.getJSONObject("current")
        val daily = w.getJSONObject("daily")
        return Data(
            query = query, city = name,
            tempC = cur.getDouble("temperature_2m"),
            code = cur.getInt("weather_code"),
            hi = daily.getJSONArray("temperature_2m_max").getDouble(0),
            lo = daily.getJSONArray("temperature_2m_min").getDouble(0),
            updated = System.currentTimeMillis()
        )
    }

    private fun serialize(d: Data) = JSONObject().apply {
        put("q", d.query); put("c", d.city); put("t", d.tempC); put("k", d.code)
        put("h", d.hi); put("l", d.lo); put("u", d.updated)
    }.toString()

    private fun parse(s: String): Data? = try {
        if (s.isBlank()) null else JSONObject(s).let {
            Data(it.getString("q"), it.getString("c"), it.getDouble("t"), it.getInt("k"), it.getDouble("h"), it.getDouble("l"), it.getLong("u"))
        }
    } catch (e: Exception) { null }

    companion object {
        /** WMO weather interpretation codes -> short text. */
        fun describe(code: Int): String = when (code) {
            0 -> "Clear"
            1, 2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 65, 66, 67 -> "Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Showers"
            85, 86 -> "Snow showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "—"
        }
    }
}
