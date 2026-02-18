package com.github.auties00.cobalt.model.message.util;

import java.util.Objects;

@ProtobufMessage(name = "Citation")
public final class Citation {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String title;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String subtitle;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String cmsId;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String imageUrl;


    Citation(String title, String subtitle, String cmsId, String imageUrl) {
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

    public Citation setTitle(String title) {
        this.title = title;
        return this;
    }

    public Citation setSubtitle(String subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    public Citation setCmsId(String cmsId) {
        this.cmsId = cmsId;
        return this;
    }

    public Citation setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        return this;
    }
}
