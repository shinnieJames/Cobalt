package com.github.auties00.cobalt.message.preview;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

/**
 * Downloads inline JPEG bytes for a preview card (group profile
 * picture, catalog product image, page favicon) and caps the payload
 * size so a hostile server cannot blow up the heap.
 *
 * <p>Mirrors WhatsApp Web's
 * {@code WAWebMediaDataUtils.getResizedThumbData} contract at the
 * download seam: WA fetches the URL, decodes the bytes, and resizes
 * to a small JPEG via the browser's {@code <canvas>}. Cobalt resizes
 * via {@link ImageIO} + {@link BufferedImage} when the optional
 * {@code java.desktop} module is on the runtime path, and falls back
 * to the source bytes when it is not (Cobalt declares
 * {@code requires static java.desktop} so the build still succeeds
 * on minimal runtimes).
 */
@WhatsAppWebModule(moduleName = "WAWebMediaDataUtils")
public final class PreviewThumbnailFetcher {
    /**
     * Maximum payload accepted by the downloader. Larger responses
     * are dropped because the inline JPEG slot is meant for a small
     * preview, and an attacker-controlled server should not be able
     * to allocate hundreds of megabytes.
     */
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    /**
     * Target side length (in pixels) of the resized JPEG thumbnail.
     */
    private static final int TARGET_SIZE = 100;

    /**
     * JPEG quality applied to the re-encoded thumbnail.
     */
    private static final float JPEG_QUALITY = 0.75f;

    /**
     * Hidden constructor for the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private PreviewThumbnailFetcher() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Fetches {@code uri} via {@code httpClient} and returns the raw
     * bytes capped at {@link #MAX_BYTES}, or {@code null} on any
     * failure.
     *
     * @param httpClient the HTTP client to issue the GET on
     * @param uri        the image URI
     * @param timeout    the per-request timeout
     * @return the image bytes, or {@code null} when the fetch failed
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaDataUtils", exports = "getResizedThumbData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static byte[] download(HttpClient httpClient, URI uri, Duration timeout) {
        if (httpClient == null || uri == null) {
            return null;
        }
        try {
            var request = HttpRequest.newBuilder(uri)
                    .timeout(timeout != null ? timeout : Duration.ofSeconds(15))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            var body = response.body();
            if (body == null || body.length == 0 || body.length > MAX_BYTES) {
                return null;
            }
            var resized = tryResize(body);
            return resized != null ? resized : body;
        } catch (Exception _) {
            return null;
        }
    }

    /**
     * Decodes {@code source} as an image, resizes it to a square
     * {@link #TARGET_SIZE} × {@link #TARGET_SIZE} JPEG, and returns
     * the encoded bytes.
     *
     * <p>When {@code java.desktop} is unavailable at runtime (the
     * module is declared
     * {@code requires static java.desktop}, so the JVM may run
     * without it) or the source bytes are not a recognised image
     * format, this method returns {@code null} and the caller falls
     * back to the source bytes.
     *
     * @param source the source bytes
     * @return the resized JPEG bytes, or {@code null} when the resize
     *         failed
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaDataUtils", exports = "getResizedThumbData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static byte[] tryResize(byte[] source) {
        try {
            var sourceImage = ImageIO.read(new ByteArrayInputStream(source));
            if (sourceImage == null) {
                return null;
            }
            var width = sourceImage.getWidth();
            var height = sourceImage.getHeight();
            if (width <= 0 || height <= 0) {
                return null;
            }
            var scale = Math.min((double) TARGET_SIZE / width, (double) TARGET_SIZE / height);
            var targetWidth = Math.max(1, (int) Math.round(width * scale));
            var targetHeight = Math.max(1, (int) Math.round(height * scale));
            var resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = null;
            try {
                graphics = resized.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
            } finally {
                if (graphics != null) {
                    graphics.dispose();
                }
            }
            var output = new ByteArrayOutputStream();
            var writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                return null;
            }
            var writer = writers.next();
            try (var stream = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(stream);
                var params = writer.getDefaultWriteParam();
                if (params.canWriteCompressed()) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionType("JPEG");
                    params.setCompressionQuality(JPEG_QUALITY);
                }
                writer.write(null, new IIOImage(resized, null, null), params);
            } finally {
                writer.dispose();
            }
            return output.toByteArray();
        } catch (NoClassDefFoundError | RuntimeException | IOException _) {
            return null;
        }
    }
}
