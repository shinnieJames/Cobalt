package com.github.auties00.cobalt.registration.push.fcm.mcs;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * Wire shape of an MCS Iq stanza (frame tag {@code 7}).
 *
 * @apiNote
 * Cobalt only ever emits one shape: the stream-ack iq sent every
 * {@code STREAM_ACK_EVERY} packets, carrying the current
 * {@code last_stream_id_received} so the server can advance its retry
 * cursor.
 */
@ProtobufMessage(name = "FcmMcsIqStanza")
public final class FcmMcsIqStanza {
    /**
     * Iq type.
     *
     * @apiNote
     * Cobalt sends {@code 1} (set) for stream acks.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.INT64)
    long type;

    /**
     * Iq id.
     *
     * @apiNote
     * The native client sends an empty string for stream acks.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String id;

    /**
     * Extension nested message carrying the iq-specific payload.
     *
     * @apiNote
     * For a stream ack the extension's {@link Extension#id} is
     * {@code 13} and its {@link Extension#data} is a zero-length
     * byte string.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    Extension extension;

    /**
     * Cursor sent alongside the iq.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.INT64)
    long lastStreamIdReceived;

    /**
     * Iq status; always {@code 0} for stream acks.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.INT64)
    long status;

    /**
     * Constructs a new iq with the given values.
     *
     * @param type                 the iq type
     * @param id                   the iq id
     * @param extension            the extension payload
     * @param lastStreamIdReceived the cursor to advertise
     * @param status               the iq status
     */
    FcmMcsIqStanza(long type, String id, Extension extension,
                   long lastStreamIdReceived, long status) {
        this.type = type;
        this.id = id;
        this.extension = extension;
        this.lastStreamIdReceived = lastStreamIdReceived;
        this.status = status;
    }

    /**
     * Iq extension payload (field 7 of the parent iq).
     *
     * @apiNote
     * Stream acks fill {@link #id} with {@code 13} and {@link #data}
     * with an empty byte array.
     */
    @ProtobufMessage(name = "FcmMcsIqStanza.Extension")
    public static final class Extension {
        /**
         * Extension id.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.INT64)
        long id;

        /**
         * Opaque extension payload bytes.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
        byte[] data;

        /**
         * Constructs a new extension.
         *
         * @param id   the extension id
         * @param data the opaque payload bytes
         */
        Extension(long id, byte[] data) {
            this.id = id;
            this.data = data;
        }
    }
}
