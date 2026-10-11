package net.geryu.polyhedrals.physics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiceGeometryTest {
    private static List<double[]> cube(double half, double x, double y, double z) {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            points.add(new double[] {
                    x + ((i & 1) == 0 ? -half : half),
                    y + ((i & 2) == 0 ? -half : half),
                    z + ((i & 4) == 0 ? -half : half)
            });
        }
        return points;
    }

    @Test
    void cubeHullHasSixQuadFaces() {
        ConvexHull.Result hull = ConvexHull.compute(cube(0.5, 0, 0, 0));

        assertEquals(6, hull.planeCount(), "a cube has six faces once coplanar triangles are merged");
        assertEquals(8, hull.pointCount());

        int cursor = 0;
        for (int f = 0; f < hull.planeCount(); f++) {
            assertEquals(4, hull.polygons()[cursor], "each merged cube face should be a quad");
            cursor += hull.polygons()[cursor] + 1;
        }
    }

    @Test
    void cubeMassPropertiesMatchTheClosedForm() {
        double side = 0.8;
        PolyhedronMass.Result mass = PolyhedronMass.compute(ConvexHull.compute(cube(side / 2, 0, 0, 0)));

        double moment = side * side / 6.0;
        assertEquals(side * side * side, mass.volume(), 1e-9);
        assertArrayEquals(new double[3], mass.centroid(), 1e-9);
        assertArrayEquals(new double[] {moment, 0, 0, 0, moment, 0, 0, 0, moment}, mass.inertia(), 1e-9);
    }

    @Test
    void offCentreSolidReportsItsTrueCentroid() {
        PolyhedronMass.Result mass = PolyhedronMass.compute(ConvexHull.compute(cube(0.5, 3, -2, 0.5)));

        assertArrayEquals(new double[] {3, -2, 0.5}, mass.centroid(), 1e-9);
    }

    @ParameterizedTest(name = "the d{0} model hulls to {1} faces")
    @CsvSource({"4,4", "6,6", "8,8", "10,10", "12,12", "20,20", "100,10"})
    void diceModelsHullToTheExpectedFaceCount(int sides, int expectedFaces) {
        assertEquals(expectedFaces, DiceShapes.forSides(sides).faceCount());
    }

    @ParameterizedTest(name = "the d{0} shape is centred and sanely sized")
    @ValueSource(ints = {4, 6, 8, 10, 12, 20, 100})
    void diceShapesAreCentredAtTheCentreOfVolume(int sides) {
        DiceShapes.Shape shape = DiceShapes.forSides(sides);

        assertArrayEquals(new double[3], PolyhedronMass.compute(shape.hull()).centroid(), 1e-9);
        assertTrue(shape.boundingRadius() > 0.02 && shape.boundingRadius() < 0.5,
                "bounding radius out of range: " + shape.boundingRadius());
    }
}
