package com.arlight.core.reward;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Guarda que item se entrega como recompensa por cada nivel. El item se clona TAL CUAL
 * (con su Material real, cantidad, nombre, lore, encantamientos, NBT, etc.), asi que
 * funciona igual para items vanilla o de mods -- no hace falta interpretar nada, solo
 * clonar lo que el admin tenga en la mano.
 */
public class RewardManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<Integer, ItemStack> rewardsByLevel = new HashMap<>();

    public RewardManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rewards.yml");
    }

    public ItemStack getReward(int level) {
        ItemStack item = rewardsByLevel.get(level);
        return item == null ? null : item.clone();
    }

    public boolean hasReward(int level) {
        return rewardsByLevel.containsKey(level);
    }

    public void setReward(int level, ItemStack item) {
        rewardsByLevel.put(level, item.clone());
        save();
    }

    public void removeReward(int level) {
        rewardsByLevel.remove(level);
        save();
    }

    public Map<Integer, ItemStack> getAll() {
        return rewardsByLevel;
    }

    public void load() {
        rewardsByLevel.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        org.bukkit.configuration.ConfigurationSection section = yaml.getConfigurationSection("rewards");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                ItemStack item = section.getItemStack(key);
                if (item != null) {
                    rewardsByLevel.put(level, item);
                }
            } catch (NumberFormatException ignored) {
                // clave no numerica, se ignora
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<Integer, ItemStack> entry : rewardsByLevel.entrySet()) {
            yaml.set("rewards." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar rewards.yml", e);
        }
    }
}
