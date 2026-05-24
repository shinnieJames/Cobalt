package com.github.auties00.cobalt.node.smax.profilepicture;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The optional privacy-token sub-payload of a
 * {@link SmaxProfilePictureGetRequest}; wraps a
 * {@code <tctoken>...</tctoken>} child carrying the privacy-token
 * contents bytes inside an {@code <smax$any>} envelope.
 *
 * @apiNote
 * Pass an instance to {@link SmaxProfilePictureGetRequest} when the
 * caller has a fresh privacy token from
 * {@code WAWebPrivacyTokenJob} and wants to authenticate the
 * picture-get against that token; the relay rejects the fetch if the
 * token is invalid or expired.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutProfilePictureTCTokenMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutProfilePicturePrivacyTokenContentsMixin")
public final class SmaxProfilePictureGetTcTokenMixin {
    /**
     * The optional {@code t} timestamp on the {@code <tctoken/>}
     * element.
     */
    private final Long tctokenT;

    /**
     * The opaque privacy-token contents bytes.
     */
    private final byte[] anyElementValue;

    /**
     * Constructs a privacy-token payload.
     *
     * @apiNote
     * Use this when assembling a {@link SmaxProfilePictureGetRequest}
     * that should be authenticated by a fresh privacy token.
     *
     * @param tctokenT        the optional issuance timestamp; may be
     *                        {@code null}
     * @param anyElementValue the privacy-token bytes; never
     *                        {@code null}
     * @throws NullPointerException if {@code anyElementValue} is
     *                              {@code null}
     */
    public SmaxProfilePictureGetTcTokenMixin(Long tctokenT, byte[] anyElementValue) {
        this.tctokenT = tctokenT;
        this.anyElementValue = Objects.requireNonNull(anyElementValue, "anyElementValue cannot be null");
    }

    /**
     * Returns the optional issuance timestamp.
     *
     * @apiNote
     * Read by {@link #toNode()} to decide whether to stamp the
     * {@code <tctoken t=...>} attribute.
     *
     * @return an {@link Optional} carrying the timestamp, or
     *         {@link Optional#empty()} when omitted
     */
    public Optional<Long> tctokenT() {
        return Optional.ofNullable(tctokenT);
    }

    /**
     * Returns the privacy-token contents bytes.
     *
     * @apiNote
     * Opaque to Cobalt; the relay's privacy-token validator decodes
     * the contents.
     *
     * @return the bytes; never {@code null}
     */
    public byte[] anyElementValue() {
        return anyElementValue;
    }

    /**
     * Builds the {@code <smax$any>} wrapper node carrying the
     * {@code <tctoken/>} child.
     *
     * @apiNote
     * The node has shape
     * {@snippet lang=xml :
     * <smax$any><tctoken t="N"?>...bytes...</tctoken></smax$any>
     * }
     *
     * @implNote
     * This implementation builds the {@code <tctoken/>} first then
     * wraps it in the outer {@code <smax$any>}; the wire layout is
     * identical to WA Web's
     * {@code mergeTCTokenMixin} emit.
     *
     * @return the {@link Node}
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutProfilePictureTCTokenMixin",
            exports = "mergeTCTokenMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Node toNode() {
        var tctokenBuilder = new NodeBuilder()
                .description("tctoken")
                .content(anyElementValue);
        if (tctokenT != null) {
            tctokenBuilder.attribute("t", tctokenT);
        }
        return new NodeBuilder()
                .description("smax$any")
                .content(tctokenBuilder.build())
                .build();
    }

    /**
     * Compares this payload to another for value equality on the
     * timestamp and contents bytes.
     *
     * @param obj the object to compare against
     * @return {@code true} when {@code obj} is a
     *         {@link SmaxProfilePictureGetTcTokenMixin} with
     *         identical fields
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxProfilePictureGetTcTokenMixin) obj;
        return Objects.equals(this.tctokenT, that.tctokenT)
                && Arrays.equals(this.anyElementValue, that.anyElementValue);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @implNote
     * This implementation mixes {@link Arrays#hashCode(byte[])} of
     * the contents into the hash so byte-array contents drive the
     * result.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        var result = Objects.hash(tctokenT);
        result = 31 * result + Arrays.hashCode(anyElementValue);
        return result;
    }

    /**
     * Returns a debug-friendly representation of this payload.
     *
     * @apiNote
     * Intended for logging; the format is not part of the public
     * contract.
     *
     * @return the string form
     */
    @Override
    public String toString() {
        return "SmaxProfilePictureGetTcTokenMixin[tctokenT=" + tctokenT
                + ", anyElementValue=" + Arrays.toString(anyElementValue) + ']';
    }
}
