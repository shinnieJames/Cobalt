package com.github.auties00.cobalt.client.info;

import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;

public sealed interface WhatsAppMobileClientInfo
        extends WhatsAppClientInfo
        permits WhatsAppAndroidClientInfo, WhatsAppIosClientInfo {
    static WhatsAppMobileClientInfo of(ClientPlatformType platform) {
        return switch (platform) {
            case ANDROID -> WhatsAppAndroidClientInfo.ofPersonal();
            case IOS -> WhatsAppIosClientInfo.ofPersonal();
            case ANDROID_BUSINESS -> WhatsAppAndroidClientInfo.ofBusiness();
            case IOS_BUSINESS -> WhatsAppIosClientInfo.ofBusiness();
            default -> throw new IllegalStateException("Unexpected value: " + platform);
        };
    }

    boolean business();
    String computeRegistrationToken(long nationalPhoneNumber);
}
