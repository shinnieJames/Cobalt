package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural tests for {@link ParticipantsStanza}, mirroring
 * {@code WAWebSendMsgCreateFanoutStanza.createFanoutMsgStanza} (the
 * {@code <participants>} branch) and
 * {@code WAWebSendGroupSkmsgJob.encryptAndSendSenderKeyMsg}.
 *
 * @apiNote
 * Pins one dimension per test: empty-input edge cases for both wrappers,
 * the {@code decrypt-fail} default vs override on distribution
 * {@code <enc>}s, RCAT content-binding resolution keyed by user JID, the
 * skip-on-null-recipient rule that excludes the sender-key cipher
 * payload, and the {@link ParticipantsStanza#requiresIdentityNode}
 * predicate.
 *
 * @implNote
 * This implementation drives the two wrappers directly with raw
 * payload/binding inputs; no store seeding is needed.
 */
@DisplayName("ParticipantsStanza")
class ParticipantsStanzaTest {

    /**
     * A device JID for the primary device of the test user.
     */
    private static final Jid DEVICE_A = Jid.of("12025550100:0@s.whatsapp.net");

    /**
     * A device JID for a companion device of the same user.
     */
    private static final Jid DEVICE_B = Jid.of("12025550100:73@s.whatsapp.net");

    /**
     * The user JID corresponding to both {@link #DEVICE_A} and
     * {@link #DEVICE_B}.
     */
    private static final Jid USER_A = Jid.of("12025550100@s.whatsapp.net");

    /**
     * Fixture ciphertext bytes used wherever the actual ciphertext does
     * not matter.
     */
    private static final byte[] CIPHERTEXT = new byte[]{1, 2, 3};

    /**
     * Fixture RCAT tag bytes used wherever the actual binding value does
     * not matter.
     */
    private static final byte[] RCAT_TAG = new byte[]{4, 5, 6};

    /**
     * Empty payload list collapses to {@code null}.
     */
    @Test
    @DisplayName("buildSenderKeyDistribution: empty payload list returns null")
    void emptyPayloadsReturnsNull() {
        assertNull(ParticipantsStanza.buildSenderKeyDistribution(List.of(), null, null));
    }

    /**
     * Null payload list collapses to {@code null}.
     */
    @Test
    @DisplayName("buildSenderKeyDistribution: null payload list returns null")
    void nullPayloadsReturnsNull() {
        assertNull(ParticipantsStanza.buildSenderKeyDistribution(null, null, null));
    }

    /**
     * The {@code decrypt-fail} attribute defaults to {@code "hide"} on
     * every distribution {@code <enc>}.
     */
    @Test
    @DisplayName("buildSenderKeyDistribution: decrypt-fail defaults to \"hide\" on every <enc>")
    void defaultDecryptFailHide() {
        var payload = new MessageEncryptedPayload(MessageEncryptionType.PKMSG, CIPHERTEXT, DEVICE_A);
        var participants = ParticipantsStanza.buildSenderKeyDistribution(
                List.of(payload), null, null);

        var enc = participants.getChild("to").orElseThrow()
                .getChild("enc").orElseThrow();
        assertEquals("hide", enc.getAttributeAsString("decrypt-fail").orElseThrow(),
                "SK distribution must never produce a visible decrypt-fail placeholder");
    }

    /**
     * An explicit {@code decrypt-fail} value overrides the
     * {@code "hide"} default.
     */
    @Test
    @DisplayName("buildSenderKeyDistribution: explicit decrypt-fail overrides the default")
    void overrideDecryptFail() {
        var payload = new MessageEncryptedPayload(MessageEncryptionType.PKMSG, CIPHERTEXT, DEVICE_A);
        var participants = ParticipantsStanza.buildSenderKeyDistribution(
                List.of(payload), null, "custom-fail");

        var enc = participants.getChild("to").orElseThrow()
                .getChild("enc").orElseThrow();
        assertEquals("custom-fail", enc.getAttributeAsString("decrypt-fail").orElseThrow());
    }

    /**
     * Content bindings are looked up by user JID, not device JID; a
     * matching user JID attaches a {@code <content_binding>} to every
     * device of that user.
     */
    @Test
    @DisplayName("buildSenderKeyDistribution: content binding is keyed by user JID, not device JID")
    void contentBindingByUserJid() {
        var payload = new MessageEncryptedPayload(MessageEncryptionType.PKMSG, CIPHERTEXT, DEVICE_A);
        var bindings = Map.of(USER_A, RCAT_TAG);
        var participants = ParticipantsStanza.buildSenderKeyDistribution(
                List.of(payload), bindings, null);

        var to = participants.getChild("to").orElseThrow();
        assertTrue(to.getChild("content_binding").isPresent(),
                "RCAT keyed by user JID must attach <content_binding> under <to> for any matching device");
    }

    /**
     * A binding for an unrelated user JID does not leak onto the
     * {@code <to>} for {@link #USER_A}.
     */
    @Test
    @DisplayName("buildSenderKeyDistribution: no binding for unmapped user JID")
    void noBindingForUnmappedUser() {
        var payload = new MessageEncryptedPayload(MessageEncryptionType.PKMSG, CIPHERTEXT, DEVICE_A);
        var otherUser = Jid.of("19255550000@s.whatsapp.net");
        var bindings = Map.of(otherUser, RCAT_TAG);
        var participants = ParticipantsStanza.buildSenderKeyDistribution(
                List.of(payload), bindings, null);

        var to = participants.getChild("to").orElseThrow();
        assertFalse(to.getChild("content_binding").isPresent(),
                "RCAT for a different user must not bleed onto the <to> node");
    }

    /**
     * Payloads with a null {@code recipientJid} represent the
     * sender-key cipher and are skipped from the {@code <participants>}
     * output.
     */
    @Test
    @DisplayName("buildSenderKeyDistribution: payloads with null recipientJid are skipped")
    void nullRecipientSkipped() {
        var withRecipient = new MessageEncryptedPayload(MessageEncryptionType.PKMSG, CIPHERTEXT, DEVICE_A);
        var withoutRecipient = new MessageEncryptedPayload(MessageEncryptionType.PKMSG, CIPHERTEXT, null);
        var participants = ParticipantsStanza.buildSenderKeyDistribution(
                List.of(withRecipient, withoutRecipient), null, null);

        var toCount = participants.streamChildren("to").count();
        assertEquals(1, toCount, "payloads with null recipientJid (sender-key group cipher) must be skipped");
    }

    /**
     * No device whose user matches any binding key collapses
     * {@link ParticipantsStanza#buildContentBindingOnly} to {@code null}.
     */
    @Test
    @DisplayName("buildContentBindingOnly: returns null when no bindings match the device list")
    void contentBindingOnlyNullWhenNoMatch() {
        var bindings = Map.of(Jid.of("19255550000@s.whatsapp.net"), RCAT_TAG);
        var result = ParticipantsStanza.buildContentBindingOnly(List.of(DEVICE_A), bindings);
        assertNull(result, "no matching user JID -> entire <participants> node is null");
    }

    /**
     * Every device whose user matches a binding key produces a
     * {@code <to><content_binding/></to>} child.
     */
    @Test
    @DisplayName("buildContentBindingOnly: builds <to><content_binding/></to> only for matched users")
    void contentBindingOnlyEmitsForMatch() {
        var bindings = Map.of(USER_A, RCAT_TAG);
        var participants = ParticipantsStanza.buildContentBindingOnly(List.of(DEVICE_A, DEVICE_B), bindings);

        var toNodes = participants.streamChildren("to").toList();
        assertEquals(2, toNodes.size(),
                "every device JID whose user matches the RCAT map gets a <to>; here both DEVICE_A and DEVICE_B share USER_A");
        for (var to : toNodes) {
            assertTrue(to.getChild("content_binding").isPresent(),
                    "every emitted <to> in content-binding-only mode must carry <content_binding>");
        }
    }

    /**
     * {@link ParticipantsStanza#requiresIdentityNode(List)} returns
     * {@code true} iff at least one payload is a PreKey message; a null
     * list returns {@code false} instead of throwing.
     */
    @Test
    @DisplayName("requiresIdentityNode: true iff at least one payload is a PreKey message")
    void requiresIdentityNode() {
        var pkmsg = new MessageEncryptedPayload(MessageEncryptionType.PKMSG, CIPHERTEXT, DEVICE_A);
        var msg = new MessageEncryptedPayload(MessageEncryptionType.MSG, CIPHERTEXT, DEVICE_B);

        assertFalse(ParticipantsStanza.requiresIdentityNode(List.of(msg)),
                "all-MSG fanout does not need a <device-identity>");
        assertTrue(ParticipantsStanza.requiresIdentityNode(List.of(pkmsg)),
                "any PKMSG triggers the <device-identity>");
        assertTrue(ParticipantsStanza.requiresIdentityNode(List.of(msg, pkmsg)),
                "even a single PKMSG mixed in triggers the <device-identity>");
        assertFalse(ParticipantsStanza.requiresIdentityNode(null),
                "null payload list must return false, not throw");
    }
}
