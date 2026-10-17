package dev.itemfixer.features.fixer;

import dev.itemfixer.ItemFixerPlugin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class FixRuleRepository {
    public static final int SCHEMA = 1;

    private final ItemFixerPlugin plugin;
    private final Path file;
    private final Object writeLock = new Object();
    private final AtomicLong saveVersion = new AtomicLong();
    private volatile FixSnapshot snapshot = FixSnapshot.empty();

    public FixRuleRepository(ItemFixerPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("rules.yml");
    }

    public FixSnapshot snapshot() {
        return snapshot;
    }

    public boolean hasRules() {
        return !snapshot.rules().isEmpty();
    }

    public void loadAsync(Consumer<Boolean> onMain) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            byte[] bytes = null;
            boolean failed = false;
            try {
                bytes = Files.readAllBytes(file);
            } catch (NoSuchFileException exception) {
                bytes = null;
            } catch (IOException exception) {
                failed = true;
                plugin.getLogger().warning("could not read rules.yml: " + message(exception));
            }
            byte[] payload = bytes;
            boolean readFailed = failed;
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean ok = !readFailed;
                if (payload != null) {
                    try {
                        publish(parse(payload));
                    } catch (Exception exception) {
                        ok = false;
                        plugin.getLogger().warning("could not read rules.yml: " + message(exception));
                    }
                }
                if (onMain != null) {
                    onMain.accept(ok);
                }
            });
        });
    }

    public void add(ItemStack oldItem, ItemStack newItem) {
        List<FixRule> next = new ArrayList<>(snapshot.rules());
        next.add(new FixRule(oldItem, newItem));
        publish(next);
        saveAsync();
    }

    public void replace(int index, ItemStack oldItem, ItemStack newItem) {
        List<FixRule> next = new ArrayList<>(snapshot.rules());
        next.set(index, new FixRule(oldItem, newItem));
        publish(next);
        saveAsync();
    }

    public void delete(int index) {
        List<FixRule> next = new ArrayList<>(snapshot.rules());
        next.remove(index);
        publish(next);
        saveAsync();
    }

    private void publish(List<FixRule> rules) {
        snapshot = FixSnapshot.of(rules);
    }

    private void saveAsync() {
        long version = saveVersion.incrementAndGet();
        String yaml = toYaml(snapshot);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeIfCurrent(version, yaml));
    }

    private void writeIfCurrent(long version, String yaml) {
        synchronized (writeLock) {
            if (saveVersion.get() != version) {
                return;
            }
            try {
                Files.createDirectories(file.getParent());
                Path temp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(temp, yaml, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException exception) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("could not save rules.yml: " + message(exception));
            }
        }
    }

    private String toYaml(FixSnapshot snap) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FixRule rule : snap.rules()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("old", rule.oldTemplate());
            row.put("new", rule.newTemplate());
            rows.add(row);
        }
        yaml.set("rules", rows);
        return yaml.saveToString();
    }

    private List<FixRule> parse(byte[] bytes) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(new String(bytes, StandardCharsets.UTF_8));
        int schema = yaml.getInt("schema-version", SCHEMA);
        if (schema != SCHEMA) {
            throw new IllegalStateException("unsupported rules schema " + schema);
        }
        List<FixRule> rules = new ArrayList<>();
        List<Map<?, ?>> rows = yaml.getMapList("rules");
        for (int index = 0; index < rows.size(); index++) {
            ItemStack oldItem = item(rows.get(index).get("old"));
            ItemStack newItem = item(rows.get(index).get("new"));
            if (oldItem == null || newItem == null) {
                plugin.getLogger().warning("skipped rule " + (index + 1) + " because an item could not be read");
                continue;
            }
            rules.add(new FixRule(oldItem, newItem));
        }
        return rules;
    }

    private static ItemStack item(Object raw) {
        if (raw instanceof ItemStack stack) {
            return stack.getType().isAir() ? null : stack;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                values.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            try {
                ItemStack stack = ItemStack.deserialize(values);
                if (stack == null || stack.getType().isAir()) {
                    return null;
                }
                return stack;
            } catch (RuntimeException exception) {
                return null;
            }
        }
        return null;
    }

    private static String message(Exception exception) {
        String text = exception.getMessage();
        if (text == null || text.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return text;
    }
}
