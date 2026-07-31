package com.arlight.core.gui;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.api.MinigameProvider;
import com.arlight.core.api.MinigameStatus;
import com.arlight.core.api.ArlightCoreIcons;
import com.arlight.core.registry.MinigameRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class SelectorGUI {

    public static final String TITLE = ChatColor.AQUA + "Minijuegos";
    public static final String DECORATED_TITLE = ArlightCoreIcons.SWORDS + TITLE;
    private static final String ID_KEY_NAME = "core-minigame-id";

    public static NamespacedKey idKey(ArlightCorePlugin plugin) {
        return new NamespacedKey(plugin, ID_KEY_NAME);
    }

    public static Inventory build(ArlightCorePlugin plugin, MinigameRegistry registry) {
        Inventory inv = Bukkit.createInventory(null, 27,
                plugin.getConfig().getBoolean("decorations.enabled", true)
                        ? DECORATED_TITLE : TITLE);

        int slot = 0;
        for (MinigameProvider provider : registry.getAll()) {
            if (slot >= 27) break;
            inv.setItem(slot++, buildIcon(plugin, provider));
        }
        return inv;
    }

    private static ItemStack buildIcon(ArlightCorePlugin plugin, MinigameProvider provider) {
        ItemStack icon = provider.getIcon() != null ? provider.getIcon().clone() : new ItemStack(Material.PAPER);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(provider.getDisplayName());
            List<String> lore = new ArrayList<>();
            MinigameStatus status = plugin.isMinigameEnabled(provider.getId())
                    ? provider.getStatus() : MinigameStatus.DISABLED;
            lore.add(switch (status) {
                case AVAILABLE -> icon(plugin, ArlightCoreIcons.CHECK) + ChatColor.GREEN
                        + "Disponible - click para unirte";
                case WAITING -> icon(plugin, ArlightCoreIcons.CLOCK) + ChatColor.GREEN
                        + "Esperando jugadores - click para unirte";
                case IN_PROGRESS -> icon(plugin, ArlightCoreIcons.SWORDS) + ChatColor.RED
                        + "Partida en curso";
                case RESTARTING -> icon(plugin, ArlightCoreIcons.WARNING) + ChatColor.YELLOW
                        + "Reiniciando arena...";
                case DISABLED -> icon(plugin, ArlightCoreIcons.CROSS) + ChatColor.DARK_RED
                        + "Desactivado temporalmente";
            });
            lore.add(icon(plugin, ArlightCoreIcons.PLAYERS) + ChatColor.GRAY + "Jugadores: "
                    + ChatColor.WHITE + provider.getCurrentPlayers()
                    + "/" + (provider.getMaxPlayers() <= 0 ? "?" : provider.getMaxPlayers()));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(idKey(plugin), PersistentDataType.STRING, provider.getId());
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private static String icon(ArlightCorePlugin plugin, String icon) {
        return plugin.getConfig().getBoolean("decorations.enabled", true) ? icon : "";
    }

    public static boolean isTitle(String title) {
        return TITLE.equals(title) || DECORATED_TITLE.equals(title);
    }

    /** Devuelve el id del minijuego asociado a este item de la GUI, o null si no tiene la marca. */
    public static String getProviderIdFromItem(ArlightCorePlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(idKey(plugin), PersistentDataType.STRING);
    }
}
