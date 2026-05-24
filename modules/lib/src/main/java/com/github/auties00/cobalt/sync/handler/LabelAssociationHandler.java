package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.LabelBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.LabelAssociationAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code label_jid} app-state sync action that pins or unpins a
 * chat or contact under a given chat label.
 *
 * @apiNote
 * Drives the SMB/Business "label this chat" affordance: when the primary
 * device tags a conversation with a colour-coded label the resulting
 * association fans out across the {@link SyncPatchType#REGULAR}
 * collection so companion devices show the same label badge. The
 * mutation index encodes the label id and the target JID, formatted as
 * {@snippet :
 *     ["label_jid", labelId, chatJid]
 * }
 *
 * @implNote
 * This implementation stores associations directly on the
 * {@link Label#assignments()} set rather than in a separate
 * label-association table the way WA Web's
 * {@code WAWebDBLabelAssociationDatabaseApi.addOrEditLabelAssociations}
 * does, so the
 * {@code applyLabelAssociationChanges} frontend RPC is not modelled.
 * Target-JID resolution mirrors WA Web's chat-table-first, then
 * LID-to-phone fallback order. The
 * {@code biz_automatically_labeled_chat_system_message} notification
 * emitted by WA Web for the {@code DO_NEW_ORDER} and {@code DO_LEAD}
 * predefined labels and the
 * {@code WAWebWamLabelSyncTrackingReporter} telemetry are not modelled.
 */
@WhatsAppWebModule(moduleName = "WAWebLabelJidSync")
public final class LabelAssociationHandler implements WebAppStateActionHandler {

    /**
     * The position of the target JID inside the parsed mutation
     * {@code indexParts} array.
     *
     * @apiNote
     * Mirrors WA Web's {@code chatJidIndex = 2} assignment in the
     * constructor of {@code WAWebLabelJidSync}; preserving the constant
     * keeps the wire-level layout visible if WA Web ever reshapes the
     * index.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int CHAT_JID_INDEX = 2;

    /**
     * Constructs a new singleton {@link LabelAssociationHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public LabelAssociationHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return LabelAssociationAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return LabelAssociationAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return LabelAssociationAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation classifies a missing index slot as
     * {@link MutationApplicationResult#malformed()} explicitly so the
     * outer try/catch does not turn the malformed index into
     * {@link MutationApplicationResult#failed()} via an
     * {@code IndexOutOfBoundsException} from {@code JSON.parseArray}.
     * The {@link LabelAssociationAction#labeled()} accessor coalesces a
     * {@code null} protobuf field to {@code false} per the project's
     * "no Optional&lt;Boolean&gt;" rule, so a {@code null}
     * {@code labeled} is treated as remove rather than as
     * {@link MutationApplicationResult#malformed()}. When the label is
     * not yet known locally a stub
     * {@link Label} with empty name and zero colour is created so the
     * association can be recorded, mirroring WA Web's behaviour where
     * {@code addOrEditLabelAssociations} writes the row even if the
     * label table has not yet seen the corresponding edit.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            if (mutation.operation() != SyncdOperation.SET) {
                return MutationApplicationResult.unsupported();
            }

            var indexArray = JSON.parseArray(mutation.index());
            if (indexArray.size() <= CHAT_JID_INDEX) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            var labelId = indexArray.getString(1);
            var targetJidString = indexArray.getString(CHAT_JID_INDEX);
            if (labelId == null || labelId.isEmpty() || targetJidString == null || targetJidString.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (!(mutation.value().action().orElse(null) instanceof LabelAssociationAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var labeled = action.labeled();

            Jid targetJid;
            try {
                targetJid = Jid.of(targetJidString);
            } catch (Exception jidError) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            if (targetJid == null) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            var resolvedTargetJid = targetJid;
            var resolvedChat = client.store().findChatByJid(targetJid);
            if (resolvedChat.isPresent()) {
                resolvedTargetJid = resolvedChat.get().toJid();
            } else if (targetJid.hasLidServer()) {
                var phoneJid = client.store().findPhoneByLid(targetJid);
                if (phoneJid.isPresent()) {
                    resolvedTargetJid = phoneJid.get();
                }
            }

            var label = client.store()
                    .findLabel(labelId)
                    .orElseGet(() -> {
                        var newLabel = new LabelBuilder()
                                .id(labelId)
                                .name("")
                                .color(0)
                                .build();
                        client.store().addLabel(newLabel);
                        return newLabel;
                    });

            if (labeled) {
                label.addAssignment(resolvedTargetJid);
            } else {
                label.removeAssignment(resolvedTargetJid);
            }

            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

}
