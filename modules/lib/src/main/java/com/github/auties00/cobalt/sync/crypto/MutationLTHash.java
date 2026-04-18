package com.github.auties00.cobalt.sync.crypto;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionEntry;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.store.WhatsAppStore;

import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.logging.Logger;

/**
 * LT-Hash (Lattice Hash) implementation for anti-tampering verification.
 *
 * <p>LT-Hash is a cryptographic accumulator with the following properties:
 * <ul>
 *   <li><b>Commutative</b>: hash(a, b) = hash(b, a)</li>
 *   <li><b>Associative</b>: hash(hash(a, b), c) = hash(a, hash(b, c))</li>
 *   <li><b>Reversible</b>: hash_remove(hash(a, b), b) = hash(a)</li>
 *   <li><b>Deterministic</b>: Same input always produces same output</li>
 * </ul>
 *
 * <p>This implementation uses HKDF-expanded values with unsigned 16-bit wrapping
 * arithmetic, matching the WhatsApp Web {@code WACryptoLtHash} algorithm. Each
 * value MAC is expanded via HKDF-SHA256 to 128 bytes, then treated as 64
 * little-endian Uint16 values for pointwise addition or subtraction with
 * wrapping overflow (mod 65536).
 *
 * @implNote WACryptoLtHash.LtHash16 (LT-Hash primitive operations),
 *           WAWebSyncdAntiTamperingLtHash (checkLtHash, reportCollectionInconsistency —
 *           consistency verification against stored sync action entries).
 *           The WA Web {@code getLidMigrationStage} and {@code getPureSyncDSessionDetails}
 *           exports are intentionally NOT ported: both are WAM telemetry helpers that
 *           return enum constants used only as event properties on the
 *           {@code WamSyncdHashMismatchDetection} and related telemetry events. Cobalt
 *           does not emit these WAM events, so the helpers have no call sites.
 */
@WhatsAppWebModule(moduleName = "WACryptoLtHash")
@WhatsAppWebModule(moduleName = "WAWebSyncdAntiTamperingLtHash")
public final class MutationLTHash {
    /**
     * Logger for LT-Hash consistency check diagnostics.
     *
     * @implNote WAWebSyncdAntiTamperingLtHash.checkLtHash — WALogger.ERROR replaced
     *           with JUL logger per Cobalt conventions
     */
    private static final Logger LOGGER = Logger.getLogger(MutationLTHash.class.getName()); // WAWebSyncdAntiTamperingLtHash: WALogger

    /**
     * Length of the hash state in bytes (64 little-endian Uint16 values).
     * Exported as {@code KEY_LENGTH_BYTES} in WA Web.
     *
     * @implNote WACryptoLtHash.KEY_LENGTH_BYTES — constant {@code u = 128}
     */
    @WhatsAppWebExport(moduleName = "WACryptoLtHash", exports = "KEY_LENGTH_BYTES", adaptation = WhatsAppAdaptation.DIRECT)
    public static final int HASH_LENGTH = 128; // WACryptoLtHash: u = 128 (KEY_LENGTH_BYTES)

    /**
     * HKDF info string used for expanding value MACs.
     * Despite the WA Web variable name "salt", this is used as the HKDF info/context
     * parameter, not as the HKDF salt (which is null/default zeros).
     *
     * @implNote WACryptoLtHash.LT_HASH_ANTI_TAMPERING — {@code new LtHash16("WhatsApp Patch Integrity")} stored as {@code this.salt}
     */
    private static final byte[] HKDF_INFO = "WhatsApp Patch Integrity".getBytes(); // WACryptoLtHash: m = new d("WhatsApp Patch Integrity")

    /**
     * The empty/zero hash state.
     * Used as the initial state for a collection with no mutations.
     *
     * @implNote WACryptoLtHash.EMPTY_LT_HASH — constant {@code c = new ArrayBuffer(u)}
     */
    @WhatsAppWebExport(moduleName = "WACryptoLtHash", exports = "EMPTY_LT_HASH", adaptation = WhatsAppAdaptation.DIRECT)
    public static final byte[] EMPTY_HASH = new byte[HASH_LENGTH]; // WACryptoLtHash: c = new ArrayBuffer(u) (EMPTY_LT_HASH)

