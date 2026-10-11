package net.geryu.polyhedrals.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.geryu.polyhedrals.DiceEntity;
import net.geryu.polyhedrals.physics.DiceShapes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3d;

public class DiceRenderer extends EntityRenderer<DiceEntity> {
    private final ItemRenderer itemRenderer;

    public DiceRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.12F;
    }

    @Override
    public void render(DiceEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        poseStack.mulPose(entity.getRenderOrientation(partialTick));
        float scale = (float) DiceShapes.WORLD_SCALE;
        poseStack.scale(scale, scale, scale);

        Vector3d offset = DiceShapes.forSides(entity.getSides()).renderTranslation();
        poseStack.translate(offset.x, offset.y, offset.z);

        itemRenderer.renderStatic(
                entity.getItem(),
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId());

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DiceEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
