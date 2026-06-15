package com.github.auties00.cobalt.client.info;

import com.github.auties00.cobalt.model.auth.Version;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class WhatsAppIosClientInfo implements WhatsAppMobileClientInfo {
    private static final Version MOBILE_IOS_VERSION = Version.of("2.25.37.76");

    private static volatile WhatsAppIosClientInfo personalIpaInfo;
    private static final Object personalIpaInfoLock = new Object();
    private static volatile WhatsAppIosClientInfo businessIpaInfo;
    private static final Object businessIpaInfoLock = new Object();

    private static final String MOBILE_IOS_STATIC = "0a1mLfGUIBVrMKF1RdvLI5lkRBvof6vn0fD2QRSM";
    private static final String MOBILE_BUSINESS_IOS_STATIC = "USUDuDYDeQhY4RF2fCSp5m3F6kJ1M2J8wS7bbNA2";

    private final Version version;
    private final boolean business;

    private WhatsAppIosClientInfo(Version version, boolean business) {
        this.version = version;
        this.business = business;
    }

    public static WhatsAppIosClientInfo ofPersonal() {
        if (personalIpaInfo == null) {
            synchronized (personalIpaInfoLock) {
                if(personalIpaInfo == null) {
                    personalIpaInfo = queryIpaInfo(false);
                }
            }
        }
        return personalIpaInfo;
    }

    public static WhatsAppIosClientInfo ofBusiness() {
        if (businessIpaInfo == null) {
            synchronized (businessIpaInfoLock) {
                if(businessIpaInfo == null) {
                    businessIpaInfo = queryIpaInfo(true);
                }
            }
        }
        return businessIpaInfo;
    }

    private static WhatsAppIosClientInfo queryIpaInfo(boolean business) {
        return new WhatsAppIosClientInfo(MOBILE_IOS_VERSION, business);
    }

    @Override
    public Version version() {
        return version;
    }

    @Override
    public boolean business() {
        return business;
    }

    @Override
    public String computeRegistrationToken(long nationalPhoneNumber) {
        try {
            var staticToken = business ? MOBILE_BUSINESS_IOS_STATIC : MOBILE_IOS_STATIC;
            var token = staticToken + HexFormat.of().formatHex(version.toHash()) + nationalPhoneNumber;
            var digest = MessageDigest.getInstance("MD5");
            digest.update(token.getBytes());
            var result = digest.digest();
            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException exception) {
            throw new UnsupportedOperationException("Missing md5 implementation", exception);
        }
    }
}
