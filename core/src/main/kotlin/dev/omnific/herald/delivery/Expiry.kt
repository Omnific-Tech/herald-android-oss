package dev.omnific.herald.delivery

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Whether a message may still be displayed.
 *
 * A message that arrives after its expiry is acknowledged as suppressed rather
 * than shown: an earthquake alert that surfaces an hour late is worse than none.
 * An unparseable timestamp is treated as expired, because showing a
 * notification Herald cannot vouch for is the worse failure.
 */
fun isExpired(expiresAt: String, now: Instant = Instant.now()): Boolean =
    try {
        !now.isBefore(Instant.parse(expiresAt))
    } catch (_: DateTimeParseException) {
        true
    }
