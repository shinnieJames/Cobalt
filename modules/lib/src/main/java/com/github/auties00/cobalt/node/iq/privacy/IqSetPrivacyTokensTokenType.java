package com.github.auties00.cobalt.node.iq.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

/**
 * Closed set of token-type discriminators recognised by the relay on the legacy
 * {@code <iq xmlns="privacy" type="set"><tokens>} surface.
 *
 * @apiNote
 * Use when constructing an {@link IqSetPrivacyTokensRequest}. The token-type discriminator selects
 * which class of pre-shared privacy token the relay should mint and register against the supplied
 * peer JID.
 *
 * @implNote
 * This implementation mirrors the {@code TokenType} enum exported by
 * {@code WAWebSetPrivacyTokensJob}; WA Web ships only the {@code trusted_contact} value today.
 */
@WhatsAppWebModule(moduleName = "WAWebSetPrivacyTokensJob")
public enum IqSetPrivacyTokensTokenType {
    /**
     * The {@code trusted_contact} token type.
     *
     * @apiNote
     * Issues a trusted-contact (TC) token for the supplied peer. WA Web's caller
     * {@code WAWebSendTcTokenChatAction.sendTcToken} mints one of these on the first reply to a
     * peer and again whenever the peer's device identity changes
     * ({@code WAWebSendTcTokenWhenDeviceIdentityChange}), and the token gates downstream
     * call/messages reputation features that depend on cross-device trust pinning.
     */
    TRUSTED_CONTACT("trusted_contact");

    /**
     * The wire token emitted in the {@code type} attribute of each {@code <token>} grandchild.
     */
    private final String wire;

    /**
     * Constructs a token-type constant from its wire token.
     *
     * @apiNote
     * Constructor is package-private as enum constants are the only producers.
     *
     * @param wire the wire token; never {@code null}
     */
    IqSetPrivacyTokensTokenType(String wire) {
        this.wire = wire;
    }

    /**
     * Returns the wire token for this token type.
     *
     * @apiNote
     * Use when serialising a {@code <token type=...>} attribute on an
     * {@link IqSetPrivacyTokensRequest}.
     *
     * @return the wire token; never {@code null}
     */
    public String wire() {
        return wire;
    }
}
