package com.github.auties00.cobalt.wam.privatestats.ed25519;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

/**
 * Performs field arithmetic over {@code GF(2^255 - 19)}, the prime
 * field underlying Curve25519 and Ed25519.
 *
 * <p>Field elements are represented as 16 little-endian limbs in
 * radix {@code 2^16}, stored in a {@code long[16]}. This is the same
 * layout used by {@link WhatsAppWebModule WACryptoPrimitives}'s
 * {@code lowlevel} object, itself a port of tweetnacl-js'
 * {@code Float64Array(16)} model. Keeping the limb layout identical
 * lets the port be diff-validated against the JavaScript reference
 * function-for-function.
 *
 * <p>Limbs are signed; subtraction may produce negative limbs, and
 * the carry chain in {@link #car25519} normalises any layout
 * (including negative limbs and limbs up to {@code ~2^36}) back to
 * {@code [0, 2^16)} after a multiplication. Every operation is
 * constant time with respect to limb values; no branch depends on
 * secret data.
 */
@WhatsAppWebModule(moduleName = "WACryptoPrimitives")
public final class Ed25519Field {
    /**
     * The number of base-{@code 2^16} limbs in a field element.
     */
    public static final int LIMBS = 16;

    /**
     * The number of bytes in the canonical little-endian encoding of
     * a field element.
     */
    public static final int BYTES = 32;

    /**
     * Prevents instantiation of this utility class.
     *
     * @throws AssertionError always
     */
    private Ed25519Field() {
        throw new AssertionError("Ed25519Field is a utility class and must not be instantiated");
    }

    /**
     * Allocates a fresh, zero-initialised field element.
     *
     * @apiNote
     * Mirrors the tweetnacl {@code gf()} factory used pervasively in
     * the {@link WhatsAppWebModule WACryptoPrimitives} {@code lowlevel}
     * routines.
     *
     * @return a new {@code long[LIMBS]} representing the additive
     *         identity
     */
    public static long[] gf() {
        return new long[LIMBS];
    }

    /**
     * Allocates a field element initialised to a small unsigned
     * constant.
     *
     * @apiNote
     * Used to materialise the literal-valued field constants
     * ({@code 0}, {@code 1}, {@code 2}) consumed by
     * {@link Ed25519HashToPoint} and {@link Ed25519Point}.
     *
     * @param n the constant value, must satisfy {@code 0 <= n < 2^16}
     * @return a fresh field element equal to {@code n}
     */
    public static long[] gfFromSmall(int n) {
        var out = new long[LIMBS];
        out[0] = n & 0xFFFFL;
        return out;
    }

    /**
     * Copies a field element into an existing destination.
     *
     * @apiNote
     * Mirrors {@link WhatsAppWebModule WACryptoPrimitives}
     * {@code lowlevel.set25519}.
     *
     * @param r the destination, overwritten in place
     * @param a the source element
     */
    public static void set25519(long[] r, long[] a) {
        System.arraycopy(a, 0, r, 0, LIMBS);
    }

    /**
     * Performs coordinate-wise addition: {@code o = a + b}.
     *
     * @apiNote
     * Mirrors {@link WhatsAppWebModule WACryptoPrimitives}
     * {@code lowlevel.A}. Limbs are added without reduction; callers
     * must invoke a reducing operation (the carry chain inside
     * {@link #mul} or an explicit {@link #car25519}) before
     * serialising via {@link #pack25519}.
     *
     * @param o the destination element (may alias {@code a} or
     *          {@code b})
     * @param a the left operand
     * @param b the right operand
     */
    public static void add(long[] o, long[] a, long[] b) {
        for (var i = 0; i < LIMBS; i++) {
            o[i] = a[i] + b[i];
        }
    }

    /**
     * Performs coordinate-wise subtraction: {@code o = a - b}.
     *
     * @apiNote
     * Mirrors {@link WhatsAppWebModule WACryptoPrimitives}
     * {@code lowlevel.Z}. Limbs may go negative; the next reducing
     * operation normalises them.
     *
     * @param o the destination element (may alias {@code a} or
     *          {@code b})
     * @param a the left operand
     * @param b the right operand
     */
    public static void sub(long[] o, long[] a, long[] b) {
        for (var i = 0; i < LIMBS; i++) {
            o[i] = a[i] - b[i];
        }
    }

