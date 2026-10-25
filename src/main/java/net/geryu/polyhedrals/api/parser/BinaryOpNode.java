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
        // unary minus is 0 - x, reformat this back...
        if (operator == '-' && left.equals(new NumberNode(0)))
            return "-" + operand(right, true, ctx);
        return operand(left, false, ctx) + " " + operator + " " + operand(right, true, ctx);
    }

    private String operand(ASTNode node, boolean right, RollContext ctx) {
        // Retain parenthesis for correctness
        String shown = node.toString(ctx);
        boolean grouped = node instanceof BinaryOpNode child
                && (precedence(child.operator) < precedence(operator)
                    || right && precedence(child.operator) == precedence(operator));
        return grouped ? "(" + shown + ")" : shown;
    }

    private static int precedence(char operator) {
        return operator == '*' || operator == '/' ? 1 : 0;
    }

    @Override
    public void collectPhysicalDice(RollContext ctx, List<PhysicalDice> diceToRoll) {
        left.collectPhysicalDice(ctx, diceToRoll);
        right.collectPhysicalDice(ctx, diceToRoll);
    }
}
