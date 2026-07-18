package com.arlight.core.items;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public class CoreItems {

    private static final String SELECTOR_KEY = "core-selector-item";
    private static final String REWARDS_KEY = "core-rewards-item";

    private CoreItems() {
    }

    public static NamespacedKey selectorKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, SELECTOR_KEY);
    }

    public static NamespacedKey rewardsKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, REWARDS_KEY);
    }

    public static ItemStack createSelectorItem(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Minijuegos");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click derecho para ver los minijuegos",
                    ChatColor.GRAY + "disponibles y unirte a uno."
            ));
            meta.getPersistentDataContainer().set(selectorKey(plugin), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createRewardsItem(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Nivel y Recompensas");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click derecho para ver tu nivel",
                    ChatColor.GRAY + "y reclamar recompensas."
            ));
            meta.getPersistentDataContainer().set(rewardsKey(plugin), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isSelectorItem(JavaPlugin plugin, ItemStack item) {
        return hasMarker(item, selectorKey(plugin));
    }

    public static boolean isRewardsItem(JavaPlugin plugin, ItemStack item) {
        return hasMarker(item, rewardsKey(plugin));
    }

    private static boolean hasMarker(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
