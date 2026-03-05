package com.github.auties00.cobalt.wam.binary;

import static com.github.auties00.cobalt.wam.binary.WamTags.GLOBAL;

/**
 * A stateless encoding facade for the known global attribute entries
 * in the WAM binary protocol.
 *
 * <p>Each WhatsApp-defined global field has a dedicated pair of methods:
 * a {@code xxxSize} method that returns the exact byte count, and a
 * {@code writeXxx} method that writes into a pre-allocated buffer and
 * returns the new offset. Field identifiers are hardcoded inside each
 * method so callers never deal with raw numeric IDs.
 *
 * <p>This class is thread-safe as all methods are static and operate
 * on provided parameters without shared mutable state.
 *
 * @see WamEncoder
 * @see WamTags#GLOBAL
 */
public final class WamGlobalEncoder {
    private static final int MNC = 3;
    private static final int MCC = 5;
    private static final int PLATFORM = 11;
    private static final int DEVICE_NAME = 13;
    private static final int OS_VERSION = 15;
    private static final int APP_VERSION = 17;
    private static final int APP_IS_BETA_RELEASE = 21;
    private static final int NETWORK_IS_WIFI = 23;
    private static final int COMMIT_TIME = 47;
    private static final int BROWSER_VERSION = 295;
    private static final int WEBC_ENV = 633;
    private static final int MEM_CLASS = 655;
    private static final int YEAR_CLASS = 689;
    private static final int WEBC_PHONE_PLATFORM = 707;
    private static final int BROWSER = 779;
    private static final int WEBC_PHONE_CHARGING = 783;
    private static final int WEBC_PHONE_DEVICE_MANUFACTURER = 829;
    private static final int WEBC_PHONE_DEVICE_MODEL = 831;
    private static final int WEBC_PHONE_OS_BUILD_NUMBER = 833;
    private static final int WEBC_PHONE_OS_VERSION = 835;
    private static final int WEBC_BUCKET = 875;
    private static final int WEBC_WEB_PLATFORM = 899;
    private static final int WEBC_PHONE_APP_VERSION = 1005;
    private static final int WEBC_NATIVE_BETA_UPDATES = 1007;
    private static final int WEBC_NATIVE_AUTOLAUNCH = 1009;
    private static final int APP_BUILD = 1657;
    private static final int YEAR_CLASS_2016 = 2617;
    private static final int DATACENTER = 2795;
    private static final int BEACON_SESSION_ID = 3433;
    private static final int STREAM_ID = 3543;
    private static final int WEBC_TAB_ID = 3727;
    private static final int AB_KEY_2 = 4473;
    private static final int DEVICE_VERSION = 4505;
    private static final int EXPO_KEY = 5029;
    private static final int PS_ID = 6005;
    private static final int OC_VERSION = 6251;
    private static final int WEBC_WEB_DEVICE_MANUFACTURER = 6599;
    private static final int WEBC_WEB_DEVICE_MODEL = 6601;
    private static final int WEBC_WEB_OS_RELEASE_NUMBER = 6603;
    private static final int WEBC_WEB_ARCH = 6605;
    private static final int PS_COUNTRY_CODE = 6833;
    private static final int NUM_CPU = 10317;
    private static final int SERVICE_IMPROVEMENT_OPT_OUT = 13293;
    private static final int DEVICE_CLASSIFICATION = 14507;
    private static final int WAMETA_LOGGER_TEST_FILTER = 15881;
    private static final int WEBC_REVISION = 18491;
    private static final int IS_IN_COHORT = 19129;

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private WamGlobalEncoder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // ---- mnc (3, int) ----

    /**
     * Returns the number of bytes required to encode the mobile network
     * code global.
     *
     * <p>The MNC identifies the mobile carrier of the paired phone's SIM
     * card. This global is populated from the phone's connection
     * properties when available.
     *
     * @param value the mobile network code
     * @return the encoded size in bytes
     */
    public static int mncSize(long value) {
        return WamEncoder.intSize(MNC, value);
    }

