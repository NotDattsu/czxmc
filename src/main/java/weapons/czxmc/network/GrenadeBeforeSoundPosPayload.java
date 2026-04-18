package weapons.czxmc.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import weapons.czxmc.CZXMCWeapons;

/**
 * Paquete S2C para reproducir sonido "before" con posición 3D.
 *
 * Incluye la posición de la granada para que el cliente use
 * sound attenuation basada en distancia.
 */
public record GrenadeBeforeSoundPosPayload(
        Vec3 position,
        float volume,
        float pitch
) implements CustomPacketPayload {

    public static final Type<GrenadeBeforeSoundPosPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CZXMCWeapons.MOD_ID, "grenade_before_sound_pos"));

    public static final StreamCodec<FriendlyByteBuf, GrenadeBeforeSoundPosPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeDouble(p.position().x());
                        buf.writeDouble(p.position().y());
                        buf.writeDouble(p.position().z());
                        buf.writeFloat(p.volume());
                        buf.writeFloat(p.pitch());
                    },
                    buf -> new GrenadeBeforeSoundPosPayload(
                            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                            buf.readFloat(),
                            buf.readFloat()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}