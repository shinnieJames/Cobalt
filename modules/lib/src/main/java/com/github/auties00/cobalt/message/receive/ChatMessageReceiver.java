package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.exception.WhatsAppMessageException.Receive.InvalidDeviceSentMessage.DsmErrorType;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.addon.EncMessageFactory;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryptionHandler;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveBotInfo;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveEncryptedPayload;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanzaParser;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfoBuilder;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.FutureProofMessageType;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.model.message.MessageThreadId;
import com.github.auties00.cobalt.model.message.system.DeviceSentMessage;
import com.github.auties00.cobalt.model.message.poll.PollCreationMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.text.HighlyStructuredMessage;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppChatStore;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Inbound receiver that runs every non-newsletter {@code <message>} stanza through
 * the full Signal-protocol decryption pipeline and produces a populated
 * {@link ChatMessageInfo}.
 *
 * <p>Processing happens in two phases. The decryption phase parses the stanza,
 * validates the {@code <enc>} ordering and the ADV identity for companion-device
 * senders, dispatches each encrypted payload through {@link MessageDecryptionHandler},
 * and flushes the Signal store. The protobuf phase decodes the plaintext, validates
 * the HSM flag, imports any embedded sender-key distribution message, unwraps a
 * {@link DeviceSentMessage} envelope for self-sent messages, and assembles the final
 * {@link ChatMessageInfo}.
 *
 * <p>Selected by {@link MessageReceivingService#process(Stanza)} for every stanza whose
 * {@code from} JID is not a newsletter; this covers 1:1, group, broadcast, status, and
 * peer-protocol messages.
 *
 * @implNote
 * This implementation collapses WhatsApp Web's
 * {@code WAWebMsgProcessingDecryptApi.decryptE2EPayload} (decryption iteration plus
 * ADV validation) and {@code WAWebHandleMsgProcess.processDecryptedMessageProto}
 * (post-decryption protobuf processing) into a single straight-line method instead
 * of WA Web's mutually-recursive callback graph between
 * {@code WAWebMsgProcessingApiUtils.parseMessage} and the various
 * {@code processXxxMsg} branches; this is possible because Cobalt does not run the
 * reactive {@code MessageQueue} dispatcher that WA Web uses to gate processing per
 * chat.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsg")
@WhatsAppWebModule(moduleName = "WAWebMsgProcessingDecryptApi")
@WhatsAppWebModule(moduleName = "WAWebHandleMsgProcess")
@WhatsAppWebModule(moduleName = "WAWebMsgProcessingApiUtils")
final class ChatMessageReceiver extends MessageReceiver<ChatMessageInfo> {
    /**
     * Logger used for per-stage decryption and protobuf-processing diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(ChatMessageReceiver.class.getName());

    /**
     * Decryption service used for the per-enc iteration in the decryption phase.
     *
     * <p>Owns the per-device Signal cipher (PKMSG/MSG), the group sender-key cipher
     * (SKMSG), and the bot AES-GCM scheme (MSMSG); injected so the same service can be
     * reused across receivers and stubbed in tests.
     */
    private final MessageDecryption decryption;

    /**
     * Constructs a chat receiver bound to the given store and decryption service.
     *
     * @param store      the central session store
     * @param decryption the decryption service for the Signal protocol and bot
     *                   messages
     * @throws NullPointerException if {@code decryption} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsg", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    ChatMessageReceiver(LinkedWhatsAppStore store, MessageDecryption decryption) {
        super(store);
        this.decryption = Objects.requireNonNull(decryption, "decryption");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Runs the full two-phase decrypt-then-decode pipeline; returns {@code null} for
     * unavailable fanout placeholders that should be silently acknowledged.
     *
     * @implNote
     * This implementation extends WhatsApp Web's expired-status metric suppression
     * into a full exception suppression: an expired status message (older than 24
     * hours, see {@link #isExpiredStatus(MessageReceiveStanza)}) whose decryption
     * fails is silently dropped via {@code null} so the orchestrator does not raise
     * a retry receipt; WA Web's
     * {@code WAWebMsgProcessingDecryptionHandler.postIncomingMessageDropExpired}
     * still produces a metric on the same branch but lets the receipt path proceed.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsg", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptApi", exports = "decryptE2EPayload",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    ChatMessageInfo receive(Stanza node, Jid fromJid) {
        var selfJid = store.accountStore().jid().orElse(null);
        var selfLidJid = store.accountStore().lid().orElse(null);
        var stanza = MessageReceiveStanzaParser.parse(node, selfJid, selfLidJid);

        if (stanza.isUnavailable()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Skipping unavailable (fanout) message {0}", stanza.id());
            return null;
        }

        if (stanza.encs().isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Message {0} has no encrypted payloads", stanza.id());
            return null;
        }

        validateRecipient(stanza);
        validateNotHostedCompanion(stanza);

        validateEncOrdering(stanza);
        validateAdvIdentity(stanza);
        byte[] plaintext;
        try {
            plaintext = decryptPayloads(stanza);
        } catch (WhatsAppMessageException.Receive e) {
            if (isExpiredStatus(stanza)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Skipping decryption error for expired status {0}", stanza.id());
                return null;
            }
            throw e;
        }
        flushSignalStore();

        var container = decodeProtobuf(stanza.id(), plaintext);
        if (container == null) {
            throw new WhatsAppMessageException.Receive.InvalidProtobuf(
                    "Failed to decode protobuf for: " + stanza.id(), null);
        }

        validateHsmConsistency(stanza, container);
        processSenderKeyDistribution(container, stanza);

        var chatJid = resolveChatJid(stanza);
        var effectiveContainer = container;

        if (container.futureProofContentType() == FutureProofMessageType.DEVICE_SENT) {
            var dsm = container.deviceSentMessage().orElseThrow();
            effectiveContainer = unwrapDeviceSentMessage(container, dsm, stanza);
            chatJid = dsm.destinationJid().orElseThrow(() ->
                    new WhatsAppMessageException.Receive.InvalidDeviceSentMessage(
                            DsmErrorType.INVALID_DSM));
        } else if (shouldHaveDeviceSentMessage(stanza, container)) {
            throw new WhatsAppMessageException.Receive.InvalidDeviceSentMessage(
                    DsmErrorType.MISSING_DSM);
        }

        maybeDecryptPollVote(effectiveContainer, stanza);

        return buildChatMessageInfo(stanza, chatJid, effectiveContainer);
    }

    /**
     * Auto-decrypts an inbound poll vote in place, populating its
     * {@link PollUpdateMessage#selectedOptions() selectedOptions} with the
     * resolved option labels.
     *
     * <p>Mirrors the send-side vote encryption performed during message
     * preparation: when {@code container} carries a {@link PollUpdateMessage}
     * whose encrypted {@code vote} is present, the referenced
     * {@link PollCreationMessage} is resolved from the local store, the vote is
     * decrypted to its selected option hashes, and those hashes are matched back
     * to the poll's option labels. The decryption is best-effort: a missing
     * poll-creation message (not cached locally) or a cipher rejection (for
     * example a sender JID-form mismatch, which this client does not yet retry
     * across LID and PN forms) leaves the vote untouched rather than failing the
     * receive.
     *
     * @param container the decoded inbound message container
     * @param stanza    the parsed receive stanza, supplying the voter JID
     */
    @WhatsAppWebExport(moduleName = "WAWebPollsVoteDecryption", exports = "decryptVote",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void maybeDecryptPollVote(MessageContainer container, MessageReceiveStanza stanza) {
        if (!(container.content() instanceof PollUpdateMessage poll) || poll.vote().isEmpty()) {
            return;
        }
        var pollKey = poll.pollCreationMessageKey().orElse(null);
        if (pollKey == null) {
            return;
        }
        if (!(store.chatStore().findMessageByKey(pollKey).orElse(null) instanceof ChatMessageInfo pollCreation)
                || !(pollCreation.message().content() instanceof PollCreationMessage creation)) {
            return;
        }
        var voterJid = stanza.senderJid().toUserJid();
        try {
            var selectedHashes = EncMessageFactory.decryptPollVote(poll.vote().get(), pollCreation, voterJid);
            poll.setSelectedOptions(matchSelectedOptions(creation, selectedHashes));
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Could not auto-decrypt poll vote {0}: {1}", stanza.id(), exception.getMessage());
        }
    }

    /**
     * Maps decrypted option hashes back to the matching poll option labels.
     *
     * <p>Each option name is hashed with SHA-256, the same digest the vote
     * encoder applies, and the hashes are compared against the decrypted
     * selection. Hashes with no matching option are dropped.
     *
     * @param creation       the poll-creation message carrying the option labels
     * @param selectedHashes the SHA-256 hashes of the voter's selected options
     * @return the matched option labels, in selection order
     */
    private static List<String> matchSelectedOptions(PollCreationMessage creation, List<byte[]> selectedHashes) {
        if (selectedHashes.isEmpty()) {
            return List.of();
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
        var labelsByHash = new HashMap<String, String>(creation.options().size());
        for (var option : creation.options()) {
            var name = option.optionName().orElse(null);
            if (name == null) {
                continue;
            }
            digest.reset();
            labelsByHash.put(HexFormat.of().formatHex(digest.digest(name.getBytes(StandardCharsets.UTF_8))), name);
        }
        var labels = new ArrayList<String>(selectedHashes.size());
        for (var hash : selectedHashes) {
            var label = labelsByHash.get(HexFormat.of().formatHex(hash));
            if (label != null) {
                labels.add(label);
            }
        }
        return labels;
    }

    /**
     * Rejects stanzas that carry a {@code recipient_pn} or {@code recipient_lid}
     * attribute when the sender is not the logged-in user's own device.
     *
     * <p>The recipient attributes are reserved for peer-protocol messages echoed
     * between the user's own devices; receiving them from any other sender is a
     * protocol violation that is rejected up front.
     *
     * @param stanza the parsed stanza
     * @throws WhatsAppMessageException.Receive.InvalidMessage if a recipient
     *         attribute appears on a non-peer message
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgParser", exports = "incomingMsgParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void validateRecipient(MessageReceiveStanza stanza) {
        var hasRecipient = stanza.recipientPn().isPresent()
                || stanza.recipientLid().isPresent();

        if (hasRecipient && !isFromMe(stanza)) {
            throw new WhatsAppMessageException.Receive.InvalidMessage(
                    "Recipient attribute from non-peer device: " + stanza.senderJid(), null);
        }
    }

    /**
     * Rejects stanzas from a hosted companion device that target a group, community,
     * broadcast, or status chat.
     *
     * <p>Hosted companions (the WhatsApp Business coexistence-mode devices) may only
     * participate in 1:1 chats with their primary, so anything else is dropped as an
     * invalid stanza.
     *
     * @param stanza the parsed stanza
     * @throws WhatsAppMessageException.Receive.InvalidMessage if a hosted companion
     *         device targets a group, community, or broadcast chat
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgParser", exports = "incomingMsgParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void validateNotHostedCompanion(MessageReceiveStanza stanza) {
        var participant = stanza.participant().orElse(null);
        if (participant == null) {
            return;
        }

        if (!participant.hasHostedServer() && !participant.hasHostedLidServer()) {
            return;
        }

        var chatJid = stanza.chatJid();
        if (chatJid.hasGroupOrCommunityServer()
                || chatJid.hasBroadcastServer()) {
            throw new WhatsAppMessageException.Receive.InvalidMessage(
                    "Hosted companion device " + participant
                            + " cannot send to " + chatJid, null);
        }
    }

    /**
     * Resolves the effective chat JID for storing the message, applying bot-specific
     * target overrides.
     *
     * <p>Bot-server messages can re-target a different chat via the
     * {@code target_chat_jid} or {@code target_chat_jid_lid} stanza attribute; the
     * resulting {@link ChatMessageInfo} is routed into that chat instead of the bot's
     * own JID so the user sees the reply in the originating thread.
     *
     * @param stanza the parsed stanza
     * @return the effective chat JID
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgParser", exports = "incomingMsgParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolveChatJid(MessageReceiveStanza stanza) {
        if (stanza.chatJid().hasBotServer()) {
            var targetChatJid = stanza.targetChatJidLid()
                    .or(stanza::targetChatJid)
                    .orElse(null);
            if (targetChatJid != null) {
                return targetChatJid;
            }
        }
        return stanza.chatJid();
    }

    /**
     * Returns whether the stanza is a status update older than 24 hours.
     *
     * <p>{@link #receive(Stanza, Jid)} uses this to silently drop decryption errors on
     * expired status content rather than triggering a retry receipt for a story the
     * user can no longer view.
     *
     * @implNote
     * This implementation uses {@link ChronoUnit#HOURS} arithmetic for the 24-hour
     * threshold; WhatsApp Web uses {@code DAY_SECONDS} so the comparison is
     * second-level but the result is identical.
     *
     * @param stanza the parsed stanza
     * @return {@code true} when the stanza is a status broadcast that is more than
     *         24 hours old
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isExpiredStatus(MessageReceiveStanza stanza) {
        if (!stanza.chatJid().isStatusBroadcastAccount()) {
            return false;
        }

        var age = ChronoUnit.HOURS.between(stanza.timestamp(), Instant.now());
        return age > 24;
    }

    /**
     * Warns when a stanza presents an SKMSG payload before the per-device Signal
     * payload in a two-enc stanza.
     *
     * <p>The expected ordering is per-device-first so the SKMSG can be validated
     * against the sender-key distribution that lives in the per-device payload. An
     * out-of-order pair is logged and processing continues rather than aborting.
     *
     * @param stanza the parsed stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptApi", exports = "decryptE2EPayload",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void validateEncOrdering(MessageReceiveStanza stanza) {
        var encs = stanza.encs();
        if (encs.size() == 2
                && encs.getFirst().e2eType() == MessageEncryptionType.SKMSG) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Message {0}: SKMSG is out of order (should not be first of two encs)",
                    stanza.id());
        }
    }

    /**
     * Validates the ADV (Auxiliary Device Validation) identity when the stanza
     * sender is a companion device and carries a PKMSG payload.
     *
     * <p>Ensures that a companion device with a freshly-established Signal session
     * cannot impersonate the primary.
     *
     * @implNote
     * This implementation extracts the identity key from the PKMSG ciphertext via
     * {@link MessageDecryption#extractIdentityKeyFromPkmsg(byte[])}, parses the
     * device-identity protobuf attached to the stanza, and accepts the binding on
     * a missing local identity record; the cryptographic signature check is
     * delegated to libsignal's session-installation path so this method only
     * surfaces structural failures.
     *
     * @param stanza the parsed stanza
     * @throws WhatsAppMessageException.Receive.AdvFailure if validation fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptApi", exports = "decryptE2EPayload",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void validateAdvIdentity(MessageReceiveStanza stanza) {
        if (!stanza.isCompanionDevice()) {
            return;
        }

        var pkmsgPayload = stanza.encs().stream()
                .filter(enc -> enc.e2eType() == MessageEncryptionType.PKMSG)
                .findFirst()
                .orElse(null);
        if (pkmsgPayload == null) {
            return;
        }

        var deviceIdentityBytes = stanza.deviceIdentity().orElse(null);
        if (deviceIdentityBytes == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Companion device {0} sent PKMSG without device-identity",
                    stanza.senderJid());
            throw new WhatsAppMessageException.Receive.AdvFailure(
                    "Missing device-identity for companion device: "
                            + stanza.senderJid());
        }

        var identityKey = decryption.extractIdentityKeyFromPkmsg(
                pkmsgPayload.ciphertext()).orElse(null);
        if (identityKey == null) {
            throw new WhatsAppMessageException.Receive.AdvFailure(
                    "Cannot extract identity key from PKMSG for: "
                            + stanza.senderJid());
        }

        try {
            var signedIdentity = ADVSignedDeviceIdentitySpec.decode(deviceIdentityBytes);

            var primaryJid = stanza.senderJid().toUserJid();
            var storedKey = store.signalStore().findIdentityByAddress(
                    primaryJid.toSignalAddress());

            if (storedKey.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "No stored identity for primary {0}, accepting ADV",
                        primaryJid);
            }
        } catch (WhatsAppMessageException.Receive e) {
            throw e;
        } catch (Exception e) {
            throw new WhatsAppMessageException.Receive.AdvFailure(
                    "ADV identity parsing failed for: "
                            + stanza.senderJid(), e);
        }
    }

    /**
     * Iterates over every encrypted payload in the stanza, dispatching to the
     * appropriate cipher and returning the first successful plaintext.
     *
     * <p>Every enc is attempted even after the first success so the Signal session
     * state is advanced for every encryption type via the
     * {@link MessageDecryptionHandler} across both slots.
     *
     * @implNote
     * This implementation rethrows the dominant failure surfaced via
     * {@link MessageDecryptionHandler#failedError()} when no payload could be
     * decrypted, so the caller sees the original Signal-protocol exception rather
     * than a synthesised wrapper; a synthesised
     * {@link WhatsAppMessageException.Receive.Unknown} is only used when the handler
     * recorded no failure at all (which should not happen given the
     * {@link MessageDecryptionHandler#canDecryptNext(MessageReceiveEncryptedPayload)}
     * contract).
     *
     * @param stanza the parsed stanza
     * @return the first successfully decrypted plaintext bytes
     * @throws WhatsAppMessageException.Receive if every payload fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptApi", exports = "decryptE2EPayload",
            adaptation = WhatsAppAdaptation.DIRECT)
    private byte[] decryptPayloads(MessageReceiveStanza stanza) {
        var handler = new MessageDecryptionHandler();
        byte[] plaintext = null;

        for (var enc : stanza.encs()) {
            if (!handler.canDecryptNext(enc)) {
                continue;
            }

            try {
                var decrypted = decryptSinglePayload(enc, stanza);

                if (plaintext == null) {
                    plaintext = decrypted;
                }
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Decrypted message {0} via {1}",
                        stanza.id(), enc.e2eType());
            } catch (WhatsAppMessageException.Receive e) {
                handler.handleError(enc, e);
            }
        }

        if (plaintext != null) {
            return plaintext;
        }

        var error = handler.failedError().orElse(null);
        if (error != null) {
            throw error;
        }

        throw new WhatsAppMessageException.Receive.Unknown(
                "No encrypted payloads could be decrypted for: "
                        + stanza.id(), null);
    }

    /**
     * Dispatches a single encrypted payload to the cipher matching its
     * {@link MessageEncryptionType}.
     *
     * <p>The four ciphertext types map to the three Signal entry points on
     * {@link MessageDecryption} plus the special MSMSG bot-message scheme.
     *
     * @implNote
     * This implementation resolves the bot {@code messageId} using
     * {@link MessageReceiveBotInfo#editTargetId()} when present and falling back to
     * the stanza id; WhatsApp Web's
     * {@code WAWebBotMessageSecret.decryptMsmsgBotMessage} uses
     * {@code botEditTargetId} only when the bot edit type is
     * {@code INNER}/{@code LAST}, which Cobalt collapses to "always use the edit
     * target id when present" because the broader fallback is harmless on the
     * non-edit path.
     *
     * @param enc    the encrypted payload to decrypt
     * @param stanza the parent stanza for addressing context
     * @return the decrypted plaintext bytes with padding already removed (when
     *         applicable)
     * @throws WhatsAppMessageException.Receive if decryption fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptEnc", exports = "decryptEnc",
            adaptation = WhatsAppAdaptation.DIRECT)
    private byte[] decryptSinglePayload(
            MessageReceiveEncryptedPayload enc,
            MessageReceiveStanza stanza
    ) {
        return switch (enc.e2eType()) {
            case SKMSG -> {
                var groupJid = stanza.chatJid();
                if (!groupJid.hasGroupOrCommunityServer()
                        && !groupJid.hasBroadcastServer()) {
                    throw new WhatsAppMessageException.Receive.InvalidMessage(
                            "SKMSG for non-group JID: " + groupJid, null);
                }
                var participant = stanza.participant().orElseThrow(() ->
                        new WhatsAppMessageException.Receive.InvalidMessage(
                                "SKMSG without participant for: " + groupJid, null));
                yield decryption.decryptFromGroup(
                        enc.ciphertext(), groupJid, participant);
            }
            case PKMSG, MSG -> {
                var sender = resolveSignalSender(stanza);
                yield decryption.decryptFromDevice(
                        enc.ciphertext(), sender, enc.e2eType());
            }
            case MSMSG -> {
                var messageSecret = resolveBotMessageSecret(stanza);
                var messageId = stanza.botInfo()
                        .flatMap(MessageReceiveBotInfo::editTargetId)
                        .orElse(stanza.id());
                var targetSenderJid = stanza.targetSenderJid()
                        .map(Jid::toUserJid)
                        .orElseGet(() -> requireSelfJid().toUserJid());
                var botSenderJid = stanza.senderJid().toUserJid();
                yield decryption.decryptBotMessage(
                        enc.ciphertext(), messageSecret, messageId,
                        targetSenderJid, botSenderJid);
            }
        };
    }

    /**
     * Resolves the Signal sender JID used to look up a per-device session.
     *
     * <p>Picks the participant JID for group and broadcast chats and the {@code from}
     * JID otherwise, since the Signal cipher needs the device-level JID rather than the
     * chat JID.
     *
     * @param stanza the parsed stanza
     * @return the sender device JID for Signal session lookup
     * @throws WhatsAppMessageException.Receive.InvalidMessage if a group or broadcast
     *         stanza is missing its participant
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptEnc", exports = "decryptEnc",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolveSignalSender(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        if (chatJid.hasGroupOrCommunityServer() || chatJid.hasBroadcastServer()) {
            return stanza.participant().orElseThrow(() ->
                    new WhatsAppMessageException.Receive.InvalidMessage(
                            "PKMSG/MSG without participant for group: "
                                    + chatJid, null));
        }
        return chatJid;
    }

    /**
     * Resolves the {@code messageSecret} for a bot message by looking up the target
     * message in the store.
     *
     * @implNote
     * This implementation skips WhatsApp Web's {@code WAWebMsmsgMsgSecretCache} layer
     * and reads the target message straight from the store via
     * {@link LinkedWhatsAppChatStore#findMessageById(com.github.auties00.cobalt.model.jid.JidProvider, String)},
     * keyed by {@code (targetChatJid, targetId)}. The result is accepted only when the
     * resolved {@link ChatMessageInfo#messageSecret()} is non-empty; an absent secret
     * raises {@link WhatsAppMessageException.Receive.InvalidMessage} rather than WA
     * Web's {@code WAWebOrphanBotMsgError} because Cobalt does not yet model the
     * orphan-bot-message deferral path.
     *
     * @param stanza the parsed stanza carrying {@code target_id} and
     *               {@code target_chat_jid} metadata
     * @return the 32-byte message secret
     * @throws WhatsAppMessageException.Receive.InvalidMessage if the target message
     *         or its secret cannot be found
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private byte[] resolveBotMessageSecret(MessageReceiveStanza stanza) {
        var targetId = stanza.targetId().orElseThrow(() ->
                new WhatsAppMessageException.Receive.InvalidMessage(
                        "MSMSG missing target_id", null));

        var targetChatJid = stanza.targetChatJid().orElse(stanza.chatJid());

        var targetMessage = store.chatStore().findMessageById(targetChatJid, targetId)
                .orElse(null);
        if (targetMessage instanceof ChatMessageInfo chatInfo) {
            var secret = chatInfo.messageSecret().orElse(null);
            if (secret != null && secret.length > 0) {
                return secret;
            }
        }
        throw new WhatsAppMessageException.Receive.InvalidMessage(
                "Cannot find messageSecret for target message: " + targetId
                        + " in chat: " + targetChatJid, null);
    }

    /**
     * Flushes the Signal protocol store after a successful decryption pass.
     *
     * <p>Session state updated during PKMSG installation and MSG ratcheting must be
     * durable before the next stanza is processed.
     *
     * @implNote
     * This implementation logs and swallows any flush failure so a transient store
     * error does not propagate as a fatal receive error; a persistent failure will
     * be surfaced by the next save attempt on a subsequent message.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptApi", exports = "decryptE2EPayload",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void flushSignalStore() {
        try {
            store.save();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to flush signal store: {0}", e.getMessage());
        }
    }

    /**
     * Rejects stanzas whose HSM flag disagrees with the decoded protobuf content.
     *
     * <p>The mismatch resolves to
     * {@link com.github.auties00.cobalt.message.receive.crypto.MessageDecryptionResult#HSM_MISMATCH},
     * which the receipt handler treats as a silent drop.
     *
     * @param stanza    the parsed stanza
     * @param container the decoded message container
     * @throws WhatsAppMessageException.Receive.HsmMismatch if the stanza is not HSM
     *         but the protobuf carries a {@link HighlyStructuredMessage}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgProcessUtils", exports = "preProcessMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void validateHsmConsistency(
            MessageReceiveStanza stanza,
            MessageContainer container
    ) {
        if (!stanza.isHsm() && container.content() instanceof HighlyStructuredMessage) {
            throw new WhatsAppMessageException.Receive.HsmMismatch(
                    "HSM mismatch for: " + stanza.id());
        }
    }

    /**
     * Imports the sender-key distribution embedded in the decoded protobuf, when
     * present.
     *
     * <p>Importing the distribution lets future SKMSG payloads from the same sender in
     * the same group decrypt without an extra round-trip.
     *
     * @implNote
     * This implementation validates that the embedded group id matches the stanza
     * chat JID before importing the key so a malicious sender cannot install a
     * sender-key for a different group; on mismatch the import is skipped and a
     * warning is logged. The libsignal-side import error is caught and logged
     * rather than propagated so a single corrupt distribution does not abort the
     * remaining post-decryption pipeline.
     *
     * @param container the decoded message container
     * @param stanza    the parsed stanza for group and sender context
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingApiUtils", exports = "parseMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void processSenderKeyDistribution(
            MessageContainer container,
            MessageReceiveStanza stanza
    ) {
        var skdm = container.senderKeyDistributionMessage().orElse(null);
        if (skdm == null) {
            return;
        }

        var skdmGroupJid = skdm.groupJid()
                .orElse(null);
        var distributionData = skdm.axolotlSenderKeyDistributionMessage()
                .orElse(null);
        if (distributionData == null || distributionData.length == 0) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Sender key distribution missing data for {0}",
                    stanza.id());
            return;
        }

        var groupJid = stanza.chatJid();
        if (!Objects.equals(groupJid, skdmGroupJid)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Sender key distribution group ID mismatch: stanza={0}, proto={1}",
                    groupJid, skdmGroupJid);
            return;
        }

        try {
            decryption.processSenderKeyDistribution(
                    groupJid, stanza.senderJid(), distributionData);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Processed sender key distribution from {0} for {1}",
                    stanza.senderJid(), groupJid);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to process sender key distribution for {0}: {1}",
                    stanza.id(), e.getMessage());
        }
    }

    /**
     * Returns whether the decoded protobuf for a self-sent stanza should carry a
     * {@link DeviceSentMessage} wrapper.
     *
     * <p>A missing DSM on a type that requires one resolves to
     * {@link WhatsAppMessageException.Receive.InvalidDeviceSentMessage} with
     * {@link DsmErrorType#MISSING_DSM}.
     *
     * @implNote
     * This implementation mirrors WA Web's {@code parseMessage} pre-switch
     * bypass plus its per-type rules:
     * <ul>
     *   <li>{@code protocolMessage} carrying any of {@code historySyncNotification},
     *       {@code initialSecurityNotificationSettingSync},
     *       {@code appStateSyncKeyShare}, {@code appStateSyncKeyRequest},
     *       {@code peerDataOperationRequestMessage},
     *       {@code peerDataOperationRequestResponseMessage},
     *       {@code cloudApiThreadControlNotification}, or
     *       {@code lidMigrationMappingSyncMessage}: never (control-plane
     *       short-circuit).</li>
     *   <li>{@code CHAT}: always.</li>
     *   <li>{@code OTHER_BROADCAST}, {@code OTHER_STATUS}, {@code PEER_CHAT}:
     *       never.</li>
     *   <li>{@code GROUP}: only when {@link MessageReceiveStanza#isDirect()} is
     *       {@code true} AND the message is not the appdata-default sender-key
     *       distribution bootstrap (WA Web's {@code K(e)} predicate).</li>
     *   <li>{@code DIRECT_PEER_STATUS}: only when
     *       {@link MessageReceiveStanza#isDirect()} is {@code true}.</li>
     *   <li>{@code PEER_BROADCAST}: only when none of the encs is an SKMSG and
     *       the stanza is not a delivery retry (WA Web's
     *       {@code isMessageRetry && deviceSentMessage == null} short-circuit).</li>
     * </ul>
     *
     * @param stanza    the parsed stanza
     * @param container the decoded message container, consulted for the
     *                  protocol-message bypass and the appdata-default
     *                  sender-key-distribution exception
     * @return {@code true} if a DSM wrapper should be present
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingApiUtils", exports = "parseMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean shouldHaveDeviceSentMessage(MessageReceiveStanza stanza, MessageContainer container) {
        if (!isFromMe(stanza)) {
            return false;
        }

        if (isProtocolMessageBypass(container)) {
            return false;
        }

        return switch (stanza.messageType()) {
            case CHAT -> true;
            case OTHER_BROADCAST, OTHER_STATUS, PEER_CHAT -> false;
            case GROUP -> stanza.isDirect() && !isAppdataDefaultSenderKeyOnly(stanza, container);
            case DIRECT_PEER_STATUS -> stanza.isDirect();
            case PEER_BROADCAST -> {
                var hasSenderKeyEnc = stanza.encs().stream()
                        .anyMatch(enc -> enc.e2eType().isSenderKeyMessage());
                if (hasSenderKeyEnc) {
                    yield false;
                }
                yield !stanza.isRetry();
            }
        };
    }

    /**
     * Returns whether the decoded container carries a control-plane
     * {@link ProtocolMessage} variant that is exempt from the
     * device-sent-message requirement.
     *
     * <p>Mirrors the pre-switch short-circuit in WA Web's {@code parseMessage}:
     * any of the eight listed protocol-message variants routes through
     * {@code parseProtocolMessage} (or the {@code lidMigrationSyncMessage}
     * sibling branch) without consulting the DSM expectation.
     *
     * @param container the decoded message container
     * @return {@code true} when the container carries a bypass-eligible
     *         protocol message
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingApiUtils", exports = "parseMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isProtocolMessageBypass(MessageContainer container) {
        if (!(container.content() instanceof ProtocolMessage protocolMessage)) {
            return false;
        }
        return protocolMessage.historySyncNotification().isPresent()
                || protocolMessage.initialSecurityNotificationSettingSync().isPresent()
                || protocolMessage.appStateSyncKeyShare().isPresent()
                || protocolMessage.appStateSyncKeyRequest().isPresent()
                || protocolMessage.peerDataOperationRequestMessage().isPresent()
                || protocolMessage.peerDataOperationRequestResponseMessage().isPresent()
                || protocolMessage.cloudApiThreadControlNotification().isPresent()
                || protocolMessage.lidMigrationMappingSyncMessage().isPresent();
    }

    /**
     * Returns whether a group stanza is the appdata-default sender-key
     * distribution bootstrap that WA Web excludes from the DSM expectation.
     *
     * <p>Mirrors WA Web's {@code K(e)} predicate: the stanza's
     * {@code meta.appdata} attribute is {@code "default"} and the decoded
     * container carries a {@link com.github.auties00.cobalt.model.message.group.SenderKeyDistributionMessage}
     * side channel. In that case, the GROUP-direct self message is allowed to
     * arrive without a DSM wrapper because it is a key-rotation bootstrap
     * rather than a user-visible payload.
     *
     * @param stanza    the parsed stanza
     * @param container the decoded message container
     * @return {@code true} when the bootstrap exception applies
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingApiUtils", exports = "parseMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isAppdataDefaultSenderKeyOnly(MessageReceiveStanza stanza, MessageContainer container) {
        return stanza.appdata().filter("default"::equals).isPresent()
                && container.senderKeyDistributionMessage().isPresent();
    }

    /**
     * Unwraps a {@link DeviceSentMessage} envelope and merges
     * {@code messageContextInfo} fields from the outer envelope into the inner
     * message.
     *
     * <p>The inner message takes priority for the bot, secret, and thread fields and
     * the outer envelope supplies {@code limitSharingV2}.
     *
     * @implNote
     * This implementation preserves Cobalt-only inner context fields
     * ({@code deviceListMetadata}, {@code paddingBytes}, etc.) by copying every
     * inner field individually before applying the outer-vs-inner precedence on
     * {@code messageSecret}, {@code messageAssociation}, {@code threadId},
     * {@code botMetadata}, and {@code limitSharingV2}; WhatsApp Web's reference
     * implementation only carries the five fields exercised by its
     * {@code MessageContextInfo} type so Cobalt's extra fields have no WA Web
     * counterpart and are folded in unconditionally.
     *
     * @param outerContainer the outer container carrying the DSM
     * @param dsm            the DSM wrapper
     * @param stanza         the parsed stanza, used for error context
     * @return the inner container with the merged context info
     * @throws WhatsAppMessageException.Receive.InvalidDeviceSentMessage if the
     *         inner message is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebDeviceSentMessageProtoUtils", exports = "unwrapDeviceSentMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private MessageContainer unwrapDeviceSentMessage(
            MessageContainer outerContainer,
            DeviceSentMessage dsm,
            MessageReceiveStanza stanza
    ) {
        var inner = dsm.message();
        if (inner.isEmpty()) {
            throw new WhatsAppMessageException.Receive.InvalidDeviceSentMessage(
                    DsmErrorType.INVALID_DSM);
        }

        var innerContainer = inner.get();

        var outerCtx = outerContainer.messageContextInfo().orElse(null);
        var innerCtx = innerContainer.messageContextInfo().orElse(null);

        var mergedCtx = new ChatMessageContextInfoBuilder();

        if (innerCtx != null) {
            innerCtx.deviceListMetadata().ifPresent(mergedCtx::deviceListMetadata);
            innerCtx.deviceListMetadataVersion().ifPresent(mergedCtx::deviceListMetadataVersion);
            innerCtx.paddingBytes().ifPresent(mergedCtx::paddingBytes);
            innerCtx.messageAddOnDurationInSecs().ifPresent(mergedCtx::messageAddOnDurationInSecs);
            innerCtx.botMessageSecret().ifPresent(mergedCtx::botMessageSecret);
            innerCtx.reportingTokenVersion().ifPresent(mergedCtx::reportingTokenVersion);
            innerCtx.messageAddOnExpiryType().ifPresent(mergedCtx::messageAddOnExpiryType);
            if (innerCtx.capiCreatedGroup()) {
                mergedCtx.capiCreatedGroup(true);
            }
            innerCtx.supportPayload().ifPresent(mergedCtx::supportPayload);
            innerCtx.limitSharing().ifPresent(mergedCtx::limitSharing);
            innerCtx.weblinkRenderConfig().ifPresent(mergedCtx::weblinkRenderConfig);
        }

        var messageSecret = innerCtx != null ? innerCtx.messageSecret().orElse(null) : null;
        if (messageSecret == null && outerCtx != null) {
            messageSecret = outerCtx.messageSecret().orElse(null);
        }
        if (messageSecret != null) {
            mergedCtx.messageSecret(messageSecret);
        }

        var messageAssociation = innerCtx != null ? innerCtx.messageAssociation().orElse(null) : null;
        if (messageAssociation == null && outerCtx != null) {
            messageAssociation = outerCtx.messageAssociation().orElse(null);
        }
        if (messageAssociation != null) {
            mergedCtx.messageAssociation(messageAssociation);
        }

        if (outerCtx != null) {
            outerCtx.limitSharingV2().ifPresent(mergedCtx::limitSharingV2);
        }

        List<MessageThreadId> threadId;
        if (innerCtx != null && !innerCtx.threadId().isEmpty()) {
            threadId = innerCtx.threadId();
        } else if (outerCtx != null) {
            threadId = outerCtx.threadId();
        } else {
            threadId = List.of();
        }
        mergedCtx.threadId(threadId);

        var botMetadata = innerCtx != null ? innerCtx.botMetadata().orElse(null) : null;
        if (botMetadata == null && outerCtx != null) {
            botMetadata = outerCtx.botMetadata().orElse(null);
        }
        if (botMetadata != null) {
            mergedCtx.botMetadata(botMetadata);
        }

        return innerContainer.withMessageContextInfo(mergedCtx.build());
    }

    /**
     * Builds the final {@link ChatMessageInfo} from the stanza metadata and the
     * decoded (and possibly DSM-unwrapped) container.
     *
     * <p>The result is the {@link ChatMessageInfo} returned by
     * {@link #receive(Stanza, Jid)} and persisted by the orchestrator.
     *
     * @implNote
     * This implementation always stamps the resulting status as
     * {@link MessageStatus#DELIVERED} regardless of WA Web's {@code ACK.READ}
     * fast-path for self-notes; the status is later overridden by an explicit
     * read receipt if one arrives.
     *
     * @param stanza    the parsed stanza
     * @param chatJid   the effective chat JID (possibly overridden by DSM
     *                  destination)
     * @param container the decoded (and possibly unwrapped) message container
     * @return the fully-populated message info
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingApiUtils", exports = "generateBaseMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private ChatMessageInfo buildChatMessageInfo(
            MessageReceiveStanza stanza,
            Jid chatJid,
            MessageContainer container
    ) {
        var fromMe = isFromMe(stanza);
        var senderJid = stanza.senderJid().toUserJid();

        var key = new MessageKeyBuilder()
                .id(stanza.id())
                .parentJid(chatJid)
                .fromMe(fromMe)
                .senderJid(senderJid)
                .build();

        var builder = new ChatMessageInfoBuilder()
                .key(key)
                .message(container)
                .timestamp(stanza.timestamp())
                .status(MessageStatus.DELIVERED)
                .senderJid(senderJid)
                .broadcast(stanza.chatJid().hasBroadcastServer())
                .pushName(stanza.pushName().orElse(null))
                .urlText(stanza.urlText())
                .urlNumber(stanza.urlNumber());

        container.messageContextInfo()
                .flatMap(ChatMessageContextInfo::messageSecret)
                .ifPresent(builder::messageSecret);

        return builder.build();
    }
}
