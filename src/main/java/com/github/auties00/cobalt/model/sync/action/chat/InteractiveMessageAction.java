package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
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
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_LOW;

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

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String agmId;

    InteractiveMessageAction(InteractiveMessageActionMode type, String agmId) {
        this.type = Objects.requireNonNull(type);
        this.agmId = agmId;
    }

    public InteractiveMessageActionMode type() {
        return type;
    }

    public java.util.Optional<String> agmId() {
        return java.util.Optional.ofNullable(agmId);
    }

    public void setType(InteractiveMessageActionMode type) {
        this.type = type;
    }

    public void setAgmId(String agmId) {
        this.agmId = agmId;
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
