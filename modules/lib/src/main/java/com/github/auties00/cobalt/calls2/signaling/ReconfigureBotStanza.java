package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a {@code <reconfigure_bot>} in-call action: a request to reconfigure the GenAI bot
 * participating in the call.
 *
 * <p>A reconfigure-bot action asks the call's GenAI bot to reload or re-apply its configuration. It
 * carries the universal call header and a numeric {@code req_id} correlating the reconfiguration
 * request with its acknowledgement.
 *
 * <p>On the wire the element is {@code <reconfigure_bot call-id="..." call-creator="..."
 * req_id="N"/>}.
 *
 * @implNote This implementation models the {@code <reconfigure_bot>} element built by
 * {@code serialize_reconfigure_bot} and parsed by {@code deserialize_reconfigure_bot} in the wa-voip
 * WASM module {@code ff-tScznZ8P} ({@code stanzas/genai.cc}), carried in message-container type
 * {@code 0x70} and projecting to taxonomy ordinal {@code 61} ({@link Calls2SignalingType#RECONFIGURE_BOT}).
 * {@code deserialize_reconfigure_bot} requires the {@code req_id} attribute. Attributes are stamped
 * over the common header written by {@code populate_common_call_attr} (fn11591): {@code call-id} (data
 * offset {@code 0x888f9}) and {@code call-creator} (data offset {@code 0x45ea5}).
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param requestId   the reconfiguration request correlation id
 * @see Calls2SignalingType#RECONFIGURE_BOT
 */
public record ReconfigureBotStanza(String callId, Jid callCreator, int requestId)
        implements InCallActionStanza {
    /**
     * The wire element tag for a reconfigure-bot action.
     */
    public static final String ELEMENT = "reconfigure_bot";

    /**
     * The wire attribute naming the reconfiguration request id.
     */
    private static final String REQUEST_ID_ATTRIBUTE = "req_id";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public ReconfigureBotStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#RECONFIGURE_BOT}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.RECONFIGURE_BOT;
    }

    /**
     * Builds the {@code <reconfigure_bot call-id call-creator req_id/>} action stanza.
     *
     * @return the reconfigure-bot action stanza
     */
    @Override
    public Stanza toStanza() {
        return CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .attribute(REQUEST_ID_ATTRIBUTE, requestId)
                .build();
    }

    /**
     * Decodes a {@code <reconfigure_bot>} action stanza into a {@link ReconfigureBotStanza}.
     *
     * @param stanza the {@code <reconfigure_bot>} stanza
     * @return the decoded reconfigure-bot action
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id}, {@code call-creator}, or
     *                                {@code req_id} attribute is absent
     */
    public static ReconfigureBotStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var requestId = stanza.getAttributeAsInt(REQUEST_ID_ATTRIBUTE)
                .orElseThrow(() -> new NoSuchElementException("reconfigure_bot requires a req_id attribute"));
        return new ReconfigureBotStanza(callId, callCreator, requestId);
    }
}
