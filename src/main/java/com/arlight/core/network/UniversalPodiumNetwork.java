package com.arlight.core.network;

import com.arlight.core.api.PodiumEntry;
import com.arlight.core.api.PodiumStat;
import com.arlight.core.api.UniversalPodiumResult;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Canal servidor -> ArlightChatClient para el podio universal individual. */
public final class UniversalPodiumNetwork {
    public static final String CHANNEL = "arlightcore:podium";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final JavaPlugin plugin;

    public UniversalPodiumNetwork(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean supports(Player player) {
        return player != null && player.isOnline()
                && player.getListeningPluginChannels().contains(CHANNEL);
    }

    public void show(Player viewer, UniversalPodiumResult result) {
        if (viewer == null || result == null || !supports(viewer)) return;
        int viewerPlace = result.viewerPlace();
        List<PodiumStat> viewerStats = result.viewerStats().isEmpty()
                ? result.podium().stream()
                    .filter(entry -> entry.uuid() != null && entry.uuid().equals(viewer.getUniqueId()))
                    .findFirst().map(PodiumEntry::stats).orElse(List.of())
                : result.viewerStats();

        StringBuilder message = new StringBuilder("SHOW|")
                .append(encode(result.minigameId())).append('|')
                .append(encode(result.minigameName())).append('|')
                .append(encode(result.theme())).append('|')
                .append(encode(result.subtitle())).append('|')
                .append(result.displayTicks()).append('|')
                .append(encode(result.requeueCommand())).append('|')
                .append(viewerPlace).append('|')
                .append(encodeEntries(result.podium())).append('|')
                .append(encodeStats(viewerStats));
        send(viewer, message.toString());
    }

    public void hide(Player player) {
        if (supports(player)) send(player, "HIDE");
    }

    private static String encodeEntries(List<PodiumEntry> entries) {
        StringBuilder out = new StringBuilder();
        for (PodiumEntry entry : entries) {
            if (out.length() > 0) out.append(';');
            out.append(entry.place()).append(',')
                    .append(entry.uuid() == null ? "" : entry.uuid()).append(',')
                    .append(encode(entry.name())).append(',')
                    .append(encode(entry.pose())).append(',')
                    .append(encodeStats(entry.stats()));
        }
        return out.toString();
    }

    private static String encodeStats(List<PodiumStat> stats) {
        if (stats == null || stats.isEmpty()) return "";
        StringBuilder raw = new StringBuilder();
        for (PodiumStat stat : stats) {
            if (raw.length() > 0) raw.append('\n');
            raw.append(safe(stat.icon())).append('\t')
                    .append(safe(stat.label())).append('\t')
                    .append(safe(stat.value()));
        }
        return encode(raw.toString());
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\t', ' ');
    }

    private static String encode(String value) {
        if (value == null || value.isEmpty()) return "";
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void send(Player player, String message) {
        byte[] text = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream payload = new ByteArrayOutputStream(text.length + 5);
        writeVarInt(payload, text.length);
        payload.writeBytes(text);
        player.sendPluginMessage(plugin, CHANNEL, payload.toByteArray());
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & -128) != 0) {
            out.write(value & 127 | 128);
            value >>>= 7;
        }
        out.write(value);
    }
}
