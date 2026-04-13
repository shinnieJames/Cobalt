package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.send.token.ReportingToken;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
import com.github.auties00.cobalt.model.message.security.EncReactionMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Builds the {@code <reporting>} stanza child node containing the
 * reporting token (franking tag) for message integrity verification.
 *
 * <p>Reporting tokens are only generated when:
 * <ul>
 *   <li>The {@code rt_sender_reporting_token_version} AB prop is &gt; 0</li>
 *   <li>The message type is compatible (not reaction, poll vote, or
 *       event response)</li>
 *   <li>The message has a messageSecret</li>
 * </ul>
 *
 * @implNote WAWebReportingTokenUtils.genReportingTokenBody: delegates to
 * {@code genReportingToken} to derive the key from messageSecret, compute
 * HMAC over the reporting token content, then wraps the result in
 * {@code <reporting><reporting_token v="...">token</reporting_token></reporting>}.
 * WAWebReportingTokenUtils.genReportingTokenBodyForStanza: wraps
 * {@code genReportingTokenBody} with MESSAGE_HISTORY_BUNDLE handling.
 * WAWebMessagingGatingUtils.isReportingTokenSendingEnabled: checks
 * {@code rt_sender_reporting_token_version > 0}.
 * WAWebMessagePluginGenerateReportingTokenContent.isMsgTypeReportingTokenCompatible:
 * excludes reactions, encrypted reactions, event responses, and poll votes.
 */
public final class ReportingStanza {
    /**
     * Logger for reporting token generation failures.
     *
     * @implNote WAWebReportingTokenUtils.genReportingTokenBody: logs
     * {@code "unexpected exception in generating reporting token body"}
     * on failure.
     */
    private static final System.Logger LOGGER = System.getLogger(ReportingStanza.class.getName());

    /**
     * The AB props service used to query the sender reporting token version.
     *
     * @implNote WAWebMessagingGatingUtils.getSenderReportingTokenVersion:
     * reads {@code rt_sender_reporting_token_version} from AB props.
     */
    private final ABPropsService abPropsService;

    /**
     * Creates a new reporting stanza builder.
     *
     * @param abPropsService the AB props service for version lookup
     * @throws NullPointerException if {@code abPropsService} is {@code null}
     *
     * @implNote ADAPTED: WAWebReportingTokenUtils: module-level functions
     * use {@code WAWebMessagingGatingUtils} which reads AB props;
     * Cobalt injects the AB props service via constructor.
     */
    public ReportingStanza(ABPropsService abPropsService) {
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
    }

    /**
     * Builds the {@code <reporting>} node for the given message.
     *
     * <p>Returns {@code null} if reporting tokens are disabled, the
     * message type is incompatible, or the message has no messageSecret.
     *
     * @param messageInfo the outgoing message
     * @param selfJid     the sender's user JID
     * @param remoteJid   the remote JID (recipient for 1:1, group JID
     *                    for groups, status JID for broadcasts)
     * @return the reporting node, or {@code null}
     *
     * @implNote WAWebReportingTokenUtils.genReportingTokenBody: calls
     * {@code genReportingToken(msg, proto)} and wraps in WAP nodes.
     * WAWebReportingTokenUtils.genReportingTokenBodyForStanza: delegates
     * to {@code genReportingTokenBody} for non-history-bundle messages.
     */
    public Node build(ChatMessageInfo messageInfo, Jid selfJid, Jid remoteJid) {
        // WAWebMessagingGatingUtils.isReportingTokenSendingEnabled:
        // rt_sender_reporting_token_version > 0
        int senderVersion = abPropsService.getInt(ABProp.RT_SENDER_REPORTING_TOKEN_VERSION);
        if (senderVersion <= 0) {
            return null;
        }

        // WAWebMessagePluginGenerateReportingTokenContent.isMsgTypeReportingTokenCompatible:
        // excludes REACTION, REACTION_ENC, EVENT_RESPONSE, POLL_UPDATE
        var message = messageInfo.message().content();
        if (!isMsgTypeCompatible(message)) {
            return null;
        }

        var messageSecret = messageInfo.messageSecret().orElse(null);
        if (messageSecret == null) {
            return null;
        }

        // TODO: Implement proper reporting token content extraction.
        //  WA Web uses WAWebReportingTokenContent.ReportingTokenContentCalculator
        //  which builds a sparse copy of the Message protobuf containing only
        //  the fields specified by WAWebReportingTokenConfig for the given version,
        //  then serializes that sparse copy. The config is a base64-encoded protobuf
        //  (REPORTING_TOKEN_CONFIG_BASE64) mapping field numbers to extraction rules.
        //  Currently we use the full serialized protobuf, which causes server-side
        //  HMAC verification to fail.
        var serializedProto = MessageContainerSpec.encode(messageInfo.message());

        var id = messageInfo.key().id();
        if(id.isEmpty()) {
            return null;
        }

        try {
            var reportingToken = ReportingToken.generate(
                    messageSecret,
                    id.get(),
                    selfJid.toUserJid(),
                    remoteJid.toUserJid(),
                    serializedProto,
                    senderVersion
            );
            if (reportingToken.isEmpty()) {
                return null;
            }

            var reportingBody = new NodeBuilder()
                    .description("reporting_token")
                    .attribute("v", String.valueOf(reportingToken.get().version()))
                    .content(reportingToken.get().token())
                    .build();
            return new NodeBuilder()
                    .description("reporting")
                    .content(reportingBody)
                    .build();
        } catch (GeneralSecurityException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to generate reporting token: {0}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks whether the message type is compatible with reporting tokens.
     *
     * @param message the message content to check
     * @return {@code true} if the message type supports reporting tokens
     *
     * @implNote WAWebMessagePluginGenerateReportingTokenContent.isMsgTypeReportingTokenCompatible:
     * returns {@code false} for REACTION, REACTION_ENC, EVENT_RESPONSE, POLL_UPDATE.
     */
    private static boolean isMsgTypeCompatible(Message message) {
        return switch (message) {
            case ReactionMessage _, PollUpdateMessage _, EncReactionMessage _, EncEventResponseMessage _ -> false;
            case null, default -> true;
        };
    }
}
