package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Collections;
import java.util.List;

@ProtobufMessage(name = "SyncActionValue.RecentEmojiWeightsAction")
public final class RecentEmojiWeightsAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<RecentEmojiWeight> weights;


    RecentEmojiWeightsAction(List<RecentEmojiWeight> weights) {
        this.weights = weights;
    }

    public List<RecentEmojiWeight> weights() {
        return weights == null ? List.of() : Collections.unmodifiableList(weights);
    }

    public RecentEmojiWeightsAction setWeights(List<RecentEmojiWeight> weights) {
        this.weights = weights;
        return this;
    }
}
