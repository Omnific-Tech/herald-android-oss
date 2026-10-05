package dev.omnific.herald.twa

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsSession
import androidx.browser.trusted.TokenStore
import androidx.browser.trusted.TrustedWebActivityIntentBuilder
import com.google.androidbrowserhelper.trusted.LauncherActivity
import com.google.androidbrowserhelper.trusted.LauncherActivityMetadata
import com.google.androidbrowserhelper.trusted.SessionStore
import com.google.androidbrowserhelper.trusted.SharedPreferencesTokenStore
import com.google.androidbrowserhelper.trusted.TwaLauncher
import com.google.androidbrowserhelper.trusted.TwaProviderPicker
import com.google.androidbrowserhelper.trusted.splashscreens.SplashScreenStrategy
import dev.omnific.herald.HeraldConfig

/**
 * Opens the product's PWA as a Trusted Web Activity.
 *
 * A notification tap arrives here with the delivery's link. The URL is resolved
 * against the configured origin before launch, so a link Herald somehow let
 * through still cannot send the user to another origin.
 *
 * With `web_subscription_bridge` on, launching still goes through
 * [LauncherActivity] and [TwaLauncher]; the bridge only adds a callback and
 * takes the session they create.
 */
open class HeraldLauncherActivity : LauncherActivity() {

    private val config: HeraldConfig by lazy { HeraldConfig.fromAssets(this) }

    /** The name this device registers under with Herald. */
    protected open val heraldDisplayName: String get() = Build.MODEL

    override fun getLaunchingUrl(): Uri {
        val requested = intent?.data ?: return Uri.parse(config.startUrl)

        // Only a URL already on the trusted origin is taken as given.
        val origin = Uri.parse(config.origin)
        val sameOrigin = requested.scheme == origin.scheme &&
            requested.host == origin.host &&
            requested.port == origin.port

        return if (sameOrigin) requested else Uri.parse(config.startUrl)
    }

    override fun getCustomTabsCallback(): CustomTabsCallback {
        val enforcer = super.getCustomTabsCallback()
        if (!config.webSubscriptionBridge) return enforcer
        return SubscriptionBridge(this, config, heraldDisplayName, enforcer)
    }

    /** Mirrors [LauncherActivity.createTwaLauncher], adding session capture. */
    override fun createTwaLauncher(): TwaLauncher {
        if (!config.webSubscriptionBridge) return super.createTwaLauncher()
        val browser = LauncherActivityMetadata.parse(this).launchingBrowser
        // TwaLauncher skips warmup on current Chrome, but Chrome refuses
        // validateRelationship and postMessage until a client has called it.
        (browser ?: TwaProviderPicker.pickProvider(packageManager).provider)?.let {
            CustomTabsClient.connectAndInitialize(this, it)
        }
        return BridgeTwaLauncher(
            this,
            browser,
            SessionStore.makeSessionId(taskId),
            SharedPreferencesTokenStore(this),
        )
    }
}

/**
 * TwaLauncher keeps its session private but hands it to the splash screen
 * strategy just before launch, so the bridge rides along there.
 */
private class BridgeTwaLauncher(
    context: Context,
    provider: String?,
    sessionId: Int,
    tokenStore: TokenStore,
) : TwaLauncher(context, provider, sessionId, tokenStore) {

    override fun launch(
        builder: TrustedWebActivityIntentBuilder,
        callback: CustomTabsCallback?,
        splashScreenStrategy: SplashScreenStrategy?,
        completionCallback: Runnable?,
        fallbackStrategy: FallbackStrategy,
    ) {
        val bridge = callback as? SubscriptionBridge
        val strategy = if (bridge == null) splashScreenStrategy else SessionCapture(bridge, splashScreenStrategy)
        super.launch(builder, callback, strategy, completionCallback, fallbackStrategy)
    }
}

private class SessionCapture(
    private val bridge: SubscriptionBridge,
    private val splash: SplashScreenStrategy?,
) : SplashScreenStrategy {

    override fun onTwaLaunchInitiated(providerPackage: String, builder: TrustedWebActivityIntentBuilder) {
        splash?.onTwaLaunchInitiated(providerPackage, builder)
    }

    override fun configureTwaBuilder(
        builder: TrustedWebActivityIntentBuilder,
        session: CustomTabsSession,
        onReadyCallback: Runnable,
    ) {
        bridge.attach(session)
        if (splash == null) onReadyCallback.run() else splash.configureTwaBuilder(builder, session, onReadyCallback)
    }
}
