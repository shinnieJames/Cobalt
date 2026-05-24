package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregated per-user result for a single {@code <user>} entry inside a USync
 * response.
 *
 * @apiNote
 * One instance is produced for every entry the relay returns in the
 * {@code <list>} child of a USync IQ. The peer's identifiers
 * ({@link #id()} from the {@code jid} attribute and {@link #phoneJid()} from
 * the {@code pn_jid} attribute) are exposed verbatim; per-protocol payloads
 * are looked up by name with {@link #getProtocolResult(UsyncProtocol)} or
 * {@link #getProtocolResult(String)}, and presence is probed with
 * {@link #hasProtocolResult(UsyncProtocol)}. The map is not exposed directly
 * because per-protocol values are typed as {@link UsyncProtocolResult},
 * forcing the caller to pattern-match success and error variants.
 *
 * @implNote
 * This implementation defensively copies the protocol map into an
 * unmodifiable {@link Map#copyOf(Map)} snapshot so embedders cannot mutate
 * shared state. The JS counterpart in {@code WAWebUsync.m} returns a fresh
 * plain object per entry; Cobalt preserves that immutability guarantee.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public final class UsyncUserResult {
    /**
     * The peer's canonical JID echoed in the {@code jid} attribute, or
     * {@code null} when the relay omitted it.
     */
    private final Jid id;

    /**
     * The peer's phone-number JID echoed in the {@code pn_jid} attribute, or
     * {@code null} when the relay omitted it.
     */
    private final Jid phoneJid;

    /**
     * Per-protocol results keyed by the protocol wire name (e.g.
     * {@code "contact"}, {@code "devices"}).
     */
    private final Map<String, UsyncProtocolResult> protocolResults;

    /**
     * Creates a new per-user result.
     *
     * @apiNote
     * Invoked by the top-level USync parser once per {@code <user>} child.
     * Embedders do not normally call this constructor.
     *
     * @param id              the peer's canonical {@link Jid}, or {@code null}
     *                        when the {@code jid} attribute is absent
     * @param phoneJid        the peer's phone-number {@link Jid}, or
     *                        {@code null} when the {@code pn_jid} attribute is
     *                        absent
     * @param protocolResults the per-protocol map, or {@code null} for an
     *                        empty result set
     */
    public UsyncUserResult(Jid id, Jid phoneJid, Map<String, UsyncProtocolResult> protocolResults) {
        this.id = id;
        this.phoneJid = phoneJid;
        this.protocolResults = protocolResults == null ? Map.of() : Map.copyOf(protocolResults);
    }

    /**
     * Returns the peer's canonical {@link Jid}, when present.
     *
     * @apiNote
     * Maps to the {@code jid} attribute on the {@code <user>} child. WA Web
     * normalises this through {@code WAWebJidToWid.deviceJidToUserWid}; Cobalt
     * keeps the raw {@link Jid} for callers that need the exact value the
     * relay echoed back.
     *
     * @return the canonical {@link Jid}, or empty when absent
     */
    public Optional<Jid> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Returns the peer's phone-number {@link Jid}, when present.
     *
     * @apiNote
     * Maps to the {@code pn_jid} attribute on the {@code <user>} child,
     * present when the request specified a phone-number addressing mode and
     * the relay echoes the resolved phone-number JID alongside the canonical
     * id.
     *
     * @return the phone-number {@link Jid}, or empty when absent
     */
    public Optional<Jid> phoneJid() {
        return Optional.ofNullable(phoneJid);
    }

    /**
     * Returns the parse result for the specified protocol, when present.
     *
     * @apiNote
     * Convenience wrapper that delegates to
     * {@link #getProtocolResult(String)} with {@link UsyncProtocol#name()}.
     * Prefer this overload at call sites that already hold a typed protocol
     * descriptor.
     *
     * @param protocol the protocol descriptor
     * @return the parse result, or empty when this user entry has no payload
     *         for the requested protocol
     * @throws NullPointerException if {@code protocol} is {@code null}
     */
    public Optional<UsyncProtocolResult> getProtocolResult(UsyncProtocol protocol) {
        Objects.requireNonNull(protocol, "protocol cannot be null");
        return getProtocolResult(protocol.name());
    }

    /**
     * Returns the parse result for the named protocol, when present.
     *
     * @apiNote
     * Looks up by the wire name the relay tags each child element with
     * ({@code "contact"}, {@code "devices"}, {@code "picture"}, etc.). Returns
     * either a success {@link UsyncProtocolResponse} variant or an
     * {@link UsyncProtocolError}; callers pattern-match on
     * {@link UsyncProtocolResult} to discriminate.
     *
     * @param protocolName the protocol's wire name
     * @return the parse result, or empty when this user entry has no payload
     *         for the named protocol
     */
    public Optional<UsyncProtocolResult> getProtocolResult(String protocolName) {
        return Optional.ofNullable(protocolResults.get(protocolName));
    }

    /**
     * Returns whether the relay returned a parse result for the specified
     * protocol on this user entry.
     *
     * @apiNote
     * Cheaper than {@link #getProtocolResult(UsyncProtocol)} when only
     * presence matters; in particular, lets callers skip protocols the relay
     * omitted (typically because the peer's privacy settings hid the value).
     *
     * @param protocol the protocol descriptor
     * @return {@code true} when a per-protocol entry exists
     * @throws NullPointerException if {@code protocol} is {@code null}
     */
    public boolean hasProtocolResult(UsyncProtocol protocol) {
        Objects.requireNonNull(protocol, "protocol cannot be null");
        return protocolResults.containsKey(protocol.name());
    }
}
