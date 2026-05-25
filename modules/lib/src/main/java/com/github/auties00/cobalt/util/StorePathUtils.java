package com.github.auties00.cobalt.util;

import com.github.auties00.cobalt.client.WhatsAppClientType;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

/**
 * Resolves the on-disk layout of Cobalt's persistent stores.
 *
 * <p>Each {@link WhatsAppClientType} owns its own home directory under a
 * caller-supplied base path, and every session lives in a UUID-named
 * subdirectory below that. Reading or writing the Cobalt session tree goes
 * through these helpers, which create directories on the read paths, identify
 * the most recently modified session for auto-resume, and recursively delete a
 * session subtree.
 */
public final class StorePathUtils {
    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private StorePathUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Resolves the path of {@code fileName} inside the session directory
     * identified by {@code uuid}, creating the session directory if necessary.
     *
     * <p>The returned path locates a per-session artifact (Signal-protocol
     * bundle, sync cursor, AB-props snapshot) and is ready for use with
     * {@link Files#newOutputStream(Path, java.nio.file.OpenOption...)} or
     * {@link Files#readAllBytes(Path)}.
     *
     * @param clientType    the client type that owns the home directory layer
     *                      of the path
     * @param baseDirectory the base storage directory
     * @param uuid          the session identifier
     * @param fileName      the file name inside the session
     * @return the resolved path
     * @throws IOException if the parent directories cannot be created
     */
    public static Path getSessionFile(WhatsAppClientType clientType, Path baseDirectory, String uuid, String fileName) throws IOException {
        return getSessionDirectory(clientType, baseDirectory, uuid)
                .resolve(fileName);
    }

    /**
     * Returns the session directory with the most recent
     * {@code lastModifiedTime} under the home directory for {@code clientType},
     * or an empty {@link Optional} when the home directory is empty.
     *
     * <p>Feeding the returned path's filename back into
     * {@link #getSessionDirectory(WhatsAppClientType, Path, String)} reattaches
     * to the same store, which implements auto-resume of the last session
     * opened on this host.
     *
     * @implNote
     * This implementation walks the home directory one level deep via
     * {@link Files#walk(Path, int, java.nio.file.FileVisitOption...)} with
     * {@code maxDepth=0} plus {@code skip(1)} to omit the root itself. Entries
     * whose {@code lastModifiedTime} cannot be probed lose ties to entries whose
     * timestamp is readable; ties between two unreadable entries resolve to the
     * first scanned.
     *
     * @param clientType    the client type that owns the home directory layer
     *                      of the path
     * @param baseDirectory the base storage directory
     * @return the most recently modified session directory, or empty when none
     *         exist
     * @throws IOException if the home directory cannot be walked
     */
    @SuppressWarnings({"ConstantValue"})
    public static Optional<Path> getLatestSessionDirectory(WhatsAppClientType clientType, Path baseDirectory) throws IOException {
        var sessionsDirectory = getHomeDirectory(clientType, baseDirectory);
        try(var walker = Files.walk(sessionsDirectory, 0).skip(1)) {
            return walker.reduce((first, second) -> {
                var firstTimestamp = getLastModifiedTime(first);
                var secondTimestamp = getLastModifiedTime(second);
                if(firstTimestamp.isEmpty() && secondTimestamp.isEmpty()) {
                    return first;
                } else if(firstTimestamp.isPresent() && secondTimestamp.isEmpty()) {
                    return first;
                } else if(firstTimestamp.isEmpty() && secondTimestamp.isPresent()) {
                    return second;
                } else {
                    return firstTimestamp.get().compareTo(secondTimestamp.get()) >= 0
                            ? first
                            : second;
                }
            });
        }
    }

    /**
     * Returns the last modified time of {@code first}, swallowing
     * {@link IOException} as an empty {@link Optional}.
     *
     * <p>Keeps the directory walk in
     * {@link #getLatestSessionDirectory(WhatsAppClientType, Path)} total even
     * when individual entries cannot be stat'ed (transient races, missing
     * permissions).
     *
     * @param first the path to probe
     * @return the last modified time, or empty when stat fails
     */
    private static Optional<FileTime> getLastModifiedTime(Path first) {
        try {
            var result = Files.getLastModifiedTime(first);
            return Optional.of(result);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves the session directory identified by {@code path} under the home
     * directory for {@code clientType}, creating it if necessary.
     *
     * <p>Used directly when the session identifier is already known (typical for
     * resuming or for tests); per-file resolution goes through
     * {@link #getSessionFile(WhatsAppClientType, Path, String, String)} instead.
     *
     * @param clientType    the client type that owns the home directory layer
     *                      of the path
     * @param baseDirectory the base storage directory
     * @param path          the session identifier
     * @return the resolved session directory, guaranteed to exist
     * @throws IOException if the directory cannot be created
     */
    public static Path getSessionDirectory(WhatsAppClientType clientType, Path baseDirectory, String path) throws IOException {
        var result = getHomeDirectory(clientType, baseDirectory)
                .resolve(path);
        Files.createDirectories(result);
        return result;
    }

    /**
     * Resolves the home directory for {@code type} under {@code baseDirectory},
     * creating it if necessary.
     *
     * <p>The home directory is the parent location for session enumeration and
     * session-creation calls of a given client type. The two client types map
     * to fixed segment names ({@code "web"} and {@code "mobile"}) so the same
     * base directory may host both worlds side by side without collisions.
     *
     * @implNote
     * This implementation hard-codes the segment names rather than deriving them
     * from {@link WhatsAppClientType#name()} so that the layout stays stable
     * across enum renames.
     *
     * @param type          the client type
     * @param baseDirectory the base storage directory
     * @return the resolved home directory, guaranteed to exist
     * @throws IOException if the directory cannot be created
     */
    public static Path getHomeDirectory(WhatsAppClientType type, Path baseDirectory) throws IOException {
        var id = switch (type) {
            case WEB -> "web";
            case MOBILE -> "mobile";
        };
        var result = baseDirectory.resolve(id);
        Files.createDirectories(result);
        return result;
    }

    /**
     * Recursively deletes {@code path} and every file and directory underneath
     * it.
     *
     * <p>Wipes a session after a logout or frees disk space for a corrupted
     * store. Returns silently when {@code path} does not exist so callers do not
     * have to pre-check.
     *
     * @implNote
     * This implementation uses
     * {@link Files#walkFileTree(Path, java.nio.file.FileVisitor)} with a
     * {@link SimpleFileVisitor} so each child is deleted in post-order, allowing
     * the parent {@link Files#delete(Path)} call to succeed once the directory
     * is empty.
     *
     * @param path the path to delete
     * @throws IOException if any filesystem operation fails
     */
    public static void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
