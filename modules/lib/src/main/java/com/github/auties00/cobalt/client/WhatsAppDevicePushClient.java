package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.registration.push.NoopMobilePushClient;
import com.github.auties00.cobalt.registration.push.apns.ApnsClient;
import com.github.auties00.cobalt.registration.push.fcm.FcmClient;

import java.util.Set;

/**
 * Bridge between Cobalt and an embedder-supplied push-notification
 * service that produces the device-side data the registration body
 * advertises so the WhatsApp registration server can deliver
 * verification codes via silent push.
 *
 * <p>The native WhatsApp mobile clients embed two push-related fields
 * in their registration request bodies:
 * <ol>
 *   <li>a <b>device push token</b> obtained from Firebase Cloud
 *       Messaging on Android or Apple Push Notification Service on iOS,
 *       advertised so the registration server knows where to silent-push
 *       the verification code if the user picks a push-based
 *       verification method;</li>
 *   <li>the <b>verification code</b> received via that silent push,
 *       extracted from the FCM data message or APNS payload and echoed
 *       back to the server on the next {@code /v2/code} call.</li>
 * </ol>
 * Both values are obtained from an authenticated network session with
 * the vendor's push service, which Cobalt does not have direct access
 * to. This interface is the seam through which an embedding application
 * supplies them.
 *
 * <p>Unlike {@link WhatsAppDeviceAttestor}, this interface is not sealed
 * and not platform-specific at the type level: an FCM-based
 * implementation is used for Android devices and an APNS-based
 * implementation for iOS devices, but both expose the same surface and
 * are kept stateful by the embedder so neither method needs the live
 * {@link com.github.auties00.cobalt.store.WhatsAppStore} as input.
 *
 * @apiNote Implementations are not required to be stateless or
 *          thread-safe: the registration code calls each method
 *          sequentially from the thread that drives the registration
 *          ceremony and never concurrently.
 */
public interface WhatsAppDevicePushClient extends AutoCloseable {
    /**
     * Returns a fresh, unauthenticated {@link ApnsClient} typed as a
     * {@link WhatsAppDevicePushClient}. The caller must call
     * {@link #authenticate(WhatsAppDevice)} with an iOS or iOS-business
     * device before any of the read-only accessors become usable.
     *
     * @return a new unauthenticated APNS-backed push client
     */
    static WhatsAppDevicePushClient apns() {
        return ApnsClient.newSession();
    }

    /**
     * Returns a fresh, unauthenticated {@link FcmClient} typed as a
     * {@link WhatsAppDevicePushClient}. The caller must call
     * {@link #authenticate(WhatsAppDevice)} with an Android or
     * Android-business device before any of the read-only accessors
     * become usable.
     *
     * @return a new unauthenticated FCM-backed push client
     */
    static WhatsAppDevicePushClient fcm() {
        return FcmClient.newSession();
    }

    /**
     * Returns a no-op push client that emits empty token and code
     * values. Used by the registration code as the low-trust default
     * when no push client is configured: the {@code push_token} and
     * {@code push_code} form fields are still emitted but with empty
     * values, which the server tolerates as a low-trust signal.
     *
     * @return a stateless no-op push client
     */
    static WhatsAppDevicePushClient noop() {
        return NoopMobilePushClient.INSTANCE;
    }

    /**
     * Returns the set of {@link ClientPlatformType} values this push
     * client is willing to authenticate against. Used by registration
     * code (and embedders selecting a push client at runtime) to
     * verify that a chosen client matches the device platform before
     * driving {@link #authenticate(WhatsAppDevice)}.
     *
     * <p>For example, an APNS-backed implementation returns
     * {@code {IOS, IOS_BUSINESS}}, an FCM-backed implementation
     * returns {@code {ANDROID, ANDROID_BUSINESS}}, and the no-op
     * client returned by {@link #noop()} returns every entry of the
     * enum since it accepts any device unconditionally.
     *
     * @return an unmodifiable, non-empty set of supported platforms
     */
    Set<ClientPlatformType> supportedPlatforms();

