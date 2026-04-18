package weapons.czxmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import weapons.czxmc.CZXMCWeapons;

/**
 * Paquete S2C que dispara un flash de pantalla + sonido de explosión en el cliente.
 *
 * Usa StreamCodec manual porque composite() soporta máximo 6 campos.
 *
 * @param intensity          Intensidad inicial del flash (0.0 – 1.0)
 * @param durationTicks      Duración total del fade-out en ticks (= duración del sonido)
 * @param r                  Componente rojo del color (0–255)
 * @param g                  Componente verde del color (0–255)
 * @param b                  Componente azul del color (0–255)
 * @param soundVolume        Volumen del sonido de explosión (0.0 – 1.0)
 * @param soundPitch         Pitch del sonido de explosión
 * @param playBeforeSound    Si true, reproducir el sonido "before" en el cliente también
 * @param beforeVolume       Volumen del sonido "before" (0.0 – 1.0)
 */
public record GrenadeFlashPayload(
        float   intensity,
        int     durationTicks,
        int     r,
        int     g,
        int     b,
        float   soundVolume,
        float   soundPitch,
        boolean playBeforeSound,
        float   beforeVolume
) implements CustomPacketPayload {

    public static final Type<GrenadeFlashPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "grenade_flash"));

    public static final StreamCodec<FriendlyByteBuf, GrenadeFlashPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeFloat(p.intensity());
                        buf.writeInt(p.durationTicks());
                        buf.writeInt(p.r());
                        buf.writeInt(p.g());
                        buf.writeInt(p.b());
                        buf.writeFloat(p.soundVolume());
                        buf.writeFloat(p.soundPitch());
                        buf.writeBoolean(p.playBeforeSound());
                        buf.writeFloat(p.beforeVolume());
                    },
                    buf -> new GrenadeFlashPayload(
                        buf.readFloat(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readBoolean(),
                        buf.readFloat()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
