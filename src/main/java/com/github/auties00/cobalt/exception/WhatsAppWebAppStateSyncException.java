package com.github.auties00.cobalt.exception;

import com.github.auties00.cobalt.model.error.DisconnectReason;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import java.util.HexFormat;
import java.util.Objects;

/**
 * Base exception for Web App State (syncd) synchronization failures.
 * <p>
 * Web App State sync is the mechanism WhatsApp uses to synchronize application state
 * across multiple devices including contacts, chats, settings, starred messages,
 * blocked contacts, and more. The state is stored as encrypted key-value patches
 * that are synchronized via the WhatsApp servers.
 *
 * <h2>Web App State Architecture</h2>
 * The synchronization system uses:
 * <ul>
 *   <li><b>Collections:</b> Named groups of related state (e.g., "regular_high", "critical_block")</li>
 *   <li><b>Patches:</b> Incremental updates containing state changes</li>
 *   <li><b>Mutations:</b> Individual key-value operations (set/delete)</li>
 *   <li><b>Snapshots:</b> Complete state dumps for initial sync</li>
 *   <li><b>Integrity:</b> HMAC-based verification of patch sequences</li>
 *   <li><b>External blobs:</b> Large mutations stored separately and referenced by URL</li>
 * </ul>
 *
 * <h2>Exception Hierarchy</h2>
 * <ul>
 *   <li><b>MAC validation failures (fatal):</b>
 *     <ul>
 *       <li>{@link SnapshotMacMismatch} - Snapshot integrity check failed</li>
 *       <li>{@link PatchMacMismatch} - Patch integrity check failed</li>
 *       <li>{@link ValueMacMismatch} - Mutation value integrity check failed</li>
 *       <li>{@link IndexMacMismatch} - Mutation index integrity check failed</li>
 *     </ul>
 *   </li>
 *   <li><b>Key errors (retryable):</b>
 *     <ul>
 *       <li>{@link MissingKey} - Required encryption key not yet available</li>
 *     </ul>
 *   </li>
 *   <li><b>Decryption errors (retryable):</b>
 *     <ul>
 *       <li>{@link DecryptionFailed} - Cryptographic decryption operation failed</li>
 *     </ul>
 *   </li>
 *   <li><b>External mutation errors (retryable):</b>
 *     <ul>
 *       <li>{@link ExternalDownloadFailed} - Failed to download external blob</li>
 *       <li>{@link ExternalDecodeFailed} - Failed to decode external blob data</li>
 *     </ul>
 *   </li>
 *   <li><b>Computation errors (fatal):</b>
 *     <ul>
 *       <li>{@link MacComputationFailed} - HMAC computation failed</li>
 *     </ul>
 *   </li>
 *   <li><b>Unknown errors (fatal):</b>
 *     <ul>
 *       <li>{@link UnexpectedError} - Unclassified sync failure</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Fatality and Recovery</h2>
 * The {@link #isFatal()} method indicates whether the error is recoverable:
 * <ul>
 *   <li><b>Fatal errors:</b> Require clearing local state and performing a full resync.
 *       These typically indicate data integrity issues that cannot be resolved without
 *       a complete state reset from scratch.</li>
 *   <li><b>Non-fatal errors:</b> Can be retried, often after obtaining missing keys
 *       or waiting for network conditions to improve. Use exponential backoff.</li>
 * </ul>
 *
 * @see SnapshotMacMismatch
 * @see PatchMacMismatch
 * @see ValueMacMismatch
 * @see IndexMacMismatch
 * @see MissingKey
 * @see DecryptionFailed
 * @see ExternalDownloadFailed
 * @see ExternalDecodeFailed
 * @see MacComputationFailed
 * @see UnexpectedError
 */
