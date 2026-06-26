package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a {@code <dtmf>} in-call action: the sender transmits a single DTMF tone during a
 * business or PSTN call.
 *
 * <p>A DTMF action carries one dual-tone multi-frequency keypress to the far end of a business or
 * gateway-bridged call. It carries the universal call header and a single {@code <tone>} child element
 * whose text content is the tone symbol (a digit {@code 0}-{@code 9}, {@code *}, {@code #}, or a
 * letter {@code A}-{@code D}). Each action carries exactly one tone; a sequence of keypresses is sent
 * as a sequence of actions.
 *
 * <p>On the wire the element is
 * {@snippet lang="xml" :
 * <dtmf call-id="..." call-creator="...">
 *   <tone>5</tone>
 * </dtmf>
 * }
 *
 * @implNote This implementation models the {@code <dtmf>} element built by {@code serialize_dtmf} in
 * the wa-voip WASM module {@code ff-tScznZ8P} ({@code stanzas/biz.cc}), carried in message-container
 * type {@code 0x68} and projecting to taxonomy ordinal {@code 42} ({@link Calls2SignalingType#DTMF_TONE}).
 * The tone is a {@code <tone>} child element with text content, parsed alongside the common header by
 * the inbound message reader (fn string-table tag {@code tone}). Attributes are stamped over the
 * common header written by {@code populate_common_call_attr} (fn11591): {@code call-id} (data offset
 * {@code 0x888f9}) and {@code call-creator} (data offset {@code 0x45ea5}).
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param tone        the DTMF tone symbol; never {@code null}
 * @see Calls2SignalingType#DTMF_TONE
 */
public record DtmfStanza(String callId, Jid callCreator, String tone)
        implements InCallActionStanza {
    /**
     * The wire element tag for a DTMF action.
     */
    public static final String ELEMENT = "dtmf";

    /**
     * The wire child element carrying the DTMF tone symbol.
     */
    private static final String TONE_ELEMENT = "tone";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code tone} is
     *                              {@code null}
     */
    public DtmfStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(tone, "tone cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#DTMF_TONE}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.DTMF_TONE;
    }

    /**
     * Builds the {@code <dtmf> <tone/> </dtmf>} action stanza.
     *
     * @return the DTMF action stanza
     */
    @Override
    public Stanza toStanza() {
        var toneNode = new StanzaBuilder()
                .description(TONE_ELEMENT)
                .content(tone)
                .build();
        return CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .content(toneNode)
                .build();
    }

    /**
     * Decodes a {@code <dtmf>} action stanza into a {@link DtmfStanza}.
     *
     * @param stanza the {@code <dtmf>} stanza
     * @return the decoded DTMF action
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent, or if the {@code <tone>} child element is absent
     */
    public static DtmfStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var tone = stanza.getChild(TONE_ELEMENT)
                .flatMap(Stanza::toContentString)
                .orElseThrow(() -> new NoSuchElementException("dtmf requires a tone child element"));
        return new DtmfStanza(callId, callCreator, tone);
    }
}
