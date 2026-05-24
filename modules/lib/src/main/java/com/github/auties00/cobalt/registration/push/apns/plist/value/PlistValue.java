package com.github.auties00.cobalt.registration.push.apns.plist.value;

/**
 * Sealed value model for one node of an Apple property list.
 *
 * @apiNote
 * Used to represent a parsed plist tree for the APNS connect handshake;
 * pattern-match exhaustively over the eight permitted variants
 * ({@link PlistDictionaryValue}, {@link PlistArrayValue},
 * {@link PlistStringValue}, {@link PlistDataValue},
 * {@link PlistIntegerValue}, {@link PlistFloatingPointValue},
 * {@link PlistBooleanValue}, {@link PlistDateValue}) when consuming a
 * decoded value. The closure mirrors the eight concrete types defined
 * by Apple's {@code PropertyList-1.0.dtd}.
 *
 * @implNote
 * This implementation is closed at compile time so the binary plist
 * parser and serializer can switch on the concrete type without a
 * default branch; every variant is an immutable {@code record}.
 */
public sealed interface PlistValue permits
        PlistDictionaryValue,
        PlistArrayValue,
        PlistStringValue,
        PlistDataValue,
        PlistIntegerValue,
        PlistFloatingPointValue,
        PlistBooleanValue,
        PlistDateValue {
}
