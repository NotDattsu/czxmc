package weapons.czxmc.client.renderer;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import weapons.czxmc.CZXMCWeapons;
import weapons.czxmc.item.GunItem;

public class GunRenderer extends GeoItemRenderer<GunItem> {

    public GunRenderer() {
        super(new GunModel());
    }

    public static class GunModel extends GeoModel<GunItem> {

        @Override
        public ResourceLocation getModelResource(GunItem animatable) {
            return ResourceLocation.fromNamespaceAndPath(
                CZXMCWeapons.MOD_ID, "geo/gun.geo.json"
            );
        }

        @Override
        public ResourceLocation getTextureResource(GunItem animatable) {
            return ResourceLocation.fromNamespaceAndPath(
                CZXMCWeapons.MOD_ID, "textures/item/copper_gun.png"
            );
        }

        @Override
        public ResourceLocation getAnimationResource(GunItem animatable) {
            return ResourceLocation.fromNamespaceAndPath(
                CZXMCWeapons.MOD_ID, "animations/gun.animation.json"
            );
        }
    }
}
