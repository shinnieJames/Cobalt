package com.github.auties00.cobalt.model.sync.action.business;


import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link BusinessBroadcastAssociationAction}.
 *
 * @param first  the first string value
 * @param second the second string value
 */
public record BusinessBroadcastAssociationActionArgs(String first, String second) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     *
     * @return a two-element array containing both string values
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{first, second};
    }
}
