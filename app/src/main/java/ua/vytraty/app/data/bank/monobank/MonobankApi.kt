package ua.vytraty.app.data.bank.monobank

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/** Monobank personal API: https://api.monobank.ua/docs/ (token from https://api.monobank.ua). */
interface MonobankApi {
    @GET("personal/client-info")
    suspend fun clientInfo(@Header("X-Token") token: String): MonoClientInfo

    /** Time in unix seconds; range must be ≤ 31 days + 1 hour; 1 request per 60 s. */
    @GET("personal/statement/{account}/{from}/{to}")
    suspend fun statement(
        @Header("X-Token") token: String,
        @Path("account") account: String,
        @Path("from") from: Long,
        @Path("to") to: Long,
    ): List<MonoStatementItem>

    companion object {
        fun create(): MonobankApi {
            val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.monobank.ua/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(MonobankApi::class.java)
        }
    }
}

@Serializable
data class MonoClientInfo(
    val clientId: String = "",
    val name: String = "",
    val accounts: List<MonoAccount> = emptyList(),
)

@Serializable
data class MonoAccount(
    val id: String,
    val balance: Long = 0,
    val creditLimit: Long = 0,
    val type: String = "black",
    val currencyCode: Int = 980,
    val maskedPan: List<String> = emptyList(),
    val iban: String = "",
)

@Serializable
data class MonoStatementItem(
    val id: String,
    val time: Long,
    val description: String = "",
    val mcc: Int = 0,
    val originalMcc: Int = 0,
    val hold: Boolean = false,
    /** Amount in account currency, minor units; negative for expenses. */
    val amount: Long,
    val operationAmount: Long = 0,
    val currencyCode: Int = 980,
    val commissionRate: Long = 0,
    val cashbackAmount: Long = 0,
    val balance: Long = 0,
    val comment: String = "",
    @SerialName("counterName") val counterName: String = "",
)

object MonoCurrency {
    fun code(numeric: Int): String = when (numeric) {
        980 -> "UAH"
        840 -> "USD"
        978 -> "EUR"
        985 -> "PLN"
        826 -> "GBP"
        else -> numeric.toString()
    }
}
