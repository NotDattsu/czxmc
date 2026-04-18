package weapons.czxmc.item;

import net.minecraft.server.level.ServerLevel;
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
import weapons.czxmc.entity.AnchorHookEntity;

import java.util.List;

/**
 * Item del Grapple Hook (Ancla de Abordaje).
 *
 * ── Uso ───────────────────────────────────────────────────────────────────
 *   Mantener click derecho → carga la fuerza de lanzamiento (hasta 1 segundo)
 *   Soltar click derecho   → lanza el ancla con la fuerza acumulada
 *   Click derecho (con ancla activa) → retracta inmediatamente
 *
 * ── Distancia y fuerza (FIX #6) ──────────────────────────────────────────
 *   MIN_THROW_SPEED aumentado de 1.5 → 2.5 para que incluso un tap corto
 *   sea usable. La escala de power arranca desde 0 (no desde 0.05) para que
 *   la curva sea lineal y predecible: tap rápido = distancia corta,
 *   carga completa = distancia larga.
 */
public class AnchorItem extends Item implements GeoItem {

    // ── Constantes de lanzamiento ─────────────────────────────────────────────
    /** FIX #6: velocidad mínima aumentada de 1.5 a 2.5 — un tap rápido ya es usable */
    private static final float MIN_THROW_SPEED  = 2.5f;
    /** Velocidad máxima (carga completa) */
    private static final float MAX_THROW_SPEED  = 4.5f;
    /** Ticks necesarios para carga completa (20 ticks = 1 segundo) */
    private static final int   MAX_CHARGE_TICKS = 20;
    /** Ticks de vuelo mínimos (distancia corta) */
    private static final int   MIN_FLY_TICKS    = 25;
    /** Ticks de vuelo máximos (distancia larga, a full carga) */
    private static final int   MAX_FLY_TICKS    = 80;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");

    public AnchorItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /**
     * Al hacer click:
     *  - Si ya hay un ancla activa → retractar
     *  - Si no hay ancla → empezar a cargar (mantener presionado)
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            AnchorHookEntity existing = findPlayerHook(level, player);
            if (existing != null) {
                existing.retract();
                return InteractionResultHolder.success(stack);
            }
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * Se llama cuando el jugador suelta el botón antes de que expire el uso.
     * FIX #6: power ahora arranca desde 0.0 (no 0.05) para una curva lineal real.
     * Incluso un tap rapidísimo (1 tick) da ~5% de rango, que es suficiente para
     * ganchos cercanos. A mayor carga, mayor velocidad Y mayor distancia.
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (!(entity instanceof Player player) || level.isClientSide) return;

        int ticksHeld = getUseDuration(stack, entity) - timeCharged;

        // FIX #6: clamp desde 0.0 (no 0.05) — la fuerza mínima ya es usable con MIN_THROW_SPEED=2.5
        float power    = Mth.clamp(ticksHeld / (float) MAX_CHARGE_TICKS, 0.0f, 1.0f);
        float speed    = Mth.lerp(power, MIN_THROW_SPEED, MAX_THROW_SPEED);
        int   flyTicks = (int) Mth.lerp(power, MIN_FLY_TICKS, MAX_FLY_TICKS);

        Vec3 vel  = player.getLookAngle().scale(speed);
        AnchorHookEntity hook = new AnchorHookEntity(level, player, vel, flyTicks);
        level.addFreshEntity(hook);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    private static AnchorHookEntity findPlayerHook(Level level, Player player) {
        if (!(level instanceof ServerLevel sl)) return null;

        List<AnchorHookEntity> hooks = sl.getEntitiesOfClass(
            AnchorHookEntity.class,
            player.getBoundingBox().inflate(200),
            e -> e.getThrower() == player && !e.isRemoved()
        );

        return hooks.isEmpty() ? null : hooks.get(0);
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
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
