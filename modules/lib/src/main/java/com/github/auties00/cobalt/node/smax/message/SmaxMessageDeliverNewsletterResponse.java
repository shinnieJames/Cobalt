package com.github.auties00.cobalt.node.smax.message;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The structured projection of an inbound
 * {@code <message type="media">} stanza addressed from a newsletter
 * JID; carries the envelope correlation metadata and a classifier for
 * the fanout-content variant the inner payload represents.
 *
 * @apiNote
 * Surfaced to callers handling newsletter deliveries; pair with a
 * {@link SmaxMessageDeliverNewsletterAcknowledgement} to ack or NACK
 * the delivery. The {@link #raw()} accessor exposes the underlying
 * {@link Node} so callers can decrypt and extract the 14 documented
 * fanout-content payload shapes (text, media, reaction, poll, etc.)
 * without Cobalt having to model each shape here.
 */
@WhatsAppWebModule(moduleName = "WASmaxInMessageDeliverNewsletterRequest")
@WhatsAppWebModule(moduleName = "WASmaxInMessageDeliverNewsletterMessageWithJIDMixin")
@WhatsAppWebModule(moduleName = "WASmaxInMessageDeliverNewsletterMessageFanoutMixin")
@WhatsAppWebModule(moduleName = "WASmaxInMessageDeliverReceiverContentTypeMediaRCATMixin")
public final class SmaxMessageDeliverNewsletterResponse implements SmaxOperation.Response {
    /**
     * The newsletter JID this delivery originated from.
     */
    private final Jid newsletterJid;

    /**
     * The relay-assigned stanza id.
     */
    private final String stanzaId;

    /**
     * The relay-assigned message id within the originating
     * newsletter.
     *
     * @apiNote
     * Used as a stable handle when correlating subsequent
     * reactions and poll-votes back to this delivery.
     */
    private final long serverId;

    /**
     * The unix-second timestamp of the post.
     */
    private final long timestamp;

    /**
     * Whether the {@code is_sender="true"} attribute was present;
     * {@code true} when the connected client authored this post.
     */
    private final boolean fromSelf;

    /**
     * The optional original-message timestamp from the
     * {@code <meta original_msg_t/>} child; set when this post is an
     * edit.
     */
    private final Long metaOriginalMsgT;

    /**
     * The optional last-edit timestamp from the
     * {@code <meta msg_edit_t/>} child.
     */
    private final Long metaMsgEditT;

    /**
     * The optional {@code <meta><admin_profile/></meta>} child as a
     * raw {@link Node} for downstream projection.
     */
    private final Node adminProfileMeta;

    /**
     * Whether the {@code <meta><paid_partnership/></meta>} marker was
     * present.
     */
    private final boolean hasPaidPartnership;

    /**
     * The optional offline counter from the {@code offline}
     * attribute; bounded to {@code 0..12}.
     */
    private final Integer offline;

    /**
     * The fanout-content variant name classifying the inner payload
     * shape.
     *
     * @apiNote
     * Lets callers branch on a single field rather than re-walking
     * the children; the documented values are
     * {@code "NewsletterText"}, {@code "NewsletterMedia"},
     * {@code "NewsletterReaction"},
     * {@code "NewsletterReactionRevoke"}, {@code "NewsletterEdit"},
     * {@code "NewsletterRevoke"}, {@code "NewsletterPollCreation"},
     * {@code "NewsletterQuizCreation"},
     * {@code "NewsletterPollVote"},
     * {@code "NewsletterPollResultSnapshot"},
     * {@code "NewsletterQuestion"},
     * {@code "NewsletterQuestionResponse"},
     * {@code "NewsletterQuestionReply"},
     * {@code "NewsletterWAMOEmpty"}.
     */
    private final String fanoutContentName;

    /**
     * The {@code mediatype} attribute on the {@code <plaintext/>}
     * child; always the literal {@code "url"} on documented
     * deliveries.
     */
    private final String plaintextMediatype;

    /**
     * The raw bytes carried by the {@code <rcat/>} child.
     *
     * @apiNote
     * Feeds the receiver-content-type-media decrypt step; opaque to
     * Cobalt.
     */
    private final byte[] rcatBytes;

    /**
     * The raw {@code <message>} {@link Node} backing this projection.
     *
     * @apiNote
     * Exposed so callers can project the variable-shape
     * fanout-content children (text body, media descriptor, poll
     * metadata, etc.) without Cobalt having to model 14 distinct
     * payload variants here.
     */
    private final Node raw;

    /**
     * Constructs a newsletter-delivery projection.
     *
     * @apiNote
     * Called by {@link #of(Node)} after a successful parse; not
     * intended for direct caller use.
     *
     * @param newsletterJid      the source newsletter JID; never
     *                           {@code null}
     * @param stanzaId           the relay-assigned stanza id; never
     *                           {@code null}
     * @param serverId           the server-assigned message id
     * @param timestamp          the unix-second timestamp
     * @param fromSelf           whether the connected client authored
     *                           the post
     * @param metaOriginalMsgT   the optional original timestamp;
     *                           may be {@code null}
     * @param metaMsgEditT       the optional edit timestamp; may be
     *                           {@code null}
     * @param adminProfileMeta   the optional admin-profile child
     *                           node; may be {@code null}
     * @param hasPaidPartnership whether the paid-partnership marker
     *                           was present
     * @param offline            the optional offline counter; may be
     *                           {@code null}
     * @param fanoutContentName  the fanout-content variant name;
     *                           never {@code null}
     * @param plaintextMediatype the plaintext mediatype literal;
     *                           never {@code null}
     * @param rcatBytes          the raw rcat bytes; never {@code null}
     * @param raw                the underlying message node; never
     *                           {@code null}
     * @throws NullPointerException if any non-nullable argument is
     *                              {@code null}
     */
    public SmaxMessageDeliverNewsletterResponse(Jid newsletterJid,
                   String stanzaId,
                   long serverId,
                   long timestamp,
                   boolean fromSelf,
                   Long metaOriginalMsgT,
                   Long metaMsgEditT,
                   Node adminProfileMeta,
                   boolean hasPaidPartnership,
                   Integer offline,
                   String fanoutContentName,
                   String plaintextMediatype,
                   byte[] rcatBytes,
                   Node raw) {
        this.newsletterJid = Objects.requireNonNull(newsletterJid, "newsletterJid cannot be null");
        this.stanzaId = Objects.requireNonNull(stanzaId, "stanzaId cannot be null");
        this.serverId = serverId;
        this.timestamp = timestamp;
        this.fromSelf = fromSelf;
        this.metaOriginalMsgT = metaOriginalMsgT;
        this.metaMsgEditT = metaMsgEditT;
        this.adminProfileMeta = adminProfileMeta;
        this.hasPaidPartnership = hasPaidPartnership;
        this.offline = offline;
        this.fanoutContentName = Objects.requireNonNull(fanoutContentName, "fanoutContentName cannot be null");
        this.plaintextMediatype = Objects.requireNonNull(plaintextMediatype, "plaintextMediatype cannot be null");
        this.rcatBytes = Objects.requireNonNull(rcatBytes, "rcatBytes cannot be null");
        this.raw = Objects.requireNonNull(raw, "raw cannot be null");
    }

    /**
     * Returns the source newsletter JID.
     *
     * @return the JID; never {@code null}
     */
    public Jid newsletterJid() {
        return newsletterJid;
    }

    /**
     * Returns the relay-assigned stanza id.
     *
     * @apiNote
     * Echo this into the matching
     * {@link SmaxMessageDeliverNewsletterAcknowledgement} stanza.
     *
     * @return the id; never {@code null}
     */
    public String stanzaId() {
        return stanzaId;
    }

    /**
     * Returns the server-assigned message id within the newsletter.
     *
     * @return the id
     */
    public long serverId() {
        return serverId;
    }

    /**
     * Returns the unix-second timestamp of the post.
     *
     * @return the timestamp
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * Reports whether the connected client authored this post.
     *
     * @return {@code true} when the {@code is_sender="true"}
     *         attribute was present
     */
    public boolean fromSelf() {
        return fromSelf;
    }

    /**
     * Returns the optional original-message timestamp.
     *
     * @apiNote
     * Set when the post is an edit, pointing at the original post's
     * timestamp.
     *
     * @return an {@link Optional} carrying the timestamp, or
     *         {@link Optional#empty()} when the post is not an edit
     */
    public Optional<Long> metaOriginalMsgT() {
        return Optional.ofNullable(metaOriginalMsgT);
    }

    /**
     * Returns the optional last-edit timestamp.
     *
     * @return an {@link Optional} carrying the timestamp, or
     *         {@link Optional#empty()} when omitted
     */
    public Optional<Long> metaMsgEditT() {
        return Optional.ofNullable(metaMsgEditT);
    }

    /**
     * Returns the optional admin-profile metadata projection.
     *
     * @apiNote
     * The wrapped node is the raw
     * {@code <meta><admin_profile/></meta>} child; callers project
     * its attributes as needed.
     *
     * @return an {@link Optional} carrying the node, or
     *         {@link Optional#empty()} when omitted
     */
    public Optional<Node> adminProfileMeta() {
        return Optional.ofNullable(adminProfileMeta);
    }

    /**
     * Reports whether the paid-partnership marker was present.
     *
     * @return {@code true} when the marker was present
     */
    public boolean hasPaidPartnership() {
        return hasPaidPartnership;
    }

    /**
     * Returns the optional offline counter.
     *
     * @apiNote
     * Bounded to {@code 0..12} by the WA Web schema; set on
     * deliveries replayed from the relay's offline backlog.
     *
     * @return an {@link Optional} carrying the counter, or
     *         {@link Optional#empty()} when omitted
     */
    public Optional<Integer> offline() {
        return Optional.ofNullable(offline);
    }

    /**
     * Returns the fanout-content variant name classifying the inner
     * payload shape.
     *
     * @return the variant name; never {@code null}
     */
    public String fanoutContentName() {
        return fanoutContentName;
    }

    /**
     * Returns the {@code <plaintext mediatype>} literal.
     *
     * @apiNote
     * Always the literal {@code "url"} on documented deliveries.
     *
     * @return the mediatype; never {@code null}
     */
    public String plaintextMediatype() {
        return plaintextMediatype;
    }

    /**
     * Returns the raw bytes carried by {@code <rcat/>}.
     *
     * @apiNote
     * Feeds the receiver-content-type-media decrypt pipeline; opaque
     * to Cobalt.
     *
     * @return the bytes; never {@code null}
     */
    public byte[] rcatBytes() {
        return rcatBytes;
    }

    /**
     * Returns the underlying {@code <message>} {@link Node}.
     *
     * @apiNote
     * Lets callers project the variable-shape fanout-content
     * children without Cobalt having to model every documented
     * payload variant.
     *
     * @return the raw node; never {@code null}
     */
    public Node raw() {
        return raw;
    }

    /**
     * Parses a newsletter-delivery projection from the given
     * {@code <message>} stanza.
     *
     * @apiNote
     * Returns {@link Optional#empty()} for any deviation from the
     * documented schema (wrong description, wrong type, non-newsletter
     * sender JID, missing or out-of-range server_id / timestamp /
     * offline, missing plaintext, missing or non-url mediatype,
     * missing rcat, missing rcat bytes, unrecognised fanout-content
     * shape).
     *
     * @implNote
     * This implementation rolls the WA Web
     * {@code parseNewsletterRequest},
     * {@code parseNewsletterMessageWithJIDMixin},
     * {@code parseNewsletterMessageFanoutMixin}, and
     * {@code parseReceiverContentTypeMediaRCATMixin} cascades into
     * one straight-line pass. The server-id range
     * ({@code 99..2147476647}) and timestamps ({@code original_msg_t}
     * in {@code 1577865600..4102473600}, {@code msg_edit_t} in
     * millisecond-domain bounds) mirror the WA Web range checks
     * verbatim.
     *
     * @param node the inbound message stanza; never {@code null}
     * @return an {@link Optional} carrying the projection, or
     *         {@link Optional#empty()} when the stanza does not match
     *         the expected shape
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInMessageDeliverNewsletterRequest",
            exports = "parseNewsletterRequest", adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxMessageDeliverNewsletterResponse> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("message")) {
            return Optional.empty();
        }
        if (!node.hasAttribute("type", "media")) {
            return Optional.empty();
        }
        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null || !from.hasNewsletterServer()) {
            return Optional.empty();
        }
        var stanzaId = node.getAttributeAsString("id").orElse(null);
        if (stanzaId == null) {
            return Optional.empty();
        }
        var serverIdOpt = node.getAttributeAsLong("server_id");
        if (serverIdOpt.isEmpty()) {
            return Optional.empty();
        }
        var serverId = serverIdOpt.getAsLong();
        if (serverId < 99 || serverId > 2147476647L) {
            return Optional.empty();
        }
        var timestampOpt = node.getAttributeAsLong("t");
        if (timestampOpt.isEmpty()) {
            return Optional.empty();
        }
        var timestamp = timestampOpt.getAsLong();
        if (timestamp < 0) {
            return Optional.empty();
        }
        var fromSelf = node.hasAttribute("is_sender", "true");
        var meta = node.getChild("meta").orElse(null);
        Long metaOriginalMsgT = null;
        Long metaMsgEditT = null;
        Node adminProfileMeta = null;
        var hasPaidPartnership = false;
        if (meta != null) {
            var origOpt = meta.getAttributeAsLong("original_msg_t");
            if (origOpt.isPresent()) {
                var ov = origOpt.getAsLong();
                if (ov < 1577865600L || ov > 4102473600L) {
                    return Optional.empty();
                }
                metaOriginalMsgT = ov;
            }
            var editOpt = meta.getAttributeAsLong("msg_edit_t");
            if (editOpt.isPresent()) {
                var ev = editOpt.getAsLong();
                if (ev < 1577865600000L || ev > 4102473600000L) {
                    return Optional.empty();
                }
                metaMsgEditT = ev;
            }
            adminProfileMeta = meta.getChild("admin_profile").orElse(null);
            hasPaidPartnership = meta.hasChild("paid_partnership");
        }
        Integer offline = null;
        var offlineOpt = node.getAttributeAsInt("offline");
        if (offlineOpt.isPresent()) {
            var ov = offlineOpt.getAsInt();
            if (ov < 0 || ov > 12) {
                return Optional.empty();
            }
            offline = ov;
        }
        var plaintext = node.getChild("plaintext").orElse(null);
        if (plaintext == null) {
            return Optional.empty();
        }
        if (!plaintext.hasAttribute("mediatype", "url")) {
            return Optional.empty();
        }
        var rcat = node.getChild("rcat").orElse(null);
        if (rcat == null) {
            return Optional.empty();
        }
        var rcatBytes = rcat.toContentBytes().orElse(null);
        if (rcatBytes == null) {
            return Optional.empty();
        }
        var fanoutContentName = classifyFanoutContent(node);
        if (fanoutContentName == null) {
            return Optional.empty();
        }
        return Optional.of(new SmaxMessageDeliverNewsletterResponse(
                from,
                stanzaId,
                serverId,
                timestamp,
                fromSelf,
                metaOriginalMsgT,
                metaMsgEditT,
                adminProfileMeta,
                hasPaidPartnership,
                offline,
                fanoutContentName,
                "url",
                rcatBytes,
                node));
    }

    /**
     * Classifies the fanout-content variant by inspecting the
     * child structure of the message stanza.
     *
     * @apiNote
     * Returns {@code null} when no variant matched.
     *
     * @implNote
     * This implementation walks the WA Web
     * {@code parseNewsletterMessageFanoutContent} priority order
     * (question, question-response, edit, question-reply, revoke,
     * reaction (optionally revoke), poll-creation, quiz-creation,
     * poll-vote, poll-result-snapshot, wamo-empty) and collapses the
     * Cobalt classifier to a label-only result. Downstream callers
     * re-walk {@link #raw} to extract the variant payload, so this
     * function only needs to surface the variant name.
     *
     * @param node the message stanza; never {@code null}
     * @return the variant name, or {@code null} when no variant
     *         matched
     */
    @WhatsAppWebExport(moduleName = "WASmaxInMessageDeliverNewsletterMessageFanoutContent",
            exports = "parseNewsletterMessageFanoutContent",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String classifyFanoutContent(Node node) {
        if (node.hasChild("question")) {
            return "NewsletterQuestion";
        }
        if (node.hasChild("question_response")) {
            return "NewsletterQuestionResponse";
        }
        if (node.hasChild("edit")) {
            return "NewsletterEdit";
        }
        if (node.hasChild("question_reply")) {
            return "NewsletterQuestionReply";
        }
        if (node.hasChild("revoke")) {
            return "NewsletterRevoke";
        }
        if (node.hasChild("reaction")) {
            if (node.getChild("reaction")
                    .map(c -> c.hasAttribute("operation", "revoke"))
                    .orElse(false)) {
                return "NewsletterReactionRevoke";
            }
            return "NewsletterReaction";
        }
        if (node.hasChild("poll_creation")) {
            return "NewsletterPollCreation";
        }
        if (node.hasChild("quiz_creation")) {
            return "NewsletterQuizCreation";
        }
        if (node.hasChild("poll_vote")) {
            return "NewsletterPollVote";
        }
        if (node.hasChild("poll_result_snapshot")) {
            return "NewsletterPollResultSnapshot";
        }
        if (node.hasChild("wamo")) {
            return "NewsletterWAMOEmpty";
        }
        return "NewsletterText";
    }

    /**
     * Compares this projection to another for value equality on
     * every field including the underlying {@link Node}.
     *
     * @param obj the object to compare against
     * @return {@code true} when {@code obj} is a
     *         {@link SmaxMessageDeliverNewsletterResponse} with
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
        var that = (SmaxMessageDeliverNewsletterResponse) obj;
        return this.serverId == that.serverId
                && this.timestamp == that.timestamp
                && this.fromSelf == that.fromSelf
                && this.hasPaidPartnership == that.hasPaidPartnership
                && Objects.equals(this.newsletterJid, that.newsletterJid)
                && Objects.equals(this.stanzaId, that.stanzaId)
                && Objects.equals(this.metaOriginalMsgT, that.metaOriginalMsgT)
                && Objects.equals(this.metaMsgEditT, that.metaMsgEditT)
                && Objects.equals(this.adminProfileMeta, that.adminProfileMeta)
                && Objects.equals(this.offline, that.offline)
                && Objects.equals(this.fanoutContentName, that.fanoutContentName)
                && Objects.equals(this.plaintextMediatype, that.plaintextMediatype)
                && Arrays.equals(this.rcatBytes, that.rcatBytes)
                && Objects.equals(this.raw, that.raw);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @implNote
     * This implementation mixes
     * {@link Arrays#hashCode(byte[])} of the rcat bytes into the
     * field-wise hash so byte-array contents drive the result.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        var result = Objects.hash(newsletterJid, stanzaId, serverId, timestamp, fromSelf,
                metaOriginalMsgT, metaMsgEditT, adminProfileMeta, hasPaidPartnership, offline,
                fanoutContentName, plaintextMediatype, raw);
        result = 31 * result + Arrays.hashCode(rcatBytes);
        return result;
    }

    /**
     * Returns a debug-friendly representation of this projection.
     *
     * @apiNote
     * Intended for logging; the rcat bytes and underlying node are
     * deliberately omitted to keep the result readable. The format
     * is not part of the public contract.
     *
     * @return the string form
     */
    @Override
    public String toString() {
        return "SmaxMessageDeliverNewsletterResponse[newsletterJid=" + newsletterJid
                + ", stanzaId=" + stanzaId
                + ", serverId=" + serverId
                + ", timestamp=" + timestamp
                + ", fromSelf=" + fromSelf
                + ", metaOriginalMsgT=" + metaOriginalMsgT
                + ", metaMsgEditT=" + metaMsgEditT
                + ", hasPaidPartnership=" + hasPaidPartnership
                + ", offline=" + offline
                + ", fanoutContentName=" + fanoutContentName
                + ", plaintextMediatype=" + plaintextMediatype + ']';
    }
}
