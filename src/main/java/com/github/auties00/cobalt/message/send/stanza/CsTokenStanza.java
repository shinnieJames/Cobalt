package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the {@code <cstoken>} stanza child node containing an
 * HMAC-SHA-256 non-contact token (NCT) for the recipient.
 *
 * <p>The token is included only when:
 * <ul>
 *   <li>The {@code wa_nct_token_send_enabled} AB prop is {@code true}</li>
 *   <li>The recipient is a regular user (not a bot or group)</li>
 *   <li>An NCT salt is available in the store</li>
 *   <li>The chat has an {@code accountLid} for the recipient</li>
 * </ul>
 *
 * <p>The HMAC is computed as {@code HMAC-SHA-256(salt, accountLid.toString())}
 * and cached per recipient LID, with an LRU-style eviction policy
 * (maximum 5 entries).  The salt itself is also cached to avoid
 * redundant decoding.
 *
 * @implNote WAWebSendMsgCreateFanoutStanza.genCsTokenBody: checks the
 * {@code wa_nct_token_send_enabled} AB prop, retrieves the NCT salt
 * from {@code WAWebUserPrefsIndexedDBStorage}, computes
 * {@code hmacSha256(salt, accountLid.toString())}, caches results with
 * LRU eviction (max 5), and returns {@code <cstoken>} node.
 * @see TcTokenStanza
 * @see ChatFanoutStanza
 */
public final class CsTokenStanza {
    /**
     * Logger for HMAC computation failures and missing data.
     *
     * @implNote WAWebSendMsgCreateFanoutStanza.genCsTokenBody: logs
     * {@code "[nct-cstoken] no salt available in IndexedDB"} and
     * {@code "[nct-cstoken] recipientLid is null"} warnings.
     */
    private static final System.Logger LOGGER = System.getLogger(CsTokenStanza.class.getName());

    /**
     * Maximum number of cached HMAC results per salt.
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: {@code I = 5}, the
     * LRU cache capacity for computed CSTokens.
     */
    private static final int MAX_CACHE_SIZE = 5;

    /**
     * The HMAC algorithm used for token computation.
     *
     * @implNote WACryptoHmac.hmacSha256: uses HMAC-SHA-256.
     */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * The WhatsApp store, used to retrieve the NCT salt and chat data.
     *
     * @implNote WAWebSendMsgCreateFanoutStanza.genCsTokenBody: retrieves
     * the salt from {@code WAWebUserPrefsIndexedDBStorage.userPrefsIdb}
     * and the accountLid from the chat record.
     */
    private final WhatsAppStore store;

    /**
     * The AB props service, used to check if NCT token sending is enabled.
     *
     * @implNote WAWebSendMsgCreateFanoutStanza.genCsTokenBody: checks
     * {@code WAWebABProps.getABPropConfigValue("wa_nct_token_send_enabled")}.
     */
    private final ABPropsService abPropsService;

    /**
     * Cached reference to the last salt bytes used, to avoid re-decoding.
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: module-level variable
     * {@code E} caches the decoded salt, cleared when the raw salt changes.
     */
    private byte[] cachedSalt;

    /**
     * Cached HMAC results keyed by the recipient LID string.
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: module-level {@code k}
     * map caches computed HMAC results, evicted when size exceeds
     * {@link #MAX_CACHE_SIZE}.
     */
    private final LinkedHashMap<String, byte[]> hmacCache;

    /**
     * Creates a new CS token stanza builder.
     *
     * @param store          the WhatsApp store for salt and chat lookup
     * @param abPropsService the AB props service for feature gating
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote ADAPTED: WAWebSendMsgCreateFanoutStanza.genCsTokenBody:
     * module-level function uses module-scope imports; Cobalt injects
     * dependencies via constructor.
     */
    public CsTokenStanza(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.hmacCache = new LinkedHashMap<>();
    }

