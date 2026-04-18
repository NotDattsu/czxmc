package weapons.czxmc.client.sound;

import net.minecraft.sounds.SoundEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de sonidos de Echo Grenade.
 * Usa DynamicEchoBeforeSoundInstance para control de volumen por distancia.
 */
public class EchoSoundManager {

    private static final Map<String, DynamicEchoBeforeSoundInstance> activeSounds = new ConcurrentHashMap<>();

    public static void playBeforeSound(String id, SoundEvent sound, float volume, float pitch, float x, float y, float z) {
        stopBeforeSound(id);
        
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        
        DynamicEchoBeforeSoundInstance soundInstance = new DynamicEchoBeforeSoundInstance(
                sound, volume, pitch, (float) x, (float) y, (float) z
        );
        
        activeSounds.put(id, soundInstance);
        mc.getSoundManager().play(soundInstance);
    }

    public static void updateVolume(String id, float volume) {
        DynamicEchoBeforeSoundInstance sound = activeSounds.get(id);
        if (sound != null) {
            sound.setVolume(volume);
        }
    }

    public static void stopBeforeSound(String id) {
        DynamicEchoBeforeSoundInstance sound = activeSounds.remove(id);
        if (sound != null) {
            sound.stop();
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(sound);
            }
        }
    }

    public static void stopAll() {
        for (DynamicEchoBeforeSoundInstance sound : activeSounds.values()) {
            sound.stop();
        }
        activeSounds.clear();
    }
}