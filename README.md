# ArlightCore

Nucleo compartido para los minijuegos del servidor Arlight (Bingo, y a futuro SkyWars, LuckyWars,
TNT Run, Parkour, BuildBattle, etc). No es un minijuego en si -- da la infraestructura comun que
todos comparten:

- Item fisico **Minijuegos** (brujula): GUI con todos los minijuegos registrados y su estado
  (esperando jugadores / en curso), click para unirte.
- Cola central exclusiva: un jugador no puede esperar en dos minijuegos ni entrar a otro mientras
  conserva una sesión activa. Las colas se limpian al salir, desconectarse o iniciar la sesión.
- Item fisico **Nivel y Recompensas** (estrella de nether): GUI con tu XP/nivel actual y las
  recompensas de cada nivel, reclamables con click.
- Sistema de XP/niveles: +5 XP (configurable) por ganar cualquier minijuego registrado, cada 30 XP
  (configurable) sube un nivel.
- Recompensas por nivel 100% configurables sosteniendo el item en la mano (`/core reward set <nivel>`)
  -- funciona igual con items vanilla o de mods, porque simplemente clona el ItemStack tal cual esta
  en tu mano (con su nombre, lore, encantamientos, NBT, todo).
- Reclamo de recompensas restringido a ciertos mundos (`claim-worlds` en config.yml, por defecto
  `survival`), para que no se pierdan muriendo en un minijuego.
- Placeholders internos para LPC, TAB y otros plugins compatibles con PlaceholderAPI.

## Placeholders

- `%arlightcore_level%`: nivel actual del minipase.
- `%arlightcore_level_tag%`: etiqueta coloreada `[Nivel X]` lista para el chat.
- `%arlightcore_xp%`: XP total.
- `%arlightcore_xp_progress%`: XP acumulada dentro del nivel actual.
- `%arlightcore_xp_per_level%`: XP necesaria por nivel.
- `%arlightcore_xp_to_next_level%`: XP restante para el siguiente nivel.
- `%arlightcore_wins%`, `%arlightcore_played%`, `%arlightcore_losses%` y
  `%arlightcore_abandons%`: estadísticas globales.
- `%arlightcore_win_rate%`: porcentaje global de victorias.
- `%arlightcore_minigame%`: nombre del minijuego activo o en espera; vacío si no participa.
- `%arlightcore_minigame_tag%`: etiqueta `[Minijuego] ` coloreada y lista para el chat; incluye
  el espacio final únicamente cuando existe un minijuego.

La expansión viene dentro de ArlightCore: no hay que descargarla mediante eCloud.

## Como compilar

```
cd ArlightCore
mvn clean package
mvn install
```

El `mvn install` es importante: instala el jar en tu repositorio Maven **local**, para que los
plugins de minijuegos (Bingo, etc.) lo puedan usar como dependencia sin tener que publicarlo en
ningún lado. El jar final para el servidor queda en `target/ArlightCore-1.15.0.jar`
y va en `/plugins` como cualquier otro plugin.

## Como lo usan los plugins de minijuegos

Cualquier plugin de minijuego que quiera aparecer en el selector y dar XP al ganar necesita:

1. Agregar esta dependencia a su `pom.xml` (despues de haber corrido `mvn install` en este proyecto):
   ```xml
   <dependency>
       <groupId>com.arlight</groupId>
       <artifactId>ArlightCore</artifactId>
       <version>1.15.0</version>
       <scope>provided</scope>
   </dependency>
   ```
2. En su `plugin.yml`, agregar `softdepend: [ArlightCore]` (para que ArlightCore se cargue primero
   si esta presente, pero el plugin siga funcionando sin el).
3. Al arrancar, si detecta que ArlightCore esta instalado
   (`Bukkit.getPluginManager().getPlugin("ArlightCore") != null`), registrarse:
   ```java
   ArlightCoreAPI.registerMinigame(new MinigameProvider() {
       public String getId() { return "bingo"; }
       public String getDisplayName() { return ChatColor.GOLD + "Bingo"; }
       public ItemStack getIcon() { return new ItemStack(Material.PAPER); }
       public MinigameStatus getStatus() { return game.getState() == GameState.WAITING ? MinigameStatus.WAITING : MinigameStatus.IN_PROGRESS; }
       public void join(Player player) { game.addPlayer(player, null); }
       public int getCurrentPlayers() { return game.getPlayerCount(); }
       public int getMaxPlayers() { return 10; }
   });
   ```
4. Cuando un jugador gane, llamar `ArlightCoreAPI.addWinXp(player)`.

El selector del Core usa automáticamente la cola central. Otros plugins también pueden usarla con
`ArlightCoreAPI.joinQueue(player, "bingo")`, consultar `getQueueSize("bingo")` y salir con
`ArlightCoreAPI.leaveQueue(player)`.

Todo este enganche es **opcional y aislado**: si ArlightCore no esta instalado, el plugin de
minijuego sigue funcionando exactamente igual, solo que no aparece en el selector ni da XP.

## Corrección de color de iconos en 1.14.1

Los glifos decorativos ahora se dibujan en blanco antes de restaurar el color del texto. Esto
evita que los colores de títulos, lore, scoreboards y otros componentes oscurezcan o tiñan las
texturas del resource pack.

## Comandos

- `/core items` - te da (o te vuelve a dar) los dos items fisicos
- `/core reward set <nivel>` (admin) - la recompensa de ese nivel pasa a ser el item que tenes en la mano
- `/core reward remove <nivel>` (admin)
- `/core reward list` (admin) - lista los niveles con recompensa configurada
- `/core xp <jugador> <cantidad>` (admin) - da XP manualmente (util para probar)
- `/core queue status` - muestra la cola actual
- `/core queue leave` - abandona la cola actual de forma segura
- `/core games` - muestra minijuegos, estados, colas y jugadores en partida
- `/core reload` (admin) - recarga config.yml

