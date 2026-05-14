package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.CallOptions;
import com.github.auties00.cobalt.call.IncomingCall;
import com.github.auties00.cobalt.call.signaling.CallEndReason;
import com.github.auties00.cobalt.exception.WhatsAppException;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.message.send.ack.AckResult;
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
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.business.profile.BusinessCategoryTypeahead;
import com.github.auties00.cobalt.model.business.profile.BusinessProfile;
import com.github.auties00.cobalt.model.call.CallLink;
import com.github.auties00.cobalt.model.call.CallLinkCreate;
import com.github.auties00.cobalt.model.call.CallLinkMedia;
import com.github.auties00.cobalt.model.chat.*;
import com.github.auties00.cobalt.model.chat.community.*;
import com.github.auties00.cobalt.model.chat.group.*;
import com.github.auties00.cobalt.model.contact.*;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.federated.*;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.LidChange;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.newsletter.*;
import com.github.auties00.cobalt.model.payment.BrazilCustomPaymentMethod;
import com.github.auties00.cobalt.model.payment.BrazilCustomPaymentMethodCreate;
import com.github.auties00.cobalt.model.payment.PaymentsTosV3ConsumerVariant;
import com.github.auties00.cobalt.model.preference.*;
import com.github.auties00.cobalt.model.privacy.*;
import com.github.auties00.cobalt.model.reporting.*;
import com.github.auties00.cobalt.model.setting.*;
import com.github.auties00.cobalt.model.setting.notice.UserNoticeBundle;
import com.github.auties00.cobalt.model.setting.notice.UserNoticeStage;
import com.github.auties00.cobalt.model.setting.notice.UserNoticeStageQuery;
import com.github.auties00.cobalt.model.setting.privacy.*;
import com.github.auties00.cobalt.model.setting.push.PushConfig;
import com.github.auties00.cobalt.model.signal.*;
import com.github.auties00.cobalt.model.sync.AppStateSyncCollection;
import com.github.auties00.cobalt.model.sync.AppStateSyncResult;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * Public contract for the WhatsApp client.
 *
 * <p>This is the consumer-facing interface; every caller in the codebase
 * depends on it (never on a concrete implementation). The default
 * production implementation is {@link LinkedWhatsAppClient}, wired up by
 * {@link WhatsAppClientBuilder}. Tests can supply their own
 * implementation through the {@code client.test} package without
 * standing up a real socket.
 *
 * <p>Method-level Javadoc lives here on the interface; the
 * implementation in {@link LinkedWhatsAppClient} inherits docs via
 * {@code {@inheritDoc}} so each concrete method does not duplicate the
 * contract. Implementation-specific notes (WA-source mappings, timing,
 * adaptation comments) remain on the impl as inline annotations and
 * comments.
 */
public interface WhatsAppClient {
    /**
     * Returns a new {@link WhatsAppClientBuilder} for constructing a
     * configured {@link WhatsAppClient} instance.
     *
     * @return a fresh builder
     */
    static WhatsAppClientBuilder builder() {
        return WhatsAppClientBuilder.INSTANCE;
    }

    /**
     * Returns the persisted session state bound to this client.
     *
     * @return the store
     */
    WhatsAppStore store();

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
    WhatsAppClient connect();

    /**
     * Completes the pending request whose {@code id} attribute matches
     * the inbound node, if any.
     *
     * @param node the inbound node that may carry a response to a pending
     *             request
     */
    void resolvePendingRequest(Node node);

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
    void disconnect(WhatsAppClientDisconnectReason reason);

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
    void sendNodeWithNoResponse(Node node);

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
    Node sendNode(NodeBuilder node);

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
    Node sendNode(NodeBuilder node, Function<Node, Boolean> filter);

    /**
     * Dispatches a typed MEX (GraphQL-over-XMPP) request and records a
     * {@link MexEventV2Event} describing the outcome.
     *
     * <p>This is the shared sink for every outgoing MEX request and mirrors
     * WA Web's {@code WAWebMexNativeClient.fetchQuery} where each fetch is
     * wrapped by a {@code MexPerfTracker} that logs a {@code MexEventV2} WAM
     * event on success or failure. Callers pass the typed request value
     * directly — the GraphQL query id, the operation name, the encoding
     * discriminant and the outbound stanza are all derived from the request
     * itself through {@link MexOperation.Request#id()},
     * {@link MexOperation.Request#name()},
     * {@code instanceof MexOperation.ArgoRequest} and
     * {@link MexOperation.Request#toNode()}.
     * @param request the typed MEX request to dispatch; never {@code null}
     * @return the response node from the WhatsApp relay
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Node sendNode(MexOperation.Request request);

    /**
     * Dispatches a typed MEX request whose response is discarded while still
     * emitting the {@link MexEventV2Event} telemetry.
     *
     * <p>Used by MEX mutations where the caller only needs the side effect
     * of the stanza (for example newsletter leave/join) and ignores the
     * returned payload. Internally the method still blocks on the response
     * so the telemetry accurately records success/failure of the round trip,
     * matching the semantics of the original {@code sendNode(request.toNode())}
     * call sites the helper replaced.
     * @param request the typed MEX request to dispatch; never {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    void sendNodeWithNoResponse(MexOperation.Request request);

    /**
     * Dispatches a typed SMAX request and returns the parsed response node.
     *
     * <p>Convenience over the raw {@code sendNode(request.toNode())} call
     * pattern — every SMAX RPC implementation that builds the stanza via
     * {@link SmaxOperation.Request#toNode()} can be dispatched in a single
     * call.
     *
     * @param request the typed SMAX request to dispatch; never {@code null}
     * @return the inbound response node; never {@code null}
     * @throws NullPointerException            if {@code request} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Node sendNode(SmaxOperation.Request request);

    /**
     * Dispatches a typed SMAX request whose response is discarded.
     *
     * <p>Used for cast-shape SMAX RPCs where the caller only needs the
     * side effect and ignores the returned payload.
     *
     * @param request the typed SMAX request to dispatch; never {@code null}
     * @throws NullPointerException            if {@code request} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    void sendNodeWithNoResponse(SmaxOperation.Request request);

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
     */
    UsyncResult sendNode(UsyncQuery query) throws InterruptedException;

    /**
     * Disconnects this client from the WhatsApp servers, preserving its
     * credentials for future reconnections.
     *
     * <p>Equivalent to
     * {@link #disconnect(WhatsAppClientDisconnectReason) disconnect(DISCONNECTED)}.
     */
    void disconnect();

    /**
     * Disconnects and immediately re-establishes the connection.
     *
     * <p>Equivalent to
     * {@link #disconnect(WhatsAppClientDisconnectReason) disconnect(RECONNECTING)}.
     */
    void reconnect();

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
     */
    void logout();

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
     */
    void logoutCompanion(JidProvider companion);

    /**
     * Returns the companion JIDs currently linked to this account.
     *
     * <p>Runs a USync device-list query for the caller's own user JID
     * via the injected {@link DeviceService} and materialises the
     * cached device entries as companion JIDs (user+device slot). The
     * primary device (device 0) is included as the first entry when
     * present; companions follow in server order.
     *
     * <p>Callers that only need the raw {@link DeviceList} record can
     * use {@link DeviceService#syncAndGetDeviceList(Collection)}
     * directly; this helper exists to surface the user-facing list of
     * linked devices with each entry already projected into a
     * device-qualified JID.
     *
     * @return the linked companion JIDs, in server order
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open during the
     *                                         USync round-trip
     */
    SequencedCollection<Jid> queryLinkedDevices();

    /**
     * Returns whether a live socket to the WhatsApp servers is currently
     * open.
     *
     * @return {@code true} if the socket is open and the handshake has
     *         not been torn down, {@code false} otherwise
     */
    boolean isConnected();

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
    WhatsAppClient waitForDisconnection();

    /**
     * Delegates to the configured {@link WhatsAppClientErrorHandler} and
     * applies the returned {@link WhatsAppClientErrorHandler.Result} as a
     * concrete session-control decision.
     *
     * <p>Before delegating, any app-state (syncd) fatal failure is mirrored
     * to the WAM pipeline via {@link #emitSyncdFatalErrorMetric(WhatsAppWebAppStateSyncException)}.
     * This mirrors WA Web's {@code WAWebSyncdUploadFatalErrorMetric.uploadFatalErrorMetric}
     * central uploader, which WA Web fans out at every inline syncd fatal
     * detection site.
     *
     * @param exception the exception to handle
     */
    void handleFailure(WhatsAppException exception);

    /**
     * Pushes a batch of sync mutations for the given patch type to the
     * companion app-state service.
     *
     * @param type    the sync patch type being updated
     * @param patches the ordered mutations to apply
     */
    void pushWebAppState(SyncPatchType type, List<SyncPendingMutation> patches);

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
    boolean pullWebAppState(SyncPatchType... patches);

    /**
     * Sends an acknowledgment stanza for the given inbound node, using
     * the node's own {@code id} attribute as the acknowledgment id.
     *
     * @param node the inbound node to acknowledge
     */
    void sendAck(Node node);

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
    void sendAck(String id, Node node);

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
    void sendPreKeys(long keysCount);

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
    void sendReceipt(String id, JidProvider from, String type);

    /**
     * Queries the metadata of a group or community.
     *
     * @param chat the target group or community
     * @return the non-{@code null} metadata
     * @throws IllegalArgumentException if the JID is not a group or
     *         community
     * @throws NoSuchElementException if the server response is invalid
     */
    ChatMetadata queryChatMetadata(JidProvider chat);

    /**
     * Queries the WhatsApp Business profile associated with the given
     * contact.
     *
     * <p>Mirrors {@code WAWebQueryBusinessProfileJob.default}. The
     * legacy IQ stanza carries a single {@code <profile jid/>} entry
     * under {@code <business_profile v="3"/>}; the relay echoes the
     * full profile body when the merchant has one.
     *
     * @param contact the contact whose business profile should be
     *                fetched; never {@code null}
     * @return the parsed profile, or {@link Optional#empty()} if the
     *         contact is not a business
     * @throws NullPointerException            if {@code contact} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant
     */
    Optional<BusinessProfile> queryBusinessProfile(JidProvider contact);

    /**
     * Parses a {@code category} node into a {@link BusinessCategory},
     * URL-decoding the human-readable name.
     *
     * @param node the category node
     * @return the parsed category
     * @throws NoSuchElementException if the category content is missing
     */
    BusinessCategory parseBusinessCategory(Node node);

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
     * @param profile the new business-profile metadata; must not be
     *                {@code null}
     * @throws NullPointerException            if {@code profile} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editBusinessProfile(BusinessProfile profile);

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
     * @param enabled {@code true} to enable the cart, {@code false} to
     *                disable it
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void updateBusinessCartEnabled(boolean enabled);

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
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void deleteBusinessCoverPhoto();

    /**
     * Fetches the long-lived public verification certificate published by a
     * WhatsApp Business account.
     *
     * <p>WhatsApp Business accounts sign sensitive catalog payloads — the
     * merchant phone-number envelope, address-verification responses, and
     * the direct-connection-encrypted payloads attached to product orders —
     * with a server-issued certificate. This call returns the PEM that backs
     * those signatures so a Cobalt-driven storefront can verify that what
     * came over the wire was actually emitted by the advertised business and
     * not relayed through an impersonating peer.
     *
     * <p>Typical use is to fetch the certificate once when the storefront
     * first interacts with a business, cache it for the merchant's lifetime,
     * and re-validate every signed catalog response against it. Pair this
     * call with {@link #queryBusinessSignedUserInfo(Jid)} to obtain the
     * matching signed phone-number envelope.
     *
     * @param businessJid the JID of the business whose certificate is
     *                    being fetched; never {@code null}
     * @return the PEM string when the server returned a certificate;
     *         {@link Optional#empty()} when the server replied with no
     *         certificate (typically because the business has not been
     *         provisioned with one yet)
     * @throws NullPointerException            if {@code businessJid} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    Optional<String> queryBusinessPublicKey(JidProvider businessJid);

    /**
     * Fetches the cryptographically-signed phone-number envelope a WhatsApp
     * Business account exposes to its catalog clients.
     *
     * <p>The envelope binds the business's JID to its merchant phone number
     * and a notional business domain, all signed by the business's private
     * key. Callers verify the signature with the matching certificate
     * returned by {@link #queryBusinessPublicKey(Jid)}, which lets a
     * Cobalt-driven storefront prove the displayed contact details actually
     * belong to the business it is talking to. The {@code ttl_timestamp}
     * carried alongside is the server-recommended expiry after which a
     * client should re-fetch the envelope.
     *
     * <p>Most every field is wrapped in an {@link Optional} because the
     * server omits the entire envelope (and any individual sub-field)
     * whenever the business has not been provisioned with a signed identity
     * — applications should treat an all-empty result as "no verifiable
     * identity available" rather than as a hard error.
     *
     * @param businessJid the JID of the business whose signed-user-info
     *                    envelope should be fetched; never {@code null}
     * @return the parsed envelope; never {@code null}, with each field
     *         exposed as an {@link Optional} since the server may omit any
     *         or all of them
     * @throws NullPointerException            if {@code businessJid} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    BusinessSignedUserInfo queryBusinessSignedUserInfo(JidProvider businessJid);

    /**
     * Fetches the legal-entity disclosure that one or more WhatsApp Business
     * accounts publish to their customers.
     *
     * <p>Several jurisdictions (most notably India under the IT Rules 2021)
     * require business accounts to register the legal name of the entity
     * operating the account, the entity type, and dedicated customer-care /
     * grievance-officer contact channels. WhatsApp surfaces those fields in
     * the in-app "Business info" panel; this call returns the same data so
     * a Cobalt-driven catalog browser can render the disclosure before
     * letting the user place an order, or so a compliance dashboard can
     * audit the merchant's registration status.
     *
     * <p>The call is batched: a single round trip resolves the disclosure
     * for every JID the caller passes in, with each result returned in the
     * same order as the input list. Businesses that have not registered any
     * compliance information come back with empty / default-valued fields
     * rather than being dropped from the result.
     *
     * @param bizJids the business JIDs whose compliance metadata should
     *                be fetched; never {@code null}, must be non-empty
     * @return the parsed compliance entries in server order; never
     *         {@code null}, possibly empty
     * @throws NullPointerException            if {@code bizJids} is
     *                                         {@code null}
     * @throws IllegalArgumentException        if {@code bizJids} is empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    List<BusinessMerchantCompliance> queryMerchantCompliance(List<? extends JidProvider> jids);

    /**
     * Publishes or updates the legal-entity disclosure shown to customers
     * on the authenticated business account.
     *
     * <p>This is the writer counterpart to
     * {@link #queryMerchantCompliance(List)}: it overwrites the registered
     * entity name, entity type, custom type description, registration flag
     * and contact channels (customer care and grievance officer) shown in
     * the WhatsApp Business "Business info" panel. The disclosure is
     * server-persisted, propagates to every linked device, and becomes
     * visible to anyone querying the same business JID.
     *
     * <p>Every textual parameter is nullable: a non-{@code null} value
     * overwrites the server-side state, while {@code null} leaves the
     * existing value untouched. The {@code registered} flag is always
     * sent. The server echoes the post-update state back, which the method
     * returns so callers can confirm what was actually persisted without
     * an extra round trip.
     *
     * @param entityName              the legal-entity name; {@code null}
     *                                to leave the existing value untouched
     * @param entityType              the legal-entity type identifier;
     *                                {@code null} to leave it untouched
     * @param entityTypeCustom        free-text override for the entity
     *                                type; {@code null} to leave it
     *                                untouched
     * @param registered              whether the entity is currently
     *                                registered; always emitted on the
     *                                wire
     * @param customerCareDetails     the customer-care contact channels;
     *                                {@code null} to leave them untouched
     * @param grievanceOfficerDetails the grievance-officer contact
     *                                channels; {@code null} to leave them
     *                                untouched
     * @return the merchant-compliance entry the server returned after
     *         applying the update; never {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws NoSuchElementException          if the response carries no
     *                                         {@code <merchant_info>}
     *                                         child
     */
    BusinessMerchantCompliance editMerchantCompliance(MerchantComplianceEdit edit);

    /**
     * Searches the WhatsApp Business category catalog for entries whose
     * localized name matches the given partial query.
     *
     * <p>Every WhatsApp Business profile must declare a category (e.g.
     * "Restaurant", "Clothing store") which is shown in the in-app business
     * info panel and used for discoverability. WhatsApp's onboarding flow
     * powers its "Business category" picker with this autocomplete endpoint:
     * as the user types a few characters, the server returns the
     * server-localized categories whose name matches. Cobalt-driven
     * onboarding flows that let merchants configure their own category
     * should call this on every keystroke to drive the same picker.
     *
     * <p>The result also surfaces a "not a business" placeholder id:
     * categories that match the lookup but should be presented as the
     * "not a business" sentinel option carry their {@code notABiz} flag set,
     * which lets the caller render that option distinctly.
     *
     * @param query the partial text the user has typed; never {@code null}
     * @return the matched categories, each with the server-issued id, the
     *         localized display name, and the "not a business" flag; never
     *         {@code null}
     * @throws NullPointerException            if {@code query} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws NoSuchElementException          if the server's reply is
     *                                         malformed
     */
    BusinessCategoryTypeahead queryBusinessCategoryTypeahead(String query);

    /**
     * Queries the detail of a business order identified by a message id and
     * a server-issued token.
     *
     * <p>Mirrors {@code WAWebBizQueryOrderJob.queryOrder}. The request is
     * issued through the {@code queryOrder} MEX operation, which wraps
     * the GraphQL variables under {@code request.order} and dispatches the
     * IQ via the {@code w:mex} namespace. The response is projected into
     * an {@link BusinessOrder} carrying the creation timestamp, the
     * price details and the ordered items.
     *
     * <p>This entry point targets the GraphQL code path; the legacy
     * {@code fb:thrift_iq} fallback used by WA Web when the
     * {@code graphQLForGetOrderInfoEnabled} gate is off is intentionally
     * omitted since it has been largely replaced by the MEX path.
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
    Optional<BusinessOrder> queryOrder(String messageId, String tokenBase64);

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
    String createQuickReply(QuickReplyCreate create);

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
     * @param quickReplyId the stable id of the quick reply being edited
     * @param shortcut     the new shortcut text
     * @param message      the new message body
     * @param keywords     the new keyword list; {@code null} is coerced to
     *                     an empty list
     * @throws NullPointerException            if any of the string arguments
     *                                         is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<QuickReply> editQuickReply(QuickReplyEdit edit);

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
     * @param quickReplyId the id of the quick reply to delete
     * @throws NullPointerException            if {@code quickReplyId} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<QuickReply> deleteQuickReply(String quickReplyId);

    /**
     * Queries the bot profile for the given bot JID with the default persona.
     *
     * @param botJid the bot JID to query
     * @return the bot profile, or empty if not found or on error
     */
    Optional<BotProfile> queryBotProfile(JidProvider botJid);

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
    Optional<BotProfile> queryBotProfile(JidProvider botJid, String personaId);

    /**
     * Places a one-to-one voice or video call to {@code target} and
     * returns a live {@link ActiveCall} bound to the four media ports.
     *
     * @param target the JID of the callee, must be a user JID
     * @param options  the options for the call
     * @return the live call session
     * @throws NullPointerException            if {@code target} is {@code null}
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    ActiveCall startCall(JidProvider target, CallOptions options);

    /**
     * Accepts a pending {@link IncomingCall} offer with the given
     * {@link CallOptions} and returns a live {@link ActiveCall} bound
     * to the four media ports.
     *
     * <p>Sends the {@code <call><accept/></call>} stanza, registers
     * the live session in the call engine's in-flight registry, and
     * parks it in {@link CallState#CONNECTING} until transport setup
     * completes. The accepting {@code options} may downgrade an
     * offered video call to audio-only (set
     * {@link CallOptions#videoEnabled()} to {@code false}) but cannot
     * upgrade an audio-only offer to video — for that the caller must
     * place a fresh video call.
     *
     * @param offer   the offer being accepted
     * @param options the local side's preferred settings
     * @return the live session
     * @throws NullPointerException            if {@code offer} or
     *                                         {@code options} is {@code null}
     * @throws IllegalStateException           if the offer has already
     *                                         been responded to
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    ActiveCall acceptCall(IncomingCall offer, CallOptions options);

    /**
     * Rejects a pending {@link IncomingCall} offer with the given
     * {@link CallEndReason}.
     *
     * <p>Sends the {@code <call><reject/></call>} stanza, drops the
     * call from the store, and fires {@code onCallEnded} on every
     * registered listener with the supplied reason.
     *
     * @param offer  the offer being rejected
     * @param reason the reason to communicate to the peer
     * @throws NullPointerException            if {@code offer} or
     *                                         {@code reason} is {@code null}
     * @throws IllegalStateException           if the offer has already
     *                                         been responded to
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void rejectCall(IncomingCall offer, CallEndReason reason);

    /**
     * Sends a {@code <call><terminate reason call-id call-creator/></call>}
     * stanza ending an in-progress call.
     *
     * <p>Lets callers choose any of the WA Web {@link CallEndReason}
     * variants (timeout, mic permission denial, blocked-by-callee, etc).
     * The {@code call-creator} attribute is always set to the local
     * user's device JID, matching outgoing terminations from the call
     * initiator.
     *
     * @param callId the identifier of the call to terminate
     * @param reason the end-call reason; placed on the {@code reason}
     *               attribute as {@link CallEndReason#wireValue()}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws NoSuchElementException          if no call with the given id
     *                                         is cached in the store
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void terminateCall(String callId, CallEndReason reason);

    /**
     * Convenience overload of {@link #terminateCall(String, CallEndReason)}
     * that extracts the {@code callId} from the given {@link ActiveCall}.
     *
     * @param call   the in-progress call to terminate
     * @param reason the end-call reason
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void terminateCall(ActiveCall call, CallEndReason reason);

    /**
     * Sends a {@code <call><preaccept call-id call-creator/></call>} stanza
     * indicating that this device has finished its receive-side validation
     * of an incoming offer and is now alerting the local user.
     *
     * <p>WA Web emits a {@code <preaccept>} between {@code <offer>} and
     * {@code <accept>}; live captures show it carrying the negotiated
     * audio codec, encopt, and capability children. Cobalt has no media
     * plane to negotiate from, so the payload is reduced to the
     * {@code call-id} / {@code call-creator} pair the server requires.
     *
     * @param callId the identifier carried by the original offer
     * @throws NullPointerException            if {@code callId} is {@code null}
     * @throws NoSuchElementException          if no call with the given id
     *                                         is cached in the store
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void preacceptCall(String callId);

    /**
     * Convenience overload of {@link #preacceptCall(String)} that pulls
     * both the call id and the caller JID directly from the cached
     * {@link IncomingCall}.
     *
     * @param call the offer to preaccept
     * @throws NullPointerException            if {@code call} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void preacceptCall(IncomingCall call);

    /**
     * Sends a {@code <call><mute call-id call-creator state=...></call>}
     * stanza announcing that the local user has muted or unmuted their
     * microphone.
     *
     * @param callId the identifier of the in-progress call
     * @param muted  {@code true} to announce a mute, {@code false} for an
     *               unmute
     * @throws NullPointerException            if {@code callId} is {@code null}
     * @throws NoSuchElementException          if no call with the given id
     *                                         is cached in the store
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void muteCall(String callId, boolean muted);

    /**
     * Convenience overload of {@link #muteCall(String, boolean)} that
     * pulls the call id directly from the given {@link ActiveCall}.
     *
     * @param call  the in-progress call
     * @param muted {@code true} to announce a mute, {@code false} for an
     *              unmute
     * @throws NullPointerException            if {@code call} is {@code null}
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void muteCall(ActiveCall call, boolean muted);

    /**
     * Sends a {@code <call><video_state call-id call-creator state=...></call>}
     * stanza announcing that the local user has turned video on or off
     * during a call. Also drives the M4 mid-call video-upgrade flow —
     * {@code videoEnabled=true} is the upgrade request the peer sees as
     * {@code onCallVideoStateChanged(..., true)}, and {@code false} is
     * the inverse.
     *
     * @param call         the in-progress call to update
     * @param videoEnabled {@code true} to announce video-on, {@code false}
     *                     for video-off
     * @throws NullPointerException            if {@code call} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void editCallVideoState(ActiveCall call, boolean videoEnabled);

    /**
     * Initiates a group call by emitting the signalling
     * {@code <call><offer/></call>} stanza carrying the group JID and
     * participant list.
     *
     * <p>As with one-to-one calls, Cobalt emits only the signalling layer:
     * no group rekey, no per-participant encrypted session, no media.
     * @param group        the JID of the group being called, must be a group
     *                     server JID
     * @param participants the participants to invite; must be non-empty
     * @param video        whether to advertise this as a video call
     * @return the live call session
     * @throws NullPointerException     if {@code group} or
     *                                  {@code participants} is {@code null}
     * @throws IllegalArgumentException if {@code group} is not a group JID
     *                                  or {@code participants} is empty
     * @throws IllegalStateException    if this client is not logged in
     */
    ActiveCall startGroupCall(JidProvider group, Collection<? extends JidProvider> participants, boolean video);

    /**
     * Sends a {@code <call><group_update call-id call-creator action="add"></call>}
     * stanza adding the given participants to an in-progress group call.
     *
     * <p>WA Web emits this when the call's host invites additional users
     * after the initial offer. The {@code <group_update>} child carries a
     * nested {@code <group_info>} node listing the JIDs being added.
     *
     * @param callId       the identifier of the in-progress call
     * @param participants the participants to invite; must be non-empty
     * @throws NullPointerException            if any argument is {@code null}
     * @throws NoSuchElementException          if no call with the given id
     *                                         is cached in the store
     * @throws IllegalArgumentException        if the call's chat is not a
     *                                         group/community JID or
     *                                         {@code participants} is empty
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void addCallParticipants(String callId, Collection<? extends JidProvider> participants);

    /**
     * Convenience overload of
     * {@link #addCallParticipants(String, Collection)} that pulls the
     * call id and group JID directly from the given {@link ActiveCall}.
     *
     * @param call         the in-progress call
     * @param participants the participants to invite; must be non-empty
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if the call is not a
     *                                         group/community call or
     *                                         {@code participants} is empty
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void addCallParticipants(ActiveCall call, Collection<? extends JidProvider> participants);

    /**
     * Sends a {@code <call><group_update call-id call-creator action="remove"></call>}
     * stanza removing the given participants from an in-progress group call.
     *
     * @param callId       the identifier of the in-progress call
     * @param participants the participants to remove; must be non-empty
     * @throws NullPointerException            if any argument is {@code null}
     * @throws NoSuchElementException          if no call with the given id
     *                                         is cached in the store
     * @throws IllegalArgumentException        if the call's chat is not a
     *                                         group/community JID or
     *                                         {@code participants} is empty
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void removeCallParticipants(String callId, Collection<? extends JidProvider> participants);

    /**
     * Convenience overload of
     * {@link #removeCallParticipants(String, Collection)} that pulls the
     * call id and group JID directly from the given {@link ActiveCall}.
     *
     * @param call         the in-progress call
     * @param participants the participants to remove; must be non-empty
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if the call is not a
     *                                         group/community call or
     *                                         {@code participants} is empty
     * @throws IllegalStateException           if this client is not logged in
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void removeCallParticipants(ActiveCall call, Collection<? extends JidProvider> participants);

    /**
     * Issues a {@code trusted_contact} privacy token for the given peer,
     * mirroring {@code WAWebSendTcTokenChatAction.sendTcToken} → {@code
     * WAWebSetPrivacyTokensJob.issuePrivacyToken}.
     *
     * <p>WhatsApp requires the local user to vouch for a peer ahead of the
     * call signalling exchange so the relay can decide whether the peer's
     * device should ring. The IQ envelope is
     * {@code <iq type="set" xmlns="privacy" to="s.whatsapp.net">
     * <tokens><token jid type="trusted_contact" t/></tokens></iq>}.
     *
     * <p>Cobalt's call placement does not block on this token, but
     * applications driving an end-to-end interactive call should issue it
     * for every peer they intend to call so the relay's TC-token gate is
     * satisfied.
     *
     * @param peer the JID of the peer to vouch for
     * @throws NullPointerException            if {@code peer} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the server rejects the IQ
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void issueTrustedContactToken(JidProvider peer);

    /**
     * Sends a {@link ScheduledCallCreationMessage} announcing a scheduled
     * voice or video call inside the given chat.
     *
     * <p>Mirrors the WhatsApp UX where the user picks a future date and
     * time, optionally names the meeting, and posts an announcement that
     * other participants can opt-in to. The actual call happens later via
     * {@link #startCall(Jid, boolean)} or {@link #startGroupCall(Jid,
     * Collection, boolean)}.
     *
     * @param chat        the chat to post the announcement in
     * @param title       the human-readable title of the scheduled call
     * @param scheduledAt the {@link Instant} the call is scheduled for
     * @param video       {@code true} for a video call, {@code false} for
     *                    voice-only
     * @return the server-ack result describing the delivery outcome
     * @throws NullPointerException                   if any reference
     *                                                argument is {@code null}
     * @throws WhatsAppMessageException.Send.InvalidRecipient if the JID does not match a supported chat type
     */
    AckResult sendScheduledCall(JidProvider chat, String title, Instant scheduledAt, boolean video);

