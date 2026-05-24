package com.github.auties00.cobalt.call.internal.rtp.srtp;

/**
 * Identifies which side of a DTLS-SRTP association we are. Determines
 * which half of the 60-byte exported keying material (RFC 5764 §4.2)
 * is the outbound key+salt and which is the inbound one.
 */
public enum SrtpRole {
    /**
     * The DTLS client. Outbound traffic is keyed with the
     * {@code client_write} master key + salt; inbound traffic is
     * keyed with the {@code server_write} pair.
     */
    CLIENT,

    /**
     * The DTLS server. Outbound traffic is keyed with the
     * {@code server_write} master key + salt; inbound traffic is
     * keyed with the {@code client_write} pair.
     */
    SERVER
}
