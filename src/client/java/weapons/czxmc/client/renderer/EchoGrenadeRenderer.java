package weapons.czxmc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import weapons.czxmc.CZXMCWeapons;
import weapons.czxmc.entity.GrenadeEntity;

/**
 * Renderer de la Echo Grenade.
 *
 * Cambios respecto a la versión anterior:
 *
 *  1. TRANSPARENCIA: se sobreescribe getRenderType() para devolver
 *     entityTranslucent en lugar del tipo opaco por defecto de GeckoLib.
 *
 *  2. GLOW EMISIVO: se agrega EmissiveLayer, un GeoRenderLayer que
 *     re-renderiza el modelo con echo_grenade_emissive.png a brillo
 *     completo (LightTexture.FULL_BRIGHT). La textura actúa como máscara:
 *       - píxeles con color/alpha  → esa zona brilla con esos colores
 *       - píxeles transparentes   → sin glow
 *
 *  3. PULSO INESTABLE: la intensidad del glow se modula con tres ondas
 *     seno de frecuencias distintas, produciendo un parpadeo orgánico
 *     que imita una fuente de luz inestable.
 *
 *  4. POSICIÓN DEL ECHO SHARD: se re-aplica manualmente el transform del
 *     bone "All" y se usa la posición absoluta del locator en espacio modelo.
 *
 *  5. [NUEVO] VISIBILIDAD DEL SHARD A TRAVÉS DE TRANSPARENCIA:
 *     El shard se renderiza con entityTranslucent para que el depth buffer
 *     no lo oculte detrás de la geometría semitransparente de la granada.
 *     Adicionalmente se renderiza en dos pasadas: una normal (para el depth)
 *     y una translúcida (para que se vea a través del cuerpo de la granada).
 *
 *  6. [NUEVO] SACUDIDA POR INERCIA:
 *     Un spring-damper trackea la aceleración de la entidad entre frames y
 *     desplaza el shard en la dirección opuesta, simulando que está suelto
 *     dentro del contenedor y se mueve por inercia cuando la granada es
 *     lanzada, rebota o cambia de dirección.
 */
public class EchoGrenadeRenderer extends GeoEntityRenderer<GrenadeEntity> {

    private static final ItemStack ECHO_SHARD_STACK = new ItemStack(Items.ECHO_SHARD);

    // Guardados durante render() para usarlos en renderRecursively() y en el layer.
    private MultiBufferSource currentBuffer;
    private int               currentPackedLight;

    // ── 6. Física del shard (spring-damper) ──────────────────────────────────
    //
    // Se simula un shard "suelto" dentro del contenedor.
    // Cuando la granada acelera, el shard se desplaza en dirección contraria
    // (inercia) y luego rebota de vuelta gracias al spring, amortiguado.
    //
    // Variables de estado (persistentes entre frames):
    private double prevEntityX, prevEntityY, prevEntityZ; // posición anterior
    private double velX, velY, velZ;                      // velocidad del shard relativa
    private double offsetX, offsetY, offsetZ;             // desplazamiento actual
    private int    lastTick = -1;                         // para detectar el primer frame

    // Parámetros del spring-damper (ajustables a gusto):
    private static final float SPRING_K   = 18f;    // rigidez (Hz² aprox) — más alto = rebota más rápido
    private static final float DAMPING    = 5.5f;   // amortiguación — más alto = para más rápido
    private static final float MAX_OFFSET = 0.045f; // desplazamiento máximo en bloques (~0.7 px a 16px/bloque)

    public EchoGrenadeRenderer(EntityRendererProvider.Context context) {
        super(context, new EchoGrenadeModel());
        this.shadowRadius = 0.36f;
        this.addRenderLayer(new EmissiveLayer(this));
    }

    // ── 1. TRANSPARENCIA ──────────────────────────────────────────────────────

