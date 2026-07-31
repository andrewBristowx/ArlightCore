package com.arlight.core.listeners;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.api.MinigameProvider;
import com.arlight.core.items.CoreItems;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Recupera de forma segura las sesiones abandonadas o interrumpidas por reinicios. */
public class MinigameSessionListener implements Listener {

    private final ArlightCorePlugin plugin;

    public MinigameSessionListener(ArlightCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getVisualScoreboardNetwork() != null) {
            plugin.getVisualScoreboardNetwork().forget(player.getUniqueId());
        }
        if (!plugin.getSessionManager().hasSession(player.getUniqueId())) {
            plugin.getQueueManager().leave(player, true, false);
            return;
        }
        String minigameId = plugin.getSessionManager().getMinigameId(player.getUniqueId());
        if (minigameId == null) return;

        MinigameProvider provider = plugin.getMinigameRegistry().get(minigameId);
        if (provider != null) {
            try {
                provider.handleDisconnect(player);
            } catch (Exception error) {
                plugin.getLogger().warning("No se pudo notificar la desconexión a " + minigameId
                        + ": " + error.getMessage());
            }
        }

        // Se marca después de avisar al minijuego para que siempre quede pendiente.
        boolean actuallyPlayed = plugin.getSessionManager().hasStarted(player.getUniqueId());
        if (plugin.getSessionManager().markPendingRestore(player.getUniqueId()) && actuallyPlayed) {
            plugin.getStatsManager().recordAbandon(player.getUniqueId(), minigameId);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getSessionManager().isPendingRestore(player.getUniqueId())) return;

        String minigameId = plugin.getSessionManager().getMinigameId(player.getUniqueId());
        MinigameProvider provider = plugin.getMinigameRegistry().get(minigameId);
        if (provider != null) {
            try {
                // También limpia participantes que el minijuego haya recuperado de disco
                // después de un reinicio inesperado.
                provider.handleDisconnect(player);
            } catch (Exception error) {
                plugin.getLogger().warning("No se pudo limpiar la sesión interrumpida de "
                        + minigameId + ": " + error.getMessage());
            }
        }

        boolean externalInventory = plugin.getSessionManager().usesExternalInventory(player.getUniqueId());

        Runnable recover = () -> {
            if (!player.isOnline()) return;
            if (!plugin.getSessionManager().restorePending(player)) return;

            if (provider != null) {
                try {
                    provider.cleanupAfterRecovery(player);
                } catch (Exception error) {
                    plugin.getLogger().warning("No se pudieron limpiar los objetos temporales de "
                            + minigameId + ": " + error.getMessage());
                }
            }

            CoreItems.giveIfMissing(plugin, player);
            player.sendMessage(ChatColor.RED + "Fuiste descalificado porque abandonaste la partida.");
            player.sendMessage(ChatColor.GREEN + "Tu inventario fue restaurado y regresaste al lobby.");
        };

        org.bukkit.Location lobbySpawn = plugin.getLobbySpawn();
        if (externalInventory) {
            // Multiverse-Inventories restaura el perfil correcto al CAMBIAR de grupo.
            // Teletransportamos primero y cerramos la sesión dos ticks después, evitando
            // escribir el inventario del Survival dentro del perfil del minijuego.
            if (lobbySpawn != null) {
                player.teleport(lobbySpawn);
                Bukkit.getScheduler().runTaskLater(plugin, recover, 2L);
            } else {
                plugin.getLogger().warning("No se pudo enviar a " + player.getName()
                        + " al lobby porque el mundo '" + plugin.getLobbyWorld() + "' no está cargado.");
                Bukkit.getScheduler().runTask(plugin, recover);
            }
        } else {
            // Modo clásico: Core restaura la copia local un tick después del ingreso.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (lobbySpawn != null) player.teleport(lobbySpawn);
                else plugin.getLogger().warning("No se pudo enviar a " + player.getName()
                        + " al lobby porque el mundo '" + plugin.getLobbyWorld() + "' no está cargado.");
                recover.run();
            });
        }
    }
}
