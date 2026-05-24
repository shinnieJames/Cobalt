package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the self-send PN/LID round-trip through {@link AbstractWhatsAppStore}.
 *
 * @apiNote
 * Pins the store-side half of the self-send routing contract: when the local user sends a
 * message addressed to their own phone-number JID, the store must resolve the corresponding LID
 * and the LID must round-trip back to the same PN. Without this, the server acks the stanza but
 * the primary phone never surfaces the message because the destination chat is keyed under the
 * LID, not the PN.
 *
 * @implNote
 * This test compares against a captured live oracle
 * ({@code fixtures/device/self-chat-routing.expected.json}) produced by exercising WA Web's
 * {@code findOrCreateLatestChat(<own PN>)}, which yields a LID-keyed chat. The round-trip checked
 * here is {@code PN -> findLidByPhone -> LID -> findPhoneByLid -> PN}; the assertion on the
 * reverse direction exists to guarantee {@link AbstractWhatsAppStore#findPhoneByLid(Jid)} carries
 * the self-special-case symmetric to {@link AbstractWhatsAppStore#findLidByPhone(Jid)}, without
 * which the local user's LID would never resolve back to a PN.
 */
class SelfPnLidRoutingTest {

    /**
     * Round-trips the local user's own PN through the LID lookup back to the same PN and
     * matches the captured oracle's account LID.
     */
    @Test
    void selfMappingRoundTripMatchesLiveOracle() {
        var oracle = DeviceFixtures.loadOracle("self-chat-routing");
        var pnInput = Jid.of(oracle.getString("pnInput").replace("@c.us", "@s.whatsapp.net"));
        var expectedLidBare = Jid.of(oracle.getString("chatId"));
        var expectedAccountLid = Jid.of(oracle.getString("accountLid"));

        var store = DeviceFixtures.temporaryStore(pnInput, expectedLidBare);

        var lookedUpLid = store.findLidByPhone(pnInput).orElseThrow();
        assertEquals(expectedLidBare.toUserJid(), lookedUpLid.toUserJid(),
                "findLidByPhone(<own PN>) must return the LID WA Web resolves on the same input");

        var lookedUpPn = store.findPhoneByLid(expectedLidBare).orElseThrow();
        assertEquals(pnInput.toUserJid(), lookedUpPn.toUserJid(),
                "findPhoneByLid(<own LID>) must round-trip back to the same PN; "
                        + "before the symmetric self-special-case fix, this returned empty");

        assertEquals(expectedLidBare, expectedAccountLid,
                "captured oracle invariant: chat.id == chat.accountLid for self-chat");
    }
}
