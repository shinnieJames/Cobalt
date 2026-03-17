package com.github.auties00.cobalt.model.setting;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "WallpaperSettings")
public final class WallpaperSettings {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String filename;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
    Integer opacity;


    WallpaperSettings(String filename, Integer opacity) {
        this.filename = filename;
        this.opacity = opacity;
    }

    public Optional<String> filename() {
        return Optional.ofNullable(filename);
    }

    public OptionalInt opacity() {
        return opacity == null ? OptionalInt.empty() : OptionalInt.of(opacity);
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setOpacity(Integer opacity) {
        this.opacity = opacity;
    }
}
