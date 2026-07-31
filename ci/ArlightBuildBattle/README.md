# ArlightBuildBattle 1.1.1
MVP jugable: mundo `vacio`, grupo `buildbattle`, propuestas de tema escritas por jugadores, GUI de votación, parcelas protegidas, tiempo de construcción, votación 1-6, ganador por promedio, panel visual y limpieza.

Comandos: `/bb join`, `/bb leave`, `/bb theme <tema>`, `/bb stats`, `/bb admin forcestart|skip|clear|reload`.


## Integración visual 1.1.0
Requiere ArlightChatClient 2.8.0. Canal: `arlightbuildbattle:waiting`.


## Corrección 1.1.1
Corrige los dos errores `cannot find symbol` de `plotCenter`: se llamaba al método `gap` como si fuera una variable. Ahora usa `gap()` en ambos cálculos.
