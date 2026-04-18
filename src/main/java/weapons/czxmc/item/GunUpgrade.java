package weapons.czxmc.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/**
 * Sistema de mejoras de la pistola.
 *
 * ── Reglas ────────────────────────────────────────────────────────────────
 *   MAX_TOTAL       = 5  (mejoras totales aplicables a la vez)
 *   DAMAGE / IMPULSE / AIR_SHOTS: máx 2 de cada tipo
 *   RELOAD_SPEED:                 máx 1 (solo una a la vez)
 *
 * ── Cómo aplicar (jugador) ────────────────────────────────────────────────
 *   Sostené el ítem de mejora en la mano principal y la pistola en la
 *   mano secundaria (o viceversa), luego hacé clic derecho.
 *
 * ── Tiers disponibles ─────────────────────────────────────────────────────
 *   DAMAGE     : damage_1 (+20%), damage_2 (+35%), damage_3 (+50%)
 *   IMPULSE    : impulse_1 (+15%), impulse_2 (+25%), impulse_3 (+40%)
 *   AIR_SHOTS  : air_shots_1 (+1), air_shots_2 (+2), air_shots_3 (+3)
 *   RELOAD_SPEED: reload_half (÷2), reload_quarter (÷4)
 */
public class GunUpgrade {

    // ── Tipos de mejora ───────────────────────────────────────────────────────
    public enum Type {
        DAMAGE,
        IMPULSE,
        AIR_SHOTS,
        RELOAD_SPEED;

        /** Máximas mejoras de este tipo aplicables simultáneamente. */
        public int maxPerType() {
            return this == RELOAD_SPEED ? 1 : 2;
        }
    }

    // ── IDs de mejora (un ítem por cada ID) ──────────────────────────────────
    public enum Id {
        // ─ Daño ──────────────────────────────────────────────────────────────
        DAMAGE_1   ("damage_1",       Type.DAMAGE),
        DAMAGE_2   ("damage_2",       Type.DAMAGE),
        DAMAGE_3   ("damage_3",       Type.DAMAGE),

        // ─ Impulso ───────────────────────────────────────────────────────────
        IMPULSE_1  ("impulse_1",      Type.IMPULSE),
        IMPULSE_2  ("impulse_2",      Type.IMPULSE),
        IMPULSE_3  ("impulse_3",      Type.IMPULSE),

        // ─ Balas efectivas en el aire ─────────────────────────────────────────
        AIR_SHOTS_1("air_shots_1",   Type.AIR_SHOTS),
        AIR_SHOTS_2("air_shots_2",   Type.AIR_SHOTS),
        AIR_SHOTS_3("air_shots_3",   Type.AIR_SHOTS),

        // ─ Velocidad de recarga (max 1 aplicada) ──────────────────────────────
        RELOAD_HALF   ("reload_half",    Type.RELOAD_SPEED),
        RELOAD_QUARTER("reload_quarter", Type.RELOAD_SPEED);

        public final String key;
        public final Type   type;

        Id(String key, Type type) {
            this.key  = key;
            this.type = type;
        }

        /** Busca un Id por su clave NBT. Devuelve null si no existe. */
        public static Id fromKey(String key) {
            for (Id id : values()) {
                if (id.key.equals(key)) return id;
            }
            return null;
        }
    }

    // ── Límites globales ──────────────────────────────────────────────────────
    public static final int MAX_TOTAL = 5;

    // ── Clave NBT en el ItemStack de la pistola ───────────────────────────────
    private static final String NBT_UPGRADES = "GunUpgrades";

    // ══════════════════════════════════════════════════════════════════════════
    //  Lectura / escritura de mejoras en el NBT de la pistola
    // ══════════════════════════════════════════════════════════════════════════

    /** Devuelve la lista de mejoras instaladas en la pistola (en orden de aplicación). */
    public static List<Id> getUpgradeIds(ItemStack stack) {
        CompoundTag tag  = getTag(stack);
        List<Id>    list = new ArrayList<>();
        if (!tag.contains(NBT_UPGRADES, Tag.TAG_LIST)) return list;
        ListTag nbtList = tag.getList(NBT_UPGRADES, Tag.TAG_STRING);
        for (int i = 0; i < nbtList.size(); i++) {
            Id id = Id.fromKey(nbtList.getString(i));
            if (id != null) list.add(id);
        }
        return list;
    }

    /** Cuenta cuántas mejoras del tipo dado están instaladas. */
    public static int countType(ItemStack stack, Type type) {
        int n = 0;
        for (Id id : getUpgradeIds(stack)) if (id.type == type) n++;
        return n;
    }

    /**
     * Verifica si se puede añadir la mejora `id` a la pistola.
     * Comprueba: total < MAX_TOTAL y count(type) < type.maxPerType().
     */
    public static boolean canAddUpgrade(ItemStack stack, Id id) {
        List<Id> current = getUpgradeIds(stack);
        if (current.size() >= MAX_TOTAL) return false;
        return countType(stack, id.type) < id.type.maxPerType();
    }

