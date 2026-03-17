package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.payment.PaymentLinkMetadata;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.LinkPreviewMetadata")
public final class MessageLinkPreviewMetadata implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    PaymentLinkMetadata paymentLinkMetadata;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageURLMetadata urlMetadata;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
    Integer fbExperimentId;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
    Integer linkMediaDuration;

    @ProtobufProperty(index = 5, type = ProtobufType.ENUM)
    SocialMediaPostType socialMediaPostType;

    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    Boolean linkInlineVideoMuted;

    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    String videoContentUrl;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    EmbeddedMusic musicMetadata;

    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    String videoContentCaption;


    MessageLinkPreviewMetadata(PaymentLinkMetadata paymentLinkMetadata, MessageURLMetadata urlMetadata, Integer fbExperimentId, Integer linkMediaDuration, SocialMediaPostType socialMediaPostType, Boolean linkInlineVideoMuted, String videoContentUrl, EmbeddedMusic musicMetadata, String videoContentCaption) {
        this.paymentLinkMetadata = paymentLinkMetadata;
        this.urlMetadata = urlMetadata;
        this.fbExperimentId = fbExperimentId;
        this.linkMediaDuration = linkMediaDuration;
        this.socialMediaPostType = socialMediaPostType;
        this.linkInlineVideoMuted = linkInlineVideoMuted;
        this.videoContentUrl = videoContentUrl;
        this.musicMetadata = musicMetadata;
        this.videoContentCaption = videoContentCaption;
    }

    public Optional<PaymentLinkMetadata> paymentLinkMetadata() {
        return Optional.ofNullable(paymentLinkMetadata);
    }

    public Optional<MessageURLMetadata> urlMetadata() {
        return Optional.ofNullable(urlMetadata);
    }

    public OptionalInt fbExperimentId() {
        return fbExperimentId == null ? OptionalInt.empty() : OptionalInt.of(fbExperimentId);
    }

    public OptionalInt linkMediaDuration() {
        return linkMediaDuration == null ? OptionalInt.empty() : OptionalInt.of(linkMediaDuration);
    }

    public Optional<SocialMediaPostType> socialMediaPostType() {
        return Optional.ofNullable(socialMediaPostType);
    }

    public boolean linkInlineVideoMuted() {
        return linkInlineVideoMuted != null && linkInlineVideoMuted;
    }

    public Optional<String> videoContentUrl() {
        return Optional.ofNullable(videoContentUrl);
    }

    public Optional<EmbeddedMusic> musicMetadata() {
        return Optional.ofNullable(musicMetadata);
    }

    public Optional<String> videoContentCaption() {
        return Optional.ofNullable(videoContentCaption);
    }

    public void setPaymentLinkMetadata(PaymentLinkMetadata paymentLinkMetadata) {
        this.paymentLinkMetadata = paymentLinkMetadata;
    }

    public void setUrlMetadata(MessageURLMetadata urlMetadata) {
        this.urlMetadata = urlMetadata;
    }

    public void setFbExperimentId(Integer fbExperimentId) {
        this.fbExperimentId = fbExperimentId;
    }

    public void setLinkMediaDuration(Integer linkMediaDuration) {
        this.linkMediaDuration = linkMediaDuration;
    }

    public void setSocialMediaPostType(SocialMediaPostType socialMediaPostType) {
        this.socialMediaPostType = socialMediaPostType;
    }

    public void setLinkInlineVideoMuted(Boolean linkInlineVideoMuted) {
        this.linkInlineVideoMuted = linkInlineVideoMuted;
    }

    public void setVideoContentUrl(String videoContentUrl) {
        this.videoContentUrl = videoContentUrl;
    }

    public void setMusicMetadata(EmbeddedMusic musicMetadata) {
        this.musicMetadata = musicMetadata;
    }

    public void setVideoContentCaption(String videoContentCaption) {
        this.videoContentCaption = videoContentCaption;
    }

    @ProtobufEnum(name = "Message.LinkPreviewMetadata.SocialMediaPostType")
    public static enum SocialMediaPostType {
        NONE(0),
        REEL(1),
        LIVE_VIDEO(2),
        LONG_VIDEO(3),
        SINGLE_IMAGE(4),
        CAROUSEL(5);

        SocialMediaPostType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
