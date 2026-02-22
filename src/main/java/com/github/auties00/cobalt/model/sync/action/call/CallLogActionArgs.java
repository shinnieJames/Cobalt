package com.github.auties00.cobalt.model.sync.action.call;


import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link CallLogAction}.
 *
 * @param callerJid the caller's JID
 * @param callId    the call identifier
 * @param fromMe    whether the call was initiated by the current user
 */
public record CallLogActionArgs(Jid callerJid, String callId, boolean fromMe) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     *
     * @return a three-element array encoding the call log key
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{callerJid.toString(), callId, fromMe ? "1" : "0"};
    }
}
