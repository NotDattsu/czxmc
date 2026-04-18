package weapons.czxmc.mixin;

import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.SimpleContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor para leer/escribir los campos protegidos de ItemCombinerMenu
 * (clase padre de AnvilMenu) sin necesitar @Shadow en el mixin hijo.
 *
 * Usar @Shadow de campos de la clase padre en un mixin apuntado al hijo
 * depende de que el refmap esté cargado; este patrón evita ese problema.
 */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {

    @Accessor("inputSlots")
    SimpleContainer getInputSlots();

    @Accessor("resultSlots")
    ResultContainer getResultSlots();
}
