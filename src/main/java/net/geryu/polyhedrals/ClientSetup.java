package net.geryu.polyhedrals;

import net.geryu.polyhedrals.client.DiceRenderer;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;

@EventBusSubscriber(modid = Polyhedrals.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Polyhedrals.DICE_ENTITY.get(), DiceRenderer::new);
    }

    // Tint index from the model materials. 0: faces, 1: detail
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            DiceColors colors = stack.getOrDefault(Polyhedrals.TWO_TONE_COLORS.get(), DiceColors.DEFAULT);
            return 0xFF000000 | (tintIndex == 0 ? colors.faceColor() : colors.detailColor());
        }, Polyhedrals.DICE.values().stream().map(DeferredItem::get).toArray(Item[]::new));
    }
}
