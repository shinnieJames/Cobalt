package com.github.auties00.cobalt.model.message;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

@ProtobufMessage(name = "Citation")
public final class MessageCitation {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String title;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String subtitle;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String cmsId;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String imageUrl;


    MessageCitation(String title, String subtitle, String cmsId, String imageUrl) {
        this.title = Objects.requireNonNull(title);
        this.subtitle = Objects.requireNonNull(subtitle);
        this.cmsId = Objects.requireNonNull(cmsId);
        this.imageUrl = Objects.requireNonNull(imageUrl);
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public String cmsId() {
        return cmsId;
    }

    public String imageUrl() {
        return imageUrl;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public void setCmsId(String cmsId) {
        this.cmsId = cmsId;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
