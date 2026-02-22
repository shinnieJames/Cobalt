package com.github.auties00.cobalt.device.fanout;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Service that calculates participant hash (phash) for group messages.
 *
 * <p>The phash verifies that sender and server agree on the list of participants
 * that should receive a group message.
 *
 * @apiNote WAWebPhashUtils: provides phashV1 and phashV2 functions that hash
 * sorted legacy JID strings and return a prefixed base64-encoded truncated hash.
 */
public final class DevicePhashCalculator {

    private static final System.Logger LOGGER = System.getLogger(DevicePhashCalculator.class.getName());

    /**
     * Number of hash bytes to use for the phash (6 bytes = 8 base64 chars).
     *
     * @apiNote WAWebPhashUtils: truncates the hash to first 6 bytes before base64 encoding.
     */
    private static final int HASH_BYTES_TO_USE = 6;

    private final ABPropsService abPropsService;

    public DevicePhashCalculator(ABPropsService abPropsService) {
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
    }

    /**
     * Calculates the phash for a set of device JIDs.
     *
     * @param deviceJids          the device JIDs to include in the hash
     * @param version             the phash version to use
     * @param allowIncludeMetaBot if true, checks AB props to determine if Meta AI bot should be included (V2 only)
     * @return the phash string (e.g., "2:q83vEjRW")
     * @throws NoSuchAlgorithmException if the hash algorithm is not available
     *
     * @apiNote WAWebPhashUtils.phashV2: sorts JIDs in legacy format, hashes with SHA-256,
     * truncates to 6 bytes, and prepends "2:" prefix.
     */
    public String calculate(
            Set<Jid> deviceJids,
            DevicePhashVersion version,
            boolean allowIncludeMetaBot
    ) throws NoSuchAlgorithmException {
        var jidsToHash = new ArrayList<>(deviceJids);

        // WAWebPhashUtils.phashV2: includes Meta AI bot JID for open groups
        // when web_ai_group_open_support and ai_group_participation_enabled are true
        // WAWebBotGroupGatingUtils.isOpenGroupBotParticipantAddEnabled()
        if (allowIncludeMetaBot && version.supportsMetaBot() && isOpenGroupBotParticipantAddEnabled()) {
            jidsToHash.add(Jid.metaAiBotAccount());
        }

        // WAWebPhashUtils.phashV1: uses asUserWidOrThrow to validate and convert to user WID
        // WAWebPhashUtils: converts JIDs to legacy string format and sorts alphabetically
        var legacyJids = jidsToHash.stream()
                .map(jid -> toLegacyJidString(jid, version))
                .sorted(Comparator.naturalOrder())
                .toList();

        // WAWebPhashUtils: logs the JIDs being hashed (unless gkx 26258 is enabled)
        // "[phashV1] calculating phash for {jids}" / "[phashV2] calculating phash for {jids}"
        LOGGER.log(System.Logger.Level.TRACE,
                "[{0}] calculating phash for {1}",
                version.prefix(),
                String.join(",", legacyJids));

        // WAWebPhashUtils: concatenates all JID strings and hashes them
        var digest = MessageDigest.getInstance(version.algorithm());
        for (var legacyJid : legacyJids) {
            digest.update(legacyJid.getBytes(StandardCharsets.UTF_8));
        }
        var hash = digest.digest();

        // WAWebPhashUtils: truncates hash to first 6 bytes and base64 encodes
        var truncated = new byte[HASH_BYTES_TO_USE];
        System.arraycopy(hash, 0, truncated, 0, HASH_BYTES_TO_USE);

        var base64 = Base64.getEncoder().encodeToString(truncated);
        return version.prefix() + base64;
    }

    /**
     * Checks if the open group bot participant feature is enabled.
     *
     * @return true if both web_ai_group_open_support and ai_group_participation_enabled are true
     *
     * @apiNote WAWebBotGroupGatingUtils.isOpenGroupBotParticipantAddEnabled: returns true only if
     * both AB props are enabled.
     */
    private boolean isOpenGroupBotParticipantAddEnabled() {
        var webAiGroupOpenSupport = abPropsService.getBool(ABProp.WEB_AI_GROUP_OPEN_SUPPORT);
        var aiGroupParticipationEnabled = abPropsService.getBool(ABProp.AI_GROUP_PARTICIPATION_ENABLED);
        return webAiGroupOpenSupport && aiGroupParticipationEnabled;
    }

    /**
     * Converts a device JID to legacy format for phash calculation.
     *
     * @apiNote WAWebWid.toString: V1 uses {@code toString({legacy: true})} producing "user@s.whatsapp.net".
     * V2 uses {@code toString({legacy: true, formatFull: true})} producing "user.0:device@s.whatsapp.net".
     * Legacy format converts c.us server to s.whatsapp.net.
     */
    private static String toLegacyJidString(Jid jid, DevicePhashVersion version) {
        // WAWebPhashUtils.phashV1: uses asUserWidOrThrow which validates the JID is a user type
        // and strips device information, keeping only user@server
        var user = jid.user();
        var device = jid.device();

        // WAWebWid.toString({legacy: true}): converts c.us to s.whatsapp.net
        var legacyServer = toLegacyServer(jid.server());

        return switch (version) {
            // WAWebPhashUtils.phashV1: simple legacy format "user@server"
            // Uses asUserWidOrThrow(e).toString({legacy: true}) which strips device
            case V1 -> user + "@" + legacyServer;

            // WAWebPhashUtils.phashV2: full legacy format "user.0:device@server"
            // Uses e.toString({legacy: true, formatFull: true})
            // formatFull adds ".0" agent and always includes ":device" even when 0
            case V2 -> user + ".0:" + device + "@" + legacyServer;
        };
    }

    /**
     * Converts a server to its legacy format.
     *
     * @apiNote WAWebWid.toString: when legacy=true, converts "c.us" to "s.whatsapp.net"
     */
    private static JidServer toLegacyServer(JidServer server) {
        if (server.equals(JidServer.legacyUser())) {
            return JidServer.user();
        }
        return server;
    }
}
