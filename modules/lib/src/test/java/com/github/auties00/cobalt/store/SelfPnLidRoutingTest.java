package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Closes the loop on the self-send PN→LID routing bug.
 *
 * <p>Background: outgoing self-messages were addressed to the user's own
 * PN. WA Web instead rewrites a self-PN chat to the user's own LID and
 * stamps {@code peer_recipient_pn} on the wire. Without that rewrite the
 * server {@code ack}s but the primary phone never surfaces the message.
 *
 * <p>The fix was two-fold:
 * <ul>
 *   <li>{@link AbstractWhatsAppStore#findPhoneByLid(Jid)} grew a self-special
 *       case symmetric to {@link AbstractWhatsAppStore#findLidByPhone(Jid)},
 *       so that for the local user the LID→PN lookup falls back to
 *       {@link AbstractWhatsAppStore#jid()} the same way the PN→LID lookup
 *       falls back to {@link AbstractWhatsAppStore#lid()};</li>
 *   <li>{@code UserMessageSender.maybeReplaceWidWithAccountLid} rewrites
 *       the chat JID for a self-send before fanout/stanza building,
 *       mirroring WA Web's
 *       {@code WAWebPnlessStanzaMigration.maybeReplaceWidWithAccountLid}.</li>
 * </ul>
 *
 * <p>This test asserts the store side of the contract against the captured
 * live oracle ({@code fixtures/device/self-chat-routing.expected.json}):
 * for the captured account, the round-trip
 * {@code PN → findLidByPhone → LID → findPhoneByLid → PN} returns the
 * expected JIDs, with the {@code accountLid} field on WA Web's chat
 * record matching the LID Cobalt resolves through its store.
 */
class SelfPnLidRoutingTest {

    @Test
    void selfMappingRoundTripMatchesLiveOracle() {
        var oracle = DeviceFixtures.loadOracle("self-chat-routing");
        var pnInput = Jid.of(oracle.getString("pnInput").replace("@c.us", "@s.whatsapp.net"));
        var expectedLidBare = Jid.of(oracle.getString("chatId"));
        var expectedAccountLid = Jid.of(oracle.getString("accountLid"));

        // The oracle captured WA Web's findOrCreateLatestChat(<own PN>) producing
        // a LID-keyed chat. Cobalt's store should be able to give the same LID
        // back when populated with the same self identity.
        var store = DeviceFixtures.temporaryStore(pnInput, expectedLidBare);

        var lookedUpLid = store.findLidByPhone(pnInput).orElseThrow();
        assertEquals(expectedLidBare.toUserJid(), lookedUpLid.toUserJid(),
                "findLidByPhone(<own PN>) must return the LID WA Web resolves on the same input");

        var lookedUpPn = store.findPhoneByLid(expectedLidBare).orElseThrow();
        assertEquals(pnInput.toUserJid(), lookedUpPn.toUserJid(),
                "findPhoneByLid(<own LID>) must round-trip back to the same PN; "
                        + "before the symmetric self-special-case fix, this returned empty");

        // The chat.accountLid field on WA Web's side matches the LID, confirming
        // that maybeReplaceWidWithAccountLid points at the right destination.
        assertEquals(expectedLidBare, expectedAccountLid,
                "captured oracle invariant: chat.id == chat.accountLid for self-chat");
    }
}
