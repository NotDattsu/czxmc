package weapons.czxmc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import weapons.czxmc.ModEntities;

/**
 * Proyectil del Grapple Hook (Ancla de Abordaje).
 *
 * FIX #7 → Pull FUERTE y rapido: PULL_FORCE 0.35→0.85, MAX_PULL_SPEED 1.0→2.8
 * FIX #8 → Compensacion de gravedad durante el swing (swing fluido tipo whiplash)
 * FIX #9 → Stuck detection: retracta si el jugador no progresa hacia el anchor
 * FIX #10→ Retracta si el anchor esta bloqueado directamente arriba (caso hojas)
 * FIX #11→ Gracia inicial: ignora deteccion de salto los primeros ticks (funciona en suelo)
 * FIX #12→ Impulso vertical inicial al comenzar el pull (primeros ticks)
 */
public class AnchorHookEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Integer> STATE =
        SynchedEntityData.defineId(AnchorHookEntity.class, EntityDataSerializers.INT);

    public enum State { FLYING, HOOKED_BLOCK, HOOKED_MOB, RETRACTING }

    // ── Fisica del proyectil ──────────────────────────────────────────────────
    private static final double GRAVITY                 = 0.05;
    private static final double DRAG                    = 0.01;

    // ── Pull hacia el bloque enganchado ───────────────────────────────────────
    // [FIX #7] Pull fuerte. Era 0.35 (lento). 0.85 da aceleracion notoria.
    private static final double PULL_FORCE              = 0.85;
    // [FIX #7] Velocidad maxima muy aumentada. Era 1.0 m/tick. Ahora 2.8.
    private static final double MAX_PULL_SPEED          = 2.8;
    // [FIX #8] Compensacion de gravedad (~80%). Sin esto el jugador cae en
    // picada durante el swing y pierde todo el momentum vertical.
    private static final double GRAVITY_COMPENSATION    = 0.065;

    // ── Mob pull ──────────────────────────────────────────────────────────────
    private static final double MOB_PULL_FORCE          = 0.55;
    private static final double PLAYER_TOWARD_MOB_FORCE = 0.20;

    // ── Retraccion ────────────────────────────────────────────────────────────
    private static final double RETRACT_SPEED           = 1.8;
    private static final double RETRACT_DONE_DIST       = 1.5;

    // ── Vuelo ─────────────────────────────────────────────────────────────────
    private static final int    DEFAULT_MAX_FLY_TICKS   = 40;
    private static final double MIN_FLYING_SPEED_SQ     = 0.005;
    private static final double MAX_HOOK_DISTANCE_SQ    = 50.0 * 50.0;   // [FIX #1]
    private static final int    MAX_HOOK_TICKS          = 300;            // [FIX #2]

    // [FIX #3] Boost vertical al engancharse. Aumentado de 0.22 a 0.55.
    private static final double HOOK_VERTICAL_BOOST     = 0.55;

    // [FIX #4] Control WASD durante el pull
    private static final double WASD_CONTROL_FORCE      = 0.12;

    // [FIX #5] Deteccion de salto
    private static final double JUMP_DETECT_THRESHOLD   = 0.28;
    private static final double GROUND_JUMP_THRESHOLD   = 0.18;

    // [FIX #9] Ticks sin progreso para considerar stuck
    private static final int    STUCK_TICK_THRESHOLD    = 18;

    // [FIX #10] Ratio horizontal/vertical para detectar "anchor directamente arriba"
    private static final double ABOVE_STUCK_RATIO       = 0.65;

    // [FIX #11] Ticks de gracia al inicio del hook donde NO se verifica deteccion
    // de salto. Esto permite que el pull funcione aunque el jugador este en el suelo,
    // ya que el aumento de velocidad Y del pull disparaba la deteccion erroneamente.
    private static final int    JUMP_DETECT_GRACE_TICKS = 8;

    // [FIX #12] Impulso vertical adicional aplicado durante los primeros ticks del pull.
    // Ayuda a despegar del suelo sin necesidad de saltar manualmente.
    private static final double INITIAL_UPWARD_BOOST    = 0.18;
    private static final int    INITIAL_BOOST_TICKS     = 6;

    private int maxFlyTicks = DEFAULT_MAX_FLY_TICKS;

    private LivingEntity thrower;
    private LivingEntity hookedMob;
    private Vec3         hookedBlockPos;

    private double  prevExpectedThrowerYVel = Double.NaN;
    private boolean prevThrowerOnGround     = false;
    private int     hookTickCount           = 0;

    // [FIX #9]
    private double prevDistToAnchor = -1.0;
    private int    stuckTicks       = 0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public static final RawAnimation ANIM_FLY  = RawAnimation.begin().thenLoop("fly");
    public static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");

    public AnchorHookEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public AnchorHookEntity(Level level, LivingEntity thrower, Vec3 initialVelocity, int maxFlyTicks) {
        this(ModEntities.ANCHOR_HOOK, level);
        this.thrower     = thrower;
        this.maxFlyTicks = maxFlyTicks;

        Vec3 look = thrower.getLookAngle();
        double spawnX = thrower.getX() + look.x * 0.5;
        double spawnY = thrower.getEyeY() - 0.1;
        double spawnZ = thrower.getZ() + look.z * 0.5;

        moveTo(spawnX, spawnY, spawnZ, getYRot(), getXRot());
        setDeltaMovement(initialVelocity);
        updateRotationFromVelocity(initialVelocity);
        setState(State.FLYING);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, State.FLYING.ordinal());
    }

    public State getState() { return State.values()[entityData.get(STATE)]; }
    public void  setState(State s) { entityData.set(STATE, s.ordinal()); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        switch (getState()) {
            case FLYING       -> tickFlying();
            case HOOKED_BLOCK -> tickHookedBlock();
            case HOOKED_MOB   -> tickHookedMob();
            case RETRACTING   -> tickRetracting();
        }
    }

    private void tickFlying() {
        Vec3 vel = getDeltaMovement();
        vel = vel.add(0, -GRAVITY, 0).scale(1.0 - DRAG);
        setDeltaMovement(vel);
        updateRotationFromVelocity(vel);

        if (vel.lengthSqr() < MIN_FLYING_SPEED_SQ) {
            setState(State.RETRACTING);
            return;
        }

        // [FIX #1] Chequear distancia maxima antes de detectar colisiones.
        if (thrower != null) {
            double distSq = position().distanceToSqr(thrower.position());
            if (distSq > MAX_HOOK_DISTANCE_SQ) {
                setState(State.RETRACTING);
                return;
            }
        }

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this,
                e -> e != thrower && e instanceof LivingEntity);

        if (hit.getType() == HitResult.Type.BLOCK) {
            hookedBlockPos          = ((BlockHitResult) hit).getLocation();
            moveTo(hookedBlockPos.x, hookedBlockPos.y, hookedBlockPos.z);
            setDeltaMovement(Vec3.ZERO);
            prevExpectedThrowerYVel = Double.NaN;
            prevThrowerOnGround     = thrower != null && thrower.onGround();
            hookTickCount           = 0;
            prevDistToAnchor        = -1.0;
            stuckTicks              = 0;

            // [FIX #3] Impulso vertical al engancharse (aumentado de 0.22 a 0.55).
            if (thrower != null) {
                Vec3 tVel    = thrower.getDeltaMovement();
                Vec3 boosted = new Vec3(tVel.x, Math.max(tVel.y, HOOK_VERTICAL_BOOST), tVel.z);
                thrower.setDeltaMovement(boosted);
                thrower.hurtMarked = true;
                if (thrower instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundSetEntityMotionPacket(
                        sp.getId(), sp.getDeltaMovement()));
                }
            }

            setState(State.HOOKED_BLOCK);
            return;
        }

        if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult ehr = (EntityHitResult) hit;
            if (ehr.getEntity() instanceof LivingEntity mob && mob != thrower) {
                hookedMob = mob;
                setState(State.HOOKED_MOB);
                return;
            }
        }

        move(MoverType.SELF, vel);

        if (tickCount >= maxFlyTicks) {
            setState(State.RETRACTING);
        }
    }

    private void tickHookedBlock() {
        if (thrower == null || !thrower.isAlive()) {
            discard();
            return;
        }

        // [FIX #2] Timeout anti-stuck
        hookTickCount++;
        if (hookTickCount > MAX_HOOK_TICKS) {
            setState(State.RETRACTING);
            return;
        }

        Vec3 anchorPos    = position();
        Vec3 playerCenter = thrower.position().add(0, thrower.getEyeHeight() * 0.5, 0);
        Vec3 toAnchor     = anchorPos.subtract(playerCenter);
        double dist       = toAnchor.length();
        double currentYVel = thrower.getDeltaMovement().y;
        boolean onGround   = thrower.onGround();

        // [FIX #10] Retractar si el anchor esta directamente encima y hay
        // colision vertical (jugador bloqueado por bloques arriba).
        // Resuelve el caso tipico: enganchado en hojas justo sobre la cabeza.
        {
            double dx = hookedBlockPos.x - thrower.getX();
            double dz = hookedBlockPos.z - thrower.getZ();
            double dy = hookedBlockPos.y - thrower.getEyeY();
            boolean anchorDirectlyAbove = dy > 0.5
                    && Math.sqrt(dx * dx + dz * dz) < dy * ABOVE_STUCK_RATIO;
            if (anchorDirectlyAbove && thrower.verticalCollision) {
                setState(State.RETRACTING);
                return;
            }
        }

        // [FIX #5] Deteccion de salto - doble metodo
        // [FIX #11] Ignorar durante los primeros JUMP_DETECT_GRACE_TICKS ticks
        // para que el pull funcione cuando el jugador esta en el suelo.
        // Sin esta gracia, el aumento de velocidad Y causado por el propio pull
        // (o por el HOOK_VERTICAL_BOOST) era interpretado como un salto.
        if (hookTickCount > JUMP_DETECT_GRACE_TICKS) {
            if (prevThrowerOnGround && currentYVel > GROUND_JUMP_THRESHOLD) {
                setState(State.RETRACTING);
                return;
            }
            if (!Double.isNaN(prevExpectedThrowerYVel)
                    && currentYVel > prevExpectedThrowerYVel + JUMP_DETECT_THRESHOLD) {
                setState(State.RETRACTING);
                return;
            }
        }
        prevThrowerOnGround = onGround;

        Vec3 playerVel = thrower.getDeltaMovement();

        // Cancelar si el jugador paso la posicion del bloque
        if (dist > 0.01 && dist < 3.0 && toAnchor.normalize().dot(playerVel) < -0.1) {
            setState(State.RETRACTING);
            return;
        }

        // [FIX #6] Auto-cancelar con distancia dinamica segun velocidad
        double playerSpeed     = playerVel.length();
        double autoRetractDist = Mth.clamp(playerSpeed * 5.0, 2.0, 5.0);
        if (dist < autoRetractDist) {
            setState(State.RETRACTING);
            return;
        }

        // [FIX #9] Stuck detection: si el jugador no avanza hacia el anchor
        // en STUCK_TICK_THRESHOLD ticks (unos 0.9 segundos), retractar.
        if (prevDistToAnchor >= 0.0) {
            if (dist >= prevDistToAnchor - 0.05) {
                stuckTicks++;
                if (stuckTicks >= STUCK_TICK_THRESHOLD) {
                    setState(State.RETRACTING);
                    return;
                }
            } else {
                stuckTicks = 0;
            }
        }
        prevDistToAnchor = dist;

        // ── Pull principal ─────────────────────────────────────────────────────
        Vec3 toAnchorNorm    = toAnchor.normalize();
        Vec3 pull            = toAnchorNorm.scale(PULL_FORCE);
        double speedToAnchor = playerVel.dot(toAnchorNorm);

        Vec3 newVel = (speedToAnchor < MAX_PULL_SPEED)
                ? playerVel.add(pull)
                : playerVel;

        // [FIX #8] Compensacion de gravedad durante el pull.
        // Cancela ~80% de la gravedad del jugador para que el swing sea
        // fluido y con momentum, en vez de caer en picada inmediatamente.
        newVel = new Vec3(newVel.x, newVel.y + GRAVITY_COMPENSATION, newVel.z);

        // [FIX #12] Impulso vertical inicial: durante los primeros INITIAL_BOOST_TICKS
        // ticks se aplica un empuje extra hacia arriba para despegar del suelo.
        // Se reduce gradualmente para que la transicion sea suave.
        if (hookTickCount <= INITIAL_BOOST_TICKS) {
            double boostFactor = 1.0 - (hookTickCount / (double) INITIAL_BOOST_TICKS);
            newVel = new Vec3(newVel.x, newVel.y + INITIAL_UPWARD_BOOST * boostFactor, newVel.z);
        }

        // [FIX #4] Control con WASD durante el pull
        float forwardInput = thrower.zza;
        float strafeInput  = thrower.xxa;
        if (Math.abs(forwardInput) > 0.01 || Math.abs(strafeInput) > 0.01) {
            Vec3 lookFlat  = thrower.getLookAngle().multiply(1, 0, 1);
            double flatLen = lookFlat.length();
            if (flatLen > 0.001) {
                lookFlat = lookFlat.scale(1.0 / flatLen);
                Vec3 rightFlat  = new Vec3(-lookFlat.z, 0, lookFlat.x);
                Vec3 inputForce = lookFlat.scale(forwardInput  * WASD_CONTROL_FORCE)
                                          .add(rightFlat.scale(strafeInput * WASD_CONTROL_FORCE));
                newVel = newVel.add(inputForce);
            }
        }

        thrower.setDeltaMovement(newVel);
        thrower.hurtMarked = true;

        if (thrower instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(
                sp.getId(), sp.getDeltaMovement()));
        }

        prevExpectedThrowerYVel = newVel.y + pull.y;
    }

    private void tickHookedMob() {
        if (thrower == null || hookedMob == null
                || !thrower.isAlive() || !hookedMob.isAlive()) {
            setState(State.RETRACTING);
            return;
        }

        moveTo(hookedMob.getX(), hookedMob.getY() + hookedMob.getBbHeight() * 0.5,
               hookedMob.getZ(), getYRot(), getXRot());

        Vec3 playerPos = thrower.position();
        Vec3 mobPos    = hookedMob.position();
        double dist    = mobPos.distanceTo(playerPos);

        if (dist < 2.0) {
            setState(State.RETRACTING);
            return;
        }

        Vec3 toPlayer = playerPos.subtract(mobPos).normalize();
        Vec3 toMob    = mobPos.subtract(playerPos).normalize();

        hookedMob.setDeltaMovement(hookedMob.getDeltaMovement().add(toPlayer.scale(MOB_PULL_FORCE)));
        hookedMob.hurtMarked = true;

        thrower.setDeltaMovement(thrower.getDeltaMovement().add(toMob.scale(PLAYER_TOWARD_MOB_FORCE)));
        thrower.hurtMarked = true;

        if (thrower instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(
                sp.getId(), sp.getDeltaMovement()));
        }
    }

    private void tickRetracting() {
        if (thrower == null || !thrower.isAlive()) {
            discard();
            return;
        }

        Vec3 target   = thrower.getEyePosition();
        Vec3 toTarget = target.subtract(position());
        double dist   = toTarget.length();

        if (dist < RETRACT_DONE_DIST) {
            discard();
            return;
        }

        Vec3 retractVel = toTarget.normalize().scale(RETRACT_SPEED);
        setDeltaMovement(retractVel);
        updateRotationFromVelocity(retractVel);
        move(MoverType.SELF, retractVel);
    }

    public void retract() {
        if (getState() != State.RETRACTING) {
            setState(State.RETRACTING);
        }
    }

    public boolean isActive() {
        return getState() != State.RETRACTING;
    }

    private void updateRotationFromVelocity(Vec3 vel) {
        if (vel.lengthSqr() < 0.0001) return;
        double horLen = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        float yaw   = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(vel.y, horLen));
        setYRot(yaw);
        setXRot(pitch);
        yRotO = yaw;
        xRotO = pitch;
    }

    public LivingEntity getThrower()        { return thrower; }
    public LivingEntity getHookedMob()      { return hookedMob; }
    public Vec3         getHookedBlockPos() { return hookedBlockPos; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            State s = getState();
            state.setAnimation(s == State.FLYING || s == State.RETRACTING ? ANIM_FLY : ANIM_IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setState(State.values()[tag.getInt("HookState")]);
        this.maxFlyTicks = tag.contains("MaxFlyTicks") ? tag.getInt("MaxFlyTicks") : DEFAULT_MAX_FLY_TICKS;
        Vec3 vel = new Vec3(tag.getDouble("VelX"), tag.getDouble("VelY"), tag.getDouble("VelZ"));
        setDeltaMovement(vel);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("HookState", getState().ordinal());
        tag.putInt("MaxFlyTicks", maxFlyTicks);
        Vec3 vel = getDeltaMovement();
        tag.putDouble("VelX", vel.x);
        tag.putDouble("VelY", vel.y);
        tag.putDouble("VelZ", vel.z);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 4096; }
}
