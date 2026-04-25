package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Builds the {@code <tctoken>} stanza child node with the trust
 * contact token for the recipient.
 *
 * <p>The token is included only when the
 * {@code privacy_token_sending_on_all_1_on_1_messages} AB prop is
 * enabled and the chat has a non-expired TC token.
 *
 * <p>This class also hosts Cobalt's adapted port of the
 * {@code WAWebTrustedContactsUtils} module, which exposes a small set
 * of pure token-lifetime predicates and a MEX base64 encoder.  Since
 * the JS module has no state of its own (it reads only AB props and
 * the system clock), its helpers live here alongside the single
 * current caller; further Cobalt callers that need to evaluate a
 * token's lifetime may depend on this class.
 *
 * @implNote WAWebSendMsgCreateFanoutStanza function S/R: checks
 * {@code privacy_token_sending_on_all_1_on_1_messages} AB prop, then
 * includes {@code <tctoken>} with the chat's tcToken bytes if the
 * token is not expired per
 * {@code WAWebTrustedContactsUtils.isTokenExpired}.
 * @see CsTokenStanza
 * @see ChatFanoutStanza
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgCreateFanoutStanza")
@WhatsAppWebModule(moduleName = "WAWebTrustedContactsUtils")
public final class TcTokenStanza {
    /**
     * Upper bound on {@code tctoken_duration} in seconds, 180 days.
     *
     * @implNote WAWebTrustedContactsUtils top-level constant:
     * {@code e = 15552e3}, i.e. 180 days expressed in seconds.
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "getTcTokenDuration",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int MAX_TC_TOKEN_DURATION_SECONDS = 15_552_000;

    /**
     * The WhatsApp store, used for chat lookup.
     *
     * @implNote WAWebSendMsgCreateFanoutStanza function S/R: retrieves
     * the chat from {@code WAWebChatCollection.ChatCollection.get(l)}.
     */
    private final WhatsAppStore store;

    /**
     * The AB props service, used to check the privacy token AB prop
     * and the token expiry parameters.
     *
     * @implNote WAWebSendMsgCreateFanoutStanza function S/R: checks
     * {@code WAWebABProps.getABPropConfigValue("privacy_token_sending_on_all_1_on_1_messages")}.
     * WAWebTrustedContactsUtils: queries {@code tctoken_duration},
     * {@code tctoken_duration_sender}, {@code tctoken_num_buckets}
     * and {@code tctoken_num_buckets_sender}.
     */
    private final ABPropsService abPropsService;

