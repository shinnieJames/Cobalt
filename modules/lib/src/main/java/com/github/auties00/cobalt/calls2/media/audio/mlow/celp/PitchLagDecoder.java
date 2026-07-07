package com.github.auties00.cobalt.calls2.media.audio.mlow.celp;

import com.github.auties00.cobalt.calls2.media.audio.mlow.entropy.MlowEntropyWrapper;
import com.github.auties00.cobalt.calls2.media.audio.mlow.entropy.MlowRangeDecoder;
import com.github.auties00.cobalt.calls2.media.audio.mlow.tables.PitchTables;

/**
 * Decodes the per-subframe integer pitch lags of an MLow voiced low-band frame, the port of the
 * lag-decode tail of {@code decode_lb_voiced} in {@code smpl_param_coding.c}.
 *
 * <p>MLow codes the pitch lag as a coarse block segmentation plus within-block deltas, not as one lag per
 * subframe. The decode proceeds in three stages, all against the {@link PitchTables} cumulative mass
 * functions:
 * <ul>
 * <li><b>Block-segmentation index.</b> For the first frame of a packet (no previous lag block) the
 * native index is read directly against the block-segmentation index CMF. For later frames of a
 * multi-frame packet it is read in two steps: a block-transition symbol conditioned on the previous
 * frame's last lag block, then a windowed block-segmentation index restricted to the segmentations whose
 * first block matches the transition. The native index is mapped back to the C block-segmentation index
 * through the permutation table, selecting one reconstructed {@link PitchTables.Blockseg}.</li>
 * <li><b>First lag.</b> When the segmentation does not chain onto the previous frame's block (or there is
 * no previous frame) the first segment's lag is read as a uniform symbol over the block size and offset
 * into the chosen first block.</li>
 * <li><b>Delta lags.</b> Each remaining segment's lag is a delta against the running previous lag, read
 * against the within-block delta-lag CMF selected by the frame's mean quantized adaptive-codebook gain
 * class, windowed by the block transition.</li>
 * </ul>
 * The output is one integer lag index per pitch subframe ({@code SMPL_PITCH_NUM_SUBFRAMES} of them),
 * matching the native {@code laginds} array bit-for-bit.
 *
 * <p>This decoder is stateful across the frames of a packet: it carries the previous frame's last lag
 * block and lag index, which condition the block-transition and delta-lag decode of the next frame.
 * {@link #reset()} clears that carry and must be called whenever the native decoder would reset it,
 * namely when conditional coding is disabled for a frame (the native {@code cond_coding == FALSE} path
 * that sets {@code prev_lagblk = prev_lagidx = -1}).
 *
 * @implNote This implementation reproduces the native block-size and minimum-lag constants
 * ({@code blocksize = SMPL_PITCHBLOCK_MS * SMPL_PITCH_FS_KHZ * 2 = 64}) and the exact delta-lag pointer
 * arithmetic {@code &delta_lag_CMF[delta_range_start + 2 * blocksize - 1]} with a windowed decode of
 * length {@code blocksize + 1}, where {@code delta_range_start = -(prev_lagidx - prev_lagblk * blocksize)
 * + (blk - prev_lagblk) * blocksize}. The native code scans the permutation table linearly to invert
 * {@code blocksegs2idx}; this port does the same since the table is short and the inversion runs once per
 * frame. The block-transition path is only reachable for 20 ms multi-frame packets (the native
 * {@code framelen_ms == 20} assertion), which is exactly the shipped 16 kHz, 60 ms configuration's
 * three-frame packet.
 */
public final class PitchLagDecoder {
    /**
     * The within-block lag block size in lag indices, the native
     * {@code SMPL_PITCHBLOCK_MS * SMPL_PITCH_FS_KHZ * 2} = {@code 2 * 16 * 2} = 64.
     *
     * <p>A lag index runs over one block of this many positions; the uniform first-lag symbol and the
     * delta-lag window are both this size.
     */
    private static final int BLOCKSIZE = 64;

    /**
     * Number of pitch subframes a 20 ms frame's lags span, the native {@code SMPL_PITCH_NUM_SUBFRAMES}.
     */
    private static final int PITCH_NUM_SUBFRAMES = 8;

    /**
     * The shared 20 ms pitch decode data, the native {@code smpl_get_pitch_data(SMPL_PITCH_NUM_SUBFRAMES)}.
     */
    private final PitchTables.PitchData data;

