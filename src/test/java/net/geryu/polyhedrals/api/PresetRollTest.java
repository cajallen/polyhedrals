package net.geryu.polyhedrals.api;

import net.geryu.polyhedrals.api.parser.ASTNode;
import net.geryu.polyhedrals.api.parser.DiceParser;
import net.geryu.polyhedrals.api.parser.PhysicalDice;
import net.geryu.polyhedrals.api.parser.RollContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PresetRollTest {
    private record Parsed(ASTNode root, List<PhysicalDice> dice) {}

    private static Parsed parse(String expression) {
        ASTNode root = DiceParser.parse(expression);
        List<PhysicalDice> dice = new ArrayList<>();
        root.collectPhysicalDice(new RollContext(sides -> 1), dice);
        return new Parsed(root, dice);
    }

    private static int total(Parsed parsed, int[] faces) {
        return parsed.root().evaluate(RollContext.landed(parsed.dice(), faces, sides -> 1));
    }

    @Test
    void facesApplyInThrowOrderClampedAndTheRestDrawnFairly() {
        Parsed parsed = parse("1d20+2d6+3");
        int[] faces = new PresetRoll.Faces(List.of(25, 0)).facesFor(parsed.root(), parsed.dice(), sides -> 4);
        assertArrayEquals(new int[] {20, 1, 4}, faces);
    }

    @ParameterizedTest(name = "{0} forced to {1} reads {2}")
    @CsvSource({
            "1d20+5, 25, 25",
            "1d20+5, 12, 12",
            "3d6+2, 11, 11",
            "3d6+2, 30, 20",
            "3d6+2, 1, 5",
            "1d20-1d4, 10, 10",
            "1d20-1d4, -3, -3",
            "2d8+1d4+1, 17, 17"})
    void totalSolvesToTheNearestReachable(String expression, int target, int expected) {
        Parsed parsed = parse(expression);
        int[] faces = new PresetRoll.Total(target).facesFor(parsed.root(), parsed.dice(), sides -> 1);
        assertEquals(expected, total(parsed, faces));
    }
}
