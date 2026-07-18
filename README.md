# ArlightCore

Nucleo compartido para los minijuegos del servidor Arlight (Bingo, y a futuro SkyWars, LuckyWars,
TNT Run, Parkour, BuildBattle, etc). No es un minijuego en si -- da la infraestructura comun que
todos comparten:

- Item fisico **Minijuegos** (brujula): GUI con todos los minijuegos registrados y su estado
  (esperando jugadores / en curso), click para unirte.
- Item fisico **Nivel y Recompensas** (estrella de nether): GUI con tu XP/nivel actual y las
  recompensas de cada nivel, reclamables con click.
- Sistema de XP/niveles: +5 XP (configurable) por ganar cualquier minijuego registrado, cada 30 XP
  (configurable) sube un nivel.
- Recompensas por nivel 100% configurables sosteniendo el item en la mano (`/core reward set <nivel>`)
  -- funciona igual con items vanilla o de mods, porque simplemente clona el ItemStack tal cual esta
  en tu mano (con su nombre, lore, encantamientos, NBT, todo).
- Reclamo de recompensas restringido a ciertos mundos (`claim-worlds` en config.yml, por defecto
  `survival`), para que no se pierdan muriendo en un minijuego.

## Como compilar

```
cd ArlightCore
mvn clean package
mvn install
```

El `mvn install` es importante: instala el jar en tu repositorio Maven **local**, para que los
plugins de minijuegos (Bingo, etc.) lo puedan usar como dependencia sin tener que publicarlo en
ningun lado. El jar final para el servidor queda en `target/ArlightCore-1.0.0.jar` -- va a
`/plugins` como cualquier otro plugin.

## Como lo usan los plugins de minijuegos

Cualquier plugin de minijuego que quiera aparecer en el selector y dar XP al ganar necesita:

1. Agregar esta dependencia a su `pom.xml` (despues de haber corrido `mvn install` en este proyecto):
   ```xml
   <dependency>
       <groupId>com.arlight</groupId>
       <artifactId>ArlightCore</artifactId>
       <version>1.0.0</version>
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
   });
   ```
4. Cuando un jugador gane, llamar `ArlightCoreAPI.addWinXp(player)`.

Todo este enganche es **opcional y aislado**: si ArlightCore no esta instalado, el plugin de
minijuego sigue funcionando exactamente igual, solo que no aparece en el selector ni da XP.

## Comandos

- `/core items` - te da (o te vuelve a dar) los dos items fisicos
- `/core reward set <nivel>` (admin) - la recompensa de ese nivel pasa a ser el item que tenes en la mano
- `/core reward remove <nivel>` (admin)
- `/core reward list` (admin) - lista los niveles con recompensa configurada
- `/core xp <jugador> <cantidad>` (admin) - da XP manualmente (util para probar)
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
