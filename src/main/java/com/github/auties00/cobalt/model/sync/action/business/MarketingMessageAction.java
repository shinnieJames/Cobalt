package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "SyncActionValue.MarketingMessageAction")
public final class MarketingMessageAction implements SyncAction<MarketingMessageActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "marketingMessage";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR;

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


    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String message;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    MarketingMessagePrototypeType type;

    @ProtobufProperty(index = 4, type = ProtobufType.INT64)
    Long createdAt;

    @ProtobufProperty(index = 5, type = ProtobufType.INT64)
    Long lastSentAt;

    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    Boolean isDeleted;

    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    String mediaId;


    MarketingMessageAction(String name, String message, MarketingMessagePrototypeType type, Long createdAt, Long lastSentAt, Boolean isDeleted, String mediaId) {
        this.name = name;
        this.message = message;
        this.type = type;
        this.createdAt = createdAt;
        this.lastSentAt = lastSentAt;
        this.isDeleted = isDeleted;
        this.mediaId = mediaId;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    public Optional<MarketingMessagePrototypeType> type() {
        return Optional.ofNullable(type);
    }

    public OptionalLong createdAt() {
        return createdAt == null ? OptionalLong.empty() : OptionalLong.of(createdAt);
    }

    public OptionalLong lastSentAt() {
        return lastSentAt == null ? OptionalLong.empty() : OptionalLong.of(lastSentAt);
    }

    public boolean isDeleted() {
        return isDeleted != null && isDeleted;
    }

    public Optional<String> mediaId() {
        return Optional.ofNullable(mediaId);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setType(MarketingMessagePrototypeType type) {
        this.type = type;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public void setLastSentAt(Long lastSentAt) {
        this.lastSentAt = lastSentAt;
    }

    public void setDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    @ProtobufEnum(name = "SyncActionValue.MarketingMessageAction.MarketingMessagePrototypeType")
    public static enum MarketingMessagePrototypeType {
        PERSONALIZED(0);

        MarketingMessagePrototypeType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }


}
