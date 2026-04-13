package com.github.auties00.cobalt.message.receive.crypto;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.groups.SignalSenderKeyName;


/**
 * Factory for creating {@link SignalSenderKeyName} objects from JIDs.
 * <p>
 * Used by both message sending (for encryption and sender key distribution)
 * and message receiving (for decryption and processing received sender keys).
 *
 * @implNote WAWebSignalCommonUtils.createSignalLikeSenderKeyName: constructs
 * a sender key name as {@code groupJid + "::" + createSignalAddress(senderJid)}.
 * In Cobalt, the SignalSenderKeyName record handles the internal format.
 */
public final class SenderKeyNameFactory {

    /**
     * Prevents instantiation of this utility class.
     *
     * @implNote NO_WA_BASIS
     */
    private SenderKeyNameFactory() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Creates a {@link SignalSenderKeyName} from group and sender JIDs.
     * <p>
     * The sender key name uniquely identifies a sender's key within a group,
     * combining:
     * <ul>
     *   <li>Group JID - identifies the group</li>
     *   <li>Sender address - identifies the sender within the group</li>
     * </ul>
     *
     * @param groupJid  the group JID
     * @param senderJid the sender's device JID
     * @return the {@link SignalSenderKeyName} for use with the Signal group cipher
     *
     * @implNote WAWebSignalCommonUtils.createSignalLikeSenderKeyName: constructs
     * {@code groupJid + "::" + createSignalAddress(senderJid, deviceId)}.
     * In Cobalt, the sender address is created from the JID's
     * {@code user()} and {@code device()} components.
     */
    public static SignalSenderKeyName create(Jid groupJid, Jid senderJid) {
        var senderAddress = new SignalProtocolAddress(senderJid.user(), senderJid.device());
        return new SignalSenderKeyName(groupJid.toString(), senderAddress);
    }

}
