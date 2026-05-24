package com.github.auties00.cobalt.registration.push.fcm;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.client.WhatsAppDevice;
import com.github.auties00.cobalt.exception.WhatsAppRegistrationException;
import com.github.auties00.cobalt.Faker;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.registration.MobileClientRegistration;
import com.github.auties00.cobalt.registration.push.apns.ApnsClient;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live integration suite for {@link FcmClient}.
 */
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class FcmClientTest {
    /**
     * Tracks whether the throttle guard has fired yet.
     *
     * @apiNote
     * Flipped to {@code false} after the first
     * {@link #throttleAgainstC2dmAntiAbuse()} call so the first test
     * runs without the cool-down wait.
     */
    private static final AtomicBoolean FIRST_TEST = new AtomicBoolean(true);

    /**
     * Verifies that a fresh push client can drive a complete WhatsApp
     * registration to completion.
     *
     * @apiNote
     * Retries up to five times with a fresh phone number per attempt
     * to absorb transient WhatsApp-side rejections; fails the build
     * only when every attempt is rejected or every attempt times out
     * waiting for the silent verification push.
     *
     * @throws Throwable if the registration fails for a reason other
     *                   than {@link WhatsAppRegistrationException} or
     *                   a push-arrival timeout
     */
    @Test
    public void deliversPushCodeViaRegistration() throws Throwable {
        var maxAttempts = 5;
        var perAttemptTimeout = Duration.ofMinutes(2);
        var device = WhatsAppDevice.ios(false);
        Throwable lastFailure = null;
        for (var attempt = 1; attempt <= maxAttempts; attempt++) {
            var phoneNumber = Faker.randomItalianMobile();
            try (var pushClient = ApnsClient.newSession()) {
                pushClient.authenticate(device);

                var store = WhatsAppStoreFactory.temporary()
                        .create(WhatsAppClientType.MOBILE, phoneNumber);
                store.setDevice(device);

                var verification = WhatsAppClientVerificationHandler.Mobile
                        .whatsapp(pushClient::getPushCode);

                var registrationFailure = new AtomicReference<Throwable>();
                var registrationDone = new CountDownLatch(1);
                var thread = Thread.ofVirtual().start(() -> {
                    try (var registration = MobileClientRegistration.newRegistration(
                            store, verification, null, pushClient)) {
                        registration.register();
                    } catch (Throwable t) {
                        registrationFailure.set(t);
                    } finally {
                        registrationDone.countDown();
                    }
                });

                if (!registrationDone.await(perAttemptTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    // WA never silent-pushed: getPushCode is parked on the holder's
                    // monitor inside the registration thread. Closing the push
                    // client wakes it and lets the registration thread unwind.
                    pushClient.close();
                    if (!registrationDone.await(15, TimeUnit.SECONDS)) {
                        thread.interrupt();
                        registrationDone.await();
                    }
                    lastFailure = new AssertionError(
                            "attempt " + attempt + " timed out waiting for push to " + phoneNumber);
                    System.out.printf("attempt %d/%d for %d timed out waiting for push%n",
                            attempt, maxAttempts, phoneNumber);
                    continue;
                }

                var failure = registrationFailure.get();
                if (failure == null) {
                    assertTrue(store.registered(),
                            "store must report registered after a successful flow");
                    return;
                }
                if (failure instanceof WhatsAppRegistrationException reg) {
                    lastFailure = reg;
                    System.out.printf("attempt %d/%d for %d rejected by Whatsapp: %s%n",
                            attempt, maxAttempts, phoneNumber, reg.getMessage());
                    continue;
                }
                throw failure;
            }
        }
        fail("Registration via push did not succeed within " + maxAttempts + " attempts: "
             + lastFailure.getMessage(),
                lastFailure);
    }

    /**
     * Sleeps 45 seconds before every test except the first.
     *
     * @implNote
     * This implementation uses an {@link AtomicBoolean} guard rather
     * than relying on JUnit ordering so the throttle holds even when
     * the test runner randomises method order.
     *
     * @throws InterruptedException if the sleep is interrupted
     */
    @BeforeEach
    void throttleAgainstC2dmAntiAbuse() throws InterruptedException {
        if (FIRST_TEST.compareAndSet(true, false)) {
            return;
        }
        TimeUnit.SECONDS.sleep(45);
    }

    /**
     * Verifies the supported-platforms set covers both Android
     * variants.
     */
    @Test
    void supportedPlatformsListsBothAndroidVariants() {
        try (var client = FcmClient.newSession()) {
            assertEquals(
                    Set.of(ClientPlatformType.ANDROID, ClientPlatformType.ANDROID_BUSINESS),
                    client.supportedPlatforms());
        }
    }

    /**
     * Verifies {@link FcmClient#close()} is idempotent before
     * authentication runs.
     */
    @Test
    void closeIsIdempotentBeforeAuthenticate() {
        var client = FcmClient.newSession();
        client.close();
        assertDoesNotThrow(client::close);
    }

    /**
     * Verifies the read-only accessors throw before authentication
     * has placed the client in {@code AUTHENTICATED}.
     */
    @Test
    void accessorsRejectUnauthenticatedClient() {
        try (var client = FcmClient.newSession()) {
            assertFalse(client.isAuthenticated());
            assertThrows(IllegalStateException.class, client::getSession);
            assertThrows(IllegalStateException.class, client::getPushToken);
            assertThrows(IllegalStateException.class, client::getPushCode);
        }
    }

    /**
     * Verifies non-Android device profiles are rejected before any
     * network I/O.
     */
    @Test
    void rejectsNonAndroidDevice() {
        try (var client = FcmClient.newSession()) {
            assertThrows(IllegalArgumentException.class,
                    () -> client.authenticate(WhatsAppDevice.ios(false)));
            assertThrows(IllegalArgumentException.class,
                    () -> client.authenticate(WhatsAppDevice.web()));
            assertFalse(client.isAuthenticated());
        }
    }

    /**
     * Verifies the personal Android variant authenticates and surfaces
     * a non-blank FCM registration token.
     */
    @Test
    void authenticatesPersonalAndProducesPushToken() {
        try (var client = FcmClient.newSession()) {
            client.authenticate(WhatsAppDevice.android(false));
            assertTrue(client.isAuthenticated());
            var token = client.getPushToken();
            assertNotNull(token);
            assertFalse(token.isBlank(),
                    () -> "expected non-empty FCM registration token, got: " + token);
        }
    }

    /**
     * Verifies the business Android variant authenticates and surfaces
     * a non-blank FCM registration token.
     */
    @Test
    void authenticatesBusinessAndProducesPushToken() {
        try (var client = FcmClient.newSession()) {
            client.authenticate(WhatsAppDevice.android(true));
            assertTrue(client.isAuthenticated());
            assertFalse(client.getPushToken().isBlank());
        }
    }

    /**
     * Verifies a second
     * {@link FcmClient#authenticate(WhatsAppDevice)} call on an
     * already-authenticated client throws.
     */
    @Test
    void rejectsDoubleAuthenticate() {
        try (var client = FcmClient.newSession()) {
            client.authenticate(WhatsAppDevice.android(false));
            assertThrows(IllegalStateException.class,
                    () -> client.authenticate(WhatsAppDevice.android(false)));
        }
    }

    /**
     * Verifies a captured {@link FcmSession} reloads via
     * {@link FcmClient#loadSession(FcmSession)} and yields the same
     * push token.
     *
     * @implNote
     * This implementation asserts pointer-equality on the FCM token
     * (not just non-empty) because the token is server-issued and
     * persisted in the session; a reload that produced a different
     * token would mean the session round-trip silently dropped the
     * cached value.
     *
     * @throws Exception if the FIS refresh call fails during reload
     */
    @Test
    void sessionRoundTripsThroughLoadSession() throws Exception {
        FcmSession saved;
        String firstToken;
        try (var client = FcmClient.newSession()) {
            client.authenticate(WhatsAppDevice.android(false));
            firstToken = client.getPushToken();
            saved = client.getSession();
            assertNotEquals(0L, saved.androidId());
            assertNotEquals(0L, saved.securityToken());
            assertFalse(saved.fcmToken().isBlank());
        }

        try (var loaded = FcmClient.loadSession(saved)) {
            assertTrue(loaded.isAuthenticated());
            assertEquals(firstToken, loaded.getPushToken());
        }
    }
}
