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
 * The package-private LMDB facade that backs every message accessor on {@link PersistentStore}.
 *
 * <p>The env rooted at {@code <sessionDirectory>/messages.lmdb} hosts three named databases:
 * <ul>
 *   <li>{@code chat_messages} keyed by {@code chatJid + 0x00 + msgId}, scanned per chat through
 *       the half-open range {@code [chatJid + 0x00, chatJid + 0x01)};</li>
 *   <li>{@code newsletter_messages} keyed by {@code newsletterJid + 0x00 + serverId(BE)}, with
 *       cursor first/last yielding oldest/newest directly because {@code serverId} is monotonic
 *       per newsletter;</li>
 *   <li>{@code status_messages} flat, keyed by {@code msgId}, holding the global status feed.</li>
 * </ul>
 *
 * @apiNote
 * Only {@link PersistentStore} and its {@link PersistentChat} / {@link PersistentNewsletter}
 * subtypes consume this facade. The instance is owned by the parent store and shut down through
 * {@link PersistentStore#close()} or {@link PersistentStore#delete()}.
 *
 * @implNote
 * This implementation eagerly decodes each LMDB value into heap-resident objects before the read
 * transaction closes (see {@link CopyingProtobufInputStream}) so callers never hold references to
 * mmap'd memory that may be invalidated by a concurrent writer. Write transactions are short-lived
 * and retry once on {@link Env.MapFullException} after a {@link #growMap() doubling resize} guarded
 * by {@link #growLock}. Stream-returning methods open a long-lived read transaction tied to the
 * returned {@link Stream#close()} hook; callers MUST consume the stream inside a try-with-resources
 * block to avoid leaking a slot in LMDB's reader table.
 *
 * @see PersistentStore
 */
final class PersistentMessageStore implements AutoCloseable {
    /**
     * The byte separating the JID prefix from the message-id suffix in composite keys.
     *
     * @implNote
     * This implementation relies on {@code 0x00} being absent from both JID string forms and
     * base64-shaped message ids, so the separator is unambiguous.
     */
    private static final byte KEY_SEPARATOR = 0x00;

    /**
     * The sentinel byte that terminates a per-JID cursor range.
     *
     * @implNote
     * This implementation uses {@code 0x01} (one greater than {@link #KEY_SEPARATOR}) so the
     * half-open range {@code [jid + 0x00, jid + 0x01)} captures every key whose prefix matches the
     * JID exactly without spilling into the next JID's keyspace.
     */
    private static final byte KEY_RANGE_END = 0x01;

    /**
     * The maximum number of LMDB reader slots provisioned on the env.
     *
     * @implNote
     * This implementation provisions 512 slots, comfortably above the realistic concurrent ceiling
     * under virtual threads (dozens) without bloating the lock table.
     */
    private static final int MAX_READERS = 512;

    /**
     * The number of named databases opened inside the env.
     */
    private static final int MAX_DBS = 3;

    /**
     * The name of the chat-message database.
     */
    private static final String CHAT_MESSAGES_DBI = "chat_messages";

    /**
     * The name of the newsletter-message database.
     */
    private static final String NEWSLETTER_MESSAGES_DBI = "newsletter_messages";

    /**
     * The name of the status-feed database.
     */
    private static final String STATUS_MESSAGES_DBI = "status_messages";

    /**
     * The underlying LMDB environment.
     */
    private final Env<ByteBuffer> env;

    /**
     * The handle to the {@value #CHAT_MESSAGES_DBI} database.
     */
    private final Dbi<ByteBuffer> chatMessages;

    /**
     * The handle to the {@value #NEWSLETTER_MESSAGES_DBI} database.
     */
    private final Dbi<ByteBuffer> newsletterMessages;

    /**
     * The handle to the {@value #STATUS_MESSAGES_DBI} database.
     */
    private final Dbi<ByteBuffer> statusMessages;

    /**
     * The current configured map size in bytes.
     *
     * @implNote
     * This implementation marks the field {@code volatile} so concurrent writers observe the most
     * recent resize before retrying. Mutations happen only under {@link #growLock}.
     */
    private volatile long mapSize;

    /**
     * The mutex guarding {@link #growMap()} so concurrent writers serialise on the resize.
     */
    private final Object growLock;

    /**
     * Constructs a facade around an already-opened env and its three database handles.
     *
     * @apiNote
     * This constructor is private; instances are produced through {@link #open(Path, long)}.
     *
     * @param env                the LMDB environment
     * @param chatMessages       the chat-message dbi
     * @param newsletterMessages the newsletter-message dbi
     * @param statusMessages     the status-feed dbi
     * @param initialMapSize     the initial map size in bytes, retained for grow-on-full bookkeeping
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
     * Opens or creates the LMDB env under {@code envDirectory} and returns a facade ready to serve
     * chat, newsletter and status reads and writes.
     *
     * @apiNote
     * Invoked by {@link PersistentStoreFactory} after the metadata snapshot is loaded or a fresh
     * store is built. The directory is created if it does not already exist.
     *
     * @implNote
     * This implementation provisions {@value #MAX_READERS} reader slots and {@value #MAX_DBS}
     * named databases on the env. The three dbis are opened with {@link DbiFlags#MDB_CREATE} so
     * cold starts succeed without a separate bootstrap step.
     *
     * @param envDirectory   the directory that will host {@code data.mdb} and {@code lock.mdb};
     *                       created if it does not exist
     * @param initialMapSize the maximum size in bytes the env file may grow to before
     *                       {@link Env.MapFullException} triggers a doubling resize
     * @return a fully initialised facade
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
     * Inserts or replaces a chat message in the {@code chat_messages} dbi.
     *
     * @apiNote
     * Called from {@link PersistentChat#addMessage(ChatMessageInfo)}. A message with no key id is
     * silently dropped because the dbi key requires one.
     *
     * @implNote
     * This implementation uses {@link Dbi#reserve(Txn, Object, int)} to write the protobuf
     * payload directly into LMDB-allocated space, avoiding an intermediate heap buffer.
     *
     * @param chatJid the JID identifying the owning chat
     * @param info    the message to persist
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
     * Returns the chat message under the composite key {@code chatJid + 0x00 + msgId}, or empty.
     *
     * @apiNote
     * Called from {@link PersistentChat#getMessageById(String)}.
     *
     * @implNote
     * This implementation decodes the value via {@link CopyingProtobufInputStream} so the result
     * outlives the surrounding read transaction safely.
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
     * Removes the chat message under the composite key {@code chatJid + 0x00 + msgId}.
     *
     * @apiNote
     * Called from {@link PersistentChat#removeMessage(String)}.
     *
     * @param chatJid the JID identifying the owning chat
     * @param msgId   the message key id
     * @return {@code true} if an entry was removed, {@code false} if no such entry existed
     */
    boolean removeChatMessage(Jid chatJid, String msgId) {
        var key = encodePrefixedKey(chatJid, msgId.getBytes(StandardCharsets.UTF_8));
        return withWriteTxn(txn -> chatMessages.delete(txn, key));
    }

    /**
     * Removes every chat message stored for the given chat.
     *
     * @apiNote
     * Called from {@link PersistentChat#removeMessages()} and from
     * {@link PersistentStore#removeChat} so that a removed chat's body history does not outlive
     * its metadata.
     *
     * @implNote
     * This implementation walks the per-JID range under a single write transaction; see
     * {@link #deleteRange(Dbi, Txn, KeyRange)}.
     *
     * @param chatJid the JID identifying the owning chat
     * @return the number of entries removed
     */
    int removeChatMessages(Jid chatJid) {
        var range = jidRange(chatJid);
        return withWriteTxn(txn -> deleteRange(chatMessages, txn, range));
    }

    /**
     * Returns the oldest chat message stored for the given chat in cursor order, or empty.
     *
     * @apiNote
     * Called from {@link PersistentChat#oldestMessage()}.
     *
     * @param chatJid the JID identifying the owning chat
     * @return the first message in the per-JID range, or empty
     */
    Optional<ChatMessageInfo> oldestChatMessage(Jid chatJid) {
        return firstInRange(chatMessages, jidRange(chatJid), PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Returns the newest chat message stored for the given chat in cursor order, or empty.
     *
     * @apiNote
     * Called from {@link PersistentChat#newestMessage()}.
     *
     * @param chatJid the JID identifying the owning chat
     * @return the last message in the per-JID range, or empty
     */
    Optional<ChatMessageInfo> newestChatMessage(Jid chatJid) {
        return lastInRange(chatMessages, jidRange(chatJid), PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Returns the number of chat messages stored for the given chat.
     *
     * @apiNote
     * Called once by {@link PersistentChat#attach(PersistentMessageStore)} to seed the per-chat
     * cached counter; subsequent {@link PersistentChat#messageCount()} calls answer from the cache.
     *
     * @implNote
     * This implementation walks the cursor range and is {@code O(n)} in the chat size; callers
     * that need the value frequently must cache it client-side.
     *
     * @param chatJid the JID identifying the owning chat
     * @return the number of stored messages
     */
    int countChatMessages(Jid chatJid) {
        return countRange(chatMessages, jidRange(chatJid));
    }

    /**
     * Returns a lazy stream of every chat message stored for the given chat, in cursor order.
     *
     * @apiNote
     * The returned stream owns a read transaction and a cursor that remain open until the stream
     * is closed. Callers MUST consume it inside a try-with-resources block; otherwise the
     * underlying read transaction stays in LMDB's reader table until garbage collection.
     *
     * @param chatJid the JID identifying the owning chat
     * @return a closeable stream of decoded chat messages
     */
    Stream<ChatMessageInfo> streamChatMessages(Jid chatJid) {
        return streamRange(chatMessages, jidRange(chatJid), PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Inserts or replaces a newsletter message in the {@code newsletter_messages} dbi.
     *
     * @apiNote
     * Called from {@link PersistentNewsletter#addMessage(NewsletterMessageInfo)}. The key is
     * {@code newsletterJid + 0x00 + serverId(BE)} so cursor first/last yields oldest/newest with
     * no extra index.
     *
     * @implNote
     * This implementation uses {@link Dbi#reserve(Txn, Object, int)} to encode the payload
     * directly into LMDB-allocated space.
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
     * Returns the newsletter message under the composite key
     * {@code newsletterJid + 0x00 + serverId(BE)}, or empty.
     *
     * @apiNote
     * Called from {@link PersistentNewsletter#getMessageById(String)} and from
     * {@link PersistentStore#findMessageById(com.github.auties00.cobalt.model.newsletter.Newsletter, String)}
     * as the fast path before the per-newsletter scan fallback.
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
     * Removes the newsletter message identified by its server id.
     *
     * @apiNote
     * Called from {@link PersistentNewsletter#removeMessage(String)} after the caller parses the
     * message-id string into a numeric server id.
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
     * Removes every newsletter message stored for the given newsletter.
     *
     * @apiNote
     * Called from {@link PersistentNewsletter#removeMessages()} and from
     * {@link PersistentStore#removeNewsletter} so a removed newsletter's body history does not
     * outlive its metadata.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the number of entries removed
     */
    int removeNewsletterMessages(Jid newsletterJid) {
        var range = jidRange(newsletterJid);
        return withWriteTxn(txn -> deleteRange(newsletterMessages, txn, range));
    }

    /**
     * Returns the oldest newsletter message (lowest {@code serverId}) for the given newsletter,
     * or empty.
     *
     * @apiNote
     * Called from {@link PersistentNewsletter#oldestMessage()}.
     *
     * @implNote
     * This implementation relies on the big-endian {@code serverId} encoding so memcmp-ordered
     * LMDB cursor traversal aligns with the natural numeric order.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the oldest message, or empty
     */
    Optional<NewsletterMessageInfo> oldestNewsletterMessage(Jid newsletterJid) {
        return firstInRange(newsletterMessages, jidRange(newsletterJid), PersistentMessageStore::decodeNewsletterMessage);
    }

    /**
     * Returns the newest newsletter message (highest {@code serverId}) for the given newsletter,
     * or empty.
     *
     * @apiNote
     * Called from {@link PersistentNewsletter#newestMessage()}.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the newest message, or empty
     */
    Optional<NewsletterMessageInfo> newestNewsletterMessage(Jid newsletterJid) {
        return lastInRange(newsletterMessages, jidRange(newsletterJid), PersistentMessageStore::decodeNewsletterMessage);
    }

    /**
     * Returns the number of newsletter messages stored for the given newsletter.
     *
     * @apiNote
     * Called once by {@link PersistentNewsletter#attach(PersistentMessageStore)} to seed the
     * cached counter.
     *
     * @implNote
     * This implementation walks the cursor range; it is {@code O(n)} in the newsletter size.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the number of stored messages
     */
    int countNewsletterMessages(Jid newsletterJid) {
        return countRange(newsletterMessages, jidRange(newsletterJid));
    }

    /**
     * Returns a lazy stream of every newsletter message stored for the given newsletter, in
     * cursor order.
     *
     * @apiNote
     * The returned stream owns a read transaction and a cursor that remain open until the stream
     * is closed; consume inside a try-with-resources block.
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
     * @apiNote
     * Called from {@link PersistentStore#addStatus(ChatMessageInfo)}. A message with no key id
     * is silently dropped because the dbi key requires one.
     *
     * @param info the message to persist
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
     * Returns the status-feed message under the flat key {@code msgId}, or empty.
     *
     * @apiNote
     * Called from {@link PersistentStore#findStatusById(String)} and as a status-broadcast branch
     * of {@link PersistentStore#findMessageById}.
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
     * Removes the status-feed message under {@code msgId} and returns its previous value.
     *
     * @apiNote
     * Called from {@link PersistentStore#removeStatus(String)}.
     *
     * @implNote
     * This implementation reads the value first inside the write transaction so the caller can
     * observe what was removed; LMDB's {@link Dbi#delete(Txn, Object)} does not return the prior
     * value.
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
     * @apiNote
     * No caller currently consumes this directly; retained for parity with the chat and
     * newsletter accessors.
     *
     * @implNote
     * This implementation queries {@link Dbi#stat(Txn)} which returns the entry count in
     * {@code O(1)} from LMDB's internal counter, unlike the per-JID counts that need a cursor
     * walk.
     *
     * @return the entry count
     */
    int countStatusMessages() {
        return withReadTxn(txn -> (int) statusMessages.stat(txn).entries);
    }

    /**
     * Returns a lazy stream of every status-feed message in cursor order.
     *
     * @apiNote
     * Called from {@link PersistentStore#status()}; the stream is consumed inside a
     * try-with-resources block and collected into a list.
     *
     * @return a closeable stream of decoded status messages
     */
    Stream<ChatMessageInfo> streamStatusMessages() {
        return streamRange(statusMessages, KeyRange.all(), PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Returns the set of distinct chat JIDs that currently have at least one message stored.
     *
     * @apiNote
     * Called by {@link PersistentStoreFactory} on boot to recover orphan chats whose messages
     * landed in LMDB but whose metadata never reached the next {@code store.proto} snapshot.
     *
     * @return a freshly allocated set of chat JIDs, never {@code null}
     */
    Set<Jid> distinctChatJids() {
        return distinctJids(chatMessages);
    }

    /**
     * Returns the set of distinct newsletter JIDs that currently have at least one message stored.
     *
     * @apiNote
     * Called by {@link PersistentStoreFactory} on boot to recover orphan newsletters whose
     * messages landed in LMDB but whose metadata never reached the next snapshot.
     *
     * @return a freshly allocated set of newsletter JIDs, never {@code null}
     */
    Set<Jid> distinctNewsletterJids() {
        return distinctJids(newsletterMessages);
    }

    /**
     * Closes the underlying env, releasing all native resources.
     *
     * @apiNote
     * Called from {@link PersistentStore#close()} and {@link PersistentStore#delete()}. After
     * this call every accessor will fail; the parent store nulls its reference so a second
     * close is a no-op.
     */
    @Override
    public void close() {
        env.close();
    }

    /**
     * Walks {@code dbi} once and collects the distinct JID prefixes that precede the first
     * {@link #KEY_SEPARATOR} in each key.
     *
     * @apiNote
     * Internal helper for the orphan-recovery accessors {@link #distinctChatJids()} and
     * {@link #distinctNewsletterJids()}.
     *
     * @implNote
     * This implementation duplicates each key buffer before copying the prefix so the iteration
     * does not disturb LMDB's internal position state.
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
     * Returns the offset of the first {@link #KEY_SEPARATOR} in {@code buffer} relative to its
     * position, or {@code -1} if absent.
     *
     * @apiNote
     * Internal helper for {@link #distinctJids(Dbi)}.
     *
     * @param buffer the buffer to scan
     * @return the separator offset, or {@code -1}
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
     * Returns the half-open key range covering every entry whose key begins with
     * {@code jid + 0x00}.
     *
     * @apiNote
     * Internal helper for per-JID cursor operations.
     *
     * @implNote
     * This implementation allocates both endpoints as direct buffers because LMDB's JNI binding
     * requires direct memory for key arguments.
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
     * Encodes a flat key holding only the supplied bytes into a direct buffer ready for LMDB.
     *
     * @apiNote
     * Internal helper for the status-feed dbi which has no JID prefix.
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
     * Encodes a composite key of the form {@code jidBytes + 0x00 + suffixBytes} into a direct
     * buffer.
     *
     * @apiNote
     * Internal helper for the chat and newsletter dbis.
     *
     * @param jid    the JID prefix
     * @param suffix the suffix bytes (a message id or an encoded server id)
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
     * Encodes {@code serverId} as four big-endian bytes.
     *
     * @apiNote
     * Used as the suffix of newsletter-message keys.
     *
     * @implNote
     * This implementation emits big-endian so unsigned-byte LMDB cursor compare yields the natural
     * numeric order; little-endian would invert the cursor walk.
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
     * Decodes an LMDB value buffer into a fully heap-resident {@link ChatMessageInfo}.
     *
     * @apiNote
     * Internal helper for every chat and status read.
     *
     * @implNote
     * This implementation wraps the buffer in {@link CopyingProtobufInputStream} so every
     * {@code bytes} field is copied to the heap during decode, making the result safe to outlive
     * the source LMDB transaction.
     *
     * @param buffer the LMDB value buffer
     * @return the decoded chat message
     */
    private static ChatMessageInfo decodeChatMessage(ByteBuffer buffer) {
        return ChatMessageInfoSpec.decode(new CopyingProtobufInputStream(buffer));
    }

    /**
     * Decodes an LMDB value buffer into a fully heap-resident {@link NewsletterMessageInfo}.
     *
     * @apiNote
     * Internal helper for every newsletter read.
     *
     * @param buffer the LMDB value buffer
     * @return the decoded newsletter message
     */
    private static NewsletterMessageInfo decodeNewsletterMessage(ByteBuffer buffer) {
        return NewsletterMessageInfoSpec.decode(new CopyingProtobufInputStream(buffer));
    }

    /**
     * Returns the first decoded entry of {@code dbi} restricted to {@code range}, or empty.
     *
     * @apiNote
     * Internal helper for {@link #oldestChatMessage(Jid)} and
     * {@link #oldestNewsletterMessage(Jid)}.
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
     * Returns the last decoded entry of {@code dbi} restricted to {@code range}, or empty.
     *
     * @apiNote
     * Internal helper for {@link #newestChatMessage(Jid)} and
     * {@link #newestNewsletterMessage(Jid)}.
     *
     * @implNote
     * This implementation walks forward and overwrites a running pointer because LMDB Java does
     * not expose a reverse iterator on {@link KeyRange#closedOpen(Object, Object)}.
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
     * Returns the number of entries in {@code dbi} restricted to {@code range}.
     *
     * @apiNote
     * Internal helper for {@link #countChatMessages(Jid)} and
     * {@link #countNewsletterMessages(Jid)}.
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
     * Deletes every entry of {@code dbi} that lies within {@code range}, inside the given write
     * transaction.
     *
     * @apiNote
     * Internal helper for {@link #removeChatMessages(Jid)} and
     * {@link #removeNewsletterMessages(Jid)}.
     *
     * @implNote
     * This implementation positions a cursor on each iterated key via {@link GetOp#MDB_SET} and
     * calls {@link org.lmdbjava.Cursor#delete} on the cursor; deleting through the iterator's own
     * key buffer would race with LMDB's internal cursor invalidation.
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
     * Wraps a {@code dbi} cursor walk into a closeable {@link Stream} whose
     * {@link Stream#close()} hook closes both the cursor iterable and the read transaction.
     *
     * @apiNote
     * Internal helper for {@link #streamChatMessages(Jid)},
     * {@link #streamNewsletterMessages(Jid)} and {@link #streamStatusMessages()}.
     *
     * @implNote
     * This implementation aborts the read transaction and rethrows if cursor allocation fails
     * after the txn is open but before the stream takes ownership; without that path a failed
     * open would leak the txn.
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
     * Runs {@code body} inside a fresh read transaction and returns its result.
     *
     * @apiNote
     * Internal helper. The transaction is always closed before this method returns; the body must
     * not retain the transaction or any buffers obtained from it.
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
     * Runs {@code body} inside a fresh write transaction, committing on success and retrying
     * once after a map resize on {@link Env.MapFullException}.
     *
     * @apiNote
     * Internal helper for every mutating accessor.
     *
     * @implNote
     * This implementation aborts the first transaction on overflow, doubles the map under
     * {@link #growLock}, then opens a second transaction; if the retry trips
     * {@link Env.MapFullException} again the exception propagates unchanged because the body's
     * effective payload exceeds the doubled bound.
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
     * Doubles the env map size under {@link #growLock} so concurrent writers serialise on the
     * resize.
     *
     * @apiNote
     * Internal helper; the only caller is {@link #withWriteTxn(Function)} after it observes
     * {@link Env.MapFullException}.
     */
    private void growMap() {
        synchronized (growLock) {
            mapSize *= 2;
            env.setMapSize(mapSize);
        }
    }

    /**
     * The {@link ProtobufInputStream} subclass that copies every {@code bytes} and {@code string}
     * field to the heap during decode, making decoded messages safe to outlive the source LMDB
     * read transaction.
     *
     * @apiNote
     * Used by {@link #decodeChatMessage(ByteBuffer)} and
     * {@link #decodeNewsletterMessage(ByteBuffer)}; never visible to callers of this facade.
     *
     * @implNote
     * The stock {@code ProtobufInputStream.fromBuffer(...)} returns each {@code bytes} field as a
     * {@link ByteBuffer#slice slice} of the source buffer, sharing memory. When the source is an
     * LMDB-mapped page that slice becomes invalid the moment the read transaction closes, which
     * would turn any later access into a SIGSEGV. This implementation overrides
     * {@link #readBytes(int)} and {@link #readString(int)} to allocate fresh heap arrays so the
     * decoded message escapes the read transaction safely. Strings are technically already copied
     * by the stock implementation but the override is retained for symmetry.
     */
    private static final class CopyingProtobufInputStream extends ProtobufInputStream {
        /**
         * The source buffer; advances as bytes are consumed.
         */
        private final ByteBuffer buffer;

        /**
         * Constructs a stream over {@code buffer}.
         *
         * @apiNote
         * Reads start at the buffer's current position and advance it.
         *
         * @param buffer the source buffer
         */
        private CopyingProtobufInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation delegates to {@link ByteBuffer#get()} which advances the position
         * by one.
         */
        @Override
        protected byte readByte() {
            return buffer.get();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation copies {@code size} bytes out of the source buffer into a fresh
         * heap array and wraps it as a {@link ByteBuffer}, so the returned value is independent
         * of the LMDB-mapped source.
         */
        @Override
        protected ByteBuffer readBytes(int size) {
            var copy = new byte[size];
            buffer.get(copy);
            return ByteBuffer.wrap(copy);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation copies {@code size} bytes out of the source buffer into a fresh
         * heap array and wraps it as a {@link ProtobufString.Lazy}.
         */
        @Override
        protected ProtobufString.Lazy readString(int size) {
            var copy = new byte[size];
            buffer.get(copy);
            return ProtobufString.lazy(copy);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation delegates to {@link ByteBuffer#mark()}.
         */
        @Override
        protected void mark() {
            buffer.mark();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation delegates to {@link ByteBuffer#reset()}.
         */
        @Override
        protected void rewind() {
            buffer.reset();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation returns the negation of {@link ByteBuffer#hasRemaining()}.
         */
        @Override
        protected boolean isFinished() {
            return !buffer.hasRemaining();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation slices the next {@code size} bytes and wraps the slice in a fresh
         * {@link CopyingProtobufInputStream}. The slice is safe even when it points into
         * mmap memory: every read through the sub-stream goes through this subclass's overrides
         * and copies into the heap before the slice can be invalidated.
         */
        @Override
        protected ProtobufInputStream subStream(int size) {
            var position = buffer.position();
            var slice = buffer.slice(position, size);
            buffer.position(position + size);
            return new CopyingProtobufInputStream(slice);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation does nothing; the source buffer's lifetime is owned by the
         * surrounding LMDB read transaction, which closes it through the stream's
         * {@link Stream#close()} hook or through the body of {@link #withReadTxn(Function)}.
         */
        @Override
        public void close() {

        }
    }
}
