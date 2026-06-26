package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.send.token.ReportingToken;
import com.github.auties00.cobalt.message.send.token.ReportingTokenContent;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
import com.github.auties00.cobalt.model.message.security.EncReactionMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Builds the optional {@code <reporting>} child of an outgoing {@code <message>} stanza carrying the franking-tag
 * reporting token used for end-to-end message integrity reporting.
 * <p>
 * Composed by {@link ChatFanoutStanza} and {@link GroupSkmsgFanoutStanza}. The {@code <reporting>} child carries a
 * nested {@code <reporting_token v="N">} whose HMAC-SHA-256 ties the sender, recipient, stanza id, and a sparse copy of
 * the message protobuf together so the recipient can later prove authorship without revealing the message body. Gated by
 * the {@link ABProp#RT_SENDER_REPORTING_TOKEN_VERSION} version prop, the message-type compatibility filter
 * ({@link #isMsgTypeCompatible(Message)}), and the presence of a {@link ChatMessageInfo#messageSecret()}.
 */
@WhatsAppWebModule(moduleName = "WAWebReportingTokenUtils")
@WhatsAppWebModule(moduleName = "WAWebMessagingGatingUtils")
@WhatsAppWebModule(moduleName = "WAWebMessagePluginGenerateReportingTokenContent")
public final class ReportingStanza {
    /**
     * Logs reporting-token generation failures.
     */
    private static final System.Logger LOGGER = System.getLogger(ReportingStanza.class.getName());

    /**
     * Holds the AB-props service consulted for the sender reporting token version.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs a stanza builder bound to the given AB-props service.
     * <p>
     * The builder is otherwise stateless.
     *
     * @param abPropsService the {@link ABPropsService}
     * @throws NullPointerException if {@code abPropsService} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebReportingTokenUtils", exports = "genReportingTokenBody",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public ReportingStanza(ABPropsService abPropsService) {
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
    }

    /**
     * Builds the {@code <reporting>} child for the given outgoing message.
     * <p>
     * Returns {@code null} when any of the four gates fails: the sender version is zero (reporting tokens disabled),
     * {@link #isMsgTypeCompatible(Message)} rejects the message type, the message carries no
     * {@link ChatMessageInfo#messageSecret()}, or the key has no id. The {@code selfJid} is the sender's user JID; the
     * {@code remoteJid} is the recipient for 1:1 sends, the group JID for group sends, or the status JID for status
     * broadcasts. All three inputs are folded into the HMAC key.
     *
     * @implNote This implementation encodes the full {@link MessageContainerSpec} and then runs the sparse projector
     * {@link ReportingTokenContent#compute(byte[], int)} to retain only the field numbers whitelisted for the current
     * sender version. The message is always re-encoded because the reporting-token-content cache is upstream of this
     * call.
     *
     * @param messageInfo the outgoing {@link ChatMessageInfo}
     * @param selfJid     the sender's user {@link Jid}
     * @param remoteJid   the recipient or group {@link Jid}
     * @return the {@code <reporting>} {@link Stanza}, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebReportingTokenUtils", exports = "genReportingTokenBody",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Stanza build(ChatMessageInfo messageInfo, Jid selfJid, Jid remoteJid) {
        var senderVersion = getSenderReportingTokenVersion();
        if (!isReportingTokenSendingEnabled(senderVersion)) {
            return null;
        }

        var message = messageInfo.message().content();
        if (!isMsgTypeCompatible(message)) {
            return null;
        }

        var messageSecret = messageInfo.messageSecret().orElse(null);
        if (messageSecret == null) {
            return null;
        }

        var fullProto = MessageContainerSpec.encode(messageInfo.message());
        var serializedProto = ReportingTokenContent.compute(fullProto, senderVersion);

        var id = messageInfo.key().id();
        if (id.isEmpty()) {
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

            var reportingBody = new StanzaBuilder()
                    .description("reporting_token")
                    .attribute("v", String.valueOf(reportingToken.get().version()))
                    .content(reportingToken.get().token())
                    .build();
            return new StanzaBuilder()
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
     * Returns the sender reporting-token version this client should advertise on every outgoing token.
     * <p>
     * A value of zero or below disables reporting-token emission entirely; a positive value selects which HMAC key
     * derivation scheme {@link ReportingToken#generate(byte[], String, Jid, Jid, byte[], int)} uses. Backed by
     * {@link ABProp#RT_SENDER_REPORTING_TOKEN_VERSION}.
     *
     * @return the sender reporting-token version
     */
    @WhatsAppWebExport(moduleName = "WAWebMessagingGatingUtils",
            exports = "getSenderReportingTokenVersion", adaptation = WhatsAppAdaptation.DIRECT)
    int getSenderReportingTokenVersion() {
        return abPropsService.getInt(ABProp.RT_SENDER_REPORTING_TOKEN_VERSION);
    }

    /**
     * Returns whether reporting-token generation is enabled for the given sender version.
     * <p>
     * A strictly positive version enables generation; anything else disables it.
     *
     * @param senderVersion the sender reporting-token version
     * @return {@code true} when {@code senderVersion} is strictly positive
     */
    @WhatsAppWebExport(moduleName = "WAWebMessagingGatingUtils",
            exports = "isReportingTokenSendingEnabled", adaptation = WhatsAppAdaptation.DIRECT)
    static boolean isReportingTokenSendingEnabled(int senderVersion) {
        return senderVersion > 0;
    }

    /**
     * Returns whether the message type is compatible with reporting tokens.
     * <p>
     * Reactions, encrypted reactions, encrypted event responses, and poll vote updates are excluded; the bodies of those
     * types do not carry the kind of content the franking tag is meant to authenticate.
     *
     * @implNote This implementation enumerates the four excluded message classes via a sealed switch; the accept/reject
     * decision matches the message types Cobalt emits.
     *
     * @param message the unwrapped {@link Message}, possibly {@code null}
     * @return {@code true} when the message type supports reporting tokens
     */
    @WhatsAppWebExport(moduleName = "WAWebMessagePluginGenerateReportingTokenContent",
            exports = "isMsgTypeReportingTokenCompatible", adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean isMsgTypeCompatible(Message message) {
        return switch (message) {
            case ReactionMessage _, PollUpdateMessage _, EncReactionMessage _, EncEventResponseMessage _ -> false;
            case null, default -> true;
        };
    }
}
