package ua.vytraty.app.data.rates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.vytraty.app.data.prefs.SettingsRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

/**
 * PrivatBank card rates (https://api.privatbank.ua/p24api/pubinfo?json&exchange&coursid=11),
 * cached in settings. Used by the dashboard to show every wallet in one currency.
 */
class ExchangeRates(private val settings: SettingsRepository) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Fetches the rates when the cached ones are older than [MAX_AGE_MS]. Quietly does nothing when offline. */
    suspend fun refreshIfStale(force: Boolean = false) {
        val s = settings.current()
        if (!force && System.currentTimeMillis() - s.ratesUpdated < MAX_AGE_MS && s.ratesRaw.isNotBlank()) return
        val fetched = runCatching { fetch() }.getOrNull() ?: return
        if (fetched.isNotEmpty()) settings.setRates(encode(fetched), System.currentTimeMillis())
    }

    private suspend fun fetch(): Map<String, Double> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(URL).header("User-Agent", "vytraty-android").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("PrivatBank ${resp.code}")
            json.parseToJsonElement(resp.body!!.string()).jsonArray.mapNotNull { el ->
                val o = el.jsonObject
                if (o["base_ccy"]?.jsonPrimitive?.content != "UAH") return@mapNotNull null
                val ccy = o["ccy"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val buy = o["buy"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val sale = o["sale"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: buy
                ccy to (buy + sale) / 2
            }.toMap()
        }
    }

    companion object {
        const val URL = "https://api.privatbank.ua/p24api/pubinfo?json&exchange&coursid=11"
        val MAX_AGE_MS: Long = TimeUnit.HOURS.toMillis(6)

        fun encode(rates: Map<String, Double>) = rates.entries.joinToString(";") { "${it.key}=${it.value}" }

        /** "USD=44.6;EUR=51.3" → map. UAH is always 1. */
        fun decode(raw: String): Map<String, Double> = buildMap {
            put("UAH", 1.0)
            raw.split(';').forEach { part ->
                val (code, value) = part.split('=').takeIf { it.size == 2 } ?: return@forEach
                value.toDoubleOrNull()?.let { put(code.uppercase(), it) }
            }
        }

        /** Converts minor units between currencies; null when a rate is missing. */
        fun convert(minor: Long, from: String, to: String, rates: Map<String, Double>): Long? {
            if (from.equals(to, true)) return minor
            val f = rates[from.uppercase()] ?: return null
            val t = rates[to.uppercase()] ?: return null
            return BigDecimal(minor).multiply(BigDecimal(f)).divide(BigDecimal(t), 0, RoundingMode.HALF_UP).toLong()
        }
    }
}
