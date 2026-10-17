package dev.itemfixer.features.gui;

import dev.itemfixer.ItemFixerPlugin;
import dev.itemfixer.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class RuleEditorMenu implements FixerHolder {
    static final int OLD = 20;
    static final int ARROW = 22;
    static final int NEW = 24;
    private static final int BOOK = 4;
    private static final int BACK = 27;
    private static final int DELETE = 35;
    private static final int SAVE = 31;
    private static final int SIZE = 36;

    private final ItemFixerPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;
    private final int ruleIndex;
    private int generation;
    private boolean silent;
    private boolean returned;
    private boolean handedOff;

    private RuleEditorMenu(ItemFixerPlugin plugin, Player viewer, int ruleIndex, ItemStack oldItem, ItemStack newItem) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.ruleIndex = ruleIndex;
        this.inventory = Bukkit.createInventory(this, SIZE, Messages.text(Messages.TITLE_EDITOR));
        paintShell();
        if (oldItem != null) {
            inventory.setItem(OLD, oldItem.clone());
        }
        if (newItem != null) {
            inventory.setItem(NEW, newItem.clone());
        }
        refreshSave();
    }

    public static void open(ItemFixerPlugin plugin, Player player, int ruleIndex, ItemStack oldItem, ItemStack newItem, boolean sound) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof FixerHolder holder) {
            holder.markSilent();
        }
        RuleEditorMenu menu = new RuleEditorMenu(plugin, player, ruleIndex, oldItem, newItem);
        player.openInventory(menu.inventory);
        if (sound) {
            MenuSounds.open(player);
        }
    }

    private void paintShell() {
        ItemStack filler = GuiItems.filler(plugin);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
        inventory.setItem(OLD, null);
        inventory.setItem(NEW, null);
        inventory.setItem(BOOK, GuiItems.button(
                plugin,
                viewer,
                Material.BOOK,
                Messages.EDITOR_BOOK,
                Messages.EDITOR_LORE_1,
                Messages.EDITOR_LORE_2));
        inventory.setItem(ARROW, GuiItems.button(plugin, viewer, Material.SPECTRAL_ARROW, Messages.ARROW));
        inventory.setItem(BACK, GuiItems.button(plugin, viewer, Material.ARROW, Messages.BACK));
        if (ruleIndex >= 0) {
            inventory.setItem(DELETE, GuiItems.button(plugin, viewer, Material.BARRIER, Messages.DELETE));
        }
        refreshSave();
    }

    private void refreshSave() {
        boolean ready = !GuiItems.isAir(inventory.getItem(OLD)) && !GuiItems.isAir(inventory.getItem(NEW));
        inventory.setItem(SAVE, GuiItems.saveButton(plugin, viewer, ready));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int raw = event.getRawSlot();
        if (raw < 0) {
            if (event.getClick() == ClickType.LEFT) {
                dropCursor(player, false);
            } else if (event.getClick() == ClickType.RIGHT) {
                dropCursor(player, true);
            }
            return;
        }
        if (raw >= SIZE) {
            onBottom(player, event);
            return;
        }
        if (raw == SAVE) {
            trySave(player);
            return;
        }
        if (raw == BACK) {
            later(player, () -> RuleListMenu.open(plugin, player, 0, true));
            return;
        }
        if (raw == DELETE && ruleIndex >= 0) {
            openConfirm(player);
            return;
        }
        InventoryAction action = event.getAction();
        if (action == InventoryAction.DROP_ALL_CURSOR || action == InventoryAction.DROP_ONE_CURSOR) {
            dropCursor(player, action == InventoryAction.DROP_ONE_CURSOR);
            return;
        }
        if (raw != OLD && raw != NEW) {
            return;
        }
        if (action == InventoryAction.DROP_ALL_SLOT || action == InventoryAction.DROP_ONE_SLOT) {
            dropSlot(player, raw, action == InventoryAction.DROP_ONE_SLOT);
            return;
        }
        ClickType click = event.getClick();
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            takeOut(player, raw);
            return;
        }
        if (click != ClickType.LEFT && click != ClickType.RIGHT) {
            return;
        }
        swap(player, raw);
    }

    private void later(Player player, Runnable action) {
        int generation = nextGeneration();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!stillOpen(player, generation)) {
                return;
            }
            if (!player.hasPermission("itemfixer.admin")) {
                deny(player);
                return;
            }
            action.run();
        });
    }

    private void onBottom(Player player, InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            shiftIn(player, event);
            return;
        }
        if (action == InventoryAction.COLLECT_TO_CURSOR || event.getClick() == ClickType.DOUBLE_CLICK) {
            return;
        }
        if (isDrop(action)) {
            ItemStack dropping = action == InventoryAction.DROP_ALL_CURSOR || action == InventoryAction.DROP_ONE_CURSOR
                    ? player.getItemOnCursor()
                    : event.getCurrentItem();
            if (GuiItems.isTagged(plugin, dropping)) {
                return;
            }
            event.setCancelled(false);
            return;
        }
        event.setCancelled(false);
    }

    private static boolean isDrop(InventoryAction action) {
        return action == InventoryAction.DROP_ALL_CURSOR
                || action == InventoryAction.DROP_ONE_CURSOR
                || action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_SLOT;
    }

    private void swap(Player player, int slot) {
        ItemStack cursor = GuiItems.copy(player.getItemOnCursor());
        ItemStack current = GuiItems.copy(inventory.getItem(slot));
        if (cursor == null && current == null) {
            return;
        }
        int generation = nextGeneration();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!stillOpen(player, generation)) {
                return;
            }
            inventory.setItem(slot, cursor);
            setCursor(player, current);
            refreshSave();
            MenuSounds.place(player);
        });
    }

    private void takeOut(Player player, int slot) {
        ItemStack current = GuiItems.copy(inventory.getItem(slot));
        if (current == null) {
            return;
        }
        int generation = nextGeneration();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!stillOpen(player, generation)) {
                return;
            }
            ItemStack now = inventory.getItem(slot);
            if (GuiItems.isAir(now) || now.getAmount() != current.getAmount() || !now.isSimilar(current)) {
                return;
            }
            inventory.setItem(slot, null);
            if (!GuiItems.isTagged(plugin, now)) {
                var leftover = player.getInventory().addItem(now.clone());
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
            refreshSave();
            MenuSounds.place(player);
        });
    }

    private void shiftIn(Player player, InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        ItemStack moving = GuiItems.copy(event.getCurrentItem());
        if (clicked == null || moving == null) {
            return;
        }
        int target = GuiItems.isAir(inventory.getItem(OLD)) ? OLD : GuiItems.isAir(inventory.getItem(NEW)) ? NEW : -1;
        if (target < 0) {
            return;
        }
        int from = event.getSlot();
        int generation = nextGeneration();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!stillOpen(player, generation)) {
                return;
            }
            ItemStack now = clicked.getItem(from);
            if (now == null || now.getAmount() != moving.getAmount() || !now.isSimilar(moving)) {
                return;
            }
            if (!GuiItems.isAir(inventory.getItem(target))) {
                return;
            }
            clicked.setItem(from, null);
            inventory.setItem(target, moving);
            refreshSave();
            MenuSounds.place(player);
        });
    }

    @Override
    public void onDrag(Player player, InventoryDragEvent event) {
        if (event.getRawSlots().size() != 1) {
            return;
        }
        int raw = event.getRawSlots().iterator().next();
        if (raw != OLD && raw != NEW) {
            return;
        }
        ItemStack placed = GuiItems.copy(event.getNewItems().get(raw));
        ItemStack cursor = GuiItems.copy(event.getCursor());
        int generation = nextGeneration();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!stillOpen(player, generation)) {
                return;
            }
            inventory.setItem(raw, placed);
            setCursor(player, cursor);
            refreshSave();
            MenuSounds.place(player);
        });
    }

    private void trySave(Player player) {
        ItemStack oldItem = GuiItems.asTemplate(plugin, inventory.getItem(OLD));
        ItemStack newItem = GuiItems.asTemplate(plugin, inventory.getItem(NEW));
        if (oldItem == null || newItem == null) {
            MenuSounds.deny(player);
            Messages.actionBar(player, Messages.NEED_BOTH);
            return;
        }
        int generation = nextGeneration();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!stillOpen(player, generation)) {
                return;
            }
            if (!player.hasPermission("itemfixer.admin")) {
                deny(player);
                return;
            }
            if (ruleIndex >= 0 && ruleIndex >= plugin.rules().snapshot().rules().size()) {
                MenuSounds.deny(player);
                return;
            }
            returnInputs(player);
            GuiItems.strip(plugin, player);
            if (ruleIndex < 0) {
                plugin.rules().add(oldItem, newItem);
            } else {
                plugin.rules().replace(ruleIndex, oldItem, newItem);
            }
            plugin.fixer().sweep();
            MenuSounds.save(player);
            Messages.actionBar(player, Messages.SAVED);
            RuleListMenu.open(plugin, player, 0, false);
        });
    }

    private void openConfirm(Player player) {
        ItemStack oldItem = GuiItems.copy(inventory.getItem(OLD));
        ItemStack newItem = GuiItems.copy(inventory.getItem(NEW));
        later(player, () -> {
            handedOff = true;
            ConfirmDeleteMenu.open(plugin, player, ruleIndex, oldItem, newItem, true);
        });
    }

    private void dropSlot(Player player, int slot, boolean one) {
        ItemStack current = GuiItems.copy(inventory.getItem(slot));
        if (current == null) {
            return;
        }
        int generation = nextGeneration();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!stillOpen(player, generation)) {
                return;
            }
            ItemStack now = inventory.getItem(slot);
            if (GuiItems.isAir(now) || now.getAmount() != current.getAmount() || !now.isSimilar(current)) {
                return;
            }
            if (GuiItems.isTagged(plugin, now)) {
                inventory.setItem(slot, one && now.getAmount() > 1 ? remainder(now) : null);
            } else {
                ItemStack dropping = now.clone();
                if (one && now.getAmount() > 1) {
                    dropping.setAmount(1);
                    inventory.setItem(slot, remainder(now));
                } else {
                    inventory.setItem(slot, null);
                }
                player.getWorld().dropItemNaturally(player.getLocation(), dropping);
            }
            refreshSave();
            MenuSounds.clear(player);
        });
    }

    private void dropCursor(Player player, boolean one) {
        ItemStack cursor = GuiItems.copy(player.getItemOnCursor());
        if (cursor == null) {
            return;
        }
        int generation = nextGeneration();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!stillOpen(player, generation)) {
                return;
            }
            ItemStack now = player.getItemOnCursor();
            if (GuiItems.isAir(now) || now.getAmount() != cursor.getAmount() || !now.isSimilar(cursor)) {
                return;
            }
            if (GuiItems.isTagged(plugin, now)) {
                setCursor(player, one && now.getAmount() > 1 ? remainder(now) : null);
            } else {
                ItemStack dropping = now.clone();
                if (one && now.getAmount() > 1) {
                    dropping.setAmount(1);
                    setCursor(player, remainder(now));
                } else {
                    setCursor(player, null);
                }
                player.getWorld().dropItemNaturally(player.getLocation(), dropping);
            }
            MenuSounds.clear(player);
        });
    }

    private static ItemStack remainder(ItemStack stack) {
        ItemStack left = stack.clone();
        left.setAmount(stack.getAmount() - 1);
        return left;
    }

    private void deny(Player player) {
        markSilent();
        MenuSounds.deny(player);
        Messages.actionBar(player, Messages.NO_PERMISSION);
        player.closeInventory();
    }

    private static void setCursor(Player player, ItemStack stack) {
        player.setItemOnCursor(stack == null ? new ItemStack(Material.AIR) : stack);
    }

    private boolean stillOpen(Player player, int generation) {
        return generation == this.generation
                && player.isOnline()
                && player.getOpenInventory().getTopInventory().getHolder() == this;
    }

    private int nextGeneration() {
        return ++generation;
    }

    @Override
    public void returnInputs(Player player) {
        if (returned || handedOff) {
            return;
        }
        returned = true;
        give(player, inventory.getItem(OLD));
        give(player, inventory.getItem(NEW));
        inventory.setItem(OLD, null);
        inventory.setItem(NEW, null);
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
