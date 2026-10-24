package dev.itemfixer.hooks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.itemfixer.ItemFixerPlugin;
import dev.itemfixer.Messages;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ModrinthUpdateChecker {
    private static final String PAGE = "https://modrinth.com/plugin/itemfixer";
    private static final String API = "https://api.modrinth.com/v2/project/itemfixer/version";

    private final ItemFixerPlugin plugin;
    private final AtomicBoolean checked = new AtomicBoolean();
    private volatile String newer;

    public ModrinthUpdateChecker(ItemFixerPlugin plugin) {
        this.plugin = plugin;
    }

    public void checkOnce() {
        if (!plugin.settings().updateChecker() || !checked.compareAndSet(false, true)) {
            return;
        }
        String current = plugin.getPluginMeta().getVersion();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String found = fetch(current);
            if (found != null) {
                newer = found;
                plugin.getLogger().info("a newer version (" + found + ") is on Modrinth: " + PAGE);
            }
        });
    }

    public void notifyOnJoin(Player player) {
        if (newer == null || !plugin.settings().updateChecker() || !player.hasPermission("itemfixer.admin")) {
            return;
        }
        player.sendMessage(Messages.text(Messages.UPDATE_AVAILABLE));
    }

    private String fetch(String current) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(API).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "ItemFixer/" + current + " (" + PAGE + ")");
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            } finally {
                connection.disconnect();
            }
            String newest = newest(JsonParser.parseString(body.toString()).getAsJsonArray());
            return newest != null && VersionCompare.isNewer(newest, current) ? newest : null;
        } catch (Exception failure) {
            plugin.getLogger().info("could not reach Modrinth for the update check: " + failure.getMessage());
            return null;
        }
    }

    private static String newest(JsonArray versions) {
        String newest = null;
        for (JsonElement entry : versions) {
            String number = entry.getAsJsonObject().get("version_number").getAsString();
            if (newest == null || VersionCompare.isNewer(number, newest)) {
                newest = number;
            }
        }
        return newest;
    }
}
