package com.github.auties00.cobalt.model.message.call;

import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.BCallMessage")
public final class BCallMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String sessionId;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    MediaType mediaType;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] masterKey;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String caption;


    BCallMessage(String sessionId, MediaType mediaType, byte[] masterKey, String caption) {
        this.sessionId = sessionId;
        this.mediaType = mediaType;
        this.masterKey = masterKey;
        this.caption = caption;
    }

    public Optional<String> sessionId() {
        return Optional.ofNullable(sessionId);
    }

    public Optional<MediaType> mediaType() {
        return Optional.ofNullable(mediaType);
    }

    public Optional<byte[]> masterKey() {
        return Optional.ofNullable(masterKey);
    }

    public Optional<String> caption() {
        return Optional.ofNullable(caption);
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public void setMasterKey(byte[] masterKey) {
        this.masterKey = masterKey;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    @ProtobufEnum(name = "Message.BCallMessage.MediaType")
    public static enum MediaType {
        UNKNOWN(0),
        AUDIO(1),
        VIDEO(2);

        MediaType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
