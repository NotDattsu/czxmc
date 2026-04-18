package weapons.czxmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import weapons.czxmc.CZXMCWeapons;

/**
 * Paquete C2S (cliente → servidor) para la tecla genérica de recarga/acción-2 de artefactos.
 *
 * No lleva datos: el servidor simplemente sabe que el jugador presionó
 * la tecla de recarga de artefacto y ejecuta la acción correspondiente
 * según lo que tenga equipado.
 */
public record ArtifactReloadPayload() implements CustomPacketPayload {

    public static final Type<ArtifactReloadPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "artifact_reload"));

    public static final StreamCodec<FriendlyByteBuf, ArtifactReloadPayload> CODEC =
            StreamCodec.unit(new ArtifactReloadPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
