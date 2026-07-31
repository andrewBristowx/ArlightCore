package com.arlight.core.placeholders;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.api.MinigameProvider;
import com.arlight.core.stats.PlayerStatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/** Placeholders internos de ArlightCore para LPC, TAB, hologramas y otros plugins. */
public final class ArlightCoreExpansion extends PlaceholderExpansion {

    private final ArlightCorePlugin plugin;

    public ArlightCoreExpansion(ArlightCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "arlightcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Arlight";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Evita que /papi reload elimine esta expansión, pues viene incluida en el Core. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if (player == null) return "";

        UUID uuid = player.getUniqueId();
        int xp = plugin.getLevelManager().getXp(uuid);
        int level = plugin.getLevelManager().getLevel(uuid);
        int progress = plugin.getLevelManager().getXpIntoCurrentLevel(uuid);
        int required = plugin.getLevelManager().getXpPerLevel();
        int remaining = progress == 0 ? required : required - progress;
        PlayerStatsManager.Stats stats = plugin.getStatsManager().getGlobal(uuid);
        String minigameId = plugin.getSessionManager().getMinigameId(uuid);
        if (minigameId == null) {
            minigameId = plugin.getQueueManager().getQueuedMinigame(uuid);
        }
        String minigameName = resolveMinigameName(minigameId);

        return switch (identifier.toLowerCase(Locale.ROOT)) {
            case "level" -> Integer.toString(level);
            case "level_tag" -> ChatColor.LIGHT_PURPLE + "[Nivel " + level + "]" + ChatColor.RESET;
            case "xp" -> Integer.toString(xp);
            case "xp_progress" -> Integer.toString(progress);
            case "xp_per_level" -> Integer.toString(required);
            case "xp_to_next_level" -> Integer.toString(remaining);
            case "wins" -> Integer.toString(stats.wins());
            case "played" -> Integer.toString(stats.played());
            case "losses" -> Integer.toString(stats.losses());
            case "abandons" -> Integer.toString(stats.abandons());
            case "win_rate" -> String.format(Locale.US, "%.1f", stats.winRate());
            case "minigame" -> minigameName;
            case "minigame_tag" -> minigameName.isEmpty() ? ""
                    : ChatColor.AQUA + "[" + minigameName + ChatColor.AQUA + "] " + ChatColor.RESET;
            default -> null;
        };
    }

    private String resolveMinigameName(String minigameId) {
        if (minigameId == null || minigameId.isBlank()) return "";
        MinigameProvider provider = plugin.getMinigameRegistry().get(minigameId);
        if (provider == null || provider.getDisplayName() == null || provider.getDisplayName().isBlank()) {
            return minigameId;
        }
        return provider.getDisplayName();
    }
}
