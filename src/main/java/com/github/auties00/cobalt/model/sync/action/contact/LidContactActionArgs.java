package com.github.auties00.cobalt.model.sync.action.contact;


import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link LidContactAction}.
 *
 * @param jid the JID to include in the index
 */
public record LidContactActionArgs(Jid jid) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     *
     * @return a single-element array containing the JID string
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{jid.toString()};
    }
}
