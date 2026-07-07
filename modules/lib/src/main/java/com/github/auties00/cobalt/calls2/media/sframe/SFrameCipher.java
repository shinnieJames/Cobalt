package com.github.auties00.cobalt.calls2.media.sframe;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/**
 * The cipher that seals and opens one SFrame media frame under a single key id: AES-128 in counter
 * mode for confidentiality and a truncated HMAC-SHA256 over the trailer and ciphertext for
 * authentication.
 *
 * <p>A frame is sealed by deriving the per-frame counter block from the frame counter, AES-CTR
 * encrypting the plaintext under it, and computing the truncated HMAC-SHA256 over the SFrame trailer
 * (the associated data) followed by the ciphertext. A frame is opened by recomputing and verifying
 * that tag in constant time before decrypting.
 *
 * <p>The 16-byte AES counter block for a frame is the frame counter serialized big-endian into the
 * middle eight bytes of an otherwise-zero 16-byte block (four zero bytes, the eight big-endian counter
 * bytes, four zero bytes), AES-encrypted under the AES key, then XORed with a stored counter mask. The
 * truncated tag is the leading {@code tagLength} bytes of
 * {@code HMAC-SHA256(authKey, trailer || ciphertext)}; a suite whose tag length is zero performs no
 * authentication.
 *
 * @implNote This implementation reproduces {@code SFrameAesCipher} (fn6878 {@code initCipher} /
 * {@code initCounterMask}, fn6880 {@code cipherCreate} / {@code getCounter}, fn6886
 * {@code encryptData}, fn6887 {@code generateAndAppendTag}, fn6890 {@code decryptData}) from
 * {@code wa_sframe_cipher_aes_impl.cc} of the wa-voip WASM module {@code ff-tScznZ8P}, onto the JDK
 * {@code Cipher("AES/CTR/NoPadding")} and {@code Mac("HmacSHA256")} providers. The native cipher does
 * NOT use the RFC 9605 salt-XOR-counter nonce or the length-suffixed AEAD layout of the superseded
 * {@code call/rtp/sframe/SFrameCipher}; it builds the CTR block by AES-encrypting the big-endian
 * counter (recovered as occupying bytes [4, 12) of the block, see {@link #counterBlock(long)}) and
 * XORing a stored 16-byte counter mask ({@code initCounterMask}, cipher offset {@code 0x58}), and
 * authenticates over {@code trailer || ciphertext} with the trailer as the only associated data. The
 * AES key is 16 bytes (AES-128) and the HMAC key 32 bytes, both copied directly from the resolved key
 * ({@code initCipher} validates auth key size {@code 0x20} and the {@code 0x1c} key-params marker).
 * <p>
 * The 16-byte counter mask is supplied by the caller ({@link SFrameKeyProvider}) rather than computed
 * here. {@code initCipher} (reversibility/instructions.jsonl fn6878, instructions {@code at}
 * 2837925-2837955) builds the mask at cipher offset {@code 0x58} by passing the 12 bytes at
 * {@code key+0x10} (the {@code 0x1c}-byte key-params buffer is a 16-byte AES key followed by a 12-byte
 * salt) through the host callback {@code unnamed_function_9110(&DAT_ram_00000e0d, cipher+0x58, 0x10,
 * key+0x10, 0xc)}. That callback selector ({@code 0xe0d}) is the engine's bounded-copy trampoline, NOT
 * a crypto primitive: the identical selector copies the freshly AES-CTR-decrypted plaintext out to the
 * caller buffer in {@code decryptData} (fn6890) and copies the caller IV into a local before the XOR in
 * {@code cipherCreate} (fn6880), neither of which is an encryption. So the mask is the 12-byte salt
 * right-padded with four zero bytes to 16 bytes (recovered structure); {@link #zeroCounterMask()} is the
 * all-zero mask for a call with no salt installed.
 * <p>
 * TODO: the per-key-id 12-byte salt VALUE is still unrecovered. It is produced by the libsframe key
 * store's per-key-id derivation ({@code fn10765} at the key-store vtable slot {@code +0x1c}), whose body
 * is a native/BoringSSL callback NOT present in this WASM, so {@link SFrameKeyProvider} cannot yet emit
 * a real salt and currently passes {@link #zeroCounterMask()}. A live capture on 2026-06-15
 * (re/calls2-spec/captures/sframe-frame-live.json) cleared the prior fake-media blocker (a 3-party SFU
 * group VIDEO call connected, video encoded at 14 fps and decoded on peers, and raw-CDP worker
 * breakpoints were proven to reach the voip pthread pool by pausing {@code derive_sframe_key}), yet
 * {@code wa_sframe_encrypt} and its only in-WASM video caller {@code wa_video_sframe_encode_cb} (fn5477)
 * NEVER fired: in SFU group-call mode the SFrame per-frame transform is not engaged on the relayed video
 * send path (the per-participant keys are still derived and rotated, but the media rides the relay
 * hop-by-hop SRTP layer), and a 1:1 call uses no SFrame at all, so no live SFrame media frame is
 * produced by WA Web to read the salt or the mask from. Until the salt source is recovered the mask
 * stays all-zero, so encrypt and decrypt remain mutually consistent but are not byte-compatible with the
 * native counter mask.
 */
