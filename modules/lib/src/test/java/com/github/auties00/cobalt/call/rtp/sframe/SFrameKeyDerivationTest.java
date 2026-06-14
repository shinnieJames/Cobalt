package com.github.auties00.cobalt.call.rtp.sframe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Known-answer tests for {@link SFrameKeyDerivation}.
 *
 * <p>The call key and the expected base keys were captured live from the WhatsApp VoIP native call
 * engine: the {@code VoipCrypto::HkdfSha256} inputs (the 16-byte salt and 16-byte input keying
 * material, which are the two halves of the call key) and the resulting base keys handed to the
 * SFrame key provider for each participant of a one-to-one call. The two participant JIDs are the
 * real self and peer LIDs from that call, so this suite pins the call-key split, the
 * {@code "e2e sframe key"} label, the JID concatenation, and the output length against genuine engine
 * output.
 */
public class SFrameKeyDerivationTest {
    private static final HexFormat HEX = HexFormat.of();

    // callKey = salt half (callKey[0:16]) || ikm half (callKey[16:32]), captured live
    private static final byte[] CALL_KEY = HEX.parseHex(
            "0ca0b1ab1e8d555292e9fc488c3b7034" + "4f072b4acb9c5a98949a929ae237a8ac");
    private static final String SELF_JID = "258252122116273:63@lid";
    private static final String PEER_JID = "39110693621863:0@lid";

    @Test
    @DisplayName("derives each participant base key to the captured live vector")
    public void vectors() {
        assertArrayEquals(
                HEX.parseHex("d4203d875960017266b2e904ed02cc7be12553112e4378047b8de912005d5f4d"),
                SFrameKeyDerivation.deriveParticipantBaseKey(CALL_KEY, SELF_JID), SELF_JID);
        assertArrayEquals(
                HEX.parseHex("2fe0c5b8d8dc42cebde0ee3e8a6b23b610e6831214c6a10d13688e26ae29e789"),
                SFrameKeyDerivation.deriveParticipantBaseKey(CALL_KEY, PEER_JID), PEER_JID);
    }

    @Test
    @DisplayName("derives a 32-byte base key")
    public void length() {
        assertEquals(32, SFrameKeyDerivation.deriveParticipantBaseKey(CALL_KEY, SELF_JID).length);
    }

    @Test
    @DisplayName("derives distinct base keys for distinct participant JIDs from one call key")
    public void distinctPerJid() {
        var self = SFrameKeyDerivation.deriveParticipantBaseKey(CALL_KEY, SELF_JID);
        var peer = SFrameKeyDerivation.deriveParticipantBaseKey(CALL_KEY, PEER_JID);
        assertFalse(Arrays.equals(self, peer));
    }

    @Test
    @DisplayName("rejects a call key whose length is not 32 bytes")
    public void rejectsWrongLength() {
        assertThrows(IllegalArgumentException.class,
                () -> SFrameKeyDerivation.deriveParticipantBaseKey(new byte[16], SELF_JID));
        assertThrows(IllegalArgumentException.class,
                () -> SFrameKeyDerivation.deriveParticipantBaseKey(new byte[31], SELF_JID));
    }
}
