package weapons.czxmc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import weapons.czxmc.item.ConnectorItem;

public class ConnectorLineRenderer {

    public static final ResourceLocation CHAIN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/chain.png");

    private static final double TILE_LENGTH = 1.0;
    private static final float CHAIN_HALF_W = (3f / 16f) / 2f;

    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.level == null) return;
        if (ctx.matrixStack() == null) return;
        if (ctx.consumers() == null) return;

        ItemStack connectorStack = findConnectorInInventory(mc.player);
        if (connectorStack == null) return;

        CustomData data = connectorStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        boolean hasPos1 = tag.contains("pos1");
        boolean hasPos2 = tag.contains("pos2");

        Vec3 cam = ctx.camera().getPosition();
        MultiBufferSource buffers = ctx.consumers();

        if (hasPos1 && hasPos2) {
            BlockPos pos1 = BlockPos.of(tag.getLong("pos1"));
            BlockPos pos2 = BlockPos.of(tag.getLong("pos2"));
            double x1 = pos1.getX() + 0.5, y1 = pos1.getY() + 0.5, z1 = pos1.getZ() + 0.5;
            double x2 = pos2.getX() + 0.5, y2 = pos2.getY() + 0.5, z2 = pos2.getZ() + 0.5;
            ctx.matrixStack().pushPose();
            drawChain(mc, ctx.matrixStack(), buffers, cam, x1, y1, z1, x2, y2, z2, 255);
            ctx.matrixStack().popPose();

        } else if (hasPos1) {
            BlockPos pos1 = BlockPos.of(tag.getLong("pos1"));
            Vec3 target = getLookTarget(mc);
            double x1 = pos1.getX() + 0.5, y1 = pos1.getY() + 0.5, z1 = pos1.getZ() + 0.5;
            ctx.matrixStack().pushPose();
            drawChain(mc, ctx.matrixStack(), buffers, cam, x1, y1, z1, target.x, target.y, target.z, 160);
            ctx.matrixStack().popPose();
        }
    }

    /**
     * Dibuja un tramo de cadena entre dos puntos mundo.
     * Wrapper público para uso desde AnchorSystemRenderer u otros renderers.
     *
     * @param ps       PoseStack del WorldRenderContext (sin transforms adicionales)
     * @param buffers  MultiBufferSource del WorldRenderContext
     * @param cam      Posición de la cámara en coordenadas mundo
     * @param x1,y1,z1 Primer extremo en coordenadas mundo
     * @param x2,y2,z2 Segundo extremo en coordenadas mundo
     * @param alpha    Opacidad (0-255)
     */
    public static void drawChainBetween(PoseStack ps, MultiBufferSource buffers,
                                        Vec3 cam,
                                        double x1, double y1, double z1,
                                        double x2, double y2, double z2,
                                        int alpha) {
        drawChain(Minecraft.getInstance(), ps, buffers, cam, x1, y1, z1, x2, y2, z2, alpha);
    }

    private static void drawChain(Minecraft mc, PoseStack ps, MultiBufferSource buffers,
                                  Vec3 cam,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  int alpha) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001) return;

        double lx = dx / length, ly = dy / length, lz = dz / length;

        double ux = 0, uy = 1, uz = 0;
        if (Math.abs(lx * ux + ly * uy + lz * uz) > 0.95) { ux = 1; uy = 0; uz = 0; }
        double px = ly * uz - lz * uy, py = lz * ux - lx * uz, pz = lx * uy - ly * ux;
        double pl = Math.sqrt(px * px + py * py + pz * pz);
        if (pl < 0.001) return;
        px /= pl; py /= pl; pz /= pl;

        double rx = ly * pz - lz * py, ry = lz * px - lx * pz, rz = lx * py - ly * px;
        double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl < 0.001) return;
        rx /= rl; ry /= rl; rz /= rl;

        float hw  = CHAIN_HALF_W;
        float pxf = (float)(px * hw), pyf = (float)(py * hw), pzf = (float)(pz * hw);
        float rxf = (float)(rx * hw), ryf = (float)(ry * hw), rzf = (float)(rz * hw);

        float UA_MIN = 0f / 16f, UA_MAX = 3f / 16f;
        float UB_MIN = 3f / 16f, UB_MAX = 6f / 16f;

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentCull(CHAIN_TEXTURE));
        Matrix4f mat        = ps.last().pose();
        PoseStack.Pose pose = ps.last();

        int    numFullTiles  = (int)(length / TILE_LENGTH);
        double remainder     = length - numFullTiles * TILE_LENGTH;
        int    totalSegments = numFullTiles + (remainder > 0.001 ? 1 : 0);

        for (int i = 0; i < totalSegments; i++) {
            double dStart = i * TILE_LENGTH;
            double dEnd   = Math.min((i + 1) * TILE_LENGTH, length);
            double segLen = dEnd - dStart;

            double t0 = dStart / length;
            double t1 = dEnd   / length;

            double wx0  = x2 - dx * t0, wy0  = y2 - dy * t0, wz0  = z2 - dz * t0;
            double wx1s = x2 - dx * t1, wy1s = y2 - dy * t1, wz1s = z2 - dz * t1;

            float cx0 = (float)(wx0  - cam.x), cy0 = (float)(wy0  - cam.y), cz0 = (float)(wz0  - cam.z);
            float cx1 = (float)(wx1s - cam.x), cy1 = (float)(wy1s - cam.y), cz1 = (float)(wz1s - cam.z);

            float vNear = 0f;
            float vFar  = (float)(segLen / TILE_LENGTH);

            double midX = (wx0 + wx1s) * 0.5, midY = (wy0 + wy1s) * 0.5, midZ = (wz0 + wz1s) * 0.5;
            int lightPacked = sampleLightmap(mc, midX, midY, midZ);

            addQuad(consumer, mat, pose, alpha, lightPacked,
                    cx0 - pxf, cy0 - pyf, cz0 - pzf,  UA_MIN, vNear,
                    cx0 + pxf, cy0 + pyf, cz0 + pzf,  UA_MAX, vNear,
                    cx1 + pxf, cy1 + pyf, cz1 + pzf,  UA_MAX, vFar,
                    cx1 - pxf, cy1 - pyf, cz1 - pzf,  UA_MIN, vFar);

            addQuad(consumer, mat, pose, alpha, lightPacked,
                    cx0 - rxf, cy0 - ryf, cz0 - rzf,  UB_MIN, vNear,
                    cx0 + rxf, cy0 + ryf, cz0 + rzf,  UB_MAX, vNear,
                    cx1 + rxf, cy1 + ryf, cz1 + rzf,  UB_MAX, vFar,
                    cx1 - rxf, cy1 - ryf, cz1 - rzf,  UB_MIN, vFar);
        }
    }

    /**
     * Samplea el lightmap evitando posiciones dentro de bloques sólidos
     * (como el bloque ancla) que devolverían luz 0.
     */
    private static int sampleLightmap(Minecraft mc, double x, double y, double z) {
        if (mc.level == null) return LightTexture.FULL_BRIGHT;
        BlockPos pos = BlockPos.containing(x, y, z);
        int attempts = 0;
        while (attempts < 4 && mc.level.getBlockState(pos).canOcclude()) {
            pos = pos.below();
            attempts++;
        }
        int block = mc.level.getBrightness(LightLayer.BLOCK, pos);
        int sky   = mc.level.getBrightness(LightLayer.SKY,   pos);
        return LightTexture.pack(block, sky);
    }

    private static void addQuad(VertexConsumer buf, Matrix4f mat, PoseStack.Pose pose, int alpha, int light,
                                float x1, float y1, float z1, float u1, float v1,
                                float x2, float y2, float z2, float u2, float v2,
                                float x3, float y3, float z3, float u3, float v3,
                                float x4, float y4, float z4, float u4, float v4) {
        addVtx(buf, mat, pose, alpha, light, x1, y1, z1, u1, v1);
        addVtx(buf, mat, pose, alpha, light, x2, y2, z2, u2, v2);
        addVtx(buf, mat, pose, alpha, light, x3, y3, z3, u3, v3);
        addVtx(buf, mat, pose, alpha, light, x4, y4, z4, u4, v4);
        // Cara trasera
        addVtx(buf, mat, pose, alpha, light, x4, y4, z4, u4, v4);
        addVtx(buf, mat, pose, alpha, light, x3, y3, z3, u3, v3);
        addVtx(buf, mat, pose, alpha, light, x2, y2, z2, u2, v2);
        addVtx(buf, mat, pose, alpha, light, x1, y1, z1, u1, v1);
    }

    private static void addVtx(VertexConsumer buf, Matrix4f mat, PoseStack.Pose pose, int alpha, int light,
                               float x, float y, float z, float u, float v) {
        // Normal forzada a (0,1,0) → factor 1.0 en el shader de entidades,
        // equivalente a "shade: false" de los bloques de cadena vanilla.
        // Sin esto las caras laterales recibirían un multiplicador ~0.6-0.8,
        // oscureciendo la cadena respecto a la vanilla.
        buf.addVertex(mat, x, y, z)
           .setColor(255, 255, 255, alpha)
           .setUv(u, v)
           .setOverlay(OverlayTexture.NO_OVERLAY)
           .setUv2(light & 0xFFFF, (light >> 16) & 0xFFFF)
           .setNormal(pose, 0f, 1f, 0f);
    }

    private static ItemStack findConnectorInInventory(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isConnectorWithData(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (isConnectorWithData(off)) return off;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isConnectorWithData(stack)) return stack;
        }
        if (main.getItem() instanceof ConnectorItem) return main;
        if (off.getItem() instanceof ConnectorItem)  return off;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof ConnectorItem) return stack;
        }
        return null;
    }

    private static boolean isConnectorWithData(ItemStack stack) {
        if (!(stack.getItem() instanceof ConnectorItem)) return false;
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        return tag.contains("pos1") || tag.contains("pos2");
    }

    private static Vec3 getLookTarget(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos bp = ((BlockHitResult) hit).getBlockPos();
            return new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
        }
        Vec3 eye  = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getLookAngle();
        return eye.add(look.scale(8.0));
    }
}
