package com.github.auties00.cobalt.client.linked;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.DevicePlatformType;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.util.DataUtils;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.List;
import java.util.Objects;

/**
 * The hardware and platform identity a Cobalt client advertises as its
 * underlying device.
 *
 * @apiNote
 * Picked at builder time and embedded into the connection handshake so
 * the WhatsApp servers categorise the session for telemetry, feature
 * gating, and User-Agent construction. Cobalt does not auto-detect the
 * host machine; the caller picks one of the pre-built profiles
 * ({@link #web()}, {@link #desktop()}, {@link #ios(boolean)},
 * {@link #android(boolean)}) or constructs one explicitly. Mobile
 * factories randomise the model and OS version from a curated list to
 * reduce fingerprintability.
 *
 * @implNote
 * This implementation is the wire-level counterpart of WA Web's
 * {@code DeviceProps} message in
 * {@code WAWebProtobufsCompanionReg.pb}. The {@link #platform} enum
 * values map one-to-one to {@code DeviceProps$PlatformType}, with the
 * desktop and web flavours pinned per host (Windows hosts pick
 * {@code UWP}, Darwin hosts pick {@code CATALINA}).
 *
 * @see LinkedWhatsAppClientType
 * @see DevicePlatformType
 */
@ProtobufMessage
@WhatsAppWebModule(moduleName = "WAWebProtobufsCompanionReg.pb")
public final class LinkedWhatsAppClientDevice {
    /**
     * The pool of iOS device fingerprints the {@link #ios(boolean)}
     * factory samples from.
     *
     * @apiNote
     * Each entry pairs a real iPhone model with a real iOS version,
     * build number, and internal model identifier so the resulting
     * {@code DeviceProps} payload looks plausible to server-side
     * heuristics.
     *
     * @implNote
     * This implementation samples uniformly via
     * {@link DataUtils#randomInt(int)}
     * on every call to {@link #ios(boolean)}; there is no caching so
     * each registered session is independent.
     */
    private static final List<LinkedWhatsAppClientDevice> IOS_DEVICES = List.of(
            new LinkedWhatsAppClientDevice(
                    "iPhone 7",
                    "Apple",
                    null,
                    ClientAppVersion.of("14.8.1"),
                    "18H107",
                    "iPhone9,3",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone 7",
                    "Apple",
                    null,
                    ClientAppVersion.of("15.8.2"),
                    "19H384",
                    "iPhone9,3",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone 7 Plus",
                    "Apple",
                    null,
                    ClientAppVersion.of("14.8.1"),
                    "18H107",
                    "iPhone9,4",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone 7 Plus",
                    "Apple",
                    null,
                    ClientAppVersion.of("15.8.2"),
                    "19H384",
                    "iPhone9,4",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone 8",
                    "Apple",
                    null,
                    ClientAppVersion.of("13.7"),
                    "17H35",
                    "iPhone10,4",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone 8",
                    "Apple",
                    null,
                    ClientAppVersion.of("14.8.1"),
                    "18H107",
                    "iPhone10,4",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone 8",
                    "Apple",
                    null,
                    ClientAppVersion.of("15.8.2"),
                    "19H384",
                    "iPhone10,4",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone 8",
                    "Apple",
                    null,
                    ClientAppVersion.of("16.7.7"),
                    "20H330",
                    "iPhone10,4",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone 8 Plus",
                    "Apple",
                    null,
                    ClientAppVersion.of("14.8.1"),
                    "18H107",
                    "iPhone10,5",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone 8 Plus",
                    "Apple",
                    null,
                    ClientAppVersion.of("15.8.2"),
                    "19H384",
                    "iPhone10,5",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone 8 Plus",
                    "Apple",
                    null,
                    ClientAppVersion.of("16.7.7"),
                    "20H330",
                    "iPhone10,5",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone X",
                    "Apple",
                    null,
                    ClientAppVersion.of("14.8.1"),
                    "18H107",
                    "iPhone10,6",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone X",
                    "Apple",
                    null,
                    ClientAppVersion.of("15.8.2"),
                    "19H384",
                    "iPhone10,6",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone X",
                    "Apple",
                    null,
                    ClientAppVersion.of("16.7.7"),
                    "20H330",
                    "iPhone10,6",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XR",
                    "Apple",
                    null,
                    ClientAppVersion.of("14.8.1"),
                    "18H107",
                    "iPhone11,8",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XR",
                    "Apple",
                    null,
                    ClientAppVersion.of("15.8.2"),
                    "19H384",
                    "iPhone11,8",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XR",
                    "Apple",
                    null,
                    ClientAppVersion.of("16.7.7"),
                    "20H330",
                    "iPhone11,8",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XR",
                    "Apple",
                    null,
                    ClientAppVersion.of("17.4.1"),
                    "21E236",
                    "iPhone11,8",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XS",
                    "Apple",
                    null,
                    ClientAppVersion.of("14.8.1"),
                    "18H107",
                    "iPhone11,2",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XS",
                    "Apple",
                    null,
                    ClientAppVersion.of("15.8.2"),
                    "19H384",
                    "iPhone11,2",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XS",
                    "Apple",
                    null,
                    ClientAppVersion.of("16.7.7"),
                    "20H330",
                    "iPhone11,2",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XS",
                    "Apple",
                    null,
                    ClientAppVersion.of("17.4.1"),
                    "21E236",
                    "iPhone11,2",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XS Max",
                    "Apple",
                    null,
                    ClientAppVersion.of("14.8.1"),
                    "18H107",
                    "iPhone11,6",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XS Max",
                    "Apple",
                    null,
                    ClientAppVersion.of("15.8.2"),
                    "19H384",
                    "iPhone11,6",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XS Max",
                    "Apple",
                    null,
                    ClientAppVersion.of("16.7.7"),
                    "20H330",
                    "iPhone11,6",
                    LinkedWhatsAppClientType.MOBILE
            ),
            new LinkedWhatsAppClientDevice(
                    "iPhone XS Max",
                    "Apple",
                    null,
                    ClientAppVersion.of("17.4.1"),
                    "21E236",
                    "iPhone11,6",
                    LinkedWhatsAppClientType.MOBILE
            )
    );

