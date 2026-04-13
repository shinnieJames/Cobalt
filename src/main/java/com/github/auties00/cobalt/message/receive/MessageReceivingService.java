package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.dedup.MessageDedup;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.message.MessageInfo;
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
 * @implNote ADAPTED: WAWebCommsHandleMessagingStanza.handleMessagingStanza
 * handles the {@code "message"} tag for non-newsletter E2E messages (routing
 * to WAWebHandleMsg) and the {@code "receipt"} tag for non-call, non-retry
 * receipts (routing to WAWebHandleMsgReceipt).  Newsletter messages fall
 * through to WAWebCommsHandleWorkerCompatibleStanza.handleWorkerCompatibleStanza
 * which routes them to WAWebHandleNewsletterMsg.  Cobalt unifies both newsletter
 * and E2E message routing here, while receipt handling is split out to a
 * separate {@code ReceiptStreamHandler} at the socket stream level.
 */
public final class MessageReceivingService {
    /**
     * Logger for diagnostic messages during message routing and dedup.
     *
     * @implNote ADAPTED: WAWebCommsHandleMessagingStanza uses WALogger;
     * Cobalt uses {@code System.Logger} instead.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageReceivingService.class.getName());

    /**
     * The receiver for E2E-encrypted chat messages.
     *
     * @implNote ADAPTED: WAWebCommsHandleMessagingStanza.handleMessagingStanza
     * delegates to WAWebHandleMsg for non-newsletter messages; Cobalt uses
     * constructor-based DI via this field.
     */
    private final ChatMessageReceiver chatReceiver;

    /**
     * The receiver for plaintext newsletter messages.
     *
     * @implNote ADAPTED: WAWebCommsHandleWorkerCompatibleStanza.handleWorkerCompatibleStanza
     * delegates newsletter messages to WAWebHandleNewsletterMsg; Cobalt uses
     * constructor-based DI via this field.
     */
    private final NewsletterMessageReceiver newsletterReceiver;

    /**
     * Dedup cache preventing duplicate processing of the same E2E message
     * during offline/online transitions.
     *
     * @implNote ADAPTED: WAWebMessageDedupUtils maintains a module-level
     * pending message cache keyed by
     * WAWebPendingMessageKey.createPendingMessageKey(key, ts, encs) that
     * is cleared when offline delivery count reaches zero.  In WA Web the
     * dedup is called from WAWebHandleMsg, not handleMessagingStanza;
     * Cobalt wraps it around the receiver call in {@link #process(Node)}.
     */
    private final MessageDedup dedup;

    /**
     * Creates a new message receiving service, assembling the internal
     * receiver graph from the provided dependencies.
     *
     * @param store      the central session data repository
     * @param decryption the decryption service for Signal protocol
     *                   (PKMSG/MSG/SKMSG) and bot messages (MSMSG)
     *
     * @implNote ADAPTED: WAWebCommsHandleMessagingStanza uses module-level
     * imports for WAWebHandleMsg and WAWebHandleMsgReceipt; Cobalt assembles
     * the receiver graph via constructor-based DI.
     */
    public MessageReceivingService(
            WhatsAppStore store,
            MessageDecryption decryption
    ) {
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
     * {@link ChatMessageInfo}.
     * Returns {@code null} for unavailable (fanout placeholder) messages.
     *
     * @param node the raw incoming {@code <message>} node
     * @return the processed message info, or {@code null} for unavailable
     *         messages
     * @throws WhatsAppMessageException.Receive if decryption or
     *         validation fails for E2E messages
     *
     * @implNote ADAPTED: WAWebCommsHandleMessagingStanza.handleMessagingStanza
     * checks {@code WAWebWid.isNewsletter(from)} and returns {@code undefined}
     * for newsletter messages, letting WAWebCommsHandleWorkerCompatibleStanza
     * route them to WAWebHandleNewsletterMsg.  For non-newsletter messages it
     * calls {@code WAWebHandleMsg(e)}.  Cobalt unifies both paths here: the
     * newsletter check ({@code fromJid.hasNewsletterServer()}) routes to
     * {@link NewsletterMessageReceiver}, while all other messages route to
     * {@link ChatMessageReceiver}.
     */
    public MessageInfo process(Node node) {
        Objects.requireNonNull(node, "node");

        // WAWebCommsHandleMessagingStanza.handleMessagingStanza: checks
        // WAWebWid.isNewsletter(from) — newsletter messages return undefined,
        // falling through to WAWebCommsHandleWorkerCompatibleStanza which
        // calls WAWebHandleNewsletterMsg.  Cobalt unifies both paths here.
        var fromJid = node.getRequiredAttributeAsJid("from");
        if (fromJid.hasNewsletterServer()) { // WAWebWid.isNewsletter
            return newsletterReceiver.receive(node, fromJid);
        }

        // ADAPTED: WAWebHandleMsg calls WAWebMessageDedupUtils.addPendingMessage
        // using WAWebPendingMessageKey.createPendingMessageKey(key, ts, encs).
        // Cobalt uses a simplified key and wraps the dedup around the receiver call.
        var id = node.getRequiredAttributeAsString("id");
        var dedupKey = fromJid + ":" + id;
        if (dedup.isPending(dedupKey)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Duplicate message {0}, skipping", id);
            return null;
        }
        dedup.add(dedupKey);

        try {
            return chatReceiver.receive(node, fromJid); // WAWebHandleMsg
        } finally {
            dedup.remove(dedupKey); // NO_WA_BASIS: Cobalt-specific cleanup
        }
    }

    /**
     * Clears the pending message dedup cache.
     *
     * <p>Should be called when offline delivery ends so that messages
     * received in a new session are not falsely considered duplicates.
     *
     * @implNote WAWebMessageDedupUtils.maybeClearPendingMessages: clears
     * the cache when the offline count reaches zero.
     */
    public void clearPendingMessages() {
        dedup.clear(); // WAWebMessageDedupUtils.maybeClearPendingMessages
    }
}
