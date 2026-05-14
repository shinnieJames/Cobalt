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
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SyncPendingMutation;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * In-memory {@link WhatsAppClient} implementation for tests.
 *
 * <p>Every method on the {@link WhatsAppClient} contract is implemented
 * here; methods that no test currently needs throw
 * {@link UnsupportedOperationException} with a self-describing message so
 * a test that accidentally relies on them fails loudly with the actual
 * method name in the stack trace.
 *
 * <p>The four methods used by the device-package orchestrators are wired
 * to fluent state held on this class:
 * <ul>
 *   <li>{@link #store()} returns whatever {@link #withStore(WhatsAppStore)}
 *       was last given;</li>
 *   <li>{@link #sendNode(NodeBuilder)} delegates to a caller-supplied
 *       {@link Function} so tests can return canned IQ responses;</li>
 *   <li>{@link #handleFailure(com.github.auties00.cobalt.exception.WhatsAppException)}
 *       records the exception into a list observable via
 *       {@link #failures()};</li>
 *   <li>{@link #queryChatMetadata(JidProvider)} returns from a map of
 *       presets installed via
 *       {@link #withChatMetadata(JidProvider, com.github.auties00.cobalt.model.chat.ChatMetadata)}.</li>
 * </ul>
 *
 * <p>Add more wired overrides here when a new test needs them.
 */
public final class TestWhatsAppClient implements WhatsAppClient {
    private WhatsAppStore store;
    private ABPropsService abPropsService;
    private Function<NodeBuilder, Node> sendNodeHandler = node -> {
        throw new UnsupportedOperationException("TestWhatsAppClient.sendNode: no handler configured");
    };
    private final List<com.github.auties00.cobalt.exception.WhatsAppException> failures = new ArrayList<>();
    private final Map<JidProvider, com.github.auties00.cobalt.model.chat.ChatMetadata> chatMetadata = new HashMap<>();
    private Boolean isConnected;

    /** Returns a new test client with no preset state. */
    public static TestWhatsAppClient create() {
        return new TestWhatsAppClient();
    }

    /** Installs the {@link WhatsAppStore} returned by {@link #store()}. */
    public TestWhatsAppClient withStore(WhatsAppStore store) {
        this.store = store;
        return this;
    }

    /** Installs the handler used by {@link #sendNode(NodeBuilder)}. */
    public TestWhatsAppClient withSendNodeHandler(Function<NodeBuilder, Node> handler) {
        this.sendNodeHandler = handler;
        return this;
    }

    /** Installs a canned {@link com.github.auties00.cobalt.model.chat.ChatMetadata} for the given JID. */
    public TestWhatsAppClient withChatMetadata(JidProvider jid, com.github.auties00.cobalt.model.chat.ChatMetadata metadata) {
        this.chatMetadata.put(jid, metadata);
        return this;
    }

    /** Installs the {@link ABPropsService} returned by {@link #abPropsService()}. */
    public TestWhatsAppClient withAbPropsService(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
        return this;
    }

    /**
     * Pins the value returned by {@link #isConnected()}. When unset
     * (the default), the method throws to surface tests that depend
     * on connectivity without declaring it.
     *
     * @param connected the value to report
     * @return this client, for chaining
     */
    public TestWhatsAppClient withIsConnected(boolean connected) {
        this.isConnected = connected;
        return this;
    }

    /** Returns every exception passed to {@link #handleFailure} in order. */
    public List<com.github.auties00.cobalt.exception.WhatsAppException> failures() {
        return Collections.unmodifiableList(failures);
    }

    @Override
    public WhatsAppStore store() {
        return store;
    }

    /**
     * Returns the AB-props service installed via {@link #withAbPropsService}.
     *
     * <p>Kept as a public method on the test harness so tests can grab the
     * service and pass it explicitly to constructor-DI consumers. Not part
     * of the {@link WhatsAppClient} interface — production code always
     * receives the AB-props service via constructor injection.
     *
     * @return the installed AB-props service
     */
    public ABPropsService abPropsService() {
        if (abPropsService == null) {
            throw new UnsupportedOperationException(
                    "TestWhatsAppClient: abPropsService is not configured — call withAbPropsService(..) first");
        }
        return abPropsService;
    }

    @Override
    public WhatsAppClient connect() {
        throw new UnsupportedOperationException("TestWhatsAppClient: connect(..) is not stubbed");
    }

    @Override
    public void resolvePendingRequest(Node node) {
        throw new UnsupportedOperationException("TestWhatsAppClient: resolvePendingRequest(..) is not stubbed");
    }

    @Override
    public void disconnect(WhatsAppClientDisconnectReason reason) {
        throw new UnsupportedOperationException("TestWhatsAppClient: disconnect(..) is not stubbed");
    }

    @Override
    public void sendNodeWithNoResponse(Node node) {
        if (store != null) {
            for (var listener : store.listeners()) {
                listener.onNodeSent(this, node);
            }
        }
    }

    @Override
    public Node sendNode(NodeBuilder node) {
        return sendNodeHandler.apply(node);
    }

    @Override
    public Node sendNode(NodeBuilder node, Function<Node, Boolean> filter) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendNode(..) is not stubbed");
    }

    @Override
    public Node sendNode(MexOperation.Request request) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendNode(..) is not stubbed");
    }

    @Override
    public void sendNodeWithNoResponse(MexOperation.Request request) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendNodeWithNoResponse(..) is not stubbed");
    }

    @Override
    public Node sendNode(SmaxOperation.Request request) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendNode(..) is not stubbed");
    }

    @Override
    public void sendNodeWithNoResponse(SmaxOperation.Request request) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendNodeWithNoResponse(..) is not stubbed");
    }

    @Override
    public UsyncResult sendNode(UsyncQuery query) throws InterruptedException {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendNode(..) is not stubbed");
    }

    @Override
    public void disconnect() {
        throw new UnsupportedOperationException("TestWhatsAppClient: disconnect(..) is not stubbed");
    }

    @Override
    public void reconnect() {
        throw new UnsupportedOperationException("TestWhatsAppClient: reconnect(..) is not stubbed");
    }

    @Override
    public void logout() {
        throw new UnsupportedOperationException("TestWhatsAppClient: logout(..) is not stubbed");
    }

    @Override
    public void logoutCompanion(JidProvider companionProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: logoutCompanion(..) is not stubbed");
    }

    @Override
    public SequencedCollection<Jid> queryLinkedDevices() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryLinkedDevices(..) is not stubbed");
    }

    @Override
    public boolean isConnected() {
        if (isConnected == null) {
            throw new UnsupportedOperationException("TestWhatsAppClient: isConnected(..) is not stubbed — call withIsConnected(..) first");
        }
        return isConnected;
    }

    @Override
    public WhatsAppClient waitForDisconnection() {
        throw new UnsupportedOperationException("TestWhatsAppClient: waitForDisconnection(..) is not stubbed");
    }

    @Override
    public void handleFailure(WhatsAppException exception) {
        failures.add(exception);
    }

    @Override
    public void pushWebAppState(SyncPatchType type, List<SyncPendingMutation> patches) {
        throw new UnsupportedOperationException("TestWhatsAppClient: pushWebAppState(..) is not stubbed");
    }

    @Override
    public boolean pullWebAppState(SyncPatchType... patches) {
        throw new UnsupportedOperationException("TestWhatsAppClient: pullWebAppState(..) is not stubbed");
    }

    @Override
    public void sendAck(Node node) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendAck(..) is not stubbed");
    }

    @Override
    public void sendAck(String id, Node node) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendAck(..) is not stubbed");
    }

    @Override
    public void sendPreKeys(long keysCount) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendPreKeys(..) is not stubbed");
    }

    @Override
    public void sendReceipt(String id, JidProvider fromProvider, String type) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendReceipt(..) is not stubbed");
    }

    @Override
    public ChatMetadata queryChatMetadata(JidProvider chat) {
        var hit = chatMetadata.get(chat);
        if (hit == null) throw new UnsupportedOperationException("TestWhatsAppClient.queryChatMetadata: no preset for " + chat);
        return hit;
    }

    @Override
    public Optional<BusinessProfile> queryBusinessProfile(JidProvider contact) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessProfile(..) is not stubbed");
    }

    @Override
    public BusinessCategory parseBusinessCategory(Node node) {
        throw new UnsupportedOperationException("TestWhatsAppClient: parseBusinessCategory(..) is not stubbed");
    }

    @Override
    public void editBusinessProfile(BusinessProfile profile) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editBusinessProfile(..) is not stubbed");
    }

    @Override
    public void updateBusinessCartEnabled(boolean enabled) {
        throw new UnsupportedOperationException("TestWhatsAppClient: updateBusinessCartEnabled(..) is not stubbed");
    }

    @Override
    public void deleteBusinessCoverPhoto() {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteBusinessCoverPhoto(..) is not stubbed");
    }

    @Override
    public Optional<String> queryBusinessPublicKey(JidProvider businessJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessPublicKey(..) is not stubbed");
    }

    @Override
    public BusinessSignedUserInfo queryBusinessSignedUserInfo(JidProvider businessJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessSignedUserInfo(..) is not stubbed");
    }

    @Override
    public List<BusinessMerchantCompliance> queryMerchantCompliance(List<? extends JidProvider> jids) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryMerchantCompliance(..) is not stubbed");
    }

    @Override
    public BusinessMerchantCompliance editMerchantCompliance(MerchantComplianceEdit edit) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editMerchantCompliance(..) is not stubbed");
    }

    @Override
    public BusinessCategoryTypeahead queryBusinessCategoryTypeahead(String query) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessCategoryTypeahead(..) is not stubbed");
    }

    @Override
    public Optional<BusinessOrder> queryOrder(String messageId, String tokenBase64) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryOrder(..) is not stubbed");
    }

    @Override
    public String createQuickReply(QuickReplyCreate create) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createQuickReply(..) is not stubbed");
    }

    @Override
    public Optional<QuickReply> editQuickReply(QuickReplyEdit edit) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editQuickReply(..) is not stubbed");
    }

    @Override
    public Optional<QuickReply> deleteQuickReply(String quickReplyId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteQuickReply(..) is not stubbed");
    }

    @Override
    public Optional<BotProfile> queryBotProfile(JidProvider botJid) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBotProfile(..) is not stubbed");
    }

    @Override
    public Optional<BotProfile> queryBotProfile(JidProvider botJid, String personaId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBotProfile(..) is not stubbed");
    }

    @Override
    public ActiveCall startCall(JidProvider targetProvider, CallOptions options) {
        throw new UnsupportedOperationException("TestWhatsAppClient: startCall(..) is not stubbed");
    }

    @Override
    public ActiveCall acceptCall(IncomingCall offer, CallOptions options) {
        throw new UnsupportedOperationException("TestWhatsAppClient: acceptCall(..) is not stubbed");
    }

    @Override
    public void rejectCall(IncomingCall offer, CallEndReason reason) {
        throw new UnsupportedOperationException("TestWhatsAppClient: rejectCall(..) is not stubbed");
    }

    @Override
    public void terminateCall(String callId, CallEndReason reason) {
        throw new UnsupportedOperationException("TestWhatsAppClient: terminateCall(..) is not stubbed");
    }

    @Override
    public void terminateCall(ActiveCall call, CallEndReason reason) {
        throw new UnsupportedOperationException("TestWhatsAppClient: terminateCall(..) is not stubbed");
    }

    @Override
    public void preacceptCall(String callId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: preacceptCall(..) is not stubbed");
    }

    @Override
    public void preacceptCall(IncomingCall call) {
        throw new UnsupportedOperationException("TestWhatsAppClient: preacceptCall(..) is not stubbed");
    }

    @Override
    public void muteCall(String callId, boolean muted) {
        throw new UnsupportedOperationException("TestWhatsAppClient: muteCall(..) is not stubbed");
    }

    @Override
    public void muteCall(ActiveCall call, boolean muted) {
        throw new UnsupportedOperationException("TestWhatsAppClient: muteCall(..) is not stubbed");
    }

    @Override
    public void editCallVideoState(ActiveCall call, boolean videoEnabled) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editCallVideoState(..) is not stubbed");
    }

    @Override
    public ActiveCall startGroupCall(JidProvider groupProvider, Collection<? extends JidProvider> participantsProvider, boolean video) {
        throw new UnsupportedOperationException("TestWhatsAppClient: startGroupCall(..) is not stubbed");
    }

    @Override
    public void addCallParticipants(String callId, Collection<? extends JidProvider> participantsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addCallParticipants(..) is not stubbed");
    }

    @Override
    public void addCallParticipants(ActiveCall call, Collection<? extends JidProvider> participantsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addCallParticipants(..) is not stubbed");
    }

    @Override
    public void removeCallParticipants(String callId, Collection<? extends JidProvider> participantsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: removeCallParticipants(..) is not stubbed");
    }

    @Override
    public void removeCallParticipants(ActiveCall call, Collection<? extends JidProvider> participantsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: removeCallParticipants(..) is not stubbed");
    }

    @Override
    public void issueTrustedContactToken(JidProvider peerProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: issueTrustedContactToken(..) is not stubbed");
    }

    @Override
    public AckResult sendScheduledCall(JidProvider chatProvider, String title, Instant scheduledAt, boolean video) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendScheduledCall(..) is not stubbed");
    }

    @Override
    public AckResult cancelScheduledCall(MessageKey creationKey) {
        throw new UnsupportedOperationException("TestWhatsAppClient: cancelScheduledCall(..) is not stubbed");
    }

    @Override
    public Optional<ActiveCall> joinCallLink(String token, CallLinkMedia media) {
        throw new UnsupportedOperationException("TestWhatsAppClient: joinCallLink(..) is not stubbed");
    }

    @Override
    public SequencedCollection<Newsletter> queryNewsletters() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletters(..) is not stubbed");
    }

    @Override
    public SequencedCollection<Chat> queryGroups() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroups(..) is not stubbed");
    }

    @Override
    public Optional<String> queryGroupInviteCode(JidProvider groupProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupInviteCode(..) is not stubbed");
    }

    @Override
    public String revokeGroupInviteCode(JidProvider groupProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: revokeGroupInviteCode(..) is not stubbed");
    }

    @Override
    public Jid joinGroupViaInvite(String inviteCode) {
        throw new UnsupportedOperationException("TestWhatsAppClient: joinGroupViaInvite(..) is not stubbed");
    }

    @Override
    public GroupInvitePicture queryGroupInvitePicture(JidProvider groupProvider, String inviteCode) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupInvitePicture(..) is not stubbed");
    }

    @Override
    public GroupInvitePicture queryGroupInvitePicture(JidProvider groupProvider, String inviteCode, String query) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupInvitePicture(..) is not stubbed");
    }

    @Override
    public GroupInvitePicture queryGroupInvitePicturePreview(JidProvider groupProvider, String inviteCode) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupInvitePicturePreview(..) is not stubbed");
    }

    @Override
    public GroupInvitePicture queryGroupInvitePicturePreview(JidProvider groupProvider, String inviteCode, String query) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupInvitePicturePreview(..) is not stubbed");
    }

    @Override
    public GroupMetadata queryGroupInfoByInvite(GroupInvite invite) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupInfoByInvite(..) is not stubbed");
    }

    @Override
    public void sendGroupInvite(JidProvider groupProvider, JidProvider targetProvider, Instant inviteTimestamp) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendGroupInvite(..) is not stubbed");
    }

    @Override
    public List<Jid> queryGroupJoinRequests(JidProvider groupProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupJoinRequests(..) is not stubbed");
    }

    @Override
    public void acceptGroupJoinRequest(JidProvider groupProvider, JidProvider applicantProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: acceptGroupJoinRequest(..) is not stubbed");
    }

    @Override
    public void rejectGroupJoinRequest(JidProvider groupProvider, JidProvider applicantProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: rejectGroupJoinRequest(..) is not stubbed");
    }

    @Override
    public AckResult sendPeerMessage(JidProvider chatJidProvider, ChatMessageInfo response) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendPeerMessage(..) is not stubbed");
    }

    @Override
    public Map<Jid, Boolean> hasWhatsapp(Collection<? extends JidProvider> phoneNumbersProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: hasWhatsapp(..) is not stubbed");
    }

    @Override
    public boolean hasWhatsapp(JidProvider phoneProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: hasWhatsapp(..) is not stubbed");
    }

    @Override
    public Optional<String> queryName(JidProvider jidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryName(..) is not stubbed");
    }

    @Override
    public Map<Jid, Jid> syncContacts(Collection<ContactCard> contacts) {
        throw new UnsupportedOperationException("TestWhatsAppClient: syncContacts(..) is not stubbed");
    }

    @Override
    public Optional<Newsletter> queryNewsletter(JidProvider newsletterJidProvider, NewsletterViewerRole role) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletter(..) is not stubbed");
    }

    @Override
    public Optional<Newsletter> queryNewsletter(JidProvider newsletterJidProvider, NewsletterViewerRole role, boolean dehydrated) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletter(..) is not stubbed");
    }

    @Override
    public Newsletter createNewsletter(NewsletterCreate create) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createNewsletter(..) is not stubbed");
    }

    @Override
    public void editNewsletterMetadata(NewsletterMetadataEdit edit) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editNewsletterMetadata(..) is not stubbed");
    }

    @Override
    public Optional<Newsletter> deleteNewsletter(JidProvider newsletterProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteNewsletter(..) is not stubbed");
    }

    @Override
    public void joinNewsletter(JidProvider newsletterProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: joinNewsletter(..) is not stubbed");
    }

    @Override
    public void leaveNewsletter(JidProvider newsletterProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: leaveNewsletter(..) is not stubbed");
    }

    @Override
    public void muteNewsletter(JidProvider newsletterProvider, boolean mute) {
        throw new UnsupportedOperationException("TestWhatsAppClient: muteNewsletter(..) is not stubbed");
    }

    @Override
    public void reactToNewsletterMessage(JidProvider newsletterProvider, String serverMessageId, String emoji) {
        throw new UnsupportedOperationException("TestWhatsAppClient: reactToNewsletterMessage(..) is not stubbed");
    }

    @Override
    public void revokeNewsletterMessage(JidProvider newsletterProvider, String serverMessageId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: revokeNewsletterMessage(..) is not stubbed");
    }

    @Override
    public void acceptNewsletterAdminInvite(JidProvider newsletterProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: acceptNewsletterAdminInvite(..) is not stubbed");
    }

    @Override
    public void revokeNewsletterAdminInvite(JidProvider newsletterProvider, JidProvider adminProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: revokeNewsletterAdminInvite(..) is not stubbed");
    }

    @Override
    public void demoteNewsletterAdmin(JidProvider newsletterProvider, JidProvider adminProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: demoteNewsletterAdmin(..) is not stubbed");
    }

    @Override
    public void editNewsletterReactionSetting(JidProvider newsletterProvider, NewsletterReactionSettings setting) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editNewsletterReactionSetting(..) is not stubbed");
    }

    @Override
    public List<NewsletterCapability> queryNewsletterAdminCapabilities(JidProvider newsletterProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterAdminCapabilities(..) is not stubbed");
    }

    @Override
    public OptionalLong queryNewsletterAdminInfo(JidProvider newsletterProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterAdminInfo(..) is not stubbed");
    }

    @Override
    public List<NewsletterFollower> queryNewsletterFollowers(JidProvider newsletterProvider, int count) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterFollowers(..) is not stubbed");
    }

    @Override
    public List<NewsletterAdminInvite> queryNewsletterPendingInvites(JidProvider newsletterProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterPendingInvites(..) is not stubbed");
    }

    @Override
    public NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterDirectoryList(..) is not stubbed");
    }

    @Override
    public NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view, String cursorToken) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterDirectoryList(..) is not stubbed");
    }

    @Override
    public NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view, List<String> countryCodes, List<String> categories, Long limit, String cursorToken, boolean fetchStatusMetadata) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterDirectoryList(..) is not stubbed");
    }

    @Override
    public NewsletterDirectoryPage searchNewsletterDirectory(String searchText) {
        throw new UnsupportedOperationException("TestWhatsAppClient: searchNewsletterDirectory(..) is not stubbed");
    }

    @Override
    public NewsletterDirectoryPage searchNewsletterDirectory(String searchText, List<String> categories) {
        throw new UnsupportedOperationException("TestWhatsAppClient: searchNewsletterDirectory(..) is not stubbed");
    }

    @Override
    public NewsletterDirectoryPage searchNewsletterDirectory(String searchText, List<String> categories, Long limit, String cursorToken, boolean fetchStatusMetadata) {
        throw new UnsupportedOperationException("TestWhatsAppClient: searchNewsletterDirectory(..) is not stubbed");
    }

    @Override
    public List<NewsletterDirectoryCategory> queryNewsletterDirectoryCategoriesPreview(String input) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterDirectoryCategoriesPreview(..) is not stubbed");
    }

    @Override
    public NewsletterDirectoryPage queryRecommendedNewsletters() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryRecommendedNewsletters(..) is not stubbed");
    }

    @Override
    public NewsletterDirectoryPage queryRecommendedNewsletters(Long limit, List<String> countryCodes, boolean fetchStatusMetadata) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryRecommendedNewsletters(..) is not stubbed");
    }

    @Override
    public List<NewsletterDirectoryEntry> querySimilarNewsletters(JidProvider newsletterProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: querySimilarNewsletters(..) is not stubbed");
    }

    @Override
    public List<NewsletterDirectoryEntry> querySimilarNewsletters(JidProvider newsletterProvider, Long limit, List<String> countryCodes, boolean fetchStatusMetadata) {
        throw new UnsupportedOperationException("TestWhatsAppClient: querySimilarNewsletters(..) is not stubbed");
    }

    @Override
    public Optional<NewsletterLinkPreview> queryNewsletterLinkPreview(String url) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterLinkPreview(..) is not stubbed");
    }

    @Override
    public boolean isNewsletterDomainPreviewable(String url) {
        throw new UnsupportedOperationException("TestWhatsAppClient: isNewsletterDomainPreviewable(..) is not stubbed");
    }

    @Override
    public List<NewsletterReactor> queryNewsletterMessageReactionSenders(JidProvider newsletterProvider, long serverMessageId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterMessageReactionSenders(..) is not stubbed");
    }

    @Override
    public List<NewsletterPollVoter> queryNewsletterPollVoters(JidProvider newsletterProvider, long serverMessageId, long limit) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterPollVoters(..) is not stubbed");
    }

    @Override
    public List<NewsletterPollVoter> queryNewsletterPollVoters(JidProvider newsletterProvider, long serverMessageId, long limit, String voteHash) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterPollVoters(..) is not stubbed");
    }

    @Override
    public void transferNewsletterOwnership(JidProvider newsletterProvider, JidProvider newOwnerProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: transferNewsletterOwnership(..) is not stubbed");
    }

    @Override
    public NewsletterAdminInvite createNewsletterAdminInvite(JidProvider newsletterProvider, JidProvider inviteeProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createNewsletterAdminInvite(..) is not stubbed");
    }

    @Override
    public void addNewsletterPaidPartnershipLabel(JidProvider newsletterProvider, String serverMessageId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addNewsletterPaidPartnershipLabel(..) is not stubbed");
    }

    @Override
    public void logNewsletterExposures(List<NewsletterExposure> exposures) {
        throw new UnsupportedOperationException("TestWhatsAppClient: logNewsletterExposures(..) is not stubbed");
    }

    @Override
    public NewsletterReportAppeal createNewsletterReportAppeal(String reason, String reportId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createNewsletterReportAppeal(..) is not stubbed");
    }

    @Override
    public List<NewsletterEnforcement> queryNewsletterEnforcements(JidProvider newsletterProvider, String locale) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterEnforcements(..) is not stubbed");
    }

    @Override
    public List<NewsletterReport> queryNewsletterReports() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterReports(..) is not stubbed");
    }

    @Override
    public List<NewsletterInsightMetric> queryNewsletterInsights(JidProvider newsletterProvider, List<String> metrics) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterInsights(..) is not stubbed");
    }

    @Override
    public String queryNewsletterDsbInfo(String entityId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterDsbInfo(..) is not stubbed");
    }

    @Override
    public Optional<String> queryAbout(JidProvider jidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryAbout(..) is not stubbed");
    }

    @Override
    public Optional<String> queryUsername() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryUsername(..) is not stubbed");
    }

    @Override
    public boolean editUsername(String username) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editUsername(..) is not stubbed");
    }

    @Override
    public void editUsernameRecoveryKey(String pin) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editUsernameRecoveryKey(..) is not stubbed");
    }

    @Override
    public boolean checkUsernameAvailability(String candidate) {
        throw new UnsupportedOperationException("TestWhatsAppClient: checkUsernameAvailability(..) is not stubbed");
    }

    @Override
    public void editTextStatus(String text, String emoji, Duration ephemeralDuration) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editTextStatus(..) is not stubbed");
    }

    @Override
    public Map<Jid, ContactTextStatus> queryUserTextStatuses(List<? extends JidProvider> usersProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryUserTextStatuses(..) is not stubbed");
    }

    @Override
    public Optional<LidChange> queryLidChangeNotification() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryLidChangeNotification(..) is not stubbed");
    }

    @Override
    public List<OhaiKeyConfig> queryOhaiKeyConfig() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryOhaiKeyConfig(..) is not stubbed");
    }

    @Override
    public List<BusinessCatalogEntry> queryBusinessCatalog(JidProvider businessJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessCatalog(..) is not stubbed");
    }

    @Override
    public List<BusinessCatalogEntry> queryBusinessCatalog(JidProvider businessJidProvider, int limit) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessCatalog(..) is not stubbed");
    }

    @Override
    public List<BusinessCatalog> queryBusinessCollections(JidProvider businessJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessCollections(..) is not stubbed");
    }

    @Override
    public List<BusinessCatalog> queryBusinessCollections(JidProvider businessJidProvider, int limit) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessCollections(..) is not stubbed");
    }

    @Override
    public BusinessPostcodeVerification verifyBusinessPostcode(JidProvider businessJidProvider, String directConnectionEncryptedInfo) {
        throw new UnsupportedOperationException("TestWhatsAppClient: verifyBusinessPostcode(..) is not stubbed");
    }

    @Override
    public BusinessRefreshedCart refreshBusinessCart(BusinessCartRefresh refresh) {
        throw new UnsupportedOperationException("TestWhatsAppClient: refreshBusinessCart(..) is not stubbed");
    }

    @Override
    public BusinessCtwaContext queryCtwaContext(JidProvider businessJidProvider, String inviteCode, String expectedSourceUrl) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryCtwaContext(..) is not stubbed");
    }

    @Override
    public SequencedCollection<Jid> queryBlockList() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBlockList(..) is not stubbed");
    }

    @Override
    public void blockContact(JidProvider contactProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: blockContact(..) is not stubbed");
    }

    @Override
    public void unblockContact(JidProvider contactProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: unblockContact(..) is not stubbed");
    }

    @Override
    public BlockListResult queryBlockList(String itemDhash) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBlockList(..) is not stubbed");
    }

    @Override
    public OptOutListResult queryOptOutList(String itemDhash, String iqCategory) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryOptOutList(..) is not stubbed");
    }

    @Override
    public ContactBlacklistResult queryContactBlacklist(String categoryName, ContactBlacklistAddressingMode addressingMode) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryContactBlacklist(..) is not stubbed");
    }

    @Override
    public void updateOptOutList(OptOutListUpdate update) {
        throw new UnsupportedOperationException("TestWhatsAppClient: updateOptOutList(..) is not stubbed");
    }

    @Override
    public Optional<URI> queryPicture(JidProvider jidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryPicture(..) is not stubbed");
    }

    @Override
    public void editName(String newPushName) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editName(..) is not stubbed");
    }

    @Override
    public void editAbout(String aboutText) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editAbout(..) is not stubbed");
    }

    @Override
    public void editProfilePicture(byte[] jpegBytes) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editProfilePicture(..) is not stubbed");
    }

    @Override
    public void removeProfilePicture() {
        throw new UnsupportedOperationException("TestWhatsAppClient: removeProfilePicture(..) is not stubbed");
    }

    @Override
    public void editPresence(ContactStatus status) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editPresence(..) is not stubbed");
    }

    @Override
    public void editPresence(ContactStatus status, String presenceName) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editPresence(..) is not stubbed");
    }

    @Override
    public void editChatState(JidProvider chatProvider, ContactStatus state) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editChatState(..) is not stubbed");
    }

    @Override
    public void subscribeToPresence(JidProvider targetProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: subscribeToPresence(..) is not stubbed");
    }

    @Override
    public void subscribeToPresence(JidProvider presenceToProvider, String presenceName, JidProvider presenceContextProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: subscribeToPresence(..) is not stubbed");
    }

    @Override
    public void unsubscribeFromPresence(JidProvider targetProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: unsubscribeFromPresence(..) is not stubbed");
    }

    @Override
    public AckResult sendMessage(JidProvider jidProvider, MessageContainer container) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendMessage(..) is not stubbed");
    }

    @Override
    public AckResult sendMessage(MessageInfo messageInfo) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendMessage(..) is not stubbed");
    }

    @Override
    public AckResult editMessage(MessageKey originalKey, MessageContainer newContent) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editMessage(..) is not stubbed");
    }

    @Override
    public AckResult deleteMessage(MessageKey key, boolean everyone) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteMessage(..) is not stubbed");
    }

    @Override
    public ChatMessageInfo sendStatus(MessageContainer content) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendStatus(..) is not stubbed");
    }

    @Override
    public AckResult deleteStatus(String statusId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteStatus(..) is not stubbed");
    }

    @Override
    public void markStatusViewed(String statusId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: markStatusViewed(..) is not stubbed");
    }

    @Override
    public StatusPrivacySetting queryStatusPrivacy() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryStatusPrivacy(..) is not stubbed");
    }

    @Override
    public void editStatusPrivacy(StatusPrivacyMode mode, Collection<? extends JidProvider> jidsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editStatusPrivacy(..) is not stubbed");
    }

    @Override
    public AckResult forwardMessage(MessageKey sourceKey, JidProvider destinationProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: forwardMessage(..) is not stubbed");
    }

    @Override
    public void forwardMessages(Collection<MessageKey> sourceKeys, Collection<? extends JidProvider> destinationsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: forwardMessages(..) is not stubbed");
    }

    @Override
    public AckResult addReaction(MessageKey messageKey, String emoji) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addReaction(..) is not stubbed");
    }

    @Override
    public AckResult removeReaction(MessageKey messageKey) {
        throw new UnsupportedOperationException("TestWhatsAppClient: removeReaction(..) is not stubbed");
    }

    @Override
    public void starMessage(MessageKey key) {
        throw new UnsupportedOperationException("TestWhatsAppClient: starMessage(..) is not stubbed");
    }

    @Override
    public void unstarMessage(MessageKey key) {
        throw new UnsupportedOperationException("TestWhatsAppClient: unstarMessage(..) is not stubbed");
    }

    @Override
    public void archiveChat(JidProvider chatProvider, boolean archive) {
        throw new UnsupportedOperationException("TestWhatsAppClient: archiveChat(..) is not stubbed");
    }

    @Override
    public void pinChat(JidProvider chatProvider, boolean pin) {
        throw new UnsupportedOperationException("TestWhatsAppClient: pinChat(..) is not stubbed");
    }

    @Override
    public void muteChat(JidProvider chatProvider, Instant muteUntil) {
        throw new UnsupportedOperationException("TestWhatsAppClient: muteChat(..) is not stubbed");
    }

    @Override
    public void unmuteChat(JidProvider chatProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: unmuteChat(..) is not stubbed");
    }

    @Override
    public void markChatAsRead(JidProvider chatProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: markChatAsRead(..) is not stubbed");
    }

    @Override
    public void markChatAsUnread(JidProvider chatProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: markChatAsUnread(..) is not stubbed");
    }

    @Override
    public void clearChat(JidProvider chatProvider, boolean keepStarred) {
        throw new UnsupportedOperationException("TestWhatsAppClient: clearChat(..) is not stubbed");
    }

    @Override
    public Optional<Chat> deleteChat(JidProvider chatProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteChat(..) is not stubbed");
    }

    @Override
    public void lockChat(JidProvider chatProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: lockChat(..) is not stubbed");
    }

    @Override
    public void unlockChat(JidProvider chatProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: unlockChat(..) is not stubbed");
    }

    @Override
    public String createLabel(String name, int colorIndex) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createLabel(..) is not stubbed");
    }

    @Override
    public Optional<Label> editLabel(String labelId, String name, int colorIndex) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editLabel(..) is not stubbed");
    }

    @Override
    public Optional<Label> deleteLabel(String labelId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteLabel(..) is not stubbed");
    }

    @Override
    public Optional<Label> deleteLabel(Label label) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteLabel(..) is not stubbed");
    }

    @Override
    public void reorderLabels(List<String> labelIds) {
        throw new UnsupportedOperationException("TestWhatsAppClient: reorderLabels(..) is not stubbed");
    }

    @Override
    public void associateLabel(String labelId, JidProvider chatProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: associateLabel(..) is not stubbed");
    }

    @Override
    public void associateLabel(Label label, JidProvider chat) {
        throw new UnsupportedOperationException("TestWhatsAppClient: associateLabel(..) is not stubbed");
    }

    @Override
    public void dissociateLabel(String labelId, JidProvider chatProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: dissociateLabel(..) is not stubbed");
    }

    @Override
    public void dissociateLabel(Label label, JidProvider chat) {
        throw new UnsupportedOperationException("TestWhatsAppClient: dissociateLabel(..) is not stubbed");
    }

    @Override
    public void associateLabel(String labelId, MessageKey messageKey) {
        throw new UnsupportedOperationException("TestWhatsAppClient: associateLabel(..) is not stubbed");
    }

    @Override
    public void associateLabel(Label label, MessageKey messageKey) {
        throw new UnsupportedOperationException("TestWhatsAppClient: associateLabel(..) is not stubbed");
    }

    @Override
    public void dissociateLabel(String labelId, MessageKey messageKey) {
        throw new UnsupportedOperationException("TestWhatsAppClient: dissociateLabel(..) is not stubbed");
    }

    @Override
    public void dissociateLabel(Label label, MessageKey messageKey) {
        throw new UnsupportedOperationException("TestWhatsAppClient: dissociateLabel(..) is not stubbed");
    }

    @Override
    public Jid createBroadcastList(String name, Collection<? extends JidProvider> recipientsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createBroadcastList(..) is not stubbed");
    }

    @Override
    public void editBroadcastList(JidProvider broadcastListIdProvider, String newName, Collection<? extends JidProvider> newRecipientsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editBroadcastList(..) is not stubbed");
    }

    @Override
    public void deleteBroadcastList(JidProvider broadcastListIdProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteBroadcastList(..) is not stubbed");
    }

    @Override
    public ChatMessageInfo sendBroadcast(JidProvider broadcastListIdProvider, MessageContainer message) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendBroadcast(..) is not stubbed");
    }

    @Override
    public void assignChatToAgent(JidProvider chatProvider, String agentId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: assignChatToAgent(..) is not stubbed");
    }

    @Override
    public void unassignChatFromAgent(JidProvider chatProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: unassignChatFromAgent(..) is not stubbed");
    }

    @Override
    public void editChatAssignmentOpenedStatus(JidProvider chatProvider, boolean opened) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editChatAssignmentOpenedStatus(..) is not stubbed");
    }

    @Override
    public AccountDisappearingMode queryDisappearingMode() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryDisappearingMode(..) is not stubbed");
    }

    @Override
    public void editEphemeralTimer(JidProvider chatProvider, ChatEphemeralTimer timer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editEphemeralTimer(..) is not stubbed");
    }

    @Override
    public TosNotices queryTosNotices(Collection<String> noticeIds) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryTosNotices(..) is not stubbed");
    }

    @Override
    public void cancelGdprRequest(GdprReportType reportType) {
        throw new UnsupportedOperationException("TestWhatsAppClient: cancelGdprRequest(..) is not stubbed");
    }

    @Override
    public Optional<String> queryPushServerKey() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryPushServerKey(..) is not stubbed");
    }

    @Override
    public Map<PrivacySettingType, PrivacySettingValue> queryPrivacySettings() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryPrivacySettings(..) is not stubbed");
    }

    @Override
    public void editPrivacySetting(PrivacySettingType type, PrivacySettingValue value) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editPrivacySetting(..) is not stubbed");
    }

    @Override
    public void editPrivacySetting(PrivacySettingType type, PrivacySettingValue value, Collection<? extends JidProvider> excludedOrIncludedProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editPrivacySetting(..) is not stubbed");
    }

    @Override
    public void editReadReceipts(boolean enabled) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editReadReceipts(..) is not stubbed");
    }

    @Override
    public void issuePrivacyTokens(JidProvider userJidProvider, Collection<PrivacyTokenType> tokenTypes, Instant timestamp) {
        throw new UnsupportedOperationException("TestWhatsAppClient: issuePrivacyTokens(..) is not stubbed");
    }

    @Override
    public PrivacyDisallowedList queryPrivacyDisallowedList(JidProvider jidProvider, String dhash, String category, String type) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryPrivacyDisallowedList(..) is not stubbed");
    }

    @Override
    public Optional<ReachoutTimelock> queryReachoutTimelock() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryReachoutTimelock(..) is not stubbed");
    }

    @Override
    public Optional<UserIntegritySignals> queryUserIntegritySignals(JidProvider userJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryUserIntegritySignals(..) is not stubbed");
    }

    @Override
    public Optional<NewChatMessageCappingInfo> queryNewChatMessageCappingInfo() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewChatMessageCappingInfo(..) is not stubbed");
    }

    @Override
    public void submitPasskeyIntegrityChallenge(byte[] signedChallenge, boolean prfAvailable) {
        throw new UnsupportedOperationException("TestWhatsAppClient: submitPasskeyIntegrityChallenge(..) is not stubbed");
    }

    @Override
    public Optional<String> queryUserCountryCode(JidProvider userJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryUserCountryCode(..) is not stubbed");
    }

    @Override
    public Optional<String> queryUserUsername(JidProvider userJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryUserUsername(..) is not stubbed");
    }

    @Override
    public void editDefaultDisappearingMode(ChatEphemeralTimer timer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editDefaultDisappearingMode(..) is not stubbed");
    }

    @Override
    public GroupMetadata createGroup(String subject, Collection<? extends JidProvider> participantsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createGroup(..) is not stubbed");
    }

    @Override
    public GroupMetadata createGroup(String subject, ChatEphemeralTimer ephemeralTimer, Collection<? extends JidProvider> participantsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createGroup(..) is not stubbed");
    }

    @Override
    public void leaveGroup(JidProvider groupProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: leaveGroup(..) is not stubbed");
    }

    @Override
    public void leaveGroup(JidProvider... groups) {
        throw new UnsupportedOperationException("TestWhatsAppClient: leaveGroup(..) is not stubbed");
    }

    @Override
    public boolean isGroupInternal(JidProvider groupJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: isGroupInternal(..) is not stubbed");
    }

    @Override
    public Optional<GroupMetadata> queryGroupInfo(JidProvider groupProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupInfo(..) is not stubbed");
    }

    @Override
    public Optional<GroupMetadata> queryGroupInfo(JidProvider groupProvider, boolean includeUsername, String participantsPhash, String queryContext) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupInfo(..) is not stubbed");
    }

    @Override
    public Optional<GroupMetadata> queryGroupInfoIncludingBots(JidProvider groupProvider, boolean includeUsername, String participantsPhash, String queryContext) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupInfoIncludingBots(..) is not stubbed");
    }

    @Override
    public Optional<String> queryGroupInviteCode(JidProvider groupProvider, String queryContext) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupInviteCode(..) is not stubbed");
    }

    @Override
    public String createGroupInviteCode(JidProvider receiverProvider, String entryPoint) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createGroupInviteCode(..) is not stubbed");
    }

    @Override
    public CommunityMetadata createCommunity(String name, String description) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createCommunity(..) is not stubbed");
    }

    @Override
    public CommunityMetadata createCommunity(String name, String description, ChatEphemeralTimer ephemeralTimer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createCommunity(..) is not stubbed");
    }

    @Override
    public void deactivateCommunity(JidProvider communityProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deactivateCommunity(..) is not stubbed");
    }

    @Override
    public void leaveCommunity(JidProvider communityProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: leaveCommunity(..) is not stubbed");
    }

    @Override
    public void transferCommunityOwnership(JidProvider communityProvider, JidProvider newOwnerProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: transferCommunityOwnership(..) is not stubbed");
    }

    @Override
    public List<Jid> querySubgroupSuggestions(JidProvider communityProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: querySubgroupSuggestions(..) is not stubbed");
    }

    @Override
    public void approveSubgroupSuggestion(JidProvider communityProvider, JidProvider suggestedSubgroupProvider, JidProvider suggestionCreatorProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: approveSubgroupSuggestion(..) is not stubbed");
    }

    @Override
    public void rejectSubgroupSuggestion(JidProvider communityProvider, JidProvider suggestedSubgroupProvider, JidProvider suggestionCreatorProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: rejectSubgroupSuggestion(..) is not stubbed");
    }

    @Override
    public long querySubgroupParticipantCount(JidProvider subgroupProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: querySubgroupParticipantCount(..) is not stubbed");
    }

    @Override
    public List<CommunityLinkedGroup> querySubgroups(JidProvider communityProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: querySubgroups(..) is not stubbed");
    }

    @Override
    public Map<Jid, GroupParticipantStatus> addGroupParticipants(JidProvider groupProvider, Collection<? extends JidProvider> toAddProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addGroupParticipants(..) is not stubbed");
    }

    @Override
    public Map<Jid, GroupParticipantStatus> removeGroupParticipants(JidProvider groupProvider, Collection<? extends JidProvider> toRemoveProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: removeGroupParticipants(..) is not stubbed");
    }

    @Override
    public Map<Jid, GroupParticipantStatus> removeGroupParticipants(JidProvider groupProvider, Collection<? extends JidProvider> toRemoveProvider, boolean removeLinkedGroups) {
        throw new UnsupportedOperationException("TestWhatsAppClient: removeGroupParticipants(..) is not stubbed");
    }

    @Override
    public void promoteGroupParticipants(JidProvider groupProvider, Collection<? extends JidProvider> toPromoteProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: promoteGroupParticipants(..) is not stubbed");
    }

    @Override
    public void demoteGroupParticipants(JidProvider groupProvider, Collection<? extends JidProvider> toDemoteProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: demoteGroupParticipants(..) is not stubbed");
    }

    @Override
    public void favoriteSticker(String stickerHash) {
        throw new UnsupportedOperationException("TestWhatsAppClient: favoriteSticker(..) is not stubbed");
    }

    @Override
    public void unfavoriteSticker(String stickerHash) {
        throw new UnsupportedOperationException("TestWhatsAppClient: unfavoriteSticker(..) is not stubbed");
    }

    @Override
    public void removeRecentSticker(String stickerHash) {
        throw new UnsupportedOperationException("TestWhatsAppClient: removeRecentSticker(..) is not stubbed");
    }

    @Override
    public ChatMessageInfo createPoll(PollCreate create) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createPoll(..) is not stubbed");
    }

    @Override
    public AckResult votePoll(MessageKey pollKey, List<String> selectedOptions) {
        throw new UnsupportedOperationException("TestWhatsAppClient: votePoll(..) is not stubbed");
    }

    @Override
    public AckResult closePoll(MessageKey pollKey) {
        throw new UnsupportedOperationException("TestWhatsAppClient: closePoll(..) is not stubbed");
    }

    @Override
    public void sendBotWelcomeRequest(JidProvider botJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendBotWelcomeRequest(..) is not stubbed");
    }

    @Override
    public void renameAiThread(String chatJid, String threadId, String newName) {
        throw new UnsupportedOperationException("TestWhatsAppClient: renameAiThread(..) is not stubbed");
    }

    @Override
    public void deleteAiThread(String chatJid, String threadId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteAiThread(..) is not stubbed");
    }

    @Override
    public void favoriteChat(JidProvider chatProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: favoriteChat(..) is not stubbed");
    }

    @Override
    public void unfavoriteChat(JidProvider chatProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: unfavoriteChat(..) is not stubbed");
    }

    @Override
    public String addNoteToChat(JidProvider chatProvider, String noteText) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addNoteToChat(..) is not stubbed");
    }

    @Override
    public void editNoteOnChat(JidProvider chatProvider, String noteId, String newText) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editNoteOnChat(..) is not stubbed");
    }

    @Override
    public void deleteNoteFromChat(JidProvider chatProvider, String noteId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteNoteFromChat(..) is not stubbed");
    }

    @Override
    public AckResult pinMessage(MessageKey msgKey) {
        throw new UnsupportedOperationException("TestWhatsAppClient: pinMessage(..) is not stubbed");
    }

    @Override
    public AckResult unpinMessage(MessageKey msgKey) {
        throw new UnsupportedOperationException("TestWhatsAppClient: unpinMessage(..) is not stubbed");
    }

    @Override
    public void editLocale(String locale) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editLocale(..) is not stubbed");
    }

    @Override
    public void editDisableLinkPreviews(boolean disabled) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editDisableLinkPreviews(..) is not stubbed");
    }

    @Override
    public void editTwentyFourHourFormat(boolean twentyFourHourFormat) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editTwentyFourHourFormat(..) is not stubbed");
    }

    @Override
    public void editAIFeaturesEnabled(boolean enabled) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editAIFeaturesEnabled(..) is not stubbed");
    }

    @Override
    public void editUnarchiveChatsOnNewMessage(boolean unarchive) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editUnarchiveChatsOnNewMessage(..) is not stubbed");
    }

    @Override
    public void editNotificationActivity(NotificationActivitySettingAction.NotificationActivitySetting setting) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editNotificationActivity(..) is not stubbed");
    }

    @Override
    public Optional<CtwaAccessTokenSession> queryAccessTokenAndSessionCookies(String code, JidProvider fromUserJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryAccessTokenAndSessionCookies(..) is not stubbed");
    }

    @Override
    public Optional<String> queryAccountNonce(String identifierScope) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryAccountNonce(..) is not stubbed");
    }

    @Override
    public Optional<BusinessEligibility> queryBusinessEligibility(boolean featuresMetaVerified, boolean featuresMarketingMessages, boolean featuresGenai) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessEligibility(..) is not stubbed");
    }

    @Override
    public Optional<BusinessLinkedAccounts> queryLinkedAccounts() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryLinkedAccounts(..) is not stubbed");
    }

    @Override
    public Optional<BusinessDataSharingConsent> queryBusinessPrivacySetting() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessPrivacySetting(..) is not stubbed");
    }

    @Override
    public void editBusinessPrivacySetting(BusinessDataSharingConsent dataSharingConsent) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editBusinessPrivacySetting(..) is not stubbed");
    }

    @Override
    public void updateMessageFeedbackPreference(MessageFeedbackAction action, JidProvider jidProvider, String feedback) {
        throw new UnsupportedOperationException("TestWhatsAppClient: updateMessageFeedbackPreference(..) is not stubbed");
    }

    @Override
    public Optional<BusinessMeteredMessagingCheckout> queryMeteredMessagingCheckout(List<? extends JidProvider> participantsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryMeteredMessagingCheckout(..) is not stubbed");
    }

    @Override
    public Optional<BusinessMeteredMessagingCheckout> queryMeteredMessagingCheckout(List<? extends JidProvider> participantsProvider, boolean useAdAccount, boolean skipDedupe, String offerId, List<BusinessMeteredMessagingPendingCampaign> pendingCampaigns) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryMeteredMessagingCheckout(..) is not stubbed");
    }

    @Override
    public Optional<CtwaSilentNonceResult> querySilentNonce(JidProvider fromUserJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: querySilentNonce(..) is not stubbed");
    }

    @Override
    public boolean sendAccountRecoveryNonce(JidProvider fromUserJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendAccountRecoveryNonce(..) is not stubbed");
    }

    @Override
    public void uploadAdMedia(CtwaAdMediaEntry media, List<CtwaAdMediaEntry> mediaList) {
        throw new UnsupportedOperationException("TestWhatsAppClient: uploadAdMedia(..) is not stubbed");
    }

    @Override
    public void editPaymentsTosV3Acceptance(int acceptPayTosVersion, PaymentsTosV3ConsumerVariant variant) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editPaymentsTosV3Acceptance(..) is not stubbed");
    }

    @Override
    public BrazilCustomPaymentMethod createBrazilCustomPaymentMethod(BrazilCustomPaymentMethodCreate create) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createBrazilCustomPaymentMethod(..) is not stubbed");
    }

    @Override
    public NewsletterPublishAck publishNewsletterMessage(JidProvider newsletterJidProvider, NewsletterPublishMessageRequest request) {
        throw new UnsupportedOperationException("TestWhatsAppClient: publishNewsletterMessage(..) is not stubbed");
    }

    @Override
    public NewsletterPublishAck publishNewsletterStatus(JidProvider newsletterJidProvider, NewsletterPublishStatusRequest request) {
        throw new UnsupportedOperationException("TestWhatsAppClient: publishNewsletterStatus(..) is not stubbed");
    }

    @Override
    public Node sendNode(IqOperation.Request request) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendNode(..) is not stubbed");
    }

    @Override
    public List<BusinessProduct> queryBusinessCatalogProducts(JidProvider catalogJidProvider, List<String> productIds, int width, int height) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessCatalogProducts(..) is not stubbed");
    }

    @Override
    public List<BusinessProduct> queryBusinessCatalogProducts(JidProvider catalogJidProvider, List<String> productIds, int width, int height, String directConnectionEncryptedInfo) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBusinessCatalogProducts(..) is not stubbed");
    }

    @Override
    public void editBusinessCoverPhoto(long id, Instant ts, byte[] token) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editBusinessCoverPhoto(..) is not stubbed");
    }

    @Override
    public void clearDirtyBits(Map<String, Long> dirtyBits) {
        throw new UnsupportedOperationException("TestWhatsAppClient: clearDirtyBits(..) is not stubbed");
    }

    @Override
    public MediaConnection queryMediaConns() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryMediaConns(..) is not stubbed");
    }

    @Override
    public void deleteTosNotice(String noticeId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteTosNotice(..) is not stubbed");
    }

    @Override
    public void acknowledgeTosNotices(List<String> noticeIds) {
        throw new UnsupportedOperationException("TestWhatsAppClient: acknowledgeTosNotices(..) is not stubbed");
    }

    @Override
    public Optional<IdentityKeyDigest> queryKeyDigest() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryKeyDigest(..) is not stubbed");
    }

    @Override
    public Map<Jid, IdentityKey> queryIdentityKeys(List<? extends JidProvider> deviceJidsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryIdentityKeys(..) is not stubbed");
    }

    @Override
    public void rotateSignedPreKey(SignalSignedPreKey signedPreKey) {
        throw new UnsupportedOperationException("TestWhatsAppClient: rotateSignedPreKey(..) is not stubbed");
    }

    @Override
    public void uploadSignalPreKeys(SignalPreKeyBundle bundle) {
        throw new UnsupportedOperationException("TestWhatsAppClient: uploadSignalPreKeys(..) is not stubbed");
    }

    @Override
    public void uploadRegistrationPreKeys(SignalPreKeyBundle bundle) {
        throw new UnsupportedOperationException("TestWhatsAppClient: uploadRegistrationPreKeys(..) is not stubbed");
    }

    @Override
    public Optional<PrivateStatsToken> issuePrivateStatsToken(byte[] blindedCredential, byte[] projectName) {
        throw new UnsupportedOperationException("TestWhatsAppClient: issuePrivateStatsToken(..) is not stubbed");
    }

    @Override
    public AppStateSyncResult syncAppState(List<AppStateSyncCollection> collections) {
        throw new UnsupportedOperationException("TestWhatsAppClient: syncAppState(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addListener(WhatsAppClientListener listener) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient removeListener(WhatsAppClientListener listener) {
        throw new UnsupportedOperationException("TestWhatsAppClient: removeListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addChatsListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<Chat>> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addChatsListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addContactsListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<Contact>> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addContactsListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addStatusListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<ChatMessageInfo>> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addStatusListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addNodeSentListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Node> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addNodeSentListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addLoggedInListener(WhatsappClientListenerConsumer.Unary<WhatsAppClient> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addLoggedInListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addCallListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, IncomingCall> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addCallListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addWebHistorySyncPastParticipantsListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, Collection<GroupPastParticipant>> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addWebHistorySyncPastParticipantsListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addDisconnectedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, WhatsAppClientDisconnectReason> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addDisconnectedListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addWebAppPrimaryFeaturesListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, List<String>> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addWebAppPrimaryFeaturesListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addContactPresenceListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, Jid> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addContactPresenceListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addNewslettersListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Collection<Newsletter>> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addNewslettersListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addNodeReceivedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Node> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addNodeReceivedListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addWebAppStateActionListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, SyncAction, String> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addWebAppStateActionListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addWebHistorySyncMessagesListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Chat, Boolean> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addWebHistorySyncMessagesListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addNewStatusListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, ChatMessageInfo> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addNewStatusListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addAccountTypeChangedListener(WhatsappClientListenerConsumer.Quaternary<WhatsAppClient, Jid, ADVEncryptionType, ADVEncryptionType> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addAccountTypeChangedListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addAboutChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, String, String> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addAboutChangedListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addNewMessageListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, MessageInfo> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addNewMessageListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addMessageDeletedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, MessageInfo, Boolean> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addMessageDeletedListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addPrivacySettingChangedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, PrivacySettingEntry> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addPrivacySettingChangedListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addWebHistorySyncProgressListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Integer, Boolean> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addWebHistorySyncProgressListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addProfilePictureChangedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Jid> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addProfilePictureChangedListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addMessageStatusListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, MessageInfo> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addMessageStatusListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addNameChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, String, String> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addNameChangedListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addMessageReplyListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, MessageInfo, MessageInfo> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addMessageReplyListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addDeviceIdentityChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, Set<Jid>> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addDeviceIdentityChangedListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addNewContactListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Contact> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addNewContactListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addContactBlockedListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Jid> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addContactBlockedListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addContactTextStatusListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, Jid, ContactTextStatus> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addContactTextStatusListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addLocaleChangedListener(WhatsappClientListenerConsumer.Ternary<WhatsAppClient, String, String> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addLocaleChangedListener(..) is not stubbed");
    }

    @Override
    public WhatsAppClient addRegistrationCodeListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, Long> consumer) {
        throw new UnsupportedOperationException("TestWhatsAppClient: addRegistrationCodeListener(..) is not stubbed");
    }

    @Override
    public void acknowledgeGroup(JidProvider groupProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: acknowledgeGroup(..) is not stubbed");
    }

    @Override
    public boolean acceptGroupAdd(GroupAddAccept accept) {
        throw new UnsupportedOperationException("TestWhatsAppClient: acceptGroupAdd(..) is not stubbed");
    }

    @Override
    public List<GroupMetadata> batchQueryGroupInfo(Collection<? extends JidProvider> groupsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: batchQueryGroupInfo(..) is not stubbed");
    }

    @Override
    public Map<Jid, GroupParticipantStatus> cancelGroupMembershipRequests(JidProvider groupProvider, Collection<? extends JidProvider> applicantsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: cancelGroupMembershipRequests(..) is not stubbed");
    }

    @Override
    public SubgroupSuggestionResult suggestNewSubgroup(SubgroupSuggestionNew suggestion) {
        throw new UnsupportedOperationException("TestWhatsAppClient: suggestNewSubgroup(..) is not stubbed");
    }

    @Override
    public SubgroupSuggestionResult suggestExistingSubgroups(JidProvider communityProvider, Collection<? extends JidProvider> candidateGroupsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: suggestExistingSubgroups(..) is not stubbed");
    }

    @Override
    public Optional<GroupMetadata> deleteParentGroup(JidProvider communityProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: deleteParentGroup(..) is not stubbed");
    }

    @Override
    public List<GroupProfilePicture> queryGroupProfilePictures(Collection<? extends JidProvider> groupsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupProfilePictures(..) is not stubbed");
    }

    @Override
    public Optional<GroupMetadata> queryInviteGroupInfo(String inviteCode) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryInviteGroupInfo(..) is not stubbed");
    }

    @Override
    public Optional<ChatMetadata> queryLinkedGroup(JidProvider communityProvider, String queryLinkedType, JidProvider queryLinkedJidProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryLinkedGroup(..) is not stubbed");
    }

    @Override
    public Map<Jid, Jid> queryLinkedGroupsParticipants(JidProvider communityProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryLinkedGroupsParticipants(..) is not stubbed");
    }

    @Override
    public List<GroupMembershipApprovalRequest> queryGroupMembershipApprovalRequests(JidProvider groupProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupMembershipApprovalRequests(..) is not stubbed");
    }

    @Override
    public List<GroupMetadata> queryParticipatingGroups(boolean includeParticipants, boolean includeDescription) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryParticipatingGroups(..) is not stubbed");
    }

    @Override
    public List<GroupMessageReport> queryReportedMessages(JidProvider groupProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryReportedMessages(..) is not stubbed");
    }

    @Override
    public boolean joinLinkedGroup(JidProvider communityProvider, JidProvider subgroupProvider, String joinLinkedGroupType) {
        throw new UnsupportedOperationException("TestWhatsAppClient: joinLinkedGroup(..) is not stubbed");
    }

    @Override
    public List<LinkedSubgroupResult> linkSubgroups(JidProvider communityProvider, List<? extends JidProvider> subgroupsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: linkSubgroups(..) is not stubbed");
    }

    @Override
    public void reportGroupMessages(JidProvider groupProvider, String messageId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: reportGroupMessages(..) is not stubbed");
    }

    @Override
    public Map<Jid, GroupParticipantStatus> revokeGroupRequestCode(JidProvider groupProvider, List<? extends JidProvider> participantsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: revokeGroupRequestCode(..) is not stubbed");
    }

    @Override
    public Optional<GroupMetadata> editGroupMetadata(GroupMetadataEdit edit) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editGroupMetadata(..) is not stubbed");
    }

    @Override
    public List<UnlinkedSubgroupResult> unlinkSubgroups(JidProvider communityProvider, List<? extends JidProvider> subgroupsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: unlinkSubgroups(..) is not stubbed");
    }

    @Override
    public NewsletterMessageHistory queryNewsletterMessageUpdates(JidProvider newsletterProvider, int count, NewsletterHistoryDirection direction) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterMessageUpdates(..) is not stubbed");
    }

    @Override
    public NewsletterMessageHistory queryNewsletterMessageUpdates(JidProvider newsletterProvider, int count, Long since, NewsletterHistoryDirection direction) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterMessageUpdates(..) is not stubbed");
    }

    @Override
    public NewsletterMessageHistory queryNewsletterMessages(JidProvider newsletterProvider, int count) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterMessages(..) is not stubbed");
    }

    @Override
    public NewsletterMessageHistory queryNewsletterMessages(JidProvider newsletterProvider, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterMessages(..) is not stubbed");
    }

    @Override
    public NewsletterMessageHistory queryNewsletterMessages(String inviteKey, int count) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterMessages(..) is not stubbed");
    }

    @Override
    public NewsletterMessageHistory queryNewsletterMessages(String inviteKey, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterMessages(..) is not stubbed");
    }

    @Override
    public List<NewsletterQuestionResponse> queryNewsletterResponses(JidProvider newsletterProvider, long questionResponsesServerId, int questionResponsesCount) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterResponses(..) is not stubbed");
    }

    @Override
    public List<NewsletterQuestionResponse> queryNewsletterResponses(JidProvider newsletterProvider, long questionResponsesServerId, int questionResponsesCount, String questionResponsesBefore, NewsletterResponsesFilter filter, String searchText) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterResponses(..) is not stubbed");
    }

    @Override
    public NewsletterStatusHistory queryNewsletterStatusUpdates(JidProvider newsletterProvider, int count, NewsletterHistoryDirection direction) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterStatusUpdates(..) is not stubbed");
    }

    @Override
    public NewsletterStatusHistory queryNewsletterStatusUpdates(JidProvider newsletterProvider, int count, Long since, NewsletterHistoryDirection direction) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterStatusUpdates(..) is not stubbed");
    }

    @Override
    public NewsletterStatusHistory queryNewsletterStatuses(JidProvider newsletterProvider, int count) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterStatuses(..) is not stubbed");
    }

    @Override
    public NewsletterStatusHistory queryNewsletterStatuses(JidProvider newsletterProvider, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterStatuses(..) is not stubbed");
    }

    @Override
    public NewsletterStatusHistory queryNewsletterStatuses(String inviteKey, int count) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterStatuses(..) is not stubbed");
    }

    @Override
    public NewsletterStatusHistory queryNewsletterStatuses(String inviteKey, int count, NewsletterViewerRole viewRole, NewsletterHistoryDirection direction) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterStatuses(..) is not stubbed");
    }

    @Override
    public Map<Jid, List<NewsletterMyAddOn>> queryNewsletterMyAddOns(int limit, JidProvider newsletterProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryNewsletterMyAddOns(..) is not stubbed");
    }

    @Override
    public Duration subscribeToNewsletterLiveUpdates(JidProvider newsletterProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: subscribeToNewsletterLiveUpdates(..) is not stubbed");
    }

    @Override
    public Optional<AbPropsBundle> queryExperimentConfig(String propsHash, Integer propsRefreshId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryExperimentConfig(..) is not stubbed");
    }

    @Override
    public Optional<AbPropsBundle> queryGroupExperimentConfig(JidProvider groupProvider, String propsHash) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryGroupExperimentConfig(..) is not stubbed");
    }

    @Override
    public Optional<BotDirectory> queryBotList(String botV, String botBhash, List<? extends JidProvider> botArgsProvider) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryBotList(..) is not stubbed");
    }

    @Override
    public Optional<String> reportBug(BugReport report) {
        throw new UnsupportedOperationException("TestWhatsAppClient: reportBug(..) is not stubbed");
    }

    @Override
    public void reportInAppCommsEvent(InAppCommsEvent event) {
        throw new UnsupportedOperationException("TestWhatsAppClient: reportInAppCommsEvent(..) is not stubbed");
    }

    @Override
    public void acknowledgeOfflineBatch(int offlineBatchCount) {
        throw new UnsupportedOperationException("TestWhatsAppClient: acknowledgeOfflineBatch(..) is not stubbed");
    }

    @Override
    public void enableActiveMode() {
        throw new UnsupportedOperationException("TestWhatsAppClient: enableActiveMode(..) is not stubbed");
    }

    @Override
    public void enablePassiveMode() {
        throw new UnsupportedOperationException("TestWhatsAppClient: enablePassiveMode(..) is not stubbed");
    }

    @Override
    public Optional<PreKeyBundleResult> queryPreKeyBundles(List<PreKeyBundleRequest> users) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryPreKeyBundles(..) is not stubbed");
    }

    @Override
    public Optional<PreKeyBundleResult> queryMissingPreKeys(List<MissingPreKeyUserRequest> users) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryMissingPreKeys(..) is not stubbed");
    }

    @Override
    public Optional<SignedAttributionCredential> signAnonymousAttributionCredential(byte[] blindedCredentialElementValue, String projectNameElementValue) {
        throw new UnsupportedOperationException("TestWhatsAppClient: signAnonymousAttributionCredential(..) is not stubbed");
    }

    @Override
    public boolean queryPublicAnnouncementBlocked() {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryPublicAnnouncementBlocked(..) is not stubbed");
    }

    @Override
    public void editPublicAnnouncementBlocked(PsaChatBlockAction blockingAction) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editPublicAnnouncementBlocked(..) is not stubbed");
    }

    @Override
    public void editPushConfig(PushConfig config) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editPushConfig(..) is not stubbed");
    }

    @Override
    public void publishViewReceipt(ViewReceipt receipt) {
        throw new UnsupportedOperationException("TestWhatsAppClient: publishViewReceipt(..) is not stubbed");
    }

    @Override
    public void sendStatsBuffer(Instant addT, byte[] addElementValue) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendStatsBuffer(..) is not stubbed");
    }

    @Override
    public void sendSupportFeedback(JidProvider fromProvider, String messageId, List<String> feedbackKinds) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendSupportFeedback(..) is not stubbed");
    }

    @Override
    public Optional<SupportTicketAcknowledgement> sendSupportContactForm(SupportContactForm form) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendSupportContactForm(..) is not stubbed");
    }

    @Override
    public void reportIndividualForSpam(IndividualSpamReport report) {
        throw new UnsupportedOperationException("TestWhatsAppClient: reportIndividualForSpam(..) is not stubbed");
    }

    @Override
    public void reportGroupForSpam(GroupSpamReport report) {
        throw new UnsupportedOperationException("TestWhatsAppClient: reportGroupForSpam(..) is not stubbed");
    }

    @Override
    public void reportNewsletterForSpam(NewsletterSpamReport report) {
        throw new UnsupportedOperationException("TestWhatsAppClient: reportNewsletterForSpam(..) is not stubbed");
    }

    @Override
    public void reportStatus(MessageInfo status, String reason, String subject) {
        throw new UnsupportedOperationException("TestWhatsAppClient: reportStatus(..) is not stubbed");
    }

    @Override
    public void joinUnifiedSession(String unifiedSessionId) {
        throw new UnsupportedOperationException("TestWhatsAppClient: joinUnifiedSession(..) is not stubbed");
    }

    @Override
    public Optional<UserNoticeBundle> queryPendingUserNotices(Instant getUserDisclosuresT) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryPendingUserNotices(..) is not stubbed");
    }

    @Override
    public List<UserNoticeStage> queryUserNoticeStages(List<UserNoticeStageQuery> queries) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryUserNoticeStages(..) is not stubbed");
    }

    @Override
    public CallLink createCallLink(CallLinkCreate create) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createCallLink(..) is not stubbed");
    }

    @Override
    public Optional<CallLink> queryCallLink(String token, CallLinkMedia media, String action) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryCallLink(..) is not stubbed");
    }

    @Override
    public void editCallLinkWaitingRoom(boolean enabled, String linkToken, CallLinkMedia media) {
        throw new UnsupportedOperationException("TestWhatsAppClient: editCallLinkWaitingRoom(..) is not stubbed");
    }

    @Override
    public Optional<FederatedIdentityState> checkFederatedIdentityExists(Instant timestamp) {
        throw new UnsupportedOperationException("TestWhatsAppClient: checkFederatedIdentityExists(..) is not stubbed");
    }

    @Override
    public Optional<FederatedIdentityPing> sendFederatedIdentityPing(FederatedRsaEncryption encryption, Instant timestamp, byte[] fbid) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendFederatedIdentityPing(..) is not stubbed");
    }

    @Override
    public Optional<FederatedIdentityCertificate> queryFederatedIdentityCertificate(Instant timestamp, boolean hasPayloadEncCertificates, boolean hasPasswordPem) {
        throw new UnsupportedOperationException("TestWhatsAppClient: queryFederatedIdentityCertificate(..) is not stubbed");
    }

    @Override
    public Optional<FederatedAccessTokenRefresh> refreshFederatedIdentityAccessTokens(FederatedRsaEncryption encryption, Instant timestamp, byte[] fbid) {
        throw new UnsupportedOperationException("TestWhatsAppClient: refreshFederatedIdentityAccessTokens(..) is not stubbed");
    }

    @Override
    public Optional<FederatedEncryptedAction> sendFederatedIdentityEncryptedPayload(FederatedRsaEncryption encryption, Instant timestamp, byte[] fbid, byte[] action) {
        throw new UnsupportedOperationException("TestWhatsAppClient: sendFederatedIdentityEncryptedPayload(..) is not stubbed");
    }

    @Override
    public FederatedEnterpriseCustomer createEnterpriseAuthenticatedCustomer(EnterpriseAuthenticatedCustomerCreate create) {
        throw new UnsupportedOperationException("TestWhatsAppClient: createEnterpriseAuthenticatedCustomer(..) is not stubbed");
    }


}
