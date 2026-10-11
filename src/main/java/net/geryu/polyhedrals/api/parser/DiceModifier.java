package net.geryu.polyhedrals.api.parser;

import java.util.List;

public interface DiceModifier {
    void apply(List<Integer> rolls, int sides, RollContext ctx);
}
