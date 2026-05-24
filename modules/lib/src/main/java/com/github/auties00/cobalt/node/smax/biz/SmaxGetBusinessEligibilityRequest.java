package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code GetBusinessEligibility} IQ request stanza for
 * the SMB marketing-messages and Meta-Verified gating bridge.
 *
 * @apiNote
 * Used by Cobalt clients that mirror WA Web's
 * {@code WAWebGetBusinessEligibilityJob.getBusinessEligibility} flow,
 * which is driven by {@code WAWebRefreshBusinessEligibility} on an
 * exponential-backoff loop to refresh the per-broadcast Meta-Verified,
 * marketing-messages and GenAI eligibility surfaces; each feature
 * toggle attribute opts into the matching projection in the reply.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code makeGetBusinessEligibilityRequest} by stamping the static
 * {@code xmlns="w:biz"} envelope and emitting a
 * {@code <features/>} child carrying the three optional toggle
 * attributes; only attributes whose constructor argument is
 * non-{@code null} are emitted.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBizMarketingMessageGetBusinessEligibilityRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutBizMarketingMessageHackBaseIQGetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutBizMarketingMessageBaseIQGetRequestMixin")
public final class SmaxGetBusinessEligibilityRequest implements SmaxOperation.Request {
    /**
     * The optional {@code meta_verified} attribute on the
     * {@code <features/>} child; opts the reply into the
     * Meta-Verified projection.
     */
    private final String featuresMetaVerified;

    /**
     * The optional {@code marketing_messages} attribute on the
     * {@code <features/>} child; opts the reply into the
     * marketing-messages projection.
     */
    private final String featuresMarketingMessages;

    /**
     * The optional {@code genai} attribute on the
     * {@code <features/>} child; opts the reply into the GenAI
     * projection.
     */
    private final String featuresGenai;

    /**
     * The optional {@code from} attribute echoed onto the outbound
     * IQ via the {@code HackBaseIQGetRequestMixin}.
     *
     * @apiNote
     * The active user {@link Jid} is the only legal value;
     * {@code null} omits the attribute, which is the default
     * behavior because the upstream
     * {@code WASmaxBizMarketingMessageGetBusinessEligibilityRPC.sendGetBusinessEligibilityRPC}
     * never propagates an {@code iqFrom} into
     * {@code makeGetBusinessEligibilityRequest}.
     */
    private final Jid fromUserJid;

    /**
     * Constructs a request with all three feature toggles unset and
     * no {@code from} echo.
     *
     * @apiNote
     * Suitable for callers that wish to probe the relay for the
     * default set of features the server chooses to surface, with
     * no opt-in selection.
     */
    public SmaxGetBusinessEligibilityRequest() {
        this(null, null, null, null);
    }

    /**
     * Constructs a request with the three optional feature toggles
     * and no {@code from} echo.
     *
     * @apiNote
     * Matches the default
     * {@code WAWebGetBusinessEligibilityJob.getBusinessEligibility}
     * call site which passes per-feature opt-in flags
     * ({@code checkMarketingMessages}, {@code checkGenAI}) without
     * an explicit {@code from} echo.
     *
     * @param featuresMetaVerified      the optional Meta-Verified
     *                                  toggle attribute; may be
     *                                  {@code null}
     * @param featuresMarketingMessages the optional
     *                                  marketing-messages toggle
     *                                  attribute; may be
     *                                  {@code null}
     * @param featuresGenai             the optional GenAI toggle
     *                                  attribute; may be
     *                                  {@code null}
     */
    public SmaxGetBusinessEligibilityRequest(String featuresMetaVerified,
                   String featuresMarketingMessages,
                   String featuresGenai) {
        this(featuresMetaVerified, featuresMarketingMessages, featuresGenai, null);
    }

