package net.geryu.polyhedrals.physics;

public final class PolyhedronMass {
    public record Result(double volume, double[] centroid, double[] inertia) {}

    private PolyhedronMass() {}

    public static Result compute(ConvexHull.Result hull) {
        double volume = 0;
        double[] centroidAccum = new double[3];
        double[] covariance = new double[9];

        int cursor = 0;
        while (cursor < hull.polygons().length) {
            int count = hull.polygons()[cursor++];
            int first = hull.polygons()[cursor];
            for (int i = 1; i < count - 1; i++) {
                double[] a = point(hull, first);
                double[] b = point(hull, hull.polygons()[cursor + i]);
                double[] c = point(hull, hull.polygons()[cursor + i + 1]);

                double det = determinant(a, b, c);
                double tetraVolume = det / 6.0;
                volume += tetraVolume;

                for (int k = 0; k < 3; k++) {
                    centroidAccum[k] += tetraVolume * (a[k] + b[k] + c[k]) / 4.0;
                }
                accumulateCovariance(covariance, a, b, c, det);
            }
            cursor += count;
        }

        if (Math.abs(volume) < 1e-12) {
            throw new IllegalArgumentException("Polyhedron encloses no volume");
        }

        double[] centroid = new double[] {
                centroidAccum[0] / volume,
                centroidAccum[1] / volume,
                centroidAccum[2] / volume
        };

        for (int i = 0; i < 9; i++) {
            covariance[i] /= volume;
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                covariance[r * 3 + c] -= centroid[r] * centroid[c];
            }
        }

        double trace = covariance[0] + covariance[4] + covariance[8];
        double[] inertia = new double[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                inertia[r * 3 + c] = (r == c ? trace : 0) - covariance[r * 3 + c];
            }
        }

        return new Result(Math.abs(volume), centroid, inertia);
    }

    private static void accumulateCovariance(double[] covariance, double[] a, double[] b, double[] c, double det) {
        double scale = det / 120.0;
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 3; col++) {
                double sum = 2 * (a[r] * a[col] + b[r] * b[col] + c[r] * c[col])
                        + (a[r] * b[col] + b[r] * a[col])
                        + (a[r] * c[col] + c[r] * a[col])
                        + (b[r] * c[col] + c[r] * b[col]);
                covariance[r * 3 + col] += scale * sum;
            }
        }
    }

    private static double[] point(ConvexHull.Result hull, int index) {
        return new double[] {
                hull.points()[index * 3],
                hull.points()[index * 3 + 1],
                hull.points()[index * 3 + 2]
        };
    }

    private static double determinant(double[] a, double[] b, double[] c) {
        return a[0] * (b[1] * c[2] - b[2] * c[1])
                - a[1] * (b[0] * c[2] - b[2] * c[0])
                + a[2] * (b[0] * c[1] - b[1] * c[0]);
    }
}
