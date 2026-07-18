package com.arlight.core.xp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Guarda la XP de cada jugador y que niveles ya reclamo, en un archivo playerdata.yml.
 */
public class PlayerLevelManager {

    private final JavaPlugin plugin;
    private final File file;

    private int xpPerLevel = 30;

    private final Map<UUID, Integer> xpByPlayer = new HashMap<>();
    private final Map<UUID, Set<Integer>> claimedLevelsByPlayer = new HashMap<>();

    public PlayerLevelManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
    }

    public void setXpPerLevel(int xpPerLevel) {
        this.xpPerLevel = Math.max(1, xpPerLevel);
    }

    public int getXp(UUID uuid) {
        return xpByPlayer.getOrDefault(uuid, 0);
    }

    public int getLevel(UUID uuid) {
        return getXp(uuid) / xpPerLevel;
    }

    /** XP que falta para el proximo nivel. */
    public int getXpIntoCurrentLevel(UUID uuid) {
        return getXp(uuid) % xpPerLevel;
    }

    public int getXpPerLevel() {
        return xpPerLevel;
    }

    public void addXp(UUID uuid, int amount) {
        xpByPlayer.merge(uuid, amount, Integer::sum);
        save();
    }

    public boolean isClaimed(UUID uuid, int level) {
        Set<Integer> claimed = claimedLevelsByPlayer.get(uuid);
        return claimed != null && claimed.contains(level);
    }

    public void markClaimed(UUID uuid, int level) {
        claimedLevelsByPlayer.computeIfAbsent(uuid, k -> new HashSet<>()).add(level);
        save();
    }

    public void load() {
        xpByPlayer.clear();
        claimedLevelsByPlayer.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        org.bukkit.configuration.ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection == null) return;

        for (String key : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int xp = playersSection.getInt(key + ".xp", 0);
                List<Integer> claimed = playersSection.getIntegerList(key + ".claimed-levels");
                xpByPlayer.put(uuid, xp);
                claimedLevelsByPlayer.put(uuid, new HashSet<>(claimed));
            } catch (IllegalArgumentException ignored) {
                // uuid corrupto, se ignora esa entrada
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : xpByPlayer.entrySet()) {
            String base = "players." + entry.getKey();
            yaml.set(base + ".xp", entry.getValue());
            Set<Integer> claimed = claimedLevelsByPlayer.get(entry.getKey());
            yaml.set(base + ".claimed-levels", claimed == null ? List.of() : new java.util.ArrayList<>(claimed));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar playerdata.yml", e);
        }
    }
}
