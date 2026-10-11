package net.geryu.polyhedrals.api.parser;

import java.util.List;

public interface ASTNode {
    int evaluate(RollContext ctx);

    String toString(RollContext ctx);

    void collectPhysicalDice(RollContext ctx, List<PhysicalDice> diceToRoll);
}
