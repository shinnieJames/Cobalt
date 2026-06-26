package com.github.auties00.cobalt.calls2.common;

import java.util.Optional;

/**
 * Enumerates the value-type codes a voip-param schema descriptor carries for one tunable.
 *
 * <p>Every tunable in the voip-param catalogue has a fixed value type, encoded as a single
 * byte at offset {@code +6} of the engine's parameter-entry descriptor. That byte tells the
 * deserializer how to read the value out of the JSON document and how the rate-control rule
 * engine must encode an override for it. This enum reproduces the five descriptor codes:
 * {@link #INTEGER} is a signed integer whose byte width is given separately by the
 * descriptor's value length (one, two, four, or eight bytes); {@link #FLOAT} is a four-byte
 * float; {@link #STRING} is a fixed-width character buffer whose byte capacity is the
 * descriptor's value length; {@link #ARRAY} is a homogeneous array whose per-element width
 * and element type live in the descriptor's array fields; and {@link #ARRAY_COUNT} is the
 * count marker that pairs immediately after an {@link #ARRAY} entry, carrying the array's
 * runtime element count.
 *
 * <p>The wire codes are contiguous from {@code 1} to {@code 5}: {@code 1} {@link #INTEGER},
 * {@code 2} {@link #FLOAT}, {@code 3} {@link #STRING}, {@code 4} {@link #ARRAY}, {@code 5}
 * {@link #ARRAY_COUNT}. Code {@code 0} is not a valid value type.
 *
 * @implNote This implementation ports the value-type byte at offset {@code +6} of the
 * {@code voip_param_entry} (the {@code reg_param_entry_impl} descriptor) as decoded by the
 * scalar formatter {@code param_to_str_from_buf_with_type} ({@code func[10918]}) and the
 * array formatter {@code print_one_voip_arr_param} ({@code func[10921]}) in
 * {@code voip_param_internal.cc} of the wa-voip WASM module {@code O4cDmmXP6rI}. The scalar
 * formatter dispatches code {@code 1} to a width-keyed signed-integer load
 * ({@code i64.load8_s}/{@code load16_s}/{@code load32_s}/{@code load} for width
 * {@code 1}/{@code 2}/{@code 4}/{@code 8}, integer format string at memory {@code 555026}),
 * code {@code 2} to a single {@code f32.load} (float format string at {@code 445002}; the
 * following {@code f64.promote_f32} is only the C vararg widening, so there is no eight-byte
 * double path), and code {@code 3} to formatting the value buffer as a {@code char} array of
 * the descriptor's value length (string format string at {@code 937716}); any other code
 * hits an unknown-type assertion. The array formatter handles code {@code 4} and reads the
 * runtime element count from a paired following entry whose type is code {@code 5},
 * validating it against the capacity {@code value_length / element_width}. Semantic boolean
 * toggles in the live descriptor table are integer-backed fields of width {@code 1}, code
 * {@code 1}, not a distinct type code.
 */
public enum VoipParamType {
    /**
     * A signed integer whose byte width ({@code 1}, {@code 2}, {@code 4}, or {@code 8}) is
     * carried by the descriptor's value-length field, keyed under code {@code 1}.
     */
    INTEGER(1),

    /**
     * A four-byte float, keyed under code {@code 2}.
     */
    FLOAT(2),

    /**
     * A fixed-width text buffer, keyed under code {@code 3}.
     */
    STRING(3),

    /**
     * A homogeneous array whose per-element width and element type live in the
     * descriptor's array fields, keyed under code {@code 4}.
     */
    ARRAY(4),

    /**
     * The count marker that pairs immediately after an {@link #ARRAY} entry to carry the
     * array's runtime element count, keyed under code {@code 5}.
     */
    ARRAY_COUNT(5),

    /**
     * The type of a wire field with no recovered native descriptor, keyed under code {@code 0}.
     *
     * <p>A {@code <voip_settings>} leaf that resolves to no modelled {@link VoipParamKey} is carried
     * by an {@linkplain VoipParamKey#unknown(String) unknown} key whose type is this constant. It is not one of the five engine
     * descriptor codes ({@code 1} to {@code 5}); it marks a value retained by wire path whose engine
     * value type is unknown, so it is read back through whichever {@link VoipParams} accessor coerces
     * the raw value the deserializer stored.
     */
    UNKNOWN(0);

    /**
     * The integer value-type code the engine stores in the descriptor's type byte.
     */
    private final int code;

    /**
     * Constructs a value-type constant bound to its engine type code.
     *
     * @param code the integer value-type code the engine stores
     */
    VoipParamType(int code) {
        this.code = code;
    }

    /**
     * Returns the integer value-type code the engine stores in the descriptor's type byte.
     *
     * @return the engine value-type code
     */
    public int code() {
        return code;
    }

    /**
     * Returns whether this type is a scalar that holds a single value.
     *
     * <p>The scalar types are {@link #INTEGER}, {@link #FLOAT}, and
     * {@link #STRING}; {@link #ARRAY} and {@link #ARRAY_COUNT} are not scalar.
     *
     * @return {@code true} if this type holds a single value, {@code false} otherwise
     */
    public boolean isScalar() {
        return this == INTEGER || this == FLOAT || this == STRING;
    }

    /**
     * Returns the value type whose {@linkplain #code() code} equals the given value.
     *
     * @param code the engine value-type code to resolve
     * @return the matching value type, or {@link Optional#empty()} if no type matches
     */
    public static Optional<VoipParamType> ofCode(int code) {
        for (var type : values()) {
            if (type.code == code) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
