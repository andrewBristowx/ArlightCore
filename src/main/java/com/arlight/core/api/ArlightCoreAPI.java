package com.arlight.core.api;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.reward.RewardManager;
import com.arlight.core.xp.PlayerLevelManager;
import com.arlight.core.registry.MinigameRegistry;
import com.arlight.core.items.CoreItems;
import com.arlight.core.session.MinigameSessionManager;
import com.arlight.core.stats.PlayerStatsManager;
import com.arlight.core.queue.MinigameQueueManager;
import com.arlight.core.network.VisualScoreboardNetwork;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
    private static MinigameSessionManager sessionManager;
    private static PlayerStatsManager statsManager;
    private static MinigameQueueManager queueManager;
    private static JavaPlugin plugin;
    private static int xpPerWin = 5;

    private ArlightCoreAPI() {
    }

    /** Uso interno del plugin ArlightCore -- no llamar desde otros plugins. */
    public static void init(MinigameRegistry registry, PlayerLevelManager levelManager,
                            RewardManager rewardManager, MinigameSessionManager sessionManager,
                            MinigameQueueManager queueManager, PlayerStatsManager statsManager,
                            JavaPlugin plugin, int xpPerWin) {
        ArlightCoreAPI.registry = registry;
        ArlightCoreAPI.levelManager = levelManager;
        ArlightCoreAPI.rewardManager = rewardManager;
        ArlightCoreAPI.sessionManager = sessionManager;
        ArlightCoreAPI.statsManager = statsManager;
        ArlightCoreAPI.queueManager = queueManager;
        ArlightCoreAPI.plugin = plugin;
        ArlightCoreAPI.xpPerWin = xpPerWin;
    }

    public static void registerMinigame(MinigameProvider provider) {
        if (registry != null) registry.register(provider);
    }

    public static void unregisterMinigame(String id) {
        if (queueManager != null) queueManager.clear(id);
        if (registry != null) registry.unregister(id);
    }

    /** Actualiza la recompensa de XP después de recargar config.yml. */
    public static void setXpPerWin(int amount) {
        xpPerWin = Math.max(0, amount);
    }

    /** Da la XP configurada por ganar un minijuego (por defecto 5, ver config.yml de ArlightCore). */
    public static void addWinXp(Player player) {
        String minigame = sessionManager == null ? null
                : sessionManager.getMinigameId(player.getUniqueId());
        int reward = plugin instanceof ArlightCorePlugin core
                ? core.getXpPerWin(minigame) : xpPerWin;
        if (levelManager != null) {
            levelManager.addXp(player.getUniqueId(), reward);
        }
        if (statsManager != null) {
            if (minigame != null) statsManager.recordWin(player.getUniqueId(), minigame);
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

    /** Guarda el inventario y reserva al jugador para un único minijuego. */
    public static boolean beginMinigameSession(Player player, String minigameId) {
        if (sessionManager == null) return false;
        if (plugin instanceof ArlightCorePlugin core && !core.isMinigameEnabled(minigameId)) return false;
        boolean started = sessionManager.begin(player, minigameId);
        if (started && plugin instanceof ArlightCorePlugin core
                && core.getLobbyScoreboardManager() != null) {
            // Se ejecuta antes de que el minijuego muestre su panel visual, evitando que el
            // refresco periódico del lobby lo borre un segundo después.
            core.getLobbyScoreboardManager().releaseForMinigame(player);
        }
        if (started && queueManager != null) queueManager.promote(player.getUniqueId());
        return started;
    }

    /** Registra una partida jugada cuando el minijuego comienza de verdad. */
    public static boolean markMinigameStarted(Player player) {
        if (player == null || sessionManager == null) return false;
        String minigameId = sessionManager.getMinigameId(player.getUniqueId());
        if (minigameId == null || !sessionManager.markStarted(player.getUniqueId())) return false;
        if (statsManager != null) statsManager.recordPlayed(player.getUniqueId(), minigameId);
        return true;
    }

    /** Añade al jugador a la cola exclusiva del minijuego y llama a provider.join(). */
    public static boolean joinQueue(Player player, String minigameId) {
        if (registry == null || queueManager == null) return false;
        MinigameProvider provider = registry.get(minigameId);
        return provider != null && queueManager.join(player, provider);
    }

    public static boolean leaveQueue(Player player) {
        return queueManager != null && queueManager.leave(player, true);
    }

    public static boolean isQueued(Player player) {
        return queueManager != null && player != null && queueManager.isQueued(player.getUniqueId());
    }

    public static String getQueuedMinigame(Player player) {
        return queueManager == null || player == null ? null
                : queueManager.getQueuedMinigame(player.getUniqueId());
    }

    public static int getQueueSize(String minigameId) {
        return queueManager == null ? 0 : queueManager.size(minigameId);
    }

    /** Permite a los minijuegos consultar el interruptor central antes de mostrar sus propios mensajes. */
    public static boolean isMinigameEnabled(String minigameId) {
        return !(plugin instanceof ArlightCorePlugin core) || core.isMinigameEnabled(minigameId);
    }

    /** Finaliza la sesión y restaura el inventario que tenía antes de entrar. */
    public static boolean endMinigameSession(Player player) {
        String minigame = sessionManager == null ? null : sessionManager.getMinigameId(player.getUniqueId());
        boolean actuallyPlayed = sessionManager != null && sessionManager.hasStarted(player.getUniqueId());
        boolean restored = sessionManager != null && sessionManager.end(player, true);
        if (queueManager != null && player != null) queueManager.remove(player.getUniqueId());
        if (restored && actuallyPlayed && statsManager != null && minigame != null) {
            statsManager.recordCompleted(player.getUniqueId(), minigame);
        }
        if (restored && plugin != null) CoreItems.giveIfMissing(plugin, player);
        return restored;
    }

    /**
     * Cancela una sesión antes de que la partida haya comenzado. Restaura el
     * inventario, elimina cualquier cola residual y no suma una partida
     * completada ni un abandono.
     */
    public static boolean cancelMinigameSession(Player player) {
        if (player == null) return false;
        boolean restored = sessionManager != null && sessionManager.end(player, true);
        if (queueManager != null) queueManager.remove(player.getUniqueId());
        if (restored && plugin != null) CoreItems.giveIfMissing(plugin, player);
        return restored;
    }

    public static boolean isInMinigame(Player player) {
        return sessionManager != null && sessionManager.hasSession(player.getUniqueId());
    }

    public static String getCurrentMinigame(Player player) {
        return sessionManager == null ? null : sessionManager.getMinigameId(player.getUniqueId());
    }

    public static void giveCoreItems(Player player) {
        if (plugin != null) CoreItems.giveIfMissing(plugin, player);
    }

    /** Muestra un scoreboard visual avanzado en clientes con ArlightChatClient. */
    public static void showVisualScoreboard(Player player, String style, String title,
                                            String footer, double progress, String progressText,
                                            List<VisualScoreboardNetwork.Line> lines) {
        if (plugin instanceof ArlightCorePlugin core && core.getVisualScoreboardNetwork() != null) {
            core.getVisualScoreboardNetwork().show(player, style, title, footer,
                    progress, progressText, lines);
        }
    }

    public static VisualScoreboardNetwork.Line visualLine(String icon, String label, String value) {
        return new VisualScoreboardNetwork.Line(icon, label, value);
    }

    public static boolean supportsVisualScoreboard(Player player) {
        return plugin instanceof ArlightCorePlugin core
                && core.getVisualScoreboardNetwork() != null
                && core.getConfig().getBoolean("visual-scoreboards.enabled", true)
                && core.getVisualScoreboardNetwork().supports(player);
    }

    /** Indica si el cliente visual debe sustituir al sidebar Bukkit para evitar superposición. */
    public static boolean shouldCoverVanillaSidebar(Player player) {
        return supportsVisualScoreboard(player)
                && plugin instanceof ArlightCorePlugin core
                && core.getConfig().getBoolean("visual-scoreboards.cover-vanilla-sidebar", true);
    }

    /** Oculta el panel visual. El scoreboard Bukkit de respaldo se controla por separado. */
    public static void clearVisualScoreboard(Player player) {
        if (plugin instanceof ArlightCorePlugin core && core.getVisualScoreboardNetwork() != null) {
            core.getVisualScoreboardNetwork().clear(player);
        }
    }

    /** Muestra el podio visual universal a un jugador. */
    public static boolean showUniversalPodium(Player player, UniversalPodiumResult result) {
        if (!(plugin instanceof ArlightCorePlugin core) || core.getUniversalPodiumNetwork() == null) return false;
        if (!core.getConfig().getBoolean("universal-podium.enabled", true)) return false;
        core.getUniversalPodiumNetwork().show(player, result);
        return true;
    }

    public static boolean supportsUniversalPodium(Player player) {
        return plugin instanceof ArlightCorePlugin core
                && core.getUniversalPodiumNetwork() != null
                && core.getUniversalPodiumNetwork().supports(player);
    }

    public static void hideUniversalPodium(Player player) {
        if (plugin instanceof ArlightCorePlugin core && core.getUniversalPodiumNetwork() != null) {
            core.getUniversalPodiumNetwork().hide(player);
        }
    }

    /** Teletransporta al spawn configurado de legos sin tocar la sesión. */
    public static boolean teleportToLobby(Player player) {
        if (player == null || !(plugin instanceof ArlightCorePlugin core)) return false;
        org.bukkit.Location destination = core.getLobbySpawn();
        return destination != null && player.teleport(destination);
    }

    /** True cuando Multiverse-Inventories administra los perfiles entre mundos. */
    public static boolean isExternalInventoryManagementActive() {
        return plugin instanceof ArlightCorePlugin core
                && core.getMultiverseIntegrationManager() != null
                && core.getMultiverseIntegrationManager().isInventoryManagementActive();
    }

    /** True cuando ArlightCore administra los enlaces de Nether/End con MV-NetherPortals. */
    public static boolean isMultiverseNetherPortalsActive() {
        return plugin instanceof ArlightCorePlugin core
                && core.getMultiverseIntegrationManager() != null
                && core.getMultiverseIntegrationManager().isNetherPortalManagementActive();
    }

    /**
     * Registra un trío de mundos de minijuego en Multiverse-Inventories y enlaza sus
     * portales con Multiverse-NetherPortals. Devuelve false si una integración requerida falla.
     */
    public static boolean configureInventoryGroup(String groupName, String overworld,
                                                  String nether, String end) {
        return plugin instanceof ArlightCorePlugin core
                && core.getMultiverseIntegrationManager() != null
                && core.getMultiverseIntegrationManager().configureInventoryGroup(
                        groupName, overworld, nether, end);
    }

    public static boolean configurePortalLinks(String overworld, String nether, String end) {
        return plugin instanceof ArlightCorePlugin core
                && core.getMultiverseIntegrationManager() != null
                && core.getMultiverseIntegrationManager().configurePortalLinks(overworld, nether, end);
    }

    public static boolean configureMinigameWorlds(String groupName, String overworld,
                                                  String nether, String end) {
        return plugin instanceof ArlightCorePlugin core
                && core.getMultiverseIntegrationManager() != null
                && core.getMultiverseIntegrationManager().configureMinigameWorlds(
                        groupName, overworld, nether, end);
    }

    /**
     * Configura de forma escalonada e idempotente el trío de mundos de un minijuego.
     * El futuro termina únicamente cuando Multiverse-Core, Inventories y NetherPortals
     * han finalizado su parte, evitando mover jugadores durante la sincronización.
     */
    public static CompletableFuture<Boolean> configureMinigameWorldsStable(String groupName,
                                                                           String overworld,
                                                                           String nether,
                                                                           String end) {
        if (!(plugin instanceof ArlightCorePlugin core)
                || core.getMultiverseIntegrationManager() == null) {
            return CompletableFuture.completedFuture(false);
        }
        return core.getMultiverseIntegrationManager().configureMinigameWorldsStable(
                groupName, overworld, nether, end);
    }

}
