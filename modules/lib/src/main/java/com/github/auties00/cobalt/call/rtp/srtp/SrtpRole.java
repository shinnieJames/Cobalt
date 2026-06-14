package com.github.auties00.cobalt.call.rtp.srtp;

/**
 * Identifies which side of a DTLS-SRTP association the local peer occupies.
 *
 * <p>The role selects which half of the 60-byte exported keying material (RFC 5764 section 4.2)
 * is treated as the outbound (sending) key and salt and which half is treated as the inbound
 * (receiving) key and salt. The DTLS handshake exports keying material as
 * {@code client_write_*} followed by {@code server_write_*}; the local role decides which of
 * those two pairs the local endpoint writes with.
 */
public enum SrtpRole {
    /**
     * Marks the local peer as the DTLS client.
     *
     * <p>Outbound traffic is keyed with the {@code client_write} master key and salt; inbound
     * traffic is keyed with the {@code server_write} master key and salt.
     */
    CLIENT,

    /**
     * Marks the local peer as the DTLS server.
     *
     * <p>Outbound traffic is keyed with the {@code server_write} master key and salt; inbound
     * traffic is keyed with the {@code client_write} master key and salt.
     */
    SERVER
}
