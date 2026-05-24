package com.github.auties00.cobalt.registration.push.apns;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.client.WhatsAppDevice;
import com.github.auties00.cobalt.exception.WhatsAppRegistrationException;
import com.github.auties00.cobalt.Faker;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.registration.MobileClientRegistration;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live integration suite for {@link ApnsClient}.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class ApnsClientTest {

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
     * Verifies the supported-platforms set lists both iOS variants.
     */
    @Test
    void supportedPlatformsListsBothIosVariants() {
        try (var client = ApnsClient.newSession()) {
            assertEquals(
                    Set.of(ClientPlatformType.IOS, ClientPlatformType.IOS_BUSINESS),
                    client.supportedPlatforms());
        }
    }

    /**
     * Verifies that closing an unauthenticated client is idempotent.
     */
    @Test
    void closeIsIdempotentBeforeAuthenticate() {
        var client = ApnsClient.newSession();
        client.close();
        assertDoesNotThrow(client::close);
    }

    /**
     * Verifies that read-only accessors reject an unauthenticated
     * client.
     */
    @Test
    void accessorsRejectUnauthenticatedClient() {
        try (var client = ApnsClient.newSession()) {
            assertFalse(client.isAuthenticated());
            assertThrows(IllegalStateException.class, client::getSession);
            assertThrows(IllegalStateException.class, client::getPushToken);
            assertThrows(IllegalStateException.class, client::getPushCode);
        }
    }

    /**
     * Verifies that authenticate rejects non-iOS device profiles.
     */
    @Test
    void rejectsNonIosDevice() {
        try (var client = ApnsClient.newSession()) {
            assertThrows(IllegalArgumentException.class,
                    () -> client.authenticate(WhatsAppDevice.android(false)));
            assertThrows(IllegalArgumentException.class,
                    () -> client.authenticate(WhatsAppDevice.web()));
            assertFalse(client.isAuthenticated());
        }
    }

    /**
     * Verifies that authenticating as an iOS personal device yields
     * a 64-character hex push token.
     *
     * @implNote
     * APNS device tokens are 32 raw bytes; the
     * {@link ApnsClient#getPushToken()} contract returns them
     * hex-encoded, so the expected width is 64 characters.
     */
    @Test
    void authenticatesPersonalAndProducesPushToken() {
        try (var client = ApnsClient.newSession()) {
            client.authenticate(WhatsAppDevice.ios(false));
            assertTrue(client.isAuthenticated());
            var token = client.getPushToken();
            assertNotNull(token);
            assertEquals(64, token.length(), () -> "expected 64-char hex token, got: " + token);
        }
    }

    /**
     * Verifies that authenticating as an iOS business device yields
     * a 64-character hex push token.
     */
    @Test
    void authenticatesBusinessAndProducesPushToken() {
        try (var client = ApnsClient.newSession()) {
            client.authenticate(WhatsAppDevice.ios(true));
            assertTrue(client.isAuthenticated());
            assertEquals(64, client.getPushToken().length());
        }
    }

    /**
     * Verifies that calling authenticate twice on the same client
     * is rejected.
     */
    @Test
    void rejectsDoubleAuthenticate() {
        try (var client = ApnsClient.newSession()) {
            client.authenticate(WhatsAppDevice.ios(false));
            assertThrows(IllegalStateException.class,
                    () -> client.authenticate(WhatsAppDevice.ios(false)));
        }
    }

    /**
     * Verifies that a session captured from one client restores
     * into another and produces a valid push token.
     *
     * @implNote
     * Apple rotates the APNS device token across courier sessions,
     * so the reload produces a 64-char hex token but not
     * necessarily the same one; only the activation certificate is
     * durable. The test asserts shape rather than equality on the
     * reloaded token for that reason.
     */
    @Test
    void sessionRoundTripsThroughLoadSession() throws Exception {
        ApnsSession saved;
        try (var client = ApnsClient.newSession()) {
            client.authenticate(WhatsAppDevice.ios(false));
            assertEquals(64, client.getPushToken().length());
            saved = client.getSession();
            assertTrue(saved.privateKeyDer().length > 0);
            assertTrue(saved.publicKeyDer().length > 0);
            assertTrue(saved.deviceCertificate().length > 0);
        }

        try (var loaded = ApnsClient.loadSession(saved)) {
            assertTrue(loaded.isAuthenticated());
            assertEquals(64, loaded.getPushToken().length());
        }
    }
}
