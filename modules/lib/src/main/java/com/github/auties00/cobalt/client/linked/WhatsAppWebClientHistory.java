package com.github.auties00.cobalt.client.linked;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

/**
 * The chat-history-sync policy a web companion advertises to the primary
 * device after linking, telling it how much past history to ship.
 *
 * <p>The policy carries a history-size cap and a flag for whether the
 * newsletter surface is included. Pick the volume via
 * {@link #discard(boolean)}, {@link #standard(boolean)},
 * {@link #extended(boolean)}, or a custom {@link #custom(int, boolean)}
 * cap. Bigger caps cost more memory, bandwidth, and pairing time.
 *
 * @apiNote
 * Set this on the client builder when the companion should request more or
 * less past history than the default; the value is advertised once, during
 * the companion's first handshake.
 *
 * @implNote
 * This implementation collapses the WhatsApp message, which carries 24
 * fields (storage quota, recent-sync window, on-demand readiness, support
 * flags), into the two fields most embedders need to set explicitly. The
 * 59206 cap on the standard preset is a Cobalt-chosen mid-range value;
 * WhatsApp computes its quota dynamically from local storage estimation.
 *
 * @see LinkedWhatsAppClient
 */
@ProtobufMessage
@WhatsAppWebModule(moduleName = "WAWebProtobufsCompanionReg.pb")
public final class WhatsAppWebClientHistory {
    /**
     * The cached zero-history policy with newsletters disabled.
     */
    private static final WhatsAppWebClientHistory ZERO = new WhatsAppWebClientHistory(0, false);

    /**
     * The cached zero-history policy with newsletters enabled.
     */
    private static final WhatsAppWebClientHistory ZERO_WITH_NEWSLETTERS = new WhatsAppWebClientHistory(0, true);

    /**
     * The cached standard-volume policy with newsletters disabled.
     *
     * @implNote
     * This implementation uses 59206 as a Cobalt-chosen mid-range cap;
     * WhatsApp computes its own per-session quota from local storage
     * estimation.
     */
    private static final WhatsAppWebClientHistory STANDARD = new WhatsAppWebClientHistory(59206, false);

    /**
     * The cached standard-volume policy with newsletters enabled.
     */
    private static final WhatsAppWebClientHistory STANDARD_WITH_NEWSLETTERS = new WhatsAppWebClientHistory(59206, true);

    /**
     * The cached extended-volume policy (request as much as the server
     * will deliver) with newsletters disabled.
     */
    private static final WhatsAppWebClientHistory EXTENDED = new WhatsAppWebClientHistory(Integer.MAX_VALUE, false);

    /**
     * The cached extended-volume policy with newsletters enabled.
     */
    private static final WhatsAppWebClientHistory EXTENDED_WITH_NEWSLETTERS = new WhatsAppWebClientHistory(Integer.MAX_VALUE, true);

    /**
     * The maximum number of history items to request during the initial
     * sync, where {@link Integer#MAX_VALUE} means unlimited.
     *
     * @implNote
     * This implementation expresses the cap as an item count rather than as
     * the day count WhatsApp uses.
     */
    @WhatsAppWebExport(moduleName = "WAWebProtobufsCompanionReg.pb",
            exports = "DeviceProps$HistorySyncConfigSpec", adaptation = WhatsAppAdaptation.ADAPTED)
    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    final int size;

    /**
     * Whether the newsletter surface is included in the initial
     * history sync.
     */
    @WhatsAppWebExport(moduleName = "WAWebProtobufsCompanionReg.pb",
            exports = "DeviceProps$HistorySyncConfigSpec", adaptation = WhatsAppAdaptation.ADAPTED)
    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    final boolean newsletters;

    /**
     * Constructs a new policy from raw size and newsletter components,
     * reached by application code through {@link #discard(boolean)},
     * {@link #standard(boolean)}, {@link #extended(boolean)}, or
     * {@link #custom(int, boolean)}.
     *
     * @param size        the history size cap
     * @param newsletters whether newsletters are included
     */
    WhatsAppWebClientHistory(int size, boolean newsletters) {
        this.size = size;
        this.newsletters = newsletters;
    }

