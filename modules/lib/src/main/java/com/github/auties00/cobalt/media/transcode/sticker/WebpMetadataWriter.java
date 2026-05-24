package com.github.auties00.cobalt.media.transcode.sticker;

import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.util.DataUtils;
import com.alibaba.fastjson2.JSON;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Appends a sticker-metadata EXIF chunk to an extended WebP file.
 *
 * @apiNote
 * Called by the sticker transcoder pipeline after libwebp has produced a
 * 512x512 extended-WebP encode of the source image. The metadata payload is
 * the sticker descriptor dictionary WhatsApp ships on the wire (publisher,
 * pack id, emoji list, accessibility text, sticker-maker source type and so
 * on); see
 * {@link StickerPipeline}
 * for the canonical field set.
 *
 * @implNote
 * This implementation is a direct port of WA Web's
 * {@code WAWebAddWebpMetadata.addWebpMetadata}. The input must already be an
 * extended WebP (the file body begins with {@code RIFF<size>WEBPVP8X...});
 * the upstream JS path enforces the same precondition. The metadata is
 * embedded as an EXIF RIFF chunk holding a minimal little-endian TIFF
 * header with a single private tag (id {@code 0x5741}, type
 * {@code UNDEFINED}) whose value is a UTF-8 JSON serialisation of the
 * descriptor dictionary. WA Web does not set the EXIF flag bit in the
 * VP8X chunk header and neither does this implementation; the WhatsApp
 * decoder reads the EXIF chunk by FourCC regardless of the flag.
 *
 * @see #write(byte[], Map)
 */
@WhatsAppWebModule(moduleName = "WAWebAddWebpMetadata")
final class WebpMetadataWriter {
    /**
     * Length of the RIFF outer header: {@code "RIFF" + 4-byte size + "WEBP"}.
     */
    private static final int RIFF_HEADER_LENGTH = 12;

    /**
     * Length of a RIFF chunk header: {@code 4-byte FourCC + 4-byte size}.
     */
    private static final int CHUNK_HEADER_LENGTH = 8;

    /**
     * Offset within the RIFF outer header where the overall file-size field
     * lives. The value stored at this offset is {@code totalLength - 8}.
     */
    private static final int RIFF_SIZE_OFFSET = 4;

    /**
     * FourCC of the chunk this writer emits.
     */
    private static final byte[] EXIF_CHUNK_NAME = {'E', 'X', 'I', 'F'};

    /**
     * FourCC of the chunk that marks an extended WebP. The presence of this
     * chunk is the precondition for embedding metadata.
     */
    private static final byte[] VP8X_CHUNK_NAME = {'V', 'P', '8', 'X'};

    /**
     * Length of the TIFF wrapper preceding the JSON payload: 8 bytes of
     * header ({@code "II" + magic + first IFD offset}) + 2 bytes of tag
     * count + 12 bytes of single-tag entry = 22 bytes.
     */
    private static final int TIFF_WRAPPER_LENGTH = 22;

    /**
     * Custom TIFF tag id WhatsApp reserves for the sticker descriptor
     * payload. Stored little-endian on the wire as bytes {@code 'A' 'W'}.
     */
    private static final int WA_PRIVATE_TIFF_TAG = 0x5741;

    /**
     * TIFF tag data type {@code UNDEFINED}; treats the payload as opaque
     * bytes.
     */
    private static final int TIFF_TYPE_UNDEFINED = 7;

