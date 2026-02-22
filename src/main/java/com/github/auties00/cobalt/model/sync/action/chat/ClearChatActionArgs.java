package com.github.auties00.cobalt.model.sync.action.chat;


import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link ClearChatAction}.
 *
 * @param jid   the JID to include in the index
 * @param flag1 the first boolean flag, serialized as {@code "1"} or {@code "0"}
 * @param flag2 the second boolean flag, serialized as {@code "1"} or {@code "0"}
 */
public record ClearChatActionArgs(Jid jid, boolean flag1, boolean flag2) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     *
     * @return a three-element array containing the JID string and both flags
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{jid.toString(), flag1 ? "1" : "0", flag2 ? "1" : "0"};
    }
}
