package com.github.auties00.cobalt.model.sync.action.chat;


import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link DeleteChatAction}.
 *
 * @param jid  the JID to include in the index
 * @param flag the boolean flag, serialized as {@code "1"} or {@code "0"}
 */
public record DeleteChatActionArgs(Jid jid, boolean flag) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     *
     * @return a two-element array containing the JID string and the flag
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{jid.toString(), flag ? "1" : "0"};
    }
}
