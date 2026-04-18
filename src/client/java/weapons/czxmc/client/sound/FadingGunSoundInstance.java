package weapons.czxmc.client.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * SoundInstance con envolvente de volumen: fade in → hold → fade out.
 * Se usa para el jugador que dispara/recarga (los demás escuchan la versión
 * normal via level.playSound desde el servidor).
 *
 * Attenuation.NONE: al ser el propio jugador quien dispara, el sonido se
 * escucha a volumen pleno independientemente de la posición.
 */
public class FadingGunSoundInstance extends AbstractSoundInstance
        implements TickableSoundInstance {

    /** Ticks de fade-in al comienzo (2 ticks ≈ 100 ms) */
    private static final int FADE_IN_TICKS = 2;
    /** Ticks de fade-out al final (8 ticks ≈ 400 ms) */
    private static final int FADE_OUT_TICKS = 8;
    /** Ticks máximos que puede vivir la instancia (seguridad) */
    private static final int MAX_LIFE_TICKS = 120;

    private final float maxVolume;
    private final int holdTicks;   // ticks a volumen pleno antes del fade-out
    private int currentTick = 0;
    private boolean stopped = false;

    /**
     * @param sound     SoundEvent a reproducir
     * @param volume    volumen máximo (0–1)
     * @param pitch     pitch
     * @param holdTicks ticks a volumen pleno (disparo ≈ 10, recarga ≈ 40)
     */
    public FadingGunSoundInstance(SoundEvent sound, float volume, float pitch, int holdTicks) {
        super(sound, SoundSource.PLAYERS, RandomSource.create());
        this.maxVolume = volume;
        this.holdTicks = holdTicks;
        // Empieza casi mudo para el fade-in (0 podría ser ignorado por el motor de audio)
        this.volume = 0.01f;
        this.pitch = pitch;
        this.looping = false;
        this.delay = 0;
        // Sin atenuación por distancia: es el sonido "en la cabeza" del jugador
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public void tick() {
        if (stopped) return;
        currentTick++;

        int fadeOutStart = FADE_IN_TICKS + holdTicks;
        int totalDuration = fadeOutStart + FADE_OUT_TICKS;

        if (currentTick <= FADE_IN_TICKS) {
            // Fase 1 — fade in
            this.volume = maxVolume * ((float) currentTick / FADE_IN_TICKS);

        } else if (currentTick <= fadeOutStart) {
            // Fase 2 — hold (volumen pleno)
            this.volume = maxVolume;

        } else {
            // Fase 3 — fade out
            float progress = (float)(currentTick - fadeOutStart) / FADE_OUT_TICKS;
            this.volume = maxVolume * Math.max(0f, 1f - progress);
            if (progress >= 1f || currentTick > MAX_LIFE_TICKS) {
                this.stopped = true;
                this.volume = 0f;
            }
        }
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }
}
