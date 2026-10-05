package dev.omnific.herald.twa

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.app.NotificationManagerCompat
import dev.omnific.herald.HeraldConfig
import dev.omnific.herald.applyHeraldSubscriptions
import dev.omnific.herald.client.DeviceStore
import dev.omnific.herald.selectSubscriptions
import java.util.concurrent.Executors

/**
 * Lets the product's PWA read and replace this device's subscriptions over the
 * TWA's postMessage channel.
 *
 * Browser callbacks arrive on the main thread; so does all state here. Other
 * callbacks are forwarded to [delegate], which keeps the library's quality
 * enforcement in place.
 */
internal class SubscriptionBridge(
    context: Context,
    private val config: HeraldConfig,
    private val displayName: String,
    private val delegate: CustomTabsCallback,
) : CustomTabsCallback() {

    private val context = context.applicationContext
    private val origin = Uri.parse(config.origin)
    private val channelIds = config.channels.map { it.id }.toSet()
    private val main = Handler(Looper.getMainLooper())
    private var session: CustomTabsSession? = null
    private var validationRequested = false
    private var validated = false
    private var navigated = false
    private var channelRequested = false

    /** Adopts the session the TWA is launched with. Later sessions are ignored. */
    fun attach(session: CustomTabsSession) {
        if (this.session == null) this.session = session
    }

    override fun onRelationshipValidationResult(relation: Int, requestedOrigin: Uri, result: Boolean, extras: Bundle?) {
        delegate.onRelationshipValidationResult(relation, requestedOrigin, result, extras)
        if (relation != CustomTabsService.RELATION_USE_AS_ORIGIN || requestedOrigin != origin) return
        validated = result
        if (!result) Log.w(TAG, "subscription bridge disabled: use_as_origin not verified for $origin")
        requestChannel()
    }

    override fun onNavigationEvent(navigationEvent: Int, extras: Bundle?) {
        delegate.onNavigationEvent(navigationEvent, extras)
        // A navigation replaces the page and its port, so each one needs a new channel.
        if (navigationEvent == NAVIGATION_FINISHED) {
            navigated = true
            channelRequested = false
            // Chrome refuses validation until warmed up, which the launcher
            // starts alongside the launch; by the first page load it has landed.
            if (!validationRequested) {
                validationRequested =
                    session?.validateRelationship(CustomTabsService.RELATION_USE_AS_ORIGIN, origin, null) == true
            }
            requestChannel()
        }
    }

    override fun onMessageChannelReady(extras: Bundle?) {
        delegate.onMessageChannelReady(extras)
        val saved = DeviceStore(context).subscriptions
        send(BridgeReply.ready(config.selectSubscriptions(null, saved), saved != null, notificationsEnabled()))
    }

    override fun onPostMessage(message: String, extras: Bundle?) {
        delegate.onPostMessage(message, extras)
        if (validated) handle(message)
    }

    override fun extraCallback(callbackName: String, args: Bundle?) = delegate.extraCallback(callbackName, args)

    override fun extraCallbackWithResult(callbackName: String, args: Bundle?): Bundle? =
        delegate.extraCallbackWithResult(callbackName, args)

    private fun requestChannel() {
        if (!validated || !navigated || channelRequested) return
        channelRequested = session?.requestPostMessageChannel(origin, origin, Bundle()) == true
    }

    private fun handle(message: String) {
        when (val command = parseBridgeCommand(message, channelIds) ?: return) {
            is BridgeCommand.GetSubscriptions ->
                send(BridgeReply.result(command.id, config.selectSubscriptions(null, DeviceStore(context).subscriptions)))
            is BridgeCommand.Rejected -> send(BridgeReply.failure(command.id, BridgeError.INVALID_SUBSCRIPTIONS))
            is BridgeCommand.SetSubscriptions -> {
                if (!notificationsEnabled()) {
                    send(BridgeReply.failure(command.id, BridgeError.NOTIFICATIONS_DENIED))
                    return
                }
                // One thread, so overlapping sets reach Herald and the store in the order sent.
                setExecutor.execute {
                    val error = runCatching {
                        applyHeraldSubscriptions(context, displayName, command.subscriptions)
                    }.exceptionOrNull()
                    if (error != null) Log.w(TAG, "could not update subscriptions", error)
                    val reply = if (error == null) {
                        BridgeReply.result(command.id)
                    } else {
                        BridgeReply.failure(command.id, bridgeErrorCode(error))
                    }
                    main.post { send(reply) }
                }
            }
        }
    }

    private fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun send(reply: BridgeReply) {
        session?.postMessage(encodeBridgeReply(reply), null)
    }

    private companion object {
        const val TAG = "Herald"
        val setExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "herald-bridge") }
    }
}