    /**
     * Builds the {@code <cstoken>} node for the given chat recipient.
     *
     * <p>Returns {@code null} if the AB prop is disabled, the recipient
     * is not a regular user, no NCT salt is available, or the chat has
     * no {@code accountLid}.
     *
     * @param chatJid the recipient chat JID
     * @return the cstoken node, or {@code null} if not applicable
     *
     * @implNote WAWebSendMsgCreateFanoutStanza.genCsTokenBody: checks
     * {@code wa_nct_token_send_enabled}, {@code t.isRegularUser()},
     * retrieves salt, retrieves {@code chat.accountLid}, computes
     * {@code hmacSha256(salt, accountLid.toString())}, and returns
     * {@code wap("cstoken", null, hmacResult)}.
     */
    public Node build(Jid chatJid) {
        // WAWebSendMsgCreateFanoutStanza.genCsTokenBody: wa_nct_token_send_enabled !== true
        if (!abPropsService.getBool(ABProp.WA_NCT_TOKEN_SEND_ENABLED)) {
            return null;
        }

        // WAWebSendMsgCreateFanoutStanza.genCsTokenBody: !t.isRegularUser()
        // isRegularUser = isUser() && !isPSA() && !isBot()
        if (!isRegularUser(chatJid)) {
            return null;
        }

        // WAWebSendMsgCreateFanoutStanza.genCsTokenBody: userPrefsIdb.get("WAWebNctSalt")
        var salt = store.nctSalt().orElse(null);
        if (salt == null) {
            // WAWebSendMsgCreateFanoutStanza.genCsTokenBody:
            // WARN("[nct-cstoken] no salt available in IndexedDB")
            LOGGER.log(System.Logger.Level.WARNING, "[nct-cstoken] no salt available in store");
            return null;
        }

        // WAWebSendMsgCreateFanoutStanza.genCsTokenBody: chat.accountLid
        var chat = store.findChatByJid(chatJid).orElse(null);
        var recipientLid = chat != null ? chat.accountLid().orElse(null) : null;
        if (recipientLid == null) {
            // WAWebSendMsgCreateFanoutStanza.genCsTokenBody:
            // WARN("[nct-cstoken] recipientLid is null")
            LOGGER.log(System.Logger.Level.WARNING, "[nct-cstoken] recipientLid is null");
            return null;
        }

        try {
            // WAWebSendMsgCreateFanoutStanza.genCsTokenBody: salt cache check
            // If the salt changed, clear the HMAC cache
            if (cachedSalt == null || !java.util.Arrays.equals(cachedSalt, salt)) {
                cachedSalt = salt;
                hmacCache.clear();
            }

            // WAWebSendMsgCreateFanoutStanza.genCsTokenBody: LID string as cache key
            var lidString = recipientLid.toString();

            // WAWebSendMsgCreateFanoutStanza.genCsTokenBody: check cache
            var cached = hmacCache.get(lidString);
            if (cached != null) {
                return new NodeBuilder()
                        .description("cstoken")
                        .content(cached)
                        .build();
            }

            // WAWebSendMsgCreateFanoutStanza.genCsTokenBody:
            // hmacSha256(decodedSalt, new TextEncoder().encode(lidString))
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(salt, HMAC_ALGORITHM));
            var hmacResult = mac.doFinal(lidString.getBytes(StandardCharsets.UTF_8));

            // WAWebSendMsgCreateFanoutStanza.genCsTokenBody: LRU eviction
            if (hmacCache.size() >= MAX_CACHE_SIZE) {
                var firstKey = hmacCache.keySet().iterator().next();
                if (firstKey != null) {
                    hmacCache.remove(firstKey);
                }
            }
            hmacCache.put(lidString, hmacResult);

            // WAWebSendMsgCreateFanoutStanza.genCsTokenBody:
            // wap("cstoken", null, hmacResult)
            return new NodeBuilder()
                    .description("cstoken")
                    .content(hmacResult)
                    .build();
        } catch (GeneralSecurityException e) {
            // WAWebSendMsgCreateFanoutStanza.genCsTokenBody:
            // WARN("[nct-cstoken] generation failed - " + error)
            LOGGER.log(System.Logger.Level.WARNING,
                    "[nct-cstoken] generation failed - {0}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks whether the given JID represents a regular user
     * (not a bot, group, or broadcast).
     *
     * @param jid the JID to check
     * @return {@code true} if the JID is a regular user
     *
     * @implNote WAWebWid.isRegularUser: returns
     * {@code isUser() && !isPSA() && !isBot()}.
     * ADAPTED: Cobalt does not have a PSA check; the PSA account
     * is extremely rare and irrelevant for NCT tokens.
     */
    private static boolean isRegularUser(Jid jid) {
        // WAWebWid.isRegularUser: isUser() && !isPSA() && !isBot()
        // isUser() includes c.us, lid, bot, hosted, hosted.lid
        // After excluding bot, only c.us and lid remain for regular users
        return (jid.hasUserServer() || jid.hasLidServer()) && !jid.isBot();
    }
}
