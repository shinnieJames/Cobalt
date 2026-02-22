package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;

@ProtobufMessage(name = "SyncActionValue.RecentEmojiWeightsAction")
public final class RecentEmojiWeightsAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "recent_emoji_weights_action";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

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
