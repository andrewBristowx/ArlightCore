package com.arlight.core.listeners;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.items.CoreItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Mantiene el lobby principal seguro y rescata a quienes caigan al vacío. */
public final class LobbyProtectionListener implements Listener {
    private final ArlightCorePlugin plugin;

    public LobbyProtectionListener(ArlightCorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean inLobby(Player player) {
        return player.getWorld().getName().equalsIgnoreCase(plugin.getLobbyWorld());
    }

    private boolean canBuild(Player player) {
        return player.isOp() || player.hasPermission("arlightcore.lobby.build");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.isLobbyBlockProtectionEnabled() && inLobby(event.getPlayer())
                && !canBuild(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.isLobbyBlockProtectionEnabled() && inLobby(event.getPlayer())
                && !canBuild(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        if (plugin.isLobbyBlockProtectionEnabled()
                && event.getLocation().getWorld() != null
                && event.getLocation().getWorld().getName().equalsIgnoreCase(plugin.getLobbyWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        if (plugin.isLobbyBlockProtectionEnabled()
                && event.getBlock().getWorld().getName().equalsIgnoreCase(plugin.getLobbyWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (plugin.isLobbyBlockProtectionEnabled()
                && event.getBlock().getWorld().getName().equalsIgnoreCase(plugin.getLobbyWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && inLobby(player)) {
            event.setCancelled(true);
            player.setFireTicks(0);
            player.setHealth(player.getMaxHealth());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && inLobby(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (inLobby(player) && player.getLocation().getY() < plugin.getLobbyVoidY()) {
            Location spawn = plugin.getLobbySpawn();
            if (spawn != null) {
                player.teleport(spawn);
                player.setFallDistance(0);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location spawn = plugin.getLobbySpawn();
        if (spawn != null && event.getPlayer().getWorld().getName().equalsIgnoreCase(plugin.getLobbyWorld())) {
            event.setRespawnLocation(spawn);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isTeleportToLobbyOnJoin() || plugin.getSessionManager().isPendingRestore(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            Location spawn = plugin.getLobbySpawn();
            if (spawn == null) return;
            player.teleport(spawn);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.setFireTicks(0);
            CoreItems.giveIfMissing(plugin, player);
        });
    }
}
