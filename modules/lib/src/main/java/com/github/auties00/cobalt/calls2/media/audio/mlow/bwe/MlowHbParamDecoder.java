package com.github.auties00.cobalt.calls2.media.audio.mlow.bwe;

import com.github.auties00.cobalt.calls2.media.audio.mlow.entropy.MlowEntropyWrapper;
import com.github.auties00.cobalt.calls2.media.audio.mlow.entropy.MlowRangeDecoder;

/**
 * Per-frame high-band parameter decoder for the MLow speech codec, the port of {@code smpl_decode_hb_params}
 * ({@code smpl_param_coding.c}).
 *
 * <p>For the greater-than-16 kHz profiles each internal frame carries, after its low-band parameters, two
 * high-band vector-quantization indices: a high-band gain index and a high-band LSF index. This decoder reads
 * them from the same range-coded stream the low-band decode reads, immediately after the low-band parameters
 * of the frame, exactly as the native core decoder interleaves {@code smpl_decode_hb_params} after
 * {@code smpl_decode_lb_params}. The gain index is read first against the gain entropy cumulative-mass
 * function (CMF) selected by frame length, voicing, and rate; the LSF index is read second against the LSF CMF,
 * which is the conditional CMF row selected by the previous frame's LSF index when the frame is conditionally
 * coded and low rate, and the unconditional CMF otherwise.
 *
 * <p>This decoder is stateful across the frames of a packet and across packets in one continuous decode
 * session: it threads the previous high-band LSF index for the conditional CMF row selection, mirroring the
 * {@code ParamsDecoder.prev_hb_lpc_ix} field of the native core decoder. Construct one per logical stream and
 * feed it every frame in order; {@link #reset()} returns it to the freshly constructed state.
 *
 * @implNote This implementation ports {@code smpl_decode_hb_params} statement for statement: the gain decode
 * first, then the LSF decode, then the {@code prev_hb_lpc_ix} update. The conditional LSF CMF row is windowed
 * out of the concatenated conditional block by {@code hb_lpc_vq_sel_cond[voiced][prev_hb_lpc_ix] * CMFLen}, the
 * native pointer arithmetic; the offset-aware
 * {@link MlowEntropyWrapper#decodeUpdate(MlowRangeDecoder, int[], int, int)} reads that row in place (measuring
 * every span relative to its first windowed entry) without copying it out. The frame-length index is the
 * native {@code frame_length_16 == 320}
 * test, expressed here as the four-high-band-subframe (20 ms) case.
 */
public final class MlowHbParamDecoder {
    /**
     * The previous frame's high-band LSF index, the native {@code ParamsDecoder.prev_hb_lpc_ix}; read to select
     * the conditional LSF CMF row and updated after each frame's LSF decode.
     */
    private int prevHbLpcIx;

    /**
     * Constructs a high-band parameter decoder with cleared conditional-coding state.
     */
    public MlowHbParamDecoder() {
        this.prevHbLpcIx = 0;
    }

    /**
     * Returns this decoder to its freshly constructed state.
     *
     * <p>Clears the previous high-band LSF index so the next frame's conditional CMF row selection starts from
     * the reset value. Call this between independent decode sessions; do not call it between the packets of one
     * continuous stream, which must thread state.
     */
    public void reset() {
        this.prevHbLpcIx = 0;
    }

    /**
     * Decodes one internal frame's high-band parameters from the shared range-coded stream, the port of
     * {@code smpl_decode_hb_params}.
     *
     * <p>Reads the gain index against the gain CMF selected by frame length, voicing, and rate, then the LSF
     * index against the conditional or unconditional LSF CMF, advancing {@code decoder} past both symbols and
     * updating the threaded previous high-band LSF index.
     *
     * @param decoder         the range decoder positioned at the frame's first high-band symbol, immediately
     *                        after the frame's low-band parameters
     * @param frameLength16   the low-band frame length in samples, the native {@code frame_length_16}; selects
     *                        the gain frame-length class ({@code 320} is the 20 ms four-high-band-subframe case)
     * @param voiced          {@code true} when the frame is voiced
     * @param condCoding      the conditional-coding flag in effect for the frame, the native {@code cond_coding}
     * @param lowRate         {@code true} for the low-rate mode
     * @return the decoded high-band parameters of the frame
     */
    public MlowBandwidthExtension.HbFrameParams decode(MlowRangeDecoder decoder, int frameLength16, boolean voiced,
                                                       boolean condCoding, boolean lowRate) {
        int v = voiced ? 1 : 0;
        int lr = lowRate ? 1 : 0;
        int framelen20 = frameLength16 == 320 ? 1 : 0;

        // Decode gains.
        int[] gainCmf = MlowHbTables.gainCmf(framelen20, v, lr);
        int gainQi = MlowEntropyWrapper.decodeUpdate(decoder, gainCmf);

        // Decode LSFs.
        int lsfIdx;
        if (condCoding && lowRate) {
            int cmfLen = MlowHbTables.lsfSize(v, lr) + 1;
            int row = MlowHbTables.selCond(v)[prevHbLpcIx];
            int[] condBlock = MlowHbTables.lsfCmfCond(v);
            lsfIdx = MlowEntropyWrapper.decodeUpdate(decoder, condBlock, row * cmfLen, cmfLen);
        } else {
            lsfIdx = MlowEntropyWrapper.decodeUpdate(decoder, MlowHbTables.lsfCmf(v, lr));
        }
        prevHbLpcIx = lsfIdx;

        return new MlowBandwidthExtension.HbFrameParams(gainQi, lsfIdx);
    }
}
