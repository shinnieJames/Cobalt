package com.github.auties00.cobalt.model.message.util;

import com.github.auties00.cobalt.model.message.Message;

import java.util.Objects;

@ProtobufMessage(name = "Message.VideoEndCard")
public final class VideoEndCard implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String username;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String caption;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String thumbnailImageUrl;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String profilePictureUrl;


    VideoEndCard(String username, String caption, String thumbnailImageUrl, String profilePictureUrl) {
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

    public VideoEndCard setUsername(String username) {
        this.username = username;
        return this;
    }

    public VideoEndCard setCaption(String caption) {
        this.caption = caption;
        return this;
    }

    public VideoEndCard setThumbnailImageUrl(String thumbnailImageUrl) {
        this.thumbnailImageUrl = thumbnailImageUrl;
        return this;
    }

    public VideoEndCard setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
        return this;
    }
}
