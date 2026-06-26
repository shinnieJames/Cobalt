package com.github.auties00.cobalt.stanza.smax.pings;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.smax.SmaxStanza;

import java.util.Objects;
import java.util.Optional;

/**
 * Models the inbound {@code <iq type="result">} reply to a {@link SmaxPingsClientRequest}.
 *
 * <p>This projection surfaces the relay's keep-alive reply so the stream-stall detector can confirm
 * that the most recent ping landed. The only payload-bearing field is the server-stamped
 * {@link #timestamp()}, which embedders use to estimate clock skew. The reply has a single
 * success-only shape with no error variants, so the response is modelled as one record-style
 * projection rather than a sealed family.
 */
@WhatsAppWebModule(moduleName = "WASmaxInPingsClientResponseServerResponse")
@WhatsAppWebModule(moduleName = "WASmaxInPingsEnums")
public final class SmaxPingsClientResponseServerResponse implements SmaxStanza.Response {
    /**
     * The relay JID that produced the reply.
     *
     * @implNote
     * This implementation accepts any JID that {@link #isDomainOrUserJid(Jid)} considers valid: a
     * server-only {@code s.whatsapp.net}, {@code g.us}, or {@code call} JID, or any standard
     * user-server JID.
     */
    private final Jid from;

    /**
     * The literal {@code "result"} type tag.
     *
     * @implNote
     * This implementation always stores the literal {@code "result"} after a successful parse; the
     * field is preserved rather than elided so the projection can be debug-printed without losing
     * the wire-format hint.
     */
    private final String type;

    /**
     * The relay-stamped server timestamp, in seconds since the Unix epoch.
     */
    private final long timestamp;

    /**
     * Constructs a new server-response projection.
     *
     * <p>Invoked by {@link #of(Stanza, Stanza)} once the envelope shape has been validated; embedders
     * typically do not instantiate this directly.
     *
     * @param from the relay JID; never {@code null}
     * @param type the literal {@code "result"} type tag; never {@code null}
     * @param timestamp the relay-stamped server timestamp, in seconds
     * @throws NullPointerException if either {@code from} or {@code type} is {@code null}
     */
    public SmaxPingsClientResponseServerResponse(Jid from, String type, long timestamp) {
        this.from = Objects.requireNonNull(from, "from cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.timestamp = timestamp;
    }

    /**
     * Returns the relay JID that produced the reply.
     *
     * @return the relay JID; never {@code null}
     */
    public Jid from() {
        return from;
    }

    /**
     * Returns the literal {@code "result"} type tag.
     *
     * @return the type tag; never {@code null}
     */
    public String type() {
        return type;
    }

    /**
     * Returns the relay-stamped server timestamp.
     *
     * @return the timestamp, in seconds since the Unix epoch
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * Parses a {@link SmaxPingsClientResponseServerResponse} from the given inbound stanza,
     * cross-checked against the original outbound request.
     *
     * <p>Returns {@link Optional#empty()} when any precondition fails: the reply must be an
     * {@code <iq>}, must carry a {@code from} JID that satisfies {@link #isDomainOrUserJid(Jid)},
     * must have {@code type="result"}, must echo the request's {@code id}, and must carry a
     * non-negative integer {@code t} attribute.
     *
     * @param stanza the inbound IQ stanza; never {@code null}
     * @param request the original outbound request used to cross-check the echoed {@code id}; never
     *                {@code null}
     * @return an {@link Optional} carrying the parsed projection, or empty when the stanza did not
     *         satisfy the preconditions
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInPingsClientResponseServerResponse",
            exports = "parseClientResponseServerResponse",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxPingsClientResponseServerResponse> of(Stanza stanza, Stanza request) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        if (!stanza.hasDescription("iq")) {
            return Optional.empty();
        }
        var from = stanza.getAttributeAsJid("from").orElse(null);
        if (from == null) {
            return Optional.empty();
        }
        if (!isDomainOrUserJid(from)) {
            return Optional.empty();
        }
        if (!stanza.hasAttribute("type", "result")) {
            return Optional.empty();
        }
        var requestId = request.getAttributeAsString("id").orElse(null);
        if (requestId == null) {
            return Optional.empty();
        }
        if (!stanza.hasAttribute("id", requestId)) {
            return Optional.empty();
        }
        var timestamp = stanza.getAttributeAsLong("t");
        if (timestamp.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SmaxPingsClientResponseServerResponse(from, "result", timestamp.getAsLong()));
    }

    /**
     * Returns whether the given JID is accepted as the reply source.
     *
     * <p>Accepts the JID when it is either a server-only JID for the bare WhatsApp
     * ({@code s.whatsapp.net}), group ({@code g.us}), or call ({@code call}) domains, or any
     * standard user-server JID.
     *
     * @implNote
     * This implementation inlines the disjunction of a domain-JID check and a user-JID check that
     * the WA Web validator object composes; the validator-list shape is collapsed into a single
     * boolean because the parser only needs the truth value.
     *
     * @param jid the JID to validate; never {@code null}
     * @return {@code true} when the JID is accepted as the reply source
     */
    @WhatsAppWebExport(moduleName = "WASmaxInPingsEnums",
            exports = "DOMAINJID_USERJID",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean isDomainOrUserJid(Jid jid) {
        if (jid.isServerJid(JidServer.user())
                || jid.isServerJid(JidServer.groupOrCommunity())
                || jid.isServerJid(JidServer.call())) {
            return true;
        }
        return jid.hasUserServer();
    }

    /**
     * Returns whether the given object is a {@link SmaxPingsClientResponseServerResponse} with equal
     * {@code from}, {@code type}, and {@code timestamp}.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when all three fields match
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxPingsClientResponseServerResponse) obj;
        return this.timestamp == that.timestamp
                && Objects.equals(this.from, that.from)
                && Objects.equals(this.type, that.type);
    }

    /**
     * Returns a hash code derived from {@code from}, {@code type}, and {@code timestamp}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(from, type, timestamp);
    }

    /**
     * Returns a debug-friendly textual representation of this projection.
     *
     * @return the textual representation
     */
    @Override
    public String toString() {
        return "SmaxPingsClientResponseServerResponse[from=" + from
                + ", type=" + type
                + ", timestamp=" + timestamp + ']';
    }
}
