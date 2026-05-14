package com.github.auties00.cobalt.message;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.key.SignalPreKeyPair;
import com.github.auties00.libsignal.state.SignalPreKeyBundleBuilder;

/**
 * Builds a libsignal session between two {@link WhatsAppStore} instances
 * so the {@link com.github.auties00.cobalt.message.send.crypto.MessageEncryption}
 * pipeline can encrypt for a recipient that the sender has not yet
 * messaged.
 *
 * <p>The helper mimics the production wire flow:
 *
 * <ol>
 *   <li>The recipient's store advertises a {@code SignalPreKeyBundle}
 *       (identity key + signed prekey + one-time prekey) — equivalent to
 *       what WA Web's prekey-fetch IQ returns.</li>
 *   <li>The sender's {@link SignalSessionCipher} processes the bundle,
 *       installing a session record keyed by the recipient's
 *       {@link com.github.auties00.libsignal.SignalProtocolAddress}.</li>
 * </ol>
 *
 * <p>After this call returns, the sender's
 * {@link com.github.auties00.cobalt.message.send.crypto.MessageEncryption#encryptForDevice}
 * will produce a {@code PreKeySignalMessage} that the recipient can
 * decrypt with its own {@link SignalSessionCipher}.
 */
public final class TestSignalSession {

    /**
     * Hidden constructor; this is a static-helper class.
     */
    private TestSignalSession() {
        throw new AssertionError("TestSignalSession is not instantiable");
    }

    /**
     * Seeds {@code store} with at least one one-time prekey so it can
     * publish a complete {@link com.github.auties00.libsignal.state.SignalPreKeyBundle}.
     *
     * <p>Idempotent — if the store already has a prekey, this is a no-op.
     *
     * @param store the store to seed
     * @return the (existing or newly-added) prekey
     */
    public static SignalPreKeyPair seedOneTimePreKey(WhatsAppStore store) {
        var existing = store.preKeys();
        if (!existing.isEmpty()) {
            return existing.iterator().next();
        }
        var preKey = SignalPreKeyPair.random(1);
        store.addPreKey(preKey);
        return preKey;
    }

    /**
     * Installs a session on {@code senderStore} so it can encrypt to
     * {@code recipientJid}. The recipient's identity is drawn from
     * {@code recipientStore} and packaged into a {@link com.github.auties00.libsignal.state.SignalPreKeyBundle}.
     *
     * @param senderStore    the sender's protocol store
     * @param recipientJid   the recipient device JID
     * @param recipientStore the recipient's protocol store
     */
    public static void establishSession(WhatsAppStore senderStore, Jid recipientJid, WhatsAppStore recipientStore) {
        var preKey = seedOneTimePreKey(recipientStore);
        var signedKey = recipientStore.signedKeyPair();
        var bundle = new SignalPreKeyBundleBuilder()
                .registrationId(recipientStore.registrationId())
                .deviceId(recipientJid.device())
                .preKeyId(preKey.id())
                .preKeyPublic(preKey.publicKey())
                .signedPreKeyId(signedKey.id())
                .signedPreKeyPublic(signedKey.publicKey())
                .signedPreKeySignature(signedKey.signature())
                .identityKey(recipientStore.identityKeyPair().publicKey())
                .build();
        new SignalSessionCipher(senderStore).process(recipientJid.toSignalAddress(), bundle);
    }
}
