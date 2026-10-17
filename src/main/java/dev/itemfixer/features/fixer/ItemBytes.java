package dev.itemfixer.features.fixer;

import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class ItemBytes {
    private final boolean named;
    private final String rootName;

    private ItemBytes(boolean named, String rootName) {
        this.named = named;
        this.rootName = rootName;
    }

    static ItemBytes probe(JavaPlugin plugin) {
        try {
            byte[] data = Bukkit.getUnsafe().serializeItem(new ItemStack(Material.STONE));
            if (Bukkit.getUnsafe().deserializeItem(data).getType() != Material.STONE) {
                plugin.getLogger().warning("offline player files will be skipped: unrecognised item format");
                return null;
            }
            if (roundTrip(data, true)) {
                return new ItemBytes(true, NamedBinaryTag.read(data).name);
            }
            if (roundTrip(data, false)) {
                return new ItemBytes(false, "");
            }
        } catch (RuntimeException | IOException exception) {
            plugin.getLogger().warning("offline player files will be skipped: " + exception.getMessage());
            return null;
        }
        plugin.getLogger().warning("offline player files will be skipped: unrecognised item format");
        return null;
    }

    private static boolean roundTrip(byte[] data, boolean named) {
        try {
            NamedBinaryTag.Compound compound = named ? NamedBinaryTag.read(data).root : NamedBinaryTag.readBody(data);
            String name = named ? NamedBinaryTag.read(data).name : "";
            byte[] again = named
                    ? NamedBinaryTag.write(new NamedBinaryTag.Saved(name, compound, false))
                    : NamedBinaryTag.writeBody(compound);
            ItemStack stack = Bukkit.getUnsafe().deserializeItem(again);
            return stack != null && stack.getType() == Material.STONE;
        } catch (RuntimeException | IOException exception) {
            return false;
        }
    }

    ItemStack toItem(NamedBinaryTag.Compound compound) throws IOException {
        byte[] data = named
                ? NamedBinaryTag.write(new NamedBinaryTag.Saved(rootName, compound, false))
                : NamedBinaryTag.writeBody(compound);
        return Bukkit.getUnsafe().deserializeItem(data);
    }

    NamedBinaryTag.Compound fromItem(ItemStack stack) throws IOException {
        byte[] data = Bukkit.getUnsafe().serializeItem(stack);
        if (named) {
            return NamedBinaryTag.read(data).root;
        }
        return NamedBinaryTag.readBody(data);
    }
}
