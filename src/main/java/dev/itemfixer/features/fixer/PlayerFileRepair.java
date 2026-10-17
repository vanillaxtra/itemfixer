package dev.itemfixer.features.fixer;

import dev.itemfixer.configs.RepairSettings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

final class PlayerFileRepair {
    final byte[] bytes;
    final List<ItemStack> pending;

    private PlayerFileRepair(byte[] bytes, List<ItemStack> pending) {
        this.bytes = bytes;
        this.pending = pending;
    }

    static PlayerFileRepair unchanged() {
        return new PlayerFileRepair(null, List.of());
    }

    static PlayerFileRepair apply(
            byte[] file,
            ItemBytes items,
            FixSnapshot snap,
            RepairSettings settings,
            Map<UUID, String> worlds,
            ItemFixService fixer) throws IOException {
        NamedBinaryTag.Saved saved = NamedBinaryTag.read(file);
        if (!allowed(saved.root, settings, worlds)) {
            return unchanged();
        }
        List<ItemStack> pending = new ArrayList<>();
        boolean changed = fixList(saved.root, "Inventory", items, snap, settings, fixer, true, pending);
        if (settings.scanEnderChest()) {
            changed |= fixList(saved.root, "EnderItems", items, snap, settings, fixer, false, pending);
        }
        if (!changed) {
            return unchanged();
        }
        return new PlayerFileRepair(NamedBinaryTag.write(saved), List.copyOf(pending));
    }

    private static boolean allowed(NamedBinaryTag.Compound root, RepairSettings settings, Map<UUID, String> worlds) {
        UUID worldId = worldId(root);
        if (worldId == null) {
            return settings.repairsUnknownWorld();
        }
        String name = worlds.get(worldId);
        if (name == null) {
            return settings.repairsUnknownWorld();
        }
        return settings.repairsWorld(name);
    }

    private static UUID worldId(NamedBinaryTag.Compound root) {
        if (!(root.get("WorldUUIDMost") instanceof NamedBinaryTag.NumberTag most)
                || !(root.get("WorldUUIDLeast") instanceof NamedBinaryTag.NumberTag least)) {
            return null;
        }
        return new UUID(most.asLong(), least.asLong());
    }

    private static boolean fixList(
            NamedBinaryTag.Compound root,
            String key,
            ItemBytes items,
            FixSnapshot snap,
            RepairSettings settings,
            ItemFixService fixer,
            boolean armorSlots,
            List<ItemStack> pending) throws IOException {
        if (!(root.get(key) instanceof NamedBinaryTag.ListTag list)) {
            return false;
        }
        boolean skipArmor = armorSlots && !settings.scansArmorSlots(snap.anyArmor());
        List<NamedBinaryTag.Tag> next = new ArrayList<>();
        List<ItemStack> extras = new ArrayList<>();
        boolean changed = false;
        for (NamedBinaryTag.Tag tag : list.values()) {
            if (!(tag instanceof NamedBinaryTag.Compound compound)) {
                next.add(tag);
                continue;
            }
            if (skipArmor && armorSlot(compound)) {
                next.add(tag);
                continue;
            }
            NamedBinaryTag.Tag slot = compound.remove("Slot");
            ItemStack stack = items.toItem(compound);
            if (slot != null) {
                compound.put("Slot", slot);
            }
            if (stack == null || stack.getType().isAir()) {
                next.add(tag);
                continue;
            }
            ItemFixService.Replacement replacement = fixer.replace(stack, snap);
            if (!replacement.changed) {
                next.add(tag);
                continue;
            }
            changed = true;
            extras.addAll(fixer.extras(replacement));
            if (replacement.placed == null) {
                continue;
            }
            NamedBinaryTag.Compound updated = items.fromItem(replacement.placed);
            if (slot != null) {
                updated.put("Slot", slot);
            }
            next.add(updated);
        }
        if (!extras.isEmpty() && armorSlots) {
            changed |= placeExtras(next, extras, items, pending);
        } else {
            pending.addAll(extras);
        }
        if (!changed) {
            return false;
        }
        root.put(key, new NamedBinaryTag.ListTag(next.isEmpty() ? 0 : 10, next));
        return true;
    }

    private static boolean placeExtras(
            List<NamedBinaryTag.Tag> items,
            List<ItemStack> extras,
            ItemBytes codec,
            List<ItemStack> pending) throws IOException {
        Set<Integer> used = new HashSet<>();
        for (NamedBinaryTag.Tag tag : items) {
            if (tag instanceof NamedBinaryTag.Compound compound && compound.get("Slot") instanceof NamedBinaryTag.NumberTag slot) {
                used.add((int) slot.asLong());
            }
        }
        boolean placedAny = false;
        for (ItemStack extra : extras) {
            int free = -1;
            for (int slot = 0; slot <= 35; slot++) {
                if (!used.contains(slot)) {
                    free = slot;
                    break;
                }
            }
            if (free < 0) {
                pending.add(extra);
                continue;
            }
            NamedBinaryTag.Compound compound = codec.fromItem(extra);
            compound.put("Slot", new NamedBinaryTag.NumberTag(1, free));
            items.add(compound);
            used.add(free);
            placedAny = true;
        }
        return placedAny;
    }

    private static boolean armorSlot(NamedBinaryTag.Compound compound) {
        if (!(compound.get("Slot") instanceof NamedBinaryTag.NumberTag slot)) {
            return false;
        }
        long value = slot.asLong();
        return value >= 100 && value <= 103;
    }
}
