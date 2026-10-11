package net.geryu.polyhedrals.physics;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class DiceFaceNumbering {
    private static final double ANTIPODAL_COSINE = -Math.cos(Math.toRadians(5));

    private DiceFaceNumbering() {}

    public static int[] valuesFor(DiceShapes.Shape shape) {
        int faceCount = shape.faceCount();
        int[] values = new int[faceCount];
        Arrays.fill(values, -1);

        List<Integer> order = new ArrayList<>();
        for (int face = 0; face < faceCount; face++) {
            order.add(face);
        }
        order.sort(Comparator
                .comparingDouble((Integer f) -> -round(shape.faceNormal(f).y))
                .thenComparingDouble(f -> round(shape.faceNormal(f).x))
                .thenComparingDouble(f -> round(shape.faceNormal(f).z)));

        // properly orient faces for opposite n+1 rule
        int next = 1;
        for (int face : order) {
            if (values[face] >= 0) continue;
            int opposite = antipodeOf(shape, face);
            values[face] = next;
            if (opposite >= 0 && values[opposite] < 0) {
                values[opposite] = faceCount + 1 - next;
            }
            next++;
            while (next <= faceCount && isTaken(values, next)) {
                next++;
            }
        }

        if (isPercentile(shape)) {
            for (int face = 0; face < faceCount; face++) {
                values[face] = (values[face] - 1) * 10;
            }
        }
        return values;
    }

    private static boolean isPercentile(DiceShapes.Shape shape) {
        return shape.sides() == 100;
    }

    public static int percentileTens(int roll) {
        //00-90 
        return (roll - 1) / 10 * 10;
    }

    public static int faceShowing(DiceShapes.Shape shape, int roll) {
        int printed = isPercentile(shape) ? percentileTens(roll) : roll;
        int[] values = valuesFor(shape);
        for (int face = 0; face < values.length; face++) {
            if (values[face] == printed)
                return face;
        }
        return -1;
    }

    static boolean hasOppositeFaces(DiceShapes.Shape shape) {
        return antipodeOf(shape, 0) >= 0;
    }

    private static int antipodeOf(DiceShapes.Shape shape, int face) {
        Vector3d normal = shape.faceNormal(face);
        for (int other = 0; other < shape.faceCount(); other++) {
            if (other != face && normal.dot(shape.faceNormal(other)) < ANTIPODAL_COSINE) {
                return other;
            }
        }
        return -1;
    }

    private static boolean isTaken(int[] values, int value) {
        for (int assigned : values) {
            if (assigned == value)
                return true;
        }
        return false;
    }

    private static double round(double component) {
        return Math.round(component * 1.0e6) / 1.0e6;
    }
}