    /**
     * Prevents instantiation of this utility class.
     *
     * @implNote WACryptoLtHash.LT_HASH_ANTI_TAMPERING
     */
    private MutationLTHash() {

    }

    /**
     * Expands a value MAC to 128 bytes using HKDF-SHA256.
     *
     * <p>Performs HKDF-Extract with null salt (defaults to 32 zero bytes per RFC 5869)
     * followed by HKDF-Expand with info="WhatsApp Patch Integrity" and length=128.
     *
     * @implNote WACryptoLtHash.LtHash16.$1/$2 — calls {@code WACryptoHkdf.extractAndExpand(valueMac, this.salt, 128)}
     * @param valueMac the value MAC to expand
     * @return the expanded 128-byte value
     */
    private static byte[] expand(byte[] valueMac) {
        try {
            var kdf = KDF.getInstance("HKDF-SHA256"); // WACryptoHkdf.extractAndExpand
            var params = HKDFParameterSpec.ofExtract() // WACryptoHkdf.extractSha256(null, valueMac)
                    .addIKM(valueMac)
                    .thenExpand(HKDF_INFO, HASH_LENGTH); // WACryptoHkdf.expand(prk, this.salt, 128)
            return kdf.deriveData(params);
        } catch (GeneralSecurityException e) {
            throw new InternalError("Failed to expand value MAC via HKDF", e);
        }
    }

    /**
     * Performs pointwise unsigned 16-bit arithmetic on two hash buffers.
     *
     * <p>Treats both input buffers as arrays of 64 little-endian Uint16 values
     * and applies the given operator pointwise with unsigned 16-bit wrapping.
     *
     * @implNote WACryptoLtHash.LtHash16.performPointwiseWithOverflow
     * @param hash the current hash state buffer
     * @param expanded the HKDF-expanded value buffer
     * @param addition {@code true} for addition, {@code false} for subtraction
     * @return new hash state after the pointwise operation
     */
    private static byte[] performPointwiseWithOverflow(byte[] hash, byte[] expanded, boolean addition) {
        var hashBuf = ByteBuffer.wrap(hash).order(ByteOrder.LITTLE_ENDIAN); // WACryptoLtHash.performPointwiseWithOverflow: new DataView(t)
        var expandedBuf = ByteBuffer.wrap(expanded).order(ByteOrder.LITTLE_ENDIAN); // WACryptoLtHash.performPointwiseWithOverflow: new DataView(n)
        var result = ByteBuffer.allocate(hashBuf.capacity()).order(ByteOrder.LITTLE_ENDIAN); // WACryptoLtHash.performPointwiseWithOverflow: new DataView(new ArrayBuffer(e.byteLength))
        for (var i = 0; i < hashBuf.capacity(); i += 2) { // WACryptoLtHash.performPointwiseWithOverflow: l += s (s=2)
            var a = Short.toUnsignedInt(hashBuf.getShort()); // WACryptoLtHash.performPointwiseWithOverflow: e.getUint16(l, true)
            var b = Short.toUnsignedInt(expandedBuf.getShort()); // WACryptoLtHash.performPointwiseWithOverflow: o.getUint16(l, true)
            result.putShort((short) ((addition ? a + b : a - b) & 0xFFFF)); // WACryptoLtHash.performPointwiseWithOverflow: i.setUint16(l, r(e, t), true)
        }
        return result.array();
    }

    /**
     * Adds a single element to the current hash state.
     *
     * <p>The element (value MAC) is first expanded via HKDF-SHA256 to 128 bytes,
     * then both the current hash and expanded value are treated as 64
     * little-endian Uint16 values and pointwise added with unsigned 16-bit
     * wrapping overflow.
     *
     * @implNote WACryptoLtHash.LtHash16.$1 — single-element add via
     *           {@code performPointwiseWithOverflow(hash, expanded, (a, b) => a + b)}
     * @param currentHash the current hash state (must be {@link #HASH_LENGTH} bytes)
     * @param valueMac the value MAC to add (will be HKDF-expanded)
     * @return new hash state after addition
     */
    private static byte[] addSingle(byte[] currentHash, byte[] valueMac) {
        var expanded = expand(valueMac); // WACryptoLtHash.LtHash16.$1: extractAndExpand(valueMac, this.salt, 128)
        return performPointwiseWithOverflow(currentHash, expanded, true); // WACryptoLtHash.LtHash16.$1: performPointwiseWithOverflow(e, n, (a, b) => a + b)
    }

