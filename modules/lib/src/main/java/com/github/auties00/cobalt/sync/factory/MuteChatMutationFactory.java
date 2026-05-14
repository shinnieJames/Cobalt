package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.MuteAction;
import com.github.auties00.cobalt.model.sync.action.chat.MuteActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing mute-chat sync mutations.
 *
 * <p>Mirrors the {@code generateMuteMutation} export of WhatsApp Web's
 * {@code WAWebMuteChatSync} module. The factory is the outgoing-mutation
 * counterpart of {@link com.github.auties00.cobalt.sync.handler.MuteChatHandler}
 * — the handler keeps the inbound {@code applyMutation} pipeline while this
 * class produces the {@link SyncPendingMutation} values dispatched by
 * {@link com.github.auties00.cobalt.client.WhatsAppClient}.
 */
public final class MuteChatMutationFactory {
    /**
     * The AB-props service consulted for the
     * {@code enable_mention_everyone_syncd_sender} gate when building the
     * outgoing mute mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs a mute-chat mutation factory bound to the given AB-props
     * service.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       generated mute mutation
     */
    public MuteChatMutationFactory(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * Builds a pending mutation for muting or unmuting a chat.
     *
     * <p>Per WhatsApp Web {@code WAWebMuteChatSync.generateMuteMutation}:
     * <pre>{@code
     * generateMuteMutation(chatWid, muteEndSeconds, mentionAllSeconds) {
     *   var muted = muteEndSeconds !== undefined && muteEndSeconds !== 0;
     *   var now   = unixTimeMs();
     *   var endMs = muteEndSeconds;
     *   if (endMs !== -1) endMs *= 1000; // keep -1 as sentinel, otherwise seconds -> millis
     *   var mute  = {muted, muteEndTimestamp: endMs};
     *   if (isGroup(chatWid) && mentionAllSeconds != null
     *       && getABPropConfigValue("enable_mention_everyone_syncd_sender")) {
     *     mute.muteEveryoneMentionEndTimestamp = mentionAllSeconds > 0
     *         ? mentionAllSeconds * 1000
     *         : mentionAllSeconds;
     *   }
     *   return buildPendingMutation({
     *     collection: this.collectionName,
     *     indexArgs:  [await getChatJidMutationIndexForChat(chatWid, Actions.Mute)],
     *     operation:  SyncdOperation.SET,
     *     version:    this.getVersion(),
     *     value:      {muteAction: mute},
     *     timestamp:  now,
     *     action:     this.getAction()
     *   });
     * }
     * }</pre>
     *
     * <p>In WA Web {@code muteEndSeconds === -1} is a reserved sentinel meaning
     * "muted indefinitely" and is stored verbatim in the protobuf {@code int64}
     * field (NOT multiplied by 1000). Cobalt mirrors this: when
     * {@code muteEndSeconds == -1} the resulting {@link Instant} is
     * {@code Instant.ofEpochMilli(-1)} so that the on-wire int64 value is
     * {@code -1}. For all other non-zero values the seconds are converted to
     * milliseconds via {@link Instant#ofEpochMilli(long)}. When
     * {@code muteEndSeconds == 0} the chat is being unmuted and the timestamp
     * is serialised as {@code Instant.ofEpochMilli(0)} which matches WA Web's
     * {@code l = 0 * 1000 = 0} branch.
     *
     * <p>The {@code mentionAllSeconds} parameter follows the same sentinel
     * convention for non-positive values (passed through without scaling);
     * positive values are multiplied by 1000. The conversion is only applied
     * for groups and only when the
     * {@code enable_mention_everyone_syncd_sender} AB prop is enabled.
     *
     * <p>Per the comment in {@code WAWebLockChatSync.getChatLockMutation},
     * Cobalt does not yet track the outgoing-mutation LID/PN swap at this
     * layer (WA Web's {@code getChatJidMutationIndexForChat} would swap a PN
     * for its paired LID when LID1x1 migration is active). Callers that need
     * LID-aware indexing should resolve the index JID before invoking this
     * method.
     *
     * @param client            the WhatsApp client, used to read the
     *                          {@code enable_mention_everyone_syncd_sender}
     *                          AB prop and to supply the current timestamp
     * @param chatJid           the JID of the chat to mute or unmute
     * @param muteEndSeconds    the mute end time in seconds since the epoch;
     *                          {@code 0} means unmute, {@code -1} means muted
     *                          indefinitely, any other positive value is the
     *                          expiration timestamp
     * @param mentionAllSeconds the optional mention-everyone mute end time in
     *                          seconds, or {@code null} if the caller does
     *                          not wish to set a mention-everyone mute
     * @return the pending mutation for the mute action
     */
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "generateMuteMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation generateMuteMutation(
            WhatsAppClient client,
            Jid chatJid,
            long muteEndSeconds,
            Long mentionAllSeconds
    ) {
        var now = Instant.now();
        var muted = muteEndSeconds != 0L;
        // Preserves the -1 sentinel; all other values are scaled from seconds to milliseconds.
        var muteEndInstant = muteEndSeconds == -1L
                ? Instant.ofEpochMilli(-1L)
                : Instant.ofEpochMilli(muteEndSeconds * 1000L);
        var actionBuilder = new MuteActionBuilder()
                .muted(muted)
                .muteEndTimestamp(muteEndInstant);
        //   && (n > 0 ? s.muteEveryoneMentionEndTimestamp = n * 1e3 : s.muteEveryoneMentionEndTimestamp = n)
        if (chatJid.hasGroupOrCommunityServer()
                && mentionAllSeconds != null
                && abPropsService.getBool(ABProp.ENABLE_MENTION_EVERYONE_SYNCD_SENDER)) {
            var mentionMillis = mentionAllSeconds > 0
                    ? mentionAllSeconds * 1000L
                    : mentionAllSeconds;
            actionBuilder.muteEveryoneMentionEndTimestamp(Instant.ofEpochMilli(mentionMillis));
        }
        var action = actionBuilder.build();
        var value = new SyncActionValueBuilder()
                .timestamp(now)
                .muteAction(action)
                .build();
        var index = JSON.toJSONString(List.of(MuteAction.ACTION_NAME, chatJid.toString()));
        var trusted = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                now,
                MuteAction.ACTION_VERSION
        );
        return new SyncPendingMutation(trusted, 0); // ADAPTED: WA Web returns the raw mutation object; Cobalt wraps it in SyncPendingMutation for the outgoing queue
    }
}
