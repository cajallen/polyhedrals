package net.geryu.polyhedrals;

import com.mojang.serialization.MapCodec;
import net.geryu.polyhedrals.api.BroadcastMode;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.conditions.ICondition;

public final class PolyhedralsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RECIPES_ENABLED = BUILDER
            .comment("Whether dice can be crafted. Takes effect on /reload.")
            .define("recipes_enabled", true);

    public static final ModConfigSpec.EnumValue<BroadcastMode> BROADCAST = BUILDER
            .comment("Who sees a roll's result in chat: SELF, LOCAL, or GLOBAL.")
            .defineEnum("broadcast", BroadcastMode.LOCAL, BroadcastMode.SELF, BroadcastMode.LOCAL, BroadcastMode.GLOBAL);

    public static final ModConfigSpec.BooleanValue WAIT_FOR_DICE = BUILDER
            .comment("Whether /polyhedrals roll refuses while the player still has dice rolling.")
            .define("wait_for_dice", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private PolyhedralsConfig() {}

    record RecipesEnabled() implements ICondition {
        static final MapCodec<RecipesEnabled> CODEC = MapCodec.unit(new RecipesEnabled());

        @Override
        public boolean test(IContext context) {
            return RECIPES_ENABLED.get();
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }
}
