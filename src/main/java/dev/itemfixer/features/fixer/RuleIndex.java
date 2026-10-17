package dev.itemfixer.features.fixer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;


public final class RuleIndex {
    private final Map<String, int[]> byMaterial;

    public RuleIndex(List<String> materials) {
        Map<String, List<Integer>> grouped = new HashMap<>();
        for (int index = 0; index < materials.size(); index++) {
            grouped.computeIfAbsent(materials.get(index), key -> new ArrayList<>()).add(index);
        }
        Map<String, int[]> frozen = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : grouped.entrySet()) {
            int[] indexes = new int[entry.getValue().size()];
            for (int i = 0; i < indexes.length; i++) {
                indexes[i] = entry.getValue().get(i);
            }
            frozen.put(entry.getKey(), indexes);
        }
        this.byMaterial = Map.copyOf(frozen);
    }

    public int[] candidates(String material) {
        int[] found = byMaterial.get(material);
        return found == null ? new int[0] : found.clone();
    }

    public static int firstMatch(int[] candidates, IntPredicate matches) {
        for (int candidate : candidates) {
            if (matches.test(candidate)) {
                return candidate;
            }
        }
        return -1;
    }

    public static int clampedAmount(int oldAmount, int maxStack) {
        if (oldAmount < 1 || maxStack < 1) {
            return 0;
        }
        return Math.min(oldAmount, maxStack);
    }

    public static int leftover(int oldAmount, int maxStack) {
        return Math.max(0, oldAmount - clampedAmount(oldAmount, maxStack));
    }

    public static void main(String[] args) {
        RuleIndex index = new RuleIndex(List.of("DIAMOND_SWORD", "BOW", "DIAMOND_SWORD"));
        int[] swords = index.candidates("DIAMOND_SWORD");
        assert swords.length == 2 : swords.length;
        assert swords[0] == 0;
        assert swords[1] == 2;
        assert index.candidates("STONE").length == 0;
        int[] bows = index.candidates("BOW");
        assert bows.length == 1 && bows[0] == 1;
        assert firstMatch(swords, candidate -> true) == 0;
        assert firstMatch(swords, candidate -> candidate == 2) == 2;
        assert firstMatch(swords, candidate -> false) == -1;
        assert clampedAmount(64, 16) == 16;
        assert clampedAmount(5, 64) == 5;
        assert clampedAmount(0, 64) == 0;
        assert clampedAmount(5, 0) == 0;
        assert leftover(64, 16) == 48;
        assert leftover(5, 64) == 0;
        assert leftover(5, 0) == 5;
        assert leftover(-1, 16) == 0;
    }
}
