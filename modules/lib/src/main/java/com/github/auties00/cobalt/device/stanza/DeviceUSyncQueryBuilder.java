package com.github.auties00.cobalt.device.stanza;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.info.DeviceListHashInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.util.RandomIdUtils;

import java.util.*;

/**
 * Builds the USync IQ stanzas used by the device-protocol query path.
 *
 * @apiNote
 * Callers (typically {@link com.github.auties00.cobalt.device.DeviceService})
 * pass a set of user {@link Jid}s plus an optional dhash-and-timestamp map and
 * receive one or more IQ {@link NodeBuilder} instances they can send through
 * the socket. Each IQ wraps the WAP {@code <iq xmlns="usync" type="get">}
 * shape that {@link com.github.auties00.cobalt.device.adv.DeviceADVValidator}
 * and {@link DeviceUSyncResponseParser} consume on the response side. The
 * builder is the wire-level entry point for the
 * {@code WAWebAdvSyncDeviceListApi.syncDeviceList} flow, but does not include
 * the contact / business / picture / lid / disappearing-mode protocols that
 * the broader {@code WAWebUsync.USyncQuery} chain supports.
 *
 * @implNote
 * This implementation flattens the JS fluent {@code USyncQuery} builder into
 * a single static factory because Cobalt only emits the device protocol
 * (optionally paired with username) and never combines several USync
 * protocols in one query. Attribute insertion order matches WA Web's WAP
 * object literal so the encoded bytes are byte-identical to live traffic.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
@WhatsAppWebModule(moduleName = "WAWebUsyncDevice")
@WhatsAppWebModule(moduleName = "WAWebUsyncUsername")
public final class DeviceUSyncQueryBuilder {

    /**
     * Caps the number of {@code <user>} entries that fit in a single USync IQ.
     *
     * @apiNote
     * Larger user sets passed to {@link #build(Set, String, Map, boolean)}
     * are sliced into batches of this size so each individual IQ stays under
     * the server-enforced stanza budget the
     * {@code WAWebAdvSyncDeviceListApi.syncDeviceList} flow assumes.
     *
     * @implNote
     * This implementation hard-codes the cap because WA Web does the same
     * splitting outside the {@code USyncQuery} builder and the value is not
     * exposed as an AB prop.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = "syncDeviceList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int MAX_USERS_PER_QUERY = 500;

    /**
     * Phone-number addressing literal for the {@code addressing_mode}
     * attribute on a USync contact-protocol query element.
     *
     * @apiNote
     * Mirrors the {@code "pn"} entry of WA Web's frozen
     * {@code USYNC_ADDRESSING_MODE = {PN: "pn", LID: "lid"}} object exported
     * from {@code WAWebUsync}. Callers write the value verbatim into the
     * {@code addressing_mode} attribute when the contact protocol is in use.
     *
     * @see #USYNC_ADDRESSING_MODE_LID
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USYNC_ADDRESSING_MODE",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String USYNC_ADDRESSING_MODE_PN = "pn";

    /**
     * Long-identifier (LID) addressing literal for the {@code addressing_mode}
     * attribute on a USync contact-protocol query element.
     *
     * @apiNote
     * Mirrors the {@code "lid"} entry of WA Web's frozen
     * {@code USYNC_ADDRESSING_MODE = {PN: "pn", LID: "lid"}} object exported
     * from {@code WAWebUsync}.
     *
     * @see #USYNC_ADDRESSING_MODE_PN
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USYNC_ADDRESSING_MODE",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String USYNC_ADDRESSING_MODE_LID = "lid";

    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private DeviceUSyncQueryBuilder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Builds one or more USync device-protocol IQ stanzas for the given users.
     *
     * @apiNote
     * Returns a list because the {@code userJids} set is sliced into batches of
     * up to {@value MAX_USERS_PER_QUERY} entries (see
     * {@link #MAX_USERS_PER_QUERY}); callers send each {@link NodeBuilder} in
     * turn. The {@code context} attribute is written verbatim onto the
     * {@code <usync>} element and propagated through
     * {@code WAWebContactSyncLogger}; typical values are
     * {@code "message"} / {@code "interactive"} / {@code "inactive_group_migration"}.
     * Pass {@code includeUsernameProtocol = true} when the call site also
     * needs the {@code <username/>} probe (see
     * {@link com.github.auties00.cobalt.device.DeviceService}).
     *
     * @implNote
     * This implementation always returns at least one batch, even when
     * {@code userJids} is empty, so callers do not have to special-case the
     * zero-user path; the resulting IQ carries an empty
     * {@code <list/>}.
     *
     * @param userJids                the JIDs whose device lists must be queried
     * @param context                 the {@code context} attribute written
     *                                onto the {@code <usync>} element
     * @param hashInfos               per-user dhash-and-timestamp records that
     *                                let the server reply with
     *                                {@code <devices/>} when the cached list
     *                                is unchanged, or {@code null} for an
     *                                unconditional refresh
     * @param includeUsernameProtocol whether to add the
     *                                {@code <username/>} probe to the
     *                                {@code <query>} element
     * @return the IQ {@link NodeBuilder} instances, one per batch
     * @throws NullPointerException if {@code userJids} or {@code context} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static List<NodeBuilder> build(Set<Jid> userJids, String context, Map<Jid, DeviceListHashInfo> hashInfos, boolean includeUsernameProtocol) {
        Objects.requireNonNull(userJids, "userJids cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        var userJidsCount = userJids.size();
        if (userJidsCount <= MAX_USERS_PER_QUERY) {
            return List.of(buildEntry(userJids, context, hashInfos, includeUsernameProtocol));
        } else {
            var iterator = userJids.iterator();
            var batch = new ArrayList<Jid>(MAX_USERS_PER_QUERY);
            var batches = new ArrayList<NodeBuilder>(userJidsCount / MAX_USERS_PER_QUERY);
            while (iterator.hasNext()) {
                batch.add(iterator.next());
                if (batch.size() == MAX_USERS_PER_QUERY) {
                    batches.add(buildEntry(batch, context, hashInfos, includeUsernameProtocol));
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                batches.add(buildEntry(batch, context, hashInfos, includeUsernameProtocol));
            }
            return batches;
        }
    }

    /**
     * Assembles a single batch IQ around a {@code <usync><query/><list/></usync>}
     * envelope.
     *
     * @apiNote
     * Internal worker called by {@link #build(Set, String, Map, boolean)} for
     * every batch slice; callers should not use it directly.
     *
     * @implNote
     * This implementation drops the public-service announcements account from
     * the user list (matching the {@code WAWebWid.isServer} filter inside
     * {@code USyncQuery.$3}), then mirrors the JS attribute insertion order
     * ({@code sid}, {@code index}, {@code last}, {@code mode},
     * {@code context}) so the serialised WAP bytes match live traffic. The
     * outer {@code <iq>} {@link Node} has no {@code id} attribute; the
     * transport layer assigns one at send time, which is why a
     * {@link NodeBuilder} is returned instead of a built {@link Node}.
     *
     * @param userJids                the JIDs forming this batch
     * @param context                 the {@code context} attribute
     * @param hashInfos               the per-user dhash map, or {@code null}
     * @param includeUsernameProtocol whether to add the {@code <username/>}
     *                                probe
     * @return the partially built {@link NodeBuilder} for the IQ
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "USyncUsernameProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static NodeBuilder buildEntry(Collection<Jid> userJids, String context, Map<Jid, DeviceListHashInfo> hashInfos, boolean includeUsernameProtocol) {
        var sessionId = RandomIdUtils.newId();

        var userNodes = userJids.stream()
                .filter(jid -> !jid.toUserJid().equals(Jid.announcementsAccount()))
                .map(jid -> buildUserNode(jid, hashInfos))
                .toList();

        var listNode = new NodeBuilder()
                .description("list")
                .content(userNodes)
                .build();

        var devicesNode = new NodeBuilder()
                .description("devices")
                .attribute("version", "2")
                .build();

        Node queryNode;
        if (includeUsernameProtocol) {
            var usernameNode = new NodeBuilder()
                    .description("username")
                    .build();
            queryNode = new NodeBuilder()
                    .description("query")
                    .content(devicesNode, usernameNode)
                    .build();
        } else {
            queryNode = new NodeBuilder()
                    .description("query")
                    .content(devicesNode)
                    .build();
        }

        var usyncNode = new NodeBuilder()
                .description("usync")
                .attribute("sid", sessionId)
                .attribute("index", "0")
                .attribute("last", "true")
                .attribute("mode", "query")
                .attribute("context", context)
                .content(queryNode, listNode)
                .build();

        return new NodeBuilder()
                .description("iq")
                .attribute("to", JidServer.user())
                .attribute("xmlns", "usync")
                .attribute("type", "get")
                .content(usyncNode);
    }

    /**
     * Builds the per-user {@code <devices>} element carrying the cached dhash
     * and timestamps.
     *
     * @apiNote
     * Internal worker for {@link #buildUserNode(Jid, Map)}; emitted only when
     * the server may reply with the lighter "omitted" form (cached list is
     * still current) instead of resending the full device list.
     *
     * @implNote
     * This implementation returns {@code null} (so the caller omits the
     * element entirely) when every field of {@code hashInfo} is unset,
     * matching WA Web's {@code USyncDeviceProtocol.getUserElement} which
     * returns {@code null} in the same case.
     *
     * @param hashInfo the cached dhash record for the user, or {@code null}
     * @return the {@code <devices>} {@link Node}, or {@code null} when no
     *         attributes would be set
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "USyncDeviceProtocol",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static Node buildUserDevicesElement(DeviceListHashInfo hashInfo) {
        if (hashInfo == null) {
            return null;
        }

        var hash = hashInfo.hash();
        var ts = hashInfo.timestamp();
        var expectedTs = hashInfo.expectedTimestamp().orElse(null);

        if (hash == null && ts == null && expectedTs == null) {
            return null;
        }

        var devicesBuilder = new NodeBuilder()
                .description("devices");
        if (hash != null) {
            devicesBuilder.attribute("device_hash", hash);
        }
        if (ts != null) {
            devicesBuilder.attribute("ts", ts.getEpochSecond());
        }
        if (expectedTs != null) {
            devicesBuilder.attribute("expected_ts", expectedTs.getEpochSecond());
        }
        return devicesBuilder.build();
    }

    /**
     * Builds the {@code <user jid="...">} envelope for one entry of the USync
     * batch.
     *
     * @apiNote
     * Internal worker for {@link #buildEntry(Collection, String, Map, boolean)};
     * one is emitted per JID that survives the announcements-account filter.
     *
     * @implNote
     * This implementation defers to {@link #buildUserDevicesElement(DeviceListHashInfo)}
     * for the optional inner {@code <devices>} child. The JID written into
     * the {@code jid} attribute is normalised via {@link Jid#toUserJid()} so
     * the device suffix is stripped, matching WA Web's
     * {@code USER_JID} attribute helper.
     *
     * @param jid       the user JID
     * @param hashInfos the dhash map keyed by user JID, or {@code null}
     * @return the built {@code <user>} {@link Node}
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static Node buildUserNode(Jid jid, Map<Jid, DeviceListHashInfo> hashInfos) {
        var userJid = jid.toUserJid();
        var builder = new NodeBuilder()
                .description("user")
                .attribute("jid", userJid);

        if (hashInfos != null) {
            var devicesElement = buildUserDevicesElement(hashInfos.get(userJid));
            if (devicesElement != null) {
                builder.content(devicesElement);
            }
        }

        return builder.build();
    }
}
