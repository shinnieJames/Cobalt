package com.github.auties00.cobalt.call.signaling;

import java.util.Arrays;
import java.util.Objects;

/**
 * One {@code <auth_token id>bytes</auth_token>} child of a call ACK's {@code <relay>} block.
 *
 * <p>The auth-token bytes the call layer uses to derive ICE credentials for the relay handshake;
 * referenced by {@link RelayEndpoint#authTokenId()}. The captured payload is 70 bytes.
 */
public final class AuthToken {
    /**
     * The {@code id} attribute, an integer index referenced by the matching {@code <te2>} child.
     */
    private final int id;

    /**
     * The auth-token bytes, the element's content. Defensive copy held internally.
     */
    private final byte[] bytes;

    /**
     * Constructs an auth-token entry.
     *
     * @param id    the {@code id} attribute
     * @param bytes the auth-token bytes; never {@code null}
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    AuthToken(int id, byte[] bytes) {
        this.id = id;
        this.bytes = Objects.requireNonNull(bytes, "bytes cannot be null").clone();
    }

    /**
     * Returns the {@code id} attribute.
     *
     * @return the auth-token id
     */
    public int id() {
        return id;
    }

    /**
     * Returns a defensive copy of the auth-token bytes.
     *
     * @return the bytes; never {@code null}
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof AuthToken that
                && this.id == that.id
                && Arrays.equals(this.bytes, that.bytes));
    }

    @Override
    public int hashCode() {
        return 31 * id + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "AuthToken[id=" + id + ", len=" + bytes.length + ']';
    }
}
