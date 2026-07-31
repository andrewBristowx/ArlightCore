package com.arlight.core.leaderboard;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.stats.PlayerStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Textos flotantes configurables para niveles del minipase y victorias globales. */
public final class LeaderboardManager {
    private record Board(String id, String type, String world, double x, double y, double z) { }

    private final ArlightCorePlugin plugin;
    private final File file;
    private final NamespacedKey marker;
    private final Map<String, Board> boards = new HashMap<>();
    private final List<UUID> spawned = new ArrayList<>();

    public LeaderboardManager(ArlightCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "leaderboards.yml");
        this.marker = new NamespacedKey(plugin, "leaderboard-display");
    }

    public void start() {
        load();
        refresh();
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 200L, 200L);
    }

    public void stop() {
        clearSpawned();
    }

    public String create(String type, Location location) {
        String normalized = normalizeType(type);
        if (normalized == null || location.getWorld() == null) return null;
        int number = 1;
        while (boards.containsKey(normalized + "-" + number)) number++;
        String id = normalized + "-" + number;
        boards.put(id, new Board(id, normalized, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ()));
        save();
        refresh();
        return id;
    }

    public boolean remove(String id) {
        boolean removed = boards.remove(id.toLowerCase()) != null;
        if (removed) {
            save();
            refresh();
        }
        return removed;
    }

    public List<String> ids() {
        return boards.keySet().stream().sorted().toList();
    }

    private String normalizeType(String type) {
        if (type == null) return null;
        if (type.equalsIgnoreCase("levels") || type.equalsIgnoreCase("nivel")) return "levels";
        if (type.equalsIgnoreCase("wins") || type.equalsIgnoreCase("victorias")) return "wins";
        return null;
    }

    public void refresh() {
        clearSpawned();
        removeOrphanDisplays();
        for (Board board : boards.values()) spawnBoard(board);
    }

    private void spawnBoard(Board board) {
        World world = Bukkit.getWorld(board.world());
        if (world == null) return;
        List<String> lines = board.type().equals("levels") ? levelLines() : winLines();
        Location base = new Location(world, board.x(), board.y(), board.z());
        for (int index = 0; index < lines.size(); index++) {
            int lineIndex = index;
            Location lineLocation = base.clone().add(0, (lines.size() - index - 1) * 0.27, 0);
            TextDisplay display = world.spawn(lineLocation, TextDisplay.class, entity -> {
                entity.setText(lines.get(lineIndex));
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setSeeThrough(true);
                entity.setShadowed(true);
                entity.setPersistent(false);
                entity.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
            });
            spawned.add(display.getUniqueId());
        }
    }

    private List<String> levelLines() {
        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>(plugin.getLevelManager().getAllXp().entrySet());
        ranking.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "TOP NIVEL DEL MINIPASE");
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            UUID uuid = ranking.get(i).getKey();
            lines.add(position(i) + playerName(uuid) + ChatColor.GRAY + " - Nivel "
                    + ChatColor.WHITE + plugin.getLevelManager().getLevel(uuid));
        }
        if (ranking.isEmpty()) lines.add(ChatColor.GRAY + "Todavía no hay datos");
        return lines;
    }

    private List<String> winLines() {
        List<UUID> ranking = new ArrayList<>(plugin.getStatsManager().getKnownPlayers());
        ranking.sort(Comparator.comparingInt((UUID uuid) ->
                plugin.getStatsManager().getGlobal(uuid).wins()).reversed());
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "TOP VICTORIAS GLOBALES");
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            UUID uuid = ranking.get(i);
            PlayerStatsManager.Stats stats = plugin.getStatsManager().getGlobal(uuid);
            lines.add(position(i) + playerName(uuid) + ChatColor.GRAY + " - "
                    + ChatColor.WHITE + stats.wins() + " victorias");
        }
        if (ranking.isEmpty()) lines.add(ChatColor.GRAY + "Todavía no hay datos");
        return lines;
    }

    private String position(int index) {
        ChatColor color = index == 0 ? ChatColor.GOLD : index == 1 ? ChatColor.GRAY
                : index == 2 ? ChatColor.RED : ChatColor.WHITE;
        return color + "#" + (index + 1) + " ";
    }

    private String playerName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString().substring(0, 8) : player.getName();
    }

    private void clearSpawned() {
        for (UUID uuid : spawned) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        }
        spawned.clear();
    }

    private void removeOrphanDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) display.remove();
            }
        }
    }

    private void load() {
        boards.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("leaderboards");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String base = id + ".";
            boards.put(id.toLowerCase(), new Board(id.toLowerCase(), section.getString(base + "type", "levels"),
                    section.getString(base + "world", plugin.getLobbyWorld()), section.getDouble(base + "x"),
                    section.getDouble(base + "y"), section.getDouble(base + "z")));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Board board : boards.values()) {
            String base = "leaderboards." + board.id() + ".";
            yaml.set(base + "type", board.type());
            yaml.set(base + "world", board.world());
            yaml.set(base + "x", board.x());
            yaml.set(base + "y", board.y());
            yaml.set(base + "z", board.z());
        }
        try {
            yaml.save(file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar leaderboards.yml", error);
        }
    }
}
