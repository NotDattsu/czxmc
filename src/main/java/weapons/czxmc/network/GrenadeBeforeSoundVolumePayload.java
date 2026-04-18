package weapons.czxmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import weapons.czxmc.CZXMCWeapons;

/**
 * Paquete S2C para actualizar el volumen del sonido "before" según la distancia del jugador.
 * Se envía cada tick para que el volumen se ajuste dinámicamente.
 */
public record GrenadeBeforeSoundVolumePayload(
        float volume,
        boolean stopSound
) implements CustomPacketPayload {

    public static final Type<GrenadeBeforeSoundVolumePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "grenade_before_sound_volume"));

    public static final StreamCodec<FriendlyByteBuf, GrenadeBeforeSoundVolumePayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeFloat(p.volume());
                        buf.writeBoolean(p.stopSound());
                    },
                    buf -> new GrenadeBeforeSoundVolumePayload(buf.readFloat(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}