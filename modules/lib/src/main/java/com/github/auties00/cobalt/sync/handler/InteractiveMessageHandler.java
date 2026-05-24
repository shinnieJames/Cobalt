package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.InteractiveMessageState;
import com.github.auties00.cobalt.model.chat.InteractiveMessageStateBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.InteractiveMessageAction;
import com.github.auties00.cobalt.model.sync.action.chat.InteractiveMessageAction.InteractiveMessageActionMode;
import com.github.auties00.cobalt.model.sync.action.chat.InteractiveMessageActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.Collection;

/**
 * Applies the {@code interactive_message_action} app-state sync action that
 * disables the call-to-action on a Galaxy interactive message.
 *
 * @apiNote
 * Drives the cross-device "disable CTA" flow surfaced by Galaxy
 * commerce/CTWA messages: when the primary device hides a CTA the
 * resulting mutation fans out across the {@link SyncPatchType#REGULAR_LOW}
 * collection so companions stop offering the action. The mutation index
 * is the standard message-keyed shape with an extra {@code subId} slot
 * for the per-CTA discriminator, formatted as
 * {@snippet :
 *     ["interactive_message_action", chatJid, messageId, fromMe, participant, subId]
 * }
 *
 * @implNote
 * This implementation persists the per-CTA state through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#putInteractiveMessageState(InteractiveMessageState)}
 * keyed by {@code agmId|<id>}, {@code messageId|<id>} and the full
 * composite index, replacing WA Web's
 * {@code frontendFireAndForget("addGalaxyDisableCTAByAgmId" / "addGalaxyDisableCTAMessageId")}
 * RPCs. Chat resolution is direct via
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#findChatByJid(Jid)}
 * rather than through WA Web's
 * {@code WAWebSyncdResolveMessages.resolveMessagesForMutations} batch
 * cache, and message lookup uses
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#findMessageById}
 * in place of the {@code msgKeyToDbIdWithoutFromMeParticipant} prefix
 * scan.
 */
@WhatsAppWebModule(moduleName = "WAWebInteractiveMessageSync")
public final class InteractiveMessageHandler implements WebAppStateActionHandler {

