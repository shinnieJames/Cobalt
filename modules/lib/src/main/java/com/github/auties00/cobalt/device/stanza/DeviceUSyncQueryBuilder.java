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
 * Builds the USync IQ stanzas Cobalt sends to the WhatsApp server when it needs to learn
 * which companion devices each user has linked.
 *
 * <p>USync is WhatsApp's multi-user query protocol; the device protocol inside it returns
 * each user's device list and signed key index list. This builder wraps the repetitive XML
 * construction (session id generation, per-user nodes, delta update attributes, username
 * co-query, batching large lists) so callers at {@link com.github.auties00.cobalt.device.DeviceService}
 * can simply provide a set of JIDs and a sync context.
 *
 * <p>Each user entry optionally carries the locally cached {@code device_hash}, timestamp, and
 * expected timestamp so the server can answer with an "omitted" result (hash still matches)
 * instead of retransmitting unchanged device lists.
 *
 * @implNote WAWebUsync.USyncQuery: constructs and executes USync requests with configurable
 * protocols and user lists. WAWebUsyncDevice.USyncDeviceProtocol: defines the device protocol
 * with {@code getName()="devices"}, {@code getQueryElement()}, and {@code getUserElement()}.
 * WAWebUsyncUsername.USyncUsernameProtocol: optional username co-query protocol.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
@WhatsAppWebModule(moduleName = "WAWebUsyncDevice")
@WhatsAppWebModule(moduleName = "WAWebUsyncUsername")
public final class DeviceUSyncQueryBuilder {

