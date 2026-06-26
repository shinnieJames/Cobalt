package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Represents a {@code <screen_share>} in-call action: a participant starts, stops, or fails screen
 * sharing.
 *
 * <p>A screen-share action reports a transition in the sender's screen-sharing stream. It carries the
 * universal call header, a numeric {@code screenshare_state} code and an optional numeric
 * {@code version} naming the negotiated screen-share protocol generation. The recovered state codes
 * are {@code 1} (sharing started), {@code 2} (sharing stopped), and {@code 3} (sharing failed); the
 * version distinguishes the V2 single-stream port-swap path from the V3 dual-stream auxiliary-stream
 * path. The dual-stream lifecycle and per-direction request counters are owned by the screen-share
 * controller, not by this wire record.
 *
 * <p>On the wire the element is {@code <screen_share call-id="..." call-creator="..."
 * screenshare_state="N" version="N"/>}.
 *
 * @implNote This implementation models the {@code <screen_share>} element built by
 * {@code serialize_screen_share} in the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code stanzas/in_call_actions.cc}), carried in message-container type {@code 0x6c} and projecting
 * to taxonomy ordinal {@code 38} ({@link Calls2SignalingType#SCREEN_SHARE}); the matching ack is
 * taxonomy ordinal {@code 39}. The state codes {@code 1}/{@code 2}/{@code 3} are taken from the
 * {@code send_screen_share_event} (state, sharer_jid, version, reason) call site in
 * {@code media/screen_share.cc}. Attributes are stamped over the common header written by
 * {@code populate_common_call_attr} (fn11591): {@code call-id} (data offset {@code 0x888f9}) and
 * {@code call-creator} (data offset {@code 0x45ea5}).
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param state       the numeric screen-share state code ({@code 1} start, {@code 2} stopped,
 *                    {@code 3} failed)
 * @param version     the negotiated screen-share protocol version, or {@code -1} when absent
 * @see Calls2SignalingType#SCREEN_SHARE
 */
public record ScreenShareStanza(String callId, Jid callCreator, int state, int version)
        implements InCallActionStanza {
    /**
     * The wire element tag for a screen-share action.
     */
    public static final String ELEMENT = "screen_share";

    /**
     * The wire attribute naming the screen-share state code.
     */
    private static final String STATE_ATTRIBUTE = "screenshare_state";

    /**
     * The wire attribute naming the negotiated screen-share protocol version.
     */
    private static final String VERSION_ATTRIBUTE = "version";

    /**
     * The screen-share state code reported when sharing starts.
     */
    public static final int STATE_STARTED = 1;

    /**
     * The screen-share state code reported when sharing stops.
     */
    public static final int STATE_STOPPED = 2;

    /**
     * The screen-share state code reported when sharing fails.
     */
    public static final int STATE_FAILED = 3;

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public ScreenShareStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
    }

    /**
     * Returns the negotiated screen-share protocol version, if present.
     *
     * @return an {@link OptionalInt} holding the version, or empty when the action carries none
     */
    public OptionalInt versionValue() {
        return version < 0 ? OptionalInt.empty() : OptionalInt.of(version);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#SCREEN_SHARE}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.SCREEN_SHARE;
    }

    /**
     * Builds the {@code <screen_share call-id call-creator screenshare_state version/>} action stanza.
     *
     * <p>An absent {@code version} is omitted from the stanza.
     *
     * @return the screen-share action stanza
     */
    @Override
    public Stanza toStanza() {
        return CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .attribute(STATE_ATTRIBUTE, state)
                .attribute(VERSION_ATTRIBUTE, version, version >= 0)
                .build();
    }

    /**
     * Decodes a {@code <screen_share>} action stanza into a {@link ScreenShareStanza}.
     *
     * <p>An absent {@code screenshare_state} decodes to {@code 0}; an absent {@code version} decodes
     * to {@code -1}.
     *
     * @param stanza the {@code <screen_share>} stanza
     * @return the decoded screen-share action
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static ScreenShareStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var state = stanza.getAttributeAsInt(STATE_ATTRIBUTE, 0);
        var version = stanza.getAttributeAsInt(VERSION_ATTRIBUTE, -1);
        return new ScreenShareStanza(callId, callCreator, state, version);
    }
}
