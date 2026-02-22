package com.github.auties00.cobalt.model.sync.action.chat;


import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link ArchiveChatAction}.
 *
 * @param jid the JID to include in the index
 */
public record ArchiveChatActionArgs(Jid jid) implements SyncActionArgs {
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
