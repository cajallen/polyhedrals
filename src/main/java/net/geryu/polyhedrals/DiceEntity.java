package net.geryu.polyhedrals;

import net.geryu.polyhedrals.api.DiceRollEvent;
import net.geryu.polyhedrals.api.PhysicalDiceLandEvent;
import net.geryu.polyhedrals.physics.DiceFaceNumbering;
import net.geryu.polyhedrals.physics.DiceOrientation;
import net.geryu.polyhedrals.physics.DicePhysicsWorld;
import net.geryu.polyhedrals.physics.DiceShapes;
import net.geryu.polyhedrals.physics.DiceTuning;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DiceEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM =
            SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Quaternionf> DATA_ORIENTATION =
            SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.QUATERNION);
    // The client draws each mesh about its own centre of mass, so it has to know which die this is.
    private static final EntityDataAccessor<Integer> DATA_SIDES =
            SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.INT);

    private static final int PICKUP_DELAY = 20;
    private static final double LOST_BELOW_WORLD = 32.0;
    private static final double PLACED_THROW_TICKS = 6.0;
    private static final double PLACED_MAX_DROP = 8.0;

    private boolean canPickup;
    private UUID rollGroup;
    private int diceNodeId;
    private final Set<ServerPlayer> viewers = new HashSet<>();
    private int result;
    private int presetNatural;
    private UUID ownerId;

    private UUID partnerId;
    private boolean isUnitsDie;
    private boolean awaitingUnits;
    private int unitsRoll;

    private DicePhysicsWorld.Die physics;
    private boolean launched;
    private int alignTicks;
    private Quaternionf alignFrom;
    private Quaternionf alignTarget;
    private boolean landed;
    private int ticksAtRest;
    private boolean claimed;

    private Quaternionf clientPrevOrientation;
    private Quaternionf clientOrientation;
    private Vec3 lerpTarget;

    public DiceEntity(EntityType<? extends DiceEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        if (!level.isClientSide) {
            entityData.set(DATA_ORIENTATION, DiceOrientation.random(random::nextGaussian));
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!launched && presetNatural == 0) {
            launch();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_ORIENTATION, new Quaternionf());
        builder.define(DATA_SIDES, 20);
    }

    public void setSides(int sides) {
        entityData.set(DATA_SIDES, sides);
    }

    public int getSides() {
        return entityData.get(DATA_SIDES);
    }

    public void joinGroup(UUID rollGroup, int diceNodeId) {
        this.rollGroup = rollGroup;
        this.diceNodeId = diceNodeId;
    }

    public UUID getRollGroup() {
        return rollGroup;
    }

    int diceNodeId() {
        return diceNodeId;
    }

    Set<ServerPlayer> viewers() {
        return viewers;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        viewers.add(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        viewers.remove(player);
    }

    public void restShowing(int natural) {
        this.presetNatural = natural;
    }

    public void setCanPickup(boolean canPickup) {
        this.canPickup = canPickup;
    }

    public void setItem(ItemStack stack) {
        this.entityData.set(DATA_ITEM, stack.copy());
    }

    public ItemStack getItem() {
        return this.entityData.get(DATA_ITEM);
    }

    public void setOwner(Entity owner) {
        this.ownerId = owner == null ? null : owner.getUUID();
    }

    public Entity getOwner() {
        return ownerId != null && level() instanceof ServerLevel serverLevel
                ? findEntity(serverLevel.getServer(), ownerId) : null;
    }

    static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null)
                return entity;
        }
        return null;
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            advanceClientInterpolation();
            return;
        }
        // Our roll has already finished, exit
        if (!landed && rollGroup != null && !RollManager.isActive(rollGroup)) {
            cancel();
            return;
        }
        if (!launched) {
            placeAtRest();
        }
        if (physics == null) {
            if (landed) {
                tickAtRest();
            }
            return;
        }

        if (getY() < level().getMinBuildHeight() - LOST_BELOW_WORLD) {
            if (!landed) {
                land();
            }
            if (!canPickup || returnToThrower()) {
                discard();
            } else {
                physics.settle();
            }
            return;
        }

        if (alignTarget != null) {
            advanceAlignment();
        } else {
            syncFromBody();
            if (!landed) {
                checkForRest();
            }
        }

        if (landed) {
            tickAtRest();
        }
    }

    private void launch() {
        launched = true;
        if (!(level() instanceof ServerLevel serverLevel))
            return;

        Vec3 velocity = getDeltaMovement().scale(20.0);
        physics = DicePhysicsHandler.worldFor(serverLevel).addDie(
                DiceShapes.forSides(getSides()),
                new Vector3d(getX(), getY(), getZ()),
                new Vector3d(velocity.x, velocity.y, velocity.z),
                new Vector3d(
                        random.nextGaussian() * DiceTuning.THROW_SPIN,
                        random.nextGaussian() * DiceTuning.THROW_SPIN,
                        random.nextGaussian() * DiceTuning.THROW_SPIN));
        physics.setOrientation(entityData.get(DATA_ORIENTATION));
        setDeltaMovement(Vec3.ZERO);
    }

    public void pairWithUnitsDie(DiceEntity units) {
        units.isUnitsDie = true;
        units.partnerId = getUUID();
        partnerId = units.getUUID();
        if (presetNatural > 0) {
            units.restShowing(presetNatural - DiceFaceNumbering.percentileTens(presetNatural));
        }
    }

    private DiceEntity partner() {
        return partnerId != null && level() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(partnerId) instanceof DiceEntity die ? die : null;
    }

    private void placeAtRest() {
        launched = true;
        DiceShapes.Shape shape = DiceShapes.forSides(getSides());
        int face = DiceFaceNumbering.faceShowing(shape, presetNatural);
        Quaternionf pose = face < 0 ? new Quaternionf()
                : DiceOrientation.showing(shape, face, random.nextFloat() * (float) (2 * Math.PI));
        entityData.set(DATA_ORIENTATION, pose);

        Vec3 from = position();
        Vec3 carried = getDeltaMovement().multiply(PLACED_THROW_TICKS, 0, PLACED_THROW_TICKS);
        BlockHitResult ahead = level().clip(new ClipContext(
                from, from.add(carried), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 above = ahead.getType() == HitResult.Type.MISS ? ahead.getLocation()
                : ahead.getLocation().subtract(carried.normalize().scale(DiceShapes.WORLD_SCALE));
        BlockHitResult floor = level().clip(new ClipContext(
                above, above.subtract(0, PLACED_MAX_DROP, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double y = floor.getType() == HitResult.Type.MISS ? above.y : floor.getLocation().y;
        setPos(above.x, y + DiceOrientation.restingHeight(pose, shape), above.z);
        setDeltaMovement(Vec3.ZERO);
        land();
    }

    private void syncFromBody() {
        Vector3d pos = physics.position();
        setPos(pos.x, pos.y, pos.z);
        entityData.set(DATA_ORIENTATION, physics.orientation());
    }

    private void checkForRest() {
        if (physics.isAtRest() || tickCount > DiceTuning.SETTLE_DEADLINE_SECONDS * 20) {
            beginAlignment();
        }
    }

    private void beginAlignment() {
        physics.settle();
        alignFrom = new Quaternionf(physics.orientation());
        alignTarget = DiceOrientation.restingRotation(alignFrom, physics.shape());
        alignTicks = 0;
    }

    private void advanceAlignment() {
        alignTicks++;
        float progress = Math.min(1.0f, (float) alignTicks / DiceTuning.ALIGN_TICKS);
        Quaternionf eased = new Quaternionf(alignFrom).slerp(alignTarget, progress);
        physics.setOrientation(eased);
        entityData.set(DATA_ORIENTATION, eased);

        if (progress >= 1.0f) {
            alignFrom = null;
            alignTarget = null;
            land();
        }
    }

    private void land() {
        // We use 2 d10s for d100 instead of the sphere, so there is some deferred handoff here
        if (landed)
            return;
        landed = true;
        if (isUnitsDie) {
            result = naturalRoll();
            DiceEntity percentile = partner();
            if (percentile != null) {
                percentile.unitsLanded(result);
            }
            return;
        }
        if (getSides() == 100) {
            awaitingUnits = true;
            if (unitsRoll > 0) {
                unitsLanded(unitsRoll);
            }
            return;
        }
        publish(naturalRoll());
    }

    private void unitsLanded(int units) {
        unitsRoll = units;
        if (awaitingUnits) {
            awaitingUnits = false;
            publish(naturalRoll() + unitsRoll);
        }
    }

    private void forceUnits() {
        DiceEntity units = partner();
        if (units != null) {
            units.land();
            unitsLanded(units.result);
        } else {
            unitsLanded(random.nextInt(10) + 1);
        }
    }

    private void publish(int natural) {
        result = NeoForge.EVENT_BUS.post(new DiceRollEvent(getOwner(), getSides(), natural)).getTotal();

        setCustomName(Component.literal(String.valueOf(result)));
        setCustomNameVisible(true);

        NeoForge.EVENT_BUS.post(new PhysicalDiceLandEvent(this, result));
        if (rollGroup != null) {
            RollManager.addResult(this, result);
        }
    }

    private int naturalRoll() {
        boolean percentile = getSides() == 100;
        if (presetNatural > 0) {
            return percentile ? DiceFaceNumbering.percentileTens(presetNatural) : presetNatural;
        }
        // A die with no model of its own tumbles as a d20, whose faces it cannot report.
        if (physics != null && (percentile || physics.shape().faceCount() == getSides())) {
            int shown = DiceOrientation.shownValue(
                    physics.orientation(), physics.shape(), DiceTuning.READABLE_TILT_DEGREES);
            if (shown >= 0) {
                return shown;
            }
        }
        return percentile ? random.nextInt(10) * 10 : random.nextInt(getSides()) + 1;
    }

    private void tickAtRest() {
        ticksAtRest++;
        if (isUnitsDie) {
            if (partner() == null) {
                discard();
            }
            return;
        }
        if (awaitingUnits) {
            DiceEntity units = partner();
            if (units == null || units.landed) {
                forceUnits();
            }
            return;
        }
        if (!canPickup) {
            if (ticksAtRest > DiceTuning.DISCARD_AFTER_SECONDS * 20) {
                discard();
            }
            return;
        }
        if (ticksAtRest < PICKUP_DELAY)
            return;

        List<Player> nearby = level().getEntitiesOfClass(
                Player.class, getBoundingBox().inflate(0.6), player -> player.isAlive() && !player.isSpectator());
        for (Player player : nearby) {
            ItemStack stack = getItem().copy();
            if (!stack.isEmpty() && player.getInventory().add(stack)) {
                claimed = true;
                player.take(this, 1);
                discard();
                return;
            }
        }

        if (ticksAtRest > DiceTuning.RETURN_AFTER_SECONDS * 20 && returnToThrower()) {
            discard();
        }
    }

    private boolean returnToThrower() {
        if (claimed)
            return true;
        ItemStack stack = getItem().copy();
        if (stack.isEmpty()) {
            claimed = true;
            return true;
        }
        if (!(getOwner() instanceof Player player) || !player.isAlive()) {
            return false;
        }
        claimed = true;
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        return true;
    }

    private void dropWhereItLies() {
        if (claimed)
            return;
        ItemStack stack = getItem().copy();
        if (stack.isEmpty())
            return;
        claimed = true;
        level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), stack));
    }

    void cancel() {
        landed = true;
        awaitingUnits = false;
        DiceEntity units = isUnitsDie ? null : partner();
        if (units != null) {
            units.cancel();
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            if (!landed) {
                land();
            }
            if (awaitingUnits) {
                forceUnits();
            }
            // Player can go back to chunk to pickup.
            if (canPickup && !reason.shouldSave() && !returnToThrower()) {
                dropWhereItLies();
            }
        }
        super.remove(reason);
    }

    @Override
    public void onRemovedFromLevel() {
        // Chunk unloads skip remove()
        super.onRemovedFromLevel();
        if (physics != null && level() instanceof ServerLevel serverLevel) {
            DicePhysicsHandler.removeDie(serverLevel, physics);
            physics = null;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpTarget = new Vec3(x, y, z);
    }

    @Override
    public double lerpTargetX() {
        return lerpTarget != null ? lerpTarget.x : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpTarget != null ? lerpTarget.y : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpTarget != null ? lerpTarget.z : getZ();
    }

    private void advanceClientInterpolation() {
        if (lerpTarget != null) {
            setPos(lerpTarget);
            lerpTarget = null;
        }
        Quaternionf synced = new Quaternionf(entityData.get(DATA_ORIENTATION));
        clientPrevOrientation = clientOrientation != null ? clientOrientation : synced;
        clientOrientation = synced;
    }

    public Quaternionf getRenderOrientation(float partialTick) {
        if (clientOrientation == null)
            return new Quaternionf(entityData.get(DATA_ORIENTATION));
        return new Quaternionf(clientPrevOrientation).slerp(clientOrientation, partialTick);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Sides")) {
            setSides(tag.getInt("Sides"));
        }
        result = tag.getInt("Result");
        presetNatural = tag.getInt("PresetNatural");
        landed = tag.getBoolean("Landed");
        launched = landed;
        if (tag.hasUUID("RollGroup")) {
            rollGroup = tag.getUUID("RollGroup");
        }
        diceNodeId = tag.getInt("DiceNodeId");
        canPickup = tag.getBoolean("CanPickup");
        if (tag.hasUUID("Owner")) {
            ownerId = tag.getUUID("Owner");
        }
        if (tag.hasUUID("Partner")) {
            partnerId = tag.getUUID("Partner");
        }
        isUnitsDie = tag.getBoolean("UnitsDie");
        awaitingUnits = tag.getBoolean("AwaitingUnits");
        unitsRoll = tag.getInt("UnitsRoll");
        if (tag.contains("Item")) {
            setItem(ItemStack.parseOptional(registryAccess(), tag.getCompound("Item")));
        }
        if (tag.contains("Orientation")) {
            ExtraCodecs.QUATERNIONF.parse(NbtOps.INSTANCE, tag.get("Orientation"))
                    .ifSuccess(orientation -> entityData.set(DATA_ORIENTATION, orientation));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Sides", getSides());
        tag.putInt("Result", result);
        tag.putInt("PresetNatural", presetNatural);
        tag.putBoolean("Landed", landed);
        if (rollGroup != null) {
            tag.putUUID("RollGroup", rollGroup);
        }
        tag.putInt("DiceNodeId", diceNodeId);
        tag.putBoolean("CanPickup", canPickup);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        if (partnerId != null) {
            tag.putUUID("Partner", partnerId);
        }
        tag.putBoolean("UnitsDie", isUnitsDie);
        tag.putBoolean("AwaitingUnits", awaitingUnits);
        tag.putInt("UnitsRoll", unitsRoll);
        if (!getItem().isEmpty()) {
            tag.put("Item", getItem().save(registryAccess()));
        }
        tag.put("Orientation", ExtraCodecs.QUATERNIONF.encodeStart(NbtOps.INSTANCE, entityData.get(DATA_ORIENTATION)).getOrThrow());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }
}