    /**
     * Writes the mobile network code global attribute into the output
     * array.
     *
     * @param value  the mobile network code
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeMnc(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(MNC, GLOBAL, value, output, offset);
    }

    // ---- mcc (5, int) ----

    /**
     * Returns the number of bytes required to encode the mobile country
     * code global.
     *
     * <p>The MCC identifies the country of the paired phone's SIM card.
     * This global is populated from the phone's connection properties
     * when available.
     *
     * @param value the mobile country code
     * @return the encoded size in bytes
     */
    public static int mccSize(long value) {
        return WamEncoder.intSize(MCC, value);
    }

    /**
     * Writes the mobile country code global attribute into the output
     * array.
     *
     * @param value  the mobile country code
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeMcc(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(MCC, GLOBAL, value, output, offset);
    }

    // ---- generic null global ----

    /**
     * Returns the number of bytes required to encode a null global
     * entry for the given field identifier.
     *
     * <p>A null global is written when a previously non-{@code null}
     * global transitions to {@code null}, matching the
     * {@code VALUE_NULL} tag entry produced by WhatsApp Web's
     * {@code writeGlobalAttribute(buffer, id, null)}.
     *
     * @param fieldId the numeric field identifier
     * @return the encoded size in bytes
     */
    public static int nullGlobalSize(int fieldId) {
        return WamEncoder.nullSize(fieldId);
    }

    /**
     * Writes a null global entry into the output array.
     *
     * @param fieldId the numeric field identifier
     * @param output  the output byte array
     * @param offset  the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeNullGlobal(int fieldId, byte[] output, int offset) {
        return WamEncoder.writeNull(fieldId, GLOBAL, output, offset);
    }

    // ---- platform (11, int) ----

    /**
     * Returns the number of bytes required to encode the platform global.
     *
     * @param value the platform identifier (e.g. {@code 1} for web,
     *              {@code 2} for mobile)
     * @return the encoded size in bytes
     */
    public static int platformSize(long value) {
        return WamEncoder.intSize(PLATFORM, value);
    }

    /**
     * Writes the platform global attribute into the output array.
     *
     * @param value  the platform identifier
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writePlatform(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(PLATFORM, GLOBAL, value, output, offset);
    }

    // ---- deviceName (13, string) ----

    /**
     * Returns the number of bytes required to encode the device name
     * global.
     *
     * @param value the device name, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int deviceNameSize(String value) {
        return WamEncoder.stringSize(DEVICE_NAME, value);
    }

    /**
     * Writes the device name global attribute into the output array.
     *
     * @param value  the device name, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeDeviceName(String value, byte[] output, int offset) {
        return WamEncoder.writeString(DEVICE_NAME, GLOBAL, value, output, offset);
    }

    // ---- osVersion (15, string) ----

    /**
     * Returns the number of bytes required to encode the OS version
     * global.
     *
     * @param value the OS version string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int osVersionSize(String value) {
        return WamEncoder.stringSize(OS_VERSION, value);
    }

    /**
     * Writes the OS version global attribute into the output array.
     *
     * @param value  the OS version string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeOsVersion(String value, byte[] output, int offset) {
        return WamEncoder.writeString(OS_VERSION, GLOBAL, value, output, offset);
    }

    // ---- appVersion (17, string) ----

    /**
     * Returns the number of bytes required to encode the app version
     * global.
     *
     * <p>The app version is transmitted as a UTF-8 string on the wire
     * (e.g. {@code "2.2409.2"}).
     *
     * @param value the version string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int appVersionSize(String value) {
        return WamEncoder.stringSize(APP_VERSION, value);
    }

    /**
     * Writes the app version global attribute into the output array.
     *
     * @param value  the version string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeAppVersion(String value, byte[] output, int offset) {
        return WamEncoder.writeString(APP_VERSION, GLOBAL, value, output, offset);
    }

    // ---- appIsBetaRelease (21, bool as int) ----

    /**
     * Returns the number of bytes required to encode the app-is-beta
     * global.
     *
     * @param value {@code true} if this is a beta release
     * @return the encoded size in bytes
     */
    public static int appIsBetaReleaseSize(boolean value) {
        return WamEncoder.intSize(APP_IS_BETA_RELEASE, value ? 1 : 0);
    }

