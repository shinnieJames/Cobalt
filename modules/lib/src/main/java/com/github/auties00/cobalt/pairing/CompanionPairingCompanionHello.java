package com.github.auties00.cobalt.pairing;

import com.github.auties00.libsignal.key.SignalIdentityKeyPair;

/**
 * Holds the intermediate state that the companion must retain between
 * sending {@code companion_hello} and receiving {@code primary_hello}.
 *
 * @param linkCodePairingSecret                       the eight-character Crockford base32 pairing code shown to the user
 * @param linkCodePairingWrappedCompanionEphemeralPub the salt (32 bytes) || IV (16 bytes) || AES-CTR ciphertext of the companion ephemeral public key
 * @param companionEphemeralKeyPair                   the companion ADV ephemeral Curve25519 keypair; the private key is used during {@link #companionFinish}
 * @implNote WAWebcompanionHello: return extends({}, a, {linkCodePairingSecret: e})
 */
record CompanionPairingCompanionHello(
        String linkCodePairingSecret,
        byte[] linkCodePairingWrappedCompanionEphemeralPub,
        SignalIdentityKeyPair companionEphemeralKeyPair
) {

}
