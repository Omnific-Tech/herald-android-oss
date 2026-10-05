package dev.omnific.herald.client

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The device's own credential and Firebase installation identity.
 *
 * The token is stored in private shared preferences, readable only by this
 * application's UID. It is not additionally encrypted: on a device where
 * another process can read this file, a stored key would be readable too.
 */
class DeviceStore internal constructor(private val preferences: SharedPreferences) {

    constructor(context: Context) :
        this(context.applicationContext.getSharedPreferences("herald.device", Context.MODE_PRIVATE))

    val deviceId: String? get() = preferences.getString(KEY_DEVICE_ID, null)
    val installId: String? get() = preferences.getString(KEY_INSTALL_ID, null)
    val token: String? get() = preferences.getString(KEY_TOKEN, null)
    val isRegistered: Boolean get() = token != null
    val subscriptions: List<Subscription>?
        get() = preferences.getString(KEY_SUBSCRIPTIONS, null)?.let { encoded ->
            runCatching { Json.decodeFromString<List<Subscription>>(encoded) }.getOrNull()
        }

    fun saveSubscriptions(subscriptions: List<Subscription>) {
        check(preferences.edit().putString(KEY_SUBSCRIPTIONS, Json.encodeToString(subscriptions)).commit()) {
            "could not persist Herald subscriptions"
        }
    }

    fun save(response: RegisterResponse, fcmFid: String) {
        preferences.edit()
            .putString(KEY_DEVICE_ID, response.deviceId)
            .putString(KEY_INSTALL_ID, response.installId)
            .putString(KEY_TOKEN, response.deviceToken)
            .putString(KEY_FCM_FID, fcmFid)
            .apply()
    }

    fun saveFCMFid(fcmFid: String) {
        preferences.edit().putString(KEY_FCM_FID, fcmFid).apply()
    }

    /** Forgets the credential, retaining the user's subscription choice. */
    fun clear() {
        preferences.edit()
            .remove(KEY_DEVICE_ID)
            .remove(KEY_INSTALL_ID)
            .remove(KEY_TOKEN)
            .remove(KEY_FCM_FID)
            .apply()
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_TOKEN = "token"
        const val KEY_FCM_FID = "fcm_fid"
        const val KEY_SUBSCRIPTIONS = "subscriptions"
    }
}
