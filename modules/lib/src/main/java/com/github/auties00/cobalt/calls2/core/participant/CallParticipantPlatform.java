package com.github.auties00.cobalt.calls2.core.participant;

/**
 * Enumerates the client platform the engine attributes to a call participant.
 *
 * <p>Every participant carries a platform code that identifies the kind of WhatsApp
 * client the peer is running, surfaced as the {@code platform} attribute on its
 * membership stanza and exposed through the participant view. The seventeen recognized
 * platforms span the mobile apps ({@link #ANDROID}, {@link #IPHONE}, {@link #IOS_TABLET},
 * {@link #IPAD}, {@link #KAIOS}, {@link #WP}, {@link #WEARM}), the desktop clients
 * ({@link #WINDOWS}, {@link #MACOS}, {@link #MAC_OS_ELECTRON}, {@link #WINDOWS_ELECTRON},
 * {@link #PORTAL}), the web client ({@link #WEB}), the business apps ({@link #SMBA},
 * {@link #SMBI}), the Cloud API client ({@link #CAPI}), and the {@link #UNKNOWN}
 * sentinel.
 *
 * <p>Each constant carries the {@link #code() integer code} the engine stores and the
 * lower-case {@link #token() wire token} the engine emits. The {@link #UNKNOWN} constant
 * doubles as the out-of-range fallback: {@link #ofCode(int)} resolves any code outside
 * {@code 0..16} to {@link #UNKNOWN}, mirroring the engine's own "unknown" default.
 *
 * @implNote This implementation ports the seventeen-entry {@code peer_platform} name
 * table at data segment offset {@code 0x1262fc} of the wa-voip WASM module
 * {@code ff-tScznZ8P} (the array dereferenced by {@code platform_to_cstr}). The recovered
 * tokens in code order are {@code unknown, android, iphone, wp, ios_tablet, kaios,
 * windows, portal, mac_os_electron, windows_electron, wearm, macos, capi, ipad, smba,
 * smbi, web}; any code greater than {@code 16} stringifies to {@code "unknown"}.
 */
public enum CallParticipantPlatform {
    /**
     * An unrecognized or unspecified platform.
     *
     * <p>This is also the fallback the engine reports for any out-of-range platform code.
     */
    UNKNOWN(0, "unknown"),

    /**
     * The Android mobile client.
     */
    ANDROID(1, "android"),

    /**
     * The iPhone mobile client.
     */
    IPHONE(2, "iphone"),

    /**
     * The Windows Phone client.
     */
    WP(3, "wp"),

    /**
     * The iOS tablet client.
     */
    IOS_TABLET(4, "ios_tablet"),

    /**
     * The KaiOS client.
     */
    KAIOS(5, "kaios"),

    /**
     * The native Windows desktop client.
     */
    WINDOWS(6, "windows"),

    /**
     * The Portal device client.
     */
    PORTAL(7, "portal"),

    /**
     * The Electron-era macOS desktop client.
     */
    MAC_OS_ELECTRON(8, "mac_os_electron"),

    /**
     * The Electron-era Windows desktop client.
     */
    WINDOWS_ELECTRON(9, "windows_electron"),

    /**
     * The wearable (watch) client.
     */
    WEARM(10, "wearm"),

    /**
     * The native macOS desktop client.
     */
    MACOS(11, "macos"),

    /**
     * The Cloud API client.
     */
    CAPI(12, "capi"),

    /**
     * The iPad client.
     */
    IPAD(13, "ipad"),

    /**
     * The Android business (SMB) client.
     */
    SMBA(14, "smba"),

    /**
     * The iOS business (SMB) client.
     */
    SMBI(15, "smbi"),

    /**
     * The web client.
     */
    WEB(16, "web");

    /**
     * The integer code the engine stores for this platform.
     */
    private final int code;

    /**
     * The lower-case wire token the engine emits for this platform.
     */
    private final String token;

    /**
     * Constructs a platform constant bound to its engine code and wire token.
     *
     * @param code  the integer code the engine stores
     * @param token the lower-case wire token the engine emits
     */
    CallParticipantPlatform(int code, String token) {
        this.code = code;
        this.token = token;
    }

    /**
     * Returns the integer code the engine stores for this platform.
     *
     * @return the engine platform code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the lower-case wire token the engine emits for this platform.
     *
     * @return the wire token, such as {@code "android"} or {@code "web"}
     */
    public String token() {
        return token;
    }

    /**
     * Returns the platform whose {@linkplain #code() code} equals the given value.
     *
     * <p>Any code outside the defined range {@code 0..16} resolves to {@link #UNKNOWN},
     * matching the engine's out-of-range fallback.
     *
     * @param code the engine platform code to resolve
     * @return the matching platform, or {@link #UNKNOWN} if the code is out of range
     */
    public static CallParticipantPlatform ofCode(int code) {
        for (var platform : values()) {
            if (platform.code == code) {
                return platform;
            }
        }
        return UNKNOWN;
    }

    /**
     * Returns the platform whose {@linkplain #token() wire token} equals the given token.
     *
     * <p>Any unrecognized or {@code null} token resolves to {@link #UNKNOWN}.
     *
     * @param token the wire token to resolve, may be {@code null}
     * @return the matching platform, or {@link #UNKNOWN} if the token is unrecognized
     */
    public static CallParticipantPlatform ofToken(String token) {
        for (var platform : values()) {
            if (platform.token.equals(token)) {
                return platform;
            }
        }
        return UNKNOWN;
    }
}
