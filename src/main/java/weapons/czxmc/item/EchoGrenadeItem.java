package weapons.czxmc.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import weapons.czxmc.entity.EchoGrenadeEntity;

/**
 * Echo Grenade — item lanzable con sistema de carga por mantener presionado.
 *
 * FIX: El lanzamiento ya no ocurre al presionar click derecho.
 *   1. Presionar RMB → startUsingItem (empieza a cargar silenciosamente).
 *   2. Mantener RMB  → la carga aumenta hasta MAX_CHARGE_TICKS (1.5 segundos).
 *   3. Soltar RMB    → releaseUsing() lanza la granada con la potencia acumulada.
 *
 * La velocidad de lanzamiento escala de 25% (tap inmediato, mín. 3 ticks)
 * a 100% (carga completa de MAX_CHARGE_TICKS). Esto elimina el problema
 * de "apenas apretás se tira".
 */
public class EchoGrenadeItem extends Item implements GeoItem {

    private static final float BASE_THROW_SPEED  = 2.2f;

    /** Ticks para alcanzar la carga máxima (60 ticks = 3 segundos). */
    private static final int MAX_CHARGE_TICKS = 60;

    /** Velocidad mínima de lanzamiento (tap rápido), como fracción de BASE_THROW_SPEED. */
    private static final float MIN_CHARGE_FACTOR = 0.15f;

    /** Mínimo de ticks que hay que mantener para que se lance. Menos = no dispara. */
    private static final int MIN_HOLD_TICKS = 1;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");

    public EchoGrenadeItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    // ── Use-animation ─────────────────────────────────────────────────────────

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        // Suficientemente grande para que nunca se alcance por tiempo;
        // el lanzamiento ocurre siempre en releaseUsing().
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // BOW para que aparezca el indicador de carga (arco) al presionar click
        return UseAnim.BOW;
    }

    // ── Iniciar carga al presionar RMB ────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // ── Lanzamiento al soltar RMB ─────────────────────────────────────────────

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof Player player)) return;
        if (level.isClientSide) return;

        int held = getUseDuration(stack, living) - timeLeft;

        // Ignorar taps demasiado cortos (accidentales)
        if (held < MIN_HOLD_TICKS) return;

        // Progreso de carga: 0.0 (tap) → 1.0 (carga completa)
        float chargeProgress = Math.min(1.0f, (float) held / MAX_CHARGE_TICKS);

        // Velocidad final = factor mínimo + escala de potencia
        float speedFactor = MIN_CHARGE_FACTOR + (1.0f - MIN_CHARGE_FACTOR) * chargeProgress;

        // Ajuste por pitch: apuntar arriba = más lejos, abajo = menos
        float pitchStrength = 1.0f + (-player.getXRot() / 90.0f) * 0.55f;
        pitchStrength = Mth.clamp(pitchStrength, 0.55f, 1.65f);

        float speed = BASE_THROW_SPEED * pitchStrength * speedFactor;

        Vec3 look     = living.getLookAngle();
        Vec3 velocity = look.scale(speed);

        EchoGrenadeEntity grenade = new EchoGrenadeEntity(level, player, velocity);
        level.addFreshEntity(grenade);

        if (!player.isCreative()) {
            stack.shrink(1);
        }

        // Cooldown de 10 segundos para evitar spam
        player.getCooldowns().addCooldown(this, 200);
    }

    // ── GeoItem ───────────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 2, state -> {
            state.setAnimation(ANIM_IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}