    /**
     * Removes a single element from the current hash state.
     *
     * <p>This is the inverse operation of {@link #addSingle(byte[], byte[])}.
     * The element (value MAC) is first expanded via HKDF-SHA256 to 128 bytes,
     * then both the current hash and expanded value are treated as 64
     * little-endian Uint16 values and pointwise subtracted with unsigned
     * 16-bit wrapping underflow.
     *
     * @implNote WACryptoLtHash.LtHash16.$2 — single-element subtract via
     *           {@code performPointwiseWithOverflow(hash, expanded, (a, b) => a - b)}
     * @param currentHash the current hash state (must be {@link #HASH_LENGTH} bytes)
     * @param valueMac the value MAC to remove (will be HKDF-expanded)
     * @return new hash state after removal
     */
    private static byte[] subtractSingle(byte[] currentHash, byte[] valueMac) {
        var expanded = expand(valueMac); // WACryptoLtHash.LtHash16.$2: extractAndExpand(valueMac, this.salt, 128)
        return performPointwiseWithOverflow(currentHash, expanded, false); // WACryptoLtHash.LtHash16.$2: performPointwiseWithOverflow(e, n, (a, b) => a - b)
    }

    /**
     * Adds multiple elements to the current hash state by reducing over the list.
     *
     * <p>Each value MAC is individually expanded and pointwise-added to the hash,
     * sequentially reducing from the initial {@code currentHash}.
     *
     * @implNote WACryptoLtHash.LtHash16.add — reduces over valueMacs calling {@code $1} for each
     * @param currentHash the current hash state (must be {@link #HASH_LENGTH} bytes)
     * @param valueMacs the list of value MACs to add
     * @return new hash state after all additions
     */
    @WhatsAppWebExport(moduleName = "WACryptoLtHash", exports = "LT_HASH_ANTI_TAMPERING", adaptation = WhatsAppAdaptation.ADAPTED)
    public static byte[] add(byte[] currentHash, List<byte[]> valueMacs) {
        var result = currentHash; // WACryptoLtHash.LtHash16.add: Promise.resolve(r)
        for (var valueMac : valueMacs) { // WACryptoLtHash.LtHash16.add: o.reduce(...)
            result = addSingle(result, valueMac); // WACryptoLtHash.LtHash16.add: t.$1(yield e, n)
        }
        return result;
    }

    /**
     * Removes multiple elements from the current hash state by reducing over the list.
     *
     * <p>Each value MAC is individually expanded and pointwise-subtracted from the hash,
     * sequentially reducing from the initial {@code currentHash}.
     *
     * @implNote WACryptoLtHash.LtHash16.subtract — reduces over valueMacs calling {@code $2} for each
     * @param currentHash the current hash state (must be {@link #HASH_LENGTH} bytes)
     * @param valueMacs the list of value MACs to subtract
     * @return new hash state after all removals
     */
    @WhatsAppWebExport(moduleName = "WACryptoLtHash", exports = "LT_HASH_ANTI_TAMPERING", adaptation = WhatsAppAdaptation.ADAPTED)
    public static byte[] subtract(byte[] currentHash, List<byte[]> valueMacs) {
        var result = currentHash; // WACryptoLtHash.LtHash16.subtract: Promise.resolve(r)
        for (var valueMac : valueMacs) { // WACryptoLtHash.LtHash16.subtract: o.reduce(...)
            result = subtractSingle(result, valueMac); // WACryptoLtHash.LtHash16.subtract: t.$2(yield e, n)
        }
        return result;
    }

