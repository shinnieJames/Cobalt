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
 * @implNote WAWebPhashUtils: provides phashV1 and phashV2 functions that hash
 * sorted legacy JID strings and return a prefixed base64-encoded truncated hash.
 */
public final class DevicePhashCalculator {

    private static final System.Logger LOGGER = System.getLogger(DevicePhashCalculator.class.getName());

    /**
     * Number of hash bytes to use for the phash (6 bytes = 8 base64 chars).
     *
     * @implNote WAWebPhashUtils: truncates the hash to first 6 bytes before base64 encoding.
     */
    private static final int HASH_BYTES_TO_USE = 6;

    /**
     * The JID for the Meta AI TEE (Trusted Execution Environment) bot account
     * ({@code 1273596044787272@bot}).
     *
     * @implNote WAWebBotUtils.META_BOT_TEE_FBID_WID: createUserWidOrThrow("1273596044787272@bot")
     */
    private static final Jid META_AI_TEE_BOT_ACCOUNT = new Jid("1273596044787272", JidServer.bot());

    /**
     * The AB props service used to check feature flags.
     */
    private final ABPropsService abPropsService;

    /**
     * Creates a new phash calculator with the specified AB props service.
     *
     * @param abPropsService the AB props service for feature flag checks
     * @throws NullPointerException if {@code abPropsService} is {@code null}
     * @implNote WAWebPhashUtils: depends on WAWebBotGroupGatingUtils which reads AB props.
     */
    public DevicePhashCalculator(ABPropsService abPropsService) {
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
    }

    /**
     * Calculates the phash for a set of device JIDs without bot injection.
     *
     * <p>Convenience overload that passes {@code false} for both open and TEE bot
     * injection flags. This matches the WA Web default behavior where phashV2 is called
     * with no bot parameters.
     *
     * @param deviceJids the device JIDs to include in the hash
     * @param version    the phash version to use
     * @param allowIncludeOpenBot if {@code true}, checks AB props to determine if the open group
     *                           Meta AI bot should be included (V2 only)
     * @return the phash string (e.g., "2:q83vEjRW")
     * @throws NoSuchAlgorithmException if the hash algorithm is not available
     * @implNote WAWebPhashUtils.phashV2: most callers pass only one arg (JID array),
     * defaulting both bot flags to {@code false}. This overload exists for backward
     * compatibility and should be migrated to the 4-param variant.
     */
    public String calculate(
            Set<Jid> deviceJids,
            DevicePhashVersion version,
            boolean allowIncludeOpenBot
    ) throws NoSuchAlgorithmException {
        return calculate(deviceJids, version, allowIncludeOpenBot, false);
    }

