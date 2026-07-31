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

    /** Categoría única (por ejemplo bingo o skywars). Por defecto usa el mismo ID. */
    default String getCategory() {
        return getId();
    }

    /** Nombre mostrado en el selector (puede tener colores de ChatColor). */
    String getDisplayName();

    /** Icono mostrado en el selector. */
    ItemStack getIcon();

    /** Estado actual (si se puede unir o no). */
    MinigameStatus getStatus();

    /** Se llama cuando un jugador hace click para unirse desde el selector. */
    void join(Player player);

    /** Salida opcional; se mantiene default para no romper minijuegos existentes. */
    default void leave(Player player) {
    }

    /**
     * Se llama cuando un jugador se desconecta con una sesión activa. El minijuego
     * debe quitarlo de la partida, pero NO debe cerrar la sesión del Core: el Core
     * la conserva para restaurar inventario y ubicación al próximo ingreso.
     */
    default void handleDisconnect(Player player) {
    }

    /**
     * Se ejecuta después de restaurar una sesión interrumpida. Permite que cada
     * minijuego elimine objetos temporales que pudieran existir en respaldos antiguos.
     */
    default void cleanupAfterRecovery(Player player) {
    }

    /** Permite al Core consultar si el jugador sigue dentro del minijuego. */
    default boolean isPlaying(Player player) {
        return false;
    }

    default int getCurrentPlayers() {
        return 0;
    }

    default int getMaxPlayers() {
        return 0;
    }
}
