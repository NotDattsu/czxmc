package weapons.czxmc.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import weapons.czxmc.CZXMCWeapons;

/**
 * Partícula de estela de la bala de la copper gun.
 *
 * Usa una spritesheet de 64×64 con sprites de 8×8 (8×8 = 64 sprites).
 * Cada partícula elige un sprite al azar, rota lentamente y se desvanece.
 *
 * ── Por qué necesita su propio ParticleRenderType ─────────────────────────
 *   ParticleRenderType.CUSTOM pasa null como buffer a render().
 *   Hay que definir un tipo custom que:
 *     1. Vincula la textura propia (no el atlas de partículas vanilla)
 *     2. Devuelve un BufferBuilder listo en formato PARTICLE
 *     3. Usa blending aditivo para que las partículas brillen
 */
public class GunBulletTrailParticle extends TextureSheetParticle {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CZXMCWeapons.MOD_ID, "textures/particle/gun_bullet_trail.png");

    // ── Render type con textura propia y blend aditivo ────────────────────────
    private static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TEXTURE);
            RenderSystem.enableBlend();
            // Blend aditivo: las partículas suman su color al fondo (glow)
            RenderSystem.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE
            );
            RenderSystem.depthMask(false);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() { return "GUN_BULLET_TRAIL"; }
    };

    // ── UV del sprite aleatorio ───────────────────────────────────────────────
    private static final int GRID_SIZE = 8;  // 64px / 8px por sprite = 8 celdas
    // Solo las primeras 20 celdas de la grilla tienen sprites (filas 0, 1, y 4 de la fila 2).
    // nextInt(64) causaba que ~69% de las partículas usaran celdas vacías → invisibles.
    private static final int SPRITE_COUNT = 20;
    private final float u0, v0, u1, v1;

    private final float baseSize;
    private final float spinOffset;

    protected GunBulletTrailParticle(ClientLevel level,
                                     double x, double y, double z,
                                     double xd, double yd, double zd) {
        super(level, x, y, z, xd, yd, zd);

        // Sprite al azar — solo los 20 primeros tienen contenido en la spritesheet
        int index = this.random.nextInt(SPRITE_COUNT);
        int cellX = index % GRID_SIZE;
        int cellY = index / GRID_SIZE;
        float step = 1.0f / GRID_SIZE;
        this.u0 = cellX * step;
        this.v0 = cellY * step;
        this.u1 = this.u0 + step;
        this.v1 = this.v0 + step;

        this.baseSize  = 0.09f + this.random.nextFloat() * 0.05f;
        this.spinOffset = this.random.nextFloat() * 360.0f;

        this.lifetime    = 5 + this.random.nextInt(4);
        this.quadSize    = this.baseSize;
        this.hasPhysics  = false;
        this.friction    = 0.90f;
        this.alpha       = 0.95f;
        this.rCol        = 1.0f;
        this.gCol        = 0.85f;
        this.bCol        = 0.65f;
    }

    @Override
    public ParticleRenderType getRenderType() { return RENDER_TYPE; }

    @Override
    public void tick() {
        super.tick();
        float lifeProgress = (float) this.age / (float) this.lifetime;
        float fade = 1.0f - Mth.clamp(lifeProgress, 0.0f, 1.0f);
        this.alpha     = fade * 0.90f;
        this.quadSize  = this.baseSize * (0.5f + 0.5f * fade);
    }

    /**
     * Renderiza la partícula.
     *
     * El buffer ya tiene la textura y el shader vinculados por RENDER_TYPE.begin().
     * DefaultVertexFormat.PARTICLE = POSITION(3) + UV0(2) + COLOR(4) + UV2(2)
     * Por eso hay que llamar .setLight() — UV2 es el lightmap.
     */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 camPos = camera.getPosition();

        float x = (float)(Mth.lerp(partialTick, this.xo, this.x) - camPos.x());
        float y = (float)(Mth.lerp(partialTick, this.yo, this.y) - camPos.y());
        float z = (float)(Mth.lerp(partialTick, this.zo, this.z) - camPos.z());

        float size = this.quadSize;
        float spin  = (this.age + partialTick) * 28.0f + this.spinOffset;

        // Rotar los corners: primero alinear con la cámara, luego spin propio
        Quaternionf rot = new Quaternionf(camera.rotation());
        rot.mul(Axis.ZP.rotationDegrees(spin));

        Vector3f[] corners = {
                new Vector3f(-1f, -1f, 0f),
                new Vector3f(-1f,  1f, 0f),
                new Vector3f( 1f,  1f, 0f),
                new Vector3f( 1f, -1f, 0f)
        };
        float[][] uvs = { {u0,v1}, {u0,v0}, {u1,v0}, {u1,v1} };

        int r = (int)(this.rCol * 255);
        int g = (int)(this.gCol * 255);
        int b = (int)(this.bCol * 255);
        int a = (int)(this.alpha * 255);

        // Brillo completo porque usamos blend aditivo (la luz real no importa)
        int light = 0xF000F0;

        for (int i = 0; i < 4; i++) {
            Vector3f v = new Vector3f(corners[i]);
            v.rotate(rot);
            v.mul(size);

            buffer.addVertex(x + v.x(), y + v.y(), z + v.z())
                    .setUv(uvs[i][0], uvs[i][1])
                    .setColor(r, g, b, a)
                    .setLight(light);
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new GunBulletTrailParticle(level, x, y, z, xd, yd, zd);
        }
    }
}