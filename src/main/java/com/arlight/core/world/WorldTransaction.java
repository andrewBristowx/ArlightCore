package com.arlight.core.world;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorldTransaction(
        UUID id,
        String owner,
        String activeWorld,
        String preparedWorld,
        String backupFolder,
        WorldTransactionStage stage,
        Instant createdAt,
        Instant updatedAt,
        String error
) {
    public WorldTransaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(activeWorld, "activeWorld");
        Objects.requireNonNull(preparedWorld, "preparedWorld");
        Objects.requireNonNull(backupFolder, "backupFolder");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        error = error == null ? "" : error;
    }

    public WorldTransaction advance(WorldTransactionStage next) {
        return new WorldTransaction(id, owner, activeWorld, preparedWorld, backupFolder,
                next, createdAt, Instant.now(), "");
    }

    public WorldTransaction fail(String message) {
        return new WorldTransaction(id, owner, activeWorld, preparedWorld, backupFolder,
                WorldTransactionStage.FAILED, createdAt, Instant.now(),
                message == null ? "Error desconocido" : message);
    }
}
