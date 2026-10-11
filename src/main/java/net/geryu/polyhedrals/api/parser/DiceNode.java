package net.geryu.polyhedrals.api.parser;

import net.geryu.polyhedrals.api.RollBuilder;

import java.util.ArrayList;
import java.util.List;

public record DiceNode(int id, ASTNode countNode, ASTNode sidesNode, List<DiceModifier> modifiers) implements ASTNode {
    @Override
    public int evaluate(RollContext ctx) {
        return ctx.termTotal(id, () -> total(ctx));
    }

    private int total(RollContext ctx) {
        int sides = sidesNode.evaluate(ctx);
        List<Integer> rolls = new ArrayList<>(ctx.getPhysicalResults(id));
        if (!ctx.isPhysical()) {
            int count = countNode.evaluate(ctx);
            if (count > RollBuilder.MAX_DICE) {
                throw new IllegalArgumentException("Evaluated count (" + count + ") exceeds " + RollBuilder.MAX_DICE);
            }
            for (int i = 0; i < count; i++) {
                rolls.add(ctx.roll(sides));
            }
        }
        for (DiceModifier mod : modifiers) {
            mod.apply(rolls, sides, ctx);
        }
        return rolls.stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public String toString(RollContext ctx) {
        return String.valueOf(evaluate(ctx));
    }

    @Override
    public void collectPhysicalDice(RollContext ctx, List<PhysicalDice> diceToRoll) {
        int count = countNode.evaluate(ctx);
        if (count > 0) {
            diceToRoll.add(new PhysicalDice(count, sidesNode.evaluate(ctx), id));
        }
    }
}
