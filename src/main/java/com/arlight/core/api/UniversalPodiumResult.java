package com.arlight.core.api;

import java.util.List;

/** Datos independientes del cliente para una pantalla de podio individual. */
public record UniversalPodiumResult(
        String minigameId,
        String minigameName,
        String theme,
        String subtitle,
        int displayTicks,
        String requeueCommand,
        int viewerPlace,
        List<PodiumEntry> podium,
        List<PodiumStat> viewerStats
) {
    public UniversalPodiumResult {
        minigameId = clean(minigameId, "minigame");
        minigameName = clean(minigameName, "Resultados");
        theme = clean(theme, minigameId);
        subtitle = clean(subtitle, "Partida finalizada");
        displayTicks = Math.max(80, displayTicks);
        requeueCommand = requeueCommand == null ? "" : requeueCommand.strip().replaceFirst("^/", "");
        viewerPlace = Math.max(0, viewerPlace);
        podium = podium == null ? List.of() : podium.stream().limit(3).toList();
        viewerStats = viewerStats == null ? List.of() : List.copyOf(viewerStats);
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
