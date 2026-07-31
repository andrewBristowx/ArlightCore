package com.arlight.core.commands;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.items.CoreItems;
import com.arlight.core.api.MinigameProvider;
import com.arlight.core.api.ArlightCoreIcons;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

public class CoreCommand implements CommandExecutor, TabCompleter {

    private final ArlightCorePlugin plugin;
    private final Map<String, Long> resetConfirmations = new HashMap<>();

    public CoreCommand(ArlightCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("items", "reward", "xp", "stats", "status", "doctor", "worldtx", "debug", "recover", "setlobby", "lobby", "queue", "games", "minigames", "leaderboard", "hologram", "multiverse", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("queue")) {
            return filter(Arrays.asList("leave", "status"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("minigames")) {
            return filter(Arrays.asList("status", "enable", "disable"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("minigames")
                && (args[1].equalsIgnoreCase("enable") || args[1].equalsIgnoreCase("disable"))) {
            List<String> ids = plugin.getMinigameRegistry().getAll().stream()
                    .map(MinigameProvider::getId).collect(Collectors.toList());
            ids.add("all");
            return filter(ids, args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("multiverse")) {
            return filter(Arrays.asList("status", "sync"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("worldtx")) {
            return filter(List.of("list", "recover"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reward")) {
            return filter(Arrays.asList("set", "remove", "list"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("xp")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            return filter(List.of("view", "reset", "set"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("stats")) {
            return filter(Bukkit.getOfflinePlayers().length == 0 ? List.of() :
                    Arrays.stream(Bukkit.getOfflinePlayers()).map(OfflinePlayer::getName)
                            .filter(java.util.Objects::nonNull).collect(Collectors.toList()), args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("debug") || args[0].equalsIgnoreCase("recover"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("leaderboard")) {
            return filter(Arrays.asList("create", "remove", "list", "refresh"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("leaderboard")
                && args[1].equalsIgnoreCase("create")) {
            return filter(Arrays.asList("levels", "wins"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("hologram")) {
            return filter(Arrays.asList("create", "addline", "setline", "removeline", "move", "remove", "list", "refresh"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("hologram")
                && Arrays.asList("addline", "setline", "removeline", "move", "remove").contains(args[1].toLowerCase())) {
            return filter(plugin.getHologramManager().ids(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("leaderboard")
                && args[1].equalsIgnoreCase("remove")) {
            return filter(plugin.getLeaderboardManager().ids(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reward")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove"))) {
            return filter(plugin.getRewardManager().getAll().keySet().stream().map(String::valueOf).collect(Collectors.toList()), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /core <items|reward|xp|stats|status|doctor|worldtx|debug|recover|setlobby|lobby|queue|games|minigames|leaderboard|hologram|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "items": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                CoreItems.giveIfMissing(plugin, player);
                sender.sendMessage(ChatColor.GREEN + "Tus items del Core están listos.");
                return true;
            }

            case "reward": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /core reward <set|remove|list> [nivel]");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "set": {
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(ChatColor.RED + "Solo un jugador sosteniendo el item puede hacer esto.");
                            return true;
                        }
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core reward set <nivel>");
                            return true;
                        }
                        int level;
                        try {
                            level = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "El nivel tiene que ser un numero.");
                            return true;
                        }
                        if (level <= 0) {
                            sender.sendMessage(ChatColor.RED + "El nivel debe ser mayor que cero.");
                            return true;
                        }
                        Player player = (Player) sender;
                        ItemStack held = player.getInventory().getItemInMainHand();
                        if (held.getType().isAir()) {
                            sender.sendMessage(ChatColor.RED + "Tenes que sostener el item que queres dar de recompensa.");
                            return true;
                        }
                        plugin.getRewardManager().setReward(level, held);
                        sender.sendMessage(ChatColor.GREEN + "Recompensa del nivel " + level + " actualizada (" + held.getAmount() + "x " + held.getType() + ").");
                        return true;
                    }
                    case "remove": {
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core reward remove <nivel>");
                            return true;
                        }
                        try {
                            int level = Integer.parseInt(args[2]);
                            if (level <= 0) {
                                sender.sendMessage(ChatColor.RED + "El nivel debe ser mayor que cero.");
                                return true;
                            }
                            plugin.getRewardManager().removeReward(level);
                            sender.sendMessage(ChatColor.GREEN + "Recompensa del nivel " + level + " eliminada.");
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "El nivel tiene que ser un numero.");
                        }
                        return true;
                    }
                    case "list": {
                        sender.sendMessage(ChatColor.GOLD + "Niveles con recompensa: " + ChatColor.WHITE
                                + plugin.getRewardManager().getAll().keySet());
                        return true;
                    }
                    default:
                        sender.sendMessage(ChatColor.RED + "Subcomando de reward desconocido.");
                        return true;
                }
            }

            case "xp": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Uso: /core xp <jugador> <cantidad>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Jugador no encontrado (debe estar conectado).");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount <= 0) {
                        sender.sendMessage(ChatColor.RED + "La cantidad de XP debe ser mayor que cero.");
                        return true;
                    }
                    plugin.getLevelManager().addXp(target.getUniqueId(), amount);
                    sender.sendMessage(ChatColor.GREEN + "Se le dio " + amount + " XP a " + target.getName() + ".");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "La cantidad tiene que ser un numero.");
                }
                return true;
            }

            case "stats": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Uso: /core stats <view|reset|set> <jugador> ...");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "Ese jugador nunca ha entrado al servidor.");
                    return true;
                }
                if (args[1].equalsIgnoreCase("view")) {
                    sendStats(sender, target, args.length >= 4 ? args[3] : null);
                    return true;
                }
                if (args[1].equalsIgnoreCase("reset")) {
                    String minigame = args.length >= 4 && !args[3].equalsIgnoreCase("confirm") ? args[3] : null;
                    boolean confirmed = Arrays.stream(args).anyMatch(value -> value.equalsIgnoreCase("confirm"));
                    String key = sender.getName().toLowerCase() + ":" + target.getUniqueId() + ":" + (minigame == null ? "*" : minigame.toLowerCase());
                    if (!confirmed || resetConfirmations.getOrDefault(key, 0L) < System.currentTimeMillis()) {
                        resetConfirmations.put(key, System.currentTimeMillis() + 30_000L);
                        sender.sendMessage(ChatColor.YELLOW + "Confirma durante 30 segundos con: /core stats reset "
                                + args[2] + (minigame == null ? "" : " " + minigame) + " confirm");
                        return true;
                    }
                    resetConfirmations.remove(key);
                    boolean reset = plugin.getStatsManager().reset(target.getUniqueId(), minigame);
                    if (!reset) sender.sendMessage(ChatColor.YELLOW + "No había estadísticas que eliminar.");
                    else {
                        plugin.getLeaderboardManager().refresh();
                        plugin.getAuditLogger().log(sender, "STATS_RESET target=" + target.getUniqueId() + " minigame=" + (minigame == null ? "ALL" : minigame));
                        sender.sendMessage(ChatColor.GREEN + "Estadísticas reiniciadas correctamente.");
                    }
                    return true;
                }
                if (args[1].equalsIgnoreCase("set")) {
                    if (args.length < 6) {
                        sender.sendMessage(ChatColor.RED + "Uso: /core stats set <jugador> <minijuego> <campo> <valor>");
                        return true;
                    }
                    try {
                        int value = Integer.parseInt(args[5]);
                        boolean changed = plugin.getStatsManager().set(target.getUniqueId(), args[3], args[4], value);
                        if (!changed) sender.sendMessage(ChatColor.RED + "Campo inválido o valor negativo. Campos: played, wins, losses, abandons, streak, best-streak.");
                        else {
                            plugin.getLeaderboardManager().refresh();
                            plugin.getAuditLogger().log(sender, "STATS_SET target=" + target.getUniqueId() + " minigame=" + args[3] + " field=" + args[4] + " value=" + value);
                            sender.sendMessage(ChatColor.GREEN + "Estadística actualizada.");
                        }
                    } catch (NumberFormatException error) {
                        sender.sendMessage(ChatColor.RED + "El valor debe ser un número entero.");
                    }
                    return true;
                }
                sender.sendMessage(ChatColor.RED + "Uso: /core stats <view|reset|set> <jugador> ...");
                return true;
            }

            case "status": {
                if (!checkAdmin(sender)) return true;
                sender.sendMessage(icon(ArlightCoreIcons.INFO) + ChatColor.GOLD
                        + "Estado de ArlightCore " + ChatColor.GRAY + "v"
                        + plugin.getDescription().getVersion());
                sender.sendMessage(ChatColor.YELLOW + "Base de datos: " + (plugin.getDatabaseManager().isEnabled() ? ChatColor.GREEN + "CONECTADA" : ChatColor.RED + "YAML / DESCONECTADA"));
                sender.sendMessage(ChatColor.YELLOW + "Minijuegos registrados: " + ChatColor.WHITE + plugin.getMinigameRegistry().size());
                sender.sendMessage(ChatColor.YELLOW + "Sesiones: " + ChatColor.WHITE + plugin.getSessionManager().size()
                        + ChatColor.GRAY + " (pendientes: " + plugin.getSessionManager().pendingCount() + ")");
                sender.sendMessage(ChatColor.YELLOW + "Jugadores en colas: " + ChatColor.WHITE + plugin.getQueueManager().totalSize());
                sender.sendMessage(ChatColor.YELLOW + "Acceso a minijuegos: "
                        + (plugin.areMinigamesEnabled() ? ChatColor.GREEN + "HABILITADO" : ChatColor.RED + "DESHABILITADO"));
                if (!plugin.getDisabledMinigames().isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Deshabilitados individualmente: "
                            + ChatColor.WHITE + plugin.getDisabledMinigames());
                }
                return true;
            }

            case "doctor": {
                if (!checkAdmin(sender)) return true;
                var health = plugin.getSessionManager().storageHealth();
                sender.sendMessage(ChatColor.GOLD + "ArlightCore Doctor "
                        + ChatColor.WHITE + "v" + plugin.getDescription().getVersion());
                sender.sendMessage(ChatColor.YELLOW + "Entorno: " + ChatColor.WHITE
                        + "Java " + System.getProperty("java.version") + " | "
                        + Bukkit.getBukkitVersion());
                sender.sendMessage(ChatColor.YELLOW + "Sesiones: " + ChatColor.WHITE
                        + plugin.getSessionManager().size() + ChatColor.GRAY
                        + " | pendientes=" + plugin.getSessionManager().pendingCount()
                        + " | restaurando=" + plugin.getSessionManager().restoringCount());
                sender.sendMessage(ChatColor.YELLOW + "Persistencia: "
                        + (health.healthy() ? ChatColor.GREEN + "SALUDABLE" : ChatColor.RED + "REVISAR")
                        + ChatColor.GRAY + " | último=" + health.lastEvent());
                sender.sendMessage(ChatColor.YELLOW + "Archivos: " + ChatColor.WHITE
                        + "actual=" + health.currentFile()
                        + " temp=" + health.temporaryFile()
                        + " backup=" + health.backupFile()
                        + " journal=" + health.journalFile());
                if (health.error() != null && !health.error().isBlank()) {
                    sender.sendMessage(ChatColor.RED + "Error de persistencia: " + health.error());
                }
                sender.sendMessage(ChatColor.YELLOW + "Mundos cargados: " + ChatColor.WHITE
                        + Bukkit.getWorlds().size() + ChatColor.GRAY + " | lobby="
                        + (Bukkit.getWorld(plugin.getLobbyWorld()) == null
                        ? ChatColor.RED + "NO CARGADO" : ChatColor.GREEN + "OK"));
                sender.sendMessage(ChatColor.YELLOW + "Minijuegos registrados: " + ChatColor.WHITE
                        + plugin.getMinigameRegistry().getAll().size());
                sender.sendMessage(ChatColor.YELLOW + "Integraciones: " + ChatColor.WHITE
                        + "PAPI=" + pluginEnabled("PlaceholderAPI")
                        + " MV=" + pluginEnabled("Multiverse-Core")
                        + " MVI=" + pluginEnabled("Multiverse-Inventories")
                        + " MVNP=" + pluginEnabled("Multiverse-NetherPortals"));
                sender.sendMessage(ChatColor.YELLOW + "Transacciones de mundos: " + ChatColor.WHITE
                        + plugin.getArenaWorldManager().transactions().size() + ChatColor.GRAY
                        + " | pendientes=" + plugin.getArenaWorldManager().pendingCount());
                if (plugin.getArenaWorldManager().lastError() != null
                        && !plugin.getArenaWorldManager().lastError().isBlank()) {
                    sender.sendMessage(ChatColor.RED + "Último error de mundos: "
                            + plugin.getArenaWorldManager().lastError());
                }
                return true;
            }

            case "worldtx": {
                if (!checkAdmin(sender)) return true;
                if (args.length >= 2 && args[1].equalsIgnoreCase("recover")) {
                    var recovered = plugin.getArenaWorldManager().recoverInterrupted();
                    sender.sendMessage(ChatColor.GREEN + "Recuperación revisada: " + recovered.size()
                            + " transacción(es).");
                    for (var result : recovered) {
                        sender.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED)
                                + String.valueOf(result.transactionId()) + " " + result.stage()
                                + ChatColor.GRAY + " - " + result.message());
                    }
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + "Transacciones de mundos");
                var transactions = plugin.getArenaWorldManager().transactions();
                if (transactions.isEmpty()) sender.sendMessage(ChatColor.GRAY + "No hay transacciones registradas.");
                for (var tx : transactions) {
                    sender.sendMessage(ChatColor.GRAY + tx.id().toString() + " " + ChatColor.YELLOW
                            + tx.owner() + ChatColor.WHITE + " " + tx.activeWorld() + " <- "
                            + tx.preparedWorld() + ChatColor.GRAY + " [" + tx.stage() + "]");
                    if (!tx.error().isBlank()) sender.sendMessage(ChatColor.RED + "  " + tx.error());
                }
                sender.sendMessage(ChatColor.YELLOW + "Usa /core worldtx recover para reanudar pendientes.");
                return true;
            }

            case "debug": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Uso: /core debug <jugador>"); return true; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                var uuid = target.getUniqueId();
                sender.sendMessage(ChatColor.GOLD + "Diagnóstico de " + target.getName());
                sender.sendMessage(ChatColor.YELLOW + "Conectado: " + ChatColor.WHITE + target.isOnline());
                sender.sendMessage(ChatColor.YELLOW + "Sesión: " + ChatColor.WHITE + plugin.getSessionManager().hasSession(uuid)
                        + ChatColor.GRAY + " | iniciada=" + plugin.getSessionManager().hasStarted(uuid)
                        + " | recuperación=" + plugin.getSessionManager().isPendingRestore(uuid));
                sender.sendMessage(ChatColor.YELLOW + "Minijuego: " + ChatColor.WHITE + String.valueOf(plugin.getSessionManager().getMinigameId(uuid)));
                sender.sendMessage(ChatColor.YELLOW + "Cola: " + ChatColor.WHITE + String.valueOf(plugin.getQueueManager().getQueuedMinigame(uuid)));
                sendStats(sender, target, null);
                return true;
            }

            case "recover": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Uso: /core recover <jugador>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(ChatColor.RED + "El jugador debe estar conectado."); return true; }
                String minigame = plugin.getSessionManager().getMinigameId(target.getUniqueId());
                if (!plugin.getSessionManager().forceRecover(target)) {
                    sender.sendMessage(ChatColor.RED + "No tiene una recuperación pendiente; no se modificó su inventario.");
                    return true;
                }
                MinigameProvider provider = plugin.getMinigameRegistry().get(minigame);
                if (provider != null) {
                    try {
                        provider.cleanupAfterRecovery(target);
                    } catch (Exception error) {
                        plugin.getLogger().warning("La sesión fue restaurada, pero " + minigame
                                + " no pudo limpiar sus objetos temporales: " + error.getMessage());
                    }
                }
                if (plugin.getLobbySpawn() != null) target.teleport(plugin.getLobbySpawn());
                CoreItems.giveIfMissing(plugin, target);
                plugin.getAuditLogger().log(sender, "SESSION_RECOVER target=" + target.getUniqueId() + " minigame=" + minigame);
                sender.sendMessage(ChatColor.GREEN + "Sesión recuperada e inventario restaurado.");
                return true;
            }

            case "setlobby": {
                if (!checkAdmin(sender)) return true;
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Este comando debe usarse dentro del juego.");
                    return true;
                }
                plugin.setLobbySpawn(player.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Lobby principal establecido en "
                        + player.getWorld().getName() + " (" + player.getLocation().getBlockX() + ", "
                        + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ() + ").");
                return true;
            }

            case "lobby": {
                if (!(sender instanceof Player player)) return true;
                if (plugin.getSessionManager().hasSession(player.getUniqueId())) {
                    sender.sendMessage(ChatColor.RED + "No puedes usar /core lobby mientras participas en un minijuego.");
                    return true;
                }
                if (plugin.getLobbySpawn() == null) {
                    sender.sendMessage(ChatColor.RED + "El mundo del lobby no está cargado.");
                    return true;
                }
                player.teleport(plugin.getLobbySpawn());
                CoreItems.giveIfMissing(plugin, player);
                return true;
            }

            case "queue": {
                if (!(sender instanceof Player player)) return true;
                if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
                    String queued = plugin.getQueueManager().getQueuedMinigame(player.getUniqueId());
                    sender.sendMessage(queued == null
                            ? ChatColor.YELLOW + "No estás en ninguna cola."
                            : ChatColor.GREEN + "Estás en la cola de " + queued + " ("
                            + plugin.getQueueManager().size(queued) + " jugadores).");
                    return true;
                }
                if (args[1].equalsIgnoreCase("leave")) {
                    if (!plugin.getQueueManager().leave(player, true)) {
                        sender.sendMessage(ChatColor.YELLOW + "No estás en ninguna cola.");
                    }
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + "Uso: /core queue <status|leave>");
                return true;
            }

            case "games": {
                sender.sendMessage(ChatColor.GOLD + "Minijuegos registrados:");
                if (plugin.getMinigameRegistry().size() == 0) {
                    sender.sendMessage(ChatColor.GRAY + "No hay minijuegos registrados.");
                    return true;
                }
                for (MinigameProvider provider : plugin.getMinigameRegistry().getAll()) {
                    String shownStatus = plugin.isMinigameEnabled(provider.getId())
                            ? provider.getStatus().name() : "DISABLED_BY_CORE";
                    sender.sendMessage(ChatColor.YELLOW + "- " + provider.getId()
                            + ChatColor.GRAY + " | " + shownStatus
                            + " | XP victoria " + plugin.getXpPerWin(provider.getId())
                            + " | cola " + plugin.getQueueManager().size(provider.getId())
                            + " | partida " + provider.getCurrentPlayers() + "/"
                            + (provider.getMaxPlayers() <= 0 ? "?" : provider.getMaxPlayers()));
                }
                return true;
            }

            case "minigames": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
                    sender.sendMessage(icon(ArlightCoreIcons.INFO) + ChatColor.GOLD
                            + "Control de minijuegos");
                    sender.sendMessage(ChatColor.YELLOW + "Interruptor global: "
                            + (plugin.areMinigamesEnabled() ? ChatColor.GREEN + "HABILITADO" : ChatColor.RED + "DESHABILITADO"));
                    if (plugin.getMinigameRegistry().size() == 0) {
                        sender.sendMessage(ChatColor.GRAY + "No hay minijuegos registrados.");
                    } else {
                        for (MinigameProvider provider : plugin.getMinigameRegistry().getAll()) {
                            sender.sendMessage((plugin.isMinigameEnabled(provider.getId()) ? ChatColor.GREEN : ChatColor.RED)
                                    + "- " + provider.getId() + ChatColor.GRAY + " (" + provider.getDisplayName() + ChatColor.GRAY + ")");
                        }
                    }
                    return true;
                }
                boolean enable;
                if (args[1].equalsIgnoreCase("enable")) enable = true;
                else if (args[1].equalsIgnoreCase("disable")) enable = false;
                else {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /core minigames <status|enable|disable> [id|all]");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Indica el ID del minijuego o 'all'.");
                    return true;
                }
                String id = args[2].toLowerCase(java.util.Locale.ROOT);
                if (id.equals("all")) {
                    plugin.setAllMinigamesEnabled(enable);
                    plugin.getAuditLogger().log(sender, "MINIGAMES_GLOBAL enabled=" + enable);
                    sender.sendMessage(enable
                            ? icon(ArlightCoreIcons.CHECK) + ChatColor.GREEN
                                    + "Todos los minijuegos vuelven a aceptar jugadores."
                            : icon(ArlightCoreIcons.WARNING) + ChatColor.RED
                                    + "Se bloquearon nuevas entradas a todos los minijuegos.");
                    return true;
                }
                MinigameProvider provider = plugin.getMinigameRegistry().get(id);
                if (provider == null) {
                    sender.sendMessage(icon(ArlightCoreIcons.CROSS) + ChatColor.RED
                            + "No hay un minijuego registrado con el ID '" + id + "'.");
                    return true;
                }
                plugin.setMinigameEnabled(id, enable);
                plugin.getAuditLogger().log(sender, "MINIGAME_ACCESS id=" + id + " enabled=" + enable);
                sender.sendMessage(enable
                        ? icon(ArlightCoreIcons.CHECK) + ChatColor.GREEN
                                + "El minijuego '" + id + "' fue habilitado."
                        : icon(ArlightCoreIcons.WARNING) + ChatColor.RED
                                + "El minijuego '" + id + "' fue deshabilitado para nuevas entradas.");
                return true;
            }

