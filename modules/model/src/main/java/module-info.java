/**
 * Hand-written protobuf domain model shared by both Cobalt client transports.
 *
 * <p>This module holds the WhatsApp domain types only: messages and their content variants, chats,
 * contacts, newsletters and calls, the WhatsApp Business and commerce models, the Meta Cloud API
 * request and response models, user settings, preferences and privacy, the app-state (syncd)
 * replication models, and the low-level Signal, pairing and device-fanout wire types. Every type is
 * a {@code @ProtobufMessage}, {@code @ProtobufEnum} or {@code @ProtobufMixin}; the module carries no
 * client, socket or transport logic. It has no dependency on the client library
 * {@code com.github.auties00.cobalt}; the dependency runs the other way, so a type in this module can
 * never reference a client-library type.
 *
 * <h2>Export policy</h2>
 * The package exports are split into two tiers:
 * <ul>
 *   <li><b>Public domain API</b> (unqualified {@code exports}): packages whose types appear on the
 *       user-facing client surface, whether as a parameter or result of a client operation, as a
 *       listener payload, on a store accessor, or as a variant of a public sealed hierarchy that
 *       callers construct and pattern-match (for example
 *       {@link com.github.auties00.cobalt.model.message.MessageContainer},
 *       {@link com.github.auties00.cobalt.model.chat.Chat} and
 *       {@link com.github.auties00.cobalt.model.jid.Jid}). These are readable by every consumer.
 *   <li><b>Library-internal model</b> (qualified {@code exports ... to com.github.auties00.cobalt}):
 *       wire, cryptographic, app-state-sync and device-fanout plumbing that only the client library
 *       manipulates. No legitimate user-facing signature is expected to name these types, so they are
 *       hidden from ordinary consumers and made visible only to the client library.
 * </ul>
 *
 * <p>The single qualified target {@code com.github.auties00.cobalt} is the client library, the only
 * module that depends on this one; {@code com.github.auties00.cobalt.wam} and
 * {@code com.github.auties00.cobalt.meta} are independent of it.
 */
module com.github.auties00.cobalt.model {
    // transitive: Jid.of(ProtobufString) and other public custom (de)serializers expose protobuf-base types
    requires transitive it.auties.protobuf.base;
    requires com.github.auties00.collections;
    // transitive: Jid.toSignalAddress() returns a libsignal SignalProtocolAddress on the public API
    requires transitive com.github.auties00.libsignal;
    requires com.alibaba.fastjson2;
    requires com.googlecode.ezvcard;

    // Addressing
    exports com.github.auties00.cobalt.model.jid;

    // Messaging: the MessageContainer envelope and its content variants
    exports com.github.auties00.cobalt.model.message;
    exports com.github.auties00.cobalt.model.message.text;
    exports com.github.auties00.cobalt.model.message.media;
    exports com.github.auties00.cobalt.model.message.interactive;
    exports com.github.auties00.cobalt.model.message.context;
    exports com.github.auties00.cobalt.model.message.addon;
    exports com.github.auties00.cobalt.model.message.call;
    exports com.github.auties00.cobalt.model.message.commerce;
    exports com.github.auties00.cobalt.model.message.contact;
    exports com.github.auties00.cobalt.model.message.event;
    exports com.github.auties00.cobalt.model.message.group;
    exports com.github.auties00.cobalt.model.message.list;
    exports com.github.auties00.cobalt.model.message.location;
    exports com.github.auties00.cobalt.model.message.newsletter;
    exports com.github.auties00.cobalt.model.message.payment;
    exports com.github.auties00.cobalt.model.message.poll;
    exports com.github.auties00.cobalt.model.message.status;
    exports com.github.auties00.cobalt.model.message.bot;

    // Conversations: chats, groups, contacts, newsletters and calls
    exports com.github.auties00.cobalt.model.chat;
    exports com.github.auties00.cobalt.model.chat.group;
    exports com.github.auties00.cobalt.model.contact;
    exports com.github.auties00.cobalt.model.newsletter;
    exports com.github.auties00.cobalt.model.call;

    // Media transfer descriptors and geographic points
    exports com.github.auties00.cobalt.model.media;
    exports com.github.auties00.cobalt.model.location;

    // AI bots: profile queries plus the BotMetadata content tree
    exports com.github.auties00.cobalt.model.bot;
    exports com.github.auties00.cobalt.model.bot.ai;
    exports com.github.auties00.cobalt.model.bot.feedback;
    exports com.github.auties00.cobalt.model.bot.metrics;
    exports com.github.auties00.cobalt.model.bot.plugin;
    exports com.github.auties00.cobalt.model.bot.profile;
    exports com.github.auties00.cobalt.model.bot.rendering;
    exports com.github.auties00.cobalt.model.bot.response;
    exports com.github.auties00.cobalt.model.bot.session;

