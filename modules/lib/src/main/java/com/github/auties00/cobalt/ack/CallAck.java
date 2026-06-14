package com.github.auties00.cobalt.ack;

import com.github.auties00.cobalt.call.signaling.CallRelay;
import com.github.auties00.cobalt.model.jid.Jid;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@link AckResult} variant returned for {@code <ack class="call">} stanzas.
 *
 * <p>Call ACKs confirm that the server processed an outbound call-signaling stanza. On a
 * {@code type="offer"} ACK the body always contains one {@code <relay>} child; on success it is
 * fully populated with the {@link CallRelay#tokens() tokens}, {@link CallRelay#authTokens() auth
 * tokens}, {@link CallRelay#endpoints() te2 endpoints}, {@link CallRelay#callKey() call key},
 * and {@link CallRelay#hbhKey() hop-by-hop key} the call layer drives the media-plane handshake
 * against, and on a NACK it carries only the denormalised {@code call-creator} / {@code call-id}
 * attributes. Other call-class ACK types ({@code accept}, {@code reject}, etc.) typically carry
 * no relay block.
 */
public final class CallAck implements AckResult {
    private final String id;
    private final Instant timestamp;
    private final String type;
    private final Jid from;
    private final Jid participant;
    private final Jid recipient;
    private final Integer error;
    private final CallRelay relay;

    /**
     * Constructs a call ack snapshot. Package-private; the only caller is {@link AckParser}.
     */
    CallAck(String id, Instant timestamp, String type, Jid from, Jid participant, Jid recipient,
            Integer error, CallRelay relay) {
        this.id = id;
        this.timestamp = timestamp;
        this.type = type;
        this.from = from;
        this.participant = participant;
        this.recipient = recipient;
        this.error = error;
        this.relay = relay;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public AckClass ackClass() {
        return AckClass.CALL;
    }

    @Override
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    @Override
    public Optional<String> type() {
        return Optional.ofNullable(type);
    }

    @Override
    public Optional<Jid> from() {
        return Optional.ofNullable(from);
    }

    @Override
    public Optional<Jid> participant() {
        return Optional.ofNullable(participant);
    }

    @Override
    public Optional<Jid> recipient() {
        return Optional.ofNullable(recipient);
    }

    @Override
    public OptionalInt error() {
        return error != null ? OptionalInt.of(error) : OptionalInt.empty();
    }

    /**
     * Returns the parsed {@code <relay>} child of the {@code <ack>} stanza.
     *
     * @return the relay block, or {@link Optional#empty()} when none was present
     */
    public Optional<CallRelay> relay() {
        return Optional.ofNullable(relay);
    }

    @Override
    public String toString() {
        return "CallAck[id=" + id + ", type=" + type + ", error=" + error
                + ", relay=" + (relay != null) + ']';
    }
}
