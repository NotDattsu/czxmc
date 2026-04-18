package weapons.czxmc.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor para invocar broadcastChanges() desde AbstractContainerMenu.
 * Se necesita porque no se puede hacer @Shadow de un método de la clase padre
 * en un mixin que apunta a la clase hijo (AnvilMenu).
 */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {
    @Invoker("broadcastChanges")
    void invokeBroadcastChanges();
}
