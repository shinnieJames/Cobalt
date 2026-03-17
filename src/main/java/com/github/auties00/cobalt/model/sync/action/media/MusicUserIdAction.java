package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Collections;
import java.util.Map;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.MusicUserIdAction")
public final class MusicUserIdAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "music_user_id";

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


    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String musicUserId;

    @ProtobufProperty(index = 2, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.STRING)
    Map<String, String> musicUserIdMap;


    MusicUserIdAction(String musicUserId, Map<String, String> musicUserIdMap) {
        this.musicUserId = musicUserId;
        this.musicUserIdMap = musicUserIdMap;
    }

    public Optional<String> musicUserId() {
        return Optional.ofNullable(musicUserId);
    }

    public Map<String, String> musicUserIdMap() {
        return musicUserIdMap == null ? Map.of() : Collections.unmodifiableMap(musicUserIdMap);
    }

    public void setMusicUserId(String musicUserId) {
        this.musicUserId = musicUserId;
    }

    public void setMusicUserIdMap(Map<String, String> musicUserIdMap) {
        this.musicUserIdMap = musicUserIdMap;
    }
}