    /**
     * Constructs a new singleton {@link InteractiveMessageHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebInteractiveMessageSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public InteractiveMessageHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebInteractiveMessageSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return InteractiveMessageAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebInteractiveMessageSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return InteractiveMessageAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebInteractiveMessageSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return InteractiveMessageAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation records the {@code agmId} state before the
     * message lookup so that an absent message still publishes the AGM
     * mapping (matching WA Web's order); when the message is found and
     * the action is
     * {@link InteractiveMessageActionMode#DISABLE_CTA} the per-message
     * and per-composite-index entries are written. A {@code null}
     * {@code agmId} combined with an absent message yields
     * {@link MutationApplicationResult#orphan(String, String)} with model
     * type {@code "Msg"}; a non-{@link InteractiveMessageActionMode#DISABLE_CTA}
     * action with a present message resolves to
     * {@link MutationApplicationResult#skipped()} matching WA Web's
     * {@code Skipped} branch.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebInteractiveMessageSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            if (mutation.operation() != SyncdOperation.SET) {
                return MutationApplicationResult.unsupported();
            }

            var indexArray = JSON.parseArray(mutation.index());
            if (indexArray.size() < 6) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            var chatJidString = indexArray.getString(1);
            var messageId = indexArray.getString(2);
            var fromMeString = indexArray.getString(3);
            var participantString = indexArray.getString(4);
            var subIdString = indexArray.getString(5);

            if (isNullOrEmpty(chatJidString) || isNullOrEmpty(messageId)
                    || isNullOrEmpty(fromMeString) || isNullOrEmpty(participantString)
                    || isNullOrEmpty(subIdString)) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (!(mutation.value().action().orElse(null) instanceof InteractiveMessageAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var incomingMsgKey = SyncdIndexUtils.syncKeyToMsgKey(
                    client.store(), chatJidString, messageId, fromMeString, participantString
            );
            if (incomingMsgKey.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            var chatJid = Jid.of(chatJidString);
            var localChat = client.store().findChatByJid(chatJid);

            var agmId = action.agmId().orElse(null);

            if (localChat.isEmpty()) {
                return MutationApplicationResult.orphan(
                        SyncdIndexUtils.serializeMessageKey(incomingMsgKey.get()),
                        "Msg"
                );
            }

            var maybeMessage = client.store().findMessageById(localChat.get(), messageId);

            if (agmId != null) {
                client.store().putInteractiveMessageState(new InteractiveMessageStateBuilder()
                        .messageId("agmId|" + agmId)
                        .type(action.type())
                        .agmId(action.agmId().orElse(null))
                        .build());
            }

            if (maybeMessage.isEmpty()) {
                if (agmId != null) {
                    return MutationApplicationResult.success();
                }

                return MutationApplicationResult.orphan(
                        SyncdIndexUtils.serializeMessageKey(incomingMsgKey.get()),
                        "Msg"
                );
            }

            var chatMessage = maybeMessage.get();

            if (action.type() != InteractiveMessageActionMode.DISABLE_CTA) {
                return MutationApplicationResult.skipped();
            }

            var messageKeyId = chatMessage.key().id().orElse(messageId);
            client.store().putInteractiveMessageState(new InteractiveMessageStateBuilder()
                    .messageId("messageId|" + messageKeyId)
                    .type(action.type())
                    .agmId(action.agmId().orElse(null))
                    .build());
            client.store().putInteractiveMessageState(new InteractiveMessageStateBuilder()
                    .messageId("%s|%s|%s|%s|%s".formatted(chatJidString, messageId, fromMeString, participantString, subIdString))
                    .type(action.type())
                    .agmId(action.agmId().orElse(null))
                    .build());
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Builds the {@link InteractiveMessageAction} payload that callers
     * embed inside an outgoing pending mutation.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code $InteractiveMessageSync$p_1} factory shape so that
     * {@link com.github.auties00.cobalt.sync.WebAppStateSender} can
     * assemble the mutation envelope without duplicating the typed
     * action body. The remaining wiring (chat-jid resolution,
     * {@link com.github.auties00.cobalt.model.sync.SyncActionValue}
     * wrapping, signing) lives in the sender pipeline because Cobalt
     * splits envelope construction from action authoring.
     *
     * @param type  the interactive-message action mode, normally
     *              {@link InteractiveMessageActionMode#DISABLE_CTA}
     * @param agmId the optional Galaxy AGM identifier; pass {@code null}
     *              when the action is not bound to a specific AGM
     * @return a freshly built {@link InteractiveMessageAction}
     */
    @WhatsAppWebExport(moduleName = "WAWebInteractiveMessageSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public InteractiveMessageAction buildDisableCTAAction(InteractiveMessageActionMode type, String agmId) {
        var builder = new InteractiveMessageActionBuilder().type(type);
        if (agmId != null) {
            builder.agmId(agmId);
        }
        return builder.build();
    }

    /**
     * Returns whether the given string is {@code null} or empty.
     *
     * @apiNote
     * Mirrors the JavaScript falsy check {@code (!value)} that WA Web
     * applies to every part of the five-element index before treating
     * the mutation as malformed.
     *
     * @param value the candidate string
     * @return {@code true} when {@code value} is {@code null} or has
     *         length zero
     */
    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * Returns the per-AGM and per-message
     * {@link InteractiveMessageState} entries currently tracked in the
     * store.
     *
     * @apiNote
     * Provided as a test seam so suites that exercise the handler can
     * read the state recorded by
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * without exposing
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#interactiveMessageStates()}
     * to every caller.
     *
     * @param client the {@link WhatsAppClient} whose store should be
     *               read
     * @return the live, immutable view of the store's interactive
     *         message states
     */
    public Collection<InteractiveMessageState> interactiveMessageStates(WhatsAppClient client) {
        return client.store().interactiveMessageStates();
    }
}
