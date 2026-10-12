package net.geryu.polyhedrals.physics;

import org.joml.Vector3d;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DiceShapes {
    public static final double WORLD_SCALE = 0.25;

    public static final String DECAL_MARKER = "# --- number decals";

    public record Shape(
            int sides,
            ConvexHull.Result hull,
            double[] inertia,
            double boundingRadius,
            Vector3d modelCentroid) {
        public Vector3d faceNormal(int index) {
            double[] planes = hull.planes();
            return new Vector3d(planes[index * 4], planes[index * 4 + 1], planes[index * 4 + 2]);
        }

        public int faceCount() {
            return hull.planeCount();
        }

        // Cancels the item renderer's half-block shift and puts the centroid, which the body rotates about, at the origin.
        public Vector3d renderTranslation() {
            return new Vector3d(0.5).sub(modelCentroid);
        }
    }

    private static final Map<Integer, Shape> CACHE = new ConcurrentHashMap<>();

    private DiceShapes() {}

    public static Shape forSides(int sides) {
        return CACHE.computeIfAbsent(sides, DiceShapes::load);
    }

    static String modelNameFor(int sides) {
        return switch (sides) {
            case 4, 6, 8, 10, 12, 16, 20 -> "d" + sides;
            case 100 -> "d10";
            default -> "d20";
        };
    }

    private static Shape load(int sides) {
        String model = modelNameFor(sides);
        List<double[]> modelPoints = readObjVertices("/assets/polyhedrals/models/item/" + model + ".obj");

        ConvexHull.Result modelHull = ConvexHull.compute(modelPoints);
        double[] centroid = PolyhedronMass.compute(modelHull).centroid();

        List<double[]> worldPoints = new ArrayList<>(modelPoints.size());
        for (double[] p : modelPoints) {
            worldPoints.add(new double[] {
                    (p[0] - centroid[0]) * WORLD_SCALE,
                    (p[1] - centroid[1]) * WORLD_SCALE,
                    (p[2] - centroid[2]) * WORLD_SCALE
            });
        }

        ConvexHull.Result hull = ConvexHull.compute(worldPoints);
        PolyhedronMass.Result mass = PolyhedronMass.compute(hull);

        double boundingRadius = 0;
        for (int i = 0; i < hull.pointCount(); i++) {
            double x = hull.points()[i * 3];
            double y = hull.points()[i * 3 + 1];
            double z = hull.points()[i * 3 + 2];
            boundingRadius = Math.max(boundingRadius, Math.sqrt(x * x + y * y + z * z));
        }

        return new Shape(
                sides,
                hull,
                mass.inertia(),
                boundingRadius,
                new Vector3d(centroid));
    }

    static List<double[]> readObjVertices(String resourcePath) {
        try (InputStream in = DiceShapes.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing dice model resource: " + resourcePath);
            }
            List<double[]> points = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(DECAL_MARKER)) break;
                if (!line.startsWith("v ")) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) continue;
                points.add(new double[] {
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3])
                });
            }
            return points;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read dice model " + resourcePath, e);
        }
    }
}
