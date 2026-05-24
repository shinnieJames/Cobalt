package com.github.auties00.cobalt.node.smax.prekeys;

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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="encrypt" type="get">} stanza that asks the
 * relay for one or more user pre-key bundles.
 *
 * @apiNote
 * Built by Cobalt's Signal key-fetch path, the counterpart of WA Web's
 * {@code WAWebFetchPrekeysJob.fetchPrekeys}. The relay returns the
 * registration id, identity key, signed pre-key, and an optional unsigned
 * pre-key per user; with these the caller can seed a Signal session for
 * an outbound message. Setting {@code hasUserReasonIdentity=true} also
 * asks the relay to attach the device-identity attestation so the caller
 * can verify the pre-key bundle belongs to the claimed account.
 *
 * @implNote
 * This implementation collapses the {@code WASmaxOutPreKeysClientRequestMixin}
 * envelope shaping (xmlns/to/type wiring) and the per-user
 * {@code <user jid reason?/>} child construction into a single
 * {@link #toNode()} pass; the JS layer routes the same data through three
 * separate mixin functions.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPreKeysFetchKeyBundlesRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutPreKeysClientRequestMixin")
public final class SmaxPreKeysFetchKeyBundlesRequest implements SmaxOperation.Request {
    /**
     * The per-user fetch entries that will appear as
     * {@code <user>} children of the request.
     */
    private final List<UserKeyRequest> users;

    /**
     * Constructs a request for the given list of users.
     *
     * @apiNote
     * Used directly by Cobalt's send path when a chat fan-out requires
     * fetching the pre-key bundles of the addressee devices. The relay
     * rejects empty requests, so the constructor refuses an empty list
     * early.
     *
     * @param users the per-user fetch entries
     * @throws NullPointerException     if {@code users} is {@code null}
     * @throws IllegalArgumentException if {@code users} is empty
     */
    public SmaxPreKeysFetchKeyBundlesRequest(List<UserKeyRequest> users) {
        Objects.requireNonNull(users, "users cannot be null");
        if (users.isEmpty()) {
            throw new IllegalArgumentException("users cannot be empty");
        }
        this.users = List.copyOf(users);
    }

    /**
     * Returns the list of users carried by this request.
     *
     * @apiNote
     * Exposed for test and audit code. The returned list is unmodifiable
     * so callers cannot mutate it after construction.
     *
     * @return an unmodifiable {@link List} of {@link UserKeyRequest}
     */
    public List<UserKeyRequest> users() {
        return users;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hard-codes {@code xmlns="encrypt"},
     * {@code type="get"}, and {@code to=s.whatsapp.net} per the
     * {@code WASmaxOutPreKeysFetchKeyBundlesRequest.makeFetchKeyBundlesRequest}
     * fixture, then nests one {@code <user jid reason?/>} per entry under
     * a single {@code <key>} child.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPreKeysFetchKeyBundlesRequest",
            exports = "makeFetchKeyBundlesRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var userNodes = new ArrayList<Node>(users.size());
        for (var user : users) {
            var userBuilder = new NodeBuilder()
                    .description("user")
                    .attribute("jid", user.userJid());
            if (user.hasUserReasonIdentity()) {
                userBuilder.attribute("reason", "identity");
            }
            userNodes.add(userBuilder.build());
        }
        var keyNode = new NodeBuilder()
                .description("key")
                .content(userNodes)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "encrypt")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(keyNode);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation compares the carried {@link #users} list; two
     * requests are equal when their users lists are equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxPreKeysFetchKeyBundlesRequest) obj;
        return Objects.equals(this.users, that.users);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hashes the carried {@link #users} list to stay
     * consistent with {@link #equals(Object)}.
     */
    @Override
    public int hashCode() {
        return Objects.hash(users);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors the record-like rendering used across
     * the {@code Smax*} stanza family.
     */
    @Override
    public String toString() {
        return "SmaxPreKeysFetchKeyBundlesRequest[users=" + users + ']';
    }

    /**
     * Per-user entry in the outbound {@code <key>} payload.
     *
     * @apiNote
     * Pairs a target user {@link Jid} with the optional
     * {@code reason="identity"} hint. When the hint is set, the relay
     * attaches the device-identity attestation so callers can verify the
     * pre-key bundle's authenticity.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutPreKeysFetchKeyBundlesRequest")
    public static final class UserKeyRequest {
        /**
         * The target user {@link Jid} whose pre-key bundle is being
         * requested.
         */
        private final Jid userJid;

        /**
         * Whether to set {@code reason="identity"} on the
         * {@code <user>} child.
         */
        private final boolean hasUserReasonIdentity;

        /**
         * Constructs a per-user request entry.
         *
         * @apiNote
         * Callers should set {@code hasUserReasonIdentity=true} when
         * they need the relay to include the device-identity attestation
         * (typically for first contact with a previously unknown device);
         * for normal re-keying it can stay {@code false}.
         *
         * @param userJid               the target user {@link Jid}
         * @param hasUserReasonIdentity whether to set the identity-reason
         *                              hint
         * @throws NullPointerException if {@code userJid} is {@code null}
         */
        public UserKeyRequest(Jid userJid, boolean hasUserReasonIdentity) {
            this.userJid = Objects.requireNonNull(userJid, "userJid cannot be null");
            this.hasUserReasonIdentity = hasUserReasonIdentity;
        }

        /**
         * Returns the target user {@link Jid}.
         *
         * @apiNote
         * Used by {@link SmaxPreKeysFetchKeyBundlesRequest#toNode()} to
         * populate the {@code jid} attribute of each {@code <user>}
         * child.
         *
         * @return the user {@link Jid}
         */
        public Jid userJid() {
            return userJid;
        }

        /**
         * Returns whether the identity-reason hint is set.
         *
         * @apiNote
         * Used by {@link SmaxPreKeysFetchKeyBundlesRequest#toNode()} to
         * decide whether to emit the {@code reason="identity"}
         * attribute.
         *
         * @return {@code true} when the hint is set
         */
        public boolean hasUserReasonIdentity() {
            return hasUserReasonIdentity;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares both the {@link #userJid} and the
         * {@link #hasUserReasonIdentity} flag.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (UserKeyRequest) obj;
            return this.hasUserReasonIdentity == that.hasUserReasonIdentity
                    && Objects.equals(this.userJid, that.userJid);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes both fields to stay consistent with
         * {@link #equals(Object)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(userJid, hasUserReasonIdentity);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} stanza family.
         */
        @Override
        public String toString() {
            return "SmaxPreKeysFetchKeyBundlesRequest.UserKeyRequest[userJid=" + userJid
                    + ", hasUserReasonIdentity=" + hasUserReasonIdentity + ']';
        }
    }
}
