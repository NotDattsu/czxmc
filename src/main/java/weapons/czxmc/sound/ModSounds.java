package weapons.czxmc.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import weapons.czxmc.CZXMCWeapons;

public class ModSounds {

    // ── Sonidos de la pistola ─────────────────────────────────────────────────

    public static SoundEvent GUN_SHOOT;
    public static SoundEvent GUN_SHOOT_ALT;
    public static SoundEvent GUN_RELOAD;
    public static SoundEvent GUN_HIT;

    // ── Sonidos del dash ──────────────────────────────────────────────────────
    public static SoundEvent DASH;
    public static SoundEvent GUN_UPGRADE;

    // ── Sonidos de la Echo Grenade ────────────────────────────────────────────

    /** Rebotes: hit1, hit2, hit3 */
    public static SoundEvent ECHO_HIT_1;
    public static SoundEvent ECHO_HIT_2;
    public static SoundEvent ECHO_HIT_3;

    /** Sonido de advertencia pre-explosión */
    public static SoundEvent ECHO_BEFORE;

    /** Sonido de explosión */
    public static SoundEvent ECHO_EXPLODE;

    /**
     * Duración en ticks del sonido "before".
     * 5.603 segundos = 112 ticks (redondeado).
     * El servidor envía el paquete exactamente este número de ticks antes de explotar,
     * de modo que el sonido comienza a tiempo para terminar justo cuando ocurre la explosión.
     */
    public static final int ECHO_BEFORE_DURATION_TICKS = 112;

    /**
     * Duración en ticks del flash de pantalla y sonido de explosión.
     * 80 ticks = 4 segundos totales:
     *   - Primeros 2 segundos fijos (40 ticks): pantalla blanca total, ceguera completa.
     *   - Siguientes 2 segundos (40 ticks): fade-out lineal, recuperación gradual de visión.
     */
    public static final int ECHO_EXPLODE_DURATION_TICKS = 80;

    // ── Registro ──────────────────────────────────────────────────────────────

    public static void registerAll() {
        GUN_SHOOT     = register("gun_shoot");
        GUN_SHOOT_ALT = register("gun_shoot_alt");
        GUN_RELOAD    = register("gun_reload");
        GUN_HIT       = register("gun_hit");
        GUN_UPGRADE   = register("gun_upgrade");
        DASH          = register("dash");

        ECHO_HIT_1    = register("echo_grenade_hit1");
        ECHO_HIT_2    = register("echo_grenade_hit2");
        ECHO_HIT_3    = register("echo_grenade_hit3");
        ECHO_BEFORE   = register("echo_grenade_before");
        ECHO_EXPLODE  = register("echo_grenade_explosion");

        CZXMCWeapons.LOGGER.info("Sonidos registrados.");
    }

    private static SoundEvent register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                SoundEvent.createVariableRangeEvent(id));
    }
}
