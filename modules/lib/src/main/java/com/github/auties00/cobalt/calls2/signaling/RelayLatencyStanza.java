package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Represents a {@code <relaylatency>} signaling message, a relay-latency probe report.
 *
 * <p>A relay-latency message reports the round-trip latency a device measured to each relay it
 * probed, together with the device's bandwidth estimate toward each relay. The body is a list of
 * {@link RelayLatencyEntry} entries, one per probed relay; the receiving side feeds them into relay
 * election and one-side bandwidth estimation. The message carries the universal call header, may
 * carry a {@code transaction-id} correlating the report to a probe round, and may carry the
 * {@code has-bot} marker for a bot-bearing call.
 *
 * <p>This message is distinct from the {@code <relay>} block: a {@code <relay>} block advertises
 * relay servers and is parsed into {@link RelayInfo} and the {@link RelayCandidate} list, whereas a
 * {@code <relaylatency>} message reports measurements against those relays.
 *
 * <p>On the wire the message is
 * {@snippet lang="xml" :
 * <relaylatency call-id="..." call-creator="..." transaction-id="3">
 *   <te latency="42" ul_bw="500" dl_bw="800" relay_name="mxp1c01"/>
 *   <te xlatency="55" relay_name="mxp1c02"/>
 * </relaylatency>
 * }
 *
 * @implNote This implementation models the {@code <relaylatency>} element built by
 * {@code serialize_relay_latency} (fn11727) and parsed by {@code deserialize_relay_latency} (fn11729)
 * in the wa-voip WASM module {@code ff-tScznZ8P} ({@code stanzas/transport.cc}). The message carries
 * {@code has-bot} and {@code transaction-id} (data offset {@code 0x88892}) attributes over the common
 * header stamped by {@code populate_common_call_attr} (fn11591), and a repeated {@code <te>} child,
 * each entry parsed by {@code deserialize_te} (fn11622) and modeled by {@link RelayLatencyEntry}.
 *
 * @param callId        the call identifier; never {@code null}
 * @param callCreator   the call creator's device JID; never {@code null}
 * @param hasBot        whether the {@code has-bot} attribute marks a bot-bearing call
 * @param transactionId the {@code transaction-id} attribute, or {@code -1} when absent
 * @param entries       the {@code <te>} latency entries, in wire order; never {@code null}
 * @see RelayLatencyEntry
 * @see Calls2SignalingType#RELAY_LATENCY
 */
public record RelayLatencyStanza(String callId,
                                 Jid callCreator,
                                 boolean hasBot,
                                 int transactionId,
                                 List<RelayLatencyEntry> entries) implements CallMessage {
    /**
     * The wire element tag for a relay-latency message.
     */
    public static final String ELEMENT = "relaylatency";

    /**
     * The sentinel value standing in for an absent {@code transaction-id} attribute.
     */
    private static final int UNSET = -1;

    /**
     * The wire attribute marking a bot-bearing call.
     */
    private static final String HAS_BOT_ATTRIBUTE = "has-bot";

    /**
     * The wire attribute naming the probe transaction identifier.
     */
    private static final String TRANSACTION_ID_ATTRIBUTE = "transaction-id";

    /**
     * The wire literal a boolean attribute carries when set; booleans on the call plane serialize as
     * {@code '1'}/{@code '0'} rather than {@code true}/{@code false}.
     */
    private static final String FLAG_TRUE = "1";

    /**
     * Canonicalizes the record components, copying the entry list immutably.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code entries} is
     *                              {@code null}
     */
    public RelayLatencyStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries cannot be null"));
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#RELAY_LATENCY}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.RELAY_LATENCY;
    }

    /**
     * Returns the probe transaction identifier, if present.
     *
     * @return an {@link OptionalInt} holding the {@code transaction-id}, or empty when absent
     */
    public OptionalInt transactionIdValue() {
        return transactionId == UNSET ? OptionalInt.empty() : OptionalInt.of(transactionId);
    }

    /**
     * Builds the {@code <relaylatency>} action stanza for this message.
     *
     * <p>The stanza stamps {@code call-id} and {@code call-creator} as every action does; {@code has-bot}
     * is written only when set and {@code transaction-id} is omitted when absent. Each latency entry
     * becomes a {@code <te>} child.
     *
     * @return the relay-latency action stanza
     */
    @Override
    public Stanza toStanza() {
        var children = new ArrayList<Stanza>(entries.size());
        for (var entry : entries) {
            children.add(entry.toNode());
        }
        var builder = CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .attribute(HAS_BOT_ATTRIBUTE, FLAG_TRUE, hasBot)
                .attribute(TRANSACTION_ID_ATTRIBUTE, transactionId, transactionId != UNSET);
        if (!children.isEmpty()) {
            builder.content(children);
        }
        return builder.build();
    }

    /**
     * Decodes a {@code <relaylatency>} action stanza into a {@link RelayLatencyStanza}.
     *
     * <p>The {@code <te>} children are decoded through {@link RelayLatencyEntry#of(Stanza)}; a {@code <te>}
     * that does not decode is skipped.
     *
     * @param stanza the {@code <relaylatency>} stanza
     * @return the decoded message
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static RelayLatencyStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var hasBot = FLAG_TRUE.equals(stanza.getAttributeAsString(HAS_BOT_ATTRIBUTE, null));
        var transactionId = stanza.getAttributeAsInt(TRANSACTION_ID_ATTRIBUTE, UNSET);
        var entries = new ArrayList<RelayLatencyEntry>();
        for (var child : stanza.getChildren(RelayLatencyEntry.ELEMENT)) {
            RelayLatencyEntry.of(child).ifPresent(entries::add);
        }
        return new RelayLatencyStanza(callId, callCreator, hasBot, transactionId, entries);
    }
}
