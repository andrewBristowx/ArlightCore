package com.arlight.core.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Predicate;

/** Escritura durable de archivos pequeños de estado con respaldo recuperable. */
public final class AtomicFileStore {

    public enum Recovery {
        CURRENT,
        TEMP_PROMOTED,
        BACKUP_RESTORED,
        EMPTY
    }

    private AtomicFileStore() {
    }

    public static void write(Path target, String content, boolean keepBackup) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        Path parent = target.toAbsolutePath().getParent();
        if (parent == null) throw new IOException("El archivo no tiene carpeta padre: " + target);
        Files.createDirectories(parent);

        Path temporary = temporary(target);
        Files.writeString(temporary, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }

        if (keepBackup && Files.isRegularFile(target)) {
            Files.copy(target, backup(target), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        }
        promote(temporary, target);
    }

    public static Recovery recover(Path target, Predicate<Path> validator) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(validator, "validator");
        Path temporary = temporary(target);
        Path backup = backup(target);

        if (isValid(target, validator)) {
            Files.deleteIfExists(temporary);
            return Recovery.CURRENT;
        }
        if (isValid(temporary, validator)) {
            preserveCorruptTarget(target);
            promote(temporary, target);
            return Recovery.TEMP_PROMOTED;
        }
        if (isValid(backup, validator)) {
            preserveCorruptTarget(target);
            write(target, Files.readString(backup, StandardCharsets.UTF_8), false);
            return Recovery.BACKUP_RESTORED;
        }
        if (!Files.exists(target) && !Files.exists(temporary) && !Files.exists(backup)) {
            return Recovery.EMPTY;
        }
        throw new IOException("No existe una copia válida para recuperar " + target.getFileName());
    }

    public static Path temporary(Path target) {
        return target.resolveSibling(target.getFileName() + ".tmp");
    }

    public static Path backup(Path target) {
        return target.resolveSibling(target.getFileName() + ".bak");
    }

    private static boolean isValid(Path path, Predicate<Path> validator) {
        return Files.isRegularFile(path) && validator.test(path);
    }

    private static void preserveCorruptTarget(Path target) throws IOException {
        if (!Files.exists(target)) return;
        Files.move(target, target.resolveSibling(target.getFileName() + ".corrupt"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static void promote(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
