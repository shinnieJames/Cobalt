package com.github.auties00.cobalt.registration.push.fcm.checkin;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * Wire body for the gzipped POST against
 * {@code android.clients.google.com/checkin}, the first step of the
 * Android device-registration handshake.
 *
 * @apiNote
 * Field numbers come from Google's internal AndroidCheckin protobuf
 * (the public reference is the leaked {@code checkin.proto} schema);
 * only the subset Cobalt actually needs is encoded. The corresponding
 * response is decoded by {@link FcmCheckinResponse}.
 */
@ProtobufMessage(name = "FcmCheckinRequest")
public final class FcmCheckinRequest {
    /**
     * Existing Android id.
     *
     * @apiNote
     * {@code 0} for a fresh registration; non-zero when the device
     * is re-confirming a previously assigned id.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.INT64)
    long id;

    /**
     * Nested device description.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    Checkin checkin;

    /**
     * UI locale.
     *
     * @apiNote
     * Cobalt sends {@code "en_US"} unconditionally.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String locale;

    /**
     * Random per-checkin logging id.
     *
     * @apiNote
     * Generated as {@code SecureRandom.nextLong() & MAX_LONG} so the
     * server cannot fingerprint Cobalt across calls via a stable
     * value.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.INT64)
    long loggingId;

    /**
     * Time zone.
     *
     * @apiNote
     * Cobalt sends {@code "UTC"} unconditionally.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.STRING)
    String timeZone;

    /**
     * Checkin protocol version.
     *
     * @apiNote
     * The native Android client sends {@code 3}.
     */
    @ProtobufProperty(index = 14, type = ProtobufType.INT32)
    int version;

    /**
     * Numeric flag Cobalt sets to {@code 0}.
     *
     * @apiNote
     * Carried so the wire body matches what the server expects;
     * non-zero values are not modeled.
     */
    @ProtobufProperty(index = 20, type = ProtobufType.INT64)
    long fragment;

    /**
     * Numeric flag Cobalt sets to {@code 0}.
     *
     * @apiNote
     * Same rationale as {@link #fragment}: shipped to keep the wire
     * body intact.
     */
    @ProtobufProperty(index = 22, type = ProtobufType.INT64)
    long userSerialNumber;

    /**
     * Constructs a new request with the given values.
     *
     * @param id               the existing Android id
     * @param checkin          the nested device description
     * @param locale           the UI locale
     * @param loggingId        the random per-checkin logging id
     * @param timeZone         the time zone
     * @param version          the protocol version
     * @param fragment         the fragment flag
     * @param userSerialNumber the multi-user serial number flag
     */
    FcmCheckinRequest(long id, Checkin checkin, String locale, long loggingId,
                      String timeZone, int version, long fragment, long userSerialNumber) {
        this.id = id;
        this.checkin = checkin;
        this.locale = locale;
        this.loggingId = loggingId;
        this.timeZone = timeZone;
        this.version = version;
        this.fragment = fragment;
        this.userSerialNumber = userSerialNumber;
    }

    /**
     * Nested device-and-event payload (field 4 of the outer request).
     */
    @ProtobufMessage(name = "FcmCheckinRequest.Checkin")
    public static final class Checkin {
        /**
         * Build description of the device being impersonated.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        Build build;

        /**
         * Last-checkin timestamp, in milliseconds since epoch.
         *
         * @apiNote
         * {@code 0} for a fresh registration.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.INT64)
        long lastCheckinMs;

        /**
         * One synthetic event entry.
         *
         * @apiNote
         * Cobalt always sends a single {@code event_log_start}
         * event; the server expects at least one event to be present.
         */
        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        Event event;

        /**
         * Multi-user serial number.
         *
         * @apiNote
         * {@code 0} on stock Android.
         */
        @ProtobufProperty(index = 9, type = ProtobufType.INT64)
        long userNumber;

