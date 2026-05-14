package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.send.ack.AckParser;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.stanza.MetaStanza;
import com.github.auties00.cobalt.message.send.stanza.NewsletterStanza;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.FutureProofMessageType;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.contact.ContactMessage;
import com.github.auties00.cobalt.model.message.media.*;
import com.github.auties00.cobalt.model.message.poll.PollCreationMessage;
import com.github.auties00.cobalt.model.message.poll.PollResultSnapshotMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.QuestionResponseMessage;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;

import java.util.Optional;

/**
 * Sends messages to newsletter channels.
 *
 * <p>Newsletter messages are not end-to-end encrypted. They are sent over
 * the SMAX transport: the serialised protobuf payload is wrapped in a
 * {@code <plaintext>} child, and the stanza {@code type} attribute reflects
 * the content classification (text, media, poll, reaction). Each method on
 * this sender builds the wire shape for one specific content kind.
 */
@WhatsAppWebModule(moduleName = "WAWebNewsletterSendMessageQueryJob")
@WhatsAppWebModule(moduleName = "WASmaxMessagePublishNewsletterRPC")
@WhatsAppWebModule(moduleName = "WASmaxOutMessagePublishNewsletterClientIdContent")
final class NewsletterMessageSender extends MessageSender<NewsletterMessageInfo> {
    /**
     * Holds the logger used for newsletter-message diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(NewsletterMessageSender.class.getName());

    /**
     * Holds the {@code edit} attribute value used for newsletter message edits,
     * distinct from the regular message-edit and pin-in-chat values.
     */
    @WhatsAppWebExport(moduleName = "WAWebAck", exports = "EDIT_ATTR",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String NEWSLETTER_MSG_EDIT = "3";

    /**
     * Constructs a newsletter sender bound to the given client.
     *
     * @param client         the WhatsApp client used to dispatch stanzas
     * @param abPropsService the AB-props service consulted by the base sender
     * @param wamService     the WAM telemetry service shared with the base sender
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSendMessageQueryJob", exports = "querySendNewsletterMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    NewsletterMessageSender(WhatsAppClient client, ABPropsService abPropsService, WamService wamService) {
        super(client, abPropsService, wamService);
    }

    /**
     * Sends the given message to the specified newsletter, dispatching to the
     * content-type-specific stanza builder based on the message payload.
     *
     * @param newsletterJid the newsletter JID
     * @param messageInfo   the outgoing newsletter message
     * @return the server ack result
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSendMessageQueryJob", exports = "querySendNewsletterMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    AckResult send(Jid newsletterJid, NewsletterMessageInfo messageInfo) {
        var container = messageInfo.message();
        var containerType = container.futureProofContentType();

        if (containerType == FutureProofMessageType.QUESTION || containerType == FutureProofMessageType.QUESTION_REPLY) {
            var innerContent = container.content();
            var mediaSubtype = resolveSmaxMediaType(innerContent);
            var isMedia = !"text".equals(mediaSubtype);
            var payload = MessageContainerSpec.encode(MessageContainer.of(innerContent));
            var metaNode = containerType == FutureProofMessageType.QUESTION
                    ? MetaStanza.buildNewsletterQuestion()
                    : MetaStanza.buildNewsletterQuestionReply();
            var plaintextNode = isMedia
                    ? NewsletterStanza.buildPlaintext(payload, mediaSubtype)
                    : NewsletterStanza.buildPlaintext(payload);
            var stanzaBuilder = new NodeBuilder()
                    .description("message")
                    .attribute("id", messageInfo.key().id().orElseThrow())
                    .attribute("to", newsletterJid)
                    .attribute("type", isMedia ? "media" : "text");
            if (isMedia) {
                stanzaBuilder.attribute("media_id", messageInfo.mediaHandle().orElse(null));
            }
            var stanza = stanzaBuilder.content(metaNode, plaintextNode);
            var ackNode = client.sendNode(stanza);
            return AckParser.parse(ackNode);
        } else {
            var message = container.content();

            var stanza = switch (message) {
                case ProtocolMessage p when p.type().orElse(null) == ProtocolMessage.Type.REVOKE ->
                        buildRevoke(messageInfo, newsletterJid);

                case ProtocolMessage p when p.type().orElse(null) == ProtocolMessage.Type.MESSAGE_EDIT ->
                        buildEdit(messageInfo, newsletterJid);

                case PollCreationMessage _ ->
                        buildPoll(messageInfo, newsletterJid, "creation");

                case PollResultSnapshotMessage _ ->
                        buildPoll(messageInfo, newsletterJid, "result_snapshot");

                case ReactionMessage r ->
                        buildReaction(messageInfo, newsletterJid, r);

                case PollUpdateMessage p ->
                        buildPollVote(messageInfo, newsletterJid, p);

                case QuestionResponseMessage _ -> buildQuestionResponse(messageInfo, newsletterJid, container);

                case ExtendedTextMessage t when t.matchedText().isEmpty() ->
                        buildText(messageInfo, newsletterJid);

                case MediaMessage _ -> buildMedia(messageInfo, newsletterJid, resolveSmaxMediaType(message));

                default -> throw new WhatsAppMessageException.Send.Unknown("Invalid message type");
            };

            var ackNode = client.sendNode(stanza);
            return AckParser.parse(ackNode);
        }
    }

    /**
     * Builds the SMAX stanza for a plain-text newsletter message.
     *
     * @param info          the outgoing newsletter message
     * @param newsletterJid the newsletter JID
     * @return the stanza builder
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterTextMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildText(NewsletterMessageInfo info, Jid newsletterJid) {
        var payload = MessageContainerSpec.encode(info.message());
        var plaintextNode = NewsletterStanza.buildPlaintext(payload);
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", "text")
                .content(plaintextNode);
    }

    /**
     * Builds the SMAX stanza for a newsletter question-response message.
     * The {@code server_id} attribute carries the parent question's server id.
     *
     * @param messageInfo   the outgoing newsletter message
     * @param newsletterJid the newsletter JID
     * @param container     the question-response container
     * @return the stanza builder
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterQuestionResponsePublishMixin",
            exports = "applyMixin", adaptation = WhatsAppAdaptation.DIRECT)
    private static NodeBuilder buildQuestionResponse(NewsletterMessageInfo messageInfo, Jid newsletterJid, MessageContainer container) {
        var payload = MessageContainerSpec.encode(container);
        var metaNode = MetaStanza.buildNewsletterQuestionResponse();
        var plaintextNode = NewsletterStanza.buildPlaintext(payload);
        return new NodeBuilder()
                .description("message")
                .attribute("id", messageInfo.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", "text")
                .attribute("server_id", messageInfo.serverId())
                .content(metaNode, plaintextNode);
    }

    /**
     * Builds the SMAX stanza for a media or URL newsletter message. The
     * stanza {@code type} is always {@code "media"}; the specific subtype is
     * carried by the {@code mediatype} attribute on the inner
     * {@code <plaintext>} node, and the optional media handle is written into
     * the {@code media_id} attribute on the outer {@code <message>}.
     *
     * @param info          the outgoing newsletter message
     * @param newsletterJid the newsletter JID
     * @param mediaType     the SMAX media-subtype string
     * @return the stanza builder
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterMediaPublishMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildMedia(
            NewsletterMessageInfo info, Jid newsletterJid, String mediaType
    ) {
        var payload = MessageContainerSpec.encode(info.message());
        var plaintextNode = NewsletterStanza.buildPlaintext(payload, mediaType);
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", "media")
                .attribute("media_id", info.mediaHandle().orElse(null))
                .content(plaintextNode);
    }

    /**
     * Builds the SMAX stanza for a poll-creation or poll-result-snapshot
     * newsletter message.
     *
     * @param info          the outgoing newsletter message
     * @param newsletterJid the newsletter JID
     * @param polltype      the poll type marker ({@code "creation"} or
     *                      {@code "result_snapshot"})
     * @return the stanza builder
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishContentTypePollCreationMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishContentTypePollResultSnapshotMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildPoll(
            NewsletterMessageInfo info, Jid newsletterJid, String polltype
    ) {
        var payload = MessageContainerSpec.encode(info.message());
        var metaNode = new NodeBuilder()
                .description("meta")
                .attribute("polltype", polltype)
                .build();
        var plaintextNode = NewsletterStanza.buildPlaintext(payload);
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", "poll")
                .content(metaNode, plaintextNode);
    }

    /**
     * Builds the SMAX stanza for an admin revoke. The stanza carries
     * {@code edit="8"} (hard-coded by {@code mergeAdminRevokeMixin} regardless
     * of the resolved edit attribute) and a single empty {@code <plaintext/>}
     * child.
     *
     * @param info          the outgoing newsletter message
     * @param newsletterJid the newsletter JID
     * @return the stanza builder
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterRevokeMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildRevoke(NewsletterMessageInfo info, Jid newsletterJid) {
        var plaintextNode = new NodeBuilder()
                .description("plaintext")
                .build();
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", "text")
                .attribute("edit", "8")
                .content(plaintextNode);
    }

    /**
     * Builds the SMAX stanza for a newsletter text or media edit. The edit
     * marker is the hard-coded {@code edit="3"} attribute. Media edits also
     * carry the inner {@code mediatype} attribute on {@code <plaintext>} and
     * the {@code media_id} attribute on {@code <message>}.
     *
     * @param info          the outgoing newsletter message
     * @param newsletterJid the newsletter JID
     * @return the stanza builder
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterEditMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildEdit(NewsletterMessageInfo info, Jid newsletterJid) {
        var protocolMessage = (ProtocolMessage) info.message().content();
        var editedMessage = protocolMessage.editedMessage();
        var editedContent = editedMessage.map(MessageContainer::content).orElse(null);

        var mediaSubtype = editedContent != null ? resolveSmaxMediaType(editedContent) : "text";
        var isMediaEdit = !"text".equals(mediaSubtype);
        var stanzaType = isMediaEdit ? "media" : "text";

        var payload = MessageContainerSpec.encode(info.message());
        var plaintextNode = isMediaEdit
                ? NewsletterStanza.buildPlaintext(payload, mediaSubtype)
                : NewsletterStanza.buildPlaintext(payload);

        var builder = new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", stanzaType)
                .attribute("edit", NEWSLETTER_MSG_EDIT);
        if (isMediaEdit) {
            builder.attribute("media_id", info.mediaHandle().orElse(null));
        }
        return builder.content(plaintextNode);
    }

    /**
     * Builds the SMAX stanza for a reaction or reaction-revoke on an existing
     * newsletter message. The {@code server_id} attribute carries the parent
     * message's server id; an empty or {@code null} reaction code triggers
     * the revoke shape with {@code edit="7"} and an empty
     * {@code <reaction/>} child.
     *
     * @param info          the outgoing newsletter message
     * @param newsletterJid the newsletter JID
     * @param reaction      the reaction payload
     * @return the stanza builder
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishContentTypeReactionMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterReactionMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterReactionRevokeMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildReaction(
            NewsletterMessageInfo info, Jid newsletterJid, ReactionMessage reaction
    ) {
        var parentServerId = resolveParentServerId(reaction.key().orElse(null));

        var isRevoke = reaction.text().filter(t -> !t.isEmpty()).isEmpty();

        var builder = new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", "reaction")
                .attribute("server_id", parentServerId);
        if (isRevoke) {
            builder.attribute("edit", "7");
            builder.content(buildReactionRevoke());
        } else {
            builder.content(buildReactionContent(reaction.text().get()));
        }
        return builder;
    }

    /**
     * Builds a {@code <reaction code="..."/>} node carrying the given emoji code.
     *
     * @param s the reaction emoji code
     * @return the reaction node
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishContentTypeReactionMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Node buildReactionContent(String s) {
        return new NodeBuilder()
                .description("reaction")
                .attribute("code", s)
                .build();
    }

    /**
     * Builds an empty {@code <reaction/>} child node accompanying the
     * {@code edit="7"} marker for a reaction revoke.
     *
     * @return the empty reaction-revoke node
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterReactionRevokeMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Node buildReactionRevoke() {
        return new NodeBuilder()
                .description("reaction")
                .build();
    }

    /**
     * Resolves the {@code server_id} of the parent (target) message identified
     * by the given key. Returns {@code 0} when the parent message cannot be
     * found in the store.
     *
     * @param targetKey the target message key, or {@code null}
     * @return the parent server id, or {@code 0} when unresolved
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSendMessageQueryJob", exports = "querySendNewsletterMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private int resolveParentServerId(MessageKey targetKey) {
        if (targetKey == null) {
            return 0;
        }
        return store.findMessageByKey(targetKey)
                .filter(msg -> msg instanceof NewsletterMessageInfo)
                .map(msg -> ((NewsletterMessageInfo) msg).serverId())
                .orElse(0);
    }

    /**
     * Builds the SMAX stanza for a poll vote on an existing newsletter poll.
     * The {@code server_id} attribute carries the parent poll-creation
     * message's server id, and a {@code <votes>} child wraps one
     * {@code <vote>} per selected option.
     *
     * @param info          the outgoing newsletter message
     * @param newsletterJid the newsletter JID
     * @param pollUpdate    the poll-update payload
     * @return the stanza builder, or {@code null} when the parent poll cannot
     *         be resolved
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishContentTypePollVoteMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterPollVoteMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private NodeBuilder buildPollVote(
            NewsletterMessageInfo info, Jid newsletterJid, PollUpdateMessage pollUpdate
    ) {
        var pollKey = pollUpdate.pollCreationMessageKey();
        if (pollKey.isEmpty()) {
            return null;
        }

        var parentMessage = store.findMessageByKey(pollKey.get());
        if (parentMessage.isEmpty()
                || !(parentMessage.get() instanceof NewsletterMessageInfo parentNewsletter)
                || !(parentNewsletter.message().content() instanceof PollCreationMessage pollCreationMessage)) {
            return null;
        }

        var voteChildren = pollCreationMessage.options()
                .stream()
                .map(PollCreationMessage.Option::optionName)
                .flatMap(Optional::stream)
                .map(name -> new NodeBuilder()
                        .description("vote")
                        .content(name)
                        .build())
                .toList();
        var votesNode = new NodeBuilder()
                .description("votes")
                .content(voteChildren)
                .build();
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", "poll")
                .attribute("server_id", parentNewsletter.serverId())
                .content(new NodeBuilder()
                        .description("meta")
                        .attribute("polltype", "vote")
                        .build(), votesNode);
    }

    /**
     * Resolves the SMAX media-subtype string for the given message. Unlike
     * {@link #resolveStanzaType}, which returns generic stanza types, this
     * returns the specific media classification flowed into the
     * {@code mediatype} attribute.
     *
     * @param message the newsletter message payload
     * @return the SMAX media subtype, or {@code "text"} when no media classifies
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSendMessageQueryJob", exports = "querySendNewsletterMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String resolveSmaxMediaType(Message message) {
        return switch (message) {
            case ImageMessage _ -> "image";
            case VideoMessage v -> v.gifPlayback() ? "gif" : "video";
            case AudioMessage a -> a.ptt() ? "ptt" : "audio";
            case DocumentMessage _ -> "document";
            case StickerMessage _ -> "sticker";
            case StickerPackMessage _ -> "sticker_pack";
            case ContactMessage _ -> "vcard";
            case ExtendedTextMessage t when t.matchedText().isPresent() -> "url";
            default -> "text";
        };
    }
}