    /**
     * Hidden constructor; the type exposes only static entry points.
     */
    private WebpMetadataWriter() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a copy of {@code source} with an EXIF chunk holding the given
     * metadata appended at the end of the RIFF stream.
     *
     * @apiNote
     * The sticker pipeline passes a metadata map keyed by the field names
     * WA Web canonicalises in {@code WAWebStickerMetadataParsing}
     * ({@code "sticker-pack-publisher"}, {@code "emojis"},
     * {@code "is-first-party-sticker"}, etc.). Values are serialised to
     * JSON via FastJSON2 and follow the standard JSON type mapping
     * (strings, numbers, booleans, arrays, maps).
     *
     * @param source   an extended WebP file (begins with
     *                 {@code RIFF<size>WEBPVP8X...})
     * @param metadata the sticker descriptor dictionary; keys are the
     *                 canonical WhatsApp field names
     * @return a fresh byte array holding the updated WebP file
     * @throws WhatsAppMediaException.Processing if {@code source} is not a
     *         valid extended WebP (missing the {@code RIFF/WEBP/VP8X}
     *         markers)
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebAddWebpMetadata", exports = "addWebpMetadata",
                       adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] write(byte[] source, Map<String, Object> metadata)
            throws WhatsAppMediaException.Processing {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(metadata, "metadata");
        if (!isExtendedWebp(source)) {
            throw new WhatsAppMediaException.Processing(
                    "WebP source is not an extended file (missing VP8X chunk)");
        }
        var jsonBytes = JSON.toJSONString(metadata).getBytes(StandardCharsets.UTF_8);
        var rawMetadata = buildTiffWrapper(jsonBytes);
        var needsPadding = (jsonBytes.length & 1) != 0;
        var chunkSize = CHUNK_HEADER_LENGTH + rawMetadata.length + (needsPadding ? 1 : 0);
        var totalLength = source.length + chunkSize;
        var result = new byte[totalLength];
        System.arraycopy(source, 0, result, 0, source.length);
        var cursor = source.length;
        System.arraycopy(EXIF_CHUNK_NAME, 0, result, cursor, EXIF_CHUNK_NAME.length);
        cursor += EXIF_CHUNK_NAME.length;
        DataUtils.putInt(result, cursor, rawMetadata.length, ByteOrder.LITTLE_ENDIAN);
        cursor += 4;
        System.arraycopy(rawMetadata, 0, result, cursor, rawMetadata.length);
        cursor += rawMetadata.length;
        if (needsPadding) {
            result[cursor] = 0;
        }
        DataUtils.putInt(result, RIFF_SIZE_OFFSET, totalLength - 8, ByteOrder.LITTLE_ENDIAN);
        return result;
    }

    /**
     * Builds the 22-byte TIFF wrapper plus the JSON payload that the WA
     * private tag points at.
     *
     * @apiNote
     * The wire layout mirrors WA Web's helper {@code c(jsonBytes)} inside
     * {@code WAWebAddWebpMetadata}:
     * {@snippet :
     *  // bytes 0..1   "II" (little-endian TIFF marker)
     *  // bytes 2..3   42, 0 (TIFF magic 0x002A)
     *  // bytes 4..7   first IFD offset = 8
     *  // bytes 8..9   number of tag entries = 1
     *  // bytes 10..11 tag id = 0x5741 ("AW" bytes; little-endian 0x5741)
     *  // bytes 12..13 data type = 7 (UNDEFINED)
     *  // bytes 14..17 value count = jsonBytes.length
     *  // bytes 18..21 value offset = 22
     *  // bytes 22..   JSON UTF-8 payload
     * }
     *
     * @param jsonBytes the UTF-8 JSON serialisation of the metadata map
     * @return the concatenated TIFF wrapper + JSON payload
     */
    private static byte[] buildTiffWrapper(byte[] jsonBytes) {
        var result = new byte[TIFF_WRAPPER_LENGTH + jsonBytes.length];
        result[0] = 'I';
        result[1] = 'I';
        result[2] = 42;
        result[3] = 0;
        DataUtils.putInt(result, 4, 8, ByteOrder.LITTLE_ENDIAN);
        result[8] = 1;
        result[9] = 0;
        result[10] = (byte) (WA_PRIVATE_TIFF_TAG & 0xFF);
        result[11] = (byte) ((WA_PRIVATE_TIFF_TAG >>> 8) & 0xFF);
        result[12] = (byte) TIFF_TYPE_UNDEFINED;
        result[13] = 0;
        DataUtils.putInt(result, 14, jsonBytes.length, ByteOrder.LITTLE_ENDIAN);
        DataUtils.putInt(result, 18, 22, ByteOrder.LITTLE_ENDIAN);
        System.arraycopy(jsonBytes, 0, result, TIFF_WRAPPER_LENGTH, jsonBytes.length);
        return result;
    }

    /**
     * Reports whether the given byte array is an extended WebP (carries a
     * VP8X chunk immediately after the RIFF outer header).
     *
     * @apiNote
     * Mirrors WA Web's {@code WAWebWebp.isExtendedFile}: the first 12 bytes
     * must be the RIFF outer header, then the next four bytes (the first
     * chunk's FourCC) must be {@code "VP8X"}. The sticker pipeline produces
     * extended WebPs via libwebpmux before invoking this writer.
     *
     * @param source the candidate WebP bytes
     * @return {@code true} when {@code source} is a valid extended WebP
     */
    private static boolean isExtendedWebp(byte[] source) {
        if (source.length < RIFF_HEADER_LENGTH + VP8X_CHUNK_NAME.length) {
            return false;
        }
        if (source[0] != 'R' || source[1] != 'I' || source[2] != 'F' || source[3] != 'F') {
            return false;
        }
        if (source[8] != 'W' || source[9] != 'E' || source[10] != 'B' || source[11] != 'P') {
            return false;
        }
        for (var i = 0; i < VP8X_CHUNK_NAME.length; i++) {
            if (source[RIFF_HEADER_LENGTH + i] != VP8X_CHUNK_NAME[i]) {
                return false;
            }
        }
        return true;
    }

}