public sealed abstract class WhatsAppWebAppStateSyncException extends WhatsAppException
        permits WhatsAppWebAppStateSyncException.SnapshotMacMismatch,
                WhatsAppWebAppStateSyncException.PatchMacMismatch,
                WhatsAppWebAppStateSyncException.ValueMacMismatch,
                WhatsAppWebAppStateSyncException.IndexMacMismatch,
                WhatsAppWebAppStateSyncException.MissingKey,
                WhatsAppWebAppStateSyncException.MissingKeyOnAllDevices,
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
     * Exception thrown when snapshot MAC validation fails.
     * <p>
     * The snapshot MAC is computed over the entire sync state snapshot and validates
     * that the data hasn't been corrupted or tampered with during transmission.
     * Snapshots are used for initial sync or resync after fatal errors.
     *
     * <h2>MAC Computation</h2>
     * The snapshot MAC is computed using HMAC-SHA256 over:
     * <ul>
     *   <li>The snapshot version number</li>
     *   <li>All mutation records in the snapshot</li>
     *   <li>The collection identifier</li>
     * </ul>
     *
     * <h2>Failure Implications</h2>
     * <ul>
     *   <li>The snapshot data was corrupted during transmission</li>
     *   <li>The snapshot was tampered with (potential attack)</li>
     *   <li>The MAC key is incorrect or corrupted</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * This is a fatal error. Clear the affected collection's local state and request
     * a new snapshot from the server.
     */
    public static final class SnapshotMacMismatch extends WhatsAppWebAppStateSyncException {
        /**
         * The sync collection (patch type) that failed validation.
         */
        private final SyncPatchType collectionName;

        /**
         * The version number of the snapshot that failed MAC validation.
         */
        private final long version;

        /**
         * Constructs a new snapshot MAC mismatch exception.
         *
         * @param collectionName the sync collection that failed validation; must not be null
         * @param version        the version number of the failing snapshot
         * @throws NullPointerException if collectionName is null
         */
        public SnapshotMacMismatch(SyncPatchType collectionName, long version) {
            super("Snapshot MAC mismatch for collection " + collectionName + " at version " + version);
            this.collectionName = Objects.requireNonNull(collectionName);
            this.version = version;
        }

        /**
         * Returns the sync collection that failed validation.
         * <p>
         * Different collections contain different types of state data (contacts, settings, etc.).
         *
         * @return the patch type / collection name; never null
         */
        public SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * Returns the version number of the snapshot that failed validation.
         * <p>
         * The version number identifies a specific point in the sync state timeline.
         *
         * @return the snapshot version
         */
        public long version() {
            return version;
        }

        /**
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code true} - snapshot MAC failures are always fatal
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Exception thrown when patch MAC validation fails.
     * <p>
     * Patches are incremental state updates applied on top of a base snapshot.
     * The patch MAC validates the integrity of these updates.
     *
     * <h2>MAC Computation</h2>
     * The patch MAC is computed using HMAC-SHA256 over:
     * <ul>
     *   <li>The patch version number</li>
     *   <li>All mutation records in the patch</li>
     *   <li>The previous patch's MAC (chain linking)</li>
     * </ul>
     *
     * <h2>Failure Implications</h2>
     * <ul>
     *   <li>The patch data was corrupted during transmission</li>
     *   <li>The patch was tampered with (potential attack)</li>
     *   <li>The patch chain was broken (missing intermediate patches)</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * This is a fatal error. Clear the affected collection's local state and request
     * a fresh snapshot from the server.
     */
    public static final class PatchMacMismatch extends WhatsAppWebAppStateSyncException {
        /**
         * The sync collection (patch type) that failed validation.
         */
        private final SyncPatchType collectionName;

        /**
         * The version number of the patch that failed MAC validation.
         */
        private final long version;

        /**
         * Constructs a new patch MAC mismatch exception.
         *
         * @param collectionName the sync collection that failed validation; must not be null
         * @param version        the version number of the failing patch
         * @throws NullPointerException if collectionName is null
         */
        public PatchMacMismatch(SyncPatchType collectionName, long version) {
            super("Patch MAC mismatch for collection " + collectionName + " at version " + version);
            this.collectionName = Objects.requireNonNull(collectionName);
            this.version = version;
        }

        /**
         * Returns the sync collection that failed validation.
         * <p>
         * Different collections contain different types of state data (contacts, settings, etc.).
         *
         * @return the patch type / collection name; never null
         */
        public SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * Returns the version number of the patch that failed validation.
         * <p>
         * The version number identifies a specific point in the sync state timeline.
         *
         * @return the patch version
         */
        public long version() {
            return version;
        }

        /**
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code true} - patch MAC failures are always fatal
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Exception thrown when mutation value MAC validation fails.
     * <p>
     * Each mutation's encrypted value has an HMAC that ensures the decrypted
     * content matches what was originally encrypted. This provides end-to-end
     * integrity verification of individual state values.
     *
     * <h2>Value MAC Structure</h2>
     * The value MAC is computed over the plaintext value before encryption and
     * verified after decryption. This ensures:
     * <ul>
     *   <li>The value wasn't modified during encryption</li>
     *   <li>The decryption key is correct</li>
     *   <li>The ciphertext wasn't tampered with</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * This is a fatal error indicating potential data corruption or tampering.
     * The affected collection must be reset and re-synced.
     */
    public static final class ValueMacMismatch extends WhatsAppWebAppStateSyncException {
        /**
         * Constructs a new value MAC mismatch exception.
         */
        public ValueMacMismatch() {
            super("Value MAC mismatch: mutation value integrity check failed");
        }

        /**
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code true} - value MAC failures are always fatal
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Exception thrown when mutation index MAC validation fails.
     * <p>
     * Each mutation is identified by an encrypted index that serves as the key
     * in the key-value store. The index MAC ensures this identifier hasn't been
     * tampered with.
     *
     * <h2>Index MAC Purpose</h2>
     * The index MAC provides:
     * <ul>
     *   <li>Integrity of the mutation identifier</li>
     *   <li>Prevention of mutation substitution attacks</li>
     *   <li>Verification that decryption produced valid data</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * This is a fatal error indicating potential data corruption or tampering.
     * The affected collection must be reset and re-synced.
     */
    public static final class IndexMacMismatch extends WhatsAppWebAppStateSyncException {
        /**
         * Constructs a new index MAC mismatch exception.
         */
        public IndexMacMismatch() {
            super("Index MAC mismatch: mutation index integrity check failed");
        }

        /**
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code true} - index MAC failures are always fatal
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Exception thrown when a required encryption key is missing.
     * <p>
     * Sync state mutations are encrypted with rotating app state keys. Each key
     * has a unique identifier and a limited lifetime. This exception occurs when
     * a mutation references a key that the client doesn't yet have.
     *
     * <h2>Key Architecture</h2>
     * <ul>
     *   <li>Keys are distributed from the primary device to companion devices</li>
     *   <li>Each key has a unique ID (fingerprint) used for lookup</li>
     *   <li>Keys are rotated periodically for forward secrecy</li>
     *   <li>Old keys are retained for decrypting historical data</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * This is a retryable error:
     * <ol>
     *   <li>Request app state keys from the primary device</li>
     *   <li>Wait for the key sync to complete</li>
     *   <li>Retry the web app state sync operation</li>
     * </ol>
     */
    public static final class MissingKey extends WhatsAppWebAppStateSyncException {
        /**
         * The identifier of the missing encryption key.
         */
        private final byte[] keyId;

        /**
         * Constructs a new missing key exception.
         *
         * @param keyId the identifier of the missing key; must not be null
         * @throws NullPointerException if keyId is null
         */
        public MissingKey(byte[] keyId) {
            super("Missing sync key with id " + HexFormat.of().formatHex(
                    Objects.requireNonNull(keyId, "keyId cannot be null")));
            this.keyId = keyId;
        }

        /**
         * Returns the identifier of the missing key.
         * <p>
         * This ID can be used to request the specific key from the primary device
         * or to log the missing key for debugging purposes.
         *
         * @return the key ID as a byte array; never null
         */
        public byte[] keyId() {
            return keyId;
        }

        /**
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code false} - missing key errors are retryable
         */
        @Override
        public boolean isFatal() {
            return false;
        }
    }

    /**
     * Exception thrown when a sync key is missing on all companion devices.
     * <p>
     * Per WhatsApp Web WAWebSyncdStoreMissingKeys: when a sync key is missing,
     * the client asks all companion devices. If all devices respond that they
     * don't have the key, the sync state is unrecoverable.
     *
     * <h2>Detection</h2>
     * This condition is detected when:
     * <ul>
     *   <li>All companion devices have been asked for the key</li>
     *   <li>All devices responded that they don't have it</li>
     *   <li>No pending responses remain (e.g., after a device is removed)</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * This is a fatal error. The sync state cannot be recovered without
     * logging out and re-linking the device.
     */
    public static final class MissingKeyOnAllDevices extends WhatsAppWebAppStateSyncException {
        private final byte[] keyId;

        public MissingKeyOnAllDevices(byte[] keyId) {
            super("Missing sync key with id " + HexFormat.of().formatHex(
                    Objects.requireNonNull(keyId, "keyId cannot be null")) + " on all companion devices");
            this.keyId = keyId;
        }

        public byte[] keyId() {
            return keyId;
        }

        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Exception thrown when mutation decryption fails.
     * <p>
     * This occurs when the cryptographic decryption operation fails after
     * the correct key has been located. Possible causes include corrupted
     * ciphertext or issues with key derivation.
     *
     * <h2>Decryption Process</h2>
     * Mutation decryption uses:
     * <ul>
     *   <li>AES-256-GCM for authenticated encryption</li>
     *   <li>Key derivation from app state key material</li>
     *   <li>Per-mutation nonce/IV for uniqueness</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * This is a retryable error. If decryption consistently fails:
     * <ol>
     *   <li>Verify the key material is correct</li>
     *   <li>Request the mutation again from the server</li>
     *   <li>If failures persist, treat as fatal and resync</li>
     * </ol>
     */
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
         * @param message additional context about the decryption failure
         * @param cause   the underlying cryptographic exception
         */
        public DecryptionFailed(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code false} - decryption errors are retryable
         */
        @Override
        public boolean isFatal() {
            return false;
        }
    }

    /**
     * Exception thrown when downloading external mutations fails.
     * <p>
     * Large mutations (exceeding inline size limits) are stored as external blobs
     * on WhatsApp's media servers. The mutation record contains a URL to download
     * the actual data. This exception occurs when that download fails.
     *
     * <h2>External Mutation Architecture</h2>
     * <ul>
     *   <li>Mutations larger than ~16KB are stored externally</li>
     *   <li>The patch contains a blob reference (URL + encryption key)</li>
     *   <li>The client downloads and decrypts the blob separately</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * This is a retryable error:
     * <ol>
     *   <li>Wait with exponential backoff</li>
     *   <li>Retry the download</li>
     *   <li>If the URL has expired, request fresh sync data</li>
     * </ol>
     */
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
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code false} - download errors are retryable
         */
        @Override
        public boolean isFatal() {
            return false;
        }
    }

    /**
     * Exception thrown when decoding external mutations fails.
     * <p>
     * After downloading external mutation blobs, the data must be decrypted
     * and decoded from protobuf format. This exception occurs when the decoded
     * data is malformed or doesn't match expected structure.
     *
     * <h2>Decode Process</h2>
     * <ol>
     *   <li>Download encrypted blob from URL</li>
     *   <li>Decrypt using the blob's encryption key</li>
     *   <li>Decompress if compressed</li>
     *   <li>Parse as protobuf mutation list</li>
     * </ol>
     *
     * <h2>Recovery</h2>
     * This is a retryable error. If decoding consistently fails, the blob
     * may be corrupted and a fresh sync request may be needed.
     */
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
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code false} - decode errors are retryable
         */
        @Override
        public boolean isFatal() {
            return false;
        }
    }

    /**
     * Exception thrown when MAC computation fails.
     * <p>
     * This occurs when the cryptographic HMAC operation fails during sync
     * processing. This is typically caused by JCE provider issues, invalid
     * key material, or memory corruption.
     *
     * <h2>Possible Causes</h2>
     * <ul>
     *   <li>JCE security provider not available or misconfigured</li>
     *   <li>Invalid or corrupted MAC key material</li>
     *   <li>Memory corruption affecting cryptographic state</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * This is a fatal error. The cryptographic subsystem may be compromised.
     * Re-establish keys and resync from scratch.
     */
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
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code true} - MAC computation failures are fatal
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Exception thrown when a SET mutation's {@code SyncActionValue} has no timestamp.
     *
     * <p>Per WhatsApp Web {@code validateAndTypeSetMutations}: every SET mutation
     * must have a non-{@code null} timestamp in its {@code SyncActionValue}.
     * A missing timestamp is a fatal error that stops processing of the
     * affected collection.
     *
     * <p>This validation applies only to SET mutations. REMOVE mutations
     * do not carry a {@code SyncActionValue} and are not subject to this check.
     */
    public static final class MissingActionTimestamp extends WhatsAppWebAppStateSyncException {
        /**
         * Constructs a new missing action timestamp exception.
         */
        public MissingActionTimestamp() {
            super("Missing action timestamp in sync mutation");
        }

        /**
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code true} - missing action timestamp is always fatal
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Exception thrown when a patch contains multiple mutations with the same
     * index for the same operation type.
     *
     * <p>Per WhatsApp Web {@code validateNoSameIndexForMultipleMutations}:
     * if two SET mutations or two REMOVE mutations in a single patch share
     * the same index, the patch is malformed and processing is aborted
     * with a fatal error.
     *
     * <p>For snapshots, duplicate indices are logged as a metric but do
     * not trigger a fatal error.
     */
    public static final class DuplicateIndexInPatch extends WhatsAppWebAppStateSyncException {
        /**
         * The sync collection that contained the duplicate index.
         */
        private final SyncPatchType collectionName;

        /**
         * Constructs a new duplicate index in patch exception.
         *
         * @param collectionName the affected collection; must not be {@code null}
         */
        public DuplicateIndexInPatch(SyncPatchType collectionName) {
            super("Same index for multiple mutations in patch for collection " + collectionName);
            this.collectionName = collectionName;
        }

        /**
         * Returns the affected collection.
         *
         * @return the patch type; never {@code null}
         */
        public SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code true} - duplicate index in patch is always fatal
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Exception thrown for unexpected or unclassified sync errors.
     * <p>
     * This is a catch-all for errors that don't fit into other categories.
     * These are treated as fatal since the specific cause is unknown and
     * safe recovery cannot be guaranteed.
     *
     * <h2>When This Occurs</h2>
     * <ul>
     *   <li>New error types not yet categorized</li>
     *   <li>Internal state inconsistencies</li>
     *   <li>Unexpected server responses</li>
     *   <li>Programming errors in sync logic</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * This is a fatal error. Log the exception details for debugging and
     * perform a full resync from scratch.
     */
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
         * @param message additional context about the unexpected error
         * @param cause   the underlying unexpected exception
         */
        public UnexpectedError(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Returns whether this exception represents a fatal error.
         *
         * @return {@code true} - unexpected errors are treated as fatal
         */
        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Exception thrown when the server did not send all expected patches.
     *
     * <p>This occurs when the minimum version of received patches is greater
     * than the local version plus one, indicating a gap in the patch sequence.
     *
     * <p>This is a fatal error requiring a full resync from snapshot.
     */
    public static final class MissingPatches extends WhatsAppWebAppStateSyncException {
        private final SyncPatchType collectionName;
        private final long localVersion;
        private final long minPatchVersion;

        /**
         * Constructs a new missing patches exception.
         *
         * @param collectionName  the affected collection
         * @param localVersion    the current local version
         * @param minPatchVersion the minimum version among received patches
         */
        public MissingPatches(SyncPatchType collectionName, long localVersion, long minPatchVersion) {
            super("Missing patches for collection " + collectionName
                    + ": local version " + localVersion + ", min patch version " + minPatchVersion);
            this.collectionName = Objects.requireNonNull(collectionName);
            this.localVersion = localVersion;
            this.minPatchVersion = minPatchVersion;
        }

        /**
         * Returns the affected collection.
         *
         * @return the patch type; never null
         */
        public SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * Returns the current local version.
         *
         * @return the local version
         */
        public long localVersion() {
            return localVersion;
        }

        /**
         * Returns the minimum version among received patches.
         *
         * @return the minimum patch version
         */
        public long minPatchVersion() {
            return minPatchVersion;
        }

        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Exception thrown when a patch contains a terminal exit code.
     *
     * <p>All exit codes are terminal and indicate the server is signaling
     * that the collection data is unrecoverable. Known codes:
     * <ul>
     *   <li>100 — missing data</li>
     *   <li>101 — deserialization error</li>
     * </ul>
     *
     * <p>This is a fatal error.
     */
    public static final class TerminalPatch extends WhatsAppWebAppStateSyncException {
        private final SyncPatchType collectionName;
        private final DisconnectReason exitCode;

        /**
         * Constructs a new terminal patch exception.
         *
         * @param collectionName the affected collection
         * @param exitCode       the exit code from the patch
         */
        public TerminalPatch(SyncPatchType collectionName, DisconnectReason exitCode) {
            super("Terminal patch for collection " + collectionName + " with exit code: " + exitCode);
            this.collectionName = Objects.requireNonNull(collectionName);
            this.exitCode = Objects.requireNonNull(exitCode);
        }

        /**
         * Returns the affected collection.
         *
         * @return the patch type; never null
         */
        public SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * Returns the exit code from the patch.
         *
         * @return the exit code; never null
         */
        public DisconnectReason exitCode() {
            return exitCode;
        }

        @Override
        public boolean isFatal() {
            return true;
        }
    }

    /**
     * Exception thrown when the server returns a 409 conflict response.
     *
     * <p>This indicates that the server rejected the client's patch because
     * a newer version exists. The client should re-fetch and retry.
     */
    public static final class Conflict extends WhatsAppWebAppStateSyncException {
        private final boolean hasMorePatches;

        /**
         * Constructs a new conflict exception.
         *
         * @param hasMorePatches whether the server indicated more patches are available
         */
        public Conflict(boolean hasMorePatches) {
            super("Server returned 409 conflict");
            this.hasMorePatches = hasMorePatches;
        }

        /**
         * Returns whether the server indicated more patches are available.
         *
         * @return {@code true} if more patches are available
         */
        public boolean hasMorePatches() {
            return hasMorePatches;
        }

        @Override
        public boolean isFatal() {
            return false;
        }
    }

    /**
     * Exception thrown when the server returns a retryable error code.
     *
     * <p>This covers error codes other than 409 (conflict) and 400/404 (fatal).
     */
    public static final class RetryableServerError extends WhatsAppWebAppStateSyncException {
        private final String errorCode;

        /**
         * Constructs a new retryable server error exception.
         *
         * @param errorCode the server error code
         */
        public RetryableServerError(String errorCode) {
            super("Server returned retryable error code: " + errorCode);
            this.errorCode = errorCode;
        }

        /**
         * Returns the server error code.
         *
         * @return the error code
         */
        public String errorCode() {
            return errorCode;
        }

        @Override
        public boolean isFatal() {
            return false;
        }
    }
}
