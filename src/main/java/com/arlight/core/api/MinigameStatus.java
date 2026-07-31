package com.arlight.core.api;

public enum MinigameStatus {
    AVAILABLE,   // disponible, equivalente a WAITING para integraciones nuevas
    WAITING,     // esperando jugadores; se puede unir
    IN_PROGRESS, // partida en curso
    RESTARTING,  // limpiando o regenerando arena
    DISABLED;    // desactivado o en mantenimiento

    public boolean canJoin() {
        return this == AVAILABLE || this == WAITING;
    }
}
