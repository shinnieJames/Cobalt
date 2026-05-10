package com.github.auties00.cobalt.exception;

import java.lang.foreign.MemorySegment;

/**
 * Thrown for failures that occur on the call stack — the audio and
 * video codecs, the RTP/SRTP packetisation layer, and the WebRTC
 * transports (ICE, DTLS, SCTP, DataChannel) that sit underneath.
 *
 * <p>A WhatsApp voice or video call lives on a separate set of
 * connections from the messaging WebSocket. The flow is roughly:
 * gather ICE candidates, negotiate a DTLS-SRTP session over the
 * winning candidate pair, exchange media as encrypted RTP packets,
 * and (optionally) carry control traffic on a DataChannel layered
 * over SCTP. Each of those steps can fail independently, and each
 * failure surfaces through one of the nested subtypes; native
 * codec failures (libopus, libvpx, openh264, libspeexdsp) follow
 * the same pattern.
 *
 * <p>Call exceptions are never fatal: a failed call, a corrupt
 * frame, or a stuck handshake is scoped to a single call and the
 * messaging session keeps running. The configurable error handler
 * decides whether to retry, fall back to a different transport, or
 * surface the failure to the user.
 *
 * @see Opus
 * @see SpeexDsp
 * @see Rtp
 * @see Srtp
 * @see DtlsHandshake
 * @see Ice
 * @see DataChannel
 * @see Sctp
 * @see H264
 * @see Vpx
 */