public final class SFrameCipher {
    /**
     * Holds the JCA transformation name for AES in counter mode without padding.
     */
    private static final String AES_CTR_TRANSFORMATION = "AES/CTR/NoPadding";

    /**
     * Holds the JCA key algorithm name for the AES key.
     */
    private static final String AES_KEY_ALGORITHM = "AES";

    /**
     * Holds the JCA algorithm name for the HMAC-SHA256 authentication primitive.
     */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Holds the cipher suite selecting the EVP variant and the truncated tag length.
     */
    private final SFrameCipherSuite suite;

    /**
     * Holds the 16-byte AES-128 encryption key.
     */
    private final byte[] aesKey;

    /**
     * Holds the 32-byte HMAC-SHA256 authentication key.
     */
    private final byte[] authKey;

    /**
     * Holds the 16-byte counter mask, XORed against each per-frame AES-encrypted counter block to form
     * the CTR initial block; supplied by {@link SFrameKeyProvider} from the per-key-id salt.
     */
    private final byte[] counterMask;

    /**
     * Holds the immutable AES key specification reused across frames on this thread-confined cipher.
     */
    private final SecretKeySpec aesKeySpec;

    /**
     * Holds the immutable HMAC key specification reused across frames on this thread-confined cipher.
     */
    private final SecretKeySpec macKeySpec;

    /**
     * Holds the AES counter-mode engine reused across frames.
     *
     * <p>One {@link SFrameCipher} instance is confined to a single stream direction and its media thread,
     * so the mutable JCA engine is a plain reused field rather than a per-call {@code getInstance}.
     */
    private final Cipher ctrCipher;

    /**
     * Holds the AES single-block engine reused to turn each per-frame counter block into keystream.
     */
    private final Cipher blockCipher;

    /**
     * Holds the HMAC-SHA256 engine reused across frames for the truncated authentication tag.
     */
    private final Mac hmac;

    /**
     * Holds the sixteen-byte counter-block scratch reused across frames.
     *
     * <p>Only the middle eight counter bytes are ever written; the surrounding bytes stay zero across
     * reuse, matching the fresh zero-filled block the native layout expects.
     */
    private final byte[] counterScratch = new byte[SFrameCipherSuite.IV_LENGTH];

    /**
     * Holds the length, in bytes, of the per-key-id salt the counter mask is built from.
     *
     * <p>The native {@code initCipher} (fn6878) reads exactly this many bytes from {@code key+0x10} of
     * the {@code 0x1c}-byte key-params buffer and copies them into the zero-initialized 16-byte mask.
     */
    public static final int SALT_LENGTH = 12;

