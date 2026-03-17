package com.github.auties00.cobalt.model.message.commerce;

import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.InvoiceMessage")
public final class InvoiceMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String note;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String token;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    AttachmentType attachmentType;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String attachmentMimetype;

    @ProtobufProperty(index = 5, type = ProtobufType.BYTES)
    byte[] attachmentMediaKey;

    @ProtobufProperty(index = 6, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant attachmentMediaKeyTimestamp;

    @ProtobufProperty(index = 7, type = ProtobufType.BYTES)
    byte[] attachmentFileSha256;

    @ProtobufProperty(index = 8, type = ProtobufType.BYTES)
    byte[] attachmentFileEncSha256;

    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    String attachmentDirectPath;

    @ProtobufProperty(index = 10, type = ProtobufType.BYTES)
    byte[] attachmentJpegThumbnail;


    InvoiceMessage(String note, String token, AttachmentType attachmentType, String attachmentMimetype, byte[] attachmentMediaKey, Instant attachmentMediaKeyTimestamp, byte[] attachmentFileSha256, byte[] attachmentFileEncSha256, String attachmentDirectPath, byte[] attachmentJpegThumbnail) {
        this.note = note;
        this.token = token;
        this.attachmentType = attachmentType;
        this.attachmentMimetype = attachmentMimetype;
        this.attachmentMediaKey = attachmentMediaKey;
        this.attachmentMediaKeyTimestamp = attachmentMediaKeyTimestamp;
        this.attachmentFileSha256 = attachmentFileSha256;
        this.attachmentFileEncSha256 = attachmentFileEncSha256;
        this.attachmentDirectPath = attachmentDirectPath;
        this.attachmentJpegThumbnail = attachmentJpegThumbnail;
    }

    public Optional<String> note() {
        return Optional.ofNullable(note);
    }

    public Optional<String> token() {
        return Optional.ofNullable(token);
    }

    public Optional<AttachmentType> attachmentType() {
        return Optional.ofNullable(attachmentType);
    }

    public Optional<String> attachmentMimetype() {
        return Optional.ofNullable(attachmentMimetype);
    }

    public Optional<byte[]> attachmentMediaKey() {
        return Optional.ofNullable(attachmentMediaKey);
    }

    public Optional<Instant> attachmentMediaKeyTimestamp() {
        return Optional.ofNullable(attachmentMediaKeyTimestamp);
    }

    public Optional<byte[]> attachmentFileSha256() {
        return Optional.ofNullable(attachmentFileSha256);
    }

    public Optional<byte[]> attachmentFileEncSha256() {
        return Optional.ofNullable(attachmentFileEncSha256);
    }

    public Optional<String> attachmentDirectPath() {
        return Optional.ofNullable(attachmentDirectPath);
    }

    public Optional<byte[]> attachmentJpegThumbnail() {
        return Optional.ofNullable(attachmentJpegThumbnail);
    }

    public void setNote(String note) {
        this.note = note;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setAttachmentType(AttachmentType attachmentType) {
        this.attachmentType = attachmentType;
    }

    public void setAttachmentMimetype(String attachmentMimetype) {
        this.attachmentMimetype = attachmentMimetype;
    }

    public void setAttachmentMediaKey(byte[] attachmentMediaKey) {
        this.attachmentMediaKey = attachmentMediaKey;
    }

    public void setAttachmentMediaKeyTimestamp(Instant attachmentMediaKeyTimestamp) {
        this.attachmentMediaKeyTimestamp = attachmentMediaKeyTimestamp;
    }

    public void setAttachmentFileSha256(byte[] attachmentFileSha256) {
        this.attachmentFileSha256 = attachmentFileSha256;
    }

    public void setAttachmentFileEncSha256(byte[] attachmentFileEncSha256) {
        this.attachmentFileEncSha256 = attachmentFileEncSha256;
    }

    public void setAttachmentDirectPath(String attachmentDirectPath) {
        this.attachmentDirectPath = attachmentDirectPath;
    }

    public void setAttachmentJpegThumbnail(byte[] attachmentJpegThumbnail) {
        this.attachmentJpegThumbnail = attachmentJpegThumbnail;
    }

    @ProtobufEnum(name = "Message.InvoiceMessage.AttachmentType")
    public static enum AttachmentType {
        IMAGE(0),
        PDF(1);

        AttachmentType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
