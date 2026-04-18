package weapons.czxmc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import weapons.czxmc.CZXMCWeapons;
import weapons.czxmc.item.EchoGrenadeItem;

/**
 * Renderer del EchoGrenadeItem en la mano/inventario.
 *
 * FIX — ECHO SHARD VISIBLE EN EL ITEM:
 *   Se porta el mismo sistema de renderizado del Echo Shard que usa
 *   EchoGrenadeRenderer (la entidad). El shard se renderiza interceptando
 *   el bone "All" en renderRecursively(), con buffer immediate para garantizar
 *   visibilidad a través de la geometría translúcida de la granada.
 *
 *   Sin el spring-damper de inercia (no hay entidad en movimiento),
 *   pero con la misma posición y rotación del locator del modelo.
 *
 * También incluye:
 *   - getRenderType() → entityTranslucent para la geometría semitransparente.
 *   - EmissiveLayer   → brillo pulsante con echo_grenade_emissive.png.
 *   - Pulso basado en System.currentTimeMillis() (no tickCount — el item no tiene).
 */
public class EchoGrenadeItemRenderer extends GeoItemRenderer<EchoGrenadeItem> {

    private static final ItemStack ECHO_SHARD_STACK = new ItemStack(Items.ECHO_SHARD);

    // Guardamos el bufferSource durante renderByItem para usarlo en renderRecursively
    private MultiBufferSource currentBuffer;
    private int               currentPackedLight;

    // Física del shard - smoothed con promedio móvil
    private double playerVelX = 0, playerVelY = 0, playerVelZ = 0;
    private double shardOffsetX = 0, shardOffsetY = 0, shardOffsetZ = 0;
    private boolean playerTrackingInitialized = false;
    private int lastPlayerTick = -1;

    private static final float SMOOTH_FACTOR = 0.15f;
    private static final float MAX_OFFSET = 0.008f;

    public EchoGrenadeItemRenderer() {
        super(new EchoGrenadeItemModel());
        this.addRenderLayer(new EmissiveLayer(this));
    }

    // ── Capturar buffer durante el render ─────────────────────────────────────

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context,
                             PoseStack poseStack, MultiBufferSource bufferSource,
                             int packedLight, int packedOverlay) {
        this.currentBuffer      = bufferSource;
        this.currentPackedLight = packedLight;

        // Escalar y posicionar para vista en mano (first y third person)
        boolean isThirdPerson = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                             || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean isFirstPerson = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                            || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        if (isThirdPerson) {
            // Tercera persona - más cerca de la mano
            poseStack.translate(0.0, -0.3, 0.1);
            poseStack.scale(1f, 1f, 1f);
        } else if (isFirstPerson) {
            // Primera persona - más grande y más abajo
            poseStack.translate(0.0, -0.5, 0.0);
            poseStack.scale(1.2f, 1.2f, 1.2f);
        } else if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || 
                  context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            // Skip rendering in offhand - let vanilla handle it
            this.currentBuffer = null;
            return;
        } else if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || 
                  context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            // Normal rendering in main hand
        }

        super.renderByItem(stack, context, poseStack, bufferSource, packedLight, packedOverlay);
        this.currentBuffer = null;
    }

    // ── Translucencia ─────────────────────────────────────────────────────────

    @Override
    public RenderType getRenderType(EchoGrenadeItem animatable,
                                    ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource,
                                    float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    // ── Echo Shard en el bone "All" ────────────────────────────────────────────

    @Override
    public void renderRecursively(PoseStack poseStack,
                                  EchoGrenadeItem animatable,
                                  GeoBone bone,
                                  RenderType renderType,
                                  MultiBufferSource bufferSource,
                                  VertexConsumer buffer,
                                  boolean isReRender,
                                  float partialTick,
                                  int packedLight,
                                  int packedOverlay,
                                  int color) {

        super.renderRecursively(poseStack, animatable, bone, renderType,
                bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, color);

        // Solo procesar el bone raíz "All", y solo en el pass principal (no en re-renders de layers)
        if (!bone.getName().equals("All") || isReRender) return;

        MultiBufferSource src   = currentBuffer != null ? currentBuffer : bufferSource;
        int               light = currentBuffer != null ? currentPackedLight : packedLight;

        poseStack.pushPose();

        // Re-aplicar el transform del bone "All" (pivot + rotación + escala)
        // para que el shard quede posicionado en el espacio del modelo correctamente.
        float px = bone.getPivotX() / 16f;
        float py = bone.getPivotY() / 16f;
        float pz = bone.getPivotZ() / 16f;

        poseStack.translate(px, py, pz);
        poseStack.mulPose(new Quaternionf()
                .rotateZ(bone.getRotZ())
                .rotateY(bone.getRotY())
                .rotateX(bone.getRotX()));
        poseStack.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
        poseStack.translate(-px, -py, -pz);

// Actualizar offset del shard basado en velocidad del jugador (smoothed)
        updateShardPhysics();

        // Posición del locator del shard dentro del modelo + desplazamiento
        poseStack.translate(
                0.0f / 16f + shardOffsetX,
                5.0f / 16f + shardOffsetY,
                0.0f / 16f + shardOffsetZ
        );

        // Renderizar el Echo Shard
        renderEchoShardAtLocator(poseStack, light);

        poseStack.popPose();
    }

    // ── Física del shard con smoothing (sin jitter) ───────────────────────────

    private void updateShardPhysics() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        int tick = player.tickCount;

        // Primer frame: inicializar
        if (!playerTrackingInitialized || lastPlayerTick > tick) {
            playerVelX = playerVelY = playerVelZ = 0;
            shardOffsetX = shardOffsetY = shardOffsetZ = 0;
            playerTrackingInitialized = true;
            lastPlayerTick = tick;
            return;
        }

        lastPlayerTick = tick;

        // Calcular velocidad actual del jugador
        double currentVelX = player.getX() - player.xo;
        double currentVelY = player.getY() - player.yo;
        double currentVelZ = player.getZ() - player.zo;

        // Promedio móvil exponencial para suavizar
        playerVelX = playerVelX + (currentVelX - playerVelX) * SMOOTH_FACTOR;
        playerVelY = playerVelY + (currentVelY - playerVelY) * SMOOTH_FACTOR;
        playerVelZ = playerVelZ + (currentVelZ - playerVelZ) * SMOOTH_FACTOR;

        // Mapear velocidad a offset con límites suaves
        shardOffsetX = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, playerVelX * 0.8));
        shardOffsetY = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, playerVelY * 0.6));
        shardOffsetZ = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, playerVelZ * 0.8));
    }

    /**
     * Renderiza el Echo Shard con un MultiBufferSource immediate para que se dibuje
     * DESPUÉS de que GeckoLib ya encoló la granada, evitando que el depth buffer
     * lo oculte detrás de la geometría translúcida.
     *
     * Misma lógica que EchoGrenadeRenderer.renderEchoShardAtLocator(), pero sin
     * el spring-damper de inercia (el item no tiene velocidad de entidad).
     */
    private void renderEchoShardAtLocator(PoseStack poseStack, int packedLight) {
        poseStack.pushPose();

        // Escala más grande para el echo shard
        poseStack.mulPose(Axis.YP.rotationDegrees(12f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(22f));
        poseStack.scale(0.2f, 0.2f, 0.2f);

        // Buffer immediate: se envía al GPU en cuanto llamamos endBatch(),
        // garantizando que el shard se dibuje encima de la geometría translúcida.
        MultiBufferSource.BufferSource immediateBuffer =
                MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(1536));

        Minecraft.getInstance().getItemRenderer().renderStatic(
                ECHO_SHARD_STACK,
                ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                immediateBuffer,
                Minecraft.getInstance().level,   // nivel actual del cliente
                0
        );

        immediateBuffer.endBatch();

        poseStack.popPose();
    }

