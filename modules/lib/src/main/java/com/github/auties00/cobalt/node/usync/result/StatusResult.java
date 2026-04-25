package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Optional;

/**
 * Success result of {@code WAWebUsyncStatus.statusParser}.
 *
 * <p>Three states distinguishable by the {@link #status()} value:
 * <ul>
 *   <li>{@code Optional.of(text)} — the live status text,</li>
 *   <li>{@code Optional.of("")} — the relay returned a {@code code="401"}
 *       indicating the peer's privacy settings hide the status,</li>
 *   <li>{@code Optional.empty()} — the peer has no status set.</li>
 * </ul>
 *
 * @implNote WAWebUsyncStatus.statusParser: returns one of
 *     {@code string | "" | null}; Cobalt collapses all three into an
 *     {@link Optional} with the empty-string discriminator preserved.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncStatus")
public final class StatusResult implements UsyncProtocolResponse {
    /**
     * The status string, or {@code null} when the peer has no status set.
     * The empty string preserves the {@code code="401"} privacy-block
     * marker.
     */
    private final String status;

    /**
     * Creates a new status result.
     *
     * @param status the status text, the empty string for the privacy-block
     *               marker, or {@code null} for "no status set"
     */
    public StatusResult(String status) {
        this.status = status;
    }

    /**
     * Returns the status, when present.
     *
     * @return the status
     */
    public Optional<String> status() {
        return Optional.ofNullable(status);
    }
}
