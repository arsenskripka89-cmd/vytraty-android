package ua.vytraty.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val captureEnabled: Boolean = true,
    val enabledPackages: Set<String> = emptySet(), // empty = all supported
    val notifyUncategorized: Boolean = true,
    /** record a captured payment only when a rule (or a card number) points it to a wallet */
    val onlyMatchedWallet: Boolean = true,
    val monobankToken: String = "",
    val monobankLastSync: Long = 0,
    val monobankAutoSync: Boolean = true,
    val mainCurrency: String = "UAH",
    val onboardingDone: Boolean = false,
    /** PrivatBank rates, "USD=44.6;EUR=51.3" in UAH per unit, and when they were fetched */
    val ratesRaw: String = "",
    val ratesUpdated: Long = 0,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val captureEnabled = booleanPreferencesKey("capture_enabled")
        val enabledPackages = stringSetPreferencesKey("enabled_packages")
        val notifyUncategorized = booleanPreferencesKey("notify_uncategorized")
        val onlyMatchedWallet = booleanPreferencesKey("only_matched_wallet")
        val ratesRaw = stringPreferencesKey("rates_raw")
        val ratesUpdated = longPreferencesKey("rates_updated")
        val monobankToken = stringPreferencesKey("monobank_token")
        val monobankLastSync = longPreferencesKey("monobank_last_sync")
        val monobankAutoSync = booleanPreferencesKey("monobank_auto_sync")
        val mainCurrency = stringPreferencesKey("main_currency")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            captureEnabled = p[Keys.captureEnabled] ?: true,
            enabledPackages = p[Keys.enabledPackages] ?: emptySet(),
            notifyUncategorized = p[Keys.notifyUncategorized] ?: true,
            onlyMatchedWallet = p[Keys.onlyMatchedWallet] ?: true,
            ratesRaw = p[Keys.ratesRaw] ?: "",
            ratesUpdated = p[Keys.ratesUpdated] ?: 0L,
            monobankToken = p[Keys.monobankToken] ?: "",
            monobankLastSync = p[Keys.monobankLastSync] ?: 0L,
            monobankAutoSync = p[Keys.monobankAutoSync] ?: true,
            mainCurrency = p[Keys.mainCurrency] ?: "UAH",
            onboardingDone = p[Keys.onboardingDone] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setCaptureEnabled(v: Boolean) = context.dataStore.edit { it[Keys.captureEnabled] = v }
    suspend fun setEnabledPackages(v: Set<String>) = context.dataStore.edit { it[Keys.enabledPackages] = v }
    suspend fun setNotifyUncategorized(v: Boolean) = context.dataStore.edit { it[Keys.notifyUncategorized] = v }
    suspend fun setMonobankToken(v: String) = context.dataStore.edit { it[Keys.monobankToken] = v.trim() }
    suspend fun setMonobankLastSync(v: Long) = context.dataStore.edit { it[Keys.monobankLastSync] = v }
    suspend fun setMonobankAutoSync(v: Boolean) = context.dataStore.edit { it[Keys.monobankAutoSync] = v }
    suspend fun setMainCurrency(v: String) = context.dataStore.edit { it[Keys.mainCurrency] = v }
    suspend fun setOnlyMatchedWallet(v: Boolean) = context.dataStore.edit { it[Keys.onlyMatchedWallet] = v }
    suspend fun setRates(raw: String, updated: Long) = context.dataStore.edit {
        it[Keys.ratesRaw] = raw
        it[Keys.ratesUpdated] = updated
    }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[Keys.onboardingDone] = v }
}
