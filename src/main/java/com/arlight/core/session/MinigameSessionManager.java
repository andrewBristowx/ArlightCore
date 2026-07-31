package com.arlight.core.session;

import com.arlight.core.storage.AtomicFileStore;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/**
 * Mantiene una única sesión de minijuego por jugador y conserva su inventario.
 * Los cambios se persisten atómicamente antes de aplicar restauraciones.
 */
public class MinigameSessionManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Path journalFile;
    private final int maxJournalEntries;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final BooleanSupplier externalInventoryManagement;
    private String lastStorageEvent = "NOT_LOADED";
    private String lastStorageError;

    public MinigameSessionManager(JavaPlugin plugin) {
        this(plugin, () -> false);
    }

    public MinigameSessionManager(JavaPlugin plugin, BooleanSupplier externalInventoryManagement) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sessions.yml");
        this.journalFile = plugin.getDataFolder().toPath().resolve("sessions.journal");
        this.maxJournalEntries = Math.max(20, Math.min(2000,
                plugin.getConfig().getInt("sessions.journal-max-entries", 200)));
        this.externalInventoryManagement = externalInventoryManagement == null
                ? () -> false : externalInventoryManagement;
    }

    public synchronized boolean begin(Player player, String minigameId) {
        if (player == null || minigameId == null || minigameId.isBlank()) return false;
        UUID uuid = player.getUniqueId();
        Session existing = sessions.get(uuid);
        if (existing != null) return existing.minigameId().equalsIgnoreCase(minigameId);

        boolean external = externalInventoryManagement.getAsBoolean();
        Session created = new Session(
                minigameId.toLowerCase(),
                external ? new ItemStack[0] : cloneItems(player.getInventory().getStorageContents()),
                external ? new ItemStack[0] : cloneItems(player.getInventory().getArmorContents()),
                external ? null : cloneItem(player.getInventory().getItemInOffHand()),
                external ? 0 : player.getInventory().getHeldItemSlot(),
                false,
                false,
                false,
                external
        );
        sessions.put(uuid, created);
        if (!save()) {
            sessions.remove(uuid);
            return false;
        }
        journal("BEGIN", uuid, created.minigameId());
        return true;
    }

    public synchronized boolean end(Player player, boolean restoreInventory) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        Session session = sessions.get(uuid);
        if (session == null) return false;

        if (restoreInventory && !session.externalInventory()) {
            // La restauración es una transacción en dos fases. Primero se persiste
            // RESTORING; aplicar el mismo snapshot otra vez es idempotente porque
            // el inventario se limpia por completo antes de copiarlo.
            Session restoring = new Session(session.minigameId(), session.storage(),
                    session.armor(), session.offHand(), session.heldSlot(), session.started(),
                    true, true, session.externalInventory());
            sessions.put(uuid, restoring);
            if (!save()) {
                sessions.put(uuid, session);
                return false;
            }
            try {
                player.getInventory().clear();
                player.getInventory().setStorageContents(cloneItems(session.storage()));
                player.getInventory().setArmorContents(cloneItems(session.armor()));
                player.getInventory().setItemInOffHand(cloneItem(session.offHand()));
                player.getInventory().setHeldItemSlot(session.heldSlot());
                player.updateInventory();
                player.saveData();
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.SEVERE,
                        "La sesión se cerró, pero falló la restauración de " + uuid, error);
                journal("RESTORE_FAILED", uuid, session.minigameId());
                return false;
            }
        }

        Session durableBeforeRemoval = sessions.get(uuid);
        sessions.remove(uuid);
        if (!save()) {
            sessions.put(uuid, durableBeforeRemoval == null ? session : durableBeforeRemoval);
            return false;
        }
        journal(restoreInventory ? "END_RESTORED" : "END", uuid, session.minigameId());
        return true;
    }

    /** Conserva la sesión para recuperarla cuando el jugador vuelva a conectarse. */
    public synchronized boolean markPendingRestore(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        if (session == null || session.pendingRestore()) return false;
        Session updated = new Session(session.minigameId(), session.storage(), session.armor(),
                session.offHand(), session.heldSlot(), session.started(), true,
                session.restoring(), session.externalInventory());
        sessions.put(uuid, updated);
        if (!save()) {
            sessions.put(uuid, session);
            return false;
        }
        journal("MARK_PENDING", uuid, session.minigameId());
        return true;
    }

    /** Restaura y elimina una sesión pendiente. Solo debe llamarse con el jugador conectado. */
    public synchronized boolean restorePending(Player player) {
        if (player == null || !player.isOnline()) return false;
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.pendingRestore()) return false;
        boolean restored = end(player, true);
        if (restored) journal("RECOVER", player.getUniqueId(), session.minigameId());
        return restored;
    }

    public synchronized boolean isPendingRestore(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        return session != null && session.pendingRestore();
    }

    public synchronized boolean usesExternalInventory(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        return session != null && session.externalInventory();
    }

    public synchronized boolean hasSession(UUID uuid) {
        return uuid != null && sessions.containsKey(uuid);
    }

    /** Marca el inicio real de la partida. Devuelve true solamente la primera vez. */
    public synchronized boolean markStarted(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        if (session == null || session.started()) return false;
        Session updated = new Session(session.minigameId(), session.storage(), session.armor(),
                session.offHand(), session.heldSlot(), true, session.pendingRestore(),
                session.restoring(), session.externalInventory());
        sessions.put(uuid, updated);
        if (!save()) {
            sessions.put(uuid, session);
            return false;
        }
        journal("MARK_STARTED", uuid, session.minigameId());
        return true;
    }

    public synchronized boolean hasStarted(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        return session != null && session.started();
    }

    public synchronized String getMinigameId(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        return session == null ? null : session.minigameId();
    }

    public synchronized int size() {
        return sessions.size();
    }

    public synchronized int pendingCount() {
        return (int) sessions.values().stream().filter(Session::pendingRestore).count();
    }

    public synchronized int restoringCount() {
        return (int) sessions.values().stream().filter(Session::restoring).count();
    }

    /** Fuerza la restauración únicamente cuando existe una recuperación pendiente. */
    public boolean forceRecover(Player player) {
        return restorePending(player);
    }

    public synchronized void load() {
        sessions.clear();
        try {
            AtomicFileStore.Recovery recovery = AtomicFileStore.recover(
                    file.toPath(), this::isValidYaml);
            lastStorageEvent = recovery.name();
            lastStorageError = null;
            if (recovery == AtomicFileStore.Recovery.EMPTY) return;
        } catch (IOException error) {
            lastStorageEvent = "RECOVERY_FAILED";
            lastStorageError = error.getMessage();
            plugin.getLogger().log(Level.SEVERE,
                    "No se pudo recuperar sessions.yml; no se cargaron sesiones", error);
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException error) {
            lastStorageEvent = "LOAD_FAILED";
            lastStorageError = error.getMessage();
            plugin.getLogger().log(Level.SEVERE, "No se pudo cargar sessions.yml", error);
            return;
        }
        var section = yaml.getConfigurationSection("sessions");
        if (section == null) return;

        for (String rawUuid : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                String base = "sessions." + rawUuid;
                String minigame = yaml.getString(base + ".minigame");
                if (minigame == null || minigame.isBlank()) continue;
                sessions.put(uuid, new Session(
                        minigame,
                        readItems(yaml.getList(base + ".storage")),
                        readItems(yaml.getList(base + ".armor")),
                        yaml.getItemStack(base + ".offhand"),
                        yaml.getInt(base + ".held-slot", 0),
                        yaml.getBoolean(base + ".started", false),
                        true,
                        yaml.getBoolean(base + ".restoring", false),
                        yaml.getBoolean(base + ".external-inventory", false)
                ));
            } catch (IllegalArgumentException error) {
                plugin.getLogger().warning("Sesión ignorada por UUID inválido: " + rawUuid);
            }
        }
        journal("LOAD_INTERRUPTED_SESSIONS", null, "count=" + sessions.size());
    }

    public synchronized boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            String base = "sessions." + entry.getKey();
            Session session = entry.getValue();
            yaml.set(base + ".minigame", session.minigameId());
            yaml.set(base + ".storage", Arrays.asList(session.storage()));
            yaml.set(base + ".armor", Arrays.asList(session.armor()));
            yaml.set(base + ".offhand", session.offHand());
            yaml.set(base + ".held-slot", session.heldSlot());
            yaml.set(base + ".started", session.started());
            yaml.set(base + ".status", session.restoring() ? "RESTORING"
                    : session.pendingRestore() ? "DISQUALIFIED" : "ACTIVE");
            yaml.set(base + ".restore-on-join", session.pendingRestore());
            yaml.set(base + ".restoring", session.restoring());
            yaml.set(base + ".external-inventory", session.externalInventory());
        }
        try {
            AtomicFileStore.write(file.toPath(), yaml.saveToString(), true);
            lastStorageEvent = "ATOMIC_SAVE";
            lastStorageError = null;
            return true;
        } catch (IOException error) {
            lastStorageEvent = "SAVE_FAILED";
            lastStorageError = error.getMessage();
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar sessions.yml", error);
            return false;
        }
    }

    public synchronized StorageHealth storageHealth() {
        Path target = file.toPath();
        return new StorageHealth(
                lastStorageError == null,
                lastStorageEvent,
                lastStorageError,
                Files.isRegularFile(target),
                Files.isRegularFile(AtomicFileStore.temporary(target)),
                Files.isRegularFile(AtomicFileStore.backup(target)),
                Files.isRegularFile(journalFile)
        );
    }

    private boolean isValidYaml(Path path) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(path.toFile());
            return true;
        } catch (IOException | InvalidConfigurationException error) {
            return false;
        }
    }

    private void journal(String action, UUID uuid, String detail) {
        try {
            List<String> entries = Files.isRegularFile(journalFile)
                    ? new ArrayList<>(Files.readAllLines(journalFile, StandardCharsets.UTF_8))
                    : new ArrayList<>();
            String actor = uuid == null ? "-" : uuid.toString();
            entries.add(Instant.now() + " | " + action + " | " + actor + " | " + detail);
            if (entries.size() > maxJournalEntries) {
                entries = new ArrayList<>(entries.subList(
                        entries.size() - maxJournalEntries, entries.size()));
            }
            AtomicFileStore.write(journalFile, String.join(System.lineSeparator(), entries)
                    + System.lineSeparator(), false);
        } catch (IOException error) {
            lastStorageError = "No se pudo escribir sessions.journal: " + error.getMessage();
            plugin.getLogger().warning(lastStorageError);
        }
    }

    private ItemStack[] readItems(List<?> raw) {
        if (raw == null) return new ItemStack[0];
        List<ItemStack> items = new ArrayList<>();
        for (Object value : raw) items.add(value instanceof ItemStack item ? item : null);
        return items.toArray(new ItemStack[0]);
    }

    private ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = cloneItem(source[i]);
        return copy;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    public record StorageHealth(
            boolean healthy,
            String lastEvent,
            String error,
            boolean currentFile,
            boolean temporaryFile,
            boolean backupFile,
            boolean journalFile
    ) {
    }

    private record Session(
            String minigameId,
            ItemStack[] storage,
            ItemStack[] armor,
            ItemStack offHand,
            int heldSlot,
            boolean started,
            boolean pendingRestore,
            boolean restoring,
            boolean externalInventory
    ) {
    }
}
