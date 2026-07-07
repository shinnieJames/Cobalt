package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a {@code <peer_state>} in-call action: a report of a single peer's membership state.
 *
 * <p>A peer-state action communicates the membership/connection state the engine tracks for one
 * participant. It carries the universal call header and nests one {@code <user>} child that pins the
 * target peer by its {@link #peerJid() JID} and decorates it with a {@link #state() numeric peer-state
 * code} drawn from a twelve-entry state table. The engine builds it through
 * {@code make_and_send_peer_state_msg}, embedding the target peer JID and the peer-state enum together
 * with a rolling wall-clock timestamp; state code {@code 10} is the {@code cancel_offer} marker used
 * when cancelling an offer for specific participants. This peer-state code is the engine's membership
 * view and is distinct from the application-facing peer-state model.
 *
 * <p>On the wire the peer-state code is not written numerically: the engine projects it through the
 * twelve-entry state table to a lower-cased string, and the {@code <peer_state>} element nests a
 * {@code <user>} child carrying that string and the peer JID. The recovered table is
 * {@code 0=invalid, 1=connected, 2=outgoing, 3=receipt, 4=rejected, 5=terminated, 6=timedout,
 * 7=creating, 8=invisible, 9=visible, 10=cancel_offer, 11=invited}; a code at or above {@code 12}
 * serializes as the {@code UNKNOWN PARTICIPANT STATE} sentinel the engine uses for an out-of-range
 * value.
 *
 * <p>On the wire the element is
 * {@snippet lang="xml" :
 * <peer_state call-id="..." call-creator="..." t="1781494319173">
 *     <user state="cancel_offer" jid="258252122116273@lid"/>
 * </peer_state>
 * }
 * where {@code t} is the engine's send-time wall-clock and {@code state} is the stringified peer-state
 * code.
 *
 * @implNote This implementation models the {@code <peer_state>} element built by
 * {@code make_and_send_peer_state_msg} (fn11435, {@code messages/call_signaling_sender.cc}) and
 * serialized by {@code serialize_peer_state} (fn11702, {@code protocol/xmpp/stanzas/group_call.cc}) in
 * the wa-voip WASM module {@code ff-tScznZ8P}, carried in message type {@code 0x13} and projecting to
 * taxonomy id {@code 19} ({@link Calls2SignalingType#PEER_STATE}). The serializer writes the element tag
 * {@code peer_state} (data offset {@code 0x7111b}), the common header {@code call-id}
 * ({@code 0x888f9}) and {@code call-creator} ({@code 0x45ea5}), a {@code t} timestamp attribute
 * ({@code 0x2653f}) set from the send-time wall-clock, then one {@code user} child ({@code 0x48f24})
 * carrying a {@code state} attribute ({@code 0x71b60}) whose value is the peer-state enum
 * (serializer-struct field at offset {@code 0xb4}; the source message field is {@code 0x6b4}) projected
 * through the twelve-entry string table at data offset {@code 0x1262b0} ({@code UNKNOWN PARTICIPANT STATE}
 * at {@code 0xc3c52} for an index at or above {@code 0xc}) and a {@code jid} attribute ({@code 0x87ad0})
 * holding the target peer JID (serializer-struct field at offset {@code 0x64}; the source message field
 * is {@code 0x704}). The wire shape and the table values are corroborated by a connected group call that
 * sent {@code <peer_state ... t><user state="cancel_offer" jid="...@lid"/></peer_state>} and
 * {@code <user state="invisible" .../>} (live capture
 * {@code re/calls2-spec/captures/group-stanzas-bizcaller.jsonl} and the matching peer and primary
 * captures), acknowledged by {@code <ack class="call" type="peer_state">}. The {@code t} timestamp is an
 * engine-internal send-time wall-clock (computed in fn11435, not a caller-supplied value) and is omitted
 * on encode; an inbound {@code t} is ignored on decode.
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param peerJid     the JID of the peer whose state is reported, carried in the {@code <user>} child;
 *                    never {@code null}
 * @param state       the numeric peer-state code ({@code 0..0xb}; an out-of-range value serializes as the
 *                    unknown-state sentinel)
 * @see Calls2SignalingType#PEER_STATE
 */
public record PeerStateStanza(String callId, Jid callCreator, Jid peerJid, int state)
        implements InCallActionStanza {
    /**
     * The wire element tag for a peer-state action.
     */
    public static final String ELEMENT = "peer_state";

    /**
     * The wire element tag for the nested participant entry that carries the peer JID and state string.
     */
    private static final String USER_ELEMENT = "user";

    /**
     * The wire attribute on the {@code <user>} child naming the peer whose state is reported.
     */
    private static final String JID_ATTRIBUTE = "jid";

    /**
     * The wire attribute on the {@code <user>} child naming the stringified peer-state code.
     */
    private static final String STATE_ATTRIBUTE = "state";

    /**
     * The string the engine writes for a peer-state code that lies outside the {@link #STATE_NAMES} table.
     */
    private static final String UNKNOWN_STATE = "UNKNOWN PARTICIPANT STATE";

    /**
     * Maps each peer-state code to its lower-cased wire string, indexed by code.
     *
     * <p>This is the twelve-entry table the engine indexes at data offset {@code 0x1262b0}: the code is
     * the array index and the value is the string written into the {@code <user state>} attribute.
     */
    private static final String[] STATE_NAMES = {
            "invalid",
            "connected",
            "outgoing",
            "receipt",
            "rejected",
            "terminated",
            "timedout",
            "creating",
            "invisible",
            "visible",
            "cancel_offer",
            "invited"
    };

    /**
     * Maps each lower-cased wire string back to its peer-state code for inbound decoding.
     *
     * <p>This is the inverse of {@link #STATE_NAMES}: the state string written into a {@code <user state>}
     * attribute resolves back to the numeric code the engine stores.
     */
    private static final Map<String, Integer> CODE_BY_NAME = buildCodeByName();

    /**
     * Builds the inverse of {@link #STATE_NAMES}, mapping each wire string to its peer-state code.
     *
     * @return an unmodifiable map from state string to numeric code
     */
    private static Map<String, Integer> buildCodeByName() {
        var codeByName = new HashMap<String, Integer>(STATE_NAMES.length * 2);
        for (var code = 0; code < STATE_NAMES.length; code++) {
            codeByName.put(STATE_NAMES[code], code);
        }
        return Map.copyOf(codeByName);
    }

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code peerJid} is
     *                              {@code null}
     */
    public PeerStateStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(peerJid, "peerJid cannot be null");
    }

    /**
     * Returns the lower-cased wire string the given peer-state code projects to.
     *
     * <p>A code within {@code 0..0xb} resolves through the twelve-entry {@link #STATE_NAMES} table; a code
     * at or above {@code 12} resolves to the {@link #UNKNOWN_STATE} sentinel the engine writes for an
     * out-of-range index.
     *
     * @param code the numeric peer-state code
     * @return the wire string for the code
     */
    private static String stateName(int code) {
        return code >= 0 && code < STATE_NAMES.length ? STATE_NAMES[code] : UNKNOWN_STATE;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#PEER_STATE}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.PEER_STATE;
    }

    /**
     * Builds the {@code <peer_state call-id call-creator>} action stanza with its nested {@code <user>}
     * child.
     *
     * <p>The common header is stamped first, then a single {@code <user state jid/>} child carries the
     * stringified peer-state code and the target peer JID. The engine-internal {@code t} send-time
     * wall-clock is not written; the receiver treats it as informational.
     *
     * @return the peer-state action stanza
     */
    @Override
    public Stanza toStanza() {
        var user = new StanzaBuilder()
                .description(USER_ELEMENT)
                .attribute(STATE_ATTRIBUTE, stateName(state))
                .attribute(JID_ATTRIBUTE, peerJid)
                .build();
        return CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .content(user)
                .build();
    }

    /**
     * Decodes a {@code <peer_state>} action stanza into a {@link PeerStateStanza}.
     *
     * <p>The nested {@code <user>} child supplies the peer JID and the {@code state} string, which is
     * projected back through the twelve-entry table to its numeric code; an unrecognized or absent state
     * string decodes to code {@code 0} ({@code invalid}). The {@code t} timestamp attribute, when present,
     * is ignored.
     *
     * @param stanza the {@code <peer_state>} stanza
     * @return the decoded peer-state action
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute is
     *                                absent, or if the nested {@code <user>} child or its {@code jid}
     *                                attribute is absent
     */
    public static PeerStateStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var user = stanza.getChild(USER_ELEMENT)
                .orElseThrow(() -> new NoSuchElementException("peer_state is missing its user child"));
        var peerJid = user.getRequiredAttributeAsJid(JID_ATTRIBUTE);
        var state = user.getAttributeAsString(STATE_ATTRIBUTE)
                .map(name -> CODE_BY_NAME.getOrDefault(name, 0))
                .orElse(0);
        return new PeerStateStanza(callId, callCreator, peerJid, state);
    }
}