    /**
     * Sends a {@link ScheduledCallEditMessage} cancelling a previously
     * announced scheduled call.
     *
     * @param creationKey the {@link MessageKey} of the original
     *                    {@link ScheduledCallCreationMessage}; its
     *                    {@link MessageKey#parentJid() parentJid} identifies
     *                    the chat the announcement was posted in
     * @return the server-ack result describing the delivery outcome
     * @throws NullPointerException                           if any
     *                                                        argument is
     *                                                        {@code null}
     * @throws NoSuchElementException                         if
     *                                                        {@code creationKey}
     *                                                        has no parent JID
     * @throws WhatsAppMessageException.Send.InvalidRecipient if the JID does not match a supported chat type
     */
    AckResult cancelScheduledCall(MessageKey creationKey);

    /**
     * Resolves a shared call-link token, then places a 1:1 call to the
     * link's owning device.
     *
     * <p>Mirrors WhatsApp Web's "Join via link" flow: the local client
     * issues a {@code <link_query>} to fetch the link's media kind and
     * call creator, then dispatches an {@code <offer>} addressed to the
     * creator.
     *
     * @param token the call-link token (the path segment after
     *              {@code https://call.whatsapp.com/voice/} or
     *              {@code .../video/})
     * @param media the media kind expected by the link
     * @return the live call session, or empty when the relay rejects
     *         the join request
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejects the query
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    Optional<ActiveCall> joinCallLink(String token, CallLinkMedia media);

    /**
     * Queries the server for the list of newsletters this account follows
     * and reconciles them into the local store.
     *
     * <p>Dispatches the
     * {@link FetchAllNewslettersMetadataMexRequest mexFetchAllNewsletters}
     * MEX query and, for every item returned, ensures a matching
     * {@link Newsletter} exists in the store keyed by its JID. The order of
     * the returned collection mirrors the server-side order, which WA Web
     * also surfaces verbatim to the UI.
     *
     * @return the newsletters, in server order
     */
    SequencedCollection<Newsletter> queryNewsletters();

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
     */
    SequencedCollection<Chat> queryGroups();

    /**
     * Queries the current invite code attached to the given group
     * without supplying a telemetry context tag.
     *
     * <p>Convenience for
     * {@link #queryGroupInviteCode(JidProvider, String)} that passes
     * {@code null} for {@code queryContext}.
     *
     * @param groupProvider the non-{@code null} target group JID
     * @return an {@link Optional} carrying the current invite-code
     *         scalar, or empty when the relay returned no payload
     * @throws NullPointerException     if {@code groupProvider} is {@code null}
     */
    Optional<String> queryGroupInviteCode(JidProvider group);

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
     */
    String revokeGroupInviteCode(JidProvider group);

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
     */
    Jid joinGroupViaInvite(String inviteCode);

    /**
     * Fetches the profile picture of a group through a public invite link,
     * without joining the group first.
     *
     * <p>WhatsApp's invite-link landing screen shows the group's icon,
     * subject and member count before the user commits to joining; this
     * method backs that preview. Applications that resolve
     * {@code chat.whatsapp.com/<code>} links — for example to render a
     * preview card before the user taps "Join group" — should call this
     * with the parsed invite code to obtain a downloadable URL for the
     * group icon.
     *
     * <p>This call does not change the local group membership and works
     * even when the caller is not a member of the group; the returned URL
     * can be downloaded via the standard media-download path.
     *
     * @param group      the JID of the group the invite refers to; must be
     *                   a group JID
     * @param inviteCode the public invite code parsed from the
     *                   {@code chat.whatsapp.com} URL; never {@code null}
     * @return the picture identity, type, download URL and direct-path
     *         tuple; never {@code null}
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws NoSuchElementException          if the server reply carries no
     *                                         picture entry
     */
    GroupInvitePicture queryGroupInvitePicture(JidProvider group, String inviteCode);

    /**
     * Overload of {@link #queryGroupInvitePicture(JidProvider, String)}
     * accepting a lookup hint (typically {@code "url"} to request a CDN
     * download URL; pass {@code null} to ask the server only for identity
     * metadata).
     *
     * @param group      the group JID
     * @param inviteCode the invite code
     * @param query      the lookup hint, or {@code null}
     * @return the picture entry
     * @throws NullPointerException            if {@code group} or
     *                                         {@code inviteCode} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws NoSuchElementException          if no picture entry is returned
     */
    GroupInvitePicture queryGroupInvitePicture(JidProvider group, String inviteCode, String query);

    /**
     * Fetches the low-resolution {@code preview} thumbnail for an invite
     * link's group icon.
     *
     * @param group      the group JID
     * @param inviteCode the public invite code
     * @return the picture entry
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws NoSuchElementException          if no picture entry is returned
     */
    GroupInvitePicture queryGroupInvitePicturePreview(JidProvider group, String inviteCode);

    /**
     * Overload of {@link #queryGroupInvitePicturePreview(JidProvider, String)}
     * accepting a lookup hint.
     *
     * @param group      the group JID
     * @param inviteCode the invite code
     * @param query      the lookup hint, or {@code null}
     * @return the picture entry
     * @throws NullPointerException            if {@code group} or
     *                                         {@code inviteCode} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws NoSuchElementException          if no picture entry is returned
     */
    GroupInvitePicture queryGroupInvitePicturePreview(JidProvider group, String inviteCode, String query);

    /**
     * Queries group metadata using a v4 invite received in-band via a
     * {@link GroupInviteMessage}.
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
     * @param inviteTimestamp the non-{@code null} invite expiration time
     * @param inviteCode      the non-{@code null} invite code from the
     *                        {@code GroupInviteMessage}
     * @return the non-{@code null} group metadata
     * @throws NullPointerException   if any JID / invite code / timestamp is {@code null}
     * @throws NoSuchElementException if the server response is not a group
     */
    GroupMetadata queryGroupInfoByInvite(GroupInvite invite);

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
     * @param inviteTimestamp the non-{@code null} invite expiration time
     */
    void sendGroupInvite(JidProvider group, JidProvider target, Instant inviteTimestamp);

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
     */
    List<Jid> queryGroupJoinRequests(JidProvider group);

    /**
     * Approves a pending join request, admitting the applicant into the
     * group.
     *
     * @param group     the non-{@code null} target group JID
     * @param applicant the non-{@code null} applicant JID
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     */
    void acceptGroupJoinRequest(JidProvider group, JidProvider applicant);

    /**
     * Rejects a pending join request, keeping the applicant out of the
     * group.
     *
     * @param group     the non-{@code null} target group JID
     * @param applicant the non-{@code null} applicant JID
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     */
    void rejectGroupJoinRequest(JidProvider group, JidProvider applicant);

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
     */
    AckResult sendPeerMessage(JidProvider chatJid, ChatMessageInfo response);

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
     */
    Map<Jid, Boolean> hasWhatsapp(Collection<? extends JidProvider> phoneNumbers);

    /**
     * Convenience singleton wrapper over
     * {@link #hasWhatsapp(Collection)} for checking a single phone-number
     * JID.
     *
     * @param phone the non-{@code null} user JID to look up
     * @return {@code true} if the server reports the phone as registered
     * @throws NullPointerException if {@code phone} is {@code null}
     */
    boolean hasWhatsapp(JidProvider phone);

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
     */
    Optional<String> queryName(JidProvider jid);

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
     */
    Map<Jid, Jid> syncContacts(Collection<ContactCard> contacts);

    /**
     * Queries the full metadata envelope for a newsletter with the
     * viewer role the account currently holds.
     *
     * <p>Convenience for
     * {@link #queryNewsletter(Jid, NewsletterViewerRole, boolean)} that
     * passes {@code false} for {@code dehydrated}.
     *
     * @param newsletterJid the non-{@code null} newsletter JID
     * @param role          the viewer role to assert during the query
     * @return the newsletter metadata, or empty if not accessible
     * @throws NullPointerException if {@code newsletterJid} is {@code null}
     */
    Optional<Newsletter> queryNewsletter(JidProvider newsletterJid, NewsletterViewerRole role);

    /**
     * Queries metadata for a single newsletter, choosing between the
     * full and the lightweight ("dehydrated") projection.
     *
     * <p>When {@code dehydrated} is {@code false} (the default),
     * dispatches the {@link FetchNewsletterMexRequest mexGetNewsletter}
     * MEX query with all view flags enabled so the server returns the
     * full thread metadata together with the viewer-scoped settings.
     *
     * <p>When {@code dehydrated} is {@code true}, dispatches the
     * {@link FetchNewsletterDehydratedMexRequest mexGetNewsletterDehydrated}
     * query, which drops the heavy fields (full image, viewer-scoped
     * settings) and only echoes the subscriber count, verification flag,
     * reaction-codes setting and any associated WAMO subscription plan
     * identifier. Those scalars are folded back into the store-resident
     * newsletter so subsequent reads observe them.
     *
     * @param newsletterJid the non-{@code null} newsletter JID
     * @param role          the viewer role to assert during the query,
     *                      or {@code null} to omit the {@code view_role}
     *                      field
     * @param dehydrated    when {@code true} request the lightweight
     *                      projection; when {@code false} request the
     *                      full one
     * @return the newsletter metadata, or empty if not accessible
     * @throws NullPointerException if {@code newsletterJid} is {@code null}
     */
    Optional<Newsletter> queryNewsletter(JidProvider newsletterJid, NewsletterViewerRole role, boolean dehydrated);

    /**
     * Creates a brand-new newsletter owned by this account.
     *
     * <p>Dispatches the
     * {@link CreateNewsletterMexRequest mexCreateNewsletter} MEX mutation
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
     */
    Newsletter createNewsletter(NewsletterCreate create);

    /**
     * Edits the mutable metadata (name, description, picture) of a
     * newsletter owned by this account.
     *
     * <p>Dispatches the
     * {@link UpdateNewsletterMexRequest mexUpdateNewsletter} MEX mutation.
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
     */
    void editNewsletterMetadata(NewsletterMetadataEdit edit);

    /**
     * Permanently deletes a newsletter owned by this account.
     *
     * <p>Dispatches the
     * {@link DeleteNewsletterMexRequest mexDeleteNewsletter} MEX mutation
     * and removes the newsletter from the local store on success.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @throws NullPointerException if {@code newsletter} is {@code null}
     */
    Optional<Newsletter> deleteNewsletter(JidProvider newsletter);

    /**
     * Subscribes this account to the given newsletter so future updates
     * are delivered.
     *
     * <p>Dispatches the
     * {@link JoinNewsletterMexRequest mexJoinNewsletter} MEX mutation and
     * registers the newsletter in the local store so subsequent lookups
     * succeed.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @throws NullPointerException if {@code newsletter} is {@code null}
     */
    void joinNewsletter(JidProvider newsletter);

    /**
     * Unsubscribes this account from the given newsletter so updates stop
     * being delivered.
     *
     * <p>Dispatches the
     * {@link LeaveNewsletterMexRequest mexLeaveNewsletter} MEX mutation
     * and removes the newsletter from the local store on success.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @throws NullPointerException if {@code newsletter} is {@code null}
     */
    void leaveNewsletter(JidProvider newsletter);

    /**
     * Toggles the mute state on admin-activity notifications for the given
     * newsletter.
     *
     * <p>Dispatches the
     * {@link UpdateNewsletterUserSettingMexRequest mexUpdateNewsletterUserSetting}
     * MEX mutation with {@code type=MUTE_ADMIN_ACTIVITY} and
     * {@code value=ON} (mute) or {@code value=OFF} (unmute).
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param mute       {@code true} to mute, {@code false} to unmute
     * @throws NullPointerException if {@code newsletter} is {@code null}
     */
    void muteNewsletter(JidProvider newsletter, boolean mute);

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
     */
    void reactToNewsletterMessage(JidProvider newsletter, String serverMessageId, String emoji);

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
     */
    void revokeNewsletterMessage(JidProvider newsletter, String serverMessageId);

    /**
     * Accepts a pending newsletter admin invitation addressed to this
     * account.
     *
     * <p>Dispatches the
     * {@link AcceptNewsletterAdminInviteMexRequest acceptNewsletterAdminInvite}
     * MEX mutation.
     *
     * @param newsletter the non-{@code null} newsletter JID whose pending
     *                   admin invite is being accepted
     * @throws NullPointerException if {@code newsletter} is {@code null}
     */
    void acceptNewsletterAdminInvite(JidProvider newsletter);

    /**
     * Revokes a pending admin invitation previously issued by this
     * account for the given newsletter.
     *
     * <p>Dispatches the
     * {@link RevokeNewsletterAdminInviteMexRequest revokeNewsletterAdminInvite}
     * MEX mutation.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param admin      the non-{@code null} JID of the user whose pending
     *                   invite is being revoked
     * @throws NullPointerException if any argument is {@code null}
     */
    void revokeNewsletterAdminInvite(JidProvider newsletter, JidProvider admin);

    /**
     * Demotes an existing newsletter administrator back to a regular
     * follower.
     *
     * <p>Dispatches the
     * {@link DemoteNewsletterAdminMexRequest demoteNewsletterAdmin} MEX
     * mutation.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param admin      the non-{@code null} JID of the admin to demote
     * @throws NullPointerException if any argument is {@code null}
     */
    void demoteNewsletterAdmin(JidProvider newsletter, JidProvider admin);

    /**
     * Updates the reaction policy for a newsletter owned by this account.
     *
     * <p>Dispatches the
     * {@link UpdateNewsletterMexRequest mexUpdateNewsletter} MEX mutation
     * with an {@code updates} object carrying the serialised reaction
     * settings. WA Web treats reaction policy changes as a standard
     * newsletter metadata edit.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param setting    the non-{@code null} reaction policy to install
     * @throws NullPointerException if any argument is {@code null}
     */
    void editNewsletterReactionSetting(JidProvider newsletter, NewsletterReactionSettings setting);

