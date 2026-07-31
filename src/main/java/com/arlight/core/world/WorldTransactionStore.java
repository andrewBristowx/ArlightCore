package com.arlight.core.world;

import com.arlight.core.storage.AtomicFileStore;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public final class WorldTransactionStore {
    private final Path directory;

    public WorldTransactionStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public synchronized void save(WorldTransaction transaction) throws IOException {
        Files.createDirectories(directory);
        Properties values = new Properties();
        values.setProperty("id", transaction.id().toString());
        values.setProperty("owner", transaction.owner());
        values.setProperty("activeWorld", transaction.activeWorld());
        values.setProperty("preparedWorld", transaction.preparedWorld());
        values.setProperty("backupFolder", transaction.backupFolder());
        values.setProperty("stage", transaction.stage().name());
        values.setProperty("createdAt", transaction.createdAt().toString());
        values.setProperty("updatedAt", transaction.updatedAt().toString());
        values.setProperty("error", transaction.error());
        StringWriter writer = new StringWriter();
        values.store(writer, "ArlightCore world transaction");
        AtomicFileStore.write(file(transaction.id()), writer.toString(), true);
    }

    public synchronized List<WorldTransaction> loadAll() throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        List<WorldTransaction> result = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            for (Path path : stream.filter(candidate -> candidate.getFileName().toString().endsWith(".properties"))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                result.add(read(path));
            }
        }
        return List.copyOf(result);
    }

    public synchronized WorldTransaction read(UUID id) throws IOException {
        return read(file(id));
    }

    private WorldTransaction read(Path path) throws IOException {
        Properties values = new Properties();
        values.load(new StringReader(Files.readString(path)));
        return new WorldTransaction(
                UUID.fromString(required(values, "id")),
                required(values, "owner"),
                required(values, "activeWorld"),
                required(values, "preparedWorld"),
                required(values, "backupFolder"),
                WorldTransactionStage.valueOf(required(values, "stage")),
                Instant.parse(required(values, "createdAt")),
                Instant.parse(required(values, "updatedAt")),
                values.getProperty("error", ""));
    }

    private Path file(UUID id) {
        return directory.resolve(id + ".properties");
    }

    private static String required(Properties values, String key) throws IOException {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Falta " + key + " en el diario");
        return value;
    }
}
