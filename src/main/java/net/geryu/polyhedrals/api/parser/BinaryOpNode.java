package net.geryu.polyhedrals.api.parser;

import java.util.List;

public record BinaryOpNode(ASTNode left, char operator, ASTNode right) implements ASTNode {
    @Override
    public int evaluate(RollContext ctx) {
        int l = left.evaluate(ctx);
        int r = right.evaluate(ctx);
        return switch (operator) {
            case '+' -> l + r;
            case '-' -> l - r;
            case '*' -> l * r;
            case '/' -> r != 0 ? l / r : 0; // division always rounds down, div 0 = 0. TODO?
            default -> throw new IllegalStateException("Unknown operator " + operator);
        };
    }

    @Override
    public String toString(RollContext ctx) {
        return left.toString(ctx) + " " + operator + " " + right.toString(ctx);
    }

    @Override
    public void collectPhysicalDice(RollContext ctx, List<PhysicalDice> diceToRoll) {
        left.collectPhysicalDice(ctx, diceToRoll);
        right.collectPhysicalDice(ctx, diceToRoll);
    }
}
