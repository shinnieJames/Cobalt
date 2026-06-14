package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.dedup.MessageDedup;
import com.github.auties00.cobalt.ack.AckResult;
import com.github.auties00.cobalt.message.send.bot.BotProtobufTransform;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution;
import com.github.auties00.cobalt.message.send.stanza.*;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.media.DocumentMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.media.transcode.MediaTranscoderService;
import com.github.auties00.cobalt.privacy.LiveTrustedContactTokenService;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamMsgUtils;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.SendDocumentEventBuilder;
import com.github.auties00.cobalt.wam.event.WebcMessageSendEventBuilder;
import com.github.auties00.cobalt.wam.type.DocumentType;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Live implementation of {@link MessageSendingService} that orchestrates every outgoing-message
 * wire dispatch.
 *
 * <p>The service prepares a raw {@link MessageContainer} into a fully-populated
 * {@link MessageInfo} via {@link MessagePreparer}, dedupes concurrent sends keyed by message id
 * via {@link MessageDedup}, brackets the dispatch in a {@code WebcMessageSend} WAM event, then
 * routes by the parent JID's server kind to one of the per-chat-kind sub-senders:
 *
 * <ul>
 *   <li>1:1 PN or LID chat to {@link UserMessageSender}: per-device Signal
 *       fanout with optional phash-mismatch resend.</li>
 *   <li>group or community to {@link GroupMessageSender}: sender-key SKMSG,
 *       sender-key distribution on first contact, addressing-mode migration on
 *       stale-ack errors.</li>
 *   <li>status broadcast to {@link StatusMessageSender}: SKMSG against the
 *       audience derived from the user's status privacy preferences.</li>
 *   <li>business broadcast list to {@link BroadcastMessageSender}: SKMSG against
 *       the audience read from the local
 *       {@link com.github.auties00.cobalt.model.business.BusinessBroadcastList}
 *       roster.</li>
 *   <li>newsletter to {@link NewsletterMessageSender}: plaintext SMAX publish,
 *       no Signal envelope.</li>
 * </ul>
 *
 * <p>Peer protocol messages (app-state sync, fatal-exception notification,
 * peer-data ops) bypass the dispatch switch and go through
 * {@link #sendPeer(Jid, ChatMessageInfo)}, which delegates straight to
 * {@link PeerMessageSender}.
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgJob")
@WhatsAppWebModule(moduleName = "WAWebEncryptAndSendStatusMsg")
@WhatsAppWebModule(moduleName = "WAWebNewsletterSendMessageQueryJob")
@WhatsAppWebModule(moduleName = "WAWebSendAppStateSyncMsgJob")
@WhatsAppWebModule(moduleName = "WAWebSendMsgRecordAction")
public final class LiveMessageSendingService implements MessageSendingService {
    /**
     * Holds the fixed mapping from lower-case file extensions (without the
     * leading dot) to the corresponding WAM {@link DocumentType} bucket
     * consumed by {@link #logSendDocumentEvent(String, long)}.
     *
     * <p>It is populated verbatim from WA Web's
     * {@code WAWebProcessRawMediaLogging} extension-to-type table so the
     * emitted {@code documentType} and {@code documentExt} fields match the
     * upstream wire shape exactly. Extensions not in the table fall back to
     * {@link DocumentType#OTHER} with an empty {@code documentExt}.
     *
     * @implNote
     * The WA Web table classifies {@code wmv} as
     * {@link DocumentType#AUDIO} despite {@code wmv} being a video
     * container; the entry is kept verbatim because the WAM backend
     * expects this exact bucketing.
     */
    @WhatsAppWebExport(moduleName = "WAWebProcessRawMediaLogging", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final Map<String, DocumentType> DOCUMENT_EXT_TO_TYPE = Map.<String, DocumentType>ofEntries(
            Map.entry("ai", DocumentType.IMAGE),
            Map.entry("ico", DocumentType.IMAGE),
            Map.entry("jpeg", DocumentType.IMAGE),
            Map.entry("jpg", DocumentType.IMAGE),
            Map.entry("png", DocumentType.IMAGE),
            Map.entry("ps", DocumentType.IMAGE),
            Map.entry("psd", DocumentType.IMAGE),
            Map.entry("svg", DocumentType.IMAGE),
            Map.entry("tif", DocumentType.IMAGE),
            Map.entry("tiff", DocumentType.IMAGE),
            Map.entry("3g2", DocumentType.VIDEO),
            Map.entry("3gp", DocumentType.VIDEO),
            Map.entry("avi", DocumentType.VIDEO),
            Map.entry("flv", DocumentType.VIDEO),
            Map.entry("h264", DocumentType.VIDEO),
            Map.entry("m4v", DocumentType.VIDEO),
            Map.entry("mkv", DocumentType.VIDEO),
            Map.entry("mov", DocumentType.VIDEO),
            Map.entry("mp4", DocumentType.VIDEO),
            Map.entry("mpg", DocumentType.VIDEO),
            Map.entry("mpeg", DocumentType.VIDEO),
            Map.entry("rm", DocumentType.VIDEO),
            Map.entry("vob", DocumentType.VIDEO),
            Map.entry("wmv", DocumentType.AUDIO),
            Map.entry("aif", DocumentType.AUDIO),
            Map.entry("cda", DocumentType.AUDIO),
            Map.entry("mpa", DocumentType.AUDIO),
            Map.entry("opus", DocumentType.AUDIO),
            Map.entry("ogg", DocumentType.AUDIO),
            Map.entry("wlp", DocumentType.AUDIO),
            Map.entry("amr", DocumentType.AUDIO),
            Map.entry("mp3", DocumentType.AUDIO),
            Map.entry("m4a", DocumentType.AUDIO),
            Map.entry("aac", DocumentType.AUDIO),
            Map.entry("wav", DocumentType.AUDIO),
            Map.entry("wma", DocumentType.AUDIO),
            Map.entry("pdf", DocumentType.DOCUMENT),
            Map.entry("doc", DocumentType.DOCUMENT),
            Map.entry("docx", DocumentType.DOCUMENT),
            Map.entry("ppt", DocumentType.DOCUMENT),
            Map.entry("pptx", DocumentType.DOCUMENT),
            Map.entry("xls", DocumentType.DOCUMENT),
            Map.entry("xlsx", DocumentType.DOCUMENT),
            Map.entry("txt", DocumentType.DOCUMENT),
            Map.entry("rtf", DocumentType.DOCUMENT),
            Map.entry("tex", DocumentType.DOCUMENT),
            Map.entry("csv", DocumentType.DOCUMENT),
            Map.entry("wpd", DocumentType.DOCUMENT),
            Map.entry("7z", DocumentType.COMPRESSED_FILE),
            Map.entry("arj", DocumentType.COMPRESSED_FILE),
            Map.entry("deb", DocumentType.COMPRESSED_FILE),
            Map.entry("pkg", DocumentType.COMPRESSED_FILE),
            Map.entry("rar", DocumentType.COMPRESSED_FILE),
            Map.entry("rpm", DocumentType.COMPRESSED_FILE),
            Map.entry("gz", DocumentType.COMPRESSED_FILE),
            Map.entry("z", DocumentType.COMPRESSED_FILE),
            Map.entry("zip", DocumentType.COMPRESSED_FILE),
            Map.entry("apk", DocumentType.EXECUTABLE),
            Map.entry("bat", DocumentType.EXECUTABLE),
            Map.entry("bin", DocumentType.EXECUTABLE),
            Map.entry("cgi", DocumentType.EXECUTABLE),
            Map.entry("pl", DocumentType.EXECUTABLE),
            Map.entry("com", DocumentType.EXECUTABLE),
            Map.entry("exe", DocumentType.EXECUTABLE),
            Map.entry("gadget", DocumentType.EXECUTABLE),
            Map.entry("jar", DocumentType.EXECUTABLE),
            Map.entry("msi", DocumentType.EXECUTABLE),
            Map.entry("py", DocumentType.EXECUTABLE),
            Map.entry("wsf", DocumentType.EXECUTABLE)
    );

    /**
     * Turns a raw {@link MessageContainer} into a populated {@link MessageInfo}.
     */
    private final MessagePreparer preparer;

    /**
     * Rejects a second concurrent send carrying the same wire id.
     */
    private final MessageDedup messageDedup;

    /**
     * Handles 1:1 PN- or LID-addressed chats.
     */
    private final UserMessageSender userSender;

    /**
     * Handles group and community chats.
     */
    private final GroupMessageSender groupSender;

    /**
     * Handles status broadcasts.
     */
    private final StatusMessageSender statusSender;

    /**
     * Handles business broadcast-list sends addressed via
     * {@code <id>@broadcast}.
     */
    private final BroadcastMessageSender broadcastSender;

    /**
     * Handles plaintext SMAX newsletter publishes.
     */
    private final NewsletterMessageSender newsletterSender;

    /**
     * Handles app-state-sync and other own-device peer protocol stanzas.
     */
    private final PeerMessageSender peerSender;

    /**
     * Reaches shared services not already injected into the sub-senders.
     */
    private final LinkedWhatsAppClient client;

    /**
     * Records per-send {@code WebcMessageSend} events.
     */
    private final WamService wamService;

    /**
     * Attaches a rich preview to outgoing extended-text messages whose body
     * contains a URL.
     */
    private final MediaTranscoderService mediaTranscoderService;

    /**
     * Constructs a {@link LiveMessageSendingService} and wires up every sub-sender.
     *
     * <p>Embedders construct exactly one of these per client; the constructor
     * also instantiates the link-preview pipeline, the sender-key distribution
     * helper, and the stanza-decoration builders ({@link BotStanza},
     * {@link BizStanza}, {@link MetaStanza}, {@link ReportingStanza},
     * {@link CtwaAttributionStanza}, {@link TcTokenStanza}) that the sub-senders
     * share.
     *
     * @param client                 the {@link LinkedWhatsAppClient} used to dispatch
     *                                stanzas
     * @param encryption             the {@link MessageEncryption} service
     * @param deviceService          the {@link DeviceService} used for fanout
     *                               resolution
     * @param lidMigrationService    the {@link LidMigrationService} consulted by
     *                               the user-chat sender to gate the PN-to-LID
     *                               stanza rewrite
     * @param abPropsService         the {@link ABPropsService} consulted for
     *                               feature-gating decisions
     * @param wamService             the {@link WamService} that records per-send
     *                               events
     * @param mediaTranscoderService the {@link MediaTranscoderService} consulted
     *                               for link-preview decoration on outgoing text
     *                               messages
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public LiveMessageSendingService(
            LinkedWhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            LidMigrationService lidMigrationService,
            ABPropsService abPropsService,
            WamService wamService,
            MediaTranscoderService mediaTranscoderService
    ) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(encryption, "encryption");
        Objects.requireNonNull(deviceService, "deviceService");
        Objects.requireNonNull(lidMigrationService, "lidMigrationService");
        Objects.requireNonNull(abPropsService, "abPropsService");
        Objects.requireNonNull(wamService, "wamService");
        Objects.requireNonNull(mediaTranscoderService, "mediaTranscoderService");

        this.client = client;
        this.wamService = wamService;
        this.mediaTranscoderService = mediaTranscoderService;
        var store = client.store();
        this.preparer = new MessagePreparer(store);
        this.messageDedup = new MessageDedup();
        var skDistribution = new SenderKeyDistribution(encryption, deviceService, store);
        var botTransform = new BotProtobufTransform(store);
        var botStanza = new BotStanza(encryption, botTransform);
        var bizStanza = new BizStanza(store);
        var metaStanza = new MetaStanza(store);
        var reportingStanza = new ReportingStanza(abPropsService);
        var ctwaStanza = new CtwaAttributionStanza(store, abPropsService);
        var tcTokenStanza = new TcTokenStanza(store, abPropsService, new LiveTrustedContactTokenService(abPropsService));

        this.userSender = new UserMessageSender(client, encryption, deviceService, lidMigrationService, abPropsService,
                botStanza, bizStanza, metaStanza, reportingStanza, ctwaStanza, tcTokenStanza, wamService);
        this.groupSender = new GroupMessageSender(client, encryption, deviceService, abPropsService, skDistribution, botStanza, bizStanza, metaStanza, reportingStanza, wamService);
        this.statusSender = new StatusMessageSender(client, encryption, deviceService, abPropsService, skDistribution, metaStanza, reportingStanza, wamService);
        this.broadcastSender = new BroadcastMessageSender(client, encryption, deviceService, abPropsService, skDistribution, metaStanza, reportingStanza, wamService);
        this.newsletterSender = new NewsletterMessageSender(client, abPropsService, wamService);
        this.peerSender = new PeerMessageSender(client, encryption, deviceService, abPropsService, wamService);
    }

    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public AckResult send(Jid chatJid, MessageContainer container) {
        Objects.requireNonNull(chatJid, "chatJid");
        Objects.requireNonNull(container, "container");

        if (container.content() instanceof ExtendedTextMessage extended) {
            mediaTranscoderService.decorate(chatJid, extended);
        }

        MessageInfo prepared;
        if (chatJid.hasServer(JidServer.newsletter())) {
            prepared = preparer.prepareNewsletter(chatJid, container);
        } else {
            prepared = preparer.prepareChat(chatJid, container);
        }

        return send(prepared);
    }

    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public AckResult send(MessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo");

        var messageId = messageInfo
                .key()
                .id()
                .orElseThrow(() -> new IllegalArgumentException("messageId is required for outgoing messages"));
        var parentJid = messageInfo.key()
                .parentJid()
                .orElseThrow(() -> new IllegalArgumentException("parentJid is required for outgoing messages"));

        if (messageInfo.message() != null
                && messageInfo.message().content() instanceof DocumentMessage document) {
            logSendDocumentEvent(
                    document.fileName().orElse(null),
                    document.mediaSize().orElse(0L));
        }

        if (messageDedup.isPending(messageId)) {
            throw new WhatsAppMessageException.Send.Unknown(
                    "Duplicate send for message ID: " + messageId, null);
        }

        messageDedup.add(messageId);
        try {
            var sendEvent = buildWebcMessageSendEvent(messageInfo);
            var result = switch (messageInfo) {
                case ChatMessageInfo chatMessage when parentJid.hasUserServer() || parentJid.hasLidServer() ->
                    userSender.send(parentJid, chatMessage);
                case ChatMessageInfo chatMessage when parentJid.hasGroupOrCommunityServer() ->
                    groupSender.send(parentJid, chatMessage);
                case ChatMessageInfo chatMessage when parentJid.isStatusBroadcastAccount() ->
                    statusSender.send(parentJid, chatMessage);
                case ChatMessageInfo chatMessage when parentJid.hasBroadcastServer() ->
                    broadcastSender.send(parentJid, chatMessage);
                case NewsletterMessageInfo newsletterMessage when parentJid.hasNewsletterServer() ->
                    newsletterSender.send(parentJid, newsletterMessage);
                default -> throw new WhatsAppMessageException.Send.InvalidRecipient(
                    parentJid, "Unsupported combination: " + messageInfo.getClass().getSimpleName()
                            + " with JID type " + parentJid.server());
            };
            // Hand the local user's own trusted-contact token to a one-to-one peer after a non-protocol
            // message, matching WAWebSendMsgJob, so the peer keeps a current token across identity
            // rotations; the issue is gated to at most once per sender bucket. Fire-and-forget on a
            // virtual thread so the send path never blocks on the reciprocal token.
            if (messageInfo instanceof ChatMessageInfo
                    && (parentJid.hasUserServer() || parentJid.hasLidServer())
                    && !(messageInfo.message() != null
                            && messageInfo.message().content() instanceof ProtocolMessage)) {
                Thread.ofVirtual().name("tc-token-" + parentJid).start(() -> client.issueTrustedContactToken(parentJid));
            }
            if (sendEvent != null) {
                wamService.commit(sendEvent.stopMessageSendT().build());
            }
            return result;
        } finally {
            messageDedup.remove(messageId);
        }
    }

    /**
     * Commits the {@code SendDocumentEvent} for an outgoing document
     * send.
     *
     * <p>The document-send pipeline calls this before the upload begins; it
     * mirrors WA Web's
     * {@code WAWebProcessRawMediaLogging.logSendDocumentEvent}. The
     * filename is split on {@code .} and the last segment, lowercased, is
     * looked up in {@link #DOCUMENT_EXT_TO_TYPE} to resolve the
     * {@link DocumentType}; an absent or unknown extension yields an empty
     * {@code documentExt} property and falls back to
     * {@link DocumentType#OTHER}.
     *
     * @implNote
     * This implementation leaves the {@code documentPageSize} WAM
     * property unset because WA Web's emission site declares it on the
     * event spec but never populates it; the field is reserved for
     * future use.
     *
     * @param filename the user-visible document filename;
     *                 {@code null} resolves the extension as the empty
     *                 string (mirroring WA Web's
     *                 {@code e?.split(".").pop() ?? ""} fallback)
     * @param size     the raw decrypted document size in bytes
     */
    @WhatsAppWebExport(moduleName = "WAWebProcessRawMediaLogging", exports = "logSendDocumentEvent",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void logSendDocumentEvent(String filename, long size) {
        String extension;
        if (filename == null || filename.isEmpty()) {
            extension = "";
        } else {
            var dotIndex = filename.lastIndexOf('.');
            var tail = dotIndex < 0 ? filename : filename.substring(dotIndex + 1);
            extension = tail.toLowerCase(Locale.ROOT);
        }
        var normalizedExt = DOCUMENT_EXT_TO_TYPE.containsKey(extension) ? extension : "";
        var documentType = DOCUMENT_EXT_TO_TYPE.getOrDefault(normalizedExt, DocumentType.OTHER);
        wamService.commit(new SendDocumentEventBuilder()
                .documentSize((double) size)
                .documentType(documentType)
                .documentExt(normalizedExt)
                .build());
    }

    /**
     * Builds the pre-configured {@link WebcMessageSendEventBuilder} for the
     * given outgoing message, or returns {@code null} when the event must be
     * suppressed.
     *
     * <p>Suppressed for newsletter sends (a different WA Web logger covers
     * those) and for protocol revoke messages. For every other chat message the
     * builder is pre-populated with the resolved {@code messageType},
     * {@code messageMediaType}, the {@code messageIsForward} flag taken from the
     * optional {@link ContextualMessage#contextInfo()}, and the latency timer is
     * started so it can be stopped before the success-branch commit on the
     * caller side.
     *
     * @param messageInfo the message about to be sent
     * @return the prepared builder, or {@code null} when no event should be
     *         emitted
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgRecordAction", exports = "sendMsgRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private WebcMessageSendEventBuilder buildWebcMessageSendEvent(MessageInfo messageInfo) {
        if (!(messageInfo instanceof ChatMessageInfo chatMessage)) {
            return null;
        }
        var content = chatMessage.message() == null ? null : chatMessage.message().content();
        if (content instanceof ProtocolMessage protocol
                && protocol.type().orElse(null) == ProtocolMessage.Type.REVOKE) {
            return null;
        }
        var isForwarded = content instanceof ContextualMessage contextual
                && contextual.contextInfo().map(ctx -> ctx.isForwarded()).orElse(false);
        return new WebcMessageSendEventBuilder()
                .messageType(WamMsgUtils.getWamMessageType(chatMessage))
                .messageMediaType(WamMsgUtils.getWamMediaType(chatMessage))
                .messageIsForward(isForwarded)
                .startMessageSendT();
    }

    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSendKeyDistributionMsgAction", exports = "sendKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public void sendKeyDistribution(Jid groupJid, MessageKey key) {
        Objects.requireNonNull(groupJid, "groupJid");
        Objects.requireNonNull(key, "key");

        var messageId = key.id()
                .orElseThrow(() -> new IllegalArgumentException(
                        "messageId is required for key distribution"));

        if (!groupJid.hasGroupOrCommunityServer()) {
            throw new WhatsAppMessageException.Send.InvalidRecipient(
                    groupJid, "Key distribution is only supported for group chats");
        }

        groupSender.sendKeyDistribution(groupJid, messageId);
    }

    @WhatsAppWebExport(moduleName = "WAWebSendAppStateSyncMsgJob", exports = "encryptAndSendKeyMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    public AckResult sendPeer(Jid targetDevice, ChatMessageInfo messageInfo) {
        Objects.requireNonNull(targetDevice, "targetDevice");
        Objects.requireNonNull(messageInfo, "messageInfo");
        return peerSender.send(targetDevice, messageInfo);
    }
}
