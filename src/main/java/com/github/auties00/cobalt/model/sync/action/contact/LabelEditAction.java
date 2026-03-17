package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "SyncActionValue.LabelEditAction")
public final class LabelEditAction implements SyncAction<LabelEditActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "label_edit";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 3;

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

    @ProtobufProperty(index = 2, type = ProtobufType.INT32)
    Integer color;

    @ProtobufProperty(index = 3, type = ProtobufType.INT32)
    Integer predefinedId;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean deleted;

    @ProtobufProperty(index = 5, type = ProtobufType.INT32)
    Integer orderIndex;

    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    Boolean isActive;

    @ProtobufProperty(index = 7, type = ProtobufType.ENUM)
    ListType type;

    @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
    Boolean isImmutable;

    @ProtobufProperty(index = 9, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant muteEndTimeMs;


    LabelEditAction(String name, Integer color, Integer predefinedId, Boolean deleted, Integer orderIndex, Boolean isActive, ListType type, Boolean isImmutable, Instant muteEndTimeMs) {
        this.name = name;
        this.color = color;
        this.predefinedId = predefinedId;
        this.deleted = deleted;
        this.orderIndex = orderIndex;
        this.isActive = isActive;
        this.type = type;
        this.isImmutable = isImmutable;
        this.muteEndTimeMs = muteEndTimeMs;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public OptionalInt color() {
        return color == null ? OptionalInt.empty() : OptionalInt.of(color);
    }

    public OptionalInt predefinedId() {
        return predefinedId == null ? OptionalInt.empty() : OptionalInt.of(predefinedId);
    }

    public boolean deleted() {
        return deleted != null && deleted;
    }

    public OptionalInt orderIndex() {
        return orderIndex == null ? OptionalInt.empty() : OptionalInt.of(orderIndex);
    }

    public boolean isActive() {
        return isActive != null && isActive;
    }

    public Optional<ListType> type() {
        return Optional.ofNullable(type);
    }

    public boolean isImmutable() {
        return isImmutable != null && isImmutable;
    }

    public Optional<Instant> muteEndTimeMs() {
        return Optional.ofNullable(muteEndTimeMs);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setColor(Integer color) {
        this.color = color;
    }

    public void setPredefinedId(Integer predefinedId) {
        this.predefinedId = predefinedId;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public void setActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public void setType(ListType type) {
        this.type = type;
    }

    public void setImmutable(Boolean isImmutable) {
        this.isImmutable = isImmutable;
    }

    public void setMuteEndTimeMs(Instant muteEndTimeMs) {
        this.muteEndTimeMs = muteEndTimeMs;
    }

    @ProtobufEnum(name = "SyncActionValue.LabelEditAction.ListType")
    public static enum ListType {
        NONE(0),
        UNREAD(1),
        GROUPS(2),
        FAVORITES(3),
        PREDEFINED(4),
        CUSTOM(5),
        COMMUNITY(6),
        SERVER_ASSIGNED(7),
        DRAFTED(8),
        AI_HANDOFF(9),
        CHANNELS(10);

        ListType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }


}
