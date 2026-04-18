package weapons.czxmc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import weapons.czxmc.entity.GrenadeEntity;

/**
 * Renderer debug de la granada.
 *
 * Usa {@link GrenadeEntity#getClientSpinX(float)} etc. para la rotación —
 * valores acumulados localmente en el cliente cada client-tick, con su propio
 * prev/curr. Esto evita el snap que ocurría con PREV_SPIN/SPIN del servidor:
 * esos valores solo cambian en server-ticks, así que al resetearse partialTick
 * a 0 en cada client-tick el lerp rebobinaba y la rotación se veía trabada.
 */
public class GrenadeDebugRenderer extends EntityRenderer<GrenadeEntity> {

    private final ItemRenderer itemRenderer;

    public GrenadeDebugRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.36f;
    }

    @Override
    public void render(GrenadeEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        // Usar rotación acumulada localmente en el cliente (getClientSpin*).
        // NO usar getSpinX(partialTick) — ese interpola entre PREV_SPIN/SPIN del servidor,
        // que solo cambian una vez por server-tick. Al resetearse partialTick cada client-tick,
        // el lerp rebobina y produce el snap/trabado visible.
        float rx = entity.getClientSpinX(partialTick);
        float ry = entity.getClientSpinY(partialTick);
        float rz = entity.getClientSpinZ(partialTick);

        // Un poco más grande visualmente para que no se vea chica frente a la hitbox.
        poseStack.scale(1.10f, 1.10f, 1.10f);

        // Centrar el pivote para que rote desde el centro, no desde una esquina.
        poseStack.translate(0.0, 0.22, 0.0);

        Quaternionf quat = new Quaternionf()
            .rotateX((float) Math.toRadians(rx))
            .rotateY((float) Math.toRadians(ry))
            .rotateZ((float) Math.toRadians(rz));

        poseStack.mulPose(quat);
        poseStack.translate(0.0, -0.22, 0.0);

        ItemStack debugStack = new ItemStack(Items.TNT);
        itemRenderer.renderStatic(
            debugStack,
            ItemDisplayContext.GROUND,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            buffer,
            entity.level(),
            entity.getId()
        );

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(GrenadeEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper.png");
    }
}
