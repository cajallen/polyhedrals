package net.geryu.polyhedrals.api;

import net.geryu.polyhedrals.DiceEntity;
import net.neoforged.bus.api.Event;

public class PhysicalDiceLandEvent extends Event {
    private final DiceEntity diceEntity;
    private final int result;

    public PhysicalDiceLandEvent(DiceEntity diceEntity, int result) {
        this.diceEntity = diceEntity;
        this.result = result;
    }

    public DiceEntity getDiceEntity() { return diceEntity; }
    public int getResult() { return result; }
}