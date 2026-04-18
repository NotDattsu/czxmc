package weapons.czxmc.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import weapons.czxmc.network.WallPhasePayload;
import weapons.czxmc.sound.ModSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Amuleto de Dash — Trinket.
 *
 * Se equipa en la ranura "chest/necklace" de Trinkets (ajustable en trinkets.json).
 * Se activa con la tecla de dash (registrada en CZXMCWeaponsClient).
 * El cliente detecta la tecla, lee las teclas WASD activas, y manda DashPayload al servidor.
 *
 * ── Dirección ────────────────────────────────────────────────────────────
 *   Si el jugador está presionando teclas de movimiento (W/A/S/D):
 *     → el dash va en esa dirección relativa a donde mira
 *   Si no presiona nada:
 *     → el dash va en la dirección que está mirando
 *
 * ── Cargas ───────────────────────────────────────────────────────────────
 *   Todas recargan juntas. El jugador decide cuándo recargar manualmente
 *   o se recarga automáticamente al gastar la última carga.
 */
public class DashItem extends TrinketItem {

    // ── Física ────────────────────────────────────────────────────────────────
    private static final double DASH_SPEED        = 1.6;  // velocidad horizontal del dash
    private static final double DASH_VERTICAL     = 0.22; // impulso vertical leve
    private static final double IMPACT_RADIUS     = 4.0;
    private static final double IMPACT_KNOCKBACK  = 3.5;

    // ── Cooldown ──────────────────────────────────────────────────────────────
    public static final int BASE_COOLDOWN = 60; // 3 segundos base

    private static final String NBT_CHARGES_LEFT = "DashChargesLeft";
    private static final Random SOUND_RNG = new Random();

    // ── Noclip wall phase ─────────────────────────────────────────────────────
    /**
     * Sesión de wall-phase activa.
     * El cliente maneja el movimiento visual (noPhysics); el servidor solo espera
     * ticksLeft ticks y luego teletransporta al jugador a la posición de salida.
     */
    private record WallPhaseSession(Vec3 exitPos, int ticksLeft, Vec3 exitVelocity, ItemStack stack) {}
    private static final ConcurrentHashMap<UUID, WallPhaseSession> WALL_SESSIONS = new ConcurrentHashMap<>();

    /** Velocidad de atravesado por tick (bloques/tick). Debe coincidir con lo que se envía al cliente. */
    private static final double WALL_STEP = 0.28;

    public DashItem(Properties properties) {
        super(properties);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  EJECUTAR DASH — Llamado desde el handler de red en CZXMCWeapons
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Punto de entrada del dash. Llamado cuando el servidor recibe DashPayload.
     *
     * @param sp          El jugador que activa el dash
     * @param inputDirX   Componente X de las teclas WASD del cliente (-1 a 1)
     * @param inputDirZ   Componente Z de las teclas WASD del cliente (-1 a 1)
     * @param useMovement true = usar WASD, false = usar mirada
     */
    public static void handleDash(ServerPlayer sp, float inputDirX, float inputDirZ, boolean useMovement) {
        // Buscar el amuleto equipado en Trinkets
        ItemStack stack = findEquippedDash(sp);
        if (stack == null) return;

        // Verificar cooldown
        if (sp.getCooldowns().isOnCooldown(stack.getItem())) return;

        int charges = getChargesLeft(stack);
        if (charges <= 0) {
            sp.displayClientMessage(
                Component.translatable("item.cz-x-mc-weapons.dash_amulet.no_charges")
                    .withStyle(ChatFormatting.RED), true);
            startRecharge(sp, stack);
            return;
        }

        // Calcular la dirección del dash
        Vec3 dashDir = computeDashDirection(sp, inputDirX, inputDirZ, useMovement);

        // Ejecutar
        if (DashUpgrade.hasWall(stack)) {
            Vec3 wallDest = findWallPassthrough(sp, dashDir);
            if (wallDest != null) {
                Vec3 entryPos = sp.position();
                // Calcular cuántos ticks necesita el cliente para cruzar la pared
                double crossDist = wallDest.subtract(entryPos).length();
                int ticks = Math.max(1, (int) Math.ceil(crossDist / WALL_STEP));
                Vec3 exitVel = new Vec3(dashDir.x * 0.85, 0.1, dashDir.z * 0.85);

                // Guardar sesión simplificada: el servidor solo espera y hace teleport final
                WALL_SESSIONS.put(sp.getUUID(), new WallPhaseSession(wallDest, ticks, exitVel, stack));

                // ── Enviar al cliente para que active el noclip visual ──────────
                // La velocidad que el cliente aplica a su player: WALL_STEP bloques/tick
                // en dirección del dash + pequeño impulso vertical
                ServerPlayNetworking.send(sp, new WallPhasePayload(
                        dashDir.x * WALL_STEP,
                        0.04,
                        dashDir.z * WALL_STEP,
                        ticks
                ));

                // Efecto visual de entrada al iniciar la sesión
                spawnWallPhaseEffect(sp.level(), entryPos, true);
                playDashSound(sp);
                consumeCharge(sp, stack);
                return;
            }
        }

        // Dash normal — velocidad en la dirección calculada + pequeño impulso vertical
        Vec3 velocity = new Vec3(
            dashDir.x * DASH_SPEED,
            DASH_VERTICAL,
            dashDir.z * DASH_SPEED
        );
        sp.setDeltaMovement(velocity);
        sp.connection.send(new ClientboundSetEntityMotionPacket(sp.getId(), velocity));

        Vec3 estimatedDest = sp.position().add(dashDir.x * 4, 0.5, dashDir.z * 4);
        applySpecialEffects(sp, stack, estimatedDest);
        playDashSound(sp);
        consumeCharge(sp, stack);
    }

    // ── Calcular dirección según WASD o mirada ────────────────────────────────
    private static Vec3 computeDashDirection(ServerPlayer sp, float ix, float iz, boolean useMovement) {
        if (!useMovement || (Math.abs(ix) < 0.1 && Math.abs(iz) < 0.1)) {
            // Sin input de movimiento → usar la dirección horizontal que mira
            Vec3 look = sp.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            return flat.lengthSqr() > 0.001 ? flat.normalize() : flat;
        }

        // Con WASD: rotar el input relativo a la rotación del jugador
        // ix = strafe (-1 izquierda, +1 derecha), iz = forward (-1 atrás, +1 adelante)
        float yaw = (float) Math.toRadians(sp.getYRot());
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);

        // Rotar el vector de input al espacio mundial
        double worldX = -iz * sinYaw - ix * cosYaw;  // notar el signo de iz
        double worldZ =  iz * cosYaw - ix * sinYaw;

        Vec3 dir = new Vec3(worldX, 0, worldZ);
        return dir.lengthSqr() > 0.001 ? dir.normalize() : dir;
    }