    /**
     * Multiplies two field elements modulo {@code 2^255 - 19}:
     * {@code o = (a * b) mod p}.
     *
     * @apiNote
     * Mirrors {@link WhatsAppWebModule WACryptoPrimitives}
     * {@code lowlevel.M}. Safe under output-input aliasing because
     * the partial products are accumulated into local variables
     * before being written back into {@code o}.
     *
     * @implNote
     * This implementation performs schoolbook multiplication into 31
     * accumulators, applies the {@code 2^256 = 38 (mod p)} reduction
     * that folds the 15 high limbs back into the low ones, then runs
     * two {@link #car25519} passes to normalise every limb back into
     * {@code [0, 2^16)}. The 16-by-16 partial-product expansion is
     * fully unrolled to mirror tweetnacl's {@code M} routine line for
     * line; the choice is for diff-validatability against the
     * reference, not performance.
     *
     * @param o the destination element (may alias {@code a} or
     *          {@code b})
     * @param a the left operand
     * @param b the right operand
     */
    public static void mul(long[] o, long[] a, long[] b) {
        long v;
        long t0 = 0, t1 = 0, t2 = 0, t3 = 0, t4 = 0, t5 = 0, t6 = 0, t7 = 0,
                t8 = 0, t9 = 0, t10 = 0, t11 = 0, t12 = 0, t13 = 0, t14 = 0, t15 = 0,
                t16 = 0, t17 = 0, t18 = 0, t19 = 0, t20 = 0, t21 = 0, t22 = 0, t23 = 0,
                t24 = 0, t25 = 0, t26 = 0, t27 = 0, t28 = 0, t29 = 0, t30 = 0;
        var b0 = b[0];
        var b1 = b[1];
        var b2 = b[2];
        var b3 = b[3];
        var b4 = b[4];
        var b5 = b[5];
        var b6 = b[6];
        var b7 = b[7];
        var b8 = b[8];
        var b9 = b[9];
        var b10 = b[10];
        var b11 = b[11];
        var b12 = b[12];
        var b13 = b[13];
        var b14 = b[14];
        var b15 = b[15];

        v = a[0];
        t0 += v * b0; t1 += v * b1; t2 += v * b2; t3 += v * b3;
        t4 += v * b4; t5 += v * b5; t6 += v * b6; t7 += v * b7;
        t8 += v * b8; t9 += v * b9; t10 += v * b10; t11 += v * b11;
        t12 += v * b12; t13 += v * b13; t14 += v * b14; t15 += v * b15;
        v = a[1];
        t1 += v * b0; t2 += v * b1; t3 += v * b2; t4 += v * b3;
        t5 += v * b4; t6 += v * b5; t7 += v * b6; t8 += v * b7;
        t9 += v * b8; t10 += v * b9; t11 += v * b10; t12 += v * b11;
        t13 += v * b12; t14 += v * b13; t15 += v * b14; t16 += v * b15;
        v = a[2];
        t2 += v * b0; t3 += v * b1; t4 += v * b2; t5 += v * b3;
        t6 += v * b4; t7 += v * b5; t8 += v * b6; t9 += v * b7;
        t10 += v * b8; t11 += v * b9; t12 += v * b10; t13 += v * b11;
        t14 += v * b12; t15 += v * b13; t16 += v * b14; t17 += v * b15;
        v = a[3];
        t3 += v * b0; t4 += v * b1; t5 += v * b2; t6 += v * b3;
        t7 += v * b4; t8 += v * b5; t9 += v * b6; t10 += v * b7;
        t11 += v * b8; t12 += v * b9; t13 += v * b10; t14 += v * b11;
        t15 += v * b12; t16 += v * b13; t17 += v * b14; t18 += v * b15;
        v = a[4];
        t4 += v * b0; t5 += v * b1; t6 += v * b2; t7 += v * b3;
        t8 += v * b4; t9 += v * b5; t10 += v * b6; t11 += v * b7;
        t12 += v * b8; t13 += v * b9; t14 += v * b10; t15 += v * b11;
        t16 += v * b12; t17 += v * b13; t18 += v * b14; t19 += v * b15;
        v = a[5];
        t5 += v * b0; t6 += v * b1; t7 += v * b2; t8 += v * b3;
        t9 += v * b4; t10 += v * b5; t11 += v * b6; t12 += v * b7;
        t13 += v * b8; t14 += v * b9; t15 += v * b10; t16 += v * b11;
        t17 += v * b12; t18 += v * b13; t19 += v * b14; t20 += v * b15;
        v = a[6];
        t6 += v * b0; t7 += v * b1; t8 += v * b2; t9 += v * b3;
        t10 += v * b4; t11 += v * b5; t12 += v * b6; t13 += v * b7;
        t14 += v * b8; t15 += v * b9; t16 += v * b10; t17 += v * b11;
        t18 += v * b12; t19 += v * b13; t20 += v * b14; t21 += v * b15;
        v = a[7];
        t7 += v * b0; t8 += v * b1; t9 += v * b2; t10 += v * b3;
        t11 += v * b4; t12 += v * b5; t13 += v * b6; t14 += v * b7;
        t15 += v * b8; t16 += v * b9; t17 += v * b10; t18 += v * b11;
        t19 += v * b12; t20 += v * b13; t21 += v * b14; t22 += v * b15;
        v = a[8];
        t8 += v * b0; t9 += v * b1; t10 += v * b2; t11 += v * b3;
        t12 += v * b4; t13 += v * b5; t14 += v * b6; t15 += v * b7;
        t16 += v * b8; t17 += v * b9; t18 += v * b10; t19 += v * b11;
        t20 += v * b12; t21 += v * b13; t22 += v * b14; t23 += v * b15;
        v = a[9];
        t9 += v * b0; t10 += v * b1; t11 += v * b2; t12 += v * b3;
        t13 += v * b4; t14 += v * b5; t15 += v * b6; t16 += v * b7;
        t17 += v * b8; t18 += v * b9; t19 += v * b10; t20 += v * b11;
        t21 += v * b12; t22 += v * b13; t23 += v * b14; t24 += v * b15;
        v = a[10];
        t10 += v * b0; t11 += v * b1; t12 += v * b2; t13 += v * b3;
        t14 += v * b4; t15 += v * b5; t16 += v * b6; t17 += v * b7;
        t18 += v * b8; t19 += v * b9; t20 += v * b10; t21 += v * b11;
        t22 += v * b12; t23 += v * b13; t24 += v * b14; t25 += v * b15;
        v = a[11];
        t11 += v * b0; t12 += v * b1; t13 += v * b2; t14 += v * b3;
        t15 += v * b4; t16 += v * b5; t17 += v * b6; t18 += v * b7;
        t19 += v * b8; t20 += v * b9; t21 += v * b10; t22 += v * b11;
        t23 += v * b12; t24 += v * b13; t25 += v * b14; t26 += v * b15;
        v = a[12];
        t12 += v * b0; t13 += v * b1; t14 += v * b2; t15 += v * b3;
        t16 += v * b4; t17 += v * b5; t18 += v * b6; t19 += v * b7;
        t20 += v * b8; t21 += v * b9; t22 += v * b10; t23 += v * b11;
        t24 += v * b12; t25 += v * b13; t26 += v * b14; t27 += v * b15;
        v = a[13];
        t13 += v * b0; t14 += v * b1; t15 += v * b2; t16 += v * b3;
        t17 += v * b4; t18 += v * b5; t19 += v * b6; t20 += v * b7;
        t21 += v * b8; t22 += v * b9; t23 += v * b10; t24 += v * b11;
        t25 += v * b12; t26 += v * b13; t27 += v * b14; t28 += v * b15;
        v = a[14];
        t14 += v * b0; t15 += v * b1; t16 += v * b2; t17 += v * b3;
        t18 += v * b4; t19 += v * b5; t20 += v * b6; t21 += v * b7;
        t22 += v * b8; t23 += v * b9; t24 += v * b10; t25 += v * b11;
        t26 += v * b12; t27 += v * b13; t28 += v * b14; t29 += v * b15;
        v = a[15];
        t15 += v * b0; t16 += v * b1; t17 += v * b2; t18 += v * b3;
        t19 += v * b4; t20 += v * b5; t21 += v * b6; t22 += v * b7;
        t23 += v * b8; t24 += v * b9; t25 += v * b10; t26 += v * b11;
        t27 += v * b12; t28 += v * b13; t29 += v * b14; t30 += v * b15;

        t0 += 38 * t16;
        t1 += 38 * t17;
        t2 += 38 * t18;
        t3 += 38 * t19;
        t4 += 38 * t20;
        t5 += 38 * t21;
        t6 += 38 * t22;
        t7 += 38 * t23;
        t8 += 38 * t24;
        t9 += 38 * t25;
        t10 += 38 * t26;
        t11 += 38 * t27;
        t12 += 38 * t28;
        t13 += 38 * t29;
        t14 += 38 * t30;

        o[0] = t0;
        o[1] = t1;
        o[2] = t2;
        o[3] = t3;
        o[4] = t4;
        o[5] = t5;
        o[6] = t6;
        o[7] = t7;
        o[8] = t8;
        o[9] = t9;
        o[10] = t10;
        o[11] = t11;
        o[12] = t12;
        o[13] = t13;
        o[14] = t14;
        o[15] = t15;

        car25519(o);
        car25519(o);
    }

