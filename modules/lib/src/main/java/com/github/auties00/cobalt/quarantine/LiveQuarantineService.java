package com.github.auties00.cobalt.quarantine;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.NewMessageListener;
import com.github.auties00.cobalt.listener.linked.LinkedMessageQuarantinedListener;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.QuarantinedMessageBuilder;
import com.github.auties00.cobalt.model.message.call.CallLogMessage;
import com.github.auties00.cobalt.model.message.contact.ContactMessage;
import com.github.auties00.cobalt.model.message.contact.ContactsArrayMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
import com.github.auties00.cobalt.model.message.interactive.TemplateButton;
import com.github.auties00.cobalt.model.message.interactive.TemplateMessage;
import com.github.auties00.cobalt.model.message.location.LiveLocationMessage;
import com.github.auties00.cobalt.model.message.location.LocationMessage;
import com.github.auties00.cobalt.model.message.media.AlbumMessage;
import com.github.auties00.cobalt.model.message.media.AudioMessage;
import com.github.auties00.cobalt.model.message.media.DocumentMessage;
import com.github.auties00.cobalt.model.message.media.ImageMessage;
import com.github.auties00.cobalt.model.message.media.StickerMessage;
import com.github.auties00.cobalt.model.message.media.VideoMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.security.EncReactionMessage;
import com.github.auties00.cobalt.model.message.system.KeepInChatMessage;
import com.github.auties00.cobalt.model.message.system.PinInChatMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.message.text.HighlyStructuredMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.model.privacy.DefenseModePrivacyValue;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.DefenseModeQuarantineEventBuilder;
import com.github.auties00.cobalt.wam.type.DefenseModeQuarantineAction;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production {@link QuarantineService} backed by a {@link LinkedWhatsAppClient}.
 *
 * @implNote
 * This implementation classifies {@link MessageContainer#content()} (the envelope-unwrapped
 * message) rather than re-deriving WA Web's separate {@code maybeGetFutureproofMessage} pass:
 * because {@code content()} already unwraps every view-once, ephemeral and futureproof layer to
 * the innermost displayable message, classifying it yields the same result as WA Web's
 * stronger-of-outer-and-inner rule (the outer futureproof envelope itself has no displayable
 * content and so never raises the verdict).
 */
@WhatsAppWebModule(moduleName = "WAWebQuarantineActionUtils")
public final class LiveQuarantineService implements QuarantineService {
    /**
     * The bound client, used to read Defense Mode state, the account identity and the contact
     * roster.
     */
    private final LinkedWhatsAppClient client;

    /**
     * The AB-props service consulted for the Defense Mode feature flags.
     */
    private final ABPropsService abPropsService;

    /**
     * The telemetry sink for the {@code DefenseModeQuarantine} metrics.
     */
    private final WamService wamService;

    /**
     * The keys of the messages quarantined during this session, used to drive {@link #restoreAll()}
     * without scanning the message store.
     */
    private final Set<MessageKey> quarantinedKeys;

