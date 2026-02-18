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
    private static final int BROWSER = 779;
    private static final int WEBC_WEB_PLATFORM = 899;
    private static final int APP_BUILD = 1657;
    private static final int BEACON_SESSION_ID = 3433;
    private static final int STREAM_ID = 3543;
    private static final int WEBC_TAB_ID = 3727;
    private static final int AB_KEY_2 = 4473;
    private static final int DEVICE_VERSION = 4505;
    private static final int PS_ID = 6005;
    private static final int OC_VERSION = 6251;
    private static final int NUM_CPU = 10317;
    private static final int DEVICE_CLASSIFICATION = 14507;
    private static final int WEBC_REVISION = 18491;

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private WamGlobalEncoder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
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

    // ---- deviceClassification (14507, int) ----

    /**
     * Returns the number of bytes required to encode the device
     * classification global.
     *
     * @param value the device classification enum value (e.g. {@code 3}
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

    // ---- beaconSessionId (3433, int) ----

    /**
     * Returns the number of bytes required to encode the beacon session
     * ID global written per-event when beaconing is active.
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
}
