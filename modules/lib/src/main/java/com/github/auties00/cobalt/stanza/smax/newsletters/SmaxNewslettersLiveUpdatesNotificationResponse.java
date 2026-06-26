package com.github.auties00.cobalt.stanza.smax.newsletters;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.smax.SmaxStanza;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the inbound live-updates notification pushed by the relay
 * to a client previously subscribed via
 * {@link SmaxNewslettersSubscribeToLiveUpdatesRequest}.
 * The receive pipeline parses the inbound stanza, projects
 * {@link #messages()} into the local newsletter store, then emits a
 * {@link SmaxNewslettersLiveUpdatesNotificationAcknowledgement}. The
 * acknowledgement must fire even when the delivery is rejected,
 * otherwise the relay re-pushes the same notification.
 */
@WhatsAppWebModule(moduleName = "WASmaxInNewslettersLiveUpdatesNotificationRequest")
@WhatsAppWebModule(moduleName = "WASmaxInNewslettersCommonNotificationMixin")
@WhatsAppWebModule(moduleName = "WASmaxInNewslettersNewsletterMessageResponsePayloadMixin")
public final class SmaxNewslettersLiveUpdatesNotificationResponse implements SmaxStanza.Response {
    /**
     * Holds the notification stanza id, echoed verbatim into the
     * {@link SmaxNewslettersLiveUpdatesNotificationAcknowledgement}.
     */
    private final String notificationId;

    /**
     * Holds the newsletter {@link Jid} that produced the notification.
     */
    private final Jid notificationFrom;

    /**
     * Holds the optional newsletter {@link Jid} echoed on the
     * {@code <messages>} payload.
     */
    private final Jid messagesJid;

    /**
     * Holds the optional unix-second timestamp echoed on the
     * {@code <messages>} payload.
     */
    private final Long messagesTimestamp;

    /**
     * Holds the list of newsletter messages carried in the live-updates
     * delta.
     */
    private final List<NewsletterMessage> messages;

    /**
     * Constructs a new inbound projection.
     * The {@code messagesJid} and {@code messagesTimestamp} are optional
     * because the relay only echoes them when the corresponding
     * attributes were present on the wire; {@code notificationId} and
     * {@code notificationFrom} are mandatory because the
     * {@link SmaxNewslettersLiveUpdatesNotificationAcknowledgement}
     * echoes them verbatim.
     *
     * @param notificationId    the notification id; never {@code null}
     * @param notificationFrom  the notification sender {@link Jid}; never {@code null}
     * @param messagesJid       the optional echoed {@link Jid}; may be {@code null}
     * @param messagesTimestamp the optional echoed timestamp; may be {@code null}
     * @param messages          the message entries; never {@code null} (empty allowed)
     * @throws NullPointerException if {@code notificationId} or {@code notificationFrom} is {@code null}
     */
    public SmaxNewslettersLiveUpdatesNotificationResponse(String notificationId, Jid notificationFrom,
                   Jid messagesJid, Long messagesTimestamp,
                   List<NewsletterMessage> messages) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId cannot be null");
        this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
        this.messagesJid = messagesJid;
        this.messagesTimestamp = messagesTimestamp;
        this.messages = List.copyOf(Objects.requireNonNullElse(messages, List.of()));
    }

    /**
     * Returns the notification id.
     *
     * @return the id; never {@code null}
     */
    public String notificationId() {
        return notificationId;
    }

    /**
     * Returns the notification sender {@link Jid}.
     *
     * @return the {@link Jid}; never {@code null}
     */
    public Jid notificationFrom() {
        return notificationFrom;
    }

    /**
     * Returns the optional echoed newsletter {@link Jid}.
     *
     * @return an {@link Optional} carrying the {@link Jid}, or empty when the relay omitted it
     */
    public Optional<Jid> messagesJid() {
        return Optional.ofNullable(messagesJid);
    }

    /**
     * Returns the optional echoed timestamp.
     *
     * @return an {@link Optional} carrying the unix-second timestamp, or empty when the relay omitted it
     */
    public Optional<Long> messagesTimestamp() {
        return Optional.ofNullable(messagesTimestamp);
    }

    /**
     * Returns the message entries.
     *
     * @return an unmodifiable {@link List} of entries; never {@code null}
     */
    public List<NewsletterMessage> messages() {
        return messages;
    }

    /**
     * Parses a notification projection from a {@code <notification/>}
     * {@link Stanza}.
     * Returns {@link Optional#empty()} when the description is not
     * {@code notification}, the {@code type} attribute is not
     * {@code "newsletter"}, the required {@code id} or {@code from}
     * attributes are missing, the {@code <live_updates>} or
     * {@code <messages>} envelope is absent, the {@code t} attribute on
     * {@code <messages>} is negative, or any nested {@code <message>}
     * fails its own {@link NewsletterMessage#of(Stanza)} parse.
     *
     * @param stanza the inbound notification stanza; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty on no-match
     * @throws NullPointerException if {@code stanza} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInNewslettersLiveUpdatesNotificationRequest",
            exports = "parseLiveUpdatesNotificationRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxNewslettersLiveUpdatesNotificationResponse> of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        if (!stanza.hasDescription("notification")) {
            return Optional.empty();
        }
        if (!stanza.hasAttribute("type", "newsletter")) {
            return Optional.empty();
        }
        var notificationId = stanza.getAttributeAsString("id").orElse(null);
        if (notificationId == null) {
            return Optional.empty();
        }
        var from = stanza.getAttributeAsJid("from").orElse(null);
        if (from == null) {
            return Optional.empty();
        }
        var liveUpdates = stanza.getChild("live_updates").orElse(null);
        if (liveUpdates == null) {
            return Optional.empty();
        }
        var messagesNode = liveUpdates.getChild("messages").orElse(null);
        if (messagesNode == null) {
            return Optional.empty();
        }
        var messagesJid = messagesNode.getAttributeAsJid("jid").orElse(null);
        Long timestamp = null;
        var tOpt = messagesNode.getAttributeAsLong("t");
        if (tOpt.isPresent()) {
            var tv = tOpt.getAsLong();
            if (tv < 0) {
                return Optional.empty();
            }
            timestamp = tv;
        }
        var entries = new ArrayList<NewsletterMessage>();
        for (var messageNode : messagesNode.getChildren("message")) {
            var entry = NewsletterMessage.of(messageNode).orElse(null);
            if (entry == null) {
                return Optional.empty();
            }
            entries.add(entry);
        }
        return Optional.of(new SmaxNewslettersLiveUpdatesNotificationResponse(notificationId, from, messagesJid, timestamp, entries));
    }

    /**
     * Compares two notifications for value equality on every field.
     *
     * @param obj the reference object to compare against
     * @return {@code true} when {@code obj} is a notification with equal field values
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxNewslettersLiveUpdatesNotificationResponse) obj;
        return Objects.equals(this.notificationId, that.notificationId)
                && Objects.equals(this.notificationFrom, that.notificationFrom)
                && Objects.equals(this.messagesJid, that.messagesJid)
                && Objects.equals(this.messagesTimestamp, that.messagesTimestamp)
                && Objects.equals(this.messages, that.messages);
    }

    /**
     * Returns the hash code derived from every field.
     *
     * @return the combined hash of every field
     */
    @Override
    public int hashCode() {
        return Objects.hash(notificationId, notificationFrom, messagesJid, messagesTimestamp, messages);
    }

    /**
     * Returns a debug representation including every field.
     *
     * @return a record-like rendering of this notification
     */
    @Override
    public String toString() {
        return "SmaxNewslettersLiveUpdatesNotificationResponse[notificationId=" + notificationId
                + ", notificationFrom=" + notificationFrom
                + ", messagesJid=" + messagesJid
                + ", messagesTimestamp=" + messagesTimestamp
                + ", messages=" + messages + ']';
    }

    /**
     * Represents one typed projection of a {@code <message>} entry
     * carried by a live-updates notification.
     *
     * @implNote This implementation declares its own message projection rather than reusing {@link SmaxNewslettersGetNewsletterMessagesResponse.NewsletterMessage}, even though the two share the wire shape and parser export name, to keep the live-updates payload self-contained and decouple the receive handler from the history-fetch type.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersNewsletterMessageHistoryWithAddOnsMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersNewsletterMessageHistoryMixin")
    public static final class NewsletterMessage {
        /**
         * Holds the optional client-supplied stanza id of the message.
         */
        private final String stanzaId;

        /**
         * Holds the server-assigned monotonic message id within the
         * newsletter.
         */
        private final long serverId;

        /**
         * Holds the optional unix-second timestamp of the message.
         */
        private final Long timestamp;

        /**
         * Holds whether the message was authored by the connected client.
         */
        private final boolean fromSelf;

        /**
         * Holds the underlying {@link Stanza} exposing the variable-shape
         * add-on children.
         */
        private final Stanza raw;

        /**
         * Constructs a new newsletter-message projection.
         * The {@code stanzaId} and {@code timestamp} are optional because
         * the relay only emits them when the message carries those
         * attributes on the wire.
         *
         * @param stanzaId  the optional stanza id; may be {@code null}
         * @param serverId  the server-assigned id
         * @param timestamp the optional unix-second timestamp; may be {@code null}
         * @param fromSelf  whether the message was authored by self
         * @param raw       the underlying {@link Stanza}; never {@code null}
         * @throws NullPointerException if {@code raw} is {@code null}
         */
        public NewsletterMessage(String stanzaId, long serverId, Long timestamp, boolean fromSelf, Stanza raw) {
            this.stanzaId = stanzaId;
            this.serverId = serverId;
            this.timestamp = timestamp;
            this.fromSelf = fromSelf;
            this.raw = Objects.requireNonNull(raw, "raw cannot be null");
        }

        /**
         * Returns the optional client-supplied stanza id.
         *
         * @return an {@link Optional} carrying the stanza id, or empty when the relay omitted it
         */
        public Optional<String> stanzaId() {
            return Optional.ofNullable(stanzaId);
        }

        /**
         * Returns the server-assigned message id.
         *
         * @return the server-assigned id
         */
        public long serverId() {
            return serverId;
        }

        /**
         * Returns the optional unix-second timestamp.
         *
         * @return an {@link Optional} carrying the timestamp, or empty when the relay omitted it
         */
        public Optional<Long> timestamp() {
            return Optional.ofNullable(timestamp);
        }

        /**
         * Returns whether the message was authored by the connected
         * client.
         *
         * @return {@code true} when {@code is_sender="true"} was present on the wire
         */
        public boolean fromSelf() {
            return fromSelf;
        }

        /**
         * Returns the underlying {@link Stanza}.
         *
         * @return the raw {@link Stanza} exposing the add-on children; never {@code null}
         */
        public Stanza raw() {
            return raw;
        }

        /**
         * Parses a {@link NewsletterMessage} from a {@code <message>}
         * {@link Stanza}.
         * Returns {@link Optional#empty()} when the description is not
         * {@code message}, the {@code server_id} attribute is missing or
         * outside the {@code [99, 2147476647]} range, or the optional
         * {@code t} attribute is negative.
         *
         * @param messageStanza the source {@link Stanza}; never {@code null}
         * @return an {@link Optional} carrying the parsed entry, or empty on no-match
         * @throws NullPointerException if {@code messageStanza} is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WASmaxInNewslettersNewsletterMessageHistoryWithAddOnsMixin",
                exports = "parseNewsletterMessageHistoryWithAddOnsMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<NewsletterMessage> of(Stanza messageStanza) {
            Objects.requireNonNull(messageStanza, "messageStanza cannot be null");
            if (!messageStanza.hasDescription("message")) {
                return Optional.empty();
            }
            var stanzaId = messageStanza.getAttributeAsString("id").orElse(null);
            var serverIdOpt = messageStanza.getAttributeAsLong("server_id");
            if (serverIdOpt.isEmpty()) {
                return Optional.empty();
            }
            var serverId = serverIdOpt.getAsLong();
            if (serverId < 99 || serverId > 2147476647L) {
                return Optional.empty();
            }
            Long timestamp = null;
            var tOpt = messageStanza.getAttributeAsLong("t");
            if (tOpt.isPresent()) {
                var tv = tOpt.getAsLong();
                if (tv < 0) {
                    return Optional.empty();
                }
                timestamp = tv;
            }
            var fromSelf = messageStanza.hasAttribute("is_sender", "true");
            return Optional.of(new NewsletterMessage(stanzaId, serverId, timestamp, fromSelf, messageStanza));
        }

        /**
         * Compares two entries for value equality on every field.
         *
         * @param obj the reference object to compare against
         * @return {@code true} when {@code obj} is a {@link NewsletterMessage} with equal field values
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (NewsletterMessage) obj;
            return this.serverId == that.serverId
                    && this.fromSelf == that.fromSelf
                    && Objects.equals(this.stanzaId, that.stanzaId)
                    && Objects.equals(this.timestamp, that.timestamp)
                    && Objects.equals(this.raw, that.raw);
        }

        /**
         * Returns the hash code derived from every field.
         *
         * @return the combined hash of every field
         */
        @Override
        public int hashCode() {
            return Objects.hash(stanzaId, serverId, timestamp, fromSelf, raw);
        }

        /**
         * Returns a debug representation including the typed fields.
         *
         * @return a record-like rendering of this entry, excluding the underlying {@link Stanza} for brevity
         */
        @Override
        public String toString() {
            return "SmaxNewslettersLiveUpdatesNotificationResponse.NewsletterMessage[stanzaId=" + stanzaId
                    + ", serverId=" + serverId
                    + ", timestamp=" + timestamp
                    + ", fromSelf=" + fromSelf + ']';
        }
    }
}
