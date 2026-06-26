package com.github.auties00.cobalt.calls2.core.participant;

import java.util.Optional;

/**
 * Enumerates the user-type classification the engine assigns to a call participant.
 *
 * <p>Every participant carries a user type derived from the {@code type} attribute on
 * its membership stanza. The type distinguishes an ordinary human participant
 * ({@link #NORMAL}) from one that has an associated bot ({@link #HAS_BOT}) and from a
 * participant that is itself a bot ({@link #BOT}). The engine reads this from the wire
 * token: an absent or empty token means {@link #NORMAL}, {@code "has-bot"} means
 * {@link #HAS_BOT}, and {@code "bot"} means {@link #BOT}.
 *
 * <p>Each constant carries the {@link #code() integer code} the engine assigns. The
 * engine reserves code {@code 0} as an error sentinel for an unrecognized token; this
 * enum has no constant for that value, and {@link #ofToken(String)} returns
 * {@link Optional#empty()} for any token it cannot classify.
 *
 * @implNote This implementation ports the user-type token mapping
 * ({@code wa_call_cstr_to_user_type}) of the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code call_participant.cc}). The recovered mapping is empty token to {@code 1}
 * (normal), {@code "has-bot"} to {@code 2}, {@code "bot"} to {@code 3}, and any other
 * token to {@code 0} (error); the engine matches {@code "bot"} as the suffix of the
 * interned {@code "has-bot"} string.
 */
public enum CallParticipantUserType {
    /**
     * An ordinary human participant with no associated bot.
     *
     * <p>This is the classification for an absent or empty {@code type} token.
     */
    NORMAL(1, ""),

    /**
     * A participant that has an associated bot.
     */
    HAS_BOT(2, "has-bot"),

    /**
     * A participant that is itself a bot.
     */
    BOT(3, "bot");

    /**
     * The integer code reserved by the engine for an unrecognized user-type token.
     */
    private static final int ERROR_CODE = 0;

    /**
     * The integer code the engine assigns to this user type.
     */
    private final int code;

    /**
     * The wire token that selects this user type, empty for {@link #NORMAL}.
     */
    private final String token;

    /**
     * Constructs a user-type constant bound to its engine code and wire token.
     *
     * @param code  the integer code the engine assigns
     * @param token the wire token that selects this type
     */
    CallParticipantUserType(int code, String token) {
        this.code = code;
        this.token = token;
    }

    /**
     * Returns the integer code the engine assigns to this user type.
     *
     * @return the engine code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the wire token that selects this user type.
     *
     * @return the wire token, empty for {@link #NORMAL}
     */
    public String token() {
        return token;
    }

    /**
     * Returns the user type selected by the given wire token.
     *
     * <p>A {@code null} or empty token resolves to {@link #NORMAL}; {@code "has-bot"} and
     * {@code "bot"} resolve to their constants; any other token yields
     * {@link Optional#empty()}, matching the engine's error sentinel.
     *
     * @param token the wire token to classify, may be {@code null}
     * @return the matching user type, or {@link Optional#empty()} if the token is
     *         unrecognized
     */
    public static Optional<CallParticipantUserType> ofToken(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.of(NORMAL);
        }
        for (var type : values()) {
            if (type.token.equals(token)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the user type whose {@linkplain #code() code} equals the given value.
     *
     * <p>The engine's error sentinel ({@code 0}) and any other unmapped value yield
     * {@link Optional#empty()}.
     *
     * @param code the engine code to resolve
     * @return the matching user type, or {@link Optional#empty()} if no type matches
     */
    public static Optional<CallParticipantUserType> ofCode(int code) {
        if (code == ERROR_CODE) {
            return Optional.empty();
        }
        for (var type : values()) {
            if (type.code == code) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
