package weapons.czxmc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import weapons.czxmc.CZXMCWeapons;
import weapons.czxmc.entity.AnchorHookEntity;
import weapons.czxmc.item.AnchorItem;

public final class AnchorSystemRenderer {

    private static volatile Vec3 hookEndWorld = null;
    private static volatile boolean isHookActive = false;

    static void setHookWorld(Vec3 pos) {
        hookEndWorld = pos;
        isHookActive = true;
    }

    static void clearHookWorld() {
        hookEndWorld = null;
        isHookActive = false;
    }

    public static Vec3 getHookEndWorld() {
        return hookEndWorld;
    }

    public static boolean isHookActive() {
        return isHookActive;
    }

    public static void renderChain(WorldRenderContext ctx) {
    }

    public static class GunItemRenderer extends GeoItemRenderer<AnchorItem> {

        public GunItemRenderer() {
            super(new GunModel());
        }

        @Override
        public void renderRecursively(PoseStack poseStack, AnchorItem animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, int color) {

            if (isReRender) {
                super.renderRecursively(poseStack, animatable, bone, renderType,
                        bufferSource, buffer, isReRender, partialTick,
                        packedLight, packedOverlay, color);
                return;
            }

            super.renderRecursively(poseStack, animatable, bone, renderType,
                    bufferSource, buffer, isReRender, partialTick,
                    packedLight, packedOverlay, color);

            if (!bone.getName().equals("end")) return;

            Matrix4f boneMatrix = poseStack.last().pose();
            Vector4f transformed = boneMatrix.transform(new Vector4f(0f, 0f, 0f, 1f));
            Vec3 pos = new Vec3(transformed.x(), transformed.y(), transformed.z());

            double dist = Math.sqrt(pos.x * pos.x + pos.y * pos.y + pos.z * pos.z);
            if (dist > 10) return;

            Minecraft mc = Minecraft.getInstance();
            Vec3 hookPos = getHookEndWorld();
            if (hookPos == null) return;

            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 camPos = camera.getPosition();
            Vec3 hookCamPos = hookPos.subtract(camPos);

            poseStack.pushPose();
            ConnectorLineRenderer.drawChainBetween(
                    poseStack, bufferSource, camPos,
                    pos.x, pos.y, pos.z,
                    hookCamPos.x, hookCamPos.y, hookCamPos.z,
                    255
            );
            poseStack.popPose();
        }

        public static class GunModel extends GeoModel<AnchorItem> {
            @Override
            public ResourceLocation getModelResource(AnchorItem animatable) {
                return ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "geo/grapple_gun.geo.json");
            }
            @Override
            public ResourceLocation getTextureResource(AnchorItem animatable) {
                return ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "textures/item/anchor_hook.png");
            }
            @Override
            public ResourceLocation getAnimationResource(AnchorItem animatable) {
                return ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "animations/anchor.animation.json");
            }
        }
    }

    public static class HookEntityRenderer extends GeoEntityRenderer<AnchorHookEntity> {

        private static final float MODEL_YAW_OFFSET = -90f;
        private static final float HOOK_OFFSET = 8.4f;

        public HookEntityRenderer(EntityRendererProvider.Context context) {
            super(context, new HookModel());
            this.shadowRadius = 0.15f;
        }

        @Override
        public void render(AnchorHookEntity entity, float entityYaw, float partialTick,
                           PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

            float interpYaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            float interpPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-interpYaw + MODEL_YAW_OFFSET));
            poseStack.mulPose(Axis.XP.rotationDegrees(interpPitch));
            super.render(entity, 0f, partialTick, poseStack, buffer, packedLight);
            poseStack.popPose();

            if (!entity.isAlive()) {
                clearHookWorld();
                return;
            }

            Vec3 hookPos = entity.position();
            float radYaw = (float)Math.toRadians(interpYaw);
            float radPitch = (float)Math.toRadians(interpPitch);

            Vec3 hookEndPos = new Vec3(
                hookPos.x + HOOK_OFFSET * Mth.cos(radPitch) * Mth.sin(-radYaw),
                hookPos.y + HOOK_OFFSET * Mth.sin(radPitch),
                hookPos.z + HOOK_OFFSET * Mth.cos(radPitch) * Mth.cos(radYaw)
            );

            setHookWorld(hookEndPos);
        }

        @Override
        public void renderRecursively(PoseStack poseStack, AnchorHookEntity animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, int color) {

            super.renderRecursively(poseStack, animatable, bone, renderType,
                    bufferSource, buffer, isReRender, partialTick,
                    packedLight, packedOverlay, color);

            if (!bone.getName().equals("end_hook") || isReRender) return;

            Matrix4f matrix = poseStack.last().pose();
            Vector4f transformed = matrix.transform(new Vector4f(0f, 0f, 0f, 1f));
            Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 worldPos = new Vec3(transformed.x() + camPos.x, transformed.y() + camPos.y, transformed.z() + camPos.z);
            setHookWorld(worldPos);
        }

        public static class HookModel extends GeoModel<AnchorHookEntity> {
            @Override
            public ResourceLocation getModelResource(AnchorHookEntity animatable) {
                return ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "geo/anchor.geo.json");
            }
            @Override
            public ResourceLocation getTextureResource(AnchorHookEntity animatable) {
                return ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "textures/item/anchor.png");
            }
            @Override
            public ResourceLocation getAnimationResource(AnchorHookEntity animatable) {
                return ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "animations/anchor_hook.animation.json");
            }
        }
    }
}