package weapons.czxmc.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import weapons.czxmc.ModEntities;
import weapons.czxmc.ModParticles;
import weapons.czxmc.item.GunItem;

import java.util.List;

public class GunBulletEntity extends ThrowableItemProjectile {

    private static final double CUSTOM_GRAVITY    = 0.008;
    private static final double IMPACT_RADIUS     = 4.0;
    private static final double KNOCKBACK_FORCE   = 4.0;
    private static final float  MAX_DAMAGE        = 20.0f;
    private static final float  MIN_DAMAGE_FACTOR = 0.4f;

    public GunBulletEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    public GunBulletEntity(Level level, LivingEntity shooter) {
        super(ModEntities.GUN_BULLET, shooter, level);
        this.setNoGravity(true);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.GUNPOWDER;
    }

    @Override
    public void tick() {
        super.tick();

        Vec3 mov = getDeltaMovement();
        setDeltaMovement(mov.x, mov.y - CUSTOM_GRAVITY, mov.z);

        if (!level().isClientSide) {
            spawnTrailParticle();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (!level().isClientSide) impact(true);
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        if (!level().isClientSide) impact(false);
    }

    private void spawnTrailParticle() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        Vec3 vel = getDeltaMovement();
        if (vel.lengthSqr() < 0.000001) return;

        Vec3 backPos = position().subtract(vel.normalize().scale(0.20));

        serverLevel.sendParticles(
                ModParticles.GUN_BULLET_TRAIL,
                backPos.x, backPos.y, backPos.z,
                1,
                0.02, 0.02, 0.02,
                0.0
        );
    }

    private void impact(boolean hitEntity) {
        Vec3 impactPos = this.position();

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.GUST_EMITTER_LARGE,
                    getX(), getY(), getZ(),
                    1, 0, 0, 0, 0
            );

            serverLevel.sendParticles(
                    ParticleTypes.EXPLOSION,
                    getX(), getY(), getZ(),
                    1, 0, 0, 0, 0
            );

            serverLevel.sendParticles(
                    ParticleTypes.CRIT,
                    getX(), getY(), getZ(),
                    12, 0.3, 0.3, 0.3, 0.18
            );

            serverLevel.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    getX(), getY(), getZ(),
                    5, 0.4, 0.4, 0.4, 0.02
            );
        }

        AABB area = new AABB(
                getX() - IMPACT_RADIUS, getY() - IMPACT_RADIUS, getZ() - IMPACT_RADIUS,
                getX() + IMPACT_RADIUS, getY() + IMPACT_RADIUS, getZ() + IMPACT_RADIUS
        );

        List<LivingEntity> nearby = level().getEntitiesOfClass(LivingEntity.class, area);
        boolean hitSoundPlayed = false;

        for (LivingEntity entity : nearby) {
            double dist = entity.position().distanceTo(impactPos);
            if (dist > IMPACT_RADIUS) continue;

            double proximity = 1.0 - (dist / IMPACT_RADIUS);
            float damageFactor = (float) (MIN_DAMAGE_FACTOR + (1.0 - MIN_DAMAGE_FACTOR) * proximity);
            boolean isOwner = (entity == getOwner());

            if (!isOwner) {
                float damage = MAX_DAMAGE * damageFactor;
                entity.hurt(damageSources().thrown(this, getOwner()), damage);

                if (!hitSoundPlayed && getOwner() instanceof ServerPlayer sp) {
                    GunItem.playHitSound(sp);
                    hitSoundPlayed = true;
                }
            }

            Vec3 rawDir = entity.position().subtract(impactPos);
            Vec3 dir;
            if (rawDir.lengthSqr() < 0.0001) {
                dir = new Vec3(0, 1, 0);
            } else {
                rawDir = rawDir.normalize();
                double biasedY = Math.max(rawDir.y, 0.4);
                dir = new Vec3(rawDir.x * 0.8, biasedY, rawDir.z * 0.8).normalize();
            }

            double force = KNOCKBACK_FORCE * proximity;
            Vec3 currVel = entity.getDeltaMovement();
            Vec3 newVel = new Vec3(
                    currVel.x + dir.x * force,
                    Math.max(currVel.y, dir.y * force),
                    currVel.z + dir.z * force
            );

            entity.setDeltaMovement(newVel);
            entity.hurtMarked = true;

            if (entity instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetEntityMotionPacket(sp.getId(), newVel));
            }
        }

        this.discard();
    }
}