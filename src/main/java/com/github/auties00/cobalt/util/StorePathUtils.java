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

public final class StorePathUtils {
    private StorePathUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Path getSessionFile(WhatsAppClientType clientType, Path baseDirectory, String uuid, String fileName) throws IOException {
        return getSessionDirectory(clientType, baseDirectory, uuid)
                .resolve(fileName);
    }

    @SuppressWarnings({"ConstantValue"}) // I prefer the readability like this
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

    private static Optional<FileTime> getLastModifiedTime(Path first) {
        try {
            var result = Files.getLastModifiedTime(first);
            return Optional.of(result);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static Path getSessionDirectory(WhatsAppClientType clientType, Path baseDirectory, String path) throws IOException {
        var result = getHomeDirectory(clientType, baseDirectory)
                .resolve(path);
        Files.createDirectories(result);
        return result;
    }

    public static Path getHomeDirectory(WhatsAppClientType type, Path baseDirectory) throws IOException {
        var id = switch (type) {
            case WEB -> "web";
            case MOBILE -> "mobile";
        };
        var result = baseDirectory.resolve(id);
        Files.createDirectories(result);
        return result;
    }

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
