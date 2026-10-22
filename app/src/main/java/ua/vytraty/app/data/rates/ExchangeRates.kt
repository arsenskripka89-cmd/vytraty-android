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
import ua.vytraty.app.domain.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

/**
 * PrivatBank card rates (https://api.privatbank.ua/p24api/pubinfo?json&exchange&coursid=11),
 * cached in settings. PrivatBank quotes only the dollar and the euro, so the other currencies of
 * [Money.currencies] take the NBU rate. Used wherever amounts in several currencies are added up.
 */
class ExchangeRates(private val settings: SettingsRepository) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches the rates when the cached ones are older than [MAX_AGE_MS] or lack a currency of the pickers.
     * Quietly does nothing when offline.
     */
    suspend fun refreshIfStale(force: Boolean = false) {
        val s = settings.current()
        val complete = decode(s.ratesRaw).keys.containsAll(Money.currencies)
        if (!force && System.currentTimeMillis() - s.ratesUpdated < MAX_AGE_MS && complete) return
        val fetched = runCatching { fetch() }.getOrNull() ?: return
        // a currency that did not come this time (NBU down) keeps its last known rate
        if (fetched.isNotEmpty()) settings.setRates(encode(decode(s.ratesRaw) + fetched), System.currentTimeMillis())
    }

    private suspend fun fetch(): Map<String, Double> = withContext(Dispatchers.IO) {
        val privat = get(URL).jsonArray.mapNotNull { el ->
            val o = el.jsonObject
            if (o["base_ccy"]?.jsonPrimitive?.content != "UAH") return@mapNotNull null
            val ccy = o["ccy"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val buy = o["buy"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val sale = o["sale"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: buy
            ccy to (buy + sale) / 2
        }.toMap()
        if (privat.isEmpty()) return@withContext privat
        val nbu = runCatching {
            get(NBU_URL).jsonArray.mapNotNull { el ->
                val o = el.jsonObject
                val ccy = o["cc"]?.jsonPrimitive?.content?.uppercase() ?: return@mapNotNull null
                val rate = o["rate"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                ccy to rate
            }.toMap()
        }.getOrDefault(emptyMap())
        nbu.filterKeys { it in Money.currencies && it != "UAH" } + privat
    }

    private fun get(url: String) = client.newCall(Request.Builder().url(url).header("User-Agent", "vytraty-android").build())
        .execute().use { resp ->
            if (!resp.isSuccessful) error("$url ${resp.code}")
            json.parseToJsonElement(resp.body!!.string())
        }

    companion object {
        const val URL = "https://api.privatbank.ua/p24api/pubinfo?json&exchange&coursid=11"
        const val NBU_URL = "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?json"
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

        /** Adds up amounts in different currencies as [to]; currencies without a rate are left out and reported. */
        fun sum(values: Iterable<Pair<Long, String>>, to: String, rates: Map<String, Double>): Converted {
            var total = 0L
            val missing = mutableSetOf<String>()
            values.forEach { (minor, currency) ->
                val converted = convert(minor, currency, to, rates)
                if (converted == null) missing += currency.uppercase() else total += converted
            }
            return Converted(total, missing)
        }
    }
}

/** A total in one currency, and the currencies that could not be added to it for lack of a rate. */
data class Converted(val minor: Long, val missing: Set<String> = emptySet())
