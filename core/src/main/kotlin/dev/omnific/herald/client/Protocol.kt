package dev.omnific.herald.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Herald protocol v1 wire types.
 *
 * These mirror docs/protocol-v1.md in the herald repository. Changing one here
 * without changing it there breaks delivery silently.
 */
@Serializable
data class RegisterRequest(
    @SerialName("application") val application: String? = null,
    @SerialName("platform") val platform: String = "android",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("fcm_fid") val fcmFid: String = "",
)

@Serializable
data class RegisterResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("install_id") val installId: String,
    @SerialName("device_token") val deviceToken: String,
    /** Retained by Herald for delivery auditing. */
    @SerialName("acknowledged_sequence") val acknowledgedSequence: Long = 0,
)

@Serializable
data class Subscription(
    @SerialName("channel") val channel: String,
    @SerialName("topic") val topic: String = "",
)

@Serializable
data class SubscriptionsRequest(
    @SerialName("subscriptions") val subscriptions: List<Subscription>,
)

@Serializable
data class FCMRequest(
    @SerialName("fcm_fid") val fcmFid: String,
)

@Serializable
data class AckRequest(
    @SerialName("status") val status: String,
)

@Serializable
data class Message(
    @SerialName("id") val id: String,
    @SerialName("channel") val channel: String,
    @SerialName("topic") val topic: String = "",
    @SerialName("priority") val priority: String = "normal",
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
    @SerialName("link") val link: String = "",
    @SerialName("data") val data: JsonObject? = null,
    @SerialName("collapse_key") val collapseKey: String = "",
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class StreamEvent(
    @SerialName("delivery_id") val deliveryId: String,
    @SerialName("sequence") val sequence: Long,
    @SerialName("message") val message: Message,
)

/** Client-reported delivery outcomes. */
object AckStatus {
    const val DELIVERED = "delivered"
    const val DISPLAYED = "displayed"

    /** Arrived already expired and was not shown. */
    const val SUPPRESSED = "suppressed"
}
