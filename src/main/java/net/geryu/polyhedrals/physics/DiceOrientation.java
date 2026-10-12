package net.geryu.polyhedrals.physics;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.DoubleSupplier;

public final class DiceOrientation {
    private static final Vector3f DOWN = new Vector3f(0, -1, 0);
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    private DiceOrientation() {}

    public static Quaternionf random(DoubleSupplier gaussian) {
        return new Quaternionf((float) gaussian.getAsDouble(), (float) gaussian.getAsDouble(),
                (float) gaussian.getAsDouble(), (float) gaussian.getAsDouble()).normalize();
    }

    public static Quaternionf restingRotation(Quaternionf current, DiceShapes.Shape shape) {
        Vector3f downmost = worldNormal(current, shape, faceMostAlong(current, shape, DOWN));
        // We must use downward face because d4s
        return new Quaternionf().rotationTo(downmost, DOWN).mul(current);
    }

    public static Quaternionf showing(DiceShapes.Shape shape, int face, float heading) {
        Quaternionf levelled = new Quaternionf().rotationTo(shape.faceNormal(face).get(new Vector3f()), readDirection(shape));
        return new Quaternionf().rotationY(heading).mul(levelled);
    }

    public static double restingHeight(Quaternionf orientation, DiceShapes.Shape shape) {
        double[] points = shape.hull().points();
        double lowest = 0;
        for (int i = 0; i < shape.hull().pointCount(); i++) {
            Vector3f world = orientation.transform(
                    new Vector3f((float) points[i * 3], (float) points[i * 3 + 1], (float) points[i * 3 + 2]));
            lowest = Math.min(lowest, world.y);
        }
        return -lowest;
    }

    public static int shownValue(Quaternionf orientation, DiceShapes.Shape shape, double toleranceDegrees) {
        Vector3f read = readDirection(shape);
        int face = faceMostAlong(orientation, shape, read);
        if (worldNormal(orientation, shape, face).dot(read) < Math.cos(Math.toRadians(toleranceDegrees))) {
            return -1;
        }
        return DiceFaceNumbering.valuesFor(shape)[face];
    }

    private static Vector3f readDirection(DiceShapes.Shape shape) {
        return DiceFaceNumbering.hasOppositeFaces(shape) ? UP : DOWN;
    }

    private static int faceMostAlong(Quaternionf orientation, DiceShapes.Shape shape, Vector3f direction) {
        int best = 0;
        float bestDot = -Float.MAX_VALUE;
        for (int face = 0; face < shape.faceCount(); face++) {
            float dot = worldNormal(orientation, shape, face).dot(direction);
            if (dot > bestDot) {
                bestDot = dot;
                best = face;
            }
        }
        return best;
    }

    private static Vector3f worldNormal(Quaternionf orientation, DiceShapes.Shape shape, int face) {
        return orientation.transform(shape.faceNormal(face).get(new Vector3f()));
    }
}
