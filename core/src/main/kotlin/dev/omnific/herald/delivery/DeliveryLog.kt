package dev.omnific.herald.delivery

import android.content.Context

/**
 * Remembers which deliveries have already been shown.
 *
 * Herald is at-least-once: the same delivery can arrive twice, across a
 * reconnect or across both transports. Deduplicating on delivery id here is
 * what turns that into one visible notification.
 */
class DeliveryLog(context: Context, private val capacity: Int = 500) {

    private val preferences =
        context.applicationContext.getSharedPreferences("herald.deliveries", Context.MODE_PRIVATE)

    @Synchronized
    fun wasSeen(deliveryId: String): Boolean = ordered().contains(deliveryId)

    /** Records a delivery only after Android accepted its notification. */
    @Synchronized
    fun markSeen(deliveryId: String) {
        val seen = ordered()
        if (seen.contains(deliveryId)) return

        seen.add(deliveryId)
        // Bounded: an install that runs for years must not accumulate ids
        // forever, and a delivery old enough to fall off has long since expired.
        while (seen.size > capacity) {
            seen.removeAt(0)
        }

        preferences.edit().putString(KEY_SEEN, seen.joinToString(separator = "\n")).apply()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun ordered(): MutableList<String> {
        val stored = preferences.getString(KEY_SEEN, "").orEmpty()
        if (stored.isEmpty()) return mutableListOf()
        return stored.split("\n").filter { it.isNotEmpty() }.toMutableList()
    }

    private companion object {
        const val KEY_SEEN = "seen"
    }
}
