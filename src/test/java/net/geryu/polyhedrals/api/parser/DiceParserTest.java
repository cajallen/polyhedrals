package net.geryu.polyhedrals.api.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiceParserTest {
    private static RollContext landed(ASTNode root, IntUnaryOperator rerolls, int... faces) {
        List<PhysicalDice> dice = new ArrayList<>();
        root.collectPhysicalDice(new RollContext(sides -> 1), dice);
        return RollContext.landed(dice, faces, rerolls);
    }

    private static IntUnaryOperator drawing(int... values) {
        int[] next = {0};
        return sides -> values[next[0]++];
    }

    private static int[] faces(String faces) {
        return faces.isBlank() ? new int[0] : Arrays.stream(faces.split(" ")).mapToInt(Integer::parseInt).toArray();
    }

    @ParameterizedTest(name = "{0} landing [{1}] totals {2}")
    @CsvSource({
            "2+3*4, '', 14",
            "(2+3)*4, '', 20",
            "-5+10, '', 5",
            "(2*3)d6+5, 1 2 3 4 5 6, 26",
            "1d20+2d6, 15 3 4, 22",
            "4d6h3, 2 6 1 5, 13",
            "2d20l1, 18 4, 4",
            "3d6r=1, 1 4 1, 10",
            "3d6r<3, 1 2 5, 11",
            "2d6r>4, 6 3, 6",
            "7/0, '', 0"})
    void physicalRolls(String expression, String faces, int total) {
        ASTNode root = DiceParser.parse(expression);
        assertEquals(total, root.evaluate(landed(root, sides -> 3, faces(faces))));
    }

    @ParameterizedTest(name = "{0} landing [{1}] is shown as {2}")
    @CsvSource({
            "(1d6+2)*1d4, 1 3, (1 + 2) * 3",
            "2*3+4, '', 2 * 3 + 4",
            "2-(3-1), '', 2 - (3 - 1)",
            "8/(2*2), '', 8 / (2 * 2)",
            "-5+10, '', -5 + 10",
            "2*-3, '', 2 * (-3)"})
    void workingKeepsItsGrouping(String expression, String faces, String shown) {
        ASTNode root = DiceParser.parse(expression);
        assertEquals(shown, root.toString(landed(root, sides -> 3, faces(faces))));
    }

    @Test
    void diceThatNeverLandedAreRolledWithoutThrowing() {
        ASTNode root = DiceParser.parse("1d20+2d6");
        List<PhysicalDice> dice = new ArrayList<>();
        root.collectPhysicalDice(new RollContext(sides -> 1), dice);
        RollContext ctx = RollContext.physical(sides -> sides / 2);
        ctx.addPhysicalResult(2, 5);

        assertEquals(List.of(10, 3), ctx.rollMissing(dice));
        assertEquals(18, root.evaluate(ctx));
    }

    @Test
    void logicalRollsDrawEachDieFromTheProvider() {
        assertEquals(13, DiceParser.parse("4d6h3").evaluate(new RollContext(drawing(2, 6, 1, 5))));
    }

    @Test
    void countAndSidesAreEvaluatedBeforeThrowing() {
        List<PhysicalDice> dice = new ArrayList<>();
        DiceParser.parse("(2*3)d(4+2)+5").collectPhysicalDice(new RollContext(sides -> 1), dice);
        assertEquals(List.of(new PhysicalDice(6, 6, 1)), dice);
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @ValueSource(strings = {"", "1d20x", "(1d6", "d", "2+", "1d6h", "1d(1d6)", "(1d6)d6", "(2+1d4)d(6+1d2)"})
    void malformedExpressionsAreRejected(String expression) {
        assertThrows(IllegalArgumentException.class, () -> DiceParser.parse(expression));
    }

    @Test
    void diceBesideAParenthesisedRollStillParse() {
        assertEquals(9, DiceParser.parse("(1d6+2)*1d4").evaluate(new RollContext(drawing(1, 3))));
    }

    @Test
    void aLogicalRollRefusesTooManyDice() {
        ASTNode root = DiceParser.parse("(1000*1000)d6");
        assertThrows(IllegalArgumentException.class, () -> root.evaluate(new RollContext(sides -> 1)));
    }

    @Test
    void aLandedRollDrawsItsRerollsOnce() {
        ASTNode root = DiceParser.parse("3d6r=1");
        int[] draws = {0};
        RollContext ctx = landed(root, sides -> ++draws[0] + 1, 1, 4, 1);

        assertEquals(9, root.evaluate(ctx));
        assertEquals("9", root.toString(ctx));
        assertEquals(2, draws[0]);
    }
}
