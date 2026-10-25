package net.geryu.polyhedrals.api;

import net.geryu.polyhedrals.DiceColors;
import net.geryu.polyhedrals.DiceEntity;
import net.geryu.polyhedrals.Polyhedrals;
import net.geryu.polyhedrals.PolyhedralsConfig;
import net.geryu.polyhedrals.RollManager;
import net.geryu.polyhedrals.api.parser.ASTNode;
import net.geryu.polyhedrals.api.parser.DiceParser;
import net.geryu.polyhedrals.api.parser.PhysicalDice;
import net.geryu.polyhedrals.api.parser.RollContext;
import net.geryu.polyhedrals.physics.DiceThrow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RollBuilder {
    public static final int MAX_DICE = 100;
    public static final int MAX_SIDES = 1000;

    private final String expression;
    private Entity owner;
    private Vec3 position;
    private Vec3 velocity = Vec3.ZERO;
    private DiceColors colors;
    private BroadcastMode broadcast;
    private boolean canPickup = false;
    private ItemStack customItem = null;
    private PresetRoll preset = null;

    RollBuilder(String expression) {
        this.expression = expression;
    }

    /** Overrides the configured broadcast mode for this roll; {@link BroadcastMode#NONE} to report it yourself. */
    public RollBuilder broadcast(BroadcastMode broadcast) {
        this.broadcast = broadcast;
        return this;
    }

    public RollBuilder at(Vec3 position) {
        this.position = position;
        return this;
    }

    public RollBuilder thrownBy(Entity owner) {
        this.owner = owner;
        return this;
    }

    /** Tosses the dice underhand from {@code thrower}'s hand, so they rise into view rather than leave from the eyes. */
    public RollBuilder underhand(LivingEntity thrower, InteractionHand hand) {
        Vec3 look = thrower.getLookAngle();
        Vec3 heading = Vec3.directionFromRotation(0, thrower.getYRot());
        Vector3d forward = new Vector3d(heading.x, 0, heading.z).normalize();
        boolean rightHand = (hand == InteractionHand.MAIN_HAND) == (thrower.getMainArm() == HumanoidArm.RIGHT);
        Vector3d offset = DiceThrow.handOffset(forward, rightHand ? 1 : -1);
        Vector3d lob = DiceThrow.lob(forward, look.y);

        Vec3 body = thrower.position().add(0, offset.y, 0);
        Vec3 handPos = thrower.position().add(offset.x, offset.y, offset.z);
        boolean blocked = thrower.level().clip(new ClipContext(body, handPos, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, thrower)).getType() != HitResult.Type.MISS;

        this.owner = thrower;
        this.position = blocked ? body : handPos;
        this.velocity = vec3(lob);
        return this;
    }

    /** In blocks per tick, matching {@link Entity#setDeltaMovement}. */
    public RollBuilder withVelocity(Vec3 velocity) {
        this.velocity = velocity;
        return this;
    }

    public RollBuilder colors(int faceColor, int detailColor) {
        this.colors = new DiceColors(faceColor, detailColor);
        return this;
    }

    public RollBuilder canPickup(boolean canPickup) {
        this.canPickup = canPickup;
        return this;
    }

    public RollBuilder item(ItemStack item) {
        this.customItem = item;
        return this;
    }

    public RollBuilder preset(PresetRoll preset) {
        this.preset = preset;
        return this;
    }

    public Integer evaluateLogical() {
        try {
            return DiceParser.parse(expression).evaluate(new RollContext(sides -> PolyhedralsAPI.rollLogical(sides, owner)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public UUID spawn() {
        if (owner == null) {
            throw new IllegalStateException("Owner (roller) must be set for physical rolls");
        }
        Level level = owner.level();
        if (level.isClientSide)
            return null;

        ASTNode root;
        List<PhysicalDice> dice = new ArrayList<>();
        try {
            root = DiceParser.parse(expression);
            root.collectPhysicalDice(new RollContext(sides -> PolyhedralsAPI.rollLogical(sides, owner)), dice);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int count = dice.stream().mapToInt(PhysicalDice::count).sum();
        if (count > MAX_DICE || count < 1 || dice.stream().anyMatch(d -> d.sides() < 1 || d.sides() > MAX_SIDES))
            return null;

        UUID groupId = RollManager.startGroup(owner, dice, root,
                broadcast != null ? broadcast : PolyhedralsConfig.BROADCAST.get());
        int[] presetFaces = preset == null ? null
                : preset.facesFor(root, dice, sides -> PolyhedralsAPI.RANDOM.nextInt(sides) + 1);

        // count literal die for de-overlapping
        int slots = count + dice.stream().filter(d -> d.sides() == 100).mapToInt(PhysicalDice::count).sum();
        Vec3 origin = position != null ? position : owner.position().add(0, 1.5, 0);
        List<Vector3d> positions = new ArrayList<>();
        List<Vector3d> velocities = new ArrayList<>();
        DiceThrow.layout(slots, new Vector3d(origin.x, origin.y, origin.z),
                new Vector3d(velocity.x, velocity.y, velocity.z), PolyhedralsAPI.RANDOM, positions, velocities);

        int slot = 0;
        int thrown = 0;
        for (PhysicalDice term : dice) {
            for (int i = 0; i < term.count(); i++) {
                DiceEntity die = newDie(level, origin, positions.get(slot), velocities.get(slot++),
                        term.sides(), dieStack(term.sides()), canPickup);
                die.joinGroup(groupId, term.nodeId());
                if (presetFaces != null) {
                    die.restShowing(presetFaces[thrown]);
                }
                thrown++;
                if (term.sides() == 100) {
                    ItemStack unitsStack = new ItemStack(Polyhedrals.diceItem(10));
                    unitsStack.set(Polyhedrals.TWO_TONE_COLORS.get(),
                            die.getItem().getOrDefault(Polyhedrals.TWO_TONE_COLORS.get(), DiceColors.DEFAULT));
                    DiceEntity units = newDie(level, origin, positions.get(slot), velocities.get(slot++), 10, unitsStack, false);
                    die.pairWithUnitsDie(units);
                    level.addFreshEntity(units);
                }
                level.addFreshEntity(die);
            }
        }
        return groupId;
    }

    private ItemStack dieStack(int sides) {
        if (customItem != null && !customItem.isEmpty()) {
            return customItem.copy();
        }
        ItemStack stack = new ItemStack(Polyhedrals.diceItem(sides));
        DiceColors chosen = colors != null ? colors
                : owner instanceof Player player ? player.getData(Polyhedrals.DEFAULT_COLORS) : null;
        if (chosen != null) {
            stack.set(Polyhedrals.TWO_TONE_COLORS.get(), chosen);
        }
        return stack;
    }

    private DiceEntity newDie(Level level, Vec3 origin, Vector3d target, Vector3d dieVelocity, int sides,
                              ItemStack stack, boolean pickup) {
        DiceEntity die = new DiceEntity(Polyhedrals.DICE_ENTITY.get(), level);
        die.setPos(clear(level, origin, vec3(target)));
        die.setDeltaMovement(vec3(dieVelocity));
        die.setSides(sides);
        die.setItem(stack);
        die.setCanPickup(pickup);
        die.setOwner(owner);
        return die;
    }

    private Vec3 clear(Level level, Vec3 origin, Vec3 target) {
        BlockHitResult hit = level.clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS ? target : origin.lerp(hit.getLocation(), 0.5);
    }

    private static Vec3 vec3(Vector3d v) {
        return new Vec3(v.x, v.y, v.z);
    }
}
