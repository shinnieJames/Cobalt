package com.github.auties00.cobalt.model.contact;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage
public final class ContactTextStatus {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String text;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String emoji;

    @ProtobufProperty(index = 3, type = ProtobufType.INT32)
    Integer ephemeralDurationSeconds;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT64)
    Long lastUpdateTimeSeconds;

    ContactTextStatus(String text, String emoji, Integer ephemeralDurationSeconds, Long lastUpdateTimeSeconds) {
        this.text = text;
        this.emoji = emoji;
        this.ephemeralDurationSeconds = ephemeralDurationSeconds;
        this.lastUpdateTimeSeconds = lastUpdateTimeSeconds;
    }

    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    public Optional<String> emoji() {
        return Optional.ofNullable(emoji);
    }

    public OptionalInt ephemeralDurationSeconds() {
        return ephemeralDurationSeconds == null ? OptionalInt.empty() : OptionalInt.of(ephemeralDurationSeconds);
    }

    public Optional<Instant> lastUpdateTime() {
        return lastUpdateTimeSeconds == null ? Optional.empty() : Optional.of(Instant.ofEpochSecond(lastUpdateTimeSeconds));
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public void setEphemeralDurationSeconds(Integer ephemeralDurationSeconds) {
        this.ephemeralDurationSeconds = ephemeralDurationSeconds;
    }

    public void setLastUpdateTime(Instant lastUpdateTime) {
        this.lastUpdateTimeSeconds = lastUpdateTime == null ? null : lastUpdateTime.getEpochSecond();
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof ContactTextStatus that
                && Objects.equals(text, that.text)
                && Objects.equals(emoji, that.emoji)
                && Objects.equals(ephemeralDurationSeconds, that.ephemeralDurationSeconds)
                && Objects.equals(lastUpdateTimeSeconds, that.lastUpdateTimeSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, emoji, ephemeralDurationSeconds, lastUpdateTimeSeconds);
    }
}
