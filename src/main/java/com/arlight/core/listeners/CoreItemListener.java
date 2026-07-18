package com.arlight.core.listeners;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.gui.RewardsGUI;
import com.arlight.core.gui.SelectorGUI;
import com.arlight.core.items.CoreItems;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
        giveItemsIfMissing(player);
    }

    private void giveItemsIfMissing(Player player) {
        boolean hasSelector = false;
        boolean hasRewards = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (CoreItems.isSelectorItem(plugin, item)) hasSelector = true;
            if (CoreItems.isRewardsItem(plugin, item)) hasRewards = true;
        }
        if (!hasSelector) player.getInventory().addItem(CoreItems.createSelectorItem(plugin));
        if (!hasRewards) player.getInventory().addItem(CoreItems.createRewardsItem(plugin));
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
        }
    }
}
