package com.github.auties00.cobalt.model.sync.action.device;


import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link NuxAction}.
 *
 * @param value the string value to include in the index
 */
public record NuxActionArgs(String value) implements SyncActionArgs {
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
