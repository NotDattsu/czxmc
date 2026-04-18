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
 * Sistema de mejoras del Amuleto de Dash.
 *
 * ── Reglas ────────────────────────────────────────────────────────────────
 *   MAX_TOTAL   = 3  (ranuras de mejora por amuleto)
 *   MAX_SPECIAL = 1  (solo 1 mejora especial a la vez)
 *   CHARGES     : máx 2 aplicadas (da hasta 3 cargas en total)
 *   COOLDOWN    : máx 1
 *   SPECIAL     : máx 1 (PHASE, IMPACT o WALL, mutuamente excluyentes)
 *
 * ── Cómo aplicar ─────────────────────────────────────────────────────────
 *   Sostené la mejora en una mano y el amuleto en la otra, click derecho.
 *   Para quitar mejoras: usar yunque con el amuleto solo (devuelve mejoras).
 */
public class DashUpgrade {

    public static final int MAX_TOTAL   = 3;
    public static final int MAX_SPECIAL = 1;

    // ── Tipos ─────────────────────────────────────────────────────────────────
    public enum Type {
        CHARGES,    // más cargas acumulables
        COOLDOWN,   // cooldown más corto
        SPECIAL;    // habilidad especial (una sola)

        public int maxPerType() {
            return switch (this) {
                case CHARGES  -> 2;
                case COOLDOWN -> 1;
                case SPECIAL  -> 1;
            };
        }
    }

    // ── IDs ───────────────────────────────────────────────────────────────────
    public enum Id {
        CHARGES_1  ("charges_1",   Type.CHARGES),
        CHARGES_2  ("charges_2",   Type.CHARGES),
        COOLDOWN_1 ("cooldown_1",  Type.COOLDOWN),
        // Especiales — mutuamente excluyentes (solo 1 de las 3)
        SPECIAL_PHASE ("special_phase",  Type.SPECIAL),  // atravesar mobs (slowness + wither)
        SPECIAL_IMPACT("special_impact", Type.SPECIAL),  // impacto AoE en destino
        SPECIAL_WALL  ("special_wall",   Type.SPECIAL);  // atravesar paredes ≤2 bloques

        public final String key;
        public final Type   type;

        Id(String key, Type type) {
            this.key  = key;
            this.type = type;
        }

        public static Id fromKey(String key) {
            for (Id id : values()) if (id.key.equals(key)) return id;
            return null;
        }
    }

    // ── NBT ───────────────────────────────────────────────────────────────────
    private static final String NBT_UPGRADES = "DashUpgrades";

    // ── Lectura / escritura ───────────────────────────────────────────────────

    public static List<Id> getUpgrades(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        List<Id> result = new ArrayList<>();
        if (!tag.contains(NBT_UPGRADES, Tag.TAG_LIST)) return result;
        ListTag list = tag.getList(NBT_UPGRADES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            Id id = Id.fromKey(list.getString(i));
            if (id != null) result.add(id);
        }
        return result;
    }

    private static void setUpgrades(ItemStack stack, List<Id> upgrades) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = new ListTag();
        for (Id id : upgrades) list.add(StringTag.valueOf(id.key));
        tag.put(NBT_UPGRADES, list);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ── Validación ────────────────────────────────────────────────────────────

    public static boolean canAdd(ItemStack stack, Id id) {
        List<Id> current = getUpgrades(stack);
        if (current.size() >= MAX_TOTAL) return false;
        long countType = current.stream().filter(u -> u.type == id.type).count();
        if (countType >= id.type.maxPerType()) return false;
        // Si es especial, no puede haber otra especial ya
        if (id.type == Type.SPECIAL) {
            boolean hasSpecial = current.stream().anyMatch(u -> u.type == Type.SPECIAL);
            if (hasSpecial) return false;
        }
        return true;
    }

    public static void addUpgrade(ItemStack stack, Id id) {
        List<Id> current = getUpgrades(stack);
        current.add(id);
        setUpgrades(stack, current);
    }

    public static void removeUpgrade(ItemStack stack, Id id) {
        List<Id> current = getUpgrades(stack);
        current.remove(id);
        setUpgrades(stack, current);
    }

    public static void clearUpgrades(ItemStack stack) {
        setUpgrades(stack, new ArrayList<>());
    }

    // ── Getters de stats ──────────────────────────────────────────────────────

    /** Cargas totales disponibles (base 1 + mejoras). */
    public static int getTotalCharges(ItemStack stack) {
        List<Id> upgrades = getUpgrades(stack);
        int bonus = 0;
        for (Id id : upgrades) {
            if (id == Id.CHARGES_1) bonus += 1;
            if (id == Id.CHARGES_2) bonus += 2;
        }
        return 1 + bonus;
    }

    /**
     * Multiplicador de cooldown (1.0 = base, menor = más rápido).
     * Con COOLDOWN_1 → 0.5 (mitad del tiempo).
     */
    public static double getCooldownMultiplier(ItemStack stack) {
        List<Id> upgrades = getUpgrades(stack);
        for (Id id : upgrades) {
            if (id == Id.COOLDOWN_1) return 0.5;
        }
        return 1.0;
    }

    public static boolean hasPhase(ItemStack stack) {
        return getUpgrades(stack).contains(Id.SPECIAL_PHASE);
    }

    public static boolean hasImpact(ItemStack stack) {
        return getUpgrades(stack).contains(Id.SPECIAL_IMPACT);
    }

    public static boolean hasWall(ItemStack stack) {
        return getUpgrades(stack).contains(Id.SPECIAL_WALL);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        List<Id> upgrades = getUpgrades(stack);
        if (upgrades.isEmpty()) {
            tooltip.add(Component.translatable("item.cz-x-mc-weapons.dash_amulet.no_upgrades")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.cz-x-mc-weapons.dash_amulet.upgrades_header")
            .withStyle(ChatFormatting.GRAY));
        for (Id id : upgrades) {
            tooltip.add(Component.literal("  ")
                .append(Component.translatable("item.cz-x-mc-weapons.upgrade." + id.key)
                    .withStyle(ChatFormatting.AQUA)));
        }
        tooltip.add(Component.translatable("item.cz-x-mc-weapons.dash_amulet.upgrade_slots",
            upgrades.size(), MAX_TOTAL)
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
