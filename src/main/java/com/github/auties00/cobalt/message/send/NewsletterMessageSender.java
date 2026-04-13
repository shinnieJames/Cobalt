package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.send.ack.AckParser;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.stanza.MetaStanza;
import com.github.auties00.cobalt.message.send.stanza.NewsletterStanza;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.FutureProofMessageType;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
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

import java.util.Optional;

/**
 * Sends messages to newsletters ({@code newsletter@newsletter}).
 *
 * <p>Newsletter messages are <b>not</b> end-to-end encrypted.  They are
 * sent as plaintext via the SMAX protocol, which uses a different stanza
 * structure from the Signal-encrypted path: the serialised protobuf is
 * wrapped in a {@code <plaintext>} child node, and the {@code type}
 * attribute reflects the content type rather than the generic stanza
 * type.
 *
 * <h3>Supported content types</h3>
 * <ul>
 *   <li><b>Text</b>: {@code <message type="text"><plaintext>...</plaintext></message>}</li>
 *   <li><b>Media</b>: {@code <message type="image|video|..."><plaintext>...</plaintext>
 *       <media_id>handle</media_id></message>}</li>
 *   <li><b>Poll creation</b>: {@code <message type="poll"><meta polltype="creation"/>
 *       <plaintext>...</plaintext></message>}</li>
 *   <li><b>Poll result</b>: {@code <message type="poll"><meta polltype="result_snapshot"/>
 *       <plaintext>...</plaintext></message>}</li>
 *   <li><b>Revoke</b>: {@code <message edit="7"><admin_revoke/></message>}</li>
 *   <li><b>Edit (text)</b>: {@code <message type="text"><admin_edit/>
 *       <plaintext>...</plaintext></message>}</li>
 *   <li><b>Edit (media)</b>: {@code <message type="..."><admin_edit/>
 *       <plaintext>...</plaintext></message>}</li>
 *   <li><b>Reaction</b>: {@code <message><reaction code="..."/></message>} (targets
 *       an existing server message)</li>
 *   <li><b>Poll vote</b>: {@code <message><poll_vote><vote>hash</vote>...</poll_vote>
 *       </message>} (targets an existing server message)</li>
 * </ul>
 *
 * @apiNote WAWebNewsletterSendMessageQueryJob.querySendNewsletterMessage:
 * dispatches to content-type-specific SMAX mixins per message type.
 * WASmaxMessagePublishNewsletterRPC.sendNewsletterRPC: sends via
 * {@code WAComms.sendSmaxStanza} (same wire transport as regular stanzas).
 * WASmaxOutMessagePublishNewsletterClientIdContent: routes to
 * newsletterText, newsletterMediaPublish, newsletterEdit,
 * newsletterRevoke, newsletterPollCreation, etc.
 */
final class NewsletterMessageSender extends MessageSender<NewsletterMessageInfo> {
    /**
     * Logger for newsletter message sending diagnostics.
     *
     * @implNote ADAPTED: WA Web uses WALogger; Cobalt uses {@link System.Logger}.
     */
    private static final System.Logger LOGGER = System.getLogger(NewsletterMessageSender.class.getName());

    /**
     * The edit attribute value for newsletter message edits.
     *
     * @implNote WAWebAck.EDIT_ATTR.NEWSLETTER_MSG_EDIT = 3,
     * distinct from MESSAGE_EDIT (1) and PIN_IN_CHAT (2).
     */
    private static final String NEWSLETTER_MSG_EDIT = "3";

    /**
     * Creates a new newsletter message sender.
     *
     * @param client the WhatsApp client for sending stanzas
     *
     * @implNote ADAPTED: WAWebNewsletterSendMessageQueryJob uses module-level
     * imports; Cobalt uses constructor-based DI instead.
     */
    NewsletterMessageSender(WhatsAppClient client) {
        super(client);
    }

