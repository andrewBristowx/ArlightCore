package com.arlight.core.api;

import com.arlight.core.reward.RewardManager;
import com.arlight.core.xp.PlayerLevelManager;
import com.arlight.core.registry.MinigameRegistry;
import org.bukkit.entity.Player;

/**
 * Punto de entrada estatico para que otros plugins (Bingo, SkyWars, etc.) interactuen
 * con ArlightCore: registrar su minijuego en el selector y otorgar XP al ganar.
 *
 * Antes de usar esta clase, el plugin llamador debe comprobar que ArlightCore este
 * instalado (ej. Bukkit.getPluginManager().getPlugin("ArlightCore") != null) para
 * evitar errores si no esta presente.
 */
public final class ArlightCoreAPI {

    private static MinigameRegistry registry;
    private static PlayerLevelManager levelManager;
    private static RewardManager rewardManager;
    private static int xpPerWin = 5;

    private ArlightCoreAPI() {
    }

    /** Uso interno del plugin ArlightCore -- no llamar desde otros plugins. */
    public static void init(MinigameRegistry registry, PlayerLevelManager levelManager, RewardManager rewardManager, int xpPerWin) {
        ArlightCoreAPI.registry = registry;
        ArlightCoreAPI.levelManager = levelManager;
        ArlightCoreAPI.rewardManager = rewardManager;
        ArlightCoreAPI.xpPerWin = xpPerWin;
    }

    public static void registerMinigame(MinigameProvider provider) {
        if (registry != null) registry.register(provider);
    }

    public static void unregisterMinigame(String id) {
        if (registry != null) registry.unregister(id);
    }

    /** Da la XP configurada por ganar un minijuego (por defecto 5, ver config.yml de ArlightCore). */
    public static void addWinXp(Player player) {
        if (levelManager != null) {
            levelManager.addXp(player.getUniqueId(), xpPerWin);
        }
    }

    public static void addXp(Player player, int amount) {
        if (levelManager != null) {
            levelManager.addXp(player.getUniqueId(), amount);
        }
    }

    public static int getLevel(Player player) {
        return levelManager != null ? levelManager.getLevel(player.getUniqueId()) : 0;
    }

    public static int getXp(Player player) {
        return levelManager != null ? levelManager.getXp(player.getUniqueId()) : 0;
    }
}