    /**
     * Calculates the phash for a set of device JIDs.
     *
     * <p>For V1, each JID is validated as a user WID and converted to simple legacy format.
     * For V2, each JID is converted to full legacy format with agent and device components.
     * Both versions sort the JID strings, hash them, truncate to 6 bytes, and base64-encode
     * the result with a version prefix.
     *
     * @param deviceJids         the device JIDs to include in the hash
     * @param version            the phash version to use
     * @param allowIncludeOpenBot if {@code true}, checks AB props to determine if the open group
     *                           Meta AI bot should be included (V2 only)
     * @param allowIncludeTeeBot  if {@code true}, checks AB props to determine if the TEE group
     *                           Meta AI bot should be included (V2 only)
     * @return the phash string (e.g., "2:q83vEjRW")
     * @throws NoSuchAlgorithmException if the hash algorithm is not available
     * @implNote WAWebPhashUtils.phashV1: sorts JIDs in legacy format, hashes with SHA-1,
     * truncates to 6 bytes, and prepends "1:" prefix.
     * WAWebPhashUtils.phashV2: sorts JIDs in legacy format, hashes with SHA-256,
     * truncates to 6 bytes, and prepends "2:" prefix. Supports open and TEE bot injection.
     */
    public String calculate(
            Set<Jid> deviceJids,
            DevicePhashVersion version,
            boolean allowIncludeOpenBot,
            boolean allowIncludeTeeBot
    ) throws NoSuchAlgorithmException {
        var jidsToHash = new ArrayList<>(deviceJids);

        // WAWebPhashUtils.phashV2: includes Meta AI open group bot JID
        // when isOpenGroupBotParticipantAddEnabled() && allowIncludeOpenBot
        // WAWebBotGroupGatingUtils.isOpenGroupBotParticipantAddEnabled()
        if (allowIncludeOpenBot && version.supportsMetaBot() && isOpenGroupBotParticipantAddEnabled()) {
            jidsToHash.add(Jid.metaAiBotAccount()); // WAWebBotUtils.META_BOT_FBID_WID
        }

        // WAWebPhashUtils.phashV2: includes Meta AI TEE group bot JID
        // when isTEEGroupBotParticipantAddEnabled() && allowIncludeTeeBot
        // WAWebBotGroupGatingUtils.isTEEGroupBotParticipantAddEnabled()
        if (allowIncludeTeeBot && version.supportsMetaBot() && isTEEGroupBotParticipantAddEnabled()) {
            jidsToHash.add(META_AI_TEE_BOT_ACCOUNT); // WAWebBotUtils.META_BOT_TEE_FBID_WID
        }

        // WAWebPhashUtils: logs the JIDs being hashed (unless gkx 26258 is enabled)
        // "[phashV1] calculating phash for {jids}" / "[phashV2] calculating phash for {jids}"
        // WAWebPhashUtils.phashV1: uses asUserWidOrThrow to validate and convert to user WID
        // WAWebPhashUtils: converts JIDs to legacy string format and sorts alphabetically
        var legacyJids = jidsToHash.stream()
                .map(jid -> toLegacyJidString(jid, version))
                .sorted(Comparator.naturalOrder())
                .toList();

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

        // WAWebPhashUtils: uses WABase64.encodeB64 (standard base64 with padding)
        var base64 = Base64.getEncoder().encodeToString(truncated);
        return version.prefix() + base64;
    }

    /**
     * Checks if the open group bot participant feature is enabled.
     *
     * @return {@code true} if both {@code web_ai_group_open_support} and
     *         {@code ai_group_participation_enabled} are {@code true}
     * @implNote WAWebBotGroupGatingUtils.isOpenGroupBotParticipantAddEnabled: returns
     * {@code true} only if both AB props are enabled.
     */
    private boolean isOpenGroupBotParticipantAddEnabled() {
        var webAiGroupOpenSupport = abPropsService.getBool(ABProp.WEB_AI_GROUP_OPEN_SUPPORT);
        var aiGroupParticipationEnabled = abPropsService.getBool(ABProp.AI_GROUP_PARTICIPATION_ENABLED);
        return webAiGroupOpenSupport && aiGroupParticipationEnabled;
    }

    /**
     * Checks if the TEE group bot participant feature is enabled.
     *
     * @return {@code true} if both {@code web_ai_group_open_support} and
     *         {@code ai_group_participation_add_tee_enabled} are {@code true}
     * @implNote WAWebBotGroupGatingUtils.isTEEGroupBotParticipantAddEnabled: returns
     * {@code true} only if both AB props are enabled.
     */
    private boolean isTEEGroupBotParticipantAddEnabled() {
        var webAiGroupOpenSupport = abPropsService.getBool(ABProp.WEB_AI_GROUP_OPEN_SUPPORT);
        var aiGroupParticipationAddTeeEnabled = abPropsService.getBool(ABProp.AI_GROUP_PARTICIPATION_ADD_TEE_ENABLED);
        return webAiGroupOpenSupport && aiGroupParticipationAddTeeEnabled;
    }

    /**
     * Converts a device JID to legacy format for phash calculation.
     *
     * @param jid     the JID to convert
     * @param version the phash version determining the format
     * @return the legacy JID string
     * @implNote WAWebWid.toString: V1 uses {@code toString({legacy: true})} via
     * {@code asUserWidOrThrow} producing "user@s.whatsapp.net".
     * V2 uses {@code toString({legacy: true, formatFull: true})} producing
     * "user.0:device@s.whatsapp.net". Legacy format converts c.us server to s.whatsapp.net.
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
     * @param server the server to convert
     * @return the legacy server, with {@code c.us} mapped to {@code s.whatsapp.net}
     * @implNote WAWebWid.toString: when legacy=true, converts "c.us" to "s.whatsapp.net"
     */
    private static JidServer toLegacyServer(JidServer server) {
        if (server.equals(JidServer.legacyUser())) {
            return JidServer.user();
        }
        return server;
    }
}
