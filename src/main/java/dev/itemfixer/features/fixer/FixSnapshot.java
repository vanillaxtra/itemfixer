package dev.itemfixer.features.fixer;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.EquipmentSlot;

public final class FixSnapshot {
    private final List<FixRule> rules;
    private final RuleIndex index;
    private final boolean anyArmor;

    private FixSnapshot(List<FixRule> rules, RuleIndex index, boolean anyArmor) {
        this.rules = rules;
        this.index = index;
        this.anyArmor = anyArmor;
    }

    public static FixSnapshot empty() {
        return of(List.of());
    }

    public static FixSnapshot of(List<FixRule> rules) {
        List<FixRule> copy = List.copyOf(rules);
        List<String> materials = new ArrayList<>(copy.size());
        boolean anyArmor = false;
        for (FixRule rule : copy) {
            materials.add(rule.oldTemplate().getType().name());
            EquipmentSlot slot = rule.oldTemplate().getType().getEquipmentSlot();
            if (slot != null && slot.isArmor()) {
                anyArmor = true;
            }
        }
        return new FixSnapshot(copy, new RuleIndex(materials), anyArmor);
    }

    public List<FixRule> rules() {
        return rules;
    }

    public RuleIndex index() {
        return index;
    }

    public boolean anyArmor() {
        return anyArmor;
    }
}
