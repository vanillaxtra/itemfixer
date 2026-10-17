package dev.itemfixer.features.gui;

import dev.itemfixer.ItemFixerPlugin;
import dev.itemfixer.Messages;
import dev.itemfixer.features.fixer.FixRule;
import dev.itemfixer.features.fixer.FixSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class RuleListMenu implements FixerHolder {
    public static final int SIZE = 54;
    private static final int BOOK = 4;
    private static final int FIRST = 9;
    private static final int LAST = 44;
    private static final int PAGE_SIZE = LAST - FIRST + 1;
    private static final int PREVIOUS = 45;
    private static final int NEXT = 53;

    private final ItemFixerPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;
    private final int page;
    private boolean silent;

    private RuleListMenu(ItemFixerPlugin plugin, Player viewer, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, SIZE, Messages.text(Messages.TITLE_LIST));
        paint();
    }

    public static void open(ItemFixerPlugin plugin, Player player, int page, boolean sound) {
        markCurrentSilent(player);
        RuleListMenu menu = new RuleListMenu(plugin, player, clamp(plugin, page));
        player.openInventory(menu.inventory);
        if (sound) {
            MenuSounds.open(player);
        }
    }

    private static int clamp(ItemFixerPlugin plugin, int page) {
        int last = lastPage(plugin.rules().snapshot().rules().size());
        if (page < 0) {
            return 0;
        }
        return Math.min(page, last);
    }

    private static int lastPage(int rules) {
        int entries = rules + 1;
        return Math.max(0, (entries - 1) / PAGE_SIZE);
    }

    private static boolean hasNext(int rules, int page) {
        return (page + 1) * PAGE_SIZE < rules + 1;
    }

    private static void markCurrentSilent(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof FixerHolder holder) {
            holder.markSilent();
        }
    }

    private void paint() {
        ItemStack filler = GuiItems.filler(plugin);
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, filler.clone());
        }
        for (int slot = 45; slot < SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
        for (int slot = FIRST; slot <= LAST; slot++) {
            inventory.setItem(slot, null);
        }
        inventory.setItem(BOOK, GuiItems.button(
                plugin,
                viewer,
                Material.BOOK,
                Messages.HELP_NAME,
                Messages.LIST_LORE_1));
        FixSnapshot snap = plugin.rules().snapshot();
        int rules = snap.rules().size();
        int start = page * PAGE_SIZE;
        for (int offset = 0; offset < PAGE_SIZE; offset++) {
            int entry = start + offset;
            int slot = FIRST + offset;
            if (entry < rules) {
                FixRule rule = snap.rules().get(entry);
                inventory.setItem(slot, GuiItems.ruleIcon(plugin, viewer, rule.oldTemplate(), rule.newTemplate()));
            } else if (entry == rules) {
                inventory.setItem(slot, GuiItems.addButton(plugin, viewer));
            }
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS, GuiItems.button(plugin, viewer, Material.ARROW, Messages.PREVIOUS));
        }
        if (hasNext(rules, page)) {
            inventory.setItem(NEXT, GuiItems.button(plugin, viewer, Material.ARROW, Messages.NEXT));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= SIZE) {
            return;
        }
        int rules = plugin.rules().snapshot().rules().size();
        if (raw == PREVIOUS && page > 0) {
            turn(player, page - 1);
            return;
        }
        if (raw == NEXT && hasNext(rules, page)) {
            turn(player, page + 1);
            return;
        }
        if (raw < FIRST || raw > LAST) {
            return;
        }
        int entry = page * PAGE_SIZE + (raw - FIRST);
        if (entry == rules) {
            later(player, () -> RuleEditorMenu.open(plugin, player, -1, null, null, true));
            return;
        }
        if (entry > rules) {
            return;
        }
        FixRule rule = plugin.rules().snapshot().rules().get(entry);
        ItemStack oldItem = GuiItems.tag(plugin, rule.oldTemplate().clone());
        ItemStack newItem = GuiItems.tag(plugin, rule.newTemplate().clone());
        later(player, () -> RuleEditorMenu.open(plugin, player, entry, oldItem, newItem, true));
    }

    private void turn(Player player, int target) {
        int last = lastPage(plugin.rules().snapshot().rules().size());
        if (target < 0 || target > last || target == page) {
            return;
        }
        later(player, () -> {
            RuleListMenu.open(plugin, player, target, false);
            MenuSounds.page(player);
        });
    }

    private void later(Player player, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (player.getOpenInventory().getTopInventory().getHolder() != this) {
                return;
            }
            if (!player.hasPermission("itemfixer.admin")) {
                markSilent();
                MenuSounds.deny(player);
                Messages.actionBar(player, Messages.NO_PERMISSION);
                player.closeInventory();
                return;
            }
            action.run();
        });
    }

    @Override
    public void onDrag(Player player, InventoryDragEvent event) {
    }

    @Override
    public void returnInputs(Player player) {
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