    /**
     * Whether the current host is a Mac
     */
    private static final boolean IS_MAC;

    static {
        var osName = System.getProperty("os.name", "").toLowerCase();
        IS_MAC = osName.contains("mac") || osName.contains("darwin");
    }

    /**
     * The user-facing model name (for example {@code "iPhone 8"} or
     * {@code "Pixel_5"}).
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String model;

    /**
     * The device manufacturer name (for example {@code "Apple"} or
     * {@code "Google"}).
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String manufacturer;

    /**
     * The platform identifier the server uses to route the session.
     *
     * @apiNote
     * Mirrors {@code DeviceProps$PlatformType} from
     * {@code WAWebProtobufsCompanionReg.pb}; values include
     * {@link ClientPlatformType#IOS},
     * {@link ClientPlatformType#ANDROID},
     * {@link ClientPlatformType#WEB}, {@link ClientPlatformType#WINDOWS},
     * and {@link ClientPlatformType#MACOS}.
     */
    @WhatsAppWebExport(moduleName = "WAWebProtobufsCompanionReg.pb",
            exports = "DeviceProps$PlatformType", adaptation = WhatsAppAdaptation.DIRECT)
    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    ClientPlatformType platform;

    /**
     * The operating system version running on the device (for example
     * {@code 16.7.7} for iOS or {@code 14} for Android).
     */
    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    ClientAppVersion osDeviceAppVersion;

