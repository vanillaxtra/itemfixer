package dev.itemfixer.listeners;

import dev.itemfixer.ItemFixerPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerJoinListener implements Listener {
    private final ItemFixerPlugin plugin;

    public PlayerJoinListener(ItemFixerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.fixer().noteOnline(event.getPlayer().getUniqueId());
        plugin.fixer().givePending(event.getPlayer());
        plugin.updates().notifyOnJoin(event.getPlayer());
        if (!plugin.fixer().hasRules()) {
            return;
        }
        plugin.fixer().markDirty(event.getPlayer().getUniqueId());
    }
}
