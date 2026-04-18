package weapons.czxmc.client.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * SoundInstance para el sonido "before" de la Echo Grenade.
 * Permite actualizar el volumen dinámicamente según la distancia del jugador.
 */
public class DynamicEchoBeforeSoundInstance extends AbstractSoundInstance
        implements TickableSoundInstance {

    private float volume = 1.0f;
    private boolean stopped = false;

    public DynamicEchoBeforeSoundInstance(SoundEvent sound, float volume, float pitch, float x, float y, float z) {
        super(sound, SoundSource.PLAYERS, RandomSource.create());
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.looping = true;
        this.delay = 0;
        this.relative = false;
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    @Override
    public void tick() {
        // Se actualiza desde el cliente cuando recibe el payload de actualización
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public void stop() {
        this.stopped = true;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getZ() {
        return z;
    }
}