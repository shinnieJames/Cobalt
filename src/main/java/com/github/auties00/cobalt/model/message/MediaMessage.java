package com.github.auties00.cobalt.model.message;

import com.github.auties00.cobalt.model.message.media.*;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

public sealed interface MediaMessage extends ContextualMessage permits
    AudioMessage,
    DocumentMessage,
    ImageMessage,
    StickerMessage,
    VideoMessage {

    Optional<String> url();

    Optional<byte[]> fileSha256();

    OptionalLong fileLength();

    Optional<byte[]> mediaKey();

    Optional<byte[]> fileEncSha256();

    Optional<String> directPath();

    Optional<Instant> mediaKeyTimestamp();

    Optional<String> mimetype();

}
