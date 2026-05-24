package com.github.auties00.cobalt.node.smax.support;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Single {@code <message>} child of a {@link SmaxNewsletterReportRequest} payload, describing
 * one offending newsletter message harvested from the local cache.
 *
 * @apiNote
 * Built by callers of {@link SmaxNewsletterReportRequest} (typically WA Web's
 * {@code WAWebNewsletterReportUtils.sendNewsletterReport}) to enumerate the offending messages
 * attached to a newsletter spam report; supply either the scalar {@code (messageFrom, t, id)}
 * fields or a fully pre-built {@link Node}.
 *
 * @implNote
 * This implementation models WA Web's {@code WASmaxOutSpamMessageMixin.makeMessageRaw}
 * minimal-attribute shape ({@code from}, {@code t}, {@code id}); the optional {@code raw} field
 * lets callers bypass scalar reconstruction when WA Web has already produced a richer node.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutSpamMessageMixin")
public final class SmaxNewsletterReportMessageEntry {
    /**
     * The sender JID of the offending message.
     *
     * @apiNote
     * Routed into {@code <message from="..."/>} via WA Web's {@code JID} marshaller.
     */
    private final Jid messageFrom;

    /**
     * The message timestamp in Unix seconds.
     *
     * @apiNote
     * Routed into {@code <message t="..."/>}.
     */
    private final long messageTimestamp;

    /**
     * The message stanza id.
     *
     * @apiNote
     * Routed into {@code <message id="..."/>} via WA Web's {@code STANZA_ID} marshaller.
     */
    private final String messageId;

    /**
     * The optional pre-built {@code <message>} node.
     *
     * @apiNote
     * When set, the entry's {@link #toNode()} returns this node verbatim and ignores the scalar
     * fields; suitable when WA Web has already produced a richer node carrying additional
     * mixin payloads.
     */
    private final Node raw;

    /**
     * Constructs an entry from the minimal scalar fields.
     *
     * @apiNote
     * Convenience overload for callers that have only the three required attributes; equivalent
     * to {@code this(messageFrom, messageTimestamp, messageId, null)}.
     *
     * @param messageFrom      the sender JID; never {@code null}
     * @param messageTimestamp the timestamp in Unix seconds
     * @param messageId        the stanza id; never {@code null}
     * @throws NullPointerException if {@code messageFrom} or {@code messageId} is {@code null}
     */
    public SmaxNewsletterReportMessageEntry(Jid messageFrom, long messageTimestamp, String messageId) {
        this(messageFrom, messageTimestamp, messageId, null);
    }

    /**
     * Constructs an entry that prefers a pre-built node over the scalar fields.
     *
     * @apiNote
     * Use when WA Web has already produced a richer {@code <message>} node carrying additional
     * mixin payloads; the scalar fields are still retained for {@link #equals(Object)} /
     * {@link #hashCode()} / {@link #toString()} parity.
     *
     * @param messageFrom      the sender JID; never {@code null}
     * @param messageTimestamp the timestamp in Unix seconds
     * @param messageId        the stanza id; never {@code null}
     * @param raw              the optional pre-built node; may be {@code null}
     * @throws NullPointerException if {@code messageFrom} or {@code messageId} is {@code null}
     */
    public SmaxNewsletterReportMessageEntry(Jid messageFrom, long messageTimestamp, String messageId, Node raw) {
        this.messageFrom = Objects.requireNonNull(messageFrom, "messageFrom cannot be null");
        this.messageTimestamp = messageTimestamp;
        this.messageId = Objects.requireNonNull(messageId, "messageId cannot be null");
        this.raw = raw;
    }

    /**
     * Returns the sender JID.
     *
     * @apiNote
     * Surfaces the value routed into {@code <message from>}.
     *
     * @return the JID; never {@code null}
     */
    public Jid messageFrom() {
        return messageFrom;
    }

    /**
     * Returns the message timestamp.
     *
     * @apiNote
     * Surfaces the value routed into {@code <message t>}.
     *
     * @return the timestamp in Unix seconds
     */
    public long messageTimestamp() {
        return messageTimestamp;
    }

    /**
     * Returns the message stanza id.
     *
     * @apiNote
     * Surfaces the value routed into {@code <message id>}.
     *
     * @return the id; never {@code null}
     */
    public String messageId() {
        return messageId;
    }

    /**
     * Returns the optional pre-built {@code <message>} node.
     *
     * @apiNote
     * Empty when {@link #toNode()} should reconstruct the envelope from the scalar fields.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> raw() {
        return Optional.ofNullable(raw);
    }

    /**
     * Builds the {@code <message>} child suitable for embedding into a
     * {@link SmaxNewsletterReportRequest} payload.
     *
     * @apiNote
     * Returns the pre-built {@link #raw} node when set; otherwise reconstructs the minimal
     * {@code (from, t, id)} envelope.
     *
     * @return the built node; never {@code null}
     */
    public Node toNode() {
        if (raw != null) {
            return raw;
        }
        return new NodeBuilder()
                .description("message")
                .attribute("from", messageFrom)
                .attribute("t", messageTimestamp)
                .attribute("id", messageId)
                .build();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxNewsletterReportMessageEntry) obj;
        return this.messageTimestamp == that.messageTimestamp
                && Objects.equals(this.messageFrom, that.messageFrom)
                && Objects.equals(this.messageId, that.messageId)
                && Objects.equals(this.raw, that.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageFrom, messageTimestamp, messageId, raw);
    }

    @Override
    public String toString() {
        return "SmaxNewsletterReportMessageEntry[messageFrom=" + messageFrom
                + ", messageTimestamp=" + messageTimestamp
                + ", messageId=" + messageId + ']';
    }
}
