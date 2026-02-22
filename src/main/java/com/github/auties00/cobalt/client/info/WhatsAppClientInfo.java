package com.github.auties00.cobalt.client.info;

import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;

public sealed interface WhatsAppClientInfo
        permits WhatsAppWebClientInfo, WhatsAppMobileClientInfo {
    static WhatsAppClientInfo of(ClientPlatformType platform) {
        return switch (platform) {
            case ANDROID -> WhatsAppAndroidClientInfo.ofPersonal();
            case IOS -> WhatsAppIosClientInfo.ofPersonal();
            case ANDROID_BUSINESS -> WhatsAppAndroidClientInfo.ofBusiness();
            case IOS_BUSINESS -> WhatsAppIosClientInfo.ofBusiness();
            case WINDOWS, MACOS -> WhatsAppWebClientInfo.of();
            default -> throw new IllegalStateException("Unexpected value: " + platform);
        };
    }

    ClientAppVersion version();
}
