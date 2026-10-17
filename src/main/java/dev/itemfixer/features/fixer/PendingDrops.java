package dev.itemfixer.features.fixer;

import dev.itemfixer.ItemFixerPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class PendingDrops {
    private final ItemFixerPlugin plugin;
    private final File file;
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();
    private final Object writeLock = new Object();
    private final AtomicLong saveVersion = new AtomicLong();

    PendingDrops(ItemFixerPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending.yml");
    }

    void loadAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            byte[] bytes = null;
            try {
                if (file.isFile()) {
                    bytes = Files.readAllBytes(file.toPath());
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("could not read pending.yml: " + exception.getMessage());
            }
            byte[] payload = bytes;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (payload != null) {
                    read(payload);
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    give(player);
                }
            });
        });
    }

    void add(UUID playerId, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return;
        }
        pending.computeIfAbsent(playerId, ignored -> new ArrayList<>()).addAll(stacks);
        saveAsync();
    }

    void give(Player player) {
        List<ItemStack> stacks = pending.remove(player.getUniqueId());
        if (stacks == null || stacks.isEmpty()) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack.clone());
            }
        }
        saveAsync();
    }

    private void read(byte[] bytes) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(bytes, StandardCharsets.UTF_8));
            pending.clear();
            for (String key : yaml.getKeys(false)) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(key);
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                List<ItemStack> stacks = new ArrayList<>();
                List<?> raw = yaml.getList(key);
                if (raw == null) {
                    continue;
                }
                for (Object entry : raw) {
                    if (entry instanceof ItemStack stack && !stack.getType().isAir()) {
                        stacks.add(stack);
                    }
                }
                if (!stacks.isEmpty()) {
                    pending.put(playerId, stacks);
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("could not read pending.yml: " + exception.getMessage());
        }
    }

    private void saveAsync() {
        long version = saveVersion.incrementAndGet();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, List<ItemStack>> entry : pending.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        String text = yaml.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(version, text));
    }

    private void write(long version, String text) {
        synchronized (writeLock) {
            if (saveVersion.get() != version) {
                return;
            }
            try {
                Files.createDirectories(file.toPath().getParent());
                Path temp = file.toPath().resolveSibling("pending.yml.tmp");
                Files.writeString(temp, text, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException exception) {
                    Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("could not save pending.yml: " + exception.getMessage());
            }
        }
    }
}
