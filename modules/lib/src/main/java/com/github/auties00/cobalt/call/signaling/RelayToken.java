package com.github.auties00.cobalt.call.signaling;

import java.util.Arrays;
import java.util.Objects;

/**
 * One {@code <token id>bytes</token>} child of a call ACK's {@code <relay>} block.
 *
 * <p>An opaque server-issued token identifying a relay candidate; referenced by
 * {@link RelayEndpoint#tokenId()}. Three tokens are typically issued per call (one per relay
 * candidate); the bytes are 182 bytes on captured snapshots.
 */
public final class RelayToken {
    /**
     * The {@code id} attribute, an integer index referenced by the matching {@code <te2>} child.
     */
    private final int id;

    /**
     * The token bytes, the element's content. Defensive copy held internally.
     */
    private final byte[] bytes;

    /**
     * Constructs a token entry.
     *
     * @param id    the {@code id} attribute
     * @param bytes the token bytes; never {@code null}
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    RelayToken(int id, byte[] bytes) {
        this.id = id;
        this.bytes = Objects.requireNonNull(bytes, "bytes cannot be null").clone();
    }

    /**
     * Returns the {@code id} attribute.
     *
     * @return the token id
     */
    public int id() {
        return id;
    }

    /**
     * Returns a defensive copy of the token bytes.
     *
     * @return the bytes; never {@code null}
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof RelayToken that
                && this.id == that.id
                && Arrays.equals(this.bytes, that.bytes));
    }

    @Override
    public int hashCode() {
        return 31 * id + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "RelayToken[id=" + id + ", len=" + bytes.length + ']';
    }
}
