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
 * The outbound {@code <iq xmlns="urn:xmpp:whatsapp:push" type="set">}
 * stanza that registers or clears a push-notification channel.
 *
 * @apiNote
 * Built by Cobalt's push-registration path, the counterpart of WA Web's
 * {@code WAWebSetPushConfigJob.setPushConfig}. The caller chooses either
 * a {@link SmaxPushConfigSetSetVariant.Config} payload to register a
 * platform-specific push channel (W3C Push API endpoint for web,
 * APNs/iOS, FCM-style for Android, WNS for Windows, etc.) or a
 * {@link SmaxPushConfigSetSetVariant.Clear} payload to de-register. WA
 * Web emits exactly this stanza after a successful
 * {@code PushManager.subscribe} in
 * {@code WAWebSubscribePushManagerAction}; Cobalt embedders that wire
 * server-side push to their own application use the same RPC.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPushConfigSetRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutPushConfigBaseIQSetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutPushConfigSetSetConfigOrSetClearMixinGroup")
public final class SmaxPushConfigSetRequest implements SmaxOperation.Request {
    /**
     * The exclusive payload variant: either {@code <config>} or
     * {@code <clear>}.
     */
    private final SmaxPushConfigSetSetVariant variant;

    /**
     * Constructs a push-config request.
     *
     * @apiNote
     * Pass a {@link SmaxPushConfigSetSetVariant.Config} to register a
     * platform-specific push channel or a
     * {@link SmaxPushConfigSetSetVariant.Clear} to drop the registration.
     *
     * @param variant the payload variant
     * @throws NullPointerException if {@code variant} is {@code null}
     */
    public SmaxPushConfigSetRequest(SmaxPushConfigSetSetVariant variant) {
        this.variant = Objects.requireNonNull(variant, "variant cannot be null");
    }

    /**
     * Returns the payload variant.
     *
     * @apiNote
     * Exposed for test and audit code; the variant is immutable.
     *
     * @return the {@link SmaxPushConfigSetSetVariant}
     */
    public SmaxPushConfigSetSetVariant variant() {
        return variant;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hard-codes
     * {@code xmlns="urn:xmpp:whatsapp:push"}, {@code type="set"}, and
     * {@code to=s.whatsapp.net} per the
     * {@code WASmaxOutPushConfigSetRequest.makeSetRequest} fixture, then
     * nests {@link SmaxPushConfigSetSetVariant#toNode()} as the sole
     * payload.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPushConfigSetRequest",
            exports = "makeSetRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "urn:xmpp:whatsapp:push")
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
                .content(variant.toNode());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation compares the carried {@link #variant}.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxPushConfigSetRequest) obj;
        return Objects.equals(this.variant, that.variant);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hashes the carried {@link #variant} to stay
     * consistent with {@link #equals(Object)}.
     */
    @Override
    public int hashCode() {
        return Objects.hash(variant);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors the record-like rendering used across
     * the {@code Smax*} stanza family.
     */
    @Override
    public String toString() {
        return "SmaxPushConfigSetRequest[variant=" + variant + ']';
    }
}
