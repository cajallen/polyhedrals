package net.geryu.polyhedrals.api.parser;

import java.util.List;

public record KeepModifier(boolean highest, int count) implements DiceModifier {
    @Override
    public void apply(List<Integer> rolls, int sides, RollContext ctx) {
        if (rolls.size() <= count)
            return;
        List<Integer> sorted = rolls.stream().sorted().toList();
        rolls.clear();
        rolls.addAll(highest ? sorted.subList(sorted.size() - count, sorted.size()) : sorted.subList(0, count));
    }
}
