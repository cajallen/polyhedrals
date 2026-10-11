package net.geryu.polyhedrals.api.parser;

import java.util.List;

public record RerollModifier(char operator, int target) implements DiceModifier {
    @Override
    public void apply(List<Integer> rolls, int sides, RollContext ctx) {
        rolls.replaceAll(roll -> switch (operator) {
            case '=' -> roll == target;
            case '<' -> roll < target;
            case '>' -> roll > target;
            default -> throw new IllegalStateException("Unknown operator " + operator);
        } ? ctx.roll(sides) : roll);
    }
}
