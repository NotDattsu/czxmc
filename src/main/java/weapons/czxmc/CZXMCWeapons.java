package weapons.czxmc;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weapons.czxmc.item.DashItem;
import weapons.czxmc.item.ModItems;
import weapons.czxmc.network.ArtifactReloadPayload;
import weapons.czxmc.network.DashPayload;
import weapons.czxmc.network.GunSoundPayload;
import weapons.czxmc.network.GrenadeFlashPayload;
import weapons.czxmc.network.GrenadeBeforeSoundPayload;
import weapons.czxmc.network.GrenadeBeforeSoundPosPayload;
import weapons.czxmc.network.GrenadeBeforeSoundVolumePayload;
import weapons.czxmc.network.WallPhasePayload;
import weapons.czxmc.ModParticles;
import weapons.czxmc.sound.ModSounds;

public class CZXMCWeapons implements ModInitializer {
    public static final String MOD_ID = "cz-x-mc-weapons";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModParticles.init();
        ModSounds.registerAll();
        ModEntities.registerAll();
        ModItems.registerAll();
        ModCommands.registerAll();

        // Paquete S2C de sonido con fade (pistola)
        PayloadTypeRegistry.playS2C().register(GunSoundPayload.TYPE, GunSoundPayload.CODEC);

        // Paquete S2C de flash de pantalla + sonido de explosión (echo grenade)
        PayloadTypeRegistry.playS2C().register(GrenadeFlashPayload.TYPE, GrenadeFlashPayload.CODEC);

        // Paquete S2C de sonido "before" de echo grenade
        PayloadTypeRegistry.playS2C().register(GrenadeBeforeSoundPayload.TYPE, GrenadeBeforeSoundPayload.CODEC);

        // Paquete S2C de sonido "before" con posición 3D
        PayloadTypeRegistry.playS2C().register(GrenadeBeforeSoundPosPayload.TYPE, GrenadeBeforeSoundPosPayload.CODEC);

        // Paquete S2C para actualizar volumen del sonido "before" cada tick
        PayloadTypeRegistry.playS2C().register(GrenadeBeforeSoundVolumePayload.TYPE, GrenadeBeforeSoundVolumePayload.CODEC);

        // Paquete S2C de noclip visual para wall-phase
        PayloadTypeRegistry.playS2C().register(WallPhasePayload.TYPE, WallPhasePayload.CODEC);

        // Paquete C2S del dash
        PayloadTypeRegistry.playC2S().register(DashPayload.TYPE, DashPayload.CODEC);

        // Paquete C2S de recarga de artefacto
        PayloadTypeRegistry.playC2S().register(ArtifactReloadPayload.TYPE, ArtifactReloadPayload.CODEC);

        // Cuando el servidor recibe el paquete de dash, ejecuta el dash
        ServerPlayNetworking.registerGlobalReceiver(DashPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                DashItem.handleDash(
                    context.player(),
                    payload.dirX(),
                    payload.dirZ(),
                    payload.useMovement()
                );
            });
        });

        // Cuando el servidor recibe la tecla de recarga, recarga el artefacto equipado
        ServerPlayNetworking.registerGlobalReceiver(ArtifactReloadPayload.TYPE, (payload, context) -> {
            context.server().execute(() ->
                DashItem.handleManualRecharge(context.player())
            );
        });

        LOGGER.info("CZ x MC Weapons cargado correctamente.");
    }
}
