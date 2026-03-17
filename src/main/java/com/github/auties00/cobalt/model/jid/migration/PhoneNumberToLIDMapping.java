package com.github.auties00.cobalt.model.jid.migration;

import com.github.auties00.cobalt.model.jid.Jid;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * A mapping entry used in history sync that associates a phone number JID with
 * its corresponding LID JID.
 *
 * <p>Unlike {@link LIDMigrationMapping}, which represents identifiers as raw
 * numeric values and is used during real-time 1:1 chat migration, this class
 * represents identifiers as full {@link Jid} instances and is used during
 * history sync chunk processing. It appears as field 15
 * ({@code phoneNumberToLidMappings}) of the {@code Conversation} protobuf
 * message.
 *
 * <p>When a history sync chunk is received, the companion device iterates over
 * these mappings and, for each entry where both the
 * {@linkplain #pnJid() phone number JID} and the {@linkplain #lidJid() LID JID}
 * are present, builds its local phone-number-to-LID database so that future
 * lookups can resolve a phone number to the corresponding LID.
 */
@ProtobufMessage(name = "PhoneNumberToLIDMapping")
public final class PhoneNumberToLIDMapping {
    /**
     * The phone number JID being mapped, for example
     * {@code 1234567890@s.whatsapp.net}.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid pnJid;

    /**
     * The LID JID that corresponds to the phone number, for example
     * {@code 123456@lid}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    Jid lidJid;


    /**
     * Constructs a new {@code PhoneNumberToLIDMapping} with the specified JIDs.
     *
     * @param pnJid  the phone number JID, or {@code null}
     * @param lidJid the corresponding LID JID, or {@code null}
     */
    PhoneNumberToLIDMapping(Jid pnJid, Jid lidJid) {
        this.pnJid = pnJid;
        this.lidJid = lidJid;
    }

    /**
     * Returns the phone number JID being mapped.
     *
     * @return an {@code Optional} describing the phone number JID, or an empty
     *         {@code Optional} if not set
     */
    public Optional<Jid> pnJid() {
        return Optional.ofNullable(pnJid);
    }

    /**
     * Returns the LID JID that corresponds to the phone number.
     *
     * @return an {@code Optional} describing the LID JID, or an empty
     *         {@code Optional} if not set
     */
    public Optional<Jid> lidJid() {
        return Optional.ofNullable(lidJid);
    }

    /**
     * Sets the phone number JID being mapped.
     *
     * @param pnJid the new phone number JID, or {@code null}
     */
    public void setPnJid(Jid pnJid) {
        this.pnJid = pnJid;
    }

    /**
     * Sets the LID JID that corresponds to the phone number.
     *
     * @param lidJid the new LID JID, or {@code null}
     */
    public void setLidJid(Jid lidJid) {
        this.lidJid = lidJid;
    }
}
