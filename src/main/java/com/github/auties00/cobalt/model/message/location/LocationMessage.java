package com.github.auties00.cobalt.model.message.location;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveHeader;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
import com.github.auties00.cobalt.model.message.interactive.TemplateMessage;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.LocationMessage")
public final class LocationMessage implements InteractiveHeader, InteractiveMessage.MediaSpec, TemplateMessage.Title, TemplateMessage.TitleSpec, ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.DOUBLE)
    Double degreesLatitude;

    @ProtobufProperty(index = 2, type = ProtobufType.DOUBLE)
    Double degreesLongitude;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String address;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String url;

    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    Boolean isLive;

    @ProtobufProperty(index = 7, type = ProtobufType.UINT32)
    Integer accuracyInMeters;

    @ProtobufProperty(index = 8, type = ProtobufType.FLOAT)
    Float speedInMps;

    @ProtobufProperty(index = 9, type = ProtobufType.UINT32)
    Integer degreesClockwiseFromMagneticNorth;

    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    String comment;

    @ProtobufProperty(index = 16, type = ProtobufType.BYTES)
    byte[] jpegThumbnail;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;


    LocationMessage(Double degreesLatitude, Double degreesLongitude, String name, String address, String url, Boolean isLive, Integer accuracyInMeters, Float speedInMps, Integer degreesClockwiseFromMagneticNorth, String comment, byte[] jpegThumbnail, ContextInfo contextInfo) {
        this.degreesLatitude = degreesLatitude;
        this.degreesLongitude = degreesLongitude;
        this.name = name;
        this.address = address;
        this.url = url;
        this.isLive = isLive;
        this.accuracyInMeters = accuracyInMeters;
        this.speedInMps = speedInMps;
        this.degreesClockwiseFromMagneticNorth = degreesClockwiseFromMagneticNorth;
        this.comment = comment;
        this.jpegThumbnail = jpegThumbnail;
        this.contextInfo = contextInfo;
    }

    public OptionalDouble degreesLatitude() {
        return degreesLatitude == null ? OptionalDouble.empty() : OptionalDouble.of(degreesLatitude);
    }

    public OptionalDouble degreesLongitude() {
        return degreesLongitude == null ? OptionalDouble.empty() : OptionalDouble.of(degreesLongitude);
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<String> address() {
        return Optional.ofNullable(address);
    }

    public Optional<String> url() {
        return Optional.ofNullable(url);
    }

    public boolean isLive() {
        return isLive != null && isLive;
    }

    public OptionalInt accuracyInMeters() {
        return accuracyInMeters == null ? OptionalInt.empty() : OptionalInt.of(accuracyInMeters);
    }

    public OptionalDouble speedInMps() {
        return speedInMps == null ? OptionalDouble.empty() : OptionalDouble.of(speedInMps);
    }

    public OptionalInt degreesClockwiseFromMagneticNorth() {
        return degreesClockwiseFromMagneticNorth == null ? OptionalInt.empty() : OptionalInt.of(degreesClockwiseFromMagneticNorth);
    }

    public Optional<String> comment() {
        return Optional.ofNullable(comment);
    }

    public Optional<byte[]> jpegThumbnail() {
        return Optional.ofNullable(jpegThumbnail);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public void setDegreesLatitude(Double degreesLatitude) {
        this.degreesLatitude = degreesLatitude;
    }

    public void setDegreesLongitude(Double degreesLongitude) {
        this.degreesLongitude = degreesLongitude;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setLive(Boolean isLive) {
        this.isLive = isLive;
    }

    public void setAccuracyInMeters(Integer accuracyInMeters) {
        this.accuracyInMeters = accuracyInMeters;
    }

    public void setSpeedInMps(Float speedInMps) {
        this.speedInMps = speedInMps;
    }

    public void setDegreesClockwiseFromMagneticNorth(Integer degreesClockwiseFromMagneticNorth) {
        this.degreesClockwiseFromMagneticNorth = degreesClockwiseFromMagneticNorth;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setJpegThumbnail(byte[] jpegThumbnail) {
        this.jpegThumbnail = jpegThumbnail;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }
}