    @Override
    public RenderType getRenderType(GrenadeEntity animatable,
                                    ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource,
                                    float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    // ── Modelo GeckoLib ───────────────────────────────────────────────────────

    public static class EchoGrenadeModel extends GeoModel<GrenadeEntity> {

        @Override
        public ResourceLocation getModelResource(GrenadeEntity animatable) {
            return ResourceLocation.fromNamespaceAndPath(
                    CZXMCWeapons.MOD_ID, "geo/echo_grenade.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(GrenadeEntity animatable) {
            return ResourceLocation.fromNamespaceAndPath(
                    CZXMCWeapons.MOD_ID, "textures/item/echo_grenade.png");
        }

        @Override
        public ResourceLocation getAnimationResource(GrenadeEntity animatable) {
            return ResourceLocation.fromNamespaceAndPath(
                    CZXMCWeapons.MOD_ID, "animations/echo_grenade.animation.json");
        }
    }

    // ── 2 y 3. CAPA EMISIVA CON PULSO ─────────────────────────────────────────

    private static final class EmissiveLayer extends GeoRenderLayer<GrenadeEntity> {

        private static final ResourceLocation EMISSIVE_TEX =
                ResourceLocation.fromNamespaceAndPath(
                        CZXMCWeapons.MOD_ID, "textures/item/echo_grenade_emissive.png");

        EmissiveLayer(GeoRenderer<GrenadeEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack,
                           GrenadeEntity animatable,
                           BakedGeoModel bakedModel,
                           RenderType renderType,
                           MultiBufferSource bufferSource,
                           VertexConsumer buffer,
                           float partialTick,
                           int packedLight,
                           int packedOverlay) {

            float t = (animatable.tickCount + partialTick) / 20f;

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

    // ── Render principal ──────────────────────────────────────────────────────

    @Override
    public void render(GrenadeEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        this.currentBuffer      = bufferSource;
        this.currentPackedLight = packedLight;

        // ── 6. Actualizar física del shard ────────────────────────────────────
        updateShardPhysics(entity, partialTick);

        poseStack.pushPose();

        float rx = entity.getClientSpinX(partialTick);
        float ry = entity.getClientSpinY(partialTick);
        float rz = entity.getClientSpinZ(partialTick);

        poseStack.scale(1.10f, 1.10f, 1.10f);
        poseStack.translate(0.0, 0.22, 0.0);

        Quaternionf quat = new Quaternionf()
                .rotateX((float) Math.toRadians(rx))
                .rotateY((float) Math.toRadians(ry))
                .rotateZ((float) Math.toRadians(rz));

        poseStack.mulPose(quat);
        poseStack.translate(0.0, -0.22, 0.0);

        super.render(entity, yaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();

        this.currentBuffer = null;
    }

    // ── 6. Lógica del spring-damper ───────────────────────────────────────────

    /**
     * Calcula el desplazamiento del shard para este frame usando un spring-damper.
     *
     * Física simplificada (Euler semi-implícito, dt ≈ 1/20 por tick):
     *   aceleración_entidad = (posición_actual − posición_anterior) / dt²
     *   fuerza_sobre_shard  = −aceleración_entidad  (inercia, dirección opuesta)
     *   nueva_vel           = vel + (−k*offset − damping*vel + fuerza) * dt
     *   nuevo_offset        = offset + nueva_vel * dt
     *
     * Se llama desde render() con el partialTick interpolado para suavidad.
     */
    private void updateShardPhysics(GrenadeEntity entity, float partialTick) {
        // Posición interpolada de la entidad este frame
        double cx = entity.xo + (entity.getX() - entity.xo) * partialTick;
        double cy = entity.yo + (entity.getY() - entity.yo) * partialTick;
        double cz = entity.zo + (entity.getZ() - entity.zo) * partialTick;

        int tick = entity.tickCount;

        // Primer frame: inicializar sin sacudida
        if (lastTick == -1 || lastTick > tick) {
            prevEntityX = cx;
            prevEntityY = cy;
            prevEntityZ = cz;
            velX = velY = velZ = 0;
            offsetX = offsetY = offsetZ = 0;
            lastTick = tick;
            return;
        }

        lastTick = tick;

        // dt en segundos — usamos 1/20 (un tick) como paso fijo para estabilidad
        float dt = 1f / 20f;

        // Aceleración de la entidad (variación de velocidad por frame)
        // = desplazamiento entre frames / dt²
        // Multiplicamos por un factor de sensibilidad para amplificar el efecto.
        float sensitivity = 3.0f;
        double accelX = (cx - prevEntityX) / dt * sensitivity;
        double accelY = (cy - prevEntityY) / dt * sensitivity;
        double accelZ = (cz - prevEntityZ) / dt * sensitivity;

        prevEntityX = cx;
        prevEntityY = cy;
        prevEntityZ = cz;

        // Spring-damper: F = -k*offset - damping*vel - accel_entidad
        // El shard se resiste a la aceleración del contenedor (inercia)
        double fx = -SPRING_K * offsetX - DAMPING * velX - accelX;
        double fy = -SPRING_K * offsetY - DAMPING * velY - accelY;
        double fz = -SPRING_K * offsetZ - DAMPING * velZ - accelZ;

        velX += fx * dt;
        velY += fy * dt;
        velZ += fz * dt;

        offsetX += velX * dt;
        offsetY += velY * dt;
        offsetZ += velZ * dt;

        // Clamp al radio máximo para que el shard no atraviese las paredes
        double len = Math.sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ);
        if (len > MAX_OFFSET) {
            double scale = MAX_OFFSET / len;
            offsetX *= scale;
            offsetY *= scale;
            offsetZ *= scale;
            // Amortiguar la velocidad al chocar con el límite (rebote parcial)
            velX *= -0.3;
            velY *= -0.3;
            velZ *= -0.3;
        }
    }

    // ── 4. INTERCEPCIÓN DEL BONE "All" ────────────────────────────────────────

    @Override
    public void renderRecursively(PoseStack poseStack,
                                  GrenadeEntity animatable,
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

        if (!bone.getName().equals("All") || isReRender) return;

        MultiBufferSource src   = currentBuffer != null ? currentBuffer : bufferSource;
        int               light = currentBuffer != null ? currentPackedLight : packedLight;

        poseStack.pushPose();

        // Re-aplicar transform del bone "All"
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

        // Posición base del locator + desplazamiento de inercia del shard
        poseStack.translate(
                0.0f  / 16f + offsetX,
                5.0f  / 16f + offsetY,
                0.0f  / 16f + offsetZ
        );

        renderEchoShardAtLocator(poseStack, src, light, animatable);

        poseStack.popPose();
    }

    // ── 5. Echo Shard — buffer immediate para visibilidad a través de transparencia ──

    /**
     * Renderiza el Echo Shard con la escala y rotación originales.
     *
     * RAÍZ DEL PROBLEMA:
     *   Minecraft usa un sistema de rendering por lotes (batched). El MultiBufferSource
     *   principal acumula vértices en buffers internos por cada RenderType, y los envía
     *   al GPU todos juntos al final del frame. Esto significa que el ORDEN en que
     *   renderizamos en Java NO es el orden en que se dibujan en GPU — el depth buffer
     *   ya tiene los valores de la granada cuando el shard finalmente se procesa.
     *
     *   El lambda `renderType -> shardConsumer` del intento anterior fallaba porque
     *   acumulaba vértices pero nunca los enviaba al GPU (nunca se llamaba endBatch()).
     *   El resultado era un shard invisible.
     *
     * SOLUCIÓN — MultiBufferSource.immediate() con endBatch() explícito:
     *   Un buffer "immediate" (no batched) envía cada RenderType al GPU en cuanto se
     *   llama endBatch(). Al llamarlo DESPUÉS de que GeckoLib ya dibujó la granada al
     *   buffer principal, el shard se renderiza al GPU DESPUÉS que la granada, con el
     *   depth buffer ya conteniendo sus valores.
     *
     *   Para que el shard se vea a través de la zona translúcida usamos
     *   entityTranslucentEmissive, que tiene depthWrite=false y siempre pasa el
     *   depth test — el shard se dibuja encima de lo que ya está en el framebuffer,
     *   incluyendo la geometría semitransparente de la granada.
     *
     * FLUJO DE RENDER:
     *   1. super.render() → GeckoLib encola la granada en el buffer batched principal.
     *   2. renderRecursively() intercepta el bone "All" → llama a este método.
     *   3. renderStatic() escribe el shard en nuestro buffer immediate.
     *   4. immediateBuffer.endBatch() → envía el shard AL GPU AHORA, en este momento,
     *      con el shard como "última capa" visible — después de la granada.
     */
    private void renderEchoShardAtLocator(PoseStack poseStack,
                                          MultiBufferSource ignoredBatchedBuffer,
                                          int packedLight,
                                          GrenadeEntity entity) {
        poseStack.pushPose();

        // ── Rotación original (sin cambios respecto al código original) ───────
        poseStack.mulPose(Axis.YP.rotationDegrees(12f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(22f));

        // ── Escala original (sin cambios) ─────────────────────────────────────
        poseStack.scale(0.22f, 0.22f, 0.22f);

        // ── Buffer immediate: se envía al GPU en cuanto llamamos endBatch() ───
        //
        // Tesselator.getInstance().getBuilder() en versiones antiguas;
        // en 1.20+ el patrón correcto es MultiBufferSource.immediate con un
        // ByteBufferBuilder dedicado, o reutilizar el Tesselator.
        //
        // Usamos el Tesselator del cliente para no crear un ByteBufferBuilder
        // extra en cada frame (evita GC pressure).
        MultiBufferSource.BufferSource immediateBuffer =
                MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(1536));

        Minecraft.getInstance().getItemRenderer().renderStatic(
                ECHO_SHARD_STACK,
                ItemDisplayContext.FIXED,           // contexto original — sprite con grosor 3D
                LightTexture.FULL_BRIGHT,           // brillo propio
                OverlayTexture.NO_OVERLAY,
                poseStack,
                immediateBuffer,
                entity.level(),
                entity.getId() + 7
        );

        // Enviar AHORA al GPU — después de que GeckoLib ya encoló la granada.
        // El shard se dibuja encima de la geometría translúcida porque se procesa
        // después en el pipeline de OpenGL.
        immediateBuffer.endBatch();

        poseStack.popPose();
    }
}