package com.github.auties00.cobalt.model.chat.group;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.util.Objects;

/**
 * Represents an intent expressed against a WhatsApp group's profile
 * picture on a {@link GroupMetadataEdit}.
 *
 * <p>Like {@link GroupDescription}, WhatsApp Web models a picture update
 * as a tri-state: "replace with new bytes", "remove the existing picture",
 * or "leave untouched". The third state is encoded by leaving the picture
 * field on the enclosing {@link GroupMetadataEdit} {@code null}. The
 * first two states are the variants of this sealed interface:
 * {@link Set} carries the new picture bytes, while {@link Clear} requests
 * removal of any existing picture.
 *
 * <p>An empty {@code byte[]} payload is rejected by {@link Set} so that
 * it cannot collide with the {@link Clear} encoding. Callers wanting to
 * remove the picture must use {@link Clear} explicitly.
 *
 * <p>On the wire, the protobuf encoding remains a plain {@code BYTES}
 * field: the raw bytes of a {@link Set} for a non-empty picture and an
 * empty {@code byte[0]} for a {@link Clear}. The custom deserializer
 * round-trips {@code null} to {@code null}, {@code byte[0]} to
 * {@link Clear} and any other value to {@link Set}.
 */
public sealed interface GroupPicture {
    /**
     * Replaces the existing group picture with the supplied bytes.
     *
     * <p>The bytes must be non-{@code null} and non-empty; an empty
     * payload is reserved as the {@link Clear} sentinel on the wire. The
     * payload is not defensively cloned by this record; callers wanting
     * to guard against external mutation must clone before constructing.
     *
     * @param bytes the new picture payload (typically a 256x256 JPEG),
     *              never {@code null} or empty
     */
    record Set(byte[] bytes) implements GroupPicture {
        /**
         * Compact canonical constructor that validates the bytes.
         *
         * @throws NullPointerException     if {@code bytes} is
         *                                  {@code null}
         * @throws IllegalArgumentException if {@code bytes} has zero
         *                                  length
         */
        public Set {
            Objects.requireNonNull(bytes, "bytes cannot be null; use Clear to remove the picture");
            if (bytes.length == 0) {
                throw new IllegalArgumentException("bytes cannot be empty; use Clear to remove the picture");
            }
        }
    }

    /**
     * Requests removal of the existing group picture. The editor
     * translates this into a {@code w:profile:picture} {@code iq} of
     * type {@code set} with no body, matching WA Web's
     * {@code WAWebSendProfilePictureJob} removal path where the
     * {@code picture} argument is {@code null}.
     */
    record Clear() implements GroupPicture {
    }

    /**
     * Deserializes a wire-level {@code BYTES} value into the matching
     * {@code GroupPicture} variant.
     *
     * <p>A {@code null} wire value yields a {@code null}
     * {@code GroupPicture} (the enclosing edit treats this as "leave
     * untouched"). A zero-length wire value yields a {@link Clear}; any
     * non-empty value yields a {@link Set}.
     *
     * @param wire the protobuf BYTES value, possibly {@code null}
     * @return the matching variant, or {@code null} when {@code wire} is
     *         {@code null}
     */
    @ProtobufDeserializer
    static GroupPicture of(byte[] wire) {
        if (wire == null) {
            return null;
        }
        if (wire.length == 0) {
            return new Clear();
        }
        return new Set(wire);
    }

    /**
     * Serializes this variant into the wire-level {@code BYTES}
     * representation.
     *
     * <p>{@link Set} round-trips its {@link Set#bytes() bytes} verbatim.
     * {@link Clear} is encoded as an empty {@code byte[0]}, which the
     * matching deserializer reconstitutes back into a {@link Clear}.
     *
     * @return the protobuf BYTES value, never {@code null}
     */
    @ProtobufSerializer
    default byte[] toProtoBytes() {
        return switch (this) {
            case Set set -> set.bytes();
            case Clear ignored -> new byte[0];
        };
    }
}
