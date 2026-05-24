package com.github.auties00.cobalt.registration.push.fcm.checkin;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * Wire shape of the response to {@code POST /checkin}, gunzipped
 * before decoding.
 *
 * @apiNote
 * Only the two fields Cobalt cares about (the assigned Android id
 * and its security token) are mapped; all other fields the server
 * returns are ignored.
 */
@ProtobufMessage(name = "FcmCheckinResponse")
public final class FcmCheckinResponse {
    /**
     * Server-assigned 64-bit Android device id.
     *
     * @apiNote
     * Becomes the username on the MCS login.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.FIXED64)
    long androidId;

    /**
     * Server-assigned 64-bit security token paired with
     * {@link #androidId}.
     *
     * @apiNote
     * Becomes the password on the MCS login.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.FIXED64)
    long securityToken;

    /**
     * Constructs a new checkin response with the given values.
     *
     * @param androidId     the assigned Android id
     * @param securityToken the paired security token
     */
    FcmCheckinResponse(long androidId, long securityToken) {
        this.androidId = androidId;
        this.securityToken = securityToken;
    }

    /**
     * Returns the server-assigned Android device id.
     *
     * @return the Android id
     */
    public long androidId() {
        return androidId;
    }

    /**
     * Returns the server-assigned security token.
     *
     * @return the security token
     */
    public long securityToken() {
        return securityToken;
    }
}
