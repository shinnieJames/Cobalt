package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;

/**
 * Sealed family of every protocol-specific success variant returned by a
 * per-user, per-protocol USync parser.
 *
 * @apiNote
 * Sibling permit of {@link UsyncProtocolError} under
 * {@link UsyncProtocolResult}. Cobalt embedders never construct these
 * directly: instances are produced by the USync response dispatcher in
 * {@code WAWebUsync.m} (one parser per {@code <user>} child tag) and surfaced
 * through {@link UsyncUserResult#getProtocolResult(String)}. Switch on this
 * type when a single call site asks for several protocols at once and needs to
 * route each result without an intermediate cast.
 *
 * @implNote
 * This implementation enumerates exactly the eleven {@code WAWebUsync*}
 * protocol modules that ship a {@code *Parser} export today
 * ({@code WAWebUsyncBotProfile}, {@code WAWebUsyncBusiness},
 * {@code WAWebUsyncContact}, {@code WAWebUsyncDevice},
 * {@code WAWebUsyncDisappearingMode}, {@code WAWebUsyncFeature},
 * {@code WAWebUsyncLid}, {@code WAWebUsyncPicture},
 * {@code WAWebUsyncStatus}, {@code WAWebUsyncTextStatus},
 * {@code WAWebUsyncUsername}). Adding a twelfth parser on the WA side requires
 * extending this permits list to keep the {@code switch} pattern matching
 * exhaustive.
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