    /**
     * Authenticates this push client with its underlying push service
     * using the given device profile.
     *
     * <p>For FCM-based implementations this typically completes a
     * checkin with Google Play Services using the device's model,
     * manufacturer, and OS version to register a fresh push
     * registration ID. For APNS-based implementations this opens an
     * authenticated session with Apple's APNS gateway. Implementations
     * are expected to be idempotent: a call made while
     * {@link #isAuthenticated} already returns {@code true} should be
     * a no-op (or a cheap re-check) rather than re-driving the full
     * authentication ceremony.
     *
     * @param device the device profile to authenticate as; never
     *               {@code null}
     * @throws RuntimeException if the underlying push service refuses
     *                          to authenticate the given device
     *                          profile
     */
    void authenticate(WhatsAppDevice device);

    /**
     * Returns whether this push client currently holds an active
     * authenticated session with its underlying push service.
     *
     * <p>Used to decide whether {@link #authenticate} needs to be
     * driven before {@link #getPushToken} or {@link #getPushCode} can
     * be safely consulted. The client returned by {@link #noop()}
     * reports {@code true} unconditionally so callers do not need to
     * special-case it: the empty token / empty code values it produces
     * are valid even without any real authentication.
     *
     * @return {@code true} if an authenticated session is in place
     */
    boolean isAuthenticated();

    /**
     * Returns the device push registration token advertised in the
     * {@code push_token} form field.
     *
     * <p>On Android this is the long base64url-ish string returned by
     * {@code FirebaseMessaging.getInstance().getToken()} on a real
     * Play-Services device. On iOS this is the hex-encoded device token
     * the iOS app receives via {@code -[UIApplication
     * application:didRegisterForRemoteNotificationsWithDeviceToken:]}.
     * Cobalt embeds it once at the start of the registration ceremony
     * so the WhatsApp registration server knows where to silent-push
     * the verification code if the user later picks a push-based
     * verification method.
     *
     * <p>The token's lifecycle is independent from {@link #getPushCode}:
     * {@code push_token} is sent at registration init (on
     * {@code /v2/exist}), then the server may push a silent payload
     * back, and the code extracted from that push becomes
     * {@code push_code} on the subsequent {@code /v2/code} call.
     *
     * <p>Implementations should return the empty string when push is
     * unavailable (Huawei-style or sideloaded Android, simulator or
     * jailbroken iOS), in which case the {@code push_token} field is
     * still emitted but with an empty value, which the server tolerates
     * as a low-trust signal.
     *
     * @return the push device token, or empty string when push is
     *         unavailable; never {@code null}
     */
    String getPushToken();

    /**
     * Releases any resources held by this push client (network
     * sockets, threads, decryption material). The default
     * implementation is a no-op for stateless clients such as the one
     * returned by {@link #noop()}; vendor-backed implementations
     * (FCM/APNS) override it to tear down their long-lived
     * connections.
     *
     * <p>Narrows {@link AutoCloseable#close()}'s {@code throws
     * Exception} to no checked exceptions so callers may use the
     * client in plain try-with-resources blocks without a wrapping
     * {@code try/catch}.
     */
    @Override
    default void close() {
    }

    /**
     * Returns the verification code received via a silent push message,
     * embedded as the {@code push_code} form field in {@code /v2/code}
     * when the user picked a push-based verification method.
     *
     * <p>The flow is: the previous {@code /v2/exist} call advertised the
     * device's push token via {@link #getPushToken}, the server silently
     * pushed a payload containing a verification code, the embedder's
     * push handler extracted that code, and the next {@code /v2/code}
     * call surrenders it back here. Cobalt's built-in flow does not
     * drive this verification path (it offers SMS / voice / wa_old
     * instead), so this method is rarely consulted unless the embedder
     * has wired a push listener.
     *
     * <p>Implementations should return the empty string when no push
     * verification is in flight; the server treats an empty
     * {@code push_code} the same as the field being absent.
     *
     * @return the push-delivered verification code, or empty string
     *         when no push verification is in flight; never {@code null}
     */
    String getPushCode();
}
