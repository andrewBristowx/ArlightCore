package com.arlight.core.session;

import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.function.BooleanSupplier;

/**
 * Mantiene una única sesión de minijuego por jugador y conserva su inventario
 * original. Los datos se guardan en sessions.yml para sobrevivir reinicios.
 */
public class MinigameSessionManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final BooleanSupplier externalInventoryManagement;

    public MinigameSessionManager(JavaPlugin plugin) {
        this(plugin, () -> false);
    }

    public MinigameSessionManager(JavaPlugin plugin, BooleanSupplier externalInventoryManagement) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sessions.yml");
        this.externalInventoryManagement = externalInventoryManagement == null
                ? () -> false : externalInventoryManagement;
    }

    public boolean begin(Player player, String minigameId) {
        if (player == null || minigameId == null || minigameId.isBlank()) return false;
        Session existing = sessions.get(player.getUniqueId());
        if (existing != null) return existing.minigameId().equalsIgnoreCase(minigameId);

        boolean external = externalInventoryManagement.getAsBoolean();
        sessions.put(player.getUniqueId(), new Session(
                minigameId.toLowerCase(),
                external ? new ItemStack[0] : cloneItems(player.getInventory().getStorageContents()),
                external ? new ItemStack[0] : cloneItems(player.getInventory().getArmorContents()),
                external ? null : cloneItem(player.getInventory().getItemInOffHand()),
                external ? 0 : player.getInventory().getHeldItemSlot(),
                false,
                false,
                external
        ));
        save();
        return true;
    }

    public boolean end(Player player, boolean restoreInventory) {
        if (player == null) return false;
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return false;

        if (restoreInventory && !session.externalInventory()) {
            player.getInventory().clear();
            player.getInventory().setStorageContents(cloneItems(session.storage()));
            player.getInventory().setArmorContents(cloneItems(session.armor()));
            player.getInventory().setItemInOffHand(cloneItem(session.offHand()));
            player.getInventory().setHeldItemSlot(session.heldSlot());
            player.updateInventory();
        }
        save();
        return true;
    }

    /** Conserva la sesión para recuperarla cuando el jugador vuelva a conectarse. */
    public boolean markPendingRestore(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        if (session == null) return false;
        if (session.pendingRestore()) return false;
        sessions.put(uuid, new Session(session.minigameId(), session.storage(), session.armor(),
                session.offHand(), session.heldSlot(), session.started(), true, session.externalInventory()));
        save();
        return true;
    }

    /** Restaura y elimina una sesión pendiente. Solo debe llamarse con el jugador conectado. */
    public boolean restorePending(Player player) {
        if (player == null || !player.isOnline()) return false;
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.pendingRestore()) return false;
        return end(player, true);
    }

    public boolean isPendingRestore(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        return session != null && session.pendingRestore();
    }

    public boolean usesExternalInventory(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        return session != null && session.externalInventory();
    }

    public boolean hasSession(UUID uuid) {
        return uuid != null && sessions.containsKey(uuid);
    }

    /** Marca el inicio real de la partida. Devuelve true solamente la primera vez. */
    public boolean markStarted(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        if (session == null || session.started()) return false;
        sessions.put(uuid, new Session(session.minigameId(), session.storage(), session.armor(),
                session.offHand(), session.heldSlot(), true, session.pendingRestore(), session.externalInventory()));
        save();
        return true;
    }

    public boolean hasStarted(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        return session != null && session.started();
    }

    public String getMinigameId(UUID uuid) {
        Session session = uuid == null ? null : sessions.get(uuid);
        return session == null ? null : session.minigameId();
    }

    public int size() {
        return sessions.size();
    }

    public int pendingCount() {
        return (int) sessions.values().stream().filter(Session::pendingRestore).count();
    }

    /** Fuerza la restauración únicamente cuando existe una recuperación pendiente. */
    public boolean forceRecover(Player player) {
        return restorePending(player);
    }

    public void load() {
        sessions.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("sessions");
        if (section == null) return;

        for (String rawUuid : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                String base = "sessions." + rawUuid;
                String minigame = yaml.getString(base + ".minigame");
                if (minigame == null || minigame.isBlank()) continue;
                // Toda sesión encontrada al arrancar procede de una partida interrumpida.
                // Se fuerza su recuperación al próximo ingreso del jugador.
                sessions.put(uuid, new Session(
                        minigame,
                        readItems(yaml.getList(base + ".storage")),
                        readItems(yaml.getList(base + ".armor")),
                        yaml.getItemStack(base + ".offhand"),
                        yaml.getInt(base + ".held-slot", 0),
                        yaml.getBoolean(base + ".started", false),
                        true,
                        yaml.getBoolean(base + ".external-inventory", false)
                ));
            } catch (IllegalArgumentException error) {
                plugin.getLogger().warning("Sesión ignorada por UUID inválido: " + rawUuid);
            }
        }
    }

    public void save() {
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
            yaml.set(base + ".status", session.pendingRestore() ? "DISQUALIFIED" : "ACTIVE");
            yaml.set(base + ".restore-on-join", session.pendingRestore());
            yaml.set(base + ".external-inventory", session.externalInventory());
        }
        try {
            yaml.save(file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar sessions.yml", error);
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

    private record Session(
            String minigameId,
            ItemStack[] storage,
            ItemStack[] armor,
            ItemStack offHand,
            int heldSlot,
            boolean started,
            boolean pendingRestore,
            boolean externalInventory
    ) {
    }
}
