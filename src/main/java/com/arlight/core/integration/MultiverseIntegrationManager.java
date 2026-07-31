package com.arlight.core.integration;

import com.arlight.core.ArlightCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integración opcional con Multiverse-Inventories y Multiverse-NetherPortals.
 *
 * Se usan los comandos públicos de Multiverse en lugar de depender directamente de sus
 * clases Java. Esto mantiene ArlightCore compatible con Multiverse 5.0.x y 5.1+ y evita
 * que el plugin deje de cargar cuando alguno de los complementos no está instalado.
 */
public final class MultiverseIntegrationManager {

    private final ArlightCorePlugin plugin;
    private boolean enabled;
    private boolean inventoriesEnabled;
    private boolean netherPortalsEnabled;
    private boolean requireInventories;
    private boolean requireNetherPortals;
    private boolean removeWorldsFromOtherGroups;
    private boolean syncStaticGroupsOnStartup;
    private String defaultShares;
    private final Map<String, List<String>> configuredGroups = new LinkedHashMap<>();
    private boolean backupCreatedThisBoot;
    private long lastSyncMillis;
    private int lastGroupsSynced;
    private String lastError;

    // Evita que Bingo/ArlightCore vuelvan a importar o sincronizar el mismo trío
    // varias veces durante el mismo arranque. Multiverse actualiza sus YAML después
    // de ejecutar los comandos, por lo que una segunda llamada en el mismo tick veía
    // datos antiguos y lanzaba otra importación.
    private final Set<String> knownWorldCache = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> recentImports = new ConcurrentHashMap<>();
    private final Map<String, Long> recentPortalLinks = new ConcurrentHashMap<>();
    private final Set<String> stableConfigurations = ConcurrentHashMap.newKeySet();
    private final Map<String, CompletableFuture<Boolean>> inFlightConfigurations = new ConcurrentHashMap<>();
    private static final long COMMAND_COOLDOWN_MILLIS = 30_000L;

    public MultiverseIntegrationManager(ArlightCorePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        reload();
        if (syncStaticGroupsOnStartup) {
            Bukkit.getScheduler().runTaskLater(plugin, this::syncConfiguredGroups, 40L);
        } else {
            plugin.getLogger().info("Sincronización automática de grupos estáticos desactivada. "
                    + "Usa /core multiverse sync después de revisar los nombres de mundos.");
        }
    }

    public void reloadAndSync() {
        reload();
        if (syncStaticGroupsOnStartup) {
            Bukkit.getScheduler().runTask(plugin, this::syncConfiguredGroups);
        }
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("integrations.multiverse.enabled", true);
        inventoriesEnabled = plugin.getConfig().getBoolean(
                "integrations.multiverse.inventories.enabled", true);
        netherPortalsEnabled = plugin.getConfig().getBoolean(
                "integrations.multiverse.nether-portals.enabled", true);
        requireInventories = plugin.getConfig().getBoolean(
                "integrations.multiverse.inventories.require-plugin", false);
        requireNetherPortals = plugin.getConfig().getBoolean(
                "integrations.multiverse.nether-portals.require-plugin", false);
        removeWorldsFromOtherGroups = plugin.getConfig().getBoolean(
                "integrations.multiverse.inventories.remove-from-other-groups", true);
        syncStaticGroupsOnStartup = plugin.getConfig().getBoolean(
                "integrations.multiverse.inventories.sync-static-groups-on-startup", false);
        defaultShares = plugin.getConfig().getString(
                "integrations.multiverse.inventories.default-shares",
                "inventory,experience,health,hunger,potion_effects");
        if (defaultShares == null || defaultShares.isBlank()) {
            defaultShares = "inventory,experience,health,hunger,potion_effects";
        }

        configuredGroups.clear();
        ConfigurationSection groups = plugin.getConfig().getConfigurationSection(
                "integrations.multiverse.inventories.groups");
        if (groups != null) {
            for (String groupName : groups.getKeys(false)) {
                List<String> worlds = cleanWorldNames(groups.getStringList(groupName + ".worlds"));
                if (!worlds.isEmpty()) configuredGroups.put(normalizeGroup(groupName), worlds);
            }
        }
    }

    public boolean isInventoryManagementActive() {
        return enabled && inventoriesEnabled && isPluginEnabled("Multiverse-Inventories");
    }

    public boolean isNetherPortalManagementActive() {
        return enabled && netherPortalsEnabled && isPluginEnabled("Multiverse-NetherPortals");
    }