## Configuracion (`config.yml`)

```yaml
xp:
  xp-per-win: 5
  xp-per-level: 30

claim-worlds:
  - "survival"

give-items-on-join: true
```

## Persistencia

- `playerdata.yml`: XP de cada jugador y que niveles ya reclamo.
- `rewards.yml`: el item de recompensa configurado para cada nivel.

## Ideas para seguir extendiendo

- Comando `/core level <jugador>` para consultar el nivel de otro jugador.
- Anuncio de servidor cuando alguien sube de nivel.
- Integracion con una economia (Vault) ademas de/en vez de XP propia.
# ArlightCore 1.13.0

## Administración y diagnóstico

- `/core status`: estado de MySQL, minijuegos, colas y sesiones.
- `/core debug <jugador>`: sesión, cola y estadísticas del jugador.
- `/core recover <jugador>`: restaura de forma segura una sesión pendiente.
- `/core stats view <jugador> [minijuego]`: consulta estadísticas.
- `/core stats reset <jugador> [minijuego] confirm`: reinicio con confirmación.
- `/core stats set <jugador> <minijuego> <campo> <valor>`: corrección administrativa.

Las operaciones sensibles se guardan en `plugins/ArlightCore/audit.log`.

## Correcciones de 1.9.1

- `/core reload` actualiza inmediatamente la XP entregada por victoria.
- La XP ya no puede quedar negativa ni desbordar el límite entero.
- Los niveles de recompensa deben ser mayores que cero.
- Las recompensas sobrantes se dejan de forma segura junto al jugador si el inventario está lleno.
- `claim-worlds` se compara sin distinguir mayúsculas y minúsculas.
- `/core recover` completa la restauración aunque un minijuego falle al limpiar objetos temporales.

## Control de minijuegos en 1.10.0

- `/core minigames status`: muestra el interruptor global y el estado de cada minijuego.
- `/core minigames disable all`: bloquea nuevas entradas a todos los minijuegos.
- `/core minigames enable all`: vuelve a habilitar el acceso global.
- `/core minigames disable <id>`: deshabilita solo un minijuego.
- `/core minigames enable <id>`: vuelve a habilitarlo.
- Las partidas que ya están en curso no se expulsan ni se interrumpen.
- El selector muestra como deshabilitados los juegos bloqueados por el Core.

## Scoreboard del lobby en 1.11.0

- Sidebar personal con título `Minijuegos`.
- Muestra nombre, XP total del pase, victorias y partidas jugadas.
- Texto final configurable, por defecto `Gracias holy.gg`.
- Solo aparece en el lobby general cuando el jugador no tiene una sesión activa.
- Se actualiza automáticamente y no reemplaza el scoreboard de una partida.

## XP por minijuego en 1.12.0

- Cada minijuego puede entregar una cantidad distinta de XP al ganador.
- `xp.per-minigame.skywars` y `xp.per-minigame.bingo` controlan sus recompensas.
- Los juegos sin configuración específica utilizan `xp.xp-per-win`.
- Los cambios se aplican con `/core reload`.

## Scoreboard y emotes en 1.13.0

- El lobby muestra el nivel del pase en vez de la XP acumulada.
- El título predeterminado es `Minijuegos :ponicalva:`.
- Los alias configurados en ArlightChat se convierten en el título y el pie del scoreboard.
- ArlightChat sigue siendo opcional; el Core funciona aunque no esté instalado.

## Decoraciones y API de iconos en 1.14.0

- Decora de forma moderada el scoreboard, selector, colas y mensajes administrativos.
- Se puede apagar todo con `decorations.enabled: false`.
- Expone `ArlightCoreIcons` para que los próximos minijuegos compartan los mismos símbolos.
- Requiere el resource pack ArlightChat 1.4.0 para ver las texturas; sin él se verán
  caracteres cuadrados, pero la lógica del Core seguirá funcionando.


## Integración Multiverse en 1.15.0

ArlightCore coordina Multiverse sin guardar una segunda copia del inventario:

- Multiverse-Inventories conserva los perfiles de inventario y estadísticas por grupo.
- Multiverse-NetherPortals enlaza el Overworld, Nether y End de cada minijuego.
- Los mundos dinámicos creados por Bingo se importan primero en Multiverse-Core.
- Core crea una copia de `plugins/Multiverse-Inventories/groups.yml` antes de modificar grupos.
- `/core multiverse status` muestra el estado de las tres extensiones.
- `/core multiverse sync` aplica manualmente los grupos estáticos declarados en `config.yml`.

Por seguridad, `sync-static-groups-on-startup` viene en `false`. Las arenas de Bingo sí se
registran automáticamente cuando terminan de crearse, pero los grupos existentes de Survival,
SkyWars y Eventos no se cambian hasta que un administrador revise sus nombres y ejecute el comando
de sincronización.


## Botón de cosméticos 1.19.0
Cuando `ArlightCosmetics` está habilitado, el mundo configurado como lobby recibe un
fragmento de amatista en la ranura 6. Abre `/cosmeticos` con clic derecho y se puede
configurar en `lobby-items.cosmetics`.


## Entrega 1.20.0 — Safe Minigame World Handoff

Esta versión añade una configuración escalonada e idempotente de Multiverse para que Bingo no reimporte ni resincronice el mismo trío mientras el jugador entra. Consulta `CAMBIOS_1.20.0.txt` y `PROBAR_1.20.0.txt`.
