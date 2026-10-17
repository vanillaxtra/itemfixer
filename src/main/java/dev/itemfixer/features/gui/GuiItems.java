package dev.itemfixer.features.gui;

import dev.itemfixer.ItemFixerPlugin;
import dev.itemfixer.Messages;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class GuiItems {
    private GuiItems() {
    }

    public static ItemStack copy(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack.clone();
    }

    public static ItemStack asTemplate(ItemFixerPlugin plugin, ItemStack stack) {
        ItemStack copy = copy(stack);
        if (copy == null) {
            return null;
        }
        copy.setAmount(1);
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().remove(key(plugin));
            copy.setItemMeta(meta);
        }
        return copy;
    }

    public static ItemStack tag(ItemFixerPlugin plugin, ItemStack stack) {
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        copy.setItemMeta(meta);
        return copy;
    }

    public static boolean isTagged(ItemFixerPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    public static ItemStack button(ItemFixerPlugin plugin, Player player, Material material, String nameKey, String... loreKeys) {
        return button(plugin, player, material, false, nameKey, loreKeys);
    }

    private static ItemStack button(
            ItemFixerPlugin plugin,
            Player player,
            Material material,
            boolean glint,
            String nameKey,
            String... loreKeys) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.text(nameKey));
        if (loreKeys.length > 0) {
            List<Component> lore = new ArrayList<>();
            for (String loreKey : loreKeys) {
                lore.add(Messages.text(loreKey));
            }
            meta.lore(lore);
        }
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        return store(plugin, item, meta);
    }

    public static ItemStack ruleIcon(ItemFixerPlugin plugin, Player player, ItemStack oldItem, ItemStack newItem) {
        ItemStack icon = oldItem.clone();
        icon.setAmount(1);
        ItemMeta meta = icon.getItemMeta();
        Component name = shownName(player, oldItem)
                .append(Component.text(" --> ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .append(shownName(player, newItem));
        meta.displayName(name);
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        if (!lore.isEmpty()) {
            lore.add(Component.text(" "));
        }
        lore.add(Messages.text(Messages.CLICK_EDIT));
        meta.lore(lore);
        return store(plugin, icon, meta);
    }

    private static Component shownName(Player player, ItemStack stack) {
        return GlobalTranslator.render(stack.displayName(), player.locale());
    }

    public static ItemStack addButton(ItemFixerPlugin plugin, Player player) {
        return button(plugin, player, Material.EMERALD, true, Messages.ADD);
    }

    public static ItemStack saveButton(ItemFixerPlugin plugin, Player player, boolean ready) {
        return button(plugin, player, Material.EMERALD, ready, Messages.SAVE);
    }

    public static ItemStack filler(ItemFixerPlugin plugin) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setHideTooltip(true);
        return store(plugin, item, meta);
    }

    private static ItemStack store(ItemFixerPlugin plugin, ItemStack item, ItemMeta meta) {
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static void strip(ItemFixerPlugin plugin, Player player) {
        stripInventory(plugin, player.getInventory());
        stripInventory(plugin, player.getEnderChest());
        if (isTagged(plugin, player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private static void stripInventory(ItemFixerPlugin plugin, Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isTagged(plugin, inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
    }

    public static boolean isAir(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    private static NamespacedKey key(ItemFixerPlugin plugin) {
        return new NamespacedKey(plugin, "gui");
    }
}