    /**
     * Writes the app-is-beta global attribute into the output array.
     *
     * @param value  {@code true} if this is a beta release
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeAppIsBetaRelease(boolean value, byte[] output, int offset) {
        return WamEncoder.writeInt(APP_IS_BETA_RELEASE, GLOBAL, value ? 1 : 0, output, offset);
    }

    // ---- networkIsWifi (23, bool as int) ----

    /**
     * Returns the number of bytes required to encode the network-is-wifi
     * global.
     *
     * @param value {@code true} if the device is connected via Wi-Fi
     * @return the encoded size in bytes
     */
    public static int networkIsWifiSize(boolean value) {
        return WamEncoder.intSize(NETWORK_IS_WIFI, value ? 1 : 0);
    }

    /**
     * Writes the network-is-wifi global attribute into the output array.
     *
     * @param value  {@code true} if the device is connected via Wi-Fi
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeNetworkIsWifi(boolean value, byte[] output, int offset) {
        return WamEncoder.writeInt(NETWORK_IS_WIFI, GLOBAL, value ? 1 : 0, output, offset);
    }

    // ---- commitTime (47, int) ----

    /**
     * Returns the number of bytes required to encode the commit-time
     * global written before each event.
     *
     * @param epochSeconds the Unix epoch seconds when the event was
     *                     committed
     * @return the encoded size in bytes
     */
    public static int commitTimeSize(long epochSeconds) {
        return WamEncoder.intSize(COMMIT_TIME, epochSeconds);
    }

    /**
     * Writes the commit-time global attribute into the output array.
     *
     * @param epochSeconds the Unix epoch seconds
     * @param output       the output byte array
     * @param offset       the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeCommitTime(long epochSeconds, byte[] output, int offset) {
        return WamEncoder.writeInt(COMMIT_TIME, GLOBAL, epochSeconds, output, offset);
    }

    // ---- browserVersion (295, string) ----

    /**
     * Returns the number of bytes required to encode the browser version
     * global.
     *
     * @param value the browser version string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int browserVersionSize(String value) {
        return WamEncoder.stringSize(BROWSER_VERSION, value);
    }

    /**
     * Writes the browser version global attribute into the output array.
     *
     * @param value  the browser version string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeBrowserVersion(String value, byte[] output, int offset) {
        return WamEncoder.writeString(BROWSER_VERSION, GLOBAL, value, output, offset);
    }

    // ---- webcEnv (633, int enum) ----

    /**
     * Returns the number of bytes required to encode the web client
     * environment global.
     *
     * @param value the environment code enum value
     * @return the encoded size in bytes
     */
    public static int webcEnvSize(long value) {
        return WamEncoder.intSize(WEBC_ENV, value);
    }

    /**
     * Writes the web client environment global attribute into the
     * output array.
     *
     * @param value  the environment code enum value
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcEnv(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(WEBC_ENV, GLOBAL, value, output, offset);
    }

    // ---- memClass (655, int) ----

    /**
     * Returns the number of bytes required to encode the memory class
     * global.
     *
     * @param value the device memory class in megabytes
     * @return the encoded size in bytes
     */
    public static int memClassSize(long value) {
        return WamEncoder.intSize(MEM_CLASS, value);
    }

    /**
     * Writes the memory class global attribute into the output array.
     *
     * @param value  the device memory class in megabytes
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeMemClass(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(MEM_CLASS, GLOBAL, value, output, offset);
    }

    // ---- yearClass (689, int) ----

    /**
     * Returns the number of bytes required to encode the year class
     * global.
     *
     * <p>The year class is a device performance classification derived
     * from Facebook's device-year-class library. It estimates the year
     * in which the device's hardware specs would have been considered
     * high-end.
     *
     * @param value the year class (e.g. {@code 2024})
     * @return the encoded size in bytes
     */
    public static int yearClassSize(long value) {
        return WamEncoder.intSize(YEAR_CLASS, value);
    }