    /**
     * Squares a field element modulo {@code 2^255 - 19}:
     * {@code o = a^2 mod p}.
     *
     * @apiNote
     * Mirrors {@link WhatsAppWebModule WACryptoPrimitives}
     * {@code lowlevel.S}, which delegates to {@code M(o, a, a)}.
     *
     * @param o the destination element (may alias {@code a})
     * @param a the operand
     */
    public static void square(long[] o, long[] a) {
        mul(o, a, a);
    }

    /**
     * Propagates carries through one full limb pass, normalising
     * every limb to {@code [0, 2^16)}.
     *
     * @apiNote
     * Mirrors {@link WhatsAppWebModule WACryptoPrimitives}
     * {@code lowlevel.car25519}. Run after every multiplication or
     * pair of additions to keep limbs in the layout that
     * {@link #pack25519} expects.
     *
     * @implNote
     * This implementation uses a {@code +65535} bias so that negative
     * limbs produced by {@link #sub} flow correctly through Java's
     * arithmetic right shift; the final adjustment to {@code o[0]}
     * folds the top carry back into the low limb via the
     * {@code 2^256 = 38} relation, with the {@code 1 + 37 = 38} split
     * removing the bias as it folds.
     *
     * @param o the field element to normalise in place
     */
    public static void car25519(long[] o) {
        long c = 1;
        for (var i = 0; i < LIMBS; i++) {
            var v = o[i] + c + 65535L;
            c = v >> 16;
            o[i] = v - (c << 16);
        }
        o[0] += c - 1 + 37 * (c - 1);
    }

