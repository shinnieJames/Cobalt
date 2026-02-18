package com.github.auties00.cobalt.model.sync.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Objects;

@ProtobufMessage(name = "SyncActionValue.InteractiveMessageAction")
public final class InteractiveMessageAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    InteractiveMessageActionMode type;


    InteractiveMessageAction(InteractiveMessageActionMode type) {
        this.type = Objects.requireNonNull(type);
    }

    public InteractiveMessageActionMode type() {
        return type;
    }

    public InteractiveMessageAction setType(InteractiveMessageActionMode type) {
        this.type = type;
        return this;
    }

    @ProtobufEnum(name = "SyncActionValue.InteractiveMessageAction.InteractiveMessageActionMode")
    public static enum InteractiveMessageActionMode {
        DISABLE_CTA(1);

        InteractiveMessageActionMode(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
