package net.geryu.polyhedrals.physics;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DicePhysicsWorldTest {
    private static final double FLOOR_TOP = 1.0;
    private static final int TICKS_PER_SECOND = 20;

    private static final DicePhysicsWorld.TerrainSource FLOOR = floorOf(DicePhysicsWorld.Surface.DEFAULT);

    private static DicePhysicsWorld.TerrainSource floorOf(DicePhysicsWorld.Surface surface) {
        return (region, out) -> {
            if (region.minY() >= FLOOR_TOP || region.maxY() <= 0)
                return;
            for (int x = (int) Math.floor(region.minX()); x <= (int) Math.floor(region.maxX()); x++) {
                for (int z = (int) Math.floor(region.minZ()); z <= (int) Math.floor(region.maxZ()); z++) {
                    out.add(new DicePhysicsWorld.Box(x, 0, z, x + 1, FLOOR_TOP, z + 1, surface));
                }
            }
        };
    }

    private static void run(DicePhysicsWorld world, double seconds) {
        for (int tick = 0; tick < seconds * TICKS_PER_SECOND; tick++) {
            world.step(FLOOR);
        }
    }

    private static Vector3d spin(Random random) {
        return new Vector3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).mul(DiceTuning.THROW_SPIN);
    }

    @ParameterizedTest(name = "a d{0} thrown onto a floor comes to rest on it")
    @ValueSource(ints = {4, 6, 8, 10, 12, 16, 20, 100})
    void diceLandAndSettle(int sides) {
        DiceShapes.Shape shape = DiceShapes.forSides(sides);

        try (DicePhysicsWorld world = new DicePhysicsWorld()) {
            DicePhysicsWorld.Die die = world.addDie(shape,
                    new Vector3d(0.5, 3.0, 0.5), new Vector3d(1.5, 0, 0.4), new Vector3d(18, -25, 12));

            double lowest = Double.MAX_VALUE;
            int stoppedFor = 0;
            int ticks = 0;
            for (; ticks < 10 * TICKS_PER_SECOND && stoppedFor < 5; ticks++) {
                world.step(FLOOR);
                assertTrue(die.position().isFinite(), "position went non-finite on tick " + ticks);
                lowest = Math.min(lowest, die.position().y);
                stoppedFor = die.isAtRest() ? stoppedFor + 1 : 0;
            }

            assertTrue(stoppedFor >= 5, "never came to rest within 10 seconds");
            assertTrue(ticks < 6 * TICKS_PER_SECOND, "took " + ticks);

            double restY = die.position().y;
            assertTrue(restY > FLOOR_TOP, "ended below the floor at y=" + restY);
            assertTrue(restY < FLOOR_TOP + shape.boundingRadius() * 1.5, "ended hovering at y=" + restY);
            assertTrue(lowest > FLOOR_TOP - shape.boundingRadius(), "sank into the floor, reaching y=" + lowest);
        }
    }

    @Test
    void dFoursStrikingABlockCornerAtFullSpeedStayOnTheFloor() {
        DiceShapes.Shape shape = DiceShapes.forSides(4);
        Random random = new Random(4);

        for (int drop = 0; drop < 200; drop++) {
            try (DicePhysicsWorld world = new DicePhysicsWorld()) {
                DicePhysicsWorld.Die die = world.addDie(shape,
                        new Vector3d(1 + random.nextGaussian() * 0.1, 2.0, 1 + random.nextGaussian() * 0.1),
                        new Vector3d(0, -DiceTuning.MAX_SPEED, 0),
                        spin(random));

                for (int tick = 0; tick < TICKS_PER_SECOND; tick++) {
                    world.step(FLOOR);
                    assertTrue(die.position().y > FLOOR_TOP - shape.boundingRadius(),
                            "drop " + drop + " sank into the floor on tick " + tick + ", reaching y=" + die.position().y);
                }
            }
        }
    }

    @Test
    void landingIsReportedAsAnImpact() {
        try (DicePhysicsWorld world = new DicePhysicsWorld()) {
            DicePhysicsWorld.Die die = world.addDie(DiceShapes.forSides(6),
                    new Vector3d(0.5, 3.0, 0.5), new Vector3d(), new Vector3d());

            boolean sawImpact = false;
            for (int tick = 0; tick < 3 * TICKS_PER_SECOND && !sawImpact; tick++) {
                world.step(FLOOR);
                sawImpact = world.impacts().stream().anyMatch(impact -> impact.die() == die && impact.speed() > 1.0);
            }

            assertTrue(sawImpact, "a die falling two blocks should register an audible impact");
        }
    }

    @Test
    void aSettledDieStruckByAnotherStaysPut() {
        DiceShapes.Shape shape = DiceShapes.forSides(6);

        try (DicePhysicsWorld world = new DicePhysicsWorld()) {
            DicePhysicsWorld.Die resting = world.addDie(shape,
                    new Vector3d(0.5, 1.5, 0.5), new Vector3d(), new Vector3d());
            run(world, 2);
            resting.settle();
            Vector3d frozen = resting.position();

            DicePhysicsWorld.Die falling = world.addDie(shape,
                    new Vector3d(0.55, 2.5, 0.5), new Vector3d(0, -4, 0), new Vector3d(3, 0, 2));
            run(world, 3);
            falling.settle();
            run(world, 2);

            assertTrue(resting.position().distance(frozen) < 1.0e-9,
                    "a settled die was knocked loose, ending at " + resting.position());
        }
    }

    @Test
    void diceCollideWithEachOtherRatherThanOverlapping() {
        DiceShapes.Shape shape = DiceShapes.forSides(6);

        try (DicePhysicsWorld world = new DicePhysicsWorld()) {
            DicePhysicsWorld.Die a = world.addDie(shape, new Vector3d(0.5, 1.2, 0.5), new Vector3d(), new Vector3d());
            DicePhysicsWorld.Die b = world.addDie(shape, new Vector3d(0.55, 2.0, 0.5), new Vector3d(), new Vector3d());
            run(world, 4);

            double separation = a.position().distance(b.position());
            assertTrue(separation > shape.boundingRadius(), "two dice ended up inside each other, " + separation + " apart");
        }
    }

    private static double reboundHeight(DicePhysicsWorld.Surface surface) {
        DicePhysicsWorld.TerrainSource floor = floorOf(surface);
        try (DicePhysicsWorld world = new DicePhysicsWorld()) {
            DicePhysicsWorld.Die die = world.addDie(DiceShapes.forSides(6),
                    new Vector3d(0.5, 3.0, 0.5), new Vector3d(), new Vector3d());

            boolean landed = false;
            for (int tick = 0; tick < 3 * TICKS_PER_SECOND && !landed; tick++) {
                world.step(floor);
                landed = !world.impacts().isEmpty();
            }
            assertTrue(landed, "the die never reached the floor");

            double landedAt = die.position().y;
            double highest = landedAt;
            for (int tick = 0; tick < TICKS_PER_SECOND; tick++) {
                world.step(floor);
                highest = Math.max(highest, die.position().y);
            }
            return highest - landedAt;
        }
    }

    private static double slideDistance(DicePhysicsWorld.Surface surface) {
        DicePhysicsWorld.TerrainSource floor = floorOf(surface);
        try (DicePhysicsWorld world = new DicePhysicsWorld()) {
            DiceShapes.Shape shape = DiceShapes.forSides(6);
            double resting = FLOOR_TOP + DiceOrientation.restingHeight(new Quaternionf(), shape);
            DicePhysicsWorld.Die die = world.addDie(shape,
                    new Vector3d(0.5, resting, 0.5), new Vector3d(4, 0, 0), new Vector3d());
            for (int tick = 0; tick < 3 * TICKS_PER_SECOND; tick++) {
                world.step(floor);
            }
            return die.position().x - 0.5;
        }
    }

    @Test
    void aBouncySurfaceThrowsDiceBackUp() {
        double bouncy = reboundHeight(new DicePhysicsWorld.Surface(DiceTuning.FRICTION, 0.8, 0));
        double ordinary = reboundHeight(DicePhysicsWorld.Surface.DEFAULT);
        assertTrue(bouncy > 0.5 && bouncy > 5 * ordinary,
                "bounced " + bouncy + " off a bouncy floor against " + ordinary + " off an ordinary one");
    }

    @Test
    void diceSlideFurtherOnASlipperySurface() {
        double slippery = slideDistance(new DicePhysicsWorld.Surface(DiceTuning.FRICTION * 0.05, DiceTuning.RESTITUTION, 0));
        double ordinary = slideDistance(DicePhysicsWorld.Surface.DEFAULT);
        assertTrue(slippery > 2 * ordinary, "slid " + slippery + " on a slippery floor against " + ordinary);
    }

    @Test
    void aStickySurfaceHoldsDiceInPlace() {
        double sticky = slideDistance(new DicePhysicsWorld.Surface(DiceTuning.FRICTION, 0, DiceTuning.STICKY_DRAG / 2));
        double ordinary = slideDistance(new DicePhysicsWorld.Surface(DiceTuning.FRICTION, 0, 0));
        assertTrue(sticky < ordinary / 2, "slid " + sticky + " on a sticky floor against " + ordinary);
    }

    @Test
    void aHandfulOfDiceLeavesTheHandTogether() {
        Random random = new Random(10);
        List<Vector3d> positions = new ArrayList<>();
        List<Vector3d> velocities = new ArrayList<>();
        DiceThrow.layout(10, new Vector3d(0.5, FLOOR_TOP + 1.5, 0.5), new Vector3d(6, -3.5, 0), random, positions, velocities);

        try (DicePhysicsWorld world = new DicePhysicsWorld()) {
            List<DicePhysicsWorld.Die> dice = new ArrayList<>();
            for (int i = 0; i < positions.size(); i++) {
                dice.add(world.addDie(DiceShapes.forSides(6), positions.get(i), velocities.get(i), spin(random)));
            }
            run(world, 3);

            for (DicePhysicsWorld.Die die : dice) {
                double sideways = Math.abs(die.position().z - 0.5);
                assertTrue(sideways < 2.0, "a die was flung " + sideways + " blocks sideways");
            }
        }
    }
}
