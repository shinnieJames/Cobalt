package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;

@ProtobufMessage(name = "SyncActionValue.LabelReorderingAction")
public final class LabelReorderingAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "label_reordering";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 3;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }


    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    List<Integer> sortedLabelIds;


    LabelReorderingAction(List<Integer> sortedLabelIds) {
        this.sortedLabelIds = sortedLabelIds;
    }

    public List<Integer> sortedLabelIds() {
        return sortedLabelIds == null ? List.of() : Collections.unmodifiableList(sortedLabelIds);
    }

    public LabelReorderingAction setSortedLabelIds(List<Integer> sortedLabelIds) {
        this.sortedLabelIds = sortedLabelIds;
        return this;
    }
}
