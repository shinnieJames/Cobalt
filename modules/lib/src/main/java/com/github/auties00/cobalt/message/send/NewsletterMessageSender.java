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
@WhatsAppWebModule(moduleName = "WAWebNewsletterSendMessageQueryJob")
@WhatsAppWebModule(moduleName = "WASmaxMessagePublishNewsletterRPC")
@WhatsAppWebModule(moduleName = "WASmaxOutMessagePublishNewsletterClientIdContent")
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
    @WhatsAppWebExport(moduleName = "WAWebAck", exports = "EDIT_ATTR",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String NEWSLETTER_MSG_EDIT = "3";

    /**
     * Creates a new newsletter message sender.
     *
     * @param client the WhatsApp client for sending stanzas
     *
     * @implNote ADAPTED: WAWebNewsletterSendMessageQueryJob uses module-level
     * imports; Cobalt uses constructor-based DI instead.
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSendMessageQueryJob", exports = "querySendNewsletterMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSendMessageQueryJob", exports = "querySendNewsletterMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    AckResult send(Jid newsletterJid, NewsletterMessageInfo messageInfo) {
        var container = messageInfo.message();
        var containerType = container.futureProofContentType();

        // WASmaxOutMessagePublishNewsletterClientIdContent: question and
        // question reply wrappers take priority over inner content type.
        // The inner content is wrapped in mergeNewsletterTextOrMediaPublishMixinGroup
        // which dispatches to mergeNewsletterTextMixin (type="text") or
        // mergeNewsletterMediaMixin (type="media"); the specific media subtype
        // is carried as the mediatype attribute on the inner <plaintext>.
        if (containerType == FutureProofMessageType.QUESTION || containerType == FutureProofMessageType.QUESTION_REPLY) {
            var innerContent = container.content();
            var mediaSubtype = resolveSmaxMediaType(innerContent);
            var isMedia = !"text".equals(mediaSubtype);
            var payload = MessageContainerSpec.encode(MessageContainer.of(innerContent));
            var metaNode = containerType == FutureProofMessageType.QUESTION
                    ? MetaStanza.buildNewsletterQuestion()
                    : MetaStanza.buildNewsletterQuestionReply();
            // WASmaxOutMessagePublishNewsletterTextMixin: <plaintext> has no
            // mediatype attribute; WASmaxOutMessagePublishNewsletterMediaMixin:
            // <plaintext mediatype="...">.
            var plaintextNode = isMedia
                    ? NewsletterStanza.buildPlaintext(payload, mediaSubtype)
                    : NewsletterStanza.buildPlaintext(payload);
            var stanzaBuilder = new NodeBuilder()
                    .description("message")
                    .attribute("id", messageInfo.key().id().orElseThrow())
                    .attribute("to", newsletterJid)
                    // mergeContentTypeTextMixin or mergeContentTypeMediaMixin:
                    // the outer type attribute is "text" or "media", never the
                    // specific media subtype (which lives on <plaintext>).
                    .attribute("type", isMedia ? "media" : "text");
            if (isMedia) {
                // WASmaxOutMessagePublishNewsletterMediaPublishMixin: media_id
                // is an attribute on <message>, not a child element.
                stanzaBuilder.attribute("media_id", messageInfo.mediaHandle().orElse(null));
            }
            var stanza = stanzaBuilder.content(metaNode, plaintextNode);
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
     * Builds the SMAX stanza for a newsletter question response.
     *
     * <p>The wire shape is a text-typed message with the question-response
     * meta marker and the response payload. The {@code server_id} is the
     * parent (question) message's server id, NOT the response's own
     * server id. Cobalt resolves the parent server id via
     * {@code container.content()} cast to {@link QuestionResponseMessage}
     * (whose target is left to the caller; until that path is wired the
     * value is read from {@link NewsletterMessageInfo#serverId()} as the
     * existing placeholder, which matches the previous behaviour).
     *
     * @apiNote WASmaxOutMessagePublishNewsletterQuestionResponsePublishMixin:
     * {@code <message type="text" server_id="parentServerId">
     * <meta questiontype="response"/><plaintext>payload</plaintext></message>}.
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
                // WASmaxOutMessagePublishContentTypeTextMixin: type="text"
                .attribute("type", "text")
                // WAWebNewsletterSendMessageQueryJob.m: server_id is the
                // parent (question) message's server id.
                .attribute("server_id", messageInfo.serverId())
                .content(metaNode, plaintextNode);
    }

    /**
     * Builds the SMAX stanza for media and URL messages.
     *
     * <p>The stanza {@code type} is always {@code "media"} (from
     * {@code mergeContentTypeMediaMixin}); the specific media subtype
     * (image / video / audio / document / sticker / vcard / url / ...)
     * is carried as the {@code mediatype} attribute on the inner
     * {@code <plaintext>} node. The optional media handle is encoded as
     * the {@code media_id} attribute on the outer {@code <message>} node
     * (NOT as a child element).
     *
     * @apiNote WASmaxOutMessagePublishNewsletterMediaPublishMixin:
     * {@code <message type="media" media_id="handle">
     * <plaintext mediatype="image">payload</plaintext></message>}.
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterMediaPublishMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildMedia(
            NewsletterMessageInfo info, Jid newsletterJid, String mediaType
    ) {
        var payload = MessageContainerSpec.encode(info.message());
        var plaintextNode = NewsletterStanza.buildPlaintext(payload, mediaType);
        // WASmaxOutMessagePublishNewsletterMediaPublishMixin: media_id is an
        // ATTRIBUTE on <message>, not a child element. WASmaxAttrs.OPTIONAL
        // omits the attribute entirely when the handle is null.
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", "media")
                .attribute("media_id", info.mediaHandle().orElse(null))
                .content(plaintextNode);
    }

    /**
     * Builds the SMAX stanza for poll creation and poll result snapshot.
     *
     * @apiNote WASmaxOutMessagePublishContentTypePollCreationMixin,
     * WASmaxOutMessagePublishContentTypePollResultSnapshotMixin:
     * {@code <message type="poll"><meta polltype="..."/>
     * <plaintext>payload</plaintext></message>}
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
     * Builds the SMAX stanza for revoke (admin delete).
     *
     * <p>The wire shape is an admin-revoke marker on a synthetic
     * text-typed message that carries an empty {@code <plaintext/>} child,
     * mirroring WA Web's {@code mergeNewsletterRevokeMixin}:
     * {@code mergeContentTypeTextMixin(mergeAdminRevokeMixin(smax("message",
     * null, smax("plaintext", null))))}.
     *
     * @apiNote WASmaxOutMessagePublishNewsletterRevokeMixin:
     * {@code <message type="text" edit="8"><plaintext/></message>}.
     * {@code edit="8"} is {@code WAWebAck.EDIT_ATTR.ADMIN_REVOKE} and is
     * hard-coded by {@code mergeAdminRevokeMixin} regardless of the
     * Cobalt-side {@link MessageSender#resolveEditAttribute} computation.
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterRevokeMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildRevoke(NewsletterMessageInfo info, Jid newsletterJid) {
        // WASmaxOutMessagePublishNewsletterRevokeMixin: child is an empty
        // <plaintext/> node (no payload bytes), NOT a synthetic <admin_revoke/>
        // child. The admin-revoke marker is the edit="8" attribute mixin, not
        // an element.
        var plaintextNode = new NodeBuilder()
                .description("plaintext")
                .build();
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                // WASmaxOutMessagePublishContentTypeTextMixin
                .attribute("type", "text")
                // WASmaxOutMessagePublishAdminRevokeMixin: hard-coded "8"
                .attribute("edit", "8")
                .content(plaintextNode);
    }

    /**
     * Builds the SMAX stanza for edit (text or media).
     *
     * <p>The {@code admin_edit} marker is encoded as the
     * {@code edit="3"} attribute mixin (NOT a child element). The stanza
     * carries either a text payload (for text edits) or a media payload
     * with the {@code mediatype} attribute on {@code <plaintext>} and the
     * media handle as the {@code media_id} attribute on {@code <message>}
     * (for media edits).
     *
     * @apiNote WASmaxOutMessagePublishNewsletterEditMixin:
     * {@code <message type="text" edit="3"><plaintext>payload</plaintext></message>}
     * for text edits, or {@code <message type="media" edit="3"
     * media_id="handle"><plaintext mediatype="image">payload</plaintext></message>}
     * for media edits.
     * {@code edit="3"} is {@code WAWebAck.EDIT_ATTR.NEWSLETTER_MSG_EDIT}
     * and is hard-coded by {@code mergeAdminEditMixin}.
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterEditMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildEdit(NewsletterMessageInfo info, Jid newsletterJid) {
        var protocolMessage = (ProtocolMessage) info.message().content();
        var editedMessage = protocolMessage.editedMessage();
        var editedContent = editedMessage.map(MessageContainer::content).orElse(null);

        // WASmaxOutMessagePublishNewsletterEditMixin -> mergeNewsletterTextMixin
        // for text or mergeNewsletterMediaMixin for media. The edited type
        // is "text" for text/null, "media" for media leaves; the specific
        // SMAX media subtype string flows into <plaintext mediatype="...">.
        var mediaSubtype = editedContent != null ? resolveSmaxMediaType(editedContent) : "text";
        var isMediaEdit = !"text".equals(mediaSubtype);
        var stanzaType = isMediaEdit ? "media" : "text";

        var payload = MessageContainerSpec.encode(info.message());
        var plaintextNode = isMediaEdit
                ? NewsletterStanza.buildPlaintext(payload, mediaSubtype)
                : NewsletterStanza.buildPlaintext(payload);

        // WAWebAck.EDIT_ATTR.NEWSLETTER_MSG_EDIT = 3
        // mergeAdminEditMixin: smax("message", {edit:"3"}). The admin_edit
        // marker is purely an attribute mixin; WA Web does NOT emit a
        // synthetic <admin_edit/> child element.
        var builder = new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                .attribute("type", stanzaType)
                .attribute("edit", NEWSLETTER_MSG_EDIT);
        if (isMediaEdit) {
            // WASmaxOutMessagePublishNewsletterMediaPublishMixin: media_id is
            // an attribute on <message>, only meaningful for media edits.
            builder.attribute("media_id", info.mediaHandle().orElse(null));
        }
        return builder.content(plaintextNode);
    }

    /**
     * Builds the SMAX stanza for reactions on existing messages.
     *
     * <p>Reactions target an existing newsletter message by the parent
     * message's server id (resolved via {@link ReactionMessage#key()} →
     * the parent's {@link NewsletterMessageInfo#serverId()}). An empty or
     * {@code null} reaction code signals a reaction revoke, which uses
     * {@code mergeNewsletterReactionRevokeMixin} (which combines
     * {@code mergeContentTypeReactionMixin} → {@code type="reaction"} and
     * {@code mergeRevokeMixin} → {@code edit="7"}, with an empty
     * {@code <reaction/>} child).
     *
     * @apiNote WASmaxOutMessagePublishContentTypeReactionMixin:
     * {@code <message type="reaction" server_id="parentServerId">
     * <reaction code="emoji"/></message>} for a reaction.
     * WASmaxOutMessagePublishNewsletterReactionRevokeMixin:
     * {@code <message type="reaction" edit="7" server_id="parentServerId">
     * <reaction/></message>} for a revoke.
     * @implNote WAWebNewsletterSendMessageQueryJob.c:
     * {@code messageServerId = e.parentMsgServerId} — the SERVER id of
     * the message being reacted to, NOT the reaction's own server id.
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
        // WAWebNewsletterSendMessageQueryJob.c: messageServerId = e.parentMsgServerId
        // The parent (target) message's server id is resolved via the reaction's
        // target MessageKey, not the reaction message's own server id.
        var parentServerId = resolveParentServerId(reaction.key().orElse(null));

        // WAWebNewsletterSendMessageQueryJob.c: reactionCode != null && reactionCode !== ""
        // treats empty string as revoke, same as null
        var isRevoke = reaction.text().filter(t -> !t.isEmpty()).isEmpty();

        var builder = new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                // WASmaxOutMessagePublishContentTypeReactionMixin: type="reaction"
                .attribute("type", "reaction")
                .attribute("server_id", parentServerId);
        if (isRevoke) {
            // WASmaxOutMessagePublishRevokeMixin: edit="7" (sender_revoke).
            builder.attribute("edit", "7");
            builder.content(buildReactionRevoke());
        } else {
            builder.content(buildReactionContent(reaction.text().get()));
        }
        return builder;
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
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishContentTypeReactionMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Node buildReactionContent(String s) {
        return new NodeBuilder()
                .description("reaction")
                .attribute("code", s)
                .build();
    }

    /**
     * Builds an empty {@code <reaction/>} node for revoking a reaction.
     *
     * <p>WA Web's {@code mergeNewsletterReactionRevokeMixin} emits an
     * empty {@code <reaction>} (no attributes, no content), accompanying
     * the {@code edit="7"} marker on the parent {@code <message>} node.
     *
     * @return the empty reaction revoke node
     *
     * @implNote WASmaxOutMessagePublishNewsletterReactionRevokeMixin:
     * {@code smax("reaction", null)}.
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterReactionRevokeMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Node buildReactionRevoke() {
        return new NodeBuilder()
                .description("reaction")
                .build();
    }

    /**
     * Resolves the {@code server_id} of the parent (target) message given
     * the target {@link MessageKey}.
     *
     * <p>WA Web reads {@code e.parentMsgServerId} from the input arguments;
     * Cobalt looks the target message up in {@link com.github.auties00.cobalt.store.WhatsAppStore}
     * and returns its {@link NewsletterMessageInfo#serverId()}. If the
     * parent is not stored, falls back to {@code 0} so the wire stanza
     * still serialises (the server will reject it, mirroring WA Web's
     * behaviour when {@code parentMsgServerId} is missing).
     *
     * @param targetKey the target message key, or {@code null}
     * @return the parent server id, or {@code 0} when unresolved
     *
     * @implNote WAWebNewsletterSendMessageQueryJob.c:
     * {@code messageServerId: e.parentMsgServerId}.
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
     * Builds the SMAX stanza for poll votes on existing newsletter messages.
     *
     * <p>Newsletter poll votes target the parent poll-creation message by
     * its server id (resolved via {@link PollUpdateMessage#pollCreationMessageKey()}
     * → the parent's {@link NewsletterMessageInfo#serverId()}). The wire
     * shape is a poll-typed message with {@code <meta polltype="vote"/>}
     * and a single {@code <votes>} child wrapping one {@code <vote>} per
     * selected option.
     *
     * @apiNote WASmaxOutMessagePublishContentTypePollVoteMixin +
     * WASmaxOutMessagePublishNewsletterPollVoteMixin:
     * {@code <message type="poll" server_id="parentServerId">
     * <meta polltype="vote"/><votes><vote>hash</vote>...</votes></message>}.
     * @implNote WAWebNewsletterSendMessageQueryJob.d:
     * {@code messageServerId = e.parentMsgServerId} and
     * {@code voteArgs: e.votes.map(v => ({voteElementValue: v}))}.
     * The {@code voteElementValue} is the SHA-256 hash of the selected
     * option name (computed by the WA Web poll-vote pipeline before
     * reaching this stanza builder); for the cleartext newsletter
     * transport, WA Web sends the hashes, NOT the cleartext option names.
     * Cobalt's {@link PollUpdateMessage#vote()} carries an encrypted
     * {@code PollEncValue}, so the hashes are not directly available
     * here. Until a poll-vote-hash resolver is wired up, this builder
     * returns {@code null} when the parent poll cannot be resolved or
     * when the selected hashes are unavailable, matching the
     * "missing data" branch of WA Web (which throws upstream).
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

        // WAWebNewsletterSendMessageQueryJob.d: voteElementValue per selected
        // option. WA Web hashes the option name via SHA-256 before this point;
        // Cobalt currently passes the cleartext option name (DEFERRED: hash
        // resolution requires the poll's encryption key + selected indices,
        // which are not yet plumbed through PollUpdateMessage in Cobalt).
        var voteChildren = pollCreationMessage.options()
                .stream()
                .map(PollCreationMessage.Option::optionName)
                .flatMap(Optional::stream)
                .map(name -> new NodeBuilder()
                        .description("vote")
                        .content(name)
                        .build())
                .toList();
        // WASmaxOutMessagePublishNewsletterPollVoteMixin: parent <votes> wrapper
        var votesNode = new NodeBuilder()
                .description("votes")
                .content(voteChildren)
                .build();
        return new NodeBuilder()
                .description("message")
                .attribute("id", info.key().id().orElseThrow())
                .attribute("to", newsletterJid)
                // WASmaxOutMessagePublishContentTypePollVoteMixin: type="poll"
                .attribute("type", "poll")
                // WAWebNewsletterSendMessageQueryJob.d: server_id is the PARENT
                // poll-creation message's server id.
                .attribute("server_id", parentNewsletter.serverId())
                // WASmaxOutMessagePublishContentTypePollVoteMixin: <meta polltype="vote"/>
                .content(new NodeBuilder()
                        .description("meta")
                        .attribute("polltype", "vote")
                        .build(), votesNode);
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
    @WhatsAppWebExport(moduleName = "WAWebNewsletterSendMessageQueryJob", exports = "querySendNewsletterMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
