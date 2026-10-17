package dev.itemfixer.features.fixer;

import dev.itemfixer.ItemFixerPlugin;
import dev.itemfixer.Messages;
import dev.itemfixer.features.gui.FixerHolder;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class FixAll {
    private final ItemFixerPlugin plugin;
    private final ItemFixService fixer;
    private final PendingDrops pending;
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final Set<UUID> targeted = new HashSet<>();
    private final Set<UUID> notifyOnJoin = new HashSet<>();
    private final Queue<Path> files = new ArrayDeque<>();
    private final Set<Path> queued = new HashSet<>();
    private final ConcurrentLinkedQueue<Loaded> ready = new ConcurrentLinkedQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger writes = new AtomicInteger();
    private CommandSender sender;
    private ItemBytes items;
    private boolean running;
    private boolean listing;
    private boolean cancelled;

    FixAll(ItemFixerPlugin plugin, ItemFixService fixer, PendingDrops pending) {
        this.plugin = plugin;
        this.fixer = fixer;
        this.pending = pending;
    }

    void noteOnline(UUID playerId) {
        online.add(playerId);
    }

    void announce(Player player) {
        if (notifyOnJoin.remove(player.getUniqueId()) && player.hasPermission("itemfixer.notify")) {
            player.sendMessage(Messages.text(Messages.ITEMS_UPDATED));
        }
    }

    void start(CommandSender sender) {
        if (!fixer.hasRules()) {
            sender.sendMessage(Messages.text(Messages.FIXALL_EMPTY));
            return;
        }
        if (running) {
            sender.sendMessage(Messages.text(Messages.FIXALL_BUSY));
            return;
        }
        cancelled = false;
        running = true;
        this.sender = sender;
        sender.sendMessage(Messages.text(Messages.FIXALL_STARTED));
        items = ItemBytes.probe(plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            if (fixer.skips(player)) {
                continue;
            }
            fixer.queue(player.getUniqueId());
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof FixerHolder)) {
                targeted.add(player.getUniqueId());
            }
        }
        if (items == null || Bukkit.getWorlds().isEmpty()) {
            checkDone();
            return;
        }
        Path folder = Bukkit.getWorlds().get(0).getWorldFolder().toPath().resolve("playerdata");
        Set<UUID> alreadyOnline = new HashSet<>(online);
        listing = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Path> found = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.dat")) {
                for (Path path : stream) {
                    UUID playerId = id(path);
                    if (playerId != null && !alreadyOnline.contains(playerId)) {
                        found.add(path);
                    }
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("could not list player data: " + exception.getMessage());
            }
            List<Path> listed = found;
            Bukkit.getScheduler().runTask(plugin, () -> {
                listing = false;
                if (cancelled || !running) {
                    return;
                }
                for (Path path : listed) {
                    offer(path);
                }
                pump();
                checkDone();
            });
        });
    }

    void cancel() {
        cancelled = true;
        running = false;
        listing = false;
        files.clear();
        queued.clear();
        targeted.clear();
        ready.clear();
        sender = null;
    }

    int consume(int budget) {
        if (!running) {
            return 0;
        }
        int used = 0;
        Loaded loaded;
        while (used < budget && (loaded = ready.poll()) != null) {
            used++;
            apply(loaded);
        }
        pump();
        checkDone();
        return used;
    }

    void finished(UUID playerId) {
        if (targeted.remove(playerId)) {
            checkDone();
        }
    }

    void vanished(UUID playerId) {
        online.remove(playerId);
        if (targeted.remove(playerId)) {
            checkDone();
        }
    }

    boolean running() {
        return running;
    }

    boolean expecting(UUID playerId) {
        return targeted.contains(playerId);
    }

    void menuOpened(UUID playerId) {
        if (targeted.remove(playerId)) {
            checkDone();
        }
    }

    private void apply(Loaded loaded) {
        UUID playerId = id(loaded.path);
        if (playerId != null && (online.contains(playerId) || Bukkit.getPlayer(playerId) != null)) {
            fixer.queue(playerId);
            return;
        }
        try {
            Map<UUID, String> worlds = new HashMap<>();
            for (World world : Bukkit.getWorlds()) {
                worlds.put(world.getUID(), world.getName());
            }
            PlayerFileRepair result = PlayerFileRepair.apply(
                    loaded.bytes, items, plugin.rules().snapshot(), plugin.settings(), worlds, fixer);
            if (result.bytes == null) {
                return;
            }
            UUID id = playerId;
            byte[] out = result.bytes;
            List<org.bukkit.inventory.ItemStack> drops = result.pending;
            writes.incrementAndGet();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean wrote = false;
                if (!cancelled && plugin.isEnabled() && (id == null || !online.contains(id))) {
                    try {
                        write(loaded.path, out);
                        wrote = true;
                    } catch (IOException exception) {
                        plugin.getLogger().warning("could not write " + loaded.path.getFileName() + ": " + exception.getMessage());
                    }
                }
                boolean saved = wrote;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    writes.decrementAndGet();
                    if (saved && id != null && plugin.isEnabled()) {
                        notifyOnJoin.add(id);
                        if (!drops.isEmpty()) {
                            pending.add(id, drops);
                        }
                    }
                    checkDone();
                });
            });
        } catch (Exception exception) {
            plugin.getLogger().warning("skipped " + loaded.path.getFileName() + ": " + exception.getMessage());
        }
    }

    private void pump() {
        if (!running) {
            return;
        }
        int cap = plugin.settings().playersPerTick();
        while (inFlight.get() + ready.size() < cap && !files.isEmpty()) {
            Path path = files.poll();
            queued.remove(path);
            inFlight.incrementAndGet();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                byte[] data = null;
                try {
                    data = Files.readAllBytes(path);
                } catch (IOException exception) {
                    plugin.getLogger().warning("could not read " + path.getFileName() + ": " + exception.getMessage());
                }
                byte[] payload = data;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    inFlight.decrementAndGet();
                    if (!cancelled && payload != null) {
                        ready.add(new Loaded(path, payload));
                    }
                    checkDone();
                });
            });
        }
    }

    private void offer(Path path) {
        if (queued.add(path)) {
            files.add(path);
        }
    }

    private void checkDone() {
        if (!running || listing || !targeted.isEmpty() || !files.isEmpty() || inFlight.get() != 0 || writes.get() != 0 || !ready.isEmpty()) {
            return;
        }
        running = false;
        CommandSender done = sender;
        sender = null;
        if (done != null) {
            done.sendMessage(Messages.text(Messages.FIXALL_DONE));
        }
    }

    private static UUID id(Path path) {
        String name = path.getFileName().toString();
        if (!name.endsWith(".dat")) {
            return null;
        }
        try {
            return UUID.fromString(name.substring(0, name.length() - 4));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void write(Path file, byte[] bytes) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, bytes);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Loaded(Path path, byte[] bytes) {
    }
}
