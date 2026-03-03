package com.github.auties00.cobalt.model.jid.migration;

import com.github.auties00.cobalt.model.jid.Jid;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

/**
 * An individual mapping entry within a {@link LIDMigrationMappingSyncPayload}
 * that associates a phone number with its corresponding LID identifiers.
 *
 * <p>Each entry maps a phone number JID to the numeric
 * user part of one or two LID JIDs. The {@linkplain #pn() phone number} is the
 * raw numeric identifier (for example {@code 1234567890} for the JID
 * {@code 1234567890@s.whatsapp.net}). The {@linkplain #assignedLid() assigned LID}
 * is the identifier assigned by the primary device at migration time (for example
 * {@code 123456} for the JID {@code 123456@lid}).
 *
 * <p>The optional {@linkplain #latestLid() latest LID} represents the most recent
 * LID known to the primary device, which may differ from the assigned LID if the
 * user's LID was updated after the migration process began. When present, the
 * companion device uses it to detect mapping conflicts during thread migration.
 */
@ProtobufMessage(name = "LIDMigrationMapping")
public final class LIDMigrationMapping {
    /**
     * the phone number JID being migrated.
     *
     * <p>This value corresponds to the user portion of a standard WhatsApp JID,
     * for example {@code 1234567890} from {@code 1234567890@s.whatsapp.net}.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.UINT64)
    Jid pn;

    /**
     * the LID JID assigned by the primary device during
     * migration.
     *
     * <p>This value corresponds to the user portion of a LID JID, for example
     * {@code 123456} from {@code 123456@lid}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    Jid assignedLid;

    /**
     * the most recent LID JID known to the primary device,
     * or {@code null} if unavailable.
     *
     * <p>When this value differs from {@linkplain #assignedLid() assignedLid}, it
     * indicates that the user's LID was updated after the migration process began.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.UINT64)
    Jid latestLid;


    /**
     * Constructs a new {@code LIDMigrationMapping} with the specified values.
     *
     * @param pn          the phone number JID
     * @param assignedLid the LID assigned by the primary
     * @param latestLid   the most recent LID, or
     *                    {@code null} if unavailable
     */
    LIDMigrationMapping(Jid pn, Jid assignedLid, Jid latestLid) {
        this.pn = Objects.requireNonNull(pn);
        this.assignedLid = Objects.requireNonNull(assignedLid);
        this.latestLid = latestLid;
    }

    /**
     * Returns the phone number JID being migrated.
     *
     * @return the phone number as a numeric identifier
     */
    public Jid pn() {
        return pn;
    }

    /**
     * Returns the LID assigned by the primary device.
     *
     * @return the assigned LID as a numeric identifier
     */
    public Jid assignedLid() {
        return assignedLid;
    }

    /**
     * Returns the most recent LID known to the primary
     * device.
     *
     * @return an {@code Optional} containing the latest LID if available, or
     *         an empty {@code Optional} if not set
     */
    public Optional<Jid> latestLid() {
        return Optional.ofNullable(latestLid);
    }

    /**
     * Sets the phone number JID being migrated.
     *
     * @param pn the new phone number numeric identifier
     * @return this instance for chaining
     */
    public LIDMigrationMapping setPn(Jid pn) {
        Objects.requireNonNull(pn);

        this.pn = pn;
        return this;
    }

    /**
     * Sets the LID assigned by the primary device.
     *
     * @param assignedLid the new assigned LID numeric identifier
     * @return this instance for chaining
     */
    public LIDMigrationMapping setAssignedLid(Jid assignedLid) {
        Objects.requireNonNull(assignedLid);

        this.assignedLid = assignedLid;
        return this;
    }

    /**
     * Sets the most recent LID known to the primary
     * device.
     *
     * @param latestLid the new latest LID, or {@code null} to clear
     * @return this instance for chaining
     */
    public LIDMigrationMapping setLatestLid(Jid latestLid) {
        this.latestLid = latestLid;
        return this;
    }
}