    // ── Efectos especiales post-dash ──────────────────────────────────────────
    private static void applySpecialEffects(ServerPlayer sp, ItemStack stack, Vec3 dest) {
        if (DashUpgrade.hasPhase(stack)) applyPhase(sp, dest);
        if (DashUpgrade.hasImpact(stack)) applyImpact(sp, dest);
    }

    private static void applyPhase(ServerPlayer sp, Vec3 dest) {
        Vec3 start = sp.position();
        Vec3 dir   = dest.subtract(start);
        double len = dir.length();
        if (len < 0.01) return;

        AABB box = new AABB(start, dest).inflate(1.0);
        for (LivingEntity e : sp.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == sp) continue;
            Vec3 toE = e.position().subtract(start);
            double dot = toE.dot(dir.normalize());
            if (dot < 0 || dot > len + 1) continue;
            // Slowness IV por 10 segundos + Wither II por 6 segundos
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 3));
            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));
            // Daño directo al pasar a través
            e.hurt(sp.level().damageSources().playerAttack(sp), 6.0f);
        }
        // Partículas de phase a lo largo del trayecto
        if (sp.level() instanceof ServerLevel sl) {
            int steps = Math.max(1, (int)(len / 0.8));
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                Vec3 pos = start.add(dir.x * t, dir.y * t, dir.z * t);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                    pos.x, pos.y + 1.0, pos.z, 4, 0.15, 0.3, 0.15, 0.0);
            }
        }
    }

    private static void applyImpact(ServerPlayer sp, Vec3 dest) {
        AABB area = new AABB(dest, dest).inflate(IMPACT_RADIUS);
        for (LivingEntity e : sp.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (e == sp) continue;
            double dist = e.position().distanceTo(dest);
            if (dist > IMPACT_RADIUS) continue;
            double proximity = 1.0 - dist / IMPACT_RADIUS;
            Vec3 dir = e.position().subtract(dest).normalize();
            if (dir.lengthSqr() < 0.001) dir = new Vec3(0, 1, 0);
            double force = IMPACT_KNOCKBACK * proximity;
            // Fuerte lanzamiento horizontal + buen impulso vertical
            Vec3 vel = e.getDeltaMovement().add(dir.x * force, 0.6 + dir.y * force * 0.6, dir.z * force);
            e.setDeltaMovement(vel);
            e.hurtMarked = true;
            if (e instanceof ServerPlayer target)
                target.connection.send(new ClientboundSetEntityMotionPacket(target.getId(), vel));
            // Daño real: hasta 14 corazones en el centro, decae con la distancia
            e.hurt(sp.level().damageSources().playerAttack(sp), (float)(14.0 * proximity));
        }
        if (sp.level() instanceof ServerLevel sl) {
            // Explosión visual grande
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                dest.x, dest.y + 0.5, dest.z, 3, 0.5, 0.3, 0.5, 0.0);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                dest.x, dest.y + 0.5, dest.z, 14, 0.8, 0.4, 0.8, 0.0);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                dest.x, dest.y + 0.8, dest.z, 20, 0.5, 0.5, 0.5, 0.4);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                dest.x, dest.y + 0.3, dest.z, 10, 0.6, 0.3, 0.6, 0.04);
        }
    }

    /** Efecto visual de partículas al entrar/salir de una pared. */
    private static void spawnWallPhaseEffect(Level level, Vec3 pos, boolean entry) {
        if (!(level instanceof ServerLevel sl)) return;
        if (entry) {
            // Entrada: vórtice de portal que se traga al jugador
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                pos.x, pos.y + 1.0, pos.z, 50, 0.3, 0.6, 0.3, 0.6);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                pos.x, pos.y + 1.0, pos.z, 12, 0.2, 0.5, 0.2, 0.1);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                pos.x, pos.y + 1.0, pos.z, 20, 0.3, 0.6, 0.3, 0.5);
        } else {
            // Salida: explosión de portal hacia afuera
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                pos.x, pos.y + 1.0, pos.z, 50, 0.3, 0.6, 0.3, 0.6);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                pos.x, pos.y + 1.0, pos.z, 20, 0.3, 0.6, 0.3, 0.2);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                pos.x, pos.y + 1.0, pos.z, 15, 0.3, 0.5, 0.3, 0.5);
        }
    }

    /** Construye una lista de posiciones interpoladas desde start hasta exit, cada stepSize bloques. */
    private static List<Vec3> buildWallPath(Vec3 start, Vec3 exit, double stepSize) {
        Vec3 delta = exit.subtract(start);
        double len  = delta.length();
        List<Vec3> path = new ArrayList<>();
        if (len < 0.001) { path.add(exit); return path; }
        int steps = Math.max(1, (int) Math.ceil(len / stepSize));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            path.add(new Vec3(start.x + delta.x * t, start.y + delta.y * t, start.z + delta.z * t));
        }
        return path;
    }

    /** Devuelve true si el jugador tiene una sesión de wall-phase activa. Usado por el mixin del servidor. */
    public static boolean hasActiveWallPhase(ServerPlayer sp) {
        return WALL_SESSIONS.containsKey(sp.getUUID());
    }

    /**
     * Avanza un tick la sesión de wall-phase del jugador.
     * El cliente ya está manejando el movimiento visual (noPhysics + velocidad).
     * El servidor solo cuenta ticks y al finalizar teletransporta al jugador a la
     * posición de salida para sincronizar el estado oficial.
     * Llamado cada tick desde ModItems.
     */
    public static void tickWallPhase(ServerPlayer sp) {
        WallPhaseSession session = WALL_SESSIONS.get(sp.getUUID());
        if (session == null) return;

        // Limpiar si el jugador murió o desconectó
        if (!sp.isAlive()) {
            WALL_SESSIONS.remove(sp.getUUID());
            return;
        }

        int newTicks = session.ticksLeft() - 1;

        if (newTicks <= 0) {
            // Llegamos al final → teleport final de sincronización + efectos de salida
            Vec3 exitPos = session.exitPos();
            sp.teleportTo(exitPos.x, exitPos.y, exitPos.z);
            sp.setDeltaMovement(session.exitVelocity());
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp.getId(), session.exitVelocity()));
            spawnWallPhaseEffect(sp.level(), exitPos, false);
            applySpecialEffects(sp, session.stack(), exitPos);
            WALL_SESSIONS.remove(sp.getUUID());
        } else {
            // Actualizar contador — el movimiento real lo maneja el cliente
            WALL_SESSIONS.put(sp.getUUID(),
                new WallPhaseSession(session.exitPos(), newTicks, session.exitVelocity(), session.stack()));
        }
    }

    private static Vec3 findWallPassthrough(ServerPlayer sp, Vec3 dashDir) {
        Level level = sp.level();
        Vec3 start  = sp.getEyePosition();
        double scanDist = 8.0;

        for (double d = 1.0; d <= scanDist; d += 0.5) {
            Vec3 check = start.add(dashDir.x * d, 0, dashDir.z * d);
            if (!level.getBlockState(BlockPos.containing(check)).isAir()) {
                int solid = 0;
                for (int t = 0; t < 6; t++) {
                    Vec3 ins = start.add(dashDir.x * (d + t * 0.4), 0, dashDir.z * (d + t * 0.4));
                    if (!level.getBlockState(BlockPos.containing(ins)).isAir()) solid++;
                }
                if (solid <= 5) { // ≤2 bloques aprox
                    for (double past = d + 1.5; past <= d + 4.0; past += 0.5) {
                        Vec3 other = start.add(dashDir.x * past, 0, dashDir.z * past);
                        BlockPos bp = BlockPos.containing(other);
                        if (level.getBlockState(bp).isAir()
                                && level.getBlockState(bp.above()).isAir()) {
                            return new Vec3(other.x, sp.getY(), other.z);
                        }
                    }
                }
                return null;
            }
        }
        return null;
    }

    // ── Carga / recarga ───────────────────────────────────────────────────────
    private static void consumeCharge(ServerPlayer sp, ItemStack stack) {
        int remaining = getChargesLeft(stack) - 1;
        setChargesLeft(stack, remaining);
        if (remaining <= 0) startRecharge(sp, stack);
    }

    public static void startRecharge(Player player, ItemStack stack) {
        int cd = (int)(BASE_COOLDOWN * DashUpgrade.getCooldownMultiplier(stack));
        player.getCooldowns().addCooldown(stack.getItem(), cd);
    }

    /** Llamado cada tick desde ModItems: restaura cargas cuando termina el cooldown. */
    public static void tickRecharge(ServerPlayer sp) {
        ItemStack stack = findEquippedDash(sp);
        if (stack == null) return;
        boolean onCooldown = sp.getCooldowns().isOnCooldown(stack.getItem());
        if (!onCooldown && getChargesLeft(stack) <= 0) {
            setChargesLeft(stack, DashUpgrade.getTotalCharges(stack));
        }
    }

    // ── Buscar el amuleto equipado en Trinkets ────────────────────────────────
    public static ItemStack findEquippedDash(Player player) {
        // Buscar primero en las ranuras de Trinkets (slot correcto de equipo)
        ItemStack[] found = {null};
        TrinketsApi.getTrinketComponent(player).ifPresent(comp ->
            comp.forEach((slotRef, stack) -> {
                if (found[0] == null && stack.getItem() instanceof DashItem)
                    found[0] = stack;
            })
        );
        return found[0];
    }

    // ── Recarga manual (tecla Artifact Reload) ────────────────────────────────
    /**
     * Llamado cuando el jugador presiona la tecla genérica de recarga de artefacto.
     * Solo funciona si el jugador tiene el amuleto equipado en Trinkets y tiene cargas
     * menores al máximo (o está en cooldown).
     */
    public static void handleManualRecharge(ServerPlayer sp) {
        ItemStack stack = findEquippedDash(sp);
        if (stack == null) return;

        int current = getChargesLeft(stack);
        int max     = DashUpgrade.getTotalCharges(stack);
        boolean onCooldown = sp.getCooldowns().isOnCooldown(stack.getItem());

        if (current >= max && !onCooldown) {
            sp.displayClientMessage(
                Component.translatable("item.cz-x-mc-weapons.dash_amulet.already_full")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        // Cancela cualquier cooldown pendiente y restaura cargas
        sp.getCooldowns().removeCooldown(stack.getItem());
        setChargesLeft(stack, max);
        sp.displayClientMessage(
            Component.translatable("item.cz-x-mc-weapons.dash_amulet.recharged")
                .withStyle(ChatFormatting.AQUA), true);
        playUpgradeSound(sp);
    }

    // ── Sonidos ───────────────────────────────────────────────────────────────
    private static void playDashSound(ServerPlayer sp) {
        if (ModSounds.DASH == null) return;
        float pitch = 0.88f + SOUND_RNG.nextFloat() * 0.24f;
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
            ModSounds.DASH, SoundSource.PLAYERS, 0.8f, pitch);
    }

    public static void playUpgradeSound(ServerPlayer sp) {
        if (ModSounds.GUN_UPGRADE == null) return;
        float pitch = 0.98f + SOUND_RNG.nextFloat() * 0.04f;
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
            ModSounds.GUN_UPGRADE, SoundSource.PLAYERS, 0.9f, pitch);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int charges = getChargesLeft(stack);
        int max     = DashUpgrade.getTotalCharges(stack);
        tooltip.add(Component.translatable("item.cz-x-mc-weapons.dash_amulet.charges", charges, max)
            .withStyle(charges > 0 ? ChatFormatting.AQUA : ChatFormatting.RED));
        DashUpgrade.appendTooltip(stack, tooltip);
        tooltip.add(Component.translatable("item.cz-x-mc-weapons.dash_amulet.hint_use")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ── NBT ───────────────────────────────────────────────────────────────────
    public static int getChargesLeft(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(NBT_CHARGES_LEFT)) return DashUpgrade.getTotalCharges(stack);
        return tag.getInt(NBT_CHARGES_LEFT);
    }

    public static void setChargesLeft(ItemStack stack, int charges) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(NBT_CHARGES_LEFT, Math.max(0, charges));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