    /**
     * Maximum users per USync query batch.
     *
     * @implNote WAWebAdvSyncDeviceListApi: batches large user lists to avoid oversized requests.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = "syncDeviceList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int MAX_USERS_PER_QUERY = 500;

    /**
     * Wire-protocol addressing-mode discriminator for the {@code addressing_mode}
     * attribute on the contact USync query element: phone-number addressing.
     *
     * <p>WhatsApp Web exports a frozen object {@code USYNC_ADDRESSING_MODE = {PN: "pn",
     * LID: "lid"}} from {@code WAWebUsync} that callers branch on when building the
     * contact protocol query element. Cobalt mirrors the constant as plain string
     * fields because no enum coercion is required at the wire layer; they are written
     * verbatim into the {@code addressing_mode} attribute when present.
     *
     * @implNote WAWebUsync.USYNC_ADDRESSING_MODE.PN: literal value {@code "pn"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USYNC_ADDRESSING_MODE",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String USYNC_ADDRESSING_MODE_PN = "pn";

    /**
     * Wire-protocol addressing-mode discriminator for the {@code addressing_mode}
     * attribute on the contact USync query element: long-identifier (LID) addressing.
     *
     * @implNote WAWebUsync.USYNC_ADDRESSING_MODE.LID: literal value {@code "lid"}.
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
     * Builds batched USync queries with optional username protocol.
     *
     * <p>Splits large user lists into batches of up to {@value MAX_USERS_PER_QUERY} users
     * and constructs an IQ stanza for each batch.
     *
     * @implNote WAWebUsyncDevice.USyncDeviceProtocol: defines the device protocol query.
     * WAWebUsync.USyncQuery.$3: constructs the full IQ stanza with usync, query, and list nodes.
     * WAWebAdvSyncDeviceListApi: orchestrates the device sync request with user hash info.
     * @param userJids                the user JIDs to query
     * @param context                 the context for device filtering
     * @param hashInfos               hash information for delta updates, or {@code null}
     * @param includeUsernameProtocol whether to include the username protocol
     * @return list of IQ node builders, one per batch
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
     * Builds a single USync IQ stanza for a batch of users.
     *
     * <p>Constructs the full IQ structure: {@code <iq> <usync> <query> <devices/> </query>
     * <list> <user/> ... </list> </usync> </iq>}.
     *
     * @implNote WAWebUsync.USyncQuery.$3: builds the complete IQ stanza with usync node containing
     * query (with protocol elements) and list (with user elements). The IQ id attribute is
     * added by the transport layer when sending.
     * @implNote ADAPTED: WAWebUsyncUsername.USyncUsernameProtocol is a protocol descriptor class
     * in WA Web with {@code getName()="username"}, {@code getQueryElement()} returning
     * {@code WAWap.wap("username", null)} (an empty {@code <username/>} node), and
     * {@code getUserElement()} returning {@code null}. Cobalt inlines this as a boolean flag:
     * when {@code includeUsernameProtocol} is true an empty {@code <username/>} child is appended
     * to the {@code <query>} node. Because {@code getUserElement} always returns {@code null}
     * in WA Web, no per-user element is ever emitted, and no {@code <user>} child is added for
     * the username protocol here either.
     * @param userJids                the user JIDs to include in this batch
     * @param context                 the context string for the usync request
     * @param hashInfos               hash information for delta updates, or {@code null}
     * @param includeUsernameProtocol whether to include the username protocol query element
     * @return the IQ node builder (not yet built, so caller can add id attribute)
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "USyncUsernameProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static NodeBuilder buildEntry(Collection<Jid> userJids, String context, Map<Jid, DeviceListHashInfo> hashInfos, boolean includeUsernameProtocol) {
        // WAWap.generateId(): generates session ID in WhatsApp format
        var sessionId = RandomIdUtils.newId();

        // WAWebAdvSyncDeviceListApi: filters out PSA (Public Service Announcements) account
        // e.id.user!=="0" && a.withUser(...)
        var userNodes = userJids.stream()
                .filter(jid -> !jid.toUserJid().equals(Jid.announcementsAccount()))
                .map(jid -> buildUserNode(jid, hashInfos))
                .toList();

        var listNode = new NodeBuilder()
                .description("list")
                .content(userNodes)
                .build();

        // WAWebUsyncDevice.USyncDeviceProtocol: defines devices protocol with version
        var devicesNode = new NodeBuilder()
                .description("devices")
                .attribute("version", "2")
                .build();

        // WAWebUsyncUsername.USyncUsernameProtocol.getQueryElement: WAWap.wap("username", null)
        // yields an empty <username/> element. getUserElement always returns null so no per-user
        // <username> child is emitted.
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

        // WAWebUsync.USyncQuery.$3: WAWap.wap("usync", {sid, index:"0", last:"true",
        // mode, context}) — attribute insertion order matches the JS object literal
        // exactly so that the encoded WAP byte stream is identical to live traffic.
        var usyncNode = new NodeBuilder()
                .description("usync")
                .attribute("sid", sessionId)
                .attribute("index", "0")
                .attribute("last", "true")
                .attribute("mode", "query")
                .attribute("context", context)
                .content(queryNode, listNode)
                .build();

        // WAWebUsync.USyncQuery.$3: WAWap.wap("iq", {to: S_WHATSAPP_NET, xmlns: "usync",
        // type: "get", id: generateId()}) — attribute order mirrors JS source. The id
        // attribute is appended by the transport layer at send time.
        return new NodeBuilder()
                .description("iq")
                .attribute("to", JidServer.user())
                .attribute("xmlns", "usync")
                .attribute("type", "get")
                .content(usyncNode);
    }

    /**
     * Builds the per-user devices element for delta updates.
     *
     * <p>Returns a {@code <devices>} node with {@code device_hash}, {@code ts}, and
     * {@code expected_ts} attributes if hash info is available, or {@code null} if all
     * values are absent.
     *
     * @implNote WAWebUsyncDevice.USyncDeviceProtocol.getUserElement: returns a devices node
     * with device_hash, ts, expected_ts attributes for delta queries, or null if all are null.
     * @param hashInfo the hash info for this user, or {@code null}
     * @return the devices user element, or {@code null} if not needed
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "USyncDeviceProtocol",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static Node buildUserDevicesElement(DeviceListHashInfo hashInfo) {
        // WAWebUsyncDevice.USyncDeviceProtocol.getUserElement
        if (hashInfo == null) {
            return null;
        }

        var hash = hashInfo.hash();
        var ts = hashInfo.timestamp();
        var expectedTs = hashInfo.expectedTimestamp().orElse(null);

        // WAWebUsyncDevice.USyncDeviceProtocol.getUserElement: return null if all are null
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
     * Builds a user node for the USync query list.
     *
     * <p>Each user node contains the JID attribute and optional protocol-specific children
     * such as a {@code <devices>} element with delta update attributes.
     *
     * @implNote WAWebUsync.USyncQuery.$3: constructs user nodes with jid attribute and
     * protocol-specific children from each protocol's getUserElement method.
     * @param jid       the user JID
     * @param hashInfos hash information for delta updates, or {@code null}
     * @return the user node
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static Node buildUserNode(Jid jid, Map<Jid, DeviceListHashInfo> hashInfos) {
        var userJid = jid.toUserJid();
        var builder = new NodeBuilder()
                .description("user")
                .attribute("jid", userJid);

        // WAWebUsyncDevice.USyncDeviceProtocol.getUserElement: adds devices child element
        // with device_hash, ts, and expected_ts for delta updates
        if (hashInfos != null) {
            var devicesElement = buildUserDevicesElement(hashInfos.get(userJid));
            if (devicesElement != null) {
                builder.content(devicesElement);
            }
        }

        return builder.build();
    }
}