    /**
     * The previous frame's last lag block, the native {@code ParamsDecoder.prev_lagblk}; {@code -1} when
     * there is no previous frame in this packet or conditional coding was disabled.
     */
    private int prevLagblk;

    /**
     * The previous frame's last lag index, the native {@code ParamsDecoder.prev_lagidx}; {@code -1} when
     * there is no previous frame in this packet or conditional coding was disabled.
     */
    private int prevLagidx;

    /**
     * The C block-segmentation index decoded by the most recent {@link #decodeLags} call, the native
     * {@code blocksegs_ix}; {@code -1} before any lag decode.
     *
     * <p>The lags alone do not determine the segmentation (two segments can share a coarse block yet carry
     * distinct lags), so this records the decoded segmentation index for callers that need to reconstruct the
     * encode-side {@code blocksegs_ix}, such as the encoder round-trip cross-check.
     */
    private int lastBlocksegsIx = -1;

    /**
     * Constructs a pitch-lag decoder over the 20 ms decode tables and clears its cross-frame carry.
     *
     * <p>The decoder starts in the reset state ({@code prevLagblk == prevLagidx == -1}), ready to decode
     * the first frame of a packet.
     */
    public PitchLagDecoder() {
        this.data = PitchTables.data20();
        reset();
    }

    /**
     * Clears the cross-frame carry, the native {@code prev_lagblk = prev_lagidx = -1} reset.
     *
     * <p>Must be called whenever the native decoder would reset its lag carry: at the start of a packet
     * and on any frame decoded with conditional coding disabled. After a reset the next decoded frame
     * takes the no-previous-block path (direct block-segmentation index, uniform first lag).
     */
    public void reset() {
        this.prevLagblk = -1;
        this.prevLagidx = -1;
        this.lastBlocksegsIx = -1;
    }

    /**
     * Returns the C block-segmentation index decoded by the most recent {@link #decodeLags} call, the native
     * {@code blocksegs_ix}.
     *
     * <p>This is the segmentation index that, together with the decoded lags, reconstructs the encode-side
     * {@code blocksegs_ix} a parameter serializer must re-emit. It is {@code -1} before any lag decode and is
     * not part of the synthesis contract; the synthesis path needs only the lags themselves.
     *
     * @return the most recently decoded block-segmentation index, or {@code -1} before any decode
     */
    public int lastBlocksegsIx() {
        return lastBlocksegsIx;
    }

    /**
     * Decodes the per-subframe integer pitch lags of one voiced low-band frame, the lag-decode tail of
     * {@code decode_lb_voiced}.
     *
     * <p>Reads the block-segmentation index (conditioned on the cross-frame carry), then the first lag and
     * the delta lags, and expands them into one lag per pitch subframe. The {@code meanAcbgQ14} argument
     * is the frame's mean quantized adaptive-codebook gain in Q14, computed by the gain decode that runs
     * earlier in the native {@code decode_lb_voiced}; it selects the delta-lag CMF class. The cross-frame
     * carry is updated so the next call can decode a multi-frame continuation.
     *
     * @param decoder     the range decoder positioned at the pitch-lag parameters
     * @param meanAcbgQ14 the frame's mean quantized adaptive-codebook gain in Q14, the native
     *                    {@code mean_acbg_Q14}
     * @return the {@link #PITCH_NUM_SUBFRAMES} integer lag indices, the native {@code laginds}
     */
    public int[] decodeLags(MlowRangeDecoder decoder, int meanAcbgQ14) {
        int ixJulia = decodeBlocksegIndex(decoder);
        int blocksegsIx = blocksegIxFromJulia(ixJulia);
        lastBlocksegsIx = blocksegsIx;
        PitchTables.Blockseg seg = data.blocksegs()[blocksegsIx];

        int[] laginds = new int[PITCH_NUM_SUBFRAMES];
        int blk = seg.blocks()[0];
        int deltaBlk = blk - prevLagblk;
        int startSeg = 0;
        int lagindsIx = 0;
        if (!(prevLagblk > -1 && deltaBlk >= -1 && deltaBlk <= 2)) {
            int lagind = MlowEntropyWrapper.decodeUniform(decoder, BLOCKSIZE) + blk * BLOCKSIZE;
            for (int j = 0; j < seg.seglens()[0]; j++) {
                laginds[lagindsIx++] = lagind;
            }
            prevLagblk = blk;
            prevLagidx = lagind;
            startSeg = 1;
        }

        int mode = selectMode(meanAcbgQ14);
        int[] deltaLagCmf = data.deltaLagCmfs()[mode];
        int[] blocks = seg.blocks();
        int[] seglens = seg.seglens();
        for (int k = startSeg; k < seg.nblocks(); k++) {
            blk = blocks[k];
            deltaBlk = blk - prevLagblk;
            int prevLagidxMod = prevLagidx - prevLagblk * BLOCKSIZE;
            int deltaRangeStart = -prevLagidxMod + deltaBlk * BLOCKSIZE;
            int windowStart = deltaRangeStart + 2 * BLOCKSIZE - 1;
            int idx = MlowEntropyWrapper.decodeUpdate(decoder, deltaLagCmf, windowStart, BLOCKSIZE + 1);
            int lagind = idx + deltaRangeStart + prevLagidx;
            for (int j = 0; j < seglens[k]; j++) {
                laginds[lagindsIx++] = lagind;
            }
            prevLagblk = blk;
            prevLagidx = lagind;
        }
        return laginds;
    }

