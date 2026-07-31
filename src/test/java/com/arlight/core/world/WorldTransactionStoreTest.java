package com.arlight.core.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldTransactionStoreTest {
    @TempDir Path directory;

    @Test
    void persistsEveryRecoveryFieldAtomically() throws Exception {
        WorldTransactionStore store = new WorldTransactionStore(directory);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        WorldTransaction transaction = new WorldTransaction(id, "bingo", "arena", "arena_next",
                id + "-arena", WorldTransactionStage.BACKED_UP, now, now, "");
        store.save(transaction);

        WorldTransaction loaded = store.read(id);
        assertEquals(transaction, loaded);
        assertEquals(1, store.loadAll().size());
    }
}
