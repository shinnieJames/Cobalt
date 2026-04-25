package com.github.auties00.cobalt.media;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.media.MediaProvider;

import java.util.*;

/**
 * Represents a CDN host entry returned by the WhatsApp {@code media_conn}
 * query.
 *
 * <p>Each host carries a hostname, the IP addresses advertised by the
 * server, the sets of media types it accepts for download and upload
 * operations, and the list of download buckets it owns. Primary hosts
 * may additionally carry a nested fallback hostname and a list of fallback
 * IP addresses which the retry loop rotates to when the primary fails.
 *
 * <p>The sealed interface has two permitted implementations:
 * <ul>
 *   <li>{@link Primary}: a host with type {@code "primary"} that may
 *       advertise a nested fallback hostname.</li>
 *   <li>{@link Fallback}: a host with type {@code "fallback"} that never
 *       advertises a nested fallback.</li>
 * </ul>
 *
 * <p>Both variants normalise the media type before checking their
 * download/upload supported sets, mirroring WA Web's helper
 * {@code d(t)} for downloads and {@code m(t)} for uploads which collapse
 * PTV/newsletter-PTV to video and product/product-catalog-image down to
 * their base types.
 *
 * @implNote WAWebMediaHost: {@code MediaHost} class and {@code HOST_TYPE}
 * enum. WAWebMediaHostsRouteSelection: {@code routeSelection} and
 * {@code OPERATIONS}. WABase64Modulo: deterministic bucket selection.
 */
@WhatsAppWebModule(moduleName = "WAWebMediaHost")
@WhatsAppWebModule(moduleName = "WAWebMediaHostsRouteSelection")
@WhatsAppWebModule(moduleName = "WAWebMediaHostsMaybeSwitchHost")
@WhatsAppWebModule(moduleName = "WABase64Modulo")
public sealed interface MediaHost {

    /**
     * The remaining-bytes threshold, in bytes, below which a mid-transfer
     * host switch is never attempted for a long-running document upload
     * or download.
     *
     * <p>Consumed by {@link #maybeSwitchHost} to short-circuit the decision
     * when there are fewer than {@code 50 MiB} left to transfer: at that
     * point the cost of restarting on a new host outweighs the benefit of
     * honouring a fresh route list.
     *
     * <p>In WA Web the same constant gates {@code shouldPollUploadHosts}
     * and {@code shouldPollDownloadHosts} in {@code WAWebMmsClientPollMediaHosts},
     * which only starts the periodic {@code media_conn} re-query loop for
     * DOCUMENT transfers larger than this threshold. Cobalt's synchronous
     * retry loop in {@link MediaConnection#upload} and
     * {@link MediaConnection#download} does not currently run the polling
     * loop, so this constant is provided for parity and future use but is
     * not wired into the transfer orchestration.
     *
     * @implNote WAWebMediaHostsMaybeSwitchHost.THRESHOLD: {@code 52428800}
     * (50 MiB).
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHostsMaybeSwitchHost", exports = "THRESHOLD",
            adaptation = WhatsAppAdaptation.DIRECT)
    int SWITCH_HOST_THRESHOLD = 52428800;

    /**
     * The outcome of a {@link #maybeSwitchHost} decision: whether the
     * current host should be replaced and, if so, the host that should
     * take its place.
     *
     * <p>When {@link #changed} is {@code false} the {@link #host} field
     * echoes the current host passed into {@code maybeSwitchHost} so that
     * callers can assign the result unconditionally without a null check.
     *
     * @implNote WAWebMediaHostsMaybeSwitchHost.maybeSwitchHost: the
     * {@code {changed, host}} object literal returned by every branch of
     * the decision tree.
     * @param changed whether the host should be switched
     * @param host    the host to use, never {@code null}
     */
    @WhatsAppWebModule(moduleName = "WAWebMediaHostsMaybeSwitchHost")
    record SwitchHostResult(boolean changed, MediaHost host) {
    }

