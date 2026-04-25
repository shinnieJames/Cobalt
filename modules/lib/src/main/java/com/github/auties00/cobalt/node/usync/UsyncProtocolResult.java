package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.usync.result.UsyncProtocolError;
import com.github.auties00.cobalt.node.usync.result.UsyncProtocolResponse;

/**
 * Sealed sum type for the value a USync per-user, per-protocol parser
 * returns.
 *
 * <p>Splits into two permits:
 * <ul>
 *   <li>{@link UsyncProtocolResponse} — every protocol-specific success
 *       variant (the eleven concrete success types under
 *       {@link com.github.auties00.cobalt.node.usync.result}). Itself a
 *       sealed interface so callers can switch over the response branch
 *       without re-handling errors.</li>
 *   <li>{@link UsyncProtocolError} — the shared "the relay returned an
 *       error for this user/protocol pair" variant.</li>
 * </ul>
 *
 * <p>Callers can pattern-match at either level:
 *
 * <pre>{@code
 * // Branch on success vs error first, then narrow on success
 * switch (result) {
 *     case UsyncProtocolError e    -> handleError(e);
 *     case UsyncProtocolResponse r -> handleResponse(r);
 * }
 *
 * // Or skip straight to a specific protocol
 * switch (result) {
 *     case ContactResult c      -> handleContact(c);
 *     case DeviceResult d       -> handleDevices(d);
 *     case UsyncProtocolError e -> handleError(e);
 *     default                   -> { /* other protocols * / }
 * }
 * }</pre>
 *
 * @implNote each {@code WAWebUsync*Protocol.parser} in WhatsApp Web
 *     returns either {@code {errorCode, errorText}} or a
 *     protocol-specific shape; this two-level sealed hierarchy mirrors
 *     that union with a clean error / response split.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public sealed interface UsyncProtocolResult permits UsyncProtocolResponse, UsyncProtocolError {
}