    /**
     * Performs a constant-time conditional swap: if {@code b == 1}
     * swaps {@code p} and {@code q}, otherwise leaves both unchanged.
     *
     * @apiNote
     * Mirrors {@link WhatsAppWebModule WACryptoPrimitives}
     * {@code lowlevel.sel25519}. Used by the Montgomery ladder in
     * {@link Ed25519Point#scalarMult} and by every other constant-time
     * branch in this package.
     *
     * @param p the first element, possibly mutated
     * @param q the second element, possibly mutated
     * @param b the swap bit; must be exactly {@code 0} or {@code 1}
     */
    public static void sel25519(long[] p, long[] q, long b) {
        var c = ~(b - 1);
        for (var i = 0; i < LIMBS; i++) {
            var t = c & (p[i] ^ q[i]);
            p[i] ^= t;
            q[i] ^= t;
        }
    }

    /**
     * Encodes a field element as 32 canonical little-endian bytes.
     *
     * @apiNote
     * Mirrors {@link WhatsAppWebModule WACryptoPrimitives}
     * {@code lowlevel.pack25519}. The output is the canonical
     * representative in {@code [0, p)}, suitable for wire transport
     * or hashing.
     *
     * @implNote
     * This implementation runs three carry passes to fully reduce the
     * limbs, then performs two constant-time conditional subtractions
     * of {@code p} via {@link #sel25519} to enforce the canonical
     * representative.
     *
     * @param o the 32-byte destination buffer
     * @param n the field element to encode (read-only; not mutated)
     */
    public static void pack25519(byte[] o, long[] n) {
        var t = n.clone();
        car25519(t);
        car25519(t);
        car25519(t);
        var m = new long[LIMBS];
        for (var j = 0; j < 2; j++) {
            m[0] = t[0] - 0xffedL;
            for (var i = 1; i < 15; i++) {
                m[i] = t[i] - 0xffffL - ((m[i - 1] >> 16) & 1L);
                m[i - 1] &= 0xffffL;
            }
            m[15] = t[15] - 0x7fffL - ((m[14] >> 16) & 1L);
            var b = (m[15] >> 16) & 1L;
            m[14] &= 0xffffL;
            sel25519(t, m, 1 - b);
        }
        for (var i = 0; i < LIMBS; i++) {
            o[2 * i] = (byte) (t[i] & 0xff);
            o[2 * i + 1] = (byte) ((t[i] >> 8) & 0xff);
        }
    }

