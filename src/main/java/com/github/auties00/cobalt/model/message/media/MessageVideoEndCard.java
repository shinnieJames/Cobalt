package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.message.Message;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

@ProtobufMessage(name = "Message.VideoEndCard")
public final class MessageVideoEndCard implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String username;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String caption;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String thumbnailImageUrl;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String profilePictureUrl;


    MessageVideoEndCard(String username, String caption, String thumbnailImageUrl, String profilePictureUrl) {
        this.username = Objects.requireNonNull(username);
        this.caption = Objects.requireNonNull(caption);
        this.thumbnailImageUrl = Objects.requireNonNull(thumbnailImageUrl);
        this.profilePictureUrl = Objects.requireNonNull(profilePictureUrl);
    }

    public String username() {
        return username;
    }

    public String caption() {
        return caption;
    }

    public String thumbnailImageUrl() {
        return thumbnailImageUrl;
    }

    public String profilePictureUrl() {
        return profilePictureUrl;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setThumbnailImageUrl(String thumbnailImageUrl) {
        this.thumbnailImageUrl = thumbnailImageUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }
}
