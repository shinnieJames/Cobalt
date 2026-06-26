package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

/**
 * Holds the shared wire constants and common-header helpers the call-message payload records use.
 *
 * <p>Every call action element inside the top-level {@code <call>} stanza carries two universal
 * attributes the wa-voip engine stamps on every outgoing action and requires on every inbound one:
 * the call identifier and the call creator. This package-private utility centralizes the two
 * attribute names and the stamp helper so each payload record applies them identically rather than
 * repeating the literals, keeping the per-record builders to their own type-specific attributes and
 * children.
 *
 * @implNote This implementation centralizes the attribute names {@code populate_common_call_attr}
 * (fn11591) stamps in the wa-voip WASM module {@code ff-tScznZ8P}: {@code call-id} (data offset
 * {@code 0x888f9}, stamped from the call context offset {@code 0x04}) and {@code call-creator} (data
 * offset {@code 0x45ea5}, the originator device JID from offset {@code 0x54}). It holds no state and
 * is not instantiable.
 */
final class CallMessages {
    /**
     * The wire attribute naming the call identifier, stamped on every action element.
     */
    static final String CALL_ID_ATTRIBUTE = "call-id";

    /**
     * The wire attribute naming the call creator's device JID, stamped on every action element.
     */
    static final String CALL_CREATOR_ATTRIBUTE = "call-creator";

    /**
     * Prevents instantiation of this utility holder.
     *
     * @throws AssertionError always, since this class is not instantiable
     */
    private CallMessages() {
        throw new AssertionError("CallMessages is not instantiable");
    }

    /**
     * Stamps the universal call header onto an action-element builder.
     *
     * <p>Applies the {@code call-id} and {@code call-creator} attributes in the order the engine
     * writes them, returning the same builder so the caller can chain its type-specific attributes
     * and content.
     *
     * @param builder     the action-element builder to stamp
     * @param callId      the call identifier
     * @param callCreator the call creator's device JID
     * @return the supplied builder, with the common header applied
     */
    static StanzaBuilder stampHeader(StanzaBuilder builder, String callId, Jid callCreator) {
        return builder
                .attribute(CALL_ID_ATTRIBUTE, callId)
                .attribute(CALL_CREATOR_ATTRIBUTE, callCreator);
    }
}
