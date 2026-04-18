package com.github.auties00.cobalt.pairing;

/**
 * Holds the output of {@link #companionFinish}: the wrapped key bundle
 * that must ship in the {@code companion_finish} IQ and the ADV master
 * secret that must be persisted as the store's {@code advSecretKey}
 * once the handshake is acknowledged.
 *
 * @param linkCodePairingWrappedKeyBundle the salt (32 bytes) || IV (12 bytes) || AES-GCM ciphertext containing the identity bundle
 * @param advSecret                       the HKDF-derived 32-byte ADV master secret
 * @param companionIdentityPublic         the companion's long-term identity public key; echoed into the IQ
 * @implNote WAWebcompanionFinishInternal: return {companionIdentityPublic, linkCodePairingWrappedKeyBundle, advSecret}
 */
record CompanionPairingCompanionFinish(
        byte[] linkCodePairingWrappedKeyBundle,
        byte[] advSecret,
        byte[] companionIdentityPublic
) {

}