    /**
     * Sends a message to a newsletter channel via the SMAX protocol.
     *
     * <p>Dispatches to content-type-specific stanza builders based on
     * the message type: text, media, poll, revoke, edit, reaction,
     * poll vote, and question response. Newsletter messages are NOT
     * end-to-end encrypted.
     *
     * @param newsletterJid the newsletter JID
     * @param messageInfo   the outgoing newsletter message
     * @return the server ack result
     *
     * @implNote WAWebNewsletterSendMessageQueryJob.querySendNewsletterMessage:
     * validates the newsletter JID, builds the content-type-specific mixin
     * arguments, and sends via WASmaxMessagePublishNewsletterRPC.sendNewsletterRPC.
     */
    @Override
    AckResult send(Jid newsletterJid, NewsletterMessageInfo messageInfo) {
        var container = messageInfo.message();
        var containerType = container.futureProofContentType();

        // WASmaxOutMessagePublishNewsletterClientIdContent: question and
        // question reply wrappers take priority over inner content type
        if (containerType == FutureProofMessageType.QUESTION || containerType == FutureProofMessageType.QUESTION_REPLY) {
            var innerContent = container.content();
            var innerType = resolveSmaxMediaType(innerContent);
            var payload = MessageContainerSpec.encode(MessageContainer.of(innerContent));
            var metaNode = containerType == FutureProofMessageType.QUESTION
                    ? MetaStanza.buildNewsletterQuestion()
                    : MetaStanza.buildNewsletterQuestionReply();
            var plaintextNode = NewsletterStanza.buildPlaintext(payload,
                    "text".equals(innerType) ? null : innerType);
            var mediaIdNode = NewsletterStanza.buildMediaId(messageInfo);
            var stanza = new NodeBuilder()
                    .description("message")
                    .attribute("id", messageInfo.key().id().orElseThrow())
                    .attribute("to", newsletterJid)
                    .attribute("type", innerType)
                    .content(metaNode, plaintextNode, mediaIdNode);
            var ackNode = client.sendNode(stanza);
            return AckParser.parse(ackNode);
        } else {
            var message = container.content();

            var stanza = switch (message) {
                // WASmaxOutMessagePublishNewsletterRevokeMixin
                case ProtocolMessage p when p.type().orElse(null) == ProtocolMessage.Type.REVOKE ->
                        buildRevoke(messageInfo, newsletterJid);

                // WASmaxOutMessagePublishNewsletterEditMixin (text edit)
                case ProtocolMessage p when p.type().orElse(null) == ProtocolMessage.Type.MESSAGE_EDIT ->
                        buildEdit(messageInfo, newsletterJid);

                // WASmaxOutMessagePublishContentTypePollCreationMixin
                case PollCreationMessage _ ->
                        buildPoll(messageInfo, newsletterJid, "creation");

                // WASmaxOutMessagePublishContentTypePollResultSnapshotMixin
                case PollResultSnapshotMessage _ ->
                        buildPoll(messageInfo, newsletterJid, "result_snapshot");

                // WASmaxOutMessagePublishContentTypeReactionMixin
                case ReactionMessage r ->
                        buildReaction(messageInfo, newsletterJid, r);

                // WASmaxOutMessagePublishContentTypePollVoteMixin
                case PollUpdateMessage p ->
                        buildPollVote(messageInfo, newsletterJid, p);

                // WASmaxOutMessagePublishNewsletterQuestionResponsePublishMixin
                case QuestionResponseMessage _ -> buildQuestionResponse(messageInfo, newsletterJid, container);

                // WASmaxOutMessagePublishNewsletterTextMixin
                case ExtendedTextMessage t when t.matchedText().isEmpty() ->
                        buildText(messageInfo, newsletterJid);

                // WASmaxOutMessagePublishNewsletterMediaPublishMixin:
                // URL messages and all other media types
                case MediaMessage _ -> buildMedia(messageInfo, newsletterJid, resolveSmaxMediaType(message));

                default -> throw new WhatsAppMessageException.Send.Unknown("Invalid message type");
            };

            var ackNode = client.sendNode(stanza);
            return AckParser.parse(ackNode);
        }
    }

