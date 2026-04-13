package com.github.auties00.cobalt.message.receive.stanza;

import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Objects;
import java.util.Optional;

/**
 * Parsed participant entry from the {@code <participants>} child of a
 * broadcast or peer-broadcast message stanza.
 *
 * <p>Each {@code <to>} child within {@code <participants>} represents
 * one recipient in the broadcast contact list (BCL).  The entry carries
 * the recipient JID and optional LID/PN mapping information used for
 * the phone-number-to-LID migration.
 *
 * @implNote WAWebHandleMsgParser function y(): parses each {@code <to>}
 * child within {@code <participants>} to extract jid, eph_setting,
 * peer_recipient_lid, peer_recipient_pn, peer_recipient_username,
 * and recipient_latest_lid.
 */
public final class MessageReceiveBroadcastParticipant {
    private final Jid jid;
    private final String ephSetting;
    private final Jid peerRecipientLid;
    private final Jid peerRecipientPn;
    private final String peerRecipientUsername;
    private final Jid recipientLatestLid;

    public MessageReceiveBroadcastParticipant(
            Jid jid,
            String ephSetting,
            Jid peerRecipientLid,
            Jid peerRecipientPn,
            String peerRecipientUsername,
            Jid recipientLatestLid
    ) {
        this.jid = Objects.requireNonNull(jid, "jid cannot be null");
        this.ephSetting = ephSetting;
        this.peerRecipientLid = peerRecipientLid;
        this.peerRecipientPn = peerRecipientPn;
        this.peerRecipientUsername = peerRecipientUsername;
        this.recipientLatestLid = recipientLatestLid;
    }

    public Jid jid() {
        return jid;
    }

    public Optional<String> ephSetting() {
        return Optional.ofNullable(ephSetting);
    }

    public Optional<Jid> peerRecipientLid() {
        return Optional.ofNullable(peerRecipientLid);
    }

    public Optional<Jid> peerRecipientPn() {
        return Optional.ofNullable(peerRecipientPn);
    }

    public Optional<String> peerRecipientUsername() {
        return Optional.ofNullable(peerRecipientUsername);
    }

    public Optional<Jid> recipientLatestLid() {
        return Optional.ofNullable(recipientLatestLid);
    }
}
