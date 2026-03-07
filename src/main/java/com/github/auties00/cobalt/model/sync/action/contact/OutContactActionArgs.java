package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link OutContactAction}.
 *
 * <p>The sync index produced is {@code ["out_contact", userJid]}.
 *
 * @param userJid the JID of the outgoing contact
 */
public record OutContactActionArgs(String userJid) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{userJid};
    }
}
