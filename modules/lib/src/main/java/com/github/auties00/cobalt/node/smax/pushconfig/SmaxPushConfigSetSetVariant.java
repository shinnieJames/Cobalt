package com.github.auties00.cobalt.node.smax.pushconfig;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed disjunction of payload variants for
 * {@link SmaxPushConfigSetRequest}.
 *
 * @apiNote
 * The push-config RPC is a two-shape SET: either a
 * {@link Config} registration (with a nested platform-specific
 * {@link SmaxPushConfigSetConfigVariant}) or a {@link Clear}
 * de-registration (optionally scoped to one platform). Mirrors WA Web's
 * {@code WASmaxOutPushConfigSetSetConfigOrSetClearMixinGroup} branch
 * selector.
 */
public sealed interface SmaxPushConfigSetSetVariant
        permits SmaxPushConfigSetSetVariant.Config, SmaxPushConfigSetSetVariant.Clear {

    /**
     * Builds the {@code <config>} or {@code <clear>} child node.
     *
     * @apiNote
     * Invoked by {@link SmaxPushConfigSetRequest#toNode()} to materialise
     * the variant into the outbound stanza.
     *
     * @return the {@link Node}
     */
    Node toNode();

    /**
     * The {@code <config>} variant. Registers a push channel for a
     * specific client family.
     *
     * @apiNote
     * Carries exactly one of the platform-specific config mixins
     * exposed through {@link SmaxPushConfigSetConfigVariant} (FB,
     * Android, Apple, WNS, Enterprise, or Web Push); the caller picks
     * the one matching the embedder's notification platform.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutPushConfigSetSetConfigMixin")
    @WhatsAppWebModule(moduleName = "WASmaxOutPushConfigConfigMixins")
    final class Config implements SmaxPushConfigSetSetVariant {
        /**
         * The platform-specific config payload.
         */
        private final SmaxPushConfigSetConfigVariant config;

        /**
         * Constructs a {@code <config>} variant.
         *
         * @apiNote
         * The supplied {@link SmaxPushConfigSetConfigVariant}
         * determines which platform-specific {@code <config>} shape the
         * relay sees.
         *
         * @param config the platform-specific config
         * @throws NullPointerException if {@code config} is {@code null}
         */
        public Config(SmaxPushConfigSetConfigVariant config) {
            this.config = Objects.requireNonNull(config, "config cannot be null");
        }

        /**
         * Returns the platform-specific config payload.
         *
         * @apiNote
         * Exposed for test and audit code.
         *
         * @return the {@link SmaxPushConfigSetConfigVariant}
         */
        public SmaxPushConfigSetConfigVariant config() {
            return config;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation delegates to the carried
         * {@link SmaxPushConfigSetConfigVariant#toNode()}, mirroring the
         * WA Web {@code mergeSetSetConfigMixin} forwarder.
         */
        @Override
        @WhatsAppWebExport(moduleName = "WASmaxOutPushConfigSetSetConfigMixin",
                exports = "mergeSetSetConfigMixin",
                adaptation = WhatsAppAdaptation.DIRECT)
        public Node toNode() {
            return config.toNode();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the carried config.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Config) obj;
            return Objects.equals(this.config, that.config);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes the carried config.
         */
        @Override
        public int hashCode() {
            return Objects.hash(config);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} stanza family.
         */
        @Override
        public String toString() {
            return "SmaxPushConfigSetSetVariant.Config[config=" + config + ']';
        }
    }

    /**
     * The {@code <clear>} variant. Drops a push registration.
     *
     * @apiNote
     * Set {@link #clearPlatform()} to scope the clear to a single
     * platform (e.g., {@code "web"} to drop only the web subscription
     * while leaving any mobile registration intact); leave it null to
     * drop every registration tied to the account.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutPushConfigSetClearMixin")
    final class Clear implements SmaxPushConfigSetSetVariant {
        /**
         * The optional platform scope.
         *
         * @apiNote
         * One of {@code "fb"}, {@code "apple"}, {@code "android"},
         * {@code "wns"}, {@code "ent"}, {@code "web"}; {@code null}
         * drops every registration.
         */
        private final String clearPlatform;

        /**
         * Constructs a {@code <clear>} variant.
         *
         * @apiNote
         * Pass a non-null {@code clearPlatform} to scope the clear; pass
         * {@code null} to drop every registration tied to the account.
         *
         * @param clearPlatform the optional platform scope
         */
        public Clear(String clearPlatform) {
            this.clearPlatform = clearPlatform;
        }

        /**
         * Returns the optional platform scope.
         *
         * @apiNote
         * When present, the relay clears only the registration matching
         * that platform name; when absent every registration is
         * dropped.
         *
         * @return an {@link Optional} carrying the scope
         */
        public Optional<String> clearPlatform() {
            return Optional.ofNullable(clearPlatform);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation emits a single {@code <clear>} element,
         * adding the {@code platform} attribute only when
         * {@link #clearPlatform} is non-null per the
         * {@code WASmaxOutPushConfigSetClearMixin.mergeSetClearMixin}
         * fixture.
         */
        @Override
        @WhatsAppWebExport(moduleName = "WASmaxOutPushConfigSetClearMixin",
                exports = "mergeSetClearMixin",
                adaptation = WhatsAppAdaptation.DIRECT)
        public Node toNode() {
            var builder = new NodeBuilder()
                    .description("clear");
            if (clearPlatform != null) {
                builder.attribute("platform", clearPlatform);
            }
            return builder.build();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the optional platform scope.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Clear) obj;
            return Objects.equals(this.clearPlatform, that.clearPlatform);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes the optional platform scope.
         */
        @Override
        public int hashCode() {
            return Objects.hash(clearPlatform);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} stanza family.
         */
        @Override
        public String toString() {
            return "SmaxPushConfigSetSetVariant.Clear[clearPlatform=" + clearPlatform + ']';
        }
    }
}
