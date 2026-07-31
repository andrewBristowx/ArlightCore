package com.arlight.core.api;

/** Una estadística visual mostrada en la pantalla universal de resultados. */
public record PodiumStat(String icon, String label, String value) {
    public PodiumStat {
        icon = icon == null ? "" : icon;
        label = label == null ? "" : label;
        value = value == null ? "" : value;
    }
}
