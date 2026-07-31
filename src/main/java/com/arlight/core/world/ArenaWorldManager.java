package com.arlight.core.world;

import com.arlight.core.ArlightCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Traspaso transaccional de carpetas de mundo ya preparadas. El minijuego genera
 * y valida su mundo bajo un nombre temporal; Core se encarga únicamente del
 * cambio atómico, el respaldo y la recuperación.
 */
public final class ArenaWorldManager {
    private final ArlightCorePlugin plugin;
    private final Path worldRoot;
    private final Path backupRoot;
    private final WorldTransactionStore store;
    private volatile String lastError = "";

    public ArenaWorldManager(ArlightCorePlugin plugin) {
        this.plugin = plugin;
        this.worldRoot = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        this.backupRoot = plugin.getDataFolder().toPath().resolve("world-backups").toAbsolutePath().normalize();
        this.store = new WorldTransactionStore(plugin.getDataFolder().toPath().resolve("world-transactions"));
    }

    public WorldHandoffResult replace(String owner, String activeWorld, String preparedWorld) {
        if (!Bukkit.isPrimaryThread()) {
            return new WorldHandoffResult(false, null, WorldTransactionStage.FAILED,
                    "El traspaso debe iniciarse en el hilo principal del servidor.");
        }
        try {
            validateName(activeWorld);
            validateName(preparedWorld);
            if (activeWorld.equalsIgnoreCase(preparedWorld)) throw new IOException("Los mundos activo y preparado son iguales");
            Path active = worldPath(activeWorld);
            Path prepared = worldPath(preparedWorld);
            validatePrepared(prepared);
            ensureWorldCanUnload(activeWorld);
            if (Bukkit.getWorld(preparedWorld) != null) throw new IOException("El mundo preparado todavía está cargado");

            UUID id = UUID.randomUUID();
            String backupFolder = id + "-" + activeWorld;
            WorldTransaction tx = new WorldTransaction(id, cleanOwner(owner), activeWorld,
                    preparedWorld, backupFolder, WorldTransactionStage.PREPARING,
                    Instant.now(), Instant.now(), "");
            store.save(tx);
            tx = persist(tx.advance(WorldTransactionStage.VALIDATED));

            World loaded = Bukkit.getWorld(activeWorld);
            if (loaded != null && !Bukkit.unloadWorld(loaded, true)) {
                throw new IOException("Bukkit no pudo descargar el mundo activo");
            }

            Files.createDirectories(backupRoot);
            Path backup = backupPath(tx);
            if (Files.exists(active, LinkOption.NOFOLLOW_LINKS)) move(active, backup);
            tx = persist(tx.advance(WorldTransactionStage.BACKED_UP));

            move(prepared, active);
            tx = persist(tx.advance(WorldTransactionStage.SWITCHED));

            World replacement = Bukkit.createWorld(new WorldCreator(activeWorld));
            if (replacement == null) throw new IOException("Bukkit no pudo cargar el mundo nuevo");
            tx = persist(tx.advance(WorldTransactionStage.COMPLETE));
            lastError = "";
            return new WorldHandoffResult(true, id, tx.stage(),
                    "Mundo activado; el respaldo se conserva en " + backupFolder);
        } catch (Exception error) {
            lastError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            plugin.getLogger().log(Level.SEVERE, "Falló el traspaso seguro de mundo", error);
            return new WorldHandoffResult(false, null, WorldTransactionStage.FAILED, lastError);
        }
    }

    public synchronized List<WorldHandoffResult> recoverInterrupted() {
        List<WorldHandoffResult> results = new ArrayList<>();
        try {
            for (WorldTransaction tx : store.loadAll()) {
                if (tx.stage().terminal()) continue;
                results.add(recover(tx));
            }
        } catch (Exception error) {
            lastError = error.getMessage();
            plugin.getLogger().log(Level.SEVERE, "No se pudieron leer transacciones de mundos", error);
        }
        return List.copyOf(results);
    }

