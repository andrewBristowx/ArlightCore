package com.arlight.core.listeners;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.gui.RewardsGUI;
import com.arlight.core.gui.SelectorGUI;
import com.arlight.core.gui.ProfileGUI;
import com.arlight.core.items.CoreItems;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class CoreItemListener implements Listener {

    private final ArlightCorePlugin plugin;

    public CoreItemListener(ArlightCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.isGiveItemsOnJoin()) return;
        Player player = event.getPlayer();
        if (player.getWorld().getName().equalsIgnoreCase(plugin.getLobbyWorld())) {
            CoreItems.giveIfMissing(plugin, player);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getName().equalsIgnoreCase(plugin.getLobbyWorld())) {
            if (plugin.isGiveItemsOnJoin() && !plugin.getSessionManager().hasSession(player.getUniqueId())) {
                CoreItems.giveIfMissing(plugin, player);
            }
        } else if (!plugin.getSessionManager().hasSession(player.getUniqueId())) {
            CoreItems.removeAll(plugin, player);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (CoreItems.isSelectorItem(plugin, item)) {
            event.setCancelled(true);
            player.openInventory(SelectorGUI.build(plugin, plugin.getMinigameRegistry()));
            return;
        }

        if (CoreItems.isRewardsItem(plugin, item)) {
            event.setCancelled(true);
            player.openInventory(RewardsGUI.build(plugin, player, plugin.getLevelManager(), plugin.getRewardManager()));
            return;
        }

        if (CoreItems.isCosmeticsItem(plugin, item)) {
            event.setCancelled(true);
            if (!Bukkit.getPluginManager().isPluginEnabled("ArlightCosmetics")) {
                player.sendMessage(ChatColor.RED + "El armario de cosméticos no está disponible ahora mismo.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.7F);
                return;
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45F, 1.15F);
            player.performCommand("cosmeticos");
            return;
        }

        if (CoreItems.isProfileItem(plugin, item)) {
            event.setCancelled(true);
            player.openInventory(ProfileGUI.build(player, plugin.getLevelManager(), plugin.getStatsManager(),
                    plugin.getRewardManager()));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (CoreItems.isCoreItem(plugin, event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (CoreItems.isCoreItem(plugin, event.getMainHandItem())
                || CoreItems.isCoreItem(plugin, event.getOffHandItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (CoreItems.isCoreItem(plugin, event.getCurrentItem())
                || CoreItems.isCoreItem(plugin, event.getCursor())) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (CoreItems.isCoreItem(plugin, event.getOldCursor())) event.setCancelled(true);
    }
}
