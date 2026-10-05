package dev.omnific.herald

import android.content.Context
import dev.omnific.herald.client.Subscription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Product configuration, read from an asset rather than compiled in.
 *
 * Creating a product APK is meant to require configuration and assets, never an
 * edit to this module.
 */
@Serializable
data class HeraldConfig(
    /** Herald's base URL, for example `https://herald.example`. */
    @SerialName("herald_url") val heraldUrl: String,
    /** The application slug this APK belongs to. */
    @SerialName("application") val application: String,
    /** Trusted origin. Notification links resolve against it and nothing else. */
    @SerialName("origin") val origin: String,
    @SerialName("start_url") val startUrl: String,
    @SerialName("channels") val channels: List<ChannelSpec>,
    @SerialName("web_subscription_bridge") val webSubscriptionBridge: Boolean = false,
) {
    init {
        require(heraldUrl.startsWith("https://") || heraldUrl.startsWith("http://localhost")) {
            "herald_url must be https, or http://localhost for development"
        }
        require(origin.startsWith("https://")) { "origin must be https" }
        require(channels.isNotEmpty()) { "at least one channel is required" }
    }

    /**
     * Resolves a notification link against the trusted origin.
     *
     * Herald rejects an off-origin link at publish time; this is the second
     * check, because a client that concatenates blindly would still follow one
     * if that ever failed.
     */
    fun resolveLink(path: String?): String {
        if (path.isNullOrEmpty()) return startUrl
        if (!path.startsWith("/")) return startUrl
        if (path.length > 1 && (path[1] == '/' || path[1] == '\\')) return startUrl
        return origin.trimEnd('/') + path
    }

    /** Every configured channel, with no topic filter. */
    fun allChannels(): List<Subscription> = channels.map { Subscription(it.id) }

    companion object {
        const val ASSET_NAME: String = "herald.json"

        private val json = Json { ignoreUnknownKeys = true }

        /** Reads [ASSET_NAME] from the APK's assets. */
        fun fromAssets(context: Context, assetName: String = ASSET_NAME): HeraldConfig =
            context.assets.open(assetName).use { stream ->
                json.decodeFromString(serializer(), stream.readBytes().decodeToString())
            }
    }
}

/** One Android notification channel, mapped from a Herald channel of the same id. */
@Serializable
data class ChannelSpec(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String = "",
    /** One of `default`, `high`, `low`. */
    @SerialName("importance") val importance: String = "default",
)
