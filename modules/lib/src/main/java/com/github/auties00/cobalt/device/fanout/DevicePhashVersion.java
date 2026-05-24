package com.github.auties00.cobalt.device.fanout;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Selects the participant-hash algorithm ({@code phashV1} versus {@code phashV2}) used on
 * WhatsApp group message stanzas.
 *
 * @apiNote
 * Passed to {@link DevicePhashCalculator#calculate} to switch between the legacy SHA-1 V1
 * format and the current SHA-256 V2 format. Each enum constant carries the
 * {@link java.security.MessageDigest} algorithm name, the literal prefix prepended to the
 * base64-encoded truncated digest, and whether the version permits Meta AI bot injection;
 * only V2 supports the bot injection branch.
 *
 * @see DevicePhashCalculator
 */
@WhatsAppWebModule(moduleName = "WAWebPhashUtils")
public enum DevicePhashVersion {

    /**
     * The legacy SHA-1 participant hash, encoded with the {@code "1:"} prefix.
     *
     * @apiNote
     * Matches WA Web's {@code WAWebPhashUtils.phashV1}; produces eight base64 characters after
     * the prefix and does not allow Meta AI bot injection regardless of caller flags.
     */
    V1("SHA-1", "1:", false),

    /**
     * The current SHA-256 participant hash, encoded with the {@code "2:"} prefix.
     *
     * @apiNote
     * Matches WA Web's {@code WAWebPhashUtils.phashV2}; produces eight base64 characters after
     * the prefix and supports injecting the open or TEE Meta AI bot account JIDs when both the
     * caller flag and the matching AB prop on {@link DevicePhashCalculator} are set.
     */
    V2("SHA-256", "2:", true);

    /**
     * The {@link java.security.MessageDigest} algorithm name used for this version.
     */
    private final String algorithm;

    /**
     * The literal prefix prepended to the base64-encoded truncated digest.
     */
    private final String prefix;

    /**
     * Whether this version permits Meta AI bot injection into the hashed set.
     */
    private final boolean supportsMetaBot;

    /**
     * Constructs an enum constant with the wire-shape parameters of one phash version.
     *
     * @apiNote
     * Not callable from outside the enum; used only by the {@link #V1} and {@link #V2}
     * declarations.
     *
     * @param algorithm       the {@link java.security.MessageDigest} algorithm name
     * @param prefix          the literal prefix prepended to the encoded digest
     * @param supportsMetaBot whether Meta AI bot injection is allowed for this version
     */
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = {"phashV1", "phashV2"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    DevicePhashVersion(String algorithm, String prefix, boolean supportsMetaBot) {
        this.algorithm = algorithm;
        this.prefix = prefix;
        this.supportsMetaBot = supportsMetaBot;
    }

    /**
     * Returns the {@link java.security.MessageDigest} algorithm name for this version.
     *
     * @apiNote
     * Used by {@link DevicePhashCalculator#calculate} to call
     * {@link java.security.MessageDigest#getInstance(String)}; expect
     * {@link java.security.NoSuchAlgorithmException} from the calculator if the JRE does not
     * provide the algorithm.
     *
     * @return either {@code "SHA-1"} ({@link #V1}) or {@code "SHA-256"} ({@link #V2})
     */
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = {"phashV1", "phashV2"},
            adaptation = WhatsAppAdaptation.DIRECT)
    public String algorithm() {
        return algorithm;
    }

    /**
     * Returns the literal prefix prepended to the encoded digest.
     *
     * @apiNote
     * The returned prefix is what callers see at the start of every {@code phash} stanza
     * attribute value; it lets the server route the stanza to the matching server-side hash
     * algorithm.
     *
     * @return either {@code "1:"} ({@link #V1}) or {@code "2:"} ({@link #V2})
     */
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = {"phashV1", "phashV2"},
            adaptation = WhatsAppAdaptation.DIRECT)
    public String prefix() {
        return prefix;
    }

    /**
     * Returns whether this version permits Meta AI bot injection into the hashed set.
     *
     * @apiNote
     * Consulted by {@link DevicePhashCalculator#calculate} before evaluating the
     * {@code allowIncludeOpenBot} or {@code allowIncludeTeeBot} branches; V1 returns
     * {@code false} so the bot-injection branch is never taken.
     *
     * @return {@code true} for {@link #V2}, {@code false} for {@link #V1}
     */
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = "phashV2",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean supportsMetaBot() {
        return supportsMetaBot;
    }
}