    /**
     * Constructs a cipher from the resolved per-key-id key material, the per-key-id counter mask, and
     * the negotiated suite.
     *
     * <p>The {@code aesKey} must be {@value SFrameCipherSuite#AES_KEY_LENGTH} bytes, the {@code authKey}
     * {@value SFrameCipherSuite#HMAC_KEY_LENGTH} bytes, and {@code counterMask}
     * {@value SFrameCipherSuite#IV_LENGTH} bytes. The mask is the value the native engine stores at
     * cipher offset {@code 0x58}; build it from a 12-byte salt with {@link #counterMaskFromSalt(byte[])}
     * or pass {@link #zeroCounterMask()} when no salt is installed.
     *
     * @param suite       the cipher suite carrying the EVP variant and tag length
     * @param aesKey      the AES-128 encryption key
     * @param authKey     the HMAC-SHA256 authentication key
     * @param counterMask the 16-byte counter mask XORed into each counter block
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code aesKey}, {@code authKey}, or {@code counterMask} has the
     *                                  wrong length
     */
    public SFrameCipher(SFrameCipherSuite suite, byte[] aesKey, byte[] authKey, byte[] counterMask) {
        Objects.requireNonNull(suite, "suite cannot be null");
        Objects.requireNonNull(aesKey, "aesKey cannot be null");
        Objects.requireNonNull(authKey, "authKey cannot be null");
        Objects.requireNonNull(counterMask, "counterMask cannot be null");
        if (aesKey.length != SFrameCipherSuite.AES_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "aesKey must be " + SFrameCipherSuite.AES_KEY_LENGTH + " bytes, got " + aesKey.length);
        }
        if (authKey.length != SFrameCipherSuite.HMAC_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "authKey must be " + SFrameCipherSuite.HMAC_KEY_LENGTH + " bytes, got " + authKey.length);
        }
        if (counterMask.length != SFrameCipherSuite.IV_LENGTH) {
            throw new IllegalArgumentException(
                    "counterMask must be " + SFrameCipherSuite.IV_LENGTH + " bytes, got " + counterMask.length);
        }
        this.suite = suite;
        this.aesKey = aesKey.clone();
        this.authKey = authKey.clone();
        this.counterMask = counterMask.clone();
        this.aesKeySpec = new SecretKeySpec(this.aesKey, AES_KEY_ALGORITHM);
        this.macKeySpec = new SecretKeySpec(this.authKey, HMAC_ALGORITHM);
        try {
            this.ctrCipher = Cipher.getInstance(AES_CTR_TRANSFORMATION);
            this.blockCipher = Cipher.getInstance("AES/ECB/NoPadding");
            this.hmac = Mac.getInstance(HMAC_ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("SFrame cipher initialization failed", e);
        }
    }

    /**
     * Constructs a cipher with an all-zero counter mask.
     *
     * <p>This is the no-salt form: every counter block is the AES encryption of the big-endian counter
     * with no mask XOR, which is what the call media path effectively does until the per-key-id salt that
     * feeds the real mask is recovered (see the class {@code TODO}).
     *
     * @param suite   the cipher suite carrying the EVP variant and tag length
     * @param aesKey  the AES-128 encryption key
     * @param authKey the HMAC-SHA256 authentication key
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code aesKey} or {@code authKey} has the wrong length
     */
    public SFrameCipher(SFrameCipherSuite suite, byte[] aesKey, byte[] authKey) {
        this(suite, aesKey, authKey, zeroCounterMask());
    }

    /**
     * Returns a fresh all-zero counter mask.
     *
     * <p>This is the mask for a cipher with no per-key-id salt installed; XORing it leaves the
     * AES-encrypted counter block unchanged.
     *
     * @return a new {@value SFrameCipherSuite#IV_LENGTH}-byte all-zero array
     */
    public static byte[] zeroCounterMask() {
        return new byte[SFrameCipherSuite.IV_LENGTH];
    }

    /**
     * Builds the 16-byte counter mask from a per-key-id salt.
     *
     * <p>The mask is the salt's bytes copied into a zero-initialized 16-byte buffer, reproducing the
     * native {@code initCipher} (fn6878) which copies the 12 bytes at {@code key+0x10} into the
     * zero-filled mask at cipher offset {@code 0x58} through the engine's bounded-copy trampoline (not a
     * crypto primitive). A salt shorter than {@value SFrameCipherSuite#IV_LENGTH} bytes leaves the
     * trailing bytes zero; a salt of {@value SFrameCipherSuite#IV_LENGTH} or more bytes is truncated to
     * the mask width.
     *
     * @implNote This implementation reproduces the salt-to-mask copy of {@code initCipher} (fn6878): the
     * native reads {@value #SALT_LENGTH} salt bytes, so a {@value #SALT_LENGTH}-byte salt yields a mask
     * whose last four bytes are zero. The salt value itself is not yet recovered (see the class
     * {@code TODO}); this builder is the faithful structure for once it is.
     * @param salt the per-key-id salt, at most {@value SFrameCipherSuite#IV_LENGTH} bytes consumed
     * @return the {@value SFrameCipherSuite#IV_LENGTH}-byte counter mask
     * @throws NullPointerException if {@code salt} is {@code null}
     */
    public static byte[] counterMaskFromSalt(byte[] salt) {
        Objects.requireNonNull(salt, "salt cannot be null");
        var mask = new byte[SFrameCipherSuite.IV_LENGTH];
        System.arraycopy(salt, 0, mask, 0, Math.min(salt.length, SFrameCipherSuite.IV_LENGTH));
        return mask;
    }

    /**
     * Returns the cipher suite this cipher applies.
     *
     * @return the cipher suite
     */
    public SFrameCipherSuite suite() {
        return suite;
    }

    /**
     * Returns the truncated authentication-tag length, in bytes, this cipher appends.
     *
     * @return the tag length from the {@linkplain #suite() suite}
     */
    public int tagLength() {
        return suite.tagLength();
    }

    /**
     * Seals one frame: AES-CTR encrypts {@code plaintext} under the counter block for {@code counter}
     * and appends the truncated HMAC-SHA256 over {@code trailer || ciphertext}.
     *
     * @param trailer   the SFrame trailer bytes covered as associated data
     * @param plaintext the frame plaintext to encrypt
     * @param counter   the per-frame counter selecting the counter block
     * @return the ciphertext followed by the {@link #tagLength()}-byte tag
     * @throws NullPointerException       if {@code trailer} or {@code plaintext} is {@code null}
     * @throws WhatsAppCallException.Srtp if the AES or HMAC computation fails
     */
    public byte[] seal(byte[] trailer, byte[] plaintext, long counter) {
        Objects.requireNonNull(trailer, "trailer cannot be null");
        Objects.requireNonNull(plaintext, "plaintext cannot be null");
        var counterBlock = counterBlock(counter);
        var ciphertext = aesCtr(plaintext, counterBlock);
        var tagLength = suite.tagLength();
        if (tagLength == 0) {
            return ciphertext;
        }
        var tag = authTag(trailer, ciphertext);
        var out = Arrays.copyOf(ciphertext, ciphertext.length + tagLength);
        System.arraycopy(tag, 0, out, ciphertext.length, tagLength);
        return out;
    }

    /**
     * Opens one frame: verifies the trailing tag against the HMAC over {@code trailer || ciphertext},
     * then AES-CTR decrypts the body.
     *
     * <p>Returns {@code null} when the body is shorter than the tag or the tag does not verify; the
     * caller drops the frame in those cases. The HMAC is verified BEFORE the AES-CTR decrypt.
     *
     * @param trailer the SFrame trailer bytes covered as associated data
     * @param body    the ciphertext followed by the {@link #tagLength()}-byte tag
     * @param counter the per-frame counter selecting the counter block
     * @return the recovered plaintext, or {@code null} if the body is too short or authentication
     *         fails
     * @throws NullPointerException       if {@code trailer} or {@code body} is {@code null}
     * @throws WhatsAppCallException.Srtp if the AES or HMAC computation fails
     */
    public byte[] open(byte[] trailer, byte[] body, long counter) {
        Objects.requireNonNull(trailer, "trailer cannot be null");
        Objects.requireNonNull(body, "body cannot be null");
        var tagLength = suite.tagLength();
        if (body.length < tagLength) {
            return null;
        }
        var ciphertextLength = body.length - tagLength;
        var ciphertext = Arrays.copyOfRange(body, 0, ciphertextLength);
        if (tagLength > 0) {
            var receivedTag = Arrays.copyOfRange(body, ciphertextLength, body.length);
            var expectedTag = authTag(trailer, ciphertext);
            if (!MessageDigest.isEqual(expectedTag, receivedTag)) {
                return null;
            }
        }
        return aesCtr(ciphertext, counterBlock(counter));
    }

    /**
     * Builds the 16-byte AES-CTR initial block for a frame counter.
     *
     * <p>The counter is serialized big-endian into the middle eight bytes of an otherwise-zero
     * 16-byte block, AES-encrypted under the AES key, then XORed byte-for-byte with the stored
     * {@link #counterMask}, matching the native {@code getCounter}/{@code cipherCreate} sequence. The
     * block layout is four zero bytes, the eight big-endian counter bytes, then four zero bytes:
     *
     * {@snippet :
     *   block[0 .. 4]   = 0x00000000           // four leading zero bytes
     *   block[4 .. 12]  = bigEndian(counter)   // eight counter bytes
     *   block[12 .. 16] = 0x00000000           // four trailing zero bytes
     * }
     *
     * @implNote This implementation places the big-endian counter at byte offset four within the
     * 16-byte block, not in the high eight bytes. The placement is pinned at the instruction level:
     * {@code wa_sframe_encrypt} (re/calls2-spec/captures, reversibility/instructions.jsonl fn6898)
     * zeroes the four bytes at scratch offset 96 and offset 108, stores {@code bswap64(counter)} as a
     * little-endian {@code i64} at offset 100 (instructions {@code at} 2842059-2842158, so a
     * big-endian counter occupies bytes [4, 12) of the 16-byte region [96, 112)), then passes that
     * region as the IV to {@code getCounter} (fn6880), which AES-encrypts it and XORs the 16-byte
     * counter mask stored at the cipher offset {@code 0x58} (instructions {@code at} 2838437-2838479).
     * {@code wa_sframe_decrypt} (fn6900) builds the identical block (instructions {@code at}
     * 2843200-2843289).
     * @param counter the per-frame counter
     * @return the 16-byte CTR initial block
     * @throws WhatsAppCallException.Srtp if the AES encryption of the counter block fails
     */
    private byte[] counterBlock(long counter) {
        var block = counterScratch;
        for (var i = 0; i < Long.BYTES; i++) {
            block[Long.BYTES + 3 - i] = (byte) (counter >>> (8 * i));
        }
        var encrypted = aesEncryptBlock(block);
        for (var i = 0; i < SFrameCipherSuite.IV_LENGTH; i++) {
            encrypted[i] ^= counterMask[i];
        }
        return encrypted;
    }

    /**
     * Runs AES-128 counter mode (its own inverse) over {@code data} from the given CTR initial block.
     *
     * @param data        the bytes to transform
     * @param initalBlock the 16-byte CTR initial counter block
     * @return the transformed bytes
     * @throws WhatsAppCallException.Srtp if the AES provider rejects the inputs
     */
    private byte[] aesCtr(byte[] data, byte[] initalBlock) {
        try {
            ctrCipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, new IvParameterSpec(initalBlock));
            return ctrCipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("SFrame AES-CTR failed", e);
        }
    }

    /**
     * AES-encrypts a single 16-byte block in ECB mode, the primitive that turns each per-frame counter
     * block into the pre-mask CTR keystream block.
     *
     * @param block the 16-byte block to encrypt
     * @return the 16-byte AES-encrypted block
     * @throws WhatsAppCallException.Srtp if the AES provider rejects the inputs
     */
    private byte[] aesEncryptBlock(byte[] block) {
        try {
            blockCipher.init(Cipher.ENCRYPT_MODE, aesKeySpec);
            return blockCipher.doFinal(block);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("SFrame AES block encryption failed", e);
        }
    }

    /**
     * Computes the leading {@link #tagLength()} bytes of the HMAC-SHA256 over {@code trailer ||
     * ciphertext}.
     *
     * <p>The trailer is the only associated data; there is no nonce or length suffix in the HMAC
     * input, unlike the superseded RFC 9605 cipher.
     *
     * @param trailer    the SFrame trailer associated-data bytes
     * @param ciphertext the ciphertext bytes
     * @return the truncated authentication tag
     * @throws WhatsAppCallException.Srtp if the HMAC provider rejects the inputs
     */
    private byte[] authTag(byte[] trailer, byte[] ciphertext) {
        try {
            hmac.init(macKeySpec);
            hmac.update(trailer);
            hmac.update(ciphertext);
            var full = hmac.doFinal();
            return Arrays.copyOf(full, suite.tagLength());
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("SFrame HMAC failed", e);
        }
    }
}
