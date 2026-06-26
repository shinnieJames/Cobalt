package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a {@code <notify>} in-call action: an out-of-band status notification, principally the
 * sender's battery state.
 *
 * <p>A notify action reports a low-priority status update to the other participants. It carries the
 * universal call header and a numeric {@code batterystate} code describing the sender's battery
 * condition. The {@code notify} container is also the message-type vehicle through which several
 * containerless actions (such as raise-hand and mute) travel; this record models the battery-state
 * use of the element itself.
 *
 * <p>On the wire the element is {@code <notify call-id="..." call-creator="..." batterystate="N"/>}.
 *
 * @implNote This implementation models the {@code <notify>} element built by {@code serialize_notify}
 * and parsed by {@code deserialize_notify} in the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code stanzas/in_call_actions.cc}), carried in message-container type {@code 0x68} and projecting
 * to taxonomy ordinal {@code 16} ({@link Calls2SignalingType#NOTIFY}). {@code deserialize_notify}
 * requires the {@code batterystate} attribute. Attributes are stamped over the common header written
 * by {@code populate_common_call_attr} (fn11591): {@code call-id} (data offset {@code 0x888f9}) and
 * {@code call-creator} (data offset {@code 0x45ea5}).
 *
 * @param callId       the call identifier; never {@code null}
 * @param callCreator  the call creator's device JID; never {@code null}
 * @param batteryState the numeric battery-state code reported by the sender
 * @see Calls2SignalingType#NOTIFY
 */
public record NotifyStanza(String callId, Jid callCreator, int batteryState)
        implements InCallActionStanza {
    /**
     * The wire element tag for a notify action.
     */
    public static final String ELEMENT = "notify";

    /**
     * The wire attribute naming the sender's battery-state code.
     */
    private static final String BATTERY_STATE_ATTRIBUTE = "batterystate";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public NotifyStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#NOTIFY}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.NOTIFY;
    }

    /**
     * Builds the {@code <notify call-id call-creator batterystate/>} action stanza.
     *
     * @return the notify action stanza
     */
    @Override
    public Stanza toStanza() {
        return CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .attribute(BATTERY_STATE_ATTRIBUTE, batteryState)
                .build();
    }

    /**
     * Decodes a {@code <notify>} action stanza into a {@link NotifyStanza}.
     *
     * @param stanza the {@code <notify>} stanza
     * @return the decoded notify action
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id}, {@code call-creator}, or
     *                                {@code batterystate} attribute is absent
     */
    public static NotifyStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var batteryState = stanza.getAttributeAsInt(BATTERY_STATE_ATTRIBUTE)
                .orElseThrow(() -> new NoSuchElementException("notify requires a batterystate attribute"));
        return new NotifyStanza(callId, callCreator, batteryState);
    }
}