        /**
         * Constructs a new checkin block.
         *
         * @param build         the device build description
         * @param lastCheckinMs the last-checkin timestamp
         * @param event         the synthetic event entry
         * @param userNumber    the multi-user serial number
         */
        Checkin(Build build, long lastCheckinMs, Event event, long userNumber) {
            this.build = build;
            this.lastCheckinMs = lastCheckinMs;
            this.event = event;
            this.userNumber = userNumber;
        }
    }

    /**
     * Build description (field 1 of {@link Checkin}).
     *
     * @apiNote
     * Cobalt impersonates a Nexus 7
     * ({@code "google/razor/flo:5.0.1/..."}) running SDK 30; the
     * synthetic profile is deliberately stable across embedders.
     */
    @ProtobufMessage(name = "FcmCheckinRequest.Build")
    public static final class Build {
        /**
         * Build fingerprint, e.g.
         * {@code "google/razor/flo:5.0.1/LRX22C/1602158:user/release-keys"}.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String fingerprint;

        /**
         * Hardware id, e.g. {@code "flo"}.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String hardware;

        /**
         * Brand, e.g. {@code "google"}.
         */
        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String brand;

        /**
         * Client identifier.
         *
         * @apiNote
         * Always {@code "android-google"} in Cobalt's synthetic
         * profile.
         */
        @ProtobufProperty(index = 6, type = ProtobufType.STRING)
        String clientId;

        /**
         * Build timestamp, in epoch seconds.
         */
        @ProtobufProperty(index = 7, type = ProtobufType.INT64)
        long timeMs;

        /**
         * SDK version (Android API level).
         *
         * @apiNote
         * Cobalt sends {@code 30}.
         */
        @ProtobufProperty(index = 10, type = ProtobufType.INT32)
        int sdkVersion;

        /**
         * Marketing model, e.g. {@code "Nexus 7"}.
         */
        @ProtobufProperty(index = 11, type = ProtobufType.STRING)
        String model;

        /**
         * OEM, e.g. {@code "asus"}.
         */
        @ProtobufProperty(index = 12, type = ProtobufType.STRING)
        String manufacturer;

        /**
         * Internal product code, e.g. {@code "razor"}.
         */
        @ProtobufProperty(index = 13, type = ProtobufType.STRING)
        String product;

        /**
         * Whether an OTA update is installed.
         *
         * @apiNote
         * {@code false} for a fresh synthetic device.
         */
        @ProtobufProperty(index = 14, type = ProtobufType.BOOL)
        boolean otaInstalled;

        /**
         * Constructs a new build block.
         *
         * @param fingerprint  the build fingerprint
         * @param hardware     the hardware id
         * @param brand        the brand
         * @param clientId     the client identifier
         * @param timeMs       the build timestamp
         * @param sdkVersion   the SDK version
         * @param model        the marketing model
         * @param manufacturer the OEM
         * @param product      the internal product code
         * @param otaInstalled whether an OTA update is installed
         */
        Build(String fingerprint, String hardware, String brand, String clientId,
              long timeMs, int sdkVersion, String model, String manufacturer,
              String product, boolean otaInstalled) {
            this.fingerprint = fingerprint;
            this.hardware = hardware;
            this.brand = brand;
            this.clientId = clientId;
            this.timeMs = timeMs;
            this.sdkVersion = sdkVersion;
            this.model = model;
            this.manufacturer = manufacturer;
            this.product = product;
            this.otaInstalled = otaInstalled;
        }
    }

    /**
     * Single event entry (field 3 of {@link Checkin}).
     */
    @ProtobufMessage(name = "FcmCheckinRequest.Event")
    public static final class Event {
        /**
         * Event tag.
         *
         * @apiNote
         * Cobalt always sends {@code "event_log_start"} for a
         * synthetic checkin.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String tag;

        /**
         * Event timestamp, in epoch milliseconds.
         */
        @ProtobufProperty(index = 3, type = ProtobufType.INT64)
        long timeMs;

        /**
         * Constructs a new event entry.
         *
         * @param tag    the event tag
         * @param timeMs the event timestamp
         */
        Event(String tag, long timeMs) {
            this.tag = tag;
            this.timeMs = timeMs;
        }
    }
}
