package com.github.auties00.cobalt.client.info;

import com.github.auties00.cobalt.Faker;
import org.junit.jupiter.api.Test;


import static com.github.auties00.cobalt.model.device.pairing.ClientPlatformType.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Smoke test suite for the {@link WhatsAppClientInfo} hierarchy.
 *
 * @apiNote Exercises each public flavour ({@link WhatsAppWebClientInfo}, {@link WhatsAppWindowsClientInfo},
 *          {@link WhatsAppAndroidClientInfo} and {@link WhatsAppIosClientInfo}) end to end: confirms that the version
 *          accessor for every platform completes without throwing, and that
 *          {@link WhatsAppMobileClientInfo#computeRegistrationToken(long)} produces a token for each mobile platform.
 * @implNote This implementation reaches out over the network: web tests scrape {@code web.whatsapp.com}, Android tests
 *           download the current APK from the Play Store, iOS tests call the App Store lookup endpoint, and Windows tests
 *           hit the Microsoft Store catalog. The tests intentionally cover behaviour rather than wire output because the
 *           upstream version numbers shift with every WhatsApp release; the suite only asserts no exception escapes the
 *           resolution pipeline.
 */
public class WhatsAppClientInfoTest {
    /**
     * Random Italian mobile number fed as the {@code nationalPhoneNumber} argument to the registration token tests.
     *
     * @apiNote The actual digits are irrelevant; the registration token algorithm is deterministic from any non null
     *          {@code long}, and the suite only asserts the computation does not throw.
     * @implNote This implementation pulls a value from {@link Faker#randomItalianMobile()} so the suite uses different
     *           inputs across runs and any digit dependent branch in the token algorithm is exercised over time.
     */
    private static final Long PHONE_NUMBER_MOCK = Faker.randomItalianMobile();

    /**
     * The web and macOS factories resolve a non null version against {@code web.whatsapp.com}.
     */
    @Test
    public void testWebVersion() {
        assertDoesNotThrow(() -> WhatsAppClientInfo.of(MACOS).version());
        assertDoesNotThrow(() -> WhatsAppClientInfo.of(WINDOWS).version());
    }

    /**
     * The consumer iOS factory resolves a version against the Apple App Store.
     */
    @Test
    public void testPersonalIosVersion() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(IOS).version());
    }

    /**
     * The business iOS factory resolves a version against the Apple App Store.
     */
    @Test
    public void testBusinessIosVersion() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(IOS_BUSINESS).version());
    }

    /**
     * The consumer Android factory resolves a version from the downloaded Play Store APK.
     */
    @Test
    public void testPersonalAndroidVersion() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(ANDROID).version());
    }

    /**
     * The business Android factory resolves a version from the downloaded Play Store APK.
     */
    @Test
    public void testBusinessAndroidVersion() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(ANDROID_BUSINESS).version());
    }

    /**
     * The consumer iOS token algorithm completes for a plausible phone number.
     */
    @Test
    public void testPersonalIosToken() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(IOS).computeRegistrationToken(PHONE_NUMBER_MOCK));
    }

    /**
     * The business iOS token algorithm completes for a plausible phone number.
     */
    @Test
    public void testBusinessIosToken() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(IOS_BUSINESS).computeRegistrationToken(PHONE_NUMBER_MOCK));
    }

    /**
     * The consumer Android token algorithm completes for a plausible phone number.
     */
    @Test
    public void testPersonalAndroidToken() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(ANDROID).computeRegistrationToken(PHONE_NUMBER_MOCK));
    }

    /**
     * The business Android token algorithm completes for a plausible phone number.
     */
    @Test
    public void testBusinessAndroidToken() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(ANDROID_BUSINESS).computeRegistrationToken(PHONE_NUMBER_MOCK));
    }
}
