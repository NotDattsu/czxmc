package weapons.czxmc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import weapons.czxmc.CZXMCWeapons;
import weapons.czxmc.entity.GunBulletEntity;

/**
 * Renderer de la bala de la copper gun.
 *
 * Renderiza los dos cubos concéntricos del modelo dust.json con UVs por cara
 * idénticas a las definidas en el JSON (coordenadas Bedrock convertidas a
 * espacio normalizado 0-1 dividiendo por 8, que es el tamaño de la grilla
 * del UV-layout de dust.png de 64×64).
 *
 * Conversión de UVs Bedrock → normalizadas: valor_bedrock / 8.0f
 *
 * Layout de la textura (64×64 px, grilla de 8×8 "píxeles Bedrock"):
 *   v 0.00 - 0.50 → datos del cubo interior  (4 filas superiores)
 *   v 0.50 - 1.00 → datos del cubo exterior  (4 filas inferiores)
 */
public class GunBulletRenderer extends EntityRenderer<GunBulletEntity> {

    private static final ResourceLocation DUST_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CZXMCWeapons.MOD_ID, "textures/item/dust.png");

    private static final float SPIN_DEG_PER_TICK = 18.0f;

    // ── UVs normalizadas del cubo exterior (filas v 0.75-1.0) ────────────────
    // Bedrock per-face [u1,v1,u2,v2] / 8.0f
    private static final float[][] UV_OUTER = {
            // north [2,6,4,8]
            {0.25f, 0.75f, 0.50f, 1.00f},
            // east  [0,6,2,8]
            {0.00f, 0.75f, 0.25f, 1.00f},
            // south [6,6,8,8]
            {0.75f, 0.75f, 1.00f, 1.00f},
            // west  [4,6,6,8]
            {0.50f, 0.75f, 0.75f, 1.00f},
            // up    [4,6,2,4] — u invertida, v invertida
            {0.50f, 0.75f, 0.25f, 0.50f},
            // down  [6,4,4,6] — u invertida, v invertida
            {0.75f, 0.50f, 0.50f, 0.75f},
    };

    // ── UVs normalizadas del cubo interior (filas v 0.0-0.5) ─────────────────
    private static final float[][] UV_INNER = {
            // north [2,2,4,4]
            {0.25f, 0.25f, 0.50f, 0.50f},
            // east  [0,2,2,4]
            {0.00f, 0.25f, 0.25f, 0.50f},
            // south [6,2,8,4]
            {0.75f, 0.25f, 1.00f, 0.50f},
            // west  [4,2,6,4]
            {0.50f, 0.25f, 0.75f, 0.50f},
            // up    [4,2,2,0] — u invertida, v invertida
            {0.50f, 0.25f, 0.25f, 0.00f},
            // down  [6,0,4,2] — u invertida
            {0.75f, 0.00f, 0.50f, 0.25f},
    };

    public GunBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(GunBulletEntity entity) {
        return DUST_TEXTURE;
    }

    @Override
    public void render(GunBulletEntity entity, float entityYaw, float partialTick,
                       PoseStack ps, MultiBufferSource buf, int packedLight) {
        ps.pushPose();

        float time = entity.tickCount + partialTick;
        ps.mulPose(Axis.YP.rotationDegrees(time * SPIN_DEG_PER_TICK));
        ps.mulPose(Axis.XP.rotationDegrees(time * SPIN_DEG_PER_TICK * 0.67f));

        // Escala: px Bedrock → bloques (1/16)
        float S = 1f / 16f;
        ps.scale(S, S, S);

        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentCull(DUST_TEXTURE));
        PoseStack.Pose pose = ps.last();

        // Cubo exterior: half-extent 1.2 (dust.json from -1.2 to +1.2)
        renderCubePerFace(pose, vc, packedLight, 1.2f, UV_OUTER);

        // Cubo interior: half-extent 1.1 (dust.json from -1.1 to +1.1)
        renderCubePerFace(pose, vc, packedLight, 1.1f, UV_INNER);

        ps.popPose();
        super.render(entity, entityYaw, partialTick, ps, buf, packedLight);
    }

    /**
     * Renderiza un cubo con UVs por cara que coinciden con las del modelo dust.json.
     *
     * @param h     semilado del cubo en px Bedrock
     * @param uvs   array de 6 entradas [north, east, south, west, up, down],
     *              cada una con [u1, v1, u2, v2] normalizadas 0-1.
     *              Si u1 > u2 o v1 > v2, la cara se renderiza espejada.
     */
    private static void renderCubePerFace(PoseStack.Pose pose, VertexConsumer vc, int light,
                                          float h, float[][] uvs) {
        float[] n = uvs[0], e = uvs[1], s = uvs[2], w = uvs[3], u = uvs[4], d = uvs[5];

        // North (z=-h)  — quad: TL, BL, BR, TR
        quad(pose, vc, light,  -h,  h, -h,   -h, -h, -h,    h, -h, -h,    h,  h, -h,
                n[0],n[1], n[0],n[3], n[2],n[3], n[2],n[1],   0, 0, -1);
        // South (z=+h)
        quad(pose, vc, light,   h,  h,  h,    h, -h,  h,   -h, -h,  h,   -h,  h,  h,
                s[0],s[1], s[0],s[3], s[2],s[3], s[2],s[1],   0, 0,  1);
        // East  (x=+h)
        quad(pose, vc, light,   h,  h, -h,    h, -h, -h,    h, -h,  h,    h,  h,  h,
                e[0],e[1], e[0],e[3], e[2],e[3], e[2],e[1],   1, 0,  0);
        // West  (x=-h)
        quad(pose, vc, light,  -h,  h,  h,   -h, -h,  h,   -h, -h, -h,   -h,  h, -h,
                w[0],w[1], w[0],w[3], w[2],w[3], w[2],w[1],  -1, 0,  0);
        // Up    (y=+h)
        quad(pose, vc, light,  -h,  h,  h,   -h,  h, -h,    h,  h, -h,    h,  h,  h,
                u[0],u[1], u[0],u[3], u[2],u[3], u[2],u[1],   0, 1,  0);
        // Down  (y=-h)
        quad(pose, vc, light,  -h, -h, -h,   -h, -h,  h,    h, -h,  h,    h, -h, -h,
                d[0],d[1], d[0],d[3], d[2],d[3], d[2],d[1],   0,-1,  0);
    }

    /** Emite 4 vértices con sus UVs individuales. */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int light,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float u0, float v0,
                             float u1, float v1,
                             float u2, float v2,
                             float u3, float v3,
                             float nx, float ny, float nz) {
        vtx(pose, vc, light, x0, y0, z0, u0, v0, nx, ny, nz);
        vtx(pose, vc, light, x1, y1, z1, u1, v1, nx, ny, nz);
        vtx(pose, vc, light, x2, y2, z2, u2, v2, nx, ny, nz);
        vtx(pose, vc, light, x3, y3, z3, u3, v3, nx, ny, nz);
    }

    private static void vtx(PoseStack.Pose pose, VertexConsumer vc, int light,
                            float x, float y, float z, float u, float v,
                            float nx, float ny, float nz) {
        vc.addVertex(pose.pose(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}