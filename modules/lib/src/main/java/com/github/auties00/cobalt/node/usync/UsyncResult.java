package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.usync.result.UsyncProtocolError;
import com.github.auties00.cobalt.node.usync.result.UsyncUserResult;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregated result of a USync query.
 *
 * <p>The relay's response carries three sections:
 * <ul>
 *   <li>a per-protocol {@code <result>} block with optional
 *       {@code <error/>} or {@code refresh="..."} attributes that apply to
 *       <em>every</em> user in the response. Looked up via
 *       {@link #getProtocolError(UsyncProtocol)} and
 *       {@link #getProtocolRefresh(UsyncProtocol)}.</li>
 *   <li>a {@code <list>} block with one {@code <user>} child per peer.
 *       Exposed as an unmodifiable list via {@link #users()}.</li>
 *   <li>a top-level error (when the IQ failed wholesale): exposed via
 *       {@link #topLevelError()}.</li>
 * </ul>
 *
 * <p>Maps are not exposed directly — every per-protocol lookup goes
 * through a method so callers always interact with the result through a
 * stable typed surface.
 *
 * @implNote WAWebUsync.usyncParser.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public final class UsyncResult {
    /**
     * Per-user, per-protocol parse results in relay order.
     */
    private final List<UsyncUserResult> users;

    /**
     * Map from protocol name to the error metadata that applied to every
     * user in the response.
     */
    private final Map<String, UsyncProtocolError> protocolErrors;

    /**
     * Map from protocol name to the requested refresh window.
     */
    private final Map<String, Duration> protocolRefreshes;

    /**
     * Top-level error populated when the IQ failed entirely.
     */
    private final UsyncTopLevelError topLevelError;

    /**
     * Creates a new aggregated result. All collections are defensively
     * copied so the public surface stays immutable.
     *
     * @param users             the per-user results
     * @param protocolErrors    map from protocol name to error
     * @param protocolRefreshes map from protocol name to refresh window
     * @param topLevelError     top-level error, or {@code null} on success
     */
    public UsyncResult(
            List<UsyncUserResult> users,
            Map<String, UsyncProtocolError> protocolErrors,
            Map<String, Duration> protocolRefreshes,
            UsyncTopLevelError topLevelError) {
        this.users = users == null ? List.of() : List.copyOf(users);
        this.protocolErrors = protocolErrors == null ? Map.of() : Map.copyOf(protocolErrors);
        this.protocolRefreshes = protocolRefreshes == null ? Map.of() : Map.copyOf(protocolRefreshes);
        this.topLevelError = topLevelError;
    }

    /**
     * Returns the per-user results in relay order.
     *
     * @return an unmodifiable list, never {@code null}
     */
    public List<UsyncUserResult> users() {
        return Collections.unmodifiableList(users);
    }

    /**
     * Returns the protocol-level error that applied to every user, when
     * present.
     *
     * @param protocol the protocol descriptor
     * @return the error metadata
     */
    public Optional<UsyncProtocolError> getProtocolError(UsyncProtocol protocol) {
        Objects.requireNonNull(protocol, "protocol cannot be null");
        return getProtocolError(protocol.name());
    }

    /**
     * Returns the protocol-level error for the named protocol, when
     * present.
     *
     * @param protocolName the protocol's wire name
     * @return the error metadata
     */
    public Optional<UsyncProtocolError> getProtocolError(String protocolName) {
        return Optional.ofNullable(protocolErrors.get(protocolName));
    }

    /**
     * Returns the refresh-window hint the relay attached to the protocol,
     * when present.
     *
     * @param protocol the protocol descriptor
     * @return the refresh duration
     */
    public Optional<Duration> getProtocolRefresh(UsyncProtocol protocol) {
        Objects.requireNonNull(protocol, "protocol cannot be null");
        return getProtocolRefresh(protocol.name());
    }

    /**
     * Returns the refresh-window hint for the named protocol, when
     * present.
     *
     * @param protocolName the protocol's wire name
     * @return the refresh duration
     */
    public Optional<Duration> getProtocolRefresh(String protocolName) {
        return Optional.ofNullable(protocolRefreshes.get(protocolName));
    }

    /**
     * Returns the top-level IQ error, when the request failed wholesale.
     *
     * @return the error metadata
     */
    public Optional<UsyncTopLevelError> topLevelError() {
        return Optional.ofNullable(topLevelError);
    }

    /**
     * Returns whether the request failed wholesale.
     *
     * @return {@code true} when the IQ returned an error envelope
     */
    public boolean failed() {
        return topLevelError != null;
    }
}