    /**
     * Builds the SMAX stanza for plain text messages.
     *
     * @apiNote WASmaxOutMessagePublishNewsletterTextMixin:
     * {@code <message type="text"><plaintext>payload</plaintext></message>}
     */
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
     * Builds the SMAX stanza for question response.
     *
     * @apiNote WASmaxOutMessagePublishNewsletterQuestionResponsePublishMixin:
     * {@code <message server_id="..."><meta questiontype="response"/>
     * <plaintext>payload</plaintext></message>}
     */
    private static NodeBuilder buildQuestionResponse(NewsletterMessageInfo messageInfo, Jid newsletterJid, MessageContainer container) {
        var payload = MessageContainerSpec.encode(container);
        var metaNode = MetaStanza.buildNewsletterQuestionResponse();
        var plaintextNode = NewsletterStanza.buildPlaintext(payload);
        return new NodeBuilder()
                .description("message")
                .attribute("id", messageInfo.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("server_id", String.valueOf(messageInfo.serverId()))
                .content(metaNode, plaintextNode);
    }

    /**
     * Builds the SMAX stanza for media and URL messages.
     *
     * <p>Includes the {@code <media_id>} child when a media handle is
     * available (set by the upload step before sending).
     *
     * @apiNote WASmaxOutMessagePublishNewsletterMediaPublishMixin:
     * {@code <message type="..."><plaintext>payload</plaintext>
     * <media_id>handle</media_id></message>}
     */
    private NodeBuilder buildMedia(
            NewsletterMessageInfo info, Jid newsletterJid, String mediaType
    ) {
        var payload = MessageContainerSpec.encode(info.message());
        var plaintextNode = NewsletterStanza.buildPlaintext(payload, mediaType);
        var mediaIdNode = NewsletterStanza.buildMediaId(info);
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", mediaType)
                .content(plaintextNode, mediaIdNode);
    }

    /**
     * Builds the SMAX stanza for poll creation and poll result snapshot.
     *
     * @apiNote WASmaxOutMessagePublishContentTypePollCreationMixin,
     * WASmaxOutMessagePublishContentTypePollResultSnapshotMixin:
     * {@code <message type="poll"><meta polltype="..."/>
     * <plaintext>payload</plaintext></message>}
     */
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
     * Builds the SMAX stanza for revoke (admin delete).
     *
     * @apiNote WASmaxOutMessagePublishNewsletterRevokeMixin:
     * {@code <message edit="7"><admin_revoke/></message>}
     */
    private NodeBuilder buildRevoke(NewsletterMessageInfo info, Jid newsletterJid) {
        var adminRevokeNode = new NodeBuilder()
                .description("admin_revoke")
                .build();
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("edit", resolveEditAttribute(info.message()))
                .content(adminRevokeNode);
    }

    /**
     * Builds the SMAX stanza for edit (text or media).
     *
     * <p>The edit stanza includes {@code <admin_edit/>} alongside the
     * new payload.  The stanza type is determined by the edited content.
     *
     * @apiNote WASmaxOutMessagePublishNewsletterEditMixin:
     * {@code <message edit="1"><admin_edit/>
     * <plaintext>payload</plaintext></message>}
     */
    private NodeBuilder buildEdit(NewsletterMessageInfo info, Jid newsletterJid) {
        var protocolMessage = (ProtocolMessage) info.message().content();
        var editedMessage = protocolMessage.editedMessage();
        var editedContent = editedMessage.map(MessageContainer::content).orElse(null);

        var payload = MessageContainerSpec.encode(info.message());
        var adminEditNode = new NodeBuilder()
                .description("admin_edit")
                .build();
        var plaintextNode = NewsletterStanza.buildPlaintext(payload);

        // WASmaxOutMessagePublishNewsletterEditMixin: type is resolved
        // from the edited content (text for text edits, media type for
        // media edits)
        var type = editedContent != null ? resolveSmaxMediaType(editedContent) : "text";

        // WAWebAck.EDIT_ATTR.NEWSLETTER_MSG_EDIT = 3
        // Newsletter edits use "3", distinct from regular MESSAGE_EDIT ("1")
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", type)
                .attribute("edit", NEWSLETTER_MSG_EDIT)
                .content(adminEditNode, plaintextNode, NewsletterStanza.buildMediaId(info));
    }

    /**
     * Builds the SMAX stanza for reactions on existing messages.
     *
     * <p>Reactions target an existing server message by its server ID.
     * An empty or null reaction code signals a reaction revoke.
     *
     * @apiNote WASmaxOutMessagePublishContentTypeReactionMixin:
     * {@code <message server_id="..."><reaction code="emoji"/></message>}
     * or {@code <message server_id="..."><reaction_revoke/></message>}
     */
    private NodeBuilder buildReaction(
            NewsletterMessageInfo info, Jid newsletterJid, ReactionMessage reaction
    ) {

        // WAWebNewsletterSendMessageQueryJob.c: reactionCode != null && reactionCode !== ""
        // treats empty string as revoke, same as null
        Node reactionNode = reaction.text()
                .filter(t -> !t.isEmpty())
                .map(this::buildReactionContent)
                .orElseGet(this::buildReactionRevoke);

        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("server_id", info.serverId())
                .content(reactionNode);
    }

    /**
     * Builds a {@code <reaction code="...">} node for a non-empty reaction.
     *
     * @param s the reaction emoji code
     * @return the reaction node
     *
     * @implNote WAWebNewsletterSendMessageQueryJob.c:
     * {@code {newsletterReaction: {reactionCode: t}}}.
     */
    private Node buildReactionContent(String s) {
        return new NodeBuilder()
                .description("reaction")
                .attribute("code", s)
                .build();
    }

    /**
     * Builds a {@code <reaction_revoke/>} node for revoking a reaction.
     *
     * @return the reaction revoke node
     *
     * @implNote WAWebNewsletterSendMessageQueryJob.c:
     * {@code {isNewsletterReactionRevoke: true}}.
     */
    private Node buildReactionRevoke() {
        return new NodeBuilder()
                .description("reaction_revoke")
                .build();
    }

    /**
     * Builds the SMAX stanza for poll votes on existing messages.
     *
     * @apiNote WASmaxOutMessagePublishContentTypePollVoteMixin:
     * {@code <message server_id="..."><poll_vote>
     * <vote>hash</vote>...</poll_vote></message>}
     */
    private NodeBuilder buildPollVote(
            NewsletterMessageInfo info, Jid newsletterJid, PollUpdateMessage pollUpdate
    ) {
        // WASmaxOutMessagePublishNewsletterPollVoteMixin: each vote
        // element contains the option name as its value
        var pollKey = pollUpdate.pollCreationMessageKey();
        if(pollKey.isEmpty()) {
            return null;
        }

        var pollMessage = store.findMessageByKey(pollKey.get());
        if(pollMessage.isEmpty() || !(pollMessage.get().message().content() instanceof PollCreationMessage pollCreationMessage)) {
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
        var pollVoteNode = new NodeBuilder()
                .description("poll_vote")
                .content(voteChildren)
                .build();
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("server_id", String.valueOf(info.serverId()))
                .content(pollVoteNode);
    }

    /**
     * Resolves the SMAX content type attribute from the message.
     *
     * <p>Unlike {@link #resolveStanzaType} which returns generic stanza
     * types (text/media/reaction/poll/event), this returns the specific
     * media type string used in the SMAX {@code type} attribute.
     *
     * @apiNote WAWebNewsletterSendMessageQueryJob: uses the media type
     * string directly as the SMAX {@code type} attribute.
     */
    private static String resolveSmaxMediaType(Message message) {
        return switch (message) {
            case ImageMessage _ -> "image";
            case VideoMessage v -> v.gifPlayback() ? "gif" : "video";
            case AudioMessage a -> a.ptt() ? "ptt" : "audio";
            case DocumentMessage _ -> "document";
            case StickerMessage _ -> "sticker";
            // WAWebNewsletterSendMessageQueryJob.p: sticker_pack type
            case StickerPackMessage _ -> "sticker_pack";
            case ContactMessage _ -> "vcard";
            case ExtendedTextMessage t when t.matchedText().isPresent() -> "url";
            default -> "text";
        };
    }
}
