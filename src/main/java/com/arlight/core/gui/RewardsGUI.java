package com.arlight.core.gui;

import com.arlight.core.reward.RewardManager;
import com.arlight.core.xp.PlayerLevelManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class RewardsGUI {

    public static final String TITLE = ChatColor.LIGHT_PURPLE + "Nivel y Recompensas";
    private static final String LEVEL_KEY_NAME = "core-reward-level";

    public static NamespacedKey levelKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, LEVEL_KEY_NAME);
    }

    public static Inventory build(JavaPlugin plugin, Player player, PlayerLevelManager levelManager, RewardManager rewardManager) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        int level = levelManager.getLevel(player.getUniqueId());
        int xp = levelManager.getXp(player.getUniqueId());
        int xpPerLevel = levelManager.getXpPerLevel();
        int xpIntoLevel = levelManager.getXpIntoCurrentLevel(player.getUniqueId());

        ItemStack info = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Nivel del minipase " + level);
            infoMeta.setLore(List.of(
                    ChatColor.GRAY + "XP total: " + ChatColor.WHITE + xp,
                    ChatColor.GRAY + "Progreso al siguiente nivel: " + ChatColor.WHITE + xpIntoLevel + "/" + xpPerLevel
            ));
            info.setItemMeta(infoMeta);
        }
        inv.setItem(4, info);

        int maxLevelToShow = Math.max(level + 3, 9);
        int slot = 9;
        for (int lvl = 1; lvl <= maxLevelToShow && slot < 54; lvl++, slot++) {
            inv.setItem(slot, buildLevelIcon(plugin, player, lvl, level, levelManager, rewardManager));
        }
        return inv;
    }

    private static ItemStack buildLevelIcon(JavaPlugin plugin, Player player, int lvl, int currentLevel,
                                             PlayerLevelManager levelManager, RewardManager rewardManager) {
        ItemStack reward = rewardManager.getReward(lvl);
        boolean reached = lvl <= currentLevel;
        boolean claimed = levelManager.isClaimed(player.getUniqueId(), lvl);

        ItemStack icon = reward != null ? reward.clone() : new ItemStack(Material.BARRIER);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((reached ? ChatColor.GREEN : ChatColor.GRAY) + "Nivel " + lvl);

            List<String> lore = new ArrayList<>();
            if (reward == null) {
                lore.add(ChatColor.DARK_GRAY + "(sin recompensa configurada)");
            } else if (!reached) {
                lore.add(ChatColor.RED + "Todavia no alcanzaste este nivel");
            } else if (claimed) {
                lore.add(ChatColor.GRAY + "Ya reclamado");
            } else {
                lore.add(ChatColor.YELLOW + "Click para reclamar!");
            }
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(levelKey(plugin), PersistentDataType.INTEGER, lvl);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /** Devuelve el nivel asociado a un item de esta GUI, o null si no tiene la marca. */
    public static Integer getLevelFromItem(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(levelKey(plugin), PersistentDataType.INTEGER);
    }
}