            case "leaderboard": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /core leaderboard <create|remove|list|refresh>");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "create": {
                        if (!(sender instanceof Player player) || args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core leaderboard create <levels|wins>");
                            return true;
                        }
                        String id = plugin.getLeaderboardManager().create(args[2], player.getLocation());
                        if (id == null) {
                            sender.sendMessage(ChatColor.RED + "Tipo inválido. Usa levels o wins.");
                        } else {
                            sender.sendMessage(ChatColor.GREEN + "Texto flotante creado con ID " + id + ".");
                        }
                        return true;
                    }
                    case "remove": {
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core leaderboard remove <id>");
                            return true;
                        }
                        sender.sendMessage(plugin.getLeaderboardManager().remove(args[2])
                                ? ChatColor.GREEN + "Texto flotante eliminado."
                                : ChatColor.RED + "No existe un texto flotante con ese ID.");
                        return true;
                    }
                    case "list":
                        sender.sendMessage(ChatColor.GOLD + "Textos flotantes: " + ChatColor.WHITE
                                + plugin.getLeaderboardManager().ids());
                        return true;
                    case "refresh":
                        plugin.getLeaderboardManager().refresh();
                        sender.sendMessage(ChatColor.GREEN + "Clasificaciones actualizadas.");
                        return true;
                    default:
                        sender.sendMessage(ChatColor.RED + "Subcomando desconocido.");
                        return true;
                }
            }

            case "hologram": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 2) {
                    sendHologramUsage(sender);
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "create": {
                        if (!(sender instanceof Player player) || args.length < 4) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core hologram create <id> <linea 1 | linea 2>");
                            return true;
                        }
                        List<String> lines = Arrays.stream(joinArgs(args, 3).split("\\s*\\|\\s*"))
                                .filter(line -> !line.isBlank()).collect(Collectors.toList());
                        boolean created = plugin.getHologramManager().create(args[2], player.getLocation(), lines);
                        sender.sendMessage(created
                                ? ChatColor.GREEN + "Holograma '" + args[2].toLowerCase() + "' creado."
                                : ChatColor.RED + "No se pudo crear. Revisa el ID, el texto o si ya existe.");
                        return true;
                    }
                    case "addline": {
                        if (args.length < 4) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core hologram addline <id> <texto>");
                            return true;
                        }
                        sender.sendMessage(plugin.getHologramManager().addLine(args[2], joinArgs(args, 3))
                                ? ChatColor.GREEN + "Línea agregada."
                                : ChatColor.RED + "No existe ese holograma.");
                        return true;
                    }
                    case "setline": {
                        if (args.length < 5) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core hologram setline <id> <numero> <texto>");
                            return true;
                        }
                        Integer line = parsePositiveInt(args[3]);
                        if (line == null) {
                            sender.sendMessage(ChatColor.RED + "El número de línea no es válido.");
                            return true;
                        }
                        sender.sendMessage(plugin.getHologramManager().setLine(args[2], line, joinArgs(args, 4))
                                ? ChatColor.GREEN + "Línea actualizada."
                                : ChatColor.RED + "No existe el holograma o esa línea.");
                        return true;
                    }
                    case "removeline": {
                        if (args.length < 4) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core hologram removeline <id> <numero>");
                            return true;
                        }
                        Integer line = parsePositiveInt(args[3]);
                        sender.sendMessage(line != null && plugin.getHologramManager().removeLine(args[2], line)
                                ? ChatColor.GREEN + "Línea eliminada."
                                : ChatColor.RED + "No se pudo eliminar (debe quedar al menos una línea).");
                        return true;
                    }
                    case "move": {
                        if (!(sender instanceof Player player) || args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core hologram move <id>");
                            return true;
                        }
                        sender.sendMessage(plugin.getHologramManager().move(args[2], player.getLocation())
                                ? ChatColor.GREEN + "Holograma movido a tu posición."
                                : ChatColor.RED + "No existe ese holograma.");
                        return true;
                    }
                    case "remove": {
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core hologram remove <id>");
                            return true;
                        }
                        sender.sendMessage(plugin.getHologramManager().remove(args[2])
                                ? ChatColor.GREEN + "Holograma eliminado."
                                : ChatColor.RED + "No existe ese holograma.");
                        return true;
                    }
                    case "list":
                        sender.sendMessage(ChatColor.GOLD + "Hologramas: " + ChatColor.WHITE
                                + plugin.getHologramManager().ids());
                        return true;
                    case "refresh":
                        plugin.getHologramManager().refresh();
                        sender.sendMessage(ChatColor.GREEN + "Hologramas actualizados.");
                        return true;
                    default:
                        sendHologramUsage(sender);
                        return true;
                }
            }

            case "multiverse": {
                if (!checkAdmin(sender)) return true;
                if (plugin.getMultiverseIntegrationManager() == null) {
                    sender.sendMessage(ChatColor.RED + "La integración Multiverse no está inicializada.");
                    return true;
                }
                if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
                    plugin.getMultiverseIntegrationManager().sendStatus(sender);
                    return true;
                }
                if (args[1].equalsIgnoreCase("sync")) {
                    boolean ok = plugin.getMultiverseIntegrationManager().syncConfiguredGroups();
                    sender.sendMessage(ok
                            ? ChatColor.GREEN + "Grupos de Multiverse sincronizados."
                            : ChatColor.RED + "La sincronización terminó con errores; revisa la consola.");
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + "Uso: /core multiverse <status|sync>");
                return true;
            }

            case "reload": {
                if (!checkAdmin(sender)) return true;
                plugin.reloadCoreConfig();
                sender.sendMessage(icon(ArlightCoreIcons.CHECK) + ChatColor.GREEN
                        + "Configuración recargada.");
                return true;
            }

            default:
                sender.sendMessage(icon(ArlightCoreIcons.CROSS) + ChatColor.RED
                        + "Subcomando desconocido.");
                return true;
        }
    }

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission("arlightcore.admin")) {
            sender.sendMessage(icon(ArlightCoreIcons.CROSS) + ChatColor.RED
                    + "No tienes permiso para hacer eso.");
            return false;
        }
        return true;
    }

    private String icon(String icon) {
        return plugin.getConfig().getBoolean("decorations.enabled", true) ? icon : "";
    }

    private String pluginEnabled(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name) ? "OK" : "OFF";
    }

    private String joinArgs(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    private Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void sendHologramUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Uso: /core hologram <create|addline|setline|removeline|move|remove|list|refresh>");
    }

    private void sendStats(CommandSender sender, OfflinePlayer target, String minigame) {
        var stats = minigame == null ? plugin.getStatsManager().getGlobal(target.getUniqueId())
                : plugin.getStatsManager().get(target.getUniqueId(), minigame);
        sender.sendMessage(ChatColor.GOLD + "Estadísticas de " + target.getName()
                + (minigame == null ? " (globales)" : " en " + minigame));
        sender.sendMessage(ChatColor.GRAY + "Partidas=" + stats.played() + " | Victorias=" + stats.wins()
                + " | Derrotas=" + stats.losses() + " | Abandonos=" + stats.abandons());
        sender.sendMessage(ChatColor.GRAY + "Winrate=" + String.format(java.util.Locale.ROOT, "%.1f%%", stats.winRate())
                + " | Racha=" + stats.currentStreak() + " | Mejor=" + stats.bestStreak());
    }
}
