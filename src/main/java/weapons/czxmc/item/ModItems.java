package weapons.czxmc.item;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import weapons.czxmc.CZXMCWeapons;
import weapons.czxmc.item.GunItem;
import weapons.czxmc.item.DashItem;
import weapons.czxmc.item.DashUpgrade;
import weapons.czxmc.item.DashUpgradeItem;
import weapons.czxmc.item.ConnectorItem;
import weapons.czxmc.item.EchoGrenadeItem;
import weapons.czxmc.item.AnchorItem;
import weapons.czxmc.item.DebugGrenadeItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ModItems {

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEMS REGISTRADOS
    // ══════════════════════════════════════════════════════════════════════════

    // ── Pistola ───────────────────────────────────────────────────────────────
    public static final Item GUN = registerItem("gun",
            new GunItem(new Item.Properties().stacksTo(1)));

    // ── Mejoras de Daño ───────────────────────────────────────────────────────
    public static final Item UPGRADE_DAMAGE_1 = registerUpgrade(GunUpgrade.Id.DAMAGE_1);
    public static final Item UPGRADE_DAMAGE_2 = registerUpgrade(GunUpgrade.Id.DAMAGE_2);
    public static final Item UPGRADE_DAMAGE_3 = registerUpgrade(GunUpgrade.Id.DAMAGE_3);

    // ── Mejoras de Impulso ────────────────────────────────────────────────────
    public static final Item UPGRADE_IMPULSE_1 = registerUpgrade(GunUpgrade.Id.IMPULSE_1);
    public static final Item UPGRADE_IMPULSE_2 = registerUpgrade(GunUpgrade.Id.IMPULSE_2);
    public static final Item UPGRADE_IMPULSE_3 = registerUpgrade(GunUpgrade.Id.IMPULSE_3);

    // ── Mejoras de Balas Efectivas en el Aire ─────────────────────────────────
    public static final Item UPGRADE_AIR_SHOTS_1 = registerUpgrade(GunUpgrade.Id.AIR_SHOTS_1);
    public static final Item UPGRADE_AIR_SHOTS_2 = registerUpgrade(GunUpgrade.Id.AIR_SHOTS_2);
    public static final Item UPGRADE_AIR_SHOTS_3 = registerUpgrade(GunUpgrade.Id.AIR_SHOTS_3);

    // ── Mejoras de Velocidad de Recarga ───────────────────────────────────────
    public static final Item UPGRADE_RELOAD_HALF    = registerUpgrade(GunUpgrade.Id.RELOAD_HALF);
    public static final Item UPGRADE_RELOAD_QUARTER = registerUpgrade(GunUpgrade.Id.RELOAD_QUARTER);

    // ── Amuleto de Dash ───────────────────────────────────────────────────────
    public static final Item DASH_AMULET = registerItem("dash_amulet",
            new DashItem(new Item.Properties().stacksTo(1)));

    // ── Conector de línea ─────────────────────────────────────────────────────
    public static final Item CONNECTOR = registerItem("connector",
            new ConnectorItem(new Item.Properties().stacksTo(1)));

    // ── Ancla de abordaje ─────────────────────────────────────────────────────
    public static final Item ANCHOR = registerItem("anchor",
            new AnchorItem(new Item.Properties().stacksTo(1)));

    // ── Granada debug ─────────────────────────────────────────────────────────
    public static final Item DEBUG_GRENADE = registerItem("debug_grenade",
            new DebugGrenadeItem(new Item.Properties().stacksTo(16)));

    // ── Echo Grenade ──────────────────────────────────────────────────────────
    public static final Item ECHO_GRENADE = registerItem("echo_grenade",
            new EchoGrenadeItem(new Item.Properties().stacksTo(16)));

    // ── Mejoras del Dash ──────────────────────────────────────────────────────
    public static final Item DASH_UPGRADE_CHARGES_1   = registerDashUpgrade(DashUpgrade.Id.CHARGES_1);
    public static final Item DASH_UPGRADE_CHARGES_2   = registerDashUpgrade(DashUpgrade.Id.CHARGES_2);
    public static final Item DASH_UPGRADE_COOLDOWN_1  = registerDashUpgrade(DashUpgrade.Id.COOLDOWN_1);
    public static final Item DASH_UPGRADE_PHASE       = registerDashUpgrade(DashUpgrade.Id.SPECIAL_PHASE);
    public static final Item DASH_UPGRADE_IMPACT      = registerDashUpgrade(DashUpgrade.Id.SPECIAL_IMPACT);
    public static final Item DASH_UPGRADE_WALL        = registerDashUpgrade(DashUpgrade.Id.SPECIAL_WALL);

    // ══════════════════════════════════════════════════════════════════════════
    //  CONSTANTES CONFIGURABLES
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Balas efectivas base en el aire antes de pasar a efectividad reducida.
     * Las mejoras AIR_SHOTS suman por encima de este valor.
     *
     *   Disparo 1 en aire → condición 2 (normal)
     *   Disparo 2+ en aire → condición 3 (reducida) — sin mejoras
     */
    public static final int MAX_EFFECTIVE_AIR_SHOTS = 1;

    // ══════════════════════════════════════════════════════════════════════════
    //  ESTADO POR JUGADOR — Balas en el aire y Slow Falling
    // ══════════════════════════════════════════════════════════════════════════

    private static final Map<UUID, Integer> airShotsUsed          = new HashMap<>();
    private static final Set<UUID>          gunSlowFallingPlayers  = new HashSet<>();
    private static final Map<UUID, Boolean> wasOnGround            = new HashMap<>();
    /** Item que tenía el jugador en la mano principal el tick anterior (para detectar equip) */
    private static final Map<UUID, Item>    prevMainHandItem        = new HashMap<>();

    public static int  getAirShotsUsed(UUID uuid)        { return airShotsUsed.getOrDefault(uuid, 0); }
    public static void incrementAirShots(UUID uuid)       { airShotsUsed.put(uuid, getAirShotsUsed(uuid) + 1); }
    public static boolean hasGunSlowFalling(UUID uuid)    { return gunSlowFallingPlayers.contains(uuid); }
    public static void addGunSlowFalling(UUID uuid)       { gunSlowFallingPlayers.add(uuid); }
    public static void clearGunSlowFalling(UUID uuid)     { gunSlowFallingPlayers.remove(uuid); }

    // ══════════════════════════════════════════════════════════════════════════
    //  ESTADO POR JUGADOR — Recarga progresiva
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Estado de una recarga en progreso.
     *
     * @param startTick   Tick del mundo cuando empezó la recarga.
     * @param capacity    Cuántas balas se van a cargar (1 ó 2).
     * @param consumed    Cuántas balas ya se consumieron (se actualiza en tick).
     * @param totalTicks  Duración total de la recarga en ticks.
     */
    private record ReloadState(long startTick, int capacity, int consumed, int totalTicks) {}

    private static final Map<UUID, ReloadState> reloadStates = new HashMap<>();

    /**
     * Inicia el rastreo de una recarga en progreso.
     * Llamado desde GunItem cuando se aplica el cooldown de recarga.
     *
     * @param uuid       UUID del jugador.
     * @param startTick  Tick actual del mundo (level.getGameTime()).
     * @param capacity   Número de balas a cargar (1 ó 2).
     * @param totalTicks Duración del cooldown de recarga.
     */
    public static void startReload(UUID uuid, long startTick, int capacity, int totalTicks) {
        reloadStates.put(uuid, new ReloadState(startTick, capacity, 0, totalTicks));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  REGISTRO
    // ══════════════════════════════════════════════════════════════════════════

    private static Item registerItem(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, name), item);
    }

    private static Item registerDashUpgrade(DashUpgrade.Id id) {
        return registerItem(
            "dash_upgrade_" + id.key,
            new DashUpgradeItem(id, new Item.Properties().stacksTo(16))
        );
    }

    private static Item registerUpgrade(GunUpgrade.Id id) {
        return registerItem(
            "gun_upgrade_" + id.key,
            new GunUpgradeItem(id, new Item.Properties().stacksTo(16))
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TICK — Lógica por tick (aterrizaje, recarga progresiva)
    // ══════════════════════════════════════════════════════════════════════════

    public static void registerAll() {
        CZXMCWeapons.LOGGER.info("Registrando items de " + CZXMCWeapons.MOD_ID);

        // ── Pestañas del inventario creativo ──────────────────────────────────
        // Pistola → pestaña de Combate (armas)
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(GUN);
            entries.accept(DASH_AMULET);
            entries.accept(CONNECTOR);
            entries.accept(ANCHOR);
        });

        // Mejoras → pestaña de Ingredientes
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(UPGRADE_DAMAGE_1);
            entries.accept(UPGRADE_DAMAGE_2);
            entries.accept(UPGRADE_DAMAGE_3);
            entries.accept(UPGRADE_IMPULSE_1);
            entries.accept(UPGRADE_IMPULSE_2);
            entries.accept(UPGRADE_IMPULSE_3);
            entries.accept(UPGRADE_AIR_SHOTS_1);
            entries.accept(UPGRADE_AIR_SHOTS_2);
            entries.accept(UPGRADE_AIR_SHOTS_3);
            entries.accept(UPGRADE_RELOAD_HALF);
            entries.accept(UPGRADE_RELOAD_QUARTER);
            entries.accept(DASH_UPGRADE_CHARGES_1);
            entries.accept(DASH_UPGRADE_CHARGES_2);
            entries.accept(DASH_UPGRADE_COOLDOWN_1);
            entries.accept(DASH_UPGRADE_PHASE);
            entries.accept(DASH_UPGRADE_IMPACT);
            entries.accept(DASH_UPGRADE_WALL);
        });

        ServerTickEvents.END_WORLD_TICK.register((ServerLevel world) -> {
            for (Player player : world.players()) {
                if (!(player instanceof ServerPlayer sp)) continue;
                UUID uuid = sp.getUUID();

                // ── Detección de aterrizaje ────────────────────────────────────
                boolean now  = sp.onGround();
                boolean prev = wasOnGround.getOrDefault(uuid, true);
                wasOnGround.put(uuid, now);

                if (now && !prev) {
                    // Aterrizó → resetear contador de balas en aire (cond 3)
                    airShotsUsed.remove(uuid);

                    // Quitar Slow Falling de condición 1
                    if (gunSlowFallingPlayers.contains(uuid)) {
                        sp.removeEffect(MobEffects.SLOW_FALLING);
                        gunSlowFallingPlayers.remove(uuid);
                    }
                }

                // Limpiar tracking si el Slow Falling expiró naturalmente
                if (gunSlowFallingPlayers.contains(uuid) && !sp.hasEffect(MobEffects.SLOW_FALLING)) {
                    gunSlowFallingPlayers.remove(uuid);
                }


                // ── Recarga progresiva ─────────────────────────────────────────
                tickReload(sp, uuid, world.getGameTime());

                // ── Recargar cargas del dash ───────────────────────────────────
                DashItem.tickRecharge(sp);

                // ── Noclip wall-phase ──────────────────────────────────────────
                DashItem.tickWallPhase(sp);
            }
        });
    }

    /**
     * Gestiona el consumo progresivo de pólvora durante la recarga.
     *
     * Para una recarga de 2 balas (60 ticks base):
     *   • Al 1/3 del tiempo (tick 20): consume 1 pólvora, LoadedRounds = 1
     *   • Al 2/3 del tiempo (tick 40): consume 1 pólvora, LoadedRounds = 2
     *
     * Para una recarga de 1 bala (30 ticks base):
     *   • A la mitad del tiempo (tick 15): consume 1 pólvora, LoadedRounds = 1
     *
     * Al finalizar el cooldown, limpia el estado de recarga.
     * Si el jugador se queda sin pólvora a mitad de recarga, la bala
     * correspondiente no se carga (el cañón queda sin esa bala).
     */
    private static void tickReload(ServerPlayer sp, UUID uuid, long currentTick) {
        ReloadState reload = reloadStates.get(uuid);
        if (reload == null) return;

        // Si el cooldown ya terminó, limpiar estado
        if (!sp.getCooldowns().isOnCooldown(GUN)) {
            reloadStates.remove(uuid);
            return;
        }

        // Buscar la pistola en el inventario del jugador
        ItemStack gun = findGunStack(sp);
        if (gun == null) {
            reloadStates.remove(uuid);
            return;
        }

        long elapsed = currentTick - reload.startTick;
        int  consumed = reload.consumed;

        if (reload.capacity >= 2) {
            // ── Recarga de 2 balas ─────────────────────────────────────────────
            long tick1 = reload.totalTicks / 3L;      // 1er cañón carga a 1/3 del tiempo
            long tick2 = (2L * reload.totalTicks) / 3; // 2do cañón carga a 2/3 del tiempo

            if (elapsed >= tick1 && consumed < 1) {
                if (consumeOneGunpowder(sp)) {
                    GunItem.setLoadedRounds(gun, 1);
                    consumed = 1;
                    // Sonido de carga del 1er cañón
                    sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS, 0.6f, 1.2f);
                }
            }

            if (elapsed >= tick2 && consumed < 2) {
                if (consumeOneGunpowder(sp)) {
                    GunItem.setLoadedRounds(gun, 2);
                    consumed = 2;
                    // Sonido de carga completa
                    sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.8f, 1.0f);
                }
            }

        } else {
            // ── Recarga de 1 bala ──────────────────────────────────────────────
            long tickMid = reload.totalTicks / 2L;

            if (elapsed >= tickMid && consumed < 1) {
                if (consumeOneGunpowder(sp)) {
                    GunItem.setLoadedRounds(gun, 1);
                    consumed = 1;
                    sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.8f, 1.0f);
                }
            }
        }

        // Actualizar el estado con el nuevo consumido
        reloadStates.put(uuid, new ReloadState(
            reload.startTick, reload.capacity, consumed, reload.totalTicks
        ));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS PRIVADOS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Busca el ItemStack de la pistola en el jugador.
     * Primero manos (principal/secundaria), luego inventario.
     */
    private static ItemStack findGunStack(Player player) {
        if (player.getMainHandItem().getItem() instanceof GunItem)  return player.getMainHandItem();
        if (player.getOffhandItem().getItem()  instanceof GunItem)  return player.getOffhandItem();
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof GunItem) return s;
        }
        return null;
    }

    /**
     * Consume exactamente 1 unidad de pólvora del inventario del jugador.
     * Devuelve true si pudo consumir, false si no había.
     */
    private static boolean consumeOneGunpowder(Player player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.is(Items.GUNPOWDER) && s.getCount() > 0) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }
}
