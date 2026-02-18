package com.github.auties00.cobalt.model.device.info;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Hash information for a device list, used in USync queries to enable delta updates.
 */
@ProtobufMessage
public final class DeviceListHashInfo {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final String hash;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    final Instant timestamp;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    final Instant expectedTimestamp;

    DeviceListHashInfo(String hash, Instant timestamp, Instant expectedTimestamp) {
        this.hash = hash;
        this.timestamp = timestamp;
        this.expectedTimestamp = expectedTimestamp;
    }

    public String hash() {
        return hash;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public Optional<Instant> expectedTimestamp() {
        return Optional.ofNullable(expectedTimestamp);
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof DeviceListHashInfo that
                            && Objects.equals(hash, that.hash)
                            && Objects.equals(timestamp, that.timestamp)
                            && Objects.equals(expectedTimestamp, that.expectedTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, timestamp, expectedTimestamp);
    }

    @Override
    public String toString() {
        return "DeviceListHashInfo[" +
               "hash=" + hash + ", " +
               "timestamp=" + timestamp + ", " +
               "expectedTimestamp=" + expectedTimestamp + ']';
    }
}
