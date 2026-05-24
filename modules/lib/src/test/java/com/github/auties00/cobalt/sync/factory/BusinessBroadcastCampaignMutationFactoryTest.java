package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastCampaignAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastCampaignAction.Status;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastCampaignActionBuilder;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link BusinessBroadcastCampaignMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing business-broadcast-campaign mutation against
 * the {@code WAWebBroadcastCampaignSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.BusinessBroadcastCampaignHandler}
 * whose inbound-side coverage lives in
 * {@code BusinessBroadcastCampaignHandlerTest}.
 *
 * @implNote
 * This implementation builds a fixed sample campaign action so the encoded
 * bytes are deterministic; the WA Web oracle is captured with the same
 * field values.
 */
@DisplayName("BusinessBroadcastCampaignMutationFactory")
class BusinessBroadcastCampaignMutationFactoryTest {
    /**
     * The broadcast JID used by the sample campaign action.
     */
    private static final String BROADCAST_JID = "123-1234567890@broadcast";

    /**
     * The factory under test; rebuilt before each scenario.
     */
    private BusinessBroadcastCampaignMutationFactory factory;

    /**
     * Builds a fresh {@link BusinessBroadcastCampaignMutationFactory} before each test.
     */
    @BeforeEach
    void setUp() {
        factory = new BusinessBroadcastCampaignMutationFactory();
    }

    /**
     * Returns a fixed {@link BusinessBroadcastCampaignAction} matching the WA Web capture.
     *
     * @apiNote
     * Test fixture; the field values must stay in lockstep with the
     * captured oracle for byte-equality to hold.
     *
     * @return the canonical sample action
     */
    private BusinessBroadcastCampaignAction sampleAction() {
        return new BusinessBroadcastCampaignActionBuilder()
                .deviceId(0)
                .broadcastJid(BROADCAST_JID)
                .name("Promo")
                .msgId("tpl-1")
                .status(Status.SCHEDULED)
                .createTimestamp(1_700_000_000_000L)
                .scheduledTimestamp(1_700_001_000_000L)
                .reservedQuota(100)
                .build();
    }

    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/business-broadcast-campaign/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/business-broadcast-campaign/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = factory.getCampaignMutation("camp-oracle", sampleAction(),
                Instant.ofEpochSecond(1_700_000_000L));
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
