package com.arlight.core;

import com.arlight.core.api.ArlightCoreAPI;
import com.arlight.core.commands.CoreCommand;
import com.arlight.core.listeners.CoreGUIListener;
import com.arlight.core.listeners.CoreItemListener;
import com.arlight.core.registry.MinigameRegistry;
import com.arlight.core.reward.RewardManager;
import com.arlight.core.xp.PlayerLevelManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class ArlightCorePlugin extends JavaPlugin {

    private MinigameRegistry minigameRegistry;
    private PlayerLevelManager levelManager;
    private RewardManager rewardManager;

    private int xpPerWin;
    private boolean giveItemsOnJoin;
    private List<String> claimWorlds = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.minigameRegistry = new MinigameRegistry();
        this.levelManager = new PlayerLevelManager(this);
        this.rewardManager = new RewardManager(this);

        loadCoreConfigValues();
        levelManager.load();
        rewardManager.load();

        ArlightCoreAPI.init(minigameRegistry, levelManager, rewardManager, xpPerWin);

        getServer().getPluginManager().registerEvents(new CoreItemListener(this), this);
        getServer().getPluginManager().registerEvents(new CoreGUIListener(this), this);
        CoreCommand coreCommand = new CoreCommand(this);
        getCommand("core").setExecutor(coreCommand);
        getCommand("core").setTabCompleter(coreCommand);

        getLogger().info("ArlightCore habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        if (levelManager != null) levelManager.save();
        if (rewardManager != null) rewardManager.save();
        getLogger().info("ArlightCore deshabilitado.");
    }

    public void reloadCoreConfig() {
        reloadConfig();
        loadCoreConfigValues();
    }

    private void loadCoreConfigValues() {
        this.xpPerWin = getConfig().getInt("xp.xp-per-win", 5);
        int xpPerLevel = getConfig().getInt("xp.xp-per-level", 30);
        this.giveItemsOnJoin = getConfig().getBoolean("give-items-on-join", true);
        this.claimWorlds = getConfig().getStringList("claim-worlds");
        if (levelManager != null) {
            levelManager.setXpPerLevel(xpPerLevel);
        }
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

    public boolean isGiveItemsOnJoin() {
        return giveItemsOnJoin;
    }

    public List<String> getClaimWorlds() {
        return claimWorlds;
    }
}
