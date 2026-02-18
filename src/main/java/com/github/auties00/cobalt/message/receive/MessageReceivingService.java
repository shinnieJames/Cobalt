package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.dedup.MessageDedup;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.model.info.MessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Objects;

/**
 * Orchestrates the processing of all incoming messages by dispatching
 * to the appropriate receiver based on the sender's JID type.
 *
 * <p>This service is the main entry point for incoming message processing,
 * mirroring the role of {@code MessageSendingService} on the outbound side.
 * It receives a raw {@code <message>} node and routes to:
 * <ul>
 *   <li>{@link NewsletterMessageReceiver} for newsletter JIDs
 *       ({@code *@newsletter}) — plaintext protobuf, no E2E encryption</li>
 *   <li>{@link ChatMessageReceiver} for all other JIDs — full Signal
 *       protocol decryption pipeline</li>
 * </ul>
 *
 * @apiNote WAWebCommsHandleMessagingStanza.handleMessagingStanza: routes
 * newsletter messages to the SMAX pipeline and E2E messages to
 * WAWebHandleMsg.
 */
public final class MessageReceivingService {
    private static final System.Logger LOGGER = System.getLogger("MessageReceivingService");

    /**
     * The receiver for E2E-encrypted chat messages.
     */
    private final ChatMessageReceiver chatReceiver;

    /**
     * The receiver for plaintext newsletter messages.
     */
    private final NewsletterMessageReceiver newsletterReceiver;

    /**
     * The central session data repository, used for stanza parsing.
     */
    private final WhatsAppStore store;

    /**
     * Dedup cache preventing duplicate processing of the same E2E message
     * during offline/online transitions.
     *
     * @apiNote WAWebMessageDedupUtils: maintains a pending message cache
     * keyed by (chat, externalId, ts) that is cleared when offline delivery
     * count reaches zero.
     */
    private final MessageDedup dedup;

    /**
     * Creates a new message receiving service, assembling the internal
     * receiver graph from the provided dependencies.
     *
     * @param store      the central session data repository
     * @param decryption the decryption service for Signal protocol
     *                   (PKMSG/MSG/SKMSG) and bot messages (MSMSG)
     */
    public MessageReceivingService(
            WhatsAppStore store,
            MessageDecryption decryption
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.chatReceiver = new ChatMessageReceiver(store, decryption);
        this.newsletterReceiver = new NewsletterMessageReceiver(store);
        this.dedup = new MessageDedup();
    }

    /**
     * Processes an incoming {@code <message>} node, producing the
     * appropriate {@link MessageInfo} subtype.
     *
     * <p>Newsletter messages produce
     * {@link NewsletterMessageInfo};
     * all other messages go through E2E decryption and produce
     * {@link com.github.auties00.cobalt.model.info.ChatMessageInfo}.
     * Returns {@code null} for unavailable (fanout placeholder) messages.
     *
     * @param node the raw incoming {@code <message>} node
     * @return the processed message info, or {@code null} for unavailable
     *         messages
     * @throws WhatsAppMessageException.Receive if decryption or
     *         validation fails for E2E messages
     *
     * @apiNote WAWebCommsHandleMessagingStanza: checks
     * {@code isNewsletter(from)} to route to the SMAX pipeline or to
     * WAWebHandleMsg.
     */
    public MessageInfo process(Node node) {
        Objects.requireNonNull(node, "node");

        var fromJid = node.getRequiredAttributeAsJid("from");
        if (fromJid.hasNewsletterServer()) {
            return newsletterReceiver.receive(node, fromJid);
        }

        // Dedup check for E2E messages
        var id = node.getRequiredAttributeAsString("id");
        var dedupKey = fromJid + ":" + id;
        if (dedup.isPending(dedupKey)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Duplicate message {0}, skipping", id);
            return null;
        }
        dedup.add(dedupKey);

        try {
            return chatReceiver.receive(node, fromJid);
        } finally {
            dedup.remove(dedupKey);
        }
    }

    /**
     * Clears the pending message dedup cache.
     *
     * <p>Should be called when offline delivery ends so that messages
     * received in a new session are not falsely considered duplicates.
     *
     * @apiNote WAWebMessageDedupUtils.maybeClearPendingMessages: clears
     * the cache when the offline count reaches zero.
     */
    public void clearPendingMessages() {
        dedup.clear();
    }
}
