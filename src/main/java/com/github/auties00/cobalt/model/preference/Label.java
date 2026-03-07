package com.github.auties00.cobalt.model.preference;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.action.contact.LabelEditAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SequencedCollection;
import java.util.SequencedSet;

@ProtobufMessage
public final class Label {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final String id;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 3, type = ProtobufType.INT32)
    int color;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    SequencedSet<Jid> assignments;

    @ProtobufProperty(index = 5, type = ProtobufType.INT32)
    Integer predefinedId;

    @ProtobufProperty(index = 6, type = ProtobufType.INT32)
    Integer orderIndex;

    @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
    Boolean isActive;

    @ProtobufProperty(index = 8, type = ProtobufType.ENUM)
    LabelEditAction.ListType type;

    @ProtobufProperty(index = 9, type = ProtobufType.BOOL)
    Boolean isImmutable;

    Label(String id, String name, int color, SequencedSet<Jid> assignments, Integer predefinedId, Integer orderIndex, Boolean isActive, LabelEditAction.ListType type, Boolean isImmutable) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.assignments = assignments;
        this.predefinedId = predefinedId;
        this.orderIndex = orderIndex;
        this.isActive = isActive;
        this.type = type;
        this.isImmutable = isImmutable;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int color() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public OptionalInt predefinedId() {
        return predefinedId == null ? OptionalInt.empty() : OptionalInt.of(predefinedId);
    }

    public void setPredefinedId(Integer predefinedId) {
        this.predefinedId = predefinedId;
    }

    public OptionalInt orderIndex() {
        return orderIndex == null ? OptionalInt.empty() : OptionalInt.of(orderIndex);
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Optional<Boolean> isActive() {
        return Optional.ofNullable(isActive);
    }

    public void setActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Optional<LabelEditAction.ListType> type() {
        return Optional.ofNullable(type);
    }

    public void setType(LabelEditAction.ListType type) {
        this.type = type;
    }

    public Optional<Boolean> isImmutable() {
        return Optional.ofNullable(isImmutable);
    }

    public void setImmutable(Boolean isImmutable) {
        this.isImmutable = isImmutable;
    }

    public SequencedCollection<Jid> assignments() {
        return Collections.unmodifiableSequencedCollection(assignments);
    }

    public void addAssignment(Jid jid) {
        assignments.add(jid);
    }

    public boolean removeAssignment(Jid jid) {
        return assignments.remove(jid);
    }
}
