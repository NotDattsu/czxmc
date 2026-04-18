package weapons.czxmc.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class ConnectorItem extends Item {

    public ConnectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level  level  = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos  clicked = context.getClickedPos();
        ItemStack stack   = context.getItemInHand();

        CustomData data    = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag    = data.copyTag();
        boolean hasPos1    = tag.contains("pos1");
        boolean hasPos2    = tag.contains("pos2");

        if (!hasPos1) {
            // ── Primer clic: guardar punto A ──────────────────────────────────
            tag.putLong("pos1", clicked.asLong());
            tag.remove("pos2");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            if (level.isClientSide) {
                player.displayClientMessage(
                    Component.literal("§aPunto A marcado: " + fmtPos(clicked)), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);

        } else if (!hasPos2) {
            BlockPos pos1 = BlockPos.of(tag.getLong("pos1"));

            if (pos1.equals(clicked)) {
                // Mismo bloque → limpiar
                tag.remove("pos1");
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                if (level.isClientSide) {
                    player.displayClientMessage(
                        Component.literal("§cPunto A limpiado."), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            // ── Segundo clic: guardar punto B → línea activa ──────────────────
            tag.putLong("pos2", clicked.asLong());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            if (level.isClientSide) {
                player.displayClientMessage(
                    Component.literal("§bPunto B marcado: " + fmtPos(clicked) + " §7— §aLínea activa"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);

        } else {
            // ── Tercer clic: limpiar todo y empezar desde este bloque ─────────
            tag.remove("pos1");
            tag.remove("pos2");
            tag.putLong("pos1", clicked.asLong());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            if (level.isClientSide) {
                player.displayClientMessage(
                    Component.literal("§eReiniciado. Punto A: " + fmtPos(clicked)), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    private static String fmtPos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }
}
