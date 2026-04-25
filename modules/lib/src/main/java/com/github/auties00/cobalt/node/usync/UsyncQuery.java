package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.result.UsyncProtocolError;
import com.github.auties00.cobalt.node.usync.result.UsyncUserResult;
import com.github.auties00.cobalt.node.usync.protocol.UsyncBotProfileProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncBusinessProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncContactProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncDeviceProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncDisappearingModeProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncFeatureProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncLidProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncPictureProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncStatusProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncTextStatusProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncUsernameProtocol;
import com.github.auties00.cobalt.util.RandomIdUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Builder for a USync IQ query.
 *
 * <p>Mirrors {@code WAWebUsync.USyncQuery} from WhatsApp Web. The
 * "must have at least one protocol" invariant is enforced by the type
 * system: instances are constructed via {@link #of(UsyncProtocol)} (or one
 * of the protocol-specific {@code of*} factories), all of which require a
 * starting protocol. Additional protocols, mode, context, and users are
 * attached through fluent {@code with*} setters.
 *
 * <p>The query is pure: {@link #toNode()} produces the outbound IQ stanza
 * and {@link #parseResponse(Node)} parses the inbound response into a
 * {@link UsyncResult}. Backoff side-effects (waiting before send,
 * applying server-supplied {@code error_backoff} hints after parse) are
 * driven by {@code WhatsAppClient.executeUsyncQuery}, which composes both
 * sides with a shared {@link UsyncBackoff} registry.
 *
 * <p><strong>Thread safety.</strong> A single {@code UsyncQuery} is
 * <em>not</em> safe to share across threads. Each call site is expected
 * to construct its own builder, configure it on one thread, and dispatch
 * it once via {@code WhatsAppClient.executeUsyncQuery}. The internal
 * lists ({@code protocols}, {@code users}) and the {@code lidProtocolAdded}
 * flag are not synchronised, so concurrent {@code with*} mutations on the
 * same instance produce {@link java.util.ConcurrentModificationException}
 * during {@link #toNode()} or otherwise undefined behaviour. Concurrent
 * <em>reads</em> after the builder has been fully configured (i.e.
 * concurrent calls to {@link #toNode()} or {@link #parseResponse(Node)}
 * with no parallel {@code with*} calls) are safe but rarely useful.
 * Different threads each holding their own {@code UsyncQuery} can
 * dispatch in parallel without coordination — the underlying transport
 * and the shared {@link UsyncBackoff} registry are concurrency-safe.
 *
 * @implNote WAWebUsync.USyncQuery: instance fields {@code context},
 *     {@code mode}, {@code protocols}, {@code users}, and {@code $1} (the
 *     LID-added flag) are mirrored. The internal {@code $3} method is
 *     split into {@link #toNode()}; the JS {@code execute} method moves
 *     to {@code WhatsAppClient.executeUsyncQuery} so the transport layer
 *     owns dispatch and backoff orchestration. The single-thread-builder
 *     contract matches WA Web's JS where {@code USyncQuery} is also
 *     used in a single-threaded build-then-execute pattern.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public final class UsyncQuery {
    /**
     * Logger used for the parse-success messages WhatsApp Web emits via
     * {@code WALogger.LOG}.
     */
    private static final Logger LOGGER = Logger.getLogger(UsyncQuery.class.getName());

    /**
     * Ordered list of protocols to query. Always non-empty by construction.
     */
    private final List<UsyncProtocol> protocols;

    /**
     * Ordered list of user entries to query.
     */
    private final List<UsyncUser> users;

    /**
     * The {@code context} attribute on the {@code <usync>} stanza.
     */
    private UsyncContext context;

    /**
     * The {@code mode} attribute on the {@code <usync>} stanza.
     */
    private UsyncMode mode;

    /**
     * Whether the LID protocol has already been added. Subsequent
     * {@link #withLidProtocol()} calls are no-ops.
     */
    private boolean lidProtocolAdded;

    /**
     * Hidden constructor; use the {@link #of} factories.
     */
    private UsyncQuery(UsyncProtocol firstProtocol) {
        this.protocols = new ArrayList<>();
        this.protocols.add(firstProtocol);
        this.users = new ArrayList<>();
        this.context = UsyncContext.INTERACTIVE;
        this.mode = UsyncMode.QUERY;
        if (firstProtocol instanceof UsyncLidProtocol) {
            this.lidProtocolAdded = true;
        }
    }

    /**
     * Creates a new query starting with the supplied protocol.
     *
     * @param firstProtocol the first protocol; must not be {@code null}
     * @return a fresh query
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery", adaptation = WhatsAppAdaptation.ADAPTED)
    public static UsyncQuery of(UsyncProtocol firstProtocol) {
        Objects.requireNonNull(firstProtocol, "firstProtocol cannot be null");
        return new UsyncQuery(firstProtocol);
    }

    /** @return a query starting with the contact protocol */
    public static UsyncQuery ofContact(UsyncAddressingMode addressingMode) {
        return of(new UsyncContactProtocol(addressingMode));
    }

    /** @return a query starting with the device protocol */
    public static UsyncQuery ofDevices() {
        return of(new UsyncDeviceProtocol());
    }

    /** @return a query starting with the business protocol */
    public static UsyncQuery ofBusiness() {
        return of(new UsyncBusinessProtocol());
    }

    /** @return a query starting with the picture protocol */
    public static UsyncQuery ofPicture() {
        return of(new UsyncPictureProtocol());
    }

    /** @return a query starting with the status protocol */
    public static UsyncQuery ofStatus() {
        return of(new UsyncStatusProtocol());
    }

    /** @return a query starting with the disappearing-mode protocol */
    public static UsyncQuery ofDisappearingMode() {
        return of(new UsyncDisappearingModeProtocol());
    }

    /** @return a query starting with the LID protocol */
    public static UsyncQuery ofLid() {
        return of(new UsyncLidProtocol());
    }

    /** @return a query starting with the bot-profile protocol */
    public static UsyncQuery ofBotProfile() {
        return of(new UsyncBotProfileProtocol());
    }

    /** @return a query starting with the username protocol */
    public static UsyncQuery ofUsername() {
        return of(new UsyncUsernameProtocol());
    }

    /** @return a query starting with the text-status protocol */
    public static UsyncQuery ofTextStatus() {
        return of(new UsyncTextStatusProtocol());
    }

    /**
     * @return a query starting with the feature protocol restricted to
     *     the given queries
     */
    public static UsyncQuery ofFeatures(List<UsyncFeatureProtocol.FeatureQuery> queries) {
        return of(new UsyncFeatureProtocol(queries));
    }

    /**
     * Sets the query mode. Defaults to {@link UsyncMode#QUERY}.
     *
     * @param mode the mode
     * @return this builder
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withMode", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withMode(UsyncMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode cannot be null");
        return this;
    }

    /**
     * Sets the query context. Defaults to {@link UsyncContext#INTERACTIVE}.
     *
     * @param context the context
     * @return this builder
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withContext", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withContext(UsyncContext context) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        return this;
    }

    /**
     * Adds another protocol to this query.
     *
     * @param protocol the protocol
     * @return this builder
     */
    public UsyncQuery withProtocol(UsyncProtocol protocol) {
        Objects.requireNonNull(protocol, "protocol cannot be null");
        if (protocol instanceof UsyncLidProtocol) {
            return withLidProtocol();
        }
        protocols.add(protocol);
        return this;
    }

    /** Adds the contact protocol. */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withContactProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withContactProtocol(UsyncAddressingMode addressingMode) {
        protocols.add(new UsyncContactProtocol(addressingMode));
        return this;
    }

    /** Adds the business protocol. */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withBusinessProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withBusinessProtocol() {
        protocols.add(new UsyncBusinessProtocol());
        return this;
    }

    /** Adds the device protocol. */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withDeviceProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withDeviceProtocol() {
        protocols.add(new UsyncDeviceProtocol());
        return this;
    }

    /** Adds the disappearing-mode protocol. */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withDisappearingModeProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withDisappearingModeProtocol() {
        protocols.add(new UsyncDisappearingModeProtocol());
        return this;
    }

    /** Adds the picture protocol. */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withPictureProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withPictureProtocol() {
        protocols.add(new UsyncPictureProtocol());
        return this;
    }

    /** Adds the status protocol. */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withStatusProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withStatusProtocol() {
        protocols.add(new UsyncStatusProtocol());
        return this;
    }

    /** Adds the text-status protocol. */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withTextStatusProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withTextStatusProtocol() {
        protocols.add(new UsyncTextStatusProtocol());
        return this;
    }

    /** Adds the feature protocol. */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withFeaturesProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withFeaturesProtocol(List<UsyncFeatureProtocol.FeatureQuery> queries) {
        protocols.add(new UsyncFeatureProtocol(queries));
        return this;
    }

    /**
     * Adds the LID protocol. Idempotent: subsequent calls are no-ops.
     *
     * @return this builder
     * @implNote WAWebUsync.USyncQuery.withLidProtocol: the JS guard is
     *     the {@code this.$1} flag.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withLidProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withLidProtocol() {
        if (!lidProtocolAdded) {
            protocols.add(new UsyncLidProtocol());
            lidProtocolAdded = true;
        }
        return this;
    }

    /** Adds the bot-profile protocol. */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withBotProfileProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withBotProfileProtocol() {
        protocols.add(new UsyncBotProfileProtocol());
        return this;
    }

    /** Adds the username protocol. */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withUsernameProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withUsernameProtocol() {
        protocols.add(new UsyncUsernameProtocol());
        return this;
    }

    /**
     * Adds a user entry.
     *
     * @param user the user
     * @return this builder
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withUser", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncQuery withUser(UsyncUser user) {
        users.add(Objects.requireNonNull(user, "user cannot be null"));
        return this;
    }

    /**
     * Adds a user entry and, if the LID protocol is part of this query,
     * pre-populates the user's LID from the provided current-LID hint.
     *
     * @param user        the user entry
     * @param currentLid  the LID currently associated with the user's PN,
     *                    or {@code null} if unknown
     * @return this builder
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.withUser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncQuery withUser(UsyncUser user, Jid currentLid) {
        Objects.requireNonNull(user, "user cannot be null");
        if (lidProtocolAdded && user.id().isPresent()) {
            var id = user.id().get();
            if (id.hasServer(JidServer.lid())) {
                user.withLid(id);
            } else if (id.hasServer(JidServer.user()) && currentLid != null) {
                user.withLid(currentLid);
            }
        }
        users.add(user);
        return this;
    }

    /** @return the configured context */
    public UsyncContext context() {
        return context;
    }

    /** @return the configured mode */
    public UsyncMode mode() {
        return mode;
    }

    /**
     * Returns an immutable snapshot of the registered protocols.
     *
     * @return the protocols, in registration order
     */
    public List<UsyncProtocol> protocols() {
        return List.copyOf(protocols);
    }

    /**
     * Returns an immutable snapshot of the registered users.
     *
     * @return the users, in registration order
     */
    public List<UsyncUser> users() {
        return List.copyOf(users);
    }

    /**
     * Builds the outbound IQ stanza.
     *
     * @return the IQ builder ready for dispatch through
     *     {@code WhatsAppClient.sendNode}
     * @implNote WAWebUsync.USyncQuery.$3.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery", adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var validUsers = new ArrayList<UsyncUser>();
        for (var user : users) {
            var id = user.id().orElse(null);
            if (id != null && id.hasServer(JidServer.legacyUser())) {
                continue;
            }
            validUsers.add(user);
        }
        if (validUsers.isEmpty() && !users.isEmpty()) {
            LOGGER.warning("Usync warning: every supplied user was filtered out before dispatch");
        }

        var queryChildren = new ArrayList<Node>(protocols.size());
        for (var protocol : protocols) {
            queryChildren.add(protocol.buildQueryElement());
        }
        var queryNode = new NodeBuilder().description("query").content(queryChildren).build();

        var userNodes = new ArrayList<Node>(validUsers.size());
        for (var user : validUsers) {
            var userBuilder = new NodeBuilder().description("user");
            user.id().ifPresent(jid -> userBuilder.attribute("jid", jid.toString()));
            user.phoneJid().ifPresent(jid -> userBuilder.attribute("pn_jid", jid.toString()));
            var children = new ArrayList<Node>(protocols.size());
            for (var protocol : protocols) {
                protocol.buildUserElement(user).ifPresent(children::add);
            }
            userBuilder.content(children);
            userNodes.add(userBuilder.build());
        }
        var listNode = new NodeBuilder().description("list").content(userNodes).build();

        var usyncNode = new NodeBuilder()
                .description("usync")
                .attribute("sid", RandomIdUtils.newId())
                .attribute("index", "0")
                .attribute("last", "true")
                .attribute("mode", mode.wireValue())
                .attribute("context", context.wireValue())
                .content(List.of(queryNode, listNode))
                .build();

        return new NodeBuilder()
                .description("iq")
                .attribute("to", JidServer.user().toString())
                .attribute("xmlns", "usync")
                .attribute("type", "get")
                .attribute("id", RandomIdUtils.newId())
                .content(List.of(usyncNode));
    }

    /**
     * Parses the inbound IQ response into a {@link UsyncResult}.
     *
     * <p>This method is pure: it does not touch any backoff registry. The
     * caller (typically {@code WhatsAppClient.executeUsyncQuery}) is
     * responsible for applying any
     * {@link UsyncProtocolError#errorBackoff()} side-effects.
     *
     * @param response the relay's IQ response
     * @return the aggregated parse result
     * @implNote WAWebUsync.usyncParser.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "usyncParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncResult parseResponse(Node response) {
        Objects.requireNonNull(response, "response cannot be null");
        var type = response.getAttributeAsString("type", "");
        if (!"result".equals(type)) {
            return new UsyncResult(List.of(), Map.of(), Map.of(), parseTopLevelError(response));
        }

        var usyncNode = response.getChild("usync")
                .orElseThrow(() -> new IllegalStateException("usync node missing in response"));
        var resultNode = usyncNode.getChild("result");
        var listNode = usyncNode.getChild("list");

        var protocolErrors = new HashMap<String, UsyncProtocolError>();
        var protocolRefreshes = new HashMap<String, Duration>();
        if (resultNode.isPresent()) {
            for (var protocol : protocols) {
                resultNode.get().getChild(protocol.name()).ifPresent(node -> {
                    var err = node.getChild("error");
                    if (err.isPresent()) {
                        var e = err.get();
                        var code = e.getRequiredAttributeAsInt("code");
                        var text = e.getAttributeAsString("text", "");
                        var backoff = e.getAttributeAsLong("backoff").stream()
                                .mapToObj(Duration::ofSeconds).findFirst().orElse(null);
                        protocolErrors.put(protocol.name(), new UsyncProtocolError(code, text, backoff));
                    } else {
                        node.getAttributeAsLong("refresh").stream()
                                .mapToObj(Duration::ofSeconds).findFirst()
                                .ifPresent(d -> protocolRefreshes.put(protocol.name(), d));
                    }
                });
            }
        }

        var users = new ArrayList<UsyncUserResult>();
        if (listNode.isPresent()) {
            listNode.get().streamChildren("user").forEach(userNode -> {
                var perProtocol = new LinkedHashMap<String, UsyncProtocolResult>();
                for (var protocol : protocols) {
                    userNode.getChild(protocol.name())
                            .ifPresent(child -> perProtocol.put(protocol.name(), protocol.parseUserResult(child)));
                }
                var jid = userNode.getAttributeAsJid("jid").orElse(null);
                var phoneJid = userNode.getAttributeAsJid("pn_jid").orElse(null);
                users.add(new UsyncUserResult(jid, phoneJid, perProtocol));
            });
        }

        LOGGER.fine("usync query success!");
        return new UsyncResult(users, protocolErrors, protocolRefreshes, null);
    }

    /**
     * Builds a top-level error from an IQ whose {@code type} is not
     * {@code "result"}.
     */
    private UsyncTopLevelError parseTopLevelError(Node response) {
        var errorNode = response.getChild("error");
        var code = errorNode.map(n -> n.getAttributeAsInt("code", -1)).orElse(-1);
        var text = errorNode.map(n -> n.getAttributeAsString("text", "")).orElse("");
        var typeAttr = errorNode.map(n -> n.getAttributeAsString("type", "")).orElse("");
        return new UsyncTopLevelError(code, text, typeAttr);
    }
}
