package com.arlight.core.hologram;

import com.arlight.core.ArlightCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Hologramas de texto personalizados, independientes de las clasificaciones. */
public final class HologramManager implements Listener {
    private record Hologram(String id, String world, double x, double y, double z, List<String> lines) { }

    private final ArlightCorePlugin plugin;
    private final File file;
    private final NamespacedKey marker;
    private final Map<String, Hologram> holograms = new HashMap<>();
    private final List<UUID> spawned = new ArrayList<>();

    public HologramManager(ArlightCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
        this.marker = new NamespacedKey(plugin, "custom-hologram-display");
    }

    public void start() {
        load();
        clearTaggedDisplays();
        refresh();
        // Algunos gestores de mundos terminan de cargar el lobby después de onEnable.
        // Este segundo intento evita que el holograma guardado quede sin aparecer.
        Bukkit.getScheduler().runTaskLater(plugin, this::refresh, 40L);
    }

    public void stop() {
        save();
        clearSpawned();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        removeTaggedDisplays(event.getWorld());
        Bukkit.getScheduler().runTask(plugin, this::refresh);
    }

    public boolean create(String id, Location location, List<String> lines) {
        String key = normalizeId(id);
        if (key == null || holograms.containsKey(key) || location.getWorld() == null || lines.isEmpty()) return false;
        holograms.put(key, new Hologram(key, location.getWorld().getName(), location.getX(),
                location.getY(), location.getZ(), new ArrayList<>(lines)));
        save();
        refresh();
        return true;
    }

    public boolean remove(String id) {
        boolean removed = holograms.remove(String.valueOf(id).toLowerCase()) != null;
        if (removed) {
            save();
            refresh();
        }
        return removed;
    }

    public boolean move(String id, Location location) {
        String key = String.valueOf(id).toLowerCase();
        Hologram old = holograms.get(key);
        if (old == null || location.getWorld() == null) return false;
        holograms.put(key, new Hologram(key, location.getWorld().getName(), location.getX(),
                location.getY(), location.getZ(), old.lines()));
        save();
        refresh();
        return true;
    }

    public boolean addLine(String id, String line) {
        Hologram old = holograms.get(String.valueOf(id).toLowerCase());
        if (old == null) return false;
        List<String> lines = new ArrayList<>(old.lines());
        lines.add(line);
        replaceLines(old, lines);
        return true;
    }

    public boolean setLine(String id, int lineNumber, String line) {
        Hologram old = holograms.get(String.valueOf(id).toLowerCase());
        if (old == null || lineNumber < 1 || lineNumber > old.lines().size()) return false;
        List<String> lines = new ArrayList<>(old.lines());
        lines.set(lineNumber - 1, line);
        replaceLines(old, lines);
        return true;
    }

    public boolean removeLine(String id, int lineNumber) {
        Hologram old = holograms.get(String.valueOf(id).toLowerCase());
        if (old == null || old.lines().size() <= 1 || lineNumber < 1 || lineNumber > old.lines().size()) return false;
        List<String> lines = new ArrayList<>(old.lines());
        lines.remove(lineNumber - 1);
        replaceLines(old, lines);
        return true;
    }

    private void replaceLines(Hologram old, List<String> lines) {
        holograms.put(old.id(), new Hologram(old.id(), old.world(), old.x(), old.y(), old.z(), lines));
        save();
        refresh();
    }

    public List<String> ids() {
        return holograms.keySet().stream().sorted().toList();
    }

    public void refresh() {
        clearSpawned();
        for (Hologram hologram : holograms.values()) spawn(hologram);
    }

    private void spawn(Hologram hologram) {
        World world = Bukkit.getWorld(hologram.world());
        if (world == null) return;
        Location base = new Location(world, hologram.x(), hologram.y(), hologram.z());
        List<String> lines = hologram.lines();
        for (int index = 0; index < lines.size(); index++) {
            int lineIndex = index;
            Location location = base.clone().add(0, (lines.size() - index - 1) * 0.27, 0);
            TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
                entity.setText(color(lines.get(lineIndex)));
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setSeeThrough(true);
                entity.setShadowed(true);
                entity.setPersistent(true);
                entity.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
            });
            spawned.add(display.getUniqueId());
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String normalizeId(String id) {
        if (id == null) return null;
        String key = id.trim().toLowerCase();
        return key.matches("[a-z0-9_-]{1,32}") ? key : null;
    }

    private void clearSpawned() {
        for (UUID uuid : spawned) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        }
        spawned.clear();
    }

    private void clearTaggedDisplays() {
        for (World world : Bukkit.getWorlds()) removeTaggedDisplays(world);
    }

    private void removeTaggedDisplays(World world) {
        for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
            Byte value = display.getPersistentDataContainer().get(marker, PersistentDataType.BYTE);
            if (value != null && value == (byte) 1) display.remove();
        }
    }

    private void load() {
        holograms.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("holograms");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String base = id + ".";
            List<String> lines = section.getStringList(base + "lines");
            if (lines.isEmpty()) continue;
            holograms.put(id.toLowerCase(), new Hologram(id.toLowerCase(),
                    section.getString(base + "world", plugin.getLobbyWorld()),
                    section.getDouble(base + "x"), section.getDouble(base + "y"),
                    section.getDouble(base + "z"), lines));
        }
        plugin.getLogger().info("Hologramas personalizados cargados: " + holograms.size());
    }

    private void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta de datos para guardar hologramas.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Hologram hologram : holograms.values()) {
            String base = "holograms." + hologram.id() + ".";
            yaml.set(base + "world", hologram.world());
            yaml.set(base + "x", hologram.x());
            yaml.set(base + "y", hologram.y());
            yaml.set(base + "z", hologram.z());
            yaml.set(base + "lines", hologram.lines());
        }
        try {
            yaml.save(file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar holograms.yml", error);
        }
    }
}
