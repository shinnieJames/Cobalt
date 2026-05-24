package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.CallEndReason;
import com.github.auties00.cobalt.call.CallOptions;
import com.github.auties00.cobalt.call.IncomingCall;
import com.github.auties00.cobalt.exception.*;
import com.github.auties00.cobalt.model.bot.profile.BotDirectory;
import com.github.auties00.cobalt.model.bot.profile.BotProfile;
import com.github.auties00.cobalt.model.business.BusinessDataSharingConsent;
import com.github.auties00.cobalt.model.business.BusinessSignedUserInfo;
import com.github.auties00.cobalt.model.business.cart.BusinessCartRefresh;
import com.github.auties00.cobalt.model.business.cart.BusinessRefreshedCart;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalog;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogEntry;
import com.github.auties00.cobalt.model.business.catalog.BusinessProduct;
import com.github.auties00.cobalt.model.business.compliance.BusinessMerchantCompliance;
import com.github.auties00.cobalt.model.business.compliance.MerchantComplianceEdit;
import com.github.auties00.cobalt.model.business.ctwa.BusinessCtwaContext;
import com.github.auties00.cobalt.model.business.ctwa.CtwaAccessTokenSession;
import com.github.auties00.cobalt.model.business.ctwa.CtwaAdMediaEntry;
import com.github.auties00.cobalt.model.business.ctwa.CtwaSilentNonceResult;
import com.github.auties00.cobalt.model.business.linking.BusinessEligibility;
import com.github.auties00.cobalt.model.business.linking.BusinessLinkedAccounts;
import com.github.auties00.cobalt.model.business.marketing.BusinessMeteredMessagingCheckout;
import com.github.auties00.cobalt.model.business.marketing.BusinessMeteredMessagingPendingCampaign;
import com.github.auties00.cobalt.model.business.order.BusinessOrder;
import com.github.auties00.cobalt.model.business.postcode.BusinessPostcodeVerification;
import com.github.auties00.cobalt.model.business.postcode.BusinessPostcodeVerificationResult;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.business.profile.BusinessCategoryTypeahead;
import com.github.auties00.cobalt.model.business.profile.BusinessProfile;
import com.github.auties00.cobalt.model.call.CallLink;
import com.github.auties00.cobalt.model.call.CallLinkCreate;
import com.github.auties00.cobalt.model.call.CallLinkMedia;
import com.github.auties00.cobalt.model.call.CallLog;
import com.github.auties00.cobalt.model.chat.*;
import com.github.auties00.cobalt.model.chat.community.*;
import com.github.auties00.cobalt.model.chat.group.*;
import com.github.auties00.cobalt.model.contact.*;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.federated.*;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.LidChange;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.call.ScheduledCallCreationMessage;
import com.github.auties00.cobalt.model.message.call.ScheduledCallEditMessage;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.group.GroupInviteMessage;
import com.github.auties00.cobalt.model.message.poll.PollCreationMessage;
import com.github.auties00.cobalt.model.newsletter.*;
import com.github.auties00.cobalt.model.payment.BrazilCustomPaymentMethod;
import com.github.auties00.cobalt.model.payment.BrazilCustomPaymentMethodCreate;
import com.github.auties00.cobalt.model.payment.PaymentsTosV3ConsumerVariant;
import com.github.auties00.cobalt.model.preference.*;
import com.github.auties00.cobalt.model.privacy.*;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.model.reporting.*;
import com.github.auties00.cobalt.model.setting.*;
import com.github.auties00.cobalt.model.setting.notice.UserNoticeBundle;
import com.github.auties00.cobalt.model.setting.notice.UserNoticeStage;
import com.github.auties00.cobalt.model.setting.notice.UserNoticeStageQuery;
import com.github.auties00.cobalt.model.setting.privacy.ContactBlacklistAddressingMode;
import com.github.auties00.cobalt.model.setting.privacy.OptOutListUpdate;
import com.github.auties00.cobalt.model.setting.push.PushConfig;
import com.github.auties00.cobalt.model.signal.*;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeight;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethod;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.action.setting.NotificationActivitySettingAction;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.usync.UsyncQuery;
import com.github.auties00.cobalt.node.usync.UsyncResult;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.util.BusinessLabelConstants;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * Public contract for the WhatsApp client surface that mirrors WA Web's
 * top-level XMPP session: encrypted socket, signed pre-key uploads, app-state
 * sync, IQ/SMAX/MEX dispatch, business catalog jobs, signalling for VoIP,
 * and the full set of conversation operations the Web UI exposes.
 *
 * <p>This is the consumer-facing interface; every caller in the codebase
 * depends on it (never on a concrete implementation). The default production
 * implementation is {@link DefaultWhatsAppClient}, wired up by
 * {@link WhatsAppClientBuilder}. Tests can supply their own implementation
 * through the {@code client.test} package without standing up a real socket.
 *
 * <p>Lifecycle: callers obtain a builder via {@link #builder()}, configure
 * the store and the {@link WhatsAppClientErrorHandler}, call {@link #connect()}
 * to bring the socket up, drive feature operations on this interface, and
 * shut down with {@link #disconnect()}, {@link #reconnect()}, or
 * {@link #logout()}. {@link #waitForDisconnection()} blocks the caller until
 * a non-reconnect disconnect lands. Session events are delivered through
 * {@link WhatsAppClientListener}.
 *
 * <p>Method-level javadoc lives here on the interface; the implementation
 * in {@link DefaultWhatsAppClient} inherits docs via {@code {@inheritDoc}} so
 * each concrete method does not duplicate the contract. Implementation-only
 * notes (WA-source mappings, timing, adaptation comments) remain on the impl
 * via {@code com.github.auties00.cobalt.meta.*} annotations and {@code @implNote}.
 */
public interface WhatsAppClient {
    /**
     * Returns the entry point for assembling a configured
     * {@link WhatsAppClient}.
     *
     * @apiNote
     * The returned {@link WhatsAppClientBuilder} is a static singleton
     * because every per-session knob (id, serializer, error handler) is
     * supplied through fluent {@code newConnection*} methods rather than
     * constructor arguments. Embedders call this once at startup and
     * chain {@code newConnection(...).build()} to obtain a ready
     * {@code WhatsAppClient}.
     *
     * @return the shared {@link WhatsAppClientBuilder} singleton
     */
    static WhatsAppClientBuilder builder() {
        return WhatsAppClientBuilder.INSTANCE;
    }

    /**
     * Returns the {@link WhatsAppStore} that backs this session.
     *
     * @apiNote
     * The store is the single source of truth for every persisted
     * entity this session knows about: chats, contacts, messages,
     * Signal keys, app-state versions, AB-prop overrides, and presence.
     * Embedders read state directly via its accessors and subscribe to
     * live updates with {@link WhatsAppStore#addListener}.
     *
     * @return the live store backing this client
     */
    WhatsAppStore store();

    /**
     * Brings the encrypted socket up and starts the stanza pump.
     *
     * @apiNote
     * This is the entry point that turns a configured but inert
     * {@link WhatsAppClient} into a live session: it opens the encrypted
     * tunnel, installs a JVM shutdown hook so an abrupt process exit
     * still flushes pending work, and starts dispatching events to
     * {@link WhatsAppClientListener} subscribers. The method returns as
     * soon as the socket is up; subsequent handshake, pairing, and login
     * events arrive asynchronously through
     * {@link WhatsAppClientListener#onLoggedIn(WhatsAppClient)} and
     * related callbacks. Use {@link #waitForDisconnection()} on a
     * separate thread when the caller needs to block until the session
     * ends.
     *
     * @return {@code this}, for fluent chaining
     * @throws IllegalStateException if the client is already connected
     */
    WhatsAppClient connect();

    /**
     * Completes the pending request whose {@code id} attribute matches
     * the inbound node.
     *
     * @apiNote
     * Internal plumbing for request/response correlation: every
     * request carries an id that the matching response echoes back,
     * and this call wakes the parked
     * {@link #sendNode(NodeBuilder)} caller whose id corresponds to
     * the inbound node. Embedders driving custom stanzas through
     * {@link #sendNode(NodeBuilder)} do not call this directly.
     *
     * @param node the inbound node that may carry a response to a
     *             pending request
     */
    void resolvePendingRequest(Node node);

    /**
     * Tears down the session for the given reason and propagates that
     * reason to every registered listener.
     *
     * @apiNote
     * The chosen
     * {@link WhatsAppClientDisconnectReason} drives store-level side
     * effects: {@link WhatsAppClientDisconnectReason#LOGGED_OUT} and
     * {@link WhatsAppClientDisconnectReason#BANNED} purge the persisted
     * credentials so the next connect needs a fresh pairing ceremony,
     * {@link WhatsAppClientDisconnectReason#RECONNECTING} leaves them
     * intact and triggers an immediate reconnect, and
     * {@link WhatsAppClientDisconnectReason#DISCONNECTED} simply closes
     * the socket. Listeners observe the transition via
     * {@link WhatsAppClientListener#onDisconnected(WhatsAppClient, WhatsAppClientDisconnectReason)}.
     *
     * @param reason the disconnection reason
     */
    void disconnect(WhatsAppClientDisconnectReason reason);

    /**
     * Sends the given node on the current socket without waiting for a
     * response.
     *
     * @apiNote
     * Use this for fire-and-forget messages: presence updates, ack
     * stanzas, receipts, and analytics broadcasts that either do not
     * require an acknowledgment or whose acknowledgment arrives as a
     * separate inbound notification. For request/response exchanges
     * call {@link #sendNode(NodeBuilder)} instead.
     *
     * @param node the node to send
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    void sendNodeWithNoResponse(Node node);

    /**
     * Sends a request node and blocks until the corresponding response
     * arrives.
     *
     * @apiNote
     * Convenience overload that matches the first inbound node carrying
     * the same {@code id} attribute as the outgoing request. Equivalent
     * to calling {@link #sendNode(NodeBuilder, Function)} with a
     * {@code null} filter.
     *
     * @param node the outgoing request builder
     * @return the response node
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    Node sendNode(NodeBuilder node);

    /**
     * Sends a request node and blocks the calling virtual thread until a
     * response matching the supplied filter arrives.
     *
     * @apiNote
     * Lowest-level escape hatch for callers that need to dispatch a
     * hand-built request and pick a specific response out of a stream
     * of inbound nodes (for example, a multi-stage device-list
     * exchange or a vendor extension). If the builder has no
     * correlation id one is generated and injected before
     * serialisation so the response matcher always has something to
     * correlate on. The outgoing node is also delivered to listeners
     * through
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
    Node sendNode(NodeBuilder node, Function<Node, Boolean> filter);

    /**
     * Dispatches a typed MEX (GraphQL-over-XMPP) request and returns the
     * parsed response node.
     *
     * @apiNote
     * Lowest-level entry point for the MEX (GraphQL-over-XMPP)
     * request family: newsletter mutations, business catalog queries,
     * conversation lock toggles, and similar typed operations. All
     * routing metadata (query id, operation name, encoding) is read
     * off the typed request, so callers only assemble the request and
     * let this method handle dispatch and the matching success or
     * failure telemetry.
     *
     * @param request the typed MEX request to dispatch
     * @return the response node from the WhatsApp relay
     * @throws NullPointerException            if {@code request} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    Node sendNode(MexOperation.Request request);

    /**
     * Dispatches a typed MEX request whose response is discarded while
     * still emitting the round-trip WAM telemetry.
     *
     * @apiNote
     * Use this for MEX mutations whose response carries no payload the
     * caller cares about (for example a newsletter join or leave). The
     * method still blocks on the response and records the same
     * success/failure telemetry as
     * {@link #sendNode(MexOperation.Request)}, minus the value
     * return.
     *
     * @param request the typed MEX request to dispatch
     * @throws NullPointerException            if {@code request} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    void sendNodeWithNoResponse(MexOperation.Request request);

    /**
     * Dispatches a typed SMAX request and returns the parsed response
     * node.
     *
     * @apiNote
     * Lowest-level entry point for the SMAX RPC family, modelled as
     * typed {@link SmaxOperation.Request} values. Use this for
     * endpoints whose response carries data the caller consumes (for
     * example call-link queries); use
     * {@link #sendNodeWithNoResponse(SmaxOperation.Request)} for
     * fire-and-forget mutations whose payload is ignored.
     *
     * @param request the typed SMAX request to dispatch
     * @return the inbound response node
     * @throws NullPointerException            if {@code request} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    Node sendNode(SmaxOperation.Request request);

    /**
     * Dispatches a typed SMAX request whose response is discarded.
     *
     * @apiNote
     * Use this for fire-and-forget SMAX RPCs where the side effect of
     * sending the request is the whole contract and the response
     * carries no payload the caller cares about. Compare
     * {@link #sendNode(SmaxOperation.Request)} for the value-returning
     * variant.
     *
     * @param request the typed SMAX request to dispatch
     * @throws NullPointerException            if {@code request} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    void sendNodeWithNoResponse(SmaxOperation.Request request);

    /**
     * Dispatches a USync query and returns the parsed result.
     *
     * @apiNote
     * Canonical entry point for USync operations: contact sync,
     * device-list lookup, status-privacy contact resolution, bot
     * profile fetch, and similar batched lookups. The query carries
     * its own protocol mix and user list; this method honours any
     * active backoff window the server has signalled for those
     * protocols, dispatches the request, parses the response, and
     * records any returned backoff hint so subsequent queries observe
     * the rate limit. Concurrent calls from different threads are
     * supported provided each call uses its own {@link UsyncQuery}
     * instance.
     *
     * @param query the query; must not be shared across threads while
     *              still being configured
     * @return the parsed result
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws InterruptedException            if the thread is
     *                                         interrupted while waiting
     *                                         for an active backoff to
     *                                         elapse
     */
    UsyncResult sendNode(UsyncQuery query) throws InterruptedException;

    /**
     * Disconnects this client from the WhatsApp servers, preserving the
     * persisted credentials for future reconnections.
     *
     * @apiNote
     * Convenience shorthand for
     * {@link #disconnect(WhatsAppClientDisconnectReason)
     * disconnect(DISCONNECTED)}. Use this when the embedder wants to
     * release the socket without surrendering the pairing; the next
     * {@link #connect()} resumes without a fresh QR scan.
     */
    void disconnect();

    /**
     * Disconnects and immediately re-establishes the connection.
     *
     * @apiNote
     * Convenience shorthand for
     * {@link #disconnect(WhatsAppClientDisconnectReason)
     * disconnect(RECONNECTING)}. Use this to recover from a transient
     * network issue, force a fresh handshake, or apply a credential
     * change that takes effect only on the next login. The
     * reconnect-reason disconnect notification fires once, then a new
     * login flow runs end to end and listeners observe a fresh
     * {@link WhatsAppClientListener#onLoggedIn(WhatsAppClient)}.
     */
    void reconnect();

    /**
     * Logs this companion out of WhatsApp, invalidating the persisted
     * credentials for this session.
     *
     * @apiNote
     * Performs the same "Log out" action a user invokes from the
     * Linked Devices menu: the primary device detaches this companion
     * and the persisted credentials are discarded. After this returns,
     * the next {@link #connect()} requires a fresh authentication
     * ceremony (QR scan, pairing code, or phone-number registration).
     * Sessions that have not yet learned their own JID short-circuit
     * to a local {@link #disconnect(WhatsAppClientDisconnectReason)}
     * with {@link WhatsAppClientDisconnectReason#LOGGED_OUT}. To
     * detach a different linked device while staying connected, use
     * {@link #logoutCompanion(JidProvider)} instead.
     */
    void logout();

    /**
     * Detaches the given companion device from this account.
     *
     * @apiNote
     * Drives the "Log out" affordance shown against another linked
     * device in the Linked Devices panel: the primary device detaches
     * the supplied companion while leaving this session connected. The
     * companion must belong to this account; the server rejects JIDs
     * that do not appear in the caller's own device list. To log out
     * the currently-connected companion itself, use {@link #logout()}.
     *
     * @param companion the companion JID to detach; must include an
     *                  agent index (device slot) and must be a device
     *                  JID belonging to this account
     * @throws NullPointerException            if {@code companion} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    void logoutCompanion(JidProvider companion);

    /**
     * Reconciles the local view of the Linked Devices panel with the
     * server.
     *
     * @apiNote
     * Use after pairing or unpairing a companion from another device,
     * or whenever the Linked Devices settings surface should redraw
     * against an authoritative copy. The primary device (slot 0)
     * appears first; companions follow in server order. The new list
     * replaces {@link WhatsAppStore#linkedDevices()} and
     * {@link WhatsAppClientListener#onLinkedDevices} fires with the
     * new authoritative set.
     *
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    void refreshLinkedDevices();

    /**
     * Returns whether a live socket to the WhatsApp servers is currently
     * open.
     *
     * @apiNote
     * Reports the transport-level state only: returns {@code true}
     * between the moment {@link #connect()} brings the socket up and
     * the moment {@link #disconnect()} or a remote close tears it down.
     * It does not gate on login or pairing state; for those, observe
     * the {@link WhatsAppClientListener} callbacks.
     *
     * @return {@code true} if the socket is open and the handshake has
     *         not been torn down, {@code false} otherwise
     */
    boolean isConnected();

    /**
     * Blocks the calling thread until this session is disconnected.
     *
     * @apiNote
     * Use this as the canonical "park the main thread" idiom in
     * applications whose lifetime is bounded by a single WhatsApp
     * session. The wait completes only when
     * {@link WhatsAppClientListener#onDisconnected(WhatsAppClient, WhatsAppClientDisconnectReason)}
     * fires with a reason other than
     * {@link WhatsAppClientDisconnectReason#RECONNECTING}, so transient
     * reconnect cycles do not wake the caller and a long-running app
     * stays parked across them.
     *
     * @return {@code this}, for fluent chaining
     */
    WhatsAppClient waitForDisconnection();

    /**
     * Routes a session-fatal failure through the configured
     * {@link WhatsAppClientErrorHandler} and applies its decision.
     *
     * @apiNote
     * The central choke point for every error that bubbles out of the
     * socket layer, a sync round trip, or any other in-flight
     * operation. The handler's returned
     * {@link WhatsAppClientErrorHandler.Result} is mapped to a
     * concrete session-control action: discard, disconnect, reconnect,
     * log out, or ban. App-state fatal failures are additionally
     * mirrored to the telemetry pipeline so dashboards can correlate
     * the error with the failing sync collection. Embedders normally
     * do not call this directly; library internals invoke it when they
     * raise a {@link WhatsAppException}.
     *
     * @param exception the exception to handle
     */
    void handleFailure(WhatsAppException exception);

    /**
     * Hands a batch of outgoing sync mutations to the companion
     * app-state pipeline for upload.
     *
     * @apiNote
     * Every mutation that needs to propagate to other linked devices
     * (archive, mute, pin, label, quick-reply add or edit, etc.)
     * enters the app-state pipeline through this method. The patches
     * are grouped per collection, encrypted, uploaded under the
     * appropriate {@link SyncPatchType} version, and finally
     * re-applied locally so the optimistic store update is reconciled
     * with the server's authoritative version. Embedders only call
     * this when driving a sync action that has no dedicated
     * client-method wrapper; the typed action wrappers (archive, mute,
     * etc.) invoke it internally.
     *
     * @param type    the sync patch type being updated
     * @param patches the ordered mutations to apply
     */
    void pushWebAppState(SyncPatchType type, List<SyncPendingMutation> patches);

    /**
     * Pulls the latest server patches for the given collections and
     * reports whether any state actually changed.
     *
     * @apiNote
     * Used both during initial post-login bootstrap and as the manual
     * reconciliation hook the server signals when an app-state
     * dirty-bit notification arrives. The boolean return distinguishes
     * a no-op sync (no patches, no snapshot) from one that actually
     * applied remote updates; embedders that surface dirty-bit
     * telemetry use it to detect false-positive notifications. Other
     * callers may ignore the return value.
     *
     * @param patches the patch types to pull; an empty array is
     *                tolerated
     * @return {@code true} if any synced collection had patches or a
     *         snapshot, {@code false} when every collection sync
     *         completed without applying any state changes or when
     *         {@code patches} is empty
     */
    boolean pullWebAppState(SyncPatchType... patches);

    /**
     * Acknowledges an inbound stanza using its own {@code id} attribute
     * as the ack id.
     *
     * @apiNote
     * Convenience over {@link #sendAck(String, Node)} for callers
     * that need to confirm receipt of a stanza without substituting
     * their own correlation id. The library invokes this internally
     * on every received message, receipt, IQ result, and notification
     * so the server stops retransmitting.
     *
     * @param node the inbound node to acknowledge
     */
    void sendAck(Node node);

    /**
     * Acknowledges an inbound stanza using the supplied id.
     *
     * @apiNote
     * Acknowledges receipt of an inbound stanza so the server stops
     * retransmitting it; the ack is addressed back to the original
     * sender and tagged with the kind of stanza being acknowledged.
     * Use this when the caller needs to acknowledge with a synthesised
     * id (for example a coalesced delivery receipt covering a batch);
     * for the common one-stanza-one-ack case prefer
     * {@link #sendAck(Node)}.
     *
     * @param id   the acknowledgment id
     * @param node the inbound node being acknowledged
     */
    void sendAck(String id, Node node);

    /**
     * Generates and uploads a fresh batch of Signal pre-keys so remote
     * devices can establish new end-to-end sessions with this client.
     *
     * @apiNote
     * Replenishes the server-side pre-key bundle that remote devices
     * use to establish new end-to-end sessions. The post-login
     * bootstrap calls this with the full initial budget; later top-ups
     * happen whenever the server announces the bundle is running low.
     * The requested count is clamped upward so a degenerate small
     * request still ships a useful batch; on success the generated
     * keys are appended to the store so they can be reused when the
     * server hands the bundle back during sender-key fan-out.
     *
     * @param keysCount the number of additional pre-keys to generate
     *                  and upload; internally clamped upward
     */
    void sendPreKeys(long keysCount);

    /**
     * Sends a delivery, read, or played receipt for a single message
     * id.
     *
     * @apiNote
     * Per-message receipt entry point: most embedders only need to
     * mark one message at a time and this helper covers that case
     * directly. The {@code type} accepts the standard receipt names
     * ({@code "read"}, {@code "played"}, {@code "read-self"}, etc.);
     * pass {@code null} for a plain delivery receipt. The call is a
     * no-op when the client does not yet know its own JID so the
     * early-bootstrap window cannot leak unauthenticated receipts
     * during pairing.
     *
     * @param id   the message id to acknowledge
     * @param from the JID of the remote party to receipt
     * @param type the receipt type (for example {@code "read"} or
     *             {@code "played"}); {@code null} for a delivery
     *             receipt
     */
    void sendReceipt(String id, JidProvider from, String type);

    /**
     * Queries the server-side metadata of a group or community chat.
     *
     * @apiNote
     * Drives the panel that opens when a user taps a group or
     * community header: subject, description, creation timestamp,
     * participant list with admin flags, ephemeral-message setting,
     * and (for communities) the linked child groups. Embedders call
     * this on demand rather than prefetching on every chat switch.
     *
     * @param chat the target group or community
     * @return the parsed metadata
     * @throws IllegalArgumentException        if the JID is not a group
     *                                         or community
     * @throws NoSuchElementException          if the server response is
     *                                         malformed
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    ChatMetadata queryChatMetadata(JidProvider chat);

    /**
     * Queries the WhatsApp Business profile published by the given
     * contact.
     *
     * @apiNote
     * Drives the business-info pane that opens when a user taps a
     * merchant chat header: address, business hours, website list,
     * cover photo id, declared categories, optional cart toggle, and
     * so on. The server returns the full profile when the merchant
     * has one and signals an empty profile when the contact is not a
     * business; the latter surfaces as an empty {@link Optional}.
     *
     * @param contact the contact whose business profile should be
     *                fetched
     * @return the parsed profile, or {@link Optional#empty()} if the
     *         contact is not a business
     * @throws NullPointerException            if {@code contact} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    Optional<BusinessProfile> queryBusinessProfile(JidProvider contact);

    /**
     * Parses a single {@code <category>} node into a
     * {@link BusinessCategory}.
     *
     * @apiNote
     * Helper used by every code path that consumes a business-category
     * payload directly off the wire (business-profile responses,
     * category typeahead responses, catalog product nodes). It
     * URL-decodes the human-readable name so callers receive the
     * localized text verbatim. Embedders rarely call this directly; it
     * is exposed so extensions parsing custom stanzas can reuse the
     * same logic.
     *
     * @param node the {@code category} node
     * @return the parsed category
     * @throws NoSuchElementException if the category content is missing
     */
    BusinessCategory parseBusinessCategory(Node node);

    /**
     * Persists an updated WhatsApp Business profile for the
     * authenticated user.
     *
     * @apiNote
     * Writer counterpart to {@link #queryBusinessProfile(JidProvider)}:
     * drives the merchant's "Edit business info" form. Every field on
     * the supplied {@link BusinessProfile} that is non-{@code null} is
     * sent as a delta mutation; absent fields are left untouched
     * server-side. Embedders pass a profile carrying only the fields
     * they want to change.
     *
     * @param profile the new business-profile metadata
     * @throws NullPointerException            if {@code profile} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editBusinessProfile(BusinessProfile profile);

    /**
     * Toggles the in-chat shopping cart feature on the authenticated
     * user's business profile.
     *
     * @apiNote
     * Drives the "Shopping cart" toggle inside the business profile
     * settings: enabling it lets customers compose a multi-product
     * order inside the chat before sending it through to the merchant.
     *
     * @param enabled {@code true} to enable the cart, {@code false} to
     *                disable it
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void updateBusinessCartEnabled(boolean enabled);

    /**
     * Removes the current cover photo from the authenticated user's
     * business profile.
     *
     * @apiNote
     * Drives the "Remove cover photo" action in the business profile
     * editor: the merchant strips the masthead image and the profile
     * falls back to the platform default cover.
     *
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void deleteBusinessCoverPhoto();

    /**
     * Fetches the long-lived public verification certificate a WhatsApp
     * Business account publishes to its catalog clients.
     *
     * @apiNote
     * Business accounts sign sensitive catalog payloads (the merchant
     * phone-number envelope, address-verification responses, and
     * direct-connection-encrypted payloads attached to product orders)
     * with a server-issued certificate. This call returns the PEM
     * that backs those signatures so a Cobalt-driven storefront can
     * prove the payload it received actually came from the advertised
     * business and was not relayed through an impersonating peer.
     * Typical use: fetch once on first interaction with a business,
     * cache for the merchant's lifetime, and re-validate every signed
     * catalog response against it. Pair with
     * {@link #queryBusinessSignedUserInfo(JidProvider)} to obtain the
     * matching signed phone-number envelope.
     *
     * @param businessJid the JID of the business whose certificate is
     *                    being fetched
     * @return the PEM string when the server returned a certificate, or
     *         {@link Optional#empty()} when the server replied with no
     *         certificate (typically because the business has not been
     *         provisioned with one yet)
     * @throws NullPointerException            if {@code businessJid} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    Optional<String> queryBusinessPublicKey(JidProvider businessJid);

    /**
     * Fetches the cryptographically signed phone-number envelope a
     * WhatsApp Business account exposes to its catalog clients.
     *
     * @apiNote
     * The envelope binds the business JID to its merchant phone
     * number and notional business domain, all signed by the
     * business's private key. Callers verify the signature with the
     * matching certificate returned by
     * {@link #queryBusinessPublicKey(JidProvider)} to prove the
     * displayed contact details actually belong to the business they
     * are talking to. The {@code ttl_timestamp} carried alongside is
     * the server-recommended expiry after which clients should
     * re-fetch. Most fields are {@link Optional} because the server
     * omits the entire envelope (and any individual sub-field) when
     * the business has not been provisioned with a signed identity;
     * applications should treat an all-empty result as "no verifiable
     * identity available" rather than a hard error.
     *
     * @param businessJid the JID of the business whose signed
     *                    phone-number envelope should be fetched
     * @return the parsed envelope, with each field exposed as an
     *         {@link Optional} since the server may omit any or all of
     *         them
     * @throws NullPointerException            if {@code businessJid} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    BusinessSignedUserInfo queryBusinessSignedUserInfo(JidProvider businessJid);

    /**
     * Fetches the legal-entity disclosure one or more WhatsApp
     * Business accounts publish to their customers.
     *
     * @apiNote
     * Several jurisdictions (most notably India under the IT Rules
     * 2021) require business accounts to register the legal name of
     * the operating entity, the entity type, and dedicated
     * customer-care and grievance-officer contact channels. WhatsApp
     * surfaces those fields in the in-app "Business info" panel; this
     * call returns the same data so a Cobalt-driven catalog browser
     * can render the disclosure before letting the user place an
     * order, or a compliance dashboard can audit the merchant's
     * registration status. The call is batched: one round trip
     * resolves the disclosure for every JID, with each result returned
     * in the same order as the input list. Businesses that have not
     * registered compliance information come back with empty or
     * default-valued fields rather than being dropped.
     *
     * @param jids the business JIDs whose compliance metadata should
     *             be fetched
     * @return the parsed compliance entries in server order
     * @throws NullPointerException            if {@code jids} is
     *                                         {@code null}
     * @throws IllegalArgumentException        if {@code jids} is empty
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    List<BusinessMerchantCompliance> queryMerchantCompliance(List<? extends JidProvider> jids);

    /**
     * Publishes or updates the legal-entity disclosure shown to
     * customers on the authenticated business account.
     *
     * @apiNote
     * Writer counterpart to
     * {@link #queryMerchantCompliance(List)}: overwrites the registered
     * entity name, type, custom type description, registration flag,
     * and contact channels (customer care and grievance officer) shown
     * in the business profile's compliance panel. The disclosure is
     * server persisted, propagates to every linked device, and becomes
     * visible to anyone querying the same business JID. Every textual
     * field on the {@link MerchantComplianceEdit} is nullable: a
     * non-{@code null} value overwrites the server-side state, a
     * {@code null} leaves the existing value untouched. The server
     * echoes the post-update state back, which the method returns so
     * callers confirm what was actually persisted without an extra
     * round trip.
     *
     * @param edit the disclosure changes to apply
     * @return the merchant-compliance entry the server returned after
     *         applying the update
     * @throws NullPointerException            if {@code edit} is
     *                                         {@code null}
     * @throws NoSuchElementException          if the response carries
     *                                         no
     *                                         {@code <merchant_info>}
     *                                         child
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    BusinessMerchantCompliance editMerchantCompliance(MerchantComplianceEdit edit);

    /**
     * Searches the WhatsApp Business category catalog for entries whose
     * localized name matches the given partial query.
     *
     * @apiNote
     * Every WhatsApp Business profile must declare a category
     * ({@code "Restaurant"}, {@code "Clothing store"}, etc.) used both
     * in the in-app business info panel and for discoverability. This
     * autocomplete endpoint backs the onboarding "Business category"
     * picker: the server returns the localized categories whose name
     * matches the typed prefix. Embedders driving a merchant
     * onboarding flow call this on each keystroke. The result also
     * surfaces a "not a business" placeholder id: categories that
     * should be presented as the sentinel option carry their
     * {@code notABiz} flag set so the caller renders them distinctly.
     *
     * @param query the partial text the user has typed
     * @return the matched categories with their server-issued id,
     *         localized display name, and "not a business" flag
     * @throws NullPointerException            if {@code query} is
     *                                         {@code null}
     * @throws NoSuchElementException          if the server reply is
     *                                         malformed
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     */
    BusinessCategoryTypeahead queryBusinessCategoryTypeahead(String query);

    /**
     * Fetches the full detail of a business order identified by a
     * message id and a server-issued token.
     *
     * @apiNote
     * Drives the order-detail panel a recipient sees after tapping
     * through an order message to view the cart: creation timestamp,
     * currency, subtotal, total, and the list of ordered products with
     * quantities and per-line prices. The sensitive token shipped
     * alongside the order message authenticates the request, so a
     * third party that only knows the order id cannot enumerate the
     * cart. The result is projected into a {@link BusinessOrder} value.
     *
     * @param messageId   the server-issued order id (typically the id
     *                    of the {@code OrderMessage})
     * @param tokenBase64 the sensitive base64-encoded token shipped
     *                    with the order message
     * @return the parsed order, or {@link Optional#empty()} when the
     *         relay returns no order payload
     * @throws NullPointerException            if {@code messageId} or
     *                                         {@code tokenBase64} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<BusinessOrder> queryOrder(String messageId, String tokenBase64);

    /**
     * Creates a new quick reply template and propagates it to every
     * linked device through app-state sync.
     *
     * @apiNote
     * Drives the "Add quick reply" affordance inside the Business
     * Tools menu: the user picks a short shortcut ({@code "/menu"})
     * and a longer canned response ({@code "Here is our menu: ..."}),
     * with optional keyword tags driving the autocomplete surface. A
     * random client-generated id is minted for the new template; it
     * serves as the primary key under which the entry is filed in the
     * local store. The store is updated eagerly so the caller observes
     * the new template immediately, without waiting for the round trip
     * through the server and the inbound sync patch.
     *
     * @param create the new template content
     * @return the newly minted quick reply id
     * @throws NullPointerException            if {@code create} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    String createQuickReply(QuickReplyCreate create);

    /**
     * Replaces the content of an existing quick reply template and propagates the change through app-state sync.
     *
     * @apiNote
     * Drives the "Edit quick reply" affordance in the Business Tools
     * menu: the new shortcut, message body, and keyword list overwrite
     * the existing entry under the supplied id, and a quick-reply
     * sync patch is pushed so every linked device picks up the
     * change. The call returns {@link Optional#empty()} when the id is
     * unknown locally; the method does not synthesise a placeholder
     * entry for an id the local store has never seen.
     *
     * @param edit the new content keyed by the existing template id; never {@code null}
     * @return the pre-edit {@link QuickReply} snapshot, or {@link Optional#empty()} when no template with that id existed locally
     * @throws NullPointerException            if {@code edit} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<QuickReply> editQuickReply(QuickReplyEdit edit);

    /**
     * Removes a quick reply template and propagates the removal through app-state sync.
     *
     * @apiNote
     * Drives the "Delete quick reply" affordance in the Business
     * Tools menu: a removal mutation is pushed against the quick-reply
     * collection so every linked device drops the entry, and the
     * local store is updated eagerly so the caller observes the
     * deletion immediately.
     *
     * @param quickReplyId the id of the quick reply to delete; never {@code null}
     * @return the removed {@link QuickReply} as it existed before deletion, or {@link Optional#empty()} when no template with that id existed locally
     * @throws NullPointerException            if {@code quickReplyId} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<QuickReply> deleteQuickReply(String quickReplyId);

    /**
     * Queries the public profile of a Meta AI or third-party bot for its default persona.
     *
     * @apiNote
     * Single-argument convenience for
     * {@link #queryBotProfile(JidProvider, String)} that omits the
     * persona selector so the server returns the bot's default
     * persona (the one shown in the bot's chat header).
     *
     * @param botJid the bot JID to query; never {@code null}
     * @return the parsed {@link BotProfile}, or {@link Optional#empty()} when the server returns no profile for the bot
     * @throws NullPointerException            if {@code botJid} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     * @see #queryBotProfile(JidProvider, String)
     */
    Optional<BotProfile> queryBotProfile(JidProvider botJid);

    /**
     * Queries the public profile of a Meta AI or third-party bot for a specific persona.
     *
     * @apiNote
     * Drives the bot info header rendered at the top of a bot chat:
     * display name, attributes blob, description, registered slash
     * commands, suggested prompts, and classification flags
     * (Meta-created, creator name and link, professional-status type).
     * The supplied {@code personaId} selects a specific persona;
     * passing {@code null} requests the bot's default persona.
     *
     * @param botJid    the bot JID to query; never {@code null}
     * @param personaId the persona id, or {@code null} to omit the {@code persona_id} attribute and request the default persona
     * @return the parsed {@link BotProfile}, or {@link Optional#empty()} when the server returns no profile for the bot
     * @throws NullPointerException            if {@code botJid} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<BotProfile> queryBotProfile(JidProvider botJid, String personaId);

    /**
     * Places a one-to-one voice or video call to {@code target} and returns a live session.
     *
     * @apiNote
     * Drives the "Call" / "Video call" affordance in the user chat
     * header: sends a call offer addressed at the callee's device
     * list, registers the resulting {@link ActiveCall} in the
     * in-flight call store, and returns it to the caller so further
     * signalling (mute, video state, termination) can be driven
     * through the session handle. The {@link CallOptions} argument
     * selects audio-only vs video on the outgoing leg via
     * {@link CallOptions#videoEnabled()}.
     *
     * @param target  the JID of the callee; must be a user JID
     * @param options the call options selecting audio vs video on the outgoing leg
     * @return the live {@link ActiveCall} session bound to the negotiated call id
     * @throws NullPointerException            if {@code target} is {@code null}
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     * @see #acceptCall(IncomingCall, CallOptions)
     * @see #terminateCall(ActiveCall, CallEndReason)
     */
    ActiveCall startCall(JidProvider target, CallOptions options);

    /**
     * Accepts a pending {@link IncomingCall} offer and returns a live session.
     *
     * @apiNote
     * Drives the green "Accept" button on the incoming-call sheet:
     * marks the offer as responded so a later reject or terminate
     * becomes a no-op, accepts the call, and parks the resulting
     * session in the connecting state until transport setup completes.
     * The accepting {@link CallOptions} may downgrade an offered video
     * call to audio-only by passing
     * {@link CallOptions#videoEnabled()} as {@code false} but cannot
     * upgrade an audio-only offer to video; for that, place a fresh
     * video call through {@link #startCall(JidProvider, CallOptions)}.
     *
     * @param offer   the incoming offer to accept; never {@code null}
     * @param options the local side's preferred settings (audio vs video) for the answered leg
     * @return the live {@link ActiveCall} session for the accepted call
     * @throws NullPointerException            if {@code offer} or {@code options} is {@code null}
     * @throws IllegalStateException           if the offer has already been responded to
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    ActiveCall acceptCall(IncomingCall offer, CallOptions options);

    /**
     * Rejects a pending {@link IncomingCall} offer with the supplied end-call reason.
     *
     * @apiNote
     * Drives the red "Decline" button on the incoming-call sheet:
     * marks the offer as responded so a subsequent accept or
     * terminate becomes a no-op, signals the rejection with the
     * supplied {@link CallEndReason}, drops the offer from the call
     * store, and fires {@link WhatsAppClientListener#onCallEnded} on
     * every registered listener so the UI can dismiss its ringtone
     * and dialog.
     *
     * @param offer  the incoming offer to reject; never {@code null}
     * @param reason the reason to communicate to the peer; placed on the {@code reason} attribute as {@link CallEndReason#wireValue()}
     * @throws NullPointerException            if {@code offer} or {@code reason} is {@code null}
     * @throws IllegalStateException           if the offer has already been responded to
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void rejectCall(IncomingCall offer, CallEndReason reason);

    /**
     * Terminates an in-progress call with the supplied reason.
     *
     * @apiNote
     * Drives every voluntary call-end path (hang up, timeout, mic
     * permission denial, blocked-by-callee, network lost, etc.): looks
     * up the call peer from the in-flight call store keyed by
     * {@code callId}, then signals call termination to the peer with
     * the chosen {@link CallEndReason}.
     *
     * @param callId the identifier of the call to terminate; never {@code null}
     * @param reason the end-call reason; placed on the {@code reason} attribute as {@link CallEndReason#wireValue()}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws NoSuchElementException          if no call with the given id is cached in the store
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     * @see #terminateCall(ActiveCall, CallEndReason)
     */
    void terminateCall(String callId, CallEndReason reason);

    /**
     * Terminates an in-progress call, taking the call id and peer JID directly from the supplied {@link ActiveCall}.
     *
     * @apiNote
     * Use this when the caller already holds the live
     * {@link ActiveCall} handle returned by
     * {@link #startCall(JidProvider, CallOptions)} or
     * {@link #acceptCall(IncomingCall, CallOptions)}, so the store
     * lookup performed by {@link #terminateCall(String, CallEndReason)}
     * is unnecessary.
     *
     * @param call   the in-progress call to terminate; never {@code null}
     * @param reason the end-call reason; placed on the {@code reason} attribute as {@link CallEndReason#wireValue()}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     * @see #terminateCall(String, CallEndReason)
     */
    void terminateCall(ActiveCall call, CallEndReason reason);

    /**
     * Announces to the relay that this device has consumed an incoming offer and is ringing the local user.
     *
     * @apiNote
     * Issues the preaccept signal that sits between the inbound offer
     * and the eventual accept, telling the relay that this device
     * passed receive-side validation and is now alerting the user.
     * The peer is looked up out of the in-flight call store using
     * {@code callId}.
     *
     * @param callId the identifier carried by the original offer; never {@code null}
     * @throws NullPointerException            if {@code callId} is {@code null}
     * @throws NoSuchElementException          if no call with the given id is cached in the store
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     * @see #preacceptCall(IncomingCall)
     */
    void preacceptCall(String callId);

    /**
     * Announces preaccept on an incoming offer, taking the call id and peer JID directly from the supplied {@link IncomingCall}.
     *
     * @apiNote
     * Use this when the caller already holds the {@link IncomingCall}
     * value delivered through
     * {@link WhatsAppClientListener#onCall(WhatsAppClient, IncomingCall)}, so the store
     * lookup performed by {@link #preacceptCall(String)} is
     * unnecessary.
     *
     * @param call the incoming offer to preaccept; never {@code null}
     * @throws NullPointerException            if {@code call} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     * @see #preacceptCall(String)
     */
    void preacceptCall(IncomingCall call);

    /**
     * Announces a mic-mute or mic-unmute on an in-progress call.
     *
     * @apiNote
     * Drives the mute toggle shown in the in-call control bar: signals
     * the mute state to the peer so the remote UI can render the
     * muted-mic icon next to the local participant.
     *
     * @param callId the identifier of the in-progress call; never {@code null}
     * @param muted  {@code true} to announce a mute, {@code false} to announce an unmute
     * @throws NullPointerException            if {@code callId} is {@code null}
     * @throws NoSuchElementException          if no call with the given id is cached in the store
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     * @see #muteCall(ActiveCall, boolean)
     */
    void muteCall(String callId, boolean muted);

    /**
     * Announces a mic-mute or mic-unmute on an in-progress call, taking the call id and peer JID directly from the supplied {@link ActiveCall}.
     *
     * @apiNote
     * Use this when the caller already holds the live
     * {@link ActiveCall} handle returned by
     * {@link #startCall(JidProvider, CallOptions)} or
     * {@link #acceptCall(IncomingCall, CallOptions)}, so the store
     * lookup performed by {@link #muteCall(String, boolean)} is
     * unnecessary.
     *
     * @param call  the in-progress call; never {@code null}
     * @param muted {@code true} to announce a mute, {@code false} to announce an unmute
     * @throws NullPointerException            if {@code call} is {@code null}
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     * @see #muteCall(String, boolean)
     */
    void muteCall(ActiveCall call, boolean muted);

    /**
     * Announces a camera-on or camera-off transition on an in-progress call.
     *
     * @apiNote
     * Drives the camera toggle shown in the in-call control bar and
     * also drives the mid-call video-upgrade flow: passing
     * {@code true} both announces video-on for the local participant
     * and surfaces the upgrade-to-video request to the peer (which
     * sees it as
     * {@link WhatsAppClientListener#onCallVideoStateChanged
     * onCallVideoStateChanged(..., true)}); passing {@code false}
     * announces video-off and the inverse downgrade.
     *
     * @param call         the in-progress call to update; never {@code null}
     * @param videoEnabled {@code true} to announce video-on, {@code false} to announce video-off
     * @throws NullPointerException            if {@code call} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void editCallVideoState(ActiveCall call, boolean videoEnabled);

    /**
     * Initiates a group call addressed at the given group JID and participant list.
     *
     * @apiNote
     * Drives the "Call group" / "Video call group" affordance in a
     * group chat header: sends a call offer addressed at the group
     * with the participant device list inlined, registers the
     * resulting {@link ActiveCall} in the in-flight call store, and
     * returns it so the caller can drive further signalling through
     * the session handle.
     *
     * @param group        the JID of the group being called; must have a group or community server
     * @param participants the participants to invite; must be non-empty
     * @param video        {@code true} to advertise this as a video call, {@code false} for audio-only
     * @return the live {@link ActiveCall} session bound to the negotiated call id
     * @throws NullPointerException     if {@code group} or {@code participants} is {@code null}
     * @throws IllegalArgumentException if {@code group} is not a group or community JID, or {@code participants} is empty
     * @throws IllegalStateException    if this client is not logged in
     * @see #addCallParticipants(ActiveCall, Collection)
     * @see #removeCallParticipants(ActiveCall, Collection)
     */
    ActiveCall startGroupCall(JidProvider group, Collection<? extends JidProvider> participants, boolean video);

    /**
     * Invites additional participants to an in-progress group call.
     *
     * @apiNote
     * Drives the "Add participant" affordance on the in-call
     * participant sheet: signals the join request to the group call,
     * listing the JIDs being added. The host JID is taken from the
     * in-flight call store entry keyed by {@code callId}.
     *
     * @param callId       the identifier of the in-progress call; never {@code null}
     * @param participants the participants to invite; must be non-empty
     * @throws NullPointerException            if any argument is {@code null}
     * @throws NoSuchElementException          if no call with the given id is cached in the store
     * @throws IllegalArgumentException        if the call's chat is not a group or community JID, or {@code participants} is empty
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     * @see #addCallParticipants(ActiveCall, Collection)
     */
    void addCallParticipants(String callId, Collection<? extends JidProvider> participants);

    /**
     * Invites additional participants to an in-progress group call, taking the call id and group JID directly from the supplied {@link ActiveCall}.
     *
     * @apiNote
     * Use this when the caller already holds the live
     * {@link ActiveCall} handle returned by
     * {@link #startGroupCall(JidProvider, Collection, boolean)}, so
     * the store lookup performed by
     * {@link #addCallParticipants(String, Collection)} is
     * unnecessary.
     *
     * @param call         the in-progress group call; never {@code null}
     * @param participants the participants to invite; must be non-empty
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if the call's chat is not a group or community JID, or {@code participants} is empty
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     * @see #addCallParticipants(String, Collection)
     */
    void addCallParticipants(ActiveCall call, Collection<? extends JidProvider> participants);

    /**
     * Removes participants from an in-progress group call.
     *
     * @apiNote
     * Drives the "Remove from call" affordance on the in-call
     * participant sheet: signals the removal to the group call,
     * listing the JIDs being removed. The host JID is taken from the
     * in-flight call store entry keyed by {@code callId}.
     *
     * @param callId       the identifier of the in-progress call; never {@code null}
     * @param participants the participants to remove; must be non-empty
     * @throws NullPointerException            if any argument is {@code null}
     * @throws NoSuchElementException          if no call with the given id is cached in the store
     * @throws IllegalArgumentException        if the call's chat is not a group or community JID, or {@code participants} is empty
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     * @see #removeCallParticipants(ActiveCall, Collection)
     */
    void removeCallParticipants(String callId, Collection<? extends JidProvider> participants);

    /**
     * Removes participants from an in-progress group call, taking the call id and group JID directly from the supplied {@link ActiveCall}.
     *
     * @apiNote
     * Use this when the caller already holds the live
     * {@link ActiveCall} handle returned by
     * {@link #startGroupCall(JidProvider, Collection, boolean)}, so
     * the store lookup performed by
     * {@link #removeCallParticipants(String, Collection)} is
     * unnecessary.
     *
     * @param call         the in-progress group call; never {@code null}
     * @param participants the participants to remove; must be non-empty
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if the call's chat is not a group or community JID, or {@code participants} is empty
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     * @see #removeCallParticipants(String, Collection)
     */
    void removeCallParticipants(ActiveCall call, Collection<? extends JidProvider> participants);

    /**
     * Issues a {@code trusted_contact} privacy token for the given peer.
     *
     * @apiNote
     * Vouches for a peer ahead of the call signalling exchange so the
     * relay's trusted-contact gate admits the outgoing offer. The
     * token carries the current unix timestamp and must be issued
     * before placing a call to a peer the server does not yet
     * recognise as trusted.
     *
     * @param peer the JID of the peer to vouch for; never {@code null}
     * @throws NullPointerException            if {@code peer} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the server rejects the IQ
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void issueTrustedContactToken(JidProvider peer);

    /**
     * Posts a {@link ScheduledCallCreationMessage} announcing a scheduled voice or video call inside the given chat.
     *
     * @apiNote
     * Drives the "Schedule call" affordance inside a chat: posts a
     * chat message carrying the chosen title, future timestamp, and
     * audio-vs-video flag that other participants can opt into. The
     * actual call is placed later via
     * {@link #startCall(JidProvider, CallOptions)} or
     * {@link #startGroupCall(JidProvider, Collection, boolean)}; the
     * announcement is purely an in-chat coordination message.
     *
     * @param chat        the chat to post the announcement in; never {@code null}
     * @param title       the human-readable title of the scheduled call; never {@code null}
     * @param scheduledAt the future {@link Instant} the call is scheduled for; never {@code null}
     * @param video       {@code true} for a video call, {@code false} for voice-only
     * @throws NullPointerException                           if any argument is {@code null}
     * @throws WhatsAppMessageException.Send.InvalidRecipient if the JID does not match a supported chat type
     */
    void sendScheduledCall(JidProvider chat, String title, Instant scheduledAt, boolean video);

    /**
     * Cancels a previously announced scheduled call.
     *
     * @apiNote
     * Drives the "Cancel scheduled call" affordance on a posted
     * scheduled-call announcement: posts a
     * {@link ScheduledCallEditMessage} with edit type
     * {@link ScheduledCallEditMessage.EditType#CANCEL} keyed by the
     * original creation message so participants see the announcement
     * marked as cancelled. The destination chat is taken from
     * {@link MessageKey#parentJid() creationKey.parentJid()}.
     *
     * @param creationKey the {@link MessageKey} of the original {@link ScheduledCallCreationMessage}; never {@code null}
     * @throws NullPointerException                           if {@code creationKey} is {@code null}
     * @throws NoSuchElementException                         if {@code creationKey} has no parent JID
     * @throws WhatsAppMessageException.Send.InvalidRecipient if the JID does not match a supported chat type
     */
    void cancelScheduledCall(MessageKey creationKey);

    /**
     * Resolves a shared call-link token and places a one-to-one call to the link's owning device.
     *
     * @apiNote
     * Drives the "Join via link" flow exposed by tapping a
     * {@code call.whatsapp.com/voice/<token>} or
     * {@code .../video/<token>} URL: looks up the link's creator JID,
     * then places an outgoing call to that creator with audio or
     * video selected by {@code media}. Returns
     * {@link Optional#empty()} when the relay accepts the link query
     * but reports no resolvable creator (revoked or expired link).
     *
     * @param token the call-link token, i.e. the path segment after {@code https://call.whatsapp.com/voice/} or {@code .../video/}; never {@code null}
     * @param media the media kind expected by the link; never {@code null}
     * @return the live {@link ActiveCall} session, or {@link Optional#empty()} when the link cannot be resolved to a callable creator
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejects the link query
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    Optional<ActiveCall> joinCallLink(String token, CallLinkMedia media);

    /**
     * Reconciles the local view of the Channels tab with the server.
     *
     * @apiNote
     * Use to redraw the Channels surface against an authoritative copy
     * of the newsletters this account follows. Every followed
     * newsletter is merged into {@link WhatsAppStore} keyed by its
     * JID, and {@link WhatsAppClientListener#onNewsletters} fires once
     * with the new authoritative set the first time the refresh
     * succeeds for the session.
     *
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void refreshNewsletters();

    /**
     * Reconciles the local view of the Groups section of the chat list
     * with the server.
     *
     * @apiNote
     * Use to redraw the groups surface against an authoritative copy
     * of the groups this account participates in. Every group is
     * merged into {@link WhatsAppStore} (chat record plus parsed
     * {@link GroupMetadata}), and
     * {@link WhatsAppClientListener#onGroups} fires once with the new
     * authoritative set.
     *
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void refreshGroups();

    /**
     * Queries the current invite code attached to the given group, without supplying a telemetry context tag.
     *
     * @apiNote
     * Single-argument convenience for
     * {@link #queryGroupInviteCode(JidProvider, String)} that omits
     * the telemetry context tag, matching the call shape used outside
     * the dedicated invite-link admin panel.
     *
     * @param group the target group JID; never {@code null}
     * @return the current invite-code scalar, or {@link Optional#empty()} when the relay returned no payload
     * @throws NullPointerException     if {@code group} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group or community JID
     * @see #queryGroupInviteCode(JidProvider, String)
     */
    Optional<String> queryGroupInviteCode(JidProvider group);

    /**
     * Revokes the current invite code for the given group and returns the freshly minted replacement.
     *
     * @apiNote
     * Drives the "Reset link" affordance on the group invite-link
     * sheet: the server rotates the invite code, and the new value is
     * returned so the caller can display or re-share it.
     *
     * @param group the target group JID; never {@code null}
     * @return the freshly minted invite code
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws IllegalArgumentException        if the JID is not a group or community JID
     * @throws NoSuchElementException          if the response carries no invite code
     * @throws WhatsAppServerRuntimeException  if the server rejects the IQ
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    String revokeGroupInviteCode(JidProvider group);

    /**
     * Joins a group using a public invite code parsed from a {@code chat.whatsapp.com/XYZ} link.
     *
     * @apiNote
     * Drives the "Join group" affordance on the invite-link landing
     * sheet. The server returns the joined group JID immediately
     * for open groups; for approval-gated groups it returns the
     * gated group's JID and creates a pending membership request.
     * The returned JID identifies the group in both cases.
     *
     * @param inviteCode the invite code (the path segment after {@code chat.whatsapp.com/}); never {@code null}
     * @return the JID of the joined group (or the gated group for which a request was created)
     * @throws NullPointerException            if {@code inviteCode} is {@code null}
     * @throws NoSuchElementException          if the server response is malformed
     * @throws WhatsAppServerRuntimeException  if the server rejects the IQ
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Jid joinGroupViaInvite(String inviteCode);

    /**
     * Fetches the full-resolution profile picture of a group through a public invite link, without joining the group first.
     *
     * @apiNote
     * Backs the icon shown on the invite-link landing sheet alongside
     * the group subject and member count: returns the picture
     * identity, MIME type, download URL, and direct-path tuple. The
     * call works even when the caller is not a member of the group
     * and does not modify local group membership.
     *
     * @param group      the JID of the group the invite refers to; must be a group JID
     * @param inviteCode the public invite code parsed from the {@code chat.whatsapp.com} URL; never {@code null}
     * @return the picture identity, type, download URL and direct-path tuple
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws NoSuchElementException          if the server reply carries no picture entry
     * @see #queryGroupInvitePicture(JidProvider, String, String)
     * @see #queryGroupInvitePicturePreview(JidProvider, String)
     */
    GroupInvitePicture queryGroupInvitePicture(JidProvider group, String inviteCode);

    /**
     * Fetches the full-resolution profile picture of a group through a public invite link, forwarding a server-side lookup hint.
     *
     * @apiNote
     * Three-argument variant of
     * {@link #queryGroupInvitePicture(JidProvider, String)} that
     * forwards a server-side lookup hint; pass {@code "url"} to
     * request a CDN download URL or {@code null} to ask for identity
     * metadata only.
     *
     * @param group      the JID of the group the invite refers to; must be a group JID
     * @param inviteCode the public invite code; never {@code null}
     * @param query      the {@code query} attribute value, or {@code null} to omit it
     * @return the picture identity, type, download URL and direct-path tuple
     * @throws NullPointerException            if {@code group} or {@code inviteCode} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws NoSuchElementException          if no picture entry is returned
     */
    GroupInvitePicture queryGroupInvitePicture(JidProvider group, String inviteCode, String query);

    /**
     * Fetches the low-resolution thumbnail of a group icon through a public invite link.
     *
     * @apiNote
     * Variant of {@link #queryGroupInvitePicture(JidProvider, String)}
     * that requests the preview thumbnail used in the invite-link
     * preview card rather than the full-resolution picture used on
     * the invite-link landing sheet.
     *
     * @param group      the JID of the group the invite refers to; must be a group JID
     * @param inviteCode the public invite code; never {@code null}
     * @return the picture identity, type, download URL and direct-path tuple
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws NoSuchElementException          if no picture entry is returned
     * @see #queryGroupInvitePicturePreview(JidProvider, String, String)
     * @see #queryGroupInvitePicture(JidProvider, String)
     */
    GroupInvitePicture queryGroupInvitePicturePreview(JidProvider group, String inviteCode);

    /**
     * Fetches the low-resolution thumbnail of a group icon through a public invite link, forwarding a server-side lookup hint.
     *
     * @apiNote
     * Three-argument variant of
     * {@link #queryGroupInvitePicturePreview(JidProvider, String)}
     * that forwards a server-side lookup hint.
     *
     * @param group      the JID of the group the invite refers to; must be a group JID
     * @param inviteCode the public invite code; never {@code null}
     * @param query      the {@code query} attribute value, or {@code null} to omit it
     * @return the picture identity, type, download URL and direct-path tuple
     * @throws NullPointerException            if {@code group} or {@code inviteCode} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws NoSuchElementException          if no picture entry is returned
     */
    GroupInvitePicture queryGroupInvitePicturePreview(JidProvider group, String inviteCode, String query);

    /**
     * Queries group metadata using a v4 invite received in-band via a {@link GroupInviteMessage}.
     *
     * @apiNote
     * Backs the preview rendered when a user receives an in-chat
     * group invite (the v4 flow, distinct from public invite-link
     * previews). The invite code, expiration, and inviting
     * administrator are forwarded to the server, which returns the
     * group's metadata.
     *
     * @param invite the in-band group invite, carrying the invitee JID, sender JID, expiration and invite code; never {@code null}
     * @return the parsed group metadata
     * @throws NullPointerException            if {@code invite} is {@code null}
     * @throws NoSuchElementException          if the server response is not a group subtree
     * @throws WhatsAppServerRuntimeException  if the server rejects the IQ
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    GroupMetadata queryGroupInfoByInvite(GroupInvite invite);

    /**
     * Accepts an in-band group invite (v4) sent by an administrator and joins the referenced group.
     *
     * @apiNote
     * Drives the "Accept" button on the in-chat group-invite preview.
     * The expiration timestamp and inviting administrator are
     * forwarded to the server. Both the immediate-join and
     * approval-pending paths resolve to the same group JID, so the
     * method returns nothing.
     *
     * @param group           the JID of the group being joined; must be a group or community JID
     * @param target          the inviting administrator JID; never {@code null}
     * @param inviteTimestamp the invite expiration time; never {@code null}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if {@code group} is not a group or community JID
     * @throws WhatsAppServerRuntimeException  if the server rejects the IQ
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void sendGroupInvite(JidProvider group, JidProvider target, Instant inviteTimestamp);

    /**
     * Queries the list of pending join-request applicants for an approval-gated group.
     *
     * @apiNote
     * Backs the "Pending requests" admin sheet shown for
     * approval-gated groups: returns the applicant JIDs in server
     * order and emits the corresponding view-pending-participants
     * telemetry event.
     *
     * @param group the target group JID; never {@code null}
     * @return the pending applicants in server order
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws IllegalArgumentException        if the JID is not a group or community JID
     * @throws WhatsAppServerRuntimeException  if the server rejects the IQ
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    List<Jid> queryGroupJoinRequests(JidProvider group);

    /**
     * Approves a pending join request and admits the applicant into the group.
     *
     * @apiNote
     * Drives the "Approve" affordance on a pending-request entry in
     * the group admin sheet: admits the applicant into the group and
     * emits the corresponding membership-request-approve telemetry
     * event carrying the success flag and server response time.
     *
     * @param group     the target group JID; never {@code null}
     * @param applicant the applicant JID to admit; never {@code null}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if the JID is not a group or community JID
     * @throws WhatsAppServerRuntimeException  if the server rejects the IQ
     * @throws WhatsAppSessionException.Closed if the socket is closed
     * @see #rejectGroupJoinRequest(JidProvider, JidProvider)
     */
    void acceptGroupJoinRequest(JidProvider group, JidProvider applicant);

    /**
     * Rejects a pending join request and keeps the applicant out of the group.
     *
     * @apiNote
     * Drives the "Reject" affordance on a pending-request entry in
     * the group admin sheet: keeps the applicant out of the group and
     * emits the corresponding membership-request-reject telemetry
     * event carrying the success flag and server response time.
     *
     * @param group     the target group JID; never {@code null}
     * @param applicant the applicant JID to reject; never {@code null}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if the JID is not a group or community JID
     * @throws WhatsAppServerRuntimeException  if the server rejects the IQ
     * @throws WhatsAppSessionException.Closed if the socket is closed
     * @see #acceptGroupJoinRequest(JidProvider, JidProvider)
     */
    void rejectGroupJoinRequest(JidProvider group, JidProvider applicant);

    /**
     * Encrypts and dispatches a peer protocol message to one of the current account's own devices.
     *
     * @apiNote
     * Used for the device-to-device traffic that never reaches another
     * user: app-state-sync key shares, sender-key resets,
     * fatal-exception notifications, and other peer protocol payloads
     * exchanged between a user's linked devices. The destination
     * {@code chatJid} is typically the primary device JID owned by
     * this session.
     *
     * @param chatJid  the destination device JID; never {@code null}
     * @param response the fully-populated peer message to encrypt and send; never {@code null}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void sendPeerMessage(JidProvider chatJid, ChatMessageInfo response);

    /**
     * Resolves a batch of phone-number JIDs to whether each one corresponds to a registered WhatsApp account.
     *
     * @apiNote
     * Backs the in-product contact picker's "is on WhatsApp" probe:
     * batches every supplied JID into one server lookup and reports,
     * per input, whether the corresponding phone number is registered
     * to a WhatsApp account.
     *
     * @param phoneNumbers the user JIDs to look up; never {@code null}
     * @return an unmodifiable map from the server-echoed phone JID to whether that user is registered
     * @throws NullPointerException           if {@code phoneNumbers} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay rejects the USync query
     */
    Map<Jid, Boolean> hasWhatsapp(Collection<? extends JidProvider> phoneNumbers);

    /**
     * Resolves a single phone-number JID to whether it corresponds to a registered WhatsApp account.
     *
     * @apiNote
     * Single-input convenience over {@link #hasWhatsapp(Collection)}
     * that returns {@code true} when any returned entry is positive
     * and {@code false} otherwise, including the case where the JID
     * does not map to a phone number.
     *
     * @param phone the user JID to look up; never {@code null}
     * @return {@code true} when the server reports the phone as registered, {@code false} otherwise
     * @throws NullPointerException           if {@code phone} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay rejects the USync query
     */
    boolean hasWhatsapp(JidProvider phone);

    /**
     * Queries the push name associated with the given user JID.
     *
     * @apiNote
     * Backs every UI surface that needs to render a contact's display
     * name when the local store has nothing on file: consults the
     * cached {@link Contact#chosenName() chosen name} first and only
     * falls back to a remote lookup when no usable cached value
     * exists. The remote lookup returns the push name the contact has
     * chosen to publish.
     *
     * @param jid the user JID to resolve; never {@code null}
     * @return the resolved push name, or {@link Optional#empty()} when neither the store nor the server knows one
     * @throws NullPointerException           if {@code jid} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay rejects the USync query
     */
    Optional<String> queryName(JidProvider jid);

    /**
     * Uploads a batch of phone-number contacts and returns the phone-to-JID mapping the server resolves for them.
     *
     * @apiNote
     * Backs the "Add to WhatsApp from system contacts" flow: extracts
     * the default cell numbers from every supplied contact card,
     * batches them into a background contact-sync lookup, and returns
     * only the entries the server acknowledges as registered. The
     * returned map is keyed by the phone-number JID sent and valued by
     * the server-normalised JID (the two differ only when the server
     * rewrites the identifier, for example during LID migration).
     *
     * @param contacts the contact cards to synchronise; never {@code null}
     * @return an unmodifiable map from phone-number JID to the server-returned JID for every successfully resolved contact
     * @throws NullPointerException           if {@code contacts} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay rejects the USync query
     */
    Map<Jid, Jid> syncContacts(Collection<ContactCard> contacts);

    /**
     * Queries the full metadata envelope for a newsletter with the supplied viewer role.
     *
     * @apiNote
     * Two-argument convenience for
     * {@link #queryNewsletter(JidProvider, NewsletterViewerRole, boolean)}
     * that passes {@code false} for {@code dehydrated}, returning
     * full thread metadata plus viewer-scoped settings (the default
     * path used when opening a newsletter from the chat list).
     *
     * @param newsletterJid the newsletter JID to query; never {@code null}
     * @param role          the viewer role to assert during the query, or {@code null} to omit the {@code view_role} field
     * @return the parsed {@link Newsletter}, or {@link Optional#empty()} when the response carries no usable payload
     * @throws NullPointerException            if {@code newsletterJid} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     * @see #queryNewsletter(JidProvider, NewsletterViewerRole, boolean)
     */
    Optional<Newsletter> queryNewsletter(JidProvider newsletterJid, NewsletterViewerRole role);

    /**
     * Queries metadata for a single newsletter, choosing between the full and the lightweight projection.
     *
     * @apiNote
     * Backs both the channel header opened on a newsletter and the
     * lightweight background refresh that keeps subscriber counts
     * current. When {@code dehydrated} is {@code false}, the server
     * returns full thread metadata plus the viewer-scoped settings;
     * when {@code true}, it returns only the subscriber count,
     * verification flag, reaction-codes setting, and any associated
     * WAMO subscription plan id. The result is folded into the
     * store-resident newsletter so subsequent reads observe it.
     *
     * @param newsletterJid the newsletter JID to query; never {@code null}
     * @param role          the viewer role to assert during the query, or {@code null} to omit the {@code view_role} field
     * @param dehydrated    {@code true} for the lightweight projection, {@code false} for the full one
     * @return the parsed {@link Newsletter}, or {@link Optional#empty()} when the response carries no usable payload
     * @throws NullPointerException            if {@code newsletterJid} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<Newsletter> queryNewsletter(JidProvider newsletterJid, NewsletterViewerRole role, boolean dehydrated);

    /**
     * Creates a brand-new newsletter owned by this account.
     *
     * @apiNote
     * Drives the "Create channel" flow in the channels surface:
     * publishes a new newsletter with the supplied name, optional
     * description, and optional JPEG picture bytes. On success the
     * server returns the freshly allocated newsletter id, which is
     * resolved to a local {@link Newsletter} and inserted into the
     * store.
     *
     * @param create the new newsletter content (name, optional description, optional picture bytes); never {@code null}
     * @return the newly created {@link Newsletter}
     * @throws NullPointerException            if {@code create} is {@code null}
     * @throws NoSuchElementException          if the server response does not contain a newsletter id
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Newsletter createNewsletter(NewsletterCreate create);

    /**
     * Edits the mutable metadata (name, description, picture) of a newsletter owned by this account.
     *
     * @apiNote
     * Drives the "Edit channel info" affordance on the newsletter
     * admin sheet. Each of name, description, and picture is sent
     * only when the corresponding field on
     * {@link NewsletterMetadataEdit} is present; absent fields are
     * omitted from the request so the server leaves them untouched.
     *
     * @param edit the newsletter JID together with the fields to edit; never {@code null}
     * @throws NullPointerException            if {@code edit} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editNewsletterMetadata(NewsletterMetadataEdit edit);

    /**
     * Permanently deletes a newsletter owned by this account.
     *
     * @apiNote
     * Drives the "Delete channel" affordance on the newsletter admin
     * sheet: removes the newsletter both server-side and from the
     * local store. Returns the pre-deletion snapshot, or
     * {@link Optional#empty()} when the newsletter is unknown
     * locally.
     *
     * @param newsletter the newsletter JID to delete; never {@code null}
     * @return the removed {@link Newsletter} as it existed before deletion, or {@link Optional#empty()} when the newsletter was unknown locally
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<Newsletter> deleteNewsletter(JidProvider newsletter);

    /**
     * Subscribes this account to the given newsletter so future updates are delivered.
     *
     * @apiNote
     * Drives the "Follow channel" button on a newsletter header:
     * subscribes this account to the newsletter and registers it in
     * the local store so subsequent lookups succeed.
     *
     * @param newsletter the newsletter JID to follow; never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void joinNewsletter(JidProvider newsletter);

    /**
     * Unsubscribes this account from the given newsletter so updates stop being delivered.
     *
     * @apiNote
     * Drives the "Unfollow channel" affordance on a newsletter
     * header: unsubscribes this account and removes the newsletter
     * from the local store on success.
     *
     * @param newsletter the newsletter JID to unfollow; never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void leaveNewsletter(JidProvider newsletter);

    /**
     * Toggles the mute state on admin-activity notifications for the given newsletter.
     *
     * @apiNote
     * Drives the "Mute admin activity" toggle on the newsletter
     * settings sheet: silences notifications about admin-only
     * activity on this newsletter for the authenticated user.
     *
     * @param newsletter the newsletter JID; never {@code null}
     * @param mute       {@code true} to mute, {@code false} to unmute
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void muteNewsletter(JidProvider newsletter, boolean mute);

    /**
     * Posts, updates or revokes an emoji reaction on a newsletter message.
     *
     * @apiNote
     * Drives the reaction picker on a newsletter message: posting a
     * non-empty {@code emoji} sets or replaces the reaction; passing
     * {@code null} or an empty string revokes the existing reaction.
     *
     * @param newsletter      the newsletter JID hosting the message; never {@code null}
     * @param serverMessageId the target server message id; never {@code null}
     * @param emoji           the reaction emoji to set, or {@code null}/empty to revoke the existing reaction
     * @throws NullPointerException            if {@code newsletter} or {@code serverMessageId} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void reactToNewsletterMessage(JidProvider newsletter, String serverMessageId, String emoji);

    /**
     * Admin-revokes a message previously published on a newsletter owned by this account.
     *
     * @apiNote
     * Drives the "Delete for everyone" affordance on a newsletter
     * message from the channel admin context: revokes the published
     * message identified by {@code serverMessageId} so followers no
     * longer see it.
     *
     * @param newsletter      the newsletter JID hosting the message; never {@code null}
     * @param serverMessageId the target server message id; never {@code null}
     * @throws NullPointerException            if {@code newsletter} or {@code serverMessageId} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void revokeNewsletterMessage(JidProvider newsletter, String serverMessageId);

    /**
     * Accepts a pending newsletter admin invitation addressed to this account.
     *
     * @apiNote
     * Drives the "Accept admin invite" affordance on the newsletter
     * admin-invite modal: this account becomes an administrator of
     * the newsletter referenced by the supplied JID.
     *
     * @param newsletter the newsletter JID whose pending admin invite is being accepted; never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void acceptNewsletterAdminInvite(JidProvider newsletter);

    /**
     * Revokes a pending admin invitation previously issued by this account for the given newsletter.
     *
     * @apiNote
     * Drives the "Cancel admin invite" affordance on the newsletter
     * admin-invite management surface: revokes a pending admin
     * invitation previously issued by this account against the
     * supplied invitee. Only the channel owner may issue the call;
     * the server rejects the request from non-owners. Pairs with
     * {@link #createNewsletterAdminInvite(JidProvider, JidProvider)}
     * and {@link #queryNewsletterPendingInvites(JidProvider)}.
     *
     * @param newsletter the newsletter JID whose pending invite is being revoked; never {@code null}
     * @param admin      the JID of the user whose pending invite is being revoked; never {@code null}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     * @see #createNewsletterAdminInvite(JidProvider, JidProvider)
     * @see #queryNewsletterPendingInvites(JidProvider)
     */
    void revokeNewsletterAdminInvite(JidProvider newsletter, JidProvider admin);

    /**
     * Demotes an existing newsletter administrator back to a regular follower.
     *
     * @apiNote
     * Drives the "Dismiss as admin" affordance and the admin's own
     * "Step down" affordance on the newsletter admin sheet: the
     * supplied admin's local membership role drops back to
     * {@code Subscriber} on success. The owner may demote any admin;
     * an admin may demote themselves.
     *
     * @param newsletter the newsletter JID; never {@code null}
     * @param admin      the JID of the admin being demoted; never {@code null}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void demoteNewsletterAdmin(JidProvider newsletter, JidProvider admin);

    /**
     * Updates the reaction policy for a newsletter owned by this account.
     *
     * @apiNote
     * Drives the "Reactions" picker on the newsletter settings sheet
     * (allowed reaction code set, all-emoji vs none, etc.): the new
     * reaction policy is rolled into a regular newsletter metadata
     * edit so the server applies it alongside any other pending
     * changes.
     *
     * @param newsletter the newsletter JID whose reaction policy is being changed; never {@code null}
     * @param setting    the reaction policy to install; never {@code null}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editNewsletterReactionSetting(JidProvider newsletter, NewsletterReactionSettings setting);

    /**
     * Queries the capability flags granted to this account on the given newsletter.
     *
     * @apiNote
     * Drives the per-channel admin UI gating in the newsletter admin
     * surfaces. Capabilities are the typed feature gates the relay
     * turns on for a given channel (insights dashboards, polls and
     * quizzes, music attachments, sticker pack sharing, the
     * channel-status producer API, etc.). They determine which
     * affordances embedders can surface for the authenticated admin
     * and which operations the relay will accept for this channel.
     *
     * @param newsletter the newsletter JID whose admin capabilities are being queried; never {@code null}
     * @return the capabilities granted to this account on the channel; never {@code null}
     * @throws NullPointerException           if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterCapability> queryNewsletterAdminCapabilities(JidProvider newsletter);

    /**
     * Queries the number of administrators currently configured on the given newsletter.
     *
     * @apiNote
     * Backs the "X admins" badge surfaced on the channel info screen.
     *
     * @param newsletter the newsletter JID; never {@code null}
     * @return an {@link OptionalLong} carrying the admin count, or empty when the relay did not report one
     * @throws NullPointerException           if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    OptionalLong queryNewsletterAdminInfo(JidProvider newsletter);

    /**
     * Queries a page of followers for the given newsletter.
     *
     * @apiNote
     * Backs the follower roster surface channel admins use to inspect
     * their subscriber base and identify who currently holds an admin
     * role. Each {@link NewsletterFollower} carries the follower JID,
     * the optional push-name and disclosed phone number, the
     * channel-relative role (subscriber, admin, owner), and the
     * moment at which the follow happened. Admins and owners are
     * sorted ahead of subscribers in the returned page.
     *
     * @param newsletter the newsletter JID; never {@code null}
     * @param count      the requested follower page size; the caller should clamp it against the server maximum
     * @return the followers reported on this page, in server order; never {@code null}
     * @throws NullPointerException           if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterFollower> queryNewsletterFollowers(JidProvider newsletter, int count);

    /**
     * Queries the list of pending administrator invitations attached to the given newsletter.
     *
     * @apiNote
     * Backs the "Pending invites" list on the newsletter admin-invite
     * management surface. Newsletter owners issue admin invitations
     * via {@link #createNewsletterAdminInvite(JidProvider, JidProvider)};
     * this query returns those invitations that have been issued but
     * not yet accepted via
     * {@link #acceptNewsletterAdminInvite(JidProvider)} or revoked via
     * {@link #revokeNewsletterAdminInvite(JidProvider, JidProvider)}.
     *
     * @param newsletter the newsletter JID whose pending admin invitations are being listed; never {@code null}
     * @return the pending invitations, in server order; never {@code null}
     * @throws NullPointerException           if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterAdminInvite> queryNewsletterPendingInvites(JidProvider newsletter);

    /**
     * Fetches the first page of the given newsletter directory slice with no filters and the relay's default page size.
     *
     * @apiNote
     * Convenience overload of
     * {@link #queryNewsletterDirectoryList(NewsletterDirectoryListView, List, List, Long, String, boolean)};
     * use it as the entry point into the explore tab when the caller
     * does not yet need country or category narrowing.
     *
     * @param view the directory slice to query; never {@code null}
     * @return the first page of results
     * @throws NullPointerException           if {@code view} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view);

    /**
     * Fetches a subsequent page of the given newsletter directory slice with no filters.
     *
     * @apiNote
     * Convenience overload of
     * {@link #queryNewsletterDirectoryList(NewsletterDirectoryListView, List, List, Long, String, boolean)};
     * pass the {@link NewsletterDirectoryPage#nextCursor()} returned
     * by the previous page as {@code cursorToken} to walk the explore
     * tab forward.
     *
     * @param view        the directory slice to query; never {@code null}
     * @param cursorToken the pagination cursor returned by a previous page
     * @return the requested page of results
     * @throws NullPointerException           if {@code view} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view, String cursorToken);

    /**
     * Queries a paginated page of the newsletter directory filtered by view and optional country/category filters.
     *
     * @apiNote
     * Powers the explore tab of the newsletter directory: the
     * {@link NewsletterDirectoryListView} selects one of
     * {@code RECOMMENDED}, {@code NEW}, {@code POPULAR},
     * {@code FEATURED}, or {@code TRENDING}, and the optional filters
     * narrow by country and category. The returned page bundles the
     * directory entries together with a forward-only cursor that
     * callers feed back to fetch the following page; pass
     * {@code null} as {@code cursorToken} on the first call.
     *
     * @param view                the directory slice to query; never {@code null}
     * @param countryCodes        the ISO country codes to filter by, or {@code null} for no country filter
     * @param categories          the upper-case category wire strings to filter by (e.g. {@code "BUSINESS"}), or {@code null} for no category filter
     * @param limit               the page size, or {@code null} to let the relay apply its default
     * @param cursorToken         the start cursor for pagination, or {@code null} on the first page
     * @param fetchStatusMetadata {@code true} to request the optional {@code status_metadata} sub-selection
     * @return the directory page bundling the entries and the next-page cursor; never {@code null}
     * @throws NullPointerException           if {@code view} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view, List<String> countryCodes, List<String> categories, Long limit, String cursorToken, boolean fetchStatusMetadata);

    /**
     * Searches the newsletter directory for channels matching the given free-text query.
     *
     * @apiNote
     * Convenience overload of
     * {@link #searchNewsletterDirectory(String, List, Long, String, boolean)}
     * that drives the directory search box with no category filter
     * and the relay's default page size.
     *
     * @param searchText the free-text search hint
     * @return the first page of matching results
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterDirectoryPage searchNewsletterDirectory(String searchText);

    /**
     * Searches the newsletter directory and narrows the result set to the given editorial categories.
     *
     * @apiNote
     * Convenience overload of
     * {@link #searchNewsletterDirectory(String, List, Long, String, boolean)}
     * that retains the relay's default page size and skips the
     * status-metadata sub-selection.
     *
     * @param searchText the free-text search hint
     * @param categories the upper-case category wire strings to filter by, or {@code null} for no category filter
     * @return the first page of matching results
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterDirectoryPage searchNewsletterDirectory(String searchText, List<String> categories);

    /**
     * Searches the newsletter directory with full control over filters, paging and status-metadata projection.
     *
     * @apiNote
     * Drives the directory search box on the newsletter explore tab.
     * Pagination follows the same cursor protocol as
     * {@link #queryNewsletterDirectoryList(NewsletterDirectoryListView, List, List, Long, String, boolean)};
     * pass {@code null} on the initial call and feed back the cursor
     * returned by {@link NewsletterDirectoryPage#nextCursor()} on
     * subsequent calls.
     *
     * @param searchText          the free-text search query, or {@code null}
     * @param categories          the upper-case category wire strings to filter by, or {@code null}
     * @param limit               the page size, or {@code null} to let the relay apply its default
     * @param cursorToken         the pagination cursor, or {@code null} on the first page
     * @param fetchStatusMetadata {@code true} to request the optional {@code status_metadata} sub-selection
     * @return the directory page bundling the entries and the next-page cursor; never {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterDirectoryPage searchNewsletterDirectory(String searchText, List<String> categories, Long limit, String cursorToken, boolean fetchStatusMetadata);

    /**
     * Queries the newsletter directory landing categories together with a preview of featured channels for each category.
     *
     * @apiNote
     * Powers the directory landing surface: each category is shown
     * together with a handful of featured newsletters as a visual
     * preview before the user drills down. The {@code input} argument
     * is threaded opaquely through to the server so callers can pass
     * a serialised filter context (per-category limit, country code,
     * category set) without this method having to know the wire shape.
     *
     * @param input the serialised input variable forwarded to the relay; may be {@code null}
     * @return the categories surfaced on the directory landing screen, each carrying a featured-newsletter preview; never {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterDirectoryCategory> queryNewsletterDirectoryCategoriesPreview(String input);

    /**
     * Queries the recommended-newsletters feed personalised for this account using the relay's defaults.
     *
     * @apiNote
     * Convenience overload of
     * {@link #queryRecommendedNewsletters(Long, List, boolean)} that
     * lets the relay choose the page size, omits the country scope
     * and skips the {@code status_metadata} sub-selection.
     *
     * @return the directory page bundling the recommended entries and the next-page cursor; never {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterDirectoryPage queryRecommendedNewsletters();

    /**
     * Queries the recommended-newsletters feed personalised for this account.
     *
     * @apiNote
     * Powers the "Recommended for you" rail on the newsletter explore
     * tab. The recommendation engine runs entirely server-side; the
     * client only supplies the page size and an optional country
     * scope.
     *
     * @param limit               the maximum number of recommended newsletters to return, or {@code null} to let the relay apply its default page size
     * @param countryCodes        the ISO country codes used to scope the recommendation, or {@code null} to omit the field
     * @param fetchStatusMetadata {@code true} to request the optional {@code status_metadata} sub-selection
     * @return the directory page bundling the recommended entries and the next-page cursor; never {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterDirectoryPage queryRecommendedNewsletters(Long limit, List<String> countryCodes, boolean fetchStatusMetadata);

    /**
     * Queries newsletters similar to the given seed using the relay's defaults.
     *
     * @apiNote
     * Convenience overload of
     * {@link #querySimilarNewsletters(JidProvider, Long, List, boolean)}
     * that lets the relay choose the page size, omits the country
     * scope and skips the {@code status_metadata} sub-selection.
     *
     * @param newsletter the seed newsletter JID; never {@code null}
     * @return the similar newsletters reported by the relay
     * @throws NullPointerException           if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterDirectoryEntry> querySimilarNewsletters(JidProvider newsletter);

    /**
     * Queries newsletters similar to the given seed newsletter.
     *
     * @apiNote
     * Powers the "you might also like" rail on a newsletter channel
     * page. The relay computes similarity entirely server-side; the
     * client only supplies the seed JID, the page size, and an
     * optional country scope.
     *
     * @param newsletter          the seed newsletter JID; never {@code null}
     * @param limit               the maximum number of similar newsletters to return, or {@code null} to let the relay apply its default
     * @param countryCodes        the ISO country codes used to scope the recommendation, or {@code null} to omit the field
     * @param fetchStatusMetadata {@code true} to request the optional {@code status_metadata} sub-selection
     * @return the similar newsletters reported by the relay; never {@code null}
     * @throws NullPointerException           if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterDirectoryEntry> querySimilarNewsletters(JidProvider newsletter, Long limit, List<String> countryCodes, boolean fetchStatusMetadata);

    /**
     * Queries the server-side link preview for a URL pasted into a newsletter compose surface.
     *
     * @apiNote
     * Drives the inline link-preview chip that appears under a URL
     * pasted into the newsletter compose box. Newsletter messages
     * cannot use the regular client-side link-preview pipeline
     * because the recipient anonymity guarantee forbids the client
     * from fetching the target URL directly; instead, the server
     * acts as a trusted proxy and returns title, description,
     * dimensions, and an encrypted thumbnail handle that can be
     * downloaded through the standard media pipeline.
     *
     * @param url the URL to unfurl; never {@code null}
     * @return an {@link Optional} carrying the unfurled preview, or {@link Optional#empty()} when the relay returned no payload
     * @throws NullPointerException if {@code url} is {@code null}
     */
    Optional<NewsletterLinkPreview> queryNewsletterLinkPreview(String url);

    /**
     * Checks whether the given URL belongs to a domain whose link previews may be rendered inside newsletter messages.
     *
     * @apiNote
     * Drives the compose-time warning shown when a pasted URL points
     * to a domain that newsletter integrity has blocked from preview
     * unfurling. Compose surfaces call this query before publishing
     * so they can warn the user when a disallowed domain would
     * otherwise be silently stripped of its preview by the server.
     *
     * @param url the URL whose domain is being validated; never {@code null}
     * @return {@code true} when the relay reports the domain as previewable, {@code false} otherwise
     * @throws NullPointerException if {@code url} is {@code null}
     */
    boolean isNewsletterDomainPreviewable(String url);

    /**
     * Queries the per-emoji list of senders that reacted to the given newsletter message.
     *
     * @apiNote
     * Backs the "who reacted with X" surface inside a newsletter
     * message details panel. Each reaction code is reported together
     * with its sender roster (JID plus optional profile picture
     * direct path); the query returns reactions for every code
     * present on the message, so callers can filter to a single code
     * client-side.
     *
     * @param newsletter      the newsletter JID; never {@code null}
     * @param serverMessageId the server-assigned message id
     * @return the per-emoji reactor list, in server order; never {@code null}
     * @throws NullPointerException           if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterReactor> queryNewsletterMessageReactionSenders(JidProvider newsletter, long serverMessageId);

    /**
     * Queries the list of voters on a newsletter poll across every option.
     *
     * @apiNote
     * Convenience overload of
     * {@link #queryNewsletterPollVoters(JidProvider, long, long, String)}
     * that forwards a {@code null} option hash, so the relay returns
     * voters bucketed by every option of the poll.
     *
     * @param newsletter      the newsletter JID hosting the poll; never {@code null}
     * @param serverMessageId the poll message id
     * @param limit           the maximum voter edges per option
     * @return the per-option voter groups
     * @throws NullPointerException           if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterPollVoter> queryNewsletterPollVoters(JidProvider newsletter, long serverMessageId, long limit);

    /**
     * Queries the list of voters on a newsletter poll, optionally narrowed to a single option.
     *
     * @apiNote
     * Powers the per-option voter breakdown on a newsletter poll
     * message: newsletter polls track voters per option through a
     * base64-encoded option hash. Passing a non-{@code null}
     * {@code voteHash} narrows the response to senders of that
     * specific option; passing {@code null} returns voters across
     * every option of the poll.
     *
     * @param newsletter      the newsletter JID hosting the poll; never {@code null}
     * @param serverMessageId the server-assigned id of the poll message
     * @param limit           the maximum number of voter edges to return
     * @param voteHash        the base64-encoded option hash to filter on, or {@code null} to return voters across every option
     * @return the per-option voter groups, in server order; never {@code null}
     * @throws NullPointerException           if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterPollVoter> queryNewsletterPollVoters(JidProvider newsletter, long serverMessageId, long limit, String voteHash);

    /**
     * Transfers ownership of the given newsletter to the supplied user.
     *
     * @apiNote
     * Drives the "Transfer ownership" affordance on the newsletter
     * owner settings sheet: only the current owner may initiate the
     * transfer, and the target user must already have a registered
     * WhatsApp account. After the transfer succeeds the original
     * owner is demoted to admin and the supplied user becomes the
     * sole owner.
     *
     * @param newsletter the newsletter JID whose ownership is being transferred; never {@code null}
     * @param newOwner   the JID of the user receiving ownership; never {@code null}
     * @throws NullPointerException           if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    void transferNewsletterOwnership(JidProvider newsletter, JidProvider newOwner);

    /**
     * Issues a newsletter administrator invitation to the given user.
     *
     * @apiNote
     * Drives the "Invite as admin" affordance on the newsletter
     * admin-invite management surface: the owner of a newsletter
     * invites another user to become a co-administrator. The
     * invitation is recorded server-side and surfaces to the invitee
     * through {@link #queryNewsletterPendingInvites(JidProvider)};
     * the invitee accepts via
     * {@link #acceptNewsletterAdminInvite(JidProvider)} or the
     * inviter revokes via
     * {@link #revokeNewsletterAdminInvite(JidProvider, JidProvider)}.
     *
     * @param newsletter the newsletter JID whose admin roster is being expanded; never {@code null}
     * @param invitee    the JID of the user being invited; never {@code null}
     * @return the persisted invitation, carrying the invitee JID and the expiration instant; never {@code null}
     * @throws NullPointerException           if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterAdminInvite createNewsletterAdminInvite(JidProvider newsletter, JidProvider invitee);

    /**
     * Attaches the legally-required paid-partnership disclosure label to a newsletter message.
     *
     * @apiNote
     * Drives the "Mark as paid partnership" affordance on a newsletter
     * post owned by this account: monetised creators must disclose
     * paid partnerships under EU DSA Article 26, and this call flags
     * an existing message so the server renders the disclosure badge
     * alongside it.
     *
     * @param newsletter      the newsletter JID hosting the message being labelled; never {@code null}
     * @param serverMessageId the server-assigned message id of the post being labelled; never {@code null}
     * @throws NullPointerException           if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    void addNewsletterPaidPartnershipLabel(JidProvider newsletter, String serverMessageId);

    /**
     * Logs a batch of newsletter exposure events for attribution and directory-ranking purposes.
     *
     * @apiNote
     * Feeds the server-side newsletter ranking signal: while the user
     * browses newsletters the client records lightweight exposure
     * entries (newsletter JID plus capability flag) and flushes them
     * to the relay through this call. The backend uses the exposure
     * signal to improve directory ranking; the client treats the
     * response as a fire-and-forget acknowledgement.
     *
     * @param exposures the batch of exposure entries; never {@code null}; an empty list still issues the no-op call
     * @throws NullPointerException            if {@code exposures} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    void logNewsletterExposures(List<NewsletterExposure> exposures);

    /**
     * Files an appeal against an enforcement decision recorded on the given report.
     *
     * @apiNote
     * Drives the "Appeal" affordance surfaced to newsletter admins
     * and channel owners after a server-side enforcement action (ban,
     * content removal, geographic suspension): {@code reason} carries
     * the free-form justification entered by the user and
     * {@code reportId} identifies the underlying decision being
     * contested.
     *
     * @param reason   the free-form appeal justification; never {@code null}
     * @param reportId the identifier of the report whose enforcement decision is being contested; never {@code null}
     * @return the persisted appeal record; never {@code null}
     * @throws NullPointerException           if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload or omitted the appeal record
     */
    NewsletterReportAppeal createNewsletterReportAppeal(String reason, String reportId);

    /**
     * Queries the active enforcement actions recorded against the given newsletter.
     *
     * @apiNote
     * Backs the channel enforcement panel newsletter admins inspect
     * to view pending or resolved actions taken on their channel.
     * Enforcements come in four categories (profile-picture
     * deletions, suspensions, violating messages, geographic
     * suspensions), each carrying the violation metadata, the appeal
     * state, and the localised explanatory copy.
     *
     * @param newsletter the newsletter JID; never {@code null}
     * @param locale     the BCP-47 locale tag used to localise the explanatory copy, or {@code null} to fall back to the relay's default
     * @return the enforcements taken on this channel, flattened into a single list; never {@code null}
     * @throws NullPointerException           if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterEnforcement> queryNewsletterEnforcements(JidProvider newsletter, String locale);

    /**
     * Queries the list of moderation reports filed against newsletters owned by this account.
     *
     * @apiNote
     * Backs the creator self-management surface where the owner
     * inspects which reports have been filed, their current status,
     * and any associated appeal records. The relay returns every
     * report filed against newsletters this account has authority
     * over; there is no per-newsletter scoping variable.
     *
     * @return the moderation reports filed against newsletters this account has authority over; never {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterReport> queryNewsletterReports();

    /**
     * Queries the analytics insights for the given newsletter.
     *
     * @apiNote
     * Backs the newsletter admin analytics surface (views, reactions,
     * follower-growth deltas). Passing {@code null} for
     * {@code metrics} requests every metric the relay is willing to
     * expose to this caller. Each returned metric also carries the
     * last-update time and freshness status reported for the insights
     * snapshot.
     *
     * @param newsletter the newsletter JID; never {@code null}
     * @param metrics    the list of metric identifiers to fetch values for, or {@code null} to omit the field and let the relay pick the default metric set
     * @return the metrics reported by the relay, in server order; never {@code null}
     * @throws NullPointerException           if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterInsightMetric> queryNewsletterInsights(JidProvider newsletter, List<String> metrics);

    /**
     * Initiates a Data Subject Request for the given entity.
     *
     * @apiNote
     * Drives the GDPR-mandated download/deletion flow surfaced to
     * newsletter admins. Although it reads like a "get info"
     * operation, the relay treats it as a mutation because submitting
     * the request kicks off a backend export or deletion job. The
     * returned reference number lets the caller later check progress
     * through privacy-tooling surfaces.
     *
     * @param entityId the identifier of the entity whose DSR is being initiated; never {@code null}
     * @return the relay-assigned reference number; never {@code null}
     * @throws NullPointerException           if {@code entityId} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload or omitted the reference number
     */
    String queryNewsletterDsbInfo(String entityId);

    /**
     * Queries the "about" status text of the given user.
     *
     * @apiNote
     * Backs the biographical line surfaced on a contact's profile
     * (for example {@code "At the movies"} or {@code "Busy"}); this
     * is the persistent profile line, distinct from the ephemeral
     * text status shown on the status tab. The method picks the
     * canonical transport at runtime so callers do not need to know
     * which path was used:
     * <ul>
     *   <li>If the target is a LID JID, or the
     *       {@code mex_usync_about_status} AB prop is enabled, the
     *       query routes through the USync about-status projection.</li>
     *   <li>Otherwise it routes through the GraphQL about query.</li>
     * </ul>
     *
     * @param jid the user JID whose about text should be fetched
     * @return the about text when the server responds with a non-empty {@code <status>} element; {@link Optional#empty()} otherwise
     * @throws NullPointerException            if {@code jid} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<String> queryAbout(JidProvider jid);

    /**
     * Queries the WhatsApp username currently claimed by the authenticated account.
     *
     * @apiNote
     * Drives the "Your username" row on the account-settings sheet.
     * Usernames complement phone numbers as an alternative identifier.
     * The server also returns the registration state (PENDING or
     * ACTIVE) and the recovery-PIN hash; this convenience helper
     * projects only the username because the auxiliary fields are
     * consumed internally by the sign-up surface.
     *
     * @return the claimed username, or {@link Optional#empty()} when the account has no username set or the relay returned no payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<String> queryUsername();

    /**
     * Claims, updates or probes the WhatsApp username for the authenticated account.
     *
     * @apiNote
     * Drives the "Set username" / "Change username" affordance on the
     * account-settings sheet: passing a non-empty value assigns the
     * username to the account. Passing a {@code null} or empty value
     * forwards an empty payload, which the relay treats as a probe
     * that exercises the mutation without committing a username.
     *
     * @param username the candidate username to claim, or {@code null}/empty to forward an empty variables payload
     * @return {@code true} when the relay reports {@code "SUCCESS"}, {@code false} when it answers with any other status token or no payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    boolean editUsername(String username);

    /**
     * Sets or rotates the recovery PIN attached to the WhatsApp username claimed by the authenticated account.
     *
     * @apiNote
     * Drives the "Username PIN" rotation surface: the recovery PIN
     * backs the username so the account can be regained if the
     * registered phone number becomes unavailable. Passing a
     * {@code null} value clears the PIN.
     *
     * @param pin the new recovery PIN to register, or {@code null} to clear the PIN
     * @throws WhatsAppServerRuntimeException  if the relay rejects the mutation, returns no payload or replies with any token other than {@code "SUCCESS"}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    void editUsernameRecoveryKey(String pin);

    /**
     * Checks whether the given candidate username is available for registration on the WhatsApp relay.
     *
     * @apiNote
     * Drives the live-validation probe behind the username picker UI.
     * The server returns both an availability result and an array of
     * suggested alternatives generated when the candidate is taken
     * or invalid; this helper projects only the boolean.
     *
     * @param candidate the candidate username to validate; {@code null} forwards an empty payload, treated by the relay as a no-op probe
     * @return {@code true} when the relay reports the candidate as available, {@code false} when it is rejected or the relay returned no payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    boolean checkUsernameAvailability(String candidate);

    /**
     * Publishes or clears the authenticated user's ephemeral text status.
     *
     * @apiNote
     * Drives the "Text status" card on the profile drawer: the new
     * value surfaces next to the user's name on every linked device.
     * Pass both {@code text} and {@code emoji} as {@code null} (or
     * {@code text} empty) to clear the published status.
     *
     * @param text              the status body, or {@code null} / empty to
     *                          clear the existing status
     * @param emoji             the optional emoji decoration, or
     *                          {@code null} to publish without an emoji
     * @param ephemeralDuration the auto-expiry duration; {@code null} or
     *                          {@link Duration#ZERO} disables auto-expiry
     * @throws IllegalArgumentException        if {@code ephemeralDuration}
     *                                         is negative
     * @throws WhatsAppServerRuntimeException  if the relay rejects the
     *                                         mutation, returns no payload,
     *                                         or replies with any token
     *                                         other than {@code "SUCCESS"}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    void editTextStatus(String text, String emoji, Duration ephemeralDuration);

    /**
     * Fetches the ephemeral text statuses published by one or more users.
     *
     * @apiNote
     * Backs the read path that powers the contact-card "Text status"
     * badge and the chat-list status preview. Each returned
     * {@link ContactTextStatus} carries the status text, the optional
     * emoji, the author-relative last-update timestamp, and the
     * ephemeral duration so callers can reproduce the expiry
     * countdown. Authors that have not published a text status are
     * omitted from the result map.
     *
     * @param users the user JIDs whose text status should be fetched
     * @return a {@link Map} from queried JID to its parsed
     *         {@link ContactTextStatus}, never {@code null} and empty when
     *         the relay returned no payload or none of the queried users
     *         has a status set
     * @throws NullPointerException            if {@code users} is
     *                                         {@code null} or contains a
     *                                         {@code null} entry
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    Map<Jid, ContactTextStatus> queryUserTextStatuses(List<? extends JidProvider> users);

    /**
     * Fetches the latest linked-identity (LID) rotation pair for the
     * authenticated account.
     *
     * @apiNote
     * Used by callers reconciling local storage after the server
     * rotates an account's LID during the LID-migration rollout: the
     * returned pair maps the old LID to the new LID and lets the
     * caller rewrite any persisted references.
     *
     * @return an {@link Optional} carrying the parsed {@link LidChange}
     *         rotation pair, or {@link Optional#empty()} when the relay
     *         returned no payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    Optional<LidChange> queryLidChangeNotification();

    /**
     * Fetches the current Oblivious HTTP for Initiation (OHAI) key
     * configuration advertised by the WhatsApp relay.
     *
     * @apiNote
     * Drives the HPKE key-bundle refresh used to encapsulate ACS
     * (Account Centre Service) requests. Each entry carries a key id,
     * a last-updated time, and the public key bytes; callers cache
     * the bundle and refetch when the relay rotates the set.
     *
     * @return an unmodifiable list of {@link OhaiKeyConfig} entries
     *         advertised by the relay, empty when the relay returned no
     *         payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    List<OhaiKeyConfig> queryOhaiKeyConfig();

    /**
     * Fetches the first page of products from a WhatsApp Business catalog.
     *
     * @apiNote
     * Convenience overload that calls
     * {@link #queryBusinessCatalog(JidProvider, int)} with the
     * default page size of {@code 5}.
     *
     * @param businessJid the business JID whose catalog should be fetched
     * @return the list of parsed {@link BusinessCatalogEntry} instances on
     *         the first page; never {@code null} and empty when the
     *         catalog has no products or the response is absent
     * @throws NullPointerException if {@code businessJid} is {@code null}
     */
    List<BusinessCatalogEntry> queryBusinessCatalog(JidProvider businessJid);

    /**
     * Fetches the first page of products from a WhatsApp Business catalog,
     * using a caller-chosen page size.
     *
     * @apiNote
     * Backs the storefront product grid customers see on a merchant's
     * profile. The returned list preserves server-side ordering so
     * that callers paginating manually across successive calls
     * observe a stable traversal.
     *
     * @param businessJid the business JID whose catalog should be fetched
     * @param limit       the maximum number of products per page;
     *                    any positive value is accepted and the relay
     *                    clamps silently
     * @return the list of parsed {@link BusinessCatalogEntry} instances on
     *         the first page; never {@code null} and empty when the
     *         catalog has no products or the response is absent
     * @throws NullPointerException     if {@code businessJid} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code limit} is not positive
     */
    List<BusinessCatalogEntry> queryBusinessCatalog(JidProvider businessJid, int limit);

    /**
     * Fetches the first page of collections inside a WhatsApp Business
     * catalog.
     *
     * @apiNote
     * Convenience overload that calls
     * {@link #queryBusinessCollections(JidProvider, int)} with the
     * default collection page size of {@code 5} and the default
     * per-collection product preview of {@code 100}.
     *
     * @param businessJid the business JID whose collections should be
     *                    fetched
     * @return the list of parsed {@link BusinessCatalog} instances on the
     *         first page; never {@code null} and empty when the catalog
     *         has no collections or the response is absent
     * @throws NullPointerException if {@code businessJid} is {@code null}
     */
    List<BusinessCatalog> queryBusinessCollections(JidProvider businessJid);

    /**
     * Fetches the first page of collections inside a WhatsApp Business
     * catalog, using a caller-chosen page size.
     *
     * @apiNote
     * Backs the storefront collection-browser customers see on a
     * merchant's profile. The number of products previewed inside each
     * collection defaults to {@code 100}; use
     * {@link #queryBusinessCollections(JidProvider, int, int)} to choose
     * a different per-collection preview size.
     *
     * @param businessJid the business JID whose collections should be
     *                    fetched
     * @param limit       the maximum number of collections per page
     * @return the list of parsed {@link BusinessCatalog} instances on the
     *         first page; never {@code null} and empty when the catalog
     *         has no collections or the response is absent
     * @throws NullPointerException     if {@code businessJid} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code limit} is not positive
     */
    List<BusinessCatalog> queryBusinessCollections(JidProvider businessJid, int limit);

    /**
     * Fetches the first page of collections inside a WhatsApp Business
     * catalog, choosing both the collection page size and how many
     * products are previewed inside each collection.
     *
     * @apiNote
     * Backs the storefront collection-browser customers see on a
     * merchant's profile. Each collection in the result previews up to
     * {@code itemLimit} of its products; raise it to render larger
     * collection thumbnails or lower it to fetch lighter pages.
     *
     * @param businessJid the business JID whose collections should be
     *                    fetched
     * @param limit       the maximum number of collections per page
     * @param itemLimit   the maximum number of products previewed inside
     *                    each returned collection
     * @return the list of parsed {@link BusinessCatalog} instances on the
     *         first page; never {@code null} and empty when the catalog
     *         has no collections or the response is absent
     * @throws NullPointerException     if {@code businessJid} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code limit} or
     *                                  {@code itemLimit} is not positive
     */
    List<BusinessCatalog> queryBusinessCollections(JidProvider businessJid, int limit, int itemLimit);

    /**
     * Checks whether a WhatsApp Business catalog services a given
     * postcode.
     *
     * @apiNote
     * Backs the "Delivers to {area}" pre-flight badge the cart UI
     * shows before letting the buyer commit. The verdict is one of
     * {@link BusinessPostcodeVerificationResult#SUCCESS},
     * {@link BusinessPostcodeVerificationResult#INVALID_POSTCODE}, or
     * {@link BusinessPostcodeVerificationResult#UNSERVICEABLE_LOCATION};
     * the optional {@code encryptedLocationName} carries the resolved
     * area name (encrypted under the merchant's direct-connection
     * key) that callers decrypt to render the cart-UI hint.
     *
     * @param businessJid                   the JID of the business whose
     *                                      service area is being tested
     * @param directConnectionEncryptedInfo the opaque direct-connection
     *                                      envelope carrying the
     *                                      buyer-side postcode
     * @return the parsed verification verdict and the optional encrypted
     *         location name; never {@code null}
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant or an unrecognised
     *                                         {@code result_code}
     */
    BusinessPostcodeVerification verifyBusinessPostcode(JidProvider businessJid, String directConnectionEncryptedInfo);

    /**
     * Re-prices a business shopping cart against the merchant's current
     * catalog and returns the authoritative cart.
     *
     * @apiNote
     * Backs the "review order" step the cart UI runs before the
     * place-order tap. The response carries an updated cart-wide
     * total and one per-line entry with the rebuilt name, price,
     * currency, media, remaining stock count, and any active
     * sale-price window; lines whose product was removed surface
     * their server-side status code so the UI can grey them out. The
     * {@link BusinessCartRefresh} bundles the merchant JID, the cart
     * product ids in server-side order, the desired thumbnail
     * dimensions, and an optional direct-connection envelope used by
     * merchants enrolled in the encrypted direct-connection feature.
     *
     * @param refresh the cart-refresh request bundling the business JID,
     *                ordered product ids, thumbnail size, and optional
     *                direct-connection envelope
     * @return the freshly-rebuilt cart with up-to-date prices and
     *         availability for every line; never {@code null}
     * @throws NullPointerException            if {@code refresh} or any
     *                                         non-nullable field is
     *                                         {@code null}
     * @throws IllegalArgumentException        if the product id list is
     *                                         empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant
     */
    BusinessRefreshedCart refreshBusinessCart(BusinessCartRefresh refresh);

    /**
     * Resolves a click-to-WhatsApp (CTWA) deep-link invite into the
     * originating ad's metadata.
     *
     * @apiNote
     * Used by business clients to populate the system "context" bubble
     * that quotes the Facebook / Instagram ad which opened a brand-new
     * chat (or to stamp the next outgoing message's
     * {@link ContextInfo#externalAdReply()}). The
     * {@code expectedSourceUrl} is replayed back to the server so it
     * can reject replays where the invite code was lifted out of
     * context. The returned record carries every ad-side field
     * WhatsApp surfaces in-app: thumbnail bytes (or CDN URL),
     * headline, body, video URL, source-app identifier, and the
     * WAMO-AGM greeting / payload / image fields used by AGM-enrolled
     * advertisers.
     *
     * @param businessJid       the JID of the business advertised in the
     *                          ad
     * @param inviteCode        the invite code embedded in the
     *                          click-to-WhatsApp deep link
     * @param expectedSourceUrl the deep-link URL the client landed on,
     *                          replayed back to the server for replay
     *                          rejection
     * @return the parsed ad context; never {@code null}
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws NoSuchElementException          if the server returned no
     *                                         context or an incomplete
     *                                         response
     */
    BusinessCtwaContext queryCtwaContext(JidProvider businessJid, String inviteCode, String expectedSourceUrl);

    /**
     * Reconciles the local view of the Blocked Contacts privacy list
     * with the server.
     *
     * @apiNote
     * Use after the user toggles a block from another paired device,
     * or whenever the Blocked Contacts surface should redraw against
     * an authoritative copy. The blocked-contact set on
     * {@link WhatsAppStore} is replaced with the server's view, and
     * {@link WhatsAppClientListener#onContactBlocked} fires once per
     * contact whose blocked flag flipped. If WhatsApp is in the
     * middle of migrating phone numbers to LID identifiers on this
     * account, the refresh refuses to commit a list that would be
     * addressed against the wrong identifier kind for the current
     * device, raises the matching
     * {@link WhatsAppLidMigrationException} subtype, and leaves the
     * local list untouched.
     *
     * @throws WhatsAppLidMigrationException.StateDiscrepancy        if
     *         the server's view of the LID migration disagrees with
     *         the local view
     * @throws WhatsAppLidMigrationException.BlocklistChatDbUnmigrated
     *         if the server returns a LID-addressed list before this
     *         device has finished migrating its chats to LID and the
     *         primary has not yet sent the mapping payload
     */
    void refreshBlockList();

    /**
     * Blocks a contact at the server.
     *
     * @apiNote
     * Drives the "Block contact" action: after this returns, the
     * contact can no longer send messages or see this account's
     * presence. On success the contact is added to the
     * {@link WhatsAppStore} block list eagerly.
     *
     * @param contact the contact to block
     * @throws NullPointerException           if {@code contact} is
     *                                        {@code null}
     * @throws WhatsAppServerRuntimeException if the relay rejected the
     *                                        request
     */
    void blockContact(JidProvider contact);

    /**
     * Unblocks a contact at the server.
     *
     * @apiNote
     * Drives the "Unblock contact" action: the contact is restored to
     * the normal messaging and presence channels. On success the
     * contact is removed from the {@link WhatsAppStore} block list
     * eagerly.
     *
     * @param contact the contact to unblock
     * @throws NullPointerException           if {@code contact} is
     *                                        {@code null}
     * @throws WhatsAppServerRuntimeException if the relay rejected the
     *                                        request
     */
    void unblockContact(JidProvider contact);

    /**
     * Reconciles the local view of one marketing-message opt-out list
     * category with the server.
     *
     * @apiNote
     * Use after the user toggles a marketing opt-out from another
     * paired device, or whenever the marketing-message settings
     * surface for that category should redraw against an
     * authoritative copy. The new entries replace
     * {@link WhatsAppStore#optOutListEntries(String)} for the
     * category, and {@link WhatsAppClientListener#onOptOutList} fires
     * with the new set. Reads the cached digest from
     * {@link WhatsAppStore#optOutListHash(String)} so an unchanged
     * server-side view is a no-op.
     *
     * @param category the opt-out category
     * @throws NullPointerException            if {@code category} is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected
     *                                         the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void refreshOptOutList(String category);

    /**
     * Reconciles the local view of one privacy-axis contact blacklist
     * with the server.
     *
     * @apiNote
     * Use to redraw the privacy settings surface for one axis (for
     * example {@code "last"}, {@code "profile"}, {@code "status"},
     * {@code "online"}) against an authoritative copy. The
     * addressing-mode parameter chooses between the phone-number and
     * the LID variant. The new entries replace
     * {@link WhatsAppStore#contactBlacklistEntries(String)} for the
     * category, and {@link WhatsAppClientListener#onContactBlacklist}
     * fires with the new set. Reads the cached digest from
     * {@link WhatsAppStore#contactBlacklistHash(String)} so an
     * unchanged server-side view is a no-op.
     *
     * @param category       the privacy axis category name
     * @param addressingMode the JID addressing mode to request
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay returned an
     *                                         error response
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void refreshContactBlacklist(String category, ContactBlacklistAddressingMode addressingMode);

    /**
     * Adds or removes a contact on the marketing-message opt-out list.
     *
     * @apiNote
     * Drives the per-business marketing opt-out toggle: applies the
     * requested membership change for the supplied JID, category, and
     * action, alongside the analytics metadata ({@code reason},
     * {@code entry_point}, {@code signup_id}) and an optional opt-out
     * duration override. This is the marketing-messaging counterpart
     * of {@link #blockContact(JidProvider)} /
     * {@link #unblockContact(JidProvider)}.
     *
     * @param update the {@link OptOutListUpdate} bundling the target JID,
     *               category, action, optional digest, and optional
     *               analytics metadata
     * @throws NullPointerException            if {@code update} or any
     *                                         required field is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void updateOptOutList(OptOutListUpdate update);

    /**
     * Fetches the profile picture URL for the given JID.
     *
     * @apiNote
     * Drives the contact-card / chat-list avatar fetch: returns the
     * picture URL when present, or {@link Optional#empty()} when the
     * target has no picture set.
     *
     * @param jid the JID whose picture URL should be fetched
     * @return the picture URL when present; {@link Optional#empty()}
     *         otherwise
     * @throws NullPointerException            if {@code jid} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<URI> queryPicture(JidProvider jid);

    /**
     * Changes the push name (broadcast display name) for this account.
     *
     * @apiNote
     * Backs the "Your name" edit on the profile drawer: writes the
     * new name into {@link WhatsAppStore#setName(String)} and
     * notifies listeners.
     *
     * @param newPushName the new broadcast display name
     * @throws NullPointerException            if {@code newPushName} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editName(String newPushName);

    /**
     * Changes this account's "About" status text.
     *
     * @apiNote
     * Backs the "About" edit on the profile drawer: blocks until the
     * server acknowledges the new biographical line.
     *
     * @param aboutText the new about text
     * @throws NullPointerException            if {@code aboutText} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editAbout(String aboutText);

    /**
     * Changes this account's profile picture.
     *
     * @apiNote
     * Backs the "Change profile picture" tap on the profile drawer:
     * the full-size JPEG and a 96x96 preview thumbnail (generated
     * locally) are uploaded together.
     *
     * @param jpegBytes the full-size JPEG payload
     * @throws NullPointerException            if {@code jpegBytes} is
     *                                         {@code null}
     * @throws IllegalArgumentException        if {@code jpegBytes} is not
     *                                         a valid image
     * @throws IllegalStateException           if the self JID is not
     *                                         known
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editProfilePicture(byte[] jpegBytes);

    /**
     * Removes this account's profile picture.
     *
     * @apiNote
     * Backs the "Remove profile picture" tap on the profile drawer:
     * the picture is dropped server-side and the contact card falls
     * back to the default avatar on every device that observes this
     * account.
     *
     * @throws IllegalStateException           if the self JID is not
     *                                         known
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void removeProfilePicture();

    /**
     * Broadcasts this client's own presence state, reusing the store's
     * push name.
     *
     * @apiNote
     * Convenience overload that calls
     * {@link #editPresence(ContactStatus, String)} with
     * {@link WhatsAppStore#name()} as the display-name override.
     *
     * @param status {@link ContactStatus#AVAILABLE} or
     *               {@link ContactStatus#UNAVAILABLE}
     * @throws NullPointerException            if {@code status} is
     *                                         {@code null}
     * @throws IllegalArgumentException        if {@code status} is not
     *                                         {@code AVAILABLE} or
     *                                         {@code UNAVAILABLE}
     * @throws WhatsAppSessionException.Closed if the socket has been
     *                                         closed
     */
    void editPresence(ContactStatus status);

    /**
     * Broadcasts this client's own presence state with an explicit
     * display-name override.
     *
     * @apiNote
     * Drives the foreground/background presence broadcast emitted
     * when a session gains or loses focus: announces
     * {@link ContactStatus#AVAILABLE} or
     * {@link ContactStatus#UNAVAILABLE} together with the supplied
     * push name (when non-{@code null}) so the relay can forward it
     * to peers.
     *
     * @param status       {@link ContactStatus#AVAILABLE} or
     *                     {@link ContactStatus#UNAVAILABLE}
     * @param presenceName optional display name override; may be
     *                     {@code null}
     * @throws NullPointerException            if {@code status} is
     *                                         {@code null}
     * @throws IllegalArgumentException        if {@code status} is not
     *                                         {@code AVAILABLE} or
     *                                         {@code UNAVAILABLE}
     * @throws WhatsAppSessionException.Closed if the socket has been
     *                                         closed
     */
    void editPresence(ContactStatus status, String presenceName);

    /**
     * Sends a chat-state indication (typing, recording, or paused) for a
     * conversation.
     *
     * @apiNote
     * Drives the "typing..." / "recording audio..." footer the
     * remote peer sees while the user composes. The accepted states
     * are:
     * <ul>
     *   <li>{@link ContactStatus#COMPOSING} for a text-in-progress
     *       indicator</li>
     *   <li>{@link ContactStatus#RECORDING} for a voice-message
     *       recording indicator</li>
     *   <li>{@link ContactStatus#UNAVAILABLE} to clear any
     *       in-progress indicator</li>
     * </ul>
     *
     * @param chat  the chat JID the state applies to
     * @param state {@link ContactStatus#COMPOSING},
     *              {@link ContactStatus#RECORDING}, or
     *              {@link ContactStatus#UNAVAILABLE} (mapped to paused)
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws IllegalArgumentException        if {@code state} is not one
     *                                         of {@code COMPOSING},
     *                                         {@code RECORDING}, or
     *                                         {@code UNAVAILABLE}
     * @throws WhatsAppSessionException.Closed if the socket has been
     *                                         closed
     */
    void editChatState(JidProvider chat, ContactStatus state);

    /**
     * Subscribes to real-time presence updates for a contact.
     *
     * @apiNote
     * Convenience overload that calls
     * {@link #subscribeToPresence(JidProvider, String, JidProvider)}
     * with {@code null} for both the display-name override and the
     * context scope. After the server accepts the subscribe, presence
     * pushes for the target flow until the subscription is cancelled
     * via {@link #unsubscribeFromPresence(JidProvider)} or the socket
     * closes.
     *
     * @param target the contact JID to subscribe to
     * @throws NullPointerException            if {@code target} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been
     *                                         closed
     */
    void subscribeToPresence(JidProvider target);

    /**
     * Subscribes to real-time presence updates for a target, with an
     * optional display name and context scope.
     *
     * @apiNote
     * Drives the presence-push subscription that powers the "online"
     * / "last seen" footer on chat headers and the per-participant
     * indicators on community / group surfaces.
     *
     * @param presenceTo      the JID to subscribe to
     * @param presenceName    optional display name; may be {@code null}
     * @param presenceContext optional context JID for community / group
     *                        surfaces; {@code null} for the default 1:1
     *                        chat scope
     * @throws NullPointerException            if {@code presenceTo} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void subscribeToPresence(JidProvider presenceTo, String presenceName, JidProvider presenceContext);

    /**
     * Cancels a presence subscription previously established with
     * {@link #subscribeToPresence(JidProvider)}.
     *
     * @apiNote
     * Symmetric counterpart to {@link #subscribeToPresence(JidProvider)}:
     * after this returns, no further presence push notifications
     * arrive for the contact until a new subscription is opened.
     *
     * @param target the contact JID to unsubscribe from
     * @throws NullPointerException            if {@code target} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been
     *                                         closed
     */
    void unsubscribeFromPresence(JidProvider target);

    /**
     * Sends a fresh message to a chat.
     *
     * @apiNote
     * Primary outgoing-message entry point: prepares the raw
     * {@link MessageContainer} into a populated {@link ChatMessageInfo}
     * (or {@link NewsletterMessageInfo} for newsletter JIDs),
     * encrypts per-device, and dispatches through the chat, group,
     * status, or newsletter sender appropriate to the JID server.
     *
     * @param jid       the destination chat JID
     * @param container the message payload to send
     * @throws NullPointerException                           if any
     *                                                        argument is
     *                                                        {@code null}
     * @throws WhatsAppMessageException.Send.InvalidRecipient if the JID
     *                                                        does not
     *                                                        match a
     *                                                        supported
     *                                                        chat type
     */
    void sendMessage(JidProvider jid, MessageContainer container);

    /**
     * Sends a pre-built {@link MessageInfo} without re-running the
     * preparer pipeline.
     *
     * @apiNote
     * Use this overload when the caller has assembled a
     * {@link ChatMessageInfo} or {@link NewsletterMessageInfo} with a
     * message id, timestamp, and any extension metadata already
     * populated; typical scenarios are rehydrating a draft or
     * re-transmitting a message that failed a previous send. Wraps
     * the same dispatch
     * {@link #sendMessage(JidProvider, MessageContainer)} uses.
     *
     * @param messageInfo the fully-populated outgoing message
     * @throws NullPointerException                           if
     *                                                        {@code messageInfo}
     *                                                        is
     *                                                        {@code null}
     * @throws WhatsAppMessageException.Send.InvalidRecipient if the JID
     *                                                        does not
     *                                                        match a
     *                                                        supported
     *                                                        chat type
     */
    void sendMessage(MessageInfo messageInfo);

    /**
     * Edits the body of a previously sent message.
     *
     * @apiNote
     * Drives the "Edit message" action: the replacement payload is
     * wrapped in a protocol-message envelope and dispatched through
     * the standard send pipeline so every linked device reconciles
     * the change. The edit window is enforced server-side; late edits
     * are rejected by the server.
     *
     * @param originalKey the key of the message to edit; must carry a
     *                    {@code parentJid} identifying the chat
     * @param newContent  the replacement message container
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code originalKey} has no
     *                                  {@code parentJid}
     */
    void editMessage(MessageKey originalKey, MessageContainer newContent);

    /**
     * Deletes a message locally or for every participant in the chat.
     *
     * @apiNote
     * Drives the "Delete for me" / "Delete for everyone" actions in
     * the message context menu. When {@code everyone} is {@code false}
     * the message is removed from the local store.
     *
     * @param key      the key of the message to delete
     * @param everyone {@code true} to delete for every participant
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} has no
     *                                  {@code parentJid}, or (when
     *                                  {@code everyone} is {@code false})
     *                                  no {@code id}
     */
    void deleteMessage(MessageKey key, boolean everyone);

    /**
     * Posts a new status update to {@code status@broadcast}.
     *
     * @apiNote
     * Drives the "Add status" surface: prepares the content (message
     * id, message secret, device context info) and routes it through
     * the status-specific sender, which applies sender-key encryption
     * to the current status audience. The returned
     * {@link ChatMessageInfo} is the persisted model row callers
     * reference later (for example to revoke the post via
     * {@link #deleteStatus(String)}).
     *
     * @param content the raw status body (text, image, video, sticker
     *                etc.)
     * @return the persisted message info for the new status
     * @throws NullPointerException  if {@code content} is {@code null}
     * @throws IllegalStateException if the client is not logged in or the
     *                               message could not be stored after
     *                               sending
     */
    ChatMessageInfo sendStatus(MessageContainer content);

    /**
     * Revokes a previously posted status update.
     *
     * @apiNote
     * Drives the "Delete status" tap on the status-tray surface:
     * issues a revoke protocol message addressed at
     * {@code status@broadcast} carrying the key of the original status
     * post. The status-specific sender handles device-list narrowing
     * and the direct-fanout fallback when recipients have left the
     * audience.
     *
     * @param statusId the id of the status message to revoke
     * @throws NullPointerException  if {@code statusId} is {@code null}
     * @throws IllegalStateException if the client is not logged in
     */
    void deleteStatus(String statusId);

    /**
     * Emits a {@code read} receipt for a viewed status update.
     *
     * @apiNote
     * Drives the "viewed" tick the author sees on their status entry:
     * a read receipt is delivered to the status broadcast addressed
     * at the original status author. The author JID is resolved from
     * the cached status message's sender.
     *
     * @param statusId the id of the viewed status message
     * @throws NullPointerException   if {@code statusId} is {@code null}
     * @throws NoSuchElementException if no status with the given id is
     *                                cached or it has no sender JID
     */
    void markStatusViewed(String statusId);

    /**
     * Reconciles the local view of the Status privacy panel with the
     * server.
     *
     * @apiNote
     * Use to redraw the Status privacy panel against an authoritative
     * copy of the selected distribution mode and the JID list paired
     * with the whitelist or except-contacts modes. The new value
     * replaces {@link WhatsAppStore#statusPrivacy()} and
     * {@link WhatsAppClientListener#onStatusPrivacyChanged} fires
     * with the new value.
     *
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void refreshStatusPrivacy();

    /**
     * Changes the Status privacy configuration.
     *
     * @apiNote
     * Drives the "Status privacy" panel write: applies the new
     * distribution mode server-side and the local
     * {@link PrivacySettingType#STATUS} entry in the store is updated
     * eagerly.
     *
     * @param mode the new distribution mode
     * @param jids the JID list applied by
     *             {@link StatusPrivacyMode#WHITELIST} and
     *             {@link StatusPrivacyMode#CONTACTS_EXCEPT}; may be empty
     *             or {@code null} for {@link StatusPrivacyMode#CONTACTS}
     * @throws NullPointerException if {@code mode} is {@code null}
     */
    void editStatusPrivacy(StatusPrivacyMode mode, Collection<? extends JidProvider> jids);

    /**
     * Forwards a single message to a single destination chat.
     *
     * @apiNote
     * Convenience overload that calls
     * {@link #forwardMessages(Collection, Collection)} with singleton
     * collections; matches the single-message branch of the forward
     * picker. PSA-message forwards also fire the corresponding
     * chat-PSA telemetry event.
     *
     * @param sourceKey   the key of the message to forward
     * @param destination the destination chat JID
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the source message cannot be
     *                                  resolved in the local store
     */
    void forwardMessage(MessageKey sourceKey, JidProvider destination);

    /**
     * Forwards a set of messages to every destination chat.
     *
     * @apiNote
     * Drives the multi-select "Forward to" picker: resolves each
     * source key against the local store, extracts the
     * {@link MessageContainer}, and sends it to every destination in
     * turn. Unresolvable source keys and unsendable destinations are
     * skipped silently.
     *
     * @param sourceKeys   the keys of the messages to forward
     * @param destinations the destination chat JIDs
     * @throws NullPointerException if any argument is {@code null}
     */
    void forwardMessages(Collection<MessageKey> sourceKeys, Collection<? extends JidProvider> destinations);

    /**
     * Adds or replaces this account's reaction on a message.
     *
     * @apiNote
     * Drives the emoji-reaction picker shown when long-pressing a
     * message bubble: builds a reaction message keyed to the target
     * and dispatches it through the standard send pipeline; the
     * reaction is automatically wrapped in an encrypted-reaction
     * envelope when the target chat is a CAG community subgroup.
     * Sending an empty emoji is equivalent to
     * {@link #removeReaction(MessageKey)}.
     *
     * @param messageKey the key of the message being reacted to
     * @param emoji      the reaction emoji; empty string removes the
     *                   existing reaction
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code messageKey} has no
     *                                  {@code parentJid}
     */
    void addReaction(MessageKey messageKey, String emoji);

    /**
     * Removes this account's reaction from a message.
     *
     * @apiNote
     * Convenience overload that calls
     * {@link #addReaction(MessageKey, String)} with the empty string;
     * the empty value is the wire signal to withdraw the sender's
     * previous reaction.
     *
     * @param messageKey the key of the message whose reaction should be
     *                   removed
     * @throws NullPointerException     if {@code messageKey} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code messageKey} has no
     *                                  {@code parentJid}
     */
    void removeReaction(MessageKey messageKey);

    /**
     * Stars (bookmarks) a message so it appears in the account's starred
     * messages list.
     *
     * @apiNote
     * Drives the star tap on a message bubble: the change is
     * propagated to every linked device via the {@code REGULAR_HIGH}
     * app-state sync collection. PSA-message stars also fire the
     * corresponding chat-PSA telemetry event.
     *
     * @param key the key of the message to star
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} has no
     *                                  {@code parentJid} or no
     *                                  {@code id}
     */
    void starMessage(MessageKey key);

    /**
     * Unstars a previously starred message.
     *
     * @apiNote
     * Counterpart to {@link #starMessage(MessageKey)}: emits the
     * {@code REGULAR_HIGH} sync mutation and flips the local star
     * flag back off.
     *
     * @param key the key of the message to unstar
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} has no
     *                                  {@code parentJid} or no
     *                                  {@code id}
     */
    void unstarMessage(MessageKey key);

    /**
     * Archives or unarchives a chat.
     *
     * @apiNote
     * Drives the "Archive chat" swipe / context-menu action: the
     * change propagates to every linked device via the
     * {@code REGULAR_LOW} app-state sync collection, and archiving
     * also queues an unpin mutation. The local
     * {@link Chat#setArchived(Boolean)} flag is flipped eagerly so
     * callers observe the change without waiting for the sync
     * round-trip.
     *
     * @param chat    the JID of the chat to archive or unarchive
     * @param archive {@code true} to archive, {@code false} to unarchive
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void archiveChat(JidProvider chat, boolean archive);

    /**
     * Pins or unpins a chat.
     *
     * @apiNote
     * Drives the pin / unpin context-menu action: the change
     * propagates to every linked device via the {@code REGULAR_LOW}
     * app-state sync collection, and the local
     * {@link Chat#setPinnedTimestamp(Instant)} is updated eagerly.
     * When pinning, {@link Chat#setArchived(Boolean)} is forced to
     * {@code false} so a pinned chat cannot stay archived.
     *
     * @param chat the JID of the chat to pin or unpin
     * @param pin  {@code true} to pin the chat, {@code false} to unpin
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void pinChat(JidProvider chat, boolean pin);

    /**
     * Mutes a chat until the given instant.
     *
     * @apiNote
     * Drives the "Mute notifications" dialog: the change propagates
     * to every linked device via the {@code REGULAR_HIGH} app-state
     * sync collection, and the mute state is applied to the local
     * {@link Chat} eagerly. Pass {@code null} to unmute; an
     * {@link Instant} with epoch second {@code -1} is the sentinel
     * for "muted indefinitely".
     *
     * @param chat      the JID of the chat to mute
     * @param muteUntil the instant at which the mute should expire, or
     *                  {@code null} to unmute
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void muteChat(JidProvider chat, Instant muteUntil);

    /**
     * Unmutes a chat.
     *
     * @apiNote
     * Convenience overload that calls
     * {@link #muteChat(JidProvider, Instant)} with {@code null} for
     * {@code muteUntil}, restoring the chat's default notification
     * behaviour.
     *
     * @param chat the JID of the chat to unmute
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void unmuteChat(JidProvider chat);

    /**
     * Marks a chat as read.
     *
     * @apiNote
     * Drives the implicit "seen" signal fired when a chat receives
     * focus: the change propagates to every linked device via the
     * {@code REGULAR_LOW} app-state sync collection, and the local
     * {@link Chat#setMarkedAsUnread(Boolean)} and
     * {@link Chat#setUnreadCount(Integer)} flags are cleared eagerly.
     *
     * @param chat the JID of the chat to mark as read
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void markChatAsRead(JidProvider chat);

    /**
     * Marks a chat as unread.
     *
     * @apiNote
     * Drives the "Mark as unread" context-menu tap: the change
     * propagates to every linked device via the {@code REGULAR_LOW}
     * app-state sync collection. The local
     * {@link Chat#setMarkedAsUnread(Boolean)} flag is set eagerly,
     * along with the sentinel unread-count value that marks the chat
     * as "manually unread".
     *
     * @param chat the JID of the chat to mark as unread
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void markChatAsUnread(JidProvider chat);

    /**
     * Empties a chat while keeping the chat row itself.
     *
     * @apiNote
     * Drives the "Clear messages" confirmation: the change propagates
     * to every linked device via the {@code REGULAR_HIGH} app-state
     * sync collection, and {@link Chat#removeMessages()} is applied
     * eagerly so callers observe the chat emptied without waiting
     * for the sync round-trip.
     *
     * @param chat        the JID of the chat to clear
     * @param keepStarred {@code true} to preserve starred messages,
     *                    {@code false} to delete them with the rest
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void clearChat(JidProvider chat, boolean keepStarred);

    /**
     * Deletes a chat entirely from the local store and every linked
     * device.
     *
     * @apiNote
     * Drives the "Delete chat" confirmation: the change propagates
     * to every linked device via the {@code REGULAR_HIGH} app-state
     * sync collection, and the chat is removed from the local store
     * eagerly.
     *
     * @param chat the JID of the chat to delete
     * @return the deleted {@link Chat} when it existed locally, or
     *         {@link Optional#empty()} otherwise
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    Optional<Chat> deleteChat(JidProvider chat);

    /**
     * Locks a chat behind the chat-lock PIN.
     *
     * @apiNote
     * Drives the "Lock chat" tap that hides the chat from the main
     * list: queues an unarchive mutation, an unpin mutation, and a
     * lock mutation, then pushes the full set through
     * {@link #pushWebAppState(SyncPatchType, List)}, and mirrors
     * the locked-and-unarchived update on the local {@link Chat}
     * eagerly.
     *
     * @param chat the JID of the chat to lock
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void lockChat(JidProvider chat);

    /**
     * Unlocks a chat, restoring it to the main chat list.
     *
     * @apiNote
     * Counterpart to {@link #lockChat(JidProvider)}: queues only the
     * lock mutation, pushes it through
     * {@link #pushWebAppState(SyncPatchType, List)}, and flips the
     * locked flag back off on the local {@link Chat} eagerly.
     *
     * @param chat the JID of the chat to unlock
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void unlockChat(JidProvider chat);

    /**
     * Creates a new business chat label with the given name and palette
     * colour index.
     *
     * @apiNote
     * Drives the "New label" form on the Business labels manager:
     * the name is mapped to a predefined id when applicable via
     * {@link BusinessLabelConstants#mapLabelNameToPredefinedId(String)}
     * and the label is registered as a regular (non-predefined)
     * custom label.
     *
     * @param name       the user-visible display name of the label
     * @param colorIndex the palette colour index
     * @return the newly-allocated label id (stringified integer)
     * @throws NullPointerException if {@code name} is {@code null}
     */
    String createLabel(String name, int colorIndex);

    /**
     * Edits the display name and palette colour of an existing chat label.
     *
     * @apiNote
     * Drives the "Edit label" form on the Business labels manager.
     * Pair this entry point with {@link #createLabel(String, int)}
     * when applications surface a single add-or-edit affordance.
     *
     * @param labelId    the label identifier
     * @param name       the new display name
     * @param colorIndex the new palette colour index
     * @return the updated label, or {@link Optional#empty()} when no label
     *         with {@code labelId} exists in the local store
     * @throws NullPointerException if {@code labelId} or {@code name} is {@code null}
     */
    Optional<Label> editLabel(String labelId, String name, int colorIndex);

    /**
     * Deletes an existing chat label along with every chat or contact
     * association registered against it.
     *
     * @apiNote
     * Drives the "Delete label" affordance on the Business labels
     * manager. The accompanying assignments are torn down in the
     * same patch so the relay does not surface stale label badges on
     * the formerly tagged chats.
     *
     * @param labelId the identifier of the label to delete
     * @return the removed label, or {@link Optional#empty()} when no
     *         label with {@code labelId} exists in the local store
     * @throws NullPointerException if {@code labelId} is {@code null}
     */
    Optional<Label> deleteLabel(String labelId);

    /**
     * Deletes an existing chat label along with every chat or contact
     * association registered against it.
     *
     * @apiNote
     * Convenience overload that resolves the label identifier from
     * {@link Label#id()} before delegating to
     * {@link #deleteLabel(String)}.
     *
     * @param label the label to delete
     * @return the removed label, or {@link Optional#empty()} when no
     *         label with the same id exists in the local store
     * @throws NullPointerException if {@code label} is {@code null}
     */
    Optional<Label> deleteLabel(Label label);

    /**
     * Applies a new user-chosen order to the chat labels.
     *
     * @apiNote
     * Drives the drag-to-reorder gesture on the Business labels
     * manager. Submit the full ordered list of label identifiers
     * rather than a delta; entries absent from the list keep whatever
     * {@code orderIndex} they had.
     *
     * @param labelIds the label identifiers in the new display order
     * @throws NullPointerException if {@code labelIds} is {@code null}
     */
    void reorderLabels(List<String> labelIds);

    /**
     * Associates a label with the given chat.
     *
     * @apiNote
     * Drives the "Apply label" affordance on the Business chat list:
     * tags the chat so it surfaces under the matching label in the
     * labels manager.
     *
     * @param labelId the label identifier
     * @param chat    the chat JID to tag with the label
     * @throws NullPointerException if any argument is {@code null}
     */
    void associateLabel(String labelId, JidProvider chat);

    /**
     * Associates a label with the given chat.
     *
     * @apiNote
     * Convenience overload that resolves the label identifier from
     * {@link Label#id()} before delegating to
     * {@link #associateLabel(String, JidProvider)}.
     *
     * @param label the label
     * @param chat  the chat JID to tag with the label
     * @throws NullPointerException if any argument is {@code null}
     */
    void associateLabel(Label label, JidProvider chat);

    /**
     * Dissociates a label from the given chat.
     *
     * @apiNote
     * Removes the tag set by
     * {@link #associateLabel(String, JidProvider)}, driving the
     * "Remove label" affordance on the Business chat list.
     *
     * @param labelId the label identifier
     * @param chat    the chat JID to untag
     * @throws NullPointerException if any argument is {@code null}
     */
    void dissociateLabel(String labelId, JidProvider chat);

    /**
     * Dissociates a label from the given chat.
     *
     * @apiNote
     * Convenience overload that resolves the label identifier from
     * {@link Label#id()} before delegating to
     * {@link #dissociateLabel(String, JidProvider)}.
     *
     * @param label the label
     * @param chat  the chat JID to untag
     * @throws NullPointerException if any argument is {@code null}
     */
    void dissociateLabel(Label label, JidProvider chat);

    /**
     * Associates a label with a specific message.
     *
     * @apiNote
     * Tags an individual message under the named label rather than
     * the enclosing chat, driving the message-level label affordance
     * on the Business chat list. Receivers reconcile the assignment
     * against the message's parent chat record.
     *
     * @param labelId    the label identifier
     * @param messageKey the target message key
     * @throws NullPointerException if any argument is {@code null}
     */
    void associateLabel(String labelId, MessageKey messageKey);

    /**
     * Associates a label with a specific message.
     *
     * @apiNote
     * Convenience overload that resolves the label identifier from
     * {@link Label#id()} before delegating to
     * {@link #associateLabel(String, MessageKey)}.
     *
     * @param label      the label
     * @param messageKey the target message key
     * @throws NullPointerException if any argument is {@code null}
     */
    void associateLabel(Label label, MessageKey messageKey);

    /**
     * Dissociates a label from a specific message.
     *
     * @apiNote
     * Removes the tag set by {@link #associateLabel(String, MessageKey)}.
     *
     * @param labelId    the label identifier
     * @param messageKey the target message key
     * @throws NullPointerException if any argument is {@code null}
     */
    void dissociateLabel(String labelId, MessageKey messageKey);

    /**
     * Dissociates a label from a specific message.
     *
     * @apiNote
     * Convenience overload that resolves the label identifier from
     * {@link Label#id()} before delegating to
     * {@link #dissociateLabel(String, MessageKey)}.
     *
     * @param label      the label
     * @param messageKey the target message key
     * @throws NullPointerException if any argument is {@code null}
     */
    void dissociateLabel(Label label, MessageKey messageKey);

    /**
     * Creates a new business broadcast list with the given display name
     * and recipients.
     *
     * @apiNote
     * Drives the "New broadcast list" creation flow on the Business
     * chat list. Use the returned JID to subsequently send messages
     * with {@link #sendBroadcast(JidProvider, MessageContainer)}.
     *
     * @param name       the display name of the broadcast list
     * @param recipients the recipient JIDs
     * @return the JID identifying the new broadcast list
     * @throws NullPointerException if any argument is {@code null}
     */
    Jid createBroadcastList(String name, Collection<? extends JidProvider> recipients);

    /**
     * Renames a broadcast list and replaces its recipient set.
     *
     * @apiNote
     * Drives the "Edit broadcast list" affordance on the Business
     * chat list. Submit the full new recipient list rather than a
     * delta: the underlying mutation overwrites the previous
     * participant set on every linked device.
     *
     * @param broadcastListId the JID returned by
     *                        {@link #createBroadcastList(String, Collection)}
     * @param newName         the new display name
     * @param newRecipients   the full new set of recipient JIDs
     * @throws NullPointerException if any argument is {@code null}
     */
    void editBroadcastList(JidProvider broadcastListId, String newName, Collection<? extends JidProvider> newRecipients);

    /**
     * Deletes a broadcast list.
     *
     * @apiNote
     * Drives the "Delete broadcast list" affordance on the Business
     * chat list. Outgoing messages already sent to the list keep
     * their per-recipient delivery state; only the list itself
     * disappears.
     *
     * @param broadcastListId the broadcast list JID
     * @throws NullPointerException if {@code broadcastListId} is {@code null}
     */
    void deleteBroadcastList(JidProvider broadcastListId);

    /**
     * Sends a message to every recipient of the given broadcast list.
     *
     * @apiNote
     * Drives the "Send to broadcast list" action on the Business chat
     * list: the relay fans the payload out to each participant
     * individually so recipients never see each other.
     *
     * @param broadcastListId the broadcast list JID
     * @param message         the message payload
     * @return the resulting chat-message metadata
     * @throws NullPointerException if any argument is {@code null}
     */
    ChatMessageInfo sendBroadcast(JidProvider broadcastListId, MessageContainer message);

    /**
     * Assigns the given chat to the given agent.
     *
     * @apiNote
     * Drives the "Assign chat" affordance on the Business Premium
     * chat-assignment surface. Pass the empty string for
     * {@code agentId} to clear the current assignment (or use
     * {@link #unassignChatFromAgent(JidProvider)} for a
     * self-documenting call site).
     *
     * @param chat    the chat JID
     * @param agentId the agent identifier
     * @throws NullPointerException if any argument is {@code null}
     */
    void assignChatToAgent(JidProvider chat, String agentId);

    /**
     * Unassigns the given chat from any agent.
     *
     * @apiNote
     * Convenience over
     * {@link #assignChatToAgent(JidProvider, String)} with an empty
     * agent id: drops every existing assignment for the chat in one
     * call.
     *
     * @param chat the chat JID
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void unassignChatFromAgent(JidProvider chat);

    /**
     * Records whether the assigned agent has opened the given chat.
     *
     * @apiNote
     * Drives the "agent has read this chat" indicator on the Business
     * Premium chat-assignment surface. Useful so the next time a
     * different agent opens the same chat, the local UI can render
     * whether the originally assigned agent has already seen the
     * latest messages.
     *
     * @param chat   the chat JID
     * @param opened {@code true} when the assigned agent has opened the chat
     * @throws NullPointerException  if {@code chat} is {@code null}
     * @throws IllegalStateException when the chat is not currently assigned
     */
    void editChatAssignmentOpenedStatus(JidProvider chat, boolean opened);

    /**
     * Reconciles the local view of the account-wide default
     * Disappearing Messages timer with the server.
     *
     * @apiNote
     * Use to redraw the "Default message timer" entry on the Privacy
     * panel against an authoritative copy. The timer is shared
     * across all linked devices. Typically called once after login to
     * seed the "new chat" UI, or after
     * {@link #editDefaultDisappearingMode(ChatEphemeralTimer)} to
     * confirm the change took effect. The new value replaces
     * {@link WhatsAppStore#disappearingMode()} and
     * {@link WhatsAppClientListener#onDisappearingModeChanged}
     * fires.
     *
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws NoSuchElementException          if the server reply contains
     *                                         no disappearing-mode entry
     */
    void refreshDisappearingMode();

    /**
     * Sets the per-chat disappearing-message timer.
     *
     * @apiNote
     * Drives the "Disappearing messages" toggle on the chat info
     * panel; the change is mirrored across both participants of a
     * peer chat or every member of a group, and is recorded as a
     * system message in the conversation. Use
     * {@link #editDefaultDisappearingMode(ChatEphemeralTimer)} when
     * the intent is to change the default for newly-opened chats
     * rather than an existing thread.
     *
     * @param chat  the JID of the chat whose timer is being changed
     * @param timer the new timer; {@link ChatEphemeralTimer#OFF} disables
     *              disappearing messages
     * @throws NullPointerException if any argument is {@code null}
     */
    void editEphemeralTimer(JidProvider chat, ChatEphemeralTimer timer);

    /**
     * Reads the server-side acceptance state for the named Terms-of-Service
     * and disclosure notices.
     *
     * @apiNote
     * Powers the acceptance prompts WhatsApp shows before unlocking
     * features such as updated ToS, business-bot disclosures and
     * broadcast or newsletter producer disclosures. Applications gating
     * their own feature surface on a notice call this and check the
     * per-notice verdict before allowing the user in. The returned
     * {@link TosNotices#refresh()} value is the server's recommended
     * re-poll cadence; long-running sessions typically schedule the
     * next call after that interval elapses.
     *
     * @param noticeIds the disclosure or ToS identifiers to inspect;
     *                  never {@code null} but possibly empty (an empty
     *                  collection still returns the refresh hint)
     * @return the per-notice acceptance verdicts and the recommended
     *         re-poll interval; never {@code null}
     * @throws NullPointerException            if {@code noticeIds} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws NoSuchElementException          if the server reply carries
     *                                         no {@link TosNotices} entry
     */
    TosNotices queryTosNotices(Collection<String> noticeIds);

    /**
     * Cancels a previously-submitted "Request account info" GDPR data
     * export so the server discards it before the report finishes
     * generating.
     *
     * @apiNote
     * Drives the "Cancel data export" affordance under the Settings
     * GDPR panel; useful when an application reverts a request that
     * was issued in error. The server queues the export and emails a
     * download link when ready, so a successful cancellation must
     * race the link-generation deadline.
     *
     * @param reportType which export to cancel:
     *                   {@link GdprReportType#ACCOUNT} for the full
     *                   account report or
     *                   {@link GdprReportType#NEWSLETTERS} for the
     *                   newsletter-only variant; never {@code null}
     * @throws NullPointerException            if {@code reportType} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    void cancelGdprRequest(GdprReportType reportType);

    /**
     * Fetches the WhatsApp Web push-server public key used to encrypt
     * browser push subscriptions before forwarding them to the server.
     *
     * @apiNote
     * Powers the WhatsApp Web PWA push-notification flow: the key
     * encrypts the Service Worker push subscription endpoint and
     * auth secrets before upload, so the server can deliver
     * background pushes without seeing the raw browser-side
     * credentials. Applications wiring up their own browser-style
     * push delivery call this once per session and feed the result
     * into the encryption step before registering the subscription.
     * Most non-browser embedders do not need to call this.
     *
     * @return the base-64 encoded public key when the server published
     *         one; {@link Optional#empty()} when the server returned an
     *         error (an installed
     *         {@link WhatsAppClientErrorHandler} can recover the
     *         underlying error code for richer diagnostics)
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    Optional<String> queryPushServerKey();

    /**
     * Reads the current privacy configuration of the local account.
     *
     * @apiNote
     * Mirrors the Settings privacy panel: returns the active audience
     * for every supported {@link PrivacySettingType}. The Status
     * privacy distribution is excluded because it travels on a
     * separate channel and is surfaced by
     * {@link #refreshStatusPrivacy()} instead.
     *
     * @return an immutable map from every recognised
     *         {@link PrivacySettingType} to the
     *         {@link PrivacySettingValue} the server currently enforces;
     *         never {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Map<PrivacySettingType, PrivacySettingValue> queryPrivacySettings();

    /**
     * Changes a single privacy setting on the local account without
     * touching the per-contact allow or block list.
     *
     * @apiNote
     * Drives the per-category dropdowns on the Settings privacy
     * panel for values that do not need a roster (e.g.
     * {@link PrivacySettingValue#EVERYONE},
     * {@link PrivacySettingValue#CONTACTS},
     * {@link PrivacySettingValue#NOBODY}). Use the three-argument
     * overload for {@link PrivacySettingValue#CONTACTS_EXCEPT} or
     * {@link PrivacySettingValue#CONTACTS_ONLY}, which require the
     * paired list of contacts.
     *
     * @param type  the setting to change; never {@code null}
     * @param value the new audience; must be accepted by
     *              {@link PrivacySettingType#isSupported(PrivacySettingValue)}
     * @throws NullPointerException     if {@code type} or {@code value}
     *                                  is {@code null}
     * @throws IllegalArgumentException if the value is not supported by
     *                                  the given type
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    void editPrivacySetting(PrivacySettingType type, PrivacySettingValue value);

    /**
     * Changes a single privacy setting on the local account, pairing the
     * new audience with an allow or block list of contacts when the
     * selected {@link PrivacySettingValue} requires one.
     *
     * @apiNote
     * Drives the per-category dropdowns on the Settings privacy
     * panel for the value variants that refine their audience with an
     * explicit roster:
     * {@link PrivacySettingValue#CONTACTS_EXCEPT} (block these
     * contacts) and {@link PrivacySettingValue#CONTACTS_ONLY} (allow
     * only these contacts). Observers registered via
     * {@link #addPrivacySettingChangedListener(WhatsappClientListenerConsumer.Binary)}
     * see the new state after the server acknowledges the change
     * without another round trip.
     *
     * @param type               the setting to change; never {@code null}
     * @param value              the new audience; must be accepted by
     *                           {@link PrivacySettingType#isSupported(PrivacySettingValue)}
     * @param excludedOrIncluded the per-contact allowlist
     *                           ({@link PrivacySettingValue#CONTACTS_ONLY})
     *                           or blocklist
     *                           ({@link PrivacySettingValue#CONTACTS_EXCEPT});
     *                           may be {@code null} or empty when the
     *                           value does not refine its audience with
     *                           a list
     * @throws NullPointerException     if {@code type} or {@code value}
     *                                  is {@code null}
     * @throws IllegalArgumentException if the value is not supported by
     *                                  the given type
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    void editPrivacySetting(PrivacySettingType type, PrivacySettingValue value, Collection<? extends JidProvider> excludedOrIncluded);

    /**
     * Toggles whether the local account sends "read" receipts (blue
     * double-ticks) to peers.
     *
     * @apiNote
     * Drives the "Read receipts" toggle on the Settings privacy panel;
     * the switch is symmetric. When disabled, peers stop seeing when
     * this account opens their messages, but in exchange this account
     * also stops seeing read receipts from them. Enabling read
     * receipts immediately re-enables both directions. Use this
     * convenience when the UI surfaces a simple on/off toggle rather
     * than the broader audience picker driven by
     * {@link #editPrivacySetting(PrivacySettingType, PrivacySettingValue)}.
     *
     * @param enabled {@code true} to send and receive read receipts;
     *                {@code false} to disable both directions
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    void editReadReceipts(boolean enabled);

    /**
     * Issues one or more privacy tokens that bind the local account to a
     * peer's identity.
     *
     * @apiNote
     * Powers the "trusted contact" workflow: when the local user
     * explicitly trusts a peer, this call records a server-side bond
     * that survives either side's device re-pairings, so the peer's
     * identity-key changes do not silently invalidate the relationship
     * and message-receipt privacy decisions can be short-circuited for
     * that contact. Subsequent device-list rebuilds and Signal protocol
     * session resets read the same token back to confirm the peer is
     * still trusted. Issuing a fresh token for a peer who already has
     * one of the same type overwrites the previous record.
     *
     * @param userJid    the peer whose identity the tokens cover; never
     *                   {@code null}
     * @param tokenTypes the token categories to issue; never {@code null},
     *                   must be non-empty
     * @param timestamp  the issue time the server records on each token;
     *                   never {@code null}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if {@code tokenTypes} is empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    void issuePrivacyTokens(JidProvider userJid, Collection<PrivacyTokenType> tokenTypes, Instant timestamp);

    /**
     * Reconciles the local cache of a server-side privacy "disallowed
     * list", returning either a {@code match} verdict or the fresh
     * contact roster paired with the new content digest.
     *
     * @apiNote
     * Privacy disallowed lists are the per-category exclusion rosters
     * backing the {@link PrivacySettingType#LAST_SEEN},
     * {@link PrivacySettingType#PROFILE_PIC},
     * {@link PrivacySettingType#STATUS} and
     * {@link PrivacySettingType#ADD_ME_TO_GROUPS} audience selectors.
     * Each category maintains its own sliding digest ({@code dhash}) so
     * reconciliation can hit a server-side fast path when the local
     * cache is up to date. Applications typically call this on first
     * connect for every category they care about, with {@code dhash}
     * set to the digest from the previous run (or {@code ""} on a cold
     * start), and again whenever a server-side 409 conflict on a
     * privacy-set update signals that the digest the client carried in
     * the request was already stale.
     *
     * @param jid      the requesting user's JID; the MEX query wraps the
     *                 single user in its {@code query_input} array; never
     *                 {@code null}
     * @param dhash    the digest of the locally cached roster used for
     *                 delta refreshes; pass {@code ""} (or {@code null}) on
     *                 a cold start so the server returns the full list
     * @param category the privacy domain identifier in MEX form
     *                 ({@code "ABOUT"}, {@code "GROUPADD"}, {@code "LAST"},
     *                 {@code "PROFILE"}); never {@code null}
     * @param type     the allow/deny polarity ({@code "DENYLIST"} for the
     *                 standard exclusion roster); never {@code null}
     * @return the reconciliation verdict; never {@code null}
     * @throws NullPointerException            if {@code jid}, {@code category}
     *                                         or {@code type} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    PrivacyDisallowedList queryPrivacyDisallowedList(JidProvider jid, String dhash, String category, String type);

    /**
     * Reads the current "reachout timelock" enforcement window applied
     * to the authenticated account.
     *
     * @apiNote
     * Surfaces the same compliance verdict WhatsApp shows on the
     * spam-cooldown notice when an account has been flagged for spam
     * reports or suspicious outreach: whether enforcement is
     * currently active, when the window ends, and which enforcement
     * type applies (soft warning, hard block, etc.). The post-login
     * bootstrap invokes this once; applications that want to gate
     * their own send path on the timelock should call it directly
     * and inspect the result.
     *
     * @return the parsed {@link ReachoutTimelock} verdict, or
     *         {@link Optional#empty()} when the server returned no payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<ReachoutTimelock> queryReachoutTimelock();

    /**
     * Fetches the FMX (first-message-experience) integrity signals the
     * relay attaches to a peer.
     *
     * @apiNote
     * Powers the safety nudges WhatsApp shows when a user starts a
     * conversation with an unfamiliar contact: the
     * {@code is_new_account} flag drives the "new on WhatsApp" badge,
     * while {@code is_suspicious_start_chat} drives the spam-warning
     * sheet. Callers integrating their own start-chat surface can call
     * this once the user composes the first outbound message to drive
     * the same advisories.
     *
     * @param userJid the user JID whose signals are being fetched; never
     *                {@code null}
     * @return the parsed {@link UserIntegritySignals}, or
     *         {@link Optional#empty()} when the relay returned no payload
     *         (for example because the peer is not visible to integrity
     *         scoring)
     * @throws NullPointerException            if {@code userJid} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    Optional<UserIntegritySignals> queryUserIntegritySignals(JidProvider userJid);

    /**
     * Reads the current outbound new-chat messaging quota the server
     * enforces on the authenticated account.
     *
     * @apiNote
     * Surfaces the throttling state for brand-new chat threads opened
     * per billing cycle: total quota, used quota, cycle start and end
     * timestamps, and the status flags ({@code oteStatus},
     * {@code mvStatus}, {@code cappingStatus}) the official clients
     * use to drive the throttling UI and per-message warnings. The
     * post-login bootstrap invokes this once; applications that want
     * to gate their own send path on the capping verdict should call
     * it directly and inspect the result.
     *
     * @return the parsed {@link NewChatMessageCappingInfo}, or
     *         {@link Optional#empty()} when the server returned no envelope
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<NewChatMessageCappingInfo> queryNewChatMessageCappingInfo();

    /**
     * Submits a passkey-signed integrity challenge response.
     *
     * @apiNote
     * Completes the server-initiated integrity handshake: when the
     * relay emits an integrity challenge request, the caller signs
     * the challenge with the user's WebAuthn passkey credential and
     * submits the JSON-serialised assertion through this entry point.
     * There is no automatic post-login invocation; callers wire their
     * own observer on the integrity-challenge notification and invoke
     * this method when the challenge arrives. A clean return signals
     * acceptance; rejection surfaces as
     * {@link WhatsAppServerRuntimeException} carrying the relay-side
     * {@code error_message}. The {@code prfAvailable} argument
     * reflects whether the WebAuthn assertion includes a
     * {@code prf_output} field.
     *
     * @param signedChallenge the JSON-serialised WebAuthn assertion
     *                        bytes; never {@code null}
     * @param prfAvailable    {@code true} when the assertion carries a
     *                        {@code prf_output} field
     * @throws NullPointerException            if {@code signedChallenge}
     *                                         is {@code null}
     * @throws WhatsAppServerRuntimeException  when the relay rejected
     *                                         the challenge; the
     *                                         relay-side
     *                                         {@code error_message} is
     *                                         carried in the exception
     *                                         message
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    void submitPasskeyIntegrityChallenge(byte[] signedChallenge, boolean prfAvailable);

    /**
     * Reads the registered country code for a single user.
     *
     * @apiNote
     * The country code is the two-letter ISO identifier the server
     * derives from the user's phone-number registration.
     *
     * @param userJid the user JID to query; never {@code null}
     * @return the ISO country code, or {@link Optional#empty()} when the
     *         server returned no item or no country code
     * @throws NullPointerException            if {@code userJid} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<String> queryUserCountryCode(JidProvider userJid);

    /**
     * Reads the WhatsApp username record claimed by a single user.
     *
     * @apiNote
     * WhatsApp lets users claim a global username distinct from their
     * phone-number identifier; once claimed, the username is reachable
     * for any user JID. The returned {@link UserUsername} carries the
     * claimed name together with its registration state, the instant it
     * was registered, the recovery PIN bound to it, and the lookup
     * status reported for this user.
     *
     * @param userJid the user JID to query; never {@code null}
     * @return the claimed username record, or {@link Optional#empty()}
     *         when the user has not claimed one or the server returned no
     *         item
     * @throws NullPointerException            if {@code userJid} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<UserUsername> queryUserUsername(JidProvider userJid);

    /**
     * Sets the account-wide default disappearing-message timer for all
     * new chats.
     *
     * @apiNote
     * Drives the "Default message timer" entry on the privacy panel;
     * the timer is applied to every brand-new conversation opened
     * from any linked device. Use
     * {@link #editEphemeralTimer(JidProvider, ChatEphemeralTimer)} to
     * change the timer on an existing chat.
     *
     * @param timer the new default timer; {@link ChatEphemeralTimer#OFF}
     *              disables disappearing messages by default
     * @throws NullPointerException if {@code timer} is {@code null}
     */
    void editDefaultDisappearingMode(ChatEphemeralTimer timer);

    /**
     * Creates a new WhatsApp group with the given subject and initial
     * participants, leaving disappearing messages off.
     *
     * @apiNote
     * Convenience over
     * {@link #createGroup(String, ChatEphemeralTimer, Collection)}
     * preset to {@link ChatEphemeralTimer#OFF}.
     *
     * @param subject      the group display name; never {@code null}
     * @param participants the user JIDs to add on creation; never
     *                     {@code null} and must be non-empty
     * @return the parsed metadata of the freshly created group
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the participant collection is empty
     */
    GroupMetadata createGroup(String subject, Collection<? extends JidProvider> participants);

    /**
     * Creates a new WhatsApp group with the given subject, initial
     * disappearing-message timer and participants.
     *
     * @apiNote
     * Drives the "New group" creation flow on the sidebar. The
     * freshly created group appears in the local store immediately
     * after this call returns.
     *
     * @param subject         the group display name; never {@code null}
     * @param ephemeralTimer  the initial disappearing-message timer;
     *                        {@link ChatEphemeralTimer#OFF} disables it
     * @param participants    the user JIDs to add on creation; never
     *                        {@code null} and must be non-empty
     * @return the parsed metadata of the freshly created group
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the participant collection is empty
     */
    GroupMetadata createGroup(String subject, ChatEphemeralTimer ephemeralTimer, Collection<? extends JidProvider> participants);

    /**
     * Leaves a WhatsApp group, removing the current user from the
     * participant list server-side.
     *
     * @apiNote
     * Drives the "Exit group" affordance on the group info panel.
     * Use {@link #leaveCommunity(JidProvider)} to detach from a
     * community (which exits the parent and every linked subgroup in
     * one round trip) and {@link #leaveGroup(JidProvider...)} to
     * batch multiple exits.
     *
     * @param group the JID of the group to leave; never {@code null}
     * @throws NullPointerException     if {@code group} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     */
    void leaveGroup(JidProvider group);

    /**
     * Leaves multiple WhatsApp groups in one request.
     *
     * @apiNote
     * Varargs convenience over {@link #leaveGroup(JidProvider)} that
     * batches several JIDs into a single request.
     *
     * @param groups the JIDs of the groups to leave; never {@code null}
     *               or empty
     * @throws NullPointerException     if {@code groups} or any element
     *                                  is {@code null}
     * @throws IllegalArgumentException if {@code groups} is empty or any
     *                                  JID is not a group/community
     */
    void leaveGroup(JidProvider... groups);

    /**
     * Reports whether the given group is flagged as "internal" by the
     * WhatsApp relay.
     *
     * @apiNote
     * Internal groups are the Meta-side staff or testing groups whose
     * lifecycle differs from regular consumer groups. The flag is
     * reported uniformly across regular groups, community parents,
     * default subgroups, and ordinary subgroups.
     *
     * @param groupJid the group JID to query; never {@code null}
     * @return {@code true} if the relay reports the group as internal,
     *         {@code false} otherwise (including when the relay returned
     *         no payload)
     * @throws NullPointerException            if {@code groupJid} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    boolean isGroupInternal(JidProvider groupJid);

    /**
     * Queries the metadata envelope for a group.
     *
     * @apiNote
     * Convenience over
     * {@link #queryGroupInfo(JidProvider, boolean, String, String)} that
     * fetches a cold metadata snapshot with no participant-list partial
     * hash and no query-context tag.
     *
     * @param group the group JID
     * @return the parsed metadata, or {@link Optional#empty()} when the
     *         relay returned no payload
     * @throws NullPointerException if {@code group} is {@code null}
     */
    Optional<GroupMetadata> queryGroupInfo(JidProvider group);

    /**
     * Queries the full metadata envelope for a group, excluding bot
     * participants from the participant edge list.
     *
     * @apiNote
     * Backs the group info panel for consumer groups. Use
     * {@link #queryGroupInfoIncludingBots(JidProvider, boolean, String, String)}
     * when the UI renders bots as first-class members.
     *
     * @param group             the group JID to query; never {@code null}
     * @param includeUsername   whether to hydrate the username subtree
     *                          on every participant
     * @param participantsPhash the participant-list partial hash
     *                          carried for incremental refreshes;
     *                          {@code null} on a cold fetch
     * @param queryContext      a telemetry tag identifying the UI
     *                          surface that triggered the fetch;
     *                          {@code null} when no tag applies
     * @return the parsed {@link GroupMetadata}, or
     *         {@link Optional#empty()} when the relay returned no payload
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<GroupMetadata> queryGroupInfo(JidProvider group, boolean includeUsername, String participantsPhash, String queryContext);

    /**
     * Queries the full metadata envelope for a group, including bot
     * participants in the participant edge list.
     *
     * @apiNote
     * Used by chat UIs that render bots as first-class members; the
     * non-bot variant is
     * {@link #queryGroupInfo(JidProvider, boolean, String, String)}.
     *
     * @param group             the group JID to query; never {@code null}
     * @param includeUsername   whether to hydrate the username subtree
     *                          on every participant
     * @param participantsPhash the participant-list partial hash, or
     *                          {@code null} on a cold fetch
     * @param queryContext      a telemetry tag identifying the UI
     *                          surface that triggered the fetch, or
     *                          {@code null}
     * @return the parsed {@link GroupMetadata}, or
     *         {@link Optional#empty()} when the relay returned no payload
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<GroupMetadata> queryGroupInfoIncludingBots(JidProvider group, boolean includeUsername, String participantsPhash, String queryContext);

    /**
     * Reads the current invite code attached to the given group without
     * rotating it.
     *
     * @apiNote
     * The invite code is the opaque scalar backing the shareable
     * {@code chat.whatsapp.com/<code>} link. Use
     * {@link #createGroupInviteCode(JidProvider, String)} to rotate it
     * and invalidate any previously distributed link.
     *
     * @param group        the group JID to query; never {@code null}
     * @param queryContext a telemetry tag identifying the UI surface
     *                     that triggered the fetch; {@code null} when
     *                     no tag applies
     * @return the current invite-code scalar, or
     *         {@link Optional#empty()} when the relay returned no payload
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<String> queryGroupInviteCode(JidProvider group, String queryContext);

    /**
     * Rotates the invite code for the given group, community or contact,
     * invalidating any previously distributed
     * {@code chat.whatsapp.com/<code>} link.
     *
     * @apiNote
     * Drives the "Reset link" affordance on the invite sheet. The
     * {@code entryPoint} telemetry tag is mandatory: every caller
     * must identify the UI surface that triggered the rotation.
     *
     * @param receiver   the receiver JID the code is being minted for
     *                   (group, community or contact); never {@code null}
     * @param entryPoint the UI-surface telemetry tag identifying what
     *                   triggered the rotation, e.g.
     *                   {@code "CHAT_INFO_INVITE_BUTTON"}; never
     *                   {@code null}
     * @return the freshly minted invite-code scalar
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    String createGroupInviteCode(JidProvider receiver, String entryPoint);

    /**
     * Creates a new WhatsApp community with the given name and optional
     * description, leaving disappearing messages off.
     *
     * @apiNote
     * Convenience over
     * {@link #createCommunity(String, String, ChatEphemeralTimer)}
     * preset to {@link ChatEphemeralTimer#OFF}.
     *
     * @param name        the community display name; never {@code null}
     * @param description an optional community description; {@code null}
     *                    to omit
     * @return the parsed metadata of the freshly created community
     * @throws NullPointerException if {@code name} is {@code null}
     */
    CommunityMetadata createCommunity(String name, String description);

    /**
     * Creates a new WhatsApp community with the given name, description
     * and default disappearing-message timer.
     *
     * @apiNote
     * Drives the "New community" creation flow on the sidebar; the
     * parent group is created in request-required mode, so additions
     * to subgroups require admin approval. The community appears in
     * the local store immediately after this call returns.
     *
     * @param name           the community display name; never {@code null}
     * @param description    an optional community description;
     *                       {@code null} to omit
     * @param ephemeralTimer the initial disappearing-message timer;
     *                       {@link ChatEphemeralTimer#OFF} disables it
     * @return the parsed metadata of the freshly created community
     * @throws NullPointerException   if {@code name} or
     *                                {@code ephemeralTimer} is {@code null}
     * @throws NoSuchElementException if the server response does not
     *                                carry a {@code <group>} community
     *                                subtree
     */
    CommunityMetadata createCommunity(String name, String description, ChatEphemeralTimer ephemeralTimer);

    /**
     * Deactivates a community parent group on the server, turning every
     * linked subgroup into a standalone group.
     *
     * @apiNote
     * Drives the "Deactivate community" affordance on the community
     * admin sheet. The subgroups themselves are not deleted; they
     * survive as regular standalone groups under the same JIDs.
     *
     * @param community the JID of the community to deactivate; never
     *                  {@code null}
     * @throws NullPointerException          if {@code community} is
     *                                       {@code null}
     * @throws IllegalArgumentException      if the JID is not a
     *                                       group/community
     * @throws WhatsAppServerRuntimeException when the relay returns a
     *                                       client- or server-error reply
     */
    void deactivateCommunity(JidProvider community);

    /**
     * Leaves a WhatsApp community, detaching the current user from the
     * parent group and every subgroup transitively.
     *
     * @apiNote
     * Drives the "Exit community" affordance on the community info
     * panel. The exit is applied to the parent and every linked
     * subgroup in one round trip; use
     * {@link #leaveGroup(JidProvider)} to leave a single subgroup
     * without exiting the community.
     *
     * @param community the JID of the community to leave; never
     *                  {@code null}
     * @throws NullPointerException          if {@code community} is
     *                                       {@code null}
     * @throws IllegalArgumentException      if the JID is not a
     *                                       group/community
     * @throws WhatsAppServerRuntimeException when the relay returns a
     *                                       client- or server-error reply
     */
    void leaveCommunity(JidProvider community);

    /**
     * Transfers ownership of a community to one of its existing admins.
     *
     * @apiNote
     * Drives the "Transfer community ownership" affordance on the
     * community admin sheet. The new owner must already be an admin
     * of the community.
     *
     * @param community the community JID; never {@code null}
     * @param newOwner  the JID of the new owner; never {@code null}, must
     *                  be an existing admin of the community
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code community} is not a
     *                                  group/community or {@code newOwner}
     *                                  is a group/community
     */
    void transferCommunityOwnership(JidProvider community, JidProvider newOwner);

    /**
     * Reads the pending subgroup suggestions for a community.
     *
     * @apiNote
     * Surfaces the groups the local user belongs to that the server
     * recommends moving into the community. Drives the "Suggested
     * subgroups" affordance on the community admin sheet, pairing
     * with {@link #approveSubgroupSuggestion} and
     * {@link #rejectSubgroupSuggestion} for the per-candidate verdicts.
     *
     * @param community the community JID to query; never {@code null}
     * @return the list of suggested subgroup JIDs; empty when there are
     *         no pending suggestions
     * @throws NullPointerException     if {@code community} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     */
    List<Jid> querySubgroupSuggestions(JidProvider community);

    /**
     * Approves a pending subgroup suggestion, accepting the group into
     * the community as an official subgroup.
     *
     * @apiNote
     * The {@code suggestionCreator} is the user that originally proposed
     * the candidate; it travels through
     * {@link #querySubgroupSuggestions(JidProvider)} on the
     * {@code creator.id} field of each suggestion edge.
     *
     * @param community         the parent community JID; never {@code null}
     * @param suggestedSubgroup the JID of the suggested group to approve;
     *                          never {@code null}
     * @param suggestionCreator the JID of the user that proposed the
     *                          suggestion; never {@code null}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if either group JID is not a
     *                                  group/community
     */
    void approveSubgroupSuggestion(JidProvider community, JidProvider suggestedSubgroup, JidProvider suggestionCreator);

    /**
     * Rejects a pending subgroup suggestion, declining the recommendation
     * to move the group into the community.
     *
     * @apiNote
     * The {@code suggestionCreator} is the user that originally proposed
     * the candidate; it travels through
     * {@link #querySubgroupSuggestions(JidProvider)} on the
     * {@code creator.id} field of each suggestion edge.
     *
     * @param community         the parent community JID; never {@code null}
     * @param suggestedSubgroup the JID of the suggested group to reject;
     *                          never {@code null}
     * @param suggestionCreator the JID of the user that proposed the
     *                          suggestion; never {@code null}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if either group JID is not a
     *                                  group/community
     */
    void rejectSubgroupSuggestion(JidProvider community, JidProvider suggestedSubgroup, JidProvider suggestionCreator);

    /**
     * Reads the participant count of a single subgroup by piggy-backing
     * on the community-wide participant-count MEX query.
     *
     * @apiNote
     * Cheap counter for the community admin sheet when only the
     * headline number for one subgroup is needed; the projection is
     * the cheapest available because the MEX query returns counts for
     * every subgroup in the same envelope.
     *
     * @param subgroup the subgroup JID to query; never {@code null}
     * @return the total participant count, or {@code -1} when the server
     *         response does not carry a count for the requested subgroup
     * @throws NullPointerException     if {@code subgroup} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     */
    long querySubgroupParticipantCount(JidProvider subgroup);

    /**
     * Reads the full list of subgroups that belong to a community,
     * projecting each entry into a {@link CommunityLinkedGroup}.
     *
     * @apiNote
     * Drives the "Subgroups" tab on the community info panel. The
     * first entry of the result is always the community's
     * default announcement subgroup
     * ({@link CommunityLinkedGroup#isDefaultSubgroup()} {@code == true});
     * regular subgroups follow in the order the relay surfaced them.
     *
     * @param community the non-{@code null} community JID
     * @return the list of {@link CommunityLinkedGroup} entries belonging
     *         to the community, default subgroup first
     * @throws NullPointerException          if {@code community} is
     *                                       {@code null}
     * @throws IllegalArgumentException      if the JID is not a
     *                                       group/community
     * @throws WhatsAppServerRuntimeException when the relay omits the
     *                                       default-subgroup record or
     *                                       the regular-subgroup edges
     */
    List<CommunityLinkedGroup> querySubgroups(JidProvider community);

    /**
     * Adds one or more participants to a WhatsApp group, returning the
     * per-participant server status.
     *
     * @apiNote
     * Drives the "Add participants" affordance on the group info
     * panel. Inspect the returned status map to surface per-target
     * failures (the addition can succeed for some JIDs and fail for
     * others, for instance when a peer's privacy settings forbid being
     * added to groups).
     *
     * @param group the target group JID; never {@code null}
     * @param toAdd the user JIDs to add; never {@code null} and must be
     *              non-empty
     * @return a map from each target JID to its server-assigned
     *         {@link GroupParticipantStatus};
     *         {@link GroupParticipantStatus#OK} signals success
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *                                  or the collection is empty
     */
    Map<Jid, GroupParticipantStatus> addGroupParticipants(JidProvider group, Collection<? extends JidProvider> toAdd);

    /**
     * Removes one or more participants from a WhatsApp group without
     * cascading the eviction to linked subgroups.
     *
     * @apiNote
     * Convenience over
     * {@link #removeGroupParticipants(JidProvider, Collection, boolean)}
     * preset to {@code removeLinkedGroups=false}. Use the three-argument
     * overload to evict the participants from every subgroup of the
     * parent community in one round trip.
     *
     * @param group    the target group JID; never {@code null}
     * @param toRemove the user JIDs to remove; never {@code null} and
     *                 must be non-empty
     * @return a map from each target JID to its server-assigned
     *         {@link GroupParticipantStatus}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code toRemove} is empty
     */
    Map<Jid, GroupParticipantStatus> removeGroupParticipants(JidProvider group, Collection<? extends JidProvider> toRemove);

    /**
     * Removes one or more participants from a group and optionally
     * cascades the eviction across every subgroup of the same community.
     *
     * @apiNote
     * Drives the "Remove participant" affordance on the group info
     * panel; the cascading variant matches the "Remove from
     * community" affordance on the community admin sheet.
     *
     * @param group              the target group JID; never {@code null}
     * @param toRemove           the participants to remove; never
     *                           {@code null} and must be non-empty
     * @param removeLinkedGroups when {@code true} the participants are
     *                           also evicted from every subgroup of the
     *                           parent community in one round trip
     * @return a map from each participant JID to the per-target outcome
     *         {@link GroupParticipantStatus};
     *         {@link GroupParticipantStatus#OK} signals success
     * @throws NullPointerException            if any reference argument
     *                                         is {@code null}
     * @throws IllegalArgumentException        if {@code toRemove} is empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    Map<Jid, GroupParticipantStatus> removeGroupParticipants(JidProvider group, Collection<? extends JidProvider> toRemove, boolean removeLinkedGroups);

    /**
     * Promotes one or more participants of a WhatsApp group to
     * administrator.
     *
     * @apiNote
     * Drives the "Make group admin" affordance on the group info
     * panel.
     *
     * @param group      the target group JID; never {@code null}
     * @param toPromote  the user JIDs to promote; never {@code null} and
     *                   must be non-empty
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *                                  or the collection is empty
     */
    void promoteGroupParticipants(JidProvider group, Collection<? extends JidProvider> toPromote);

    /**
     * Demotes one or more administrators of a WhatsApp group to regular
     * members.
     *
     * @apiNote
     * Drives the "Dismiss as admin" affordance on the group info
     * panel. The inverse of
     * {@link #promoteGroupParticipants(JidProvider, Collection)}.
     *
     * @param group     the target group JID; never {@code null}
     * @param toDemote  the user JIDs to demote; never {@code null} and
     *                  must be non-empty
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     *                                  or the collection is empty
     */
    void demoteGroupParticipants(JidProvider group, Collection<? extends JidProvider> toDemote);

    /**
     * Marks a sticker as a favourite on the user's account.
     *
     * @apiNote
     * Drives the "star" affordance on the sticker picker; the change
     * is mirrored to every linked device, so a sticker
     * starred on one device shows up in the favourites row on every
     * other.
     *
     * @param stickerHash the sticker file hash that uniquely identifies
     *                    the sticker across devices
     * @throws NullPointerException if {@code stickerHash} is {@code null}
     */
    void favoriteSticker(String stickerHash);

    /**
     * Unmarks a sticker as a favourite on the user's account.
     *
     * @apiNote
     * Inverse of {@link #favoriteSticker(String)}; same affordance
     * on the sticker picker when the user taps a starred sticker.
     *
     * @param stickerHash the sticker file hash that uniquely identifies
     *                    the sticker across devices
     * @throws NullPointerException if {@code stickerHash} is {@code null}
     */
    void unfavoriteSticker(String stickerHash);

    /**
     * Removes a sticker from the recent-stickers collection.
     *
     * @apiNote
     * Drives the "Remove from recents" affordance on the sticker
     * picker; the removal is mirrored to every linked device so the
     * sticker disappears from the recents row globally.
     *
     * @param stickerHash the sticker file hash that uniquely identifies
     *                    the sticker across devices
     * @throws NullPointerException if {@code stickerHash} is {@code null}
     */
    void removeRecentSticker(String stickerHash);

    /**
     * Creates and sends a new poll in the specified chat.
     *
     * @apiNote
     * Drives the "Create poll" affordance on the composer.
     * The returned {@link ChatMessageInfo} carries the server-allocated
     * message id and timestamp; pair it with
     * {@link #votePoll(MessageKey, List)} and
     * {@link #closePoll(MessageKey)} for the read and lifecycle paths.
     *
     * @param create the poll envelope carrying the chat JID, question,
     *               option labels and selectable-options count
     * @return the {@link ChatMessageInfo} carrying the sent poll
     *         creation message
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the chat is not currently
     *                                  signed in
     */
    ChatMessageInfo createPoll(PollCreate create);

    /**
     * Casts a vote on an existing poll.
     *
     * @apiNote
     * Drives the "Vote" affordance on the poll bubble. The
     * vote is delivered end-to-end encrypted to the poll creator; the
     * relay forwards the ciphertext opaquely. Re-voting on the same poll
     * by re-issuing this call replaces the previous selection.
     *
     * @param pollKey          the {@link MessageKey} of the
     *                         {@link PollCreationMessage} being voted on
     * @param selectedOptions  the ordered list of selected option labels
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code pollKey} has no parent
     *                                  JID, the referenced poll-creation
     *                                  message is missing from the local
     *                                  store, or it carries no
     *                                  {@code messageSecret}
     */
    void votePoll(MessageKey pollKey, List<String> selectedOptions);

    /**
     * Closes the given poll so that no further votes can be cast.
     *
     * @apiNote
     * Drives the "Stop poll" affordance on the poll bubble.
     * Receivers mark the poll as closed and refuse to accept additional
     * {@code vote} stanzas for it.
     *
     * @param pollKey the {@link MessageKey} of the
     *                {@link PollCreationMessage} to close
     * @throws NullPointerException     if {@code pollKey} is {@code null}
     * @throws IllegalArgumentException if {@code pollKey} has no parent JID
     */
    void closePoll(MessageKey pollKey);

    /**
     * Sends a welcome-message request to the given WhatsApp bot and
     * records that the welcome has been triggered.
     *
     * @apiNote
     * Drives the first-time open flow for a bot chat: the welcome message
     * is shown once and the record is mirrored to every linked device so
     * subsequent opens skip the prompt.
     *
     * @param botJid the JID of the bot to send the welcome request to
     * @throws NullPointerException if {@code botJid} is {@code null}
     */
    void sendBotWelcomeRequest(JidProvider botJid);

    /**
     * Renames an AI conversation thread owned by the given bot.
     *
     * @apiNote
     * Drives the "Rename thread" affordance on the Meta AI
     * conversation list; the new title is mirrored to every linked
     * device so the renamed thread shows the same name across them.
     *
     * @param chatJid  the bot JID owning the thread, encoded as a plain
     *                 string JID (e.g. {@code 12345@bot})
     * @param threadId the AI thread identifier
     * @param newName  the new thread title
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code newName} is blank
     */
    void renameAiThread(String chatJid, String threadId, String newName);

    /**
     * Deletes an AI conversation thread owned by the given bot.
     *
     * @apiNote
     * Drives the "Delete thread" affordance on the Meta AI
     * conversation list; the deletion is mirrored to every linked
     * device so the thread disappears across them.
     *
     * @param chatJid  the bot JID owning the thread, encoded as a plain
     *                 string JID
     * @param threadId the AI thread identifier
     * @throws NullPointerException if any argument is {@code null}
     */
    void deleteAiThread(String chatJid, String threadId);

    /**
     * Adds the given chat to the favourites list.
     *
     * @apiNote
     * Drives the "Pin to favourites" affordance on the sidebar;
     * the favourites list is mirrored to every linked device so the
     * same set of chats surfaces under the favourites filter across
     * them.
     *
     * @param chat the JID of the chat to favourite
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void favoriteChat(JidProvider chat);

    /**
     * Removes the given chat from the favourites list.
     *
     * @apiNote
     * Inverse of {@link #favoriteChat(JidProvider)}; same surface on
     * the sidebar.
     *
     * @param chat the JID of the chat to unfavourite
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void unfavoriteChat(JidProvider chat);

    /**
     * Adds a note to the given chat.
     *
     * @apiNote
     * Drives the "Add note" affordance on the Business chat info
     * panel; the note is private to the local account and mirrored
     * to every linked device. The returned id is the same identifier
     * receivers observe in the synced mutation; pair it with
     * {@link #editNoteOnChat(JidProvider, String, String)} and
     * {@link #deleteNoteFromChat(JidProvider, String)} for the edit
     * and delete paths.
     *
     * @param chat     the chat the note is attached to
     * @param noteText the free-text body of the note
     * @return the generated note id
     * @throws NullPointerException if any argument is {@code null}
     */
    String addNoteToChat(JidProvider chat, String noteText);

    /**
     * Updates the text of an existing note attached to the given chat.
     *
     * @apiNote
     * Drives the "Edit note" affordance on the Business chat info
     * panel.
     *
     * @param chat     the chat the note is attached to
     * @param noteId   the identifier of the note to update
     * @param newText  the new note text
     * @throws NullPointerException if any argument is {@code null}
     */
    void editNoteOnChat(JidProvider chat, String noteId, String newText);

    /**
     * Deletes an existing note from the given chat.
     *
     * @apiNote
     * Drives the "Delete note" affordance on the Business chat info
     * panel; the deletion is mirrored to every linked device.
     *
     * @param chat   the chat the note is attached to
     * @param noteId the identifier of the note to delete
     * @throws NullPointerException if any argument is {@code null}
     */
    void deleteNoteFromChat(JidProvider chat, String noteId);

    /**
     * Pins the referenced message inside its chat for every participant.
     *
     * @apiNote
     * Drives the "Pin message" affordance on the message bubble.
     * The pin is visible to every participant of the chat; use
     * {@link #unpinMessage(MessageKey)} to remove it.
     *
     * @param msgKey the key of the message to pin
     * @throws NullPointerException     if {@code msgKey} is {@code null}
     * @throws IllegalArgumentException if {@code msgKey} has no parent JID
     */
    void pinMessage(MessageKey msgKey);

    /**
     * Removes the pin from the referenced message inside its chat for
     * every participant.
     *
     * @apiNote
     * Inverse of {@link #pinMessage(MessageKey)}; same surface on
     * the message bubble.
     *
     * @param msgKey the key of the message to unpin
     * @throws NullPointerException     if {@code msgKey} is {@code null}
     * @throws IllegalArgumentException if {@code msgKey} has no parent JID
     */
    void unpinMessage(MessageKey msgKey);

    /**
     * Changes the user's preferred UI locale.
     *
     * @apiNote
     * Drives the Settings language picker; the new locale is
     * mirrored to every linked device on the {@code CRITICAL_BLOCK}
     * app-state collection so paired devices switch their UI in
     * lockstep.
     *
     * @param locale the new BCP-47 locale tag (e.g. {@code "en_US"});
     *               never {@code null}
     * @throws NullPointerException            if {@code locale} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editLocale(String locale);

    /**
     * Changes the user's disable-link-previews privacy setting.
     *
     * @apiNote
     * Drives the "Disable link previews" toggle on the Settings
     * privacy panel; the new value is mirrored to every linked
     * device so the URL-unfurl behaviour stays consistent across
     * them.
     *
     * @param disabled {@code true} to disable link previews,
     *                 {@code false} to allow them
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editDisableLinkPreviews(boolean disabled);

    /**
     * Toggles the user's 12-hour / 24-hour clock display preference.
     *
     * @apiNote
     * Drives the "Use 24-hour time" toggle in Settings; eagerly
     * writes through to
     * {@link WhatsAppStore#setTwentyFourHourFormat(boolean)} so
     * local reads see the new value before the linked-device fanout
     * returns.
     *
     * @param twentyFourHourFormat {@code true} for 24-hour display,
     *                             {@code false} for 12-hour display
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editTwentyFourHourFormat(boolean twentyFourHourFormat);

    /**
     * Toggles the Maiba AI features preference via the boolean shortcut.
     *
     * @apiNote
     * Convenience entry point for the "Enable Meta AI" toggle
     *
     * @param enabled whether Meta AI features should be enabled or not
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editAIFeaturesEnabled(boolean enabled);

    /**
     * Toggles whether incoming messages auto-unarchive their chat.
     *
     * @apiNote
     * Drives the "Keep chats archived" toggle in Settings; eagerly
     * writes through to
     * {@link WhatsAppStore#setUnarchiveChats(boolean)} so subsequent
     * local reads observe the new preference immediately.
     *
     * @param unarchive {@code true} to auto-unarchive a chat on each
     *                  inbound message, {@code false} to keep archived
     *                  chats archived
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editUnarchiveChatsOnNewMessage(boolean unarchive);

    /**
     * Updates the per-account notification-activity preference.
     *
     * @apiNote
     * Surfaces the forward-looking notification-activity setting
     * (action index 60, collection {@code REGULAR}); eagerly writes
     * through to
     * {@link WhatsAppStore#setNotificationActivitySetting(NotificationActivitySettingAction.NotificationActivitySetting)}
     * so subsequent local reads see the new value.
     *
     * @param setting the non-{@code null}
     *                {@link NotificationActivitySettingAction.NotificationActivitySetting}
     *                to persist
     * @throws NullPointerException            if {@code setting} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editNotificationActivity(NotificationActivitySettingAction.NotificationActivitySetting setting);

    /**
     * Exchanges a CTWA email-recovery verification code for an
     * Ads-Manager session bundle.
     *
     * @apiNote
     * Backs the second step of the click-to-WhatsApp ads recovery
     * flow: the user has typed the code received by email, this call
     * trades the code for the Facebook access token and Ads-Manager
     * session cookies needed to drive the linked Ads surface. The
     * {@code 431 TOO_MANY_ATTEMPTS} and {@code 432 INCORRECT_NONCE}
     * relay arms surface as {@link WhatsAppServerRuntimeException}
     * with their original numeric codes.
     *
     * @param code        the non-{@code null} user-supplied
     *                    verification code
     * @param fromUserJid the optional {@code from} echo carried on the
     *                    outbound IQ; may be {@code null}
     * @return an {@link Optional} carrying the parsed
     *         {@link CtwaAccessTokenSession}, or empty when no
     *         documented variant parsed
     * @throws NullPointerException            if {@code code} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with one of the
     *                                         documented client/server
     *                                         error variants
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<CtwaAccessTokenSession> queryAccessTokenAndSessionCookies(String code, JidProvider fromUserJid);

    /**
     * Issues an account-binding nonce for the WhatsApp-to-Facebook
     * business linking flow.
     *
     * @apiNote
     * Called by the linking flow that ties the local WhatsApp
     * Business account to a Facebook business identity; the returned
     * nonce is then echoed back to the Meta-side surface as proof of
     * possession.
     *
     * @param identifierScope the optional {@code <identifier scope/>}
     *                        attribute; {@code null} omits the child
     * @return an {@link Optional} carrying the issued nonce, or empty
     *         when the relay did not return a documented
     *         {@code Success} variant
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client/server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<String> queryAccountNonce(String identifierScope);

    /**
     * Queries the marketing-messages, Meta-Verified, and GenAI
     * feature-eligibility status of the local business account.
     *
     * @apiNote
     * Drives the SMB feature-discovery surfaces (marketing campaigns,
     * Meta Verified badge, GenAI compose). Each boolean argument
     * selects whether the corresponding projection is requested; the
     * relay only echoes back the feature blocks the client opted into.
     *
     * @param featuresMetaVerified      whether to request the
     *                                  Meta-Verified projection
     * @param featuresMarketingMessages whether to request the
     *                                  marketing-messages projection
     * @param featuresGenai             whether to request the GenAI
     *                                  projection
     * @return an {@link Optional} carrying the parsed
     *         {@link BusinessEligibility}, or empty on no-parse
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client/server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<BusinessEligibility> queryBusinessEligibility(boolean featuresMetaVerified, boolean featuresMarketingMessages, boolean featuresGenai);

    /**
     * Lists the Facebook, Instagram, and WhatsApp-ad-identity accounts
     * currently linked to this business account.
     *
     * @apiNote
     * Backs the "Linked accounts" panel in the SMB settings: surfaces
     * the optional Facebook page, Facebook business, Instagram
     * professional, and WhatsApp-ad-identity sub-tuples relevant to
     * cross-surface attribution.
     *
     * @return an {@link Optional} carrying the parsed
     *         {@link BusinessLinkedAccounts}, or empty on no-parse
     * @throws WhatsAppServerRuntimeException  if the relay returned the
     *                                         {@code Forbidden} arm or
     *                                         any other documented
     *                                         client/server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<BusinessLinkedAccounts> queryLinkedAccounts();

    /**
     * Fetches the current SMB-data-sharing-with-Meta consent value
     * stored server-side.
     *
     * @apiNote
     * Drives the "Share business data with Meta" toggle position in
     * SMB privacy settings; surfaces the
     * {@link BusinessDataSharingConsent} variant currently recorded
     * for this account.
     *
     * @return an {@link Optional} carrying the parsed
     *         {@link BusinessDataSharingConsent}, or empty on no-parse
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client/server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<BusinessDataSharingConsent> queryBusinessPrivacySetting();

    /**
     * Persists the SMB-data-sharing-with-Meta consent value
     * server-side.
     *
     * @apiNote
     * Drives the SMB "Share business data with Meta" choice change;
     * passing {@code null} clears any stored choice so the relay
     * reverts to the unset state.
     *
     * @param dataSharingConsent the {@link BusinessDataSharingConsent}
     *                           to persist; {@code null} to clear the
     *                           stored value
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         change with a documented
     *                                         client/server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editBusinessPrivacySetting(BusinessDataSharingConsent dataSharingConsent);

    /**
     * Records a per-contact message-feedback preference.
     *
     * @apiNote
     * Backs the per-conversation feedback actions exposed by SMB
     * (block, unblock, report, and similar opaque verbs) by tagging
     * a specific contact with a {@link MessageFeedbackAction} and an
     * optional free-form annotation.
     *
     * @param action   the non-{@code null} feedback action verb
     * @param jid      the non-{@code null} target contact JID
     * @param feedback the optional free-form annotation; may be
     *                 {@code null}
     * @throws NullPointerException            if {@code action} or
     *                                         {@code jid} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         change with a documented
     *                                         client/server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void updateMessageFeedbackPreference(MessageFeedbackAction action, JidProvider jid, String feedback);

    /**
     * Convenience overload of
     * {@link #queryMeteredMessagingCheckout(List, boolean, boolean, String, List)}
     * for the basic quote-only case.
     *
     * @apiNote
     * Forwards {@code false} for both ad-account and dedupe-skip
     * markers, omits the offer id, and supplies no pending campaigns.
     *
     * @param participants the non-{@code null} prospective message
     *                     recipients
     * @return an {@link Optional} carrying the checkout quote, or
     *         empty when the relay returned no payload
     * @throws NullPointerException if {@code participants} is {@code null}
     */
    Optional<BusinessMeteredMessagingCheckout> queryMeteredMessagingCheckout(List<? extends JidProvider> participants);

    /**
     * Queries the metered-messaging checkout for the SMB
     * marketing-messages feature.
     *
     * @apiNote
     * Drives the marketing-campaign creation surface that needs to
     * surface the per-recipient cost, the remaining campaign quota,
     * and the per-recipient eligibility tuple before letting the user
     * confirm a paid broadcast. The optional markers select extra
     * projections returned by the relay.
     *
     * @param participants     the non-{@code null} recipient JIDs;
     *                         must be in the 1..2000 range
     * @param useAdAccount     whether to attach the
     *                         {@code <use_ad_account/>} marker
     * @param skipDedupe       whether to attach the
     *                         {@code <skip_dedupe/>} marker
     * @param offerId          the optional offer id; may be {@code null}
     * @param pendingCampaigns optional pending-campaign list (max 200);
     *                         may be {@code null}
     * @return an {@link Optional} carrying the parsed
     *         {@link BusinessMeteredMessagingCheckout}, or empty on
     *         no-parse
     * @throws NullPointerException            if {@code participants}
     *                                         is {@code null}
     * @throws IllegalArgumentException        if {@code participants}
     *                                         is outside the supported
     *                                         range or
     *                                         {@code pendingCampaigns}
     *                                         exceeds 200 entries
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client/server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<BusinessMeteredMessagingCheckout> queryMeteredMessagingCheckout(List<? extends JidProvider> participants, boolean useAdAccount, boolean skipDedupe, String offerId, List<BusinessMeteredMessagingPendingCampaign> pendingCampaigns);

    /**
     * Issues a silent CTWA access-token nonce probe.
     *
     * @apiNote
     * Backs the silent-recovery flow that decides whether to bother
     * the user with an email-recovery prompt: the relay either
     * accepts the silent path and returns a
     * {@link CtwaSilentNonceResult.Issued}, or refuses and reports
     * the email address that must confirm account ownership via
     * {@link CtwaSilentNonceResult.RecoveryRequired}.
     *
     * @param fromUserJid the optional {@code from} echo on the
     *                    outbound IQ; may be {@code null}
     * @return an {@link Optional} carrying the parsed
     *         {@link CtwaSilentNonceResult}, or empty on no-parse
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client/server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<CtwaSilentNonceResult> querySilentNonce(JidProvider fromUserJid);

    /**
     * Triggers dispatch of a CTWA account-recovery nonce.
     *
     * @apiNote
     * Used by the CTWA recovery flow when the silent nonce probe
     * came back as {@link CtwaSilentNonceResult.RecoveryRequired};
     * the relay then ships the recovery nonce out-of-band, typically
     * by email, so the user can paste it back into
     * {@link #queryAccessTokenAndSessionCookies(String, JidProvider)}.
     *
     * @param fromUserJid the optional {@code from} echo on the
     *                    outbound IQ; may be {@code null}
     * @return {@code true} when the relay confirms dispatch,
     *         {@code false} on failure or non-parse
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client/server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    boolean sendAccountRecoveryNonce(JidProvider fromUserJid);

    /**
     * Uploads CTWA-ad-media descriptors to the native-ad payload
     * service.
     *
     * @apiNote
     * Backs the CTWA ad authoring flow: after the bytes are uploaded
     * to the media servers via the regular upload pipeline, this
     * call binds the resulting (id, type) descriptors to the
     * native-ad payload so the ad creative can reference them.
     *
     * @param media     the optional primary media descriptor; may be
     *                  {@code null}
     * @param mediaList the non-{@code null} additional descriptors
     *                  (0..10)
     * @throws NullPointerException            if {@code mediaList} is {@code null}
     * @throws IllegalArgumentException        if {@code mediaList}
     *                                         exceeds 10 entries
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client/server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void uploadAdMedia(CtwaAdMediaEntry media, List<CtwaAdMediaEntry> mediaList);

    /**
     * Records the user's acceptance of a versioned payments
     * Terms-of-Service document.
     *
     * @apiNote
     * Backs the BR-PIX and UPI consumer payment onboarding screens
     * that present a versioned ToS modal; this method records the
     * acceptance so the relay considers the user enrolled at the
     * specified version. Select the per-market consumer variant by
     * passing the corresponding {@link PaymentsTosV3ConsumerVariant}.
     *
     * @param acceptPayTosVersion the integer ToS version being accepted
     * @param variant             the non-{@code null} consumer-variant
     *                            payload selecting BR/UPI
     * @throws NullPointerException            if {@code variant} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the consent
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editPaymentsTosV3Acceptance(int acceptPayTosVersion, PaymentsTosV3ConsumerVariant variant);

    /**
     * Creates a Brazil-specific custom payment method on the local
     * business account.
     *
     * @apiNote
     * Backs the BR SMB "Add payment method" flow that lets a
     * merchant declare a {@code "pay_on_delivery"} or
     * {@code "pix_key"} channel alongside 1..5 metadata key-value
     * pairs. The supplied {@link BrazilCustomPaymentMethodCreate}
     * carries the device id, method type, optional update marker,
     * optional p2p/p2m flow, and the metadata tuples.
     *
     * @param create the non-{@code null} create request payload
     * @return the freshly created {@link BrazilCustomPaymentMethod}
     * @throws NullPointerException            if {@code create} or any
     *                                         non-nullable nested
     *                                         argument is {@code null}
     * @throws IllegalArgumentException        if the metadata list is
     *                                         empty or has more than 5
     *                                         entries
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         create with an IQ-level
     *                                         error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    BrazilCustomPaymentMethod createBrazilCustomPaymentMethod(BrazilCustomPaymentMethodCreate create);

    /**
     * Publishes a newsletter post or question response.
     *
     * @apiNote
     * Drives the newsletter compose flow: the helper picks between
     * the brand-new-message wire shape and the question-response wire
     * shape based on whether the
     * {@link NewsletterPublishMessageRequest} carries a target
     * message server id, and waits for the application-level ack.
     *
     * @param newsletterJid the non-{@code null} target newsletter JID
     * @param request       the non-{@code null} publish request
     * @return the non-{@code null} relay {@link NewsletterPublishAck}
     * @throws NullPointerException            if either argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         negative ack or the
     *                                         envelope did not parse
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    NewsletterPublishAck publishNewsletterMessage(JidProvider newsletterJid, NewsletterPublishMessageRequest request);

    /**
     * Publishes a newsletter status post.
     *
     * @apiNote
     * Sister of
     * {@link #publishNewsletterMessage(JidProvider, NewsletterPublishMessageRequest)}
     * scoped to the status namespace; picks between brand-new-status
     * and status-reaction-on-existing-status wire shapes based on
     * whether the {@link NewsletterPublishStatusRequest} carries a
     * target status server id.
     *
     * @param newsletterJid the non-{@code null} target newsletter JID
     * @param request       the non-{@code null} publish request
     * @return the non-{@code null} relay {@link NewsletterPublishAck}
     * @throws NullPointerException            if either argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         negative ack or the
     *                                         envelope did not parse
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    NewsletterPublishAck publishNewsletterStatus(JidProvider newsletterJid, NewsletterPublishStatusRequest request);

    /**
     * Dispatches a typed legacy-IQ request and returns the raw
     * reply.
     *
     * @apiNote
     * Escape hatch for callers that hand-craft an
     * {@link IqOperation.Request} and want the raw {@link Node}
     * back instead of a model projection; the higher-level helpers
     * delegate through this when no typed model is wired up yet.
     *
     * @param request the non-{@code null} typed legacy-IQ request
     * @return the non-{@code null} raw inbound reply node
     * @throws NullPointerException            if {@code request} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Node sendNode(IqOperation.Request request);

    /**
     * Convenience overload of
     * {@link #queryBusinessCatalogProducts(JidProvider, List, int, int, String)}
     * for ordinary (non-direct-connection) merchants.
     *
     * @apiNote
     * Forwards a {@code null} direct-connection encrypted-info blob,
     * which is the regular-merchant case; use the full overload when
     * fetching catalog entries for a direct-connection merchant.
     *
     * @param catalogJid the merchant's catalog JID
     * @param productIds the product ids to fetch
     * @param width      the requested image width
     * @param height     the requested image height
     * @return the products
     * @throws NullPointerException if {@code catalogJid} is {@code null}
     */
    List<BusinessProduct> queryBusinessCatalogProducts(JidProvider catalogJid, List<String> productIds, int width, int height);

    /**
     * Fetches the WhatsApp Business catalog product list for a
     * merchant by id.
     *
     * @apiNote
     * Drives the storefront product-detail surface that needs a
     * specific set of catalog entries (typically the ones referenced
     * by a chat-attached order or by a cart). The returned list is
     * in the relay's reply order, normally matching
     * {@code productIds}, so callers can zip the two lists; entries
     * the relay could not resolve surface as {@link BusinessProduct}
     * instances with {@link BusinessProduct#invalid()} set to
     * {@code true}.
     *
     * @param catalogJid                    the non-{@code null} merchant
     *                                      catalog JID
     * @param productIds                    the non-{@code null} and
     *                                      non-empty list of catalog
     *                                      product ids to fetch
     * @param width                         the desired image width in
     *                                      pixels
     * @param height                        the desired image height in
     *                                      pixels
     * @param directConnectionEncryptedInfo the optional direct-connection
     *                                      blob carried for
     *                                      direct-connection merchants;
     *                                      may be {@code null}
     * @return the non-{@code null} parsed product list, possibly empty
     * @throws NullPointerException            if {@code catalogJid} or
     *                                         {@code productIds} is {@code null}
     * @throws IllegalArgumentException        if {@code productIds} is empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error variant
     */
    List<BusinessProduct> queryBusinessCatalogProducts(JidProvider catalogJid, List<String> productIds, int width, int height, String directConnectionEncryptedInfo);

    /**
     * Attaches an uploaded cover photo to the authenticated user's
     * Business profile.
     *
     * @apiNote
     * Backs the SMB "Change cover photo" surface; the caller is
     * expected to have already uploaded the JPEG via the media
     * upload pipeline, so this method only ships the resulting
     * {@code (id, ts, token)} triple that wires the upload to the
     * profile.
     *
     * @param id    the upload id returned by the media upload pipeline
     * @param ts    the non-{@code null} upload timestamp
     * @param token the non-{@code null} opaque upload token issued by
     *              the media server
     * @throws NullPointerException            if {@code ts} or
     *                                         {@code token} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         documented client- or
     *                                         server-error variant
     */
    void editBusinessCoverPhoto(long id, Instant ts, byte[] token);

    /**
     * Clears one or more server-tracked dirty-bit entries.
     *
     * @apiNote
     * Called after the client has fully ingested the resources the
     * relay marked dirty (typical examples: {@code account_sync},
     * {@code groups}, {@code blocklist}). Without the
     * acknowledgement the relay re-publishes the same info-bulletin
     * stanzas on every reconnect. Each
     * {@code (resource, timestamp)} entry maps to one
     * {@code <clean/>} child on the outbound IQ.
     *
     * @param dirtyBits the non-{@code null} and non-empty
     *                  {@code (type, timestamp)} entries to clear
     * @throws NullPointerException            if {@code dirtyBits} is {@code null}
     * @throws IllegalArgumentException        if {@code dirtyBits} is empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         documented client- or
     *                                         server-error variant
     */
    void clearDirtyBits(Map<String, Long> dirtyBits);

    /**
     * Transcodes the given source bytes to the wire format expected for
     * {@code provider}'s slot, uploads the encoded payload to WhatsApp's
     * CDN, and populates both the codec-derived and CDN metadata fields
     * on {@code provider}.
     *
     * @apiNote
     * The bytes are first piped through an internal transcoder pipeline
     * keyed by {@link MediaProvider#mediaPath()}: image sources are
     * re-encoded as MJPEG with a derived micro-thumbnail, videos as
     * H.264 / AAC MP4 with a faststart moov, audio as 48 kHz stereo AAC
     * (M4A) or, for push-to-talk
     * {@link com.github.auties00.cobalt.model.message.media.AudioMessage
     * audio messages}, as 16 kHz mono Opus (OGG) plus the wire-format
     * waveform, stickers as a 512x512 WebP, documents pass through with
     * a PDF page-zero thumbnail and page count when applicable, and slots
     * without a dedicated pipeline (application-state and history-sync
     * blobs, profile pictures, newsletter media) pass through verbatim.
     * The transcoder applies its derived fields (width, height, duration,
     * mimetype, jpegThumbnail, waveform, pageCount, mediaSize) directly
     * to {@code provider}; the CDN upload then writes {@code mediaUrl},
     * {@code mediaDirectPath}, {@code mediaKey},
     * {@code mediaKeyTimestamp}, {@code mediaSha256}, and
     * {@code mediaEncryptedSha256} so the same {@link MediaProvider} can
     * immediately be embedded in an outgoing {@link MessageContainer}
     * and dispatched through {@link #sendMessage(JidProvider, MessageContainer)}.
     * Uses the cached media connection held by the store and blocks
     * until the first {@code media_conn} refresh has landed.
     *
     * @param provider    the media provider to populate; codec-derived
     *                    and CDN metadata fields are written in place
     * @param inputStream the raw plaintext bytes to transcode and upload
     * @return {@code true} if the upload succeeded; {@code false} if
     *         the {@link MediaProvider#mediaPath()} carries no CDN
     *         path and the operation was skipped
     * @throws NullPointerException              if any argument is
     *                                           {@code null}
     * @throws WhatsAppMediaException.Processing if the transcoder
     *                                           pipeline fails to
     *                                           decode, encode, or
     *                                           buffer the source
     * @throws WhatsAppMediaException.Upload     if every candidate
     *                                           CDN host failed, the
     *                                           response was
     *                                           malformed, or an I/O
     *                                           error occurred
     *                                           during the POST
     * @throws WhatsAppMediaException.Connection if the calling thread
     *                                           is interrupted while
     *                                           waiting for the
     *                                           cached media
     *                                           connection
     * @throws WhatsAppSessionException.Closed   if the socket is no
     *                                           longer open
     */
    boolean uploadMedia(MediaProvider provider, InputStream inputStream);

    /**
     * Transcodes the source file and uploads the result to WhatsApp's
     * CDN, mutating the provider with the resulting metadata.
     *
     * @apiNote
     * Preferred entry point when the caller already has a file on disk:
     * skips the source-side temp-file spill that the
     * {@link #uploadMedia(MediaProvider, InputStream)} adapter performs
     * for non-file-backed streams. The file is not modified or deleted
     * by this method.
     *
     * @param provider the media provider to populate
     * @param source   the raw plaintext file
     * @return {@code true} if the upload succeeded; {@code false} if
     *         the {@link MediaProvider#mediaPath()} carries no CDN path
     * @throws NullPointerException              if any argument is
     *                                           {@code null}
     * @throws WhatsAppMediaException.Processing if the transcoder
     *                                           pipeline fails to
     *                                           decode, encode, or
     *                                           buffer the source
     * @throws WhatsAppMediaException.Upload     if every candidate CDN
     *                                           host failed, the
     *                                           response was malformed,
     *                                           or an I/O error
     *                                           occurred during the
     *                                           POST
     * @throws WhatsAppMediaException.Connection if the calling thread
     *                                           is interrupted while
     *                                           waiting for the cached
     *                                           media connection
     * @throws WhatsAppSessionException.Closed   if the socket is no
     *                                           longer open
     */
    boolean uploadMedia(MediaProvider provider, Path source);

    /**
     * Downloads the encrypted media described by the given provider
     * from WhatsApp's CDN and returns a stream over the decrypted
     * bytes.
     *
     * @apiNote
     * Retrieves the actual content of a piece of media received in a
     * chat: the photo, video, voice message, document, sticker, or
     * status the user wants to view, save, or forward. WhatsApp keeps
     * this content scrambled on its servers, so this fetches it and
     * hands back the original, ready-to-use bytes. Read the stream to
     * obtain the media, and close it once done.
     *
     * @param provider the received media to download
     * @return a decrypting {@link InputStream} positioned at the
     *         start of the plaintext payload, never {@code null}
     * @throws NullPointerException              if {@code provider}
     *                                           is {@code null}
     * @throws WhatsAppMediaException.Download   if every candidate
     *                                           CDN host failed, the
     *                                           encrypted-hash check
     *                                           failed, or an I/O
     *                                           error occurred
     *                                           during the GET
     * @throws WhatsAppMediaException.Connection if the calling thread
     *                                           is interrupted while
     *                                           waiting for the
     *                                           cached media
     *                                           connection
     * @throws WhatsAppSessionException.Closed   if the socket is no
     *                                           longer open
     */
    InputStream downloadMedia(MediaProvider provider);

    /**
     * Records dismissal of a single Terms-of-Service notice.
     *
     * @apiNote
     * Backs the "Got it" tap on legal-update prompts that do not
     * require explicit acceptance; tells the relay it can stop
     * re-publishing the notice. Use
     * {@link #queryTosNotices(Collection)} first to learn which
     * notice ids are currently outstanding.
     *
     * @param noticeId the non-{@code null} notice id to delete
     * @throws NullPointerException            if {@code noticeId} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         documented client- or
     *                                         server-error variant
     */
    void deleteTosNotice(String noticeId);

    /**
     * Records acceptance of one or more Terms-of-Service notices.
     *
     * @apiNote
     * Marks the user as having accepted each requested legal-update
     * or disclosure prompt, unlocking any feature gated on it. Use
     * {@link #queryTosNotices(Collection)} first to enumerate the
     * outstanding notice ids and to verify the acceptance later.
     *
     * @param noticeIds the non-{@code null} notice ids being
     *                  accepted; may be empty
     * @throws NullPointerException            if {@code noticeIds} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         documented client- or
     *                                         server-error variant
     */
    void acknowledgeTosNotices(List<String> noticeIds);

    /**
     * Fetches the relay's digest of the local Signal pre-key bundle.
     *
     * @apiNote
     * Drives the periodic key-resync check: the local client
     * recomputes the same digest from its own bundle and compares,
     * triggering a fresh upload via
     * {@link #uploadSignalPreKeys(SignalPreKeyBundle)} when the two
     * diverge. The returned {@link IdentityKeyDigest} carries the
     * registration id, key-bundle type marker, identity public key,
     * signed pre-key, one-time pre-key identifiers, and the SHA-1
     * digest over the concatenated material.
     *
     * @return an {@link Optional} carrying the parsed
     *         {@link IdentityKeyDigest}, or empty when the relay had
     *         no record on file
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         documented client- or
     *                                         server-error variant
     */
    Optional<IdentityKeyDigest> queryKeyDigest();

    /**
     * Fetches the long-term identity public keys for one or more
     * device JIDs.
     *
     * @apiNote
     * Used when the local client is about to start a Signal session
     * with a peer device and needs that device's identity public key
     * to verify the fingerprint. Devices the relay could not resolve
     * are omitted from the result; each returned {@link IdentityKey}
     * carries the device it belongs to.
     *
     * @param deviceJids the non-{@code null} and non-empty list of
     *                   devices whose identity keys to fetch
     * @return the resolved {@link IdentityKey} entries, one per device
     *         the relay returned a key for
     * @throws NullPointerException            if {@code deviceJids} is {@code null}
     * @throws IllegalArgumentException        if {@code deviceJids} is empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         documented client- or
     *                                         server-error variant
     */
    List<IdentityKey> queryIdentityKeys(List<? extends JidProvider> deviceJids);

    /**
     * Rotates the local signed pre-key on the relay.
     *
     * @apiNote
     * Called periodically (typically every few days) so that
     * subsequent inbound Signal sessions derive their shared secret
     * from a fresh signed pre-key; this limits the window over
     * which a previously leaked key can be used to start new
     * sessions with the local user.
     *
     * @param signedPreKey the non-{@code null} freshly-minted
     *                     {@link SignalSignedPreKey}
     * @throws NullPointerException            if {@code signedPreKey} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         documented client- or
     *                                         server-error variant
     */
    void rotateSignedPreKey(SignalSignedPreKey signedPreKey);

    /**
     * Uploads a fresh batch of one-time pre-keys to the relay.
     *
     * @apiNote
     * Replenishes the relay-side one-time pre-key pool that drains
     * as remote devices fetch keys to start new Signal sessions
     * with the local user. Call when
     * {@link #queryKeyDigest()} indicates the pool is below the
     * client's refill watermark.
     *
     * @param bundle the non-{@code null} {@link SignalPreKeyBundle} to upload
     * @throws NullPointerException            if {@code bundle} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         documented client- or
     *                                         server-error variant
     */
    void uploadSignalPreKeys(SignalPreKeyBundle bundle);

    /**
     * Uploads the registration-time pre-key bundle to the relay.
     *
     * @apiNote
     * Runs once during device registration handshake; distinct from
     * {@link #uploadSignalPreKeys(SignalPreKeyBundle)} which runs
     * after registration to replenish the one-time pre-key pool.
     * Same payload shape, separate wire IQ pair.
     *
     * @param bundle the non-{@code null} {@link SignalPreKeyBundle} to upload
     * @throws NullPointerException            if {@code bundle} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         documented client- or
     *                                         server-error variant
     */
    void uploadRegistrationPreKeys(SignalPreKeyBundle bundle);

    /**
     * Performs the blind-sign round-trip that issues a private-stats
     * anonymous credential.
     *
     * @apiNote
     * Private-stats is WhatsApp's anonymous-attribution telemetry
     * channel: the caller passes a blinded elliptic-curve point
     * (32 bytes) and a project-name tag (UTF-8 bytes) scoping the
     * credential to a particular collector. The relay co-signs the
     * blinded point with a project-specific key and returns the
     * signed point plus the ACS public key, DLEQ proof coordinates,
     * and mint timestamp. The caller then unblinds the signed point
     * to obtain the redeemable token.
     *
     * @param blindedCredential the non-{@code null} blinded
     *                          elliptic-curve point bytes
     * @param projectName       the non-{@code null} UTF-8 project-name
     *                          bytes
     * @return an {@link Optional} carrying the parsed
     *         {@link PrivateStatsToken}, or empty on no-parse
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         documented client- or
     *                                         server-error variant
     */
    Optional<PrivateStatsToken> issuePrivateStatsToken(byte[] blindedCredential, byte[] projectName);

    /**
     * Performs a SyncD app-state server-sync round trip.
     *
     * @apiNote
     * Drives the app-state collections (chats, contacts, settings,
     * etc.). Each {@link AppStateSyncCollection} entry either
     * requests a snapshot (when the locally-known version is empty)
     * or the patches above the known version; entries shipping local
     * mutations attach the encoded {@code SyncdPatch} bytes via
     * {@link AppStateSyncCollection#patch()}. The returned
     * {@link AppStateSyncResult} carries one
     * {@link AppStateSyncCollectionResult} per requested collection;
     * callers iterate the list to drive their per-collection retry,
     * reconcile, and snapshot-fetch logic.
     *
     * @param collections the non-{@code null} per-collection entries;
     *                    may be empty
     * @return the non-{@code null} parsed sync result
     * @throws NullPointerException            if {@code collections} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         documented client- or
     *                                         server-error variant
     */
    AppStateSyncResult syncAppState(List<AppStateSyncCollection> collections);

    /**
     * Acknowledges full ingestion of a group's metadata and history
     * snapshot.
     *
     * @apiNote
     * Called after the local store has consumed the {@code <dirty/>}
     * notification carrying the group's pending updates. Without
     * the ack the relay redelivers the same notification on every
     * reconnect.
     *
     * @param group the non-{@code null} group JID being acknowledged
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    void acknowledgeGroup(JidProvider group);

    /**
     * Accepts a pending group-add invite.
     *
     * @apiNote
     * Used when the relay invited the caller to a group whose
     * membership-approval mode is {@code request_required}: the
     * accept either commits the caller as a participant immediately
     * or enqueues a request for an admin to review. The returned
     * boolean reports which branch occurred.
     *
     * @param accept the non-{@code null} {@link GroupAddAccept}
     *               carrying the target group JID, invite code,
     *               expiration timestamp, and inviting admin JID
     * @return {@code true} when the relay routed the accept into the
     *         pending-approval queue, {@code false} when the caller
     *         was admitted immediately
     * @throws NullPointerException            if {@code accept} or any
     *                                         required nested field is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    boolean acceptGroupAdd(GroupAddAccept accept);

    /**
     * Queries metadata for many groups in one round trip.
     *
     * @apiNote
     * Used by sync paths that need to refresh metadata for several
     * groups at once, for example after a long disconnect when many
     * per-group dirty bits have accumulated. The relay returns one
     * entry per requested JID across four possible shapes (full
     * {@code group_info}, truncated {@code group_info}, the
     * {@code group_forbidden} marker, or the {@code group_not_exist}
     * marker); the two markers are silently dropped from the
     * returned list because they carry no metadata.
     *
     * @param groups the non-{@code null} group JIDs being queried
     * @return an unmodifiable list of {@link GroupMetadata} entries in
     *         the relay's reply order, possibly empty
     * @throws NullPointerException            if {@code groups} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    List<GroupMetadata> batchQueryGroupInfo(Collection<? extends JidProvider> groups);

    /**
     * Cancels one or more pending self-issued membership-approval
     * requests.
     *
     * @apiNote
     * Used when the local user, having previously asked to join a
     * closed group or community sub-group, withdraws the request
     * before an admin acts on it. The returned map's values surface
     * the per-applicant outcome:
     * {@link GroupParticipantStatus#OK} for successful cancellations,
     * {@link GroupParticipantStatus#NOT_AUTHORIZED} when the caller
     * does not own the targeted request and lacks admin rights, and
     * {@link GroupParticipantStatus#NOT_WHATSAPP_USER} when the relay
     * could not find a matching pending request.
     *
     * @param group      the non-{@code null} target group JID
     * @param applicants the non-{@code null} participant JIDs whose
     *                   pending requests are being cancelled
     * @return an unmodifiable map keyed by applicant JID in the
     *         relay's reply order
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    Map<Jid, GroupParticipantStatus> cancelGroupMembershipRequests(JidProvider group, Collection<? extends JidProvider> applicants);

    /**
     * Suggests a brand-new sub-group be created under a parent
     * community.
     *
     * @apiNote
     * Backs the community-admin tooling: the admin describes a
     * sub-group that does not yet exist, the relay reserves a JID,
     * and the returned {@link SubgroupSuggestionResult} carries the
     * provisional metadata (jid, creator, creation timestamp,
     * optional creator phone number, and an optional
     * description-error string when the relay could not accept the
     * supplied description verbatim).
     *
     * @param suggestion the non-{@code null} {@link SubgroupSuggestionNew}
     *                   carrying the parent community JID, subject,
     *                   optional description, and the locked/announcement/
     *                   hiddenGroup toggles
     * @return the suggestion result, always carrying
     *         {@link SubgroupSuggestionResult.Kind#NEW_GROUP}
     * @throws NullPointerException            if {@code suggestion} or
     *                                         any non-nullable nested
     *                                         field is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    SubgroupSuggestionResult suggestNewSubgroup(SubgroupSuggestionNew suggestion);

    /**
     * Suggests pre-existing groups be linked into a parent community
     * as sub-groups.
     *
     * @apiNote
     * Sister of {@link #suggestNewSubgroup(SubgroupSuggestionNew)} for
     * the case where the admin wants to recommend already-existing
     * groups be folded into the community rather than spinning up a
     * fresh one. The relay validates each candidate against the
     * community's policies; rejected candidates carry a
     * {@link SubgroupSuggestionResult.Candidate.Reason} discriminator
     * pinpointing why.
     *
     * @param community       the non-{@code null} parent community JID
     * @param candidateGroups the non-{@code null} and non-empty list
     *                        of candidate sub-group JIDs
     * @return the suggestion result, always carrying
     *         {@link SubgroupSuggestionResult.Kind#EXISTING_GROUPS}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if {@code candidateGroups} is empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    SubgroupSuggestionResult suggestExistingSubgroups(JidProvider community, Collection<? extends JidProvider> candidateGroups);

    /**
     * Deactivates a community parent group.
     *
     * @apiNote
     * Equivalent in effect to deactivating the entire community:
     * every linked sub-group is unlinked and converted back into a
     * standalone group. The returned {@link Optional} carries the
     * post-deletion stored {@link GroupMetadata} when the community
     * was known locally; otherwise empty.
     *
     * @param community the non-{@code null} parent community JID
     * @return an {@link Optional} carrying the post-deletion
     *         {@link GroupMetadata}, or empty when the community is
     *         not in the local store
     * @throws NullPointerException            if {@code community} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    Optional<GroupMetadata> deleteParentGroup(JidProvider community);

    /**
     * Queries the profile pictures for many groups in one batch.
     *
     * @apiNote
     * Backs the community-roster and chat-list flows that need to
     * surface fresh profile pictures for several groups at once.
     * The {@link GroupProfilePicture} model unifies the three
     * possible relay shapes: a URL projection populates
     * {@link GroupProfilePicture#url()} and
     * {@link GroupProfilePicture#directPath()}, an inline blob
     * populates {@link GroupProfilePicture#blob()}, and an
     * unchanged/absent marker leaves all of them unset.
     *
     * @param groups the non-{@code null} group JIDs being queried
     * @return an unmodifiable list of {@link GroupProfilePicture}
     *         entries in the relay's reply order
     * @throws NullPointerException            if {@code groups} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    List<GroupProfilePicture> queryGroupProfilePictures(Collection<? extends JidProvider> groups);

    /**
     * Resolves a group's metadata snapshot from a deep-link invite
     * code without joining it.
     *
     * @apiNote
     * Backs the "Preview group before joining" UI flow: the relay
     * validates the supplied invite code and projects subject,
     * picture, owner, admins, ephemeral state, etc. The reply also
     * carries the relay-reported {@code size} attribute, folded into
     * {@link GroupMetadata#size()}.
     *
     * @param inviteCode the non-{@code null} invite code
     * @return an {@link Optional} carrying the parsed
     *         {@link GroupMetadata}, or empty when the relay could
     *         not produce a {@code <group>} subtree
     * @throws NullPointerException            if {@code inviteCode} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    Optional<GroupMetadata> queryInviteGroupInfo(String inviteCode);

    /**
     * Resolves a single linked-group entry within a parent community.
     *
     * @apiNote
     * Backs the "Go to parent community" and "Go to general
     * sub-group" navigation actions in the chat UI. The
     * {@code queryLinkedType} discriminator selects which projection
     * the relay returns:
     * <ul>
     *   <li>{@code "parent"}: the parent community itself.</li>
     *   <li>{@code "general"}: the community's general-chat
     *   sub-group.</li>
     *   <li>{@code "sub_group"}: the specific sub-group identified by
     *   {@code queryLinkedJid}.</li>
     * </ul>
     *
     * @param community       the non-{@code null} parent community JID
     * @param queryLinkedType the non-{@code null} linked-type
     *                        discriminator
     * @param queryLinkedJid  the specific linked-group JID; required
     *                        when {@code queryLinkedType} is
     *                        {@code "sub_group"}, may be {@code null}
     *                        for {@code "parent"} and {@code "general"}
     * @return an {@link Optional} carrying the parsed metadata, either
     *         {@link GroupMetadata} for a sub-group projection or
     *         {@link CommunityMetadata} for the parent-community
     *         projection, or empty when the relay returned no
     *         resolvable subtree
     * @throws NullPointerException            if any required argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    Optional<ChatMetadata> queryLinkedGroup(JidProvider community, String queryLinkedType, JidProvider queryLinkedJid);

    /**
     * Returns the union of participants across every group linked to a
     * parent community.
     *
     * @apiNote
     * Backs the "@all" mention flow inside community sub-groups,
     * where the mentioning UI must display the union of every
     * sub-group participant. The returned map keys are the primary
     * JIDs; the values are the resolved phone-number JIDs when the
     * relay supplied LID-to-PN resolution, or {@code null} when it
     * did not.
     *
     * @param community the non-{@code null} parent community JID
     * @return an unmodifiable map keyed by the participant's primary
     *         JID in the relay's reply order
     * @throws NullPointerException            if {@code community} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    Map<Jid, Jid> queryLinkedGroupsParticipants(JidProvider community);

    /**
     * Returns the pending membership-approval requests awaiting an
     * admin's review.
     *
     * @apiNote
     * Backs the "Pending requests" tab in the group-info UI. Each
     * {@link GroupMembershipApprovalRequest} entry captures the
     * requesting user's identity, the optional resolved phone-number
     * JID and username, the parent-community JID for community-link
     * join requests, the request timestamp, and the
     * {@link GroupMembershipApprovalRequest.Method} indicating the
     * pathway through which the request was filed.
     *
     * @param group the non-{@code null} target group JID
     * @return an unmodifiable list of pending requests in the relay's
     *         reply order
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    List<GroupMembershipApprovalRequest> queryGroupMembershipApprovalRequests(JidProvider group);

    /**
     * Returns every group the caller currently participates in.
     *
     * @apiNote
     * Used by initial-sync paths that bootstrap the local chat list
     * when the persistent store is empty. The two boolean flags
     * select which optional projections the relay attaches to the
     * full or truncated {@code group_info} entries it returns.
     *
     * @param includeParticipants whether to include per-group
     *                            participant edges
     * @param includeDescription  whether to include per-group
     *                            description text
     * @return an unmodifiable list of {@link GroupMetadata} entries in
     *         the relay's reply order, one per participating group
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    List<GroupMetadata> queryParticipatingGroups(boolean includeParticipants, boolean includeDescription);

    /**
     * Returns the in-group messages members previously reported to
     * the group's administrators.
     *
     * @apiNote
     * Backs the "View previously reported messages" admin view. The
     * relay deduplicates reports by offending stanza id, so a single
     * message reported by several members surfaces as one
     * {@link GroupMessageReport} carrying multiple
     * {@link GroupMessageReport.Reporter} rows.
     *
     * @param group the non-{@code null} target group JID
     * @return an unmodifiable list of {@link GroupMessageReport}
     *         entries in the relay's reply order
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    List<GroupMessageReport> queryReportedMessages(JidProvider group);

    /**
     * Joins a community sub-group.
     *
     * @apiNote
     * Backs the "Join sub-group" flow inside community navigation;
     * the relay decides whether to add the caller directly or
     * enqueue a request for an admin based on the sub-group's
     * membership-approval mode. The returned boolean reports which
     * branch occurred.
     *
     * @param community           the non-{@code null} parent community JID
     * @param subgroup            the non-{@code null} sub-group JID
     *                            being joined
     * @param joinLinkedGroupType the non-{@code null} join-type
     *                            discriminator carried in the request
     * @return {@code true} when the relay routed the join into the
     *         pending-approval queue, {@code false} when the caller
     *         was admitted immediately
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    boolean joinLinkedGroup(JidProvider community, JidProvider subgroup, String joinLinkedGroupType);

    /**
     * Links existing groups under a community parent.
     *
     * @apiNote
     * Backs the community-admin "Add existing group" flow that
     * promotes a standalone group into a community sub-group.
     * Linking implicitly transfers each sub-group's members into the
     * parent community; members whose privacy settings forbid the
     * implicit add are reported back through
     * {@link LinkedSubgroupResult#participantErrors()} keyed by JID
     * with the wire-level {@code "403"} code.
     *
     * @param community the non-{@code null} parent community JID
     * @param subgroups the non-{@code null} sub-group JIDs being linked
     * @return an unmodifiable list of {@link LinkedSubgroupResult}
     *         entries in the relay's reply order
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    List<LinkedSubgroupResult> linkSubgroups(JidProvider community, List<? extends JidProvider> subgroups);

    /**
     * Reports an in-group message to WhatsApp moderation.
     *
     * @apiNote
     * Backs the "Report message" UI action inside groups; the report
     * is forwarded to the upstream moderation pipeline. Success is
     * silent; only failures observably surface.
     *
     * @param group     the non-{@code null} target group JID
     * @param messageId the non-{@code null} server id of the
     *                  offending message
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    void reportGroupMessages(JidProvider group, String messageId);

    /**
     * Revokes the per-participant invite codes used by closed groups.
     *
     * @apiNote
     * Backs the admin "Reset invite link" action when running on a
     * group whose invite mode is the per-participant
     * {@code request_required} variant. The returned map's values
     * surface the per-participant outcome:
     * {@link GroupParticipantStatus#OK} for successful revocations,
     * {@link GroupParticipantStatus#NOT_WHATSAPP_USER} when the
     * relay could not find a matching request code.
     *
     * @param group        the non-{@code null} target group JID
     * @param participants the non-{@code null} per-participant JIDs
     *                     whose request codes are being revoked
     * @return an unmodifiable map keyed by the participant JID in the
     *         relay's reply order
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    Map<Jid, GroupParticipantStatus> revokeGroupRequestCode(JidProvider group, List<? extends JidProvider> participants);

    /**
     * Applies a batch of edits to a group's metadata.
     *
     * @apiNote
     * Single entry point for subject and description rewrites,
     * profile-picture updates and removals, binary property toggles
     * (locked, announcement, ephemeral, membership-approval, and so
     * on), and the local-only {@code statusMuted} sync flag. Every
     * optional field on {@link GroupMetadataEdit} that carries a
     * present value drives one mutation:
     * <ul>
     *   <li>{@link GroupMetadataEdit#subject() subject} present
     *       rewrites the group title.</li>
     *   <li>{@link GroupMetadataEdit#description() description}
     *       present rewrites or clears the description; a
     *       {@link GroupDescription.Set Set} sets the body, a
     *       {@link GroupDescription.Clear Clear} removes it.</li>
     *   <li>{@link GroupMetadataEdit#picture() picture} present
     *       updates or removes the group icon;
     *       {@link GroupPicture.Set Set} uploads the picture bytes,
     *       {@link GroupPicture.Clear Clear} removes it.</li>
     *   <li>Each binary toggle (locked, announcement, and siblings)
     *       is batched into a single property-update request.</li>
     *   <li>Each
     *       {@link GroupMetadataEdit#limitSharingEnabled() limitSharing},
     *       {@link GroupMetadataEdit#memberAddAdminOnly() memberAdd},
     *       {@link GroupMetadataEdit#memberLinkAdminOnly() memberLink},
     *       {@link GroupMetadataEdit#memberShareGroupHistoryAdminOnly() memberShareGroupHistory},
     *       and
     *       {@link GroupMetadataEdit#allowNonAdminSubGroupCreation() allowNonAdminSubGroupCreation}
     *       toggle is applied through its corresponding property
     *       mutation.</li>
     *   <li>{@link GroupMetadataEdit#ephemeralExpiration() ephemeralExpiration}
     *       present or {@link GroupMetadataEdit#notEphemeral() notEphemeral}
     *       {@code true} is routed through
     *       {@link #editEphemeralTimer(JidProvider, ChatEphemeralTimer)},
     *       which applies the disappearing-message timer change and
     *       the in-memory chat-ephemerality state update.</li>
     *   <li>{@link GroupMetadataEdit#statusMuted() statusMuted}
     *       present merges the value into the stored
     *       {@link GroupMetadata#statusMuted() statusMuted} field
     *       without producing a network packet.</li>
     * </ul>
     *
     * @param edit the non-{@code null} edit packet
     * @return an {@link Optional} carrying the post-edit
     *         {@link GroupMetadata}, or empty when the group is not in
     *         the store
     * @throws NullPointerException            if {@code edit} is {@code null}
     * @throws IllegalArgumentException        if the JID is not a
     *                                         group/community
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    Optional<GroupMetadata> editGroupMetadata(GroupMetadataEdit edit);

    /**
     * Detaches sub-groups from their parent community.
     *
     * @apiNote
     * Inverse of
     * {@link #linkSubgroups(JidProvider, List)}; detached sub-groups
     * become standalone groups again. Each
     * {@link UnlinkedSubgroupResult} carries the sub-group's JID,
     * whether the relay echoed the
     * {@code remove_orphaned_members="true"} attribute, and an
     * optional {@link UnlinkedSubgroupResult.Reason} discriminator
     * pinpointing why the candidate failed to detach.
     *
     * @param community the non-{@code null} parent community JID
     * @param subgroups the non-{@code null} sub-group JIDs being detached
     * @return an unmodifiable list of {@link UnlinkedSubgroupResult}
     *         entries in the relay's reply order
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a documented
     *                                         {@code ClientError} or
     *                                         {@code ServerError}
     */
    List<UnlinkedSubgroupResult> unlinkSubgroups(JidProvider community, List<? extends JidProvider> subgroups);

    /**
     * Convenience overload of
     * {@link #queryNewsletterMessageUpdates(JidProvider, int, Long, NewsletterHistoryDirection)}
     * for a cold fetch.
     *
     * @apiNote
     * Forwards a {@code null} {@code since} watermark so the relay
     * starts from the newest cursor regardless of any local delta
     * state.
     *
     * @param newsletter the newsletter
     * @param count      the per-call cap
     * @param direction  the cursor direction (Before/After pivot)
     * @return the message-history page
     * @throws NullPointerException if any argument is {@code null}
     */
    NewsletterMessageHistory queryNewsletterMessageUpdates(JidProvider newsletter, int count, NewsletterHistoryDirection direction);

    /**
     * Returns the windowed message-update deltas applied to a
     * newsletter since a reference timestamp.
     *
     * @apiNote
     * Used by the newsletter sync path that backfills the local
     * message store after a reconnect; the stream carries only
     * per-message deltas (edits, reactions, deletes) rather than
     * full message bodies.
     *
     * @param newsletter the non-{@code null} newsletter JID being queried
     * @param count      the per-call cap; must be non-negative
     * @param since      the reference timestamp delta-cursor; may be {@code null}
     * @param direction  the optional pagination cursor; may be {@code null}
     * @return the non-{@code null} message-update slice
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         documented
     *                                         {@code ClientError},
     *                                         {@code ServerError}, or
     *                                         the envelope did not parse
     */
    NewsletterMessageHistory queryNewsletterMessageUpdates(JidProvider newsletter, int count, Long since, NewsletterHistoryDirection direction);

    /**
     * Returns a windowed page of newsletter message envelopes,
     * addressing the newsletter by its JID.
     *
     * @apiNote
     * Convenience for
     * {@link #queryNewsletterMessages(JidProvider, int, NewsletterViewerRole, NewsletterHistoryDirection)}
     * that omits the optional view-role and pagination cursor.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param count      the per-call cap; must be non-negative
     * @return the non-{@code null} message-history page
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    NewsletterMessageHistory queryNewsletterMessages(JidProvider newsletter, int count);

    /**
     * Returns a windowed page of newsletter message envelopes,
     * addressing the newsletter by its JID, with optional view-role
     * projection and pagination cursor.
     *
     * @apiNote
     * Drives the newsletter detail view's message-history pagination.
     * The {@code direction} argument controls which side of the
     * anchor is fetched; the {@code viewRole} echoes the role-scoped
     * ACL projection requested by the UI.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param count      the per-call cap; must be non-negative
     * @param viewRole   optional ACL projection role echoed in the
     *                   {@code <view_role>} attribute; may be {@code null}
     * @param direction  the pagination cursor; may be {@code null}
     * @return the non-{@code null} message-history page
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         documented
     *                                         {@code ClientError},
     *                                         {@code ServerError}, or
     *                                         the envelope did not parse
     */
    NewsletterMessageHistory queryNewsletterMessages(JidProvider newsletter, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction);

    /**
     * Returns a windowed page of newsletter message envelopes,
     * addressing the newsletter by its public invite key.
     *
     * @apiNote
     * Convenience for
     * {@link #queryNewsletterMessages(String, int, NewsletterViewerRole, NewsletterHistoryDirection)}
     * that omits the optional view-role and pagination cursor.
     *
     * @param inviteKey the non-{@code null} newsletter invite key
     * @param count     the per-call cap; must be non-negative
     * @return the non-{@code null} message-history page
     * @throws NullPointerException            if {@code inviteKey} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    NewsletterMessageHistory queryNewsletterMessages(String inviteKey, int count);

    /**
     * Returns a windowed page of newsletter message envelopes,
     * addressing the newsletter by its public invite key, with
     * optional view-role projection and pagination cursor.
     *
     * @apiNote
     * Companion to
     * {@link #queryNewsletterMessages(JidProvider, int, NewsletterViewerRole, NewsletterHistoryDirection)}
     * for clients holding only the public invite link instead of the
     * JID (for example an external referrer).
     *
     * @param inviteKey the non-{@code null} newsletter invite key
     * @param count     the per-call cap; must be non-negative
     * @param viewRole  optional ACL projection role; may be {@code null}
     * @param direction the pagination cursor; may be {@code null}
     * @return the non-{@code null} message-history page
     * @throws NullPointerException            if {@code inviteKey} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         documented
     *                                         {@code ClientError},
     *                                         {@code ServerError}, or
     *                                         the envelope did not parse
     */
    NewsletterMessageHistory queryNewsletterMessages(String inviteKey, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction);

    /**
     * Convenience overload of
     * {@link #queryNewsletterResponses(JidProvider, long, int, String, NewsletterResponsesFilter, String)}
     * for the basic page-from-top case.
     *
     * @apiNote
     * Forwards {@code null} for the before-cursor, filter, and
     * search-text so the relay returns the newest responder page.
     *
     * @param newsletter                the non-{@code null} newsletter JID
     * @param questionResponsesServerId the server id of the question/poll message
     * @param questionResponsesCount    the per-call cap
     * @return the responder entries
     * @throws NullPointerException if {@code newsletter} is {@code null}
     */
    List<NewsletterQuestionResponse> queryNewsletterResponses(JidProvider newsletter, long questionResponsesServerId, int questionResponsesCount);

    /**
     * Fetches the responder slice for an interactive newsletter
     * question post.
     *
     * @apiNote
     * Backs the newsletter-author UI that visualises the
     * per-subscriber response stream attached to a question post.
     * Each {@link NewsletterQuestionResponse} carries the responder
     * identity, response timestamp, and a flag marking responses the
     * question owner has already replied to.
     *
     * @param newsletter                the non-{@code null} newsletter JID
     * @param questionResponsesServerId the server id of the question/poll message
     * @param questionResponsesCount    the per-call cap; must be non-negative
     * @param questionResponsesBefore   optional anchor for backwards
     *                                  pagination; may be {@code null}
     * @param filter                    optional filter discriminator; may be {@code null}
     * @param searchText                optional search string; may be {@code null}
     * @return the non-{@code null} responder entries in server order
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         documented
     *                                         {@code ClientError},
     *                                         {@code ServerError}, or
     *                                         the envelope did not parse
     */
    List<NewsletterQuestionResponse> queryNewsletterResponses(JidProvider newsletter, long questionResponsesServerId, int questionResponsesCount, String questionResponsesBefore, NewsletterResponsesFilter filter, String searchText);

    /**
     * Convenience overload of
     * {@link #queryNewsletterStatusUpdates(JidProvider, int, Long, NewsletterHistoryDirection)}
     * for a cold fetch.
     *
     * @apiNote
     * Forwards a {@code null} {@code since} watermark so the relay
     * starts from the newest cursor regardless of any local delta
     * state.
     *
     * @param newsletter the newsletter
     * @param count      the per-call cap
     * @param direction  the cursor direction (Before/After pivot)
     * @return the status-update slice
     * @throws NullPointerException if any argument is {@code null}
     */
    NewsletterStatusHistory queryNewsletterStatusUpdates(JidProvider newsletter, int count, NewsletterHistoryDirection direction);

    /**
     * Returns the windowed status-update deltas applied to a
     * newsletter since a reference timestamp.
     *
     * @apiNote
     * Status-scoped sister of
     * {@link #queryNewsletterMessageUpdates(JidProvider, int, Long, NewsletterHistoryDirection)};
     * drives the newsletter sync path that backfills the local
     * status store after a reconnect.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param count      the per-call cap; must be non-negative
     * @param since      the reference timestamp delta-cursor; may be {@code null}
     * @param direction  the optional pagination cursor; may be {@code null}
     * @return the non-{@code null} status-update slice
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         documented
     *                                         {@code ClientError},
     *                                         {@code ServerError}, or
     *                                         the envelope did not parse
     */
    NewsletterStatusHistory queryNewsletterStatusUpdates(JidProvider newsletter, int count, Long since, NewsletterHistoryDirection direction);

    /**
     * Fetches a windowed page of newsletter status envelopes,
     * addressing the newsletter by its JID.
     *
     * @apiNote
     * Convenience for
     * {@link #queryNewsletterStatuses(JidProvider, int, NewsletterViewerRole, NewsletterHistoryDirection)}
     * that omits the optional view-role and pagination cursor.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param count      the per-call cap; must be non-negative
     * @return the non-{@code null} status-history page
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    NewsletterStatusHistory queryNewsletterStatuses(JidProvider newsletter, int count);

    /**
     * Fetches a windowed page of newsletter status envelopes,
     * addressing the newsletter by its JID, with optional view-role
     * projection and pagination cursor.
     *
     * @apiNote
     * Status-scoped sister of
     * {@link #queryNewsletterMessages(JidProvider, int, NewsletterViewerRole, NewsletterHistoryDirection)};
     * drives the status-history pagination in the newsletter detail
     * view.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param count      the per-call cap; must be non-negative
     * @param viewRole   optional ACL projection role; may be {@code null}
     * @param direction  the pagination cursor; may be {@code null}
     * @return the non-{@code null} status-history page
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         documented
     *                                         {@code ClientError},
     *                                         {@code ServerError}, or
     *                                         the envelope did not parse
     */
    NewsletterStatusHistory queryNewsletterStatuses(JidProvider newsletter, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction);

    /**
     * Fetches a windowed page of newsletter status envelopes,
     * addressing the newsletter by its public invite key.
     *
     * @apiNote
     * Convenience for
     * {@link #queryNewsletterStatuses(String, int, NewsletterViewerRole, NewsletterHistoryDirection)}
     * that omits the optional view-role and pagination cursor.
     *
     * @param inviteKey the non-{@code null} newsletter invite key
     * @param count     the per-call cap; must be non-negative
     * @return the non-{@code null} status-history page
     * @throws NullPointerException            if {@code inviteKey} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    NewsletterStatusHistory queryNewsletterStatuses(String inviteKey, int count);

    /**
     * Fetches a windowed page of newsletter status envelopes,
     * addressing the newsletter by its public invite key, with
     * optional view-role projection and pagination cursor.
     *
     * @apiNote
     * Companion to
     * {@link #queryNewsletterStatuses(JidProvider, int, NewsletterViewerRole, NewsletterHistoryDirection)}
     * for clients holding only the public invite link instead of the
     * JID.
     *
     * @param inviteKey the non-{@code null} newsletter invite key
     * @param count     the per-call cap; must be non-negative
     * @param viewRole  optional ACL projection role; may be {@code null}
     * @param direction the pagination cursor; may be {@code null}
     * @return the non-{@code null} status-history page
     * @throws NullPointerException            if {@code inviteKey} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         documented
     *                                         {@code ClientError},
     *                                         {@code ServerError}, or
     *                                         the envelope did not parse
     */
    NewsletterStatusHistory queryNewsletterStatuses(String inviteKey, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction);

    /**
     * Returns the caller's own reactions and poll votes on messages
     * of a specific newsletter.
     *
     * @apiNote
     * Used by the newsletter detail view to populate the "My recent
     * reactions" panel with the caller's most-used reactions for the
     * channel and to highlight options the caller already voted for
     * in newsletter polls.
     *
     * @param limit      the per-call cap; must be non-negative
     * @param newsletter the non-{@code null} newsletter JID
     * @return a non-{@code null} map keyed by newsletter JID whose
     *         values are the per-message add-on entries
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         documented
     *                                         {@code ClientError},
     *                                         {@code ServerError}, or
     *                                         the envelope did not parse
     */
    Map<Jid, List<NewsletterMyAddOn>> queryNewsletterMyAddOns(int limit, JidProvider newsletter);

    /**
     * Subscribes the connection to real-time updates for a specific
     * newsletter.
     *
     * @apiNote
     * Once acknowledged the relay starts pushing
     * {@code <notification type="newsletter">} stanzas carrying the
     * live message and status delta stream. The subscription is
     * bounded by the returned duration (the relay clamps it to
     * {@code [30s, 600s]}); the caller is expected to refresh
     * before it expires to keep the stream alive.
     *
     * @param newsletter the non-{@code null} newsletter JID being
     *                   subscribed to
     * @return the non-{@code null} relay-chosen subscription duration
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         documented
     *                                         {@code ClientError},
     *                                         {@code ServerError}, or
     *                                         the envelope did not parse
     */
    Duration subscribeToNewsletterLiveUpdates(JidProvider newsletter);

    /**
     * Fetches the latest A/B-experiment configuration bundle.
     *
     * @apiNote
     * Refreshes the global props cache; the relay either echoes back
     * the materialised props subtree (when the supplied hash is
     * stale or absent) or short-circuits to a delta when the cached
     * snapshot is already current.
     *
     * @param propsHash      the client's currently-cached props hash;
     *                       may be {@code null} on the first fetch
     * @param propsRefreshId the client's currently-cached refresh id;
     *                       may be {@code null} on the first fetch
     * @return an {@link Optional} carrying the parsed
     *         {@link AbPropsBundle}, or empty on no-parse
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<AbPropsBundle> queryExperimentConfig(String propsHash, Integer propsRefreshId);

    /**
     * Fetches the per-group A/B-experiment configuration.
     *
     * @apiNote
     * Refreshes group-scoped feature gates after the relay pushes a
     * {@code <notification type="abprops">} bump that names a
     * specific group.
     *
     * @param group     the non-{@code null} target group JID
     * @param propsHash the cached group-props hash, or {@code null}
     * @return an {@link Optional} carrying the parsed
     *         {@link AbPropsBundle}, or empty on no-parse
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<AbPropsBundle> queryGroupExperimentConfig(JidProvider group, String propsHash);

    /**
     * Fetches the bot directory listing.
     *
     * @apiNote
     * Refreshes the curated set of WhatsApp AI bots the user can
     * interact with. The request is digest-gated: an unchanged
     * directory echoes the cached {@code bhash} back without any
     * payload.
     *
     * @param botV     the supported protocol revision (typically
     *                 {@code "2"} or {@code "3"}); may be {@code null}
     * @param botBhash the cached directory digest; may be {@code null}
     * @param botArgs  the non-{@code null} optional bot JIDs to scope
     *                 the query to
     * @return an {@link Optional} carrying the parsed
     *         {@link BotDirectory}, or empty on no-parse
     * @throws NullPointerException            if {@code botArgs} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<BotDirectory> queryBotList(String botV, String botBhash, List<? extends JidProvider> botArgs);

    /**
     * Submits a bug report to the WhatsApp support backend.
     *
     * @apiNote
     * Backs the in-app "Contact us" and "Report a problem" flows;
     * the supplied {@link BugReport} carries the reporter JID,
     * description, debug-info JSON, optional device-log handle,
     * optional pre-uploaded attachments, and the optional title,
     * category, ASL join key, and reproducibility tag.
     *
     * @param report the non-{@code null} bug report payload
     * @return an {@link Optional} carrying the backend-side task id,
     *         or empty when the relay omitted it
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<String> reportBug(BugReport report);

    /**
     * Reports an in-app marketing event to the relay.
     *
     * @apiNote
     * Drives attribution of in-app banner and promotion impressions,
     * clicks, and dismissals. The supplied {@link InAppCommsEvent}
     * carries the promotion id, event type code, event timestamp,
     * and an opaque JSON {@code logdata} payload whose schema is
     * owned by the promotion authoring tool.
     *
     * @param event the non-{@code null} in-app comms event
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void reportInAppCommsEvent(InAppCommsEvent event);

    /**
     * Acknowledges the boundary of an offline-batch backfill.
     *
     * @apiNote
     * Called after the client has fully ingested every stanza in
     * the post-reconnect offline backfill so the relay advances its
     * offline cursor. Without the ack the same batch is redelivered
     * on the next reconnect.
     *
     * @param offlineBatchCount the number of stanzas the client has
     *                          just fully consumed; must match the
     *                          size the relay announced
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void acknowledgeOfflineBatch(int offlineBatchCount);

    /**
     * Switches this client's session into active mode.
     *
     * @apiNote
     * Tells the relay this device is the user's primary foregrounded
     * surface and should receive immediate push of new stanzas,
     * presence updates, typing indicators, and the like. A
     * multi-device session typically has one device in active mode
     * at a time; backgrounded companions stay in passive mode (see
     * {@link #enablePassiveMode()}) so the relay can suppress
     * non-essential pushes.
     *
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void enableActiveMode();

    /**
     * Switches this client's session into passive mode.
     *
     * @apiNote
     * Tells the relay this device is backgrounded so non-essential
     * pushes (typing indicators, presence updates) can be suspended
     * until the next call to {@link #enableActiveMode()}. See
     * {@link #enableActiveMode()} for the active/passive model.
     *
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void enablePassiveMode();

    /**
     * Fetches Signal pre-key bundles for the supplied users.
     *
     * @apiNote
     * Required before the local client can send messages to a peer
     * it has never communicated with; the returned bundle carries
     * the recipient's identity key, signed pre-key, and one-time
     * pre-key needed to seed a Signal session.
     *
     * @param users the non-{@code null} per-user requests
     * @return an {@link Optional} carrying the parsed
     *         {@link PreKeyBundleResult}, or empty on no-parse
     * @throws NullPointerException            if {@code users} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<PreKeyBundleResult> queryPreKeyBundles(List<PreKeyBundleRequest> users);

    /**
     * Fetches additional one-time pre-keys for users whose locally
     * cached bundles have been exhausted.
     *
     * @apiNote
     * Called when an outbound message would otherwise reuse a stale
     * pre-key; the relay returns just the missing one-time keys
     * without re-sending the long-lived identity material.
     *
     * @param users the non-{@code null} per-user requests
     * @return an {@link Optional} carrying the parsed
     *         {@link PreKeyBundleResult}, or empty on no-parse
     * @throws NullPointerException            if {@code users} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<PreKeyBundleResult> queryMissingPreKeys(List<MissingPreKeyUserRequest> users);

    /**
     * Co-signs a blinded credential token via the anonymous-
     * attribution signing service.
     *
     * @apiNote
     * Signing half of the private-stats handshake: the client
     * blinds a token, the relay co-signs it without learning the
     * contents, and the client later unblinds the result to obtain
     * an unlinkable credential it can spend to attest to a stats
     * event without revealing its identity. The
     * {@code projectNameElementValue} selects which signer key the
     * relay uses.
     *
     * @param blindedCredentialElementValue the non-{@code null}
     *                                      blinded credential token
     * @param projectNameElementValue       the non-{@code null} signing
     *                                      project identifier
     * @return an {@link Optional} carrying the
     *         {@link SignedAttributionCredential}, or empty when the
     *         relay returned no parseable variant
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<SignedAttributionCredential> signAnonymousAttributionCredential(byte[] blindedCredentialElementValue, String projectNameElementValue);

    /**
     * Queries whether the user has blocked Public Service
     * Announcement chats.
     *
     * @apiNote
     * PSAs are official chat blasts the WhatsApp team itself
     * publishes (launch announcements, safety notices, feature
     * tutorials). This method surfaces the current opt-out state so
     * the UI can render the correct toggle position.
     *
     * @return {@code true} when the relay reports the PSA channel
     *         currently blocked, {@code false} when active
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    boolean queryPublicAnnouncementBlocked();

    /**
     * Updates whether the user is blocking Public Service
     * Announcement chats.
     *
     * @apiNote
     * Drives the SMB and consumer PSA-block toggle. See
     * {@link #queryPublicAnnouncementBlocked()} for a definition of
     * PSAs.
     *
     * @param blockingAction the non-{@code null} mutation verb to apply
     *                       to the PSA block list
     * @throws NullPointerException            if {@code blockingAction} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editPublicAnnouncementBlocked(PsaChatBlockAction blockingAction);

    /**
     * Updates the device's push-notification configuration on the
     * relay.
     *
     * @apiNote
     * Called at session bootstrap or whenever the OS-issued push
     * token rotates; submits a fresh platform-specific
     * {@link PushConfig} (FCM, APNs, Web push, WNS, Enterprise,
     * Facebook) or a {@link PushConfig.Clear} that removes the
     * registration so the relay stops sending wakeup pings.
     *
     * @param config the non-{@code null} push-config payload
     * @throws NullPointerException            if {@code config} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editPushConfig(PushConfig config);

    /**
     * Publishes a view receipt for one or more status posts.
     *
     * @apiNote
     * Drives the "Seen by" surface on the publisher's side. Distinct
     * from a regular read receipt because the wire shape batches
     * several server ids under a single posting; the supplied
     * {@link ViewReceipt} carries the receipt id, publisher JID, the
     * status-namespace flag, and the list of viewed server ids.
     *
     * @param receipt the non-{@code null} view-receipt payload
     * @throws NullPointerException            if {@code receipt} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void publishViewReceipt(ViewReceipt receipt);

    /**
     * Sends a buffered batch of low-priority client-side stats
     * events to the relay.
     *
     * @apiNote
     * Counterpart of the main telemetry stream for events that are
     * too cheap to warrant standalone events; the client keeps a
     * separate stats-buffer aggregator and ships it through this
     * call on a flush tick.
     *
     * @param addT            the non-{@code null} buffer add timestamp
     * @param addElementValue the non-{@code null} serialised event blob
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void sendStatsBuffer(Instant addT, byte[] addElementValue);

    /**
     * Sends a free-form support feedback report.
     *
     * @apiNote
     * Backs the in-app "Send feedback" surface; supplies the
     * reporter JID, the quoted message id, and a list of
     * feedback-kind tags so the support team can triage.
     *
     * @param from          the non-{@code null} reporter JID
     * @param messageId     the non-{@code null} quoted message id
     * @param feedbackKinds the non-{@code null} feedback-kind tags
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void sendSupportFeedback(JidProvider from, String messageId, List<String> feedbackKinds);

    /**
     * Submits a structured "Contact us" form to support.
     *
     * @apiNote
     * Backs the help-center contact flow; the supplied
     * {@link SupportContactForm} carries the reporter JID,
     * description, topic, optional topic id, debug-info JSON,
     * optional pre-uploaded log handle, and the additional
     * context-flow string used by the support tooling for routing.
     *
     * @param form the non-{@code null} contact form
     * @return an {@link Optional} carrying the
     *         {@link SupportTicketAcknowledgement}, or empty on
     *         no-parse
     * @throws NullPointerException            if {@code form} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request (retryable or
     *                                         non-retryable)
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<SupportTicketAcknowledgement> sendSupportContactForm(SupportContactForm form);

    /**
     * Reports an individual contact for spam.
     *
     * @apiNote
     * Backs the in-app "Block and report" flow; forwards the report
     * to WhatsApp's trust-and-safety pipeline alongside the
     * caller-curated list of offending stanza ids so the moderator
     * has the conversation context. The supplied
     * {@link IndividualSpamReport} carries the reportee JID, the
     * spam-flow code, an optional {@code is_known_chat} marker, and
     * the evidence stanza ids.
     *
     * @param report the non-{@code null} individual spam report
     * @throws NullPointerException            if {@code report} or any
     *                                         non-nullable nested
     *                                         field is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void reportIndividualForSpam(IndividualSpamReport report);

    /**
     * Reports a group chat for spam.
     *
     * @apiNote
     * Group-scoped sister of
     * {@link #reportIndividualForSpam(IndividualSpamReport)}. The
     * supplied {@link GroupSpamReport} additionally carries an
     * optional {@code adder} JID identifying the user who originally
     * added the reporter to the group, useful for surfacing
     * add-spam patterns.
     *
     * @param report the non-{@code null} group spam report
     * @throws NullPointerException            if {@code report} or any
     *                                         non-nullable nested
     *                                         field is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void reportGroupForSpam(GroupSpamReport report);

    /**
     * Reports a newsletter for spam.
     *
     * @apiNote
     * Backs newsletter abuse complaints; the supplied
     * {@link NewsletterSpamReport} carries the newsletter JID,
     * spam-flow code, optional subject, and a curated set of
     * reported message receipts so the trust-and-safety pipeline can
     * attribute the complaint.
     *
     * @param report the non-{@code null} newsletter spam report
     * @throws NullPointerException            if {@code report} or any
     *                                         non-nullable nested
     *                                         field is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void reportNewsletterForSpam(NewsletterSpamReport report);

    /**
     * Reports a status post for trust-and-safety review.
     *
     * @apiNote
     * Accepts both regular chat statuses broadcast on
     * {@code status@broadcast} and newsletter statuses; the right
     * RPC is dispatched based on the runtime type of
     * {@code status}. Callers supply only the user-typed fields
     * ({@code reason} code and optional {@code subject}); the wire
     * fields (target JID, message id or server id, timestamp) are
     * derived from the supplied {@link MessageInfo}.
     *
     * @param status  the non-{@code null} offending status post
     * @param reason  the non-{@code null} spam-flow code identifying
     *                the report flow
     * @param subject optional free-text comment; may be {@code null}
     * @throws NullPointerException            if {@code status} or
     *                                         {@code reason} is {@code null}
     * @throws IllegalArgumentException        if {@code status} carries
     *                                         no timestamp, sender, or
     *                                         message identifier
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void reportStatus(MessageInfo status, String reason, String subject);

    /**
     * Registers this device's user-journey analytics session id with
     * the server.
     *
     * @apiNote
     * Most embedders do not need to call this. It is exposed for
     * clients that mirror the user-journey telemetry surface
     * (Channels, Forward-message, CTWA-ad-creation, signup-funnel).
     * Typical usage:
     * {@snippet :
     *     var id = String.valueOf((Instant.now().toEpochMilli() + Duration.ofDays(3).toMillis())
     *         % Duration.ofDays(7).toMillis());
     *     client.joinUnifiedSession(id);
     * }
     *
     * @param unifiedSessionId the non-{@code null} session id to
     *                         announce; the canonical derivation is a
     *                         rolling 7-day clock
     *                         ({@code (now + 3d) mod 7d}), but any
     *                         non-{@code null} string is accepted
     * @throws NullPointerException            if {@code unifiedSessionId} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void joinUnifiedSession(String unifiedSessionId);

    /**
     * Fetches the Terms-of-Service and privacy-policy notices the
     * user has not yet acknowledged.
     *
     * @apiNote
     * User-notice disclosures are the modal pop-ups WhatsApp shows
     * when Terms of Service, privacy policy, or a regional
     * compliance notice changes; each entry carries the disclosure
     * id the client passes back to record the dismissal once the
     * user closes the modal.
     *
     * @param getUserDisclosuresT the non-{@code null} request timestamp
     * @return an {@link Optional} carrying the parsed
     *         {@link UserNoticeBundle}, or empty on no-parse
     * @throws NullPointerException            if {@code getUserDisclosuresT} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<UserNoticeBundle> queryPendingUserNotices(Instant getUserDisclosuresT);

    /**
     * Fetches the per-stage acknowledgement state for an explicit
     * list of user-notice disclosure ids.
     *
     * @apiNote
     * Used after a refresh to verify that the local cache matches
     * the server's view of which Terms-of-Service and privacy
     * notices the user has acknowledged or dismissed. See
     * {@link #queryPendingUserNotices(Instant)} for what
     * disclosures are.
     *
     * @param queries the non-{@code null} per-disclosure stage queries
     * @return an unmodifiable list of {@link UserNoticeStage} entries,
     *         empty when the relay returned no parseable variant
     * @throws NullPointerException            if {@code queries} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    List<UserNoticeStage> queryUserNoticeStages(List<UserNoticeStageQuery> queries);

    /**
     * Mints a fresh call link that can be shared to invite
     * participants into a multi-party call.
     *
     * @apiNote
     * The relay returns a 22-character opaque token embedded into
     * the canonical {@code https://call.whatsapp.com/{voice|video}/{token}}
     * URL exposed by {@link CallLink#url()}. The token's media kind
     * is fixed at creation time and cannot be changed afterwards.
     * The supplied {@link CallLinkCreate} carries the media kind
     * plus four optional fields:
     * <ul>
     *   <li>creator device JID, pinning the link to a known device.</li>
     *   <li>in-flight call id, bundling the link with an existing call.</li>
     *   <li>creator display username, surfaced in the join-prompt UI.</li>
     *   <li>scheduled-call start instant when the link backs an event.</li>
     * </ul>
     *
     * @param create the non-{@code null} call-link create payload
     * @return the freshly minted {@link CallLink}
     * @throws NullPointerException            if {@code create} or its
     *                                         media kind is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    CallLink createCallLink(CallLinkCreate create);

    /**
     * Resolves the metadata behind an existing call link.
     *
     * @apiNote
     * Backs the "Join via link" preview step: the relay resolves
     * the supplied token and replies with the link's creator
     * device, optional creator phone-number JID, optional creator
     * username, and current waiting-room state. The reply also
     * indicates whether the link is bound to a scheduled-call event.
     * The {@code action} discriminator gates the kind of resolve:
     * {@code "preview"} surfaces metadata to a prospective joiner,
     * {@code "edit"} refreshes metadata for the creator.
     *
     * @param token  the non-{@code null} link token
     * @param media  the non-{@code null} expected media kind; must
     *               match the link's configured media or the relay
     *               rejects the query
     * @param action the non-{@code null} action discriminator
     *               (typically {@code "preview"} or {@code "edit"})
     * @return an {@link Optional} carrying the resolved {@link CallLink},
     *         or empty when the reply could not be parsed
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<CallLink> queryCallLink(String token, CallLinkMedia media, String action);

    /**
     * Toggles the waiting-room gate on an existing call link.
     *
     * @apiNote
     * Only mutates the link's waiting-room state; in-flight call
     * sessions are unaffected. Subsequent
     * {@link #queryCallLink(String, CallLinkMedia, String)} replies
     * surface the new state via {@link CallLink#waitingRoom()}.
     *
     * @param enabled   {@code true} to enable the gate, {@code false}
     *                  to disable it
     * @param linkToken the non-{@code null} link token
     * @param media     the non-{@code null} link media kind
     * @throws NullPointerException            if any reference argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editCallLinkWaitingRoom(boolean enabled, String linkToken, CallLinkMedia media);

    /**
     * Checks whether the user already has an active federated-identity
     * link to a Meta account.
     *
     * @apiNote
     * First step of the Meta-SSO linking flow: a non-zero
     * {@link FederatedIdentityState} lets the client skip directly
     * to certificate fetch via
     * {@link #queryFederatedIdentityCertificate(Instant, boolean, boolean)};
     * a suspension marker tells the client to show the appropriate
     * recovery surface; absent state means the linking handshake
     * must run from scratch.
     *
     * @param timestamp the non-{@code null} request timestamp
     * @return an {@link Optional} carrying the relay-reported
     *         {@link FederatedIdentityState}, or empty when the
     *         response could not be parsed
     * @throws NullPointerException            if {@code timestamp} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<FederatedIdentityState> checkFederatedIdentityExists(Instant timestamp);

    /**
     * Pings the federated-identity bridge to keep an in-flight
     * enrolment alive.
     *
     * @apiNote
     * Called on the cadence chosen by the previous ping reply; the
     * returned {@link FederatedIdentityPing} surfaces the next
     * recommended interval. The {@link FederatedRsaEncryption} type
     * stays typed because its four-blob payload is mandatory and
     * meaningful as a unit.
     *
     * @param encryption the non-{@code null} RSA-2048 encryption envelope
     * @param timestamp  the non-{@code null} request timestamp
     * @param fbid       the non-{@code null} encrypted Facebook id payload
     * @return an {@link Optional} carrying the
     *         {@link FederatedIdentityPing} result, or empty when the
     *         response could not be parsed
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<FederatedIdentityPing> sendFederatedIdentityPing(FederatedRsaEncryption encryption, Instant timestamp, byte[] fbid);

    /**
     * Fetches the federated-identity certificate bundle.
     *
     * @apiNote
     * Returns up to three PEM bundles the client uses to encrypt
     * payloads to the Meta-side bridge and to verify bridge-signed
     * responses. The {@code hasPayloadEncCertificates} flag selects
     * whether the encryption and signature PEMs are returned;
     * {@code hasPasswordPem} adds the password PEM bundle.
     *
     * @param timestamp                 the non-{@code null} request timestamp
     * @param hasPayloadEncCertificates {@code true} to request the
     *                                  payload-encryption certs
     *                                  (encryption + signature PEMs)
     * @param hasPasswordPem            {@code true} to request the
     *                                  password PEM bundle
     * @return an {@link Optional} carrying the
     *         {@link FederatedIdentityCertificate}, or empty when the
     *         response could not be parsed
     * @throws NullPointerException            if {@code timestamp} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<FederatedIdentityCertificate> queryFederatedIdentityCertificate(Instant timestamp, boolean hasPayloadEncCertificates, boolean hasPasswordPem);

    /**
     * Refreshes the access tokens held inside the federated-identity
     * bridge.
     *
     * @apiNote
     * Called when the local copies of the bridge-held access tokens
     * are about to expire; the relay rotates them and returns the
     * new set wrapped inside a fresh
     * {@link FederatedRsaEncryption} envelope.
     *
     * @param encryption the non-{@code null} RSA-2048 encryption envelope
     * @param timestamp  the non-{@code null} request timestamp
     * @param fbid       the non-{@code null} encrypted Facebook id payload
     * @return an {@link Optional} carrying the
     *         {@link FederatedAccessTokenRefresh} reply, or empty when
     *         the response could not be parsed
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<FederatedAccessTokenRefresh> refreshFederatedIdentityAccessTokens(FederatedRsaEncryption encryption, Instant timestamp, byte[] fbid);

    /**
     * Submits an arbitrary encrypted action payload to the
     * federated-identity bridge.
     *
     * @apiNote
     * Generic catch-all RPC for actions the bridge can perform on
     * the linked Meta account once the client holds a valid Waffle
     * session. The reply may carry a {@code wf_deleted} marker
     * surfaced via {@link FederatedEncryptedAction#deleted()};
     * when set, the client must purge its local link state because
     * the bridge has dropped the federated-identity link entirely.
     *
     * @param encryption the non-{@code null} RSA-2048 encryption envelope
     * @param timestamp  the non-{@code null} request timestamp
     * @param fbid       the non-{@code null} encrypted Facebook id payload
     * @param action     the non-{@code null} encrypted action payload
     * @return an {@link Optional} carrying the
     *         {@link FederatedEncryptedAction} reply, or empty when
     *         the response could not be parsed
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<FederatedEncryptedAction> sendFederatedIdentityEncryptedPayload(FederatedRsaEncryption encryption, Instant timestamp, byte[] fbid, byte[] action);

    /**
     * Mints a WhatsApp Enterprise Authenticated Customer record.
     *
     * @apiNote
     * Final step of the federated-identity to enterprise enrolment
     * flow: once the user has accepted the disclosure in their
     * preferred locale, the Meta bridge mints the WAEntAC record so
     * subsequent business surfaces (catalog, hosted business
     * account, and so on) can address the linked enterprise account.
     * The supplied {@link EnterpriseAuthenticatedCustomerCreate}
     * bundles the RSA encryption envelope, request timestamp,
     * accepted disclosure id and version, and the disclosure
     * language and locale tags.
     *
     * @param create the non-{@code null} create request payload
     * @return the minted {@link FederatedEnterpriseCustomer}
     * @throws NullPointerException            if {@code create} or any
     *                                         non-nullable nested
     *                                         field is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    FederatedEnterpriseCustomer createEnterpriseAuthenticatedCustomer(EnterpriseAuthenticatedCustomerCreate create);

    /**
     * Returns the synced raw string value of the given AB prop.
     *
     * @apiNote
     * Blocks until the first AB-props sync round completes; once a
     * value is cached subsequent calls return immediately. Falls
     * back to {@link ABProp#defaultValue()} when the server has not
     * provided one.
     *
     * @param prop the non-{@code null} {@link ABProp} definition
     * @return the synced string value, or the default when the server
     *         has not provided one
     * @throws NullPointerException if {@code prop} is {@code null}
     */
    String queryAbPropString(ABProp prop);

    /**
     * Returns the synced boolean value of the given AB prop.
     *
     * @apiNote
     * Boolean projection of {@link #queryAbPropString(ABProp)};
     * blocks until the first AB-props sync round completes and
     * falls back to {@link ABProp#defaultValue()} when the server
     * has not provided one.
     *
     * @param prop the non-{@code null} {@link ABProp} definition
     * @return the parsed boolean, or the default when the server has
     *         not provided one
     * @throws NullPointerException if {@code prop} is {@code null}
     */
    boolean queryAbPropBool(ABProp prop);

    /**
     * Returns the synced integer value of the given AB prop.
     *
     * @apiNote
     * Integer projection of {@link #queryAbPropString(ABProp)};
     * blocks until the first AB-props sync round completes.
     *
     * @param prop the non-{@code null} {@link ABProp} definition
     * @return the parsed integer, the default, or {@code 0}
     * @throws NullPointerException if {@code prop} is {@code null}
     */
    int queryAbPropInt(ABProp prop);

    /**
     * Returns the synced long value of the given AB prop.
     *
     * @apiNote
     * Long projection of {@link #queryAbPropString(ABProp)};
     * blocks until the first AB-props sync round completes.
     *
     * @param prop the non-{@code null} {@link ABProp} definition
     * @return the parsed long, the default, or {@code 0L}
     * @throws NullPointerException if {@code prop} is {@code null}
     */
    long queryAbPropLong(ABProp prop);

    /**
     * Returns the synced double value of the given AB prop.
     *
     * @apiNote
     * Double projection of {@link #queryAbPropString(ABProp)};
     * blocks until the first AB-props sync round completes.
     *
     * @param prop the non-{@code null} {@link ABProp} definition
     * @return the parsed double, the default, or {@code 0.0}
     * @throws NullPointerException if {@code prop} is {@code null}
     */
    double queryAbPropDouble(ABProp prop);

    /**
     * Toggles enrolment in the WhatsApp Web and Desktop beta program.
     *
     * @apiNote
     * The Web and Desktop clients share the same JS bundle, so this
     * single toggle controls the beta channel for both surfaces. Flipping
     * the flag emits a singleton {@code external_web_beta} mutation in
     * the {@code REGULAR} sync collection so every linked Web/Desktop
     * installation converges on the same value.
     *
     * @param enrolled {@code true} to opt the account into the beta
     *                 program, {@code false} to opt out
     */
    void editWebBetaEnrollment(boolean enrolled);

    /**
     * Forces every outgoing VoIP call to route through WhatsApp relays
     * instead of peer-to-peer.
     *
     * @apiNote
     * Drives the "Always relay calls" privacy switch on the Settings
     * privacy-calls surface; when enabled, relayed calls mask the
     * caller's network identifiers from the callee. The change
     * propagates to every linked device via the singleton
     * {@code privacySettingRelayAllCalls} mutation in the
     * {@code REGULAR} sync collection.
     *
     * @param enabled {@code true} to force relay routing,
     *                {@code false} to allow peer-to-peer routing
     */
    void editAlwaysRelayCalls(boolean enabled);

    /**
     * Enables or disables Meta AI's on-device Private Processing for
     * sensitive computations.
     *
     * @apiNote
     * Drives the Private Processing toggle on the chat settings surface.
     * The change propagates to every linked device via the singleton
     * {@code private_processing_setting} mutation in the
     * {@code REGULAR_HIGH} sync collection. The protobuf schema models
     * three states ({@code UNDEFINED}, {@code ENABLED},
     * {@code DISABLED}); this method only emits explicit
     * {@code ENABLED}/{@code DISABLED} values.
     *
     * @param enabled {@code true} to enable Private Processing,
     *                {@code false} to disable it
     */
    void editAiPrivateProcessing(boolean enabled);

    /**
     * Toggles whether automated message and contact detections (spam,
     * flagged-account warnings) are mirrored across linked devices.
     *
     * @apiNote
     * Drives the Click-To-WhatsApp detected-outcomes onboarding switch
     * exposed on the Web settings surface. The change propagates to
     * every linked device via the singleton
     * {@code detected_outcomes_status_action} mutation in the
     * {@code REGULAR} sync collection.
     *
     * @param enabled {@code true} to enable cross-device sync of
     *                automated detections, {@code false} to disable it
     */
    void editAutomatedDetections(boolean enabled);

    /**
     * Marks a one-time onboarding hint as dismissed on every linked
     * device so it does not re-appear.
     *
     * @apiNote
     * Drives the acknowledgement path for tooltips, banners, and
     * one-time tips WhatsApp surfaces on the Web and Desktop client.
     * Each hint is identified by an opaque key; the mutation is keyed
     * on that identifier so each dismissal gets its own row in the
     * sync stream.
     *
     * @param hintId the opaque identifier of the onboarding hint to
     *               dismiss
     * @throws NullPointerException if {@code hintId} is {@code null}
     */
    void dismissOnboardingHint(String hintId);

    /**
     * Restores a previously dismissed onboarding hint so it can appear
     * again on every linked device.
     *
     * @apiNote
     * Counterpart to {@link #dismissOnboardingHint(String)}; emits the
     * same {@code nux_action} mutation with the acknowledged flag set
     * back to {@code false}.
     *
     * @param hintId the opaque identifier of the onboarding hint to
     *               restore
     * @throws NullPointerException if {@code hintId} is {@code null}
     */
    void restoreOnboardingHint(String hintId);

    /**
     * Marks a tappable button on an interactive message as used so it
     * appears disabled on every linked device.
     *
     * @apiNote
     * Drives the post-tap state of call-to-action, flow-trigger, and
     * list-entry buttons on interactive and template messages: tapping
     * a button on one device should disable it on every linked device
     * so a user cannot retrigger the same flow twice. The mutation is
     * keyed on the button identifier so each tappable element gets its
     * own row.
     *
     * @param buttonId the identifier of the interactive button being
     *                 disabled
     * @throws NullPointerException if {@code buttonId} is {@code null}
     */
    void disableInteractiveMessageButton(String buttonId);

    /**
     * Publishes the local recent-emoji usage ranking so every linked
     * device shows the same suggestion order in the emoji picker.
     *
     * @apiNote
     * Each entry pairs an emoji glyph with a usage weight; receivers
     * replace their snapshot wholesale. An empty list is valid and
     * represents "no recent usage".
     *
     * @param usage the per-emoji weight snapshot to publish
     * @throws NullPointerException if {@code usage} is {@code null}
     */
    void editRecentEmojiUsage(List<RecentEmojiWeight> usage);

    /**
     * Edits the addressbook entry for the given contact and propagates
     * the change to every linked device.
     *
     * @apiNote
     * Drives the contact-editor surface: adding or renaming a contact
     * emits a {@code contactAction} mutation that the primary device
     * replays against its native addressbook. Use
     * {@link #deleteContact(JidProvider)} to remove an entry instead.
     *
     * @param edit the contact-edit packet describing the target and the
     *             fields to update; only the target JID is required
     * @throws NullPointerException if {@code edit} is {@code null}
     */
    void editContact(ContactEdit edit);

    /**
     * Removes the addressbook entry for the given contact from every
     * linked device.
     *
     * @apiNote
     * Drives the contact-deletion path: emits a {@code contactAction}
     * mutation with operation {@code REMOVE} so receiving devices drop
     * the matching addressbook row.
     *
     * @param contact the JID of the contact to remove
     * @throws NullPointerException if {@code contact} is {@code null}
     */
    void deleteContact(JidProvider contact);

    /**
     * Records a finished VoIP call in the cross-device call history.
     *
     * @apiNote
     * Emit one mutation per terminated VoIP call. The {@link CallLog}
     * argument already carries the call identifier, the creator JID,
     * and the incoming/outgoing direction; the mutation index is
     * derived from those fields so every linked device sees the same
     * entry in its call tab. The {@link CallLog#callId() callId} and
     * {@link CallLog#callCreatorJid() callCreatorJid} fields must be
     * present.
     *
     * @param entry the call record to ship; must carry a non-empty
     *              {@code callId} and a {@code callCreatorJid}
     * @throws NullPointerException     if {@code entry} is {@code null}
     * @throws IllegalArgumentException if {@code entry} is missing
     *                                  {@code callId} or
     *                                  {@code callCreatorJid}
     */
    void addCallLog(CallLog entry);

    /**
     * Toggles whether the merchant account shares a specific customer's
     * data with the advertiser that drove the conversation.
     *
     * @apiNote
     * Drives the per-customer data-sharing switch surfaced on the
     * Click-To-WhatsApp advertising flow. The mutation is keyed on the
     * customer's LID so each customer has its own row.
     *
     * @param customer the customer's LID-form JID
     * @param enabled  {@code true} to opt the customer into per-customer
     *                 data sharing, {@code false} to opt them out
     * @throws NullPointerException if {@code customer} is {@code null}
     */
    void editAdvertiserDataSharing(Jid customer, boolean enabled);

    /**
     * Replaces the merchant's full set of custom payment methods.
     *
     * @apiNote
     * Drives the Brazil PIX phase-1 seller-sync feature: merchants curate
     * the payment methods that they want to offer their customers, and
     * the mutation overwrites the entire list on every linked device.
     *
     * @param methods the new snapshot of custom payment methods to
     *                broadcast
     * @throws NullPointerException if {@code methods} is {@code null}
     */
    void editCustomPaymentMethods(List<CustomPaymentMethod> methods);

    /**
     * Records the user's acceptance (or revocation) of a region-specific
     * payments terms-of-service notice.
     *
     * @apiNote
     * Drives the merchant terms-of-service flow that prefaces payments
     * features (for example the Brazilian PIX privacy policy). The
     * acceptance state propagates to every linked device so the user is
     * not prompted again.
     *
     * @param notice   the payment notice the acceptance applies to
     * @param accepted {@code true} when the user accepts the notice,
     *                 {@code false} to revoke the acceptance
     * @throws NullPointerException if {@code notice} is {@code null}
     */
    void editPaymentTos(PaymentTosAction.PaymentNotice notice, boolean accepted);

    WhatsAppClient addListener(WhatsAppClientListener listener);

    WhatsAppClient removeListener(WhatsAppClientListener listener);

    WhatsAppClient addChatsListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<Chat>> consumer);

    WhatsAppClient addContactsListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<Contact>> consumer);

    WhatsAppClient addStatusListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<ChatMessageInfo>> consumer);

    WhatsAppClient addNodeSentListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Node> consumer);

    WhatsAppClient addLoggedInListener(WhatsappClientListenerConsumer.Unary<WhatsAppClient> consumer);

    WhatsAppClient addCallListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, IncomingCall> consumer);

    WhatsAppClient addWebHistorySyncPastParticipantsListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, Collection<GroupPastParticipant>> consumer);

    WhatsAppClient addDisconnectedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, WhatsAppClientDisconnectReason> consumer);

    WhatsAppClient addWebAppPrimaryFeaturesListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, List<String>> consumer);

    WhatsAppClient addContactPresenceListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, Jid> consumer);

    WhatsAppClient addNewslettersListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<Newsletter>> consumer);

    WhatsAppClient addNodeReceivedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Node> consumer);

    WhatsAppClient addWebAppStateActionListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, SyncAction, String> consumer);

    WhatsAppClient addWebHistorySyncMessagesListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Chat, Boolean> consumer);

    WhatsAppClient addNewStatusListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, ChatMessageInfo> consumer);

    WhatsAppClient addAccountTypeChangedListener(WhatsappClientListenerConsumer.Quaternary<WhatsAppClient, Jid, ADVEncryptionType, ADVEncryptionType> consumer);

    WhatsAppClient addAboutChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, String, String> consumer);

    WhatsAppClient addNewMessageListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, MessageInfo> consumer);

    WhatsAppClient addMessageDeletedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, MessageInfo, Boolean> consumer);

    WhatsAppClient addPrivacySettingChangedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, PrivacySettingEntry> consumer);

    WhatsAppClient addWebHistorySyncProgressListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Integer, Boolean> consumer);

    WhatsAppClient addProfilePictureChangedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Jid> consumer);

    WhatsAppClient addMessageStatusListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, MessageInfo> consumer);

    WhatsAppClient addNameChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, String, String> consumer);

    WhatsAppClient addMessageReplyListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, MessageInfo, MessageInfo> consumer);

    WhatsAppClient addDeviceIdentityChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, Set<Jid>> consumer);

    WhatsAppClient addNewContactListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Contact> consumer);

    WhatsAppClient addContactBlockedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Jid> consumer);

    WhatsAppClient addContactTextStatusListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, ContactTextStatus> consumer);

    WhatsAppClient addLocaleChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, String, String> consumer);

    WhatsAppClient addRegistrationCodeListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Long> consumer);
}
