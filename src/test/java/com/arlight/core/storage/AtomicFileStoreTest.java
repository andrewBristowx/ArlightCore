package com.arlight.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFileStoreTest {

    @TempDir
    Path directory;

    @Test
    void keepsPreviousGoodFileAsBackup() throws Exception {
        Path target = directory.resolve("sessions.yml");
        AtomicFileStore.write(target, "version: one\n", true);
        AtomicFileStore.write(target, "version: two\n", true);

        assertEquals("version: two\n", Files.readString(target));
        assertEquals("version: one\n", Files.readString(AtomicFileStore.backup(target)));
    }

    @Test
    void restoresBackupWhenCurrentFileIsInvalid() throws Exception {
        Path target = directory.resolve("sessions.yml");
        AtomicFileStore.write(target, "valid\n", true);
        AtomicFileStore.write(target, "new-valid\n", true);
        Files.writeString(target, "corrupt\n");

        AtomicFileStore.Recovery recovery = AtomicFileStore.recover(target,
                path -> read(path).equals("valid\n"));

        assertEquals(AtomicFileStore.Recovery.BACKUP_RESTORED, recovery);
        assertEquals("valid\n", Files.readString(target));
        assertTrue(Files.exists(target.resolveSibling("sessions.yml.corrupt")));
    }

    @Test
    void promotesCompleteTemporaryFileAfterInterruptedMove() throws Exception {
        Path target = directory.resolve("sessions.yml");
        Files.writeString(target, "corrupt\n");
        Files.writeString(AtomicFileStore.temporary(target), "complete\n");

        AtomicFileStore.Recovery recovery = AtomicFileStore.recover(target,
                path -> read(path).equals("complete\n"));

        assertEquals(AtomicFileStore.Recovery.TEMP_PROMOTED, recovery);
        assertEquals("complete\n", Files.readString(target));
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception ignored) {
            return "";
        }
    }
}