// ── Glow emissivo con pulso inestable ─────────────────────────────────────

    private static final class EmissiveLayer extends GeoRenderLayer<EchoGrenadeItem> {

        private static final ResourceLocation EMISSIVE_TEX =
                ResourceLocation.fromNamespaceAndPath(
                        CZXMCWeapons.MOD_ID, "textures/item/echo_grenade_emissive.png");

        EmissiveLayer(GeoRenderer<EchoGrenadeItem> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack,
                           EchoGrenadeItem animatable,
                           BakedGeoModel bakedModel,
                           RenderType renderType,
                           MultiBufferSource bufferSource,
                           VertexConsumer buffer,
                           float partialTick,
                           int packedLight,
                           int packedOverlay) {

            float t = (System.currentTimeMillis() % 1_000_000L) / 1000f;

            float pulse = 0.70f
                    + 0.15f * (float) Math.sin(t * 2.1f)
                    + 0.08f * (float) Math.sin(t * 6.3f + 0.8f)
                    + 0.07f * (float) Math.sin(t * 14.7f + 2.3f);

            int alpha = (int) (Math.min(1f, Math.max(0f, pulse)) * 255f);
            int color = (alpha << 24) | 0x00FFFFFF;

            RenderType emissiveType = RenderType.entityTranslucentEmissive(EMISSIVE_TEX);

            getRenderer().reRender(
                    bakedModel,
                    poseStack,
                    bufferSource,
                    animatable,
                    emissiveType,
                    bufferSource.getBuffer(emissiveType),
                    partialTick,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    color
            );
        }
    }

    // ── Modelo ────────────────────────────────────────────────────────────────

    public static class EchoGrenadeItemModel extends GeoModel<EchoGrenadeItem> {

        @Override
        public ResourceLocation getModelResource(EchoGrenadeItem animatable) {
            return ResourceLocation.fromNamespaceAndPath(
                    CZXMCWeapons.MOD_ID, "geo/echo_grenade.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(EchoGrenadeItem animatable) {
            return ResourceLocation.fromNamespaceAndPath(
                    CZXMCWeapons.MOD_ID, "textures/item/echo_grenade.png");
        }

        @Override
        public ResourceLocation getAnimationResource(EchoGrenadeItem animatable) {
            return ResourceLocation.fromNamespaceAndPath(
                    CZXMCWeapons.MOD_ID, "animations/echo_grenade.animation.json");
        }
    }
}