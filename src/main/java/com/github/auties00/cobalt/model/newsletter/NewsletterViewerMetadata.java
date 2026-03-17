package com.github.auties00.cobalt.model.newsletter;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

/**
 * The metadata describing the current viewer's relationship with a
 * newsletter, including their mute preference, role, and WAMO
 * subscription status.
 */
@ProtobufMessage
public final class NewsletterViewerMetadata {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    boolean mute;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    NewsletterViewerRole role;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    NewsletterWamoSubStatus wamoSubStatus;

    /**
     * Constructs a new {@code NewsletterViewerMetadata} with the specified
     * mute preference, role, and WAMO subscription status.
     *
     * @param mute          {@code true} if the viewer has muted the newsletter
     * @param role          the viewer's role, defaults to {@link NewsletterViewerRole#UNKNOWN}
     *                      if {@code null}
     * @param wamoSubStatus the WAMO subscription status, may be {@code null}
     */
    NewsletterViewerMetadata(boolean mute, NewsletterViewerRole role, NewsletterWamoSubStatus wamoSubStatus) {
        this.mute = mute;
        this.role = Objects.requireNonNullElse(role, NewsletterViewerRole.UNKNOWN);
        this.wamoSubStatus = wamoSubStatus;
    }

    /**
     * Returns whether the viewer has muted the newsletter.
     *
     * @return {@code true} if the newsletter is muted
     */
    public boolean mute() {
        return mute;
    }

    /**
     * Returns the viewer's role within the newsletter.
     *
     * @return the viewer role, never {@code null}
     */
    public NewsletterViewerRole role() {
        return role;
    }

    /**
     * Returns the viewer's WAMO subscription status, if available.
     *
     * @return an {@link Optional} containing the WAMO sub status,
     *         or empty if not set
     */
    public Optional<NewsletterWamoSubStatus> wamoSubStatus() {
        return Optional.ofNullable(wamoSubStatus);
    }

    /**
     * Sets whether the viewer has muted the newsletter.
     *
     * @param mute {@code true} to mute the newsletter
     */
    public void setMute(boolean mute) {
        this.mute = mute;
    }

    /**
     * Sets the viewer's role within the newsletter.
     *
     * @param role the viewer role, defaults to {@link NewsletterViewerRole#UNKNOWN}
     *             if {@code null}
     */
    public void setRole(NewsletterViewerRole role) {
        this.role = Objects.requireNonNullElse(role, NewsletterViewerRole.UNKNOWN);
    }

    /**
     * Sets the viewer's WAMO subscription status.
     *
     * @param wamoSubStatus the WAMO sub status
     */
    public void setWamoSubStatus(NewsletterWamoSubStatus wamoSubStatus) {
        this.wamoSubStatus = wamoSubStatus;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NewsletterViewerMetadata that
                && mute == that.mute
                && role == that.role
                && wamoSubStatus == that.wamoSubStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mute, role, wamoSubStatus);
    }
}
