package dev.omnific.herald.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.omnific.herald.ChannelSpec
import dev.omnific.herald.HeraldConfig
import dev.omnific.herald.client.Message

/** Renders Herald messages as Android notifications. */
class Notifier(
    private val context: Context,
    private val config: HeraldConfig,
    private val launcher: Class<*>,
    private val smallIconRes: Int,
) {

    private val manager = NotificationManagerCompat.from(context)

    /**
     * Creates one Android channel per configured Herald channel. Separate
     * channels are what let a user silence one product's alerts without
     * silencing the other's, in Android settings and on a mirrored watch.
     */
    fun ensureChannels() {
        config.channels.forEach { spec ->
            val channel = NotificationChannel(spec.id, spec.name, spec.importance.toAndroidImportance())
            channel.description = spec.description
            manager.createNotificationChannel(channel)
        }
    }

    /** Shows [message]. The channel must be one this product declared. */
    fun show(deliveryId: String, message: Message): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val channelId = config.channels
            .firstOrNull { it.id == message.channel }
            ?.id
            ?: config.channels.first().id

        val intent = Intent(context, launcher).apply {
            action = Intent.ACTION_VIEW
            // Resolved against the trusted origin, never taken as given.
            data = Uri.parse(config.resolveLink(message.link))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pending = PendingIntent.getActivity(
            context,
            deliveryId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIconRes)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setPriority(message.priority.toCompatPriority())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        // A collapse key replaces the earlier notification for the same event
        // rather than stacking another one beside it.
        val tag = message.collapseKey.ifEmpty { message.id }
        return try {
            manager.notify(tag, NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun String.toAndroidImportance(): Int = when (this) {
        "high" -> NotificationManager.IMPORTANCE_HIGH
        "low" -> NotificationManager.IMPORTANCE_LOW
        else -> NotificationManager.IMPORTANCE_DEFAULT
    }

    private fun String.toCompatPriority(): Int =
        if (this == "high") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

    private companion object {
        const val NOTIFICATION_ID: Int = 1
    }
}
