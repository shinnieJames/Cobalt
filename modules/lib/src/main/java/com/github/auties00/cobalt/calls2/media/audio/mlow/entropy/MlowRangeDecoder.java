package com.github.auties00.cobalt.calls2.media.audio.mlow.entropy;

/**
 * Bit-exact range decoder for the MLow speech codec bitstream.
 *
 * <p>MLow reuses the byte-wise range coder that Opus/CELT borrowed from the FIFO arithmetic coder of
 * Martin (1979) and Pasco (1976). The MLow native sources carry the canonical {@code celt/entdec.c}
 * and {@code celt/entcode.c} verbatim and drive them through the thin {@code smpl_entropy_wrapper.c}
 * shim; this class is a faithful, integer bit-exact port of those decoder primitives. Every register
 * is a 32-bit unsigned quantity held in a {@code long} masked to 32 bits, so the Java arithmetic
 * reproduces the C {@code opus_uint32} wrapping exactly.
 *
 * <p>The decoder tracks two 32-bit registers. {@code rng} is the number of distinct values the current
 * coding interval can represent. {@code val} is the difference between the top of the interval and the
 * encoded input value, minus one (the convention used by the reference decoder). Decoding a symbol is a
 * two-step transaction: {@link #decode(long)} computes the cumulative frequency the encoder must have
 * used, the caller resolves that frequency to a symbol through its probability model, and
 * {@link #update(long, long, long)} narrows the interval to that symbol. Convenience entry points
 * ({@link #decodeIcdf(int[], int)}, {@link #decodeBitLogp(int)}, {@link #decodeBits(int)},
 * {@link #decodeUint(long)}) fold both steps into one call for the cases where the symbol resolution is
 * mechanical.
 *
 * <p>Raw bits ({@link #decodeBits(int)} and the trailing fixed-width tail of {@link #decodeUint(long)})
 * are pulled from the OPPOSITE end of the buffer through a separate bit window. The range-coded symbols
 * consume bytes from the front via {@code offs}; raw bits consume bytes from the back via
 * {@code endOffs}. The two streams meet in the middle of the same buffer, which is why the decoder reads
 * zero past either boundary instead of failing.
 *
 * <p>This type is not thread-safe; a decoder instance owns one frame's traversal and is single-threaded
 * by construction (one decode runs on one virtual thread).
 *
 * @implNote This implementation is a direct port of {@code celt/entdec.c} (ec_dec_init, ec_decode,
 * ec_decode_bin, ec_dec_update, ec_dec_normalize, ec_dec_bit_logp, ec_dec_icdf, ec_dec_uint,
 * ec_dec_bits) plus the {@code celt/entcode.c} helpers (ec_tell, ec_tell_frac, celt_udiv). The
 * normalization threshold is {@code EC_CODE_BOT} = {@code EC_CODE_TOP >> EC_SYM_BITS} =
 * {@code (1 << 31) >> 8} = {@code 2^23} = {@code 0x800000}, NOT {@code 2^24}: while the interval is at
 * most {@code 2^23} the decoder shifts in another input byte. {@code celt_udiv} is plain unsigned
 * integer division because the MLow build does not define {@code USE_SMALL_DIV_TABLE} (that table is an
 * ARM-only path that returns the identical quotient). {@code IMUL32} is a 32-bit-truncating multiply.
 */
public final class MlowRangeDecoder {
    /**
     * Number of bits emitted or consumed at a time by the range coder, {@code EC_SYM_BITS}.
     *
     * <p>The coder works in base {@code 2^8}: one byte per renormalization step. This is why range
     * decoding is faster than bit-at-a-time arithmetic coding.
     */
    private static final int EC_SYM_BITS = 8;

    /**
     * Total number of bits in each state register, {@code EC_CODE_BITS}.
     *
     * <p>Both {@code val} and {@code rng} are 32-bit unsigned quantities.
     */
    private static final int EC_CODE_BITS = 32;

    /**
     * Largest value a single output symbol can take, {@code EC_SYM_MAX} = {@code (1 << 8) - 1} = 255.
     */
    private static final long EC_SYM_MAX = (1L << EC_SYM_BITS) - 1;

