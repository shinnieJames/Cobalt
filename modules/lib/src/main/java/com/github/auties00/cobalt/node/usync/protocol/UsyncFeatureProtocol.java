package com.github.auties00.cobalt.node.usync.protocol;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncUser;
import com.github.auties00.cobalt.node.usync.result.FeatureResult;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * USync {@code feature} protocol descriptor.
 *
 * @apiNote
 * Asks the relay which of the named features each peer supports. Used by
 * VoIP capability checks (see {@code WAWebDebugUsync}, which queries the
 * {@code voip} feature). The {@code <feature>} query element carries one
 * empty child per requested feature key; the response carries a {@code value}
 * attribute per supported key.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncFeature")
public final class UsyncFeatureProtocol implements UsyncProtocol {
    /**
     * Wire literal for the protocol tag name.
     */
    public static final String NAME = "feature";

    /**
     * Features the relay is asked to report on; never empty by
     * construction.
     */
    private final List<FeatureQuery> queries;

    /**
     * Builds a feature-protocol descriptor for the given queries.
     *
     * @apiNote
     * Pass at least one feature key; an empty list is rejected with
     * {@link IllegalArgumentException}, matching the JS
     * {@code err("must specify at least one query")} throw.
     *
     * @param queries the features to request
     * @throws NullPointerException     if {@code queries} is {@code null}
     * @throws IllegalArgumentException if {@code queries} is empty
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncFeature",
            exports = "USyncFeaturesProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncFeatureProtocol(List<FeatureQuery> queries) {
        Objects.requireNonNull(queries, "queries cannot be null");
        if (queries.isEmpty()) {
            throw new IllegalArgumentException("must specify at least one query");
        }
        this.queries = List.copyOf(queries);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncFeature",
            exports = "USyncFeaturesProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits one empty child per requested feature key,
     * keyed by {@link FeatureQuery#wireValue()}; the JS module ships the
     * same shape pre-built in its {@code s} frozen-object dictionary so the
     * Cobalt path stays closer to the request data.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncFeature",
            exports = "USyncFeaturesProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        var children = queries.stream()
                .map(q -> new NodeBuilder().description(q.wireValue()).build())
                .toList();
        return new NodeBuilder().description(NAME).content(children).build();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@link Optional#empty()} because
     * the feature protocol has no per-user payload on the request side,
     * matching the JS {@code null} return in
     * {@code USyncFeaturesProtocol.getUserElement}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncFeature",
            exports = "USyncFeaturesProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation walks every {@link FeatureQuery} constant rather
     * than just the requested ones, mirroring the JS
     * {@code Object.keys(s).forEach(...)} pass; the relay is allowed to
     * return more keys than were asked for and the parser preserves
     * whichever ones carry a {@code value} attribute.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncFeature",
            exports = "featureParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncProtocolResult parseUserResult(Node child) {
        if (!child.hasDescription(NAME)) {
            throw new IllegalStateException("expected <" + NAME + ">, got <" + child.description() + ">");
        }
        var error = UsyncContactProtocol.parseError(child);
        if (error.isPresent()) {
            return error.get();
        }
        var values = new HashMap<FeatureQuery, String>();
        for (var key : FeatureQuery.values()) {
            child.getChild(key.wireValue()).ifPresent(c ->
                    c.getAttributeAsString("value").ifPresent(v -> values.put(key, v)));
        }
        return new FeatureResult(values);
    }

    /**
     * Enumerates the feature keys the {@code <feature>} query understands.
     *
     * @apiNote
     * Constants mirror the eleven keys hardcoded in the JS {@code s}
     * dictionary; the relay ignores keys it does not recognise.
     */
    @WhatsAppWebModule(moduleName = "WAWebUsyncFeature")
    public enum FeatureQuery {
        /**
         * Document-message support.
         */
        DOCUMENT("document"),
        /**
         * Generic encryption support.
         */
        ENCRYPT("encrypt"),
        /**
         * Encrypted block-list support.
         */
        ENCRYPT_BLIST("encrypt_blist"),
        /**
         * Encrypted contact-card support.
         */
        ENCRYPT_CONTACT("encrypt_contact"),
        /**
         * Encrypted group v2 support.
         */
        ENCRYPT_GROUP_GEN2("encrypt_group_gen2"),
        /**
         * Encrypted image support.
         */
        ENCRYPT_IMAGE("encrypt_image"),
        /**
         * Encrypted location-share support.
         */
        ENCRYPT_LOCATION("encrypt_location"),
        /**
         * Encrypted URL support.
         */
        ENCRYPT_URL("encrypt_url"),
        /**
         * Encryption v2 support.
         */
        ENCRYPT_V2("encrypt_v2"),
        /**
         * VoIP signalling support.
         */
        VOIP("voip"),
        /**
         * Multi-agent (CRM) support.
         */
        MULTI_AGENT("multi_agent");

        /**
         * Literal tag name on the wire.
         */
        private final String wireValue;

        /**
         * Binds a new constant to its wire literal.
         *
         * @param wireValue the literal tag name on the wire
         */
        FeatureQuery(String wireValue) {
            this.wireValue = wireValue;
        }

        /**
         * Returns the literal tag name on the wire.
         *
         * @apiNote
         * Used by {@link #buildQueryElement()} to name the per-feature
         * empty child and by {@link #parseUserResult(Node)} to look up the
         * matching response element.
         *
         * @return the wire literal
         */
        public String wireValue() {
            return wireValue;
        }
    }
}