    public boolean configureInventoryGroup(String groupName, String overworld,
                                           String nether, String end) {
        List<String> worlds = cleanWorldNames(java.util.Arrays.asList(overworld, nether, end));
        if (worlds.size() != 3) return false;
        boolean worldsKnown = ensureMultiverseWorlds(overworld, nether, end);
        return worldsKnown && ensureInventoryGroup(normalizeGroup(groupName), worlds);
    }

    public boolean configurePortalLinks(String overworld, String nether, String end) {
        List<String> worlds = cleanWorldNames(java.util.Arrays.asList(overworld, nether, end));
        if (worlds.size() != 3) return false;
        boolean worldsKnown = ensureMultiverseWorlds(overworld, nether, end);
        return worldsKnown && ensurePortalLinks(overworld, nether, end);
    }

    public boolean configureMinigameWorlds(String groupName, String overworld,
                                           String nether, String end) {
        if (!enabled) return true;
        List<String> worlds = cleanWorldNames(java.util.Arrays.asList(overworld, nether, end));
        if (worlds.size() != 3) {
            lastError = "El trío de mundos está incompleto para el grupo " + groupName + ".";
            plugin.getLogger().warning(lastError);
            return false;
        }

        boolean worldsKnown = ensureMultiverseWorlds(overworld, nether, end);
        boolean inventoriesOk = worldsKnown && ensureInventoryGroup(normalizeGroup(groupName), worlds);
        boolean portalsOk = worldsKnown && ensurePortalLinks(overworld, nether, end);
        return inventoriesOk && portalsOk;
    }

