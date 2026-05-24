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
 * The outbound {@code <iq xmlns="w:stats" type="set">} stanza uploading
 * an encoded WAM (WhatsApp Analytics Metrics) buffer to the relay.
 *
 * @apiNote
 * Backs the field-stats / WAM upload pipeline driven by WA Web's
 * {@code WAWebStatsUploadJob}: a periodic flush serialises a batch of
 * encoded WAM events into the {@code addElementValue} byte buffer and
 * sends it through
 * {@code WASmaxStatsSendBufferRPC.sendSendBufferRPC}. The reply is
 * parsed by {@link SmaxStatsSendBufferResponse}; a {@code Success}
 * lets the local buffer be cleared, an {@code ErrorRetry} requires
 * re-buffering, and an {@code ErrorNoRetry} surfaces a permanent
 * rejection.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutStatsSendBufferRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutStatsBaseIQSetRequestMixin")
public final class SmaxStatsSendBufferRequest implements SmaxOperation.Request {
    /**
     * The Unix-epoch timestamp at which the batch was sealed;
     * forwarded verbatim into the {@code <add t="..."/>} attribute.
     */
    private final long addT;

    /**
     * The encoded WAM payload bytes carried as the {@code <add>}
     * child's content.
     */
    private final byte[] addElementValue;

    /**
     * Constructs a new send-buffer request.
     *
     * @apiNote
     * The pair {@code (addT, addElementValue)} matches the keys WA
     * Web's {@code WAWebStatsUploadJob} passes to
     * {@code sendSendBufferRPC}; the bytes are the encoded WAM
     * buffer, not a serialised stanza child.
     *
     * @param addT the batch timestamp in seconds since the Unix epoch
     * @param addElementValue the encoded WAM payload bytes; never
     *                        {@code null}
     * @throws NullPointerException if {@code addElementValue} is
     *                              {@code null}
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
     *
     * @apiNote
     * Produces
     * {@code <iq xmlns="w:stats" type="set" to="s.whatsapp.net">
     *   <add t="...">BYTES</add></iq>}; the envelope's {@code id} is
     * stamped by the dispatch path.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the
     *         {@code <add>} child
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
     * Returns whether the given object is a
     * {@link SmaxStatsSendBufferRequest} with equal timestamp and
     * payload.
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
