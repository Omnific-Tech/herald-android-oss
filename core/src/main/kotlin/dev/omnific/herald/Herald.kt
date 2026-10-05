package dev.omnific.herald

import android.content.Context
import dev.omnific.herald.client.DeviceStore
import dev.omnific.herald.client.HeraldClient
import dev.omnific.herald.client.HeraldHttpException
import dev.omnific.herald.client.Subscription
import com.google.android.gms.tasks.Tasks
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient

/**
 * Entry point for a product APK.
 *
 * A product supplies configuration and assets; nothing here needs editing to
 * add another product.
 */
class Herald private constructor(
    val config: HeraldConfig,
    val store: DeviceStore,
    val client: HeraldClient,
    val http: OkHttpClient,
) {

    /**
     * Registers if this install has no credential yet, then replaces its
     * subscriptions. Safe to call on every launch.
     */
    fun ensureRegistered(displayName: String, subscriptions: List<Subscription>? = null) {
        val selected = config.selectSubscriptions(subscriptions, store.subscriptions)
        // A raw FID is not targetable until FCM has registered this app
        // instance. Waiting here prevents Herald from publishing to it early.
        Tasks.await(FirebaseMessaging.getInstance().register())
        val fcmFid = Tasks.await(FirebaseInstallations.getInstance().id)
        var token = store.token

        if (token == null) {
            val response = client.register(config.application, displayName, fcmFid)
            store.save(response, fcmFid)
            token = response.deviceToken
        }

        val activeToken = token

        try {
            client.updateFCM(activeToken, fcmFid)
            store.saveFCMFid(fcmFid)
            client.replaceSubscriptions(activeToken, selected)
        } catch (error: HeraldHttpException) {
            // The token was revoked or the device removed server-side. Drop it
            // and register again rather than retrying with a dead credential.
            if (!error.isUnauthorized) throw error
            store.clear()
            val response = client.register(config.application, displayName, fcmFid)
            store.save(response, fcmFid)
            client.replaceSubscriptions(response.deviceToken, selected)
        }
    }

    /** Every configured channel, with no topic filter. */
    fun allChannels(): List<Subscription> = config.allChannels()

    companion object {
        fun create(context: Context, config: HeraldConfig = HeraldConfig.fromAssets(context)): Herald {
            val http = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            return Herald(
                config = config,
                store = DeviceStore(context),
                client = HeraldClient(config.heraldUrl, http),
                http = http,
            )
        }
    }
}

/**
 * Registers this Firebase installation with Herald, off the main thread.
 *
 * Safe to call on every launch: registration is skipped once this install has a
 * credential, and the service ignores a duplicate start.
 */
fun startHerald(
    context: Context,
    displayName: String,
    subscriptions: List<Subscription>? = null,
    onError: (Throwable) -> Unit = {},
) {
    if (!bootstrapRunning.compareAndSet(false, true)) return
    Thread({
        try {
            val herald = Herald.create(context)
            synchronized(registrationLock) {
                herald.ensureRegistered(displayName, subscriptions)
            }
        } catch (error: Throwable) {
            onError(error)
        } finally {
            bootstrapRunning.set(false)
        }
    }, "herald-bootstrap").start()
}

private val bootstrapRunning = AtomicBoolean(false)
private val registrationLock = Any()

/** Applies a complete list only after Herald accepts it. Never exposes the device token. */
fun setHeraldSubscriptions(
    context: Context,
    displayName: String,
    subscriptions: List<Subscription>,
    onResult: (Throwable?) -> Unit,
) {
    Thread({
        onResult(runCatching { applyHeraldSubscriptions(context, displayName, subscriptions) }.exceptionOrNull())
    }, "herald-subscriptions").start()
}

/** Blocking form of [setHeraldSubscriptions]; call off the main thread. */
internal fun applyHeraldSubscriptions(context: Context, displayName: String, subscriptions: List<Subscription>) {
    synchronized(registrationLock) {
        val herald = Herald.create(context)
        herald.ensureRegistered(displayName, subscriptions)
        herald.store.saveSubscriptions(subscriptions)
    }
}

/** An explicit list, else the user's saved one, else every channel whole. */
internal fun HeraldConfig.selectSubscriptions(
    explicit: List<Subscription>?,
    saved: List<Subscription>?,
): List<Subscription> = explicit ?: saved ?: allChannels()
