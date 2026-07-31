package com.arlight.core.queue;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.api.MinigameProvider;
import com.arlight.core.api.MinigameStatus;
import com.arlight.core.api.ArlightCoreIcons;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Mantiene una sola cola de minijuego por jugador. */
public class MinigameQueueManager {

    private final ArlightCorePlugin plugin;
    private final Map<String, LinkedHashSet<UUID>> queues = new LinkedHashMap<>();
    private final Map<UUID, String> playerQueues = new LinkedHashMap<>();

    public MinigameQueueManager(ArlightCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Reserva al jugador y llama al proveedor. Si el proveedor falla, la reserva
     * se revierte para evitar colas fantasma.
     */
    public boolean join(Player player, MinigameProvider provider) {
        if (player == null || provider == null) return false;
        UUID uuid = player.getUniqueId();
        String id = provider.getId().toLowerCase();

        if (!plugin.areMinigamesEnabled()) {
            player.sendMessage(color(withIcon("messages.minigames.all-disabled",
                    plugin.getConfig().getString("messages.minigames.all-disabled",
                            "&cLos minijuegos están deshabilitados temporalmente."))));
            return false;
        }
        if (!plugin.isMinigameEnabled(id)) {
            player.sendMessage(color(withIcon("messages.minigames.disabled",
                    plugin.getConfig().getString("messages.minigames.disabled",
                                    "&cEl minijuego &e%game% &cestá deshabilitado temporalmente.")
                            .replace("%game%", provider.getDisplayName()))));
            return false;
        }

        if (plugin.getSessionManager().hasSession(uuid)) {
            send(player, "messages.queue.already-playing",
                    "&cYa estás participando en un minijuego.", provider);
            return false;
        }

        String current = playerQueues.get(uuid);
        if (current != null) {
            if (current.equals(id)) {
                send(player, "messages.queue.already-queued",
                        "&eYa estás en la cola de %game%.", provider);
            } else {
                player.sendMessage(color(withIcon("messages.queue.other-queue",
                        plugin.getConfig().getString("messages.queue.other-queue",
                                        "&cYa estás en la cola de %game%. Sal primero con /core queue leave.")
                                .replace("%game%", current))));
            }
            return false;
        }

        if (!provider.getStatus().canJoin()) {
            send(player, "messages.queue.not-available",
                    "&c%game% no está aceptando jugadores ahora.", provider);
            return false;
        }

        Set<UUID> queue = queues.computeIfAbsent(id, ignored -> new LinkedHashSet<>());
        int max = provider.getMaxPlayers();
        if (max > 0 && queue.size() >= max) {
            send(player, "messages.queue.full", "&cLa cola de %game% está llena.", provider);
            return false;
        }

        queue.add(uuid);
        playerQueues.put(uuid, id);
        try {
            provider.join(player);
        } catch (Exception error) {
            remove(uuid);
            plugin.getLogger().warning("No se pudo unir a " + player.getName() + " a " + id
                    + ": " + error.getMessage());
            send(player, "messages.queue.join-error",
                    "&cNo se pudo entrar a %game%. Inténtalo nuevamente.", provider);
            return false;
        }

        if (plugin.getSessionManager().hasSession(uuid)) {
            send(player, "messages.queue.joined-game", "&aEntraste a &e%game%&a.", provider);
        } else {
            send(player, "messages.queue.joined", "&aTe uniste a la cola de &e%game%&a.", provider);
        }
        return true;
    }

    public boolean leave(Player player, boolean notifyProvider) {
        return leave(player, notifyProvider, true);
    }

    public boolean leave(Player player, boolean notifyProvider, boolean sendMessage) {
        if (player == null) return false;
        String id = playerQueues.get(player.getUniqueId());
        if (id == null) return false;
        MinigameProvider provider = plugin.getMinigameRegistry().get(id);
        if (notifyProvider && provider != null) {
            try {
                provider.leave(player);
            } catch (Exception error) {
                plugin.getLogger().warning("No se pudo avisar la salida de cola a " + id
                        + ": " + error.getMessage());
            }
        }
        remove(player.getUniqueId());
        if (sendMessage) {
            player.sendMessage(color(withIcon("messages.queue.left",
                    plugin.getConfig().getString("messages.queue.left",
                                    "&cSaliste de la cola de %game%.")
                            .replace("%game%", provider == null ? id : provider.getDisplayName()))));
        }
        return true;
    }

    /** Elimina la reserva cuando el minijuego inicia la sesión. */
    public void promote(UUID uuid) {
        remove(uuid);
    }

    public void remove(UUID uuid) {
        String id = playerQueues.remove(uuid);
        if (id == null) return;
        Set<UUID> queue = queues.get(id);
        if (queue != null) {
            queue.remove(uuid);
            if (queue.isEmpty()) queues.remove(id);
        }
    }

    public void clear(String minigameId) {
        if (minigameId == null) return;
        Set<UUID> removed = queues.remove(minigameId.toLowerCase());
        if (removed != null) removed.forEach(playerQueues::remove);
    }

    public boolean isQueued(UUID uuid) {
        return uuid != null && playerQueues.containsKey(uuid);
    }

    public String getQueuedMinigame(UUID uuid) {
        return uuid == null ? null : playerQueues.get(uuid);
    }

    public int totalSize() {
        return playerQueues.size();
    }

    public int size(String minigameId) {
        Set<UUID> queue = minigameId == null ? null : queues.get(minigameId.toLowerCase());
        return queue == null ? 0 : queue.size();
    }

    public List<UUID> players(String minigameId) {
        Set<UUID> queue = minigameId == null ? null : queues.get(minigameId.toLowerCase());
        return queue == null ? List.of() : new ArrayList<>(queue);
    }

    private void send(Player player, String path, String fallback, MinigameProvider provider) {
        String name = provider.getDisplayName() == null ? provider.getId() : provider.getDisplayName();
        player.sendMessage(color(withIcon(path,
                plugin.getConfig().getString(path, fallback).replace("%game%", name))));
    }

    private String withIcon(String path, String message) {
        if (!plugin.getConfig().getBoolean("decorations.enabled", true)) return message;
        String icon = switch (path) {
            case "messages.queue.joined", "messages.queue.joined-game" -> ArlightCoreIcons.CHECK;
            case "messages.queue.left" -> ArlightCoreIcons.WARNING;
            case "messages.queue.already-queued" -> ArlightCoreIcons.CLOCK;
            case "messages.minigames.all-disabled", "messages.queue.other-queue" -> ArlightCoreIcons.WARNING;
            case "messages.minigames.disabled", "messages.queue.already-playing",
                 "messages.queue.not-available", "messages.queue.full",
                 "messages.queue.join-error" -> ArlightCoreIcons.CROSS;
            default -> ArlightCoreIcons.INFO;
        };
        return icon + message;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