    /**
     * The OS build number (for example {@code "20H330"} for iOS).
     *
     * @apiNote
     * May be {@code null} on platforms where the build number is not
     * applicable; in that case {@link #osBuildNumber()} falls back to
     * the OS version string.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String osBuildNumber;

    /**
     * The internal hardware model identifier (for example
     * {@code "iPhone10,4"} for iPhone 8 or {@code "Pixel_5"} for
     * Google Pixel 5).
     *
     * @apiNote
     * May be {@code null} for web and desktop clients where the host
     * machine has no equivalent hardware identifier.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String modelId;

    /**
     * The WhatsApp client flavour the device represents.
     *
     * @apiNote
     * Distinguishes a {@link LinkedWhatsAppClientType#WEB} companion from a
     * {@link LinkedWhatsAppClientType#MOBILE} primary client; the value
     * drives transport selection and registration code paths.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.ENUM)
    LinkedWhatsAppClientType clientType;

    /**
     * Constructs a new device descriptor from explicit components.
     *
     * @apiNote
     * Package-private; instances reach embedders through the protobuf
     * deserialiser and through the static factories
     * ({@link #web()}, {@link #desktop()}, {@link #ios(boolean)},
     * {@link #android(boolean)}).
     *
     * @param model              the user-facing model name
     * @param manufacturer       the device manufacturer
     * @param platform           the wire-level platform identifier, or
     *                           {@code null}
     * @param osDeviceAppVersion the operating system version
     * @param osBuildNumber      the OS build number, or {@code null}
     * @param modelId            the internal hardware model identifier,
     *                           or {@code null}
     * @param clientType         the WhatsApp client flavour
     */
    LinkedWhatsAppClientDevice(
            String model,
            String manufacturer,
            ClientPlatformType platform,
            ClientAppVersion osDeviceAppVersion,
            String osBuildNumber,
            String modelId,
            LinkedWhatsAppClientType clientType
    ) {
        this.model = model;
        this.modelId = modelId;
        this.manufacturer = manufacturer;
        this.platform = platform;
        this.osDeviceAppVersion = osDeviceAppVersion;
        this.osBuildNumber = osBuildNumber;
        this.clientType = clientType;
    }

    /**
     * Returns a device descriptor configured as a browser-based
     * WhatsApp Web companion.
     *
     * @apiNote
     * Use this when the embedder wants to look like a Chrome
     * WhatsApp Web tab. The wire platform is pinned to
     * {@link ClientPlatformType#WEB} so the server routes through the
     * WebSocket endpoint used by genuine browser sessions.
     *
     * @implNote
     * This implementation hardcodes the Chrome 10.0 fingerprint that
     * WA Web's {@code WAWebClientPayload} emits in
     * {@code ClientPayload.UserAgent}; manufacturer and model match
     * Chrome on Windows.
     *
     * @return a new web-configured device descriptor
     */
    public static LinkedWhatsAppClientDevice web() {
        return new LinkedWhatsAppClientDevice(
                "Chrome",
                "Google Inc.",
                ClientPlatformType.WEB,
                ClientAppVersion.of("10.0"),
                null,
                null,
                LinkedWhatsAppClientType.WEB
        );
    }

    /**
     * Returns a device descriptor configured as a WhatsApp Desktop
     * companion, auto-detecting the host platform.
     *
     * @apiNote
     * Use this when the embedder wants to look like the native WhatsApp
     * Desktop application. Cobalt's socket layer switches from
     * WebSocket to raw TCP+TLS when the platform is
     * {@link ClientPlatformType#WINDOWS} or
     * {@link ClientPlatformType#MACOS}, matching the transport the
     * Electron-era WhatsApp Desktop app uses.
     *
     * @implNote
     * This implementation reads the JVM's {@code os.name} and picks
     * {@link ClientPlatformType#MACOS} on Darwin hosts and
     * {@link ClientPlatformType#WINDOWS} otherwise; Linux hosts fall
     * back to Windows because there is no native Linux WA Desktop
     * build.
     *
     * @return a desktop-configured device descriptor matching the host
     *         platform, or a Windows descriptor when the host is
     *         neither Windows nor macOS
     */
    public static LinkedWhatsAppClientDevice desktop() {
        if (IS_MAC) {
            return new LinkedWhatsAppClientDevice(
                    "MacBook Pro",
                    "Apple",
                    ClientPlatformType.MACOS,
                    ClientAppVersion.of("14.5"),
                    null,
                    null,
                    LinkedWhatsAppClientType.WEB
            );
        } else {
            return new LinkedWhatsAppClientDevice(
                    "Desktop",
                    "Microsoft",
                    ClientPlatformType.WINDOWS,
                    ClientAppVersion.of("10.0"),
                    null,
                    null,
                    LinkedWhatsAppClientType.WEB
            );
        }
    }

