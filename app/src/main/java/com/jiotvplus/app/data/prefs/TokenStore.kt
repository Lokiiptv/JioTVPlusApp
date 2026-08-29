package com.jiotvplus.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_SSO_TOKEN = stringPreferencesKey("sso_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_SUBSCRIBER_ID = stringPreferencesKey("subscriber_id")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_PHONE_NUMBER = stringPreferencesKey("phone_number")
        private val KEY_IDENTIFIER = stringPreferencesKey("identifier")
        private val KEY_LANGUAGES = stringPreferencesKey("selected_languages")
        private val KEY_RECENT_CHANNELS = stringPreferencesKey("recent_channels")
    }

    suspend fun saveAuthSession(
        authToken: String,
        refreshToken: String,
        ssoToken: String,
        userId: String,
        subscriberId: String,
        deviceId: String,
        phoneNumber: String
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = authToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_SSO_TOKEN] = ssoToken
            prefs[KEY_USER_ID] = userId
            prefs[KEY_SUBSCRIBER_ID] = subscriberId
            prefs[KEY_DEVICE_ID] = deviceId
            prefs[KEY_PHONE_NUMBER] = phoneNumber
        }
    }

    suspend fun saveIdentifier(identifier: String) {
        dataStore.edit { it[KEY_IDENTIFIER] = identifier }
    }

    suspend fun getAuthToken(): String? =
        dataStore.data.first()[KEY_AUTH_TOKEN]?.takeIf { it.isNotBlank() }

    suspend fun getRefreshToken(): String? =
        dataStore.data.first()[KEY_REFRESH_TOKEN]?.takeIf { it.isNotBlank() }

    suspend fun getSsoToken(): String? =
        dataStore.data.first()[KEY_SSO_TOKEN]?.takeIf { it.isNotBlank() }

    suspend fun getUserId(): String? =
        dataStore.data.first()[KEY_USER_ID]?.takeIf { it.isNotBlank() }

    suspend fun getSubscriberId(): String? =
        dataStore.data.first()[KEY_SUBSCRIBER_ID]?.takeIf { it.isNotBlank() }

    suspend fun getDeviceId(): String? =
        dataStore.data.first()[KEY_DEVICE_ID]?.takeIf { it.isNotBlank() }

    suspend fun getPhoneNumber(): String? =
        dataStore.data.first()[KEY_PHONE_NUMBER]?.takeIf { it.isNotBlank() }

    suspend fun getIdentifier(): String? =
        dataStore.data.first()[KEY_IDENTIFIER]?.takeIf { it.isNotBlank() }

    suspend fun clearSession() {
        dataStore.edit { it.clear() }
    }

    suspend fun saveLanguages(languages: Set<String>) {
        dataStore.edit { it[KEY_LANGUAGES] = languages.joinToString(",") }
    }

    suspend fun getLanguages(): Set<String> =
        dataStore.data.first()[KEY_LANGUAGES]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    suspend fun saveRecentChannels(contentIds: List<String>) {
        dataStore.edit { it[KEY_RECENT_CHANNELS] = contentIds.take(20).joinToString(",") }
    }

    suspend fun getRecentChannels(): List<String> =
        dataStore.data.first()[KEY_RECENT_CHANNELS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { prefs ->
        !prefs[KEY_AUTH_TOKEN].isNullOrBlank()
    }
}