    /**
     * Carry bit of the high-order range symbol, {@code EC_CODE_TOP} = {@code 1 << (32 - 1)} = {@code 2^31}.
     *
     * <p>After full renormalization {@code rng} always lies in {@code (2^23, 2^31]}, so the most
     * significant byte of the interval is fully resolved. {@code val} is reduced modulo this value, which
     * is the {@code & (EC_CODE_TOP - 1)} mask in {@link #normalize()}.
     */
    private static final long EC_CODE_TOP = 1L << (EC_CODE_BITS - 1);

    /**
     * Low-order bit of the high-order range symbol, {@code EC_CODE_BOT} = {@code EC_CODE_TOP >> EC_SYM_BITS}
     * = {@code 2^23} = {@code 0x800000}.
     *
     * <p>This is the renormalization threshold. While {@code rng <= EC_CODE_BOT} the decoder shifts the
     * interval up by one byte and pulls in another input byte. Using {@code 2^24} here would renormalize
     * one byte too eagerly whenever {@code rng} lands in {@code (2^23, 2^24]} and would desynchronize the
     * decoder from any encoder that used the canonical {@code 2^23} threshold.
     */
    private static final long EC_CODE_BOT = EC_CODE_TOP >> EC_SYM_BITS;

    /**
     * Number of bits available for the last partial symbol in the code field, {@code EC_CODE_EXTRA} =
     * {@code ((EC_CODE_BITS - 2) % EC_SYM_BITS) + 1} = {@code (30 % 8) + 1} = 7.
     *
     * <p>Used by {@link #MlowRangeDecoder(byte[], int, int)} to seed {@code rng} = {@code 1 << 7} and to
     * compute the bits of the first input byte that prime {@code val}.
     */
    private static final int EC_CODE_EXTRA = ((EC_CODE_BITS - 2) % EC_SYM_BITS) + 1;

    /**
     * Number of bits in the raw-bit window, {@code EC_WINDOW_SIZE} = {@code sizeof(ec_window) * 8} = 32.
     *
     * <p>{@code ec_window} is an {@code opus_uint32}, so the window holds at most 32 bits. The refill loop
     * in {@link #decodeBits(int)} stops once it cannot fit another whole byte.
     */
    private static final int EC_WINDOW_SIZE = 32;

    /**
     * Number of bits of the range-coded portion of a raw unsigned integer, {@code EC_UINT_BITS} = 8.
     *
     * <p>{@link #decodeUint(long)} splits large integers into a range-coded high part of up to this many
     * bits and a raw low part read by {@link #decodeBits(int)}.
     */
    private static final int EC_UINT_BITS = 8;

    /**
     * Mask isolating the low 32 bits, used to emulate {@code opus_uint32} wraparound.
     */
    private static final long U32 = 0xFFFFFFFFL;

    /**
     * The encoded byte buffer holding both the front range-coded bytes and the back raw bits.
     *
     * <p>Not {@code final} so a single decoder instance can be re-pointed at successive packets through
     * {@link #reset(byte[], int, int)}; each reset fully re-primes the interval.
     */
    private byte[] buf;

    /**
     * Base index in {@link #buf} of logical byte 0, supporting decode over a sub-window of a larger array.
     */
    private int bufBase;

    /**
     * Number of valid bytes in {@link #buf} starting at index 0, mirroring {@code ec_ctx.storage}.
     *
     * <p>Reads at or beyond this offset return 0 (the coder is defined to read zeros past the end), which
     * lets the front range-coded stream and the back raw-bit stream share one buffer without explicit
     * bounds management.
     */
    private int storage;

    /**
     * Offset of the next range-coded byte to read from the front of the buffer, {@code ec_ctx.offs}.
     */
    private int offs;

    /**
     * Number of raw-bit bytes already consumed from the back of the buffer, {@code ec_ctx.endOffs}.
     */
    private int endOffs;

    /**
     * Buffered raw bits awaiting extraction, {@code ec_ctx.end_window}, held as a 32-bit unsigned value.
     */
    private long endWindow;

    /**
     * Number of valid bits currently in {@link #endWindow}, {@code ec_ctx.nend_bits}.
     */
    private int nendBits;

    /**
     * Running count of whole bits consumed, {@code ec_ctx.nbits_total}, excluding the partial bits still
     * inside the range coder.
     *
     * <p>{@link #tell()} and {@link #tellFrac()} derive the consumed-bit estimate from this field. It is
     * seeded in the constructor with a deliberate {@code +1} bias so that the decoder and a matching
     * encoder agree on the count.
     */
    private int nbitsTotal;

