package com.arlight.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/** Conexión MySQL/MariaDB y persistencia de los datos permanentes del Core. */
public final class DatabaseManager {

    public record LevelData(int xp, Set<Integer> claimedLevels) { }
    public record StatsData(int played, int wins, int losses, int abandons,
                            int currentStreak, int bestStreak) { }

    private final JavaPlugin plugin;
    private final boolean requested;
    private HikariDataSource dataSource;
    private ExecutorService writer;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.requested = config.getBoolean("database.enabled", false);
        if (!requested) return;

        String host = config.getString("database.host", "127.0.0.1");
        int port = config.getInt("database.port", 3306);
        String database = config.getString("database.name", "arlightcore");
        String username = config.getString("database.username", "");
        String password = config.getString("database.password", "");
        boolean ssl = config.getBoolean("database.use-ssl", false);

        try {
            HikariConfig hikari = new HikariConfig();
            hikari.setPoolName("ArlightCore-MySQL");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            hikari.setUsername(username);
            hikari.setPassword(password);
            hikari.setMaximumPoolSize(Math.max(2, config.getInt("database.pool-size", 4)));
            hikari.setMinimumIdle(1);
            hikari.setConnectionTimeout(10_000L);
            hikari.setInitializationFailTimeout(10_000L);
            dataSource = new HikariDataSource(hikari);
            writer = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ArlightCore-Database");
                thread.setDaemon(true);
                return thread;
            });
            createTables();
            plugin.getLogger().info("Conexión MySQL de ArlightCore establecida correctamente.");
        } catch (Exception error) {
            plugin.getLogger().log(Level.SEVERE,
                    "No se pudo conectar a MySQL. ArlightCore continuará usando archivos YAML.", error);
            close();
        }
    }

    public boolean isEnabled() {
        return dataSource != null && !dataSource.isClosed();
    }

    private void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS arlight_players ("
                    + "uuid CHAR(36) PRIMARY KEY, xp INT NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS arlight_claimed_rewards ("
                    + "uuid CHAR(36) NOT NULL, level INT NOT NULL, PRIMARY KEY (uuid, level))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS arlight_stats ("
                    + "uuid CHAR(36) NOT NULL, minigame VARCHAR(64) NOT NULL, played INT NOT NULL DEFAULT 0, "
                    + "wins INT NOT NULL DEFAULT 0, losses INT NOT NULL DEFAULT 0, abandons INT NOT NULL DEFAULT 0, "
                    + "current_streak INT NOT NULL DEFAULT 0, best_streak INT NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (uuid, minigame))");
        }
    }

    public Map<UUID, LevelData> loadLevels() {
        Map<UUID, Integer> xp = new HashMap<>();
        Map<UUID, Set<Integer>> claimed = new HashMap<>();
        if (!isEnabled()) return Map.of();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT uuid, xp FROM arlight_players")) {
                while (rows.next()) xp.put(UUID.fromString(rows.getString(1)), rows.getInt(2));
            }
            try (ResultSet rows = statement.executeQuery("SELECT uuid, level FROM arlight_claimed_rewards")) {
                while (rows.next()) claimed.computeIfAbsent(UUID.fromString(rows.getString(1)), ignored -> new HashSet<>())
                        .add(rows.getInt(2));
            }
        } catch (Exception error) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron cargar niveles desde MySQL.", error);
            return Map.of();
        }
        Map<UUID, LevelData> result = new HashMap<>();
        Set<UUID> uuids = new HashSet<>(xp.keySet());
        uuids.addAll(claimed.keySet());
        for (UUID uuid : uuids) result.put(uuid,
                new LevelData(xp.getOrDefault(uuid, 0), Set.copyOf(claimed.getOrDefault(uuid, Set.of()))));
        return result;
    }

    public Map<UUID, Map<String, StatsData>> loadStats() {
        Map<UUID, Map<String, StatsData>> result = new HashMap<>();
        if (!isEnabled()) return result;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT uuid, minigame, played, wins, losses, abandons, current_streak, best_streak FROM arlight_stats")) {
            while (rows.next()) {
                UUID uuid = UUID.fromString(rows.getString(1));
                result.computeIfAbsent(uuid, ignored -> new HashMap<>()).put(rows.getString(2),
                        new StatsData(rows.getInt(3), rows.getInt(4), rows.getInt(5), rows.getInt(6),
                                rows.getInt(7), rows.getInt(8)));
            }
        } catch (Exception error) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron cargar estadísticas desde MySQL.", error);
        }
        return result;
    }

    public void saveLevels(Map<UUID, Integer> xp, Map<UUID, Set<Integer>> claimed) {
        if (!isEnabled() || writer == null) return;
        Map<UUID, Integer> xpCopy = new HashMap<>(xp);
        Map<UUID, Set<Integer>> claimedCopy = new HashMap<>();
        claimed.forEach((uuid, levels) -> claimedCopy.put(uuid, new HashSet<>(levels)));
        writer.execute(() -> replaceLevels(xpCopy, claimedCopy));
    }

    private void replaceLevels(Map<UUID, Integer> xp, Map<UUID, Set<Integer>> claimed) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement clear = connection.createStatement()) {
                clear.executeUpdate("DELETE FROM arlight_claimed_rewards");
                clear.executeUpdate("DELETE FROM arlight_players");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO arlight_players(uuid, xp) VALUES (?, ?)")) {
                for (Map.Entry<UUID, Integer> entry : xp.entrySet()) {
                    insert.setString(1, entry.getKey().toString()); insert.setInt(2, entry.getValue()); insert.addBatch();
                }
                insert.executeBatch();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO arlight_claimed_rewards(uuid, level) VALUES (?, ?)")) {
                for (Map.Entry<UUID, Set<Integer>> entry : claimed.entrySet()) for (int level : entry.getValue()) {
                    insert.setString(1, entry.getKey().toString()); insert.setInt(2, level); insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException error) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron guardar niveles en MySQL.", error);
        }
    }

    public void saveStats(Map<UUID, Map<String, StatsData>> stats) {
        if (!isEnabled() || writer == null) return;
        Map<UUID, Map<String, StatsData>> copy = new HashMap<>();
        stats.forEach((uuid, games) -> copy.put(uuid, new HashMap<>(games)));
        writer.execute(() -> replaceStats(copy));
    }

    private void replaceStats(Map<UUID, Map<String, StatsData>> stats) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement clear = connection.createStatement()) { clear.executeUpdate("DELETE FROM arlight_stats"); }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO arlight_stats(uuid,minigame,played,wins,losses,abandons,current_streak,best_streak) VALUES (?,?,?,?,?,?,?,?)")) {
                for (Map.Entry<UUID, Map<String, StatsData>> player : stats.entrySet()) {
                    for (Map.Entry<String, StatsData> game : player.getValue().entrySet()) {
                        StatsData value = game.getValue();
                        insert.setString(1, player.getKey().toString()); insert.setString(2, game.getKey());
                        insert.setInt(3, value.played()); insert.setInt(4, value.wins());
                        insert.setInt(5, value.losses()); insert.setInt(6, value.abandons());
                        insert.setInt(7, value.currentStreak()); insert.setInt(8, value.bestStreak()); insert.addBatch();
                    }
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException error) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron guardar estadísticas en MySQL.", error);
        }
    }

    public void flushAndClose() {
        if (writer != null) {
            writer.shutdown();
            try { writer.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        close();
    }

    private void close() {
        if (dataSource != null) dataSource.close();
        dataSource = null;
    }
}
