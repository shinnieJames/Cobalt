package com.github.auties00.cobalt.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads platform-native shared libraries used by Cobalt's call layer.
 *
 * <h2>Resolution order</h2>
 *
 * <ol>
 *   <li><b>Classpath bundle</b> — looks the binary up at the path
 *       declared in the manifest entry (e.g.
 *       {@code dependencies/libopus/bin/linux-x86_64/libopus.so}).
 *       Populated by the per-classifier
 *       {@code cobalt-VERSION-natives-<classifier>.jar} artifacts a
 *       consumer can opt into for offline / Maven-Central-only
 *       deployments.</li>
 *   <li><b>On-disk cache</b> — checks
 *       {@code ${user.home}/.cobalt/natives/<cobalt-version>/<classifier>/<libname>}.
 *       Populated by a previous successful download.</li>
 *   <li><b>GitHub download</b> — fetches the binary from the Cobalt
 *       source repository at the same path declared in the manifest,
 *       streams it through a {@link DigestInputStream} into a
 *       {@code .part} file under the cache, verifies the SHA-256
 *       against the manifest, then atomically renames into place.</li>
 *   <li><b>System library</b> — last-resort fallback for development
 *       on hosts where the library is package-managed
 *       (apt/Homebrew/etc.).</li>
 * </ol>
 *
 * <p>The default {@code cobalt} JAR ships <em>no</em> binaries — it
 * relies on the download path. Bundled
 * {@code cobalt:natives-<classifier>} JARs short-circuit the download
 * but only for their declared classifier; if the consumer somehow
 * picked the wrong classifier (e.g. shipped a {@code linux-x86_64}
 * fat-jar to an {@code aarch64} host), the loader detects this and
 * throws an explicit error rather than silently falling through to
 * the slow download path.
 *
 * <h2>Configuration</h2>
 *
 * <p>The {@code cobalt.natives.cache} system property overrides the
 * on-disk cache root (default {@code ${user.home}/.cobalt/natives}).
 * Useful when {@code $HOME} is read-only (e.g. some CI runners) or
 * when sharing the cache between several JVMs.
 *
 * <h2>FFM workflow</h2>
 *
 * <pre>{@code
 *   var lookup = NativeLibLoader.load("opus", arena);
 *   var encoderCreate = Linker.nativeLinker().downcallHandle(
 *       lookup.find("opus_encoder_create").orElseThrow(),
 *       FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));
 * }</pre>
 *
 * <p>Library naming: callers supply the platform-agnostic library
 * name (e.g. {@code "opus"}) and the loader maps it to the OS-specific
 * filename ({@code libopus.so} / {@code libopus.dylib} /
 * {@code opus.dll}) per {@link System#mapLibraryName(String)}.
 */
public final class NativeLibLoader {
    /**
     * Resource path (inside the regular {@code cobalt} JAR) of the
     * SHA-256 + path manifest the download leg cross-checks every
     * fetched binary against. Without this manifest, a tampered
     * upstream could silently substitute attacker-supplied natives.
     */
    private static final String MANIFEST_RESOURCE = "META-INF/native-checksums.json";

    /**
     * Canonical GitHub raw-content prefix the download leg fetches
     * binaries from — concatenated with the immutable commit SHA
     * recorded in the manifest, producing the URL
     * {@code https://raw.githubusercontent.com/Auties00/Cobalt/<sha>/<path>}.
     */
    private static final String REPO_BASE =
            "https://raw.githubusercontent.com/Auties00/Cobalt/";

    /**
     * System property: overrides the on-disk cache root.
     */
    private static final String SYS_CACHE = "cobalt.natives.cache";

    /**
     * Default cache directory under the user's home.
     */
    private static final Path DEFAULT_CACHE_ROOT =
            Paths.get(System.getProperty("user.home", "."), ".cobalt", "natives");

    /**
     * The classifiers Cobalt publishes natives for. Used by
     * {@link #verifyNoForeignBundle} to detect wrong-classifier
     * bundle mismatches at load time.
     */
    private static final String[] KNOWN_CLASSIFIERS = {
            "linux-x86_64",
            "linux-aarch64",
            "darwin-x86_64",
            "darwin-aarch64",
            "windows-x86_64",
            "windows-aarch64",
    };

    /**
     * HTTP connect timeout for the download leg.
     */
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * HTTP per-request timeout for the download leg.
     */
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /**
     * Length, in hex chars, of a SHA-256 digest.
     */
    private static final int SHA256_HEX_LENGTH = 64;

    /**
     * Per-process cache of {@link SymbolLookup}s, keyed by the
     * platform-agnostic name passed to {@link #load(String, Arena)}.
     */
    private static final ConcurrentHashMap<String, SymbolLookup> CACHE = new ConcurrentHashMap<>();

    /**
     * The Maven-style classifier of the running platform — one of
     * {@link #KNOWN_CLASSIFIERS}. Computed once per JVM.
     */
    private static final String CLASSIFIER = computeClassifier();

    /**
     * Lazily-loaded list of every {@link #MANIFEST_RESOURCE} on the
     * classpath. Each element is one module's manifest; the
     * {@code cobalt} module ships one, the
     * {@code cobalt-call-toolkit} companion ships another, and so
     * on. Looked up via {@link #lookupEntry(String, String)} which
     * scans the list to find the manifest declaring a given
     * {@code <lib>/<classifier>} key.
     */
    private static volatile List<ModuleManifest> manifests;

    /**
     * Lazily-resolved on-disk cache root.
     */
    private static volatile Path cacheRoot;

    /**
     * Lazily-built HTTP client used to fetch binaries from GitHub.
     */
    private static volatile HttpClient httpClient;

    /**
     * Prevents instantiation.
     */
    private NativeLibLoader() {
        throw new AssertionError("NativeLibLoader is not instantiable");
    }

    /**
     * Returns the running platform's Maven-style classifier (e.g.
     * {@code "linux-x86_64"}).
     *
     * @return the classifier string
     */
    public static String classifier() {
        return CLASSIFIER;
    }

    /**
     * Loads the named native library and returns a {@link SymbolLookup}
     * bound to the supplied {@link Arena}.
     *
     * @param libraryName the platform-agnostic library name (e.g.
     *                    {@code "speexdsp"})
     * @param arena       the arena bounding the lookup's lifetime;
     *                    pass {@link Arena#global()} for
     *                    JVM-lifetime libraries
     * @return a {@link SymbolLookup} for the library's exports
     * @throws NullPointerException if any argument is {@code null}
     * @throws UnsatisfiedLinkError if the library cannot be located
     *                              via classpath, cache, download,
     *                              or system fallback
     */
    public static SymbolLookup load(String libraryName, Arena arena) {
        Objects.requireNonNull(libraryName, "libraryName cannot be null");
        Objects.requireNonNull(arena, "arena cannot be null");
        var cached = CACHE.get(libraryName);
        if (cached != null) {
            return cached;
        }
        synchronized (NativeLibLoader.class) {
            cached = CACHE.get(libraryName);
            if (cached != null) {
                return cached;
            }
            var path = resolve(libraryName);
            if (path != null) {
                System.load(path.toAbsolutePath().toString());
                var lookup = SymbolLookup.libraryLookup(path, arena);
                CACHE.put(libraryName, lookup);
                return lookup;
            }
            try {
                System.loadLibrary(libraryName);
                var lookup = SymbolLookup.libraryLookup(System.mapLibraryName(libraryName), arena);
                CACHE.put(libraryName, lookup);
                return lookup;
            } catch (UnsatisfiedLinkError e) {
                throw new UnsatisfiedLinkError(
                        "could not load native library '" + libraryName + "' for classifier '"
                                + CLASSIFIER + "': " + describeFailure(libraryName)
                                + " — System.loadLibrary fallback also failed (" + e.getMessage() + ")");
            }
        }
    }

    /**
     * Resolves the binary for {@code libraryName} on disk, walking
     * classpath → cache → download. Returns {@code null} when no
     * manifest entry is published for this library/classifier
     * combination — the caller then tries
     * {@link System#loadLibrary} as a last resort (the
     * developer-on-Linux-with-libopus-installed scenario).
     *
     * @param libraryName the library name
     * @return the local path, or {@code null}
     */
    private static Path resolve(String libraryName) {
        var lookup = lookupEntry(libraryName, CLASSIFIER).orElse(null);
        if (lookup == null) {
            return null;
        }
        var entry = lookup.entry();
        // Preserve the binary's on-disk basename rather than
        // remapping via System.mapLibraryName(libraryName). For
        // single-library bindings (libopus → libopus.so) those
        // happen to match. For multi-library bindings whose members
        // reference each other through ELF SONAME tags
        // (libavformat.so.61 → libavcodec.so.61), the original
        // basename is what the dynamic linker needs to see when it
        // resolves NEEDED entries against already-loaded shared
        // objects. Renaming to libffmpeg-avformat.so would defeat
        // that.
        var fileName = basenameOf(entry.path());

        var classpathPath = extractFromClasspath(entry, fileName);
        if (classpathPath != null) {
            return classpathPath;
        }
        verifyNoForeignBundle(libraryName, entry);

        var cachePath = resolveCachePath(fileName);
        if (cachePath == null) {
            return null;
        }
        if (Files.exists(cachePath)) {
            return cachePath;
        }
        return downloadToCache(libraryName, fileName, lookup, cachePath);
    }

    /**
     * Returns the trailing path component of a forward-slash
     * separated manifest path. Used to recover the canonical
     * binary filename ({@code libavformat.so.61}) so the cache and
     * classpath extraction don't re-derive it from
     * {@link System#mapLibraryName}, which strips the soname
     * version suffix.
     *
     * @param path the manifest path
     * @return the trailing component (basename)
     */
    private static String basenameOf(String path) {
        var idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    /**
     * Tries to extract the binary from the classpath at the path
     * declared in the manifest entry — the same
     * {@code dependencies/<lib>/bin/<classifier>/<filename>} path
     * the per-classifier JARs ship binaries under, matching the
     * source-repo layout. Returns the extracted path on success, or
     * {@code null} when no resource exists.
     *
     * @param entry    the manifest entry whose path to probe
     * @param fileName the platform-mapped filename
     * @return the extracted path, or {@code null}
     */
    private static Path extractFromClasspath(Entry entry, String fileName) {
        try (var in = NativeLibLoader.class.getClassLoader().getResourceAsStream(entry.path())) {
            if (in == null) {
                return null;
            }
            var dir = ensureCacheDir();
            var out = dir.resolve(fileName);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "failed to extract bundled native library: " + entry.path(), e);
        }
    }

    /**
     * Throws when the classpath contains a binary for one of the
     * other published classifiers but not for the running one — the
     * consumer picked the wrong native bundle. Pointing at the
     * misconfiguration is cheaper than waiting for a multi-MB pull
     * on every cold start.
     *
     * <p>Derives the {@code dependencies/<lib-dir>/bin/<x>/<file>}
     * pattern from the manifest entry's own path so we don't need
     * a separate lib-name → lib-dir mapping.
     *
     * @param libraryName the library name (for the diagnostic)
     * @param entry       the manifest entry for the running
     *                    classifier
     */
    private static void verifyNoForeignBundle(String libraryName, Entry entry) {
        var ourPath = entry.path();
        var marker = "/bin/" + CLASSIFIER + "/";
        int idx = ourPath.indexOf(marker);
        if (idx < 0) {
            return;
        }
        var prefix = ourPath.substring(0, idx + "/bin/".length());
        var suffix = ourPath.substring(idx + marker.length());
        for (var other : KNOWN_CLASSIFIERS) {
            if (other.equals(CLASSIFIER)) {
                continue;
            }
            var probe = prefix + other + "/"
                    + suffix.replace(extensionFor(CLASSIFIER), extensionFor(other));
            if (NativeLibLoader.class.getClassLoader().getResource(probe) != null) {
                throw new UnsatisfiedLinkError(
                        "wrong native bundle on classpath for library '" + libraryName
                                + "': found " + probe
                                + " but the running platform is '" + CLASSIFIER + "'."
                                + " Use the default `cobalt` artifact (downloads natives at runtime),"
                                + " or replace the natives JAR with `cobalt:natives-" + CLASSIFIER + "`.");
            }
        }
    }

    /**
     * Returns the on-disk cache path for the given filename —
     * {@code <cache-root>/<cobalt-version>/<classifier>/<filename>}.
     * The version segment ensures stale binaries from a prior
     * install aren't reused after an upgrade.
     *
     * @param fileName the platform-mapped filename
     * @return the cache path, or {@code null} when the manifest is
     *         absent (no version to scope the cache by)
     */
    private static Path resolveCachePath(String fileName) {
        var version = anyManifestVersion();
        if (version == null) {
            return null;
        }
        try {
            var dir = cacheRoot().resolve(version).resolve(CLASSIFIER);
            Files.createDirectories(dir);
            return dir.resolve(fileName);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "failed to create cache directory for " + fileName, e);
        }
    }

    /**
     * Downloads the binary for {@code libraryName} from GitHub,
     * streams it to a {@code .part} file under the cache while
     * digesting + size-counting in the same pass, then atomically
     * renames into place once the SHA-256 + size match the manifest.
     *
     * <p>Memory cost is bounded by {@link Files#copy}'s 8 KB buffer
     * — never holds the full binary in memory.
     *
     * @param libraryName the library name (for diagnostics)
     * @param fileName    the platform-mapped filename
     * @param lookup      the manifest entry plus the manifest that
     *                    declared it (so the URL pins to the right
     *                    commit SHA)
     * @param cachePath   the destination path
     * @return the on-disk cache path with the verified binary
     * @throws UnsatisfiedLinkError if the download fails, the size
     *                              mismatches, or the checksum
     *                              doesn't match
     */
    private static Path downloadToCache(String libraryName, String fileName,
                                        ManifestLookup lookup, Path cachePath) {
        var entry = lookup.entry();
        var commitSha = lookup.manifest().commitSha();
        if (commitSha == null || commitSha.isEmpty()) {
            throw new UnsatisfiedLinkError(
                    "checksum manifest " + lookup.manifest().source()
                            + " is missing the commitSha field — "
                            + "the release process must run "
                            + "tooling/native-checksums/generate.sh after committing the binaries");
        }
        var url = REPO_BASE + commitSha + "/" + entry.path();
        var tmp = cachePath.resolveSibling(fileName + ".part");

        String actualSha;
        long actualSize;
        try {
            var response = httpClient().send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(HTTP_REQUEST_TIMEOUT)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " from " + url);
            }
            var digest = MessageDigest.getInstance("SHA-256");
            try (var body = response.body();
                 var digesting = new DigestInputStream(body, digest)) {
                Files.copy(digesting, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            actualSha = HexFormat.of().formatHex(digest.digest());
            actualSize = Files.size(tmp);
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new UnsatisfiedLinkError(
                    "failed to download '" + url + "' for native library '" + libraryName
                            + "': " + e.getMessage()
                            + " — add the cobalt:natives-" + CLASSIFIER
                            + " classifier JAR to the classpath to skip the download.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteQuietly(tmp);
            throw new UnsatisfiedLinkError(
                    "interrupted while downloading '" + libraryName + "' from " + url);
        } catch (NoSuchAlgorithmException e) {
            deleteQuietly(tmp);
            throw new IllegalStateException("SHA-256 unavailable", e);
        }

        if (actualSize != entry.size()) {
            deleteQuietly(tmp);
            throw new UnsatisfiedLinkError(
                    "downloaded size mismatch for '" + libraryName + "': expected "
                            + entry.size() + " bytes, got " + actualSize);
        }
        if (!actualSha.equalsIgnoreCase(entry.sha256())) {
            deleteQuietly(tmp);
            throw new UnsatisfiedLinkError(
                    "SHA-256 mismatch for '" + libraryName + "': expected "
                            + entry.sha256() + ", got " + actualSha);
        }
        try {
            Files.move(tmp, cachePath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new UncheckedIOException(
                    "failed to commit cached native library to " + cachePath, e);
        }
        return cachePath;
    }

    /**
     * Returns the lazily-built shared {@link HttpClient}. Held
     * statically because the {@code java.net.http} client is
     * heavyweight (selector thread + thread pool) and cheap to
     * reuse across many downloads.
     *
     * @return the client
     */
    private static HttpClient httpClient() {
        var c = httpClient;
        if (c != null) {
            return c;
        }
        synchronized (NativeLibLoader.class) {
            if (httpClient != null) {
                return httpClient;
            }
            httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(HTTP_CONNECT_TIMEOUT)
                    .build();
            return httpClient;
        }
    }

    /**
     * Returns the lazily-resolved on-disk cache root. Honours the
     * {@code cobalt.natives.cache} system property; falls back to
     * {@link #DEFAULT_CACHE_ROOT}.
     *
     * @return the cache root
     */
    private static Path cacheRoot() {
        var local = cacheRoot;
        if (local != null) {
            return local;
        }
        synchronized (NativeLibLoader.class) {
            if (cacheRoot != null) {
                return cacheRoot;
            }
            var override = System.getProperty(SYS_CACHE);
            cacheRoot = override != null && !override.isEmpty()
                    ? Paths.get(override)
                    : DEFAULT_CACHE_ROOT;
            return cacheRoot;
        }
    }

    /**
     * Ensures the per-classifier directory inside the cache root
     * exists, even when the manifest version is unknown
     * (development tree, no manifest). Used as the extraction
     * destination for classpath-bundled binaries.
     *
     * @return the directory path
     * @throws IOException if the directory cannot be created
     */
    private static Path ensureCacheDir() throws IOException {
        var version = anyManifestVersion();
        var dir = cacheRoot().resolve(version != null ? version : "dev").resolve(CLASSIFIER);
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Returns a one-line description of why the binary couldn't be
     * found, used when surfacing the final
     * {@link UnsatisfiedLinkError} to the caller.
     *
     * @param libraryName the library name
     * @return the diagnostic
     */
    private static String describeFailure(String libraryName) {
        if (manifestEntry(libraryName, CLASSIFIER).isEmpty()) {
            return "no classpath bundle and no manifest entry — "
                    + "this classifier isn't published";
        }
        return "no classpath bundle and download/cache failed";
    }

    /**
     * Returns the conventional library extension for the given
     * classifier — used by {@link #verifyNoForeignBundle} to
     * cross-translate filenames across classifiers.
     *
     * @param classifier the classifier
     * @return the extension (with leading dot)
     */
    private static String extensionFor(String classifier) {
        if (classifier.startsWith("linux-")) return ".so";
        if (classifier.startsWith("darwin-")) return ".dylib";
        if (classifier.startsWith("windows-")) return ".dll";
        return "";
    }

    /**
     * Best-effort delete, used to clean up the {@code .part} file
     * on any download failure so a retry isn't poisoned by partial
     * state.
     *
     * @param path the path to delete
     */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException _) {
        }
    }

    /**
     * Looks up an entry across every loaded manifest, returning the
     * entry plus the manifest that declared it. The manifest is
     * needed by {@link #downloadToCache} so it can pin the URL to
     * the right commit SHA — different modules ship different
     * binaries and may have been committed at different SHAs.
     *
     * @param libraryName the library name
     * @param classifier  the classifier
     * @return the lookup, or empty when no manifest declares the key
     */
    private static Optional<ManifestLookup> lookupEntry(String libraryName, String classifier) {
        var key = libraryName + "/" + classifier;
        for (var module : loadManifests()) {
            var entry = module.entries().get(key);
            if (entry != null) {
                return Optional.of(new ManifestLookup(module, entry));
            }
        }
        return Optional.empty();
    }

    /**
     * Convenience wrapper that returns just the {@link Entry}; used
     * by call sites that don't need the owning manifest.
     *
     * @param libraryName the library name
     * @param classifier  the classifier
     * @return the entry, or empty when absent
     */
    private static Optional<Entry> manifestEntry(String libraryName, String classifier) {
        return lookupEntry(libraryName, classifier).map(ManifestLookup::entry);
    }

    /**
     * Returns the version of any loaded manifest — used to scope
     * the cache directory. When multiple modules ship manifests at
     * different versions, the cache scoping picks the first
     * manifest's version; collisions across versions are unlikely
     * in practice (binary filenames are unique per lib) and would
     * just cause redundant downloads, not corruption.
     *
     * @return some loaded manifest's version, or {@code null} when
     *         no manifest is on the classpath
     */
    private static String anyManifestVersion() {
        var loaded = loadManifests();
        return loaded.isEmpty() ? null : loaded.get(0).version();
    }

    /**
     * Loads (and caches) every {@link #MANIFEST_RESOURCE} on the
     * classpath. Empty list when no module ships a manifest — the
     * loader then treats every {@link #load} call as "no download
     * leg, classpath-or-system-only".
     *
     * <p>Conflict policy: identical entries across modules are fine
     * (two modules shipping the same opus binary share an SHA);
     * conflicting entries (same key, different SHA) throw an
     * {@link IllegalStateException} naming both manifests.
     *
     * @return the loaded manifests, or empty
     */
    private static List<ModuleManifest> loadManifests() {
        var local = manifests;
        if (local != null) {
            return local;
        }
        synchronized (NativeLibLoader.class) {
            if (manifests != null) {
                return manifests;
            }
            var loaded = new ArrayList<ModuleManifest>();
            try {
                var urls = NativeLibLoader.class.getClassLoader().getResources(MANIFEST_RESOURCE);
                while (urls.hasMoreElements()) {
                    var url = urls.nextElement();
                    try (var stream = url.openStream()) {
                        loaded.add(parseManifest(url.toString(), stream));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("failed to enumerate " + MANIFEST_RESOURCE, e);
            }
            verifyNoConflicts(loaded);
            manifests = Collections.unmodifiableList(loaded);
            return manifests;
        }
    }

    /**
     * Throws an {@link IllegalStateException} if two manifests
     * declare the same {@code <lib>/<classifier>} key with
     * different SHA-256 — that means somebody shipped a tampered
     * binary, or two unrelated builds collided on a name. Identical
     * entries are silently allowed (a transitive dep + a direct dep
     * could each pull the same module's natives).
     *
     * @param loaded the parsed manifests
     */
    private static void verifyNoConflicts(List<ModuleManifest> loaded) {
        var seen = new HashMap<String, ModuleManifest>();
        for (var module : loaded) {
            for (var key : module.entries().keySet()) {
                var existingModule = seen.get(key);
                if (existingModule == null) {
                    seen.put(key, module);
                    continue;
                }
                var existingEntry = existingModule.entries().get(key);
                var newEntry = module.entries().get(key);
                if (!existingEntry.sha256().equalsIgnoreCase(newEntry.sha256())) {
                    throw new IllegalStateException(
                            "conflicting native-checksum entries for '" + key + "': "
                                    + existingModule.source() + " declares sha=" + existingEntry.sha256()
                                    + ", " + module.source() + " declares sha=" + newEntry.sha256());
                }
            }
        }
    }

    /**
     * Parses one manifest's JSON body into a {@link ModuleManifest}.
     *
     * @param source human-readable origin (the resource URL string),
     *               used only in conflict messages
     * @param json   the manifest body
     * @return the parsed manifest
     */
    private static ModuleManifest parseManifest(String source, InputStream json) {
        var root = JSON.parseObject(json, StandardCharsets.UTF_8);
        var version = root.getString("version");
        var commitSha = root.getString("commitSha");
        var entries = new LinkedHashMap<String, Entry>();
        var binaries = root.getJSONObject("binaries");
        if (binaries != null) {
            for (var key : binaries.keySet()) {
                var obj = (JSONObject) binaries.get(key);
                entries.put(key, new Entry(
                        obj.getString("sha256"),
                        obj.getLongValue("size"),
                        obj.getString("path")));
            }
        }
        return new ModuleManifest(source, version, commitSha, Map.copyOf(entries));
    }

    /**
     * Computes the Maven-style classifier of the running JVM from
     * {@code os.name} and {@code os.arch}.
     *
     * @return the classifier string (e.g. {@code "linux-x86_64"})
     * @throws IllegalStateException if the running OS or
     *                               architecture is not recognised
     */
    private static String computeClassifier() {
        var name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return osToken(name) + "-" + archToken(arch);
    }

    /**
     * Maps {@code os.name} to a stable OS token.
     *
     * @param name the lower-cased {@code os.name} value
     * @return one of {@code "linux"}, {@code "darwin"},
     *         {@code "windows"}
     * @throws IllegalStateException if the OS is not supported
     */
    private static String osToken(String name) {
        if (name.contains("linux")) return "linux";
        if (name.contains("mac") || name.contains("darwin")) return "darwin";
        if (name.contains("windows")) return "windows";
        throw new IllegalStateException("unsupported os.name: " + name);
    }

    /**
     * Maps {@code os.arch} to a stable CPU token. Folds the various
     * aliases ({@code amd64}/{@code x86_64},
     * {@code aarch64}/{@code arm64}) into a canonical form.
     *
     * @param arch the lower-cased {@code os.arch} value
     * @return one of {@code "x86_64"}, {@code "aarch64"}
     * @throws IllegalStateException if the architecture is not
     *                               supported
     */
    private static String archToken(String arch) {
        if (arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64")) return "x86_64";
        if (arch.equals("aarch64") || arch.equals("arm64")) return "aarch64";
        throw new IllegalStateException("unsupported os.arch: " + arch);
    }

    /**
     * Drops every cached {@link SymbolLookup}, forcing a fresh
     * resolve on the next {@link #load} for any given library.
     * Useful for tests that swap the classpath or cache between
     * runs, and for applications that want to reclaim the native
     * handles after a long-lived call session ends.
     */
    public static void clearCache() {
        synchronized (NativeLibLoader.class) {
            CACHE.clear();
        }
    }

    /**
     * One manifest entry — the integrity + locator data for a
     * single platform-specific native binary.
     *
     * @param sha256 lower-case hex SHA-256 of the binary's bytes
     * @param size   the binary's size in bytes
     * @param path   the binary's path inside the Cobalt repository
     *               (e.g.
     *               {@code "dependencies/libopus/bin/linux-x86_64/libopus.so"})
     */
    private record Entry(String sha256, long size, String path) {
        /**
         * Compact constructor — null-checks fields and validates
         * the SHA-256 hex.
         */
        Entry {
            Objects.requireNonNull(sha256, "sha256 cannot be null");
            Objects.requireNonNull(path, "path cannot be null");
            if (sha256.length() != SHA256_HEX_LENGTH) {
                throw new IllegalArgumentException(
                        "sha256 must be " + SHA256_HEX_LENGTH + " hex chars, got " + sha256.length());
            }
            if (size < 0) {
                throw new IllegalArgumentException("size must be ≥ 0");
            }
        }
    }

    /**
     * One module's parsed manifest — the {@code cobalt} module ships
     * one, the {@code cobalt-call-toolkit} ships another. Each
     * declares its own {@code version} + {@code commitSha} (since the
     * binaries each module ships were committed at potentially
     * different repository revisions) and a map of
     * {@code <lib>/<classifier> → Entry}.
     *
     * @param source    human-readable origin (the resource URL
     *                  string), used only in conflict messages
     * @param version   the module version that produced these
     *                  binaries
     * @param commitSha the immutable commit SHA the binaries were
     *                  committed at (drives the download URL)
     * @param entries   the {@code <lib>/<classifier> → Entry} map
     */
    private record ModuleManifest(String source, String version, String commitSha,
                                   Map<String, Entry> entries) {
        /**
         * Compact constructor — null-checks fields.
         */
        ModuleManifest {
            Objects.requireNonNull(source, "source cannot be null");
            Objects.requireNonNull(entries, "entries cannot be null");
        }
    }

    /**
     * The result of looking a {@code <lib>/<classifier>} key up
     * across every loaded manifest — the matching entry plus the
     * manifest that declared it. The manifest pointer is what lets
     * the download leg pin the URL to the right commit SHA: each
     * module ships its natives at the commit that introduced them,
     * and the pointer carries that commit through to the URL builder.
     *
     * @param manifest the manifest declaring this entry
     * @param entry    the entry itself
     */
    private record ManifestLookup(ModuleManifest manifest, Entry entry) {
        /**
         * Compact constructor — null-checks fields.
         */
        ManifestLookup {
            Objects.requireNonNull(manifest, "manifest cannot be null");
            Objects.requireNonNull(entry, "entry cannot be null");
        }
    }
}
