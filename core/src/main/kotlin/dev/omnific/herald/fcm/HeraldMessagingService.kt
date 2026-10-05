package dev.omnific.herald.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.omnific.herald.Herald
import dev.omnific.herald.client.AckStatus
import dev.omnific.herald.client.HeraldClient
import dev.omnific.herald.client.HeraldHttpException
import dev.omnific.herald.client.StreamEvent
import dev.omnific.herald.delivery.DeliveryLog
import dev.omnific.herald.delivery.isExpired
import dev.omnific.herald.notifications.Notifier
import dev.omnific.herald.R

/** Receives a data-only FCM message, renders it, and reports the outcome. */
class HeraldMessagingService : FirebaseMessagingService() {

    /** Keeps Herald's target current when Firebase rotates this installation. */
    override fun onRegistered(installationId: String) {
        super.onRegistered(installationId)
        Thread({
            val herald = Herald.create(this)
            val token = herald.store.token ?: return@Thread
            try {
                herald.client.updateFCM(token, installationId)
                herald.store.saveFCMFid(installationId)
            } catch (error: HeraldHttpException) {
                if (error.isUnauthorized) herald.store.clear()
            } catch (_: Exception) {
                // A later FCM sync or app launch retries the refresh.
            }
        }, "herald-fcm-registration").start()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val payload = remoteMessage.data[DELIVERY_KEY] ?: return
        val event = try {
            HeraldClient.defaultJson.decodeFromString(StreamEvent.serializer(), payload)
        } catch (_: Exception) {
            return
        }

        val herald = Herald.create(this)
        val log = DeliveryLog(this)
        val notifier = Notifier(
            context = this,
            config = herald.config,
            launcher = launcherClass(),
            smallIconRes = R.drawable.ic_herald_notification,
        )
        notifier.ensureChannels()

        val status = when {
            isExpired(event.message.expiresAt) -> AckStatus.SUPPRESSED
            log.wasSeen(event.deliveryId) -> AckStatus.DELIVERED
            notifier.show(event.deliveryId, event.message) -> {
                log.markSeen(event.deliveryId)
                AckStatus.DISPLAYED
            }
            else -> AckStatus.SUPPRESSED
        }

        val token = herald.store.token ?: return
        try {
            herald.client.acknowledge(token, event.deliveryId, status)
        } catch (_: Exception) {
            // The notification has already been handled. A failed ACK is
            // observable server-side but must not crash the FCM callback.
        }
    }

    private fun launcherClass(): Class<*> {
        val name = packageManager.getLaunchIntentForPackage(packageName)?.component?.className
            ?: error("the product APK must declare a launcher activity")
        return Class.forName(name)
    }

    private companion object {
        const val DELIVERY_KEY = "herald_delivery"
    }
}
