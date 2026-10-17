package dev.itemfixer.listeners;

import dev.itemfixer.ItemFixerPlugin;
import dev.itemfixer.features.gui.FixerHolder;
import dev.itemfixer.features.gui.GuiItems;
import dev.itemfixer.features.gui.MenuSounds;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class FixerGuiCloseListener implements Listener {
    private final ItemFixerPlugin plugin;

    public FixerGuiCloseListener(ItemFixerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof FixerHolder holder)) {
            return;
        }
        holder.returnInputs(player);
        GuiItems.strip(plugin, player);
        if (holder.consumeSilent()) {
            return;
        }
        MenuSounds.close(player);
        plugin.fixer().markDirty(player.getUniqueId());
    }
}
