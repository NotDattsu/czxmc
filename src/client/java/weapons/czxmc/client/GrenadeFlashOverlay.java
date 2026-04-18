package weapons.czxmc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import weapons.czxmc.client.sound.FadingEchoExplosionSoundInstance;
import weapons.czxmc.sound.ModSounds;

/**
 * Overlay de flash de pantalla para la explosión de la Echo Grenade.
 *
 * Usa ticks del juego en lugar de tiempo real para que el efecto
 * se pause correctamente cuando el juego se pausa.
 */
public class GrenadeFlashOverlay {

    /** Duración de la fase hold en ticks (40 ticks = 2 segundos). */
    private static final int HOLD_TICKS  = 40;

    /** Duración total del efecto en ticks (80 ticks = 4 segundos). */
    private static final int TOTAL_TICKS = 80;

    /**
     * Tick del juego cuando se activó el flash.
     * -1 indica que no hay flash activo.
     */
    private static int flashStartTick = -1;

// ── API pública ───────────────────────────────────────────────────────────

    /**
     * @return true si hay un flash activo (útil para suprimir el sonido "before").
     */
    public static boolean isActive() {
        if (flashStartTick < 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        int elapsed = mc.player.tickCount - flashStartTick;
        return elapsed < TOTAL_TICKS;
    }

    /**
     * Activa el flash visual y reproduce el sonido de explosión con fade-out.
     *
     * @param intensity     Intensidad del flash (ignorada durante la fase HOLD)
     * @param durationTicks Duración del audio de explosión en ticks
     * @param r             Rojo (ignorado — siempre blanco puro)
     * @param g             Verde (ignorado — siempre blanco puro)
     * @param b             Azul  (ignorado — siempre blanco puro)
     * @param soundVolume   Volumen del sonido (pre-ajustado por distancia en el servidor)
     * @param soundPitch    Pitch del sonido de explosión
     */
    public static void trigger(float intensity, int durationTicks,
                               int r, int g, int b,
                               float soundVolume, float soundPitch) {
        if (durationTicks <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Activar flash basado en ticks del juego
        flashStartTick = mc.player.tickCount;

        // Reproducir sonido de explosión con fade-out sincronizado
        if (soundVolume > 0.01f && ModSounds.ECHO_EXPLODE != null) {
            mc.getSoundManager().play(
                    new FadingEchoExplosionSoundInstance(
                            ModSounds.ECHO_EXPLODE,
                            soundVolume, soundPitch, durationTicks
                    )
            );
        }
    }

    /**
     * Debe llamarse cada tick para actualizar el estado.
     */
    public static void tick() {
        // El estado se calcula dinámicamente en isActive() y render()
    }

    // ── Render ───────────────────────────────────────────────────────────────

    public static void render(GuiGraphics guiGraphics, DeltaTracker tickDelta) {
        if (flashStartTick < 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int elapsed = mc.player.tickCount - flashStartTick;

        if (elapsed >= TOTAL_TICKS) {
            flashStartTick = -1;
            return;
        }

        final float alpha;
        if (elapsed <= HOLD_TICKS) {
            alpha = 1.0f;
        } else {
            float fadeProgress = (float) (elapsed - HOLD_TICKS) / (float) (TOTAL_TICKS - HOLD_TICKS);
            alpha = 1.0f - Math.min(1.0f, fadeProgress);
        }

        if (alpha <= 0.0f) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        Tesselator   tess = Tesselator.getInstance();
        BufferBuilder buf  = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f mat = guiGraphics.pose().last().pose();
        int      a   = (int) (alpha * 255f);

        // FIX 3: Quad cubre exactamente la pantalla completa en coords GUI
        buf.addVertex(mat, 0,       screenH, 0).setColor(255, 255, 255, a);
        buf.addVertex(mat, screenW, screenH, 0).setColor(255, 255, 255, a);
        buf.addVertex(mat, screenW, 0,       0).setColor(255, 255, 255, a);
        buf.addVertex(mat, 0,       0,       0).setColor(255, 255, 255, a);

        MeshData mesh = buf.buildOrThrow();
        BufferUploader.drawWithShader(mesh);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}