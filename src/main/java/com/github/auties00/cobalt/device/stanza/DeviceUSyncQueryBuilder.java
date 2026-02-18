package com.github.auties00.cobalt.device.stanza;

import com.github.auties00.cobalt.model.device.info.DeviceListHashInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.util.WhatsAppIdGenerator;

import java.util.*;

/**
 * Builds USync IQ stanzas for device list queries.
 *
 * @apiNote WAWebUsync.USyncQuery: constructs and executes USync requests with configurable
 * protocols and user lists. WAWebUsyncDevice.USyncDeviceProtocol: defines the device protocol.
 */
public final class DeviceUSyncQueryBuilder {

    /**
     * Maximum users per USync query batch.
     *
     * @apiNote WAWebUsync: batches large user lists to avoid oversized requests.
     */
    private static final int MAX_USERS_PER_QUERY = 500;

    private DeviceUSyncQueryBuilder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Builds batched USync queries with optional username protocol.
     *
     * @param userJids                the user JIDs to query
     * @param context                 the context for device filtering
     * @param hashInfos               hash information for delta updates, or {@code null}
     * @param includeUsernameProtocol whether to include the username protocol
     * @return list of IQ node builders, one per batch
     *
     * @apiNote WAWebUsync.USyncQuery: supports adding multiple protocols (devices, username,
     * contact, picture, etc.) in a single request.
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

    private static NodeBuilder buildEntry(Collection<Jid> userJids, String context, Map<Jid, DeviceListHashInfo> hashInfos, boolean includeUsernameProtocol) {
        // WAWap.generateId(): generates session ID in WhatsApp format
        var sessionId = WhatsAppIdGenerator.newId();

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

    private static Node buildUserNode(Jid jid, Map<Jid, DeviceListHashInfo> hashInfos) {
        var userJid = jid.toUserJid();
        var builder = new NodeBuilder()
                .description("user")
                .attribute("jid", userJid);

        // WAWebAdvSyncDeviceListApi: adds device_hash, ts, and expected_ts attributes
        // for delta updates when hash info is available
        if (hashInfos != null) {
            var hashInfo = hashInfos.get(userJid);
            if (hashInfo != null) {
                builder.attribute("device_hash", hashInfo.hash());
                builder.attribute("ts", hashInfo.timestamp().getEpochSecond());
                hashInfo.expectedTimestamp()
                        .ifPresent(instant -> builder.attribute("expected_ts", instant.getEpochSecond()));
            }
        }

        return builder.build();
    }
}