    /**
     * The current interval size, {@code ec_ctx.rng}, as a 32-bit unsigned value.
     *
     * <p>After every {@link #normalize()} this lies in {@code (EC_CODE_BOT, EC_CODE_TOP]} =
     * {@code (2^23, 2^31]}.
     */
    private long rng;

    /**
     * The decoder difference register, {@code ec_ctx.val}, as a 32-bit unsigned value.
     *
     * <p>Holds the top of the current interval minus the encoded value minus one.
     */
    private long val;

    /**
     * The saved normalization factor from the most recent {@link #decode(long)} or
     * {@link #decodeBin(int)}, {@code ec_ctx.ext}.
     *
     * <p>{@link #update(long, long, long)} reuses this factor, which is why {@code decode} and
     * {@code update} must be called in strict alternation.
     */
    private long ext;

    /**
     * The most recently read range-coder input byte awaiting bit consumption, {@code ec_ctx.rem}.
     *
     * <p>Each renormalization combines the leftover bits of this byte with the next byte to refill the low
     * end of {@code val}.
     */
    private int rem;

    /**
     * Nonzero once a decode error has been observed, {@code ec_ctx.error}.
     *
     * <p>{@link #decodeUint(long)} sets this when the raw tail exceeds the declared range, matching the
     * reference behavior of clamping rather than throwing.
     */
    private int error;

    /**
     * Constructs a range decoder over an entire byte array.
     *
     * <p>Equivalent to {@link #MlowRangeDecoder(byte[], int, int)} with {@code offset} 0 and
     * {@code length} equal to {@code data.length}.
     *
     * @param data the encoded frame bytes
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public MlowRangeDecoder(byte[] data) {
        this(data, 0, data.length);
    }

    /**
     * Constructs a range decoder over a window of a byte array and primes the interval.
     *
     * <p>The window {@code [offset, offset + length)} becomes the logical buffer; index 0 of the logical
     * buffer is {@code data[offset]}. The constructor seeds {@code rng} = {@code 1 << EC_CODE_EXTRA} =
     * {@code 1 << 7}, reads the first input byte into {@code rem}, derives the initial {@code val} from the
     * high {@code EC_CODE_EXTRA} bits of that byte, then runs one {@link #normalize()} so {@code rng} grows
     * into its post-normalization range {@code (2^23, 2^31]}.
     *
     * <p>The {@code nbitsTotal} seed includes the {@code EC_CODE_BITS + 1} bias of the reference decoder so
     * that {@link #tell()} reports the same count an encoder would; this is why a freshly initialized
     * decoder reports one consumed bit.
     *
     * @param data   the backing byte array
     * @param offset the index in {@code data} of the first logical byte
     * @param length the number of logical bytes available to the decoder
     * @throws NullPointerException      if {@code data} is {@code null}
     * @throws IndexOutOfBoundsException if {@code [offset, offset + length)} is not within {@code data}
     */
    public MlowRangeDecoder(byte[] data, int offset, int length) {
        reset(data, offset, length);
    }

