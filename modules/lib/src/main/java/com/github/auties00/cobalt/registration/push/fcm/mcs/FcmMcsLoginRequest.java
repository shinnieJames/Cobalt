package com.github.auties00.cobalt.registration.push.fcm.mcs;

import com.github.auties00.cobalt.registration.push.fcm.checkin.FcmCheckinResponse;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.List;

/**
 * First framed packet (MCS frame tag {@code 2}) sent on the MCS
 * stream after the 1-byte version preamble.
 *
 * @apiNote
 * Authenticates the device with the
 * {@code androidId}/{@code securityToken} pair from
 * {@link FcmCheckinResponse} and replays any unacked persistent ids
 * from previous sessions so the server can drop them from its retry
 * queue.
 */
@ProtobufMessage(name = "FcmMcsLoginRequest")
public final class FcmMcsLoginRequest {
    /**
     * Client id.
     *
     * @apiNote
     * Always {@code "android-30"}; matches the SDK level Cobalt
     * advertises in checkin.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String id;

    /**
     * MCS domain; always {@code "mcs.android.com"}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String domain;

    /**
     * MCS username.
     *
     * @apiNote
     * The decimal {@code androidId} as a string.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String user;

    /**
     * MCS resource.
     *
     * @apiNote
     * Also the decimal {@code androidId} as a string; the native
     * client uses the same value here as in {@link #user}.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String resource;

    /**
     * MCS password.
     *
     * @apiNote
     * The decimal {@code securityToken} as a string.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String authToken;

    /**
     * Device id derived from {@link #user}.
     *
     * @apiNote
     * Formatted as {@code "android-" + Long.toHexString(androidId)}.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String deviceId;

    /**
     * Repeated key/value settings.
     *
     * @apiNote
     * Cobalt always sends a single {@code new_vc=1} entry, mirroring
     * the native client.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    List<Setting> settings;

    /**
     * Persistent ids the client has already acked locally.
     *
     * @apiNote
     * The server uses this list to skip redelivery of those messages
     * after a reconnect.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    List<String> persistentIds;

    /**
     * Whether to use adaptive heartbeats.
     *
     * @apiNote
     * The native client sends {@code false}.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.BOOL)
    boolean adaptiveHeartbeat;

    /**
     * Whether to use the RMQ2 ack scheme.
     *
     * @apiNote
     * The native client sends {@code true}.
     */
    @ProtobufProperty(index = 14, type = ProtobufType.BOOL)
    boolean useRmq2;

    /**
     * Auth service id.
     *
     * @apiNote
     * The native client sends {@code 2}.
     */
    @ProtobufProperty(index = 16, type = ProtobufType.INT64)
    long authService;

    /**
     * Network type id.
     *
     * @apiNote
     * The native client sends {@code 1}.
     */
    @ProtobufProperty(index = 17, type = ProtobufType.INT64)
    long networkType;

    /**
     * Constructs a new login request with the given values.
     *
     * @param id                the client id
     * @param domain            the MCS domain
     * @param user              the MCS username
     * @param resource          the MCS resource
     * @param authToken         the MCS password
     * @param deviceId          the device id
     * @param settings          the key/value settings
     * @param persistentIds     the persistent ids to replay
     * @param adaptiveHeartbeat whether to negotiate adaptive heartbeats
     * @param useRmq2           whether to negotiate the RMQ2 ack scheme
     * @param authService       the auth service id
     * @param networkType       the network type id
     */
    FcmMcsLoginRequest(String id, String domain, String user, String resource,
                       String authToken, String deviceId, List<Setting> settings,
                       List<String> persistentIds, boolean adaptiveHeartbeat,
                       boolean useRmq2, long authService, long networkType) {
        this.id = id;
        this.domain = domain;
        this.user = user;
        this.resource = resource;
        this.authToken = authToken;
        this.deviceId = deviceId;
        this.settings = settings;
        this.persistentIds = persistentIds;
        this.adaptiveHeartbeat = adaptiveHeartbeat;
        this.useRmq2 = useRmq2;
        this.authService = authService;
        this.networkType = networkType;
    }

    /**
     * One key/value setting on the login packet.
     *
     * @apiNote
     * Cobalt only ever sends {@code new_vc=1}; other settings the
     * native client may include are not modeled.
     */
    @ProtobufMessage(name = "FcmMcsLoginRequest.Setting")
    public static final class Setting {
        /**
         * Setting name.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String name;

        /**
         * Setting value.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String value;

        /**
         * Constructs a new setting.
         *
         * @param name  the setting name
         * @param value the setting value
         */
        Setting(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
