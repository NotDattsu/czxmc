package weapons.czxmc.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Ítem de mejora de la pistola.
 *
 * ── Cómo aplicar ──────────────────────────────────────────────────────────
 *   Sostené la mejora en UNA mano y la pistola en la OTRA, luego
 *   hacé clic derecho con el ítem de mejora.
 *
 * ── Límites por tipo ──────────────────────────────────────────────────────
 *   DAMAGE, IMPULSE, AIR_SHOTS : máx 2 de cada tipo en la misma pistola
 *   RELOAD_SPEED               : máx 1 (solo una velocidad de recarga)
 *   Total global               : máx 5 mejoras simultáneas
 */
public class GunUpgradeItem extends Item {

    private final GunUpgrade.Id upgradeId;

    public GunUpgradeItem(GunUpgrade.Id upgradeId, Properties properties) {
        super(properties);
        this.upgradeId = upgradeId;
    }

    public GunUpgrade.Id getUpgradeId() { return upgradeId; }

    // ══════════════════════════════════════════════════════════════════════════
    //  USE — Aplicar mejora a la pistola en la otra mano
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack upgradeStack = player.getItemInHand(hand);

        // Buscar la pistola en la mano contraria
        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND)
                                    ? InteractionHand.OFF_HAND
                                    : InteractionHand.MAIN_HAND;
        ItemStack gunStack = player.getItemInHand(otherHand);

        if (!(gunStack.getItem() instanceof GunItem)) {
            // Sin pistola en la otra mano → mostrar ayuda
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("item.cz-x-mc-weapons.upgrade.no_gun")
                        .withStyle(ChatFormatting.RED),
                    true
                );
            }
            return InteractionResultHolder.fail(upgradeStack);
        }

        if (!level.isClientSide) {
            // Verificar XP — cuesta 1 nivel
            if (player.experienceLevel < 1 && !player.getAbilities().instabuild) {
                player.displayClientMessage(
                    Component.translatable("item.cz-x-mc-weapons.upgrade.no_xp")
                        .withStyle(ChatFormatting.RED),
                    true
                );
                return InteractionResultHolder.fail(upgradeStack);
            }

            if (GunUpgrade.canAddUpgrade(gunStack, upgradeId)) {
                GunUpgrade.addUpgrade(gunStack, upgradeId);
                upgradeStack.shrink(1); // consumir el ítem de mejora

                // Consumir 1 nivel de XP (salvo modo creativo)
                if (!player.getAbilities().instabuild) {
                    player.giveExperienceLevels(-1);
                }

                player.displayClientMessage(
                    Component.translatable(
                        "item.cz-x-mc-weapons.upgrade.applied",
                        Component.translatable("item.cz-x-mc-weapons.upgrade." + upgradeId.key)
                    ).withStyle(ChatFormatting.GREEN),
                    true
                );

                // Sonido de mejora aplicada
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    GunItem.playUpgradeSound(sp);
                }
            } else {
                // No se puede aplicar: límite de tipo o total alcanzado
                boolean totalFull = GunUpgrade.getUpgradeIds(gunStack).size() >= GunUpgrade.MAX_TOTAL;

                String reasonKey = totalFull
                    ? "item.cz-x-mc-weapons.upgrade.fail_total"
                    : "item.cz-x-mc-weapons.upgrade.fail_type";

                player.displayClientMessage(
                    Component.translatable(reasonKey).withStyle(ChatFormatting.RED),
                    true
                );
                return InteractionResultHolder.fail(upgradeStack);
            }
        }

        return InteractionResultHolder.success(upgradeStack);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TOOLTIP del ítem de mejora
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        // Descripción del efecto
        tooltip.add(GunUpgrade.getUpgradeDescription(upgradeId));

        // Límite del tipo
        tooltip.add(
            Component.translatable(
                "item.cz-x-mc-weapons.upgrade.type_limit",
                upgradeId.type.maxPerType()
            ).withStyle(ChatFormatting.DARK_GRAY)
        );

        // Instrucción de aplicación
        tooltip.add(
            Component.translatable("item.cz-x-mc-weapons.upgrade.apply_hint")
                .withStyle(ChatFormatting.DARK_GRAY)
        );

        // Costo en XP
        tooltip.add(
            Component.translatable("item.cz-x-mc-weapons.upgrade.xp_cost")
                .withStyle(ChatFormatting.GREEN)
        );
    }
}