    /**
     * Writes the year class global attribute into the output array.
     *
     * @param value  the year class (e.g. {@code 2024})
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeYearClass(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(YEAR_CLASS, GLOBAL, value, output, offset);
    }

    // ---- webcPhonePlatform (707, int enum) ----

    /**
     * Returns the number of bytes required to encode the phone platform
     * global.
     *
     * <p>This identifies the platform type of the paired phone
     * (e.g. Android, iOS), as reported by the phone's connection data.
     *
     * @param value the platform type enum value
     * @return the encoded size in bytes
     */
    public static int webcPhonePlatformSize(long value) {
        return WamEncoder.intSize(WEBC_PHONE_PLATFORM, value);
    }

    /**
     * Writes the phone platform global attribute into the output array.
     *
     * @param value  the platform type enum value
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcPhonePlatform(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(WEBC_PHONE_PLATFORM, GLOBAL, value, output, offset);
    }

    // ---- browser (779, string) ----

    /**
     * Returns the number of bytes required to encode the browser global.
     *
     * @param value the browser name, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int browserSize(String value) {
        return WamEncoder.stringSize(BROWSER, value);
    }

    /**
     * Writes the browser global attribute into the output array.
     *
     * @param value  the browser name, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeBrowser(String value, byte[] output, int offset) {
        return WamEncoder.writeString(BROWSER, GLOBAL, value, output, offset);
    }

    // ---- webcPhoneCharging (783, bool as int) ----

    /**
     * Returns the number of bytes required to encode the phone-charging
     * global.
     *
     * @param value {@code true} if the paired phone is charging
     * @return the encoded size in bytes
     */
    public static int webcPhoneChargingSize(boolean value) {
        return WamEncoder.intSize(WEBC_PHONE_CHARGING, value ? 1 : 0);
    }

    /**
     * Writes the phone-charging global attribute into the output array.
     *
     * @param value  {@code true} if the paired phone is charging
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcPhoneCharging(boolean value, byte[] output, int offset) {
        return WamEncoder.writeInt(WEBC_PHONE_CHARGING, GLOBAL, value ? 1 : 0, output, offset);
    }

    // ---- webcPhoneDeviceManufacturer (829, string) ----

    /**
     * Returns the number of bytes required to encode the phone device
     * manufacturer global.
     *
     * @param value the manufacturer name, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int webcPhoneDeviceManufacturerSize(String value) {
        return WamEncoder.stringSize(WEBC_PHONE_DEVICE_MANUFACTURER, value);
    }

    /**
     * Writes the phone device manufacturer global attribute into the
     * output array.
     *
     * @param value  the manufacturer name, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcPhoneDeviceManufacturer(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WEBC_PHONE_DEVICE_MANUFACTURER, GLOBAL, value, output, offset);
    }

    // ---- webcPhoneDeviceModel (831, string) ----

    /**
     * Returns the number of bytes required to encode the phone device
     * model global.
     *
     * @param value the model name, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int webcPhoneDeviceModelSize(String value) {
        return WamEncoder.stringSize(WEBC_PHONE_DEVICE_MODEL, value);
    }

    /**
     * Writes the phone device model global attribute into the output
     * array.
     *
     * @param value  the model name, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcPhoneDeviceModel(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WEBC_PHONE_DEVICE_MODEL, GLOBAL, value, output, offset);
    }

    // ---- webcPhoneOsBuildNumber (833, string) ----

    /**
     * Returns the number of bytes required to encode the phone OS build
     * number global.
     *
     * @param value the build number string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int webcPhoneOsBuildNumberSize(String value) {
        return WamEncoder.stringSize(WEBC_PHONE_OS_BUILD_NUMBER, value);
    }

    /**
     * Writes the phone OS build number global attribute into the output
     * array.
     *
     * @param value  the build number string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcPhoneOsBuildNumber(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WEBC_PHONE_OS_BUILD_NUMBER, GLOBAL, value, output, offset);
    }

    // ---- webcPhoneOsVersion (835, string) ----

    /**
     * Returns the number of bytes required to encode the phone OS
     * version global.
     *
     * @param value the version string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int webcPhoneOsVersionSize(String value) {
        return WamEncoder.stringSize(WEBC_PHONE_OS_VERSION, value);
    }

    /**
     * Writes the phone OS version global attribute into the output
     * array.
     *
     * @param value  the version string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcPhoneOsVersion(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WEBC_PHONE_OS_VERSION, GLOBAL, value, output, offset);
    }

    // ---- webcBucket (875, string) ----

    /**
     * Returns the number of bytes required to encode the web bucket
     * global.
     *
     * @param value the experiment bucket string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int webcBucketSize(String value) {
        return WamEncoder.stringSize(WEBC_BUCKET, value);
    }

    /**
     * Writes the web bucket global attribute into the output array.
     *
     * @param value  the experiment bucket string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcBucket(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WEBC_BUCKET, GLOBAL, value, output, offset);
    }

    // ---- webcWebPlatform (899, int enum) ----

    /**
     * Returns the number of bytes required to encode the web platform
     * type global.
     *
     * @param value the web platform enum value
     * @return the encoded size in bytes
     */
    public static int webcWebPlatformSize(long value) {
        return WamEncoder.intSize(WEBC_WEB_PLATFORM, value);
    }

