package com.github.auties00.cobalt.node.smax.stats;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Carries the outbound {@code <iq xmlns="w:stats" type="set">} stanza that uploads an encoded WAM
 * (WhatsApp Analytics Metrics) buffer to the relay.
 * <p>
 * A periodic flush serialises a batch of encoded WAM events into the {@code addElementValue} byte
 * buffer, seals it with a timestamp, and dispatches it as a single IQ. The reply is parsed by
 * {@link SmaxStatsSendBufferResponse}: {@link SmaxStatsSendBufferResponse.Success} lets the local
 * buffer be cleared, {@link SmaxStatsSendBufferResponse.ErrorRetry} requires re-buffering the batch
 * for the next flush window, and {@link SmaxStatsSendBufferResponse.ErrorNoRetry} surfaces a
 * permanent rejection.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutStatsSendBufferRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutStatsBaseIQSetRequestMixin")
public final class SmaxStatsSendBufferRequest implements SmaxOperation.Request {
    /**
     * Holds the Unix-epoch timestamp at which the batch was sealed.
     * <p>
     * Forwarded verbatim into the {@code <add t="..."/>} attribute of the outbound stanza.
     */
    private final long addT;

    /**
     * Holds the encoded WAM payload bytes.
     * <p>
     * These bytes are the serialised WAM buffer, not a serialised stanza child; they are carried as
     * the {@code <add>} child's content by {@link #toNode()}.
     */
    private final byte[] addElementValue;

    /**
     * Constructs a new send-buffer request from a sealed batch timestamp and its encoded payload.
     * <p>
     * The pair {@code (addT, addElementValue)} mirrors the keys WhatsApp Web passes when sealing a
     * WAM batch for upload; {@code addElementValue} is the encoded WAM buffer rather than a
     * serialised stanza child.
     *
     * @param addT the batch timestamp in seconds since the Unix epoch
     * @param addElementValue the encoded WAM payload bytes; never {@code null}
     * @throws NullPointerException if {@code addElementValue} is {@code null}
     */
    public SmaxStatsSendBufferRequest(long addT, byte[] addElementValue) {
        this.addT = addT;
        this.addElementValue = Objects.requireNonNull(addElementValue,
                "addElementValue cannot be null");
    }

    /**
     * Returns the batch timestamp.
     *
     * @return the timestamp in seconds since the Unix epoch
     */
    public long addT() {
        return addT;
    }

    /**
     * Returns the encoded WAM payload bytes.
     *
     * @return the bytes; never {@code null}
     */
    public byte[] addElementValue() {
        return addElementValue;
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     * <p>
     * Produces an {@code <iq xmlns="w:stats" type="set">} envelope addressed to
     * {@link Jid#userServer()} wrapping a single {@code <add t="...">} child whose content is the
     * encoded WAM payload; the envelope's {@code id} is stamped by the dispatch path.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the {@code <add>} child
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutStatsSendBufferRequest",
            exports = "makeSendBufferRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var addNode = new NodeBuilder()
                .description("add")
                .attribute("t", addT)
                .content(addElementValue)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:stats")
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
                .content(addNode);
    }

    /**
     * Compares this request with the given object for equality.
     * <p>
     * Two requests are equal when both are {@link SmaxStatsSendBufferRequest} instances with the
     * same timestamp and byte-for-byte equal payloads.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when both fields match
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxStatsSendBufferRequest) obj;
        return this.addT == that.addT
                && Arrays.equals(this.addElementValue, that.addElementValue);
    }

    /**
     * Returns a hash code derived from the timestamp and payload.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        var result = Long.hashCode(addT);
        result = 31 * result + Arrays.hashCode(addElementValue);
        return result;
    }

    /**
     * Returns a debug-friendly textual representation of this request.
     *
     * @return the textual representation
     */
    @Override
    public String toString() {
        return "SmaxStatsSendBufferRequest[addT=" + addT
                + ", addElementValue=" + Arrays.toString(addElementValue) + ']';
    }
}