    /**
     * Re-points this decoder at a fresh window and re-primes the interval, the state-reuse form of
     * {@link #MlowRangeDecoder(byte[], int, int)}.
     *
     * <p>Overwrites every register with the same seed sequence the constructor computes (bounds check,
     * buffer window, {@code rng = 1 << EC_CODE_EXTRA}, first-byte {@code rem}/{@code val} priming, then one
     * {@link #normalize()}), so a decoder driven with {@code reset} is in the identical state a fresh
     * decoder over the same window would be. It lets one decoder instance serve every packet of a stream
     * without reallocating.
     *
     * @param data   the backing byte array
     * @param offset the index in {@code data} of the first logical byte
     * @param length the number of logical bytes available to the decoder
     * @throws NullPointerException      if {@code data} is {@code null}
     * @throws IndexOutOfBoundsException if {@code [offset, offset + length)} is not within {@code data}
     */
    public void reset(byte[] data, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IndexOutOfBoundsException(
                    "window [" + offset + ", " + (offset + length) + ") out of bounds for length " + data.length);
        }
        this.buf = data;
        this.bufBase = offset;
        this.storage = length;
        this.endOffs = 0;
        this.endWindow = 0;
        this.nendBits = 0;
        this.nbitsTotal = EC_CODE_BITS + 1
                - ((EC_CODE_BITS - EC_CODE_EXTRA) / EC_SYM_BITS) * EC_SYM_BITS;
        this.offs = 0;
        this.rng = 1L << EC_CODE_EXTRA;
        this.rem = readByte();
        this.val = (this.rng - 1 - (this.rem >> (EC_SYM_BITS - EC_CODE_EXTRA))) & U32;
        this.error = 0;
        normalize();
    }

    /**
     * Reads the next range-coder byte from the front of the buffer, {@code ec_read_byte}.
     *
     * <p>Returns the unsigned byte at {@code offs} and advances {@code offs}, or 0 once {@code offs}
     * reaches {@link #storage}. Reading zeros past the end is part of the coder contract and lets the
     * range-coded and raw-bit streams share one buffer.
     *
     * @return the next input byte in {@code [0, 255]}, or 0 if exhausted
     */
    private int readByte() {
        return offs < storage ? (buf[bufBase + offs++] & 0xFF) : 0;
    }

    /**
     * Reads the next raw-bit byte from the back of the buffer, {@code ec_read_byte_from_end}.
     *
     * <p>Consumes bytes in reverse from {@code storage - 1} downward, advancing {@code endOffs}, or 0 once
     * the back stream meets the front. The reference increments {@code endOffs} before indexing, so the
     * first byte returned is {@code buf[storage - 1]}.
     *
     * @return the next raw-bit byte in {@code [0, 255]}, or 0 if exhausted
     */
    private int readByteFromEnd() {
        return endOffs < storage ? (buf[bufBase + storage - ++endOffs] & 0xFF) : 0;
    }

    /**
     * Normalizes {@code val} and {@code rng} so the interval again fills the high-order byte,
     * {@code ec_dec_normalize}.
     *
     * <p>While {@code rng <= EC_CODE_BOT} ({@code 2^23}) the interval is too small to resolve another
     * symbol, so the loop shifts {@code rng} up one byte and folds a fresh input byte into the bottom of
     * {@code val}. It combines the leftover bits of the previous byte ({@code rem}) with the new byte,
     * keeps the top {@code EC_CODE_EXTRA} bits, complements them against {@code EC_SYM_MAX}, and masks the
     * result to {@code EC_CODE_TOP - 1} so {@code val} stays below {@code 2^31}.
     *
     * @implNote This implementation reproduces the {@code (sym << 8 | rem) >> (8 - 7)} shift and the
     * {@code (EC_SYM_MAX & ~sym)} complement of the C source exactly; the byte-window refill order (read
     * the new byte AFTER capturing the old {@code rem}) is load-bearing because successive renormalizations
     * chain through {@code rem}.
     */
    private void normalize() {
        while (Long.compareUnsigned(rng, EC_CODE_BOT) <= 0) {
            nbitsTotal += EC_SYM_BITS;
            rng = (rng << EC_SYM_BITS) & U32;
            int sym = rem;
            rem = readByte();
            sym = ((sym << EC_SYM_BITS) | rem) >> (EC_SYM_BITS - EC_CODE_EXTRA);
            val = (((val << EC_SYM_BITS) + (EC_SYM_MAX & ~sym)) & (EC_CODE_TOP - 1)) & U32;
        }
    }

    /**
     * Computes the cumulative frequency of the next symbol given a total frequency, {@code ec_decode}.
     *
     * <p>Saves the normalization factor {@code ext = rng / ft} into {@link #ext} for the matching
     * {@link #update(long, long, long)}, computes {@code s = val / ext}, and returns
     * {@code ft - min(s + 1, ft)}. The returned value falls in {@code [fl, fh)} where {@code [fl, fh)} is
     * the encoded symbol's cumulative-frequency span; the caller maps it to a symbol through its model and
     * then calls {@code update}.
     *
     * <p>Exactly one {@code update} must follow each {@code decode}; calling {@code decode} twice without an
     * intervening {@code update} corrupts the state.
     *
     * @param ft the total frequency of the alphabet the next symbol was coded with; must be positive
     * @return the cumulative frequency representing the encoded symbol, in {@code [0, ft)}
     */
    public long decode(long ft) {
        ext = celtUdiv(rng, ft);
        long s = Long.divideUnsigned(val, ext);
        return ft - ecMini(s + 1, ft);
    }

    /**
     * Computes the cumulative frequency for an alphabet whose total frequency is a power of two,
     * {@code ec_decode_bin}.
     *
     * <p>Equivalent to {@link #decode(long)} with {@code ft = 1 << bits}, but derives {@code ext} by a
     * shift instead of a division. Saves {@code ext = rng >> bits} for the matching
     * {@link #update(long, long, long)}.
     *
     * @param bits the base-two logarithm of the total frequency; must be in {@code [0, 31]}
     * @return the cumulative frequency representing the encoded symbol, in {@code [0, 1 << bits)}
     */
    public long decodeBin(int bits) {
        ext = rng >>> bits;
        long s = Long.divideUnsigned(val, ext);
        long ft = 1L << bits;
        return ft - ecMini(s + 1, ft);
    }

    /**
     * Advances past the decoded symbol using its cumulative-frequency span, {@code ec_dec_update}.
     *
     * <p>Reuses the {@link #ext} factor saved by the preceding {@link #decode(long)} or
     * {@link #decodeBin(int)}. Subtracts {@code ext * (ft - fh)} from {@code val}, then narrows {@code rng}
     * to {@code ext * (fh - fl)} when {@code fl > 0}, or to {@code rng - ext * (ft - fh)} when {@code fl}
     * is zero (the special case that keeps the interval aligned to the buffer top). Finishes with one
     * {@link #normalize()}.
     *
     * @param fl the cumulative frequency of all symbols before the decoded one
     * @param fh the cumulative frequency up to and including the decoded one
     * @param ft the total frequency, which must equal the value passed to the preceding {@code decode}
     */
    public void update(long fl, long fh, long ft) {
        long s = imul32(ext, ft - fh);
        val = (val - s) & U32;
        rng = (fl > 0 ? imul32(ext, fh - fl) : (rng - s) & U32);
        normalize();
    }

    /**
     * Decodes a single bit whose probability of being one is {@code 1 / (1 << logp)},
     * {@code ec_dec_bit_logp}.
     *
     * <p>Splits the interval at {@code s = rng >> logp}: returns 1 when {@code val < s} (taking the small
     * sub-interval), otherwise returns 0 and subtracts {@code s} from {@code val}. Renormalizes before
     * returning.
     *
     * @param logp the negative base-two logarithm of the probability of a one bit
     * @return 1 if the decoded bit is one, 0 otherwise
     */
    public int decodeBitLogp(int logp) {
        long r = rng;
        long d = val;
        long s = r >>> logp;
        int ret = Long.compareUnsigned(d, s) < 0 ? 1 : 0;
        if (ret == 0) {
            val = (d - s) & U32;
        }
        rng = (ret != 0) ? s : (r - s) & U32;
        normalize();
        return ret;
    }

    /**
     * Decodes one symbol against an inverse cumulative-distribution table, {@code ec_dec_icdf}.
     *
     * <p>The table {@code icdf} is monotonically non-increasing with a final entry of zero; symbol
     * {@code s} occupies the interval {@code [ft - icdf[s - 1], ft - icdf[s])} where {@code ft = 1 << ftb}.
     * The loop walks the table, scaling each frequency by {@code r = rng >> ftb}, until {@code val} no
     * longer falls below the running boundary, then narrows the interval to the located symbol and
     * renormalizes. No separate {@link #update(long, long, long)} call is needed.
     *
     * @param icdf the inverse CDF table; entries are unsigned bytes (0 to 255), non-increasing, last is 0
     * @param ftb  the number of bits of precision in the distribution, so {@code ft = 1 << ftb}
     * @return the decoded symbol index
     */
    public int decodeIcdf(int[] icdf, int ftb) {
        long s = rng;
        long d = val;
        long r = s >>> ftb;
        int ret = -1;
        long t;
        do {
            t = s;
            s = imul32(r, icdf[++ret] & 0xFFL);
        } while (Long.compareUnsigned(d, s) < 0);
        val = (d - s) & U32;
        rng = (t - s) & U32;
        normalize();
        return ret;
    }

    /**
     * Extracts a raw unsigned integer with an arbitrary range from the stream, {@code ec_dec_uint}.
     *
     * <p>The integer must have been encoded with the matching {@code ec_enc_uint}. When the range needs
     * more than {@code EC_UINT_BITS} = 8 bits, the value is split: the high part is range-coded through one
     * {@link #decode(long)}/{@link #update(long, long, long)} pair and the low part is read raw by
     * {@link #decodeBits(int)}. If the reassembled value exceeds the declared maximum it is clamped to
     * {@code ft - 1} and {@link #error} is set, mirroring the reference rather than throwing. For small
     * ranges the whole value is range-coded in one transaction.
     *
     * @param ft one more than the maximum decodable value; must be at least 2 and at most {@code 2^32 - 1}
     * @return the decoded unsigned integer in {@code [0, ft)}, as a value in {@code [0, 2^32)}
     */
    public long decodeUint(long ft) {
        long ftMinus = ft - 1;
        int ftb = ecIlog(ftMinus);
        if (ftb > EC_UINT_BITS) {
            ftb -= EC_UINT_BITS;
            long ftHigh = (ftMinus >>> ftb) + 1;
            long s = decode(ftHigh);
            update(s, s + 1, ftHigh);
            long tail = decodeBits(ftb);
            long result = ((s << ftb) | tail) & U32;
            if (Long.compareUnsigned(result, ftMinus) <= 0) {
                return result;
            }
            error = 1;
            return ftMinus;
        }
        long ftSmall = ftMinus + 1;
        long s = decode(ftSmall);
        update(s, s + 1, ftSmall);
        return s;
    }

    /**
     * Extracts a fixed-width run of raw bits from the back of the stream, {@code ec_dec_bits}.
     *
     * <p>Raw bits are read least-significant-first from a 32-bit window refilled by
     * {@link #readByteFromEnd()}. When the window holds fewer than {@code bits} valid bits the refill loop
     * shifts whole bytes in until it cannot fit another, then the low {@code bits} are masked off and
     * shifted out. The bytes come from the END of the buffer, opposite the range-coded stream.
     *
     * @param bits the number of raw bits to extract; must be in {@code [0, 25]}
     * @return the extracted bits, right-aligned, in {@code [0, (1 << bits))}
     */
    public long decodeBits(int bits) {
        long window = endWindow;
        int available = nendBits;
        if (available < bits) {
            do {
                window |= ((long) readByteFromEnd() << available) & U32;
                available += EC_SYM_BITS;
            } while (available <= EC_WINDOW_SIZE - EC_SYM_BITS);
        }
        long ret = window & (((1L << bits) - 1) & U32);
        window = (window >>> bits) & U32;
        available -= bits;
        endWindow = window;
        nendBits = available;
        nbitsTotal += bits;
        return ret;
    }

    /**
     * Returns the number of whole bits consumed so far, {@code ec_tell}.
     *
     * <p>Computed as {@code nbitsTotal - ilog(rng)}. The estimate is always slightly larger than the exact
     * value because all rounding error is in the positive direction; it is suitable for making symmetric
     * coding decisions an encoder could reproduce.
     *
     * @return the number of bits consumed, rounded up
     */
    public int tell() {
        return nbitsTotal - ecIlog(rng);
    }

    /**
     * Returns the number of bits consumed so far scaled by {@code 2^BITRES} (eighths of a bit),
     * {@code ec_tell_frac}.
     *
     * <p>Uses the fast linear-plus-lookup approximation of the reference: it linearizes the leading bits of
     * {@code rng} and corrects with an eight-entry threshold table to recover the fractional bit position
     * to {@code 1/8}-bit resolution.
     *
     * @return the number of bits consumed, scaled by 8, as a 32-bit unsigned value
     */
    public long tellFrac() {
        long nbits = ((long) nbitsTotal << BITRES) & U32;
        int l = ecIlog(rng);
        long r = (rng >>> (l - 16)) & U32;
        long b = (r >>> 12) - 8;
        b += (Long.compareUnsigned(r, TELL_FRAC_CORRECTION[(int) b]) > 0) ? 1 : 0;
        long lScaled = ((long) l << BITRES) + b;
        return (nbits - lScaled) & U32;
    }

    /**
     * Returns whether a decode error has been observed, {@code ec_get_error}.
     *
     * <p>Set by {@link #decodeUint(long)} when a raw tail exceeds the declared range. The decoder keeps
     * running after an error; the flag merely records that a clamp occurred.
     *
     * @return {@code true} if an error occurred during decoding, {@code false} otherwise
     */
    public boolean hasError() {
        return error != 0;
    }

    /**
     * Returns the current value of the {@code val} register as a 32-bit unsigned value.
     *
     * <p>Exposed for state inspection and bit-exactness verification against a reference trace; it is not
     * part of the decode contract.
     *
     * @return the {@code val} register in {@code [0, 2^32)}
     */
    long val() {
        return val;
    }

    /**
     * Returns the current value of the {@code rng} register as a 32-bit unsigned value.
     *
     * <p>Exposed for state inspection and bit-exactness verification against a reference trace; it is not
     * part of the decode contract.
     *
     * @return the {@code rng} register in {@code [0, 2^32)}
     */
    long rng() {
        return rng;
    }

    /**
     * Returns the saved normalization factor {@code ext} as a 32-bit unsigned value.
     *
     * <p>Exposed for state inspection and bit-exactness verification against a reference trace; it is not
     * part of the decode contract.
     *
     * @return the {@code ext} register in {@code [0, 2^32)}
     */
    long ext() {
        return ext;
    }

    /**
     * Resolution of the fractional bit-usage measurement, {@code BITRES} = 3, so units are eighths of a bit.
     */
    private static final int BITRES = 3;

    /**
     * Threshold table used by {@link #tellFrac()} to recover the exact fractional-bit transition points,
     * the {@code correction} array of {@code ec_tell_frac}.
     *
     * <p>Every entry fits in an unsigned 16-bit value ({@code <= 0xFFFF}), so it is stored as {@code int};
     * the sole read widens it to {@code long} for {@link Long#compareUnsigned(long, long)} against a value
     * that is itself in {@code [2^15, 2^16)}, and since each stored value is non-negative the widening
     * preserves the exact magnitude the {@code long} table produced.
     */
    private static final int[] TELL_FRAC_CORRECTION = {
            35733, 38967, 42495, 46340,
            50535, 55109, 60097, 65535
    };

    /**
     * Computes {@code min(a, b)} on 32-bit unsigned values, the {@code EC_MINI} macro.
     *
     * <p>The reference defines this branchlessly; this implementation uses an explicit unsigned comparison
     * which yields the identical result for the in-range operands of the range coder.
     *
     * @param a the first operand, treated as 32-bit unsigned
     * @param b the second operand, treated as 32-bit unsigned
     * @return the unsigned minimum of {@code a} and {@code b}
     */
    private static long ecMini(long a, long b) {
        return Long.compareUnsigned(a, b) <= 0 ? a : b;
    }

    /**
     * Computes a 32-bit-truncating unsigned multiply, the {@code IMUL32} macro.
     *
     * <p>Multiplies two 32-bit unsigned operands and keeps the low 32 bits, reproducing C
     * {@code opus_uint32} overflow.
     *
     * @param a the first operand, treated as 32-bit unsigned
     * @param b the second operand, treated as 32-bit unsigned
     * @return the low 32 bits of {@code a * b}
     */
    private static long imul32(long a, long b) {
        return (a * b) & U32;
    }

    /**
     * Computes the integer base-two logarithm plus one of a nonzero value, {@code EC_ILOG}.
     *
     * <p>Equals {@code 32 - numberOfLeadingZeros(v)}, the position of the most significant set bit plus
     * one. Undefined for {@code v == 0}, matching the reference contract; callers guarantee a nonzero
     * argument.
     *
     * @param v the value whose bit length is required; must be nonzero in {@code [1, 2^32)}
     * @return the number of significant bits in {@code v}
     */
    private static int ecIlog(long v) {
        return 32 - Integer.numberOfLeadingZeros((int) (v & U32));
    }

    /**
     * Computes unsigned integer division, {@code celt_udiv}.
     *
     * <p>The MLow build does not define {@code USE_SMALL_DIV_TABLE} (an ARM-only optimization that returns
     * the identical quotient), so this is plain unsigned division.
     *
     * @param n the dividend, treated as 32-bit unsigned
     * @param d the divisor, treated as 32-bit unsigned; must be positive
     * @return the unsigned quotient {@code n / d}
     */
    private static long celtUdiv(long n, long d) {
        return Long.divideUnsigned(n, d);
    }
}