    /**
     * Writes the web platform type global attribute into the output
     * array.
     *
     * @param value  the web platform enum value
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcWebPlatform(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(WEBC_WEB_PLATFORM, GLOBAL, value, output, offset);
    }

    // ---- webcPhoneAppVersion (1005, string) ----

    /**
     * Returns the number of bytes required to encode the phone app
     * version global.
     *
     * <p>This is the WhatsApp version running on the paired phone,
     * populated from the phone's connection data.
     *
     * @param value the version string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int webcPhoneAppVersionSize(String value) {
        return WamEncoder.stringSize(WEBC_PHONE_APP_VERSION, value);
    }

    /**
     * Writes the phone app version global attribute into the output
     * array.
     *
     * @param value  the version string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcPhoneAppVersion(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WEBC_PHONE_APP_VERSION, GLOBAL, value, output, offset);
    }

    // ---- webcNativeBetaUpdates (1007, bool as int) ----

    /**
     * Returns the number of bytes required to encode the native beta
     * updates global.
     *
     * @param value {@code true} if the native app is configured for
     *              beta updates
     * @return the encoded size in bytes
     */
    public static int webcNativeBetaUpdatesSize(boolean value) {
        return WamEncoder.intSize(WEBC_NATIVE_BETA_UPDATES, value ? 1 : 0);
    }

    /**
     * Writes the native beta updates global attribute into the output
     * array.
     *
     * @param value  {@code true} if configured for beta updates
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcNativeBetaUpdates(boolean value, byte[] output, int offset) {
        return WamEncoder.writeInt(WEBC_NATIVE_BETA_UPDATES, GLOBAL, value ? 1 : 0, output, offset);
    }

    // ---- webcNativeAutolaunch (1009, bool as int) ----

    /**
     * Returns the number of bytes required to encode the native
     * autolaunch global.
     *
     * @param value {@code true} if the native app is configured for
     *              autolaunch
     * @return the encoded size in bytes
     */
    public static int webcNativeAutolaunchSize(boolean value) {
        return WamEncoder.intSize(WEBC_NATIVE_AUTOLAUNCH, value ? 1 : 0);
    }

    /**
     * Writes the native autolaunch global attribute into the output
     * array.
     *
     * @param value  {@code true} if configured for autolaunch
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcNativeAutolaunch(boolean value, byte[] output, int offset) {
        return WamEncoder.writeInt(WEBC_NATIVE_AUTOLAUNCH, GLOBAL, value ? 1 : 0, output, offset);
    }

    // ---- appBuild (1657, int enum) ----

    /**
     * Returns the number of bytes required to encode the app build type
     * global.
     *
     * @param value the app build type enum value
     * @return the encoded size in bytes
     */
    public static int appBuildSize(long value) {
        return WamEncoder.intSize(APP_BUILD, value);
    }

    /**
     * Writes the app build type global attribute into the output array.
     *
     * @param value  the app build type enum value
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeAppBuild(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(APP_BUILD, GLOBAL, value, output, offset);
    }

    // ---- yearClass2016 (2617, int) ----

    /**
     * Returns the number of bytes required to encode the year class 2016
     * global.
     *
     * <p>A variant of the year class metric using a 2016-era
     * classification baseline.
     *
     * @param value the year class value
     * @return the encoded size in bytes
     */
    public static int yearClass2016Size(long value) {
        return WamEncoder.intSize(YEAR_CLASS_2016, value);
    }

