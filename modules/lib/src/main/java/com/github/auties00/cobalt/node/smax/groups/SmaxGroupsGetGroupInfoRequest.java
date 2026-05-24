package com.github.auties00.cobalt.node.smax.groups;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq type="get" xmlns="w:g2" to="<groupJid>">} stanza that fetches a single group's
 * metadata projection, optionally probing the V4-invite-link add-request flow at the same time.
 *
 * @apiNote
 * Drives the chat-info refresh and invite-link landing surfaces backed by
 * {@code WAWebGroupGetGroupInfoJob}, {@code WAWebGroupInviteV4Job}, and similar consumers. The optional
 * {@link #queryPhash()} lets the relay skip parts of the projection that have not changed since the caller's
 * last fetch; the optional add-request triple ({@link #addRequestExpiration()}, {@link #addRequestAdmin()},
 * {@link #addRequestCode()}) attaches the V4 invite landing probe used by {@code WAWebGroupInviteV4Job}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsGetGroupInfoRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseGetGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseIQGetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsAddRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsCodeMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsGetGroupInfoRequestTypeMixin")
public final class SmaxGroupsGetGroupInfoRequest implements SmaxOperation.Request {
    /**
     * The group {@link Jid} routed verbatim into the IQ envelope's {@code to} attribute.
     */
    private final Jid groupJid;

    /**
     * The optional dehydration-hash hint stamped on the inner {@code <query phash="...">} child; when
     * supplied, the relay can return a delta-only projection.
     */
    private final String queryPhash;

    /**
     * The optional V4-invite-link {@code <add_request expiration="...">} attribute; non-null switches the
     * request on the invite-landing probe.
     */
    private final Long addRequestExpiration;

    /**
     * The optional V4-invite-link admin-targeted {@code <add_request admin="...">} recipient; mutually
     * exclusive with {@link #addRequestCode}.
     */
    private final Jid addRequestAdmin;

    /**
     * The optional V4-invite-link code-targeted {@code <add_request code="...">} string; mutually exclusive
     * with {@link #addRequestAdmin}.
     */
    private final String addRequestCode;

    /**
     * Constructs a metadata-only request for the given group.
     *
     * @apiNote
     * Convenience constructor for callers that do not need the dehydration hint or the V4 invite-landing
     * probe; delegates to the full constructor with all optional parameters set to {@code null}.
     *
     * @param groupJid the group {@link Jid}; never {@code null}
     * @throws NullPointerException if {@code groupJid} is {@code null}
     */
    public SmaxGroupsGetGroupInfoRequest(Jid groupJid) {
        this(groupJid, null, null, null, null);
    }

    /**
     * Constructs a fully-parametrised request.
     *
     * @apiNote
     * Pass either {@code addRequestAdmin} or {@code addRequestCode} (not both) when {@code addRequestExpiration}
     * is supplied; the relay rejects requests carrying both target attributes.
     *
     * @param groupJid             the group {@link Jid}; never {@code null}
     * @param queryPhash           the optional dehydration hash hint; may be {@code null}
     * @param addRequestExpiration the optional add-request expiration timestamp; may be {@code null}
     * @param addRequestAdmin      the optional add-request admin target; may be {@code null}
     * @param addRequestCode       the optional add-request code target; may be {@code null}
     * @throws NullPointerException if {@code groupJid} is {@code null}
     */
    public SmaxGroupsGetGroupInfoRequest(Jid groupJid, String queryPhash, Long addRequestExpiration,
                   Jid addRequestAdmin, String addRequestCode) {
        this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
        this.queryPhash = queryPhash;
        this.addRequestExpiration = addRequestExpiration;
        this.addRequestAdmin = addRequestAdmin;
        this.addRequestCode = addRequestCode;
    }

    /**
     * Returns the group {@link Jid} being queried.
     *
     * @return the group JID; never {@code null}
     */
    public Jid groupJid() {
        return groupJid;
    }

    /**
     * Returns the optional dehydration hash.
     *
     * @apiNote
     * Empty means the request is for a full projection; a non-empty value lets the relay return a
     * delta-only response.
     *
     * @return an {@link Optional} carrying the hash, or empty when the caller did not supply one
     */
    public Optional<String> queryPhash() {
        return Optional.ofNullable(queryPhash);
    }

    /**
     * Returns the optional V4-invite-link add-request expiration timestamp.
     *
     * @return an {@link Optional} carrying the expiration, or empty when the caller did not supply one
     */
    public Optional<Long> addRequestExpiration() {
        return Optional.ofNullable(addRequestExpiration);
    }

    /**
     * Returns the optional V4-invite-link add-request admin target.
     *
     * @return an {@link Optional} carrying the admin JID, or empty when the caller did not supply one
     */
    public Optional<Jid> addRequestAdmin() {
        return Optional.ofNullable(addRequestAdmin);
    }

    /**
     * Returns the optional V4-invite-link add-request code target.
     *
     * @return an {@link Optional} carrying the code, or empty when the caller did not supply one
     */
    public Optional<String> addRequestCode() {
        return Optional.ofNullable(addRequestCode);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation stamps {@code phash} on the {@code <query/>} child when {@link #queryPhash()} is
     * non-empty, nests an {@code <add_request/>} child carrying the supplied expiration / admin / code
     * attributes when {@link #addRequestExpiration()} is non-empty, then wraps the result in the canonical
     * {@code <iq xmlns="w:g2" type="get" to="<groupJid>">} envelope.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsGetGroupInfoRequest",
            exports = "makeGetGroupInfoRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var queryBuilder = new NodeBuilder()
                .description("query");
        if (queryPhash != null) {
            queryBuilder.attribute("phash", queryPhash);
        }
        if (addRequestExpiration != null) {
            var addRequestBuilder = new NodeBuilder()
                    .description("add_request")
                    .attribute("expiration", addRequestExpiration);
            if (addRequestAdmin != null) {
                addRequestBuilder.attribute("admin", addRequestAdmin);
            }
            if (addRequestCode != null) {
                addRequestBuilder.attribute("code", addRequestCode);
            }
            queryBuilder.content(addRequestBuilder.build());
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", groupJid)
                .attribute("type", "get")
                .content(queryBuilder.build());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGroupsGetGroupInfoRequest) obj;
        return Objects.equals(this.groupJid, that.groupJid)
                && Objects.equals(this.queryPhash, that.queryPhash)
                && Objects.equals(this.addRequestExpiration, that.addRequestExpiration)
                && Objects.equals(this.addRequestAdmin, that.addRequestAdmin)
                && Objects.equals(this.addRequestCode, that.addRequestCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupJid, queryPhash, addRequestExpiration, addRequestAdmin, addRequestCode);
    }

    @Override
    public String toString() {
        return "SmaxGroupsGetGroupInfoRequest[groupJid=" + groupJid
                + ", queryPhash=" + queryPhash
                + ", addRequestExpiration=" + addRequestExpiration
                + ", addRequestAdmin=" + addRequestAdmin
                + ", addRequestCode=" + addRequestCode + ']';
    }
}
