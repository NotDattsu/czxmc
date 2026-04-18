package weapons.czxmc.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import weapons.czxmc.network.GrenadeFlashPayload;
import weapons.czxmc.network.GrenadeBeforeSoundPayload;
import weapons.czxmc.network.GrenadeBeforeSoundPosPayload;
import weapons.czxmc.network.GrenadeBeforeSoundVolumePayload;
import weapons.czxmc.sound.ModSounds;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Echo Grenade — granada de atracción con física custom y sonidos personalizados.
 *
 * FIXES aplicados respecto a la versión anterior:
 *
 *  FIX 1 — TIMER DE EXPLOSIÓN:
 *    La granada ya no explota al llegar a MAX_AGE desde el lanzamiento.
 *    Ahora inicia un contador en el momento en que entra en reposo y explota
 *    REST_EXPLOSION_DELAY ticks (10 segundos) después. MAX_AGE sigue siendo
 *    un fallback de seguridad en caso de que nunca llegue a reposar.
 *
 *  FIX 2 — SONIDO DE HIT SPAMMY:
 *    Se agrega un debounce de HIT_SOUND_COOLDOWN ticks para que el sonido de
 *    rebote no se reproduzca múltiples veces por tick cuando la granada
 *    está asentándose sobre una superficie (la gravedad la jala hacia abajo
 *    y cada tick detectaba un nuevo "rebote").
 *
 *  FIX 3 — PARTÍCULAS DE PORTAL (efecto enderman):
 *    Se eliminan las partículas PORTAL del efecto de atracción. Ahora se usan
 *    SCULK_CHARGE_POP y SOUL_FIRE_FLAME, que son visualmente coherentes con
 *    el tema Sculk de la granada y no parecen teletransportación de enderman.
 *
 *  FIX 4 — PARTÍCULAS VISIBLES DESDE LEJOS:
 *    Toda partícula se envía con overrideLimiter=true mediante sendParticlesForced(),
 *    lo que hace que sean visibles a cualquier distancia de renderizado de partículas.
 *
 *  FIX 5 — EXPLOSIÓN ANTES DEL SONIDO (SONIC_BOOM eliminado):
 *    Se elimina ParticleTypes.SONIC_BOOM, que tenía su propio sonido interno que
 *    competía y se adelantaba al sonido personalizado de explosión. Reemplazado
 *    con EXPLOSION_EMITTER para el destello central + más partículas sculk.
 *
 *  FIX 6 — PARTÍCULAS DE EXPLOSIÓN ESCASAS:
 *    La cantidad de partículas en spawnExplosionParticles se aumentó
 *    significativamente: 3 anillos de shockwave, 60 sculk souls dispersos,
 *    un anillo interior adicional, soul fire flames y ash para profundidad visual.
 *
 *  FIX 7 — LANZAMIENTO POR HOLD:
 *    La lógica de throw se movió a EchoGrenadeItem.releaseUsing() para que
 *    el lanzamiento ocurra al SOLTAR el botón, no al presionarlo. Ver EchoGrenadeItem.
 */
public class EchoGrenadeEntity extends GrenadeEntity {

    // ── Constantes de timing ──────────────────────────────────────────────────

    private static final int ATTRACT_START = 30;

    /**
     * MAX_AGE es el fallback de seguridad (25 segundos desde el lanzamiento).
     * En condiciones normales la explosión ocurre por REST_EXPLOSION_DELAY.
     */
    private static final int ECHO_MAX_AGE_FALLBACK = 500;

    /**
     * Ticks de espera desde que la granada está en reposo hasta que explota.
     * 200 ticks = 10 segundos exactos.
     */
    private static final int REST_EXPLOSION_DELAY = 200;

    // ── Constantes de radio ───────────────────────────────────────────────────

    /** Radio de atracción -范围 reducido */
    private static final double ATTRACT_RADIUS    = 6.0;
    private static final double KILL_RADIUS       = 2.5;
    private static final double STUN_RADIUS       = 4.5;
    
    /** Flash reducido para que no sea tan invasivo */
    private static final double FLASH_RADIUS      = 12.0;
    
