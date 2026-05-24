package com.github.auties00.cobalt.media.transcode;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.media.MediaConnectionService;
import com.github.auties00.cobalt.media.MediaPayload;
import com.github.auties00.cobalt.media.transcode.audio.AudioPipeline;
import com.github.auties00.cobalt.media.transcode.audio.PttPipeline;
import com.github.auties00.cobalt.media.transcode.document.DocumentPipeline;
import com.github.auties00.cobalt.media.transcode.image.ImagePipeline;
import com.github.auties00.cobalt.media.transcode.sticker.StickerPipeline;
import com.github.auties00.cobalt.media.transcode.text.TextPipeline;
import com.github.auties00.cobalt.media.transcode.video.VideoPipeline;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.media.ExternalBlobReference;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.message.media.AudioMessage;
import com.github.auties00.cobalt.model.message.media.DocumentMessage;
import com.github.auties00.cobalt.model.message.media.ImageMessage;
import com.github.auties00.cobalt.model.message.media.StickerMessage;
import com.github.auties00.cobalt.model.message.media.VideoMessage;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncNotification;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.preference.Sticker;
import com.github.auties00.cobalt.model.sync.action.media.StickerAction;
import com.github.auties00.cobalt.model.sync.action.setting.SettingsSyncAction;
import com.github.auties00.cobalt.props.ABPropsService;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Prepares outgoing media for upload to the WhatsApp CDN by dispatching
 * to the matching per-format pipeline.
 *
 * @apiNote
 * Owned by {@code LinkedWhatsAppClient} and consulted from two call
 * sites: the byte-level {@link #transcode(MediaProvider, Path)} (and its
 * {@link InputStream} adapter) is invoked from {@code uploadMedia}
 * immediately before the encrypted upload is handed to
 * {@link com.github.auties00.cobalt.media.MediaConnection#upload(MediaProvider, MediaPayload)};
 * the orchestration entry point
 * {@link #decorate(Jid, ExtendedTextMessage)} is invoked from the
 * message-sending pipeline to enrich a text message with a rich link
 * preview. Not part of the public API surface.
 *
 * @implNote
 * This implementation owns the per-format pipeline instances as private
 * final fields and pattern-matches on the sealed {@link MediaProvider}
 * hierarchy so every variant is routed at compile time.
 * {@link AudioMessage} splits on {@link AudioMessage#ptt()} so
 * push-to-talk voice notes hit the OGG-Opus pipeline.
 * {@link ExtendedTextMessage} is the entry point for the link-preview
 * orchestration; its byte-level upload bypasses the transcoder because
 * the JPEG payload already matches the wire format. Internal-blob
 * providers ({@link ExternalBlobReference}, {@link HistorySyncNotification})
 * pass through unchanged so application-state and history-sync uploads
 * stay byte-identical. The {@link InputStream} overload skips the source
 * spill when the stream is a {@link FileInputStream} (channel extracted
 * via {@link FileInputStream#getChannel()}) and otherwise spills to a
 * delete-on-success temp file before delegating to the channel-based
 * dispatch.
 */
public final class MediaTranscoderService {
    /**
     * Prefix used for the source-spill temp file when an
     * {@link InputStream} entry point is taken and the stream is not
     * already file-backed.
     */
    private static final String SOURCE_SPILL_PREFIX = "cobalt-src-";

    /**
     * Suffix used for the source-spill temp file.
     */
    private static final String SOURCE_SPILL_SUFFIX = ".tmp";

    /**
     * The owning client; consulted for the media-upload quality
     * preference on every transcode call and threaded into the pipelines
     * that need session-level state.
     */
    private final WhatsAppClient client;

    /**
     * The image transcoder.
     */
    private final ImagePipeline imagePipeline;

    /**
     * The video transcoder.
     */
    private final VideoPipeline videoPipeline;

    /**
     * The non-voice audio transcoder.
     */
    private final AudioPipeline audioPipeline;

    /**
     * The voice-note transcoder.
     */
    private final PttPipeline pttPipeline;

    /**
     * The sticker transcoder.
     */
    private final StickerPipeline stickerPipeline;

    /**
     * The document transcoder.
     */
    private final DocumentPipeline documentPipeline;

    /**
     * The link-preview orchestrator.
     */
    private final TextPipeline textPipeline;

    /**
     * Constructs the media-transcoder service bound to {@code client}.
     *
     * @apiNote
     * Invoked once per session by {@code LinkedWhatsAppClient}.
     *
     * @param client                 the owning client; must not be
     *                               {@code null}
     * @param abPropsService         the AB-props service threaded into
     *                               the text pipeline; must not be
     *                               {@code null}
     * @param mediaConnectionService the CDN credentials singleton
     *                               threaded into the text pipeline for
     *                               link-preview thumbnail uploads; must
     *                               not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public MediaTranscoderService(WhatsAppClient client, ABPropsService abPropsService,
                                  MediaConnectionService mediaConnectionService) {
        this.client = Objects.requireNonNull(client, "client");
        Objects.requireNonNull(abPropsService, "abPropsService");
        Objects.requireNonNull(mediaConnectionService, "mediaConnectionService");
        this.imagePipeline = new ImagePipeline();
        this.videoPipeline = new VideoPipeline();
        this.audioPipeline = new AudioPipeline();
        this.pttPipeline = new PttPipeline();
        this.stickerPipeline = new StickerPipeline();
        this.documentPipeline = new DocumentPipeline();
        this.textPipeline = new TextPipeline(client, abPropsService, mediaConnectionService);
    }

    /**
     * Transcodes the source file for the upload slot encoded by
     * {@code provider} and applies the resulting codec metadata to
     * {@code provider} in place.
     *
     * @apiNote
     * Primary entry point: when the caller has a file on disk this
     * variant avoids any source-side spill. The returned
     * {@link MediaPayload} carries the encoded plaintext (heap byte array
     * for image/sticker outputs; temp file for muxed outputs and for the
     * document pass-through). The caller must invoke
     * {@link MediaPayload#close()} after the upload completes to release
     * any owned temp file. {@link MediaProvider} variants without a
     * dedicated byte-level pipeline ({@link ExternalBlobReference}
     * application-state and history blobs, {@link HistorySyncNotification}
     * archives, and {@link ExtendedTextMessage} link previews whose HQ
     * thumbnail upload bypasses this method entirely) pass through as a
     * {@link MediaPayload.OfPath} that references {@code source} with
     * {@code ownsFile} = {@code false}.
     *
     * @param provider the upload target; codec-derived fields are applied
     *                 to this instance, never {@code null}
     * @param source   the raw user-provided file path, never {@code null}
     * @return the encoded payload
     * @throws WhatsAppMediaException.Processing if the selected pipeline
     *         fails to decode, encode, or buffer the source
     * @throws NullPointerException              if {@code provider} or
     *                                           {@code source} is
     *                                           {@code null}
     */
    public MediaPayload transcode(MediaProvider provider, Path source)
            throws WhatsAppMediaException.Processing {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(source, "source");
        return switch (provider) {
            case DocumentMessage _ -> documentPipeline.run(provider, source);
            case ExtendedTextMessage _,
                 ExternalBlobReference _,
                 HistorySyncNotification _ -> passThroughPath(source);
            case ImageMessage _,
                 VideoMessage _,
                 AudioMessage _,
                 StickerMessage _,
                 StickerAction _,
                 Sticker _ -> dispatchToPipeline(provider, source);
        };
    }

    /**
     * Transcodes the source stream for the upload slot encoded by
     * {@code provider} and applies the resulting codec metadata to
     * {@code provider} in place.
     *
     * @apiNote
     * Convenience entry point for callers that hold a stream rather than
     * a file. The stream is closed by this method on every exit path.
     * When the stream is already a {@link FileInputStream} the
     * underlying {@link FileChannel} is used directly without spilling;
     * otherwise the stream is drained to a delete-on-success temp file
     * before the channel-based dispatch runs. For the
     * {@link DocumentMessage} branch the spilled file is transferred to
     * the returned {@link MediaPayload.OfPath} so the upload reads from
     * the same file that PDFBox parsed.
     *
     * @param provider the upload target; codec-derived fields are applied
     *                 to this instance, never {@code null}
     * @param source   the raw user-provided stream; closed before this
     *                 method returns, never {@code null}
     * @return the encoded payload
     * @throws WhatsAppMediaException.Processing if buffering, decoding,
     *         encoding, or muxing fails
     * @throws NullPointerException              if {@code provider} or
     *                                           {@code source} is
     *                                           {@code null}
     */
    public MediaPayload transcode(MediaProvider provider, InputStream source)
            throws WhatsAppMediaException.Processing {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(source, "source");
        try (source) {
            if (source instanceof FileInputStream fis) {
                return transcodeFromChannel(provider, fis.getChannel());
            }
            return transcodeFromStream(provider, source);
        } catch (IOException e) {
            throw new WhatsAppMediaException.Processing("failed to consume source stream", e);
        }
    }

    /**
     * Drains {@code source} into a temp file and dispatches the path or
     * channel form depending on the provider.
     *
     * @param provider the upload target
     * @param source   the source stream (not yet drained)
     * @return the encoded payload
     * @throws IOException                       if the spill fails
     * @throws WhatsAppMediaException.Processing if the pipeline fails
     */
    private MediaPayload transcodeFromStream(MediaProvider provider, InputStream source)
            throws IOException, WhatsAppMediaException.Processing {
        var spill = Files.createTempFile(SOURCE_SPILL_PREFIX, SOURCE_SPILL_SUFFIX);
        var keepSpill = false;
        try {
            try (var out = Files.newOutputStream(spill, StandardOpenOption.WRITE)) {
                source.transferTo(out);
            }
            if (provider instanceof DocumentMessage) {
                var raw = documentPipeline.run(provider, spill);
                keepSpill = true;
                return new MediaPayload.OfPath(spill, raw.length(), true);
            }
            if (provider instanceof ExtendedTextMessage
                    || provider instanceof ExternalBlobReference
                    || provider instanceof HistorySyncNotification) {
                var spillSize = Files.size(spill);
                keepSpill = true;
                return new MediaPayload.OfPath(spill, spillSize, true);
            }
            try (var channel = FileChannel.open(spill, StandardOpenOption.READ)) {
                return dispatchToChannel(provider, channel);
            }
        } finally {
            if (!keepSpill) {
                try {
                    Files.deleteIfExists(spill);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Routes a channel-backed source to the matching pipeline.
     *
     * @param provider the upload target
     * @param channel  the source channel
     * @return the encoded payload
     * @throws WhatsAppMediaException.Processing if the pipeline fails
     */
    private MediaPayload transcodeFromChannel(MediaProvider provider, FileChannel channel)
            throws WhatsAppMediaException.Processing {
        return switch (provider) {
            case DocumentMessage _ ->
                    throw new IllegalStateException(
                            "DocumentMessage must dispatch through the path form");
            case ExtendedTextMessage _,
                 ExternalBlobReference _,
                 HistorySyncNotification _ ->
                    throw new IllegalStateException(
                            "pass-through providers must dispatch through the path form");
            case ImageMessage _,
                 VideoMessage _,
                 AudioMessage _,
                 StickerMessage _,
                 StickerAction _,
                 Sticker _ -> dispatchToChannel(provider, channel);
        };
    }

    /**
     * Opens {@code source} and dispatches to the channel-based pipeline.
     *
     * @param provider the upload target
     * @param source   the path to open
     * @return the encoded payload
     * @throws WhatsAppMediaException.Processing if opening or pipeline
     *         fails
     */
    private MediaPayload dispatchToPipeline(MediaProvider provider, Path source)
            throws WhatsAppMediaException.Processing {
        try (var channel = FileChannel.open(source, StandardOpenOption.READ)) {
            return dispatchToChannel(provider, channel);
        } catch (IOException e) {
            throw new WhatsAppMediaException.Processing("failed to open media source", e);
        }
    }

    /**
     * Dispatches to the channel-based pipeline that matches the provider.
     *
     * @param provider the upload target
     * @param channel  the source channel; not closed by this method
     * @return the encoded payload
     * @throws WhatsAppMediaException.Processing if the pipeline fails
     */
    private MediaPayload dispatchToChannel(MediaProvider provider, FileChannel channel)
            throws WhatsAppMediaException.Processing {
        var quality = client.store().mediaUploadQuality()
                .orElse(SettingsSyncAction.MediaQualitySetting.STANDARD);
        return switch (provider) {
            case ImageMessage _ -> imagePipeline.run(provider, channel, quality);
            case VideoMessage _ -> videoPipeline.run(provider, channel, quality);
            case AudioMessage audio when audio.ptt() -> pttPipeline.run(provider, channel);
            case AudioMessage _ -> audioPipeline.run(provider, channel, quality);
            case StickerMessage _, StickerAction _, Sticker _ ->
                    stickerPipeline.run(provider, channel);
            case DocumentMessage _,
                 ExtendedTextMessage _,
                 ExternalBlobReference _,
                 HistorySyncNotification _ ->
                    throw new IllegalStateException(
                            "channel dispatch does not apply to " + provider.getClass());
        };
    }

    /**
     * Wraps a caller-provided path as a non-owning
     * {@link MediaPayload.OfPath} for pass-through providers.
     *
     * @param source the caller's file
     * @return a non-owning path payload
     * @throws WhatsAppMediaException.Processing if the file cannot be
     *         sized
     */
    private static MediaPayload passThroughPath(Path source)
            throws WhatsAppMediaException.Processing {
        long length;
        try {
            length = Files.size(source);
        } catch (IOException e) {
            throw new WhatsAppMediaException.Processing("failed to size pass-through source", e);
        }
        return new MediaPayload.OfPath(source, length, false);
    }

    /**
     * Resolves the first URL in {@code message}'s body into a rich link
     * preview card and stamps the result onto {@code message}.
     *
     * @param chatJid the target chat JID
     * @param message the outgoing message; mutated in place
     */
    public void decorate(Jid chatJid, ExtendedTextMessage message) {
        textPipeline.run(chatJid, message);
    }
}
