package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Objects;

/**
 * Base class for all message receivers, providing shared protobuf
 * decoding and self-account identification utilities.
 *
 * <p>Subclasses implement the path-specific processing logic:
 * {@link ChatMessageReceiver} for E2E-encrypted messages and
 * {@link NewsletterMessageReceiver} for plaintext newsletter messages.
 *
 * @param <T> the type of {@link MessageInfo} produced by this receiver
 *
 * @apiNote WAWebHandleMsg: E2E message processing.
 * WASmaxInMessageDeliverNewsletterRequest: newsletter message processing.
 */
abstract sealed class MessageReceiver<T extends MessageInfo>
        permits ChatMessageReceiver, NewsletterMessageReceiver {

    private static final System.Logger LOGGER = System.getLogger(MessageReceiver.class.getName());

    /**
     * The central session data repository.
     */
    final WhatsAppStore store;

    MessageReceiver(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Processes an incoming message node, producing the appropriate
     * {@link MessageInfo} subtype.
     *
     * @param node    the raw {@code <message>} node
     * @param fromJid the JID from the {@code from} attribute
     * @return the processed message info, or {@code null} for messages
     *         that should be silently acknowledged (e.g. unavailable)
     */
    abstract T receive(Node node, Jid fromJid);

    /**
     * Returns the JID of the currently logged-in device.
     *
     * @return the self JID
     * @throws IllegalStateException if not logged in
     */
    Jid requireSelfJid() {
        return store.jid().orElseThrow(() ->
                new IllegalStateException("Not logged in"));
    }

    /**
     * Decodes a raw protobuf byte array into a {@link MessageContainer}.
     *
     * <p>Returns {@code null} and logs a warning if decoding fails,
     * rather than throwing.  Callers that need to distinguish between
     * a missing protobuf and a malformed one should check the return
     * value explicitly.
     *
     * @param messageId the message ID for log context
     * @param plaintext the raw protobuf bytes
     * @return the decoded container, or {@code null} on failure
     *
     * @apiNote WAWebHandleMsgProcess.processDecryptedMessageProto:
     * decodes the protobuf after removing PKCS#7 padding.
     */
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
     * Convenience overload that extracts the sender JID from a parsed
     * stanza.
     *
     * @param stanza the parsed stanza
     * @return {@code true} if the sender matches the logged-in user
     */
    boolean isFromMe(MessageReceiveStanza stanza) {
        return isFromMe(stanza.senderJid());
    }

    /**
     * Determines whether the given stanza sender is the current user's
     * account.
     *
     * @param senderJid the sender JID to check
     * @return {@code true} if the sender matches the logged-in user
     *
     * @apiNote WAWebMsgProcessingApiUtils: {@code fromMe = isMeAccount(author)}
     */
    boolean isFromMe(Jid senderJid) {
        var selfJid = store.jid().orElse(null);
        if (selfJid == null) {
            return false;
        }
        return senderJid.toUserJid().equals(selfJid.toUserJid());
    }
}