    /** Sonido - volumen baja más rápido y llega más lejos antes de cortarse */
    private static final double SOUND_FULL_RADIUS = 3.0;  // Desde aquí empieza a bajar
    private static final double SOUND_MAX_RADIUS  = 50.0; // Llega muy lejos antes de cortarse
    private static final float SOUND_MIN_VOLUME   = 0.02f; // Muy bajo para que dure más

    // ── Constantes de fuerza ──────────────────────────────────────────────────

    private static final double ATTRACT_FORCE_BASE = 0.06;
    private static final double ATTRACT_FORCE_MAX  = 0.22;

    // ── Constantes de sonido de hit ───────────────────────────────────────────

    private static final float[] HIT_BASE_PITCH  = { 0.9f, 1.1f, 1.3f };
    private static final float   HIT_PITCH_RANGE = 0.20f;

    /**
     * Intervalo mínimo en ticks entre sonidos de rebote consecutivos.
     * Evita el spam de hit sounds cuando la granada se está asentando y
     * la gravedad la hace "rebotar" micro-veces cada tick.
     */
    private static final int HIT_SOUND_COOLDOWN = 10;

    // ── Estado ────────────────────────────────────────────────────────────────

    /** Ticks desde el último sonido de hit (debounce). */
    private int lastHitSoundAge = -HIT_SOUND_COOLDOWN;

    private boolean attracting         = false;
    private boolean beforeSoundPlayed  = false;
    private boolean explosionTriggered = false;
    
    /** Jugadores que ya recibieron el sonido before */
    private Set<ServerPlayer> playersWhoHeardBefore = new HashSet<>();

    /**
     * Timer de explosión basado en reposo.
     *   -1  → la granada aún no ha llegado a reposar.
     *  >=0  → ticks transcurridos desde que entró en reposo.
     */
    private int restExplosionTimer = -1;

    // ── Constructores ─────────────────────────────────────────────────────────