    /**
     * Returns a device descriptor configured as a randomly sampled iOS
     * iPhone.
     *
     * @apiNote
     * Use this when the embedder runs the iOS mobile registration flow
     * and wants a plausible-looking device fingerprint. The model and
     * iOS version are sampled uniformly from {@link #IOS_DEVICES} on
     * every call.
     *
     * @implNote
     * This implementation pins the wire platform to
     * {@link ClientPlatformType#IOS_BUSINESS} when {@code business} is
     * {@code true} and {@link ClientPlatformType#IOS} otherwise; the
     * client type is always {@link LinkedWhatsAppClientType#MOBILE}.
     *
     * @param business {@code true} for the WhatsApp Business variant,
     *                 {@code false} for the consumer variant
     * @return a new iOS-configured device descriptor
     */
    public static LinkedWhatsAppClientDevice ios(boolean business) {
        var device = IOS_DEVICES.get(DataUtils.randomInt(IOS_DEVICES.size()));
        return new LinkedWhatsAppClientDevice(
                device.model,
                device.manufacturer,
                business ? ClientPlatformType.IOS_BUSINESS : ClientPlatformType.IOS,
                device.osDeviceAppVersion,
                device.osBuildNumber,
                device.modelId,
                LinkedWhatsAppClientType.MOBILE
        );
    }

    /**
     * Returns a device descriptor configured as a randomly generated
     * Google Pixel device.
     *
     * @apiNote
     * Use this when the embedder runs the Android mobile registration
     * flow and wants a plausible-looking device fingerprint. The Pixel
     * model number is sampled in {@code [2, 8]} and the Android version
     * in {@code [11, 15]} on every call.
     *
     * @implNote
     * This implementation pins the wire platform to
     * {@link ClientPlatformType#ANDROID_BUSINESS} when {@code business}
     * is {@code true} and {@link ClientPlatformType#ANDROID} otherwise;
     * the client type is always {@link LinkedWhatsAppClientType#MOBILE}.
     *
     * @param business {@code true} for the WhatsApp Business variant,
     *                 {@code false} for the consumer variant
     * @return a new Android-configured device descriptor
     */
    public static LinkedWhatsAppClientDevice android(boolean business) {
        var model = "Pixel_" + DataUtils.randomInt(2, 9);
        return new LinkedWhatsAppClientDevice(
                model,
                "Google",
                business ? ClientPlatformType.ANDROID_BUSINESS : ClientPlatformType.ANDROID,
                ClientAppVersion.of(String.valueOf(DataUtils.randomInt(11, 16))),
                null,
                model,
                LinkedWhatsAppClientType.MOBILE
        );
    }

    /**
     * Returns the OS build number, falling back to the OS version
     * string when the build number is {@code null}.
     *
     * @apiNote
     * Web and desktop descriptors do not carry a build number; the
     * fallback keeps every accessor non-null so registration code can
     * attach the value to a request body without a null guard.
     *
     * @return the OS build number string, never {@code null}
     */
    public String osBuildNumber() {
        return Objects.requireNonNullElse(osBuildNumber, osDeviceAppVersion.toString());
    }