    /**
     * Creates a new TC token stanza builder.
     *
     * @param store          the WhatsApp store for chat lookup
     * @param abPropsService the AB props service for feature gating
     *                       and token expiry parameters
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote ADAPTED: WAWebSendMsgCreateFanoutStanza function S/R:
     * module-level function uses module-scope imports; Cobalt injects
     * dependencies via constructor.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public TcTokenStanza(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
    }

    /**
     * Builds the {@code <tctoken>} node for the given chat recipient.
     *
     * <p>Returns {@code null} if the AB prop is disabled, the chat is
     * not found, the chat has no TC token, or the token timestamp is
     * expired.
     *
     * @param chatJid the recipient chat JID
     * @return the tctoken node, or {@code null} if not applicable
     *
     * @implNote WAWebSendMsgCreateFanoutStanza function S/R: checks
     * AB prop, retrieves tcToken and tcTokenTimestamp from chat,
     * verifies not expired via
     * {@code WAWebTrustedContactsUtils.isTokenExpired(n, TcTokenMode.Receiver)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Node build(Jid chatJid) {
        // WAWebSendMsgCreateFanoutStanza function S/R:
        // if (!getABPropConfigValue("privacy_token_sending_on_all_1_on_1_messages") || e == null)
        var tcTokenEnabled = abPropsService.getBool(ABProp.PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES);
        if (!tcTokenEnabled) {
            return null;
        }

        var chat = store.findChatByJid(chatJid).orElse(null);
        if (chat == null) {
            return null;
        }

        // WAWebSendMsgCreateFanoutStanza function S/R:
        // var t = e.tcToken, n = e.tcTokenTimestamp
        var tcToken = chat.tcToken().orElse(null);
        var tcTokenTimestamp = chat.tcTokenTimestamp().orElse(null);

        // WAWebSendMsgCreateFanoutStanza function S/R:
        // t == null || n == null || isTokenExpired(n, TcTokenMode.Receiver) → null
        if (tcToken == null || tcTokenTimestamp == null) {
            return null;
        }

        // WAWebTrustedContactsUtils.isTokenExpired(n, TcTokenMode.Receiver)
        if (isTokenExpired(tcTokenTimestamp, TcTokenMode.RECEIVER)) {
            return null;
        }

        return new NodeBuilder()
                .description("tctoken")
                .content(tcToken)
                .build();
    }

    /**
     * Returns the token duration in seconds for the given mode,
     * clamped to {@link #MAX_TC_TOKEN_DURATION_SECONDS}.
     *
     * @param mode the trusted-contact token role to query
     * @return the duration in seconds
     * @throws NullPointerException if {@code mode} is {@code null}
     *
     * @implNote WAWebTrustedContactsUtils.getTcTokenDuration:
     * {@code Math.min(getABPropConfigValue(t === Receiver ?
     * "tctoken_duration" : "tctoken_duration_sender"), 15552e3)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "getTcTokenDuration",
            adaptation = WhatsAppAdaptation.DIRECT)
    private int getTcTokenDuration(TcTokenMode mode) {
        // WAWebTrustedContactsUtils.getTcTokenDuration:
        // n = t === Receiver ? "tctoken_duration" : "tctoken_duration_sender"
        var durationProp = mode == TcTokenMode.RECEIVER
                ? ABProp.TCTOKEN_DURATION
                : ABProp.TCTOKEN_DURATION_SENDER;
        // WAWebTrustedContactsUtils.getTcTokenDuration:
        // Math.min(getABPropConfigValue(n), 15552e3)
        return Math.min(abPropsService.getInt(durationProp), MAX_TC_TOKEN_DURATION_SECONDS);
    }

    /**
     * Returns the unix-seconds cutoff below which a token timestamp
     * is considered expired, for the given mode.
     *
     * <p>The cutoff is computed as
     * {@code (currentBucket - (numBuckets - 1)) * duration} where
     * {@code currentBucket = floor(unixTime / duration)}.
     *
     * @param mode the trusted-contact token role to query
     * @return the cutoff expressed in seconds since the Unix epoch
     * @throws NullPointerException if {@code mode} is {@code null}
     *
     * @implNote WAWebTrustedContactsUtils.tokenExpirationCutoff:
     * {@code t = e === Receiver ? "tctoken_num_buckets" :
     * "tctoken_num_buckets_sender"}, {@code n =
     * getABPropConfigValue(t)}, {@code r = getTcTokenDuration(e)},
     * {@code a = floor(unixTime() / r)}, {@code i = a - (n - 1)},
     * {@code return castToUnixTime(i * r)}.  Cobalt returns a plain
     * {@code long} of seconds — the {@code castToUnixTime} clamp to
     * int32 is irrelevant for realistic bucket math and matches the
     * value space of {@link Instant#getEpochSecond()}.
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "tokenExpirationCutoff",
            adaptation = WhatsAppAdaptation.DIRECT)
    private long tokenExpirationCutoff(TcTokenMode mode) {
        // WAWebTrustedContactsUtils.tokenExpirationCutoff:
        // t = e === Receiver ? "tctoken_num_buckets" : "tctoken_num_buckets_sender"
        var bucketsProp = mode == TcTokenMode.RECEIVER
                ? ABProp.TCTOKEN_NUM_BUCKETS
                : ABProp.TCTOKEN_NUM_BUCKETS_SENDER;

        // WAWebTrustedContactsUtils.tokenExpirationCutoff: n = getABPropConfigValue(t)
        var numBuckets = abPropsService.getInt(bucketsProp);

        // WAWebTrustedContactsUtils.tokenExpirationCutoff: r = getTcTokenDuration(e)
        var duration = getTcTokenDuration(mode);
        if (duration <= 0) {
            // NO_WA_BASIS: WA Web's AB prop config provides a non-zero
            // default (604800); Cobalt guards against division-by-zero
            // if the AB prop layer ever returns a non-positive value.
            return Long.MAX_VALUE;
        }

        // WAWebTrustedContactsUtils.tokenExpirationCutoff:
        // a = Math.floor(unixTime() / r)
        var currentBucket = Math.floorDiv(Instant.now().getEpochSecond(), duration);
        // WAWebTrustedContactsUtils.tokenExpirationCutoff: i = a - (n - 1)
        var cutoffBucket = currentBucket - (numBuckets - 1);
        // WAWebTrustedContactsUtils.tokenExpirationCutoff:
        // return castToUnixTime(i * r)
        return cutoffBucket * duration;
    }

    /**
     * Returns {@code true} if the given token timestamp is expired
     * for the given mode.
     *
     * @param tokenTimestamp the token timestamp to check
     * @param mode           the trusted-contact token role to query
     * @return {@code true} if the token is expired
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebTrustedContactsUtils.isTokenExpired:
     * {@code e < tokenExpirationCutoff(t)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "isTokenExpired",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isTokenExpired(Instant tokenTimestamp, TcTokenMode mode) {
        // WAWebTrustedContactsUtils.isTokenExpired: return e < d(t)
        return tokenTimestamp.getEpochSecond() < tokenExpirationCutoff(mode);
    }

    /**
     * Returns {@code true} if a new sender-side TC token should be
     * issued — either because no prior token exists, or because the
     * prior token falls into an earlier {@code tctoken_duration_sender}
     * bucket than the current time.
     *
     * @param tokenTimestamp the prior sender-token timestamp, or
     *                       {@code null} if no token has been issued
     * @return {@code true} if a new token should be sent
     *
     * @implNote WAWebTrustedContactsUtils.shouldSendNewToken:
     * {@code if (e == null) return true}, else
     * {@code t = getABPropConfigValue("tctoken_duration_sender")},
     * {@code n = floor(unixTime()/t)}, {@code r = floor(e/t)},
     * {@code return n > r}.  Note the JS function reads the raw
     * {@code tctoken_duration_sender} AB prop directly — it does NOT
     * go through {@code getTcTokenDuration} and therefore is NOT
     * clamped to 15552000.
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "shouldSendNewToken",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean shouldSendNewToken(Instant tokenTimestamp) {
        // WAWebTrustedContactsUtils.shouldSendNewToken: if (e == null) return true
        if (tokenTimestamp == null) {
            return true;
        }
        // WAWebTrustedContactsUtils.shouldSendNewToken:
        // t = getABPropConfigValue("tctoken_duration_sender")
        var duration = abPropsService.getInt(ABProp.TCTOKEN_DURATION_SENDER);
        if (duration <= 0) {
            // NO_WA_BASIS: Cobalt guards against division-by-zero; the
            // WA Web AB prop default is 604800.
            return true;
        }
        // WAWebTrustedContactsUtils.shouldSendNewToken:
        // n = floor(unixTime() / t)
        var nowBucket = Math.floorDiv(Instant.now().getEpochSecond(), duration);
        // WAWebTrustedContactsUtils.shouldSendNewToken: r = floor(e / t)
        var tokenBucket = Math.floorDiv(tokenTimestamp.getEpochSecond(), duration);
        // WAWebTrustedContactsUtils.shouldSendNewToken: return n > r
        return nowBucket > tokenBucket;
    }

    /**
     * Encodes a raw trusted-contact token as a standard base64 string
     * suitable for embedding in a MEX (GraphQL) privacy-token argument.
     *
     * @param tcToken the raw token bytes
     * @return the base64-encoded token, with padding
     * @throws NullPointerException if {@code tcToken} is {@code null}
     *
     * @implNote WAWebTrustedContactsUtils.encodeTcTokenForMex:
     * {@code return WABase64.encodeB64(e)}.  {@code WABase64.encodeB64}
     * uses standard alphabet (chars {@code +} and {@code /}) with
     * {@code =} padding — {@link Base64#getEncoder()} matches exactly.
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "encodeTcTokenForMex",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static String encodeTcTokenForMex(byte[] tcToken) {
        // WAWebTrustedContactsUtils.encodeTcTokenForMex:
        // return WABase64.encodeB64(e)
        return Base64.getEncoder().encodeToString(Objects.requireNonNull(tcToken, "tcToken"));
    }

    /**
     * Identifies the role a trusted-contact token plays when queried
     * for its lifetime parameters.  Each role is backed by a distinct
     * pair of {@code tctoken_duration*} / {@code tctoken_num_buckets*}
     * AB props.
     *
     * @implNote WAWebTrustedContactsUtils.TcTokenMode:
     * {@code $InternalEnum({Sender: "sender", Receiver: "receiver"})}.
     */
    @WhatsAppWebExport(moduleName = "WAWebTrustedContactsUtils", exports = "TcTokenMode",
            adaptation = WhatsAppAdaptation.DIRECT)
    private enum TcTokenMode {
        /**
         * The current device is the token's sender — use
         * {@code tctoken_duration_sender} and
         * {@code tctoken_num_buckets_sender}.
         *
         * @implNote WAWebTrustedContactsUtils.TcTokenMode.Sender:
         * string value {@code "sender"}.
         */
        SENDER,

        /**
         * The current device is the token's receiver — use
         * {@code tctoken_duration} and {@code tctoken_num_buckets}.
         *
         * @implNote WAWebTrustedContactsUtils.TcTokenMode.Receiver:
         * string value {@code "receiver"}.
         */
        RECEIVER
    }
}
