package dev.itemfixer.configs;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/** Immutable repair switches. Built on the main thread and swapped in one write. */
public final class RepairSettings {
    public static final int DEFAULT_CYCLE_SECONDS = 20;
    public static final int DEFAULT_PLAYERS_PER_TICK = 1;

    private final List<String> bypassPermissions;
    private final boolean whitelist;
    private final Set<String> worlds;
    private final boolean scanEnderChest;
    private final boolean skipArmorWithoutEquipable;
    private final int cycleSeconds;
    private final int playersPerTick;

    public RepairSettings(
            List<String> bypassPermissions,
            boolean whitelist,
            Set<String> worlds,
            boolean scanEnderChest,
            boolean skipArmorWithoutEquipable,
            int cycleSeconds,
            int playersPerTick) {
        this.bypassPermissions = List.copyOf(bypassPermissions);
        this.whitelist = whitelist;
        Set<String> folded = new HashSet<>();
        for (String world : worlds) {
            folded.add(world.toLowerCase(Locale.ROOT));
        }
        this.worlds = Set.copyOf(folded);
        this.scanEnderChest = scanEnderChest;
        this.skipArmorWithoutEquipable = skipArmorWithoutEquipable;
        this.cycleSeconds = cycleSeconds < 1 ? DEFAULT_CYCLE_SECONDS : cycleSeconds;
        this.playersPerTick = playersPerTick < 1 ? DEFAULT_PLAYERS_PER_TICK : playersPerTick;
    }

    public static RepairSettings defaults() {
        return new RepairSettings(List.of("itemfixer.bypass"), false, Set.of(), false, true, DEFAULT_CYCLE_SECONDS, DEFAULT_PLAYERS_PER_TICK);
    }

    public boolean bypasses(Predicate<String> hasPermission) {
        for (String permission : bypassPermissions) {
            if (hasPermission.test(permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean repairsWorld(String worldName) {
        boolean listed = worlds.contains(worldName.toLowerCase(Locale.ROOT));
        return whitelist ? listed : !listed;
    }

    /** An offline file with no world id is repaired on a blacklist and skipped on a whitelist. */
    public boolean repairsUnknownWorld() {
        return !whitelist;
    }

    /** Armor slots are visited when the optimisation is off, or when some old item is wearable armor. */
    public boolean scansArmorSlots(boolean anyOldItemIsArmor) {
        return !skipArmorWithoutEquipable || anyOldItemIsArmor;
    }

    public boolean scanEnderChest() {
        return scanEnderChest;
    }

    public int cycleSeconds() {
        return cycleSeconds;
    }

    public int playersPerTick() {
        return playersPerTick;
    }

    public static void main(String[] args) {
        RepairSettings open = RepairSettings.defaults();
        assert open.repairsWorld("lobby");
        assert open.repairsUnknownWorld();
        assert !new RepairSettings(List.of(), true, Set.of("arena"), false, true, 20, 1).repairsUnknownWorld();
        assert open.bypasses(permission -> permission.equals("itemfixer.bypass"));
        assert !open.bypasses(permission -> false);
        assert open.scansArmorSlots(true);
        assert !open.scansArmorSlots(false);
        assert !open.scanEnderChest();
        assert open.cycleSeconds() == DEFAULT_CYCLE_SECONDS;
        assert open.playersPerTick() == DEFAULT_PLAYERS_PER_TICK;

        RepairSettings forced = new RepairSettings(List.of(), false, Set.of(), false, false, 0, 0);
        assert forced.scansArmorSlots(false);
        assert forced.cycleSeconds() == DEFAULT_CYCLE_SECONDS;
        assert forced.playersPerTick() == DEFAULT_PLAYERS_PER_TICK;
        assert !forced.bypasses(permission -> true);

        RepairSettings blacklist = new RepairSettings(List.of(), false, Set.of("lobby"), false, true, 20, 1);
        assert !blacklist.repairsWorld("Lobby");
        assert blacklist.repairsWorld("world");

        RepairSettings whitelist = new RepairSettings(List.of(), true, Set.of("arena"), false, true, 20, 1);
        assert whitelist.repairsWorld("Arena");
        assert !whitelist.repairsWorld("lobby");
        assert !new RepairSettings(List.of(), true, Set.of(), false, true, 20, 1).repairsWorld("world");
    }
}
