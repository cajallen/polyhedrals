package net.geryu.polyhedrals.api.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

public class RollContext {
    private final IntUnaryOperator rollProvider;
    private final boolean physical;
    private final Map<Integer, List<Integer>> physicalResults = new HashMap<>();
    private final Map<Integer, Integer> termTotals = new HashMap<>();

    public RollContext(IntUnaryOperator rollProvider) {
        this(rollProvider, false);
    }

    private RollContext(IntUnaryOperator rollProvider, boolean physical) {
        this.rollProvider = rollProvider;
        this.physical = physical;
    }

    public static RollContext physical(IntUnaryOperator rerolls) {
        return new RollContext(rerolls, true);
    }

    public static RollContext landed(List<PhysicalDice> dice, int[] faces, IntUnaryOperator rerolls) {
        RollContext ctx = physical(rerolls);
        int die = 0;
        for (PhysicalDice term : dice) {
            for (int i = 0; i < term.count(); i++) {
                ctx.addPhysicalResult(term.nodeId(), faces[die++]);
            }
        }
        return ctx;
    }

    public boolean isPhysical() {
        return physical;
    }

    public int roll(int sides) {
        return rollProvider.applyAsInt(sides);
    }

    public void addPhysicalResult(int diceNodeId, int result) {
        physicalResults.computeIfAbsent(diceNodeId, k -> new ArrayList<>()).add(result);
        termTotals.remove(diceNodeId);
    }

    int termTotal(int diceNodeId, IntSupplier total) {
        if (!physical)
            return total.getAsInt();
        Integer known = termTotals.get(diceNodeId);
        if (known == null) {
            known = total.getAsInt();
            termTotals.put(diceNodeId, known);
        }
        return known;
    }

    public List<Integer> getPhysicalResults(int diceNodeId) {
        return physicalResults.getOrDefault(diceNodeId, List.of());
    }
}
