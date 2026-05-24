package com.github.auties00.cobalt.call.internal.transport.ice;

import java.util.Objects;

/**
 * The local and remote ufrag/password pairs an {@link IceAgent} uses
 * to drive STUN binding requests per RFC 8445 §7.2.2 (USERNAME =
 * {@code remoteUfrag:localUfrag}, MESSAGE-INTEGRITY keyed on
 * {@code remotePassword}).
 *
 * <p>For WhatsApp's relay-mediated path the {@code localPassword} is
 * actually the {@code relay_key} from {@code RelayListUpdate} —
 * {@link com.github.auties00.cobalt.call.internal.transport.relay.WaRelayMessageIntegrity}
 * documents the (non-RFC) keying derivation.
 *
 * @param localUfrag     the local user fragment (random 4..256 bytes
 *                       per RFC 8445 §5.3); peer's USERNAME prefixes
 *                       this when sending to us
 * @param localPassword  the local password — used to verify
 *                       MESSAGE-INTEGRITY on incoming binding
 *                       requests
 * @param remoteUfrag    the remote user fragment, learned from the
 *                       peer's offer/answer
 * @param remotePassword the remote password — used to compute
 *                       MESSAGE-INTEGRITY on outgoing binding
 *                       requests
 */
public record IceCredentials(
        String localUfrag,
        byte[] localPassword,
        String remoteUfrag,
        byte[] remotePassword
) {
    /**
     * Compact constructor — null-checks fields.
     */
    public IceCredentials {
        Objects.requireNonNull(localUfrag, "localUfrag cannot be null");
        Objects.requireNonNull(localPassword, "localPassword cannot be null");
        Objects.requireNonNull(remoteUfrag, "remoteUfrag cannot be null");
        Objects.requireNonNull(remotePassword, "remotePassword cannot be null");
        if (localUfrag.isEmpty()) {
            throw new IllegalArgumentException("localUfrag cannot be empty");
        }
        if (remoteUfrag.isEmpty()) {
            throw new IllegalArgumentException("remoteUfrag cannot be empty");
        }
    }

    /**
     * Returns the USERNAME attribute the agent stamps into outbound
     * binding requests per RFC 8445 §7.2.2 — concatenation of
     * {@code remoteUfrag} and {@code localUfrag} separated by
     * {@code ':'}.
     *
     * @return the USERNAME string
     */
    public String outboundUsername() {
        return remoteUfrag + ":" + localUfrag;
    }
}