    private WorldHandoffResult recover(WorldTransaction tx) {
        if (!Bukkit.isPrimaryThread()) {
            return new WorldHandoffResult(false, tx.id(), tx.stage(), "La recuperación requiere el hilo principal");
        }
        Path active = worldPath(tx.activeWorld());
        Path prepared = worldPath(tx.preparedWorld());
        Path backup = backupPath(tx);
        try {
            if (tx.stage() == WorldTransactionStage.PREPARING || tx.stage() == WorldTransactionStage.VALIDATED) {
                WorldTransaction rolled = persist(tx.advance(WorldTransactionStage.ROLLED_BACK));
                return new WorldHandoffResult(true, tx.id(), rolled.stage(), "No se había cambiado ninguna carpeta");
            }
            if (tx.stage() == WorldTransactionStage.BACKED_UP) {
                if (Files.isDirectory(prepared, LinkOption.NOFOLLOW_LINKS)) {
                    move(prepared, active);
                    tx = persist(tx.advance(WorldTransactionStage.SWITCHED));
                } else {
                    rollbackFolders(active, backup);
                    WorldTransaction rolled = persist(tx.advance(WorldTransactionStage.ROLLED_BACK));
                    return new WorldHandoffResult(true, tx.id(), rolled.stage(), "Se restauró el respaldo");
                }
            }
            if (tx.stage() == WorldTransactionStage.SWITCHED) {
                World world = Bukkit.getWorld(tx.activeWorld());
                if (world == null) world = Bukkit.createWorld(new WorldCreator(tx.activeWorld()));
                if (world != null) {
                    WorldTransaction completed = persist(tx.advance(WorldTransactionStage.COMPLETE));
                    return new WorldHandoffResult(true, tx.id(), completed.stage(), "Se terminó de cargar el mundo nuevo");
                }
                rollbackFolders(active, backup);
                Bukkit.createWorld(new WorldCreator(tx.activeWorld()));
                WorldTransaction rolled = persist(tx.advance(WorldTransactionStage.ROLLED_BACK));
                return new WorldHandoffResult(true, tx.id(), rolled.stage(), "El mundo nuevo falló; se restauró el respaldo");
            }
            return new WorldHandoffResult(true, tx.id(), tx.stage(), "Sin acciones pendientes");
        } catch (Exception error) {
            lastError = error.getMessage();
            try { store.save(tx.fail(lastError)); } catch (Exception ignored) { }
            return new WorldHandoffResult(false, tx.id(), WorldTransactionStage.FAILED, lastError);
        }
    }

    public List<WorldTransaction> transactions() {
        try {
            return store.loadAll();
        } catch (IOException error) {
            lastError = error.getMessage();
            return List.of();
        }
    }

    public long pendingCount() {
        return transactions().stream().filter(tx -> !tx.stage().terminal()).count();
    }

    public String lastError() { return lastError; }

    private WorldTransaction persist(WorldTransaction tx) throws IOException {
        store.save(tx);
        return tx;
    }

    private void ensureWorldCanUnload(String name) throws IOException {
        World world = Bukkit.getWorld(name);
        if (world != null && !world.getPlayers().isEmpty()) {
            throw new IOException("Todavía hay " + world.getPlayers().size() + " jugador(es) en " + name);
        }
    }

    private void validatePrepared(Path prepared) throws IOException {
        if (!Files.isDirectory(prepared, LinkOption.NOFOLLOW_LINKS)) throw new IOException("No existe la carpeta preparada");
        if (Files.isSymbolicLink(prepared)) throw new IOException("La carpeta preparada no puede ser un enlace simbólico");
        if (!Files.isRegularFile(prepared.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("El mundo preparado no contiene level.dat");
        }
    }

    private Path worldPath(String name) {
        Path result = worldRoot.resolve(name).normalize();
        if (!result.getParent().equals(worldRoot)) throw new IllegalArgumentException("Ruta de mundo insegura");
        return result;
    }

    private Path backupPath(WorldTransaction tx) {
        Path result = backupRoot.resolve(tx.backupFolder()).normalize();
        if (!result.getParent().equals(backupRoot)) throw new IllegalArgumentException("Ruta de respaldo insegura");
        return result;
    }

    private static void validateName(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9._-]{1,80}") || name.equals(".") || name.equals("..")) {
            throw new IOException("Nombre de mundo inválido");
        }
    }

    private static String cleanOwner(String owner) {
        if (owner == null || owner.isBlank()) return "unknown";
        String cleaned = owner.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return cleaned.substring(0, Math.min(64, cleaned.length()));
    }

    private static void move(Path source, Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("El destino ya existe: " + target.getFileName());
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private static void rollbackFolders(Path active, Path backup) throws IOException {
        if (Files.exists(active, LinkOption.NOFOLLOW_LINKS)) {
            Path failed = active.resolveSibling(active.getFileName() + ".failed-" + System.currentTimeMillis());
            move(active, failed);
        }
        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) move(backup, active);
    }
}
