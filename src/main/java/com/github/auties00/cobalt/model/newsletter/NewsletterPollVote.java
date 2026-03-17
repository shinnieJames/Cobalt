package com.github.auties00.cobalt.model.newsletter;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Arrays;
import java.util.Objects;

/**
 * A poll vote entry in a newsletter message, containing the vote option
 * hash and the number of votes it received.
 *
 * <p>The vote hash is a 32-byte identifier corresponding to a specific
 * poll option. The count represents the total number of votes for that
 * option.
 */
@ProtobufMessage
public final class NewsletterPollVote {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] hash;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    long count;

    /**
     * Constructs a new {@code NewsletterPollVote} with the specified option
     * hash and vote count.
     *
     * @param hash  the 32-byte poll option hash
     * @param count the number of votes for this option
     */
    public NewsletterPollVote(byte[] hash, long count) {
        this.hash = Objects.requireNonNull(hash, "hash cannot be null");
        this.count = count;
    }

    /**
     * Returns the 32-byte poll option hash.
     *
     * @return the option hash, never {@code null}
     */
    public byte[] hash() {
        return hash;
    }

    /**
     * Returns the number of votes for this option.
     *
     * @return the vote count
     */
    public long count() {
        return count;
    }

    /**
     * Sets the poll option hash.
     *
     * @param hash the 32-byte option hash, must not be {@code null}
     * @throws NullPointerException if {@code hash} is {@code null}
     */
    public void setHash(byte[] hash) {
        this.hash = Objects.requireNonNull(hash, "hash cannot be null");
    }

    /**
     * Sets the vote count for this option.
     *
     * @param count the vote count
     */
    public void setCount(long count) {
        this.count = count;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NewsletterPollVote that
                && Arrays.equals(hash, that.hash)
                && count == that.count;
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(hash) + Long.hashCode(count);
    }

    @Override
    public String toString() {
        return "NewsletterPollVote[" +
                "hash=" + Arrays.toString(hash) +
                ", count=" + count +
                ']';
    }
}
