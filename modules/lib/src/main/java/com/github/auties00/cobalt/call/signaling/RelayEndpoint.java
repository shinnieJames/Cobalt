package com.github.auties00.cobalt.call.signaling;

import java.util.Arrays;
import java.util.Objects;

/**
 * One {@code <te2>} child of a call ACK's {@code <relay>} block: a candidate relay endpoint the
 * caller can drive the Allocate handshake against.
 *
 * <p>Each physical relay candidate is announced twice (once for its IPv4 address, once for its
 * IPv6 address) sharing the same {@link #relayId()}; pair entries by {@code relayId} when
 * address-family preference matters. The captured content shape is 6 bytes (IPv4 + 2-byte port)
 * or 18 bytes (IPv6 + 2-byte port); WhatsApp's standard relay port is {@code 3478}.
 */
public final class RelayEndpoint {
    /**
     * The {@code auth_token_id} attribute, referencing one {@link AuthToken#id()}.
     */
    private final int authTokenId;

    /**
     * The {@code relay_id} attribute, the physical relay candidate identifier shared across
     * address families.
     */
    private final int relayId;

    /**
     * The {@code token_id} attribute, referencing one {@link RelayToken#id()}.
     */
    private final int tokenId;

    /**
     * The {@code domain_name} attribute, the DNS name the relay connector resolves, or
     * {@code null} when the relay block omits it (group-call offer {@code <te2>} entries carry only
     * {@code relay_name}).
     */
    private final String domainName;

    /**
     * The {@code relay_name} attribute, the short relay identifier (for example {@code "mxp1c01"}).
     */
    private final String relayName;

    /**
     * The {@code c2r_rtt} attribute, the server's last observed client-to-relay round-trip time.
     */
    private final int c2rRtt;

    /**
     * The encoded relay address bytes, the element's content. Defensive copy held internally.
     */
    private final byte[] bytes;

    /**
     * Constructs a {@code <te2>} entry.
     *
     * @param authTokenId the {@code auth_token_id} attribute
     * @param relayId     the {@code relay_id} attribute
     * @param tokenId     the {@code token_id} attribute
     * @param domainName  the {@code domain_name} attribute, or {@code null} when absent
     * @param relayName   the {@code relay_name} attribute; never {@code null}
     * @param c2rRtt      the {@code c2r_rtt} attribute
     * @param bytes       the encoded relay address bytes; never {@code null}
     * @throws NullPointerException if {@code relayName} or {@code bytes} is {@code null}
     */
    RelayEndpoint(int authTokenId, int relayId, int tokenId,
                  String domainName, String relayName, int c2rRtt,
                  byte[] bytes) {
        this.authTokenId = authTokenId;
        this.relayId = relayId;
        this.tokenId = tokenId;
        this.domainName = domainName;
        this.relayName = Objects.requireNonNull(relayName, "relayName cannot be null");
        this.c2rRtt = c2rRtt;
        this.bytes = Objects.requireNonNull(bytes, "bytes cannot be null").clone();
    }

    /**
     * Returns the {@code auth_token_id} attribute.
     *
     * @return the auth-token reference
     */
    public int authTokenId() {
        return authTokenId;
    }

    /**
     * Returns the {@code relay_id} attribute.
     *
     * @return the physical relay candidate identifier
     */
    public int relayId() {
        return relayId;
    }

    /**
     * Returns the {@code token_id} attribute.
     *
     * @return the relay-token reference
     */
    public int tokenId() {
        return tokenId;
    }

    /**
     * Returns the {@code domain_name} attribute.
     *
     * @return the DNS name, or {@code null} when the relay block omitted it
     */
    public String domainName() {
        return domainName;
    }

    /**
     * Returns the {@code relay_name} attribute.
     *
     * @return the short relay identifier; never {@code null}
     */
    public String relayName() {
        return relayName;
    }

    /**
     * Returns the {@code c2r_rtt} attribute.
     *
     * @return the client-to-relay round-trip time
     */
    public int c2rRtt() {
        return c2rRtt;
    }

    /**
     * Returns a defensive copy of the encoded relay address bytes.
     *
     * @return the bytes; never {@code null}
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof RelayEndpoint that
                && this.authTokenId == that.authTokenId
                && this.relayId == that.relayId
                && this.tokenId == that.tokenId
                && this.c2rRtt == that.c2rRtt
                && Objects.equals(this.domainName, that.domainName)
                && this.relayName.equals(that.relayName)
                && Arrays.equals(this.bytes, that.bytes));
    }

    @Override
    public int hashCode() {
        return Objects.hash(authTokenId, relayId, tokenId, domainName, relayName, c2rRtt,
                Arrays.hashCode(bytes));
    }

    @Override
    public String toString() {
        return "RelayEndpoint[authTokenId=" + authTokenId + ", relayId=" + relayId
                + ", tokenId=" + tokenId + ", domain=" + domainName
                + ", relay=" + relayName + ", c2rRtt=" + c2rRtt
                + ", len=" + bytes.length + ']';
    }
}
