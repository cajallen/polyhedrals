package net.geryu.polyhedrals;

import net.geryu.polyhedrals.api.BroadcastMode;
import net.geryu.polyhedrals.api.PhysicalRollGroupEvent;
import net.geryu.polyhedrals.api.PolyhedralsAPI;
import net.geryu.polyhedrals.api.RollResult;
import net.geryu.polyhedrals.api.parser.ASTNode;
import net.geryu.polyhedrals.api.parser.PhysicalDice;
import net.geryu.polyhedrals.api.parser.RollContext;
import net.geryu.polyhedrals.physics.DiceTuning;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = Polyhedrals.MODID)
public final class RollManager {
    private static final Map<UUID, Group> ACTIVE_GROUPS = new HashMap<>();
    private static final int TIMEOUT_TICKS = (int) ((DiceTuning.SETTLE_DEADLINE_SECONDS + 5.0) * 20);

    private RollManager() {}

    public static UUID startGroup(Entity roller, List<PhysicalDice> dice, ASTNode root, BroadcastMode broadcast) {
        UUID groupId = UUID.randomUUID();
        ACTIVE_GROUPS.put(groupId, new Group(roller, dice, root, broadcast));
        return groupId;
    }

    static boolean isActive(UUID groupId) {
        return ACTIVE_GROUPS.containsKey(groupId);
    }

    static boolean isRolling(Entity owner) {
        return ACTIVE_GROUPS.values().stream().anyMatch(group -> group.ownerId.equals(owner.getUUID()));
    }

    static boolean queueClear(Entity owner, int delayTicks) {
        boolean queued = false;
        for (Group group : ACTIVE_GROUPS.values()) {
            if (group.ownerId.equals(owner.getUUID())) {
                if (group.clearInTicks == 0) {
                    group.clearInTicks = delayTicks;
                }
                queued = true;
            }
        }
        return queued;
    }

    static void addResult(DiceEntity die, int result) {
        Group group = ACTIVE_GROUPS.get(die.getRollGroup());
        if (group == null)
            return;
        group.results.add(result);
        group.ctx.addPhysicalResult(die.diceNodeId(), result);
        group.idleTicks = 0;
        if (group.results.size() >= group.expected) {
            ACTIVE_GROUPS.remove(die.getRollGroup());
            group.finish(die.getRollGroup(), die.viewers());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Set<UUID> cleared = new HashSet<>();
        Map<UUID, Group> timedOut = new HashMap<>();
        ACTIVE_GROUPS.entrySet().removeIf(entry -> {
            Group group = entry.getValue();
            if (group.clearInTicks > 0 && --group.clearInTicks == 0) {
                cleared.add(entry.getKey());
                return true;
            }
            if (++group.idleTicks > TIMEOUT_TICKS) {
                timedOut.put(entry.getKey(), group);
                return true;
            }
            return false;
        });
        timedOut.forEach((groupId, group) -> {
            group.results.addAll(group.ctx.rollMissing(group.dice));
            group.finish(groupId, Set.of());
        });
        if (cleared.isEmpty())
            return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            level.getEntities(Polyhedrals.DICE_ENTITY.get(), die -> cleared.contains(die.getRollGroup()))
                    .forEach(DiceEntity::cancel);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_GROUPS.clear();
    }

    private static final class Group {
        private final MinecraftServer server;
        private final UUID ownerId;
        private final String ownerName;
        private final List<PhysicalDice> dice;
        private final int expected;
        private final ASTNode root;
        private final BroadcastMode broadcast;
        private final RollContext ctx;
        private final List<Integer> results = new ArrayList<>();
        private int idleTicks;
        private int clearInTicks;

        Group(Entity owner, List<PhysicalDice> dice, ASTNode root, BroadcastMode broadcast) {
            this.server = owner.getServer();
            this.ownerId = owner.getUUID();
            this.ownerName = owner.getScoreboardName();
            this.dice = dice;
            this.expected = dice.stream().mapToInt(PhysicalDice::count).sum();
            this.root = root;
            this.broadcast = broadcast;
            this.ctx = RollContext.physical(sides -> PolyhedralsAPI.rollLogical(sides, owner()));
        }

        Entity owner() {
            return DiceEntity.findEntity(server, ownerId);
        }

        void finish(UUID groupId, Set<ServerPlayer> viewers) {
            RollResult rolled = new RollResult(List.copyOf(results), root.evaluate(ctx));
            announce(rolled.total(), viewers);
            NeoForge.EVENT_BUS.post(new PhysicalRollGroupEvent(groupId, owner(), rolled));
        }

        void announce(int total, Set<ServerPlayer> viewers) {
            if (broadcast == BroadcastMode.NONE)
                return;
            String working = root.toString(ctx);
            String shown = working.equals(String.valueOf(total)) ? working : working + " = " + total;
            Component message = Component.literal(ownerName + " rolled: " + shown);

            if (broadcast == BroadcastMode.GLOBAL) {
                server.getPlayerList().broadcastSystemMessage(message, false);
                return;
            }
            Set<ServerPlayer> hearers = broadcast == BroadcastMode.LOCAL ? new HashSet<>(viewers) : new HashSet<>();
            ServerPlayer roller = server.getPlayerList().getPlayer(ownerId);
            if (roller != null) {
                hearers.add(roller);
            }
            hearers.forEach(player -> player.sendSystemMessage(message));
        }
    }
}
