# Guía visual de minijuegos

Los plugins nuevos deben importar `com.arlight.core.api.ArlightCoreIcons` y usar:

- `PLAYERS`: colas, equipos y cantidad de jugadores.
- `CLOCK`: cuenta regresiva y tiempo restante.
- `SWORDS`: inicio de partida, combate y desempate.
- `CHECK`: objetivo o acción completada.
- `CROSS`: errores, eliminación o acción rechazada.
- `WARNING`: cancelaciones, abandono y avisos importantes.
- `TROPHY`: ganador y victoria.
- `STAR`: puntos, progreso y recompensas.
- `INFO`: ayuda y estados neutrales.

Ejemplo:

```java
player.sendMessage(ArlightCoreIcons.TROPHY + ChatColor.GOLD + "¡Victoria!");
```

Los caracteres corresponden al resource pack ArlightChat 1.4.0 o superior.
