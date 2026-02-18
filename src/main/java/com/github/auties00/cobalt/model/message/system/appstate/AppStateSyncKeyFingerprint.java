package com.github.auties00.cobalt.model.message.system.appstate;

import com.github.auties00.cobalt.model.message.Message;

import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.AppStateSyncKeyFingerprint")
public final class AppStateSyncKeyFingerprint implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer rawId;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
    Integer currentIndex;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT32, packed = true)
    List<Integer> deviceIndexes;


    AppStateSyncKeyFingerprint(Integer rawId, Integer currentIndex, List<Integer> deviceIndexes) {
        this.rawId = rawId;
        this.currentIndex = currentIndex;
        this.deviceIndexes = deviceIndexes;
    }

    public OptionalInt rawId() {
        return rawId == null ? OptionalInt.empty() : OptionalInt.of(rawId);
    }

    public OptionalInt currentIndex() {
        return currentIndex == null ? OptionalInt.empty() : OptionalInt.of(currentIndex);
    }

    public List<Integer> deviceIndexes() {
        return deviceIndexes == null ? List.of() : Collections.unmodifiableList(deviceIndexes);
    }

    public AppStateSyncKeyFingerprint setRawId(Integer rawId) {
        this.rawId = rawId;
        return this;
    }

    public AppStateSyncKeyFingerprint setCurrentIndex(Integer currentIndex) {
        this.currentIndex = currentIndex;
        return this;
    }

    public AppStateSyncKeyFingerprint setDeviceIndexes(List<Integer> deviceIndexes) {
        this.deviceIndexes = deviceIndexes;
        return this;
    }
}
