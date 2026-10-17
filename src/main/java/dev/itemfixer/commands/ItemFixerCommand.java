package dev.itemfixer.commands;

import dev.itemfixer.ItemFixerPlugin;
import dev.itemfixer.Messages;
import dev.itemfixer.features.gui.RuleListMenu;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ItemFixerCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "itemfixer.admin";

    private final ItemFixerPlugin plugin;

    public ItemFixerCommand(ItemFixerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage(Messages.text(Messages.NO_PERMISSION));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadSettings();
            plugin.rules().loadAsync(ok -> sender.sendMessage(Messages.text(ok ? Messages.RELOADED : Messages.RELOAD_FAILED)));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("fixall")) {
            plugin.fixer().fixAll(sender);
            return true;
        }
        if (args.length > 0) {
            sender.sendMessage(Messages.text(Messages.USAGE));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.text(Messages.PLAYERS_ONLY));
            return true;
        }
        RuleListMenu.open(plugin, player, 0, true);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN) || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new java.util.ArrayList<>();
        if ("reload".startsWith(prefix)) {
            options.add("reload");
        }
        if ("fixall".startsWith(prefix)) {
            options.add("fixall");
        }
        return options;
    }
}
