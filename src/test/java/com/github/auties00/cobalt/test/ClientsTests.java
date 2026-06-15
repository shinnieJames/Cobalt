package com.github.auties00.cobalt.test;

import com.github.auties00.cobalt.client.info.WhatsAppClientInfo;
import com.github.auties00.cobalt.client.info.WhatsAppMobileClientInfo;
import org.junit.jupiter.api.Test;

import static com.github.auties00.cobalt.model.auth.UserAgent.PlatformType.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientsTests {
    private static final Long PHONE_NUMBER_MOCK = 34751869223L;
    private static final String PINNED_IOS_VERSION = "2.25.37.76";

    @Test
    public void testWebVersion() {
        assertDoesNotThrow(() -> WhatsAppClientInfo.of(MACOS).version());
        assertDoesNotThrow(() -> WhatsAppClientInfo.of(WINDOWS).version());
    }

    @Test
    public void testPersonalIosVersion() {
        assertEquals(PINNED_IOS_VERSION, WhatsAppMobileClientInfo.of(IOS).version().toString());
    }

    @Test
    public void testBusinessIosVersion() {
        assertEquals(PINNED_IOS_VERSION, WhatsAppMobileClientInfo.of(IOS_BUSINESS).version().toString());
    }

    @Test
    public void testPersonalAndroidVersion() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(ANDROID).version());
    }

    @Test
    public void testBusinessAndroidVersion() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(ANDROID_BUSINESS).version());
    }

    @Test
    public void testPersonalIosToken() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(IOS).computeRegistrationToken(PHONE_NUMBER_MOCK));
    }

    @Test
    public void testBusinessIosToken() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(IOS_BUSINESS).computeRegistrationToken(PHONE_NUMBER_MOCK));
    }

    @Test
    public void testPersonalAndroidToken() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(ANDROID).computeRegistrationToken(PHONE_NUMBER_MOCK));
    }

    @Test
    public void testBusinessAndroidToken() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(ANDROID_BUSINESS).computeRegistrationToken(PHONE_NUMBER_MOCK));
    }
}
