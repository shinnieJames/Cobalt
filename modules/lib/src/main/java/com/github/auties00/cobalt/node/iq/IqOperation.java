package com.github.auties00.cobalt.node.iq;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.NodeBuilder;

/**
 * Root sealed type for every typed legacy {@code <iq>} operation modelled in Cobalt.
 *
 * <p>Legacy IQ denotes the older WhatsApp Web pattern that builds an {@code <iq>} stanza
 * directly via the {@code WADeprecatedSendIq.deprecatedSendIq} dispatcher rather than going
 * through the SMAX typed-stanza-builder framework. The pattern predates the SMAX rewrite and
 * remains in use across the {@code WAWeb*Job} module family in the WA Web bundle (unpair,
 * profile-picture upload, push-server settings, media-conn refresh, set-about, ...).
 *
 * <p>Cobalt models every legacy-IQ operation as a pair of top-level types: a final
 * {@code Iq<Op>Request} class implementing {@link Request} and an {@code Iq<Op>Response}
 * sealed interface implementing {@link Response} whose permits enumerate the documented reply
 * variants. The structural shape is identical to the {@code SmaxOperation} hierarchy but the
 * source provenance is a {@code WAWeb*Job} (or matching {@code WADeprecatedWapParser} parser)
 * rather than a {@code WASmax*RPC} dispatcher.
 *
 * <p>The sealed hierarchy permits exactly two participants, {@link Request} and
 * {@link Response}, so every legacy-IQ operation handle is statically classified as one or
 * the other.
 *
 * @apiNote
 * Most Cobalt embedders interact with concrete subtypes (for example
 * {@code IqUnpairDeviceRequest}, {@code IqSetAboutResponse}) rather than this root type; the
 * sealed interface exists so the dispatcher can take a single {@link Request} parameter and
 * so reflective tooling can enumerate the legacy-IQ surface from one entry point.
 */
@WhatsAppWebModule(moduleName = "WADeprecatedSendIq")
public sealed interface IqOperation permits IqOperation.Request, IqOperation.Response {
    /**
     * Outbound side of a legacy-IQ operation, implemented by every concrete
     * {@code Iq<Op>Request} type.
     *
     * @apiNote
     * Callers dispatch a request by passing the typed instance to the client's
     * {@code sendNode(IqOperation.Request)} entry point; the dispatcher invokes
     * {@link #toNode()} to materialise the canonical {@code <iq>} envelope before writing it
     * to the socket. There is no parser hook on this interface; the matching
     * {@code Iq<Op>Response.of(Node, Node)} static is the inverse.
     */
    non-sealed interface Request extends IqOperation {
        /**
         * Builds the outbound IQ stanza for this request.
         *
         * @apiNote
         * Each concrete implementation serialises its typed fields into the canonical
         * {@code <iq>} envelope expected by the relay, including the {@code xmlns},
         * {@code to}, and {@code type} attributes; the returned builder is ready to be sent
         * without further mutation.
         *
         * @implSpec
         * Implementations must return a non-{@code null} builder describing a single
         * {@code <iq>} root node. The builder must not be mutated by the caller before
         * dispatch; the dispatcher reads the builder once and releases it.
         *
         * @return the outbound stanza builder, never {@code null}
         */
        NodeBuilder toNode();
    }

    /**
     * Inbound side of a legacy-IQ operation, implemented by every concrete
     * {@code Iq<Op>Response} sealed interface (or its individual variant classes).
     *
     * @apiNote
     * The type exists purely as the closed counterpart of {@link Request} so the entire
     * legacy-IQ surface can be reasoned about as a single sealed hierarchy; callers obtain
     * concrete variants via each operation's {@code Iq<Op>Response.of(Node, Node)} static.
     */
    non-sealed interface Response extends IqOperation {
    }
}
