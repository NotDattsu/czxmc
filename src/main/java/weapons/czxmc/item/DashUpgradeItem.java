package weapons.czxmc.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Ítem de mejora del Amuleto de Dash.
 * Uso: sostené la mejora en una mano y el amuleto en la otra, click derecho.
 */
public class DashUpgradeItem extends Item {

    private final DashUpgrade.Id upgradeId;

    public DashUpgradeItem(DashUpgrade.Id upgradeId, Properties properties) {
        super(properties);
        this.upgradeId = upgradeId;
    }

    public DashUpgrade.Id getUpgradeId() { return upgradeId; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack upgradeStack = player.getItemInHand(hand);

        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND)
                                    ? InteractionHand.OFF_HAND
                                    : InteractionHand.MAIN_HAND;
        ItemStack amuletStack = player.getItemInHand(otherHand);

        if (!(amuletStack.getItem() instanceof DashItem)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("item.cz-x-mc-weapons.upgrade.no_dash_amulet")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(upgradeStack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            // Verificar XP
            if (!sp.isCreative() && sp.experienceLevel < 1) {
                sp.displayClientMessage(
                    Component.translatable("item.cz-x-mc-weapons.upgrade.no_xp")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(upgradeStack);
            }

            if (DashUpgrade.canAdd(amuletStack, upgradeId)) {
                DashUpgrade.addUpgrade(amuletStack, upgradeId);
                upgradeStack.shrink(1);

                if (!sp.isCreative()) sp.giveExperienceLevels(-1);

                sp.displayClientMessage(
                    Component.translatable("item.cz-x-mc-weapons.upgrade.applied",
                        Component.translatable("item.cz-x-mc-weapons.upgrade." + upgradeId.key))
                        .withStyle(ChatFormatting.GREEN), true);

                // Sonido de mejora
                DashItem.playUpgradeSound(sp);
            } else {
                boolean typeFull = DashUpgrade.getUpgrades(amuletStack)
                    .stream().filter(u -> u.type == upgradeId.type).count()
                    >= upgradeId.type.maxPerType();

                String msgKey = (DashUpgrade.getUpgrades(amuletStack).size() >= DashUpgrade.MAX_TOTAL)
                    ? "item.cz-x-mc-weapons.upgrade.fail_total"
                    : "item.cz-x-mc-weapons.upgrade.fail_type";

                sp.displayClientMessage(
                    Component.translatable(msgKey).withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(upgradeStack);
            }
        }

        return InteractionResultHolder.success(upgradeStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(
            "item.cz-x-mc-weapons.upgrade." + upgradeId.key + ".desc")
            .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.cz-x-mc-weapons.upgrade.apply_hint_dash")
            .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.cz-x-mc-weapons.upgrade.xp_cost")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
