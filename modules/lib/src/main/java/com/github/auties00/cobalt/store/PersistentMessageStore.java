package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoSpec;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfoSpec;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.GetOp;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Package-private LMDB facade that backs {@link PersistentStore}'s message
 * storage.
 *
 * <p>Owns one {@link Env} rooted at {@code <sessionDirectory>/messages.lmdb}
 * and exposes three logical databases:
 * <ul>
 *   <li>{@code chat_messages} keyed by {@code chatJid + 0x00 + msgId} —
 *       per-chat range scans use a half-open cursor range from
 *       {@code chatJid + 0x00} to {@code chatJid + 0x01}.
 *   <li>{@code newsletter_messages} keyed by {@code newsletterJid + 0x00 +
 *       serverId(BE)} — serverId is monotonic and unique per newsletter, so
 *       cursor first/last directly yields oldest/newest. Lookup by key-id
 *       falls back to a per-newsletter scan.
 *   <li>{@code status_messages} flat, keyed by {@code msgId} — used for the
 *       global status feed.
 * </ul>
 *
 * <p>Read operations decode the protobuf payload eagerly into a heap copy
 * before the transaction closes, so callers never hold references to
 * mmap'd memory that may be invalidated by a concurrent writer.
 *
 * <p>Write operations open a single short-lived write transaction and
 * commit immediately. On {@link Env.MapFullException} the env is grown
 * (doubled) under {@link #growLock} and the write is retried once.
 *
 * <p>Stream-returning methods open a read transaction whose lifetime is
 * tied to the returned stream's {@link Stream#close()} hook; callers MUST
 * consume the stream inside a try-with-resources block, otherwise the
 * underlying read transaction occupies a slot in LMDB's reader table
 * until the stream is garbage collected.
 *
 * @see PersistentStore
 */
final class PersistentMessageStore implements AutoCloseable {
    /**
     * Key separator between the JID prefix and the message-id suffix.
     * {@code 0x00} is safe because JID string forms never contain a NUL
     * and message-id strings are base64-shaped.
     */
    private static final byte KEY_SEPARATOR = 0x00;

    /**
     * Sentinel byte that terminates a per-JID cursor range. One greater
     * than {@link #KEY_SEPARATOR}, so a half-open range
     * {@code [jid + 0x00, jid + 0x01)} captures every key whose prefix
     * matches the JID exactly.
     */
    private static final byte KEY_RANGE_END = 0x01;

    /**
     * Maximum number of LMDB reader slots. Realistic concurrent ceiling
     * under virtual threads is in the dozens; 512 is comfortable
     * headroom without bloating the lock table.
     */
    private static final int MAX_READERS = 512;

    /**
     * Number of named databases opened inside the env.
     */
    private static final int MAX_DBS = 3;

    /**
     * Name of the chat-message database.
     */
    private static final String CHAT_MESSAGES_DBI = "chat_messages";

    /**
     * Name of the newsletter-message database.
     */
    private static final String NEWSLETTER_MESSAGES_DBI = "newsletter_messages";

    /**
     * Name of the status-feed database.
     */
    private static final String STATUS_MESSAGES_DBI = "status_messages";

    /**
     * Underlying LMDB environment.
     */
    private final Env<ByteBuffer> env;

    /**
     * Chat-message database handle.
     */
    private final Dbi<ByteBuffer> chatMessages;

    /**
     * Newsletter-message database handle.
     */
    private final Dbi<ByteBuffer> newsletterMessages;

    /**
     * Status-feed database handle.
     */
    private final Dbi<ByteBuffer> statusMessages;

    /**
     * Current configured map size in bytes; doubled on
     * {@link Env.MapFullException}.
     */
    private volatile long mapSize;

    /**
     * Mutex guarding {@link #growMap()} so concurrent writers do not
     * race when resizing the env.
     */
    private final Object growLock;

    /**
     * Constructs a {@code MessageStore} around an already-opened env and
     * its three database handles.
     *
     * @param env                 the LMDB environment
     * @param chatMessages        the chat-message dbi
     * @param newsletterMessages  the newsletter-message dbi
     * @param statusMessages      the status-feed dbi
     * @param initialMapSize      the initial map size, retained for
     *                            grow-on-full bookkeeping
     */
    private PersistentMessageStore(Env<ByteBuffer> env, Dbi<ByteBuffer> chatMessages, Dbi<ByteBuffer> newsletterMessages, Dbi<ByteBuffer> statusMessages, long initialMapSize) {
        this.env = env;
        this.chatMessages = chatMessages;
        this.newsletterMessages = newsletterMessages;
        this.statusMessages = statusMessages;
        this.mapSize = initialMapSize;
        this.growLock = new Object();
    }

    /**
     * Opens or creates an LMDB environment under {@code envDirectory} and
     * the three named databases backing chat, newsletter and status
     * messages.
     *
     * @param envDirectory   the directory that will host {@code data.mdb}
     *                       and {@code lock.mdb}; created if it does not
     *                       exist
     * @param initialMapSize the maximum size (bytes) the env file may
     *                       grow to before {@link Env.MapFullException}
     *                       triggers a doubling resize
     * @return a fully initialised {@code MessageStore}
     * @throws IOException if the directory cannot be created
     */
    static PersistentMessageStore open(Path envDirectory, long initialMapSize) throws IOException {
        Files.createDirectories(envDirectory);
        var env = Env.create()
                .setMapSize(initialMapSize)
                .setMaxDbs(MAX_DBS)
                .setMaxReaders(MAX_READERS)
                .open(envDirectory.toFile());
        var chatMessages = env.openDbi(CHAT_MESSAGES_DBI, DbiFlags.MDB_CREATE);
        var newsletterMessages = env.openDbi(NEWSLETTER_MESSAGES_DBI, DbiFlags.MDB_CREATE);
        var statusMessages = env.openDbi(STATUS_MESSAGES_DBI, DbiFlags.MDB_CREATE);
        return new PersistentMessageStore(env, chatMessages, newsletterMessages, statusMessages, initialMapSize);
    }

    /**
     * Inserts or replaces a chat message in the {@code chat_messages}
     * dbi.
     *
     * @param chatJid the JID identifying the owning chat
     * @param info    the message to persist; ignored if it carries no
     *                key id
     */
    void putChatMessage(Jid chatJid, ChatMessageInfo info) {
        var msgId = info.key().id().orElse(null);
        if (msgId == null) {
            return;
        }
        var key = encodePrefixedKey(chatJid, msgId.getBytes(StandardCharsets.UTF_8));
        var size = ChatMessageInfoSpec.sizeOf(info);
        withWriteTxn(txn -> {
            var value = chatMessages.reserve(txn, key, size);
            ChatMessageInfoSpec.encode(info, ProtobufOutputStream.toBuffer(value));
            return null;
        });
    }

    /**
     * Looks up a chat message by its key id within the given chat.
     *
     * @param chatJid the JID identifying the owning chat
     * @param msgId   the message key id
     * @return the decoded message, or empty if no entry exists
     */
    Optional<ChatMessageInfo> getChatMessage(Jid chatJid, String msgId) {
        var key = encodePrefixedKey(chatJid, msgId.getBytes(StandardCharsets.UTF_8));
        return withReadTxn(txn -> {
            var buffer = chatMessages.get(txn, key);
            return buffer == null
                    ? Optional.<ChatMessageInfo>empty()
                    : Optional.of(decodeChatMessage(buffer));
        });
    }

    /**
     * Removes a chat message by its key id within the given chat.
     *
     * @param chatJid the JID identifying the owning chat
     * @param msgId   the message key id
     * @return {@code true} if an entry was removed, {@code false} if no
     *         such entry existed
     */
    boolean removeChatMessage(Jid chatJid, String msgId) {
        var key = encodePrefixedKey(chatJid, msgId.getBytes(StandardCharsets.UTF_8));
        return withWriteTxn(txn -> chatMessages.delete(txn, key));
    }

    /**
     * Removes every chat message belonging to the given chat.
     *
     * @param chatJid the JID identifying the owning chat
     * @return the number of entries removed
     */
    int removeChatMessages(Jid chatJid) {
        var range = jidRange(chatJid);
        return withWriteTxn(txn -> deleteRange(chatMessages, txn, range));
    }

    /**
     * Returns the oldest chat message of the given chat, or empty if the
     * chat is empty.
     *
     * @param chatJid the JID identifying the owning chat
     * @return the oldest message in cursor order, or empty
     */
    Optional<ChatMessageInfo> oldestChatMessage(Jid chatJid) {
        return firstInRange(chatMessages, jidRange(chatJid), PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Returns the newest chat message of the given chat, or empty if
     * the chat is empty.
     *
     * @param chatJid the JID identifying the owning chat
     * @return the newest message in cursor order, or empty
     */
    Optional<ChatMessageInfo> newestChatMessage(Jid chatJid) {
        return lastInRange(chatMessages, jidRange(chatJid), PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Returns the number of chat messages stored for the given chat.
     *
     * <p>Performed by walking the cursor range — {@code O(n)} in the
     * chat size. Callers that need this value frequently must cache it
     * client-side.
     *
     * @param chatJid the JID identifying the owning chat
     * @return the number of stored messages
     */
    int countChatMessages(Jid chatJid) {
        return countRange(chatMessages, jidRange(chatJid));
    }

    /**
     * Returns a lazy stream of every chat message belonging to the
     * given chat, in cursor order.
     *
     * <p>The returned stream owns a read transaction and a cursor that
     * remain open until the stream is closed. Callers MUST consume the
     * stream inside a try-with-resources block; otherwise the underlying
     * resources are leaked.
     *
     * @param chatJid the JID identifying the owning chat
     * @return a closeable stream of decoded chat messages
     */
    Stream<ChatMessageInfo> streamChatMessages(Jid chatJid) {
        return streamRange(chatMessages, jidRange(chatJid), PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Inserts or replaces a newsletter message in the
     * {@code newsletter_messages} dbi.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @param info          the message to persist
     */
    void putNewsletterMessage(Jid newsletterJid, NewsletterMessageInfo info) {
        var key = encodePrefixedKey(newsletterJid, encodeServerId(info.serverId()));
        var size = NewsletterMessageInfoSpec.sizeOf(info);
        withWriteTxn(txn -> {
            var value = newsletterMessages.reserve(txn, key, size);
            NewsletterMessageInfoSpec.encode(info, ProtobufOutputStream.toBuffer(value));
            return null;
        });
    }

    /**
     * Looks up a newsletter message by its server id.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @param serverId      the server-assigned monotonic identifier
     * @return the decoded message, or empty if no entry exists
     */
    Optional<NewsletterMessageInfo> getNewsletterMessageByServerId(Jid newsletterJid, int serverId) {
        var key = encodePrefixedKey(newsletterJid, encodeServerId(serverId));
        return withReadTxn(txn -> {
            var buffer = newsletterMessages.get(txn, key);
            return buffer == null
                    ? Optional.<NewsletterMessageInfo>empty()
                    : Optional.of(decodeNewsletterMessage(buffer));
        });
    }

    /**
     * Removes a newsletter message by its server id.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @param serverId      the server-assigned monotonic identifier
     * @return {@code true} if an entry was removed
     */
    boolean removeNewsletterMessageByServerId(Jid newsletterJid, int serverId) {
        var key = encodePrefixedKey(newsletterJid, encodeServerId(serverId));
        return withWriteTxn(txn -> newsletterMessages.delete(txn, key));
    }

    /**
     * Removes every newsletter message belonging to the given
     * newsletter.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the number of entries removed
     */
    int removeNewsletterMessages(Jid newsletterJid) {
        var range = jidRange(newsletterJid);
        return withWriteTxn(txn -> deleteRange(newsletterMessages, txn, range));
    }

    /**
     * Returns the oldest newsletter message (lowest server id) for the
     * given newsletter, or empty if the newsletter is empty.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the oldest message, or empty
     */
    Optional<NewsletterMessageInfo> oldestNewsletterMessage(Jid newsletterJid) {
        return firstInRange(newsletterMessages, jidRange(newsletterJid), PersistentMessageStore::decodeNewsletterMessage);
    }

    /**
     * Returns the newest newsletter message (highest server id) for the
     * given newsletter, or empty if the newsletter is empty.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the newest message, or empty
     */
    Optional<NewsletterMessageInfo> newestNewsletterMessage(Jid newsletterJid) {
        return lastInRange(newsletterMessages, jidRange(newsletterJid), PersistentMessageStore::decodeNewsletterMessage);
    }

    /**
     * Returns the number of newsletter messages stored for the given
     * newsletter.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the number of stored messages
     */
    int countNewsletterMessages(Jid newsletterJid) {
        return countRange(newsletterMessages, jidRange(newsletterJid));
    }

    /**
     * Returns a lazy stream of every newsletter message belonging to the
     * given newsletter, in cursor order.
     *
     * <p>The returned stream owns a read transaction and a cursor that
     * remain open until the stream is closed. Callers MUST consume the
     * stream inside a try-with-resources block.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return a closeable stream of decoded newsletter messages
     */
    Stream<NewsletterMessageInfo> streamNewsletterMessages(Jid newsletterJid) {
        return streamRange(newsletterMessages, jidRange(newsletterJid), PersistentMessageStore::decodeNewsletterMessage);
    }

    /**
     * Inserts or replaces a status-feed message keyed by message id.
     *
     * @param info the message to persist; ignored if it carries no key
     *             id
     */
    void putStatusMessage(ChatMessageInfo info) {
        var msgId = info.key().id().orElse(null);
        if (msgId == null) {
            return;
        }
        var key = encodeKey(msgId.getBytes(StandardCharsets.UTF_8));
        var size = ChatMessageInfoSpec.sizeOf(info);
        withWriteTxn(txn -> {
            var value = statusMessages.reserve(txn, key, size);
            ChatMessageInfoSpec.encode(info, ProtobufOutputStream.toBuffer(value));
            return null;
        });
    }

    /**
     * Looks up a status-feed message by id.
     *
     * @param msgId the message key id
     * @return the decoded message, or empty
     */
    Optional<ChatMessageInfo> getStatusMessage(String msgId) {
        var key = encodeKey(msgId.getBytes(StandardCharsets.UTF_8));
        return withReadTxn(txn -> {
            var buffer = statusMessages.get(txn, key);
            return buffer == null
                    ? Optional.<ChatMessageInfo>empty()
                    : Optional.of(decodeChatMessage(buffer));
        });
    }

    /**
     * Removes a status-feed message by id.
     *
     * @param msgId the message key id
     * @return the previously stored message, or empty if absent
     */
    Optional<ChatMessageInfo> removeStatusMessage(String msgId) {
        var key = encodeKey(msgId.getBytes(StandardCharsets.UTF_8));
        return withWriteTxn(txn -> {
            var existing = statusMessages.get(txn, key);
            if (existing == null) {
                return Optional.<ChatMessageInfo>empty();
            }
            var decoded = decodeChatMessage(existing);
            statusMessages.delete(txn, key);
            return Optional.of(decoded);
        });
    }

    /**
     * Returns the number of status-feed messages stored.
     *
     * @return the entry count
     */
    int countStatusMessages() {
        return withReadTxn(txn -> (int) statusMessages.stat(txn).entries);
    }

    /**
     * Returns a lazy stream of every status-feed message in cursor
     * order.
     *
     * <p>The returned stream owns a read transaction and a cursor that
     * remain open until the stream is closed.
     *
     * @return a closeable stream of decoded status messages
     */
    Stream<ChatMessageInfo> streamStatusMessages() {
        return streamRange(statusMessages, KeyRange.all(), PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Returns the set of distinct chat JIDs that currently have at
     * least one message stored. Used by {@link PersistentStore} on boot
     * to recover orphan chats whose metadata was lost between the LMDB
     * commit and the next metadata snapshot.
     *
     * @return a freshly allocated set of chat JIDs, never {@code null}
     */
    Set<Jid> distinctChatJids() {
        return distinctJids(chatMessages);
    }

    /**
     * Returns the set of distinct newsletter JIDs that currently have
     * at least one message stored. Used by {@link PersistentStore} on
     * boot to recover orphan newsletters.
     *
     * @return a freshly allocated set of newsletter JIDs, never
     *         {@code null}
     */
    Set<Jid> distinctNewsletterJids() {
        return distinctJids(newsletterMessages);
    }

    /**
     * Closes the underlying env, releasing all native resources.
     */
    @Override
    public void close() {
        env.close();
    }

    /**
     * Walks {@code dbi} once and collects the set of distinct JID
     * prefixes (the bytes preceding the {@link #KEY_SEPARATOR}).
     *
     * @param dbi the database to scan
     * @return a set of decoded JIDs
     */
    private Set<Jid> distinctJids(Dbi<ByteBuffer> dbi) {
        return withReadTxn(txn -> {
            var jids = new HashSet<Jid>();
            try (var iter = dbi.iterate(txn, KeyRange.all())) {
                for (var kv : iter) {
                    var key = kv.key();
                    var separator = findSeparator(key);
                    if (separator < 0) {
                        continue;
                    }
                    var jidBytes = new byte[separator];
                    key.duplicate().get(jidBytes);
                    jids.add(Jid.of(new String(jidBytes, StandardCharsets.UTF_8)));
                }
            }
            return jids;
        });
    }

    /**
     * Returns the index of the first {@link #KEY_SEPARATOR} in
     * {@code buffer} relative to its position, or {@code -1} if absent.
     *
     * @param buffer the buffer to scan
     * @return the separator index, or {@code -1}
     */
    private static int findSeparator(ByteBuffer buffer) {
        var view = buffer.duplicate();
        var pos = view.position();
        var limit = view.limit();
        for (var i = pos; i < limit; i++) {
            if (view.get(i) == KEY_SEPARATOR) {
                return i - pos;
            }
        }
        return -1;
    }

    /**
     * Returns the half-open key range covering every entry whose key
     * begins with {@code jid + 0x00}.
     *
     * @param jid the JID prefix
     * @return a half-open {@link KeyRange}
     */
    private static KeyRange<ByteBuffer> jidRange(Jid jid) {
        var jidBytes = jid.toString().getBytes(StandardCharsets.UTF_8);
        var start = ByteBuffer.allocateDirect(jidBytes.length + 1)
                .put(jidBytes)
                .put(KEY_SEPARATOR)
                .flip();
        var stop = ByteBuffer.allocateDirect(jidBytes.length + 1)
                .put(jidBytes)
                .put(KEY_RANGE_END)
                .flip();
        return KeyRange.closedOpen(start, stop);
    }

    /**
     * Encodes a flat key holding only the supplied bytes into a direct
     * buffer ready to be passed to LMDB.
     *
     * @param bytes the key bytes
     * @return a flipped direct buffer containing {@code bytes}
     */
    private static ByteBuffer encodeKey(byte[] bytes) {
        return ByteBuffer.allocateDirect(bytes.length)
                .put(bytes)
                .flip();
    }

    /**
     * Encodes a composite key of the form
     * {@code jidBytes + 0x00 + suffixBytes} into a direct buffer.
     *
     * @param jid    the JID prefix
     * @param suffix the suffix bytes (msgId or encoded serverId)
     * @return a flipped direct buffer containing the composite key
     */
    private static ByteBuffer encodePrefixedKey(Jid jid, byte[] suffix) {
        var jidBytes = jid.toString().getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocateDirect(jidBytes.length + 1 + suffix.length)
                .put(jidBytes)
                .put(KEY_SEPARATOR)
                .put(suffix)
                .flip();
    }

    /**
     * Encodes a {@code serverId} as 4 big-endian bytes so unsigned-byte
     * cursor compare yields the natural numeric order.
     *
     * @param serverId the server-assigned monotonic identifier
     * @return a four-byte big-endian encoding
     */
    private static byte[] encodeServerId(int serverId) {
        return new byte[]{
                (byte) (serverId >>> 24),
                (byte) (serverId >>> 16),
                (byte) (serverId >>> 8),
                (byte) serverId
        };
    }

    /**
     * Decodes an LMDB value buffer into a fully materialised
     * {@link ChatMessageInfo}.
     *
     * <p>Wraps the buffer in {@link CopyingProtobufInputStream} so that
     * every {@code bytes} and {@code string} field is copied to the
     * heap during decode, making the resulting message safe to outlive
     * the source LMDB transaction.
     *
     * @param buffer the LMDB value buffer
     * @return the decoded chat message
     */
    private static ChatMessageInfo decodeChatMessage(ByteBuffer buffer) {
        return ChatMessageInfoSpec.decode(new CopyingProtobufInputStream(buffer));
    }

    /**
     * Decodes an LMDB value buffer into a fully materialised
     * {@link NewsletterMessageInfo}.
     *
     * @param buffer the LMDB value buffer
     * @return the decoded newsletter message
     */
    private static NewsletterMessageInfo decodeNewsletterMessage(ByteBuffer buffer) {
        return NewsletterMessageInfoSpec.decode(new CopyingProtobufInputStream(buffer));
    }

    /**
     * Returns the first decoded entry of {@code dbi} restricted to
     * {@code range}, or empty if the range is empty.
     *
     * @param dbi     the database to query
     * @param range   the key range
     * @param decoder how to decode each value buffer
     * @param <T>     the decoded type
     * @return the first entry, or empty
     */
    private <T> Optional<T> firstInRange(Dbi<ByteBuffer> dbi, KeyRange<ByteBuffer> range, Function<ByteBuffer, T> decoder) {
        return withReadTxn(txn -> {
            try (var iter = dbi.iterate(txn, range)) {
                var it = iter.iterator();
                return it.hasNext()
                        ? Optional.of(decoder.apply(it.next().val()))
                        : Optional.<T>empty();
            }
        });
    }

    /**
     * Returns the last decoded entry of {@code dbi} restricted to
     * {@code range}, or empty if the range is empty.
     *
     * @param dbi     the database to query
     * @param range   the key range
     * @param decoder how to decode each value buffer
     * @param <T>     the decoded type
     * @return the last entry, or empty
     */
    private <T> Optional<T> lastInRange(Dbi<ByteBuffer> dbi, KeyRange<ByteBuffer> range, Function<ByteBuffer, T> decoder) {
        return withReadTxn(txn -> {
            T last = null;
            try (var iter = dbi.iterate(txn, range)) {
                for (var kv : iter) {
                    last = decoder.apply(kv.val());
                }
            }
            return Optional.ofNullable(last);
        });
    }

    /**
     * Returns the number of entries in {@code dbi} restricted to
     * {@code range}.
     *
     * @param dbi   the database to query
     * @param range the key range
     * @return the entry count
     */
    private int countRange(Dbi<ByteBuffer> dbi, KeyRange<ByteBuffer> range) {
        return withReadTxn(txn -> {
            var count = 0;
            try (var iter = dbi.iterate(txn, range)) {
                for (var ignored : iter) {
                    count++;
                }
            }
            return count;
        });
    }

    /**
     * Deletes every entry of {@code dbi} that lies within {@code range}
     * inside the given write transaction.
     *
     * @param dbi   the database to mutate
     * @param txn   the active write transaction
     * @param range the key range to delete
     * @return the number of entries removed
     */
    private static int deleteRange(Dbi<ByteBuffer> dbi, Txn<ByteBuffer> txn, KeyRange<ByteBuffer> range) {
        var removed = 0;
        try (var cursor = dbi.openCursor(txn)) {
            try (var iter = dbi.iterate(txn, range)) {
                for (var kv : iter) {
                    if (cursor.get(kv.key(), GetOp.MDB_SET)) {
                        cursor.delete();
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Wraps a {@code dbi} cursor walk into a closeable {@link Stream}
     * whose {@link Stream#close()} hook closes both the cursor iterable
     * and the read transaction.
     *
     * @param dbi     the database to query
     * @param range   the key range
     * @param decoder how to decode each value buffer
     * @param <T>     the decoded type
     * @return a closeable stream of decoded entries
     */
    private <T> Stream<T> streamRange(Dbi<ByteBuffer> dbi, KeyRange<ByteBuffer> range, Function<ByteBuffer, T> decoder) {
        var txn = env.txnRead();
        CursorIterable<ByteBuffer> iter = null;
        try {
            iter = dbi.iterate(txn, range);
            var iterRef = iter;
            var spliterator = Spliterators.spliteratorUnknownSize(iter.iterator(), Spliterator.ORDERED | Spliterator.NONNULL);
            return StreamSupport.stream(spliterator, false)
                    .map(kv -> decoder.apply(kv.val()))
                    .onClose(() -> {
                        try {
                            iterRef.close();
                        } finally {
                            txn.close();
                        }
                    });
        } catch (RuntimeException error) {
            if (iter != null) {
                try {
                    iter.close();
                } catch (RuntimeException suppressed) {
                    error.addSuppressed(suppressed);
                }
            }
            txn.close();
            throw error;
        }
    }

    /**
     * Runs {@code body} inside a fresh read transaction, returning its
     * result. The transaction is always closed before this method
     * returns.
     *
     * @param body the read-only operation
     * @param <T>  the result type
     * @return the value produced by {@code body}
     */
    private <T> T withReadTxn(Function<Txn<ByteBuffer>, T> body) {
        try (var txn = env.txnRead()) {
            return body.apply(txn);
        }
    }

    /**
     * Runs {@code body} inside a fresh write transaction. On success
     * the transaction is committed; on {@link Env.MapFullException} the
     * env is doubled and the body is retried once. If the retry also
     * trips {@link Env.MapFullException} that exception is allowed to
     * propagate.
     *
     * @param body the write operation
     * @param <T>  the result type
     * @return the value produced by {@code body}
     */
    private <T> T withWriteTxn(Function<Txn<ByteBuffer>, T> body) {
        try (var txn = env.txnWrite()) {
            try {
                var result = body.apply(txn);
                txn.commit();
                return result;
            } catch (Env.MapFullException _) {
                txn.abort();
            }
        }
        growMap();
        try (var txn = env.txnWrite()) {
            var result = body.apply(txn);
            txn.commit();
            return result;
        }
    }

    /**
     * Doubles the env map size under {@link #growLock} so concurrent
     * writers serialise on the resize.
     */
    private void growMap() {
        synchronized (growLock) {
            mapSize *= 2;
            env.setMapSize(mapSize);
        }
    }

    /**
     * {@link ProtobufInputStream} backed by an mmap-resident
     * {@link ByteBuffer} that copies every {@code bytes} and
     * {@code string} field to the heap at decode time.
     *
     * <p>The stock {@code ProtobufInputStream.fromBuffer(...)}
     * implementation returns each {@code bytes} field as a
     * {@link ByteBuffer#slice slice} of the source buffer, sharing
     * memory with it. When the source is an LMDB-mapped page the slice
     * becomes invalid the moment the read transaction closes, which
     * would turn any later access from {@code SIGSEGV}-fatal. Strings
     * are already copied internally by the stock implementation, but
     * {@code bytes} fields are not.
     *
     * <p>This subclass eliminates that footgun by overriding
     * {@link #readBytes(int)} (and, redundantly for symmetry,
     * {@link #readString(int)}) to allocate a fresh heap array,
     * draining {@code size} bytes out of the source buffer into it.
     * Decoded messages can then escape the read transaction safely —
     * which is required by every accessor on this store, since they
     * all return decoded values to callers that hold them past the
     * {@code withReadTxn} boundary.
     */
    private static final class CopyingProtobufInputStream extends ProtobufInputStream {
        /**
         * The source buffer; advances as bytes are consumed.
         */
        private final ByteBuffer buffer;

        /**
         * Constructs a stream over {@code buffer}; reads start at the
         * buffer's current position and advance it.
         *
         * @param buffer the source buffer
         */
        private CopyingProtobufInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        /**
         * Returns the next byte and advances the position by one.
         *
         * @return the next byte
         */
        @Override
        protected byte readByte() {
            return buffer.get();
        }

        /**
         * Reads {@code size} bytes into a fresh heap array and wraps
         * the array as a {@link ByteBuffer} so that the resulting
         * value is independent of the source buffer.
         *
         * @param size the number of bytes to read
         * @return a heap-backed buffer holding the copied bytes
         */
        @Override
        protected ByteBuffer readBytes(int size) {
            var copy = new byte[size];
            buffer.get(copy);
            return ByteBuffer.wrap(copy);
        }

        /**
         * Reads {@code size} bytes into a fresh heap array and wraps
         * it in a {@link ProtobufString.Lazy} backed by that array.
         *
         * @param size the number of bytes to read
         * @return a heap-backed lazy string
         */
        @Override
        protected ProtobufString.Lazy readString(int size) {
            var copy = new byte[size];
            buffer.get(copy);
            return ProtobufString.lazy(copy);
        }

        /**
         * Records the current buffer position so a subsequent
         * {@link #rewind()} can restore it.
         */
        @Override
        protected void mark() {
            buffer.mark();
        }

        /**
         * Restores the buffer position to the most recently
         * {@linkplain #mark() marked} value.
         */
        @Override
        protected void rewind() {
            buffer.reset();
        }

        /**
         * Returns whether the source buffer has any bytes left to
         * read.
         *
         * @return {@code true} when no bytes remain
         */
        @Override
        protected boolean isFinished() {
            return !buffer.hasRemaining();
        }

        /**
         * Returns a sub-stream covering the next {@code size} bytes of
         * the source buffer, advancing the parent position past them.
         *
         * <p>The sub-stream is backed by a slice of the source buffer
         * which is safe even when the slice points into mmap memory:
         * every read through the sub-stream goes through this
         * subclass's overrides and copies into the heap.
         *
         * @param size the number of bytes the sub-stream may read
         * @return a sub-stream over the requested slice
         */
        @Override
        protected ProtobufInputStream subStream(int size) {
            var position = buffer.position();
            var slice = buffer.slice(position, size);
            buffer.position(position + size);
            return new CopyingProtobufInputStream(slice);
        }

        /**
         * Releases no resources; the source buffer's lifetime is
         * owned by the surrounding LMDB read transaction.
         */
        @Override
        public void close() {

        }
    }
}
