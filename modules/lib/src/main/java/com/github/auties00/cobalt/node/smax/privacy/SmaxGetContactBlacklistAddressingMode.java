package com.github.auties00.cobalt.node.smax.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxIqErrorResponseMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Selects the addressing mode for a {@link SmaxGetContactBlacklistRequest}.
 *
 * <p>The {@link #LID} mode drives the migration of the Last-Seen, About, Group-Add, and Profile-Picture privacy
 * lists to LID, while the {@link #PN} mode is the historical default for non-migrated clients.
 */
public enum SmaxGetContactBlacklistAddressingMode {
    /**
     * Selects the legacy phone-number addressing mode that emits a bare {@code <privacy>} envelope.
     *
     * <p>Used for clients still on the PN-addressed disallowed-list flow; the relay returns a
     * {@link SmaxGetContactBlacklistResponse.Success} variant whose {@code <user/>} children are PN JIDs.
     */
    PN,

    /**
     * Selects the migrated LID addressing mode that emits a {@code <privacy addressing_mode="lid">} envelope.
     *
     * <p>The relay returns a {@link SmaxGetContactBlacklistResponse.SuccessLID} variant whose {@code <user/>}
     * children carry the {@link SmaxGetContactBlacklistContactListId} discriminator.
     */
    LID
}
