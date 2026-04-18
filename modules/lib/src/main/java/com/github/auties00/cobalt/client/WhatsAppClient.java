package com.github.auties00.cobalt.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.*;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.bot.profile.*;
import com.github.auties00.cobalt.model.business.*;
import com.github.auties00.cobalt.model.business.profile.*;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.chat.community.CommunityLinkedGroup;
import com.github.auties00.cobalt.model.chat.community.CommunityLinkedGroupBuilder;
import com.github.auties00.cobalt.model.chat.community.CommunityMetadata;
import com.github.auties00.cobalt.model.chat.community.CommunityMetadataBuilder;
import com.github.auties00.cobalt.model.chat.group.*;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessageBuilder;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterViewerRole;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.StarAction;
import com.github.auties00.cobalt.model.sync.action.contact.StarActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.mex.json.community.FetchAllSubgroupsMex;
import com.github.auties00.cobalt.node.mex.json.community.FetchAllSubgroupsMex.Response.DefaultSubGroup;
import com.github.auties00.cobalt.node.mex.json.community.FetchAllSubgroupsMex.Response.SubGroups;
import com.github.auties00.cobalt.node.mex.json.community.FetchSubgroupSuggestionsMex;
import com.github.auties00.cobalt.node.mex.json.community.QuerySubgroupParticipantCountMex;
import com.github.auties00.cobalt.node.mex.json.community.TransferCommunityOwnershipMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.AcceptNewsletterAdminInviteMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.CreateNewsletterMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.DeleteNewsletterMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.DemoteNewsletterAdminMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.FetchAllNewslettersMetadataMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.FetchNewsletterMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.JoinNewsletterMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.LeaveNewsletterMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.RevokeNewsletterAdminInviteMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.UpdateNewsletterMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.UpdateNewsletterUserSettingMex;
import com.github.auties00.cobalt.model.newsletter.NewsletterReactionSettings;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.socket.WhatsAppSocketClient;
import com.github.auties00.cobalt.socket.WhatsAppSocketListener;
import com.github.auties00.cobalt.socket.WhatsAppSocketStanza;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRangeBuilder;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.handler.ArchiveChatHandler;
import com.github.auties00.cobalt.sync.handler.ClearChatHandler;
import com.github.auties00.cobalt.sync.handler.DeleteChatHandler;
import com.github.auties00.cobalt.sync.handler.LockChatHandler;
import com.github.auties00.cobalt.sync.handler.MarkChatAsReadHandler;
import com.github.auties00.cobalt.sync.handler.MuteChatHandler;
import com.github.auties00.cobalt.sync.handler.PinChatHandler;
import com.github.auties00.cobalt.sync.key.SyncKeyUtils;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.cobalt.util.RandomIdUtils;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.curve25519.Curve25519;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.key.SignalPreKeyPair;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The central entry point for interacting with a WhatsApp account from
 * Cobalt.
 *
 * <p>A {@code WhatsAppClient} owns the lifecycle of a single session: it
 * wires together the persisted {@link WhatsAppStore}, the Noise-encrypted
 * socket, the Signal protocol ciphers, and the constellation of services
 * responsible for device management, message send/receive, sync, LID
 * migration, and telemetry. Callers obtain instances through
 * {@link #builder()} and drive them through
 * {@link #connect()}, {@link #disconnect()},
 * {@link #reconnect()}, and {@link #logout()}; observation happens through
 * {@link WhatsAppClientListener} callbacks registered on the underlying
 * store.
 *
 * <p>Every method that performs I/O runs on a virtual thread and blocks
 * the caller until a response is available. Errors are funneled through
 * the configured {@link WhatsAppClientErrorHandler} so recovery policy is
 * pluggable rather than hardcoded.
 *
 * @implNote WAWebSocketModel.Socket: the overall shape of the client
 * mirrors WhatsApp Web's top-level socket model, but with the
 * Cobalt-specific DI structure (services injected in the constructor) and
 * virtual-thread blocking replacing module-level imports and async/await.
 * @see WhatsAppClientBuilder
 * @see WhatsAppClientListener
 * @see WhatsAppClientErrorHandler
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@WhatsAppWebModule(moduleName = "WAWebSocketModel")
public final class WhatsAppClient {
    /**
     * The single-byte encoding of the Signal identity key type, used when
     * building the {@code <type>} node in pre-key upload stanzas.
     */
    private static final byte[] SIGNAL_KEY_TYPE = {SignalIdentityPublicKey.type()};

    /**
     * The edge length in pixels used when requesting profile-picture
     * thumbnails.
     */
    private static final int PROFILE_PIC_SIZE = 64;
    /**
     * Pattern used to validate email strings of the shape
     * {@code local@domain}.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^(.+)@(\\S+)$");

    /**
     * The lower bound on the number of pre-keys uploaded per batch; keeps
     * batches useful even if the caller asks for fewer.
     */
    private static final long MIN_PRE_KEYS_COUNT = 5;

    /**
     * The persisted session state (credentials, chats, contacts, Signal
     * keys, listeners) bound to this client.
     */
    private final WhatsAppStore store;
    /**
     * The strategy that decides how the client should react to errors
     * raised by any subsystem.
     */
    private final WhatsAppClientErrorHandler errorHandler;
    /**
     * The strategy that enriches outgoing messages with link previews.
     */
    private final WhatsAppClientMessagePreviewHandler messagePreviewHandler;
    /**
     * The service that drives companion app-state synchronisation (push
     * and pull of sync patches).
     */
    private final WebAppStateService webAppStateService;
    /**
     * The service that migrates legacy addressing to LID-based
     * addressing.
     */
    private final LidMigrationService lidMigrationService;
    /**
     * The service that tracks the companion device list and drives ADV
     * (Auxiliary Device Verification) checks.
     */
    private final DeviceService deviceService;
    /**
     * The service that maintains the AB (A/B) property cache used across
     * feature-gating decisions.
     */
    private final ABPropsService abPropsService;
    /**
     * The service that performs LID migration for inactive groups.
     */
    private final InactiveGroupLidMigrationService inactiveGroupLidMigrationService;
    /**
     * The service that encapsulates message send and receive plumbing.
     */
    private final MessageService messageService;
    /**
     * The service that batches and flushes WAM (WhatsApp telemetry)
     * events.
     */
    private final WamService wamService;
    /**
     * The companion pairing service for the WEB
     */
    private final CompanionPairingService companionPairingService;
    /**
     * The snapshot recovery service
     */
    private final SnapshotRecoveryService snapshotRecoveryService;
    /**
     * The live socket to the WhatsApp server, or {@code null} when the
     * client is not connected.
     */
    private WhatsAppSocketClient socketClient;
    /**
     * The high-level stanza router that consumes nodes from the socket
     * and dispatches them to handlers.
     */
    private final SocketStream socketStream;
    /**
     * Outstanding request/response IQ stanzas, keyed by the {@code id}
     * attribute so matching responses can complete them.
     */
    private final ConcurrentMap<String, WhatsAppSocketStanza> pendingSocketRequests;
    /**
     * The JVM shutdown hook that disconnects the session gracefully on
     * process exit; installed lazily during {@link #connect()}.
     */
    private Thread shutdownHook;

    /**
     * Constructs a new client and wires all of its internal services
     * together.
     *
     * <p>This constructor is package-private because it is only meant to
     * be invoked by the {@link WhatsAppClientBuilder}; use
     * {@link WhatsAppClient#builder()} to obtain a builder instead.
     *
     * @param store                   the persisted session state; must
     *                                not be {@code null}
     * @param webVerificationHandler  the companion-linking verification
     *                                handler; required when
     *                                {@code store.clientType() == WEB}
     *                                and ignored otherwise
     * @param messagePreviewHandler   the outgoing message preview
     *                                decorator; must not be {@code null}
     * @param errorHandler            the recovery strategy for failures;
     *                                must not be {@code null}
     * @throws NullPointerException      if {@code store} or
     *                                   {@code errorHandler} is {@code null}
     * @throws IllegalArgumentException  if the web verification handler
     *                                   is required but missing, or
     *                                   present when it should not be
     */
    WhatsAppClient(WhatsAppStore store, WhatsAppClientVerificationHandler.Web webVerificationHandler, WhatsAppClientMessagePreviewHandler messagePreviewHandler, WhatsAppClientErrorHandler errorHandler) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler cannot be null");
        if ((store.clientType() == WhatsAppClientType.WEB) == (webVerificationHandler == null)) {
            throw new IllegalArgumentException("webVerificationHandler cannot be null when client type is WEB");
        }
        var sessionCipher = new SignalSessionCipher(store);
        var groupCipher = new SignalGroupCipher(store);
        this.abPropsService = new ABPropsService(this);
        this.snapshotRecoveryService = new SnapshotRecoveryService(this, abPropsService);
        this.webAppStateService = new WebAppStateService(this, abPropsService, snapshotRecoveryService);
        this.lidMigrationService = new LidMigrationService(this, abPropsService);
        this.inactiveGroupLidMigrationService = new InactiveGroupLidMigrationService(this, abPropsService);
        this.deviceService = new DeviceService(this, abPropsService, sessionCipher);
        this.messageService = new MessageService(this, sessionCipher, groupCipher, deviceService, abPropsService);
        this.wamService = new WamService(this, abPropsService);
        this.pendingSocketRequests = new ConcurrentHashMap<>();
        this.companionPairingService = new CompanionPairingService(this, webVerificationHandler);
        this.socketStream = new SocketStream(this, webVerificationHandler, lidMigrationService, inactiveGroupLidMigrationService, messageService, abPropsService, deviceService, wamService, snapshotRecoveryService, webAppStateService, companionPairingService);
        this.messagePreviewHandler = messagePreviewHandler;
    }

    /**
     * Returns the shared {@link WhatsAppClientBuilder} entry point.
     *
     * @return the builder singleton
     */
    public static WhatsAppClientBuilder builder() {
        return WhatsAppClientBuilder.INSTANCE;
    }

    //<editor-fold desc="Data">

    /**
     * Returns the persisted session state bound to this client.
     *
     * @return the store
     */
    public WhatsAppStore store() {
        return store;
    }

    /**
     * Returns the message preview handler installed on this client.
     *
     * @return the preview handler
     */
    public WhatsAppClientMessagePreviewHandler messagePreviewHandler() {
        return messagePreviewHandler;
    }

    /**
     * Returns the AB properties service used for feature-gating checks.
     *
     * @return the AB properties service
     */
    public ABPropsService abPropsService() {
        return abPropsService;
    }

    /**
     * Returns the LID migration service, which tracks the progress of
     * migrating legacy addressing (phone-number based) to LID-based
     * addressing across chats.
     *
     * @return the LID migration service
     */
    public com.github.auties00.cobalt.migration.LidMigrationService lidMigrationService() {
        return lidMigrationService;
    }

    //</editor-fold>

    //<editor-fold desc="Connection">

    /**
     * Establishes a connection to the WhatsApp servers.
     *
     * <p>This method opens the encrypted socket, installs the shutdown
     * hook, and starts the stanza pump. It returns as soon as the socket
     * is up; subsequent handshake and login events are delivered
     * asynchronously through {@link WhatsAppClientListener} callbacks.
     *
     * @return {@code this}, for fluent chaining
     * @throws IllegalStateException if the client is already connected
     */
    public WhatsAppClient connect() {
        connect(null);
        return this;
    }

    /**
     * Internal entry point for {@link #connect()} and reconnection paths
     * that need to flag the cause so transient failures are translated
     * into {@link WhatsAppReconnectionException}.
     *
     * @param reason the disconnection reason driving this connect, or
     *               {@code null} for the initial connect
     */
    private void connect(WhatsAppClientDisconnectReason reason) {
        if (isConnected()) {
            throw new IllegalStateException("Client is already connected");
        }

        try {
            this.socketClient = WhatsAppSocketClient.newCipheredSocketClient(store);
            socketClient.connect(new WhatsAppSocketListener() {
                @Override
                public void onNode(Node node) {
                    WhatsAppClient.this.onNode(node);
                }

                @Override
                public void onError(WhatsAppException exception) {
                    handleFailure(exception);
                }

                @Override
                public void onClose() {
                    disconnect(WhatsAppClientDisconnectReason.RECONNECTING);
                }
            });
        } catch (IOException throwable) {
            if (reason == WhatsAppClientDisconnectReason.RECONNECTING) {
                // TODO: Add attempts count
                handleFailure(new WhatsAppReconnectionException(throwable.getMessage(), 0, throwable));
            } else {
                handleFailure(new WhatsAppConnectionException(throwable.getMessage(), throwable));
            }
            return;
        }

        if (shutdownHook == null) {
            this.shutdownHook = Thread.ofPlatform()
                    .name("CobaltShutdownHandler")
                    .unstarted(() -> disconnect(WhatsAppClientDisconnectReason.DISCONNECTED, false));
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }
    }

    /**
     * Dispatches an inbound {@link Node} to the listeners and stream
     * handlers, translating any unhandled error into a recoverable
     * {@link WhatsAppStreamException}.
     *
     * @param node the inbound node
     */
    private void onNode(Node node) {
        try {
            for (var listener : store.listeners()) {
                Thread.startVirtualThread(() -> listener.onNodeReceived(this, node));
            }
            resolvePendingRequest(node);
            socketStream.handle(node);
        } catch (WhatsAppStreamException exception) {
            handleFailure(exception);
        } catch (Throwable throwable) {
            handleFailure(new WhatsAppStreamException(throwable));
        }
    }

    /**
     * Completes the pending request whose {@code id} attribute matches
     * the inbound node, if any.
     *
     * @param node the inbound node that may carry a response to a pending
     *             request
     */
    public void resolvePendingRequest(Node node) {
        var id = node.getAttributeAsString("id", null);
        if (id == null) {
            return;
        }

        var request = pendingSocketRequests.remove(id);
        if (request != null) {
            request.complete(node);
        }
    }

    /**
     * Tears down the session for the given reason.
     *
     * <p>The reason is propagated to listeners via
     * {@link WhatsAppClientListener#onDisconnected(WhatsAppClient, WhatsAppClientDisconnectReason)}
     * and drives store-level side effects (for example, deleting
     * credentials on {@code LOGGED_OUT} or {@code BANNED}).
     *
     * @param reason the disconnection reason
     */
    public void disconnect(WhatsAppClientDisconnectReason reason) {
        disconnect(reason, true);
    }

    /**
     * Internal implementation of {@link #disconnect(WhatsAppClientDisconnectReason)}
     * that allows the shutdown-hook cleanup to be suppressed when the
     * disconnect originates from the hook itself.
     *
     * @param reason                 the disconnection reason
     * @param canRemoveShutdownHook  whether the JVM shutdown hook should
     *                               be removed as part of this disconnect
     */
    private void disconnect(WhatsAppClientDisconnectReason reason, boolean canRemoveShutdownHook) {
        // Per WA Web WAWebSocketModel.sendLogout: flush pending sentinel
        // mutations before disconnecting so key expiration is propagated.
        // WA Web bounds the wait via Math.min(20, Math.max(0, getSyncdSentinelTimeoutSeconds())) * 1000
        // so the logout cannot block indefinitely if a flush stalls.
        if (reason == WhatsAppClientDisconnectReason.LOGGED_OUT
                || reason == WhatsAppClientDisconnectReason.DISCONNECTED) {
            flushDirtyCollectionsWithTimeout();
        }

        wamService.close();

        if (socketClient != null) {
            socketClient.disconnect();
        }

        pendingSocketRequests.forEach((ignored, request) -> request.complete(null));
        pendingSocketRequests.clear();

        try {
            if (reason == WhatsAppClientDisconnectReason.LOGGED_OUT || reason == WhatsAppClientDisconnectReason.BANNED) {
                store.delete();
            } else {
                store.save();
            }
        } catch (IOException e) {
            handleFailure(new WhatsAppStreamException(e));
        }

        socketStream.reset();
        webAppStateService.reset();
        lidMigrationService.reset();

        // Stop ADV check scheduler (will be restarted on successful reconnection)
        deviceService.stopAdvCheckScheduler();

        if (reason != WhatsAppClientDisconnectReason.RECONNECTING && shutdownHook != null && canRemoveShutdownHook) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null;
        }

        for (var listener : store.listeners()) {
            listener.onDisconnected(this, reason);
        }

        if (reason == WhatsAppClientDisconnectReason.RECONNECTING) {
            connect(reason);
        }
    }

    /**
     * Flushes any dirty syncd collections on a virtual thread bounded by
     * the {@code syncd_sentinel_timeout_seconds} AB property.
     *
     * <p>This mirrors the pre-logout sentinel flush performed by WhatsApp
     * Web so that key-expiration mutations are propagated before the
     * socket is closed. The flush runs on a daemon virtual thread joined
     * with the configured timeout; if the join expires or is interrupted,
     * the disconnect proceeds anyway and a warning is logged. Exceptions
     * thrown by the flush itself are swallowed because disconnect must
     * not be blocked by a flush failure.
     *
     * @implNote WAWebSocketModel.sendLogout: bounded sentinel flush wait,
     * where {@code var t = Math.min(20, Math.max(0, getSyncdSentinelTimeoutSeconds())) * 1000}
     * is the upper bound applied here via
     * {@link SyncKeyUtils#getSyncdSentinelTimeoutSeconds(ABPropsService)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSocketModel", exports = "Socket",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void flushDirtyCollectionsWithTimeout() {
        // WAWebSocketModel.sendLogout: var t = Math.min(20, Math.max(0, getSyncdSentinelTimeoutSeconds())) * 1000
        var configuredTimeoutSeconds = SyncKeyUtils.getSyncdSentinelTimeoutSeconds(abPropsService);
        var clampedSeconds = Math.min(20, Math.max(0, configuredTimeoutSeconds));
        var timeoutMs = clampedSeconds * 1000L;

        var flushThread = Thread.ofVirtual()
                .name("CobaltSentinelFlush")
                .unstarted(() -> {
                    try {
                        webAppStateService.flushDirtyCollections();
                    } catch (Exception ignored) {
                        // Best-effort: don't let flush failures block disconnect
                    }
                });
        flushThread.start();

        if (timeoutMs == 0L) {
            // WAWebSocketModel.sendLogout: when t === 0 the await resolves immediately
            return;
        }

        try {
            flushThread.join(timeoutMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }

        if (flushThread.isAlive()) {
            // WAWebSocketModel.sendLogout: timeoutPromise resolves and the await proceeds even if the flush is still inflight
            System.getLogger(WhatsAppClient.class.getName())
                    .log(System.Logger.Level.WARNING,
                            "Sentinel flush did not complete within {0}ms, proceeding with disconnect",
                            timeoutMs);
        }
    }

    /**
     * Sends the given node on the current socket without waiting for a
     * response.
     *
     * <p>Useful for stanzas that either do not require an acknowledgment
     * (for example, presence updates) or whose acknowledgment is routed
     * through an independent channel.
     *
     * @param node the node to send
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    public void sendNodeWithNoResponse(Node node) {
        try {
            socketClient.sendNode(node);
        } catch (IOException exception) {
            throw new WhatsAppSessionException.Closed();
        }
        for (var listener : store.listeners()) {
            Thread.startVirtualThread(() -> listener.onNodeSent(this, node));
        }
    }

    /**
     * Sends a request node and blocks until the corresponding response
     * arrives.
     *
     * <p>Equivalent to {@link #sendNode(NodeBuilder, Function)} with a
     * {@code null} filter, which matches the first response carrying the
     * same {@code id} attribute.
     *
     * @param node the outgoing request builder
     * @return the response node
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    public Node sendNode(NodeBuilder node) {
        return sendNode(node, null);
    }

    /**
     * Sends a request node and blocks until a response matching the
     * supplied filter arrives.
     *
     * <p>If the builder has no {@code id} attribute, a random one is
     * assigned before sending. Listeners receive the outgoing node via
     * {@link WhatsAppClientListener#onNodeSent(WhatsAppClient, Node)}
     * before this method returns.
     *
     * @param node   the outgoing request builder; may be mutated to
     *               inject an {@code id} attribute
     * @param filter an optional predicate restricting the accepted
     *               responses; {@code null} accepts any response
     * @return the response node
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    public Node sendNode(NodeBuilder node, Function<Node, Boolean> filter) {
        if (!node.hasAttribute("id")) {
            node.attribute("id", DataUtils.randomHex(10));
        }

        var outgoing = node.build();
        var outgoingId = outgoing.getRequiredAttribute("id")
                .toString();
        try {
            socketClient.sendNode(outgoing);
        } catch (IOException exception) {
            throw new WhatsAppSessionException.Closed();
        }

        for (var listener : store.listeners()) {
            Thread.startVirtualThread(() -> listener.onNodeSent(this, outgoing));
        }

        var request = new WhatsAppSocketStanza(outgoing, filter);
        pendingSocketRequests.put(outgoingId, request);
        return request.waitForResponse();
    }

    /**
     * Disconnects this client from the WhatsApp servers, preserving its
     * credentials for future reconnections.
     *
     * <p>Equivalent to
     * {@link #disconnect(WhatsAppClientDisconnectReason) disconnect(DISCONNECTED)}.
     */
    public void disconnect() {
        disconnect(WhatsAppClientDisconnectReason.DISCONNECTED);
    }

    /**
     * Disconnects and immediately re-establishes the connection.
     *
     * <p>Equivalent to
     * {@link #disconnect(WhatsAppClientDisconnectReason) disconnect(RECONNECTING)}.
     */
    public void reconnect() {
        disconnect(WhatsAppClientDisconnectReason.RECONNECTING);
    }

    /**
     * Logs this client out of WhatsApp, invalidating the stored
     * credentials.
     *
     * <p>For web companion sessions this issues a
     * {@code remove-companion-device} IQ so that the primary device
     * detaches the companion; for sessions without a known local JID it
     * falls back to a local {@link #disconnect(WhatsAppClientDisconnectReason)}
     * with {@link WhatsAppClientDisconnectReason#LOGGED_OUT}. The next
     * connection attempt requires a fresh authentication ceremony (QR
     * scan, pairing code, or phone-number registration).
     */
    public void logout() {
        var localJid = store.jid();
        if (localJid.isEmpty()) {
            disconnect(WhatsAppClientDisconnectReason.LOGGED_OUT);
        } else {
            var device = new NodeBuilder()
                    .description("remove-companion-device")
                    .attribute("value", localJid.get())
                    .attribute("reason", "user_initiated")
                    .build();
            var iqNode = new NodeBuilder()
                    .description("iq")
                    .attribute("xmlns", "md")
                    .attribute("to", JidServer.user())
                    .attribute("type", "set")
                    .content(device);
            sendNode(iqNode);
        }
    }

    /**
     * Returns whether a live socket to the WhatsApp servers is currently
     * open.
     *
     * @return {@code true} if the socket is open and the handshake has
     *         not been torn down, {@code false} otherwise
     */
    public boolean isConnected() {
        return socketClient != null && socketClient.isConnected();
    }

    /**
     * Blocks the calling thread until this session is disconnected.
     *
     * <p>Installs a transient listener that completes once
     * {@link WhatsAppClientListener#onDisconnected(WhatsAppClient, WhatsAppClientDisconnectReason)}
     * fires with a reason other than
     * {@link WhatsAppClientDisconnectReason#RECONNECTING}, so silent
     * reconnection cycles do not wake the caller.
     *
     * @return {@code this}, for fluent chaining
     */
    public WhatsAppClient waitForDisconnection() {
        if (!isConnected()) {
            return this;
        }

        var future = new CompletableFuture<Void>();
        var listener = new WhatsAppClientListener() {
            @Override
            public void onDisconnected(WhatsAppClient whatsapp, WhatsAppClientDisconnectReason reason) {
                if (reason != WhatsAppClientDisconnectReason.RECONNECTING) {
                    future.complete(null);
                }
            }
        };
        store.addListener(listener);
        future.join();
        store.removeListener(listener);
        return this;
    }

    //</editor-fold>

    //<editor-fold desc="Error handling">

    /**
     * Delegates to the configured {@link WhatsAppClientErrorHandler} and
     * applies the returned {@link WhatsAppClientErrorHandler.Result} as a
     * concrete session-control decision.
     *
     * @param exception the exception to handle
     */
    public void handleFailure(WhatsAppException exception) {
        var result = errorHandler.handleError(this, exception);
        switch (result) {
            case BAN -> disconnect(WhatsAppClientDisconnectReason.BANNED);
            case LOG_OUT -> disconnect(WhatsAppClientDisconnectReason.LOGGED_OUT);
            case DISCONNECT -> disconnect(WhatsAppClientDisconnectReason.DISCONNECTED);
            case RECONNECT -> disconnect(WhatsAppClientDisconnectReason.RECONNECTING);
        }
    }

    /**
     * Pushes a batch of sync mutations for the given patch type to the
     * companion app-state service.
     *
     * @param type    the sync patch type being updated
     * @param patches the ordered mutations to apply
     */
    public void pushWebAppState(SyncPatchType type, List<SyncPendingMutation> patches) {
        webAppStateService.pushPatches(type, patches);
    }

    /**
     * Requests that the companion app-state service pull the latest
     * patches for the given patch types from the server.
     *
     * @param patches the patch types to pull; an empty array is tolerated
     */
    public void pullWebAppState(SyncPatchType... patches) {
        webAppStateService.pullPatches(patches);
    }

    /**
     * Schedules the all-devices-responded check for missing sync key timeout.
     *
     * <p>Called when a companion device responds to a key share request without
     * providing the requested key, to trigger the grace period before fatal.
     */
    public void scheduleAllDevicesRespondedCheck() {
        webAppStateService.scheduleAllDevicesRespondedCheck();
    }

    /**
     * Reschedules the missing sync key timeout check.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdStoreMissingKeys.updateMissingKeys}:
     * after keys with data are received and removed from the missing key store,
     * the timeout is rescheduled because the earliest missing key may have changed.
     *
     * @implNote WAWebSyncdStoreMissingKeys._setMissingKeyTimeout
     */
    public void rescheduleMissingSyncKeyTimeout() {
        webAppStateService.rescheduleMissingSyncKeyTimeout();
    }

    /**
     * Retries orphan mutations across all collections.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan}: called after events that
     * may have introduced new entities (history sync, contact sync) so that
     * previously orphaned mutations can be resolved.
     */
    public void retryOrphanMutations() {
        webAppStateService.retryAllOrphanMutations();
    }

    /**
     * Re-issues the verified-name business certificate after the display
     * name has changed, using the current Signal identity private key
     * for signature.
     *
     * @param newName the new verified display name; {@code null} falls
     *                back to {@link WhatsAppStore#name()}
     */
    private void updateBusinessCertificate(String newName) {
        var details = new BusinessVerifiedNameCertificateDetailsBuilder()
                .verifiedName(Objects.requireNonNullElse(newName, store.name()))
                .issuer(BusinessVerifiedNameCertificate.CertificateIssuer.SMALL_BUSINESS)
                .serial(Math.abs(ThreadLocalRandom.current().nextLong()))
                .build();
        var encodedDetails = BusinessVerifiedNameCertificateDetailsSpec.encode(details);
        var certificate = new BusinessVerifiedNameCertificateBuilder()
                .details(encodedDetails)
                .signature(Curve25519.sign(store.identityKeyPair().privateKey().toEncodedPoint(), encodedDetails))
                .build();
        var verifiedNameRequest = new NodeBuilder()
                .description("verified_name")
                .attribute("v", 2)
                .content(BusinessVerifiedNameCertificateSpec.encode(certificate))
                .build();
        var queryRequest = new NodeBuilder()
                .description("iq")
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
                .attribute("xmlns", "w:biz")
                .content(verifiedNameRequest);
        var verifiedName = sendNode(queryRequest)
                .getChild("verified_name")
                .flatMap(node -> node.getAttributeAsString("id"))
                .orElse("");
        store.setVerifiedName(verifiedName);
    }

    /**
     * Sends an acknowledgment stanza for the given inbound node, using
     * the node's own {@code id} attribute as the acknowledgment id.
     *
     * @param node the inbound node to acknowledge
     */
    public void sendAck(Node node) {
        var id = node.getRequiredAttributeAsString("id");
        sendAck(id, node);
    }

    /**
     * Sends an acknowledgment stanza for the given inbound node using
     * the supplied id.
     *
     * <p>The acknowledgment is routed to the node's {@code from}
     * attribute and, when present, propagates the {@code participant}
     * (as {@code recipient}) and {@code type} attributes. For
     * non-{@code message} stanzas, the original {@code type} is copied
     * over so the server can correlate the ack with the intended stanza
     * class.
     *
     * @param id   the acknowledgment id
     * @param node the inbound node being acknowledged
     */
    public void sendAck(String id, Node node) {
        var ackBuilder = new NodeBuilder()
                .description("ack")
                .attribute("id", id);

        var ackClass = node.description();
        var isMessage = ackClass.equals("message");
        ackBuilder.attribute("class", ackClass);

        var ackTo = node.getRequiredAttributeAsJid("from");
        ackBuilder.attribute("to", ackTo);

        node.getAttributeAsJid("participant")
                .map(jid -> jid)
                .ifPresent(receiptParticipant -> ackBuilder.attribute("recipient", receiptParticipant));

        if (!isMessage) {
            node.getAttributeAsString("type")
                    .ifPresent(type -> ackBuilder.attribute("type", type));
        }

        sendNodeWithNoResponse(ackBuilder.build());
    }

    /**
     * Generates and uploads a fresh batch of Signal pre-keys so remote
     * devices can establish new sessions with this client.
     *
     * <p>The requested count is clamped to a minimum of
     * {@link #MIN_PRE_KEYS_COUNT} so every upload remains useful. The
     * newly generated keys are appended to the store on success.
     *
     * @param keysCount the number of additional pre-keys to generate and
     *                  upload; internally clamped to
     *                  {@link #MIN_PRE_KEYS_COUNT}
     */
    public void sendPreKeys(long keysCount) {
        keysCount = Math.max(keysCount, MIN_PRE_KEYS_COUNT);
        var startId = store.hasPreKeys() ? store.preKeys().getLast().id() + 1 : 1;
        var listBody = new ArrayList<Node>();
        var preKeys = new ArrayList<SignalPreKeyPair>();
        while (keysCount-- > 0) {
            var preKeyPair = SignalPreKeyPair.random(startId++);
            preKeys.add(preKeyPair);
            var id = new NodeBuilder()
                    .description("id")
                    .content(DataUtils.intToBytes(preKeyPair.id(), 3))
                    .build();
            var value = new NodeBuilder()
                    .description("value")
                    .content(preKeyPair.publicKey().toEncodedPoint())
                    .build();
            var preKayNode = new NodeBuilder()
                    .description("key")
                    .content(id, value)
                    .build();
            listBody.add(preKayNode);
        }
        var registration = new NodeBuilder()
                .description("registration")
                .content(DataUtils.intToBytes(store.registrationId(), 4))
                .build();
        var type = new NodeBuilder()
                .description("type")
                .content(SIGNAL_KEY_TYPE)
                .build();
        var identity = new NodeBuilder()
                .description("identity")
                .content(store.identityKeyPair().publicKey().toEncodedPoint())
                .build();
        var list = new NodeBuilder()
                .description("list")
                .content(listBody)
                .build();
        var skeyId = new NodeBuilder()
                .description("id")
                .content(DataUtils.intToBytes(store.signedKeyPair().id(), 3))
                .build();
        var skeyValue = new NodeBuilder()
                .description("value")
                .content(store.signedKeyPair().publicKey().toEncodedPoint())
                .build();
        var skeySignature = new NodeBuilder()
                .description("signature")
                .content(store.signedKeyPair().signature())
                .build();
        var skey = new NodeBuilder()
                .description("skey")
                .content(skeyId, skeyValue, skeySignature)
                .build();
        var queryRequest = new NodeBuilder()
                .description("iq")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .attribute("xmlns", "encrypt")
                .content(registration, type, identity, list, skey);
        sendNode(queryRequest);
        for (var preKey : preKeys) {
            store.addPreKey(preKey);
        }
    }

    /**
     * Sends a delivery or read receipt for a message.
     *
     * <p>This is a no-op when the client does not yet know its own JID;
     * the receipt is silently dropped to avoid sending unauthenticated
     * receipts during the very early stages of login.
     *
     * @param id   the message id to acknowledge
     * @param from the JID of the remote party to receipt
     * @param type the receipt type (for example {@code "read"} or
     *             {@code "played"}); {@code null} for a delivery receipt
     */
    public void sendReceipt(String id, Jid from, String type) {
        var me = store.jid()
                .orElse(null);
        if (me == null) {
            return;
        }

        var receipt = new NodeBuilder()
                .description("receipt")
                .attribute("id", id)
                .attribute("type", type)
                .attribute("to", from)
                .build();
        sendNodeWithNoResponse(receipt);
    }

    /**
     * Queries the metadata of a group or community.
     *
     * @param chat the target group or community
     * @return the non-{@code null} metadata
     * @throws IllegalArgumentException if the JID is not a group or
     *         community
     * @throws NoSuchElementException if the server response is invalid
     */
    public ChatMetadata queryChatMetadata(JidProvider chat) {
        if (!chat.toJid().hasServer(JidServer.groupOrCommunity())) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var jid = chat.toJid();
        var body = new NodeBuilder()
                .description("query")
                .attribute("request", "interactive")
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", jid)
                .attribute("type", "get")
                .content(body);
        var response = sendNode(iqNode);
        return handleChatMetadata(response);
    }

    /**
     * Extracts the {@code group} subtree from the server response and
     * updates the local chat store with the parsed metadata.
     *
     * <p>If the chat is not yet known, a new entry is added to the store
     * with the subject from the server as its display name.
     *
     * @param response the server response node
     * @return the parsed metadata
     * @throws NoSuchElementException if the response does not contain a
     *                                {@code group} node
     */
    private ChatMetadata handleChatMetadata(Node response) {
        var metadataNode = Optional.of(response)
                .filter(entry -> entry.hasDescription("group"))
                .or(() -> response.getChild("group"))
                .orElseThrow(() -> new NoSuchElementException("Erroneous response: %s".formatted(response)));
        var metadata = parseChatMetadata(metadataNode);
        store.addChatMetadata(metadata);
        var chat = store.findChatByJid(metadata.jid())
                .orElseGet(() -> store().addNewChat(metadata.jid()));
        chat.setName(metadata.subject());
        return metadata;
    }

    /**
     * Parses the {@code group} node returned by a chat metadata query
     * into a {@link ChatMetadata} instance, distinguishing group chats
     * from communities based on the presence of the {@code parent} child
     * element.
     *
     * <p>For communities, this method issues additional sub-queries to
     * fetch linked-group participants and sub-groups so the returned
     * metadata carries the full community structure.
     *
     * @param node the {@code group} node from the server response
     * @return the parsed chat metadata
     */
    private ChatMetadata parseChatMetadata(Node node) {
        var groupIdUser = node.getRequiredAttributeAsString("id");
        var groupId = Jid.of(groupIdUser, JidServer.groupOrCommunity());
        var subject = node.getAttributeAsString("subject", "");
        var subjectAuthor = node.getAttributeAsJid("s_o", null);
        var subjectTimestampSeconds = node.getAttributeAsLong("s_t", 0);
        var foundationTimestampSeconds = node.getAttributeAsLong("creation", 0);
        var founder = node.getAttributeAsJid("creator", null);
        var description = node.getChild("description")
                .flatMap(parent -> parent.getChild("body"))
                .flatMap(Node::toContentString)
                .orElse(null);
        var descriptionId = node.getChild("description")
                .flatMap(descriptionNode -> descriptionNode.getAttributeAsString("id"))
                .orElse(null);
        var ephemeral = node.getChild("ephemeral")
                .map(ephemeralNode -> ChatEphemeralTimer.of((int) ephemeralNode.getAttributeAsLong("expiration", 0)))
                .orElse(null);
        var communityNode = node.getChild("parent")
                .orElse(null);
        var lidAddressingMode = node.hasAttribute("addressing_mode", "lid");
        var linkedParent = node.getChild("linked_parent")
                .flatMap(parent -> parent.getAttributeAsJid("jid"))
                .orElse(null);
        var isIncognito = node.hasChild("incognito");
        var defaultSubgroup = node.hasAttribute("default_sub_group", true);
        if (communityNode == null) {
            var restrict = node.hasChild("announce");
            var announce = node.hasChild("restrict");
            var memberAddModeAdminOnly = node.getChild("member_add_mode")
                    .flatMap(Node::toContentString)
                    .map(mode -> Objects.equals(mode, "admin_add"))
                    .orElse(false);
            var groupMembershipApprovalMode = node.getChild("membership_approval_mode")
                    .flatMap(entry -> entry.getChild("group_join"))
                    .map(entry -> entry.hasAttribute("state", "on"))
                    .orElse(false);
            var memberLinkModeAdminOnly = node.getChild("member_link_mode")
                    .flatMap(Node::toContentString)
                    .map(mode -> Objects.equals(mode, "admin_link"))
                    .orElse(false);
            var noFrequentlyForwarded = node.hasChild("no_frequently_forwarded");
            var groupSupport = node.hasChild("support");
            var groupSuspended = node.hasChild("suspended");
            var groupReportToAdminMode = node.hasChild("allow_admin_reports");
            var generalSubgroup = node.hasChild("general_chat");
            var groupGeneralChatAutoAddDisabled = node.hasChild("auto_add_disabled");
            var hiddenSubgroup = node.hasChild("hidden_group");
            var groupHasCapi = node.hasChild("capi");
            var groupSafetyCheck = node.hasChild("group_safety_check");
            var participants = node.streamChildren("participant")
                    .filter(entry -> !entry.hasAttribute("error"))
                    .map(entry -> {
                        var id = entry.getRequiredAttributeAsJid("jid");
                        var role = entry.getAttributeAsString("type")
                                .map(GroupPartipantRole::of)
                                .orElse(GroupPartipantRole.USER);
                        return new GroupParticipantBuilder()
                                .userJid(id)
                                .rank(role)
                                .build();
                    })
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new GroupMetadataBuilder()
                    .jid(groupId)
                    .subject(subject)
                    .subjectAuthorJid(subjectAuthor)
                    .subjectTimestamp(subjectTimestampSeconds == 0 ? null : Instant.ofEpochSecond(subjectTimestampSeconds))
                    .foundationTimestamp(foundationTimestampSeconds == 0 ? null : Instant.ofEpochSecond(foundationTimestampSeconds))
                    .founderJid(founder)
                    .description(description)
                    .descriptionId(descriptionId)
                    .restrict(restrict)
                    .announce(announce)
                    .memberAddModeAdminOnly(memberAddModeAdminOnly)
                    .membershipApprovalMode(groupMembershipApprovalMode)
                    .memberLinkModeAdminOnly(memberLinkModeAdminOnly)
                    .noFrequentlyForwarded(noFrequentlyForwarded)
                    .participants(participants)
                    .ephemeralExpiration(ephemeral)
                    .parentCommunityJid(linkedParent)
                    .isLidAddressingMode(lidAddressingMode)
                    .isIncognito(isIncognito)
                    .defaultSubgroup(defaultSubgroup)
                    .generalSubgroup(generalSubgroup)
                    .hiddenSubgroup(hiddenSubgroup)
                    .support(groupSupport)
                    .suspended(groupSuspended)
                    .reportToAdminMode(groupReportToAdminMode)
                    .generalChatAutoAddDisabled(groupGeneralChatAutoAddDisabled)
                    .hasCapi(groupHasCapi)
                    .groupSafetyCheck(groupSafetyCheck)
                    .build();
        } else {
            var restrict = node.hasChild("locked");
            var announce = node.hasChild("announcement");
            var noFrequentlyForwarded = node.hasChild("no_frequently_forwarded");
            var communityMembershipApprovalMode = node.getChild("membership_approval_mode")
                    .flatMap(entry -> entry.getChild("group_join"))
                    .map(entry -> entry.hasAttribute("state", "on"))
                    .orElse(false);
            var memberAddModeAdminOnly = node.getChild("member_add_mode")
                    .flatMap(Node::toContentString)
                    .map(mode -> Objects.equals(mode, "admin_add"))
                    .orElse(false);
            var memberLinkModeAdminOnly = node.getChild("member_link_mode")
                    .flatMap(Node::toContentString)
                    .map(mode -> Objects.equals(mode, "admin_link"))
                    .orElse(false);
            var allowNonAdminSubGroupCreation = node.hasChild("allow_non_admin_sub_group_creation");
            var support = node.hasChild("support");
            var suspended = node.hasChild("suspended");
            var reportToAdminMode = node.hasChild("allow_admin_reports");
            var communityGeneralSubgroup = node.hasChild("general_chat");
            var generalChatAutoAddDisabled = node.hasChild("auto_add_disabled");
            var hiddenSubgroup = node.hasChild("hidden_group");
            var hasCapi = node.hasChild("capi");
            var groupSafetyCheck = node.hasChild("group_safety_check");
            var participantLabelEnabled = node.hasChild("participant_label_enabled");
            var limitSharingEnabled = node.hasChild("limit_sharing_enabled");
            var isParentGroupClosed = communityNode.hasAttribute("default_membership_approval_mode", "request_required");
            var size = node.getAttributeAsInt("size", null);
            var growthLockedNode = node.getChild("growth_locked").orElse(null);
            var growthLockType = growthLockedNode != null
                    ? growthLockedNode.getAttributeAsString("type").orElse(null)
                    : null;
            var growthLockExpirationSeconds = growthLockedNode != null
                    ? growthLockedNode.getAttributeAsLong("expiration", 0)
                    : 0L;
            var growthLockExpiration = growthLockExpirationSeconds > 0
                    ? Instant.ofEpochSecond(growthLockExpirationSeconds)
                    : null;
            var descriptionNode = node.getChild("description").orElse(null);
            var descriptionTimestampSeconds = descriptionNode != null
                    ? descriptionNode.getAttributeAsLong("t", 0)
                    : 0L;
            var descriptionTimestamp = descriptionTimestampSeconds > 0
                    ? Instant.ofEpochSecond(descriptionTimestampSeconds)
                    : null;
            var descriptionAuthor = descriptionNode != null
                    ? descriptionNode.getAttributeAsJid("participant", null)
                    : null;
            var evolutionVersion = node.getChild("evolution_version")
                    .map(ev -> ev.getAttributeAsInt("value", null))
                    .orElse(null);
            var linkedGroupsQueryBody = new NodeBuilder()
                    .description("linked_groups_participants")
                    .build();
            var linkedGroupsQueryRequest = new NodeBuilder()
                    .description("iq")
                    .attribute("xmlns", "w:g2")
                    .attribute("to", groupId)
                    .attribute("type", "get")
                    .content(linkedGroupsQueryBody);
            var linkedGroupsResponse = sendNode(linkedGroupsQueryRequest);
            var participants = linkedGroupsResponse
                    .streamChild("linked_groups_participants")
                    .flatMap(participantsNodeBody -> participantsNodeBody.streamChildren("participant"))
                    .flatMap(participantNode -> participantNode.streamAttributeAsJid("jid"))
                    .map(jid -> new GroupParticipantBuilder().userJid(jid).build())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            var communityGroupsQuery = new FetchAllSubgroupsMex.Request(groupId.toString(), "INTERACTIVE", null);
            var communityGroupsRequestNode = communityGroupsQuery.toNode();
            var communityGroupsResponseNode = sendNode(communityGroupsRequestNode);
            var communityGroupsResponse = FetchAllSubgroupsMex.Response.of(communityGroupsResponseNode);
            var communityLinkedGroups = new LinkedHashSet<CommunityLinkedGroup>();
            communityGroupsResponse.ifPresent(response -> {
                response.defaultSubGroup().ifPresent(defaultSg -> communityLinkedGroups.add(
                        new CommunityLinkedGroupBuilder()
                                .jid(defaultSg.id().map(Jid::of).orElse(null))
                                .subject(defaultSg.subject().flatMap(DefaultSubGroup.Subject::value).orElse(null))
                                .subjectTimestamp(defaultSg.subject().flatMap(DefaultSubGroup.Subject::creationTime).orElse(null))
                                .parentGroupJid(groupId)
                                .defaultSubgroup(true)
                                .build()
                ));
                response.subGroups()
                        .stream()
                        .map(SubGroups::edges)
                        .flatMap(Collection::stream)
                        .map(SubGroups.Edges::node)
                        .flatMap(Optional::stream)
                        .map(entry -> new CommunityLinkedGroupBuilder()
                                .jid(entry.id().map(Jid::of).orElse(null))
                                .subject(entry.subject().flatMap(SubGroups.Edges.Node.Subject::value).orElse(null))
                                .subjectTimestamp(entry.subject().flatMap(SubGroups.Edges.Node.Subject::creationTime).orElse(null))
                                .parentGroupJid(groupId)
                                .generalSubgroup(entry.properties().flatMap(SubGroups.Edges.Node.Properties::generalChat).map(Boolean::parseBoolean).orElse(false))
                                .membershipApprovalMode(entry.properties().map(SubGroups.Edges.Node.Properties::membershipApprovalModeEnabled).orElse(false))
                                .hiddenSubgroup(entry.properties().flatMap(SubGroups.Edges.Node.Properties::hiddenGroup).map(Boolean::parseBoolean).orElse(false))
                                .build())
                        .forEach(communityLinkedGroups::add);
            });
            return new CommunityMetadataBuilder()
                    .jid(groupId)
                    .subject(subject)
                    .subjectAuthorJid(subjectAuthor)
                    .subjectTimestamp(Instant.ofEpochSecond(subjectTimestampSeconds))
                    .foundationTimestamp(Instant.ofEpochSecond(foundationTimestampSeconds))
                    .founderJid(founder)
                    .description(description)
                    .descriptionId(descriptionId)
                    .descriptionTimestamp(descriptionTimestamp)
                    .descriptionAuthorJid(descriptionAuthor)
                    .restrict(restrict)
                    .announce(announce)
                    .noFrequentlyForwarded(noFrequentlyForwarded)
                    .membershipApprovalMode(communityMembershipApprovalMode)
                    .memberLinkModeAdminOnly(memberLinkModeAdminOnly)
                    .allowNonAdminSubGroupCreation(allowNonAdminSubGroupCreation)
                    .memberAddModeAdminOnly(memberAddModeAdminOnly)
                    .growthLockExpiration(growthLockExpiration)
                    .growthLockType(growthLockType)
                    .reportToAdminMode(reportToAdminMode)
                    .size(size)
                    .support(support)
                    .suspended(suspended)
                    .isParentGroupClosed(isParentGroupClosed)
                    .defaultSubgroup(defaultSubgroup)
                    .generalSubgroup(communityGeneralSubgroup)
                    .hiddenSubgroup(hiddenSubgroup)
                    .groupSafetyCheck(groupSafetyCheck)
                    .generalChatAutoAddDisabled(generalChatAutoAddDisabled)
                    .hasCapi(hasCapi)
                    .evolutionVersion(evolutionVersion)
                    .participantLabelEnabled(participantLabelEnabled)
                    .limitSharingEnabled(limitSharingEnabled)
                    .participants(participants)
                    .ephemeralExpiration(ephemeral)
                    .communityGroups(communityLinkedGroups)
                    .isLidAddressingMode(lidAddressingMode)
                    .isIncognito(isIncognito)
                    .build();
        }
    }

    /**
     * Parses a {@code participant} node into a {@link GroupParticipant},
     * skipping entries that carry an {@code error} attribute.
     *
     * @param node the participant node
     * @return a singleton stream with the parsed participant, or an
     *         empty stream for error entries
     */
    private Stream<GroupParticipant> parseGroupParticipant(Node node) {
        if (node.hasAttribute("error")) {
            return Stream.empty();
        }

        var id = node.getRequiredAttributeAsJid("jid");
        var role = GroupPartipantRole.of(node.getRequiredAttributeAsString("type"));
        var result = new GroupParticipantBuilder()
                .userJid(id)
                .rank(role)
                .build();
        return Stream.of(result);
    }

    /**
     * Queries the WhatsApp Business profile associated with the given
     * contact.
     *
     * @param contact the contact whose business profile should be fetched
     * @return the parsed profile, or empty if the contact is not a
     *         business
     */
    public Optional<BusinessProfile> queryBusinessProfile(JidProvider contact) {
        var profileNode = new NodeBuilder()
                .description("profile")
                .attribute("value", contact)
                .build();
        var businessProfileNode = new NodeBuilder()
                .description("business_profile")
                .attribute("v", 116)
                .content(profileNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:biz")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(businessProfileNode);
        var result = sendNode(iqNode);
        return result.getChild("business_profile")
                .flatMap(entry -> entry.getChild("profile"))
                .map(this::parseBusinessProfile);
    }

    /**
     * Parses the {@code profile} child of a business profile response
     * into a {@link BusinessProfile}.
     *
     * @param node the {@code profile} node
     * @return the parsed profile
     */
    private BusinessProfile parseBusinessProfile(Node node) {
        var jid = node.getRequiredAttributeAsJid("value");
        var address = node.getChild("address")
                .flatMap(Node::toContentString)
                .orElse(null);
        var description = node.getChild("description")
                .flatMap(Node::toContentString)
                .orElse(null);
        var websites = node.streamChildren("website")
                .flatMap(Node::streamContentString)
                .map(URI::create)
                .toList();
        var email = node.getChild("email")
                .flatMap(Node::toContentString)
                .orElse(null);
        var categories = node.streamChildren("categories")
                .flatMap(entry -> entry.streamChild("category"))
                .map(this::parseBusinessCategory)
                .toList();
        var cartEnabled = node.getChild("profile_options")
                .flatMap(entry -> entry.getChild("cart_enabled"))
                .flatMap(Node::toContentBool)
                .orElse(!node.hasChild("profile_options"));
        var hours = node.getChild("business_hours")
                .flatMap(attributes -> attributes.getAttributeAsString("timezone"))
                .map(timezone -> parseBusinessHours(node, timezone))
                .orElse(null);
        var automatedType = node.getChild("automated_type")
                .flatMap(Node::toContentString)
                .map(BusinessAutomatedType::of)
                .orElse(null);
        return new BusinessProfileBuilder()
                .jid(jid)
                .description(description)
                .address(address)
                .email(email)
                .hours(hours)
                .cartEnabled(cartEnabled)
                .websites(websites)
                .categories(categories)
                .automatedType(automatedType)
                .build();
    }

    /**
     * Parses a {@code category} node into a {@link BusinessCategory},
     * URL-decoding the human-readable name.
     *
     * @param node the category node
     * @return the parsed category
     * @throws NoSuchElementException if the category content is missing
     */
    public BusinessCategory parseBusinessCategory(Node node) {
        var id = node.getRequiredAttributeAsString("id");
        var name = node.toContentString()
                .map(content -> URLDecoder.decode(content, StandardCharsets.UTF_8))
                .orElseThrow(() -> new NoSuchElementException("Missing business category content"));
        return new BusinessCategoryBuilder()
                .id(id)
                .name(name)
                .build();
    }

    /**
     * Parses the {@code business_hours} children of a business profile
     * into a {@link BusinessHours} anchored at the supplied timezone.
     *
     * @param node     the business profile node
     * @param timezone the timezone attribute from the
     *                 {@code business_hours} node
     * @return the parsed business hours configuration
     */
    private BusinessHours parseBusinessHours(Node node, String timezone) {
        var entries = node.streamChild("business_hours")
                .flatMap(entry -> entry.streamChildren("business_hours_config"))
                .map(this::parseBusinessHoursEntry)
                .toList();
        return new BusinessHoursBuilder()
                .timeZone(timezone)
                .entries(entries)
                .build();
    }

    /**
     * Parses a single {@code business_hours_config} node into a
     * {@link BusinessHoursEntry}.
     *
     * @param node the config node describing one day of business hours
     * @return the parsed entry
     */
    private BusinessHoursEntry parseBusinessHoursEntry(Node node) {
        var dayOfWeek = node.getRequiredAttributeAsString("day_of_week");
        var mode = node.getRequiredAttributeAsString("mode");
        var openTime = node.getAttributeAsLong("open_time", 0);
        var closeTime = node.getAttributeAsLong("close_time", 0);
        return new BusinessHoursEntryBuilder()
                .day(BusinessHoursDay.of(dayOfWeek))
                .mode(BusinessHoursMode.of(mode))
                .openTime(LocalTime.ofSecondOfDay(openTime))
                .closeTime(LocalTime.ofSecondOfDay(closeTime))
                .build();
    }

    /**
     * Queries the bot profile for the given bot JID with the default persona.
     *
     * @param botJid the bot JID to query
     * @return the bot profile, or empty if not found or on error
     */
    public Optional<BotProfile> queryBotProfile(JidProvider botJid) {
        return queryBotProfile(botJid, null);
    }

    /**
     * Queries the bot profile for the given bot JID.
     *
     * <p>Bot profiles contain the bot's display name, description,
     * registered commands, suggested prompts, and classification flags.
     * The query is executed via the usync protocol with the bot profile
     * protocol element.
     *
     * @param botJid    the bot JID to query
     * @param personaId the persona ID, or {@code null} for the default persona
     * @return the bot profile, or empty if not found or on error
     *
     * @apiNote WAWebRequestBotProfiles.requestBotProfiles: uses usync
     * with context "interactive" and WAWebUsyncBotProfile protocol.
     * WAWebBotProfileCollection: caches results in memory and IndexedDB.
     */
    public Optional<BotProfile> queryBotProfile(JidProvider botJid, String personaId) {
        var profileQueryNode = new NodeBuilder()
                .description("profile")
                .attribute("v", "1")
                .build();
        var botQueryNode = new NodeBuilder()
                .description("bot")
                .content(profileQueryNode)
                .build();
        var queryNode = new NodeBuilder()
                .description("query")
                .content(botQueryNode)
                .build();

        var userProfileNode = new NodeBuilder()
                .description("profile")
                .attribute("persona_id", personaId)
                .build();
        var userBotNode = new NodeBuilder()
                .description("bot")
                .content(userProfileNode)
                .build();
        var userNode = new NodeBuilder()
                .description("user")
                .attribute("jid", botJid.toJid().toUserJid())
                .content(userBotNode)
                .build();
        var listNode = new NodeBuilder()
                .description("list")
                .content(userNode)
                .build();

        var usyncNode = new NodeBuilder()
                .description("usync")
                .attribute("sid", RandomIdUtils.generateSid())
                .attribute("mode", "query")
                .attribute("last", "true")
                .attribute("index", "0")
                .attribute("context", "interactive")
                .content(queryNode, listNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "usync")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(usyncNode);
        var result = sendNode(iqNode);

        return result.streamChildren("usync")
                .flatMap(node -> node.streamChild("list"))
                .flatMap(node -> node.streamChildren("user"))
                .flatMap(user -> user.streamChild("bot"))
                .filter(bot -> bot.getChild("error").isEmpty())
                .flatMap(bot -> bot.streamChild("profile"))
                .map(profile -> parseBotProfile(botJid.toJid().toUserJid(), profile))
                .findFirst();
    }

    /**
     * Parses the {@code profile} child of a usync bot profile response
     * into a {@link BotProfile}.
     *
     * @param botJid  the bot JID the response pertains to
     * @param profile the profile node
     * @return the parsed bot profile
     */
    private BotProfile parseBotProfile(Jid botJid, Node profile) {
        var name = profile.getChild("name")
                .flatMap(Node::toContentString)
                .orElse(null);
        var attributes = profile.getChild("attributes")
                .flatMap(Node::toContentString)
                .orElse(null);
        var description = profile.getChild("description")
                .flatMap(Node::toContentString).orElse(null);
        var category = profile.getChild("category")
                .flatMap(Node::toContentString)
                .map(BotProfileCategory::of)
                .orElse(null);
        var isDefault = profile.getChild("default")
                .flatMap(Node::toContentBool)
                .orElse(false);
        var personaId = profile.getAttributeAsString("persona_id", null);

        var prompts = profile.streamChild("prompts")
                .flatMap(promptsNode -> promptsNode.streamChildren("prompt"))
                .map(WhatsAppClient::parseBotPrompt)
                .toList();

        var commandsDescription = profile.getChild("commands")
                .flatMap(commandsNode -> commandsNode.getChild("description"))
                .flatMap(Node::toContentString)
                .orElse(null);
        var commands = profile.streamChild("commands")
                .flatMap(commandsNode -> commandsNode.streamChildren("command"))
                .map(WhatsAppClient::parseBotCommand)
                .toList();

        var isMetaCreated = profile.getChild("is_meta_created")
                .flatMap(Node::toContentBool)
                .orElse(false);

        var creatorNode = profile.getChild("creator").orElse(null);
        var creatorName = creatorNode != null
                ? creatorNode.getChild("name").flatMap(Node::toContentString).orElse(null)
                : null;
        var creatorProfileUrl = creatorNode != null
                ? creatorNode.getChild("profile_url").flatMap(Node::toContentString).map(URI::create).orElse(null)
                : null;

        var professionalStatus = profile.getChild("posing_as_professional")
                .flatMap(node -> node.getAttributeAsString("type"))
                .map(BotProfessionalStatus::of)
                .orElse(null);

        return new BotProfileBuilder()
                .jid(botJid)
                .name(name)
                .attributes(attributes)
                .description(description)
                .category(category)
                .isDefault(isDefault)
                .prompts(prompts)
                .personaId(personaId)
                .commands(commands)
                .commandsDescription(commandsDescription)
                .isMetaCreated(isMetaCreated)
                .creatorName(creatorName)
                .creatorProfileUrl(creatorProfileUrl)
                .professionalStatus(professionalStatus)
                .build();
    }

    /**
     * Parses a {@code command} node into a {@link BotProfileCommand}.
     *
     * @param command the command node from a bot profile
     * @return the parsed command descriptor
     */
    private static BotProfileCommand parseBotCommand(Node command) {
        var commandName = command.getChild("name")
                .flatMap(Node::toContentString)
                .orElse("");
        var commandDescription = command.getChild("description")
                .flatMap(Node::toContentString)
                .orElse(null);
        return new BotProfileCommandBuilder()
                .name(commandName)
                .description(commandDescription)
                .build();
    }

    /**
     * Parses a {@code prompt} node into a {@link BotProfilePrompt}.
     *
     * @param prompt the prompt node from a bot profile
     * @return the parsed suggested-prompt descriptor
     */
    private static BotProfilePrompt parseBotPrompt(Node prompt) {
        var emoji = prompt.getChild("emoji")
                .flatMap(Node::toContentString)
                .orElse(null);
        var text = prompt.getChild("text")
                .flatMap(Node::toContentString)
                .orElse(null);
        return new BotProfilePromptBuilder()
                .emoji(emoji)
                .text(text)
                .build();
    }

    /**
     * Builds the IQ node used to initiate a voice or video call with the
     * given JID.
     *
     * @param jid the call recipient
     * @return the call offer node
     * @throws UnsupportedOperationException currently a stub pending
     *                                       implementation
     */
    private Node createCall(JidProvider jid) {
        throw new UnsupportedOperationException();
    }

    /**
     * Queries the server for the list of newsletters this account follows
     * and reconciles them into the local store.
     *
     * <p>Dispatches the
     * {@link FetchAllNewslettersMetadataMex.Request mexFetchAllNewsletters}
     * MEX query and, for every item returned, ensures a matching
     * {@link Newsletter} exists in the store keyed by its JID. The order of
     * the returned collection mirrors the server-side order, which WA Web
     * also surfaces verbatim to the UI.
     *
     * @return the newsletters, in server order
     *
     * @implNote WAWebNewsletterMetadataJob.getAllNewslettersMetadata delegates
     * to {@code WAWebNewsletterMetadataQueryJob.queryAllNewslettersMetadata}
     * which ultimately emits the {@code mexFetchAllNewsletters} GraphQL
     * query. Cobalt skips the in-memory orchestrator layer and calls the
     * MEX builder directly, then upserts the minimal {@link Newsletter}
     * state (JID) into the store so subsequent lookups succeed.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterMetadataJob", exports = "getAllNewslettersMetadata",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public SequencedCollection<Newsletter> queryNewsletters() {
        var request = new FetchAllNewslettersMetadataMex.Request(Boolean.TRUE); // WAWebMexFetchAllNewslettersMetadataJob.mexFetchAllNewsletters: fetch_wamo_sub: true
        var response = sendNode(request.toNode());
        var parsed = FetchAllNewslettersMetadataMex.Response.of(response);
        var newsletters = new LinkedHashSet<Newsletter>();
        parsed.ifPresent(r -> {
            for (var item : r.items()) {
                item.id().ifPresent(id -> {
                    var jid = Jid.of(id); // WAJids.toNewsletterJid
                    var newsletter = store.findNewsletterByJid(jid)
                            .orElseGet(() -> store.addNewNewsletter(jid));
                    newsletters.add(newsletter);
                });
            }
        });
        return newsletters;
    }

    /**
     * Queries the server for the list of group chats this account
     * participates in.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code get} addressed to
     * {@link JidServer#groupOrCommunity()} carrying a
     * {@code <participating><participants/></participating>} body. The
     * response contains a {@code <groups>} element with one
     * {@code <group>} child per participating group; each child is parsed
     * via {@link #parseChatMetadata(Node)} and merged into the local
     * store. The returned collection preserves server order and contains
     * the {@link Chat} counterparts.
     *
     * @return the groups, in server order
     *
     * @implNote WAWebGroupQueryJob.queryAllGroups delegates to
     * {@code WASmaxGroupsGetParticipatingGroupsRPC.sendGetParticipatingGroupsRPC}
     * which emits
     * {@code WASmaxOutGroupsGetParticipatingGroupsRequest.makeGetParticipatingGroupsRequest}
     * with {@code xmlns="w:g2", to=G_US, type="get"} and an
     * {@code <participating>} child carrying optional
     * {@code <participants/>} and {@code <description/>} markers. Cobalt
     * requests the full {@code <participants/>} shape to populate the
     * chat store and surfaces the resulting {@link Chat} list; the
     * truncated-group recovery path is skipped because Cobalt's store
     * keeps full metadata in-memory.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupQueryJob", exports = "queryAllGroups",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public SequencedCollection<Chat> queryGroups() {
        var participantsMarker = new NodeBuilder()
                .description("participants") // WASmaxOutGroupsGetParticipatingGroupsRequest.makeGetParticipatingGroupsRequestParticipatingParticipants
                .build();
        var participatingNode = new NodeBuilder()
                .description("participating") // WASmaxOutGroupsGetParticipatingGroupsRequest: smax("participating", null, participants?, description?)
                .content(participantsMarker) // WAWebGroupQueryJob.queryAllGroups: hasParticipants: true
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseGetServerMixin: xmlns: "w:g2"
                .attribute("to", Jid.of(JidServer.groupOrCommunity())) // WASmaxOutGroupsBaseGetServerMixin: to: G_US
                .attribute("type", "get") // WAWebSmaxBaseIQGetRequestMixin: type: "get"
                .content(participatingNode);
        var response = sendNode(iqNode); // WASmaxGroupsGetParticipatingGroupsRPC.sendGetParticipatingGroupsRPC
        var chats = new LinkedHashSet<Chat>();
        response.streamChildren() // response "<groups>" wrapper (or direct "<group>" children)
                .flatMap(wrapper -> wrapper.hasDescription("group")
                        ? Stream.of(wrapper)
                        : wrapper.streamChildren("group"))
                .forEach(groupNode -> {
                    var metadata = parseChatMetadata(groupNode); // WAWebGroupsQueryApi.parseGroupSmax
                    store.addChatMetadata(metadata);
                    var chat = store.findChatByJid(metadata.jid())
                            .orElseGet(() -> store.addNewChat(metadata.jid()));
                    chat.setName(metadata.subject());
                    chats.add(chat);
                });
        return chats;
    }

    /**
     * Fetches the current invite code for the given group so it can be
     * shared as a {@code chat.whatsapp.com/...} link.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code get} addressed to
     * the group JID with an empty {@code <invite/>} body. The server
     * response contains a {@code <invite code="..."/>} child whose
     * {@code code} attribute is the opaque invite token.
     *
     * @param group the non-{@code null} target group JID
     * @return the non-{@code null} invite code
     * @throws NullPointerException     if {@code group} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote The wire form matches the legacy
     * {@code queryGroupInviteCodeParser} in WAWebGroupInviteJob, which
     * reads {@code iq > invite[code]}. WA Web's
     * {@code WAWebGroupInviteAction.queryGroupInviteCode} currently
     * routes through {@code WAWebMexFetchGroupInviteCodeJob} (GraphQL
     * MEX); Cobalt uses the equivalent IQ-level fetch the same parser
     * understands, keeping the code free from MEX plumbing for a flow
     * that is already fully supported on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteAction", exports = "queryGroupInviteCode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public String queryGroupInviteCode(Jid group) {
        Objects.requireNonNull(group, "group cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var inviteNode = new NodeBuilder()
                .description("invite") // queryGroupInviteCodeParser: e.child("invite")
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", group) // WASmaxOutGroupsBaseGetGroupMixin: to: GROUP_JID(t)
                .attribute("type", "get")
                .content(inviteNode);
        var response = sendNode(iqNode);
        return response.getChild("invite") // queryGroupInviteCodeParser: e.child("invite").attrString("code")
                .flatMap(invite -> invite.getAttributeAsString("code"))
                .orElseThrow(() -> new NoSuchElementException("Missing invite code in response: %s".formatted(response)));
    }

    /**
     * Revokes the current invite code for the given group and returns
     * the freshly minted replacement.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the group JID with an empty {@code <invite/>} body. The server
     * rotates the code and returns an {@code <invite code="..."/>}
     * carrying the new value.
     *
     * @param group the non-{@code null} target group JID
     * @return the non-{@code null} new invite code
     * @throws NullPointerException     if {@code group} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote WAWebGroupInviteJob.resetGroupInviteCode emits the same
     * {@code iq[type=set, xmlns="w:g2", to=GROUP_JID]<invite/>} shape and
     * parses the returned {@code <invite code="..."/>} with
     * {@code queryGroupInviteCodeParser}.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob", exports = "resetGroupInviteCode",
            adaptation = WhatsAppAdaptation.DIRECT)
    public String revokeGroupInviteCode(Jid group) {
        Objects.requireNonNull(group, "group cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var inviteNode = new NodeBuilder()
                .description("invite") // WAWebGroupInviteJob.resetGroupInviteCode: wap("invite", null)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WAWebGroupInviteJob.resetGroupInviteCode: xmlns: "w:g2"
                .attribute("to", group) // WAWebGroupInviteJob.resetGroupInviteCode: to: GROUP_JID(e)
                .attribute("type", "set") // WAWebGroupInviteJob.resetGroupInviteCode: type: "set"
                .content(inviteNode);
        var response = sendNode(iqNode);
        return response.getChild("invite") // queryGroupInviteCodeParser: e.child("invite").attrString("code")
                .flatMap(invite -> invite.getAttributeAsString("code"))
                .orElseThrow(() -> new NoSuchElementException("Missing invite code in response: %s".formatted(response)));
    }

    /**
     * Joins a group using a public invite code (typically extracted from
     * a {@code chat.whatsapp.com/XYZ} link) and returns the JID of the
     * joined group.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * {@link JidServer#groupOrCommunity()} containing a single
     * {@code <invite code="...">} element. The server replies with
     * either a {@code <group jid="..."/>} child on immediate join, or a
     * {@code <membership_approval_request jid="..."/>} child when the
     * group requires admin approval; in both cases the group JID is
     * extracted from the {@code jid} attribute.
     *
     * @param inviteCode the non-{@code null} invite code
     * @return the non-{@code null} JID of the joined group
     * @throws NullPointerException   if {@code inviteCode} is {@code null}
     * @throws NoSuchElementException if the server response is malformed
     *
     * @implNote WAWebGroupInviteJob.joinGroupViaInvite builds
     * {@code iq[type=set, xmlns="w:g2", to=G_US]<invite code=CUSTOM_STRING(e)/>}
     * and parses the response with {@code joinGroupViaInviteParser},
     * accepting either a {@code <group>} or
     * {@code <membership_approval_request>} child. Cobalt mirrors that
     * wire shape and returns the group JID as-is; the membership-request
     * vs. immediate-join distinction is preserved on the wire but
     * collapsed in the return value because both paths ultimately
     * resolve to the same group JID.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob", exports = "joinGroupViaInvite",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Jid joinGroupViaInvite(String inviteCode) {
        Objects.requireNonNull(inviteCode, "inviteCode cannot be null");
        var inviteNode = new NodeBuilder()
                .description("invite") // WAWebGroupInviteJob.joinGroupViaInvite: wap("invite", {code: CUSTOM_STRING(e)})
                .attribute("code", inviteCode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WAWebGroupInviteJob.joinGroupViaInvite: xmlns: "w:g2"
                .attribute("to", Jid.of(JidServer.groupOrCommunity())) // WAWebGroupInviteJob.joinGroupViaInvite: to: G_US
                .attribute("type", "set") // WAWebGroupInviteJob.joinGroupViaInvite: type: "set"
                .content(inviteNode);
        var response = sendNode(iqNode);
        return response.getChild("group") // joinGroupViaInviteParser: maybeChild("group")
                .or(() -> response.getChild("membership_approval_request")) // joinGroupViaInviteParser: t ? membership_approval_request : group
                .flatMap(node -> node.getAttributeAsJid("jid")) // joinGroupViaInviteParser: attrGroupJid("jid")
                .orElseThrow(() -> new NoSuchElementException("Invalid join-group response: %s".formatted(response)));
    }

    /**
     * Queries public group metadata using only a public invite code,
     * without joining the group.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code get} addressed to
     * {@link JidServer#groupOrCommunity()} with an
     * {@code <invite code="..."/>} body. The server replies with a
     * {@code <group>} subtree that is parsed by
     * {@link #parseChatMetadata(Node)} and returned as a
     * {@link GroupMetadata}.
     *
     * @param inviteCode the non-{@code null} invite code
     * @return the non-{@code null} group metadata
     * @throws NullPointerException   if {@code inviteCode} is {@code null}
     * @throws NoSuchElementException if the server response is not a group
     *
     * @implNote WAWebGroupQueryJob.queryGroupInvite delegates to
     * {@code WASmaxGroupsGetInviteGroupInfoRPC.sendGetInviteGroupInfoRPC}
     * which emits
     * {@code WASmaxOutGroupsGetInviteGroupInfoRequest.makeGetInviteGroupInfoRequest}
     * ({@code xmlns="w:g2", to=G_US, type="get"} + {@code <invite code=...>}).
     * Cobalt emits the same wire shape.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupQueryJob", exports = "queryGroupInvite",
            adaptation = WhatsAppAdaptation.DIRECT)
    public GroupMetadata queryGroupInfoByInviteCode(String inviteCode) {
        Objects.requireNonNull(inviteCode, "inviteCode cannot be null");
        var inviteNode = new NodeBuilder()
                .description("invite") // WASmaxOutGroupsGetInviteGroupInfoRequest: smax("invite", {code: CUSTOM_STRING(t)})
                .attribute("code", inviteCode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseGetServerMixin: xmlns: "w:g2"
                .attribute("to", Jid.of(JidServer.groupOrCommunity())) // WASmaxOutGroupsBaseGetServerMixin: to: G_US
                .attribute("type", "get") // WAWebSmaxBaseIQGetRequestMixin: type: "get"
                .content(inviteNode);
        var response = sendNode(iqNode); // WASmaxGroupsGetInviteGroupInfoRPC.sendGetInviteGroupInfoRPC
        var metadata = handleChatMetadata(response); // WAWebGroupsQueryApi.parseGroupSmax
        if (!(metadata instanceof GroupMetadata groupMetadata)) {
            throw new NoSuchElementException("Expected a group metadata, got %s".formatted(metadata));
        }
        return groupMetadata;
    }

    /**
     * Queries group metadata using a v4 invite received in-band via a
     * {@link com.github.auties00.cobalt.model.message.group.GroupInviteMessage}.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code get} addressed to
     * the group JID with an {@code <add_request>} child carrying the
     * invite code, the expiration and the administrator that issued the
     * invite. The server replies with a {@code <group>} subtree that is
     * parsed by {@link #parseChatMetadata(Node)}.
     *
     * @param invitee         the non-{@code null} JID of the invitee
     *                        ({@code self})
     * @param sender          the non-{@code null} group or administrator
     *                        JID that identifies the invite target on
     *                        the wire ({@code iqTo})
     * @param inviteTimestamp the invite expiration time in seconds since
     *                        the Unix epoch
     * @param inviteCode      the non-{@code null} invite code from the
     *                        {@code GroupInviteMessage}
     * @return the non-{@code null} group metadata
     * @throws NullPointerException   if any JID / invite code is {@code null}
     * @throws NoSuchElementException if the server response is not a group
     *
     * @implNote WAWebGroupInviteV4Job.queryGroupInviteV4 delegates to
     * {@code WASmaxGroupsGetGroupInfoRPC.sendGetGroupInfoRPC} with
     * {@code addRequestArgs:{addRequestCode, addRequestExpiration, addRequestAdmin}, iqTo}.
     * The resulting SMAX maps to
     * {@code iq[type=get, xmlns="w:g2", to=iqTo]<add_request code=... expiration=... admin=... invitee=.../>}.
     * Cobalt emits that wire shape directly; the {@code queryPhash}
     * optimisation (which returns {@code phashMatch:true} when the
     * cached participant list is still current) is not used because
     * Cobalt's store is in-memory and always authoritative.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteV4Job", exports = "queryGroupInviteV4",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public GroupMetadata queryGroupInfoByInviteV4(Jid invitee, Jid sender, long inviteTimestamp, String inviteCode) {
        Objects.requireNonNull(invitee, "invitee cannot be null");
        Objects.requireNonNull(sender, "sender cannot be null");
        Objects.requireNonNull(inviteCode, "inviteCode cannot be null");
        var addRequestNode = new NodeBuilder()
                .description("add_request") // WAWebGroupInviteV4Job.queryGroupInviteV4: addRequestArgs
                .attribute("code", inviteCode) // addRequestCode
                .attribute("expiration", inviteTimestamp) // addRequestExpiration
                .attribute("admin", sender) // addRequestAdmin
                .attribute("invitee", invitee)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseGetGroupMixin: xmlns: "w:g2"
                .attribute("to", sender) // iqTo
                .attribute("type", "get")
                .content(addRequestNode);
        var response = sendNode(iqNode); // WASmaxGroupsGetGroupInfoRPC.sendGetGroupInfoRPC
        var metadata = handleChatMetadata(response); // WAWebGroupsQueryApi.parseGroupSmax
        if (!(metadata instanceof GroupMetadata groupMetadata)) {
            throw new NoSuchElementException("Expected a group metadata, got %s".formatted(metadata));
        }
        return groupMetadata;
    }

    /**
     * Accepts an in-band group invite (v4) sent by an administrator,
     * joining the referenced group.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the group JID with an {@code <accept>} child carrying the invite
     * code, its expiration, and the inviting administrator. On success
     * the server returns a confirmation from which the joined group's
     * JID is extracted (or, if the group requires approval, the same
     * JID is returned because the wire protocol reuses the group JID
     * for the pending request).
     *
     * @param group           the non-{@code null} group JID
     * @param target          the non-{@code null} inviting administrator
     *                        JID
     * @param inviteTimestamp the invite expiration time in seconds since
     *                        the Unix epoch
     *
     * @implNote WAWebGroupInviteV4Job.joinGroupViaInviteV4 delegates to
     * {@code WASmaxGroupsAcceptGroupAddRPC.sendAcceptGroupAddRPC} with
     * {@code acceptCode, acceptExpiration, acceptAdmin}. Cobalt mirrors
     * the request wire shape; the SMAX response variants
     * ({@code AcceptGroupAddResponseSuccess} and
     * {@code AcceptGroupAddResponseGroupJoinRequestSuccess}) are not
     * distinguished by the caller because both resolve to the same
     * group JID.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteV4Job", exports = "joinGroupViaInviteV4",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendGroupInviteV4(Jid group, Jid target, long inviteTimestamp) {
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var acceptNode = new NodeBuilder()
                .description("accept") // WAWebGroupInviteV4Job.joinGroupViaInviteV4: acceptArgs
                .attribute("code", "") // acceptCode placeholder — caller supplies via companion queryGroupInfoByInviteV4
                .attribute("expiration", inviteTimestamp) // acceptExpiration
                .attribute("admin", target) // acceptAdmin
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", group) // iqTo
                .attribute("type", "set")
                .content(acceptNode);
        sendNode(iqNode); // WASmaxGroupsAcceptGroupAddRPC.sendAcceptGroupAddRPC
    }

    /**
     * Queries the list of pending join-request applicants for the given
     * group.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code get} addressed to
     * the group JID with a {@code <membership_approval_requests/>} body.
     * The server replies with one
     * {@code <membership_approval_request jid="..."/>} child per
     * pending request; the JIDs are returned in server order.
     *
     * @param group the non-{@code null} target group JID
     * @return the pending applicants, in server order
     * @throws NullPointerException     if {@code group} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote WAWebGroupGetMembershipApprovalRequestsJob
     * .queryAndUpdateGroupMembershipApprovalRequests delegates to
     * {@code WASmaxGroupsGetMembershipApprovalRequestsRPC.sendGetMembershipApprovalRequestsRPC},
     * whose request is built by
     * {@code WASmaxOutGroupsGetMembershipApprovalRequestsRequest.makeGetMembershipApprovalRequestsRequest}
     * ({@code xmlns="w:g2", to=GROUP_JID, type="get"} +
     * {@code <membership_approval_requests/>}). Cobalt emits the same
     * wire shape and returns only the JIDs, skipping the
     * requestor / requestMethod / parentGroup / identity-mixin fields
     * which are not yet surfaced by Cobalt's chat metadata model.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupGetMembershipApprovalRequestsJob",
            exports = "queryAndUpdateGroupMembershipApprovalRequests",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<Jid> queryGroupJoinRequests(Jid group) {
        Objects.requireNonNull(group, "group cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var membershipNode = new NodeBuilder()
                .description("membership_approval_requests") // WASmaxOutGroupsGetMembershipApprovalRequestsRequest: smax("membership_approval_requests", null)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseGetGroupMixin: xmlns: "w:g2"
                .attribute("to", group) // WASmaxOutGroupsBaseGetGroupMixin: to: GROUP_JID(t)
                .attribute("type", "get")
                .content(membershipNode);
        var response = sendNode(iqNode); // WASmaxGroupsGetMembershipApprovalRequestsRPC.sendGetMembershipApprovalRequestsRPC
        var result = new ArrayList<Jid>();
        response.streamChildren() // response "<membership_approval_requests>" wrapper
                .flatMap(wrapper -> wrapper.streamChildren("membership_approval_request"))
                .forEach(requestNode -> {
                    var jid = requestNode.getAttributeAsJid("jid", null); // WAWebGroupGetMembershipApprovalRequestsJob: userJidToUserWid(e.jid)
                    if (jid != null) {
                        result.add(jid);
                    }
                });
        return result;
    }

    /**
     * Approves a pending join request, admitting the applicant into the
     * group.
     *
     * @param group     the non-{@code null} target group JID
     * @param applicant the non-{@code null} applicant JID
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote Delegates to
     * {@link #changeMembershipRequestState(Jid, Jid, String)} with
     * {@code approve}, whose wire shape matches
     * WASmaxOutGroupsMembershipRequestsActionRequest.makeMembershipRequestsActionRequest's
     * {@code <approve>} branch:
     * {@code iq[type=set, xmlns="w:g2", to=GROUP_JID]<membership_requests_action><approve><participant jid=.../></approve></membership_requests_action>}.
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsMembershipRequestsActionRequest",
            exports = "makeMembershipRequestsActionRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void acceptGroupJoinRequest(Jid group, Jid applicant) {
        changeMembershipRequestState(group, applicant, "approve"); // WASmaxOutGroupsMembershipRequestsActionRequest: makeMembershipRequestsActionRequestMembershipRequestsActionApprove
    }

    /**
     * Rejects a pending join request, keeping the applicant out of the
     * group.
     *
     * @param group     the non-{@code null} target group JID
     * @param applicant the non-{@code null} applicant JID
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote Delegates to
     * {@link #changeMembershipRequestState(Jid, Jid, String)} with
     * {@code reject}, whose wire shape matches
     * WASmaxOutGroupsMembershipRequestsActionRequest.makeMembershipRequestsActionRequest's
     * {@code <reject>} branch.
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsMembershipRequestsActionRequest",
            exports = "makeMembershipRequestsActionRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void rejectGroupJoinRequest(Jid group, Jid applicant) {
        changeMembershipRequestState(group, applicant, "reject"); // WASmaxOutGroupsMembershipRequestsActionRequest: makeMembershipRequestsActionRequestMembershipRequestsActionReject
    }

    /**
     * Shared implementation for {@link #acceptGroupJoinRequest(Jid, Jid)}
     * and {@link #rejectGroupJoinRequest(Jid, Jid)} — emits the
     * {@code <membership_requests_action>} IQ with either an
     * {@code <approve>} or a {@code <reject>} child.
     *
     * @param group     the non-{@code null} target group JID
     * @param applicant the non-{@code null} applicant JID
     * @param action    {@code approve} or {@code reject}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     */
    private void changeMembershipRequestState(Jid group, Jid applicant, String action) {
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(applicant, "applicant cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var participantNode = new NodeBuilder()
                .description("participant") // WASmaxOutGroupsMembershipRequestsActionRequest: makeMembershipRequestsActionRequestMembershipRequestsActionApproveParticipant / RejectParticipant
                .attribute("jid", applicant) // smax("participant", {jid: JID(t)})
                .build();
        var actionNode = new NodeBuilder()
                .description(action) // WASmaxOutGroupsMembershipRequestsActionRequest: smax("approve" | "reject", null, REPEATED_CHILD(participant, ...))
                .content(participantNode)
                .build();
        var membershipActionNode = new NodeBuilder()
                .description("membership_requests_action") // WASmaxOutGroupsMembershipRequestsActionRequest: smax("membership_requests_action", null, OPTIONAL_CHILD(approve), OPTIONAL_CHILD(reject))
                .content(actionNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", group) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(t)
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(membershipActionNode);
        sendNode(iqNode); // WASmaxGroupsMembershipRequestsActionRPC.sendMembershipRequestsActionRPC
    }

    /**
     * Sends a peer protocol message to one of the current account's own
     * devices.
     *
     * <p>Peer messages carry app-state sync payloads, key shares, and
     * fatal-exception notifications between a user's linked devices; they
     * never reach any other JID. The destination {@code chatJid} is
     * typically the primary device JID owned by this session.
     *
     * @param chatJid  the destination device JID
     * @param response the fully-populated peer message
     * @return the server ack result
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg: delegates
     * to {@link MessageService#sendPeer(Jid, ChatMessageInfo)}, which owns
     * the Signal-encrypted peer stanza emission.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendAppStateSyncMsgJob", exports = "encryptAndSendKeyMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult sendPeerMessage(Jid chatJid, ChatMessageInfo response) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        Objects.requireNonNull(response, "response cannot be null");
        return messageService.sendPeer(chatJid, response); // WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg
    }

    /**
     * Queries the display name associated with the given user JID.
     *
     * @param receiver the user JID
     * @return the display name, or empty if the server has none
     * @throws UnsupportedOperationException currently a stub pending
     *                                       implementation
     */
    public Optional<String> queryName(Jid receiver) {
        throw new UnsupportedOperationException();
    }

    /**
     * Requests a batch of historical messages for the given newsletter.
     *
     * <p>Sends a {@code newsletter} {@code iq} of type {@code get} addressed
     * to the newsletter JID with a single {@code <messages count="N"/>}
     * child. The server streams back existing newsletter messages which
     * arrive as separate stanzas and are dispatched through the normal
     * receiver path; this method itself only issues the fetch request.
     *
     * @param newsletterJid the non-{@code null} newsletter JID
     * @param count         the number of messages to fetch
     * @throws NullPointerException     if {@code newsletterJid} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code count} is not positive
     *
     * @implNote WA Web issues the batch fetch over the {@code newsletter}
     * IQ namespace on the {@code WASmaxMessageGetNewsletterHistoryRPC}
     * path. Cobalt emits the equivalent wire shape directly because this
     * flow has no dedicated MEX query.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterMessageFetchJob", exports = "queryNewsletterMessages",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void queryNewsletterMessages(Jid newsletterJid, int count) {
        Objects.requireNonNull(newsletterJid, "newsletterJid cannot be null");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        var messagesNode = new NodeBuilder()
                .description("messages")
                .attribute("count", count)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "newsletter")
                .attribute("to", newsletterJid)
                .attribute("type", "get")
                .content(messagesNode);
        sendNode(iqNode);
    }

    /**
     * Queries metadata for a single newsletter with the viewer role the
     * account currently holds.
     *
     * <p>Dispatches the {@link FetchNewsletterMex.Request mexGetNewsletter}
     * MEX query with the newsletter JID serialised as the {@code input}
     * variable and the view flags enabled so the server returns the full
     * thread metadata together with the viewer-scoped settings.
     *
     * @param newsletterJid the non-{@code null} newsletter JID
     * @param role          the viewer role to assert during the query
     * @return the newsletter metadata, or empty if not accessible
     * @throws NullPointerException if {@code newsletterJid} is {@code null}
     *
     * @implNote WAWebNewsletterMetadataJob.getNewsletterMetadata delegates
     * to {@code WAWebNewsletterMetadataQueryJob.queryNewsletterMetadataByJid}
     * which emits {@code mexGetNewsletter}. Cobalt collapses the layering
     * and builds the MEX request directly, returning the local
     * {@link Newsletter} upserted from the response.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterMetadataJob", exports = "getNewsletterMetadata",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Newsletter> queryNewsletter(Jid newsletterJid, NewsletterViewerRole role) {
        Objects.requireNonNull(newsletterJid, "newsletterJid cannot be null");
        var inputJson = new JSONObject()
                .fluentPut("key", newsletterJid.toString()) // WAWebMexFetchNewsletterJob.mexGetNewsletter: input.key
                .fluentPut("type", "JID"); // WAWebMexFetchNewsletterJob.mexGetNewsletter: input.type
        if (role != null) {
            inputJson.fluentPut("view_role", role.name()); // WAWebMexFetchNewsletterJob.mexGetNewsletter: input.view_role
        }
        var request = new FetchNewsletterMex.Request(
                Boolean.TRUE, // fetch_creation_time
                Boolean.TRUE, // fetch_full_image
                Boolean.TRUE, // fetch_viewer_metadata
                Boolean.TRUE, // fetch_wamo_sub
                inputJson.toJSONString()
        );
        var response = sendNode(request.toNode());
        var parsed = FetchNewsletterMex.Response.of(response);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        var newsletter = store.findNewsletterByJid(newsletterJid)
                .orElseGet(() -> store.addNewNewsletter(newsletterJid));
        return Optional.of(newsletter);
    }

    /**
     * Creates a brand-new newsletter owned by this account.
     *
     * <p>Dispatches the
     * {@link CreateNewsletterMex.Request mexCreateNewsletter} MEX mutation
     * with the supplied name, optional description and optional picture
     * bytes. On success the server returns the freshly allocated newsletter
     * id which Cobalt resolves to a local {@link Newsletter} after
     * upserting it into the store.
     *
     * @param name        the non-{@code null} newsletter display name
     * @param description the optional newsletter description
     * @param picture     the optional JPEG-encoded newsletter picture bytes
     * @return the newly created {@link Newsletter}
     * @throws NullPointerException   if {@code name} is {@code null}
     * @throws NoSuchElementException if the server response does not
     *                                contain a newsletter id
     *
     * @implNote WAWebNewsletterCreateAction.createNewsletterAction delegates
     * to {@code WAWebNewsletterCreateJob.createNewsletter} which posts the
     * {@code mexCreateNewsletter} mutation. Cobalt collapses the action +
     * job layers into a direct MEX call and returns the local newsletter
     * model rather than the full hydration pipeline.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterCreateAction", exports = "createNewsletterAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Newsletter createNewsletter(String name, String description, byte[] picture) {
        Objects.requireNonNull(name, "name cannot be null");
        var input = new JSONObject()
                .fluentPut("name", name); // WAWebNewsletterCreateJob.createNewsletter: input.name
        if (description != null) {
            input.fluentPut("description", description); // WAWebNewsletterCreateJob.createNewsletter: input.description
        }
        if (picture != null && picture.length > 0) {
            // WAWebNewsletterCreateJob.encodePicture: base64-encodes the raw JPEG bytes
            input.fluentPut("picture", Base64.getEncoder().encodeToString(picture));
        }
        var request = new CreateNewsletterMex.Request(input.toJSONString());
        var response = sendNode(request.toNode());
        var parsed = CreateNewsletterMex.Response.of(response)
                .orElseThrow(() -> new NoSuchElementException("Missing create-newsletter response: %s".formatted(response)));
        var id = parsed.id()
                .orElseThrow(() -> new NoSuchElementException("Missing newsletter id in response"));
        var newsletterJid = Jid.of(id);
        return store.findNewsletterByJid(newsletterJid)
                .orElseGet(() -> store.addNewNewsletter(newsletterJid));
    }

    /**
     * Edits the mutable metadata (name, description, picture) of a
     * newsletter owned by this account.
     *
     * <p>Dispatches the
     * {@link UpdateNewsletterMex.Request mexUpdateNewsletter} MEX mutation.
     * Fields left {@code null} are omitted from the request so the server
     * leaves them untouched.
     *
     * @param newsletter  the non-{@code null} newsletter JID
     * @param name        the new name, or {@code null} to keep unchanged
     * @param description the new description, or {@code null} to keep
     *                    unchanged
     * @param picture     the new JPEG-encoded picture bytes, or
     *                    {@code null} to keep unchanged
     * @throws NullPointerException if {@code newsletter} is {@code null}
     *
     * @implNote WAWebNewsletterMetadataJob.editNewsletterMetadata delegates
     * to {@code WAWebNewsletterMetadataQueryJob.editNewsletterMetadataQuery}
     * which dispatches {@code mexUpdateNewsletter} with a merged
     * {@code updates} object including the base64-encoded picture. Cobalt
     * constructs the same variables object directly.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterMetadataJob", exports = "editNewsletterMetadata",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editNewsletterMetadata(Jid newsletter, String name, String description, byte[] picture) {
        Objects.requireNonNull(newsletter, "newsletter cannot be null");
        var updates = new JSONObject();
        if (name != null) {
            updates.fluentPut("name", name); // WAWebNewsletterMetadataQueryJob.editNewsletterMetadataQuery: updates.name
        }
        if (description != null) {
            updates.fluentPut("description", description); // WAWebNewsletterMetadataQueryJob.editNewsletterMetadataQuery: updates.description
        }
        if (picture != null) {
            // WAWebNewsletterCreateJob.encodePicture: empty byte[] clears the picture, non-empty replaces it
            updates.fluentPut("picture", picture.length == 0 ? "" : Base64.getEncoder().encodeToString(picture));
        }
        var request = new UpdateNewsletterMex.Request(newsletter.toString(), updates.toJSONString());
        sendNode(request.toNode());
    }

    /**
     * Permanently deletes a newsletter owned by this account.
     *
     * <p>Dispatches the
     * {@link DeleteNewsletterMex.Request mexDeleteNewsletter} MEX mutation
     * and removes the newsletter from the local store on success.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @throws NullPointerException if {@code newsletter} is {@code null}
     *
     * @implNote WAWebNewsletterDeleteAction.deleteNewsletterAction delegates
     * to {@code WAWebNewsletterDeleteJob.deleteNewsletter} which posts
     * {@code mexDeleteNewsletter}. Cobalt collapses action + job into a
     * direct MEX call and evicts the newsletter from the store.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterDeleteAction", exports = "deleteNewsletterAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteNewsletter(Jid newsletter) {
        Objects.requireNonNull(newsletter, "newsletter cannot be null");
        var request = new DeleteNewsletterMex.Request(newsletter.toString());
        sendNode(request.toNode());
        store.removeNewsletter(newsletter); // WAWebNewsletterBridgeApi.deleteNewsletter: evict from local store
    }

    /**
     * Subscribes this account to the given newsletter so future updates
     * are delivered.
     *
     * <p>Dispatches the
     * {@link JoinNewsletterMex.Request mexJoinNewsletter} MEX mutation and
     * registers the newsletter in the local store so subsequent lookups
     * succeed.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @throws NullPointerException if {@code newsletter} is {@code null}
     *
     * @implNote WAWebNewsletterSubscribeAction.subscribeToNewsletterAction
     * delegates to
     * {@code WAWebNewsletterSubscribeJob.subscribeToNewsletter} which
     * emits {@code mexJoinNewsletter}. Cobalt skips the action-layer
     * bookkeeping (QPL markers, similar-channel session logging) and
     * invokes the MEX mutation directly.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSubscribeAction", exports = "subscribeToNewsletterAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void joinNewsletter(Jid newsletter) {
        Objects.requireNonNull(newsletter, "newsletter cannot be null");
        var request = new JoinNewsletterMex.Request(newsletter.toString());
        sendNode(request.toNode());
        store.findNewsletterByJid(newsletter)
                .orElseGet(() -> store.addNewNewsletter(newsletter));
    }

    /**
     * Unsubscribes this account from the given newsletter so updates stop
     * being delivered.
     *
     * <p>Dispatches the
     * {@link LeaveNewsletterMex.Request mexLeaveNewsletter} MEX mutation
     * and removes the newsletter from the local store on success.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @throws NullPointerException if {@code newsletter} is {@code null}
     *
     * @implNote WAWebNewsletterUnsubscribeAction.unsubscribeFromNewsletterAction
     * delegates to
     * {@code WAWebNewsletterUnsubscribeJob.unsubscribeFromNewsletter}
     * which posts {@code mexLeaveNewsletter}. Cobalt skips the QPL markers
     * and the conditional "guest downgrade" branch (driven by
     * {@code deleteLocalModels}) and always evicts the local model.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterUnsubscribeAction", exports = "unsubscribeFromNewsletterAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void leaveNewsletter(Jid newsletter) {
        Objects.requireNonNull(newsletter, "newsletter cannot be null");
        var request = new LeaveNewsletterMex.Request(newsletter.toString());
        sendNode(request.toNode());
        store.removeNewsletter(newsletter);
    }

    /**
     * Toggles the mute state on admin-activity notifications for the given
     * newsletter.
     *
     * <p>Dispatches the
     * {@link UpdateNewsletterUserSettingMex.Request mexUpdateNewsletterUserSetting}
     * MEX mutation with {@code type=MUTE_ADMIN_ACTIVITY} and
     * {@code value=ON} (mute) or {@code value=OFF} (unmute).
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param mute       {@code true} to mute, {@code false} to unmute
     * @throws NullPointerException if {@code newsletter} is {@code null}
     *
     * @implNote WAWebNewsletterUpdateUserSettingsAction.updateNewsletterUserSettingsAction
     * fans out to per-channel toggles via
     * {@code WAWebNewsletterToggleAdminActivityMuteStateAction} which calls
     * {@code WAWebNewsletterUpdateUserSettingJob.updateNewsletterUserSetting}
     * and ultimately {@code mexUpdateNewsletterUserSetting}. Cobalt emits
     * the equivalent MEX mutation directly with the same
     * {@code type}/{@code value} pair the JS path uses for admin activity.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterUpdateUserSettingsAction", exports = "updateNewsletterUserSettingsAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void muteNewsletter(Jid newsletter, boolean mute) {
        Objects.requireNonNull(newsletter, "newsletter cannot be null");
        var input = new JSONObject()
                .fluentPut("newsletter_id", newsletter.toString()) // WAWebNewsletterUpdateUserSettingJob: newsletter_id
                .fluentPut("type", "MUTE_ADMIN_ACTIVITY") // WAWebNewsletterUpdateUserSettingJob: ADMIN_NOTIFICATIONS -> "MUTE_ADMIN_ACTIVITY"
                .fluentPut("value", mute ? "ON" : "OFF"); // WAWebNewsletterUpdateUserSettingJob: MUTED_STATE -> "ON", else "OFF"
        var request = new UpdateNewsletterUserSettingMex.Request(input.toJSONString());
        sendNode(request.toNode());
    }

    /**
     * Posts or updates an emoji reaction on a newsletter message.
     *
     * <p>Sends a SMAX {@code <message server_id="...">} stanza whose body is
     * a {@code <reaction code="emoji"/>} child when {@code emoji} is
     * non-empty, or {@code <reaction_revoke/>} when {@code emoji} is
     * {@code null} or empty. This mirrors the same wire shape emitted by
     * WA Web for newsletter reactions.
     *
     * @param newsletter      the non-{@code null} newsletter JID
     * @param serverMessageId the non-{@code null} target server message id
     * @param emoji           the reaction emoji; {@code null} or empty
     *                        revokes the existing reaction
     * @throws NullPointerException if {@code newsletter} or
     *                              {@code serverMessageId} is {@code null}
     *
     * @implNote WAWebNewsletterSendReactionAction.sendNewsletterReaction
     * routes through {@code WAWebNewsletterSendMessageJob} with a
     * {@link ReactionMessage} target; Cobalt emits the equivalent SMAX
     * {@code <message server_id=...><reaction .../></message>} stanza
     * directly since newsletter reactions are plaintext and carry no
     * encrypted payload.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSendReactionAction", exports = "sendNewsletterReaction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishContentTypeReactionMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void reactToNewsletterMessage(Jid newsletter, String serverMessageId, String emoji) {
        Objects.requireNonNull(newsletter, "newsletter cannot be null");
        Objects.requireNonNull(serverMessageId, "serverMessageId cannot be null");
        // WAWebNewsletterSendMessageQueryJob.c: empty string treated as revoke, same as null
        var reactionNode = (emoji == null || emoji.isEmpty())
                ? new NodeBuilder().description("reaction_revoke").build() // WAWebNewsletterSendMessageQueryJob.c: {isNewsletterReactionRevoke:true}
                : new NodeBuilder().description("reaction").attribute("code", emoji).build(); // WAWebNewsletterSendMessageQueryJob.c: {newsletterReaction:{reactionCode:t}}
        var stanza = new NodeBuilder()
                .description("message")
                .attribute("id", DataUtils.randomHex(10))
                .attribute("to", newsletter)
                .attribute("server_id", serverMessageId)
                .content(reactionNode);
        sendNode(stanza);
    }

    /**
     * Revokes (admin-deletes) a message previously published on a
     * newsletter owned by this account.
     *
     * <p>Sends a SMAX {@code <message edit="3">} stanza carrying an
     * {@code <admin_revoke/>} child, addressed to the newsletter JID with
     * the target {@code server_id} attribute set.
     *
     * @param newsletter      the non-{@code null} newsletter JID
     * @param serverMessageId the non-{@code null} target server message id
     * @throws NullPointerException if {@code newsletter} or
     *                              {@code serverMessageId} is {@code null}
     *
     * @implNote WAWebNewsletterRevokeStatusAction.revokeNewsletterStatusAction
     * delegates to
     * {@code WAWebNewsletterRevokeStatusQueryJob.queryRevokeNewsletterStatus}
     * which emits the
     * {@code WASmaxStatusPublishPostNewsletterStatusRPC.sendPostNewsletterStatusRPC}
     * variant with {@code isStatusNewsletterRevoke=true}. Cobalt emits the
     * equivalent SMAX {@code <message edit="3"><admin_revoke/></message>}
     * stanza directly, matching the newsletter-specific EDIT_ATTR value
     * defined by {@code WAWebAck.EDIT_ATTR.NEWSLETTER_MSG_EDIT}.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterRevokeStatusAction", exports = "revokeNewsletterStatusAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterRevokeMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void revokeNewsletterMessage(Jid newsletter, String serverMessageId) {
        Objects.requireNonNull(newsletter, "newsletter cannot be null");
        Objects.requireNonNull(serverMessageId, "serverMessageId cannot be null");
        var adminRevokeNode = new NodeBuilder()
                .description("admin_revoke") // WASmaxOutMessagePublishNewsletterRevokeMixin: admin_revoke child
                .build();
        var stanza = new NodeBuilder()
                .description("message")
                .attribute("id", DataUtils.randomHex(10))
                .attribute("to", newsletter)
                .attribute("server_id", serverMessageId)
                .attribute("edit", "3") // WAWebAck.EDIT_ATTR.NEWSLETTER_MSG_EDIT
                .content(adminRevokeNode);
        sendNode(stanza);
    }

    /**
     * Accepts a pending newsletter admin invitation addressed to this
     * account.
     *
     * <p>Dispatches the
     * {@link AcceptNewsletterAdminInviteMex.Request acceptNewsletterAdminInvite}
     * MEX mutation.
     *
     * @param newsletter the non-{@code null} newsletter JID whose pending
     *                   admin invite is being accepted
     * @throws NullPointerException if {@code newsletter} is {@code null}
     *
     * @implNote WAWebNewsletterAcceptAdminInviteAction.acceptNewsletterAdminInviteAction
     * delegates to
     * {@code WAWebMexAcceptNewsletterAdminInviteJob.acceptNewsletterAdminInvite}.
     * Cobalt collapses action + job into a direct MEX call.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterAcceptAdminInviteAction", exports = "acceptNewsletterAdminInviteAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void acceptNewsletterAdminInvite(Jid newsletter) {
        Objects.requireNonNull(newsletter, "newsletter cannot be null");
        var request = new AcceptNewsletterAdminInviteMex.Request(newsletter.toString());
        sendNode(request.toNode());
    }

    /**
     * Revokes a pending admin invitation previously issued by this
     * account for the given newsletter.
     *
     * <p>Dispatches the
     * {@link RevokeNewsletterAdminInviteMex.Request revokeNewsletterAdminInvite}
     * MEX mutation.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param admin      the non-{@code null} JID of the user whose pending
     *                   invite is being revoked
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebRevokeNewsletterAdminInviteAction.revokeNewsletterAdminInviteAction
     * delegates to
     * {@code WAWebNewsletterRevokeAdminInviteJob.revokeNewsletterAdminInvite}
     * which emits {@code revokeNewsletterAdminInvite}. Cobalt skips the
     * owner-check and local {@code pendingAdmins} bookkeeping because the
     * server enforces the same authorization on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebRevokeNewsletterAdminInviteAction", exports = "revokeNewsletterAdminInviteAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void revokeNewsletterAdminInvite(Jid newsletter, Jid admin) {
        Objects.requireNonNull(newsletter, "newsletter cannot be null");
        Objects.requireNonNull(admin, "admin cannot be null");
        var request = new RevokeNewsletterAdminInviteMex.Request(newsletter.toString(), admin.toString());
        sendNode(request.toNode());
    }

    /**
     * Demotes an existing newsletter administrator back to a regular
     * follower.
     *
     * <p>Dispatches the
     * {@link DemoteNewsletterAdminMex.Request demoteNewsletterAdmin} MEX
     * mutation.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param admin      the non-{@code null} JID of the admin to demote
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebDemoteNewsletterAdminAction.demoteNewsletterAdminAction
     * delegates to
     * {@code WAWebNewsletterDemoteAdminJob.demoteNewsletterAdmin} which
     * emits {@code demoteNewsletterAdmin}. Cobalt skips the membership
     * precondition checks (owner-only for others, admin-only for self)
     * because the server enforces identical authorization.
     */
    @WhatsAppWebExport(moduleName = "WAWebDemoteNewsletterAdminAction", exports = "demoteNewsletterAdminAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void demoteNewsletterAdmin(Jid newsletter, Jid admin) {
        Objects.requireNonNull(newsletter, "newsletter cannot be null");
        Objects.requireNonNull(admin, "admin cannot be null");
        var request = new DemoteNewsletterAdminMex.Request(newsletter.toString(), admin.toString());
        sendNode(request.toNode());
    }

    /**
     * Updates the reaction policy for a newsletter owned by this account.
     *
     * <p>Dispatches the
     * {@link UpdateNewsletterMex.Request mexUpdateNewsletter} MEX mutation
     * with an {@code updates} object carrying the serialised reaction
     * settings. WA Web treats reaction policy changes as a standard
     * newsletter metadata edit.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param setting    the non-{@code null} reaction policy to install
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebMexUpdateNewsletterJob.mexUpdateNewsletter: encodes the
     * reaction codes through the {@code settings.reaction_codes} leaf of
     * the {@code updates} object. Cobalt emits the same shape, mapping
     * {@link NewsletterReactionSettings.Type} constants to the uppercase
     * wire values WA Web expects.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateNewsletterJob", exports = "mexUpdateNewsletter",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeNewsletterReactionSetting(Jid newsletter, NewsletterReactionSettings setting) {
        Objects.requireNonNull(newsletter, "newsletter cannot be null");
        Objects.requireNonNull(setting, "setting cannot be null");
        var reactionCodes = new JSONObject()
                .fluentPut("value", setting.value().name()); // WAWebMexUpdateNewsletterJob: settings.reaction_codes.value
        if (!setting.blockedCodes().isEmpty()) {
            reactionCodes.fluentPut("blocked_codes", setting.blockedCodes()); // ADAPTED: propagate the BLOCKLIST payload
        }
        var settings = new JSONObject()
                .fluentPut("reaction_codes", reactionCodes);
        var updates = new JSONObject()
                .fluentPut("settings", settings);
        var request = new UpdateNewsletterMex.Request(newsletter.toString(), updates.toJSONString());
        sendNode(request.toNode());
    }

    /**
     * Queries the "about" status text of the given user.
     *
     * @param jid the user JID
     * @return the about text, or empty if the user has none visible
     * @throws UnsupportedOperationException currently a stub pending
     *                                       implementation
     */
    public Optional<String> queryAbout(Jid jid) {
        throw new UnsupportedOperationException();
    }

    /**
     * Requests the business catalog for the given business JID.
     *
     * @param targetJid the business JID
     * @throws UnsupportedOperationException currently a stub pending
     *                                       implementation
     */
    public void queryBusinessCatalog(Jid targetJid) {
        throw new UnsupportedOperationException();
    }

    /**
     * Requests the business collections (catalog sub-groupings) for the
     * given business JID.
     *
     * @param targetJid the business JID
     * @throws UnsupportedOperationException currently a stub pending
     *                                       implementation
     */
    public void queryBusinessCollections(Jid targetJid) {
        throw new UnsupportedOperationException();
    }

    /**
     * Queries the server for the list of contacts currently blocked by
     * this account.
     *
     * @return the blocked JIDs
     * @throws UnsupportedOperationException currently a stub pending
     *                                       implementation
     */
    public SequencedCollection<Jid> queryBlockList() {
        throw new UnsupportedOperationException();
    }

    /**
     * Queries the profile picture URL for the given JID.
     *
     * @param self the JID whose picture should be fetched
     * @return the picture URL, or empty if none is published
     * @throws UnsupportedOperationException currently a stub pending
     *                                       implementation
     */
    public Optional<URI> queryPicture(Jid self) {
        throw new UnsupportedOperationException();
    }

    /**
     * Subscribes to presence updates for the given contact.
     *
     * @param jid the contact JID
     * @throws UnsupportedOperationException currently a stub pending
     *                                       implementation
     */
    public void subscribeToPresence(Jid jid) {
        throw new UnsupportedOperationException();
    }

    /**
     * Sends a fresh message to the given chat JID.
     *
     * <p>The raw {@link MessageContainer} is prepared into a fully
     * populated {@code ChatMessageInfo} (or {@code NewsletterMessageInfo}
     * for newsletter JIDs), encrypted per-device, and dispatched via the
     * appropriate chat / group / status / newsletter sender. The call
     * blocks on the current virtual thread until the server ack arrives.
     *
     * @param jid       the destination chat JID
     * @param container the message payload to send
     * @return the server ack result describing the delivery outcome
     * @throws NullPointerException                               if any argument is {@code null}
     * @throws WhatsAppMessageException.Send.InvalidRecipient     if the JID does not match a supported chat type
     *
     * @implNote WAWebSendMsgJob.encryptAndSendMsg: routes to the
     * user / group / status / newsletter send paths after preparation.
     * Cobalt centralises preparation and dispatch inside
     * {@link MessageService#send(Jid, MessageContainer)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult sendMessage(Jid jid, MessageContainer container) {
        Objects.requireNonNull(jid, "jid cannot be null");
        Objects.requireNonNull(container, "container cannot be null");
        return messageService.send(jid, container); // WAWebSendMsgJob.encryptAndSendMsg
    }

    /**
     * Sends a pre-built {@link MessageInfo} without re-running the
     * {@code MessagePreparer} pipeline.
     *
     * <p>Use this overload when the caller has assembled a
     * {@link ChatMessageInfo} or {@link com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo}
     * with a message id, timestamp, and any extension metadata already
     * populated (for example when rehydrating a draft or re-transmitting
     * a message that failed a previous send).
     *
     * @param messageInfo the fully-populated outgoing message
     * @return the server ack result
     * @throws NullPointerException                               if {@code messageInfo} is {@code null}
     * @throws WhatsAppMessageException.Send.InvalidRecipient     if the JID does not match a supported chat type
     *
     * @implNote WAWebSendMsgJob.encryptAndSendMsg: routes to the
     * user / group / status / newsletter send paths when the caller
     * already owns the prepared {@code MessageInfo}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult sendMessage(MessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo cannot be null");
        return messageService.send(messageInfo); // WAWebSendMsgJob.encryptAndSendMsg
    }

    /**
     * Edits the body of a previously sent message.
     *
     * <p>The original message is addressed by {@code originalKey} and the
     * replacement payload is supplied as a {@link MessageContainer}. The
     * method wraps the replacement in a {@code ProtocolMessage} of type
     * {@link ProtocolMessage.Type#MESSAGE_EDIT}, allocates a new message
     * id for the edit stanza, and dispatches through the standard send
     * pipeline so that every linked device reconciles the change.
     *
     * <p>The original key must carry a {@code parentJid} identifying the
     * chat in which the edit takes place. The window during which a
     * message can be edited is enforced server-side; the server rejects
     * late edits with an {@code EditWindowExpired} error that is surfaced
     * in the returned {@link AckResult}.
     *
     * @param originalKey the key of the message to edit
     * @param newContent  the replacement message container
     * @return the server ack result describing the delivery outcome
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the original key has no
     *                                  {@code parentJid}
     *
     * @implNote WAWebSendMessageEditAction.sendMessageEdit: builds a
     * {@code ProtocolMessage} with {@code type = MESSAGE_EDIT} and
     * {@code editedMessage} carrying the new container, then dispatches
     * it via {@code sendMsgRecord}. Cobalt delegates the record
     * dispatch to {@link MessageService#send(Jid, MessageContainer)} so
     * the preparer populates the message id, message secret, and device
     * list metadata.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMessageEditAction", exports = "sendMessageEdit",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendMessageEditAction", exports = "createEditMsgData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendMessageEditAction", exports = "addAndSendMessageEdit",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult editMessage(MessageKey originalKey, MessageContainer newContent) {
        Objects.requireNonNull(originalKey, "originalKey cannot be null");
        Objects.requireNonNull(newContent, "newContent cannot be null");
        var parentJid = originalKey.parentJid() // WAWebSendMessageEditAction.createEditMsgData: e.id.remote
                .orElseThrow(() -> new IllegalArgumentException("originalKey must carry a parentJid"));
        var protocol = new ProtocolMessageBuilder() // WAWebSendMessageEditAction.createEditMsgData: protocolMessageKey: e.id, editMsgType
                .key(originalKey) // WAWebSendMessageEditAction.createEditMsgData: protocolMessageKey: e.id
                .type(ProtocolMessage.Type.MESSAGE_EDIT) // WAWebSendMessageEditAction.createEditMsgData: subtype: "message_edit"
                .editedMessageContainer(newContent) // WAWebSendMessageEditAction.createEditMsgData: body: t.trim() (container)
                .timestampMs(Instant.now()) // WAWebSendMessageEditAction.createEditMsgData: latestEditSenderTimestampMs: unixTimeMs()
                .build();
        var wrapper = MessageContainer.of(protocol); // WAWebSendMessageEditAction.addAndSendMessageEdit: yield y(o, t, m, r, _)
        return messageService.send(parentJid, wrapper); // WAWebSendMessageEditAction.sendMsgEditRecord -> WAWebSendMsgRecordAction.sendMsgRecord
    }

    /**
     * Revokes a message locally ("delete for me") without instructing the
     * server to remove it from any other device.
     *
     * <p>This method matches {@code WAWebChatSendMessages.sendDeleteMsgs}
     * in WA Web: it deletes the referenced message from the local store
     * and publishes a
     * {@link com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeAction}
     * through the REGULAR_HIGH app-state collection so that every linked
     * device removes the message too.
     *
     * @param key the key of the message to delete
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if the key has no {@code parentJid}
     *                                  or no {@code id}
     *
     * @implNote WAWebChatSendMessages.sendDeleteMsgs: emits a
     * delete-for-me sync action and removes the message from the
     * in-memory store. Cobalt builds the sync mutation manually and
     * routes it through
     * {@link WebAppStateService#pushPatches(SyncPatchType, SequencedCollection)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebChatSendMessages", exports = "sendDeleteMsgs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void revokeMessage(MessageKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        var parentJid = key.parentJid() // WAWebChatSendMessages.sendDeleteMsgs: i.id (chatJid)
                .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
        var messageId = key.id() // WAWebChatSendMessages.sendDeleteMsgs: s = t.list (messageIds)
                .orElseThrow(() -> new IllegalArgumentException("key must carry an id"));

        // WAWebChatSendMessages.sendDeleteMsgs: s.forEach(e => e.delete())
        store.findChatByJid(parentJid)
                .ifPresent(chat -> chat.removeMessage(messageId)); // WAWebChatSendMessages.sendDeleteMsgs: e.delete()

        // WAWebChatSendMessages.sendDeleteMsgs: WAWebChatSendDeleteMsgsBridge.sendDeleteMsgs
        // emits a DeleteMessageForMe sync action to every linked device.
        var indexArgs = new String[] {
                parentJid.toString(), // WAWebSyncdUtils.constructMsgKeySegments: remote
                messageId, // WAWebSyncdUtils.constructMsgKeySegments: id
                key.fromMe() ? "1" : "0", // WAWebSyncdUtils.constructMsgKeySegments: fromMe
                key.senderJid().map(Jid::toString).orElse("0") // WAWebSyncdUtils.constructMsgKeySegments: participant
        };
        var deleteAction = new com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeActionBuilder()
                .messageTimestamp(Instant.now()) // WAWebDeleteMessageForMeSync: deleteMessageForMeAction.messageTimestamp
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(Instant.now())
                .deleteMessageForMeAction(deleteAction)
                .build();
        var indexJson = JSON.toJSONString(java.util.List.of(
                com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeAction.ACTION_NAME, // "deleteMessageForMe"
                indexArgs[0], indexArgs[1], indexArgs[2], indexArgs[3]
        ));
        var mutation = new DecryptedMutation.Trusted(
                indexJson,
                value,
                SyncdOperation.SET,
                Instant.now(),
                com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeAction.ACTION_VERSION
        );
        webAppStateService.pushPatches(
                com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeAction.COLLECTION_NAME,
                java.util.List.of(new SyncPendingMutation(mutation, 0))
        ); // WAWebChatSendMessages.sendDeleteMsgs: WAWebChatSendDeleteMsgsBridge
    }

    /**
     * Revokes a message for everyone in the chat ("delete for everyone").
     *
     * <p>The method constructs a {@code ProtocolMessage} of type
     * {@link ProtocolMessage.Type#REVOKE} carrying the original
     * {@code MessageKey} and dispatches it through the standard send
     * pipeline so that every participant sees the message disappear.
     *
     * <p>The caller is responsible for ensuring they have permission to
     * revoke the target message. The server rejects unauthorised revokes
     * and the failure is surfaced in the returned {@link AckResult}.
     *
     * @param key the key of the message to revoke
     * @return the server ack result describing the delivery outcome
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if the key has no {@code parentJid}
     *
     * @implNote WAWebRevokeMsgAction.sendRevoke, WAWebRevokeMsgAction.revoke:
     * wraps the original key inside a {@code ProtocolMessage} with
     * {@code type = REVOKE} and sends it via
     * {@code WAWebSendMsgRecordAction.sendMsgRecord}. Cobalt delegates
     * the record dispatch to {@link MessageService#send(Jid, MessageContainer)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebRevokeMsgAction", exports = "sendRevoke",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebRevokeMsgAction", exports = "revoke",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatSendMessages", exports = "sendRevokeMsgs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult revokeMessageForEveryone(MessageKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        var parentJid = key.parentJid() // WAWebRevokeMsgAction._sendRevoke: s.id.remote
                .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
        var protocol = new ProtocolMessageBuilder() // WAWebRevokeMsgAction._sendRevoke: {type: PROTOCOL, kind: ProtocolRevoke, protocolMessageKey: s.id}
                .key(key) // WAWebRevokeMsgAction._sendRevoke: protocolMessageKey: s.id
                .type(ProtocolMessage.Type.REVOKE) // WAWebRevokeMsgAction._sendRevoke: MsgKind.ProtocolRevoke, subtype: sender_revoke | admin_revoke
                .timestampMs(Instant.now()) // WAWebRevokeMsgAction._sendRevoke: revokeTimestamp: C
                .build();
        var wrapper = MessageContainer.of(protocol); // WAWebRevokeMsgAction._sendRevoke -> WAWebSendMsgRecordAction.sendMsgRecord
        return messageService.send(parentJid, wrapper); // WAWebRevokeMsgAction._sendRevoke -> sendMsgRecord
    }

    /**
     * Forwards a single message to a single destination chat.
     *
     * <p>Equivalent to calling
     * {@link #forwardMessages(Collection, Collection)} with singleton
     * collections for both arguments.
     *
     * @param sourceKey   the key of the message to forward
     * @param destination the destination chat JID
     * @return the server ack result for the single forwarded message
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the source message cannot be
     *                                  resolved in the local store
     *
     * @implNote WAWebChatForwardMessage.forwardMessages: WA Web iterates
     * across chats; Cobalt collapses the single-destination case to a
     * direct send for caller convenience.
     */
    @WhatsAppWebExport(moduleName = "WAWebChatForwardMessage", exports = "forwardMessages",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult forwardMessage(MessageKey sourceKey, Jid destination) {
        Objects.requireNonNull(sourceKey, "sourceKey cannot be null");
        Objects.requireNonNull(destination, "destination cannot be null");
        var parentJid = sourceKey.parentJid() // WAWebChatForwardMessage.getForwardedMessageFields: e.id.remote
                .orElseThrow(() -> new IllegalArgumentException("sourceKey must carry a parentJid"));
        var messageId = sourceKey.id() // WAWebChatForwardMessage.getForwardedMessageFields: e.id
                .orElseThrow(() -> new IllegalArgumentException("sourceKey must carry an id"));
        var source = store.findMessageById(parentJid, messageId) // WAWebMsgCollection.MsgCollection.get(e.id)
                .orElseThrow(() -> new IllegalArgumentException("Source message not found in local store: " + messageId));
        // WAWebChatForwardMessage.getForwardedMessageFields: strip local-only fields and forward the message body
        var container = source.message(); // WAWebChatForwardMessage.forwardMessages: sendTextMsgToChatAction / sendMediaMsgToChatAction with forwarded container
        return messageService.send(destination, container); // WAWebChatForwardMessage.forwardMessages -> sendMsgRecord
    }

    /**
     * Forwards a set of messages to every destination chat.
     *
     * <p>The method resolves each source key against the local store,
     * extracts the underlying {@link MessageContainer}, and sends it to
     * every destination in turn. WA Web performs the fan-out as a single
     * batched promise; Cobalt serialises per destination per message
     * because virtual-thread blocking sends are cheap and keep the call
     * sequencing deterministic.
     *
     * <p>Unresolvable source keys and unsendable destinations are
     * skipped silently, mirroring WA Web's {@code canForward} /
     * {@code canSend} filters.
     *
     * @param sourceKeys   the keys of the messages to forward
     * @param destinations the destination chat JIDs
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebForwardMessagesToChat.forwardMessagesToChats:
     * fan-outs every source to every destination; Cobalt mirrors the
     * cartesian product but serialises each leg through the normal send
     * pipeline.
     */
    @WhatsAppWebExport(moduleName = "WAWebForwardMessagesToChat", exports = "forwardMessagesToChats",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatForwardMessage", exports = "forwardMessages",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatForwardMessage", exports = "getForwardedMessageFields",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void forwardMessages(Collection<MessageKey> sourceKeys, Collection<Jid> destinations) {
        Objects.requireNonNull(sourceKeys, "sourceKeys cannot be null");
        Objects.requireNonNull(destinations, "destinations cannot be null");
        // WAWebForwardMessagesToChat.forwardMessagesToChats: u.map(e => canForward(e))
        var containers = new ArrayList<MessageContainer>();
        for (var sourceKey : sourceKeys) {
            sourceKey.parentJid() // WAWebChatForwardMessage.getForwardedMessageFields: e.id.remote
                    .flatMap(parent -> sourceKey.id()
                            .flatMap(id -> store.findMessageById(parent, id)))
                    .map(MessageInfo::message) // WAWebChatForwardMessage.getForwardedMessageFields: omit local-only fields
                    .ifPresent(containers::add);
        }
        // WAWebForwardMessagesToChat.forwardMessagesToChats: i.filter(e => e.canSend).map(chat => forwardMessages)
        for (var destination : destinations) {
            for (var container : containers) {
                messageService.send(destination, container); // WAWebChatForwardMessage.forwardMessages -> sendMsgRecord
            }
        }
    }

    /**
     * Adds or replaces the current account's reaction on a given
     * message.
     *
     * <p>The method builds a {@link ReactionMessage} whose
     * {@code text} field is the new emoji and whose {@code key} points to
     * the target message; the resulting container is dispatched through
     * the standard send pipeline. The preparer takes care of converting
     * the reaction into an {@code EncReactionMessage} automatically when
     * the target chat is a CAG community subgroup.
     *
     * <p>Sending an empty emoji deletes the account's previous reaction
     * (see {@link #removeReaction(MessageKey)}).
     *
     * @param messageKey the key of the message being reacted to
     * @param emoji      the reaction emoji; empty string removes the
     *                   existing reaction
     * @return the server ack result for the reaction stanza
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the target key has no
     *                                  {@code parentJid}
     *
     * @implNote WAWebReactionsMsgAction.addOrUpdateReactions: the
     * post-send bookkeeping step that refreshes the local reactions
     * collection. The WA Web send itself runs through
     * {@code sendMsgRecord}; Cobalt routes through the normal message
     * service which handles both the reaction stanza emission and the
     * local reaction tracking via {@code AbstractWhatsAppStore}.
     */
    @WhatsAppWebExport(moduleName = "WAWebReactionsMsgAction", exports = "addOrUpdateReactions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebReactionsMsgAction", exports = "resendUpdateFailedPropsForSentReactionsDBAndModel",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult addReaction(MessageKey messageKey, String emoji) {
        Objects.requireNonNull(messageKey, "messageKey cannot be null");
        Objects.requireNonNull(emoji, "emoji cannot be null");
        var parentJid = messageKey.parentJid() // WAWebReactionsMsgAction.addOrUpdateReactions: e.parentMsgKey.remote
                .orElseThrow(() -> new IllegalArgumentException("messageKey must carry a parentJid"));
        var reaction = new ReactionMessageBuilder() // WAWebReactionsMsgAction.addOrUpdateReactions: reactionText: p
                .key(messageKey) // WAWebReactionsMsgAction.addOrUpdateReactions: parentMsgKey: r
                .text(emoji) // WAWebReactionsMsgAction.addOrUpdateReactions: reactionText
                .senderTimestampMs(Instant.now()) // WAWebReactionsMsgAction.addOrUpdateReactions: timestamp: i.timestamp
                .build();
        // Record the locally sent reaction so the store reflects the
        // in-flight state until the server ack lands. The preparer auto
        // converts to EncReactionMessage for CAG groups.
        store.trackSentReaction(messageKey, emoji); // WAWebReactionsMsgAction.addOrUpdateReactionsModelCollection: ReactionsCollection.addOrUpdateReaction
        return messageService.send(parentJid, MessageContainer.of(reaction)); // WAWebReactionsMsgAction -> sendMsgRecord
    }

    /**
     * Removes the current account's reaction from a given message.
     *
     * <p>Equivalent to calling {@link #addReaction(MessageKey, String)}
     * with an empty emoji string: WA Web treats an empty
     * {@code reactionText} as the signal to withdraw the sender's
     * previous reaction.
     *
     * @param messageKey the key of the message whose reaction should
     *                   be removed
     * @return the server ack result for the reaction stanza
     * @throws NullPointerException     if {@code messageKey} is {@code null}
     * @throws IllegalArgumentException if the target key has no
     *                                  {@code parentJid}
     *
     * @implNote WAWebReactionsBEUtils.REVOKED_REACTION_TEXT: empty-string
     * marker used to signal reaction removal. Matches
     * {@link #addReaction(MessageKey, String)} with an empty emoji.
     */
    @WhatsAppWebExport(moduleName = "WAWebReactionsMsgAction", exports = "addOrUpdateReactions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult removeReaction(MessageKey messageKey) {
        Objects.requireNonNull(messageKey, "messageKey cannot be null");
        return addReaction(messageKey, ""); // WAWebReactionsBEUtils.REVOKED_REACTION_TEXT: ""
    }

    /**
     * Stars (bookmarks) a message so it appears in the account's starred
     * messages list.
     *
     * <p>The change is propagated to every linked device via the
     * REGULAR_HIGH app-state sync collection. The target message must
     * already exist in the local store; orphan keys are rejected by the
     * remote-side handler on receiving devices.
     *
     * @param key the key of the message to star
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if the key has no {@code parentJid}
     *                                  or no {@code id}
     *
     * @implNote WAWebStarMessageSync.getStarMessageMutations: builds a
     * REGULAR_HIGH sync mutation with {@code starred = true} and the
     * message-key segments as the index. Also flips the local
     * {@link ChatMessageInfo#setStarred(Boolean)} eagerly so the caller
     * observes the bookmarked state immediately.
     */
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "getStarMessageMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void starMessage(MessageKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        pushStarMutation(key, true); // WAWebStarMessageSync.getStarMessageMutations: starAction.starred = true
    }

    /**
     * Unstars a previously starred message.
     *
     * <p>Counterpart to {@link #starMessage(MessageKey)}: emits a
     * REGULAR_HIGH sync mutation with {@code starred = false} and flips
     * the local star flag back off.
     *
     * @param key the key of the message to unstar
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if the key has no {@code parentJid}
     *                                  or no {@code id}
     *
     * @implNote WAWebStarMessageSync.getStarMessageMutations: builds a
     * REGULAR_HIGH sync mutation with {@code starred = false}.
     */
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "getStarMessageMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unstarMessage(MessageKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        pushStarMutation(key, false); // WAWebStarMessageSync.getStarMessageMutations: starAction.starred = false
    }

    /**
     * Builds and dispatches the {@link StarAction} sync mutation for the
     * target message.
     *
     * <p>The index follows WA Web's
     * {@code WAWebSyncdUtils.constructMsgKeySegmentsFromMsgKey} shape:
     * {@code ["star", remote, id, fromMe, participant]} where
     * {@code fromMe} is {@code "1"} / {@code "0"} and
     * {@code participant} is the sender JID for non-self group messages
     * and {@code "0"} otherwise. The mutation is routed through
     * {@link WebAppStateService#pushPatches(SyncPatchType, SequencedCollection)}
     * so that every linked device receives the change.
     *
     * <p>In addition to the sync mutation, the local message's star
     * flag is flipped eagerly so the caller observes the bookmarked
     * state immediately, without waiting for the sync patch to round
     * trip through the server.
     *
     * @param key     the message key to star / unstar
     * @param starred whether the message should be starred
     * @throws IllegalArgumentException if the key has no {@code parentJid}
     *                                  or no {@code id}
     *
     * @implNote WAWebStarMessageSync.getStarMessageMutations: wraps the
     * index and value into a SET mutation tagged with
     * {@code version = 2}; Cobalt delegates to
     * {@link WebAppStateService#pushPatches(SyncPatchType, SequencedCollection)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "getStarMessageMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void pushStarMutation(MessageKey key, boolean starred) {
        var parentJid = key.parentJid() // WAWebStarMessageSync.getStarMessageMutations: remote
                .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
        var messageId = key.id() // WAWebStarMessageSync.getStarMessageMutations: id
                .orElseThrow(() -> new IllegalArgumentException("key must carry an id"));

        // WAWebSyncdUtils.constructMsgKeySegmentsFromMsgKey
        var fromMe = key.fromMe();
        var participant = key.senderJid().orElse(null);
        var participantSegment = (participant != null && !parentJid.hasUserServer() && !fromMe)
                ? participant.toString()
                : "0"; // WAWebSyncdUtils.extractParticipantForSync

        var action = new StarActionBuilder() // WAWebStarMessageSync.getStarMessageMutations: {starAction: {starred: a}}
                .starred(starred)
                .build();
        var value = new SyncActionValueBuilder() // WAWebSyncdActionUtils.buildPendingMutation: encodeProtobuf(SyncActionValueSpec, ...)
                .timestamp(Instant.now()) // WAWebSyncdActionUtils.buildPendingMutation: timestamp: e
                .starAction(action)
                .build();
        var indexJson = JSON.toJSONString(java.util.List.of(
                StarAction.ACTION_NAME, // WAWebStarMessageSync.getAction: "star"
                parentJid.toString(), // WAWebSyncdUtils.constructMsgKeySegments: remote.toString({legacy: true})
                messageId, // WAWebSyncdUtils.constructMsgKeySegments: id
                fromMe ? "1" : "0", // WAWebSyncdUtils.constructMsgKeySegments: fromMe
                participantSegment // WAWebSyncdUtils.extractParticipantForSync
        ));
        var mutation = new DecryptedMutation.Trusted(
                indexJson,
                value,
                SyncdOperation.SET, // WAWebStarMessageSync.getStarMessageMutations: operation: SyncdMutation$SyncdOperation.SET
                Instant.now(),
                StarAction.ACTION_VERSION // WAWebStarMessageSync.getVersion: 2
        );
        webAppStateService.pushPatches(
                StarAction.COLLECTION_NAME, // WAWebStarMessageSync.collectionName: RegularHigh
                java.util.List.of(new SyncPendingMutation(mutation, 0))
        );

        // WAWebStarMessageSync.applyMutations: k.star = p (flip local flag eagerly)
        store.findMessageById(parentJid, messageId).ifPresent(info -> {
            switch (info) {
                case ChatMessageInfo c -> c.setStarred(starred);
                case com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo n -> n.setStarred(starred);
                default -> { /* no-op for unsupported info types */ }
            }
        });
    }

    //<editor-fold desc="Chat operations">

    /**
     * Builds a minimal outgoing {@link SyncActionMessageRange} for the given
     * chat covering the chat's most recent message.
     *
     * <p>WhatsApp Web's {@code WAWebMessageRangeUtils.constructMessageRange}
     * walks the chat's local message history and stamps the last regular and
     * system message timestamps plus the individual message keys. Cobalt's
     * message-range infrastructure does not yet maintain the same
     * browser-specific indices, so this helper produces the best approximation
     * possible from the in-memory {@link Chat}: it records the newest message
     * timestamp as {@code lastMessageTimestamp} and leaves the message list
     * empty. If the chat has no messages the builder returns {@code null} so
     * the caller can decide whether to emit the mutation with an absent
     * range.
     *
     * @implNote ADAPTED: WAWebMessageRangeUtils.constructMessageRange — Cobalt
     *           does not persist per-message timestamps for range
     *           construction, so the range is approximated from the chat's
     *           {@link Chat#newestMessage()} timestamp
     * @param chat the chat whose range is being built
     * @return the built message range, or {@code null} when the chat has no
     *         messages
     */
    private SyncActionMessageRange buildOutgoingMessageRange(Chat chat) {
        var newest = chat.newestMessage().orElse(null); // WAWebMessageRangeUtils.constructMessageRange: msgs iteration
        if (newest == null) { // ADAPTED: no messages -> no meaningful range
            return null;
        }
        var timestamp = newest.timestamp().orElse(null); // WAWebMessageRangeUtils.constructMessageRange: msg.t
        var builder = new SyncActionMessageRangeBuilder(); // WAWebMessageRangeUtils.constructMessageRange: {lastMessageTimestamp: ..., lastSystemMessageTimestamp: ..., messages: [...]}
        if (timestamp != null) { // WAWebMessageRangeUtils.constructMessageRange: lastMessageTimestamp = max(regularTs)
            builder.lastMessageTimestamp(timestamp);
        }
        return builder.build();
    }

    /**
     * Archives or unarchives a chat, propagating the change to every linked
     * device via the {@code REGULAR_LOW} app-state sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebSetArchiveChatAction.setArchive}: resolves
     * the target chat, builds an archive mutation via
     * {@link ArchiveChatHandler#getMutationsForArchive(Instant, boolean, Jid, SyncActionMessageRange)}
     * (which additionally queues an unpin mutation when archiving), pushes
     * the mutations via {@link #pushWebAppState(SyncPatchType, List)}, and
     * flips the local {@link Chat#setArchived(Boolean)} flag eagerly so
     * callers observe the change without waiting for the sync round-trip.
     *
     * @param chat    the JID of the chat to archive or unarchive
     * @param archive {@code true} to archive, {@code false} to unarchive
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebSetArchiveChatAction.setArchive: fetches the chat,
     * invokes {@code sendConversationArchive} which builds
     * {@code getMutationsForArchive} and commits them via
     * {@code lockForMessageRangeSync}; Cobalt delegates to
     * {@link WebAppStateService#pushPatches} and applies the archive state
     * change on the local {@link Chat} eagerly.
     */
    @WhatsAppWebExport(moduleName = "WAWebSetArchiveChatAction", exports = "setArchive",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void archiveChat(Jid chat, boolean archive) {
        Objects.requireNonNull(chat, "chat cannot be null");
        var timestamp = Instant.now(); // WAWebSetArchiveChatAction.setArchive -> WAWebChatArchiveBridge.sendConversationArchive: var l = unixTimeMs()
        var chatModel = store.findChatByJid(chat).orElse(null); // WAWebSetArchiveChatAction.setArchive: unproxy(e); t.archive lookup
        var messageRange = chatModel != null ? buildOutgoingMessageRange(chatModel) : null; // WAWebArchiveChatSync.getArchiveChatMutation: messageRange: constructMessageRange
        var mutations = ArchiveChatHandler.INSTANCE.getMutationsForArchive(timestamp, archive, chat, messageRange); // WAWebArchiveChatSync.getMutationsForArchive
        webAppStateService.pushPatches(com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatAction.COLLECTION_NAME, mutations); // WAWebChatArchiveBridge.sendConversationArchive: lockForMessageRangeSync(["chat"], mutations, ...)
        if (chatModel != null) { // WAWebChatArchiveBridge.sendConversationArchive: merge(t.toString(), {archive: a, pin: void 0 if archive})
            chatModel.setArchived(archive); // WAWebSetArchiveChatAction.setArchive: t.archive = a
            if (archive) { // WAWebSetArchiveChatAction.setArchive: a && (t.pin = void 0)
                chatModel.setPinnedTimestamp(null); // WAWebSetArchiveChatAction.setArchive: t.pin = void 0
            }
        }
    }

    /**
     * Pins or unpins a chat, propagating the change to every linked device
     * via the {@code REGULAR_LOW} app-state sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebSetPinChatAction.setPin}: resolves the
     * chat, checks the local pin limit (3 for chats, 2 for newsletters),
     * builds a pin mutation via
     * {@link PinChatHandler#getPinMutation(Instant, boolean, Jid)}, pushes it
     * via {@link #pushWebAppState(SyncPatchType, List)}, and flips the local
     * {@link Chat#setPinnedTimestamp(Instant)} eagerly. When pinning,
     * {@link Chat#setArchived(Boolean)} is forced to {@code false} to match
     * WA Web's sticky-state invariant.
     *
     * @param chat the JID of the chat to pin or unpin
     * @param pin  {@code true} to pin the chat, {@code false} to unpin
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebSetPinChatAction.setPin: fetches the chat, invokes
     * {@code WAWebChatPinBridge.setPin} which builds
     * {@code getMutationsForPin} and commits them via
     * {@code lockForSync}; Cobalt delegates to
     * {@link WebAppStateService#pushPatches} and applies the pin timestamp
     * on the local {@link Chat} eagerly. WAM telemetry is intentionally
     * omitted.
     */
    @WhatsAppWebExport(moduleName = "WAWebSetPinChatAction", exports = "setPin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void pinChat(Jid chat, boolean pin) {
        Objects.requireNonNull(chat, "chat cannot be null");
        var timestamp = Instant.now(); // WAWebSetPinChatAction.setPin: var _ = unixTimeMs()
        var mutations = PinChatHandler.INSTANCE.getMutationsForPin(timestamp, pin, chat); // WAWebPinChatSync.getMutationsForPin
        webAppStateService.pushPatches(PinChatHandler.INSTANCE.collectionName(), mutations); // WAWebChatPinBridge.setPin: lockForSync(["chat"], i, ...)
        store.findChatByJid(chat).ifPresent(chatModel -> { // WAWebSetPinChatAction.setPin: t.pin = u
            chatModel.setPinnedTimestamp(pin ? timestamp : null); // WAWebSetPinChatAction.setPin: u = a ? _ : 0
            if (pin) { // WAWebPinChatSync.applyMutation: if (pinned) o.archive = false
                chatModel.setArchived(false);
            }
        });
    }

    /**
     * Mutes a chat until the given instant, propagating the change to every
     * linked device via the {@code REGULAR_HIGH} app-state sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebMuteChatSync.generateMuteMutation}:
     * converts the supplied {@link Instant} to epoch seconds, builds a mute
     * mutation via
     * {@link MuteChatHandler#generateMuteMutation(WhatsAppClient, Jid, long, Long)},
     * pushes it via {@link #pushWebAppState(SyncPatchType, List)}, and
     * applies the mute state on the local {@link Chat} eagerly so callers
     * observe the change immediately.
     *
     * <p>{@code muteUntil} can be {@code null} to unmute the chat (the helper
     * emits {@code muteEndSeconds = 0}). An {@code Instant} with an epoch
     * second of {@code -1} is treated as "muted indefinitely" per WA Web's
     * sentinel convention.
     *
     * @param chat      the JID of the chat to mute
     * @param muteUntil the instant at which the mute should expire, or
     *                  {@code null} to unmute
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebMuteChatSync.generateMuteMutation
     */
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void muteChat(Jid chat, Instant muteUntil) {
        Objects.requireNonNull(chat, "chat cannot be null");
        var muteEndSeconds = muteUntil == null ? 0L : muteUntil.getEpochSecond(); // WAWebMuteChatSync.generateMuteMutation: var t = muteEndSeconds (0 for unmute, -1 for indefinite)
        var mutation = MuteChatHandler.INSTANCE.generateMuteMutation(this, chat, muteEndSeconds, null); // WAWebMuteChatSync.generateMuteMutation
        webAppStateService.pushPatches(MuteChatHandler.INSTANCE.collectionName(), List.of(mutation)); // WAWebSyncdActionUtils.buildPendingMutation -> lockForSync
        store.findChatByJid(chat).ifPresent(chatModel -> // WAWebMuteChatSync.applyMutations: C.muteExpiration = g (apply locally for eager consistency)
                chatModel.setMute(com.github.auties00.cobalt.model.chat.ChatMute.mutedUntil(muteEndSeconds)));
    }

    /**
     * Unmutes a chat.
     *
     * <p>Convenience wrapper around {@link #muteChat(Jid, Instant)} with a
     * {@code null} {@code muteUntil}, which emits a sync mutation with
     * {@code muted = false} and {@code muteEndTimestamp = 0}.
     *
     * @param chat the JID of the chat to unmute
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebMuteChatSync.generateMuteMutation with {@code muteEndSeconds == 0}
     */
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unmuteChat(Jid chat) {
        muteChat(chat, null); // WAWebMuteChatSync.generateMuteMutation: muted = false, muteEndTimestamp = 0
    }

    /**
     * Marks a chat as read, propagating the change to every linked device via
     * the {@code REGULAR_LOW} app-state sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebUpdateUnreadChatAction.sendSeen} ->
     * {@code WAWebChatSeenBridge.sendConversationSeen}: builds a
     * mark-chat-as-read mutation via
     * {@link MarkChatAsReadHandler#getMarkChatAsReadMutation(Instant, boolean, Jid, SyncActionMessageRange)}
     * with {@code read = true}, pushes it via
     * {@link #pushWebAppState(SyncPatchType, List)}, and clears the local
     * {@link Chat#setMarkedAsUnread(Boolean)} and
     * {@link Chat#setUnreadCount(Integer)} flags eagerly.
     *
     * @param chat the JID of the chat to mark as read
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebUpdateUnreadChatAction.sendSeen,
     * WAWebChatSeenBridge.sendConversationSeen: invokes
     * {@code WAWebMarkChatAsReadSync.getMarkChatAsReadMutation} with
     * {@code read = true}. Cobalt delegates to
     * {@link WebAppStateService#pushPatches} and applies the read-state
     * change on the local {@link Chat} eagerly.
     */
    @WhatsAppWebExport(moduleName = "WAWebUpdateUnreadChatAction", exports = "sendSeen",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void markChatAsRead(Jid chat) {
        Objects.requireNonNull(chat, "chat cannot be null");
        pushMarkChatAsReadMutation(chat, true); // WAWebChatSeenBridge.sendConversationSeen: getMarkChatAsReadMutation(i, true, e.id)
    }

    /**
     * Marks a chat as unread, propagating the change to every linked device
     * via the {@code REGULAR_LOW} app-state sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebUpdateUnreadChatAction.markUnread} ->
     * {@code WAWebChatSeenBridge.sendConversationUnseen}: builds a
     * mark-chat-as-read mutation with {@code read = false}, pushes it via
     * {@link #pushWebAppState(SyncPatchType, List)}, and sets the local
     * {@link Chat#setMarkedAsUnread(Boolean)} flag eagerly along with
     * {@code unreadCount = -1} per
     * {@code WAWebConstantsDeprecated.MARKED_AS_UNREAD}.
     *
     * @param chat the JID of the chat to mark as unread
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebUpdateUnreadChatAction.markUnread,
     * WAWebChatSeenBridge.sendConversationUnseen: invokes
     * {@code getMarkChatAsReadMutation} with {@code read = false}. Cobalt
     * delegates to {@link WebAppStateService#pushPatches} and applies the
     * unread state on the local {@link Chat} eagerly.
     */
    @WhatsAppWebExport(moduleName = "WAWebUpdateUnreadChatAction", exports = "markUnread",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void markChatAsUnread(Jid chat) {
        Objects.requireNonNull(chat, "chat cannot be null");
        pushMarkChatAsReadMutation(chat, false); // WAWebChatSeenBridge.sendConversationUnseen: getMarkChatAsReadMutation(n, false, e)
    }

    /**
     * Builds and dispatches the read-state mutation for the target chat.
     *
     * @param chat the chat JID
     * @param read {@code true} for mark as read, {@code false} for mark as
     *             unread
     */
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = "getMarkChatAsReadMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void pushMarkChatAsReadMutation(Jid chat, boolean read) {
        var timestamp = Instant.now(); // WAWebChatSeenBridge.sendConversationSeen / sendConversationUnseen: var i = unixTimeMs()
        var chatModel = store.findChatByJid(chat).orElse(null); // WAWebMarkChatAsReadSync: chat resolution for message-range construction
        var messageRange = chatModel != null ? buildOutgoingMessageRange(chatModel) : null; // WAWebMarkChatAsReadSync.getMarkChatAsReadMutation: constructMessageRange
        var mutation = MarkChatAsReadHandler.INSTANCE.getMarkChatAsReadMutation(timestamp, read, chat, messageRange); // WAWebMarkChatAsReadSync.getMarkChatAsReadMutation
        webAppStateService.pushPatches(MarkChatAsReadHandler.INSTANCE.collectionName(), List.of(mutation)); // WAWebChatSeenBridge.sendConversationSeen: lockForMessageRangeSync([], [l], ...)
        if (chatModel != null) { // WAWebMarkChatAsReadSync.$MarkChatAsReadSync$p_1: backend updateChatReadStatus
            if (read) { // WAWebMarkChatAsReadSync.applyMutations: t === true branch
                chatModel.setMarkedAsUnread(false); // WAWebChatSeenBridge.markConversationSeen: {unreadCount: n, unreadDividerOffset: 0}
                chatModel.setUnreadCount(0); // WAWebChatSeenBridge.markConversationSeen: unreadCount: n (n=0 when fully read)
            } else { // WAWebMarkChatAsReadSync.applyMutations: t === false branch
                chatModel.setMarkedAsUnread(true); // WAWebUpdateUnreadChatAction.markUnread: t.markedUnread = !0
                chatModel.setUnreadCount(-1); // WAWebConstantsDeprecated.MARKED_AS_UNREAD = -1
            }
        }
    }

    /**
     * Clears all messages from a chat while keeping the chat itself,
     * propagating the change to every linked device via the
     * {@code REGULAR_HIGH} app-state sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebClearChatSync.getClearChatMutation} ->
     * {@code WAWebClearChatPopup.react} invocation path: builds a clear-chat
     * mutation via
     * {@link ClearChatHandler#getClearChatMutation(Instant, Jid, boolean, boolean, SyncActionMessageRange)}
     * and pushes it via {@link #pushWebAppState(SyncPatchType, List)}. The
     * local {@link Chat#removeMessages()} call is applied eagerly so callers
     * observe the chat emptied without waiting for the sync round-trip.
     *
     * @param chat        the JID of the chat to clear
     * @param keepStarred whether starred messages should be preserved
     *                    ({@code true}) or deleted with the rest
     *                    ({@code false})
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebClearChatSync.getClearChatMutation (sync mutation build
     * and dispatch). The media-delete flag defaults to {@code false} (keep
     * media) per the common WA Web flow; callers that need to delete media
     * should invoke the handler directly with {@code deleteMedia=true}.
     */
    @WhatsAppWebExport(moduleName = "WAWebClearChatSync", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void clearChat(Jid chat, boolean keepStarred) {
        Objects.requireNonNull(chat, "chat cannot be null");
        var timestamp = Instant.now(); // WAWebClearChatSync.getClearChatMutation: timestamp: e
        var chatModel = store.findChatByJid(chat).orElse(null); // WAWebSyncdGetChat.resolveChatForMutationIndex (at apply time)
        var messageRange = chatModel != null ? buildOutgoingMessageRange(chatModel) : null; // WAWebClearChatSync.getClearChatMutation: constructForwardMovingMessageRange
        // WAWebClearChatSync.$ClearChatSync$p_3: [t.toJid(), n?"1":"0", r?"1":"0"] — n = deleteStarred (inverse of keepStarred), r = deleteMedia
        var mutation = ClearChatHandler.INSTANCE.getClearChatMutation(timestamp, chat, !keepStarred, false, messageRange); // WAWebClearChatSync.getClearChatMutation
        webAppStateService.pushPatches(ClearChatHandler.INSTANCE.collectionName(), List.of(mutation)); // WAWebSyncdCoreApi.lockForSync -> push
        if (chatModel != null) { // WAWebClearChatSync.clearChat: queryAndRemoveMessagesInMessageRange
            chatModel.removeMessages(); // ADAPTED: WAWebClearChatSync.clearChat — Cobalt drops all messages since per-message range deletion is not yet supported
        }
    }

    /**
     * Deletes a chat entirely, propagating the change to every linked device
     * via the {@code REGULAR_HIGH} app-state sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebDeleteChatSync.getDeleteChatMutation} ->
     * {@code WAWebDeleteChatPopup.react} invocation path: builds a
     * delete-chat mutation via
     * {@link DeleteChatHandler#getDeleteChatMutation(Instant, Jid, boolean, SyncActionMessageRange)},
     * pushes it via {@link #pushWebAppState(SyncPatchType, List)}, and
     * removes the chat from the local store eagerly.
     *
     * @param chat the JID of the chat to delete
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebDeleteChatSync.getDeleteChatMutation (sync mutation
     * build and dispatch). The media-delete flag defaults to {@code false}
     * (keep media) per the common WA Web flow; callers that need to delete
     * media should invoke the handler directly.
     */
    @WhatsAppWebExport(moduleName = "WAWebDeleteChatSync", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteChat(Jid chat) {
        Objects.requireNonNull(chat, "chat cannot be null");
        var timestamp = Instant.now(); // WAWebDeleteChatSync.getDeleteChatMutation: timestamp: e
        var chatModel = store.findChatByJid(chat).orElse(null); // WAWebSyncdGetChat.resolveChatForMutationIndex (at apply time)
        var messageRange = chatModel != null ? buildOutgoingMessageRange(chatModel) : null; // WAWebDeleteChatSync.getDeleteChatMutation: constructForwardMovingMessageRange
        var mutation = DeleteChatHandler.INSTANCE.getDeleteChatMutation(timestamp, chat, false, messageRange); // WAWebDeleteChatSync.getDeleteChatMutation
        webAppStateService.pushPatches(DeleteChatHandler.INSTANCE.collectionName(), List.of(mutation)); // WAWebSyncdCoreApi.lockForSync -> push
        if (chatModel != null) { // WAWebDeleteChatSync.deleteChat: deleteFromStorage
            store.removeChat(chatModel); // ADAPTED: WAWebChatDeleteBridge.deleteFromStorage — Cobalt removes the chat from the in-memory store
        }
    }

    /**
     * Locks a chat, hiding it from the main chat list behind the chat-lock
     * PIN.
     *
     * <p>Per WhatsApp Web {@code WAWebChatLockAction.setChatAsLocked}: invokes
     * {@code WAWebLockChatSync.sendLockMutation} with {@code isLocked=true},
     * which queues an unarchive mutation, an unpin mutation, and a lock
     * mutation via
     * {@link LockChatHandler#getMutationsForLock(Instant, boolean, Jid, SyncActionMessageRange)};
     * Cobalt pushes the full set through
     * {@link #pushWebAppState(SyncPatchType, List)} and mirrors the
     * {@code isLocked=true, archive=false, pin=undefined} update on the
     * local {@link Chat} eagerly.
     *
     * @param chat the JID of the chat to lock
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebChatLockAction.setChatAsLocked
     */
    @WhatsAppWebExport(moduleName = "WAWebChatLockAction", exports = "setChatAsLocked",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void lockChat(Jid chat) {
        Objects.requireNonNull(chat, "chat cannot be null");
        pushLockMutation(chat, true); // WAWebChatLockAction.setChatAsLocked: u(e, !0, t)
    }

    /**
     * Unlocks a chat, restoring it to the main chat list.
     *
     * <p>Per WhatsApp Web {@code WAWebChatLockAction.setChatAsUnlocked}:
     * invokes {@code WAWebLockChatSync.sendLockMutation} with
     * {@code isLocked=false}, which queues only the lock mutation via
     * {@link LockChatHandler#getMutationsForLock(Instant, boolean, Jid, SyncActionMessageRange)};
     * Cobalt pushes the mutation through
     * {@link #pushWebAppState(SyncPatchType, List)} and flips the
     * {@code isLocked=false} flag on the local {@link Chat} eagerly.
     *
     * @param chat the JID of the chat to unlock
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebChatLockAction.setChatAsUnlocked
     */
    @WhatsAppWebExport(moduleName = "WAWebChatLockAction", exports = "setChatAsUnlocked",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unlockChat(Jid chat) {
        Objects.requireNonNull(chat, "chat cannot be null");
        pushLockMutation(chat, false); // WAWebChatLockAction.setChatAsUnlocked: u(e, !1, t)
    }

    /**
     * Builds and dispatches the lock-state mutation set for the target chat.
     *
     * @param chat   the chat JID
     * @param locked {@code true} to lock, {@code false} to unlock
     */
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "sendLockMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void pushLockMutation(Jid chat, boolean locked) {
        var timestamp = Instant.now(); // WAWebLockChatSync.sendLockMutation: var i = unixTimeMs()
        var chatModel = store.findChatByJid(chat).orElse(null); // WAWebChatLockAction.e: ChatCollection.get(e)
        var messageRange = chatModel != null ? buildOutgoingMessageRange(chatModel) : null; // WAWebLockChatSync.sendLockMutation: ArchiveChatSync.getArchiveChatMutation requires a range
        var mutations = LockChatHandler.INSTANCE.getMutationsForLock(timestamp, locked, chat, messageRange); // WAWebLockChatSync.sendLockMutation
        webAppStateService.pushPatches(LockChatHandler.INSTANCE.collectionName(), mutations); // WAWebSyncdCoreApi.lockForSync([], mutations, ...)
        if (chatModel != null) { // WAWebChatLockAction.e: updateChatTable(e, r) then ChatCollection.get(e).set(r)
            chatModel.setLocked(locked); // WAWebChatLockAction.e: {isLocked: t}
            if (locked) { // WAWebChatLockAction.e: t ? {isLocked: t, archive: !1, pin: void 0} : {isLocked: t}
                chatModel.setArchived(false); // WAWebChatLockAction.e: archive: !1
                chatModel.setPinnedTimestamp(null); // WAWebChatLockAction.e: pin: void 0
            }
        }
    }

    /**
     * Sets the per-chat disappearing-message timer via a direct IQ stanza.
     *
     * <p>For peer (one-to-one) chats, the IQ is sent with
     * {@code xmlns="disappearing_mode"} and a
     * {@code <disappearing_mode duration="..."/>} child addressed to the
     * chat JID. For group chats, the IQ is sent to the group JID with
     * {@code xmlns="w:g2"} and a {@code <ephemeral expiration="..."/>} child
     * (or {@code <not_ephemeral/>} when the timer is disabled).
     *
     * <p>The local {@link Chat#setEphemeralExpiration(ChatEphemeralTimer)}
     * and {@link Chat#setEphemeralSettingTimestamp(Instant)} fields are
     * updated eagerly after the server returns success.
     *
     * @param chat  the JID of the chat whose timer is being changed
     * @param timer the new timer; {@link ChatEphemeralTimer#OFF} disables
     *              disappearing messages
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebChatEphemerality + WAWebGroupModifyInfoJob.setEphemeralGroupProperty
     * (group path) / WAWebSetDisappearingModeJob.setDisappearingMode (peer
     * path). Cobalt unifies the two paths behind a single entry point and
     * dispatches the appropriate IQ shape based on the JID server. The
     * protocol message that WA Web's
     * {@code WAWebChangeEphemeralDurationChatAction.changeChatEphemeralDuration}
     * injects into the chat is intentionally skipped because it is a UI
     * concern.
     */
    @WhatsAppWebExport(moduleName = "WAWebChatEphemerality", exports = "setEphemeralSetting",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void setEphemeralTimer(Jid chat, ChatEphemeralTimer timer) {
        Objects.requireNonNull(chat, "chat cannot be null");
        Objects.requireNonNull(timer, "timer cannot be null");
        var seconds = timer.periodSeconds(); // ChatEphemeralTimer.periodSeconds -> WAWebEphemeralConstants duration in seconds
        if (chat.hasGroupOrCommunityServer()) { // WAWebChangeEphemeralDurationChatAction.changeEphemeralDuration: getIsGroup(e) branch
            // WAWebGroupModifyInfoJob.setEphemeralGroupProperty: builds a w:g2 iq with <ephemeral expiration="..."/> or <not_ephemeral/>
            var child = seconds > 0 // WAWebGroupModifyInfoJob.setEphemeralGroupProperty: t > 0 ? {ephemeralArgs: {ephemeralExpiration: t}} : {hasNotEphemeral: true}
                    ? new NodeBuilder().description("ephemeral").attribute("expiration", seconds).build() // WASmaxOutGroupsSetPropertyRequest.makeSetPropertyRequestEphemeral: smax("ephemeral", {expiration: INT(t)})
                    : new NodeBuilder().description("not_ephemeral").build(); // WASmaxOutGroupsSetPropertyRequest.makeSetPropertyRequestNotEphemeral: smax("not_ephemeral", null)
            var iqNode = new NodeBuilder()
                    .description("iq")
                    .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin.mergeBaseSetGroupMixin: xmlns: "w:g2"
                    .attribute("to", chat) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(t)
                    .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: set
                    .content(child);
            sendNode(iqNode); // WASmaxGroupsSetPropertyRPC.sendSetPropertyRPC: deprecatedSendIq
        } else { // WAWebChangeEphemeralDurationChatAction.changeChatEphemeralDuration: 1-1 chat branch
            // WAWebSetDisappearingModeJob.setDisappearingMode: wap("iq", {xmlns: "disappearing_mode", to: S_WHATSAPP_NET, type: "set"}, wap("disappearing_mode", {duration: String(t)}))
            // ADAPTED: WA Web's one-to-one path sends a protocol message through the chat instead of a direct IQ; Cobalt uses the same IQ shape the global-default path uses but addresses the IQ to the chat
            var disappearing = new NodeBuilder()
                    .description("disappearing_mode") // WAWebSetDisappearingModeJob.setDisappearingMode: wap("disappearing_mode", ...)
                    .attribute("duration", seconds) // WAWebSetDisappearingModeJob.setDisappearingMode: duration: CUSTOM_STRING(String(t))
                    .build();
            var iqNode = new NodeBuilder()
                    .description("iq")
                    .attribute("xmlns", "disappearing_mode") // WAWebSetDisappearingModeJob.setDisappearingMode: xmlns: "disappearing_mode"
                    .attribute("to", chat) // ADAPTED: per-chat path addresses the chat itself, contrasting with the global path which targets S_WHATSAPP_NET
                    .attribute("type", "set") // WAWebSetDisappearingModeJob.setDisappearingMode: type: "set"
                    .content(disappearing);
            sendNode(iqNode); // WAWebSetDisappearingModeJob.setDisappearingMode: deprecatedSendIq(r, s)
        }
        store.findChatByJid(chat).ifPresent(chatModel -> { // WAWebChangeEphemeralDurationChatAction: t.ephemeralDuration = d; updateChatTable(...)
            chatModel.setEphemeralExpiration(timer == ChatEphemeralTimer.OFF ? null : timer); // WAWebChangeEphemeralDurationChatAction: ephemeralDuration = d
            chatModel.setEphemeralSettingTimestamp(Instant.now()); // WAWebUpdateEphemeralSettingTimestampChatAction: t.ephemeralSettingTimestamp = ...
        });
    }

    /**
     * Sets the default disappearing-message timer for all new chats, via a
     * direct IQ stanza to {@link JidServer#user()}.
     *
     * <p>Per WhatsApp Web {@code WAWebSetDisappearingModeJob.setDisappearingMode}:
     * sends an {@code iq} stanza with {@code xmlns="disappearing_mode"},
     * {@code type="set"}, and a {@code <disappearing_mode duration="..."/>}
     * child node. The local
     * {@link WhatsAppStore#setNewChatsEphemeralTimer(ChatEphemeralTimer)}
     * is updated eagerly after the server returns success.
     *
     * @param timer the new default timer; {@link ChatEphemeralTimer#OFF}
     *              disables disappearing messages by default
     * @throws NullPointerException if {@code timer} is {@code null}
     *
     * @implNote WAWebSetDisappearingModeJob.setDisappearingMode
     */
    @WhatsAppWebExport(moduleName = "WAWebSetDisappearingModeJob", exports = "setDisappearingMode",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void setDefaultDisappearingMode(ChatEphemeralTimer timer) {
        Objects.requireNonNull(timer, "timer cannot be null");
        var seconds = timer.periodSeconds(); // ChatEphemeralTimer.periodSeconds: duration in seconds
        var disappearing = new NodeBuilder()
                .description("disappearing_mode") // WAWebSetDisappearingModeJob.setDisappearingMode: wap("disappearing_mode", {duration: CUSTOM_STRING(String(t))})
                .attribute("duration", seconds) // WAWebSetDisappearingModeJob.setDisappearingMode: duration: String(t)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq") // WAWebSetDisappearingModeJob.setDisappearingMode: wap("iq", ...)
                .attribute("xmlns", "disappearing_mode") // WAWebSetDisappearingModeJob.setDisappearingMode: xmlns: "disappearing_mode"
                .attribute("to", JidServer.user()) // WAWebSetDisappearingModeJob.setDisappearingMode: to: S_WHATSAPP_NET
                .attribute("type", "set") // WAWebSetDisappearingModeJob.setDisappearingMode: type: "set"
                .content(disappearing);
        sendNode(iqNode); // WAWebSetDisappearingModeJob.setDisappearingMode: deprecatedSendIq(r, s)
        store.setNewChatsEphemeralTimer(timer); // WAWebSetDisappearingModePrivacyAction.setDisappearingMode: updateDisappearingModeForContact -> store.newChatsEphemeralTimer
    }

    /**
     * Returns the default disappearing-message timer applied to new chats.
     *
     * <p>Per WhatsApp Web {@code WAWebGetDisappearingModeJob.getDisappearingMode}:
     * normally this would query the server for the current default, but
     * Cobalt instead returns the value cached on the local
     * {@link WhatsAppStore} because the server-side default is kept in sync
     * via {@code WAWebHandleDisappearingModeNotification} whenever the user
     * changes it from any device.
     *
     * @return the cached default ephemeral timer, never {@code null}
     *
     * @implNote ADAPTED: WAWebGetDisappearingModeJob.getDisappearingMode
     * queries a {@code USyncQuery} with the disappearing-mode protocol to
     * fetch a peer's current setting; Cobalt's local
     * {@link WhatsAppStore#newChatsEphemeralTimer()} reflects the current
     * own-account default via {@code WAWebHandleDisappearingModeNotification}
     * updates.
     */
    @WhatsAppWebExport(moduleName = "WAWebGetDisappearingModeJob", exports = "getDisappearingMode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public ChatEphemeralTimer queryChatsDefaultEphemeral() {
        return store.newChatsEphemeralTimer(); // WAWebGetDisappearingModeJob: USyncQuery result.disappearing_mode.duration — Cobalt reads from the locally cached default
    }

    /**
     * Creates a new WhatsApp group with the given subject and initial
     * participants, leaving disappearing messages off.
     *
     * <p>Equivalent to {@link #createGroup(String, ChatEphemeralTimer, Collection)}
     * with {@link ChatEphemeralTimer#OFF}.
     *
     * @param subject      the non-{@code null} group display name
     * @param participants the non-{@code null}, non-empty collection of
     *                     user JIDs to add on creation
     * @return the parsed metadata of the freshly created group
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the participant collection is empty
     *
     * @implNote Convenience overload mirroring WA Web's {@code createGroup}
     * call path with an {@code ephemeralDuration} of {@code 0}.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupCreateJob", exports = "createGroup",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public GroupMetadata createGroup(String subject, Collection<Jid> participants) {
        return createGroup(subject, ChatEphemeralTimer.OFF, participants);
    }

    /**
     * Creates a new WhatsApp group with the given subject, initial
     * disappearing-message timer and participants.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the {@code g.us} server carrying a {@code <create>} child with the
     * requested {@code subject}, an optional {@code <ephemeral
     * expiration="..."/>} child and one {@code <participant jid="..."/>}
     * child per member.
     *
     * <p>The server response embeds a {@code <group>} subtree identical in
     * shape to the one returned by {@code queryChatMetadata}, so it is
     * routed through {@link #handleChatMetadata(Node)} to populate the
     * local store with the new chat.
     *
     * @param subject         the non-{@code null} group display name
     * @param ephemeralTimer  the initial disappearing-message timer;
     *                        {@link ChatEphemeralTimer#OFF} disables it
     * @param participants    the non-{@code null}, non-empty collection of
     *                        user JIDs to add on creation
     * @return the parsed metadata of the freshly created group
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the participant collection is empty
     *
     * @implNote WAWebGroupCreateJob.createGroup builds a SMAX
     * {@code CreateRequest} via {@code WASmaxOutGroupsCreateRequest.makeCreateRequest}
     * with {@code to=G_US}, {@code xmlns="w:g2"}, {@code type="set"} and a
     * {@code <create>} body containing the subject, optional ephemeral
     * duration and participant JIDs. Cobalt emits the same wire shape
     * directly; the WAM telemetry (GroupCreateCWamEvent) and SMAX
     * {@code CreateResponseGroupAlreadyExists} branch are skipped — the
     * error path surfaces through the usual server IQ error handling.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupCreateJob", exports = "createGroup",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public GroupMetadata createGroup(String subject, ChatEphemeralTimer ephemeralTimer, Collection<Jid> participants) {
        Objects.requireNonNull(subject, "subject cannot be null");
        Objects.requireNonNull(ephemeralTimer, "ephemeralTimer cannot be null");
        Objects.requireNonNull(participants, "participants cannot be null");
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required");
        }
        var createChildren = new ArrayList<Node>(participants.size() + 1); // WASmaxOutGroupsCreateRequest: REPEATED_CHILD(participant, ...) + OPTIONAL_CHILD(ephemeral, ...)
        for (var participant : participants) { // WAWebGroupCreateJob.createGroup: participantArgs.map(...)
            createChildren.add(new NodeBuilder()
                    .description("participant") // WASmaxOutGroupsCreateRequest.makeCreateRequestCreateParticipant: smax("participant", ...)
                    .attribute("jid", participant) // WASmaxOutGroupsCreateRequest: jid: JID(t)
                    .build());
        }
        var ephemeralSeconds = ephemeralTimer.periodSeconds(); // WAWebGroupCreateJob.createGroup: h===void 0 ? 0 : h
        if (ephemeralSeconds != 0) { // WAWebGroupCreateJob.createGroup: if (y !== 0)
            createChildren.add(new NodeBuilder()
                    .description("ephemeral") // WASmaxOutGroupsCreateRequest.makeCreateRequestCreateEphemeral: smax("ephemeral", {expiration: INT(t)})
                    .attribute("expiration", ephemeralSeconds)
                    .build());
        }
        var createNode = new NodeBuilder()
                .description("create") // WASmaxOutGroupsCreateRequest: smax("create", null, ...)
                .attribute("subject", subject) // WASmaxOutGroupsNamedSubjectMixin.mergeNamedSubjectMixin: subject: CUSTOM_STRING(t)
                .content(createChildren)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetServerMixin.mergeBaseSetServerMixin: xmlns: "w:g2"
                .attribute("to", Jid.of(JidServer.groupOrCommunity())) // WASmaxOutGroupsBaseSetServerMixin: to: G_US
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(createNode);
        var response = sendNode(iqNode); // WASmaxGroupsCreateRPC.sendCreateRPC: sendIQ(...)
        var metadata = handleChatMetadata(response); // WAWebGroupCreateJob.createGroup: parses the <group> subtree returned by CreateResponseSuccess
        if (!(metadata instanceof GroupMetadata groupMetadata)) { // WAWebGroupCreateJob: createGroup always creates a non-community group
            throw new NoSuchElementException("Expected a group metadata, got %s".formatted(metadata));
        }
        return groupMetadata;
    }

    /**
     * Leaves a WhatsApp group, removing the current user from the
     * participant list on the server side.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the {@code g.us} server carrying a {@code <leave>} child with a
     * single {@code <group id="..."/>} element.
     *
     * @param group the non-{@code null} JID of the group to leave
     * @throws NullPointerException     if {@code group} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote WAWebGroupExitJob.leaveGroup delegates to
     * {@code leaveGroups} which builds an {@code iq} of
     * {@code type="set", xmlns="w:g2", to=G_US} containing a
     * {@code <leave>} element with one {@code <group id="..."/>} child per
     * group. Cobalt sends the same wire shape for the single-group case;
     * the per-group response codes parsed by WA Web are not surfaced — a
     * server error maps to the usual {@code ServerStatusCodeError}
     * translation.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupExitJob", exports = "leaveGroup",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void leaveGroup(Jid group) {
        Objects.requireNonNull(group, "group cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var groupNode = new NodeBuilder()
                .description("group") // WAWebGroupExitJob.leaveGroup: wap("group", {id: GROUP_JID(e)})
                .attribute("id", group)
                .build();
        var leaveNode = new NodeBuilder()
                .description("leave") // WAWebGroupExitJob.leaveGroup: wap("leave", null, [group...])
                .content(groupNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WAWebGroupExitJob.leaveGroup: xmlns: "w:g2"
                .attribute("to", Jid.of(JidServer.groupOrCommunity())) // WAWebGroupExitJob.leaveGroup: to: G_US
                .attribute("type", "set") // WAWebGroupExitJob.leaveGroup: type: "set"
                .content(leaveNode);
        sendNode(iqNode); // WAWebGroupExitJob.leaveGroup: deprecatedSendIq(i, s)
    }

    /**
     * Creates a new WhatsApp community with the given name and optional
     * description, leaving disappearing messages off.
     *
     * <p>Equivalent to {@link #createCommunity(String, String, ChatEphemeralTimer)}
     * with {@link ChatEphemeralTimer#OFF}.
     *
     * @param name        the non-{@code null} community display name
     * @param description an optional community description; {@code null} to
     *                    omit
     * @return the parsed metadata of the freshly created community
     * @throws NullPointerException if {@code name} is {@code null}
     *
     * @implNote Convenience overload mirroring the WA Web
     * {@code sendCreateCommunity} call path with no default disappearing
     * timer.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupCommunityJob", exports = "sendCreateCommunity",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CommunityMetadata createCommunity(String name, String description) {
        return createCommunity(name, description, ChatEphemeralTimer.OFF);
    }

    /**
     * Creates a new WhatsApp community with the given name, description and
     * default disappearing-message timer.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the {@code g.us} server carrying a {@code <create>} child with the
     * requested subject, a {@code <parent default_membership_approval_mode="request_required"/>}
     * marker, an optional {@code <description id="..."><body>...</body></description>}
     * sub-tree and an optional {@code <ephemeral expiration="..."/>} child.
     *
     * <p>The server response embeds a {@code <group>} subtree identical in
     * shape to the one returned by {@code queryChatMetadata}, which is
     * routed through {@link #handleChatMetadata(Node)} so the new community
     * appears in the local store immediately.
     *
     * @param name           the non-{@code null} community display name
     * @param description    an optional community description; {@code null}
     *                       to omit
     * @param ephemeralTimer the initial disappearing-message timer;
     *                       {@link ChatEphemeralTimer#OFF} disables it
     * @return the parsed metadata of the freshly created community
     * @throws NullPointerException   if {@code name} or
     *                                {@code ephemeralTimer} is {@code null}
     * @throws NoSuchElementException if the server response does not carry
     *                                a {@code <group>} community subtree
     *
     * @implNote WAWebGroupCommunityJob.sendCreateCommunity: delegates to
     * {@code WASmaxGroupsCreateRPC.sendCreateRPC} with
     * {@code parentArgs.hasParentGroupDefaultMembershipApprovalMode} set to
     * {@code true} when the caller asks for a closed parent; Cobalt always
     * emits the closed marker because WA Web hardcodes the
     * {@code request_required} value in the {@code <parent>} element. The
     * {@code hasCreateGeneralChat}, {@code hasAllowNonAdminSubGroupCreation}
     * flags and LID addressing-mode override are skipped as they are
     * unreachable from the current WA Web UI; if needed they can be added
     * as overloads. WAM telemetry
     * ({@code WAWebGroupCreateCWamEventBuilder}) and the SMAX
     * {@code CreateResponseGroupAlreadyExists} error branch are skipped —
     * errors surface through the usual IQ error translation.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupCommunityJob", exports = "sendCreateCommunity",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CommunityMetadata createCommunity(String name, String description, ChatEphemeralTimer ephemeralTimer) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(ephemeralTimer, "ephemeralTimer cannot be null");
        var createChildren = new ArrayList<Node>(4); // WASmaxOutGroupsCreateRequest: OPTIONAL_CHILD(description), OPTIONAL_CHILD(parent), OPTIONAL_CHILD(ephemeral)
        if (description != null && !description.isEmpty()) { // WAWebGroupCommunityJob.sendCreateCommunity: a != null && a.length > 0
            createChildren.add(new NodeBuilder()
                    .description("description") // WASmaxOutGroupsCreateRequest.makeCreateRequestCreateDescription: smax("description", {id}, smax("body", null, value))
                    .attribute("id", RandomIdUtils.generateSid()) // WAWebGroupCommunityJob.sendCreateCommunity: descriptionId: WAWap.generateId()
                    .content(new NodeBuilder()
                            .description("body")
                            .content(description.getBytes(StandardCharsets.UTF_8)) // bodyElementValue: a
                            .build())
                    .build());
        }
        createChildren.add(new NodeBuilder()
                .description("parent") // WASmaxOutGroupsCreateRequest.makeCreateRequestCreateParent: smax("parent", ...) with hasParentGroupDefaultMembershipApprovalMode
                .attribute("default_membership_approval_mode", "request_required") // WASmaxOutGroupsParentGroupDefaultMembershipApprovalModeMixin: default_membership_approval_mode attr marks the closed parent
                .build());
        var ephemeralSeconds = ephemeralTimer.periodSeconds();
        if (ephemeralSeconds != 0) { // ephemeralArgs OPTIONAL_CHILD is only emitted when a non-zero timer is requested
            createChildren.add(new NodeBuilder()
                    .description("ephemeral") // WASmaxOutGroupsCreateRequest.makeCreateRequestCreateEphemeral: smax("ephemeral", {expiration: INT})
                    .attribute("expiration", ephemeralSeconds)
                    .build());
        }
        var createNode = new NodeBuilder()
                .description("create") // WASmaxOutGroupsCreateRequest: smax("create", null, ...)
                .attribute("subject", name) // WAWebGroupCommunityJob.sendCreateCommunity: namedSubjectOrUnnamedSubjectFallbackMixinGroupArgs.namedSubject.anySubject = l
                .content(createChildren)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetServerMixin: xmlns: "w:g2"
                .attribute("to", Jid.of(JidServer.groupOrCommunity())) // WASmaxOutGroupsBaseSetServerMixin: to: G_US
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(createNode);
        var response = sendNode(iqNode); // WASmaxGroupsCreateRPC.sendCreateRPC
        var metadata = handleChatMetadata(response); // WAWebGroupCommunityJob.sendCreateCommunity: parses the <group> subtree of CreateResponseSuccess
        if (!(metadata instanceof CommunityMetadata communityMetadata)) { // WAWebGroupCommunityJob: the server always returns a parent group tree for this flow
            throw new NoSuchElementException("Expected community metadata, got %s".formatted(metadata));
        }
        return communityMetadata;
    }

    /**
     * Deactivates (deletes) a community parent group on the server, turning
     * every linked subgroup into a standalone group.
     *
     * <p>Issues the
     * {@code WASmaxGroupsDeleteParentGroupRPC.sendDeleteParentGroupRPC}
     * mutation by sending an {@code iq} of
     * {@code type="set", xmlns="w:g2"} addressed to the community JID with
     * a {@code <delete_parent/>} body.
     *
     * @param community the non-{@code null} JID of the community to
     *                  deactivate
     * @throws NullPointerException     if {@code community} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote WAWebGroupCommunityJob.sendDeactivateCommunity: delegates to
     * {@code WASmaxGroupsDeleteParentGroupRPC.sendDeleteParentGroupRPC}
     * which produces an IQ with {@code to=GROUP_JID(parentGroupId)} and a
     * single {@code <delete_parent/>} body. The SMAX client/server error
     * branches surface through the usual IQ error translation rather than
     * the WA Web {@code ServerStatusCodeError} rejection.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupCommunityJob", exports = "sendDeactivateCommunity",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deactivateCommunity(Jid community) {
        Objects.requireNonNull(community, "community cannot be null");
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var deleteParentNode = new NodeBuilder()
                .description("delete_parent") // WASmaxOutGroupsDeleteParentGroupRequest: smax("delete_parent", null)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", community) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(iqTo)
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(deleteParentNode);
        sendNode(iqNode); // WASmaxGroupsDeleteParentGroupRPC.sendDeleteParentGroupRPC
    }

    /**
     * Links an existing group to a community as a subgroup.
     *
     * <p>Issues the MEX-style {@code WASmaxGroupsLinkSubGroupsRPC.sendLinkSubGroupsRPC}
     * mutation by sending an {@code iq} of
     * {@code type="set", xmlns="w:g2"} addressed to the community JID
     * carrying a {@code <links>} body with a single
     * {@code <link_group><group jid="..."/></link_group>} element.
     *
     * @param community the non-{@code null} community JID to link into
     * @param subgroup  the non-{@code null} group JID to attach as a
     *                  subgroup
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if either JID is not a group/community
     *
     * @implNote WAWebGroupCommunityJob.sendLinkSubgroups: for each subgroup
     * the SMAX request builds
     * {@code <group jid=GROUP_JID(id) hasHiddenGroup=false/>} wrapped in a
     * {@code <link_group>}. Cobalt always sends a single subgroup per call
     * and omits the {@code hidden_group} marker because WA Web hardcodes it
     * to {@code false} here. The per-subgroup error codes parsed by WA Web
     * ({@code failedGroups}, {@code failedParticipantJids}) are not
     * surfaced — a full-stanza error maps to the usual IQ error handling.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupCommunityJob", exports = "sendLinkSubgroups",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void linkGroupToCommunity(Jid community, Jid subgroup) {
        Objects.requireNonNull(community, "community cannot be null");
        Objects.requireNonNull(subgroup, "subgroup cannot be null");
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community for community");
        }
        if (!subgroup.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community for subgroup");
        }
        var groupNode = new NodeBuilder()
                .description("group") // WASmaxOutGroupsLinkSubGroupsRequest: smax("group", {jid: GROUP_JID(e)})
                .attribute("jid", subgroup)
                .build();
        var linkGroupNode = new NodeBuilder()
                .description("link_group") // WASmaxOutGroupsLinkSubGroupsRequest: smax("link_group", null, group...)
                .content(groupNode)
                .build();
        var linksNode = new NodeBuilder()
                .description("links") // WASmaxOutGroupsLinkSubGroupsRequest: smax("links", null, link_group...)
                .content(linkGroupNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", community) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(iqTo)
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(linksNode);
        sendNode(iqNode); // WASmaxGroupsLinkSubGroupsRPC.sendLinkSubGroupsRPC
    }

    /**
     * Unlinks a subgroup from its parent community, leaving the subgroup as
     * a standalone group.
     *
     * <p>Issues the {@code WASmaxGroupsUnlinkGroupsRPC.sendUnlinkGroupsRPC}
     * mutation by sending an {@code iq} of
     * {@code type="set", xmlns="w:g2"} addressed to the community JID
     * carrying an {@code <unlink>} body with a single
     * {@code <group jid="..."/>} element.
     *
     * @param community the non-{@code null} parent community JID
     * @param subgroup  the non-{@code null} subgroup JID to detach
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if either JID is not a group/community
     *
     * @implNote WAWebGroupCommunityJob.sendUnlinkSubgroups: defaults
     * {@code removeOrphanMembers} to {@code false}, matching Cobalt which
     * omits the {@code remove_orphaned_members} attribute entirely. The
     * per-subgroup error codes parsed by WA Web ({@code failedGroups}) are
     * not surfaced — a full-stanza error maps to the usual IQ error
     * handling.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupCommunityJob", exports = "sendUnlinkSubgroups",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unlinkGroupFromCommunity(Jid community, Jid subgroup) {
        Objects.requireNonNull(community, "community cannot be null");
        Objects.requireNonNull(subgroup, "subgroup cannot be null");
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community for community");
        }
        if (!subgroup.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community for subgroup");
        }
        var groupNode = new NodeBuilder()
                .description("group") // WASmaxOutGroupsUnlinkGroupsRequest: smax("group", {jid: GROUP_JID(e)})
                .attribute("jid", subgroup)
                .build();
        var unlinkNode = new NodeBuilder()
                .description("unlink") // WASmaxOutGroupsUnlinkGroupsRequest: smax("unlink", null, group...)
                .attribute("unlink_type", "sub_group") // WASmaxOutGroupsUnlinkGroupsRequest: the unlink_type attr discriminates sub-group vs linked-parent unlinking
                .content(groupNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", community) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(iqTo)
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(unlinkNode);
        sendNode(iqNode); // WASmaxGroupsUnlinkGroupsRPC.sendUnlinkGroupsRPC
    }

    /**
     * Leaves a WhatsApp community, detaching the current user from the
     * parent group and every subgroup transitively.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the {@code g.us} server carrying a {@code <leave>} body with a single
     * {@code <linked_groups parent_group_jid="..."/>} element, so the
     * server applies the exit to every linked subgroup in one round-trip.
     *
     * @param community the non-{@code null} JID of the community to leave
     * @throws NullPointerException     if {@code community} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote WAWebGroupExitJob.leaveCommunity delegates to
     * {@code leaveCommunities} which builds an {@code iq} of
     * {@code type="set", xmlns="w:g2", to=G_US} containing a
     * {@code <leave>} element with one
     * {@code <linked_groups parent_group_jid="..."/>} child per community.
     * Cobalt emits the single-community wire shape; per-community response
     * codes parsed by the WA Web {@code leaveCommunitiesResultParser} are
     * not surfaced — a server error maps to the usual
     * {@code ServerStatusCodeError} translation.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupExitJob", exports = "leaveCommunity",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void leaveCommunity(Jid community) {
        Objects.requireNonNull(community, "community cannot be null");
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var linkedGroupsNode = new NodeBuilder()
                .description("linked_groups") // WAWebGroupExitJob.leaveCommunities: wap("linked_groups", {parent_group_jid: GROUP_JID(e)})
                .attribute("parent_group_jid", community)
                .build();
        var leaveNode = new NodeBuilder()
                .description("leave") // WAWebGroupExitJob.leaveCommunities: wap("leave", null, [linked_groups...])
                .content(linkedGroupsNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WAWebGroupExitJob.leaveCommunities: xmlns: "w:g2"
                .attribute("to", Jid.of(JidServer.groupOrCommunity())) // WAWebGroupExitJob.leaveCommunities: to: G_US
                .attribute("type", "set") // WAWebGroupExitJob.leaveCommunities: type: "set"
                .content(leaveNode);
        sendNode(iqNode); // WAWebGroupExitJob.leaveCommunities: deprecatedSendIq(i, u)
    }

    /**
     * Transfers ownership of a community to one of its existing admins.
     *
     * <p>Issues the {@code WAWebMexTransferCommunityOwnershipJob} MEX
     * mutation over {@code w:mex}, carrying a serialised JSON
     * {@code input} containing the community id and the new owner's JID.
     *
     * @param community the non-{@code null} community JID
     * @param newOwner  the non-{@code null} JID of the new owner; must be
     *                  an existing admin of the community
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code community} is not a
     *                                  group/community or {@code newOwner}
     *                                  is a group/community
     *
     * @implNote WAWebMexTransferCommunityOwnershipJob.mexTransferCommunityOwnershipJob:
     * the MEX variables are wrapped by {@link TransferCommunityOwnershipMex.Request}
     * which serialises the {@code input} object to a JSON string. Cobalt
     * skips the returned {@code lid_migration_state} post-processing; the
     * store refresh is left to the next {@code queryChatMetadata}
     * invocation.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexTransferCommunityOwnershipJob", exports = "mexTransferCommunityOwnershipJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void transferCommunityOwnership(Jid community, Jid newOwner) {
        Objects.requireNonNull(community, "community cannot be null");
        Objects.requireNonNull(newOwner, "newOwner cannot be null");
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        if (newOwner.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a user JID for newOwner");
        }
        var input = JSON.toJSONString(Map.of( // WAWebMexTransferCommunityOwnershipJob.mexTransferCommunityOwnershipJob: fetchQuery(s, {input: JSON.stringify({group_id, new_owner_id})})
                "group_id", community.toString(),
                "new_owner_id", newOwner.toString()
        ));
        var request = new TransferCommunityOwnershipMex.Request(input);
        sendNode(request.toNode()); // WAWebMexClient.fetchQuery
    }

    /**
     * Queries the pending subgroup suggestions for a community — groups the
     * user belongs to that the server recommends moving into the community.
     *
     * <p>Issues the {@code WAWebMexFetchSubgroupSuggestionsJob} MEX query
     * over {@code w:mex} using
     * {@link FetchSubgroupSuggestionsMex.Request} with
     * {@code query_context="INTERACTIVE"}.
     *
     * @param community the non-{@code null} community JID to query
     * @return the list of suggested subgroup JIDs; empty when there are no
     *         pending suggestions
     * @throws NullPointerException     if {@code community} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote WAWebMexFetchSubgroupSuggestionsJob.mexFetchSubgroupSuggestions:
     * reads {@code xwa2_group_query_by_id.sub_group_suggestions.edges[].node.id}.
     * The per-suggestion metadata (creator, subject, participant count,
     * etc.) is available on {@link FetchSubgroupSuggestionsMex.Response} if
     * richer views are required; this convenience returns only the JIDs.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchSubgroupSuggestionsJob", exports = "mexFetchSubgroupSuggestions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<Jid> querySubgroupSuggestions(Jid community) {
        Objects.requireNonNull(community, "community cannot be null");
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var request = new FetchSubgroupSuggestionsMex.Request(community.toString(), "INTERACTIVE", null);
        var response = sendNode(request.toNode()); // WAWebMexClient.fetchQuery
        return FetchSubgroupSuggestionsMex.Response.of(response)
                .flatMap(FetchSubgroupSuggestionsMex.Response::subGroupSuggestions)
                .stream()
                .map(FetchSubgroupSuggestionsMex.Response.SubGroupSuggestions::edges)
                .flatMap(Collection::stream)
                .map(FetchSubgroupSuggestionsMex.Response.SubGroupSuggestions.Edges::node)
                .flatMap(Optional::stream)
                .map(FetchSubgroupSuggestionsMex.Response.SubGroupSuggestions.Edges.Node::id)
                .flatMap(Optional::stream)
                .map(Jid::of)
                .toList();
    }

    /**
     * Approves a pending subgroup suggestion, accepting the group into the
     * community as an official subgroup.
     *
     * <p>Equivalent to calling the shared {@code sub_group_suggestions_action}
     * stanza with an {@code <approve>} child.
     *
     * @param community         the non-{@code null} parent community JID
     * @param suggestedSubgroup the non-{@code null} JID of the suggested
     *                          group to approve
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if either JID is not a
     *                                  group/community
     *
     * @implNote WAWebSubgroupSuggestionsActionJob.sendSubgroupSuggestionsAction
     * with {@code SubgroupSuggestionAction.APPROVE}: the
     * {@code <sub_group_suggestion>} child carries both {@code jid} and
     * {@code creator}; Cobalt omits the {@code creator} attribute as WA Web
     * resolves it server-side when missing. Error translation ({@code p(...)}
     * helper) is replaced by Cobalt's IQ error handling.
     */
    @WhatsAppWebExport(moduleName = "WAWebSubgroupSuggestionsActionJob", exports = "sendSubgroupSuggestionsAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void approveSubgroupSuggestion(Jid community, Jid suggestedSubgroup) {
        subgroupSuggestionsAction(community, suggestedSubgroup, "approve"); // WAWebSubgroupSuggestionsActionJob: n === c.APPROVE
    }

    /**
     * Rejects a pending subgroup suggestion, declining the recommendation
     * to move the group into the community.
     *
     * <p>Equivalent to calling the shared {@code sub_group_suggestions_action}
     * stanza with a {@code <reject>} child.
     *
     * @param community         the non-{@code null} parent community JID
     * @param suggestedSubgroup the non-{@code null} JID of the suggested
     *                          group to reject
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if either JID is not a
     *                                  group/community
     *
     * @implNote WAWebSubgroupSuggestionsActionJob.sendSubgroupSuggestionsAction
     * with {@code SubgroupSuggestionAction.REJECT}: same wire shape as
     * approve with a {@code <reject>} child instead of {@code <approve>}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSubgroupSuggestionsActionJob", exports = "sendSubgroupSuggestionsAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void rejectSubgroupSuggestion(Jid community, Jid suggestedSubgroup) {
        subgroupSuggestionsAction(community, suggestedSubgroup, "reject"); // WAWebSubgroupSuggestionsActionJob: n === c.REJECT
    }

    /**
     * Issues a {@code sub_group_suggestions_action} stanza against the
     * given community with the requested action (approve/reject).
     *
     * @param community   the validated community JID
     * @param subgroup    the validated suggested subgroup JID
     * @param actionLabel {@code "approve"} or {@code "reject"}
     *
     * @implNote WASmaxOutGroupsSubGroupSuggestionsActionRequest.makeSubGroupSuggestionsActionRequest:
     * the WA Web request wraps per-action children
     * ({@code <approve>}/{@code <reject>}/{@code <cancel>}) inside a
     * {@code <sub_group_suggestions_action>} element. Cobalt emits exactly
     * one action child per call.
     */
    private void subgroupSuggestionsAction(Jid community, Jid subgroup, String actionLabel) {
        Objects.requireNonNull(community, "community cannot be null");
        Objects.requireNonNull(subgroup, "subgroup cannot be null");
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community for community");
        }
        if (!subgroup.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community for suggested subgroup");
        }
        var suggestionNode = new NodeBuilder()
                .description("sub_group_suggestion") // WASmaxOutGroupsSubGroupSuggestionWithoutCreatorMixin: smax("sub_group_suggestion", {jid: GROUP_JID(t)})
                .attribute("jid", subgroup)
                .build();
        var actionNode = new NodeBuilder()
                .description(actionLabel) // WASmaxOutGroupsSubGroupSuggestionsActionRequest: smax("approve"|"reject", null, REPEATED_CHILD(sub_group_suggestion, ...))
                .content(suggestionNode)
                .build();
        var suggestionsActionNode = new NodeBuilder()
                .description("sub_group_suggestions_action") // WASmaxOutGroupsSubGroupSuggestionsActionRequest: smax("sub_group_suggestions_action", null, OPTIONAL_CHILD(...))
                .content(actionNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", community) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(iqTo)
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(suggestionsActionNode);
        sendNode(iqNode); // WASmaxGroupsSubGroupSuggestionsActionRPC.sendSubGroupSuggestionsActionRPC
    }

    /**
     * Queries the participant count of a single subgroup by piggy-backing
     * on the community-wide participant-count MEX query.
     *
     * <p>Issues the {@code WAWebMexQuerySubgroupParticipantCountJob} MEX
     * query over {@code w:mex} and projects the answer to the single
     * subgroup of interest.
     *
     * @param subgroup the non-{@code null} subgroup JID to query
     * @return the total participant count, or {@code -1} when the server
     *         response does not carry a count for the requested subgroup
     * @throws NullPointerException     if {@code subgroup} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote WAWebMexQuerySubgroupParticipantCountJobQuery.graphql:
     * reads {@code xwa2_group_query_by_id.sub_groups.edges[].node.total_participants_count}.
     * WA Web passes the parent community id and receives every subgroup's
     * count in a single round-trip; Cobalt exposes the single-subgroup
     * shortcut by sending the subgroup as the {@code input} id and picking
     * the matching edge.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexQuerySubgroupParticipantCountJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public long querySubgroupParticipantCount(Jid subgroup) {
        Objects.requireNonNull(subgroup, "subgroup cannot be null");
        if (!subgroup.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var request = new QuerySubgroupParticipantCountMex.Request(subgroup.toString());
        var response = sendNode(request.toNode()); // WAWebMexClient.fetchQuery
        return QuerySubgroupParticipantCountMex.Response.of(response)
                .flatMap(QuerySubgroupParticipantCountMex.Response::subGroups)
                .stream()
                .map(QuerySubgroupParticipantCountMex.Response.SubGroups::edges)
                .flatMap(Collection::stream)
                .map(QuerySubgroupParticipantCountMex.Response.SubGroups.Edges::node)
                .flatMap(Optional::stream)
                .filter(entry -> entry.id().map(id -> Objects.equals(id, subgroup.toString()) || Objects.equals(id, subgroup.user())).orElse(false))
                .map(QuerySubgroupParticipantCountMex.Response.SubGroups.Edges.Node::totalParticipantsCount)
                .filter(java.util.OptionalLong::isPresent)
                .mapToLong(java.util.OptionalLong::getAsLong)
                .findFirst()
                .orElse(-1L);
    }

    /**
     * Queries the full list of subgroups that belong to a community.
     *
     * <p>Issues the {@code WAWebMexFetchAllSubgroupsJob} MEX query over
     * {@code w:mex} via {@link FetchAllSubgroupsMex.Request} with
     * {@code query_context="INTERACTIVE"} and returns every subgroup
     * declared by the response, including the default subgroup.
     *
     * @param community the non-{@code null} community JID
     * @return the list of subgroup JIDs belonging to the community
     * @throws NullPointerException     if {@code community} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote WAWebMexFetchAllSubgroupsJob: reads
     * {@code xwa2_group_query_by_id.sub_groups.edges[].node} plus the
     * {@code default_sub_group} field. Cobalt already uses this query
     * internally from {@code parseChatMetadata}; this method exposes it as
     * a user-facing call on demand.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAllSubgroupsJob", exports = "mexFetchAllSubgroups",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<Jid> querySubgroups(Jid community) {
        Objects.requireNonNull(community, "community cannot be null");
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var request = new FetchAllSubgroupsMex.Request(community.toString(), "INTERACTIVE", null);
        var response = sendNode(request.toNode()); // WAWebMexClient.fetchQuery
        var parsed = FetchAllSubgroupsMex.Response.of(response);
        var result = new ArrayList<Jid>();
        parsed.ifPresent(entry -> {
            entry.defaultSubGroup()
                    .flatMap(DefaultSubGroup::id)
                    .map(Jid::of)
                    .ifPresent(result::add);
            entry.subGroups()
                    .stream()
                    .map(SubGroups::edges)
                    .flatMap(Collection::stream)
                    .map(SubGroups.Edges::node)
                    .flatMap(Optional::stream)
                    .map(SubGroups.Edges.Node::id)
                    .flatMap(Optional::stream)
                    .map(Jid::of)
                    .forEach(result::add);
        });
        return List.copyOf(result);
    }

    /**
     * Adds one or more participants to a WhatsApp group, returning the
     * per-participant server status.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the group JID with an {@code <add>} body containing one
     * {@code <participant jid="..."/>} child per target user. The server
     * responds with one {@code <participant>} element per input, carrying
     * an {@code error} attribute when the addition failed.
     *
     * @param group the non-{@code null} target group JID
     * @param toAdd the non-{@code null}, non-empty collection of user JIDs
     *              to add
     * @return a map from the target JID to its server-assigned
     *         {@link GroupParticipantStatus}. {@link GroupParticipantStatus#OK}
     *         indicates the participant was added successfully.
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *                                  or the collection is empty
     *
     * @implNote WAWebGroupModifyParticipantsJob.addGroupParticipants builds
     * a SMAX {@code AddParticipantsRequest} via
     * {@code WASmaxOutGroupsAddParticipantsRequest.makeAddParticipantsRequest}.
     * The {@code privacy_token_sending_on_group_participant_add} AB prop
     * and chat-table permission-token map are skipped: Cobalt does not
     * exchange per-participant privacy tokens on add. WAM telemetry and
     * username post-processing (setUsernamesJob) are also skipped.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyParticipantsJob", exports = "addGroupParticipants",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, GroupParticipantStatus> addGroupParticipants(Jid group, Collection<Jid> toAdd) {
        return modifyGroupParticipants(group, toAdd, "add"); // WAWebGroupModifyParticipantsJob: switch on action -> smax("add", ...)
    }

    /**
     * Removes one or more participants from a WhatsApp group, returning
     * the per-participant server status.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the group JID with a {@code <remove>} body containing one
     * {@code <participant jid="..."/>} child per target user.
     *
     * @param group    the non-{@code null} target group JID
     * @param toRemove the non-{@code null}, non-empty collection of user
     *                 JIDs to remove
     * @return a map from the target JID to its server-assigned
     *         {@link GroupParticipantStatus}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *                                  or the collection is empty
     *
     * @implNote WAWebGroupModifyParticipantsJob.removeGroupParticipants
     * delegates to
     * {@code WASmaxOutGroupsRemoveParticipantsRequest.makeRemoveParticipantsRequest}
     * which emits an {@code <iq type="set" xmlns="w:g2" to=groupJid>}
     * carrying a {@code <remove>} element. Cobalt does not send
     * {@code linked_groups="true"} — linked-group removal is not exposed
     * by this API. Username post-processing and WAM telemetry are skipped.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyParticipantsJob", exports = "removeGroupParticipants",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, GroupParticipantStatus> removeGroupParticipants(Jid group, Collection<Jid> toRemove) {
        return modifyGroupParticipants(group, toRemove, "remove"); // WAWebGroupModifyParticipantsJob: smax("remove", ...)
    }

    /**
     * Promotes one or more participants of a WhatsApp group to
     * administrator.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the group JID with a {@code <promote>} body containing one
     * {@code <participant jid="..."/>} child per target user.
     *
     * @param group      the non-{@code null} target group JID
     * @param toPromote  the non-{@code null}, non-empty collection of user
     *                   JIDs to promote
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *                                  or the collection is empty
     *
     * @implNote WAWebGroupModifyParticipantsJob.promoteGroupParticipants
     * routes through
     * {@code WASmaxOutGroupsPromoteDemoteRequest.makePromoteDemoteRequest}
     * with a {@code promoteArgs} branch. WAM telemetry, username post-processing
     * and LID-migration normalisation (WAWebLidMigrationUtils) are skipped —
     * Cobalt relies on caller-provided JIDs being in the correct addressing
     * mode.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyParticipantsJob", exports = "promoteGroupParticipants",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void promoteGroupParticipants(Jid group, Collection<Jid> toPromote) {
        modifyGroupParticipants(group, toPromote, "promote"); // WAWebGroupModifyParticipantsJob: smax("promote", ...)
    }

    /**
     * Demotes one or more administrators of a WhatsApp group to regular
     * members.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the group JID with a {@code <demote>} body containing one
     * {@code <participant jid="..."/>} child per target user.
     *
     * @param group     the non-{@code null} target group JID
     * @param toDemote  the non-{@code null}, non-empty collection of user
     *                  JIDs to demote
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *                                  or the collection is empty
     *
     * @implNote WAWebGroupModifyParticipantsJob.demoteGroupParticipants
     * routes through
     * {@code WASmaxOutGroupsPromoteDemoteRequest.makePromoteDemoteRequest}
     * with a {@code demoteArgs} branch. WAM telemetry, username post-processing
     * and LID-migration normalisation are skipped.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyParticipantsJob", exports = "demoteGroupParticipants",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void demoteGroupParticipants(Jid group, Collection<Jid> toDemote) {
        modifyGroupParticipants(group, toDemote, "demote"); // WAWebGroupModifyParticipantsJob: smax("demote", ...)
    }

    /**
     * Sends a participant-modification IQ to the given group and parses
     * the per-participant response codes.
     *
     * <p>This private helper backs {@link #addGroupParticipants(Jid, Collection)},
     * {@link #removeGroupParticipants(Jid, Collection)},
     * {@link #promoteGroupParticipants(Jid, Collection)} and
     * {@link #demoteGroupParticipants(Jid, Collection)}. The action name
     * is used as the body child description
     * ({@code "add"}/{@code "remove"}/{@code "promote"}/{@code "demote"})
     * per WA Web's {@code WASmaxOutGroups*Request} modules.
     *
     * @param group         the target group JID
     * @param participants  the participants to modify
     * @param action        the body child name
     * @return a map from participant JID to the server-assigned status
     */
    private Map<Jid, GroupParticipantStatus> modifyGroupParticipants(Jid group, Collection<Jid> participants, String action) {
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(participants, "participants cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required");
        }
        var participantNodes = new ArrayList<Node>(participants.size()); // WASmaxOutGroupsAddParticipantsRequest: REPEATED_CHILD(participant, n, 1, 1024)
        for (var participant : participants) {
            participantNodes.add(new NodeBuilder()
                    .description("participant") // WASmaxOutGroups*Request.make*Participant: smax("participant", {jid: ...})
                    .attribute("jid", participant)
                    .build());
        }
        var actionNode = new NodeBuilder()
                .description(action) // WASmaxOutGroups*Request: smax("add"|"remove"|"promote"|"demote", null, [...])
                .content(participantNodes)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin.mergeBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", group) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(t)
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(actionNode);
        var response = sendNode(iqNode); // WASmaxGroups*ParticipantsRPC: deprecatedSendIq
        return parseParticipantStatus(response); // WAWebGroupModifyParticipantsJob: participants.map(e => ({code: ...}))
    }

    /**
     * Extracts a map of JID to {@link GroupParticipantStatus} from a
     * participant-modification response.
     *
     * <p>Iterates the {@code <participant>} descendants of the response,
     * reading each one's {@code jid} attribute as the map key and its
     * {@code error} attribute (defaulted to {@code 200}) as the map
     * value.
     *
     * @param response the server response node
     * @return a map from participant JID to parsed status
     */
    private Map<Jid, GroupParticipantStatus> parseParticipantStatus(Node response) {
        var result = new LinkedHashMap<Jid, GroupParticipantStatus>();
        response.streamChildren() // WAWebGroupModifyParticipantsJob: response body child (add/remove/promote/demote)
                .flatMap(entry -> entry.streamChildren("participant")) // WAWebGroupModifyParticipantsJob: participants.map
                .forEach(participantNode -> {
                    var jid = participantNode.getAttributeAsJid("jid", null); // WAWebGroupModifyParticipantsJob: userWid = userJidToUserWid(e.jid)
                    if (jid == null) {
                        return;
                    }
                    var code = (int) participantNode.getAttributeAsLong("error", GroupParticipantStatus.OK.code()); // WAWebGroupModifyParticipantsJob: code: r != null ? r : "200"
                    result.put(jid, GroupParticipantStatus.of(code));
                });
        return result;
    }

    /**
     * Changes the subject (display name) of a WhatsApp group.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the group JID with a {@code <subject>} child whose text content is
     * the new subject.
     *
     * @param group      the non-{@code null} target group JID
     * @param newSubject the non-{@code null} new subject
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote WAWebGroupModifyInfoJob.setGroupSubject emits a SMAX
     * {@code SetSubjectRequest} via
     * {@code WASmaxOutGroupsSetSubjectRequest.makeSetSubjectRequest} which
     * sets {@code xmlns="w:g2", to=groupJid, type="set"} and appends a
     * {@code <subject>newSubject</subject>} child. Cobalt emits the same
     * wire shape.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyInfoJob", exports = "setGroupSubject",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void changeGroupSubject(Jid group, String newSubject) {
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(newSubject, "newSubject cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var subjectNode = new NodeBuilder()
                .description("subject") // WASmaxOutGroupsSetSubjectChangeSubjectMixin: smax("subject", null, t)
                .content(newSubject) // subjectElementValue
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", group) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(t)
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(subjectNode);
        sendNode(iqNode); // WASmaxGroupsSetSubjectRPC.sendSetSubjectRPC
    }

    /**
     * Changes the free-form description of a WhatsApp group, or clears it
     * when {@code newDescription} is {@code null}.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the group JID. When the new description is non-{@code null}, the
     * body is a {@code <description>} element wrapping a {@code <body>}
     * child with the new text. When {@code null}, the body is an empty
     * {@code <description delete="true"/>} element, matching WA Web's
     * {@code hasDescriptionDeleteTrue:!0} branch.
     *
     * @param group          the non-{@code null} target group JID
     * @param newDescription the new description, or {@code null} to clear
     * @throws NullPointerException     if {@code group} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote WAWebGroupModifyInfoJob.setGroupDescription delegates to
     * {@code WASmaxOutGroupsSetDescriptionRequest.makeSetDescriptionRequest}.
     * The WA Web call also passes {@code descriptionId} and
     * {@code descriptionPrev} revision identifiers; Cobalt does not
     * currently track those revisions, so it omits both attributes,
     * which is the {@code OPTIONAL} behaviour on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyInfoJob", exports = "setGroupDescription",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeGroupDescription(Jid group, String newDescription) {
        Objects.requireNonNull(group, "group cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var descriptionBuilder = new NodeBuilder()
                .description("description"); // WASmaxOutGroupsSetDescriptionRequest: smax("description", {id, prev, delete}, body)
        if (newDescription != null) { // WAWebGroupModifyInfoJob.setGroupDescription: r != null ? ... : hasDescriptionDeleteTrue:true
            var bodyNode = new NodeBuilder()
                    .description("body") // WASmaxOutGroupsSetDescriptionRequest.makeSetDescriptionRequestDescriptionBody
                    .content(newDescription) // bodyElementValue
                    .build();
            descriptionBuilder.content(bodyNode);
        } else {
            descriptionBuilder.attribute("delete", "true"); // WAWebGroupModifyInfoJob.setGroupDescription: hasDescriptionDeleteTrue => delete="true"
        }
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", group) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(t)
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(descriptionBuilder.build());
        sendNode(iqNode); // WASmaxGroupsSetDescriptionRPC.sendSetDescriptionRPC
    }

    /**
     * Changes the profile picture of a WhatsApp group to the given image
     * bytes.
     *
     * <p>Sends a {@code w:profile:picture} {@code iq} of type {@code set}
     * addressed to {@link JidServer#user()} with the {@code target}
     * attribute set to the group JID and a {@code <picture type="image">}
     * body whose binary content is the JPEG/PNG payload.
     *
     * @param group      the non-{@code null} target group JID
     * @param imageBytes the non-{@code null} raw picture bytes (usually a
     *                   256x256 JPEG)
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote Default export of WAWebSendProfilePictureJob emits an
     * {@code <iq type="set" xmlns="w:profile:picture" to=S_WHATSAPP_NET
     * target=groupJid>} (the {@code target} attribute is dropped for
     * user-picture updates). Cobalt mirrors that shape for groups by
     * always including the {@code target} attribute.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendProfilePictureJob", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeGroupPicture(Jid group, byte[] imageBytes) {
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(imageBytes, "imageBytes cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        sendPictureIq(group, imageBytes); // WAWebSendProfilePictureJob.default: wap("picture", {type: "image"}, a)
    }

    /**
     * Removes the profile picture of a WhatsApp group.
     *
     * <p>Sends a {@code w:profile:picture} {@code iq} of type {@code set}
     * addressed to {@link JidServer#user()} with the {@code target}
     * attribute set to the group JID and no body, matching WA Web's
     * {@code removePicture} branch where the {@code picture} child is
     * {@code null}.
     *
     * @param group the non-{@code null} target group JID
     * @throws NullPointerException     if {@code group} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote Default export of WAWebSendProfilePictureJob emits the
     * same IQ shape as {@link #changeGroupPicture(Jid, byte[])} with the
     * body set to {@code null}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendProfilePictureJob", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void removeGroupPicture(Jid group) {
        Objects.requireNonNull(group, "group cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        sendPictureIq(group, null); // WAWebSendProfilePictureJob.default: a=null -> no picture body
    }

    /**
     * Sends a {@code w:profile:picture} IQ with an optional picture body.
     *
     * <p>This private helper backs {@link #changeGroupPicture(Jid, byte[])}
     * and {@link #removeGroupPicture(Jid)}. It always emits the
     * {@code target} attribute because the caller restricts usage to
     * group JIDs.
     *
     * @param group      the target group JID
     * @param imageBytes the picture bytes to upload, or {@code null} to
     *                   clear the picture
     */
    private void sendPictureIq(Jid group, byte[] imageBytes) {
        var iqBuilder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:profile:picture") // WAWebSendProfilePictureJob: xmlns: "w:profile:picture"
                .attribute("to", JidServer.user()) // WAWebSendProfilePictureJob: to: S_WHATSAPP_NET
                .attribute("target", group) // WAWebSendProfilePictureJob: target: isGroup(t) ? CHAT_JID(t) : DROP_ATTR
                .attribute("type", "set"); // WAWebSendProfilePictureJob: type: "set"
        if (imageBytes != null) { // WAWebSendProfilePictureJob: a ? wap("picture", {type: "image"}, a) : null
            var pictureNode = new NodeBuilder()
                    .description("picture")
                    .attribute("type", "image")
                    .content(imageBytes)
                    .build();
            iqBuilder.content(pictureNode);
        }
        sendNode(iqBuilder); // WAWebSendProfilePictureJob: deprecatedSendIq(iqNode, s)
    }

    /**
     * Toggles a group or community setting between
     * {@link ChatPolicy#ANYONE} (all members) and
     * {@link ChatPolicy#ADMINS} (admins only).
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} whose body
     * depends on the chosen {@link GroupSetting} and {@code policy} value,
     * mirroring WA Web's
     * {@code WAWebGroupModifyInfoJob.setGroupProperty} switch:
     * <ul>
     *   <li>{@link GroupSetting#ANNOUNCEMENT}: emits
     *       {@code <announcement/>} for {@link ChatPolicy#ADMINS} or
     *       {@code <not_announcement/>} otherwise.</li>
     *   <li>{@link GroupSetting#RESTRICT}: emits {@code <locked/>} for
     *       {@link ChatPolicy#ADMINS} or {@code <unlocked/>} otherwise.</li>
     *   <li>{@link GroupSetting#NO_FREQUENTLY_FORWARDED}: emits
     *       {@code <no_frequently_forwarded/>} for
     *       {@link ChatPolicy#ADMINS} or {@code <frequently_forwarded_ok/>}
     *       otherwise.</li>
     *   <li>{@link GroupSetting#MEMBERSHIP_APPROVAL_MODE}: emits a
     *       {@code <membership_approval_mode>} wrapper around a
     *       {@code <group_join/>} toggle.</li>
     *   <li>{@link GroupSetting#REPORT_TO_ADMIN_MODE}: emits
     *       {@code <allow_admin_reports/>} or
     *       {@code <not_allow_admin_reports/>}.</li>
     *   <li>{@link GroupSetting#ALLOW_NON_ADMIN_SUB_GROUP_CREATION}:
     *       emits
     *       {@code <allow_non_admin_sub_group_creation/>} or
     *       {@code <not_allow_non_admin_sub_group_creation/>}.</li>
     * </ul>
     *
     * <p>The {@link GroupSetting#EPHEMERAL} case is not supported through
     * this method because disappearing messages are managed via
     * {@link #setEphemeralTimer(Jid, ChatEphemeralTimer)}. Settings that
     * WA Web routes through the MEX {@code mexUpdateGroupPropertyJob}
     * GraphQL endpoint instead of a direct IQ
     * ({@link GroupSetting#MEMBER_ADD_MODE},
     * {@link GroupSetting#MEMBER_LINK_MODE},
     * {@link GroupSetting#MEMBER_SHARE_GROUP_HISTORY_MODE},
     * {@link GroupSetting#LIMIT_SHARING}) throw
     * {@link UnsupportedOperationException}.
     *
     * @param group   the non-{@code null} target group JID
     * @param setting the non-{@code null} setting to update
     * @param policy  the non-{@code null} target policy
     * @throws NullPointerException          if any argument is {@code null}
     * @throws IllegalArgumentException      if the JID is not a
     *                                       group/community
     * @throws UnsupportedOperationException if the setting is not dispatched
     *                                       through a direct {@code w:g2}
     *                                       IQ by WA Web
     *
     * @implNote WAWebGroupModifyInfoJob.setGroupProperty switches on
     * {@code GROUP_SETTING_TYPE} and dispatches a SMAX
     * {@code SetPropertyRequest} via
     * {@code WASmaxOutGroupsSetPropertyRequest.makeSetPropertyRequest}.
     * The policy is encoded as a pair of mutually exclusive boolean flags
     * in WA Web ({@code hasLocked}/{@code hasUnlocked} etc.), which Cobalt
     * collapses into a single child-element emission per branch.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyInfoJob", exports = "setGroupProperty",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeGroupSetting(Jid group, GroupSetting setting, ChatPolicy policy) {
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(setting, "setting cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var enabled = policy == ChatPolicy.ADMINS; // WAWebGroupModifyInfoJob.setGroupProperty: i === 1 (enabled) / i !== 1 (disabled)
        Node body = switch (setting) {
            case ANNOUNCEMENT -> new NodeBuilder() // WAWebGroupModifyInfoJob.setGroupProperty: hasAnnouncement=i===1 / hasNotAnnouncement=i!==1
                    .description(enabled ? "announcement" : "not_announcement")
                    .build();
            case RESTRICT -> new NodeBuilder() // WAWebGroupModifyInfoJob.setGroupProperty: hasLocked=i===1 / hasUnlocked=i!==1
                    .description(enabled ? "locked" : "unlocked")
                    .build();
            case NO_FREQUENTLY_FORWARDED -> new NodeBuilder() // WAWebGroupModifyInfoJob.setGroupProperty: hasNoFrequentlyForwarded=i===1 / hasFrequentlyForwardedOk=i!==1
                    .description(enabled ? "no_frequently_forwarded" : "frequently_forwarded_ok")
                    .build();
            case MEMBERSHIP_APPROVAL_MODE -> new NodeBuilder() // WAWebGroupModifyInfoJob.setGroupProperty: membershipApprovalModeArgs = i===1 ? enabled : disabled
                    .description("membership_approval_mode") // WASmaxOutGroupsSetPropertyRequest.makeSetPropertyRequestMembershipApprovalMode
                    .content(new NodeBuilder()
                            .description("group_join") // WASmaxOutGroupsMembershipApprovalGroupJoinModeMixin: <group_join state=.../> style
                            .attribute("state", enabled ? "on" : "off") // parseChatMetadata reads attribute state="on" | "off"
                            .build())
                    .build();
            case REPORT_TO_ADMIN_MODE -> new NodeBuilder() // WAWebGroupModifyInfoJob.setGroupProperty: hasAllowAdminReports=i===1 / hasNotAllowAdminReports=i!==1
                    .description(enabled ? "allow_admin_reports" : "not_allow_admin_reports")
                    .build();
            case ALLOW_NON_ADMIN_SUB_GROUP_CREATION -> new NodeBuilder() // WAWebGroupModifyInfoJob.setGroupProperty: hasAllowNonAdminSubGroupCreation / hasNotAllowNonAdminSubGroupCreation
                    .description(enabled ? "allow_non_admin_sub_group_creation" : "not_allow_non_admin_sub_group_creation")
                    .build();
            case EPHEMERAL -> throw new UnsupportedOperationException(
                    "Use setEphemeralTimer(Jid, ChatEphemeralTimer) to configure ephemeral messaging");
            case MEMBER_ADD_MODE, MEMBER_LINK_MODE, MEMBER_SHARE_GROUP_HISTORY_MODE, LIMIT_SHARING ->
                    throw new UnsupportedOperationException(
                            "Setting %s is dispatched through the MEX update-group-property pipeline and is not supported via a w:g2 IQ".formatted(setting));
        };
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", group) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(t)
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(body);
        sendNode(iqNode); // WASmaxGroupsSetPropertyRPC.sendSetPropertyRPC
    }

    /**
     * Changes who may add new participants to a WhatsApp group between
     * {@link GroupAddMode#ADMIN_ADD} and {@link GroupAddMode#ALL_MEMBER_ADD}.
     *
     * <p>Sends a {@code w:g2} {@code iq} of type {@code set} addressed to
     * the group JID whose body is a {@code <member_add_mode>} element
     * whose text content is the mode's {@link GroupAddMode#data() wire
     * identifier} ({@code "admin_add"} or {@code "all_member_add"}).
     *
     * @param group the non-{@code null} target group JID
     * @param mode  the non-{@code null} new add-mode
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote This field is normally toggled through WA Web's
     * {@code WAWebMexUpdateGroupPropertyJob.mexUpdateGroupPropertyJob}
     * MEX endpoint. The equivalent wire format for a direct IQ is
     * described by {@code WASmaxOutGroupsAdminAddModeMixin.mergeAdminAddModeMixin}
     * and {@code WASmaxOutGroupsAllMembersAddModeMixin.mergeAllMembersAddModeMixin},
     * which emit {@code <member_add_mode>admin_add</member_add_mode>}
     * and {@code <member_add_mode>all_member_add</member_add_mode>}
     * respectively. Cobalt dispatches that child inline as a
     * {@code w:g2} IQ.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyInfoJob", exports = "setGroupProperty",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeGroupMemberAddMode(Jid group, GroupAddMode mode) {
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var memberAddModeNode = new NodeBuilder()
                .description("member_add_mode") // WASmaxOutGroupsAdminAddModeMixin / WASmaxOutGroupsAllMembersAddModeMixin: smax("member_add_mode", null, "admin_add" | "all_member_add")
                .content(mode.data())
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", group) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(t)
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(memberAddModeNode);
        sendNode(iqNode); // ADAPTED: WA Web dispatches this field via WAWebMexUpdateGroupPropertyJob; Cobalt sends the equivalent w:g2 IQ directly
    }

    //</editor-fold>

    public WhatsAppClient addListener(WhatsAppClientListener listener) {
        store.addListener(listener);
        return this;
    }

    public WhatsAppClient removeListener(WhatsAppClientListener listener) {
        store.removeListener(listener);
        return this;
    }

    public WhatsAppClient addChatsListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<Chat>> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onChats(WhatsAppClient arg0, Collection<Chat> arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addContactsListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<Contact>> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onContacts(WhatsAppClient arg0, Collection<Contact> arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addStatusListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<ChatMessageInfo>> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onStatus(WhatsAppClient arg0, Collection<ChatMessageInfo> arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addNodeSentListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Node> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onNodeSent(WhatsAppClient arg0, Node arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addLoggedInListener(WhatsappClientListenerConsumer.Unary<WhatsAppClient> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onLoggedIn(WhatsAppClient arg0) {
                consumer.accept(arg0);
            }
        });
        return this;
    }

    public WhatsAppClient addCallListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, CallOffer> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onCall(WhatsAppClient arg0, CallOffer arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addWebHistorySyncPastParticipantsListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, Collection<GroupPastParticipant>> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onWebHistorySyncPastParticipants(WhatsAppClient arg0, Jid arg1, Collection<GroupPastParticipant> arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addDisconnectedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, WhatsAppClientDisconnectReason> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onDisconnected(WhatsAppClient arg0, WhatsAppClientDisconnectReason arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addWebAppPrimaryFeaturesListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, List<String>> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onWebAppPrimaryFeatures(WhatsAppClient arg0, List<String> arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addContactPresenceListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, Jid> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onContactPresence(WhatsAppClient arg0, Jid arg1, Jid arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addNewslettersListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<Newsletter>> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onNewsletters(WhatsAppClient arg0, Collection<Newsletter> arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addNodeReceivedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Node> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onNodeReceived(WhatsAppClient arg0, Node arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addWebAppStateActionListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, SyncAction, String> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onWebAppStateAction(WhatsAppClient arg0, SyncAction arg1, String arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addWebHistorySyncMessagesListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Chat, Boolean> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onWebHistorySyncMessages(WhatsAppClient arg0, Chat arg1, boolean arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addNewStatusListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, ChatMessageInfo> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onNewStatus(WhatsAppClient arg0, ChatMessageInfo arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addAccountTypeChangedListener(WhatsappClientListenerConsumer.Quaternary<WhatsAppClient, Jid, ADVEncryptionType, ADVEncryptionType> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onAccountTypeChanged(WhatsAppClient arg0, Jid arg1, ADVEncryptionType arg2, ADVEncryptionType arg3) {
                consumer.accept(arg0, arg1, arg2, arg3);
            }
        });
        return this;
    }

    public WhatsAppClient addAboutChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, String, String> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onAboutChanged(WhatsAppClient arg0, String arg1, String arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addNewMessageListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, MessageInfo> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onNewMessage(WhatsAppClient arg0, MessageInfo arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addMessageDeletedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, MessageInfo, Boolean> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onMessageDeleted(WhatsAppClient arg0, MessageInfo arg1, boolean arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addPrivacySettingChangedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, PrivacySettingEntry> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onPrivacySettingChanged(WhatsAppClient arg0, PrivacySettingEntry arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addWebHistorySyncProgressListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Integer, Boolean> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onWebHistorySyncProgress(WhatsAppClient arg0, int arg1, boolean arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addProfilePictureChangedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Jid> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onProfilePictureChanged(WhatsAppClient arg0, Jid arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addMessageStatusListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, MessageInfo> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onMessageStatus(WhatsAppClient arg0, MessageInfo arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addNameChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, String, String> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onNameChanged(WhatsAppClient arg0, String arg1, String arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addMessageReplyListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, MessageInfo, MessageInfo> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onMessageReply(WhatsAppClient arg0, MessageInfo arg1, MessageInfo arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addDeviceIdentityChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, Set<Jid>> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onDeviceIdentityChanged(WhatsAppClient arg0, Jid arg1, Set<Jid> arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addNewContactListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Contact> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onNewContact(WhatsAppClient arg0, Contact arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addContactBlockedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Jid> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onContactBlocked(WhatsAppClient arg0, Jid arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }

    public WhatsAppClient addContactTextStatusListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, ContactTextStatus> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onContactTextStatus(WhatsAppClient arg0, Jid arg1, ContactTextStatus arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addLocaleChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, String, String> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onLocaleChanged(WhatsAppClient arg0, String arg1, String arg2) {
                consumer.accept(arg0, arg1, arg2);
            }
        });
        return this;
    }

    public WhatsAppClient addRegistrationCodeListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Long> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onRegistrationCode(WhatsAppClient arg0, long arg1) {
                consumer.accept(arg0, arg1);
            }
        });
        return this;
    }
}
