package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-user result for one entry in a USync response.
 *
 * <p>Each {@code <user>} entry in the {@code <list>} reply carries the
 * peer's identifiers as attributes ({@code jid} and optionally
 * {@code pn_jid}) plus one child element per protocol the query asked
 * about. This class wraps both into a typed, immutable shape.
 *
 * <p>Per-protocol results are not exposed as a raw {@link Map}; callers
 * look up a specific protocol via
 * {@link #getProtocolResult(UsyncProtocol)} (typed) or
 * {@link #getProtocolResult(String)} (by wire name) and check membership
 * via {@link #hasProtocolResult(UsyncProtocol)}.
 *
 * @implNote WAWebUsync.m: the JS function builds a flat object keyed by
 *     protocol name. Cobalt nests the protocols under a private map so
 *     the JID identifiers cannot collide with protocol names and the map
 *     never leaks to callers.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public final class UsyncUserResult {
    /**
     * The peer's canonical JID, or {@code null} when the relay omitted
     * the {@code jid} attribute.
     */
    private final Jid id;

    /**
     * The peer's phone-number JID, or {@code null} when the relay
     * omitted the {@code pn_jid} attribute.
     */
    private final Jid phoneJid;

    /**
     * Map from protocol name to the parsed result for this user.
     */
    private final Map<String, UsyncProtocolResult> protocolResults;

    /**
     * Creates a new per-user result. The map is defensively copied to
     * guarantee external immutability.
     *
     * @param id              the canonical JID, or {@code null}
     * @param phoneJid        the phone-number JID, or {@code null}
     * @param protocolResults the per-protocol results; defaults to an
     *                        empty map when {@code null}
     */
    public UsyncUserResult(Jid id, Jid phoneJid, Map<String, UsyncProtocolResult> protocolResults) {
        this.id = id;
        this.phoneJid = phoneJid;
        this.protocolResults = protocolResults == null ? Map.of() : Map.copyOf(protocolResults);
    }

    /**
     * Returns the peer's canonical JID, when present.
     *
     * @return the JID
     */
    public Optional<Jid> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Returns the peer's phone-number JID, when present.
     *
     * @return the phone-number JID
     */
    public Optional<Jid> phoneJid() {
        return Optional.ofNullable(phoneJid);
    }

    /**
     * Returns the parse result for the specified protocol, when present.
     *
     * @param protocol the protocol descriptor
     * @return the parse result
     */
    public Optional<UsyncProtocolResult> getProtocolResult(UsyncProtocol protocol) {
        Objects.requireNonNull(protocol, "protocol cannot be null");
        return getProtocolResult(protocol.name());
    }

    /**
     * Returns the parse result for the named protocol, when present.
     *
     * @param protocolName the protocol's wire name
     * @return the parse result
     */
    public Optional<UsyncProtocolResult> getProtocolResult(String protocolName) {
        return Optional.ofNullable(protocolResults.get(protocolName));
    }

    /**
     * Returns whether the relay returned a parse result for the
     * specified protocol on this user entry.
     *
     * @param protocol the protocol descriptor
     * @return {@code true} when present
     */
    public boolean hasProtocolResult(UsyncProtocol protocol) {
        Objects.requireNonNull(protocol, "protocol cannot be null");
        return protocolResults.containsKey(protocol.name());
    }
}