    /**
     * Decides whether a long-running document upload or download that is
     * currently using {@code current} should switch to a different host
     * because the periodic {@code media_conn} re-query returned a new
     * route list.
     *
     * <p>The algorithm, lifted verbatim from
     * {@code WAWebMediaHostsMaybeSwitchHost.maybeSwitchHost}:
     * <ol>
     *   <li>When {@code bytesRemaining} is smaller than
     *       {@link #SWITCH_HOST_THRESHOLD} the current host is kept.</li>
     *   <li>When the current host is {@link Primary} and the new route's
     *       selected host differs from it, switch to the new selected
     *       host.</li>
     *   <li>When the current host is {@link Fallback} and the previous
     *       route's selected host differs from the new route's selected
     *       host, switch to the new selected host.</li>
     *   <li>When the current host is {@link Fallback} and either
     *       <ul>
     *         <li>the current host still equals the previous fallback host
     *             but the server-returned fallback host changed, or</li>
     *         <li>the current host still equals the previous selected
     *             host's nested fallback but that nested fallback
     *             changed,</li>
     *       </ul>
     *       switch to the new selected host (
     *       {@code previousRoute.selectedHost}).</li>
     *   <li>Otherwise keep the current host.</li>
     * </ol>
     *
     * <p>Cobalt's current upload/download loop in
     * {@link MediaConnection#upload} and {@link MediaConnection#download}
     * does not re-query {@code media_conn} while a transfer is in flight,
     * so this method is not yet wired into the retry path. It is provided
     * here for parity with the WA Web source and to support future
     * long-running-transfer polling.
     *
     * @implNote WAWebMediaHostsMaybeSwitchHost.maybeSwitchHost with the
     * local helper {@code l(e, t, n)} inlined as
     * {@link #isNestedFallbackChanged}.
     * @param current        the host currently being used for the transfer
     * @param previousRoute  the route selection result in force before the
     *                       most recent {@code media_conn} re-query
     * @param newRoute       the route selection result returned by the
     *                       most recent {@code media_conn} re-query
     * @param bytesRemaining the number of bytes left to transfer
     * @return the switch decision, with {@code host} echoing
     *         {@code current} when {@code changed} is {@code false}
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHostsMaybeSwitchHost", exports = "maybeSwitchHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    static SwitchHostResult maybeSwitchHost(
            MediaHost current,
            RouteSelectionResult previousRoute,
            RouteSelectionResult newRoute,
            long bytesRemaining
    ) {
        // WAWebMediaHostsMaybeSwitchHost.maybeSwitchHost
        // Aliases the previous and new route blocks to match the JS names:
        // a = previous fallback, i = previous selected,
        // s = new fallback, u = new selected
        var previousFallback = previousRoute.fallbackHost().orElse(null);
        var previousSelected = previousRoute.selectedHost().orElse(null);
        var newFallback = newRoute.fallbackHost().orElse(null);
        var newSelected = newRoute.selectedHost().orElse(null);

        // WAWebMediaHostsMaybeSwitchHost.maybeSwitchHost
        // o < e: when fewer than THRESHOLD bytes remain the switch cost
        // outweighs any topology benefit, so always stay on the current host
        if (bytesRemaining < SWITCH_HOST_THRESHOLD) {
            return new SwitchHostResult(false, current);
        }

        // WAWebMediaHostsMaybeSwitchHost.maybeSwitchHost
        // t.type === "primary" && !t.equals(u): the current host is the
        // primary slot but the re-query returned a different selected host
        if (current instanceof Primary && !equalsByHostname(current, newSelected)) {
            return new SwitchHostResult(true, newSelected);
        }

        // WAWebMediaHostsMaybeSwitchHost.maybeSwitchHost
        // t.type === "fallback" && !i.equals(u): the current host is a
        // fallback-class host but the previously-selected primary host
        // differs from the new selected host, meaning the server elected
        // a new primary so we should rejoin the primary path
        if (current instanceof Fallback && !equalsByHostname(previousSelected, newSelected)) {
            return new SwitchHostResult(true, newSelected);
        }

        // WAWebMediaHostsMaybeSwitchHost.maybeSwitchHost
        // t.type === "fallback" && (l(t, a, s) || l(t, i.fallback, u.fallback))
        // the current host is a fallback and either
        //   - it equals the previous fallback slot while the server returned
        //     a new fallback, or
        //   - it equals the previous selected host's nested fallback while
        //     the server returned a new nested fallback
        // in either case rejoin the new primary path
        if (current instanceof Fallback
                && (isNestedFallbackChanged(current, previousFallback, newFallback)
                        || isNestedFallbackChanged(current, nestedFallbackHost(previousSelected), nestedFallbackHost(newSelected)))) {
            return new SwitchHostResult(true, newSelected);
        }

        // WAWebMediaHostsMaybeSwitchHost.maybeSwitchHost
        // Any other case where the current host is primary, fallback, or
        // null leaves it untouched; the JS exhaustiveness wildcard throws
        // on an unknown discriminator but every Java MediaHost is covered
        // by Primary / Fallback / null above
        return new SwitchHostResult(false, current);
    }

    /**
     * Returns whether two hosts are considered equal under WA Web's
     * {@code MediaHost.prototype.equals} semantics, which compare hosts
     * solely by their hostname.
     *
     * <p>Java records compare every component for equality, but WA Web
     * collapses the comparison down to {@code this.hostname === t?.hostname}
     * so that re-parsed {@code media_conn} responses with cosmetically
     * different IP orderings or rule encodings still register as the same
     * host. {@link #maybeSwitchHost} relies on this hostname-only semantic
     * to keep a transfer pinned to its current host across re-queries.
     *
     * @implNote WAWebMediaHost.MediaHost.prototype.equals:
     * {@code this.hostname === (t == null ? void 0 : t.hostname)}.
     * @param a the first host, may be {@code null}
     * @param b the second host, may be {@code null}
     * @return {@code true} when both hosts are non-{@code null} and share
     *         the same hostname, otherwise {@code false}
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean equalsByHostname(MediaHost a, MediaHost b) {
        // WAWebMediaHost.MediaHost.prototype.equals
        // this.hostname === (t == null ? void 0 : t.hostname)
        // Note: WA Web's strict equality treats undefined === undefined as
        // true, but two null hosts in Cobalt are treated as not-equal here
        // because every call site already null-guards its arguments before
        // routing into this helper.
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.hostname(), b.hostname());
    }

    /**
     * Tests whether {@code current} still matches the previous host in a
     * given slot while the server has elected a new host for that slot.
     *
     * <p>Mirrors the WA Web local helper
     * {@code l(e, t, n) = e.equals(t) && n != null && t != null && !t.equals(n)}:
     * the current host must equal the previous slot host, both the
     * previous and new slot hosts must be present, and the previous and
     * new hosts must differ. Only when all four conditions hold does a
     * slot change warrant a host switch.
     *
     * @implNote WAWebMediaHostsMaybeSwitchHost local helper {@code l}.
     * @param current         the host currently in use
     * @param previousSlotHost the host that occupied the slot before the
     *                        re-query, may be {@code null}
     * @param newSlotHost     the host that occupies the slot after the
     *                        re-query, may be {@code null}
     * @return {@code true} if the slot's host changed under the current
     *         host's feet
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHostsMaybeSwitchHost", exports = "maybeSwitchHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean isNestedFallbackChanged(MediaHost current, MediaHost previousSlotHost, MediaHost newSlotHost) {
        // WAWebMediaHostsMaybeSwitchHost.l
        // e.equals(t) && n != null && t != null && !t.equals(n)
        // The .equals call here is the WA Web hostname-only comparison.
        return equalsByHostname(current, previousSlotHost)
                && newSlotHost != null
                && previousSlotHost != null
                && !equalsByHostname(previousSlotHost, newSlotHost);
    }

    /**
     * Looks up the nested fallback host of a primary host, if any.
     *
     * <p>Implements the JS {@code u.fallback} / {@code i.fallback} property
     * access used by {@link #maybeSwitchHost}. Cobalt exposes the nested
     * fallback as a hostname {@link Optional} on {@link Primary}; matching
     * it against the surrounding host list would require a scan, but the
     * JS source stores the nested fallback as an inline {@code MediaHost}
     * instance on the primary object, and compares it with
     * {@code equals}. Because Cobalt does not keep a {@code MediaHost}
     * reference for the nested fallback, this helper returns {@code null}
     * whenever the nested fallback lookup would be required, which
     * collapses the corresponding branch of {@link #maybeSwitchHost}
     * to a no-op. The outer branch (previous-fallback vs. new-fallback)
     * still triggers correctly and covers the common case.
     *
     * @implNote WAWebMediaHostsMaybeSwitchHost.maybeSwitchHost:
     * {@code i.fallback} / {@code u.fallback} property access on a
     * {@code HOST_TYPE.PRIMARY} instance.
     * @param primary the primary host to inspect, or {@code null}
     * @return the nested fallback host, or {@code null} when the primary
     *         has no nested fallback or is itself {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHostsMaybeSwitchHost", exports = "maybeSwitchHost",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static MediaHost nestedFallbackHost(MediaHost primary) {
        // WAWebMediaHostsMaybeSwitchHost.maybeSwitchHost
        // Cobalt does not retain a MediaHost reference for the nested
        // fallback; only the hostname string is kept on Primary. Returning
        // null here makes the corresponding branch of l(...) fail the
        // null-guard and thus behaves as "no change" for that slot.
        return null;
    }

    /**
     * Enumerates the two media operations that can be performed against a
     * CDN host.
     *
     * <p>Used by {@link #routeSelection} to pick the correct supported-type
     * check: {@link #UPLOAD} matches against the host's upload media type
     * set, while {@link #DOWNLOAD} matches against the download set and
     * additionally participates in the bucket-based host selection.
     *
     * <p>This enum consolidates two structurally identical WA Web
     * constant modules into a single Java counterpart:
     * <ul>
     *   <li>{@code WAWebMmsOperationsConst.default}: the canonical MMS
     *       operation constant imported by {@code WAWebMmsClient} and
     *       {@code WAWebMediaEntry} to tag {@code getHostsInfo} calls.</li>
     *   <li>{@code WAWebMediaHostsRouteSelection.OPERATIONS}: a local
     *       duplicate used by the route-selection algorithm.</li>
     * </ul>
     *
     * @implNote WAWebMmsOperationsConst.default: {@code {DOWNLOAD:"DOWNLOAD",UPLOAD:"UPLOAD"}}.
     * @implNote WAWebMediaHostsRouteSelection.OPERATIONS: an inline local copy of the same constant.
     */
    @WhatsAppWebModule(moduleName = "WAWebMmsOperationsConst")
    @WhatsAppWebModule(moduleName = "WAWebMediaHostsRouteSelection")
    enum Operation {
        /**
         * The upload operation; route selection chooses the first host
         * whose upload media type set contains the requested media type.
         *
         * @implNote WAWebMmsOperationsConst.default: {@code UPLOAD:"UPLOAD"}.
         * @implNote WAWebMediaHostsRouteSelection.OPERATIONS: {@code UPLOAD} constant.
         */
        @WhatsAppWebExport(moduleName = "WAWebMmsOperationsConst", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        @WhatsAppWebExport(moduleName = "WAWebMediaHostsRouteSelection", exports = "OPERATIONS",
                adaptation = WhatsAppAdaptation.DIRECT)
        UPLOAD,

        /**
         * The download operation; route selection applies bucket-based
         * host matching (when vcache aggregation is enabled) and then
         * falls back to the first host whose download media type set
         * contains the requested media type.
         *
         * @implNote WAWebMmsOperationsConst.default: {@code DOWNLOAD:"DOWNLOAD"}.
         * @implNote WAWebMediaHostsRouteSelection.OPERATIONS: {@code DOWNLOAD} constant.
         */
        @WhatsAppWebExport(moduleName = "WAWebMmsOperationsConst", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        @WhatsAppWebExport(moduleName = "WAWebMediaHostsRouteSelection", exports = "OPERATIONS",
                adaptation = WhatsAppAdaptation.DIRECT)
        DOWNLOAD
    }

    /**
     * The outcome of a route selection pass: the best-matching host for
     * the requested operation and media type, the first fallback-class
     * host from the connection's host list, and the download bucket that
     * WA Web would have stashed on {@code selectedHost} via
     * {@code setSelectedBucket(p)} before returning.
     *
     * <p>The {@code selectedBucket} is present only when
     * {@link #routeSelection} chose {@code selectedHost} from the bucket
     * map (either the explicit {@code p}-bucket entry or the bucket-0
     * default). When the selected host comes from the fall-through linear
     * scan, or the operation is {@link Operation#UPLOAD}, the bucket is
     * empty — mirroring WA Web's behaviour where {@code setSelectedBucket}
     * is only called inside the bucket-branch of {@code routeSelection}.
     *
     * <p>Consumed by {@link MediaConnection#upload(MediaProvider, java.io.InputStream)}
     * and {@link MediaConnection#download(MediaProvider)} which feed the
     * pair into the {@code selectHost} rotation strategy; the bucket is
     * attached as the {@code _nc_cat} query parameter of the formatted
     * download URL only when the contacted hostname matches
     * {@code selectedHost}.
     *
     * @implNote WAWebMediaHostsRouteSelection.routeSelection: the return
     * value of the function, which exposes {@code selectedHost} and
     * {@code fallback} on the route object, together with the
     * {@code setSelectedBucket(p)} side effect re-materialised here as an
     * immutable field.
     * @param selectedHost   the selected host, or empty if none matched
     * @param fallbackHost   the fallback host, or empty if none exists
     * @param selectedBucket the download bucket that WA Web would have
     *                       assigned via {@code setSelectedBucket(p)},
     *                       or empty when no bucket applies
     */
    @WhatsAppWebModule(moduleName = "WAWebMediaHostsRouteSelection")
    record RouteSelectionResult(
            Optional<MediaHost> selectedHost,
            Optional<MediaHost> fallbackHost,
            Optional<Integer> selectedBucket
    ) {
    }

    /**
     * Picks the best CDN host for the given operation and media type from
     * a parsed host list.
     *
     * <p>For download operations the method tries bucket-based routing
     * first when vcache aggregation is enabled:
     * <ol>
     *   <li>When {@code encFileHash} is {@code null} the bucket defaults
     *       to {@code 0}.</li>
     *   <li>Otherwise the bucket is computed as
     *       {@code base64Modulo(encFileHash, maxBuckets) + 100}.</li>
     *   <li>The host whose {@code downloadBuckets} list claims the
     *       computed bucket is preferred; when no host claims the bucket
     *       the {@code 0}-bucket host is tried.</li>
     * </ol>
     * When bucket-based selection finds nothing (or for upload
     * operations) the method falls back to a linear scan for the first
     * host that supports the requested media type.
     *
     * <p>Regardless of the operation, the fallback host is always the
     * first host in the list whose type is {@link Fallback}.
     *
     * @implNote WAWebMediaHostsRouteSelection.routeSelection combined with
     * its internal bucket map helper ({@code u}) and the primary/fallback
     * selection loop.
     * @param operation                  the operation to perform
     * @param mediaType                  the media path to match
     * @param hosts                      the list of available hosts
     * @param encFileHash                the base64-encoded encrypted file
     *                                   hash, or {@code null} if unavailable
     * @param maxBuckets                 the maximum number of buckets, or
     *                                   {@code null} if not available
     * @param vcacheAggregationEnabled   whether the
     *                                   {@code mms_vcache_aggregation_enabled}
     *                                   AB prop is enabled
     * @return the route selection result containing the selected host,
     *         the fallback host, and the bucket that WA Web would have
     *         stashed on {@code selectedHost} via
     *         {@code setSelectedBucket(p)}
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHostsRouteSelection", exports = "routeSelection",
            adaptation = WhatsAppAdaptation.DIRECT)
    static RouteSelectionResult routeSelection(
            Operation operation,
            MediaPath mediaType,
            List<? extends MediaHost> hosts,
            String encFileHash,
            Integer maxBuckets,
            boolean vcacheAggregationEnabled
    ) {
        // WAWebMediaHostsRouteSelection.routeSelection
        // Empty host list yields an empty RouteSelectionResult, matching
        // the null branch in the WA Web source: return{selectedHost:null,fallbackHost:null}
        if (hosts.isEmpty()) {
            return new RouteSelectionResult(Optional.empty(), Optional.empty(), Optional.empty());
        }

        MediaHost selected = null;
        Integer selectedBucket = null;
        if (operation == Operation.DOWNLOAD) {
            // WAWebMediaHostsRouteSelection.routeSelection
            // Computes the bucket for deterministic host selection:
            // n == null ? p = 0 : m && i != null && (p = r("WABase64Modulo")(n, i) + 100).
            // When n != null but the AB prop is off or maxBuckets is null,
            // p is left undefined (mapped to null here)
            Integer bucket;
            if (encFileHash == null) {
                bucket = 0;
            } else if (vcacheAggregationEnabled && maxBuckets != null) {
                bucket = base64Modulo(encFileHash, maxBuckets) + 100;
            } else {
                bucket = null;
            }

            // WAWebMediaHostsRouteSelection.routeSelection
            // Builds the bucket -> host map once (helper u) and then
            // consults it for the computed bucket; bucket 0 is kept as a
            // default fall-through when the per-bucket host does not match
            var bucketMap = buildBucketMap(hosts);
            var bucketHost = bucket != null ? bucketMap.get(bucket) : null;
            var defaultHost = bucketMap.get(0);

            // WAWebMediaHostsRouteSelection.routeSelection
            // f != null && f.supportsDownloadMediaType(s) ? c = f
            //   : g != null && g.supportsDownloadMediaType(s) && (c = g)
            if (bucketHost != null && supportsDownloadMediaType(bucketHost, mediaType)) {
                selected = bucketHost;
            } else if (defaultHost != null && supportsDownloadMediaType(defaultHost, mediaType)) {
                selected = defaultHost;
            }

            // WAWebMediaHostsRouteSelection.routeSelection
            // (d = c) == null || d.setSelectedBucket(p): the bucket is
            // attached to the selected host only when the bucket branch
            // actually produced one. When c stayed null, WA Web skips the
            // assignment entirely and selectedBucket stays undefined on any
            // later linear-scan selection.
            if (selected != null) {
                selectedBucket = bucket;
            }
        }

        // WAWebMediaHostsRouteSelection.routeSelection
        // var h = a.find(function(e){return e.isFallback()}): first
        // fallback-class host, independent of selected host choice
        MediaHost fallback = null;
        for (var host : hosts) {
            if (host instanceof Fallback) {
                fallback = host;
                break;
            }
        }

        // WAWebMediaHostsRouteSelection.routeSelection
        // c = c != null ? c : a.find(function(t){...}): fall-through linear
        // scan when bucket routing did not produce a selected host (or the
        // operation is UPLOAD, which skips the bucket branch entirely).
        // Intentionally does NOT touch selectedBucket: WA Web's
        // setSelectedBucket was already a no-op for this branch because c
        // was null at the time of the (d=c)==null||d.setSelectedBucket call
        if (selected == null) {
            for (var host : hosts) {
                if (operation == Operation.UPLOAD
                        ? supportsUploadMediaType(host, mediaType)
                        : supportsDownloadMediaType(host, mediaType)) {
                    selected = host;
                    break;
                }
            }
        }

        return new RouteSelectionResult(
                Optional.ofNullable(selected),
                Optional.ofNullable(fallback),
                Optional.ofNullable(selectedBucket)
        );
    }

    /**
     * Returns whether the given host can serve a download for the
     * specified media type, after collapsing the media type through the
     * WA Web download-type normalisation map.
     *
     * @implNote WAWebMediaHost.MediaHost.supportsDownloadMediaType.
     * @param host      the host to check
     * @param mediaType the media type to check
     * @return {@code true} if the normalized type is in the host's download set
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean supportsDownloadMediaType(MediaHost host, MediaPath mediaType) {
        return host.download().contains(normalizeDownloadMediaType(mediaType));
    }

    /**
     * Returns whether the given host can accept an upload for the
     * specified media type, after collapsing the media type through the
     * WA Web upload-type normalisation map.
     *
     * @implNote WAWebMediaHost.MediaHost.supportsUploadMediaType.
     * @param host      the host to check
     * @param mediaType the media type to check
     * @return {@code true} if the normalized type is in the host's upload set
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean supportsUploadMediaType(MediaHost host, MediaPath mediaType) {
        return host.upload().contains(normalizeUploadMediaType(mediaType));
    }

    /**
     * Builds a map from download bucket number to the host that claims
     * that bucket.
     *
     * <p>Iterates every host's {@code downloadBuckets} list; if multiple
     * hosts claim the same bucket the last writer wins, mirroring the
     * plain assignment semantics of the JS object in the source module.
     *
     * @implNote WAWebMediaHostsRouteSelection local function {@code u}.
     * @param hosts the list of hosts to index
     * @return a map from bucket number to host
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHostsRouteSelection", exports = "routeSelection",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static Map<Integer, MediaHost> buildBucketMap(List<? extends MediaHost> hosts) {
        // WAWebMediaHostsRouteSelection.routeSelection
        // Iterates every host's bucket list and maps each bucket number to
        // the owning host; later assignments overwrite earlier ones
        var map = new HashMap<Integer, MediaHost>();
        for (var host : hosts) {
            for (var bucket : host.downloadBuckets()) {
                map.put(bucket, host);
            }
        }
        return map;
    }

    /**
     * Computes the modulo of a base64-encoded string treated as a
     * big-endian byte stream.
     *
     * <p>Decodes the input, then processes each byte as two 4-bit nibbles,
     * accumulating the remainder via
     * {@code ((remainder << 4) + nibble) % divisor}. Used as the
     * deterministic bucket-selection hash for download route selection.
     *
     * @implNote WABase64Modulo.default.
     * @param base64 the base64-encoded string
     * @param divisor the modulo divisor (maxBuckets)
     * @return the modulo result
     */
    @WhatsAppWebExport(moduleName = "WABase64Modulo", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static int base64Modulo(String base64, int divisor) {
        // WABase64Modulo.default
        // Decodes the base64 string into raw bytes and processes each byte
        // as two 4-bit nibbles to mirror the JS BigInt-free implementation
        var decoded = Base64.getDecoder().decode(base64);
        var remainder = 0;
        for (var b : decoded) {
            var unsigned = b & 0xFF;
            var high = unsigned >> 4;
            var low = unsigned & 0x0F;
            remainder = ((remainder << 4) + high) % divisor;
            remainder = ((remainder << 4) + low) % divisor;
        }
        return remainder;
    }

    /**
     * Returns the hostname of this CDN host.
     *
     * @implNote WAWebMediaHost.MediaHost constructor: {@code this.hostname = t.hostname}.
     * @return the hostname, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    String hostname();

    /**
     * Returns the optional {@code class} attribute advertised by the server
     * for this host.
     *
     * <p>The {@code class} attribute groups hosts by deployment tier (e.g.
     * {@code mms} versus {@code mmg}) and is echoed back unchanged into
     * downstream analytics and request-tagging objects produced by
     * {@code mapParsedMediaConn}.
     *
     * @implNote WAWebMediaHost.MediaHost constructor: {@code this.class = t.class}.
     * WAMediaConnParser.mediaConnParser host parser: {@code class: e.maybeAttrString("class")}.
     * @return an {@link Optional} containing the host class, or empty if the
     *         attribute is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    Optional<String> hostClass();

    /**
     * Returns the list of IP addresses the server advertises for this
     * host.
     *
     * @implNote WAWebMediaHost.MediaHost constructor: {@code this.ips = t.ips || []}.
     * @return an unmodifiable list of IP address strings, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    List<String> ips();

    /**
     * Returns the set of media types this host accepts for downloads.
     *
     * <p>When the server response omits the download rules the set
     * defaults to all known server media types (minus the handful of
     * non-routable entries filtered by {@code compactMap}).
     *
     * @implNote WAWebMediaHost.MediaHost constructor: {@code this.$1}
     * produced by the {@code c(parseRules)} helper, defaulting to
     * {@code MEDIA_TYPE_VALUES}.
     * @return an unmodifiable set of supported download media paths
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    Set<MediaPath> download();

    /**
     * Returns the set of media types this host accepts for uploads.
     *
     * <p>When the server response omits the upload rules the set
     * defaults to all known server media types (minus the handful of
     * non-routable entries filtered by {@code compactMap}).
     *
     * @implNote WAWebMediaHost.MediaHost constructor: {@code this.$2}
     * produced by the {@code c(parseRules)} helper, defaulting to
     * {@code MEDIA_TYPE_VALUES}.
     * @return an unmodifiable set of supported upload media paths
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    Set<MediaPath> upload();

    /**
     * Returns the download bucket identifiers owned by this host.
     *
     * <p>Buckets participate in the deterministic download host
     * selection: the file hash is reduced modulo {@code maxBuckets} to
     * pick the bucket, then the owning host is looked up.
     *
     * @implNote WAWebMediaHost.MediaHost constructor:
     * {@code this.downloadBuckets = r} from the {@code c(t.rules)} helper.
     * @return an unmodifiable list of bucket numbers, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    List<Integer> downloadBuckets();

    /**
     * Returns the nested fallback hostname declared by this host, if any.
     *
     * <p>Only {@link Primary} hosts can advertise a nested fallback;
     * {@link Fallback} hosts always return an empty optional. The nested
     * fallback is rotated in by {@code selectHost} when the selected host
     * itself fails.
     *
     * @implNote WAWebMediaHost.MediaHost constructor:
     * {@code this.fallback = t.fallback != null ? new MediaHost(...) : null}.
     * @return an {@link Optional} containing the fallback hostname, or empty
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    Optional<String> fallbackHostname();

    /**
     * Returns whether this host accepts a download of the media produced
     * by the given provider.
     *
     * <p>The provider's media path is first normalised to collapse PTV
     * variants onto video and product onto image before the host's
     * supported-type set is consulted.
     *
     * @implNote WAWebMediaHost.MediaHost.supportsDownloadMediaType combined
     * with {@code WAWebMediaHost.d} for download-type normalisation.
     * @param provider the media provider whose type to check
     * @return {@code true} if this host supports downloading that media type
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    boolean canDownload(MediaProvider provider);

    /**
     * Returns whether this host accepts an upload of the media produced
     * by the given provider.
     *
     * <p>The provider's media path is first normalised to collapse PTV
     * onto video and product-catalog-image onto product before the
     * host's supported-type set is consulted.
     *
     * @implNote WAWebMediaHost.MediaHost.supportsUploadMediaType combined
     * with {@code WAWebMediaHost.m} for upload-type normalisation.
     * @param provider the media provider whose type to check
     * @return {@code true} if this host supports uploading that media type
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    boolean canUpload(MediaProvider provider);

    /**
     * Normalises a media path before consulting a host's download support
     * set. Mirrors the WA Web helper {@code d(t)}:
     * <ul>
     *   <li>{@link MediaPath#PTV} and {@link MediaPath#NEWSLETTER_PTV}
     *       collapse to {@link MediaPath#VIDEO}.</li>
     *   <li>{@link MediaPath#PRODUCT} collapses to
     *       {@link MediaPath#IMAGE}.</li>
     *   <li>Every other type is returned unchanged.</li>
     * </ul>
     *
     * @implNote WAWebMediaHost local helper {@code d}.
     * @param path the media path to normalize
     * @return the normalized media path for download checking
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static MediaPath normalizeDownloadMediaType(MediaPath path) {
        // WAWebMediaHost.MediaHost
        // Collapses PTV / newsletter-PTV to VIDEO and PRODUCT to IMAGE
        // before consulting the download-type set
        return switch (path) {
            case PTV, NEWSLETTER_PTV -> MediaPath.VIDEO;
            case PRODUCT -> MediaPath.IMAGE;
            default -> path;
        };
    }

    /**
     * Normalises a media path before consulting a host's upload support
     * set. Mirrors the WA Web helper {@code m(t)}:
     * <ul>
     *   <li>{@link MediaPath#PTV} collapses to
     *       {@link MediaPath#VIDEO}.</li>
     *   <li>{@link MediaPath#PRODUCT_CATALOG_IMAGE} collapses to
     *       {@link MediaPath#PRODUCT}.</li>
     *   <li>Every other type is returned unchanged.</li>
     * </ul>
     *
     * @implNote WAWebMediaHost local helper {@code m}.
     * @param path the media path to normalize
     * @return the normalized media path for upload checking
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static MediaPath normalizeUploadMediaType(MediaPath path) {
        // WAWebMediaHost.MediaHost
        // Collapses PTV to VIDEO and PRODUCT_CATALOG_IMAGE to PRODUCT
        // before consulting the upload-type set
        return switch (path) {
            case PTV -> MediaPath.VIDEO;
            case PRODUCT_CATALOG_IMAGE -> MediaPath.PRODUCT;
            default -> path;
        };
    }

    /**
     * A primary CDN host.
     *
     * <p>Primary hosts have type {@code "primary"} and may advertise a
     * nested fallback hostname together with its own IP list. The route
     * selection algorithm prefers primary hosts over fallback-class hosts
     * and consults the download bucket assignments to pick one
     * deterministically.
     *
     * <p>This record is the Java realisation of the
     * {@code HOST_TYPE.PRIMARY} discriminator value: in WA Web a host's
     * {@code type} string field is set to the frozen
     * {@code HOST_TYPE.PRIMARY = "primary"} constant; in Cobalt the
     * discriminator is materialised as the record class identity so an
     * {@code instanceof Primary} check replaces the JS string compare.
     *
     * @implNote WAWebMediaHost.MediaHost for a {@code HOST_TYPE.PRIMARY}
     * instance.
     * @implNote WAWebMediaHost.HOST_TYPE.PRIMARY: the {@code "primary"}
     * discriminator string is collapsed into the {@link Primary} class
     * identity.
     * @param hostname         the hostname of this host
     * @param hostClass        the optional {@code class} attribute advertised
     *                         by the server, used by analytics and request
     *                         tagging
     * @param ips              the list of IP addresses advertised by the server
     * @param fallbackHostname the fallback hostname, or empty if no fallback
     * @param fallbackClass    the optional {@code fallback_class} attribute
     *                         advertised by the server alongside the nested
     *                         fallback host
     * @param fallbackIps      the list of fallback IP addresses
     * @param downloadBuckets  the download bucket assignments for this host
     * @param download         the set of supported download media types
     * @param upload           the set of supported upload media types
     */
    @WhatsAppWebModule(moduleName = "WAWebMediaHost")
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "HOST_TYPE",
            adaptation = WhatsAppAdaptation.ADAPTED)
    record Primary(
            String hostname,
            Optional<String> hostClass,
            List<String> ips,
            Optional<String> fallbackHostname,
            Optional<String> fallbackClass,
            List<String> fallbackIps,
            List<Integer> downloadBuckets,
            Set<MediaPath> download,
            Set<MediaPath> upload
    ) implements MediaHost {

        /**
         * Returns whether this primary host accepts a download of the
         * media produced by the given provider, after applying the
         * download-type normalisation.
         *
         * @implNote WAWebMediaHost.MediaHost.supportsDownloadMediaType.
         * @param provider the media provider whose type to check
         * @return {@code true} if the normalized type is in the download set
         */
        @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public boolean canDownload(MediaProvider provider) {
            Objects.requireNonNull(provider, "provider cannot be null");
            // WAWebMediaHost.MediaHost
            // supportsDownloadMediaType(t) = this.$1.has(d(t))
            return download.contains(normalizeDownloadMediaType(provider.mediaPath()));
        }

        /**
         * Returns whether this primary host accepts an upload of the
         * media produced by the given provider, after applying the
         * upload-type normalisation.
         *
         * @implNote WAWebMediaHost.MediaHost.supportsUploadMediaType.
         * @param provider the media provider whose type to check
         * @return {@code true} if the normalized type is in the upload set
         */
        @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public boolean canUpload(MediaProvider provider) {
            Objects.requireNonNull(provider, "provider cannot be null");
            // WAWebMediaHost.MediaHost
            // supportsUploadMediaType(t) = this.$2.has(m(t))
            return upload.contains(normalizeUploadMediaType(provider.mediaPath()));
        }
    }

    /**
     * A fallback-class CDN host.
     *
     * <p>Fallback hosts have type {@code "fallback"} and never advertise
     * a nested fallback of their own. They are used by the retry loop as
     * an alternate endpoint after the primary host has exhausted its
     * attempts.
     *
     * <p>This record is the Java realisation of the
     * {@code HOST_TYPE.FALLBACK} discriminator value: in WA Web a host's
     * {@code type} string field is set to the frozen
     * {@code HOST_TYPE.FALLBACK = "fallback"} constant; in Cobalt the
     * discriminator is materialised as the record class identity so an
     * {@code instanceof Fallback} check replaces the JS string compare,
     * and WA Web's {@code isFallback()} method is similarly replaced by
     * the same pattern match.
     *
     * @implNote WAWebMediaHost.MediaHost for a {@code HOST_TYPE.FALLBACK}
     * instance.
     * @implNote WAWebMediaHost.HOST_TYPE.FALLBACK: the {@code "fallback"}
     * discriminator string is collapsed into the {@link Fallback} class
     * identity.
     * @param hostname         the hostname of this host
     * @param hostClass        the optional {@code class} attribute advertised
     *                         by the server, used by analytics and request
     *                         tagging
     * @param ips              the list of IP addresses advertised by the server
     * @param downloadBuckets  the download bucket assignments for this host
     * @param download         the set of supported download media types
     * @param upload           the set of supported upload media types
     */
    @WhatsAppWebModule(moduleName = "WAWebMediaHost")
    @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "HOST_TYPE",
            adaptation = WhatsAppAdaptation.ADAPTED)
    record Fallback(
            String hostname,
            Optional<String> hostClass,
            List<String> ips,
            List<Integer> downloadBuckets,
            Set<MediaPath> download,
            Set<MediaPath> upload
    ) implements MediaHost {

        /**
         * Returns whether this fallback host accepts a download of the
         * media produced by the given provider, after applying the
         * download-type normalisation.
         *
         * @implNote WAWebMediaHost.MediaHost.supportsDownloadMediaType.
         * @param provider the media provider whose type to check
         * @return {@code true} if the normalized type is in the download set
         */
        @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public boolean canDownload(MediaProvider provider) {
            Objects.requireNonNull(provider, "provider cannot be null");
            // WAWebMediaHost.MediaHost
            // supportsDownloadMediaType(t) = this.$1.has(d(t))
            return download.contains(normalizeDownloadMediaType(provider.mediaPath()));
        }

        /**
         * Returns whether this fallback host accepts an upload of the
         * media produced by the given provider, after applying the
         * upload-type normalisation.
         *
         * @implNote WAWebMediaHost.MediaHost.supportsUploadMediaType.
         * @param provider the media provider whose type to check
         * @return {@code true} if the normalized type is in the upload set
         */
        @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public boolean canUpload(MediaProvider provider) {
            Objects.requireNonNull(provider, "provider cannot be null");
            // WAWebMediaHost.MediaHost
            // supportsUploadMediaType(t) = this.$2.has(m(t))
            return upload.contains(normalizeUploadMediaType(provider.mediaPath()));
        }

        /**
         * Always returns an empty optional because fallback-class hosts
         * never advertise a nested fallback hostname of their own.
         *
         * @implNote WAWebMediaHost.MediaHost constructor: fallback hosts
         * are built with {@code fallback: void 0}.
         * @return an empty {@link Optional}
         */
        @WhatsAppWebExport(moduleName = "WAWebMediaHost", exports = "MediaHost",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public Optional<String> fallbackHostname() {
            return Optional.empty();
        }
    }
}