    // WhatsApp Business and commerce queried through the client
    exports com.github.auties00.cobalt.model.business;
    exports com.github.auties00.cobalt.model.business.profile;
    exports com.github.auties00.cobalt.model.business.aichannel;
    exports com.github.auties00.cobalt.model.business.acs;
    exports com.github.auties00.cobalt.model.business.cart;
    exports com.github.auties00.cobalt.model.business.order;
    exports com.github.auties00.cobalt.model.business.ctwa;
    exports com.github.auties00.cobalt.model.business.promotion;
    exports com.github.auties00.cobalt.model.business.subscription;
    exports com.github.auties00.cobalt.model.business.compliance;
    exports com.github.auties00.cobalt.model.business.crossposting;
    exports com.github.auties00.cobalt.model.business.linking;
    exports com.github.auties00.cobalt.model.business.postcode;
    exports com.github.auties00.cobalt.model.business.flow;
    exports com.github.auties00.cobalt.model.business.webgraphql;

    // Meta Cloud API request and response models
    exports com.github.auties00.cobalt.model.cloud;
    exports com.github.auties00.cobalt.model.cloud.analytics;
    exports com.github.auties00.cobalt.model.cloud.commerce;
    exports com.github.auties00.cobalt.model.cloud.flow;
    exports com.github.auties00.cobalt.model.cloud.phone;
    exports com.github.auties00.cobalt.model.cloud.signup;
    exports com.github.auties00.cobalt.model.cloud.template;
    exports com.github.auties00.cobalt.model.cloud.template.library;
    exports com.github.auties00.cobalt.model.cloud.waba;

    // Account device: capabilities, ADV identity, device-list info, pairing config and device-sync keys
    exports com.github.auties00.cobalt.model.device.capabilities;
    exports com.github.auties00.cobalt.model.device.identity;
    exports com.github.auties00.cobalt.model.device.info;
    exports com.github.auties00.cobalt.model.device.pairing;
    exports com.github.auties00.cobalt.model.device.sync;

    // Settings, preferences and privacy
    exports com.github.auties00.cobalt.model.setting;
    exports com.github.auties00.cobalt.model.setting.notice;
    exports com.github.auties00.cobalt.model.setting.privacy;
    exports com.github.auties00.cobalt.model.setting.push;
    exports com.github.auties00.cobalt.model.preference;
    exports com.github.auties00.cobalt.model.privacy;
    exports com.github.auties00.cobalt.model.props;
    exports com.github.auties00.cobalt.model.tos;

    // Server-pushed account integrity challenge payload (listener-facing)
    exports com.github.auties00.cobalt.model.integrity;

    // Payments
    exports com.github.auties00.cobalt.model.payment;

    // App-state (syncd): the SyncAction hierarchy, action values and orphan mutations
    exports com.github.auties00.cobalt.model.sync;
    exports com.github.auties00.cobalt.model.sync.action;
    exports com.github.auties00.cobalt.model.sync.action.bot;
    exports com.github.auties00.cobalt.model.sync.action.chat;
    exports com.github.auties00.cobalt.model.sync.action.device;
    exports com.github.auties00.cobalt.model.sync.action.media;
    exports com.github.auties00.cobalt.model.sync.action.payment;
    exports com.github.auties00.cobalt.model.sync.action.privacy;
    exports com.github.auties00.cobalt.model.sync.action.setting;
    exports com.github.auties00.cobalt.model.sync.mutation;

    // System/control message wrappers exposed to the client (app-state keys, peer device control)
    exports com.github.auties00.cobalt.model.message.system.appstate;
    exports com.github.auties00.cobalt.model.message.system.peer;

    // Error and diagnostic models carried on the public exceptions
    exports com.github.auties00.cobalt.model.error;

    // Protobuf type-conversion mixins
    exports com.github.auties00.cobalt.model.mixin;

    // Signal/Noise protocol key material (the client library uses libsignal types at runtime)
    exports com.github.auties00.cobalt.model.signal to com.github.auties00.cobalt;

    // Device metadata, props and platform enum (internal fanout bookkeeping)
    exports com.github.auties00.cobalt.model.device to com.github.auties00.cobalt;

    // Low-level syncd wire types plus the linked-device-only sync actions
    exports com.github.auties00.cobalt.model.sync.data to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.sync.history to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.sync.action.business to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.sync.action.call to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.sync.action.contact to com.github.auties00.cobalt;

    // Protocol, history-sync and encrypted system message wrappers
    exports com.github.auties00.cobalt.model.message.system to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.message.system.history to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.message.security to com.github.auties00.cobalt;

    // PN-to-LID chat migration wire models
    exports com.github.auties00.cobalt.model.jid.migration to com.github.auties00.cobalt;

    // Community metadata resolved by internal stream handlers
    exports com.github.auties00.cobalt.model.chat.community to com.github.auties00.cobalt;

    // Low-level VOIP RTC data-channel wire types
    exports com.github.auties00.cobalt.model.call.datachannel to com.github.auties00.cobalt;

    // Business back-office models reached only through internal GraphQL operations
    exports com.github.auties00.cobalt.model.business.ads to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.business.ai to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.business.auth to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.business.catalog to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.business.marketing to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.business.support to com.github.auties00.cobalt;
    exports com.github.auties00.cobalt.model.business.waa to com.github.auties00.cobalt;

    // Abuse-report token wire schema
    exports com.github.auties00.cobalt.model.reporting to com.github.auties00.cobalt;

    // Federated-identity (Waffle) relay wire models
    exports com.github.auties00.cobalt.model.federated to com.github.auties00.cobalt;
}
