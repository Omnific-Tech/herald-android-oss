package dev.omnific.herald.twa

import dev.omnific.herald.client.HeraldHttpException
import dev.omnific.herald.client.Subscription
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A page command that deserves a reply. */
internal sealed interface BridgeCommand {
    val id: String

    data class GetSubscriptions(override val id: String) : BridgeCommand
    data class SetSubscriptions(override val id: String, val subscriptions: List<Subscription>) : BridgeCommand

    /** A recognised command whose payload failed validation. */
    data class Rejected(override val id: String) : BridgeCommand
}

internal object BridgeError {
    const val INVALID_SUBSCRIPTIONS = "invalid_subscriptions"
    const val NOTIFICATIONS_DENIED = "notifications_denied"
    const val HERALD_UNAVAILABLE = "herald_unavailable"
}

@Serializable
internal data class BridgeReply(
    val type: String,
    val id: String? = null,
    val ok: Boolean? = null,
    val error: String? = null,
    val subscriptions: List<Subscription>? = null,
    val saved: Boolean? = null,
    val notifications: String? = null,
) {
    companion object {
        fun ready(subscriptions: List<Subscription>, saved: Boolean, notificationsEnabled: Boolean) = BridgeReply(
            type = "herald.ready",
            subscriptions = subscriptions,
            saved = saved,
            notifications = if (notificationsEnabled) "granted" else "denied",
        )

        fun result(id: String, subscriptions: List<Subscription>? = null) =
            BridgeReply(RESULT, id, ok = true, subscriptions = subscriptions)

        fun failure(id: String, error: String) = BridgeReply(RESULT, id, ok = false, error = error)

        private const val RESULT = "herald.subscriptions.result"
    }
}

private const val MAX_ID_LENGTH = 64
private const val MAX_SUBSCRIPTIONS = 250
private const val MAX_TOPIC_LENGTH = 200

// Topics are always written, so the page never has to infer an omitted one.
private val bridgeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Returns null for anything that cannot be answered: not a command object, an
 * unknown type, or no valid id to address a reply to.
 */
internal fun parseBridgeCommand(message: String, channelIds: Set<String>): BridgeCommand? {
    val command = runCatching { bridgeJson.parseToJsonElement(message) }.getOrNull() as? JsonObject ?: return null
    val id = command.string("id")?.takeIf { it.length in 1..MAX_ID_LENGTH } ?: return null

    return when (command.string("type")) {
        "herald.subscriptions.get" -> BridgeCommand.GetSubscriptions(id)
        "herald.subscriptions.set" -> {
            val subscriptions = command["subscriptions"]?.let { element ->
                runCatching { bridgeJson.decodeFromJsonElement(ListSerializer(Subscription.serializer()), element) }
                    .getOrNull()
            }
            if (subscriptions != null && subscriptions.isValid(channelIds)) {
                BridgeCommand.SetSubscriptions(id, subscriptions)
            } else {
                BridgeCommand.Rejected(id)
            }
        }
        else -> null
    }
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun List<Subscription>.isValid(channelIds: Set<String>): Boolean =
    size <= MAX_SUBSCRIPTIONS &&
        all { it.channel in channelIds && it.topic.length <= MAX_TOPIC_LENGTH } &&
        toSet().size == size

/** Only Herald refusing the list itself is the page's to fix; the rest is worth retrying. */
internal fun bridgeErrorCode(error: Throwable): String =
    if (error is HeraldHttpException && error.isPermanent && !error.isUnauthorized &&
        error.path.endsWith("/v1/subscriptions")) {
        BridgeError.INVALID_SUBSCRIPTIONS
    } else {
        BridgeError.HERALD_UNAVAILABLE
    }

internal fun encodeBridgeReply(reply: BridgeReply): String = bridgeJson.encodeToString(BridgeReply.serializer(), reply)