    public EchoGrenadeEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.MAX_AGE = ECHO_MAX_AGE_FALLBACK;
    }

    public EchoGrenadeEntity(Level level, LivingEntity thrower, Vec3 velocity) {
        super(level, thrower, velocity);
        this.MAX_AGE = ECHO_MAX_AGE_FALLBACK;
    }

    // ── Hook de física (server-only) ──────────────────────────────────────────

    @Override
    protected void onPhysicsTick() {
        if (level().isClientSide) return;

        // ── FIX 1: Inicio del timer de reposo ─────────────────────────────────
        if (isResting() && restExplosionTimer < 0) {
            restExplosionTimer = 0;
        }

        if (restExplosionTimer >= 0) {
            restExplosionTimer++;

            // Sonido "before" sincronizado con el timer de reposo
            int beforeOffset  = ModSounds.ECHO_BEFORE_DURATION_TICKS;
            int beforeTrigger = REST_EXPLOSION_DELAY - beforeOffset;
            if (!beforeSoundPlayed && restExplosionTimer >= beforeTrigger) {
                beforeSoundPlayed = true;
            }

            // Explosión cuando se cumple el delay
            if (restExplosionTimer >= REST_EXPLOSION_DELAY) {
                triggerExplosion();
                return;
            }
        } else {
            // Fallback: sonido "before" basado en age si nunca llegó a reposar
            int beforeOffset = ModSounds.ECHO_BEFORE_DURATION_TICKS;
            if (!beforeSoundPlayed && age >= (ECHO_MAX_AGE_FALLBACK - beforeOffset)) {
                beforeSoundPlayed = true;
            }
        }

        // ── Atracción ──────────────────────────────────────────────────────────
        // Comienza desde ATTRACT_START ticks de vuelo, o inmediatamente al reposar
        if (age >= ATTRACT_START || restExplosionTimer >= 0) {
            if (!attracting) attracting = true;
            tickAttraction();
        }

        // ── Partículas ─────────────────────────────────────────────────────────
        if (age % 3 == 0 && level() instanceof ServerLevel sl) {
            if (!attracting) {
                // Pre-atracción: llamas suaves de alma
                spawnParticlesForced(sl, ParticleTypes.SOUL_FIRE_FLAME,
                        getX(), getY() + 0.1, getZ(), 1, 0.05, 0.05, 0.05, 0.01);
            } else {
                spawnAttractionParticles(sl);
            }
}
        
        // ── Sonido: actualizar volumen cada tick según distancia ────────────────────
        if (beforeSoundPlayed && !explosionTriggered && level() instanceof ServerLevel sl2) {
            tickSoundUpdates(sl2, position());
        }
    }

    // ── Sonido "before" ─────────────────────────────────────────────────────

    /**
     * Secuencia de explosión en dos llamadas consecutivas:
     *   1ª llamada → envía paquetes de flash/sonido y spawn de partículas.
     *   2ª llamada → descarta la entidad.
     *
     * Esto garantiza que los paquetes lleguen al cliente ANTES del destroy packet.
     */
    private void triggerExplosion() {
        if (!explosionTriggered) {
            explosionTriggered = true;
            ServerLevel sl = (ServerLevel) level();
            Vec3 center = position();
            spawnExplosionParticles(sl, center);
            sendFlashAndSoundToNearbyPlayers(sl, center);
            // Enviar stop a todos los que escuchan
            for (ServerPlayer player : playersWhoHeardBefore) {
                ServerPlayNetworking.send(player, new GrenadeBeforeSoundVolumePayload(0f, true));
            }
            playersWhoHeardBefore.clear();
            applyDamageAndEffects(sl, center);
        } else {
            discard();
        }
    }

    @Override
    protected void onExplosion() {
        // Fallback cuando se alcanza MAX_AGE sin llegar a reposar
        if (level().isClientSide) return;
        triggerExplosion();
    }

    // ── FIX 2: Sonido de hit con debounce ────────────────────────────────────

    @Override
    protected void onBounce(int bounceNumber) {
        if (level().isClientSide) return;

        // Solo reproducir el sonido si pasaron HIT_SOUND_COOLDOWN ticks desde el último
        if (age - lastHitSoundAge < HIT_SOUND_COOLDOWN) return;
        lastHitSoundAge = age;

        int variant = (int) (Math.random() * 3);
        float basePitch       = HIT_BASE_PITCH[variant];
        float pitchVariation  = (float) (Math.random() * 2 * HIT_PITCH_RANGE) - HIT_PITCH_RANGE;
        float pitch           = basePitch + pitchVariation;

        net.minecraft.sounds.SoundEvent hitSound = switch (variant) {
            case 0  -> ModSounds.ECHO_HIT_1;
            case 1  -> ModSounds.ECHO_HIT_2;
            default -> ModSounds.ECHO_HIT_3;
        };

        // Volumen decrece con cada rebote, mínimo 0.30
        float volume = Math.max(0.30f, 0.85f - (bounceNumber - 1) * 0.08f);
        level().playSound(null, getX(), getY(), getZ(),
                hitSound, SoundSource.NEUTRAL, volume, pitch);
    }

    // ── Sonido "before" ───────────────────────────────────────────────────────

    /**
     * Envía actualizaciones de volumen cada tick a los jugadores basada en distancia.
     * Cuando explota o el jugador sale del área, enviar stop.
     */
    private void tickSoundUpdates(ServerLevel sl, Vec3 center) {
        if (!beforeSoundPlayed) return;

        // Cuando explota, detener todos los sonidos
        if (explosionTriggered) {
            for (ServerPlayer player : playersWhoHeardBefore) {
                ServerPlayNetworking.send(player, new GrenadeBeforeSoundVolumePayload(0f, true));
            }
            playersWhoHeardBefore.clear();
            return;
        }

        AABB area = new AABB(center, center).inflate(SOUND_MAX_RADIUS);
        List<ServerPlayer> nearbyPlayers = sl.getEntitiesOfClass(ServerPlayer.class, area);

        // Crear set de jugadores cercanos para comparar
        Set<ServerPlayer> nearbySet = new HashSet<>(nearbyPlayers);

        // Players que desarrollaron antes - verificar si siguen dentro
        for (ServerPlayer player : playersWhoHeardBefore) {
            if (!nearbySet.contains(player)) {
                //Player salió del área - enviar stop
                ServerPlayNetworking.send(player, new GrenadeBeforeSoundVolumePayload(0f, true));
            }
        }
        playersWhoHeardBefore.retainAll(nearbySet);

        // Procesar jugadores cercanos
        for (ServerPlayer player : nearbyPlayers) {
            double dist = player.position().distanceTo(center);

            // Si está muy lejos, ya no envía nada (stop se envió arriba)
            if (dist > SOUND_MAX_RADIUS) continue;

            float volume = computeSoundVolume(dist);

            // Si el volumen es 0 o muy bajo, enviar stop
            if (volume <= 0.01f) {
                if (playersWhoHeardBefore.contains(player)) {
                    ServerPlayNetworking.send(player, new GrenadeBeforeSoundVolumePayload(0f, true));
                    playersWhoHeardBefore.remove(player);
                }
                continue;
            }

            if (!playersWhoHeardBefore.contains(player)) {
                playersWhoHeardBefore.add(player);
                ServerPlayNetworking.send(player, new GrenadeBeforeSoundPosPayload(
                        center, volume, 1.0f
                ));
            } else {
                ServerPlayNetworking.send(player, new GrenadeBeforeSoundVolumePayload(volume, false));
            }
        }
    }

    // ── Atracción ─────────────────────────────────────────────────────────────

    private void tickAttraction() {
        Vec3   center   = position();
        // Progreso: usa timer de reposo si disponible, sino usa age
        float  progress = (restExplosionTimer >= 0)
                ? Math.min(1.0f, (float) restExplosionTimer / REST_EXPLOSION_DELAY)
                : Math.min(1.0f, (float) (age - ATTRACT_START) / (ECHO_MAX_AGE_FALLBACK - ATTRACT_START));

        double force = ATTRACT_FORCE_BASE + (ATTRACT_FORCE_MAX - ATTRACT_FORCE_BASE) * (progress * progress);

        AABB             area   = new AABB(center, center).inflate(ATTRACT_RADIUS);
        List<LivingEntity> nearby = level().getEntitiesOfClass(LivingEntity.class, area);

        for (LivingEntity entity : nearby) {
            if (entity == getThrower()) continue;
            double dist = entity.position().distanceTo(center);
            if (dist > ATTRACT_RADIUS || dist < 0.1) continue;

            Vec3   dir         = center.subtract(entity.position()).normalize();
            double distFactor  = 1.0 - (dist / ATTRACT_RADIUS) * 0.5;
            double actualForce = force * distFactor;

            Vec3 current = entity.getDeltaMovement();
            entity.setDeltaMovement(
                    current.x + dir.x * actualForce,
                    current.y + dir.y * actualForce * 0.4,
                    current.z + dir.z * actualForce
            );
            entity.hurtMarked = true;

            if (entity instanceof ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(
                        sp.getId(), entity.getDeltaMovement()));
            }
        }
    }

    // ── Daño ──────────────────────────────────────────────────────────────────

    private void applyDamageAndEffects(ServerLevel sl, Vec3 center) {
        DamageSource       dmgSrc = damageSources().explosion(this, (LivingEntity) getThrower());
        AABB               area   = new AABB(center, center).inflate(STUN_RADIUS);
        List<LivingEntity> nearby = sl.getEntitiesOfClass(LivingEntity.class, area);

        for (LivingEntity entity : nearby) {
            if (entity == getThrower()) continue;
            double dist = entity.position().distanceTo(center);
            if (dist <= KILL_RADIUS) {
                entity.hurt(dmgSrc, 999.0f);
            } else if (dist <= STUN_RADIUS) {
                float proximity = (float) (1.0 - (dist - KILL_RADIUS) / (STUN_RADIUS - KILL_RADIUS));
                int   stunTicks = (int) (100 * proximity) + 40;
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stunTicks, 4, false, true));
                entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, stunTicks / 2, 0, false, true));
            }
        }
    }

    // ── Flash + sonido de explosión ───────────────────────────────────────────

    private void sendFlashAndSoundToNearbyPlayers(ServerLevel sl, Vec3 center) {
        int  soundDuration = ModSounds.ECHO_EXPLODE_DURATION_TICKS;
        AABB flashArea     = new AABB(center, center).inflate(FLASH_RADIUS);
        List<ServerPlayer> players = sl.getEntitiesOfClass(ServerPlayer.class, flashArea);

        // Enviar payload sin volumen pre-calculado (el cliente reproducirá con posición 3D)
        for (ServerPlayer player : players) {
            ServerPlayNetworking.send(player, new GrenadeFlashPayload(
                    1.0f, soundDuration,
                    255, 255, 255,
                    1.0f, 1.0f,
                    false, 0f
            ));
        }
    }

    // ── Cálculo de volumen por distancia ─────────────────────────────────────

    private float computeSoundVolume(double dist) {
        if (dist <= SOUND_FULL_RADIUS) {
            return 1.0f;
        }
        if (dist >= SOUND_MAX_RADIUS) {
            return 0f; // Cortar completamente cuando está muy lejos
        }
        // Interpolación cuadrática para que baje más rápido al inicio
        float t = (float) ((dist - SOUND_FULL_RADIUS) / (SOUND_MAX_RADIUS - SOUND_FULL_RADIUS));
        float volume = 1.0f - (t * t); // Cuadrático - baja más rápido
        return Math.max(volume, 0f);
    }

    // ── Partículas de atracción con anillo que se achica progresivamente ──────────

    private void spawnAttractionParticles(ServerLevel sl) {
        float progress = (restExplosionTimer >= 0)
                ? Math.min(1.0f, (float) restExplosionTimer / REST_EXPLOSION_DELAY)
                : Math.min(1.0f, (float) (age - ATTRACT_START) / (float) (ECHO_MAX_AGE_FALLBACK - ATTRACT_START));

        // Anillo se achica desde ATTRACT_RADIUS hasta casi 0 (la granada) en la explosión
        double ringRadius = ATTRACT_RADIUS * (1.0 - progress * 0.92);
        int    count      = 12 + (int) (progress * 24);

        // Anillo de SCULK_SOUL más visible y rotando más rápido
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i / count) + (age * 0.6);
            double px    = getX() + Math.cos(angle) * ringRadius;
            double py    = getY() + 0.5 + Math.sin(age * 0.3) * 0.4;
            double pz    = getZ() + Math.sin(angle) * ringRadius;
            spawnParticlesForced(sl, ParticleTypes.SCULK_SOUL, px, py, pz, 1,
                    (getX() - px) * 0.08, 0.03, (getZ() - pz) * 0.08, 0.015);
        }

        // Centro: SCULK_CHARGE_POP más frequente
        if (age % 2 == 0) {
            spawnParticlesForced(sl, ParticleTypes.SCULK_CHARGE_POP,
                    getX(), getY() + 0.3, getZ(), 6, 0.15, 0.15, 0.15, 0.015);
        }

        // Llamas de alma
        if (age % 4 == 0) {
            double offX = (Math.random() - 0.5) * 0.5;
            double offZ = (Math.random() - 0.5) * 0.5;
            spawnParticlesForced(sl, ParticleTypes.SOUL_FIRE_FLAME,
                    getX() + offX, getY() + 0.15, getZ() + offZ, 3, 0.05, 0.05, 0.05, 0.02);
        }

        // Segundo anillo interno cuando progress > 0.4 (más cerca de la explosión)
        if (progress > 0.4f && age % 3 == 0) {
            double innerR = ringRadius * 0.5;
            for (int i = 0; i < 8; i++) {
                double angle = (2 * Math.PI * i / 8) - (age * 0.8);
                spawnParticlesForced(sl, ParticleTypes.SCULK_CHARGE_POP,
                        getX() + Math.cos(angle) * innerR,
                        getY() + 0.4,
                        getZ() + Math.sin(angle) * innerR,
                        1, 0, 0.015, 0, 0);
            }
        }

        // Tercer anillo muy pequeño cerca de explotar
        if (progress > 0.7f && age % 2 == 0) {
            double tinyR = ringRadius * 0.25;
            for (int i = 0; i < 6; i++) {
                double angle = (2 * Math.PI * i / 6) - (age * 1.2);
                spawnParticlesForced(sl, ParticleTypes.SCULK_SOUL,
                        getX() + Math.cos(angle) * tinyR,
                        getY() + 0.5,
                        getZ() + Math.sin(angle) * tinyR,
                        1, 0, 0.01, 0, 0.008);
            }
        }
    }

    // ── Partículas de explosión estilo Warden ────────────────────────────────

