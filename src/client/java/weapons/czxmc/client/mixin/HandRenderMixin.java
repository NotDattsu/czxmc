package weapons.czxmc.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapons.czxmc.item.EchoGrenadeItem;

@Mixin(ItemInHandRenderer.class)
public class HandRenderMixin {

    @Unique
    private static final ResourceLocation PLAYER_TEXTURES = 
            ResourceLocation.withDefaultNamespace("textures/entity/player.png");

    @Inject(method = "renderArmWithItem", at = @At("TAIL"))
    private void onRenderArmWithItemTail(
            Player player,
            float tickDelta,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci) {
        
        if (!(item.getItem() instanceof EchoGrenadeItem)) return;
        if (player != Minecraft.getInstance().player) return;
        
        drawHand(poseStack, bufferSource, packedLight, hand == InteractionHand.OFF_HAND);
    }

    @Unique
    private void drawHand(PoseStack poseStack, MultiBufferSource bufferSource, 
                         int packedLight, boolean isLeftHand) {
        var mc = Minecraft.getInstance();
        if (mc.getEntityRenderDispatcher() == null) return;
        
        PlayerRenderer pr = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
        if (pr == null) return;
        
        PlayerModel<?> model = (PlayerModel<?>) pr.getModel();
        if (model == null) return;
        
        poseStack.pushPose();
        
        if (isLeftHand) {
            poseStack.translate(-0.55, -0.50, 0.0);
        } else {
            poseStack.translate(0.55, -0.50, 0.0);
        }
        
        var arm = isLeftHand ? model.leftArm : model.rightArm;
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entitySolid(PLAYER_TEXTURES));
        arm.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY);
        
        poseStack.popPose();
    }
}