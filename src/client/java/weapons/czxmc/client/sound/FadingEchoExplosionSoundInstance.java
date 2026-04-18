package weapons.czxmc.client.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * SoundInstance para el sonido de explosión de la Echo Grenade.
 *
 * FIX — BASADO EN TIEMPO DE SISTEMA:
 *   Antes usaba un contador de ticks (currentTick++) que se detenía si el juego
 *   se pausaba, causando que el sonido avanzara más rápido que el overlay visual
 *   cuando el jugador reanudaba la partida (el audio del sistema no se pausa,
 *   pero el tick counter sí, creando un desfase).
 *
 *   Ahora usa System.currentTimeMillis() para que el fadeout del audio siempre
 *   esté perfectamente sincronizado con el fadeout visual del GrenadeFlashOverlay,
 *   independientemente de pausas o lag de ticks.
 *
 * Fase HOLD (primeros HOLD_MS = 2000ms): volumen al máximo → tinnitus pleno.
 * Fase FADE (siguientes FADE_MS):        volumen desciende linealmente → silencio.
 *
 * Usa Attenuation.NONE porque el volumen ya viene pre-calculado por el servidor
 * en función de la distancia real del jugador a la explosión.
 */
public class FadingEchoExplosionSoundInstance extends AbstractSoundInstance
        implements TickableSoundInstance {

    /** Duración del hold en ms — igual que GrenadeFlashOverlay.HOLD_MS. */
    private static final long HOLD_MS = 2000L;

    private final float maxVolume;
    private final long  startMs;
    private final long  totalMs;
    private boolean     stopped = false;

    public FadingEchoExplosionSoundInstance(SoundEvent sound, float volume, float pitch, int durationTicks) {
        super(sound, SoundSource.PLAYERS, RandomSource.create());
        this.maxVolume = volume;
        // Convertir duración de ticks a ms (1 tick = 50 ms)
        this.totalMs   = Math.max((long) durationTicks * 50L, HOLD_MS + 100L);
        this.startMs   = System.currentTimeMillis();

        this.volume      = volume;
        this.pitch       = pitch;
        this.looping     = false;
        this.delay       = 0;
        this.attenuation = Attenuation.NONE;

        // Posicionar en el jugador (atenuación desactivada; la posición es irrelevante
        // para el cálculo de volumen, pero OpenAL la necesita)
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }
    }

    @Override
    public void tick() {
        if (stopped) return;

        long elapsed = System.currentTimeMillis() - startMs;

        if (elapsed >= totalMs) {
            stopped      = true;
            this.volume  = 0f;
            return;
        }

        if (elapsed <= HOLD_MS) {
            // Fase HOLD: volumen al máximo (sincronizado con pantalla completamente blanca)
            this.volume = maxVolume;
        } else {
            // Fase FADE: descenso lineal — espejo exacto del overlay visual
            long  fadeTotal    = totalMs - HOLD_MS;
            long  fadeElapsed  = elapsed - HOLD_MS;
            float fadeProgress = (float) fadeElapsed / (float) fadeTotal;
            this.volume = maxVolume * Math.max(0f, 1f - fadeProgress);
        }
    }

    @Override
    public boolean isStopped() {
        if (stopped) return true;
        // También reportar como detenido si el tiempo total ya pasó
        return System.currentTimeMillis() - startMs >= totalMs;
    }
}