    /** Aplica la mejora `id` a la pistola. Devuelve false si no es posible. */
    public static boolean addUpgrade(ItemStack stack, Id id) {
        if (!canAddUpgrade(stack, id)) return false;
        CompoundTag tag = getTag(stack);
        ListTag list = tag.contains(NBT_UPGRADES, Tag.TAG_LIST)
                       ? tag.getList(NBT_UPGRADES, Tag.TAG_STRING)
                       : new ListTag();
        list.add(StringTag.valueOf(id.key));
        tag.put(NBT_UPGRADES, list);
        setTag(stack, tag);
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Cálculo de modificadores (llamado desde GunItem en tiempo de disparo)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Multiplicador de daño total.
     * Cada mejora DAMAGE instalada multiplica el daño de forma independiente.
     *
     * Ejemplos:
     *   DAMAGE_1 + DAMAGE_3 → 1.20 × 1.50 = 1.80 (80% más de daño)
     *   DAMAGE_3 × 2        → 1.50 × 1.50 = 2.25
     */
    public static double getDamageMultiplier(ItemStack stack) {
        double mult = 1.0;
        for (Id id : getUpgradeIds(stack)) {
            mult *= switch (id) {
                case DAMAGE_1 -> 1.20;
                case DAMAGE_2 -> 1.35;
                case DAMAGE_3 -> 1.50;
                default       -> 1.0;
            };
        }
        return mult;
    }

    /**
     * Multiplicador de impulso (propulsión del jugador y knockback a mobs).
     * Escala tanto el retroceso del jugador como el knockback al impacto.
     *
     * Ejemplos:
     *   IMPULSE_2 + IMPULSE_3 → 1.25 × 1.40 = 1.75
     */
    public static double getImpulseMultiplier(ItemStack stack) {
        double mult = 1.0;
        for (Id id : getUpgradeIds(stack)) {
            mult *= switch (id) {
                case IMPULSE_1 -> 1.15;
                case IMPULSE_2 -> 1.25;
                case IMPULSE_3 -> 1.40;
                default        -> 1.0;
            };
        }
        return mult;
    }

    /**
     * Balas efectivas extra en el aire (se suma a ModItems.MAX_EFFECTIVE_AIR_SHOTS).
     * Con 2× AIR_SHOTS_3 → +6 balas extra → 7 balas efectivas totales en el aire.
     */
    public static int getBonusAirShots(ItemStack stack) {
        int bonus = 0;
        for (Id id : getUpgradeIds(stack)) {
            bonus += switch (id) {
                case AIR_SHOTS_1 -> 1;
                case AIR_SHOTS_2 -> 2;
                case AIR_SHOTS_3 -> 3;
                default          -> 0;
            };
        }
        return bonus;
    }

    /**
     * Multiplicador del cooldown de recarga (solo aplica la 1 mejora RELOAD_SPEED instalada).
     *   RELOAD_HALF    → 0.50  (mitad del tiempo = 2× más rápida)
     *   RELOAD_QUARTER → 0.25  (un cuarto    = 4× más rápida)
     *   Sin mejora     → 1.00
     */
    public static double getReloadMultiplier(ItemStack stack) {
        for (Id id : getUpgradeIds(stack)) {
            if (id == Id.RELOAD_HALF)    return 0.50;
            if (id == Id.RELOAD_QUARTER) return 0.25;
        }
        return 1.0;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Tooltip auxiliar (llamado desde GunItem.appendHoverText)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Añade las líneas de mejoras al tooltip de la pistola.
     * Muestra cada mejora instalada y las ranuras disponibles.
     */
    public static void appendUpgradeTooltip(ItemStack stack, List<Component> tooltip) {
        List<Id> upgrades = getUpgradeIds(stack);
        if (upgrades.isEmpty()) {
            tooltip.add(Component.translatable("item.cz-x-mc-weapons.gun.no_upgrades")
                .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.cz-x-mc-weapons.gun.upgrades_header")
                .withStyle(ChatFormatting.GOLD));
            for (Id id : upgrades) {
                tooltip.add(Component.translatable("item.cz-x-mc-weapons.upgrade." + id.key)
                    .withStyle(ChatFormatting.AQUA));
            }
        }
        tooltip.add(Component.translatable(
            "item.cz-x-mc-weapons.gun.upgrade_slots",
            upgrades.size(), MAX_TOTAL
        ).withStyle(upgrades.size() >= MAX_TOTAL ? ChatFormatting.RED : ChatFormatting.GRAY));
    }

    /**
     * Descripción corta de lo que hace cada mejora (para el tooltip del ítem de mejora).
     */
    public static Component getUpgradeDescription(Id id) {
        return Component.translatable("item.cz-x-mc-weapons.upgrade." + id.key + ".desc")
            .withStyle(ChatFormatting.GRAY);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers NBT internos
    // ══════════════════════════════════════════════════════════════════════════

    private static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void setTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
