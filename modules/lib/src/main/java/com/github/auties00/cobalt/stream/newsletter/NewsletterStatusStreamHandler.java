package com.github.auties00.cobalt.stream.newsletter;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfoBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;

/**
 * Handles inbound {@code <status>} stanzas pushed from newsletter
 * (channel) servers.
 *
 * @apiNote
 * Surfaces the newsletter-status delivery pipeline that powers the
 * WhatsApp Channels surface. Listeners registered on the client are
 * notified of every text or media post published on a channel the
 * current account follows; reaction and reaction-revoke events are
 * acknowledged but not materialised, mirroring
 * {@code WAWebHandleNewsletterStatus}'s short-circuit on
 * {@code StatusNewsletterReaction} / {@code StatusNewsletterReactionRevoke}.
 *
 * @implNote
 * This implementation decodes the {@code <plaintext>} child via
 * {@link MessageContainerSpec#decode(byte[])} on the dispatch thread;
 * decode failures are demoted to a debug log rather than thrown because
 * a single malformed channel post must not poison the stream. Cobalt
 * does not currently project admin revoke ({@code edit="8"}) stanzas
 * into synthetic protocol-revoke messages the way
 * {@code WAWebNewsletterStatusUtils.mapStatusRevokeToMsgData} does.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleNewsletterStatus")
@WhatsAppWebModule(moduleName = "WAWebNewsletterStatusUtils")
public final class NewsletterStatusStreamHandler implements SocketStream.Handler {
    /**
     * Logger used for debug-level diagnostics while parsing newsletter
     * status stanzas.
     *
     * @apiNote
     * Receives debug entries for skipped revoke stanzas, missing
     * plaintext payloads, and {@link MessageContainerSpec#decode} failures;
     * downstream code never relies on these messages.
     */
    private static final System.Logger LOGGER = System.getLogger(NewsletterStatusStreamHandler.class.getName());

    /**
     * Reference to the owning {@link WhatsAppClient} used to access the
     * store and broadcast new-message events to registered listeners.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a handler bound to the given {@link WhatsAppClient}.
     *
     * @apiNote
     * Invoked by the socket-stream wiring at client construction; user
     * code does not instantiate handlers directly.
     *
     * @param whatsapp the owning {@link WhatsAppClient} instance
     */
    public NewsletterStatusStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Decodes and stores the inbound newsletter post, then dispatches
     * an {@code onNewMessage} callback to every listener registered on
     * the store. Reaction and reaction-revoke variants are silently
     * dropped, and admin-revoke variants ({@code edit="8"}) are logged
     * and skipped pending higher-layer revoke modelling.
     *
     * @implNote
     * This implementation refuses to materialise admin-revoke stanzas
     * even though
     * {@code WAWebNewsletterStatusUtils.mapStatusRevokeToMsgData} would
     * synthesise a {@code MSG_TYPE.PROTOCOL} / {@code ProtocolRevoke}
     * message; the revoke is left for a future higher-layer pathway.
     * Stanzas missing a {@code from} attribute, a non-newsletter
     * {@code from}, a missing {@code id}, or an empty {@code plaintext}
     * are dropped before {@link MessageContainerSpec#decode(byte[])}
     * runs.
     */
    @Override
    public void handle(Node node) {
        var from = node.getAttributeAsJid("from", null);
        if (from == null || !from.hasNewsletterServer()) {
            return;
        }

        var id = node.getAttributeAsString("id", null);
        if (id == null) {
            return;
        }

        if (node.hasChild("reaction")) {
            return;
        }

        var edit = node.getAttributeAsString("edit", null);
        if ("8".equals(edit)) {
            // TODO: project admin revoke (edit="8") into a synthetic protocol
            //       message the way WAWebNewsletterStatusUtils.mapStatusRevokeToMsgData
            //       does, rather than dropping it here.
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Skipping newsletter status revoke for {0} from {1}", id, from);
            return;
        }

        var plaintext = node.getChild("plaintext")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (plaintext == null || plaintext.length == 0) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring newsletter status with no plaintext content for {0}", id);
            return;
        }

        var message = decodeMessage(id, plaintext);
        if (message == null) {
            return;
        }

        var serverId = node.getAttributeAsInt("server_id", 0);
        var timestamp = resolveTimestamp(node);
        var isSender = "true".equals(node.getAttributeAsString("is_sender", null));

        var key = new MessageKeyBuilder()
                .id(id)
                .parentJid(from)
                .fromMe(isSender)
                .build();

        var info = new NewsletterMessageInfoBuilder()
                .key(key)
                .serverId(serverId)
                .timestamp(timestamp)
                .message(message)
                .status(MessageStatus.DELIVERED)
                .build();

        storeMessage(from, info);
        notifyNewMessage(info);
    }

    /**
     * Decodes the raw plaintext payload carried by a newsletter status
     * stanza into a typed {@link MessageContainer}.
     *
     * @apiNote
     * Returns {@code null} rather than throwing so that a single
     * unparseable stanza cannot abort the dispatch loop.
     *
     * @implNote
     * This implementation logs the failure at {@code DEBUG} level and
     * suppresses the exception; the {@code <status>} stanza is treated
     * as if it had never arrived.
     *
     * @param id        the stanza identifier, included in the debug log
     *                  message for traceability
     * @param plaintext the raw protobuf bytes lifted from the
     *                  {@code <plaintext>} child node
     * @return the decoded {@link MessageContainer}, or {@code null}
     *         when the bytes cannot be parsed
     */
    private MessageContainer decodeMessage(String id, byte[] plaintext) {
        try {
            return MessageContainerSpec.decode(plaintext);
        } catch (Exception exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Failed to decode newsletter status {0}: {1}", id, exception.getMessage());
            return null;
        }
    }

    /**
     * Resolves the {@link Instant} represented by the stanza's {@code t}
     * attribute.
     *
     * @apiNote
     * Newsletter status stanzas carry their server-side publish time as
     * an epoch-second long; consumers downstream expect an
     * {@link Instant} or {@code null} when the attribute is absent.
     *
     * @param node the {@code <status>} stanza node
     * @return the parsed {@link Instant}, or {@code null} when the
     *         attribute is missing
     */
    private Instant resolveTimestamp(Node node) {
        var timestamp = node.getAttributeAsLong("t", (Long) null);
        return timestamp == null ? null : Instant.ofEpochSecond(timestamp);
    }

    /**
     * Appends the decoded post to the newsletter's message collection
     * and bumps the newsletter-level metadata.
     *
     * @apiNote
     * Lazily creates the {@code Newsletter} store entry if the channel
     * has never been seen on this device, mirroring the upstream
     * pattern in {@code WAWebHandleNewsletterStatus} where
     * {@code WAWebMessageQueue.onMessageQueue} is keyed by the channel
     * {@link Jid}.
     *
     * @implNote
     * This implementation increments the unread counter only for posts
     * the current account did not author (the {@code is_sender="true"}
     * attribute case is excluded), and updates the channel-level
     * timestamp to the post's timestamp regardless of authorship.
     *
     * @param newsletterJid the channel {@link Jid} that owns the post
     * @param info          the decoded {@link NewsletterMessageInfo} to
     *                      persist
     */
    private void storeMessage(Jid newsletterJid, NewsletterMessageInfo info) {
        var newsletter = whatsapp.store()
                .findNewsletterByJid(newsletterJid)
                .orElseGet(() -> whatsapp.store().addNewNewsletter(newsletterJid));
        newsletter.setTimestamp(info.timestamp().orElse(null));
        if (!info.key().fromMe()) {
            newsletter.setUnreadMessagesCount(newsletter.unreadMessagesCount() + 1);
        }
        newsletter.addMessage(info);
    }

    /**
     * Broadcasts an {@code onNewMessage} callback to every listener
     * registered on the store.
     *
     * @apiNote
     * Used to surface inbound channel posts on the public listener
     * surface so that application code can react to them.
     *
     * @implNote
     * This implementation dispatches each callback on its own virtual
     * thread so that a slow listener cannot block the socket-stream
     * dispatch loop or starve other listeners.
     *
     * @param info the decoded {@link MessageInfo} to publish
     */
    private void notifyNewMessage(MessageInfo info) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onNewMessage(whatsapp, info));
        }
    }
}