    /**
     * Queries the capability flags granted to this account on the given
     * newsletter.
     *
     * <p>Capabilities are the typed feature gates the relay turns on for a
     * channel: insights dashboards, polls and quizzes, music attachments,
     * sticker pack sharing, the channel-status producer API, and so on.
     * They drive which UI affordances Cobalt can surface for the
     * authenticated admin and what operations are accepted by the relay
     * for this channel.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterAdminCapabilitiesMexRequest mexFetchNewsletterAdminCapabilities}
     * MEX query and decodes each entry into the {@link NewsletterCapability}
     * enum, mapping unrecognised tokens to
     * {@link NewsletterCapability#UNKNOWN}.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @return the capabilities granted to this account; never {@code null}
     * @throws NullPointerException          if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterCapability> queryNewsletterAdminCapabilities(JidProvider newsletter);

    /**
     * Queries the number of administrators currently configured on the
     * given newsletter.
     *
     * <p>Backs the "X admins" badge surfaced on the channel info screen.
     * The relay's {@code mexFetchNewsletterAdminInfo} fragment also echoes
     * back the channel id, but it is identical to the input and therefore
     * not exposed by this method; callers already hold the JID they
     * passed in. The full admin roster lives behind separate queries
     * such as {@link #queryNewsletterPendingInvites(Jid)} and the admin
     * profile lookups.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterAdminInfoMexRequest mexFetchNewsletterAdminInfo}
     * MEX query.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @return an {@link OptionalLong} carrying the admin count, or empty
     *         when the relay did not report one
     * @throws NullPointerException          if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    OptionalLong queryNewsletterAdminInfo(JidProvider newsletter);

    /**
     * Queries a page of followers for the given newsletter.
     *
     * <p>Backs the follower roster surface that channel admins use to
     * inspect their subscriber base and identify who currently holds an
     * admin role. Each entry exposes the follower JID, the optional
     * push-name and disclosed phone number, the channel-relative role
     * (subscriber, admin, owner) and the moment at which the follow
     * happened.
     *
     * <p>The caller is responsible for clamping {@code count} to the
     * server-imposed maximum exposed by the {@code maxSubscriberNumber}
     * AB prop in WA Web; the relay rejects oversized pages.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterFollowersMexRequest mexFetchNewsletterFollowers}
     * MEX query.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param count      the requested follower page size; the caller should
     *                   clamp it against the server maximum
     * @return the followers reported on this page, in server order; never
     *         {@code null}
     * @throws NullPointerException          if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterFollower> queryNewsletterFollowers(JidProvider newsletter, int count);

    /**
     * Queries the list of pending administrator invitations attached to
     * the given newsletter.
     *
     * <p>Newsletter owners may issue admin invitations to other users via
     * {@link #createNewsletterAdminInvite(Jid, Jid)}; this query returns
     * those invitations that have been issued but not yet accepted via
     * {@link #acceptNewsletterAdminInvite(Jid)} or revoked via
     * {@link #revokeNewsletterAdminInvite(Jid, Jid)}, so the owner UI can
     * surface them as pending entries.
     *
     * <p>The pending-invites fragment carries the invitee identifier and
     * disclosed phone number but not the invitation expiration time —
     * that field is only echoed back at creation time. Cobalt therefore
     * leaves {@link NewsletterAdminInvite#expirationTime()} empty for
     * entries returned by this query.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterPendingInvitesMexRequest mexFetchNewsletterPendingInvites}
     * MEX query.
     *
     * @param newsletter the non-{@code null} newsletter JID whose pending
     *                   admin invitations are being listed
     * @return the pending invitations, in server order; never {@code null}
     * @throws NullPointerException          if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterAdminInvite> queryNewsletterPendingInvites(JidProvider newsletter);

    /**
     * Convenience overload of
     * {@link #queryNewsletterDirectoryList(NewsletterDirectoryListView, List, List, Long, String, boolean)}
     * fetching the first page with no filters and the relay's default page
     * size.
     *
     * @param view the directory slice
     * @return the first page of results
     * @throws NullPointerException if {@code view} is {@code null}
     */
    NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view);

    /**
     * Convenience overload of
     * {@link #queryNewsletterDirectoryList(NewsletterDirectoryListView, List, List, Long, String, boolean)}
     * fetching the next page with no filters.
     *
     * @param view        the directory slice
     * @param cursorToken the pagination cursor returned by a previous page
     * @return the requested page of results
     * @throws NullPointerException if {@code view} is {@code null}
     */
    NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view, String cursorToken);

    /**
     * Queries a paginated page of the newsletter directory filtered by view
     * (Recommended, New, Popular, Featured, Trending) and optional
     * country/category filters.
     *
     * <p>This query powers the explore tab of the newsletter directory.
     * The returned page bundles the directory entries together with a
     * forward-only cursor that callers feed back to fetch the following
     * page; pass {@code null} as {@code cursorToken} on the first call.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterDirectoryListMexRequest mexFetchNewsletterDirectoryList}
     * MEX query.
     *
     * @param view                 the directory slice to query; never
     *                             {@code null}
     * @param countryCodes         the ISO country codes to filter by, or
     *                             {@code null} for no country filter
     * @param categories           the upper-case category wire strings to
     *                             filter by (e.g. {@code "BUSINESS"}), or
     *                             {@code null} for no category filter
     * @param limit                the page size, or {@code null} to let the
     *                             relay apply its default
     * @param cursorToken          the start cursor for pagination, or
     *                             {@code null} on the first page
     * @param fetchStatusMetadata  {@code true} to request the optional
     *                             {@code status_metadata} sub-selection,
     *                             mirroring
     *                             {@code WAWebNewsletterGatingUtils.isNewsletterStatusReceiverEnabled()}
     * @return the directory page bundling the entries and the next-page
     *         cursor; never {@code null}
     * @throws NullPointerException          if {@code view} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view, List<String> countryCodes, List<String> categories, Long limit, String cursorToken, boolean fetchStatusMetadata);

    /**
     * Searches the newsletter directory for channels whose names, handles
     * or descriptions match the given free-text query.
     *
     * <p>The optional category filter narrows the search to specific
     * editorial categories. Pagination follows the same cursor protocol
     * as {@link #queryNewsletterDirectoryList}: pass {@code null} on the
     * initial call and feed back the cursor returned by
     * {@link NewsletterDirectoryPage#nextCursor()} on subsequent calls.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterDirectorySearchResultsMexRequest mexFetchNewsletterDirectorySearchResults}
     * MEX query.
     *
     * @param searchText the free-text search hint
     * @return the first page of matching results
     */
    NewsletterDirectoryPage searchNewsletterDirectory(String searchText);

    /**
     * Overload of {@link #searchNewsletterDirectory(String)} accepting a
     * category filter.
     *
     * @param searchText the search hint
     * @param categories the category filter, or {@code null}
     * @return the first page of matching results
     */
    NewsletterDirectoryPage searchNewsletterDirectory(String searchText, List<String> categories);

    /**
     * Full-arg form of {@link #searchNewsletterDirectory}.
     *
     * @param searchText          the free-text search query, or {@code null}
     * @param categories          the upper-case category wire strings to
     *                            filter by, or {@code null}
     * @param limit               the page size, or {@code null}
     * @param cursorToken         the pagination cursor, or {@code null}
     * @param fetchStatusMetadata {@code true} to request the optional
     *                            {@code status_metadata} sub-selection
     * @return the directory page bundling the entries and the next-page cursor
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterDirectoryPage searchNewsletterDirectory(String searchText, List<String> categories, Long limit, String cursorToken, boolean fetchStatusMetadata);

    /**
     * Queries the newsletter directory landing categories together with a
     * preview of featured channels for each category.
     *
     * <p>WA Web renders this query's response as the directory landing
     * surface: each category is shown together with a handful of
     * featured newsletters as a visual preview before the user drills
     * down. The optional input is a serialised filter context — Cobalt
     * threads it through opaquely.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterDirectoryCategoriesPreviewMexRequest mexFetchNewsletterDirectoryCategoriesPreview}
     * MEX query.
     *
     * @param input the optional input variable forwarded to the relay;
     *              may be {@code null}
     * @return the categories surfaced on the directory landing screen,
     *         each carrying a featured-newsletter preview; never
     *         {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterDirectoryCategory> queryNewsletterDirectoryCategoriesPreview(String input);

    /**
     * Convenience overload of
     * {@link #queryRecommendedNewsletters(Long, List, boolean)} fetching the
     * default page with no country scope.
     *
     * @return the directory page
     */
    NewsletterDirectoryPage queryRecommendedNewsletters();

    /**
     * Queries the recommended-newsletters feed personalised for this
     * account.
     *
     * <p>Dispatches the
     * {@link FetchRecommendedNewslettersMexRequest mexFetchRecommendedNewsletters}
     * MEX query.
     *
     * @param limit                the maximum number of recommended
     *                             newsletters to return, or {@code null}
     *                             to let the relay apply its default page
     *                             size
     * @param countryCodes         the ISO country codes used to scope the
     *                             recommendation, or {@code null} to omit
     *                             the field
     * @param fetchStatusMetadata  {@code true} to request the optional
     *                             {@code status_metadata} sub-selection
     * @return the directory page bundling the recommended entries and
     *         the next-page cursor; never {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterDirectoryPage queryRecommendedNewsletters(Long limit, List<String> countryCodes, boolean fetchStatusMetadata);

    /**
     * Convenience overload of
     * {@link #querySimilarNewsletters(JidProvider, Long, List, boolean)} using
     * the relay's default page size and no country scope.
     *
     * @param newsletter the seed newsletter
     * @return the similar newsletters
     * @throws NullPointerException if {@code newsletter} is {@code null}
     */
    List<NewsletterDirectoryEntry> querySimilarNewsletters(JidProvider newsletter);

    /**
     * Queries newsletters that are similar to the given seed newsletter.
     *
     * <p>WA Web surfaces this as a "you might also like" rail on the
     * channel page. The relay computes the similarity entirely server-side;
     * the client only supplies the seed JID, the page size and an optional
     * country scope.
     *
     * <p>Dispatches the
     * {@link FetchSimilarNewslettersMexRequest mexFetchSimilarNewsletters}
     * MEX query.
     *
     * @param newsletter           the non-{@code null} seed newsletter JID
     * @param limit                the maximum number of similar newsletters
     *                             to return, or {@code null} to let the
     *                             relay apply its default
     * @param countryCodes         the ISO country codes used to scope the
     *                             recommendation, or {@code null} to omit
     *                             the field
     * @param fetchStatusMetadata  {@code true} to request the optional
     *                             {@code status_metadata} sub-selection
     * @return the similar newsletters reported by the relay; never
     *         {@code null}
     * @throws NullPointerException          if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterDirectoryEntry> querySimilarNewsletters(JidProvider newsletter, Long limit, List<String> countryCodes, boolean fetchStatusMetadata);

    /**
     * Queries the server-side ("plaintext") link preview for a URL pasted
     * into a newsletter compose surface.
     *
     * <p>Newsletter messages cannot use the regular client-side link
     * preview pipeline because the recipient anonymity guarantee forbids
     * the client from fetching the target URL directly. Instead, the
     * server acts as a trusted proxy: it unfurls the URL, returns the
     * title/description plus an encrypted media handle that can be
     * downloaded through the standard media pipeline, and reveals nothing
     * about the recipient.
     *
     * <p>Dispatches the
     * {@link FetchPlaintextLinkPreviewMexRequest} MEX query.
     *
     * @param url the non-{@code null} URL to unfurl
     * @return an {@link Optional} carrying the unfurled preview, or
     *         empty when the relay returned no payload
     * @throws NullPointerException if {@code url} is {@code null}
     */
    Optional<NewsletterLinkPreview> queryNewsletterLinkPreview(String url);

    /**
     * Checks whether the given URL belongs to a domain whose link previews
     * may be rendered inside newsletter messages.
     *
     * <p>The WhatsApp backend maintains an allow-list of domains whose
     * previews may be unfurled in newsletter posts. Compose surfaces call
     * this query before publishing so they can warn the user when a
     * disallowed domain would otherwise be silently stripped of its
     * preview.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterIsDomainPreviewableMexRequest mexFetchNewsletterIsDomainPreviewable}
     * MEX query.
     *
     * @param url the non-{@code null} URL whose domain is being validated
     * @return {@code true} when the relay reports the domain as
     *         previewable, {@code false} otherwise (including when the
     *         domain is missing from the response or the response is
     *         empty)
     * @throws NullPointerException if {@code url} is {@code null}
     */
    boolean isNewsletterDomainPreviewable(String url);

    /**
     * Queries the per-emoji list of senders that reacted to the given
     * newsletter message.
     *
     * <p>Each reaction code (emoji) is reported together with its sender
     * roster — useful when building the "who reacted with X" surface in
     * the message details panel. The query returns reactions for every
     * code present on the message; callers can filter to a specific code
     * client-side.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterMessageReactionSenderListMexRequest mexFetchNewsletterMessageReactionSenderList}
     * MEX query.
     *
     * @param newsletter      the non-{@code null} newsletter JID
     * @param serverMessageId the server-assigned message id
     * @return the per-emoji reactor list, in server order; never
     *         {@code null}
     * @throws NullPointerException          if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterReactor> queryNewsletterMessageReactionSenders(JidProvider newsletter, long serverMessageId);

    /**
     * Convenience overload of
     * {@link #queryNewsletterPollVoters(JidProvider, long, long, String)} that
     * returns voters across every option.
     *
     * @param newsletter      the newsletter JID
     * @param serverMessageId the poll message id
     * @param limit           the maximum voter edges per option
     * @return the per-option voter groups
     * @throws NullPointerException if {@code newsletter} is {@code null}
     */
    List<NewsletterPollVoter> queryNewsletterPollVoters(JidProvider newsletter, long serverMessageId, long limit);

    /**
     * Queries the list of voters on a newsletter poll, optionally narrowed
     * to a single poll option.
     *
     * <p>Newsletter polls track voters per option using a base64-encoded
     * option hash. Passing a non-{@code null} {@code voteHash} narrows the
     * response to the senders of that specific option; passing
     * {@code null} returns voters across every option of the poll.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterPollVotersMexRequest} MEX query.
     *
     * @param newsletter      the non-{@code null} newsletter JID hosting
     *                        the poll
     * @param serverMessageId the server-assigned id of the poll message
     * @param limit           the maximum number of voter edges to return
     * @param voteHash        the base64-encoded option hash to filter on,
     *                        or {@code null} to return voters across every
     *                        option
     * @return the per-option voter groups, in server order; never
     *         {@code null}
     * @throws NullPointerException          if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterPollVoter> queryNewsletterPollVoters(JidProvider newsletter, long serverMessageId, long limit, String voteHash);

    /**
     * Transfers ownership of the given newsletter to the supplied user.
     *
     * <p>Only the current owner may initiate the transfer. The target user
     * must already have a registered WhatsApp account. After the mutation
     * succeeds the original owner is demoted to admin and the supplied
     * user becomes the sole owner.
     *
     * <p>Dispatches the
     * {@link ChangeNewsletterOwnerMexRequest mexChangeNewsletterOwner}
     * MEX mutation. The relay echoes back the newsletter id on success;
     * Cobalt asserts that the echo arrived but does not surface it to
     * the caller — they already hold the JID they passed in.
     *
     * @param newsletter the non-{@code null} newsletter JID whose ownership
     *                   is being transferred
     * @param newOwner   the non-{@code null} JID of the user receiving
     *                   ownership
     * @throws NullPointerException          if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    void transferNewsletterOwnership(JidProvider newsletter, JidProvider newOwner);

    /**
     * Issues a newsletter administrator invitation to the given user.
     *
     * <p>The owner of a newsletter calls this mutation to invite another
     * user to become a co-administrator. The invitation is recorded
     * server-side and surfaces to the invitee through
     * {@link #queryNewsletterPendingInvites}; the invitee can then accept
     * via {@link #acceptNewsletterAdminInvite} or the inviter can revoke
     * via {@link #revokeNewsletterAdminInvite}.
     *
     * <p>Dispatches the
     * {@link CreateNewsletterAdminInviteMexRequest createNewsletterAdminInvite}
     * MEX mutation.
     *
     * @param newsletter the non-{@code null} newsletter JID whose admin
     *                   roster is being expanded
     * @param invitee    the non-{@code null} JID of the user being invited
     * @return the persisted invitation, carrying the invitee JID and the
     *         expiration instant; never {@code null}
     * @throws NullPointerException          if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    NewsletterAdminInvite createNewsletterAdminInvite(JidProvider newsletter, JidProvider invitee);

    /**
     * Attaches the legally-required paid-partnership disclosure label to a
     * newsletter message.
     *
     * <p>Monetised creators must disclose paid partnerships on sponsored
     * messages. This mutation flags an existing message so the server
     * renders the disclosure badge alongside it.
     *
     * <p>Dispatches the
     * {@link NewsletterAddPaidPartnershipLabelMexRequest mexNewsletterAddPaidPartnershipLabelJob}
     * MEX mutation. The relay echoes back the newsletter id on success;
     * Cobalt asserts that the echo arrived but does not surface it to
     * the caller — they already hold the JID they passed in.
     *
     * @param newsletter      the non-{@code null} newsletter JID hosting
     *                        the message being labelled
     * @param serverMessageId the non-{@code null} server-assigned message
     *                        id of the post being labelled
     * @throws NullPointerException          if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    void addNewsletterPaidPartnershipLabel(JidProvider newsletter, String serverMessageId);

    /**
     * Logs a batch of newsletter exposure events for attribution and
     * directory-ranking purposes.
     *
     * <p>While the user browses newsletters the client records lightweight
     * exposure entries (newsletter JID + capability flag) and flushes them
     * to the relay through this mutation. The backend uses the exposure
     * signal to improve directory ranking; the client treats the response
     * as a fire-and-forget acknowledgement.
     *
     * <p>Dispatches the
     * {@link LogNewsletterExposuresMexRequest mexLogNewsletterExposures}
     * MEX mutation.
     *
     * @param exposures the non-{@code null} batch of exposure entries; may
     *                  be empty, in which case the relay still records
     *                  the no-op call
     * @throws NullPointerException            if {@code exposures} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    void logNewsletterExposures(List<NewsletterExposure> exposures);

    /**
     * Files an appeal against an enforcement decision (for example a ban
     * or content removal) recorded on the given report.
     *
     * <p>The appeal flow is surfaced to newsletter admins and channel
     * owners after the server has taken an enforcement action. The reason
     * is the free-form justification entered by the user; the report id
     * identifies the underlying decision being contested.
     *
     * <p>Dispatches the {@link CreateReportAppealMexRequest createReportAppeal} MEX mutation.
     *
     * @param reason   the non-{@code null} free-form appeal justification
     * @param reportId the non-{@code null} identifier of the report whose
     *                 enforcement decision is being contested
     * @return the persisted appeal record; never {@code null}
     * @throws NullPointerException          if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload or
     *                                        omitted the appeal record
     */
    NewsletterReportAppeal createNewsletterReportAppeal(String reason, String reportId);

    /**
     * Queries the active enforcement actions recorded against the given
     * newsletter.
     *
     * <p>The response groups enforcements by category (profile-picture
     * deletions, suspensions, violating messages, geographic suspensions),
     * each carrying the violation metadata, the appeal state and the
     * localised explanatory copy. Newsletter admins use this query to
     * inspect pending or resolved enforcement actions taken on their
     * channel.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterEnforcementsMexRequest mexFetchNewsletterEnforcements}
     * MEX query.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param locale     the BCP-47 locale tag used to localise the
     *                   explanatory copy, or {@code null} to fall back to
     *                   the relay's default
     * @return the enforcements taken on this channel, flattened into a
     *         single list discriminated by
     *         {@link NewsletterEnforcement#category()}; never {@code null}
     * @throws NullPointerException          if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterEnforcement> queryNewsletterEnforcements(JidProvider newsletter, String locale);

    /**
     * Queries the list of moderation reports filed against newsletters
     * owned by this account.
     *
     * <p>This query supports the creator self-management surface where the
     * owner inspects which reports have been filed, their current status
     * and any associated appeal records. The relay returns every report
     * filed against newsletters this account has authority over — there is
     * no per-newsletter scoping variable.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterReportsMexRequest mexFetchNewsletterReports}
     * MEX query.
     *
     * @return the moderation reports filed against newsletters this
     *         account has authority over; never {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterReport> queryNewsletterReports();

    /**
     * Queries the analytics insights for the given newsletter.
     *
     * <p>Newsletter admins use this query to drive the analytics surface
     * that exposes per-metric values such as views, reactions and
     * follower-growth deltas. The metric identifiers follow the WA Web
     * naming scheme; passing {@code null} requests every metric the relay
     * is willing to expose to this caller.
     *
     * <p>Dispatches the
     * {@link FetchNewsletterInsightsMexRequest mexFetchNewsletterInsights}
     * MEX query.
     *
     * @param newsletter the non-{@code null} newsletter JID
     * @param metrics    the list of metric identifiers to fetch values
     *                   for, or {@code null} to omit the field and let
     *                   the relay pick the default metric set
     * @return the metrics reported by the relay, in server order; never
     *         {@code null}
     * @throws NullPointerException          if {@code newsletter} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload
     */
    List<NewsletterInsightMetric> queryNewsletterInsights(JidProvider newsletter, List<String> metrics);

    /**
     * Initiates a Data Subject Request (DSR) — the GDPR-mandated
     * download/deletion flow — for the given entity (typically a
     * newsletter the caller administers).
     *
     * <p>Despite being a "get info" operation the relay models this as a
     * GraphQL mutation because submitting the request has side effects
     * (it kicks off a backend export or deletion job). The response
     * carries the generated reference number so the caller can later
     * check progress through privacy-tooling surfaces.
     *
     * <p>Dispatches the {@link GetDsbInfoMexRequest mexGetDsbInfo} MEX mutation.
     *
     * @param entityId the non-{@code null} identifier of the entity whose
     *                 DSR is being initiated
     * @return the relay-assigned reference number; never {@code null}
     * @throws NullPointerException          if {@code entityId} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay returned no payload or
     *                                        omitted the reference number
     */
    String queryNewsletterDsbInfo(String entityId);

    /**
     * Queries the "about" status text of the given user.
     *
     * <p>The about status is the short biographical line (for example
     * {@code "At the movies"} or {@code "Busy"}) that a user sets in their
     * profile; it is distinct from the ephemeral text status shown on the
     * status tab.
     *
     * <p>Mirrors WA Web's {@code WAWebContactStatusBridge.getStatus} +
     * {@code WAWebGetAboutQueryJob.getAbout} dispatch, picking the canonical
     * transport at runtime so callers do not need to know which path was
     * used:
     * <ul>
     *   <li>If the target is a LID JID, or the
     *       {@code mex_usync_about_status} AB prop is enabled, the query
     *       routes through the USync MEX projection
     *       ({@code mexUsyncQuery({about_status:true, ...})}).</li>
     *   <li>Otherwise it routes through the MEX
     *       {@code mexGetAbout} GraphQL query.</li>
     * </ul>
     * The legacy direct {@code <iq xmlns="status">} stanza is retained as a
     * private fallback ({@link #queryAboutViaStatusIq(Jid)}) but is not used
     * in the current WA Web behaviour and is only invoked if neither modern
     * path is applicable.
     *
     * @param jid the user JID whose about text should be fetched
     * @return the about text when the server responds with a non-empty
     *         {@code <status>} element; {@link Optional#empty()} otherwise
     * @throws NullPointerException            if {@code jid} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<String> queryAbout(JidProvider jid);

    /**
     * Queries the WhatsApp username currently claimed by the authenticated
     * account.
     *
     * <p>Usernames are an alternative identifier introduced by WhatsApp to
     * complement phone numbers. WA Web's {@code mexGetUsernameQueryJob}
     * surfaces three fields on the {@code username_info} envelope: the
     * assigned {@code username}, the registration {@code state} (such as
     * {@code PENDING} or {@code ACTIVE}) and the {@code pin} hash used by
     * the recovery flow. The {@code state} and {@code pin} values are only
     * consumed internally by the WA Web sign-up surface, so this convenience
     * helper projects only the publicly meaningful field — the claimed
     * username — and delegates the auxiliary metadata to callers willing to
     * dispatch the typed {@link GetUsernameMexRequest} directly. The
     * username, when set, is also implicitly published to peers through the
     * standard MEX usync projection — see {@link #queryUserUsername(Jid)}
     * for fetching another user's username.
     *
     * <p>Dispatches the {@link GetUsernameMexRequest mexGetUsernameQueryJob}
     * MEX query.
     *
     * @return the claimed username, or {@link Optional#empty()} when the
     *         account has no username set or the relay returned no payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    Optional<String> queryUsername();

    /**
     * Claims, updates or reserves the WhatsApp username for the
     * authenticated account.
     *
     * <p>Setting a non-empty username assigns it to the account; the relay
     * answers with the {@code xwa2_username_set.result} status token, which
     * is reported as {@code "SUCCESS"} on a successful mutation. Setting an
     * empty or {@code null} input clears the entire variables payload —
     * mirroring WA Web's {@code isStringNullOrEmpty(t.input) ? {} : t}
     * short-circuit — and lets the relay decide how to react (the JS source
     * uses this to issue the mutation as a probe without committing a
     * username). The boolean projection mirrors WA Web's
     * {@code mexSetUsernameQueryJob} return value
     * ({@code result === "SUCCESS"}).
     *
     * <p>Dispatches the {@link SetUsernameMexRequest mexSetUsernameQueryJob}
     * MEX mutation.
     *
     * @param username the candidate username to claim, or {@code null} /
     *                 empty to forward an empty variables payload
     * @return {@code true} when the relay reports {@code "SUCCESS"},
     *         {@code false} when it answers with any other status token or
     *         no payload at all
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    boolean editUsername(String username);

    /**
     * Sets or rotates the recovery PIN attached to the WhatsApp username
     * claimed by the authenticated account.
     *
     * <p>The recovery PIN backs the username so the account can be regained
     * if the registered phone number becomes unavailable. WhatsApp's
     * settings UI rotates this PIN through the
     * {@code Account -> Username PIN} flow; callers that want to mirror
     * the same affordance dispatch this mutation with the new PIN string.
     * The relay answers with the {@code xwa2_username_pin_set.result}
     * status token; WA Web's {@code mexSetUsernameKeyQueryJob} returns
     * {@code result === "SUCCESS"}, and Cobalt mirrors that check by
     * throwing on any non-success answer.
     *
     * <p>Dispatches the
     * {@link SetUsernameKeyMexRequest mexSetUsernameKeyQueryJob} MEX
     * mutation.
     *
     * @param pin the new recovery PIN to register; {@code null} forwards an
     *            empty {@code variables} object so the relay clears the PIN
     * @throws WhatsAppServerRuntimeException  if the relay rejects the
     *                                         mutation, returns no payload
     *                                         or replies with any token
     *                                         other than {@code "SUCCESS"}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    void editUsernameRecoveryKey(String pin);

    /**
     * Checks whether the given candidate username is available for
     * registration on the WhatsApp relay.
     *
     * <p>This is the live-validation probe that powers the username picker
     * UI: WA Web's {@code mexCheckUsernameAvailabilityQueryJob} project two
     * fields onto the {@code xwa2_username_check} envelope —
     * {@code isUsernameAvailable: result === "SUCCESS"} and an array of
     * suggested alternatives generated when the candidate is taken or
     * invalid. This convenience helper mirrors the boolean projection;
     * callers that need the suggestions list can dispatch the typed
     * {@link UsernameAvailabilityMexRequest} directly and inspect
     * {@link UsernameAvailabilityMexResponse#suggestedUsernames()}.
     *
     * <p>Dispatches the
     * {@link UsernameAvailabilityMexRequest mexCheckUsernameAvailabilityQueryJob}
     * MEX query.
     *
     * @param candidate the candidate username to validate; {@code null}
     *                  forwards an empty {@code variables} object,
     *                  matching the WA Web behaviour when the input field
     *                  is empty
     * @return {@code true} when the relay reports the candidate as
     *         available, {@code false} when it is rejected or the relay
     *         returned no payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    boolean checkUsernameAvailability(String candidate);

    /**
     * Publishes the authenticated user's ephemeral text status (the short
     * "About"-line decoration shown next to the contact's name) and clears
     * any existing entry when called with empty arguments.
     *
     * <p>The text status is the short message — optionally accompanied by
     * an emoji — that the user broadcasts as their current status. The
     * status expires automatically after {@code ephemeralDuration} elapses;
     * passing a {@code null} or {@link Duration#ZERO} duration disables
     * the auto-expiry.
     *
     * <p>Calling with both {@code text} and {@code emoji} {@code null} (or
     * the text empty) clears the published status entirely; the duration
     * is silently coerced to zero in that case to mirror the WA Web
     * normalisation in
     * {@code WAWebTextStatusParseUtils.createTextStatusObjectForUpdateRequest}.
     * The relay answers with the {@code xwa2_update_text_status.result}
     * status token; this method throws on any non-success answer to mirror
     * WA Web's binary success-vs-failure surface.
     *
     * <p>Dispatches the
     * {@link UpdateTextStatusMexRequest mexUpdateTextStatus} MEX mutation.
     *
     * @param text              the status body, or {@code null} / empty to
     *                          clear the existing status
     * @param emoji             the optional emoji decoration, or
     *                          {@code null} to publish without an emoji
     * @param ephemeralDuration the auto-expiry duration; {@code null} or
     *                          {@link Duration#ZERO} disables auto-expiry.
     *                          Must not be negative.
     * @throws IllegalArgumentException        if {@code ephemeralDuration}
     *                                         is negative
     * @throws WhatsAppServerRuntimeException  if the relay rejects the
     *                                         mutation, returns no payload
     *                                         or replies with any token
     *                                         other than {@code "SUCCESS"}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    void editTextStatus(String text, String emoji, Duration ephemeralDuration);

    /**
     * Fetches the ephemeral text-status entries published by one or more
     * users on the WhatsApp relay.
     *
     * <p>Each entry on the relay's {@code xwa2_text_status_list} envelope is
     * projected onto a {@link ContactTextStatus} carrying the status text,
     * the optional emoji, the author-relative last-update timestamp and the
     * ephemeral duration so callers can render the same expiry countdown
     * WA Web shows on the status surface. Authors that have not published
     * any text status are omitted from the result map.
     *
     * <p>Dispatches the
     * {@link FetchTextStatusListMexRequest WAWebMexFetchTextStatusListJobQuery}
     * MEX query. WA Web only ever batches a single user per call; this
     * overload preserves the wire-level batch shape so callers can amortise
     * the round-trip when querying a contact roster.
     *
     * @param users the user JIDs whose text status should be fetched;
     *              must not be {@code null} or contain {@code null} entries
     * @return a {@link Map} from queried JID to its parsed text status,
     *         never {@code null} and empty when the relay returned no
     *         payload or none of the queried users has a status set
     * @throws NullPointerException            if {@code users} is
     *                                         {@code null} or contains a
     *                                         {@code null} entry
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    Map<Jid, ContactTextStatus> queryUserTextStatuses(List<? extends JidProvider> users);

    /**
     * Retrieves the most recent linked-identity (LID) change for the
     * authenticated account, returning the previous and current identifier
     * pair.
     *
     * <p>LID is WhatsApp's non-phone account identifier used during the
     * LID migration rollout. When the server rotates an account's LID,
     * clients reconcile local storage by issuing this query to learn the
     * old-to-new mapping so chat references can be updated without losing
     * history.
     *
     * <p>Dispatches the
     * {@link LidChangeNotificationMexRequest parseLidChangeNotification}
     * MEX query.
     *
     * @return an {@link Optional} containing the parsed
     *         {@link LidChange} rotation pair, or empty if the relay
     *         returned no payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<LidChange> queryLidChangeNotification();

    /**
     * Queries the current OHAI (Oblivious HTTP Authentication for
     * Initiation) key configuration list issued by the WhatsApp relay.
     *
     * <p>OHAI is the HPKE key bundle used to encapsulate ACS (Account
     * Centre Service) requests sent by the OHAI client. The relay rotates
     * the key set periodically and clients are expected to refetch the
     * configuration when their cached value expires.
     *
     * <p>Dispatches the
     * {@link FetchOHAIKeyConfigMexRequest mexFetchOHAIKeyConfig} MEX
     * query.
     *
     * @return an unmodifiable list of {@link OhaiKeyConfig} entries
     *         advertised by the relay, empty when the relay returned no
     *         payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    List<OhaiKeyConfig> queryOhaiKeyConfig();

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
     */
    List<BusinessCatalogEntry> queryBusinessCatalog(JidProvider businessJid);

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
     */
    List<BusinessCatalogEntry> queryBusinessCatalog(JidProvider businessJid, int limit);

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
     */
    List<BusinessCatalog> queryBusinessCollections(JidProvider businessJid);

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
     * should instantiate {@link QueryProductCollectionsMexRequest}
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
     */
    List<BusinessCatalog> queryBusinessCollections(JidProvider businessJid, int limit);

    /**
     * Asks a WhatsApp Business catalog whether it can fulfil orders to a
     * given postcode.
     *
     * <p>Some merchants restrict shipping or service area to specific
     * postcodes — a food-delivery business might only operate in certain
     * neighbourhoods, a courier might decline rural pickups. WhatsApp's
     * cart UI calls this entry point before letting the buyer commit to a
     * cart so the user sees an "out of service area" hint immediately
     * rather than after placing the order. A Cobalt-driven storefront
     * mirrors that pre-flight check by passing the buyer's postcode
     * (encrypted into the direct-connection envelope using the merchant's
     * direct-connection key) and consuming the verdict.
     *
     * <p>The verdict is one of {@link BusinessPostcodeVerificationResult#SUCCESS},
     * {@link BusinessPostcodeVerificationResult#INVALID_POSTCODE} or
     * {@link BusinessPostcodeVerificationResult#UNSERVICEABLE_LOCATION}; the
     * accompanying optional {@code encryptedLocationName} carries the
     * resolved area name (encrypted under the same direct-connection key)
     * that callers can decrypt and surface as "Delivers to <area>" in the
     * cart UI.
     *
     * @param businessJid                   the JID of the business
     *                                      whose service area is being
     *                                      tested; never {@code null}
     * @param directConnectionEncryptedInfo the opaque direct-connection
     *                                      envelope carrying the
     *                                      buyer-side postcode; never
     *                                      {@code null}
     * @return the parsed verification verdict and the optional
     *         encrypted location name; never {@code null}
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
     * catalog and returns the authoritative cart total.
     *
     * <p>WhatsApp shopping carts are assembled client-side: the user adds
     * line items from the catalog browser and the cart's local view tracks
     * each line's price as it was at add time. Before letting the user
     * place the order, WhatsApp's UI re-runs the cart through this
     * endpoint so prices, stock counts, sale-price windows and product
     * availability all reflect the merchant's current state — protecting
     * both sides from stale-cart confusion when the merchant edits the
     * catalog between cart assembly and checkout. Cobalt-driven cart UIs
     * should call this immediately before the "place order" action and
     * render the freshly-priced total back to the user for confirmation.
     *
     * <p>The response includes the updated cart-wide total and one
     * per-line entry with the rebuilt name, price, currency, media,
     * remaining stock count and any active sale-price window — enough to
     * fully re-render the cart without a separate catalog fetch. Lines
     * whose product was removed from the catalog will surface their
     * server-side status code (e.g. unavailable) so the UI can grey them
     * out.
     *
     * @param bizJid                        the JID of the business
     *                                      whose cart is being
     *                                      refreshed; never {@code null}
     * @param productIds                    the product ids on the cart
     *                                      to reprice in server-side
     *                                      order; never {@code null},
     *                                      must be non-empty
     * @param width                         the desired thumbnail width
     *                                      in pixels
     * @param height                        the desired thumbnail height
     *                                      in pixels
     * @param directConnectionEncryptedInfo the opaque direct-connection
     *                                      envelope used by merchants
     *                                      enrolled in the encrypted
     *                                      direct-connection feature;
     *                                      pass {@code null} when the
     *                                      merchant does not require
     *                                      one
     * @return the freshly-rebuilt cart, with up-to-date prices and
     *         availability for every line; never {@code null}
     * @throws NullPointerException            if any non-nullable
     *                                         parameter is {@code null}
     * @throws IllegalArgumentException        if {@code productIds} is
     *                                         empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant
     */
    BusinessRefreshedCart refreshBusinessCart(BusinessCartRefresh refresh);

    /**
     * Resolves a click-to-WhatsApp (CTWA) deep link into the originating
     * ad's metadata.
     *
     * <p>"Click-to-WhatsApp" is the Meta-side ad placement that lets
     * Facebook / Instagram users tap an ad and immediately open a chat
     * with the advertised business. The chat is opened with a system
     * "context" message that quotes the ad — its headline, body, image or
     * video thumbnail, originating page — so the merchant knows exactly
     * which ad drove the conversation. WhatsApp populates that context by
     * hitting this endpoint once with the invite code embedded in the deep
     * link; a Cobalt-driven business client must do the same before
     * inserting the ad-context bubble at the top of a brand-new chat or
     * before stamping the next outgoing message's
     * {@link ContextInfo#externalAdReply()} field.
     *
     * <p>The {@code expectedSourceUrl} acts as a confirmation that the
     * caller really did follow the deep link from the same source that
     * issued the invite — it lets the server reject replays where the
     * invite code was lifted out of context and re-used elsewhere. The
     * returned record carries every ad-side field WhatsApp surfaces in
     * the in-app context bubble: thumbnail bytes (or a CDN URL), the ad
     * headline / body, video URL when the ad was a video, source-app
     * identifier and the WAMO-AGM greeting / payload / image fields used
     * by AGM-enrolled advertisers.
     *
     * @param businessJid       the JID of the business advertised in the
     *                          ad; never {@code null}
     * @param inviteCode        the invite code embedded in the
     *                          click-to-WhatsApp deep link; never
     *                          {@code null}
     * @param expectedSourceUrl the deep-link URL the client landed on;
     *                          replayed back to the server so it can
     *                          reject mismatched invocations; never
     *                          {@code null}
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
     * @return the blocked JIDs in server-returned order
     */
    SequencedCollection<Jid> queryBlockList();

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
     * @param contact the contact to block
     * @throws NullPointerException           if {@code contact} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay rejected the request
     */
    void blockContact(JidProvider contact);

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
     * @param contact the contact to unblock
     * @throws NullPointerException           if {@code contact} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay rejected the request
     */
    void unblockContact(JidProvider contact);

    /**
     * Fetches the local user's block list using the delta-fetch fast
     * path.
     *
     * <p>Sends {@code <iq xmlns="blocklist" type="get">} with the
     * supplied digest of the cached block list, mirroring WA Web's
     * {@code WASmaxBlocklistsGetBlockListRPC.sendGetBlockListRPC}.
     * When the relay's current digest matches the supplied one it
     * answers with the cache-hit shape and Cobalt returns
     * {@link BlockListResult.Unchanged}; otherwise the relay returns
     * the fresh list and Cobalt returns
     * {@link BlockListResult.Updated} carrying the JIDs and the new
     * digest the caller should remember for the next round-trip.
     *
     * <p>Pass {@code null} as {@code itemDhash} on the very first
     * fetch — the relay treats the missing digest as a cache miss
     * and answers with the full list.
     *
     * @param itemDhash the cached list digest, or {@code null} on the
     *                  first fetch
     * @return the {@link BlockListResult} describing the outcome;
     *         never {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected
     *                                         the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    BlockListResult queryBlockList(String itemDhash);

    /**
     * Fetches the local user's marketing-message opt-out list for the
     * given category using the delta-fetch fast path.
     *
     * <p>Sends {@code <iq xmlns="optoutlist" type="get" category="...">}
     * with the supplied digest of the cached opt-out list, mirroring
     * WA Web's
     * {@code WASmaxBlocklistsGetOptOutListRPC.sendGetOptOutListRPC}.
     * When the relay's current digest matches the supplied one it
     * answers with the cache-hit shape and Cobalt returns
     * {@link OptOutListResult.Unchanged}; otherwise the relay returns
     * the fresh list and Cobalt returns
     * {@link OptOutListResult.Updated}.
     *
     * @param itemDhash  the cached digest, or {@code null} on the first
     *                   fetch
     * @param iqCategory the category code; never {@code null}
     * @return the {@link OptOutListResult} describing the outcome;
     *         never {@code null}
     * @throws NullPointerException            if {@code iqCategory} is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    OptOutListResult queryOptOutList(String itemDhash, String iqCategory);

    /**
     * Fetches the per-category contact blacklist for the supplied
     * addressing mode.
     *
     * <p>Sends {@code <iq xmlns="privacy" type="get"><privacy
     * addressing_mode="..."><list value="contact_blacklist"
     * name="..."/></privacy></iq>} mirroring WA Web's
     * {@code WASmaxPrivacyGetContactBlacklistRPC.sendGetContactBlacklistRPC}.
     * The category name is one of the privacy axes ({@code "last"},
     * {@code "profile"}, {@code "status"}, {@code "online"}, etc.),
     * and the addressing mode selects between the legacy
     * phone-number form and the modern lid form.
     *
     * <p>When the relay omits the inner blacklist payload Cobalt
     * returns {@link ContactBlacklistResult.Unchanged}; otherwise it
     * returns {@link ContactBlacklistResult.Updated} carrying the
     * fresh list of blocked JIDs, the new digest, and the addressing
     * mode the relay used to encode the entries.
     *
     * @param categoryName   the privacy category name; never
     *                       {@code null}
     * @param addressingMode the JID addressing mode; never
     *                       {@code null}
     * @return the {@link ContactBlacklistResult} describing the
     *         outcome; never {@code null}
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay returned
     *                                         an error response
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    ContactBlacklistResult queryContactBlacklist(String categoryName, ContactBlacklistAddressingMode addressingMode);

    /**
     * Adds or removes a contact on the local user's marketing-message
     * opt-out list.
     *
     * <p>Sends {@code <iq xmlns="optoutlist" type="set"><item
     * jid="..." category="..." action="..."/></iq>} mirroring WA
     * Web's
     * {@code WASmaxBlocklistsUpdateOptOutListRPC.sendUpdateOptOutListRPC}.
     * The marketing-messaging counterpart of the higher-level
     * {@link #blockContact(Jid)} / {@link #unblockContact(Jid)}
     * helpers — the relay records the requested membership change
     * along with the supplied analytics metadata
     * ({@code reason} / {@code entry_point} / {@code signup_id}) and
     * the optional opt-out duration override.
     *
     * <p>The method returns {@code void}: the wire-level
     * {@link SmaxUpdateOptOutListResponse} success variants merely
     * confirm the change and carry no payload Cobalt currently
     * surfaces. An error response is escalated as a
     * {@link WhatsAppServerRuntimeException}.
     *
     * @param itemJid        the target business JID; never
     *                       {@code null}
     * @param itemCategory   the category code; never {@code null}
     * @param itemAction     the add / remove action code; never
     *                       {@code null}
     * @param itemDhash      the optional cached digest; may be
     *                       {@code null}
     * @param itemReason     the optional reason; may be {@code null}
     * @param itemEntryPoint the optional entry-point; may be
     *                       {@code null}
     * @param itemSignupId   the optional signup id; may be
     *                       {@code null}
     * @param itemDuration   the optional duration override; may be
     *                       {@code null}
     * @throws NullPointerException            if {@code itemJid},
     *                                         {@code itemCategory} or
     *                                         {@code itemAction} is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected
     *                                         the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void updateOptOutList(OptOutListUpdate update);

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
     * @param jid the JID whose picture URL should be fetched
     * @return the picture URL when present; {@link Optional#empty()}
     *         otherwise (including when the server returns no
     *         {@code <picture>} child, matching WA Web's HTTP 404 branch)
     * @throws NullPointerException            if {@code jid} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<URI> queryPicture(JidProvider jid);

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
     * {@link PushNameSettingHandler#applyMutation apply} pipeline
     * persists the new name into {@link WhatsAppStore#setName(String)} and
     * notifies listeners.
     * @param newPushName the new broadcast display name; must not be
     *                    {@code null}
     * @throws NullPointerException            if {@code newPushName} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editName(String newPushName);

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
     * @param aboutText the new about text; must not be {@code null}
     * @throws NullPointerException            if {@code aboutText} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editAbout(String aboutText);

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
     * @param jpegBytes the full-size JPEG payload; must not be {@code null}
     * @throws NullPointerException            if {@code jpegBytes} is {@code null}
     * @throws IllegalArgumentException        if {@code jpegBytes} is not a
     *                                         valid image
     * @throws IllegalStateException           if the self JID is not known
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editProfilePicture(byte[] jpegBytes);

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
     * server-driven notification, matching the behaviour of the group
     * picture-clearing path in {@link #editGroupMetadata(GroupMetadataEdit)}.
     * @throws IllegalStateException           if the self JID is not known
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void removeProfilePicture();

    /**
     * Broadcasts this client's own presence state to the server using
     * the local user's push name from {@link WhatsAppStore#name()}.
     *
     * <p>Convenience for {@link #editPresence(ContactStatus, String)}
     * that supplies the store-resident push name as the
     * {@code presenceName} argument.
     *
     * @param status {@link ContactStatus#AVAILABLE} or {@link ContactStatus#UNAVAILABLE}
     * @throws NullPointerException            if {@code status} is {@code null}
     * @throws IllegalArgumentException        if {@code status} is not
     *                                         {@code AVAILABLE} or {@code UNAVAILABLE}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void editPresence(ContactStatus status);

    /**
     * Broadcasts this client's own presence state to the server with an
     * explicit display-name override.
     *
     * <p>Emits a {@code <presence type="available"/>} stanza when
     * {@code status} is {@link ContactStatus#AVAILABLE} or a
     * {@code <presence type="unavailable"/>} stanza when {@code status}
     * is {@link ContactStatus#UNAVAILABLE}; both carry the supplied push
     * name (when non-{@code null}) so the server can forward it to peers.
     *
     * <p>On WA Web the module exposes this behavior as two parameterless
     * bridge functions ({@code setPresenceAvailable} /
     * {@code setPresenceUnavailable}) that delegate to
     * {@code WASendPresenceStatusProtocol.sendPresenceStatusProtocol},
     * which itself forwards into
     * {@code WASmaxPresenceAvailabilityRPC.sendAvailabilityRPC}. Cobalt
     * collapses the three layers into this single entry point because
     * the underlying stanza shape is identical and the destructure-and-
     * rename is idiomatically expressed as plain method parameters in
     * Java.
     *
     * @param status       {@link ContactStatus#AVAILABLE} or
     *                     {@link ContactStatus#UNAVAILABLE}
     * @param presenceName optional display name override; may be {@code null}
     * @throws NullPointerException            if {@code status} is {@code null}
     * @throws IllegalArgumentException        if {@code status} is not
     *                                         {@code AVAILABLE} or {@code UNAVAILABLE}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void editPresence(ContactStatus status, String presenceName);

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
     * @param chat  the chat JID the state applies to
     * @param state {@link ContactStatus#COMPOSING}, {@link ContactStatus#RECORDING}
     *              or {@link ContactStatus#UNAVAILABLE} (mapped to paused)
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if {@code state} is not one of
     *                                         {@code COMPOSING}, {@code RECORDING}
     *                                         or {@code UNAVAILABLE}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void editChatState(JidProvider chat, ContactStatus state);

    /**
     * Subscribes to real-time presence updates for the given contact,
     * using a bare subscribe request with no display-name or context
     * scope.
     *
     * <p>Convenience for {@link #subscribeToPresence(Jid, String, Jid)}
     * that supplies {@code null} for both the display-name override and
     * the context scope. The server will subsequently push
     * {@code <presence>} notifications for the target until the
     * subscription is cancelled via
     * {@link #unsubscribeFromPresence(Jid)} or the socket is closed.
     *
     * @param target the contact JID to subscribe to
     * @throws NullPointerException            if {@code target} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void subscribeToPresence(JidProvider target);

    /**
     * Subscribes to real-time presence updates for the given target,
     * optionally carrying a display-name override and an explicit
     * context JID for community / group-scoped subscriptions.
     *
     * <p>Emits a {@code <presence type="subscribe" to="<presenceTo>"/>}
     * stanza. WA Web additionally threads a privacy-token payload
     * ({@code tCTokenMixinArgs}) through the subscribe request when the
     * target is a user JID; Cobalt omits that mixin because privacy
     * tokens are managed by a separate store/flow.
     *
     * @param presenceTo      the non-{@code null} JID to subscribe to
     * @param presenceName    optional display name; may be {@code null}
     * @param presenceContext optional context JID for community / group
     *                        surfaces; may be {@code null} for the
     *                        default 1:1 chat scope
     * @throws NullPointerException            if {@code presenceTo} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void subscribeToPresence(JidProvider presenceTo, String presenceName, JidProvider presenceContext);

    /**
     * Cancels a presence subscription previously established via
     * {@link #subscribeToPresence(Jid)}.
     *
     * <p>Emits a {@code <presence type="unsubscribe" to="<targetJid>"/>} stanza,
     * the symmetric counterpart of the subscribe request. After the server
     * acknowledges the unsubscribe, no further presence push notifications
     * will arrive for the given contact until a new subscription is created.
     * @param target the contact JID to unsubscribe from
     * @throws NullPointerException            if {@code target} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket has been closed
     */
    void unsubscribeFromPresence(JidProvider target);

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
     */
    AckResult sendMessage(JidProvider jid, MessageContainer container);

    /**
     * Sends a pre-built {@link MessageInfo} without re-running the
     * {@code MessagePreparer} pipeline.
     *
     * <p>Use this overload when the caller has assembled a
     * {@link ChatMessageInfo} or {@link NewsletterMessageInfo}
     * with a message id, timestamp, and any extension metadata already
     * populated (for example when rehydrating a draft or re-transmitting
     * a message that failed a previous send).
     *
     * @param messageInfo the fully-populated outgoing message
     * @return the server ack result
     * @throws NullPointerException                               if {@code messageInfo} is {@code null}
     * @throws WhatsAppMessageException.Send.InvalidRecipient     if the JID does not match a supported chat type
     */
    AckResult sendMessage(MessageInfo messageInfo);

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
     */
    AckResult editMessage(MessageKey originalKey, MessageContainer newContent);

    /**
     * Deletes a message, either locally ("delete for me") or for every
     * participant in the chat ("delete for everyone"), depending on the
     * {@code everyone} flag.
     *
     * <p>When {@code everyone} is {@code false}, this matches
     * {@code WAWebChatSendMessages.sendDeleteMsgs} in WA Web: the
     * referenced message is removed from the local store and a
     * {@link DeleteMessageForMeAction}
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
     */
    AckResult deleteMessage(MessageKey key, boolean everyone);

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
     */
    ChatMessageInfo sendStatus(MessageContainer content);

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
     */
    AckResult deleteStatus(String statusId);

    /**
     * Emits a {@code read} receipt for a viewed status update.
     *
     * <p>Status read receipts are delivered to {@code status@broadcast}
     * with the original status author attached as the {@code participant}
     * attribute, mirroring WhatsApp Web's
     * {@code WAWebStatusReceipt.sendStatusMsgRead} flow.
     *
     * @param statusId the id of the viewed status message; the author JID
     *                 is derived from the cached status message's sender
     * @throws NullPointerException   if {@code statusId} is {@code null}
     * @throws NoSuchElementException if no status with the given id is
     *                                cached or it has no sender JID
     */
    void markStatusViewed(String statusId);

    /**
     * Queries the server for the current Status privacy configuration.
     *
     * <p>The IQ is sent with {@code xmlns="status"} and a single
     * {@code <privacy/>} child addressed to {@code s.whatsapp.net}. The
     * server responds with a {@code <privacy>} element containing the
     * selected mode and any paired JID list.
     *
     * @return the current status privacy setting, never {@code null}
     */
    StatusPrivacySetting queryStatusPrivacy();

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
     */
    void editStatusPrivacy(StatusPrivacyMode mode, Collection<? extends JidProvider> jids);

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
     */
    AckResult forwardMessage(MessageKey sourceKey, JidProvider destination);

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
     */
    void forwardMessages(Collection<MessageKey> sourceKeys, Collection<? extends JidProvider> destinations);

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
     */
    AckResult addReaction(MessageKey messageKey, String emoji);

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
     */
    AckResult removeReaction(MessageKey messageKey);

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
     */
    void starMessage(MessageKey key);

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
     */
    void unstarMessage(MessageKey key);

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
     */
    void archiveChat(JidProvider chat, boolean archive);

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
     */
    void pinChat(JidProvider chat, boolean pin);

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
     */
    void muteChat(JidProvider chat, Instant muteUntil);

    /**
     * Unmutes a chat.
     *
     * <p>Convenience wrapper around {@link #muteChat(Jid, Instant)} with a
     * {@code null} {@code muteUntil}, which emits a sync mutation with
     * {@code muted = false} and {@code muteEndTimestamp = 0}.
     *
     * @param chat the JID of the chat to unmute
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void unmuteChat(JidProvider chat);

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
     */
    void markChatAsRead(JidProvider chat);

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
     */
    void markChatAsUnread(JidProvider chat);

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
     */
    void clearChat(JidProvider chat, boolean keepStarred);

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
     */
    Optional<Chat> deleteChat(JidProvider chat);

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
     */
    void lockChat(JidProvider chat);

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
     */
    void unlockChat(JidProvider chat);

    /**
     * Creates a new chat label with the given name and palette colour index.
     *
     * <p>Per WhatsApp Web {@code WAWebBizLabelEditingAction.labelAddAction}:
     * allocates the next label id via
     * {@code WAWebDBLabelDatabaseApi.getNextLabelId}, maps the name to a
     * predefined-id when applicable (via
     * {@link BusinessLabelConstants#mapLabelNameToPredefinedId(String)}), and
     * issues a {@code getLabelMutation} with {@code deleted=false} and
     * {@code type=CUSTOM}. The id is computed as one above the highest
     * existing numeric label id (starting at {@code 1}), matching WA Web's
     * client-side {@code getNextLabelId} which does the same {@code max +
     * parseInt} scan over the label table.
     *
     * @param name       the user-visible display name of the label
     * @param colorIndex the palette colour index
     * @return the newly-allocated label id (stringified integer)
     * @throws NullPointerException if {@code name} is {@code null}
     */
    String createLabel(String name, int colorIndex);

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
     */
    Optional<Label> editLabel(String labelId, String name, int colorIndex);

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
     */
    Optional<Label> deleteLabel(String labelId);

    /**
     * Convenience overload of {@link #deleteLabel(String)} that extracts
     * the identifier from the given {@link Label}.
     *
     * @param label the label to delete
     * @throws NullPointerException if {@code label} is {@code null}
     */
    Optional<Label> deleteLabel(Label label);

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
     */
    void reorderLabels(List<String> labelIds);

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
     */
    void associateLabel(String labelId, JidProvider chat);

    /**
     * Convenience overload of {@link #associateLabel(String, JidProvider)}
     * that extracts the label id from the given {@link Label}.
     *
     * @param label the label
     * @param chat  the chat JID to tag with the label
     * @throws NullPointerException if any argument is {@code null}
     */
    void associateLabel(Label label, JidProvider chat);

    /**
     * Dissociates a label from the given chat.
     *
     * <p>Counterpart to {@link #associateLabel(String, Jid)}: emits a
     * {@link LabelAssociationAction} with {@code labeled=false}.
     *
     * @param labelId the label identifier
     * @param chat    the chat JID to untag
     * @throws NullPointerException if any argument is {@code null}
     */
    void dissociateLabel(String labelId, JidProvider chat);

    /**
     * Convenience overload of {@link #dissociateLabel(String, JidProvider)}
     * that extracts the label id from the given {@link Label}.
     *
     * @param label the label
     * @param chat  the chat JID to untag
     * @throws NullPointerException if any argument is {@code null}
     */
    void dissociateLabel(Label label, JidProvider chat);

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
     */
    void associateLabel(String labelId, MessageKey messageKey);

    /**
     * Convenience overload of {@link #associateLabel(String, MessageKey)}
     * that extracts the label id from the given {@link Label}.
     *
     * @param label      the label
     * @param messageKey the target message key
     * @throws NullPointerException if any argument is {@code null}
     */
    void associateLabel(Label label, MessageKey messageKey);

    /**
     * Dissociates a label from a specific message.
     *
     * @param labelId    the label identifier
     * @param messageKey the target message key
     * @throws NullPointerException if any argument is {@code null}
     */
    void dissociateLabel(String labelId, MessageKey messageKey);

    /**
     * Convenience overload of {@link #dissociateLabel(String, MessageKey)}
     * that extracts the label id from the given {@link Label}.
     *
     * @param label      the label
     * @param messageKey the target message key
     * @throws NullPointerException if any argument is {@code null}
     */
    void dissociateLabel(Label label, MessageKey messageKey);

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
     */
    Jid createBroadcastList(String name, Collection<? extends JidProvider> recipients);

    /**
     * Renames a broadcast list and/or replaces its recipients.
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
     * <p>Per WhatsApp Web {@code WAWebBroadcastListSync.getDeleteBroadcastListMutation}:
     * emits a REMOVE mutation under the {@code REGULAR} collection keyed by
     * the list id, then clears the entry from the store.
     *
     * @param broadcastListId the broadcast list JID
     * @throws NullPointerException if {@code broadcastListId} is {@code null}
     */
    void deleteBroadcastList(JidProvider broadcastListId);

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
     */
    ChatMessageInfo sendBroadcast(JidProvider broadcastListId, MessageContainer message);

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
     */
    void assignChatToAgent(JidProvider chat, String agentId);

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
     */
    void unassignChatFromAgent(JidProvider chat);

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
     */
    void editChatAssignmentOpenedStatus(JidProvider chat, boolean opened);

    /**
     * Reads the account-wide default disappearing-message timer that new
     * chats inherit when they are first opened.
     *
     * <p>WhatsApp lets each account configure a default expiry that is
     * applied to every brand-new conversation opened from this device; the
     * value matches the "Default message timer" entry in the WhatsApp Web
     * privacy panel and is mirrored across all linked devices. The
     * accompanying timestamp records when the user last changed the setting
     * and is useful for ordering local UI against incoming sync mutations.
     *
     * <p>Applications typically call this once after login to seed their
     * "new chat" UI, or to confirm that {@link #editDefaultDisappearingMode}
     * actually took effect on the server.
     *
     * @return the current default timer paired with the unix-second
     *         timestamp it was last changed at; never {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws NoSuchElementException          if the server reply contains
     *                                         no disappearing-mode entry
     */
    AccountDisappearingMode queryDisappearingMode();

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
     */
    void editEphemeralTimer(JidProvider chat, ChatEphemeralTimer timer);

    /**
     * Asks the server which Terms-of-Service / disclosure notices the
     * current account has accepted.
     *
     * <p>WhatsApp shows occasional acceptance prompts (e.g. updated ToS,
     * business-bot disclosures, broadcast / newsletter producer
     * disclosures) before unlocking the matching feature. The server
     * tracks whether each notice id has been acknowledged; this method
     * returns the latest verdict for every id the caller asks about, so
     * applications can decide whether to surface a prompt before allowing
     * the user to use a gated feature.
     *
     * <p>The returned {@link TosNotices#refresh()} value is the server's
     * recommended re-poll cadence — applications that keep a long-running
     * session typically schedule the next call after that interval
     * elapses. The interval is clamped to a sane range (two hours to
     * three days) with a one-day fallback when the server reply is out of
     * range.
     *
     * @param noticeIds the disclosure / ToS identifiers to inspect; never
     *                  {@code null} but possibly empty (an empty
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
     * <p>Through the WhatsApp Settings UI, users can request a downloadable
     * report of their account or newsletter data; the server queues the
     * job and emails a download link when ready. Once submitted, the user
     * can call this entry point to abort the pending request — useful when
     * an application surfaces a "Cancel data export" affordance, or
     * automatically reverts a request that was issued in error.
     *
     * <p>The call is fire-and-forget: the server acknowledges the
     * cancellation but returns no body, so applications observing the
     * cancellation success have to re-query the GDPR status separately.
     *
     * @param reportType which export to cancel —
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
     * <p>WhatsApp Web's PWA push-notification flow encrypts the
     * Service-Worker push subscription endpoint and auth secrets with
     * this key before uploading them so the server can deliver
     * background pushes without seeing the raw browser-side credentials.
     * Applications wiring up their own browser-style push delivery — or
     * mirroring WA Web's PWA install flow — call this once per session
     * to retrieve the key, then feed it into the encryption step before
     * registering the subscription.
     *
     * <p>Most non-browser embeddings of Cobalt do not need to call this
     * method; it only matters for clients that actually deliver pushes
     * through the WA Web push pipeline.
     *
     * @return the base-64 encoded public key when the server published
     *         one; {@link Optional#empty()} when the server returned an
     *         error (an installed
     *         {@link WhatsAppClientErrorHandler} can recover the
     *         underlying error code if richer diagnostics are needed)
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    Optional<String> queryPushServerKey();

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
     */
    Map<PrivacySettingType, PrivacySettingValue> queryPrivacySettings();

    /**
     * Changes a single privacy setting on the local account without touching
     * the per-contact allow/block list.
     *
     * <p>Equivalent to
     * {@link #editPrivacySetting(PrivacySettingType, PrivacySettingValue, Collection)}
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
     */
    void editPrivacySetting(PrivacySettingType type, PrivacySettingValue value);

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
     */
    void editPrivacySetting(PrivacySettingType type, PrivacySettingValue value, Collection<? extends JidProvider> excludedOrIncluded);

    /**
     * Toggles whether the local account sends "read" receipts (the blue
     * double-ticks) to peers.
     *
     * <p>Read receipts are a single account-wide privacy switch in
     * WhatsApp's Settings → Privacy panel: when disabled, peers stop
     * seeing when this account opens their messages, but in exchange
     * this account also stops seeing read receipts from them. Enabling
     * read receipts immediately re-enables both directions.
     *
     * <p>This is a convenience shortcut over
     * {@link #editPrivacySetting(PrivacySettingType, PrivacySettingValue)}
     * with the read-receipts category. Use it when an application
     * surfaces a simple on/off toggle rather than the broader privacy
     * audience picker.
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
     * <p>Privacy tokens are server-side bindings between two accounts that
     * survive across either side's device re-pairings. The current concrete
     * use case is the "trusted contact" workflow: when the local user
     * explicitly trusts a peer, this call records the bond on the server so
     * the peer's identity-key changes do not silently invalidate the
     * relationship and so message-receipt privacy decisions can be
     * short-circuited for that contact. Subsequent device-list rebuilds and
     * Signal protocol session resets read the same token back to confirm
     * the peer is still trusted.
     *
     * <p>The {@code timestamp} value is recorded server-side and surfaced
     * back as the issue time on subsequent token reads — pass the actual
     * instant the bond was created so the server can sequence multiple
     * tokens issued for the same peer. Issuing a fresh token for a peer
     * who already has one of the same type overwrites the previous record.
     *
     * @param userJid    the peer whose identity the tokens cover; never
     *                   {@code null}
     * @param tokenTypes the token categories to issue; never {@code null},
     *                   must be non-empty
     * @param timestamp  the non-{@code null} issue time the server records
     *                   on each token
     * @throws NullPointerException            if any argument is {@code null}
     * @throws IllegalArgumentException        if {@code tokenTypes} is empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    void issuePrivacyTokens(JidProvider userJid, Collection<PrivacyTokenType> tokenTypes, Instant timestamp);

    /**
     * Reconciles the local cache of a server-side privacy "disallowed list",
     * returning either a {@code match} verdict or the fresh contact roster
     * paired with the new content digest.
     *
     * <p>Privacy disallowed lists are the per-category exclusion rosters that
     * back the {@link PrivacySettingType#LAST_SEEN}, {@link PrivacySettingType#PROFILE_PIC},
     * {@link PrivacySettingType#STATUS} and {@link PrivacySettingType#ADD_ME_TO_GROUPS}
     * audience selectors. Each category maintains its own sliding-digest
     * (the {@code dhash}) so reconciliation can hit a server-side fast path
     * when the local cache is up to date. Submit the digest of the locally
     * cached roster as {@code dhash}; the server returns a
     * {@link PrivacyDisallowedList#isMatch()} verdict when the digest matches
     * its own and otherwise {@link PrivacyDisallowedList#users()} plus
     * {@link PrivacyDisallowedList#dhash()} carrying the fresh contacts and
     * the new digest.
     *
     * <p>Goes through the {@code w:mex} GraphQL pipeline (WA Web's preferred
     * transport when {@code WAWebPrivacyGatingUtils.isMexPrivacyContactListEnabled}
     * is on and a device LID is known); the legacy {@code privacy} XMPP IQ
     * form is intentionally not surfaced — callers shouldn't pick a transport.
     *
     * <p>Applications typically call this on first connect for every
     * category they care about (with {@code dhash} set to the digest from
     * the previous run, or {@code ""} on a cold start) and again whenever a
     * server-side 409 conflict on a privacy-set update signals that the
     * digest the client carried in the request was already stale.
     *
     * @param jid      the requesting user's JID — the MEX query wraps the
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
     * Reads the current "reachout timelock" enforcement window applied to the
     * authenticated account.
     *
     * <p>WhatsApp throttles new-chat initiation when an account has been
     * flagged for spam reports or suspicious outreach: while the timelock is
     * active the send path is gated and the UI surfaces a cooldown notice.
     * The query returns whether enforcement is currently active, when the
     * window ends and which enforcement type applies (soft warning, hard
     * block, etc.).
     *
     * <p>Cobalt invokes this once during the post-login bootstrap so the
     * server records the same compliance ping WA Web emits at app launch
     * (see {@code WAWebStartBackend} which calls
     * {@code WAWebGetReachoutTimelockJob.fetchReachoutTimelock} as part of
     * the post-success housekeeping). The returned record is currently
     * surfaced to callers but not persisted on the store; applications that
     * want to gate their own send path on the timelock should call this
     * directly and inspect the result.
     *
     * @return an {@link Optional} carrying the parsed
     *         {@link ReachoutTimelock} verdict, or empty when the
     *         server returned no payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<ReachoutTimelock> queryReachoutTimelock();

    /**
     * Fetches the FMX (first-message-experience) integrity signals the
     * relay attaches to a peer, exposing whether the peer is a new account
     * and whether starting a chat with them is flagged as suspicious.
     *
     * <p>Integrity signals power the safety nudges WhatsApp shows when a
     * user starts a conversation with an unfamiliar contact: the
     * {@code is_new_account} flag drives the "new on WhatsApp" badge while
     * {@code is_suspicious_start_chat} drives the spam-warning sheet.
     * Callers integrating their own start-chat surface can call this once
     * the user composes the first outbound message to drive the same
     * advisories.
     *
     * <p>Dispatches the
     * {@link FetchIntegritySignalsMexRequest fetchIntegritySignals} MEX
     * query.
     *
     * @param userJid the non-{@code null} user JID whose signals are being
     *                fetched
     * @return an {@link Optional} carrying the parsed
     *         {@link UserIntegritySignals}, or empty if the relay
     *         returned no payload (for example because the peer is
     *         not visible to integrity scoring)
     * @throws NullPointerException            if {@code userJid} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     */
    Optional<UserIntegritySignals> queryUserIntegritySignals(JidProvider userJid);

    /**
     * Reads the current outbound new-chat messaging quota the server enforces
     * on the authenticated account.
     *
     * <p>WhatsApp meters how many brand-new chat threads a user can open per
     * billing cycle; the response carries the total quota, used quota, the
     * cycle start/end timestamps and a series of status flags
     * ({@code oteStatus}, {@code mvStatus}, {@code cappingStatus}) that drive
     * the throttling UI and per-message warnings on the official clients.
     *
     * <p>Cobalt invokes this once during the post-login bootstrap so the
     * server registers the same compliance ping WA Web emits whenever a new
     * chat is initiated (the persisted-job
     * {@code WAWebGetNewChatMessageCappingInfoJob.getNewChatMessageCapping}
     * delegates to {@code WAWebMexFetchNewChatMessageCappingInfoJob.mexFetchNewChatMessageCapping}).
     * The returned record is surfaced to callers but not currently persisted
     * on the store; applications that want to gate their own send path on
     * the capping verdict should call this directly and inspect the result.
     *
     * @return an {@link Optional} carrying the parsed
     *         {@link NewChatMessageCappingInfo}, or empty when the
     *         server returned no envelope
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<NewChatMessageCappingInfo> queryNewChatMessageCappingInfo();

    /**
     * Submits a passkey-signed integrity challenge response and throws when
     * the relay rejects the assertion.
     *
     * <p>Server-side integrity challenges are emitted as
     * {@code <notification type="mex" op_name="NotificationIntegrityChallengeRequest">}
     * stanzas, observable through Cobalt's MEX notification stream. Once a
     * caller receives a challenge it signs it with the user's WebAuthn
     * passkey credential and submits the JSON-serialised assertion through
     * this entry point. The relay's verdict comes back as a
     * {@code success} flag plus an optional {@code error_message}; on
     * rejection this method throws a
     * {@link WhatsAppServerRuntimeException} carrying the relay-side
     * error message. A clean return signals acceptance.
     *
     * <p>The handshake is initiated by the server, so there is no automatic
     * post-login invocation: callers wire their own observer on the
     * {@code NotificationIntegrityChallengeRequest} notification (currently
     * logged and _ by {@code NotificationMexStreamHandler}) and invoke
     * this method when the challenge arrives. The {@code prfAvailable}
     * argument mirrors WA Web's {@code e.prf_output != null} check on the
     * WebAuthn assertion.
     *
     * @param signedChallenge the JSON-serialised WebAuthn assertion bytes;
     *                        Cobalt base64-encodes them inline to mirror the
     *                        JS {@code btoa(JSON.stringify(e))} call; never
     *                        {@code null}
     * @param prfAvailable    {@code true} when the assertion carries a
     *                        {@code prf_output} field
     * @throws NullPointerException            if {@code signedChallenge} is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  when the relay rejected the
     *                                         challenge; the relay-side
     *                                         {@code error_message} is
     *                                         carried in the exception
     *                                         message
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    void submitPasskeyIntegrityChallenge(byte[] signedChallenge, boolean prfAvailable);

    /**
     * Reads the registered country code for a single user through the
     * MEX/GraphQL transport.
     *
     * <p>The country code is the two-letter ISO identifier the server
     * derives from the user's phone-number registration; it is exposed by
     * the MEX usync projection but has no native USync equivalent. This
     * method routes through
     * {@link #executeUsyncMex(Boolean, Boolean, Boolean, String)} with only
     * the {@code include_country_code} toggle enabled.
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
     * Reads the WhatsApp username claimed by a single user through the
     * MEX/GraphQL transport.
     *
     * <p>WhatsApp now lets users claim a global username distinct from their
     * phone-number identifier; once claimed, the username is reachable via
     * the MEX usync projection. This convenience method routes through
     * {@link #executeUsyncMex(Boolean, Boolean, Boolean, String)} with only
     * the {@code include_username} toggle enabled and surfaces the
     * {@code username_info.username} string. Callers that need the full
     * username envelope (state, pin, status) should call
     * {@link #executeUsyncMex(Boolean, Boolean, Boolean, String)} directly.
     *
     * @param userJid the user JID to query; never {@code null}
     * @return the claimed username, or {@link Optional#empty()} when the
     *         user has not claimed one or the server returned no item
     * @throws NullPointerException            if {@code userJid} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<String> queryUserUsername(JidProvider userJid);

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
     */
    void editDefaultDisappearingMode(ChatEphemeralTimer timer);

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
     */
    GroupMetadata createGroup(String subject, Collection<? extends JidProvider> participants);

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
     */
    GroupMetadata createGroup(String subject, ChatEphemeralTimer ephemeralTimer, Collection<? extends JidProvider> participants);

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
     */
    void leaveGroup(JidProvider group);

    /**
     * Leaves multiple WhatsApp groups in one request.
     *
     * <p>Convenience varargs overload of
     * {@link #leaveGroup(JidProvider)} that batches several JIDs into a
     * single {@code <leave>} stanza, mirroring WA Web's
     * {@code WAWebGroupExitJob} which accepts a list and emits one
     * {@code <group id="..."/>} per entry.
     *
     * @param groups the JIDs of the groups to leave; never {@code null} or
     *               empty
     * @throws NullPointerException     if {@code groups} or any element is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code groups} is empty or any
     *                                  JID is not a group/community
     */
    void leaveGroup(JidProvider... groups);

    /**
     * Reports whether the given group is flagged as "internal" by the
     * WhatsApp relay.
     *
     * <p>Internal groups are the Meta-side staff or testing groups whose
     * lifecycle differs from regular consumer groups; the indicator is
     * surfaced on the four group inline fragments
     * ({@code XWA2GroupRegularGroup}, {@code XWA2CommunityGroup},
     * {@code XWA2CommunityDefaultSubGroup}, {@code XWA2CommunitySubGroup})
     * via the shared {@code properties.internal} scalar.
     *
     * <p>Dispatches the
     * {@link FetchGroupIsInternalMexRequest mexFetchGroupIsInternal} MEX
     * query.
     *
     * @param groupJid the non-{@code null} group JID to query
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
     * Convenience overload of
     * {@link #queryGroupInfo(JidProvider, boolean, String, String)} that
     * fetches a cold metadata snapshot with no participant-list partial hash
     * and no query-context tag.
     *
     * @param group the group JID
     * @return the group metadata, or empty when the relay returned no payload
     * @throws NullPointerException if {@code group} is {@code null}
     */
    Optional<GroupMetadata> queryGroupInfo(JidProvider group);

    /**
     * Queries the full metadata envelope for a group, excluding bot
     * participants from the participant edge list.
     *
     * <p>Dispatches the {@code mexGetGroupInfo} MEX query.
     *
     * @param group             the non-{@code null} group JID to query
     * @param includeUsername   whether to hydrate the {@code username_info}
     *                          subtree on every participant; {@code null}
     *                          mirrors WA Web's "field absent" wire shape
     * @param participantsPhash the participant-list partial hash carried
     *                          for incremental refreshes; {@code null} on
     *                          a cold fetch
     * @param queryContext      the WA Web query-context tag identifying
     *                          the UI surface that triggered the fetch;
     *                          {@code null} when no tag applies
     * @return an {@link Optional} carrying the parsed {@link GroupMetadata},
     *         or empty when the relay returned no payload
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<GroupMetadata> queryGroupInfo(JidProvider group, boolean includeUsername, String participantsPhash, String queryContext);

    /**
     * Queries the full metadata envelope for a group, including bot
     * participants in the participant edge list.
     *
     * <p>Dispatches the {@code mexGetGroupInfoIncludBots} MEX query, used
     * by chat UIs that render bots as first-class members.
     *
     * @param group             the non-{@code null} group JID to query
     * @param includeUsername   whether to hydrate the {@code username_info}
     *                          subtree on every participant
     * @param participantsPhash the participant-list partial hash, or
     *                          {@code null} on a cold fetch
     * @param queryContext      the WA Web query-context tag, or {@code null}
     * @return an {@link Optional} carrying the parsed {@link GroupMetadata},
     *         or empty when the relay returned no payload
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<GroupMetadata> queryGroupInfoIncludingBots(JidProvider group, boolean includeUsername, String participantsPhash, String queryContext);

    /**
     * Queries the current invite code attached to the given group without
     * rotating it.
     *
     * <p>The invite code is the opaque scalar that backs the shareable
     * {@code chat.whatsapp.com/<code>} link. To rotate it (invalidating any
     * previously distributed link) use
     * {@link #createGroupInviteCode(Jid, String)} instead.
     *
     * <p>Dispatches the
     * {@link FetchGroupInviteCodeMexRequest fetchMexGroupInviteCode} MEX
     * query.
     *
     * @param group        the non-{@code null} group JID to query
     * @param queryContext the WA Web query-context tag identifying the UI
     *                     surface that triggered the fetch; {@code null}
     *                     when no tag applies
     * @return an {@link Optional} carrying the current invite-code scalar,
     *         or empty when the relay returned no payload
     * @throws NullPointerException            if {@code group} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Optional<String> queryGroupInviteCode(JidProvider group, String queryContext);

    /**
     * Rotates the invite code for the given group, community or other
     * receiver, invalidating any previously distributed
     * {@code chat.whatsapp.com/<code>} link.
     *
     * <p>The relay mints a fresh opaque code which is returned through the
     * response payload. The {@code entryPoint} telemetry tag is mandatory:
     * WA Web's sole caller ({@code WAWebOutContactInviteAction.sendInvite})
     * always forwards a non-{@code null} surface identifier and Cobalt
     * mirrors the contract at the API boundary.
     *
     * <p>Dispatches the
     * {@link CreateInviteCodeMexRequest mexCreateInviteCode} MEX mutation.
     *
     * @param receiver   the non-{@code null} receiver JID the code is being
     *                   minted for (group, community or contact)
     * @param entryPoint the non-{@code null} UI-surface telemetry tag
     *                   identifying what triggered the rotation, e.g.
     *                   {@code "CHAT_INFO_INVITE_BUTTON"}
     * @return an {@link Optional} carrying the freshly minted invite-code
     *         scalar, or empty when the relay returned no payload
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    String createGroupInviteCode(JidProvider receiver, String entryPoint);

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
     */
    CommunityMetadata createCommunity(String name, String description);

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
     */
    CommunityMetadata createCommunity(String name, String description, ChatEphemeralTimer ephemeralTimer);

    /**
     * Deactivates (deletes) a community parent group on the server, turning
     * every linked subgroup into a standalone group.
     *
     * <p>Delegates to the typed {@link SmaxGroupsDeleteParentGroupRequest}
     * which issues the
     * {@code WASmaxGroupsDeleteParentGroupRPC.sendDeleteParentGroupRPC}
     * mutation: an {@code iq} of {@code type="set", xmlns="w:g2"} addressed
     * to the community JID with a {@code <delete_parent/>} body. Relay-side
     * client/server errors are surfaced as
     * {@link WhatsAppServerRuntimeException}.
     *
     * @param community the non-{@code null} JID of the community to
     *                  deactivate
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
     * <p>Delegates to the typed {@link IqGroupExitRequest} in
     * {@link IqGroupExitRequest.Mode#LINKED_GROUPS} mode: an {@code iq} of
     * {@code type="set", xmlns="w:g2"} addressed to the {@code g.us}
     * server carrying a {@code <leave>} body with a single
     * {@code <linked_groups parent_group_jid="..."/>} element, so the
     * server applies the exit to every linked subgroup in one round-trip.
     * Per-target codes carried in the success reply are observational;
     * relay-side errors are surfaced as
     * {@link WhatsAppServerRuntimeException}.
     *
     * @param community the non-{@code null} JID of the community to leave
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
     */
    void transferCommunityOwnership(JidProvider community, JidProvider newOwner);

    /**
     * Queries the pending subgroup suggestions for a community — groups the
     * user belongs to that the server recommends moving into the community.
     *
     * <p>Issues the {@code WAWebMexFetchSubgroupSuggestionsJob} MEX query
     * over {@code w:mex} using
     * {@link FetchSubgroupSuggestionsMexRequest} with
     * {@code query_context="INTERACTIVE"}.
     *
     * @param community the non-{@code null} community JID to query
     * @return the list of suggested subgroup JIDs; empty when there are no
     *         pending suggestions
     * @throws NullPointerException     if {@code community} is {@code null}
     * @throws IllegalArgumentException if the JID is not a group/community
     */
    List<Jid> querySubgroupSuggestions(JidProvider community);

    /**
     * Approves a pending subgroup suggestion, accepting the group into the
     * community as an official subgroup.
     *
     * <p>Equivalent to calling the shared {@code sub_group_suggestions_action}
     * stanza with an {@code <approve>} child carrying the {@code creator}
     * and {@code jid} attributes of the candidate suggestion. The
     * {@code creator} JID is the user that originally proposed the
     * candidate; it is surfaced through the suggestion-fetch flow on the
     * {@code creator.id} field of each suggestion edge.
     *
     * @param community         the non-{@code null} parent community JID
     * @param suggestedSubgroup the non-{@code null} JID of the suggested
     *                          group to approve
     * @param suggestionCreator the non-{@code null} JID of the user that
     *                          proposed the suggestion
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if either group JID is not a
     *                                  group/community
     */
    void approveSubgroupSuggestion(JidProvider community, JidProvider suggestedSubgroup, JidProvider suggestionCreator);

    /**
     * Rejects a pending subgroup suggestion, declining the recommendation
     * to move the group into the community.
     *
     * <p>Equivalent to calling the shared {@code sub_group_suggestions_action}
     * stanza with a {@code <reject>} child carrying the {@code creator}
     * and {@code jid} attributes of the candidate suggestion. The
     * {@code creator} JID is the user that originally proposed the
     * candidate; it is surfaced through the suggestion-fetch flow on the
     * {@code creator.id} field of each suggestion edge.
     *
     * @param community         the non-{@code null} parent community JID
     * @param suggestedSubgroup the non-{@code null} JID of the suggested
     *                          group to reject
     * @param suggestionCreator the non-{@code null} JID of the user that
     *                          proposed the suggestion
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if either group JID is not a
     *                                  group/community
     */
    void rejectSubgroupSuggestion(JidProvider community, JidProvider suggestedSubgroup, JidProvider suggestionCreator);

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
     */
    long querySubgroupParticipantCount(JidProvider subgroup);

    /**
     * Queries the full list of subgroups that belong to a community,
     * projecting each entry into a {@link CommunityLinkedGroup} with the
     * subgroup's subject, creation timestamp, general/hidden/approval
     * markers, and back-reference to the parent community.
     *
     * <p>Issues the {@code WAWebMexFetchAllSubgroupsJob} MEX query over
     * {@code w:mex} via {@link FetchAllSubgroupsMexRequest} with
     * {@code query_context="INTERACTIVE"} and returns every subgroup
     * declared by the response. The first entry of the result is always
     * the community's default announcement subgroup
     * ({@link CommunityLinkedGroup#isDefaultSubgroup()} {@code == true});
     * regular subgroups follow in the order the relay surfaced them.
     *
     * <p>Mirrors WA Web's {@code mexFetchAllSubgroups} which throws a
     * {@code 500 ServerStatusCodeError} when the response omits the
     * default subgroup or the regular subgroup edges; Cobalt surfaces
     * the same condition as a {@link WhatsAppServerRuntimeException}.
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
     */
    Map<Jid, GroupParticipantStatus> addGroupParticipants(JidProvider group, Collection<? extends JidProvider> toAdd);

    /**
     * Removes one or more participants from a WhatsApp group without
     * cascading the eviction to linked sub-groups.
     *
     * <p>Convenience for
     * {@link #removeGroupParticipants(Jid, Collection, boolean)} that
     * passes {@code false} for {@code removeLinkedGroups}.
     *
     * @param group    the non-{@code null} target group JID
     * @param toRemove the non-{@code null}, non-empty collection of user
     *                 JIDs to remove
     * @return a map from the target JID to its server-assigned
     *         {@link GroupParticipantStatus}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code toRemove} is empty
     */
    Map<Jid, GroupParticipantStatus> removeGroupParticipants(JidProvider group, Collection<? extends JidProvider> toRemove);

    /**
     * Removes one or more participants from a group and, when requested,
     * cascades the eviction across every sub-group of the same community
     * in a single round trip.
     *
     * <p>Dispatches the SMAX-typed
     * {@code WASmaxGroupsRemoveParticipantsRPC}: when
     * {@code removeLinkedGroups} is {@code true} the relay also evicts
     * the participants from every linked sub-group of the parent
     * community; when {@code false} the call behaves like the plain
     * 2-arg overload.
     *
     * @param group              the target group JID; never {@code null}
     * @param toRemove           the participants to remove; never
     *                           {@code null} and must contain at least
     *                           one entry
     * @param removeLinkedGroups when {@code true} the participants are
     *                           also evicted from every sub-group of
     *                           the parent community in one round trip
     * @return a map from each participant JID to the per-target outcome
     *         {@link GroupParticipantStatus}; entries map to
     *         {@link GroupParticipantStatus#OK} when the relay returned no
     *         error, or to the matching status constant for the relay's
     *         per-target rejection code
     * @throws NullPointerException            if any reference argument
     *                                         is {@code null}
     * @throws IllegalArgumentException        if {@code toRemove}
     *                                         is empty
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     */
    Map<Jid, GroupParticipantStatus> removeGroupParticipants(JidProvider group, Collection<? extends JidProvider> toRemove, boolean removeLinkedGroups);

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
     */
    void promoteGroupParticipants(JidProvider group, Collection<? extends JidProvider> toPromote);

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
     */
    void demoteGroupParticipants(JidProvider group, Collection<? extends JidProvider> toDemote);

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
     */
    void favoriteSticker(String stickerHash);

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
     */
    void unfavoriteSticker(String stickerHash);

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
     */
    void removeRecentSticker(String stickerHash);

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
     */
    ChatMessageInfo createPoll(PollCreate create);

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
     * {@link PollEncValue}.
     *
     * <p>The vote payload is HKDF-derived and AES-GCM-encrypted via
     * {@link EncMessageFactory#encryptPollVote}: the option labels are hashed
     * with SHA-256, packed into a {@link PollVoteMessage},
     * encrypted under a key derived from the poll creator's
     * {@code messageSecret}, and emitted as a {@link PollEncValue} that the
     * server forwards opaquely to the poll creator.
     *
     * @param pollKey          the {@link MessageKey} of the
     *                         {@link PollCreationMessage} being voted on
     * @param selectedOptions  the ordered list of selected option labels
     * @return the server ack result for the vote stanza
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code pollKey} has no parent JID,
     *                                  or the referenced poll-creation message
     *                                  is missing from the local store, or it
     *                                  carries no {@code messageSecret}
     */
    AckResult votePoll(MessageKey pollKey, List<String> selectedOptions);

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
     */
    AckResult closePoll(MessageKey pollKey);

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
     */
    void sendBotWelcomeRequest(JidProvider botJid);

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
     */
    void renameAiThread(String chatJid, String threadId, String newName);

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
     */
    void deleteAiThread(String chatJid, String threadId);

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
     * @param chat the JID of the chat to favourite
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void favoriteChat(JidProvider chat);

    /**
     * Removes the given chat from the favourites list and propagates the
     * change to every linked device via the {@code REGULAR_HIGH} app-state
     * sync collection.
     *
     * <p>Counterpart to {@link #favoriteChat(Jid)}: emits the same full
     * favourites list minus the target JID.
     *
     * @param chat the JID of the chat to unfavourite
     * @throws NullPointerException if {@code chat} is {@code null}
     */
    void unfavoriteChat(JidProvider chat);

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
     */
    String addNoteToChat(JidProvider chat, String noteText);

    /**
     * Updates the text of an existing note attached to the given chat and
     * propagates the change via the {@code REGULAR_LOW} app-state sync
     * collection.
     *
     * @param chat     the chat the note is attached to
     * @param noteId   the identifier of the note to update
     * @param newText  the new note text
     * @throws NullPointerException if any argument is {@code null}
     */
    void editNoteOnChat(JidProvider chat, String noteId, String newText);

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
     */
    void deleteNoteFromChat(JidProvider chat, String noteId);

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
     */
    AckResult pinMessage(MessageKey msgKey);

    /**
     * Removes the pin from the referenced message inside its chat for every
     * participant.
     *
     * <p>Counterpart to {@link #pinMessage(MessageKey)}: emits the
     * same message with {@link PinInChatMessage.Type#UNPIN_FOR_ALL}.
     *
     * @param msgKey the key of the message to unpin
     * @return the server ack result for the unpin stanza
     * @throws NullPointerException     if {@code msgKey} is {@code null}
     * @throws IllegalArgumentException if {@code msgKey} has no parent JID
     */
    AckResult unpinMessage(MessageKey msgKey);

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
     * @param locale the new BCP-47 locale tag (e.g. {@code "en_US"}); must not
     *               be {@code null}
     * @throws NullPointerException            if {@code locale} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editLocale(String locale);

    /**
     * Changes the user's disable-link-previews privacy setting and propagates
     * the change to every linked device via the {@code REGULAR} app-state
     * sync collection.
     *
     * <p>Mirrors {@code WAWebDisableLinkPreviewsSync.sendMutation}, which
     * builds a {@code setting_disableLinkPreviews} mutation through
     * {@link DisableLinkPreviewsHandler#getMutation(Instant, boolean)} and
     * hands it to {@code WAWebSyncdCoreApi.lockForSync}. Cobalt uses
     * {@link WebAppStateService#pushPatches(SyncPatchType, SequencedCollection)}
     * for the same purpose.
     *
     * <p>The new value is also eagerly written to
     * {@link WhatsAppStore#setDisableLinkPreviews(boolean)} so subsequent
     * reads are consistent.
     * @param disabled {@code true} to disable link previews, {@code false} to
     *                 allow them
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editDisableLinkPreviews(boolean disabled);

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
     * @param twentyFourHourFormat {@code true} to enable 24-hour display,
     *                             {@code false} for 12-hour display
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editTwentyFourHourFormat(boolean twentyFourHourFormat);

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
     * {@link #editAIFeaturesEnabled(boolean)} maps the boolean flag to the
     * {@code ENABLED} / {@code DISABLED} enum variants.
     *
     * <p>The new value is also eagerly written to
     * {@link WhatsAppStore#setAiBusinessAgentStatus(MaibaAIFeaturesControlAction.MaibaAIFeatureStatus)}
     * so subsequent reads are consistent.
     * @param enabled {@code true} to enable AI features (emits {@code ENABLED}),
     *                {@code false} to disable them (emits {@code DISABLED})
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editAIFeaturesEnabled(boolean enabled);

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
     * @param unarchive {@code true} to automatically unarchive a chat on the
     *                  arrival of a new message, {@code false} to keep archived
     *                  chats archived
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editUnarchiveChatsOnNewMessage(boolean unarchive);

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
     * @param setting the new {@link NotificationActivitySettingAction.NotificationActivitySetting};
     *                must not be {@code null}
     * @throws NullPointerException            if {@code setting} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editNotificationActivity(NotificationActivitySettingAction.NotificationActivitySetting setting);

    /**
     * Exchanges a CTWA email-recovery verification code for a
     * Facebook access token and Ads-Manager session cookies.
     *
     * <p>Sends the {@code GetAccessTokenAndSessionCookiesRPC} IQ
     * carrying the user-supplied verification code under
     * {@code <iq xmlns="fb:thrift_iq" type="get" to="s.whatsapp.net"/>}
     * and projects the {@code Success} reply into a
     * {@link CtwaAccessTokenSession} bundle. The dedicated literal
     * variants ({@code 431 TOO_MANY_ATTEMPTS}, {@code 432
     * INCORRECT_NONCE}) and any other client/server failure surface as
     * a {@link WhatsAppServerRuntimeException}.
     *
     * @param code        the user-supplied verification code; never
     *                    {@code null}
     * @param fromUserJid the optional {@code from} echo; may be
     *                    {@code null}
     * @return an {@link Optional} carrying the parsed
     *         {@link CtwaAccessTokenSession} bundle, or
     *         {@link Optional#empty()} when no documented variant
     *         parsed
     * @throws NullPointerException        if {@code code} is
     *                                     {@code null}
     * @throws WhatsAppServerRuntimeException if the relay rejected the
     *                                     request with one of the
     *                                     literal {@code 431}/{@code 432}
     *                                     codes, a generic 4xx, or a
     *                                     5xx server failure
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<CtwaAccessTokenSession> queryAccessTokenAndSessionCookies(String code, JidProvider fromUserJid);

    /**
     * Issues an account-binding nonce used by the linking flow to bind
     * the WhatsApp account to a Facebook business identity.
     *
     * <p>Sends the {@code GetAccountNonceRPC} IQ under
     * {@code <iq xmlns="fb:thrift_iq" type="get"/>} optionally
     * carrying an {@code <identifier scope="..."/>} child and returns
     * the issued nonce string.
     *
     * @param identifierScope the optional {@code <identifier scope/>}
     *                        attribute; {@code null} omits the child
     * @return an {@link Optional} carrying the issued nonce, or
     *         {@link Optional#empty()} when the relay did not return a
     *         documented {@code Success} variant
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a
     *                                         documented client / server
     *                                         error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<String> queryAccountNonce(String identifierScope);

    /**
     * Queries the relay for the current marketing-message /
     * Meta-Verified / GenAI feature-eligibility status of the local
     * business account.
     *
     * <p>Sends the {@link SmaxGetBusinessEligibilityRequest} IQ under
     * {@code <iq xmlns="w:biz" type="get"/>} carrying a
     * {@code <features/>} child whose three optional attributes select
     * which projections are returned by the relay. Each toggle is
     * encoded on the wire as {@code "1"} when {@code true} and
     * {@code "0"} when {@code false}; passing {@code null} omits the
     * attribute entirely so the server returns no projection for that
     * feature.
     *
     * @param featuresMetaVerified      the optional Meta-Verified
     *                                  toggle; {@code null} to omit
     * @param featuresMarketingMessages the optional marketing-messages
     *                                  toggle; {@code null} to omit
     * @param featuresGenai             the optional GenAI toggle;
     *                                  {@code null} to omit
     * @return an {@link Optional} carrying the parsed
     *         {@link BusinessEligibility} bundle when the relay
     *         returned an eligibility tuple, or
     *         {@link Optional#empty()} on no-parse
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a
     *                                         documented client / server
     *                                         error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<BusinessEligibility> queryBusinessEligibility(boolean featuresMetaVerified, boolean featuresMarketingMessages, boolean featuresGenai);

    /**
     * Lists the Facebook / Instagram / WhatsApp-ad-identity accounts
     * currently linked to this WhatsApp business account.
     *
     * <p>Sends the {@link SmaxGetLinkedAccountsRequest} IQ under
     * {@code <iq xmlns="fb:thrift_iq" type="get"/>} carrying an empty
     * {@code <linked_accounts/>} child and projects the relay's
     * {@code Success} arm carrying optional Facebook page, Facebook
     * business, Instagram professional, and WhatsApp-ad-identity
     * sub-tuples.
     *
     * @return an {@link Optional} carrying the parsed
     *         {@link BusinessLinkedAccounts} bundle when the relay
     *         returned a linked-accounts tuple, or
     *         {@link Optional#empty()} on no-parse
     * @throws WhatsAppServerRuntimeException  if the relay returned the
     *                                         literal {@code Forbidden}
     *                                         arm or any other client /
     *                                         server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<BusinessLinkedAccounts> queryLinkedAccounts();

    /**
     * Fetches the current SMB-data-sharing-with-Meta consent value
     * stored server-side.
     *
     * <p>Sends the {@link SmaxGetPrivacySettingRequest} IQ under
     * {@code <iq xmlns="w:biz" type="get" to="s.whatsapp.net"/>} with
     * an empty {@code <privacy/>} body and projects the
     * {@code <smb_data_sharing_with_meta_consent value="..."/>} grand-child.
     *
     * @return an {@link Optional} carrying the parsed
     *         {@link BusinessDataSharingConsent} when the relay
     *         returned a consent value, or {@link Optional#empty()}
     *         on no-parse
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client / server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<BusinessDataSharingConsent> queryBusinessPrivacySetting();

    /**
     * Persists the SMB-data-sharing-with-Meta consent value
     * server-side.
     *
     * <p>Sends the {@code SetPrivacySettingRPC} IQ under
     * {@code <iq xmlns="w:biz" type="set" to="s.whatsapp.net"/>} with
     * a {@code <privacy>} body that wraps an optional
     * {@code <smb_data_sharing_with_meta_consent value=...>} child.
     * Passing {@code null} as {@code dataSharingConsent} clears the
     * stored choice.
     *
     * @param dataSharingConsent the consent value to persist; pass
     *                           {@code null} to clear the value
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         change with a documented
     *                                         client / server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editBusinessPrivacySetting(BusinessDataSharingConsent dataSharingConsent);

    /**
     * Records a per-contact message-feedback preference (block /
     * unblock / report and similar opaque actions).
     *
     * <p>Sends the {@link SmaxUpdatePreferenceRequest} IQ under
     * {@code <iq xmlns="w:biz:msg_feedback" type="set"
     * to="s.whatsapp.net"/>} with a {@code <user_feedback action= jid=
     * feedback?/>} child.
     *
     * @param action   the feedback action verb; never {@code null}
     * @param jid      the target contact JID; never {@code null}
     * @param feedback the optional free-form annotation; may be
     *                 {@code null}
     * @throws NullPointerException            if {@code action} or
     *                                         {@code jid} is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         change with a documented
     *                                         client / server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void updateMessageFeedbackPreference(MessageFeedbackAction action, JidProvider jid, String feedback);

    /**
     * Convenience overload of
     * {@link #queryMeteredMessagingCheckout(List, boolean, boolean, String, List)}
     * for the basic quote-only case (no ad-account marker, no dedupe-skip, no
     * offer id, no pending campaigns).
     *
     * @param participants the prospective message recipients
     * @return the checkout quote, or empty when the relay returned no payload
     * @throws NullPointerException if {@code participants} is {@code null}
     */
    Optional<BusinessMeteredMessagingCheckout> queryMeteredMessagingCheckout(List<? extends JidProvider> participants);

    /**
     * Queries the relay-side metered-messaging checkout for the SMB
     * marketing-messages feature, returning the cost / quota /
     * eligibility tuple used by the campaign-creation UI.
     *
     * <p>Sends the {@link SmaxGetSMBMeteredMessagingCheckoutRequest} IQ
     * under {@code <iq xmlns="w:biz" type="get"/>} carrying the
     * {@code <participants/>} payload plus the four optional
     * markers / projections, then projects the
     * {@code Success} arm into the cost-quote tuple.
     *
     * @param participants     the recipient JIDs (1..2000); never
     *                         {@code null}
     * @param useAdAccount     whether to attach the
     *                         {@code <use_ad_account/>} marker
     * @param skipDedupe       whether to attach the
     *                         {@code <skip_dedupe/>} marker
     * @param offerId          the optional {@code <offer id/>} value;
     *                         may be {@code null}
     * @param pendingCampaigns the optional pending-campaign list (max
     *                         200); may be {@code null}
     * @return an {@link Optional} carrying the parsed
     *         {@link BusinessMeteredMessagingCheckout} bundle when the
     *         relay returned a cost quote, or
     *         {@link Optional#empty()} on no-parse
     * @throws NullPointerException            if {@code participants}
     *                                         is {@code null}
     * @throws IllegalArgumentException        if {@code participants}
     *                                         is outside the supported
     *                                         range or
     *                                         {@code pendingCampaigns}
     *                                         exceeds 200 entries
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client / server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<BusinessMeteredMessagingCheckout> queryMeteredMessagingCheckout(List<? extends JidProvider> participants, boolean useAdAccount, boolean skipDedupe, String offerId, List<BusinessMeteredMessagingPendingCampaign> pendingCampaigns);

    /**
     * Issues a silent CTWA access-token nonce — a probe used by the
     * silent-recovery flow to detect whether the local credentials are
     * still valid before triggering a full recovery prompt.
     *
     * <p>Sends the {@code RequestSilentNonceRPC} IQ under
     * {@code <iq xmlns="fb:thrift_iq" type="get"
     * to="s.whatsapp.net"/>} optionally echoing the active user JID
     * onto the {@code from} attribute. The relay either accepts the
     * silent path ({@link CtwaSilentNonceResult.Issued}) or refuses
     * and reports the email address that must confirm account
     * ownership before further nonces will be issued
     * ({@link CtwaSilentNonceResult.RecoveryRequired}).
     *
     * @param fromUserJid the optional {@code from} echo; may be
     *                    {@code null}
     * @return an {@link Optional} carrying the parsed
     *         {@link CtwaSilentNonceResult} variant, or
     *         {@link Optional#empty()} on no-parse
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client / server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<CtwaSilentNonceResult> querySilentNonce(JidProvider fromUserJid);

    /**
     * Triggers the server to dispatch a CTWA account-recovery nonce
     * (typically delivered as an out-of-band email).
     *
     * <p>Sends the {@code SendAccountRecoveryNonceRPC} IQ under
     * {@code <iq xmlns="fb:thrift_iq" type="get"
     * to="s.whatsapp.net"/>} optionally echoing the active user JID
     * onto the {@code from} attribute. The relay's response carries a
     * {@code Fail}/{@code Success} status content; this helper folds
     * the two arms into a boolean so callers can branch on whether the
     * recovery email was actually dispatched.
     *
     * @param fromUserJid the optional {@code from} echo; may be
     *                    {@code null}
     * @return {@code true} when the relay confirms the recovery email
     *         was dispatched, {@code false} when the relay tried but
     *         failed or when the response did not parse
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client / server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    boolean sendAccountRecoveryNonce(JidProvider fromUserJid);

    /**
     * Uploads CTWA-ad-media descriptors (id/type pairs) to the
     * native-ad payload service.
     *
     * <p>Sends the {@code UploadAdMediaRPC} IQ under
     * {@code <iq xmlns="fb:thrift_iq" type="set" to="s.whatsapp.net"/>}
     * carrying an optional primary {@code <media id type/>} child plus
     * 0..10 {@code <media_list id type/>} children. The relay's
     * success arm only echoes the same identifiers back, so this
     * helper returns no value; an unsuccessful response surfaces as a
     * {@link WhatsAppServerRuntimeException}.
     *
     * @param media     the optional primary media descriptor; may be
     *                  {@code null}
     * @param mediaList the additional media-list entries (0..10);
     *                  never {@code null}
     * @throws NullPointerException            if {@code mediaList} is
     *                                         {@code null}
     * @throws IllegalArgumentException        if {@code mediaList}
     *                                         exceeds 10 entries
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request with a documented
     *                                         client / server error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void uploadAdMedia(CtwaAdMediaEntry media, List<CtwaAdMediaEntry> mediaList);

    /**
     * Records the user's acceptance of a versioned payments
     * Terms-of-Service document.
     *
     * <p>Sends the {@code SetPaymentsTOSv3RPC} IQ under
     * {@code <iq xmlns="urn:xmpp:whatsapp:account" type="set"
     * to="s.whatsapp.net"/>} carrying the
     * {@code <accept_pay version="3" tos_version=... service=...>}
     * envelope chosen via the {@code BR}/{@code UPI} consumer variant.
     * The relay's success arm only echoes the request, so this helper
     * returns no value; an unsuccessful response surfaces as a
     * {@link WhatsAppServerRuntimeException}.
     *
     * @param acceptPayTosVersion the integer ToS version being
     *                            accepted
     * @param variant             the consumer-variant payload selecting
     *                            BR / UPI; never {@code null}
     * @throws NullPointerException            if {@code variant} is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         consent
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editPaymentsTosV3Acceptance(int acceptPayTosVersion, PaymentsTosV3ConsumerVariant variant);

    /**
     * Creates a Brazil-specific custom payment method
     * ({@code "pay_on_delivery"} or {@code "pix_key"}) for the local
     * business account and persists the supplied 1..5 metadata
     * key-value pairs.
     *
     * <p>Sends the {@link SmaxBrPaymentCreateCustomPaymentMethodRequest}
     * IQ under {@code <iq xmlns="w:pay" type="set"
     * to="s.whatsapp.net"/>} wrapping an
     * {@code <account action="create-custom-payment-method"
     * device_id country="BR">} envelope.
     *
     * @param accountDeviceId           the opaque device-id for the
     *                                  enrolment; never {@code null}
     * @param customPaymentMethodType   one of {@code "pay_on_delivery"}
     *                                  / {@code "pix_key"}; never
     *                                  {@code null}
     * @param customPaymentMethodUpdate the optional {@code update}
     *                                  marker; may be {@code null}
     * @param customPaymentMethodFlow   the optional {@code "p2p"} /
     *                                  {@code "p2m"} flow; may be
     *                                  {@code null}
     * @param metadata                  1..5 metadata pairs; never
     *                                  {@code null}, never empty
     * @return an {@link Optional} carrying the parsed
     *         {@link BrazilCustomPaymentMethod} when the relay
     *         assigned a credential-id, or {@link Optional#empty()}
     *         on no-parse
     * @throws NullPointerException            if any non-nullable
     *                                         argument is {@code null}
     * @throws IllegalArgumentException        if {@code metadata} is
     *                                         empty or has more than 5
     *                                         entries
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         create with an IQ-level
     *                                         error
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    BrazilCustomPaymentMethod createBrazilCustomPaymentMethod(BrazilCustomPaymentMethodCreate create);

/**
     * Publishes a newsletter post (or question response) to a
     * newsletter JID and waits for the application-level ack.
     *
     * <p>Sends a {@code <message to=NEWSLETTER_JID>} envelope wrapping
     * the supplied payload bytes inside a {@code <plaintext>} child,
     * choosing between the brand-new-message and the
     * question-response wire shapes based on whether the request
     * carries a target message server id. Mirrors WA Web's
     * {@code WASmaxMessagePublishNewsletterRPC}.
     *
     * @param newsletterJid the target newsletter JID; never
     *                      {@code null}
     * @param request       the publish request; never {@code null}
     * @return the relay's positive acknowledgement, never
     *         {@code null}
     * @throws NullPointerException            if either argument is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         negative ack or the
     *                                         envelope did not parse
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    NewsletterPublishAck publishNewsletterMessage(JidProvider newsletterJid, NewsletterPublishMessageRequest request);

    /**
     * Publishes a newsletter status post to a newsletter JID and
     * waits for the application-level ack.
     *
     * <p>Sends a {@code <status to=NEWSLETTER_JID>} envelope wrapping
     * the supplied payload bytes inside a {@code <plaintext>} child,
     * choosing between the brand-new-status and the
     * status-reaction-on-existing-status wire shapes based on whether
     * the request carries a target status server id. Mirrors WA
     * Web's {@code WASmaxStatusPublishPostNewsletterStatusRPC}.
     *
     * @param newsletterJid the target newsletter JID; never
     *                      {@code null}
     * @param request       the publish request; never {@code null}
     * @return the relay's positive acknowledgement, never
     *         {@code null}
     * @throws NullPointerException            if either argument is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         negative ack or the
     *                                         envelope did not parse
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    NewsletterPublishAck publishNewsletterStatus(JidProvider newsletterJid, NewsletterPublishStatusRequest request);

    /**
     * Dispatches the given typed legacy-IQ {@link IqOperation.Request} and
     * returns the raw response stanza.
     *
     * @param request the typed legacy-IQ request; never {@code null}
     * @return the raw inbound reply node; never {@code null}
     * @throws NullPointerException            if {@code request} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    Node sendNode(IqOperation.Request request);

    /**
     * Convenience overload of
     * {@link #queryBusinessCatalogProducts(JidProvider, List, int, int, String)}
     * for ordinary (non-direct-connection) merchants.
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
     * <p>Mirrors {@code WAWebQueryProductListCatalogJob.QueryProductListCatalog}.
     * The legacy IQ request carries one {@code <product><id/></product>}
     * child per id, the requested image dimensions, and an optional
     * direct-connection encrypted info blob; the relay echoes the full
     * product detail block per id (or a synthetic
     * {@code "INVALID_PRODUCT"} marker when an id does not resolve to
     * a live catalog entry).
     *
     * <p>The returned list is in the same order the relay published it
     * — typically the order of {@code productIds} — so callers can
     * zip the two lists together. Invalid entries surface as
     * {@link BusinessProduct} instances with {@link BusinessProduct#invalid()}
     * set to {@code true}; their other fields are unset.
     *
     * @param catalogJid                    the merchant catalog JID; never
     *                                      {@code null}
     * @param productIds                    the catalog product ids to fetch;
     *                                      never {@code null} and must be
     *                                      non-empty
     * @param width                         the desired image width in pixels
     * @param height                        the desired image height in pixels
     * @param directConnectionEncryptedInfo the optional direct-connection
     *                                      blob carried for direct-connection
     *                                      merchants; may be {@code null}
     * @return the parsed product list; never {@code null}, possibly empty
     *         when the relay published no products
     * @throws NullPointerException            if {@code catalogJid} or
     *                                         {@code productIds} is {@code null}
     * @throws IllegalArgumentException        if {@code productIds} is empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a client-
     *                                         or server-error variant
     */
    List<BusinessProduct> queryBusinessCatalogProducts(JidProvider catalogJid, List<String> productIds, int width, int height, String directConnectionEncryptedInfo);

    /**
     * Attaches a previously-uploaded cover photo artefact to the
     * authenticated user's WhatsApp Business profile.
     *
     * <p>Mirrors {@code WAWebBusinessProfileJob.sendCoverPhoto}. The
     * caller is expected to have already uploaded the JPEG to
     * WhatsApp's media servers via the media upload pipeline; this
     * method ships the resulting {@code (id, ts, token)} triple to
     * the relay so the profile is updated to reference the new
     * upload. The relay does not echo any payload back on success;
     * any error variant raises an exception so callers can rely on
     * normal return paths.
     *
     * @param id    the upload id returned by the media upload pipeline
     * @param ts    the non-{@code null} upload timestamp
     * @param token the opaque upload token issued by the media server;
     *              never {@code null}
     * @throws NullPointerException            if {@code ts} or {@code token}
     *                                         is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant, or no
     *                                         documented variant
     *                                         matched
     */
    void editBusinessCoverPhoto(long id, Instant ts, byte[] token);

    /**
     * Clears one or more server-tracked dirty-bit entries so the relay
     * stops re-asserting them on the next reconnection.
     *
     * <p>Mirrors {@code WAWebClearDirtyBitsJob.clearDirtyBits}. The
     * relay tracks per-resource "dirty since {@code timestamp}" markers
     * (e.g. {@code account_sync}, {@code groups}, {@code blocklist}) and
     * re-publishes them in info-bulletin stanzas until the client
     * acknowledges the high-water-mark timestamp. Each
     * {@code (type, timestamp)} pair becomes one {@code <clean/>} child
     * of the outbound IQ.
     *
     * <p>The relay does not echo any payload back on success; any error
     * variant raises an exception so callers can rely on normal return
     * paths.
     *
     * @param dirtyBits the {@code (type, timestamp)} entries to clear,
     *                  one per resource the client wants the relay to
     *                  stop reporting; never {@code null} and must be
     *                  non-empty
     * @throws NullPointerException            if {@code dirtyBits} is
     *                                         {@code null}
     * @throws IllegalArgumentException        if {@code dirtyBits} is
     *                                         empty
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant, or no documented
     *                                         variant matched
     */
    void clearDirtyBits(Map<String, Long> dirtyBits);

    /**
     * Fetches the per-device media-connection routing descriptor — the
     * authentication token, candidate CDN host list, and retry budgets
     * the client must obtain before uploading or downloading any
     * encrypted attachment.
     *
     * <p>Mirrors {@code WAWebQueryMediaConnsJob.queryMediaConn}. The
     * relay returns a single {@code <media_conn/>} envelope carrying
     * the auth token, the per-host endpoints with their accepted media
     * types and bucket assignments, the routes/auth time-to-live
     * deltas, and the manual / auto retry budgets. The reply is
     * projected onto the {@link MediaConnection} model so callers can
     * immediately call {@code upload} / {@code download} on the
     * returned instance.
     *
     * <p>Each successful query stamps a fresh {@code timestamp} on the
     * returned model; the caller is expected to consult
     * {@link MediaConnection#isExpired()} and
     * {@link MediaConnection#needsRefresh()} to decide when to re-query.
     *
     * @return the parsed media-connection descriptor; never
     *         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant, or no documented
     *                                         variant matched
     */
    MediaConnection queryMediaConns();

    /**
     * Acknowledges deletion of a single Terms-of-Service notice — tells
     * the relay that the user has dismissed a previously-published
     * notice that was not awaiting acceptance.
     *
     * <p>Mirrors {@code WAWebTosJob.deleteTosNotice}. The relay tracks
     * each pending legal-update / disclosure prompt by an opaque
     * identifier; this method ships the {@code <delete_notice id ...>}
     * IQ and waits for the relay's empty acknowledgement. Use
     * {@link #queryTosNotices(Collection)} first to learn
     * which notice ids are currently outstanding.
     *
     * @param noticeId the notice id to delete; never {@code null}
     * @throws NullPointerException            if {@code noticeId} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant, or no documented
     *                                         variant matched
     */
    void deleteTosNotice(String noticeId);

    /**
     * Acknowledges acceptance of one or more Terms-of-Service notices —
     * marks the user as having accepted each requested legal-update or
     * disclosure prompt, unlocking any feature gated on it.
     *
     * <p>Mirrors {@code WAWebTosJob.acceptTosNotice}. The IQ wraps one
     * {@code <notice id .../>} child per id; the relay returns an
     * empty acknowledgement on success. Use
     * {@link #queryTosNotices(Collection)} first to learn
     * which notice ids are currently outstanding and to verify
     * acceptance afterwards.
     *
     * @param noticeIds the notice ids being accepted; never
     *                  {@code null}, may be empty
     * @throws NullPointerException            if {@code noticeIds} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant, or no documented
     *                                         variant matched
     */
    void acknowledgeTosNotices(List<String> noticeIds);

    /**
     * Fetches the digest of the local Signal pre-key bundle held by
     * the relay.
     *
     * <p>Mirrors {@code WAWebDigestKeyJob.digestKey}. The relay returns
     * its current view of the local user's registration id, key-bundle
     * type marker, identity public key, signed pre-key, one-time
     * pre-key identifier list and a SHA-1 digest over the concatenated
     * material. Callers compare this digest against a locally
     * recomputed one and trigger a fresh upload via
     * {@link #uploadSignalPreKeys(SignalPreKeyBundle)} when they
     * diverge.
     *
     * @return an {@link Optional} carrying the parsed
     *         {@link IdentityKeyDigest}, or empty when the relay had
     *         no record on file
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant
     */
    Optional<IdentityKeyDigest> queryKeyDigest();

    /**
     * Fetches the long-term identity public keys for one or more
     * device JIDs.
     *
     * <p>Mirrors {@code WAWebGetIdentityKeysJob.getAndStoreIdentityKeys}.
     * The relay returns one entry per requested device, mixing
     * successfully-resolved keys and per-device error envelopes.
     * Failures (relay could not resolve the device) are silently
     * dropped from the returned map; callers that need to inspect
     * per-device failures should drop down to the typed
     * {@link IqGetIdentityKeysResponse} via
     * {@link #sendNode(IqOperation.Request)}.
     *
     * @param deviceJids the devices whose identity keys to fetch;
     *                   never {@code null} and never empty
     * @return the per-device identity key map, keyed by JID
     * @throws NullPointerException            if {@code deviceJids}
     *                                         is {@code null}
     * @throws IllegalArgumentException        if {@code deviceJids}
     *                                         is empty
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant
     */
    Map<Jid, IdentityKey> queryIdentityKeys(List<? extends JidProvider> deviceJids);

    /**
     * Rotates the local signed pre-key by uploading a freshly-minted
     * one to the relay.
     *
     * <p>Mirrors {@code WAWebRotateKeyJob.rotateKey}. The relay
     * replaces the previous signed pre-key with the supplied one;
     * subsequent inbound Signal sessions will use the new key for the
     * shared-secret derivation. WA Web rotates the signed pre-key on
     * a schedule (typically every few days).
     *
     * @param signedPreKey the freshly-minted signed pre-key; never
     *                     {@code null}
     * @throws NullPointerException            if {@code signedPreKey}
     *                                         is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant, or no
     *                                         documented variant
     *                                         matched
     */
    void rotateSignedPreKey(SignalSignedPreKey signedPreKey);

    /**
     * Uploads a fresh batch of one-time pre-keys (and the current
     * signed pre-key) to the relay.
     *
     * <p>Mirrors {@code WAWebUploadPreKeysJob.uploadPreKeys}. Used
     * after the registration handshake to replenish the relay-side
     * one-time pre-key pool — the relay hands one out to every
     * remote device that wants to start a Signal session with the
     * local user, so the pool drains over time.
     *
     * @param bundle the pre-key bundle to upload; never {@code null}
     * @throws NullPointerException            if {@code bundle} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant, or no
     *                                         documented variant
     *                                         matched
     */
    void uploadSignalPreKeys(SignalPreKeyBundle bundle);

    /**
     * Uploads the registration-time pre-key bundle to the relay.
     *
     * <p>Mirrors {@code WAWebUploadPrekeysForRegTask.uploadPrekeysForReg}.
     * Distinct from {@link #uploadSignalPreKeys(SignalPreKeyBundle)}:
     * the registration-time path runs once during the device
     * registration handshake; the post-registration path runs every
     * time the local one-time pre-key pool needs replenishing. The
     * payload shape is identical, but the wire envelope is a separate
     * IQ pair.
     *
     * @param bundle the pre-key bundle to upload; never {@code null}
     * @throws NullPointerException            if {@code bundle} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant, or no
     *                                         documented variant
     *                                         matched
     */
    void uploadRegistrationPreKeys(SignalPreKeyBundle bundle);

    /**
     * Issues a private-stats anonymous credential by performing the
     * blind-sign IQ round-trip against the relay.
     *
     * <p>Mirrors {@code WAWebPrivateStatsIssueTokenJob.issuePrivateStatsToken}.
     * The caller passes a blinded elliptic-curve point (32 bytes) and
     * a project-name tag (UTF-8 bytes) scoping the credential to a
     * particular collector. The relay signs the blinded point with a
     * project-specific private key and returns the signed point, the
     * matching ACS public key, the DLEQ proof coordinates, and the
     * mint timestamp. Callers then unblind the signed point to obtain
     * the redeemable token.
     *
     * @param blindedCredential the blinded elliptic-curve point bytes;
     *                          never {@code null}
     * @param projectName       the UTF-8 project-name bytes; never
     *                          {@code null}
     * @return an {@link Optional} carrying the parsed
     *         {@link PrivateStatsToken}, or empty when the relay
     *         response did not match any documented variant
     * @throws NullPointerException            if any reference
     *                                         argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant
     */
    Optional<PrivateStatsToken> issuePrivateStatsToken(byte[] blindedCredential, byte[] projectName);

    /**
     * Performs a SyncD app-state server-sync round trip.
     *
     * <p>Mirrors {@code WAWebSyncdServerSync.serverSync}. Each
     * {@link AppStateSyncCollection} entry asks the relay for either
     * a snapshot (when the locally-known version is empty) or for the
     * patches above the known version. Entries that ship local
     * mutations attach the encoded {@code SyncdPatch} bytes via
     * {@link AppStateSyncCollection#patch()}.
     *
     * <p>The returned {@link AppStateSyncResult} carries one
     * {@link AppStateSyncCollectionResult} per requested collection
     * — callers iterate the list to drive their per-collection retry
     * / reconcile / snapshot-fetch logic.
     *
     * @param collections the per-collection entries; never
     *                    {@code null}, possibly empty
     * @return the parsed sync result; never {@code null}
     * @throws NullPointerException            if {@code collections}
     *                                         is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  if the relay returned a
     *                                         client- or server-error
     *                                         variant, or no
     *                                         documented variant
     *                                         matched
     */
    AppStateSyncResult syncAppState(List<AppStateSyncCollection> collections);

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

    /**
     * Acknowledges that the client has fully ingested a group's metadata
     * and message history snapshot.
     *
     * <p>Sent after the local store has consumed the {@code <dirty/>}
     * notification carrying the group's pending updates. Receiving the
     * acknowledgement causes the relay to advance the per-group dirty
     * cursor so that the next reconnect will not redeliver the same
     * notification. The relay's {@code Success} reply carries no payload
     * beyond the IQ envelope echo, so this method returns no result; an
     * unsuccessful response is surfaced as
     * {@link WhatsAppServerRuntimeException}.
     *
     * @param group the group JID being acknowledged; never {@code null}
     * @throws NullPointerException            if {@code group} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    void acknowledgeGroup(JidProvider group);

    /**
     * Accepts a pending group-add invite, returning whether the relay
     * routed the accept into the membership-approval queue rather than
     * admitting the caller outright.
     *
     * <p>Used when the relay invited the caller to a group whose
     * membership-approval mode is {@code request_required}: the
     * accept may either commit the participant immediately (the relay
     * returns the {@code Success} variant) or queue the request for an
     * admin to review (the relay returns the
     * {@code GroupJoinRequestSuccess} variant). Both variants carry no
     * payload beyond the IQ envelope echo plus a discriminator child
     * marker, so the only useful caller-visible bit is whether the join
     * is pending approval.
     *
     * @param group            the target group JID; never {@code null}
     * @param acceptCode       the invite code carried in the original
     *                         add notification; never {@code null}
     * @param acceptExpiration the invite expiration timestamp echoed in
     *                         the accept payload
     * @param acceptAdmin      the inviting admin's JID; never {@code null}
     * @return {@code true} when the relay routed the accept into the
     *         pending-approval queue (the caller is now waiting for an
     *         admin to act on the request), {@code false} when the
     *         relay admitted the caller as a regular participant
     *         immediately
     * @throws NullPointerException            if any required argument is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    boolean acceptGroupAdd(GroupAddAccept accept);

    /**
     * Queries metadata for many groups in one round trip.
     *
     * <p>Used by sync paths that need to refresh metadata for several
     * groups simultaneously — for example, after a long disconnect when
     * many per-group dirty bits accumulated. The relay's response
     * carries one {@code <group/>} child per requested JID; each child
     * is one of four shapes (full {@code group_info}, truncated
     * {@code group_info}, {@code group_forbidden} marker, or
     * {@code group_not_exist} marker). Successfully resolvable entries
     * are surfaced in the returned list; {@code group_forbidden} and
     * {@code group_not_exist} markers are silently skipped because they
     * carry no metadata beyond the requested JID.
     *
     * @param groups the group JIDs being queried; never {@code null}
     * @return an unmodifiable list of {@link GroupMetadata} entries —
     *         one per group that the relay was able to project, in the
     *         relay's reply order; empty when none of the requested
     *         groups could be resolved
     * @throws NullPointerException            if {@code groups} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    List<GroupMetadata> batchQueryGroupInfo(Collection<? extends JidProvider> groups);

    /**
     * Cancels one or more pending self-issued membership-approval
     * requests, returning a per-applicant outcome map.
     *
     * <p>Sent when the local user — having previously asked to join a
     * closed group or community sub-group — withdraws the request
     * before an admin acts on it. Each applicant the relay successfully
     * removes from the pending queue maps to
     * {@link GroupParticipantStatus#OK}; applicants the relay refused
     * to cancel map to {@link GroupParticipantStatus#NOT_AUTHORIZED}
     * (the caller does not own the targeted request and lacks admin
     * rights) or {@link GroupParticipantStatus#NOT_WHATSAPP_USER} (the
     * relay could not find a pending request matching the supplied
     * applicant; the {@code 404} arm of the WA Web disjunction is
     * surfaced as the {@code NOT_WHATSAPP_USER} status because it
     * shares the same numeric code).
     *
     * @param group      the target group JID; never {@code null}
     * @param applicants the participant JIDs whose pending requests are
     *                   being cancelled; never {@code null}
     * @return an unmodifiable map keyed by applicant JID, in the
     *         relay's reply order, mapping to the per-applicant outcome
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    Map<Jid, GroupParticipantStatus> cancelGroupMembershipRequests(JidProvider group, Collection<? extends JidProvider> applicants);

    /**
     * Suggests a brand-new sub-group be created under a parent community
     * and returns the relay's projection of the freshly reserved
     * sub-group.
     *
     * <p>Used by the community admin tooling: the admin describes a
     * sub-group that does not yet exist; the relay reserves a new JID
     * and returns its provisional metadata (jid, creator, creation
     * timestamp, optional creator phone number, and an optional
     * description-error string when the relay could not accept the
     * supplied description verbatim).
     *
     * @param community    the parent community JID; never {@code null}
     * @param subject      the proposed sub-group's display name; never
     *                     {@code null}
     * @param description  optional description body; may be {@code null}
     * @param locked       when {@code true} the suggested sub-group's
     *                     chat-info edits are admin-only
     * @param announcement when {@code true} only admins may post in the
     *                     suggested sub-group
     * @param hiddenGroup  when {@code true} the suggested sub-group is
     *                     hidden from the community directory
     * @return the suggestion result, always carrying
     *         {@link SubgroupSuggestionResult.Kind#NEW_GROUP}
     * @throws NullPointerException            if any non-nullable
     *                                         argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    SubgroupSuggestionResult suggestNewSubgroup(SubgroupSuggestionNew suggestion);

    /**
     * Suggests one or more pre-existing groups be linked into a parent
     * community as sub-groups, returning the per-candidate outcome.
     *
     * <p>Sister of
     * {@link #suggestNewSubgroup(Jid, String, String, boolean, boolean, boolean)}
     * — used when the admin wants to recommend that already-existing
     * groups be folded into the community rather than spinning up a
     * fresh one. The relay validates each candidate against the
     * community's policies and surfaces a per-candidate result row;
     * candidates the relay rejects carry a
     * {@link SubgroupSuggestionResult.Candidate.Reason} discriminator
     * pinpointing the rejection arm.
     *
     * @param community       the parent community JID; never
     *                        {@code null}
     * @param candidateGroups the JIDs of the candidate sub-groups;
     *                        never {@code null} and must contain at
     *                        least one entry
     * @return the suggestion result, always carrying
     *         {@link SubgroupSuggestionResult.Kind#EXISTING_GROUPS}
     * @throws NullPointerException            if any non-nullable
     *                                         argument is {@code null}
     * @throws IllegalArgumentException        if {@code candidateGroups}
     *                                         is empty
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    SubgroupSuggestionResult suggestExistingSubgroups(JidProvider community, Collection<? extends JidProvider> candidateGroups);

    /**
     * Deactivates (deletes) a community parent group.
     *
     * <p>Equivalent in effect to deactivating the entire community —
     * every linked sub-group is unlinked and converted back into a
     * standalone group. The relay's {@code Success} reply carries no
     * payload beyond the IQ envelope echo, so this method returns no
     * result; an unsuccessful response is surfaced as
     * {@link WhatsAppServerRuntimeException}.
     *
     * @param community the parent community JID; never {@code null}
     * @throws NullPointerException            if {@code community} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    Optional<GroupMetadata> deleteParentGroup(JidProvider community);

    /**
     * Queries the profile pictures for many groups in one batch.
     *
     * <p>Backs the community-roster and chat-list flows that need to
     * surface fresh profile pictures for several groups simultaneously.
     * The relay returns one entry per requested group; each entry is
     * either a URL projection (carrying the directly-usable download
     * URL plus the media-server direct path), a blob projection
     * (carrying the picture bytes inline), or a marker indicating that
     * the picture was unchanged or absent. All three branches are
     * unified by the {@link GroupProfilePicture} model — a successful
     * URL entry populates {@link GroupProfilePicture#url()} and
     * {@link GroupProfilePicture#directPath()}, a successful blob entry
     * populates {@link GroupProfilePicture#blob()}, and a marker entry
     * populates none of them.
     *
     * @param groups the group JIDs being queried; never {@code null}
     * @return an unmodifiable list of {@link GroupProfilePicture}
     *         entries, in the relay's reply order
     * @throws NullPointerException            if {@code groups} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    List<GroupProfilePicture> queryGroupProfilePictures(Collection<? extends JidProvider> groups);

    /**
     * Queries the relay's preview of the group identified by an invite
     * code, returning the group's metadata snapshot without actually
     * joining it.
     *
     * <p>Used by the "preview group before joining" UI flow: the
     * caller supplies an invite code that arrived via a deep link, the
     * relay validates it and projects the
     * {@code InviteLinkGroupInfoMixin} subtree (subject, picture,
     * owner, admins, ephemeral state, etc.). The reply also carries
     * the relay-reported {@code size} attribute, which is folded into
     * the resulting {@link GroupMetadata#size()}.
     *
     * @param inviteCode the invite code; never {@code null}
     * @return an {@link Optional} carrying the parsed metadata, or
     *         empty when the relay could not produce a {@code <group>}
     *         subtree (typically because the invite code does not map
     *         to a group)
     * @throws NullPointerException            if {@code inviteCode} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    Optional<GroupMetadata> queryInviteGroupInfo(String inviteCode);

    /**
     * Resolves a single linked-group entry within a parent community,
     * returning its full metadata.
     *
     * <p>Used to back the "go to parent community" or "go to general
     * sub-group" navigation actions in the chat UI. The
     * {@code queryLinkedType} discriminator tells the relay which
     * linked-group projection to return:
     * <ul>
     *   <li>{@code "parent"} — the parent community itself;</li>
     *   <li>{@code "general"} — the community's general-chat
     *   sub-group;</li>
     *   <li>{@code "sub_group"} — the specific sub-group identified by
     *   {@code queryLinkedJid}.</li>
     * </ul>
     *
     * @param community       the parent community JID; never
     *                        {@code null}
     * @param queryLinkedType the linked-type discriminator; never
     *                        {@code null}
     * @param queryLinkedJid  the specific linked-group JID, required
     *                        when {@code queryLinkedType} is
     *                        {@code "sub_group"}; may be {@code null}
     *                        for the {@code "parent"} and
     *                        {@code "general"} discriminators
     * @return an {@link Optional} carrying the parsed metadata —
     *         either {@link GroupMetadata} for a sub-group projection
     *         or {@link CommunityMetadata}
     *         for the parent-community projection — or empty when the
     *         relay returned no resolvable {@code <group>} subtree
     * @throws NullPointerException            if any required argument
     *                                         is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    Optional<ChatMetadata> queryLinkedGroup(JidProvider community, String queryLinkedType, JidProvider queryLinkedJid);

    /**
     * Returns the union of participants across every group linked to a
     * parent community.
     *
     * <p>Backs the "@all" mention flow inside community sub-groups,
     * where the mentioning UI must display the union of every
     * sub-group participant. Each participant is surfaced with its
     * primary JID; the relay-supplied phone-number JID echo (when LID
     * to phone-number mapping is available) is folded into the
     * returned {@code Map}, which maps each participant's primary JID
     * to its resolved phone-number JID, or to {@code null} when the
     * relay omitted the resolution.
     *
     * @param community the parent community JID; never {@code null}
     * @return an unmodifiable map keyed by the participant's primary
     *         JID, in the relay's reply order, mapping to the
     *         resolved phone-number JID or to {@code null} when the
     *         relay did not perform LID-to-PN resolution
     * @throws NullPointerException            if {@code community} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    Map<Jid, Jid> queryLinkedGroupsParticipants(JidProvider community);

    /**
     * Returns the list of pending membership-approval requests for a
     * group administrator to review.
     *
     * <p>Backs the "Pending requests" tab in the group-info UI. Each
     * entry captures the requesting user's identity, the optional
     * resolved phone-number JID and username, the parent-community JID
     * for community-link join requests, the request timestamp, and the
     * pathway through which the request was filed
     * ({@link GroupMembershipApprovalRequest.Method}).
     *
     * @param group the target group JID; never {@code null}
     * @return an unmodifiable list of pending requests, in the relay's
     *         reply order
     * @throws NullPointerException            if {@code group} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    List<GroupMembershipApprovalRequest> queryGroupMembershipApprovalRequests(JidProvider group);

    /**
     * Returns the relay's snapshot of every group the caller currently
     * participates in.
     *
     * <p>Used by initial-sync paths that bootstrap the local chat list
     * when the persistent store is empty. Each {@code <group>} child of
     * the relay's reply is one of two shapes — full {@code group_info}
     * or truncated {@code group_info} — and is mapped to a
     * {@link GroupMetadata} entry through the same parser used for
     * unsolicited group-metadata IQ responses.
     *
     * @param includeParticipants whether the response should include the
     *                            per-group participant edges
     * @param includeDescription  whether the response should include the
     *                            per-group description text
     * @return an unmodifiable list of {@link GroupMetadata} entries —
     *         one per participating group, in the relay's reply order
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    List<GroupMetadata> queryParticipatingGroups(boolean includeParticipants, boolean includeDescription);

    /**
     * Returns the list of in-group messages that members previously
     * reported to the group's administrators.
     *
     * <p>Backs the "View previously reported messages" admin view, which
     * lists each report alongside its reporters and the timestamp at
     * which the report was filed. The relay deduplicates reports by
     * offending stanza id, so a single message that several members
     * reported is surfaced as one {@link GroupMessageReport} entry
     * carrying multiple {@link GroupMessageReport.Reporter} rows.
     *
     * @param group the target group JID; never {@code null}
     * @return an unmodifiable list of {@link GroupMessageReport}
     *         entries, in the relay's reply order
     * @throws NullPointerException            if {@code group} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    List<GroupMessageReport> queryReportedMessages(JidProvider group);

    /**
     * Joins a community sub-group, returning whether the relay routed
     * the join into the membership-approval queue rather than admitting
     * the caller outright.
     *
     * <p>Backs the "Join sub-group" flow inside community navigation —
     * the relay decides whether to add the caller directly (returning
     * the {@code Success} variant) or enqueue a request for an admin
     * to act on (returning the {@code GroupJoinRequestSuccess} variant)
     * based on the sub-group's membership-approval mode. Both variants
     * carry no payload beyond the IQ envelope echo plus a discriminator
     * marker, so the only useful caller-visible bit is whether the
     * join is pending approval.
     *
     * @param community           the parent community JID; never
     *                            {@code null}
     * @param subgroup            the sub-group JID being joined; never
     *                            {@code null}
     * @param joinLinkedGroupType the join-type discriminator carried
     *                            in the request payload; never
     *                            {@code null}
     * @return {@code true} when the relay routed the join into the
     *         pending-approval queue (the caller is now waiting for
     *         an admin to act on the request), {@code false} when the
     *         relay admitted the caller as a regular participant
     *         immediately
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    boolean joinLinkedGroup(JidProvider community, JidProvider subgroup, String joinLinkedGroupType);

    /**
     * Links one or more existing groups under a community parent and
     * returns the per-sub-group outcome.
     *
     * <p>Backs the community-admin "Add existing group" flow that
     * promotes a standalone group into a community sub-group. Each
     * sub-group is linked with its directory-visible flag set; callers
     * needing the {@code <hidden_group/>} marker should construct the
     * underlying {@link SmaxGroupsLinkSubGroupsRequest} directly.
     *
     * <p>Linking a sub-group implicitly transfers its members into the
     * parent community as well. Members whose privacy settings forbid
     * the implicit add are reported back through
     * {@link LinkedSubgroupResult#participantErrors()} keyed by their
     * JID; the relay always emits {@code "403"} as the per-participant
     * error code.
     *
     * @param community the parent community JID; never {@code null}
     * @param subgroups the sub-group JIDs being linked; never
     *                  {@code null}
     * @return an unmodifiable list of {@link LinkedSubgroupResult}
     *         entries, in the relay's reply order
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    List<LinkedSubgroupResult> linkSubgroups(JidProvider community, List<? extends JidProvider> subgroups);

    /**
     * Reports an in-group message to WhatsApp moderation.
     *
     * <p>Backs the "Report message" UI action inside groups: the
     * report is forwarded to the upstream moderation pipeline. The
     * relay's {@code Success} reply carries no payload beyond the IQ
     * envelope echo, so this method returns no result; an unsuccessful
     * response is surfaced as
     * {@link WhatsAppServerRuntimeException}.
     *
     * @param group     the target group JID; never {@code null}
     * @param messageId the server id of the offending message; never
     *                  {@code null}
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    void reportGroupMessages(JidProvider group, String messageId);

    /**
     * Revokes the per-participant invite codes used by closed groups,
     * returning the per-participant outcome map.
     *
     * <p>Backs the admin "Reset invite link" action when running on a
     * group whose invite mode is the per-participant
     * {@code request_required} variant. Each participant the relay
     * successfully revoked maps to {@link GroupParticipantStatus#OK};
     * participants whose request code could not be found map to
     * {@link GroupParticipantStatus#NOT_WHATSAPP_USER} (the
     * {@code "404"} arm of the WA Web disjunction is surfaced here
     * because it shares the same numeric code).
     *
     * @param group        the target group JID; never {@code null}
     * @param participants the per-participant JIDs whose request codes
     *                     are being revoked; never {@code null}
     * @return an unmodifiable map keyed by the participant JID, in the
     *         relay's reply order, mapping to the per-participant
     *         outcome
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    Map<Jid, GroupParticipantStatus> revokeGroupRequestCode(JidProvider group, List<? extends JidProvider> participants);

    /**
     * Applies a batch of edits to a WhatsApp group's metadata, covering
     * subject and description rewrites, profile-picture updates and
     * removals, binary property toggles (locked / announcement /
     * ephemeral / membership-approval / etc.) and the local-only
     * {@code statusMuted} sync flag.
     *
     * <p>Every optional field on {@link GroupMetadataEdit} that carries
     * a present value drives a single mutation:
     * <ul>
     *   <li>{@link GroupMetadataEdit#subject() subject} present — dispatches a
     *       {@code w:g2} {@code <subject>} {@code iq} of type {@code set}.</li>
     *   <li>{@link GroupMetadataEdit#description() description} present —
     *       dispatches a {@code w:g2} {@code <description>} {@code iq} of
     *       type {@code set}: a
     *       {@link com.github.auties00.cobalt.model.chat.group.GroupDescription.Set Set}
     *       variant emits a {@code <body>NEW</body>} child while a
     *       {@link com.github.auties00.cobalt.model.chat.group.GroupDescription.Clear Clear}
     *       variant emits the {@code delete="true"} attribute matching
     *       WA Web's {@code hasDescriptionDeleteTrue:!0} branch.</li>
     *   <li>{@link GroupMetadataEdit#picture() picture} present — dispatches a
     *       {@code w:profile:picture} {@code iq} of type {@code set}: a
     *       {@link com.github.auties00.cobalt.model.chat.group.GroupPicture.Set Set}
     *       variant carries the picture bytes (the relay-assigned
     *       picture identifier is discarded — this method has no return
     *       slot for it), while a
     *       {@link com.github.auties00.cobalt.model.chat.group.GroupPicture.Clear Clear}
     *       variant emits the no-body removal IQ.</li>
     *   <li>Each {@link GroupMetadataEdit#locked() locked} /
     *       {@link GroupMetadataEdit#unlocked() unlocked} /
     *       {@link GroupMetadataEdit#announcement() announcement} pair (and
     *       siblings) emits the corresponding settings toggle through the
     *       {@code WASmaxGroupsSetPropertyRPC} pipeline; the toggles are
     *       batched into a single IQ when at least one is set.</li>
     *   <li>Each
     *       {@link GroupMetadataEdit#limitSharingEnabled() limitSharing} /
     *       {@link GroupMetadataEdit#memberAddAdminOnly() memberAdd} /
     *       {@link GroupMetadataEdit#memberLinkAdminOnly() memberLink} /
     *       {@link GroupMetadataEdit#memberShareGroupHistoryAdminOnly() memberShareGroupHistory}
     *       and
     *       {@link GroupMetadataEdit#allowNonAdminSubGroupCreation() allowNonAdminSubGroupCreation}
     *       pair is dispatched through the
     *       {@code WAWebMexUpdateGroupPropertyJob} GraphQL endpoint. The
     *       {@code limitSharing} pair commits a
     *       {@code LimitSharingSettingUpdateWamEvent} and the
     *       {@code allowNonAdminSubGroupCreation} pair commits a
     *       {@code CommunityGroupJourneyEvent} after the mutation
     *       returns.</li>
     *   <li>{@link GroupMetadataEdit#ephemeralExpiration() ephemeralExpiration}
     *       present or {@link GroupMetadataEdit#notEphemeral() notEphemeral}
     *       {@code true} — routed through
     *       {@link #editEphemeralTimer(JidProvider, ChatEphemeralTimer)},
     *       which dispatches the {@code WASmaxGroupsSetPropertyRPC} IQ
     *       plus the in-memory chat-ephemerality state update and the
     *       {@code EphemeralSettingChangeWamEvent} commit.</li>
     *   <li>{@link GroupMetadataEdit#statusMuted() statusMuted} present — merges
     *       the value into the in-memory {@link GroupMetadata#statusMuted()
     *       statusMuted} field on the stored row, without producing a
     *       network packet. This is the local-apply path from
     *       {@code WAWebUserStatusMuteSync.applyMutations}.</li>
     * </ul>
     *
     * <p>The returned {@link Optional} carries the updated
     * {@link GroupMetadata} row from the store when the target group is
     * known. When the store has no record of the group, this method
     * returns {@link Optional#empty()} after the relay calls (if any)
     * have completed, matching the partial-state semantics of the
     * previous granular methods.
     *
     * @param edit the edit packet; never {@code null}
     * @return an {@link Optional} carrying the post-edit
     *         {@link GroupMetadata}, or empty when the group is not in
     *         the store
     * @throws NullPointerException            if {@code edit} is
     *                                         {@code null}
     * @throws IllegalArgumentException        if the JID is not a
     *                                         group/community
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    Optional<GroupMetadata> editGroupMetadata(GroupMetadataEdit edit);

    /**
     * Detaches one or more sub-groups from their parent community,
     * returning the per-sub-group outcome.
     *
     * <p>Inverse of {@link #linkSubgroups(Jid, List)} — sub-groups the
     * relay successfully detached become standalone groups again. Each
     * candidate maps to an {@link UnlinkedSubgroupResult} carrying the
     * sub-group's JID, whether the relay echoed the
     * {@code remove_orphaned_members="true"} attribute, and an
     * optional {@link UnlinkedSubgroupResult.Reason} discriminator
     * pinpointing why the candidate failed to detach (if it did).
     *
     * @param community the parent community JID; never {@code null}
     * @param subgroups the sub-group JIDs being detached; never
     *                  {@code null}
     * @return an unmodifiable list of {@link UnlinkedSubgroupResult}
     *         entries, in the relay's reply order
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError}
     *                                         response, or when the
     *                                         response stanza could not
     *                                         be parsed against any of
     *                                         the documented variants
     */
    List<UnlinkedSubgroupResult> unlinkSubgroups(JidProvider community, List<? extends JidProvider> subgroups);

    /**
     * Convenience overload of
     * {@link #queryNewsletterMessageUpdates(JidProvider, int, Long, NewsletterHistoryDirection)}
     * for a cold fetch with no {@code since} watermark.
     *
     * @param newsletter the newsletter
     * @param count      the per-call cap
     * @param direction  the cursor direction (Before/After pivot)
     * @return the message-history page
     * @throws NullPointerException if any argument is {@code null}
     */
    NewsletterMessageHistory queryNewsletterMessageUpdates(JidProvider newsletter, int count, NewsletterHistoryDirection direction);

    /**
     * Returns the windowed list of message-update deltas applied to a
     * newsletter since the supplied reference timestamp.
     *
     * <p>Used by the newsletter sync path that backfills the local
     * message store after a reconnect — the update stream carries
     * only the per-message deltas (edits, reactions, deletes) rather
     * than full message bodies.
     *
     * @param newsletter the newsletter JID being queried; never {@code null}
     * @param count      the per-call cap; must be non-negative
     * @param since      the reference timestamp delta-cursor; may be {@code null}
     * @param direction  the optional pagination cursor; may be {@code null}
     * @return the message-update slice; never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError} or
     *                                         {@code ServerError} or
     *                                         the envelope did not
     *                                         parse
     */
    NewsletterMessageHistory queryNewsletterMessageUpdates(JidProvider newsletter, int count, Long since, NewsletterHistoryDirection direction);

    /**
     * Returns a windowed page of newsletter message envelopes,
     * addressing the newsletter by its JID.
     *
     * <p>Convenience for
     * {@link #queryNewsletterMessages(Jid, int, String, NewsletterHistoryDirection)}
     * that omits the optional view-role and pagination cursor.
     *
     * @param newsletter the newsletter JID; never {@code null}
     * @param count      the per-call cap; must be non-negative
     * @return the message-history page; never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    NewsletterMessageHistory queryNewsletterMessages(JidProvider newsletter, int count);

    /**
     * Returns a windowed page of newsletter message envelopes,
     * addressing the newsletter by its JID, with an optional view-role
     * projection and pagination cursor.
     *
     * <p>Dispatches {@code WASmaxNewslettersGetNewsletterMessagesRPC}
     * with the {@code by_jid} selector; the {@code direction} argument
     * controls which side of the anchor is fetched.
     *
     * @param newsletter the newsletter JID; never {@code null}
     * @param count      the per-call cap; must be non-negative
     * @param viewRole   optional ACL projection role echoed in the
     *                   {@code <view_role>} attribute; may be {@code null}
     * @param direction  the pagination cursor; may be {@code null}
     * @return the message-history page; never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         {@code ClientError},
     *                                         {@code ServerError} or
     *                                         the envelope did not parse
     */
    NewsletterMessageHistory queryNewsletterMessages(JidProvider newsletter, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction);

    /**
     * Returns a windowed page of newsletter message envelopes,
     * addressing the newsletter by its public invite key.
     *
     * <p>Convenience for
     * {@link #queryNewsletterMessages(String, int, String, NewsletterHistoryDirection)}
     * that omits the optional view-role and pagination cursor.
     *
     * @param inviteKey the newsletter invite key; never {@code null}
     * @param count     the per-call cap; must be non-negative
     * @return the message-history page; never {@code null}
     * @throws NullPointerException            if {@code inviteKey} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    NewsletterMessageHistory queryNewsletterMessages(String inviteKey, int count);

    /**
     * Returns a windowed page of newsletter message envelopes,
     * addressing the newsletter by its public invite key, with an
     * optional view-role projection and pagination cursor.
     *
     * <p>Companion to
     * {@link #queryNewsletterMessages(Jid, int, String, NewsletterHistoryDirection)}
     * for clients holding only the public invite link instead of the JID
     * (e.g. an external referrer).
     *
     * @param inviteKey the newsletter invite key; never {@code null}
     * @param count     the per-call cap; must be non-negative
     * @param viewRole  optional ACL projection role; may be {@code null}
     * @param direction the pagination cursor; may be {@code null}
     * @return the message-history page; never {@code null}
     * @throws NullPointerException            if {@code inviteKey} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         {@code ClientError},
     *                                         {@code ServerError} or
     *                                         the envelope did not parse
     */
    NewsletterMessageHistory queryNewsletterMessages(String inviteKey, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction);

    /**
     * Convenience overload of
     * {@link #queryNewsletterResponses(JidProvider, long, int, String, NewsletterResponsesFilter, String)}
     * for the basic page-from-top case with no before-cursor, no filter and
     * no search text.
     *
     * @param newsletter      the newsletter JID
     * @param serverMessageId the question message id
     * @param count           the per-call cap
     * @return the question responses
     * @throws NullPointerException if {@code newsletter} is {@code null}
     */
    List<NewsletterQuestionResponse> queryNewsletterResponses(JidProvider newsletter, long questionResponsesServerId, int questionResponsesCount);

    /**
     * Fetches the responder slice for an interactive newsletter
     * question post.
     *
     * <p>Backs the newsletter-author UI that visualises the per-
     * subscriber response stream attached to a question post; each
     * row carries the responder identity, response timestamp and a
     * flag marking responses the question owner has already replied
     * to.
     *
     * @param newsletter               the newsletter JID; never {@code null}
     * @param questionResponsesServerId the server id of the question/poll message
     * @param questionResponsesCount    the per-call cap; must be non-negative
     * @param questionResponsesBefore   optional anchor for backwards pagination; may be {@code null}
     * @param filter                    optional filter discriminator; may be {@code null}
     * @param searchText                optional search string; may be {@code null}
     * @return the responder entries, in server order; never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError},
     *                                         {@code ServerError} or
     *                                         the envelope did not
     *                                         parse
     */
    List<NewsletterQuestionResponse> queryNewsletterResponses(JidProvider newsletter, long questionResponsesServerId, int questionResponsesCount, String questionResponsesBefore, NewsletterResponsesFilter filter, String searchText);

    /**
     * Convenience overload of
     * {@link #queryNewsletterStatusUpdates(JidProvider, int, Long, NewsletterHistoryDirection)}
     * for a cold fetch with no {@code since} watermark.
     *
     * @param newsletter the newsletter
     * @param count      the per-call cap
     * @param direction  the cursor direction (Before/After pivot)
     * @return the status-update slice
     * @throws NullPointerException if any argument is {@code null}
     */
    NewsletterStatusHistory queryNewsletterStatusUpdates(JidProvider newsletter, int count, NewsletterHistoryDirection direction);

    /**
     * Retrieves the windowed list of status-update deltas applied to a
     * newsletter since the supplied reference timestamp.
     *
     * <p>Equivalent to
     * {@link #queryNewsletterMessageUpdates(Jid, int, Long, NewsletterHistoryDirection)}
     * but scoped to status messages instead of regular messages.
     *
     * @param newsletter the newsletter JID; never {@code null}
     * @param count      the per-call cap; must be non-negative
     * @param since      the reference timestamp delta-cursor; may be {@code null}
     * @param direction  the optional pagination cursor; may be {@code null}
     * @return the status-update slice; never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError},
     *                                         {@code ServerError} or
     *                                         the envelope did not
     *                                         parse
     */
    NewsletterStatusHistory queryNewsletterStatusUpdates(JidProvider newsletter, int count, Long since, NewsletterHistoryDirection direction);

    /**
     * Fetches a windowed page of newsletter status envelopes,
     * addressing the newsletter by its JID.
     *
     * <p>Convenience for
     * {@link #queryNewsletterStatuses(Jid, int, String, NewsletterHistoryDirection)}
     * that omits the optional view-role and pagination cursor.
     *
     * @param newsletter the newsletter JID; never {@code null}
     * @param count      the per-call cap; must be non-negative
     * @return the status-history page; never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    NewsletterStatusHistory queryNewsletterStatuses(JidProvider newsletter, int count);

    /**
     * Fetches a windowed page of newsletter status envelopes,
     * addressing the newsletter by its JID, with an optional view-role
     * projection and pagination cursor.
     *
     * <p>Equivalent to
     * {@link #queryNewsletterMessages(Jid, int, String, NewsletterHistoryDirection)}
     * but scoped to status messages instead of regular messages.
     *
     * @param newsletter the newsletter JID; never {@code null}
     * @param count      the per-call cap; must be non-negative
     * @param viewRole   optional ACL projection role; may be {@code null}
     * @param direction  the pagination cursor; may be {@code null}
     * @return the status-history page; never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         {@code ClientError},
     *                                         {@code ServerError} or
     *                                         the envelope did not parse
     */
    NewsletterStatusHistory queryNewsletterStatuses(JidProvider newsletter, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction);

    /**
     * Fetches a windowed page of newsletter status envelopes,
     * addressing the newsletter by its public invite key.
     *
     * <p>Convenience for
     * {@link #queryNewsletterStatuses(String, int, String, NewsletterHistoryDirection)}
     * that omits the optional view-role and pagination cursor.
     *
     * @param inviteKey the newsletter invite key; never {@code null}
     * @param count     the per-call cap; must be non-negative
     * @return the status-history page; never {@code null}
     * @throws NullPointerException            if {@code inviteKey} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    NewsletterStatusHistory queryNewsletterStatuses(String inviteKey, int count);

    /**
     * Fetches a windowed page of newsletter status envelopes,
     * addressing the newsletter by its public invite key, with an
     * optional view-role projection and pagination cursor.
     *
     * <p>Companion to
     * {@link #queryNewsletterStatuses(Jid, int, String, NewsletterHistoryDirection)}
     * for clients holding only the public invite link instead of the JID.
     *
     * @param inviteKey the newsletter invite key; never {@code null}
     * @param count     the per-call cap; must be non-negative
     * @param viewRole  optional ACL projection role; may be {@code null}
     * @param direction the pagination cursor; may be {@code null}
     * @return the status-history page; never {@code null}
     * @throws NullPointerException            if {@code inviteKey} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned a
     *                                         {@code ClientError},
     *                                         {@code ServerError} or
     *                                         the envelope did not parse
     */
    NewsletterStatusHistory queryNewsletterStatuses(String inviteKey, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction);

    /**
     * Returns the caller's own contributions (reactions and poll
     * votes) on messages of a specific newsletter, grouped by
     * newsletter JID.
     *
     * <p>Used by the newsletter detail view to surface the "My
     * recent reactions" panel that pre-populates emoji selectors
     * with the caller's most-used reactions for that channel and to
     * highlight options the caller already voted for in newsletter
     * polls.
     *
     * @param limit      the per-call cap; must be non-negative
     * @param newsletter the newsletter JID; never {@code null}
     * @return a map keyed by newsletter JID, whose values are the
     *         per-message add-on entries; never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError},
     *                                         {@code ServerError} or
     *                                         the envelope did not
     *                                         parse
     */
    Map<Jid, List<NewsletterMyAddOn>> queryNewsletterMyAddOns(int limit, JidProvider newsletter);

    /**
     * Subscribes the connection to real-time updates for a specific
     * newsletter and returns the relay-chosen subscription duration.
     *
     * <p>Once acknowledged the relay starts pushing
     * {@code <notification type="newsletter">} stanzas — handled
     * elsewhere by
     * {@code SmaxNewslettersLiveUpdatesNotificationResponse} — that
     * carry the live message/status delta stream until the
     * returned duration elapses or the socket disconnects. The
     * caller is expected to refresh the subscription before the
     * returned duration runs out to keep the stream alive.
     *
     * @param newsletter the newsletter JID being subscribed to;
     *                   never {@code null}
     * @return the relay-chosen subscription duration (bounded by the
     *         relay to {@code [30s, 600s]}); never {@code null}
     * @throws NullPointerException            if {@code newsletter} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no
     *                                         longer open
     * @throws WhatsAppServerRuntimeException  when the relay returned
     *                                         a {@code ClientError},
     *                                         {@code ServerError} or
     *                                         the envelope did not
     *                                         parse
     */
    Duration subscribeToNewsletterLiveUpdates(JidProvider newsletter);

    /**
     * Fetches the latest A/B-experiment configuration bundle.
     *
     * <p>Useful for refreshing the global props cache: the relay either
     * echoes back the materialised props subtree (when the supplied hash
     * is stale or absent) or short-circuits to a delta when the cached
     * snapshot is already current.
     *
     * @param propsHash      the client's currently-cached props hash; may
     *                       be {@code null} on the first fetch
     * @param propsRefreshId the client's currently-cached refresh id; may
     *                       be {@code null} on the first fetch
     * @return an {@link Optional} carrying the parsed
     *         {@link AbPropsBundle}, or empty on no-parse
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<AbPropsBundle> queryExperimentConfig(String propsHash, Integer propsRefreshId);

    /**
     * Fetches the per-group A/B-experiment configuration.
     *
     * <p>Useful for refreshing group-scoped feature gates after the relay
     * pushes a {@code <notification type="abprops">} bump that names a
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
     * <p>Useful for refreshing the curated set of WhatsApp AI bots the
     * user can interact with. Digest-gated: the cached {@code bhash} is
     * echoed back so an unchanged listing transfers no payload.
     *
     * @param botV     the supported protocol revision (typically
     *                 {@code "2"} or {@code "3"}); may be {@code null}
     * @param botBhash the cached directory digest; may be {@code null}
     * @param botArgs  optional list of bot JIDs to scope the query to
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
     * <p>Useful for surfacing user-reported issues from the in-app
     * "Contact us" / "Report a problem" flow. Carries a free-form
     * description, debug-information JSON, an optional pre-uploaded
     * device-log handle, and an arbitrary number of pre-uploaded media
     * attachments.
     *
     * @param from                 the reporter's own JID
     * @param description          the user-supplied description
     * @param debugInformationJson the serialised debug-info blob
     * @param deviceLogHandle      optional handle for a pre-uploaded device log
     * @param mediaUploads         optional pre-uploaded attachments
     * @param title                optional report title
     * @param category             optional category code
     * @param clientServerJoinKey  optional ASL join key
     * @param reproducibility      optional reproducibility tag
     * @return an {@link Optional} carrying the backend-side task id
     *         assigned to the report, or empty when the relay omitted
     *         it
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     * @apiNote The {@code mediaUploads} parameter type still lives in
     *          {@code com.github.auties00.cobalt.node.smax.bugreporting}
     *          and is a value-tuple of pre-uploaded handle metadata.
     *          Flagged for follow-up: mirror it into
     *          {@code modules/model} when the broader bug-reporting
     *          domain model is designed.
     */
    Optional<String> reportBug(BugReport report);

    /**
     * Reports an InAppComms (in-app marketing) event to the relay.
     *
     * <p>Useful for attribution of in-app banner / promotion impressions,
     * clicks, and dismissals. The {@code logdata} field carries an opaque
     * JSON blob whose schema is owned by the promotion authoring tool.
     *
     * @param eventPromotionId  the promotion identifier
     * @param eventType         the event type code (e.g. impression)
     * @param eventTimestampSec the event time in seconds since the Unix epoch
     * @param eventLogdata      the opaque JSON payload
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void reportInAppCommsEvent(InAppCommsEvent event);

    /**
     * Acknowledges the boundary of an offline-batch backfill the server
     * just delivered.
     *
     * <p>While a client is offline the relay queues all stanzas that
     * would have been pushed to it; on reconnect those stanzas are
     * re-delivered as a single batch with a known size. After the
     * client has fully ingested every stanza in the batch, it must
     * call this method to advance the relay's offline-cursor — without
     * the acknowledgement the same batch is redelivered on the next
     * reconnect.
     *
     * <p>Fire-and-forget — the relay never replies.
     *
     * @param offlineBatchCount the number of stanzas the client has just
     *                          fully consumed (must match the size the
     *                          relay announced for this offline batch)
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void acknowledgeOfflineBatch(int offlineBatchCount);

    /**
     * Switches this client's WhatsApp session into <em>active</em> mode
     * — telling the relay this device is the user's primary
     * foregrounded surface and should receive immediate push of new
     * stanzas, presence updates, typing indicators, etc.
     *
     * <p>Active vs passive is the WhatsApp equivalent of "is this app
     * window in the foreground?". A multi-device session typically has
     * one device in active mode at a time; backgrounded companions stay
     * in passive mode (see {@link #enablePassiveMode()}) so the relay
     * can suppress non-essential pushes.
     *
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void enableActiveMode();

    /**
     * Switches this client's WhatsApp session into <em>passive</em> mode
     * — telling the relay this device is backgrounded so non-essential
     * pushes (typing indicators, presence updates) can be suspended
     * until the next call to {@link #enableActiveMode()}.
     *
     * <p>See {@link #enableActiveMode()} for the active/passive model.
     *
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void enablePassiveMode();

    /**
     * Fetches Signal pre-key bundles for the supplied users.
     *
     * <p>Useful before the local client can send messages to a peer it
     * has never communicated with — the bundle carries the recipient's
     * identity key, signed pre-key, and one-time pre-key needed to seed
     * a Signal session.
     *
     * @param users the non-{@code null} list of per-user requests
     * @return an {@link Optional} carrying the parsed
     *         {@link PreKeyBundleResult}, or empty on no-parse
     * @throws NullPointerException            if {@code users} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<PreKeyBundleResult> queryPreKeyBundles(List<PreKeyBundleRequest> users);

    /**
     * Fetches additional one-time pre-keys for users whose locally cached
     * bundles have been exhausted.
     *
     * <p>Useful when an outbound message would otherwise reuse a stale
     * pre-key; the relay returns just the missing one-time keys without
     * re-sending the long-lived identity material.
     *
     * @param users the non-{@code null} list of per-user requests
     * @return an {@link Optional} carrying the parsed
     *         {@link PreKeyBundleResult}, or empty on no-parse
     * @throws NullPointerException            if {@code users} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<PreKeyBundleResult> queryMissingPreKeys(List<MissingPreKeyUserRequest> users);

    /**
     * Submits a blinded credential token to the WhatsApp anonymous-
     * attribution signing service and returns the server-signed
     * counterpart.
     *
     * <p>"Private stats" is WhatsApp's anonymous-attribution telemetry
     * channel — the client blinds a token client-side, the relay's
     * signing service co-signs it without learning its contents, and
     * the client later unblinds the result to obtain an
     * <em>unlinkable</em> credential it can spend to attest to a stats
     * event without revealing its identity. This is the signing half
     * of that handshake.
     *
     * @param blindedCredentialElementValue the non-{@code null} blinded
     *                                      credential token to be signed
     * @param projectNameElementValue       the non-{@code null} signing
     *                                      project identifier (selects
     *                                      which signer key the server
     *                                      uses)
     * @return an {@link Optional} carrying the
     *         {@link SignedAttributionCredential} on success, or empty
     *         when the relay returned no parseable variant
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<SignedAttributionCredential> signAnonymousAttributionCredential(byte[] blindedCredentialElementValue, String projectNameElementValue);

    /**
     * Queries whether the local user has blocked Public Service
     * Announcement (PSA) chats.
     *
     * <p>PSAs are official chat blasts the WhatsApp team itself posts —
     * for example launch announcements, safety notices, or feature
     * tutorials. Each user can opt out of receiving them; this method
     * surfaces the current opt-out state so the UI can render the
     * correct toggle position.
     *
     * @return {@code true} when the relay reports the PSA channel is
     *         currently blocked (muted), {@code false} when active
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    boolean queryPublicAnnouncementBlocked();

    /**
     * Updates whether the local user is blocking Public Service
     * Announcement (PSA) chats. See {@link #queryPublicAnnouncementBlocked()}
     * for what PSAs are.
     *
     * @param blockingAction the non-{@code null} mutation verb to apply
     *                       to the PSA block list
     * @throws NullPointerException            if {@code blockingAction} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editPublicAnnouncementBlocked(PsaChatBlockAction blockingAction);

    /**
     * Updates the FCM / APNs / Web push / WNS / Enterprise / Facebook
     * push-notification configuration registered with the relay.
     *
     * <p>Useful at session bootstrap or when the OS-issued push token
     * rotates — submits either a fresh platform-specific configuration
     * or a {@link PushConfig.Clear} request that removes the device's
     * push registration so the relay stops sending wakeup pings.
     *
     * @param config the non-{@code null} push-config payload
     * @throws NullPointerException            if {@code config} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editPushConfig(PushConfig config);

    /**
     * Publishes a "view" receipt for one or more status posts the local
     * user just consumed.
     *
     * <p>Useful for driving the "Seen by" surface on the publisher's
     * side. Distinct from a regular read receipt because the wire shape
     * carries a batch of server-ids under a single posting.
     *
     * @param receiptId      the non-{@code null} receipt id
     * @param to             the non-{@code null} publisher JID
     * @param hasStatusClass {@code true} for the status namespace,
     *                       {@code false} for plain
     * @param itemServerIds  the non-{@code null} list of viewed server-ids
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void publishViewReceipt(ViewReceipt receipt);

    /**
     * Sends a buffered batch of low-priority client-side stats events to
     * the relay.
     *
     * <p>Useful as the counterpart of the WAM stream — the WhatsApp
     * client keeps a separate stats-buffer for events that are too cheap
     * to warrant standalone WAM events.
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
     * <p>Useful for the in-app "Send feedback" surface — surfaces a
     * quoted message id and a list of feedback kind tags so the support
     * team can triage.
     *
     * @param from          the non-{@code null} reporter JID
     * @param messageId     the non-{@code null} quoted message id
     * @param feedbackKinds the non-{@code null} list of feedback kind tags
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void sendSupportFeedback(JidProvider from, String messageId, List<String> feedbackKinds);

    /**
     * Submits a structured "Contact us" form to support.
     *
     * <p>Useful for the help-center contact flow — carries a topic,
     * optional topic id, debug-info JSON, optional uploaded log handle,
     * and an additional context-flow string for routing within the
     * support tooling.
     *
     * @param from                            the non-{@code null} reporter JID
     * @param description                     the non-{@code null} description
     * @param topic                           the non-{@code null} topic string
     * @param topicId                         optional topic id; may be {@code null}
     * @param debugInformationJson            optional debug-info JSON; may be {@code null}
     * @param uploadedLogsId                  optional pre-uploaded log handle; may be {@code null}
     * @param additionalAttributesContextFlow optional context flow; may be {@code null}
     * @return an {@link Optional} carrying the
     *         {@link SupportTicketAcknowledgement}, or empty on no-parse
     * @throws NullPointerException            if any required argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     *                                         (retryable or non-retryable)
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<SupportTicketAcknowledgement> sendSupportContactForm(SupportContactForm form);

    /**
     * Reports an individual contact for spam.
     *
     * <p>Useful for the in-app "Block and report" flow. Sends the
     * report to WhatsApp's trust-and-safety pipeline alongside the
     * caller-curated list of offending {@code <message>} stanza
     * identifiers so the moderator has the conversation context.
     *
     * <p>For the rare flows that need to attach FRX (free-form
     * reporting extension) payloads, biz opt-out / biz-report /
     * UI-state-set / TC-token children, or report calls and user-
     * initiated extensions, instantiate {@link SmaxIndividualReportRequest#builder()}
     * directly and dispatch via the lower-level {@link #sendNode(NodeBuilder)}.
     *
     * @param target              the reportee's JID; never {@code null}
     * @param spamFlow            the WhatsApp spam-flow code identifying which
     *                            user-visible report flow is being submitted;
     *                            never {@code null}
     * @param isKnownChat         optional marker — when non-{@code null} the
     *                            report carries the {@code is_known_chat}
     *                            attribute signalling whether the reporter has
     *                            previously chatted with the reportee
     * @param reportedMessageIds  zero or more reported {@code <message>} stanza
     *                            ids attached as evidence; never {@code null}
     * @throws NullPointerException            if any non-nullable argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void reportIndividualForSpam(IndividualSpamReport report);

    /**
     * Reports a group chat for spam.
     *
     * <p>Group-scoped sister of {@link #reportIndividualForSpam(Jid, String, String, List)}.
     * Includes the optional {@code adder} JID — the user who originally added the
     * reporter to the group, useful for surfacing add-spam patterns.
     *
     * <p>For the rare flows that need to attach FRX payloads or report
     * group calls, instantiate {@link SmaxGroupReportRequest#builder()}
     * directly and dispatch via {@link #sendNode(NodeBuilder)}.
     *
     * @param group               the reported group JID; never {@code null}
     * @param spamFlow            the WhatsApp spam-flow code; never {@code null}
     * @param adder               the JID of the user who added the reporter to
     *                            the group, or {@code null} if unknown
     * @param subject             the group subject string echoed for attribution
     *                            context, or {@code null}
     * @param isKnownChat         optional {@code is_known_chat} marker; may be {@code null}
     * @param reportedMessageIds  zero or more reported {@code <message>} stanza
     *                            ids attached as evidence; never {@code null}
     * @throws NullPointerException            if any non-nullable argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void reportGroupForSpam(GroupSpamReport report);

    /**
     * Reports a newsletter (channel) for spam.
     *
     * <p>Useful for surfacing newsletter abuse complaints — sends the
     * newsletter JID and a curated set of reported message receipts so
     * the trust-and-safety pipeline can attribute the complaint.
     *
     * @param spamListJid      the non-{@code null} newsletter JID
     * @param spamListSpamFlow the non-{@code null} spam-flow code
     * @param spamListSubject  optional subject; may be {@code null}
     * @param messages         the non-{@code null} list of reported messages
     * @throws NullPointerException            if any required argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     * @apiNote The {@code messages} parameter type still lives in
     *          {@code com.github.auties00.cobalt.node.smax.support}
     *          (a value-object pair of message id and timestamp).
     *          Flagged for follow-up: mirror it into
     *          {@code modules/model} when the broader spam-reporting
     *          domain model is designed.
     */
    void reportNewsletterForSpam(NewsletterSpamReport report);

    /**
     * Reports a status post for trust-and-safety review.
     *
     * <p>Accepts both regular chat statuses (broadcast on
     * {@code status@broadcast}) and newsletter statuses; the right
     * RPC is dispatched based on the runtime type of {@code status}.
     * The wire fields (target JID, message id or server id, timestamp)
     * are derived from the supplied {@link MessageInfo}, so callers
     * only need to supply the user-facing fields they would type into
     * WhatsApp: a {@code reason} code and an optional {@code subject}.
     *
     * @param status  the offending status post; never {@code null}
     * @param reason  the spam-flow code identifying the report flow;
     *                never {@code null}
     * @param subject optional free-text comment; may be {@code null}.
     *                Carried only for newsletter status reports;
     *                ignored for chat statuses since the v1 wire shape
     *                has no subject field
     * @throws NullPointerException            if {@code status} or
     *                                         {@code reason} is {@code null}
     * @throws IllegalArgumentException        if {@code status} carries no
     *                                         timestamp, sender, or
     *                                         message identifier
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void reportStatus(MessageInfo status, String reason, String subject);

    /**
     * Notifies the relay that this device wants to join a "unified
     * session" — WhatsApp's cross-device session-fan-out protocol that
     * shares a single logical chat session across multiple devices
     * identified by the same id.
     *
     * <p>Fire-and-forget. The relay uses the announcement to wire the
     * device into any in-flight cross-device sync the unified session
     * is participating in.
     *
     * @param unifiedSessionId the non-{@code null} cross-device session id
     * @throws NullPointerException            if {@code unifiedSessionId} is {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void joinUnifiedSession(String unifiedSessionId);

    /**
     * Fetches the pending Terms-of-Service / privacy-policy notices the
     * user has not yet acknowledged.
     *
     * <p>"User-notice disclosures" are the modal pop-ups WhatsApp shows
     * when the Terms of Service, privacy policy, or a regional
     * compliance notice changes — each entry carries the disclosure id
     * the client uses to record the dismissal once the user closes the
     * modal.
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
     * Fetches the per-stage acknowledgement state for an explicit list
     * of user-notice disclosure ids — useful after a refresh to verify
     * that the local cache matches the server's view of which Terms of
     * Service / privacy notices the user has acknowledged or dismissed.
     * See {@link #queryPendingUserNotices(long)} for what disclosures are.
     *
     * @param queries the non-{@code null} list of per-disclosure
     *                stage queries
     * @return an unmodifiable list of {@link UserNoticeStage} entries,
     *         empty when the relay returned no parseable variant
     * @throws NullPointerException            if {@code queries} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    List<UserNoticeStage> queryUserNoticeStages(List<UserNoticeStageQuery> queries);

    /**
     * Mints a fresh call link that can be shared to invite participants
     * into a multi-party call.
     *
     * <p>Mirrors WA Web's {@code createCallLinkJob} — the relay returns a
     * 22-character opaque token which Cobalt embeds into the canonical
     * {@code https://call.whatsapp.com/{voice|video}/{token}} URL exposed
     * by {@link CallLink#url()}. The token's media kind is fixed at
     * creation time and cannot be changed afterwards.
     *
     * <p>Optional parameters mirror the four wire-level attributes of the
     * {@code <link_create/>} payload: {@code linkCreateCallCreator} pins
     * the link to a specific creator device when the caller already knows
     * it; {@code linkCreateCallId} bundles the link with an in-flight call
     * rather than a fresh one; {@code linkCreateLinkCreatorUsername}
     * surfaces in the join-prompt UI; {@code eventStartTime} is set when
     * the link backs a scheduled-call event.
     *
     * @param media               the non-{@code null} media kind for the
     *                            link
     * @param callCreator         the optional creator device JID
     * @param callId              the optional in-flight call id
     * @param creatorUsername     the optional creator display username
     * @param waitingRoomEnabled  {@code true} to gate joins behind a
     *                            waiting room
     * @param eventStartTime      the optional scheduled-call start instant
     * @return an {@link Optional} carrying the freshly minted
     *         {@link CallLink}, or empty when the relay's reply could not
     *         be parsed
     * @throws NullPointerException            if {@code media} is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    CallLink createCallLink(CallLinkCreate create);

    /**
     * Queries the relay for the metadata behind an existing call link.
     *
     * <p>Mirrors the "Join via link" preview step in WA Web: the relay
     * resolves the supplied token and replies with the link's creator
     * device, optional creator phone-number JID, optional creator
     * username, and current waiting-room state. The reply also indicates
     * whether the link is bound to a scheduled-call event.
     *
     * <p>The {@code action} discriminator gates the kind of resolve: a
     * passive {@code "preview"} that surfaces metadata to a prospective
     * joiner, or {@code "edit"} for a creator-side metadata refresh. The
     * relay only emits the {@code <waiting_room/>} child for
     * {@code "edit"}-class queries.
     *
     * @param token  the non-{@code null} link token
     * @param media  the non-{@code null} media kind expected by the
     *               caller; must match the link's configured media or
     *               the relay will reject the query
     * @param action the non-{@code null} action discriminator (typically
     *               {@code "preview"} or {@code "edit"})
     * @return an {@link Optional} carrying the resolved {@link CallLink},
     *         or empty when the relay's reply could not be parsed
     * @throws NullPointerException            if any argument is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<CallLink> queryCallLink(String token, CallLinkMedia media, String action);

    /**
     * Toggles the waiting-room gate on an existing call link.
     *
     * <p>Mirrors WA Web's {@code toggleWaitingRoomForCallLink} job: the
     * relay either acknowledges the toggle or replies with a
     * {@code Nack}. WA Web translates the latter into a
     * {@code ServerStatusCodeError}; Cobalt raises the equivalent
     * {@link WhatsAppServerRuntimeException} so callers do not have to
     * inspect the wire-level reply themselves.
     *
     * <p>This call only changes the link's waiting-room state — it does
     * not affect any in-flight call session. Subsequent
     * {@link #queryCallLink(String, CallLinkMedia, String)} replies
     * surface the new state via
     * {@link CallLink#waitingRoom()}.
     *
     * @param enabled   {@code true} to enable the gate; {@code false} to
     *                  disable it. Encoded on the wire as {@code "1"} or
     *                  {@code "0"} respectively
     * @param linkToken the non-{@code null} link token
     * @param media     the non-{@code null} link media kind
     * @throws NullPointerException            if any reference argument
     *                                         is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    void editCallLinkWaitingRoom(boolean enabled, String linkToken, CallLinkMedia media);

    /**
     * Checks whether the local user already has an active federated-identity
     * ("Waffle") link to a Meta account, and returns the bridge's lifecycle
     * state for that link.
     *
     * <p>This is the first step of the Meta-SSO linking flow: if a non-zero
     * state already exists the client can skip directly to certificate fetch
     * via {@link #queryFederatedIdentityCertificate(long, boolean, boolean)};
     * if the relay surfaces a suspension marker the client must show the
     * appropriate recovery surface; if no state exists the client begins the
     * linking handshake from scratch.
     *
     * @param timestamp the non-{@code null} request timestamp
     * @return an {@link Optional} containing the {@link FederatedIdentityState}
     *         reported by the relay, or empty when the relay's response could
     *         not be parsed
     * @throws NullPointerException            if {@code timestamp} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<FederatedIdentityState> checkFederatedIdentityExists(Instant timestamp);

    /**
     * Pings the federated-identity (Waffle) bridge to keep an in-flight
     * enrolment alive, and surfaces the next ping cadence chosen by the
     * relay.
     *
     * @param encryption the non-{@code null} RSA-2048 encryption envelope
     *                   (kept typed because the four-blob payload is
     *                   mandatory and meaningful as a unit)
     * @param timestamp  the non-{@code null} request timestamp
     * @param fbid       the non-{@code null} encrypted Facebook id payload
     * @return an {@link Optional} containing the {@link FederatedIdentityPing}
     *         result reported by the relay, or empty when the relay's
     *         response could not be parsed
     * @throws NullPointerException            if {@code encryption},
     *                                         {@code timestamp} or
     *                                         {@code fbid} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<FederatedIdentityPing> sendFederatedIdentityPing(FederatedRsaEncryption encryption, Instant timestamp, byte[] fbid);

    /**
     * Fetches the federated-identity (Waffle) certificate bundle the client
     * uses to encrypt subsequent payloads to the Meta-side bridge and to
     * verify bridge-signed responses.
     *
     * <p>Up to three PEM bundles are returned, depending on which of the
     * request flags is set: an encryption PEM (always available), a
     * signature PEM (always available), and the password PEM (only when
     * {@code hasPasswordPem} is {@code true}). The {@code
     * hasPayloadEncCertificates} flag selects whether the encryption and
     * signature PEMs are returned at all.
     *
     * @param timestamp                 the non-{@code null} request timestamp
     * @param hasPayloadEncCertificates {@code true} to request the
     *                                  payload-encryption certs (encryption
     *                                  + signature PEMs)
     * @param hasPasswordPem            {@code true} to request the password
     *                                  PEM bundle
     * @return an {@link Optional} containing the {@link FederatedIdentityCertificate}
     *         reported by the relay, or empty when the relay's response
     *         could not be parsed
     * @throws NullPointerException            if {@code timestamp} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<FederatedIdentityCertificate> queryFederatedIdentityCertificate(Instant timestamp, boolean hasPayloadEncCertificates, boolean hasPasswordPem);

    /**
     * Refreshes the access tokens held inside the federated-identity
     * (Waffle) bridge, returning the relay-rotated set wrapped inside a
     * fresh {@link FederatedRsaEncryption} envelope.
     *
     * @param encryption the non-{@code null} RSA-2048 encryption envelope
     * @param timestamp  the non-{@code null} request timestamp
     * @param fbid       the non-{@code null} encrypted Facebook id payload
     * @return an {@link Optional} containing the
     *         {@link FederatedAccessTokenRefresh} reply, or empty when the
     *         relay's response could not be parsed
     * @throws NullPointerException            if {@code encryption},
     *                                         {@code timestamp} or
     *                                         {@code fbid} is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<FederatedAccessTokenRefresh> refreshFederatedIdentityAccessTokens(FederatedRsaEncryption encryption, Instant timestamp, byte[] fbid);

    /**
     * Submits an arbitrary encrypted action payload to the federated-identity
     * (Waffle) bridge — the generic catch-all RPC for actions the bridge can
     * perform on the linked Meta account once the client holds a valid
     * Waffle session.
     *
     * <p>The reply may also carry a {@code wf_deleted} marker (surfaced via
     * {@link FederatedEncryptedAction#deleted()}) telling the client the
     * bridge has dropped the federated-identity link entirely; when that
     * marker is set the client must purge its local link state.
     *
     * @param encryption the non-{@code null} RSA-2048 encryption envelope
     * @param timestamp  the non-{@code null} request timestamp
     * @param fbid       the non-{@code null} encrypted Facebook id payload
     * @param action     the non-{@code null} encrypted action payload
     * @return an {@link Optional} containing the {@link FederatedEncryptedAction}
     *         reply, or empty when the relay's response could not be parsed
     * @throws NullPointerException            if any argument is {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    Optional<FederatedEncryptedAction> sendFederatedIdentityEncryptedPayload(FederatedRsaEncryption encryption, Instant timestamp, byte[] fbid, byte[] action);

    /**
     * Mints a WhatsApp Enterprise Authenticated Customer (WAEntAC) record
     * after the user has consented to a specific disclosure.
     *
     * <p>The final step of the federated-identity (Waffle) → enterprise
     * enrolment flow: once the user has accepted disclosure
     * {@code disclosureId} in their preferred locale, the Meta bridge mints
     * the enterprise customer record so subsequent business surfaces
     * (catalog, hosted business account, etc.) can address the linked
     * enterprise account.
     *
     * <p>"WAEntAC" is WhatsApp's internal acronym for WhatsApp Enterprise
     * Authenticated Customer; this is the call site that creates one.
     *
     * @param encryption        the non-{@code null} RSA-2048 encryption
     *                          envelope
     * @param timestamp         the non-{@code null} request timestamp
     * @param disclosureId      the accepted disclosure id
     * @param disclosureVersion the non-{@code null} accepted disclosure
     *                          version
     * @param disclosureLg      the non-{@code null} disclosure language tag
     * @param disclosureLc      the non-{@code null} disclosure locale tag
     * @return an {@link Optional} containing the {@link FederatedEnterpriseCustomer}
     *         reply, or empty when the relay's response could not be parsed
     * @throws NullPointerException            if any required argument is
     *                                         {@code null}
     * @throws WhatsAppServerRuntimeException  if the relay rejected the
     *                                         request
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    FederatedEnterpriseCustomer createEnterpriseAuthenticatedCustomer(EnterpriseAuthenticatedCustomerCreate create);
}
