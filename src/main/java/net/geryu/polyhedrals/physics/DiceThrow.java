package net.geryu.polyhedrals.physics;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class DiceThrow {
    private DiceThrow() {}

    private static final double SPACING = 0.34;
    private static final double FAN = 0.35;
    private static final double JITTER = 0.04;

    private static final double HAND_HEIGHT = 0.9;
    private static final double HAND_REACH = 0.35;
    private static final double HAND_SIDE = 0.35;

    private static final double LOB_FORWARD = 0.25;
    private static final double LOB_LIFT = 0.4;
    private static final double AIM_INFLUENCE = 0.6;

    public static Vector3d handOffset(Vector3d forward, int side) {
        return new Vector3d(forward).mul(HAND_REACH)
                .add(-forward.z * side * HAND_SIDE, HAND_HEIGHT, forward.x * side * HAND_SIDE);
    }

    public static Vector3d lob(Vector3d forward, double lookUp) {
        double aim = 1 + AIM_INFLUENCE * lookUp;
        return new Vector3d(forward).mul(LOB_FORWARD * aim).add(0, LOB_LIFT * aim, 0);
    }

    public static void layout(int count, Vector3d origin, Vector3d velocity, Random rng,
                              List<Vector3d> positions, List<Vector3d> velocities) {
        int reach = (int) Math.ceil(Math.cbrt(count)) + 1;
        List<Vector3d> lattice = new ArrayList<>();
        for (int x = -reach; x <= reach; x++)
            for (int y = -reach; y <= reach; y++)
                for (int z = -reach; z <= reach; z++)
                    lattice.add(new Vector3d(x, y, z).mul(SPACING));
        Collections.shuffle(lattice, rng);
        lattice.sort(Comparator.comparingDouble(Vector3d::lengthSquared));

        List<Vector3d> offsets = new ArrayList<>(lattice.subList(0, count));
        Vector3d centre = new Vector3d();
        offsets.forEach(centre::add);
        centre.div(count);

        double speed = velocity.length();
        for (Vector3d offset : offsets) {
            offset.sub(centre);
            positions.add(new Vector3d(origin).add(offset));
            velocities.add(new Vector3d(velocity)
                    .fma(speed * FAN, offset)
                    .add(rng.nextGaussian() * speed * JITTER,
                            rng.nextGaussian() * speed * JITTER,
                            rng.nextGaussian() * speed * JITTER));
        }
    }
}