public sealed abstract class WhatsAppCallException
        extends WhatsAppException
        permits WhatsAppCallException.Opus,
                WhatsAppCallException.SpeexDsp,
                WhatsAppCallException.Rtp,
                WhatsAppCallException.Srtp,
                WhatsAppCallException.DtlsHandshake,
                WhatsAppCallException.Ice,
                WhatsAppCallException.DataChannel,
                WhatsAppCallException.Sctp,
                WhatsAppCallException.H264,
                WhatsAppCallException.Vpx {

    /**
     * Constructs a new call exception with the specified detail message.
     *
     * @param message the detail message describing the call error
     */
    protected WhatsAppCallException(String message) {
        super(message);
    }

    /**
     * Constructs a new call exception with the specified detail message and cause.
     *
     * @param message the detail message describing the call error
     * @param cause   the underlying cause of this exception
     */
    protected WhatsAppCallException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns whether the failure invalidates the current WhatsApp session.
     *
     * <p>Call failures are isolated from the main messaging channel;
     * they never tear the session down.
     *
     * @return {@code false}
     */
    @Override
    public boolean isFatal() {
        return false;
    }

    /**
     * Thrown when libopus encode or decode fails — wraps a non-zero
     * {@code OPUS_*} error code or a Java-side invariant violation.
     *
     * <p>Callers that already have a libopus error code in hand can
     * use {@link #fromErr(String, int)} to build an exception whose
     * message includes libopus's own textual description from
     * {@code opus_strerror}.
     */
    public static final class Opus extends WhatsAppCallException {
        /**
         * Constructs a new Opus exception with the specified message.
         *
         * @param message the detail message describing the codec failure
         */
        public Opus(String message) {
            super(message);
        }

        /**
         * Constructs a new Opus exception with the specified message and cause.
         *
         * @param message the detail message describing the codec failure
         * @param cause   the underlying cause
         */
        public Opus(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Builds an exception whose message includes libopus's textual
         * description of the {@code OPUS_*} error code.
         *
         * @param prefix  human-readable context ("encode failed")
         * @param errCode the libopus error code (negative for failures)
         * @return a new exception ready to throw
         */
        public static Opus fromErr(String prefix, int errCode) {
            return new Opus(prefix + ": " + opusErrString(errCode) + " (code " + errCode + ")");
        }

        /**
         * Reads libopus's static error string for the given error code.
         *
         * @param errCode the libopus error code
         * @return the error string, or "unknown" if the lookup fails
         */
        private static String opusErrString(int errCode) {
            try {
                var ptr = com.github.auties00.cobalt.call.audio.opus.bindings.Opus.opus_strerror(errCode);
                if (ptr.equals(MemorySegment.NULL)) {
                    return "unknown";
                }
                return ptr.reinterpret(Long.MAX_VALUE).getString(0);
            } catch (Throwable t) {
                return "unknown";
            }
        }
    }

    /**
     * Thrown when a libspeexdsp call fails or returns an error code.
     *
     * <p>Wraps {@link Throwable}s thrown by FFM downcalls so callers
     * don't have to catch {@code Throwable} themselves.
     */
    public static final class SpeexDsp extends WhatsAppCallException {
        /**
         * Constructs a new SpeexDSP exception with the specified message.
         *
         * @param message the detail message describing the failure
         */
        public SpeexDsp(String message) {
            super(message);
        }

        /**
         * Constructs a new SpeexDSP exception with the specified message and cause.
         *
         * @param message the detail message describing the failure
         * @param cause   the underlying cause
         */
        public SpeexDsp(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when RTP packet encode/decode, jitter-buffer ordering,
     * or SRTP protect/unprotect fails at the RTP layer.
     *
     * <p>Wraps both protocol-level errors (truncated header, wrong
     * version, SSRC mismatch) and Java-side invariant violations.
     */
    public static final class Rtp extends WhatsAppCallException {
        /**
         * Constructs a new RTP exception with the specified message.
         *
         * @param message the detail message describing the failure
         */
        public Rtp(String message) {
            super(message);
        }

        /**
         * Constructs a new RTP exception with the specified message and cause.
         *
         * @param message the detail message describing the failure
         * @param cause   the underlying cause
         */
        public Rtp(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when SRTP/SRTCP packet protection or unprotection
     * fails.
     *
     * <p>Wraps the underlying
     * {@link java.security.GeneralSecurityException} (for cipher or
     * HMAC initialisation or transformation errors), or stands on
     * its own to report packet-format violations, replay detection,
     * and authentication-tag mismatches.
     */
    public static final class Srtp extends WhatsAppCallException {
        /**
         * Constructs a new SRTP exception with the specified message.
         *
         * @param message the detail message describing the failure
         */
        public Srtp(String message) {
            super(message);
        }

        /**
         * Constructs a new SRTP exception with the specified message and cause.
         *
         * @param message the detail message describing the failure
         * @param cause   the underlying cause
         */
        public Srtp(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when the DTLS-SRTP handshake fails: peer fingerprint
     * mismatch, unsupported SRTP profile, alert received, etc.
     */
    public static final class DtlsHandshake extends WhatsAppCallException {
        /**
         * Constructs a new DTLS handshake exception with the specified message.
         *
         * @param message the detail message describing the handshake failure
         */
        public DtlsHandshake(String message) {
            super(message);
        }

        /**
         * Constructs a new DTLS handshake exception with the specified message and cause.
         *
         * @param message the detail message describing the handshake failure
         * @param cause   the underlying cause
         */
        public DtlsHandshake(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when ICE candidate gathering, connectivity checks, or
     * candidate-pair nomination fail.
     *
     * <p>Wraps both protocol-level errors (malformed STUN response,
     * missing MESSAGE-INTEGRITY, etc.) and Java-side invariant
     * violations.
     */
    public static final class Ice extends WhatsAppCallException {
        /**
         * Constructs a new ICE exception with the specified message.
         *
         * @param message the detail message describing the failure
         */
        public Ice(String message) {
            super(message);
        }

        /**
         * Constructs a new ICE exception with the specified message and cause.
         *
         * @param message the detail message describing the failure
         * @param cause   the underlying cause
         */
        public Ice(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when a WebRTC DataChannel operation fails: malformed
     * DCEP message on the wire, attempt to use a channel in the
     * wrong state, stream-id collision, or unsupported channel
     * type.
     *
     * <p>Wraps usrsctp-level failures from the underlying SCTP
     * association by chaining a {@link Sctp} exception as the cause.
     */
    public static final class DataChannel extends WhatsAppCallException {
        /**
         * Constructs a new DataChannel exception with the specified message.
         *
         * @param message the detail message describing the failure
         */
        public DataChannel(String message) {
            super(message);
        }

        /**
         * Constructs a new DataChannel exception with the specified message and cause.
         *
         * @param message the detail message describing the failure
         * @param cause   the underlying cause
         */
        public DataChannel(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when a usrsctp operation fails — wraps either a
     * non-zero return code from the C library or a Java-side
     * invariant violation (closed socket, wrong-sized buffer, etc.).
     */
    public static final class Sctp extends WhatsAppCallException {
        /**
         * Constructs a new SCTP exception with the specified message.
         *
         * @param message the detail message describing the failure
         */
        public Sctp(String message) {
            super(message);
        }

        /**
         * Constructs a new SCTP exception with the specified message and cause.
         *
         * @param message the detail message describing the failure
         * @param cause   the underlying cause
         */
        public Sctp(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when an openh264 operation fails — wraps a non-zero
     * return code from a vtable method ({@code Initialize},
     * {@code EncodeFrame}, {@code DecodeFrame2}, etc.) or a
     * Java-side invariant violation (closed codec, wrong frame
     * size, etc.).
     */
    public static final class H264 extends WhatsAppCallException {
        /**
         * Constructs a new H.264 exception with the specified message.
         *
         * @param message the detail message describing the failure
         */
        public H264(String message) {
            super(message);
        }

        /**
         * Constructs a new H.264 exception with the specified message and cause.
         *
         * @param message the detail message describing the failure
         * @param cause   the underlying cause
         */
        public H264(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when a libvpx operation fails — wraps a non-zero
     * {@code vpx_codec_err_t} return code or a Java-side invariant
     * violation (closed codec, wrong frame dimensions, etc.).
     *
     * <p>Callers that already have a {@code vpx_codec_err_t} value
     * in hand can use {@link #fromErr(String, int)} to build an
     * exception whose message includes libvpx's own textual
     * description from {@code vpx_codec_err_to_string}.
     */
    public static final class Vpx extends WhatsAppCallException {
        /**
         * Constructs a new VPx exception with the specified message.
         *
         * @param message the detail message describing the failure
         */
        public Vpx(String message) {
            super(message);
        }

        /**
         * Constructs a new VPx exception with the specified message and cause.
         *
         * @param message the detail message describing the failure
         * @param cause   the underlying cause
         */
        public Vpx(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Builds an exception whose message includes libvpx's
         * textual description of the {@code vpx_codec_err_t} code.
         *
         * @param prefix  human-readable context ("encode failed")
         * @param errCode the {@code vpx_codec_err_t} return value
         * @return a new exception ready to throw
         */
        public static Vpx fromErr(String prefix, int errCode) {
            return new Vpx(prefix + ": " + vpxErrString(errCode) + " (code " + errCode + ")");
        }

        /**
         * Reads libvpx's static error string for the given error code.
         *
         * @param errCode the {@code vpx_codec_err_t} value
         * @return the error string, or "unknown" if the lookup fails
         */
        private static String vpxErrString(int errCode) {
            try {
                var ptr = com.github.auties00.cobalt.call.video.vpx.bindings.LibVpx.vpx_codec_err_to_string(errCode);
                if (ptr.equals(MemorySegment.NULL)) {
                    return "unknown";
                }
                return ptr.reinterpret(Long.MAX_VALUE).getString(0);
            } catch (Throwable t) {
                return "unknown";
            }
        }
    }
}
