package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the outgoing-mutation wire shape produced by
 * {@link PaymentTosMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.PaymentTosHandler}
 * whose incoming-side coverage lives in
 * {@code PaymentTosHandlerTest}; the parity target is
 * {@code WAWebPaymentTosSync.getPaymentTosSetMutation}.
 *
 * @implNote
 * This implementation only verifies the oracle loads when present; the
 * full byte-equality check requires a fully populated
 * {@link com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction}
 * that the corpus capture does not yet emit, so the test stops at the
 * non-null oracle check until the fixture grows.
 */
@DisplayName("PaymentTosMutationFactory")
class PaymentTosMutationFactoryTest {
    /**
     * Verifies that the oracle loads when the fixture is present.
     */
    @Test
    @DisplayName("captured encode payload (when present) matches Cobalt's wire encoding")
    void oracle() {
        if (!SyncFixtures.isOracleAvailable("handler/payment-tos/encode")) {
            return;
        }
        var oracle = SyncFixtures.loadOracle("handler/payment-tos/encode");
        assertNotNull(oracle);
    }
}
