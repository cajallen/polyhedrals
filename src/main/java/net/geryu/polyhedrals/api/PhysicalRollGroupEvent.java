package net.geryu.polyhedrals.api;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;

import java.util.UUID;

public class PhysicalRollGroupEvent extends Event {
    private final UUID groupId;
    private final Entity roller;
    private final RollResult result;

    public PhysicalRollGroupEvent(UUID groupId, Entity roller, RollResult result) {
        this.groupId = groupId;
        this.roller = roller;
        this.result = result;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public Entity getRoller() {
        return roller;
    }

    public RollResult getResult() {
        return result;
    }
}
