package com.arlight.core.gui;

import com.arlight.core.api.MinigameProvider;
import com.arlight.core.api.MinigameStatus;
import com.arlight.core.registry.MinigameRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class SelectorGUI {

    public static final String TITLE = ChatColor.AQUA + "Minijuegos";
    private static final String ID_KEY_NAME = "core-minigame-id";

    public static NamespacedKey idKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, ID_KEY_NAME);
    }

    public static Inventory build(JavaPlugin plugin, MinigameRegistry registry) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        int slot = 0;
        for (MinigameProvider provider : registry.getAll()) {
            if (slot >= 27) break;
            inv.setItem(slot++, buildIcon(plugin, provider));
        }
        return inv;
    }

    private static ItemStack buildIcon(JavaPlugin plugin, MinigameProvider provider) {
        ItemStack icon = provider.getIcon() != null ? provider.getIcon().clone() : new ItemStack(Material.PAPER);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(provider.getDisplayName());
            List<String> lore = new ArrayList<>();
            boolean waiting = provider.getStatus() == MinigameStatus.WAITING;
            lore.add(waiting ? ChatColor.GREEN + "Esperando jugadores - click para unirte" : ChatColor.RED + "Partida en curso");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(idKey(plugin), PersistentDataType.STRING, provider.getId());
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /** Devuelve el id del minijuego asociado a este item de la GUI, o null si no tiene la marca. */
    public static String getProviderIdFromItem(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(idKey(plugin), PersistentDataType.STRING);
    }
}
