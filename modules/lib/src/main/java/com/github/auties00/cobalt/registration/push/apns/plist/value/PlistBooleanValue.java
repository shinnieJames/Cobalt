package com.github.auties00.cobalt.registration.push.apns.plist.value;

/**
 * Plist boolean leaf, the binary plist {@code 0x08} (false) and
 * {@code 0x09} (true) markers.
 *
 * @apiNote
 * Used wherever an APNS plist payload encodes a true/false flag (for
 * example the {@code APNSEnvironment} sandbox indicator on the connect
 * handshake).
 *
 * @implNote
 * This implementation is a value-typed {@code record} carrying the
 * primitive {@code boolean} directly to keep the parsed tree
 * allocation-light.
 *
 * @param value the boolean
 */
public record PlistBooleanValue(boolean value) implements PlistValue {
}
