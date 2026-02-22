package com.github.auties00.cobalt.model.sync.action.chat;


import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link InteractiveMessageAction}.
 *
 * @param remote      the remote chat JID
 * @param id          the message identifier
 * @param fromMe      whether the message was sent by the current user
 * @param participant the participant JID, or {@code null} if not applicable
 * @param extra       the additional string argument appended after the message key
 */
public record InteractiveMessageActionArgs(Jid remote, String id, boolean fromMe, Jid participant, String extra) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     *
     * @return a five-element array encoding the message key followed by the extra string
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{
                remote.toString(),
                id,
                fromMe ? "1" : "0",
                participant != null && !fromMe ? participant.toString() : "0",
                extra
        };
    }
}
