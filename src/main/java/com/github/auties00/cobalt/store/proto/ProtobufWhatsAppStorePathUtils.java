package com.github.auties00.cobalt.store.proto;

import com.github.auties00.cobalt.client.WhatsAppClientType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

final class ProtobufWhatsAppStorePathUtils {
    static Path getSessionFile(WhatsAppClientType clientType, Path baseDirectory, String uuid, String fileName) {
        try {
            var result = getSessionDirectory(clientType, baseDirectory, uuid).resolve(fileName);
            Files.createDirectories(result.getParent());
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot create directory", exception);
        }
    }

    static Path getSessionDirectory(WhatsAppClientType clientType, Path baseDirectory, String path) {
        try {
            var result = getHome(clientType, baseDirectory).resolve(path);
            Files.createDirectories(result.getParent());
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path getHome(WhatsAppClientType type, Path baseDirectory) {
        return baseDirectory.resolve(type == WhatsAppClientType.MOBILE ? "mobile" : "web");
    }

    static void deleteRecursively(Path path) throws IOException {
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
