package net.geryu.polyhedrals.physics;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.ode.DBody;
import org.ode4j.ode.DBox;
import org.ode4j.ode.DContact;
import org.ode4j.ode.DContactBuffer;
import org.ode4j.ode.DConvex;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.DJointGroup;
import org.ode4j.ode.DMass;
import org.ode4j.ode.DSpace;
import org.ode4j.ode.DWorld;
import org.ode4j.ode.OdeConstants;
import org.ode4j.ode.OdeHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DicePhysicsWorld implements AutoCloseable {
    private static final int MAX_CONTACTS = 8;

    private static final long CATEGORY_TERRAIN = 1L;
    private static final long CATEGORY_DIE = 2L;

    private static boolean odeStarted;

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    @FunctionalInterface
    public interface TerrainSource {
        void collectBoxes(Box region, Set<Box> out);
    }

    public record Impact(Die die, Vector3d position, double speed) {}

    private final DWorld world;
    private final DSpace space;
    private final DJointGroup contactGroup;
    private final DContactBuffer contactBuffer = new DContactBuffer(MAX_CONTACTS);

    private final List<Die> dice = new ArrayList<>();
    private final List<DBox> terrainPool = new ArrayList<>();
    private final Set<Box> terrainBoxes = new LinkedHashSet<>();
    private final List<Impact> impacts = new ArrayList<>();

    private final DVector3 scratchVelocityA = new DVector3();
    private final DVector3 scratchVelocityB = new DVector3();

    private double accumulator;

    public DicePhysicsWorld() {
        startOde();
        world = OdeHelper.createWorld();
        space = OdeHelper.createBVHSpace(CATEGORY_TERRAIN);
        contactGroup = OdeHelper.createJointGroup();

        // TODO? Move to tuning? Probably doesn't matter
        world.setERP(0.2);
        world.setCFM(1.0e-5);
        world.setQuickStepNumIterations(24);
        world.setContactSurfaceLayer(0.001);
        world.setContactMaxCorrectingVel(10);
        world.setGravity(0, DiceTuning.GRAVITY, 0);
        world.setDamping(
                DiceTuning.perSubstepDamping(DiceTuning.LINEAR_DAMPING_PER_SECOND),
                DiceTuning.perSubstepDamping(DiceTuning.ANGULAR_DAMPING_PER_SECOND));

        world.setAutoDisableFlag(true);
        world.setAutoDisableLinearThreshold(0.08);
        world.setAutoDisableAngularThreshold(0.15);
        world.setAutoDisableTime(0.2);
    }

    private static synchronized void startOde() {
        if (!odeStarted) {
            OdeHelper.initODE2(0);
            odeStarted = true;
        }
    }

    @Override
    public void close() {
        contactGroup.destroy();
        space.destroy();
        world.destroy();
    }

    public Die addDie(DiceShapes.Shape shape, Vector3d position, Vector3d velocity, Vector3d spin) {
        Die die = new Die(shape, position, velocity, spin);
        dice.add(die);
        return die;
    }

    public void remove(Die die) {
        if (dice.remove(die)) {
            die.destroy();
        }
    }

    public List<Impact> impacts() {
        return impacts;
    }

    public void step(TerrainSource terrain) {
        impacts.clear();
        if (dice.isEmpty()) {
            accumulator = 0;
            return;
        }

        refreshTerrain(terrain);

        double substep = DiceTuning.substepSeconds();
        accumulator += DiceTuning.tickSeconds();
        while (accumulator >= substep) {
            for (Die die : dice) {
                die.clampSpeed();
            }
            space.collide(null, this::onPotentialContact);
            world.quickStep(substep);
            contactGroup.empty();
            accumulator -= substep;
        }

        for (Die die : dice) {
            die.recordRestSample();
        }
    }

    private void refreshTerrain(TerrainSource terrain) {
        terrainBoxes.clear();
        for (Die die : dice) {
            if (die.settled) continue;
            Vector3d centre = die.position();
            double reach = die.shape.boundingRadius() + DiceTuning.TERRAIN_MARGIN;
            terrain.collectBoxes(new Box(
                    centre.x - reach, centre.y - reach, centre.z - reach,
                    centre.x + reach, centre.y + reach, centre.z + reach), terrainBoxes);
        }

        int used = 0;
        for (Box box : terrainBoxes) {
            DBox geom = terrainGeom(used++);
            geom.setLengths(
                    Math.max(box.maxX() - box.minX(), 1.0e-4),
                    Math.max(box.maxY() - box.minY(), 1.0e-4),
                    Math.max(box.maxZ() - box.minZ(), 1.0e-4));
            geom.setPosition(
                    (box.minX() + box.maxX()) * 0.5,
                    (box.minY() + box.maxY()) * 0.5,
                    (box.minZ() + box.maxZ()) * 0.5);
            geom.enable();
        }
        for (int i = used; i < terrainPool.size(); i++) {
            terrainPool.get(i).disable();
        }
    }

    private DBox terrainGeom(int index) {
        while (terrainPool.size() <= index) {
            DBox geom = OdeHelper.createBox(space, 1, 1, 1);
            geom.setCategoryBits(CATEGORY_TERRAIN);
            geom.setCollideBits(CATEGORY_DIE);
            terrainPool.add(geom);
        }
        return terrainPool.get(index);
    }

    private void onPotentialContact(Object data, DGeom o1, DGeom o2) {
        if (o1 instanceof DSpace || o2 instanceof DSpace) {
            OdeHelper.spaceCollide2(o1, o2, data, this::onPotentialContact);
            return;
        }
        DBody b1 = movableBody(o1);
        DBody b2 = movableBody(o2);
        if (b1 == null && b2 == null)
            return;

        for (DContact contact : contactBuffer) {
            contact.surface.mode = OdeConstants.dContactBounce
                    | OdeConstants.dContactSoftCFM
                    | OdeConstants.dContactApprox1;
            contact.surface.mu = DiceTuning.FRICTION;
            contact.surface.bounce = DiceTuning.RESTITUTION;
            contact.surface.bounce_vel = DiceTuning.BOUNCE_THRESHOLD;
            contact.surface.soft_cfm = 0.002;
        }

        int count = OdeHelper.collide(o1, o2, MAX_CONTACTS, contactBuffer.getGeomBuffer());
        for (int i = 0; i < count; i++) {
            DContact contact = contactBuffer.get(i);
            recordImpact(contact, b1, b2);
            OdeHelper.createContactJoint(world, contactGroup, contact).attach(b1, b2);
        }
    }

    private void recordImpact(DContact contact, DBody b1, DBody b2) {
        Die die = (Die) (b1 != null ? b1 : b2).getData();

        scratchVelocityA.setZero();
        scratchVelocityB.setZero();
        if (b1 != null) b1.getPointVel(contact.geom.pos, scratchVelocityA);
        if (b2 != null) b2.getPointVel(contact.geom.pos, scratchVelocityB);
        scratchVelocityA.eqDiff(scratchVelocityA, scratchVelocityB);

        // Read before the step, since the step cancels the approach.
        double closingSpeed = Math.abs(scratchVelocityA.dot(contact.geom.normal));
        if (closingSpeed < DiceTuning.MIN_AUDIBLE_IMPACT)
            return;

        impacts.add(new Impact(die, new Vector3d(
                contact.geom.pos.get0(), contact.geom.pos.get1(), contact.geom.pos.get2()),
                closingSpeed));
    }

    // Force die to static when settled.
    private static DBody movableBody(DGeom geom) {
        DBody body = geom.getBody();
        return body == null || ((Die) body.getData()).settled ? null : body;
    }

    public final class Die {
        private final DiceShapes.Shape shape;
        private final DBody body;
        private final DConvex geom;
        private boolean settled;

        private final Vector3d[] restPositions = new Vector3d[DiceTuning.REST_WINDOW];
        private final Quaternionf[] restOrientations = new Quaternionf[DiceTuning.REST_WINDOW];
        private int restSamples;
        private int restCursor;

        private Die(DiceShapes.Shape shape, Vector3d position, Vector3d velocity, Vector3d spin) {
            this.shape = shape;
            this.body = OdeHelper.createBody(world);
            this.body.setData(this);

            ConvexHull.Result hull = shape.hull();
            this.geom = OdeHelper.createConvex(space,
                    hull.planes(), hull.planeCount(),
                    hull.points(), hull.pointCount(),
                    hull.polygons());
            this.geom.setBody(body);
            this.geom.setCategoryBits(CATEGORY_DIE);
            this.geom.setCollideBits(CATEGORY_TERRAIN | CATEGORY_DIE);

            DMass mass = OdeHelper.createMass();
            double[] inertia = shape.inertia();
            double dieMass = DiceTuning.DIE_MASS;
            // ode argument order is l11, l22, l33, l12, l13, l23
            mass.setParameters(dieMass, 0, 0, 0,
                    dieMass * inertia[0], dieMass * inertia[4], dieMass * inertia[8],
                    dieMass * inertia[1], dieMass * inertia[2], dieMass * inertia[5]);
            this.body.setMass(mass);

            this.body.setPosition(position.x, position.y, position.z);
            this.body.setLinearVel(velocity.x, velocity.y, velocity.z);
            this.body.setAngularVel(spin.x, spin.y, spin.z);
        }

        public Vector3d position() {
            var p = body.getPosition();
            return new Vector3d(p.get0(), p.get1(), p.get2());
        }

        public Quaternionf orientation() {
            var q = body.getQuaternion();
            // ode4j w first, JOML w last.
            return new Quaternionf(
                    (float) q.get1(), (float) q.get2(), (float) q.get3(), (float) q.get0());
        }

        public void setOrientation(Quaternionf q) {
            body.setQuaternion(new DQuaternion(q.w(), q.x(), q.y(), q.z()));
        }

        private void clampSpeed() {
            if (settled)
                return;
            var velocity = body.getLinearVel();
            double speed = velocity.length();
            if (speed > DiceTuning.MAX_SPEED) {
                double scale = DiceTuning.MAX_SPEED / speed;
                body.setLinearVel(
                        velocity.get0() * scale,
                        velocity.get1() * scale,
                        velocity.get2() * scale);
            }
        }

        private void recordRestSample() {
            if (settled)
                return;
            int slot = restCursor % restPositions.length;
            restPositions[slot] = position();
            restOrientations[slot] = orientation();
            restCursor++;
            if (restSamples < restPositions.length) restSamples++;
        }

        public boolean isAtRest() {
            if (restSamples < restPositions.length)
                return false;
            int oldest = restCursor % restPositions.length;
            double drift = position().distance(restPositions[oldest]);
            double turn = restOrientations[oldest].difference(orientation(), new Quaternionf()).angle();
            return drift < DiceTuning.restDriftAllowance() && turn < DiceTuning.restTurnAllowance();
        }

        public void settle() {
            settled = true;
            body.setLinearVel(0, 0, 0);
            body.setAngularVel(0, 0, 0);
            body.disable();
        }

        public DiceShapes.Shape shape() {
            return shape;
        }

        private void destroy() {
            geom.destroy();
            body.destroy();
        }
    }
}
