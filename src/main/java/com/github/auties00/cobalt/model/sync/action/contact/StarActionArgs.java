package com.github.auties00.cobalt.model.sync.action.contact;


import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link StarAction}.
 *
 * @param remote      the remote chat JID
 * @param id          the message identifier
 * @param fromMe      whether the message was sent by the current user
 * @param participant the participant JID, or {@code null} if not applicable
 */
public record StarActionArgs(Jid remote, String id, boolean fromMe, Jid participant) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     *
     * @return a four-element array encoding the message key
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{
                remote.toString(),
                id,
                fromMe ? "1" : "0",
                participant != null && !fromMe ? participant.toString() : "0"
        };
    }
}
