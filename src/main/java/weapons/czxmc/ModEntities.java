package weapons.czxmc;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import weapons.czxmc.entity.GunBulletEntity;
import weapons.czxmc.entity.GrenadeEntity;
import weapons.czxmc.entity.AnchorHookEntity;
import weapons.czxmc.entity.EchoGrenadeEntity;

public class ModEntities {

    public static final EntityType<GunBulletEntity> GUN_BULLET = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "gun_bullet"),
            FabricEntityTypeBuilder.<GunBulletEntity>create(MobCategory.MISC, GunBulletEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .build()
    );

    public static final EntityType<GrenadeEntity> GRENADE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "grenade"),
            FabricEntityTypeBuilder.<GrenadeEntity>create(MobCategory.MISC, GrenadeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.3f, 0.3f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)   // Sincroniza cada tick para la rotación
                    .build()
    );

    public static final EntityType<AnchorHookEntity> ANCHOR_HOOK = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "anchor_hook"),
            FabricEntityTypeBuilder.<AnchorHookEntity>create(MobCategory.MISC, AnchorHookEntity::new)
                    .dimensions(EntityDimensions.fixed(0.4f, 0.4f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<EchoGrenadeEntity> ECHO_GRENADE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "echo_grenade"),
            FabricEntityTypeBuilder.<EchoGrenadeEntity>create(MobCategory.MISC, EchoGrenadeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.3f, 0.3f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static void registerAll() {
        CZXMCWeapons.LOGGER.info("Registrando entidades de " + CZXMCWeapons.MOD_ID);
    }
}
