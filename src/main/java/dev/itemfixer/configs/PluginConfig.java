package dev.itemfixer.configs;

import dev.itemfixer.ItemFixerPlugin;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PluginConfig {
    private final ItemFixerPlugin plugin;
    private volatile RepairSettings current = RepairSettings.defaults();

    public PluginConfig(ItemFixerPlugin plugin) {
        this.plugin = plugin;
    }

    public RepairSettings current() {
        return current;
    }

    public boolean load() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) {
            plugin.saveDefaultConfig();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("could not read config.yml, keeping the current settings: " + exception.getMessage());
            return false;
        }
        current = read(yaml, plugin.getLogger());
        return true;
    }

    private static RepairSettings read(YamlConfiguration yaml, Logger logger) {
        List<String> bypass = strings(yaml, "bypass-permissions", List.of("itemfixer.bypass"), logger);
        boolean whitelist = whitelist(yaml, logger);
        Set<String> worlds = new HashSet<>();
        for (String world : strings(yaml, "worlds.list", List.of(), logger)) {
            worlds.add(world.toLowerCase(Locale.ROOT));
        }
        boolean enderChest = bool(yaml, "scan.ender-chest", false, logger);
        boolean skipArmor = bool(yaml, "optimisations.skip-armor-without-equipable", true, logger);
        int cycleSeconds = positive(yaml, "cycle-seconds", RepairSettings.DEFAULT_CYCLE_SECONDS, logger);
        int playersPerTick = positive(yaml, "players-per-tick", RepairSettings.DEFAULT_PLAYERS_PER_TICK, logger);
        boolean updateChecker = bool(yaml, "update-checker", true, logger);
        return new RepairSettings(bypass, whitelist, worlds, enderChest, skipArmor, cycleSeconds, playersPerTick, updateChecker);
    }

    private static boolean whitelist(YamlConfiguration yaml, Logger logger) {
        if (!yaml.isSet("worlds.mode")) {
            logger.warning("config.yml missing worlds.mode, using blacklist");
            return false;
        }
        String mode = yaml.getString("worlds.mode", "");
        if (mode.equalsIgnoreCase("blacklist")) {
            return false;
        }
        if (mode.equalsIgnoreCase("whitelist")) {
            return true;
        }
        logger.warning("config.yml worlds.mode must be blacklist or whitelist, using blacklist");
        return false;
    }

    private static List<String> strings(YamlConfiguration yaml, String path, List<String> fallback, Logger logger) {
        if (!yaml.isSet(path)) {
            logger.warning("config.yml missing " + path + ", using " + fallback);
            return fallback;
        }
        if (!yaml.isList(path)) {
            logger.warning("config.yml " + path + " must be a list, using " + fallback);
            return fallback;
        }
        return yaml.getStringList(path).stream().filter(entry -> entry != null && !entry.isBlank()).toList();
    }

    private static boolean bool(YamlConfiguration yaml, String path, boolean fallback, Logger logger) {
        if (!yaml.isSet(path)) {
            logger.warning("config.yml missing " + path + ", using " + fallback);
            return fallback;
        }
        if (!yaml.isBoolean(path)) {
            logger.warning("config.yml " + path + " must be true or false, using " + fallback);
            return fallback;
        }
        return yaml.getBoolean(path);
    }

    private static int positive(YamlConfiguration yaml, String path, int fallback, Logger logger) {
        if (!yaml.isSet(path)) {
            logger.warning("config.yml missing " + path + ", using " + fallback);
            return fallback;
        }
        if (!yaml.isInt(path) || yaml.getInt(path) < 1) {
            logger.warning("config.yml " + path + " must be at least 1, using " + fallback);
            return fallback;
        }
        return yaml.getInt(path);
    }
}
