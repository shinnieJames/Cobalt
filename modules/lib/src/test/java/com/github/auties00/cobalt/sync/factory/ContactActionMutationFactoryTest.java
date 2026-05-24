package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link ContactActionMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing contact-sync mutation against the
 * {@code WAWebContactSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.ContactActionHandler}
 * whose inbound-side coverage lives in
 * {@code ContactActionHandlerTest}.
 *
 * @implNote
 * This implementation calls the eight-arg overload directly with a pinned
 * {@link Instant} so the captured oracle's timestamp lines up with
 * Cobalt's re-encoded bytes; the seven-arg overload would use
 * {@link Instant#now()} and break byte parity.
 */
@DisplayName("ContactActionMutationFactory")
class ContactActionMutationFactoryTest {
    /**
     * Phone-number JID used for the contact.
     */
    private static final Jid CONTACT_PN = Jid.of("33330000@s.whatsapp.net");

    /**
     * LID-form JID used for the same contact.
     */
    private static final Jid CONTACT_LID = Jid.of("70000000000000@lid");

    /**
     * The factory under test; rebuilt before each scenario.
     */
    private ContactActionMutationFactory factory;

    /**
     * Builds a fresh {@link ContactActionMutationFactory} before each test.
     */
    @BeforeEach
    void setUp() {
        factory = new ContactActionMutationFactory();
    }

    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/contact/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/contact/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = factory.getContactSyncMutation(
                CONTACT_PN, "Maria", "Maria Garcia", false, CONTACT_LID, true, "maria",
                Instant.ofEpochSecond(oracle.getLong("timestampSeconds")));
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
