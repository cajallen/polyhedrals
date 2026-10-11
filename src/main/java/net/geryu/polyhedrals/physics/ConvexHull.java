package net.geryu.polyhedrals.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConvexHull {
    public record Result(double[] points, double[] planes, int[] polygons, int pointCount, int planeCount) {}

    private ConvexHull() {}

    public static Result compute(List<double[]> rawPoints) {
        return new Builder(rawPoints).build();
    }

    private static final class Builder {
        private final double weldEpsilon;
        private final double visibleEpsilon;
        private final double collinearEpsilon;
        // Face epsilon, allows slightly non-flat faces on die.
        private static final double COPLANAR_ANGLE_COSINE = Math.cos(Math.toRadians(12));

        private final List<double[]> pts;

        Builder(List<double[]> rawPoints) {
            double scale = characteristicLength(rawPoints);
            this.weldEpsilon = scale * 1.0e-6;
            this.visibleEpsilon = scale * 1.0e-4;
            this.collinearEpsilon = scale * 1.0e-3;
            this.pts = weld(rawPoints);
            if (pts.size() < 4) {
                throw new IllegalArgumentException("Too few points (" + pts.size() + ") to build hull");
            }
        }

        private static double characteristicLength(List<double[]> points) {
            if (points.isEmpty())
                return 1;
            double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (double[] p : points) {
                for (int axis = 0; axis < 3; axis++) {
                    min[axis] = Math.min(min[axis], p[axis]);
                    max[axis] = Math.max(max[axis], p[axis]);
                }
            }
            double longest = 0;
            for (int axis = 0; axis < 3; axis++) {
                longest = Math.max(longest, max[axis] - min[axis]);
            }
            return longest > 0 ? longest : 1;
        }

        Result build() {
            List<int[]> triangles = buildTriangleHull();
            List<int[]> hullFaces = mergeCoplanar(triangles);

            int[] remap = new int[pts.size()];
            java.util.Arrays.fill(remap, -1);
            List<double[]> keptPoints = new ArrayList<>();
            for (int[] face : hullFaces) {
                for (int idx : face) {
                    if (remap[idx] < 0) {
                        remap[idx] = keptPoints.size();
                        keptPoints.add(pts.get(idx));
                    }
                }
            }

            double[] points = new double[keptPoints.size() * 3];
            for (int i = 0; i < keptPoints.size(); i++) {
                double[] p = keptPoints.get(i);
                points[i * 3] = p[0];
                points[i * 3 + 1] = p[1];
                points[i * 3 + 2] = p[2];
            }

            double[] planes = new double[hullFaces.size() * 4];
            int polygonLength = 0;
            for (int[] face : hullFaces) {
                polygonLength += face.length + 1;
            }
            int[] polygons = new int[polygonLength];

            int planeCursor = 0;
            int polyCursor = 0;
            for (int[] face : hullFaces) {
                double[] normal = faceNormal(face);
                double[] centre = new double[3];
                for (int idx : face) {
                    double[] v = pts.get(idx);
                    centre[0] += v[0] / face.length;
                    centre[1] += v[1] / face.length;
                    centre[2] += v[2] / face.length;
                }
                planes[planeCursor++] = normal[0];
                planes[planeCursor++] = normal[1];
                planes[planeCursor++] = normal[2];
                planes[planeCursor++] = dot(normal, centre);

                polygons[polyCursor++] = face.length;
                for (int idx : face) {
                    polygons[polyCursor++] = remap[idx];
                }
            }

            return new Result(points, planes, polygons, keptPoints.size(), hullFaces.size());
        }

        private List<double[]> weld(List<double[]> input) {
            List<double[]> out = new ArrayList<>();
            for (double[] p : input) {
                boolean duplicate = false;
                for (double[] q : out) {
                    if (Math.abs(p[0] - q[0]) < weldEpsilon
                            && Math.abs(p[1] - q[1]) < weldEpsilon
                            && Math.abs(p[2] - q[2]) < weldEpsilon) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    out.add(new double[] {p[0], p[1], p[2]});
                }
            }
            return out;
        }

        private List<int[]> buildTriangleHull() {
            int[] seed = findSeedTetrahedron();
            List<int[]> faces = new ArrayList<>();
            int a = seed[0], b = seed[1], c = seed[2], d = seed[3];
            faces.add(orient(new int[] {a, b, c}, pts.get(d)));
            faces.add(orient(new int[] {a, b, d}, pts.get(c)));
            faces.add(orient(new int[] {a, c, d}, pts.get(b)));
            faces.add(orient(new int[] {b, c, d}, pts.get(a)));

            for (int i = 0; i < pts.size(); i++) {
                if (i == a || i == b || i == c || i == d) continue;
                double[] p = pts.get(i);

                List<int[]> visible = new ArrayList<>();
                List<int[]> hidden = new ArrayList<>();
                for (int[] face : faces) {
                    if (signedDistance(face, p) > visibleEpsilon) {
                        visible.add(face);
                    } else {
                        hidden.add(face);
                    }
                }
                if (visible.isEmpty()) continue;

                Map<Long, int[]> horizon = new HashMap<>();
                for (int[] face : visible) {
                    for (int e = 0; e < 3; e++) {
                        int u = face[e];
                        int v = face[(e + 1) % 3];
                        if (horizon.remove(edgeKey(v, u)) == null) {
                            horizon.put(edgeKey(u, v), new int[] {u, v});
                        }
                    }
                }

                faces = hidden;
                for (int[] edge : horizon.values()) {
                    faces.add(new int[] {edge[0], edge[1], i});
                }
            }
            return faces;
        }

        private int[] findSeedTetrahedron() {
            int i0 = 0;
            for (int i = 1; i < pts.size(); i++) {
                if (pts.get(i)[0] < pts.get(i0)[0]) i0 = i;
            }

            int i1 = -1;
            double best = 0;
            for (int i = 0; i < pts.size(); i++) {
                double dist = distance(pts.get(i0), pts.get(i));
                if (dist > best) {
                    best = dist;
                    i1 = i;
                }
            }

            int i2 = -1;
            best = 0;
            for (int i = 0; i < pts.size(); i++) {
                double area = length(cross(sub(pts.get(i1), pts.get(i0)), sub(pts.get(i), pts.get(i0))));
                if (area > best) {
                    best = area;
                    i2 = i;
                }
            }
            if (i2 < 0) throw new IllegalArgumentException("All points are colinear");

            double[] normal = cross(sub(pts.get(i1), pts.get(i0)), sub(pts.get(i2), pts.get(i0)));
            int i3 = -1;
            best = 0;
            for (int i = 0; i < pts.size(); i++) {
                double volume = Math.abs(dot(normal, sub(pts.get(i), pts.get(i0))));
                if (volume > best) {
                    best = volume;
                    i3 = i;
                }
            }
            if (i3 < 0) throw new IllegalArgumentException("All points are coplanar");

            return new int[] {i0, i1, i2, i3};
        }

        private int[] orient(int[] tri, double[] interior) {
            return signedDistance(tri, interior) > 0 ? new int[] {tri[0], tri[2], tri[1]} : tri;
        }

        private List<int[]> mergeCoplanar(List<int[]> triangles) {
            List<double[]> groupNormals = new ArrayList<>();
            List<List<int[]>> groups = new ArrayList<>();

            for (int[] tri : triangles) {
                double[] normal = faceNormal(tri);

                int match = -1;
                for (int g = 0; g < groupNormals.size(); g++) {
                    if (dot(groupNormals.get(g), normal) > COPLANAR_ANGLE_COSINE) {
                        match = g;
                        break;
                    }
                }
                if (match < 0) {
                    groupNormals.add(normal);
                    groups.add(new ArrayList<>());
                    match = groups.size() - 1;
                }
                groups.get(match).add(tri);
            }

            List<int[]> faces = new ArrayList<>();
            for (List<int[]> group : groups) {
                faces.add(boundaryLoop(group));
            }
            return faces;
        }

        private int[] boundaryLoop(List<int[]> group) {
            Map<Long, int[]> edges = new HashMap<>();
            for (int[] tri : group) {
                for (int e = 0; e < 3; e++) {
                    int u = tri[e];
                    int v = tri[(e + 1) % 3];
                    if (edges.remove(edgeKey(v, u)) == null) {
                        edges.put(edgeKey(u, v), new int[] {u, v});
                    }
                }
            }

            Map<Integer, Integer> next = new HashMap<>();
            for (int[] edge : edges.values()) {
                next.put(edge[0], edge[1]);
            }

            int start = edges.values().iterator().next()[0];
            List<Integer> loop = new ArrayList<>();
            int current = start;
            do {
                loop.add(current);
                Integer step = next.get(current);
                if (step == null) {
                    throw new IllegalStateException("Coplanar face boundary is not a closed loop");
                }
                current = step;
            } while (current != start && loop.size() <= next.size());

            return dropCollinear(loop);
        }

        private int[] dropCollinear(List<Integer> loop) {
            List<Integer> kept = new ArrayList<>();
            int n = loop.size();
            for (int i = 0; i < n; i++) {
                double[] prev = pts.get(loop.get((i - 1 + n) % n));
                double[] cur = pts.get(loop.get(i));
                double[] nxt = pts.get(loop.get((i + 1) % n));
                double[] span = sub(nxt, prev);
                double spanLength = length(span);
                double deviation = spanLength > 0 ? length(cross(sub(cur, prev), span)) / spanLength : 0;
                if (deviation > collinearEpsilon) {
                    kept.add(loop.get(i));
                }
            }
            int[] out = new int[kept.size()];
            for (int i = 0; i < kept.size(); i++) out[i] = kept.get(i);
            return out;
        }

        private double[] faceNormal(int[] face) {
            double nx = 0, ny = 0, nz = 0;
            for (int i = 0; i < face.length; i++) {
                double[] cur = pts.get(face[i]);
                double[] nxt = pts.get(face[(i + 1) % face.length]);
                nx += (cur[1] - nxt[1]) * (cur[2] + nxt[2]);
                ny += (cur[2] - nxt[2]) * (cur[0] + nxt[0]);
                nz += (cur[0] - nxt[0]) * (cur[1] + nxt[1]);
            }
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-15) throw new IllegalStateException("Degenerate face");
            return new double[] {nx / len, ny / len, nz / len};
        }

        private double signedDistance(int[] face, double[] p) {
            return dot(faceNormal(face), sub(p, pts.get(face[0])));
        }
    }

    private static long edgeKey(int u, int v) {
        return ((long) u << 32) | (v & 0xFFFFFFFFL);
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double length(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    private static double distance(double[] a, double[] b) {
        return length(sub(a, b));
    }
}
