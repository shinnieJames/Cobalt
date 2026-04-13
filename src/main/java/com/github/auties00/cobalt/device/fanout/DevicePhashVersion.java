package com.github.auties00.cobalt.device.fanout;

/**
 * Participant hash (phash) version for group message verification.
 *
 * @implNote WAWebPhashUtils: defines phashV1 (SHA-1) and phashV2 (SHA-256) algorithms
 * for calculating participant hashes used in group message stanzas.
 */
public enum DevicePhashVersion {

    /**
     * Version 1: SHA-1 based phash with "1:" prefix.
     *
     * @implNote WAWebPhashUtils.phashV1: legacy format, does not support Meta AI bot injection.
     * Uses {@code asUserWidOrThrow(e).toString({legacy: true})} for JID formatting.
     */
    V1("SHA-1", "1:", false),

    /**
     * Version 2: SHA-256 based phash with "2:" prefix.
     *
     * @implNote WAWebPhashUtils.phashV2: current format, supports open and TEE Meta AI bot
     * injection for groups. Uses {@code e.toString({legacy: true, formatFull: true})} for
     * JID formatting, which includes agent and device components.
     */
    V2("SHA-256", "2:", true);

    /**
     * The hash algorithm name (e.g., "SHA-1", "SHA-256").
     */
    private final String algorithm;

    /**
     * The version prefix (e.g., "1:", "2:").
     */
    private final String prefix;

    /**
     * Whether this version supports Meta AI bot injection.
     */
    private final boolean supportsMetaBot;

    /**
     * Constructs a new phash version with the given algorithm, prefix, and bot support flag.
     *
     * @param algorithm    the hash algorithm name
     * @param prefix       the version prefix string
     * @param supportsMetaBot whether this version supports Meta AI bot injection
     * @implNote WAWebPhashUtils: phashV1 uses SHA-1 with "1:" prefix, phashV2 uses SHA-256
     * with "2:" prefix. Bot injection is only supported in phashV2.
     */
    DevicePhashVersion(String algorithm, String prefix, boolean supportsMetaBot) {
        this.algorithm = algorithm;
        this.prefix = prefix;
        this.supportsMetaBot = supportsMetaBot;
    }

    /**
     * Returns the hash algorithm name for this version.
     *
     * @return the algorithm name (e.g., "SHA-1", "SHA-256")
     * @implNote WAWebPhashUtils: phashV1 uses {@code crypto.subtle.digest({name: "SHA-1"})},
     * phashV2 uses {@code WACryptoSha256.sha256()}.
     */
    public String algorithm() {
        return algorithm;
    }

    /**
     * Returns the prefix string for this version.
     *
     * @return the prefix (e.g., "1:", "2:")
     * @implNote WAWebPhashUtils: phashV1 returns "1:" + base64, phashV2 returns "2:" + base64.
     */
    public String prefix() {
        return prefix;
    }

    /**
     * Returns whether this version supports Meta AI bot injection.
     *
     * @return {@code true} if Meta AI bot can be injected
     * @implNote WAWebPhashUtils: only phashV2 supports bot injection via
     * WAWebBotGroupGatingUtils checks.
     */
    public boolean supportsMetaBot() {
        return supportsMetaBot;
    }
}
