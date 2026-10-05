package dev.omnific.herald.template

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import dev.omnific.herald.startHerald
import dev.omnific.herald.twa.HeraldLauncherActivity

/**
 * The product's entry point: a Trusted Web Activity over its own origin.
 *
 * Subclassing exists only so the notification permission is requested from a
 * real activity; the launching behaviour is the toolkit's.
 */
class ProductLauncherActivity : HeraldLauncherActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return

        if (notificationsAllowed()) {
            startNotifications()
        } else if (savedInstanceState == null) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST)
        }
    }

    /**
     * The browser opens over this activity and would hide the permission
     * dialog, so the TWA waits until the user has answered it.
     */
    override fun shouldLaunchImmediately(): Boolean = notificationsAllowed()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startNotifications()
        }
        launchTwa()
    }

    /** Android 13 onwards will not show a notification without this grant. */
    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun startNotifications() {
        startHerald(
            context = applicationContext,
            displayName = heraldDisplayName,
            onError = { error -> Log.w("Herald", "could not register", error) },
        )
    }

    private companion object {
        const val PERMISSION_REQUEST = 1
    }
}
