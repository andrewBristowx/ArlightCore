package com.arlight.buildbattle;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Envía la sala visual al mod ArlightChatClient 2.8.0. */
public final class WaitingScreenNetwork {
    public static final String CHANNEL = "arlightbuildbattle:waiting";
    private final JavaPlugin plugin;

    public WaitingScreenNetwork(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, int players, int maxPlayers, int countdown,
                     String status, List<String> names) {
        if (!enabled(player)) return;
        send(player, "SHOW|" + players + "|" + maxPlayers + "|" + countdown
                + "|" + safe(status) + "|" + safeNames(names));
    }

    public void update(Player player, int players, int maxPlayers, int countdown,
                       String status, List<String> names) {
        if (!enabled(player)) return;
        send(player, "UPDATE|" + players + "|" + maxPlayers + "|" + countdown
                + "|" + safe(status) + "|" + safeNames(names));
    }

    public void hide(Player player) {
        if (player != null && player.isOnline()) send(player, "HIDE");
    }

    private boolean enabled(Player player) {
        return player != null && player.isOnline()
                && plugin.getConfig().getBoolean("waiting-screen.enabled", true);
    }

    private void send(Player player, String command) {
        byte[] text = command.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream payload = new ByteArrayOutputStream(text.length + 5);
        writeVarInt(payload, text.length);
        payload.writeBytes(text);
        player.sendPluginMessage(plugin, CHANNEL, payload.toByteArray());
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        while ((value & -128) != 0) {
            output.write(value & 127 | 128);
            value >>>= 7;
        }
        output.write(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('|', '/').replace(',', ' ');
    }

    private static String safeNames(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        return names.stream().limit(12).map(WaitingScreenNetwork::safe)
                .reduce((left, right) -> left + "," + right).orElse("");
    }
}
