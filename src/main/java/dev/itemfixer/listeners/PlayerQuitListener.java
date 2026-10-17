package dev.itemfixer.listeners;

import dev.itemfixer.ItemFixerPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerQuitListener implements Listener {
    private final ItemFixerPlugin plugin;

    public PlayerQuitListener(ItemFixerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.fixer().forget(event.getPlayer().getUniqueId());
    }
}