    /**
     * Constructs a request with the three optional feature toggles
     * and an optional {@code from} echo.
     *
     * @apiNote
     * Use the {@code fromUserJid} overload when a companion linked
     * device proxies the request on behalf of the active user.
     *
     * @param featuresMetaVerified      the optional Meta-Verified
     *                                  toggle attribute; may be
     *                                  {@code null}
     * @param featuresMarketingMessages the optional
     *                                  marketing-messages toggle
     *                                  attribute; may be
     *                                  {@code null}
     * @param featuresGenai             the optional GenAI toggle
     *                                  attribute; may be
     *                                  {@code null}
     * @param fromUserJid               the optional user
     *                                  {@link Jid} to echo onto the
     *                                  {@code from} attribute; may
     *                                  be {@code null}
     */
    public SmaxGetBusinessEligibilityRequest(String featuresMetaVerified,
                   String featuresMarketingMessages,
                   String featuresGenai,
                   Jid fromUserJid) {
        this.featuresMetaVerified = featuresMetaVerified;
        this.featuresMarketingMessages = featuresMarketingMessages;
        this.featuresGenai = featuresGenai;
        this.fromUserJid = fromUserJid;
    }

    /**
     * Returns the optional Meta-Verified toggle.
     *
     * @return an {@link Optional} carrying the toggle, or empty
     *         when the attribute was omitted
     */
    public Optional<String> featuresMetaVerified() {
        return Optional.ofNullable(featuresMetaVerified);
    }

    /**
     * Returns the optional marketing-messages toggle.
     *
     * @return an {@link Optional} carrying the toggle, or empty
     *         when the attribute was omitted
     */
    public Optional<String> featuresMarketingMessages() {
        return Optional.ofNullable(featuresMarketingMessages);
    }

    /**
     * Returns the optional GenAI toggle.
     *
     * @return an {@link Optional} carrying the toggle, or empty
     *         when the attribute was omitted
     */
    public Optional<String> featuresGenai() {
        return Optional.ofNullable(featuresGenai);
    }

    /**
     * Returns the optional {@code from} echo.
     *
     * @return an {@link Optional} carrying the user {@link Jid}, or
     *         empty when no echo was supplied
     */
    public Optional<Jid> fromUserJid() {
        return Optional.ofNullable(fromUserJid);
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @implNote
     * This implementation composes three WA Web mixins in a single
     * pass: {@code makeGetBusinessEligibilityRequest} stamps the
     * {@code xmlns="w:biz"} envelope and the {@code <features/>}
     * child with selectively-emitted toggle attributes,
     * {@code mergeHackBaseIQGetRequestMixin} stamps {@code to} and
     * the optional {@code from}, and
     * {@code mergeBaseIQGetRequestMixin} stamps {@code type="get"};
     * the {@code id} attribute is appended by Cobalt's send path.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and
     *         the {@code <features/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutBizMarketingMessageGetBusinessEligibilityRequest",
            exports = "makeGetBusinessEligibilityRequest", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutBizMarketingMessageHackBaseIQGetRequestMixin",
            exports = "mergeHackBaseIQGetRequestMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutBizMarketingMessageBaseIQGetRequestMixin",
            exports = "mergeBaseIQGetRequestMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var featuresBuilder = new NodeBuilder()
                .description("features");
        if (featuresMetaVerified != null) {
            featuresBuilder.attribute("meta_verified", featuresMetaVerified);
        }
        if (featuresMarketingMessages != null) {
            featuresBuilder.attribute("marketing_messages", featuresMarketingMessages);
        }
        if (featuresGenai != null) {
            featuresBuilder.attribute("genai", featuresGenai);
        }
        var featuresNode = featuresBuilder.build();
        var builder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:biz")
                .attribute("to", JidServer.user())
                .attribute("type", "get");
        if (fromUserJid != null) {
            builder.attribute("from", fromUserJid);
        }
        return builder.content(featuresNode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGetBusinessEligibilityRequest) obj;
        return Objects.equals(this.featuresMetaVerified, that.featuresMetaVerified)
                && Objects.equals(this.featuresMarketingMessages, that.featuresMarketingMessages)
                && Objects.equals(this.featuresGenai, that.featuresGenai)
                && Objects.equals(this.fromUserJid, that.fromUserJid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(featuresMetaVerified, featuresMarketingMessages, featuresGenai, fromUserJid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SmaxGetBusinessEligibilityRequest[featuresMetaVerified=" + featuresMetaVerified
                + ", featuresMarketingMessages=" + featuresMarketingMessages
                + ", featuresGenai=" + featuresGenai
                + ", fromUserJid=" + fromUserJid + ']';
    }
}
