package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Builds the optional {@code <tctoken>} child of an outgoing
 * {@code <message>} stanza carrying the trusted-contact privacy token for
 * the recipient, and hosts Cobalt's port of the
 * {@code WAWebTrustedContactsUtils} helpers.
 *
 * @apiNote
 * The TC token is the privacy-tier handshake that opts a contact pair
 * into receiving privacy-enhanced features (verified history, identity
 * pin). On every outgoing 1:1 message the sender can attach the most
 * recent token they hold for the recipient to demonstrate the trust
 * relationship. {@link ChatFanoutStanza} composes the {@code <tctoken>}
 * child ahead of the lower-priority {@code <cstoken>} fallback in
 * {@link CsTokenStanza}.
 *
 * @implNote
 * This implementation co-locates the lifetime helpers from
 * {@code WAWebTrustedContactsUtils} because that JS module is stateless
 * (it reads AB props and the system clock only) and its single Cobalt
 * caller lives in this class. The helpers ({@link #getTcTokenDuration},
 * {@link #tokenExpirationCutoff}, {@link #isTokenExpired},
 * {@link #shouldSendNewToken}, {@link #encodeTcTokenForMex},
 * {@link TcTokenMode}) are kept package-private even though they are not
 * called externally, so the relationship to the WA Web exports stays
 * obvious in the annotations.
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgCreateFanoutStanza")
@WhatsAppWebModule(moduleName = "WAWebTrustedContactsUtils")
public final class TcTokenStanza {
    /**
     * The upper bound on {@code tctoken_duration} in seconds; 180 days,
     * matching WA Web's {@code e = 15552e3} constant.
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "getTcTokenDuration",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int MAX_TC_TOKEN_DURATION_SECONDS = 15_552_000;

    /**
     * The {@link WhatsAppStore} used to look up the recipient chat.
     */
    private final WhatsAppStore store;

    /**
     * The {@link ABPropsService} consulted for the token-emission gate
     * and the lifetime parameters.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs a builder bound to the given store and AB-props service.
     *
     * @apiNote
     * Constructed once per client; the builder is otherwise stateless.
     *
     * @param store          the {@link WhatsAppStore}
     * @param abPropsService the {@link ABPropsService}
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public TcTokenStanza(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
    }

    /**
     * Builds the {@code <tctoken>} child for the recipient chat, or
     * {@code null} when emission does not apply.
     *
     * @apiNote
     * Returns {@code null} when any of the four gates fails:
     * {@link ABProp#PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES} is
     * disabled, the chat is not found in the store, the chat has no
     * recorded TC token, or the token timestamp is past the receiver-side
     * expiry cutoff.
     *
     * @implNote
     * This implementation queries the receiver-side expiry parameters
     * ({@link TcTokenMode#RECEIVER}); the sender-side parameters drive
     * the separate {@link #shouldSendNewToken(Instant)} predicate used by
     * the token rotation flow.
     *
     * @param chatJid the recipient chat {@link Jid}
     * @return the {@code <tctoken>} {@link Node}, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Node build(Jid chatJid) {
        var tcTokenEnabled = abPropsService.getBool(ABProp.PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES);
        if (!tcTokenEnabled) {
            return null;
        }

        var chat = store.findChatByJid(chatJid).orElse(null);
        if (chat == null) {
            return null;
        }

        var tcToken = chat.tcToken().orElse(null);
        var tcTokenTimestamp = chat.tcTokenTimestamp().orElse(null);

        if (tcToken == null || tcTokenTimestamp == null) {
            return null;
        }

        if (isTokenExpired(tcTokenTimestamp, TcTokenMode.RECEIVER)) {
            return null;
        }

        return new NodeBuilder()
                .description("tctoken")
                .content(tcToken)
                .build();
    }

    /**
     * Returns the token duration in seconds for the given role, clamped
     * to {@link #MAX_TC_TOKEN_DURATION_SECONDS}.
     *
     * @apiNote
     * Returns the receiver-side prop value for
     * {@link TcTokenMode#RECEIVER} and the sender-side prop value for
     * {@link TcTokenMode#SENDER}; the clamp protects against AB-prop
     * misconfigurations that would otherwise yield extreme bucket sizes.
     *
     * @param mode the {@link TcTokenMode}
     * @return the token lifetime in seconds
     * @throws NullPointerException if {@code mode} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "getTcTokenDuration",
            adaptation = WhatsAppAdaptation.DIRECT)
    private int getTcTokenDuration(TcTokenMode mode) {
        var durationProp = mode == TcTokenMode.RECEIVER
                ? ABProp.TCTOKEN_DURATION
                : ABProp.TCTOKEN_DURATION_SENDER;
        return Math.min(abPropsService.getInt(durationProp), MAX_TC_TOKEN_DURATION_SECONDS);
    }

    /**
     * Returns the unix-seconds cutoff below which a token timestamp is
     * considered expired for the given role.
     *
     * @apiNote
     * The cutoff is computed from a rolling bucket: every token issued
     * within the last {@code numBuckets} buckets of duration
     * {@link #getTcTokenDuration} is still valid. The boundary instant is
     * {@code (currentBucket - (numBuckets - 1)) * duration} where
     * {@code currentBucket = floor(unixTime / duration)}.
     *
     * @implNote
     * This implementation guards against a zero or negative duration by
     * returning {@link Long#MAX_VALUE}, which forces every token to be
     * considered expired; WA Web's prop default is 604800 seconds so the
     * guard rarely fires in practice.
     *
     * @param mode the {@link TcTokenMode}
     * @return the cutoff in seconds since the Unix epoch
     * @throws NullPointerException if {@code mode} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "tokenExpirationCutoff",
            adaptation = WhatsAppAdaptation.DIRECT)
    private long tokenExpirationCutoff(TcTokenMode mode) {
        var bucketsProp = mode == TcTokenMode.RECEIVER
                ? ABProp.TCTOKEN_NUM_BUCKETS
                : ABProp.TCTOKEN_NUM_BUCKETS_SENDER;

        var numBuckets = abPropsService.getInt(bucketsProp);

        var duration = getTcTokenDuration(mode);
        if (duration <= 0) {
            return Long.MAX_VALUE;
        }

        var currentBucket = Math.floorDiv(Instant.now().getEpochSecond(), duration);
        var cutoffBucket = currentBucket - (numBuckets - 1);
        return cutoffBucket * duration;
    }

    /**
     * Returns whether the given token timestamp falls before the role's
     * expiry cutoff.
     *
     * @apiNote
     * A token is expired when its issue timestamp is strictly less than
     * the cutoff returned by {@link #tokenExpirationCutoff(TcTokenMode)}.
     *
     * @param tokenTimestamp the token's issue timestamp
     * @param mode           the {@link TcTokenMode}
     * @return {@code true} when the token is expired
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "isTokenExpired",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isTokenExpired(Instant tokenTimestamp, TcTokenMode mode) {
        return tokenTimestamp.getEpochSecond() < tokenExpirationCutoff(mode);
    }

    /**
     * Returns whether the sender should issue a fresh TC token to the
     * recipient.
     *
     * @apiNote
     * A new token is required when no prior token exists or when the
     * prior token falls into an earlier sender bucket than the current
     * time. The sender lifetime is read from
     * {@link ABProp#TCTOKEN_DURATION_SENDER} (uncapped, unlike the
     * receiver-side helper).
     *
     * @implNote
     * This implementation guards against a zero or negative duration by
     * forcing a rotation; WA Web's prop default is 604800 seconds.
     *
     * @param tokenTimestamp the prior sender-token timestamp, or
     *                       {@code null} when no token has been issued
     * @return {@code true} when a new token should be sent
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "shouldSendNewToken",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean shouldSendNewToken(Instant tokenTimestamp) {
        if (tokenTimestamp == null) {
            return true;
        }
        var duration = abPropsService.getInt(ABProp.TCTOKEN_DURATION_SENDER);
        if (duration <= 0) {
            return true;
        }
        var nowBucket = Math.floorDiv(Instant.now().getEpochSecond(), duration);
        var tokenBucket = Math.floorDiv(tokenTimestamp.getEpochSecond(), duration);
        return nowBucket > tokenBucket;
    }

    /**
     * Encodes a raw TC token as a standard base64 string suitable for
     * inlining in a MEX (GraphQL) privacy-token argument.
     *
     * @apiNote
     * Used by callers that need to pass the token through a JSON or
     * GraphQL surface rather than a binary stanza child. The encoding is
     * standard base64 with padding (matching WA Web's
     * {@code WABase64.encodeB64}).
     *
     * @param tcToken the raw token bytes
     * @return the base64-encoded token
     * @throws NullPointerException if {@code tcToken} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "encodeTcTokenForMex",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static String encodeTcTokenForMex(byte[] tcToken) {
        return Base64.getEncoder().encodeToString(Objects.requireNonNull(tcToken, "tcToken"));
    }

    /**
     * Identifies the role a TC token plays when its lifetime parameters
     * are looked up.
     *
     * @apiNote
     * Each role is backed by a distinct pair of {@code tctoken_duration*}
     * and {@code tctoken_num_buckets*} AB props, so the same token bytes
     * have different validity windows depending on whether the local
     * device is the sender or the receiver of the token. Mirrors WA Web's
     * {@code TcTokenMode} enum.
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "TcTokenMode",
            adaptation = WhatsAppAdaptation.DIRECT)
    private enum TcTokenMode {
        /**
         * The current device is the token's sender; lifetime parameters
         * are read from {@link ABProp#TCTOKEN_DURATION_SENDER} and
         * {@link ABProp#TCTOKEN_NUM_BUCKETS_SENDER}.
         */
        SENDER,

        /**
         * The current device is the token's receiver; lifetime parameters
         * are read from {@link ABProp#TCTOKEN_DURATION} and
         * {@link ABProp#TCTOKEN_NUM_BUCKETS}.
         */
        RECEIVER
    }
}