    /**
     * Writes the year class 2016 global attribute into the output array.
     *
     * @param value  the year class value
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeYearClass2016(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(YEAR_CLASS_2016, GLOBAL, value, output, offset);
    }

    // ---- datacenter (2795, string) ----

    /**
     * Returns the number of bytes required to encode the datacenter
     * global.
     *
     * <p>This identifies the server datacenter handling the session,
     * typically set by the server.
     *
     * @param value the datacenter identifier, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int datacenterSize(String value) {
        return WamEncoder.stringSize(DATACENTER, value);
    }

    /**
     * Writes the datacenter global attribute into the output array.
     *
     * @param value  the datacenter identifier, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeDatacenter(String value, byte[] output, int offset) {
        return WamEncoder.writeString(DATACENTER, GLOBAL, value, output, offset);
    }

    // ---- beaconSessionId (3433, int) ----

    /**
     * Returns the number of bytes required to encode the beacon session
     * ID global written per-event when beaconing is active.
     *
     * <p>WAWebWamGlobals declares this field with ID {@code 18529}, but
     * the actual runtime code in WAWebWamLibContext hardcodes
     * {@code 3433}. This implementation uses the runtime value to match
     * what is actually written to the wire.
     *
     * @param value the beaconing sequence number
     * @return the encoded size in bytes
     */
    public static int beaconSessionIdSize(long value) {
        return WamEncoder.intSize(BEACON_SESSION_ID, value);
    }

    /**
     * Writes the beacon session ID global attribute into the output
     * array.
     *
     * @param value  the beaconing sequence number
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeBeaconSessionId(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(BEACON_SESSION_ID, GLOBAL, value, output, offset);
    }

    // ---- streamId (3543, int) ----

    /**
     * Returns the number of bytes required to encode the stream ID
     * global.
     *
     * @param value the stream identifier
     * @return the encoded size in bytes
     */
    public static int streamIdSize(long value) {
        return WamEncoder.intSize(STREAM_ID, value);
    }

    /**
     * Writes the stream ID global attribute into the output array.
     *
     * @param value  the stream identifier
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeStreamId(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(STREAM_ID, GLOBAL, value, output, offset);
    }

    // ---- webcTabId (3727, string) ----

    /**
     * Returns the number of bytes required to encode the tab ID global.
     *
     * @param value the tab identifier string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int webcTabIdSize(String value) {
        return WamEncoder.stringSize(WEBC_TAB_ID, value);
    }

    /**
     * Writes the tab ID global attribute into the output array.
     *
     * @param value  the tab identifier string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcTabId(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WEBC_TAB_ID, GLOBAL, value, output, offset);
    }

    // ---- abKey2 (4473, string) ----

    /**
     * Returns the number of bytes required to encode the AB key 2
     * global.
     *
     * @param value the AB key string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int abKey2Size(String value) {
        return WamEncoder.stringSize(AB_KEY_2, value);
    }

    /**
     * Writes the AB key 2 global attribute into the output array.
     *
     * @param value  the AB key string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeAbKey2(String value, byte[] output, int offset) {
        return WamEncoder.writeString(AB_KEY_2, GLOBAL, value, output, offset);
    }

    // ---- deviceVersion (4505, string) ----

    /**
     * Returns the number of bytes required to encode the device version
     * global.
     *
     * @param value the device version string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int deviceVersionSize(String value) {
        return WamEncoder.stringSize(DEVICE_VERSION, value);
    }

    /**
     * Writes the device version global attribute into the output array.
     *
     * @param value  the device version string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeDeviceVersion(String value, byte[] output, int offset) {
        return WamEncoder.writeString(DEVICE_VERSION, GLOBAL, value, output, offset);
    }

    // ---- expoKey (5029, string) ----

    /**
     * Returns the number of bytes required to encode the exposure key
     * global.
     *
     * <p>The exposure key tracks which AB test configurations were
     * accessed during the session.
     *
     * @param value the exposure key string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int expoKeySize(String value) {
        return WamEncoder.stringSize(EXPO_KEY, value);
    }

    /**
     * Writes the exposure key global attribute into the output array.
     *
     * @param value  the exposure key string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeExpoKey(String value, byte[] output, int offset) {
        return WamEncoder.writeString(EXPO_KEY, GLOBAL, value, output, offset);
    }

    // ---- psId (6005, string) ----

    /**
     * Returns the number of bytes required to encode the private stats
     * identifier global.
     *
     * <p>This global is only written for
     * {@link com.github.auties00.cobalt.wam.type.WamChannel#PRIVATE PRIVATE}
     * channel buffers.
     *
     * @param value the PS identifier string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int psIdSize(String value) {
        return WamEncoder.stringSize(PS_ID, value);
    }

    /**
     * Writes the private stats identifier global attribute into the
     * output array.
     *
     * @param value  the PS identifier string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writePsId(String value, byte[] output, int offset) {
        return WamEncoder.writeString(PS_ID, GLOBAL, value, output, offset);
    }

    // ---- ocVersion (6251, int) ----

    /**
     * Returns the number of bytes required to encode the official client
     * version global.
     *
     * @param value the official client version number
     * @return the encoded size in bytes
     */
    public static int ocVersionSize(long value) {
        return WamEncoder.intSize(OC_VERSION, value);
    }

