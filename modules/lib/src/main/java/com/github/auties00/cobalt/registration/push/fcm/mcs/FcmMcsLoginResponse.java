package com.github.auties00.cobalt.registration.push.fcm.mcs;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * Server reply to {@link FcmMcsLoginRequest} (MCS frame tag {@code 3}).
 *
 * @apiNote
 * On success, {@link #error} is {@code null} and
 * {@link #serverTimestamp} carries the server's clock; on failure,
 * {@link #error} carries the code and message pair.
 */
@ProtobufMessage(name = "FcmMcsLoginResponse")
public final class FcmMcsLoginResponse {
    /**
     * Optional error block.
     *
     * @apiNote
     * {@code null} iff the login succeeded.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ErrorInfo error;

    /**
     * Server's wall-clock at login time, in milliseconds since epoch.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.INT64)
    long serverTimestamp;

    /**
     * Constructs a new login response with the given values.
     *
     * @param error           the optional error block
     * @param serverTimestamp the server timestamp in millis
     */
    FcmMcsLoginResponse(ErrorInfo error, long serverTimestamp) {
        this.error = error;
        this.serverTimestamp = serverTimestamp;
    }

    /**
     * Returns the optional error block.
     *
     * @return the error info, or {@code null} on success
     */
    public ErrorInfo error() {
        return error;
    }

    /**
     * Returns the server timestamp in milliseconds since epoch.
     *
     * @return the server timestamp
     */
    public long serverTimestamp() {
        return serverTimestamp;
    }

    /**
     * Non-zero error code plus human-readable message returned when
     * the login is rejected.
     *
     * @apiNote
     * Common codes include "Expired security token" (force a fresh
     * checkin) and "Invalid credentials" (the
     * {@code androidId}/{@code securityToken} pair is wrong).
     */
    @ProtobufMessage(name = "FcmMcsLoginResponse.ErrorInfo")
    public static final class ErrorInfo {
        /**
         * Numeric error code.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.INT64)
        long code;

        /**
         * Human-readable error message.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String message;

        /**
         * Constructs a new error block.
         *
         * @param code    the numeric error code
         * @param message the human-readable message
         */
        ErrorInfo(long code, String message) {
            this.code = code;
            this.message = message;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the code
         */
        public long code() {
            return code;
        }

        /**
         * Returns the human-readable error message.
         *
         * @return the message
         */
        public String message() {
            return message;
        }
    }
}
