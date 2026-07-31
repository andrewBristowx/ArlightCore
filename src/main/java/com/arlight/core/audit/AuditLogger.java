package com.arlight.core.audit;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.logging.Level;

/** Registro separado de las acciones administrativas sensibles. */
public final class AuditLogger {
    private final JavaPlugin plugin;
    private final File file;

    public AuditLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "audit.log");
    }

    public synchronized void log(CommandSender actor, String action) {
        String name = actor == null ? "SYSTEM" : actor.getName();
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(Instant.now() + " | " + name + " | " + action + System.lineSeparator());
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "No se pudo escribir audit.log", error);
        }
    }
}
