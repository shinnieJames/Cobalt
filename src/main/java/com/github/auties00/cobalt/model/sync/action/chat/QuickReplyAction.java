package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "SyncActionValue.QuickReplyAction")
public final class QuickReplyAction implements SyncAction<QuickReplyActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "quick_reply";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 2;

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
    String shortcut;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String message;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    List<String> keywords;

    @ProtobufProperty(index = 4, type = ProtobufType.INT32)
    Integer count;

    @ProtobufProperty(index = 5, type = ProtobufType.BOOL)
    Boolean deleted;


    QuickReplyAction(String shortcut, String message, List<String> keywords, Integer count, Boolean deleted) {
        this.shortcut = shortcut;
        this.message = message;
        this.keywords = keywords;
        this.count = count;
        this.deleted = deleted;
    }

    public Optional<String> shortcut() {
        return Optional.ofNullable(shortcut);
    }

    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    public List<String> keywords() {
        return keywords == null ? List.of() : Collections.unmodifiableList(keywords);
    }

    public OptionalInt count() {
        return count == null ? OptionalInt.empty() : OptionalInt.of(count);
    }

    public boolean deleted() {
        return deleted != null && deleted;
    }

    public void setShortcut(String shortcut) {
        this.shortcut = shortcut;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }


}
