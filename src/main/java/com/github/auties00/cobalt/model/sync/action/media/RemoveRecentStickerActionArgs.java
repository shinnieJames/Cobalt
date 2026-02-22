package com.github.auties00.cobalt.model.sync.action.media;


import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link RemoveRecentStickerAction}.
 *
 * @param value the string value to include in the index
 */
public record RemoveRecentStickerActionArgs(String value) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     *
     * @return a single-element array containing the value
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{value};
    }
}
