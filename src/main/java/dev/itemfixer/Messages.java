package dev.itemfixer;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Messages {
    public static final String ITEMS_UPDATED = "chat.items-updated";
    public static final String UPDATE_AVAILABLE = "chat.update-available";
    public static final String PLAYERS_ONLY = "command.players-only";
    public static final String NO_PERMISSION = "command.no-permission";
    public static final String RELOADED = "command.reloaded";
    public static final String RELOAD_FAILED = "command.reload-failed";
    public static final String USAGE = "command.usage";
    public static final String FIXALL_STARTED = "command.fixall-started";
    public static final String FIXALL_DONE = "command.fixall-done";
    public static final String FIXALL_BUSY = "command.fixall-busy";
    public static final String FIXALL_EMPTY = "command.fixall-empty";
    public static final String TITLE_LIST = "gui.title.list";
    public static final String TITLE_EDITOR = "gui.title.editor";
    public static final String HELP_NAME = "gui.help-name";
    public static final String LIST_LORE_1 = "gui.list-lore";
    public static final String CLICK_EDIT = "gui.click-edit";
    public static final String EDITOR_BOOK = "gui.editor-book";
    public static final String EDITOR_LORE_1 = "gui.editor-lore-1";
    public static final String EDITOR_LORE_2 = "gui.editor-lore-2";
    public static final String BACK = "gui.back";
    public static final String CONFIRM_BOOK = "gui.confirm-title";
    public static final String CONFIRM_LORE = "gui.confirm";
    public static final String CANCEL = "gui.cancel";
    public static final String SAVED = "gui.saved";
    public static final String DELETED = "gui.deleted";
    public static final String NEED_BOTH = "gui.need-both";
    public static final String ARROW = "gui.arrow";
    public static final String SAVE = "gui.save";
    public static final String DELETE = "gui.delete";
    public static final String ADD = "gui.add";
    public static final String PREVIOUS = "gui.previous";
    public static final String NEXT = "gui.next";

    private static volatile FileConfiguration file = new YamlConfiguration();

    public void load(JavaPlugin plugin) {
        File target = new File(plugin.getDataFolder(), "messages.yml");
        int bundled = bundledVersion(plugin);
        if (!target.exists() || version(target) < bundled) {
            plugin.saveResource("messages.yml", true);
            plugin.getLogger().info("updated messages.yml");
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(target);
            file = loaded;
        } catch (Exception exception) {
            plugin.getLogger().warning("could not read messages.yml, keeping the previous text");
        }
    }

    private static int bundledVersion(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in == null) {
                return 0;
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            return bundled.getInt("version");
        } catch (Exception exception) {
            return 0;
        }
    }

    private static int version(File target) {
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(target);
        } catch (Exception exception) {
            return 0;
        }
        return loaded.getInt("version");
    }

    public static Component text(String path) {
        String raw = file.getString(path);
        if (raw == null) {
            raw = path;
        }
        try {
            return readable(MiniMessage.miniMessage().deserialize("<!italic>" + raw));
        } catch (Exception exception) {
            return Component.text(raw).decoration(TextDecoration.ITALIC, false);
        }
    }

    
    private static Component readable(Component component) {
        return bake(component, Style.empty());
    }

    private static Component bake(Component component, Style parent) {
        Style style = component.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (style.color() == null) {
            style = style.color(NamedTextColor.GRAY);
        }
        if (style.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            style = style.decoration(TextDecoration.ITALIC, false);
        }
        List<Component> children = new ArrayList<>(component.children().size());
        for (Component child : component.children()) {
            children.add(bake(child, style));
        }
        return component.style(style).children(children);
    }

    public static void actionBar(Player player, String path) {
        player.sendActionBar(text(path));
    }
}
