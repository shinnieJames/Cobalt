package com.github.auties00.cobalt.calls2.common;

import com.github.auties00.cobalt.calls2.core.control.PrivacyToken;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a call participant addressed by its user JID and its set of devices.
 *
 * <p>This is the richest call-layer address: a participant is one party in a call,
 * composed of an owning user {@link Jid}, an ordered list of that account's
 * {@link CallDeviceJid devices} with the primary device first, an optional opaque
 * privacy token, and optional opaque bot options.
 *
 * <p>The device list is normalized so that the primary device occupies index {@code 0}:
 * the first device that {@linkplain CallDeviceJid#isPrimary() is primary} is moved to the
 * front. When the supplied device list is empty, the participant is materialized with a
 * single default primary device synthesized from the user JID. The first element of
 * {@link #devices()} is therefore always the primary device and is also returned by
 * {@link #primaryDevice()}.
 *
 * <p>The privacy token is carried as an immutable {@link PrivacyToken}; the bot options are
 * an opaque binary payload the engine forwards uninterpreted, whose internal layout is owned
 * by the bot subsystem and is not modeled here. The bot options are defensively copied on
 * construction and on access so this value object stays immutable. Instances compare by user
 * JID, device list, the privacy token, and the byte contents of the bot options.
 *
 * @implNote This implementation composes the {@code wa_call_participant_jid} aggregate
 *           (0xa0 bytes) from {@code call_participant_jid.cc}: the user JID (+0), the
 *           privacy token (+0x4), the device count (+0x84), the device-jid array
 *           (+0x88), and the bot-options blob (+0x9c). The native {@code create3}
 *           selects the primary device as the one whose marker field (+0x40) is zero and
 *           falls back to a default primary device when the device count is out of the
 *           valid range; both behaviours are reproduced by the device-list normalization.
 * @param userJid      the participant's user JID; never {@code null}
 * @param devices      the participant's devices, primary first; never {@code null} and
 *                     never empty
 * @param privacyToken the opaque {@link PrivacyToken}, or {@code null} if absent
 * @param botOptions   the opaque bot-options blob, or {@code null} if absent
 * @see CallJid
 * @see CallDeviceJid
 */
public record CallParticipantJid(Jid userJid, List<CallDeviceJid> devices, PrivacyToken privacyToken, byte[] botOptions) {
    /**
     * Canonicalizes the record components, normalizing the device list and defensively
     * copying the opaque payloads.
     *
     * <p>The device list is copied, reordered so that the primary device (the first
     * device that {@linkplain CallDeviceJid#isPrimary() is primary}) leads, and made
     * unmodifiable. If the supplied list is empty, a single default primary device is
     * synthesized from {@code userJid} via {@link CallDeviceJid#primaryOf(Jid)}.
     *
     * @throws NullPointerException     if {@code userJid} or {@code devices} is
     *                                  {@code null}, or if {@code devices} contains a
     *                                  {@code null} element
     */
    public CallParticipantJid {
        Objects.requireNonNull(userJid, "userJid cannot be null");
        Objects.requireNonNull(devices, "devices cannot be null");
        devices = normalizeDevices(userJid, devices);
        botOptions = botOptions == null ? null : botOptions.clone();
    }

    /**
     * Returns a {@code CallParticipantJid} for the given user JID and devices, without a
     * privacy token or bot options.
     *
     * @param userJid the participant's user JID; never {@code null}
     * @param devices the participant's devices; never {@code null}, normalized so the
     *                primary device leads, and defaulted to a single primary device when
     *                empty
     * @return the participant JID
     * @throws NullPointerException if {@code userJid} or {@code devices} is {@code null},
     *                              or if {@code devices} contains a {@code null} element
     */
    public static CallParticipantJid of(Jid userJid, List<CallDeviceJid> devices) {
        return new CallParticipantJid(userJid, devices, null, null);
    }

    /**
     * Returns a single-device {@code CallParticipantJid} addressing only the primary
     * device of the given user JID.
     *
     * <p>This is the canonical one-to-one form: the device list holds exactly the
     * primary device synthesized via {@link CallDeviceJid#primaryOf(Jid)}.
     *
     * @param userJid the participant's user JID; never {@code null}
     * @return the participant JID with a single primary device
     * @throws NullPointerException     if {@code userJid} is {@code null}
     * @throws IllegalArgumentException if the user JID's server domain does not map to a
     *                                  device-callable {@link CallJidDomainType}
     */
    public static CallParticipantJid ofPrimary(Jid userJid) {
        Objects.requireNonNull(userJid, "userJid cannot be null");
        return of(userJid, List.of(CallDeviceJid.primaryOf(userJid)));
    }

    /**
     * Returns the number of devices addressed by this participant.
     *
     * @return the device count, always at least {@code 1}
     */
    public int deviceCount() {
        return devices.size();
    }

    /**
     * Returns the primary device of this participant.
     *
     * <p>The primary device is the first element of the normalized {@link #devices()}
     * list.
     *
     * @return the primary device; never {@code null}
     */
    public CallDeviceJid primaryDevice() {
        return devices.getFirst();
    }

    /**
     * Returns the opaque privacy-token blob, if present.
     *
     * <p>The returned array, when present, is a defensive copy extracted from the
     * {@link PrivacyToken}; mutating it does not affect this participant.
     *
     * @return an {@code Optional} holding a copy of the privacy-token bytes, or empty if
     *         no privacy token is set
     */
    public Optional<byte[]> privacyTokenBytes() {
        return Optional.ofNullable(privacyToken)
                .map(PrivacyToken::value);
    }

    /**
     * Returns the opaque bot-options blob, if present.
     *
     * <p>The returned array, when present, is a defensive copy; mutating it does not
     * affect this participant.
     *
     * @return an {@code Optional} holding a copy of the bot-options bytes, or empty if no
     *         bot options are set
     */
    public Optional<byte[]> botOptionsBytes() {
        return Optional.ofNullable(botOptions)
                .map(byte[]::clone);
    }

    /**
     * Returns the raw bot-options blob backing this participant.
     *
     * <p>This accessor overrides the implicit record accessor to return a defensive copy
     * so the stored array cannot be mutated through the returned reference.
     *
     * @return a copy of the bot-options bytes, or {@code null} if no bot options are set
     */
    public byte[] botOptions() {
        return botOptions == null ? null : botOptions.clone();
    }

    /**
     * Normalizes a device list so the primary device leads, defaulting to a synthesized
     * primary device when the list is empty.
     *
     * <p>If the supplied list is empty, a single default primary device is built from
     * {@code userJid}. Otherwise the list is copied; if any device
     * {@linkplain CallDeviceJid#isPrimary() is primary} and is not already first, the
     * first such device is moved to index {@code 0} while the relative order of the
     * remaining devices is preserved. The result is unmodifiable.
     *
     * @param userJid the owning user JID, used to synthesize a default primary device
     * @param devices the device list to normalize; must not contain {@code null}
     * @return an unmodifiable, primary-first device list with at least one element
     * @throws NullPointerException if {@code devices} contains a {@code null} element
     */
    private static List<CallDeviceJid> normalizeDevices(Jid userJid, List<CallDeviceJid> devices) {
        if (devices.isEmpty()) {
            return List.of(CallDeviceJid.primaryOf(userJid));
        }
        var ordered = new ArrayList<CallDeviceJid>(devices.size());
        for (var device : devices) {
            ordered.add(Objects.requireNonNull(device, "devices cannot contain a null element"));
        }
        for (var index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).isPrimary()) {
                if (index != 0) {
                    ordered.add(0, ordered.remove(index));
                }
                break;
            }
        }
        return List.copyOf(ordered);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof CallParticipantJid that
                && this.userJid.equals(that.userJid)
                && this.devices.equals(that.devices)
                && Objects.equals(this.privacyToken, that.privacyToken)
                && Arrays.equals(this.botOptions, that.botOptions));
    }

    @Override
    public int hashCode() {
        return Objects.hash(userJid, devices, privacyToken, Arrays.hashCode(botOptions));
    }

    @Override
    public String toString() {
        return "CallParticipantJid[userJid=" + userJid
                + ", devices=" + devices
                + ", privacyTokenLen=" + (privacyToken == null ? -1 : privacyToken.length())
                + ", botOptionsLen=" + (botOptions == null ? -1 : botOptions.length)
                + ']';
    }
}
