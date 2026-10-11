package net.geryu.polyhedrals.physics;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiceFaceTest {
    @ParameterizedTest(name = "a d{0} carries each number once, opposite faces summing to one more than the highest")
    @ValueSource(ints = {4, 6, 8, 10, 12, 20})
    void numbering(int sides) {
        DiceShapes.Shape shape = DiceShapes.forSides(sides);
        int[] values = DiceFaceNumbering.valuesFor(shape);

        assertArrayEquals(IntStream.rangeClosed(1, sides).toArray(), Arrays.stream(values).sorted().toArray());
        for (int face = 0; face < values.length; face++) {
            for (int other = 0; other < values.length; other++) {
                if (shape.faceNormal(face).dot(shape.faceNormal(other)) < -0.999) {
                    assertEquals(sides + 1, values[face] + values[other], "faces " + face + " and " + other);
                }
            }
        }
    }

    @ParameterizedTest(name = "a d{0} levelled from any orientation reads a face, and every face is reachable")
    @ValueSource(ints = {4, 6, 8, 10, 12, 20, 100})
    void levelledDiceReadEveryFace(int sides) {
        DiceShapes.Shape shape = DiceShapes.forSides(sides);
        Random rng = new Random(4242);
        Set<Integer> seen = new HashSet<>();

        for (int attempt = 0; attempt < 4000; attempt++) {
            Quaternionf tumbled = new Quaternionf(
                    rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian()).normalize();
            int shown = DiceOrientation.shownValue(DiceOrientation.restingRotation(tumbled, shape), shape, 2.0);
            assertTrue(shown >= 0, "levelled onto an edge from " + tumbled);
            seen.add(shown);
        }
        assertEquals(shape.faceCount(), seen.size(), "only ever showed " + seen);
    }

    @ParameterizedTest(name = "a d{0} placed on any roll reads that roll, resting above the ground")
    @ValueSource(ints = {4, 6, 8, 10, 12, 20, 100})
    void placedRollReadsBack(int sides) {
        DiceShapes.Shape shape = DiceShapes.forSides(sides);
        for (int roll = 1; roll <= sides; roll++) {
            Quaternionf pose = DiceOrientation.showing(shape, DiceFaceNumbering.faceShowing(shape, roll), roll * 0.7f);
            int printed = sides == 100 ? DiceFaceNumbering.percentileTens(roll) : roll;
            assertEquals(printed, DiceOrientation.shownValue(pose, shape, 2.0), "placed on " + roll);
            assertTrue(DiceOrientation.restingHeight(pose, shape) > 0, "placed on " + roll);
        }
    }

    @Test
    void everyPercentileRollSplitsIntoATensFaceAndAUnitsDie() {
        Set<Integer> tensFaces = new HashSet<>();
        for (int value : DiceFaceNumbering.valuesFor(DiceShapes.forSides(100))) {
            tensFaces.add(value);
        }

        for (int roll = 1; roll <= 100; roll++) {
            int tens = DiceFaceNumbering.percentileTens(roll);
            int units = roll - tens;
            assertTrue(tensFaces.contains(tens), roll + " needs a " + tens + " face");
            assertTrue(units >= 1 && units <= 10, roll + " leaves " + units + " for the units die");
        }
    }
}
