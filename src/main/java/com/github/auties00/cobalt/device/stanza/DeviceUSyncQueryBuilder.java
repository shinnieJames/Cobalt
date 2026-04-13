package com.github.auties00.cobalt.device.stanza;

import com.github.auties00.cobalt.model.device.info.DeviceListHashInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.util.RandomIdUtils;

import java.util.*;

/**
 * Builds USync IQ stanzas for device list queries.
 *
 * <p>Constructs batched USync IQ stanzas with the device protocol ({@code <devices version="2">})
 * and optional username protocol. Each query includes a list of user nodes with optional
 * delta update information (device hash, timestamp, expected timestamp).
 *
 * @implNote WAWebUsync.USyncQuery: constructs and executes USync requests with configurable
 * protocols and user lists. WAWebUsyncDevice.USyncDeviceProtocol: defines the device protocol
 * with {@code getName()="devices"}, {@code getQueryElement()}, and {@code getUserElement()}.
 */
public final class DeviceUSyncQueryBuilder {

    /**
     * Maximum users per USync query batch.
     *
     * @implNote WAWebAdvSyncDeviceListApi: batches large user lists to avoid oversized requests.
     */
    private static final int MAX_USERS_PER_QUERY = 500;

    /**
     * Prevents instantiation of this utility class.
     *
     * @implNote NO_WA_BASIS: Java-specific utility class pattern.
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
     * @param userJids                the user JIDs to include in this batch
     * @param context                 the context string for the usync request
     * @param hashInfos               hash information for delta updates, or {@code null}
     * @param includeUsernameProtocol whether to include the username protocol query element
     * @return the IQ node builder (not yet built, so caller can add id attribute)
     */
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

        // WAWebUsyncUsername.USyncUsernameProtocol: simple empty element for username protocol
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

        // WAWebUsync.USyncQuery: builds the usync node with session ID, mode, and context
        var usyncNode = new NodeBuilder()
                .description("usync")
                .attribute("sid", sessionId)
                .attribute("mode", "query")
                .attribute("last", "true")
                .attribute("index", "0")
                .attribute("context", context)
                .content(queryNode, listNode)
                .build();

        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "usync")
                .attribute("to", JidServer.user())
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
