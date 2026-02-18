package com.github.auties00.cobalt.model.message.location;

import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.message.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
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

    public LocationMessage setDegreesLatitude(Double degreesLatitude) {
        this.degreesLatitude = degreesLatitude;
        return this;
    }

    public LocationMessage setDegreesLongitude(Double degreesLongitude) {
        this.degreesLongitude = degreesLongitude;
        return this;
    }

    public LocationMessage setName(String name) {
        this.name = name;
        return this;
    }

    public LocationMessage setAddress(String address) {
        this.address = address;
        return this;
    }

    public LocationMessage setUrl(String url) {
        this.url = url;
        return this;
    }

    public LocationMessage setLive(Boolean isLive) {
        this.isLive = isLive;
        return this;
    }

    public LocationMessage setAccuracyInMeters(Integer accuracyInMeters) {
        this.accuracyInMeters = accuracyInMeters;
        return this;
    }

    public LocationMessage setSpeedInMps(Float speedInMps) {
        this.speedInMps = speedInMps;
        return this;
    }

    public LocationMessage setDegreesClockwiseFromMagneticNorth(Integer degreesClockwiseFromMagneticNorth) {
        this.degreesClockwiseFromMagneticNorth = degreesClockwiseFromMagneticNorth;
        return this;
    }

    public LocationMessage setComment(String comment) {
        this.comment = comment;
        return this;
    }

    public LocationMessage setJpegThumbnail(byte[] jpegThumbnail) {
        this.jpegThumbnail = jpegThumbnail;
        return this;
    }

    public LocationMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }
}
