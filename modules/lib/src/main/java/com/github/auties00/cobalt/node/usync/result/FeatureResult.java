package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.usync.protocol.UsyncFeatureProtocol;

import java.util.Map;

/**
 * Success result of {@code WAWebUsyncFeature.featureParser}.
 *
 * <p>Carries a map from feature key to the relay-reported support status.
 * Keys that the relay omits from the response do not appear in the map;
 * present keys carry the {@code value} attribute verbatim.
 *
 * @implNote WAWebUsyncFeature.featureParser: returns a flat object keyed
 *     by feature name. Cobalt promotes the keys to enum constants for
 *     type-safe lookup.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncFeature")
public final class FeatureResult implements UsyncProtocolResponse {
    /**
     * Map from feature key to the relay-reported support status.
     */
    private final Map<UsyncFeatureProtocol.FeatureQuery, String> features;

    /**
     * Creates a new feature result.
     *
     * @param features the feature map; defaults to an empty map when
     *                 {@code null}
     */
    public FeatureResult(Map<UsyncFeatureProtocol.FeatureQuery, String> features) {
        this.features = features == null ? Map.of() : Map.copyOf(features);
    }

    /**
     * Returns the feature map.
     *
     * @return the map, never {@code null}
     */
    public Map<UsyncFeatureProtocol.FeatureQuery, String> features() {
        return features;
    }
}
