package net.geryu.polyhedrals.api.parser;

import java.util.List;

public record NumberNode(int value) implements ASTNode {
    @Override
    public int evaluate(RollContext ctx) {
        return value;
    }

    @Override
    public String toString(RollContext ctx) {
        return String.valueOf(value);
    }

    @Override
    public void collectPhysicalDice(RollContext ctx, List<PhysicalDice> diceToRoll) {}
}