    /**
     * Result of a {@link #subtractThenAdd} operation, containing both the final hash
     * and the intermediate subtract result.
     *
     * @implNote WACryptoLtHash.LtHash16.subtractThenAdd — returns {@code {ltHash: o, subtractResult: r}}
     * @param ltHash the final hash state after subtract and add operations
     * @param subtractResult the intermediate hash state after subtract but before add operations
     */
    public record SubtractThenAddResult(
            byte[] ltHash,
            byte[] subtractResult
    ) {

    }

    /**
     * Batch operation: removes multiple elements then adds multiple elements.
     *
     * <p>First subtracts all {@code toRemove} elements from the hash, producing an
     * intermediate {@code subtractResult}. Then adds all {@code toAdd} elements to
     * produce the final {@code ltHash}. Both results are returned.
     *
     * @implNote WACryptoLtHash.LtHash16.subtractThenAdd — calls {@code subtract(hash, toRemove)}
     *           then {@code add(subtractResult, toAdd)}, returning {@code {ltHash, subtractResult}}
     * @param currentHash the current hash state (must be {@link #HASH_LENGTH} bytes)
     * @param toAdd list of value MACs to add (may be empty)
     * @param toRemove list of value MACs to remove (may be empty)
     * @return a {@link SubtractThenAddResult} containing both the final hash and intermediate subtract result
     */
    @WhatsAppWebExport(moduleName = "WACryptoLtHash", exports = "LT_HASH_ANTI_TAMPERING", adaptation = WhatsAppAdaptation.ADAPTED)
    public static SubtractThenAddResult subtractThenAdd(
        byte[] currentHash,
        List<byte[]> toAdd,
        List<byte[]> toRemove
    ) {
        var subtractResult = subtract(currentHash, toRemove); // WACryptoLtHash.LtHash16.subtractThenAdd: var r = yield this.subtract(e, n)
        var ltHash = add(subtractResult, toAdd); // WACryptoLtHash.LtHash16.subtractThenAdd: var o = yield this.add(r, t)
        return new SubtractThenAddResult(ltHash, subtractResult); // WACryptoLtHash.LtHash16.subtractThenAdd: {ltHash: o, subtractResult: r}
    }

    /**
     * Creates a copy of the given hash state.
     *
     * @implNote ADAPTED: Java defensive copy — no WA Web equivalent needed because
     *           JS ArrayBuffer has different ownership semantics
     * @param hash the hash state to copy
     * @return a new array with the same contents, or {@code null} if input is {@code null}
     */
    public static byte[] copy(byte[] hash) {
        return hash == null // ADAPTED: Java defensive copy
                ? null
                : hash.clone();
    }

    /**
     * Result of an LT-Hash consistency check, containing the consistency status
     * and the computed and cached LT-Hash values.
     *
     * <p>When the total mutation count across all checked collections exceeds the
     * maximum threshold, all fields are set to their unknown/null values indicating
     * that the check was skipped.
     *
     * @implNote WAWebSyncdAntiTamperingLtHash.checkLtHash — returns
     *           {@code {isLtHashConsistent, scratchLtHash, cachedLtHash}}
     * @param isLtHashConsistent {@code true} if all checked collections are consistent,
     *                           {@code false} if any mismatch was found, or {@code null}
     *                           if the check was skipped due to exceeding the mutation threshold
     * @param scratchLtHash the first recomputed LT-Hash from mutations, or {@code null} if
     *                      no collections were checked
     * @param cachedLtHash the first stored LT-Hash from the collection version store,
     *                     or {@code null} if no collections were checked
     */
    public record LtHashCheckResult(
            Boolean isLtHashConsistent,
            byte[] scratchLtHash,
            byte[] cachedLtHash
    ) {
    }

