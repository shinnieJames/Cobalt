package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Objects;

/**
 * Sealed base for the two inbound message receivers that collapse WhatsApp Web's
 * {@code WAWebHandleMsg} entry point into a per-stanza-class implementation.
 *
 * @apiNote
 * Subclasses are package-private and only reachable through
 * {@link MessageReceivingService#process(Node)}; embedders never instantiate or
 * subclass directly. The base owns the shared protobuf decoder and the
 * {@code isMeAccount} JID comparison so both subclasses agree on which messages are
 * self-authored.
 *
 * @implSpec
 * A subclass must convert a raw inbound {@code <message>} node into the appropriate
 * {@link MessageInfo} subtype via {@link #receive(Node, Jid)}; returning {@code null}
 * is reserved for stanzas that the receiver intentionally drops without raising an
 * error (for example unavailable fanout placeholders or newsletter messages with no
 * payload).
 *
 * @param <T> the concrete {@link MessageInfo} subtype produced by the receiver
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsg")
abstract sealed class MessageReceiver<T extends MessageInfo>
        permits ChatMessageReceiver, NewsletterMessageReceiver {

    /**
     * Logger used for diagnostic output emitted by the protobuf decoder fallback.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageReceiver.class.getName());

    /**
     * Central session store shared with every receive subclass.
     *
     * @apiNote
     * Holds the local PN, LID, Signal sessions, sender keys, and the per-chat message
     * cache used by every concrete receiver to identify the self account and to look
     * up bot-message metadata.
     */
    final WhatsAppStore store;

    /**
     * Constructs a receiver bound to the given store.
     *
     * @apiNote
     * Invoked only by the package-private subclass constructors; the orchestrator does
     * not call this directly.
     *
     * @param store the central session store; must be non-{@code null}
     * @throws NullPointerException if {@code store} is {@code null}
     */
    MessageReceiver(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Processes an incoming {@code <message>} node into the receiver's concrete
     * {@link MessageInfo} subtype.
     *
     * @apiNote
     * Called by {@link MessageReceivingService#process(Node)} after the orchestrator
     * has selected the receiver based on the {@code from} JID; a {@code null} return
     * means the message was intentionally dropped (unavailable fanout placeholder or
     * newsletter with no payload) and the caller acknowledges with a plain ack.
     *
     * @implSpec
     * A subclass must return {@code null} only for intentional silent drops; it must
     * throw a
     * {@link com.github.auties00.cobalt.exception.WhatsAppMessageException.Receive}
     * subtype for failures so the orchestrator can decide between retry, NACK, and
     * dedup.
     *
     * @param node    the raw incoming {@code <message>} node
     * @param fromJid the JID parsed from the stanza's {@code from} attribute
     * @return the processed message info, or {@code null} for an intentional silent
     *         drop
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsg", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    abstract T receive(Node node, Jid fromJid);

    /**
     * Returns the logged-in device's PN JID or fails fast when no session is active.
     *
     * @apiNote
     * Used by subclasses to construct {@code DeviceSentMessage} fallbacks and bot
     * target-sender JIDs when the stanza does not carry one.
     *
     * @implNote
     * This implementation throws {@link IllegalStateException} because the receive
     * pipeline is only entered while a session is active; the orchestrator drains
     * pending messages before tearing the session down.
     *
     * @return the local PN JID
     * @throws IllegalStateException if no session is active
     */
    Jid requireSelfJid() {
        return store.jid().orElseThrow(() ->
                new IllegalStateException("Not logged in"));
    }

    /**
     * Decodes the raw protobuf plaintext into a {@link MessageContainer}, returning
     * {@code null} on parse failure rather than throwing.
     *
     * @apiNote
     * Used by both receivers after decryption (chat path) or after reading the
     * {@code <plaintext>} child (newsletter path); a {@code null} return lets the
     * caller decide whether to NACK, retry, or silently drop based on the originating
     * stanza class.
     *
     * @implNote
     * This implementation logs the failure at WARNING level and swallows the
     * exception; WhatsApp Web's {@code WAWebHandleMsgProcess.processDecryptedMessageProto}
     * raises a {@code MessageValidationError} instead, but Cobalt centralises the NACK
     * decision in the caller rather than the decoder.
     *
     * @param messageId the message id used for log context
     * @param plaintext the raw protobuf bytes
     * @return the decoded container, or {@code null} when the protobuf cannot be
     *         parsed
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgProcess", exports = "processDecryptedMessageProto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    MessageContainer decodeProtobuf(String messageId, byte[] plaintext) {
        try {
            return MessageContainerSpec.decode(plaintext);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to decode protobuf for message {0}: {1}",
                    messageId, e.getMessage());
            return null;
        }
    }

    /**
     * Returns whether the parsed stanza was authored by the logged-in user.
     *
     * @apiNote
     * Convenience overload over {@link #isFromMe(Jid)} for callers that already hold
     * the parsed stanza; used by {@link ChatMessageReceiver} to gate
     * recipient-attribute validation and DSM expectations.
     *
     * @param stanza the parsed stanza
     * @return {@code true} if {@link MessageReceiveStanza#senderJid()} matches the
     *         local PN or LID account
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsMeUser", exports = "isMeAccount",
            adaptation = WhatsAppAdaptation.ADAPTED)
    boolean isFromMe(MessageReceiveStanza stanza) {
        return isFromMe(stanza.senderJid());
    }

    /**
     * Returns whether the given sender JID matches the logged-in user's account in
     * either the PN or LID addressing mode.
     *
     * @apiNote
     * Mirrors WhatsApp Web's {@code WAWebUserPrefsMeUser.isMeAccount} predicate; both
     * addressing modes are checked so a message addressed via either appears as self,
     * which matters for the companion-device receipts that always loop back via the
     * primary PN.
     *
     * @implNote
     * This implementation compares user-level JIDs via {@link Jid#toUserJid()} so
     * companion-device addresses ({@code user:device@server}) collapse to the same
     * account as the primary {@code user@server} JID.
     *
     * @param senderJid the sender JID to test; {@code null} returns {@code false}
     * @return {@code true} if {@code senderJid} matches the local PN or LID account
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsMeUser", exports = "isMeAccount",
            adaptation = WhatsAppAdaptation.ADAPTED)
    boolean isFromMe(Jid senderJid) {
        if (senderJid == null) {
            return false;
        }
        var senderUser = senderJid.toUserJid();
        var selfPn = store.jid().orElse(null);
        if (selfPn != null && senderUser.equals(selfPn.toUserJid())) {
            return true;
        }
        var selfLid = store.lid().orElse(null);
        return selfLid != null && senderUser.equals(selfLid.toUserJid());
    }
}
