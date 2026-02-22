package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.model.sync.SyncAction;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.MuteAction")
public final class MuteAction implements SyncAction<MuteActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "mute";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 2;

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


    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean muted;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant muteEndTimestamp;

    @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
    Boolean autoMuted;

    @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant muteEveryoneMentionEndTimestamp;


    MuteAction(Boolean muted, Instant muteEndTimestamp, Boolean autoMuted, Instant muteEveryoneMentionEndTimestamp) {
        this.muted = muted;
        this.muteEndTimestamp = muteEndTimestamp;
        this.autoMuted = autoMuted;
        this.muteEveryoneMentionEndTimestamp = muteEveryoneMentionEndTimestamp;
    }

    public boolean muted() {
        return muted != null && muted;
    }

    public Optional<Instant> muteEndTimestamp() {
        return Optional.ofNullable(muteEndTimestamp);
    }

    public boolean autoMuted() {
        return autoMuted != null && autoMuted;
    }

    public Optional<Instant> muteEveryoneMentionEndTimestamp() {
        return Optional.ofNullable(muteEveryoneMentionEndTimestamp);
    }

    public MuteAction setMuted(Boolean muted) {
        this.muted = muted;
        return this;
    }

    public MuteAction setMuteEndTimestamp(Instant muteEndTimestamp) {
        this.muteEndTimestamp = muteEndTimestamp;
        return this;
    }

    public MuteAction setAutoMuted(Boolean autoMuted) {
        this.autoMuted = autoMuted;
        return this;
    }

    public MuteAction setMuteEveryoneMentionEndTimestamp(Instant muteEveryoneMentionEndTimestamp) {
        this.muteEveryoneMentionEndTimestamp = muteEveryoneMentionEndTimestamp;
        return this;
    }


}
