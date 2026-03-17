package com.github.auties00.cobalt.model.sync;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.SyncActionMessageRange")
public final class SyncActionMessageRange implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "sync_action_message_range";

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

    @ProtobufProperty(index = 1, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant lastMessageTimestamp;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant lastSystemMessageTimestamp;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    List<SyncActionMessage> messages;


    SyncActionMessageRange(Instant lastMessageTimestamp, Instant lastSystemMessageTimestamp, List<SyncActionMessage> messages) {
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.lastSystemMessageTimestamp = lastSystemMessageTimestamp;
        this.messages = messages;
    }

    public Optional<Instant> lastMessageTimestamp() {
        return Optional.ofNullable(lastMessageTimestamp);
    }

    public Optional<Instant> lastSystemMessageTimestamp() {
        return Optional.ofNullable(lastSystemMessageTimestamp);
    }

    public List<SyncActionMessage> messages() {
        return messages == null ? List.of() : Collections.unmodifiableList(messages);
    }

    public void setLastMessageTimestamp(Instant lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public void setLastSystemMessageTimestamp(Instant lastSystemMessageTimestamp) {
        this.lastSystemMessageTimestamp = lastSystemMessageTimestamp;
    }

    public void setMessages(List<SyncActionMessage> messages) {
        this.messages = messages;
    }
}
