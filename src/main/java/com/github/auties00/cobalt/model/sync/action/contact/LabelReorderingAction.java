package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Collections;
import java.util.List;

@ProtobufMessage(name = "SyncActionValue.LabelReorderingAction")
public final class LabelReorderingAction implements SyncAction {
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
