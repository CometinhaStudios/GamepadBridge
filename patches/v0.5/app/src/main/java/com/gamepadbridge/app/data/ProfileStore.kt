package com.gamepadbridge.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gamepad_bridge_profile")
private val PROFILES_KEY = stringPreferencesKey("profiles_v2")
private val ACTIVE_KEY = stringPreferencesKey("active_profile")
private val LEGACY_PROFILE_KEY = stringPreferencesKey("profile")

class ProfileStore(private val context: Context) {
    val profilesFlow: Flow<Map<String, ControllerProfile>> = context.dataStore.data.map { prefs ->
        readProfiles(prefs)
    }
    val activeProfileNameFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[ACTIVE_KEY] }
    val profileFlow: Flow<ControllerProfile?> = context.dataStore.data.map { prefs ->
        val profiles = decodeProfiles(prefs[PROFILES_KEY].orEmpty())
        val active = prefs[ACTIVE_KEY]
        active?.let { profiles[it] } ?: profiles.values.firstOrNull()
    }

    suspend fun save(profile: ControllerProfile) = save(profile, profile.deviceName)
    suspend fun save(profile: ControllerProfile, name: String) {
        val cleanName = name.trim().ifBlank { profile.deviceName }
        context.dataStore.edit { prefs ->
            val all = decodeProfiles(prefs[PROFILES_KEY].orEmpty()).toMutableMap()
            all[cleanName] = profile
            prefs[PROFILES_KEY] = encodeProfiles(all)
            prefs[ACTIVE_KEY] = cleanName
        }
    }
    suspend fun select(name: String) {
        context.dataStore.edit { prefs ->
            if (decodeProfiles(prefs[PROFILES_KEY].orEmpty()).containsKey(name)) prefs[ACTIVE_KEY] = name
        }
    }
    suspend fun rename(oldName: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        context.dataStore.edit { prefs ->
            val all = decodeProfiles(prefs[PROFILES_KEY].orEmpty()).toMutableMap()
            val profile = all.remove(oldName) ?: return@edit
            all[clean] = profile
            prefs[PROFILES_KEY] = encodeProfiles(all)
            if (prefs[ACTIVE_KEY] == oldName) prefs[ACTIVE_KEY] = clean
        }
    }
    suspend fun delete(name: String) {
        context.dataStore.edit { prefs ->
            val all = decodeProfiles(prefs[PROFILES_KEY].orEmpty()).toMutableMap()
            all.remove(name)
            prefs[PROFILES_KEY] = encodeProfiles(all)
            if (prefs[ACTIVE_KEY] == name) prefs[ACTIVE_KEY] = all.keys.firstOrNull()
        }
    }
    private fun readProfiles(prefs: Preferences): Map<String, ControllerProfile> {
        val stored = prefs[PROFILES_KEY]
        if (stored != null) return decodeProfiles(stored)
        val legacy = prefs[LEGACY_PROFILE_KEY]?.let { ControllerProfile.deserialize(it) }
        return if (legacy == null) emptyMap() else mapOf(legacy.deviceName to legacy)
    }

    private fun encodeProfiles(profiles: Map<String, ControllerProfile>): String =
        profiles.entries.joinToString("\n") { (name, profile) ->
            "\u0024{enc(name)}:\u0024{enc(profile.serialize())}"
        }
    private fun decodeProfiles(raw: String): Map<String, ControllerProfile> {
        val result = linkedMapOf<String, ControllerProfile>()
        raw.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val split = line.indexOf(':')
            if (split <= 0) return@forEach
            runCatching {
                val name = dec(line.substring(0, split))
                val profile = ControllerProfile.deserialize(dec(line.substring(split + 1)))
                if (profile != null) result[name] = profile
            }
        }
        return result
    }
    private fun enc(value: String) = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    private fun dec(value: String) = String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
}
