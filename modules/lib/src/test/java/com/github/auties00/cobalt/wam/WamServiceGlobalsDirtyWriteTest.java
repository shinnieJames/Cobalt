package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.event.PsIdUpdateEventBuilder;
import com.github.auties00.cobalt.wam.model.WamChannel;
import com.github.auties00.cobalt.wam.type.PsIdAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural tests for {@link WamService}'s {@code prevSessionGlobals}
 * dirty-tracking. WhatsApp Web's {@code WAWebWamLibContext.$2} only
 * re-emits global attributes whose values have changed since the
 * previous flush for the same channel; unchanged globals are skipped
 * to keep the buffer small.
 *
 * <p>Captured observable: the byte length of the WAM buffer uploaded
 * via {@code client.sendNode}. The first flush carries every
 * current global; the second flush with identical global state
 * carries no globals; the third flush after mutating one global
 * carries just that one global.
 */
@DisplayName("WamService prev-session-globals dirty-write")
class WamServiceGlobalsDirtyWriteTest {
    /**
     * Verifies the dirty-tracking three-step invariant: the
     * second flush is strictly smaller than the first (no globals
     * re-emitted) and the third flush after mutating a global is
     * strictly larger than the second (the mutated global is
     * emitted).
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

        // First flush — every global is "dirty" because
        // prevSessionGlobals.get(REGULAR) is null.
        service.commit(samplePsIdEvent());
        service.flushChannel(WamChannel.REGULAR);
        assertTrue(capturedBuffers.size() == 1,
                "expected exactly one sendNode call after first flush, got " + capturedBuffers.size());
        var firstSize = capturedBuffers.getFirst().length;

        // Second flush — identical globals state, so the globals
        // section of the buffer should be empty.
        service.commit(samplePsIdEvent());
        service.flushChannel(WamChannel.REGULAR);
        assertTrue(capturedBuffers.size() == 2,
                "expected exactly two sendNode calls after second flush, got " + capturedBuffers.size());
        var secondSize = capturedBuffers.get(1).length;
        assertTrue(secondSize < firstSize,
                () -> "second flush must be smaller than the first (no globals re-emitted): first="
                        + firstSize + " second=" + secondSize);

        // Mutate one global between flushes. deviceName is null in
        // this test (initialize() wasn't run), so setting it to
        // "TestDevice" must cause that one global to be re-emitted.
        service.setDeviceName("TestDevice");

        // Third flush — only deviceName should be emitted.
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
     * Returns a representative PsIdUpdate event. Each call returns
     * a fresh instance so {@code markCommitted} doesn't reject it.
     *
     * @return a PsIdUpdate event with the CREATED action
     */
    private static com.github.auties00.cobalt.wam.model.WamEventSpec samplePsIdEvent() {
        return new PsIdUpdateEventBuilder()
                .psIdAction(PsIdAction.CREATED)
                .psIdKey(42)
                .psIdRotationFrequence(7)
                .build();
    }

    /**
     * Returns a synthetic successful IQ response.
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
     * Extracts the encoded WAM buffer from the captured {@code <iq>}
     * stanza emitted by {@link WamService}'s
     * {@code sendViaIq}. The buffer is the content of the inner
     * {@code <add>} child.
     *
     * @param builder the captured outbound stanza builder
     * @return the buffer bytes, or an empty array when the iq has
     *         no add-child binary content
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
     * {@link WamService} subclass that overrides the four abstract
     * timing methods with no-op stubs sufficient for this test —
     * the test never sleeps, never schedules, and only invokes
     * {@code flushChannel} synchronously.
     */
    private static final class InstrumentedWamService extends WamService {
        /**
         * Holder for the fixed instant {@code now()} returns.
         */
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.ofEpochSecond(1_747_000_000L));

        /**
         * Constructs the instrumented service.
         *
         * @param client    the test client
         * @param props     the test AB props
         * @param beaconing the beaconing impl
         */
        InstrumentedWamService(
                com.github.auties00.cobalt.client.WhatsAppClient client,
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
            // no-op
        }

        @Override
        protected void scheduleRecurring(Runnable task, long initialDelaySeconds, long periodSeconds) {
            // no-op
        }

        @Override
        protected void cancelAllScheduled() {
            // no-op
        }
    }

}
