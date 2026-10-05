package dev.omnific.herald.client

import java.io.IOException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Raised when Herald answers, but not with success. */
class HeraldHttpException(val code: Int, val body: String, val path: String = "") :
    IOException("herald returned $code for $path: $body") {

    /** 4xx other than 429 will not improve on retry. */
    val isPermanent: Boolean get() = code in 400..499 && code != 429

    /** The device's token is unknown or revoked; it must register again. */
    val isUnauthorized: Boolean get() = code == 401
}

/** The authenticated calls a device makes against Herald. */
class HeraldClient(
    private val baseUrl: String,
    private val http: OkHttpClient,
    private val json: Json = defaultJson,
) {

    /** Creates a device. Herald issues the install id; the caller cannot pick one. */
    fun register(application: String, displayName: String, fcmFid: String): RegisterResponse {
        val request = RegisterRequest(application = application, displayName = displayName, fcmFid = fcmFid)
        return post("/v1/devices/register", json.encodeToString(request), token = null)
            .let { json.decodeFromString(RegisterResponse.serializer(), it) }
    }

    /**
     * Issues a new token for this device, keeping its sequence and history.
     * Possession of the current token is the proof of ownership.
     */
    fun rotate(token: String, displayName: String, fcmFid: String): RegisterResponse {
        val request = RegisterRequest(displayName = displayName, fcmFid = fcmFid)
        return post("/v1/devices/register", json.encodeToString(request), token)
            .let { json.decodeFromString(RegisterResponse.serializer(), it) }
    }

    /** Replaces the device's whole subscription set. */
    fun replaceSubscriptions(token: String, subscriptions: List<Subscription>) {
        val request = SubscriptionsRequest(subscriptions)
        put("/v1/subscriptions", json.encodeToString(request), token)
    }

    /** Refreshes and re-enables this device's Firebase installation. */
    fun updateFCM(token: String, fcmFid: String) {
        put("/v1/device/fcm", json.encodeToString(FCMRequest(fcmFid)), token)
    }

    /** Acknowledges a delivery. Herald treats a repeat as a no-op. */
    fun acknowledge(token: String, deliveryId: String, status: String) {
        post("/v1/deliveries/$deliveryId/ack", json.encodeToString(AckRequest(status)), token)
    }

    private fun post(path: String, body: String, token: String?): String =
        execute(newRequest(path, token).post(body.toRequestBody(JSON_MEDIA_TYPE)).build())

    private fun put(path: String, body: String, token: String?): String =
        execute(newRequest(path, token).put(body.toRequestBody(JSON_MEDIA_TYPE)).build())

    private fun newRequest(path: String, token: String?): Request.Builder =
        Request.Builder().url(baseUrl + path).apply {
            if (token != null) header("Authorization", "Bearer $token")
        }

    private fun execute(request: Request): String =
        http.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw HeraldHttpException(response.code, body, request.url.encodedPath)
            body
        }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        // Herald requires fields such as `platform` that the wire types default.
        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