    /**
     * Checks the LT-Hash consistency of one or all collections by recomputing
     * the hash from the store's sync action entries and comparing against the
     * stored hash.
     *
     * <p>For each collection, the LT-Hash is recomputed by deduplicating entries
     * by index MAC (last-write-wins) and adding all remaining value MACs to
     * {@link #EMPTY_HASH} using {@link #add(byte[], List)}. The result is compared
     * against the stored LT-Hash from the collection metadata.
     *
     * <p>When the total mutation count across all collections exceeds the
     * {@code maxMutations} threshold, the check is skipped and an unknown result
     * is returned. For employee accounts in WA Web, this threshold is overridden
     * to 900; Cobalt uses the caller-provided value directly.
     *
     * @implNote WAWebSyncdAntiTamperingLtHash.checkLtHash (function m/p) — reads
     *           CollectionVersionStore and SyncActionStore via runInTransaction,
     *           computes scratch LT-Hash via helper function d, compares against
     *           stored ltHash per collection. ADAPTED: the WA Web employee
     *           {@code isEmployee() ? 900 : a} threshold override is not applied —
     *           Cobalt uses the caller-provided value directly since it has no
     *           employee concept. The {@code runInTransaction} wrapper is replaced
     *           by direct calls into {@link WhatsAppStore} which is the
     *           single flattened store. {@code WALogger.ERROR(...).sendLogs(..)}
     *           and the {@code CriticalBlock}-versus-employee sampling branch are
     *           replaced by a plain JUL warning since Cobalt does not re-implement
     *           the WA Web log-sampling infrastructure.
     * @param store the WhatsApp store providing sync action entries and collection metadata
     * @param collection the specific collection to check, or {@code null} to check all collections
     * @param maxMutations the maximum total mutation count threshold; if the total exceeds
     *                     this value, the check is skipped and an unknown result is returned.
     *                     Pass {@code null} to disable the threshold.
     * @param context a context string for diagnostic logging
     * @return the {@link LtHashCheckResult} with consistency status and hash values
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdAntiTamperingLtHash", exports = "checkLtHash", adaptation = WhatsAppAdaptation.ADAPTED)
    public static LtHashCheckResult checkLtHash(WhatsAppStore store, SyncPatchType collection, Integer maxMutations, String context) {
        // WAWebSyncdAntiTamperingLtHash.checkLtHash: runInTransaction({SyncActionStore, CollectionVersionStore})
        // Collect collection metadata and their sync action entries
        var collectionsData = new ArrayList<CollectionCheckData>(); // WAWebSyncdAntiTamperingLtHash.checkLtHash: a.filter(Boolean).map(...)
        if (collection == null) { // WAWebSyncdAntiTamperingLtHash.checkLtHash: r == null ? yield t.getAll() : [yield t.get(r)]
            for (var patchType : SyncPatchType.values()) {
                var metadata = store.findWebAppState(patchType);
                var entries = store.getSyncActionEntries(patchType);
                collectionsData.add(new CollectionCheckData(patchType, metadata.ltHash(), entries));
            }
        } else {
            var metadata = store.findWebAppState(collection);
            var entries = store.getSyncActionEntries(collection);
            collectionsData.add(new CollectionCheckData(collection, metadata.ltHash(), entries));
        }

        // WAWebSyncdAntiTamperingLtHash.checkLtHash: count total mutations
        var totalMutations = 0; // WAWebSyncdAntiTamperingLtHash.checkLtHash: s = 0
        for (var data : collectionsData) {
            totalMutations += data.entries().size(); // WAWebSyncdAntiTamperingLtHash.checkLtHash: s += r.length
        }

        // WAWebSyncdAntiTamperingLtHash.checkLtHash: if (i !== void 0 && s > i) return unknown
        if (maxMutations != null && totalMutations > maxMutations) {
            return new LtHashCheckResult(null, null, null);
        }

        byte[] scratchLtHash = null; // WAWebSyncdAntiTamperingLtHash.checkLtHash: u = null
        byte[] cachedLtHash = null; // WAWebSyncdAntiTamperingLtHash.checkLtHash: m = null
        var consistent = true; // WAWebSyncdAntiTamperingLtHash.checkLtHash: p = true
        var inconsistentCollections = new ArrayList<SyncPatchType>(); // WAWebSyncdAntiTamperingLtHash.checkLtHash: _ = []

        for (var data : collectionsData) { // WAWebSyncdAntiTamperingLtHash.checkLtHash: l.map(...)
            var computed = computeFromEntries(data.entries()); // WAWebSyncdAntiTamperingLtHash.checkLtHash: yield d(r)
            if (scratchLtHash == null) { // WAWebSyncdAntiTamperingLtHash.checkLtHash: u == null && (u = a)
                scratchLtHash = computed;
            }
            if (cachedLtHash == null) { // WAWebSyncdAntiTamperingLtHash.checkLtHash: m == null && (m = n)
                cachedLtHash = data.storedLtHash();
            }
            // WAWebSyncdAntiTamperingLtHash.checkLtHash: arrayBuffersEqual(n, a) || s > 0 && (p = false, _.push(t))
            if (!Arrays.equals(data.storedLtHash(), computed) && totalMutations > 0) {
                consistent = false;
                inconsistentCollections.add(data.collection());
            }
        }

        // WAWebSyncdAntiTamperingLtHash.checkLtHash: _.length > 0 && WALogger.ERROR(...)
        if (!inconsistentCollections.isEmpty()) {
            LOGGER.warning("[" + context + "] syncd: failed LtHash check for "
                    + inconsistentCollections.size() + " collections => "
                    + inconsistentCollections.subList(0, Math.min(3, inconsistentCollections.size())));
        }

        return new LtHashCheckResult(consistent, scratchLtHash, cachedLtHash);
    }

    /**
     * Reports a collection hash inconsistency by running a LT-Hash consistency
     * check and logging the result.
     *
     * <p>Calls {@link #checkLtHash(WhatsAppStore, SyncPatchType, Integer, String)}
     * with the specified collection and a default threshold of 400 mutations.
     * Logs detailed diagnostic information about the consistency check result
     * including truncated hex representations of the scratch and cached hashes.
     *
     * @implNote WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency (function _/f) —
     *           calls checkLtHash(n, e, r) with default r=400, then logs
     *           inconsistency/consistency/unknown status. ADAPTED: WA Web persists
     *           the diagnostic text to the sync database via
     *           {@code WAWebSyncdDbCallbacksApi.writeSyncdLog(e, "...")} and
     *           {@code printSyncdLog(e)}, and escalates to
     *           {@code WALogger.ERROR(...).sendLogs(...)} with employee/critical-block
     *           sampling. Cobalt mirrors the three branches (inconsistent/consistent/unknown)
     *           and the hex-suffix formatting, but writes via {@link Logger} only; the
     *           database persistence and remote log upload paths are intentionally
     *           not reproduced.
     * @param store the WhatsApp store providing sync action entries and collection metadata
     * @param collection the collection to check
     * @param diagnosticContext a human-readable context string for log messages
     * @param checkContext the context string passed to {@link #checkLtHash}
     * @param maxMutations the maximum total mutation count threshold, defaults to 400
     *                     if {@code null}
     * @return {@code true} if the LT-Hash is inconsistent, {@code false} if consistent,
     *         or {@code null} if the check was skipped (unknown)
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdAntiTamperingLtHash", exports = "reportCollectionInconsistency", adaptation = WhatsAppAdaptation.ADAPTED)
    public static Boolean reportCollectionInconsistency(
            WhatsAppStore store,
            SyncPatchType collection,
            String diagnosticContext,
            String checkContext,
            Integer maxMutations
    ) {
        var threshold = maxMutations != null ? maxMutations : 400; // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: r === void 0 && (r = 400)
        var result = checkLtHash(store, collection, threshold, checkContext); // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: yield m(n, e, r)

        // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: format hash suffixes for logging
        var scratchSuffix = result.scratchLtHash() == null
                ? "" // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: c == null ? ""
                : hexPaddedSuffix(result.scratchLtHash(), 16); // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: arrayBufferToHexPadded(c).slice(-16)
        var cachedSuffix = result.cachedLtHash() == null
                ? "" // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: i == null ? ""
                : hexPaddedSuffix(result.cachedLtHash(), 16); // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: arrayBufferToHexPadded(i).slice(-16)

        if (result.isLtHashConsistent() != null && !result.isLtHashConsistent()) {
            // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: l === false
            LOGGER.warning("[" + checkContext + "] lthash first time inconsistent."
                    + " scratchLtHash: " + scratchSuffix
                    + ", cachedLtHash: " + cachedSuffix
                    + ", context: " + diagnosticContext);
            return true; // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: return true
        } else if (result.isLtHashConsistent() != null && result.isLtHashConsistent()) {
            // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: l === true
            LOGGER.fine("lthash consistent."
                    + " scratchLtHash: " + scratchSuffix
                    + ", cachedLtHash: " + cachedSuffix
                    + ", context: " + diagnosticContext);
            return false; // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: return false
        }

        // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: l === undefined (skipped)
        LOGGER.fine("lthash unknown if consistent."
                + " scratchLtHash: " + scratchSuffix
                + ", cachedLtHash: " + cachedSuffix
                + ", context: " + diagnosticContext);
        return null; // WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency: implicit undefined return
    }

    /**
     * Computes the LT-Hash from a collection of sync action entries by deduplicating
     * entries by index MAC (last-write-wins) and adding all remaining value MACs
     * to {@link #EMPTY_HASH}.
     *
     * <p>This matches the WA Web helper function {@code d} in {@code WAWebSyncdAntiTamperingLtHash}
     * which creates a Map keyed by hex(indexMac) with value=valueMac, then calls
     * {@code LT_HASH_ANTI_TAMPERING.add(EMPTY_LT_HASH, Array.from(map.values()))}.
     *
     * @implNote WAWebSyncdAntiTamperingLtHash (function d) — deduplicates by indexMac
     *           via Map, then calls WACryptoLtHash.LT_HASH_ANTI_TAMPERING.add(EMPTY_LT_HASH, valueMacs)
     * @param entries the sync action entries for the collection
     * @return the computed LT-Hash
     */
    private static byte[] computeFromEntries(Collection<SyncActionEntry> entries) {
        // WAWebSyncdAntiTamperingLtHash.d: new Map(e.map(e => [arrayBufferToHexPadded(e.indexMac), e.valueMac]))
        // Deduplication by indexMac (using hex string as key, last-write-wins)
        var deduplicated = new LinkedHashMap<String, byte[]>(); // WAWebSyncdAntiTamperingLtHash.d: new Map(...)
        for (var entry : entries) {
            if (entry.indexMac() != null && entry.valueMac() != null) {
                deduplicated.put(HexFormat.of().formatHex(entry.indexMac()), entry.valueMac()); // WAWebSyncdAntiTamperingLtHash.d: arrayBufferToHexPadded(e.indexMac), e.valueMac
            }
        }
        // WAWebSyncdAntiTamperingLtHash.d: LT_HASH_ANTI_TAMPERING.add(EMPTY_LT_HASH, Array.from(t.values()))
        return add(EMPTY_HASH, List.copyOf(deduplicated.values()));
    }

