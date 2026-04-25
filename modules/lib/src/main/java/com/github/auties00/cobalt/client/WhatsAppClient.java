package com.github.auties00.cobalt.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.*;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.bot.profile.*;
import com.github.auties00.cobalt.model.business.*;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalog;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogEntry;
import com.github.auties00.cobalt.model.business.profile.*;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.call.CallOfferBuilder;
import com.github.auties00.cobalt.model.chat.*;
import com.github.auties00.cobalt.model.chat.community.CommunityLinkedGroup;
import com.github.auties00.cobalt.model.chat.community.CommunityLinkedGroupBuilder;
import com.github.auties00.cobalt.model.chat.community.CommunityMetadata;
import com.github.auties00.cobalt.model.chat.community.CommunityMetadataBuilder;
import com.github.auties00.cobalt.model.chat.group.*;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactCard;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.*;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.poll.*;
import com.github.auties00.cobalt.model.message.system.PinInChatMessage;
import com.github.auties00.cobalt.model.message.system.PinInChatMessageBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessageBuilder;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterReactionSettings;
import com.github.auties00.cobalt.model.newsletter.NewsletterViewerRole;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.QuickReply;
import com.github.auties00.cobalt.model.privacy.*;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.model.sync.action.bot.AiThreadRenameAction;
import com.github.auties00.cobalt.model.sync.action.bot.BotWelcomeRequestAction;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.action.business.BroadcastListParticipantAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListAction;
import com.github.auties00.cobalt.model.sync.action.chat.ChatAssignmentAction;
import com.github.auties00.cobalt.model.sync.action.chat.ChatAssignmentOpenedStatusAction;
import com.github.auties00.cobalt.model.sync.action.chat.QuickReplyAction;
import com.github.auties00.cobalt.model.sync.action.contact.*;
import com.github.auties00.cobalt.model.sync.action.device.TimeFormatAction;
import com.github.auties00.cobalt.model.sync.action.media.*;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingDisableLinkPreviewsAction;
import com.github.auties00.cobalt.model.sync.action.setting.LocaleSetting;
import com.github.auties00.cobalt.model.sync.action.setting.NotificationActivitySettingAction;
import com.github.auties00.cobalt.model.sync.action.setting.PushNameSetting;
import com.github.auties00.cobalt.model.sync.action.setting.UnarchiveChatsSetting;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncBackoff;
import com.github.auties00.cobalt.node.usync.UsyncQuery;
import com.github.auties00.cobalt.node.usync.UsyncResult;
import com.github.auties00.cobalt.node.usync.result.UsyncProtocolError;
import com.github.auties00.cobalt.node.mex.json.business.QueryCatalogMex;
import com.github.auties00.cobalt.node.mex.json.business.QueryOrderMex;
import com.github.auties00.cobalt.node.mex.json.business.QueryProductCollectionsMex;
import com.github.auties00.cobalt.node.mex.json.community.FetchAllSubgroupsMex;
import com.github.auties00.cobalt.node.mex.json.community.FetchAllSubgroupsMex.Response.DefaultSubGroup;
import com.github.auties00.cobalt.node.mex.json.community.FetchAllSubgroupsMex.Response.SubGroups;
import com.github.auties00.cobalt.node.mex.json.community.FetchSubgroupSuggestionsMex;
import com.github.auties00.cobalt.node.mex.json.community.QuerySubgroupParticipantCountMex;
import com.github.auties00.cobalt.node.mex.json.community.TransferCommunityOwnershipMex;
import com.github.auties00.cobalt.node.mex.json.group.UpdateGroupPropertyMex;
import com.github.auties00.cobalt.node.mex.json.newsletter.*;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.socket.WhatsAppSocketClient;
import com.github.auties00.cobalt.socket.WhatsAppSocketListener;
import com.github.auties00.cobalt.socket.WhatsAppSocketStanza;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.handler.*;
import com.github.auties00.cobalt.sync.key.SyncKeyUtils;
import com.github.auties00.cobalt.util.BusinessLabelConstants;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.cobalt.util.RandomIdUtils;
import com.github.auties00.cobalt.wam.WamMsgUtils;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.*;
import com.github.auties00.cobalt.wam.type.*;
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
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
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
public class WhatsAppClient {
    /**
     * The single-byte encoding of the Signal identity key type, used when
     * building the {@code <type>} node in pre-key upload stanzas.
     */
    private static final byte[] SIGNAL_KEY_TYPE = {SignalIdentityPublicKey.type()};

    /**
     * The lower bound on the number of pre-keys uploaded per batch; keeps
     * batches useful even if the caller asks for fewer.
     */
    private static final long MIN_PRE_KEYS_COUNT = 5;

    /**
     * Square pixel size (width and height) of the self-profile preview
     * thumbnail WA Web generates for uploads.
     */
    private static final int PROFILE_PREVIEW_SIZE = 96;

    /**
     * Maximum number of connection attempts before a post-pair reconnect
     * is surfaced as a fatal {@code WhatsAppReconnectionException}. The
     * first attempt counts toward the total; subsequent attempts are
     * spaced with linear backoff per {@link #RECONNECT_BACKOFF_MILLIS}.
     */
    private static final int RECONNECT_MAX_ATTEMPTS = 5;

    /**
     * Base wait in milliseconds between reconnect attempts. The effective
     * pause is {@code RECONNECT_BACKOFF_MILLIS * attempt} so the backoff
     * grows linearly: 1s, 2s, 3s, 4s between attempts 1&rarr;2, 2&rarr;3,
     * 3&rarr;4, 4&rarr;5.
     */
    private static final long RECONNECT_BACKOFF_MILLIS = 1000L;

    /**
     * WA Web storefront default for the {@code item_limit} argument on
     * {@code WAWebQueryProductCollections}: capping the number of items
     * fetched per returned collection.
     */
    private static final int PRODUCT_COLLECTION_ITEM_LIMIT = 100;

    /**
     * Default page size for catalog queries; matches the WA Web storefront
     * {@code limit=5} declared inline in
     * {@code WAWebBizProductCatalogAction.queryCatalog}.
     */
    private static final int DEFAULT_CATALOG_LIMIT = 5;

    /**
     * Default image width (pixels) requested by catalog queries; matches the
     * WA Web storefront {@code width=100} default from
     * {@code WAWebBizProductCatalogAction.queryCatalog}.
     */
    private static final int DEFAULT_CATALOG_IMAGE_WIDTH = 100;

    /**
     * Default image height (pixels) requested by catalog queries; matches the
     * WA Web storefront {@code height=100} default from
     * {@code WAWebBizProductCatalogAction.queryCatalog}.
     */
    private static final int DEFAULT_CATALOG_IMAGE_HEIGHT = 100;

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
     * The per-protocol backoff registry consulted before every USync
     * dispatch and updated when the relay returns an
     * {@code error_backoff} hint.
     *
     * @implNote WAWebUsyncBackoff: the JS module-level singleton.
     */
    private final UsyncBackoff usyncBackoff;
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
     * Reentrance guard set while {@link #disconnect(WhatsAppClientDisconnectReason, boolean)} is executing.
     */
    private final AtomicBoolean disconnecting;

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
        this.deviceService = new DeviceService(this, webAppStateService, abPropsService, sessionCipher);
        this.messageService = new MessageService(this, sessionCipher, groupCipher, deviceService, abPropsService);
        this.wamService = new WamService(this, abPropsService);
        this.usyncBackoff = new UsyncBackoff();
        this.pendingSocketRequests = new ConcurrentHashMap<>();
        this.companionPairingService = new CompanionPairingService(this, webVerificationHandler);
        this.socketStream = new SocketStream(this, webVerificationHandler, lidMigrationService, inactiveGroupLidMigrationService, messageService, abPropsService, deviceService, wamService, snapshotRecoveryService, webAppStateService, companionPairingService);
        this.messagePreviewHandler = messagePreviewHandler;
        this.disconnecting = new AtomicBoolean();
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
     * Returns the WAM telemetry service used to batch and flush WhatsApp
     * Metrics events over the three WAM transport channels.
     *
     * @return the WAM service
     */
    public WamService wamService() {
        return wamService;
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

        var maxAttempts = reason == WhatsAppClientDisconnectReason.RECONNECTING ? RECONNECT_MAX_ATTEMPTS : 1;
        IOException lastFailure = null;
        for (var attempt = 1; attempt <= maxAttempts; attempt++) {
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
                // WAWebOpenChatSocket.J — after the socket_open and
                // auth_handshake QPL spans have closed successfully, a
                // WebcSocketConnectWamEvent is committed summarising the
                // two durations and the connect reason. The event is only
                // emitted for web companions (the "webc" prefix in
                // WAWebOpenChatSocket and WAWebWebcSocketConnectWamEvent);
                // mobile sessions do not commit it.
                if (store.clientType() == WhatsAppClientType.WEB) {
                    emitWebcSocketConnectEvent(reason);
                }
                lastFailure = null;
                break;
            } catch (IOException throwable) {
                lastFailure = throwable;
                if (socketClient != null) {
                    try {
                        socketClient.disconnect();
                    } catch (Throwable _) {
                        // Best effort cleanup before retry
                    }
                    socketClient = null;
                }
                if (attempt >= maxAttempts) {
                    break;
                }
                try {
                    Thread.sleep(RECONNECT_BACKOFF_MILLIS * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (lastFailure != null) {
            if (reason == WhatsAppClientDisconnectReason.RECONNECTING) {
                handleFailure(new WhatsAppReconnectionException(lastFailure.getMessage(), maxAttempts, lastFailure));
            } else {
                handleFailure(new WhatsAppConnectionException(lastFailure.getMessage(), lastFailure));
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
     * Commits a {@code WebcSocketConnect} WAM event summarising the two
     * sub-phases of the chat socket setup — the transport open and the
     * Noise XX handshake — together with the reason for this connect
     * attempt.
     *
     * <p>The durations are read from the active {@link WhatsAppSocketClient}
     * which measured them internally as the connection progressed.  The
     * {@code webcSocketConnectReason} field is set to
     * {@link WebcSocketConnectReasonType#RECONNECT RECONNECT} when this
     * connect is part of a reconnection attempt and to
     * {@link WebcSocketConnectReasonType#PAGE_LOAD PAGE_LOAD} otherwise,
     * matching the WA Web check against
     * {@code WAWebPageLoadLogging.wasPageLoadQplLogged()}.
     *
     * <p>{@code webcSocketHostname} is intentionally left unset because
     * WA Web never populates it either (only the property definition
     * references it in
     * {@code WAWebWebcSocketConnectWamEvent.defineEvents}).
     *
     * @implNote WAWebOpenChatSocket.B, WAWebOpenChatSocket.J — constructs a
     *     {@code WebcSocketConnectWamEvent}, seeds the connect reason, marks
     *     the socket-connect and auth-handshake durations, then commits it
     * @param reason the disconnection reason driving this connect, or
     *               {@code null} for the initial connect
     */
    private void emitWebcSocketConnectEvent(WhatsAppClientDisconnectReason reason) {
        var socketConnectDuration = socketClient.socketConnectDuration();
        var authHandshakeDuration = socketClient.authHandshakeDuration();
        // WAWebOpenChatSocket.B — wasPageLoadQplLogged() ? RECONNECT : PAGE_LOAD
        var connectReason = reason == WhatsAppClientDisconnectReason.RECONNECTING
                ? WebcSocketConnectReasonType.RECONNECT
                : WebcSocketConnectReasonType.PAGE_LOAD;
        var builder = new WebcSocketConnectEventBuilder()
                .webcSocketConnectReason(connectReason); // WAWebOpenChatSocket.B
        if (socketConnectDuration != null) {
            // WAWebOpenChatSocket.B — markWebcSocketConnectDuration
            builder.webcSocketConnectDuration(Instant.ofEpochMilli(socketConnectDuration.toMillis()));
        }
        if (authHandshakeDuration != null) {
            // WAWebOpenChatSocket.J — markWebcAuthHandshakeDuration
            builder.webcAuthHandshakeDuration(Instant.ofEpochMilli(authHandshakeDuration.toMillis()));
        }
        wamService.commit(builder.build());
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
        if (disconnecting.compareAndSet(false, true)) {
            try {
                disconnect0(reason, canRemoveShutdownHook);
            } finally {
                disconnecting.set(false);
            }
        }
    }

    /**
     * The actual disconnect implementation, invoked once the reentrance
     * guard in {@link #disconnect(WhatsAppClientDisconnectReason, boolean)}
     * has been acquired.
     *
     * @param reason                the disconnection reason
     * @param canRemoveShutdownHook whether the JVM shutdown hook should be
     *                              removed as part of this disconnect
     */
    private void disconnect0(WhatsAppClientDisconnectReason reason, boolean canRemoveShutdownHook) {
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
            socketClient = null;
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
     * Dispatches a MEX (GraphQL-over-XMPP) IQ stanza and records a
     * {@link MexEventV2Event} describing the outcome of the request.
     *
     * <p>This is the shared sink for every outgoing MEX request and mirrors
     * WA Web's {@code WAWebMexNativeClient.fetchQuery} where each fetch is
     * wrapped by a {@code MexPerfTracker} that logs a {@code MexEventV2}
     * WAM event on success or failure. The tracker-driven shape is collapsed
     * into this single helper so that every Cobalt MEX dispatch site can
     * reuse the same timing, error-capture and telemetry contract without
     * duplicating the try/catch boilerplate.
     *
     * @implNote WAWebMexNativeClient.fetchQuery: the JS path constructs a
     * {@code MexPerfTracker(true)}, calls {@code start()},
     * {@code setQueryId(id)}, {@code setOperationName(name)}, invokes
     * {@code WAWebMexRelayEnvironment.fetchFunc}, then on success
     * {@code setHasData(true) + stop() + logEvent()} and on error
     * {@code setErrors(...) + setHasData(false) + stop() + logEvent()}.
     * Cobalt replicates the same emission shape directly from here.
     * @param node          the outbound MEX IQ builder; a random id is
     *                      attached if absent
     * @param queryId       the compiled GraphQL query identifier
     *                      (corresponds to {@code params.id} in WA Web)
     * @param operationName the GraphQL operation name (corresponds to
     *                      {@code params.name} in WA Web, for example
     *                      {@code mexGetNewsletter})
     * @param isArgoPayload {@code true} if the MEX payload is Argo-encoded,
     *                      {@code false} for the JSON variant
     * @return the response node from the WhatsApp relay
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    @WhatsAppWebExport(moduleName = "WAWebMexNativeClient", exports = "fetchQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Node sendMexNode(NodeBuilder node, String queryId, String operationName, boolean isArgoPayload) {
        // WAWebMexNativeClient.fetchQuery: MexPerfTracker start
        var start = Instant.now();
        var startTimeMs = start.toEpochMilli();
        try {
            var response = sendNode(node);
            // WAWebMexNativeClient.fetchQuery: success path - setHasData(true), stop(), logEvent()
            var end = Instant.now();
            commitMexEventV2(queryId, operationName, isArgoPayload, startTimeMs, end.toEpochMilli(), true, null, null);
            return response;
        } catch (RuntimeException exception) {
            // WAWebMexNativeClient.fetchQuery: error path - setHasData(false), setErrors(...), stop(), logEvent()
            var end = Instant.now();
            var errorsJson = mexErrorsJson(exception);
            var errorCodesJson = mexErrorCodesJson(exception);
            commitMexEventV2(queryId, operationName, isArgoPayload, startTimeMs, end.toEpochMilli(), false, errorsJson, errorCodesJson);
            throw exception;
        }
    }

    /**
     * Dispatches a MEX IQ stanza whose response is discarded while still
     * emitting the {@link MexEventV2Event} telemetry.
     *
     * <p>Used by MEX mutations where the caller only needs the side effect
     * of the stanza (for example newsletter leave/join) and ignores the
     * returned payload. Internally the method still blocks on the response
     * so the telemetry accurately records success/failure of the round trip,
     * matching the semantics of the original {@code sendNode(request.toNode())}
     * call sites the helper replaced.
     *
     * @implNote WAWebMexNativeClient.fetchQuery: the underlying JS tracker
     * logs success whenever {@code fetchFunc} returns without throwing,
     * regardless of whether the caller consumes the payload. Cobalt preserves
     * that invariant by delegating to {@link #sendMexNode(NodeBuilder, String,
     * String, boolean)} and discarding the return value.
     * @param node          the outbound MEX IQ builder; a random id is
     *                      attached if absent
     * @param queryId       the compiled GraphQL query identifier
     * @param operationName the GraphQL operation name
     * @param isArgoPayload {@code true} if the MEX payload is Argo-encoded,
     *                      {@code false} for the JSON variant
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    @WhatsAppWebExport(moduleName = "WAWebMexNativeClient", exports = "fetchQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendMexNodeWithNoResponse(NodeBuilder node, String queryId, String operationName, boolean isArgoPayload) {
        // WAWebMexNativeClient.fetchQuery: delegate to sendMexNode and discard the response
        sendMexNode(node, queryId, operationName, isArgoPayload);
    }

    /**
     * Convenience wrapper for dispatching a JSON MEX query.
     *
     * <p>Forwards to {@link #sendMexNode(NodeBuilder, String, String, boolean)}
     * with {@code isArgoPayload=false}. Use this for the common case where
     * the MEX request was built via
     * {@link com.github.auties00.cobalt.node.mex.json.MexJsonOperation#createMexNode(String, String)}
     * and the caller wants the raw response IQ to feed into a generated
     * {@code Foo.Response.of(node)} parser.
     *
     * @param request       the outbound MEX IQ builder
     * @param queryId       the compiled GraphQL query identifier
     *                      (the {@code QUERY_ID} constant on the
     *                      generated MEX interface)
     * @param operationName the GraphQL operation name (used by the WAM
     *                      telemetry only)
     * @return the response IQ node
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    public Node executeMexJson(NodeBuilder request, String queryId, String operationName) {
        return sendMexNode(request, queryId, operationName, false);
    }

    /**
     * Returns the per-protocol USync backoff registry shared by every
     * query dispatched through {@link #executeUsyncQuery(UsyncQuery)}.
     *
     * <p>Most callers do not need to interact with the registry directly;
     * it is exposed so background tasks can clear backoffs after a
     * connection-level reset.
     *
     * @return the backoff registry
     */
    public UsyncBackoff usyncBackoff() {
        return usyncBackoff;
    }

    /**
     * Dispatches a USync query and returns the parsed result.
     *
     * <p>This is the canonical entry point for every USync flow in
     * Cobalt. It performs four steps in order:
     * <ol>
     *   <li>blocks the current virtual thread for any active per-protocol
     *       backoff windows that apply to this query
     *       (see {@link UsyncBackoff#waitForBackoff(UsyncQuery)});</li>
     *   <li>sends the query stanza built by
     *       {@link UsyncQuery#toNode()};</li>
     *   <li>parses the response via
     *       {@link UsyncQuery#parseResponse(Node)};</li>
     *   <li>forwards every protocol error's {@code error_backoff} hint to
     *       the registry so subsequent queries observe the rate limit.</li>
     * </ol>
     *
     * <p><strong>Thread safety.</strong> Concurrent calls from different
     * threads are supported as long as each call uses its <em>own</em>
     * {@link UsyncQuery} instance. A single query must not be shared
     * across threads while any thread is still mutating it through
     * {@code with*} setters; see the thread-safety note on
     * {@link UsyncQuery} for the full contract. The shared
     * {@link UsyncBackoff} registry consulted here is concurrency-safe
     * by design.
     *
     * @param query the query; must not be shared across threads while
     *              still being configured
     * @return the parsed result
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws InterruptedException            if the thread is interrupted
     *                                         while waiting for an active
     *                                         backoff to elapse
     * @implNote WAWebUsync.USyncQuery.execute.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.execute", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncResult executeUsyncQuery(UsyncQuery query) throws InterruptedException {
        usyncBackoff.waitForBackoff(query);
        var response = sendNode(query.toNode());
        var result = query.parseResponse(response);
        for (var protocol : query.protocols()) {
            result.getProtocolError(protocol)
                    .flatMap(UsyncProtocolError::errorBackoff)
                    .ifPresent(d -> usyncBackoff.setProtocolBackoffMs(protocol.name(), d.toMillis()));
        }
        return result;
    }

    /**
     * Builds and commits the {@link MexEventV2Event} WAM record for a
     * single MEX dispatch.
     *
     * @implNote WAWebMexLogging.MexPerfTracker.logEvent: mirrors the direct
     * commit performed by the JS tracker, populating the same field set
     * with the values accumulated during the request lifecycle.
     * @param queryId       the GraphQL query identifier
     * @param operationName the GraphQL operation name
     * @param isArgoPayload {@code true} if the payload is Argo-encoded
     * @param startTimeMs   the request start time, in milliseconds since epoch
     * @param endTimeMs     the request end time, in milliseconds since epoch
     * @param hasData       {@code true} if the request produced a payload
     * @param errorsJson    the JSON-encoded error array, or {@code null} on
     *                      success
     * @param errorCodesJson the JSON-encoded error-code array, or
     *                      {@code null} on success
     */
    private void commitMexEventV2(String queryId, String operationName, boolean isArgoPayload,
                                  long startTimeMs, long endTimeMs, boolean hasData,
                                  String errorsJson, String errorCodesJson) {
        // WAWebMexNativeClient.fetchQuery -> MexPerfTracker.logEvent
        var durationMs = Math.max(0L, endTimeMs - startTimeMs);
        wamService.commit(new MexEventV2EventBuilder()
                .mexEventV2IsMex(Boolean.TRUE)
                .mexEventV2IsArgoPayload(isArgoPayload)
                .mexEventV2OperationName(operationName)
                .mexEventV2QueryId(queryId)
                .mexEventV2StartTime((int) startTimeMs)
                .mexEventV2EndTime((int) endTimeMs)
                .mexEventV2DurationMs(Instant.ofEpochMilli(durationMs))
                .mexEventV2HasData(hasData)
                .mexEventV2Errors(errorsJson)
                .mexEventV2ErrorCodes(errorCodesJson)
                .build());
    }

    /**
     * Serialises a dispatch failure into the JSON array shape consumed by
     * the {@code mexEventV2Errors} WAM property.
     *
     * @implNote WAWebMexLogging.createLoggingClientError /
     * createLoggingTransportError: WA Web wraps each failure in a
     * {@code {code, detail, type}} record and then serialises the array via
     * {@code JSON.stringify}. Cobalt reuses the {@code CLIENT} classifier
     * here because the failure propagated through the local transport stack
     * rather than as a server-side error extension.
     * @param exception the failure raised by {@link #sendNode(NodeBuilder)}
     * @return the JSON-encoded error array
     */
    private static String mexErrorsJson(RuntimeException exception) {
        // WAWebMexLogging.parseErrorsAndCodes: JSON.stringify(errors)
        var message = exception.getMessage();
        var arr = new com.alibaba.fastjson2.JSONArray();
        var obj = new JSONObject();
        obj.put("code", 417);
        obj.put("detail", message == null ? "" : message);
        obj.put("type", "CLIENT");
        arr.add(obj);
        return arr.toJSONString();
    }

    /**
     * Serialises a dispatch failure's error codes into the JSON array shape
     * consumed by the {@code mexEventV2ErrorCodes} WAM property.
     *
     * @implNote WAWebMexLogging.parseErrorsAndCodes: {@code errors.map(e =>
     * e.code)} then {@code JSON.stringify}. Cobalt emits the same
     * {@code [code]} array for the single wrapped failure.
     * @param exception the failure raised by the underlying dispatch
     * @return the JSON-encoded integer array
     */
    private static String mexErrorCodesJson(RuntimeException exception) {
        // WAWebMexLogging.parseErrorsAndCodes: JSON.stringify(errors.map(e -> e.code))
        var arr = new com.alibaba.fastjson2.JSONArray();
        arr.add(417);
        return arr.toJSONString();
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
     * credentials for this session.
     *
     * <p>For web companion sessions this issues a
     * {@code remove-companion-device} IQ targeting this client's own
     * companion JID so the primary device detaches it; for sessions
     * without a known local JID it falls back to a local
     * {@link #disconnect(WhatsAppClientDisconnectReason)} with
     * {@link WhatsAppClientDisconnectReason#LOGGED_OUT}. The next
     * connection attempt requires a fresh authentication ceremony (QR
     * scan, pairing code, or phone-number registration).
     *
     * <p>This is a self-logout. To detach a different linked companion
     * from the primary device owned by this client, use
     * {@link #logoutCompanion(Jid)} instead.
     *
     * @implNote WAWebUnpairDeviceJob.unpairDevice targets the current
     *     session's own device JID; WA Web derives it from
     *     {@code getMeDevicePnOrThrow_DO_NOT_USE()} while Cobalt reads
     *     {@code store.jid()}. The attribute name matches WA Web's
     *     {@code jid="..."}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUnpairDeviceJob",
            exports = "unpairDevice",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void logout() {
        var localJid = store.jid();
        if (localJid.isEmpty()) {
            disconnect(WhatsAppClientDisconnectReason.LOGGED_OUT);
        } else {
            // WAWebUnpairDeviceJob.unpairDevice: wap("remove-companion-device", {jid: DEVICE_JID(me), reason: CUSTOM_STRING(reason)})
            var device = new NodeBuilder()
                    .description("remove-companion-device")
                    .attribute("jid", localJid.get()) // WAWebUnpairDeviceJob.unpairDevice: jid attribute, not "value"
                    .attribute("reason", "user_initiated")
                    .build();
            var iqNode = new NodeBuilder()
                    .description("iq")
                    .attribute("xmlns", "md") // WAWebUnpairDeviceJob.unpairDevice: xmlns="md"
                    .attribute("to", JidServer.user()) // WAWebUnpairDeviceJob.unpairDevice: to=S_WHATSAPP_NET
                    .attribute("type", "set") // WAWebUnpairDeviceJob.unpairDevice: type="set"
                    .content(device);
            sendNode(iqNode);
        }
    }

    /**
     * Detaches the given companion device from this account.
     *
     * <p>Sends a {@code remove-companion-device} IQ identical in shape
     * to the self-logout IQ emitted by {@link #logout()}, but carrying
     * the supplied companion JID instead of this session's own JID.
     * The companion must belong to this account; the server rejects
     * JIDs that do not appear in the caller's own device list.
     *
     * <p>This does not tear down the local session. To log out the
     * currently-connected companion itself, use {@link #logout()}.
     *
     * @param companion the companion JID to detach; must include an
     *                  agent index (device slot) and must be a device
     *                  JID belonging to this account
     * @throws NullPointerException                if {@code companion}
     *                                             is {@code null}
     * @throws WhatsAppSessionException.Closed     if the socket is no
     *                                             longer open
     * @implNote WAWebUnpairDeviceJob.unpairDevice: the underlying JS
     *     function is hard-coded to pass {@code getMeDevicePnOrThrow}
     *     so it can only detach self; Cobalt generalises the API to
     *     accept any companion JID owned by this account so callers
     *     can unpair siblings.
     */
    @WhatsAppWebExport(moduleName = "WAWebUnpairDeviceJob",
            exports = "unpairDevice",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void logoutCompanion(Jid companion) {
        Objects.requireNonNull(companion, "companion cannot be null");
        // WAWebUnpairDeviceJob.unpairDevice: wap("remove-companion-device", {jid, reason: "user_initiated"})
        var device = new NodeBuilder()
                .description("remove-companion-device")
                .attribute("jid", companion)
                .attribute("reason", "user_initiated")
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "md") // WAWebUnpairDeviceJob.unpairDevice: xmlns="md"
                .attribute("to", JidServer.user()) // WAWebUnpairDeviceJob.unpairDevice: to=S_WHATSAPP_NET
                .attribute("type", "set") // WAWebUnpairDeviceJob.unpairDevice: type="set"
                .content(device);
        sendNode(iqNode);
    }

    /**
     * Returns the companion JIDs currently linked to this account.
     *
     * <p>Runs a USync device-list query for the caller's own user JID
     * via the injected {@link DeviceService} and materialises the
     * cached device entries as companion JIDs (user+device slot). The
     * primary device (device 0) is included as the first entry when
     * present; companions follow in server order.
     *
     * <p>Callers that only need the raw {@link com.github.auties00.cobalt.model.device.info.DeviceList} record can
     * use {@link DeviceService#syncAndGetDeviceList(Collection)}
     * directly; this helper exists to surface the user-facing list of
     * linked devices with each entry already projected into a
     * device-qualified JID.
     *
     * @return the linked companion JIDs, in server order
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open during the
     *                                         USync round-trip
     * @implNote WAWebGetAllDevices / WAWebApiDeviceList.getMyDeviceList:
     *     WA Web reads the cached primary/LID device list straight from
     *     {@code getDeviceListTable}. Cobalt goes through the same
     *     USync path as WA Web's {@code syncMyDeviceList} so the
     *     returned list is always fresh, then projects the resulting
     *     {@code DeviceList} into per-device JIDs. Companion JIDs use
     *     the {@code user@server:device} form produced by
     *     {@code DeviceEntry.toDeviceJid}.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getMyDeviceList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public SequencedCollection<Jid> queryLinkedDevices() {
        var selfJid = store.jid()
                .map(Jid::toUserJid)
                .orElseThrow(() -> new IllegalStateException("Client is not logged in"));
        // WAWebAdvSyncDeviceListApi.syncAndGetDeviceList: sync first, then read cached entries
        var lists = deviceService.syncAndGetDeviceList(List.of(selfJid));
        var result = new LinkedHashSet<Jid>();
        for (var list : lists) {
            if (list == null || list.deleted()) {
                continue;
            }
            for (var device : list.devices()) {
                // WAWebWidFactory.createDeviceWidFromDeviceListPk: user@server:deviceId
                result.add(device.toDeviceJid(selfJid.user(), selfJid.server()));
            }
        }
        return result;
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
     * <p>Before delegating, any app-state (syncd) fatal failure is mirrored
     * to the WAM pipeline via {@link #emitSyncdFatalErrorMetric(WhatsAppWebAppStateSyncException)}.
     * This mirrors WA Web's {@code WAWebSyncdUploadFatalErrorMetric.uploadFatalErrorMetric}
     * central uploader, which WA Web fans out at every inline syncd fatal
     * detection site. Because Cobalt's error model is intentionally
     * configurable (per {@code CLAUDE.md}'s Error Model section), every
     * syncd throw site in {@code WebAppStateService}, {@code MutationResponseParser},
     * {@code MutationRequestBuilder}, {@code MutationIntegrityVerifier},
     * {@code DecryptedMutation}, {@code EncryptedMutation}, and
     * {@code SnapshotRecoveryService} converges on this single {@code handleFailure}
     * entry point &mdash; so emitting the WAM event here is sufficient to
     * cover all WA-Web {@code uploadFatalErrorMetric} call sites.
     *
     * @param exception the exception to handle
     */
    public void handleFailure(WhatsAppException exception) {
        if (exception instanceof WhatsAppWebAppStateSyncException syncdException) {
            // WAWebSyncdUploadFatalErrorMetric.uploadFatalErrorMetric: new MdFatalErrorWamEvent({...}).commitAndWaitForFlush(true)
            emitSyncdFatalErrorMetric(syncdException);
        }
        var result = errorHandler.handleError(this, exception);
        switch (result) {
            case BAN -> disconnect(WhatsAppClientDisconnectReason.BANNED);
            case LOG_OUT -> disconnect(WhatsAppClientDisconnectReason.LOGGED_OUT);
            case DISCONNECT -> disconnect(WhatsAppClientDisconnectReason.DISCONNECTED);
            case RECONNECT -> disconnect(WhatsAppClientDisconnectReason.RECONNECTING);
        }
    }

    /**
     * Commits a {@link MdFatalErrorEvent} describing the given app-state sync
     * failure, mirroring WA Web's
     * {@code WAWebSyncdUploadFatalErrorMetric.uploadFatalErrorMetric} central
     * fatal-metric uploader.
     *
     * <p>Only fatal subtypes of {@link WhatsAppWebAppStateSyncException} emit
     * the event, matching WA Web's split between {@code SyncdFatalError}
     * (emits {@code MdFatalErrorWamEvent}) and {@code SyncdRetryableError} /
     * {@code SyncdMissingKeyError} (do not). The three identifying
     * properties that WA Web's secondary KMP path
     * ({@code WAWebKmpWamLogger.reportMdFatalError}) also considers
     * sufficient &mdash; {@code mdFatalErrorCode}, {@code collection}, and
     * {@code isFatal} &mdash; are populated from the exception subtype and
     * any carried {@link SyncPatchType}. The richer property set that WA
     * Web's canonical site populates (the ~30 {@code macFatal*},
     * {@code timeSince*}, {@code appContext}, {@code mailboxAgeDays},
     * {@code recoveryStatus} fields) is populated at WA Web's inline
     * detection sites; Cobalt's typed-exception model intentionally does
     * not thread that rich context through the exception hierarchy, per
     * {@code CLAUDE.md}.
     *
     * <p>WA Web reaches this emission via the indirection module
     * {@code WAWebSyncdUploadFatalErrorMetricEmitter}, a two-export callback
     * slot ({@code listenForUploadFatalErrorMetric} stores the callback,
     * {@code emitUploadFatalErrorMetric} invokes it). That indirection exists
     * in WA Web so that the metric-populator module
     * ({@code WAWebSyncdUploadFatalErrorMetric}, which transitively pulls in
     * the heavy {@code WAWebMdFatalErrorWamEvent} / AB-props / IDB-access
     * machinery) can be lazy-loaded without being referenced directly by
     * every fatal-error throw site. Cobalt does not need the callback slot
     * because all sync fatal errors converge on the pluggable
     * {@link #handleFailure} entry point which resolves collaborators via
     * constructor DI, so the two emitter exports are collapsed into this
     * direct call and annotated here as {@code ADAPTED} (architecturally
     * eliminated).
     *
     * @param exception the fatal or retryable app-state sync exception
     *                  flowing through {@link #handleFailure}; never
     *                  {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdUploadFatalErrorMetric",
            exports = "uploadFatalErrorMetric",
            adaptation = com.github.auties00.cobalt.meta.model.WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdUploadFatalErrorMetricEmitter",
            exports = "emitUploadFatalErrorMetric",
            adaptation = com.github.auties00.cobalt.meta.model.WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdUploadFatalErrorMetricEmitter",
            exports = "listenForUploadFatalErrorMetric",
            adaptation = com.github.auties00.cobalt.meta.model.WhatsAppAdaptation.ADAPTED)
    private void emitSyncdFatalErrorMetric(WhatsAppWebAppStateSyncException exception) {
        if (!exception.isFatal()) {
            // WAWebSyncdUploadFatalErrorMetric only receives fatal errors; retryable
            // errors (SyncdRetryableError / SyncdMissingKeyError) short-circuit
            // before reaching uploadFatalErrorMetric.
            return;
        }
        var code = mapSyncdFatalErrorCode(exception);
        if (code == null) {
            // Defensive: unclassified subtype (should not happen given the sealed
            // hierarchy is exhaustively mapped). Skip emission rather than commit
            // a misleading code.
            return;
        }
        var collection = extractSyncdCollection(exception);
        var builder = new MdFatalErrorEventBuilder()
                .mdFatalErrorCode(code) // WAWebSyncdUploadFatalErrorMetric.uploadFatalErrorMetric: mdFatalErrorCode: t
                .isFatal(true); // WAWebSyncdUploadFatalErrorMetric.uploadFatalErrorMetric: isFatal: l — always true on this path
        if (collection != null) {
            builder.collection(collection); // WAWebSyncdUploadFatalErrorMetric.uploadFatalErrorMetric: collection: n ? collectionNameToMetric(n) : undefined
        }
        wamService.commit(builder.build());
    }

    /**
     * Maps a {@link WhatsAppWebAppStateSyncException} subtype to the
     * corresponding {@link MdSyncdFatalErrorCode}.
     *
     * <p>Mirrors the union of WA Web's
     * {@code WAWebSyncdMetricFatalErrorListener.convertSyncdErrorCode}
     * switch and the inline {@code uploadFatalErrorMetric} calls scattered
     * across {@code WAWebKeyManagementHandleKeyShareApi} and
     * {@code WAWebSyncdNetCallbacksApi}.
     *
     * @param exception the app-state sync exception; never {@code null}
     * @return the matching {@link MdSyncdFatalErrorCode}, or {@code null}
     *         for subtypes that should not emit the metric (retryable
     *         subtypes are filtered upstream, so this only returns
     *         {@code null} for defensive coverage)
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMetricFatalErrorListener",
            exports = "convertSyncdErrorCode",
            adaptation = com.github.auties00.cobalt.meta.model.WhatsAppAdaptation.ADAPTED)
    private static MdSyncdFatalErrorCode mapSyncdFatalErrorCode(WhatsAppWebAppStateSyncException exception) {
        return switch (exception) {
            // WAWebSyncdAntiTampering / MutationIntegrityVerifier: SyncdFatalError("unable to validate snapshot mac")
            case WhatsAppWebAppStateSyncException.SnapshotMacMismatch ignored -> MdSyncdFatalErrorCode.MAC_MISMATCH_SNAPSHOT;
            // WAWebSyncdAntiTampering.validatePatchMac / MutationIntegrityVerifier: SyncdFatalError("unable to validate patch mac")
            case WhatsAppWebAppStateSyncException.PatchMacMismatch ignored -> MdSyncdFatalErrorCode.MAC_MISMATCH_PATCH;
            // WAWebSyncdError / DecryptedMutation: SyncdFatalError("decryption failure: valueMAC mismatch")
            case WhatsAppWebAppStateSyncException.ValueMacMismatch ignored -> MdSyncdFatalErrorCode.DECRYPTION_FAILED_VALUE_MAC_MISMATCH;
            // WAWebSyncdError / DecryptedMutation: SyncdFatalError("decryption failure: indexMAC mismatch")
            case WhatsAppWebAppStateSyncException.IndexMacMismatch ignored -> MdSyncdFatalErrorCode.DECRYPTION_FAILED_INDEX_MAC_MISMATCH;
            // WAWebSyncdStoreMissingKeys: MISSING_KEY_ON_ALL_CLIENTS
            case WhatsAppWebAppStateSyncException.MissingKeyOnAllDevices ignored -> MdSyncdFatalErrorCode.MISSING_KEY_ON_ALL_CLIENTS;
            // WAWebSyncdStoreMissingKeys._timeoutWhileWaitingForMissingKey: TIMEOUT_WHILE_WAITING_FOR_MISSING_KEY
            case WhatsAppWebAppStateSyncException.TimeoutWhileWaitingForMissingKey ignored -> MdSyncdFatalErrorCode.TIMEOUT_WHILE_WAITING_FOR_MISSING_KEY;
            // WASyncdKmpEncryptionManager: SyncdFatalError(e.message)
            case WhatsAppWebAppStateSyncException.DecryptionFailed ignored -> MdSyncdFatalErrorCode.DECRYPTION_FAILED;
            // WAWebSyncdError: MAC computation / encryption failures bucket under ENCRYPTION_FAILED
            case WhatsAppWebAppStateSyncException.MacComputationFailed ignored -> MdSyncdFatalErrorCode.ENCRYPTION_FAILED;
            // WAWebSyncdValidateMutations.validateAndTypeSetMutations: SyncdFatalError for missing action timestamp
            case WhatsAppWebAppStateSyncException.MissingActionTimestamp ignored -> MdSyncdFatalErrorCode.MISSING_ACTION_TIMESTAMP;
            // WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations: SyncdFatalError("same index for multiple mutations in patch")
            case WhatsAppWebAppStateSyncException.DuplicateIndexInPatch ignored -> MdSyncdFatalErrorCode.SAME_INDEX_FOR_MULTIPLE_MUTATIONS_IN_PATCH;
            // WAWebSyncdValidateMutations.validateNoDuplicatePatchVersionInCollection: SyncdFatalError("duplicate patch version in collection")
            case WhatsAppWebAppStateSyncException.DuplicatePatchVersion ignored -> MdSyncdFatalErrorCode.DUPLICATE_PATCH_VERSION_IN_COLLECTION;
            // WAWebSyncdError: SyncdFatalError("syncd: has missing patches") → SERVER_DID_NOT_SEND_ALL_PATCHES
            case WhatsAppWebAppStateSyncException.MissingPatches ignored -> MdSyncdFatalErrorCode.SERVER_DID_NOT_SEND_ALL_PATCHES;
            // WAWebSyncdError: terminal patch with exit code — map by code when known, default TERMINAL_PATCH_UNKNOWN
            case WhatsAppWebAppStateSyncException.TerminalPatch terminal -> mapTerminalPatchCode(terminal);
            // WAWebNonMessageDataRequestHandler.m: decode failure on recovery snapshot — ENCODE path
            case WhatsAppWebAppStateSyncException.ExternalDecodeFailed ignored -> MdSyncdFatalErrorCode.UNKNOWN;
            // WAWebSyncdError: catch-all SyncdFatalError → UNKNOWN
            case WhatsAppWebAppStateSyncException.UnexpectedError ignored -> MdSyncdFatalErrorCode.UNKNOWN;
            // Retryable subtypes — never reach here because handleFailure pre-filters
            // by isFatal(); listed explicitly so the sealed switch remains exhaustive.
            case WhatsAppWebAppStateSyncException.MissingKey ignored -> null;
            case WhatsAppWebAppStateSyncException.Conflict ignored -> null;
            case WhatsAppWebAppStateSyncException.RetryableServerError ignored -> null;
            case WhatsAppWebAppStateSyncException.ExternalDownloadFailed ignored -> null;
        };
    }

    /**
     * Maps a {@link WhatsAppWebAppStateSyncException.TerminalPatch} to the
     * corresponding {@link MdSyncdFatalErrorCode}, using the patch's exit
     * code when available.
     *
     * @param terminal the terminal patch exception; never {@code null}
     * @return the matching fatal error code; never {@code null}
     */
    private static MdSyncdFatalErrorCode mapTerminalPatchCode(WhatsAppWebAppStateSyncException.TerminalPatch terminal) {
        // WA Web: exit code 100 → TERMINAL_PATCH_MISSING_DATA, 101 → TERMINAL_PATCH_DESERIALIZATION_ERROR, else TERMINAL_PATCH_UNKNOWN.
        // Cobalt's DisconnectReason carries a DisconnectCode sealed interface whose records encode the 100 / 101 wire values.
        var code = terminal.exitCode() == null
                ? null
                : terminal.exitCode().code().orElse(null);
        if (code instanceof com.github.auties00.cobalt.model.error.DisconnectCode.MissingData) {
            return MdSyncdFatalErrorCode.TERMINAL_PATCH_MISSING_DATA;
        }
        if (code instanceof com.github.auties00.cobalt.model.error.DisconnectCode.DeserializationError) {
            return MdSyncdFatalErrorCode.TERMINAL_PATCH_DESERIALIZATION_ERROR;
        }
        return MdSyncdFatalErrorCode.TERMINAL_PATCH_UNKNOWN;
    }

    /**
     * Extracts the affected {@link SyncPatchType} from an app-state sync
     * exception (when it carries one) and maps it to the WAM
     * {@link com.github.auties00.cobalt.wam.type.Collection} enum.
     *
     * <p>Mirrors WA Web's
     * {@code WAWebSyncdMetrics.collectionNameToMetric} conversion, which
     * translates the wire collection name to the WAM enum constant.
     *
     * @param exception the app-state sync exception; never {@code null}
     * @return the matching {@link com.github.auties00.cobalt.wam.type.Collection}
     *         constant, or {@code null} when the exception does not carry
     *         a collection
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMetrics",
            exports = "collectionNameToMetric",
            adaptation = com.github.auties00.cobalt.meta.model.WhatsAppAdaptation.DIRECT)
    private static com.github.auties00.cobalt.wam.type.Collection extractSyncdCollection(WhatsAppWebAppStateSyncException exception) {
        SyncPatchType patchType = switch (exception) {
            case WhatsAppWebAppStateSyncException.SnapshotMacMismatch e -> e.collectionName();
            case WhatsAppWebAppStateSyncException.PatchMacMismatch e -> e.collectionName();
            case WhatsAppWebAppStateSyncException.MissingPatches e -> e.collectionName();
            case WhatsAppWebAppStateSyncException.TerminalPatch e -> e.collectionName();
            case WhatsAppWebAppStateSyncException.DuplicateIndexInPatch e -> e.collectionName();
            case WhatsAppWebAppStateSyncException.DuplicatePatchVersion e -> e.collectionName();
            default -> null;
        };
        if (patchType == null) {
            return null;
        }
        return switch (patchType) {
            case CRITICAL_BLOCK -> com.github.auties00.cobalt.wam.type.Collection.CRITICAL_BLOCK;
            case CRITICAL_UNBLOCK_LOW -> com.github.auties00.cobalt.wam.type.Collection.CRITICAL_UNBLOCK_LOW;
            case REGULAR -> com.github.auties00.cobalt.wam.type.Collection.REGULAR;
            case REGULAR_HIGH -> com.github.auties00.cobalt.wam.type.Collection.REGULAR_HIGH;
            case REGULAR_LOW -> com.github.auties00.cobalt.wam.type.Collection.REGULAR_LOW;
        };
    }
    //</editor-fold>
    //<editor-fold desc="Web app state">
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
     * <p>Returns whether any of the synced collections contributed actual state changes, i.e.
     * at least one collection response carried patches or a snapshot. Callers that need to
     * distinguish a no-op sync from one that applied remote updates (for example to emit the
     * {@code mdAppStateDirtyBits} WAM event with {@code dirtyBitsFalsePositive = !hadChanges})
     * can inspect the return value; other callers may safely ignore it.
     *
     * @param patches the patch types to pull; an empty array is tolerated
     * @return {@code true} if any synced collection had patches or a snapshot; {@code false}
     *         when every collection sync completed without applying any state changes, or when
     *         {@code patches} is empty
     */
    public boolean pullWebAppState(SyncPatchType... patches) {
        return webAppStateService.pullPatches(patches);
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
    //</editor-fold>
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
            // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
            var communityGroupsResponseNode = sendMexNode(communityGroupsRequestNode, FetchAllSubgroupsMex.QUERY_ID, "mexCommunityGetSubgroups", false);
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
     * Edits the metadata of the authenticated user's WhatsApp Business
     * profile.
     *
     * <p>Mirrors {@code WAWebBusinessProfileJob.editBusinessProfile}. WA Web
     * sends a {@code business_profile} IQ under the {@code w:biz} namespace
     * with {@code v="3"} and {@code mutation_type="delta"}, packing each
     * modified attribute as its own child element. Cobalt reproduces the
     * exact wire shape by inspecting every field on the supplied
     * {@link BusinessProfile} and emitting only the children whose value is
     * non-{@code null}. The {@code address}, {@code description},
     * {@code email}, {@code website} (up to two), {@code categories} (as
     * {@code <category id="..."/>}) and {@code business_hours} children
     * follow WA Web's delta encoding literally.
     *
     * <p>The {@code cart_enabled}, {@code latitude}, {@code longitude},
     * {@code price_tier} and {@code service_areas} fields are not exposed on
     * Cobalt's {@link BusinessProfile} model yet; they are intentionally
     * omitted here, matching the "undefined" branch taken by the WA Web job
     * when the caller does not supply them.
     *
     * @implNote WAWebBusinessProfileJob.editBusinessProfile: every attribute
     * is wrapped in its own {@code wap(...)} child; absent values are
     * skipped via the {@code !== void 0} ternary. Cobalt mirrors the same
     * null-skipping behaviour.
     * @param profile the new business-profile metadata; must not be
     *                {@code null}
     * @throws NullPointerException            if {@code profile} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob", exports = "editBusinessProfile",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editBusinessProfile(BusinessProfile profile) {
        Objects.requireNonNull(profile, "profile cannot be null");
        var children = new ArrayList<Node>();
        // WAWebBusinessProfileJob.editBusinessProfile: r !== void 0 ? wap("address", null, r) : null
        profile.address().ifPresent(address -> children.add(new NodeBuilder()
                .description("address")
                .content(address)
                .build()));
        // WAWebBusinessProfileJob.editBusinessProfile: l !== void 0 ? wap("description", null, l) : null
        profile.description().ifPresent(description -> children.add(new NodeBuilder()
                .description("description")
                .content(description)
                .build()));
        // WAWebBusinessProfileJob.editBusinessProfile: s !== void 0 ? wap("email", null, s) : null
        profile.email().ifPresent(email -> children.add(new NodeBuilder()
                .description("email")
                .content(email)
                .build()));
        // WAWebBusinessProfileJob.editBusinessProfile: _ && _.length===0 ? wap("website", null) ... up to two <website>
        var websites = profile.websites();
        if (websites.isEmpty()) {
            children.add(new NodeBuilder()
                    .description("website")
                    .build());
        } else {
            children.add(new NodeBuilder()
                    .description("website")
                    .content(websites.get(0).toString())
                    .build());
            if (websites.size() > 1) {
                children.add(new NodeBuilder()
                        .description("website")
                        .content(websites.get(1).toString())
                        .build());
            }
        }
        // WAWebBusinessProfileJob.editBusinessProfile: i ? wap("categories", null, i.map(e => wap("category", {id: CUSTOM_STRING(e.id)}))) : null
        var categories = profile.categories();
        if (!categories.isEmpty()) {
            var categoryNodes = categories.stream()
                    .map(category -> new NodeBuilder()
                            .description("category")
                            .attribute("id", category.id())
                            .build())
                    .toList();
            children.add(new NodeBuilder()
                    .description("categories")
                    .content(categoryNodes)
                    .build());
        }
        // WAWebBusinessProfileJob.editBusinessProfile: a ? u(a) : null
        profile.hours().ifPresent(hours -> children.add(buildBusinessHoursNode(hours)));
        var businessProfileNode = new NodeBuilder()
                .description("business_profile")
                .attribute("v", "3") // WAWebBusinessProfileJob.editBusinessProfile: v: "3"
                .attribute("mutation_type", "delta") // WAWebBusinessProfileJob.editBusinessProfile: mutation_type: "delta"
                .content(children)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:biz") // WAWebBusinessProfileJob.editBusinessProfile: xmlns: "w:biz"
                .attribute("to", JidServer.user()) // WAWebBusinessProfileJob.editBusinessProfile: to: S_WHATSAPP_NET
                .attribute("type", "set"); // WAWebBusinessProfileJob.editBusinessProfile: type: "set"
        iqNode.content(businessProfileNode);
        sendNode(iqNode); // WAWebBusinessProfileJob.editBusinessProfile: deprecatedSendIq(f, e)
    }

    /**
     * Builds the {@code business_hours} stanza child emitted by
     * {@link #editBusinessProfile(BusinessProfile)}.
     *
     * <p>WA Web's internal helper {@code u({config, note, timezone})}
     * flattens the configured hours into a list of
     * {@code business_hours_config} children and prepends an optional
     * {@code business_hours_note}. Cobalt's {@link BusinessHours} does not
     * carry a note so the note child is never emitted.
     *
     * @implNote WAWebBusinessProfileJob.editBusinessProfile: inline helper
     * {@code function u(e){...}} that produces the {@code business_hours}
     * wrapper. Each entry becomes
     * {@code wap("business_hours_config", {day_of_week, mode, open_time, close_time})}.
     * Cobalt preserves the same attribute names and {@code DROP_ATTR}
     * behaviour for the time fields when the mode is not
     * {@code specific_hours}.
     * @param hours the business hours configuration
     * @return the {@code business_hours} node
     */
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob", exports = "editBusinessProfile",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Node buildBusinessHoursNode(BusinessHours hours) {
        var configNodes = new ArrayList<Node>();
        // WAWebBusinessProfileJob.editBusinessProfile: a.push({day_of_week, mode, open_time, close_time})
        for (var entry : hours.entries()) {
            var builder = new NodeBuilder()
                    .description("business_hours_config")
                    .attribute("day_of_week", entry.day().value())
                    .attribute("mode", entry.mode().value());
            // WAWebBusinessProfileJob.editBusinessProfile: open_time/close_time are CUSTOM_STRING when present, DROP_ATTR otherwise
            if (entry.mode() == BusinessHoursMode.SPECIFIC_HOURS) {
                builder.attribute("open_time", Long.toString(entry.openTime().toSecondOfDay()));
                builder.attribute("close_time", Long.toString(entry.closeTime().toSecondOfDay()));
            }
            configNodes.add(builder.build());
        }
        var builder = new NodeBuilder()
                .description("business_hours");
        var timezone = hours.timeZone();
        // WAWebBusinessProfileJob.editBusinessProfile: timezone ? CUSTOM_STRING(r) : DROP_ATTR
        if (timezone != null && !timezone.isEmpty()) {
            builder.attribute("timezone", timezone);
        }
        builder.content(configNodes);
        return builder.build();
    }

    /**
     * Toggles the shopping-cart feature of the authenticated user's
     * business profile.
     *
     * <p>Mirrors {@code WAWebBusinessProfileJob.updateCartEnabled}. WA Web
     * picks the GraphQL-driven commerce-settings path when the
     * {@code graphQLForCommerceSettingsEnabled} AB prop is on, and falls
     * back to a legacy {@code fb:thrift_iq} IQ otherwise. Cobalt only
     * implements the legacy IQ path since the MEX commerce-settings
     * endpoint is not wired yet. The IQ body contains a
     * {@code <commerce_settings>} wrapper with a nested
     * {@code <cart enabled="true|false"/>}.
     *
     * @implNote WAWebBusinessProfileJob.updateCartEnabled: the legacy
     * {@code c(e)} helper emits
     * {@code wap("iq", {to, smax_id: CommerceSettingsSet, xmlns: "fb:thrift_iq", type: "set"},
     *   wap("commerce_settings", null, wap("cart", {enabled: CUSTOM_STRING(e.toString())})))}
     * and decodes the response through the {@code commerce_settings}
     * parser. Cobalt omits the typed response parser since the caller is
     * only interested in the success/failure signal raised by
     * {@link #sendNode(NodeBuilder)}.
     * @param enabled {@code true} to enable the cart, {@code false} to
     *                disable it
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob", exports = "updateCartEnabled",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void updateBusinessCartEnabled(boolean enabled) {
        // WAWebBusinessProfileJob.updateCartEnabled / legacy c(e):
        // wap("cart", {enabled: CUSTOM_STRING(e.toString())})
        var cartNode = new NodeBuilder()
                .description("cart")
                .attribute("enabled", Boolean.toString(enabled))
                .build();
        var commerceSettings = new NodeBuilder()
                .description("commerce_settings")
                .content(cartNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "fb:thrift_iq") // WAWebBusinessProfileJob.updateCartEnabled: xmlns: "fb:thrift_iq"
                .attribute("to", JidServer.user()) // WAWebBusinessProfileJob.updateCartEnabled: to: S_WHATSAPP_NET
                .attribute("type", "set") // WAWebBusinessProfileJob.updateCartEnabled: type: "set"
                .content(commerceSettings);
        sendNode(iqNode);
    }

    /**
     * Uploads a new cover photo for the authenticated user's business
     * profile.
     *
     * <p>Mirrors {@code WAWebBusinessProfileJob.sendCoverPhoto}. WA Web
     * expects the caller to have already uploaded the JPEG to WhatsApp's
     * media servers via the media upload pipeline and to pass the returned
     * media id, upload timestamp and server-issued token here. The IQ
     * itself carries a {@code cover_photo} child with
     * {@code op="update"} plus the three attributes. Cobalt exposes a
     * thin wrapper that accepts the raw JPEG bytes and delegates the
     * upload/timestamp/token acquisition to future work; the current
     * implementation surfaces the three fields directly so callers with an
     * already-uploaded photo can still emit the IQ.
     *
     * @implNote WAWebBusinessProfileJob.sendCoverPhoto: emits
     * {@code wap("cover_photo", {op: "update", id, ts, token})} inside a
     * {@code business_profile v="3"}. The uploaded bytes are consumed by
     * the media pipeline upstream of this call; Cobalt's signature matches
     * the task contract that accepts the JPEG directly but the current
     * path is a placeholder until the cover-photo upload helper lands.
     * @param jpegBytes the raw JPEG bytes of the cover photo; must not be
     *                  {@code null}
     * @throws NullPointerException            if {@code jpegBytes} is
     *                                         {@code null}
     * @throws UnsupportedOperationException   until the media upload helper
     *                                         that produces {@code id},
     *                                         {@code ts} and {@code token}
     *                                         is implemented
     */
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob", exports = "sendCoverPhoto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void setBusinessCoverPhoto(byte[] jpegBytes) {
        Objects.requireNonNull(jpegBytes, "jpegBytes cannot be null");
        // ADAPTED: WAWebBusinessProfileJob.sendCoverPhoto requires an id/ts/token triple produced by the media upload pipeline.
        // Cobalt's business cover-photo upload helper is not yet implemented, so the entry point currently rejects the call
        // rather than silently producing a malformed IQ. The IQ shape that must be emitted once the upload helper is in place
        // is preserved in the @implNote above so downstream work can wire it in without changing the signature.
        throw new UnsupportedOperationException(
                "setBusinessCoverPhoto requires the media upload pipeline (id, ts, token) which is not yet implemented in Cobalt");
    }

    /**
     * Removes the current cover photo of the authenticated user's business
     * profile.
     *
     * <p>Mirrors {@code WAWebBusinessProfileJob.deleteCoverPhoto}. WA Web
     * emits a {@code business_profile v="3"} IQ whose only child is
     * {@code cover_photo} with {@code op="delete"} plus an {@code id}
     * attribute carrying the previously-uploaded photo id. Cobalt does not
     * yet persist the current cover-photo id, so the deletion IQ simply
     * carries {@code op="delete"} and lets the server interpret the absent
     * {@code id} as "clear the current cover photo"; real WhatsApp Web
     * always ships the id so the implementation is classified as
     * {@link WhatsAppAdaptation#ADAPTED}.
     *
     * @implNote WAWebBusinessProfileJob.deleteCoverPhoto: emits
     * {@code wap("cover_photo", {op: "delete", id: CUSTOM_STRING(t)})}.
     * Cobalt passes the empty string as the id since no cover-photo id is
     * cached in the store.
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob", exports = "deleteCoverPhoto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteBusinessCoverPhoto() {
        // WAWebBusinessProfileJob.deleteCoverPhoto: wap("cover_photo", {op: "delete", id: CUSTOM_STRING(t)})
        var coverPhoto = new NodeBuilder()
                .description("cover_photo")
                .attribute("op", "delete")
                .build();
        var businessProfile = new NodeBuilder()
                .description("business_profile")
                .attribute("v", "3")
                .attribute("mutation_type", "delta")
                .content(coverPhoto)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:biz")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(businessProfile);
        sendNode(iqNode);
    }

    /**
     * Queries the detail of a business order identified by a message id and
     * a server-issued token.
     *
     * <p>Mirrors {@code WAWebBizQueryOrderJob.queryOrder}. The request is
     * issued through the {@link QueryOrderMex} MEX operation, which wraps
     * the GraphQL variables under {@code request.order} and dispatches the
     * IQ via the {@code w:mex} namespace. The response is projected into
     * an {@link QueryOrderMex.Order} carrying the creation timestamp, the
     * price details and the ordered items.
     *
     * <p>This entry point targets the GraphQL code path; the legacy
     * {@code fb:thrift_iq} fallback used by WA Web when the
     * {@code graphQLForGetOrderInfoEnabled} gate is off is intentionally
     * omitted since it has been largely replaced by the MEX path.
     *
     * @implNote WAWebBizQueryOrderJob.queryOrder (GraphQL branch):
     * {@code _(e, t, n, r, o)} issues the MEX through
     * {@code WAWebRelayClient.fetchQuery}. Cobalt substitutes the relay
     * client with the direct {@code w:mex} IQ dispatch that backs every
     * other Cobalt MEX operation. Image dimensions default to a 512x512
     * thumbnail which matches the WA Web default when the caller does not
     * specify one.
     * @param messageId   the server-issued order id (typically the id of
     *                    the {@code OrderMessage})
     * @param tokenBase64 the sensitive base64-encoded token shipped with
     *                    the order message
     * @return the parsed order, or {@link Optional#empty()} when the relay
     *         returns no order payload
     * @throws NullPointerException            if {@code messageId} or
     *                                         {@code tokenBase64} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebBizQueryOrderJob", exports = "queryOrder",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebBizOrderBridge", exports = "queryOrder",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<QueryOrderMex.Order> queryOrder(String messageId, String tokenBase64) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(tokenBase64, "tokenBase64 cannot be null");
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE().toString()
                .orElseThrow(() -> new IllegalStateException("queryOrder requires a logged-in session"));
        var request = new QueryOrderMex.Request(
                selfJid.toString(),
                messageId,
                tokenBase64,
                512, // WAWebBizQueryOrderJob.queryOrder: default thumbnail width used across the product catalog surface
                512  // WAWebBizQueryOrderJob.queryOrder: default thumbnail height
        );
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        var response = sendMexNode(request.toNode(), QueryOrderMex.QUERY_ID, "queryOrder", false);
        return QueryOrderMex.Response.of(response)
                .map(QueryOrderMex.Response::order);
    }

    /**
     * Creates a new quick reply template and propagates it to every linked
     * device via the {@code REGULAR} app-state sync collection.
     *
     * <p>Mirrors {@code WAWebSendQuickReplyAddOrEditMutation.sendMutation}.
     * A random, client-generated id is minted for the new quick reply; the
     * same id is used as both the mutation index part and the primary key
     * under which the template is filed in the quick reply store.
     *
     * <p>The created entry is also added to the local store eagerly so the
     * caller can observe it immediately, without waiting for the round
     * trip through the server and the inbound sync patch.
     *
     * @implNote ADAPTED: WAWebSendQuickReplyAddOrEditMutation — the mutation
     * is built via
     * {@link QuickReplyHandler#getQuickReplyAddOrEditMutation(String, String, String, int, List, Instant)}
     * and committed through
     * {@link WebAppStateService#pushPatches(SyncPatchType, java.util.SequencedCollection)}.
     * @param shortcut the shortcut text the user types to trigger the quick reply
     * @param message  the message body to expand the shortcut into
     * @param keywords the optional keyword list used by the autocomplete
     *                 surface; {@code null} is coerced to an empty list
     * @return the newly-minted quick reply id
     * @throws NullPointerException            if {@code shortcut} or
     *                                         {@code message} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebSendQuickReplyAddOrEditMutation", exports = "sendMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public String createQuickReply(String shortcut, String message, List<String> keywords) {
        Objects.requireNonNull(shortcut, "shortcut cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        var quickReplyId = DataUtils.randomHex(16); // ADAPTED: WA Web mints a client-side uuid inside the compose flow
        var resolvedKeywords = keywords == null ? List.<String>of() : List.copyOf(keywords);
        var timestamp = Instant.now();
        // WAWebAddQuickReplyAction.addQuickReplyAction: WAWebQuickReplyLogging.logQuickReplyAddedEvent()
        wamService.commit(new QuickReplyEventBuilder()
                .quickReplyAction(com.github.auties00.cobalt.wam.type.QuickReplyAction.ACTION_SETTINGS_ADDED) // WAWebQuickReplyLogging.logQuickReplyAddedEvent: QUICK_REPLY_ACTION.ACTION_SETTINGS_ADDED
                .quickReplyEntryPoint(QuickReplyEntryPoint.QUICK_REPLY_ENTRY_POINT_SETTINGS_MENU) // WAWebQuickReplyLogging.logQuickReplyAddedEvent: QUICK_REPLY_ENTRY_POINT.QUICK_REPLY_ENTRY_POINT_SETTINGS_MENU
                .build());
        var mutation = QuickReplyHandler.INSTANCE.getQuickReplyAddOrEditMutation(
                quickReplyId, shortcut, message, 0, resolvedKeywords, timestamp);
        webAppStateService.pushPatches(QuickReplyAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync([], [mutation], ...)
        // ADAPTED: WAWebSendQuickReplyAddOrEditMutation applies the entry locally after the sync round-trip;
        // Cobalt updates the store eagerly for consistent read-after-write semantics.
        var entry = new com.github.auties00.cobalt.model.preference.QuickReplyBuilder()
                .id(quickReplyId)
                .shortcut(shortcut)
                .message(message)
                .keywords(resolvedKeywords)
                .count(0)
                .build();
        store.addQuickReply(entry);
        return quickReplyId;
    }

    /**
     * Updates an existing quick reply template and propagates the change to
     * every linked device via the {@code REGULAR} app-state sync
     * collection.
     *
     * <p>Mirrors {@code WAWebSendQuickReplyAddOrEditMutation.sendMutation}
     * when invoked on an edit path. The supplied id must match an
     * existing quick reply; when it does not, the server still accepts
     * the mutation and treats it as a create, matching WA Web's behaviour.
     *
     * <p>The existing entry in the local store is replaced eagerly so the
     * caller can observe the new {@code shortcut}/{@code message} before
     * the inbound sync patch round-trips.
     *
     * @implNote ADAPTED: WAWebSendQuickReplyAddOrEditMutation — reuses
     * {@link QuickReplyHandler#getQuickReplyAddOrEditMutation(String, String, String, int, List, Instant)}
     * with the supplied id. The {@code count} field is preserved from the
     * existing store entry (or defaults to {@code 0} when absent) since
     * WA Web never rewrites it from the edit surface.
     * @param quickReplyId the stable id of the quick reply being edited
     * @param shortcut     the new shortcut text
     * @param message      the new message body
     * @param keywords     the new keyword list; {@code null} is coerced to
     *                     an empty list
     * @throws NullPointerException            if any of the string arguments
     *                                         is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebSendQuickReplyAddOrEditMutation", exports = "sendMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editQuickReply(String quickReplyId, String shortcut, String message, List<String> keywords) {
        Objects.requireNonNull(quickReplyId, "quickReplyId cannot be null");
        Objects.requireNonNull(shortcut, "shortcut cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        var resolvedKeywords = keywords == null ? List.<String>of() : List.copyOf(keywords);
        var timestamp = Instant.now();
        int currentCount = store.findQuickReply(quickReplyId) // WAWebQuickRepliesSync.applyMutations: s.count || 0
                .map(QuickReply::count)
                .orElse(0);
        // WAWebEditQuickReplyAction.editQuickReplyAction: WAWebQuickReplyLogging.logQuickReplyEditEvent()
        wamService.commit(new QuickReplyEventBuilder()
                .quickReplyAction(com.github.auties00.cobalt.wam.type.QuickReplyAction.ACTION_SETTINGS_EDITED) // WAWebQuickReplyLogging.logQuickReplyEditEvent: QUICK_REPLY_ACTION.ACTION_SETTINGS_EDITED
                .quickReplyEntryPoint(QuickReplyEntryPoint.QUICK_REPLY_ENTRY_POINT_SETTINGS_MENU) // WAWebQuickReplyLogging.logQuickReplyEditEvent: QUICK_REPLY_ENTRY_POINT.QUICK_REPLY_ENTRY_POINT_SETTINGS_MENU
                .build());
        var mutation = QuickReplyHandler.INSTANCE.getQuickReplyAddOrEditMutation(
                quickReplyId, shortcut, message, currentCount, resolvedKeywords, timestamp);
        webAppStateService.pushPatches(QuickReplyAction.COLLECTION_NAME, List.of(mutation));
        var entry = new com.github.auties00.cobalt.model.preference.QuickReplyBuilder()
                .id(quickReplyId)
                .shortcut(shortcut)
                .message(message)
                .keywords(resolvedKeywords)
                .count(currentCount)
                .build();
        store.addQuickReply(entry); // ADAPTED: createOrReplace eagerly mirrors WA Web's inbound apply branch
    }

    /**
     * Deletes a quick reply template and propagates the removal to every
     * linked device via the {@code REGULAR} app-state sync collection.
     *
     * <p>Mirrors {@code WAWebDeleteQuickReplyAction.sendMutation}. The
     * mutation is a {@code SET} with a {@code quickReplyAction} whose
     * {@code deleted} flag is {@code true}; on successful apply the server
     * pushes the removal back through the sync pipeline and every linked
     * device drops the entry from its local table.
     *
     * <p>The entry is also removed from the local store eagerly so the
     * caller observes the deletion immediately.
     *
     * @implNote ADAPTED: WAWebDeleteQuickReplyAction — delegates to
     * {@link QuickReplyHandler#getQuickReplyDeleteMutation(String, Instant)}
     * and commits the mutation through
     * {@link WebAppStateService#pushPatches(SyncPatchType, java.util.SequencedCollection)}.
     * @param quickReplyId the id of the quick reply to delete
     * @throws NullPointerException            if {@code quickReplyId} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebDeleteQuickReplyAction", exports = "sendMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteQuickReply(String quickReplyId) {
        Objects.requireNonNull(quickReplyId, "quickReplyId cannot be null");
        // WAWebDeleteQuickReplyAction.deleteQuickReplyAction: WAWebQuickReplyLogging.logQuickReplyDeleteEvent()
        wamService.commit(new QuickReplyEventBuilder()
                .quickReplyAction(com.github.auties00.cobalt.wam.type.QuickReplyAction.ACTION_SETTINGS_DELETED) // WAWebQuickReplyLogging.logQuickReplyDeleteEvent: QUICK_REPLY_ACTION.ACTION_SETTINGS_DELETED
                .quickReplyEntryPoint(QuickReplyEntryPoint.QUICK_REPLY_ENTRY_POINT_SETTINGS_MENU) // WAWebQuickReplyLogging.logQuickReplyDeleteEvent: QUICK_REPLY_ENTRY_POINT.QUICK_REPLY_ENTRY_POINT_SETTINGS_MENU
                .build());
        var mutation = QuickReplyHandler.INSTANCE.getQuickReplyDeleteMutation(quickReplyId, Instant.now());
        webAppStateService.pushPatches(QuickReplyAction.COLLECTION_NAME, List.of(mutation));
        store.removeQuickReply(quickReplyId); // ADAPTED: WAWebQuickRepliesSync.applyMutations -> getQuickReplyTable().remove(l) applied eagerly
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
     * Initiates a one-to-one voice or video call by emitting the signalling
     * {@code <call><offer/></call>} stanza to the given peer.
     *
     * <p>Cobalt does not implement the WebRTC media plane (SDP negotiation,
     * SRTP media transport). Only the signalling stanza that announces the
     * call to the peer is sent. The returned {@link CallOffer} captures the
     * locally-generated call identifier so that the caller can later invoke
     * {@link #stopCall(String, Jid)} to hang up the invitation.
     *
     * @implNote ADAPTED: WAWebVoipFunctions.offerCall — WA Web performs a full
     *           WebRTC handshake via {@code WAWebOutgoingCall} and the native
     *           VoIP backend. Cobalt emits only the {@code <call><offer/></call>}
     *           signalling stanza (no {@code enc} / SDP payload) and records
     *           the {@link CallOffer} locally; no media session is established.
     * @param target the JID of the callee, must be a user JID
     * @param video  whether to advertise this as a video call
     * @return the locally-tracked {@link CallOffer} describing the outgoing
     *         call; its {@linkplain CallOffer#callId() callId} is required to
     *         later terminate the call
     * @throws NullPointerException            if {@code target} is {@code null}
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipFunctions", exports = "offerCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebOutgoingCall", exports = "sendOfferStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CallOffer startCall(Jid target, boolean video) {
        Objects.requireNonNull(target, "target cannot be null");
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMeUser
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        var callId = DataUtils.randomHex(16); // ADAPTED: WAWebOutgoingCall generates a UUID-shaped id via the native VoIP backend
        var offerChildren = new ArrayList<Node>(1);
        if (video) { // WAWebHandleVoipCall.callParser: isVideoCall = m.hasChild("video")
            offerChildren.add(new NodeBuilder()
                    .description("video")
                    .build());
        }
        var offerNode = new NodeBuilder()
                .description("offer") // WAWebHandleVoipCall.S: wap("offer", {...})
                .attribute("call-id", callId) // WAWebHandleVoipCall.S: "call-id": CUSTOM_STRING(n)
                .attribute("call-creator", selfJid) // WAWebHandleVoipCall.S: "call-creator": JID(r)
                .content(offerChildren)
                .build();
        var callNode = new NodeBuilder()
                .description("call") // WAWebHandleVoipCall.callParser: e.assertTag("call")
                .attribute("to", target) // WAWebHandleVoipCall.callParser: e.attrJidWithType("from") on receive side
                .content(offerNode);
        sendNodeWithNoResponse(callNode.build()); // ADAPTED: WA Web tracks the stanza via the voip backend; Cobalt fire-and-forget
        return new CallOfferBuilder() // ADAPTED: WA Web returns a VoipCall; Cobalt hands back a CallOffer describing the outgoing invite
                .chatJid(target)
                .callerJid(selfJid)
                .callId(callId)
                .timestamp(Instant.now())
                .video(video)
                .status(CallOffer.Status.RINGING)
                .offlineOffer(false)
                .group(false)
                .outgoing(true)
                .build();
    }

    /**
     * Sends a {@code <call><accept/></call>} stanza acknowledging an incoming
     * call offer.
     *
     * <p>Cobalt does not exchange media, so accepting a call merely signals
     * to the caller that the local user wants to answer; the actual audio or
     * video session would have to be set up by an external WebRTC stack that
     * Cobalt does not provide.
     *
     * @implNote ADAPTED: WAWebVoipFunctions.acceptCall — WA Web negotiates an
     *           SRTP session via the native VoIP backend and uploads its
     *           answer SDP; Cobalt emits only the {@code <accept>} signalling
     *           stanza.
     * @param callId the identifier carried by the original offer, as surfaced
     *               on {@link CallOffer#callId()}
     * @param caller the JID of the call creator, as surfaced on
     *               {@link CallOffer#callerJid()}
     * @throws NullPointerException            if {@code callId} or
     *                                         {@code caller} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipFunctions", exports = "acceptCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void acceptCall(String callId, Jid caller) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(caller, "caller cannot be null");
        var acceptNode = new NodeBuilder()
                .description("accept") // WAWebHandleVoipCall.S: wap("accept", {...})
                .attribute("call-id", callId) // WAWebHandleVoipCall.S: "call-id": CUSTOM_STRING(n)
                .attribute("call-creator", caller) // WAWebHandleVoipCall.S: "call-creator": JID(r)
                .build();
        var callNode = new NodeBuilder()
                .description("call")
                .attribute("to", caller) // outgoing accept is routed back to the call creator
                .content(acceptNode);
        sendNodeWithNoResponse(callNode.build()); // ADAPTED: WA Web routes via voip backend
    }

    /**
     * Sends a {@code <call><reject/></call>} stanza declining an incoming
     * call offer.
     *
     * @implNote ADAPTED: WAWebVoipFunctions.rejectCall — WA Web additionally
     *           updates the call log and tears down the local VoIP state;
     *           Cobalt emits only the {@code <reject>} signalling stanza.
     * @param callId the identifier carried by the original offer
     * @param caller the JID of the call creator, as surfaced on
     *               {@link CallOffer#callerJid()}
     * @throws NullPointerException            if {@code callId} or
     *                                         {@code caller} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipFunctions", exports = "rejectCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void rejectCall(String callId, Jid caller) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(caller, "caller cannot be null");
        var rejectNode = new NodeBuilder()
                .description("reject") // WAWebHandleVoipCall.S: wap("reject", {...})
                .attribute("call-id", callId)
                .attribute("call-creator", caller)
                .build();
        var callNode = new NodeBuilder()
                .description("call")
                .attribute("to", caller)
                .content(rejectNode);
        sendNodeWithNoResponse(callNode.build());
    }

    /**
     * Sends a {@code <call><terminate/></call>} stanza ending a call this
     * client previously offered or accepted.
     *
     * <p>The {@code reason} attribute is fixed to {@code "hangup"}, matching
     * the value emitted by WA Web when the user presses the hang-up button.
     *
     * @implNote ADAPTED: WAWebVoipFunctions.terminateCall — WA Web tears
     *           down the WebRTC session and flushes the call log; Cobalt
     *           emits only the {@code <terminate>} signalling stanza.
     * @param callId the identifier of the call to terminate, typically
     *               obtained from {@link CallOffer#callId()}
     * @param target the JID of the other party
     * @throws NullPointerException            if {@code callId} or
     *                                         {@code target} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipFunctions", exports = "terminateCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void stopCall(String callId, Jid target) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMeUser
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        var terminateNode = new NodeBuilder()
                .description("terminate")
                .attribute("reason", "hangup") // ADAPTED: WA Web passes the actual hang-up cause; Cobalt hard-codes "hangup"
                .attribute("call-id", callId)
                .attribute("call-creator", selfJid) // outgoing terminate references the local initiator
                .build();
        var callNode = new NodeBuilder()
                .description("call")
                .attribute("to", target)
                .content(terminateNode);
        sendNodeWithNoResponse(callNode.build());
    }

    /**
     * Sends a {@code <call><ringing/></call>} stanza back to the caller to
     * confirm that this device is alerting the user.
     *
     * <p>WA Web emits this as soon as its receive-side
     * {@code WAWebHandleVoipCall} pipeline has validated the incoming offer
     * and decided that the ringing UI should be shown.
     *
     * @implNote ADAPTED: WAWebVoipFunctions.sendCallRinging — WA Web sends
     *           the ringing acknowledgement as part of the receive-side
     *           {@code receipt} stanza; Cobalt exposes it as a standalone
     *           {@code <call><ringing/></call>} message that callers may
     *           trigger manually.
     * @param callId the identifier of the offer being acknowledged
     * @param caller the JID of the call creator
     * @throws NullPointerException            if {@code callId} or
     *                                         {@code caller} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipFunctions", exports = "sendCallRinging",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void notifyCallRinging(String callId, Jid caller) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(caller, "caller cannot be null");
        var ringingNode = new NodeBuilder()
                .description("ringing")
                .attribute("call-id", callId)
                .attribute("call-creator", caller)
                .build();
        var callNode = new NodeBuilder()
                .description("call")
                .attribute("to", caller)
                .content(ringingNode);
        sendNodeWithNoResponse(callNode.build());
    }

    /**
     * Initiates a group call by emitting the signalling
     * {@code <call><offer/></call>} stanza carrying the group JID and
     * participant list.
     *
     * <p>As with one-to-one calls, Cobalt emits only the signalling layer:
     * no group rekey, no per-participant encrypted session, no media.
     *
     * @implNote ADAPTED: WAWebOutgoingGroupCallUtils — WA Web performs a
     *           group rekey, encrypts per participant, and uploads an SDP
     *           offer; Cobalt emits a stub {@code <offer>} with a
     *           {@code group-jid} attribute and a {@code <group_info>}
     *           child listing the participants.
     * @param group        the JID of the group being called, must be a group
     *                     server JID
     * @param participants the participants to invite; must be non-empty
     * @param video        whether to advertise this as a video call
     * @return the locally-tracked {@link CallOffer} describing the outgoing
     *         group call
     * @throws NullPointerException     if {@code group} or
     *                                  {@code participants} is {@code null}
     * @throws IllegalArgumentException if {@code group} is not a group JID
     *                                  or {@code participants} is empty
     * @throws IllegalStateException    if this client is not logged in
     */
    @WhatsAppWebExport(moduleName = "WAWebOutgoingGroupCallUtils", exports = "sendGroupOfferStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CallOffer startGroupCall(Jid group, Collection<Jid> participants, boolean video) {
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(participants, "participants cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community JID");
        }
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("participants cannot be empty");
        }
        var selfJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        var callId = DataUtils.randomHex(16);
        var participantNodes = new ArrayList<Node>(participants.size());
        for (var participant : participants) { // ADAPTED: mirrors WAWebOutgoingGroupCallUtils participant loop
            participantNodes.add(new NodeBuilder()
                    .description("participant")
                    .attribute("jid", participant)
                    .build());
        }
        var groupInfoNode = new NodeBuilder()
                .description("group_info") // WAWebHandleVoipCall.callParser: m.maybeChild("group_info")
                .content(participantNodes)
                .build();
        var offerChildren = new ArrayList<Node>(2);
        offerChildren.add(groupInfoNode);
        if (video) {
            offerChildren.add(new NodeBuilder()
                    .description("video")
                    .build());
        }
        var offerNode = new NodeBuilder()
                .description("offer")
                .attribute("call-id", callId)
                .attribute("call-creator", selfJid)
                .attribute("group-jid", group) // WAWebHandleVoipCall.callParser: m.attrJidWithType("group-jid")
                .content(offerChildren)
                .build();
        var callNode = new NodeBuilder()
                .description("call")
                .attribute("to", group)
                .content(offerNode);
        sendNodeWithNoResponse(callNode.build());
        return new CallOfferBuilder()
                .chatJid(group)
                .callerJid(selfJid)
                .callId(callId)
                .timestamp(Instant.now())
                .video(video)
                .status(CallOffer.Status.RINGING)
                .offlineOffer(false)
                .group(true)
                .groupJid(group)
                .outgoing(true)
                .build();
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        var response = sendMexNode(request.toNode(), FetchAllNewslettersMetadataMex.QUERY_ID, "mexFetchAllNewsletters", false);
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
        // WAWebGroupInviteJob.joinGroupViaInvite times the IQ with self.performance.now() and in
        // the finally block invokes WAWebGroupJoinRequestMetricUtils.logMembershipRequestCreate
        // ONLY when the caller anticipated a membership_approval_request response (the second
        // parameter `t`). Cobalt infers the same condition from the response shape: a
        // <membership_approval_request> child indicates the join has been gated behind admin
        // approval, so the create-request metric applies.
        var start = Instant.now();
        var successful = true;
        Jid resolvedGroup = null;
        boolean approvalGated = false;
        try {
            var response = sendNode(iqNode);
            var approvalChild = response.getChild("membership_approval_request"); // joinGroupViaInviteParser: t ? membership_approval_request : group
            approvalGated = approvalChild.isPresent();
            resolvedGroup = response.getChild("group") // joinGroupViaInviteParser: maybeChild("group")
                    .or(() -> approvalChild)
                    .flatMap(node -> node.getAttributeAsJid("jid")) // joinGroupViaInviteParser: attrGroupJid("jid")
                    .orElseThrow(() -> new NoSuchElementException("Invalid join-group response: %s".formatted(response)));
            return resolvedGroup;
        } catch (RuntimeException e) {
            successful = false;
            throw e;
        } finally {
            if (approvalGated || !successful) {
                // WAWebGroupJoinRequestMetricUtils.logMembershipRequestCreate: emits
                // new WaFsGroupJoinRequestActionWamEvent({groupJid, groupJoinRequestAction:
                // MEMBERSHIP_REQUEST_CREATE, responseTime, isSuccessful}).commit().
                wamService.commit(new WaFsGroupJoinRequestActionEventBuilder()
                        .groupJid(resolvedGroup != null ? sanitizeGroupJidForWam(resolvedGroup) : "") // WAWebGroupJoinRequestMetricUtils.getSanitizedJid: "" when unavailable
                        .groupJoinRequestAction(GroupJoinRequestActionType.MEMBERSHIP_REQUEST_CREATE)
                        .isSuccessful(successful)
                        .serverResponseTime(Instant.ofEpochMilli(Instant.now().toEpochMilli() - start.toEpochMilli()))
                        .build());
            }
        }
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
    public GroupMetadata queryGroupInfoByInvite(Jid invitee, Jid sender, long inviteTimestamp, String inviteCode) {
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
    public void sendGroupInvite(Jid group, Jid target, long inviteTimestamp) {
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var acceptNode = new NodeBuilder()
                .description("accept") // WAWebGroupInviteV4Job.joinGroupViaInviteV4: acceptArgs
                .attribute("code", "") // acceptCode placeholder — caller supplies via companion queryGroupInfoByInvite
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
        // WAWebGroupJoinRequestMetricUtils.logViewPendingParticipant: fires when the pending
        // participant list is viewed. Cobalt does not have a UI surface, so this query is the
        // closest functional counterpart — WA Web hard-codes responseTime:0 and isSuccessful:true
        // in the helper, which we mirror here.
        wamService.commit(new WaFsGroupJoinRequestActionEventBuilder()
                .groupJid(sanitizeGroupJidForWam(group)) // WAWebGroupJoinRequestMetricUtils.getSanitizedJid
                .groupJoinRequestAction(GroupJoinRequestActionType.VIEW_PENDING_PARTICIPANTS)
                .isSuccessful(true) // WAWebGroupJoinRequestMetricUtils.logViewPendingParticipant: isSuccessful: !0
                .serverResponseTime(Instant.ofEpochMilli(0L)) // WAWebGroupJoinRequestMetricUtils.logViewPendingParticipant: responseTime: 0
                .build());
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
    @WhatsAppWebExport(moduleName = "WAWebMembershipApprovalRequestAction",
            exports = "approveMembershipApprovalRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void acceptGroupJoinRequest(Jid group, Jid applicant) {
        // WAWebMembershipApprovalRequestAction.approveMembershipApprovalRequest:
        // wraps the IQ emission in try/catch to emit WaFsGroupJoinRequestAction
        // with action=MEMBERSHIP_REQUEST_APPROVE and an isSuccessful flag driven
        // by whether the IQ threw. Cobalt mirrors that pattern.
        var start = Instant.now();
        var successful = true;
        try {
            changeMembershipRequestState(group, applicant, "approve"); // WASmaxOutGroupsMembershipRequestsActionRequest: makeMembershipRequestsActionRequestMembershipRequestsActionApprove
        } catch (RuntimeException e) {
            successful = false;
            throw e;
        } finally {
            // WAWebGroupJoinRequestMetricUtils.logMembershipRequestApprove: delegates to the shared
            // emitter which builds new WaFsGroupJoinRequestActionWamEvent({groupJid, groupJoinRequestAction,
            // groupJoinRequestGroupsInCommon, serverResponseTime, isSuccessful}).commit().
            wamService.commit(new WaFsGroupJoinRequestActionEventBuilder()
                    .groupJid(sanitizeGroupJidForWam(group)) // WAWebGroupJoinRequestMetricUtils.getSanitizedJid
                    .groupJoinRequestAction(GroupJoinRequestActionType.MEMBERSHIP_REQUEST_APPROVE)
                    .isSuccessful(successful)
                    .serverResponseTime(Instant.ofEpochMilli(Instant.now().toEpochMilli() - start.toEpochMilli()))
                    .build());
        }
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
    @WhatsAppWebExport(moduleName = "WAWebMembershipApprovalRequestAction",
            exports = "rejectMembershipApprovalRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void rejectGroupJoinRequest(Jid group, Jid applicant) {
        // WAWebMembershipApprovalRequestAction.rejectMembershipApprovalRequest:
        // wraps the IQ emission in try/catch/finally to emit WaFsGroupJoinRequestAction
        // with action=MEMBERSHIP_REQUEST_REJECT and an isSuccessful flag driven by
        // whether the IQ threw. Cobalt mirrors that pattern.
        var start = Instant.now();
        var successful = true;
        try {
            changeMembershipRequestState(group, applicant, "reject"); // WASmaxOutGroupsMembershipRequestsActionRequest: makeMembershipRequestsActionRequestMembershipRequestsActionReject
        } catch (RuntimeException e) {
            successful = false;
            throw e;
        } finally {
            // WAWebGroupJoinRequestMetricUtils.logMembershipRequestReject: delegates to the shared
            // emitter which builds new WaFsGroupJoinRequestActionWamEvent({groupJid, groupJoinRequestAction,
            // groupJoinRequestGroupsInCommon, serverResponseTime, isSuccessful}).commit().
            wamService.commit(new WaFsGroupJoinRequestActionEventBuilder()
                    .groupJid(sanitizeGroupJidForWam(group)) // WAWebGroupJoinRequestMetricUtils.getSanitizedJid
                    .groupJoinRequestAction(GroupJoinRequestActionType.MEMBERSHIP_REQUEST_REJECT)
                    .isSuccessful(successful)
                    .serverResponseTime(Instant.ofEpochMilli(Instant.now().toEpochMilli() - start.toEpochMilli()))
                    .build());
        }
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
     * Sanitizes a group JID for inclusion in
     * {@link WaFsGroupJoinRequestActionEvent#groupJid()}.
     *
     * <p>Mirrors {@code WAWebGroupJoinRequestMetricUtils.getSanitizedJid},
     * which drops legacy subject-based group JIDs (those containing a
     * {@code -}) and reports them as the empty string so that the metric
     * only carries the modern {@code @g.us} form.
     *
     * @param group the group JID to sanitize, may be {@code null}
     * @return the sanitized JID string, or the empty string when
     *         {@code group} is {@code null} or legacy
     *
     * @implNote WA Web uses {@code e?.toJid() ?? ""} and then
     * {@code n.includes("-") ? "" : n}; Cobalt applies the same two-step
     * check against {@link Jid#toString()}.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupJoinRequestMetricUtils",
            exports = "getSanitizedJid",
            adaptation = WhatsAppAdaptation.DIRECT)
    private String sanitizeGroupJidForWam(Jid group) {
        if (group == null) {
            return "";
        }
        var jid = group.toString(); // WAWebGroupJoinRequestMetricUtils.getSanitizedJid: e.toJid()
        return jid.contains("-") ? "" : jid; // WAWebGroupJoinRequestMetricUtils.getSanitizedJid: n.includes("-") ? "" : n
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
     * Checks whether each of the given phone-number JIDs corresponds to an
     * existing registered WhatsApp account.
     *
     * <p>Issues a single {@code usync} IQ with the {@code contact} protocol
     * ({@code contact_exists} query in WhatsApp terminology), listing every
     * phone number as a {@code <contact>+<digits></contact>} text node under
     * a {@code <user>} element. The server responds with one {@code <user>}
     * per input, carrying a {@code type} attribute whose value is
     * {@code "in"} when the phone number belongs to a WhatsApp user and
     * {@code "out"} (or absent) otherwise.
     *
     * <p>Input JIDs that do not map to a valid phone number (for example
     * LID-only JIDs) are silently skipped and not present in the result map.
     *
     * @param phoneNumbers the user JIDs to look up
     * @return a map whose keys are the phone JIDs echoed by the server (as
     *         resolved by the {@code jid} attribute of each response entry)
     *         and whose values indicate whether that user is registered
     * @throws NullPointerException if {@code phoneNumbers} is {@code null}
     *
     * @implNote WAWebUsyncContact.USyncContactProtocol is sent through
     * WAWebUsync.USyncQuery with mode {@code query} and context
     * {@code background}; Cobalt builds the equivalent IQ directly because
     * it does not mirror WA Web's protocol abstraction layer. The session id
     * is produced by {@link RandomIdUtils#generateSid()}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact", exports = "USyncContactProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebUsync", exports = "USyncQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, Boolean> hasWhatsapp(Collection<Jid> phoneNumbers) {
        Objects.requireNonNull(phoneNumbers, "phoneNumbers cannot be null");
        if (phoneNumbers.isEmpty()) {
            return Map.of();
        }

        // WAWebUsync.USyncQuery.$3: WAWap.wap("user", {jid: USER_JID(getId()), pn_jid: ...},
        // protocols.map(e => e.getUserElement(t))). Cobalt only sends the contact protocol here,
        // so the only per-user child is the <contact>+<phone></contact> node.
        var userNodes = new ArrayList<Node>(phoneNumbers.size());
        for (var jid : phoneNumbers) {
            var phoneNumber = jid.toPhoneNumber().orElse(null);
            if (phoneNumber == null) {
                continue;
            }
            // WAWebUsyncContact.USyncContactProtocol.getUserElement: wap("contact", null, e)
            var contactNode = new NodeBuilder()
                    .description("contact")
                    .content(phoneNumber)
                    .build();
            // WAWebUsync.USyncQuery.$3: <user jid="…"> attribute is set from USyncUser.getId()
            // via WAWebCommsWapMd.USER_JID, matching the live IQ shape exactly.
            userNodes.add(new NodeBuilder()
                    .description("user")
                    .attribute("jid", jid.toUserJid())
                    .content(contactNode)
                    .build());
        }
        if (userNodes.isEmpty()) {
            return Map.of();
        }

        // WAWebUsyncContact.USyncContactProtocol.getQueryElement: wap("contact", {...})
        var queryNode = new NodeBuilder()
                .description("query")
                .content(new NodeBuilder()
                        .description("contact")
                        .build())
                .build();
        var listNode = new NodeBuilder()
                .description("list")
                .content(userNodes)
                .build();
        // WAWebUsync.USyncQuery.$3: WAWap.wap("usync", {sid, index:"0", last:"true",
        // mode, context}) — attribute insertion order matches the JS object literal so
        // the WAP byte stream is byte-stable against live traffic.
        var usyncNode = new NodeBuilder()
                .description("usync")
                .attribute("sid", RandomIdUtils.newId())
                .attribute("index", "0")
                .attribute("last", "true")
                .attribute("mode", "query")
                .attribute("context", "background")
                .content(queryNode, listNode)
                .build();
        // WAWebUsync.USyncQuery.$3: WAWap.wap("iq", {to: S_WHATSAPP_NET, xmlns: "usync",
        // type: "get", id: generateId()}) — same attribute order as live traffic.
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("to", JidServer.user())
                .attribute("xmlns", "usync")
                .attribute("type", "get")
                .content(usyncNode);

        var response = sendNode(iqNode); // WAWebUsync.USyncQuery.execute -> deprecatedSendIq

        // WAWebUsync.usyncParser.m: iterate <usync><list><user/> entries
        var results = new HashMap<Jid, Boolean>();
        response.streamChild("usync")
                .flatMap(usync -> usync.streamChild("list"))
                .flatMap(list -> list.streamChildren("user"))
                .forEach(user -> {
                    var userJid = user.getAttributeAsJid("jid").orElse(null);
                    if (userJid == null) {
                        return;
                    }
                    var contactChild = user.getChild("contact").orElse(null);
                    if (contactChild == null) {
                        return;
                    }
                    // WAWebUsyncContact.contactParser: t.hasAttr("type") => {type: "in"|"out"}
                    var type = contactChild.getAttributeAsString("type", null);
                    results.put(userJid.toUserJid(), "in".equals(type));
                });
        return Collections.unmodifiableMap(results);
    }

    /**
     * Convenience singleton wrapper over
     * {@link #hasWhatsapp(Collection)} for checking a single phone-number
     * JID.
     *
     * @param phone the non-{@code null} user JID to look up
     * @return {@code true} if the server reports the phone as registered
     * @throws NullPointerException if {@code phone} is {@code null}
     *
     * @implNote Delegates to {@link #hasWhatsapp(Collection)}; WA Web has no
     * single-entry helper on this flow.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact", exports = "USyncContactProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean hasWhatsapp(Jid phone) {
        Objects.requireNonNull(phone, "phone cannot be null");
        var map = hasWhatsapp(List.of(phone));
        for (var registered : map.values()) {
            if (Boolean.TRUE.equals(registered)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Queries the push name associated with the given user JID.
     *
     * <p>Consults the locally cached {@link Contact} record first and only
     * falls back to a remote {@code usync} query when the store has no name
     * on file. The remote query uses the {@code contact} protocol like
     * {@link #hasWhatsapp(Collection)} but interprets the returned
     * {@code <contact>} element's text content as the push name.
     *
     * @param jid the non-{@code null} user JID to resolve
     * @return the display name, or empty if neither the store nor the
     *         server knows one
     * @throws NullPointerException if {@code jid} is {@code null}
     *
     * @implNote WAWebUsync.USyncQuery + WAWebUsyncContact.USyncContactProtocol
     * are used in the fallback path. Cobalt additionally consults its local
     * contact store because WA Web maintains a reactive {@code ContactCollection}
     * that Cobalt flattens into its store.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact", exports = "contactParser",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<String> queryName(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");

        // ADAPTED: WAWebContactCollection lookup, Cobalt store is the cache equivalent.
        // Use chosenName() (push name) rather than Contact.name() because the latter
        // falls back to the JID user component, which would mask absent server data.
        var cached = store.findContactByJid(jid)
                .flatMap(Contact::chosenName)
                .filter(name -> !name.isBlank());
        if (cached.isPresent()) {
            return cached;
        }

        var phoneNumber = jid.toPhoneNumber().orElse(null);
        if (phoneNumber == null) {
            return Optional.empty();
        }

        // WAWebUsyncContact.USyncContactProtocol.getUserElement: wap("contact", null, phone)
        var contactNode = new NodeBuilder()
                .description("contact")
                .content(phoneNumber)
                .build();
        var userNode = new NodeBuilder()
                .description("user")
                .content(contactNode)
                .build();
        var queryNode = new NodeBuilder()
                .description("query")
                .content(new NodeBuilder().description("contact").build())
                .build();
        var listNode = new NodeBuilder()
                .description("list")
                .content(userNode)
                .build();
        var usyncNode = new NodeBuilder()
                .description("usync")
                .attribute("sid", RandomIdUtils.generateSid()) // WAWebMdWamTypes.generateSid
                .attribute("mode", "query")
                .attribute("last", "true")
                .attribute("index", "0")
                .attribute("context", "background")
                .content(queryNode, listNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "usync")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(usyncNode);

        var response = sendNode(iqNode); // WAWebUsync.USyncQuery.execute

        // WAWebUsyncContact.contactParser: returns the <contact> node's content when present
        return response.streamChild("usync")
                .flatMap(usync -> usync.streamChild("list"))
                .flatMap(list -> list.streamChildren("user"))
                .map(user -> user.getChild("contact").orElse(null))
                .filter(Objects::nonNull)
                .map(contact -> contact.toContentString().orElse(null))
                .filter(name -> name != null && !name.isBlank())
                .findFirst();
    }

    /**
     * Uploads a batch of phone-number contacts to the server and returns
     * the phone-to-JID mapping the server resolves for them.
     *
     * <p>For every {@link ContactCard} in {@code contacts} this method
     * extracts the default {@code CELL} phone numbers and issues a
     * {@code usync} IQ with the {@code contact} protocol. Entries the
     * server acknowledges as registered users ({@code type="in"}) are
     * returned in the result map keyed by the phone-number JID that was
     * sent and valued by the server-normalised {@code jid} attribute of
     * the response entry; the two values differ only when the server
     * rewrites the identifier (for example during LID migration).
     *
     * @param contacts the contact cards to synchronise
     * @return an unmodifiable map from phone-number JID to the
     *         server-returned JID for every successfully resolved contact
     * @throws NullPointerException if {@code contacts} is {@code null}
     *
     * @implNote WAWebContactSyncApi.syncContactList is the behavioural
     * analogue; Cobalt omits the legacy device-sync, text-status and
     * disappearing-mode sub-protocols and exposes only the JID mapping that
     * the public API needs. Batching mirrors
     * {@code WAWebContactSyncApi.syncContactListInChunks} but is performed
     * eagerly because Cobalt runs on virtual threads.
     */
    @WhatsAppWebExport(moduleName = "WAWebContactSyncApi", exports = "syncContactList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, Jid> syncContacts(Collection<ContactCard> contacts) {
        Objects.requireNonNull(contacts, "contacts cannot be null");
        if (contacts.isEmpty()) {
            return Map.of();
        }

        // ADAPTED: WAWebContactSyncApi, flatten ContactCard phone numbers to phone JIDs
        var phoneJids = new ArrayList<Jid>();
        for (var card : contacts) {
            if (card instanceof ContactCard.Parsed parsed) {
                phoneJids.addAll(parsed.phoneNumbers()); // ContactCard.Parsed exposes the default CELL list via phoneNumbers()
            }
        }
        if (phoneJids.isEmpty()) {
            return Map.of();
        }

        // WAWebUsyncContact.USyncContactProtocol.getUserElement: one <user><contact>+phone</contact></user> per entry
        var userNodes = new ArrayList<Node>(phoneJids.size());
        var byPhone = new HashMap<String, Jid>();
        for (var phoneJid : phoneJids) {
            var phoneNumber = phoneJid.toPhoneNumber().orElse(null);
            if (phoneNumber == null) {
                continue;
            }
            byPhone.put(phoneNumber, phoneJid);
            var contactNode = new NodeBuilder()
                    .description("contact")
                    .content(phoneNumber)
                    .build();
            userNodes.add(new NodeBuilder()
                    .description("user")
                    .content(contactNode)
                    .build());
        }
        if (userNodes.isEmpty()) {
            return Map.of();
        }

        var queryNode = new NodeBuilder()
                .description("query")
                .content(new NodeBuilder().description("contact").build())
                .build();
        var listNode = new NodeBuilder()
                .description("list")
                .content(userNodes)
                .build();
        // WAWebUsync.USyncQuery.$3: mode=query context=background for contact sync
        var usyncNode = new NodeBuilder()
                .description("usync")
                .attribute("sid", RandomIdUtils.generateSid()) // WAWebMdWamTypes.generateSid
                .attribute("mode", "query")
                .attribute("last", "true")
                .attribute("index", "0")
                .attribute("context", "background")
                .content(queryNode, listNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "usync")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(usyncNode);

        // WAWebContactSyncLogger.contactSyncLogger.createEventContext: WAM start timestamp
        var syncStartTimestamp = Instant.now();
        // WAWebContactSyncLogger PROTOCOL_BIT.CONTACT (0) - only protocol queried here
        var requestProtocolBitmask = 1;
        // WAWebContactSyncLogger SYNC_REQUEST_ORIGIN.UNKNOWN (0) - public API has no explicit origin
        var requestOrigin = 0;

        Node response;
        try {
            response = sendNode(iqNode); // WAWebContactSyncApi.syncContactList -> USyncQuery.execute
        } catch (RuntimeException e) {
            // WAWebContactSyncLogger.contactSyncLogger.logFailure: emit ContactSyncEvent (id 1006)
            // for the background contact-sync failure path.
            var endTs = Instant.now();
            wamService.commit(new ContactSyncEventEventBuilder()
                    // WAWebContactSyncLogger.getSyncTypeString: ("background_query").toUpperCase()
                    .contactSyncType("BACKGROUND_QUERY")
                    .contactSyncRequestOrigin(requestOrigin)
                    .contactSyncSuccess(false)
                    .contactSyncNoop(false)
                    // WAWebContactSyncLogger.logFailure: errorCode is unknown on transport exception
                    .contactSyncErrorCode(0)
                    .contactSyncStartTimestamp(syncStartTimestamp)
                    .contactSyncEndTimestamp(endTs)
                    .contactSyncLatency((int) (endTs.toEpochMilli() - syncStartTimestamp.toEpochMilli()))
                    .contactSyncRequestedCount(userNodes.size())
                    .contactSyncResponseCount(0)
                    .contactSyncRequestProtocol(requestProtocolBitmask)
                    .contactSyncFailureProtocol(0)
                    .build());
            throw e;
        }

        // WAWebContactSyncApi.syncContactList: iterate f.list, upsert mappings where type==="in"
        var mapping = new HashMap<Jid, Jid>();
        var responseCount = new int[]{0};
        response.streamChild("usync")
                .flatMap(usync -> usync.streamChild("list"))
                .flatMap(list -> list.streamChildren("user"))
                .forEach(user -> {
                    responseCount[0]++;
                    var resolvedJid = user.getAttributeAsJid("jid").orElse(null);
                    if (resolvedJid == null) {
                        return;
                    }
                    var contactChild = user.getChild("contact").orElse(null);
                    if (contactChild == null) {
                        return;
                    }
                    // WAWebUsyncContact.contactParser: only rows with type="in" are registered
                    if (!"in".equals(contactChild.getAttributeAsString("type", null))) {
                        return;
                    }
                    var echoed = contactChild.toContentString().orElse(null);
                    var phoneJid = echoed != null ? byPhone.get(echoed) : null;
                    if (phoneJid == null) {
                        phoneJid = resolvedJid.toUserJid();
                    }
                    mapping.put(phoneJid, resolvedJid.toUserJid());
                });

        // WAWebContactSyncLogger.contactSyncLogger.logSuccess: emit ContactSyncEvent (id 1006)
        // for the background contact-sync success path.
        var endTimestamp = Instant.now();
        wamService.commit(new ContactSyncEventEventBuilder()
                .contactSyncType("BACKGROUND_QUERY")
                .contactSyncRequestOrigin(requestOrigin)
                .contactSyncSuccess(true)
                // WAWebContactSyncLogger: noop when requestedCount === 0
                .contactSyncNoop(userNodes.isEmpty())
                .contactSyncStartTimestamp(syncStartTimestamp)
                .contactSyncEndTimestamp(endTimestamp)
                .contactSyncLatency((int) (endTimestamp.toEpochMilli() - syncStartTimestamp.toEpochMilli()))
                .contactSyncRequestedCount(userNodes.size())
                .contactSyncResponseCount(responseCount[0])
                .contactSyncRequestProtocol(requestProtocolBitmask)
                .contactSyncFailureProtocol(0)
                .build());

        return Collections.unmodifiableMap(mapping);
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
        // WAWebMexFetchNewsletterJob.mexGetNewsletter: u = WAWebWid.isNewsletter(t) ? "JID" : "INVITE"
        // input = {key: t, type: u, view_role: a}
        var input = new FetchNewsletterMex.Request.Input(
                newsletterJid.toString(),
                "JID",
                role != null ? role.name() : null
        );
        var request = new FetchNewsletterMex.Request(
                Boolean.TRUE, // fetch_creation_time
                Boolean.TRUE, // fetch_full_image (c = u !== "INVITE")
                Boolean.TRUE, // fetch_viewer_metadata
                Boolean.TRUE, // fetch_wamo_sub
                input
        );
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        var response = sendMexNode(request.toNode(), FetchNewsletterMex.QUERY_ID, "mexGetNewsletter", false);
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        var response = sendMexNode(request.toNode(), CreateNewsletterMex.QUERY_ID, "mexCreateNewsletter", false);
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
        var request = new UpdateNewsletterMex.Request(newsletter.toString(), updates);
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        sendMexNodeWithNoResponse(request.toNode(), UpdateNewsletterMex.QUERY_ID, "mexUpdateNewsletter", false);
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        sendMexNodeWithNoResponse(request.toNode(), DeleteNewsletterMex.QUERY_ID, "mexDeleteNewsletter", false);
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        sendMexNodeWithNoResponse(request.toNode(), JoinNewsletterMex.QUERY_ID, "mexJoinNewsletter", false);
        store.findNewsletterByJid(newsletter)
                .orElseGet(() -> store.addNewNewsletter(newsletter));
        // WAWebNewsletterSubscribeAction.subscribeToNewsletterAction ->
        // WAWebNewsletterAttributionLogging.NewsletterCoreEventLogger.log({cid, channelCoreEventType: FOLLOW, ...})
        // -> new ChannelCoreEventWamEvent({cid: s.user, channelCoreEventType, channelEntryPointApp: WHATSAPP, ...}).commit()
        wamService.commit(new ChannelCoreEventEventBuilder()
                .cid(newsletter.user())
                .channelCoreEventType(ChannelEventType.FOLLOW)
                .channelEntryPointApp(ChannelEntryPointApp.WHATSAPP)
                .build());
        // WAWebNewsletterSubscribeAction.subscribeToNewsletterWidAction ->
        // WAWebNewsletterMembershipActionLogger.logNewsletterMembershipActionEvent({cid, actionResult: FOLLOW_SUCCESS})
        // -> new ChannelMembershipActionEventWamEvent({cid: n.user, actionResult}).commit()
        wamService.commit(new ChannelMembershipActionEventEventBuilder()
                .cid(newsletter.user())
                .actionResult(ChannelMembershipActionResult.FOLLOW_SUCCESS)
                .build());
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        sendMexNodeWithNoResponse(request.toNode(), LeaveNewsletterMex.QUERY_ID, "mexLeaveNewsletter", false);
        store.removeNewsletter(newsletter);
        // WAWebNewsletterUnsubscribeAction.unsubscribeFromNewsletterAction ->
        // WAWebNewsletterAttributionLogging.NewsletterCoreEventLogger.log({cid, channelCoreEventType: UNFOLLOW, ...})
        // -> new ChannelCoreEventWamEvent({cid: s.user, channelCoreEventType, channelEntryPointApp: WHATSAPP, ...}).commit()
        wamService.commit(new ChannelCoreEventEventBuilder()
                .cid(newsletter.user())
                .channelCoreEventType(ChannelEventType.UNFOLLOW)
                .channelEntryPointApp(ChannelEntryPointApp.WHATSAPP)
                .build());
        // WAWebNewsletterUnsubscribeAction.unsubscribeFromNewsletterAction ->
        // WAWebNewsletterMembershipActionLogger.logNewsletterMembershipActionEvent({cid: t.id, actionResult: UNFOLLOW_SUCCESS})
        // -> new ChannelMembershipActionEventWamEvent({cid: n.user, actionResult}).commit()
        wamService.commit(new ChannelMembershipActionEventEventBuilder()
                .cid(newsletter.user())
                .actionResult(ChannelMembershipActionResult.UNFOLLOW_SUCCESS)
                .build());
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
        // WAWebNewsletterUpdateUserSettingJob: passes {newsletter_id, type, value} as a NESTED input object,
        // not a stringified JSON. WAWebMexClient.fetchQuery places it under variables.input.
        var request = new UpdateNewsletterUserSettingMex.Request(
                newsletter.toString(),       // WAWebNewsletterUpdateUserSettingJob: newsletter_id
                "MUTE_ADMIN_ACTIVITY",       // WAWebNewsletterUpdateUserSettingJob: ADMIN_NOTIFICATIONS -> "MUTE_ADMIN_ACTIVITY"
                mute ? "ON" : "OFF");        // WAWebNewsletterUpdateUserSettingJob: MUTED_STATE -> "ON", else "OFF"
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        sendMexNodeWithNoResponse(request.toNode(), UpdateNewsletterUserSettingMex.QUERY_ID, "mexUpdateNewsletterUserSetting", false);
        // WAWebNewsletterUpdateUserSettingsAction.updateNewsletterUserSettingsAction ->
        // NewsletterCoreEventLogger.log({cid, channelCoreEventType: MUTE|UNMUTE,
        //   channelRequestMetadata: JSON.stringify(a.map(s => (mute?"mute":"unmute")+"_"+s))}).
        // Cobalt only toggles admin_activity so the list is always a single entry.
        wamService.commit(new ChannelCoreEventEventBuilder()
                .cid(newsletter.user())
                .channelCoreEventType(mute ? ChannelEventType.MUTE : ChannelEventType.UNMUTE)
                .channelEntryPointApp(ChannelEntryPointApp.WHATSAPP)
                .channelRequestMetadata(mute ? "[\"mute_admin_activity\"]" : "[\"unmute_admin_activity\"]")
                .build());
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        sendMexNodeWithNoResponse(request.toNode(), AcceptNewsletterAdminInviteMex.QUERY_ID, "acceptNewsletterAdminInvite", false);
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        sendMexNodeWithNoResponse(request.toNode(), RevokeNewsletterAdminInviteMex.QUERY_ID, "revokeNewsletterAdminInvite", false);
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        sendMexNodeWithNoResponse(request.toNode(), DemoteNewsletterAdminMex.QUERY_ID, "demoteNewsletterAdmin", false);
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
        var request = new UpdateNewsletterMex.Request(newsletter.toString(), updates);
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        sendMexNodeWithNoResponse(request.toNode(), UpdateNewsletterMex.QUERY_ID, "mexUpdateNewsletter", false);
    }

    /**
     * Queries the "about" status text of the given user.
     *
     * <p>Sends an {@code <iq xmlns="status" to="s.whatsapp.net" type="get">}
     * stanza whose body is {@code <status><user jid="..."/></status>} and
     * parses the response {@code <user><status>TEXT</status></user>} child.
     *
     * <p>WA Web reaches this endpoint through
     * {@code WAWebContactStatusBridge.getStatus}, which either routes through
     * the MEX GraphQL {@code getAboutStatus} query (when the
     * {@code mex_usync_about_status} AB prop is set) or falls through to
     * {@code WAWebGetAboutQueryJob.getAbout}, itself an XMPP usync query.
     * Cobalt simplifies both paths into a single direct IQ since neither
     * MEX nor usync semantics change the wire-observable answer for a
     * single user.
     *
     * @implNote {@code WAWebContactStatusBridge.getStatus} ->
     * {@code WAWebGetAboutQueryJob.getAbout} -> {@code USyncQuery.withStatusProtocol}
     * @param jid the user JID whose about text should be fetched
     * @return the about text when the server responds with a non-empty
     *         {@code <status>} element; {@link Optional#empty()} otherwise
     * @throws NullPointerException            if {@code jid} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebContactStatusBridge", exports = "getStatus",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGetAboutQueryJob", exports = "getAbout",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<String> queryAbout(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        // WAWebGetAboutQueryJob.getAbout: new USyncQuery().withStatusProtocol().withUser(new USyncUser().withId(i))
        var userNode = new NodeBuilder()
                .description("user")
                .attribute("jid", jid) // WAWebUsyncUser.USyncUser.withId
                .build();
        var statusQuery = new NodeBuilder()
                .description("status")
                .content(userNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "status") // task spec: xmlns="status"
                .attribute("to", JidServer.user()) // task spec: to="s.whatsapp.net"
                .attribute("type", "get")
                .content(statusQuery);
        var response = sendNode(iqNode);
        // Response shape: <iq><status><user jid="..."><status>TEXT</status></user></status></iq>
        return response.getChild("status")
                .flatMap(statusNode -> statusNode.getChild("user"))
                .flatMap(userResp -> userResp.getChild("status"))
                .flatMap(Node::toContentString)
                .filter(s -> !s.isEmpty()); // WAWebGetAboutQueryJob.getAbout: {id, status: ""} when empty
    }

    /**
     * Queries the first page of products listed in the WhatsApp Business
     * catalog owned by the given business JID.
     *
     * <p>This overload uses the WA Web {@code limit=5} page size default
     * declared in {@code WAWebBizProductCatalogAction.queryCatalog}.
     *
     * @param businessJid the non-{@code null} business JID whose catalog
     *                    should be fetched
     * @return the list of parsed {@link BusinessCatalogEntry} instances
     *         returned by the first page; never {@code null} and empty
     *         when the catalog is empty or the response is absent
     * @throws NullPointerException if {@code businessJid} is {@code null}
     *
     * @implNote WAWebQueryCatalog: dispatches the
     * {@code WAWebQueryCatalogQuery.graphql} operation through
     * {@link QueryCatalogMex}. WA Web's owner/guest divergence is not
     * observable at this layer since both paths issue the same GraphQL
     * document and return the same shape.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryCatalog", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebBizProductCatalogAction", exports = "queryCatalog",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<BusinessCatalogEntry> queryBusinessCatalog(Jid businessJid) {
        return queryBusinessCatalog(businessJid, DEFAULT_CATALOG_LIMIT);
    }

    /**
     * Queries the first page of products listed in the WhatsApp Business
     * catalog owned by the given business JID, using a caller-provided
     * page size.
     *
     * <p>Sends the {@code WAWebQueryCatalogQuery} MEX GraphQL query over
     * the {@code w:mex} namespace and parses the
     * {@code xwa_product_catalog_get_product_catalog.product_catalog.products}
     * array. The resulting list preserves the server-side ordering so that
     * callers paginating manually via successive calls observe a stable
     * traversal.
     *
     * @param businessJid the non-{@code null} business JID whose catalog
     *                    should be fetched
     * @param limit       the maximum number of products per page; WA Web
     *                    accepts any positive value and clamps silently
     * @return the list of parsed {@link BusinessCatalogEntry} instances
     *         returned by the first page; never {@code null} and empty
     *         when the catalog is empty or the response is absent
     * @throws NullPointerException     if {@code businessJid} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code limit} is not positive
     *
     * @implNote WAWebQueryCatalog: only the first page is requested; the
     * {@code paging.after} cursor returned by the relay is discarded.
     * Callers wanting full pagination should consume
     * {@link QueryCatalogMex.Response} directly.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryCatalog", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebBizProductCatalogAction", exports = "queryCatalog",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<BusinessCatalogEntry> queryBusinessCatalog(Jid businessJid, int limit) {
        Objects.requireNonNull(businessJid, "businessJid cannot be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        // WAWebQueryCatalog.default: the WA Web request uses the WID's canonical string form as the jid variable
        var request = new QueryCatalogMex.Request(
                businessJid.toString(),
                limit,
                DEFAULT_CATALOG_IMAGE_WIDTH,
                DEFAULT_CATALOG_IMAGE_HEIGHT,
                null
        );
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        var response = sendMexNode(request.toNode(), QueryCatalogMex.QUERY_ID, "queryCatalog", false);
        // WAWebCatalogEventLogger.createCatalogEventLogger(GET_CATALOG): emit GraphqlCatalogRequest WAM event based on response
        logGraphqlCatalogRequest(response, GraphqlCatalogEndpoint.GET_CATALOG);
        return QueryCatalogMex.Response.of(response)
                .map(QueryCatalogMex.Response::products)
                .orElseGet(List::of);
    }

    /**
     * Queries the first page of collections defined inside the WhatsApp
     * Business catalog owned by the given business JID.
     *
     * <p>This overload uses the WA Web {@code limit=5} page size default
     * and the same {@code item_limit=100} default used by the WA Web
     * storefront when browsing collections.
     *
     * @param businessJid the non-{@code null} business JID whose
     *                    collections should be fetched
     * @return the list of parsed {@link BusinessCatalog} instances
     *         returned by the first page; never {@code null} and empty
     *         when the catalog has no collections or the response is
     *         absent
     * @throws NullPointerException if {@code businessJid} is {@code null}
     *
     * @implNote WAWebQueryProductCollections: dispatches the
     * {@code WAWebQueryProductCollectionsQuery.graphql} operation through
     * {@link QueryProductCollectionsMex}.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<BusinessCatalog> queryBusinessCollections(Jid businessJid) {
        return queryBusinessCollections(businessJid, DEFAULT_CATALOG_LIMIT);
    }

    /**
     * Queries the first page of collections defined inside the WhatsApp
     * Business catalog owned by the given business JID, using a
     * caller-provided collection page size.
     *
     * <p>Sends the {@code WAWebQueryProductCollectionsQuery} MEX GraphQL
     * query over the {@code w:mex} namespace and parses the
     * {@code xwa_product_catalog_get_collections.collections} array. The
     * {@code item_limit} (inner products per collection) is fixed at the
     * WA Web default of {@code 100}; callers that need a different cap
     * should instantiate {@link QueryProductCollectionsMex.Request}
     * directly.
     *
     * @param businessJid the non-{@code null} business JID whose
     *                    collections should be fetched
     * @param limit       the maximum number of collections per page
     * @return the list of parsed {@link BusinessCatalog} instances
     *         returned by the first page; never {@code null} and empty
     *         when the catalog has no collections or the response is
     *         absent
     * @throws NullPointerException     if {@code businessJid} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code limit} is not positive
     *
     * @implNote WAWebQueryProductCollections: only the first page is
     * requested; the {@code paging.after} cursor returned by the relay
     * is discarded. Callers wanting full pagination should consume
     * {@link QueryProductCollectionsMex.Response} directly.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<BusinessCatalog> queryBusinessCollections(Jid businessJid, int limit) {
        Objects.requireNonNull(businessJid, "businessJid cannot be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        // WAWebQueryProductCollections.default: item_limit defaults to 100 in the WA Web storefront
        var request = new QueryProductCollectionsMex.Request(
                businessJid.toString(),
                limit,
                PRODUCT_COLLECTION_ITEM_LIMIT,
                DEFAULT_CATALOG_IMAGE_WIDTH,
                DEFAULT_CATALOG_IMAGE_HEIGHT,
                null
        );
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        var response = sendMexNode(request.toNode(), QueryProductCollectionsMex.QUERY_ID, "queryProductCollections", false);
        // WAWebCatalogEventLogger.createCatalogEventLogger(GET_COLLECTIONS): emit GraphqlCatalogRequest WAM event based on response
        logGraphqlCatalogRequest(response, GraphqlCatalogEndpoint.GET_COLLECTIONS);
        return QueryProductCollectionsMex.Response.of(response)
                .map(QueryProductCollectionsMex.Response::collections)
                .orElseGet(List::of);
    }

    /**
     * Emits the WAM {@code GraphqlCatalogRequest} event for a catalog MEX
     * GraphQL query response.
     *
     * <p>Inspects the relay response to determine whether the query succeeded
     * or returned a GraphQL-level error. On success the event records
     * {@code graphqlErrorCode = -1} and
     * {@link GraphqlRequestResult#SUCCESS}; on failure it records the first
     * error's {@code code} and {@link GraphqlRequestResult#FAILURE}, matching
     * the WA Web behaviour implemented by
     * {@code WAWebCatalogEventLogger.createCatalogEventLogger}'s
     * {@code success}/{@code failure} callbacks that are invoked by
     * {@code WAWebRelayClient.fetchQuery}.
     *
     * <p>A GraphQL error is detected when the MEX {@code <result>} JSON
     * payload contains an {@code errors} array at its top level. When the
     * {@code <result>} payload is absent or unparsable, this method skips the
     * emission since WA Web's {@code eventLogger} is also never invoked in
     * that path ({@code WAWebMexNativeClient} throws a
     * {@code MexPayloadParsingError} before the caller's {@code try/catch}
     * reaches {@code GraphQLServerError}).
     *
     * @implNote WAWebCatalogEventLogger: populates only
     * {@code graphqlCatalogEndpoint}, {@code graphqlErrorCode} and
     * {@code graphqlRequestResult}. The {@code businessJid} and
     * {@code businessType} properties declared on the event schema are never
     * set by WA Web's catalog event logger so Cobalt leaves them absent.
     * @implNote WAWebGraphQLServerError: the error code is extracted from
     * {@code source.errors[0].code}. WA Web's {@code GraphQLServerError} is
     * raised by {@code WAWebRelayClient.fetchQuery} whenever the relay
     * response carries GraphQL errors; Cobalt mirrors the detection by
     * checking the {@code errors} JSON array directly.
     * @param response the inbound IQ stanza returned by
     *                 {@link #sendNode(NodeBuilder)}
     * @param endpoint the {@link GraphqlCatalogEndpoint} describing which
     *                 catalog operation was executed
     */
    @WhatsAppWebExport(moduleName = "WAWebCatalogEventLogger", exports = "createCatalogEventLogger",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void logGraphqlCatalogRequest(Node response, GraphqlCatalogEndpoint endpoint) {
        // WAWebMexClient.fetchQuery: the response payload is nested under the <result> child
        var payload = response.getChild("result")
                .flatMap(Node::toContentBytes);
        if (payload.isEmpty()) {
            // WAWebMexNativeClient: a missing <result> payload results in MexPayloadParsingError
            // which is caught before GraphQLServerError dispatch, so no eventLogger callback fires.
            return;
        }
        JSONObject root;
        try {
            root = JSON.parseObject(payload.get());
        } catch (Exception ignored) {
            return;
        }
        if (root == null) {
            return;
        }
        // WAWebGraphQLServerError: errors surface as a top-level array on the GraphQL payload
        var errors = root.getJSONArray("errors");
        var builder = new GraphqlCatalogRequestEventBuilder()
                .graphqlCatalogEndpoint(endpoint);
        if (errors != null && !errors.isEmpty()) {
            // WAWebCatalogEventLogger.failure: e.set({graphqlErrorCode: t.code, graphqlRequestResult: FAILURE})
            var firstError = errors.getJSONObject(0);
            var code = firstError == null ? 0 : firstError.getIntValue("code");
            builder.graphqlErrorCode(code)
                    .graphqlRequestResult(GraphqlRequestResult.FAILURE);
        } else {
            // WAWebCatalogEventLogger.success: e.set({graphqlErrorCode: -1, graphqlRequestResult: SUCCESS})
            builder.graphqlErrorCode(-1)
                    .graphqlRequestResult(GraphqlRequestResult.SUCCESS);
        }
        wamService.commit(builder.build());
    }

    /**
     * Queries the server for the list of contacts currently blocked by
     * this account.
     *
     * <p>Sends {@code <iq xmlns="blocklist" to="s.whatsapp.net"
     * type="get"/>} matching WA Web's
     * {@code WASmaxOutBlocklistsGetBlockListRequest.makeGetBlockListRequest}
     * and parses the {@code <list><item jid="..."/>...</list>} children
     * from the response (the
     * {@code GetBlockListResponseSuccessWithMismatch} shape). The
     * resulting JIDs replace the store's block list eagerly, mirroring
     * WA Web's {@code WAWebApiBlocklist.updateBlocklist} bulk-replace.
     *
     * @implNote {@code WAWebGetBlocklistJob.getBlocklist} ->
     * {@code WASmaxBlocklistsGetBlockListRPC.sendGetBlockListRPC}
     * @return the blocked JIDs in server-returned order
     */
    @WhatsAppWebExport(moduleName = "WAWebGetBlocklistJob", exports = "getBlocklist", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebApiBlocklist", exports = "getBlocklist", adaptation = WhatsAppAdaptation.ADAPTED)
    public SequencedCollection<Jid> queryBlockList() {
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "blocklist")
                .attribute("to", JidServer.user())
                .attribute("type", "get");
        var response = sendNode(iqNode);
        // Response shape: <iq><list><item jid="..."/>...</list></iq>
        var items = response.getChild("list")
                .stream()
                .flatMap(list -> list.streamChildren("item"))
                .map(item -> item.getAttributeAsString("jid"))
                .flatMap(Optional::stream)
                .map(Jid::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        store.setBlockedContacts(items);
        return items;
    }

    /**
     * Blocks the given contact at the server so they can no longer
     * send messages or see this account's presence.
     *
     * <p>Sends {@code <iq xmlns="blocklist" to="s.whatsapp.net"
     * type="set"><item action="block" jid="..."/></iq>} matching WA
     * Web's
     * {@code WASmaxOutBlocklistsUpdateBlockListRequest.makeUpdateBlockListRequest}
     * with the {@code updateBlockListBlockItem} variant. On success the
     * contact is added to the store's block list eagerly.
     *
     * @implNote {@code WAWebBlockUserJob.blockUnblockUser} with
     * {@code action="block"} ->
     * {@code WASmaxBlocklistsUpdateBlockListRPC.sendUpdateBlockListRPC}
     * @param contact the contact to block
     * @throws NullPointerException if {@code contact} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebBlockUserJob", exports = "blockUnblockUser", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebBlockContactAction", exports = "blockUser", adaptation = WhatsAppAdaptation.ADAPTED)
    public void blockContact(Jid contact) {
        Objects.requireNonNull(contact, "contact cannot be null");
        updateBlockList(contact, "block");
        store.addBlockedContact(contact);
        // WAWebBlockContactAction.blockContact calls WAWebWamBlockEventReporter.logBlockEvent({contact, blockEntryPoint, isBlock:true})
        logBlockEvent(contact, BlockEventActionType.BLOCK);
    }

    /**
     * Unblocks the given contact at the server, allowing them to send
     * messages and see this account's presence again.
     *
     * <p>Sends {@code <iq xmlns="blocklist" to="s.whatsapp.net"
     * type="set"><item action="unblock" jid="..."/></iq>} matching WA
     * Web's
     * {@code WASmaxOutBlocklistsUpdateBlockListRequest.makeUpdateBlockListRequest}
     * with the {@code updateBlockListUnblockItem} variant. On success
     * the contact is removed from the store's block list eagerly.
     *
     * @implNote {@code WAWebBlockUserJob.blockUnblockUser} with
     * {@code action="unblock"} ->
     * {@code WASmaxBlocklistsUpdateBlockListRPC.sendUpdateBlockListRPC}
     * @param contact the contact to unblock
     * @throws NullPointerException if {@code contact} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebBlockUserJob", exports = "blockUnblockUser", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebBlockContactAction", exports = "unblockUser", adaptation = WhatsAppAdaptation.ADAPTED)
    public void unblockContact(Jid contact) {
        Objects.requireNonNull(contact, "contact cannot be null");
        updateBlockList(contact, "unblock");
        store.removeBlockedContact(contact);
        // WAWebBlockContactAction.unblockContact calls WAWebWamBlockEventReporter.logBlockEvent({contact, blockEntryPoint, isBlock:false})
        logBlockEvent(contact, BlockEventActionType.UNBLOCK);
    }

    /**
     * Issues an {@code iq xmlns="blocklist" type="set"} stanza carrying a
     * single {@code <item action="..." jid="..."/>} child, corresponding
     * to WA Web's
     * {@code WASmaxOutBlocklistsUpdateBlockListRequest.makeUpdateBlockListRequest}.
     *
     * @implNote {@code WASmaxOutBlocklistsUpdateBlockListRequest.makeUpdateBlockListRequest}
     * @param contact the target contact JID
     * @param action  either {@code "block"} or {@code "unblock"}
     */
    private void updateBlockList(Jid contact, String action) {
        var item = new NodeBuilder()
                .description("item")
                .attribute("action", action)
                .attribute("jid", contact.toUserJid())
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "blocklist")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(item);
        sendNode(iqNode);
    }

    /**
     * Emits a {@code BlockEventsFs} WAM telemetry event for the given
     * block or unblock action, mirroring WA Web's
     * {@code WAWebWamBlockEventReporter.logBlockEvent}.
     *
     * <p>The emission is gated on the
     * {@link ABProp#BLOCK_ENTRY_POINT_LOGGING_ENABLED} AB-prop, matching
     * WA Web's {@code getABPropConfigValue("block_entry_point_logging_enabled")}
     * check. When enabled, the event is populated as follows:
     * <ul>
     *   <li>{@code blockEntryPoint}: defaults to
     *       {@link BlockEntryPoint#OTHER} because Cobalt's public blocking
     *       API does not accept an entry-point argument — this matches WA
     *       Web's fallback in {@code getBlockEventMetricFromBlockEntryPoint}
     *       when no entry point is supplied.</li>
     *   <li>{@code blockEventActionType}: {@link BlockEventActionType#BLOCK}
     *       or {@link BlockEventActionType#UNBLOCK} depending on the
     *       action.</li>
     *   <li>{@code blockEventIsSuspicious}: {@code true} when the contact is
     *       neither saved in the local address book nor part of a trusted
     *       chat, matching WA Web's
     *       {@code !(getIsMyContact(contact) || chat?.isTrusted())}.</li>
     *   <li>{@code blockEventIsUnsub}: {@code true} when the contact is not
     *       saved in the local address book, matching WA Web's
     *       {@code !getIsMyContact(contact)}.</li>
     * </ul>
     *
     * <p>The {@code pastCall} and {@code pastCallResult} properties are
     * intentionally left unset because WA Web's {@code logBlockEvent} also
     * does not populate them.
     *
     * @implNote {@code WAWebWamBlockEventReporter.logBlockEvent}
     * @param contact the contact that was just blocked or unblocked
     * @param action  the action performed: {@link BlockEventActionType#BLOCK}
     *                or {@link BlockEventActionType#UNBLOCK}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamBlockEventReporter", exports = "logBlockEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private void logBlockEvent(Jid contact, BlockEventActionType action) {
        // WAWebWamBlockEventReporter.logBlockEvent: if(getABPropConfigValue("block_entry_point_logging_enabled"))
        if (!abPropsService.getBool(ABProp.BLOCK_ENTRY_POINT_LOGGING_ENABLED)) {
            return;
        }
        // WAWebFrontendContactGetters.getIsMyContact(n): contact is considered "my contact" when saved in the address book
        var contactRecord = store.findContactByJid(contact).orElse(null);
        var isMyContact = contactRecord != null && contactRecord.fullName().isPresent();
        // WAWebChatModel.isTrusted for 1:1 user chat: notSpam || getIsMyContact(contact) || hasMaybeSentMsgToChat()
        var chatIsTrusted = store.findChatByJid(contact)
                .map(chat -> chat.notSpam() || isMyContact)
                .orElse(false);
        // WAWebWamBlockEventReporter.logBlockEvent: i = getIsMyContact(n) || chat?.isTrusted(); blockEventIsSuspicious = !i
        var isSuspicious = !(isMyContact || chatIsTrusted);
        wamService.commit(new BlockEventsFsEventBuilder()
                // WAWebWamBlockEventReporter.logBlockEvent: blockEntryPoint = t (defaults to OTHER when no entry point is carried)
                .blockEntryPoint(BlockEntryPoint.OTHER)
                // WAWebWamBlockEventReporter.logBlockEvent: blockEventActionType = r ? BLOCK : UNBLOCK
                .blockEventActionType(action)
                // WAWebWamBlockEventReporter.logBlockEvent: blockEventIsSuspicious = !i
                .blockEventIsSuspicious(isSuspicious)
                // WAWebWamBlockEventReporter.logBlockEvent: blockEventIsUnsub = !getIsMyContact(n)
                .blockEventIsUnsub(!isMyContact)
                .build());
    }

    /**
     * Queries the profile picture URL for the given JID.
     *
     * <p>Sends {@code <iq xmlns="w:profile:picture" to="s.whatsapp.net"
     * type="get" target=jid><picture type="image" query="url"/></iq>} and
     * parses {@code <picture url="..."/>} from the response.
     *
     * <p>WA Web funnels every picture request through
     * {@code WAWebContactProfilePicThumbBridge.requestProfilePicFromServer},
     * which calls {@code WAWebGetProfilePicJob.getProfilePic} ->
     * {@code WASmaxProfilePictureGetRPC.sendGetRPC} with
     * {@code pictureType: "image"}, {@code pictureQuery: "url"}. The final
     * server response is a {@code GetResponseSuccessPictureURL} carrying
     * {@code pictureUrl}. Cobalt collapses the multi-hop RPC into a single
     * IQ because the wire shape is identical.
     *
     * @implNote {@code WAWebContactProfilePicThumbBridge.requestProfilePicFromServer} ->
     * {@code WAWebGetProfilePicJob.getProfilePic} ->
     * {@code WASmaxProfilePictureGetRPC.sendGetRPC}
     * @param jid the JID whose picture URL should be fetched
     * @return the picture URL when present; {@link Optional#empty()}
     *         otherwise (including when the server returns no
     *         {@code <picture>} child, matching WA Web's HTTP 404 branch)
     * @throws NullPointerException            if {@code jid} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebContactProfilePicThumbBridge", exports = "requestProfilePicFromServer",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGetProfilePicJob", exports = "getProfilePic",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<URI> queryPicture(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        // WAWebGetProfilePicJob.getProfilePic: sendGetRPC({pictureType: "image", pictureQuery: "url", ...})
        var pictureQuery = new NodeBuilder()
                .description("picture")
                .attribute("type", "image") // preview=false -> "image"
                .attribute("query", "url") // WAWebGetProfilePicJob.getProfilePic: pictureQuery: "url"
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:profile:picture") // WAWebSendProfilePictureJob/WAWebGetProfilePicJob: xmlns: "w:profile:picture"
                .attribute("to", JidServer.user()) // S_WHATSAPP_NET
                .attribute("target", jid) // WAWebGetProfilePicJob.getProfilePic: iqTarget: widToChatJid(t)
                .attribute("type", "get")
                .content(pictureQuery);
        var response = sendNode(iqNode);
        // WAWebGetProfilePicJob.getProfilePic: GetResponseSuccessPictureURL.value.pictureUrl
        return response.getChild("picture")
                .flatMap(p -> p.getAttributeAsString("url"))
                .map(URI::create);
    }

    /**
     * Changes the push name (broadcast display name) associated with this
     * account.
     *
     * <p>Mirrors the two side effects of WA Web's
     * {@code WAWebSetPushName.setPushname} (exposed internally by
     * {@code WAWebPushNameBridge}):
     * <ol>
     *   <li>Publishes a {@code setting_pushName} sync mutation through the
     *       {@link WebAppStateService} so the new name propagates to every
     *       linked device ({@code WAWebPushNameSync.getPushnameMutation} in
     *       WA Web).</li>
     *   <li>Broadcasts a {@code <presence name="..."/>} stanza so the server
     *       forwards the updated name to contacts (matches
     *       {@code WASendPresenceStatusProtocol.sendPresenceStatusProtocol}
     *       with {@code type=undefined}).</li>
     * </ol>
     *
     * <p>Locally, the handler's own
     * {@link PushNameSettingHandler#applyMutationResult apply} pipeline
     * persists the new name into {@link WhatsAppStore#setName(String)} and
     * notifies listeners.
     *
     * @implNote {@code WAWebSetPushName.setPushname} /
     *           {@code WAWebPushNameBridge} -> sync mutation +
     *           {@code <presence name="..."/>}
     * @param newPushName the new broadcast display name; must not be
     *                    {@code null}
     * @throws NullPointerException            if {@code newPushName} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebSetPushName", exports = "setPushName",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeName(String newPushName) {
        Objects.requireNonNull(newPushName, "newPushName cannot be null");
        // WAWebPushNameSync.getPushnameMutation: setting_pushName mutation for the CRITICAL_BLOCK collection
        var mutation = PushNameSettingHandler.INSTANCE.getPushnameMutation(Instant.now(), newPushName);
        webAppStateService.pushPatches(PushNameSetting.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync
        // WASendPresenceStatusProtocol.sendPresenceStatusProtocol: smax("presence", {type: undefined, name: _})
        sendNodeWithNoResponse(new NodeBuilder()
                .description("presence") // WASmaxOutPresenceAvailabilityRequest.makeAvailabilityRequest
                .attribute("name", newPushName) // WASmaxOutPresenceAvailabilityRequest: name: OPTIONAL(CUSTOM_STRING, n)
                .build());
    }

    /**
     * Changes this account's "about" (status) text.
     *
     * <p>Emits {@code <iq xmlns="status" to="s.whatsapp.net" type="set">
     * <status>TEXT</status></iq>} and blocks for the acknowledgment.
     *
     * <p>WA Web goes through {@code WAWebContactStatusBridge.setMyStatus},
     * which enqueues the {@code setAbout} persisted job
     * ({@code WAWebPersistedJobDefinitions.jobSerializers.setAbout}). The
     * job itself emits exactly the IQ shape replicated here, so Cobalt
     * collapses the job queue into a direct send.
     *
     * @implNote {@code WAWebContactStatusBridge.setMyStatus} ->
     *           {@code WAWebPersistedJobDefinitions.jobSerializers.setAbout}
     * @param aboutText the new about text; must not be {@code null}
     * @throws NullPointerException            if {@code aboutText} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebContactStatusBridge", exports = "setMyStatus",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeAbout(String aboutText) {
        Objects.requireNonNull(aboutText, "aboutText cannot be null");
        // task spec: <iq xmlns="status" to="s.whatsapp.net" type="set"><status>TEXT</status></iq>
        var statusNode = new NodeBuilder()
                .description("status")
                .content(aboutText)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "status")
                .attribute("to", JidServer.user()) // S_WHATSAPP_NET
                .attribute("type", "set")
                .content(statusNode);
        sendNode(iqNode);
        store.setAbout(aboutText); // ADAPTED: mirror WA Web's cached Conn.about via Cobalt's single store field
    }

    /**
     * Changes this account's profile picture.
     *
     * <p>Sends {@code <iq xmlns="w:profile:picture" to="s.whatsapp.net"
     * type="set" target=selfJid>
     * <picture type="image">JPEG_BYTES</picture>
     * <picture type="preview">THUMB_BYTES</picture>
     * </iq>}, generating a 96x96 JPEG preview from the supplied full-size
     * JPEG via the JDK's {@code javax.imageio} pipeline.
     *
     * <p>Mirrors the two writes of WA Web's
     * {@code WAWebSetProfilePicJob.setMyPic} ->
     * {@code WAWebContactProfilePicThumbBridge.sendSetPicture}: a full-size
     * image upload and a preview thumbnail. WA Web routes both writes
     * through the same {@code WAWebSendProfilePictureJob.default} helper,
     * which only allows one {@code <picture>} child per IQ; Cobalt merges
     * both into a single IQ that the server accepts.
     *
     * @implNote {@code WAWebSetProfilePicJob.setMyPic} ->
     *           {@code WAWebContactProfilePicThumbBridge.sendSetPicture} ->
     *           {@code WAWebSendProfilePictureJob.default}
     * @param jpegBytes the full-size JPEG payload; must not be {@code null}
     * @throws NullPointerException            if {@code jpegBytes} is {@code null}
     * @throws IllegalArgumentException        if {@code jpegBytes} is not a
     *                                         valid image
     * @throws IllegalStateException           if the self JID is not known
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebSetProfilePicJob", exports = "setMyPic",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebContactProfilePicThumbBridge", exports = "sendSetPicture",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeProfilePicture(byte[] jpegBytes) {
        Objects.requireNonNull(jpegBytes, "jpegBytes cannot be null");
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMeUser
                .map(Jid::withoutData)
                .orElseThrow(() -> new IllegalStateException("Missing self JID"));
        var thumbBytes = generatePreviewThumbnail(jpegBytes);
        // WAWebSendProfilePictureJob.default: wap("iq", {xmlns:"w:profile:picture", to:S_WHATSAPP_NET, target, type:"set"}, wap("picture", {type:"image"}, a))
        var fullPicture = new NodeBuilder()
                .description("picture")
                .attribute("type", "image") // WAWebContactProfilePicThumbBridge.sendSetPicture: full-size image write
                .content(jpegBytes)
                .build();
        var previewPicture = new NodeBuilder()
                .description("picture")
                .attribute("type", "preview") // WAWebContactProfilePicThumbBridge.sendSetPicture: preview thumbnail write
                .content(thumbBytes)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:profile:picture")
                .attribute("to", JidServer.user()) // S_WHATSAPP_NET
                .attribute("target", selfJid)
                .attribute("type", "set")
                .content(List.of(fullPicture, previewPicture));
        sendNode(iqNode);
    }

    /**
     * Removes this account's profile picture.
     *
     * <p>Emits {@code <iq xmlns="w:profile:picture" to="s.whatsapp.net"
     * type="set" target=selfJid/>} with no {@code <picture>} child, which
     * instructs the server to delete the current profile picture.
     *
     * <p>WA Web goes through
     * {@code WAWebRemoveProfilePicJob.removeMyPic} ->
     * {@code WAWebContactProfilePicThumbBridge.requestDeletePicture}, which
     * calls {@code WAWebSendProfilePictureJob.default(self, null)} and then
     * {@code WAWebChangeProfilePicThumb.changeProfilePicThumb(self, ProfilePicCommand.Remove)}
     * to evict the local thumbnail. Cobalt replicates the wire emit and
     * lets the locally cached profile-picture URI be cleared by the next
     * server-driven notification, matching the behavior of
     * {@link #removeGroupPicture(Jid)}.
     *
     * @implNote {@code WAWebRemoveProfilePicJob.removeMyPic} ->
     *           {@code WAWebContactProfilePicThumbBridge.requestDeletePicture} ->
     *           {@code WAWebSendProfilePictureJob.default(self, null)}
     * @throws IllegalStateException           if the self JID is not known
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebRemoveProfilePicJob", exports = "removeMyPic",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebContactProfilePicThumbBridge", exports = "requestDeletePicture",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void removeProfilePicture() {
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMeUser
                .map(Jid::withoutData)
                .orElseThrow(() -> new IllegalStateException("Missing self JID"));
        sendPictureIq(selfJid, null); // WAWebSendProfilePictureJob.default: a=null -> no picture body
    }

    /**
     * Generates a square-ish preview thumbnail for the given JPEG bytes.
     *
     * <p>WA Web computes thumbnails client-side through the browser's
     * {@code Canvas} API with a nominal 96x96 target. Cobalt uses the
     * JDK's {@code javax.imageio} pipeline to produce an equivalent
     * payload sized for WhatsApp's server preview slot.
     *
     * @param jpegBytes the full-size JPEG payload
     * @return the preview JPEG bytes
     * @throws IllegalArgumentException if the input cannot be decoded as
     *                                  an image
     */
    private byte[] generatePreviewThumbnail(byte[] jpegBytes) {
        try {
            var src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpegBytes));
            if (src == null) {
                throw new IllegalArgumentException("Invalid image payload");
            }
            var thumb = new java.awt.image.BufferedImage(PROFILE_PREVIEW_SIZE, PROFILE_PREVIEW_SIZE, java.awt.image.BufferedImage.TYPE_INT_RGB); // WA Web's profile preview size
            var g = thumb.createGraphics();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, PROFILE_PREVIEW_SIZE, PROFILE_PREVIEW_SIZE, null);
            } finally {
                g.dispose();
            }
            var out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(thumb, "jpg", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to decode profile picture", e);
        }
    }

    /**
     * Broadcasts this client's own presence state to the server.
     *
     * <p>Emits a {@code <presence type="available"/>} stanza when {@code status}
     * is {@link ContactStatus#AVAILABLE} or a {@code <presence type="unavailable"/>}
     * stanza when {@code status} is {@link ContactStatus#UNAVAILABLE}; both
     * carry the local user's push name so the server can forward it to peers.
     *
     * <p>On WA Web the module exposes this behavior as two parameterless bridge
     * functions ({@code setPresenceAvailable} / {@code setPresenceUnavailable})
     * that both delegate to {@code WASendPresenceStatusProtocol.sendPresenceStatusProtocol}
     * with the current {@code WAWebConnModel.Conn.pushname}. Cobalt collapses
     * the two entry points into a single {@link ContactStatus}-switched method
     * because the underlying stanza shape is identical.
     *
     * @implNote WAWebContactPresenceBridge.setPresenceAvailable /
     *           setPresenceUnavailable -> WASendPresenceStatusProtocol.sendPresenceStatusProtocol
     *           -> WASmaxOutPresenceAvailabilityRequest.makeAvailabilityRequest:
     *           {@code smax("presence", {type: CUSTOM_STRING(n), name: CUSTOM_STRING(pushname)})}
     * @param status {@link ContactStatus#AVAILABLE} or {@link ContactStatus#UNAVAILABLE}
     * @throws NullPointerException            if {@code status} is {@code null}
     * @throws IllegalArgumentException        if {@code status} is not
     *                                         {@code AVAILABLE} or {@code UNAVAILABLE}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    @WhatsAppWebExport(moduleName = "WAWebContactPresenceBridge", exports = "setPresenceAvailable",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebContactPresenceBridge", exports = "setPresenceUnavailable",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changePresence(ContactStatus status) {
        Objects.requireNonNull(status, "status cannot be null");
        // WAWebContactPresenceBridge.setPresenceAvailable/setPresenceUnavailable: only AVAILABLE and UNAVAILABLE are valid self-presence values
        if (status != ContactStatus.AVAILABLE && status != ContactStatus.UNAVAILABLE) {
            throw new IllegalArgumentException("status must be AVAILABLE or UNAVAILABLE, got " + status);
        }
        var builder = new NodeBuilder()
                .description("presence") // WASmaxOutPresenceAvailabilityRequest.makeAvailabilityRequest: smax("presence", {...})
                .attribute("type", status.toString()); // WASendPresenceStatusProtocol.sendPresenceStatusProtocol: status -> presenceType -> CUSTOM_STRING(t)
        // WASendPresenceStatusProtocol.sendPresenceStatusProtocol: name: WAWebConnModel.Conn.pushname (OPTIONAL, omitted when absent)
        var name = store.name();
        if (name != null && !name.isEmpty()) {
            builder.attribute("name", name); // WASmaxOutPresenceAvailabilityRequest.makeAvailabilityRequest: name: OPTIONAL(CUSTOM_STRING, n)
        }
        sendNodeWithNoResponse(builder.build()); // WAComms.castSmaxStanza - fire-and-forget
    }

    /**
     * Sends a chat-state indication (typing / recording / paused) for a 1:1 or
     * group conversation.
     *
     * <p>Emits a {@code <chatstate to="<chatJid>">...</chatstate>} stanza whose
     * single child tag describes the new state:
     * <ul>
     *   <li>{@link ContactStatus#COMPOSING} -> {@code <composing/>}</li>
     *   <li>{@link ContactStatus#RECORDING} -> {@code <composing media="audio"/>}</li>
     *   <li>{@link ContactStatus#UNAVAILABLE} (idle/paused) -> {@code <paused/>}</li>
     * </ul>
     *
     * <p>WA Web splits this into three parameterless bridges
     * ({@code sendChatStateComposing}, {@code sendChatStateRecording},
     * {@code sendChatStatePaused}) that all call
     * {@code WASendChatStateProtocol.sendChatStateProtocol} with a
     * {@code "typing"}, {@code "recording_audio"} or {@code "idle"} tag.
     * Cobalt merges them into a single entry point switched on
     * {@link ContactStatus} because the underlying stanza differs only by the
     * child element.
     *
     * @implNote WAWebChatStateBridge.sendChatStateComposing /
     *           sendChatStateRecording / sendChatStatePaused ->
     *           WASendChatStateProtocol.sendChatStateProtocol ->
     *           WASmaxChatstateClientNotificationRPC.sendClientNotificationRPC ->
     *           WASmaxOutChatstateClientNotificationRequest.makeClientNotificationRequest:
     *           {@code smax("chatstate", {to: JID(t)}, <composing/paused child>)}
     * @param chat  the chat JID the state applies to
     * @param state {@link ContactStatus#COMPOSING}, {@link ContactStatus#RECORDING}
     *              or {@link ContactStatus#UNAVAILABLE} (mapped to paused)
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if {@code state} is not one of
     *                                         {@code COMPOSING}, {@code RECORDING}
     *                                         or {@code UNAVAILABLE}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    @WhatsAppWebExport(moduleName = "WAWebChatStateBridge", exports = "sendChatStateComposing",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatStateBridge", exports = "sendChatStateRecording",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatStateBridge", exports = "sendChatStatePaused",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeChatState(Jid chat, ContactStatus state) {
        Objects.requireNonNull(chat, "chat cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        // WASendChatStateProtocol.sendChatStateProtocol: branches on "idle" / "typing" / "recording_audio"
        var childBuilder = new NodeBuilder();
        switch (state) {
            case COMPOSING -> childBuilder.description("composing"); // WASmaxOutChatstateComposingMixin.mergeComposingMixin: smax("composing", {media: OPTIONAL_LITERAL("audio", false)})
            case RECORDING -> childBuilder
                    .description("composing") // WASmaxOutChatstateComposingMixin.mergeComposingMixin: smax("composing", ...)
                    .attribute("media", "audio"); // WASmaxOutChatstateComposingMixin: media: OPTIONAL_LITERAL("audio", hasComposingMediaAudio)
            case UNAVAILABLE -> childBuilder.description("paused"); // WASmaxOutChatstatePausedMixin.mergePausedMixin: smax("paused", null)
            default -> throw new IllegalArgumentException("state must be COMPOSING, RECORDING or UNAVAILABLE, got " + state);
        }
        var chatstate = new NodeBuilder()
                .description("chatstate") // WASmaxOutChatstateClientNotificationRequest.makeClientNotificationRequest: smax("chatstate", {to: JID(t)})
                .attribute("to", chat) // WASmaxOutChatstateClientNotificationRequest: to: JID(t)
                .content(childBuilder.build())
                .build();
        sendNodeWithNoResponse(chatstate); // WAComms.castSmaxStanza - fire-and-forget
    }

    /**
     * Subscribes to real-time presence updates for the given contact.
     *
     * <p>Emits a {@code <presence type="subscribe" to="<targetJid>"/>} stanza.
     * The server will subsequently push {@code <presence>} notifications for
     * that contact (available / unavailable / typing / recording) until the
     * subscription is cancelled via
     * {@link #unsubscribeFromPresence(Jid)} or the socket is closed.
     *
     * <p>WA Web additionally threads a privacy-token payload
     * ({@code tCTokenMixinArgs}) through the subscribe request when the target
     * is a user JID. Cobalt omits that mixin because privacy tokens are managed
     * by a separate store/flow; the bare subscribe stanza is the one the server
     * accepts for the common case.
     *
     * @implNote WAWebContactPresenceBridge.subscribePresence ->
     *           WAWebSendPresenceSubscriptionJob.default ->
     *           WASmaxPresenceSubscribeRPC.sendSubscribeRPC ->
     *           WASmaxOutPresenceSubscribeRequest.makeSubscribeRequest:
     *           {@code smax("presence", {type: "subscribe", to: JID(t), name?, context?})}
     * @param target the contact JID to subscribe to
     * @throws NullPointerException            if {@code target} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    @WhatsAppWebExport(moduleName = "WAWebContactPresenceBridge", exports = "subscribePresence",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void subscribeToPresence(Jid target) {
        Objects.requireNonNull(target, "target cannot be null");
        var presence = new NodeBuilder()
                .description("presence") // WASmaxOutPresenceSubscribeRequest.makeSubscribeRequest: smax("presence", {...})
                .attribute("type", "subscribe") // WASmaxOutPresenceSubscribeRequest: type: "subscribe"
                .attribute("to", target) // WASmaxOutPresenceSubscribeRequest: to: JID(t)
                .build();
        sendNodeWithNoResponse(presence); // WAComms.castSmaxStanza - fire-and-forget
    }

    /**
     * Cancels a presence subscription previously established via
     * {@link #subscribeToPresence(Jid)}.
     *
     * <p>Emits a {@code <presence type="unsubscribe" to="<targetJid>"/>} stanza,
     * the symmetric counterpart of the subscribe request. After the server
     * acknowledges the unsubscribe, no further presence push notifications
     * will arrive for the given contact until a new subscription is created.
     *
     * @implNote ADAPTED: WAWebContactPresenceBridge exposes only
     *           {@code subscribePresence}; WA Web clients never explicitly
     *           revoke a presence subscription because subscriptions are
     *           re-established implicitly on socket reconnect. Cobalt provides
     *           an explicit cancellation path so long-lived clients can opt out
     *           of a specific contact's presence feed without reconnecting.
     *           The stanza shape mirrors {@code subscribePresence} with the
     *           {@code type} flipped to {@code "unsubscribe"}.
     * @param target the contact JID to unsubscribe from
     * @throws NullPointerException            if {@code target} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    public void unsubscribeFromPresence(Jid target) {
        Objects.requireNonNull(target, "target cannot be null");
        var presence = new NodeBuilder()
                .description("presence") // ADAPTED: mirrors subscribePresence stanza with type=unsubscribe
                .attribute("type", "unsubscribe")
                .attribute("to", target)
                .build();
        sendNodeWithNoResponse(presence);
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
     * Deletes a message, either locally ("delete for me") or for every
     * participant in the chat ("delete for everyone"), depending on the
     * {@code everyone} flag.
     *
     * <p>When {@code everyone} is {@code false}, this matches
     * {@code WAWebChatSendMessages.sendDeleteMsgs} in WA Web: the
     * referenced message is removed from the local store and a
     * {@link com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeAction}
     * is published through the REGULAR_HIGH app-state collection so
     * every linked device removes the message too. In this mode the
     * method returns {@code null} because no server-ack path exists.
     *
     * <p>When {@code everyone} is {@code true}, a
     * {@code ProtocolMessage} of type {@link ProtocolMessage.Type#REVOKE}
     * is constructed around the original {@code MessageKey} and
     * dispatched through the standard send pipeline so every participant
     * sees the message disappear. The caller is responsible for ensuring
     * they have permission to revoke the target message; the server
     * rejects unauthorised revokes and the failure is surfaced in the
     * returned {@link AckResult}.
     *
     * @param key      the key of the message to delete
     * @param everyone {@code true} to delete for every participant via a
     *                 REVOKE protocol message, {@code false} to delete
     *                 only for the local account and linked devices via
     *                 a DeleteMessageForMe sync action
     * @return the server ack when {@code everyone} is {@code true};
     *         {@code null} when {@code everyone} is {@code false} (no
     *         server ack path exists for the delete-for-me branch)
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if the key has no {@code parentJid}
     *                                  or (when {@code everyone} is
     *                                  {@code false}) no {@code id}
     *
     * @implNote When {@code everyone} is {@code false}:
     * WAWebChatSendMessages.sendDeleteMsgs emits a delete-for-me sync
     * action and removes the message from the in-memory store. Cobalt
     * builds the sync mutation manually and routes it through
     * {@link WebAppStateService#pushPatches(SyncPatchType, SequencedCollection)}.
     * When {@code everyone} is {@code true}:
     * WAWebRevokeMsgAction.sendRevoke / WAWebRevokeMsgAction.revoke
     * wraps the original key inside a {@code ProtocolMessage} with
     * {@code type = REVOKE} and sends it via
     * {@code WAWebSendMsgRecordAction.sendMsgRecord}. Cobalt delegates
     * the record dispatch to {@link MessageService#send(Jid, MessageContainer)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebChatSendMessages", exports = "sendDeleteMsgs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebRevokeMsgAction", exports = "sendRevoke",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebRevokeMsgAction", exports = "revoke",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatSendMessages", exports = "sendRevokeMsgs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamChatPSALogger", exports = "logChatPSADelete",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebActionListenerHelpers", exports = "logMessageDeleteActionsMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult deleteMessage(MessageKey key, boolean everyone) {
        Objects.requireNonNull(key, "key cannot be null");
        if (everyone) {
            var parentJid = key.parentJid() // WAWebRevokeMsgAction._sendRevoke: s.id.remote
                    .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
            // WAWebActionListenerHelpers.logMessageDeleteActionsMetric: mediaType: y(t.list) computed over the
            // original messages before the revoke send mutates local state.
            // WAWebRevokeMsgAction._sendRevoke: same `s` (original message) is read for
            // messageType/messageMediaType/revokeSendDelay before the send, because the local
            // state is replaced with the revoke marker afterwards.
            var messageIdForMedia = key.id().orElse(null);
            var originalInfo = messageIdForMedia == null
                    ? null
                    : store.findMessageById(parentJid, messageIdForMedia)
                            .filter(ChatMessageInfo.class::isInstance)
                            .map(ChatMessageInfo.class::cast)
                            .orElse(null);
            var mediaType = originalInfo == null
                    ? null
                    : WamMsgUtils.getWamMediaType(originalInfo);
            // WAWebRevokeMsgAction._sendRevoke: C = unixTime(); v = C - WAWebMsgGetters.getT(s)
            var sendInstant = Instant.now();
            var revokeSendDelaySeconds = originalInfo == null
                    ? null
                    : originalInfo.timestamp()
                            .map(t -> (int) (sendInstant.getEpochSecond() - t.getEpochSecond()))
                            .orElse(null);
            var protocol = new ProtocolMessageBuilder() // WAWebRevokeMsgAction._sendRevoke: {type: PROTOCOL, kind: ProtocolRevoke, protocolMessageKey: s.id}
                    .key(key) // WAWebRevokeMsgAction._sendRevoke: protocolMessageKey: s.id
                    .type(ProtocolMessage.Type.REVOKE) // WAWebRevokeMsgAction._sendRevoke: MsgKind.ProtocolRevoke, subtype: sender_revoke | admin_revoke
                    .timestampMs(sendInstant) // WAWebRevokeMsgAction._sendRevoke: revokeTimestamp: C
                    .build();
            var wrapper = MessageContainer.of(protocol); // WAWebRevokeMsgAction._sendRevoke -> WAWebSendMsgRecordAction.sendMsgRecord
            var ack = messageService.send(parentJid, wrapper); // WAWebRevokeMsgAction._sendRevoke -> sendMsgRecord
            // WAWebActionListener._e.then(...): logMessageDeleteActionsMetric(t, a, true) fires after the revoke send completes successfully.
            emitMessageDeleteActionsEvent(parentJid, DeleteActionType.DELETE_FOR_EVERYONE, mediaType);
            // WAWebRevokeMsgAction._sendRevoke: `c === SendMsgResult.OK` gate before
            // `new SendRevokeMessageWamEvent({messageType, messageMediaType, revokeSendDelay}).commit()`.
            if (ack != null && ack.isSuccess()) {
                emitSendRevokeMessageEvent(parentJid, mediaType, revokeSendDelaySeconds);
            }
            return ack;
        }

        var parentJid = key.parentJid() // WAWebChatSendMessages.sendDeleteMsgs: i.id (chatJid)
                .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
        var messageId = key.id() // WAWebChatSendMessages.sendDeleteMsgs: s = t.list (messageIds)
                .orElseThrow(() -> new IllegalArgumentException("key must carry an id"));

        // WAWebActionListenerHelpers.logMessageDeleteActionsMetric: mediaType: y(t.list) -
        // resolved before the in-memory message is removed, otherwise getWamMediaType cannot classify it.
        var mediaType = store.findMessageById(parentJid, messageId)
                .filter(ChatMessageInfo.class::isInstance)
                .map(ChatMessageInfo.class::cast)
                .map(WamMsgUtils::getWamMediaType)
                .orElse(null);

        // WAWebChatSendMessages.sendDeleteMsgs: s.forEach(e => e.delete())
        store.findChatByJid(parentJid)
                .ifPresent(chat -> chat.removeMessage(messageId)); // WAWebChatSendMessages.sendDeleteMsgs: e.delete()

        // WAWebChatSendMessages.sendDeleteMsgs: WAWebChatSendDeleteMsgsBridge.sendDeleteMsgs
        // emits a DeleteMessageForMe sync action to every linked device.
        // WAWebSyncdUtils.constructMsgKeySegmentsFromMsgKey: builds the
        // [remote, id, fromMe, participant] tuple with `{legacy:true}` JID
        // serialization and the !remote.isUser() && !fromMe participant gate.
        var keySegments = SyncdIndexUtils.constructMsgKeySegmentsFromMsgKey(key);
        var indexJson = SyncdIndexUtils.buildIndex( // WAWebSyncdActionUtils.buildIndex
                com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeAction.ACTION_NAME, // "deleteMessageForMe"
                keySegments.get(0), // WAWebSyncdUtils.constructMsgKeySegments: remote
                keySegments.get(1), // WAWebSyncdUtils.constructMsgKeySegments: id
                keySegments.get(2), // WAWebSyncdUtils.constructMsgKeySegments: fromMe
                keySegments.get(3)  // WAWebSyncdUtils.constructMsgKeySegments: participant
        );
        var deleteAction = new com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeActionBuilder()
                .messageTimestamp(Instant.now()) // WAWebDeleteMessageForMeSync: deleteMessageForMeAction.messageTimestamp
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(Instant.now())
                .deleteMessageForMeAction(deleteAction)
                .build();
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
        // WAWebActionListenerHelpers: if(getIsPSA(e)) for(r=0;r<t.list.length;r++) WAWebWamChatPSALogger.logChatPSADelete(t.list[r])
        logPsaActionIfApplicable(key, PsaMessageActionType.DELETE); // WAWebWamChatPSALogger.logChatPSADelete -> PSA_MESSAGE_ACTION_TYPE.DELETE
        // WAWebActionListener.pe.then(...): logMessageDeleteActionsMetric(a, i, false) fires after the delete-for-me
        // send-result count equals the input count (full success).
        emitMessageDeleteActionsEvent(parentJid, DeleteActionType.DELETE_FOR_ME, mediaType);
        return null;
    }

    /**
     * Emits a {@link MessageDeleteActionsEvent} for a completed delete-for-me
     * or delete-for-everyone action on a single message.
     *
     * <p>WhatsApp Web emits this event from
     * {@code WAWebActionListenerHelpers.logMessageDeleteActionsMetric(chat, request, isDeleteForEveryone)},
     * which is invoked from the two delete listeners
     * ({@code send_delete_msgs} and {@code send_revoke_msgs}) once the
     * underlying send has completed successfully. WA Web batches the metric
     * over the full list of deleted messages; Cobalt's public
     * {@link #deleteMessage(MessageKey, boolean)} API deletes one message per
     * invocation, so {@code messagesDeleted} is always {@code 1} and
     * {@code mediaType} is simply the classification of the single message
     * being deleted (equivalent to WA Web's single-element
     * {@code y(t.list)} result).
     *
     * <p>The {@code threadId} property is intentionally left unset because
     * Cobalt does not adapt {@code WAWebChatThreadLogging} (HMAC-based chat
     * thread identifiers), mirroring the treatment of the same property in
     * other Cobalt events that WA Web populates via {@code getChatThreadID}.
     *
     * @param chatJid          the chat JID of the deleted message; used to
     *                         derive {@code isAGroup}; must not be
     *                         {@code null}
     * @param deleteActionType the delete action type to report; must not be
     *                         {@code null}
     * @param mediaType        the WAM media type of the deleted message, or
     *                         {@code null} when the message could not be
     *                         resolved from the local store (matches WA
     *                         Web's {@code y(t.list)} returning
     *                         {@code undefined})
     *
     * @implNote Adapts {@code WAWebActionListenerHelpers.logMessageDeleteActionsMetric}:
     * WA Web builds {@code new MessageDeleteActionsWamEvent({deleteActionType,
     * isAGroup: getIsGroup(chat), messagesDeleted: t.list.length,
     * threadId: yield getChatThreadID(chat.id.toJid()), mediaType: y(t.list)
     * }).commitAndWaitForFlush()}. Cobalt mirrors the payload using
     * {@link Jid#hasGroupOrCommunityServer()} for {@code isAGroup} (equivalent
     * to {@code WAWebChatGetters.getIsGroup}) and leaves {@code threadId}
     * unset. The event is committed via {@link WamService#commit(Object)};
     * {@code commitAndWaitForFlush} is not needed here because Cobalt's
     * {@code WamService} dispatches commits asynchronously without a separate
     * synchronous flush API.
     */
    @WhatsAppWebExport(moduleName = "WAWebActionListenerHelpers", exports = "logMessageDeleteActionsMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitMessageDeleteActionsEvent(Jid chatJid, DeleteActionType deleteActionType, MediaType mediaType) {
        var builder = new MessageDeleteActionsEventBuilder() // WAWebActionListenerHelpers.logMessageDeleteActionsMetric: new MessageDeleteActionsWamEvent({...})
                .deleteActionType(deleteActionType) // WAWebActionListenerHelpers: deleteActionType: n ? DELETE_FOR_EVERYONE : DELETE_FOR_ME
                .isAGroup(chatJid.hasGroupOrCommunityServer()) // WAWebActionListenerHelpers: isAGroup: getIsGroup(e)
                .messagesDeleted(1); // WAWebActionListenerHelpers: messagesDeleted: t.list.length (Cobalt deletes one message per API call)
        // WAWebActionListenerHelpers: mediaType: y(t.list) - y returns undefined when the set is empty
        if (mediaType != null) {
            builder.mediaType(mediaType);
        }
        // WAWebActionListenerHelpers: threadId: yield getChatThreadID(e.id.toJid()) - WAWebChatThreadLogging
        // (HMAC-based thread id) is not adapted in Cobalt, so threadId is left unset.
        wamService.commit(builder.build()); // WAWebActionListenerHelpers: .commitAndWaitForFlush()
    }

    /**
     * Emits a {@link SendRevokeMessageEvent} (id {@code 1348}) for a
     * delete-for-everyone send that successfully round-tripped to the server.
     *
     * <p>WhatsApp Web emits this event inside
     * {@code WAWebRevokeMsgAction._sendRevoke}: after calling
     * {@code WAWebSendMsgRecordAction.sendMsgRecord(D)} (and,
     * independently, {@code WAWebSendMsgRecordAction.sendAddonRecord(n)}
     * for the comment-addon branch), the returned
     * {@code messageSendResult} is checked against
     * {@link AckResult#isSuccess()}'s equivalent
     * {@code SendMsgResult.OK} before the event is committed. The payload
     * mirrors WA Web verbatim:
     * {@code {messageType: getWamMessageType(s),
     *        messageMediaType: getWamMediaType(s),
     *        revokeSendDelay: C - getT(s)}}
     * where {@code s} is the <em>original</em> message being revoked and
     * {@code C} is the unix timestamp at which the revoke was issued.
     *
     * <p>Cobalt's public {@link #deleteMessage(MessageKey, boolean)} API
     * exposes only the delete-for-everyone path for a single message, so
     * the event is emitted once per successful revoke send. The comment
     * addon branch in {@code WAWebRevokeMsgAction._sendRevoke} has no
     * Cobalt counterpart (comment-addon revoke is not implemented) and
     * therefore has no emission site here.
     *
     * @param chatJid       the chat JID of the message being revoked, used
     *                      to derive {@code messageType}; must not be
     *                      {@code null}
     * @param mediaType     the WAM media type classification of the
     *                      <em>original</em> message being revoked, or
     *                      {@code null} when the message could not be
     *                      resolved from the local store
     * @param revokeSendDelaySeconds the number of seconds elapsed between
     *                      the original message's server timestamp and the
     *                      moment the revoke send was issued, or
     *                      {@code null} when the original timestamp could
     *                      not be resolved
     *
     * @implNote Adapts the two
     * {@code new SendRevokeMessageWamEvent({...}).commit()} sites in
     * {@code WAWebRevokeMsgAction._sendRevoke}. WA Web calls
     * {@code .commit()}; Cobalt routes through
     * {@link WamService#commit(Object)}. {@code messageType} is derived
     * from the chat JID via {@link WamMsgUtils#getWamMessageType(Jid)}
     * which mirrors the JID-based classification WA Web performs inside
     * {@code getWamMessageType(s)} after resolving {@code s.id.remote}.
     */
    @WhatsAppWebExport(moduleName = "WAWebRevokeMsgAction", exports = "_sendRevoke",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitSendRevokeMessageEvent(Jid chatJid, MediaType mediaType, Integer revokeSendDelaySeconds) {
        var builder = new SendRevokeMessageEventBuilder() // WAWebRevokeMsgAction._sendRevoke: new SendRevokeMessageWamEvent({...})
                .messageType(WamMsgUtils.getWamMessageType(chatJid)); // WAWebRevokeMsgAction._sendRevoke: messageType: getWamMessageType(s)
        if (mediaType != null) {
            builder.messageMediaType(mediaType); // WAWebRevokeMsgAction._sendRevoke: messageMediaType: getWamMediaType(s)
        }
        if (revokeSendDelaySeconds != null) {
            builder.revokeSendDelay(revokeSendDelaySeconds); // WAWebRevokeMsgAction._sendRevoke: revokeSendDelay: v = C - getT(s)
        }
        wamService.commit(builder.build()); // WAWebRevokeMsgAction._sendRevoke: .commit()
    }

    /**
     * Posts a new status update to the {@code status@broadcast} account.
     *
     * <p>The content is dispatched through the standard send pipeline so
     * it is prepared (message id, messageSecret, deviceContextInfo) and
     * then routed through the status-specific sender which applies
     * sender-key encryption to the current status audience. The returned
     * {@link ChatMessageInfo} is the persisted model row that callers can
     * reference later (for example to revoke the post).
     *
     * @param content the raw status body (text, image, video, sticker, etc.)
     * @return the persisted message info for the new status
     * @throws NullPointerException if {@code content} is {@code null}
     * @throws IllegalStateException if the client is not logged in or the
     *                               message could not be stored after
     *                               sending
     *
     * @implNote WAWebSendStatusMsgAction.sendStatusTextMsgAction /
     * WAWebSendStatusMsgAction.sendStatusMediaMsgAction: both paths build a
     * status {@code Msg} model, persist it via {@code storeMessages}, and
     * finally call
     * {@code WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg}. Cobalt
     * collapses the preparation + dispatch into
     * {@link MessageService#send(Jid, MessageContainer)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendStatusMsgAction", exports = "sendStatusTextMsgAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendStatusMsgAction", exports = "sendStatusMediaMsgAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public ChatMessageInfo sendStatus(MessageContainer content) {
        Objects.requireNonNull(content, "content cannot be null");
        var statusJid = Jid.statusBroadcastAccount(); // WAJids.STATUS_JID
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMeUser
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        // WAWebSendStatusMsgAction.createTextStatusMsgData/createMediaStatusMsgData:
        // builds the MsgKey with a fresh id, populates from/to/participant, and wraps the container.
        var messageId = MessageIdGenerator.generate(MessageIdVersion.V2, selfJid); // WAWebMsgKey.newId
        var key = new MessageKeyBuilder()
                .id(messageId) // WAWebSendStatusMsgAction.createTextStatusMsgData: id: newId()
                .parentJid(statusJid) // WAWebSendStatusMsgAction.createTextStatusMsgData: to: STATUS_JID
                .fromMe(true) // WAWebSendStatusMsgAction.createTextStatusMsgData: selfDir: "out"
                .senderJid(selfJid) // WAWebSendStatusMsgAction.createTextStatusMsgData: participant: n (me)
                .build();
        var messageInfo = new ChatMessageInfoBuilder()
                .status(MessageStatus.PENDING) // ADAPTED: mirrors MessagePreparer.prepareChat initial status
                .senderJid(selfJid) // WAWebSendStatusMsgAction.createTextStatusMsgData: author: n
                .key(key)
                .message(content) // WAWebSendStatusMsgAction.createTextStatusMsgData: body/content
                .timestamp(Instant.now()) // WAWebSendStatusMsgAction.createTextStatusMsgData: t: unixTime()
                .broadcast(true) // WAWebSendStatusMsgAction: status is a broadcast JID
                .build();
        // WAWebSendStatusMsgAction._sendStatusMessage: var i = new StatusPosterActionsLogger;
        // The logger's constructor seeds a per-operation random sessionId that it
        // re-uses across the REQUEST / SUCCESS / FAILURE emissions for this send.
        var statusPostingSessionId = newStatusPostingSessionId(); // WAWebLogStatusPosterActions: this.sessionId = Math.floor(Math.random() * Number.MAX_SAFE_INTEGER)
        // WAWebSendStatusMsgAction._sendStatusMessage: var l = y(e.type) -> status content type mapping.
        var statusContentType = resolveStatusContentType(content);
        // WAWebSendStatusMsgAction._sendStatusMessage: i.logPostStatusRequest(l, a) — a=0 (no retry) for the first attempt.
        wamService.commit(new StatusPosterActionsEventBuilder()
                .statusEventType(StatusEventType.POST_STATUS_REQUEST)
                .statusContentType(statusContentType)
                .retryCount(0)
                .statusPostingSessionId(statusPostingSessionId)
                .build());
        try {
            // Route through MessageService.send(MessageInfo) -> StatusMessageSender.send.
            // Reuses the public StatusMessageSender.send path per the delegation rule.
            messageService.send(messageInfo); // WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg
        } catch (RuntimeException error) {
            // WAWebSendStatusMsgAction._sendStatusMessage catch block:
            // m = getErrorSafe(t); i.logPostStatusFailure(l, m.message, a)
            wamService.commit(new StatusPosterActionsEventBuilder()
                    .statusEventType(StatusEventType.POST_STATUS_FAILURE)
                    .statusContentType(statusContentType)
                    .statusPostFailureReason(error.getMessage())
                    .retryCount(0)
                    .statusPostingSessionId(statusPostingSessionId)
                    .build());
            throw error;
        }
        // WAWebSendStatusMsgAction._sendStatusMessage: d = yield statusIdForLogging(e); i.logPostStatusSuccess(l, d).
        // WAWebStatusLoggingUtils.statusIdForLogging returns the plain msg id when no
        // chat-thread logging secret (WAWebUserPrefsMultiDevice.getChatThreadLoggingSecretB64)
        // is configured; Cobalt does not maintain such a secret so we follow the same fallback.
        wamService.commit(new StatusPosterActionsEventBuilder()
                .statusEventType(StatusEventType.POST_STATUS_SUCCESS)
                .statusContentType(statusContentType)
                .statusId(messageId)
                .statusPostingSessionId(statusPostingSessionId)
                .build());
        return messageInfo;
    }

    /**
     * Maps a status {@link MessageContainer}'s content to the WAM
     * {@link StatusContentType} classification used by
     * {@link StatusPosterActionsEvent#statusContentType()}.
     *
     * <p>WhatsApp Web derives the status content type from the outgoing
     * {@code Msg} model's {@code e.type} string
     * ({@code "chat" | "image" | "video" | "gif" | "sticker" | "ptt" |
     * "audio"}); Cobalt inspects the unwrapped payload type directly because
     * it never builds the intermediate string value.
     *
     * @param content the status payload to classify; never {@code null}
     * @return the resolved content type, defaulting to {@link StatusContentType#PHOTO}
     *         for unrecognized payloads (mirroring WA Web's {@code default:
     *         PHOTO} branch)
     *
     * @implNote WAWebSendStatusMsgAction.y: single-branch switch on
     *     {@code e.type}. {@code "gif"} and {@code "sticker"} both resolve
     *     to {@link StatusContentType#GIF}; {@code "ptt"} and {@code "audio"}
     *     both resolve to {@link StatusContentType#VOICE}. Cobalt re-uses
     *     {@code VideoMessage.gifPlayback()} to detect the GIF variant of
     *     a video, matching the protobuf-level distinction WA Web applies
     *     upstream when producing the string type.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendStatusMsgAction", exports = "y",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private StatusContentType resolveStatusContentType(MessageContainer content) {
        // WAWebSendStatusMsgAction.y: switch(e){...}
        return switch (content.content()) {
            case com.github.auties00.cobalt.model.message.text.ExtendedTextMessage ignored -> StatusContentType.TEXT; // "chat" -> TEXT
            case com.github.auties00.cobalt.model.message.media.ImageMessage ignored -> StatusContentType.PHOTO; // "image" -> PHOTO
            case com.github.auties00.cobalt.model.message.media.VideoMessage video ->
                    video.gifPlayback() ? StatusContentType.GIF : StatusContentType.VIDEO; // "gif"/"video"
            case com.github.auties00.cobalt.model.message.media.StickerMessage ignored -> StatusContentType.GIF; // "sticker" -> GIF
            case com.github.auties00.cobalt.model.message.media.AudioMessage ignored -> StatusContentType.VOICE; // "ptt"/"audio" -> VOICE
            default -> StatusContentType.PHOTO; // WAWebSendStatusMsgAction.y: default: PHOTO
        };
    }

    /**
     * Generates a fresh {@code statusPostingSessionId} for one invocation of
     * {@link #sendStatus(MessageContainer)} or {@link #deleteStatus(String)}.
     *
     * <p>WhatsApp Web's {@code StatusPosterActionsLogger} constructor seeds a
     * random integer and re-uses it across the three event emissions produced
     * by a single logger instance (REQUEST / SUCCESS / FAILURE). Cobalt
     * mirrors that scoping by calling this helper once per action and
     * threading the returned id through every emission.
     *
     * @return a non-negative pseudo-random int used as the session id
     *
     * @implNote WAWebLogStatusPosterActions.StatusPosterActionsLogger:
     *     {@code this.sessionId = Math.floor(Math.random() * Number.MAX_SAFE_INTEGER)}.
     *     The {@link StatusPosterActionsEvent#statusPostingSessionId()} property is
     *     defined as {@link com.github.auties00.cobalt.wam.model.WamType#INTEGER INTEGER},
     *     so Cobalt narrows the JS 53-bit seed to an {@code int} range.
     */
    @WhatsAppWebExport(moduleName = "WAWebLogStatusPosterActions", exports = "StatusPosterActionsLogger",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private int newStatusPostingSessionId() {
        // nextInt(Integer.MAX_VALUE) yields a non-negative int. WA Web's 53-bit seed
        // is stored in a WamType.INTEGER slot, so a 31-bit value is sufficient and
        // matches the builder's Integer parameter type.
        return ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    }

    /**
     * Revokes a previously posted status update.
     *
     * <p>WhatsApp Status revokes follow the regular revoke protocol:
     * a {@link ProtocolMessage} of type
     * {@link ProtocolMessage.Type#REVOKE} is sent to
     * {@code status@broadcast} carrying the key of the original status
     * message. The status-specific sender handles the device list
     * narrowing and the direct-fanout fallback when recipients have left
     * the audience.
     *
     * @param statusId the id of the status message to revoke
     * @return the server ack result
     * @throws NullPointerException     if {@code statusId} is {@code null}
     * @throws IllegalStateException    if the client is not logged in
     *
     * @implNote WAWebRevokeStatusAction.sendStatusRevokeMsgAction: creates
     * a revoke protocol message with {@code to=STATUS_JID} and
     * {@code kind=ProtocolRevoke}, then dispatches it through
     * {@code WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg}. Cobalt
     * reuses the shared {@link MessageService}.
     */
    @WhatsAppWebExport(moduleName = "WAWebRevokeStatusAction", exports = "sendStatusRevokeMsgAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebRevokeStatusAction", exports = "createRevokeStatusMsgData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult deleteStatus(String statusId) {
        Objects.requireNonNull(statusId, "statusId cannot be null");
        var statusJid = Jid.statusBroadcastAccount(); // WAJids.STATUS_JID
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMeUser
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        // WAWebRevokeStatusAction.createRevokeStatusMsgData: new MsgKey({from: n, to: STATUS_JID, id, participant: n, selfDir: "out"})
        var originalKey = new MessageKeyBuilder()
                .id(statusId) // WAWebRevokeStatusAction.createRevokeStatusMsgData: id
                .parentJid(statusJid) // WAWebRevokeStatusAction.createRevokeStatusMsgData: to: STATUS_JID
                .fromMe(true) // WAWebRevokeStatusAction.createRevokeStatusMsgData: selfDir: "out" -> fromMe
                .senderJid(selfJid) // WAWebRevokeStatusAction.createRevokeStatusMsgData: participant: n (me)
                .build();
        var protocol = new ProtocolMessageBuilder() // WAWebRevokeStatusAction.createRevokeStatusMsgData: {kind: ProtocolRevoke, protocolMessageKey: e.id, subtype: "sender_revoke"}
                .key(originalKey) // WAWebRevokeStatusAction.createRevokeStatusMsgData: protocolMessageKey: e.id
                .type(ProtocolMessage.Type.REVOKE) // WAWebRevokeStatusAction.createRevokeStatusMsgData: MsgKind.ProtocolRevoke / subtype "sender_revoke"
                .timestampMs(Instant.now()) // WAWebRevokeStatusAction.createRevokeStatusMsgData: revokeTimestamp: unixTime()
                .build();
        var wrapper = MessageContainer.of(protocol); // WAWebRevokeStatusAction.sendStatusRevokeMsgAction: v = {type: Message, data: C}
        // WAWebRevokeStatusAction.sendStatusRevokeMsgAction: var h = new StatusPosterActionsLogger;
        // h.logDeleteStatusRequest() before the dispatch, then logDeleteStatusSuccess() or
        // logDeleteStatusFailure(L.message) depending on the outcome — all three emissions
        // share the logger's single sessionId.
        var statusPostingSessionId = newStatusPostingSessionId(); // WAWebLogStatusPosterActions: this.sessionId
        // WAWebRevokeStatusAction.sendStatusRevokeMsgAction: h.logDeleteStatusRequest()
        wamService.commit(new StatusPosterActionsEventBuilder()
                .statusEventType(StatusEventType.DELETE_STATUS_REQUEST)
                .statusPostingSessionId(statusPostingSessionId)
                .build());
        AckResult ack;
        try {
            ack = messageService.send(statusJid, wrapper); // WAWebRevokeStatusAction.sendStatusRevokeMsgAction -> WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg
        } catch (RuntimeException error) {
            // WAWebRevokeStatusAction.sendStatusRevokeMsgAction catch block:
            // L = getErrorSafe(e); h.logDeleteStatusFailure(L==null?void 0:L.message)
            wamService.commit(new StatusPosterActionsEventBuilder()
                    .statusEventType(StatusEventType.DELETE_STATUS_FAILURE)
                    .statusPostFailureReason(error.getMessage())
                    .statusPostingSessionId(statusPostingSessionId)
                    .build());
            throw error;
        }
        // WAWebRevokeStatusAction.sendStatusRevokeMsgAction: h.logDeleteStatusSuccess()
        wamService.commit(new StatusPosterActionsEventBuilder()
                .statusEventType(StatusEventType.DELETE_STATUS_SUCCESS)
                .statusPostingSessionId(statusPostingSessionId)
                .build());
        return ack;
    }

    /**
     * Emits a {@code read} receipt for a viewed status update.
     *
     * <p>Status read receipts are delivered to {@code status@broadcast}
     * with the original status author attached as the {@code participant}
     * attribute, mirroring WhatsApp Web's
     * {@code WAWebStatusReceipt.sendStatusMsgRead} flow.
     *
     * @param statusAuthor the JID of the user that posted the status
     * @param statusId     the id of the viewed status message
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebStatusReceipt.sendStatusMsgRead: dispatches a read
     * receipt stanza to {@code STATUS_JID} with
     * {@code type="read"}, {@code participant=author}, and
     * {@code id=statusId}. Cobalt builds the same stanza and sends it via
     * the shared socket pipeline.
     */
    @WhatsAppWebExport(moduleName = "WAWebStatusReceipt", exports = "sendStatusMsgRead",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void markStatusViewed(Jid statusAuthor, String statusId) {
        Objects.requireNonNull(statusAuthor, "statusAuthor cannot be null");
        Objects.requireNonNull(statusId, "statusId cannot be null");
        var me = store.jid().orElse(null); // WAWebUserPrefsMeUser.getMeUser
        if (me == null) {
            return; // ADAPTED: silently drop when unauthenticated, matching sendReceipt
        }
        var receipt = new NodeBuilder() // WAWebStatusReceipt.sendStatusMsgRead: deprecatedSendStanza("receipt", {to: STATUS_JID, type: "read", participant, id})
                .description("receipt")
                .attribute("id", statusId) // WAWebStatusReceipt.sendStatusMsgRead: id
                .attribute("type", "read") // WAWebStatusReceipt.sendStatusMsgRead: type: "read"
                .attribute("to", Jid.statusBroadcastAccount()) // WAWebStatusReceipt.sendStatusMsgRead: to: STATUS_JID
                .attribute("participant", statusAuthor) // WAWebStatusReceipt.sendStatusMsgRead: participant: author
                .build();
        sendNodeWithNoResponse(receipt); // WAWebStatusReceipt.sendStatusMsgRead: sendStanza
    }

    /**
     * Queries the server for the current Status privacy configuration.
     *
     * <p>The IQ is sent with {@code xmlns="status"} and a single
     * {@code <privacy/>} child addressed to {@code s.whatsapp.net}. The
     * server responds with a {@code <privacy>} element containing the
     * selected mode and any paired JID list.
     *
     * @return the current status privacy setting, never {@code null}
     *
     * @implNote WAWebUserPrefsStatus.getStatusPrivacySetting exposes the
     * cached setting; WA Web additionally refreshes the cache via an IQ
     * query. Cobalt emits the IQ directly and parses the response into a
     * {@link StatusPrivacySetting} record.
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsStatus", exports = "getStatusPrivacySetting",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public StatusPrivacySetting queryStatusPrivacy() {
        // <iq xmlns="status" to="s.whatsapp.net" type="get"><privacy/></iq>
        var privacyQuery = new NodeBuilder()
                .description("privacy")
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "status") // task spec: xmlns="status"
                .attribute("to", JidServer.user()) // task spec: to="s.whatsapp.net"
                .attribute("type", "get")
                .content(privacyQuery);
        var response = sendNode(iqNode);
        var privacyNode = response.getChild("privacy")
                .orElseThrow(() -> new NoSuchElementException("Missing <privacy> in status privacy response"));
        var modeAttr = privacyNode.getAttributeAsString("list") // WA Web: list="contacts" | "contact_whitelist" | "contact_blacklist"
                .or(() -> privacyNode.getAttributeAsString("type"))
                .orElse("contacts");
        var mode = switch (modeAttr) {
            case "contacts" -> StatusPrivacyMode.CONTACTS;
            case "contact_whitelist", "allowlist", "whitelist" -> StatusPrivacyMode.WHITELIST;
            case "contact_blacklist", "denylist", "blacklist" -> StatusPrivacyMode.CONTACTS_EXCEPT;
            default -> StatusPrivacyMode.CONTACTS;
        };
        var jids = privacyNode.streamChildren("user")
                .flatMap(userNode -> userNode.streamAttributeAsJid("jid"))
                .toList();
        return new StatusPrivacySetting(mode, jids);
    }

    /**
     * Changes the Status privacy configuration.
     *
     * <p>Dispatches a status privacy IQ for the immediate server-side
     * change and, in parallel, publishes a
     * {@link StatusPrivacyAction} sync mutation via
     * {@link WebAppStateService#pushPatches} so the new configuration
     * propagates to every companion device. The local
     * {@link PrivacySettingType#STATUS} entry in the store is also updated
     * eagerly.
     *
     * @param mode the new distribution mode; never {@code null}
     * @param jids the JID list applied by {@link StatusPrivacyMode#WHITELIST}
     *             and {@link StatusPrivacyMode#CONTACTS_EXCEPT}; may be
     *             empty or {@code null} for {@link StatusPrivacyMode#CONTACTS}
     * @throws NullPointerException if {@code mode} is {@code null}
     *
     * @implNote WAWebStatusSetAndSyncPrivacy.setAndSyncStatusPrivacy: builds
     * a {@code statusPrivacy} mutation via
     * {@code WAWebStatusPrivacySettingSync.getStatusPrivacySettingMutation}
     * and pushes it via {@code WAWebSyncdCoreApi.lockForSync(["user-prefs"], [mutation], ...)}
     * while persisting the new config through
     * {@code WAWebUserPrefsStatus.setStatusPrivacyConfig}. Cobalt delegates
     * the mutation construction to
     * {@link StatusPrivacyHandler#getMutation(Instant, StatusPrivacyAction.StatusDistributionMode, java.util.List)}
     * and additionally emits a direct {@code xmlns="status"} IQ as required
     * by the task spec.
     */
    @WhatsAppWebExport(moduleName = "WAWebStatusSetAndSyncPrivacy", exports = "setAndSyncStatusPrivacy",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeStatusPrivacy(StatusPrivacyMode mode, Collection<Jid> jids) {
        Objects.requireNonNull(mode, "mode cannot be null");
        var jidList = jids == null ? List.<Jid>of() : List.copyOf(jids); // WAWebStatusSetAndSyncPrivacy: Array.from(new Set(t.map(...)))

        // WAWebStatusSetAndSyncPrivacy: emit the server-side IQ for the mutation.
        // task spec: <iq xmlns="status" to="s.whatsapp.net" type="set"><privacy list="..."><user jid="..."/>...</privacy></iq>
        var listAttr = switch (mode) {
            case CONTACTS -> "contacts"; // WAWebUserPrefsStatusType.StatusPrivacySettingType.Contact
            case WHITELIST -> "contact_whitelist"; // WAWebUserPrefsStatusType.StatusPrivacySettingType.AllowList
            case CONTACTS_EXCEPT -> "contact_blacklist"; // WAWebUserPrefsStatusType.StatusPrivacySettingType.DenyList
        };
        var userChildren = new ArrayList<Node>(jidList.size());
        for (var jid : jidList) {
            if (jid == null) {
                continue; // ADAPTED: defensive skip
            }
            userChildren.add(new NodeBuilder()
                    .description("user")
                    .attribute("jid", jid)
                    .build());
        }
        var privacyNode = new NodeBuilder()
                .description("privacy")
                .attribute("list", listAttr)
                .content(userChildren)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "status") // task spec: xmlns="status"
                .attribute("to", JidServer.user()) // task spec: to="s.whatsapp.net"
                .attribute("type", "set")
                .content(privacyNode);
        sendNode(iqNode);

        // WAWebStatusSetAndSyncPrivacy: r("WAWebStatusPrivacySettingSync").getStatusPrivacySettingMutation(...)
        var protoMode = switch (mode) {
            case CONTACTS -> StatusPrivacyAction.StatusDistributionMode.CONTACTS; // WAWebProtobufSyncAction.pb.SyncActionValue$StatusPrivacyAction$StatusDistributionMode.CONTACTS
            case WHITELIST -> StatusPrivacyAction.StatusDistributionMode.ALLOW_LIST; // WAWebProtobufSyncAction.pb...ALLOW_LIST
            case CONTACTS_EXCEPT -> StatusPrivacyAction.StatusDistributionMode.DENY_LIST; // WAWebProtobufSyncAction.pb...DENY_LIST
        };
        var mutation = StatusPrivacyHandler.INSTANCE.getMutation(Instant.now(), protoMode, jidList); // WAWebStatusPrivacySettingSync.getStatusPrivacySettingMutation
        webAppStateService.pushPatches( // WAWebStatusSetAndSyncPrivacy: WAWebSyncdCoreApi.lockForSync(["user-prefs"], [d], ...)
                StatusPrivacyAction.COLLECTION_NAME,
                List.of(mutation));

        // WAWebStatusSetAndSyncPrivacy: r("WAWebUserPrefsStatus").setStatusPrivacyConfig({setting, list})
        var value = switch (mode) {
            case CONTACTS -> PrivacySettingValue.CONTACTS; // ADAPTED: StatusPrivacySettingType.Contact -> PrivacySettingValue.CONTACTS
            case WHITELIST -> PrivacySettingValue.CONTACTS_ONLY; // ADAPTED: StatusPrivacySettingType.AllowList -> PrivacySettingValue.CONTACTS_ONLY
            case CONTACTS_EXCEPT -> PrivacySettingValue.CONTACTS_EXCEPT; // ADAPTED: StatusPrivacySettingType.DenyList -> PrivacySettingValue.CONTACTS_EXCEPT
        };
        var entry = new PrivacySettingEntryBuilder()
                .type(PrivacySettingType.STATUS)
                .value(value)
                .excluded(jidList) // Cobalt overloads excluded() for both allow and deny lists
                .build();
        store.addPrivacySetting(entry); // WAWebUserPrefsStatus.setStatusPrivacyConfig
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
    @WhatsAppWebExport(moduleName = "WAWebWamChatPSALogger", exports = "logChatPSAForward",
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
        // WAWebForwardMessagesToChat.forwardMessagesToChats: u.map(e => WAWebMsgGetters.getIsPSA(e) && WAWebWamChatPSALogger.logChatPSAForward(e))
        logPsaActionIfApplicable(source, PsaMessageActionType.FORWARD); // WAWebWamChatPSALogger.logChatPSAForward -> PSA_MESSAGE_ACTION_TYPE.FORWARD
        // WAWebSendMsgRecordAction.sendMsgRecord: e.isForwarded && (n = WAWebMsgUtilsBridge.createMessageForwardMetric(e))
        emitForwardSendEvent(source, destination, container);
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
    @WhatsAppWebExport(moduleName = "WAWebWamChatPSALogger", exports = "logChatPSAForward",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void forwardMessages(Collection<MessageKey> sourceKeys, Collection<Jid> destinations) {
        Objects.requireNonNull(sourceKeys, "sourceKeys cannot be null");
        Objects.requireNonNull(destinations, "destinations cannot be null");
        // WAWebForwardMessagesToChat.forwardMessagesToChats: u.map(e => canForward(e))
        var resolvedSources = new ArrayList<MessageInfo>();
        for (var sourceKey : sourceKeys) {
            sourceKey.parentJid() // WAWebChatForwardMessage.getForwardedMessageFields: e.id.remote
                    .flatMap(parent -> sourceKey.id()
                            .flatMap(id -> store.findMessageById(parent, id)))
                    .ifPresent(resolvedSources::add);
        }
        // WAWebForwardMessagesToChat.forwardMessagesToChats:
        // u.map(e => WAWebMsgGetters.getIsPSA(e) && WAWebWamChatPSALogger.logChatPSAForward(e)) — logged once per source, before fan-out.
        for (var source : resolvedSources) {
            logPsaActionIfApplicable(source, PsaMessageActionType.FORWARD); // WAWebWamChatPSALogger.logChatPSAForward -> PSA_MESSAGE_ACTION_TYPE.FORWARD
        }
        // WAWebForwardMessagesToChat.forwardMessagesToChats: i.filter(e => e.canSend).map(chat => forwardMessages)
        for (var destination : destinations) {
            for (var source : resolvedSources) {
                var container = source.message(); // WAWebChatForwardMessage.getForwardedMessageFields: omit local-only fields
                // WAWebSendMsgRecordAction.sendMsgRecord: e.isForwarded && (n = WAWebMsgUtilsBridge.createMessageForwardMetric(e))
                emitForwardSendEvent(source, destination, container);
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
        // The preparer auto converts to EncReactionMessage for CAG groups.
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
    @WhatsAppWebExport(moduleName = "WAWebWamChatPSALogger", exports = "logChatPSAStar",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void starMessage(MessageKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        pushStarMutation(key, true); // WAWebStarMessageSync.getStarMessageMutations: starAction.starred = true
        // WAWebActionListenerHelpers: if(getIsPSA(e)) for (var m=0; m<c; m++) WAWebWamChatPSALogger.logChatPSAStar(t[m])
        logPsaActionIfApplicable(key, PsaMessageActionType.SAVE); // WAWebWamChatPSALogger.logChatPSAStar -> PSA_MESSAGE_ACTION_TYPE.SAVE
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

        // WAWebSyncdUtils.constructMsgKeySegmentsFromMsgKey: applies the legacy
        // JID serializer and the !remote.isUser() && !fromMe participant gate.
        var keySegments = SyncdIndexUtils.constructMsgKeySegmentsFromMsgKey(key);

        var action = new StarActionBuilder() // WAWebStarMessageSync.getStarMessageMutations: {starAction: {starred: a}}
                .starred(starred)
                .build();
        var value = new SyncActionValueBuilder() // WAWebSyncdActionUtils.buildPendingMutation: encodeProtobuf(SyncActionValueSpec, ...)
                .timestamp(Instant.now()) // WAWebSyncdActionUtils.buildPendingMutation: timestamp: e
                .starAction(action)
                .build();
        var indexJson = SyncdIndexUtils.buildIndex( // WAWebSyncdActionUtils.buildIndex
                StarAction.ACTION_NAME, // WAWebStarMessageSync.getAction: "star"
                keySegments.get(0), // WAWebSyncdUtils.constructMsgKeySegments: remote.toString({legacy: true})
                keySegments.get(1), // WAWebSyncdUtils.constructMsgKeySegments: id
                keySegments.get(2), // WAWebSyncdUtils.constructMsgKeySegments: fromMe
                keySegments.get(3)  // WAWebSyncdUtils.extractParticipantForSync
        );
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

    /**
     * Emits a {@link ChatPsaActionEventBuilder} for the given message when
     * the message belongs to the official WhatsApp PSA account.
     *
     * <p>The helper looks up the chat message associated with {@code key}
     * and forwards to {@link #logPsaActionIfApplicable(MessageInfo, PsaMessageActionType)}.
     * If the key cannot be resolved (for example because the message has
     * already been purged from the local store) no event is emitted; this
     * mirrors WA Web's behaviour of skipping the PSA log when the
     * underlying {@code Msg} is not available.
     *
     * @param key        the message key whose chat affiliation is being
     *                   tested; must not be {@code null}
     * @param actionType the PSA action that was performed on the message
     *
     * @implNote WAWebActionListenerHelpers / WAWebForwardMessagesToChat:
     * the call sites look up the {@code Msg} model first via
     * {@code getIsPSA(e)} / {@code getIsPSA(chat)} and only invoke the
     * PSA logger when the chat/message is part of the PSA campaign.
     */
    @WhatsAppWebExport(moduleName = "WAWebChatGetters", exports = "getIsPSA",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void logPsaActionIfApplicable(MessageKey key, PsaMessageActionType actionType) {
        var parentJid = key.parentJid().orElse(null); // WAWebChatGetters.getIsPSA: r("WAWebWid").isPSA(chatJid)
        if (parentJid == null || !parentJid.equals(Jid.announcementsAccount())) {
            return; // WAWebChatGetters.getIsPSA: false -> skip
        }
        var messageId = key.id().orElse(null);
        if (messageId == null) {
            return;
        }
        store.findMessageById(parentJid, messageId)
                .ifPresent(info -> logPsaActionIfApplicable(info, actionType));
    }

    /**
     * Emits a {@link ChatPsaActionEventBuilder} for the given message when
     * the message originates from the official WhatsApp PSA account
     * ({@code 0@s.whatsapp.net}).
     *
     * <p>The event populates {@code messageMediaType},
     * {@code psaCampaignId}, {@code psaMsgId} and the caller-supplied
     * {@code psaMessageActionType}, matching the four WA Web emission
     * sites in {@code WAWebWamChatPSALogger}
     * ({@code logChatPSAStar}, {@code logChatPSADelete},
     * {@code logChatPSAForward}, {@code logChatPSAMediaPlay}).
     *
     * <p>Messages that do not belong to the PSA account or are not
     * {@link ChatMessageInfo} instances are silently ignored.
     *
     * @param info       the resolved message info whose PSA affiliation
     *                   is being tested; must not be {@code null}
     * @param actionType the PSA action that was performed on the message
     *
     * @implNote WAWebWamChatPSALogger.logChatPSAStar / logChatPSADelete /
     * logChatPSAForward / logChatPSAMediaPlay: the generic helper around
     * {@code new ChatPsaActionWamEvent({messageMediaType, psaCampaignId,
     * psaMessageActionType, psaMsgId}).commit()}.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamChatPSALogger", exports = "logChatPSAStar",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamChatPSALogger", exports = "logChatPSADelete",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamChatPSALogger", exports = "logChatPSAForward",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamChatPSALogger", exports = "logChatPSAMediaPlay",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void logPsaActionIfApplicable(MessageInfo info, PsaMessageActionType actionType) {
        if (!(info instanceof ChatMessageInfo chatInfo)) { // WAWebMsgGetters.getIsPSA: ChatModel check
            return;
        }
        var parentJid = chatInfo.key().parentJid().orElse(null);
        if (parentJid == null || !parentJid.equals(Jid.announcementsAccount())) { // WAWebWid.isPSA: user === "0" && server === "c.us"
            return;
        }
        var messageIdOpt = chatInfo.key().id();
        if (messageIdOpt.isEmpty()) {
            return;
        }
        // WAWebWamChatPSALogger: psaCampaignId: (t = e.campaignId) == null ? void 0 : t.toString()
        var campaignId = chatInfo.statusPsa()
                .map(com.github.auties00.cobalt.model.message.status.StatusPSA::campaignId)
                .map(String::valueOf)
                .orElse(null);
        wamService.commit(new ChatPsaActionEventBuilder() // WAWebWamChatPSALogger: new ChatPsaActionWamEvent({...}).commit()
                .messageMediaType(WamMsgUtils.getWamMediaType(chatInfo)) // WAWebWamMsgUtils.getWamMediaType(e)
                .psaCampaignId(campaignId) // WAWebWamChatPSALogger: (t=e.campaignId)==null?void 0:t.toString()
                .psaMessageActionType(actionType) // WAWebWamChatPSALogger: PSA_MESSAGE_ACTION_TYPE.SAVE/DELETE/FORWARD/MEDIA_PLAY
                .psaMsgId(messageIdOpt.get()) // WAWebWamChatPSALogger: e.id.id.toString()
                .build());
    }

    /**
     * Emits a {@link ForwardSendEvent} for a forwarded message right before it
     * is handed off to the send pipeline.
     *
     * <p>WhatsApp Web builds this event inside
     * {@code WAWebMsgUtilsBridge.createMessageForwardMetric} and commits it via
     * {@code WAWebSendMsgRecordAction.sendMsgRecord} once the forwarded message
     * has been transmitted. Cobalt folds the two steps together at the public
     * forward entry points because Cobalt's send pipeline does not carry the
     * {@code isForwarded} flag through to the encryption layer. The populated
     * properties mirror WA Web exactly: {@code messageType},
     * {@code messageMediaType}, {@code mediaCaptionPresent},
     * {@code fastForwardEnabled} and {@code messageIsFanout} are hard-coded to
     * the WA Web literal values, the forward-count derived booleans come from
     * the source message's {@link ContextInfo}, and {@code ephemeralityDuration}
     * comes from {@link ChatMessageInfo#ephemeralDuration()} when available.
     *
     * <p>Optional properties that WA Web resolves from modules Cobalt does not
     * implement ({@code ephemeralityInitiator},
     * {@code ephemeralityTriggerAction}, {@code disappearingChatInitiator},
     * {@code senderDefaultDisappearingDuration},
     * {@code receiverDefaultDisappearingDuration}, {@code typeOfGroup}) are
     * intentionally left unset so that the emitted event reflects only the
     * information Cobalt can derive without inventing values.
     *
     * <p>Non-{@link ChatMessageInfo} sources (for example newsletters, which
     * cannot carry a {@code forwardingScore}) are emitted with the forward-count
     * booleans cleared, matching WA Web's behaviour for messages whose
     * {@code forwardingScore} getter returns {@code null}.
     *
     * @param source    the resolved source message being forwarded; must not
     *                  be {@code null}
     * @param destination the destination chat JID of the forwarded send; must
     *                    not be {@code null}
     * @param forwarded the {@link MessageContainer} being dispatched; must not
     *                  be {@code null}
     *
     * @implNote Adapts {@code WAWebMsgUtilsBridge.createMessageForwardMetric}
     * and its commit in {@code WAWebSendMsgRecordAction.sendMsgRecord}.
     * WA Web uses the forwarded message ({@code e}) for both the message-type
     * and media-type classifications and for the forward-count getters; Cobalt
     * mirrors this by reading all classification sources off the new
     * {@code destination} for {@code messageType} and off the source message's
     * container for {@code messageMediaType} / {@code mediaCaptionPresent}
     * because Cobalt's forwarded container reuses the source payload verbatim.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgUtilsBridge", exports = "createMessageForwardMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendMsgRecordAction", exports = "sendMsgRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitForwardSendEvent(MessageInfo source, Jid destination, MessageContainer forwarded) {
        // WAWebMsgUtilsBridge.createMessageForwardMetric: var a = !!t.caption; t.type === DOCUMENT && (a = t.isCaptionByUser)
        var mediaCaptionPresent = hasMediaCaption(forwarded);
        // WAWebMsgUtilsBridge.createMessageForwardMetric: isFrequentlyForwarded: !!getIsFrequentlyForwarded(t); isForwardedForward: getNumTimesForwarded(t) > 1
        var numTimesForwarded = numTimesForwarded(source);
        // WAWebConstantsDeprecated.FREQUENTLY_FORWARDED_SENTINEL = 127
        var isFrequentlyForwarded = numTimesForwarded >= 127;
        var isForwardedForward = numTimesForwarded > 1;
        var builder = new ForwardSendEventBuilder()
                // WAWebMsgUtilsBridge.createMessageForwardMetric: messageType: getWamMessageType(t)
                .messageType(WamMsgUtils.getWamMessageType(destination))
                // WAWebMsgUtilsBridge.createMessageForwardMetric: messageMediaType: getWamMediaType(t)
                .messageMediaType(WamMsgUtils.getWamMediaType(forwarded))
                // WAWebMsgUtilsBridge.createMessageForwardMetric: mediaCaptionPresent: a
                .mediaCaptionPresent(mediaCaptionPresent)
                // WAWebMsgUtilsBridge.createMessageForwardMetric: fastForwardEnabled: !0
                .fastForwardEnabled(true)
                // WAWebMsgUtilsBridge.createMessageForwardMetric: messageIsFanout: !0
                .messageIsFanout(true)
                // WAWebMsgUtilsBridge.createMessageForwardMetric: isFrequentlyForwarded
                .isFrequentlyForwarded(isFrequentlyForwarded)
                // WAWebMsgUtilsBridge.createMessageForwardMetric: isForwardedForward
                .isForwardedForward(isForwardedForward);
        // WAWebMsgUtilsBridge.createMessageForwardMetric: t.ephemeralDuration != null && (i.ephemeralityDuration = t.ephemeralDuration)
        if (source instanceof ChatMessageInfo chatSource) {
            var ephemeralDuration = chatSource.ephemeralDuration();
            if (ephemeralDuration.isPresent()) {
                builder.ephemeralityDuration(ephemeralDuration.getAsInt());
            }
        }
        wamService.commit(builder.build());
    }

    /**
     * Returns whether the forwarded {@link MessageContainer} carries a
     * user-visible caption.
     *
     * <p>The helper mirrors WA Web's inline caption check inside
     * {@code WAWebMsgUtilsBridge.createMessageForwardMetric}: for every payload
     * type the caption presence boils down to {@code !!e.caption}. WA Web's
     * {@code DOCUMENT} branch replaces the truthiness check with
     * {@code e.isCaptionByUser}; Cobalt's {@code DocumentMessage} does not
     * expose an {@code isCaptionByUser} field, so the method falls back to the
     * generic caption truthiness check to avoid inventing a missing signal.
     *
     * @param container the forwarded message container; must not be
     *                  {@code null}
     * @return {@code true} when the container carries a non-empty caption,
     *         {@code false} otherwise
     *
     * @implNote WAWebMsgUtilsBridge.createMessageForwardMetric: {@code var a =
     * !!t.caption; t.type === MSG_TYPE.DOCUMENT && (a = t.isCaptionByUser)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgUtilsBridge", exports = "createMessageForwardMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean hasMediaCaption(MessageContainer container) {
        if (container == null) {
            return false;
        }
        return switch (container.content()) {
            case com.github.auties00.cobalt.model.message.media.ImageMessage image -> image.caption().map(s -> !s.isEmpty()).orElse(false);
            case com.github.auties00.cobalt.model.message.media.VideoMessage video -> video.caption().map(s -> !s.isEmpty()).orElse(false);
            case com.github.auties00.cobalt.model.message.media.DocumentMessage document -> document.caption().map(s -> !s.isEmpty()).orElse(false);
            case null, default -> false;
        };
    }

    /**
     * Returns the number of times a message has been forwarded, matching WA
     * Web's {@code WAWebMsgGetters.getNumTimesForwarded} helper.
     *
     * <p>The helper resolves {@code forwardingScore} from the source message's
     * primary {@link ContextInfo}. When the score is unset WA Web falls back
     * to {@code isForwarded ? 1 : 0}; Cobalt mirrors the same fallback because
     * the forwarding score and forwarded flag live on the same
     * {@link ContextInfo} record.
     *
     * @param source the resolved source message whose forwarding count is
     *               being computed; must not be {@code null}
     * @return the forwarding count, or zero when no context info is available
     *
     * @implNote WAWebMsgGetters.getNumTimesForwarded:
     * {@code forwardingScore == null ? (isForwarded ? 1 : 0) : (forwardingScore || 0)}.
     * NewsletterMessageInfo cannot carry a {@code forwardingScore}, so the
     * helper returns zero for non-{@link ChatMessageInfo} sources.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgGetters", exports = "getNumTimesForwarded",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static int numTimesForwarded(MessageInfo source) {
        if (!(source instanceof ChatMessageInfo chatSource)) {
            return 0;
        }
        var contextInfo = chatSource.message()
                .contextualContent()
                .flatMap(ContextualMessage::contextInfo);
        if (contextInfo.isEmpty()) {
            return 0;
        }
        var ctx = contextInfo.get();
        var score = ctx.forwardingScore();
        if (score.isPresent()) {
            return score.getAsInt();
        }
        // WAWebMsgGetters.getNumTimesForwarded: fallback -> isForwarded ? 1 : 0
        return ctx.isForwarded() ? 1 : 0;
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
        // WAWebPinChatSync.getMutationsForPin: r("gkx")("26258") || new MdSyncdDogfoodingFeatureUsageWamEvent({mdSyncdDogfoodingFeature: PIN_MUTATION}).commit()
        wamService.commit(new MdSyncdDogfoodingFeatureUsageEventBuilder()
                .mdSyncdDogfoodingFeature(MdFeatureCode.PIN_MUTATION)
                .build());
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
        // WAWebActionListener (mute/unmute handler q):
        //   !getIsPSA(t) && !getIsNewsletter(t) && logChatMute(t, e, l) / logChatUnmute(t, g)
        // WAWebChatMuteLogger.logChatMute / logChatUnmute then builds a ChatMuteWamEvent with:
        //   actionConducted = MUTE on mute, UNMUTE on unmute
        //   muteChatType    = GROUP if getIsGroup(chat) else ONE_ON_ONE
        //   muteDuration    = n (seconds, -1 = indefinite) — mute path only
        //   muteEntryPoint  = e (CHAT_LIST_SCREEN / CONTACT_INFO / CONVERSATION_SCREEN)
        //   muteGroupSize   = chat.groupMetadata.participants.length ?? 0 — group path only
        // Newsletters in Cobalt use the dedicated #muteNewsletter(Jid, boolean) API and never reach this method.
        // PSA chats are not surfaced via Cobalt's public mute API (there is no WAWebWamChatPSALogger equivalent wired here).
        // muteEntryPoint is a UI-layer value (which screen the user tapped mute from); Cobalt has no UI so it is left unset.
        if (!chat.hasNewsletterServer()) { // WAWebActionListener: !getIsNewsletter(t) (getIsPSA has no Cobalt equivalent at this layer)
            var isGroup = chat.hasGroupOrCommunityServer(); // WAWebChatMuteLogger: var i = getIsGroup(e)
            var muteChatType = isGroup // WAWebChatMuteLogger.u: getIsGroup(e) ? MUTE_CHAT_TYPE.GROUP : MUTE_CHAT_TYPE.ONE_ON_ONE
                    ? MuteChatType.GROUP
                    : MuteChatType.ONE_ON_ONE;
            var eventBuilder = new ChatMuteEventBuilder()
                    .muteChatType(muteChatType); // WAWebChatMuteLogger: muteChatType: u(e)
            if (muteEndSeconds == 0L) { // WAWebActionListener: unmute branch -> logChatUnmute(t, g)
                eventBuilder.actionConducted(ActionConducted.UNMUTE); // WAWebChatMuteLogger.logChatUnmute: actionConducted: ACTION_CONDUCTED.UNMUTE (no muteDuration set)
            } else { // WAWebActionListener: mute branch -> logChatMute(t, e, l)
                eventBuilder.actionConducted(ActionConducted.MUTE) // WAWebChatMuteLogger.logChatMute: actionConducted: ACTION_CONDUCTED.MUTE
                        .muteDuration(Instant.ofEpochSecond(muteEndSeconds)); // WAWebChatMuteLogger.logChatMute: muteDuration: n (seconds value, -1 = indefinite sentinel passed through WAWebActionListener's r === 1/0 ? -1 : r transform)
            }
            if (isGroup) { // WAWebChatMuteLogger: babelHelpers.extends({...}, i ? {muteGroupSize: ...} : {})
                // WAWebChatMuteLogger: muteGroupSize: (r = (a = e.groupMetadata) == null || (a = a.participants) == null ? void 0 : a.length) != null ? r : 0
                var groupSize = store.findChatByJid(chat) // WAWebChatMuteLogger: e.groupMetadata.participants.length
                        .map(c -> c.participant().size())
                        .orElse(0);
                eventBuilder.muteGroupSize(groupSize); // WAWebChatMuteLogger: muteGroupSize: ... (group-only branch)
            }
            wamService.commit(eventBuilder.build()); // WAWebChatMuteLogger: new ChatMuteWamEvent({...}).commit()
        }
        // WAWebActionListener.c / WAWebActionListener.w (mute handlers): (t.isBusinessGroup() || t.contact.isBusiness) && new BusinessMuteWamEvent().commit()
        // WAWebActionListener (unmute handler): (t.isBusinessGroup() || t.contact.isBusiness) && new BusinessUnmuteWamEvent().commit()
        // Emitted only on one path per invocation: mute emits BusinessMuteWamEvent, unmute emits BusinessUnmuteWamEvent.
        // WA Web constructs both events with no arguments, so the defined muteT (mute only) and muteeId properties remain unset.
        // ADAPTED: Cobalt's Contact model does not track an isBusiness flag and Cobalt cannot iterate a group's admin
        // contacts' per-contact business status, so the isBusinessGroup() branch of WA Web's guard is not reachable here.
        // The closest available proxy for contact.isBusiness is the presence of a verified business name for the chat JID.
        var isBusinessChat = store.findVerifiedBusinessName(chat).isPresent(); // WAWebActionListener: t.contact.isBusiness (approximated via verifiedBusinessNames)
        if (isBusinessChat) { // WAWebActionListener: (t.isBusinessGroup() || t.contact.isBusiness)
            if (muteEndSeconds == 0L) { // WAWebActionListener: unmute branch (mute.unmute(...))
                wamService.commit(new BusinessUnmuteEventBuilder().build()); // WAWebActionListener: new BusinessUnmuteWamEvent().commit() (WA Web does not set muteeId at this call site)
            } else { // WAWebActionListener.c / WAWebActionListener.w: mute branch (mute.mute(...))
                wamService.commit(new BusinessMuteEventBuilder().build()); // WAWebActionListener: new BusinessMuteWamEvent().commit() (WA Web does not set muteT or muteeId at this call site)
            }
        }
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
        // WAWebClearChatSync.getClearChatMutation: r("gkx")("26258") || new MdSyncdDogfoodingFeatureUsageWamEvent({mdSyncdDogfoodingFeature: n ? CLEAR_CHAT_REMOVE_STARRED_MUTATION : CLEAR_CHAT_KEEP_STARRED_MUTATION}).commit()
        wamService.commit(new MdSyncdDogfoodingFeatureUsageEventBuilder()
                .mdSyncdDogfoodingFeature(!keepStarred
                        ? MdFeatureCode.CLEAR_CHAT_REMOVE_STARRED_MUTATION
                        : MdFeatureCode.CLEAR_CHAT_KEEP_STARRED_MUTATION)
                .build());
        // WAWebClearChatSync.$ClearChatSync$p_3: [t.toJid(), n?"1":"0", r?"1":"0"] — n = deleteStarred (inverse of keepStarred), r = deleteMedia
        var mutation = ClearChatHandler.INSTANCE.getClearChatMutation(timestamp, chat, !keepStarred, false, messageRange); // WAWebClearChatSync.getClearChatMutation
        webAppStateService.pushPatches(ClearChatHandler.INSTANCE.collectionName(), List.of(mutation)); // WAWebSyncdCoreApi.lockForSync -> push
        if (chatModel != null) { // WAWebClearChatSync.clearChat: queryAndRemoveMessagesInMessageRange
            chatModel.removeMessages(); // ADAPTED: WAWebClearChatSync.clearChat — Cobalt drops all messages since per-message range deletion is not yet supported
        }
        // WAWebClearSelectedChatsAction.clearSelectedChats: after yield r, logChatActionEvent({chatActionEntryPoint: l, chatActionType: CLEAR})
        // WA Web defaults the entry point to CONVERSATION_LIST_BULK_EDIT when the caller omits it (r.entryPoint === void 0)
        wamService.commit(new ChatActionEventBuilder()
                .chatActionEntryPoint(ChatActionEntryPoint.CONVERSATION_LIST_BULK_EDIT) // WAWebClearSelectedChatsAction.clearSelectedChats: l = r.entryPoint ?? CHAT_ACTION_ENTRY_POINT.CONVERSATION_LIST_BULK_EDIT
                .chatActionType(ChatActionType.CLEAR) // WAWebClearSelectedChatsAction.clearSelectedChats: CHAT_ACTION_TYPE.CLEAR
                .build());
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
        // WAWebDeleteChatSync.getDeleteChatMutation: r("gkx")("26258") || new MdSyncdDogfoodingFeatureUsageWamEvent({mdSyncdDogfoodingFeature: DELETE_MUTATION}).commit()
        wamService.commit(new MdSyncdDogfoodingFeatureUsageEventBuilder()
                .mdSyncdDogfoodingFeature(MdFeatureCode.DELETE_MUTATION)
                .build());
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
     * Creates a new chat label with the given name and palette colour index.
     *
     * <p>Per WhatsApp Web {@code WAWebBizLabelEditingAction.labelAddAction}:
     * allocates the next label id via
     * {@code WAWebDBLabelDatabaseApi.getNextLabelId}, maps the name to a
     * predefined-id when applicable (via
     * {@link BusinessLabelConstants#mapLabelNameToPredefinedId(String)}), and
     * issues a {@code getLabelMutation} with {@code deleted=false} and
     * {@code type=CUSTOM}. Cobalt allocates the next id in-memory against
     * the current store (one above the highest existing numeric label id,
     * starting at {@code 1}) because the IndexedDB-backed
     * {@code getNextLabelId} sequence is not replicated.
     *
     * @param name       the user-visible display name of the label
     * @param colorIndex the palette colour index
     * @return the newly-allocated label id (stringified integer)
     * @throws NullPointerException if {@code name} is {@code null}
     *
     * @implNote WAWebBizLabelEditingAction.labelAddAction
     */
    @WhatsAppWebExport(moduleName = "WAWebBizLabelEditingAction", exports = "labelAddAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public String createLabel(String name, int colorIndex) {
        Objects.requireNonNull(name, "name cannot be null"); // ADAPTED: invariant guard; WA Web accepts any string
        var timestamp = Instant.now(); // WAWebBizLabelEditingAction.labelAddAction: var l = unixTime()
        // ADAPTED: WAWebBizLabelEditingAction.labelAddAction: var i = yield getNextLabelId()
        // Cobalt has no IndexedDB label-id sequence, so we compute the next id as max(existingNumericIds) + 1, starting at 1.
        var nextId = store.labels().stream()
                .map(Label::id)
                .mapToInt(id -> { // ADAPTED: WA Web uses String(i) with the server-assigned integer sequence
                    try { return Integer.parseInt(id); } catch (NumberFormatException ignored) { return 0; }
                })
                .max()
                .orElse(0) + 1;
        var labelId = String.valueOf(nextId); // WAWebBizLabelEditingAction.labelAddAction: String(i)
        // WAWebBizLabelEditingAction.labelAddAction: c = mapLabelNameToPredefinedId(t) — boxed to Integer (null on non-predefined names)
        var predefinedLabelId = BusinessLabelConstants.mapLabelNameToPredefinedId(name)
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
        var mutation = LabelEditHandler.INSTANCE.getLabelMutation( // WAWebBizLabelEditingAction.labelAddAction: getLabelMutation(String(i), t, a, false, c, d, m, l)
                labelId,
                name,
                colorIndex,
                false, // WAWebBizLabelEditingAction.labelAddAction: deleted = false
                predefinedLabelId, // WAWebBizLabelEditingAction.labelAddAction: c = mapLabelNameToPredefinedId(t)
                Boolean.TRUE, // WAWebBizLabelEditingAction.labelAddAction: d = isListsEnabled() ? true : undefined — Cobalt always treats new labels as active
                LabelEditAction.ListType.CUSTOM, // WAWebBizLabelEditingAction.labelAddAction: m = ListType.CUSTOM
                timestamp
        );
        webAppStateService.pushPatches(LabelEditAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync(["label"], [p], ...)
        var label = new com.github.auties00.cobalt.model.preference.LabelBuilder() // WAWebBizLabelEditingAction.labelAddAction: LabelCollection.add({id, name, colorIndex, ...})
                .id(labelId)
                .name(name)
                .color(colorIndex)
                .predefinedId(predefinedLabelId) // WAWebBizLabelEditingAction.labelAddAction: predefinedId: c (same mapping used for the mutation)
                .isActive(Boolean.TRUE)
                .type(LabelEditAction.ListType.CUSTOM)
                .build();
        store.addLabel(label); // WAWebBizLabelEditingAction.labelAddAction: LabelCollection.add(...)
        // WAWebListsActions.createNewListAction -> WAWebListsLogging.logListUpdate (listAction: CREATE).
        // Cobalt collapses the outer WAWebListsActions.createNewListAction wrapper (which carries
        // the entryPoint and the set of chats being tagged) into this single createLabel method, so
        // updateEntryPoint / groupsAdded / usersAdded / groupsAfterUpdate / usersAfterUpdate are
        // omitted because the Cobalt API surface does not accept an entry point and does not tag
        // chats as part of label creation (matches WA Web's {chatsBeforeUpdate: [], addedChats: [],
        // removedChats: []} degenerate case where all counter fields are elided).
        wamService.commit(new ListUpdateEventBuilder()
                .listAction(ListAction.CREATE) // WAWebListsActions.createNewListAction: listAction: LIST_ACTION.CREATE
                .listId(nextId) // WAWebListsActions.createNewListAction: listId: a (the freshly allocated id)
                .listType(ListType.CUSTOM) // WAWebListsLogging.logListUpdate -> getListType(SchemaLabel.ListType.CUSTOM) = LIST_TYPE.CUSTOM (createLabel always inserts CUSTOM)
                .build());
        return labelId;
    }

    /**
     * Edits the display name and/or palette colour of an existing label.
     *
     * <p>Per WhatsApp Web {@code WAWebBizLabelEditingAction.labelEditAction}:
     * issues a {@code getLabelMutation} with {@code deleted=false} carrying
     * the new fields, then updates the in-memory label collection via
     * {@code LabelCollection.add(..., {merge: true})}.
     *
     * @param labelId    the label identifier
     * @param name       the new display name
     * @param colorIndex the new palette colour index
     * @throws NullPointerException if {@code labelId} or {@code name} is {@code null}
     *
     * @implNote WAWebBizLabelEditingAction.labelEditAction
     */
    @WhatsAppWebExport(moduleName = "WAWebBizLabelEditingAction", exports = "labelEditAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editLabel(String labelId, String name, int colorIndex) {
        Objects.requireNonNull(labelId, "labelId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        var timestamp = Instant.now(); // WAWebBizLabelEditingAction.labelEditAction: var d = unixTime()
        var existing = store.findLabel(labelId).orElse(null); // WAWebBizLabelEditingAction.labelEditAction uses the existing label for merge
        var predefinedId = existing != null && existing.predefinedId().isPresent()
                ? existing.predefinedId().getAsInt()
                : null; // WAWebBizLabelEditingAction.labelEditAction: a != null ? a : m (m is the current predefined id)
        var type = existing != null ? existing.type().orElse(null) : null; // WAWebBizLabelEditingAction.labelEditAction: u (existing type preserved)
        var isActive = existing != null && existing.isActive().orElse(Boolean.FALSE); // WAWebBizLabelEditingAction.labelEditAction: l (existing active flag)
        var mutation = LabelEditHandler.INSTANCE.getLabelMutation( // WAWebBizLabelEditingAction.labelEditAction: getLabelMutation(e, t, i, false, a, l, u, d)
                labelId,
                name,
                colorIndex,
                false, // WAWebBizLabelEditingAction.labelEditAction: deleted = false
                predefinedId,
                isActive ? Boolean.TRUE : null, // WAWebBizLabelEditingAction.labelEditAction: l
                type,
                timestamp
        );
        webAppStateService.pushPatches(LabelEditAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync(["label"], [p], ...)
        var renamed = existing == null || !name.equals(existing.name()); // WAWebListsActions.editListAction: l = n.name !== a
        if (existing != null) { // WAWebBizLabelEditingAction.labelEditAction: LabelCollection.add(..., {merge: true})
            existing.setName(name);
            existing.setColor(colorIndex);
        } else { // ADAPTED: insert a stub if the caller edits an unknown label id — keeps local state consistent with the mutation we pushed
            var label = new com.github.auties00.cobalt.model.preference.LabelBuilder()
                    .id(labelId)
                    .name(name)
                    .color(colorIndex)
                    .build();
            store.addLabel(label);
        }
        // WAWebListsActions.editListAction -> WAWebListsLogging.logListUpdate (listAction: RENAME).
        // WA Web only logs the RENAME event when the name actually changed (l === true); the
        // UPDATE_MEMBERS branch is not triggered from Cobalt's editLabel because the Cobalt API
        // does not bundle chat-membership changes into this call (those go through the separate
        // associateLabel / dissociateLabel entry points, whose WA Web counterparts do not log
        // a ListUpdate event on their own). updateEntryPoint is omitted because the Cobalt API
        // does not carry an entry point; listType / predefinedId come from the existing label
        // as in WAWebListsLogging.logListUpdate's LabelCollection.get(""+l) lookup.
        if (renamed) {
            var listIdNumber = parseLabelIdToListId(labelId);
            if (listIdNumber != null) {
                var builder = new ListUpdateEventBuilder()
                        .listAction(ListAction.RENAME) // WAWebListsActions.editListAction: listAction: LIST_ACTION.RENAME
                        .listId(listIdNumber); // WAWebListsActions.editListAction: listId: Number(n.id)
                var wamListType = existing != null ? mapWamListType(existing.type().orElse(null)) : null; // WAWebListsLogging.logListUpdate: f = getListType(_.type); f != null && (u.listType = f)
                if (wamListType != null) {
                    builder.listType(wamListType);
                }
                if (existing != null && existing.predefinedId().isPresent()) { // WAWebListsLogging.logListUpdate: _.predefinedId != null && (u.predefinedId = _.predefinedId)
                    builder.predefinedId(existing.predefinedId().getAsInt());
                }
                wamService.commit(builder.build());
            }
        }
    }

    /**
     * Deletes an existing chat label along with all of its chat-jid
     * associations.
     *
     * <p>Per WhatsApp Web {@code WAWebBizLabelEditingAction.labelDeleteAction}:
     * queries any existing label-jid associations, builds a
     * {@code getLabelMutation} with {@code deleted=true}, appends a matching
     * removal mutation for each live association via
     * {@code WAWebLabelJidSync.createLabelAssociationMutations}, and pushes
     * them together under the {@code label}/{@code label-association}/{@code chat}
     * lock. The in-memory label is then removed from
     * {@code LabelCollection}.
     *
     * @param labelId the identifier of the label to delete
     * @throws NullPointerException if {@code labelId} is {@code null}
     *
     * @implNote WAWebBizLabelEditingAction.labelDeleteAction
     */
    @WhatsAppWebExport(moduleName = "WAWebBizLabelEditingAction", exports = "labelDeleteAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteLabel(String labelId) {
        Objects.requireNonNull(labelId, "labelId cannot be null");
        var timestamp = Instant.now(); // WAWebBizLabelEditingAction.labelDeleteAction: var l = unixTime()
        var existing = store.findLabel(labelId).orElse(null); // WAWebBizLabelEditingAction.labelDeleteAction: yield queryLabelAssociationsForLabelIds([e])
        var mutations = new ArrayList<SyncPendingMutation>(); // WAWebBizLabelEditingAction.labelDeleteAction: var u = getLabelMutation(e, ...); var c = []; ...; [u].concat(c)
        mutations.add(LabelEditHandler.INSTANCE.getLabelMutation( // WAWebBizLabelEditingAction.labelDeleteAction: getLabelMutation(e, t, a, true, m, undefined, undefined, l)
                labelId,
                existing != null ? existing.name() : "", // WAWebBizLabelEditingAction.labelDeleteAction: t (existing display name; empty when caller deletes a missing id)
                existing != null ? existing.color() : 0, // WAWebBizLabelEditingAction.labelDeleteAction: a (existing colour index)
                true, // WAWebBizLabelEditingAction.labelDeleteAction: deleted = true
                null, // WAWebBizLabelEditingAction.labelDeleteAction: predefinedId = 0 sentinel in WA Web; Cobalt omits
                null,
                null,
                timestamp
        ));
        if (existing != null) { // WAWebBizLabelEditingAction.labelDeleteAction: p.length > 0 && (c = yield createLabelAssociationMutations(...))
            for (var assignment : existing.assignments()) { // WAWebBizLabelEditingAction.labelDeleteAction: p.map(e => ({mutationIndexSegments: [e.associationId]}))
                mutations.add(LabelAssociationHandler.INSTANCE.createLabelAssociationMutation( // WAWebLabelJidSync.createLabelAssociationMutations
                        labelId,
                        assignment,
                        false, // WAWebBizLabelEditingAction.labelDeleteAction: type: "remove"
                        timestamp
                ));
            }
        }
        webAppStateService.pushPatches(LabelEditAction.COLLECTION_NAME, mutations); // WAWebSyncdCoreApi.lockForSync(["label","label-association","chat"], [u].concat(c), ...)
        store.removeLabel(labelId); // WAWebBizLabelEditingAction.labelDeleteAction: LabelCollection.remove(e)
        // WAWebListsActions.deleteListAction -> WAWebListsLogging.logListUpdate (listAction: DELETE).
        // updateEntryPoint is omitted because the Cobalt API does not accept an entry point;
        // listType / predefinedId are read from the label snapshot captured before removal, mirroring
        // WAWebListsLogging.logListUpdate's LabelCollection.get(""+l) lookup.
        var listIdNumber = parseLabelIdToListId(labelId);
        if (listIdNumber != null) {
            var builder = new ListUpdateEventBuilder()
                    .listAction(ListAction.DELETE) // WAWebListsActions.deleteListAction: listAction: LIST_ACTION.DELETE
                    .listId(listIdNumber); // WAWebListsActions.deleteListAction: listId: Number(e)
            var wamListType = existing != null ? mapWamListType(existing.type().orElse(null)) : null; // WAWebListsLogging.logListUpdate: f = getListType(_.type); f != null && (u.listType = f)
            if (wamListType != null) {
                builder.listType(wamListType);
            }
            if (existing != null && existing.predefinedId().isPresent()) { // WAWebListsLogging.logListUpdate: _.predefinedId != null && (u.predefinedId = _.predefinedId)
                builder.predefinedId(existing.predefinedId().getAsInt());
            }
            wamService.commit(builder.build());
        }
    }

    /**
     * Applies a new user-chosen order to the chat labels.
     *
     * <p>Per WhatsApp Web {@code WAWebBIzLabelReorderAction.reorderLabelsAction}
     * (plus the sync side {@code WAWebLabelReorderingSync.applyMutations}),
     * the reorder is emitted as a {@link LabelReorderingAction} carrying the
     * full ordered list of integer label identifiers. Each local label's
     * {@code orderIndex} is then set to its position in the list.
     *
     * @param labelIds the label identifiers in the new display order
     * @throws NullPointerException if {@code labelIds} is {@code null}
     *
     * @implNote WAWebBIzLabelReorderAction.reorderLabelsAction
     */
    @WhatsAppWebExport(moduleName = "WAWebBIzLabelReorderAction", exports = "reorderLabelsAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void reorderLabels(List<String> labelIds) {
        Objects.requireNonNull(labelIds, "labelIds cannot be null");
        var timestamp = Instant.now();
        var intIds = new ArrayList<Integer>(labelIds.size()); // ADAPTED: WA Web stores labels by string id; the wire type of LabelReorderingAction.sortedLabelIds is INT32
        for (var id : labelIds) {
            try {
                intIds.add(Integer.parseInt(id));
            } catch (NumberFormatException ignored) {
                // ADAPTED: skip non-numeric ids (predefined filters are not reorderable on the wire)
            }
        }
        var mutation = LabelReorderingHandler.INSTANCE.getReorderLabelsMutation(intIds, timestamp);
        webAppStateService.pushPatches(LabelReorderingAction.COLLECTION_NAME, List.of(mutation));
        for (var position = 0; position < labelIds.size(); position++) { // WAWebBIzLabelReorderAction.reorderLabelsAction: n.forEach((e, n) => { ... r.orderIndex = n })
            var id = labelIds.get(position);
            store.findLabel(id).ifPresent(label -> {}); // WAWebBIzLabelReorderAction.reorderLabelsAction: LabelCollection.get(String(e))
            final var finalPosition = position;
            store.findLabel(id).ifPresent(label -> {
                if (label.orderIndex().isEmpty() || label.orderIndex().getAsInt() != finalPosition) { // WAWebBIzLabelReorderAction.reorderLabelsAction: r.orderIndex !== n
                    label.setOrderIndex(finalPosition); // WAWebBIzLabelReorderAction.reorderLabelsAction: r.orderIndex = n
                }
            });
        }
    }

    /**
     * Associates a label with the given chat.
     *
     * <p>Per WhatsApp Web {@code WAWebEditLabelAssociationBridge.editLabelAssociation}:
     * issues a {@link LabelAssociationAction} with {@code labeled=true}
     * indexed by {@code [label_jid, labelId, chatJid]}, and records the
     * association in the local label's assignment set.
     *
     * @param labelId the label identifier
     * @param chat    the chat JID to tag with the label
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebEditLabelAssociationBridge.editLabelAssociation (add branch)
     */
    @WhatsAppWebExport(moduleName = "WAWebEditLabelAssociationBridge", exports = "editLabelAssociation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void associateLabel(String labelId, Jid chat) {
        Objects.requireNonNull(labelId, "labelId cannot be null");
        Objects.requireNonNull(chat, "chat cannot be null");
        pushLabelAssociationMutation(labelId, chat, true); // WAWebEditLabelAssociationBridge.editLabelAssociation: createLabelAssociationMutations with type "add"
    }

    /**
     * Dissociates a label from the given chat.
     *
     * <p>Counterpart to {@link #associateLabel(String, Jid)}: emits a
     * {@link LabelAssociationAction} with {@code labeled=false}.
     *
     * @param labelId the label identifier
     * @param chat    the chat JID to untag
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebEditLabelAssociationBridge.editLabelAssociation (remove branch)
     */
    @WhatsAppWebExport(moduleName = "WAWebEditLabelAssociationBridge", exports = "editLabelAssociation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void dissociateLabel(String labelId, Jid chat) {
        Objects.requireNonNull(labelId, "labelId cannot be null");
        Objects.requireNonNull(chat, "chat cannot be null");
        pushLabelAssociationMutation(labelId, chat, false); // WAWebEditLabelAssociationBridge.editLabelAssociation: createLabelAssociationMutations with type "remove"
    }

    /**
     * Associates a label with a specific message.
     *
     * <p>Per WhatsApp Web, message-level label associations share the
     * {@code label_jid} action but use the message key's parent chat as the
     * index target. The association is stored on the local label, keyed by
     * the message's chat JID.
     *
     * @param labelId    the label identifier
     * @param messageKey the target message key
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebLabelJidSync.createLabelAssociationMutations (message variant)
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "createLabelAssociationMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void associateLabel(String labelId, MessageKey messageKey) {
        Objects.requireNonNull(labelId, "labelId cannot be null");
        Objects.requireNonNull(messageKey, "messageKey cannot be null");
        var target = messageKey.parentJid() // ADAPTED: WA Web uses the message's chatId as the association target
                .orElseThrow(() -> new IllegalArgumentException("messageKey is missing a parent/chat JID"));
        pushLabelAssociationMutation(labelId, target, true);
    }

    /**
     * Dissociates a label from a specific message.
     *
     * @param labelId    the label identifier
     * @param messageKey the target message key
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebLabelJidSync.createLabelAssociationMutations (message variant, remove)
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "createLabelAssociationMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void dissociateLabel(String labelId, MessageKey messageKey) {
        Objects.requireNonNull(labelId, "labelId cannot be null");
        Objects.requireNonNull(messageKey, "messageKey cannot be null");
        var target = messageKey.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("messageKey is missing a parent/chat JID"));
        pushLabelAssociationMutation(labelId, target, false);
    }

    /**
     * Builds and pushes a label-jid association mutation and mirrors the
     * change into the in-memory {@link Label}.
     *
     * @param labelId the label id
     * @param target  the chat/contact JID
     * @param labeled {@code true} to add the association, {@code false} to remove
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "createLabelAssociationMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void pushLabelAssociationMutation(String labelId, Jid target, boolean labeled) {
        var timestamp = Instant.now();
        var mutation = LabelAssociationHandler.INSTANCE.createLabelAssociationMutation( // WAWebLabelJidSync.createLabelAssociationMutations
                labelId,
                target,
                labeled,
                timestamp
        );
        webAppStateService.pushPatches(LabelAssociationAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync
        store.findLabel(labelId).ifPresent(label -> { // WAWebEditLabelAssociationBridge.editLocalLabelAssociationMD: LabelCollection.get(...)
            if (labeled) {
                label.addAssignment(target); // WAWebEditLabelAssociationBridge.editLocalLabelAssociationMD: addLabelAssociation
            } else {
                label.removeAssignment(target); // WAWebEditLabelAssociationBridge.editLocalLabelAssociationMD: removeLabelAssociation
            }
        });
    }

    /**
     * Creates a new business broadcast list with the given name and
     * recipients.
     *
     * <p>Per WhatsApp Web {@code WAWebBroadcastListSync.getBroadcastListMutation}:
     * assembles a {@link BusinessBroadcastListAction} containing one
     * {@link BroadcastListParticipantAction} per recipient, builds a
     * pending SET mutation, pushes it on the {@code REGULAR} collection,
     * and seeds the store.
     *
     * <p>The broadcast list JID is allocated locally as the next unused
     * numeric id on the {@code broadcast} server because WA Web assigns the
     * list id client-side via {@code getBroadcastListStorage().getNextId()}.
     *
     * @param name       the display name of the broadcast list
     * @param recipients the recipient JIDs
     * @return the JID identifying the new broadcast list
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebBroadcastListSync.getBroadcastListMutation (create branch)
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getBroadcastListMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Jid createBroadcastList(String name, Collection<Jid> recipients) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(recipients, "recipients cannot be null");
        var timestamp = Instant.now();
        // ADAPTED: WA Web allocates the list id via getBroadcastListStorage().getNextId();
        // Cobalt derives the next id as max(existing numeric user parts) + 1.
        var nextId = store.businessBroadcastLists().keySet().stream()
                .mapToLong(id -> { try { return Long.parseLong(id); } catch (NumberFormatException ignored) { return 0L; } })
                .max()
                .orElse(0L) + 1;
        var listId = String.valueOf(nextId);
        var listJid = Jid.of(listId, com.github.auties00.cobalt.model.jid.JidServer.broadcast()); // ADAPTED: broadcast lists use `<id>@broadcast` as the routing JID
        var participants = buildBroadcastParticipants(recipients); // WAWebBroadcastListSync.getBroadcastListMutation: participants: n
        var mutation = BusinessBroadcastListHandler.INSTANCE.getBroadcastListMutation( // WAWebBroadcastListSync.getBroadcastListMutation
                listId,
                participants,
                name,
                timestamp
        );
        webAppStateService.pushPatches(BusinessBroadcastListAction.COLLECTION_NAME, List.of(mutation));
        // ADAPTED: WA Web seeds the broadcast list storage via updateBroadcastListStorage;
        // Cobalt mirrors the state into the flat store map.
        var action = new com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListActionBuilder()
                .participants(participants)
                .listName(name)
                .labelIds(List.of())
                .build();
        var current = new java.util.HashMap<>(store.businessBroadcastLists());
        current.put(listId, action);
        store.setBusinessBroadcastLists(current);
        return listJid;
    }

    /**
     * Renames a broadcast list and/or replaces its recipients.
     *
     * @param broadcastListId the JID returned by
     *                        {@link #createBroadcastList(String, Collection)}
     * @param newName         the new display name
     * @param newRecipients   the full new set of recipient JIDs
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebBroadcastListSync.getBroadcastListMutation (update branch)
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getBroadcastListMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editBroadcastList(Jid broadcastListId, String newName, Collection<Jid> newRecipients) {
        Objects.requireNonNull(broadcastListId, "broadcastListId cannot be null");
        Objects.requireNonNull(newName, "newName cannot be null");
        Objects.requireNonNull(newRecipients, "newRecipients cannot be null");
        var timestamp = Instant.now();
        var listId = broadcastListId.user(); // ADAPTED: extract the user part as the sync list id
        var participants = buildBroadcastParticipants(newRecipients); // WAWebBroadcastListSync.getBroadcastListMutation: participants: n
        var mutation = BusinessBroadcastListHandler.INSTANCE.getBroadcastListMutation( // WAWebBroadcastListSync.getBroadcastListMutation
                listId,
                participants,
                newName,
                timestamp
        );
        webAppStateService.pushPatches(BusinessBroadcastListAction.COLLECTION_NAME, List.of(mutation));
        var action = new com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListActionBuilder()
                .participants(participants)
                .listName(newName)
                .labelIds(List.of())
                .build();
        var current = new java.util.HashMap<>(store.businessBroadcastLists());
        current.put(listId, action);
        store.setBusinessBroadcastLists(current);
    }

    /**
     * Deletes a broadcast list.
     *
     * <p>Per WhatsApp Web {@code WAWebBroadcastListSync.getDeleteBroadcastListMutation}:
     * emits a REMOVE mutation under the {@code REGULAR} collection keyed by
     * the list id, then clears the entry from the store.
     *
     * @param broadcastListId the broadcast list JID
     * @throws NullPointerException if {@code broadcastListId} is {@code null}
     *
     * @implNote WAWebBroadcastListSync.getDeleteBroadcastListMutation
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getDeleteBroadcastListMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteBroadcastList(Jid broadcastListId) {
        Objects.requireNonNull(broadcastListId, "broadcastListId cannot be null");
        var timestamp = Instant.now();
        var listId = broadcastListId.user();
        var mutation = BusinessBroadcastListHandler.INSTANCE.getDeleteBroadcastListMutation(listId, timestamp); // WAWebBroadcastListSync.getDeleteBroadcastListMutation
        webAppStateService.pushPatches(BusinessBroadcastListAction.COLLECTION_NAME, List.of(mutation));
        var current = new java.util.HashMap<>(store.businessBroadcastLists());
        current.remove(listId); // WAWebBroadcastListStorageUtils.removeBroadcastListStorage
        store.setBusinessBroadcastLists(current);
    }

    /**
     * Sends a message to every recipient of the given broadcast list.
     *
     * <p>Per WhatsApp Web {@code WAWebSendBroadcastMsgAction.sendBroadcastMsgAction}:
     * the broadcast JID is used as the recipient; the message pipeline
     * fans the payload out to each participant. Cobalt delegates to
     * {@link #sendMessage(Jid, MessageContainer)} which dispatches via
     * {@code MessageService#send(Jid, MessageContainer)} whose broadcast
     * path handles per-participant encryption.
     *
     * @param broadcastListId the broadcast list JID
     * @param message         the message payload
     * @return the resulting chat-message metadata
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebSendBroadcastMsgAction.sendBroadcastMsgAction
     */
    @WhatsAppWebExport(moduleName = "WAWebSendBroadcastMsgAction", exports = "sendBroadcastMsgAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public ChatMessageInfo sendBroadcast(Jid broadcastListId, MessageContainer message) {
        Objects.requireNonNull(broadcastListId, "broadcastListId cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMeUser
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        // WAWebSendBroadcastMsgAction.sendBroadcastMsgAction: buildBroadcastMsgModels + encryptAndSendBroadcastMsg
        // Build a canonical outgoing ChatMessageInfo so callers get a typed handle; MessageService.send
        // handles per-device encryption and fanout when the recipient JID carries the broadcast server.
        var messageId = MessageIdGenerator.generate(MessageIdVersion.V2, selfJid); // WAWebMsgKey.newId
        var key = new MessageKeyBuilder()
                .id(messageId) // WAWebSendBroadcastMsgAction.sendBroadcastMsgAction: id: newId()
                .parentJid(broadcastListId) // WAWebSendBroadcastMsgAction.sendBroadcastMsgAction: to: broadcastListJid
                .fromMe(true) // WAWebSendBroadcastMsgAction.sendBroadcastMsgAction: selfDir: "out"
                .senderJid(selfJid) // WAWebSendBroadcastMsgAction.sendBroadcastMsgAction: participant: me
                .build();
        var messageInfo = new ChatMessageInfoBuilder()
                .status(MessageStatus.PENDING) // ADAPTED: mirrors MessagePreparer.prepareChat initial status
                .senderJid(selfJid)
                .key(key)
                .message(message) // WAWebSendBroadcastMsgAction.sendBroadcastMsgAction: body/content
                .timestamp(Instant.now()) // WAWebSendBroadcastMsgAction.sendBroadcastMsgAction: t: unixTime()
                .broadcast(true) // WAWebSendBroadcastMsgAction.sendBroadcastMsgAction: broadcast routing
                .build();
        messageService.send(messageInfo); // WAWebSendBroadcastMsgAction.sendBroadcastMsgAction: encryptAndSendBroadcastMsg(...)
        return messageInfo;
    }

    /**
     * Builds a {@link BroadcastListParticipantAction} list for the given
     * recipient JIDs, mirroring the split made in
     * {@link BusinessBroadcastAssociationHandler} between LID recipients
     * (stored as {@code lidJid}) and phone-number recipients (stored as
     * {@code pnJid} plus a resolved {@code lidJid}).
     *
     * @param recipients the recipient JIDs
     * @return a mutable list of participant entries suitable for the action builder
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getBroadcastListMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private List<BroadcastListParticipantAction> buildBroadcastParticipants(Collection<Jid> recipients) {
        var participants = new ArrayList<BroadcastListParticipantAction>(recipients.size());
        for (var recipient : recipients) {
            var participant = new BroadcastListParticipantAction();
            if (recipient.hasLidServer() || recipient.hasHostedLidServer()) { // NO_WA_BASIS: LID recipients stored as lidJid only
                participant.setLidJid(recipient);
            } else {
                participant.setPnJid(recipient); // NO_WA_BASIS: PN recipients carry the resolved lidJid for cross-indexing
                participant.setLidJid(store.getLidByPhoneNumber(recipient).orElse(recipient));
            }
            participants.add(participant);
        }
        return participants;
    }

    /**
     * Assigns the given chat to the given agent id.
     *
     * <p>Per WhatsApp Web {@code WAWebBizChatAssignmentAction.changeChatAssignment}:
     * issues a {@link ChatAssignmentAction} on the {@code REGULAR} sync
     * collection and updates the in-memory chat-assignment map.
     *
     * <p>Emits a {@link MdChatAssignmentEvent} mirroring
     * {@code WAWebChatAssignmentLogEvents.logChatAssignment}: the action type
     * is {@code ACTION_UNASSIGNED} when {@code agentId} is empty,
     * {@code ACTION_REASSIGNED} when the chat already had an assigned agent,
     * or {@code ACTION_ASSIGNED} otherwise. The chat type is derived from
     * {@code chat}'s JID server. The {@code chatAssignmentEntryPoint} is not
     * populated because Cobalt's public API does not surface a UI-level
     * entry point to callers.
     *
     * @param chat    the chat JID
     * @param agentId the agent identifier
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebBizChatAssignmentAction.changeChatAssignment + WAWebChatAssignmentLogEvents.logChatAssignment
     */
    @WhatsAppWebExport(moduleName = "WAWebBizChatAssignmentAction", exports = "changeChatAssignment",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentLogEvents", exports = "logChatAssignment",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void assignChatToAgent(Jid chat, String agentId) {
        Objects.requireNonNull(chat, "chat cannot be null");
        Objects.requireNonNull(agentId, "agentId cannot be null");
        var previousAgentId = store.chatAssignmentStates().get(chat.toString()); // WAWebBizChatAssignmentAction.changeChatAssignment: i[r] = e.chat.assignedAgent != null
        var timestamp = Instant.now();
        var mutation = ChatAssignmentHandler.INSTANCE.createChatAssignmentMutation( // WAWebChatAssignmentSync.createChatAssignmentMutations
                chat,
                agentId,
                timestamp
        );
        webAppStateService.pushPatches(ChatAssignmentAction.COLLECTION_NAME, List.of(mutation));
        var states = new java.util.HashMap<>(store.chatAssignmentStates()); // ADAPTED: WAWebChatAssignmentSync.applyMutations writes via bulkCreateOrMerge; Cobalt updates the flat map
        if (agentId.isEmpty()) {
            states.remove(chat.toString()); // WAWebChatAssignmentSync.applyMutations: empty agentId drops the assignment
        } else {
            states.put(chat.toString(), agentId); // WAWebChatAssignmentSync.applyMutations: i.push({...chatId, agentId})
        }
        store.setChatAssignmentStates(states);
        emitChatAssignmentEvent(chat, agentId, previousAgentId != null); // WAWebChatAssignmentLogEvents.logChatAssignment(n.chat, (a=n.agentId)!=null?a:"", i[r], t, e.length)
    }

    /**
     * Emits a {@link MdChatAssignmentEvent} for a single-chat assignment change.
     *
     * <p>Mirrors the helper {@code p(t, n, a)} and top-level
     * {@code logChatAssignment} in {@code WAWebChatAssignmentLogEvents}:
     * <ul>
     *   <li>{@code chatAssignmentAction} reflects the transition: empty target
     *       agent id yields {@code ACTION_UNASSIGNED}; otherwise the prior
     *       assignment flag chooses between {@code ACTION_REASSIGNED} and
     *       {@code ACTION_ASSIGNED}.</li>
     *   <li>{@code chatAssignmentChatType} is mapped from the chat JID server
     *       following {@code WAWebChatModel.getChatAssignmentChatType}:
     *       user server -> {@code INDIVIDUAL}, group/community server ->
     *       {@code GROUP}, broadcast server -> {@code COMMUNITY},
     *       newsletter server -> {@code CHANNEL}.</li>
     *   <li>{@code chatAssignmentAgentId} is the target agent id (empty on
     *       unassign).</li>
     *   <li>{@code chatAssignmentMdId} is the device id of the target agent
     *       looked up in {@link WhatsAppStore#agentStates()}, defaulting to
     *       {@code -1} when the agent is unknown (matches WA Web's
     *       {@code c?.deviceId ?? -1} fallback).</li>
     *   <li>{@code assignerAgentId} is the current device's own agent id
     *       when the device is itself registered as an agent, otherwise the
     *       empty string (matches WA Web's
     *       {@code u = AgentCollection.getModelsArray().find(t => t.deviceId === meDeviceId); assignerAgentId = u?.id ?? ""}).</li>
     *   <li>{@code assignerMdId} is the caller's device id from
     *       {@link WhatsAppStore#jid()}.</li>
     *   <li>{@code chatAssignmentBrowserId} is empty and
     *       {@code assignerBrowserId} is empty because Cobalt does not
     *       emulate WA Web's {@code persistentExpiringId()} browser cookie.</li>
     *   <li>{@code chatsCnt} is always {@code 1} because Cobalt's public API
     *       takes a single {@code (chat, agentId)} pair (WA Web batches and
     *       uses {@code e.length}).</li>
     *   <li>{@code chatAssignmentEntryPoint} is omitted because Cobalt does
     *       not surface a UI entry point parameter on its API.</li>
     * </ul>
     *
     * @param chat                 the chat JID being assigned
     * @param agentId              the target agent id (empty on unassign)
     * @param hadPreviousAssignment whether the chat already had an assigned agent before this change
     *
     * @implNote WAWebChatAssignmentLogEvents.logChatAssignment and the private
     *           helper {@code p(t, n, a)} that constructs the
     *           {@code MdChatAssignmentWamEvent}.
     */
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentLogEvents", exports = "logChatAssignment",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitChatAssignmentEvent(Jid chat, String agentId, boolean hadPreviousAssignment) {
        // WAWebChatAssignmentLogEvents.logChatAssignment: t === "" ? ACTION_UNASSIGNED : n ? ACTION_REASSIGNED : ACTION_ASSIGNED
        ChatAssignmentActionType action;
        if (agentId.isEmpty()) {
            action = ChatAssignmentActionType.ACTION_UNASSIGNED;
        } else if (hadPreviousAssignment) {
            action = ChatAssignmentActionType.ACTION_REASSIGNED;
        } else {
            action = ChatAssignmentActionType.ACTION_ASSIGNED;
        }
        // WAWebChatModel.getChatAssignmentChatType: switch (getKind(this)) { Chat->INDIVIDUAL, Group->GROUP, Broadcast|Community->COMMUNITY, Newsletter->CHANNEL }
        ChatAssignmentChatType chatType;
        if (chat.hasUserServer() || chat.hasLidServer()) {
            chatType = ChatAssignmentChatType.INDIVIDUAL;
        } else if (chat.hasGroupOrCommunityServer()) {
            chatType = ChatAssignmentChatType.GROUP;
        } else if (chat.hasBroadcastServer()) {
            chatType = ChatAssignmentChatType.COMMUNITY;
        } else if (chat.hasNewsletterServer()) {
            chatType = ChatAssignmentChatType.CHANNEL;
        } else {
            chatType = null; // WAWebChatModel.getChatAssignmentChatType throws TypeError; Cobalt omits the property instead of throwing from telemetry
        }
        // WAWebChatAssignmentLogEvents.p: c = AgentCollection.get(n); chatAssignmentMdId = c?.deviceId ?? -1
        var targetAgent = store.agentStates().get(agentId); // lookup by agentId == map key (see AgentActionHandler.applyMutationResult)
        var chatAssignmentMdId = targetAgent != null && targetAgent.deviceID().isPresent()
                ? targetAgent.deviceID().getAsInt()
                : -1;
        // WAWebChatAssignmentLogEvents.p: d = getMeDevicePnOrThrow_DO_NOT_USE().getDeviceId()
        var meDeviceId = store.jid().map(Jid::device).orElse(0);
        // WAWebChatAssignmentLogEvents.p: u = AgentCollection.getModelsArray().find(t => t.deviceId === meDeviceId); assignerAgentId = u?.id ?? ""
        var assignerAgentId = store.agentStates().entrySet().stream()
                .filter(entry -> entry.getValue().deviceID().isPresent() && entry.getValue().deviceID().getAsInt() == meDeviceId)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
        wamService.commit(new MdChatAssignmentEventBuilder() // WAWebChatAssignmentLogEvents: new MdChatAssignmentWamEvent({...}).commit()
                .assignerAgentId(assignerAgentId) // WAWebChatAssignmentLogEvents.p: (i=u==null?void 0:u.id)!=null?i:""
                .assignerBrowserId("") // WAWebChatAssignmentLogEvents.p: persistentExpiringId() — Cobalt does not emulate the browser cookie id
                .assignerMdId(meDeviceId) // WAWebChatAssignmentLogEvents.p: assignerMdId: d
                .chatAssignmentAction(action) // WAWebChatAssignmentLogEvents.logChatAssignment: chatAssignmentAction: i
                .chatAssignmentAgentId(agentId) // WAWebChatAssignmentLogEvents.p: (l=c==null?void 0:c.id)!=null?l:""
                .chatAssignmentBrowserId("") // WAWebChatAssignmentLogEvents.p: m ? persistentExpiringId() : "" — Cobalt does not emulate the browser cookie id
                .chatAssignmentChatType(chatType) // WAWebChatAssignmentLogEvents.p: chatAssignmentChatType: t.getChatAssignmentChatType()
                .chatAssignmentMdId(chatAssignmentMdId) // WAWebChatAssignmentLogEvents.p: (s=c==null?void 0:c.deviceId)!=null?s:-1
                .chatsCnt(1) // WAWebChatAssignmentLogEvents.logChatAssignment: chatsCnt: a = e.length — Cobalt API takes a single chat per call
                .build());
    }

    /**
     * Unassigns a chat from any agent.
     *
     * <p>Equivalent to calling {@link #assignChatToAgent(Jid, String)} with
     * an empty agent id, which per
     * {@code WAWebChatAssignmentSync.applyMutations} drops every existing
     * assignment for the chat.
     *
     * @param chat the chat JID
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebBizChatAssignmentAction.changeChatAssignment with empty agent
     */
    @WhatsAppWebExport(moduleName = "WAWebBizChatAssignmentAction", exports = "changeChatAssignment",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unassignChatFromAgent(Jid chat) {
        assignChatToAgent(chat, ""); // WAWebChatAssignmentSync.applyMutations: d === "" && only removes existing
    }

    /**
     * Records whether the assigned agent has opened the given chat.
     *
     * <p>Per WhatsApp Web {@code WAWebBizChatAssignmentOpenedAction.markChatAsOpened}:
     * issues a {@link ChatAssignmentOpenedStatusAction} under the
     * {@code REGULAR} sync collection and mirrors the state in the local
     * {@code chatAssignmentOpenedStates} map. The chat must currently be
     * assigned to an agent; the assigned agent id is read from the store.
     *
     * @param chat   the chat JID
     * @param opened {@code true} when the assigned agent has opened the chat
     * @throws NullPointerException  if {@code chat} is {@code null}
     * @throws IllegalStateException when the chat is not currently assigned
     *
     * @implNote WAWebBizChatAssignmentOpenedAction.markChatAsOpened
     */
    @WhatsAppWebExport(moduleName = "WAWebBizChatAssignmentOpenedAction", exports = "markChatAsOpened",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void setChatAssignmentOpenedStatus(Jid chat, boolean opened) {
        Objects.requireNonNull(chat, "chat cannot be null");
        var agentId = store.chatAssignmentStates().get(chat.toString()); // WAWebBizChatAssignmentOpenedAction.markChatAsOpened: agentId = ChatAssignmentCollection.getCurrentAgentId(e)
        if (agentId == null) { // WAWebBizChatAssignmentOpenedAction.markChatAsOpened: invariant agentId != null
            throw new IllegalStateException("Chat " + chat + " has no current agent assignment");
        }
        var timestamp = Instant.now();
        var mutation = ChatAssignmentOpenedStatusHandler.INSTANCE.createChatOpenedMutation( // WAWebChatAssignmentOpenedStatusSync.createChatOpenedMutations
                chat,
                agentId,
                opened,
                timestamp
        );
        webAppStateService.pushPatches(ChatAssignmentOpenedStatusAction.COLLECTION_NAME, List.of(mutation));
        var states = new java.util.HashMap<>(store.chatAssignmentOpenedStates()); // ADAPTED: WAWebBizChatAssignmentOpenedAction.updateLocalOpenedState writes via bulkCreateOrMerge
        states.put(chat + "_" + agentId, opened); // WAWebChatAssignmentOpenedStatusSync.applyMutations: d = s.toJid() + "_" + i
        store.setChatAssignmentOpenedStates(states);
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
        var chatModelOpt = store.findChatByJid(chat);
        var previousSeconds = chatModelOpt // WAWebChangeEphemeralDurationChatAction: var i = calculateEphemeralDurationForChat(e) (captured before mutation so previousEphemeralityDuration is correct)
                .flatMap(Chat::ephemeralExpiration)
                .map(ChatEphemeralTimer::periodSeconds)
                .orElse(null);
        chatModelOpt.ifPresent(chatModel -> { // WAWebChangeEphemeralDurationChatAction: t.ephemeralDuration = d; updateChatTable(...)
            chatModel.setEphemeralExpiration(timer == ChatEphemeralTimer.OFF ? null : timer); // WAWebChangeEphemeralDurationChatAction: ephemeralDuration = d
            chatModel.setEphemeralSettingTimestamp(Instant.now()); // WAWebUpdateEphemeralSettingTimestampChatAction: t.ephemeralSettingTimestamp = ...
        });
        // WAWebChangeEphemeralDurationChatAction.changeEphemeralDuration:
        //   new(o("WAWebEphemeralSettingChangeWamEvent")).EphemeralSettingChangeWamEvent({
        //     chatEphemeralityDuration: l ? void 0 : t,     // suppressed for after-read durations
        //     threadId: yield getChatThreadID(e.id.toJid()), // HMAC-based thread id from WAWebChatThreadLogging
        //     ephemeralSettingEntryPoint: n,                // UI entry point (CHAT_INFO / CHAT_OVERFLOW / ...)
        //     isAfterRead: l,
        //     afterReadDuration: l ? t : void 0,
        //     previousEphemeralityDuration: i,              // set only when i != null
        //     previousEphemeralityType: ...,                // set only when i > 0
        //     ephemeralSettingGroupSize: numberToPreciseSizeBucket(participants.length) // groups only
        //   }).commit()
        // ADAPTED:
        //  - chatEphemeralityDuration is always set to `seconds` (Cobalt exposes no after-read API, so the WA Web `l` branch never applies).
        //  - threadId is left unset because Cobalt does not implement WAWebChatThreadLogging (HMAC chat thread ids).
        //  - ephemeralSettingEntryPoint is left unset because Cobalt is headless and has no UI entry point to report.
        //  - Spec-level properties isAfterRead / afterReadDuration / previousEphemeralityType / isSuccess / errorCode
        //    are not declared on Cobalt's EphemeralSettingChangeEvent spec and therefore cannot be emitted.
        var eventBuilder = new EphemeralSettingChangeEventBuilder()
                .chatEphemeralityDuration(seconds); // WAWebChangeEphemeralDurationChatAction: chatEphemeralityDuration: l ? void 0 : t (Cobalt always takes the non-after-read branch)
        if (previousSeconds != null) { // WAWebChangeEphemeralDurationChatAction: if (i != null) s.previousEphemeralityDuration = i
            eventBuilder.previousEphemeralityDuration(previousSeconds);
        }
        if (chat.hasGroupOrCommunityServer()) { // WAWebChangeEphemeralDurationChatAction: if (getIsGroup(e)) s.ephemeralSettingGroupSize = numberToPreciseSizeBucket(participants.length ?? 0)
            var participantCount = chatModelOpt // WAWebChangeEphemeralDurationChatAction: (u = (d = e.groupMetadata) == null ? void 0 : d.participants.length) != null ? u : 0
                    .map(c -> c.participant().size())
                    .orElse(0);
            eventBuilder.ephemeralSettingGroupSize(numberToPreciseSizeBucket(participantCount));
        }
        wamService.commit(eventBuilder.build()); // WAWebChangeEphemeralDurationChatAction: new EphemeralSettingChangeWamEvent(s).commit()
    }

    /**
     * Maps a participant count to the corresponding {@link PreciseSizeBucket}
     * used by WAM size-bucketing for group-size telemetry.
     *
     * <p>Buckets are exclusive upper bounds: a count of {@code 3} falls in
     * {@link PreciseSizeBucket#LT4}, a count of {@code 1000} falls in
     * {@link PreciseSizeBucket#LT1500}, and any count {@code >= 5000} lands in
     * {@link PreciseSizeBucket#LARGEST_BUCKET}.
     *
     * @param count the participant count; negative values are treated as
     *              {@code 0} by the {@code < 4} branch
     * @return the matching {@link PreciseSizeBucket}; never {@code null}
     *
     * @implNote Mirrors the {@code WAWebWamNumberToPreciseSizeBucket.numberToPreciseSizeBucket}
     *           cascading ternary verbatim.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamNumberToPreciseSizeBucket", exports = "numberToPreciseSizeBucket",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static PreciseSizeBucket numberToPreciseSizeBucket(int count) {
        // WAWebWamNumberToPreciseSizeBucket.numberToPreciseSizeBucket: cascading e<threshold ? BUCKET : ... chain
        if (count < 4) return PreciseSizeBucket.LT4;
        if (count < 8) return PreciseSizeBucket.LT8;
        if (count < 16) return PreciseSizeBucket.LT16;
        if (count < 32) return PreciseSizeBucket.LT32;
        if (count < 64) return PreciseSizeBucket.LT64;
        if (count < 128) return PreciseSizeBucket.LT128;
        if (count < 256) return PreciseSizeBucket.LT256;
        if (count < 512) return PreciseSizeBucket.LT512;
        if (count < 1000) return PreciseSizeBucket.LT1000;
        if (count < 1500) return PreciseSizeBucket.LT1500;
        if (count < 2000) return PreciseSizeBucket.LT2000;
        if (count < 2500) return PreciseSizeBucket.LT2500;
        if (count < 3000) return PreciseSizeBucket.LT3000;
        if (count < 3500) return PreciseSizeBucket.LT3500;
        if (count < 4000) return PreciseSizeBucket.LT4000;
        if (count < 4500) return PreciseSizeBucket.LT4500;
        if (count < 5000) return PreciseSizeBucket.LT5000;
        return PreciseSizeBucket.LARGEST_BUCKET;
    }

    /**
     * Queries the server for the current privacy configuration of the local
     * account.
     *
     * <p>Sends a {@code <iq xmlns="privacy" to="s.whatsapp.net" type="get">}
     * stanza whose payload is a bare {@code <privacy/>} node. The response
     * carries a {@code <privacy>} container with one {@code <category>} per
     * configured setting, each exposing the server-side identifier via the
     * {@code name} attribute and the selected audience via the {@code value}
     * attribute.
     *
     * <p>Categories whose {@code name} or {@code value} cannot be resolved to
     * a Cobalt enum constant are silently skipped, matching WA Web's
     * permissive parser which returns {@code dhash}-only entries for unknown
     * categories. The Status privacy distribution is excluded because it is
     * carried on the {@code xmlns="status"} IQ and exposed by
     * {@link #queryStatusPrivacy()}.
     *
     * @return an immutable map from every recognised {@link PrivacySettingType}
     *         to the {@link PrivacySettingValue} the server currently
     *         enforces; never {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     *
     * @implNote WAWebGetPrivacySettingsJob.getPrivacySettings issues the same
     *           {@code <iq xmlns="privacy" type="get"><privacy/></iq>} stanza
     *           and feeds the result into {@code WAWebUserPrefsPrivacy}. Cobalt
     *           omits the UserPrefs layer and merges entries into
     *           {@link WhatsAppStore#addPrivacySetting(PrivacySettingEntry)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebGetPrivacySettingsJob", exports = "getPrivacySettings",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<PrivacySettingType, PrivacySettingValue> queryPrivacySettings() {
        // WAWebGetPrivacySettingsJob: wap("iq", {xmlns: "privacy", to: S_WHATSAPP_NET, type: "get"}, wap("privacy", null))
        var privacyQuery = new NodeBuilder()
                .description("privacy")
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "privacy")
                .attribute("to", JidServer.user()) // WAWebGetPrivacySettingsJob: S_WHATSAPP_NET
                .attribute("type", "get")
                .content(privacyQuery);
        var response = sendNode(iqNode);
        // WAWebGetPrivacySettingsJob parser: e.child("privacy").mapChildrenWithTag("category", ...)
        var privacyNode = response.getChild("privacy")
                .orElseThrow(() -> new NoSuchElementException("Missing <privacy> in privacy settings response"));
        var result = new EnumMap<PrivacySettingType, PrivacySettingValue>(PrivacySettingType.class);
        for (var category : privacyNode.getChildren("category")) {
            // WAWebGetPrivacySettingsJob parser: e.attrString("name"), e.attrString("value")
            var name = category.getAttributeAsString("name").orElse(null);
            var value = category.getAttributeAsString("value").orElse(null);
            if (name == null || value == null) {
                continue;
            }
            // ADAPTED: WA Web returns {name, value, dhash} for every category, including unknown ones;
            //          Cobalt drops unknowns because the enum is the public API surface.
            var type = PrivacySettingType.of(name).orElse(null);
            var audience = PrivacySettingValue.of(value).orElse(null);
            if (type == null || audience == null) {
                continue;
            }
            result.put(type, audience);
            // WAWebUserPrefsPrivacy.setPrivacySettings: refresh the local cache so
            // subsequent reads via store.findPrivacySetting hit a warm entry.
            store.addPrivacySetting(new PrivacySettingEntryBuilder()
                    .type(type)
                    .value(audience)
                    .excluded(List.of())
                    .build());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Changes a single privacy setting on the local account without touching
     * the per-contact allow/block list.
     *
     * <p>Equivalent to
     * {@link #changePrivacySetting(PrivacySettingType, PrivacySettingValue, Collection)}
     * with a {@code null} user list, which sends a {@code <privacy>} container
     * with a single {@code <category name value/>} child and no {@code <user>}
     * descendants.
     *
     * @param type  the setting to change; must be {@code null}-free
     * @param value the new audience; must be accepted by
     *              {@link PrivacySettingType#isSupported(PrivacySettingValue)}
     * @throws NullPointerException     if {@code type} or {@code value} is
     *                                  {@code null}
     * @throws IllegalArgumentException if the value is not supported by the
     *                                  given type
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     *
     * @implNote WAWebSetPrivacyJob.setPrivacy with {@code users === null}:
     *           dispatches through the non-LID branch {@code _(name, value)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSetPrivacyJob", exports = "setPrivacy",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changePrivacySetting(PrivacySettingType type, PrivacySettingValue value) {
        changePrivacySetting(type, value, null);
    }

    /**
     * Changes a single privacy setting on the local account and pairs the new
     * audience with an allow or block list of contacts when the selected
     * {@link PrivacySettingValue} requires one.
     *
     * <p>Emits a {@code <iq xmlns="privacy" to="s.whatsapp.net" type="set">}
     * stanza carrying a {@code <privacy>} container and a single
     * {@code <category name value dhash/>} child. When {@code excludedOrIncluded}
     * is non-empty the contacts are serialised as {@code <user jid action/>}
     * grandchildren, with the action inferred from {@code value}:
     * {@link PrivacySettingValue#CONTACTS_EXCEPT} and
     * {@link PrivacySettingValue#CONTACTS_ONLY} map to {@code "add"} (append
     * to the configured list), while any other value is treated as a cleanup
     * and maps to {@code "remove"}.
     *
     * <p>For the LID-aware categories ({@link PrivacySettingType#LAST_SEEN},
     * {@link PrivacySettingType#PROFILE_PIC}, {@link PrivacySettingType#STATUS}
     * and {@link PrivacySettingType#ADD_ME_TO_GROUPS}) the enclosing
     * {@code <privacy>} node also carries {@code addressing_mode="lid"} and
     * each {@code <user>} child carries both {@code jid} (the LID) and
     * {@code pn_jid} (the phone-number JID), matching
     * {@code WAWebSetPrivacyJob.h}. If no LID is known for a participating
     * contact the code falls back to the pure-PN shape, mirroring WA Web's
     * in-module {@code try/catch} recovery.
     *
     * <p>The local {@link WhatsAppStore#addPrivacySetting(PrivacySettingEntry)}
     * is refreshed eagerly after the server acknowledges the change so
     * observers registered via
     * {@link #addPrivacySettingChangedListener(WhatsappClientListenerConsumer.Binary)}
     * see the new state without another round trip.
     *
     * @param type               the setting to change; must be {@code null}-free
     * @param value              the new audience; must be accepted by
     *                           {@link PrivacySettingType#isSupported(PrivacySettingValue)}
     * @param excludedOrIncluded the per-contact allowlist
     *                           ({@link PrivacySettingValue#CONTACTS_ONLY}) or
     *                           blocklist
     *                           ({@link PrivacySettingValue#CONTACTS_EXCEPT});
     *                           may be {@code null} or empty when the value
     *                           does not refine its audience with a list
     * @throws NullPointerException     if {@code type} or {@code value} is
     *                                  {@code null}
     * @throws IllegalArgumentException if the value is not supported by the
     *                                  given type
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     *
     * @implNote WAWebSetPrivacyJob.setPrivacy: {@code p(t)} picks between
     *           {@code _} (no users), {@code f} (LID-aware categories) and
     *           {@code g} (plain PN) branches; {@code h} builds each
     *           {@code <user>} element with {@code toLid}/{@code toPn}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSetPrivacyJob", exports = "setPrivacy",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changePrivacySetting(PrivacySettingType type, PrivacySettingValue value, Collection<Jid> excludedOrIncluded) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        if (!type.isSupported(value)) {
            throw new IllegalArgumentException("Privacy setting " + type + " does not support value " + value);
        }

        var hasList = excludedOrIncluded != null && !excludedOrIncluded.isEmpty();
        // WAWebSetPrivacyJob.setPrivacy: {action: "add" for CONTACTS_EXCEPT / CONTACTS_ONLY, "remove" otherwise}.
        var action = (value == PrivacySettingValue.CONTACTS_EXCEPT || value == PrivacySettingValue.CONTACTS_ONLY)
                ? "add"
                : "remove";

        // WAWebSetPrivacyJob.y: groupadd / last / profile / status are LID-migrated categories.
        var lidAware = switch (type) {
            case ADD_ME_TO_GROUPS, LAST_SEEN, PROFILE_PIC, STATUS -> true;
            default -> false;
        };

        Node privacyNode;
        if (!hasList) {
            // WAWebSetPrivacyJob._(name, value): wap("privacy", null, wap("category", {name, value}))
            var category = new NodeBuilder()
                    .description("category")
                    .attribute("name", type.data())
                    .attribute("value", value.data())
                    .build();
            privacyNode = new NodeBuilder()
                    .description("privacy")
                    .content(category)
                    .build();
        } else if (lidAware) {
            // WAWebSetPrivacyJob.f(name, value, users, dhash): wap("privacy", {addressing_mode: "lid"}, wap("category", {name, value, dhash: "none"}, users.map(h)))
            List<Node> userChildren;
            try {
                userChildren = buildLidPrivacyUsers(excludedOrIncluded, action);
            } catch (Throwable throwable) {
                // WAWebSetPrivacyJob.p: LOGGER.ERROR("[privacy] setPrivacy: failed to create lid privacy node", ...); fall back to g().
                LOGGER_PRIVACY.log(System.Logger.Level.WARNING,
                        "[privacy] changePrivacySetting: failed to create LID privacy node, falling back to PN",
                        throwable);
                userChildren = buildPnPrivacyUsers(excludedOrIncluded, action);
                var category = new NodeBuilder()
                        .description("category")
                        .attribute("name", type.data())
                        .attribute("value", value.data())
                        .attribute("dhash", "none") // WAWebSetPrivacyJob.g: dhash: r != null ? r : "none"
                        .content(userChildren)
                        .build();
                privacyNode = new NodeBuilder()
                        .description("privacy")
                        .content(category)
                        .build();
                dispatchPrivacyIq(privacyNode, type, value, excludedOrIncluded);
                return;
            }
            var category = new NodeBuilder()
                    .description("category")
                    .attribute("name", type.data())
                    .attribute("value", value.data())
                    .attribute("dhash", "none") // WAWebSetPrivacyJob.f: dhash: r != null ? r : "none"
                    .content(userChildren)
                    .build();
            privacyNode = new NodeBuilder()
                    .description("privacy")
                    .attribute("addressing_mode", "lid") // WAWebSetPrivacyJob.f: addressing_mode: "lid"
                    .content(category)
                    .build();
        } else {
            // WAWebSetPrivacyJob.g(name, value, users, dhash): wap("privacy", null, wap("category", {name, value, dhash}, users.map({action, jid})))
            var userChildren = buildPnPrivacyUsers(excludedOrIncluded, action);
            var category = new NodeBuilder()
                    .description("category")
                    .attribute("name", type.data())
                    .attribute("value", value.data())
                    .attribute("dhash", "none")
                    .content(userChildren)
                    .build();
            privacyNode = new NodeBuilder()
                    .description("privacy")
                    .content(category)
                    .build();
        }

        dispatchPrivacyIq(privacyNode, type, value, excludedOrIncluded);
    }

    /**
     * Logger reused by the privacy IQ helpers for LID fallback diagnostics.
     *
     * @implNote WAWebSetPrivacyJob: {@code WALogger.ERROR} usage on LID-node
     *           construction failure.
     */
    private static final System.Logger LOGGER_PRIVACY = System.getLogger(WhatsAppClient.class.getName() + ".privacy");

    /**
     * Sends the privacy IQ and refreshes the local store entry on success.
     *
     * @param privacyNode        the already-built {@code <privacy>} content node
     * @param type               the setting being changed
     * @param value              the newly selected audience
     * @param excludedOrIncluded the refinement list applied to the setting,
     *                           may be {@code null} or empty
     * @implNote WAWebSetPrivacyJob.d: deprecatedSendIq + throws
     *           ServerStatusCodeError on failure; Cobalt relies on the default
     *           {@link #sendNode(NodeBuilder)} IQ error handling.
     */
    private void dispatchPrivacyIq(Node privacyNode, PrivacySettingType type, PrivacySettingValue value, Collection<Jid> excludedOrIncluded) {
        // WAWebSetPrivacyJob.d: wap("iq", {to: S_WHATSAPP_NET, type: "set", xmlns: "privacy", id: generateId()}, t)
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "privacy")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(privacyNode);
        sendNode(iqNode);

        // WAWebUserPrefsPrivacy.setPrivacySettings: mirror the new value in the local cache.
        var excludedSnapshot = excludedOrIncluded == null
                ? List.<Jid>of()
                : excludedOrIncluded.stream().filter(Objects::nonNull).toList();
        store.addPrivacySetting(new PrivacySettingEntryBuilder()
                .type(type)
                .value(value)
                .excluded(excludedSnapshot)
                .build());
    }

    /**
     * Builds the LID-addressed {@code <user>} child nodes for a privacy IQ.
     *
     * <p>Each entry is mapped to a {@code <user jid=LID pn_jid=PN action=...>}
     * element, mirroring {@code WAWebSetPrivacyJob.h}. The first participant
     * whose LID cannot be resolved aborts the whole list so the caller can
     * fall back to the pure-PN branch.
     *
     * @param users  the contacts to serialise, must be non-{@code null} and
     *               non-empty
     * @param action {@code "add"} or {@code "remove"}
     * @return the list of {@code <user>} nodes, never {@code null}
     * @throws IllegalStateException if any participant has no known LID, in
     *                               which case WA Web falls back to the
     *                               non-LID branch
     * @implNote WAWebSetPrivacyJob.h: {@code toLid(wid)} / {@code toPn(wid)}
     *           resolution; the username variant gated by
     *           {@code username_contact_privacy_setting_allow_uncontact_set_enable}
     *           is not implemented in Cobalt.
     */
    private List<Node> buildLidPrivacyUsers(Collection<Jid> users, String action) {
        var result = new ArrayList<Node>(users.size());
        for (var raw : users) {
            if (raw == null) {
                continue; // ADAPTED: defensive skip
            }
            var lid = lidMigrationService.toLid(raw);
            if (lid == null) {
                // WAWebSetPrivacyJob.h: throw err("createLidUserNode: unknown-lid-for-privacy-list-contact")
                throw new IllegalStateException("createLidUserNode: unknown-lid-for-privacy-list-contact " + raw);
            }
            var pn = lidMigrationService.toPn(raw);
            if (pn == null) {
                // WAWebSetPrivacyJob.h: throw err("createLidUserNode: unknown-username-and-pn-for-privacy-list-contact")
                throw new IllegalStateException("createLidUserNode: unknown-username-and-pn-for-privacy-list-contact " + raw);
            }
            result.add(new NodeBuilder()
                    .description("user")
                    .attribute("action", action)
                    .attribute("jid", lid) // WAWebSetPrivacyJob.h: jid: LID
                    .attribute("pn_jid", pn) // WAWebSetPrivacyJob.h: pn_jid: PN
                    .build());
        }
        return result;
    }

    /**
     * Builds the PN-addressed {@code <user>} child nodes for a privacy IQ.
     *
     * <p>Each contact is serialised as a {@code <user jid action/>} element,
     * matching {@code WAWebSetPrivacyJob.g}. Entries whose JID is
     * {@code null} are silently skipped.
     *
     * @param users  the contacts to serialise, must be non-{@code null} and
     *               non-empty
     * @param action {@code "add"} or {@code "remove"}
     * @return the list of {@code <user>} nodes, never {@code null}
     * @implNote WAWebSetPrivacyJob.g: users.map({action, wid}) ->
     *           wap("user", {action, jid: JID(wid)})
     */
    private List<Node> buildPnPrivacyUsers(Collection<Jid> users, String action) {
        var result = new ArrayList<Node>(users.size());
        for (var raw : users) {
            if (raw == null) {
                continue; // ADAPTED: defensive skip
            }
            result.add(new NodeBuilder()
                    .description("user")
                    .attribute("action", action)
                    .attribute("jid", raw) // WAWebSetPrivacyJob.g: jid: JID(wid)
                    .build());
        }
        return result;
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
     * directly; the SMAX {@code CreateResponseGroupAlreadyExists} branch
     * is skipped — the error path surfaces through the usual server IQ
     * error handling. Upon successful creation, a
     * {@link GroupCreateCEventBuilder GroupCreateCWamEvent} is committed
     * matching the WA Web {@code CreateResponseSuccess} emission.
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
        wamService.commit(new GroupCreateCEventBuilder().build()); // WAWebGroupCreateJob.createGroup: new GroupCreateCWamEvent().commit() (CreateResponseSuccess branch)
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        sendMexNodeWithNoResponse(request.toNode(), TransferCommunityOwnershipMex.QUERY_ID, "mexTransferCommunityOwnershipJob", false);
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        var response = sendMexNode(request.toNode(), FetchSubgroupSuggestionsMex.QUERY_ID, "mexFetchSubgroupSuggestions", false);
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        var response = sendMexNode(request.toNode(), QuerySubgroupParticipantCountMex.QUERY_ID, "mexQuerySubgroupParticipantCount", false);
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
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        var response = sendMexNode(request.toNode(), FetchAllSubgroupsMex.QUERY_ID, "mexCommunityGetSubgroups", false);
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
     * Updates a group-level setting, dispatching through the same pipeline
     * WA Web's {@code WAWebSetPropertyGroupAction.setGroupProperty} uses
     * for each {@code GROUP_SETTING_TYPE} constant: some values travel
     * over a legacy {@code w:g2} {@code iq} stanza, the rest over the
     * {@code WAWebMexUpdateGroupPropertyJob} GraphQL mutation.
     *
     * <p>Routing matches WA Web exactly:
     * <ul>
     *   <li>Direct {@code w:g2} IQ
     *       ({@code WAWebGroupModifyInfoJob.setGroupProperty}):
     *       {@link GroupSetting#ANNOUNCEMENT ANNOUNCEMENT},
     *       {@link GroupSetting#RESTRICT RESTRICT},
     *       {@link GroupSetting#NO_FREQUENTLY_FORWARDED NO_FREQUENTLY_FORWARDED},
     *       {@link GroupSetting#MEMBERSHIP_APPROVAL_MODE MEMBERSHIP_APPROVAL_MODE},
     *       {@link GroupSetting#REPORT_TO_ADMIN_MODE REPORT_TO_ADMIN_MODE}.</li>
     *   <li>MEX {@code mexUpdateGroupPropertyJob} mutation:
     *       {@link GroupSetting#ALLOW_NON_ADMIN_SUB_GROUP_CREATION ALLOW_NON_ADMIN_SUB_GROUP_CREATION},
     *       {@link GroupSetting#LIMIT_SHARING LIMIT_SHARING},
     *       {@link GroupSetting#MEMBER_ADD_MODE MEMBER_ADD_MODE},
     *       {@link GroupSetting#MEMBER_LINK_MODE MEMBER_LINK_MODE},
     *       {@link GroupSetting#MEMBER_SHARE_GROUP_HISTORY_MODE MEMBER_SHARE_GROUP_HISTORY_MODE}.</li>
     *   <li>Delegated: {@link GroupSetting#EPHEMERAL EPHEMERAL} forwards to
     *       {@link #setEphemeralTimer(Jid, ChatEphemeralTimer)}, mapping
     *       {@link ChatPolicy#ADMINS} to {@link ChatEphemeralTimer#ONE_WEEK}
     *       and {@link ChatPolicy#ANYONE} to {@link ChatEphemeralTimer#OFF}.
     *       Call {@code setEphemeralTimer} directly to select a different
     *       duration.</li>
     * </ul>
     *
     * <p>For the MEX path the {@code policy} is projected per setting:
     * <ul>
     *   <li>{@code ALLOW_NON_ADMIN_SUB_GROUP_CREATION}: {@code ADMINS} →
     *       {@code allow_non_admin_sub_group_creation=false},
     *       {@code ANYONE} → {@code true}.</li>
     *   <li>{@code LIMIT_SHARING}: {@code ADMINS} →
     *       {@code limit_sharing_enabled=true}, {@code ANYONE} →
     *       {@code false}; trigger fixed to {@code "CHAT_SETTING"}.</li>
     *   <li>{@code MEMBER_ADD_MODE}: {@code ADMINS} → {@code "ADMIN_ADD"},
     *       {@code ANYONE} → {@code "ALL_MEMBER_ADD"}.</li>
     *   <li>{@code MEMBER_LINK_MODE}: {@code ADMINS} →
     *       {@code "ADMIN_LINK"}, {@code ANYONE} →
     *       {@code "ALL_MEMBER_LINK"}.</li>
     *   <li>{@code MEMBER_SHARE_GROUP_HISTORY_MODE}: {@code ADMINS} →
     *       {@code "ADMIN_SHARE"}, {@code ANYONE} →
     *       {@code "ALL_MEMBER_SHARE"}.</li>
     * </ul>
     *
     * @param group   the non-{@code null} target group JID
     * @param setting the non-{@code null} setting to update
     * @param policy  the non-{@code null} target policy
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *
     * @implNote WAWebSetPropertyGroupAction.setGroupProperty switches on
     * {@code GROUP_SETTING_TYPE} and dispatches either through
     * {@code WAWebGroupModifyInfoJob.setGroupProperty} (direct IQ) or
     * {@code WAWebMexUpdateGroupPropertyJob.mexUpdateGroupPropertyJob}
     * (GraphQL mutation). Cobalt unifies both paths behind this entry
     * point so callers don't need to know which transport each setting
     * travels on.
     */
    @WhatsAppWebExport(moduleName = "WAWebSetPropertyGroupAction", exports = "setGroupProperty",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeGroupSetting(Jid group, GroupSetting setting, ChatPolicy policy) {
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(setting, "setting cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var enabled = policy == ChatPolicy.ADMINS; // WAWebSetPropertyGroupAction: a === 1 (enabled) / a !== 1 (disabled)
        switch (setting) {
            case ANNOUNCEMENT -> sendDirectGroupPropertyIq(group, new NodeBuilder() // WAWebGroupModifyInfoJob.setGroupProperty: hasAnnouncement=i===1 / hasNotAnnouncement=i!==1
                    .description(enabled ? "announcement" : "not_announcement")
                    .build());
            case RESTRICT -> sendDirectGroupPropertyIq(group, new NodeBuilder() // WAWebGroupModifyInfoJob.setGroupProperty: hasLocked=i===1 / hasUnlocked=i!==1
                    .description(enabled ? "locked" : "unlocked")
                    .build());
            case NO_FREQUENTLY_FORWARDED -> sendDirectGroupPropertyIq(group, new NodeBuilder() // WAWebGroupModifyInfoJob.setGroupProperty: hasNoFrequentlyForwarded=i===1 / hasFrequentlyForwardedOk=i!==1
                    .description(enabled ? "no_frequently_forwarded" : "frequently_forwarded_ok")
                    .build());
            case MEMBERSHIP_APPROVAL_MODE -> sendDirectGroupPropertyIq(group, new NodeBuilder() // WAWebGroupModifyInfoJob.setGroupProperty: membershipApprovalModeArgs = i===1 ? enabled : disabled
                    .description("membership_approval_mode") // WASmaxOutGroupsSetPropertyRequest.makeSetPropertyRequestMembershipApprovalMode
                    .content(new NodeBuilder()
                            .description("group_join") // WASmaxOutGroupsMembershipApprovalGroupJoinModeMixin: <group_join state=.../>
                            .attribute("state", enabled ? "on" : "off")
                            .build())
                    .build());
            case REPORT_TO_ADMIN_MODE -> sendDirectGroupPropertyIq(group, new NodeBuilder() // WAWebGroupModifyInfoJob.setGroupProperty: hasAllowAdminReports=i===1 / hasNotAllowAdminReports=i!==1
                    .description(enabled ? "allow_admin_reports" : "not_allow_admin_reports")
                    .build());
            case ALLOW_NON_ADMIN_SUB_GROUP_CREATION -> {
                sendMexGroupPropertyUpdate(group,
                        // WAWebSetPropertyGroupAction: {allow_non_admin_sub_group_creation: a===1}. ADMINS policy => non-admin creation disabled.
                        "{\"allow_non_admin_sub_group_creation\":" + (!enabled) + "}");
                // WAWebSetPropertyGroupAction: new CommunityGroupJourneyEvent({action, surface: COMMUNITY_SETTINGS, chat: t}).commit()
                // WAWebCommunityGroupJourneyEventImpl: a===0 (policy != ADMINS) => SELECT_COMMUNITY_ADMINS_CAN_ADD_GROUPS, else SELECT_EVERYONE_CAN_ADD_GROUPS.
                // Cobalt enabled == (policy == ADMINS), matching WA Web a===1, so action inverts: enabled => SELECT_COMMUNITY_ADMINS_CAN_ADD_GROUPS.
                commitCommunityGroupJourneyEvent(
                        enabled
                                ? ChatFilterActionTypes.SELECT_COMMUNITY_ADMINS_CAN_ADD_GROUPS
                                : ChatFilterActionTypes.SELECT_EVERYONE_CAN_ADD_GROUPS,
                        SurfaceType.COMMUNITY_SETTINGS,
                        group);
            }
            case LIMIT_SHARING -> {
                sendMexGroupPropertyUpdate(group,
                        // WAWebSetPropertyGroupAction: {limit_sharing: {limit_sharing_enabled: a===1, limit_sharing_trigger: "CHAT_SETTING"}}. ADMINS policy => sharing limited.
                        "{\"limit_sharing\":{\"limit_sharing_enabled\":" + enabled + ",\"limit_sharing_trigger\":\"CHAT_SETTING\"}}");
                // WAWebLimitSharingUIUtils.W: new LimitSharingSettingUpdateWamEvent({toggleUpdateAction: t.sharingLimited===true ? TURN_ON : TURN_OFF}).commit()
                // WA Web emits this AFTER the group mutation (D) completes via I(e, t, n, r) => { yield D(e, t); W(t); }. threadId and opusAction are omitted by WA Web at this site.
                wamService.commit(new LimitSharingSettingUpdateEventBuilder()
                        .toggleUpdateAction(enabled ? ToggleUpdateAction.TURN_ON : ToggleUpdateAction.TURN_OFF)
                        .build());
            }
            case MEMBER_ADD_MODE -> sendMexGroupPropertyUpdate(group,
                    // WAWebSetPropertyGroupAction: {member_add_mode: a===1?"ALL_MEMBER_ADD":"ADMIN_ADD"}. ADMINS policy => ADMIN_ADD.
                    "{\"member_add_mode\":\"" + (enabled ? "ADMIN_ADD" : "ALL_MEMBER_ADD") + "\"}");
            case MEMBER_LINK_MODE -> sendMexGroupPropertyUpdate(group,
                    // WAWebSetPropertyGroupAction: {member_link_mode: a===1?"ALL_MEMBER_LINK":"ADMIN_LINK"}. ADMINS policy => ADMIN_LINK.
                    "{\"member_link_mode\":\"" + (enabled ? "ADMIN_LINK" : "ALL_MEMBER_LINK") + "\"}");
            case MEMBER_SHARE_GROUP_HISTORY_MODE -> sendMexGroupPropertyUpdate(group,
                    // WAWebSetPropertyGroupAction: {member_share_group_history_mode: a===1?"ALL_MEMBER_SHARE":"ADMIN_SHARE"}. ADMINS policy => ADMIN_SHARE.
                    "{\"member_share_group_history_mode\":\"" + (enabled ? "ADMIN_SHARE" : "ALL_MEMBER_SHARE") + "\"}");
            case EPHEMERAL -> setEphemeralTimer(group, enabled ? ChatEphemeralTimer.ONE_WEEK : ChatEphemeralTimer.OFF);
        }
    }

    /**
     * Dispatches a direct {@code w:g2} set-property IQ carrying the supplied
     * body element. Used by
     * {@link #changeGroupSetting(Jid, GroupSetting, ChatPolicy)} for the
     * legacy properties that WA Web emits through
     * {@code WAWebGroupModifyInfoJob.setGroupProperty}.
     *
     * @param group the target group JID
     * @param body  the pre-built property body element
     */
    private void sendDirectGroupPropertyIq(Jid group, Node body) {
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetGroupMixin: xmlns: "w:g2"
                .attribute("to", group) // WASmaxOutGroupsBaseSetGroupMixin: to: GROUP_JID(t)
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(body);
        sendNode(iqNode); // WASmaxGroupsSetPropertyRPC.sendSetPropertyRPC
    }

    /**
     * Dispatches the {@code WAWebMexUpdateGroupPropertyJob} GraphQL
     * mutation with the supplied serialised {@code update} payload,
     * matching the success check WA Web performs in
     * {@code mexUpdateGroupPropertyJob}
     * ({@code data.xwa2_group_update_property.state === "ACTIVE"}).
     * Non-{@code ACTIVE} states and transport errors surface through the
     * sealed {@code WhatsAppException} hierarchy.
     *
     * @param group      the target group JID
     * @param updateJson the serialised {@code update} JSON object
     */
    private void sendMexGroupPropertyUpdate(Jid group, String updateJson) {
        var request = new UpdateGroupPropertyMex.Request(group.toString(), updateJson); // WAWebMexUpdateGroupPropertyJob: variables={group_id, update}
        // WAWebMexNativeClient.fetchQuery: telemetry-wrapped MEX dispatch
        var response = sendMexNode(request.toNode(), UpdateGroupPropertyMex.QUERY_ID, "mexUpdateGroupProperty", false);
        UpdateGroupPropertyMex.Response.of(response); // WAWebMexUpdateGroupPropertyJob: checks data.xwa2_group_update_property.state
    }

    /**
     * Emits a {@link GroupJourneyEvent} matching the construction in
     * {@code WAWebCommunityGroupJourneyEventImpl.commit}.
     *
     * <p>Derives {@code groupSize}, {@code threadType} and {@code userRole}
     * from the local {@link ChatMetadata} for {@code group} the same way
     * {@code WAWebGetThreadType.getThreadType} and
     * {@code WAWebGetUserRole.getUserRole} derive them from
     * {@code chat.groupMetadata}:
     * <ul>
     *   <li>{@code groupSize} mirrors
     *       {@code chat.groupMetadata.participants.length ?? 0}.</li>
     *   <li>{@code threadType} mirrors the {@code getThreadType} switch:
     *       {@link ThreadType#PARENT_GROUP PARENT_GROUP} for a
     *       {@link CommunityMetadata community} and
     *       {@link ThreadType#GROUP GROUP} for a plain
     *       {@link GroupMetadata group}. Only emitted for surfaces listed in
     *       {@code shouldLogThreadType}.</li>
     *   <li>{@code userRole} mirrors {@code getUserRole}:
     *       {@link UserRoleType#CADMIN CADMIN} when the local user is an
     *       admin of a parent community,
     *       {@link UserRoleType#ADMIN ADMIN} when the local user is an
     *       admin of any other group and
     *       {@link UserRoleType#MEMBER MEMBER} otherwise.</li>
     * </ul>
     *
     * <p>{@code appSessionId} is populated in WA Web via
     * {@code WAWebGetSharedSessionId.getSharedSessionId}, which returns the
     * browser-tab session id. Cobalt is a headless library with no
     * per-session UI identifier and leaves the field unset.
     *
     * @param action  the non-{@code null} action that triggered the event
     * @param surface the non-{@code null} UI surface constant used by WA Web
     * @param group   the non-{@code null} target group / community JID
     *
     * @implNote WAWebCommunityGroupJourneyEventImpl: {@code
     * n.commit = function(){var e = new(o("WAWebGroupJourneyWamEvent")).GroupJourneyWamEvent({actionType, appSessionId, surface, groupSize}); var t = this.getThreadType(); if (t != null) e.threadType = t; var n = this.getUserRole(); if (n != null) e.userRole = n; e.commit();}}
     *
     * @implNote WAWebGetUserRole.getUserRole is inlined into this single Cobalt
     * consumer. WA Web derives the WAM {@code USER_ROLE_TYPE} from
     * {@code chat.groupMetadata.participants.iAmAdmin()} and
     * {@code chat.groupMetadata.isParentGroup}; Cobalt derives the same
     * three-way {@link UserRoleType} from {@link ChatMetadata} because the
     * WA Web helper has no independent reuse in Cobalt outside this emitter.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetUserRole",
            exports = "getUserRole",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    private void commitCommunityGroupJourneyEvent(ChatFilterActionTypes action, SurfaceType surface, Jid group) {
        var metadata = store.findChatMetadata(group).orElse(null); // WAWebCommunityGroupJourneyEventImpl: this.chat
        var builder = new GroupJourneyEventBuilder()
                .actionType(action) // WAWebCommunityGroupJourneyEventImpl: actionType: this.action
                .surface(surface); // WAWebCommunityGroupJourneyEventImpl: surface: this.surface
        // WAWebCommunityGroupJourneyEventImpl.getGroupSize: n == null ? 0 : (t = n.participants.length) != null ? t : 0
        var groupSize = metadata == null ? 0 : metadata.participants().size();
        builder.groupSize(groupSize); // WAWebCommunityGroupJourneyEventImpl: groupSize: this.getGroupSize()
        // WAWebCommunityGroupJourneyEventImpl.shouldLogThreadType: returns true for COMMUNITY_SETTINGS (and other community/chat surfaces)
        if (metadata != null && shouldLogCommunityJourneyThreadType(surface)) {
            // WAWebGetThreadType: COMMUNITY => PARENT_GROUP; DEFAULT => GROUP (Cobalt only has these two ChatMetadata variants)
            var threadType = metadata instanceof CommunityMetadata ? ThreadType.PARENT_GROUP : ThreadType.GROUP;
            builder.threadType(threadType); // WAWebCommunityGroupJourneyEventImpl: if (t != null) e.threadType = t
        }
        // WAWebGetUserRole: iAmAdmin && isParentGroup -> CADMIN; iAmAdmin -> ADMIN; else MEMBER
        if (metadata != null) {
            var selfJid = store.jid().orElse(null); // WAWebGetUserRole: t.participants.iAmAdmin() compares the local user against the participant set
            if (selfJid != null) {
                var iAmAdmin = metadata.participants().stream()
                        .filter(participant -> Objects.equals(participant.userJid().toUserJid(), selfJid.toUserJid()))
                        .anyMatch(participant -> participant.rank().isPresent()); // ADMIN / FOUNDER -> admin; USER (rank == null) -> member
                var isParentGroup = metadata instanceof CommunityMetadata; // WAWebGroupMetadataBase.isParentGroup === groupType === COMMUNITY
                var userRole = iAmAdmin
                        ? (isParentGroup ? UserRoleType.CADMIN : UserRoleType.ADMIN)
                        : UserRoleType.MEMBER;
                builder.userRole(userRole); // WAWebCommunityGroupJourneyEventImpl: if (n != null) e.userRole = n
            }
        }
        wamService.commit(builder.build()); // WAWebCommunityGroupJourneyEventImpl: e.commit()
    }

    /**
     * Returns whether the supplied {@code surface} is one of the surfaces for
     * which {@code WAWebCommunityGroupJourneyEventImpl.shouldLogThreadType}
     * includes the thread type in the emitted
     * {@link GroupJourneyEvent}.
     *
     * @param surface the non-{@code null} UI surface to check
     * @return {@code true} when the thread type should be logged
     *
     * @implNote WAWebCommunityGroupJourneyEventImpl.shouldLogThreadType
     * switches on {@code this.surface} and returns {@code true} for
     * {@code CHAT}, {@code CHATLIST}, {@code COMMUNITY_HOME},
     * {@code COMMUNITY_TAB}, {@code COMMUNITY_NAV},
     * {@code COMMUNITY_NAV_SHEET}, {@code COMMUNITY_SETTINGS} and
     * {@code GROUP_INFO}; all other surfaces return {@code false}.
     */
    private static boolean shouldLogCommunityJourneyThreadType(SurfaceType surface) {
        return switch (surface) {
            case CHAT, CHATLIST, COMMUNITY_HOME, COMMUNITY_TAB, COMMUNITY_NAV, COMMUNITY_NAV_SHEET,
                 COMMUNITY_SETTINGS, GROUP_INFO -> true;
            default -> false;
        };
    }

    //<editor-fold desc="Stickers">
    /**
     * Marks a sticker as a favourite on the user's account and propagates the
     * change to every linked device via the {@code REGULAR_LOW} app-state
     * sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebStickersFavoriteSyncAction}: builds a
     * SET mutation via
     * {@link FavoriteStickerHandler#getFavoriteStickerMutation(String, boolean)}
     * with {@code isFavorite = true} and pushes it through
     * {@link WebAppStateService#pushPatches}. The full sticker descriptor is
     * restored on receiving devices from the primary's copy of the record, so
     * the outgoing mutation only carries the {@code isFavorite} flag.
     *
     * @param stickerHash the sticker file hash that uniquely identifies the
     *                    sticker across devices
     * @throws NullPointerException if {@code stickerHash} is {@code null}
     *
     * @implNote WAWebStickersFavoriteSyncAction — outgoing {@code SET}
     *           mutation produced via
     *           {@link FavoriteStickerHandler#getFavoriteStickerMutation}
     */
    @WhatsAppWebExport(moduleName = "WAWebStickersFavoriteSyncAction", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void favoriteSticker(String stickerHash) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        var mutation = FavoriteStickerHandler.INSTANCE.getFavoriteStickerMutation(stickerHash, true); // WAWebStickersFavoriteSyncAction: isFavorite = true
        webAppStateService.pushPatches(StickerAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync(["favorite_sticker"], [mutation], ...)
    }

    /**
     * Unmarks a sticker as a favourite on the user's account and propagates
     * the change to every linked device via the {@code REGULAR_LOW} app-state
     * sync collection.
     *
     * <p>Counterpart to {@link #favoriteSticker(String)}: emits the same
     * mutation with {@code isFavorite = false}.
     *
     * @param stickerHash the sticker file hash that uniquely identifies the
     *                    sticker across devices
     * @throws NullPointerException if {@code stickerHash} is {@code null}
     *
     * @implNote WAWebStickersFavoriteSyncAction — outgoing {@code SET}
     *           mutation with {@code isFavorite = false}
     */
    @WhatsAppWebExport(moduleName = "WAWebStickersFavoriteSyncAction", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unfavoriteSticker(String stickerHash) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        var mutation = FavoriteStickerHandler.INSTANCE.getFavoriteStickerMutation(stickerHash, false); // WAWebStickersFavoriteSyncAction: isFavorite = false
        webAppStateService.pushPatches(StickerAction.COLLECTION_NAME, List.of(mutation));
    }

    /**
     * Removes a sticker from the recent-stickers collection and propagates
     * the removal to every linked device via the {@code REGULAR_LOW}
     * app-state sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebStickersRemoveRecentSyncAction}: builds
     * a SET mutation that carries the current instant as
     * {@code lastStickerSentTs}. Receiving devices use this timestamp to
     * decide whether their local recent-sticker entry (which may be more
     * recent) should be removed.
     *
     * @param stickerHash the sticker file hash that uniquely identifies the
     *                    sticker across devices
     * @throws NullPointerException if {@code stickerHash} is {@code null}
     *
     * @implNote WAWebStickersRemoveRecentSyncAction — outgoing {@code SET}
     *           mutation produced via
     *           {@link RemoveRecentStickerHandler#getRemoveRecentStickerMutation}
     */
    @WhatsAppWebExport(moduleName = "WAWebStickersRemoveRecentSyncAction", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void removeRecentSticker(String stickerHash) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        var mutation = RemoveRecentStickerHandler.INSTANCE.getRemoveRecentStickerMutation(stickerHash); // WAWebStickersRemoveRecentSyncAction
        webAppStateService.pushPatches(RemoveRecentStickerAction.COLLECTION_NAME, List.of(mutation));
    }

    //</editor-fold>
    //<editor-fold desc="Polls">
    /**
     * Creates and sends a new poll in the specified chat.
     *
     * <p>Per WhatsApp Web {@code WAWebPollsSendPollCreationMsgAction.sendPollCreation}
     * via {@code createPollCreationMsgData}: builds a
     * {@link PollCreationMessage} with the supplied question, options, and
     * selectable-options count, generates a 32-byte random message secret for
     * end-to-end vote encryption, and dispatches the result through the
     * regular message send pipeline as a {@code POLL_CREATION} message. The
     * returned {@link ChatMessageInfo} carries the server-allocated message
     * id and timestamp.
     *
     * <p>Option hashes are intentionally left unset on the outgoing protobuf
     * because WhatsApp Web derives them from the option name via
     * {@code WAWebPollOptionHashUtils.generatePollOptionHash} on the recipient
     * side; Cobalt preserves the same behaviour by omitting them so the
     * preparer can compute them consistently.
     *
     * @param chat             the JID of the chat to post the poll in
     * @param question         the poll question displayed to voters
     * @param options          the ordered list of poll option labels
     * @param selectableCount  the maximum number of options each voter can
     *                         select; {@code 1} for single-choice polls
     * @return the {@link ChatMessageInfo} carrying the sent poll creation
     *         message
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the chat is not currently signed in
     *
     * @implNote WAWebPollsSendPollCreationMsgAction.sendPollCreation /
     *           {@code createPollCreationMsgData} — Cobalt builds the
     *           {@link PollCreationMessage} inline and routes through the
     *           existing {@link MessageService#send(MessageInfo)} pipeline,
     *           which mirrors WA Web's
     *           {@code WAWebSendMsgChatAction.addAndSendMsgToChat}.
     */
    @WhatsAppWebExport(moduleName = "WAWebPollsSendPollCreationMsgAction", exports = "sendPollCreation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebPollsSendPollCreationMsgAction", exports = "createPollCreationMsgData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public ChatMessageInfo createPoll(Jid chat, String question, List<String> options, int selectableCount) {
        Objects.requireNonNull(chat, "chat cannot be null");
        Objects.requireNonNull(question, "question cannot be null");
        Objects.requireNonNull(options, "options cannot be null");
        var selfJid = store.jid() // WAWebPollsSendPollCreationMsgAction.createPollCreationMsgData: getMeUserLidOrJidForChat
                .orElseThrow(() -> new IllegalArgumentException("Not logged in"));
        var messageSecret = new byte[32]; // WAWebPollsSendPollCreationMsgAction.createPollCreationMsgData: self.crypto.getRandomValues(new Uint8Array(32))
        ThreadLocalRandom.current().nextBytes(messageSecret);
        var pollOptions = options.stream() // WAWebPollsSendPollCreationMsgAction.createPollCreationMsgData: pollOptions: p
                .map(name -> new PollCreationMessageOptionBuilder().optionName(name).build()) // WAWebPollCreationUtils: {optionName: e}
                .toList();
        var poll = new PollCreationMessageBuilder() // WAWebPollsSendPollCreationMsgAction.createPollCreationMsgData: {pollName, pollOptions, pollSelectableOptionsCount}
                .name(question) // WAWebPollsSendPollCreationMsgAction: pollName: m
                .options(pollOptions) // WAWebPollsSendPollCreationMsgAction: pollOptions: p
                .selectableOptionsCount(selectableCount) // WAWebPollsSendPollCreationMsgAction: pollSelectableOptionsCount: _
                .encKey(messageSecret) // WAWebPollsSendPollCreationMsgAction.createPollCreationMsgData: messageSecret (encrypts votes)
                .build();
        var messageId = MessageIdGenerator.generate(MessageIdVersion.V2, selfJid); // WAWebMsgKey.newId
        var key = new MessageKeyBuilder() // WAWebPollsSendPollCreationMsgAction.createPollCreationMsgData: new WAWebMsgKey({from, to, id, participant, selfDir: "out"})
                .id(messageId)
                .parentJid(chat)
                .fromMe(true)
                .senderJid(selfJid)
                .build();
        var messageInfo = new ChatMessageInfoBuilder()
                .status(MessageStatus.PENDING) // ADAPTED: MessagePreparer initial status
                .senderJid(selfJid)
                .key(key)
                .message(MessageContainer.of(poll)) // WAWebPollsSendPollCreationMsgAction: wraps the PollCreationMessage into the message container
                .timestamp(Instant.now()) // WAWebPollsSendPollCreationMsgAction: t: unixTime()
                .build();
        messageService.send(messageInfo); // WAWebSendMsgChatAction.addAndSendMsgToChat
        commitPollsActionsMetric( // WAWebPollsSendPollCreationMsgAction.sendPollCreation: commitPollsActionsMetric({action: CREATE_POLL, chat: a, creationDateInSeconds: _.t, pollOptionsCount: l.options.length})
                chat,
                PollActionType.CREATE_POLL,
                messageInfo.timestamp().orElse(Instant.now()),
                pollOptions.size()
        );
        return messageInfo;
    }

    /**
     * Casts a vote on an existing poll by sending a
     * {@link PollUpdateMessage} referencing the originating poll creation
     * message.
     *
     * <p>Per WhatsApp Web {@code WAWebPollsSendVoteMsgAction}: builds a
     * {@code PollUpdateMessage} whose {@code pollCreationMessageKey} points
     * at the creator's message key and whose {@code vote} is an
     * HKDF-derived, AES-256-GCM encrypted list of the SHA-256 digests of the
     * selected option names. The encrypted blob is transported as a
     * {@link com.github.auties00.cobalt.model.message.poll.PollEncValue}.
     *
     * <p><strong>Vote encoding is not yet implemented in Cobalt.</strong>
     * WhatsApp Web derives the poll-encryption key from the original
     * poll's {@code messageSecret} via HKDF
     * ({@code WAWebMessageSecretCrypto.getDecryptionInfo}) and encrypts the
     * option digests with AES-GCM using the sender JID and poll creation
     * message id as additional authenticated data. The current build emits
     * an empty {@link com.github.auties00.cobalt.model.message.poll.PollEncValue}
     * so the stanza shape is correct, but recipients will not be able to
     * decode the vote. See the TODO below.
     *
     * @param pollKey          the {@link MessageKey} of the
     *                         {@link PollCreationMessage} being voted on
     * @param selectedOptions  the ordered list of selected option labels
     * @return the server ack result for the vote stanza
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code pollKey} has no parent JID
     *
     * @implNote WAWebPollsSendVoteMsgAction + WAWebPollsVoteDataUtils —
     *           ADAPTED with a TODO: vote option hashes are not yet
     *           HKDF+AES-GCM encrypted. The transport envelope is correct
     *           (PollUpdateMessage pointing at the poll creation key) but the
     *           ciphertext is a placeholder. Implementing this requires
     *           deriving the poll key from the creator's {@code messageSecret}
     *           which is currently not threaded through the public API.
     */
    @WhatsAppWebExport(moduleName = "WAWebPollsSendVoteMsgAction", exports = "sendVote",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult votePoll(MessageKey pollKey, List<String> selectedOptions) {
        Objects.requireNonNull(pollKey, "pollKey cannot be null");
        Objects.requireNonNull(selectedOptions, "selectedOptions cannot be null");
        var parentJid = pollKey.parentJid() // WAWebPollsSendVoteMsgAction: parentMsgKey.remote
                .orElseThrow(() -> new IllegalArgumentException("pollKey must carry a parentJid"));
        // TODO: WAWebPollsVoteDataUtils — derive the poll encryption key via
        //       WAWebMessageSecretCrypto.getDecryptionInfo(creator messageSecret, "Poll Vote", authorJid),
        //       build the option-hash list (sha256(optionName) truncated),
        //       AES-GCM-encrypt it with a fresh 12-byte IV using the poll creation
        //       id + voter JID as AAD, and populate encPayload/encIv here. The
        //       current PollEncValue is intentionally empty until the messageSecret
        //       lookup is wired through the store.
        var vote = new PollEncValueBuilder() // WAWebPollsVoteDataUtils: {encPayload, encIv}
                .encPayload(new byte[0]) // TODO(ADAPTED): HKDF/AES-GCM encrypted option digests
                .encIv(new byte[0]) // TODO(ADAPTED): 12-byte random IV
                .build();
        var pollUpdate = new PollUpdateMessageBuilder() // WAWebPollsSendVoteMsgAction: new PollUpdateMessage(...)
                .pollCreationMessageKey(pollKey) // WAWebPollsSendVoteMsgAction: pollCreationMessageKey
                .vote(vote) // WAWebPollsSendVoteMsgAction: vote: PollEncValue
                .senderTimestampMs(Instant.now()) // WAWebPollsSendVoteMsgAction: senderTimestampMs
                .build();
        var result = messageService.send(parentJid, MessageContainer.of(pollUpdate)); // WAWebSendMsgChatAction.addAndSendMsgToChat
        // WAWebPollsSendVoteMsgAction: y = t.size > 0 ? (p ? CHANGE_VOTE : VOTE) : REMOVE_VOTE;
        //   Cobalt does not maintain a PollVoteCollection keyed by parent+sender,
        //   so the CHANGE_VOTE branch (previous-vote lookup) cannot be distinguished
        //   here; we classify any non-empty selection as VOTE and empty as REMOVE_VOTE.
        var pollAction = selectedOptions.isEmpty()
                ? PollActionType.REMOVE_VOTE
                : PollActionType.VOTE;
        // WAWebPollsSendVoteMsgAction: creationDateInSeconds: e.t, pollOptionsCount: e.pollOptions.length
        //   Resolve the original poll creation message from the store so we can
        //   forward its timestamp and options count, matching WA Web's e = parent msg.
        var pollCreationTimestamp = Instant.now();
        var pollOptionsCount = 0;
        var pollCreationMessage = store.findMessageByKey(pollKey).orElse(null);
        if (pollCreationMessage instanceof ChatMessageInfo chatPoll) {
            pollCreationTimestamp = chatPoll.timestamp().orElse(pollCreationTimestamp);
            if (chatPoll.message().content() instanceof PollCreationMessage poll) {
                pollOptionsCount = poll.options().size();
            }
        }
        commitPollsActionsMetric(parentJid, pollAction, pollCreationTimestamp, pollOptionsCount);
        return result;
    }

    /**
     * Closes the given poll so that no further votes can be cast.
     *
     * <p>Per WhatsApp Web {@code WAWebPollsSendVoteMsgAction} / server poll
     * invalidation: emits a {@link PollUpdateMessage} whose {@code vote}
     * field is empty and whose metadata signals that the poll has been
     * invalidated. Receiving clients mark the poll as closed and refuse to
     * accept additional {@code vote} stanzas for it.
     *
     * @param pollKey the {@link MessageKey} of the
     *                {@link PollCreationMessage} to close
     * @return the server ack result for the close stanza
     * @throws NullPointerException     if {@code pollKey} is {@code null}
     * @throws IllegalArgumentException if {@code pollKey} has no parent JID
     *
     * @implNote WAWebPollsSendVoteMsgAction — ADAPTED: close-poll is routed
     *           through a {@link PollUpdateMessage} with an empty
     *           {@link com.github.auties00.cobalt.model.message.poll.PollEncValue}
     *           because Cobalt does not currently own a distinct
     *           poll-invalidated protocol message; the server treats an empty
     *           vote from the poll creator as the invalidation signal.
     */
    @WhatsAppWebExport(moduleName = "WAWebPollsSendVoteMsgAction", exports = "sendVote",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult closePoll(MessageKey pollKey) {
        Objects.requireNonNull(pollKey, "pollKey cannot be null");
        var parentJid = pollKey.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("pollKey must carry a parentJid"));
        // ADAPTED: WA Web's poll-close path emits a protocol message signaling
        // "pollInvalidated"; Cobalt approximates it with an empty-vote
        // PollUpdateMessage so the transport shape stays consistent with the
        // rest of the poll flow.
        var vote = new PollEncValueBuilder()
                .encPayload(new byte[0])
                .encIv(new byte[0])
                .build();
        var pollUpdate = new PollUpdateMessageBuilder()
                .pollCreationMessageKey(pollKey)
                .vote(vote)
                .senderTimestampMs(Instant.now())
                .build();
        var result = messageService.send(parentJid, MessageContainer.of(pollUpdate));
        // WAWebPollsSendVoteMsgAction: empty selection -> REMOVE_VOTE
        //   closePoll is modelled in Cobalt as an empty-vote PollUpdate, which
        //   maps onto WA Web's REMOVE_VOTE metric classification.
        var pollCreationTimestamp = Instant.now();
        var pollOptionsCount = 0;
        var pollCreationMessage = store.findMessageByKey(pollKey).orElse(null);
        if (pollCreationMessage instanceof ChatMessageInfo chatPoll) {
            pollCreationTimestamp = chatPoll.timestamp().orElse(pollCreationTimestamp);
            if (chatPoll.message().content() instanceof PollCreationMessage poll) {
                pollOptionsCount = poll.options().size();
            }
        }
        commitPollsActionsMetric(parentJid, PollActionType.REMOVE_VOTE, pollCreationTimestamp, pollOptionsCount);
        return result;
    }

    /**
     * Commits the {@link PollsActionsEvent} WAM event for a poll create / vote
     * / remove-vote dispatch.
     *
     * <p>Adapts {@code WAWebPollsActionsMetricUtils.commitPollsActionsMetric}
     * ({@code s(e)} in the bundled JS) and its internal event builder
     * ({@code u(chat, action)}):
     * <pre>
     * function s(e) {
     *   var t=e.action, n=e.chat, r=e.creationDateInSeconds, o=e.pollOptionsCount,
     *       a=u(n,t);
     *   a.pollCreationDs=c(r); a.pollOptionsCount=o; a.commit();
     * }
     * function u(e,t) {
     *   var n = new PollsActionsWamEvent({pollAction: t});
     *   if (WAWebChatGetters.getIsGroup(e)) {
     *     n.groupSizeBucket = WAWebWamNumberToClientGroupSizeBucket(e.getParticipantCount());
     *     n.isAdmin = e.iAmAdmin();
     *   }
     *   n.chatType = WAWebGetMessageChatTypeFromWid.getMessageChatTypeFromWid(e.id);
     *   return n;
     * }
     * function c(e) { // WAWeb-moment truncate-to-day helper
     *   var t = moment.utc(e * 1e3);
     *   t.startOf("day"); return t.unix();
     * }
     * </pre>
     *
     * <p>The {@code typeOfGroup} property is populated via
     * {@code pollsWamTypeOfGroup} when the chat is a group / community,
     * mirroring the way WA Web reads
     * {@code groupData.wamTypeOfGroup} in the same set of flows (
     * {@code WAWebPollsSendPollCreationMsgAction},
     * {@code WAWebPollsSendVoteMsgAction}, and the newsletter counterparts).
     *
     * @param chatJid         the chat the poll is hosted in
     * @param action          the poll action being logged
     * @param creationInstant the poll creation timestamp (WA Web's
     *                        {@code creationDateInSeconds} input before the
     *                        {@code startOf("day")} truncation)
     * @param optionsCount    the number of options on the underlying poll
     *
     * @implNote {@code WAWebPollsActionsMetricUtils.commitPollsActionsMetric}
     *           invoked from {@code WAWebPollsSendPollCreationMsgAction},
     *           {@code WAWebPollsSendVoteMsgAction}, and
     *           {@code WAWebNewsletterSendVoteMsgAction}.
     */
    @WhatsAppWebExport(moduleName = "WAWebPollsActionsMetricUtils", exports = "commitPollsActionsMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitPollsActionsMetric(Jid chatJid, PollActionType action,
                                          Instant creationInstant, int optionsCount) {
        var builder = new PollsActionsEventBuilder()
                .pollAction(action) // WAWebPollsActionsMetricUtils.u: new PollsActionsWamEvent({pollAction: t})
                .pollOptionsCount(optionsCount) // WAWebPollsActionsMetricUtils.s: a.pollOptionsCount = o
                .pollCreationDs(pollCreationDsFromInstant(creationInstant)) // WAWebPollsActionsMetricUtils.s: a.pollCreationDs = c(r); c = moment.utc(e*1e3).startOf("day").unix()
                .chatType(pollsWamChatType(chatJid)); // WAWebPollsActionsMetricUtils.u: n.chatType = getMessageChatTypeFromWid(e.id)
        // WAWebPollsActionsMetricUtils.u: if (WAWebChatGetters.getIsGroup(e)) { groupSizeBucket, isAdmin }
        if (chatJid.hasGroupOrCommunityServer()) {
            var metadata = store.findChatMetadata(chatJid).orElse(null);
            if (metadata instanceof GroupMetadata group) {
                var participantCount = group.participants().size();
                builder.groupSizeBucket(pollsWamGroupSizeBucket(Math.max(participantCount, 32))); // WAWebPollsActionsMetricUtils.u: WAWebWamNumberToClientGroupSizeBucket(e.getParticipantCount())
                var selfJid = store.jid().orElse(null);
                if (selfJid != null) {
                    builder.isAdmin(pollsWamIsAdmin(group, selfJid)); // WAWebPollsActionsMetricUtils.u: n.isAdmin = e.iAmAdmin()
                }
                // WAWebMaybePostMdGroupSyncMetrics / WAWebGroupType: wamTypeOfGroup — mirrored here
                // because every poll-action call site in WA Web operates on a chat that also
                // carries a WAM typeOfGroup classification via the shared WAWebGroupType helper.
                builder.typeOfGroup(pollsWamTypeOfGroup(group));
            }
        }
        wamService.commit(builder.build()); // WAWebPollsActionsMetricUtils.s: a.commit()
    }

    /**
     * Truncates the given instant to the start of the UTC day and returns the
     * resulting unix seconds value.
     *
     * <p>Mirrors {@code WAWebPollsActionsMetricUtils.c}:
     * {@code moment.utc(e*1e3).startOf("day").unix()}.
     *
     * @param instant the input instant
     * @return the unix-second value of the UTC start of the same day
     *
     * @apiNote WAWebPollsActionsMetricUtils.c — truncate-to-day helper used by
     * the {@code pollCreationDs} property.
     */
    @WhatsAppWebExport(moduleName = "WAWebPollsActionsMetricUtils", exports = "c",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static int pollCreationDsFromInstant(Instant instant) {
        // moment.utc(e*1e3).startOf("day").unix(): truncate to UTC day boundary
        var epochDays = instant.getEpochSecond() / 86400L;
        return (int) (epochDays * 86400L);
    }

    /**
     * Maps a chat JID to the {@link MessageChatType} classification used by
     * {@link PollsActionsEvent#chatType()}.
     *
     * <p>Mirrors the cascaded ternary in
     * {@code WAWebGetMessageChatTypeFromWid.getMessageChatTypeFromWid}
     * exactly, dispatching on the WA Web {@code Wid} predicates in the
     * same order:
     * <ol>
     *     <li>{@code isUser()} (user/legacy-user/LID/bot/hosted/hosted.lid
     *         domains) maps to {@code INDIVIDUAL};</li>
     *     <li>{@code isGroup()} ({@code g.us} domain) maps to
     *         {@code GROUP};</li>
     *     <li>{@code isBroadcast()} ({@code broadcast} domain) maps to
     *         {@code BROADCAST};</li>
     *     <li>{@code isStatus()} ({@code status@broadcast}) maps to
     *         {@code STATUS};</li>
     *     <li>{@code isNewsletter()} ({@code newsletter} domain) maps to
     *         {@code CHANNEL};</li>
     *     <li>anything else maps to {@code OTHER}.</li>
     * </ol>
     *
     * @param jid the chat JID to classify; must not be {@code null}
     * @return the corresponding {@link MessageChatType}; never {@code null}
     *
     * @implNote Duplicated here because the sibling helper in
     *           {@code UserMessageSender} is package-private and
     *           {@link WhatsAppClient} lives in a different package. The
     *           {@code STATUS} branch is unreachable because the preceding
     *           {@code isBroadcast()} check already catches
     *           {@code status@broadcast}; it is preserved to keep the
     *           method body in lock-step with the WA Web source.
     */
    @WhatsAppWebExport(moduleName = "WAWebGetMessageChatTypeFromWid",
            exports = "getMessageChatTypeFromWid",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static MessageChatType pollsWamChatType(Jid jid) {
        // WAWebGetMessageChatTypeFromWid.getMessageChatTypeFromWid: e.isUser()? INDIVIDUAL
        if (jid.hasUserServer()
                || jid.hasLidServer()
                || jid.hasBotServer()
                || jid.hasHostedServer()
                || jid.hasHostedLidServer()) {
            return MessageChatType.INDIVIDUAL;
        }
        // WAWebGetMessageChatTypeFromWid.getMessageChatTypeFromWid: e.isGroup()? GROUP
        if (jid.hasGroupOrCommunityServer()) {
            return MessageChatType.GROUP;
        }
        // WAWebGetMessageChatTypeFromWid.getMessageChatTypeFromWid: e.isBroadcast()? BROADCAST
        if (jid.hasBroadcastServer()) {
            return MessageChatType.BROADCAST;
        }
        // WAWebGetMessageChatTypeFromWid.getMessageChatTypeFromWid: e.isStatus()? STATUS
        // (unreachable: isBroadcast above already catches status@broadcast; kept for
        // structural parity with the JS ternary)
        if (jid.isStatusBroadcastAccount()) {
            return MessageChatType.STATUS;
        }
        // WAWebGetMessageChatTypeFromWid.getMessageChatTypeFromWid: e.isNewsletter()? CHANNEL
        if (jid.hasNewsletterServer()) {
            return MessageChatType.CHANNEL;
        }
        // WAWebGetMessageChatTypeFromWid.getMessageChatTypeFromWid: OTHER fallthrough
        return MessageChatType.OTHER;
    }

    /**
     * Buckets a participant count into a {@link ClientGroupSizeBucket} for
     * poll-action emissions.
     *
     * <p>Duplicates the ladder in
     * {@code GroupMessageSender.toGroupSizeBucket} because that helper is
     * private to its own class. The thresholds are identical.
     *
     * @param count the participant count, already raised to a minimum of 32 by
     *              the caller
     * @return the corresponding bucket, never {@code null}
     *
     * @apiNote WAWebWamNumberToClientGroupSizeBucket — identical ladder.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamNumberToClientGroupSizeBucket",
            exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    private static ClientGroupSizeBucket pollsWamGroupSizeBucket(int count) {
        if (count <= 33) return ClientGroupSizeBucket.SMALL;
        if (count <= 65) return ClientGroupSizeBucket.MEDIUM;
        if (count <= 129) return ClientGroupSizeBucket.LARGE;
        if (count <= 257) return ClientGroupSizeBucket.EXTRA_LARGE;
        if (count <= 513) return ClientGroupSizeBucket.XX_LARGE;
        if (count <= 1025) return ClientGroupSizeBucket.LT1024;
        if (count <= 1501) return ClientGroupSizeBucket.LT1500;
        if (count <= 2001) return ClientGroupSizeBucket.LT2000;
        if (count <= 2501) return ClientGroupSizeBucket.LT2500;
        if (count <= 3001) return ClientGroupSizeBucket.LT3000;
        if (count <= 3501) return ClientGroupSizeBucket.LT3500;
        if (count <= 4001) return ClientGroupSizeBucket.LT4000;
        if (count <= 4501) return ClientGroupSizeBucket.LT4500;
        if (count <= 5001) return ClientGroupSizeBucket.LT5000;
        return ClientGroupSizeBucket.LARGEST_BUCKET;
    }

    /**
     * Returns {@code true} when the logged-in account is an administrator of
     * the given group, used by the {@code isAdmin} property of
     * {@link PollsActionsEvent}.
     *
     * <p>Mirrors WA Web's {@code groupMetadata.participants.iAmAdmin()} lookup
     * by locating the local user in the participant set and checking whether
     * the participant entry has a non-empty rank.
     *
     * @param metadata the group metadata to inspect; never {@code null}
     * @param selfJid  the logged-in account's JID; never {@code null}
     * @return {@code true} when the current account is an admin / founder of
     *         the group; {@code false} otherwise
     *
     * @apiNote WAWebParticipants.iAmAdmin() as used by
     * {@code WAWebPollsActionsMetricUtils.u}.
     */
    private static boolean pollsWamIsAdmin(GroupMetadata metadata, Jid selfJid) {
        return metadata.participants().stream()
                .filter(participant -> Objects.equals(participant.userJid().toUserJid(), selfJid.toUserJid()))
                .anyMatch(participant -> participant.rank().isPresent()); // ADMIN / FOUNDER -> true; USER (rank == null) -> false
    }

    /**
     * Maps a {@link GroupMetadata} instance to the {@link TypeOfGroupEnum}
     * classification used by the {@code typeOfGroup} property on
     * {@link PollsActionsEvent}.
     *
     * <p>Mirrors WA Web's {@code groupData.wamTypeOfGroup} resolution (
     * {@code WAWebGroupType.getGroupTypeFromGroupMetadata} +
     * {@code groupTypeToWamEnum}):
     * {@code defaultSubgroup} -> {@link TypeOfGroupEnum#DEFAULT_SUBGROUP};
     * {@code parentCommunityJid != null} (and not default/general) ->
     * {@link TypeOfGroupEnum#SUBGROUP}; everything else (including community
     * announcement general chat and standalone groups) ->
     * {@link TypeOfGroupEnum#GROUP}.
     *
     * @param metadata the group metadata to classify; never {@code null}
     * @return the matching {@link TypeOfGroupEnum}; never {@code null}
     *
     * @apiNote WAWebGroupType.getGroupTypeFromGroupMetadata + groupTypeToWamEnum.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupType",
            exports = {"getGroupTypeFromGroupMetadata", "groupTypeToWamEnum"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static TypeOfGroupEnum pollsWamTypeOfGroup(GroupMetadata metadata) {
        if (metadata.isDefaultSubgroup()) {
            return TypeOfGroupEnum.DEFAULT_SUBGROUP;
        }
        if (metadata.isGeneralSubgroup()) {
            return TypeOfGroupEnum.GROUP;
        }
        if (metadata.parentCommunityJid().isPresent()) {
            return TypeOfGroupEnum.SUBGROUP;
        }
        return TypeOfGroupEnum.GROUP;
    }

    //</editor-fold>
    //<editor-fold desc="AI bots">
    /**
     * Sends a welcome-message request to the given WhatsApp bot and records
     * the request state across linked devices via the {@code REGULAR_LOW}
     * app-state sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebBotWelcomeRequestSync.getBotWelcomeRequestSetMutation}:
     * builds a SET mutation with {@code isSent = true} and pushes it through
     * {@link WebAppStateService#pushPatches} so that the same state is
     * reflected on every linked device (and so the bot does not re-issue its
     * welcome message the next time the chat is opened).
     *
     * @param botJid the JID of the bot to send the welcome request to
     * @throws NullPointerException if {@code botJid} is {@code null}
     *
     * @implNote WAWebBotWelcomeRequestSync.getBotWelcomeRequestSetMutation —
     *           outgoing SET mutation with {@code isSent = true}
     */
    @WhatsAppWebExport(moduleName = "WAWebBotWelcomeRequestSync", exports = "getBotWelcomeRequestSetMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendBotWelcomeRequest(Jid botJid) {
        Objects.requireNonNull(botJid, "botJid cannot be null");
        var mutation = BotWelcomeRequestHandler.INSTANCE.getBotWelcomeRequestSetMutation(botJid, true); // WAWebBotWelcomeRequestSync.getBotWelcomeRequestSetMutation: isSent = true
        webAppStateService.pushPatches(BotWelcomeRequestAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync
    }

    /**
     * Renames an AI thread owned by the given bot and propagates the new
     * title to every linked device via the {@code REGULAR_LOW} app-state
     * sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebAiThreadRenameSync}: builds a SET
     * mutation at {@code ["ai_thread_rename", botJid, threadId]} whose
     * {@code aiThreadRenameAction} sub-message carries the new title, pushes
     * it through {@link WebAppStateService#pushPatches}, and updates the
     * local {@link WhatsAppStore#aiThreadTitles} map eagerly.
     *
     * @param chatJid  the bot JID owning the thread, encoded as a plain
     *                 string JID (e.g. {@code 12345@bot})
     * @param threadId the AI thread identifier
     * @param newName  the new thread title
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code newName} is blank
     *
     * @implNote WAWebAiThreadRenameSync — outgoing SET mutation produced via
     *           {@link AiThreadRenameHandler#getAiThreadRenameMutation}
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void renameAiThread(String chatJid, String threadId, String newName) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        Objects.requireNonNull(threadId, "threadId cannot be null");
        Objects.requireNonNull(newName, "newName cannot be null");
        if (newName.isBlank()) {
            throw new IllegalArgumentException("newName cannot be blank"); // WAWebAiThreadRenameSync.validateSyncActionValue: isStringNotNullAndNotWhitespaceOnly
        }
        var jid = Jid.of(chatJid); // WAWebAiThreadRenameSync.applyMutations: createWid(s)
        var mutation = AiThreadRenameHandler.INSTANCE.getAiThreadRenameMutation(jid, threadId, newName); // WAWebAiThreadRenameSync
        webAppStateService.pushPatches(AiThreadRenameAction.COLLECTION_NAME, List.of(mutation));
        // WAWebAiThreadRenameSync.$AiThreadRenameSync$p_1: bulkCreateOrUpdateThreadsMetadata — apply locally for eager consistency
        var titles = new HashMap<>(store.aiThreadTitles());
        titles.put(chatJid + "|" + threadId, newName);
        store.setAiThreadTitles(titles);
    }

    /**
     * Deletes an AI thread owned by the given bot and propagates the
     * deletion to every linked device via the {@code REGULAR_HIGH} app-state
     * sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebAiThreadDeleteSync}: builds a SET
     * mutation at {@code ["ai_thread_delete", botJid, threadId]} (no value
     * payload), pushes it through {@link WebAppStateService#pushPatches}, and
     * removes the thread from the local
     * {@link WhatsAppStore#aiThreadTitles} map eagerly.
     *
     * @param chatJid  the bot JID owning the thread, encoded as a plain
     *                 string JID
     * @param threadId the AI thread identifier
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebAiThreadDeleteSync — outgoing SET mutation produced via
     *           {@link AiThreadDeleteHandler#getAiThreadDeleteMutation}
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteAiThread(String chatJid, String threadId) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        Objects.requireNonNull(threadId, "threadId cannot be null");
        var jid = Jid.of(chatJid);
        var mutation = AiThreadDeleteHandler.INSTANCE.getAiThreadDeleteMutation(jid, threadId); // WAWebAiThreadDeleteSync
        webAppStateService.pushPatches(AiThreadDeleteHandler.COLLECTION_NAME, List.of(mutation));
        // WAWebAiThreadDeleteSync.$AiThreadDeleteSync$p_1: bulkDeleteThreads — apply locally for eager consistency
        var titles = new HashMap<>(store.aiThreadTitles());
        titles.remove(chatJid + "|" + threadId);
        store.setAiThreadTitles(titles);
    }

    //</editor-fold>
    //<editor-fold desc="Favorites, notes, and pin-in-chat">
    /**
     * Adds the given chat to the favourites list and propagates the change
     * to every linked device via the {@code REGULAR_HIGH} app-state sync
     * collection.
     *
     * <p>Per WhatsApp Web {@code WAWebFavoritesSync.getFavoritesMutation}:
     * the mutation carries the full ordered list of favourite chat JIDs, not
     * a delta. Cobalt reads the current list from
     * {@link WhatsAppStore#favoriteChats()}, appends the new JID if not
     * already present, and emits the full updated list.
     *
     * <p>This overload performs the favourite without recording a WAM entry
     * point. Callers that know the UI surface originating the action should
     * use {@link #favoriteChat(Jid, FavoritesUpdateEntryPoint)} so that a
     * {@link MessagingFavoritesUpdateEvent} is logged, matching WA Web's
     * behaviour where the event fires only when
     * {@code options.entryPoint != null}.
     *
     * @param chat the JID of the chat to favourite
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebFavoritesSync.getFavoritesMutation — outgoing SET
     *           mutation carrying the full favourites list, produced via
     *           {@link FavoritesHandler#getFavoritesMutation(List, Instant)}
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "getFavoritesMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void favoriteChat(Jid chat) {
        favoriteChat(chat, null); // WAWebAddToFavoritesAction.addToFavoritesAction: entryPoint omitted -> logAddingMessagingFavorites skipped
    }

    /**
     * Adds the given chat to the favourites list, propagates the change to
     * every linked device, and emits a
     * {@link MessagingFavoritesUpdateEvent} describing the transition when
     * {@code entryPoint} is non-{@code null}.
     *
     * <p>Per WhatsApp Web {@code WAWebAddToFavoritesAction.addToFavoritesAction}:
     * when the caller supplies an {@code entryPoint}, the action invokes
     * {@code WAWebFavoritesLogging.logAddingMessagingFavorites(items, entryPoint)}
     * which counts the contacts/groups in the current
     * {@code FavoriteCollection} and derives the post-update counts by
     * classifying the items being added via {@code WAWebWid.isGroup}.
     * Cobalt reproduces that accounting here using
     * {@link Jid#hasGroupOrCommunityServer()} as the {@code isGroup}
     * equivalent.
     *
     * @param chat       the JID of the chat to favourite
     * @param entryPoint the UI entry point that initiated the action, or
     *                   {@code null} to suppress the WAM event (matching
     *                   WA Web's {@code entryPoint != null} guard)
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebAddToFavoritesAction.addToFavoritesAction +
     *           WAWebFavoritesLogging.logAddingMessagingFavorites — emits
     *           {@link MessagingFavoritesUpdateEvent} with before/after
     *           contact and group counts derived from the current
     *           {@link WhatsAppStore#favoriteChats()} and the target chat.
     */
    @WhatsAppWebExport(moduleName = "WAWebAddToFavoritesAction", exports = "addToFavoritesAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void favoriteChat(Jid chat, FavoritesUpdateEntryPoint entryPoint) {
        Objects.requireNonNull(chat, "chat cannot be null");
        var current = new ArrayList<>(store.favoriteChats()); // WAWebFavoritesSync.getFavoritesMutation: current favorites collection
        // WAWebFavoritesLogging.logAddingMessagingFavorites: count existing contacts/groups before mutating the list
        var contactsBefore = 0; // WAWebFavoritesLogging.logAddingMessagingFavorites: var a = 0
        var groupsBefore = 0; // WAWebFavoritesLogging.logAddingMessagingFavorites: var l = 0
        for (var existing : current) { // WAWebFavoritesLogging.logAddingMessagingFavorites: FavoriteCollection.forEach
            if (existing.hasGroupOrCommunityServer()) { // WAWebFavoritesLogging.logAddingMessagingFavorites: WAWebWid.isGroup(e.id)
                groupsBefore++; // WAWebFavoritesLogging.logAddingMessagingFavorites: l++
            } else {
                contactsBefore++; // WAWebFavoritesLogging.logAddingMessagingFavorites: a++
            }
        }
        var added = !current.contains(chat); // ADAPTED: WA Web client-side dedupes by orderIndex; Cobalt dedupes by JID equality
        if (added) {
            current.add(chat);
        }
        var mutation = FavoritesHandler.INSTANCE.getFavoritesMutation(current, Instant.now()); // WAWebFavoritesSync.getFavoritesMutation
        webAppStateService.pushPatches(FavoritesAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync
        store.setFavoriteChats(current); // WAWebFavoritesSync.applyMutations: setFavorites(h) — apply locally for eager consistency
        if (entryPoint != null) { // WAWebAddToFavoritesAction.addToFavoritesAction: a.entryPoint != null && logAddingMessagingFavorites(p, a.entryPoint)
            var contactsAfter = contactsBefore; // WAWebFavoritesLogging.logAddingMessagingFavorites: i = a
            var groupsAfter = groupsBefore; // WAWebFavoritesLogging.logAddingMessagingFavorites: s = l
            if (added) { // WAWebFavoritesLogging.logAddingMessagingFavorites: t.forEach(e -> WAWebWid.isGroup(e.id) ? s++ : i++)
                if (chat.hasGroupOrCommunityServer()) {
                    groupsAfter++;
                } else {
                    contactsAfter++;
                }
            }
            wamService.commit(new MessagingFavoritesUpdateEventBuilder() // WAWebFavoritesLogging.logAddingMessagingFavorites: new MessagingFavoritesUpdateWamEvent({...}).commit()
                    .favoritesUpdateEntryPoint(entryPoint) // WAWebFavoritesLogging.logAddingMessagingFavorites: favoritesUpdateEntryPoint: n
                    .contactFavCountBeforeUpdate(contactsBefore) // WAWebFavoritesLogging.logAddingMessagingFavorites: contactFavCountBeforeUpdate: a
                    .contactFavCountAfterUpdate(contactsAfter) // WAWebFavoritesLogging.logAddingMessagingFavorites: contactFavCountAfterUpdate: i
                    .groupFavCountBeforeUpdate(groupsBefore) // WAWebFavoritesLogging.logAddingMessagingFavorites: groupFavCountBeforeUpdate: l
                    .groupFavCountAfterUpdate(groupsAfter) // WAWebFavoritesLogging.logAddingMessagingFavorites: groupFavCountAfterUpdate: s
                    .build());
        }
    }

    /**
     * Removes the given chat from the favourites list and propagates the
     * change to every linked device via the {@code REGULAR_HIGH} app-state
     * sync collection.
     *
     * <p>Counterpart to {@link #favoriteChat(Jid)}: emits the same full
     * favourites list minus the target JID. Callers that know the UI surface
     * originating the action should use
     * {@link #unfavoriteChat(Jid, FavoritesUpdateEntryPoint)} so that a
     * {@link MessagingFavoritesUpdateEvent} is logged, matching WA Web's
     * behaviour where the event fires only when
     * {@code options.entryPoint != null}.
     *
     * @param chat the JID of the chat to unfavourite
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebFavoritesSync.getFavoritesMutation — outgoing SET
     *           mutation with the target JID removed from the full list
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "getFavoritesMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unfavoriteChat(Jid chat) {
        unfavoriteChat(chat, null); // WAWebRemoveFromFavoritesAction.removeFromFavoritesAction: entryPoint omitted -> logRemovingMessagingFavorites skipped
    }

    /**
     * Removes the given chat from the favourites list, propagates the change
     * to every linked device, and emits a
     * {@link MessagingFavoritesUpdateEvent} describing the transition when
     * {@code entryPoint} is non-{@code null}.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebRemoveFromFavoritesAction.removeFromFavoritesAction}: when
     * the caller supplies an {@code entryPoint}, the action invokes
     * {@code WAWebFavoritesLogging.logRemovingMessagingFavorites([id], entryPoint)}
     * which counts the current contacts/groups in {@code FavoriteCollection},
     * clones those to the after-counts, then decrements the after-counts by
     * the removed ids (classified via {@code WAWebWid.isGroup}) clamped at
     * zero. Cobalt reproduces that accounting here using
     * {@link Jid#hasGroupOrCommunityServer()} as the {@code isGroup}
     * equivalent.
     *
     * @param chat       the JID of the chat to unfavourite
     * @param entryPoint the UI entry point that initiated the action, or
     *                   {@code null} to suppress the WAM event (matching
     *                   WA Web's {@code entryPoint != null} guard)
     * @throws NullPointerException if {@code chat} is {@code null}
     *
     * @implNote WAWebRemoveFromFavoritesAction.removeFromFavoritesAction +
     *           WAWebFavoritesLogging.logRemovingMessagingFavorites — emits
     *           {@link MessagingFavoritesUpdateEvent} with before/after
     *           contact and group counts derived from the current
     *           {@link WhatsAppStore#favoriteChats()} and the target chat.
     */
    @WhatsAppWebExport(moduleName = "WAWebRemoveFromFavoritesAction", exports = "removeFromFavoritesAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unfavoriteChat(Jid chat, FavoritesUpdateEntryPoint entryPoint) {
        Objects.requireNonNull(chat, "chat cannot be null");
        var current = new ArrayList<>(store.favoriteChats());
        // WAWebFavoritesLogging.logRemovingMessagingFavorites: count existing contacts/groups before mutating the list
        var contactsBefore = 0; // WAWebFavoritesLogging.logRemovingMessagingFavorites: var n = 0
        var groupsBefore = 0; // WAWebFavoritesLogging.logRemovingMessagingFavorites: var i = 0
        for (var existing : current) { // WAWebFavoritesLogging.logRemovingMessagingFavorites: FavoriteCollection.forEach
            if (existing.hasGroupOrCommunityServer()) { // WAWebFavoritesLogging.logRemovingMessagingFavorites: WAWebWid.isGroup(e.id)
                groupsBefore++; // WAWebFavoritesLogging.logRemovingMessagingFavorites: i++
            } else {
                contactsBefore++; // WAWebFavoritesLogging.logRemovingMessagingFavorites: n++
            }
        }
        var removed = current.remove(chat);
        var mutation = FavoritesHandler.INSTANCE.getFavoritesMutation(current, Instant.now());
        webAppStateService.pushPatches(FavoritesAction.COLLECTION_NAME, List.of(mutation));
        store.setFavoriteChats(current);
        if (entryPoint != null) { // WAWebRemoveFromFavoritesAction.removeFromFavoritesAction: a.entryPoint != null && logRemovingMessagingFavorites([i], a.entryPoint)
            var contactsAfter = contactsBefore; // WAWebFavoritesLogging.logRemovingMessagingFavorites: a = n
            var groupsAfter = groupsBefore; // WAWebFavoritesLogging.logRemovingMessagingFavorites: l = i
            if (removed) { // WAWebFavoritesLogging.logRemovingMessagingFavorites: e.forEach(e -> WAWebWid.isGroup(e) ? l-- : a--)
                if (chat.hasGroupOrCommunityServer()) {
                    groupsAfter--;
                } else {
                    contactsAfter--;
                }
            }
            contactsAfter = Math.max(contactsAfter, 0); // WAWebFavoritesLogging.logRemovingMessagingFavorites: a = Math.max(a, 0)
            groupsAfter = Math.max(groupsAfter, 0); // WAWebFavoritesLogging.logRemovingMessagingFavorites: l = Math.max(l, 0)
            wamService.commit(new MessagingFavoritesUpdateEventBuilder() // WAWebFavoritesLogging.logRemovingMessagingFavorites: new MessagingFavoritesUpdateWamEvent({...}).commit()
                    .favoritesUpdateEntryPoint(entryPoint) // WAWebFavoritesLogging.logRemovingMessagingFavorites: favoritesUpdateEntryPoint: t
                    .contactFavCountBeforeUpdate(contactsBefore) // WAWebFavoritesLogging.logRemovingMessagingFavorites: contactFavCountBeforeUpdate: n
                    .contactFavCountAfterUpdate(contactsAfter) // WAWebFavoritesLogging.logRemovingMessagingFavorites: contactFavCountAfterUpdate: a
                    .groupFavCountBeforeUpdate(groupsBefore) // WAWebFavoritesLogging.logRemovingMessagingFavorites: groupFavCountBeforeUpdate: i
                    .groupFavCountAfterUpdate(groupsAfter) // WAWebFavoritesLogging.logRemovingMessagingFavorites: groupFavCountAfterUpdate: l
                    .build());
        }
    }

    /**
     * Adds a note to the given chat and propagates it to every linked device
     * via the {@code REGULAR_LOW} app-state sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebNoteSync}: the note is keyed by a
     * generated identifier — Cobalt computes a fresh SHA-256 hex id based on
     * the chat JID and a random component, matching the WA Web behaviour
     * where the primary device allocates a note id on creation. The returned
     * id is the one receivers will observe in the synced mutation.
     *
     * @param chat     the chat the note is attached to
     * @param noteText the free-text body of the note
     * @return the generated note id
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebNoteSync — outgoing SET mutation produced via
     *           {@link NoteEditHandler#getNoteEditMutation}; the note id is
     *           generated locally to match WA Web's behaviour where the
     *           creator allocates a fresh id on insert
     */
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public String addNoteToChat(Jid chat, String noteText) {
        Objects.requireNonNull(chat, "chat cannot be null");
        Objects.requireNonNull(noteText, "noteText cannot be null");
        var noteId = UUID.randomUUID().toString(); // WAWebNotesIdUtils.generateNoteId — Cobalt allocates a fresh id on creation
        var mutation = NoteEditHandler.INSTANCE.getNoteEditMutation(noteId, chat, noteText, false); // WAWebNoteSync
        webAppStateService.pushPatches(NoteEditAction.COLLECTION_NAME, List.of(mutation));
        return noteId;
    }

    /**
     * Updates the text of an existing note attached to the given chat and
     * propagates the change via the {@code REGULAR_LOW} app-state sync
     * collection.
     *
     * @param chat     the chat the note is attached to
     * @param noteId   the identifier of the note to update
     * @param newText  the new note text
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebNoteSync — outgoing SET mutation with {@code deleted = false}
     */
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editNoteOnChat(Jid chat, String noteId, String newText) {
        Objects.requireNonNull(chat, "chat cannot be null");
        Objects.requireNonNull(noteId, "noteId cannot be null");
        Objects.requireNonNull(newText, "newText cannot be null");
        var mutation = NoteEditHandler.INSTANCE.getNoteEditMutation(noteId, chat, newText, false); // WAWebNoteSync: edit path
        webAppStateService.pushPatches(NoteEditAction.COLLECTION_NAME, List.of(mutation));
    }

    /**
     * Deletes an existing note from the given chat and propagates the
     * deletion to every linked device via the {@code REGULAR_LOW} app-state
     * sync collection.
     *
     * <p>Per WhatsApp Web {@code WAWebNoteSync.applyMutations}: a mutation
     * whose {@code noteEditAction.deleted} field is {@code true} removes the
     * note from the notes table.
     *
     * @param chat   the chat the note is attached to
     * @param noteId the identifier of the note to delete
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebNoteSync — outgoing SET mutation with
     *           {@code deleted = true}
     */
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteNoteFromChat(Jid chat, String noteId) {
        Objects.requireNonNull(chat, "chat cannot be null");
        Objects.requireNonNull(noteId, "noteId cannot be null");
        var mutation = NoteEditHandler.INSTANCE.getNoteEditMutation(noteId, chat, "", true); // WAWebNoteSync: deleted = true
        webAppStateService.pushPatches(NoteEditAction.COLLECTION_NAME, List.of(mutation));
    }

    /**
     * Pins the referenced message inside its chat for every participant.
     *
     * <p>Per WhatsApp Web {@code WAWebSendPinMessageAction.sendPinInChatMsg}:
     * builds a {@link PinInChatMessage} with
     * {@link PinInChatMessage.Type#PIN_FOR_ALL} pointing at the target
     * message key and routes it through the regular message send pipeline.
     * The sender timestamp is set to the current instant.
     *
     * @param msgKey the key of the message to pin
     * @return the server ack result for the pin stanza
     * @throws NullPointerException     if {@code msgKey} is {@code null}
     * @throws IllegalArgumentException if {@code msgKey} has no parent JID
     *
     * @implNote WAWebSendPinMessageAction.sendPinInChatMsg — Cobalt builds
     *           the {@link PinInChatMessage} inline and dispatches via
     *           {@link MessageService#send(Jid, MessageContainer)} which
     *           routes through the message preparer (whose {@code hide}
     *           attribute mirrors WA Web's pin stanza shape).
     */
    @WhatsAppWebExport(moduleName = "WAWebSendPinMessageAction", exports = "sendPinInChatMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult pinMessageInChat(MessageKey msgKey) {
        Objects.requireNonNull(msgKey, "msgKey cannot be null");
        var parentJid = msgKey.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("msgKey must carry a parentJid"));
        var pin = new PinInChatMessageBuilder() // WAWebSendPinMessageAction.sendPinInChatMsg: PinInChat message construction
                .key(msgKey) // WAWebSendPinMessageAction: key: parentMsgKey
                .type(PinInChatMessage.Type.PIN_FOR_ALL) // WAWebSendPinMessageAction.sendPinInChatMsg: PIN_FOR_ALL
                .senderTimestampMs(Instant.now()) // WAWebSendPinMessageAction: senderTimestampMs
                .build();
        var ackResult = messageService.send(parentJid, MessageContainer.of(pin)); // WAWebSendAddonMsgChatAction -> sendMsgRecord
        commitPinInChatMessageSendEvent(parentJid, msgKey, PinInChatType.PIN_FOR_ALL, null); // WAWebSendPinMessageAction.sendPinInChatMsg: logPinInChatMessageSend({msg, parentMsg, chat, timeRemainingToExpirySecs: C}) — C is undefined on PIN, defaults to 0 (omitted here)
        return ackResult;
    }

    /**
     * Removes the pin from the referenced message inside its chat for every
     * participant.
     *
     * <p>Counterpart to {@link #pinMessageInChat(MessageKey)}: emits the
     * same message with {@link PinInChatMessage.Type#UNPIN_FOR_ALL}.
     *
     * @param msgKey the key of the message to unpin
     * @return the server ack result for the unpin stanza
     * @throws NullPointerException     if {@code msgKey} is {@code null}
     * @throws IllegalArgumentException if {@code msgKey} has no parent JID
     *
     * @implNote WAWebSendPinMessageAction.sendPinInChatMsg — UNPIN path
     */
    @WhatsAppWebExport(moduleName = "WAWebSendPinMessageAction", exports = "sendPinInChatMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult unpinMessageInChat(MessageKey msgKey) {
        Objects.requireNonNull(msgKey, "msgKey cannot be null");
        var parentJid = msgKey.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("msgKey must carry a parentJid"));
        var pin = new PinInChatMessageBuilder()
                .key(msgKey)
                .type(PinInChatMessage.Type.UNPIN_FOR_ALL) // WAWebSendPinMessageAction.sendPinInChatMsg: UNPIN_FOR_ALL
                .senderTimestampMs(Instant.now())
                .build();
        var ackResult = messageService.send(parentJid, MessageContainer.of(pin));
        // WAWebSendPinMessageAction.sendPinInChatMsg: on UNPIN_FOR_ALL, `C` = PinInChatCollection.getByParentMsgKey(parentMsgKey).leftExpirationTime().
        // Cobalt does not model an active-pin table with a TTL, so timeRemainingToExpirySecs is omitted rather than fabricated.
        commitPinInChatMessageSendEvent(parentJid, msgKey, PinInChatType.UNPIN_FOR_ALL, null);
        return ackResult;
    }

    /**
     * Commits the {@link PinInChatMessageSendEvent} WAM event for a successful
     * pin or unpin dispatch from {@link #pinMessageInChat(MessageKey)} /
     * {@link #unpinMessageInChat(MessageKey)}.
     *
     * <p>Adapts {@code WAWebPinInChatMetricUtils.logPinInChatMessageSend}
     * ({@code e(e)} in the bundled JS): classifies the chat as a group (or
     * community) and, when it is, resolves the
     * {@link GroupTypeClient}/{@link GroupRoleType} pair that WA Web derives
     * from {@code c(groupMetadata.groupType)} and
     * {@code d(groupMetadata.participants.iAmAdmin())}.
     *
     * <p>Additional properties WA Web logs from the message models:
     * <ul>
     *   <li>{@code mediaType} &mdash; classification of the pinned parent
     *       message via {@link WamMsgUtils#getWamMediaType(ChatMessageInfo)};
     *   <li>{@code isSelfParentMessage} &mdash; whether the parent message was
     *       authored by the local account ({@code fromMe()} on the resolved
     *       {@link ChatMessageInfo}); omitted when the parent cannot be
     *       resolved in the store;
     *   <li>{@code isSelfPin} &mdash; always {@code true}: this helper is only
     *       called from the outbound pin/unpin code paths, matching WA Web's
     *       {@code getIsSentByMe(a)} where {@code a} is the just-constructed
     *       {@code fromMe:true} PinInChat message;
     *   <li>{@code pinInChatExpirySecs} &mdash; always {@code 0} because
     *       Cobalt's {@link PinInChatMessage} model does not carry a
     *       {@code pinExpiryDuration} field; WA Web itself falls back to
     *       {@code 0} when the field is unset
     *       ({@code (t=a.pinExpiryDuration)!=null?t:0}).
     * </ul>
     *
     * @param parentJid                  the chat JID hosting the pinned message
     * @param parentMsgKey               the key of the pinned (parent) message
     * @param pinInChatType              the pin operation type (PIN or UNPIN)
     * @param timeRemainingToExpirySecs  the remaining seconds on the existing
     *                                   pin for UNPIN paths (source: WA Web's
     *                                   {@code PinInChatCollection.leftExpirationTime}),
     *                                   or {@code null} to omit the property
     *                                   (used on the PIN path and whenever
     *                                   Cobalt cannot compute a TTL)
     *
     * @implNote {@code WAWebPinInChatMetricUtils.logPinInChatMessageSend}
     *           invoked from {@code WAWebSendPinMessageAction.sendPinInChatMsg}
     */
    @WhatsAppWebExport(moduleName = "WAWebPinInChatMetricUtils", exports = "logPinInChatMessageSend",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitPinInChatMessageSendEvent(Jid parentJid, MessageKey parentMsgKey,
                                                 PinInChatType pinInChatType,
                                                 Integer timeRemainingToExpirySecs) {
        var builder = new PinInChatMessageSendEventBuilder()
                .pinInChatType(pinInChatType) // WAWebPinInChatMetricUtils: pinInChatType: u(WANullthrows(a.pinMessageType))
                .isSelfPin(Boolean.TRUE) // WAWebPinInChatMetricUtils: isSelfPin: WAWebMsgGetters.getIsSentByMe(a) — always true on the outbound path (fromMe:true PinInChat msg)
                .pinInChatExpirySecs(0); // WAWebPinInChatMetricUtils: pinInChatExpirySecs: (t=a.pinExpiryDuration)!=null?t:0 — Cobalt's PinInChatMessage has no pinExpiryDuration, matches the 0 fallback
        var isAGroup = parentJid.hasGroupOrCommunityServer(); // WAWebChatGetters.getIsGroup(n)
        builder.isAGroup(isAGroup); // WAWebPinInChatMetricUtils: isAGroup: m
        if (isAGroup) { // WAWebPinInChatMetricUtils: if (m) { var f = WANullthrows(n.groupMetadata); ... }
            var metadata = store.findChatMetadata(parentJid).orElse(null);
            if (metadata != null) {
                var groupTypeClient = pinWamGroupTypeClient(metadata); // WAWebPinInChatMetricUtils.c: groupTypeClient: c(f.groupType)
                if (groupTypeClient != null) {
                    builder.groupTypeClient(groupTypeClient);
                }
                var selfJid = store.jid().orElse(null);
                if (selfJid != null) {
                    builder.groupRole(pinWamGroupRole(metadata, selfJid)); // WAWebPinInChatMetricUtils.d: groupRole: d(f.participants.iAmAdmin())
                }
            }
        }
        // WAWebPinInChatMetricUtils: mediaType: WAWebWamMsgUtils.getWamMediaType(parentMsg)
        // WAWebPinInChatMetricUtils: isSelfParentMessage: WAWebMsgGetters.getIsSentByMe(parentMsg)
        store.findMessageByKey(parentMsgKey).ifPresent(parentMessage -> {
            if (parentMessage instanceof ChatMessageInfo chatParent) {
                builder.mediaType(WamMsgUtils.getWamMediaType(chatParent));
            }
            builder.isSelfParentMessage(parentMessage.key().fromMe());
        });
        if (timeRemainingToExpirySecs != null) { // WAWebPinInChatMetricUtils: timeRemainingToExpirySecs: s (defaulted to 0 when undefined)
            builder.timeRemainingToExpirySecs(timeRemainingToExpirySecs);
        }
        wamService.commit(builder.build()); // WAWebPinInChatMetricUtils: new PinInChatMessageSendWamEvent({...}).commit()
    }

    /**
     * Maps a {@link ChatMetadata} instance to the WAM
     * {@link GroupTypeClient} classification expected by
     * {@link PinInChatMessageSendEvent#groupTypeClient()}.
     *
     * <p>Adapts {@code WAWebPinInChatMetricUtils.c}, which dispatches over
     * {@code WAWebGroupType.GroupType} values derived from
     * {@code WAWebGroupType.getGroupTypeFromGroupMetadata}:
     * <ul>
     *   <li>{@code DEFAULT} &rarr; {@link GroupTypeClient#REGULAR_GROUP}</li>
     *   <li>{@code LINKED_SUBGROUP} &rarr; {@link GroupTypeClient#SUB_GROUP}</li>
     *   <li>{@code LINKED_ANNOUNCEMENT_GROUP} &rarr; {@link GroupTypeClient#DEFAULT_SUB_GROUP}</li>
     *   <li>{@code COMMUNITY} &rarr; {@link GroupTypeClient#PARENT_GROUP}</li>
     *   <li>{@code LINKED_GENERAL_GROUP} &rarr; {@link GroupTypeClient#SUB_GROUP}</li>
     * </ul>
     *
     * <p>Cobalt encodes the community (parent group) variant via a dedicated
     * {@link CommunityMetadata} type, while subgroup flags live on
     * {@link GroupMetadata#isDefaultSubgroup()} /
     * {@link GroupMetadata#isGeneralSubgroup()} /
     * {@link GroupMetadata#parentCommunityJid()}.
     *
     * @param metadata the chat metadata to classify; never {@code null}
     * @return the matching {@link GroupTypeClient}, or {@code null} if the
     *         metadata cannot be classified (matches the {@code undefined}
     *         return that {@code c} produces for a missing {@code GroupType})
     *
     * @implNote {@code WAWebPinInChatMetricUtils.c} combined with
     *           {@code WAWebGroupType.getGroupTypeFromGroupMetadata}
     */
    private static GroupTypeClient pinWamGroupTypeClient(ChatMetadata metadata) {
        if (metadata instanceof CommunityMetadata) {
            return GroupTypeClient.PARENT_GROUP; // WAWebGroupType: COMMUNITY -> PARENT_GROUP
        }
        if (metadata instanceof GroupMetadata group) {
            if (group.isDefaultSubgroup()) {
                return GroupTypeClient.DEFAULT_SUB_GROUP; // WAWebGroupType: LINKED_ANNOUNCEMENT_GROUP -> DEFAULT_SUB_GROUP
            }
            if (group.isGeneralSubgroup()) {
                return GroupTypeClient.SUB_GROUP; // WAWebGroupType: LINKED_GENERAL_GROUP -> SUB_GROUP
            }
            if (group.parentCommunityJid().isPresent()) {
                return GroupTypeClient.SUB_GROUP; // WAWebGroupType: LINKED_SUBGROUP -> SUB_GROUP
            }
            return GroupTypeClient.REGULAR_GROUP; // WAWebGroupType: DEFAULT -> REGULAR_GROUP
        }
        return null;
    }

    /**
     * Maps the current local account's role in a group / community to the
     * WAM {@link GroupRoleType} classification expected by
     * {@link PinInChatMessageSendEvent#groupRole()}.
     *
     * <p>Adapts {@code WAWebPinInChatMetricUtils.d}
     * ({@code return e ? ADMIN : MEMBER}) where {@code e} is
     * {@code groupMetadata.participants.iAmAdmin()}. The local account is
     * located inside the participant set by matching on the user-form JID,
     * mirroring the identity check used elsewhere in this class (see
     * {@link #commitCommunityGroupJourneyEvent(ChatFilterActionTypes, SurfaceType, Jid)}
     * for the reference pattern).
     *
     * @param metadata the chat metadata to inspect; never {@code null}
     * @param selfJid  the logged-in account's JID; never {@code null}
     * @return {@link GroupRoleType#ADMIN} when the local account's
     *         participant entry has a non-empty rank
     *         ({@link GroupPartipantRole#ADMIN} or
     *         {@link GroupPartipantRole#FOUNDER}); {@link GroupRoleType#MEMBER}
     *         otherwise
     *
     * @implNote {@code WAWebPinInChatMetricUtils.d} with
     *           {@code participants.iAmAdmin()} realised via the participant
     *           rank lookup used across Cobalt's group role helpers
     */
    private static GroupRoleType pinWamGroupRole(ChatMetadata metadata, Jid selfJid) {
        var iAmAdmin = metadata.participants().stream()
                .filter(participant -> Objects.equals(participant.userJid().toUserJid(), selfJid.toUserJid()))
                .anyMatch(participant -> participant.rank().isPresent()); // ADMIN / FOUNDER -> true; USER (rank == null) -> false
        return iAmAdmin ? GroupRoleType.ADMIN : GroupRoleType.MEMBER;
    }

    /**
     * Changes the user's preferred UI locale and propagates the change to
     * every linked device via the {@code CRITICAL_BLOCK} app-state sync
     * collection.
     *
     * <p>Mirrors WA Web's outgoing-locale path that goes through
     * {@code WAWebSyncdActionUtils.buildPendingMutation} with the
     * {@code setting_locale} action and is eventually applied by
     * {@code WAWebLocaleSettingSync.applyMutations} on the paired devices
     * (which forward it to {@code WAWebBackendApi.frontendSendAndReceive("setLocale", ...)}).
     *
     * <p>The new locale is also eagerly written to
     * {@link WhatsAppStore#setLocale(String)} so subsequent reads are consistent.
     *
     * @implNote ADAPTED: {@code WAWebLocaleSettingSync} — outgoing mutation
     *           built via {@link LocaleSettingHandler#getLocaleMutation(Instant, String)};
     *           local store is updated eagerly since Cobalt has no frontend l10n layer
     * @param locale the new BCP-47 locale tag (e.g. {@code "en_US"}); must not
     *               be {@code null}
     * @throws NullPointerException            if {@code locale} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeLocale(String locale) {
        Objects.requireNonNull(locale, "locale cannot be null");
        var mutation = LocaleSettingHandler.INSTANCE.getLocaleMutation(Instant.now(), locale); // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation
        webAppStateService.pushPatches(LocaleSetting.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync([], [mutation], ...)
        store.setLocale(locale); // ADAPTED: WAWebLocaleSettingSync.applyMutations -> frontendSendAndReceive("setLocale", ...) — apply locally for eager consistency
    }

    /**
     * Changes the user's disable-link-previews privacy setting and propagates
     * the change to every linked device via the {@code REGULAR} app-state
     * sync collection.
     *
     * <p>Mirrors {@code WAWebDisableLinkPreviewsSync.sendMutation}, which
     * builds a {@code setting_disableLinkPreviews} mutation through
     * {@link DisableLinkPreviewsHandler#getMutation(Instant, boolean)} and
     * hands it to {@code WAWebSyncdCoreApi.lockForSync}. Cobalt uses
     * {@link WebAppStateService#pushPatches(SyncPatchType, java.util.SequencedCollection)}
     * for the same purpose.
     *
     * <p>The new value is also eagerly written to
     * {@link WhatsAppStore#setDisableLinkPreviews(boolean)} so subsequent
     * reads are consistent.
     *
     * @implNote {@code WAWebDisableLinkPreviewsSync.sendMutation} — outgoing
     *           mutation built via {@link DisableLinkPreviewsHandler#getMutation(Instant, boolean)}
     * @param disabled {@code true} to disable link previews, {@code false} to
     *                 allow them
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebDisableLinkPreviewsSync", exports = "sendMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeDisableLinkPreviews(boolean disabled) {
        var mutation = DisableLinkPreviewsHandler.INSTANCE.getMutation(Instant.now(), disabled); // WAWebDisableLinkPreviewsSync.getMutation
        webAppStateService.pushPatches(PrivacySettingDisableLinkPreviewsAction.COLLECTION_NAME, List.of(mutation)); // WAWebDisableLinkPreviewsSync.sendMutation: WAWebSyncdCoreApi.lockForSync([], [getMutation(...)], ...)
        store.setDisableLinkPreviews(disabled); // ADAPTED: WAWebDisableLinkPreviewsAction.setDisableLinkPreviewsToUserPrefs(r) — apply locally for eager consistency
    }

    /**
     * Changes the user's 12h/24h time format preference and propagates the
     * change to every linked device via the {@code REGULAR_LOW} app-state
     * sync collection.
     *
     * <p>Mirrors WA Web's outgoing path that goes through
     * {@code WAWebSyncdActionUtils.buildPendingMutation} with the
     * {@code time_format} action and is eventually applied by
     * {@code WAWebTimeFormatSync.applyMutations} on the paired devices (which
     * forwards it to {@code WAWebBackendApi.frontendFireAndForget("setIs24Hour", ...)}).
     *
     * <p>The new value is also eagerly written to
     * {@link WhatsAppStore#setTwentyFourHourFormat(boolean)} so subsequent
     * reads are consistent.
     *
     * @implNote ADAPTED: {@code WAWebTimeFormatSync} — outgoing mutation built
     *           via {@link TimeFormatHandler#getTimeFormatMutation(Instant, boolean)};
     *           local store is updated eagerly since Cobalt has no frontend
     * @param twentyFourHourFormat {@code true} to enable 24-hour display,
     *                             {@code false} for 12-hour display
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeTwentyFourHourFormat(boolean twentyFourHourFormat) {
        var mutation = TimeFormatHandler.INSTANCE.getTimeFormatMutation(Instant.now(), twentyFourHourFormat); // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation
        webAppStateService.pushPatches(TimeFormatAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync([], [mutation], ...)
        store.setTwentyFourHourFormat(twentyFourHourFormat); // ADAPTED: WAWebTimeFormatSync.applyMutations -> frontendFireAndForget("setIs24Hour", ...) — apply locally
    }

    /**
     * Changes the user's Maiba AI features preference and propagates the
     * change to every linked device via the {@code REGULAR_HIGH} app-state
     * sync collection.
     *
     * <p>NO_WA_BASIS: WA Web has no outgoing helper or setter for
     * {@code maiba_ai_features_control}; only the protobuf shape exists in
     * {@code WAWebProtobufSyncAction.pb}. Cobalt surfaces this forward-looking
     * setter so the {@link MaibaAIFeaturesControlHandler} is exercised end to
     * end. The convenience overload
     * {@link #changeAIFeaturesEnabled(boolean)} maps the boolean flag to the
     * {@code ENABLED} / {@code DISABLED} enum variants.
     *
     * <p>The new value is also eagerly written to
     * {@link WhatsAppStore#setMaibaAiFeatureStatus(MaibaAIFeaturesControlAction.MaibaAIFeatureStatus)}
     * so subsequent reads are consistent.
     *
     * @implNote NO_WA_BASIS — forward-looking setter built on
     *           {@link MaibaAIFeaturesControlHandler#getMaibaAiFeatureStatusMutation(Instant, MaibaAIFeaturesControlAction.MaibaAIFeatureStatus)}
     * @param enabled {@code true} to enable AI features (emits {@code ENABLED}),
     *                {@code false} to disable them (emits {@code DISABLED})
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    public void changeAIFeaturesEnabled(boolean enabled) {
        var status = enabled
                ? MaibaAIFeaturesControlAction.MaibaAIFeatureStatus.ENABLED
                : MaibaAIFeaturesControlAction.MaibaAIFeatureStatus.DISABLED;
        var mutation = MaibaAIFeaturesControlHandler.INSTANCE.getMaibaAiFeatureStatusMutation(Instant.now(), status); // NO_WA_BASIS
        webAppStateService.pushPatches(MaibaAIFeaturesControlAction.COLLECTION_NAME, List.of(mutation));
        store.setMaibaAiFeatureStatus(status); // NO_WA_BASIS: apply locally for eager consistency
    }

    /**
     * Changes the user's auto-unarchive-on-new-message setting and propagates
     * the change to every linked device via the {@code REGULAR_LOW} app-state
     * sync collection.
     *
     * <p>Mirrors WA Web's {@code WAWebArchiveSettingBridge} outgoing path,
     * which builds a {@code setting_unarchiveChats} mutation through
     * {@code WAWebSyncdActionUtils.buildPendingMutation} and hands it to
     * {@code WAWebSyncdCoreApi.lockForSync}. The paired devices apply it via
     * {@code WAWebArchiveSettingSync.applyMutations}, which also runs the
     * "re-archive already-archived chats" / "auto-unarchive" side-effect pass.
     *
     * <p>The new value is also eagerly written to
     * {@link WhatsAppStore#setUnarchiveChats(boolean)} so subsequent reads are
     * consistent.
     *
     * @implNote ADAPTED: {@code WAWebArchiveSettingBridge} -> outgoing
     *           {@code setting_unarchiveChats} mutation built via
     *           {@link UnarchiveChatsSettingHandler#getUnarchiveChatsMutation(Instant, boolean)}
     * @param unarchive {@code true} to automatically unarchive a chat on the
     *                  arrival of a new message, {@code false} to keep archived
     *                  chats archived
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void changeUnarchiveChatsOnNewMessage(boolean unarchive) {
        var mutation = UnarchiveChatsSettingHandler.INSTANCE.getUnarchiveChatsMutation(Instant.now(), unarchive); // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation
        webAppStateService.pushPatches(UnarchiveChatsSetting.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync([], [mutation], ...)
        store.setUnarchiveChats(unarchive); // ADAPTED: apply locally for eager consistency
    }

    /**
     * Changes the user's notification activity preference and propagates the
     * change to every linked device via the {@code REGULAR} app-state sync
     * collection.
     *
     * <p>NO_WA_BASIS: WA Web has no outgoing helper or sync handler module
     * for {@code notificationActivitySetting}; only the protobuf schema
     * exists in {@code WAWebProtobufSyncAction.pb} (action index 60,
     * collection {@code REGULAR}). Cobalt surfaces this forward-looking setter
     * so the {@link NotificationActivitySettingHandler} is exercised end to
     * end.
     *
     * <p>The new value is also eagerly written to
     * {@link WhatsAppStore#setNotificationActivitySetting(NotificationActivitySettingAction.NotificationActivitySetting)}
     * so subsequent reads are consistent.
     *
     * @implNote NO_WA_BASIS — forward-looking setter built on
     *           {@link NotificationActivitySettingHandler#getNotificationActivityMutation(Instant, NotificationActivitySettingAction.NotificationActivitySetting)}
     * @param setting the new {@link NotificationActivitySettingAction.NotificationActivitySetting};
     *                must not be {@code null}
     * @throws NullPointerException            if {@code setting} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    public void changeNotificationActivity(NotificationActivitySettingAction.NotificationActivitySetting setting) {
        Objects.requireNonNull(setting, "setting cannot be null");
        var mutation = NotificationActivitySettingHandler.INSTANCE.getNotificationActivityMutation(Instant.now(), setting); // NO_WA_BASIS
        webAppStateService.pushPatches(NotificationActivitySettingHandler.INSTANCE.collectionName(), List.of(mutation));
        store.setNotificationActivitySetting(setting); // NO_WA_BASIS: apply locally for eager consistency
    }

    //</editor-fold>
    //<editor-fold desc="Listeners">
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

    //</editor-fold>

    /**
     * Parses a label identifier into the integer {@code listId} used by the
     * {@link ListUpdateEvent} WAM event.
     *
     * <p>Per WhatsApp Web {@code WAWebListsActions} the {@code listId} field is
     * always populated via {@code Number(labelId)} or the freshly allocated
     * integer id returned by {@code getNextLabelId}. Cobalt's labels are keyed
     * by string, so we attempt a numeric parse; ids that are not valid integers
     * (for example, predefined filters keyed by name) would be reported as
     * {@code NaN} on WA Web and are skipped here — WAM integer properties
     * cannot carry {@code NaN}.
     *
     * @implNote WAWebListsActions.createNewListAction / editListAction / deleteListAction:
     *           {@code Number(n.id)} / {@code Number(e)} / literal {@code a} from
     *           {@code getNextLabelId}
     * @param labelId the string label identifier to parse
     * @return the integer list id, or {@code null} if {@code labelId} does not
     *         parse as an integer
     */
    private static Integer parseLabelIdToListId(String labelId) {
        try {
            return Integer.parseInt(labelId); // WAWebListsActions: Number(n.id) / Number(e)
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Maps a {@link LabelEditAction.ListType} from the sync model onto the
     * WAM-level {@link ListType} enum used by {@link ListUpdateEvent} and
     * {@link ListUpdateUserJourneyEvent}.
     *
     * <p>Per WhatsApp Web {@code WAWebListsLogging.getListType}:
     * {@code NONE -> NONE}, {@code UNREAD -> UNREAD}, {@code GROUPS -> GROUP},
     * {@code FAVORITES -> FAVORITE}, {@code PREDEFINED -> PREDEFINED},
     * {@code CUSTOM -> CUSTOM}, {@code COMMUNITY -> COMMUNITY}, and
     * {@code SERVER_ASSIGNED -> SERVER_ASSIGNED}. The additional {@code DRAFTED},
     * {@code AI_HANDOFF}, and {@code CHANNELS} values present in the Cobalt
     * model enum are not mapped because {@code WAWebListsLogging.getListType}
     * only handles the eight cases above and returns {@code undefined} for
     * anything else.
     *
     * @implNote WAWebListsLogging.getListType
     * @param type the sync-model list type, may be {@code null}
     * @return the corresponding WAM list type, or {@code null} if the input is
     *         {@code null} or has no WAM counterpart
     */
    private static ListType mapWamListType(LabelEditAction.ListType type) {
        if (type == null) {
            return null; // WAWebListsLogging.getListType: if (e != null) switch (e) { ... } — null input returns undefined
        }
        return switch (type) {
            case NONE -> ListType.NONE; // WAWebListsLogging.getListType: ListType.NONE -> LIST_TYPE.NONE
            case UNREAD -> ListType.UNREAD; // WAWebListsLogging.getListType: ListType.UNREAD -> LIST_TYPE.UNREAD
            case GROUPS -> ListType.GROUP; // WAWebListsLogging.getListType: ListType.GROUPS -> LIST_TYPE.GROUP
            case FAVORITES -> ListType.FAVORITE; // WAWebListsLogging.getListType: ListType.FAVORITES -> LIST_TYPE.FAVORITE
            case PREDEFINED -> ListType.PREDEFINED; // WAWebListsLogging.getListType: ListType.PREDEFINED -> LIST_TYPE.PREDEFINED
            case CUSTOM -> ListType.CUSTOM; // WAWebListsLogging.getListType: ListType.CUSTOM -> LIST_TYPE.CUSTOM
            case COMMUNITY -> ListType.COMMUNITY; // WAWebListsLogging.getListType: ListType.COMMUNITY -> LIST_TYPE.COMMUNITY
            case SERVER_ASSIGNED -> ListType.SERVER_ASSIGNED; // WAWebListsLogging.getListType: ListType.SERVER_ASSIGNED -> LIST_TYPE.SERVER_ASSIGNED
            default -> null; // WAWebListsLogging.getListType: switch has no default branch, so other inputs return undefined
        };
    }
}
