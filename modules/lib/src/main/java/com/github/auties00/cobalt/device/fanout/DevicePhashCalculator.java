package com.github.auties00.cobalt.device.fanout;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Computes the participant hash ({@code phash}) string that group-message stanzas carry so the
 * server and the sender can confirm they share the same view of the group's device membership.
 *
 * @apiNote
 * The output of {@link #calculate} goes on the outgoing {@code <message phash="...">} stanza
 * attribute for group sends and on the {@code <message>} stanzas of broadcast and status
 * messages. The server compares it to its own hash of the participant device JIDs and rejects
 * the stanza (or instructs the client to resync) on mismatch. Both the legacy SHA-1 V1 format
 * and the current SHA-256 V2 format are supported via {@link DevicePhashVersion}; V2 also
 * supports injecting the Meta AI bot account JIDs when the corresponding AB-prop gates are on.
 * {@link com.github.auties00.cobalt.device.DeviceService} also calls this calculator to
 * short-circuit a device-list sync when the server pre-announces an expected {@code phash}.
 */
@WhatsAppWebModule(moduleName = "WAWebPhashUtils")
public final class DevicePhashCalculator {

    /**
     * Logger used to mirror WA Web's {@code [phashV1] / [phashV2] calculating phash for ...}
     * diagnostic trace.
     */
    private static final System.Logger LOGGER = System.getLogger(DevicePhashCalculator.class.getName());

    /**
     * Number of leading hash bytes retained before base64 encoding.
     *
     * @apiNote
     * Six bytes encode to exactly eight base64 characters (no padding because {@code 6 % 3 == 0});
     * the prefix ({@code "1:"} or {@code "2:"}) is prepended afterwards. The truncation length
     * matches {@code WAWebPhashUtils.phashV1} and {@code WAWebPhashUtils.phashV2} which call
     * {@code slice(0, 6)} on the raw digest before base64.
     */
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = {"phashV1", "phashV2"},
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int HASH_BYTES_TO_USE = 6;

    /**
     * The Meta AI TEE (Trusted Execution Environment) bot account JID, eligible for V2 phash
     * injection when the corresponding gating flag is on.
     *
     * @apiNote
     * Matches WA Web's {@code WAWebBotUtils.META_BOT_TEE_FBID_WID} literal
     * ({@code 1273596044787272@bot}).
     */
    @WhatsAppWebExport(moduleName = "WAWebBotUtils",
            exports = "META_BOT_TEE_FBID_WID",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final Jid META_AI_TEE_BOT_ACCOUNT = new Jid("1273596044787272", JidServer.bot());

    /**
     * The {@link ABPropsService} consulted by the Meta AI bot gating predicates.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs a calculator bound to the given AB props service.
     *
     * @apiNote
     * Embedders rarely instantiate this directly; it is wired by
     * {@link com.github.auties00.cobalt.device.DeviceService} and shared across all group sends
     * from a single client.
     *
     * @param abPropsService the AB props service used by
     *                       {@link #isOpenGroupBotParticipantAddEnabled()} and
     *                       {@link #isTEEGroupBotParticipantAddEnabled()}
     * @throws NullPointerException if {@code abPropsService} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = {"phashV1", "phashV2"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DevicePhashCalculator(ABPropsService abPropsService) {
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
    }

    /**
     * Computes the {@code phash} for the given device JIDs without the TEE bot injection
     * branch.
     *
     * @apiNote
     * Convenience overload equivalent to
     * {@link #calculate(Set, DevicePhashVersion, boolean, boolean) calculate(deviceJids, version, allowIncludeOpenBot, false)}.
     * Use this on send paths that may add the open group bot but never the TEE bot
     * ({@code WAWebSendGroupSkmsgJob} is the canonical caller).
     *
     * @param deviceJids          the device JID set to hash
     * @param version             the {@link DevicePhashVersion} selecting algorithm, prefix, and bot-injection eligibility
     * @param allowIncludeOpenBot whether the open Meta AI group bot may be injected when V2 and {@link #isOpenGroupBotParticipantAddEnabled()} are both true
     * @return the encoded {@code phash} (prefix plus 8-char base64 of the first 6 digest bytes)
     * @throws NoSuchAlgorithmException if the JRE does not provide {@link DevicePhashVersion#algorithm()}
     */
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = {"phashV1", "phashV2"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    public String calculate(
            Set<Jid> deviceJids,
            DevicePhashVersion version,
            boolean allowIncludeOpenBot
    ) throws NoSuchAlgorithmException {
        return calculate(deviceJids, version, allowIncludeOpenBot, false);
    }

    /**
     * Computes the {@code phash} for the given device JIDs.
     *
     * @apiNote
     * Use V1 (SHA-1, prefix {@code "1:"}) for legacy interop and V2 (SHA-256, prefix
     * {@code "2:"}) for current group sends. The two boolean flags interact with the AB-prop
     * gates: the open Meta AI bot JID is added only when both {@code allowIncludeOpenBot} and
     * {@link #isOpenGroupBotParticipantAddEnabled()} are true; the TEE Meta AI bot JID is
     * added only when both {@code allowIncludeTeeBot} and {@link #isTEEGroupBotParticipantAddEnabled()}
     * are true. V1 never injects either bot. Sample call shape:
     * {@snippet :
     *     var phash = calculator.calculate(deviceJids, DevicePhashVersion.V2, true, false);
     *     stanzaBuilder.attribute("phash", phash);
     * }
     *
     * @implNote
     * This implementation follows the WA Web pipeline byte for byte: serialise every JID via
     * {@link #toLegacyJidString} into its V1 or V2 legacy string, sort the strings via
     * {@link Comparator#naturalOrder()}, feed each string's UTF-8 bytes into the digest, take
     * the first {@value #HASH_BYTES_TO_USE} bytes of the digest, base64-encode them, and
     * prepend {@link DevicePhashVersion#prefix()}.
     *
     * @param deviceJids          the device JID set to hash
     * @param version             the {@link DevicePhashVersion} selecting algorithm, prefix, and bot-injection eligibility
     * @param allowIncludeOpenBot whether the open Meta AI group bot may be injected (V2 only)
     * @param allowIncludeTeeBot  whether the TEE Meta AI group bot may be injected (V2 only)
     * @return the encoded {@code phash} (prefix plus 8-char base64 of the first 6 digest bytes)
     * @throws NoSuchAlgorithmException if the JRE does not provide {@link DevicePhashVersion#algorithm()}
     */
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = {"phashV1", "phashV2"},
            adaptation = WhatsAppAdaptation.DIRECT)
    public String calculate(
            Set<Jid> deviceJids,
            DevicePhashVersion version,
            boolean allowIncludeOpenBot,
            boolean allowIncludeTeeBot
    ) throws NoSuchAlgorithmException {
        var jidsToHash = new ArrayList<>(deviceJids);

        if (allowIncludeOpenBot && version.supportsMetaBot() && isOpenGroupBotParticipantAddEnabled()) {
            jidsToHash.add(Jid.metaAiBotAccount());
        }

        if (allowIncludeTeeBot && version.supportsMetaBot() && isTEEGroupBotParticipantAddEnabled()) {
            jidsToHash.add(META_AI_TEE_BOT_ACCOUNT);
        }

        var legacyJids = jidsToHash.stream()
                .map(jid -> toLegacyJidString(jid, version))
                .sorted(Comparator.naturalOrder())
                .toList();

        LOGGER.log(System.Logger.Level.TRACE,
                "[{0}] calculating phash for {1}",
                version.prefix(),
                String.join(",", legacyJids));

        var digest = MessageDigest.getInstance(version.algorithm());
        for (var legacyJid : legacyJids) {
            digest.update(legacyJid.getBytes(StandardCharsets.UTF_8));
        }
        var hash = digest.digest();

        var truncated = new byte[HASH_BYTES_TO_USE];
        System.arraycopy(hash, 0, truncated, 0, HASH_BYTES_TO_USE);

        var base64 = Base64.getEncoder().encodeToString(truncated);
        return version.prefix() + base64;
    }

    /**
     * Returns whether the open Meta AI group bot may be added to the V2 hashed set.
     *
     * @apiNote
     * Gates the conditional {@code push(META_BOT_FBID_WID)} step inside V2 phash computation;
     * matches WA Web's {@code WAWebBotGroupGatingUtils.isOpenGroupBotParticipantAddEnabled}.
     *
     * @implNote
     * This implementation requires both {@link ABProp#WEB_AI_GROUP_OPEN_SUPPORT} and
     * {@link ABProp#AI_GROUP_PARTICIPATION_ENABLED} to be true; either being unset returns
     * {@code false}.
     *
     * @return {@code true} when both gating AB props are set
     */
    @WhatsAppWebExport(moduleName = "WAWebBotGroupGatingUtils",
            exports = "isOpenGroupBotParticipantAddEnabled",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isOpenGroupBotParticipantAddEnabled() {
        var webAiGroupOpenSupport = abPropsService.getBool(ABProp.WEB_AI_GROUP_OPEN_SUPPORT);
        var aiGroupParticipationEnabled = abPropsService.getBool(ABProp.AI_GROUP_PARTICIPATION_ENABLED);
        return webAiGroupOpenSupport && aiGroupParticipationEnabled;
    }

    /**
     * Returns whether the TEE Meta AI group bot may be added to the V2 hashed set.
     *
     * @apiNote
     * Gates the conditional {@code push(META_BOT_TEE_FBID_WID)} step inside V2 phash
     * computation; matches WA Web's {@code WAWebBotGroupGatingUtils.isTEEGroupBotParticipantAddEnabled}.
     *
     * @implNote
     * This implementation requires both {@link ABProp#WEB_AI_GROUP_OPEN_SUPPORT} and
     * {@link ABProp#AI_GROUP_PARTICIPATION_ADD_TEE_ENABLED} to be true; either being unset
     * returns {@code false}.
     *
     * @return {@code true} when both gating AB props are set
     */
    @WhatsAppWebExport(moduleName = "WAWebBotGroupGatingUtils",
            exports = "isTEEGroupBotParticipantAddEnabled",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isTEEGroupBotParticipantAddEnabled() {
        var webAiGroupOpenSupport = abPropsService.getBool(ABProp.WEB_AI_GROUP_OPEN_SUPPORT);
        var aiGroupParticipationAddTeeEnabled = abPropsService.getBool(ABProp.AI_GROUP_PARTICIPATION_ADD_TEE_ENABLED);
        return webAiGroupOpenSupport && aiGroupParticipationAddTeeEnabled;
    }

    /**
     * Serialises a {@link Jid} into the legacy string form fed to the phash digest.
     *
     * @apiNote
     * V1 emits the equivalent of {@code WAWebWid.toString({legacy: true})}: the bare
     * {@code user@server} (no agent, no device). V2 emits
     * {@code WAWebWid.toString({legacy: true, formatFull: true})}: the full
     * {@code user.0:device@server} with the agent fixed at {@code 0} and the device id included.
     *
     * @implNote
     * This implementation calls {@link #toLegacyServer} to map {@code c.us} to the legacy
     * {@code s.whatsapp.net} server before composing the string, matching WA Web's
     * {@code WAWebWid.toString} legacy branch.
     *
     * @param jid     the JID to serialise
     * @param version the {@link DevicePhashVersion} selecting the format
     * @return the legacy-form string fed to the digest
     */
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = {"phashV1", "phashV2"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String toLegacyJidString(Jid jid, DevicePhashVersion version) {
        var user = jid.user();
        var device = jid.device();
        var legacyServer = toLegacyServer(jid.server());
        return switch (version) {
            case V1 -> user + "@" + legacyServer;
            case V2 -> user + ".0:" + device + "@" + legacyServer;
        };
    }

    /**
     * Maps a {@link JidServer} to its legacy wire-form name.
     *
     * @apiNote
     * Used only by {@link #toLegacyJidString} to drive the {@code c.us} to
     * {@code s.whatsapp.net} substitution; all other servers pass through unchanged.
     *
     * @implNote
     * This implementation compares the input to {@link JidServer#legacyUser()} ({@code c.us})
     * and returns {@link JidServer#user()} ({@code s.whatsapp.net}); WA Web does the same
     * mapping inside {@code WAWebWid.toString}'s {@code legacy} branch.
     *
     * @param server the input server
     * @return the legacy server name to use in the digest input
     */
    @WhatsAppWebExport(moduleName = "WAWebWid",
            exports = "toString",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static JidServer toLegacyServer(JidServer server) {
        if (server.equals(JidServer.legacyUser())) {
            return JidServer.user();
        }
        return server;
    }
}
