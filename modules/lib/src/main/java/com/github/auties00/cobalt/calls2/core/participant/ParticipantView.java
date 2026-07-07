package com.github.auties00.cobalt.calls2.core.participant;

import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Objects;
import java.util.Optional;

/**
 * An immutable flattened snapshot of a {@link CallParticipant} taken under the membership
 * lock.
 *
 * <p>This is the flyweight read seam the rest of the call engine uses to read a
 * participant WITHOUT holding the membership lock and WITHOUT chasing the heavy mutable
 * {@link CallParticipant} aggregate. A view is built once, while the lock is held, by
 * copying the participant's load-bearing fields into this record; consumers then read the
 * stable copy freely. A {@link CallParticipant} reference is never leaked out of the
 * membership layer, so this snapshot is the only participant shape the wider engine sees.
 *
 * <p>The snapshot carries the participant's validity, whether it is a call extension
 * rather than a real user, its connected and active status, its
 * {@linkplain CallParticipantPlatform platform}, its user and active-device JIDs, its
 * current {@linkplain #videoState() video state}, whether its active device has video
 * enabled, whether it is screen-sharing, and the subscribed encoded video and RTCP stream
 * ids. Because it is a record over immutable components, a view never changes after it is
 * built; a fresh snapshot is taken whenever a current read is needed.
 *
 * @implNote This implementation flattens the {@code participant_view} POD (0x108 = 264
 * bytes) the wa-voip WASM module {@code ff-tScznZ8P} produces in
 * {@code populate_participant_view} (fn10991) and {@code populate_extension_view}
 * (fn10992): validity (+0), is-extension (+1, from {@code participant+0x10}), is-connected
 * (+2, from {@code state==1}), is-active (+3, from the {@code 0xb8e>>state} active mask or
 * {@code state>0xc}), the device-video-enabled flag (+6, from {@code device+0x69}), the
 * screenshare/state byte (+8, from {@code participant+0x1c}), the platform (+0xc), the
 * formatted user JID (+0x1c, 0x50 bytes) and device JID (+0x6c, 0x50 bytes), and the
 * subscribed encoded video stream id ({@code wa_participant_view_set_subscribed_enc_stream_id},
 * fn11011) and RTCP enc stream id ({@code wa_participant_view_set_subscribed_rtcp_enc_stream_id},
 * fn11008). The native pointers the POD also carries (+0xc0 media, +0xd8 device-keys,
 * +0xf4 participant base) are engine-internal heap references with no Java counterpart and
 * are deliberately not snapshotted; the video-state value is the
 * {@code wa_participant_view_get_video_state} (fn11014) result, {@code 0x14} (20) when no
 * detail is present.
 * @param valid                   whether this snapshot holds a populated participant
 * @param extension               whether the participant is a call extension rather than a
 *                                real user
 * @param connected               whether the participant is connected with media flowing
 * @param active                  whether the participant is active or in an active-ish
 *                                transitional state
 * @param platform                the participant's client platform; never {@code null}
 * @param userJid                 the participant's user JID, or {@code null} for an
 *                                unpopulated or user-less snapshot
 * @param deviceJid               the participant's active-device JID, or {@code null} when
 *                                no active device is set
 * @param videoState              the participant's video-state code
 * @param deviceVideoEnabled      whether the participant's active device has video enabled
 * @param screenSharing           whether the participant is currently screen-sharing
 * @param subscribedVideoStreamId the subscribed encoded video stream id, or
 *                                {@value #NO_SUBSCRIBED_STREAM} when none
 * @param subscribedRtcpStreamId  the subscribed encoded RTCP stream id, or
 *                                {@value #NO_SUBSCRIBED_STREAM} when none
 * @see CallParticipant
 * @see ParticipantProvider
 */
