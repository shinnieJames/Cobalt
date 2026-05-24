package com.github.auties00.cobalt.node.smax.util;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;

import java.util.Objects;

/**
 * Shared envelope-validation helper for the legacy
 * {@code <iq id type="error">} reply produced by SMAX domains that pre-date
 * the standard echoed-{@code from} contract.
 *
 * @apiNote
 * Two WA Web domains still use this relaxed envelope:
 * {@code WASmaxInBizCtwaNativeAdDeprecatedIQErrorResponseOptionalFromMixin}
 * (BizCtwaNativeAd, consumed by
 * {@code WASmaxInBizCtwaNativeAdUploadAdMediaResponseError}) and
 * {@code WASmaxInPrivacyDeprecatedIQErrorResponseOptionalFromMixin}
 * (Privacy, consumed by
 * {@code WASmaxInPrivacyGetContactBlacklistResponseError}). Both make
 * the {@code from} attribute optional and accept either a domain JID
 * ({@code s.whatsapp.net}, {@code g.us}, {@code call}) or a user JID
 * (phone, LID, interop, msgr, or bot); the relay never has to echo the
 * request's {@code to}.
 *
 * @implNote
 * This implementation collapses both byte-identical WA Web clones into a
 * single helper; the {@code @WhatsAppWebModule} list keeps the source
 * manifest pointing at both upstream modules.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizCtwaNativeAdDeprecatedIQErrorResponseOptionalFromMixin")
@WhatsAppWebModule(moduleName = "WASmaxInPrivacyDeprecatedIQErrorResponseOptionalFromMixin")
public final class SmaxDeprecatedIqErrorResponseOptionalFromMixin {

    /**
     * Refuses instantiation of the static-only utility.
     *
     * @apiNote
     * The class exposes only static helpers; the throwing constructor
     * guards against reflective instantiation.
     */
    private SmaxDeprecatedIqErrorResponseOptionalFromMixin() {
        throw new AssertionError("SmaxDeprecatedIqErrorResponseOptionalFromMixin cannot be instantiated");
    }

    /**
     * Validates that the supplied reply is a well-formed legacy
     * {@code <iq type="error">} echoing the request's {@code id}
     * attribute.
     *
     * @apiNote
     * Returns {@code true} when the reply has the {@code iq} tag,
     * carries {@code type="error"}, echoes the request's {@code id}
     * verbatim, and either omits the {@code from} attribute or carries
     * one that parses as a domain or user JID.
     *
     * @implNote
     * This implementation hand-rolls the validation rather than
     * delegating to {@link SmaxIqErrorResponseMixin#validate(Node, Node)}
     * because the legacy envelope makes {@code from} optional and does
     * not require an echo of {@code request.to}.
     *
     * @param reply   the inbound stanza
     * @param request the outbound stanza, used to cross-check the echoed
     *                {@code id} attribute
     * @return {@code true} when {@code reply} is a legacy error envelope
     *         echoing {@code request}'s {@code id}; {@code false}
     *         otherwise
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdDeprecatedIQErrorResponseOptionalFromMixin",
            exports = "parseDeprecatedIQErrorResponseOptionalFromMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInPrivacyDeprecatedIQErrorResponseOptionalFromMixin",
            exports = "parseDeprecatedIQErrorResponseOptionalFromMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static boolean validate(Node reply, Node request) {
        Objects.requireNonNull(reply, "reply cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        if (!reply.hasDescription("iq")) {
            return false;
        }
        var requestId = request.getAttributeAsString("id").orElse(null);
        if (requestId == null) {
            return false;
        }
        if (!reply.hasAttribute("id", requestId)) {
            return false;
        }
        var from = reply.getAttributeAsString("from").orElse(null);
        if (from != null && !isDomainOrUserJid(from)) {
            return false;
        }
        return reply.hasAttribute("type", "error");
    }

    /**
     * Returns whether the supplied attribute value parses as a domain JID
     * or as a user JID.
     *
     * @apiNote
     * Used by {@link #validate(Node, Node)} to enforce the WA Web
     * {@code DOMAINJID_USERJID} optional-{@code from} contract: the
     * relay may omit the attribute entirely, set it to a domain JID
     * ({@code s.whatsapp.net}, {@code g.us}, or {@code call}), or set it
     * to any user JID (phone, LID, interop, msgr, or bot).
     *
     * @implNote
     * This implementation catches every {@link RuntimeException} from
     * {@link Jid#of(String)} so a malformed attribute resolves to
     * {@code false} rather than propagating; the domain-JID branch
     * requires a missing user component while the user-JID branch
     * requires a present user component.
     *
     * @param value the raw attribute string
     * @return {@code true} when {@code value} is a domain or user JID
     */
    private static boolean isDomainOrUserJid(String value) {
        Jid jid;
        try {
            jid = Jid.of(value);
        } catch (RuntimeException e) {
            return false;
        }
        var server = jid.server();
        if (!jid.hasUser()
                && (server.equals(JidServer.user())
                || server.equals(JidServer.groupOrCommunity())
                || server.equals(JidServer.call()))) {
            return true;
        }
        return jid.hasUser();
    }
}
