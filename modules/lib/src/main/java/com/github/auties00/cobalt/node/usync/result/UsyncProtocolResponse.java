package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;

/**
 * Sealed family of every protocol-specific <em>success</em> variant that
 * a USync per-user, per-protocol parser can return.
 *
 * <p>Sibling permit of {@link UsyncProtocolError} under
 * {@link UsyncProtocolResult}: a parser either yields an error or one of
 * the eleven concrete shapes permitted here.
 *
 * @implNote each {@code WAWebUsync*Protocol.parser} in WhatsApp Web
 *     returns its protocol-specific success shape (or an error). The
 *     eleven permits below correspond one-to-one with those eleven
 *     success shapes.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public sealed interface UsyncProtocolResponse extends UsyncProtocolResult permits
        BotProfileResult,
        BusinessResult,
        ContactResult,
        DeviceResult,
        DisappearingModeResult,
        FeatureResult,
        LidResult,
        PictureResult,
        StatusResult,
        TextStatusResult,
        UsernameResult {
}
