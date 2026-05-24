package com.github.auties00.cobalt.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.CallOptions;
import com.github.auties00.cobalt.call.internal.CallService;
import com.github.auties00.cobalt.call.IncomingCall;
import com.github.auties00.cobalt.call.CallEndReason;
import com.github.auties00.cobalt.call.internal.signaling.CallStanza;
import com.github.auties00.cobalt.device.DefaultDeviceService;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.*;
import com.github.auties00.cobalt.media.DefaultMediaConnectionService;
import com.github.auties00.cobalt.media.MediaConnectionService;
import com.github.auties00.cobalt.media.transcode.MediaTranscoderService;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.message.addon.EncMessageFactory;
import com.github.auties00.cobalt.ack.AckResult;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.message.send.stanza.NewsletterStanza;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.bot.AiThreadTitleBuilder;
import com.github.auties00.cobalt.model.bot.profile.*;
import com.github.auties00.cobalt.model.business.*;
import com.github.auties00.cobalt.model.business.cart.*;
import com.github.auties00.cobalt.model.business.catalog.*;
import com.github.auties00.cobalt.model.business.compliance.*;
import com.github.auties00.cobalt.model.business.ctwa.*;
import com.github.auties00.cobalt.model.business.linking.*;
import com.github.auties00.cobalt.model.business.marketing.*;
import com.github.auties00.cobalt.model.business.order.BusinessOrder;
import com.github.auties00.cobalt.model.business.postcode.BusinessPostcodeVerification;
import com.github.auties00.cobalt.model.business.postcode.BusinessPostcodeVerificationBuilder;
import com.github.auties00.cobalt.model.business.postcode.BusinessPostcodeVerificationResult;
import com.github.auties00.cobalt.model.business.profile.*;
import com.github.auties00.cobalt.model.call.*;
import com.github.auties00.cobalt.model.chat.*;
import com.github.auties00.cobalt.model.chat.community.*;
import com.github.auties00.cobalt.model.chat.group.*;
import com.github.auties00.cobalt.model.contact.*;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.error.DisconnectCode;
import com.github.auties00.cobalt.model.federated.*;
import com.github.auties00.cobalt.model.jid.*;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.message.*;
import com.github.auties00.cobalt.model.message.call.ScheduledCallCreationMessage;
import com.github.auties00.cobalt.model.message.call.ScheduledCallCreationMessageBuilder;
import com.github.auties00.cobalt.model.message.call.ScheduledCallEditMessage;
import com.github.auties00.cobalt.model.message.call.ScheduledCallEditMessageBuilder;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.media.*;
import com.github.auties00.cobalt.model.message.poll.*;
import com.github.auties00.cobalt.model.message.status.StatusPSA;
import com.github.auties00.cobalt.model.message.system.PinInChatMessage;
import com.github.auties00.cobalt.model.message.system.PinInChatMessageBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessageBuilder;
import com.github.auties00.cobalt.model.newsletter.*;
import com.github.auties00.cobalt.model.payment.BrazilCustomPaymentMethod;
import com.github.auties00.cobalt.model.payment.BrazilCustomPaymentMethodBuilder;
import com.github.auties00.cobalt.model.payment.BrazilCustomPaymentMethodCreate;
import com.github.auties00.cobalt.model.payment.BrazilCustomPaymentMethodMetadataEntry;
import com.github.auties00.cobalt.model.payment.PaymentsTosV3ConsumerVariant;
import com.github.auties00.cobalt.model.preference.*;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.privacy.*;
import com.github.auties00.cobalt.model.reporting.*;
import com.github.auties00.cobalt.model.setting.*;
import com.github.auties00.cobalt.model.setting.notice.UserNotice;
import com.github.auties00.cobalt.model.setting.notice.UserNoticeBundle;
import com.github.auties00.cobalt.model.setting.notice.UserNoticeStage;
import com.github.auties00.cobalt.model.setting.notice.UserNoticeStageQuery;
import com.github.auties00.cobalt.model.setting.privacy.*;
import com.github.auties00.cobalt.model.setting.push.PushConfig;
import com.github.auties00.cobalt.model.signal.*;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.model.sync.action.bot.AiThreadRenameAction;
import com.github.auties00.cobalt.model.sync.action.bot.BotWelcomeRequestAction;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.action.business.BroadcastListParticipantAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListAction;
import com.github.auties00.cobalt.model.sync.action.business.CtwaPerCustomerDataSharingAction;
import com.github.auties00.cobalt.model.sync.action.call.CallLogAction;
import com.github.auties00.cobalt.model.sync.action.chat.*;
import com.github.auties00.cobalt.model.sync.action.chat.QuickReplyAction;
import com.github.auties00.cobalt.model.sync.action.contact.*;
import com.github.auties00.cobalt.model.sync.action.device.ExternalWebBetaAction;
import com.github.auties00.cobalt.model.sync.action.device.NuxAction;
import com.github.auties00.cobalt.model.sync.action.device.TimeFormatAction;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethod;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethodsAction;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethodsActionBuilder;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosActionBuilder;
import com.github.auties00.cobalt.model.sync.action.media.*;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingDisableLinkPreviewsAction;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingRelayAllCalls;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivateProcessingSettingAction;
import com.github.auties00.cobalt.model.sync.action.setting.DetectedOutcomesStatusAction;
import com.github.auties00.cobalt.model.sync.action.setting.LocaleSetting;
import com.github.auties00.cobalt.model.sync.action.setting.NotificationActivitySettingAction;
import com.github.auties00.cobalt.model.sync.action.setting.PushNameSetting;
import com.github.auties00.cobalt.model.sync.action.setting.UnarchiveChatsSetting;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.iq.biz.*;
import com.github.auties00.cobalt.node.iq.ctwa.IqQueryCtwaContextRequest;
import com.github.auties00.cobalt.node.iq.ctwa.IqQueryCtwaContextResponse;
import com.github.auties00.cobalt.node.iq.debug.IqDebugGdprReportType;
import com.github.auties00.cobalt.node.iq.debug.IqDebugGdprRequest;
import com.github.auties00.cobalt.node.iq.debug.IqDebugGdprResponse;
import com.github.auties00.cobalt.node.iq.dirty.IqClearDirtyBitsRequest;
import com.github.auties00.cobalt.node.iq.dirty.IqClearDirtyBitsResponse;
import com.github.auties00.cobalt.node.iq.disappearing.IqQueryDisappearingModeRequest;
import com.github.auties00.cobalt.node.iq.disappearing.IqQueryDisappearingModeResponse;
import com.github.auties00.cobalt.node.iq.encrypt.*;
import com.github.auties00.cobalt.node.iq.group.*;
import com.github.auties00.cobalt.node.iq.privacy.IqSetPrivacyTokensRequest;
import com.github.auties00.cobalt.node.iq.privacy.IqSetPrivacyTokensTokenType;
import com.github.auties00.cobalt.node.iq.profilepicture.IqSendProfilePictureRequest;
import com.github.auties00.cobalt.node.iq.profilepicture.IqSendProfilePictureResponse;
import com.github.auties00.cobalt.node.iq.push.IqGetPushServerSettingsRequest;
import com.github.auties00.cobalt.node.iq.push.IqGetPushServerSettingsResponse;
import com.github.auties00.cobalt.node.iq.stats.IqIssuePrivateStatsTokenRequest;
import com.github.auties00.cobalt.node.iq.stats.IqIssuePrivateStatsTokenResponse;
import com.github.auties00.cobalt.node.iq.status.IqSetAboutRequest;
import com.github.auties00.cobalt.node.iq.status.IqSetAboutResponse;
import com.github.auties00.cobalt.node.iq.syncd.IqSyncdServerSyncCollectionState;
import com.github.auties00.cobalt.node.iq.syncd.IqSyncdServerSyncRequest;
import com.github.auties00.cobalt.node.iq.syncd.IqSyncdServerSyncRequestCollection;
import com.github.auties00.cobalt.node.iq.syncd.IqSyncdServerSyncResponse;
import com.github.auties00.cobalt.node.iq.tos.*;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.mex.json.business.*;
import com.github.auties00.cobalt.node.mex.json.community.*;
import com.github.auties00.cobalt.node.mex.json.community.FetchAllSubgroupsMexResponse.DefaultSubGroup;
import com.github.auties00.cobalt.node.mex.json.community.FetchAllSubgroupsMexResponse.SubGroups;
import com.github.auties00.cobalt.node.mex.json.group.*;
import com.github.auties00.cobalt.node.mex.json.misc.*;
import com.github.auties00.cobalt.node.mex.json.newsletter.*;
import com.github.auties00.cobalt.node.mex.json.user.*;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.abprops.SmaxAbPropsGetExperimentConfigRequest;
import com.github.auties00.cobalt.node.smax.abprops.SmaxAbPropsGetExperimentConfigResponse;
import com.github.auties00.cobalt.node.smax.abprops.SmaxAbPropsGetGroupExperimentConfigRequest;
import com.github.auties00.cobalt.node.smax.abprops.SmaxAbPropsGetGroupExperimentConfigResponse;
import com.github.auties00.cobalt.node.smax.account.*;
import com.github.auties00.cobalt.node.smax.biz.*;
import com.github.auties00.cobalt.node.smax.bot.SmaxBotBotListRequest;
import com.github.auties00.cobalt.node.smax.bot.SmaxBotBotListResponse;
import com.github.auties00.cobalt.node.smax.bugreporting.SmaxBugReportingReportBugMediaUpload;
import com.github.auties00.cobalt.node.smax.bugreporting.SmaxBugReportingReportBugRequest;
import com.github.auties00.cobalt.node.smax.bugreporting.SmaxBugReportingReportBugResponse;
import com.github.auties00.cobalt.node.smax.chatstate.SmaxClientNotificationComposing;
import com.github.auties00.cobalt.node.smax.chatstate.SmaxClientNotificationPaused;
import com.github.auties00.cobalt.node.smax.chatstate.SmaxClientNotificationRequest;
import com.github.auties00.cobalt.node.smax.groups.*;
import com.github.auties00.cobalt.node.smax.inappcomms.SmaxInAppCommsEventRequest;
import com.github.auties00.cobalt.node.smax.inappcomms.SmaxInAppCommsEventResponse;
import com.github.auties00.cobalt.node.smax.message.SmaxMessagePublishNewsletterPayload;
import com.github.auties00.cobalt.node.smax.message.SmaxMessagePublishNewsletterRequest;
import com.github.auties00.cobalt.node.smax.message.SmaxMessagePublishNewsletterResponse;
import com.github.auties00.cobalt.node.smax.newsletters.*;
import com.github.auties00.cobalt.node.smax.offlinebatch.SmaxOfflineBatchRequest;
import com.github.auties00.cobalt.node.smax.passivemode.SmaxPassiveModeActiveIQRequest;
import com.github.auties00.cobalt.node.smax.passivemode.SmaxPassiveModeActiveIQResponse;
import com.github.auties00.cobalt.node.smax.passivemode.SmaxPassiveModePassiveIQRequest;
import com.github.auties00.cobalt.node.smax.passivemode.SmaxPassiveModePassiveIQResponse;
import com.github.auties00.cobalt.node.smax.prekeys.SmaxPreKeysFetchKeyBundlesRequest;
import com.github.auties00.cobalt.node.smax.prekeys.SmaxPreKeysFetchKeyBundlesResponse;
import com.github.auties00.cobalt.node.smax.prekeys.SmaxPreKeysFetchMissingPreKeysRequest;
import com.github.auties00.cobalt.node.smax.prekeys.SmaxPreKeysFetchMissingPreKeysResponse;
import com.github.auties00.cobalt.node.smax.presence.SmaxAvailabilityRequest;
import com.github.auties00.cobalt.node.smax.presence.SmaxSubscribeRequest;
import com.github.auties00.cobalt.node.smax.privacy.*;
import com.github.auties00.cobalt.node.smax.privatestats.SmaxPrivatestatsSignCredentialRequest;
import com.github.auties00.cobalt.node.smax.privatestats.SmaxPrivatestatsSignCredentialResponse;
import com.github.auties00.cobalt.node.smax.profilepicture.SmaxProfilePictureGetRequest;
import com.github.auties00.cobalt.node.smax.profilepicture.SmaxProfilePictureGetResponse;
import com.github.auties00.cobalt.node.smax.psa.*;
import com.github.auties00.cobalt.node.smax.pushconfig.SmaxPushConfigSetConfigVariant;
import com.github.auties00.cobalt.node.smax.pushconfig.SmaxPushConfigSetRequest;
import com.github.auties00.cobalt.node.smax.pushconfig.SmaxPushConfigSetResponse;
import com.github.auties00.cobalt.node.smax.pushconfig.SmaxPushConfigSetSetVariant;
import com.github.auties00.cobalt.node.smax.receipt.SmaxReceiptPublishViewRequest;
import com.github.auties00.cobalt.node.smax.receipt.SmaxReceiptPublishViewResponse;
import com.github.auties00.cobalt.node.smax.stats.SmaxStatsSendBufferRequest;
import com.github.auties00.cobalt.node.smax.stats.SmaxStatsSendBufferResponse;
import com.github.auties00.cobalt.node.smax.status.SmaxStatusPublishPostNewsletterStatusPayload;
import com.github.auties00.cobalt.node.smax.status.SmaxStatusPublishPostNewsletterStatusRequest;
import com.github.auties00.cobalt.node.smax.status.SmaxStatusPublishPostNewsletterStatusResponse;
import com.github.auties00.cobalt.node.smax.support.*;
import com.github.auties00.cobalt.node.smax.unifiedsession.SmaxUnifiedSessionShareRequest;
import com.github.auties00.cobalt.node.smax.usernotice.SmaxUserNoticeGetDisclosureStageByIdsRequest;
import com.github.auties00.cobalt.node.smax.usernotice.SmaxUserNoticeGetDisclosureStageByIdsResponse;
import com.github.auties00.cobalt.node.smax.usernotice.SmaxUserNoticeGetDisclosuresRequest;
import com.github.auties00.cobalt.node.smax.usernotice.SmaxUserNoticeGetDisclosuresResponse;
import com.github.auties00.cobalt.node.smax.voip.*;
import com.github.auties00.cobalt.node.smax.waffle.*;
import com.github.auties00.cobalt.node.usync.*;
import com.github.auties00.cobalt.node.usync.protocol.UsyncContactProtocol;
import com.github.auties00.cobalt.node.usync.result.ContactResult;
import com.github.auties00.cobalt.node.usync.result.UsyncProtocolError;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.props.DefaultABPropsService;
import com.github.auties00.cobalt.socket.WhatsAppSocketClient;
import com.github.auties00.cobalt.socket.WhatsAppSocketListener;
import com.github.auties00.cobalt.socket.WhatsAppSocketStanza;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.*;
import com.github.auties00.cobalt.sync.handler.AiThreadDeleteHandler;
import com.github.auties00.cobalt.sync.handler.BusinessBroadcastAssociationHandler;
import com.github.auties00.cobalt.sync.handler.PinChatHandler;
import com.github.auties00.cobalt.sync.handler.SyncdIndexUtils;
import com.github.auties00.cobalt.sync.key.SyncKeyUtils;
import com.github.auties00.cobalt.util.BusinessLabelConstants;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.cobalt.util.RandomIdUtils;
import com.github.auties00.cobalt.wam.DefaultWamService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.*;
import com.github.auties00.cobalt.wam.type.*;
import com.github.auties00.curve25519.Curve25519;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.key.SignalPreKeyPair;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.Collection;
import java.util.List;
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
 * @see WhatsAppClientBuilder
 * @see WhatsAppClientListener
 * @see WhatsAppClientErrorHandler
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@WhatsAppWebModule(moduleName = "WAWebSocketModel")
final class DefaultWhatsAppClient implements WhatsAppClient {
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
     * grows linearly: 1s, 2s, 3s, 4s between attempts 1 to 2, 2 to 3,
     * 3 to 4, 4 to 5.
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
     * Mirror of {@code WAWebBizCartConstants.CART_ITEM_MAX_QUANTITY}, the
     * default per-line maximum quantity applied to refreshed-cart entries
     * when the wire omits the {@code max_available} field.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCartConstants", exports = "CART_ITEM_MAX_QUANTITY",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int CART_ITEM_MAX_QUANTITY = 99;

    /**
     * The persisted session state (credentials, chats, contacts, Signal
     * keys, listeners) bound to this client.
     */
    private final WhatsAppStore store;
    /**
     * The call engine that backs {@link #startCall(Jid, boolean)},
     * {@link #acceptCall(IncomingCall, CallOptions)}, and
     * {@link #rejectCall(IncomingCall, CallEndReason)}. Owned here,
     * threaded into the socket stream and call receiver via
     * constructor DI; never exposed publicly.
     */
    private final CallService callService;
    /**
     * The strategy that decides how the client should react to errors
     * raised by any subsystem.
     */
    private final WhatsAppClientErrorHandler errorHandler;
    /**
     * The media-transcoder service that prepares outgoing media for
     * upload to the WhatsApp CDN; bound to this client and consulted
     * from {@link #uploadMedia(MediaProvider, InputStream)} and the
     * message-sending pipeline for link-preview decoration.
     */
    private final MediaTranscoderService mediaTranscoderService;
    /**
     * The CDN credentials service that owns the
     * {@code media_conn}-derived auth token, host list, and retry budgets
     * shared by every upload and download. Constructed once per session
     * and dependency-injected into every component that needs CDN
     * access. Updated by
     * {@link com.github.auties00.cobalt.stream.control.SuccessStreamHandler}
     * each time the periodic {@code media_conn} IQ reply lands.
     */
    private final MediaConnectionService mediaConnectionService;
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
     * {@code error_backoff} hint. Mirrors the {@code WAWebUsyncBackoff}
     * module-level singleton.
     */
    private final UsyncBackoff usyncBackoff;
    /**
     * The service that drives the web companion pairing ceremony.
     */
    private final CompanionPairingService companionPairingService;
    /**
     * The service that recovers app-state snapshots after a sync failure.
     */
    private final SnapshotRecoveryService snapshotRecoveryService;
    /**
     * Factory that builds outgoing pin-chat sync mutations.
     */
    private final PinChatMutationFactory pinChatMutationFactory;
    /**
     * Factory that builds outgoing mute-chat sync mutations.
     */
    private final MuteChatMutationFactory muteChatMutationFactory;
    /**
     * Factory that builds outgoing archive-chat sync mutations.
     */
    private final ArchiveChatMutationFactory archiveChatMutationFactory;
    /**
     * Factory that builds outgoing mark-chat-as-read sync mutations.
     */
    private final MarkChatAsReadMutationFactory markChatAsReadMutationFactory;
    /**
     * Factory that builds outgoing clear-chat sync mutations.
     */
    private final ClearChatMutationFactory clearChatMutationFactory;
    /**
     * Factory that builds outgoing delete-chat sync mutations.
     */
    private final DeleteChatMutationFactory deleteChatMutationFactory;
    /**
     * Factory that builds outgoing lock-chat sync mutations.
     */
    private final LockChatMutationFactory lockChatMutationFactory;
    /**
     * Factory that builds outgoing label-edit sync mutations.
     */
    private final LabelEditMutationFactory labelEditMutationFactory;
    /**
     * Factory that builds outgoing label-jid association sync mutations.
     */
    private final LabelAssociationMutationFactory labelAssociationMutationFactory;
    /**
     * Factory that builds outgoing label-reordering sync mutations.
     */
    private final LabelReorderingMutationFactory labelReorderingMutationFactory;
    /**
     * Factory that builds outgoing quick-reply sync mutations.
     */
    private final QuickReplyMutationFactory quickReplyMutationFactory;
    /**
     * Factory that builds outgoing chat-assignment sync mutations.
     */
    private final ChatAssignmentMutationFactory chatAssignmentMutationFactory;
    /**
     * Factory that builds outgoing chat-assignment-opened-status sync mutations.
     */
    private final ChatAssignmentOpenedStatusMutationFactory chatAssignmentOpenedStatusMutationFactory;
    /**
     * Factory that builds outgoing business-broadcast-list sync mutations.
     */
    private final BusinessBroadcastListMutationFactory businessBroadcastListMutationFactory;
    /**
     * Factory that builds outgoing business-broadcast-campaign sync mutations.
     */
    private final BusinessBroadcastCampaignMutationFactory businessBroadcastCampaignMutationFactory;
    /**
     * Factory that builds outgoing favourite-sticker sync mutations.
     */
    private final FavoriteStickerMutationFactory favoriteStickerMutationFactory;
    /**
     * Factory that builds outgoing remove-recent-sticker sync mutations.
     */
    private final RemoveRecentStickerMutationFactory removeRecentStickerMutationFactory;
    /**
     * Factory that builds outgoing bot-welcome-request sync mutations.
     */
    private final BotWelcomeRequestMutationFactory botWelcomeRequestMutationFactory;
    /**
     * Factory that builds outgoing AI-thread-rename sync mutations.
     */
    private final AiThreadRenameMutationFactory aiThreadRenameMutationFactory;
    /**
     * Factory that builds outgoing AI-thread-delete sync mutations.
     */
    private final AiThreadDeleteMutationFactory aiThreadDeleteMutationFactory;
    /**
     * Factory that builds outgoing favourites sync mutations.
     */
    private final FavoritesMutationFactory favoritesMutationFactory;
    /**
     * Factory that builds outgoing note-edit sync mutations.
     */
    private final NoteEditMutationFactory noteEditMutationFactory;
    /**
     * Factory that builds outgoing locale-setting sync mutations.
     */
    private final LocaleSettingMutationFactory localeSettingMutationFactory;
    /**
     * Factory that builds outgoing disable-link-previews sync mutations.
     */
    private final DisableLinkPreviewsMutationFactory disableLinkPreviewsMutationFactory;
    /**
     * Factory that builds outgoing time-format sync mutations.
     */
    private final TimeFormatMutationFactory timeFormatMutationFactory;
    /**
     * Factory that builds outgoing Maiba-AI-features-control sync mutations.
     */
    private final MaibaAIFeaturesControlMutationFactory maibaAIFeaturesControlMutationFactory;
    /**
     * Factory that builds outgoing unarchive-chats-setting sync mutations.
     */
    private final UnarchiveChatsSettingMutationFactory unarchiveChatsSettingMutationFactory;
    /**
     * Factory that builds outgoing notification-activity-setting sync mutations.
     */
    private final NotificationActivitySettingMutationFactory notificationActivitySettingMutationFactory;
    /**
     * Factory that builds outgoing status-privacy sync mutations.
     */
    private final StatusPrivacyMutationFactory statusPrivacyMutationFactory;
    /**
     * Factory that builds outgoing push-name-setting sync mutations.
     */
    private final PushNameSettingMutationFactory pushNameSettingMutationFactory;
    /**
     * Factory that builds outgoing contact-action sync mutations.
     */
    private final ContactActionMutationFactory contactActionMutationFactory;
    /**
     * Factory that builds outgoing call-log sync mutations.
     */
    private final CallLogMutationFactory callLogMutationFactory;
    /**
     * Factory that builds outgoing NUX (onboarding-hint) sync mutations.
     */
    private final NuxActionMutationFactory nuxActionMutationFactory;
    /**
     * Factory that builds outgoing always-relay-calls sync mutations.
     */
    private final VoipRelayAllCallsMutationFactory voipRelayAllCallsMutationFactory;
    /**
     * Factory that builds outgoing custom-payment-methods sync mutations.
     */
    private final CustomPaymentMethodsMutationFactory customPaymentMethodsMutationFactory;
    /**
     * Factory that builds outgoing payment-terms-of-service sync mutations.
     */
    private final PaymentTosMutationFactory paymentTosMutationFactory;
    /**
     * Factory that builds outgoing CTWA per-customer data-sharing sync mutations.
     */
    private final CtwaPerCustomerDataSharingMutationFactory ctwaPerCustomerDataSharingMutationFactory;
    /**
     * Factory that builds outgoing Web/Desktop beta opt-in sync mutations.
     */
    private final ExternalWebBetaMutationFactory externalWebBetaMutationFactory;
    /**
     * Factory that builds outgoing Private Processing privacy sync mutations.
     */
    private final PrivateProcessingSettingMutationFactory privateProcessingSettingMutationFactory;
    /**
     * Factory that builds outgoing automated-detections sync mutations.
     */
    private final DetectedOutcomesStatusMutationFactory detectedOutcomesStatusMutationFactory;
    /**
     * Factory that builds outgoing interactive-message disable-button sync mutations.
     */
    private final InteractiveMessageMutationFactory interactiveMessageMutationFactory;
    /**
     * Factory that builds outgoing recent-emoji-usage sync mutations.
     */
    private final RecentEmojiWeightsMutationFactory recentEmojiWeightsMutationFactory;
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
     *                                and forbidden otherwise
     * @param errorHandler            the recovery strategy for failures;
     *                                must not be {@code null}
     * @throws NullPointerException      if {@code store} or
     *                                   {@code errorHandler} is
     *                                   {@code null}
     * @throws IllegalArgumentException  if the web verification handler
     *                                   is required but missing, or
     *                                   present when it should not be
     */
    DefaultWhatsAppClient(WhatsAppStore store, WhatsAppClientVerificationHandler.Web webVerificationHandler, WhatsAppClientErrorHandler errorHandler) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler cannot be null");
        if ((store.clientType() == WhatsAppClientType.WEB) == (webVerificationHandler == null)) {
            throw new IllegalArgumentException("webVerificationHandler cannot be null when client type is WEB");
        }
        var sessionCipher = new SignalSessionCipher(store);
        var groupCipher = new SignalGroupCipher(store);
        this.abPropsService = new DefaultABPropsService(this);
        this.mediaConnectionService = new DefaultMediaConnectionService(abPropsService);
        this.mediaTranscoderService = new MediaTranscoderService(this, abPropsService, mediaConnectionService);
        this.wamService = new DefaultWamService(this, abPropsService);
        this.snapshotRecoveryService = new SnapshotRecoveryService(this, abPropsService, wamService);
        this.lidMigrationService = new LidMigrationService(this, abPropsService, wamService);
        this.webAppStateService = new WebAppStateService(this, abPropsService, lidMigrationService, snapshotRecoveryService, wamService, mediaConnectionService);
        this.pinChatMutationFactory = new PinChatMutationFactory();
        this.archiveChatMutationFactory = new ArchiveChatMutationFactory(pinChatMutationFactory);
        this.muteChatMutationFactory = new MuteChatMutationFactory(abPropsService);
        this.markChatAsReadMutationFactory = new MarkChatAsReadMutationFactory();
        this.clearChatMutationFactory = new ClearChatMutationFactory();
        this.deleteChatMutationFactory = new DeleteChatMutationFactory();
        this.lockChatMutationFactory = new LockChatMutationFactory(archiveChatMutationFactory, pinChatMutationFactory);
        this.labelEditMutationFactory = new LabelEditMutationFactory();
        this.labelAssociationMutationFactory = new LabelAssociationMutationFactory();
        this.labelReorderingMutationFactory = new LabelReorderingMutationFactory();
        this.quickReplyMutationFactory = new QuickReplyMutationFactory();
        this.chatAssignmentMutationFactory = new ChatAssignmentMutationFactory();
        this.chatAssignmentOpenedStatusMutationFactory = new ChatAssignmentOpenedStatusMutationFactory();
        this.businessBroadcastListMutationFactory = new BusinessBroadcastListMutationFactory();
        this.businessBroadcastCampaignMutationFactory = new BusinessBroadcastCampaignMutationFactory();
        this.favoriteStickerMutationFactory = new FavoriteStickerMutationFactory();
        this.removeRecentStickerMutationFactory = new RemoveRecentStickerMutationFactory();
        this.botWelcomeRequestMutationFactory = new BotWelcomeRequestMutationFactory();
        this.aiThreadRenameMutationFactory = new AiThreadRenameMutationFactory();
        this.aiThreadDeleteMutationFactory = new AiThreadDeleteMutationFactory();
        this.favoritesMutationFactory = new FavoritesMutationFactory();
        this.noteEditMutationFactory = new NoteEditMutationFactory();
        this.localeSettingMutationFactory = new LocaleSettingMutationFactory();
        this.disableLinkPreviewsMutationFactory = new DisableLinkPreviewsMutationFactory();
        this.timeFormatMutationFactory = new TimeFormatMutationFactory();
        this.maibaAIFeaturesControlMutationFactory = new MaibaAIFeaturesControlMutationFactory();
        this.unarchiveChatsSettingMutationFactory = new UnarchiveChatsSettingMutationFactory();
        this.notificationActivitySettingMutationFactory = new NotificationActivitySettingMutationFactory();
        this.statusPrivacyMutationFactory = new StatusPrivacyMutationFactory();
        this.pushNameSettingMutationFactory = new PushNameSettingMutationFactory();
        this.contactActionMutationFactory = new ContactActionMutationFactory();
        this.callLogMutationFactory = new CallLogMutationFactory();
        this.nuxActionMutationFactory = new NuxActionMutationFactory();
        this.voipRelayAllCallsMutationFactory = new VoipRelayAllCallsMutationFactory();
        this.customPaymentMethodsMutationFactory = new CustomPaymentMethodsMutationFactory();
        this.paymentTosMutationFactory = new PaymentTosMutationFactory();
        this.ctwaPerCustomerDataSharingMutationFactory = new CtwaPerCustomerDataSharingMutationFactory();
        this.externalWebBetaMutationFactory = new ExternalWebBetaMutationFactory();
        this.privateProcessingSettingMutationFactory = new PrivateProcessingSettingMutationFactory();
        this.detectedOutcomesStatusMutationFactory = new DetectedOutcomesStatusMutationFactory();
        this.interactiveMessageMutationFactory = new InteractiveMessageMutationFactory();
        this.recentEmojiWeightsMutationFactory = new RecentEmojiWeightsMutationFactory();
        this.inactiveGroupLidMigrationService = new InactiveGroupLidMigrationService(this, abPropsService);
        this.deviceService = new DefaultDeviceService(this, webAppStateService, abPropsService, sessionCipher, wamService);
        this.messageService = new MessageService(this, sessionCipher, groupCipher, deviceService, lidMigrationService, abPropsService, wamService, mediaTranscoderService);
        this.usyncBackoff = new UsyncBackoff();
        this.pendingSocketRequests = new ConcurrentHashMap<>();
        this.companionPairingService = new CompanionPairingService(this, webVerificationHandler);
        this.callService = new CallService(this, wamService);
        var ackSender = new AckSender(this);
        this.socketStream = new SocketStream(this, callService, webVerificationHandler, lidMigrationService, inactiveGroupLidMigrationService, messageService, abPropsService, deviceService, wamService, snapshotRecoveryService, webAppStateService, companionPairingService, ackSender, mediaConnectionService);
        this.disconnecting = new AtomicBoolean();
    }

    //<editor-fold desc="Data">
    /** {@inheritDoc} */
    @Override
    public WhatsAppStore store() {
        return store;
    }

    //</editor-fold>
    //<editor-fold desc="Connection">
    /** {@inheritDoc} */
    @Override
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
                        DefaultWhatsAppClient.this.onNode(node);
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
                // The webc socket-connect WAM event is web-only on WA Web;
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
     * sub-phases of the chat socket setup (the transport open and the
     * Noise XX handshake) together with the reason for this connect
     * attempt.
     *
     * <p>The durations are read from the active {@link WhatsAppSocketClient}
     * which measured them internally as the connection progressed. The
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
     * @param reason the disconnection reason driving this connect, or
     *               {@code null} for the initial connect
     */
    private void emitWebcSocketConnectEvent(WhatsAppClientDisconnectReason reason) {
        var socketConnectDuration = socketClient.socketConnectDuration();
        var authHandshakeDuration = socketClient.authHandshakeDuration();
        var connectReason = reason == WhatsAppClientDisconnectReason.RECONNECTING
                ? WebcSocketConnectReasonType.RECONNECT
                : WebcSocketConnectReasonType.PAGE_LOAD;
        var builder = new WebcSocketConnectEventBuilder()
                .webcSocketConnectReason(connectReason);
        if (socketConnectDuration != null) {
            builder.webcSocketConnectDuration(Instant.ofEpochMilli(socketConnectDuration.toMillis()));
        }
        if (authHandshakeDuration != null) {
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

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
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

        pendingSocketRequests.forEach((_, request) -> request.complete(null));
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
     */
    @WhatsAppWebExport(moduleName = "WAWebSocketModel", exports = "Socket",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void flushDirtyCollectionsWithTimeout() {
        var configuredTimeoutSeconds = SyncKeyUtils.getSyncdSentinelTimeoutSeconds(abPropsService);
        var clampedSeconds = Math.min(20, Math.max(0, configuredTimeoutSeconds));
        var timeoutMs = clampedSeconds * 1000L;

        var flushThread = Thread.ofVirtual()
                .name("CobaltSentinelFlush")
                .unstarted(() -> {
                    try {
                        webAppStateService.flushDirtyCollections();
                    } catch (Exception _) {
                        // Best-effort: don't let flush failures block disconnect
                    }
                });
        flushThread.start();

        if (timeoutMs == 0L) {
            return;
        }

        try {
            flushThread.join(timeoutMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }

        if (flushThread.isAlive()) {
            System.getLogger(DefaultWhatsAppClient.class.getName())
                    .log(System.Logger.Level.WARNING,
                            "Sentinel flush did not complete within {0}ms, proceeding with disconnect",
                            timeoutMs);
        }
    }

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
    public Node sendNode(NodeBuilder node) {
        return sendNode(node, null);
    }

    /** {@inheritDoc} */
    @Override
    public Node sendNode(NodeBuilder node, Function<Node, Boolean> filter) {
        if (!node.hasAttribute("id")) {
            // randomHex(5) yields 10 uppercase hex chars (5 random bytes * 2)
            node.attribute("id", DataUtils.randomHex(5));
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexNativeClient", exports = "fetchQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Node sendNode(MexOperation.Request request) {
        // from the typed request value rather than receiving them as separate scalars
        var queryId = request.id();
        var operationName = request.name();
        var isArgoPayload = request instanceof MexOperation.Request.Argo;
        var start = Instant.now();
        var startTimeMs = start.toEpochMilli();
        try {
            var response = sendNode(request.toNode());
            var end = Instant.now();
            commitMexEventV2(queryId, operationName, isArgoPayload, startTimeMs, end.toEpochMilli(), true, null, null);
            return response;
        } catch (RuntimeException exception) {
            var end = Instant.now();
            var errorsJson = mexErrorsJson(exception);
            var errorCodesJson = mexErrorCodesJson(exception);
            commitMexEventV2(queryId, operationName, isArgoPayload, startTimeMs, end.toEpochMilli(), false, errorsJson, errorCodesJson);
            throw exception;
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexNativeClient", exports = "fetchQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendNodeWithNoResponse(MexOperation.Request request) {
        // and discard the parsed result
        sendNode(request);
    }

    /** {@inheritDoc} */
    @Override
    public Node sendNode(SmaxOperation.Request request) {
        Objects.requireNonNull(request, "request cannot be null");
        return sendNode(request.toNode());
    }

    /** {@inheritDoc} */
    @Override
    public void sendNodeWithNoResponse(SmaxOperation.Request request) {
        Objects.requireNonNull(request, "request cannot be null");
        sendNode(request);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery.execute", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncResult sendNode(UsyncQuery query) throws InterruptedException {
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
     * @param exception the failure raised by {@link #sendNode(NodeBuilder)}
     * @return the JSON-encoded error array
     */
    private static String mexErrorsJson(RuntimeException exception) {
        var message = exception.getMessage();
        var arr = new JSONArray();
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
     * @param exception the failure raised by the underlying dispatch
     * @return the JSON-encoded integer array
     */
    private static String mexErrorCodesJson(RuntimeException exception) {
        var arr = new JSONArray();
        arr.add(417);
        return arr.toJSONString();
    }

    /** {@inheritDoc} */
    @Override
    public void disconnect() {
        disconnect(WhatsAppClientDisconnectReason.DISCONNECTED);
    }

    /** {@inheritDoc} */
    @Override
    public void reconnect() {
        disconnect(WhatsAppClientDisconnectReason.RECONNECTING);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUnpairDeviceJob",
            exports = "unpairDevice",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void logout() {
        var localJid = store.jid();
        if (localJid.isEmpty()) {
            disconnect(WhatsAppClientDisconnectReason.LOGGED_OUT);
        } else {
            var device = new NodeBuilder()
                    .description("remove-companion-device")
                    .attribute("jid", localJid.get())
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUnpairDeviceJob",
            exports = "unpairDevice",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void logoutCompanion(JidProvider companionProvider) {
        var companion = Objects.requireNonNull(companionProvider, "companion cannot be null").toJid();
        var device = new NodeBuilder()
                .description("remove-companion-device")
                .attribute("jid", companion)
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getMyDeviceList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void refreshLinkedDevices() {
        var selfJid = store.jid()
                .map(Jid::toUserJid)
                .orElseThrow(() -> new IllegalStateException("Client is not logged in"));
        var lists = deviceService.syncAndGetDeviceList(List.of(selfJid));
        var result = new LinkedHashSet<Jid>();
        for (var list : lists) {
            if (list == null || list.deleted()) {
                continue;
            }
            for (var device : list.devices()) {
                result.add(device.toDeviceJid(selfJid.user(), selfJid.server()));
            }
        }
        var snapshot = List.copyOf(result);
        store.setLinkedDevices(snapshot);
        for (var listener : store.listeners()) {
            listener.onLinkedDevices(this, snapshot);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isConnected() {
        return socketClient != null && socketClient.isConnected();
    }

    /** {@inheritDoc} */
    @Override
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
    /** {@inheritDoc} */
    @Override
    public void handleFailure(WhatsAppException exception) {
        if (exception instanceof WhatsAppWebAppStateSyncException syncdException) {
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
     * sufficient ({@code mdFatalErrorCode}, {@code collection}, and
     * {@code isFatal}) are populated from the exception subtype and
     * any carried {@link SyncPatchType}. The richer property set that WA
     * Web's canonical site populates (the ~30 {@code macFatal*},
     * {@code timeSince*}, {@code appContext}, {@code mailboxAgeDays},
     * {@code recoveryStatus} fields) is populated at WA Web's inline
     * detection sites; Cobalt's typed-exception model intentionally does
     * not thread that rich context through the exception hierarchy.
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
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdUploadFatalErrorMetricEmitter",
            exports = "emitUploadFatalErrorMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdUploadFatalErrorMetricEmitter",
            exports = "listenForUploadFatalErrorMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
                .mdFatalErrorCode(code)
                .isFatal(true);
        if (collection != null) {
            builder.collection(collection);
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
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static MdSyncdFatalErrorCode mapSyncdFatalErrorCode(WhatsAppWebAppStateSyncException exception) {
        return switch (exception) {
            // WAWebSyncdAntiTampering / MutationIntegrityVerifier: SyncdFatalError("unable to validate snapshot mac")
            case WhatsAppWebAppStateSyncException.SnapshotMacMismatch _ -> MdSyncdFatalErrorCode.MAC_MISMATCH_SNAPSHOT;
            // WAWebSyncdAntiTampering.validatePatchMac / MutationIntegrityVerifier: SyncdFatalError("unable to validate patch mac")
            case WhatsAppWebAppStateSyncException.PatchMacMismatch _ -> MdSyncdFatalErrorCode.MAC_MISMATCH_PATCH;
            // WAWebSyncdError / DecryptedMutation: SyncdFatalError("decryption failure: valueMAC mismatch")
            case WhatsAppWebAppStateSyncException.ValueMacMismatch _ -> MdSyncdFatalErrorCode.DECRYPTION_FAILED_VALUE_MAC_MISMATCH;
            // WAWebSyncdError / DecryptedMutation: SyncdFatalError("decryption failure: indexMAC mismatch")
            case WhatsAppWebAppStateSyncException.IndexMacMismatch _ -> MdSyncdFatalErrorCode.DECRYPTION_FAILED_INDEX_MAC_MISMATCH;
            // WAWebSyncdStoreMissingKeys: MISSING_KEY_ON_ALL_CLIENTS
            case WhatsAppWebAppStateSyncException.MissingKeyOnAllDevices _ -> MdSyncdFatalErrorCode.MISSING_KEY_ON_ALL_CLIENTS;
            case WhatsAppWebAppStateSyncException.TimeoutWhileWaitingForMissingKey _ -> MdSyncdFatalErrorCode.TIMEOUT_WHILE_WAITING_FOR_MISSING_KEY;
            // WASyncdKmpEncryptionManager: SyncdFatalError(e.message)
            case WhatsAppWebAppStateSyncException.DecryptionFailed _ -> MdSyncdFatalErrorCode.DECRYPTION_FAILED;
            // WAWebSyncdError: MAC computation / encryption failures bucket under ENCRYPTION_FAILED
            case WhatsAppWebAppStateSyncException.MacComputationFailed _ -> MdSyncdFatalErrorCode.ENCRYPTION_FAILED;
            case WhatsAppWebAppStateSyncException.MissingActionTimestamp _ -> MdSyncdFatalErrorCode.MISSING_ACTION_TIMESTAMP;
            case WhatsAppWebAppStateSyncException.DuplicateIndexInPatch _ -> MdSyncdFatalErrorCode.SAME_INDEX_FOR_MULTIPLE_MUTATIONS_IN_PATCH;
            case WhatsAppWebAppStateSyncException.DuplicatePatchVersion _ -> MdSyncdFatalErrorCode.DUPLICATE_PATCH_VERSION_IN_COLLECTION;
            case WhatsAppWebAppStateSyncException.MissingPatches _ -> MdSyncdFatalErrorCode.SERVER_DID_NOT_SEND_ALL_PATCHES;
            case WhatsAppWebAppStateSyncException.TerminalPatch terminal -> mapTerminalPatchCode(terminal);
            case WhatsAppWebAppStateSyncException.ExternalDecodeFailed _ -> MdSyncdFatalErrorCode.UNKNOWN;
            case WhatsAppWebAppStateSyncException.UnexpectedError _ -> MdSyncdFatalErrorCode.UNKNOWN;
            // Retryable subtypes never reach here because handleFailure
            // pre-filters by isFatal(); listed explicitly so the sealed switch
            // remains exhaustive.
            case WhatsAppWebAppStateSyncException.MissingKey _ -> null;
            case WhatsAppWebAppStateSyncException.Conflict _ -> null;
            case WhatsAppWebAppStateSyncException.RetryableServerError _ -> null;
            case WhatsAppWebAppStateSyncException.ExternalDownloadFailed _ -> null;
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
        var code = terminal.exitCode() == null
                ? null
                : terminal.exitCode().code().orElse(null);
        if (code instanceof DisconnectCode.MissingData) {
            return MdSyncdFatalErrorCode.TERMINAL_PATCH_MISSING_DATA;
        }
        if (code instanceof DisconnectCode.DeserializationError) {
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
            adaptation = WhatsAppAdaptation.DIRECT)
    private static com.github.auties00.cobalt.wam.type.Collection extractSyncdCollection(WhatsAppWebAppStateSyncException exception) {
        var patchType = switch (exception) {
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
    /** {@inheritDoc} */
    @Override
    public void pushWebAppState(SyncPatchType type, List<SyncPendingMutation> patches) {
        webAppStateService.pushPatches(type, patches);
    }

    /** {@inheritDoc} */
    @Override
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
    //<editor-fold desc="Acknowledgements, receipts, and pre-keys">
    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendAck",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendAck(Node node) {
        var id = node.getRequiredAttributeAsString("id");
        sendAck(id, node);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendAck",
            adaptation = WhatsAppAdaptation.ADAPTED)
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUploadPreKeysJob", exports = "uploadPreKeys",
            adaptation = WhatsAppAdaptation.ADAPTED)
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "RECEIPT_TYPE",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendPlayedReceiptJob", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendReceipt(String id, JidProvider fromProvider, String type) {
        var from = Objects.requireNonNull(fromProvider, "from cannot be null").toJid();
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
    //</editor-fold>

    //<editor-fold desc="Chats, groups, and metadata queries">
    /** {@inheritDoc} */
    @Override
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
            var communityGroupsQuery = new FetchAllSubgroupsMexRequest(groupId.toString(), "INTERACTIVE", null);
            var communityGroupsResponseNode = sendNode(communityGroupsQuery);
            var communityGroupsResponse = FetchAllSubgroupsMexResponse.of(communityGroupsResponseNode);
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryBusinessProfileJob",
            exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<BusinessProfile> queryBusinessProfile(JidProvider contact) {
        Objects.requireNonNull(contact, "contact cannot be null");
        var entry = new IqQueryBusinessProfileRequestEntry(contact.toJid(), null);
        var request = new IqQueryBusinessProfileRequest(List.of(entry), 3);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqQueryBusinessProfileResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqQueryBusinessProfileResponse.Success success -> success.profiles()
                    .stream()
                    .findFirst()
                    .map(DefaultWhatsAppClient::toBusinessProfile);
            case IqQueryBusinessProfileResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query business profile rejected: " + clientError.errorCode());
            case IqQueryBusinessProfileResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query business profile server error: " + serverError.errorCode());
            case null -> Optional.empty();
        };
    }

    /**
     * Projects a typed
     * {@link IqQueryBusinessProfileResponse.Success.Profile} entry
     * onto Cobalt's caller-friendly {@link BusinessProfile} model. The
     * typed payload carries every field the relay echoed back; the
     * model surfaces only the fields callers consume directly so the
     * projection drops the catalog-status, profile-options, linked
     * accounts, direct-connection, service-area, offering, cover
     * photo, custom URL, prompt, command, welcome-message protocol,
     * member-since text, price-tier, authorised-agent, and parent
     * company fields.
     *
     * @param profile the typed profile entry; never {@code null}
     * @return the converted {@link BusinessProfile}; never
     *         {@code null}
     */
    private static BusinessProfile toBusinessProfile(IqQueryBusinessProfileResponse.Success.Profile profile) {
        var websites = profile.websites()
                .stream()
                .map(URI::create)
                .toList();
        var categories = profile.categories()
                .stream()
                .map(category -> new BusinessCategoryBuilder()
                        .id(category.id())
                        .name(URLDecoder.decode(category.localizedDisplayName(), StandardCharsets.UTF_8))
                        .build())
                .toList();
        var hours = profile.businessHours()
                .map(DefaultWhatsAppClient::toBusinessHours)
                .orElse(null);
        var cartEnabled = profile.profileOptions()
                .flatMap(IqQueryBusinessProfileResponse.Success.ProfileOptions::cartEnabled)
                .orElse(profile.profileOptions().isEmpty());
        var automatedType = profile.automatedType()
                .map(BusinessAutomatedType::of)
                .orElse(null);
        return new BusinessProfileBuilder()
                .jid(profile.businessJid())
                .description(profile.description().orElse(null))
                .address(profile.address().orElse(null))
                .email(profile.email().orElse(null))
                .hours(hours)
                .cartEnabled(cartEnabled)
                .websites(websites)
                .categories(categories)
                .automatedType(automatedType)
                .build();
    }

    /**
     * Projects the typed
     * {@link IqQueryBusinessProfileResponse.Success.BusinessHours}
     * payload onto Cobalt's caller-friendly {@link BusinessHours}
     * model, mapping wire-format day/mode tokens onto
     * {@link BusinessHoursDay} / {@link BusinessHoursMode} sealed
     * variants and the second-of-day open/close columns onto
     * {@link LocalTime}.
     *
     * @param businessHours the typed payload; never {@code null}
     * @return the converted {@link BusinessHours}; never {@code null}
     */
    private static BusinessHours toBusinessHours(IqQueryBusinessProfileResponse.Success.BusinessHours businessHours) {
        var entries = businessHours.config()
                .stream()
                .map(config -> new BusinessHoursEntryBuilder()
                        .day(BusinessHoursDay.of(config.dayOfWeek()))
                        .mode(BusinessHoursMode.of(config.mode()))
                        .openTime(LocalTime.ofSecondOfDay(config.openTime()))
                        .closeTime(LocalTime.ofSecondOfDay(config.closeTime()))
                        .build())
                .toList();
        return new BusinessHoursBuilder()
                .timeZone(businessHours.timezone().orElse(null))
                .entries(entries)
                .build();
    }

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob", exports = "editBusinessProfile",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editBusinessProfile(BusinessProfile profile) {
        Objects.requireNonNull(profile, "profile cannot be null");
        // to clear the slot; a non-empty list emits up to two <website>url</website> children.
        var websites = profile.websites().stream()
                .limit(2)
                .map(uri -> new IqEditBusinessProfileWebsite(uri.toString()))
                .toList();
        var categories = profile.categories().stream()
                .map(BusinessCategory::id)
                .toList();
        var businessHours = profile.hours()
                .map(DefaultWhatsAppClient::toEditBusinessHours)
                .orElse(null);
        var request = IqEditBusinessProfileRequest.builder()
                .address(profile.address().orElse(null))
                .description(profile.description().orElse(null))
                .email(profile.email().orElse(null))
                .websites(websites)
                .categories(categories.isEmpty() ? null : categories)
                .businessHours(businessHours)
                .build();
        sendNode(request);
    }

    /**
     * Projects Cobalt's caller-friendly {@link BusinessHours} model
     * onto the typed payload carried by an
     * {@link IqEditBusinessProfileRequest}, mapping the
     * {@link BusinessHoursDay} / {@link BusinessHoursMode} variants
     * back onto the wire-format day/mode tokens and the
     * {@link LocalTime} columns onto seconds since midnight. Open and
     * close times are only emitted when the entry's mode is
     * {@link BusinessHoursMode#SPECIFIC_HOURS}, matching WA Web's
     * editor that drops the attributes for closed/24h/appointment
     * days.
     *
     * @param hours the model schedule; never {@code null}
     * @return the typed payload; never {@code null}
     */
    private static IqEditBusinessProfileBusinessHours toEditBusinessHours(BusinessHours hours) {
        var configs = new ArrayList<IqEditBusinessProfileBusinessHoursConfig>();
        for (var entry : hours.entries()) {
            Integer openTime = null;
            Integer closeTime = null;
            if (entry.mode() == BusinessHoursMode.SPECIFIC_HOURS) {
                openTime = entry.openTime().toSecondOfDay();
                closeTime = entry.closeTime().toSecondOfDay();
            }
            configs.add(new IqEditBusinessProfileBusinessHoursConfig(
                    entry.day().value(), entry.mode().value(), openTime, closeTime));
        }
        return new IqEditBusinessProfileBusinessHours(hours.timeZone(), null, configs);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob", exports = "updateCartEnabled",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void updateBusinessCartEnabled(boolean enabled) {
        sendNode(new IqUpdateCartEnabledRequest(enabled));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob", exports = "deleteCoverPhoto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteBusinessCoverPhoto() {
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryGetPublicKeyJob", exports = "QueryGetPublicKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<String> queryBusinessPublicKey(JidProvider businessJidProvider) {
        var businessJid = Objects.requireNonNull(businessJidProvider, "businessJid cannot be null").toJid();
        var request = new IqQueryGetPublicKeyRequest(businessJid);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqQueryGetPublicKeyResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqQueryGetPublicKeyResponse.Success success -> success.certificate();
            case IqQueryGetPublicKeyResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query business public key rejected: " + clientError.errorCode());
            case IqQueryGetPublicKeyResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query business public key server error: " + serverError.errorCode());
            case null -> Optional.empty();
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryGetSignedUserInfoJob", exports = "QueryGetSignedUserInfo",
            adaptation = WhatsAppAdaptation.DIRECT)
    public BusinessSignedUserInfo queryBusinessSignedUserInfo(JidProvider businessJidProvider) {
        var businessJid = Objects.requireNonNull(businessJidProvider, "businessJid cannot be null").toJid();
        var request = new IqQueryGetSignedUserInfoRequest(businessJid);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqQueryGetSignedUserInfoResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqQueryGetSignedUserInfoResponse.Success success -> new BusinessSignedUserInfoBuilder()
                    .phoneNumber(success.phoneNumber().orElse(null))
                    .phoneNumberSignatureExpiration(success.phoneNumberSignatureExpiration().orElse(null))
                    .phoneNumberSignature(success.phoneNumberSignature().orElse(null))
                    .businessDomain(success.businessDomain().orElse(null))
                    .build();
            case IqQueryGetSignedUserInfoResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query business signed user-info rejected: " + clientError.errorCode());
            case IqQueryGetSignedUserInfoResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query business signed user-info server error: " + serverError.errorCode());
            case null -> new BusinessSignedUserInfoBuilder().build();
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMerchantComplianceJob", exports = "getMerchantCompliance",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<BusinessMerchantCompliance> queryMerchantCompliance(List<? extends JidProvider> jids) {
        var bizJids = Objects.requireNonNull(jids, "bizJids cannot be null").stream().map(JidProvider::toJid).toList();
        if (bizJids.isEmpty()) {
            throw new IllegalArgumentException("bizJids must not be empty");
        }
        bizJids.forEach(jid -> Objects.requireNonNull(jid, "bizJids element cannot be null"));
        var request = new IqGetMerchantComplianceRequest(bizJids);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqGetMerchantComplianceResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqGetMerchantComplianceResponse.Success success -> success.entries()
                    .stream()
                    .map(DefaultWhatsAppClient::toBusinessMerchantCompliance)
                    .toList();
            case IqGetMerchantComplianceResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query merchant compliance rejected: " + clientError.errorCode());
            case IqGetMerchantComplianceResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query merchant compliance server error: " + serverError.errorCode());
            case null -> List.of();
        };
    }

    /**
     * Projects a typed
     * {@link IqGetMerchantComplianceResponse.Success.MerchantInfo}
     * entry onto Cobalt's caller-friendly
     * {@link BusinessMerchantCompliance} model. The typed projection
     * defaults missing wire-level fields to the empty string per
     * {@code WAWebMerchantComplianceJob.merchantComplianceResponse}'s
     * parser; the model preserves the same semantics by passing those
     * empty strings through verbatim.
     *
     * @param entry the typed entry; never {@code null}
     * @return the converted model entry; never {@code null}
     */
    private static BusinessMerchantCompliance toBusinessMerchantCompliance(
            IqGetMerchantComplianceResponse.Success.MerchantInfo entry) {
        var ccd = entry.customerCareDetails();
        var customerCare = new BusinessContactDetailsBuilder()
                .email(ccd.email())
                .landlineNumber(ccd.landlineNumber())
                .mobileNumber(ccd.mobileNumber())
                .build();
        var god = entry.grievanceOfficerDetails();
        var officer = new BusinessGrievanceOfficerDetailsBuilder()
                .name(god.name())
                .email(god.email())
                .landlineNumber(god.landlineNumber())
                .mobileNumber(god.mobileNumber())
                .build();
        return new BusinessMerchantComplianceBuilder()
                .entityName(entry.entityName())
                .entityType(entry.entityType())
                .entityTypeCustom(entry.entityTypeCustom().orElse(null))
                .registered(entry.registered())
                .customerCareDetails(customerCare)
                .grievanceOfficerDetails(officer)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMerchantComplianceJob", exports = "setMerchantCompliance",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public BusinessMerchantCompliance editMerchantCompliance(MerchantComplianceEdit edit) {
        Objects.requireNonNull(edit, "edit cannot be null");
        var customerCare = edit.customerCareDetails().map(d -> new IqSetMerchantComplianceContactDetails(
                d.email().orElse(null),
                d.landlineNumber().orElse(null),
                d.mobileNumber().orElse(null))).orElse(null);
        var grievanceOfficer = edit.grievanceOfficerDetails().map(d -> new IqSetMerchantComplianceGrievanceOfficerDetails(
                d.name().orElse(null),
                new IqSetMerchantComplianceContactDetails(
                        d.email().orElse(null),
                        d.landlineNumber().orElse(null),
                        d.mobileNumber().orElse(null)))).orElse(null);
        var request = IqSetMerchantComplianceRequest.builder()
                .registered(edit.registered())
                .entityName(edit.entityName().orElse(null))
                .entityType(edit.entityType().orElse(null))
                .entityTypeCustom(edit.entityTypeCustom().orElse(null))
                .customerCareDetails(customerCare)
                .grievanceOfficerDetails(grievanceOfficer)
                .build();
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqSetMerchantComplianceResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqSetMerchantComplianceResponse.Success success -> success.entries()
                    .stream()
                    .findFirst()
                    .map(DefaultWhatsAppClient::toBusinessMerchantCompliance)
                    .orElseThrow(() -> new NoSuchElementException(
                            "Missing <merchant_info> in editMerchantCompliance response"));
            case IqSetMerchantComplianceResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Change merchant compliance rejected: " + clientError.errorCode());
            case IqSetMerchantComplianceResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Change merchant compliance server error: " + serverError.errorCode());
            case null -> throw new NoSuchElementException(
                    "Missing <merchant_info> in editMerchantCompliance response");
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryBusinessCategoriesJob", exports = "queryBusinessCategories",
            adaptation = WhatsAppAdaptation.DIRECT)
    public BusinessCategoryTypeahead queryBusinessCategoryTypeahead(String query) {
        Objects.requireNonNull(query, "query cannot be null");
        var request = new IqQueryBusinessCategoriesRequest(query);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqQueryBusinessCategoriesResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqQueryBusinessCategoriesResponse.Success success -> {
                var hits = success.categories()
                        .stream()
                        .map(entry -> new BusinessCategoryHitBuilder()
                                .id(entry.id())
                                .localizedName(entry.localizedDisplayName())
                                .notABiz(entry.notABiz())
                                .build())
                        .toList();
                yield new BusinessCategoryTypeaheadBuilder()
                        .categories(hits)
                        .notABizId(success.notABizId().isEmpty() ? null : success.notABizId())
                        .build();
            }
            case IqQueryBusinessCategoriesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query business categories typeahead rejected: " + clientError.errorCode());
            case IqQueryBusinessCategoriesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query business categories typeahead server error: " + serverError.errorCode());
            case null -> throw new NoSuchElementException(
                    "Missing <response> in business-categories typeahead response");
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBizQueryOrderJob", exports = "queryOrder",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebBizOrderBridge", exports = "queryOrder",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<BusinessOrder> queryOrder(String messageId, String tokenBase64) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(tokenBase64, "tokenBase64 cannot be null");
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE().toString()
                .orElseThrow(() -> new IllegalStateException("queryOrder requires a logged-in session"));
        var request = new QueryOrderMexRequest(
                selfJid.toString(),
                messageId,
                tokenBase64,
                512,                512        );
        var response = sendNode(request);
        return QueryOrderMexResponse.of(response)
                .map(QueryOrderMexResponse::order);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendQuickReplyAddOrEditMutation", exports = "sendQuickReplyAddOrEditMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public String createQuickReply(QuickReplyCreate create) {
        Objects.requireNonNull(create, "create cannot be null");
        var shortcut = create.shortcut();
        var message = create.message();
        var quickReplyId = DataUtils.randomHex(8); // ADAPTED: WA Web mints a client-side uuid inside the compose flow; randomHex(8) yields 16 uppercase hex chars
        var resolvedKeywords = List.copyOf(create.keywords());
        var timestamp = Instant.now();
        wamService.commit(new QuickReplyEventBuilder()
                .quickReplyAction(com.github.auties00.cobalt.wam.type.QuickReplyAction.ACTION_SETTINGS_ADDED)
                .quickReplyEntryPoint(QuickReplyEntryPoint.QUICK_REPLY_ENTRY_POINT_SETTINGS_MENU)
                .build());
        var mutation = quickReplyMutationFactory.getQuickReplyAddOrEditMutation(
                quickReplyId, shortcut, message, 0, resolvedKeywords, timestamp);
        webAppStateService.pushPatches(QuickReplyAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync([], [mutation], ...)
        // ADAPTED: WAWebSendQuickReplyAddOrEditMutation applies the entry locally after the sync round-trip;
        // Cobalt updates the store eagerly for consistent read-after-write semantics.
        var entry = new QuickReplyBuilder()
                .id(quickReplyId)
                .shortcut(shortcut)
                .message(message)
                .keywords(resolvedKeywords)
                .count(0)
                .build();
        store.addQuickReply(entry);
        return quickReplyId;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendQuickReplyAddOrEditMutation", exports = "sendQuickReplyAddOrEditMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<QuickReply> editQuickReply(QuickReplyEdit edit) {
        Objects.requireNonNull(edit, "edit cannot be null");
        var quickReplyId = edit.id();
        var existing = store.findQuickReply(quickReplyId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        var shortcut = edit.shortcut();
        var message = edit.message();
        var resolvedKeywords = List.copyOf(edit.keywords());
        var timestamp = Instant.now();
        var currentCount = existing.get().count();
        wamService.commit(new QuickReplyEventBuilder()
                .quickReplyAction(com.github.auties00.cobalt.wam.type.QuickReplyAction.ACTION_SETTINGS_EDITED)
                .quickReplyEntryPoint(QuickReplyEntryPoint.QUICK_REPLY_ENTRY_POINT_SETTINGS_MENU)
                .build());
        var mutation = quickReplyMutationFactory.getQuickReplyAddOrEditMutation(
                quickReplyId, shortcut, message, currentCount, resolvedKeywords, timestamp);
        webAppStateService.pushPatches(QuickReplyAction.COLLECTION_NAME, List.of(mutation));
        var entry = new QuickReplyBuilder()
                .id(quickReplyId)
                .shortcut(shortcut)
                .message(message)
                .keywords(resolvedKeywords)
                .count(currentCount)
                .build();
        store.addQuickReply(entry); // ADAPTED: createOrReplace eagerly mirrors WA Web's inbound apply branch
        return existing;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteQuickReplyAction", exports = "deleteQuickReplyAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<QuickReply> deleteQuickReply(String quickReplyId) {
        Objects.requireNonNull(quickReplyId, "quickReplyId cannot be null");
        var existing = store.findQuickReply(quickReplyId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        wamService.commit(new QuickReplyEventBuilder()
                .quickReplyAction(com.github.auties00.cobalt.wam.type.QuickReplyAction.ACTION_SETTINGS_DELETED)
                .quickReplyEntryPoint(QuickReplyEntryPoint.QUICK_REPLY_ENTRY_POINT_SETTINGS_MENU)
                .build());
        var mutation = quickReplyMutationFactory.getQuickReplyDeleteMutation(quickReplyId, Instant.now());
        webAppStateService.pushPatches(QuickReplyAction.COLLECTION_NAME, List.of(mutation));
        store.removeQuickReply(quickReplyId); // ADAPTED: WAWebQuickRepliesSync.applyMutations -> getQuickReplyTable().remove(l) applied eagerly
        return existing;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<BotProfile> queryBotProfile(JidProvider botJid) {
        return queryBotProfile(botJid, null);
    }

    /** {@inheritDoc} */
    @Override
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
                .map(DefaultWhatsAppClient::parseBotPrompt)
                .toList();

        var commandsDescription = profile.getChild("commands")
                .flatMap(commandsNode -> commandsNode.getChild("description"))
                .flatMap(Node::toContentString)
                .orElse(null);
        var commands = profile.streamChild("commands")
                .flatMap(commandsNode -> commandsNode.streamChildren("command"))
                .map(DefaultWhatsAppClient::parseBotCommand)
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipFunctions", exports = "offerCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebOutgoingCall", exports = "sendOfferStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public ActiveCall startCall(JidProvider targetProvider, CallOptions options) {
        var target = Objects.requireNonNull(targetProvider, "target cannot be null").toJid();
        return callService.placeCall(target, options);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipFunctions", exports = "acceptCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public ActiveCall acceptCall(IncomingCall offer, CallOptions options) {
        Objects.requireNonNull(offer, "offer cannot be null");
        Objects.requireNonNull(options, "options cannot be null");
        offer.markResponded();
        return callService.accept(offer, options);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipFunctions", exports = "rejectCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void rejectCall(IncomingCall offer, CallEndReason reason) {
        Objects.requireNonNull(offer, "offer cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        offer.markResponded();
        callService.reject(offer, reason);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipFunctions", exports = "terminateCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void terminateCall(String callId, CallEndReason reason) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        var target = store.findCallById(callId)
                .orElseThrow(() -> new NoSuchElementException("No call with id " + callId))
                .chatJid();
        var selfJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        sendNodeWithNoResponse(CallStanza.terminate(target, selfJid, callId, reason));
    }

    /** {@inheritDoc} */
    @Override
    public void terminateCall(ActiveCall call, CallEndReason reason) {
        Objects.requireNonNull(call, "call cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        var selfJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        sendNodeWithNoResponse(CallStanza.terminate(call.peer(), selfJid, call.callId(), reason));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void preacceptCall(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        var caller = store.findCallById(callId)
                .orElseThrow(() -> new NoSuchElementException("No call with id " + callId))
                .peer();
        sendNodeWithNoResponse(CallStanza.preaccept(caller, callId));
    }

    /** {@inheritDoc} */
    @Override
    public void preacceptCall(IncomingCall call) {
        Objects.requireNonNull(call, "call cannot be null");
        sendNodeWithNoResponse(CallStanza.preaccept(call.peer(), call.callId()));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void muteCall(String callId, boolean muted) {
        Objects.requireNonNull(callId, "callId cannot be null");
        var target = store.findCallById(callId)
                .orElseThrow(() -> new NoSuchElementException("No call with id " + callId))
                .chatJid();
        var selfJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        sendNodeWithNoResponse(CallStanza.mute(target, selfJid, callId, muted));
    }

    /** {@inheritDoc} */
    @Override
    public void muteCall(ActiveCall call, boolean muted) {
        Objects.requireNonNull(call, "call cannot be null");
        var selfJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        sendNodeWithNoResponse(CallStanza.mute(call.peer(), selfJid, call.callId(), muted));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editCallVideoState(ActiveCall call, boolean videoEnabled) {
        Objects.requireNonNull(call, "call cannot be null");
        sendNodeWithNoResponse(CallStanza.videoState(call.peer(), call.creator(), call.callId(), videoEnabled));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebOutgoingGroupCallUtils", exports = "sendGroupOfferStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public ActiveCall startGroupCall(JidProvider groupProvider, Collection<? extends JidProvider> participantsProvider, boolean video) {
        var participants = Objects.requireNonNull(participantsProvider, "participants cannot be null").stream().map(JidProvider::toJid).toList();
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community JID");
        }
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("participants cannot be empty");
        }
        return callService.placeCall(group, video ? CallOptions.video() : CallOptions.audio());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void addCallParticipants(String callId, Collection<? extends JidProvider> participantsProvider) {
        Objects.requireNonNull(callId, "callId cannot be null");
        var participants = Objects.requireNonNull(participantsProvider, "participants cannot be null").stream().map(JidProvider::toJid).toList();
        var group = store.findCallById(callId)
                .orElseThrow(() -> new NoSuchElementException("No call with id " + callId))
                .chatJid();
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community JID");
        }
        var selfJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        sendNodeWithNoResponse(CallStanza.groupUpdate(group, selfJid, callId, true, participants));
    }

    /** {@inheritDoc} */
    @Override
    public void addCallParticipants(ActiveCall call, Collection<? extends JidProvider> participantsProvider) {
        Objects.requireNonNull(call, "call cannot be null");
        var participants = Objects.requireNonNull(participantsProvider, "participants cannot be null").stream().map(JidProvider::toJid).toList();
        if (!call.peer().hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community JID");
        }
        var selfJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        sendNodeWithNoResponse(CallStanza.groupUpdate(call.peer(), selfJid, call.callId(), true, participants));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void removeCallParticipants(String callId, Collection<? extends JidProvider> participantsProvider) {
        Objects.requireNonNull(callId, "callId cannot be null");
        var participants = Objects.requireNonNull(participantsProvider, "participants cannot be null").stream().map(JidProvider::toJid).toList();
        var group = store.findCallById(callId)
                .orElseThrow(() -> new NoSuchElementException("No call with id " + callId))
                .chatJid();
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community JID");
        }
        var selfJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        sendNodeWithNoResponse(CallStanza.groupUpdate(group, selfJid, callId, false, participants));
    }

    /** {@inheritDoc} */
    @Override
    public void removeCallParticipants(ActiveCall call, Collection<? extends JidProvider> participantsProvider) {
        Objects.requireNonNull(call, "call cannot be null");
        var participants = Objects.requireNonNull(participantsProvider, "participants cannot be null").stream().map(JidProvider::toJid).toList();
        if (!call.peer().hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community JID");
        }
        var selfJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        sendNodeWithNoResponse(CallStanza.groupUpdate(call.peer(), selfJid, call.callId(), false, participants));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetPrivacyTokensJob", exports = "issuePrivacyToken",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSendTcTokenChatAction", exports = "sendTcToken",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void issueTrustedContactToken(JidProvider peerProvider) {
        var peer = Objects.requireNonNull(peerProvider, "peer cannot be null").toJid();
        var request = new IqSetPrivacyTokensRequest(
                peer.toUserJid(),
                Instant.now().getEpochSecond(),
                List.of(IqSetPrivacyTokensTokenType.TRUSTED_CONTACT));
        sendNode(request);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendScheduledCall(JidProvider chatProvider, String title, Instant scheduledAt, boolean video) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(scheduledAt, "scheduledAt cannot be null");
        var creation = new ScheduledCallCreationMessageBuilder()
                .scheduledTimestampMs(scheduledAt)
                .callType(video
                        ? ScheduledCallCreationMessage.CallType.VIDEO
                        : ScheduledCallCreationMessage.CallType.VOICE)
                .title(title)
                .build();
        sendMessage(chat, MessageContainer.of(creation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void cancelScheduledCall(MessageKey creationKey) {
        Objects.requireNonNull(creationKey, "creationKey cannot be null");
        var chat = creationKey.parentJid()
                .orElseThrow(() -> new NoSuchElementException("creationKey has no parent JID"));
        var edit = new ScheduledCallEditMessageBuilder()
                .key(creationKey)
                .editType(ScheduledCallEditMessage.EditType.CANCEL)
                .build();
        sendMessage(chat, MessageContainer.of(edit));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxVoipLinkQueryRPC", exports = "sendLinkQueryRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<ActiveCall> joinCallLink(String token, CallLinkMedia media) {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(media, "media cannot be null");
        var resolved = queryCallLink(token, media, "preview").orElse(null);
        if (resolved == null) {
            return Optional.empty();
        }
        var creator = resolved.creator().orElse(null);
        if (creator == null) {
            return Optional.empty();
        }
        var options = switch (media) {
            case AUDIO -> CallOptions.audio();
            case VIDEO -> CallOptions.video();
        };
        return Optional.of(startCall(creator, options));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterMetadataJob", exports = "getAllNewslettersMetadata",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void refreshNewsletters() {
        var request = new FetchAllNewslettersMetadataMexRequest(Boolean.TRUE);
        var response = sendNode(request);
        FetchAllNewslettersMetadataMexResponse.of(response).ifPresent(r -> {
            for (var item : r.items()) {
                item.id().ifPresent(id -> {
                    var jid = Jid.of(id); // WAJids.toNewsletterJid
                    if (store.findNewsletterByJid(jid).isEmpty()) {
                        store.addNewNewsletter(jid);
                    }
                });
            }
        });
        // Flip the syncedNewsletters gate the first time the metadata fetch
        // returns and surface the full snapshot to onNewsletters listeners,
        // matching the chats/status fan-out from WebHistorySyncService.
        if (!store.syncedNewsletters()) {
            store.setSyncedNewsletters(true);
            var snapshot = store.newsletters();
            for (var listener : store.listeners()) {
                Thread.startVirtualThread(() -> listener.onNewsletters(this, snapshot));
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupQueryJob", exports = "queryAllGroups",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void refreshGroups() {
        var participantsMarker = new NodeBuilder()
                .description("participants") // WASmaxOutGroupsGetParticipatingGroupsRequest.makeGetParticipatingGroupsRequestParticipatingParticipants
                .build();
        var participatingNode = new NodeBuilder()
                .description("participating") // WASmaxOutGroupsGetParticipatingGroupsRequest: smax("participating", null, participants?, description?)
                .content(participantsMarker)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseGetServerMixin: xmlns: "w:g2"
                .attribute("to", Jid.of(JidServer.groupOrCommunity())) // WASmaxOutGroupsBaseGetServerMixin: to: G_US
                .attribute("type", "get") // WAWebSmaxBaseIQGetRequestMixin: type: "get"
                .content(participatingNode);
        var response = sendNode(iqNode); // WASmaxGroupsGetParticipatingGroupsRPC.sendGetParticipatingGroupsRPC
        var groups = new LinkedHashSet<Chat>();
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
                    groups.add(chat);
                });
        var snapshot = List.copyOf(groups);
        for (var listener : store.listeners()) {
            listener.onGroups(this, snapshot);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<String> queryGroupInviteCode(JidProvider groupProvider) {
        return queryGroupInviteCode(groupProvider, null);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob", exports = "resetGroupInviteCode",
            adaptation = WhatsAppAdaptation.DIRECT)
    public String revokeGroupInviteCode(JidProvider groupProvider) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var request = new IqResetGroupInviteCodeRequest(group);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqResetGroupInviteCodeResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqResetGroupInviteCodeResponse.Success success -> success.code();
            case IqResetGroupInviteCodeResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Reset group invite code rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case IqResetGroupInviteCodeResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Reset group invite code server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
            case null -> throw new NoSuchElementException("Missing invite code in response: %s".formatted(response));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob", exports = "joinGroupViaInvite",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Jid joinGroupViaInvite(String inviteCode) {
        Objects.requireNonNull(inviteCode, "inviteCode cannot be null");
        // WAWebGroupInviteJob.joinGroupViaInvite times the IQ with self.performance.now() and in
        // the finally block invokes WAWebGroupJoinRequestMetricUtils.logMembershipRequestCreate
        // ONLY when the caller anticipated a membership_approval_request response (the second
        // parameter `t`). Cobalt infers the same condition from the response shape: a
        // <membership_approval_request> child indicates the join has been gated behind admin
        // approval, so the create-request metric applies.
        var request = new IqJoinGroupByInviteCodeRequest(inviteCode, false);
        var requestBuilder = request.toNode();
        var start = Instant.now();
        var successful = true;
        Jid resolvedGroup = null;
        var approvalGated = false;
        try {
            var response = sendNode(requestBuilder);
            return switch (IqJoinGroupByInviteCodeResponse.of(response, requestBuilder.build()).orElse(null)) {
                case IqJoinGroupByInviteCodeResponse.Success success -> {
                    resolvedGroup = success.groupJid();
                    approvalGated = success.isMembershipApprovalPending();
                    yield resolvedGroup;
                }
                case IqJoinGroupByInviteCodeResponse.UnexpectedJoinShape unexpected -> {
                    resolvedGroup = unexpected.groupJid();
                    approvalGated = unexpected.actualMembershipApprovalPending();
                    yield resolvedGroup;
                }
                case IqJoinGroupByInviteCodeResponse.ClientError clientError ->
                        throw new WhatsAppServerRuntimeException("Join group via invite rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
                case IqJoinGroupByInviteCodeResponse.ServerError serverError ->
                        throw new WhatsAppServerRuntimeException("Join group via invite server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
                case null ->
                        throw new NoSuchElementException("Invalid join-group response: %s".formatted(response));
            };
        } catch (RuntimeException e) {
            successful = false;
            throw e;
        } finally {
            if (approvalGated || !successful) {
                // new WaFsGroupJoinRequestActionWamEvent({groupJid, groupJoinRequestAction:
                // MEMBERSHIP_REQUEST_CREATE, responseTime, isSuccessful}).commit().
                wamService.commit(new WaFsGroupJoinRequestActionEventBuilder()
                        .groupJid(resolvedGroup != null ? sanitizeGroupJidForWam(resolvedGroup) : "")
                        .groupJoinRequestAction(GroupJoinRequestActionType.MEMBERSHIP_REQUEST_CREATE)
                        .isSuccessful(successful)
                        .serverResponseTime(Instant.ofEpochMilli(Instant.now().toEpochMilli() - start.toEpochMilli()))
                        .build());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi", exports = "queryGroupInviteLinkProfilePic",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public GroupInvitePicture queryGroupInvitePicture(JidProvider groupProvider, String inviteCode) {
        return queryGroupInvitePictureImpl(groupProvider, inviteCode, "image", null);
    }

    /** {@inheritDoc} */
    @Override
    public GroupInvitePicture queryGroupInvitePicture(JidProvider groupProvider, String inviteCode, String query) {
        return queryGroupInvitePictureImpl(groupProvider, inviteCode, "image", query);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi", exports = "queryGroupInviteLinkProfilePic",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public GroupInvitePicture queryGroupInvitePicturePreview(JidProvider groupProvider, String inviteCode) {
        return queryGroupInvitePictureImpl(groupProvider, inviteCode, "preview", null);
    }

    /** {@inheritDoc} */
    @Override
    public GroupInvitePicture queryGroupInvitePicturePreview(JidProvider groupProvider, String inviteCode, String query) {
        return queryGroupInvitePictureImpl(groupProvider, inviteCode, "preview", query);
    }

    /**
     * Shared implementation of {@link #queryGroupInvitePicture} and
     * {@link #queryGroupInvitePicturePreview}.
     *
     * @param groupProvider the group identifier
     * @param inviteCode    the invite code
     * @param type          {@code "image"} or {@code "preview"}
     * @param query         the lookup hint, or {@code null}
     * @return the parsed picture entry
     */
    private GroupInvitePicture queryGroupInvitePictureImpl(JidProvider groupProvider, String inviteCode, String type, String query) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        Objects.requireNonNull(inviteCode, "inviteCode cannot be null");
        var request = new IqQueryGroupInviteProfilePicRequest(IqQueryGroupInviteProfilePicMode.INVITE_LINK,
                group, inviteCode, null, type, query, null, null);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqQueryGroupInviteProfilePicResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqQueryGroupInviteProfilePicResponse.Success success ->
                    new GroupInvitePicture(success.pictureId(), success.pictureType(),
                            URI.create(success.url()), success.directPath());
            case IqQueryGroupInviteProfilePicResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Query group invite picture rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case IqQueryGroupInviteProfilePicResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Query group invite picture server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
            case null -> throw new NoSuchElementException("Missing <picture> in group invite picture response");
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteV4Job", exports = "queryGroupInviteV4",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public GroupMetadata queryGroupInfoByInvite(GroupInvite invite) {
        Objects.requireNonNull(invite, "invite cannot be null");
        var invitee = invite.invitee();
        var sender = invite.sender();
        var inviteTimestamp = invite.expiration();
        var inviteCode = invite.inviteCode();
        var addRequestNode = new NodeBuilder()
                .description("add_request")
                .attribute("code", inviteCode) // addRequestCode
                .attribute("expiration", inviteTimestamp.getEpochSecond()) // addRequestExpiration
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteV4Job", exports = "joinGroupViaInviteV4",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendGroupInvite(JidProvider groupProvider, JidProvider targetProvider, Instant inviteTimestamp) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var target = Objects.requireNonNull(targetProvider, "target cannot be null").toJid();
        Objects.requireNonNull(inviteTimestamp, "inviteTimestamp cannot be null");
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var acceptNode = new NodeBuilder()
                .description("accept")
                .attribute("code", "")
                .attribute("expiration", inviteTimestamp.getEpochSecond()) // acceptExpiration
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupGetMembershipApprovalRequestsJob",
            exports = "queryAndUpdateGroupMembershipApprovalRequests",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<Jid> queryGroupJoinRequests(JidProvider groupProvider) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
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
        wamService.commit(new WaFsGroupJoinRequestActionEventBuilder()
                .groupJid(sanitizeGroupJidForWam(group)) // WAWebGroupJoinRequestMetricUtils.getSanitizedJid
                .groupJoinRequestAction(GroupJoinRequestActionType.VIEW_PENDING_PARTICIPANTS)
                .isSuccessful(true)
                .serverResponseTime(Instant.ofEpochMilli(0L))
                .build());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsMembershipRequestsActionRequest",
            exports = "makeMembershipRequestsActionRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMembershipApprovalRequestAction",
            exports = "approveMembershipApprovalRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void acceptGroupJoinRequest(JidProvider groupProvider, JidProvider applicantProvider) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var applicant = Objects.requireNonNull(applicantProvider, "applicant cannot be null").toJid();
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsMembershipRequestsActionRequest",
            exports = "makeMembershipRequestsActionRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMembershipApprovalRequestAction",
            exports = "rejectMembershipApprovalRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void rejectGroupJoinRequest(JidProvider groupProvider, JidProvider applicantProvider) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var applicant = Objects.requireNonNull(applicantProvider, "applicant cannot be null").toJid();
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
     * and {@link #rejectGroupJoinRequest(Jid, Jid)} that emits the
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
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupJoinRequestMetricUtils",
            exports = "getSanitizedJid",
            adaptation = WhatsAppAdaptation.DIRECT)
    private String sanitizeGroupJidForWam(Jid group) {
        if (group == null) {
            return "";
        }
        var jid = group.toString();
        return jid.contains("-") ? "" : jid;
        }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendAppStateSyncMsgJob", exports = "encryptAndSendKeyMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendPeerMessage(JidProvider chatJidProvider, ChatMessageInfo response) {
        var chatJid = Objects.requireNonNull(chatJidProvider, "chatJid cannot be null").toJid();
        Objects.requireNonNull(response, "response cannot be null");
        messageService.sendPeer(chatJid, response); // WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact", exports = "USyncContactProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebUsync", exports = "USyncQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, Boolean> hasWhatsapp(Collection<? extends JidProvider> phoneNumbersProvider) {
        var phoneNumbers = Objects.requireNonNull(phoneNumbersProvider, "phoneNumbers cannot be null").stream().map(JidProvider::toJid).toList();
        if (phoneNumbers.isEmpty()) {
            return Map.of();
        }

        // WAWebUsync.USyncQuery.$3: per-user element carries jid + <contact>+phone</contact>.
        // Build a UsyncQuery anchored on the contact protocol with background context, mirroring
        // the WA Web bulk-availability lookup driven by WAWebContactSyncApi.syncContactList.
        var query = UsyncQuery.ofContact(UsyncAddressingMode.PN)
                .withContext(UsyncContext.BACKGROUND);
        var hasUser = false;
        for (var jid : phoneNumbers) {
            var phoneNumber = jid.toPhoneNumber().orElse(null);
            if (phoneNumber == null) {
                continue;
            }
            // via WAWebCommsWapMd.USER_JID, matching the live IQ shape exactly. The contact
            // payload itself is rendered by UsyncContactProtocol.buildUserElement from the
            // attached phoneNumber.
            query.withUser(UsyncUser.byPhoneNumber(phoneNumber).withId(jid.toUserJid()));
            hasUser = true;
        }
        if (!hasUser) {
            return Map.of();
        }

        UsyncResult result;
        try {
            result = sendNode(query); // WAWebUsync.USyncQuery.execute
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WhatsAppServerRuntimeException("hasWhatsapp interrupted while waiting on usync backoff", e);
        }
        if (result.failed()) {
            // Top-level IQ error envelope: surface as a server-side rejection so callers can
            // distinguish transport faults from a clean empty result.
            var top = result.topLevelError().orElse(null);
            throw new WhatsAppServerRuntimeException("hasWhatsapp rejected: code="
                    + (top == null ? -1 : top.errorCode()) + ", text="
                    + (top == null ? "" : top.errorText()));
        }

        var results = new HashMap<Jid, Boolean>();
        for (var userResult : result.users()) {
            var userJid = userResult.id().orElse(null);
            if (userJid == null) {
                continue;
            }
            var contact = userResult.getProtocolResult(UsyncContactProtocol.NAME).orElse(null);
            if (!(contact instanceof ContactResult contactResult)) {
                continue;
            }
            results.put(userJid.toUserJid(), "in".equals(contactResult.type()));
        }
        return Collections.unmodifiableMap(results);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact", exports = "USyncContactProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean hasWhatsapp(JidProvider phoneProvider) {
        var phone = Objects.requireNonNull(phoneProvider, "phone cannot be null").toJid();
        var map = hasWhatsapp(List.of(phone));
        for (var registered : map.values()) {
            if (Boolean.TRUE.equals(registered)) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact", exports = "contactParser",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<String> queryName(JidProvider jidProvider) {
        var jid = Objects.requireNonNull(jidProvider, "jid cannot be null").toJid();

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

        // <user><contact>+phone</contact></user> entry whose response carries the push name as
        // the <contact> child's text content.
        var query = UsyncQuery.ofContact(UsyncAddressingMode.PN)
                .withContext(UsyncContext.BACKGROUND)
                .withUser(UsyncUser.byPhoneNumber(phoneNumber));

        UsyncResult result;
        try {
            result = sendNode(query); // WAWebUsync.USyncQuery.execute
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WhatsAppServerRuntimeException("queryName interrupted while waiting on usync backoff", e);
        }
        if (result.failed()) {
            return Optional.empty();
        }

        for (var userResult : result.users()) {
            var contact = userResult.getProtocolResult(UsyncContactProtocol.NAME).orElse(null);
            if (!(contact instanceof ContactResult contactResult)) {
                continue;
            }
            var content = contactResult.content().orElse(null);
            if (content != null && !content.isBlank()) {
                return Optional.of(content);
            }
        }
        return Optional.empty();
    }

    /** {@inheritDoc} */
    @Override
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
        var query = UsyncQuery.ofContact(UsyncAddressingMode.PN)
                .withContext(UsyncContext.BACKGROUND);
        var byPhone = new HashMap<String, Jid>();
        var requestedCount = 0;
        for (var phoneJid : phoneJids) {
            var phoneNumber = phoneJid.toPhoneNumber().orElse(null);
            if (phoneNumber == null) {
                continue;
            }
            byPhone.put(phoneNumber, phoneJid);
            query.withUser(UsyncUser.byPhoneNumber(phoneNumber));
            requestedCount++;
        }
        if (requestedCount == 0) {
            return Map.of();
        }

        // WAWebContactSyncLogger.contactSyncLogger.createEventContext: WAM start timestamp
        var syncStartTimestamp = Instant.now();
        // WAWebContactSyncLogger PROTOCOL_BIT.CONTACT (0) - only protocol queried here
        var requestProtocolBitmask = 1;
        // WAWebContactSyncLogger SYNC_REQUEST_ORIGIN.UNKNOWN (0) - public API has no explicit origin
        var requestOrigin = 0;

        UsyncResult result;
        try {
            result = sendNode(query); // WAWebContactSyncApi.syncContactList -> USyncQuery.execute
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            var endTs = Instant.now();
            wamService.commit(new ContactSyncEventEventBuilder()
                    .contactSyncType("BACKGROUND_QUERY")
                    .contactSyncRequestOrigin(requestOrigin)
                    .contactSyncSuccess(false)
                    .contactSyncNoop(false)
                    .contactSyncErrorCode(0)
                    .contactSyncStartTimestamp(syncStartTimestamp)
                    .contactSyncEndTimestamp(endTs)
                    .contactSyncLatency((int) (endTs.toEpochMilli() - syncStartTimestamp.toEpochMilli()))
                    .contactSyncRequestedCount(requestedCount)
                    .contactSyncResponseCount(0)
                    .contactSyncRequestProtocol(requestProtocolBitmask)
                    .contactSyncFailureProtocol(0)
                    .build());
            throw new WhatsAppServerRuntimeException("syncContacts interrupted while waiting on usync backoff", e);
        } catch (RuntimeException e) {
            // WAWebContactSyncLogger.contactSyncLogger.logFailure: emit ContactSyncEvent (id 1006)
            // for the background contact-sync failure path.
            var endTs = Instant.now();
            wamService.commit(new ContactSyncEventEventBuilder()
                    .contactSyncType("BACKGROUND_QUERY")
                    .contactSyncRequestOrigin(requestOrigin)
                    .contactSyncSuccess(false)
                    .contactSyncNoop(false)
                    .contactSyncErrorCode(0)
                    .contactSyncStartTimestamp(syncStartTimestamp)
                    .contactSyncEndTimestamp(endTs)
                    .contactSyncLatency((int) (endTs.toEpochMilli() - syncStartTimestamp.toEpochMilli()))
                    .contactSyncRequestedCount(requestedCount)
                    .contactSyncResponseCount(0)
                    .contactSyncRequestProtocol(requestProtocolBitmask)
                    .contactSyncFailureProtocol(0)
                    .build());
            throw e;
        }

        if (result.failed()) {
            var top = result.topLevelError().orElse(null);
            var endTs = Instant.now();
            wamService.commit(new ContactSyncEventEventBuilder()
                    .contactSyncType("BACKGROUND_QUERY")
                    .contactSyncRequestOrigin(requestOrigin)
                    .contactSyncSuccess(false)
                    .contactSyncNoop(false)
                    .contactSyncErrorCode(top == null ? 0 : top.errorCode())
                    .contactSyncStartTimestamp(syncStartTimestamp)
                    .contactSyncEndTimestamp(endTs)
                    .contactSyncLatency((int) (endTs.toEpochMilli() - syncStartTimestamp.toEpochMilli()))
                    .contactSyncRequestedCount(requestedCount)
                    .contactSyncResponseCount(0)
                    .contactSyncRequestProtocol(requestProtocolBitmask)
                    .contactSyncFailureProtocol(requestProtocolBitmask)
                    .build());
            throw new WhatsAppServerRuntimeException("syncContacts rejected: code="
                    + (top == null ? -1 : top.errorCode()) + ", text="
                    + (top == null ? "" : top.errorText()));
        }

        var mapping = new HashMap<Jid, Jid>();
        var responseCount = 0;
        for (var userResult : result.users()) {
            responseCount++;
            var resolvedJid = userResult.id().orElse(null);
            if (resolvedJid == null) {
                continue;
            }
            var contact = userResult.getProtocolResult(UsyncContactProtocol.NAME).orElse(null);
            if (!(contact instanceof ContactResult contactResult)) {
                continue;
            }
            if (!"in".equals(contactResult.type())) {
                continue;
            }
            var echoed = contactResult.content().orElse(null);
            var phoneJid = echoed != null ? byPhone.get(echoed) : null;
            if (phoneJid == null) {
                phoneJid = resolvedJid.toUserJid();
            }
            mapping.put(phoneJid, resolvedJid.toUserJid());
        }

        // WAWebContactSyncLogger.contactSyncLogger.logSuccess: emit ContactSyncEvent (id 1006)
        // for the background contact-sync success path.
        var endTimestamp = Instant.now();
        wamService.commit(new ContactSyncEventEventBuilder()
                .contactSyncType("BACKGROUND_QUERY")
                .contactSyncRequestOrigin(requestOrigin)
                .contactSyncSuccess(true)
                // WAWebContactSyncLogger: noop when requestedCount === 0
                .contactSyncNoop(requestedCount == 0)
                .contactSyncStartTimestamp(syncStartTimestamp)
                .contactSyncEndTimestamp(endTimestamp)
                .contactSyncLatency((int) (endTimestamp.toEpochMilli() - syncStartTimestamp.toEpochMilli()))
                .contactSyncRequestedCount(requestedCount)
                .contactSyncResponseCount(responseCount)
                .contactSyncRequestProtocol(requestProtocolBitmask)
                .contactSyncFailureProtocol(0)
                .build());

        return Collections.unmodifiableMap(mapping);
    }


    /** {@inheritDoc} */
    @Override
    public Optional<Newsletter> queryNewsletter(JidProvider newsletterJidProvider, NewsletterViewerRole role) {
        var newsletterJid = Objects.requireNonNull(newsletterJidProvider, "newsletterJid cannot be null").toJid();
        return queryNewsletter(newsletterJid, role, false);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterMetadataJob", exports = "getNewsletterMetadata",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterDehydratedJob", exports = "mexGetNewsletterDehydrated",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Newsletter> queryNewsletter(JidProvider newsletterJidProvider, NewsletterViewerRole role, boolean dehydrated) {
        var newsletterJid = Objects.requireNonNull(newsletterJidProvider, "newsletterJid cannot be null").toJid();
        if (dehydrated) {
            var request = new FetchNewsletterDehydratedMexRequest(newsletterJid, role != null ? role.name() : null, true);
            var response = sendNode(request);
            var parsed = FetchNewsletterDehydratedMexResponse.of(response);
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            var stored = store.findNewsletterByJid(newsletterJid)
                    .orElseGet(() -> store.addNewNewsletter(newsletterJid));
            var threadMetadata = parsed.flatMap(FetchNewsletterDehydratedMexResponse::threadMetadata).orElse(null);
            if (threadMetadata != null) {
                // WAWebMexFetchNewsletterDehydratedJob folds the few dehydrated scalars
                // back into the store-resident metadata so subsequent reads observe them.
                var metadata = stored.metadata().orElseGet(() -> {
                    var fresh = new NewsletterMetadataBuilder().build();
                    stored.setMetadata(fresh);
                    return fresh;
                });
                var subscribers = threadMetadata.subscribersCount();
                if (subscribers.isPresent()) {
                    metadata.setSubscribersCount(subscribers.getAsLong());
                }
                threadMetadata.verification().ifPresent(value -> metadata.setVerification(
                        "ON".equals(value) ? NewsletterVerification.enabled() : NewsletterVerification.disabled()));
                threadMetadata.wamoSub()
                        .flatMap(FetchNewsletterDehydratedMexResponse.ThreadMetadata.WamoSub::planId)
                        .ifPresent(metadata::setWamoSubPlanId);
            }
            return Optional.of(stored);
        }
        // input = {key: t, type: u, view_role: a}
        var input = new FetchNewsletterMexRequest.Input(
                newsletterJid.toString(),
                "JID",
                role != null ? role.name() : null
        );
        var request = new FetchNewsletterMexRequest(
                Boolean.TRUE, // fetch_creation_time
                Boolean.TRUE, // fetch_full_image (c = u !== "INVITE")
                Boolean.TRUE, // fetch_viewer_metadata
                Boolean.TRUE, // fetch_wamo_sub
                input
        );
        var response = sendNode(request);
        var parsed = FetchNewsletterMexResponse.of(response);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        var newsletter = store.findNewsletterByJid(newsletterJid)
                .orElseGet(() -> store.addNewNewsletter(newsletterJid));
        return Optional.of(newsletter);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterCreateAction", exports = "createNewsletterAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Newsletter createNewsletter(NewsletterCreate create) {
        Objects.requireNonNull(create, "create cannot be null");
        var name = create.name();
        var description = create.description().orElse(null);
        var picture = create.picture().orElse(null);
        var request = new CreateNewsletterMexRequest(name, description, picture == null || picture.length == 0 ? null : Base64.getEncoder().encodeToString(picture));
        var response = sendNode(request);
        var parsed = CreateNewsletterMexResponse.of(response)
                .orElseThrow(() -> new NoSuchElementException("Missing create-newsletter response: %s".formatted(response)));
        var id = parsed.id()
                .orElseThrow(() -> new NoSuchElementException("Missing newsletter id in response"));
        var newsletterJid = Jid.of(id);
        return store.findNewsletterByJid(newsletterJid)
                .orElseGet(() -> store.addNewNewsletter(newsletterJid));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterMetadataJob", exports = "editNewsletterMetadata",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editNewsletterMetadata(NewsletterMetadataEdit edit) {
        Objects.requireNonNull(edit, "edit cannot be null");
        var newsletter = edit.newsletter();
        var name = edit.name().orElse(null);
        var description = edit.description().orElse(null);
        var picture = edit.picture().orElse(null);
        var updates = new JSONObject();
        if (name != null) {
            updates.fluentPut("name", name);
            }
        if (description != null) {
            updates.fluentPut("description", description);
            }
        if (picture != null) {
            updates.fluentPut("picture", picture.length == 0 ? "" : Base64.getEncoder().encodeToString(picture));
        }
        var request = new UpdateNewsletterMexRequest(newsletter.toString(), updates);
        sendNodeWithNoResponse(request);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterDeleteAction", exports = "deleteNewsletterAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Newsletter> deleteNewsletter(JidProvider newsletterProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var existing = store.findNewsletterByJid(newsletter);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        var request = new DeleteNewsletterMexRequest(newsletter.toString());
        sendNodeWithNoResponse(request);
        store.removeNewsletter(newsletter);
        return existing;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSubscribeAction", exports = "subscribeToNewsletterAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void joinNewsletter(JidProvider newsletterProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new JoinNewsletterMexRequest(newsletter.toString());
        sendNodeWithNoResponse(request);
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterUnsubscribeAction", exports = "unsubscribeFromNewsletterAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void leaveNewsletter(JidProvider newsletterProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new LeaveNewsletterMexRequest(newsletter.toString());
        sendNodeWithNoResponse(request);
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterUpdateUserSettingsAction", exports = "updateNewsletterUserSettingsAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void muteNewsletter(JidProvider newsletterProvider, boolean mute) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        // WAWebNewsletterUpdateUserSettingJob: passes {newsletter_id, type, value} as a NESTED input object,
        // not a stringified JSON. WAWebMexClient.fetchQuery places it under variables.input.
        var request = new UpdateNewsletterUserSettingMexRequest(
                newsletter.toString(),       // WAWebNewsletterUpdateUserSettingJob: newsletter_id
                "MUTE_ADMIN_ACTIVITY",
                // WAWebNewsletterUpdateUserSettingJob: ADMIN_NOTIFICATIONS -> "MUTE_ADMIN_ACTIVITY"
                mute ? "ON" : "OFF");
                // WAWebNewsletterUpdateUserSettingJob: MUTED_STATE -> "ON", else "OFF"
        sendNodeWithNoResponse(request);
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSendReactionAction", exports = "sendNewsletterReaction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishContentTypeReactionMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void reactToNewsletterMessage(JidProvider newsletterProvider, String serverMessageId, String emoji) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        Objects.requireNonNull(serverMessageId, "serverMessageId cannot be null");
        var reactionNode = (emoji == null || emoji.isEmpty())
                ? new NodeBuilder().description("reaction_revoke").build()                : new NodeBuilder().description("reaction").attribute("code", emoji).build();
                var stanza = new NodeBuilder()
                .description("message")
                .attribute("id", DataUtils.randomHex(5)) // 10 uppercase hex chars
                .attribute("to", newsletter)
                .attribute("server_id", serverMessageId)
                .content(reactionNode);
        sendNode(stanza);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterRevokeStatusAction", exports = "revokeNewsletterStatusAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterRevokeMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void revokeNewsletterMessage(JidProvider newsletterProvider, String serverMessageId) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        Objects.requireNonNull(serverMessageId, "serverMessageId cannot be null");
        var adminRevokeNode = new NodeBuilder()
                .description("admin_revoke") // WASmaxOutMessagePublishNewsletterRevokeMixin: admin_revoke child
                .build();
        var stanza = new NodeBuilder()
                .description("message")
                .attribute("id", DataUtils.randomHex(5)) // 10 uppercase hex chars
                .attribute("to", newsletter)
                .attribute("server_id", serverMessageId)
                .attribute("edit", "3") // WAWebAck.EDIT_ATTR.NEWSLETTER_MSG_EDIT
                .content(adminRevokeNode);
        sendNode(stanza);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterAcceptAdminInviteAction", exports = "acceptNewsletterAdminInviteAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void acceptNewsletterAdminInvite(JidProvider newsletterProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new AcceptNewsletterAdminInviteMexRequest(newsletter.toString());
        sendNodeWithNoResponse(request);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebRevokeNewsletterAdminInviteAction", exports = "revokeNewsletterAdminInviteAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void revokeNewsletterAdminInvite(JidProvider newsletterProvider, JidProvider adminProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var admin = Objects.requireNonNull(adminProvider, "admin cannot be null").toJid();
        var request = new RevokeNewsletterAdminInviteMexRequest(newsletter.toString(), admin.toString());
        sendNodeWithNoResponse(request);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDemoteNewsletterAdminAction", exports = "demoteNewsletterAdminAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void demoteNewsletterAdmin(JidProvider newsletterProvider, JidProvider adminProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var admin = Objects.requireNonNull(adminProvider, "admin cannot be null").toJid();
        var request = new DemoteNewsletterAdminMexRequest(newsletter.toString(), admin.toString());
        sendNodeWithNoResponse(request);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateNewsletterJob", exports = "mexUpdateNewsletter",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editNewsletterReactionSetting(JidProvider newsletterProvider, NewsletterReactionSettings setting) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
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
        var request = new UpdateNewsletterMexRequest(newsletter.toString(), updates);
        sendNodeWithNoResponse(request);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterAdminCapabilitiesJob", exports = "mexFetchNewsletterAdminCapabilities",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<NewsletterCapability> queryNewsletterAdminCapabilities(JidProvider newsletterProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new FetchNewsletterAdminCapabilitiesMexRequest(newsletter.toString());
        var response = sendNode(request);
        var parsed = FetchNewsletterAdminCapabilitiesMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter admin capabilities response: %s".formatted(response)));
        return parsed.capabilities()
                .stream()
                .map(NewsletterCapability::of)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterAdminInfoJob", exports = "mexFetchNewsletterAdminInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public OptionalLong queryNewsletterAdminInfo(JidProvider newsletterProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new FetchNewsletterAdminInfoMexRequest(newsletter.toString());
        var response = sendNode(request);
        var parsed = FetchNewsletterAdminInfoMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter admin info response: %s".formatted(response)));
        return parsed.adminCount();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterFollowersJob", exports = "mexFetchNewsletterFollowers",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<NewsletterFollower> queryNewsletterFollowers(JidProvider newsletterProvider, int count) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new FetchNewsletterFollowersMexRequest(newsletter.toString(), count);
        var response = sendNode(request);
        var parsed = FetchNewsletterFollowersMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter followers response: %s".formatted(response)));
        return parsed.followers()
                .map(FetchNewsletterFollowersMexResponse.Followers::edges)
                .orElseGet(List::of)
                .stream()
                .map(this::toNewsletterFollower)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Adapts a wire-level {@code Followers.Edges} entry into a
     * {@link NewsletterFollower} domain value.
     *
     * @param edge the wire entry; never {@code null}
     * @return the adapted follower, or {@code null} when the wire entry
     *         is missing the mandatory member identifier
     */
    private NewsletterFollower toNewsletterFollower(FetchNewsletterFollowersMexResponse.Followers.Edges edge) {
        var node = edge.node().orElse(null);
        if (node == null) {
            return null;
        }
        var id = node.id().orElse(null);
        if (id == null) {
            return null;
        }
        return new NewsletterFollowerBuilder()
                .jid(Jid.of(id))
                .displayName(node.displayName().orElse(null))
                .phoneNumber(node.pn().orElse(null))
                .username(node.usernameInfo()
                        .flatMap(FetchNewsletterFollowersMexResponse.Followers.Edges.Node.UsernameInfo::username)
                        .orElse(null))
                .role(NewsletterViewerRole.of(edge.role().orElse(null)))
                .followTime(edge.followTime().orElse(null))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterPendingInvitesJob", exports = "mexFetchNewsletterPendingInvites",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<NewsletterAdminInvite> queryNewsletterPendingInvites(JidProvider newsletterProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new FetchNewsletterPendingInvitesMexRequest(newsletter.toString());
        var response = sendNode(request);
        var parsed = FetchNewsletterPendingInvitesMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter pending invites response: %s".formatted(response)));
        return parsed.pendingAdminInvites()
                .stream()
                .map(entry -> entry.user().orElse(null))
                .filter(Objects::nonNull)
                .map(user -> {
                    var id = user.id().orElse(null);
                    if (id == null) {
                        return null;
                    }
                    return new NewsletterAdminInviteBuilder()
                            .invitee(Jid.of(id))
                            .inviteePhoneNumber(user.pn().orElse(null))
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view) {
        return queryNewsletterDirectoryList(view, null, null, null, null, false);
    }

    /** {@inheritDoc} */
    @Override
    public NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view, String cursorToken) {
        return queryNewsletterDirectoryList(view, null, null, null, cursorToken, false);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterDirectoryListJob", exports = "mexFetchNewsletterDirectoryList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterDirectoryPage queryNewsletterDirectoryList(NewsletterDirectoryListView view, List<String> countryCodes, List<String> categories, Long limit, String cursorToken, boolean fetchStatusMetadata) {
        Objects.requireNonNull(view, "view cannot be null");
        var request = new FetchNewsletterDirectoryListMexRequest(view, countryCodes, categories, limit, cursorToken, fetchStatusMetadata);
        var response = sendNode(request);
        var parsed = FetchNewsletterDirectoryListMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter directory list response: %s".formatted(response)));
        var entries = parsed.result()
                .stream()
                .map(this::toNewsletterDirectoryEntryFromList)
                .filter(Objects::nonNull)
                .toList();
        var nextCursor = parsed.pageInfo()
                .filter(FetchNewsletterDirectoryListMexResponse.PageInfo::hasNextPage)
                .flatMap(FetchNewsletterDirectoryListMexResponse.PageInfo::endCursor)
                .orElse(null);
        return new NewsletterDirectoryPageBuilder()
                .entries(entries)
                .nextCursor(nextCursor)
                .build();
    }

    /**
     * Adapts a {@code result} entry from the directory-list response to
     * a {@link NewsletterDirectoryEntry} domain value.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted entry, or {@code null} when the entry is
     *         missing the mandatory channel identifier
     */
    private NewsletterDirectoryEntry toNewsletterDirectoryEntryFromList(FetchNewsletterDirectoryListMexResponse.Result entry) {
        var id = entry.id().orElse(null);
        if (id == null) {
            return null;
        }
        var threadMetadata = entry.threadMetadata().orElse(null);
        var name = threadMetadata == null
                ? null
                : threadMetadata.name()
                        .flatMap(FetchNewsletterDirectoryListMexResponse.Result.ThreadMetadata.Name::text)
                        .orElse(null);
        var description = threadMetadata == null
                ? null
                : threadMetadata.description()
                        .flatMap(FetchNewsletterDirectoryListMexResponse.Result.ThreadMetadata.Description::text)
                        .orElse(null);
        var picture = threadMetadata == null
                ? null
                : threadMetadata.picture().orElse(null);
        return new NewsletterDirectoryEntryBuilder()
                .jid(Jid.of(id))
                .name(name)
                .description(description)
                .handle(threadMetadata == null ? null : threadMetadata.handle().orElse(null))
                .invite(threadMetadata == null ? null : threadMetadata.invite().orElse(null))
                .verification(threadMetadata == null
                        ? null
                        : threadMetadata.verification()
                                .map("ON"::equals)
                                .map(verified -> verified ? NewsletterVerification.enabled() : NewsletterVerification.disabled())
                                .orElse(null))
                .subscribersCount(threadMetadata == null || threadMetadata.subscribersCount().isEmpty()
                        ? null
                        : threadMetadata.subscribersCount().getAsLong())
                .creationTime(threadMetadata == null ? null : threadMetadata.creationTime().orElse(null))
                .pictureId(picture == null ? null : picture.id().orElse(null))
                .pictureDirectPath(picture == null ? null : picture.directPath().orElse(null))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public NewsletterDirectoryPage searchNewsletterDirectory(String searchText) {
        return searchNewsletterDirectory(searchText, null, null, null, false);
    }

    /** {@inheritDoc} */
    @Override
    public NewsletterDirectoryPage searchNewsletterDirectory(String searchText, List<String> categories) {
        return searchNewsletterDirectory(searchText, categories, null, null, false);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterDirectorySearchResultsJob", exports = "mexFetchNewsletterDirectorySearchResults",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterDirectoryPage searchNewsletterDirectory(String searchText, List<String> categories, Long limit, String cursorToken, boolean fetchStatusMetadata) {
        var request = new FetchNewsletterDirectorySearchResultsMexRequest(searchText, categories, limit, cursorToken, fetchStatusMetadata);
        var response = sendNode(request);
        var parsed = FetchNewsletterDirectorySearchResultsMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter directory search response: %s".formatted(response)));
        var entries = parsed.result()
                .stream()
                .map(this::toNewsletterDirectoryEntryFromSearch)
                .filter(Objects::nonNull)
                .toList();
        var nextCursor = parsed.pageInfo()
                .filter(FetchNewsletterDirectorySearchResultsMexResponse.PageInfo::hasNextPage)
                .flatMap(FetchNewsletterDirectorySearchResultsMexResponse.PageInfo::endCursor)
                .orElse(null);
        return new NewsletterDirectoryPageBuilder()
                .entries(entries)
                .nextCursor(nextCursor)
                .build();
    }

    /**
     * Adapts a {@code result} entry from the directory-search response
     * to a {@link NewsletterDirectoryEntry} domain value.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted entry, or {@code null} when the entry is
     *         missing the mandatory channel identifier
     */
    private NewsletterDirectoryEntry toNewsletterDirectoryEntryFromSearch(FetchNewsletterDirectorySearchResultsMexResponse.Result entry) {
        var id = entry.id().orElse(null);
        if (id == null) {
            return null;
        }
        var threadMetadata = entry.threadMetadata().orElse(null);
        var name = threadMetadata == null
                ? null
                : threadMetadata.name()
                        .flatMap(FetchNewsletterDirectorySearchResultsMexResponse.Result.ThreadMetadata.Name::text)
                        .orElse(null);
        var description = threadMetadata == null
                ? null
                : threadMetadata.description()
                        .flatMap(FetchNewsletterDirectorySearchResultsMexResponse.Result.ThreadMetadata.Description::text)
                        .orElse(null);
        var picture = threadMetadata == null
                ? null
                : threadMetadata.picture().orElse(null);
        return new NewsletterDirectoryEntryBuilder()
                .jid(Jid.of(id))
                .name(name)
                .description(description)
                .handle(threadMetadata == null ? null : threadMetadata.handle().orElse(null))
                .invite(threadMetadata == null ? null : threadMetadata.invite().orElse(null))
                .verification(threadMetadata == null
                        ? null
                        : threadMetadata.verification()
                                .map("ON"::equals)
                                .map(verified -> verified ? NewsletterVerification.enabled() : NewsletterVerification.disabled())
                                .orElse(null))
                .subscribersCount(threadMetadata == null || threadMetadata.subscribersCount().isEmpty()
                        ? null
                        : threadMetadata.subscribersCount().getAsLong())
                .creationTime(threadMetadata == null ? null : threadMetadata.creationTime().orElse(null))
                .pictureId(picture == null ? null : picture.id().orElse(null))
                .pictureDirectPath(picture == null ? null : picture.directPath().orElse(null))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterDirectoryCategoriesPreviewJob", exports = "mexFetchNewsletterDirectoryCategoriesPreview",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<NewsletterDirectoryCategory> queryNewsletterDirectoryCategoriesPreview(String input) {
        var request = new FetchNewsletterDirectoryCategoriesPreviewMexRequest(input);
        var response = sendNode(request);
        var parsed = FetchNewsletterDirectoryCategoriesPreviewMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter directory categories preview response: %s".formatted(response)));
        return parsed.result()
                .stream()
                .map(this::toNewsletterDirectoryCategory)
                .toList();
    }

    /**
     * Adapts a {@code Result} entry from the directory-categories
     * preview response to a {@link NewsletterDirectoryCategory} domain
     * value.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted category, never {@code null}
     */
    private NewsletterDirectoryCategory toNewsletterDirectoryCategory(FetchNewsletterDirectoryCategoriesPreviewMexResponse.Result entry) {
        var featured = entry.newsletters()
                .stream()
                .map(this::toNewsletterDirectoryEntryFromCategoriesPreview)
                .filter(Objects::nonNull)
                .toList();
        return new NewsletterDirectoryCategoryBuilder()
                .identifier(entry.category().orElse(null))
                .title(entry.categoryTitle().orElse(null))
                .featured(featured)
                .build();
    }

    /**
     * Adapts a {@code Newsletters} entry from the directory-categories
     * preview response to a {@link NewsletterDirectoryEntry} domain
     * value.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted entry, or {@code null} when the entry is
     *         missing the mandatory channel identifier
     */
    private NewsletterDirectoryEntry toNewsletterDirectoryEntryFromCategoriesPreview(FetchNewsletterDirectoryCategoriesPreviewMexResponse.Result.Newsletters entry) {
        var id = entry.id().orElse(null);
        if (id == null) {
            return null;
        }
        var threadMetadata = entry.threadMetadata().orElse(null);
        var name = threadMetadata == null
                ? null
                : threadMetadata.name()
                        .flatMap(FetchNewsletterDirectoryCategoriesPreviewMexResponse.Result.Newsletters.ThreadMetadata.Name::text)
                        .orElse(null);
        var description = threadMetadata == null
                ? null
                : threadMetadata.description()
                        .flatMap(FetchNewsletterDirectoryCategoriesPreviewMexResponse.Result.Newsletters.ThreadMetadata.Description::text)
                        .orElse(null);
        var picture = threadMetadata == null
                ? null
                : threadMetadata.picture().orElse(null);
        return new NewsletterDirectoryEntryBuilder()
                .jid(Jid.of(id))
                .name(name)
                .description(description)
                .handle(threadMetadata == null ? null : threadMetadata.handle().orElse(null))
                .invite(threadMetadata == null ? null : threadMetadata.invite().orElse(null))
                .verification(threadMetadata == null
                        ? null
                        : threadMetadata.verification()
                                .map("ON"::equals)
                                .map(verified -> verified ? NewsletterVerification.enabled() : NewsletterVerification.disabled())
                                .orElse(null))
                .subscribersCount(threadMetadata == null || threadMetadata.subscribersCount().isEmpty()
                        ? null
                        : threadMetadata.subscribersCount().getAsLong())
                .creationTime(threadMetadata == null ? null : threadMetadata.creationTime().orElse(null))
                .pictureId(picture == null ? null : picture.id().orElse(null))
                .pictureDirectPath(picture == null ? null : picture.directPath().orElse(null))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public NewsletterDirectoryPage queryRecommendedNewsletters() {
        return queryRecommendedNewsletters(null, null, false);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchRecommendedNewslettersJob", exports = "mexFetchRecommendedNewsletters",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterDirectoryPage queryRecommendedNewsletters(Long limit, List<String> countryCodes, boolean fetchStatusMetadata) {
        var request = new FetchRecommendedNewslettersMexRequest(limit, countryCodes, fetchStatusMetadata);
        var response = sendNode(request);
        var parsed = FetchRecommendedNewslettersMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing recommended newsletters response: %s".formatted(response)));
        var entries = parsed.result()
                .stream()
                .map(this::toNewsletterDirectoryEntryFromRecommended)
                .filter(Objects::nonNull)
                .toList();
        var nextCursor = parsed.pageInfo()
                .filter(FetchRecommendedNewslettersMexResponse.PageInfo::hasNextPage)
                .flatMap(FetchRecommendedNewslettersMexResponse.PageInfo::endCursor)
                .orElse(null);
        return new NewsletterDirectoryPageBuilder()
                .entries(entries)
                .nextCursor(nextCursor)
                .build();
    }

    /**
     * Adapts a {@code Result} entry from the recommended-newsletters
     * response to a {@link NewsletterDirectoryEntry} domain value.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted entry, or {@code null} when the entry is
     *         missing the mandatory channel identifier
     */
    private NewsletterDirectoryEntry toNewsletterDirectoryEntryFromRecommended(FetchRecommendedNewslettersMexResponse.Result entry) {
        var id = entry.id().orElse(null);
        if (id == null) {
            return null;
        }
        var threadMetadata = entry.threadMetadata().orElse(null);
        var name = threadMetadata == null
                ? null
                : threadMetadata.name()
                        .flatMap(FetchRecommendedNewslettersMexResponse.Result.ThreadMetadata.Name::text)
                        .orElse(null);
        var description = threadMetadata == null
                ? null
                : threadMetadata.description()
                        .flatMap(FetchRecommendedNewslettersMexResponse.Result.ThreadMetadata.Description::text)
                        .orElse(null);
        return new NewsletterDirectoryEntryBuilder()
                .jid(Jid.of(id))
                .name(name)
                .description(description)
                .handle(threadMetadata == null ? null : threadMetadata.handle().orElse(null))
                .invite(threadMetadata == null ? null : threadMetadata.invite().orElse(null))
                .verification(threadMetadata == null
                        ? null
                        : threadMetadata.verification()
                                .map("ON"::equals)
                                .map(verified -> verified ? NewsletterVerification.enabled() : NewsletterVerification.disabled())
                                .orElse(null))
                .subscribersCount(threadMetadata == null || threadMetadata.subscribersCount().isEmpty()
                        ? null
                        : threadMetadata.subscribersCount().getAsLong())
                .creationTime(threadMetadata == null ? null : threadMetadata.creationTime().orElse(null))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchSimilarNewslettersJob", exports = "mexFetchSimilarNewsletters",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<NewsletterDirectoryEntry> querySimilarNewsletters(JidProvider newsletterProvider, Long limit, List<String> countryCodes, boolean fetchStatusMetadata) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new FetchSimilarNewslettersMexRequest(newsletter.toString(), limit, countryCodes, fetchStatusMetadata);
        var response = sendNode(request);
        var parsed = FetchSimilarNewslettersMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing similar newsletters response: %s".formatted(response)));
        return parsed.result()
                .stream()
                .map(this::toNewsletterDirectoryEntryFromSimilar)
                .filter(Objects::nonNull)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<NewsletterDirectoryEntry> querySimilarNewsletters(JidProvider newsletter) {
        return querySimilarNewsletters(newsletter, null, null, false);
    }

    /**
     * Adapts a {@code Result} entry from the similar-newsletters
     * response to a {@link NewsletterDirectoryEntry} domain value.
     *
     * <p>The similar-newsletters fragment is the leanest of the
     * directory queries: it exposes only the channel name, picture and
     * verification status. Handle, description, invite and subscribers
     * count are intentionally absent from the wire response and remain
     * empty on the resulting entry.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted entry, or {@code null} when the entry is
     *         missing the mandatory channel identifier
     */
    private NewsletterDirectoryEntry toNewsletterDirectoryEntryFromSimilar(FetchSimilarNewslettersMexResponse.Result entry) {
        var id = entry.id().orElse(null);
        if (id == null) {
            return null;
        }
        var threadMetadata = entry.threadMetadata().orElse(null);
        var name = threadMetadata == null
                ? null
                : threadMetadata.name()
                        .flatMap(FetchSimilarNewslettersMexResponse.Result.ThreadMetadata.Name::text)
                        .orElse(null);
        var picture = threadMetadata == null
                ? null
                : threadMetadata.picture().orElse(null);
        return new NewsletterDirectoryEntryBuilder()
                .jid(Jid.of(id))
                .name(name)
                .verification(threadMetadata == null
                        ? null
                        : threadMetadata.verification()
                                .map("ON"::equals)
                                .map(verified -> verified ? NewsletterVerification.enabled() : NewsletterVerification.disabled())
                                .orElse(null))
                .pictureId(picture == null ? null : picture.id().orElse(null))
                .pictureDirectPath(picture == null ? null : picture.directPath().orElse(null))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchPlaintextLinkPreviewJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<NewsletterLinkPreview> queryNewsletterLinkPreview(String url) {
        Objects.requireNonNull(url, "url cannot be null");
        var request = new FetchPlaintextLinkPreviewMexRequest(url);
        var response = sendNode(request);
        return FetchPlaintextLinkPreviewMexResponse.of(response)
                .map(parsed -> new NewsletterLinkPreviewBuilder()
                        .title(parsed.title().orElse(null))
                        .description(parsed.description().orElse(null))
                        .previewType(parsed.previewType().orElse(null))
                        .thumbnailDirectPath(parsed.directPath().orElse(null))
                        .thumbnailHash(parsed.hash().orElse(null))
                        .thumbnailData(parsed.thumbData().orElse(null))
                        .thumbnailWidth(parsed.width().map(DefaultWhatsAppClient::parseIntegerOrNull).orElse(null))
                        .thumbnailHeight(parsed.height().map(DefaultWhatsAppClient::parseIntegerOrNull).orElse(null))
                        .build());
    }

    /**
     * Parses the supplied string as an {@link Integer} and swallows
     * malformed input by returning {@code null}.
     *
     * <p>The relay reports thumbnail dimensions as strings even though
     * they are always integers, so the conversion happens client-side.
     * Malformed values are observed in the wild from older clients;
     * Cobalt treats them as missing rather than failing the whole
     * preview parse.
     *
     * @param value the raw string, never {@code null}
     * @return the parsed integer, or {@code null} if the string is not
     *         a valid integer
     */
    private static Integer parseIntegerOrNull(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNewsletterIsDomainPreviewableAction", exports = "isDomainPreviewableAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterIsDomainPreviewableJob", exports = "mexFetchNewsletterIsDomainPreviewable",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isNewsletterDomainPreviewable(String url) {
        Objects.requireNonNull(url, "url cannot be null");
        var request = new FetchNewsletterIsDomainPreviewableMexRequest(List.of(url));
        var response = sendNode(request);
        return FetchNewsletterIsDomainPreviewableMexResponse.of(response)
                .map(FetchNewsletterIsDomainPreviewableMexResponse::urlPreviews)
                .orElse(List.of())
                .stream()
                .anyMatch(FetchNewsletterIsDomainPreviewableMexResponse.UrlPreviews::isPreviewable);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterMessageReactionSenderListJob", exports = "mexFetchNewsletterMessageReactionSenderList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<NewsletterReactor> queryNewsletterMessageReactionSenders(JidProvider newsletterProvider, long serverMessageId) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new FetchNewsletterMessageReactionSenderListMexRequest(newsletter.toString(), serverMessageId);
        var response = sendNode(request);
        var parsed = FetchNewsletterMessageReactionSenderListMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter reaction senders response: %s".formatted(response)));
        return parsed.reactions()
                .stream()
                .map(reaction -> {
                    var senders = reaction.senderList()
                            .map(FetchNewsletterMessageReactionSenderListMexResponse.Reactions.SenderList::edges)
                            .orElseGet(List::of)
                            .stream()
                            .map(edge -> edge.node().orElse(null))
                            .filter(Objects::nonNull)
                            .map(node -> {
                                var id = node.id().orElse(null);
                                if (id == null) {
                                    return null;
                                }
                                return new NewsletterReactorSenderBuilder()
                                        .jid(Jid.of(id))
                                        .profilePictureDirectPath(node.profilePicDirectPath().orElse(null))
                                        .build();
                            })
                            .filter(Objects::nonNull)
                            .toList();
                    return new NewsletterReactorBuilder()
                            .reactionCode(reaction.reactionCode().orElse(null))
                            .senders(senders)
                            .build();
                })
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterPollVotersJob", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<NewsletterPollVoter> queryNewsletterPollVoters(JidProvider newsletterProvider, long serverMessageId, long limit, String voteHash) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new FetchNewsletterPollVotersMexRequest(newsletter.toString(), limit, serverMessageId, voteHash);
        var response = sendNode(request);
        var parsed = FetchNewsletterPollVotersMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter poll voters response: %s".formatted(response)));
        return parsed.votes()
                .stream()
                .map(vote -> {
                    var voters = vote.voterList()
                            .map(FetchNewsletterPollVotersMexResponse.Votes.VoterList::edges)
                            .orElseGet(List::of)
                            .stream()
                            .map(edge -> edge.node().orElse(null))
                            .filter(Objects::nonNull)
                            .map(node -> node.id().orElse(null))
                            .filter(Objects::nonNull)
                            .map(Jid::of)
                            .toList();
                    return new NewsletterPollVoterBuilder()
                            .optionHash(vote.voteHash().orElse(null))
                            .voters(voters)
                            .build();
                })
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<NewsletterPollVoter> queryNewsletterPollVoters(JidProvider newsletter, long serverMessageId, long limit) {
        return queryNewsletterPollVoters(newsletter, serverMessageId, limit, null);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexChangeNewsletterOwnerJob", exports = "mexChangeNewsletterOwner",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void transferNewsletterOwnership(JidProvider newsletterProvider, JidProvider newOwnerProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var newOwner = Objects.requireNonNull(newOwnerProvider, "newOwner cannot be null").toJid();
        var request = new ChangeNewsletterOwnerMexRequest(newsletter.toString(), newOwner.toString());
        var response = sendNode(request);
        ChangeNewsletterOwnerMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing transfer-newsletter-ownership response: %s".formatted(response)));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexCreateNewsletterAdminInviteJob", exports = "createNewsletterAdminInvite",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NewsletterAdminInvite createNewsletterAdminInvite(JidProvider newsletterProvider, JidProvider inviteeProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var invitee = Objects.requireNonNull(inviteeProvider, "invitee cannot be null").toJid();
        var request = new CreateNewsletterAdminInviteMexRequest(newsletter.toString(), invitee.toString());
        var response = sendNode(request);
        var parsed = CreateNewsletterAdminInviteMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter admin invite response: %s".formatted(response)));
        return new NewsletterAdminInviteBuilder()
                .invitee(invitee)
                .expirationTime(parsed.inviteExpirationTime().orElse(null))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexNewsletterAddPaidPartnershipLabelJob", exports = "mexNewsletterAddPaidPartnershipLabelJob",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void addNewsletterPaidPartnershipLabel(JidProvider newsletterProvider, String serverMessageId) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        Objects.requireNonNull(serverMessageId, "serverMessageId cannot be null");
        var request = new NewsletterAddPaidPartnershipLabelMexRequest(newsletter.toString(), serverMessageId);
        var response = sendNode(request);
        NewsletterAddPaidPartnershipLabelMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter paid-partnership label response: %s".formatted(response)));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexLogNewsletterExposuresJob", exports = "mexLogNewsletterExposures",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void logNewsletterExposures(List<NewsletterExposure> exposures) {
        Objects.requireNonNull(exposures, "exposures cannot be null");
        var request = new LogNewsletterExposuresMexRequest(exposures);
        sendNodeWithNoResponse(request);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexCreateReportAppealJob", exports = "createReportAppeal",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterReportAppeal createNewsletterReportAppeal(String reason, String reportId) {
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(reportId, "reportId cannot be null");
        var request = new CreateReportAppealMexRequest(reason, reportId);
        var response = sendNode(request);
        var parsed = CreateReportAppealMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing report appeal response: %s".formatted(response)));
        var appeal = parsed.appeal()
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing appeal record in report appeal response: %s".formatted(response)));
        return new NewsletterReportAppealBuilder()
                .appealId(appeal.appealId().orElse(null))
                .reportId(appeal.reportId().orElse(parsed.reportId().orElse(null)))
                .state(appeal.state().orElse(null))
                .reason(appeal.appealReason().orElse(null))
                .creationTime(appeal.creationTime().orElse(null))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterEnforcementsJob", exports = "mexFetchNewsletterEnforcements",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<NewsletterEnforcement> queryNewsletterEnforcements(JidProvider newsletterProvider, String locale) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new FetchNewsletterEnforcementsMexRequest(locale, newsletter.toString());
        var response = sendNode(request);
        var parsed = FetchNewsletterEnforcementsMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter enforcements response: %s".formatted(response)));
        var combined = new ArrayList<NewsletterEnforcement>();
        // WAWebMexFetchNewsletterEnforcementsJob exposes four parallel arrays;
        // the channel-info UI presents them as a single chronological list, so
        // Cobalt flattens them into one list discriminated by category.
        parsed.profilePictureDeletions()
                .stream()
                .map(this::toProfilePictureDeletionEnforcement)
                .forEach(combined::add);
        parsed.suspensions()
                .stream()
                .map(this::toSuspensionEnforcement)
                .forEach(combined::add);
        parsed.violatingMessages()
                .stream()
                .map(this::toViolatingMessagesEnforcement)
                .forEach(combined::add);
        parsed.geosuspensions()
                .stream()
                .map(this::toGeosuspensionEnforcement)
                .forEach(combined::add);
        return Collections.unmodifiableList(combined);
    }

    /**
     * Adapts a {@code profile_picture_deletions} entry to a
     * {@link NewsletterEnforcement} domain value.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted enforcement, never {@code null}
     */
    private NewsletterEnforcement toProfilePictureDeletionEnforcement(FetchNewsletterEnforcementsMexResponse.ProfilePictureDeletions entry) {
        return new NewsletterEnforcementBuilder()
                .category(NewsletterEnforcement.Category.PROFILE_PICTURE_DELETION)
                .enforcementId(entry.enforcementId().orElse(null))
                .violationCategory(entry.enforcementViolationCategory().orElse(null))
                .source(entry.enforcementSource().orElse(null))
                .creationTime(entry.enforcementCreationTime().orElse(null))
                .appealState(entry.appealState().orElse(null))
                .appealCreationTime(entry.appealCreationTime().orElse(null))
                .policyExplanation(entry.enforcementPolicyInformation()
                        .flatMap(FetchNewsletterEnforcementsMexResponse.ProfilePictureDeletions.EnforcementPolicyInformation::explanation)
                        .orElse(null))
                .build();
    }

    /**
     * Adapts a {@code suspensions} entry to a
     * {@link NewsletterEnforcement} domain value.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted enforcement, never {@code null}
     */
    private NewsletterEnforcement toSuspensionEnforcement(FetchNewsletterEnforcementsMexResponse.Suspensions entry) {
        return new NewsletterEnforcementBuilder()
                .category(NewsletterEnforcement.Category.SUSPENSION)
                .enforcementId(entry.enforcementId().orElse(null))
                .violationCategory(entry.enforcementViolationCategory().orElse(null))
                .source(entry.enforcementSource().orElse(null))
                .creationTime(entry.enforcementCreationTime().orElse(null))
                .appealState(entry.appealState().orElse(null))
                .appealCreationTime(entry.appealCreationTime().orElse(null))
                .policyExplanation(entry.enforcementPolicyInformation()
                        .flatMap(FetchNewsletterEnforcementsMexResponse.Suspensions.EnforcementPolicyInformation::explanation)
                        .orElse(null))
                .build();
    }

    /**
     * Adapts a {@code violating_messages} entry to a
     * {@link NewsletterEnforcement} domain value.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted enforcement, never {@code null}
     */
    private NewsletterEnforcement toViolatingMessagesEnforcement(FetchNewsletterEnforcementsMexResponse.ViolatingMessages entry) {
        var base = entry.baseEnforcementData();
        var contentData = entry.contentData();
        var messageIds = new ArrayList<String>();
        contentData.flatMap(FetchNewsletterEnforcementsMexResponse.ViolatingMessages.ContentData::serverMsgId).ifPresent(messageIds::add);
        return new NewsletterEnforcementBuilder()
                .category(NewsletterEnforcement.Category.VIOLATING_MESSAGES)
                .enforcementId(base.flatMap(FetchNewsletterEnforcementsMexResponse.ViolatingMessages.BaseEnforcementData::enforcementId).orElse(null))
                .violationCategory(base.flatMap(FetchNewsletterEnforcementsMexResponse.ViolatingMessages.BaseEnforcementData::enforcementViolationCategory).orElse(null))
                .source(base.flatMap(FetchNewsletterEnforcementsMexResponse.ViolatingMessages.BaseEnforcementData::enforcementSource).orElse(null))
                .creationTime(base.flatMap(FetchNewsletterEnforcementsMexResponse.ViolatingMessages.BaseEnforcementData::enforcementCreationTime).orElse(null))
                .appealState(base.flatMap(FetchNewsletterEnforcementsMexResponse.ViolatingMessages.BaseEnforcementData::appealState).orElse(null))
                .appealCreationTime(base.flatMap(FetchNewsletterEnforcementsMexResponse.ViolatingMessages.BaseEnforcementData::appealCreationTime).orElse(null))
                .policyExplanation(base.flatMap(FetchNewsletterEnforcementsMexResponse.ViolatingMessages.BaseEnforcementData::enforcementPolicyInformation)
                        .flatMap(FetchNewsletterEnforcementsMexResponse.ViolatingMessages.BaseEnforcementData.EnforcementPolicyInformation::explanation)
                        .orElse(null))
                .affectedMessageIds(messageIds)
                .build();
    }

    /**
     * Adapts a {@code geosuspensions} entry to a
     * {@link NewsletterEnforcement} domain value.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted enforcement, never {@code null}
     */
    private NewsletterEnforcement toGeosuspensionEnforcement(FetchNewsletterEnforcementsMexResponse.Geosuspensions entry) {
        var base = entry.baseEnforcementData();
        return new NewsletterEnforcementBuilder()
                .category(NewsletterEnforcement.Category.GEOSUSPENSIONS)
                .enforcementId(base.flatMap(FetchNewsletterEnforcementsMexResponse.Geosuspensions.BaseEnforcementData::enforcementId).orElse(null))
                .violationCategory(base.flatMap(FetchNewsletterEnforcementsMexResponse.Geosuspensions.BaseEnforcementData::enforcementViolationCategory).orElse(null))
                .source(base.flatMap(FetchNewsletterEnforcementsMexResponse.Geosuspensions.BaseEnforcementData::enforcementSource).orElse(null))
                .creationTime(base.flatMap(FetchNewsletterEnforcementsMexResponse.Geosuspensions.BaseEnforcementData::enforcementCreationTime).orElse(null))
                .appealState(base.flatMap(FetchNewsletterEnforcementsMexResponse.Geosuspensions.BaseEnforcementData::appealState).orElse(null))
                .appealCreationTime(base.flatMap(FetchNewsletterEnforcementsMexResponse.Geosuspensions.BaseEnforcementData::appealCreationTime).orElse(null))
                .policyExplanation(base.flatMap(FetchNewsletterEnforcementsMexResponse.Geosuspensions.BaseEnforcementData::enforcementPolicyInformation)
                        .flatMap(FetchNewsletterEnforcementsMexResponse.Geosuspensions.BaseEnforcementData.EnforcementPolicyInformation::explanation)
                        .orElse(null))
                .affectedCountries(entry.countryCodes())
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterReportsJob", exports = "mexFetchNewsletterReports",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<NewsletterReport> queryNewsletterReports() {
        var request = new FetchNewsletterReportsMexRequest();
        var response = sendNode(request);
        var parsed = FetchNewsletterReportsMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter reports response: %s".formatted(response)));
        return parsed.channelsReports()
                .stream()
                .map(this::toNewsletterReport)
                .toList();
    }

    /**
     * Adapts a {@code channels_reports} entry to a
     * {@link NewsletterReport} domain value.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted report, never {@code null}
     */
    private NewsletterReport toNewsletterReport(FetchNewsletterReportsMexResponse.ChannelsReports entry) {
        var reportedContent = entry.reportedContentData()
                .map(this::toReportedContent)
                .orElse(null);
        var appeal = entry.appeal()
                .map(this::toReportAppealFromReports)
                .orElse(null);
        return new NewsletterReportBuilder()
                .reportId(entry.reportId().orElse(null))
                .status(entry.status().orElse(null))
                .creationTime(entry.creationTime().orElse(null))
                .lastUpdateTime(entry.lastUpdateTime().orElse(null))
                .channelName(entry.channelName().orElse(null))
                .channelJid(entry.channelJid().map(Jid::of).orElse(null))
                .reportedContent(reportedContent)
                .appeal(appeal)
                .build();
    }

    /**
     * Adapts a wire-level {@code reported_content_data} entry to a
     * {@link NewsletterReport.ReportedContent} domain value.
     *
     * <p>The wire entry is one of three GraphQL inline fragments
     * discriminated by the {@code __typename} field; this helper maps
     * the typename to the typed
     * {@link NewsletterReport.ReportedContent.Kind} discriminator and
     * promotes the corresponding identifier into the right domain
     * field.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted descriptor, never {@code null}
     */
    private NewsletterReport.ReportedContent toReportedContent(FetchNewsletterReportsMexResponse.ChannelsReports.ReportedContentData entry) {
        var typename = entry.typename().orElse(null);
        NewsletterReport.ReportedContent.Kind kind;
        if ("XWA2ChannelStatusData".equals(typename)) {
            kind = NewsletterReport.ReportedContent.Kind.STATUS;
        } else if ("XWA2ChannelQuestionResponseData".equals(typename)) {
            kind = NewsletterReport.ReportedContent.Kind.QUESTION_RESPONSE;
        } else if ("XWA2ChannelServerMsgData".equals(typename)) {
            kind = NewsletterReport.ReportedContent.Kind.CHANNEL_MESSAGE;
        } else {
            kind = null;
        }
        return new NewsletterReportReportedContentBuilder()
                .kind(kind)
                .serverMessageId(entry.serverMsgId().orElseGet(() -> entry.questionData()
                        .flatMap(FetchNewsletterReportsMexResponse.ChannelsReports.ReportedContentData.QuestionData::serverMsgId)
                        .orElse(null)))
                .serverStatusId(entry.serverId().orElse(null))
                .serverResponseId(entry.serverResponseId().orElse(null))
                .responderName(entry.notifyName().orElse(null))
                .build();
    }

    /**
     * Adapts a {@code reports} {@code appeal} sub-object to a
     * {@link NewsletterReportAppeal} domain value.
     *
     * @param entry the wire entry; never {@code null}
     * @return the adapted appeal record, never {@code null}
     */
    private NewsletterReportAppeal toReportAppealFromReports(FetchNewsletterReportsMexResponse.ChannelsReports.Appeal entry) {
        return new NewsletterReportAppealBuilder()
                .appealId(entry.appealId().orElse(null))
                .reportId(entry.reportId().orElse(null))
                .state(entry.state().orElse(null))
                .reason(entry.appealReason().orElse(null))
                .creationTime(entry.creationTime().orElse(null))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterInsightsJob", exports = "mexFetchNewsletterInsights",
            adaptation = WhatsAppAdaptation.DIRECT)
    public List<NewsletterInsightMetric> queryNewsletterInsights(JidProvider newsletterProvider, List<String> metrics) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new FetchNewsletterInsightsMexRequest(newsletter.toString(), metrics);
        var response = sendNode(request);
        var parsed = FetchNewsletterInsightsMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing newsletter insights response: %s".formatted(response)));
        return parsed.result()
                .stream()
                .map(metric -> {
                    var values = metric.values()
                            .stream()
                            .map(value -> new NewsletterInsightMetricValueBuilder()
                                    .value(value.value().orElse(null))
                                    .country(value.country().orElse(null))
                                    .role(value.role().orElse(null))
                                    .timestamp(value.timestamp().orElse(null))
                                    .build())
                            .toList();
                    return new NewsletterInsightMetricBuilder()
                            .identifier(metric.id().orElse(null))
                            .values(values)
                            .lastUpdateTime(parsed.lastUpdateTime().orElse(null))
                            .metricsStatus(parsed.metricsStatus().orElse(null))
                            .build();
                })
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGetDsbInfoJob", exports = "getDsbInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMexGetDsbInfoJob", exports = "mexGetDsbInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public String queryNewsletterDsbInfo(String entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        var request = new GetDsbInfoMexRequest(entityId);
        var response = sendNode(request);
        return GetDsbInfoMexResponse.of(response)
                .flatMap(GetDsbInfoMexResponse::referenceNumber)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing DSR reference number in response: %s".formatted(response)));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactStatusBridge", exports = "getStatus",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGetAboutQueryJob", exports = "getAbout",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<String> queryAbout(JidProvider jidProvider) {
        var jid = Objects.requireNonNull(jidProvider, "jid cannot be null").toJid();
        //   -> WAWebMexUsersGetAboutStatus.getMexUsersAboutStatus (USync MEX)
        //   -> WAWebMexFetchAboutStatusJob.mexGetAbout
        //   else -> USyncQuery().withStatusProtocol() (USync MEX equivalent)
        if (jid.hasLidServer() || abPropsService.getBool(ABProp.MEX_USYNC_ABOUT_STATUS)) {
            return queryAboutViaUsyncMex(jid);
        }
        return queryAboutViaMexGetAbout(jid);
    }

    /**
     * Queries the about-status line for the given user through the legacy
     * direct {@code <iq xmlns="status">} stanza.
     *
     * <p>Sends an {@code <iq xmlns="status" to="s.whatsapp.net" type="get">}
     * stanza whose body is {@code <status><user jid="..."/></status>} and
     * parses the response {@code <user><status>TEXT</status></user>} child.
     *
     * <p>This path is preserved as a fallback for environments where neither
     * modern MEX transport is reachable; WA Web no longer reaches this code
     * path through {@code WAWebContactStatusBridge.getStatus}, so the public
     * entry point {@link #queryAbout(Jid)} does not invoke it under normal
     * conditions.
     *
     * @param jid the user JID whose about text should be fetched
     * @return the about text when the server responds with a non-empty
     *         {@code <status>} element; {@link Optional#empty()} otherwise
     * @throws WhatsAppSessionException.Closed if the socket is closed
     */
    @SuppressWarnings("unused")
    private Optional<String> queryAboutViaStatusIq(Jid jid) {
        var userNode = new NodeBuilder()
                .description("user")
                .attribute("jid", jid)
                .build();
        var statusQuery = new NodeBuilder()
                .description("status")
                .content(userNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "status")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(statusQuery);
        var response = sendNode(iqNode);
        // Response shape: <iq><status><user jid="..."><status>TEXT</status></user></status></iq>
        return response.getChild("status")
                .flatMap(statusNode -> statusNode.getChild("user"))
                .flatMap(userResp -> userResp.getChild("status"))
                .flatMap(Node::toContentString)
                .filter(s -> !s.isEmpty());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexGetUsernameJob", exports = "mexGetUsernameQueryJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<String> queryUsername() {
        var request = new GetUsernameMexRequest();
        var response = sendNode(request);
        return GetUsernameMexResponse.of(response)
                .flatMap(GetUsernameMexResponse::usernameInfo)
                .flatMap(GetUsernameMexResponse.UsernameInfo::username)
                .filter(s -> !s.isEmpty());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameJob", exports = "mexSetUsernameQueryJob",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean editUsername(String username) {
        // the reservation tuple (reserved/session_id/source) is part of the WA Web sign-up surface.
        var request = new SetUsernameMexRequest(username, null, null, null);
        var response = sendNode(request);
        return SetUsernameMexResponse.of(response)
                .map(SetUsernameMexResponse::isSuccess)
                .orElse(false);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameKeyJob", exports = "mexSetUsernameKeyQueryJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editUsernameRecoveryKey(String pin) {
        var request = new SetUsernameKeyMexRequest(pin);
        var response = sendNode(request);
        var parsed = SetUsernameKeyMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException(
                        "Change username recovery key returned no payload: " + response));
        if (!parsed.isSuccess()) {
            throw new WhatsAppServerRuntimeException(
                    "Change username recovery key rejected: " + parsed.result().orElse("(no result)"));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexUsernameAvailability", exports = "mexCheckUsernameAvailabilityQueryJob",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean checkUsernameAvailability(String candidate) {
        var request = new UsernameAvailabilityMexRequest(candidate);
        var response = sendNode(request);
        return UsernameAvailabilityMexResponse.of(response)
                .map(UsernameAvailabilityMexResponse::isUsernameAvailable)
                .orElse(false);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateTextStatusJob", exports = "mexUpdateTextStatus",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebTextStatusAction", exports = "setMyTextStatus",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editTextStatus(String text, String emoji, Duration ephemeralDuration) {
        if (ephemeralDuration != null && ephemeralDuration.isNegative()) {
            throw new IllegalArgumentException("ephemeralDuration cannot be negative");
        }
        var seconds = ephemeralDuration == null ? 0L : ephemeralDuration.toSeconds();
        var request = new UpdateTextStatusMexRequest(text, emoji, seconds);
        var response = sendNode(request);
        var parsed = UpdateTextStatusMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException(
                        "Change text status returned no payload: " + response));
        if (!parsed.isSuccess()) {
            throw new WhatsAppServerRuntimeException(
                    "Change text status rejected: " + parsed.result().orElse("(no result)"));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchTextStatusListJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMexFetchTextStatusListJob", exports = "mexGetTextStatusList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, ContactTextStatus> queryUserTextStatuses(List<? extends JidProvider> usersProvider) {
        var users = Objects.requireNonNull(usersProvider, "users cannot be null").stream().map(JidProvider::toJid).toList();
        // input is a JSON array of {jid, last_update_time?, privacy_token?} entries.
        // WA Web sends a single entry per call; Cobalt batches the supplied user list.
        var inputBuilder = new StringBuilder("[");
        for (var i = 0; i < users.size(); i++) {
            var user = Objects.requireNonNull(users.get(i), "users cannot contain null entries");
            if (i > 0) {
                inputBuilder.append(',');
            }
            inputBuilder.append("{\"jid\":\"").append(user).append("\"}");
        }
        inputBuilder.append(']');
        var request = new FetchTextStatusListMexRequest(inputBuilder.toString());
        var response = sendNode(request);
        var items = FetchTextStatusListMexResponse.of(response)
                .map(FetchTextStatusListMexResponse::items)
                .orElseGet(List::of);
        if (items.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<Jid, ContactTextStatus>(items.size());
        for (var item : items) {
            // {id: WidFactory.createWid(jid), textStatusString: text, textStatusEmoji: emoji?.content,
            //  textStatusEphemeralDuration: ephemeral_duration_sec, textStatusLastUpdateTime: Number(last_update_time)}
            var jidString = item.jid().orElse(null);
            if (jidString == null) {
                continue;
            }
            var jid = Jid.of(jidString);
            var ephemeral = item.ephemeralDurationSec()
                    .map(Duration::toSeconds)
                    .map(Long::intValue)
                    .orElse(null);
            var lastUpdate = item.lastUpdateTime()
                    .map(Instant::getEpochSecond)
                    .orElse(null);
            var status = new ContactTextStatusBuilder()
                    .text(item.text().orElse(null))
                    .emoji(item.emoji()
                            .flatMap(FetchTextStatusListMexResponse.Item.Emoji::content)
                            .orElse(null))
                    .ephemeralDurationSeconds(ephemeral)
                    .lastUpdateTimeSeconds(lastUpdate)
                    .build();
            result.put(jid, status);
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Queries the about-status line for the given WhatsApp user through the
     * MEX {@code mexGetAbout} GraphQL transport.
     *
     * <p>WA Web's {@code mexGetAbout} resolves this through the
     * {@code xwa2_users_updates_since[0].updates[0].text} projection, which
     * is the most recent about-status update for the queried user. This
     * helper mirrors that projection.
     *
     * <p>This is the non-LID, AB-prop-disabled branch of
     * {@link #queryAbout(Jid)} dispatch and is private because the dispatch
     * logic is centralised on {@link #queryAbout(Jid)}.
     *
     * @param user the non-{@code null} user JID to query
     * @return the about line, or {@link Optional#empty()} when the user has
     *         no about set or the relay returned no payload
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAboutStatusJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAboutStatusJob", exports = "mexGetAbout",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Optional<String> queryAboutViaMexGetAbout(Jid user) {
        var request = new FetchAboutStatusMexRequest(user.toString());
        var response = sendNode(request);
        return FetchAboutStatusMexResponse.of(response)
                .map(FetchAboutStatusMexResponse::items)
                .flatMap(items -> items.stream().findFirst())
                .map(FetchAboutStatusMexResponse.Item::updates)
                .flatMap(updates -> updates.stream().findFirst())
                .flatMap(FetchAboutStatusMexResponse.Item.Updates::text)
                .filter(s -> !s.isEmpty());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexLidChangeNotificationQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<LidChange> queryLidChangeNotification() {
        var request = new LidChangeNotificationMexRequest();
        var response = sendNode(request);
        return LidChangeNotificationMexResponse.of(response).map(parsed -> {
            return new LidChangeBuilder()
                    .oldValue(parsed.oldValue().orElse(null))
                    .newValue(parsed.newValue().orElse(null))
                    .build();
        });
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebFetchOHAIKeyConfigJob", exports = "mexFetchOHAIKeyConfig",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<OhaiKeyConfig> queryOhaiKeyConfig() {
        var request = new FetchOHAIKeyConfigMexRequest();
        var response = sendNode(request);
        return FetchOHAIKeyConfigMexResponse.of(response)
                .map(parsed -> {
                    var configs = new ArrayList<OhaiKeyConfig>(parsed.ohaiConfigs().size());
                    for (var entry : parsed.ohaiConfigs()) {
                        configs.add(new OhaiKeyConfig(entry.aeadId().orElse(null),
                                entry.expirationDate().orElse(null),
                                entry.kdfId().orElse(null),
                                entry.kemId().orElse(null),
                                entry.keyId().orElse(null),
                                entry.lastUpdatedTime().orElse(null),
                                entry.publicKey().orElse(null)));
                    }
                    return Collections.unmodifiableList(configs);
                })
                .orElseGet(List::of);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryCatalog", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebBizProductCatalogAction", exports = "queryCatalog",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<BusinessCatalogEntry> queryBusinessCatalog(JidProvider businessJidProvider) {
        var businessJid = Objects.requireNonNull(businessJidProvider, "businessJid cannot be null").toJid();
        return queryBusinessCatalog(businessJid, DEFAULT_CATALOG_LIMIT);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryCatalog", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebBizProductCatalogAction", exports = "queryCatalog",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<BusinessCatalogEntry> queryBusinessCatalog(JidProvider businessJidProvider, int limit) {
        var businessJid = Objects.requireNonNull(businessJidProvider, "businessJid cannot be null").toJid();
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        var request = new QueryCatalogMexRequest(
                businessJid.toString(),
                limit,
                DEFAULT_CATALOG_IMAGE_WIDTH,
                DEFAULT_CATALOG_IMAGE_HEIGHT,
                null
        );
        var response = sendNode(request);
        // WAWebCatalogEventLogger.createCatalogEventLogger(GET_CATALOG): emit GraphqlCatalogRequest WAM event based on response
        logGraphqlCatalogRequest(response, GraphqlCatalogEndpoint.GET_CATALOG);
        return QueryCatalogMexResponse.of(response)
                .map(QueryCatalogMexResponse::products)
                .orElseGet(List::of);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<BusinessCatalog> queryBusinessCollections(JidProvider businessJidProvider) {
        var businessJid = Objects.requireNonNull(businessJidProvider, "businessJid cannot be null").toJid();
        return queryBusinessCollections(businessJid, DEFAULT_CATALOG_LIMIT);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<BusinessCatalog> queryBusinessCollections(JidProvider businessJidProvider, int limit) {
        return queryBusinessCollections(businessJidProvider, limit, PRODUCT_COLLECTION_ITEM_LIMIT);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryProductCollections", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<BusinessCatalog> queryBusinessCollections(JidProvider businessJidProvider, int limit, int itemLimit) {
        var businessJid = Objects.requireNonNull(businessJidProvider, "businessJid cannot be null").toJid();
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (itemLimit <= 0) {
            throw new IllegalArgumentException("itemLimit must be positive");
        }
        // The 10-arg constructor mirrors the JS request.collections object verbatim:
        //   {biz_jid, collection_limit, item_limit, after, width, height,
        //    direct_connection_encrypted_info, variant_info_fields,
        //    variant_thumbnail_height, variant_thumbnail_width}.
        var request = new QueryProductCollectionsMexRequest(
                businessJid.toString(),         // biz_jid: a.toString()
                limit,                           // collection_limit: String(s)
                itemLimit,                       // item_limit: String(c)
                null,                            // after: r (no pagination cursor)
                DEFAULT_CATALOG_IMAGE_WIDTH,     // width: String(_)
                DEFAULT_CATALOG_IMAGE_HEIGHT,    // height: String(l)
                null,                            // direct_connection_encrypted_info: i
                null,                            // variant_info_fields: d
                null,                            // variant_thumbnail_height: m != null ? String(m) : null
                null                             // variant_thumbnail_width: p != null ? String(p) : null
        );
        var response = sendNode(request);
        // WAWebCatalogEventLogger.createCatalogEventLogger(GET_COLLECTIONS): emit GraphqlCatalogRequest WAM event based on response
        logGraphqlCatalogRequest(response, GraphqlCatalogEndpoint.GET_COLLECTIONS);
        return QueryProductCollectionsMexResponse.of(response)
                .map(QueryProductCollectionsMexResponse::collections)
                .orElseGet(List::of);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVerifyPostcodeJob", exports = "VerifyPostcode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public BusinessPostcodeVerification verifyBusinessPostcode(JidProvider businessJidProvider, String directConnectionEncryptedInfo) {
        var businessJid = Objects.requireNonNull(businessJidProvider, "businessJid cannot be null").toJid();
        Objects.requireNonNull(directConnectionEncryptedInfo, "directConnectionEncryptedInfo cannot be null");
        var request = new IqVerifyPostcodeRequest(businessJid, directConnectionEncryptedInfo);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqVerifyPostcodeResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqVerifyPostcodeResponse.Success success -> new BusinessPostcodeVerificationBuilder()
                    .result(toPostcodeVerificationResult(success.resultCode()))
                    .encryptedLocationName(success.encryptedLocationName().orElse(null))
                    .build();
            case IqVerifyPostcodeResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Verify business postcode rejected: " + clientError.errorCode());
            case IqVerifyPostcodeResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Verify business postcode server error: " + serverError.errorCode());
            case null -> throw new WhatsAppServerRuntimeException(
                    "Verify business postcode returned an unrecognised response");
        };
    }

    /**
     * Projects the typed {@link IqVerifyPostcodeResponse.Success.ResultCode}
     * onto Cobalt's caller-friendly {@link BusinessPostcodeVerificationResult}
     * model by aligning the two enums on their wire string.
     *
     * @param resultCode the typed result code; never {@code null}
     * @return the matching domain enum; never {@code null}
     */
    private static BusinessPostcodeVerificationResult toPostcodeVerificationResult(
            IqVerifyPostcodeResponse.Success.ResultCode resultCode) {
        return BusinessPostcodeVerificationResult.ofWire(resultCode.wireValue())
                .orElseThrow(() -> new WhatsAppServerRuntimeException(
                        "Unmapped verify-postcode result code: " + resultCode));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBizRefreshCartJob", exports = "refreshCart",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public BusinessRefreshedCart refreshBusinessCart(BusinessCartRefresh refresh) {
        Objects.requireNonNull(refresh, "refresh cannot be null");
        var bizJid = refresh.bizJid();
        var productIds = refresh.productIds();
        if (productIds.isEmpty()) {
            throw new IllegalArgumentException("productIds must not be empty");
        }
        for (var productId : productIds) {
            Objects.requireNonNull(productId, "productIds element cannot be null");
        }
        var width = refresh.width();
        var height = refresh.height();
        var directConnectionEncryptedInfo = refresh.directConnectionEncryptedInfo().orElse(null);
        var request = new IqBizRefreshCartRequest(bizJid, productIds, width, height, directConnectionEncryptedInfo);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqBizRefreshCartResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqBizRefreshCartResponse.Success success -> new BusinessRefreshedCartBuilder()
                    .price(toBusinessCartPrice(success.price()))
                    .products(success.products()
                            .stream()
                            .map(DefaultWhatsAppClient::toBusinessCartProduct)
                            .toList())
                    .build();
            case IqBizRefreshCartResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Refresh business cart rejected: " + clientError.errorCode());
            case IqBizRefreshCartResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Refresh business cart server error: " + serverError.errorCode());
            case null -> throw new WhatsAppServerRuntimeException(
                    "Refresh business cart returned an unrecognised response");
        };
    }

    /**
     * Projects a typed {@link IqBizRefreshCartResponse.Success.Price}
     * onto Cobalt's caller-friendly {@link BusinessCartPrice} model.
     *
     * @param price the typed price block; never {@code null}
     * @return the converted block; never {@code null}
     */
    private static BusinessCartPrice toBusinessCartPrice(IqBizRefreshCartResponse.Success.Price price) {
        return new BusinessCartPriceBuilder()
                .subtotal(price.subtotal().orElse(null))
                .total(price.total().orElse(null))
                .currency(price.currency().orElse(null))
                .priceStatus(price.priceStatus().orElse(null))
                .build();
    }

    /**
     * Projects a typed {@link IqBizRefreshCartResponse.Success.Product}
     * cart line onto Cobalt's caller-friendly {@link BusinessCartProduct}
     * model, lifting the optional thumbnail descriptor into the wrapping
     * {@link BusinessCartProductMedia} structure expected by callers.
     *
     * @param product the typed cart line; never {@code null}
     * @return the converted entry; never {@code null}
     */
    private static BusinessCartProduct toBusinessCartProduct(IqBizRefreshCartResponse.Success.Product product) {
        BusinessCartProductMedia media = null;
        var thumbId = product.thumbnailId().orElse(null);
        var thumbUrl = product.thumbnailUrl().orElse(null);
        if (thumbId != null || thumbUrl != null) {
            media = new BusinessCartProductMediaBuilder()
                    .image(new BusinessCartProductImageBuilder()
                            .id(thumbId)
                            .requestImageUrl(thumbUrl)
                            .build())
                    .build();
        }
        var salePrice = product.salePrice()
                .map(sp -> new BusinessCartProductSalePriceBuilder()
                        .price(sp.price())
                        .startDate(sp.startDate().orElse(null))
                        .endDate(sp.endDate().orElse(null))
                        .build())
                .orElse(null);
        return new BusinessCartProductBuilder()
                .id(product.id())
                .name(product.name().orElse(null))
                .price(product.price().orElse(null))
                .currency(product.currency().orElse(null))
                .media(media)
                .maxAvailable(product.maxAvailable())
                .salePrice(salePrice)
                .status(product.status().orElse(null))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryCtwaContextJob", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public BusinessCtwaContext queryCtwaContext(JidProvider businessJidProvider, String inviteCode, String expectedSourceUrl) {
        var businessJid = Objects.requireNonNull(businessJidProvider, "businessJid cannot be null").toJid();
        Objects.requireNonNull(inviteCode, "inviteCode cannot be null");
        Objects.requireNonNull(expectedSourceUrl, "expectedSourceUrl cannot be null");
        var request = new IqQueryCtwaContextRequest(businessJid.toString(), inviteCode, expectedSourceUrl);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqQueryCtwaContextResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqQueryCtwaContextResponse.Success success -> new BusinessCtwaContextBuilder()
                    .sourceUrl(success.sourceUrl())
                    .sourceId(success.sourceId())
                    .sourceType(success.sourceType())
                    .title(success.title().orElse(null))
                    .description(success.description().orElse(null))
                    .thumbnailUrl(success.thumbnailUrl().orElse(null))
                    .thumbnail(success.thumbnailBytes().orElse(null))
                    .mediaUrl(success.mediaUrl().orElse(null))
                    .mediaType(success.mediaType().orElse(null))
                    .sourceApp(success.sourceApp().orElse(null))
                    .greetingMessageBody(success.greetingMessageBody().orElse(null))
                    .automatedGreetingMessageShown(success.automatedGreetingMessageShown().orElse(null))
                    .ctaPayload(success.ctaPayload().orElse(null))
                    .originalImageUrl(success.originalImageUrl().orElse(null))
                    .build();
            case IqQueryCtwaContextResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query CTWA context rejected: " + clientError.errorCode());
            case IqQueryCtwaContextResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query CTWA context server error: " + serverError.errorCode());
            case null -> throw new NoSuchElementException("Missing <context> in CTWA-context response");
        };
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
     * @param response the inbound IQ stanza returned by
     *                 {@link #sendNode(NodeBuilder)}
     * @param endpoint the {@link GraphqlCatalogEndpoint} describing which
     *                 catalog operation was executed
     */
    @WhatsAppWebExport(moduleName = "WAWebCatalogEventLogger", exports = "createCatalogEventLogger",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void logGraphqlCatalogRequest(Node response, GraphqlCatalogEndpoint endpoint) {
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
        } catch (Exception _) {
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
            var firstError = errors.getJSONObject(0);
            var code = firstError == null ? 0 : firstError.getIntValue("code");
            builder.graphqlErrorCode(code)
                    .graphqlRequestResult(GraphqlRequestResult.FAILURE);
        } else {
            builder.graphqlErrorCode(-1)
                    .graphqlRequestResult(GraphqlRequestResult.SUCCESS);
        }
        wamService.commit(builder.build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryBlockListJob", exports = "fetchAndUpdateBlocklist",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void refreshBlockList() {
        var previous = Set.copyOf(store.blockedContacts());
        fetchAndUpdateBlocklist(null);
        var current = store.blockedContacts();
        for (var contact : store.contacts()) {
            var blocked = current.contains(contact.jid());
            if (contact.blocked() != blocked) {
                contact.setBlocked(blocked);
                store.addContact(contact);
            }
        }
        for (var blockedJid : current) {
            if (store.findContactByJid(blockedJid).isEmpty()) {
                var contact = store.addNewContact(blockedJid);
                contact.setBlocked(true);
                store.addContact(contact);
            }
        }
        if (!previous.equals(current)) {
            for (var listener : store.listeners()) {
                listener.onBlockedContacts(this, current);
            }
        }
    }

    /**
     * Orchestrates one cycle of the Blocked Contacts privacy list
     * refresh: sends the cached digest, applies the relay's response
     * to the store, runs the LID-migration safety check, and emits
     * the LID-migration telemetry critical events.
     *
     * @apiNote
     * Called from {@link #refreshBlockList()} on every refresh, and
     * from the LID migration pipeline with
     * {@code reason="post-migration"} immediately after the device
     * finishes its 1:1 LID migration so the next request bypasses
     * the now-stale cache.
     *
     * @implNote
     * This implementation collapses WA Web's
     * {@code WAWebQueryBlockListJob.fetchAndUpdateBlocklist}
     * (orchestration), {@code WAWebGetBlocklistJob.getBlocklist}
     * (relay round-trip), and
     * {@code WAWebApiBlocklist.updateBlocklist} (in-memory replace)
     * into a single virtual-thread-blocking method. The cache-match
     * and CAPI variants are treated as no-ops on the store side; the
     * three mismatch variants drive the replace.
     *
     * @param reason the cause of the refresh; pass
     *               {@code "post-migration"} from the LID migration
     *               pipeline, {@code null} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryBlockListJob", exports = "fetchAndUpdateBlocklist",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGetBlocklistJob", exports = "getBlocklist",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebApiBlocklist", exports = "getBlocklist",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void fetchAndUpdateBlocklist(String reason) {
        if ("post-migration".equals(reason)) {
            store.setBlocklistHash(null);
            store.setReceivedBlocklistMigrationBefore1x1Migration(false);
        }

        var request = new SmaxGetBlockListRequest(store.blocklistHash().orElse(null));
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGetBlockListResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> { /* no-parse: leave the local cache untouched */ }
            case SmaxGetBlockListResponse.SuccessWithMatch _ -> { /* cache hit */ }
            case SmaxGetBlockListResponse.ClientError _ -> { /* transient error, leave cache */ }
            case SmaxGetBlockListResponse.ServerError _ -> { /* transient error, leave cache */ }
            case SmaxGetBlockListResponse.SuccessWithMismatch v ->
                    applyPnAddressedMismatch(v.listDhash().orElse(null), v.listItem());
            case SmaxGetBlockListResponse.MigratedSuccessWithMismatch v ->
                    applyLidAddressedMismatch(v.listDhash().orElse(null), v.listItem(), false);
            case SmaxGetBlockListResponse.ForceMigratedSuccessWithMismatch v ->
                    applyLidAddressedMismatch(v.listDhash().orElse(null), v.listItem(), true);
            case SmaxGetBlockListResponse.CAPISuccessWithMismatch v ->
                    applyLidAddressedMismatch(v.listDhash().orElse(null), v.listItem(), false);
        }
    }

    /**
     * Applies a PN-addressed blocklist mismatch to the local store.
     *
     * @implNote
     * Mirrors the PN-branch of WA Web's
     * {@code WAWebQueryBlockListJob.fetchAndUpdateBlocklist}. When
     * the device previously believed it was on a migrated blocklist
     * but the relay returns PN-addressing, the migration flag is
     * flipped back to {@code false} and the
     * {@code LidBlocklistUnexpectedPnBlocklist} telemetry event
     * fires.
     *
     * @param dhash the new server digest, or {@code null}
     * @param items the parsed PN-addressed items; never {@code null}
     */
    private void applyPnAddressedMismatch(String dhash, List<SmaxGetBlockListResponse.Item> items) {
        var wasMigrated = store.blocklistMigrated();
        store.setBlocklistHash(dhash);
        replaceBlockedContacts(items);
        if (wasMigrated) {
            commitBlocklistCriticalEvent("LidBlocklistUnexpectedPnBlocklist");
            store.setBlocklistMigrated(false);
        }
        for (var item : items) {
            if (item.displayName() != null && item.jid() != null && item.jid().hasLidServer()) {
                store.findContactByJid(item.jid())
                        .ifPresent(c -> c.setChosenName(item.displayName()));
            }
        }
    }

    /**
     * Applies a LID-addressed blocklist mismatch to the local store,
     * with the migration-safety branch the WA Web counterpart runs.
     *
     * @implNote
     * Mirrors the LID-branch of WA Web's
     * {@code WAWebQueryBlockListJob.fetchAndUpdateBlocklist}. When
     * the device has not yet finished its 1:1 LID migration, the
     * safety check in {@link #runUnmigratedBlocklistSafetyCheck()}
     * either defers the blocklist migration or throws a
     * {@link WhatsAppLidMigrationException} subtype. The
     * force-migration variant additionally emits the
     * {@code LidBlocklistForceMigratedDirty} telemetry event before
     * the replace runs.
     *
     * @param dhash the new server digest, or {@code null}
     * @param items the parsed LID-addressed items; never {@code null}
     * @param dirty whether the relay tagged the list as
     *              force-migrated ({@code dirty="true"})
     */
    private void applyLidAddressedMismatch(String dhash,
                                           List<SmaxGetBlockListResponse.Item> items,
                                           boolean dirty) {
        if (dirty) {
            commitBlocklistCriticalEvent("LidBlocklistForceMigratedDirty");
        }
        store.setBlocklistHash(dhash);
        replaceBlockedContacts(items);
        if (lidMigrationService.isLidMigrated()) {
            if (!store.blocklistMigrated()) {
                store.setBlocklistMigrated(true);
            }
        } else {
            runUnmigratedBlocklistSafetyCheck();
        }
        learnBlocklistIdentifiers(items);
    }

    /**
     * Decides what to do when a LID-addressed blocklist arrives on a
     * device that has not yet finished its 1:1 LID migration.
     *
     * @implNote
     * Mirrors the {@code D()} helper in WA Web's
     * {@code WAWebQueryBlockListJob.fetchAndUpdateBlocklist}. When
     * {@link LidMigrationService#hasStateDiscrepancy()} reports
     * drift, the migration cannot be reconciled and a
     * {@link WhatsAppLidMigrationException.StateDiscrepancy} is
     * thrown. When the peer mapping has already arrived but the
     * device-side migration has not yet run, the early-blocklist
     * flag is set so the deferred migration runs after the 1:1
     * migration completes. Otherwise the
     * {@code LidBlocklistUnmigratedChatDb} telemetry event fires and
     * a {@link WhatsAppLidMigrationException.BlocklistChatDbUnmigrated}
     * is thrown.
     */
    private void runUnmigratedBlocklistSafetyCheck() {
        if (lidMigrationService.hasStateDiscrepancy()) {
            throw new WhatsAppLidMigrationException.StateDiscrepancy();
        }
        if (lidMigrationService.hasReceivedPeerMappings()) {
            store.setReceivedBlocklistMigrationBefore1x1Migration(true);
            return;
        }
        commitBlocklistCriticalEvent("LidBlocklistUnmigratedChatDb");
        throw new WhatsAppLidMigrationException.BlocklistChatDbUnmigrated();
    }

    /**
     * Learns the identifier metadata carried by every blocklist
     * entry: LID-to-PN mappings from the {@code PnJid} arm and
     * username / display-name updates from the {@code Username}
     * and {@code DisplayName} arms.
     *
     * @implNote
     * Mirrors WA Web's
     * {@code WAWebQueryBlockListJob.learnIdentifiers}. Cobalt's
     * Contact model collapses WA Web's separate {@code displayName}
     * and {@code displayNameLID} into a single
     * {@link Contact#chosenName()}, so the LID-side display name is
     * stored as the contact's chosen name only when no chosen name
     * was previously set, to avoid stomping a primary-set push name.
     *
     * @param items the parsed blocklist items; never {@code null}
     */
    private void learnBlocklistIdentifiers(List<SmaxGetBlockListResponse.Item> items) {
        for (var item : items) {
            var jid = item.jid();
            if (jid != null && jid.hasLidServer() && item.pnJid() != null) {
                store.registerLidMapping(item.pnJid(), jid);
            }
            if (jid != null && (item.username() != null || item.displayName() != null)) {
                store.findContactByJid(jid).ifPresent(contact -> {
                    if (item.username() != null) {
                        contact.setUsername(item.username());
                    }
                    if (item.displayName() != null && contact.chosenName().isEmpty()) {
                        contact.setChosenName(item.displayName());
                    }
                });
            }
        }
    }

    /**
     * Bulk-replaces the store's blocked-contact set with the JIDs
     * extracted from the relay's per-item descriptors.
     *
     * @implNote
     * Mirrors WA Web's {@code WAWebApiBlocklist.updateBlocklist}.
     * Items lacking a JID are dropped; they only appear on the
     * sparsely-populated force-migration and Cloud-API variants.
     *
     * @param items the parsed blocklist items; never {@code null}
     */
    private void replaceBlockedContacts(List<SmaxGetBlockListResponse.Item> items) {
        var jids = items.stream()
                .map(SmaxGetBlockListResponse.Item::jid)
                .filter(Objects::nonNull)
                .toList();
        store.setBlockedContacts(jids);
    }

    /**
     * Commits a single blocklist-related critical telemetry event.
     *
     * @implNote
     * Mirrors WA Web's
     * {@code WAWebQueryBlockListJob.commitCriticalEvent} helper
     * (locally named {@code T()}); the {@code "{fetch}"} debug
     * marker is the verbatim payload WA Web sends.
     *
     * @param name the critical-event name (one of
     *             {@code LidBlocklistForceMigratedDirty},
     *             {@code LidBlocklistUnexpectedPnBlocklist},
     *             {@code LidBlocklistUnmigratedChatDb})
     */
    private void commitBlocklistCriticalEvent(String name) {
        wamService.commit(new CriticalEventEventBuilder()
                .name(name)
                .debug("{fetch}")
                .build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBlockUserJob", exports = "blockUnblockUser", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebBlockContactAction", exports = "blockUser", adaptation = WhatsAppAdaptation.ADAPTED)
    public void blockContact(JidProvider contactProvider) {
        var contact = Objects.requireNonNull(contactProvider, "contact cannot be null").toJid();
        updateBlockList(true, contact);
        store.addBlockedContact(contact);
        // WAWebBlockContactAction.blockContact calls WAWebWamBlockEventReporter.logBlockEvent({contact, blockEntryPoint, isBlock:true})
        logBlockEvent(contact, BlockEventActionType.BLOCK);
        for (var listener : store.listeners()) {
            listener.onContactBlocked(this, contact);
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBlockUserJob", exports = "blockUnblockUser", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebBlockContactAction", exports = "unblockUser", adaptation = WhatsAppAdaptation.ADAPTED)
    public void unblockContact(JidProvider contactProvider) {
        var contact = Objects.requireNonNull(contactProvider, "contact cannot be null").toJid();
        updateBlockList(false, contact);
        store.removeBlockedContact(contact);
        // WAWebBlockContactAction.unblockContact calls WAWebWamBlockEventReporter.logBlockEvent({contact, blockEntryPoint, isBlock:false})
        logBlockEvent(contact, BlockEventActionType.UNBLOCK);
        for (var listener : store.listeners()) {
            listener.onContactBlocked(this, contact);
        }
    }

    /**
     * Issues an {@code <iq xmlns="blocklist" type="set">} stanza
     * mirroring WA Web's {@code sendUpdateBlockListRPC}: builds the
     * outbound stanza via
     * {@link SmaxUpdateBlockListRequest#toNode()}, dispatches it via
     * {@link #sendNode(NodeBuilder)} (Cobalt's analogue of
     * {@code WAComms.sendSmaxStanza}), then walks the variant priority
     * chain ({@code SuccessWithMatch -> SuccessWithMismatch ->
     * MigratedSuccessWithMismatch -> CAPISuccessWithMismatch ->
     * InvalidRequest -> InternalServerError}) inside
     * {@link SmaxUpdateBlockListResponse#of(Node, Node)}.
     *
     * <p>WA Web's {@code blockUnblockUser} only inspects the response
     * for {@code InvalidRequest} or {@code InternalServerError} and
     * returns them as {@code {errorCode, errorText}}. Cobalt mirrors
     * that observation by raising
     * {@link WhatsAppServerRuntimeException} on either error variant
     * so the public {@link #blockContact(Jid)} and
     * {@link #unblockContact(Jid)} entry points can stay {@code void}.
     *
     * @param block   {@code true} to block the contact, {@code false} to unblock
     * @param contact the target contact JID; never {@code null}
     * @throws NullPointerException           if {@code contact} is {@code null}
     * @throws WhatsAppServerRuntimeException if the relay rejected the request
     */
    @WhatsAppWebExport(moduleName = "WASmaxBlocklistsUpdateBlockListRPC",
            exports = "sendUpdateBlockListRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    private void updateBlockList(boolean block, Jid contact) {
        Objects.requireNonNull(contact, "contact cannot be null");
        // WASmaxOutBlocklistsUpdateBlockListRequest.makeUpdateBlockListRequest
        var action = block ? SmaxUpdateBlockListAction.BLOCK : SmaxUpdateBlockListAction.UNBLOCK;
        var request = new SmaxUpdateBlockListRequest(action, contact.toUserJid(), null, null);
        var requestNode = request.toNode();
        // WAComms.sendSmaxStanza
        var response = sendNode(requestNode);
        // Variant priority chain: see SmaxUpdateBlockListResponse.of
        var parsed = SmaxUpdateBlockListResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> { /* no-parse: success-without-recognised-shape, treat as no-op success */ }
            case SmaxUpdateBlockListResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Block-list update rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case SmaxUpdateBlockListResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Block-list update server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
            // Success variants: nothing to do
            case SmaxUpdateBlockListResponse.SuccessWithMatch _ -> { }
            case SmaxUpdateBlockListResponse.SuccessWithMismatch _ -> { }
            case SmaxUpdateBlockListResponse.MigratedSuccessWithMismatch _ -> { }
            case SmaxUpdateBlockListResponse.CAPISuccessWithMismatch _ -> { }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBlocklistsGetOptOutListRPC",
            exports = "sendGetOptOutListRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void refreshOptOutList(String category) {
        Objects.requireNonNull(category, "category cannot be null");
        var cachedHash = store.optOutListHash(category).orElse(null);
        var request = new SmaxGetOptOutListRequest(cachedHash, category);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGetOptOutListResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> { /* no-parse: leave cache untouched */ }
            case SmaxGetOptOutListResponse.SuccessWithMatch _ -> { /* cache hit */ }
            case SmaxGetOptOutListResponse.SuccessWithMismatch v -> {
                var entries = toOptOutEntries(v.listItem());
                store.setOptOutList(category, v.listDhash().orElse(null), entries);
                for (var listener : store.listeners()) {
                    listener.onOptOutList(this, category, entries);
                }
            }
            case SmaxGetOptOutListResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Opt-out-list query rejected: code="
                            + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGetOptOutListResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Opt-out-list query server error: code="
                            + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
    }

    /**
     * Projects the wire-level
     * {@link SmaxGetOptOutListResponse.Item} list into the
     * caller-friendly {@link OptOutEntry} list. Each
     * {@link BizOptOutId} disjunction value is mapped to the matching
     * {@link OptOutTarget} arm; the
     * {@link SmaxGetOptOutListResponse.Item#expiryAt() expiry} second
     * timestamp is widened to an {@link Instant}.
     *
     * @param items the wire-level item list; never {@code null}
     * @return an immutable list of opt-out entries; never {@code null}
     */
    private static List<OptOutEntry> toOptOutEntries(List<SmaxGetOptOutListResponse.Item> items) {
        return items.stream()
                .map(DefaultWhatsAppClient::toOptOutEntry)
                .toList();
    }

    /**
     * Maps a single wire-level
     * {@link SmaxGetOptOutListResponse.Item} into the caller-friendly
     * {@link OptOutEntry} model.
     *
     * @param item the wire-level item; never {@code null}
     * @return the projected {@link OptOutEntry}; never {@code null}
     */
    private static OptOutEntry toOptOutEntry(SmaxGetOptOutListResponse.Item item) {
        var target = toOptOutTarget(item.bizOptOutIds());
        var expiry = item.expiryAtAsOptional()
                .map(Instant::ofEpochSecond)
                .orElse(null);
        return new OptOutEntry(item.action(), item.category(), expiry, target);
    }

    /**
     * Maps a wire-level {@link BizOptOutId} disjunction value into the
     * caller-friendly {@link OptOutTarget} arm.
     *
     * @param id the wire-level disjunction value; never {@code null}
     * @return the projected {@link OptOutTarget}; never {@code null}
     */
    private static OptOutTarget toOptOutTarget(BizOptOutId id) {
        return switch (id) {
            case BizOptOutId.UserJid userJid -> new OptOutTarget.User(userJid.bizOptOutJid());
            case BizOptOutId.BrandId brand -> new OptOutTarget.Brand(brand.bizOptOutBrandId(), brand.bizJid());
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxPrivacyGetContactBlacklistRPC",
            exports = "sendGetContactBlacklistRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void refreshContactBlacklist(String category, ContactBlacklistAddressingMode addressingMode) {
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(addressingMode, "addressingMode cannot be null");
        var wireMode = switch (addressingMode) {
            case PN -> SmaxGetContactBlacklistAddressingMode.PN;
            case LID -> SmaxGetContactBlacklistAddressingMode.LID;
        };
        var request = new SmaxGetContactBlacklistRequest(category, wireMode);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGetContactBlacklistResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> { /* no-parse: leave cache untouched */ }
            case SmaxGetContactBlacklistResponse.Success success -> {
                var dhash = success.listDhash().orElse(null);
                if (dhash == null) {
                    return;
                }
                var jids = success.users().stream()
                        .map(SmaxGetContactBlacklistResponse.PnUser::jid)
                        .toList();
                store.setContactBlacklist(category, dhash, jids);
                for (var listener : store.listeners()) {
                    listener.onContactBlacklist(this, category, jids);
                }
            }
            case SmaxGetContactBlacklistResponse.SuccessLID successLid -> {
                var dhash = successLid.listDhash().orElse(null);
                if (dhash == null) {
                    return;
                }
                var jids = successLid.users().stream()
                        .map(SmaxGetContactBlacklistResponse.LidUser::jid)
                        .flatMap(Optional::stream)
                        .toList();
                store.setContactBlacklist(category, dhash, jids);
                for (var listener : store.listeners()) {
                    listener.onContactBlacklist(this, category, jids);
                }
            }
            case SmaxGetContactBlacklistResponse.Error error ->
                    throw new WhatsAppServerRuntimeException("Contact-blacklist query error: code="
                            + error.errorCode() + ", text=" + error.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBlocklistsUpdateOptOutListRPC",
            exports = "sendUpdateOptOutListRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void updateOptOutList(OptOutListUpdate update) {
        Objects.requireNonNull(update, "update cannot be null");
        var itemJid = update.itemJid();
        var itemCategory = update.itemCategory();
        var itemAction = update.itemAction();
        var itemDhash = update.itemDhash().orElse(null);
        var itemReason = update.itemReason().orElse(null);
        var itemEntryPoint = update.itemEntryPoint().orElse(null);
        var itemSignupId = update.itemSignupId().orElse(null);
        var itemDuration = update.itemDuration().isPresent() ? update.itemDuration().getAsInt() : null;
        var request = new SmaxUpdateOptOutListRequest(itemJid, itemCategory, itemAction, itemDhash,
                itemReason, itemEntryPoint, itemSignupId, itemDuration);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxUpdateOptOutListResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> { /* no-parse: success-without-recognised-shape, treat as no-op success */ }
            case SmaxUpdateOptOutListResponse.SuccessWithMatch _ -> { }
            case SmaxUpdateOptOutListResponse.SuccessWithMismatch _ -> { }
            case SmaxUpdateOptOutListResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Opt-out-list update rejected: code="
                            + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxUpdateOptOutListResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Opt-out-list update server error: code="
                            + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
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
     *       API does not accept an entry-point argument; this matches WA
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
     * @param contact the contact that was just blocked or unblocked
     * @param action  the action performed: {@link BlockEventActionType#BLOCK}
     *                or {@link BlockEventActionType#UNBLOCK}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamBlockEventReporter", exports = "logBlockEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private void logBlockEvent(Jid contact, BlockEventActionType action) {
        // WAWebFrontendContactGetters.getIsMyContact(n): contact is considered "my contact" when saved in the address book
        var contactRecord = store.findContactByJid(contact).orElse(null);
        var isMyContact = contactRecord != null && contactRecord.fullName().isPresent();
        // WAWebChatModel.isTrusted for 1:1 user chat: notSpam || getIsMyContact(contact) || hasMaybeSentMsgToChat()
        var chatIsTrusted = store.findChatByJid(contact)
                .map(chat -> chat.notSpam() || isMyContact)
                .orElse(false);
        var isSuspicious = !(isMyContact || chatIsTrusted);
        wamService.commit(new BlockEventsFsEventBuilder()
                .blockEntryPoint(BlockEntryPoint.OTHER)
                .blockEventActionType(action)
                .blockEventIsSuspicious(isSuspicious)
                .blockEventIsUnsub(!isMyContact)
                .build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactProfilePicThumbBridge", exports = "requestProfilePicFromServer",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGetProfilePicJob", exports = "getProfilePic",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<URI> queryPicture(JidProvider jidProvider) {
        var jid = Objects.requireNonNull(jidProvider, "jid cannot be null").toJid();
        var request = new SmaxProfilePictureGetRequest(jid, "image", null, "url",
                null, null, null, null, null, null);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        var parsed = SmaxProfilePictureGetResponse.of(response, requestBuilder.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxProfilePictureGetResponse.SuccessPictureURL ok -> Optional.of(URI.create(ok.pictureUrl()));
            // 404 / no-data branches map to "no picture available", matching WA Web's HTTP 404 path.
            case SmaxProfilePictureGetResponse.SuccessNoData _ -> Optional.empty();
            // Avatar / inline-blob success variants don't carry a CDN URL.
            case SmaxProfilePictureGetResponse.SuccessAvatarURLs _ -> Optional.empty();
            case SmaxProfilePictureGetResponse.SuccessPictureBlob _ -> Optional.empty();
            case SmaxProfilePictureGetResponse.Error error when error.errorCode() == 404
                    || error.errorCode() == 401 -> Optional.empty();
            case SmaxProfilePictureGetResponse.Error error ->
                    throw new WhatsAppServerRuntimeException("Profile picture get rejected: code=" + error.errorCode() + ", text=" + error.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetPushnameConnAction", exports = "setPushName",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editName(String newPushName) {
        Objects.requireNonNull(newPushName, "newPushName cannot be null");
        var mutation = pushNameSettingMutationFactory.getPushnameMutation(Instant.now(), newPushName);
        webAppStateService.pushPatches(PushNameSetting.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync
        // WASendPresenceStatusProtocol.sendPresenceStatusProtocol: smax("presence", {type: undefined, name: _})
        var request = new SmaxAvailabilityRequest(null, newPushName);
        sendNodeWithNoResponse(request.toNode().build()); // WAComms.castSmaxStanza - fire-and-forget
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactStatusBridge", exports = "setMyStatus",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editAbout(String aboutText) {
        Objects.requireNonNull(aboutText, "aboutText cannot be null");
        var request = new IqSetAboutRequest(aboutText);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        var parsed = IqSetAboutResponse.of(response, requestBuilder.build()).orElse(null);
        switch (parsed) {
            case IqSetAboutResponse.Success _ -> { // ADAPTED: parser couldn't classify is treated as success-like, mirroring WA Web's cached Conn.about via Cobalt's single store field
                var status = new ContactTextStatusBuilder()
                        .text(aboutText)
                        .build();
                store.setSelfTextStatus(status);
            }
            case IqSetAboutResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Set about rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case IqSetAboutResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Set about server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetProfilePicJob", exports = "setMyPic",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebContactProfilePicThumbBridge", exports = "sendSetPicture",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editProfilePicture(byte[] jpegBytes) {
        Objects.requireNonNull(jpegBytes, "jpegBytes cannot be null");
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMeUser
                .map(Jid::withoutData)
                .orElseThrow(() -> new IllegalStateException("Missing self JID"));
        var thumbBytes = generatePreviewThumbnail(jpegBytes);
        var fullPicture = new NodeBuilder()
                .description("picture")
                .attribute("type", "image")
                .content(jpegBytes)
                .build();
        var previewPicture = new NodeBuilder()
                .description("picture")
                .attribute("type", "preview")
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebRemoveProfilePicJob", exports = "removeMyPic",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebContactProfilePicThumbBridge", exports = "requestDeletePicture",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void removeProfilePicture() {
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMeUser
                .map(Jid::withoutData)
                .orElseThrow(() -> new IllegalStateException("Missing self JID"));
        var request = new IqSendProfilePictureRequest(selfJid, null);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        var parsed = IqSendProfilePictureResponse.of(response, requestBuilder.build()).orElse(null);
        switch (parsed) {
            case null -> {
                // unparseable result; treat as success-like (matches the pre-typed-flow behaviour)
            }
            case IqSendProfilePictureResponse.Success _ -> {
                // success: picture cleared
            }
            case IqSendProfilePictureResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Remove profile picture rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case IqSendProfilePictureResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Remove profile picture server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
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
            var src = ImageIO.read(new ByteArrayInputStream(jpegBytes));
            if (src == null) {
                throw new IllegalArgumentException("Invalid image payload");
            }
            var thumb = new BufferedImage(PROFILE_PREVIEW_SIZE, PROFILE_PREVIEW_SIZE, BufferedImage.TYPE_INT_RGB); // WA Web's profile preview size
            var g = thumb.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, PROFILE_PREVIEW_SIZE, PROFILE_PREVIEW_SIZE, null);
            } finally {
                g.dispose();
            }
            var out = new ByteArrayOutputStream();
            ImageIO.write(thumb, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to decode profile picture", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void editPresence(ContactStatus status) {
        var name = store.name();
        var presenceName = (name != null && !name.isEmpty()) ? name : null;
        editPresence(status, presenceName);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactPresenceBridge", exports = "setPresenceAvailable",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebContactPresenceBridge", exports = "setPresenceUnavailable",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASendPresenceStatusProtocol",
            exports = "sendPresenceStatusProtocol", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxPresenceAvailabilityRPC",
            exports = "sendAvailabilityRPC", adaptation = WhatsAppAdaptation.DIRECT)
    public void editPresence(ContactStatus status, String presenceName) {
        Objects.requireNonNull(status, "status cannot be null");
        if (status != ContactStatus.AVAILABLE && status != ContactStatus.UNAVAILABLE) {
            throw new IllegalArgumentException("status must be AVAILABLE or UNAVAILABLE, got " + status);
        }
        var request = new SmaxAvailabilityRequest(status.toString(), presenceName);
        sendNodeWithNoResponse(request.toNode().build()); // WAComms.castSmaxStanza - fire-and-forget
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatStateBridge", exports = "sendChatStateComposing",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatStateBridge", exports = "sendChatStateRecording",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatStateBridge", exports = "sendChatStatePaused",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASendChatStateProtocol", exports = "sendChatStateProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxChatstateClientNotificationRPC", exports = "sendClientNotificationRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editChatState(JidProvider chatProvider, ContactStatus state) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        Objects.requireNonNull(state, "state cannot be null");
        // WASendChatStateProtocol.sendChatStateProtocol: branches on "idle" / "typing" / "recording_audio"
        var stateType = switch (state) {
            // WASmaxOutChatstateComposingMixin.mergeComposingMixin: smax("composing", {media: OPTIONAL_LITERAL("audio", false)})
            case COMPOSING -> new SmaxClientNotificationComposing(false);
            // WASmaxOutChatstateComposingMixin: media: OPTIONAL_LITERAL("audio", hasComposingMediaAudio)
            case RECORDING -> new SmaxClientNotificationComposing(true);
            // WASmaxOutChatstatePausedMixin.mergePausedMixin: smax("paused", null)
            case UNAVAILABLE -> new SmaxClientNotificationPaused();
            default -> throw new IllegalArgumentException("state must be COMPOSING, RECORDING or UNAVAILABLE, got " + state);
        };
        var request = new SmaxClientNotificationRequest(chat, stateType);
        sendNodeWithNoResponse(request.toNode().build()); // WAComms.castSmaxStanza - fire-and-forget
    }

    /** {@inheritDoc} */
    @Override
    public void subscribeToPresence(JidProvider targetProvider) {
        var target = Objects.requireNonNull(targetProvider, "target cannot be null").toJid();
        subscribeToPresence(target, null, null);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactPresenceBridge", exports = "subscribePresence",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxPresenceSubscribeRPC",
            exports = "castSubscribeRPC", adaptation = WhatsAppAdaptation.DIRECT)
    public void subscribeToPresence(JidProvider presenceToProvider, String presenceName, JidProvider presenceContextProvider) {
        var presenceTo = Objects.requireNonNull(presenceToProvider, "presenceTo cannot be null").toJid();
        var presenceContext = Objects.requireNonNull(presenceContextProvider, "presenceContext cannot be null").toJid();
        var request = new SmaxSubscribeRequest(presenceTo, presenceName, presenceContext);
        sendNodeWithNoResponse(request.toNode().build());
    }

    /** {@inheritDoc} */
    @Override
    public void unsubscribeFromPresence(JidProvider targetProvider) {
        var target = Objects.requireNonNull(targetProvider, "target cannot be null").toJid();
        var presence = new NodeBuilder()
                .description("presence") // ADAPTED: mirrors subscribePresence stanza with type=unsubscribe
                .attribute("type", "unsubscribe")
                .attribute("to", target)
                .build();
        sendNodeWithNoResponse(presence);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendMessage(JidProvider jidProvider, MessageContainer container) {
        var jid = Objects.requireNonNull(jidProvider, "jid cannot be null").toJid();
        Objects.requireNonNull(container, "container cannot be null");
        messageService.send(jid, container); // WAWebSendMsgJob.encryptAndSendMsg
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendMessage(MessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo cannot be null");
        messageService.send(messageInfo); // WAWebSendMsgJob.encryptAndSendMsg
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendMessageEditAction", exports = "sendMessageEdit",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendMessageEditAction", exports = "createEditMsgData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendMessageEditAction", exports = "addAndSendMessageEdit",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editMessage(MessageKey originalKey, MessageContainer newContent) {
        Objects.requireNonNull(originalKey, "originalKey cannot be null");
        Objects.requireNonNull(newContent, "newContent cannot be null");
        var parentJid = originalKey.parentJid()
        .orElseThrow(() -> new IllegalArgumentException("originalKey must carry a parentJid"));
        var protocol = new ProtocolMessageBuilder()
        .key(originalKey)
        .type(ProtocolMessage.Type.MESSAGE_EDIT)
        .editedMessageContainer(newContent)
        .timestampMs(Instant.now())
        .build();
        var wrapper = MessageContainer.of(protocol);
        messageService.send(parentJid, wrapper); // WAWebSendMessageEditAction.sendMsgEditRecord -> WAWebSendMsgRecordAction.sendMsgRecord
    }

    /** {@inheritDoc} */
    @Override
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
    public void deleteMessage(MessageKey key, boolean everyone) {
        Objects.requireNonNull(key, "key cannot be null");
        if (everyone) {
            var parentJid = key.parentJid()
            .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
            // original messages before the revoke send mutates local state.
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
                    : wamService.getWamMediaType(originalInfo);
            var sendInstant = Instant.now();
            var revokeSendDelaySeconds = originalInfo == null
                    ? null
                    : originalInfo.timestamp()
                            .map(t -> (int) (sendInstant.getEpochSecond() - t.getEpochSecond()))
                            .orElse(null);
            var protocol = new ProtocolMessageBuilder()
            .key(key)
            .type(ProtocolMessage.Type.REVOKE)
            .timestampMs(sendInstant)
            .build();
            var wrapper = MessageContainer.of(protocol); // WAWebRevokeMsgAction._sendRevoke -> WAWebSendMsgRecordAction.sendMsgRecord
            var ack = messageService.send(parentJid, wrapper); // WAWebRevokeMsgAction._sendRevoke -> sendMsgRecord
            // WAWebActionListener._e.then(...): logMessageDeleteActionsMetric(t, a, true) fires after the revoke send completes successfully.
            emitMessageDeleteActionsEvent(parentJid, DeleteActionType.DELETE_FOR_EVERYONE, mediaType);
            // `new SendRevokeMessageWamEvent({messageType, messageMediaType, revokeSendDelay}).commit()`.
            if (ack != null && ack.isSuccess()) {
                emitSendRevokeMessageEvent(parentJid, mediaType, revokeSendDelaySeconds);
            }
            return;
        }

        var parentJid = key.parentJid()
        .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
        var messageId = key.id()
        .orElseThrow(() -> new IllegalArgumentException("key must carry an id"));

        // resolved before the in-memory message is removed, otherwise getWamMediaType cannot classify it.
        var mediaType = store.findMessageById(parentJid, messageId)
                .filter(ChatMessageInfo.class::isInstance)
                .map(ChatMessageInfo.class::cast)
                .map(wamService::getWamMediaType)
                .orElse(null);

        store.findChatByJid(parentJid)
                .ifPresent(chat -> chat.removeMessage(messageId));
        // emits a DeleteMessageForMe sync action to every linked device.
        // [remote, id, fromMe, participant] tuple with `{legacy:true}` JID
        // serialization and the !remote.isUser() && !fromMe participant gate.
        var keySegments = SyncdIndexUtils.constructMsgKeySegmentsFromMsgKey(key);
        var indexJson = SyncdIndexUtils.buildIndex( // WAWebSyncdActionUtils.buildIndex
                DeleteMessageForMeAction.ACTION_NAME, // "deleteMessageForMe"
                keySegments.get(0),
                keySegments.get(1),
                keySegments.get(2),
                keySegments.get(3)        );
        var deleteAction = new DeleteMessageForMeActionBuilder()
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
                DeleteMessageForMeAction.ACTION_VERSION
        );
        webAppStateService.pushPatches(
                DeleteMessageForMeAction.COLLECTION_NAME,
                List.of(new SyncPendingMutation(mutation, 0))
        );
        // WAWebActionListenerHelpers: if(getIsPSA(e)) for(r=0;r<t.list.length;r++) WAWebWamChatPSALogger.logChatPSADelete(t.list[r])
        logPsaActionIfApplicable(key, PsaMessageActionType.DELETE); // WAWebWamChatPSALogger.logChatPSADelete -> PSA_MESSAGE_ACTION_TYPE.DELETE
        // WAWebActionListener.pe.then(...): logMessageDeleteActionsMetric(a, i, false) fires after the delete-for-me
        // send-result count equals the input count (full success).
        emitMessageDeleteActionsEvent(parentJid, DeleteActionType.DELETE_FOR_ME, mediaType);
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
     */
    @WhatsAppWebExport(moduleName = "WAWebActionListenerHelpers", exports = "logMessageDeleteActionsMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitMessageDeleteActionsEvent(Jid chatJid, DeleteActionType deleteActionType, MediaType mediaType) {
        var builder = new MessageDeleteActionsEventBuilder()
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
     */
    @WhatsAppWebExport(moduleName = "WAWebRevokeMsgAction", exports = "_sendRevoke",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitSendRevokeMessageEvent(Jid chatJid, MediaType mediaType, Integer revokeSendDelaySeconds) {
        var builder = new SendRevokeMessageEventBuilder()
        .messageType(wamService.getWamMessageType(chatJid));
        if (mediaType != null) {
            builder.messageMediaType(mediaType);
            }
        if (revokeSendDelaySeconds != null) {
            builder.revokeSendDelay(revokeSendDelaySeconds);
            }
        wamService.commit(builder.build());
        }

    /** {@inheritDoc} */
    @Override
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
                .id(messageId)
                .parentJid(statusJid)
                .fromMe(true)
                .senderJid(selfJid)
                .build();
        var messageInfo = new ChatMessageInfoBuilder()
                .status(MessageStatus.PENDING) // ADAPTED: mirrors MessagePreparer.prepareChat initial status
                .senderJid(selfJid)
                .key(key)
                .message(content)
                .timestamp(Instant.now())
                .broadcast(true) // WAWebSendStatusMsgAction: status is a broadcast JID
                .build();
        // The logger's constructor seeds a per-operation random sessionId that it
        // re-uses across the REQUEST / SUCCESS / FAILURE emissions for this send.
        var statusPostingSessionId = newStatusPostingSessionId(); // WAWebLogStatusPosterActions: this.sessionId = Math.floor(Math.random() * Number.MAX_SAFE_INTEGER)
        var statusContentType = resolveStatusContentType(content);
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
     */
    @WhatsAppWebExport(moduleName = "WAWebSendStatusMsgAction", exports = "y",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private StatusContentType resolveStatusContentType(MessageContainer content) {
        return switch (content.content()) {
            case ExtendedTextMessage _ -> StatusContentType.TEXT; // "chat" -> TEXT
            case ImageMessage _ -> StatusContentType.PHOTO; // "image" -> PHOTO
            case VideoMessage video ->
                    video.gifPlayback() ? StatusContentType.GIF : StatusContentType.VIDEO; // "gif"/"video"
            case StickerMessage _ -> StatusContentType.GIF; // "sticker" -> GIF
            case AudioMessage _ -> StatusContentType.VOICE; // "ptt"/"audio" -> VOICE
            default -> StatusContentType.PHOTO;
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
     */
    @WhatsAppWebExport(moduleName = "WAWebLogStatusPosterActions", exports = "StatusPosterActionsLogger",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private int newStatusPostingSessionId() {
        // nextInt(Integer.MAX_VALUE) yields a non-negative int. WA Web's 53-bit seed
        // is stored in a WamType.INTEGER slot, so a 31-bit value is sufficient and
        // matches the builder's Integer parameter type.
        return ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebRevokeStatusAction", exports = "sendStatusRevokeMsgAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebRevokeStatusAction", exports = "createRevokeStatusMsgData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteStatus(String statusId) {
        Objects.requireNonNull(statusId, "statusId cannot be null");
        var statusJid = Jid.statusBroadcastAccount(); // WAJids.STATUS_JID
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMeUser
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        var originalKey = new MessageKeyBuilder()
                .id(statusId)
                .parentJid(statusJid)
                .fromMe(true)
                .senderJid(selfJid)
                .build();
        var protocol = new ProtocolMessageBuilder()
        .key(originalKey)
        .type(ProtocolMessage.Type.REVOKE)
        .timestampMs(Instant.now())
        .build();
        var wrapper = MessageContainer.of(protocol);
        // h.logDeleteStatusRequest() before the dispatch, then logDeleteStatusSuccess() or
        // logDeleteStatusFailure(L.message) depending on the outcome; all three emissions
        // share the logger's single sessionId.
        var statusPostingSessionId = newStatusPostingSessionId(); // WAWebLogStatusPosterActions: this.sessionId
        wamService.commit(new StatusPosterActionsEventBuilder()
                .statusEventType(StatusEventType.DELETE_STATUS_REQUEST)
                .statusPostingSessionId(statusPostingSessionId)
                .build());
        try {
            messageService.send(statusJid, wrapper); // WAWebRevokeStatusAction.sendStatusRevokeMsgAction -> WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg
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
        wamService.commit(new StatusPosterActionsEventBuilder()
                .statusEventType(StatusEventType.DELETE_STATUS_SUCCESS)
                .statusPostingSessionId(statusPostingSessionId)
                .build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebHandleStatusReceipt", exports = "sendStatusMsgRead",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void markStatusViewed(String statusId) {
        Objects.requireNonNull(statusId, "statusId cannot be null");
        var statusAuthor = store.findStatusById(statusId)
                .orElseThrow(() -> new NoSuchElementException("No status with id " + statusId))
                .key()
                .senderJid()
                .orElseThrow(() -> new NoSuchElementException("Status " + statusId + " has no sender JID"));
        var me = store.jid().orElse(null); // WAWebUserPrefsMeUser.getMeUser
        if (me == null) {
            return; // ADAPTED: silently drop when unauthenticated, matching sendReceipt
        }
        var receipt = new NodeBuilder()
        .description("receipt")
                .attribute("id", statusId)
                .attribute("type", "read")
                .attribute("to", Jid.statusBroadcastAccount())
                .attribute("participant", statusAuthor)
                .build();
        sendNodeWithNoResponse(receipt);
        }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsStatus", exports = "getStatusPrivacySetting",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void refreshStatusPrivacy() {
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
        var setting = new StatusPrivacySettingBuilder()
                .mode(mode)
                .jids(jids)
                .build();
        store.setStatusPrivacy(setting);
        for (var listener : store.listeners()) {
            listener.onStatusPrivacyChanged(this, setting);
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStatusSetAndSyncPrivacy", exports = "setAndSyncStatusPrivacy",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editStatusPrivacy(StatusPrivacyMode mode, Collection<? extends JidProvider> jidsProvider) {
        var jids = Objects.requireNonNull(jidsProvider, "jids cannot be null").stream().map(JidProvider::toJid).toList();
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
        var mutation = statusPrivacyMutationFactory.getStatusPrivacyMutation(Instant.now(), protoMode, jidList); // WAWebStatusPrivacySettingSync.getStatusPrivacySettingMutation
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatForwardMessage", exports = "forwardMessages",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamChatPSALogger", exports = "logChatPSAForward",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void forwardMessage(MessageKey sourceKey, JidProvider destinationProvider) {
        var destination = Objects.requireNonNull(destinationProvider, "destination cannot be null").toJid();
        Objects.requireNonNull(sourceKey, "sourceKey cannot be null");
        var parentJid = sourceKey.parentJid()
        .orElseThrow(() -> new IllegalArgumentException("sourceKey must carry a parentJid"));
        var messageId = sourceKey.id()
        .orElseThrow(() -> new IllegalArgumentException("sourceKey must carry an id"));
        var source = store.findMessageById(parentJid, messageId) // WAWebMsgCollection.MsgCollection.get(e.id)
                .orElseThrow(() -> new IllegalArgumentException("Source message not found in local store: " + messageId));
        var container = source.message();
        logPsaActionIfApplicable(source, PsaMessageActionType.FORWARD); // WAWebWamChatPSALogger.logChatPSAForward -> PSA_MESSAGE_ACTION_TYPE.FORWARD
        emitForwardSendEvent(source, destination, container);
        messageService.send(destination, container); // WAWebChatForwardMessage.forwardMessages -> sendMsgRecord
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebForwardMessagesToChat", exports = "forwardMessagesToChats",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatForwardMessage", exports = "forwardMessages",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatForwardMessage", exports = "getForwardedMessageFields",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamChatPSALogger", exports = "logChatPSAForward",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void forwardMessages(Collection<MessageKey> sourceKeys, Collection<? extends JidProvider> destinationsProvider) {
        var destinations = Objects.requireNonNull(destinationsProvider, "destinations cannot be null").stream().map(JidProvider::toJid).toList();
        Objects.requireNonNull(sourceKeys, "sourceKeys cannot be null");
        var resolvedSources = new ArrayList<MessageInfo>();
        for (var sourceKey : sourceKeys) {
            sourceKey.parentJid()
            .flatMap(parent -> sourceKey.id()
                            .flatMap(id -> store.findMessageById(parent, id)))
                    .ifPresent(resolvedSources::add);
        }
        for (var source : resolvedSources) {
            logPsaActionIfApplicable(source, PsaMessageActionType.FORWARD); // WAWebWamChatPSALogger.logChatPSAForward -> PSA_MESSAGE_ACTION_TYPE.FORWARD
        }
        for (var destination : destinations) {
            for (var source : resolvedSources) {
                var container = source.message();
                emitForwardSendEvent(source, destination, container);
                messageService.send(destination, container); // WAWebChatForwardMessage.forwardMessages -> sendMsgRecord
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebReactionsMsgAction", exports = "addOrUpdateReactions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebReactionsMsgAction", exports = "resendUpdateFailedPropsForSentReactionsDBAndModel",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void addReaction(MessageKey messageKey, String emoji) {
        Objects.requireNonNull(messageKey, "messageKey cannot be null");
        Objects.requireNonNull(emoji, "emoji cannot be null");
        var parentJid = messageKey.parentJid()
        .orElseThrow(() -> new IllegalArgumentException("messageKey must carry a parentJid"));
        var reaction = new ReactionMessageBuilder()
        .key(messageKey)
        .text(emoji)
        .senderTimestampMs(Instant.now())
        .build();
        // The preparer auto converts to EncReactionMessage for CAG groups.
        messageService.send(parentJid, MessageContainer.of(reaction)); // WAWebReactionsMsgAction -> sendMsgRecord
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebReactionsMsgAction", exports = "addOrUpdateReactions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void removeReaction(MessageKey messageKey) {
        Objects.requireNonNull(messageKey, "messageKey cannot be null");
        addReaction(messageKey, "");
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "getStarMessageMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamChatPSALogger", exports = "logChatPSAStar",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void starMessage(MessageKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        pushStarMutation(key, true);
        // WAWebActionListenerHelpers: if(getIsPSA(e)) for (var m=0; m<c; m++) WAWebWamChatPSALogger.logChatPSAStar(t[m])
        logPsaActionIfApplicable(key, PsaMessageActionType.SAVE); // WAWebWamChatPSALogger.logChatPSAStar -> PSA_MESSAGE_ACTION_TYPE.SAVE
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "getStarMessageMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unstarMessage(MessageKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        pushStarMutation(key, false);
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
     */
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "getStarMessageMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void pushStarMutation(MessageKey key, boolean starred) {
        var parentJid = key.parentJid()
        .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
        var messageId = key.id()
        .orElseThrow(() -> new IllegalArgumentException("key must carry an id"));

        // JID serializer and the !remote.isUser() && !fromMe participant gate.
        var keySegments = SyncdIndexUtils.constructMsgKeySegmentsFromMsgKey(key);

        var action = new StarActionBuilder()
                .starred(starred)
                .build();
        var value = new SyncActionValueBuilder()
        .timestamp(Instant.now())
        .starAction(action)
                .build();
        var indexJson = SyncdIndexUtils.buildIndex( // WAWebSyncdActionUtils.buildIndex
                StarAction.ACTION_NAME,
                keySegments.get(0),
                keySegments.get(1),
                keySegments.get(2),
                keySegments.get(3)  // WAWebSyncdUtils.extractParticipantForSync
        );
        var mutation = new DecryptedMutation.Trusted(
                indexJson,
                value,
                SyncdOperation.SET,                Instant.now(),
                StarAction.ACTION_VERSION        );
        webAppStateService.pushPatches(
                StarAction.COLLECTION_NAME,                List.of(new SyncPendingMutation(mutation, 0))
        );

        store.findMessageById(parentJid, messageId).ifPresent(info -> {
            switch (info) {
                case ChatMessageInfo c -> c.setStarred(starred);
                case NewsletterMessageInfo n -> n.setStarred(starred);
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
     */
    @WhatsAppWebExport(moduleName = "WAWebChatGetters", exports = "getIsPSA",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void logPsaActionIfApplicable(MessageKey key, PsaMessageActionType actionType) {
        var parentJid = key.parentJid().orElse(null);
        if (parentJid == null || !parentJid.equals(Jid.announcementsAccount())) {
            return;
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
     * {@link ChatMessageInfo} instances are silently _.
     *
     * @param info       the resolved message info whose PSA affiliation
     *                   is being tested; must not be {@code null}
     * @param actionType the PSA action that was performed on the message
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
        if (!(info instanceof ChatMessageInfo chatInfo)) {
            return;
        }
        var parentJid = chatInfo.key().parentJid().orElse(null);
        if (parentJid == null || !parentJid.equals(Jid.announcementsAccount())) {
            return;
        }
        var messageIdOpt = chatInfo.key().id();
        if (messageIdOpt.isEmpty()) {
            return;
        }
        // WAWebWamChatPSALogger: psaCampaignId: (t = e.campaignId) == null ? void 0 : t.toString()
        var campaignId = chatInfo.statusPsa()
                .map(StatusPSA::campaignId)
                .map(String::valueOf)
                .orElse(null);
        wamService.commit(new ChatPsaActionEventBuilder() // WAWebWamChatPSALogger: new ChatPsaActionWamEvent({...}).commit()
                .messageMediaType(wamService.getWamMediaType(chatInfo)) // WAWebWamMsgUtils.getWamMediaType(e)
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
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgUtilsBridge", exports = "createMessageForwardMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendMsgRecordAction", exports = "sendMsgRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitForwardSendEvent(MessageInfo source, Jid destination, MessageContainer forwarded) {
        var mediaCaptionPresent = hasMediaCaption(forwarded);
        var numTimesForwarded = numTimesForwarded(source);
        // WAWebConstantsDeprecated.FREQUENTLY_FORWARDED_SENTINEL = 127
        var isFrequentlyForwarded = numTimesForwarded >= 127;
        var isForwardedForward = numTimesForwarded > 1;
        var builder = new ForwardSendEventBuilder()
                .messageType(wamService.getWamMessageType(destination))
                .messageMediaType(wamService.getWamMediaType(forwarded))
                .mediaCaptionPresent(mediaCaptionPresent)
                .fastForwardEnabled(true)
                .messageIsFanout(true)
                .isFrequentlyForwarded(isFrequentlyForwarded)
                .isForwardedForward(isForwardedForward);
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
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgUtilsBridge", exports = "createMessageForwardMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean hasMediaCaption(MessageContainer container) {
        if (container == null) {
            return false;
        }
        return switch (container.content()) {
            case ImageMessage image -> image.caption().map(s -> !s.isEmpty()).orElse(false);
            case VideoMessage video -> video.caption().map(s -> !s.isEmpty()).orElse(false);
            case DocumentMessage document -> document.caption().map(s -> !s.isEmpty()).orElse(false);
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
        return ctx.isForwarded() ? 1 : 0;
    }
    //</editor-fold>

    //<editor-fold desc="Chat operations (archive, pin, mute, lock, labels, broadcast)">
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
     * @param chat the chat whose range is being built
     * @return the built message range, or {@code null} when the chat has no
     *         messages
     */
    private SyncActionMessageRange buildOutgoingMessageRange(Chat chat) {
        var newest = chat.newestMessage().orElse(null);
        if (newest == null) { // ADAPTED: no messages -> no meaningful range
            return null;
        }
        var timestamp = newest.timestamp().orElse(null);
        var builder = new SyncActionMessageRangeBuilder();
        if (timestamp != null) {
            builder.lastMessageTimestamp(timestamp);
        }
        return builder.build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetArchiveChatAction", exports = "setArchive",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void archiveChat(JidProvider chatProvider, boolean archive) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        var timestamp = Instant.now(); // WAWebSetArchiveChatAction.setArchive -> WAWebChatArchiveBridge.sendConversationArchive: var l = unixTimeMs()
        var chatModel = store.findChatByJid(chat).orElse(null);
        var messageRange = chatModel != null ? buildOutgoingMessageRange(chatModel) : null;
        var mutations = archiveChatMutationFactory.getMutationsForArchive(timestamp, archive, chat, messageRange); // WAWebArchiveChatSync.getMutationsForArchive
        webAppStateService.pushPatches(ArchiveChatAction.COLLECTION_NAME, mutations);
        if (chatModel != null) {
            chatModel.setArchived(archive);
        if (archive) {
            chatModel.setPinnedTimestamp(null);
        }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetPinChatAction", exports = "setPin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void pinChat(JidProvider chatProvider, boolean pin) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        var timestamp = Instant.now();
        wamService.commit(new MdSyncdDogfoodingFeatureUsageEventBuilder()
                .mdSyncdDogfoodingFeature(MdFeatureCode.PIN_MUTATION)
                .build());
        var mutations = PinChatHandler.getMutationsForPin(timestamp, pin, chat); // WAWebPinChatSync.getMutationsForPin
        webAppStateService.pushPatches(PinAction.COLLECTION_NAME, mutations);
        store.findChatByJid(chat).ifPresent(chatModel -> {
            chatModel.setPinnedTimestamp(pin ? timestamp : null);
        if (pin) {
            chatModel.setArchived(false);
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void muteChat(JidProvider chatProvider, Instant muteUntil) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        var muteEndSeconds = muteUntil == null ? 0L : muteUntil.getEpochSecond();
        var mutation = muteChatMutationFactory.generateMuteMutation(this, chat, muteEndSeconds, null); // WAWebMuteChatSync.generateMuteMutation
        webAppStateService.pushPatches(MuteAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdActionUtils.buildPendingMutation -> lockForSync
        store.findChatByJid(chat).ifPresent(chatModel ->                chatModel.setMute(ChatMute.mutedUntil(muteEndSeconds)));
        // WAWebActionListener (mute/unmute handler q):
        //   !getIsPSA(t) && !getIsNewsletter(t) && logChatMute(t, e, l) / logChatUnmute(t, g)
        // WAWebChatMuteLogger.logChatMute / logChatUnmute then builds a ChatMuteWamEvent with:
        //   actionConducted = MUTE on mute, UNMUTE on unmute
        //   muteChatType    = GROUP if getIsGroup(chat) else ONE_ON_ONE
        //   muteDuration    = n (seconds, -1 = indefinite); mute path only
        //   muteEntryPoint  = e (CHAT_LIST_SCREEN / CONTACT_INFO / CONVERSATION_SCREEN)
        //   muteGroupSize   = chat.groupMetadata.participants.length ?? 0; group path only
        // Newsletters in Cobalt use the dedicated #muteNewsletter(Jid, boolean) API and never reach this method.
        // PSA chats are not surfaced via Cobalt's public mute API (there is no WAWebWamChatPSALogger equivalent wired here).
        // muteEntryPoint is a UI-layer value (which screen the user tapped mute from); Cobalt has no UI so it is left unset.
        if (!chat.hasNewsletterServer()) { // WAWebActionListener: !getIsNewsletter(t) (getIsPSA has no Cobalt equivalent at this layer)
            var isGroup = chat.hasGroupOrCommunityServer(); // WAWebChatMuteLogger: var i = getIsGroup(e)
            var muteChatType = isGroup                    ? MuteChatType.GROUP
                    : MuteChatType.ONE_ON_ONE;
            var eventBuilder = new ChatMuteEventBuilder()
                    .muteChatType(muteChatType); // WAWebChatMuteLogger: muteChatType: u(e)
            if (muteEndSeconds == 0L) { // WAWebActionListener: unmute branch -> logChatUnmute(t, g)
                eventBuilder.actionConducted(ActionConducted.UNMUTE);
                } else { // WAWebActionListener: mute branch -> logChatMute(t, e, l)
                eventBuilder.actionConducted(ActionConducted.MUTE)
                .muteDuration(Instant.ofEpochSecond(muteEndSeconds));
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unmuteChat(JidProvider chatProvider) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        muteChat(chat, null);
        }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUpdateUnreadChatAction", exports = "sendSeen",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void markChatAsRead(JidProvider chatProvider) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        pushMarkChatAsReadMutation(chat, true);
        }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUpdateUnreadChatAction", exports = "markUnread",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void markChatAsUnread(JidProvider chatProvider) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        pushMarkChatAsReadMutation(chat, false);
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
        var messageRange = chatModel != null ? buildOutgoingMessageRange(chatModel) : null;
        var mutation = markChatAsReadMutationFactory.getMarkChatAsReadMutation(timestamp, read, chat, messageRange); // WAWebMarkChatAsReadSync.getMarkChatAsReadMutation
        webAppStateService.pushPatches(MarkChatAsReadAction.COLLECTION_NAME, List.of(mutation));
        if (chatModel != null) { // WAWebMarkChatAsReadSync.$MarkChatAsReadSync$p_1: backend updateChatReadStatus
            if (read) {
                chatModel.setMarkedAsUnread(false);
            chatModel.setUnreadCount(0);
            } else {
                chatModel.setMarkedAsUnread(true);
            chatModel.setUnreadCount(-1); // WAWebConstantsDeprecated.MARKED_AS_UNREAD = -1
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebClearChatSync", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void clearChat(JidProvider chatProvider, boolean keepStarred) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        var timestamp = Instant.now();
        var chatModel = store.findChatByJid(chat).orElse(null); // WAWebSyncdGetChat.resolveChatForMutationIndex (at apply time)
        var messageRange = chatModel != null ? buildOutgoingMessageRange(chatModel) : null;
        wamService.commit(new MdSyncdDogfoodingFeatureUsageEventBuilder()
                .mdSyncdDogfoodingFeature(!keepStarred
                        ? MdFeatureCode.CLEAR_CHAT_REMOVE_STARRED_MUTATION
                        : MdFeatureCode.CLEAR_CHAT_KEEP_STARRED_MUTATION)
                .build());
        var mutation = clearChatMutationFactory.getClearChatMutation(timestamp, chat, !keepStarred, false, messageRange); // WAWebClearChatSync.getClearChatMutation
        webAppStateService.pushPatches(ClearChatAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync -> push
        if (chatModel != null) {
            // TODO: per-message range deletion is not yet supported; Cobalt drops all messages on clearChat
            chatModel.removeMessages();
        }
        // WA Web defaults the entry point to CONVERSATION_LIST_BULK_EDIT when the caller omits it (r.entryPoint === void 0)
        wamService.commit(new ChatActionEventBuilder()
                .chatActionEntryPoint(ChatActionEntryPoint.CONVERSATION_LIST_BULK_EDIT)
                .chatActionType(ChatActionType.CLEAR)
                .build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteChatSync", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Chat> deleteChat(JidProvider chatProvider) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        var chatModel = store.findChatByJid(chat).orElse(null); // WAWebSyncdGetChat.resolveChatForMutationIndex (at apply time)
        if (chatModel == null) {
            return Optional.empty();
        }
        var timestamp = Instant.now();
        var messageRange = buildOutgoingMessageRange(chatModel);
        wamService.commit(new MdSyncdDogfoodingFeatureUsageEventBuilder()
                .mdSyncdDogfoodingFeature(MdFeatureCode.DELETE_MUTATION)
                .build());
        var mutation = deleteChatMutationFactory.getDeleteChatMutation(timestamp, chat, false, messageRange); // WAWebDeleteChatSync.getDeleteChatMutation
        webAppStateService.pushPatches(DeleteChatAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync -> push
        store.removeChat(chatModel);
        return Optional.of(chatModel);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatLockAction", exports = "setChatAsLocked",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void lockChat(JidProvider chatProvider) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        pushLockMutation(chat, true);
        }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatLockAction", exports = "setChatAsUnlocked",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unlockChat(JidProvider chatProvider) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        pushLockMutation(chat, false);
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
        var timestamp = Instant.now();
        var chatModel = store.findChatByJid(chat).orElse(null);
        var messageRange = chatModel != null ? buildOutgoingMessageRange(chatModel) : null;
        var mutations = lockChatMutationFactory.getMutationsForLock(timestamp, locked, chat, messageRange); // WAWebLockChatSync.sendLockMutation
        webAppStateService.pushPatches(LockChatAction.COLLECTION_NAME, mutations); // WAWebSyncdCoreApi.lockForSync([], mutations, ...)
        if (chatModel != null) {
            chatModel.setLocked(locked);
        if (locked) {
            chatModel.setArchived(false);
        chatModel.setPinnedTimestamp(null);
        }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBizLabelEditingAction", exports = "labelAddAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public String createLabel(String name, int colorIndex) {
        Objects.requireNonNull(name, "name cannot be null"); // ADAPTED: invariant guard; WA Web accepts any string
        var timestamp = Instant.now();
        var nextId = store.labels().stream()
                .map(Label::id)
                .mapToInt(id -> {
                    try { return Integer.parseInt(id); }
                    catch (NumberFormatException e) {
                        throw new IllegalStateException("getNextLabelId: Invalid label id " + id, e);
                    }
                })
                .max()
                .orElse(0) + 1;
        var labelId = String.valueOf(nextId);
        var predefinedLabelId = BusinessLabelConstants.mapLabelNameToPredefinedId(name)
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
        var mutation = labelEditMutationFactory.getLabelMutation(                labelId,
                name,
                colorIndex,
                false,                predefinedLabelId,                Boolean.TRUE,
                LabelEditAction.ListType.CUSTOM,                timestamp
        );
        webAppStateService.pushPatches(LabelEditAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync(["label"], [p], ...)
        var label = new LabelBuilder()
        .id(labelId)
                .name(name)
                .color(colorIndex)
                .predefinedId(predefinedLabelId)
                .isActive(Boolean.TRUE)
                .type(LabelEditAction.ListType.CUSTOM)
                .build();
        store.addLabel(label);
        // WAWebListsActions.createNewListAction -> WAWebListsLogging.logListUpdate (listAction: CREATE).
        // Cobalt collapses the outer WAWebListsActions.createNewListAction wrapper (which carries
        // the entryPoint and the set of chats being tagged) into this single createLabel method, so
        // updateEntryPoint / groupsAdded / usersAdded / groupsAfterUpdate / usersAfterUpdate are
        // omitted because the Cobalt API surface does not accept an entry point and does not tag
        // chats as part of label creation (matches WA Web's {chatsBeforeUpdate: [], addedChats: [],
        // removedChats: []} degenerate case where all counter fields are elided).
        wamService.commit(new ListUpdateEventBuilder()
                .listAction(ListAction.CREATE)
                .listId(nextId)
                .listType(ListType.CUSTOM) // WAWebListsLogging.logListUpdate -> getListType(SchemaLabel.ListType.CUSTOM) = LIST_TYPE.CUSTOM (createLabel always inserts CUSTOM)
                .build());
        return labelId;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBizLabelEditingAction", exports = "labelEditAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Label> editLabel(String labelId, String name, int colorIndex) {
        Objects.requireNonNull(labelId, "labelId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        var existing = store.findLabel(labelId).orElse(null); // WAWebBizLabelEditingAction.labelEditAction uses the existing label for merge
        if (existing == null) {
            return Optional.empty();
        }
        var timestamp = Instant.now();
        var predefinedId = existing.predefinedId().isPresent()
                ? existing.predefinedId().getAsInt()
                : null;
                var type = existing.type().orElse(null);
                var isActive = existing.isActive().orElse(Boolean.FALSE);
                var mutation = labelEditMutationFactory.getLabelMutation(                labelId,
                name,
                colorIndex,
                false,                predefinedId,
                isActive ? Boolean.TRUE : null,                type,
                timestamp
        );
        webAppStateService.pushPatches(LabelEditAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync(["label"], [p], ...)
        var renamed = !name.equals(existing.name());
        existing.setName(name);
        existing.setColor(colorIndex);
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
                        .listAction(ListAction.RENAME)
                        .listId(listIdNumber);
                        var wamListType = mapWamListType(existing.type().orElse(null));
                        if (wamListType != null) {
                    builder.listType(wamListType);
                }
                if (existing.predefinedId().isPresent()) {
                    builder.predefinedId(existing.predefinedId().getAsInt());
                }
                wamService.commit(builder.build());
            }
        }
        return Optional.of(existing);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBizLabelEditingAction", exports = "labelDeleteAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Label> deleteLabel(String labelId) {
        Objects.requireNonNull(labelId, "labelId cannot be null");
        var existing = store.findLabel(labelId).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        var timestamp = Instant.now();
        var mutations = new ArrayList<SyncPendingMutation>();
        mutations.add(labelEditMutationFactory.getLabelMutation(                labelId,
                existing.name(),
                existing.color(),
                true,
                null,
                null,
                null,
                timestamp
        ));
        for (var assignment : existing.assignments()) {
            mutations.add(labelAssociationMutationFactory.createLabelAssociationMutation( // WAWebLabelJidSync.createLabelAssociationMutations
                    labelId,
                    assignment,
                    false,                    timestamp
            ));
        }
        webAppStateService.pushPatches(LabelEditAction.COLLECTION_NAME, mutations); // WAWebSyncdCoreApi.lockForSync(["label","label-association","chat"], [u].concat(c), ...)
        store.removeLabel(labelId);
        // WAWebListsActions.deleteListAction -> WAWebListsLogging.logListUpdate (listAction: DELETE).
        // updateEntryPoint is omitted because the Cobalt API does not accept an entry point;
        // listType / predefinedId are read from the label snapshot captured before removal, mirroring
        // WAWebListsLogging.logListUpdate's LabelCollection.get(""+l) lookup.
        var listIdNumber = parseLabelIdToListId(labelId);
        if (listIdNumber != null) {
            var builder = new ListUpdateEventBuilder()
                    .listAction(ListAction.DELETE)
                    .listId(listIdNumber);
                    var wamListType = mapWamListType(existing.type().orElse(null));
                    if (wamListType != null) {
                builder.listType(wamListType);
            }
            if (existing.predefinedId().isPresent()) {
                builder.predefinedId(existing.predefinedId().getAsInt());
            }
            wamService.commit(builder.build());
        }
        return Optional.of(existing);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Label> deleteLabel(Label label) {
        Objects.requireNonNull(label, "label cannot be null");
        return deleteLabel(label.id());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBIzLabelReorderAction", exports = "reorderLabelsAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void reorderLabels(List<String> labelIds) {
        Objects.requireNonNull(labelIds, "labelIds cannot be null");
        var timestamp = Instant.now();
        var intIds = new ArrayList<Integer>(labelIds.size()); // ADAPTED: WA Web stores labels by string id; the wire type of LabelReorderingAction.sortedLabelIds is INT32
        for (var id : labelIds) {
            try {
                intIds.add(Integer.parseInt(id));
            } catch (NumberFormatException _) {
                // ADAPTED: skip non-numeric ids (predefined filters are not reorderable on the wire)
            }
        }
        var mutation = labelReorderingMutationFactory.getReorderLabelsMutation(intIds, timestamp);
        webAppStateService.pushPatches(LabelReorderingAction.COLLECTION_NAME, List.of(mutation));
        for (var position = 0; position < labelIds.size(); position++) {
            var id = labelIds.get(position);
            store.findLabel(id).ifPresent(label -> {});
            final var finalPosition = position;
            store.findLabel(id).ifPresent(label -> {
                if (label.orderIndex().isEmpty() || label.orderIndex().getAsInt() != finalPosition) {
                    label.setOrderIndex(finalPosition);
                }
            });
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebEditLabelAssociationBridge", exports = "editLabelAssociation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void associateLabel(String labelId, JidProvider chatProvider) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        Objects.requireNonNull(labelId, "labelId cannot be null");
        pushLabelAssociationMutation(labelId, chat, true);
        }

    /** {@inheritDoc} */
    @Override
    public void associateLabel(Label label, JidProvider chat) {
        Objects.requireNonNull(label, "label cannot be null");
        associateLabel(label.id(), chat);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebEditLabelAssociationBridge", exports = "editLabelAssociation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void dissociateLabel(String labelId, JidProvider chatProvider) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        Objects.requireNonNull(labelId, "labelId cannot be null");
        pushLabelAssociationMutation(labelId, chat, false);
        }

    /** {@inheritDoc} */
    @Override
    public void dissociateLabel(Label label, JidProvider chat) {
        Objects.requireNonNull(label, "label cannot be null");
        dissociateLabel(label.id(), chat);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "createLabelAssociationMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void associateLabel(String labelId, MessageKey messageKey) {
        Objects.requireNonNull(labelId, "labelId cannot be null");
        Objects.requireNonNull(messageKey, "messageKey cannot be null");
        var target = messageKey.parentJid() // ADAPTED: WA Web uses the message's chatId as the association target
                .orElseThrow(() -> new IllegalArgumentException("messageKey is missing a parent/chat JID"));
        pushLabelAssociationMutation(labelId, target, true);
    }

    /** {@inheritDoc} */
    @Override
    public void associateLabel(Label label, MessageKey messageKey) {
        Objects.requireNonNull(label, "label cannot be null");
        associateLabel(label.id(), messageKey);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "createLabelAssociationMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void dissociateLabel(String labelId, MessageKey messageKey) {
        Objects.requireNonNull(labelId, "labelId cannot be null");
        Objects.requireNonNull(messageKey, "messageKey cannot be null");
        var target = messageKey.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("messageKey is missing a parent/chat JID"));
        pushLabelAssociationMutation(labelId, target, false);
    }

    /** {@inheritDoc} */
    @Override
    public void dissociateLabel(Label label, MessageKey messageKey) {
        Objects.requireNonNull(label, "label cannot be null");
        dissociateLabel(label.id(), messageKey);
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
        var mutation = labelAssociationMutationFactory.createLabelAssociationMutation( // WAWebLabelJidSync.createLabelAssociationMutations
                labelId,
                target,
                labeled,
                timestamp
        );
        webAppStateService.pushPatches(LabelAssociationAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync
        store.findLabel(labelId).ifPresent(label -> {
            if (labeled) {
                label.addAssignment(target);
                } else {
                label.removeAssignment(target);
                }
        });
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getBroadcastListMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Jid createBroadcastList(String name, Collection<? extends JidProvider> recipientsProvider) {
        var recipients = Objects.requireNonNull(recipientsProvider, "recipients cannot be null").stream().map(JidProvider::toJid).toList();
        Objects.requireNonNull(name, "name cannot be null");
        var timestamp = Instant.now();
        // ADAPTED: WA Web allocates the list id via getBroadcastListStorage().getNextId();
        // Cobalt derives the next id as max(existing numeric user parts) + 1.
        var nextId = store.businessBroadcastLists().stream()
                .mapToLong(list -> { try { return Long.parseLong(list.id()); } catch (NumberFormatException _) { return 0L; } })
                .max()
                .orElse(0L) + 1;
        var listId = String.valueOf(nextId);
        var listJid = Jid.of(listId, JidServer.broadcast()); // ADAPTED: broadcast lists use `<id>@broadcast` as the routing JID
        var participants = buildBroadcastParticipants(recipients);
        var mutation = businessBroadcastListMutationFactory.getBroadcastListMutation( // WAWebBroadcastListSync.getBroadcastListMutation
                listId,
                participants,
                name,
                timestamp
        );
        webAppStateService.pushPatches(BusinessBroadcastListAction.COLLECTION_NAME, List.of(mutation));
        // ADAPTED: WA Web seeds the broadcast list storage via updateBroadcastListStorage;
        // Cobalt mirrors the state into the flat store map.
        store.putBusinessBroadcastList(new BusinessBroadcastListBuilder()
                .id(listId)
                .participants(mirrorParticipants(participants))
                .listName(name)
                .build());
        return listJid;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getBroadcastListMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editBroadcastList(JidProvider broadcastListIdProvider, String newName, Collection<? extends JidProvider> newRecipientsProvider) {
        var newRecipients = Objects.requireNonNull(newRecipientsProvider, "newRecipients cannot be null").stream().map(JidProvider::toJid).toList();
        var broadcastListId = Objects.requireNonNull(broadcastListIdProvider, "broadcastListId cannot be null").toJid();
        Objects.requireNonNull(newName, "newName cannot be null");
        var timestamp = Instant.now();
        var listId = broadcastListId.user(); // ADAPTED: extract the user part as the sync list id
        var participants = buildBroadcastParticipants(newRecipients);
        var mutation = businessBroadcastListMutationFactory.getBroadcastListMutation( // WAWebBroadcastListSync.getBroadcastListMutation
                listId,
                participants,
                newName,
                timestamp
        );
        webAppStateService.pushPatches(BusinessBroadcastListAction.COLLECTION_NAME, List.of(mutation));
        store.putBusinessBroadcastList(new BusinessBroadcastListBuilder()
                .id(listId)
                .participants(mirrorParticipants(participants))
                .listName(newName)
                .build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getDeleteBroadcastListMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteBroadcastList(JidProvider broadcastListIdProvider) {
        var broadcastListId = Objects.requireNonNull(broadcastListIdProvider, "broadcastListId cannot be null").toJid();
        var timestamp = Instant.now();
        var listId = broadcastListId.user();
        var mutation = businessBroadcastListMutationFactory.getDeleteBroadcastListMutation(listId, timestamp); // WAWebBroadcastListSync.getDeleteBroadcastListMutation
        webAppStateService.pushPatches(BusinessBroadcastListAction.COLLECTION_NAME, List.of(mutation));
        store.removeBusinessBroadcastList(listId); // WAWebBroadcastListStorageUtils.removeBroadcastListStorage
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendBroadcastMsgAction", exports = "sendBroadcastMsgAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public ChatMessageInfo sendBroadcast(JidProvider broadcastListIdProvider, MessageContainer message) {
        var broadcastListId = Objects.requireNonNull(broadcastListIdProvider, "broadcastListId cannot be null").toJid();
        Objects.requireNonNull(message, "message cannot be null");
        var selfJid = store.jid() // WAWebUserPrefsMeUser.getMeUser
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        // Build a canonical outgoing ChatMessageInfo so callers get a typed handle; MessageService.send
        // handles per-device encryption and fanout when the recipient JID carries the broadcast server.
        var messageId = MessageIdGenerator.generate(MessageIdVersion.V2, selfJid); // WAWebMsgKey.newId
        var key = new MessageKeyBuilder()
                .id(messageId)
                .parentJid(broadcastListId)
                .fromMe(true)
                .senderJid(selfJid)
                .build();
        var messageInfo = new ChatMessageInfoBuilder()
                .status(MessageStatus.PENDING) // ADAPTED: mirrors MessagePreparer.prepareChat initial status
                .senderJid(selfJid)
                .key(key)
                .message(message)
                .timestamp(Instant.now())
                .broadcast(true)
                .build();
        messageService.send(messageInfo);
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
                participant.setLidJid(store.findLidByPhone(recipient).orElse(recipient));
            }
            participants.add(participant);
        }
        return participants;
    }

    private List<BroadcastListParticipant> mirrorParticipants(List<BroadcastListParticipantAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        var mirrored = new ArrayList<BroadcastListParticipant>(actions.size());
        for (var action : actions) {
            mirrored.add(new BroadcastListParticipantBuilder()
                    .lidJid(action.lidJid())
                    .pnJid(action.pnJid().orElse(null))
                    .build());
        }
        return mirrored;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBizChatAssignmentAction", exports = "changeChatAssignment",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentLogEvents", exports = "logChatAssignment",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void assignChatToAgent(JidProvider chatProvider, String agentId) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        Objects.requireNonNull(agentId, "agentId cannot be null");
        var existingAssignment = store.findChatAssignment(chat).orElse(null);
        var hadPreviousAssignment = existingAssignment != null && existingAssignment.agentId().isPresent();
        var timestamp = Instant.now();
        var mutation = chatAssignmentMutationFactory.createChatAssignmentMutation( // WAWebChatAssignmentSync.createChatAssignmentMutations
                chat,
                agentId,
                timestamp
        );
        webAppStateService.pushPatches(ChatAssignmentAction.COLLECTION_NAME, List.of(mutation));
        // ADAPTED: WAWebChatAssignmentSync.applyMutations writes via bulkCreateOrMerge; Cobalt updates the typed quintet
        if (agentId.isEmpty()) {
            store.removeChatAssignment(chat);
            } else {
            store.putChatAssignment(new ChatAssignmentBuilder()
            .chatJid(chat)
                    .agentId(agentId)
                    .opened(existingAssignment != null && existingAssignment.opened())
                    .build());
        }
        emitChatAssignmentEvent(chat, agentId, hadPreviousAssignment); // WAWebChatAssignmentLogEvents.logChatAssignment(n.chat, (a=n.agentId)!=null?a:"", i[r], t, e.length)
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
     */
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentLogEvents", exports = "logChatAssignment",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitChatAssignmentEvent(Jid chat, String agentId, boolean hadPreviousAssignment) {
        ChatAssignmentActionType action;
        if (agentId.isEmpty()) {
            action = ChatAssignmentActionType.ACTION_UNASSIGNED;
        } else if (hadPreviousAssignment) {
            action = ChatAssignmentActionType.ACTION_REASSIGNED;
        } else {
            action = ChatAssignmentActionType.ACTION_ASSIGNED;
        }
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
        var targetAgent = store.findAgentState(agentId).orElse(null); // lookup by agentId == record key (see AgentActionHandler.applyMutation)
        var chatAssignmentMdId = targetAgent != null && targetAgent.deviceId().isPresent()
                ? targetAgent.deviceId().getAsInt()
                : -1;
        var meDeviceId = store.jid().map(Jid::device).orElse(0);
        var assignerAgentId = store.agentStates().stream()
                .filter(entry -> entry.deviceId().isPresent() && entry.deviceId().getAsInt() == meDeviceId)
                .map(AgentState::agentId)
                .findFirst()
                .orElse("");
        wamService.commit(new MdChatAssignmentEventBuilder() // WAWebChatAssignmentLogEvents: new MdChatAssignmentWamEvent({...}).commit()
                .assignerAgentId(assignerAgentId)
                .assignerBrowserId("")
                .assignerMdId(meDeviceId)
                .chatAssignmentAction(action)
                .chatAssignmentAgentId(agentId)
                .chatAssignmentBrowserId("")
                .chatAssignmentChatType(chatType)
                .chatAssignmentMdId(chatAssignmentMdId)
                .chatsCnt(1)
                .build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBizChatAssignmentAction", exports = "changeChatAssignment",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unassignChatFromAgent(JidProvider chatProvider) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        assignChatToAgent(chat, "");
        }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBizChatAssignmentOpenedAction", exports = "markChatAsOpened",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editChatAssignmentOpenedStatus(JidProvider chatProvider, boolean opened) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        var existing = store.findChatAssignment(chat).orElse(null);
        var agentId = existing == null ? null : existing.agentId().orElse(null);
        if (agentId == null) {
            throw new IllegalStateException("Chat " + chat + " has no current agent assignment");
        }
        var timestamp = Instant.now();
        var mutation = chatAssignmentOpenedStatusMutationFactory.createChatOpenedMutation( // WAWebChatAssignmentOpenedStatusSync.createChatOpenedMutations
                chat,
                agentId,
                opened,
                timestamp
        );
        webAppStateService.pushPatches(ChatAssignmentOpenedStatusAction.COLLECTION_NAME, List.of(mutation));
        store.putChatAssignment(new ChatAssignmentBuilder()
                .chatJid(chat)
                .agentId(agentId)
                .opened(opened)
                .build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryDisappearingModeJob", exports = "queryDisappearingMode",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void refreshDisappearingMode() {
        var request = new IqQueryDisappearingModeRequest();
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        // WAWebQueryDisappearingModeJob dmParser: e.child("disappearing_mode") -> {duration: attrInt("duration"), t: attrInt("t")}
        var parsed = IqQueryDisappearingModeResponse.of(response, requestNode.build()).orElse(null);
        var mode = switch (parsed) {
            case null -> throw new NoSuchElementException("Missing <disappearing_mode> in disappearing-mode query response");
            case IqQueryDisappearingModeResponse.Success success -> new AccountDisappearingModeBuilder()
                    .durationSeconds(success.duration().getSeconds())
                    .timestamp(Instant.ofEpochSecond(success.appliedAtSeconds()))
                    .build();
            case IqQueryDisappearingModeResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Disappearing-mode query rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case IqQueryDisappearingModeResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Disappearing-mode query server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
        };
        store.setDisappearingMode(mode);
        for (var listener : store.listeners()) {
            listener.onDisappearingModeChanged(this, mode);
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatEphemerality", exports = "setEphemeralSetting",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editEphemeralTimer(JidProvider chatProvider, ChatEphemeralTimer timer) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        Objects.requireNonNull(timer, "timer cannot be null");
        var seconds = timer.periodSeconds(); // ChatEphemeralTimer.periodSeconds -> WAWebEphemeralConstants duration in seconds
        if (chat.hasGroupOrCommunityServer()) {
            var request = new SmaxGroupsSetPropertyRequest(
                    chat,
                    false, // locked
                    false, // announcement
                    false, // noFrequentlyForwarded
                    seconds > 0 ? seconds : null, // ephemeralExpiration: t > 0 -> ephemeral child with expiration
                    null, // ephemeralTrigger
                    false, // unlocked
                    false, // notAnnouncement
                    false, // frequentlyForwardedOk
                    seconds <= 0, // notEphemeral: t == 0 -> not_ephemeral child
                    null, // membershipApprovalGroupJoinMode
                    false, // allowAdminReports
                    false, // notAllowAdminReports
                    false, // allowNonAdminSubGroupCreation
                    false, // notAllowNonAdminSubGroupCreation
                    false, // groupHistory
                    false  // noGroupHistory
            );
            var requestNode = request.toNode();
            var response = sendNode(requestNode); // WASmaxGroupsSetPropertyRPC.sendSetPropertyRPC: deprecatedSendIq
            var parsed = SmaxGroupsSetPropertyResponse.of(response, requestNode.build()).orElse(null);
            switch (parsed) {
                case null ->
                        throw new WhatsAppServerRuntimeException("Change group ephemeral timer: unparseable response");
                case SmaxGroupsSetPropertyResponse.Success _ -> {
                    // No useful payload: the relay only echoes back which toggles were applied.
                }
                case SmaxGroupsSetPropertyResponse.ClientError clientError ->
                        throw new WhatsAppServerRuntimeException("Change group ephemeral timer rejected: code="
                                + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
                case SmaxGroupsSetPropertyResponse.ServerError serverError ->
                        throw new WhatsAppServerRuntimeException("Change group ephemeral timer server error: code="
                                + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
            }
        } else {
            // The typed IqSetDisappearingModeRequest targets S_WHATSAPP_NET
            // (global default), so we build the chat-addressed IQ inline and
            // validate the relay reply by checking for the canonical <error>
            // child.
            var disappearing = new NodeBuilder()
                    .description("disappearing_mode")
                    .attribute("duration", seconds)
                    .build();
            var iqNode = new NodeBuilder()
                    .description("iq")
                    .attribute("xmlns", "disappearing_mode")
                    .attribute("to", chat) // ADAPTED: per-chat path addresses the chat itself, contrasting with the global path which targets S_WHATSAPP_NET
                    .attribute("type", "set")
                    .content(disappearing);
            var response = sendNode(iqNode);
            // Validate result: the relay replies with type="result" on success or type="error" + <error code text/> on failure
            var errorChild = response.getChild("error").orElse(null);
            if (errorChild != null) {
                var errorCode = errorChild.getAttributeAsInt("code").orElse(0);
                var errorText = errorChild.getAttributeAsString("text").orElse(null);
                throw new WhatsAppServerRuntimeException("Change chat ephemeral timer rejected: code="
                        + errorCode + ", text=" + errorText);
            }
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
     */
    @WhatsAppWebExport(moduleName = "WAWebWamNumberToPreciseSizeBucket", exports = "numberToPreciseSizeBucket",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static PreciseSizeBucket numberToPreciseSizeBucket(int count) {
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

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTosJob", exports = "queryTosState",
            adaptation = WhatsAppAdaptation.DIRECT)
    public TosNotices queryTosNotices(Collection<String> noticeIds) {
        Objects.requireNonNull(noticeIds, "noticeIds cannot be null");
        // Routes through the typed IQ pair so the wire shape and reply parser stay in lockstep with WAWebTosJob.queryTosState
        var request = new IqQueryTosRequest(List.copyOf(noticeIds));
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        var parsed = IqQueryTosResponse.of(response, requestBuilder.build()).orElse(null);
        return switch (parsed) {
            case IqQueryTosResponse.Success success -> {
                var notices = new ArrayList<TosNotice>(success.notices().size());
                for (var entry : success.notices()) {
                    notices.add(new TosNoticeBuilder()
                            .id(entry.id())
                            .accepted(entry.accepted())
                            .build());
                }
                yield new TosNoticesBuilder()
                        .refreshSeconds(success.refreshIntervalSeconds())
                        .notices(notices)
                        .build();
            }
            case IqQueryTosResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("ToS-notices query rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case IqQueryTosResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("ToS-notices query server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
            case null -> throw new NoSuchElementException("Missing <tos> in TOS-notices response");
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDebugGDPR", exports = "default.cancelGDPRRequest",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void cancelGdprRequest(GdprReportType reportType) {
        Objects.requireNonNull(reportType, "reportType cannot be null");
        // Maps the public model enum onto the node-layer wire enum so the typed request can build the {gdpr, action="delete", report_type?} node tree
        var wireReportType = switch (reportType) {
            case ACCOUNT -> IqDebugGdprReportType.ACCOUNT;
            case NEWSLETTERS -> IqDebugGdprReportType.NEWSLETTERS;
        };
        var request = new IqDebugGdprRequest(wireReportType);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        var parsed = IqDebugGdprResponse.of(response, requestBuilder.build()).orElse(null);
        switch (parsed) {
            case IqDebugGdprResponse.Success _ -> {
            }
            case IqDebugGdprResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("GDPR cancel rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case IqDebugGdprResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("GDPR cancel server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
            case null -> {
                // No documented variant matched: leave the call effectively fire-and-forget like the JS path
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGetPushServerSettingsJob", exports = "getPushServerSettings",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<String> queryPushServerKey() {
        // Routes through the typed IQ pair so the wire shape and reply parser stay in lockstep with WAWebGetPushServerSettingsJob
        var request = new IqGetPushServerSettingsRequest();
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        var parsed = IqGetPushServerSettingsResponse.of(response, requestBuilder.build()).orElse(null);
        // WAWebGetPushServerSettingsJob parser: t.hasChild("error") -> error branch; otherwise t.child("settings").attrString("webserverkey")
        return switch (parsed) {
            case IqGetPushServerSettingsResponse.Success success -> Optional.of(success.webServerKey());
            // ADAPTED: error branches surface as Optional.empty so callers can consult the WhatsAppClientErrorHandler for diagnostics
            case IqGetPushServerSettingsResponse.ClientError _ -> Optional.empty();
            case IqGetPushServerSettingsResponse.ServerError _ -> Optional.empty();
            case null -> Optional.empty();
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryPrivacySettingsJob", exports = "getPrivacySettings",
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
            // subsequent reads via store.findPrivacySetting hit a warm entry.
            store.addPrivacySetting(new PrivacySettingEntryBuilder()
                    .type(type)
                    .value(audience)
                    .excluded(List.of())
                    .build());
        }
        return Collections.unmodifiableMap(result);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetPrivacyJob", exports = "setPrivacy",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editPrivacySetting(PrivacySettingType type, PrivacySettingValue value) {
        editPrivacySetting(type, value, null);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetPrivacyJob", exports = "setPrivacy",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editPrivacySetting(PrivacySettingType type, PrivacySettingValue value, Collection<? extends JidProvider> excludedOrIncludedProvider) {
        var excludedOrIncluded = Objects.requireNonNull(excludedOrIncludedProvider, "excludedOrIncluded cannot be null").stream().map(JidProvider::toJid).toList();
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        if (!type.isSupported(value)) {
            throw new IllegalArgumentException("Privacy setting " + type + " does not support value " + value);
        }

        var hasList = excludedOrIncluded != null && !excludedOrIncluded.isEmpty();
        var action = (value == PrivacySettingValue.CONTACTS_EXCEPT || value == PrivacySettingValue.CONTACTS_ONLY)
                ? "add"
                : "remove";

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
                userChildren = buildPnPrivacyUsers(excludedOrIncluded, action);
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
                dispatchPrivacyIq(privacyNode, type, value, excludedOrIncluded);
                return;
            }
            var category = new NodeBuilder()
                    .description("category")
                    .attribute("name", type.data())
                    .attribute("value", value.data())
                    .attribute("dhash", "none")
                    .content(userChildren)
                    .build();
            privacyNode = new NodeBuilder()
                    .description("privacy")
                    .attribute("addressing_mode", "lid")
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
     * Sends the privacy IQ and refreshes the local store entry on success.
     *
     * @param privacyNode        the already-built {@code <privacy>} content node
     * @param type               the setting being changed
     * @param value              the newly selected audience
     * @param excludedOrIncluded the refinement list applied to the setting,
     *                           may be {@code null} or empty
     */
    private void dispatchPrivacyIq(Node privacyNode, PrivacySettingType type, PrivacySettingValue value, Collection<Jid> excludedOrIncluded) {
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "privacy")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(privacyNode);
        sendNode(iqNode);

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
     */
    private List<Node> buildLidPrivacyUsers(Collection<Jid> users, String action) {
        var result = new ArrayList<Node>(users.size());
        for (var raw : users) {
            if (raw == null) {
                continue; // ADAPTED: defensive skip
            }
            var lid = lidMigrationService.toLid(raw);
            if (lid == null) {
                throw new IllegalStateException("createLidUserNode: unknown-lid-for-privacy-list-contact " + raw);
            }
            var pn = lidMigrationService.toPn(raw);
            if (pn == null) {
                throw new IllegalStateException("createLidUserNode: unknown-username-and-pn-for-privacy-list-contact " + raw);
            }
            result.add(new NodeBuilder()
                    .description("user")
                    .attribute("action", action)
                    .attribute("jid", lid)
                    .attribute("pn_jid", pn)
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
                    .attribute("jid", raw)
                    .build());
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetReadReceiptJob", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editReadReceipts(boolean enabled) {
        // WAWebSetReadReceiptJob: wap("category", {name: "readreceipts", value: t ? "all" : "none"})
        editPrivacySetting(
                PrivacySettingType.READ_RECEIPTS,
                enabled ? PrivacySettingValue.EVERYONE : PrivacySettingValue.NOBODY);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetPrivacyTokensJob", exports = "issuePrivacyToken",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void issuePrivacyTokens(JidProvider userJidProvider, Collection<PrivacyTokenType> tokenTypes, Instant timestamp) {
        var userJid = Objects.requireNonNull(userJidProvider, "userJid cannot be null").toJid();
        Objects.requireNonNull(tokenTypes, "tokenTypes cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (tokenTypes.isEmpty()) {
            throw new IllegalArgumentException("tokenTypes must not be empty");
        }
        var tokenNodes = new ArrayList<Node>(tokenTypes.size());
        var timestampValue = Long.toString(timestamp.getEpochSecond());
        for (var tokenType : tokenTypes) {
            Objects.requireNonNull(tokenType, "tokenTypes element cannot be null");
            tokenNodes.add(new NodeBuilder()
                    .description("token")
                    .attribute("jid", userJid) // WAWebSetPrivacyTokensJob: jid: USER_JID(t)
                    .attribute("t", timestampValue) // WAWebSetPrivacyTokensJob: t: CUSTOM_STRING(String(r))
                    .attribute("type", tokenType.wireValue()) // WAWebSetPrivacyTokensJob: type: CUSTOM_STRING(e)
                    .build());
        }
        var tokensContainer = new NodeBuilder()
                .description("tokens") // WAWebSetPrivacyTokensJob: wap("tokens", null, i)
                .content(tokenNodes)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "privacy") // WAWebSetPrivacyTokensJob: xmlns: "privacy"
                .attribute("to", JidServer.user()) // WAWebSetPrivacyTokensJob: to: S_WHATSAPP_NET
                .attribute("type", "set") // WAWebSetPrivacyTokensJob: type: "set"
                .content(tokensContainer);
        sendNode(iqNode); // WAWebSetPrivacyTokensJob: deprecatedSendIq(_, parser); throws ServerStatusCodeError on !success
    }

    /**
     * Reconciles a per-category privacy block-list (e.g. the contacts who
     * cannot see when this account is online) between the local cache and
     * the server's authoritative view.
     *
     * <p>WhatsApp's "Last seen", "Online", "Profile photo", "About" and
     * "Status" privacy panels can be set to "My contacts except...", which
     * exposes a per-category blocklist of contacts. Each linked device
     * caches its own copy of those lists; this call lets a fresh client
     * synchronise its cache against the server cheaply: it sends a digest
     * of the local list and gets back a "match" verdict if the digests
     * agree, or the full server-side list plus a new digest when they
     * disagree. Cobalt-driven clients should call this on first connect
     * for every list category they care about and then refresh whenever a
     * sync mutation invalidates the cached digest.
     *
     * <p>{@link PrivacyDisallowedList#isMatch()} returning {@code true}
     * indicates the caller's cached list is up to date and no further
     * action is required; otherwise {@link PrivacyDisallowedList#users()}
     * carries the server-side users and {@link PrivacyDisallowedList#dhash()}
     * carries the new digest to persist alongside them so the next
     * reconciliation hits the fast path.
     *
     * @param listName the privacy category whose list is being reconciled
     *                 (e.g. {@code "online"}, {@code "last"},
     *                 {@code "profile"}); never {@code null}
     * @return the comparison result; never {@code null}
     * @throws NullPointerException            if {@code listName} is
     *                                         {@code null}
     * @throws WhatsAppSessionException.Closed if the socket is no longer
     *                                         open
     * @throws NoSuchElementException          if the server reply is
     *                                         malformed
     */
    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryPrivacyDisallowedListMexJob",
            exports = "queryPrivacyDisallowedListMex", adaptation = WhatsAppAdaptation.ADAPTED)
    public PrivacyDisallowedList queryPrivacyDisallowedList(JidProvider jidProvider, String dhash, String category, String type) {
        var jid = Objects.requireNonNull(jidProvider, "jid cannot be null").toJid();
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        //   WAWebMexGetPrivacyList.fetchPrivacyList({jid:n, dhash:"", category:e(t), type:"DENYLIST"})
        var request = new GetPrivacyListsMexRequest(jid, dhash == null ? "" : dhash, category, type);
        var response = sendNode(request);
        var parsed = GetPrivacyListsMexResponse.of(response);
        if (parsed.isEmpty()) {
            // missing privacy_contact_list -> empty contacts -> {status:"match"}
            return new PrivacyDisallowedListBuilder().match(true).users(List.of()).build();
        }
        var payload = parsed.get();
        //   for (var c of contacts) { var m = c.jid ?? c.pn_jid; ... users.push(createUserWidOrThrow(m)) }
        var users = payload.contacts()
                .stream()
                .map(contact -> {
                    var canonical = contact.jid();
                    return canonical.toUserJid();
                })
                .toList();
        if (users.isEmpty()) {
            //   users.length === 0 -> {status:"match"}
            return new PrivacyDisallowedListBuilder().match(true).users(List.of()).build();
        }
        //   {status:"mismatch", users, dhash: list.dhash ?? ""}
        var freshDhash = payload.dhash().orElse("");
        return new PrivacyDisallowedListBuilder().match(false).users(users).dhash(freshDhash).build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchReachoutTimelockJob",
            exports = "mexFetchReachoutTimelock", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<ReachoutTimelock> queryReachoutTimelock() {
        //   WAWebMexClient.fetchQuery(WAWebMexFetchReachoutTimelockJobQuery.graphql, {})
        var request = new FetchReachoutTimelockMexRequest();
        var response = sendNode(request);
        return FetchReachoutTimelockMexResponse.of(response)
                .map(parsed -> new ReachoutTimelockBuilder()
                        .active(parsed.isActive())
                        .timeEnforcementEnds(parsed.timeEnforcementEnds().orElse(null))
                        .enforcementType(parsed.enforcementType().orElse(null))
                        .build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchIntegritySignals", exports = "fetchIntegritySignals",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<UserIntegritySignals> queryUserIntegritySignals(JidProvider userJidProvider) {
        var userJid = Objects.requireNonNull(userJidProvider, "userJid cannot be null").toJid();
        var request = new FetchIntegritySignalsMexRequest(userJid);
        var response = sendNode(request);
        return FetchIntegritySignalsMexResponse.of(response).map(parsed -> {
            return new UserIntegritySignalsBuilder()
                    .newAccount(parsed.isNewAccount().orElse(null))
                    .suspicious(parsed.isSuspicious().orElse(null))
                    .build();
        });
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewChatMessageCappingInfoJob",
            exports = "mexFetchNewChatMessageCapping", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<NewChatMessageCappingInfo> queryNewChatMessageCappingInfo() {
        //   var i = {input: {type: "INDIVIDUAL_NEW_CHAT_THREAD"}}
        //   WAWebMexClient.fetchQuery(WAWebMexFetchNewChatMessageCappingInfoJobQuery.graphql, i)
        var request = new FetchNewChatMessageCappingInfoMexRequest("INDIVIDUAL_NEW_CHAT_THREAD");
        var response = sendNode(request);
        return FetchNewChatMessageCappingInfoMexResponse.of(response)
                .map(parsed -> new NewChatMessageCappingInfoBuilder()
                        .totalQuota(parsed.totalQuota().orElse(null))
                        .usedQuota(parsed.usedQuota().orElse(null))
                        .cycleStartTimestamp(parsed.cycleStartTimestamp().orElse(null))
                        .cycleEndTimestamp(parsed.cycleEndTimestamp().orElse(null))
                        .serverSentTimestamp(parsed.serverSentTimestamp().orElse(null))
                        .oteStatus(parsed.oteStatus().orElse(null))
                        .mvStatus(parsed.mvStatus().orElse(null))
                        .cappingStatus(parsed.cappingStatus().orElse(null))
                        .build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexIntegrityChallengeResponse",
            exports = "mexSubmitPasskeyChallengeResponse", adaptation = WhatsAppAdaptation.DIRECT)
    public void submitPasskeyIntegrityChallenge(byte[] signedChallenge, boolean prfAvailable) {
        Objects.requireNonNull(signedChallenge, "signedChallenge cannot be null");
        //   variables = {input: {challenge_type:"PASSKEY", passkey_response:{signed_challenge: btoa(JSON.stringify(e)), prf_available: e.prf_output != null}}}
        var request = new IntegrityChallengeResponseMexRequest(signedChallenge, prfAvailable);
        var response = sendNode(request);
        var parsed = IntegrityChallengeResponseMexResponse.of(response).orElse(null);
        if (parsed == null) {
            return;
        }
        var success = parsed.success().orElse(Boolean.FALSE);
        if (!success) {
            throw new WhatsAppServerRuntimeException("Passkey integrity challenge rejected: "
                    + parsed.errorMessage().orElse(null));
        }
    }

    /**
     * Dispatches a USync query through the MEX/GraphQL transport and returns
     * the parsed response.
     *
     * <p>This is the MEX-flavoured counterpart of
     * {@link #sendNode(UsyncQuery)}; the native variant uses the
     * legacy {@code <iq xmlns="usync">} envelope, while this method delegates
     * to the {@code WAWebMexUsyncQuery} GraphQL operation. The MEX path is
     * preferred when the caller wants the richer per-user projection (about
     * status, country code and registered username) that
     * {@link UsyncMexResponse} surfaces, without splitting the round trip
     * across multiple legacy stanzas.
     *
     * <p>The three boolean toggles select which optional projection fields
     * are populated server-side; passing {@code null} for all three matches
     * the WA Web "minimal" call site that only resolves JIDs without any
     * additional metadata. The {@code input} argument is the
     * pre-serialised batch payload that WA Web obtains from
     * {@code WAWebMexUsync.serializeUsyncContext}; Cobalt forwards it
     * verbatim because the input shape is opaque at this layer and depends
     * on the concrete sub-protocol the caller is exercising.
     *
     * <p>This method is package-private because the {@link UsyncMexResponse}
     * return type carries a deeply nested wire-layer projection (about
     * status, country code, username info, lid info, ...). The sister
     * projection helpers ({@link #queryAbout(Jid)} via the private
     * {@code queryAboutViaUsyncMex} dispatch branch,
     * {@link #queryUserCountryCode(Jid)},
     * {@link #queryUserUsername(Jid)}) are the public API: they project
     * the response into the {@code Optional<String>} surface that
     * caller code actually consumes.
     *
     * @param includeAboutStatus toggles the {@code include_about_status}
     *                           variable; {@code null} omits the variable
     * @param includeCountryCode toggles the {@code include_country_code}
     *                           variable; {@code null} omits the variable
     * @param includeUsername    toggles the {@code include_username}
     *                           variable; {@code null} omits the variable
     * @param input              the pre-serialised input payload, or
     *                           {@code null} to omit the variable
     * @return the parsed response, or {@link Optional#empty()} when the
     *         server returned no envelope
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsync", exports = "mexUsyncQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    Optional<UsyncMexResponse> executeUsyncMex(Boolean includeAboutStatus,
                                                Boolean includeCountryCode,
                                                Boolean includeUsername,
                                                String input) {
        //   WAWebMexClient.fetchQuery(WAWebMexUsyncQuery.graphql,
        //     {include_about_status, include_country_code, include_username, input})
        var request = new UsyncMexRequest(includeAboutStatus, includeCountryCode, includeUsername, input);
        var response = sendNode(request);
        return UsyncMexResponse.of(response);
    }

    /**
     * Reads the about/text-status line for a single user through the USync
     * MEX/GraphQL transport.
     *
     * <p>Delegates to {@link #executeUsyncMex(Boolean, Boolean, Boolean, String)}
     * with only the {@code include_about_status} toggle enabled. The MEX path
     * additionally surfaces the timestamp and {@code status} flag the server
     * attaches to the about line, but this helper only returns the text;
     * callers that want the full projection should call
     * {@link #executeUsyncMex(Boolean, Boolean, Boolean, String)} directly
     * and walk {@link UsyncMexResponse.Item#aboutStatusInfo()}.
     *
     * <p>This is the LID / AB-prop-enabled branch of {@link #queryAbout(Jid)}
     * dispatch and is private because the dispatch logic is centralised on
     * {@link #queryAbout(Jid)}.
     *
     * @param userJid the user JID to query; never {@code null}
     * @return the about line, or {@link Optional#empty()} when the user has
     *         no about set or the server returned no item
     * @throws WhatsAppSessionException.Closed if the socket is no longer open
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsersGetAboutStatus",
            exports = "mexUsersGetAboutStatus", adaptation = WhatsAppAdaptation.ADAPTED)
    private Optional<String> queryAboutViaUsyncMex(Jid userJid) {
        //   WAWebMexUsync.mexUsyncQuery({include_about_status:true, input: serializeUsyncContext([userJid])})
        var input = serializeUsyncMexInput(userJid);
        return executeUsyncMex(Boolean.TRUE, null, null, input)
                .flatMap(response -> response.items().stream().findFirst())
                .flatMap(UsyncMexResponse.Item::aboutStatusInfo)
                .flatMap(UsyncMexResponse.Item.AboutStatusInfo::text)
                .filter(s -> !s.isEmpty());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexUsersGetCountryCode",
            exports = "mexUsersGetCountryCode", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<String> queryUserCountryCode(JidProvider userJidProvider) {
        var userJid = Objects.requireNonNull(userJidProvider, "userJid cannot be null").toJid();
        //   WAWebMexUsync.mexUsyncQuery({include_country_code:true, input: serializeUsyncContext([userJid])})
        var input = serializeUsyncMexInput(userJid);
        return executeUsyncMex(null, Boolean.TRUE, null, input)
                .flatMap(response -> response.items().stream().findFirst())
                .flatMap(UsyncMexResponse.Item::countryCode)
                .filter(s -> !s.isEmpty());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexUsersGetUsername",
            exports = "mexUsersGetUsername", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<UserUsername> queryUserUsername(JidProvider userJidProvider) {
        var userJid = Objects.requireNonNull(userJidProvider, "userJid cannot be null").toJid();
        //   WAWebMexUsync.mexUsyncQuery({include_username:true, input: serializeUsyncContext([userJid])})
        var input = serializeUsyncMexInput(userJid);
        return executeUsyncMex(null, null, Boolean.TRUE, input)
                .flatMap(response -> response.items().stream().findFirst())
                .flatMap(UsyncMexResponse.Item::usernameInfo)
                .filter(info -> info.username().filter(s -> !s.isEmpty()).isPresent())
                .map(info -> new UserUsername(
                        info.username().orElse(null),
                        info.state().orElse(null),
                        info.timestamp().orElse(null),
                        info.pin().orElse(null),
                        info.status().orElse(null)));
    }

    /**
     * Serialises a single-user MEX usync input payload as a JSON string.
     *
     * <p>WA Web obtains this payload from
     * {@code WAWebMexUsync.serializeUsyncContext}, which produces a compact
     * JSON list of {@code {jid}} entries. Cobalt emits an equivalent
     * single-element list inline because the convenience helpers only ever
     * query one JID at a time.
     *
     * @param userJid the user JID to encode; never {@code null}
     * @return the serialised input payload
     */
    private static String serializeUsyncMexInput(Jid userJid) {
        return "[{\"jid\":\"" + userJid + "\"}]";
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetDisappearingModeJob", exports = "setDisappearingMode",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void editDefaultDisappearingMode(ChatEphemeralTimer timer) {
        Objects.requireNonNull(timer, "timer cannot be null");
        var seconds = timer.periodSeconds(); // ChatEphemeralTimer.periodSeconds: duration in seconds
        var disappearing = new NodeBuilder()
                .description("disappearing_mode")
                .attribute("duration", seconds)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "disappearing_mode")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(disappearing);
        sendNode(iqNode);
        store.setNewChatsEphemeralTimer(timer);
        }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupCreateJob", exports = "createGroup",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public GroupMetadata createGroup(String subject, Collection<? extends JidProvider> participantsProvider) {
        var participants = Objects.requireNonNull(participantsProvider, "participants cannot be null").stream().map(JidProvider::toJid).toList();
        return createGroup(subject, ChatEphemeralTimer.OFF, participants);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupCreateJob", exports = "createGroup",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public GroupMetadata createGroup(String subject, ChatEphemeralTimer ephemeralTimer, Collection<? extends JidProvider> participantsProvider) {
        var participants = Objects.requireNonNull(participantsProvider, "participants cannot be null").stream().map(JidProvider::toJid).toList();
        Objects.requireNonNull(subject, "subject cannot be null");
        Objects.requireNonNull(ephemeralTimer, "ephemeralTimer cannot be null");
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required");
        }
        var createChildren = new ArrayList<Node>(participants.size() + 1); // WASmaxOutGroupsCreateRequest: REPEATED_CHILD(participant, ...) + OPTIONAL_CHILD(ephemeral, ...)
        for (var participant : participants) {
            createChildren.add(new NodeBuilder()
                    .description("participant") // WASmaxOutGroupsCreateRequest.makeCreateRequestCreateParticipant: smax("participant", ...)
                    .attribute("jid", participant) // WASmaxOutGroupsCreateRequest: jid: JID(t)
                    .build());
        }
        var ephemeralSeconds = ephemeralTimer.periodSeconds();
        if (ephemeralSeconds != 0) {
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
        var metadata = handleChatMetadata(response);
        if (!(metadata instanceof GroupMetadata groupMetadata)) { // WAWebGroupCreateJob: createGroup always creates a non-community group
            throw new NoSuchElementException("Expected a group metadata, got %s".formatted(metadata));
        }
        wamService.commit(new GroupCreateCEventBuilder().build());
        return groupMetadata;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupExitJob", exports = "leaveGroup",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void leaveGroup(JidProvider groupProvider) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        dispatchLeaveGroup(List.of(group));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupExitJob", exports = "leaveGroup",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void leaveGroup(JidProvider... groups) {
        Objects.requireNonNull(groups, "groups cannot be null");
        if (groups.length == 0) {
            throw new IllegalArgumentException("groups must contain at least one entry");
        }
        var targets = new ArrayList<Jid>(groups.length);
        for (var group : groups) {
            Objects.requireNonNull(group, "group cannot be null");
            var groupJid = group.toJid();
            if (!groupJid.hasGroupOrCommunityServer()) {
                throw new IllegalArgumentException("Expected a group/community");
            }
            targets.add(groupJid);
        }
        dispatchLeaveGroup(targets);
    }

    /**
     * Shared implementation for {@link #leaveGroup(Jid)} and
     * {@link #leaveGroup(Jid...)} that delegates to the typed
     * {@link IqGroupExitRequest}.
     *
     * @param targets the non-{@code null}, non-empty list of group JIDs to
     *                leave
     */
    private void dispatchLeaveGroup(List<Jid> targets) {
        var request = new IqGroupExitRequest(targets, IqGroupExitRequest.Mode.GROUP);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        switch (IqGroupExitResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqGroupExitResponse.Success _ -> { /* per-target codes are observational */ }
            case IqGroupExitResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Leave group rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case IqGroupExitResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Leave group server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
            case null -> { /* no documented variant matched: accept the legacy success */ }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupIsInternalJob", exports = "mexFetchGroupIsInternal",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isGroupInternal(JidProvider groupJidProvider) {
        var groupJid = Objects.requireNonNull(groupJidProvider, "groupJid cannot be null").toJid();
        var request = new FetchGroupIsInternalMexRequest(groupJid.toString());
        var response = sendNode(request);
        return FetchGroupIsInternalMexResponse.of(response)
                .map(FetchGroupIsInternalMexResponse::isInternal)
                .orElse(false);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupInfoJob", exports = "mexGetGroupInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<GroupMetadata> queryGroupInfo(JidProvider groupProvider, boolean includeUsername,
                                                  String participantsPhash, String queryContext) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var request = new FetchGroupInfoMexRequest(group.toString(), includeUsername, participantsPhash, queryContext);
        var response = sendNode(request);
        return FetchGroupInfoMexResponse.of(response)
                .map(this::mapFetchGroupInfoToMetadata);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<GroupMetadata> queryGroupInfo(JidProvider group) {
        return queryGroupInfo(group, false, null, null);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupInfoIncludBotsJob", exports = "mexGetGroupInfoIncludBots",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<GroupMetadata> queryGroupInfoIncludingBots(JidProvider groupProvider, boolean includeUsername,
                                                               String participantsPhash, String queryContext) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var request = new FetchGroupInfoIncludBotsMexRequest(group.toString(), includeUsername, participantsPhash, queryContext);
        var response = sendNode(request);
        return FetchGroupInfoIncludBotsMexResponse.of(response)
                .map(this::mapFetchGroupInfoIncludBotsToMetadata);
    }

    /**
     * Maps a parsed {@link FetchGroupInfoMexResponse} into a domain-level
     * {@link GroupMetadata}.
     *
     * <p>Translates the {@code xwa2_group_query_by_id} GraphQL envelope into
     * the protobuf-modelled metadata used elsewhere in the API. Per-field
     * mapping mirrors WA Web's {@code WAWebMexFetchGroupInfoJob} reducer: the
     * scalar {@code id}, {@code subject}, {@code description},
     * {@code creator}, {@code creation_time}, the participant edge list, and
     * the configurable {@code properties} block (membership approval mode,
     * member-add / member-link mode, ephemeral expiration, parent group jid,
     * limit-sharing flag and the support / capi / hidden-group markers).
     *
     * @param response the non-{@code null} parsed MEX response
     * @return the domain-level {@link GroupMetadata}; never {@code null}
     */
    private GroupMetadata mapFetchGroupInfoToMetadata(FetchGroupInfoMexResponse response) {
        var jid = response.id().map(Jid::of).orElseThrow(() -> new NoSuchElementException("Missing id in group info response"));
        var subject = response.subject().flatMap(FetchGroupInfoMexResponse.Subject::value).orElse("");
        var subjectAuthor = response.subject()
                .flatMap(FetchGroupInfoMexResponse.Subject::creator)
                .flatMap(FetchGroupInfoMexResponse.Subject.Creator::id)
                .map(Jid::of)
                .orElse(null);
        var subjectTimestamp = response.subject()
                .flatMap(FetchGroupInfoMexResponse.Subject::creationTime)
                .orElse(null);
        var foundationTimestamp = response.creationTime().orElse(null);
        var founder = response.creator().flatMap(FetchGroupInfoMexResponse.Creator::id).map(Jid::of).orElse(null);
        var description = response.description().flatMap(FetchGroupInfoMexResponse.Description::value).orElse(null);
        var descriptionId = response.description().flatMap(FetchGroupInfoMexResponse.Description::id).orElse(null);
        var descriptionTimestamp = response.description()
                .flatMap(FetchGroupInfoMexResponse.Description::creationTime)
                .orElse(null);
        var descriptionAuthor = response.description()
                .flatMap(FetchGroupInfoMexResponse.Description::creator)
                .flatMap(FetchGroupInfoMexResponse.Description.Creator::id)
                .map(Jid::of)
                .orElse(null);
        var participants = response.participants()
                .map(FetchGroupInfoMexResponse.Participants::edges)
                .orElse(List.of())
                .stream()
                .map(edge -> {
                    var participantJid = edge.node()
                            .flatMap(FetchGroupInfoMexResponse.Participants.Edges.Node::id)
                            .map(Jid::of)
                            .orElse(null);
                    if (participantJid == null) {
                        return null;
                    }
                    var role = edge.role()
                            .map(String::toLowerCase)
                            .filter(r -> !r.isEmpty() && !"member".equals(r))
                            .map(GroupPartipantRole::of)
                            .orElse(GroupPartipantRole.USER);
                    return new GroupParticipantBuilder()
                            .userJid(participantJid)
                            .rank(role)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var properties = response.properties().orElse(null);
        var ephemeralTimer = properties == null ? null : properties.ephemeral()
                .flatMap(FetchGroupInfoMexResponse.Properties.Ephemeral::expirationTimeInSec)
                .map(d -> ChatEphemeralTimer.of((int) d.toSeconds()))
                .orElse(null);
        var membershipApprovalMode = properties != null && properties.membershipApprovalModeEnabled();
        var memberAddModeAdminOnly = properties != null && properties.memberAddMode()
                .map("ADMIN_ADD"::equals).orElse(false);
        var memberLinkModeAdminOnly = properties != null && properties.memberLinkMode()
                .map("ADMIN_LINK"::equals).orElse(false);
        var announce = properties != null && properties.announcement().isPresent();
        var restrict = properties != null && properties.locked().isPresent();
        var allowAdminReports = properties != null && properties.allowAdminReports();
        var parentCommunityJid = properties == null ? null : properties.parentGroupJid().map(Jid::of).orElse(null);
        var hasCapi = properties != null && properties.capi().isPresent();
        var support = properties != null && properties.support().isPresent();
        var generalSubgroup = properties != null && properties.generalChat().isPresent();
        var hiddenSubgroup = properties != null && properties.hiddenGroup().isPresent();
        var groupSafetyCheck = properties != null && properties.groupSafetyCheck().isPresent();
        var autoAddDisabled = properties != null && properties.autoAddDisabled();
        var limitSharingEnabled = properties != null && properties.limitSharing()
                .map(FetchGroupInfoMexResponse.Properties.LimitSharing::limitSharingEnabled)
                .orElse(false);
        var lidAddressingMode = properties != null && properties.lidMigrationState()
                .flatMap(FetchGroupInfoMexResponse.Properties.LidMigrationState::addressingMode)
                .map("lid"::equalsIgnoreCase)
                .orElse(false);
        var size = response.totalParticipantsCount().isPresent()
                ? (int) response.totalParticipantsCount().getAsLong()
                : null;
        return new GroupMetadataBuilder()
                .jid(jid)
                .subject(subject)
                .subjectAuthorJid(subjectAuthor)
                .subjectTimestamp(subjectTimestamp)
                .foundationTimestamp(foundationTimestamp)
                .founderJid(founder)
                .description(description)
                .descriptionId(descriptionId)
                .descriptionTimestamp(descriptionTimestamp)
                .descriptionAuthorJid(descriptionAuthor)
                .participants(participants)
                .ephemeralExpiration(ephemeralTimer)
                .restrict(restrict)
                .announce(announce)
                .parentCommunityJid(parentCommunityJid)
                .isLidAddressingMode(lidAddressingMode)
                .membershipApprovalMode(membershipApprovalMode)
                .memberAddModeAdminOnly(memberAddModeAdminOnly)
                .memberLinkModeAdminOnly(memberLinkModeAdminOnly)
                .reportToAdminMode(allowAdminReports)
                .size(size)
                .support(support)
                .generalSubgroup(generalSubgroup)
                .hiddenSubgroup(hiddenSubgroup)
                .groupSafetyCheck(groupSafetyCheck)
                .generalChatAutoAddDisabled(autoAddDisabled)
                .hasCapi(hasCapi)
                .limitSharingEnabled(limitSharingEnabled)
                .build();
    }

    /**
     * Maps a parsed {@link FetchGroupInfoIncludBotsMexResponse} into a
     * domain-level {@link GroupMetadata}.
     *
     * <p>Identical mapping to {@link #mapFetchGroupInfoToMetadata}; the only
     * wire-level difference is that the participant edge list also enumerates
     * bot accounts as first-class members.
     *
     * @param response the non-{@code null} parsed MEX response
     * @return the domain-level {@link GroupMetadata}; never {@code null}
     */
    private GroupMetadata mapFetchGroupInfoIncludBotsToMetadata(FetchGroupInfoIncludBotsMexResponse response) {
        var jid = response.id().map(Jid::of).orElseThrow(() -> new NoSuchElementException("Missing id in group info response"));
        var subject = response.subject().flatMap(FetchGroupInfoIncludBotsMexResponse.Subject::value).orElse("");
        var subjectAuthor = response.subject()
                .flatMap(FetchGroupInfoIncludBotsMexResponse.Subject::creator)
                .flatMap(FetchGroupInfoIncludBotsMexResponse.Subject.Creator::id)
                .map(Jid::of)
                .orElse(null);
        var subjectTimestamp = response.subject()
                .flatMap(FetchGroupInfoIncludBotsMexResponse.Subject::creationTime)
                .orElse(null);
        var foundationTimestamp = response.creationTime().orElse(null);
        var founder = response.creator().flatMap(FetchGroupInfoIncludBotsMexResponse.Creator::id).map(Jid::of).orElse(null);
        var description = response.description().flatMap(FetchGroupInfoIncludBotsMexResponse.Description::value).orElse(null);
        var descriptionId = response.description().flatMap(FetchGroupInfoIncludBotsMexResponse.Description::id).orElse(null);
        var descriptionTimestamp = response.description()
                .flatMap(FetchGroupInfoIncludBotsMexResponse.Description::creationTime)
                .orElse(null);
        var descriptionAuthor = response.description()
                .flatMap(FetchGroupInfoIncludBotsMexResponse.Description::creator)
                .flatMap(FetchGroupInfoIncludBotsMexResponse.Description.Creator::id)
                .map(Jid::of)
                .orElse(null);
        var participants = response.participants()
                .map(FetchGroupInfoIncludBotsMexResponse.Participants::edges)
                .orElse(List.of())
                .stream()
                .map(edge -> {
                    var participantJid = edge.participant()
                            .flatMap(FetchGroupInfoIncludBotsMexResponse.Participants.Edges.Participant::id)
                            .map(Jid::of)
                            .orElse(null);
                    if (participantJid == null) {
                        return null;
                    }
                    var role = edge.role()
                            .map(String::toLowerCase)
                            .filter(r -> !r.isEmpty() && !"member".equals(r))
                            .map(GroupPartipantRole::of)
                            .orElse(GroupPartipantRole.USER);
                    return new GroupParticipantBuilder()
                            .userJid(participantJid)
                            .rank(role)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var properties = response.properties().orElse(null);
        var ephemeralTimer = properties == null ? null : properties.ephemeral()
                .flatMap(FetchGroupInfoIncludBotsMexResponse.Properties.Ephemeral::expirationTimeInSec)
                .map(d -> ChatEphemeralTimer.of((int) d.toSeconds()))
                .orElse(null);
        var membershipApprovalMode = properties != null && properties.membershipApprovalModeEnabled();
        var memberAddModeAdminOnly = properties != null && properties.memberAddMode()
                .map("ADMIN_ADD"::equals).orElse(false);
        var memberLinkModeAdminOnly = properties != null && properties.memberLinkMode()
                .map("ADMIN_LINK"::equals).orElse(false);
        var announce = properties != null && properties.announcement().isPresent();
        var restrict = properties != null && properties.locked().isPresent();
        var allowAdminReports = properties != null && properties.allowAdminReports();
        var parentCommunityJid = properties == null ? null : properties.parentGroupJid().map(Jid::of).orElse(null);
        var hasCapi = properties != null && properties.capi().isPresent();
        var support = properties != null && properties.support().isPresent();
        var generalSubgroup = properties != null && properties.generalChat().isPresent();
        var hiddenSubgroup = properties != null && properties.hiddenGroup().isPresent();
        var groupSafetyCheck = properties != null && properties.groupSafetyCheck().isPresent();
        var autoAddDisabled = properties != null && properties.autoAddDisabled();
        var limitSharingEnabled = properties != null && properties.limitSharing()
                .map(FetchGroupInfoIncludBotsMexResponse.Properties.LimitSharing::limitSharingEnabled)
                .orElse(false);
        var lidAddressingMode = properties != null && properties.lidMigrationState()
                .flatMap(FetchGroupInfoIncludBotsMexResponse.Properties.LidMigrationState::addressingMode)
                .map("lid"::equalsIgnoreCase)
                .orElse(false);
        var size = response.totalParticipantsCount().isPresent()
                ? Integer.valueOf((int) response.totalParticipantsCount().getAsLong())
                : null;
        return new GroupMetadataBuilder()
                .jid(jid)
                .subject(subject)
                .subjectAuthorJid(subjectAuthor)
                .subjectTimestamp(subjectTimestamp)
                .foundationTimestamp(foundationTimestamp)
                .founderJid(founder)
                .description(description)
                .descriptionId(descriptionId)
                .descriptionTimestamp(descriptionTimestamp)
                .descriptionAuthorJid(descriptionAuthor)
                .participants(participants)
                .ephemeralExpiration(ephemeralTimer)
                .restrict(restrict)
                .announce(announce)
                .parentCommunityJid(parentCommunityJid)
                .isLidAddressingMode(lidAddressingMode)
                .membershipApprovalMode(membershipApprovalMode)
                .memberAddModeAdminOnly(memberAddModeAdminOnly)
                .memberLinkModeAdminOnly(memberLinkModeAdminOnly)
                .reportToAdminMode(allowAdminReports)
                .size(size)
                .support(support)
                .generalSubgroup(generalSubgroup)
                .hiddenSubgroup(hiddenSubgroup)
                .groupSafetyCheck(groupSafetyCheck)
                .generalChatAutoAddDisabled(autoAddDisabled)
                .hasCapi(hasCapi)
                .limitSharingEnabled(limitSharingEnabled)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupInviteCodeJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<String> queryGroupInviteCode(JidProvider groupProvider, String queryContext) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var request = new FetchGroupInviteCodeMexRequest(group.toString(), queryContext);
        var response = sendNode(request);
        return FetchGroupInviteCodeMexResponse.of(response)
                .flatMap(FetchGroupInviteCodeMexResponse::inviteCode);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexCreateInviteCodeJob", exports = "mexCreateInviteCode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public String createGroupInviteCode(JidProvider receiverProvider, String entryPoint) {
        var receiver = Objects.requireNonNull(receiverProvider, "receiver cannot be null").toJid();
        Objects.requireNonNull(entryPoint, "entryPoint cannot be null");
        var request = new CreateInviteCodeMexRequest(receiver.toString(), entryPoint);
        var response = sendNode(request);
        return CreateInviteCodeMexResponse.of(response)
                .flatMap(CreateInviteCodeMexResponse::code)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Missing invite code in create-invite-code response: " + response));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupCommunityJob", exports = "sendCreateCommunity",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CommunityMetadata createCommunity(String name, String description) {
        return createCommunity(name, description, ChatEphemeralTimer.OFF);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupCommunityJob", exports = "sendCreateCommunity",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CommunityMetadata createCommunity(String name, String description, ChatEphemeralTimer ephemeralTimer) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(ephemeralTimer, "ephemeralTimer cannot be null");
        var createChildren = new ArrayList<Node>(4); // WASmaxOutGroupsCreateRequest: OPTIONAL_CHILD(description), OPTIONAL_CHILD(parent), OPTIONAL_CHILD(ephemeral)
        if (description != null && !description.isEmpty()) {
            createChildren.add(new NodeBuilder()
                    .description("description") // WASmaxOutGroupsCreateRequest.makeCreateRequestCreateDescription: smax("description", {id}, smax("body", null, value))
                    .attribute("id", RandomIdUtils.generateSid())
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
                .attribute("subject", name)
                .content(createChildren)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2") // WASmaxOutGroupsBaseSetServerMixin: xmlns: "w:g2"
                .attribute("to", Jid.of(JidServer.groupOrCommunity())) // WASmaxOutGroupsBaseSetServerMixin: to: G_US
                .attribute("type", "set") // WAWebSmaxBaseIQSetRequestMixin: type: "set"
                .content(createNode);
        var response = sendNode(iqNode); // WASmaxGroupsCreateRPC.sendCreateRPC
        var metadata = handleChatMetadata(response);
        if (!(metadata instanceof CommunityMetadata communityMetadata)) { // WAWebGroupCommunityJob: the server always returns a parent group tree for this flow
            throw new NoSuchElementException("Expected community metadata, got %s".formatted(metadata));
        }
        return communityMetadata;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupCommunityJob", exports = "sendDeactivateCommunity",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deactivateCommunity(JidProvider communityProvider) {
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var request = new SmaxGroupsDeleteParentGroupRequest(community);
        var requestNode = request.toNode();
        var response = sendNode(requestNode); // WASmaxGroupsDeleteParentGroupRPC.sendDeleteParentGroupRPC
        switch (SmaxGroupsDeleteParentGroupResponse.of(response, requestNode.build()).orElse(null)) {
            case SmaxGroupsDeleteParentGroupResponse.Success _ -> { /* deactivation accepted */ }
            case SmaxGroupsDeleteParentGroupResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Deactivate community rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsDeleteParentGroupResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Deactivate community server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
            case null -> { /* no documented variant matched: accept the legacy success */ }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupExitJob", exports = "leaveCommunity",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void leaveCommunity(JidProvider communityProvider) {
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var request = new IqGroupExitRequest(List.of(community), IqGroupExitRequest.Mode.LINKED_GROUPS);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        switch (IqGroupExitResponse.of(response, requestNode.build()).orElse(null)) {
            case IqGroupExitResponse.Success _ -> { /* per-target codes are observational */ }
            case IqGroupExitResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Leave community rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case IqGroupExitResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Leave community server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
            case null -> { /* no documented variant matched: accept the legacy success */ }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexTransferCommunityOwnershipJob", exports = "mexTransferCommunityOwnershipJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void transferCommunityOwnership(JidProvider communityProvider, JidProvider newOwnerProvider) {
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        var newOwner = Objects.requireNonNull(newOwnerProvider, "newOwner cannot be null").toJid();
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        if (newOwner.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a user JID for newOwner");
        }
        var input = JSON.toJSONString(Map.of(                "group_id", community.toString(),
                "new_owner_id", newOwner.toString()
        ));
        var request = new TransferCommunityOwnershipMexRequest(input);
        sendNodeWithNoResponse(request);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchSubgroupSuggestionsJob", exports = "mexFetchSubgroupSuggestions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<Jid> querySubgroupSuggestions(JidProvider communityProvider) {
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var request = new FetchSubgroupSuggestionsMexRequest(community.toString(), "INTERACTIVE", null);
        var response = sendNode(request);
        return FetchSubgroupSuggestionsMexResponse.of(response)
                .flatMap(FetchSubgroupSuggestionsMexResponse::subGroupSuggestions)
                .stream()
                .map(FetchSubgroupSuggestionsMexResponse.SubGroupSuggestions::edges)
                .flatMap(Collection::stream)
                .map(FetchSubgroupSuggestionsMexResponse.SubGroupSuggestions.Edges::node)
                .flatMap(Optional::stream)
                .map(FetchSubgroupSuggestionsMexResponse.SubGroupSuggestions.Edges.Node::id)
                .flatMap(Optional::stream)
                .map(Jid::of)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSubgroupSuggestionsActionJob", exports = "sendSubgroupSuggestionsAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void approveSubgroupSuggestion(JidProvider communityProvider, JidProvider suggestedSubgroupProvider, JidProvider suggestionCreatorProvider) {
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        var suggestedSubgroup = Objects.requireNonNull(suggestedSubgroupProvider, "suggestedSubgroup cannot be null").toJid();
        var suggestionCreator = Objects.requireNonNull(suggestionCreatorProvider, "suggestionCreator cannot be null").toJid();
        subgroupSuggestionsAction(community, suggestedSubgroup, suggestionCreator, true); // WAWebSubgroupSuggestionsActionJob: n === c.APPROVE
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSubgroupSuggestionsActionJob", exports = "sendSubgroupSuggestionsAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void rejectSubgroupSuggestion(JidProvider communityProvider, JidProvider suggestedSubgroupProvider, JidProvider suggestionCreatorProvider) {
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        var suggestedSubgroup = Objects.requireNonNull(suggestedSubgroupProvider, "suggestedSubgroup cannot be null").toJid();
        var suggestionCreator = Objects.requireNonNull(suggestionCreatorProvider, "suggestionCreator cannot be null").toJid();
        subgroupSuggestionsAction(community, suggestedSubgroup, suggestionCreator, false); // WAWebSubgroupSuggestionsActionJob: n === c.REJECT
    }

    /**
     * Issues a {@code sub_group_suggestions_action} stanza against the
     * given community with the requested action (approve/reject) by
     * delegating to the typed
     * {@link SmaxGroupsSubGroupSuggestionsActionRequest}.
     *
     * @param community         the validated community JID
     * @param subgroup          the validated suggested subgroup JID
     * @param suggestionCreator the validated suggestion-creator user JID
     * @param approve           {@code true} to emit the {@code <approve>}
     *                          child; {@code false} for {@code <reject>}
     */
    private void subgroupSuggestionsAction(Jid community, Jid subgroup, Jid suggestionCreator, boolean approve) {
        Objects.requireNonNull(community, "community cannot be null");
        Objects.requireNonNull(subgroup, "subgroup cannot be null");
        Objects.requireNonNull(suggestionCreator, "suggestionCreator cannot be null");
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community for community");
        }
        if (!subgroup.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community for suggested subgroup");
        }
        var entry = new SmaxGroupsSubGroupSuggestionsActionRequest.CreatorSuggestion(suggestionCreator, subgroup, null);
        var request = new SmaxGroupsSubGroupSuggestionsActionRequest(community,
                approve ? List.of(entry) : List.of(),
                approve ? List.of() : List.of(entry),
                List.of());
        var requestNode = request.toNode();
        var response = sendNode(requestNode); // WASmaxGroupsSubGroupSuggestionsActionRPC.sendSubGroupSuggestionsActionRPC
        switch (SmaxGroupsSubGroupSuggestionsActionResponse.of(response, requestNode.build()).orElse(null)) {
            case SmaxGroupsSubGroupSuggestionsActionResponse.Success _ -> { /* per-suggestion outcomes are observational */ }
            case SmaxGroupsSubGroupSuggestionsActionResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Sub-group suggestions action rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsSubGroupSuggestionsActionResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Sub-group suggestions action server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
            case null -> { /* no documented variant matched: accept the legacy success */ }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexQuerySubgroupParticipantCountJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public long querySubgroupParticipantCount(JidProvider subgroupProvider) {
        var subgroup = Objects.requireNonNull(subgroupProvider, "subgroup cannot be null").toJid();
        if (!subgroup.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var request = new QuerySubgroupParticipantCountMexRequest(subgroup.toString());
        var response = sendNode(request);
        return QuerySubgroupParticipantCountMexResponse.of(response)
                .flatMap(QuerySubgroupParticipantCountMexResponse::subGroups)
                .stream()
                .map(QuerySubgroupParticipantCountMexResponse.SubGroups::edges)
                .flatMap(Collection::stream)
                .map(QuerySubgroupParticipantCountMexResponse.SubGroups.Edges::node)
                .flatMap(Optional::stream)
                .filter(entry -> entry.id().map(id -> Objects.equals(id, subgroup.toString()) || Objects.equals(id, subgroup.user())).orElse(false))
                .map(QuerySubgroupParticipantCountMexResponse.SubGroups.Edges.Node::totalParticipantsCount)
                .filter(OptionalLong::isPresent)
                .mapToLong(OptionalLong::getAsLong)
                .findFirst()
                .orElse(-1L);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAllSubgroupsJob", exports = "mexFetchAllSubgroups",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<CommunityLinkedGroup> querySubgroups(JidProvider communityProvider) {
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        if (!community.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var request = new FetchAllSubgroupsMexRequest(community.toString(), "INTERACTIVE", null);
        var response = sendNode(request);
        var parsed = FetchAllSubgroupsMexResponse.of(response)
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Fetch all subgroups response did not parse"));
        // WAWebMexFetchAllSubgroupsJob: throw ServerStatusCodeError(500, "missing announcement group in response")
        var defaultSubGroup = parsed.defaultSubGroup()
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Fetch all subgroups response is missing the default subgroup"));
        // WAWebMexFetchAllSubgroupsJob: throw ServerStatusCodeError(500, "missing edges in response")
        var subGroups = parsed.subGroups()
                .orElseThrow(() -> new WhatsAppServerRuntimeException("Fetch all subgroups response is missing the subgroup edges"));
        var result = new ArrayList<CommunityLinkedGroup>();
        // WAWebMexFetchAllSubgroupsJob: c=[d(e,u,!0)] - default subgroup is prepended with defaultSubgroup=true
        result.add(new CommunityLinkedGroupBuilder()
                .jid(defaultSubGroup.id().map(Jid::of).orElse(null))
                .subject(defaultSubGroup.subject().flatMap(DefaultSubGroup.Subject::value).orElse(null))
                .subjectTimestamp(defaultSubGroup.subject().flatMap(DefaultSubGroup.Subject::creationTime).orElse(null))
                .parentGroupJid(community)
                .defaultSubgroup(true)
                .build());
        // WAWebMexFetchAllSubgroupsJob: m.forEach(...) - regular subgroups projected with d(e, r)
        subGroups.edges()
                .stream()
                .map(SubGroups.Edges::node)
                .flatMap(Optional::stream)
                .map(node -> new CommunityLinkedGroupBuilder()
                        .jid(node.id().map(Jid::of).orElse(null))
                        .subject(node.subject().flatMap(SubGroups.Edges.Node.Subject::value).orElse(null))
                        .subjectTimestamp(node.subject().flatMap(SubGroups.Edges.Node.Subject::creationTime).orElse(null))
                        .parentGroupJid(community)
                        .generalSubgroup(node.properties().flatMap(SubGroups.Edges.Node.Properties::generalChat).map(Boolean::parseBoolean).orElse(false))
                        .membershipApprovalMode(node.properties().map(SubGroups.Edges.Node.Properties::membershipApprovalModeEnabled).orElse(false))
                        .hiddenSubgroup(node.properties().flatMap(SubGroups.Edges.Node.Properties::hiddenGroup).map(Boolean::parseBoolean).orElse(false))
                        .build())
                .forEach(result::add);
        return Collections.unmodifiableList(result);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyParticipantsJob", exports = "addGroupParticipants",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, GroupParticipantStatus> addGroupParticipants(JidProvider groupProvider, Collection<? extends JidProvider> toAddProvider) {
        var toAdd = Objects.requireNonNull(toAddProvider, "toAdd cannot be null").stream().map(JidProvider::toJid).toList();
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        return modifyGroupParticipants(group, toAdd, "add"); // WAWebGroupModifyParticipantsJob: switch on action -> smax("add", ...)
    }

    /** {@inheritDoc} */
    @Override
    public Map<Jid, GroupParticipantStatus> removeGroupParticipants(JidProvider groupProvider, Collection<? extends JidProvider> toRemoveProvider) {
        var toRemove = Objects.requireNonNull(toRemoveProvider, "toRemove cannot be null").stream().map(JidProvider::toJid).toList();
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        return removeGroupParticipants(group, toRemove, false);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyParticipantsJob", exports = "removeGroupParticipants",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxGroupsRemoveParticipantsRPC", exports = "sendRemoveParticipantsRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, GroupParticipantStatus> removeGroupParticipants(JidProvider groupProvider, Collection<? extends JidProvider> toRemoveProvider,
                                                                    boolean removeLinkedGroups) {
        var toRemove = Objects.requireNonNull(toRemoveProvider, "toRemove cannot be null").stream().map(JidProvider::toJid).toList();
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        if (toRemove.isEmpty()) {
            throw new IllegalArgumentException("toRemove cannot be empty");
        }
        var request = new SmaxGroupsRemoveParticipantsRequest(group, List.copyOf(toRemove), removeLinkedGroups);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsRemoveParticipantsResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Map.of();
            case SmaxGroupsRemoveParticipantsResponse.Success success -> {
                var result = new LinkedHashMap<Jid, GroupParticipantStatus>();
                for (var entry : success.participants()) {
                    var status = entry.rejectionReason()
                            .map(reason -> GroupParticipantStatus.of(reason.errorCode()))
                            .orElse(GroupParticipantStatus.OK);
                    result.put(entry.jid(), status);
                }
                yield Map.copyOf(result);
            }
            case SmaxGroupsRemoveParticipantsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Remove participants rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsRemoveParticipantsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Remove participants server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyParticipantsJob", exports = "promoteGroupParticipants",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void promoteGroupParticipants(JidProvider groupProvider, Collection<? extends JidProvider> toPromoteProvider) {
        var toPromote = Objects.requireNonNull(toPromoteProvider, "toPromote cannot be null").stream().map(JidProvider::toJid).toList();
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        modifyGroupParticipants(group, toPromote, "promote"); // WAWebGroupModifyParticipantsJob: smax("promote", ...)
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyParticipantsJob", exports = "demoteGroupParticipants",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void demoteGroupParticipants(JidProvider groupProvider, Collection<? extends JidProvider> toDemoteProvider) {
        var toDemote = Objects.requireNonNull(toDemoteProvider, "toDemote cannot be null").stream().map(JidProvider::toJid).toList();
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        modifyGroupParticipants(group, toDemote, "demote"); // WAWebGroupModifyParticipantsJob: smax("demote", ...)
    }

    /**
     * Sends a participant-modification IQ to the given group and parses
     * the per-participant response codes.
     *
     * <p>This private helper backs {@link #addGroupParticipants(Jid, Collection)},
     * {@link #promoteGroupParticipants(Jid, Collection)} and
     * {@link #demoteGroupParticipants(Jid, Collection)}. The action name
     * is used as the body child description
     * ({@code "add"}/{@code "promote"}/{@code "demote"}) per WA Web's
     * {@code WASmaxOutGroups*Request} modules. Removal flows through the
     * SMAX-typed {@code WASmaxGroupsRemoveParticipantsRPC} via
     * {@link #removeGroupParticipants(Jid, Collection, boolean)} instead.
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

    //</editor-fold>
    //<editor-fold desc="Stickers">
    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersFavoriteSyncAction", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void favoriteSticker(String stickerHash) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        var mutation = favoriteStickerMutationFactory.getFavoriteStickerMutation(stickerHash, true); // WAWebStickersFavoriteSyncAction: isFavorite = true
        webAppStateService.pushPatches(StickerAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync(["favorite_sticker"], [mutation], ...)
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersFavoriteSyncAction", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unfavoriteSticker(String stickerHash) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        var mutation = favoriteStickerMutationFactory.getFavoriteStickerMutation(stickerHash, false); // WAWebStickersFavoriteSyncAction: isFavorite = false
        webAppStateService.pushPatches(StickerAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersRemoveRecentSyncAction", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void removeRecentSticker(String stickerHash) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        var mutation = removeRecentStickerMutationFactory.getRemoveRecentStickerMutation(stickerHash); // WAWebStickersRemoveRecentSyncAction
        webAppStateService.pushPatches(RemoveRecentStickerAction.COLLECTION_NAME, List.of(mutation));
    }

    //</editor-fold>
    //<editor-fold desc="Polls">
    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPollsSendPollCreationMsgAction", exports = "sendPollCreation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebPollsSendPollCreationMsgAction", exports = "createPollCreationMsgData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public ChatMessageInfo createPoll(PollCreate create) {
        Objects.requireNonNull(create, "create cannot be null");
        var chat = create.chat();
        var question = create.question();
        var options = create.options();
        var selectableCount = create.selectableCount();
        var selfJid = store.jid()
        .orElseThrow(() -> new IllegalArgumentException("Not logged in"));
        var messageSecret = new byte[32];
        ThreadLocalRandom.current().nextBytes(messageSecret);
        var pollOptions = options.stream()
        .map(name -> new PollCreationMessageOptionBuilder().optionName(name).build()) // WAWebPollCreationUtils: {optionName: e}
                .toList();
        var poll = new PollCreationMessageBuilder()
        .name(question) // WAWebPollsSendPollCreationMsgAction: pollName: m
                .options(pollOptions) // WAWebPollsSendPollCreationMsgAction: pollOptions: p
                .selectableOptionsCount(selectableCount) // WAWebPollsSendPollCreationMsgAction: pollSelectableOptionsCount: _
                .encKey(messageSecret)
                .build();
        var messageId = MessageIdGenerator.generate(MessageIdVersion.V2, selfJid); // WAWebMsgKey.newId
        var key = new MessageKeyBuilder()
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
        commitPollsActionsMetric(                chat,
                PollActionType.CREATE_POLL,
                messageInfo.timestamp().orElse(Instant.now()),
                pollOptions.size()
        );
        return messageInfo;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPollsSendVoteMsgAction", exports = "sendVote",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void votePoll(MessageKey pollKey, List<String> selectedOptions) {
        Objects.requireNonNull(pollKey, "pollKey cannot be null");
        Objects.requireNonNull(selectedOptions, "selectedOptions cannot be null");
        var parentJid = pollKey.parentJid() // WAWebPollsSendVoteMsgAction: parentMsgKey.remote
                .orElseThrow(() -> new IllegalArgumentException("pollKey must carry a parentJid"));
        var pollCreationMessage = store.findMessageByKey(pollKey).orElse(null);
        if (!(pollCreationMessage instanceof ChatMessageInfo chatPoll)) {
            throw new IllegalArgumentException("Poll creation message not found in store for key " + pollKey);
        }
        var voterJid = store.jid() // WAWebMsgGetters.getSender(t) on the outgoing vote
                .orElseThrow(() -> new IllegalStateException("votePoll requires a connected user"));
        // WAWebPollVoteEncryptMsgData.encryptPollVoteMsgData -> WAWebPollsVoteEncryption.encryptVote
        var vote = EncMessageFactory.encryptPollVote(selectedOptions, chatPoll, voterJid);
        var pollUpdate = new PollUpdateMessageBuilder() // WAWebPollsSendVoteMsgAction: new PollUpdateMessage(...)
                .pollCreationMessageKey(pollKey) // WAWebPollsSendVoteMsgAction: pollCreationMessageKey
                .vote(vote) // WAWebPollsSendVoteMsgAction: vote: PollEncValue
                .senderTimestampMs(Instant.now()) // WAWebPollsSendVoteMsgAction: senderTimestampMs
                .build();
        messageService.send(parentJid, MessageContainer.of(pollUpdate)); // WAWebSendMsgChatAction.addAndSendMsgToChat
        // WAWebPollsSendVoteMsgAction: y = t.size > 0 ? (p ? CHANGE_VOTE : VOTE) : REMOVE_VOTE;
        //   Cobalt does not maintain a PollVoteCollection keyed by parent+sender,
        //   so the CHANGE_VOTE branch (previous-vote lookup) cannot be distinguished
        //   here; we classify any non-empty selection as VOTE and empty as REMOVE_VOTE.
        var pollAction = selectedOptions.isEmpty()
                ? PollActionType.REMOVE_VOTE
                : PollActionType.VOTE;
        // WAWebPollsSendVoteMsgAction: creationDateInSeconds: e.t, pollOptionsCount: e.pollOptions.length
        var pollCreationTimestamp = chatPoll.timestamp().orElse(Instant.now());
        var pollOptionsCount = 0;
        if (chatPoll.message().content() instanceof PollCreationMessage poll) {
            pollOptionsCount = poll.options().size();
        }
        commitPollsActionsMetric(parentJid, pollAction, pollCreationTimestamp, pollOptionsCount);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPollsSendVoteMsgAction", exports = "sendVote",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void closePoll(MessageKey pollKey) {
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
        messageService.send(parentJid, MessageContainer.of(pollUpdate));
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
     */
    @WhatsAppWebExport(moduleName = "WAWebPollsActionsMetricUtils", exports = "commitPollsActionsMetric",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitPollsActionsMetric(Jid chatJid, PollActionType action,
                                          Instant creationInstant, int optionsCount) {
        var builder = new PollsActionsEventBuilder()
                .pollAction(action)
                .pollOptionsCount(optionsCount)
                .pollCreationDs(pollCreationDsFromInstant(creationInstant))
                .chatType(pollsWamChatType(chatJid));
                if (chatJid.hasGroupOrCommunityServer()) {
            var metadata = store.findChatMetadata(chatJid).orElse(null);
            if (metadata instanceof GroupMetadata group) {
                var participantCount = group.participants().size();
                builder.groupSizeBucket(pollsWamGroupSizeBucket(Math.max(participantCount, 32)));
                var selfJid = store.jid().orElse(null);
                if (selfJid != null) {
                    builder.isAdmin(pollsWamIsAdmin(group, selfJid));
                    }
                // because every poll-action call site in WA Web operates on a chat that also
                // carries a WAM typeOfGroup classification via the shared WAWebGroupType helper.
                builder.typeOfGroup(pollsWamTypeOfGroup(group));
            }
        }
        wamService.commit(builder.build());
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
     * @apiNote {@code WAWebPollsActionsMetricUtils.c} is the truncate-to-day
     * helper used by the {@code pollCreationDs} property.
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
     */
    @WhatsAppWebExport(moduleName = "WAWebGetMessageChatTypeFromWid",
            exports = "getMessageChatTypeFromWid",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static MessageChatType pollsWamChatType(Jid jid) {
        if (jid.hasUserServer()
                || jid.hasLidServer()
                || jid.hasBotServer()
                || jid.hasHostedServer()
                || jid.hasHostedLidServer()) {
            return MessageChatType.INDIVIDUAL;
        }
        if (jid.hasGroupOrCommunityServer()) {
            return MessageChatType.GROUP;
        }
        if (jid.hasBroadcastServer()) {
            return MessageChatType.BROADCAST;
        }
        // (unreachable: isBroadcast above already catches status@broadcast; kept for
        // structural parity with the JS ternary)
        if (jid.isStatusBroadcastAccount()) {
            return MessageChatType.STATUS;
        }
        if (jid.hasNewsletterServer()) {
            return MessageChatType.CHANNEL;
        }
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
     * @apiNote {@code WAWebWamNumberToClientGroupSizeBucket} uses an
     * identical ladder.
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
    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBotWelcomeRequestSync", exports = "getBotWelcomeRequestSetMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendBotWelcomeRequest(JidProvider botJidProvider) {
        var botJid = Objects.requireNonNull(botJidProvider, "botJid cannot be null").toJid();
        var mutation = botWelcomeRequestMutationFactory.getBotWelcomeRequestSetMutation(botJid, true);
        webAppStateService.pushPatches(BotWelcomeRequestAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void renameAiThread(String chatJid, String threadId, String newName) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        Objects.requireNonNull(threadId, "threadId cannot be null");
        Objects.requireNonNull(newName, "newName cannot be null");
        if (newName.isBlank()) {
            throw new IllegalArgumentException("newName cannot be blank");
            }
        var jid = Jid.of(chatJid);
        var mutation = aiThreadRenameMutationFactory.getAiThreadRenameMutation(jid, threadId, newName); // WAWebAiThreadRenameSync
        webAppStateService.pushPatches(AiThreadRenameAction.COLLECTION_NAME, List.of(mutation));
        // Apply locally for eager consistency
        store.putAiThreadTitle(new AiThreadTitleBuilder().threadId(chatJid + "|" + threadId).title(newName).build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteAiThread(String chatJid, String threadId) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        Objects.requireNonNull(threadId, "threadId cannot be null");
        var jid = Jid.of(chatJid);
        var mutation = aiThreadDeleteMutationFactory.getAiThreadDeleteMutation(jid, threadId); // WAWebAiThreadDeleteSync
        webAppStateService.pushPatches(AiThreadDeleteHandler.COLLECTION_NAME, List.of(mutation));
        // Apply locally for eager consistency
        store.removeAiThreadTitle(chatJid + "|" + threadId);
    }

    //</editor-fold>
    //<editor-fold desc="Favorites, notes, and pin-in-chat">
    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAddToFavoritesAction", exports = "addToFavoritesAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void favoriteChat(JidProvider chatProvider) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        var current = new ArrayList<>(store.favoriteChats());
        if (!current.contains(chat)) { // ADAPTED: WA Web client-side dedupes by orderIndex; Cobalt dedupes by JID equality
            current.add(chat);
        }
        var mutation = favoritesMutationFactory.getFavoritesMutation(current, Instant.now()); // WAWebFavoritesSync.getFavoritesMutation
        webAppStateService.pushPatches(FavoritesAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync
        // Apply locally for eager consistency
        store.setFavoriteChats(current);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebRemoveFromFavoritesAction", exports = "removeFromFavoritesAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unfavoriteChat(JidProvider chatProvider) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        var current = new ArrayList<>(store.favoriteChats());
        current.remove(chat);
        var mutation = favoritesMutationFactory.getFavoritesMutation(current, Instant.now());
        webAppStateService.pushPatches(FavoritesAction.COLLECTION_NAME, List.of(mutation));
        store.setFavoriteChats(current);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public String addNoteToChat(JidProvider chatProvider, String noteText) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        Objects.requireNonNull(noteText, "noteText cannot be null");
        var noteId = UUID.randomUUID().toString();
        var mutation = noteEditMutationFactory.getNoteEditMutation(noteId, chat, noteText, false); // WAWebNoteSync
        webAppStateService.pushPatches(NoteEditAction.COLLECTION_NAME, List.of(mutation));
        return noteId;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editNoteOnChat(JidProvider chatProvider, String noteId, String newText) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        Objects.requireNonNull(noteId, "noteId cannot be null");
        Objects.requireNonNull(newText, "newText cannot be null");
        var mutation = noteEditMutationFactory.getNoteEditMutation(noteId, chat, newText, false); // WAWebNoteSync: edit path
        webAppStateService.pushPatches(NoteEditAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteNoteFromChat(JidProvider chatProvider, String noteId) {
        var chat = Objects.requireNonNull(chatProvider, "chat cannot be null").toJid();
        Objects.requireNonNull(noteId, "noteId cannot be null");
        var mutation = noteEditMutationFactory.getNoteEditMutation(noteId, chat, "", true); // WAWebNoteSync: deleted = true
        webAppStateService.pushPatches(NoteEditAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendPinMessageAction", exports = "sendPinInChatMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void pinMessage(MessageKey msgKey) {
        Objects.requireNonNull(msgKey, "msgKey cannot be null");
        var parentJid = msgKey.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("msgKey must carry a parentJid"));
        var pin = new PinInChatMessageBuilder()
        .key(msgKey) // WAWebSendPinMessageAction: key: parentMsgKey
                .type(PinInChatMessage.Type.PIN_FOR_ALL)
                .senderTimestampMs(Instant.now()) // WAWebSendPinMessageAction: senderTimestampMs
                .build();
        messageService.send(parentJid, MessageContainer.of(pin)); // WAWebSendAddonMsgChatAction -> sendMsgRecord
        commitPinInChatMessageSendEvent(parentJid, msgKey, PinInChatType.PIN_FOR_ALL, null);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendPinMessageAction", exports = "sendPinInChatMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void unpinMessage(MessageKey msgKey) {
        Objects.requireNonNull(msgKey, "msgKey cannot be null");
        var parentJid = msgKey.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("msgKey must carry a parentJid"));
        var pin = new PinInChatMessageBuilder()
                .key(msgKey)
                .type(PinInChatMessage.Type.UNPIN_FOR_ALL)
                .senderTimestampMs(Instant.now())
                .build();
        messageService.send(parentJid, MessageContainer.of(pin));
        // Cobalt does not model an active-pin table with a TTL, so timeRemainingToExpirySecs is omitted rather than fabricated.
        commitPinInChatMessageSendEvent(parentJid, msgKey, PinInChatType.UNPIN_FOR_ALL, null);
    }

    /**
     * Commits the {@link PinInChatMessageSendEvent} WAM event for a successful
     * pin or unpin dispatch from {@link #pinMessage(MessageKey)} /
     * {@link #unpinMessage(MessageKey)}.
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
     *   <li>{@code mediaType}: classification of the pinned parent
     *       message via {@link WamService#getWamMediaType(ChatMessageInfo)};
     *   <li>{@code isSelfParentMessage}: whether the parent message was
     *       authored by the local account ({@code fromMe()} on the resolved
     *       {@link ChatMessageInfo}); omitted when the parent cannot be
     *       resolved in the store;
     *   <li>{@code isSelfPin}: always {@code true}; this helper is only
     *       called from the outbound pin/unpin code paths, matching WA Web's
     *       {@code getIsSentByMe(a)} where {@code a} is the just-constructed
     *       {@code fromMe:true} PinInChat message;
     *   <li>{@code pinInChatExpirySecs}: always {@code 0} because
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
     */
    @WhatsAppWebExport(moduleName = "WAWebPinInChatMetricUtils", exports = "logPinInChatMessageSend",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitPinInChatMessageSendEvent(Jid parentJid, MessageKey parentMsgKey,
                                                 PinInChatType pinInChatType,
                                                 Integer timeRemainingToExpirySecs) {
        var builder = new PinInChatMessageSendEventBuilder()
                .pinInChatType(pinInChatType) // WAWebPinInChatMetricUtils: pinInChatType: u(WANullthrows(a.pinMessageType))
                .isSelfPin(Boolean.TRUE)
                .pinInChatExpirySecs(0);
        var isAGroup = parentJid.hasGroupOrCommunityServer(); // WAWebChatGetters.getIsGroup(n)
        builder.isAGroup(isAGroup); // WAWebPinInChatMetricUtils: isAGroup: m
        if (isAGroup) { // WAWebPinInChatMetricUtils: if (m) { var f = WANullthrows(n.groupMetadata); ... }
            var metadata = store.findChatMetadata(parentJid).orElse(null);
            if (metadata != null) {
                var groupTypeClient = pinWamGroupTypeClient(metadata);
                if (groupTypeClient != null) {
                    builder.groupTypeClient(groupTypeClient);
                }
                var selfJid = store.jid().orElse(null);
                if (selfJid != null) {
                    builder.groupRole(pinWamGroupRole(metadata, selfJid));
                    }
            }
        }
        // WAWebPinInChatMetricUtils: mediaType: WAWebWamMsgUtils.getWamMediaType(parentMsg)
        // WAWebPinInChatMetricUtils: isSelfParentMessage: WAWebMsgGetters.getIsSentByMe(parentMsg)
        store.findMessageByKey(parentMsgKey).ifPresent(parentMessage -> {
            if (parentMessage instanceof ChatMessageInfo chatParent) {
                builder.mediaType(wamService.getWamMediaType(chatParent));
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
     *   <li>{@code DEFAULT} maps to {@link GroupTypeClient#REGULAR_GROUP}</li>
     *   <li>{@code LINKED_SUBGROUP} maps to {@link GroupTypeClient#SUB_GROUP}</li>
     *   <li>{@code LINKED_ANNOUNCEMENT_GROUP} maps to {@link GroupTypeClient#DEFAULT_SUB_GROUP}</li>
     *   <li>{@code COMMUNITY} maps to {@link GroupTypeClient#PARENT_GROUP}</li>
     *   <li>{@code LINKED_GENERAL_GROUP} maps to {@link GroupTypeClient#SUB_GROUP}</li>
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
     * the same identity check WA Web's
     * {@code WAWebGetUserRole.getUserRole} uses.
     *
     * @param metadata the chat metadata to inspect; never {@code null}
     * @param selfJid  the logged-in account's JID; never {@code null}
     * @return {@link GroupRoleType#ADMIN} when the local account's
     *         participant entry has a non-empty rank
     *         ({@link GroupPartipantRole#ADMIN} or
     *         {@link GroupPartipantRole#FOUNDER}); {@link GroupRoleType#MEMBER}
     *         otherwise
     */
    private static GroupRoleType pinWamGroupRole(ChatMetadata metadata, Jid selfJid) {
        var iAmAdmin = metadata.participants().stream()
                .filter(participant -> Objects.equals(participant.userJid().toUserJid(), selfJid.toUserJid()))
                .anyMatch(participant -> participant.rank().isPresent()); // ADMIN / FOUNDER -> true; USER (rank == null) -> false
        return iAmAdmin ? GroupRoleType.ADMIN : GroupRoleType.MEMBER;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editLocale(String locale) {
        Objects.requireNonNull(locale, "locale cannot be null");
        var mutation = localeSettingMutationFactory.getLocaleMutation(Instant.now(), locale); // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation
        webAppStateService.pushPatches(LocaleSetting.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync([], [mutation], ...)
        // Apply locally for eager consistency
        store.setLocale(locale);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDisableLinkPreviewsSync", exports = "sendMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editDisableLinkPreviews(boolean disabled) {
        var mutation = disableLinkPreviewsMutationFactory.getDisableLinkPreviewsMutation(Instant.now(), disabled); // WAWebDisableLinkPreviewsSync.getMutation
        webAppStateService.pushPatches(PrivacySettingDisableLinkPreviewsAction.COLLECTION_NAME, List.of(mutation));
        // Apply locally for eager consistency
        store.setDisableLinkPreviews(disabled);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editTwentyFourHourFormat(boolean twentyFourHourFormat) {
        var mutation = timeFormatMutationFactory.getTimeFormatMutation(Instant.now(), twentyFourHourFormat); // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation
        webAppStateService.pushPatches(TimeFormatAction.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync([], [mutation], ...)
        // Apply locally for eager consistency
        store.setTwentyFourHourFormat(twentyFourHourFormat);
    }

    /** {@inheritDoc} */
    @Override
    public void editAIFeaturesEnabled(boolean enabled) {
        var status = enabled
                ? MaibaAIFeaturesControlAction.MaibaAIFeatureStatus.ENABLED
                : MaibaAIFeaturesControlAction.MaibaAIFeatureStatus.DISABLED;
        var mutation = maibaAIFeaturesControlMutationFactory.getMaibaAiFeatureStatusMutation(Instant.now(), status); // NO_WA_BASIS
        webAppStateService.pushPatches(MaibaAIFeaturesControlAction.COLLECTION_NAME, List.of(mutation));
        store.setAiBusinessAgentStatus(status); // NO_WA_BASIS: apply locally for eager consistency
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editUnarchiveChatsOnNewMessage(boolean unarchive) {
        var mutation = unarchiveChatsSettingMutationFactory.getUnarchiveChatsMutation(Instant.now(), unarchive); // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation
        webAppStateService.pushPatches(UnarchiveChatsSetting.COLLECTION_NAME, List.of(mutation)); // WAWebSyncdCoreApi.lockForSync([], [mutation], ...)
        store.setUnarchiveChats(unarchive); // ADAPTED: apply locally for eager consistency
    }

    /** {@inheritDoc} */
    @Override
    public void editNotificationActivity(NotificationActivitySettingAction.NotificationActivitySetting setting) {
        Objects.requireNonNull(setting, "setting cannot be null");
        var mutation = notificationActivitySettingMutationFactory.getNotificationActivityMutation(Instant.now(), setting); // NO_WA_BASIS
        webAppStateService.pushPatches(SyncPatchType.REGULAR, List.of(mutation));
        store.setNotificationActivitySetting(setting); // NO_WA_BASIS: apply locally for eager consistency
    }

    //</editor-fold>
    //<editor-fold desc="Smax: biz / account / mdcompanion / message / status">

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBizCtwaAdAccountGetAccessTokenAndSessionCookiesRPC",
            exports = "sendGetAccessTokenAndSessionCookiesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<CtwaAccessTokenSession> queryAccessTokenAndSessionCookies(String code, JidProvider fromUserJidProvider) {
        var fromUserJid = Objects.requireNonNull(fromUserJidProvider, "fromUserJid cannot be null").toJid();
        var request = new SmaxGetAccessTokenAndSessionCookiesRequest(code, fromUserJid);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGetAccessTokenAndSessionCookiesResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxGetAccessTokenAndSessionCookiesResponse.Success success -> Optional.of(toCtwaAccessTokenSession(success));
            case SmaxGetAccessTokenAndSessionCookiesResponse.TooManyAttempts _ ->
                    throw new WhatsAppServerRuntimeException("Too many attempts on access-token recovery");
            case SmaxGetAccessTokenAndSessionCookiesResponse.IncorrectNonce _ ->
                    throw new WhatsAppServerRuntimeException("Incorrect nonce on access-token recovery");
            case SmaxGetAccessTokenAndSessionCookiesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Access-token recovery rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGetAccessTokenAndSessionCookiesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Access-token recovery server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /**
     * Maps a {@link SmaxGetAccessTokenAndSessionCookiesResponse.Success}
     * onto its public {@link CtwaAccessTokenSession} domain projection.
     *
     * @param success the parsed wire-format success reply; never
     *                {@code null}
     * @return the populated domain bundle; never {@code null}
     */
    private static CtwaAccessTokenSession toCtwaAccessTokenSession(SmaxGetAccessTokenAndSessionCookiesResponse.Success success) {
        var strength = success.tokenType().map(t -> switch (t) {
            case STRONG -> CtwaAdTokenStrength.STRONG;
            case WEAK -> CtwaAdTokenStrength.WEAK;
        }).orElse(null);
        return new CtwaAccessTokenSessionBuilder()
                .accessToken(success.accessToken())
                .sessionCookies(success.sessionCookies())
                .businessPersonId(success.businessPersonId())
                .tokenStrength(strength)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBizLinkingGetAccountNonceRPC",
            exports = "sendGetAccountNonceRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<String> queryAccountNonce(String identifierScope) {
        var request = new SmaxGetAccountNonceRequest(identifierScope);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGetAccountNonceResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxGetAccountNonceResponse.Success success -> Optional.of(success.nonce());
            case SmaxGetAccountNonceResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Account-nonce request rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null) + ", tosVersion=" + clientError.tosVersion().orElse(null));
            case SmaxGetAccountNonceResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Account-nonce server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBizMarketingMessageGetBusinessEligibilityRPC",
            exports = "sendGetBusinessEligibilityRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<BusinessEligibility> queryBusinessEligibility(boolean featuresMetaVerified, boolean featuresMarketingMessages, boolean featuresGenai) {
        var request = new SmaxGetBusinessEligibilityRequest(
                featuresMetaVerified ? "1" : "0",
                featuresMarketingMessages ? "1" : "0",
                featuresGenai ? "1" : "0");
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGetBusinessEligibilityResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxGetBusinessEligibilityResponse.Success success -> Optional.of(toBusinessEligibility(success));
            case SmaxGetBusinessEligibilityResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Business-eligibility query rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGetBusinessEligibilityResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Business-eligibility query server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /**
     * Maps a {@link SmaxGetBusinessEligibilityResponse.Success} onto its
     * public {@link BusinessEligibility} domain projection.
     *
     * @param success the parsed wire-format success reply; never
     *                {@code null}
     * @return the populated domain bundle; never {@code null}
     */
    private static BusinessEligibility toBusinessEligibility(SmaxGetBusinessEligibilityResponse.Success success) {
        var metaVerified = success.metaVerified()
                .map(mv -> new BusinessMetaVerifiedEligibilityBuilder()
                        .eligible(mv.status() == SmaxGetBusinessEligibilityFailSuccessStatus.SUCCESS)
                        .shouldShowPrivacyInterstitialToNewUsers(mv.shouldShowPrivacyInterstitialToNewUsers()
                                .map(f -> f == SmaxGetBusinessEligibilityFalseTrueFlag.TRUE)
                                .orElse(null))
                        .additionalParams(mv.additionalParams().orElse(null))
                        .build())
                .orElse(null);
        var marketingMessages = success.marketingMessages()
                .map(mm -> new BusinessMarketingMessagesEligibilityBuilder()
                        .status(switch (mm.status()) {
                            case FAIL -> BusinessMarketingMessagesStatus.FAIL;
                            case PAUSED -> BusinessMarketingMessagesStatus.PAUSED;
                            case SUCCESS -> BusinessMarketingMessagesStatus.SUCCESS;
                            case WARNING -> BusinessMarketingMessagesStatus.WARNING;
                        })
                        .expiration(mm.expiration().isPresent() ? mm.expiration().getAsInt() : null)
                        .build())
                .orElse(null);
        var genai = success.genai()
                .map(g -> new BusinessGenaiEligibilityBuilder()
                        .eligible(g.status() == SmaxGetBusinessEligibilityFailSuccessStatus.SUCCESS)
                        .build())
                .orElse(null);
        return new BusinessEligibilityBuilder()
                .metaVerified(metaVerified)
                .marketingMessages(marketingMessages)
                .genai(genai)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBizLinkingGetLinkedAccountsRPC",
            exports = "sendGetLinkedAccountsRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<BusinessLinkedAccounts> queryLinkedAccounts() {
        var request = new SmaxGetLinkedAccountsRequest();
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGetLinkedAccountsResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxGetLinkedAccountsResponse.Success success -> Optional.of(toBusinessLinkedAccounts(success));
            case SmaxGetLinkedAccountsResponse.Forbidden _ ->
                    throw new WhatsAppServerRuntimeException("Linked-accounts query forbidden");
            case SmaxGetLinkedAccountsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Linked-accounts query rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGetLinkedAccountsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Linked-accounts query server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /**
     * Maps a {@link SmaxGetLinkedAccountsResponse.Success} onto its
     * public {@link BusinessLinkedAccounts} domain projection.
     *
     * @param success the parsed wire-format success reply; never
     *                {@code null}
     * @return the populated domain bundle; never {@code null}
     */
    private static BusinessLinkedAccounts toBusinessLinkedAccounts(SmaxGetLinkedAccountsResponse.Success success) {
        var fbPage = success.fbPage()
                .map(p -> new BusinessLinkedFacebookPageBuilder()
                        .id(p.id())
                        .displayName(p.displayName())
                        .hasCreatedAd(p.adStatus().hasCreatedAd() == SmaxGetLinkedAccountsFalseTrueFlag.TRUE)
                        .hasActiveCtwaAd(p.adStatus().hasActiveCtwaAd() == SmaxGetLinkedAccountsFalseTrueFlag.TRUE)
                        .profileSyncDisabled(p.profileSyncState()
                                .map(s -> s == SmaxGetLinkedAccountsDisableImportState.DISABLE)
                                .orElse(null))
                        .whatsAppAsPageButtonEnabled(p.whatsAppAsPageButtonState() == SmaxGetLinkedAccountsOffOnState.ON)
                        .profilePictureUrl(p.profilePictureUrl().orElse(null))
                        .profilePictureBytes(p.profilePictureBytes().orElse(null))
                        .showOnProfile(p.showOnProfile()
                                .map(f -> f == SmaxGetLinkedAccountsFalseTrueFlag.TRUE)
                                .orElse(null))
                        .build())
                .orElse(null);
        var fbBiz = success.fbBiz()
                .map(b -> {
                    var catalog = b.catalog().orElse(null);
                    return new BusinessLinkedFacebookBusinessBuilder()
                            .id(b.id())
                            .displayName(b.displayName())
                            .catalogId(catalog == null ? null : catalog.id())
                            .catalogImportDisabled(catalog == null
                                    ? null
                                    : catalog.state() == SmaxGetLinkedAccountsDisableImportState.DISABLE)
                            .build();
                })
                .orElse(null);
        var igProfessional = success.igProfessional()
                .map(ig -> new BusinessLinkedInstagramProfessionalBuilder()
                        .igHandle(ig.igHandle())
                        .profilePictureUrl(ig.profilePictureUrl().orElse(null))
                        .profilePictureBytes(ig.profilePictureBytes().orElse(null))
                        .displayName(ig.displayName().orElse(null))
                        .showOnProfile(ig.showOnProfile()
                                .map(f -> f == SmaxGetLinkedAccountsFalseTrueFlag.TRUE)
                                .orElse(null))
                        .build())
                .orElse(null);
        var whatsAppAdIdentity = success.whatsAppAdIdentity()
                .map(ai -> new BusinessLinkedWhatsAppAdIdentityBuilder()
                        .id(ai.id())
                        .hasCreatedAd(ai.adStatus().hasCreatedAd() == SmaxGetLinkedAccountsFalseTrueFlag.TRUE)
                        .hasActiveCtwaAd(ai.adStatus().hasActiveCtwaAd() == SmaxGetLinkedAccountsFalseTrueFlag.TRUE)
                        .build())
                .orElse(null);
        return new BusinessLinkedAccountsBuilder()
                .facebookPage(fbPage)
                .facebookBusiness(fbBiz)
                .instagramProfessional(igProfessional)
                .whatsAppAdIdentity(whatsAppAdIdentity)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBizSettingsGetPrivacySettingRPC",
            exports = "sendGetPrivacySettingRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<BusinessDataSharingConsent> queryBusinessPrivacySetting() {
        // WASmaxOutBizSettingsGetPrivacySettingRequest.makeGetPrivacySettingRequest
        var request = new SmaxGetPrivacySettingRequest();
        var requestNode = request.toNode();
        // WAComms.sendSmaxStanza: dispatches the SMAX IQ and awaits the relay reply
        var response = sendNode(requestNode);
        // Folds parseGetPrivacySettingResponseSuccess + parseGetPrivacySettingResponseError
        var parsed = SmaxGetPrivacySettingResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            // ADAPTED: WASmaxParsingFailure.SmaxParsingFailure throw collapsed to Optional.empty per Cobalt SMAX-RPC convention
            case null -> Optional.empty();
            // {name:"GetPrivacySettingResponseSuccess", value:r.value}
            case SmaxGetPrivacySettingResponse.Success success ->
                    BusinessDataSharingConsent.ofWire(success.dataSharingConsent());
            // ADAPTED: {name:"GetPrivacySettingResponseError", value:a.value} re-thrown as WhatsAppServerRuntimeException (4xx arm)
            case SmaxGetPrivacySettingResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Business privacy-setting query rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            // ADAPTED: {name:"GetPrivacySettingResponseError", value:a.value} re-thrown as WhatsAppServerRuntimeException (5xx arm)
            case SmaxGetPrivacySettingResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Business privacy-setting query server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBizSettingsSetPrivacySettingRPC",
            exports = "sendSetPrivacySettingRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void editBusinessPrivacySetting(BusinessDataSharingConsent dataSharingConsent) {
        // WASmaxOutBizSettingsSetPrivacySettingRequest.makeSetPrivacySettingRequest
        var request = new SmaxSetPrivacySettingRequest(dataSharingConsent == null ? null : dataSharingConsent.wireValue());
        var requestNode = request.toNode();
        // WAComms.sendSmaxStanza: dispatches the SMAX IQ and awaits the relay reply
        var response = sendNode(requestNode);
        // Folds parseSetPrivacySettingResponseSuccess + parseSetPrivacySettingResponseError
        var parsed = SmaxSetPrivacySettingResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            // ADAPTED: WASmaxParsingFailure.SmaxParsingFailure throw collapsed to no-op per Cobalt SMAX-RPC convention
            case null -> {
            }
            // {name:"SetPrivacySettingResponseSuccess", value:a.value}
            case SmaxSetPrivacySettingResponse.Success _ -> {
            }
            // ADAPTED: {name:"SetPrivacySettingResponseError", value:i.value} re-thrown as WhatsAppServerRuntimeException (4xx arm)
            case SmaxSetPrivacySettingResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Business privacy-setting change rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            // ADAPTED: {name:"SetPrivacySettingResponseError", value:i.value} re-thrown as WhatsAppServerRuntimeException (5xx arm)
            case SmaxSetPrivacySettingResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Business privacy-setting change server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBizMsgUserFeedbackUpdatePreferenceRPC",
            exports = "sendUpdatePreferenceRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void updateMessageFeedbackPreference(MessageFeedbackAction action, JidProvider jidProvider, String feedback) {
        var jid = Objects.requireNonNull(jidProvider, "jid cannot be null").toJid();
        Objects.requireNonNull(action, "action cannot be null");
        var request = new SmaxUpdatePreferenceRequest(action.wireValue(), jid, feedback);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxUpdatePreferenceResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> {
            }
            case SmaxUpdatePreferenceResponse.Success _ -> {
            }
            case SmaxUpdatePreferenceResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Message-feedback update rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxUpdatePreferenceResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Message-feedback update server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxSmbMeteredMessagingAccountGetSMBMeteredMessagingCheckoutRPC",
            exports = "sendGetSMBMeteredMessagingCheckoutRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<BusinessMeteredMessagingCheckout> queryMeteredMessagingCheckout(List<? extends JidProvider> participantsProvider, boolean useAdAccount, boolean skipDedupe, String offerId, List<BusinessMeteredMessagingPendingCampaign> pendingCampaigns) {
        var participants = Objects.requireNonNull(participantsProvider, "participants cannot be null").stream().map(JidProvider::toJid).toList();
        // Translate the public domain pending-campaign list onto the wire-format SmaxGetSMBMeteredMessagingCheckoutPendingCampaign carriers
        List<SmaxGetSMBMeteredMessagingCheckoutPendingCampaign> wireCampaigns = null;
        if (pendingCampaigns != null) {
            wireCampaigns = new ArrayList<>(pendingCampaigns.size());
            for (var campaign : pendingCampaigns) {
                wireCampaigns.add(new SmaxGetSMBMeteredMessagingCheckoutPendingCampaign(
                        campaign.freeReservedMsgs(),
                        campaign.sendTimestamp().isPresent() ? campaign.sendTimestamp().getAsInt() : null));
            }
        }
        var request = new SmaxGetSMBMeteredMessagingCheckoutRequest(participants, useAdAccount, skipDedupe, offerId, wireCampaigns);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGetSMBMeteredMessagingCheckoutResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxGetSMBMeteredMessagingCheckoutResponse.Success success -> Optional.of(toSmbMeteredMessagingCheckout(success));
            case SmaxGetSMBMeteredMessagingCheckoutResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Metered-messaging checkout query rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGetSMBMeteredMessagingCheckoutResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Metered-messaging checkout server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    public Optional<BusinessMeteredMessagingCheckout> queryMeteredMessagingCheckout(List<? extends JidProvider> participants) {
        return queryMeteredMessagingCheckout(participants, false, false, null, null);
    }

    /**
     * Maps a {@link SmaxGetSMBMeteredMessagingCheckoutResponse.Success}
     * onto its public {@link BusinessMeteredMessagingCheckout} domain
     * projection.
     *
     * @param success the parsed wire-format success reply; never
     *                {@code null}
     * @return the populated domain bundle; never {@code null}
     */
    private static BusinessMeteredMessagingCheckout toSmbMeteredMessagingCheckout(SmaxGetSMBMeteredMessagingCheckoutResponse.Success success) {
        var srcCost = success.cost();
        var cost = new BusinessMeteredMessagingCostBuilder()
                .beforeTax(srcCost.beforeTax())
                .tax(srcCost.tax())
                .offset(srcCost.offset())
                .currency(srcCost.currency())
                .base(srcCost.base().isPresent() ? srcCost.base().getAsInt() : null)
                .baseFormatted(srcCost.baseFormatted().orElse(null))
                .discountPercent(srcCost.discountPercent().isPresent() ? srcCost.discountPercent().getAsInt() : null)
                .beforeDiscount(srcCost.beforeDiscount().isPresent() ? srcCost.beforeDiscount().getAsInt() : null)
                .beforeDiscountFormatted(srcCost.beforeDiscountFormatted().orElse(null))
                .build();
        var srcBalance = success.accountBalance();
        var balance = new BusinessMeteredMessagingAccountBalanceBuilder()
                .billing(srcBalance.billing())
                .available(srcBalance.available())
                .offset(srcBalance.offset())
                .build();
        var quota = success.quota()
                .map(q -> new BusinessMeteredMessagingQuotaBuilder()
                        .remaining(q.remaining())
                        .totalMonthly(q.totalMonthly())
                        .singleCredits(q.singleCredits().isPresent() ? q.singleCredits().getAsInt() : null)
                        .totalAvailableCredits(q.totalAvailableCredits().isPresent() ? q.totalAvailableCredits().getAsInt() : null)
                        .build())
                .orElse(null);
        return new BusinessMeteredMessagingCheckoutBuilder()
                .cost(cost)
                .integrityEligible(success.integrityIsEligible() == SmaxGetSMBMeteredMessagingCheckoutIntegrityEligibility.TRUE)
                .accountBalance(balance)
                .quota(quota)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBizAccessTokenRequestSilentNonceRPC",
            exports = "sendRequestSilentNonceRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<CtwaSilentNonceResult> querySilentNonce(JidProvider fromUserJidProvider) {
        var fromUserJid = Objects.requireNonNull(fromUserJidProvider, "fromUserJid cannot be null").toJid();
        var request = new SmaxRequestSilentNonceRequest(fromUserJid);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxRequestSilentNonceResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxRequestSilentNonceResponse.Success _ ->
                    Optional.of(new CtwaSilentNonceResult.Issued());
            case SmaxRequestSilentNonceResponse.RecoveryRequired recoveryRequired ->
                    Optional.of(new CtwaSilentNonceResult.RecoveryRequired(recoveryRequired.email()));
            case SmaxRequestSilentNonceResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Silent-nonce request rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxRequestSilentNonceResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Silent-nonce request server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBizCtwaAdAccountSendAccountRecoveryNonceRPC",
            exports = "sendSendAccountRecoveryNonceRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean sendAccountRecoveryNonce(JidProvider fromUserJidProvider) {
        var fromUserJid = Objects.requireNonNull(fromUserJidProvider, "fromUserJid cannot be null").toJid();
        var request = new SmaxSendAccountRecoveryNonceRequest(fromUserJid);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxSendAccountRecoveryNonceResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> false;
            case SmaxSendAccountRecoveryNonceResponse.Success success ->
                    success.status() == SmaxSendAccountRecoveryNonceStatus.SUCCESS;
            case SmaxSendAccountRecoveryNonceResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Account-recovery-nonce dispatch rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxSendAccountRecoveryNonceResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Account-recovery-nonce dispatch server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBizCtwaNativeAdUploadAdMediaRPC",
            exports = "sendUploadAdMediaRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void uploadAdMedia(CtwaAdMediaEntry media, List<CtwaAdMediaEntry> mediaList) {
        Objects.requireNonNull(mediaList, "mediaList cannot be null");
        var wirePrimary = media == null ? null : toSmaxAdMediaEntry(media);
        var wireList = new ArrayList<SmaxUploadAdMediaMediaEntry>(mediaList.size());
        for (var entry : mediaList) {
            wireList.add(toSmaxAdMediaEntry(entry));
        }
        var request = new SmaxUploadAdMediaRequest(wirePrimary, wireList);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxUploadAdMediaResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> {
            }
            case SmaxUploadAdMediaResponse.Success _ -> {
            }
            case SmaxUploadAdMediaResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Ad-media upload rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxUploadAdMediaResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Ad-media upload server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
    }

    /**
     * Translates a public {@link CtwaAdMediaEntry} onto its wire-format
     * {@link SmaxUploadAdMediaMediaEntry} carrier.
     *
     * @param entry the public domain entry; never {@code null}
     * @return the populated wire-format carrier; never {@code null}
     */
    private static SmaxUploadAdMediaMediaEntry toSmaxAdMediaEntry(CtwaAdMediaEntry entry) {
        var wireType = switch (entry.type()) {
            case IMAGE -> SmaxUploadAdMediaMediaType.IMAGE;
            case VIDEO -> SmaxUploadAdMediaMediaType.VIDEO;
        };
        return new SmaxUploadAdMediaMediaEntry(entry.id(), wireType);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxAccountSetPaymentsTOSv3RPC",
            exports = "sendSetPaymentsTOSv3RPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void editPaymentsTosV3Acceptance(int acceptPayTosVersion, PaymentsTosV3ConsumerVariant variant) {
        Objects.requireNonNull(variant, "variant cannot be null");
        var wireVariant = switch (variant) {
            case PaymentsTosV3ConsumerVariant.BrConsumer br ->
                    new SmaxAccountSetPaymentsTOSv3ConsumerVariant.BrConsumer(br.additionalNotices());
            case PaymentsTosV3ConsumerVariant.UpiConsumer upi ->
                    new SmaxAccountSetPaymentsTOSv3ConsumerVariant.UpiConsumer(upi.additionalNotices());
        };
        var request = new SmaxAccountSetPaymentsTOSv3Request(acceptPayTosVersion, wireVariant);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxAccountSetPaymentsTOSv3Response.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> {
            }
            case SmaxAccountSetPaymentsTOSv3Response.Success _ -> {
            }
            case SmaxAccountSetPaymentsTOSv3Response.Error error ->
                    throw new WhatsAppServerRuntimeException("Payments TOS v3 acceptance rejected: code=" + error.errorCode() + ", text=" + error.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBrPaymentCreateCustomPaymentMethodRPC",
            exports = "sendCreateCustomPaymentMethodRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public BrazilCustomPaymentMethod createBrazilCustomPaymentMethod(BrazilCustomPaymentMethodCreate create) {
        Objects.requireNonNull(create, "create cannot be null");
        var accountDeviceId = create.accountDeviceId().orElse(null);
        var customPaymentMethodType = create.customPaymentMethodType().orElse(null);
        var customPaymentMethodUpdate = create.customPaymentMethodUpdate().orElse(null);
        var customPaymentMethodFlow = create.customPaymentMethodFlow().orElse(null);
        var metadata = create.metadata().stream()
                .collect(Collectors.toMap(
                        BrazilCustomPaymentMethodMetadataEntry::key,
                        BrazilCustomPaymentMethodMetadataEntry::value,
                        (a, _) -> a,
                        LinkedHashMap::new));
        var request = new SmaxBrPaymentCreateCustomPaymentMethodRequest(accountDeviceId, customPaymentMethodType, customPaymentMethodUpdate, customPaymentMethodFlow, metadata);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxBrPaymentCreateCustomPaymentMethodResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("BR custom payment method creation: unparseable response: " + response);
            case SmaxBrPaymentCreateCustomPaymentMethodResponse.Success success ->
                    new BrazilCustomPaymentMethodBuilder()
                            .credentialId(success.credentialId())
                            .customPaymentMethodType(success.customPaymentMethodType())
                            .country(success.country().orElse(null))
                            .created(success.created().orElse(null))
                            .flow(success.flow().orElse(null))
                            .p2pEligible(success.p2pEligible().orElse(null))
                            .p2mEligible(success.p2mEligible().orElse(null))
                            .metadata(success.metadata())
                            .build();
            case SmaxBrPaymentCreateCustomPaymentMethodResponse.IqError iqError ->
                    throw new WhatsAppServerRuntimeException("BR custom payment method creation rejected: code=" + iqError.errorCode() + ", text=" + iqError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxMessagePublishNewsletterRPC",
            exports = "sendNewsletterRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterPublishAck publishNewsletterMessage(JidProvider newsletterJidProvider, NewsletterPublishMessageRequest request) {
        var newsletterJid = Objects.requireNonNull(newsletterJidProvider, "newsletterJid cannot be null").toJid();
        Objects.requireNonNull(request, "request cannot be null");
        var plaintext = NewsletterStanza.buildPlaintext(request.payloadBytes());
        var targetServerId = request.targetMessageServerId().orElse(null);
        SmaxMessagePublishNewsletterPayload payload;
        if (targetServerId != null) {
            payload = new SmaxMessagePublishNewsletterPayload.WithServerId(
                    request.stanzaId(), targetServerId, plaintext);
        } else {
            // WASmaxOutMessagePublishMsgMetaOriginMixin: <meta origin="<tag>"/>
            var originMarker = request.originTag()
                    .map(tag -> new NodeBuilder().description("meta").attribute("origin", tag).build())
                    .orElse(null);
            // WASmaxOutMessagePublishSenderContentTypeMediaRCATMixin: <plaintext mediatype="url" content_id="<id>"/>
            var mediaSenderTag = request.mediaContentId()
                    .map(id -> new NodeBuilder().description("plaintext").attribute("mediatype", "url").attribute("content_id", id).build())
                    .orElse(null);
            payload = new SmaxMessagePublishNewsletterPayload.WithClientIdOnly(
                    request.stanzaId(), originMarker, mediaSenderTag, plaintext);
        }
        var smaxRequest = new SmaxMessagePublishNewsletterRequest(newsletterJid, payload);
        var requestNode = smaxRequest.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxMessagePublishNewsletterResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null ->
                    throw new WhatsAppServerRuntimeException("Newsletter publish ack did not parse: response=" + response);
            case SmaxMessagePublishNewsletterResponse.Success success -> new NewsletterPublishAckBuilder()
                    .serverId(success.serverId().orElse(null))
                    .responseServerId(success.responseServerId().orElse(null))
                    .timestamp(Instant.ofEpochSecond(success.timestamp()))
                    .build();
            case SmaxMessagePublishNewsletterResponse.Negative negative ->
                    throw new WhatsAppServerRuntimeException("Newsletter publish negative ack: error=" + negative.errorCode() + ", appError=" + negative.applicationError().orElse(null) + ", backoff=" + negative.backoff().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxStatusPublishPostNewsletterStatusRPC",
            exports = "sendPostNewsletterStatusRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterPublishAck publishNewsletterStatus(JidProvider newsletterJidProvider, NewsletterPublishStatusRequest request) {
        var newsletterJid = Objects.requireNonNull(newsletterJidProvider, "newsletterJid cannot be null").toJid();
        Objects.requireNonNull(request, "request cannot be null");
        var plaintext = NewsletterStanza.buildPlaintext(request.payloadBytes());
        var targetServerId = request.targetStatusServerId().orElse(null);
        var payload = targetServerId != null
                ? new SmaxStatusPublishPostNewsletterStatusPayload.WithServerId(request.stanzaId(), targetServerId, plaintext)
                : new SmaxStatusPublishPostNewsletterStatusPayload.WithClientIdOnly(request.stanzaId(), plaintext);
        var smaxRequest = new SmaxStatusPublishPostNewsletterStatusRequest(newsletterJid, payload);
        var requestNode = smaxRequest.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxStatusPublishPostNewsletterStatusResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null ->
                    throw new WhatsAppServerRuntimeException("Newsletter status publish ack did not parse: response=" + response);
            case SmaxStatusPublishPostNewsletterStatusResponse.Success success -> new NewsletterPublishAckBuilder()
                    .serverId(success.serverId().orElse(null))
                    .timestamp(Instant.ofEpochSecond(success.timestamp()))
                    .build();
            case SmaxStatusPublishPostNewsletterStatusResponse.Negative negative ->
                    throw new WhatsAppServerRuntimeException("Newsletter status publish negative ack: error=" + negative.errorCode() + ", appError=" + negative.applicationError().orElse(null) + ", backoff=" + negative.backoff().orElse(null));
        };
    }

    //</editor-fold>
    //<editor-fold desc="Typed legacy IQ wrappers">

    /** {@inheritDoc} */
    @Override
    public Node sendNode(IqOperation.Request request) {
        Objects.requireNonNull(request, "request cannot be null");
        return sendNode(request.toNode());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryProductListCatalogJob",
            exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<BusinessProduct> queryBusinessCatalogProducts(JidProvider catalogJidProvider,
                                                              List<String> productIds,
                                                              int width, int height,
                                                              String directConnectionEncryptedInfo) {
        var catalogJid = Objects.requireNonNull(catalogJidProvider, "catalogJid cannot be null").toJid();
        var request = new IqQueryProductListCatalogRequest(catalogJid, productIds, width, height, directConnectionEncryptedInfo);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqQueryProductListCatalogResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqQueryProductListCatalogResponse.Success success -> success.products()
                    .stream()
                    .map(DefaultWhatsAppClient::toBusinessProduct)
                    .toList();
            case IqQueryProductListCatalogResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query biz product list catalog rejected: " + clientError.errorCode());
            case IqQueryProductListCatalogResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query biz product list catalog server error: " + serverError.errorCode());
            case null -> List.of();
        };
    }

    /** {@inheritDoc} */
    @Override
    public List<BusinessProduct> queryBusinessCatalogProducts(JidProvider catalogJid, List<String> productIds, int width, int height) {
        return queryBusinessCatalogProducts(catalogJid, productIds, width, height, null);
    }

    /**
     * Projects a typed
     * {@link IqQueryProductListCatalogResponse.Success.Product} entry
     * onto Cobalt's caller-friendly {@link BusinessProduct} model,
     * promoting opaque URL strings to {@link URI} and the
     * {@code availability} attribute to the {@link BusinessItemAvailability}
     * enum.
     *
     * @param product the typed product entry; never {@code null}
     * @return the converted model entry; never {@code null}
     */
    private static BusinessProduct toBusinessProduct(IqQueryProductListCatalogResponse.Success.Product product) {
        if (product.invalid()) {
            return new BusinessProductBuilder()
                    .id(product.id())
                    .invalid(true)
                    .build();
        }
        var images = product.images()
                .stream()
                .map(image -> new BusinessProductImageBuilder()
                        .id(image.id())
                        .requestedUri(URI.create(image.requestedUrl()))
                        .fullUri(URI.create(image.fullUrl()))
                        .build())
                .toList();
        var videos = product.videos()
                .stream()
                .map(video -> new BusinessProductVideoBuilder()
                        .id(video.id())
                        .videoUri(URI.create(video.originalVideoUrl()))
                        .thumbnailUri(URI.create(video.thumbnailUrl()))
                        .build())
                .toList();
        var salePrice = product.salePrice()
                .map(sp -> new BusinessProductSalePriceBuilder()
                        .price(sp.price())
                        .startDate(sp.startDate().orElse(null))
                        .endDate(sp.endDate().orElse(null))
                        .build())
                .orElse(null);
        var compliance = product.complianceInfo()
                .map(DefaultWhatsAppClient::toBusinessProductCompliance)
                .orElse(null);
        var availability = product.availability()
                .flatMap(BusinessItemAvailability::ofName)
                .orElse(null);
        var uri = product.url().map(URI::create).orElse(null);
        var signedShimmedUri = product.signedShimmedUrl().map(URI::create).orElse(null);
        return new BusinessProductBuilder()
                .id(product.id())
                .invalid(false)
                .name(product.name().orElse(null))
                .description(product.description().orElse(null))
                .uri(uri)
                .retailerId(product.retailerId().orElse(null))
                .availability(availability)
                .maxAvailable(product.maxAvailable())
                .currency(product.currency().orElse(null))
                .price(product.price().orElse(null))
                .hidden(product.hidden())
                .sanctioned(product.sanctioned())
                .checkmark(product.checkmark())
                .moderationStatus(product.whatsappStatus().orElse(null))
                .canAppeal(product.canAppeal())
                .images(images)
                .videos(videos)
                .salePrice(salePrice)
                .compliance(compliance)
                .signedShimmedUri(signedShimmedUri)
                .complianceCategory(product.complianceCategory().orElse(null))
                .build();
    }

    /**
     * Projects the typed
     * {@link IqQueryProductListCatalogResponse.Success.ComplianceInfo}
     * block onto Cobalt's caller-friendly
     * {@link BusinessProductCompliance} model, including the nested
     * importer address.
     *
     * @param info the typed compliance block; never {@code null}
     * @return the converted model block; never {@code null}
     */
    private static BusinessProductCompliance toBusinessProductCompliance(
            IqQueryProductListCatalogResponse.Success.ComplianceInfo info) {
        var address = info.importerAddress()
                .map(addr -> new BusinessProductImporterAddressBuilder()
                        .street1(addr.street1())
                        .street2(addr.street2().orElse(null))
                        .postalCode(addr.postalCode().orElse(null))
                        .city(addr.city())
                        .region(addr.region().orElse(null))
                        .countryCode(addr.countryCode())
                        .build())
                .orElse(null);
        return new BusinessProductComplianceBuilder()
                .countryCodeOrigin(info.countryCodeOrigin())
                .importerName(info.importerName().orElse(null))
                .importerAddress(address)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob",
            exports = "sendCoverPhoto", adaptation = WhatsAppAdaptation.DIRECT)
    public void editBusinessCoverPhoto(long id, Instant ts, byte[] token) {
        Objects.requireNonNull(ts, "ts cannot be null");
        var request = new IqSendCoverPhotoRequest(id, ts.getEpochSecond(), token);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        switch (IqSendCoverPhotoResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqSendCoverPhotoResponse.Success _ -> {
            }
            case IqSendCoverPhotoResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Upload biz cover photo rejected: " + clientError.errorCode());
            case IqSendCoverPhotoResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Upload biz cover photo server error: " + serverError.errorCode());
            case null -> throw new WhatsAppServerRuntimeException(
                    "Upload biz cover photo: response did not match any documented variant");
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebClearDirtyBitsJob",
            exports = "clearDirtyBits", adaptation = WhatsAppAdaptation.DIRECT)
    public void clearDirtyBits(Map<String, Long> dirtyBits) {
        Objects.requireNonNull(dirtyBits, "dirtyBits cannot be null");
        if (dirtyBits.isEmpty()) {
            throw new IllegalArgumentException("dirtyBits cannot be empty");
        }
        var entries = new ArrayList<IqClearDirtyBitsRequest.DirtyEntry>(dirtyBits.size());
        for (var entry : dirtyBits.entrySet()) {
            entries.add(new IqClearDirtyBitsRequest.DirtyEntry(entry.getKey(), entry.getValue()));
        }
        var request = new IqClearDirtyBitsRequest(entries);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        switch (IqClearDirtyBitsResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqClearDirtyBitsResponse.Success _ -> {
            }
            case IqClearDirtyBitsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Clear dirty bits rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case IqClearDirtyBitsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Clear dirty bits server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
            case null -> throw new WhatsAppServerRuntimeException(
                    "Clear dirty bits: response did not match any documented variant");
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMmsClient", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean uploadMedia(MediaProvider provider, InputStream inputStream) {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(inputStream, "inputStream cannot be null");
        try (var payload = mediaTranscoderService.transcode(provider, inputStream)) {
            return mediaConnectionService.upload(provider, payload);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new WhatsAppMediaException.Connection("Interrupted while awaiting media connection", interrupted);
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMmsClient", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean uploadMedia(MediaProvider provider, Path source) {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        try (var payload = mediaTranscoderService.transcode(provider, source)) {
            return mediaConnectionService.upload(provider, payload);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new WhatsAppMediaException.Connection("Interrupted while awaiting media connection", interrupted);
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMmsClient", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public InputStream downloadMedia(MediaProvider provider) {
        try {
            return mediaConnectionService.download(provider);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new WhatsAppMediaException.Connection("Interrupted while awaiting media connection", interrupted);
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTosJob",
            exports = "deleteTosNotice", adaptation = WhatsAppAdaptation.DIRECT)
    public void deleteTosNotice(String noticeId) {
        Objects.requireNonNull(noticeId, "noticeId cannot be null");
        var request = new IqDeleteTosRequest(noticeId);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        switch (IqDeleteTosResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqDeleteTosResponse.Success _ -> {
            }
            case IqDeleteTosResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Delete tos notice rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case IqDeleteTosResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Delete tos notice server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
            case null -> throw new WhatsAppServerRuntimeException(
                    "Delete tos notice: response did not match any documented variant");
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTosJob",
            exports = "acceptTosNotice", adaptation = WhatsAppAdaptation.DIRECT)
    public void acknowledgeTosNotices(List<String> noticeIds) {
        Objects.requireNonNull(noticeIds, "noticeIds cannot be null");
        var request = new IqUpdateTosRequest(List.copyOf(noticeIds));
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        switch (IqUpdateTosResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqUpdateTosResponse.Success _ -> {
            }
            case IqUpdateTosResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Update tos rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case IqUpdateTosResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Update tos server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
            case null -> throw new WhatsAppServerRuntimeException(
                    "Update tos: response did not match any documented variant");
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDigestKeyJob",
            exports = "digestKey", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<IdentityKeyDigest> queryKeyDigest() {
        var request = new IqDigestKeyRequest();
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqDigestKeyResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqDigestKeyResponse.Success success -> Optional.of(toIdentityKeyDigest(success));
            case IqDigestKeyResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Digest key rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case IqDigestKeyResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Digest key server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
            case null -> Optional.empty();
        };
    }

    /**
     * Projects a wire-format {@link IqDigestKeyResponse.Success} onto
     * its public {@link IdentityKeyDigest} domain projection.
     *
     * @param success the parsed wire-format success reply; never
     *                {@code null}
     * @return the populated domain projection; never {@code null}
     */
    private static IdentityKeyDigest toIdentityKeyDigest(IqDigestKeyResponse.Success success) {
        return new IdentityKeyDigest(
                success.registrationId(),
                success.keyBundleType(),
                success.identityPublicKey(),
                toSignalSignedPreKey(success.signedPreKey()),
                success.preKeyIds(),
                success.hash());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGetIdentityKeysJob",
            exports = "getAndStoreIdentityKeys", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<IdentityKey> queryIdentityKeys(List<? extends JidProvider> deviceJidsProvider) {
        var deviceJids = Objects.requireNonNull(deviceJidsProvider, "deviceJids cannot be null").stream().map(JidProvider::toJid).toList();
        var request = new IqGetIdentityKeysRequest(deviceJids);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqGetIdentityKeysResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqGetIdentityKeysResponse.Success success -> {
                var resolved = new ArrayList<IdentityKey>();
                for (var entry : success.entries()) {
                    if (entry instanceof IqGetIdentityKeysResponse.IdentityEntry.Resolved r) {
                        resolved.add(new IdentityKey(r.deviceJid(), r.keyBundleType(), r.identityPublicKey()));
                    }
                }
                yield Collections.unmodifiableList(resolved);
            }
            case IqGetIdentityKeysResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Fetch identity keys rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case IqGetIdentityKeysResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Fetch identity keys server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
            case null -> List.of();
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebRotateKeyJob",
            exports = "rotateKey", adaptation = WhatsAppAdaptation.ADAPTED)
    public void rotateSignedPreKey(SignalSignedPreKey signedPreKey) {
        Objects.requireNonNull(signedPreKey, "signedPreKey cannot be null");
        var request = new IqRotateKeyRequest(toIqUploadPreKeysSignedPreKey(signedPreKey));
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        switch (IqRotateKeyResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqRotateKeyResponse.Success _ -> {
            }
            case IqRotateKeyResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Rotate key rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case IqRotateKeyResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Rotate key server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
            case null -> throw new WhatsAppServerRuntimeException(
                    "Rotate key: response did not match any documented variant");
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUploadPreKeysJob",
            exports = "uploadPreKeys", adaptation = WhatsAppAdaptation.ADAPTED)
    public void uploadSignalPreKeys(SignalPreKeyBundle bundle) {
        Objects.requireNonNull(bundle, "bundle cannot be null");
        var request = new IqUploadPreKeysRequest(
                bundle.registrationId(),
                bundle.keyBundleType(),
                bundle.identityPublicKey(),
                toIqUploadPreKeysList(bundle.oneTimePreKeys()),
                toIqUploadPreKeysSignedPreKey(bundle.signedPreKey()));
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        switch (IqUploadPreKeysResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqUploadPreKeysResponse.Success _ -> {
            }
            case IqUploadPreKeysResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Upload pre-keys rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case IqUploadPreKeysResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Upload pre-keys server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
            case null -> throw new WhatsAppServerRuntimeException(
                    "Upload pre-keys: response did not match any documented variant");
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUploadPrekeysForRegTask",
            exports = "uploadPrekeysForReg", adaptation = WhatsAppAdaptation.ADAPTED)
    public void uploadRegistrationPreKeys(SignalPreKeyBundle bundle) {
        Objects.requireNonNull(bundle, "bundle cannot be null");
        var request = new IqUploadPrekeysForRegRequest(
                bundle.registrationId(),
                bundle.keyBundleType(),
                bundle.identityPublicKey(),
                toIqUploadPreKeysList(bundle.oneTimePreKeys()),
                toIqUploadPreKeysSignedPreKey(bundle.signedPreKey()));
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        switch (IqUploadPrekeysForRegResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqUploadPrekeysForRegResponse.Success _ -> {
            }
            case IqUploadPrekeysForRegResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Upload registration pre-keys rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case IqUploadPrekeysForRegResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Upload registration pre-keys server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
            case null -> throw new WhatsAppServerRuntimeException(
                    "Upload registration pre-keys: response did not match any documented variant");
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrivateStatsIssueTokenJob",
            exports = "issuePrivateStatsToken", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<PrivateStatsToken> issuePrivateStatsToken(byte[] blindedCredential, byte[] projectName) {
        Objects.requireNonNull(blindedCredential, "blindedCredential cannot be null");
        Objects.requireNonNull(projectName, "projectName cannot be null");
        var request = new IqIssuePrivateStatsTokenRequest(blindedCredential, projectName);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqIssuePrivateStatsTokenResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqIssuePrivateStatsTokenResponse.Success success -> Optional.of(new PrivateStatsToken(
                    Instant.ofEpochSecond(success.signCredentialT()),
                    success.signedCredential(),
                    success.acsPublicKey(),
                    success.dleqProofC(),
                    success.dleqProofS(),
                    success.projectName()));
            case IqIssuePrivateStatsTokenResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Issue private stats token rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case IqIssuePrivateStatsTokenResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Issue private stats token server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
            case null -> Optional.empty();
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSyncdServerSync",
            exports = "serverSync", adaptation = WhatsAppAdaptation.ADAPTED)
    public AppStateSyncResult syncAppState(List<AppStateSyncCollection> collections) {
        Objects.requireNonNull(collections, "collections cannot be null");
        var wireCollections = new ArrayList<IqSyncdServerSyncRequestCollection>(collections.size());
        for (var collection : collections) {
            wireCollections.add(new IqSyncdServerSyncRequestCollection(
                    collection.name(),
                    collection.version().isPresent() ? collection.version().getAsLong() : null,
                    collection.patch().orElse(null)));
        }
        var request = new IqSyncdServerSyncRequest(wireCollections);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        return switch (IqSyncdServerSyncResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqSyncdServerSyncResponse.Success success -> toAppStateSyncResult(success);
            case IqSyncdServerSyncResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Syncd server sync rejected: code=" + clientError.errorCode()
                                    + ", text=" + clientError.errorText().orElse(null));
            case IqSyncdServerSyncResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Syncd server sync server error: code=" + serverError.errorCode()
                                    + ", text=" + serverError.errorText().orElse(null));
            case null -> throw new WhatsAppServerRuntimeException(
                    "Syncd server sync: response did not match any documented variant");
        };
    }

    /**
     * Projects a wire-format {@link IqSyncdServerSyncResponse.Success}
     * onto its public {@link AppStateSyncResult} domain projection.
     *
     * @param success the parsed wire-format success reply; never
     *                {@code null}
     * @return the populated domain projection; never {@code null}
     */
    private static AppStateSyncResult toAppStateSyncResult(IqSyncdServerSyncResponse.Success success) {
        var collections = new ArrayList<AppStateSyncCollectionResult>();
        for (var entry : success.collections()) {
            collections.add(new AppStateSyncCollectionResult(
                    entry.name(),
                    toAppStateSyncStatus(entry.state()),
                    entry.version().orElse(null),
                    new ArrayList<>(entry.patches()),
                    entry.snapshot().orElse(null)));
        }
        return new AppStateSyncResult(collections);
    }

    /**
     * Projects a wire-side {@link IqSyncdServerSyncCollectionState}
     * onto its public {@link AppStateSyncStatus} domain projection.
     *
     * @param state the wire-side state; never {@code null}
     * @return the matching domain status; never {@code null}
     */
    private static AppStateSyncStatus toAppStateSyncStatus(IqSyncdServerSyncCollectionState state) {
        return switch (state) {
            case SUCCESS -> AppStateSyncStatus.SUCCESS;
            case SUCCESS_HAS_MORE -> AppStateSyncStatus.SUCCESS_HAS_MORE;
            case CONFLICT -> AppStateSyncStatus.CONFLICT;
            case CONFLICT_HAS_MORE -> AppStateSyncStatus.CONFLICT_HAS_MORE;
            case ERROR_FATAL -> AppStateSyncStatus.ERROR_FATAL;
            case ERROR_RETRY -> AppStateSyncStatus.ERROR_RETRY;
        };
    }

    /**
     * Projects a public {@link SignalSignedPreKey} onto its wire-side
     * {@link IqUploadPreKeysSignedPreKey} counterpart.
     *
     * @param signedPreKey the public signed pre-key; never
     *                     {@code null}
     * @return the matching wire-side signed pre-key; never
     *         {@code null}
     */
    private static IqUploadPreKeysSignedPreKey toIqUploadPreKeysSignedPreKey(SignalSignedPreKey signedPreKey) {
        return new IqUploadPreKeysSignedPreKey(
                signedPreKey.id(),
                signedPreKey.publicKey(),
                signedPreKey.signature());
    }

    /**
     * Projects a wire-side {@link IqUploadPreKeysSignedPreKey} onto
     * its public {@link SignalSignedPreKey} counterpart.
     *
     * @param signedPreKey the wire-side signed pre-key; never
     *                     {@code null}
     * @return the matching public signed pre-key; never {@code null}
     */
    private static SignalSignedPreKey toSignalSignedPreKey(IqUploadPreKeysSignedPreKey signedPreKey) {
        return new SignalSignedPreKey(
                signedPreKey.id(),
                signedPreKey.publicKey(),
                signedPreKey.signature());
    }

    /**
     * Projects a public list of {@link SignalPreKey} onto a wire-side
     * list of {@link IqUploadPreKeysPreKey}.
     *
     * @param preKeys the public list; never {@code null}
     * @return the matching wire-side list; never {@code null}
     */
    private static List<IqUploadPreKeysPreKey> toIqUploadPreKeysList(List<SignalPreKey> preKeys) {
        var wireList = new ArrayList<IqUploadPreKeysPreKey>(preKeys.size());
        for (var preKey : preKeys) {
            wireList.add(new IqUploadPreKeysPreKey(preKey.id(), preKey.publicKey()));
        }
        return wireList;
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

    public WhatsAppClient addCallListener(WhatsappClientListenerConsumer.Binary<WhatsAppClient, IncomingCall> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        addListener(new WhatsAppClientListener() {
            @Override
            public void onCall(WhatsAppClient arg0, IncomingCall arg1) {
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
     * {@code NaN} on WA Web and are skipped here because WAM integer
     * properties cannot carry {@code NaN}.
     * @param labelId the string label identifier to parse
     * @return the integer list id, or {@code null} if {@code labelId} does not
     *         parse as an integer
     */
    private static Integer parseLabelIdToListId(String labelId) {
        try {
            return Integer.parseInt(labelId); // WAWebListsActions: Number(n.id) / Number(e)
        } catch (NumberFormatException _) {
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
     * @param type the sync-model list type, may be {@code null}
     * @return the corresponding WAM list type, or {@code null} if the input is
     *         {@code null} or has no WAM counterpart
     */
    private static ListType mapWamListType(LabelEditAction.ListType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case NONE -> ListType.NONE;
            case UNREAD -> ListType.UNREAD;
            case GROUPS -> ListType.GROUP;
            case FAVORITES -> ListType.FAVORITE;
            case PREDEFINED -> ListType.PREDEFINED;
            case CUSTOM -> ListType.CUSTOM;
            case COMMUNITY -> ListType.COMMUNITY;
            case SERVER_ASSIGNED -> ListType.SERVER_ASSIGNED;
            default -> null;
            };
    }

    //<editor-fold desc="SMAX: groups, communities, newsletters, AB props, bot, support">
    // Each method below dispatches a typed Smax<Op>Request from
    // node/smax/groups/ or node/smax/newsletters/, blocks for the
    // relay's reply, and unwraps the Smax<Op>Response chain into the
    // Success projection. ClientError / ServerError variants are
    // surfaced through WhatsAppServerRuntimeException with the relay's
    // (code, text) pair embedded.

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsAcknowledgeGroupRPC", exports = "sendAcknowledgeGroupRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void acknowledgeGroup(JidProvider groupProvider) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var request = new SmaxGroupsAcknowledgeGroupRequest(group);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsAcknowledgeGroupResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Group acknowledge: unparseable response");
            case SmaxGroupsAcknowledgeGroupResponse.Success _ -> {
                // No payload to surface: the relay only echoes the IQ envelope.
            }
            case SmaxGroupsAcknowledgeGroupResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Group acknowledge rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsAcknowledgeGroupResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Group acknowledge server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsAcceptGroupAddRPC", exports = "sendAcceptGroupAddRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean acceptGroupAdd(GroupAddAccept accept) {
        Objects.requireNonNull(accept, "accept cannot be null");
        var group = accept.group();
        var acceptAdmin = accept.acceptAdmin();
        var acceptCode = accept.acceptCode();
        var acceptExpiration = accept.acceptExpiration();
        var request = new SmaxGroupsAcceptGroupAddRequest(group, acceptCode, acceptExpiration, acceptAdmin);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsAcceptGroupAddResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Accept group add: unparseable response");
            case SmaxGroupsAcceptGroupAddResponse.Success _ -> false;
            case SmaxGroupsAcceptGroupAddResponse.GroupJoinRequestSuccess _ -> true;
            case SmaxGroupsAcceptGroupAddResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Accept group add rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsAcceptGroupAddResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Accept group add server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsBatchGetGroupInfoRPC", exports = "sendBatchGetGroupInfoRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<GroupMetadata> batchQueryGroupInfo(Collection<? extends JidProvider> groupsProvider) {
        var groups = Objects.requireNonNull(groupsProvider, "groups cannot be null").stream().map(JidProvider::toJid).toList();
        var request = new SmaxGroupsBatchGetGroupInfoRequest(List.copyOf(groups));
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsBatchGetGroupInfoResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Batch get group info: unparseable response");
            case SmaxGroupsBatchGetGroupInfoResponse.Success success -> {
                var result = new ArrayList<GroupMetadata>(success.groups().size());
                for (var groupNode : success.groups()) {
                    if (groupNode.hasAttribute("error")) {
                        // group_forbidden ("403") and group_not_exist ("404") markers carry no metadata.
                        continue;
                    }
                    if (parseChatMetadata(groupNode) instanceof GroupMetadata metadata) {
                        result.add(metadata);
                    }
                }
                yield List.copyOf(result);
            }
            case SmaxGroupsBatchGetGroupInfoResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Batch get group info rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsBatchGetGroupInfoResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Batch get group info server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsCancelGroupMembershipRequestsRPC", exports = "sendCancelGroupMembershipRequestsRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, GroupParticipantStatus> cancelGroupMembershipRequests(JidProvider groupProvider, Collection<? extends JidProvider> applicantsProvider) {
        var applicants = Objects.requireNonNull(applicantsProvider, "applicants cannot be null").stream().map(JidProvider::toJid).toList();
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var request = new SmaxGroupsCancelGroupMembershipRequestsRequest(group, List.copyOf(applicants));
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsCancelGroupMembershipRequestsResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Cancel membership requests: unparseable response");
            case SmaxGroupsCancelGroupMembershipRequestsResponse.Success success -> {
                var result = new LinkedHashMap<Jid, GroupParticipantStatus>();
                for (var entry : success.participants()) {
                    var status = entry.rejectionReason()
                            .map(reason -> switch (reason.kind()) {
                                case REQUEST_NOT_FOUND -> GroupParticipantStatus.NOT_WHATSAPP_USER;
                                case NOT_AUTHORIZED -> GroupParticipantStatus.NOT_AUTHORIZED;
                            })
                            .orElse(GroupParticipantStatus.OK);
                    result.put(entry.jid(), status);
                }
                yield Map.copyOf(result);
            }
            case SmaxGroupsCancelGroupMembershipRequestsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Cancel membership requests rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsCancelGroupMembershipRequestsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Cancel membership requests server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsCreateSubGroupSuggestionRPC", exports = "sendCreateSubGroupSuggestionRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public SubgroupSuggestionResult suggestNewSubgroup(SubgroupSuggestionNew suggestionInput) {
        Objects.requireNonNull(suggestionInput, "suggestion cannot be null");
        var community = suggestionInput.community();
        var subject = suggestionInput.subject();
        var description = suggestionInput.description().orElse(null);
        var locked = suggestionInput.locked();
        var announcement = suggestionInput.announcement();
        var hiddenGroup = suggestionInput.hiddenGroup();
        var suggestion = new SmaxGroupsCreateSubGroupSuggestionSuggestion.NewGroup(
                subject, description, locked, announcement, hiddenGroup, null, null, null, null);
        return dispatchSubgroupSuggestion(community, suggestion);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsCreateSubGroupSuggestionRPC", exports = "sendCreateSubGroupSuggestionRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public SubgroupSuggestionResult suggestExistingSubgroups(JidProvider communityProvider,
                                                              Collection<? extends JidProvider> candidateGroupsProvider) {
        var candidateGroups = Objects.requireNonNull(candidateGroupsProvider, "candidateGroups cannot be null").stream().map(JidProvider::toJid).toList();
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        if (candidateGroups.isEmpty()) {
            throw new IllegalArgumentException("candidateGroups cannot be empty");
        }
        var candidates = new ArrayList<SmaxGroupsCreateSubGroupSuggestionSuggestion.ExistingGroups.Candidate>(candidateGroups.size());
        for (var jid : candidateGroups) {
            Objects.requireNonNull(jid, "candidateGroups entries cannot be null");
            candidates.add(new SmaxGroupsCreateSubGroupSuggestionSuggestion.ExistingGroups.Candidate(jid, false));
        }
        var suggestion = new SmaxGroupsCreateSubGroupSuggestionSuggestion.ExistingGroups(candidates);
        return dispatchSubgroupSuggestion(community, suggestion);
    }

    /**
     * Internal helper dispatching either suggestion variant and mapping
     * the wire result onto the domain {@link SubgroupSuggestionResult}.
     *
     * @param community  the parent community JID; never {@code null}
     * @param suggestion the prepared suggestion variant; never {@code null}
     * @return the suggestion result; never {@code null}
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
    private SubgroupSuggestionResult dispatchSubgroupSuggestion(Jid community,
                                                                 SmaxGroupsCreateSubGroupSuggestionSuggestion suggestion) {
        var request = new SmaxGroupsCreateSubGroupSuggestionRequest(community, suggestion);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsCreateSubGroupSuggestionResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Sub-group suggestion: unparseable response");
            case SmaxGroupsCreateSubGroupSuggestionResponse.NewGroupSuggestionSuccess fresh ->
                    new SubgroupSuggestionResultBuilder()
                            .kind(SubgroupSuggestionResult.Kind.NEW_GROUP)
                            .newGroupJid(fresh.subGroupSuggestionJid())
                            .newGroupCreator(fresh.subGroupSuggestionCreator())
                            .newGroupCreationTimestamp(Instant.ofEpochSecond(fresh.subGroupSuggestionCreation()))
                            .newGroupCreatorPhoneNumber(fresh.subGroupSuggestionCreatorPn().orElse(null))
                            .newGroupDescriptionError(fresh.subGroupSuggestionDescriptionError().orElse(null))
                            .candidates(List.of())
                            .build();
            case SmaxGroupsCreateSubGroupSuggestionResponse.ExistingGroupsSuggestionSuccess existing -> {
                var candidates = new ArrayList<SubgroupSuggestionResult.Candidate>(existing.candidates().size());
                for (var entry : existing.candidates()) {
                    var reason = entry.errorTag()
                            .map(SubgroupSuggestionResult.Candidate.Reason::of)
                            .orElse(null);
                    candidates.add(new SubgroupSuggestionResultCandidateBuilder()
                            .jid(entry.jid())
                            .reason(reason)
                            .build());
                }
                yield new SubgroupSuggestionResultBuilder()
                        .kind(SubgroupSuggestionResult.Kind.EXISTING_GROUPS)
                        .candidates(candidates)
                        .build();
            }
            case SmaxGroupsCreateSubGroupSuggestionResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Sub-group suggestion rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsCreateSubGroupSuggestionResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Sub-group suggestion server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsDeleteParentGroupRPC", exports = "sendDeleteParentGroupRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<GroupMetadata> deleteParentGroup(JidProvider communityProvider) {
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        var existing = store.findChatMetadata(community)
                .filter(GroupMetadata.class::isInstance)
                .map(GroupMetadata.class::cast)
                .orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        var request = new SmaxGroupsDeleteParentGroupRequest(community);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsDeleteParentGroupResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Delete parent group: unparseable response");
            case SmaxGroupsDeleteParentGroupResponse.Success _ -> {
                // No payload to surface: the relay only echoes the IQ envelope.
            }
            case SmaxGroupsDeleteParentGroupResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Delete parent group rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsDeleteParentGroupResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Delete parent group server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
        return Optional.of(existing);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetGroupProfilePicturesRPC", exports = "sendGetGroupProfilePicturesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<GroupProfilePicture> queryGroupProfilePictures(Collection<? extends JidProvider> groupsProvider) {
        var groups = Objects.requireNonNull(groupsProvider, "groups cannot be null").stream().map(JidProvider::toJid).toList();
        var pictures = groups.stream()
                .map(jid -> new SmaxGroupsGetGroupProfilePicturesRequest.PictureRequest(jid, null, null, null, null))
                .toList();
        var request = new SmaxGroupsGetGroupProfilePicturesRequest(null, null, pictures);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsGetGroupProfilePicturesResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Get group profile pictures: unparseable response");
            case SmaxGroupsGetGroupProfilePicturesResponse.Success success -> {
                var result = new ArrayList<GroupProfilePicture>(success.pictures().size());
                for (var picture : success.pictures()) {
                    var groupJid = picture.parentGroupJid().or(picture::subGroupJid).orElse(null);
                    if (groupJid == null) {
                        continue;
                    }
                    result.add(new GroupProfilePictureBuilder()
                            .groupJid(groupJid)
                            .pictureId(picture.pictureId().orElse(null))
                            .pictureType(picture.pictureType().orElse(null))
                            .url(picture.url().orElse(null))
                            .directPath(picture.directPath().orElse(null))
                            .blob(picture.blob().orElse(null))
                            .build());
                }
                yield List.copyOf(result);
            }
            case SmaxGroupsGetGroupProfilePicturesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Get group profile pictures rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsGetGroupProfilePicturesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Get group profile pictures server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetInviteGroupInfoRPC", exports = "sendGetInviteGroupInfoRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<GroupMetadata> queryInviteGroupInfo(String inviteCode) {
        Objects.requireNonNull(inviteCode, "inviteCode cannot be null");
        var request = new SmaxGroupsGetInviteGroupInfoRequest(inviteCode);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsGetInviteGroupInfoResponse.of(response).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Get invite group info: unparseable response");
            case SmaxGroupsGetInviteGroupInfoResponse.Success success -> {
                if (parseChatMetadata(success.group()) instanceof GroupMetadata metadata) {
                    yield Optional.of(metadata);
                }
                yield Optional.empty();
            }
            case SmaxGroupsGetInviteGroupInfoResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Get invite group info rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsGetInviteGroupInfoResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Get invite group info server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetLinkedGroupRPC", exports = "sendGetLinkedGroupRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<ChatMetadata> queryLinkedGroup(JidProvider communityProvider, String queryLinkedType, JidProvider queryLinkedJidProvider) {
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        var queryLinkedJid = Objects.requireNonNull(queryLinkedJidProvider, "queryLinkedJid cannot be null").toJid();
        Objects.requireNonNull(queryLinkedType, "queryLinkedType cannot be null");
        var request = new SmaxGroupsGetLinkedGroupRequest(community, queryLinkedType, queryLinkedJid);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsGetLinkedGroupResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Get linked group: unparseable response");
            case SmaxGroupsGetLinkedGroupResponse.Success success -> Optional.of(parseChatMetadata(success.group()));
            case SmaxGroupsGetLinkedGroupResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Get linked group rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsGetLinkedGroupResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Get linked group server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetLinkedGroupsParticipantsRPC", exports = "sendGetLinkedGroupsParticipantsRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, Jid> queryLinkedGroupsParticipants(JidProvider communityProvider) {
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        var request = new SmaxGroupsGetLinkedGroupsParticipantsRequest(community);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsGetLinkedGroupsParticipantsResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Get linked groups participants: unparseable response");
            case SmaxGroupsGetLinkedGroupsParticipantsResponse.Success success -> {
                var result = new LinkedHashMap<Jid, Jid>();
                for (var entry : success.participants()) {
                    result.put(entry.jid(), entry.phoneNumber().orElse(null));
                }
                yield Collections.unmodifiableMap(result);
            }
            case SmaxGroupsGetLinkedGroupsParticipantsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Get linked groups participants rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsGetLinkedGroupsParticipantsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Get linked groups participants server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetMembershipApprovalRequestsRPC", exports = "sendGetMembershipApprovalRequestsRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<GroupMembershipApprovalRequest> queryGroupMembershipApprovalRequests(JidProvider groupProvider) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var request = new SmaxGroupsGetMembershipApprovalRequestsRequest(group);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsGetMembershipApprovalRequestsResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Get membership approval requests: unparseable response");
            case SmaxGroupsGetMembershipApprovalRequestsResponse.Success success -> {
                var result = new ArrayList<GroupMembershipApprovalRequest>(success.approvals().size());
                for (var approval : success.approvals()) {
                    var method = approval.requestMethod()
                            .map(this::parseMembershipApprovalRequestMethod)
                            .orElse(null);
                    result.add(new GroupMembershipApprovalRequestBuilder()
                            .requestingJid(approval.jid())
                            .requestor(approval.requestor().orElse(null))
                            .requestorPhoneNumber(approval.requestorPn().orElse(null))
                            .requestorUsername(approval.requestorUsername().orElse(null))
                            .parentGroupJid(approval.parentGroupJid().orElse(null))
                            .requestTimestamp(Instant.ofEpochSecond(approval.requestTime()))
                            .method(method)
                            .build());
                }
                yield List.copyOf(result);
            }
            case SmaxGroupsGetMembershipApprovalRequestsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Get membership approval requests rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsGetMembershipApprovalRequestsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Get membership approval requests server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /**
     * Maps the wire {@code request_method} attribute carried by a
     * pending-approval entry onto the typed
     * {@link GroupMembershipApprovalRequest.Method} discriminator.
     *
     * @param wireValue the relay-emitted attribute value; never
     *                  {@code null}
     * @return the matching {@link GroupMembershipApprovalRequest.Method}
     *         constant, or {@code null} when the wire value is not
     *         recognised
     */
    private GroupMembershipApprovalRequest.Method parseMembershipApprovalRequestMethod(String wireValue) {
        return switch (wireValue) {
            case "InviteLink" -> GroupMembershipApprovalRequest.Method.INVITE_LINK;
            case "LinkedGroupJoin" -> GroupMembershipApprovalRequest.Method.LINKED_GROUP_JOIN;
            case "NonAdminAdd" -> GroupMembershipApprovalRequest.Method.NON_ADMIN_ADD;
            default -> null;
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetParticipatingGroupsRPC", exports = "sendGetParticipatingGroupsRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<GroupMetadata> queryParticipatingGroups(boolean includeParticipants, boolean includeDescription) {
        var request = new SmaxGroupsGetParticipatingGroupsRequest(includeParticipants, includeDescription);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsGetParticipatingGroupsResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Get participating groups: unparseable response");
            case SmaxGroupsGetParticipatingGroupsResponse.Success success -> {
                var result = new ArrayList<GroupMetadata>(success.groups().size());
                for (var groupNode : success.groups()) {
                    if (groupNode.hasAttribute("error")) {
                        continue;
                    }
                    if (parseChatMetadata(groupNode) instanceof GroupMetadata metadata) {
                        result.add(metadata);
                    }
                }
                yield List.copyOf(result);
            }
            case SmaxGroupsGetParticipatingGroupsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Get participating groups rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsGetParticipatingGroupsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Get participating groups server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetReportedMessagesRPC", exports = "sendGetReportedMessagesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<GroupMessageReport> queryReportedMessages(JidProvider groupProvider) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var request = new SmaxGroupsGetReportedMessagesRequest(group);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsGetReportedMessagesResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Get reported messages: unparseable response");
            case SmaxGroupsGetReportedMessagesResponse.Success success -> {
                var result = new ArrayList<GroupMessageReport>(success.reports().size());
                for (var report : success.reports()) {
                    var reporters = new ArrayList<GroupMessageReport.Reporter>(report.reporters().size());
                    for (var reporter : report.reporters()) {
                        reporters.add(new GroupMessageReportReporterBuilder()
                                .reporterJid(reporter.jid())
                                .reportTimestamp(Instant.ofEpochSecond(reporter.timestamp()))
                                .build());
                    }
                    result.add(new GroupMessageReportBuilder()
                            .messageId(report.messageId())
                            .reporters(reporters)
                            .build());
                }
                yield List.copyOf(result);
            }
            case SmaxGroupsGetReportedMessagesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Get reported messages rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsGetReportedMessagesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Get reported messages server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsJoinLinkedGroupRPC", exports = "sendJoinLinkedGroupRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean joinLinkedGroup(JidProvider communityProvider, JidProvider subgroupProvider, String joinLinkedGroupType) {
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        var subgroup = Objects.requireNonNull(subgroupProvider, "subgroup cannot be null").toJid();
        Objects.requireNonNull(joinLinkedGroupType, "joinLinkedGroupType cannot be null");
        var request = new SmaxGroupsJoinLinkedGroupRequest(community, subgroup, joinLinkedGroupType);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsJoinLinkedGroupResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Join linked group: unparseable response");
            case SmaxGroupsJoinLinkedGroupResponse.Success _ -> false;
            case SmaxGroupsJoinLinkedGroupResponse.GroupJoinRequestSuccess _ -> true;
            case SmaxGroupsJoinLinkedGroupResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Join linked group rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsJoinLinkedGroupResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Join linked group server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsLinkSubGroupsRPC", exports = "sendLinkSubGroupsRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<LinkedSubgroupResult> linkSubgroups(JidProvider communityProvider, List<? extends JidProvider> subgroupsProvider) {
        var subgroups = Objects.requireNonNull(subgroupsProvider, "subgroups cannot be null").stream().map(JidProvider::toJid).toList();
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        var groups = subgroups.stream()
                .map(jid -> new SmaxGroupsLinkSubGroupsRequest.RequestedGroup(jid, false))
                .toList();
        var request = new SmaxGroupsLinkSubGroupsRequest(community, groups);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsLinkSubGroupsResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Link sub-groups: unparseable response");
            case SmaxGroupsLinkSubGroupsResponse.Success success -> {
                var result = new ArrayList<LinkedSubgroupResult>(success.linkedGroups().size());
                for (var entry : success.linkedGroups()) {
                    var participantErrors = new LinkedHashMap<Jid, String>();
                    for (var error : entry.participantErrors()) {
                        participantErrors.put(error.participantJid(), error.error());
                    }
                    result.add(new LinkedSubgroupResultBuilder()
                            .jid(entry.jid())
                            .hiddenGroup(entry.hiddenGroup())
                            .participantErrors(participantErrors)
                            .build());
                }
                yield List.copyOf(result);
            }
            case SmaxGroupsLinkSubGroupsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Link sub-groups rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsLinkSubGroupsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Link sub-groups server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsReportMessagesRPC", exports = "sendReportMessagesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void reportGroupMessages(JidProvider groupProvider, String messageId) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        Objects.requireNonNull(messageId, "messageId cannot be null");
        var request = new SmaxGroupsReportMessagesRequest(group, messageId);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsReportMessagesResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Report messages: unparseable response");
            case SmaxGroupsReportMessagesResponse.Success _ -> {
                // No payload to surface: the relay only echoes the IQ envelope.
            }
            case SmaxGroupsReportMessagesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Report messages rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsReportMessagesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Report messages server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsRevokeRequestCodeRPC", exports = "sendRevokeRequestCodeRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, GroupParticipantStatus> revokeGroupRequestCode(JidProvider groupProvider, List<? extends JidProvider> participantsProvider) {
        var participants = Objects.requireNonNull(participantsProvider, "participants cannot be null").stream().map(JidProvider::toJid).toList();
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var request = new SmaxGroupsRevokeRequestCodeRequest(group, participants);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsRevokeRequestCodeResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Revoke request code: unparseable response");
            case SmaxGroupsRevokeRequestCodeResponse.Success success -> {
                var result = new LinkedHashMap<Jid, GroupParticipantStatus>();
                for (var entry : success.participants()) {
                    var status = entry.error()
                            .map(code -> {
                                try {
                                    return GroupParticipantStatus.of(Integer.parseInt(code));
                                } catch (NumberFormatException ex) {
                                    return GroupParticipantStatus.UNKNOWN;
                                }
                            })
                            .orElse(GroupParticipantStatus.OK);
                    result.put(entry.jid(), status);
                }
                yield Map.copyOf(result);
            }
            case SmaxGroupsRevokeRequestCodeResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Revoke request code rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsRevokeRequestCodeResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Revoke request code server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyInfoJob", exports = "setGroupSubject",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebGroupModifyInfoJob", exports = "setGroupDescription",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendProfilePictureJob", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendProfilePictureJob", exports = "sendProfilePicture",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxGroupsSetPropertyRPC", exports = "sendSetPropertyRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSetPropertyGroupAction", exports = "setGroupProperty",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateGroupPropertyJob", exports = "mexUpdateGroupPropertyJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChatEphemerality", exports = "setEphemeralSetting",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "applyMutations",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<GroupMetadata> editGroupMetadata(GroupMetadataEdit edit) {
        Objects.requireNonNull(edit, "edit cannot be null");
        var group = edit.group();
        if (!group.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("Expected a group/community");
        }
        var existing = store.findChatMetadata(group)
                .filter(GroupMetadata.class::isInstance)
                .map(GroupMetadata.class::cast)
                .orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        edit.subject().ifPresent(newSubject -> sendGroupSubjectIq(group, newSubject));
        edit.description().ifPresent(intent -> {
            switch (intent) {
                case GroupDescription.Set set -> sendGroupDescriptionIq(group, set.body(), false);
                case GroupDescription.Clear ignored -> sendGroupDescriptionIq(group, null, true);
            }
        });
        edit.picture().ifPresent(intent -> {
            switch (intent) {
                case GroupPicture.Set set -> sendGroupPictureIq(group, set.bytes());
                case GroupPicture.Clear ignored -> sendGroupPictureIq(group, null);
            }
        });
        if (hasAnyGroupSettingsFlag(edit)) {
            sendGroupSettingsIq(edit);
        }
        // WAWebSetPropertyGroupAction.setGroupProperty MEX-dispatched properties; each pair fires the
        // matching MEX update plus, where applicable, the WAM commit WA Web emits after the mutation
        // returns. Pairs are processed independently because both halves are set-only toggles whose
        // dispatch shape differs (a single edit may only flip one half of a pair at a time).
        if (edit.limitSharingEnabled()) {
            sendMexGroupPropertyUpdate(group,
                    "{\"limit_sharing\":{\"limit_sharing_enabled\":true,\"limit_sharing_trigger\":\"CHAT_SETTING\"}}");
            wamService.commit(new LimitSharingSettingUpdateEventBuilder()
                    .toggleUpdateAction(ToggleUpdateAction.TURN_ON)
                    .build());
        } else if (edit.limitSharingDisabled()) {
            sendMexGroupPropertyUpdate(group,
                    "{\"limit_sharing\":{\"limit_sharing_enabled\":false,\"limit_sharing_trigger\":\"CHAT_SETTING\"}}");
            wamService.commit(new LimitSharingSettingUpdateEventBuilder()
                    .toggleUpdateAction(ToggleUpdateAction.TURN_OFF)
                    .build());
        }
        if (edit.memberAddAdminOnly()) {
            sendMexGroupPropertyUpdate(group, "{\"member_add_mode\":\"ADMIN_ADD\"}");
        } else if (edit.memberAddAllMember()) {
            sendMexGroupPropertyUpdate(group, "{\"member_add_mode\":\"ALL_MEMBER_ADD\"}");
        }
        if (edit.memberLinkAdminOnly()) {
            sendMexGroupPropertyUpdate(group, "{\"member_link_mode\":\"ADMIN_LINK\"}");
        } else if (edit.memberLinkAllMember()) {
            sendMexGroupPropertyUpdate(group, "{\"member_link_mode\":\"ALL_MEMBER_LINK\"}");
        }
        if (edit.memberShareGroupHistoryAdminOnly()) {
            sendMexGroupPropertyUpdate(group, "{\"member_share_group_history_mode\":\"ADMIN_SHARE\"}");
        } else if (edit.memberShareGroupHistoryAllMember()) {
            sendMexGroupPropertyUpdate(group, "{\"member_share_group_history_mode\":\"ALL_MEMBER_SHARE\"}");
        }
        if (edit.allowNonAdminSubGroupCreation()) {
            // WAWebSetPropertyGroupAction: {allow_non_admin_sub_group_creation: a===1}. allowNonAdminSubGroupCreation=true matches WA Web a===1.
            sendMexGroupPropertyUpdate(group, "{\"allow_non_admin_sub_group_creation\":true}");
            // WAWebCommunityGroupJourneyEventImpl: a===0 (non-admins disallowed) => SELECT_COMMUNITY_ADMINS_CAN_ADD_GROUPS,
            // a!==0 (non-admins allowed) => SELECT_EVERYONE_CAN_ADD_GROUPS. allowNonAdminSubGroupCreation=true => EVERYONE.
            commitCommunityGroupJourneyEvent(
                    ChatFilterActionTypes.SELECT_EVERYONE_CAN_ADD_GROUPS,
                    SurfaceType.COMMUNITY_SETTINGS,
                    group);
        } else if (edit.notAllowNonAdminSubGroupCreation()) {
            sendMexGroupPropertyUpdate(group, "{\"allow_non_admin_sub_group_creation\":false}");
            commitCommunityGroupJourneyEvent(
                    ChatFilterActionTypes.SELECT_COMMUNITY_ADMINS_CAN_ADD_GROUPS,
                    SurfaceType.COMMUNITY_SETTINGS,
                    group);
        }
        // editGroupSetting.EPHEMERAL branch. ephemeralExpiration takes precedence over notEphemeral because a
        // present expiration always means "enable with this duration".
        if (edit.ephemeralExpiration().isPresent()) {
            editEphemeralTimer(group, ChatEphemeralTimer.of(edit.ephemeralExpiration().getAsInt()));
        } else if (edit.notEphemeral()) {
            editEphemeralTimer(group, ChatEphemeralTimer.OFF);
        }
        edit.statusMuted().ifPresent(existing::setStatusMuted);
        return Optional.of(existing);
    }

    /**
     * Returns whether the supplied edit carries at least one settings
     * toggle that must be batched into a
     * {@code WASmaxGroupsSetPropertyRPC} IQ.
     *
     * <p>The {@code allowNonAdminSubGroupCreation} pair is excluded
     * because it is dispatched through the
     * {@code WAWebMexUpdateGroupPropertyJob} MEX endpoint instead, and
     * the ephemeral fields ({@code ephemeralExpiration},
     * {@code ephemeralTrigger}, {@code notEphemeral}) are excluded
     * because they are routed through {@link #editEphemeralTimer} to
     * pick up the in-memory chat ephemerality state update and the
     * {@code EphemeralSettingChangeWamEvent} commit.
     *
     * @param edit the edit to inspect; never {@code null}
     * @return {@code true} when any batched toggle is set
     */
    private static boolean hasAnyGroupSettingsFlag(GroupMetadataEdit edit) {
        return edit.locked() || edit.unlocked()
                || edit.announcement() || edit.notAnnouncement()
                || edit.noFrequentlyForwarded() || edit.frequentlyForwardedOk()
                || edit.allowAdminReports() || edit.notAllowAdminReports()
                || edit.groupHistory() || edit.noGroupHistory()
                || edit.membershipApprovalGroupJoinMode().isPresent();
    }

    /**
     * Dispatches the {@code w:g2} {@code <subject>} IQ for a group
     * subject change, matching
     * {@code WAWebGroupModifyInfoJob.setGroupSubject} and
     * {@code WASmaxOutGroupsSetSubjectChangeSubjectMixin}.
     *
     * @param group      the target group JID; never {@code null}
     * @param newSubject the new subject text; never {@code null}
     */
    private void sendGroupSubjectIq(Jid group, String newSubject) {
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
     * Dispatches the {@code w:g2} {@code <description>} IQ for a group
     * description change. When {@code delete} is {@code true}, emits the
     * empty {@code <description delete="true"/>} variant matching WA
     * Web's {@code hasDescriptionDeleteTrue:!0} branch; otherwise wraps
     * {@code body} in a {@code <body>} child.
     *
     * @param group  the target group JID; never {@code null}
     * @param body   the new description text; ignored when {@code delete}
     *               is {@code true}, must be non-{@code null} otherwise
     * @param delete {@code true} to clear the description
     */
    private void sendGroupDescriptionIq(Jid group, String body, boolean delete) {
        var descriptionBuilder = new NodeBuilder()
                .description("description"); // WASmaxOutGroupsSetDescriptionRequest: smax("description", {id, prev, delete}, body)
        if (!delete) {
            var bodyNode = new NodeBuilder()
                    .description("body") // WASmaxOutGroupsSetDescriptionRequest.makeSetDescriptionRequestDescriptionBody
                    .content(body) // bodyElementValue
                    .build();
            descriptionBuilder.content(bodyNode);
        } else {
            descriptionBuilder.attribute("delete", "true");
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
     * Dispatches the {@code w:profile:picture} IQ for a group
     * profile-picture update. When {@code imageBytes} is {@code null},
     * emits the no-body variant that matches WA Web's
     * {@code WAWebSendProfilePictureJob(group, null)} removal path; the
     * relay-assigned picture identifier (when echoed in the success
     * response) is discarded because {@link #editGroupMetadata} has no
     * return slot for it.
     *
     * @param group      the target group JID; never {@code null}
     * @param imageBytes the new picture bytes, or {@code null} to remove
     */
    private void sendGroupPictureIq(Jid group, byte[] imageBytes) {
        if (imageBytes == null) {
            // WAWebSendProfilePictureJob: a=null -> no picture body
            var iqBuilder = new NodeBuilder()
                    .description("iq")
                    .attribute("xmlns", "w:profile:picture")
                    .attribute("to", JidServer.user())
                    .attribute("target", group)
                    .attribute("type", "set");
            sendNode(iqBuilder);
            return;
        }
        var request = new IqSendProfilePictureRequest(group, imageBytes);
        var requestBuilder = request.toNode();
        var response = sendNode(requestBuilder);
        switch (IqSendProfilePictureResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqSendProfilePictureResponse.Success _ -> {
                // The relay-assigned picture id is dropped: editGroupMetadata has no return slot for it.
            }
            case IqSendProfilePictureResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Send profile picture rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case IqSendProfilePictureResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Send profile picture server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
            case null -> {
                // No usable response; treat as success since the relay accepted the IQ.
            }
        }
    }

    /**
     * Dispatches the batched {@code WASmaxGroupsSetPropertyRPC} IQ
     * carrying every settings toggle and ephemeral override that the
     * caller flipped on the supplied edit. The relay's {@code Success}
     * reply only echoes back which toggles were applied; failures are
     * surfaced through {@link WhatsAppServerRuntimeException}.
     *
     * @param edit the edit carrying the toggle set; never {@code null}
     */
    private void sendGroupSettingsIq(GroupMetadataEdit edit) {
        var group = edit.group();
        // Ephemeral fields and the allowNonAdminSubGroupCreation pair are deliberately omitted
        // here: the former are dispatched through editEphemeralTimer (so the in-memory chat
        // ephemerality state and EphemeralSettingChangeWamEvent commit also fire), and the latter
        // is dispatched through the WAWebMexUpdateGroupPropertyJob endpoint plus a community-group
        // journey WAM commit.
        var request = new SmaxGroupsSetPropertyRequest(group, edit.locked(), edit.announcement(),
                edit.noFrequentlyForwarded(), null, null, edit.unlocked(),
                edit.notAnnouncement(), edit.frequentlyForwardedOk(), false,
                edit.membershipApprovalGroupJoinMode().orElse(null), edit.allowAdminReports(),
                edit.notAllowAdminReports(), false,
                false, edit.groupHistory(), edit.noGroupHistory());
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsSetPropertyResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Set group property: unparseable response");
            case SmaxGroupsSetPropertyResponse.Success _ -> {
                // No useful payload: the relay only echoes back which toggles were applied.
            }
            case SmaxGroupsSetPropertyResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Set group property rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsSetPropertyResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Set group property server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
    }

    /**
     * Dispatches the {@code WAWebMexUpdateGroupPropertyJob} GraphQL
     * mutation with the supplied serialised {@code update} payload,
     * matching the success check WA Web performs in
     * {@code mexUpdateGroupPropertyJob}
     * ({@code data.xwa2_group_update_property.state === "ACTIVE"}).
     * Non-{@code ACTIVE} states and transport errors surface through
     * the sealed {@code WhatsAppException} hierarchy.
     *
     * @param group      the target group JID; never {@code null}
     * @param updateJson the serialised {@code update} JSON object;
     *                   never {@code null}
     */
    private void sendMexGroupPropertyUpdate(Jid group, String updateJson) {
        var request = new UpdateGroupPropertyMexRequest(group.toString(), updateJson); // WAWebMexUpdateGroupPropertyJob: variables={group_id, update}
        var response = sendNode(request);
        UpdateGroupPropertyMexResponse.of(response); // WAWebMexUpdateGroupPropertyJob: checks data.xwa2_group_update_property.state
    }

    /**
     * Emits a {@link GroupJourneyEvent} matching the construction in
     * {@code WAWebCommunityGroupJourneyEventImpl.commit}.
     *
     * <p>Derives {@code groupSize}, {@code threadType} and
     * {@code userRole} from the local {@link ChatMetadata} for
     * {@code group} the same way {@code WAWebGetThreadType.getThreadType}
     * and {@code WAWebGetUserRole.getUserRole} derive them from
     * {@code chat.groupMetadata}:
     * <ul>
     *   <li>{@code groupSize} mirrors
     *       {@code chat.groupMetadata.participants.length ?? 0}.</li>
     *   <li>{@code threadType} mirrors the {@code getThreadType}
     *       switch: {@code PARENT_GROUP} for a community and
     *       {@code GROUP} for a plain group. Only emitted for surfaces
     *       listed in {@code shouldLogThreadType}.</li>
     *   <li>{@code userRole} mirrors {@code getUserRole}:
     *       {@code CADMIN} when the local user is an admin of a parent
     *       community, {@code ADMIN} when the local user is an admin
     *       of any other group and {@code MEMBER} otherwise.</li>
     * </ul>
     *
     * <p>{@code appSessionId} is populated in WA Web via
     * {@code WAWebGetSharedSessionId.getSharedSessionId}, which returns
     * the browser-tab session id. Cobalt is a headless library with no
     * per-session UI identifier and leaves the field unset.
     *
     * @param action  the action that triggered the event; never
     *                {@code null}
     * @param surface the UI surface constant used by WA Web; never
     *                {@code null}
     * @param group   the target group / community JID; never
     *                {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebGetUserRole", exports = "getUserRole",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitCommunityGroupJourneyEvent(ChatFilterActionTypes action, SurfaceType surface, Jid group) {
        var metadata = store.findChatMetadata(group).orElse(null); // WAWebCommunityGroupJourneyEventImpl: this.chat
        var builder = new GroupJourneyEventBuilder()
                .actionType(action) // WAWebCommunityGroupJourneyEventImpl: actionType: this.action
                .surface(surface); // WAWebCommunityGroupJourneyEventImpl: surface: this.surface
        var groupSize = metadata == null ? 0 : metadata.participants().size();
        builder.groupSize(groupSize); // WAWebCommunityGroupJourneyEventImpl: groupSize: this.getGroupSize()
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
     * Returns whether the supplied {@code surface} is one of the
     * surfaces for which
     * {@code WAWebCommunityGroupJourneyEventImpl.shouldLogThreadType}
     * includes the thread type in the emitted
     * {@link GroupJourneyEvent}.
     *
     * @param surface the UI surface to check; never {@code null}
     * @return {@code true} when the thread type should be logged
     */
    private static boolean shouldLogCommunityJourneyThreadType(SurfaceType surface) {
        return switch (surface) {
            case CHAT, CHATLIST, COMMUNITY_HOME, COMMUNITY_TAB, COMMUNITY_NAV, COMMUNITY_NAV_SHEET,
                 COMMUNITY_SETTINGS, GROUP_INFO -> true;
            default -> false;
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxGroupsUnlinkGroupsRPC", exports = "sendUnlinkGroupsRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<UnlinkedSubgroupResult> unlinkSubgroups(JidProvider communityProvider, List<? extends JidProvider> subgroupsProvider) {
        var subgroups = Objects.requireNonNull(subgroupsProvider, "subgroups cannot be null").stream().map(JidProvider::toJid).toList();
        var community = Objects.requireNonNull(communityProvider, "community cannot be null").toJid();
        // RequestedGroup(jid, removeOrphanedMembers): default false to leave orphaned members in place.
        var groups = subgroups.stream()
                .map(jid -> new SmaxGroupsUnlinkGroupsRequest.RequestedGroup(jid, false))
                .toList();
        var request = new SmaxGroupsUnlinkGroupsRequest(community, groups);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupsUnlinkGroupsResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Unlink groups: unparseable response");
            case SmaxGroupsUnlinkGroupsResponse.Success success -> {
                var result = new ArrayList<UnlinkedSubgroupResult>(success.unlinkedGroups().size());
                for (var entry : success.unlinkedGroups()) {
                    var reason = entry.errorTag()
                            .map(UnlinkedSubgroupResult.Reason::of)
                            .orElse(null);
                    result.add(new UnlinkedSubgroupResultBuilder()
                            .jid(entry.jid())
                            .removeOrphanedMembers(entry.removeOrphanedMembers())
                            .reason(reason)
                            .build());
                }
                yield List.copyOf(result);
            }
            case SmaxGroupsUnlinkGroupsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Unlink groups rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxGroupsUnlinkGroupsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Unlink groups server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersGetNewsletterMessageUpdatesRPC", exports = "sendGetNewsletterMessageUpdatesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterMessageHistory queryNewsletterMessageUpdates(JidProvider newsletterProvider, int count, Long since, NewsletterHistoryDirection direction) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new SmaxNewslettersGetNewsletterMessageUpdatesRequest(newsletter, count, since, toWireMessageUpdatesDirection(direction));
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxNewslettersGetNewsletterMessageUpdatesResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null ->
                    throw new WhatsAppServerRuntimeException("Newsletter message updates response did not parse: " + response);
            case SmaxNewslettersGetNewsletterMessageUpdatesResponse.Success success -> toMessageHistory(success.timestamp().orElse(null), success.messages());
            case SmaxNewslettersGetNewsletterMessageUpdatesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Newsletter message updates rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxNewslettersGetNewsletterMessageUpdatesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Newsletter message updates server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    public NewsletterMessageHistory queryNewsletterMessageUpdates(JidProvider newsletter, int count, NewsletterHistoryDirection direction) {
        return queryNewsletterMessageUpdates(newsletter, count, null, direction);
    }

    /** {@inheritDoc} */
    @Override
    public NewsletterMessageHistory queryNewsletterMessages(JidProvider newsletterProvider, int count) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        return queryNewsletterMessages(newsletter, count, null, null);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersGetNewsletterMessagesRPC", exports = "sendGetNewsletterMessagesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterMessageHistory queryNewsletterMessages(JidProvider newsletterProvider,
                                                            int count,
                                                            NewsletterViewerRole viewRole,
                                                            NewsletterHistoryDirection direction) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var queryParams = new SmaxNewslettersGetNewsletterMessagesQueryParams.ByJid(newsletter, viewRole == null ? null : viewRole.name().toLowerCase());
        return dispatchNewsletterMessagesPage(count, queryParams, direction);
    }

    /** {@inheritDoc} */
    @Override
    public NewsletterMessageHistory queryNewsletterMessages(String inviteKey, int count) {
        return queryNewsletterMessages(inviteKey, count, null, null);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersGetNewsletterMessagesRPC", exports = "sendGetNewsletterMessagesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterMessageHistory queryNewsletterMessages(String inviteKey,
                                                            int count,
                                                            NewsletterViewerRole viewRole,
                                                            NewsletterHistoryDirection direction) {
        Objects.requireNonNull(inviteKey, "inviteKey cannot be null");
        var queryParams = new SmaxNewslettersGetNewsletterMessagesQueryParams.ByInvite(inviteKey, viewRole == null ? null : viewRole.name().toLowerCase());
        return dispatchNewsletterMessagesPage(count, queryParams, direction);
    }

    /**
     * Internal helper dispatching the messages-page query for either
     * addressing variant.
     *
     * @param count       the per-call cap
     * @param queryParams the addressing parameters; never {@code null}
     * @param direction   the pagination cursor; may be {@code null}
     * @return the message-history page; never {@code null}
     */
    private NewsletterMessageHistory dispatchNewsletterMessagesPage(int count,
                                                                     SmaxNewslettersGetNewsletterMessagesQueryParams queryParams,
                                                                     NewsletterHistoryDirection direction) {
        var request = new SmaxNewslettersGetNewsletterMessagesRequest(count, queryParams, toWireMessagesDirection(direction));
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxNewslettersGetNewsletterMessagesResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null ->
                    throw new WhatsAppServerRuntimeException("Newsletter messages page response did not parse: " + response);
            case SmaxNewslettersGetNewsletterMessagesResponse.Success success -> toMessageHistory(success.timestamp().orElse(null), success.messages());
            case SmaxNewslettersGetNewsletterMessagesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Newsletter messages page rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxNewslettersGetNewsletterMessagesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Newsletter messages page server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /**
     * Adapts a wire-level message-history success projection into the
     * domain {@link NewsletterMessageHistory}.
     *
     * @param highWaterMark the relay-supplied "as of" unix-second
     *                      timestamp; may be {@code null}
     * @param wireMessages  the wire-level message entries; never
     *                      {@code null}
     * @return the domain history slice; never {@code null}
     */
    private NewsletterMessageHistory toMessageHistory(Long highWaterMark,
                                                      List<SmaxNewslettersGetNewsletterMessagesResponse.NewsletterMessage> wireMessages) {
        var entries = wireMessages.stream()
                .map(message -> new NewsletterMessageHistoryEntryBuilder()
                        .stanzaId(message.stanzaId().orElse(null))
                        .serverId(message.serverId())
                        .timestamp(message.timestamp().map(Instant::ofEpochSecond).orElse(null))
                        .fromSelf(message.fromSelf())
                        .build())
                .toList();
        return new NewsletterMessageHistoryBuilder()
                .highWaterMark(highWaterMark == null ? null : Instant.ofEpochSecond(highWaterMark))
                .entries(entries)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersGetNewsletterResponsesRPC", exports = "sendGetNewsletterResponsesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<NewsletterQuestionResponse> queryNewsletterResponses(JidProvider newsletterProvider, long questionResponsesServerId,
                                                                      int questionResponsesCount, String questionResponsesBefore,
                                                                      NewsletterResponsesFilter filter, String searchText) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new SmaxNewslettersGetNewsletterResponsesRequest(newsletter, questionResponsesServerId,
                questionResponsesCount, questionResponsesBefore, toWireResponsesFilter(filter), searchText);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxNewslettersGetNewsletterResponsesResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null ->
                    throw new WhatsAppServerRuntimeException("Newsletter responses response did not parse: " + response);
            case SmaxNewslettersGetNewsletterResponsesResponse.Success success -> success.questionResponses().stream()
                    .map(entry -> new NewsletterQuestionResponseBuilder()
                            .messageId(entry.messageId())
                            .timestamp(Instant.ofEpochSecond(entry.messageTimestamp()))
                            .fromSelf(entry.fromSelf())
                            .responderLid(entry.senderLid().orElse(null))
                            .responderDisplayName(entry.senderNotifyName().orElse(null))
                            .responderProfilePictureDirectPath(entry.senderPictureDirectPath())
                            .repliedByAuthor(entry.hasRepliedFlag())
                            .build())
                    .toList();
            case SmaxNewslettersGetNewsletterResponsesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Newsletter responses rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxNewslettersGetNewsletterResponsesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Newsletter responses server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    public List<NewsletterQuestionResponse> queryNewsletterResponses(JidProvider newsletter, long serverMessageId, int count) {
        return queryNewsletterResponses(newsletter, serverMessageId, count, null, null, null);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersGetNewsletterStatusUpdatesRPC", exports = "sendGetNewsletterStatusUpdatesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterStatusHistory queryNewsletterStatusUpdates(JidProvider newsletterProvider, int count, Long since, NewsletterHistoryDirection direction) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new SmaxNewslettersGetNewsletterStatusUpdatesRequest(newsletter, count, since, toWireStatusUpdatesDirection(direction));
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxNewslettersGetNewsletterStatusUpdatesResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null ->
                    throw new WhatsAppServerRuntimeException("Newsletter status updates response did not parse: " + response);
            case SmaxNewslettersGetNewsletterStatusUpdatesResponse.Success success -> toStatusHistory(success.timestamp().orElse(null), success.statuses());
            case SmaxNewslettersGetNewsletterStatusUpdatesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Newsletter status updates rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxNewslettersGetNewsletterStatusUpdatesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Newsletter status updates server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    public NewsletterStatusHistory queryNewsletterStatusUpdates(JidProvider newsletter, int count, NewsletterHistoryDirection direction) {
        return queryNewsletterStatusUpdates(newsletter, count, null, direction);
    }

    /** {@inheritDoc} */
    @Override
    public NewsletterStatusHistory queryNewsletterStatuses(JidProvider newsletterProvider, int count) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        return queryNewsletterStatuses(newsletter, count, null, null);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersGetNewsletterStatusesRPC", exports = "sendGetNewsletterStatusesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterStatusHistory queryNewsletterStatuses(JidProvider newsletterProvider,
                                                           int count,
                                                           NewsletterViewerRole viewRole,
                                                           NewsletterHistoryDirection direction) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var queryParams = new SmaxNewslettersGetNewsletterMessagesQueryParams.ByJid(newsletter, viewRole == null ? null : viewRole.name().toLowerCase());
        return dispatchNewsletterStatusesPage(count, queryParams, direction);
    }

    /** {@inheritDoc} */
    @Override
    public NewsletterStatusHistory queryNewsletterStatuses(String inviteKey, int count) {
        return queryNewsletterStatuses(inviteKey, count, null, null);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersGetNewsletterStatusesRPC", exports = "sendGetNewsletterStatusesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NewsletterStatusHistory queryNewsletterStatuses(String inviteKey,
                                                           int count,
                                                           NewsletterViewerRole viewRole,
                                                           NewsletterHistoryDirection direction) {
        Objects.requireNonNull(inviteKey, "inviteKey cannot be null");
        var queryParams = new SmaxNewslettersGetNewsletterMessagesQueryParams.ByInvite(inviteKey, viewRole == null ? null : viewRole.name().toLowerCase());
        return dispatchNewsletterStatusesPage(count, queryParams, direction);
    }

    /**
     * Internal helper dispatching the statuses-page query for either
     * addressing variant.
     *
     * @param count       the per-call cap
     * @param queryParams the addressing parameters; never {@code null}
     * @param direction   the pagination cursor; may be {@code null}
     * @return the status-history page; never {@code null}
     */
    private NewsletterStatusHistory dispatchNewsletterStatusesPage(int count,
                                                                    SmaxNewslettersGetNewsletterMessagesQueryParams queryParams,
                                                                    NewsletterHistoryDirection direction) {
        var request = new SmaxNewslettersGetNewsletterStatusesRequest(count, queryParams, toWireStatusesDirection(direction));
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxNewslettersGetNewsletterStatusesResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null ->
                    throw new WhatsAppServerRuntimeException("Newsletter statuses page response did not parse: " + response);
            case SmaxNewslettersGetNewsletterStatusesResponse.Success success -> toStatusHistory(success.timestamp().orElse(null), success.statuses());
            case SmaxNewslettersGetNewsletterStatusesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Newsletter statuses page rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxNewslettersGetNewsletterStatusesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Newsletter statuses page server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }
    /**
     * Adapts a domain NewsletterHistoryDirection cursor to the wire-level
     * direction used by the messages-page request.
     */
    private static SmaxNewslettersGetNewsletterMessagesDirection toWireMessagesDirection(NewsletterHistoryDirection direction) {
        return switch (direction) {
            case null -> null;
            case NewsletterHistoryDirection.Before b -> new SmaxNewslettersGetNewsletterMessagesDirection.Before(b.pivot());
            case NewsletterHistoryDirection.After a -> new SmaxNewslettersGetNewsletterMessagesDirection.After(a.pivot());
        };
    }

    private static SmaxNewslettersGetNewsletterMessageUpdatesDirection toWireMessageUpdatesDirection(NewsletterHistoryDirection direction) {
        return switch (direction) {
            case null -> null;
            case NewsletterHistoryDirection.Before b -> new SmaxNewslettersGetNewsletterMessageUpdatesDirection.Before(b.pivot());
            case NewsletterHistoryDirection.After a -> new SmaxNewslettersGetNewsletterMessageUpdatesDirection.After(a.pivot());
        };
    }

    private static SmaxNewslettersGetNewsletterStatusesDirection toWireStatusesDirection(NewsletterHistoryDirection direction) {
        return switch (direction) {
            case null -> null;
            case NewsletterHistoryDirection.Before b -> new SmaxNewslettersGetNewsletterStatusesDirection.Before(b.pivot());
            case NewsletterHistoryDirection.After a -> new SmaxNewslettersGetNewsletterStatusesDirection.After(a.pivot());
        };
    }

    private static SmaxNewslettersGetNewsletterStatusUpdatesDirection toWireStatusUpdatesDirection(NewsletterHistoryDirection direction) {
        return switch (direction) {
            case null -> null;
            case NewsletterHistoryDirection.Before b -> new SmaxNewslettersGetNewsletterStatusUpdatesDirection.Before(b.pivot());
            case NewsletterHistoryDirection.After a -> new SmaxNewslettersGetNewsletterStatusUpdatesDirection.After(a.pivot());
        };
    }

    /**
     * Adapts a domain {@link NewsletterResponsesFilter} to the
     * wire-level filter used by the responses request.
     *
     * @param filter the domain filter, or {@code null}
     * @return the wire-level filter, or {@code null}
     */
    private static SmaxNewslettersGetNewsletterResponsesFilter toWireResponsesFilter(NewsletterResponsesFilter filter) {
        return switch (filter) {
            case null -> null;
            case NewsletterResponsesFilter.Contacts _ -> new SmaxNewslettersGetNewsletterResponsesFilter.Contacts();
            case NewsletterResponsesFilter.Replied _ -> new SmaxNewslettersGetNewsletterResponsesFilter.Replied();
        };
    }


    /**
     * Adapts a wire-level status-history success projection into the
     * domain {@link NewsletterStatusHistory}, projecting the envelope
     * fields out of each status node.
     *
     * @param highWaterMark the relay-supplied "as of" unix-second
     *                      timestamp; may be {@code null}
     * @param wireStatuses  the wire-level status entries; never
     *                      {@code null}
     * @return the domain history slice; never {@code null}
     */
    private NewsletterStatusHistory toStatusHistory(Long highWaterMark,
                                                     List<SmaxNewslettersGetNewsletterStatusesResponse.NewsletterStatus> wireStatuses) {
        var entries = wireStatuses.stream()
                .map(status -> {
                    var raw = status.raw();
                    var stanzaId = raw.getAttributeAsString("id").orElse(null);
                    var serverId = raw.getAttributeAsLong("server_id").orElse(0L);
                    var tOpt = raw.getAttributeAsLong("t");
                    var timestamp = tOpt.isPresent() ? Instant.ofEpochSecond(tOpt.getAsLong()) : null;
                    var fromSelf = raw.hasAttribute("is_sender", "true");
                    return new NewsletterStatusHistoryEntryBuilder()
                            .stanzaId(stanzaId)
                            .serverId(serverId)
                            .timestamp(timestamp)
                            .fromSelf(fromSelf)
                            .build();
                })
                .toList();
        return new NewsletterStatusHistoryBuilder()
                .highWaterMark(highWaterMark == null ? null : Instant.ofEpochSecond(highWaterMark))
                .entries(entries)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersMyAddOnsRPC", exports = "sendMyAddOnsRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Jid, List<NewsletterMyAddOn>> queryNewsletterMyAddOns(int limit, JidProvider newsletterProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new SmaxNewslettersMyAddOnsRequest(limit, newsletter);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxNewslettersMyAddOnsResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null ->
                    throw new WhatsAppServerRuntimeException("Newsletter my-add-ons response did not parse: " + response);
            case SmaxNewslettersMyAddOnsResponse.Success success -> toMyAddOnsMap(success.blocks());
            case SmaxNewslettersMyAddOnsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Newsletter my-add-ons rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxNewslettersMyAddOnsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Newsletter my-add-ons server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /**
     * Adapts the wire-level my-add-ons success projection into a map
     * keyed by newsletter JID.
     *
     * @param wireBlocks the wire-level per-newsletter blocks; never
     *                   {@code null}
     * @return an unmodifiable map; never {@code null}
     */
    private Map<Jid, List<NewsletterMyAddOn>> toMyAddOnsMap(List<SmaxNewslettersMyAddOnsResponse.Success.NewsletterBlock> wireBlocks) {
        var result = new LinkedHashMap<Jid, List<NewsletterMyAddOn>>();
        for (var block : wireBlocks) {
            var addOns = block.messages().stream()
                    .map(this::toMyAddOn)
                    .toList();
            result.put(block.newsletterJid(), addOns);
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Adapts a wire-level message-add-on bundle into a domain
     * {@link NewsletterMyAddOn}.
     *
     * @param wire the wire bundle; never {@code null}
     * @return the domain entry; never {@code null}
     */
    private NewsletterMyAddOn toMyAddOn(SmaxNewslettersMyAddOnsResponse.Success.MessageAddOns wire) {
        var reaction = wire.reaction()
                .map(r -> new NewsletterMyAddOnReactionBuilder()
                        .code(r.code())
                        .timestamp(Instant.ofEpochSecond(r.timestamp()))
                        .build())
                .orElse(null);
        var pollVote = wire.pollVote()
                .map(p -> new NewsletterMyAddOnPollVoteBuilder()
                        .timestamp(Instant.ofEpochSecond(p.timestamp()))
                        .optionIds(p.votes())
                        .build())
                .orElse(null);
        return new NewsletterMyAddOnBuilder()
                .serverId(wire.serverId())
                .reaction(reaction)
                .pollVote(pollVote)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersSubscribeToLiveUpdatesRPC", exports = "sendSubscribeToLiveUpdatesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Duration subscribeToNewsletterLiveUpdates(JidProvider newsletterProvider) {
        var newsletter = Objects.requireNonNull(newsletterProvider, "newsletter cannot be null").toJid();
        var request = new SmaxNewslettersSubscribeToLiveUpdatesRequest(newsletter);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxNewslettersSubscribeToLiveUpdatesResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null ->
                    throw new WhatsAppServerRuntimeException("Newsletter subscribe response did not parse: " + response);
            case SmaxNewslettersSubscribeToLiveUpdatesResponse.Success success -> Duration.ofSeconds(success.duration());
            case SmaxNewslettersSubscribeToLiveUpdatesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Newsletter subscribe rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxNewslettersSubscribeToLiveUpdatesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Newsletter subscribe server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxAbPropsGetExperimentConfigRPC",
            exports = "sendGetExperimentConfigRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<AbPropsBundle> queryExperimentConfig(String propsHash,
                                                          Integer propsRefreshId) {
        var request = new SmaxAbPropsGetExperimentConfigRequest(propsHash, propsRefreshId);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxAbPropsGetExperimentConfigResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxAbPropsGetExperimentConfigResponse.Success success -> Optional.of(toAbPropsBundle(null, success.propsHash().orElse(null),
                    success.propsRefresh().orElse(null), success.propsRefreshId().orElse(null),
                    success.propsAbKey().orElse(null), success.propsNode()));
            case SmaxAbPropsGetExperimentConfigResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Experiment-config request rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxAbPropsGetExperimentConfigResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Experiment-config server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /**
     * Walks the {@code <props/>} subtree returned by an A/B experiment-
     * config fetch and projects each {@code ExperimentConfig} child into
     * a typed entry in the resulting {@link AbPropsBundle}.
     *
     * <p>The WhatsApp Web parser ({@code WAWebABPropsParseConfigValue})
     * reads the same children inline; this helper centralises the
     * per-{@code <prop>} parsing so both
     * {@link #queryExperimentConfig(String, Integer)} and
     * {@link #queryGroupExperimentConfig(Jid, String)} surface the
     * caller-friendly {@link AbPropsBundle} shape.
     *
     * @param groupJid       the optional target group JID; may be
     *                       {@code null}
     * @param hash           the relay-returned hash; may be
     *                       {@code null}
     * @param refresh        the relay-returned refresh cooldown; may be
     *                       {@code null}
     * @param refreshId      the relay-returned refresh id; may be
     *                       {@code null}
     * @param abKey          the relay-returned ab-key; may be
     *                       {@code null}
     * @param propsNode      the raw {@code <props/>} subtree; never
     *                       {@code null}
     * @return the projected {@link AbPropsBundle}
     */
    private static AbPropsBundle toAbPropsBundle(Jid groupJid, String hash, Integer refresh,
                                                 Integer refreshId, String abKey, Node propsNode) {
        var experiments = new LinkedHashMap<Integer, String>();
        for (var propNode : propsNode.getChildren("prop")) {
            var configCode = propNode.getAttributeAsInt("config_code");
            var configValue = propNode.getAttributeAsString("config_value");
            if (configCode.isPresent() && configValue.isPresent()) {
                experiments.put(configCode.getAsInt(), configValue.get());
            }
        }
        return new AbPropsBundle(groupJid, hash, refresh, refreshId, abKey, experiments);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxAbPropsGetGroupExperimentConfigRPC",
            exports = "sendGetGroupExperimentConfigRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<AbPropsBundle> queryGroupExperimentConfig(JidProvider groupProvider,
                                                               String propsHash) {
        var group = Objects.requireNonNull(groupProvider, "group cannot be null").toJid();
        var request = new SmaxAbPropsGetGroupExperimentConfigRequest(group, propsHash);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxAbPropsGetGroupExperimentConfigResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxAbPropsGetGroupExperimentConfigResponse.Success success -> Optional.of(toAbPropsBundle(group,
                    success.propsHash().orElse(null), success.propsRefresh().orElse(null),
                    success.propsRefreshId().orElse(null), success.propsAbKey().orElse(null),
                    success.propsNode()));
            case SmaxAbPropsGetGroupExperimentConfigResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Group experiment-config rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxAbPropsGetGroupExperimentConfigResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Group experiment-config server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBotBotListRPC",
            exports = "sendBotListRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<BotDirectory> queryBotList(String botV, String botBhash, List<? extends JidProvider> botArgsProvider) {
        var botArgs = Objects.requireNonNull(botArgsProvider, "botArgs cannot be null").stream().map(JidProvider::toJid).toList();
        var request = new SmaxBotBotListRequest(botV, botBhash, botArgs);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxBotBotListResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxBotBotListResponse.SuccessV2 success -> Optional.of(toBotDirectoryV2(success));
            case SmaxBotBotListResponse.SuccessV3 success -> Optional.of(toBotDirectoryV3(success));
            case SmaxBotBotListResponse.Error error ->
                    throw new WhatsAppServerRuntimeException("Bot-list request error: code=" + error.errorCode() + ", text=" + error.errorText().orElse(null));
        };
    }

    /**
     * Projects a V2 bot-directory reply into a unified
     * {@link BotDirectory}. The default-entry JID and persona id
     * are surfaced through {@link BotDirectory.DefaultBot}; the
     * V2-only theme overrides are dropped because they are
     * presentation-layer hints not surfaced through this model.
     *
     * @param success the V2 success projection; never {@code null}
     * @return the projected {@link BotDirectory}
     */
    private static BotDirectory toBotDirectoryV2(SmaxBotBotListResponse.SuccessV2 success) {
        var defaultBot = new BotDirectory.DefaultBot(success.botDefaultJid(), success.botDefaultPersonaId());
        var sections = new ArrayList<BotDirectory.Section>(success.botSection().size());
        for (var section : success.botSection()) {
            var bots = new ArrayList<BotDirectory.BotEntry>(section.bot().size());
            for (var bot : section.bot()) {
                bots.add(new BotDirectory.BotEntry(bot.jid(), bot.personaId(), null, bot.count().orElse(null)));
            }
            sections.add(new BotDirectory.Section(section.name(), section.type().wireValue(), null, bots));
        }
        return new BotDirectory(success.botV(), null, defaultBot, sections);
    }

    /**
     * Projects a V3 bot-directory reply into a unified
     * {@link BotDirectory}. The V3-only display-type discriminator is
     * surfaced as a string on each {@link BotDirectory.Section}.
     *
     * @param success the V3 success projection; never {@code null}
     * @return the projected {@link BotDirectory}
     */
    private static BotDirectory toBotDirectoryV3(SmaxBotBotListResponse.SuccessV3 success) {
        var defaultBot = success.botDefault()
                .map(d -> new BotDirectory.DefaultBot(d.jid(), d.personaId()))
                .orElse(null);
        var sections = new ArrayList<BotDirectory.Section>(success.botSection().size());
        for (var section : success.botSection()) {
            var bots = new ArrayList<BotDirectory.BotEntry>(section.bot().size());
            for (var bot : section.bot()) {
                bots.add(new BotDirectory.BotEntry(bot.jid(), bot.personaId(),
                        bot.cardTitle().orElse(null), bot.count().orElse(null)));
            }
            sections.add(new BotDirectory.Section(section.name(), section.type().wireValue(),
                    section.displayType().wireValue(), bots));
        }
        return new BotDirectory(success.botV(), success.botBhash(), defaultBot, sections);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxBugReportingReportBugRPC",
            exports = "sendReportBugRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<String> reportBug(BugReport report) {
        Objects.requireNonNull(report, "report cannot be null");
        var from = report.from();
        var description = report.description().orElse(null);
        var debugInformationJson = report.debugInformationJson().orElse(null);
        var deviceLogHandle = report.deviceLogHandle().orElse(null);
        var mediaUploads = report.mediaUploads().isEmpty() ? null : report.mediaUploads().stream()
                .map(u -> new SmaxBugReportingReportBugMediaUpload(
                        u.mediaIv().orElse(null),
                        u.mediaCipherKey().orElse(null),
                        u.mediaType().orElse(null),
                        u.mediaFileName().orElse(null),
                        u.mediaElementValue().orElse(null)))
                .toList();
        var title = report.title().orElse(null);
        var category = report.category().orElse(null);
        var clientServerJoinKey = report.clientServerJoinKey().orElse(null);
        var reproducibility = report.reproducibility().orElse(null);
        // WASmaxOutBugReportingReportBugRequest.makeReportBugRequest: var n = makeReportBugRequest(e)
        var request = new SmaxBugReportingReportBugRequest(from, description, debugInformationJson,
                deviceLogHandle, mediaUploads, title, category, clientServerJoinKey, reproducibility);
        var requestNode = request.toNode();
        // WAComms.sendSmaxStanza: var r = yield WAComms.sendSmaxStanza(n, t)
        var response = sendNode(requestNode);

        var parsed = SmaxBugReportingReportBugResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxBugReportingReportBugResponse.Success success -> Optional.of(success.taskIdElementValue());
            case SmaxBugReportingReportBugResponse.Error error ->
                    throw new WhatsAppServerRuntimeException("Bug-report rejected: code=" + error.errorCode() + ", text=" + error.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxInAppCommsEventRPC",
            exports = "sendInAppCommsEventRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void reportInAppCommsEvent(InAppCommsEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        var eventPromotionId = event.eventPromotionId().orElse(null);
        var eventType = event.eventType().orElse(null);
        var eventTimestampSec = event.eventTimestampSec();
        var eventLogdata = event.eventLogdata().orElse(null);
        var request = new SmaxInAppCommsEventRequest(eventPromotionId, eventType, eventTimestampSec, eventLogdata);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxInAppCommsEventResponse.of(response, requestNode.build()).orElse(null);
        if (parsed instanceof SmaxInAppCommsEventResponse.Error error) {
            throw new WhatsAppServerRuntimeException("In-app-comms event rejected: code=" + error.errorCode() + ", text=" + error.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOfflineBatchRPC",
            exports = "castOfflineBatchRPC", adaptation = WhatsAppAdaptation.DIRECT)
    public void acknowledgeOfflineBatch(int offlineBatchCount) {
        var request = new SmaxOfflineBatchRequest(offlineBatchCount);
        sendNodeWithNoResponse(request.toNode().build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxPassiveModeActiveIQRPC",
            exports = "sendActiveIQRPC", adaptation = WhatsAppAdaptation.DIRECT)
    public void enableActiveMode() {
        var request = new SmaxPassiveModeActiveIQRequest();
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        SmaxPassiveModeActiveIQResponse.of(response, requestNode.build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxPassiveModePassiveIQRPC",
            exports = "sendPassiveIQRPC", adaptation = WhatsAppAdaptation.DIRECT)
    public void enablePassiveMode() {
        var request = new SmaxPassiveModePassiveIQRequest();
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        SmaxPassiveModePassiveIQResponse.of(response, requestNode.build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxPreKeysFetchKeyBundlesRPC",
            exports = "sendFetchKeyBundlesRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<PreKeyBundleResult> queryPreKeyBundles(List<PreKeyBundleRequest> users) {
        Objects.requireNonNull(users, "users cannot be null");
        var wireUsers = new ArrayList<SmaxPreKeysFetchKeyBundlesRequest.UserKeyRequest>(users.size());
        for (var user : users) {
            wireUsers.add(new SmaxPreKeysFetchKeyBundlesRequest.UserKeyRequest(
                    user.userJid(), user.includeIdentityAttestation()));
        }
        var request = new SmaxPreKeysFetchKeyBundlesRequest(wireUsers);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxPreKeysFetchKeyBundlesResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxPreKeysFetchKeyBundlesResponse.Success success -> Optional.of(toPreKeyBundleResult(success));
            case SmaxPreKeysFetchKeyBundlesResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Pre-key-bundles rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxPreKeysFetchKeyBundlesResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Pre-key-bundles server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /**
     * Projects a Signal pre-key-bundle success reply into a
     * {@link PreKeyBundleResult}, partitioning the per-user entries
     * into the success and per-user error lists.
     *
     * @param success the wire-layer success projection; never
     *                {@code null}
     * @return the projected {@link PreKeyBundleResult}
     */
    private static PreKeyBundleResult toPreKeyBundleResult(SmaxPreKeysFetchKeyBundlesResponse.Success success) {
        var entries = new ArrayList<PreKeyBundleEntry>();
        var errors = new ArrayList<PreKeyBundleEntryError>();
        for (var entry : success.users()) {
            switch (entry) {
                case SmaxPreKeysFetchKeyBundlesResponse.Success.UserKeyBundle bundle -> {
                    var preKey = bundle.preKey()
                            .map(pk -> new SignalPreKey(decodeBigEndianInt(pk.keyId()), pk.keyValue()))
                            .orElse(null);
                    var signedPreKey = new SignalSignedPreKey(decodeBigEndianInt(bundle.signedPreKey().keyId()),
                            bundle.signedPreKey().keyValue(), bundle.signedPreKey().signature());
                    entries.add(new PreKeyBundleEntry(bundle.userJid(),
                            bundle.timestamp().orElse(null), bundle.cloudApi(),
                            bundle.registrationId(), bundle.keyType().orElse(null),
                            bundle.identityKey(), preKey, signedPreKey,
                            bundle.deviceIdentity().orElse(null)));
                }
                case SmaxPreKeysFetchKeyBundlesResponse.Success.UserError userError ->
                        errors.add(new PreKeyBundleEntryError(userError.userJid(),
                                userError.errorCode(), userError.errorText()));
            }
        }
        return new PreKeyBundleResult(entries, errors);
    }

    /**
     * Decodes a 1..4-byte big-endian unsigned integer from the raw
     * bytes used by the Signal pre-key wire format. Pre-key ids are
     * 3 bytes wide (24-bit unsigned); registration ids are 4 bytes
     * (32-bit unsigned).
     *
     * @param bytes the bytes; never {@code null}
     * @return the decoded integer
     */
    private static int decodeBigEndianInt(byte[] bytes) {
        var value = 0;
        for (var b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxPreKeysFetchMissingPreKeysRPC",
            exports = "sendFetchMissingPreKeysRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<PreKeyBundleResult> queryMissingPreKeys(List<MissingPreKeyUserRequest> users) {
        Objects.requireNonNull(users, "users cannot be null");
        var wireUsers = new ArrayList<SmaxPreKeysFetchMissingPreKeysRequest.UserKeyFetchRequest>(users.size());
        for (var user : users) {
            var wireDevices = new ArrayList<SmaxPreKeysFetchMissingPreKeysRequest.DeviceKeyFetchRequest>(user.devices().size());
            for (var device : user.devices()) {
                wireDevices.add(new SmaxPreKeysFetchMissingPreKeysRequest.DeviceKeyFetchRequest(
                        device.deviceId(), device.registrationId()));
            }
            wireUsers.add(new SmaxPreKeysFetchMissingPreKeysRequest.UserKeyFetchRequest(
                    user.userJid(), user.includeIdentityAttestation(), wireDevices));
        }
        var request = new SmaxPreKeysFetchMissingPreKeysRequest(wireUsers);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxPreKeysFetchMissingPreKeysResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxPreKeysFetchMissingPreKeysResponse.Success success -> Optional.of(toMissingPreKeyResult(success));
            case SmaxPreKeysFetchMissingPreKeysResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Missing-pre-keys rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxPreKeysFetchMissingPreKeysResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Missing-pre-keys server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /**
     * Projects a missing-pre-keys success reply into a flat
     * {@link PreKeyBundleResult}, emitting one
     * {@link PreKeyBundleEntry} per device the relay resolved.
     *
     * <p>Each per-device entry's JID is the user JID with the device
     * id encoded into the {@code device} component, matching the way
     * Signal session lookups address per-device sessions on the
     * client side.
     *
     * @param success the wire-layer success projection; never
     *                {@code null}
     * @return the projected {@link PreKeyBundleResult}
     */
    private static PreKeyBundleResult toMissingPreKeyResult(SmaxPreKeysFetchMissingPreKeysResponse.Success success) {
        var entries = new ArrayList<PreKeyBundleEntry>();
        var errors = new ArrayList<PreKeyBundleEntryError>();
        for (var entry : success.users()) {
            switch (entry) {
                case SmaxPreKeysFetchMissingPreKeysResponse.Success.UserDeviceBundle bundle -> {
                    for (var device : bundle.devices()) {
                        var preKey = device.preKey()
                                .map(pk -> new SignalPreKey(decodeBigEndianInt(pk.keyId()), pk.keyValue()))
                                .orElse(null);
                        var signedPreKey = new SignalSignedPreKey(decodeBigEndianInt(device.signedPreKey().keyId()),
                                device.signedPreKey().keyValue(), device.signedPreKey().signature());
                        var deviceJid = bundle.userJid().withDevice(device.deviceId());
                        entries.add(new PreKeyBundleEntry(deviceJid,
                                device.timestamp().orElse(null), device.cloudApi(),
                                device.registrationId(), device.keyType().orElse(null),
                                device.identityKey(), preKey, signedPreKey,
                                device.deviceIdentity().orElse(null)));
                    }
                }
                case SmaxPreKeysFetchMissingPreKeysResponse.Success.UserError userError ->
                        errors.add(new PreKeyBundleEntryError(userError.userJid(),
                                userError.errorCode(), userError.errorText()));
            }
        }
        return new PreKeyBundleResult(entries, errors);
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxPrivatestatsSignCredentialRPC",
            exports = "sendSignCredentialRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<SignedAttributionCredential> signAnonymousAttributionCredential(byte[] blindedCredentialElementValue,
                                                                                     String projectNameElementValue) {
        Objects.requireNonNull(blindedCredentialElementValue, "blindedCredentialElementValue cannot be null");
        Objects.requireNonNull(projectNameElementValue, "projectNameElementValue cannot be null");
        var request = new SmaxPrivatestatsSignCredentialRequest(blindedCredentialElementValue, projectNameElementValue);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxPrivatestatsSignCredentialResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxPrivatestatsSignCredentialResponse.Success success ->
                    Optional.of(new SignedAttributionCredentialBuilder()
                            .signTimestamp(success.signCredentialT())
                            .signedCredential(success.signedCredentialElementValue())
                            .acsPublicKey(success.acsPublicKeyElementValue())
                            .dleqProofC(success.dleqProofCElementValue())
                            .dleqProofS(success.dleqProofSElementValue())
                            .projectName(success.projectNameElementValue())
                            .build());
            case SmaxPrivatestatsSignCredentialResponse.ErrorNoRetry clientError ->
                    throw new WhatsAppServerRuntimeException("Sign-credential rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxPrivatestatsSignCredentialResponse.ErrorRetry serverError ->
                    throw new WhatsAppServerRuntimeException("Sign-credential server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText());
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxPsaChatBlockGetRPC",
            exports = "sendChatBlockGetRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean queryPublicAnnouncementBlocked() {
        var request = new SmaxPsaChatBlockGetRequest();
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxPsaChatBlockGetResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Psa-chat-block-get returned no parseable variant");
            case SmaxPsaChatBlockGetResponse.Success success ->
                    success.blockingStatus() == SmaxPsaChatBlockGetBlockingStatus.BLOCKED;
            case SmaxPsaChatBlockGetResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Psa-chat-block-get error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxPsaChatBlockSetRPC",
            exports = "sendChatBlockSetRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void editPublicAnnouncementBlocked(PsaChatBlockAction blockingAction) {
        Objects.requireNonNull(blockingAction, "blockingAction cannot be null");
        var request = new SmaxPsaChatBlockSetRequest(blockingAction.wireValue());
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxPsaChatBlockSetResponse.of(response, requestNode.build()).orElse(null);
        if (parsed instanceof SmaxPsaChatBlockSetResponse.ServerError serverError) {
            throw new WhatsAppServerRuntimeException("Psa-chat-block-set error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxPushConfigSetRPC",
            exports = "sendSetRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void editPushConfig(PushConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        var variant = toPushConfigWireVariant(config);
        var request = new SmaxPushConfigSetRequest(variant);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxPushConfigSetResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case SmaxPushConfigSetResponse.Success _ -> {}
            case SmaxPushConfigSetResponse.InternalServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Push-config server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText());
            case SmaxPushConfigSetResponse.Conflict conflict ->
                    throw new WhatsAppServerRuntimeException("Push-config conflict: code=" + conflict.errorCode() + ", text=" + conflict.errorText());
        }
    }

    /**
     * Translates a caller-friendly {@link PushConfig} model into the
     * wire-layer {@link SmaxPushConfigSetSetVariant} sealed
     * alternation.
     *
     * @param config the caller-friendly model; never {@code null}
     * @return the wire-layer variant
     */
    private static SmaxPushConfigSetSetVariant toPushConfigWireVariant(PushConfig config) {
        return switch (config) {
            case PushConfig.Clear clear -> new SmaxPushConfigSetSetVariant.Clear(clear.platform().orElse(null));
            case PushConfig.Fcm fcm -> {
                var items = new ArrayList<SmaxPushConfigSetConfigVariant.AndroidConfig.AndroidMuteItem>(fcm.muteItems().size());
                for (var item : fcm.muteItems()) {
                    items.add(new SmaxPushConfigSetConfigVariant.AndroidConfig.AndroidMuteItem(
                            item.groupJid(), item.muteMarker()));
                }
                yield new SmaxPushConfigSetSetVariant.Config(new SmaxPushConfigSetConfigVariant.AndroidConfig(items));
            }
            case PushConfig.Apns apns -> {
                var items = new ArrayList<SmaxPushConfigSetConfigVariant.AppleConfig.AppleItem>(apns.items().size());
                for (var item : apns.items()) {
                    items.add(new SmaxPushConfigSetConfigVariant.AppleConfig.AppleItem(
                            item.jid(), item.muteMarker().orElse(null),
                            item.notifyMarker().orElse(null), item.callMarker().orElse(null)));
                }
                yield new SmaxPushConfigSetSetVariant.Config(new SmaxPushConfigSetConfigVariant.AppleConfig(
                        apns.platform(), apns.version2(),
                        apns.id().orElse(null), apns.voipToken().orElse(null),
                        apns.previewToggle(), apns.defaultToggle(), apns.groupsToggle(),
                        apns.callToggle(), apns.statusSoundToggle().orElse(null),
                        apns.language(), apns.locale(), apns.backgroundLocationToggle().orElse(null),
                        apns.nseVersion().orElse(null), apns.nseCallToggle().orElse(null),
                        apns.nseReadToggle().orElse(null), apns.nseRetryToggle().orElse(null),
                        apns.regPushToggle().orElse(null), apns.pkey().orElse(null),
                        apns.voipPayloadType(), apns.settingsMask().orElse(null),
                        apns.appMuteMask().orElse(null), apns.appleWatchId().orElse(null),
                        apns.appleWatchPkey().orElse(null), items));
            }
            case PushConfig.Web web -> new SmaxPushConfigSetSetVariant.Config(
                    new SmaxPushConfigSetConfigVariant.WebConfig(web.endpoint(), web.auth(),
                            web.p256dh(), web.language().orElse(null), web.locale().orElse(null)));
            case PushConfig.Windows windows -> new SmaxPushConfigSetSetVariant.Config(
                    new SmaxPushConfigSetConfigVariant.WnsConfig(windows.ring().orElse(null), windows.wnsId()));
            case PushConfig.Enterprise enterprise -> new SmaxPushConfigSetSetVariant.Config(
                    new SmaxPushConfigSetConfigVariant.EnterpriseConfig(enterprise.enterpriseId()));
            case PushConfig.Facebook facebook -> new SmaxPushConfigSetSetVariant.Config(
                    new SmaxPushConfigSetConfigVariant.FbConfig(facebook.appId(), facebook.deviceId(),
                            facebook.fbId().orElse(null)));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxReceiptPublishViewRPC",
            exports = "sendReceiptPublishViewRPC", adaptation = WhatsAppAdaptation.DIRECT)
    public void publishViewReceipt(ViewReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt cannot be null");
        var receiptId = receipt.receiptId();
        var to = receipt.to();
        var hasStatusClass = receipt.hasStatusClass();
        var itemServerIds = receipt.itemServerIds();
        var request = new SmaxReceiptPublishViewRequest(receiptId, to, hasStatusClass, itemServerIds);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        SmaxReceiptPublishViewResponse.of(response, requestNode.build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxStatsSendBufferRPC",
            exports = "sendStatsSendBufferRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendStatsBuffer(Instant addT, byte[] addElementValue) {
        Objects.requireNonNull(addT, "addT cannot be null");
        Objects.requireNonNull(addElementValue, "addElementValue cannot be null");
        var request = new SmaxStatsSendBufferRequest(addT.getEpochSecond(), addElementValue);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxStatsSendBufferResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case SmaxStatsSendBufferResponse.Success _ -> {}
            case SmaxStatsSendBufferResponse.ErrorNoRetry clientError ->
                    throw new WhatsAppServerRuntimeException("Stats-buffer rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxStatsSendBufferResponse.ErrorRetry serverError ->
                    throw new WhatsAppServerRuntimeException("Stats-buffer server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText());
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxSupportMessageFeedbackSendFeedbackRPC",
            exports = "sendSendFeedbackRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendSupportFeedback(JidProvider fromProvider, String messageId,
                                    List<String> feedbackKinds) {
        var from = Objects.requireNonNull(fromProvider, "from cannot be null").toJid();
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(feedbackKinds, "feedbackKinds cannot be null");
        var request = new SmaxSendFeedbackRequest(from, messageId, feedbackKinds);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxSendFeedbackResponse.of(response, requestNode.build()).orElse(null);
        switch (parsed) {
            case SmaxSendFeedbackResponse.Success _ -> {}
            case SmaxSendFeedbackResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Send-feedback rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxSendFeedbackResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Send-feedback server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxSupportContactFormRPC",
            exports = "sendContactFormRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<SupportTicketAcknowledgement> sendSupportContactForm(SupportContactForm form) {
        Objects.requireNonNull(form, "form cannot be null");
        var from = form.from();
        var description = form.description();
        var topic = form.topic();
        var topicId = form.topicId().orElse(null);
        var debugInformationJson = form.debugInformationJson().orElse(null);
        var uploadedLogsId = form.uploadedLogsId().orElse(null);
        var additionalAttributesContextFlow = form.additionalAttributesContextFlow().orElse(null);
        var request = new SmaxContactFormRequest(from, description, topic, topicId, debugInformationJson,
                uploadedLogsId, additionalAttributesContextFlow);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxContactFormResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxContactFormResponse.ContactFormResponseSuccess success -> Optional.of(
                    new SupportTicketAcknowledgement(success.responseTicketIdElementValue(),
                            success.responseGroupJidElementValue(),
                            success.responseMessageElementValue()));
            case SmaxContactFormResponse.ContactFormResponseRetryableError retryable ->
                    throw new WhatsAppServerRuntimeException("Contact-form retryable error: code="
                            + retryable.responseErrorCode()
                            + ", next_retry_ts=" + retryable.responseNextRetryTs().orElse(null));
            case SmaxContactFormResponse.ContactFormResponseError error ->
                    throw new WhatsAppServerRuntimeException("Contact-form rejected: code="
                            + error.errorCode()
                            + ", text=" + error.errorText().orElse(null)
                            + ", tos_version=" + error.tosVersion().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxSpamIndividualReportRPC",
            exports = "sendIndividualReportRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void reportIndividualForSpam(IndividualSpamReport report) {
        Objects.requireNonNull(report, "report cannot be null");
        var target = report.target();
        var spamFlow = report.spamFlow();
        var isKnownChat = report.isKnownChat().orElse(null);
        var reportedMessageIds = report.reportedMessageIds();
        var builder = SmaxIndividualReportRequest.builder()
                .spamListJid(target)
                .spamListSpamFlow(spamFlow);
        if (isKnownChat != null) {
            builder.spamListIsKnownChat(isKnownChat);
        }
        for (var messageId : reportedMessageIds) {
            Objects.requireNonNull(messageId, "reportedMessageIds entries cannot be null");
            builder.addMessageChild(new NodeBuilder()
                    .description("message")
                    .attribute("id", messageId)
                    .build());
        }
        var request = builder.build();
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxIndividualReportResponse.of(response, requestNode.build()).orElse(null);
        if (parsed instanceof SmaxIndividualReportResponse.Error error) {
            throw new WhatsAppServerRuntimeException("Individual-report rejected: code=" + error.errorCode() + ", text=" + error.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxSpamGroupReportRPC",
            exports = "sendGroupReportRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void reportGroupForSpam(GroupSpamReport report) {
        Objects.requireNonNull(report, "report cannot be null");
        var group = report.group();
        var adder = report.adder().orElse(null);
        var spamFlow = report.spamFlow();
        var subject = report.subject().orElse(null);
        var isKnownChat = report.isKnownChat().orElse(null);
        var reportedMessageIds = report.reportedMessageIds();
        var builder = SmaxGroupReportRequest.builder()
                .spamListJid(group)
                .spamListSpamFlow(spamFlow);
        if (adder != null) {
            builder.spamListSource(adder);
        }
        if (subject != null) {
            builder.spamListSubject(subject);
        }
        if (isKnownChat != null) {
            builder.spamListIsKnownChat(isKnownChat);
        }
        for (var messageId : reportedMessageIds) {
            Objects.requireNonNull(messageId, "reportedMessageIds entries cannot be null");
            builder.addMessageChild(new NodeBuilder()
                    .description("message")
                    .attribute("id", messageId)
                    .build());
        }
        var request = builder.build();
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxGroupReportResponse.of(response, requestNode.build()).orElse(null);
        if (parsed instanceof SmaxGroupReportResponse.Error error) {
            throw new WhatsAppServerRuntimeException("Group-report rejected: code=" + error.errorCode() + ", text=" + error.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxSpamNewsletterReportRPC",
            exports = "sendNewsletterReportRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void reportNewsletterForSpam(NewsletterSpamReport report) {
        Objects.requireNonNull(report, "report cannot be null");
        var spamListJid = report.spamListJid();
        var spamListSpamFlow = report.spamListSpamFlow();
        var spamListSubject = report.spamListSubject().orElse(null);
        var messages = report.messages().stream()
                .map(e -> new SmaxNewsletterReportMessageEntry(
                        e.messageFrom().orElse(null),
                        e.messageTimestamp(),
                        e.messageId().orElse(null)))
                .toList();
        var request = new SmaxNewsletterReportRequest(spamListJid, spamListSpamFlow, spamListSubject, messages);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxNewsletterReportResponse.of(response, requestNode.build()).orElse(null);
        if (parsed instanceof SmaxNewsletterReportResponse.Error error) {
            throw new WhatsAppServerRuntimeException("Newsletter-report rejected: code=" + error.errorCode() + ", text=" + error.errorText().orElse(null));
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxSpamStatusReportRPC",
            exports = "sendStatusReportRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxSpamStatusReportV2RPC",
            exports = "sendStatusReportV2RPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public void reportStatus(MessageInfo status, String reason, String subject) {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        long timestamp = status.timestamp()
                .map(Instant::getEpochSecond)
                .orElseThrow(() -> new IllegalArgumentException("status has no timestamp"));
        switch (status) {
            case ChatMessageInfo chat -> {
                var parent = chat.key().parentJid()
                        .orElseThrow(() -> new IllegalArgumentException("status has no parent jid"));
                if (!parent.isStatusBroadcastAccount()) {
                    throw new IllegalArgumentException("not a status post: parent jid " + parent + " is not status@broadcast");
                }
                var owner = chat.senderJid()
                        .orElseThrow(() -> new IllegalArgumentException("status has no sender"));
                var messageId = chat.key().id()
                        .orElseThrow(() -> new IllegalArgumentException("status has no message id"));
                var request = SmaxStatusReportRequest.builder()
                        .spamListJid(owner)
                        .spamListSpamFlow(reason)
                        .messageFrom(owner)
                        .messageTimestamp(timestamp)
                        .messageId(messageId)
                        .build();
                var requestNode = request.toNode();
                var response = sendNode(requestNode);
                var parsed = SmaxStatusReportResponse.of(response, requestNode.build()).orElse(null);
                if (parsed instanceof SmaxStatusReportResponse.Error error) {
                    throw new WhatsAppServerRuntimeException("Status-report rejected: code=" + error.errorCode() + ", text=" + error.errorText().orElse(null));
                }
            }
            case NewsletterMessageInfo newsletter -> {
                var newsletterJid = newsletter.key().parentJid()
                        .orElseThrow(() -> new IllegalArgumentException("status has no newsletter jid"));
                var request = new SmaxStatusReportV2Request(newsletterJid, reason,
                        newsletter.serverId(), timestamp, subject, null);
                var requestNode = request.toNode();
                var response = sendNode(requestNode);
                var parsed = SmaxStatusReportV2Response.of(response, requestNode.build()).orElse(null);
                if (parsed instanceof SmaxStatusReportV2Response.Error error) {
                    throw new WhatsAppServerRuntimeException("Status-report rejected: code=" + error.errorCode() + ", text=" + error.errorText().orElse(null));
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxUnifiedSessionShareRPC",
            exports = "castShareRPC", adaptation = WhatsAppAdaptation.DIRECT)
    public void joinUnifiedSession(String unifiedSessionId) {
        Objects.requireNonNull(unifiedSessionId, "unifiedSessionId cannot be null");
        var request = new SmaxUnifiedSessionShareRequest(unifiedSessionId);
        sendNodeWithNoResponse(request.toNode().build());
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxUserNoticeGetDisclosuresRPC",
            exports = "sendGetDisclosuresRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<UserNoticeBundle> queryPendingUserNotices(Instant getUserDisclosuresT) {
        Objects.requireNonNull(getUserDisclosuresT, "getUserDisclosuresT cannot be null");
        var request = new SmaxUserNoticeGetDisclosuresRequest(getUserDisclosuresT.getEpochSecond());
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxUserNoticeGetDisclosuresResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxUserNoticeGetDisclosuresResponse.ClientSuccess success -> {
                var notices = new ArrayList<UserNotice>(success.notices().size());
                for (var entry : success.notices()) {
                    notices.add(new UserNotice(entry.timestampSeconds(), entry.version(),
                            entry.type(), entry.noticeId(), entry.stage()));
                }
                yield Optional.of(new UserNoticeBundle(notices));
            }
            case SmaxUserNoticeGetDisclosuresResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("User-notice-disclosures rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxUserNoticeGetDisclosuresResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("User-notice-disclosures server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxUserNoticeGetDisclosureStageByIdsRPC",
            exports = "sendGetDisclosureStageByIdsRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<UserNoticeStage> queryUserNoticeStages(List<UserNoticeStageQuery> queries) {
        Objects.requireNonNull(queries, "queries cannot be null");
        var wireQueries = new ArrayList<SmaxUserNoticeGetDisclosureStageByIdsRequest.DisclosureStageQuery>(queries.size());
        for (var query : queries) {
            wireQueries.add(new SmaxUserNoticeGetDisclosureStageByIdsRequest.DisclosureStageQuery(
                    query.disclosureId(), query.timestampSeconds()));
        }
        var request = new SmaxUserNoticeGetDisclosureStageByIdsRequest(wireQueries);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxUserNoticeGetDisclosureStageByIdsResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> List.of();
            case SmaxUserNoticeGetDisclosureStageByIdsResponse.ClientSuccess success -> {
                var stages = new ArrayList<UserNoticeStage>(success.notices().size());
                for (var entry : success.notices()) {
                    stages.add(new UserNoticeStage(entry.timestampSeconds(),
                            entry.version().orElse(null), entry.type().orElse(null),
                            entry.noticeId(), entry.stage()));
                }
                yield List.copyOf(stages);
            }
            case SmaxUserNoticeGetDisclosureStageByIdsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("User-notice-stages rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxUserNoticeGetDisclosureStageByIdsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("User-notice-stages server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxVoipLinkCreateRPC",
            exports = "sendLinkCreateRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebVoipCreateCallLinkJob",
            exports = "createCallLinkJob", adaptation = WhatsAppAdaptation.ADAPTED)
    public CallLink createCallLink(CallLinkCreate create) {
        Objects.requireNonNull(create, "create cannot be null");
        var media = create.media();
        var callCreator = create.callCreator();
        var callId = create.callId().orElse(null);
        var creatorUsername = create.creatorUsername().orElse(null);
        var waitingRoomEnabled = create.waitingRoomEnabled();
        var eventStartTime = create.eventStartTime().orElse(null);
        var request = new SmaxLinkCreateRequest(media.wireValue(), callCreator, callId,
                creatorUsername, waitingRoomEnabled, eventStartTime);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxLinkCreateResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("Link-create: unparseable response: " + response);
            case SmaxLinkCreateResponse.Success success -> {
                var echoedMedia = success.linkCreateMedia()
                        .flatMap(CallLinkMedia::ofWire)
                        .orElse(media);
                yield new CallLinkBuilder()
                        .token(success.linkCreateToken())
                        .media(echoedMedia)
                        .creator(success.linkCreateCallCreator().orElse(null))
                        .creatorUsername(creatorUsername)
                        .callId(success.linkCreateCallId().orElse(null))
                        .scheduled(eventStartTime != null)
                        .build();
            }
            case SmaxLinkCreateResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Link-create rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxVoipLinkQueryRPC",
            exports = "sendLinkQueryRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<CallLink> queryCallLink(String token, CallLinkMedia media, String action) {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(media, "media cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        var request = new SmaxLinkQueryRequest(token, media.wireValue(), action);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxLinkQueryResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxLinkQueryResponse.Success success -> {
                var resolvedMedia = CallLinkMedia.ofWire(success.linkQueryMedia()).orElse(media);
                var waitingRoom = success.linkQueryWaitingRoom()
                        .map(wr -> new CallLinkWaitingRoomBuilder()
                                .admin(wr.isAdmin())
                                .enabled("1".equals(wr.enabled()))
                                .build())
                        .orElse(null);
                yield Optional.of(new CallLinkBuilder()
                        .token(success.linkQueryToken())
                        .media(resolvedMedia)
                        .creator(success.linkQueryLinkCreator())
                        .creatorPn(success.linkQueryLinkCreatorPn().orElse(null))
                        .creatorUsername(success.linkQueryLinkCreatorUsername().orElse(null))
                        .scheduled(success.hasLinkQueryEvent())
                        .waitingRoom(waitingRoom)
                        .build());
            }
            case SmaxLinkQueryResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Link-query rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxVoipWaitingRoomToggleCallLinkRPC",
            exports = "sendToggleCallLinkRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebVoipWaitingRoomToggleJob",
            exports = "toggleWaitingRoomForCallLink", adaptation = WhatsAppAdaptation.ADAPTED)
    public void editCallLinkWaitingRoom(boolean enabled, String linkToken, CallLinkMedia media) {
        Objects.requireNonNull(linkToken, "linkToken cannot be null");
        Objects.requireNonNull(media, "media cannot be null");
        var request = new SmaxWaitingRoomToggleCallLinkRequest(enabled ? "1" : "0",
                linkToken, media.wireValue());
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxWaitingRoomToggleCallLinkResponse.of(response, requestNode.build()).orElse(null);
        if (parsed instanceof SmaxWaitingRoomToggleCallLinkResponse.ClientError clientError) {
            throw new WhatsAppServerRuntimeException("Waiting-room-toggle rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
        }
    }
    //</editor-fold>

    //<editor-fold desc="Federated identity (Waffle)">

    /**
     * Converts a domain-model {@link FederatedRsaEncryption} into the
     * wire-level {@link SmaxWaffleRsaEncryptionMetadata} understood by the
     * SMAX request builders.
     *
     * @param encryption the non-{@code null} domain-model envelope
     * @return the wire-level mixin
     * @throws NullPointerException if {@code encryption} is {@code null} or
     *                              any of its four blob fields is absent
     */
    private static SmaxWaffleRsaEncryptionMetadata toSmaxEncryption(FederatedRsaEncryption encryption) {
        Objects.requireNonNull(encryption, "encryption cannot be null");
        var encryptedKey = encryption.encryptedKey().orElseThrow(() -> new NullPointerException("encryption.encryptedKey cannot be null"));
        var nonce = encryption.nonce().orElseThrow(() -> new NullPointerException("encryption.nonce cannot be null"));
        var encryptedData = encryption.encryptedData().orElseThrow(() -> new NullPointerException("encryption.encryptedData cannot be null"));
        var authTag = encryption.authTag().orElseThrow(() -> new NullPointerException("encryption.authTag cannot be null"));
        return new SmaxWaffleRsaEncryptionMetadata(encryptedKey, nonce, encryptedData, authTag);
    }

    /**
     * Converts a wire-level {@link SmaxWaffleRsaEncryptionMetadata} into the
     * domain-model {@link FederatedRsaEncryption}.
     *
     * @param metadata the non-{@code null} wire-level mixin
     * @return the domain-model envelope
     */
    private static FederatedRsaEncryption fromSmaxEncryption(SmaxWaffleRsaEncryptionMetadata metadata) {
        return new FederatedRsaEncryptionBuilder()
                .encryptedKey(metadata.encryptedKey())
                .nonce(metadata.nonce())
                .encryptedData(metadata.encryptedData())
                .authTag(metadata.authTag())
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxWaffleStateExistsRPC",
            exports = "sendStateExistsRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<FederatedIdentityState> checkFederatedIdentityExists(Instant timestamp) {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        var request = new SmaxWaffleStateExistsRequest(timestamp.getEpochSecond());
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxWaffleStateExistsResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxWaffleStateExistsResponse.Success success -> Optional.of(new FederatedIdentityStateBuilder()
                    .state(success.wfState())
                    .suspended(success.suspended())
                    .noPersonalRecovery(success.suspendedNpr().orElse(null))
                    .build());
            case SmaxWaffleStateExistsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Waffle-state-exists rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxWaffleStateExistsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Waffle-state-exists server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxWaffleWFPingRPC",
            exports = "sendWFPingRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<FederatedIdentityPing> sendFederatedIdentityPing(FederatedRsaEncryption encryption,
                                                                      Instant timestamp, byte[] fbid) {
        Objects.requireNonNull(encryption, "encryption cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(fbid, "fbid cannot be null");
        var request = new SmaxWaffleWFPingRequest(toSmaxEncryption(encryption), timestamp.getEpochSecond(), fbid);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxWaffleWFPingResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxWaffleWFPingResponse.Success success -> Optional.of(new FederatedIdentityPingBuilder()
                    .pingInterval(success.pingInterval())
                    .build());
            case SmaxWaffleWFPingResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Waffle-ping rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxWaffleWFPingResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Waffle-ping server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxWaffleGetCertificateRPC",
            exports = "sendGetCertificateRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<FederatedIdentityCertificate> queryFederatedIdentityCertificate(Instant timestamp,
                                                                                     boolean hasPayloadEncCertificates,
                                                                                     boolean hasPasswordPem) {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        var request = new SmaxWaffleGetCertificateRequest(timestamp.getEpochSecond(), hasPayloadEncCertificates, hasPasswordPem);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxWaffleGetCertificateResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxWaffleGetCertificateResponse.Success success -> Optional.of(new FederatedIdentityCertificateBuilder()
                    .replyTimestamp(success.replyTimestamp())
                    .encryptionPem(success.encryptionPem().map(DefaultWhatsAppClient::toModelPem).orElse(null))
                    .signaturePem(success.signaturePem().map(DefaultWhatsAppClient::toModelPem).orElse(null))
                    .passwordPem(success.passwordPem().map(DefaultWhatsAppClient::toModelPem).orElse(null))
                    .build());
            case SmaxWaffleGetCertificateResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Waffle-certificate rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxWaffleGetCertificateResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Waffle-certificate server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /**
     * Converts a wire-level {@link SmaxWaffleGetCertificateResponse.Pem}
     * record into the domain-model {@link FederatedIdentityPem}.
     *
     * @param pem the non-{@code null} wire-level PEM record
     * @return the domain-model PEM
     */
    private static FederatedIdentityPem toModelPem(SmaxWaffleGetCertificateResponse.Pem pem) {
        return new FederatedIdentityPemBuilder()
                .ttl(pem.ttl())
                .keyId(pem.keyId())
                .pem(pem.pem())
                .build();
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxWaffleRefreshAccessTokensRPC",
            exports = "sendRefreshAccessTokensRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<FederatedAccessTokenRefresh> refreshFederatedIdentityAccessTokens(FederatedRsaEncryption encryption,
                                                                                       Instant timestamp, byte[] fbid) {
        Objects.requireNonNull(encryption, "encryption cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(fbid, "fbid cannot be null");
        var request = new SmaxWaffleRefreshAccessTokensRequest(toSmaxEncryption(encryption), timestamp.getEpochSecond(), fbid);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxWaffleRefreshAccessTokensResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxWaffleRefreshAccessTokensResponse.Success success -> Optional.of(new FederatedAccessTokenRefreshBuilder()
                    .encryption(fromSmaxEncryption(success.encryptionMetadata()))
                    .build());
            case SmaxWaffleRefreshAccessTokensResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Waffle-refresh-tokens rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxWaffleRefreshAccessTokensResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Waffle-refresh-tokens server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxWaffleEncryptedPayloadRequestRPC",
            exports = "sendEncryptedPayloadRequestRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<FederatedEncryptedAction> sendFederatedIdentityEncryptedPayload(FederatedRsaEncryption encryption,
                                                                                     Instant timestamp, byte[] fbid, byte[] action) {
        Objects.requireNonNull(encryption, "encryption cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(fbid, "fbid cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        var request = new SmaxWaffleEncryptedPayloadRequestRequest(toSmaxEncryption(encryption), timestamp.getEpochSecond(), fbid, action);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxWaffleEncryptedPayloadRequestResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> Optional.empty();
            case SmaxWaffleEncryptedPayloadRequestResponse.Success success -> Optional.of(new FederatedEncryptedActionBuilder()
                    .encryption(fromSmaxEncryption(success.encryptionMetadata()))
                    .deleted(success.wfDeleted().orElse(null))
                    .build());
            case SmaxWaffleEncryptedPayloadRequestResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("Waffle-encrypted-payload rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxWaffleEncryptedPayloadRequestResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("Waffle-encrypted-payload server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxWaffleGenerateWAEntACUserRPC",
            exports = "sendGenerateWAEntACUserRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    public FederatedEnterpriseCustomer createEnterpriseAuthenticatedCustomer(EnterpriseAuthenticatedCustomerCreate create) {
        Objects.requireNonNull(create, "create cannot be null");
        var encryption = create.encryption();
        var timestamp = create.timestamp();
        var disclosureId = create.disclosureId();
        var disclosureVersion = create.disclosureVersion();
        var disclosureLg = create.disclosureLg();
        var disclosureLc = create.disclosureLc();
        var request = new SmaxWaffleGenerateWAEntACUserRequest(toSmaxEncryption(encryption), timestamp.getEpochSecond(), disclosureId,
                disclosureVersion, disclosureLg, disclosureLc);
        var requestNode = request.toNode();
        var response = sendNode(requestNode);
        var parsed = SmaxWaffleGenerateWAEntACUserResponse.of(response, requestNode.build()).orElse(null);
        return switch (parsed) {
            case null -> throw new WhatsAppServerRuntimeException("WAEnt-AC-user: unparseable response: " + response);
            case SmaxWaffleGenerateWAEntACUserResponse.Success success -> new FederatedEnterpriseCustomerBuilder()
                    .encryption(fromSmaxEncryption(success.encryptionMetadata()))
                    .build();
            case SmaxWaffleGenerateWAEntACUserResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException("WAEnt-AC-user rejected: code=" + clientError.errorCode() + ", text=" + clientError.errorText().orElse(null));
            case SmaxWaffleGenerateWAEntACUserResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException("WAEnt-AC-user server error: code=" + serverError.errorCode() + ", text=" + serverError.errorText().orElse(null));
        };
    }

    /** {@inheritDoc} */
    @Override
    public String queryAbPropString(ABProp prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        return abPropsService.getString(prop);
    }

    /** {@inheritDoc} */
    @Override
    public boolean queryAbPropBool(ABProp prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        return abPropsService.getBool(prop);
    }

    /** {@inheritDoc} */
    @Override
    public int queryAbPropInt(ABProp prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        return abPropsService.getInt(prop);
    }

    /** {@inheritDoc} */
    @Override
    public long queryAbPropLong(ABProp prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        return abPropsService.getLong(prop);
    }

    /** {@inheritDoc} */
    @Override
    public double queryAbPropDouble(ABProp prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        return abPropsService.getDouble(prop);
    }
    // </editor-fold>

    //<editor-fold desc="App-state sync (additional exposures)">
    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebExternalBetaOptInAction", exports = "setOptInBetaAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editWebBetaEnrollment(boolean enrolled) {
        var mutation = externalWebBetaMutationFactory.getExternalWebBetaMutation(enrolled);
        webAppStateService.pushPatches(ExternalWebBetaAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipRelayAllCallsSettingSync", exports = "sendMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editAlwaysRelayCalls(boolean enabled) {
        var mutation = voipRelayAllCallsMutationFactory.getVoipRelayAllCallsMutation(Instant.now(), enabled);
        webAppStateService.pushPatches(PrivacySettingRelayAllCalls.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSettingsBridgeApi", exports = "private_processing_setting",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editAiPrivateProcessing(boolean enabled) {
        var mutation = privateProcessingSettingMutationFactory.getPrivateProcessingMutation(enabled);
        webAppStateService.pushPatches(PrivateProcessingSettingAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCTWADetectedOutcomeOnboardingStatusUpdateAction",
            exports = "ctwaDetectedOutcomeOnboardingStatusUpdateAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editAutomatedDetections(boolean enabled) {
        var mutation = detectedOutcomesStatusMutationFactory.getDetectedOutcomesStatusMutation(enabled);
        webAppStateService.pushPatches(DetectedOutcomesStatusAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNuxSync", exports = "acknowledgeNux",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void dismissOnboardingHint(String hintId) {
        Objects.requireNonNull(hintId, "hintId cannot be null");
        var mutation = nuxActionMutationFactory.getNuxMutation(hintId, Instant.now(), true);
        webAppStateService.pushPatches(NuxAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNuxSync", exports = "unAcknowledgeNux",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void restoreOnboardingHint(String hintId) {
        Objects.requireNonNull(hintId, "hintId cannot be null");
        var mutation = nuxActionMutationFactory.getNuxMutation(hintId, Instant.now(), false);
        webAppStateService.pushPatches(NuxAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebInteractiveMessageSync", exports = "sendDisableCTAMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void disableInteractiveMessageButton(String buttonId) {
        Objects.requireNonNull(buttonId, "buttonId cannot be null");
        var mutation = interactiveMessageMutationFactory.getDisableButtonMutation(buttonId);
        webAppStateService.pushPatches(InteractiveMessageAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebRecentEmojiCollection", exports = "increment",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editRecentEmojiUsage(List<RecentEmojiWeight> usage) {
        Objects.requireNonNull(usage, "usage cannot be null");
        var mutation = recentEmojiWeightsMutationFactory.getRecentEmojiWeightsMutation(usage);
        webAppStateService.pushPatches(RecentEmojiWeightsAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editContact(ContactEdit edit) {
        Objects.requireNonNull(edit, "edit cannot be null");
        var mutation = contactActionMutationFactory.getContactSyncMutation(
                edit.contact(),
                edit.firstName().orElse(null),
                edit.fullName().orElse(null),
                false,
                edit.lidJid().orElse(null),
                edit.syncToAddressbook().orElse(null),
                edit.username().orElse(null));
        webAppStateService.pushPatches(ContactAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteContact(JidProvider contact) {
        Objects.requireNonNull(contact, "contact cannot be null");
        var mutation = contactActionMutationFactory.getContactSyncMutation(
                contact.toJid(), null, null, true, null, null, null);
        webAppStateService.pushPatches(ContactAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCallLogSync", exports = "getCallLogMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void addCallLog(CallLog entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        var callId = entry.callId()
                .orElseThrow(() -> new IllegalArgumentException("entry must carry a callId"));
        var callerJid = entry.callCreatorJid()
                .orElseThrow(() -> new IllegalArgumentException("entry must carry a callCreatorJid"));
        var fromMe = !entry.isIncoming();
        var mutation = callLogMutationFactory.getCallLogMutation(Instant.now(), callerJid, callId, fromMe, entry);
        webAppStateService.pushPatches(CallLogAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync",
            exports = "getCtwaPerCustomerDataSharingMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editAdvertiserDataSharing(Jid customer, boolean enabled) {
        Objects.requireNonNull(customer, "customer cannot be null");
        var mutation = ctwaPerCustomerDataSharingMutationFactory.getCtwaPerCustomerDataSharingMutation(customer, enabled);
        webAppStateService.pushPatches(CtwaPerCustomerDataSharingAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomPaymentMethodsSync",
            exports = "getCustomPaymentMethodSetMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editCustomPaymentMethods(List<CustomPaymentMethod> methods) {
        Objects.requireNonNull(methods, "methods cannot be null");
        var action = new CustomPaymentMethodsActionBuilder()
                .customPaymentMethods(methods)
                .build();
        var mutation = customPaymentMethodsMutationFactory.getCustomPaymentMethodSetMutation(action);
        webAppStateService.pushPatches(CustomPaymentMethodsAction.COLLECTION_NAME, List.of(mutation));
    }

    /** {@inheritDoc} */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPaymentTosSync", exports = "getPaymentTosSetMutation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void editPaymentTos(PaymentTosAction.PaymentNotice notice, boolean accepted) {
        Objects.requireNonNull(notice, "notice cannot be null");
        var action = new PaymentTosActionBuilder()
                .paymentNotice(notice)
                .accepted(accepted)
                .build();
        var mutation = paymentTosMutationFactory.getPaymentTosSetMutation(action);
        webAppStateService.pushPatches(PaymentTosAction.COLLECTION_NAME, List.of(mutation));
    }
    // </editor-fold>
}
