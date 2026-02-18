package com.github.auties00.cobalt.model.device.sync;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Represents a pending device sync request that needs to be completed.
 * Used to persist failed device fetches for retry on reconnect.
 *
 */
@ProtobufMessage
public final class PendingDeviceSync implements Serializable {
    private static final int MAX_RETRIES = 3;
    private static final Duration EXPIRY_DURATION = Duration.ofHours(24);

    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final List<Jid> userJids;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    final String context;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    final Instant timestamp;

    @ProtobufProperty(index = 4, type = ProtobufType.INT32)
    final int retryCount;

    PendingDeviceSync(List<Jid> userJids, String context, Instant timestamp, int retryCount) {
        this.userJids = userJids;
        this.context = context;
        this.timestamp = timestamp;
        this.retryCount = retryCount;
    }

    /**
     * Creates a new pending device sync request.
     *
     * @param userJids the user JIDs to sync
     * @param context  the sync context
     * @return new pending sync
     */
    public static PendingDeviceSync of(Collection<Jid> userJids, String context) {
        return new PendingDeviceSync(List.copyOf(userJids), context, Instant.now(), 0);
    }

    /**
     * Creates a new pending sync with incremented retry count.
     *
     * @return pending sync with retry count incremented
     */
    public PendingDeviceSync nextRetry() {
        return new PendingDeviceSync(userJids, context, timestamp, retryCount + 1);
    }

    /**
     * Checks if this sync should be retried.
     *
     * @return true if retry count is below maximum
     */
    public boolean shouldRetry() {
        return retryCount < MAX_RETRIES;
    }

    /**
     * Checks if this sync has expired.
     *
     * @return true if older than 24 hours
     */
    public boolean isExpired() {
        return Duration.between(timestamp, Instant.now())
                       .compareTo(EXPIRY_DURATION) >= 0;
    }

    public List<Jid> userJids() {
        return userJids;
    }

    public String context() {
        return context;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public int retryCount() {
        return retryCount;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof PendingDeviceSync that
                            && retryCount == that.retryCount
                            && Objects.equals(userJids, that.userJids)
                            && Objects.equals(context, that.context)
                            && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userJids, context, timestamp, retryCount);
    }

    @Override
    public String toString() {
        return "PendingDeviceSync[" +
               "userJids=" + userJids + ", " +
               "context=" + context + ", " +
               "timestamp=" + timestamp + ", " +
               "retryCount=" + retryCount + ']';
    }

}
