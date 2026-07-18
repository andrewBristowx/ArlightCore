package com.arlight.core.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Cualquier plugin de minijuego (Bingo, SkyWars, TNT Run, etc.) implementa esta interfaz
 * y se registra con {@link ArlightCoreAPI#registerMinigame(MinigameProvider)} para aparecer
 * en el item selector de ArlightCore.
 */
public interface MinigameProvider {

    /** Id unico y estable (ej. "bingo", "skywars"). Se usa como clave interna. */
    String getId();

    /** Nombre mostrado en el selector (puede tener colores de ChatColor). */
    String getDisplayName();

    /** Icono mostrado en el selector. */
    ItemStack getIcon();

    /** Estado actual (si se puede unir o no). */
    MinigameStatus getStatus();

    /** Se llama cuando un jugador hace click para unirse desde el selector. */
    void join(Player player);
}
