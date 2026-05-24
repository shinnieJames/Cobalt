package com.github.auties00.cobalt.message.receive.crypto;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.groups.SignalSenderKeyName;

/**
 * Factory that builds the {@link SignalSenderKeyName} composite key used by the Signal
 * group cipher to address a per-sender symmetric key inside a group, broadcast, or
 * community chat.
 *
 * @apiNote
 * Call {@link #create(Jid, Jid)} on every send and receive of an SKMSG payload and
 * every {@code SenderKeyDistributionMessage} import so the libsignal store sees the
 * same composite key regardless of which side built it.
 *
 * @implNote
 * This implementation mirrors WhatsApp Web's
 * {@code WAWebSignalCommonUtils.createSignalLikeSenderKeyName}, which combines the
 * group JID string with a Signal-style {@code user.device} address and feeds the pair
 * to the libsignal sender-key store.
 */
@WhatsAppWebModule(moduleName = "WAWebSignalCommonUtils")
public final class SenderKeyNameFactory {

    /**
     * Prevents instantiation of this utility class.
     *
     * @apiNote
     * The class exposes only the static {@link #create(Jid, Jid)} factory; the
     * constructor throws so reflective instantiation also fails.
     *
     * @throws UnsupportedOperationException always
     */
    private SenderKeyNameFactory() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns the {@link SignalSenderKeyName} that identifies the given sender's
     * sender-key inside the given group.
     *
     * @apiNote
     * Used by both the send path (sender-key distribution and group encryption) and
     * the receive path (group decryption and sender-key import) so the libsignal store
     * sees the same composite key from either direction. Looked up by
     * {@code WAWebCryptoLibraryDbCallbacksApi} when loading and storing sender-key
     * sessions.
     *
     * @implNote
     * This implementation hardcodes the device id portion of the WhatsApp Web suffix
     * (the literal {@code "0"} appended by {@code createSignalLikeAddress}) inside the
     * libsignal address by passing {@link Jid#device()} directly; callers that pass a
     * companion JID retain the actual device id rather than the WA Web zero-suffix.
     *
     * @param groupJid  the group, broadcast, or community JID hosting the sender key
     * @param senderJid the sender's device-level JID
     * @return the sender-key name keyed by the group JID string and the sender's
     *         Signal protocol address
     */
    @WhatsAppWebExport(moduleName = "WAWebSignalCommonUtils", exports = "createSignalLikeSenderKeyName",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static SignalSenderKeyName create(Jid groupJid, Jid senderJid) {
        var senderAddress = new SignalProtocolAddress(senderJid.user(), senderJid.device());
        return new SignalSenderKeyName(groupJid.toString(), senderAddress);
    }
}
