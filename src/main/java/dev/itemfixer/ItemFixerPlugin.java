package dev.itemfixer;

import dev.itemfixer.commands.ItemFixerCommand;
import dev.itemfixer.configs.PluginConfig;
import dev.itemfixer.configs.RepairSettings;
import dev.itemfixer.features.fixer.FixRuleRepository;
import dev.itemfixer.features.fixer.ItemFixService;
import dev.itemfixer.features.gui.FixerHolder;
import dev.itemfixer.listeners.FixerGuiClickListener;
import dev.itemfixer.listeners.FixerGuiCloseListener;
import dev.itemfixer.listeners.FixerGuiDragListener;
import dev.itemfixer.listeners.ForeignInventoryCloseListener;
import dev.itemfixer.listeners.ItemPickupListener;
import dev.itemfixer.listeners.PlayerJoinListener;
import dev.itemfixer.listeners.PlayerQuitListener;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ItemFixerPlugin extends JavaPlugin {
    private final Messages messages = new Messages();
    private final PluginConfig config = new PluginConfig(this);
    private FixRuleRepository rules;
    private ItemFixService fixer;
    private BukkitTask sweepTask;
    private int sweepSeconds = -1;

    @Override
    public void onEnable() {
        messages.load(this);
        saveDefaultConfig();
        config.load();
        rules = new FixRuleRepository(this);
        fixer = new ItemFixService(this, rules);
        var pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new PlayerJoinListener(this), this);
        pluginManager.registerEvents(new PlayerQuitListener(this), this);
        pluginManager.registerEvents(new ItemPickupListener(this), this);
        pluginManager.registerEvents(new ForeignInventoryCloseListener(this), this);
        pluginManager.registerEvents(new FixerGuiClickListener(this), this);
        pluginManager.registerEvents(new FixerGuiDragListener(this), this);
        pluginManager.registerEvents(new FixerGuiCloseListener(this), this);
        PluginCommand command = getCommand("itemfixer");
        if (command == null) {
            getLogger().severe("itemfixer command missing from plugin.yml");
            pluginManager.disablePlugin(this);
            return;
        }
        ItemFixerCommand executor = new ItemFixerCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        for (Player player : Bukkit.getOnlinePlayers()) {
            fixer.noteOnline(player.getUniqueId());
        }
        fixer.loadPending();
        rules.loadAsync(ok -> fixer.sweep());
        Bukkit.getScheduler().runTaskTimer(this, fixer::tick, 1L, 1L);
        scheduleSweep();
    }

    // reload config
    public boolean reloadSettings() {
        messages.load(this);
        boolean ok = config.load();
        if (ok) {
            scheduleSweep();
        }
        return ok;
    }

    public RepairSettings settings() {
        return config.current();
    }

    private void scheduleSweep() {
        int seconds = config.current().cycleSeconds();
        if (sweepTask != null && sweepSeconds == seconds) {
            return;
        }
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        long period = seconds * 20L;
        sweepTask = Bukkit.getScheduler().runTaskTimer(this, fixer::sweep, period, period);
        sweepSeconds = seconds;
    }

    @Override
    public void onDisable() {
        if (fixer != null) {
            fixer.cancelFixAll();
        }
        Bukkit.getScheduler().cancelTasks(this);
        if (fixer != null) {
            for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof FixerHolder) {
                    player.closeInventory();
                }
            }
        }
    }

    public FixRuleRepository rules() {
        return rules;
    }

    public ItemFixService fixer() {
        return fixer;
    }
}
