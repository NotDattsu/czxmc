package weapons.czxmc.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import weapons.czxmc.CZXMCWeapons;
import weapons.czxmc.entity.GunBulletEntity;
import weapons.czxmc.network.GunSoundPayload;
import weapons.czxmc.sound.ModSounds;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class GunItem extends Item implements GeoItem {

    public static final Set<UUID> debugPlayers = new HashSet<>();

    // ── Cooldowns ─────────────────────────────────────────────────────────────
    public static final int SHOT_COOLDOWN           = 12;
    public static final int RELOAD_COOLDOWN_BASE    = 70;
    public static final int RELOAD_COOLDOWN_HALF_AMMO = RELOAD_COOLDOWN_BASE / 2; // 35 ticks

    /** Cooldown mínimo de recarga.
     *  Debe ser > SHOT_COOLDOWN para evitar race conditions de red donde un
     *  paquete de disparo llega al servidor antes de que el cliente reciba el
     *  sync del cooldown (especialmente con la mejora reload_quarter).
     *  20 ticks = 1 segundo — el suficiente buffer para cualquier RTT razonable.
     */
    private static final int MIN_RELOAD_COOLDOWN    = 20;

    public static final int MAX_ROUNDS = 2;

    /** Duración de la animación de recarga en segundos (del gun.animation.json). */
    private static final float RELOAD_ANIM_DURATION_SECS = 2.375f;

    // ── Propulsión ────────────────────────────────────────────────────────────
    private static final double PROPULSION_AIR          = 1.9;
    private static final double PROPULSION_GROUND_DOWN  = 0.55;
    private static final double PROPULSION_REDUCED      = 0.65;

    private static final double VERTICAL_RATIO      = 0.55;
    private static final double GROUND_VERTICAL_MIN = 0.57;
    private static final double DOWN_THRESHOLD      = -0.5;
    private static final int    SLOW_FALLING_TICKS  = 40;

    // ── Volúmenes ─────────────────────────────────────────────────────────────
    private static final float VOL_SHOOT   = 0.60f;
    private static final float VOL_RELOAD  = 0.55f;
    private static final float VOL_HIT     = 0.50f;
    private static final float VOL_UPGRADE = 0.60f;

    // ── Pitch (variación pequeña para naturalidad) ────────────────────────────
    private static final float PITCH_SHOOT_BASE  = 0.96f;
    private static final float PITCH_SHOOT_RANGE = 0.08f;  // 0.96 – 1.04
    private static final float PITCH_RELOAD_BASE = 0.98f;
    private static final float PITCH_RELOAD_RANGE= 0.04f;  // 0.98 – 1.02

    // ── Hold ticks para FadingGunSoundInstance ────────────────────────────────
    private static final int HOLD_TICKS_SHOOT  = 10;
    // Para reload se calcula dinámicamente = reloadTicks - (FADE_IN + FADE_OUT)

    // ── NBT ───────────────────────────────────────────────────────────────────
    private static final String NBT_LOADED_ROUNDS = "LoadedRounds";

    private static final Random SOUND_RNG = new Random();

    // ── GeckoLib ─────────────────────────────────────────────────────────────
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final RawAnimation ANIM_IDLE   = RawAnimation.begin().thenLoop("idle");
    // thenPlay → vuelve a STOP al terminar → permite re-triggear en disparos sucesivos
    public static final RawAnimation ANIM_SHOOT  = RawAnimation.begin().thenPlay("shot");
    public static final RawAnimation ANIM_RELOAD = RawAnimation.begin().thenPlay("reload");

    /**
     * Velocidad de la animación de recarga, sincronizada via GunSoundPayload.
     * Volatile: escrito desde el hilo de red, leído desde el hilo de render.
     * Esta instancia es singleton, así que un campo de instancia es seguro.
     * Formula: animDurationSecs / (cooldownTicks / 20f)
     * Ejemplo: cooldown 70t → speed ≈ 0.68 (más lenta que diseñada, da "peso")
     *          cooldown 20t → speed ≈ 2.38 (recarga rápida con upgrade)
     */
    public volatile float reloadAnimSpeed = 1.0f;

    // ── Estado de animación ───────────────────────────────────────────────────
    public enum AnimationState { IDLE, FIRING, RELOADING, EMPTY }

    public static AnimationState getAnimationState(ItemStack stack) {
        if (getLoadedRounds(stack) == 0) return AnimationState.EMPTY;
        return AnimationState.IDLE;
    }

    public static void onAnimationStart(AnimationState state) {}
    public static void onAnimationEnd(AnimationState state) {}

    public GunItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    // ── USE — Disparar / Recargar ─────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.fail(stack);
        }

        int loaded = getLoadedRounds(stack);

        if (loaded == 0) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                handleReloadTrigger(sp, stack, level);
            }
            return InteractionResultHolder.consume(stack);
        }

        // ── Disparo ───────────────────────────────────────────────────────────
        if (!level.isClientSide && player instanceof ServerPlayer sp) {

            boolean isCondition3 = isCondition3(sp, stack);
            float   pitch        = PITCH_SHOOT_BASE + SOUND_RNG.nextFloat() * PITCH_SHOOT_RANGE;

            // Sonido para jugadores cercanos (excluye al shooter para evitar doble sonido)
            if (ModSounds.GUN_SHOOT != null && ModSounds.GUN_SHOOT_ALT != null) {
                var soundForWorld = isCondition3 ? ModSounds.GUN_SHOOT_ALT : ModSounds.GUN_SHOOT;
                level.playSound(sp, sp.getX(), sp.getY(), sp.getZ(),
                        soundForWorld, SoundSource.PLAYERS, VOL_SHOOT, pitch);
            }

            // Sonido con fade in/out para el shooter
            ResourceLocation shootSoundId = isCondition3
                    ? ModSounds.GUN_SHOOT_ALT.getLocation()
                    : ModSounds.GUN_SHOOT.getLocation();
            ServerPlayNetworking.send(sp,
                    new GunSoundPayload(shootSoundId, VOL_SHOOT, pitch, HOLD_TICKS_SHOOT, 1.0f));

            if (ModItems.hasGunSlowFalling(sp.getUUID())) {
                sp.removeEffect(MobEffects.SLOW_FALLING);
                ModItems.clearGunSlowFalling(sp.getUUID());
            }

            applyPropulsion(player, stack);
            spawnBullet(level, player, stack);

            loaded--;
            setLoadedRounds(stack, loaded);
            triggerAnimation(player, stack, "shot");

            if (loaded == 0) {
                handleReloadTrigger(sp, stack, level);
            } else {
                player.getCooldowns().addCooldown(stack.getItem(), SHOT_COOLDOWN);
            }
        }

        return InteractionResultHolder.consume(stack);
    }

    // ── Condición 3 ───────────────────────────────────────────────────────────

    /**
     * True cuando la propulsión del próximo disparo será PROPULSION_REDUCED.
     * Usado para elegir el sonido alternativo (más contenido).
     */
    private boolean isCondition3(ServerPlayer sp, ItemStack stack) {
        Vec3    look      = sp.getLookAngle();
        boolean onGround  = sp.onGround();
        boolean crouching = sp.isCrouching();

        if (onGround) {
            // Cond 1 (suelo + abajo) → NO es cond-3; suelo sin ángulo → SÍ
            return look.y >= DOWN_THRESHOLD;
        } else if (crouching) {
            return true;
        } else {
            int maxEff = ModItems.MAX_EFFECTIVE_AIR_SHOTS + GunUpgrade.getBonusAirShots(stack);
            return ModItems.getAirShotsUsed(sp.getUUID()) >= maxEff;
        }
    }

    // ── Recarga ───────────────────────────────────────────────────────────────

    private void handleReloadTrigger(ServerPlayer sp, ItemStack stack, Level level) {
        int available = countGunpowder(sp);

        if (available == 0) {
            sp.displayClientMessage(
                Component.translatable("item.cz-x-mc-weapons.gun.no_ammo")
                    .withStyle(ChatFormatting.RED), true);
            sp.getCooldowns().addCooldown(stack.getItem(), MIN_RELOAD_COOLDOWN);
            return;
        }

        int capacity  = Math.min(available, MAX_ROUNDS);
        int baseTicks = (capacity < MAX_ROUNDS) ? RELOAD_COOLDOWN_HALF_AMMO : RELOAD_COOLDOWN_BASE;

        // Aplicar multiplicador de mejora, pero nunca bajar de MIN_RELOAD_COOLDOWN.
        // MIN_RELOAD_COOLDOWN (20 ticks) previene el race condition de red con cooldowns
        // muy pequeños (ej: quarter upgrade + media munición = 8 ticks sin el cap).
        int reloadTicks = Math.max(MIN_RELOAD_COOLDOWN,
                (int)(baseTicks * GunUpgrade.getReloadMultiplier(stack)));

        sp.getCooldowns().addCooldown(stack.getItem(), reloadTicks);

        // Velocidad de animación: escala para que la animación termine exactamente
        // cuando vence el cooldown. animSpeed > 1 = más rápida, < 1 = más lenta.
        // Formula: duracionAnimSecs / (reloadTicks / 20f)
        float animSpeed = (RELOAD_ANIM_DURATION_SECS * 20f) / reloadTicks;

        float pitch = PITCH_RELOAD_BASE + SOUND_RNG.nextFloat() * PITCH_RELOAD_RANGE;

        // Sonido de recarga para jugadores cercanos (excluye al shooter)
        if (ModSounds.GUN_RELOAD != null) {
            level.playSound(sp, sp.getX(), sp.getY(), sp.getZ(),
                    ModSounds.GUN_RELOAD, SoundSource.PLAYERS, VOL_RELOAD, pitch);
        }

        // Sonido con fade + velocidad de animación para el shooter
        // holdTicks = reloadTicks - 10 (fade-in 2 + fade-out 8), mínimo 1
        int holdTicks = Math.max(1, reloadTicks - 10);
        if (ModSounds.GUN_RELOAD != null) {
            ServerPlayNetworking.send(sp, new GunSoundPayload(
                    ModSounds.GUN_RELOAD.getLocation(), VOL_RELOAD, pitch, holdTicks, animSpeed));
        }

        ModItems.startReload(sp.getUUID(), level.getGameTime(), capacity, reloadTicks);
        triggerAnimation(sp, stack, "reload");
    }

    // ── Propulsión ────────────────────────────────────────────────────────────

    private void applyPropulsion(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer sp)) return;

        Vec3    look      = player.getLookAngle();
        Vec3    curr      = player.getDeltaMovement();
        boolean onGround  = player.onGround();
        boolean crouching = player.isCrouching();

        double  mult            = 0;
        boolean giveSlowFalling = false;
        String  debugLabel      = "";

        if (onGround) {
            if (look.y < DOWN_THRESHOLD) {
                mult            = PROPULSION_GROUND_DOWN;
                giveSlowFalling = true;
                debugLabel      = "COND 1 (suelo+abajo)";
            } else {
                mult       = PROPULSION_REDUCED;
                debugLabel = "SUELO (sin angulo)";
            }
        } else if (crouching) {
            mult       = PROPULSION_REDUCED;
            debugLabel = "COND 3 agachado (freno caída)";
            ModItems.incrementAirShots(sp.getUUID());
        } else {
            int maxEff = ModItems.MAX_EFFECTIVE_AIR_SHOTS + GunUpgrade.getBonusAirShots(stack);
            int used   = ModItems.getAirShotsUsed(sp.getUUID());
            if (used < maxEff) {
                mult       = PROPULSION_AIR;
                debugLabel = "COND 2 (aire " + (used + 1) + "/" + maxEff + ")";
                ModItems.incrementAirShots(sp.getUUID());
            } else {
                mult       = PROPULSION_REDUCED;
                debugLabel = "COND 3 (aire saturado)";
            }
        }

        double impMult = GunUpgrade.getImpulseMultiplier(stack);
        mult *= impMult;
        if (impMult != 1.0) debugLabel += String.format(" [impulse x%.2f]", impMult);

        if (debugPlayers.contains(sp.getUUID())) {
            sp.displayClientMessage(Component.literal(String.format(
                "[GunDebug] %s | mult=%.2f | rounds=%d | air=%d",
                debugLabel, mult, getLoadedRounds(stack),
                ModItems.getAirShotsUsed(sp.getUUID()))), true);
        }

        double px = look.x * -mult;
        double pz = look.z * -mult;
        double py = look.y * -mult * VERTICAL_RATIO;

        double finalY;
        if (onGround) {
            finalY = giveSlowFalling ? Math.max(py, mult * GROUND_VERTICAL_MIN) : py;
        } else {
            finalY = (py > 0) ? Math.max(curr.y + py, py) : curr.y + py;
        }

        sendVelocityToClient(sp, new Vec3(curr.x + px, finalY, curr.z + pz));

        if (giveSlowFalling && !sp.hasEffect(MobEffects.SLOW_FALLING)) {
            sp.addEffect(new MobEffectInstance(
                MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0, false, false));
            ModItems.addGunSlowFalling(sp.getUUID());
        }
    }

    // ── Sonidos especiales ────────────────────────────────────────────────────

    public static void playHitSound(ServerPlayer sp) {
        if (ModSounds.GUN_HIT == null) return;
        float pitch = 0.97f + SOUND_RNG.nextFloat() * 0.06f;
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
            ModSounds.GUN_HIT, SoundSource.PLAYERS, VOL_HIT, pitch);
    }

    public static void playUpgradeSound(ServerPlayer sp) {
        if (ModSounds.GUN_UPGRADE == null) return;
        float pitch = 0.98f + SOUND_RNG.nextFloat() * 0.04f;
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
            ModSounds.GUN_UPGRADE, SoundSource.PLAYERS, VOL_UPGRADE, pitch);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int loaded = getLoadedRounds(stack);
        ChatFormatting ammoColor = loaded > 0 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        tooltip.add(Component.translatable("item.cz-x-mc-weapons.gun.loaded_rounds", loaded, MAX_ROUNDS)
            .withStyle(ammoColor));
        GunUpgrade.appendUpgradeTooltip(stack, tooltip);
    }

    // ── Helpers NBT ──────────────────────────────────────────────────────────

    public static int getLoadedRounds(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(NBT_LOADED_ROUNDS) ? tag.getInt(NBT_LOADED_ROUNDS) : 0;
    }

    public static void setLoadedRounds(ItemStack stack, int rounds) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(NBT_LOADED_ROUNDS, Math.max(0, Math.min(rounds, MAX_ROUNDS)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    static int countGunpowder(Player player) {
        int total = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(net.minecraft.world.item.Items.GUNPOWDER)) total += s.getCount();
        }
        return total;
    }

    private void sendVelocityToClient(ServerPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity);
        player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), velocity));
    }

    private void spawnBullet(Level level, Player player, ItemStack stack) {
        GunBulletEntity bullet = new GunBulletEntity(level, player);
        Vec3 look = player.getLookAngle();
        bullet.setPos(
            player.getX() + look.x * 0.8,
            player.getEyeY() + look.y * 0.8 - 0.1,
            player.getZ() + look.z * 0.8);
        bullet.shoot(look.x, look.y, look.z, 3.0f, 0.3f);
        level.addFreshEntity(bullet);
    }

    // ── GeoItem ───────────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle_controller", 0, state -> {
            state.setAnimation(ANIM_IDLE);
            return PlayState.CONTINUE;
        }));

        controllers.add(new AnimationController<>(this, "shot_controller", 0,
                state -> PlayState.STOP)
            .triggerableAnim("shot", ANIM_SHOOT));

        // El predicate establece la velocidad de animación en cada frame.
        // reloadAnimSpeed es actualizado por el cliente al recibir el GunSoundPayload.
        // De esta forma la animación siempre termina exactamente cuando vence el cooldown.
        controllers.add(new AnimationController<>(this, "reload_controller", 0, state -> {
            state.getController().setAnimationSpeed(this.reloadAnimSpeed);
            return PlayState.STOP;
        }).triggerableAnim("reload", ANIM_RELOAD));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public void triggerAnimation(Player player, ItemStack stack, String animName) {
        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            long id = GeoItem.getOrAssignId(stack, serverLevel);
            String controllerName = animName.equals("shot") ? "shot_controller" : "reload_controller";
            triggerAnim(player, id, controllerName, animName);
        }
    }

    @Override
    public boolean allowComponentsUpdateAnimation(
            Player player, InteractionHand hand,
            ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }
}
