package com.github.auties00.cobalt.model.message.location;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.LiveLocationMessage")
public final class LiveLocationMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.DOUBLE)
    Double degreesLatitude;

    @ProtobufProperty(index = 2, type = ProtobufType.DOUBLE)
    Double degreesLongitude;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
    Integer accuracyInMeters;

    @ProtobufProperty(index = 4, type = ProtobufType.FLOAT)
    Float speedInMps;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
    Integer degreesClockwiseFromMagneticNorth;

    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String caption;

    @ProtobufProperty(index = 7, type = ProtobufType.INT64)
    Long sequenceNumber;

    @ProtobufProperty(index = 8, type = ProtobufType.UINT32)
    Integer timeOffset;

    @ProtobufProperty(index = 16, type = ProtobufType.BYTES)
    byte[] jpegThumbnail;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;


    LiveLocationMessage(Double degreesLatitude, Double degreesLongitude, Integer accuracyInMeters, Float speedInMps, Integer degreesClockwiseFromMagneticNorth, String caption, Long sequenceNumber, Integer timeOffset, byte[] jpegThumbnail, ContextInfo contextInfo) {
        this.degreesLatitude = degreesLatitude;
        this.degreesLongitude = degreesLongitude;
        this.accuracyInMeters = accuracyInMeters;
        this.speedInMps = speedInMps;
        this.degreesClockwiseFromMagneticNorth = degreesClockwiseFromMagneticNorth;
        this.caption = caption;
        this.sequenceNumber = sequenceNumber;
        this.timeOffset = timeOffset;
        this.jpegThumbnail = jpegThumbnail;
        this.contextInfo = contextInfo;
    }

    public OptionalDouble degreesLatitude() {
        return degreesLatitude == null ? OptionalDouble.empty() : OptionalDouble.of(degreesLatitude);
    }

    public OptionalDouble degreesLongitude() {
        return degreesLongitude == null ? OptionalDouble.empty() : OptionalDouble.of(degreesLongitude);
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

    public Optional<String> caption() {
        return Optional.ofNullable(caption);
    }

    public OptionalLong sequenceNumber() {
        return sequenceNumber == null ? OptionalLong.empty() : OptionalLong.of(sequenceNumber);
    }

    public OptionalInt timeOffset() {
        return timeOffset == null ? OptionalInt.empty() : OptionalInt.of(timeOffset);
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

    public void setAccuracyInMeters(Integer accuracyInMeters) {
        this.accuracyInMeters = accuracyInMeters;
    }

    public void setSpeedInMps(Float speedInMps) {
        this.speedInMps = speedInMps;
    }

    public void setDegreesClockwiseFromMagneticNorth(Integer degreesClockwiseFromMagneticNorth) {
        this.degreesClockwiseFromMagneticNorth = degreesClockwiseFromMagneticNorth;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void setTimeOffset(Integer timeOffset) {
        this.timeOffset = timeOffset;
    }

    public void setJpegThumbnail(byte[] jpegThumbnail) {
        this.jpegThumbnail = jpegThumbnail;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }
}
