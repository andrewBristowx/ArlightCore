package com.arlight.core.lobby;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.stats.PlayerStatsManager;
import com.arlight.core.api.ArlightCoreIcons;
import com.arlight.core.network.VisualScoreboardNetwork;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Scoreboard personal del lobby general de minijuegos. */
public final class LobbyScoreboardManager {

    private final ArlightCorePlugin plugin;
    private final Map<UUID, Board> boards = new HashMap<>();
    /** Jugadores cuyo panel visual actual pertenece al lobby de ArlightCore. */
    private final Set<UUID> visualOwners = new HashSet<>();
    private BukkitTask updateTask;

    public LobbyScoreboardManager(ArlightCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long interval = Math.max(10L,
                plugin.getConfig().getLong("lobby-scoreboard.update-interval-ticks", 20L));
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOnlinePlayers,
                1L, interval);
    }

    public void restart() {
        start();
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) removeIfOwned(player);
        boards.clear();
        visualOwners.clear();
    }

    private void refreshOnlinePlayers() {
        boolean enabled = plugin.getConfig().getBoolean("lobby-scoreboard.enabled", true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean shouldShow = enabled
                    && player.getWorld().getName().equalsIgnoreCase(plugin.getLobbyWorld())
                    && !plugin.getSessionManager().hasSession(player.getUniqueId());
            if (shouldShow) show(player);
            else removeIfOwned(player);
        }
        boards.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private void show(Player player) {
        Board board = boards.computeIfAbsent(player.getUniqueId(), ignored -> createBoard());
        if (board == null) return;

        Objective objective = board.objective();
        objective.setDisplayName(format(plugin.getConfig().getString(
                "lobby-scoreboard.title", "&d&lMinijuegos :ponicalva:")));

        for (String entry : new ArrayList<>(board.scoreboard().getEntries())) {
            board.scoreboard().resetScores(entry);
        }

        PlayerStatsManager.Stats stats = plugin.getStatsManager().getGlobal(player.getUniqueId());
        List<String> lines = List.of(
                ChatColor.DARK_GRAY + "────────────",
                icon(ArlightCoreIcons.PLAYERS) + ChatColor.WHITE + "Jugador: "
                        + ChatColor.LIGHT_PURPLE + player.getName(),
                ChatColor.GRAY + " ",
                icon(ArlightCoreIcons.STAR) + ChatColor.LIGHT_PURPLE + "Nivel del pase: " + ChatColor.WHITE
                        + plugin.getLevelManager().getLevel(player.getUniqueId()),
                icon(ArlightCoreIcons.TROPHY) + ChatColor.GOLD + "Victorias: "
                        + ChatColor.WHITE + stats.wins(),
                icon(ArlightCoreIcons.SWORDS) + ChatColor.AQUA + "Partidas: "
                        + ChatColor.WHITE + stats.played(),
                ChatColor.DARK_GRAY + "  ",
                icon(ArlightCoreIcons.INFO) + format(plugin.getConfig().getString(
                        "lobby-scoreboard.footer", "&eGracias &6holy.gg"))
        );

        int score = lines.size();
        for (String line : lines) objective.getScore(line).setScore(score--);
        boolean visualCoversVanilla = plugin.getConfig().getBoolean("visual-scoreboards.enabled", true)
                && plugin.getConfig().getBoolean("visual-scoreboards.cover-vanilla-sidebar", true)
                && plugin.getVisualScoreboardNetwork().supports(player);
        if (visualCoversVanilla) {
            if (player.getScoreboard() == board.scoreboard()) {
                ScoreboardManager manager = Bukkit.getScoreboardManager();
                if (manager != null) player.setScoreboard(manager.getMainScoreboard());
            }
        } else if (player.getScoreboard() != board.scoreboard()) {
            player.setScoreboard(board.scoreboard());
        }

        int xpInside = plugin.getLevelManager().getXpIntoCurrentLevel(player.getUniqueId());
        int xpPerLevel = plugin.getLevelManager().getXpPerLevel();
        double progress = xpPerLevel <= 0 ? 0.0D : Math.min(1.0D, xpInside / (double) xpPerLevel);
        visualOwners.add(player.getUniqueId());
        plugin.getVisualScoreboardNetwork().show(player, "lobby", "MINIJUEGOS",
                "Gracias holy.gg", progress, "Pase " + xpInside + "/" + xpPerLevel,
                List.of(
                        new VisualScoreboardNetwork.Line("\uE307", "Jugador", player.getName()),
                        new VisualScoreboardNetwork.Line("\uE308", "Nivel", String.valueOf(plugin.getLevelManager().getLevel(player.getUniqueId()))),
                        new VisualScoreboardNetwork.Line("\uE304", "Victorias", String.valueOf(stats.wins())),
                        new VisualScoreboardNetwork.Line("\uE306", "Partidas", String.valueOf(stats.played()))
                ));
    }

    private Board createBoard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return null;
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
                "arlight_lobby", "dummy", format(plugin.getConfig().getString(
                        "lobby-scoreboard.title", "&d&lMinijuegos :ponicalva:")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        return new Board(scoreboard, objective);
    }

    private void removeIfOwned(Player player) {
        Board board = boards.get(player.getUniqueId());
        if (board != null && player.getScoreboard() == board.scoreboard()) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) player.setScoreboard(manager.getMainScoreboard());
        }
        // Solo limpiamos el panel si el último panel enviado por ESTE manager era el del
        // lobby. Antes se enviaba CLEAR cada segundo mientras el jugador estaba en Bingo,
        // borrando el panel del minijuego y provocando desapariciones/parpadeos.
        if (visualOwners.remove(player.getUniqueId())
                && plugin.getVisualScoreboardNetwork() != null) {
            plugin.getVisualScoreboardNetwork().clear(player);
        }
    }

    /**
     * Libera inmediatamente el panel del lobby antes de que un minijuego publique el suyo.
     * Debe llamarse justo al comenzar una sesión, para que el siguiente ciclo periódico del
     * lobby no borre accidentalmente el panel de Bingo/SkyWars.
     */
    public void releaseForMinigame(Player player) {
        if (player == null) return;
        removeIfOwned(player);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String icon(String icon) {
        return plugin.getConfig().getBoolean("decorations.enabled", true) ? icon : "";
    }

    /** Sustituye los alias configurados en ArlightChat sin convertirlo en dependencia obligatoria. */
    private String format(String text) {
        String result = text == null ? "" : text;
        Plugin chat = Bukkit.getPluginManager().getPlugin("ArlightChat");
        if (chat instanceof JavaPlugin chatPlugin && chat.isEnabled()) {
            ConfigurationSection emotes = chatPlugin.getConfig().getConfigurationSection("emotes");
            if (emotes != null) {
                for (String id : emotes.getKeys(false)) {
                    String root = id + ".";
                    String character = decodeUnicode(emotes.getString(root + "character", ""));
                    if (character.isBlank()) continue;
                    for (String alias : emotes.getStringList(root + "aliases")) {
                        result = replaceIgnoreCase(result, alias, character);
                    }
                }
            }
        }
        return color(result);
    }

    private static String replaceIgnoreCase(String text, String search, String replacement) {
        if (search == null || search.isEmpty()) return text;
        StringBuilder result = new StringBuilder();
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerSearch = search.toLowerCase(Locale.ROOT);
        int from = 0;
        int index;
        while ((index = lowerText.indexOf(lowerSearch, from)) >= 0) {
            result.append(text, from, index).append(replacement);
            from = index + search.length();
        }
        return result.append(text.substring(from)).toString();
    }

    private static String decodeUnicode(String value) {
        if (value != null && value.startsWith("\\u") && value.length() == 6) {
            try {
                return Character.toString(Integer.parseInt(value.substring(2), 16));
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value == null ? "" : value;
    }

    private record Board(Scoreboard scoreboard, Objective objective) { }
}
