package weapons.czxmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import weapons.czxmc.CZXMCWeapons;

/**
 * Paquete S2C que indica al cliente reproducir el sonido "before" de la echo grenade.
 *
 * Se envía exactamente ECHO_BEFORE_DURATION_TICKS ticks antes de la explosión,
 * de modo que el sonido termina justo cuando la granada detona.
 *
 * @param volume  Volumen ya ajustado por distancia (0.0 – 1.0)
 * @param pitch   Pitch del sonido
 */
public record GrenadeBeforeSoundPayload(
        float volume,
        float pitch
) implements CustomPacketPayload {

    public static final Type<GrenadeBeforeSoundPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "grenade_before_sound"));

    public static final StreamCodec<FriendlyByteBuf, GrenadeBeforeSoundPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, GrenadeBeforeSoundPayload::volume,
                    ByteBufCodecs.FLOAT, GrenadeBeforeSoundPayload::pitch,
                    GrenadeBeforeSoundPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
