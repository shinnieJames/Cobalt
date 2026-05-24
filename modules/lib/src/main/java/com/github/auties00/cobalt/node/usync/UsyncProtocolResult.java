package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.usync.result.UsyncProtocolError;
import com.github.auties00.cobalt.node.usync.result.UsyncProtocolResponse;

/**
 * Sealed sum type of the value a per-user, per-protocol parser returns.
 *
 * @apiNote
 * Pattern-match at either level. Switching first on
 * {@link UsyncProtocolResponse} vs {@link UsyncProtocolError} lets callers
 * route success and error branches separately; switching directly on the
 * concrete success permits (e.g. {@code ContactResult}, {@code DeviceResult})
 * lets call sites that only care about one protocol drop into the typed
 * payload without an intermediate cast.
 *
 * @implNote
 * This implementation is the Cobalt counterpart of the dynamically-shaped
 * objects returned by each parser in {@code WAWebUsync*}: the JS code uses
 * the presence of an {@code errorCode} key to discriminate errors, while
 * Cobalt uses the sealed-interface permits to make the discrimination
 * compile-time exhaustive.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public sealed interface UsyncProtocolResult permits UsyncProtocolResponse, UsyncProtocolError {

}