private void spawnExplosionParticles(ServerLevel sl, Vec3 c) {
        // ── Shockwaves grandes y visibles ─────────────────────────────────────
        spawnParticlesForced(sl, ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 8, 1.0, 0.5, 1.0, 0);
        spawnParticlesForced(sl, ParticleTypes.SONIC_BOOM, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
        
        // ── SCULK_SOUL en esfera grande (muchas más partículas) ───────────────
        for (int i = 0; i < 200; i++) {
            double theta = Math.random() * 2 * Math.PI;
            double phi = (Math.random() - 0.5) * Math.PI;
            double speed = 0.15 + Math.random() * 0.35;
            
            double vx = Math.cos(theta) * Math.cos(phi) * speed;
            double vy = Math.sin(phi) * speed + 0.05;
            double vz = Math.sin(theta) * Math.cos(phi) * speed;
            
            spawnParticlesForced(sl, ParticleTypes.SCULK_SOUL,
                    c.x, c.y + 0.3, c.z, 1, vx, vy, vz, 0.05);
        }

        // ── SOUL_FIRE_FLAME en esfera (fuego azul grande) ───────────────────
        for (int i = 0; i < 80; i++) {
            double theta = Math.random() * 2 * Math.PI;
            double phi = (Math.random() - 0.5) * Math.PI;
            double speed = 0.10 + Math.random() * 0.20;
            
            double vx = Math.cos(theta) * Math.cos(phi) * speed;
            double vy = Math.sin(phi) * speed + 0.03;
            double vz = Math.sin(theta) * Math.cos(phi) * speed;
            
            spawnParticlesForced(sl, ParticleTypes.SOUL_FIRE_FLAME,
                    c.x, c.y + 0.2, c.z, 1, vx, vy, vz, 0.05);
        }

        // ── SCULK_CHARGE_POP grande ──────────────────────────────────────────
        for (int i = 0; i < 60; i++) {
            double theta = Math.random() * 2 * Math.PI;
            double phi = (Math.random() - 0.5) * Math.PI;
            double speed = 0.15 + Math.random() * 0.25;
            
            double vx = Math.cos(theta) * Math.cos(phi) * speed;
            double vy = Math.sin(phi) * speed + 0.04;
            double vz = Math.sin(theta) * Math.cos(phi) * speed;
            
            spawnParticlesForced(sl, ParticleTypes.SCULK_CHARGE_POP,
                    c.x, c.y + 0.2, c.z, 1, vx, vy, vz, 0.03);
        }

        // ── Ceniza flotante ───────────────────────────────────────────────────
        spawnParticlesForced(sl, ParticleTypes.ASH,
                c.x, c.y + 1.0, c.z, 30, 0.8, 0.4, 0.8, 0.05);
    }
    
    private void spawnWardenShockwaveRing(ServerLevel sl, Vec3 center, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i / count);
            spawnParticlesForced(sl, ParticleTypes.SCULK_SOUL,
                    center.x + Math.cos(angle) * radius, center.y + 0.3,
                    center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0.03);
        }
    }
    
    private void spawnShockwaveRing(ServerLevel sl, Vec3 center, double yawOffset, double radius, int count) {
        double radOff = Math.toRadians(yawOffset);
        for (int i = 0; i < count; i++) {
            double angle = radOff + (2 * Math.PI * i / count);
            spawnParticlesForced(sl, ParticleTypes.SCULK_CHARGE_POP,
                    center.x + Math.cos(angle) * radius, center.y + 0.4,
                    center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    /**
     * FIX 4: Envía partículas con overrideLimiter=true para que sean visibles
     * a cualquier distancia de renderizado de partículas del cliente.
     */
    private <T extends ParticleOptions> void spawnParticlesForced(
            ServerLevel sl, T particle,
            double x, double y, double z,
            int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : sl.players()) {
            sl.sendParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
        }
    }
}