    /**
     * Decodes 32 little-endian bytes into a field element.
     *
     * @apiNote
     * Mirrors {@link WhatsAppWebModule WACryptoPrimitives}
     * {@code lowlevel.unpack25519}. The high bit of byte 31 (bit 255
     * of the stream) is masked off; callers that need that bit (for
     * Edwards point sign recovery) must read it before invoking this
     * method.
     *
     * @param o the destination field element ({@code long[LIMBS]})
     * @param n the 32-byte little-endian source
     */
    public static void unpack25519(long[] o, byte[] n) {
        for (var i = 0; i < LIMBS; i++) {
            o[i] = (n[2 * i] & 0xffL) | ((n[2 * i + 1] & 0xffL) << 8);
        }
        o[15] &= 0x7fffL;
    }

    /**
     * Computes {@code o = i^((p-5)/8) mod p}.
     *
     * @apiNote
     * Mirrors {@link WhatsAppWebModule WACryptoPrimitives}
     * {@code lowlevel.pow2523}. Used as the canonical square-root
     * helper in Edwards-point decoding ({@link Ed25519Point#unpackNeg})
     * and in the Elligator2 map ({@link Ed25519HashToPoint}).
     *
     * @implNote
     * This implementation runs a 251-iteration square-and-multiply
     * ladder; the single skip at {@code a == 1} matches the bit
     * pattern of {@code (p - 5) / 8}.
     *
     * @param o the destination element
     * @param i the base element
     */
    public static void pow2523(long[] o, long[] i) {
        var c = i.clone();
        for (var a = 250; a >= 0; a--) {
            square(c, c);
            if (a != 1) {
                mul(c, c, i);
            }
        }
        set25519(o, c);
    }

    /**
     * Computes the multiplicative inverse {@code o = i^(p-2) mod p}.
     *
     * @apiNote
     * Mirrors {@link WhatsAppWebModule WACryptoPrimitives}
     * {@code lowlevel.inv25519}. The caller is responsible for
     * ensuring {@code i} is non-zero modulo {@code p}; passing zero
     * silently yields the zero element rather than throwing.
     *
     * @implNote
     * This implementation runs a 254-iteration square-and-multiply
     * ladder; the two skips at {@code a == 2} and {@code a == 4}
     * match the bit pattern of {@code p - 2 = 2^255 - 21}.
     *
     * @param o the destination element
     * @param i the base element; must be non-zero modulo {@code p}
     */
    public static void inv25519(long[] o, long[] i) {
        var c = i.clone();
        for (var a = 253; a >= 0; a--) {
            square(c, c);
            if (a != 2 && a != 4) {
                mul(c, c, i);
            }
        }
        set25519(o, c);
    }
}
