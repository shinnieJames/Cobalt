package com.github.auties00.cobalt.node.iq.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.util.RandomIdUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Issues a usync query against a list of users for a set of per-user protocols.
 * <p>
 * Models the outbound {@code <iq xmlns="usync" type="get">} stanza that drives the typed-projection
 * user queries: contact-sync (interactive and background delta refreshes), device-sync (linked-device
 * discovery for Signal-session establishment), business-profile fetch, picture fetch, status fetch,
 * disappearing-mode lookup, LID-address resolution, channel-bot profile fetch, username lookup, and
 * text-status fetch. The relay returns a per-user projection plus per-protocol error and refresh
 * envelopes parsed by {@link IqUsyncResponse}.
 *
 * @implNote
 * This implementation emits one {@code <usync>} child carrying a {@code <query>} list of bare
 * per-protocol grandchildren plus a {@code <list>} list of {@code <user>} subtrees with optional
 * {@code jid} and {@code pn_jid} attributes and per-protocol payload grandchildren. The {@code sid},
 * {@code index="0"} and {@code last="true"} attributes encode a single-iteration fan-out; the
 * per-iteration fan-out path is not modelled here.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public final class IqUsyncRequest implements IqOperation.Request {
    /**
     * Holds the {@link IqUsyncMode} that drives the relay's per-protocol caching strategy.
     */
    private final IqUsyncMode mode;

    /**
     * Holds the {@code context} attribute string identifying why the query is being issued.
     */
    private final String context;

    /**
     * Holds the protocol tag names to query for each user.
     */
    private final List<String> protocols;

    /**
     * Holds the per-user entries being queried.
     */
    private final List<User> users;

    /**
     * Constructs a new usync request with the given mode, context, protocol list and user list.
     * <p>
     * Each entry in {@code protocols} becomes one bare grandchild of the outbound {@code <query>}
     * child (e.g. {@code "devices"} yields {@code <devices/>}); the canonical protocol tags include
     * {@code feature}, {@code devices}, {@code contact}, {@code picture}, {@code status},
     * {@code business}, {@code disappearing_mode}, {@code lid}, {@code bot}, {@code username} and
     * {@code text_status}. The {@code context} value is caller-defined and is typically
     * {@code "interactive"} for foreground fetches or {@code "background"} for warmup paths.
     *
     * @param mode      the query mode; never {@code null}
     * @param context   the context tag; never {@code null}
     * @param protocols the protocol tag names; never {@code null} and never empty
     * @param users     the per-user entries; never {@code null}
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code protocols} is empty
     */
    public IqUsyncRequest(IqUsyncMode mode, String context, List<String> protocols, List<User> users) {
        this.mode = Objects.requireNonNull(mode, "mode cannot be null");
        this.context = Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(protocols, "protocols cannot be null");
        if (protocols.isEmpty()) {
            throw new IllegalArgumentException("protocols cannot be empty");
        }
        this.protocols = List.copyOf(protocols);
        Objects.requireNonNull(users, "users cannot be null");
        this.users = List.copyOf(users);
    }

    /**
     * Returns the bound {@link IqUsyncMode}.
     *
     * @return the mode; never {@code null}
     */
    public IqUsyncMode mode() {
        return mode;
    }

    /**
     * Returns the bound {@code context} attribute string.
     *
     * @return the context tag; never {@code null}
     */
    public String context() {
        return context;
    }

    /**
     * Returns the unmodifiable list of bound protocol tag names.
     *
     * @return the protocol tags; never {@code null} or empty
     */
    public List<String> protocols() {
        return protocols;
    }

    /**
     * Returns the unmodifiable list of bound per-user entries.
     *
     * @return the users; never {@code null}
     */
    public List<User> users() {
        return users;
    }

    /**
     * Builds the outbound {@code <iq>} stanza wrapping the {@code <usync>} envelope.
     * <p>
     * The resulting {@link NodeBuilder} carries a freshly generated IQ id and usync {@code sid}; the
     * dispatch layer does not overwrite either.
     *
     * @implNote
     * This implementation always sets {@code index="0"} and {@code last="true"}, encoding a
     * single-iteration fan-out rather than the multi-iteration pending-device-sync path.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the {@code <usync>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery", adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var queryChildren = new ArrayList<Node>(protocols.size());
        for (var protocol : protocols) {
            var protocolNode = new NodeBuilder()
                    .description(protocol)
                    .build();
            queryChildren.add(protocolNode);
        }
        var queryNode = new NodeBuilder()
                .description("query")
                .content(queryChildren)
                .build();
        var userChildren = new ArrayList<Node>(users.size());
        for (var user : users) {
            userChildren.add(user.toUserNode());
        }
        var listNode = new NodeBuilder()
                .description("list")
                .content(userChildren)
                .build();
        var usyncNode = new NodeBuilder()
                .description("usync")
                .attribute("sid", RandomIdUtils.newId())
                .attribute("index", "0")
                .attribute("last", "true")
                .attribute("mode", mode.wireValue())
                .attribute("context", context)
                .content(queryNode, listNode)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("id", RandomIdUtils.newId())
                .attribute("to", JidServer.user())
                .attribute("xmlns", "usync")
                .attribute("type", "get")
                .content(usyncNode);
    }

    /**
     * Compares this request with the given object for value equality across all four bound fields.
     *
     * @param obj the object to compare against; may be {@code null}
     * @return {@code true} when {@code obj} is an {@link IqUsyncRequest} with equal mode, context,
     *         protocols and users
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqUsyncRequest) obj;
        return this.mode == that.mode
                && Objects.equals(this.context, that.context)
                && Objects.equals(this.protocols, that.protocols)
                && Objects.equals(this.users, that.users);
    }

    /**
     * Returns a hash code derived from all four bound fields.
     *
     * @return the hash code consistent with {@link #equals(Object)}
     */
    @Override
    public int hashCode() {
        return Objects.hash(mode, context, protocols, users);
    }

    /**
     * Returns a debug string listing the mode, context, protocols and users.
     *
     * @return the debug representation
     */
    @Override
    public String toString() {
        return "IqUsyncRequest[mode=" + mode
                + ", context=" + context
                + ", protocols=" + protocols
                + ", users=" + users + ']';
    }

    /**
     * Carries one per-user entry inside the outbound {@code <list>} envelope.
     * <p>
     * Each entry holds an optional primary JID emitted on {@code jid}, an optional phone-number JID
     * emitted on {@code pn_jid}, and any per-protocol payload nodes routed as children of the
     * {@code <user>} subtree. Both {@link #userJid()} and {@link #pnJid()} are optional because some
     * per-protocol queries (e.g. username-lookup, phone-only-lookup) carry exactly one of the two
     * identifiers; at least one is expected to be present. The {@link #userPayloads()} list carries
     * per-protocol element nodes (e.g. {@code <contact>+15551234567</contact>},
     * {@code <devices device_hash ts/>}) contributed by the per-protocol element emitters.
     */
    @WhatsAppWebModule(moduleName = "WAWebUsync")
    @WhatsAppWebModule(moduleName = "WAWebUsyncUser")
    public static final class User {
        /**
         * Holds the optional primary user JID emitted on the {@code jid} attribute when present.
         */
        private final Jid userJid;

        /**
         * Holds the optional phone-number JID emitted on the {@code pn_jid} attribute when present.
         */
        private final Jid pnJid;

        /**
         * Holds the per-protocol payload nodes routed as children of the {@code <user>} subtree.
         */
        private final List<Node> userPayloads;

        /**
         * Constructs a new user entry bound to the given JIDs and per-protocol payloads.
         * <p>
         * Passing {@code null} for {@code userJid} when only the phone-number JID is supplied (and
         * vice versa) encodes the dual-JID LID and PN path; an empty {@code userPayloads} list is
         * valid when the per-protocol elements are contributed elsewhere.
         *
         * @param userJid      the optional primary JID; may be {@code null}
         * @param pnJid        the optional phone-number JID; may be {@code null}
         * @param userPayloads the per-protocol payload nodes; never {@code null}
         * @throws NullPointerException if {@code userPayloads} is {@code null}
         */
        public User(Jid userJid, Jid pnJid, List<Node> userPayloads) {
            this.userJid = userJid;
            this.pnJid = pnJid;
            Objects.requireNonNull(userPayloads, "userPayloads cannot be null");
            this.userPayloads = List.copyOf(userPayloads);
        }

        /**
         * Returns the optional primary user JID.
         *
         * @return an {@link Optional} carrying the JID, or empty when only the phone JID was supplied
         */
        public Optional<Jid> userJid() {
            return Optional.ofNullable(userJid);
        }

        /**
         * Returns the optional phone-number JID.
         *
         * @return an {@link Optional} carrying the JID, or empty when only the primary JID was
         *         supplied
         */
        public Optional<Jid> pnJid() {
            return Optional.ofNullable(pnJid);
        }

        /**
         * Returns the unmodifiable list of per-protocol payload nodes.
         *
         * @return the payloads; never {@code null}
         */
        public List<Node> userPayloads() {
            return userPayloads;
        }

        /**
         * Renders this entry as the {@code <user>} subtree routed inside the outbound
         * {@code <list>} envelope.
         * <p>
         * Driven by {@link IqUsyncRequest#toNode()} rather than called directly.
         *
         * @implNote
         * This implementation omits an absent JID attribute entirely rather than emitting an empty
         * value.
         *
         * @return the rendered {@link Node}
         */
        @WhatsAppWebExport(moduleName = "WAWebUsync",
                exports = "USyncQuery", adaptation = WhatsAppAdaptation.ADAPTED)
        public Node toUserNode() {
            var builder = new NodeBuilder()
                    .description("user");
            if (userJid != null) {
                builder = builder.attribute("jid", userJid);
            }
            if (pnJid != null) {
                builder = builder.attribute("pn_jid", pnJid);
            }
            return builder
                    .content(new ArrayList<>(userPayloads))
                    .build();
        }

        /**
         * Compares this entry with the given object for value equality across both JIDs and the
         * payload list.
         *
         * @param obj the object to compare against; may be {@code null}
         * @return {@code true} when {@code obj} is a {@link User} with equal JIDs and payloads
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (User) obj;
            return Objects.equals(this.userJid, that.userJid)
                    && Objects.equals(this.pnJid, that.pnJid)
                    && Objects.equals(this.userPayloads, that.userPayloads);
        }

        /**
         * Returns a hash code derived from both JIDs and the payload list.
         *
         * @return the hash code consistent with {@link #equals(Object)}
         */
        @Override
        public int hashCode() {
            return Objects.hash(userJid, pnJid, userPayloads);
        }

        /**
         * Returns a debug string listing both JIDs and the payload list.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "Request.User[userJid=" + userJid
                    + ", pnJid=" + pnJid
                    + ", userPayloads=" + userPayloads + ']';
        }
    }
}
