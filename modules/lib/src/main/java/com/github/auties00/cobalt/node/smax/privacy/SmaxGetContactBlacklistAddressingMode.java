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
 * The addressing-mode selector for a {@link SmaxGetContactBlacklistRequest}.
 *
 * @apiNote
 * Selects between the legacy phone-number-addressed disallowed-list query and the LID-promoted variant; the
 * LID variant is what {@code WAWebQueryPrivacyDisallowedListLidJob.queryPrivacyDisallowedListLid} dispatches
 * when migrating Last-Seen, About, Group-Add, or Profile-Picture privacy lists to LID, while the PN variant is
 * the historical default for non-migrated clients.
 */
public enum SmaxGetContactBlacklistAddressingMode {
    /**
     * The legacy phone-number addressing mode that emits a bare {@code <privacy>} envelope.
     *
     * @apiNote
     * Used for clients still on the PN-addressed disallowed-list flow; the relay returns a
     * {@link SmaxGetContactBlacklistResponse.Success} variant whose {@code <user/>} children are PN JIDs.
     */
    PN,

    /**
     * The migrated LID addressing mode that emits a {@code <privacy addressing_mode="lid">} envelope.
     *
     * @apiNote
     * Required by {@code WAWebQueryPrivacyDisallowedListLidJob.queryPrivacyDisallowedListLid}; the relay returns
     * a {@link SmaxGetContactBlacklistResponse.SuccessLID} variant whose {@code <user/>} children carry the
     * {@link SmaxGetContactBlacklistContactListId} discriminator.
     */
    LID
}