    /**
     * Writes the official client version global attribute into the
     * output array.
     *
     * @param value  the official client version number
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeOcVersion(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(OC_VERSION, GLOBAL, value, output, offset);
    }

    // ---- webcWebDeviceManufacturer (6599, string) ----

    /**
     * Returns the number of bytes required to encode the web device
     * manufacturer global.
     *
     * @param value the manufacturer name, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int webcWebDeviceManufacturerSize(String value) {
        return WamEncoder.stringSize(WEBC_WEB_DEVICE_MANUFACTURER, value);
    }

    /**
     * Writes the web device manufacturer global attribute into the
     * output array.
     *
     * @param value  the manufacturer name, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcWebDeviceManufacturer(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WEBC_WEB_DEVICE_MANUFACTURER, GLOBAL, value, output, offset);
    }

    // ---- webcWebDeviceModel (6601, string) ----

    /**
     * Returns the number of bytes required to encode the web device
     * model global.
     *
     * @param value the model name, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int webcWebDeviceModelSize(String value) {
        return WamEncoder.stringSize(WEBC_WEB_DEVICE_MODEL, value);
    }

    /**
     * Writes the web device model global attribute into the output
     * array.
     *
     * @param value  the model name, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcWebDeviceModel(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WEBC_WEB_DEVICE_MODEL, GLOBAL, value, output, offset);
    }

    // ---- webcWebOsReleaseNumber (6603, string) ----

    /**
     * Returns the number of bytes required to encode the web OS release
     * number global.
     *
     * @param value the release number string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int webcWebOsReleaseNumberSize(String value) {
        return WamEncoder.stringSize(WEBC_WEB_OS_RELEASE_NUMBER, value);
    }

    /**
     * Writes the web OS release number global attribute into the output
     * array.
     *
     * @param value  the release number string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcWebOsReleaseNumber(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WEBC_WEB_OS_RELEASE_NUMBER, GLOBAL, value, output, offset);
    }

    // ---- webcWebArch (6605, string) ----

    /**
     * Returns the number of bytes required to encode the web
     * architecture global.
     *
     * @param value the architecture string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int webcWebArchSize(String value) {
        return WamEncoder.stringSize(WEBC_WEB_ARCH, value);
    }

    /**
     * Writes the web architecture global attribute into the output
     * array.
     *
     * @param value  the architecture string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcWebArch(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WEBC_WEB_ARCH, GLOBAL, value, output, offset);
    }

    // ---- psCountryCode (6833, string) ----

    /**
     * Returns the number of bytes required to encode the private stats
     * country code global.
     *
     * <p>This is derived from the user's phone number and written only
     * on the {@code PRIVATE} channel.
     *
     * @param value the country code string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int psCountryCodeSize(String value) {
        return WamEncoder.stringSize(PS_COUNTRY_CODE, value);
    }

    /**
     * Writes the private stats country code global attribute into the
     * output array.
     *
     * @param value  the country code string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writePsCountryCode(String value, byte[] output, int offset) {
        return WamEncoder.writeString(PS_COUNTRY_CODE, GLOBAL, value, output, offset);
    }

    // ---- numCpu (10317, int) ----

    /**
     * Returns the number of bytes required to encode the CPU count
     * global.
     *
     * @param value the number of available processors
     * @return the encoded size in bytes
     */
    public static int numCpuSize(long value) {
        return WamEncoder.intSize(NUM_CPU, value);
    }

