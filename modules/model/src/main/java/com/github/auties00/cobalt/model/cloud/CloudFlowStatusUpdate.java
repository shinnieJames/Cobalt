package com.github.auties00.cobalt.model.cloud;

import java.util.Objects;
import java.util.Optional;

/**
 * A Flow lifecycle or health event, decoded from a {@code flows} webhook change.
 *
 * <p>The platform reports both status transitions (draft to published, published to deprecated)
 * and endpoint health alerts (error rate, latency, availability) for the Flows of a WhatsApp
 * Business Account.
 */
public final class CloudFlowStatusUpdate {
    /**
     * The event kind, for example {@code FLOW_STATUS_CHANGE} or {@code ENDPOINT_ERROR_RATE}.
     */
    private final String event;

    /**
     * The server-assigned flow id, or {@code null} when not reported.
     */
    private final String flowId;

    /**
     * The human-readable event description, or {@code null} when not reported.
     */
    private final String message;

    /**
     * The status before the transition, or {@code null} when the event is not a status change.
     */
    private final String oldStatus;

    /**
     * The status after the transition, or {@code null} when the event is not a status change.
     */
    private final String newStatus;

    /**
     * Constructs a new flow status update.
     *
     * @param event     the event kind
     * @param flowId    the server-assigned flow id, or {@code null}
     * @param message   the human-readable description, or {@code null}
     * @param oldStatus the status before the transition, or {@code null}
     * @param newStatus the status after the transition, or {@code null}
     * @throws NullPointerException if {@code event} is {@code null}
     */
    public CloudFlowStatusUpdate(String event, String flowId, String message, String oldStatus, String newStatus) {
        this.event = Objects.requireNonNull(event, "event must not be null");
        this.flowId = flowId;
        this.message = message;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    /**
     * Returns the event kind.
     *
     * @return the event, for example {@code FLOW_STATUS_CHANGE}
     */
    public String event() {
        return event;
    }

    /**
     * Returns the server-assigned flow id.
     *
     * @return an {@link Optional} carrying the id, or empty when not reported
     */
    public Optional<String> flowId() {
        return Optional.ofNullable(flowId);
    }

    /**
     * Returns the human-readable event description.
     *
     * @return an {@link Optional} carrying the description, or empty when not reported
     */
    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    /**
     * Returns the status before the transition.
     *
     * @return an {@link Optional} carrying the old status, or empty when the event is not a status
     *         change
     */
    public Optional<String> oldStatus() {
        return Optional.ofNullable(oldStatus);
    }

    /**
     * Returns the status after the transition.
     *
     * @return an {@link Optional} carrying the new status, or empty when the event is not a status
     *         change
     */
    public Optional<String> newStatus() {
        return Optional.ofNullable(newStatus);
    }
}
