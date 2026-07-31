package com.arlight.core.network;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Envía paneles visuales actualizables al mod cliente ArlightChatClient.
 * Los scoreboards Bukkit se mantienen como respaldo para clientes sin el mod.
 */
public final class VisualScoreboardNetwork {
    public static final String CHANNEL = "arlightcore:visual_scoreboard";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    public record Line(String icon, String label, String value) {
        public Line {
            icon = icon == null ? "" : icon;
            label = label == null ? "" : label;
            value = value == null ? "" : value;
        }
    }

    private final JavaPlugin plugin;
    /** Último payload enviado a cada jugador; evita paquetes idénticos y flicker innecesario. */
    private final Map<UUID, String> lastPayloadByPlayer = new HashMap<>();

    public VisualScoreboardNetwork(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean supports(Player player) {
        return player != null && player.isOnline()
                && player.getListeningPluginChannels().contains(CHANNEL);
    }

    public void show(Player player, String style, String title, String footer,
                     double progress, String progressText, List<Line> lines) {
        if (!plugin.getConfig().getBoolean("visual-scoreboards.enabled", true)
                || !supports(player)) return;
        StringBuilder payload = new StringBuilder("SHOW|")
                .append(encode(style)).append('|')
                .append(encode(title)).append('|')
                .append(encode(footer)).append('|')
                .append(Math.max(-1.0D, Math.min(1.0D, progress))).append('|')
                .append(encode(progressText)).append('|');
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) payload.append(';');
                Line line = lines.get(i);
                payload.append(encode(line.icon())).append(',')
                        .append(encode(line.label())).append(',')
                        .append(encode(line.value()));
            }
        }
        String message = payload.toString();
        String previous = lastPayloadByPlayer.put(player.getUniqueId(), message);
        if (!message.equals(previous)) send(player, message);
    }

    public void clear(Player player) {
        if (player == null) return;
        // Solo manda CLEAR si realmente existía un panel conocido para ese jugador.
        // Esto reduce tráfico y evita que un manager ajeno borre repetidamente otro panel.
        if (lastPayloadByPlayer.remove(player.getUniqueId()) != null && supports(player)) {
            send(player, "CLEAR");
        }
    }


    /** Olvida el caché al desconectarse, sin intentar enviar paquetes al cliente offline. */
    public void forget(UUID uuid) {
        if (uuid != null) lastPayloadByPlayer.remove(uuid);
    }

    private void send(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        byte[] text = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream payload = new ByteArrayOutputStream(text.length + 5);
        writeVarInt(payload, text.length);
        payload.writeBytes(text);
        player.sendPluginMessage(plugin, CHANNEL, payload.toByteArray());
    }

    private static String encode(String value) {
        if (value == null || value.isEmpty()) return "";
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & -128) != 0) {
            out.write(value & 127 | 128);
            value >>>= 7;
        }
        out.write(value);
    }
}
