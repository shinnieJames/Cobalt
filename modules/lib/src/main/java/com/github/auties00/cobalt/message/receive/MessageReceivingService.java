package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.dedup.MessageDedup;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Objects;

/**
 * Single entry point for every inbound {@code <message>} stanza, routing each one to
 * the receiver matching the stanza's address class.
 *
 * @apiNote
 * Embedders normally interact with this service indirectly through
 * {@link com.github.auties00.cobalt.client.WhatsAppClient}; direct use is meant for
 * unit tests and for custom dispatchers that bypass the socket layer. The router
 * picks {@link NewsletterMessageReceiver} when the {@code from} JID lives on the
 * {@code @newsletter} server (Channels) and {@link ChatMessageReceiver} for every
 * other address class (1:1, group, broadcast, status, peer).
 *
 * @implNote
 * This implementation mirrors WhatsApp Web's
 * {@code WAWebCommsHandleMessagingStanza.handleMessagingStanza} dispatch but inlines
 * the dedup cache that WA Web gates behind the {@code web_pending_message_cache_enabled}
 * AB prop via {@code WAWebMessageDedupUtils.isPengingMessageCacheEnabled}; Cobalt
 * always dedups so duplicate fanout deliveries are dropped without the AB-prop
 * branch.
 */
@WhatsAppWebModule(moduleName = "WAWebCommsHandleMessagingStanza")
@WhatsAppWebModule(moduleName = "WAWebHandleMsg")
public final class MessageReceivingService {
    /**
     * Logger used for the dedup-skip diagnostic.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageReceivingService.class.getName());

    /**
     * Receiver invoked for every non-newsletter inbound stanza.
     *
     * @apiNote
     * Holds the full Signal-protocol decryption pipeline.
     */
    private final ChatMessageReceiver chatReceiver;

    /**
     * Receiver invoked for every {@code @newsletter}-server inbound stanza.
     *
     * @apiNote
     * Reads the {@code <plaintext>} child directly; no Signal decryption is involved.
     */
    private final NewsletterMessageReceiver newsletterReceiver;

    /**
     * In-flight dedup cache keyed by {@code fromJid:id}.
     *
     * @apiNote
     * Guards against the same E2E message being processed twice when the server
     * fanout duplicates a delivery during an offline-to-online transition; entries
     * are short-lived and removed in the {@code finally} block of
     * {@link #process(Node)}.
     */
    private final MessageDedup dedup;

    /**
     * Constructs the receiving service and assembles the internal receiver graph.
     *
     * @apiNote
     * Constructor injection is intentional; Cobalt does not expose a stateful
     * service-locator accessor for either receiver, both of which are
     * package-private. Pass the same {@link WhatsAppStore} used by the rest of the
     * client so the receivers see consistent self-JID and Signal-session state.
     *
     * @param store      the central session store, shared with the rest of the client
     * @param decryption the Signal-protocol decryption service (PKMSG/MSG/SKMSG) plus
     *                   the MSMSG bot-message scheme used by {@link ChatMessageReceiver}
     */
    @WhatsAppWebExport(moduleName = "WAWebCommsHandleMessagingStanza", exports = "handleMessagingStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageReceivingService(
            WhatsAppStore store,
            MessageDecryption decryption
    ) {
        this.chatReceiver = new ChatMessageReceiver(store, decryption);
        this.newsletterReceiver = new NewsletterMessageReceiver(store);
        this.dedup = new MessageDedup();
    }

    /**
     * Routes and processes an incoming {@code <message>} stanza into the appropriate
     * {@link MessageInfo} subtype.
     *
     * @apiNote
     * Returns a {@link NewsletterMessageInfo} for Channels posts and a
     * {@link ChatMessageInfo} for every other message class. Returns {@code null} for
     * unavailable fanout placeholders that should be silently acknowledged and for
     * duplicate deliveries already in flight; callers must treat both as no-ops.
     *
     * @implNote
     * This implementation builds the dedup key as {@code fromJid + ":" + id} and
     * releases it in a {@code finally} so a follow-up message from the same peer is
     * not falsely flagged when the first finishes; WhatsApp Web's
     * {@code WAWebHandleMsg} delegates the same bookkeeping to
     * {@code WAWebMessageDedupUtils.addPendingMessage}, which is also released after
     * the message is fully processed.
     *
     * @param node the raw incoming {@code <message>} node; must be non-{@code null}
     * @return the processed message info, or {@code null} when the stanza is dropped
     * @throws NullPointerException             if {@code node} is {@code null}
     * @throws WhatsAppMessageException.Receive if decryption or validation fails for
     *                                          an E2E message
     */
    @WhatsAppWebExport(moduleName = "WAWebCommsHandleMessagingStanza", exports = "handleMessagingStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebHandleMsg", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageInfo process(Node node) {
        Objects.requireNonNull(node, "node");

        var fromJid = node.getRequiredAttributeAsJid("from");
        if (fromJid.hasNewsletterServer()) {
            return newsletterReceiver.receive(node, fromJid);
        }

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
     * Clears the pending-message dedup cache.
     *
     * @apiNote
     * Invoked when the offline-delivery phase ends so messages re-delivered in a new
     * session are not mistakenly flagged as duplicates; mirrors WhatsApp Web's
     * {@code WAWebMessageDedupUtils.maybeClearPendingMessages}, which is called when
     * the in-flight count drops to zero on stream resume.
     */
    @WhatsAppWebExport(moduleName = "WAWebMessageDedupUtils", exports = "maybeClearPendingMessages",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void clearPendingMessages() {
        dedup.clear();
    }
}
