package weapons.czxmc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import weapons.czxmc.ModEntities;

public class GrenadeEntity extends Entity implements GeoAnimatable {

    // ── Física — parámetros ───────────────────────────────────────────────────

    protected double GRAVITY         = 0.082;
    protected double AIR_DRAG        = 0.010;
    protected double GROUND_FRICTION = 0.74;
    protected double RESTITUTION     = 0.45;
    protected double REST_THRESHOLD  = 0.04;
    protected int    MAX_AGE         = 600;
    protected float  radius          = 0.28f;

    private static final double MAX_SPEED = 0.8;

    // ── Estado de física ──────────────────────────────────────────────────────

    private Vec3    velocity  = Vec3.ZERO;
    private boolean onSurface = false;
    private int     restTicks = 0;
    private boolean isResting = false;
    protected int   age       = 0;
    private int     bounceCount = 0;

    // ── SynchedEntityData ─────────────────────────────────────────────────────

    private static final EntityDataAccessor<Float> PREV_PHYS_X =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PREV_PHYS_Y =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PREV_PHYS_Z =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Boolean> SURFACE_SYNC =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RESTING_SYNC =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BOUNCE_SYNC =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Float> SPIN_X =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPIN_Y =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPIN_Z =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> PREV_SPIN_X =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PREV_SPIN_Y =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PREV_SPIN_Z =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> SYNC_VEL_X =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNC_VEL_Y =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNC_VEL_Z =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> SYNC_ANG_VEL_X =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNC_ANG_VEL_Y =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNC_ANG_VEL_Z =
            SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.FLOAT);

    // ── Rotación visual ───────────────────────────────────────────────────────

    private float angularVelX = 0;
    private float angularVelY = 0;
    private float angularVelZ = 0;

    private double prevPhysX    = 0;
    private double prevPhysY    = 0;
    private double prevPhysZ    = 0;
    private Vec3   prevVelocity = Vec3.ZERO;

    private float prevSpinX = 0;
    private float prevSpinY = 0;
    private float prevSpinZ = 0;

    // ── Dead reckoning ────────────────────────────────────────────────────────

    private double  clientSimX           = 0;
    private double  clientSimY           = 0;
    private double  clientSimZ           = 0;
    private Vec3    clientSimVel         = Vec3.ZERO;
    private boolean clientSimInitialized = false;

    // ── Rotación local cliente ────────────────────────────────────────────────

    private float clientSpinX     = 0;
    private float clientSpinY     = 0;
    private float clientSpinZ     = 0;
    private float clientPrevSpinX = 0;
    private float clientPrevSpinY = 0;
    private float clientPrevSpinZ = 0;

    protected LivingEntity thrower;

    // ── Constructores ─────────────────────────────────────────────────────────

    public GrenadeEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public GrenadeEntity(Level level, LivingEntity thrower, Vec3 initialVelocity) {
        this(ModEntities.GRENADE, level);
        this.thrower  = thrower;
        this.velocity = initialVelocity;

        Vec3 look = thrower.getLookAngle();
        setPos(
                thrower.getX() + look.x * 0.6,
                thrower.getEyeY() - 0.1,
                thrower.getZ() + look.z * 0.6
        );

        prevPhysX = getX();
        prevPhysY = getY();
        prevPhysZ = getZ();

        float speed = (float) initialVelocity.length();
        angularVelX = speed * 14f;
        angularVelY = speed * 4f;
        angularVelZ = speed * 9f;
    }

    // ── SynchedEntityData ─────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SPIN_X, 0f);
        builder.define(SPIN_Y, 0f);
        builder.define(SPIN_Z, 0f);
        builder.define(PREV_SPIN_X, 0f);
        builder.define(PREV_SPIN_Y, 0f);
        builder.define(PREV_SPIN_Z, 0f);
        builder.define(PREV_PHYS_X, 0f);
        builder.define(PREV_PHYS_Y, 0f);
        builder.define(PREV_PHYS_Z, 0f);
        builder.define(SURFACE_SYNC, false);
        builder.define(RESTING_SYNC, false);
        builder.define(BOUNCE_SYNC, 0);
        builder.define(SYNC_VEL_X, 0f);
        builder.define(SYNC_VEL_Y, 0f);
        builder.define(SYNC_VEL_Z, 0f);
        builder.define(SYNC_ANG_VEL_X, 0f);
        builder.define(SYNC_ANG_VEL_Y, 0f);
        builder.define(SYNC_ANG_VEL_Z, 0f);
    }

    // ── Dead reckoning — hooks de red ─────────────────────────────────────────

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (level().isClientSide) {
            if (!clientSimInitialized) {
                clientSimX = x;
                clientSimY = y;
                clientSimZ = z;
                setPos(x, y, z);
                clientSimInitialized = true;
            } else if (entityData.get(RESTING_SYNC) || entityData.get(SURFACE_SYNC)) {
                clientSimX = x;
                clientSimY = y;
                clientSimZ = z;
                setPos(x, y, z);
                clientSimVel = Vec3.ZERO;
            } else {
                clientSimX = Mth.lerp(0.3, clientSimX, x);
                clientSimY = Mth.lerp(0.3, clientSimY, y);
                clientSimZ = Mth.lerp(0.3, clientSimZ, z);
            }
            clientSimVel = getSyncedVelocity();
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide) {
            if (key == SYNC_VEL_X || key == SYNC_VEL_Y || key == SYNC_VEL_Z) {
                clientSimVel = getSyncedVelocity();
                if (!clientSimInitialized) {
                    clientSimX = getX();
                    clientSimY = getY();
                    clientSimZ = getZ();
                    clientSimInitialized = true;
                }
            }
            if (key == RESTING_SYNC && entityData.get(RESTING_SYNC)) {
                clientSimVel = Vec3.ZERO;
            }
        }
    }

    // ── Tick principal ────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            prevPhysX = getX();
            prevPhysY = getY();
            prevPhysZ = getZ();
            prevVelocity = velocity;

            entityData.set(PREV_PHYS_X, (float) prevPhysX);
            entityData.set(PREV_PHYS_Y, (float) prevPhysY);
            entityData.set(PREV_PHYS_Z, (float) prevPhysZ);
        }

        age++;
        if (age > MAX_AGE) {
            onExplosion();
            return;
        }

        if (level().isClientSide) {
            tickRotationClient();
            return;
        }

        if (isResting) {
            tickRestingPhysics();
            tickRotationServer();
            onPhysicsTick();
            return;
        }

        // 1. Gravedad
        velocity = velocity.add(0, -GRAVITY, 0);

        // 2. Drag del aire
        double drag = 1.0 - AIR_DRAG;
        velocity = new Vec3(velocity.x * drag, velocity.y * drag, velocity.z * drag);

        // 3. Clamp de velocidad
        velocity = clampVelocity(velocity);

        // 4. Mover con resolución de colisión por eje (swept)
        Vec3 resolved = resolveCollision(velocity);

        // 5. Actualizar posición
        setPos(getX() + resolved.x, getY() + resolved.y, getZ() + resolved.z);

        // 6. Fricción en el suelo
        if (onSurface) {
            velocity = new Vec3(
                    velocity.x * GROUND_FRICTION,
                    velocity.y,
                    velocity.z * GROUND_FRICTION
            );
            if (Math.abs(velocity.x) < 0.008) velocity = new Vec3(0, velocity.y, velocity.z);
            if (Math.abs(velocity.z) < 0.008) velocity = new Vec3(velocity.x, velocity.y, 0);
        }

        // 7. Detectar reposo
        double speed = velocity.length();
        if (onSurface && speed < REST_THRESHOLD) {
            restTicks++;
            if (restTicks > 5) {
                velocity    = Vec3.ZERO;
                angularVelX = 0;
                angularVelY = 0;
                angularVelZ = 0;
                isResting   = true;
                entityData.set(RESTING_SYNC, true);
            }
        } else {
            restTicks = 0;
        }

        // 8. Rotación
        tickRotationServer();

        // 9. Hook para subclases
        onPhysicsTick();
    }

    // ── Clamp de velocidad ────────────────────────────────────────────────────

    private Vec3 clampVelocity(Vec3 vel) {
        double len = vel.length();
        if (len > MAX_SPEED) {
            return vel.scale(MAX_SPEED / len);
        }
        return vel;
    }

    // ── Física en estado resting ──────────────────────────────────────────────

    private void tickRestingPhysics() {
        AABB box   = getBoundingBox();
        double probe = GRAVITY + 0.05;
        if (level().noCollision(this, box.move(0, -probe, 0))) {
            isResting = false;
            restTicks = 0;
            onSurface = false;
            velocity  = Vec3.ZERO;
            entityData.set(RESTING_SYNC, false);
            entityData.set(SYNC_VEL_X, 0f);
            entityData.set(SYNC_VEL_Y, 0f);
            entityData.set(SYNC_VEL_Z, 0f);
        }
    }

    // ── Resolución de colisión con swept AABB ─────────────────────────────────

    private Vec3 resolveCollision(Vec3 movement) {
        AABB box = getBoundingBox();
        double mx = movement.x;
        double my = movement.y;
        double mz = movement.z;

        boolean hitX = false, hitY = false, hitZ = false;

        AABB sweptY = box.expandTowards(0, my, 0);
        if (!level().noCollision(this, sweptY)) {
            my   = findFreeY(box, my);
            hitY = true;
        }
        box = box.move(0, my, 0);

        AABB sweptX = box.expandTowards(mx, 0, 0);
        if (!level().noCollision(this, sweptX)) {
            mx   = findFreeX(box, mx);
            hitX = true;
        }
        box = box.move(mx, 0, 0);

        AABB sweptZ = box.expandTowards(0, 0, mz);
        if (!level().noCollision(this, sweptZ)) {
            mz   = findFreeZ(box, mz);
            hitZ = true;
        }

        onSurface = hitY || hitX || hitZ;

        if (hitX || hitY || hitZ) {
            boolean anyBounce = false;

            if (hitY && Math.abs(velocity.y) > REST_THRESHOLD) {
                velocity   = new Vec3(velocity.x, -velocity.y * RESTITUTION, velocity.z);
                anyBounce  = true;

                float impact = (float) Math.min(
                        36.0,
                        Math.abs(velocity.y) * 170.0
                                + Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 45.0
                );
                angularVelX += (float) (-velocity.z * 14.0) + impact * 0.25f;
                angularVelZ += (float) (velocity.x  * 14.0) + impact * 0.25f;
            } else if (hitY) {
                velocity = new Vec3(velocity.x, 0, velocity.z);
            }

            if (hitX && Math.abs(velocity.x) > REST_THRESHOLD) {
                velocity  = new Vec3(-velocity.x * RESTITUTION, velocity.y, velocity.z);
                anyBounce = true;
                angularVelY += (float) (Math.abs(velocity.x) * 28.0f);
            } else if (hitX) {
                velocity = new Vec3(0, velocity.y, velocity.z);
            }

            if (hitZ && Math.abs(velocity.z) > REST_THRESHOLD) {
                velocity  = new Vec3(velocity.x, velocity.y, -velocity.z * RESTITUTION);
                anyBounce = true;
                angularVelY += (float) (Math.abs(velocity.z) * 28.0f);
            } else if (hitZ) {
                velocity = new Vec3(velocity.x, velocity.y, 0);
            }

            if (anyBounce) {
                bounceCount++;
                onBounce(bounceCount);

                float bounceDamp = Math.max(0.22f, 1f - bounceCount * 0.18f);
                angularVelX *= bounceDamp;
                angularVelY *= bounceDamp;
                angularVelZ *= bounceDamp;
            }
        }

        return new Vec3(mx, my, mz);
    }

    // ── Búsqueda de posición libre por eje ────────────────────────────────────

    private double findFreeY(AABB box, double dy) {
        double step  = dy > 0 ? 0.002 : -0.002;
        double moved = 0;
        while (Math.abs(moved) < Math.abs(dy)) {
            double next = moved + step;
            if (!level().noCollision(this, box.move(0, next, 0))) break;
            moved = next;
        }
        return moved;
    }

    private double findFreeX(AABB box, double dx) {
        double step  = dx > 0 ? 0.002 : -0.002;
        double moved = 0;
        while (Math.abs(moved) < Math.abs(dx)) {
            double next = moved + step;
            if (!level().noCollision(this, box.move(next, 0, 0))) break;
            moved = next;
        }
        return moved;
    }

    private double findFreeZ(AABB box, double dz) {
        double step  = dz > 0 ? 0.002 : -0.002;
        double moved = 0;
        while (Math.abs(moved) < Math.abs(dz)) {
            double next = moved + step;
            if (!level().noCollision(this, box.move(0, 0, next))) break;
            moved = next;
        }
        return moved;
    }

    // ── Rotación ──────────────────────────────────────────────────────────────

    private void tickRotationServer() {
        prevSpinX = entityData.get(SPIN_X);
        prevSpinY = entityData.get(SPIN_Y);
        prevSpinZ = entityData.get(SPIN_Z);

        entityData.set(PREV_SPIN_X, prevSpinX);
        entityData.set(PREV_SPIN_Y, prevSpinY);
        entityData.set(PREV_SPIN_Z, prevSpinZ);

        double speed = velocity.length();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        float brakeFactor = onSurface ? (float) Math.max(0.72, 0.94 - (1.0 - speed / REST_THRESHOLD) * 0.22) : 0.94f;
        angularVelX *= brakeFactor;
        angularVelY *= brakeFactor;
        angularVelZ *= brakeFactor;

        if (onSurface && horizontalSpeed > 0.001) {
            float rollDegrees = (float) ((horizontalSpeed / Math.max(0.12f, radius)) * 57.2958f);

            float targetX = (float) (-velocity.z * rollDegrees * 1.25f);
            float targetZ = (float) (velocity.x  * rollDegrees * 1.25f);

            angularVelX = Mth.lerp(0.28f, angularVelX, targetX);
            angularVelZ = Mth.lerp(0.28f, angularVelZ, targetZ);
            angularVelY += (float) (horizontalSpeed * 0.55f);
        }

        if (onSurface && speed < REST_THRESHOLD * 6.0) {
            float torqueStrength = (float) (1.0 - speed / (REST_THRESHOLD * 6.0));
            torqueStrength = torqueStrength * torqueStrength;

            float k_gravity = 1.6f * torqueStrength;

            float spinXRad = (float) Math.toRadians(entityData.get(SPIN_X));
            float torqueX  = -k_gravity * (float) Math.sin(2.0 * spinXRad);
            angularVelX += torqueX;

            float spinZRad = (float) Math.toRadians(entityData.get(SPIN_Z));
            float torqueZ  = -k_gravity * (float) Math.sin(2.0 * spinZRad);
            angularVelZ += torqueZ;
        }

        if (onSurface && speed < REST_THRESHOLD * 2.0
                && Math.abs(angularVelX) < 0.5f && Math.abs(angularVelZ) < 0.5f) {

            float snapStrength = 0.08f;

            float curX      = entityData.get(SPIN_X);
            float targetX   = nearestStableAngle(curX);
            float deltaX    = Mth.rotLerp(snapStrength, curX, targetX) - curX;
            angularVelX    += deltaX;

            float curZ      = entityData.get(SPIN_Z);
            float targetZ   = nearestStableAngle(curZ);
            float deltaZ    = Mth.rotLerp(snapStrength, curZ, targetZ) - curZ;
            angularVelZ    += deltaZ;
        }

        float rx = entityData.get(SPIN_X) + angularVelX;
        float ry = entityData.get(SPIN_Y) + angularVelY;
        float rz = entityData.get(SPIN_Z) + angularVelZ;

        entityData.set(SPIN_X, rx % 360f);
        entityData.set(SPIN_Y, ry % 360f);
        entityData.set(SPIN_Z, rz % 360f);

        if (!level().isClientSide) {
            entityData.set(SURFACE_SYNC,   onSurface);
            entityData.set(BOUNCE_SYNC,    bounceCount);
            entityData.set(SYNC_VEL_X,     (float) velocity.x);
            entityData.set(SYNC_VEL_Y,     (float) velocity.y);
            entityData.set(SYNC_VEL_Z,     (float) velocity.z);
            entityData.set(SYNC_ANG_VEL_X, angularVelX);
            entityData.set(SYNC_ANG_VEL_Y, angularVelY);
            entityData.set(SYNC_ANG_VEL_Z, angularVelZ);
        }
    }

    private void tickRotationClient() {
        clientPrevSpinX = clientSpinX;
        clientPrevSpinY = clientSpinY;
        clientPrevSpinZ = clientSpinZ;

        float avx = entityData.get(SYNC_ANG_VEL_X);
        float avy = entityData.get(SYNC_ANG_VEL_Y);
        float avz = entityData.get(SYNC_ANG_VEL_Z);

        clientSpinX = (clientSpinX + avx) % 360f;
        clientSpinY = (clientSpinY + avy) % 360f;
        clientSpinZ = (clientSpinZ + avz) % 360f;

        if (entityData.get(RESTING_SYNC)) {
            clientSpinX = Mth.rotLerp(0.15f, clientSpinX, nearestStableAngle(clientSpinX));
            clientSpinZ = Mth.rotLerp(0.15f, clientSpinZ, nearestStableAngle(clientSpinZ));
        }

        boolean onSurfaceClient = entityData.get(SURFACE_SYNC);
        if (clientSimInitialized && !entityData.get(RESTING_SYNC) && !onSurfaceClient) {
            clientSimX += clientSimVel.x;
            clientSimY += clientSimVel.y;
            clientSimZ += clientSimVel.z;

            clientSimVel = clientSimVel.add(0, -GRAVITY, 0);
            double drag  = 1.0 - AIR_DRAG;
            clientSimVel = new Vec3(
                    clientSimVel.x * drag,
                    clientSimVel.y * drag,
                    clientSimVel.z * drag
            );

            setPos(clientSimX, clientSimY, clientSimZ);
        }
    }

    private static float nearestStableAngle(float angle) {
        angle = ((angle % 360f) + 360f) % 360f;
        float rounded = Math.round(angle / 90f) * 90f;
        return rounded % 360f;
    }

    // ── Getters para el renderer ──────────────────────────────────────────────

    public float getSpinX()                      { return entityData.get(SPIN_X); }
    public float getSpinY()                      { return entityData.get(SPIN_Y); }
    public float getSpinZ()                      { return entityData.get(SPIN_Z); }

    public float getSpinX(float p) { return Mth.rotLerp(p, entityData.get(PREV_SPIN_X), getSpinX()); }
    public float getSpinY(float p) { return Mth.rotLerp(p, entityData.get(PREV_SPIN_Y), getSpinY()); }
    public float getSpinZ(float p) { return Mth.rotLerp(p, entityData.get(PREV_SPIN_Z), getSpinZ()); }

    public float getClientSpinX(float p) { return Mth.rotLerp(p, clientPrevSpinX, clientSpinX); }
    public float getClientSpinY(float p) { return Mth.rotLerp(p, clientPrevSpinY, clientSpinY); }
    public float getClientSpinZ(float p) { return Mth.rotLerp(p, clientPrevSpinZ, clientSpinZ); }

    public Vec3  getPhysicsVelocity()            { return velocity; }
    public Vec3  getPreviousPhysicsVelocity()     { return prevVelocity; }
    public boolean isOnSurface()                 { return entityData.get(SURFACE_SYNC); }

    /** True si la granada está en reposo completo (server-side real value). */
    public boolean isResting()                   { return isResting; }

    public LivingEntity getThrower()             { return thrower; }
    public double getPrevPhysicsX()              { return entityData.get(PREV_PHYS_X); }
    public double getPrevPhysicsY()              { return entityData.get(PREV_PHYS_Y); }
    public double getPrevPhysicsZ()              { return entityData.get(PREV_PHYS_Z); }
    public float  getVisualRadius()              { return radius; }
    public int    getBounceCount()               { return entityData.get(BOUNCE_SYNC); }

    public Vec3 getSyncedVelocity() {
        return new Vec3(
                entityData.get(SYNC_VEL_X),
                entityData.get(SYNC_VEL_Y),
                entityData.get(SYNC_VEL_Z)
        );
    }

    // ── Hooks para subclases ──────────────────────────────────────────────────

    protected void onPhysicsTick() {}
    protected void onBounce(int bounceNumber) {}
    protected void onExplosion() { this.discard(); }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age         = tag.getInt("GrenadeAge");
        velocity    = new Vec3(tag.getDouble("VelX"), tag.getDouble("VelY"), tag.getDouble("VelZ"));
        angularVelX = tag.getFloat("AngVelX");
        angularVelY = tag.getFloat("AngVelY");
        angularVelZ = tag.getFloat("AngVelZ");
        prevSpinX   = tag.getFloat("SpinXPrev");
        prevSpinY   = tag.getFloat("SpinYPrev");
        prevSpinZ   = tag.getFloat("SpinZPrev");
        prevPhysX   = tag.getDouble("PrevPhysX");
        prevPhysY   = tag.getDouble("PrevPhysY");
        prevPhysZ   = tag.getDouble("PrevPhysZ");
        onSurface   = tag.getBoolean("OnSurface");
        bounceCount = tag.getInt("BounceCount");
        isResting   = tag.getBoolean("IsResting");

        entityData.set(PREV_PHYS_X, (float) prevPhysX);
        entityData.set(PREV_PHYS_Y, (float) prevPhysY);
        entityData.set(PREV_PHYS_Z, (float) prevPhysZ);
        entityData.set(SURFACE_SYNC, onSurface);
        entityData.set(RESTING_SYNC, isResting);
        entityData.set(BOUNCE_SYNC, bounceCount);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("GrenadeAge",    age);
        tag.putDouble("VelX",       velocity.x);
        tag.putDouble("VelY",       velocity.y);
        tag.putDouble("VelZ",       velocity.z);
        tag.putFloat("AngVelX",     angularVelX);
        tag.putFloat("AngVelY",     angularVelY);
        tag.putFloat("AngVelZ",     angularVelZ);
        tag.putFloat("SpinXPrev",   prevSpinX);
        tag.putFloat("SpinYPrev",   prevSpinY);
        tag.putFloat("SpinZPrev",   prevSpinZ);
        tag.putDouble("PrevPhysX",  prevPhysX);
        tag.putDouble("PrevPhysY",  prevPhysY);
        tag.putDouble("PrevPhysZ",  prevPhysZ);
        tag.putBoolean("OnSurface", onSurface);
        tag.putBoolean("IsResting", isResting);
        tag.putInt("BounceCount",   bounceCount);
    }

    // ── GeoAnimatable ─────────────────────────────────────────────────────────

    private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }

    @Override
    public double getTick(Object o) { return ((Entity) o).tickCount; }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 4096; }
}