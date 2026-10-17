package dev.itemfixer.listeners;

import dev.itemfixer.ItemFixerPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

public final class ItemPickupListener implements Listener {
    private final ItemFixerPlugin plugin;

    public ItemPickupListener(ItemFixerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.fixer().hasRules()) {
            return;
        }
        plugin.fixer().markDirty(player.getUniqueId());
    }
}