    /**
     * Constructs a service bound to the given collaborators.
     *
     * @param client         the bound client, must not be {@code null}
     * @param abPropsService the AB-props service, must not be {@code null}
     * @param wamService     the telemetry sink, must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public LiveQuarantineService(LinkedWhatsAppClient client, ABPropsService abPropsService, WamService wamService) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
        this.wamService = Objects.requireNonNull(wamService, "wamService cannot be null");
        this.quarantinedKeys = ConcurrentHashMap.newKeySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQuarantineActionUtils", exports = "getQuarantineAction", adaptation = WhatsAppAdaptation.ADAPTED)
    public QuarantineAction getQuarantineAction(MessageContainer message, Jid sender) {
        if (message == null
                || !abPropsService.getBool(ABProp.DEFENSE_MODE_QUARANTINE)
                || !isDefenseModeActive()
                || !isQuarantinableSender(sender)) {
            return QuarantineAction.NO_QUARANTINE;
        }
        return classify(message.content());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQuarantineDataStore", exports = "bulkCreateOrReplaceQuarantineData", adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean quarantine(ChatMessageInfo info) {
        var action = getQuarantineAction(info.message(), info.senderJid().orElse(null));
        if (!action.shouldQuarantine()) {
            return false;
        }
        info.setQuarantinedMessage(new QuarantinedMessageBuilder()
                .originalData(MessageContainerSpec.encode(info.message()))
                .extractedText(action.extractedText().orElse(null))
                .build());
        quarantinedKeys.add(info.key());
        wamService.commit(new DefenseModeQuarantineEventBuilder()
                .quarantineAction(DefenseModeQuarantineAction.QUARANTINED_MSG)
                .defenseModeQuarantineEventCount(1)
                .build());
        for (var listener : client.store().listeners()) {
            if (listener instanceof LinkedMessageQuarantinedListener typed) {
                Thread.startVirtualThread(() -> typed.onMessageQuarantined(client, info));
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUnquarantineMessageJob", exports = "unquarantineMessageJob", adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean restore(ChatMessageInfo info) {
        boolean restored;
        try {
            restored = reinject(info);
        } catch (RuntimeException exception) {
            restored = false;
        }
        wamService.commit(new DefenseModeQuarantineEventBuilder()
                .quarantineAction(restored
                        ? DefenseModeQuarantineAction.QUARANTINE_RESTORE_SUCCESS
                        : DefenseModeQuarantineAction.QUARANTINE_RESTORE_FAILED)
                .build());
        return restored;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation drives the restore from the in-memory {@link #quarantinedKeys} index
     * rather than scanning the message store. Messages quarantined in an earlier session are not in
     * the index and so are not auto-restored; releasing them would require a persisted index or a
     * full store scan.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBulkUnquarantineMessagesJob", exports = "bulkUnquarantineMessagesJob", adaptation = WhatsAppAdaptation.ADAPTED)
    public void restoreAll() {
        var restored = 0;
        for (var key : Set.copyOf(quarantinedKeys)) {
            if (!(client.store().chatStore().findMessageByKey(key).orElse(null) instanceof ChatMessageInfo info)) {
                quarantinedKeys.remove(key);
                continue;
            }
            try {
                if (reinject(info)) {
                    restored++;
                }
            } catch (RuntimeException exception) {
                // a single failed restore does not abort the bulk pass
            }
        }
        if (restored > 0) {
            wamService.commit(new DefenseModeQuarantineEventBuilder()
                    .quarantineAction(DefenseModeQuarantineAction.QUARANTINE_RESTORE_AUTO)
                    .defenseModeQuarantineEventCount(restored)
                    .build());
        }
    }

    /**
     * Clears the quarantine flag on a message and re-delivers it to the {@code onNewMessage}
     * listeners.
     *
     * @param info the quarantined chat message
     * @return {@code true} when the message was quarantined and is now restored, {@code false} when
     *         it was not quarantined
     */
    private boolean reinject(ChatMessageInfo info) {
        if (info.quarantinedMessage().isEmpty()) {
            return false;
        }
        info.setQuarantinedMessage(null);
        quarantinedKeys.remove(info.key());
        for (var listener : client.store().listeners()) {
            if (listener instanceof NewMessageListener typed) {
                Thread.startVirtualThread(() -> typed.onNewMessage(client, info));
            }
        }
        return true;
    }

