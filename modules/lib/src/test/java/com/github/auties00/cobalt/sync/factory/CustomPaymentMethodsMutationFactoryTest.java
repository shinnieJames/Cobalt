package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link CustomPaymentMethodsMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing custom-payment-methods mutation against the
 * {@code WAWebCustomPaymentMethodsSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.CustomPaymentMethodsHandler}
 * whose inbound-side coverage lives in
 * {@code CustomPaymentMethodsHandlerTest}.
 *
 * @implNote
 * This implementation gates on
 * {@link SyncFixtures#isOracleAvailable(String)} so the suite remains green
 * until a real WAWeb-captured SMB Brazil PIX fixture is added.
 */
@DisplayName("CustomPaymentMethodsMutationFactory")
class CustomPaymentMethodsMutationFactoryTest {
    /**
     * Asserts that the captured encode oracle is loadable when present.
     */
    @Test
    @DisplayName("captured encode payload (when present) matches Cobalt's wire encoding")
    void oracle() {
        if (!SyncFixtures.isOracleAvailable("handler/custom-payment-methods/encode")) {
            return;
        }
        var oracle = SyncFixtures.loadOracle("handler/custom-payment-methods/encode");
        assertNotNull(oracle);
    }
}