public record ParticipantView(
        boolean valid,
        boolean extension,
        boolean connected,
        boolean active,
        CallParticipantPlatform platform,
        Jid userJid,
        Jid deviceJid,
        int videoState,
        boolean deviceVideoEnabled,
        boolean screenSharing,
        int subscribedVideoStreamId,
        int subscribedRtcpStreamId
) {
    /**
     * Holds the video-state code the engine reports when a participant has no detail
     * information.
     *
     * <p>This is the default the native {@code wa_participant_view_get_video_state} returns
     * for a participant without a populated detail record.
     */
    public static final int VIDEO_STATE_UNKNOWN = 0x14;

    /**
     * Holds the video-state code an extension participant always reports.
     *
     * <p>An extension's video is treated as enabled.
     */
    public static final int VIDEO_STATE_ENABLED = 1;

    /**
     * Holds the video-state code meaning the participant's video is paused.
     */
    public static final int VIDEO_STATE_PAUSED = 2;

    /**
     * Holds the video-state code meaning the participant's video is stopped.
     */
    public static final int VIDEO_STATE_STOPPED = 6;

    /**
     * Holds the sentinel a subscribed-stream id carries when there is no subscription.
     */
    public static final int NO_SUBSCRIBED_STREAM = -1;

    /**
     * The shared invalid (unpopulated) view.
     *
     * <p>Because the invalid view is immutable and carries no participant-specific state, one instance is
     * reused for every absent participant slot rather than allocated per lookup.
     */
    private static final ParticipantView INVALID = new ParticipantView(
            false,
            false,
            false,
            false,
            CallParticipantPlatform.UNKNOWN,
            null,
            null,
            VIDEO_STATE_UNKNOWN,
            false,
            false,
            NO_SUBSCRIBED_STREAM,
            NO_SUBSCRIBED_STREAM);

    /**
     * Validates the record components during construction.
     *
     * @throws NullPointerException if {@code platform} is {@code null}
     */
    public ParticipantView {
        Objects.requireNonNull(platform, "platform cannot be null");
    }

    /**
     * Returns an invalid (unpopulated) view.
     *
     * <p>This is the snapshot of an empty participant slot: it is not valid, not connected,
     * carries no JIDs, an {@linkplain CallParticipantPlatform#UNKNOWN unknown} platform,
     * the {@value #VIDEO_STATE_UNKNOWN} video state, and no stream subscriptions. The
     * provider returns this rather than {@code null} for an absent participant.
     *
     * @return an invalid view
     */
    public static ParticipantView invalid() {
        return INVALID;
    }

    /**
     * Returns the participant's user JID, if the snapshot carries one.
     *
     * @return an {@link Optional} over the user JID, or empty when no user JID is set
     */
    public Optional<Jid> userJidOptional() {
        return Optional.ofNullable(userJid);
    }

    /**
     * Returns the participant's active-device JID, if the snapshot carries one.
     *
     * @return an {@link Optional} over the active-device JID, or empty when no active device
     *         is set
     */
    public Optional<Jid> deviceJidOptional() {
        return Optional.ofNullable(deviceJid);
    }

    /**
     * Returns whether the participant's video is paused or stopped.
     *
     * @implNote This implementation reproduces {@code wa_participant_view_is_video_paused_or_stopped}
     * (fn11013): {@code (videoState & ~4) == 2}, which is {@code true} for both the paused
     * ({@value #VIDEO_STATE_PAUSED}) and stopped ({@value #VIDEO_STATE_STOPPED}) states.
     * @return {@code true} if the video state is paused or stopped
     */
    public boolean isVideoPausedOrStopped() {
        return (videoState & ~4) == VIDEO_STATE_PAUSED;
    }

    /**
     * Returns whether this view is valid, not a call extension, and connected.
     *
     * <p>This is the common "is this a real peer with media flowing" predicate the engine's
     * provider iterators apply.
     *
     * @return {@code true} if the view is a valid, non-extension, connected participant
     */
    public boolean isConnectedPeer() {
        return valid && !extension && connected;
    }
}
