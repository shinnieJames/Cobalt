package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Models the typed outbound {@code <iq xmlns="w:biz" type="get">} stanza that fetches one or more typed business profiles.
 *
 * <p>Chat openers, post-search profile sheets and the merchant directory populate the
 * business-profile collection from a list of merchant JIDs and consume the matching response to
 * render the merchant's banner, description, categories, contact details and hours. Each entry
 * materialises one {@code <profile jid tag/>} child; the optional version tag lets the relay
 * short-circuit when the cached profile matches the supplied tag, returning a header-only
 * acknowledgement instead of the full body.
 *
 * @implNote
 * This implementation matches {@code WAWebQueryBusinessProfileJob}, invoked by
 * {@code WAWebQueryBusinessProfile.queryBusinessProfile} with a version derived from
 * {@code WAWebBusinessProfileVersioningBridge.getBusinessProfileQueryVersion}; the version is
 * routed verbatim into the {@code v} attribute of the {@code <business_profile/>} envelope.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryBusinessProfileJob")
public final class IqQueryBusinessProfileRequest implements IqOperation.Request {
    /**
     * Holds the {@code (businessJid, tag)} entries fanned out as {@code <profile jid tag/>} children of the {@code <business_profile/>} envelope.
     */
    private final List<IqQueryBusinessProfileRequestEntry> entries;

    /**
     * Holds the protocol version routed verbatim into the {@code v} attribute of the {@code <business_profile/>} envelope.
     */
    private final int version;

    /**
     * Constructs a typed request over the given entries and protocol version.
     *
     * <p>The version is derived from {@code WAWebBusinessProfileVersioningBridge.getBusinessProfileQueryVersion}.
     * The list must contain at least one entry because the relay rejects an empty fan-out.
     *
     * @param entries the list of entries; never {@code null} and must be non-empty
     * @param version the protocol version, routed verbatim into the {@code v} attribute
     * @throws NullPointerException     if {@code entries} is {@code null}
     * @throws IllegalArgumentException if {@code entries} is empty
     */
    public IqQueryBusinessProfileRequest(List<IqQueryBusinessProfileRequestEntry> entries, int version) {
        Objects.requireNonNull(entries, "entries cannot be null");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries cannot be empty");
        }
        this.entries = List.copyOf(entries);
        this.version = version;
    }

    /**
     * Returns the requested entries.
     *
     * <p>The list preserves the caller-supplied order, which the fan-out mirrors on the wire.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<IqQueryBusinessProfileRequestEntry> entries() {
        return entries;
    }

    /**
     * Returns the protocol version stamped into the {@code v} attribute.
     *
     * <p>The value is taken verbatim from the caller and must match what the relay expects for the
     * current snapshot.
     *
     * @return the protocol version
     */
    public int version() {
        return version;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation materialises the WAP envelope produced by the
     * {@code WAWebQueryBusinessProfileJob} default export: one {@code <profile jid/>} child per
     * queried entry, with the {@code tag} attribute stamped when the entry carries one, wrapped in
     * a {@code <business_profile v/>} envelope routed to the WhatsApp service via
     * {@link JidServer#user()}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryBusinessProfileJob",
            exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var profileNodes = new ArrayList<Node>();
        for (var entry : entries) {
            var profileBuilder = new NodeBuilder()
                    .description("profile")
                    .attribute("jid", entry.businessJid());
            if (entry.tag().isPresent()) {
                profileBuilder.attribute("tag", entry.tag().get());
            }
            profileNodes.add(profileBuilder.build());
        }
        var businessProfileNode = new NodeBuilder()
                .description("business_profile")
                .attribute("v", String.valueOf(version))
                .content(profileNodes)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:biz")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(businessProfileNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqQueryBusinessProfileRequest) obj;
        return this.version == that.version
                && Objects.equals(this.entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries, version);
    }

    @Override
    public String toString() {
        return "IqQueryBusinessProfileRequest[entries=" + entries
                + ", version=" + version + ']';
    }
}
