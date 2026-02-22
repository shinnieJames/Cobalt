package com.github.auties00.cobalt.model.sync.action.bot;


import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link AiThreadRenameAction}.
 *
 * @param jid   the JID to include in the index
 * @param value the string value to include in the index
 */
public record AiThreadRenameActionArgs(Jid jid, String value) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     *
     * @return a two-element array containing the JID string and the value
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{jid.toString(), value};
    }
}
