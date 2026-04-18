package weapons.czxmc;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class ModParticles {

    public static final SimpleParticleType GUN_BULLET_TRAIL = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "gun_bullet_trail"),
            FabricParticleTypes.simple()
    );

    private ModParticles() {}

    public static void init() {
        // Intencionalmente vacío: fuerza la carga de la clase y registra el tipo.
    }
}