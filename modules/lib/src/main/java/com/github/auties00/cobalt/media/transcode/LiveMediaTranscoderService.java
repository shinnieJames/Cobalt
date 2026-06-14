package com.github.auties00.cobalt.media.transcode;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
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
 * Live implementation of {@link MediaTranscoderService} that prepares outgoing media for upload
 * to the WhatsApp CDN by dispatching to the matching per-format pipeline.
 *
 * <p>Dispatch pattern-matches on the sealed {@link MediaProvider} hierarchy so every variant is
 * routed at compile time: {@link AudioMessage} splits on {@link AudioMessage#ptt()} so
 * push-to-talk voice notes reach the OGG-Opus pipeline; internal-blob providers
 * ({@link ExternalBlobReference}, {@link HistorySyncNotification}) and
 * {@link ExtendedTextMessage} pass through unchanged because their bytes already match the wire
 * format.
 *
 * @implNote
 * This implementation owns one instance of each per-format pipeline as a private
 * final field. The {@link InputStream} overload extracts the {@link FileChannel}
 * directly when the stream is a {@link FileInputStream} and otherwise drains the
 * stream to a delete-on-success temp file before delegating to the channel-based
 * dispatch, so no source spill occurs for the common file-backed case.
 */
public final class LiveMediaTranscoderService implements MediaTranscoderService {
    /**
     * Filename prefix for the source-spill temp file created when an
     * {@link InputStream} entry point is taken and the stream is not already
     * file-backed.
     */
    private static final String SOURCE_SPILL_PREFIX = "cobalt-src-";

    /**
     * Filename suffix for the source-spill temp file.
     */
    private static final String SOURCE_SPILL_SUFFIX = ".tmp";

    /**
     * The owning client, consulted for the media-upload quality preference on
     * every channel dispatch and threaded into the pipelines that need
     * session-level state.
     */
    private final LinkedWhatsAppClient client;

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
     * <p>Instantiates one pipeline per supported media format and threads
     * {@code abPropsService} and {@code mediaConnectionService} into the
     * {@link TextPipeline} so it can gate link-preview behaviour on AB props and
     * upload link-preview thumbnails. Invoked once per session by the owning
     * client.
     *
     * @param client                 the owning client; must not be {@code null}
     * @param abPropsService         the AB-props service threaded into the text
     *                               pipeline; must not be {@code null}
     * @param mediaConnectionService the CDN credentials service threaded into the
     *                               text pipeline for link-preview thumbnail
     *                               uploads; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public LiveMediaTranscoderService(LinkedWhatsAppClient client, ABPropsService abPropsService,
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
     * {@inheritDoc}
     *
     * @implNote
     * When the caller already holds a file on disk this variant avoids any source-side spill.
     * {@link MediaProvider} variants without a dedicated byte-level pipeline, namely
     * {@link ExternalBlobReference} application-state and history blobs,
     * {@link HistorySyncNotification} archives, and {@link ExtendedTextMessage} link previews,
     * pass through as a {@link MediaPayload.OfPath} that references {@code source} without owning
     * it, so {@link MediaPayload#close()} leaves the caller's file alone.
     */
    @Override
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
     * {@inheritDoc}
     *
     * @implNote
     * The stream is closed on every exit path. When the stream is already a
     * {@link FileInputStream} the underlying {@link FileChannel} is used directly without
     * spilling; otherwise the stream is drained to a delete-on-success temp file before the
     * channel-based dispatch runs. For the {@link DocumentMessage} branch the spilled file is
     * handed to the returned {@link MediaPayload.OfPath} so the upload reads the same file the
     * document pipeline parsed.
     */
    @Override
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
     * Drains {@code source} into a temp file and dispatches the path or channel
     * form depending on the provider.
     *
     * <p>For {@link DocumentMessage} and the pass-through providers the spilled
     * temp file becomes an owning {@link MediaPayload.OfPath} so the upload reads
     * from it directly; for the encoded-output providers the spill is opened as a
     * read channel for dispatch and deleted in the {@code finally} block once
     * dispatch returns. The temp file is retained only on the branches that
     * transfer its ownership to the returned payload.
     *
     * @param provider the upload target
     * @param source   the source stream, not yet drained
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
     * Routes a channel-backed source to the matching encoded-output pipeline.
     *
     * <p>Reachable only for providers that have a channel-based pipeline. The
     * {@link DocumentMessage} and pass-through branches are unreachable here
     * because the {@link InputStream} entry point routes them through the path
     * form, so they fail fast with an {@link IllegalStateException} to surface any
     * future routing mistake.
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
     * Opens {@code source} as a read channel and dispatches it to the matching
     * channel-based pipeline.
     *
     * @param provider the upload target
     * @param source   the path to open
     * @return the encoded payload
     * @throws WhatsAppMediaException.Processing if opening the source or the
     *                                           pipeline fails
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
     * Dispatches a channel-backed source to the encoded-output pipeline that
     * matches the provider.
     *
     * <p>Resolves the media-upload quality preference from the store, defaulting
     * to {@link SettingsSyncAction.MediaQualitySetting#STANDARD} when unset, and
     * passes it to the image, video, and non-voice audio pipelines that honour it.
     * Voice notes route to {@link PttPipeline} and stickers to
     * {@link StickerPipeline}, neither of which takes a quality argument. The
     * {@code channel} is not closed by this method. The {@link DocumentMessage}
     * and pass-through branches never reach this dispatch and fail fast with an
     * {@link IllegalStateException}.
     *
     * @param provider the upload target
     * @param channel  the source channel; not closed by this method
     * @return the encoded payload
     * @throws WhatsAppMediaException.Processing if the pipeline fails
     */
    private MediaPayload dispatchToChannel(MediaProvider provider, FileChannel channel)
            throws WhatsAppMediaException.Processing {
        var quality = client.store().settingsStore().mediaUploadQuality()
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
     * Wraps a caller-provided path as a non-owning {@link MediaPayload.OfPath} for
     * pass-through providers.
     *
     * <p>The payload reports the file's current size and is constructed with
     * {@code ownsFile} false, so {@link MediaPayload#close()} never deletes the
     * caller's file.
     *
     * @param source the caller's file
     * @return a non-owning path payload over {@code source}
     * @throws WhatsAppMediaException.Processing if the file cannot be sized
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

    @Override
    public void decorate(Jid chatJid, ExtendedTextMessage message) {
        textPipeline.run(chatJid, message);
    }
}