    /**
     * Returns a User-Agent string suitable for HTTP requests issued by
     * this device.
     *
     * @apiNote
     * Used by HTTP-shaped surfaces (companion-link asset fetches,
     * media uploads, mobile registration). Web and desktop platforms
     * return a stock Chrome User-Agent so the request looks browser-like;
     * mobile platforms return the
     * {@code WhatsApp/<version> <platform>/<os> Device/<model>} shape
     * the native clients emit.
     *
     * @param clientDeviceAppVersion the WhatsApp client version to
     *                               embed in the User-Agent
     * @return the formatted User-Agent string
     * @throws IllegalStateException if the underlying platform enum has
     *                               an unexpected value
     */
    public String toUserAgent(ClientAppVersion clientDeviceAppVersion) {
        if(platform == ClientPlatformType.WINDOWS || platform == ClientPlatformType.MACOS || platform == ClientPlatformType.WEB) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";
        }else {
            var platformName = switch (platform) {
                case ANDROID -> "Android";
                case ANDROID_BUSINESS -> "SMBA";
                case IOS -> "iOS";
                case IOS_BUSINESS -> "SMB iOS";
                default -> throw new IllegalStateException("Unexpected value: " + platform);
            };
            var deviceName = switch (platform) {
                case ANDROID, ANDROID_BUSINESS -> manufacturer + "-" + model;
                case IOS, IOS_BUSINESS -> model;
                case MACOS, WINDOWS -> throw new InternalError();
                default -> throw new IllegalStateException("Unexpected value: " + platform);
            };
            var deviceDeviceAppVersion = osDeviceAppVersion.toString();
            return "WhatsApp/%s %s/%s Device/%s".formatted(
                    clientDeviceAppVersion,
                    platformName,
                    deviceDeviceAppVersion,
                    deviceName
            );
        }
    }

    /**
     * Returns a copy of this device with the platform switched to the
     * personal (non-business) variant.
     *
     * @apiNote
     * Use this to switch a Business device into a Consumer device
     * mid-session (for example after a downgrade flow). Devices that
     * are already Consumer or that have no business counterpart return
     * {@code this} unchanged.
     *
     * @return this device if already personal, otherwise a new device
     *         with the personal platform variant
     */
    public LinkedWhatsAppClientDevice toPersonal() {
        return switch (platform) {
            case ANDROID_BUSINESS -> withPlatform(ClientPlatformType.ANDROID);
            case IOS_BUSINESS -> withPlatform(ClientPlatformType.IOS);
            default -> this;
        };
    }

    /**
     * Returns a copy of this device with the platform switched to the
     * business variant.
     *
     * @apiNote
     * Use this to switch a Consumer device into a Business device
     * mid-session (for example after upgrading to WhatsApp Business).
     * Devices that are already Business or that have no business
     * counterpart return {@code this} unchanged.
     *
     * @return this device if already business, otherwise a new device
     *         with the business platform variant
     */
    public LinkedWhatsAppClientDevice toBusiness() {
        return switch (platform) {
            case ANDROID -> withPlatform(ClientPlatformType.ANDROID_BUSINESS);
            case ClientPlatformType.IOS -> withPlatform(ClientPlatformType.IOS_BUSINESS);
            default -> this;
        };
    }

    /**
     * Returns a copy of this device with the specified platform.
     *
     * @apiNote
     * General-purpose copy-with for the {@link #platform} field; used
     * internally by {@link #toPersonal()} and {@link #toBusiness()}. A
     * {@code null} argument keeps the current platform.
     *
     * @param platform the new platform type, or {@code null} to keep
     *                 the current one
     * @return a new device with the given platform
     */
    public LinkedWhatsAppClientDevice withPlatform(ClientPlatformType platform) {
        return new LinkedWhatsAppClientDevice(
                model,
                manufacturer,
                Objects.requireNonNullElse(platform, this.platform),
                osDeviceAppVersion,
                osBuildNumber,
                modelId,
                clientType
        );
    }

    /**
     * Returns the user-facing model name.
     *
     * @return the model name (for example {@code "iPhone 8"} or
     *         {@code "Pixel_5"})
     */
    public String model() {
        return model;
    }

    /**
     * Returns the internal hardware model identifier.
     *
     * @return the model identifier (for example {@code "iPhone10,4"}),
     *         or {@code null} for web and desktop descriptors
     */
    public String modelId() {
        return modelId;
    }

    /**
     * Returns the manufacturer name.
     *
     * @return the manufacturer (for example {@code "Apple"} or
     *         {@code "Google"})
     */
    public String manufacturer() {
        return manufacturer;
    }

    /**
     * Returns the wire-level platform identifier.
     *
     * @return the platform identifying the operating system and client
     *         variant
     */
    public ClientPlatformType platform() {
        return platform;
    }

    /**
     * Returns the operating system version.
     *
     * @return the OS version
     */
    public ClientAppVersion osDeviceAppVersion() {
        return osDeviceAppVersion;
    }

    /**
     * Returns the WhatsApp client flavour.
     *
     * @return the client flavour, either {@link LinkedWhatsAppClientType#MOBILE}
     *         or {@link LinkedWhatsAppClientType#WEB}
     */
    public LinkedWhatsAppClientType clientType() {
        return clientType;
    }

    /**
     * Updates the user-facing model name in place.
     *
     * @apiNote
     * Provided so the protobuf deserialiser can populate the field;
     * application code should prefer {@link #withPlatform(ClientPlatformType)}
     * style copy-withs to avoid mutating shared instances.
     *
     * @param model the new model name
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Updates the manufacturer name in place.
     *
     * @apiNote
     * Provided so the protobuf deserialiser can populate the field.
     *
     * @param manufacturer the new manufacturer name
     */
    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    /**
     * Updates the wire-level platform identifier in place.
     *
     * @apiNote
     * Provided so the protobuf deserialiser can populate the field;
     * application code should prefer {@link #withPlatform(ClientPlatformType)}.
     *
     * @param platform the new platform type
     */
    public void setPlatform(ClientPlatformType platform) {
        this.platform = platform;
    }

    /**
     * Updates the operating system version in place.
     *
     * @apiNote
     * Provided so the protobuf deserialiser can populate the field.
     *
     * @param osDeviceAppVersion the new OS version
     */
    public void setOsDeviceAppVersion(ClientAppVersion osDeviceAppVersion) {
        this.osDeviceAppVersion = osDeviceAppVersion;
    }

    /**
     * Updates the OS build number in place.
     *
     * @apiNote
     * Provided so the protobuf deserialiser can populate the field.
     *
     * @param osBuildNumber the new OS build number, or {@code null}
     */
    public void setOsBuildNumber(String osBuildNumber) {
        this.osBuildNumber = osBuildNumber;
    }

    /**
     * Updates the internal hardware model identifier in place.
     *
     * @apiNote
     * Provided so the protobuf deserialiser can populate the field.
     *
     * @param modelId the new model identifier, or {@code null}
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    /**
     * Updates the WhatsApp client flavour in place.
     *
     * @apiNote
     * Provided so the protobuf deserialiser can populate the field.
     *
     * @param clientType the new client flavour
     */
    public void setClientType(LinkedWhatsAppClientType clientType) {
        this.clientType = clientType;
    }

    /**
     * Compares this device to another object for structural equality.
     *
     * @apiNote
     * Two descriptors are equal when every component (model,
     * manufacturer, platform, OS version, OS build number, model
     * identifier, client flavour) matches.
     *
     * @param o the object to compare with
     * @return {@code true} if the descriptors are structurally equal
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof LinkedWhatsAppClientDevice that
                && Objects.equals(model, that.model)
                && Objects.equals(manufacturer, that.manufacturer)
                && platform == that.platform
                && Objects.equals(osDeviceAppVersion, that.osDeviceAppVersion)
                && Objects.equals(osBuildNumber, that.osBuildNumber)
                && Objects.equals(modelId, that.modelId)
                && clientType == that.clientType;
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(model, manufacturer, platform, osDeviceAppVersion, osBuildNumber, modelId, clientType);
    }

    /**
     * Returns a human-readable description suitable for logs.
     *
     * @return the string representation
     */
    @Override
    public String toString() {
        return "WhatsAppDevice[" +
                "model='" + model + '\'' +
                ", manufacturer='" + manufacturer + '\'' +
                ", platform=" + platform +
                ", osDeviceAppVersion=" + osDeviceAppVersion +
                ", osBuildNumber='" + osBuildNumber + '\'' +
                ", modelId='" + modelId + '\'' +
                ", clientType=" + clientType +
                ']';
    }
}
