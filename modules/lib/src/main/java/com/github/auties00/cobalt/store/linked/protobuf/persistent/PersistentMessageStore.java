package com.github.auties00.cobalt.store.linked.protobuf.persistent;

import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoSpec;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfoSpec;
import com.github.auties00.cobalt.store.linked.protobuf.persistent.mdbx.MdbxWriteQueue;
import com.github.auties00.cobalt.store.linked.protobuf.persistent.mdbx.MdbxWriteOp;
import com.github.auties00.cobalt.store.linked.protobuf.persistent.mdbx.bindings.MDBX_stat;
import com.github.auties00.cobalt.store.linked.protobuf.persistent.mdbx.bindings.MDBX_val;
import com.github.auties00.cobalt.store.linked.protobuf.persistent.mdbx.bindings.Mdbx;
import com.github.auties00.cobalt.util.NativeLibLoader;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The package-private libmdbx facade that backs every message accessor on {@link PersistentStore}.
 *
 * <p>The env rooted at {@code <sessionDirectory>/messages.mdbx} hosts three named databases:
 * <ul>
 *   <li>{@code chat_messages} keyed by {@code chatJid + 0x00 + msgId}, scanned per chat through the
 *       half-open range {@code [chatJid + 0x00, chatJid + 0x01)};</li>
 *   <li>{@code newsletter_messages} keyed by {@code newsletterJid + 0x00 + serverId(BE)}, with cursor
 *       first/last yielding oldest/newest directly because {@code serverId} is monotonic per
 *       newsletter;</li>
 *   <li>{@code status_messages} flat, keyed by {@code msgId}, holding the global status feed.</li>
 * </ul>
 *
 * @apiNote
 * Only {@link PersistentStore} and its {@link PersistentChat} / {@link PersistentNewsletter} subtypes
 * consume this facade. The instance is owned by the parent store and shut down through
 * {@link PersistentStore#close()} or {@link PersistentStore#delete()}.
 *
 * @implNote
 * This implementation binds libmdbx through the Foreign Function and Memory API (the generated
 * {@link Mdbx} bindings) rather than a JNI/JNR wrapper. The env is opened with
 * {@code MDBX_NOSTICKYTHREADS} so read transactions remain valid across virtual-thread carrier
 * migration, and {@code MDBX_LIFORECLAIM} for write-back-cache friendliness. Reads run on the calling
 * thread under a short read transaction; values are eagerly copied to the heap during decode (see
 * {@link CopyingProtobufInputStream}) so callers never hold references to mmap'd memory invalidated by
 * a later writer. Every mutation is funnelled through a single dedicated platform writer thread that
 * batches queued {@link MdbxWriteOp ops} into one transaction and pays a single durable {@code fsync} per
 * batch; this both honours libmdbx's rule that a write transaction must begin and commit on the same
 * operating-system thread and keeps the blocking {@code fsync} off the virtual-thread carrier pool.
 * The map auto-grows via {@code mdbx_env_set_geometry} up to its upper bound, so there is no
 * grow-on-full retry logic.
 *
 * @see PersistentStore
 */
final class PersistentMessageStore implements AutoCloseable {
    /**
     * The platform-agnostic native library name resolved by {@link NativeLibLoader}.
     *
     * <p>Every native dependency, libmdbx included, is linked into one combined {@code cobalt-native}
     * library, so this is the same name every other native consumer loads.
     */
    private static final String LIBRARY_NAME = "cobalt-native";

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
     * This implementation uses {@code 0x01} (one greater than {@link #KEY_SEPARATOR}) so the half-open
     * range {@code [jid + 0x00, jid + 0x01)} captures every key whose prefix matches the JID exactly
     * without spilling into the next JID's keyspace.
     */
    private static final byte KEY_RANGE_END = 0x01;

    /**
     * The maximum number of libmdbx reader slots provisioned on the env.
     *
     * @implNote
     * This implementation provisions 1024 slots: under {@code MDBX_NOSTICKYTHREADS} a slot is held per
     * open read transaction (notably the long-lived stream transactions) rather than per thread, and
     * virtual threads can fan out many concurrent readers. Each slot costs only a few bytes in the
     * lock file.
     */
    private static final long MAX_READERS = 1024;

    /**
     * The number of named databases opened inside the env.
     */
    private static final long MAX_DBS = 3;

    /**
     * The amount, in bytes, by which the env file grows when it runs out of room.
     */
    private static final long GROWTH_STEP = 16L * 1024 * 1024;

    /**
     * The free-space threshold, in bytes, past which libmdbx truncates the env file back down.
     */
    private static final long SHRINK_THRESHOLD = 32L * 1024 * 1024;

    /**
     * The number of write ops per queue chunk, and therefore the maximum size of one committed batch.
     */
    private static final int BATCH_CAPACITY = 256;

    /**
     * The backpressure ceiling for the write queue.
     *
     * @implNote
     * This implementation sets a high ceiling because each producer blocks on its own op's completion
     * before submitting another, so the live queue depth never exceeds the number of concurrent
     * producers.
     */
    private static final int MAX_PENDING = 1 << 16;

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

    private static final int RC_SUCCESS = Mdbx.MDBX_SUCCESS();
    private static final int RC_NOTFOUND = Mdbx.MDBX_NOTFOUND();
    private static final int RC_TXN_FULL = Mdbx.MDBX_TXN_FULL();
    private static final int ENV_FLAGS = Mdbx.MDBX_NOSTICKYTHREADS() | Mdbx.MDBX_LIFORECLAIM();
    private static final int TXN_RDONLY = Mdbx.MDBX_TXN_RDONLY();
    private static final int TXN_READWRITE = Mdbx.MDBX_TXN_READWRITE();
    private static final int DB_CREATE = Mdbx.MDBX_CREATE();
    private static final int PUT_RESERVE = Mdbx.MDBX_RESERVE();
    private static final int OP_FIRST = Mdbx.MDBX_FIRST();
    private static final int OP_LAST = Mdbx.MDBX_LAST();
    private static final int OP_NEXT = Mdbx.MDBX_NEXT();
    private static final int OP_PREV = Mdbx.MDBX_PREV();
    private static final int OP_SET_RANGE = Mdbx.MDBX_SET_RANGE();

    /**
     * The opaque libmdbx environment handle ({@code MDBX_env*}).
     */
    private final MemorySegment env;

    /**
     * The write queue drained by the {@link #writerThread}.
     */
    private final MdbxWriteQueue queue;

    /**
     * The single dedicated platform thread that owns every write transaction.
     */
    private final Thread writerThread;

    /**
     * The latch released once the writer has opened the three databases (or recorded a failure).
     */
    private final CountDownLatch readyLatch;

    /**
     * The error captured if the writer fails to bootstrap, surfaced to {@link #open(Path, long)}.
     */
    private volatile Throwable bootstrapError;

    /**
     * Whether the writer should keep running; cleared by {@link #close()} to start the drain.
     */
    private volatile boolean running;

    /**
     * The {@value #CHAT_MESSAGES_DBI} database handle, set once during writer bootstrap.
     */
    private int chatDbi;

    /**
     * The {@value #NEWSLETTER_MESSAGES_DBI} database handle, set once during writer bootstrap.
     */
    private int newsletterDbi;

    /**
     * The {@value #STATUS_MESSAGES_DBI} database handle, set once during writer bootstrap.
     */
    private int statusDbi;

    /**
     * The writer thread's lifetime arena; allocates the reusable scratch and is closed when the writer
     * exits.
     */
    private Arena writerArena;

    /**
     * The reusable key descriptor used by the writer; thread-confined to the writer.
     */
    private MemorySegment writerKeyVal;

    /**
     * The reusable value descriptor used by the writer; thread-confined to the writer.
     */
    private MemorySegment writerDataVal;

    /**
     * The reusable transaction-pointer cell used by the writer; thread-confined to the writer.
     */
    private MemorySegment writerTxnPtr;

    /**
     * The reusable cursor-pointer cell used by the writer; thread-confined to the writer.
     */
    private MemorySegment writerCursorPtr;

    /**
     * The reusable key-bytes scratch buffer used by the writer; grown on demand.
     */
    private MemorySegment writerKeyScratch;

    /**
     * The current capacity of {@link #writerKeyScratch}.
     */
    private int writerKeyScratchCapacity;

    /**
     * Constructs a facade around an already-opened env.
     *
     * @apiNote
     * This constructor is private; instances are produced through {@link #open(Path, long)}.
     *
     * @param env the opened libmdbx environment handle
     */
    private PersistentMessageStore(MemorySegment env) {
        this.env = env;
        this.queue = new MdbxWriteQueue(BATCH_CAPACITY, MAX_PENDING);
        this.readyLatch = new CountDownLatch(1);
        this.running = true;
        this.writerThread = new Thread(this::writerLoop, "cobalt-mdbx-writer");
        this.writerThread.setDaemon(true);
    }

    /**
     * Opens or creates the libmdbx env under {@code envDirectory} and returns a facade ready to serve
     * chat, newsletter and status reads and writes.
     *
     * @apiNote
     * Invoked by {@link PersistentLinkedWhatsAppStoreFactory} after the metadata snapshot is loaded or a fresh store
     * is built. The directory is created if it does not already exist.
     *
     * @implNote
     * This implementation loads the native library through {@link NativeLibLoader} (which
     * {@link System#load(String) loads} it so the generated bindings resolve their symbols), creates
     * the env, sets the database and reader ceilings and the auto-growing geometry, opens it with
     * {@code MDBX_NOSTICKYTHREADS | MDBX_LIFORECLAIM}, then starts the writer thread and blocks until it
     * has opened the three databases.
     *
     * @param envDirectory   the directory that will host {@code mdbx.dat} and {@code mdbx.lck}; created
     *                       if it does not exist
     * @param initialMapSize the upper bound in bytes the env file may grow to
     * @return a fully initialised facade
     * @throws IOException if the directory cannot be created or the env cannot be opened
     */
    static PersistentMessageStore open(Path envDirectory, long initialMapSize) throws IOException {
        NativeLibLoader.load(LIBRARY_NAME, Arena.global());
        Files.createDirectories(envDirectory);
        MemorySegment env;
        try (var arena = Arena.ofConfined()) {
            var penv = arena.allocate(ADDRESS);
            check(Mdbx.mdbx_env_create(penv), "mdbx_env_create");
            env = penv.get(ADDRESS, 0);
            try {
                check(Mdbx.mdbx_env_set_option(env, Mdbx.MDBX_opt_max_db(), MAX_DBS), "set max_db");
                check(Mdbx.mdbx_env_set_option(env, Mdbx.MDBX_opt_max_readers(), MAX_READERS), "set max_readers");
                check(Mdbx.mdbx_env_set_geometry(env, -1, -1, initialMapSize, GROWTH_STEP, SHRINK_THRESHOLD, -1), "mdbx_env_set_geometry");
                var pathSeg = arena.allocateFrom(envDirectory.toString());
                check(Mdbx.mdbx_env_openU(env, pathSeg, ENV_FLAGS, (short) 0644), "mdbx_env_openU");
            } catch (RuntimeException error) {
                Mdbx.mdbx_env_close_ex(env, true);
                throw new IOException("Could not open libmdbx env at " + envDirectory, error);
            }
        }
        var store = new PersistentMessageStore(env);
        store.startWriter();
        return store;
    }

    /**
     * Starts the writer thread and blocks until it has opened the three databases.
     *
     * @apiNote
     * Internal helper for {@link #open(Path, long)}.
     *
     * @implNote
     * This implementation rethrows any bootstrap failure on the calling thread and closes the env so a
     * failed open does not leak the native handle.
     */
    private void startWriter() {
        writerThread.start();
        try {
            readyLatch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            Mdbx.mdbx_env_close_ex(env, true);
            throw new IllegalStateException("Interrupted while awaiting mdbx writer bootstrap", error);
        }
        if (bootstrapError != null) {
            Mdbx.mdbx_env_close_ex(env, true);
            throw bootstrapError instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException("mdbx writer bootstrap failed", bootstrapError);
        }
    }

    /**
     * The writer thread's main loop: bootstraps the databases, then drains and group-commits batches.
     *
     * @implNote
     * This implementation owns a single thread-confined {@link Arena} for the reusable scratch so the
     * steady-state write path allocates nothing. It parks via {@link LockSupport} when the queue is
     * empty and is woken by producers on the empty-to-non-empty transition or by {@link #close()}.
     */
    private void writerLoop() {
        try (var arena = Arena.ofConfined()) {
            writerArena = arena;
            writerKeyVal = MDBX_val.allocate(arena);
            writerDataVal = MDBX_val.allocate(arena);
            writerTxnPtr = arena.allocate(ADDRESS);
            writerCursorPtr = arena.allocate(ADDRESS);
            try {
                bootstrapDatabases();
            } catch (Throwable error) {
                bootstrapError = error;
                readyLatch.countDown();
                return;
            }
            readyLatch.countDown();
            while (true) {
                var claim = queue.claim();
                if (claim.isEmpty()) {
                    if (!running && queue.isEmpty()) {
                        return;
                    }
                    LockSupport.park(this);
                    continue;
                }
                processBatch(claim.array(), claim.offset(), claim.count());
                queue.release(claim.count());
            }
        }
    }

    /**
     * Opens (creating if absent) the three named databases inside a single bootstrap transaction.
     *
     * @apiNote
     * Runs once, on the writer thread, before the drain loop.
     *
     * @implNote
     * This implementation runs on the writer thread so the database handles are created on the same
     * operating-system thread that will own every subsequent write transaction.
     */
    private void bootstrapDatabases() {
        var txn = beginTransaction(TXN_READWRITE);
        try {
            chatDbi = openDatabase(txn, CHAT_MESSAGES_DBI);
            newsletterDbi = openDatabase(txn, NEWSLETTER_MESSAGES_DBI);
            statusDbi = openDatabase(txn, STATUS_MESSAGES_DBI);
            check(Mdbx.mdbx_txn_commit_ex(txn, MemorySegment.NULL), "bootstrap commit");
        } catch (RuntimeException error) {
            Mdbx.mdbx_txn_abort_ex(txn, MemorySegment.NULL);
            throw error;
        }
    }

    /**
     * Opens a single named database, creating it if it does not exist.
     *
     * @apiNote
     * Internal helper for {@link #bootstrapDatabases()}.
     *
     * @param txn  the active bootstrap transaction
     * @param name the database name
     * @return the database handle
     */
    private int openDatabase(MemorySegment txn, String name) {
        var nameSeg = writerArena.allocateFrom(name);
        var pdbi = writerArena.allocate(JAVA_INT);
        check(Mdbx.mdbx_dbi_open(txn, nameSeg, DB_CREATE, pdbi), "mdbx_dbi_open " + name);
        return pdbi.get(JAVA_INT, 0);
    }

    /**
     * Applies a batch of write ops, preferring a single grouped commit and falling back to per-op
     * commits if the grouped transaction overflows.
     *
     * @apiNote
     * Internal helper for the writer loop.
     *
     * @implNote
     * This implementation first tries to apply all ops to one transaction and commit once (one
     * {@code fsync}). If any op or the commit reports {@code MDBX_TXN_FULL} the transaction is aborted
     * and each op is retried in its own transaction; the op actions are written to be safe to re-run.
     *
     * @param ops   the backing array containing the batch
     * @param off   the index of the first op
     * @param count the number of ops in the batch
     */
    private void processBatch(MdbxWriteOp[] ops, int off, int count) {
        if (tryCommitBatch(ops, off, count)) {
            return;
        }
        for (var i = off; i < off + count; i++) {
            commitSingle(ops[i]);
        }
    }

    /**
     * Applies all ops to a single transaction and commits once.
     *
     * @apiNote
     * Internal helper for {@link #processBatch(MdbxWriteOp[], int, int)}.
     *
     * @param ops   the backing array containing the batch
     * @param off   the index of the first op
     * @param count the number of ops in the batch
     * @return {@code true} if the batch was handled (committed or failed terminally), {@code false} if
     *         it overflowed and the caller should retry per-op
     */
    private boolean tryCommitBatch(MdbxWriteOp[] ops, int off, int count) {
        var txn = beginTransaction(TXN_READWRITE);
        try {
            for (var i = off; i < off + count; i++) {
                ops[i].result = ops[i].action.apply(txn);
            }
        } catch (MdbxException error) {
            Mdbx.mdbx_txn_abort_ex(txn, MemorySegment.NULL);
            if (error.rc == RC_TXN_FULL) {
                return false;
            }
            failBatch(ops, off, count, error);
            return true;
        } catch (RuntimeException error) {
            Mdbx.mdbx_txn_abort_ex(txn, MemorySegment.NULL);
            failBatch(ops, off, count, error);
            return true;
        }
        var rc = Mdbx.mdbx_txn_commit_ex(txn, MemorySegment.NULL);
        if (rc == RC_TXN_FULL) {
            return false;
        }
        if (rc != RC_SUCCESS) {
            failBatch(ops, off, count, new MdbxException(rc, "batch commit"));
            return true;
        }
        for (var i = off; i < off + count; i++) {
            ops[i].complete();
        }
        return true;
    }

    /**
     * Applies one op in its own transaction and commits it.
     *
     * @apiNote
     * The per-op fallback used after a grouped commit overflows.
     *
     * @param op the op to apply
     */
    private void commitSingle(MdbxWriteOp op) {
        var txn = beginTransaction(TXN_READWRITE);
        try {
            op.result = op.action.apply(txn);
        } catch (RuntimeException error) {
            Mdbx.mdbx_txn_abort_ex(txn, MemorySegment.NULL);
            op.fail(error);
            return;
        }
        var rc = Mdbx.mdbx_txn_commit_ex(txn, MemorySegment.NULL);
        if (rc != RC_SUCCESS) {
            op.fail(new MdbxException(rc, "commit"));
            return;
        }
        op.complete();
    }

    /**
     * Completes every op in a batch exceptionally with the given error.
     *
     * @apiNote
     * Internal helper for {@link #tryCommitBatch(MdbxWriteOp[], int, int)}.
     *
     * @param ops   the backing array containing the batch
     * @param off   the index of the first op
     * @param count the number of ops in the batch
     * @param error the failure to publish
     */
    private static void failBatch(MdbxWriteOp[] ops, int off, int count, RuntimeException error) {
        for (var i = off; i < off + count; i++) {
            ops[i].fail(error);
        }
    }

    /**
     * Begins a transaction on the writer thread using the reusable transaction-pointer cell.
     *
     * @apiNote
     * Internal helper invoked only on the writer thread.
     *
     * @param flags the transaction flags ({@code MDBX_TXN_READWRITE})
     * @return the transaction handle
     */
    private MemorySegment beginTransaction(int flags) {
        check(Mdbx.mdbx_txn_begin_ex(env, MemorySegment.NULL, flags, writerTxnPtr, MemorySegment.NULL), "mdbx_txn_begin");
        return writerTxnPtr.get(ADDRESS, 0);
    }

    /**
     * Submits a mutation to the writer thread and blocks until its batch is durably committed.
     *
     * @apiNote
     * Internal helper for every mutating accessor; callers are virtual threads that park (with
     * scheduler compensation) until the writer signals completion.
     *
     * @param action the mutation to apply inside the writer's transaction
     * @return the action's result
     */
    private Object submit(Function<MemorySegment, Object> action) {
        var op = new MdbxWriteOp(action);
        while (!queue.offer(op)) {
            Thread.onSpinWait();
        }
        LockSupport.unpark(writerThread);
        return op.await();
    }

    /**
     * Inserts or replaces a chat message in the {@code chat_messages} database.
     *
     * @apiNote
     * Called from {@link PersistentChat#addMessage(ChatMessageInfo)}. A message with no key id is
     * silently dropped because the key requires one.
     *
     * @param chatJid the JID identifying the owning chat
     * @param info    the message to persist
     */
    void putChatMessage(Jid chatJid, ChatMessageInfo info) {
        submit(txn -> {
            var msgId = info.key().id().orElse(null);
            if (msgId != null) {
                var buffer = reserve(txn, chatDbi, encodePrefixedKey(chatJid, msgId.getBytes(StandardCharsets.UTF_8)), ChatMessageInfoSpec.sizeOf(info));
                ChatMessageInfoSpec.encode(info, ProtobufOutputStream.toBuffer(buffer));
            }
            return null;
        });
    }

    /**
     * Returns the chat message under the composite key {@code chatJid + 0x00 + msgId}, or empty.
     *
     * @apiNote
     * Called from {@link PersistentChat#getMessageById(String)}.
     *
     * @param chatJid the JID identifying the owning chat
     * @param msgId   the message key id
     * @return the decoded message, or empty if no entry exists
     */
    Optional<ChatMessageInfo> getChatMessage(Jid chatJid, String msgId) {
        var key = encodePrefixedKey(chatJid, msgId.getBytes(StandardCharsets.UTF_8));
        return get(chatDbi, key, PersistentMessageStore::decodeChatMessage);
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
        return (Boolean) submit(txn -> delete(txn, chatDbi, key));
    }

    /**
     * Removes every chat message stored for the given chat.
     *
     * @apiNote
     * Called from {@link PersistentChat#removeMessages()} and from {@link PersistentLinkedWhatsAppChatStore#removeChat}
     * so a removed chat's body history does not outlive its metadata.
     *
     * @param chatJid the JID identifying the owning chat
     * @return the number of entries removed
     */
    int removeChatMessages(Jid chatJid) {
        return (Integer) submit(txn -> deleteRange(txn, chatDbi, chatJid));
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
    public Optional<ChatMessageInfo> oldestChatMessage(Jid chatJid) {
        return firstInRange(chatDbi, chatJid, PersistentMessageStore::decodeChatMessage);
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
        return lastInRange(chatDbi, chatJid, PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Returns the number of chat messages stored for the given chat.
     *
     * @apiNote
     * Called once by {@link PersistentChat#attach(PersistentMessageStore)} to seed the per-chat cached
     * counter; subsequent {@link PersistentChat#messageCount()} calls answer from the cache.
     *
     * @implNote
     * This implementation walks the cursor range and is {@code O(n)} in the chat size.
     *
     * @param chatJid the JID identifying the owning chat
     * @return the number of stored messages
     */
    int countChatMessages(Jid chatJid) {
        return countRange(chatDbi, chatJid);
    }

    /**
     * Returns a lazy stream of every chat message stored for the given chat, in cursor order.
     *
     * @apiNote
     * The returned stream owns a read transaction and a cursor that remain open until the stream is
     * closed. Callers MUST consume it inside a try-with-resources block; otherwise the underlying read
     * transaction stays in libmdbx's reader table until the stream is garbage-collected.
     *
     * @param chatJid the JID identifying the owning chat
     * @return a closeable stream of decoded chat messages
     */
    Stream<ChatMessageInfo> streamChatMessages(Jid chatJid) {
        return streamRange(chatDbi, jidRangeStart(chatJid), jidRangeStop(chatJid), PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Inserts or replaces a newsletter message in the {@code newsletter_messages} database.
     *
     * @apiNote
     * Called from {@link PersistentNewsletter#addMessage(NewsletterMessageInfo)}. The key is
     * {@code newsletterJid + 0x00 + serverId(BE)} so cursor first/last yields oldest/newest with no
     * extra index.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @param info          the message to persist
     */
    void putNewsletterMessage(Jid newsletterJid, NewsletterMessageInfo info) {
        submit(txn -> {
            var buffer = reserve(txn, newsletterDbi, encodePrefixedKey(newsletterJid, encodeServerId(info.serverId())), NewsletterMessageInfoSpec.sizeOf(info));
            NewsletterMessageInfoSpec.encode(info, ProtobufOutputStream.toBuffer(buffer));
            return null;
        });
    }

    /**
     * Returns the newsletter message under the composite key {@code newsletterJid + 0x00 + serverId(BE)},
     * or empty.
     *
     * @apiNote
     * Called from {@link PersistentNewsletter#getMessageById(String)} and from
     * {@link PersistentLinkedWhatsAppChatStore#findMessageById(com.github.auties00.cobalt.model.newsletter.Newsletter, String)}
     * as the fast path before the per-newsletter scan fallback.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @param serverId      the server-assigned monotonic identifier
     * @return the decoded message, or empty if no entry exists
     */
    Optional<NewsletterMessageInfo> getNewsletterMessageByServerId(Jid newsletterJid, int serverId) {
        var key = encodePrefixedKey(newsletterJid, encodeServerId(serverId));
        return get(newsletterDbi, key, PersistentMessageStore::decodeNewsletterMessage);
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
        return (Boolean) submit(txn -> delete(txn, newsletterDbi, key));
    }

    /**
     * Removes every newsletter message stored for the given newsletter.
     *
     * @apiNote
     * Called from {@link PersistentNewsletter#removeMessages()} and from
     * {@link PersistentLinkedWhatsAppChatStore#removeNewsletter} so a removed newsletter's body history does not outlive
     * its metadata.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the number of entries removed
     */
    int removeNewsletterMessages(Jid newsletterJid) {
        return (Integer) submit(txn -> deleteRange(txn, newsletterDbi, newsletterJid));
    }

    /**
     * Returns the oldest newsletter message (lowest {@code serverId}) for the given newsletter, or
     * empty.
     *
     * @apiNote
     * Called from {@link PersistentNewsletter#oldestMessage()}.
     *
     * @implNote
     * This implementation relies on the big-endian {@code serverId} encoding so memcmp-ordered cursor
     * traversal aligns with the natural numeric order.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the oldest message, or empty
     */
    Optional<NewsletterMessageInfo> oldestNewsletterMessage(Jid newsletterJid) {
        return firstInRange(newsletterDbi, newsletterJid, PersistentMessageStore::decodeNewsletterMessage);
    }

    /**
     * Returns the newest newsletter message (highest {@code serverId}) for the given newsletter, or
     * empty.
     *
     * @apiNote
     * Called from {@link PersistentNewsletter#newestMessage()}.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the newest message, or empty
     */
    Optional<NewsletterMessageInfo> newestNewsletterMessage(Jid newsletterJid) {
        return lastInRange(newsletterDbi, newsletterJid, PersistentMessageStore::decodeNewsletterMessage);
    }

    /**
     * Returns the number of newsletter messages stored for the given newsletter.
     *
     * @apiNote
     * Called once by {@link PersistentNewsletter#attach(PersistentMessageStore)} to seed the cached
     * counter.
     *
     * @implNote
     * This implementation walks the cursor range; it is {@code O(n)} in the newsletter size.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return the number of stored messages
     */
    int countNewsletterMessages(Jid newsletterJid) {
        return countRange(newsletterDbi, newsletterJid);
    }

    /**
     * Returns a lazy stream of every newsletter message stored for the given newsletter, in cursor
     * order.
     *
     * @apiNote
     * The returned stream owns a read transaction and a cursor that remain open until the stream is
     * closed; consume inside a try-with-resources block.
     *
     * @param newsletterJid the JID identifying the owning newsletter
     * @return a closeable stream of decoded newsletter messages
     */
    Stream<NewsletterMessageInfo> streamNewsletterMessages(Jid newsletterJid) {
        return streamRange(newsletterDbi, jidRangeStart(newsletterJid), jidRangeStop(newsletterJid), PersistentMessageStore::decodeNewsletterMessage);
    }

    /**
     * Inserts or replaces a status-feed message keyed by message id.
     *
     * @apiNote
     * Called from {@link PersistentLinkedWhatsAppChatStore#addStatus(ChatMessageInfo)}. A message with no key id is
     * silently dropped because the key requires one.
     *
     * @param info the message to persist
     */
    void putStatusMessage(ChatMessageInfo info) {
        submit(txn -> {
            var msgId = info.key().id().orElse(null);
            if (msgId != null) {
                var buffer = reserve(txn, statusDbi, msgId.getBytes(StandardCharsets.UTF_8), ChatMessageInfoSpec.sizeOf(info));
                ChatMessageInfoSpec.encode(info, ProtobufOutputStream.toBuffer(buffer));
            }
            return null;
        });
    }

    /**
     * Returns the status-feed message under the flat key {@code msgId}, or empty.
     *
     * @apiNote
     * Called from {@link PersistentLinkedWhatsAppChatStore#findStatusById(String)} and as a status-broadcast branch of
     * {@link PersistentLinkedWhatsAppChatStore#findMessageById}.
     *
     * @param msgId the message key id
     * @return the decoded message, or empty
     */
    Optional<ChatMessageInfo> getStatusMessage(String msgId) {
        return get(statusDbi, msgId.getBytes(StandardCharsets.UTF_8), PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Removes the status-feed message under {@code msgId} and returns its previous value.
     *
     * @apiNote
     * Called from {@link PersistentLinkedWhatsAppChatStore#removeStatus(String)}.
     *
     * @implNote
     * This implementation reads the value first inside the write transaction so the caller can observe
     * what was removed; libmdbx's delete does not return the prior value.
     *
     * @param msgId the message key id
     * @return the previously stored message, or empty if absent
     */
    @SuppressWarnings("unchecked")
    Optional<ChatMessageInfo> removeStatusMessage(String msgId) {
        var key = msgId.getBytes(StandardCharsets.UTF_8);
        return (Optional<ChatMessageInfo>) submit(txn -> {
            writeWriterKey(key);
            var rc = Mdbx.mdbx_get(txn, statusDbi, writerKeyVal, writerDataVal);
            if (rc == RC_NOTFOUND) {
                return Optional.empty();
            }
            check(rc, "mdbx_get status");
            var decoded = decodeChatMessage(writerDataVal);
            check(Mdbx.mdbx_del(txn, statusDbi, writerKeyVal, MemorySegment.NULL), "mdbx_del status");
            return Optional.of(decoded);
        });
    }

    /**
     * Returns the number of status-feed messages stored.
     *
     * @apiNote
     * No caller currently consumes this directly; retained for parity with the chat and newsletter
     * accessors.
     *
     * @implNote
     * This implementation queries {@code mdbx_dbi_stat}, which returns the entry count in {@code O(1)}
     * from libmdbx's internal counter, unlike the per-JID counts that need a cursor walk.
     *
     * @return the entry count
     */
    int countStatusMessages() {
        return withReadTransaction((txn, arena) -> {
            var stat = MDBX_stat.allocate(arena);
            check(Mdbx.mdbx_dbi_stat(txn, statusDbi, stat, MDBX_stat.sizeof()), "mdbx_dbi_stat");
            return (int) MDBX_stat.ms_entries(stat);
        });
    }

    /**
     * Returns a lazy stream of every status-feed message in cursor order.
     *
     * @apiNote
     * Called from {@link PersistentLinkedWhatsAppChatStore#status()}; the stream is consumed inside a try-with-resources
     * block and collected into a list.
     *
     * @return a closeable stream of decoded status messages
     */
    Stream<ChatMessageInfo> streamStatusMessages() {
        return streamRange(statusDbi, null, null, PersistentMessageStore::decodeChatMessage);
    }

    /**
     * Returns the set of distinct chat JIDs that currently have at least one message stored.
     *
     * @apiNote
     * Called by {@link PersistentLinkedWhatsAppStoreFactory} on boot to recover orphan chats whose messages landed in
     * the env but whose metadata never reached the next {@code store.proto} snapshot.
     *
     * @return a freshly allocated set of chat JIDs, never {@code null}
     */
    Set<Jid> distinctChatJids() {
        return distinctJids(chatDbi);
    }

    /**
     * Returns the set of distinct newsletter JIDs that currently have at least one message stored.
     *
     * @apiNote
     * Called by {@link PersistentLinkedWhatsAppStoreFactory} on boot to recover orphan newsletters whose messages
     * landed in the env but whose metadata never reached the next snapshot.
     *
     * @return a freshly allocated set of newsletter JIDs, never {@code null}
     */
    Set<Jid> distinctNewsletterJids() {
        return distinctJids(newsletterDbi);
    }

    /**
     * Closes the writer thread and the underlying env, releasing all native resources.
     *
     * @apiNote
     * Called from {@link PersistentStore#close()} and {@link PersistentStore#delete()}. After this call
     * every accessor will fail; the parent store nulls its reference so a second close is a no-op.
     *
     * @implNote
     * This implementation flips the running flag, wakes the writer so it drains any remaining ops and
     * exits, joins it, then closes the env with a final durable flush.
     */
    @Override
    public void close() {
        running = false;
        LockSupport.unpark(writerThread);
        try {
            writerThread.join();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        Mdbx.mdbx_env_close_ex(env, false);
    }

    /**
     * Reserves {@code size} bytes for a value under {@code key} in {@code dbi} and returns a buffer to
     * encode into.
     *
     * @apiNote
     * Internal writer-thread helper; the returned buffer points into libmdbx-allocated space valid
     * until the transaction commits.
     *
     * @implNote
     * This implementation uses {@code MDBX_RESERVE} so the protobuf payload is encoded directly into the
     * database page, avoiding an intermediate heap buffer.
     *
     * @param txn  the active write transaction
     * @param dbi  the target database handle
     * @param key  the key bytes
     * @param size the value size in bytes
     * @return a buffer positioned at the reserved space
     */
    private ByteBuffer reserve(MemorySegment txn, int dbi, byte[] key, int size) {
        writeWriterKey(key);
        MDBX_val.iov_base(writerDataVal, MemorySegment.NULL);
        MDBX_val.iov_len(writerDataVal, size);
        check(Mdbx.mdbx_put(txn, dbi, writerKeyVal, writerDataVal, PUT_RESERVE), "mdbx_put");
        return MDBX_val.iov_base(writerDataVal).reinterpret(size).asByteBuffer();
    }

    /**
     * Deletes a single key from {@code dbi}.
     *
     * @apiNote
     * Internal writer-thread helper.
     *
     * @param txn the active write transaction
     * @param dbi the target database handle
     * @param key the key bytes
     * @return {@code true} if an entry was removed
     */
    private boolean delete(MemorySegment txn, int dbi, byte[] key) {
        writeWriterKey(key);
        var rc = Mdbx.mdbx_del(txn, dbi, writerKeyVal, MemorySegment.NULL);
        if (rc == RC_NOTFOUND) {
            return false;
        }
        check(rc, "mdbx_del");
        return true;
    }

    /**
     * Deletes every entry of {@code dbi} whose key begins with {@code jid + 0x00}.
     *
     * @apiNote
     * Internal writer-thread helper for the range-delete accessors.
     *
     * @implNote
     * This implementation positions a cursor at the range start and deletes forward until the key
     * leaves the JID prefix, comparing each key against the range-end sentinel.
     *
     * @param txn the active write transaction
     * @param dbi the target database handle
     * @param jid the JID prefix
     * @return the number of entries removed
     */
    private int deleteRange(MemorySegment txn, int dbi, Jid jid) {
        var stop = jidRangeStop(jid);
        check(Mdbx.mdbx_cursor_open(txn, dbi, writerCursorPtr), "mdbx_cursor_open");
        var cursor = writerCursorPtr.get(ADDRESS, 0);
        try {
            var removed = 0;
            writeWriterKey(jidRangeStart(jid));
            var rc = Mdbx.mdbx_cursor_get(cursor, writerKeyVal, writerDataVal, OP_SET_RANGE);
            while (rc == RC_SUCCESS && compareKey(writerKeyVal, stop) < 0) {
                check(Mdbx.mdbx_cursor_del(cursor, 0), "mdbx_cursor_del");
                removed++;
                rc = Mdbx.mdbx_cursor_get(cursor, writerKeyVal, writerDataVal, OP_NEXT);
            }
            if (rc != RC_SUCCESS && rc != RC_NOTFOUND) {
                check(rc, "mdbx_cursor_get next");
            }
            return removed;
        } finally {
            Mdbx.mdbx_cursor_close2(cursor);
        }
    }

    /**
     * Copies {@code key} into the writer's reusable scratch buffer and points the reusable key
     * descriptor at it.
     *
     * @apiNote
     * Internal writer-thread helper; the scratch is grown on demand and never shrinks.
     *
     * @param key the key bytes
     */
    private void writeWriterKey(byte[] key) {
        if (writerKeyScratch == null || key.length > writerKeyScratchCapacity) {
            writerKeyScratchCapacity = Math.max(Integer.highestOneBit(key.length) * 2, 64);
            writerKeyScratch = writerArena.allocate(writerKeyScratchCapacity);
        }
        MemorySegment.copy(key, 0, writerKeyScratch, JAVA_BYTE, 0, key.length);
        MDBX_val.iov_base(writerKeyVal, writerKeyScratch);
        MDBX_val.iov_len(writerKeyVal, key.length);
    }

    /**
     * Returns the decoded value stored under {@code key} in {@code dbi}, or empty.
     *
     * @apiNote
     * Internal helper for the point-read accessors; runs on the calling thread under a read
     * transaction.
     *
     * @param dbi     the database handle
     * @param key     the key bytes
     * @param decoder how to decode the value buffer
     * @param <T>     the decoded type
     * @return the decoded value, or empty if no entry exists
     */
    private <T> Optional<T> get(int dbi, byte[] key, Function<MemorySegment, T> decoder) {
        return withReadTransaction((txn, arena) -> {
            var keyVal = newVal(arena, key);
            var dataVal = MDBX_val.allocate(arena);
            var rc = Mdbx.mdbx_get(txn, dbi, keyVal, dataVal);
            if (rc == RC_NOTFOUND) {
                return Optional.empty();
            }
            check(rc, "mdbx_get");
            return Optional.of(decoder.apply(dataVal));
        });
    }

    /**
     * Returns the first decoded entry of {@code dbi} in the per-JID range, or empty.
     *
     * @apiNote
     * Internal helper for the oldest-message accessors.
     *
     * @param dbi     the database handle
     * @param jid     the JID prefix
     * @param decoder how to decode the value buffer
     * @param <T>     the decoded type
     * @return the first entry, or empty
     */
    private <T> Optional<T> firstInRange(int dbi, Jid jid, Function<MemorySegment, T> decoder) {
        var start = jidRangeStart(jid);
        var stop = jidRangeStop(jid);
        return withReadTransaction((txn, arena) -> {
            var cursor = openReadCursor(txn, dbi, arena);
            try {
                var keyVal = newVal(arena, start);
                var dataVal = MDBX_val.allocate(arena);
                var rc = Mdbx.mdbx_cursor_get(cursor, keyVal, dataVal, OP_SET_RANGE);
                if (rc == RC_NOTFOUND || (rc == RC_SUCCESS && compareKey(keyVal, stop) >= 0)) {
                    return Optional.empty();
                }
                check(rc, "mdbx_cursor_get set_range");
                return Optional.of(decoder.apply(dataVal));
            } finally {
                Mdbx.mdbx_cursor_close2(cursor);
            }
        });
    }

    /**
     * Returns the last decoded entry of {@code dbi} in the per-JID range, or empty.
     *
     * @apiNote
     * Internal helper for the newest-message accessors.
     *
     * @implNote
     * This implementation seeks to the range-end sentinel and steps back one position
     * ({@code MDBX_PREV}), or takes the global last entry when nothing sorts at or after the sentinel,
     * giving an {@code O(log n)} newest lookup rather than a full forward scan.
     *
     * @param dbi     the database handle
     * @param jid     the JID prefix
     * @param decoder how to decode the value buffer
     * @param <T>     the decoded type
     * @return the last entry, or empty
     */
    private <T> Optional<T> lastInRange(int dbi, Jid jid, Function<MemorySegment, T> decoder) {
        var start = jidRangeStart(jid);
        var stop = jidRangeStop(jid);
        return withReadTransaction((txn, arena) -> {
            var cursor = openReadCursor(txn, dbi, arena);
            try {
                var keyVal = newVal(arena, stop);
                var dataVal = MDBX_val.allocate(arena);
                var rc = Mdbx.mdbx_cursor_get(cursor, keyVal, dataVal, OP_SET_RANGE);
                rc = rc == RC_NOTFOUND
                        ? Mdbx.mdbx_cursor_get(cursor, keyVal, dataVal, OP_LAST)
                        : Mdbx.mdbx_cursor_get(cursor, keyVal, dataVal, OP_PREV);
                if (rc == RC_NOTFOUND) {
                    return Optional.empty();
                }
                check(rc, "mdbx_cursor_get prev");
                if (compareKey(keyVal, start) < 0) {
                    return Optional.empty();
                }
                return Optional.of(decoder.apply(dataVal));
            } finally {
                Mdbx.mdbx_cursor_close2(cursor);
            }
        });
    }

    /**
     * Returns the number of entries in {@code dbi} within the per-JID range.
     *
     * @apiNote
     * Internal helper for the per-JID count accessors.
     *
     * @param dbi the database handle
     * @param jid the JID prefix
     * @return the entry count
     */
    private int countRange(int dbi, Jid jid) {
        var start = jidRangeStart(jid);
        var stop = jidRangeStop(jid);
        return withReadTransaction((txn, arena) -> {
            var cursor = openReadCursor(txn, dbi, arena);
            try {
                var count = 0;
                var keyVal = newVal(arena, start);
                var dataVal = MDBX_val.allocate(arena);
                var rc = Mdbx.mdbx_cursor_get(cursor, keyVal, dataVal, OP_SET_RANGE);
                while (rc == RC_SUCCESS && compareKey(keyVal, stop) < 0) {
                    count++;
                    rc = Mdbx.mdbx_cursor_get(cursor, keyVal, dataVal, OP_NEXT);
                }
                if (rc != RC_SUCCESS && rc != RC_NOTFOUND) {
                    check(rc, "mdbx_cursor_get next");
                }
                return count;
            } finally {
                Mdbx.mdbx_cursor_close2(cursor);
            }
        });
    }

    /**
     * Walks {@code dbi} once and collects the distinct JID prefixes that precede the first
     * {@link #KEY_SEPARATOR} in each key.
     *
     * @apiNote
     * Internal helper for the orphan-recovery accessors {@link #distinctChatJids()} and
     * {@link #distinctNewsletterJids()}.
     *
     * @param dbi the database to scan
     * @return a set of decoded JIDs
     */
    private Set<Jid> distinctJids(int dbi) {
        return withReadTransaction((txn, arena) -> {
            var jids = new HashSet<Jid>();
            var cursor = openReadCursor(txn, dbi, arena);
            try {
                var keyVal = MDBX_val.allocate(arena);
                var dataVal = MDBX_val.allocate(arena);
                var rc = Mdbx.mdbx_cursor_get(cursor, keyVal, dataVal, OP_FIRST);
                while (rc == RC_SUCCESS) {
                    var jid = jidFromKey(keyVal);
                    if (jid != null) {
                        jids.add(jid);
                    }
                    rc = Mdbx.mdbx_cursor_get(cursor, keyVal, dataVal, OP_NEXT);
                }
                if (rc != RC_NOTFOUND) {
                    check(rc, "mdbx_cursor_get next");
                }
                return jids;
            } finally {
                Mdbx.mdbx_cursor_close2(cursor);
            }
        });
    }

    /**
     * Wraps a cursor walk into a closeable {@link Stream} whose {@link Stream#close()} hook closes the
     * cursor, aborts the read transaction and frees the backing arena.
     *
     * @apiNote
     * Internal helper for the stream accessors.
     *
     * @implNote
     * This implementation uses a {@linkplain Arena#ofShared() shared arena} so the transaction, cursor
     * and descriptors stay valid if the consuming virtual thread migrates carriers between elements;
     * this is safe under {@code MDBX_NOSTICKYTHREADS}. A {@code null} {@code start} and {@code stop}
     * stream the whole database (the status feed); otherwise the half-open per-JID range is streamed.
     *
     * @param dbi     the database handle
     * @param start   the inclusive range start key, or {@code null} for the whole database
     * @param stop    the exclusive range end key, or {@code null} for the whole database
     * @param decoder how to decode each value buffer
     * @param <T>     the decoded type
     * @return a closeable stream of decoded entries
     */
    private <T> Stream<T> streamRange(int dbi, byte[] start, byte[] stop, Function<MemorySegment, T> decoder) {
        var arena = Arena.ofShared();
        MemorySegment txn = null;
        MemorySegment cursor = null;
        try {
            var ptxn = arena.allocate(ADDRESS);
            check(Mdbx.mdbx_txn_begin_ex(env, MemorySegment.NULL, TXN_RDONLY, ptxn, MemorySegment.NULL), "stream txn_begin");
            txn = ptxn.get(ADDRESS, 0);
            cursor = openReadCursor(txn, dbi, arena);
            var iterator = new RangeIterator<>(cursor, arena, start, stop, decoder);
            var spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL);
            var fixedCursor = cursor;
            var fixedTxn = txn;
            return StreamSupport.stream(spliterator, false)
                    .onClose(() -> {
                        try {
                            Mdbx.mdbx_cursor_close2(fixedCursor);
                            Mdbx.mdbx_txn_abort_ex(fixedTxn, MemorySegment.NULL);
                        } finally {
                            arena.close();
                        }
                    });
        } catch (RuntimeException error) {
            if (cursor != null) {
                Mdbx.mdbx_cursor_close2(cursor);
            }
            if (txn != null) {
                Mdbx.mdbx_txn_abort_ex(txn, MemorySegment.NULL);
            }
            arena.close();
            throw error;
        }
    }

    /**
     * Runs {@code body} inside a fresh read transaction on the calling thread and returns its result.
     *
     * @apiNote
     * Internal helper. The transaction is always aborted before this method returns; the body must not
     * retain the transaction or any buffers obtained from it.
     *
     * @param body the read-only operation, receiving the transaction handle and a confined arena
     * @param <T>  the result type
     * @return the value produced by {@code body}
     */
    private <T> T withReadTransaction(ReadOperation<T> body) {
        try (var arena = Arena.ofConfined()) {
            var ptxn = arena.allocate(ADDRESS);
            check(Mdbx.mdbx_txn_begin_ex(env, MemorySegment.NULL, TXN_RDONLY, ptxn, MemorySegment.NULL), "read txn_begin");
            var txn = ptxn.get(ADDRESS, 0);
            try {
                return body.apply(txn, arena);
            } finally {
                Mdbx.mdbx_txn_abort_ex(txn, MemorySegment.NULL);
            }
        }
    }

    /**
     * Opens a cursor on {@code dbi} within {@code txn}, allocating its handle cell from {@code arena}.
     *
     * @apiNote
     * Internal helper for the read accessors.
     *
     * @param txn   the active read transaction
     * @param dbi   the database handle
     * @param arena the arena to allocate the cursor-pointer cell from
     * @return the cursor handle
     */
    private static MemorySegment openReadCursor(MemorySegment txn, int dbi, Arena arena) {
        var pcursor = arena.allocate(ADDRESS);
        check(Mdbx.mdbx_cursor_open(txn, dbi, pcursor), "mdbx_cursor_open");
        return pcursor.get(ADDRESS, 0);
    }

    /**
     * Allocates an {@code MDBX_val} in {@code arena} pointing at a fresh copy of {@code bytes}.
     *
     * @apiNote
     * Internal helper for read-side key descriptors.
     *
     * @param arena the arena to allocate from
     * @param bytes the key or value bytes
     * @return the descriptor
     */
    private static MemorySegment newVal(Arena arena, byte[] bytes) {
        var data = arena.allocate(Math.max(bytes.length, 1));
        MemorySegment.copy(bytes, 0, data, JAVA_BYTE, 0, bytes.length);
        var val = MDBX_val.allocate(arena);
        MDBX_val.iov_base(val, data);
        MDBX_val.iov_len(val, bytes.length);
        return val;
    }

    /**
     * Wraps the value an {@code MDBX_val} points at as a heap-safe {@link ByteBuffer} view.
     *
     * @apiNote
     * Internal helper for the decoders; the returned buffer aliases mmap memory valid only until the
     * read transaction ends, so callers must copy out before then.
     *
     * @param val the value descriptor
     * @return a buffer over the value bytes
     */
    private static ByteBuffer valueBuffer(MemorySegment val) {
        var base = MDBX_val.iov_base(val);
        var len = MDBX_val.iov_len(val);
        return base.reinterpret(len).asByteBuffer();
    }

    /**
     * Decodes an {@code MDBX_val} value into a fully heap-resident {@link ChatMessageInfo}.
     *
     * @apiNote
     * Internal helper for every chat and status read.
     *
     * @param val the value descriptor
     * @return the decoded chat message
     */
    private static ChatMessageInfo decodeChatMessage(MemorySegment val) {
        return ChatMessageInfoSpec.decode(new CopyingProtobufInputStream(valueBuffer(val)));
    }

    /**
     * Decodes an {@code MDBX_val} value into a fully heap-resident {@link NewsletterMessageInfo}.
     *
     * @apiNote
     * Internal helper for every newsletter read.
     *
     * @param val the value descriptor
     * @return the decoded newsletter message
     */
    private static NewsletterMessageInfo decodeNewsletterMessage(MemorySegment val) {
        return NewsletterMessageInfoSpec.decode(new CopyingProtobufInputStream(valueBuffer(val)));
    }

    /**
     * Returns the JID encoded in the prefix of a composite key, or {@code null} if the key has no
     * separator.
     *
     * @apiNote
     * Internal helper for {@link #distinctJids(int)}.
     *
     * @param keyVal the key descriptor
     * @return the decoded JID, or {@code null}
     */
    private static Jid jidFromKey(MemorySegment keyVal) {
        var base = MDBX_val.iov_base(keyVal);
        var len = MDBX_val.iov_len(keyVal);
        var seg = base.reinterpret(len);
        var separator = -1;
        for (var i = 0; i < len; i++) {
            if (seg.get(JAVA_BYTE, i) == KEY_SEPARATOR) {
                separator = i;
                break;
            }
        }
        if (separator < 0) {
            return null;
        }
        var jidBytes = new byte[separator];
        MemorySegment.copy(seg, JAVA_BYTE, 0, jidBytes, 0, separator);
        return Jid.of(new String(jidBytes, StandardCharsets.UTF_8));
    }

    /**
     * Compares the bytes an {@code MDBX_val} key points at against {@code other} using unsigned
     * byte order.
     *
     * @apiNote
     * Internal helper matching libmdbx's default memcmp key ordering.
     *
     * @param keyVal the key descriptor
     * @param other  the bytes to compare against
     * @return a negative, zero or positive value as the key is less than, equal to or greater than
     *         {@code other}
     */
    private static int compareKey(MemorySegment keyVal, byte[] other) {
        var base = MDBX_val.iov_base(keyVal);
        var len = MDBX_val.iov_len(keyVal);
        var seg = base.reinterpret(len);
        var common = (int) Math.min(len, other.length);
        for (var i = 0; i < common; i++) {
            var a = seg.get(JAVA_BYTE, i) & 0xFF;
            var b = other[i] & 0xFF;
            if (a != b) {
                return a - b;
            }
        }
        return Long.compare(len, other.length);
    }

    /**
     * Returns the composite key {@code jidBytes + 0x00 + suffix}.
     *
     * @apiNote
     * Internal helper for the chat and newsletter databases.
     *
     * @param jid    the JID prefix
     * @param suffix the suffix bytes (a message id or an encoded server id)
     * @return the composite key bytes
     */
    private static byte[] encodePrefixedKey(Jid jid, byte[] suffix) {
        var jidBytes = jid.toString().getBytes(StandardCharsets.UTF_8);
        var key = new byte[jidBytes.length + 1 + suffix.length];
        System.arraycopy(jidBytes, 0, key, 0, jidBytes.length);
        key[jidBytes.length] = KEY_SEPARATOR;
        System.arraycopy(suffix, 0, key, jidBytes.length + 1, suffix.length);
        return key;
    }

    /**
     * Returns the inclusive start key {@code jidBytes + 0x00} of a per-JID range.
     *
     * @apiNote
     * Internal helper for the per-JID cursor operations.
     *
     * @param jid the JID prefix
     * @return the range start key bytes
     */
    private static byte[] jidRangeStart(Jid jid) {
        var jidBytes = jid.toString().getBytes(StandardCharsets.UTF_8);
        var key = new byte[jidBytes.length + 1];
        System.arraycopy(jidBytes, 0, key, 0, jidBytes.length);
        key[jidBytes.length] = KEY_SEPARATOR;
        return key;
    }

    /**
     * Returns the exclusive end key {@code jidBytes + 0x01} of a per-JID range.
     *
     * @apiNote
     * Internal helper for the per-JID cursor operations.
     *
     * @param jid the JID prefix
     * @return the range end key bytes
     */
    private static byte[] jidRangeStop(Jid jid) {
        var jidBytes = jid.toString().getBytes(StandardCharsets.UTF_8);
        var key = new byte[jidBytes.length + 1];
        System.arraycopy(jidBytes, 0, key, 0, jidBytes.length);
        key[jidBytes.length] = KEY_RANGE_END;
        return key;
    }

    /**
     * Encodes {@code serverId} as four big-endian bytes.
     *
     * @apiNote
     * Used as the suffix of newsletter-message keys.
     *
     * @implNote
     * This implementation emits big-endian so unsigned-byte cursor compare yields the natural numeric
     * order; little-endian would invert the cursor walk.
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
     * Throws an {@link MdbxException} if {@code rc} is not {@code MDBX_SUCCESS}.
     *
     * @apiNote
     * Internal helper wrapping every libmdbx return code; callers handle expected non-success codes
     * (such as {@code MDBX_NOTFOUND}) before calling this.
     *
     * @param rc the libmdbx return code
     * @param op the operation name, for diagnostics
     */
    private static void check(int rc, String op) {
        if (rc != RC_SUCCESS) {
            throw new MdbxException(rc, op);
        }
    }

    /**
     * Returns the libmdbx error string for {@code rc}.
     *
     * @apiNote
     * Internal helper for {@link MdbxException} messages.
     *
     * @param rc the libmdbx return code
     * @return the human-readable error description
     */
    private static String describe(int rc) {
        var seg = Mdbx.mdbx_strerror(rc);
        if (seg == null || seg.address() == 0) {
            return "unknown error";
        }
        return seg.reinterpret(Long.MAX_VALUE).getString(0);
    }

    /**
     * A read-only operation over a libmdbx transaction and a confined arena.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    private interface ReadOperation<T> {
        /**
         * Runs the operation.
         *
         * @param txn   the active read transaction
         * @param arena the confined arena scoped to the transaction
         * @return the result
         */
        T apply(MemorySegment txn, Arena arena);
    }

    /**
     * The unchecked exception raised when a libmdbx call returns a non-success code.
     *
     * @apiNote
     * Package-internal; surfaced to callers through {@link MdbxWriteOp#await()} or thrown
     * directly on the read path.
     */
    private static final class MdbxException extends RuntimeException {
        /**
         * The libmdbx return code.
         */
        final int rc;

        /**
         * Constructs an exception for the given return code and operation.
         *
         * @param rc the libmdbx return code
         * @param op the operation name
         */
        MdbxException(int rc, String op) {
            super(op + ": mdbx error " + rc + " (" + describe(rc) + ")");
            this.rc = rc;
        }
    }

    /**
     * The {@link java.util.Iterator} that advances a cursor across a key range, decoding each value.
     *
     * @param <T> the decoded element type
     *
     * @implNote
     * This implementation lazily positions the cursor on the first {@link #hasNext()} call
     * ({@code MDBX_FIRST} for a whole-database scan, {@code MDBX_SET_RANGE} for a per-JID range) and
     * advances with {@code MDBX_NEXT}, stopping at the exclusive range end.
     */
    private static final class RangeIterator<T> implements java.util.Iterator<T> {
        /**
         * The cursor being walked.
         */
        private final MemorySegment cursor;

        /**
         * The reusable key descriptor.
         */
        private final MemorySegment keyVal;

        /**
         * The reusable value descriptor.
         */
        private final MemorySegment dataVal;

        /**
         * The exclusive range end key, or {@code null} for a whole-database scan.
         */
        private final byte[] stop;

        /**
         * The decoder applied to each value.
         */
        private final Function<MemorySegment, T> decoder;

        /**
         * Whether the cursor has been positioned yet.
         */
        private boolean started;

        /**
         * Whether {@link #hasNext()} has been computed since the last {@link #next()}.
         */
        private boolean computed;

        /**
         * Whether the most recent computation found an element.
         */
        private boolean hasNext;

        /**
         * The most recently decoded element.
         */
        private T nextValue;

        /**
         * Constructs an iterator over the given cursor and range.
         *
         * @param cursor  the cursor to walk
         * @param arena   the shared arena to allocate descriptors and the start key from
         * @param start   the inclusive range start key, or {@code null} for a whole-database scan
         * @param stop    the exclusive range end key, or {@code null} for a whole-database scan
         * @param decoder the value decoder
         */
        RangeIterator(MemorySegment cursor, Arena arena, byte[] start, byte[] stop, Function<MemorySegment, T> decoder) {
            this.cursor = cursor;
            this.stop = stop;
            this.decoder = decoder;
            this.dataVal = MDBX_val.allocate(arena);
            this.keyVal = start == null ? MDBX_val.allocate(arena) : newVal(arena, start);
        }

        /**
         * Positions or advances the cursor and caches the next element.
         */
        private void advance() {
            int rc;
            if (!started) {
                started = true;
                rc = Mdbx.mdbx_cursor_get(cursor, keyVal, dataVal, stop == null && keyVal != null && MDBX_val.iov_len(keyVal) == 0 ? OP_FIRST : OP_SET_RANGE);
            } else {
                rc = Mdbx.mdbx_cursor_get(cursor, keyVal, dataVal, OP_NEXT);
            }
            if (rc == RC_NOTFOUND) {
                hasNext = false;
                return;
            }
            check(rc, "mdbx_cursor_get stream");
            if (stop != null && compareKey(keyVal, stop) >= 0) {
                hasNext = false;
                return;
            }
            nextValue = decoder.apply(dataVal);
            hasNext = true;
        }

        @Override
        public boolean hasNext() {
            if (!computed) {
                advance();
                computed = true;
            }
            return hasNext;
        }

        @Override
        public T next() {
            if (!computed) {
                advance();
            }
            if (!hasNext) {
                throw new NoSuchElementException();
            }
            computed = false;
            var value = nextValue;
            nextValue = null;
            return value;
        }
    }

    /**
     * The {@link ProtobufInputStream} subclass that copies every {@code bytes} and {@code string} field
     * to the heap during decode, making decoded messages safe to outlive the source read transaction.
     *
     * @apiNote
     * Used by the decoders; never visible to callers of this facade.
     *
     * @implNote
     * The stock {@code ProtobufInputStream.fromBuffer(...)} returns each {@code bytes} field as a
     * {@link ByteBuffer#slice slice} of the source buffer, sharing memory. When the source is an
     * mmap'd page that slice becomes invalid the moment the read transaction closes, which would turn
     * any later access into a SIGSEGV. This implementation overrides {@link #readBytes(int)} and
     * {@link #readString(int)} to allocate fresh heap arrays so the decoded message escapes the read
     * transaction safely.
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
         * This implementation delegates to {@link ByteBuffer#get()} which advances the position by one.
         */
        @Override
        protected byte readByte() {
            return buffer.get();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation copies {@code size} bytes out of the source buffer into a fresh heap array
         * and wraps it as a {@link ByteBuffer}, so the returned value is independent of the mmap'd
         * source.
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
         * This implementation copies {@code size} bytes out of the source buffer into a fresh heap array
         * and wraps it as a {@link ProtobufString.Lazy}.
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
         * {@link CopyingProtobufInputStream}; every read through the sub-stream copies into the heap
         * before the slice can be invalidated.
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
         * This implementation does nothing; the source buffer's lifetime is owned by the surrounding
         * read transaction.
         */
        @Override
        public void close() {

        }
    }
}
