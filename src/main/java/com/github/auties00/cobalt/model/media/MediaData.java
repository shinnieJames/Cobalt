package com.github.auties00.cobalt.model.media;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * A container that holds local storage metadata for a media attachment
 * associated with a message.
 *
 * <p>This message is defined in {@code WAWebProtobufsWeb.pb} as part of the
 * {@code WebMessageInfo} structure. It appears at field index 38 in
 * {@code WebMessageInfo} to reference the on-device file path where a media
 * attachment has been downloaded or cached. It is also used at field index 42
 * for quoted sticker data.
 */
@ProtobufMessage(name = "MediaData")
public final class MediaData {
    /**
     * The path on the local filesystem where the media file is stored.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String localPath;

    /**
     * Constructs a new {@code MediaData} with the given local path.
     *
     * @param localPath the local filesystem path to the media file
     */
    MediaData(String localPath) {
        this.localPath = localPath;
    }

    /**
     * Returns the path on the local filesystem where the media file is stored.
     *
     * @return an {@link Optional} containing the local path, or empty if not set
     */
    public Optional<String> localPath() {
        return Optional.ofNullable(localPath);
    }

    /**
     * Sets the local filesystem path to the media file.
     *
     * @param localPath the local path
     */
    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }
}
