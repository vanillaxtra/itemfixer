package dev.itemfixer.features.fixer;

import org.bukkit.inventory.ItemStack;

public final class FixRule {
    private final ItemStack oldItem;
    private final ItemStack newItem;

    public FixRule(ItemStack oldItem, ItemStack newItem) {
        this.oldItem = normalize(oldItem);
        this.newItem = normalize(newItem);
    }

    private static ItemStack normalize(ItemStack stack) {
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return copy;
    }

    /** Live template. Do not mutate. */
    public ItemStack oldTemplate() {
        return oldItem;
    }

    /** Live template. Do not mutate. */
    public ItemStack newTemplate() {
        return newItem;
    }
}
