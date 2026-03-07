package com.github.auties00.cobalt.model.sync.action.setting;

import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link NctSaltSyncAction}.
 *
 * <p>The sync index produced is {@code ["nct_salt_sync"]}.
 */
public record NctSaltSyncActionArgs() implements SyncActionArgs {
    /**
     * {@inheritDoc}
     */
    @Override
    public String[] toIndexArgs() {
        return new String[0];
    }
}
