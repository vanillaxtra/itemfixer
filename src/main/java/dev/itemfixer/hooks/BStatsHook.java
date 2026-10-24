package dev.itemfixer.hooks;

import dev.itemfixer.ItemFixerPlugin;
import org.bstats.bukkit.Metrics;

public final class BStatsHook {
    private BStatsHook() {
    }


    public static void start(ItemFixerPlugin plugin) {
        try {
            new Metrics(plugin, 34910);
        } catch (LinkageError | Exception failure) {
            plugin.getLogger().warning("bStats metrics could not start: " + failure.getMessage());
        }
    }
}