    /**
     * Returns whether Defense Mode is currently active for the account.
     *
     * @return {@code true} when the feature is available and the Defense Mode setting is at the
     *         standard tier, {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebQuarantineActionUtils", exports = "isDefenseModeOn", adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isDefenseModeActive() {
        return abPropsService.getInt(ABProp.DEFENSE_MODE_AVAILABLE) >= 1
                && client.store().settingsStore().findPrivacySetting(PrivacySettingType.DEFENSE_MODE)
                .orElse(null) instanceof DefenseModePrivacyValue.OnStandard;
    }

    /**
     * Returns whether the given sender's messages are subject to quarantine.
     *
     * @implNote
     * This implementation gates on the sender being a non-self user JID that is not a saved
     * contact (no {@linkplain Contact#fullName() address-book name}). WA Web additionally exempts
     * PSA, IAS, official-business, support, CAPI-support, AI-hub and Meta-AI-bot senders; Cobalt
     * does not yet classify those special account types, so they are not exempted here.
     *
     * @param sender the message sender, or {@code null}
     * @return {@code true} when the sender is quarantinable, {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebQuarantineActionUtils", exports = "shouldQuarantineSender", adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isQuarantinableSender(Jid sender) {
        if (sender == null || !sender.hasServer(JidServer.user())) {
            return false;
        }
        var account = client.store().accountStore();
        if (account.jid().filter(sender::equals).isPresent() || account.lid().filter(sender::equals).isPresent()) {
            return false;
        }
        // TODO: exempt PSA/IAS/official-business/support/CAPI-support/AI-hub/Meta-AI-bot senders once
        //       Cobalt models those special account types.
        return client.store().contactStore().findContactByJid(sender)
                .flatMap(Contact::fullName)
                .isEmpty();
    }

    /**
     * Classifies the displayable content of a message into a quarantine action.
     *
     * <p>Media messages quarantine with their caption; an extended-text message quarantines with
     * its text only when it carries link-preview media; structured messages quarantine without
     * text unless they are free of embedded media; control and no-content messages are never
     * quarantined.
     *
     * @param content the envelope-unwrapped message content, or {@code null}
     * @return the quarantine action for the content, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebQuarantineActionUtils", exports = "getQuarantineActionForMsg", adaptation = WhatsAppAdaptation.ADAPTED)
    private QuarantineAction classify(Message content) {
        return switch (content) {
            case ExtendedTextMessage text -> hasLinkPreviewMedia(text)
                    ? QuarantineAction.of(text.text().orElse(null))
                    : QuarantineAction.NO_QUARANTINE;
            case ImageMessage image -> QuarantineAction.of(image.caption().orElse(null));
            case VideoMessage video -> QuarantineAction.of(video.caption().orElse(null));
            case DocumentMessage document -> QuarantineAction.of(document.caption().orElse(null));
            case HighlyStructuredMessage structured -> isStructuredSafe(structured)
                    ? QuarantineAction.NO_QUARANTINE : QuarantineAction.WITHOUT_TEXT;
            case TemplateMessage template -> isStructuredSafe(template)
                    ? QuarantineAction.NO_QUARANTINE : QuarantineAction.WITHOUT_TEXT;
            case InteractiveMessage interactive -> isStructuredSafe(interactive)
                    ? QuarantineAction.NO_QUARANTINE : QuarantineAction.WITHOUT_TEXT;
            case CallLogMessage _ -> QuarantineAction.NO_QUARANTINE;
            case ProtocolMessage _ -> QuarantineAction.NO_QUARANTINE;
            case ReactionMessage _ -> QuarantineAction.NO_QUARANTINE;
            case EncReactionMessage _ -> QuarantineAction.NO_QUARANTINE;
            case PollUpdateMessage _ -> QuarantineAction.NO_QUARANTINE;
            case KeepInChatMessage _ -> QuarantineAction.NO_QUARANTINE;
            case PinInChatMessage _ -> QuarantineAction.NO_QUARANTINE;
            case AlbumMessage _ -> QuarantineAction.NO_QUARANTINE;
            case AudioMessage _ -> QuarantineAction.WITHOUT_TEXT;
            case StickerMessage _ -> QuarantineAction.WITHOUT_TEXT;
            case ContactMessage _ -> QuarantineAction.WITHOUT_TEXT;
            case ContactsArrayMessage _ -> QuarantineAction.WITHOUT_TEXT;
            case LocationMessage _ -> QuarantineAction.WITHOUT_TEXT;
            case LiveLocationMessage _ -> QuarantineAction.WITHOUT_TEXT;
            case null, default -> QuarantineAction.NO_QUARANTINE;
        };
    }

    /**
     * Returns whether an extended-text message carries link-preview media.
     *
     * @param text the extended-text message
     * @return {@code true} when any thumbnail, direct path or media key is present
     */
    private boolean hasLinkPreviewMedia(ExtendedTextMessage text) {
        return text.jpegThumbnail().isPresent()
                || text.thumbnailDirectPath().isPresent()
                || text.mediaKey().isPresent()
                || text.thumbnailSha256().isPresent();
    }

