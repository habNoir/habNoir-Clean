package xyz.habnoir.clean.data.prefs

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
import kotlinx.coroutines.flow.map
import xyz.habnoir.clean.model.EngineTier

private val Context.habNoirDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "habnoir_clean_prefs"
)

data class UserPreferencesSnapshot(
    val whitelistedPackages: Set<String> = emptySet(),
    val minCacheThresholdBytes: Long = DEFAULT_THRESHOLD_BYTES,
    val preferredEngineTier: EngineTier = EngineTier.ACCESSIBILITY_STAGED,
    val totalFreedBytes: Long = 0L,
    val lastCleanEpochMs: Long = 0L,
    val onboardingCompleted: Boolean = false,
    val reminderEnabled: Boolean = false
) {
    companion object {
        const val THRESHOLD_1_MB = 1_000_000L
        const val DEFAULT_THRESHOLD_BYTES = 5_000_000L
        const val THRESHOLD_10_MB = 10_000_000L
    }
}

class UserPrefsRepository(context: Context) {
    private val dataStore = context.applicationContext.habNoirDataStore

    private object Keys {
        val WHITELISTED_PACKAGES = stringSetPreferencesKey("whitelisted_packages")
        val MIN_CACHE_THRESHOLD = longPreferencesKey("min_cache_threshold_bytes")
        val ENGINE_MODE = stringPreferencesKey("preferred_engine_mode")
        val TOTAL_FREED_BYTES = longPreferencesKey("total_freed_bytes")
        val LAST_CLEAN_EPOCH_MS = longPreferencesKey("last_clean_epoch_ms")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
    }

    val preferencesFlow: Flow<UserPreferencesSnapshot> = dataStore.data.map { prefs ->
        val rawEngine = prefs[Keys.ENGINE_MODE] ?: EngineTier.ACCESSIBILITY_STAGED.name
        val engineTier = runCatching { EngineTier.valueOf(rawEngine) }
            .getOrDefault(EngineTier.ACCESSIBILITY_STAGED)

        UserPreferencesSnapshot(
            whitelistedPackages = prefs[Keys.WHITELISTED_PACKAGES] ?: emptySet(),
            minCacheThresholdBytes = prefs[Keys.MIN_CACHE_THRESHOLD]
                ?: UserPreferencesSnapshot.DEFAULT_THRESHOLD_BYTES,
            preferredEngineTier = engineTier,
            totalFreedBytes = prefs[Keys.TOTAL_FREED_BYTES] ?: 0L,
            lastCleanEpochMs = prefs[Keys.LAST_CLEAN_EPOCH_MS] ?: 0L,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: false
        )
    }

    suspend fun toggleWhitelist(packageName: String): Boolean {
        var nowWhitelisted = false
        dataStore.edit { prefs ->
            val current = prefs[Keys.WHITELISTED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            nowWhitelisted = if (current.contains(packageName)) {
                current.remove(packageName)
                false
            } else {
                current.add(packageName)
                true
            }
            prefs[Keys.WHITELISTED_PACKAGES] = current
        }
        return nowWhitelisted
    }

    suspend fun setMinCacheThresholdBytes(bytes: Long) {
        dataStore.edit { it[Keys.MIN_CACHE_THRESHOLD] = bytes }
    }

    suspend fun setPreferredEngineTier(tier: EngineTier) {
        dataStore.edit { it[Keys.ENGINE_MODE] = tier.name }
    }

    suspend fun addFreedBytes(deltaBytes: Long, timestampMs: Long) {
        if (deltaBytes <= 0L) return
        dataStore.edit { prefs ->
            val existing = prefs[Keys.TOTAL_FREED_BYTES] ?: 0L
            prefs[Keys.TOTAL_FREED_BYTES] = existing + deltaBytes
            prefs[Keys.LAST_CLEAN_EPOCH_MS] = timestampMs
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.REMINDER_ENABLED] = enabled }
    }
}
