package com.arlight.core.listeners;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.api.MinigameProvider;
import com.arlight.core.api.MinigameStatus;
import com.arlight.core.gui.RewardsGUI;
import com.arlight.core.gui.SelectorGUI;
import com.arlight.core.gui.ProfileGUI;
import com.arlight.core.reward.RewardManager;
import com.arlight.core.xp.PlayerLevelManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public class CoreGUIListener implements Listener {

    private final ArlightCorePlugin plugin;

    public CoreGUIListener(ArlightCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (SelectorGUI.isTitle(title) || title.equals(RewardsGUI.TITLE)
                || title.equals(ProfileGUI.TITLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (SelectorGUI.isTitle(title)) {
            event.setCancelled(true);
            handleSelectorClick(event);
            return;
        }

        if (title.equals(RewardsGUI.TITLE)) {
            event.setCancelled(true);
            handleRewardsClick(event);
            return;
        }

        if (title.equals(ProfileGUI.TITLE)) {
            event.setCancelled(true);
        }
    }

    private void handleSelectorClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String providerId = SelectorGUI.getProviderIdFromItem(plugin, clicked);
        if (providerId == null) return;

        MinigameProvider provider = plugin.getMinigameRegistry().get(providerId);
        if (provider == null) return;

        if (!plugin.isMinigameEnabled(providerId)) {
            player.sendMessage(ChatColor.RED + "Ese minijuego está deshabilitado temporalmente.");
            return;
        }

        if (!provider.getStatus().canJoin()) {
            player.sendMessage(ChatColor.RED + "Esa partida ya esta en curso, esperá a que termine.");
            return;
        }
        player.closeInventory();
        plugin.getQueueManager().join(player, provider);
    }

    private void handleRewardsClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        Integer level = RewardsGUI.getLevelFromItem(plugin, clicked);
        if (level == null) return;

        PlayerLevelManager levelManager = plugin.getLevelManager();
        RewardManager rewardManager = plugin.getRewardManager();

        if (level > levelManager.getLevel(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Todavia no alcanzaste ese nivel.");
            return;
        }
        if (levelManager.isClaimed(player.getUniqueId(), level)) {
            player.sendMessage(ChatColor.YELLOW + "Ya reclamaste la recompensa de ese nivel.");
            return;
        }
        if (!rewardManager.hasReward(level)) {
            player.sendMessage(ChatColor.RED + "Ese nivel no tiene una recompensa configurada.");
            return;
        }

        Collection<String> claimWorlds = plugin.getClaimWorlds();
        String currentWorld = player.getWorld().getName().toLowerCase(Locale.ROOT);
        boolean allowedWorld = claimWorlds.stream()
                .filter(java.util.Objects::nonNull)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(currentWorld::equals);
        if (!allowedWorld) {
            player.sendMessage(ChatColor.RED + "Solo podes reclamar recompensas en: " + String.join(", ", claimWorlds));
            return;
        }

        ItemStack reward = rewardManager.getReward(level);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        levelManager.markClaimed(player.getUniqueId(), level);
        player.sendMessage(ChatColor.GREEN + "Reclamaste la recompensa del nivel " + level + "!");
        player.openInventory(RewardsGUI.build(plugin, player, levelManager, rewardManager));
    }
}
