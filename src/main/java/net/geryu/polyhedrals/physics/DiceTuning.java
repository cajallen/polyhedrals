package net.geryu.polyhedrals.physics;

public final class DiceTuning {
    private DiceTuning() {}

    public static final double TIME_SCALE = 0.55;
    public static final double GRAVITY = -40.0;
    public static final double RESTITUTION = 0.15;
    public static final double BOUNCE_THRESHOLD = 0.8;
    public static final double FRICTION = 0.38;
    public static final double THROW_SPIN = 11.0;
    public static final double LINEAR_DAMPING_PER_SECOND = 0.20;
    public static final double ANGULAR_DAMPING_PER_SECOND = 0.75;
    public static final double DIE_MASS = 0.08;
    public static final double SUBSTEP_HZ = 360.0;
    public static final double MAX_SPEED = 30.0;
    public static final double TERRAIN_MARGIN = 1.0;
    public static final int REST_WINDOW = 10;
    public static final double REST_SPEED = 0.1;
    public static final double REST_TURN_RATE = 40.0;
    public static final int ALIGN_TICKS = 4;
    public static final double READABLE_TILT_DEGREES = 20.0;
    public static final double RETURN_AFTER_SECONDS = 15.0;
    public static final double DISCARD_AFTER_SECONDS = 10.0;
    public static final double SETTLE_DEADLINE_SECONDS = 30.0;
    public static final double MIN_AUDIBLE_IMPACT = 1.2;
    public static final double LOUDEST_IMPACT = 9.0;
    public static final double IMPACT_VOLUME = 1.1;
    public static final double QUIETEST_IMPACT = 0.35;

    static double tickSeconds() {
        return 0.05 * TIME_SCALE;
    }
    static double substepSeconds() {
        return 1.0 / SUBSTEP_HZ;
    }
    static double restDriftAllowance() {
        return REST_SPEED * REST_WINDOW * tickSeconds();
    }
    static double restTurnAllowance() {
        return Math.toRadians(REST_TURN_RATE * REST_WINDOW * tickSeconds());
    }
    static double perSubstepDamping(double perSecond) {
        double retainedPerSecond = Math.max(1.0e-6, 1.0 - perSecond);
        return 1.0 - Math.pow(retainedPerSecond, substepSeconds());
    }
}
