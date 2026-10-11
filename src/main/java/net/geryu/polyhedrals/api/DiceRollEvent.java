package net.geryu.polyhedrals.api;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;

public class DiceRollEvent extends Event {
    private final Entity roller;
    private final int sides;
    private final int naturalRoll;
    private int modifier = 0;

    public DiceRollEvent(Entity roller, int sides, int naturalRoll) {
        this.roller = roller;
        this.sides = sides;
        this.naturalRoll = naturalRoll;
    }

    public Entity getRoller() { return roller; }
    public int getSides() { return sides; }
    public int getNaturalRoll() { return naturalRoll; }
    public void addModifier(int modifier) { this.modifier += modifier; }
    public int getTotal() { return naturalRoll + modifier; }
}
