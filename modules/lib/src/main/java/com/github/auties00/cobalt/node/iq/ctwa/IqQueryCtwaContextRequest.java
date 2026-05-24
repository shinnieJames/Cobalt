package com.github.auties00.cobalt.node.iq.ctwa;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Objects;

/**
 * Outbound {@code <iq xmlns="fb:thrift_iq" type="get">} stanza requesting the relay-side
 * context record for a Click-To-WhatsApp (CTWA) ad click.
 *
 * @apiNote
 * Used by the CTWA chat-open flow: when a user taps a "Chat on WhatsApp" ad on Facebook or
 * Instagram, the ad funnel hands the client a {@code (account_number, code,
 * expected_source_url)} triple identifying the ad creative; this request asks the relay to
 * resolve that triple to the headline, body, thumbnail, and (when integrated) WAMO automated
 * greeting message that the chat composer should render. WA Web invokes it from
 * {@code WAWebBizQueryCtwaContextBridge.fetchCtwaContextData}.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryCtwaContextJob")
public final class IqQueryCtwaContextRequest implements IqOperation.Request {
    /**
     * Business account's phone number as a legacy-formatted JID string.
     *
     * @apiNote
     * WA Web builds this via {@code WAWebWidFactory.createWid(...).toString({legacy:true})};
     * Cobalt accepts a pre-formatted string so callers may use whichever JID factory they
     * already have wired up.
     */
    private final String accountNumber;

    /**
     * CTWA redirect code carried by the originating ad funnel.
     */
    private final String code;

    /**
     * Expected source URL the client received from the ad funnel.
     *
     * @apiNote
     * The relay echoes this back to confirm anti-spoofing: a mismatch between the URL the
     * relay knows for {@code (code, accountNumber)} and the URL the client received causes
     * the relay to refuse the query.
     */
    private final String expectedSourceUrl;

    /**
     * Constructs a new query-CTWA-context request.
     *
     * @apiNote
     * All three arguments come from the ad funnel; the request fails relay-side if any of
     * them is empty or otherwise fails the relay's anti-spoofing check.
     *
     * @param accountNumber     the business phone (legacy JID string)
     * @param code              the redirect code
     * @param expectedSourceUrl the expected source URL
     * @throws NullPointerException if any argument is {@code null}
     */
    public IqQueryCtwaContextRequest(String accountNumber, String code, String expectedSourceUrl) {
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber cannot be null");
        this.code = Objects.requireNonNull(code, "code cannot be null");
        this.expectedSourceUrl = Objects.requireNonNull(expectedSourceUrl, "expectedSourceUrl cannot be null");
    }

    /**
     * Returns the business phone (legacy JID string).
     *
     * @return the account number, never {@code null}
     */
    public String accountNumber() {
        return accountNumber;
    }

    /**
     * Returns the CTWA redirect code.
     *
     * @return the code, never {@code null}
     */
    public String code() {
        return code;
    }

    /**
     * Returns the expected source URL.
     *
     * @return the URL, never {@code null}
     */
    public String expectedSourceUrl() {
        return expectedSourceUrl;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Produces a {@code <iq xmlns="fb:thrift_iq" type="get">} envelope addressed to
     * {@link JidServer#user()} and wrapping three grandchildren in fixed order:
     * {@code <account_number>}, {@code <code>}, {@code <expected_source_url>}.
     *
     * @implNote
     * This implementation omits the {@code smax_id="CtwaGetContext"} attribute that WA Web
     * stamps on the envelope; the relay does not require it for legacy-IQ dispatch and the
     * value is not surfaced anywhere in Cobalt's stanza model.
     *
     * @return a {@link NodeBuilder} carrying the {@code <iq>} envelope and the three
     *         grandchildren
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryCtwaContextJob",
            exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var accountNumberNode = new NodeBuilder()
                .description("account_number")
                .content(accountNumber)
                .build();
        var codeNode = new NodeBuilder()
                .description("code")
                .content(code)
                .build();
        var expectedSourceUrlNode = new NodeBuilder()
                .description("expected_source_url")
                .content(expectedSourceUrl)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "fb:thrift_iq")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(accountNumberNode, codeNode, expectedSourceUrlNode);
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
        var that = (IqQueryCtwaContextRequest) obj;
        return Objects.equals(this.accountNumber, that.accountNumber)
                && Objects.equals(this.code, that.code)
                && Objects.equals(this.expectedSourceUrl, that.expectedSourceUrl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(accountNumber, code, expectedSourceUrl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqQueryCtwaContextRequest[accountNumber=" + accountNumber
                + ", code=" + code
                + ", expectedSourceUrl=" + expectedSourceUrl + ']';
    }
}
