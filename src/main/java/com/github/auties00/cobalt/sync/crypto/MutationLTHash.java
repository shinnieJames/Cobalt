package com.github.auties00.cobalt.sync.crypto;

import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * LT-Hash (Lattice Hash) implementation for anti-tampering verification.
 *
 * <p>LT-Hash is a cryptographic accumulator with the following properties:
 * <ul>
 *   <li><b>Commutative</b>: hash(a, b) = hash(b, a)</li>
 *   <li><b>Associative</b>: hash(hash(a, b), c) = hash(a, hash(b, c))</li>
 *   <li><b>Reversible</b>: hash_remove(hash(a, b), b) = hash(a)</li>
 *   <li><b>Deterministic</b>: Same input always produces same output</li>
 * </ul>
 *
 * <p>This implementation uses HKDF-expanded values with unsigned 16-bit wrapping
 * arithmetic, matching the WhatsApp Web {@code WACryptoLtHash} algorithm. Each
 * value MAC is expanded via HKDF-SHA256 to 128 bytes, then treated as 64
 * little-endian Uint16 values for pointwise addition or subtraction with
 * wrapping overflow (mod 65536).
 */
public final class MutationLTHash {
    /**
     * Length of the hash state in bytes (64 little-endian Uint16 values).
     */
    private static final int HASH_LENGTH = 128;

    /**
     * HKDF info string used for expanding value MACs.
     */
    private static final byte[] HKDF_INFO = "WhatsApp Patch Integrity".getBytes();

    /**
     * The empty/zero hash state.
     * Used as the initial state for a collection with no mutations.
     */
    public static final byte[] EMPTY_HASH = new byte[HASH_LENGTH];

    private MutationLTHash() {
        // Utility class
    }

    /**
     * Expands a value MAC to 128 bytes using HKDF-SHA256.
     *
     * @param valueMac the value MAC to expand
     * @return the expanded 128-byte value
     */
    private static byte[] expand(byte[] valueMac) {
        try {
            var kdf = KDF.getInstance("HKDF-SHA256");
            var params = HKDFParameterSpec.ofExtract()
                    .addIKM(valueMac)
                    .thenExpand(HKDF_INFO, HASH_LENGTH);
            return kdf.deriveData(params);
        } catch (GeneralSecurityException e) {
            throw new InternalError("Failed to expand value MAC via HKDF", e);
        }
    }

    /**
     * Adds an element to the current hash state.
     *
     * <p>The element (value MAC) is first expanded via HKDF-SHA256 to 128 bytes,
     * then both the current hash and expanded value are treated as 64
     * little-endian Uint16 values and pointwise added with unsigned 16-bit
     * wrapping overflow.
     *
     * <p>This operation is commutative and associative, meaning the order
     * of additions does not affect the final result.
     *
     * @param currentHash the current hash state (must be {@link #HASH_LENGTH} bytes)
     * @param valueMac the value MAC to add (will be HKDF-expanded)
     * @return new hash state after addition
     * @throws NullPointerException if currentHash or valueMac is {@code null}
     * @throws IllegalArgumentException if currentHash is not {@link #HASH_LENGTH} bytes
     */
    public static byte[] add(byte[] currentHash, byte[] valueMac) {
        if (currentHash == null) {
            throw new NullPointerException("Current hash cannot be null");
        }
        if (valueMac == null) {
            throw new NullPointerException("Value MAC cannot be null");
        }
        if (currentHash.length != HASH_LENGTH) {
            throw new IllegalArgumentException("Current hash must be " + HASH_LENGTH + " bytes");
        }

        var expanded = expand(valueMac);
        var hashBuf = ByteBuffer.wrap(currentHash).order(ByteOrder.LITTLE_ENDIAN);
        var expandedBuf = ByteBuffer.wrap(expanded).order(ByteOrder.LITTLE_ENDIAN);
        var result = ByteBuffer.allocate(HASH_LENGTH).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < HASH_LENGTH / 2; i++) {
            var a = Short.toUnsignedInt(hashBuf.getShort());
            var b = Short.toUnsignedInt(expandedBuf.getShort());
            result.putShort((short) ((a + b) & 0xFFFF));
        }

        return result.array();
    }

    /**
     * Removes an element from the current hash state.
     *
     * <p>This is the inverse operation of {@link #add(byte[], byte[])}.
     * The element (value MAC) is first expanded via HKDF-SHA256 to 128 bytes,
     * then both the current hash and expanded value are treated as 64
     * little-endian Uint16 values and pointwise subtracted with unsigned
     * 16-bit wrapping underflow.
     *
     * @param currentHash the current hash state (must be {@link #HASH_LENGTH} bytes)
     * @param valueMac the value MAC to remove (will be HKDF-expanded)
     * @return new hash state after removal
     * @throws NullPointerException if currentHash or valueMac is {@code null}
     * @throws IllegalArgumentException if currentHash is not {@link #HASH_LENGTH} bytes
     */
    public static byte[] remove(byte[] currentHash, byte[] valueMac) {
        if (currentHash == null) {
            throw new NullPointerException("Current hash cannot be null");
        }
        if (valueMac == null) {
            throw new NullPointerException("Value MAC cannot be null");
        }
        if (currentHash.length != HASH_LENGTH) {
            throw new IllegalArgumentException("Current hash must be " + HASH_LENGTH + " bytes");
        }

        var expanded = expand(valueMac);
        var hashBuf = ByteBuffer.wrap(currentHash).order(ByteOrder.LITTLE_ENDIAN);
        var expandedBuf = ByteBuffer.wrap(expanded).order(ByteOrder.LITTLE_ENDIAN);
        var result = ByteBuffer.allocate(HASH_LENGTH).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < HASH_LENGTH / 2; i++) {
            var a = Short.toUnsignedInt(hashBuf.getShort());
            var b = Short.toUnsignedInt(expandedBuf.getShort());
            result.putShort((short) ((a - b) & 0xFFFF));
        }

        return result.array();
    }

    /**
     * Batch operation: removes multiple elements then adds multiple elements.
     *
     * <p>This is equivalent to:
     * <pre>{@code
     * hash = currentHash;
     * for (valueMac : toRemove) {
     *     hash = remove(hash, valueMac);
     * }
     * for (valueMac : toAdd) {
     *     hash = add(hash, valueMac);
     * }
     * }</pre>
     *
     * @param currentHash the current hash state (must be {@link #HASH_LENGTH} bytes)
     * @param toAdd list of value MACs to add (may be empty)
     * @param toRemove list of value MACs to remove (may be empty)
     * @return new hash state after all operations
     * @throws NullPointerException if any parameter is {@code null}
     */
    public static byte[] subtractThenAdd(
        byte[] currentHash,
        List<byte[]> toAdd,
        List<byte[]> toRemove
    ) {
        if (currentHash == null) {
            throw new NullPointerException("Current hash cannot be null");
        }
        if (toAdd == null) {
            throw new NullPointerException("toAdd list cannot be null");
        }
        if (toRemove == null) {
            throw new NullPointerException("toRemove list cannot be null");
        }

        var result = currentHash;

        for (var valueMac : toRemove) {
            result = remove(result, valueMac);
        }

        for (var valueMac : toAdd) {
            result = add(result, valueMac);
        }

        return result;
    }

    /**
     * Creates a copy of the given hash state.
     *
     * @param hash the hash state to copy
     * @return a new array with the same contents
     */
    public static byte[] copy(byte[] hash) {
        return hash == null
                ? null
                : hash.clone();
    }
}
