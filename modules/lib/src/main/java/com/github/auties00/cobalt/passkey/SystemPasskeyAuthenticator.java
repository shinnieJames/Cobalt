package com.github.auties00.cobalt.passkey;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientPasskeyAuthenticator;
import com.github.auties00.warden.PasskeyException;
import com.github.auties00.warden.PasskeyRequest;
import com.github.auties00.warden.WardenAuthenticator;

/**
 * Authenticator that drives the host operating system's native WebAuthn API to assert a passkey, backed by
 * the {@code com.github.auties00:warden} library.
 *
 * <p>On a desktop the platform exposes a credential service (Windows Hello through {@code webauthn.dll},
 * macOS through AuthenticationServices, Linux through {@code libfido2}) that can reach a {@code whatsapp.com}
 * passkey stored on the machine, synced from the user's phone, or reached over the cross-device hybrid
 * transport. This bridge maps the {@link LinkedWhatsAppClientPasskeyAuthenticator} contract onto Warden's
 * {@link WardenAuthenticator}, so a desktop application can satisfy WhatsApp's passkey ceremonies with a real
 * platform prompt and no remote handoff.
 *
 * @implNote The ceremony is anchored to {@link WardenAuthenticator#NO_WINDOW}, letting the backend pick a
 * platform-default window, because this bridge has no application window of its own. Warden's native bindings
 * go through the foreign-function and memory API, so an application running Cobalt as a named module must
 * grant native access to the {@code com.github.auties00.warden} module.
 */
public final class SystemPasskeyAuthenticator implements LinkedWhatsAppClientPasskeyAuthenticator {
    /**
     * The Warden authenticator driving the host platform's native passkey service.
     */
    private final WardenAuthenticator authenticator;

    /**
     * Constructs a bridge over the given Warden authenticator.
     *
     * @param authenticator the host-platform Warden authenticator
     */
    private SystemPasskeyAuthenticator(WardenAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    /**
     * Returns an authenticator bound to the host operating system's native WebAuthn API.
     *
     * <p>The Warden authenticator is resolved eagerly, so an unsupported platform or an uninitialisable
     * service surfaces here rather than on the first ceremony.
     *
     * @return a system-backed authenticator
     * @throws UnsupportedOperationException if the host platform has no supported native WebAuthn API
     * @throws IllegalStateException         if the platform service is present but cannot be initialised
     */
    public static SystemPasskeyAuthenticator create() {
        try {
            return new SystemPasskeyAuthenticator(WardenAuthenticator.create());
        } catch (PasskeyException exception) {
            throw new IllegalStateException("The host passkey service could not be initialised", exception);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation maps the request onto a Warden {@link PasskeyRequest}, runs the platform
     * ceremony with no anchoring window, and maps the resulting
     * {@link com.github.auties00.warden.PasskeyAssertion} back onto an {@link Assertion}. A Warden
     * {@link PasskeyException} (the user declining, the ceremony timing out, no usable credential, or a
     * platform failure) is rethrown as an {@link IllegalStateException} so the caller aborts the ceremony or
     * logs the session out.
     */
    @Override
    public Assertion assertCredential(Request request) {
        var passkeyRequest = new PasskeyRequest(
                request.relyingPartyId(),
                request.challenge(),
                request.allowedCredentialIds().toArray(byte[][]::new),
                toWarden(request.userVerification()),
                request.timeout(),
                request.prfEvalFirst(),
                request.origin());
        try {
            var assertion = authenticator.getAssertion(passkeyRequest, WardenAuthenticator.NO_WINDOW);
            return new Assertion(
                    assertion.credentialId(),
                    assertion.authenticatorData(),
                    assertion.clientDataJson(),
                    assertion.signature(),
                    assertion.userHandle(),
                    assertion.prfOutput());
        } catch (PasskeyException exception) {
            throw new IllegalStateException("The host passkey ceremony did not produce an assertion", exception);
        }
    }

    /**
     * Maps Cobalt's user-verification preference onto Warden's equivalent.
     *
     * @param userVerification the Cobalt preference
     * @return the matching Warden preference
     */
    private static com.github.auties00.warden.UserVerification toWarden(UserVerification userVerification) {
        return switch (userVerification) {
            case REQUIRED -> com.github.auties00.warden.UserVerification.REQUIRED;
            case PREFERRED -> com.github.auties00.warden.UserVerification.PREFERRED;
            case DISCOURAGED -> com.github.auties00.warden.UserVerification.DISCOURAGED;
        };
    }
}
