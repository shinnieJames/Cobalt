package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.model.WamEventSpec;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Recording {@link WamService} used by Cobalt's behavioural tests as a
 * drop-in replacement for {@link DefaultWamService}.
 *
 * @apiNote
 * Production code expecting a {@link WamService} dependency
 * (handlers, receivers, factories) takes the result of
 * {@link #create(WhatsAppClient)} verbatim; subsequent
 * {@link #committedEvents()} calls return the events the production
 * code passed to {@link #commit(WamEventSpec)} in invocation order.
 *
 * @implNote
 * This implementation overrides {@link #commit(WamEventSpec)} to skip
 * the pending-list pipeline entirely (sampling, channel routing,
 * buffer encoding, upload) and append the raw event into a
 * {@link CopyOnWriteArrayList} so tests can assert on what was sent
 * without driving a flush. The four scheduling hooks are stubbed: the
 * synthetic clock advances whenever {@link #sleep(long)} is called so
 * polling loops over {@link #now()} terminate deterministically, while
 * {@link #scheduleRecurring(Runnable, long, long)} and
 * {@link #cancelAllScheduled()} are no-ops because tests trigger any
 * scheduled work by invoking the task directly.
 */
public final class TestWamService extends WamService {
    /**
     * The list of events captured in {@link #commit(WamEventSpec)}
     * invocation order; copy-on-write so reads from
     * {@link #committedEvents()} never see partial updates.
     */
    private final List<WamEventSpec> committed = new CopyOnWriteArrayList<>();

    /**
     * The synthetic UTC instant returned by {@link #now()}; advanced by
     * each {@link #sleep(long)} call so loops that poll the clock
     * progress without real wall time elapsing.
     */
    private volatile Instant now = Instant.ofEpochSecond(1_780_000_000L);

    /**
     * Constructs a recording service against the given dependencies.
     *
     * @apiNote
     * Private; tests must go through {@link #create(WhatsAppClient)} so
     * the AB-props and beaconing wiring stays consistent across the
     * suite.
     *
     * @param client    the {@link WhatsAppClient} this service emits
     *                  events for
     * @param props     the AB-props facade
     * @param beaconing the beaconing source
     */
    private TestWamService(WhatsAppClient client, ABPropsService props, WamBeaconing beaconing) {
        super(client, props, beaconing);
    }

    /**
     * Returns a recording service wired against the given client with
     * an empty {@link TestABPropsService} and a fresh
     * {@link DefaultWamBeaconing}.
     *
     * @apiNote
     * The standard test-time factory; pass the returned instance
     * wherever a {@link WamService} dependency is expected.
     *
     * @param client the test client
     * @return a new recording service
     */
    public static TestWamService create(WhatsAppClient client) {
        var props = TestABPropsService.builder().build();
        return new TestWamService(client, props, new DefaultWamBeaconing());
    }

    /**
     * Returns an immutable snapshot of every event that was passed to
     * {@link #commit(WamEventSpec)}, in commit order.
     *
     * @apiNote
     * The list is a defensive copy; mutating it does not affect the
     * service's internal recording state.
     *
     * @return the captured events, oldest first
     */
    public List<WamEventSpec> committedEvents() {
        return List.copyOf(committed);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation records the event into the captured list
     * without sampling, weight resolution, channel routing, or buffer
     * encoding; tests assert on the recorded list directly.
     */
    @Override
    public void commit(WamEventSpec event) {
        committed.add(event);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns the synthetic instant held in
     * {@link #now}; updated by {@link #sleep(long)} rather than by the
     * system clock.
     */
    @Override
    protected Instant now() {
        return now;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation advances {@link #now} by {@code millis}
     * without actually parking the calling thread; loops that poll
     * {@link #now()} and call {@link #sleep(long)} converge in real
     * time even when {@code millis} would normally be a long wait.
     */
    @Override
    protected void sleep(long millis) {
        now = now.plusMillis(millis);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * No-op; tests that need scheduled work simply invoke {@code task}
     * inline rather than waiting for a tick.
     */
    @Override
    protected void scheduleRecurring(Runnable task, long initialDelaySeconds, long periodSeconds) {
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * No-op; the no-op {@link #scheduleRecurring(Runnable, long, long)}
     * never schedules anything to cancel.
     */
    @Override
    protected void cancelAllScheduled() {
    }
}
