package com.arlight.core.api;

import java.util.List;
import java.util.UUID;

/** Un puesto individual del podio. Solo se usan los puestos 1, 2 y 3. */
public record PodiumEntry(int place, UUID uuid, String name, String pose, List<PodiumStat> stats) {
    public PodiumEntry {
        place = Math.max(1, Math.min(3, place));
        name = name == null || name.isBlank() ? "Jugador" : name;
        pose = pose == null || pose.isBlank() ? defaultPose(place) : pose;
        stats = stats == null ? List.of() : List.copyOf(stats);
    }

    private static String defaultPose(int place) {
        return switch (place) {
            case 1 -> "CHAMPION";
            case 2 -> "WAVE";
            default -> "PROUD";
        };
    }
}
