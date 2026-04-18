package weapons.czxmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import weapons.czxmc.CZXMCWeapons;

/**
 * Paquete S2C para:
 *  1. Reproducir un sonido de arma con fade in/out solo para el shooter.
 *  2. (Solo recarga) Indicar la velocidad de animación para que coincida
 *     con el cooldown real.
 *
 * @param soundId   ID del SoundEvent
 * @param volume    volumen base
 * @param pitch     pitch ya aleatorizado
 * @param holdTicks ticks a volumen pleno antes del fade-out
 * @param animSpeed velocidad de la animación de recarga (1.0 = normal).
 *                  Para sonidos de disparo, siempre 1.0 (ignorado).
 */
public record GunSoundPayload(
        ResourceLocation soundId,
        float volume,
        float pitch,
        int   holdTicks,
        float animSpeed
) implements CustomPacketPayload {

    public static final Type<GunSoundPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "gun_sound"));

    public static final StreamCodec<FriendlyByteBuf, GunSoundPayload> CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, GunSoundPayload::soundId,
                    ByteBufCodecs.FLOAT,           GunSoundPayload::volume,
                    ByteBufCodecs.FLOAT,           GunSoundPayload::pitch,
                    ByteBufCodecs.VAR_INT,         GunSoundPayload::holdTicks,
                    ByteBufCodecs.FLOAT,           GunSoundPayload::animSpeed,
                    GunSoundPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
