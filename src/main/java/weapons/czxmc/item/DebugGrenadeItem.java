package weapons.czxmc.item;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import weapons.czxmc.entity.GrenadeEntity;

/**
 * Ítem de debug para testear el sistema de física de la granada.
 * Click derecho → lanza una GrenadeEntity en la dirección que mirás.
 *
 * Se muestra como TNT en el mundo (debug visual).
 * Reemplazar este item por el real cuando la física esté a punto.
 */
public class DebugGrenadeItem extends Item {

    /** Velocidad base de lanzamiento en m/tick. */
    private static final float THROW_SPEED = 1.40f;

    public DebugGrenadeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            Vec3 look = player.getLookAngle();

            // Más fuerza si apuntás hacia arriba; menos fuerza si apuntás hacia abajo.
            // xRot: negativo = mira arriba, positivo = mira abajo.
            float pitchStrength = 1.0f + (-player.getXRot() / 90.0f) * 0.65f;
            pitchStrength = Mth.clamp(pitchStrength, 0.50f, 1.80f);

            float finalThrowSpeed = THROW_SPEED * pitchStrength;

            Vec3 initialVelocity = new Vec3(
                look.x * finalThrowSpeed,
                look.y * finalThrowSpeed,
                look.z * finalThrowSpeed
            );

            GrenadeEntity grenade = new GrenadeEntity(level, player, initialVelocity);
            level.addFreshEntity(grenade);

            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }

        return InteractionResultHolder.success(stack);
    }
}
