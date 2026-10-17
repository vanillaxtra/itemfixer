package dev.itemfixer.features.fixer;

import dev.itemfixer.ItemFixerPlugin;
import dev.itemfixer.Messages;
import dev.itemfixer.configs.RepairSettings;
import dev.itemfixer.features.gui.FixerHolder;
import dev.itemfixer.features.gui.GuiItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ItemFixService {
    private final ItemFixerPlugin plugin;
    private final FixRuleRepository rules;
    private final PendingDrops pending;
    private final FixAll fixAll;
    private final Set<UUID> dirty = new HashSet<>();

    public ItemFixService(ItemFixerPlugin plugin, FixRuleRepository rules) {
        this.plugin = plugin;
        this.rules = rules;
        this.pending = new PendingDrops(plugin);
        this.fixAll = new FixAll(plugin, this, pending);
    }

    public void loadPending() {
        pending.loadAsync();
    }

    public void fixAll(org.bukkit.command.CommandSender sender) {
        fixAll.start(sender);
    }

    public void cancelFixAll() {
        fixAll.cancel();
    }

    public void noteOnline(UUID playerId) {
        fixAll.noteOnline(playerId);
    }

    public void givePending(Player player) {
        pending.give(player);
        fixAll.announce(player);
    }

    public boolean hasRules() {
        return rules.hasRules();
    }

    public void markDirty(UUID playerId) {
        if (!rules.hasRules()) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || skipped(player)) {
            return;
        }
        dirty.add(playerId);
    }

    public void forget(UUID playerId) {
        dirty.remove(playerId);
        fixAll.vanished(playerId);
    }

    void queue(UUID playerId) {
        if (rules.hasRules()) {
            dirty.add(playerId);
        }
    }

    public void sweep() {
        if (!rules.hasRules()) {
            return;
        }
        RepairSettings settings = plugin.settings();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!skipped(player, settings)) {
                dirty.add(player.getUniqueId());
            }
        }
    }

    public void tick() {
        if (!rules.hasRules()) {
            dirty.clear();
            fixAll.cancel();
            return;
        }
        RepairSettings settings = plugin.settings();
        int budget = settings.playersPerTick();
        int used = 0;
        if (fixAll.running()) {
            used = repair(settings, budget, true);
            used += fixAll.consume(budget - used);
        }
        if (used < budget) {
            repair(settings, budget - used, false);
        }
    }

    private int repair(RepairSettings settings, int budget, boolean onlyTargets) {
        if (budget <= 0 || dirty.isEmpty()) {
            return 0;
        }
        List<UUID> finished = new ArrayList<>();
        int scanned = 0;
        for (UUID playerId : dirty) {
            if (scanned >= budget) {
                break;
            }
            boolean target = fixAll.expecting(playerId);
            if (onlyTargets != target) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                finished.add(playerId);
                fixAll.vanished(playerId);
                continue;
            }
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof FixerHolder) {
                fixAll.menuOpened(playerId);
                continue;
            }
            finished.add(playerId);
            scanned++;
            fixAll.finished(playerId);
            if (!skipped(player, settings)) {
                fix(player, settings);
            }
        }
        dirty.removeAll(finished);
        return scanned;
    }

    boolean skips(Player player) {
        return skipped(player, plugin.settings());
    }

    private boolean skipped(Player player) {
        return skips(player);
    }

    private boolean skipped(Player player, RepairSettings settings) {
        return settings.bypasses(player::hasPermission) || !settings.repairsWorld(player.getWorld().getName());
    }

    private void fix(Player player, RepairSettings settings) {
        FixSnapshot snap = rules.snapshot();
        if (snap.rules().isEmpty()) {
            return;
        }
        boolean changed = fixInventory(player, player.getInventory(), snap, !settings.scansArmorSlots(snap.anyArmor()));
        if (settings.scanEnderChest()) {
            changed |= fixInventory(player, player.getEnderChest(), snap, false);
        }
        changed |= fixCursor(player, snap);
        if (changed && player.hasPermission("itemfixer.notify")) {
            player.sendMessage(Messages.text(Messages.ITEMS_UPDATED));
        }
    }

    private boolean fixInventory(Player player, Inventory inventory, FixSnapshot snap, boolean skipArmorSlots) {
        boolean changed = false;
        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            if (skipArmorSlots && slot >= 36 && slot <= 39) {
                continue;
            }
            ItemStack current = inventory.getItem(slot);
            ItemStack updated = convert(player, current, snap);
            if (updated != current) {
                inventory.setItem(slot, updated);
                changed = true;
            }
        }
        return changed;
    }

    private boolean fixCursor(Player player, FixSnapshot snap) {
        ItemStack current = player.getItemOnCursor();
        ItemStack updated = convert(player, current, snap);
        if (updated == current) {
            return false;
        }
        player.setItemOnCursor(updated);
        return true;
    }

    private ItemStack convert(Player player, ItemStack stack, FixSnapshot snap) {
        Replacement replacement = replace(stack, snap);
        if (!replacement.changed) {
            return stack;
        }
        for (ItemStack drop : extras(replacement)) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        return replacement.placed;
    }

    Replacement replace(ItemStack stack, FixSnapshot snap) {
        if (stack == null || stack.getType().isAir() || GuiItems.isTagged(plugin, stack)) {
            return Replacement.keep(stack);
        }
        int[] candidates = snap.index().candidates(stack.getType().name());
        if (candidates.length == 0) {
            return Replacement.keep(stack);
        }
        int match = RuleIndex.firstMatch(candidates, index -> snap.rules().get(index).oldTemplate().isSimilar(stack));
        if (match < 0) {
            return Replacement.keep(stack);
        }
        ItemStack template = snap.rules().get(match).newTemplate();
        int keep = RuleIndex.clampedAmount(stack.getAmount(), template.getMaxStackSize());
        int extra = RuleIndex.leftover(stack.getAmount(), template.getMaxStackSize());
        if (extra == 0 && keep == stack.getAmount() && stack.isSimilar(template)) {
            return Replacement.keep(stack);
        }
        ItemStack placed = null;
        if (keep >= 1) {
            placed = template.clone();
            placed.setAmount(keep);
        }
        return new Replacement(placed, template, extra, true);
    }

    List<ItemStack> extras(Replacement replacement) {
        if (!replacement.changed || replacement.template == null || replacement.extra < 1) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>();
        int max = Math.max(1, replacement.template.getMaxStackSize());
        int left = replacement.extra;
        while (left > 0) {
            int amount = Math.min(left, max);
            ItemStack drop = replacement.template.clone();
            drop.setAmount(amount);
            stacks.add(drop);
            left -= amount;
        }
        return stacks;
    }

    static final class Replacement {
        final ItemStack placed;
        final ItemStack template;
        final int extra;
        final boolean changed;

        private Replacement(ItemStack placed, ItemStack template, int extra, boolean changed) {
            this.placed = placed;
            this.template = template;
            this.extra = extra;
            this.changed = changed;
        }

        static Replacement keep(ItemStack stack) {
            return new Replacement(stack, null, 0, false);
        }
    }
}
