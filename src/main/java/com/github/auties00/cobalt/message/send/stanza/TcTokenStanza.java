package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.time.Instant;
import java.util.Objects;

/**
 * Builds the {@code <tctoken>} stanza child node with the trust
 * contact token for the recipient.
 *
 * <p>The token is included only when the
 * {@code privacy_token_sending_on_all_1_on_1_messages} AB prop is
 * enabled and the chat has a non-expired TC token.
 *
 * @implNote WAWebSendMsgCreateFanoutStanza function S/R: checks
 * {@code privacy_token_sending_on_all_1_on_1_messages} AB prop, then
 * includes {@code <tctoken>} with the chat's tcToken bytes if the
 * token is not expired per
 * {@code WAWebTrustedContactsUtils.isTokenExpired}.
 * @see CsTokenStanza
 * @see ChatFanoutStanza
 */
public final class TcTokenStanza {
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
     * WAWebTrustedContactsUtils.isTokenExpired: uses
     * {@code tctoken_duration} and {@code tctoken_num_buckets} AB props.
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

        // WAWebTrustedContactsUtils.isTokenExpired(e, t): returns e < tokenExpirationCutoff(t)
        if (isTokenExpired(tcTokenTimestamp)) {
            return null;
        }

        return new NodeBuilder()
                .description("tctoken")
                .content(tcToken)
                .build();
    }

    /**
     * Checks whether the given token timestamp is expired for a receiver
     * token.
     *
     * <p>The expiration logic divides time into buckets of
     * {@code tctoken_duration} seconds each.  The current bucket is
     * computed, then a cutoff timestamp is calculated as
     * {@code (currentBucket - (numBuckets - 1)) * duration}.  If the
     * token timestamp is before this cutoff, it is considered expired.
     *
     * @param tokenTimestamp the token timestamp
     * @return {@code true} if the token is expired
     *
     * @implNote WAWebTrustedContactsUtils.isTokenExpired: delegates
     * to {@code tokenExpirationCutoff} which computes the cutoff
     * from {@code tctoken_duration} and {@code tctoken_num_buckets}.
     */
    private boolean isTokenExpired(Instant tokenTimestamp) {
        // WAWebTrustedContactsUtils.getTcTokenDuration for Receiver mode:
        // Math.min(getABPropConfigValue("tctoken_duration"), 15552000)
        var duration = Math.min(
                abPropsService.getInt(ABProp.TCTOKEN_DURATION),
                15_552_000 // WAWebTrustedContactsUtils: max = 15552e3 (180 days)
        );
        if (duration <= 0) {
            return true;
        }

        // WAWebTrustedContactsUtils.tokenExpirationCutoff:
        // var n = getABPropConfigValue("tctoken_num_buckets")
        // var a = Math.floor(unixTime() / r)
        // var i = a - (n - 1)
        // return castToUnixTime(i * r)
        var numBuckets = abPropsService.getInt(ABProp.TCTOKEN_NUM_BUCKETS);
        var now = Instant.now().getEpochSecond();
        var currentBucket = Math.floorDiv(now, duration);
        var cutoffBucket = currentBucket - (numBuckets - 1);
        var cutoffEpoch = cutoffBucket * duration;

        // WAWebTrustedContactsUtils.isTokenExpired: e < cutoff
        return tokenTimestamp.getEpochSecond() < cutoffEpoch;
    }
}