    /**
     * Writes the CPU count global attribute into the output array.
     *
     * @param value  the number of available processors
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeNumCpu(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(NUM_CPU, GLOBAL, value, output, offset);
    }

    // ---- serviceImprovementOptOut (13293, bool as int) ----

    /**
     * Returns the number of bytes required to encode the service
     * improvement opt-out global.
     *
     * @param value {@code true} if the user has opted out of service
     *              improvement data collection
     * @return the encoded size in bytes
     */
    public static int serviceImprovementOptOutSize(boolean value) {
        return WamEncoder.intSize(SERVICE_IMPROVEMENT_OPT_OUT, value ? 1 : 0);
    }

    /**
     * Writes the service improvement opt-out global attribute into the
     * output array.
     *
     * @param value  {@code true} if opted out
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeServiceImprovementOptOut(boolean value, byte[] output, int offset) {
        return WamEncoder.writeInt(SERVICE_IMPROVEMENT_OPT_OUT, GLOBAL, value ? 1 : 0, output, offset);
    }

    // ---- deviceClassification (14507, int) ----

    /**
     * Returns the number of bytes required to encode the device
     * classification global.
     *
     * @param value the device classification enum value (e.g. {@code 4}
     *              for DESKTOP)
     * @return the encoded size in bytes
     */
    public static int deviceClassificationSize(long value) {
        return WamEncoder.intSize(DEVICE_CLASSIFICATION, value);
    }

    /**
     * Writes the device classification global attribute into the output
     * array.
     *
     * @param value  the device classification enum value
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeDeviceClassification(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(DEVICE_CLASSIFICATION, GLOBAL, value, output, offset);
    }

    // ---- wametaLoggerTestFilter (15881, string) ----

    /**
     * Returns the number of bytes required to encode the WAMeta logger
     * test filter global.
     *
     * @param value the test filter string, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int wametaLoggerTestFilterSize(String value) {
        return WamEncoder.stringSize(WAMETA_LOGGER_TEST_FILTER, value);
    }

    /**
     * Writes the WAMeta logger test filter global attribute into the
     * output array.
     *
     * @param value  the test filter string, must not be {@code null}
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWametaLoggerTestFilter(String value, byte[] output, int offset) {
        return WamEncoder.writeString(WAMETA_LOGGER_TEST_FILTER, GLOBAL, value, output, offset);
    }

    // ---- webcRevision (18491, int) ----

    /**
     * Returns the number of bytes required to encode the web client
     * revision global.
     *
     * @param value the client revision number
     * @return the encoded size in bytes
     */
    public static int webcRevisionSize(long value) {
        return WamEncoder.intSize(WEBC_REVISION, value);
    }

    /**
     * Writes the web client revision global attribute into the output
     * array.
     *
     * @param value  the client revision number
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeWebcRevision(long value, byte[] output, int offset) {
        return WamEncoder.writeInt(WEBC_REVISION, GLOBAL, value, output, offset);
    }

    // ---- isInCohort (19129, bool as int) ----

    /**
     * Returns the number of bytes required to encode the is-in-cohort
     * global.
     *
     * @param value {@code true} if the user is in a cohort
     * @return the encoded size in bytes
     */
    public static int isInCohortSize(boolean value) {
        return WamEncoder.intSize(IS_IN_COHORT, value ? 1 : 0);
    }

    /**
     * Writes the is-in-cohort global attribute into the output array.
     *
     * @param value  {@code true} if in cohort
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeIsInCohort(boolean value, byte[] output, int offset) {
        return WamEncoder.writeInt(IS_IN_COHORT, GLOBAL, value ? 1 : 0, output, offset);
    }
}
