package net.geryu.polyhedrals;

import net.geryu.polyhedrals.physics.DicePhysicsWorld;
import net.geryu.polyhedrals.physics.DiceTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = Polyhedrals.MODID)
public final class DicePhysicsHandler {
    private static final Map<ServerLevel, DicePhysicsWorld> WORLDS = new IdentityHashMap<>();

    private DicePhysicsHandler() {}

    public static DicePhysicsWorld worldFor(ServerLevel level) {
        return WORLDS.computeIfAbsent(level, unused -> new DicePhysicsWorld());
    }

    static void removeDie(ServerLevel level, DicePhysicsWorld.Die die) {
        DicePhysicsWorld physics = WORLDS.get(level);
        if (physics != null) {
            physics.remove(die);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;

        DicePhysicsWorld physics = WORLDS.get(level);
        if (physics == null)
            return;

        physics.step((region, out) -> collectBlockBoxes(level, region, out));
        playImpacts(level, physics);
    }

    private static void collectBlockBoxes(ServerLevel level, DicePhysicsWorld.Box region,
                                          Set<DicePhysicsWorld.Box> out) {
        AABB area = new AABB(region.minX(), region.minY(), region.minZ(),
                region.maxX(), region.maxY(), region.maxZ());
        for (VoxelShape shape : level.getBlockCollisions(null, area)) {
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs()) {
                out.add(new DicePhysicsWorld.Box(box.minX, box.minY, box.minZ,
                        box.maxX, box.maxY, box.maxZ));
            }
        }
    }

    private static void playImpacts(ServerLevel level, DicePhysicsWorld physics) {
        if (physics.impacts().isEmpty())
            return;

        Map<DicePhysicsWorld.Die, DicePhysicsWorld.Impact> loudest = new HashMap<>();
        for (DicePhysicsWorld.Impact impact : physics.impacts()) {
            loudest.merge(impact.die(), impact, (a, b) -> a.speed() >= b.speed() ? a : b);
        }

        for (DicePhysicsWorld.Impact impact : loudest.values()) {
            Vector3d at = impact.position();
            SoundType sound = surfaceSoundAt(level, at);
            float strength = (float) Mth.clamp(impact.speed() / DiceTuning.LOUDEST_IMPACT,
                    DiceTuning.QUIETEST_IMPACT, 1.0);

            level.playSound(null, at.x, at.y, at.z,
                    sound.getStepSound(), SoundSource.BLOCKS,
                    sound.getVolume() * (float) DiceTuning.IMPACT_VOLUME * strength,
                    sound.getPitch() * (1.4F + level.random.nextFloat() * 0.2F));
        }
    }

    private static SoundType surfaceSoundAt(ServerLevel level, Vector3d at) {
        BlockPos below = BlockPos.containing(at.x, at.y - 0.08, at.z);
        BlockState state = level.getBlockState(below);
        if (state.isAir()) {
            below = BlockPos.containing(at.x, at.y, at.z);
            state = level.getBlockState(below);
        }
        return state.getSoundType(level, below, null);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            DicePhysicsWorld removed = WORLDS.remove(level);
            if (removed != null) {
                removed.close();
            }
        }
    }
}
