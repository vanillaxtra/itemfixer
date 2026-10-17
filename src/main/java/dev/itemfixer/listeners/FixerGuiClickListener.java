package dev.itemfixer.listeners;

import dev.itemfixer.ItemFixerPlugin;
import dev.itemfixer.Messages;
import dev.itemfixer.features.gui.FixerHolder;
import dev.itemfixer.features.gui.MenuSounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class FixerGuiClickListener implements Listener {
    private final ItemFixerPlugin plugin;

    public FixerGuiClickListener(ItemFixerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof FixerHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("itemfixer.admin")) {
            holder.markSilent();
            MenuSounds.deny(player);
            Messages.actionBar(player, Messages.NO_PERMISSION);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.closeInventory();
                }
            });
            return;
        }
        holder.onClick(player, event);
    }
}
