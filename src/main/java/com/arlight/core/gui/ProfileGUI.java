package com.arlight.core.gui;

import com.arlight.core.stats.PlayerStatsManager;
import com.arlight.core.xp.PlayerLevelManager;
import com.arlight.core.reward.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ProfileGUI {
    public static final String TITLE = ChatColor.AQUA + "Mi perfil y estadísticas";

    private ProfileGUI() { }

    public static Inventory build(Player player, PlayerLevelManager levels, PlayerStatsManager stats,
                                  RewardManager rewards) {
        Inventory inventory = Bukkit.createInventory(null, 45, TITLE);
        inventory.setItem(4, playerHead(player, levels, stats));
        inventory.setItem(20, passProgress(player, levels));
        inventory.setItem(22, globalStats(player, stats));
        inventory.setItem(24, pendingRewards(player, levels, rewards));

        List<String> games = new ArrayList<>(stats.getMinigames(player.getUniqueId()));
        games.sort(Comparator.naturalOrder());
        int slot = 28;
        for (String game : games) {
            if (slot >= 35) break;
            inventory.setItem(slot++, gameStats(player, game, stats));
        }
        return inventory;
    }

    private static ItemStack pendingRewards(Player player, PlayerLevelManager levels, RewardManager rewards) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int pending = levels.countUnclaimedRewards(player.getUniqueId(), rewards.getAll().keySet());
            meta.setDisplayName(ChatColor.GREEN + "Recompensas pendientes");
            meta.setLore(List.of(
                    ChatColor.GRAY + "Disponibles: " + (pending > 0 ? ChatColor.GREEN : ChatColor.WHITE) + pending,
                    ChatColor.GRAY + "Usa la estrella del lobby para reclamarlas."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack playerHead(Player player, PlayerLevelManager levels, PlayerStatsManager stats) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(ChatColor.AQUA + "Perfil de " + ChatColor.WHITE + player.getName());
            PlayerStatsManager.Stats global = stats.getGlobal(player.getUniqueId());
            meta.setLore(List.of(
                    ChatColor.LIGHT_PURPLE + "Nivel del minipase: " + ChatColor.WHITE + levels.getLevel(player.getUniqueId()),
                    ChatColor.GRAY + "Partidas jugadas: " + ChatColor.WHITE + global.played(),
                    ChatColor.GRAY + "Victorias: " + ChatColor.GREEN + global.wins()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack passProgress(Player player, PlayerLevelManager levels) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Progreso del minipase");
            meta.setLore(List.of(
                    ChatColor.GRAY + "Nivel: " + ChatColor.WHITE + levels.getLevel(player.getUniqueId()),
                    ChatColor.GRAY + "XP total: " + ChatColor.WHITE + levels.getXp(player.getUniqueId()),
                    ChatColor.GRAY + "Siguiente nivel: " + ChatColor.WHITE
                            + levels.getXpIntoCurrentLevel(player.getUniqueId()) + "/" + levels.getXpPerLevel()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack globalStats(Player player, PlayerStatsManager manager) {
        return statsItem(Material.NETHER_STAR, ChatColor.GOLD + "Estadísticas globales",
                manager.getGlobal(player.getUniqueId()));
    }

    private static ItemStack gameStats(Player player, String game, PlayerStatsManager manager) {
        return statsItem(Material.COMPASS, ChatColor.YELLOW + game.toUpperCase(),
                manager.get(player.getUniqueId(), game));
    }

    private static ItemStack statsItem(Material material, String title, PlayerStatsManager.Stats stats) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            meta.setLore(List.of(
                    ChatColor.GRAY + "Partidas: " + ChatColor.WHITE + stats.played(),
                    ChatColor.GRAY + "Victorias: " + ChatColor.GREEN + stats.wins(),
                    ChatColor.GRAY + "Derrotas: " + ChatColor.RED + stats.losses(),
                    ChatColor.GRAY + "Abandonos: " + ChatColor.RED + stats.abandons(),
                    ChatColor.GRAY + "Porcentaje de victoria: " + ChatColor.WHITE
                            + String.format(java.util.Locale.ROOT, "%.1f%%", stats.winRate()),
                    ChatColor.GRAY + "Racha actual: " + ChatColor.WHITE + stats.currentStreak(),
                    ChatColor.GRAY + "Mejor racha: " + ChatColor.GOLD + stats.bestStreak()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
}
