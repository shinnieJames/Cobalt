package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "SyncActionValue.NoteEditAction")
public final class NoteEditAction implements SyncAction<NoteEditActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "note_edit";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

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
    NoteType type;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    Jid chatJid;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64)
    Long createdAt;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean deleted;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String unstructuredContent;


    NoteEditAction(NoteType type, Jid chatJid, Long createdAt, Boolean deleted, String unstructuredContent) {
        this.type = type;
        this.chatJid = chatJid;
        this.createdAt = createdAt;
        this.deleted = deleted;
        this.unstructuredContent = unstructuredContent;
    }

    public Optional<NoteType> type() {
        return Optional.ofNullable(type);
    }

    public Optional<Jid> chatJid() {
        return Optional.ofNullable(chatJid);
    }

    public OptionalLong createdAt() {
        return createdAt == null ? OptionalLong.empty() : OptionalLong.of(createdAt);
    }

    public boolean deleted() {
        return deleted != null && deleted;
    }

    public Optional<String> unstructuredContent() {
        return Optional.ofNullable(unstructuredContent);
    }

    public void setType(NoteType type) {
        this.type = type;
    }

    public void setChatJid(Jid chatJid) {
        this.chatJid = chatJid;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public void setUnstructuredContent(String unstructuredContent) {
        this.unstructuredContent = unstructuredContent;
    }

    @ProtobufEnum(name = "SyncActionValue.NoteEditAction.NoteType")
    public static enum NoteType {
        UNSTRUCTURED(1),
        STRUCTURED(2);

        NoteType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }


}
