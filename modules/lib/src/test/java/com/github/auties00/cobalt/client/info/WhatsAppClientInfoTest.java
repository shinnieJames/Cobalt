package com.github.auties00.cobalt.client.info;

import com.github.auties00.cobalt.infra.Faker;
import org.junit.jupiter.api.Test;


import static com.github.auties00.cobalt.model.device.pairing.ClientPlatformType.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class WhatsAppClientInfoTest {
    private static final Long PHONE_NUMBER_MOCK = Faker.randomItalianMobile();

    @Test
    public void testWebVersion() {
        assertDoesNotThrow(() -> WhatsAppClientInfo.of(MACOS).version());
        assertDoesNotThrow(() -> WhatsAppClientInfo.of(WINDOWS).version());
    }
    
    @Test
    public void testPersonalIosVersion() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(IOS).version());
    }
    
    @Test
    public void testBusinessIosVersion() {
        assertDoesNotThrow(() -> WhatsAppMobileClientInfo.of(IOS_BUSINESS).version());
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
