package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.usync.protocol.UsyncFeatureProtocol;

import java.util.Map;

/**
 * Success result of the {@code WAWebUsyncFeature.featureParser} parser.
 *
 * @apiNote
 * Surfaced by USync queries that include
 * {@code UsyncQuery.withFeaturesProtocol(...)}; WA Web callers include the
 * {@code WAWebDebugUsync} VoIP-feature probe and the background contact sync,
 * both of which ask the relay which per-peer feature flags are supported.
 * Keys that the relay omits from the response do not appear in the map; keys
 * that are present carry the {@code value} attribute verbatim (the wire
 * value, typically {@code "1"} for supported).
 *
 * @implNote
 * This implementation accepts only the eleven feature keys enumerated in
 * {@code WAWebUsyncFeature}'s internal dictionary ({@code document},
 * {@code encrypt}, {@code encrypt_blist}, {@code encrypt_contact},
 * {@code encrypt_group_gen2}, {@code encrypt_image}, {@code encrypt_location},
 * {@code encrypt_url}, {@code encrypt_v2}, {@code voip}, {@code multi_agent})
 * because the JS parser silently ignores any child element whose tag is not
 * in the dictionary. The {@link UsyncFeatureProtocol.FeatureQuery} enum
 * mirrors that dictionary.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncFeature")
public final class FeatureResult implements UsyncProtocolResponse {
    /**
     * Per-feature support map keyed by
     * {@link UsyncFeatureProtocol.FeatureQuery} and valued by the raw
     * {@code value} attribute string returned by the relay.
     */
    private final Map<UsyncFeatureProtocol.FeatureQuery, String> features;

    /**
     * Creates a new feature result.
     *
     * @apiNote
     * Instantiated by the feature parser; embedders do not call this
     * directly.
     *
     * @param features the per-feature map, or {@code null} for an empty map
     */
    public FeatureResult(Map<UsyncFeatureProtocol.FeatureQuery, String> features) {
        this.features = features == null ? Map.of() : Map.copyOf(features);
    }

    /**
     * Returns the per-feature support map.
     *
     * @apiNote
     * Each entry's value is the raw {@code value} attribute as a string
     * (typically {@code "1"} for supported). A missing key means the relay
     * did not return that feature for this peer, not that the feature is
     * unsupported.
     *
     * @return the feature map, never {@code null}
     */
    public Map<UsyncFeatureProtocol.FeatureQuery, String> features() {
        return features;
    }
}
