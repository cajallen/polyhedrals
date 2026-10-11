package net.geryu.polyhedrals.api;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Random;

public final class PolyhedralsAPI {
    static final Random RANDOM = new Random();

    private PolyhedralsAPI() {}

    public static RollBuilder newRoll(String expression) {
        return new RollBuilder(expression);
    }

    public static int rollLogical(int sides, Entity roller) {
        return NeoForge.EVENT_BUS.post(new DiceRollEvent(roller, sides, RANDOM.nextInt(sides) + 1)).getTotal();
    }
}
