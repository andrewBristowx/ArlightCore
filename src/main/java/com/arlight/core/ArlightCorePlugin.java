package com.arlight.core;

import com.arlight.core.api.ArlightCoreAPI;
import com.arlight.core.commands.CoreCommand;
import com.arlight.core.listeners.CoreGUIListener;
import com.arlight.core.listeners.CoreItemListener;
import com.arlight.core.listeners.MinigameSessionListener;
import com.arlight.core.listeners.LobbyProtectionListener;
import com.arlight.core.leaderboard.LeaderboardManager;
import com.arlight.core.hologram.HologramManager;
import com.arlight.core.queue.MinigameQueueManager;
import com.arlight.core.placeholders.ArlightCoreExpansion;
import com.arlight.core.registry.MinigameRegistry;
import com.arlight.core.reward.RewardManager;
import com.arlight.core.session.MinigameSessionManager;
import com.arlight.core.stats.PlayerStatsManager;
import com.arlight.core.xp.PlayerLevelManager;
import com.arlight.core.database.DatabaseManager;
import com.arlight.core.audit.AuditLogger;
import com.arlight.core.lobby.LobbyScoreboardManager;
import com.arlight.core.integration.MultiverseIntegrationManager;
import com.arlight.core.network.VisualScoreboardNetwork;
import com.arlight.core.network.UniversalPodiumNetwork;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ArlightCorePlugin extends JavaPlugin {

    private MinigameRegistry minigameRegistry;
    private PlayerLevelManager levelManager;
    private RewardManager rewardManager;
    private MinigameSessionManager sessionManager;
    private PlayerStatsManager statsManager;
    private LeaderboardManager leaderboardManager;
    private HologramManager hologramManager;
    private MinigameQueueManager queueManager;
    private DatabaseManager databaseManager;
    private AuditLogger auditLogger;
    private LobbyScoreboardManager lobbyScoreboardManager;
    private MultiverseIntegrationManager multiverseIntegrationManager;
    private VisualScoreboardNetwork visualScoreboardNetwork;
    private UniversalPodiumNetwork universalPodiumNetwork;

    private int xpPerWin;
    private final Map<String, Integer> xpPerMinigame = new HashMap<>();
    private boolean giveItemsOnJoin;
    private String lobbyWorld;
    private boolean teleportToLobbyOnJoin;
    private double lobbyVoidY;
    private boolean lobbyBlockProtection;
    private List<String> claimWorlds = new ArrayList<>();
    private boolean minigamesEnabled;
    private final Set<String> disabledMinigames = new LinkedHashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        this.visualScoreboardNetwork = new VisualScoreboardNetwork(this);
        this.universalPodiumNetwork = new UniversalPodiumNetwork(this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, VisualScoreboardNetwork.CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, UniversalPodiumNetwork.CHANNEL);

        this.databaseManager = new DatabaseManager(this);
        this.auditLogger = new AuditLogger(this);
        this.minigameRegistry = new MinigameRegistry();
        this.levelManager = new PlayerLevelManager(this, databaseManager);
        this.rewardManager = new RewardManager(this);
        this.multiverseIntegrationManager = new MultiverseIntegrationManager(this);
        this.sessionManager = new MinigameSessionManager(this,
                () -> multiverseIntegrationManager != null
                        && multiverseIntegrationManager.isInventoryManagementActive());
        this.queueManager = new MinigameQueueManager(this);
        this.statsManager = new PlayerStatsManager(this, databaseManager);
        this.leaderboardManager = new LeaderboardManager(this);
        this.hologramManager = new HologramManager(this);
        this.lobbyScoreboardManager = new LobbyScoreboardManager(this);

        loadCoreConfigValues();
        levelManager.load();
        rewardManager.load();
        sessionManager.load();
        statsManager.load();

        ArlightCoreAPI.init(minigameRegistry, levelManager, rewardManager, sessionManager,
                queueManager, statsManager, this, xpPerWin);

        getServer().getPluginManager().registerEvents(new CoreItemListener(this), this);
        getServer().getPluginManager().registerEvents(new MinigameSessionListener(this), this);
        getServer().getPluginManager().registerEvents(new CoreGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(hologramManager, this);
        CoreCommand coreCommand = new CoreCommand(this);
        getCommand("core").setExecutor(coreCommand);
        getCommand("core").setTabCompleter(coreCommand);
        leaderboardManager.start();
        hologramManager.start();
        lobbyScoreboardManager.start();
        multiverseIntegrationManager.start();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            boolean registered = new ArlightCoreExpansion(this).register();
            if (registered) {
                getLogger().info("Placeholders de ArlightCore registrados en PlaceholderAPI.");
            } else {
                getLogger().warning("PlaceholderAPI esta activo, pero no se pudieron registrar los placeholders.");
            }
        } else {
            getLogger().info("PlaceholderAPI no esta instalado; la integracion de placeholders queda desactivada.");
        }

        getLogger().info("ArlightCore habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        if (levelManager != null) levelManager.save();
        if (rewardManager != null) rewardManager.save();
        if (sessionManager != null) sessionManager.save();
        if (statsManager != null) statsManager.save();
        if (leaderboardManager != null) leaderboardManager.stop();
        if (hologramManager != null) hologramManager.stop();
        if (lobbyScoreboardManager != null) lobbyScoreboardManager.stop();
        if (databaseManager != null) databaseManager.flushAndClose();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, VisualScoreboardNetwork.CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, UniversalPodiumNetwork.CHANNEL);
        getLogger().info("ArlightCore deshabilitado.");
    }

    public void reloadCoreConfig() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadCoreConfigValues();
        if (lobbyScoreboardManager != null) lobbyScoreboardManager.restart();
        if (multiverseIntegrationManager != null) multiverseIntegrationManager.reloadAndSync();
    }

    private void loadCoreConfigValues() {
        // Migra solo el título predeterminado anterior; respeta títulos personalizados.
        if ("&d&lMinijuegos".equals(getConfig().getString("lobby-scoreboard.title"))) {
            getConfig().set("lobby-scoreboard.title", "&d&lMinijuegos :ponicalva:");
            saveConfig();
        }
        this.xpPerWin = Math.max(0, getConfig().getInt("xp.xp-per-win", 5));
        this.xpPerMinigame.clear();
        org.bukkit.configuration.ConfigurationSection perGame =
                getConfig().getConfigurationSection("xp.per-minigame");
        if (perGame != null) {
            for (String id : perGame.getKeys(false)) {
                xpPerMinigame.put(id.trim().toLowerCase(Locale.ROOT),
                        Math.max(0, perGame.getInt(id, xpPerWin)));
            }
        }
        int xpPerLevel = getConfig().getInt("xp.xp-per-level", 30);
        this.giveItemsOnJoin = getConfig().getBoolean("give-items-on-join", true);
        this.lobbyWorld = getConfig().getString("lobby-world", "legos");
        this.teleportToLobbyOnJoin = getConfig().getBoolean("lobby.teleport-on-join", true);
        this.lobbyVoidY = getConfig().getDouble("lobby.void-rescue-y", -10.0);
        this.lobbyBlockProtection = getConfig().getBoolean("lobby.protect-blocks", true);
        this.claimWorlds = getConfig().getStringList("claim-worlds");
        this.minigamesEnabled = getConfig().getBoolean("minigames.enabled", true);
        this.disabledMinigames.clear();
        getConfig().getStringList("minigames.disabled").stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank() && !value.equals("all"))
                .forEach(disabledMinigames::add);
        if (levelManager != null) {
            levelManager.setXpPerLevel(xpPerLevel);
        }
        ArlightCoreAPI.setXpPerWin(xpPerWin);
    }

    public MinigameRegistry getMinigameRegistry() {
        return minigameRegistry;
    }

    public PlayerLevelManager getLevelManager() {
        return levelManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public MinigameSessionManager getSessionManager() {
        return sessionManager;
    }

    public PlayerStatsManager getStatsManager() {
        return statsManager;
    }

    public boolean isGiveItemsOnJoin() {
        return giveItemsOnJoin;
    }

    public List<String> getClaimWorlds() {
        return claimWorlds;
    }

    public String getLobbyWorld() {
        return lobbyWorld;
    }

    public boolean isTeleportToLobbyOnJoin() {
        return teleportToLobbyOnJoin;
    }

    public double getLobbyVoidY() {
        return lobbyVoidY;
    }

    public boolean isLobbyBlockProtectionEnabled() {
        return lobbyBlockProtection;
    }

    public Location getLobbySpawn() {
        World world = Bukkit.getWorld(lobbyWorld);
        if (world == null) return null;
        if (!getConfig().isSet("lobby.spawn.x")) return world.getSpawnLocation();
        return new Location(world,
                getConfig().getDouble("lobby.spawn.x"),
                getConfig().getDouble("lobby.spawn.y"),
                getConfig().getDouble("lobby.spawn.z"),
                (float) getConfig().getDouble("lobby.spawn.yaw"),
                (float) getConfig().getDouble("lobby.spawn.pitch"));
    }

    public void setLobbySpawn(Location location) {
        if (location.getWorld() == null) return;
        this.lobbyWorld = location.getWorld().getName();
        getConfig().set("lobby-world", lobbyWorld);
        getConfig().set("lobby.spawn.x", location.getX());
        getConfig().set("lobby.spawn.y", location.getY());
        getConfig().set("lobby.spawn.z", location.getZ());
        getConfig().set("lobby.spawn.yaw", location.getYaw());
        getConfig().set("lobby.spawn.pitch", location.getPitch());
        saveConfig();
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public MinigameQueueManager getQueueManager() {
        return queueManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuditLogger getAuditLogger() {
        return auditLogger;
    }

    public LobbyScoreboardManager getLobbyScoreboardManager() {
        return lobbyScoreboardManager;
    }

    public MultiverseIntegrationManager getMultiverseIntegrationManager() {
        return multiverseIntegrationManager;
    }

    public VisualScoreboardNetwork getVisualScoreboardNetwork() {
        return visualScoreboardNetwork;
    }

    public UniversalPodiumNetwork getUniversalPodiumNetwork() {
        return universalPodiumNetwork;
    }

    public int getXpPerWin(String minigameId) {
        if (minigameId == null || minigameId.isBlank()) return xpPerWin;
        return xpPerMinigame.getOrDefault(
                minigameId.trim().toLowerCase(Locale.ROOT), xpPerWin);
    }

    public boolean areMinigamesEnabled() {
        return minigamesEnabled;
    }

    public boolean isMinigameEnabled(String id) {
        if (!minigamesEnabled || id == null || id.isBlank()) return false;
        return !disabledMinigames.contains(id.trim().toLowerCase(Locale.ROOT));
    }

    public Set<String> getDisabledMinigames() {
        return Set.copyOf(disabledMinigames);
    }

    public void setAllMinigamesEnabled(boolean enabled) {
        this.minigamesEnabled = enabled;
        getConfig().set("minigames.enabled", enabled);
        saveConfig();
    }

    public void setMinigameEnabled(String id, boolean enabled) {
        if (id == null || id.isBlank()) return;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (enabled) disabledMinigames.remove(normalized);
        else disabledMinigames.add(normalized);
        getConfig().set("minigames.disabled", new ArrayList<>(disabledMinigames));
        saveConfig();
    }
}
