package com.gamepadbridge.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gamepad_bridge_profile")
private val PROFILE_KEY = stringPreferencesKey("profile")

class ProfileStore(private val context: Context) {
    val profileFlow: Flow<ControllerProfile?> = context.dataStore.data.map { prefs ->
        prefs[PROFILE_KEY]?.let { ControllerProfile.deserialize(it) }
    }

    suspend fun save(profile: ControllerProfile) {
        context.dataStore.edit { it[PROFILE_KEY] = profile.serialize() }
    }
}