    /**
     * Returns whether a highly-structured message is free of embedded media.
     *
     * @param structured the highly-structured message
     * @return {@code true} when its hydrated template is absent or itself media-free
     */
    private boolean isStructuredSafe(HighlyStructuredMessage structured) {
        return structured.hydratedHsm()
                .map(this::isStructuredSafe)
                .orElse(true);
    }

    /**
     * Returns whether a template message is free of embedded media across its format variant and
     * hydrated template.
     *
     * @param template the template message
     * @return {@code true} when no variant carries media, {@code false} otherwise
     */
    private boolean isStructuredSafe(TemplateMessage template) {
        var formatSafe = switch (template.format().orElse(null)) {
            case TemplateMessage.FourRowTemplate fourRow -> isStructuredSafe(fourRow);
            case TemplateMessage.HydratedFourRowTemplate hydrated -> isStructuredSafe(hydrated);
            case InteractiveMessage interactive -> isStructuredSafe(interactive);
            case null, default -> true;
        };
        return formatSafe && template.hydratedTemplate()
                .map(this::isStructuredSafe)
                .orElse(true);
    }

    /**
     * Returns whether a four-row template is free of embedded media in its title, content, footer
     * and buttons.
     *
     * @param fourRow the four-row template
     * @return {@code true} when the template carries no media, {@code false} otherwise
     */
    private boolean isStructuredSafe(TemplateMessage.FourRowTemplate fourRow) {
        var title = fourRow.title().orElse(null);
        if (title != null && !(title instanceof HighlyStructuredMessage)) {
            return false;
        }
        if (!fourRow.content().map(this::isStructuredSafe).orElse(true)) {
            return false;
        }
        if (!fourRow.footer().map(this::isStructuredSafe).orElse(true)) {
            return false;
        }
        for (var button : fourRow.buttons()) {
            if (!isStructuredSafe(button)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether a hydrated four-row template is free of embedded media in its title.
     *
     * @param hydrated the hydrated four-row template
     * @return {@code true} when the title is absent or plain text, {@code false} when it is media
     */
    private boolean isStructuredSafe(TemplateMessage.HydratedFourRowTemplate hydrated) {
        var title = hydrated.title().orElse(null);
        return title == null || title instanceof TemplateMessage.TitleSpec.HydratedTitleText;
    }

    /**
     * Returns whether an interactive message is free of embedded media in its header, content and
     * carousel cards.
     *
     * @param interactive the interactive message
     * @return {@code true} when no media, shop or collection content is present, {@code false}
     *         otherwise
     */
    private boolean isStructuredSafe(InteractiveMessage interactive) {
        if (interactive.header().map(header -> header.media().isPresent()).orElse(false)) {
            return false;
        }
        var content = interactive.content().orElse(null);
        if (content instanceof InteractiveMessage.ShopMessage || content instanceof InteractiveMessage.CollectionMessage) {
            return false;
        }
        if (content instanceof InteractiveMessage.CarouselMessage carousel) {
            for (var card : carousel.cards()) {
                if (!isStructuredSafe(card)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns whether a template button is free of embedded media in its display text and
     * payload.
     *
     * @param button the template button
     * @return {@code true} when every structured field is media-free, {@code false} otherwise
     */
    private boolean isStructuredSafe(TemplateButton button) {
        return switch (button.button().orElse(null)) {
            case TemplateButton.QuickReplyButton quick ->
                    quick.displayText().map(this::isStructuredSafe).orElse(true);
            case TemplateButton.URLButton url ->
                    url.displayText().map(this::isStructuredSafe).orElse(true)
                            && url.url().map(this::isStructuredSafe).orElse(true);
            case TemplateButton.CallButton call ->
                    call.displayText().map(this::isStructuredSafe).orElse(true)
                            && call.phoneNumber().map(this::isStructuredSafe).orElse(true);
            case null, default -> true;
        };
    }
}