    /**
     * Returns the last {@code suffixLength} characters of a hex-encoded byte array,
     * matching WA Web's {@code arrayBufferToHexPadded(buf).slice(-N)} pattern.
     *
     * @implNote WAWebSyncdAntiTamperingLtHash.reportCollectionInconsistency —
     *           {@code arrayBufferToHexPadded(hash).slice(-16)}
     * @param data the byte array to encode
     * @param suffixLength the number of trailing hex characters to return
     * @return the hex suffix string
     */
    private static String hexPaddedSuffix(byte[] data, int suffixLength) {
        var hex = HexFormat.of().formatHex(data); // WAWebSyncdCryptoUtils.arrayBufferToHexPadded
        return hex.length() <= suffixLength
                ? hex
                : hex.substring(hex.length() - suffixLength); // .slice(-16)
    }

    /**
     * Internal data holder for a single collection's check data, bundling the
     * collection type, its stored LT-Hash, and its sync action entries.
     *
     * @implNote WAWebSyncdAntiTamperingLtHash.checkLtHash — intermediate
     *           {@code {collection: t, ltHash: n, mutations: r}} objects
     * @param collection the collection type
     * @param storedLtHash the stored LT-Hash from collection metadata
     * @param entries the sync action entries for the collection
     */
    private record CollectionCheckData(
            SyncPatchType collection,
            byte[] storedLtHash,
            Collection<SyncActionEntry> entries
    ) {
    }
}
