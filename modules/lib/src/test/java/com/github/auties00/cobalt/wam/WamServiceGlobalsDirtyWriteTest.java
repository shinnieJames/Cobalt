package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.event.PsIdUpdateEventBuilder;
import com.github.auties00.cobalt.wam.model.WamChannel;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.type.PsIdAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link WamService}'s per-channel
 * dirty-tracking for session globals across three successive
 * flushes.
 *
 * @apiNote
 * Mirrors WA Web's per-{@code WamContext} {@code prevGlobals}
 * dirty-tracking inside {@code WAWebWamLibContext}: the encoder
 * re-emits only the global attributes whose values have changed
 * since the previous flush on the same channel.
 *
 * @implNote
 * The observed signal is the byte length of the WAM buffer captured
 * via {@code client.sendNode}: the first flush carries every
 * current global, the second flush with identical state carries
 * none, and the third flush after mutating one global carries
 * exactly that one global's encoded entry.
 */
@DisplayName("WamService prev-session-globals dirty-write")
class WamServiceGlobalsDirtyWriteTest {
    /**
     * The dirty-tracking three-step invariant holds: the second
     * flush is strictly smaller than the first and the third flush
     * (after mutating a global) is strictly larger than the second.
     */
    @Test
    @DisplayName("unchanged globals are skipped on the second flush; mutated globals re-emit on the third")
    void dirtyTrackingSkipsUnchangedAndEmitsMutated() {
        var selfPn = Jid.of("19254863482@s.whatsapp.net");
        var props = TestABPropsService.builder().build();
        var capturedBuffers = new ArrayList<byte[]>();
        var store = DeviceFixtures.temporaryStore(selfPn, null);
        var client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(props)
                .withIsConnected(true)
                .withSendNodeHandler(builder -> {
                    capturedBuffers.add(extractBuffer(builder));
                    return successResponse();
                });
        var service = new InstrumentedWamService(client, props, new DefaultWamBeaconing());
        service.markInitializedForTesting();
        service.setSamplingOverride(2862, 1);

        service.commit(samplePsIdEvent());
        service.flushChannel(WamChannel.REGULAR);
        assertTrue(capturedBuffers.size() == 1,
                "expected exactly one sendNode call after first flush, got " + capturedBuffers.size());
        var firstSize = capturedBuffers.getFirst().length;

        service.commit(samplePsIdEvent());
        service.flushChannel(WamChannel.REGULAR);
        assertTrue(capturedBuffers.size() == 2,
                "expected exactly two sendNode calls after second flush, got " + capturedBuffers.size());
        var secondSize = capturedBuffers.get(1).length;
        assertTrue(secondSize < firstSize,
                () -> "second flush must be smaller than the first (no globals re-emitted): first="
                        + firstSize + " second=" + secondSize);

        service.setDeviceName("TestDevice");

        service.commit(samplePsIdEvent());
        service.flushChannel(WamChannel.REGULAR);
        assertTrue(capturedBuffers.size() == 3,
                "expected exactly three sendNode calls after third flush, got " + capturedBuffers.size());
        var thirdSize = capturedBuffers.get(2).length;
        assertTrue(thirdSize > secondSize,
                () -> "third flush must be larger than the second (mutated deviceName re-emitted): second="
                        + secondSize + " third=" + thirdSize);
    }

    /**
     * Builds a fresh {@code PsIdUpdate} event with the
     * {@link PsIdAction#CREATED} action.
     *
     * @apiNote
     * Each call returns a new instance so the spec's
     * {@code markCommitted()} guard does not reject repeat commits
     * across the three flushes.
     *
     * @return a fresh {@code PsIdUpdate} event spec
     */
    private static WamEventSpec samplePsIdEvent() {
        return new PsIdUpdateEventBuilder()
                .psIdAction(PsIdAction.CREATED)
                .psIdKey(42)
                .psIdRotationFrequence(7)
                .build();
    }

    /**
     * Builds a successful IQ response recognised by
     * {@link WamService}'s
     * {@code sendWithRetry} ({@code type="result"} on the root
     * {@code <iq>}).
     *
     * @return the success node
     */
    private static Node successResponse() {
        return new NodeBuilder()
                .description("iq")
                .attribute("type", "result")
                .build();
    }

    /**
     * Extracts the encoded WAM buffer from the captured outbound
     * {@code <iq>} stanza.
     *
     * @apiNote
     * The buffer lives in the binary content of the inner
     * {@code <add>} child of
     * {@link WamService}'s
     * {@code sendViaIq} stanza shape; throws
     * {@link AssertionError} when the captured stanza does not
     * match that shape so a regression in the encoder pipeline is
     * surfaced immediately.
     *
     * @param builder the captured outbound stanza builder
     * @return the buffer bytes
     */
    private static byte[] extractBuffer(NodeBuilder builder) {
        var built = builder.build();
        var add = built.getChild("add").orElseThrow(
                () -> new AssertionError("captured iq has no <add> child"));
        if (add instanceof Node.BytesNode bytesNode) {
            return bytesNode.content();
        }
        throw new AssertionError("captured <add> child has no binary content");
    }

    /**
     * Test double for {@link WamService} that stubs the four
     * abstract timing/scheduling hooks with no-op implementations.
     *
     * @apiNote
     * The dirty-tracking test never sleeps, never schedules, and
     * drives flushes synchronously via
     * {@link WamService#flushChannel(WamChannel)}, so the abstract
     * hooks have no captured-state requirements.
     */
    private static final class InstrumentedWamService extends WamService {
        /**
         * The fixed instant returned by {@link #now()}.
         */
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.ofEpochSecond(1_747_000_000L));

        /**
         * Constructs the instrumented service.
         *
         * @param client    the bound test client
         * @param props     the bound test AB-props service
         * @param beaconing the beaconing implementation
         */
        InstrumentedWamService(
                WhatsAppClient client,
                ABPropsService props,
                WamBeaconing beaconing) {
            super(client, props, beaconing);
        }

        @Override
        protected Instant now() {
            return now.get();
        }

        @Override
        protected void sleep(long millis) {
        }

        @Override
        protected void scheduleRecurring(Runnable task, long initialDelaySeconds, long periodSeconds) {
        }

        @Override
        protected void cancelAllScheduled() {
        }
    }

}
