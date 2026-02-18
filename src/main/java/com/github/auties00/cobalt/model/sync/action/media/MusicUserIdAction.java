package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Collections;
import java.util.Map;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.MusicUserIdAction")
public final class MusicUserIdAction implements SyncAction {
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

    public MusicUserIdAction setMusicUserId(String musicUserId) {
        this.musicUserId = musicUserId;
        return this;
    }

    public MusicUserIdAction setMusicUserIdMap(Map<String, String> musicUserIdMap) {
        this.musicUserIdMap = musicUserIdMap;
        return this;
    }
}
