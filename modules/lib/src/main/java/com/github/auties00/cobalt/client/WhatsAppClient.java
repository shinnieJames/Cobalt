package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.*;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.bot.profile.*;
import com.github.auties00.cobalt.model.business.*;
import com.github.auties00.cobalt.model.business.profile.*;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.chat.community.CommunityLinkedGroup;
import com.github.auties00.cobalt.model.chat.community.CommunityLinkedGroupBuilder;
import com.github.auties00.cobalt.model.chat.community.CommunityMetadataBuilder;
import com.github.auties00.cobalt.model.chat.group.GroupMetadataBuilder;
import com.github.auties00.cobalt.model.chat.group.GroupParticipant;
import com.github.auties00.cobalt.model.chat.group.GroupParticipantBuilder;
import com.github.auties00.cobalt.model.chat.group.GroupPartipantRole;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterViewerRole;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.mex.json.community.FetchAllSubgroupsMex;
import com.github.auties00.cobalt.node.mex.json.community.FetchAllSubgroupsMex.Response.DefaultSubGroup;
import com.github.auties00.cobalt.node.mex.json.community.FetchAllSubgroupsMex.Response.SubGroups;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.socket.WhatsAppSocketClient;
import com.github.auties00.cobalt.socket.WhatsAppSocketListener;
import com.github.auties00.cobalt.socket.WhatsAppSocketStanza;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.WebAppStateService;
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
 * A class used to interface a user to Whatsapp
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public final class WhatsAppClient {
    private static final byte[] SIGNAL_KEY_TYPE = {SignalIdentityPublicKey.type()};

    private static final int PROFILE_PIC_SIZE = 64;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^(.+)@(\\S+)$");

    private static final long MIN_PRE_KEYS_COUNT = 5;

    private final WhatsAppStore store;
    private final WhatsAppClientErrorHandler errorHandler;
    private final WhatsAppClientMessagePreviewHandler messagePreviewHandler;

    private final WebAppStateService webAppStateService;
    private final LidMigrationService lidMigrationService;
    private final DeviceService deviceService;
    private final ABPropsService abPropsService;
    private final InactiveGroupLidMigrationService inactiveGroupLidMigrationService;

    private final MessageService messageService;
    private final WamService wamService;

    private WhatsAppSocketClient socketClient;
    private final SocketStream socketStream;
    private final ConcurrentMap<String, WhatsAppSocketStanza> pendingSocketRequests;
    private Thread shutdownHook;

    WhatsAppClient(WhatsAppStore store, WhatsAppClientVerificationHandler.Web webVerificationHandler, WhatsAppClientMessagePreviewHandler messagePreviewHandler, WhatsAppClientErrorHandler errorHandler) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler cannot be null");
        if ((store.clientType() == WhatsAppClientType.WEB) == (webVerificationHandler == null)) {
            throw new IllegalArgumentException("webVerificationHandler cannot be null when client type is WEB");
        }
        var sessionCipher = new SignalSessionCipher(store);
        var groupCipher = new SignalGroupCipher(store);
        this.abPropsService = new ABPropsService(this);
        var snapshotRecoveryService = new SnapshotRecoveryService(this, abPropsService);
        this.webAppStateService = new WebAppStateService(this, abPropsService, snapshotRecoveryService);
        this.lidMigrationService = new LidMigrationService(this, abPropsService);
        this.inactiveGroupLidMigrationService = new InactiveGroupLidMigrationService(this, abPropsService);
        this.deviceService = new DeviceService(this, abPropsService, sessionCipher);
        this.messageService = new MessageService(this, sessionCipher, groupCipher, deviceService, abPropsService);
        this.wamService = new WamService(this, abPropsService);
        this.pendingSocketRequests = new ConcurrentHashMap<>();
        this.socketStream = new SocketStream(this, webVerificationHandler, lidMigrationService, inactiveGroupLidMigrationService, messageService, abPropsService, deviceService, wamService, snapshotRecoveryService, webAppStateService);
        this.messagePreviewHandler = messagePreviewHandler;
    }

    /**
     * Creates a new builder
     *
     * @return a builder
     */
    public static WhatsAppClientBuilder builder() {
        return WhatsAppClientBuilder.INSTANCE;
    }

    //<editor-fold desc="Data">

    /**
     * Returns the store associated with this session
     *
     * @return a non-null WhatsappStore
     */
    public WhatsAppStore store() {
        return store;
    }

    public WhatsAppClientMessagePreviewHandler messagePreviewHandler() {
        return messagePreviewHandler;
    }

    public ABPropsService abPropsService() {
        return abPropsService;
    }

    public com.github.auties00.cobalt.migration.LidMigrationService lidMigrationService() {
        return lidMigrationService;
    }

    //</editor-fold>

    //<editor-fold desc="Connection">

    /**
     * Connects to Whatsapp
     */
    public WhatsAppClient connect() {
        connect(null);
        return this;
    }

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

    public void disconnect(WhatsAppClientDisconnectReason reason) {
        disconnect(reason, true);
    }

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
            // TODO
            e.printStackTrace();
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
     * Flushes any dirty syncd collections on a virtual thread bounded by the
     * {@code syncd_sentinel_timeout_seconds} AB prop, mirroring WA Web's
     * {@code WAWebSocketModel.sendLogout} which awaits the sentinel flush only
     * for {@code Math.min(20, Math.max(0, getSyncdSentinelTimeoutSeconds())) * 1000}
     * milliseconds before proceeding with the disconnect.
     *
     * <p>The flush is performed on a daemon virtual thread joined with the
     * configured timeout. If the join times out or the join is interrupted, the
     * disconnect proceeds anyway and a warning is logged. Exceptions thrown by
     * the flush itself are swallowed (best-effort) so a flush failure cannot
     * block disconnect.
     *
     * @implNote WAWebSocketModel.sendLogout — bounded sentinel flush wait;
     *           {@link SyncKeyUtils#getSyncdSentinelTimeoutSeconds(ABPropsService)}
     *           supplies the configured timeout.
     */
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

    public Node sendNode(NodeBuilder node) {
        return sendNode(node, null);
    }

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
     * Disconnects from Whatsapp Web's WebSocket if a previous connection exists
     *
     */
    public void disconnect() {
        disconnect(WhatsAppClientDisconnectReason.DISCONNECTED);
    }

    /**
     * Disconnects and reconnects to Whatsapp Web's WebSocket if a previous connection exists
     *
     */
    public void reconnect() {
        disconnect(WhatsAppClientDisconnectReason.RECONNECTING);
    }

    /**
     * Disconnects from Whatsapp Web's WebSocket and logs out of WhatsappWeb invalidating the previous
     * saved credentials. The next time the API is used, the QR code will need to be scanned again.
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
     * Returns whether the connection is active or not
     *
     * @return a boolean
     */
    public boolean isConnected() {
        return socketClient != null && socketClient.isConnected();
    }

    /**
     * Waits for this session to be disconnected
     */
    public WhatsAppClient waitForDisconnection() {
        if (!isConnected()) {
            return this;
        }

        var future = new CompletableFuture<Void>();
        store.listeners().add(new WhatsAppClientListener() {
            @Override
            public void onDisconnected(WhatsAppClient whatsapp, WhatsAppClientDisconnectReason reason) {
                if (reason != WhatsAppClientDisconnectReason.RECONNECTING) {
                    future.complete(null);
                }
            }
        });
        future.join();
        return this;
    }

    //</editor-fold>

    //<editor-fold desc="Error handling">

    public void handleFailure(WhatsAppException exception) {
        var result = errorHandler.handleError(this, exception);
        switch (result) {
            case BAN -> disconnect(WhatsAppClientDisconnectReason.BANNED);
            case LOG_OUT -> disconnect(WhatsAppClientDisconnectReason.LOGGED_OUT);
            case DISCONNECT -> disconnect(WhatsAppClientDisconnectReason.DISCONNECTED);
            case RECONNECT -> disconnect(WhatsAppClientDisconnectReason.RECONNECTING);
        }
    }

    public void pushWebAppState(SyncPatchType type, List<SyncPendingMutation> patches) {
        webAppStateService.pushPatches(type, patches);
    }

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

    public void sendAck(Node node) {
        var id = node.getRequiredAttributeAsString("id");
        sendAck(id, node);
    }

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

    private Node createCall(JidProvider jid) {
        throw new UnsupportedOperationException();
    }

    public SequencedCollection<Newsletter> queryNewsletters() {
        throw new UnsupportedOperationException();
    }

    public SequencedCollection<Chat> queryGroups() {
        throw new UnsupportedOperationException();
    }

    public void sendPeerMessage(Jid chatJid, ChatMessageInfo response) {
        throw new UnsupportedOperationException();
    }

    public Optional<String> queryName(Jid receiver) {
        throw new UnsupportedOperationException();
    }

    public void queryNewsletterMessages(Jid newsletterJid, int i) {
        throw new UnsupportedOperationException();
    }

    public Optional<Newsletter> queryNewsletter(Jid newsletterJid, NewsletterViewerRole role) {
        throw new UnsupportedOperationException();
    }

    public Optional<String> queryAbout(Jid jid) {
        throw new UnsupportedOperationException();
    }

    public void queryBusinessCatalog(Jid targetJid) {
        throw new UnsupportedOperationException();
    }

    public void queryBusinessCollections(Jid targetJid) {
        throw new UnsupportedOperationException();
    }

    public SequencedCollection<Jid> queryBlockList() {
        throw new UnsupportedOperationException();
    }

    public Optional<URI> queryPicture(Jid self) {
        throw new UnsupportedOperationException();
    }

    public void subscribeToPresence(Jid jid) {
        throw new UnsupportedOperationException();
    }

    public void sendMessage(Jid jid, MessageContainer container) {
        throw new UnsupportedOperationException();
    }
}
