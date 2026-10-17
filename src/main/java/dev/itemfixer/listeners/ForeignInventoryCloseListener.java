package dev.itemfixer.listeners;

import dev.itemfixer.ItemFixerPlugin;
import dev.itemfixer.features.gui.FixerHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class ForeignInventoryCloseListener implements Listener {
    private final ItemFixerPlugin plugin;

    public ForeignInventoryCloseListener(ItemFixerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof FixerHolder) {
            return;
        }
        if (!plugin.fixer().hasRules()) {
            return;
        }
        plugin.fixer().markDirty(player.getUniqueId());
    }
}
