package com.github.auties00.cobalt.device.usync;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.*;

/**
 * Builds USync IQ stanzas for device list queries.
 */
public final class DeviceUSyncQueryBuilder {
    private static final int MAX_USERS_PER_QUERY = 500;
    private static final String USYNC_XMLNS = "usync";
    private static final String DEVICES_VERSION = "2";

    private DeviceUSyncQueryBuilder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Builds batched USync queries for large user lists.
     * <p>
     * Each batch contains at most 500 users.
     *
     * @param userJids the user JIDs to query
     * @return list of IQ nodes, one per batch
     */
    public static List<NodeBuilder> build(Collection<Jid> userJids) {
        return build(userJids, "message");
    }

    /**
     * Builds batched USync queries with custom context.
     *
     * @param userJids the user JIDs to query
     * @param context  the context for device filtering
     * @return list of IQ nodes, one per batch
     */
    public static List<NodeBuilder> build(Collection<Jid> userJids, String context) {
        Objects.requireNonNull(userJids, "userJids cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        var userJidsCount = userJids.size();
        if(userJidsCount <= MAX_USERS_PER_QUERY) {
            return List.of(buildEntry(userJids, context));
        } else if(userJids instanceof List<Jid> list){
            var batches = new ArrayList<NodeBuilder>(userJidsCount / MAX_USERS_PER_QUERY);
            for (var i = 0; i < list.size(); i += MAX_USERS_PER_QUERY) {
                var end = Math.min(i + MAX_USERS_PER_QUERY, list.size());
                var batch = list.subList(i, end);
                batches.add(buildEntry(batch, context));
            }
            return batches;
        } else {
            var iterator = userJids.iterator();
            var batch = new ArrayList<Jid>(MAX_USERS_PER_QUERY);
            var batches = new ArrayList<NodeBuilder>(userJidsCount / MAX_USERS_PER_QUERY);
            while (iterator.hasNext()) {
                batch.add(iterator.next());
                if (batch.size() == MAX_USERS_PER_QUERY) {
                    batches.add(buildEntry(batch, context));
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                batches.add(buildEntry(batch, context));
            }
            return batches;
        }
    }

    private static NodeBuilder buildEntry(Collection<Jid> userJids, String context) {
        var sessionId = UUID.randomUUID().toString();

        // Build user list nodes
        var userNodes = userJids.stream()
                .map(DeviceUSyncQueryBuilder::buildUserNode)
                .toList();

        // Build list node
        var listNode = new NodeBuilder()
                .description("list")
                .content(userNodes)
                .build();

        // Build query node
        var devicesNode = new NodeBuilder()
                .description("devices")
                .attribute("version", DEVICES_VERSION)
                .build();

        var queryNode = new NodeBuilder()
                .description("query")
                .content(devicesNode)
                .build();

        // Build usync node
        var usyncNode = new NodeBuilder()
                .description("usync")
                .attribute("sid", sessionId)
                .attribute("mode", "query")
                .attribute("last", "true")
                .attribute("index", "0")
                .attribute("context", context)
                .content(queryNode, listNode)
                .build();

        // Build IQ node
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", USYNC_XMLNS)
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(usyncNode);
    }

    private static Node buildUserNode(Jid jid) {
        var userJid = jid.toUserJid();
        return new NodeBuilder()
                .description("user")
                .attribute("jid", userJid)
                .build();
    }
}
