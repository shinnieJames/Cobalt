package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

@ProtobufMessage(name = "SyncActionValue.InteractiveMessageAction")
public final class InteractiveMessageAction implements SyncAction<InteractiveMessageActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "interactive_message_action";

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
