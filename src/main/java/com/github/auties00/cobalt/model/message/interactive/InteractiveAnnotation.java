package com.github.auties00.cobalt.model.message.interactive;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.location.Location;
import com.github.auties00.cobalt.model.location.Point;
import com.github.auties00.cobalt.model.message.media.EmbeddedContent;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "InteractiveAnnotation")
public final class InteractiveAnnotation {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<Point> polygonVertices;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean shouldSkipConfirmation;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    EmbeddedContent embeddedContent;

    @ProtobufProperty(index = 8, type = ProtobufType.ENUM)
    StatusLinkType statusLinkType;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    Location location;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ContextInfo.ForwardedNewsletterMessageInfo newsletter;

    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    Boolean embeddedAction;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    TapLinkAction tapAction;


    InteractiveAnnotation(List<Point> polygonVertices, Boolean shouldSkipConfirmation, EmbeddedContent embeddedContent, StatusLinkType statusLinkType, Location location, ContextInfo.ForwardedNewsletterMessageInfo newsletter, Boolean embeddedAction, TapLinkAction tapAction) {
        this.polygonVertices = polygonVertices;
        this.shouldSkipConfirmation = shouldSkipConfirmation;
        this.embeddedContent = embeddedContent;
        this.statusLinkType = statusLinkType;
        this.location = location;
        this.newsletter = newsletter;
        this.embeddedAction = embeddedAction;
        this.tapAction = tapAction;
    }

    public List<Point> polygonVertices() {
        return polygonVertices == null ? List.of() : Collections.unmodifiableList(polygonVertices);
    }

    public boolean shouldSkipConfirmation() {
        return shouldSkipConfirmation != null && shouldSkipConfirmation;
    }

    public Optional<EmbeddedContent> embeddedContent() {
        return Optional.ofNullable(embeddedContent);
    }

    public Optional<StatusLinkType> statusLinkType() {
        return Optional.ofNullable(statusLinkType);
    }

    public Optional<? extends InteractiveAction> action() {
        if (location != null) return Optional.of(location);
        if (newsletter != null) return Optional.of(newsletter);
        if (embeddedAction != null) return Optional.of(InteractiveAction.EmbeddedAction.of(embeddedAction));
        if (tapAction != null) return Optional.of(tapAction);
        return Optional.empty();
    }

    public void setPolygonVertices(List<Point> polygonVertices) {
        this.polygonVertices = polygonVertices;
    }

    public void setShouldSkipConfirmation(Boolean shouldSkipConfirmation) {
        this.shouldSkipConfirmation = shouldSkipConfirmation;
    }

    public void setEmbeddedContent(EmbeddedContent embeddedContent) {
        this.embeddedContent = embeddedContent;
    }

    public void setStatusLinkType(StatusLinkType statusLinkType) {
        this.statusLinkType = statusLinkType;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setNewsletter(ContextInfo.ForwardedNewsletterMessageInfo newsletter) {
        this.newsletter = newsletter;
    }

    public void setEmbeddedAction(Boolean embeddedAction) {
        this.embeddedAction = embeddedAction;
    }

    public void setTapAction(TapLinkAction tapAction) {
        this.tapAction = tapAction;
    }

    @ProtobufEnum(name = "InteractiveAnnotation.StatusLinkType")
    public static enum StatusLinkType {
        RASTERIZED_LINK_PREVIEW(1),
        RASTERIZED_LINK_TRUNCATED(2),
        RASTERIZED_LINK_FULL_URL(3);

        StatusLinkType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
