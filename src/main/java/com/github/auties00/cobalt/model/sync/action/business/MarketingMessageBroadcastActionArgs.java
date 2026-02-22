package com.github.auties00.cobalt.model.sync.action.business;


import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link MarketingMessageBroadcastAction}.
 *
 * @param first  the first string value
 * @param second the second string value
 */
public record MarketingMessageBroadcastActionArgs(String first, String second) implements SyncActionArgs {
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
