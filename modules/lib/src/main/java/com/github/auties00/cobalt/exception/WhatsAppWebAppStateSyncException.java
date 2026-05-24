package com.github.auties00.cobalt.exception;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.error.DisconnectReason;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import java.util.HexFormat;
import java.util.Objects;

/**
 * Sealed root for failures during Web App State (also known as syncd)
 * synchronization.
 *
 * @apiNote
 * Web App State is the encrypted key-value protocol WhatsApp uses to
 * keep contacts, chat metadata, settings, starred messages, the
 * blocklist, and similar shared state in sync across the primary phone
 * and every linked companion device. State is published as named
 * collections; updates are sent as patches the receiving device applies
 * on top of the last known snapshot. Each patch carries mutations whose
 * values, indices, and ordering are protected by HMACs and chained
 * against the previous patch.
 *
 * <p>The nested subtypes split into three buckets that mirror WA Web's
 * {@code WAWebSyncdError} module: <ul>
 *   <li>fatal-classified errors (mapped to {@code SyncdFatalError}):
 *       integrity checks at the snapshot/patch/value/index level,
 *       unrecoverable key losses, decryption failures, structural
 *       violations of the patch stream, terminal patches, and the
 *       catch-all {@link UnexpectedError};
 *   <li>retryable-classified errors (mapped to {@code SyncdRetryableError}):
 *       {@link Conflict}, {@link RetryableServerError},
 *       {@link ExternalDownloadFailed}, {@link ExternalDecodeFailed};
 *   <li>recoverable-classified errors (mapped to {@code SyncdMissingKeyError}):
 *       {@link MissingKey}.
 * </ul>
 *
 * <p>{@link #isFatal()} reports the classification at the subtype level
 * so the configurable error handler can wipe and resync the affected
 * collection, schedule a retry, or wait for an out-of-band key delivery.
 *
 * @implNote
 * This implementation classifies each subtype individually because WA
 * Web's three concrete error classes are too coarse for embedders that
 * want to differentiate between, for example, a {@link Conflict} (must
 * refetch) and a {@link RetryableServerError} (must honour
 * {@link RetryableServerError#serverBackoffMs}).
 *
 * @see SnapshotMacMismatch
 * @see PatchMacMismatch
 * @see ValueMacMismatch
 * @see IndexMacMismatch
 * @see MissingKey
 * @see MissingKeyOnAllDevices
 * @see TimeoutWhileWaitingForMissingKey
 * @see MissingPatches
 * @see TerminalPatch
 * @see Conflict
 * @see RetryableServerError
 * @see DecryptionFailed
 * @see ExternalDownloadFailed
 * @see ExternalDecodeFailed
 * @see MacComputationFailed
 * @see MissingActionTimestamp
 * @see DuplicateIndexInPatch
 * @see DuplicatePatchVersion
 * @see UnexpectedError
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdError")
public sealed abstract class WhatsAppWebAppStateSyncException extends WhatsAppException
        permits WhatsAppWebAppStateSyncException.SnapshotMacMismatch,
                WhatsAppWebAppStateSyncException.PatchMacMismatch,
                WhatsAppWebAppStateSyncException.ValueMacMismatch,
                WhatsAppWebAppStateSyncException.IndexMacMismatch,
                WhatsAppWebAppStateSyncException.MissingKey,
                WhatsAppWebAppStateSyncException.MissingKeyOnAllDevices,
                WhatsAppWebAppStateSyncException.TimeoutWhileWaitingForMissingKey,
                WhatsAppWebAppStateSyncException.MissingPatches,
                WhatsAppWebAppStateSyncException.TerminalPatch,
                WhatsAppWebAppStateSyncException.Conflict,
                WhatsAppWebAppStateSyncException.RetryableServerError,
                WhatsAppWebAppStateSyncException.DecryptionFailed,
                WhatsAppWebAppStateSyncException.ExternalDownloadFailed,
                WhatsAppWebAppStateSyncException.ExternalDecodeFailed,
                WhatsAppWebAppStateSyncException.MacComputationFailed,
                WhatsAppWebAppStateSyncException.MissingActionTimestamp,
                WhatsAppWebAppStateSyncException.DuplicateIndexInPatch,
                WhatsAppWebAppStateSyncException.DuplicatePatchVersion,
                WhatsAppWebAppStateSyncException.UnexpectedError {

    /**
     * Constructs a new web app state sync exception with the specified detail message.
     *
     * @param message the detail message describing the sync failure
     */
    protected WhatsAppWebAppStateSyncException(String message) {
        super(message);
    }

    /**
     * Constructs a new web app state sync exception with a detail message and cause.
     *
     * @param message the detail message describing the sync failure
     * @param cause   the underlying cause of the sync failure
     */
    protected WhatsAppWebAppStateSyncException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Thrown when the HMAC stamped over a state snapshot does not match
     * the value Cobalt computes locally.
     *
     * @apiNote
     * Snapshots are full state dumps used during initial sync and after
     * a fatal resync. A mismatch means the snapshot bytes are corrupt,
     * were tampered with, or were validated against the wrong key. WA
     * Web's {@code WAWebSyncdAntiTampering} wraps the same condition as
     * a {@code SyncdFatalError}; the collection has to be wiped and
     * refetched.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class SnapshotMacMismatch extends WhatsAppWebAppStateSyncException {
        /**
         * The collection whose snapshot failed validation.
         */
        private final SyncPatchType collectionName;

        /**
         * The snapshot version that failed validation.
         */
        private final long version;

        /**
         * Constructs a new snapshot MAC mismatch exception.
         *
         * @param collectionName the collection whose snapshot failed validation
         * @param version        the snapshot version
         * @throws NullPointerException if {@code collectionName} is {@code null}
         */
        public SnapshotMacMismatch(SyncPatchType collectionName, long version) {
            super("Snapshot MAC mismatch for collection " + collectionName + " at version " + version);
            this.collectionName = Objects.requireNonNull(collectionName);
            this.version = version;
        }

        /**
         * Returns the collection whose snapshot failed validation.
         *
         * @return the collection identifier, never {@code null}
         */
        public SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * Returns the snapshot version that failed validation.
         *
         * @return the snapshot version
         */
        public long version() {
            return version;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: snapshot
         * integrity failures require a wipe-and-resync.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when the HMAC stamped over a state patch does not match
     * the value Cobalt computes locally.
     *
     * @apiNote
     * Patches are incremental updates chained on top of the latest
     * snapshot. A MAC mismatch means the patch bytes are corrupt, were
     * tampered with, or that the chain has been broken by a missing
     * predecessor; the affected collection has to be wiped and resynced.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class PatchMacMismatch extends WhatsAppWebAppStateSyncException {
        /**
         * The collection whose patch failed validation.
         */
        private final SyncPatchType collectionName;

        /**
         * The patch version that failed validation.
         */
        private final long version;

        /**
         * Constructs a new patch MAC mismatch exception.
         *
         * @param collectionName the collection whose patch failed validation
         * @param version        the patch version
         * @throws NullPointerException if {@code collectionName} is {@code null}
         */
        public PatchMacMismatch(SyncPatchType collectionName, long version) {
            super("Patch MAC mismatch for collection " + collectionName + " at version " + version);
            this.collectionName = Objects.requireNonNull(collectionName);
            this.version = version;
        }

        /**
         * Returns the collection whose patch failed validation.
         *
         * @return the collection identifier, never {@code null}
         */
        public SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * Returns the patch version that failed validation.
         *
         * @return the patch version
         */
        public long version() {
            return version;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: a broken
         * patch chain requires a wipe-and-resync.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when the HMAC over the encrypted value of a single mutation
     * does not match the expected value after decryption.
     *
     * @apiNote
     * The value MAC ties an encrypted mutation to its plaintext. A
     * mismatch means either the ciphertext was corrupted or the
     * decryption key is wrong; in either case the collection cannot be
     * trusted and has to be resynced.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class ValueMacMismatch extends WhatsAppWebAppStateSyncException {
        /**
         * Constructs a new value MAC mismatch exception.
         */
        public ValueMacMismatch() {
            super("Value MAC mismatch: mutation value integrity check failed");
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: value
         * integrity failures require a wipe-and-resync.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when the HMAC over the encrypted index of a single mutation
     * does not match the expected value after decryption.
     *
     * @apiNote
     * The index MAC binds a mutation to its key in the key-value store
     * and prevents an attacker from substituting one mutation for
     * another. A mismatch is treated as data corruption and the
     * collection is resynced.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class IndexMacMismatch extends WhatsAppWebAppStateSyncException {
        /**
         * Constructs a new index MAC mismatch exception.
         */
        public IndexMacMismatch() {
            super("Index MAC mismatch: mutation index integrity check failed");
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: index
         * integrity failures require a wipe-and-resync.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when a sync mutation references an encryption key that is
     * not yet present locally.
     *
     * @apiNote
     * App-state keys are pushed from the primary phone and rotated
     * periodically. A missing key is normally transient: requesting the
     * key from a companion that has it and waiting for the reply is
     * enough to make progress. WA Web's {@code WAWebSyncdAntiTampering}
     * and {@code WAWebSyncdDecryptMutationsWrapper} raise the equivalent
     * {@code SyncdMissingKeyError} at the same sites.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdMissingKeyError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class MissingKey extends WhatsAppWebAppStateSyncException {
        /**
         * The identifier of the missing key.
         */
        private final byte[] keyId;

        /**
         * Constructs a new missing key exception.
         *
         * @param keyId the identifier of the missing key
         * @throws NullPointerException if {@code keyId} is {@code null}
         */
        public MissingKey(byte[] keyId) {
            super("Missing sync key with id " + HexFormat.of().formatHex(
                    Objects.requireNonNull(keyId, "keyId cannot be null")));
            this.keyId = keyId;
        }

        /**
         * Returns the identifier of the missing key.
         *
         * @return the key id, never {@code null}
         */
        public byte[] keyId() {
            return keyId;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code false}: the key may
         * arrive from a companion and let progress resume.
         */
        @Override
        public boolean isFatal() {
            return false;
        }
    }

    /**
     * Thrown when no companion device has the encryption key that a
     * mutation needs.
     *
     * @apiNote
     * Mirrors the {@code SyncdFatalErrorType.MISSING_KEY_ON_ALL_CLIENTS}
     * branch in WA Web's {@code WAWebSyncdStoreMissingKeys}. Cobalt asks
     * every other device about a missing key before concluding the key
     * is unrecoverable; when every device has answered that it does not
     * hold the key, this exception is raised. The affected collection
     * cannot be decrypted on this device without re-pairing.
     */
    @WhatsAppWebModule(moduleName = "WAWebSyncdStoreMissingKeys")
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class MissingKeyOnAllDevices extends WhatsAppWebAppStateSyncException {
        /**
         * The identifier of the key that no companion device holds.
         */
        private final byte[] keyId;

        /**
         * Constructs a new missing-key-on-all-devices exception.
         *
         * @param keyId the identifier of the missing key
         * @throws NullPointerException if {@code keyId} is {@code null}
         */
        public MissingKeyOnAllDevices(byte[] keyId) {
            super("Missing sync key with id " + HexFormat.of().formatHex(
                    Objects.requireNonNull(keyId, "keyId cannot be null")) + " on all companion devices");
            this.keyId = keyId;
        }

        /**
         * Returns the identifier of the missing key.
         *
         * @return the key id, never {@code null}
         */
        public byte[] keyId() {
            return keyId;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: with no
         * device holding the key, only re-pairing recovers the
         * collection.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when the wait for a missing app-state key expires before
     * any companion device has answered with the key.
     *
     * @apiNote
     * Mirrors the {@code SyncdFatalErrorType.TIMEOUT_WHILE_WAITING_FOR_MISSING_KEY}
     * branch in WA Web's {@code WAWebSyncdStoreMissingKeys}. The wait
     * timeout is longer than the per-device round-trip, so timing out
     * means the key is effectively unobtainable and the collection has
     * to be resynced from scratch.
     */
    @WhatsAppWebModule(moduleName = "WAWebSyncdStoreMissingKeys")
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class TimeoutWhileWaitingForMissingKey extends WhatsAppWebAppStateSyncException {
        /**
         * The identifier of the key whose wait timed out.
         */
        private final byte[] keyId;

        /**
         * Constructs a new timeout-while-waiting-for-missing-key exception.
         *
         * @param keyId the identifier of the missing key whose wait timed out
         * @throws NullPointerException if {@code keyId} is {@code null}
         */
        public TimeoutWhileWaitingForMissingKey(byte[] keyId) {
            super("Timeout waiting for missing sync key with id " + HexFormat.of().formatHex(
                    Objects.requireNonNull(keyId, "keyId cannot be null")));
            this.keyId = keyId;
        }

        /**
         * Returns the identifier of the missing key whose wait timed out.
         *
         * @return the key id, never {@code null}
         */
        public byte[] keyId() {
            return keyId;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: an unbounded
         * wait failure means the key is unobtainable.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when AES-GCM decryption of a mutation fails after the
     * correct key has been located.
     *
     * @apiNote
     * Mirrors WA Web's {@code WASyncdKmpEncryptionManager} reporting
     * {@code SyncdFatalErrorType.DECRYPTION_FAILED}. Typically indicates
     * corrupted ciphertext or an issue with key derivation; the
     * collection is wiped and resynced.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class DecryptionFailed extends WhatsAppWebAppStateSyncException {
        /**
         * Constructs a new decryption failed exception wrapping a cause.
         *
         * @param cause the underlying cryptographic exception
         */
        public DecryptionFailed(Throwable cause) {
            super("Failed to decrypt mutation", cause);
        }

        /**
         * Constructs a new decryption failed exception with a message and cause.
         *
         * @param message extra detail about the decryption failure
         * @param cause   the underlying cryptographic exception
         */
        public DecryptionFailed(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: a decryption
         * failure after the right key is in hand cannot be retried.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when downloading an external mutation blob fails.
     *
     * @apiNote
     * Mutations larger than the inline limit are stored on the media
     * servers; the patch only carries a URL and the encryption key.
     * Mirrors {@code SyncdRetryableError} surfaced by WA Web's
     * {@code WAWebSyncdNetCallbacksApi}; the operation can be retried.
     */
    @WhatsAppWebModule(moduleName = "WAWebSyncdNetCallbacksApi")
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdRetryableError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class ExternalDownloadFailed extends WhatsAppWebAppStateSyncException {
        /**
         * Constructs a new external download failed exception.
         *
         * @param cause the underlying I/O or network exception
         */
        public ExternalDownloadFailed(Throwable cause) {
            super("Failed to download external mutations", cause);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code false}: the
         * external blob may become available on a retry.
         */
        @Override
        public boolean isFatal() {
            return false;
        }
    }

    /**
     * Thrown when an external mutation blob downloads successfully but
     * its decoded payload is malformed.
     *
     * @apiNote
     * Mirrors {@code SyncdRetryableError} surfaced by WA Web's
     * {@code WAWebNonMessageDataRequestHandler}; common cause is a stale
     * recovery snapshot that no longer matches the current schema.
     */
    @WhatsAppWebModule(moduleName = "WAWebNonMessageDataRequestHandler")
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdRetryableError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class ExternalDecodeFailed extends WhatsAppWebAppStateSyncException {
        /**
         * Constructs a new external decode failed exception.
         *
         * @param cause the underlying parsing or decompression exception
         */
        public ExternalDecodeFailed(Throwable cause) {
            super("Failed to decode external mutations", cause);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code false}: re-fetching
         * a fresh blob may decode successfully.
         */
        @Override
        public boolean isFatal() {
            return false;
        }
    }

    /**
     * Thrown when computing an HMAC for a sync operation fails.
     *
     * @apiNote
     * Indicates a problem with the JCE provider, the key material, or
     * the cryptographic state of the process.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class MacComputationFailed extends WhatsAppWebAppStateSyncException {
        /**
         * Constructs a new MAC computation failed exception.
         *
         * @param cause the underlying cryptographic exception
         */
        public MacComputationFailed(Throwable cause) {
            super("Failed to compute MAC", cause);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: HMAC
         * computation is required for every outgoing patch.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when a SET mutation arrives without the timestamp every
     * SyncActionValue must carry.
     *
     * @apiNote
     * The validation only applies to SET mutations; REMOVE mutations
     * have no SyncActionValue and are unaffected.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class MissingActionTimestamp extends WhatsAppWebAppStateSyncException {
        /**
         * Constructs a new missing action timestamp exception.
         */
        public MissingActionTimestamp() {
            super("Missing action timestamp in sync mutation");
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: a SET
         * mutation without a timestamp cannot be ordered against peers.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when a single patch contains two mutations of the same
     * kind (two SETs or two REMOVEs) targeting the same index.
     *
     * @apiNote
     * Such a patch is malformed by construction and processing cannot
     * continue safely.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class DuplicateIndexInPatch extends WhatsAppWebAppStateSyncException {
        /**
         * The collection that contained the duplicate index.
         */
        private final SyncPatchType collectionName;

        /**
         * Constructs a new duplicate index in patch exception.
         *
         * @param collectionName the affected collection
         */
        public DuplicateIndexInPatch(SyncPatchType collectionName) {
            super("Same index for multiple mutations in patch for collection " + collectionName);
            this.collectionName = collectionName;
        }

        /**
         * Returns the collection that contained the duplicate index.
         *
         * @return the collection identifier
         */
        public SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: a malformed
         * patch cannot be applied partially.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when two patches in the same collection share the same
     * version number.
     *
     * @apiNote
     * Patch versions are required to be unique; a duplicate makes it
     * impossible to determine ordering and the response is rejected.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class DuplicatePatchVersion extends WhatsAppWebAppStateSyncException {
        /**
         * The collection that contained the duplicate version.
         */
        private final SyncPatchType collectionName;

        /**
         * The version that appeared on more than one patch.
         */
        private final long version;

        /**
         * Constructs a new duplicate patch version exception.
         *
         * @param collectionName the affected collection
         * @param version        the duplicated version number
         */
        public DuplicatePatchVersion(SyncPatchType collectionName, long version) {
            super("Duplicate patch version " + version + " in collection " + collectionName);
            this.collectionName = Objects.requireNonNull(collectionName);
            this.version = version;
        }

        /**
         * Returns the collection that contained the duplicate version.
         *
         * @return the collection identifier, never {@code null}
         */
        public SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * Returns the duplicated version number.
         *
         * @return the version number
         */
        public long version() {
            return version;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: a duplicate
         * version makes patch ordering ambiguous.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown for sync failures that do not match any of the more
     * specific categories.
     *
     * @apiNote
     * Treated as fatal because the cause is unknown and the safe
     * recovery is to wipe the affected collection and resync.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class UnexpectedError extends WhatsAppWebAppStateSyncException {
        /**
         * Constructs a new unexpected error exception wrapping a cause.
         *
         * @param cause the underlying unexpected exception
         */
        public UnexpectedError(Throwable cause) {
            super("Unexpected sync error", cause);
        }

        /**
         * Constructs a new unexpected error exception with a message and cause.
         *
         * @param message extra detail about the unexpected error
         * @param cause   the underlying unexpected exception
         */
        public UnexpectedError(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: when the
         * cause is unknown, only a wipe-and-resync is safe.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when there is a gap in the patch sequence the server
     * delivered: the lowest version in the response is greater than the
     * local version plus one.
     *
     * @apiNote
     * Equivalent to WA Web's {@code WAWebSyncdAntiTampering}
     * version-check error raised when {@code minPatchVersion >
     * localVersion + 1}. The chain integrity is broken and the only safe
     * recovery is to wipe the collection and resync from a fresh
     * snapshot.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class MissingPatches extends WhatsAppWebAppStateSyncException {
        /**
         * The collection that has missing patches.
         */
        private final SyncPatchType collectionName;

        /**
         * The current local version of the collection.
         */
        private final long localVersion;

        /**
         * The lowest version among the patches that did arrive.
         */
        private final long minPatchVersion;

        /**
         * Constructs a new missing patches exception.
         *
         * @param collectionName  the affected collection
         * @param localVersion    the current local version
         * @param minPatchVersion the lowest version among received patches
         * @throws NullPointerException if {@code collectionName} is {@code null}
         */
        public MissingPatches(SyncPatchType collectionName, long localVersion, long minPatchVersion) {
            super("Missing patches for collection " + collectionName
                    + ": local version " + localVersion + ", min patch version " + minPatchVersion);
            this.collectionName = Objects.requireNonNull(collectionName);
            this.localVersion = localVersion;
            this.minPatchVersion = minPatchVersion;
        }

        /**
         * Returns the collection that has missing patches.
         *
         * @return the collection identifier, never {@code null}
         */
        public SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * Returns the current local version of the collection.
         *
         * @return the local version
         */
        public long localVersion() {
            return localVersion;
        }

        /**
         * Returns the lowest version among the patches that did arrive.
         *
         * @return the minimum patch version
         */
        public long minPatchVersion() {
            return minPatchVersion;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: a broken
         * patch chain cannot be back-filled.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when the server marks a patch as terminal, signaling that
     * the collection data is unrecoverable.
     *
     * @apiNote
     * The exit code carries the server's reason; the collection is
     * wiped and resynchronized from scratch.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdFatalError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class TerminalPatch extends WhatsAppWebAppStateSyncException {
        /**
         * The collection that received the terminal patch.
         */
        private final SyncPatchType collectionName;

        /**
         * The exit code declaring why the data is unrecoverable.
         */
        private final DisconnectReason exitCode;

        /**
         * Constructs a new terminal patch exception.
         *
         * @param collectionName the affected collection
         * @param exitCode       the exit code carried by the patch
         * @throws NullPointerException if {@code collectionName} or {@code exitCode} is {@code null}
         */
        public TerminalPatch(SyncPatchType collectionName, DisconnectReason exitCode) {
            super("Terminal patch for collection " + collectionName + " with exit code: " + exitCode);
            this.collectionName = Objects.requireNonNull(collectionName);
            this.exitCode = Objects.requireNonNull(exitCode);
        }

        /**
         * Returns the collection that received the terminal patch.
         *
         * @return the collection identifier, never {@code null}
         */
        public SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * Returns the exit code carried by the terminal patch.
         *
         * @return the exit code, never {@code null}
         */
        public DisconnectReason exitCode() {
            return exitCode;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code true}: a terminal
         * patch is the server explicitly declaring the data
         * unrecoverable.
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Thrown when the server returns a {@code 409 Conflict} response to
     * a patch publication, meaning a newer version exists on the server.
     *
     * @apiNote
     * The client refetches the collection and applies the patch on top
     * of the new version. The flag {@link #hasMorePatches()} mirrors WA
     * Web's {@code ConflictHasMore} variant of the {@code Conflict}
     * server response and tells the caller whether further patches are
     * pending after the conflict.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdRetryableError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class Conflict extends WhatsAppWebAppStateSyncException {
        /**
         * Whether the server signaled that more patches are available
         * after the conflict.
         */
        private final boolean hasMorePatches;

        /**
         * Constructs a new conflict exception.
         *
         * @param hasMorePatches whether the server signaled more patches are available
         */
        public Conflict(boolean hasMorePatches) {
            super("Server returned 409 conflict");
            this.hasMorePatches = hasMorePatches;
        }

        /**
         * Returns whether the server signaled that more patches are
         * available after the conflict.
         *
         * @return {@code true} if more patches are pending
         */
        public boolean hasMorePatches() {
            return hasMorePatches;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code false}: a conflict
         * is resolved by refetch-and-reapply.
         */
        @Override
        public boolean isFatal() {
            return false;
        }
    }

    /**
     * Thrown when the server returns a retryable error code other than
     * {@code 409 Conflict} or one of the fatal codes.
     *
     * @apiNote
     * The server can attach a backoff hint (in milliseconds) that is
     * exposed via {@link #serverBackoffMs()} and should be honored
     * before the next attempt. Equivalent to the {@code backoff}
     * property WA Web's {@code SyncdRetryableError} carries.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdError", exports = "SyncdRetryableError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final class RetryableServerError extends WhatsAppWebAppStateSyncException {
        /**
         * The error code the server returned.
         */
        private final String errorCode;

        /**
         * The server-suggested backoff in milliseconds, or {@code null}
         * when none was provided.
         */
        private final Long serverBackoffMs;

        /**
         * Constructs a new retryable server error exception.
         *
         * @param errorCode the server error code
         */
        public RetryableServerError(String errorCode) {
            this(errorCode, null);
        }

        /**
         * Constructs a new retryable server error exception with a server-suggested backoff.
         *
         * @param errorCode       the server error code
         * @param serverBackoffMs the server-suggested backoff in milliseconds, or {@code null}
         */
        public RetryableServerError(String errorCode, Long serverBackoffMs) {
            super("Server returned retryable error code: " + errorCode);
            this.errorCode = errorCode;
            this.serverBackoffMs = serverBackoffMs;
        }

        /**
         * Returns the error code the server returned.
         *
         * @return the error code
         */
        public String errorCode() {
            return errorCode;
        }

        /**
         * Returns the server-suggested backoff in milliseconds, when one was provided.
         *
         * @return the backoff in milliseconds, or {@code null} when none was provided
         */
        public Long serverBackoffMs() {
            return serverBackoffMs;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation always returns {@code false}: the server
         * has explicitly classified the failure as retryable.
         */
        @Override
        public boolean isFatal() {
            return false;
        }
    }
}