    /**
     * Decodes the native block-segmentation index, the index-decode block at the top of the lag decode in
     * {@code decode_lb_voiced}.
     *
     * <p>For the no-previous-block case the native index is the symbol decoded against the
     * block-segmentation index CMF plus one. For a multi-frame continuation it is decoded in two steps: a
     * block-transition symbol conditioned on the previous frame's last lag block selects the current
     * first block, then a windowed block-segmentation index restricted to that block's first-block range
     * is read and offset back into the full index space.
     *
     * @param decoder the range decoder positioned at the block-segmentation index symbol(s)
     * @return the native (one-based) block-segmentation index, the native {@code ix_julia}
     */
    private int decodeBlocksegIndex(MlowRangeDecoder decoder) {
        if (prevLagblk < 0) {
            return MlowEntropyWrapper.decodeUpdate(decoder, data.blocksegIdxCmf()) + 1;
        }
        int block0 = MlowEntropyWrapper.decodeUpdate(decoder, data.blockTransitionCmf()[prevLagblk]);
        byte[] range = data.firstBlockRange();
        int startIx = range[block0 * 2] & 0xFF;
        int rangeEnd = range[block0 * 2 + 1] & 0xFF;
        int cmfLen = rangeEnd - startIx + 2;
        return MlowEntropyWrapper.decodeUpdate(decoder, data.blocksegIdxCmf(), startIx, cmfLen) + startIx + 1;
    }

    /**
     * Inverts the C block-segmentation index permutation, the linear scan over {@code blocksegs2idx} in
     * {@code decode_lb_voiced}.
     *
     * <p>Finds the C index whose native (Julia) value equals {@code ixJulia}. The permutation table is
     * read as unsigned bytes.
     *
     * @param ixJulia the native (one-based) block-segmentation index
     * @return the C block-segmentation index in {@code [0, num_blocksegs)}
     * @throws IllegalStateException if no C index maps to {@code ixJulia}
     */
    private int blocksegIxFromJulia(int ixJulia) {
        byte[] map = data.blocksegs2idx();
        for (int i = 0; i < map.length; i++) {
            if ((map[i] & 0xFF) == ixJulia) {
                return i;
            }
        }
        throw new IllegalStateException("no block-segmentation index maps to native index " + ixJulia);
    }

    /**
     * Selects the within-block delta-lag CMF class from the mean quantized adaptive-codebook gain, the
     * native {@code mode} selection in {@code decode_lb_voiced}.
     *
     * <p>Returns 0, 1, or 2 by comparing {@code meanAcbgQ14} against the two 20 ms thresholds: class 0
     * below the first threshold, class 1 below the second, class 2 otherwise. Higher gain (more strongly
     * voiced) maps to a higher class.
     *
     * @param meanAcbgQ14 the frame's mean quantized adaptive-codebook gain in Q14
     * @return the delta-lag CMF class index in {@code [0, 2]}
     */
    private static int selectMode(int meanAcbgQ14) {
        int[] thr = PitchTables.acbgainThr20Q14();
        if (meanAcbgQ14 < thr[0]) {
            return 0;
        }
        if (meanAcbgQ14 < thr[1]) {
            return 1;
        }
        return 2;
    }
}
