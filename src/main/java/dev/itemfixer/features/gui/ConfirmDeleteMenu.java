package dev.itemfixer.features.gui;

import dev.itemfixer.ItemFixerPlugin;
import dev.itemfixer.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ConfirmDeleteMenu implements FixerHolder {
    private static final int SIZE = 27;
    private static final int CONFIRM = 11;
    private static final int ICON = 13;
    private static final int CANCEL = 15;

    private final ItemFixerPlugin plugin;
    private final Inventory inventory;
    private final int ruleIndex;
    private final ItemStack oldItem;
    private final ItemStack newItem;
    private boolean silent;
    private boolean returned;
    private boolean handedOff;

    private ConfirmDeleteMenu(ItemFixerPlugin plugin, Player viewer, int ruleIndex, ItemStack oldItem, ItemStack newItem) {
        this.plugin = plugin;
        this.ruleIndex = ruleIndex;
        this.oldItem = oldItem;
        this.newItem = newItem;
        this.inventory = Bukkit.createInventory(this, SIZE, Messages.text(Messages.CONFIRM_BOOK));
        ItemStack filler = GuiItems.filler(plugin);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
        inventory.setItem(CONFIRM, GuiItems.button(plugin, viewer, Material.LIME_CONCRETE, Messages.CONFIRM_LORE));
        if (oldItem != null && newItem != null) {
            inventory.setItem(ICON, GuiItems.ruleIcon(plugin, viewer, oldItem, newItem));
        }
        inventory.setItem(CANCEL, GuiItems.button(plugin, viewer, Material.RED_CONCRETE, Messages.CANCEL));
    }

    public static void open(ItemFixerPlugin plugin, Player player, int ruleIndex, ItemStack oldItem, ItemStack newItem, boolean sound) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof FixerHolder holder) {
            holder.markSilent();
        }
        ConfirmDeleteMenu menu = new ConfirmDeleteMenu(plugin, player, ruleIndex, oldItem, newItem);
        player.openInventory(menu.inventory);
        if (sound) {
            MenuSounds.open(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (returned || handedOff) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw == CANCEL) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!stillOpen(player)) {
                    return;
                }
                handedOff = true;
                RuleEditorMenu.open(plugin, player, ruleIndex, oldItem, newItem, true);
            });
            return;
        }
        if (raw != CONFIRM) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!stillOpen(player)) {
                return;
            }
            if (ruleIndex < 0 || ruleIndex >= plugin.rules().snapshot().rules().size()) {
                MenuSounds.deny(player);
                return;
            }
            if (!player.hasPermission("itemfixer.admin")) {
                markSilent();
                MenuSounds.deny(player);
                Messages.actionBar(player, Messages.NO_PERMISSION);
                player.closeInventory();
                return;
            }
            returnInputs(player);
            GuiItems.strip(plugin, player);
            plugin.rules().delete(ruleIndex);
            MenuSounds.delete(player);
            Messages.actionBar(player, Messages.DELETED);
            RuleListMenu.open(plugin, player, 0, false);
        });
    }

    private boolean stillOpen(Player player) {
        return player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == this;
    }

    @Override
    public void onDrag(Player player, InventoryDragEvent event) {
    }

    @Override
    public void returnInputs(Player player) {
        if (returned || handedOff) {
            return;
        }
        returned = true;
        give(player, oldItem);
        give(player, newItem);
    }

    private void give(Player player, ItemStack stack) {
        if (GuiItems.isAir(stack) || GuiItems.isTagged(plugin, stack)) {
            return;
        }
        var leftover = player.getInventory().addItem(stack.clone());
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    @Override
    public void markSilent() {
        silent = true;
    }

    @Override
    public boolean consumeSilent() {
        boolean was = silent;
        silent = false;
        return was;
    }
}