    /**
     * Returns a policy that discards all chat history, keeping only messages
     * received after the session is established.
     *
     * @apiNote
     * Use this for real-time-only integrations; pairing and resource usage
     * are minimised.
     *
     * @param newsletters whether newsletters should be synchronised during
     *                    the initial connection
     * @return the configured policy
     */
    public static WhatsAppWebClientHistory discard(boolean newsletters) {
        return newsletters ? ZERO_WITH_NEWSLETTERS : ZERO;
    }

    /**
     * Returns a policy that mirrors a typical WhatsApp Web history-sync
     * volume, balancing availability of recent history against resource
     * cost.
     *
     * @apiNote
     * The recommended option for most integrations.
     *
     * @implNote
     * This implementation uses a 59206-item cap that is Cobalt-specific;
     * WhatsApp computes its quota dynamically.
     *
     * @param newsletters whether newsletters should be synchronised during
     *                    the initial connection
     * @return the configured policy
     */
    public static WhatsAppWebClientHistory standard(boolean newsletters) {
        return newsletters ? STANDARD_WITH_NEWSLETTERS : STANDARD;
    }

    /**
     * Returns a policy that requests as much chat history as the server is
     * willing to deliver.
     *
     * <p>The replay may include several months or years of messages
     * depending on account age, so memory and bandwidth use can be
     * substantial.
     *
     * @apiNote
     * Prefer this only for integrations that need the full archive.
     *
     * @param newsletters whether newsletters should be synchronised during
     *                    the initial connection
     * @return the configured policy
     */
    public static WhatsAppWebClientHistory extended(boolean newsletters) {
        return newsletters ? EXTENDED_WITH_NEWSLETTERS : EXTENDED;
    }

    /**
     * Returns a policy with a caller-supplied history-size cap.
     *
     * <p>The amount actually delivered may be smaller than requested when
     * the account lacks enough historical data or when the WhatsApp servers
     * impose a lower per-account cap.
     *
     * @param size        the maximum number of historical items to
     *                    synchronise; must be non-negative
     * @param newsletters whether newsletters should be synchronised during
     *                    the initial connection
     * @return the configured policy
     * @throws IllegalArgumentException if {@code size} is negative
     */
    public static WhatsAppWebClientHistory custom(int size, boolean newsletters) {
        return new WhatsAppWebClientHistory(size, newsletters);
    }

    /**
     * Returns whether this policy discards all chat history.
     *
     * @return {@code true} if the size cap is zero, {@code false}
     *         otherwise
     */
    public boolean isZero() {
        return size == 0;
    }

    /**
     * Returns whether this policy requests more history than the
     * standard preset.
     *
     * @return {@code true} if the size cap exceeds the standard
     *         preset, {@code false} otherwise
     */
    public boolean isExtended() {
        return size > STANDARD.size();
    }

    /**
     * Returns the upper bound on the number of historical items this policy
     * requests.
     *
     * <p>The amount actually synchronised may be smaller due to server
     * limits or available account history.
     *
     * @return the history-size cap, or {@link Integer#MAX_VALUE} for an
     *         unbounded request
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether this policy includes newsletters in the initial
     * synchronisation.
     *
     * @return {@code true} if newsletters are included, {@code false}
     *         otherwise
     */
    public boolean hasNewsletters() {
        return newsletters;
    }

    /**
     * Compares this policy to another object for structural equality.
     *
     * @param o the object to compare with
     * @return {@code true} if {@code o} is a
     *         {@code WhatsAppWebClientHistory} with the same size and
     *         newsletter flag
     */
    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof WhatsAppWebClientHistory that
                && size == that.size
                && newsletters == that.newsletters;
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(size, newsletters);
    }

    /**
     * Returns a human-readable description suitable for logs.
     *
     * @return the string representation
     */
    @Override
    public String toString() {
        return "WhatsappWebHistorySetting[" +
                "size=" + size + ", " +
                "newsletters=" + newsletters + ']';
    }
}