    /**
     * Configuración escalonada e idempotente para mundos de minijuego.
     *
     * Se usa desde Bingo antes de mover jugadores: registra los mundos una sola vez,
     * espera dos ticks para que Multiverse termine de escribir sus datos, configura el
     * grupo de inventarios y finalmente enlaza portales. Llamadas simultáneas para el
     * mismo trío comparten el mismo CompletableFuture.
     */
    public CompletableFuture<Boolean> configureMinigameWorldsStable(String groupName,
                                                                     String overworld,
                                                                     String nether,
                                                                     String end) {
        if (!enabled) return CompletableFuture.completedFuture(true);
        String group = normalizeGroup(groupName);
        List<String> worlds = cleanWorldNames(java.util.Arrays.asList(overworld, nether, end));
        if (worlds.size() != 3) {
            lastError = "El trío de mundos está incompleto para el grupo " + group + ".";
            return CompletableFuture.completedFuture(false);
        }

        String key = group + "|" + String.join("|", worlds).toLowerCase(Locale.ROOT);
        if (stableConfigurations.contains(key) && inventoryGroupMatches(group, worlds)) {
            return CompletableFuture.completedFuture(true);
        }

        CompletableFuture<Boolean> existing = inFlightConfigurations.get(key);
        if (existing != null) return existing;

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        CompletableFuture<Boolean> raced = inFlightConfigurations.putIfAbsent(key, future);
        if (raced != null) return raced;

        Runnable begin = () -> {
            try {
                if (!ensureMultiverseWorlds(overworld, nether, end)) {
                    completeStableConfiguration(key, future, false, null);
                    return;
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        boolean inventoriesOk = ensureInventoryGroup(group, worlds);
                        if (!inventoriesOk) {
                            completeStableConfiguration(key, future, false, null);
                            return;
                        }
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            try {
                                boolean portalsOk = ensurePortalLinks(overworld, nether, end);
                                if (portalsOk) stableConfigurations.add(key);
                                completeStableConfiguration(key, future, portalsOk, null);
                            } catch (Throwable error) {
                                completeStableConfiguration(key, future, false, error);
                            }
                        }, 2L);
                    } catch (Throwable error) {
                        completeStableConfiguration(key, future, false, error);
                    }
                }, 2L);
            } catch (Throwable error) {
                completeStableConfiguration(key, future, false, error);
            }
        };

        if (Bukkit.isPrimaryThread()) begin.run();
        else Bukkit.getScheduler().runTask(plugin, begin);
        return future;
    }

    private void completeStableConfiguration(String key, CompletableFuture<Boolean> future,
                                             boolean result, Throwable error) {
        inFlightConfigurations.remove(key, future);
        if (error != null) {
            lastError = "Fallo durante la configuración estable de mundos: "
                    + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            plugin.getLogger().warning(lastError);
            future.complete(false);
        } else {
            if (result) lastError = null;
            future.complete(result);
        }
    }

    public boolean syncConfiguredGroups() {
        if (!enabled || !inventoriesEnabled) {
            lastGroupsSynced = 0;
            lastSyncMillis = System.currentTimeMillis();
            return true;
        }
        if (!isPluginEnabled("Multiverse-Inventories")) {
            String message = "Multiverse-Inventories no está activo; no se configuraron grupos.";
            if (requireInventories) plugin.getLogger().severe(message);
            else plugin.getLogger().info(message);
            lastError = requireInventories ? message : null;
            return !requireInventories;
        }

        int synced = 0;
        boolean ok = true;
        for (Map.Entry<String, List<String>> entry : configuredGroups.entrySet()) {
            List<String> existing = existingWorlds(entry.getValue());
            if (existing.isEmpty()) {
                plugin.getLogger().info("Grupo de inventarios '" + entry.getKey()
                        + "' omitido: ninguno de sus mundos existe todavía.");
                continue;
            }
            if (ensureInventoryGroup(entry.getKey(), existing)) synced++;
            else ok = false;
        }
        lastGroupsSynced = synced;
        lastSyncMillis = System.currentTimeMillis();
        if (ok) lastError = null;
        return ok;
    }

    public void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Integración Multiverse de ArlightCore");
        sender.sendMessage(ChatColor.YELLOW + "General: " + state(enabled));
        sender.sendMessage(ChatColor.YELLOW + "Multiverse-Core: "
                + pluginState("Multiverse-Core", true));
        sender.sendMessage(ChatColor.YELLOW + "Multiverse-Inventories: "
                + pluginState("Multiverse-Inventories", inventoriesEnabled));
        sender.sendMessage(ChatColor.YELLOW + "Multiverse-NetherPortals: "
                + pluginState("Multiverse-NetherPortals", netherPortalsEnabled));
        sender.sendMessage(ChatColor.YELLOW + "Inventarios externos para minijuegos: "
                + state(isInventoryManagementActive()));
        sender.sendMessage(ChatColor.YELLOW + "Sincronizar grupos estáticos al iniciar: "
                + state(syncStaticGroupsOnStartup));
        sender.sendMessage(ChatColor.YELLOW + "Grupos estáticos sincronizados: "
                + ChatColor.WHITE + lastGroupsSynced);
        if (lastSyncMillis > 0) {
            sender.sendMessage(ChatColor.GRAY + "Última sincronización: "
                    + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(lastSyncMillis)));
        }
        if (lastError != null && !lastError.isBlank()) {
            sender.sendMessage(ChatColor.RED + "Último error: " + lastError);
        }
    }

    /**
     * Registra los mundos creados por Bingo en Multiverse-Core antes de configurar
     * Inventories o NetherPortals. MVNP solo puede enlazar destinos conocidos por MV-Core.
     */
    private boolean ensureMultiverseWorlds(String overworld, String nether, String end) {
        if (!enabled) return true;
        if (!isPluginEnabled("Multiverse-Core")) {
            String message = "Multiverse-Core no está activo; no se pudo registrar el trío "
                    + overworld + ".";
            if (requireInventories || requireNetherPortals) {
                lastError = message;
                plugin.getLogger().severe(message);
                return false;
            }
            plugin.getLogger().info(message);
            return true;
        }

        boolean overworldOk = ensureMultiverseWorld(overworld, "normal");
        boolean netherOk = ensureMultiverseWorld(nether, "nether");
        boolean endOk = ensureMultiverseWorld(end, "the_end");
        return overworldOk && netherOk && endOk;
    }

    private boolean ensureMultiverseWorld(String worldName, String environment) {
        if (worldName == null || worldName.isBlank()) return false;
        String normalized = worldName.toLowerCase(Locale.ROOT);
        if (knownWorldCache.contains(normalized)) return true;

        Plugin multiverse = plugin.getServer().getPluginManager().getPlugin("Multiverse-Core");
        if (multiverse == null || !multiverse.isEnabled()) return false;

        File worldsFile = new File(multiverse.getDataFolder(), "worlds.yml");
        if (isKnownMultiverseWorld(worldsFile, worldName)) {
            knownWorldCache.add(normalized);
            return true;
        }

        File folder = new File(Bukkit.getWorldContainer(), worldName);
        if (Bukkit.getWorld(worldName) == null && !folder.isDirectory()) {
            lastError = "No se pudo importar '" + worldName + "' en Multiverse-Core: el mundo no existe.";
            plugin.getLogger().warning(lastError);
            return false;
        }

        long now = System.currentTimeMillis();
        Long lastAttempt = recentImports.get(normalized);
        if (lastAttempt != null && now - lastAttempt < COMMAND_COOLDOWN_MILLIS) {
            // El mundo existe y ya hubo un intento reciente. No repetimos /mv import
            // mientras Multiverse termina de persistir worlds.yml.
            return true;
        }
        recentImports.put(normalized, now);

        boolean accepted = dispatch("mv import " + worldName + " " + environment);
        if (!accepted) {
            lastError = "Multiverse-Core rechazó la importación de '" + worldName + "'.";
            plugin.getLogger().warning(lastError);
            return false;
        }
        knownWorldCache.add(normalized);
        plugin.getLogger().info("Multiverse-Core: registro solicitado una sola vez para '"
                + worldName + "' como " + environment + ".");
        return true;
    }

    private boolean isKnownMultiverseWorld(File worldsFile, String worldName) {
        if (!worldsFile.isFile()) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(worldsFile);
        // Multiverse 4 suele guardar bajo 'worlds:', mientras algunas builds 5.x
        // usan directamente los nombres en la raíz. Se aceptan ambos formatos.
        ConfigurationSection worlds = yaml.getConfigurationSection("worlds");
        if (worlds == null) worlds = yaml;
        for (String configured : worlds.getKeys(false)) {
            if (configured.equalsIgnoreCase(worldName)) return true;
            String name = worlds.getString(configured + ".name");
            if (name != null && name.equalsIgnoreCase(worldName)) return true;
        }
        return false;
    }

    private boolean ensureInventoryGroup(String groupName, List<String> requestedWorlds) {
        if (!enabled || !inventoriesEnabled) return true;
        Plugin mvi = plugin.getServer().getPluginManager().getPlugin("Multiverse-Inventories");
        if (mvi == null || !mvi.isEnabled()) {
            String message = "Multiverse-Inventories no está activo; el grupo '"
                    + groupName + "' no se pudo configurar.";
            if (requireInventories) plugin.getLogger().severe(message);
            else plugin.getLogger().info(message);
            lastError = requireInventories ? message : null;
            return !requireInventories;
        }

        List<String> worlds = existingWorlds(requestedWorlds);
        if (worlds.isEmpty()) {
            plugin.getLogger().warning("No se configuró el grupo '" + groupName
                    + "' porque ninguno de sus mundos existe todavía.");
            return false;
        }

        File groupsFile = new File(mvi.getDataFolder(), "groups.yml");
        backupGroupsFile(groupsFile);
        Map<String, Set<String>> currentGroups = readGroups(groupsFile);

        if (groupMatches(currentGroups, groupName, worlds)) {
            plugin.getLogger().fine("Multiverse-Inventories: grupo '" + groupName
                    + "' ya coincide; se omiten comandos duplicados.");
            return true;
        }

        if (removeWorldsFromOtherGroups) {
            for (Map.Entry<String, Set<String>> entry : currentGroups.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(groupName)) continue;
                List<String> conflicts = requestedWorlds.stream()
                        .filter(world -> containsIgnoreCase(entry.getValue(), world))
                        .toList();
                if (!conflicts.isEmpty()) {
                    dispatch("mvinv remove-worlds " + entry.getKey() + " "
                            + String.join(",", conflicts));
                }
            }
        }

        String joinedWorlds = String.join(",", worlds);
        boolean groupExists = currentGroups.keySet().stream()
                .anyMatch(name -> name.equalsIgnoreCase(groupName));
        boolean result;
        if (groupExists) {
            result = dispatch("mvinv add-worlds " + groupName + " " + joinedWorlds);
            result &= dispatch("mvinv add-shares " + groupName + " " + defaultShares);
        } else {
            result = dispatch("mvinv create-group " + groupName + " "
                    + joinedWorlds + " " + defaultShares);
        }

        if (result) {
            plugin.getLogger().info("Multiverse-Inventories: grupo '" + groupName
                    + "' sincronizado con " + joinedWorlds + ".");
            lastError = null;
        } else {
            lastError = "Multiverse-Inventories rechazó la configuración del grupo '"
                    + groupName + "'.";
            plugin.getLogger().warning(lastError);
        }
        return result;
    }

    private boolean ensurePortalLinks(String overworld, String nether, String end) {
        if (!enabled || !netherPortalsEnabled) return true;
        if (!isPluginEnabled("Multiverse-NetherPortals")) {
            String message = "Multiverse-NetherPortals no está activo; no se enlazó el trío "
                    + overworld + ".";
            if (requireNetherPortals) plugin.getLogger().severe(message);
            else plugin.getLogger().info(message);
            lastError = requireNetherPortals ? message : null;
            return !requireNetherPortals;
        }

        String linkKey = (overworld + "|" + nether + "|" + end).toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Long lastLinked = recentPortalLinks.get(linkKey);
        if (lastLinked != null && now - lastLinked < COMMAND_COOLDOWN_MILLIS) return true;

        boolean netherOk = dispatch("mvnp link nether " + overworld + " " + nether
                + " --bidirectional");
        boolean endOk = dispatch("mvnp link end " + overworld + " " + end
                + " --bidirectional");
        if (netherOk && endOk) recentPortalLinks.put(linkKey, now);
        if (netherOk && endOk) {
            plugin.getLogger().info("Multiverse-NetherPortals: enlaces de '" + overworld
                    + "' verificados/actualizados.");
            lastError = null;
            return true;
        }
        lastError = "No se pudieron crear todos los enlaces de portales para " + overworld + ".";
        plugin.getLogger().warning(lastError);
        return false;
    }

    private boolean dispatch(String command) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
            return true;
        }
        try {
            boolean accepted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (!accepted) plugin.getLogger().warning("Comando rechazado: /" + command);
            return accepted;
        } catch (Throwable error) {
            plugin.getLogger().warning("Error ejecutando /" + command + ": " + error.getMessage());
            return false;
        }
    }

    private boolean inventoryGroupMatches(String groupName, List<String> worlds) {
        Plugin mvi = plugin.getServer().getPluginManager().getPlugin("Multiverse-Inventories");
        if (mvi == null || !mvi.isEnabled()) return !requireInventories;
        return groupMatches(readGroups(new File(mvi.getDataFolder(), "groups.yml")), groupName, worlds);
    }

    private boolean groupMatches(Map<String, Set<String>> groups, String groupName, List<String> worlds) {
        Set<String> actual = null;
        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(groupName)) {
                actual = entry.getValue();
                break;
            }
        }
        if (actual == null || actual.size() != worlds.size()) return false;
        for (String world : worlds) if (!containsIgnoreCase(actual, world)) return false;
        return true;
    }

    private Map<String, Set<String>> readGroups(File groupsFile) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        if (!groupsFile.isFile()) return result;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(groupsFile);
        ConfigurationSection root = yaml.getConfigurationSection("groups");
        if (root == null) root = yaml;
        for (String group : root.getKeys(false)) {
            List<String> worlds = root.getStringList(group + ".worlds");
            if (worlds.isEmpty()) continue;
            result.put(group, new LinkedHashSet<>(worlds));
        }
        return result;
    }

    private void backupGroupsFile(File groupsFile) {
        if (backupCreatedThisBoot || !groupsFile.isFile()) return;
        File backupDir = new File(plugin.getDataFolder(), "multiverse-backups");
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear " + backupDir.getPath());
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File target = new File(backupDir, "Multiverse-Inventories-groups-" + stamp + ".yml");
        try {
            Files.copy(groupsFile.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            backupCreatedThisBoot = true;
            plugin.getLogger().info("Copia de seguridad de groups.yml creada en " + target.getPath());
        } catch (IOException error) {
            plugin.getLogger().warning("No se pudo respaldar groups.yml: " + error.getMessage());
        }
    }

    private List<String> existingWorlds(List<String> names) {
        List<String> result = new ArrayList<>();
        for (String name : cleanWorldNames(names)) {
            File folder = new File(Bukkit.getWorldContainer(), name);
            if (Bukkit.getWorld(name) != null || folder.isDirectory()) result.add(name);
        }
        return result;
    }

    private List<String> cleanWorldNames(List<String> names) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (names == null) return new ArrayList<>();
        for (String name : names) {
            if (name == null) continue;
            String clean = name.trim();
            if (!clean.isEmpty() && !clean.contains(" ") && !clean.contains(",")) result.add(clean);
        }
        return new ArrayList<>(result);
    }

    private boolean isPluginEnabled(String name) {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(name);
        return dependency != null && dependency.isEnabled();
    }

    private boolean containsIgnoreCase(Set<String> values, String wanted) {
        for (String value : values) if (value.equalsIgnoreCase(wanted)) return true;
        return false;
    }

    private String normalizeGroup(String raw) {
        if (raw == null || raw.isBlank()) return "minigame";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private String state(boolean value) {
        return value ? ChatColor.GREEN + "ACTIVO" : ChatColor.RED + "INACTIVO";
    }

    private String pluginState(String pluginName, boolean configured) {
        if (!configured) return ChatColor.GRAY + "desactivado en config";
        return isPluginEnabled(pluginName)
                ? ChatColor.GREEN + "detectado"
                : ChatColor.RED + "no instalado/no cargado";
    }
}
