package com.github.auties00.cobalt.model.message.text;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.media.EmbeddedMusic;
import com.github.auties00.cobalt.model.message.payment.PaymentExtendedMetadata;
import com.github.auties00.cobalt.model.message.payment.PaymentLinkMetadata;
import com.github.auties00.cobalt.model.message.media.MessageLinkPreviewMetadata;
import com.github.auties00.cobalt.model.message.media.MessageMMSThumbnailMetadata;
import com.github.auties00.cobalt.model.message.media.MessageVideoEndCard;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.ExtendedTextMessage")
public final class ExtendedTextMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String text;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String matchedText;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String description;

    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String title;

    @ProtobufProperty(index = 7, type = ProtobufType.FIXED32)
    Integer textArgb;

    @ProtobufProperty(index = 8, type = ProtobufType.FIXED32)
    Integer backgroundArgb;

    @ProtobufProperty(index = 9, type = ProtobufType.ENUM)
    FontType font;

    @ProtobufProperty(index = 10, type = ProtobufType.ENUM)
    PreviewType previewType;

    @ProtobufProperty(index = 16, type = ProtobufType.BYTES)
    byte[] jpegThumbnail;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 18, type = ProtobufType.BOOL)
    Boolean doNotPlayInline;

    @ProtobufProperty(index = 19, type = ProtobufType.STRING)
    String thumbnailDirectPath;

    @ProtobufProperty(index = 20, type = ProtobufType.BYTES)
    byte[] thumbnailSha256;

    @ProtobufProperty(index = 21, type = ProtobufType.BYTES)
    byte[] thumbnailEncSha256;

    @ProtobufProperty(index = 22, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 23, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant mediaKeyTimestamp;

    @ProtobufProperty(index = 24, type = ProtobufType.UINT32)
    Integer thumbnailHeight;

    @ProtobufProperty(index = 25, type = ProtobufType.UINT32)
    Integer thumbnailWidth;

    @ProtobufProperty(index = 26, type = ProtobufType.ENUM)
    InviteLinkGroupType inviteLinkGroupType;

    @ProtobufProperty(index = 27, type = ProtobufType.STRING)
    String inviteLinkParentGroupSubjectV2;

    @ProtobufProperty(index = 28, type = ProtobufType.BYTES)
    byte[] inviteLinkParentGroupThumbnailV2;

    @ProtobufProperty(index = 29, type = ProtobufType.ENUM)
    InviteLinkGroupType inviteLinkGroupTypeV2;

    @ProtobufProperty(index = 30, type = ProtobufType.BOOL)
    Boolean viewOnce;

    @ProtobufProperty(index = 31, type = ProtobufType.UINT32)
    Integer videoHeight;

    @ProtobufProperty(index = 32, type = ProtobufType.UINT32)
    Integer videoWidth;

    @ProtobufProperty(index = 33, type = ProtobufType.MESSAGE)
    MessageMMSThumbnailMetadata faviconMMSMetadata;

    @ProtobufProperty(index = 34, type = ProtobufType.MESSAGE)
    MessageLinkPreviewMetadata linkPreviewMetadata;

    @ProtobufProperty(index = 35, type = ProtobufType.MESSAGE)
    PaymentLinkMetadata paymentLinkMetadata;

    @ProtobufProperty(index = 36, type = ProtobufType.MESSAGE)
    List<MessageVideoEndCard> endCardTiles;

    @ProtobufProperty(index = 37, type = ProtobufType.STRING)
    String videoContentUrl;

    @ProtobufProperty(index = 38, type = ProtobufType.MESSAGE)
    EmbeddedMusic musicMetadata;

    @ProtobufProperty(index = 39, type = ProtobufType.MESSAGE)
    PaymentExtendedMetadata paymentExtendedMetadata;


    ExtendedTextMessage(String text, String matchedText, String description, String title, Integer textArgb, Integer backgroundArgb, FontType font, PreviewType previewType, byte[] jpegThumbnail, ContextInfo contextInfo, Boolean doNotPlayInline, String thumbnailDirectPath, byte[] thumbnailSha256, byte[] thumbnailEncSha256, byte[] mediaKey, Instant mediaKeyTimestamp, Integer thumbnailHeight, Integer thumbnailWidth, InviteLinkGroupType inviteLinkGroupType, String inviteLinkParentGroupSubjectV2, byte[] inviteLinkParentGroupThumbnailV2, InviteLinkGroupType inviteLinkGroupTypeV2, Boolean viewOnce, Integer videoHeight, Integer videoWidth, MessageMMSThumbnailMetadata faviconMMSMetadata, MessageLinkPreviewMetadata linkPreviewMetadata, PaymentLinkMetadata paymentLinkMetadata, List<MessageVideoEndCard> endCardTiles, String videoContentUrl, EmbeddedMusic musicMetadata, PaymentExtendedMetadata paymentExtendedMetadata) {
        this.text = text;
        this.matchedText = matchedText;
        this.description = description;
        this.title = title;
        this.textArgb = textArgb;
        this.backgroundArgb = backgroundArgb;
        this.font = font;
        this.previewType = previewType;
        this.jpegThumbnail = jpegThumbnail;
        this.contextInfo = contextInfo;
        this.doNotPlayInline = doNotPlayInline;
        this.thumbnailDirectPath = thumbnailDirectPath;
        this.thumbnailSha256 = thumbnailSha256;
        this.thumbnailEncSha256 = thumbnailEncSha256;
        this.mediaKey = mediaKey;
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        this.thumbnailHeight = thumbnailHeight;
        this.thumbnailWidth = thumbnailWidth;
        this.inviteLinkGroupType = inviteLinkGroupType;
        this.inviteLinkParentGroupSubjectV2 = inviteLinkParentGroupSubjectV2;
        this.inviteLinkParentGroupThumbnailV2 = inviteLinkParentGroupThumbnailV2;
        this.inviteLinkGroupTypeV2 = inviteLinkGroupTypeV2;
        this.viewOnce = viewOnce;
        this.videoHeight = videoHeight;
        this.videoWidth = videoWidth;
        this.faviconMMSMetadata = faviconMMSMetadata;
        this.linkPreviewMetadata = linkPreviewMetadata;
        this.paymentLinkMetadata = paymentLinkMetadata;
        this.endCardTiles = endCardTiles;
        this.videoContentUrl = videoContentUrl;
        this.musicMetadata = musicMetadata;
        this.paymentExtendedMetadata = paymentExtendedMetadata;
    }

    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    public Optional<String> matchedText() {
        return Optional.ofNullable(matchedText);
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    public OptionalInt textArgb() {
        return textArgb == null ? OptionalInt.empty() : OptionalInt.of(textArgb);
    }

    public OptionalInt backgroundArgb() {
        return backgroundArgb == null ? OptionalInt.empty() : OptionalInt.of(backgroundArgb);
    }

    public Optional<FontType> font() {
        return Optional.ofNullable(font);
    }

    public Optional<PreviewType> previewType() {
        return Optional.ofNullable(previewType);
    }

    public Optional<byte[]> jpegThumbnail() {
        return Optional.ofNullable(jpegThumbnail);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public boolean doNotPlayInline() {
        return doNotPlayInline != null && doNotPlayInline;
    }

    public Optional<String> thumbnailDirectPath() {
        return Optional.ofNullable(thumbnailDirectPath);
    }

    public Optional<byte[]> thumbnailSha256() {
        return Optional.ofNullable(thumbnailSha256);
    }

    public Optional<byte[]> thumbnailEncSha256() {
        return Optional.ofNullable(thumbnailEncSha256);
    }

    public Optional<byte[]> mediaKey() {
        return Optional.ofNullable(mediaKey);
    }

    public Optional<Instant> mediaKeyTimestamp() {
        return Optional.ofNullable(mediaKeyTimestamp);
    }

    public OptionalInt thumbnailHeight() {
        return thumbnailHeight == null ? OptionalInt.empty() : OptionalInt.of(thumbnailHeight);
    }

    public OptionalInt thumbnailWidth() {
        return thumbnailWidth == null ? OptionalInt.empty() : OptionalInt.of(thumbnailWidth);
    }

    public Optional<InviteLinkGroupType> inviteLinkGroupType() {
        return Optional.ofNullable(inviteLinkGroupType);
    }

    public Optional<String> inviteLinkParentGroupSubjectV2() {
        return Optional.ofNullable(inviteLinkParentGroupSubjectV2);
    }

    public Optional<byte[]> inviteLinkParentGroupThumbnailV2() {
        return Optional.ofNullable(inviteLinkParentGroupThumbnailV2);
    }

    public Optional<InviteLinkGroupType> inviteLinkGroupTypeV2() {
        return Optional.ofNullable(inviteLinkGroupTypeV2);
    }

    public boolean viewOnce() {
        return viewOnce != null && viewOnce;
    }

    public OptionalInt videoHeight() {
        return videoHeight == null ? OptionalInt.empty() : OptionalInt.of(videoHeight);
    }

    public OptionalInt videoWidth() {
        return videoWidth == null ? OptionalInt.empty() : OptionalInt.of(videoWidth);
    }

    public Optional<MessageMMSThumbnailMetadata> faviconMMSMetadata() {
        return Optional.ofNullable(faviconMMSMetadata);
    }

    public Optional<MessageLinkPreviewMetadata> linkPreviewMetadata() {
        return Optional.ofNullable(linkPreviewMetadata);
    }

    public Optional<PaymentLinkMetadata> paymentLinkMetadata() {
        return Optional.ofNullable(paymentLinkMetadata);
    }

    public List<MessageVideoEndCard> endCardTiles() {
        return endCardTiles == null ? List.of() : Collections.unmodifiableList(endCardTiles);
    }

    public Optional<String> videoContentUrl() {
        return Optional.ofNullable(videoContentUrl);
    }

    public Optional<EmbeddedMusic> musicMetadata() {
        return Optional.ofNullable(musicMetadata);
    }

    public Optional<PaymentExtendedMetadata> paymentExtendedMetadata() {
        return Optional.ofNullable(paymentExtendedMetadata);
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setMatchedText(String matchedText) {
        this.matchedText = matchedText;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setTextArgb(Integer textArgb) {
        this.textArgb = textArgb;
    }

    public void setBackgroundArgb(Integer backgroundArgb) {
        this.backgroundArgb = backgroundArgb;
    }

    public void setFont(FontType font) {
        this.font = font;
    }

    public void setPreviewType(PreviewType previewType) {
        this.previewType = previewType;
    }

    public void setJpegThumbnail(byte[] jpegThumbnail) {
        this.jpegThumbnail = jpegThumbnail;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setDoNotPlayInline(Boolean doNotPlayInline) {
        this.doNotPlayInline = doNotPlayInline;
    }

    public void setThumbnailDirectPath(String thumbnailDirectPath) {
        this.thumbnailDirectPath = thumbnailDirectPath;
    }

    public void setThumbnailSha256(byte[] thumbnailSha256) {
        this.thumbnailSha256 = thumbnailSha256;
    }

    public void setThumbnailEncSha256(byte[] thumbnailEncSha256) {
        this.thumbnailEncSha256 = thumbnailEncSha256;
    }

    public void setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
    }

    public void setMediaKeyTimestamp(Instant mediaKeyTimestamp) {
        this.mediaKeyTimestamp = mediaKeyTimestamp;
    }

    public void setThumbnailHeight(Integer thumbnailHeight) {
        this.thumbnailHeight = thumbnailHeight;
    }

    public void setThumbnailWidth(Integer thumbnailWidth) {
        this.thumbnailWidth = thumbnailWidth;
    }

    public void setInviteLinkGroupType(InviteLinkGroupType inviteLinkGroupType) {
        this.inviteLinkGroupType = inviteLinkGroupType;
    }

    public void setInviteLinkParentGroupSubjectV2(String inviteLinkParentGroupSubjectV2) {
        this.inviteLinkParentGroupSubjectV2 = inviteLinkParentGroupSubjectV2;
    }

    public void setInviteLinkParentGroupThumbnailV2(byte[] inviteLinkParentGroupThumbnailV2) {
        this.inviteLinkParentGroupThumbnailV2 = inviteLinkParentGroupThumbnailV2;
    }

    public void setInviteLinkGroupTypeV2(InviteLinkGroupType inviteLinkGroupTypeV2) {
        this.inviteLinkGroupTypeV2 = inviteLinkGroupTypeV2;
    }

    public void setViewOnce(Boolean viewOnce) {
        this.viewOnce = viewOnce;
    }

    public void setVideoHeight(Integer videoHeight) {
        this.videoHeight = videoHeight;
    }

    public void setVideoWidth(Integer videoWidth) {
        this.videoWidth = videoWidth;
    }

    public void setFaviconMMSMetadata(MessageMMSThumbnailMetadata faviconMMSMetadata) {
        this.faviconMMSMetadata = faviconMMSMetadata;
    }

    public void setLinkPreviewMetadata(MessageLinkPreviewMetadata linkPreviewMetadata) {
        this.linkPreviewMetadata = linkPreviewMetadata;
    }

    public void setPaymentLinkMetadata(PaymentLinkMetadata paymentLinkMetadata) {
        this.paymentLinkMetadata = paymentLinkMetadata;
    }

    public void setEndCardTiles(List<MessageVideoEndCard> endCardTiles) {
        this.endCardTiles = endCardTiles;
    }

    public void setVideoContentUrl(String videoContentUrl) {
        this.videoContentUrl = videoContentUrl;
    }

    public void setMusicMetadata(EmbeddedMusic musicMetadata) {
        this.musicMetadata = musicMetadata;
    }

    public void setPaymentExtendedMetadata(PaymentExtendedMetadata paymentExtendedMetadata) {
        this.paymentExtendedMetadata = paymentExtendedMetadata;
    }

    @ProtobufEnum(name = "Message.ExtendedTextMessage.FontType")
    public static enum FontType {
        SYSTEM(0),
        SYSTEM_TEXT(1),
        FB_SCRIPT(2),
        SYSTEM_BOLD(6),
        MORNINGBREEZE_REGULAR(7),
        CALISTOGA_REGULAR(8),
        EXO2_EXTRABOLD(9),
        COURIERPRIME_BOLD(10);

        FontType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "Message.ExtendedTextMessage.InviteLinkGroupType")
    public static enum InviteLinkGroupType {
        DEFAULT(0),
        PARENT(1),
        SUB(2),
        DEFAULT_SUB(3);

        InviteLinkGroupType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "Message.ExtendedTextMessage.PreviewType")
    public static enum PreviewType {
        NONE(0),
        VIDEO(1),
        PLACEHOLDER(4),
        IMAGE(5),
        PAYMENT_LINKS(6),
        PROFILE(7);

        PreviewType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
