package com.github.auties00.cobalt.node.smax.util;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;

import java.util.Objects;
import java.util.Optional;

/**
 * Shared envelope-validation helper for the standard
 * {@code <iq from id type="error">} reply produced by every SMAX domain's
 * {@code ClientError}/{@code ServerError} response variant.
 *
 * @apiNote
 * Counterpart of {@link SmaxIqResultResponseMixin} for the error
 * envelope. {@link #validate(Node, Node)} checks the tag, the echoed
 * {@code id}, the {@code from}/{@code to} echo, and asserts
 * {@code type="error"}; {@link #parseError(Node)} pulls the
 * {@code <error code text/>} child into an {@link Envelope} record.
 * Every per-domain {@code WASmaxIn*IQErrorResponseMixin} delegates to
 * this single helper.
 *
 * @implNote
 * This implementation deduplicates eleven byte-identical WA Web modules
 * (one per domain); the {@code @WhatsAppWebModule} list keeps the source
 * manifest pointing at every upstream module.
 */
@WhatsAppWebModule(moduleName = "WASmaxInGroupsIQErrorResponseMixin")
@WhatsAppWebModule(moduleName = "WASmaxInAbPropsIQErrorResponseMixin")
@WhatsAppWebModule(moduleName = "WASmaxInBizAccessTokenIQErrorResponseMixin")
@WhatsAppWebModule(moduleName = "WASmaxInBizCtwaAdAccountIQErrorResponseMixin")
@WhatsAppWebModule(moduleName = "WASmaxInBizLinkingIQErrorResponseMixin")
@WhatsAppWebModule(moduleName = "WASmaxInBizMarketingMessageIQErrorResponseMixin")
@WhatsAppWebModule(moduleName = "WASmaxInBizMsgUserFeedbackIQErrorResponseMixin")
@WhatsAppWebModule(moduleName = "WASmaxInBizSettingsIQErrorResponseMixin")
@WhatsAppWebModule(moduleName = "WASmaxInBlocklistsIQErrorResponseMixin")
@WhatsAppWebModule(moduleName = "WASmaxInBotIQErrorResponseMixin")
@WhatsAppWebModule(moduleName = "WASmaxInBugReportingIQErrorResponseMixin")
public final class SmaxIqErrorResponseMixin {

    /**
     * Refuses instantiation of the static-only utility.
     *
     * @apiNote
     * The class exposes only static helpers; the throwing constructor
     * guards against reflective instantiation.
     */
    private SmaxIqErrorResponseMixin() {
        throw new AssertionError("SmaxIqErrorResponseMixin cannot be instantiated");
    }

    /**
     * Validates that the supplied reply is a well-formed
     * {@code <iq type="error">} echoing the request's {@code id} and
     * {@code to} attributes.
     *
     * @apiNote
     * Called by {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}
     * and {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
     * before they extract the {@code <error>} child; embedders that
     * implement custom error parsers can call this directly to share
     * the same envelope check.
     *
     * @implNote
     * This implementation enforces the four WA Web invariants in order:
     * tag is {@code iq}, type is {@code error}, the request carries an
     * {@code id} (and the reply echoes it), and the reply's {@code from}
     * matches the request's {@code to}.
     *
     * @param reply   the inbound stanza
     * @param request the outbound stanza, used to cross-check the echoed
     *                {@code id} and {@code from} attributes
     * @return {@code true} when {@code reply} is an error envelope
     *         echoing {@code request}'s identifiers; {@code false}
     *         otherwise
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInGroupsIQErrorResponseMixin",
            exports = "parseIQErrorResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInAbPropsIQErrorResponseMixin",
            exports = "parseIQErrorResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBizAccessTokenIQErrorResponseMixin",
            exports = "parseIQErrorResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaAdAccountIQErrorResponseMixin",
            exports = "parseIQErrorResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingIQErrorResponseMixin",
            exports = "parseIQErrorResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageIQErrorResponseMixin",
            exports = "parseIQErrorResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBizMsgUserFeedbackIQErrorResponseMixin",
            exports = "parseIQErrorResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBizSettingsIQErrorResponseMixin",
            exports = "parseIQErrorResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBlocklistsIQErrorResponseMixin",
            exports = "parseIQErrorResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBotIQErrorResponseMixin",
            exports = "parseIQErrorResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBugReportingIQErrorResponseMixin",
            exports = "parseIQErrorResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    public static boolean validate(Node reply, Node request) {
        Objects.requireNonNull(reply, "reply cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        if (!reply.hasDescription("iq")) {
            return false;
        }
        if (!reply.hasAttribute("type", "error")) {
            return false;
        }
        var requestId = request.getAttributeAsString("id").orElse(null);
        if (requestId == null) {
            return false;
        }
        if (!reply.hasAttribute("id", requestId)) {
            return false;
        }
        var requestTo = request.getAttributeAsString("to").orElse(null);
        if (requestTo == null) {
            return false;
        }
        return reply.hasAttribute("from", requestTo);
    }

    /**
     * Extracts the {@code <error code text/>} child carried by an
     * {@code <iq type="error">} envelope.
     *
     * @apiNote
     * Returns {@link Optional#empty()} when the reply has no
     * {@code <error>} child or when the child is missing the
     * {@code code} attribute (or carries a negative code). Used by both
     * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)} and
     * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} to
     * read the {@code (code, text)} pair after envelope validation
     * succeeds.
     *
     * @implNote
     * This implementation treats {@code text} as optional but
     * {@code code} as mandatory; a negative or missing {@code code}
     * triggers {@link Optional#empty()} so the caller can fall through
     * to a different error branch.
     *
     * @param reply the inbound error stanza
     * @return an {@link Optional} carrying the parsed {@link Envelope},
     *         or empty when the {@code <error>} child is missing or
     *         malformed
     * @throws NullPointerException if {@code reply} is {@code null}
     */
    public static Optional<Envelope> parseError(Node reply) {
        Objects.requireNonNull(reply, "reply cannot be null");
        var error = reply.getChild("error").orElse(null);
        if (error == null) {
            return Optional.empty();
        }
        var code = error.getAttributeAsInt("code").orElse(-1);
        if (code < 0) {
            return Optional.empty();
        }
        var text = error.getAttributeAsString("text").orElse(null);
        return Optional.of(new Envelope(code, text));
    }

    /**
     * Container for the {@code (code, text)} pair carried by every
     * {@code <error>} child.
     *
     * @apiNote
     * Surfaced by {@link #parseError(Node)} and consumed by every
     * per-RPC {@code ClientError}/{@code ServerError} factory. The
     * {@code code} is always non-negative; the {@code text} may be
     * absent when the relay omitted it.
     *
     * @param code the numeric error code
     * @param text the optional human-readable error text
     */
    public record Envelope(int code, String text) {
        /**
         * Returns the {@code text} attribute as an {@link Optional}.
         *
         * @apiNote
         * Useful for embedders that prefer idiomatic null-safe access
         * over the raw record accessor.
         *
         * @return an {@link Optional} carrying the text, or empty when
         *         the relay omitted it
         */
        public Optional<String> textAsOptional() {
            return Optional.ofNullable(text);
        }
    }
}
